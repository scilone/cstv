<?php

declare(strict_types=1);

namespace Cstv\Backend\Tests\Integration;

use Cstv\Backend\Database\Connection;
use PDOException;

/**
 * These tests need genuinely simultaneous requests, so they drive the running Docker stack
 * through curl_multi instead of the in-process Slim app.
 */
final class ConcurrencyTest extends IntegrationTestCase
{
    public function testOnlyOneOfTwoSimultaneousPutsCanUseTheSameIfMatch(): void
    {
        $account = $this->createAccount('race-put@example.com');
        $profileId = $account['profileIds'][0];
        $path = sprintf('/v1/profiles/%s/objects/favorites/movie-1', $profileId);
        $headers = [
            'Authorization' => 'Bearer ' . $account['token'],
            'Content-Type' => 'application/vnd.cstv.blob+gzip',
            'X-Schema-Version' => '1',
        ];

        $initial = (string) gzencode('{"version":0}');
        $created = $this->parallelRequests([
            ['method' => 'PUT', 'path' => $path, 'body' => $initial, 'headers' => $headers],
        ]);
        self::assertSame(204, $created[0]['status']);
        $sharedEtag = '"' . hash('sha256', $initial) . '"';

        $results = $this->parallelRequests([
            [
                'method' => 'PUT',
                'path' => $path,
                'body' => (string) gzencode('{"version":1}'),
                'headers' => $headers + ['If-Match' => $sharedEtag],
            ],
            [
                'method' => 'PUT',
                'path' => $path,
                'body' => (string) gzencode('{"version":2}'),
                'headers' => $headers + ['If-Match' => $sharedEtag],
            ],
        ]);

        self::assertSame([204, 412], $this->statuses($results));
        self::assertSame(2, (int) $this->pdo->query(
            "SELECT COUNT(*) FROM sync_changes WHERE account_id = '{$account['id']}'",
        )->fetchColumn());
    }

    public function testTwoSimultaneousDeletesCannotRemoveBothRemainingProfiles(): void
    {
        $account = $this->createAccount('race-profile@example.com', profileCount: 2);
        $headers = ['Authorization' => 'Bearer ' . $account['token']];

        $results = $this->parallelRequests([
            ['method' => 'DELETE', 'path' => '/v1/profiles/' . $account['profileIds'][0], 'headers' => $headers],
            ['method' => 'DELETE', 'path' => '/v1/profiles/' . $account['profileIds'][1], 'headers' => $headers],
        ]);

        self::assertSame([204, 409], $this->statuses($results));
        self::assertSame(1, (int) $this->pdo->query(
            "SELECT COUNT(*) FROM profiles WHERE account_id = '{$account['id']}'",
        )->fetchColumn());
    }

    /**
     * A journal append must hold the per-account advisory lock until COMMIT, otherwise a BIGSERIAL
     * revision can become visible after a later one and a polling client skips it forever.
     */
    public function testJournalAppendWaitsForTheAccountLockHeldByAnotherConnection(): void
    {
        $account = $this->createAccount('journal-lock@example.com');
        $holder = Connection::create($this->config);
        $holder->beginTransaction();
        $lock = $holder->prepare('SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))');
        $lock->execute(['key' => 'sync:' . $account['id']]);

        $this->pdo->exec("SET statement_timeout = '750ms'");
        try {
            $blocked = $this->putObject(
                $account['token'],
                $account['profileIds'][0],
                'favorites',
                'movie-1',
                (string) gzencode('{"id":1}'),
            );
            self::assertSame(500, $blocked->getStatusCode(), 'The write should have waited for the lock.');
        } finally {
            $this->pdo->exec('SET statement_timeout = 0');
            if ($this->pdo->inTransaction()) {
                $this->pdo->rollBack();
            }
            $holder->rollBack();
            unset($holder);
        }

        self::assertSame(0, (int) $this->pdo->query('SELECT COUNT(*) FROM sync_changes')->fetchColumn());
        self::assertSame(204, $this->putObject(
            $account['token'],
            $account['profileIds'][0],
            'favorites',
            'movie-1',
            (string) gzencode('{"id":1}'),
        )->getStatusCode());
    }

    /** Sanity check that the advisory key really is per account and not global. */
    public function testJournalLockIsScopedToOneAccount(): void
    {
        $first = $this->createAccount('scope-a@example.com');
        $second = $this->createAccount('scope-b@example.com');

        $holder = Connection::create($this->config);
        $holder->beginTransaction();
        $lock = $holder->prepare('SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))');
        $lock->execute(['key' => 'sync:' . $first['id']]);

        $this->pdo->exec("SET statement_timeout = '2s'");
        try {
            $response = $this->putObject(
                $second['token'],
                $second['profileIds'][0],
                'favorites',
                'movie-1',
                (string) gzencode('{"id":1}'),
            );
            self::assertSame(204, $response->getStatusCode());
        } catch (PDOException $exception) {
            self::fail('An unrelated account was blocked: ' . $exception->getMessage());
        } finally {
            $this->pdo->exec('SET statement_timeout = 0');
            $holder->rollBack();
            unset($holder);
        }
    }
}
