<?php

declare(strict_types=1);

namespace Cstv\Backend\Tests\Integration;

final class SyncTest extends IntegrationTestCase
{
    public function testUpsertAndDeleteCreateOrderedRevisions(): void
    {
        $account = $this->createAccount();
        $profileId = $account['profileIds'][0];
        $this->putObject($account['token'], $profileId, 'favorites', 'movie-1', gzencode('{"id":1}') ?: '');
        $this->request(
            'DELETE',
            "/v1/profiles/{$profileId}/objects/favorites/movie-1",
            '',
            $this->auth($account['token']),
        );

        $rows = $this->pdo->query('SELECT revision, operation, etag FROM sync_changes ORDER BY revision')->fetchAll();
        self::assertCount(2, $rows);
        self::assertSame('UPSERT', $rows[0]['operation']);
        self::assertNotNull($rows[0]['etag']);
        self::assertSame('DELETE', $rows[1]['operation']);
        self::assertNull($rows[1]['etag']);
        self::assertGreaterThan((int) $rows[0]['revision'], (int) $rows[1]['revision']);
    }

    public function testChangesNeverExposeAnotherAccount(): void
    {
        $first = $this->createAccount('first@example.com');
        $second = $this->createAccount('second@example.com');
        $this->putObject($first['token'], $first['profileIds'][0], 'favorites', 'movie-1', gzencode('1') ?: '');
        $this->putObject($second['token'], $second['profileIds'][0], 'favorites', 'movie-2', gzencode('2') ?: '');

        $response = $this->request('GET', '/v1/sync/changes', '', $this->auth($first['token']));
        $changes = $this->json($response)['changes'];
        self::assertCount(1, $changes);
        self::assertSame('movie-1', $changes[0]['key']);
        self::assertArrayNotHasKey('payload', $changes[0]);
    }

    public function testChangesArePaginatedByCursor(): void
    {
        $account = $this->createAccount();
        $profileId = $account['profileIds'][0];
        foreach ([1, 2, 3] as $id) {
            $this->putObject(
                $account['token'],
                $profileId,
                'favorites',
                'movie-' . $id,
                gzencode((string) $id) ?: '',
            );
        }

        $first = $this->json($this->request(
            'GET',
            '/v1/sync/changes?cursor=0&limit=2',
            '',
            $this->auth($account['token']),
        ));
        self::assertCount(2, $first['changes']);
        self::assertTrue($first['hasMore']);

        $second = $this->json($this->request(
            'GET',
            '/v1/sync/changes?cursor=' . $first['nextCursor'] . '&limit=2',
            '',
            $this->auth($account['token']),
        ));
        self::assertCount(1, $second['changes']);
        self::assertFalse($second['hasMore']);
        self::assertGreaterThan($first['nextCursor'], $second['nextCursor']);
    }

    public function testPaginationCoversEveryRevisionExactlyOnce(): void
    {
        $account = $this->createAccount();
        $profileId = $account['profileIds'][0];
        for ($index = 1; $index <= 7; $index++) {
            $this->putObject($account['token'], $profileId, 'favorites', 'movie-' . $index, (string) gzencode((string) $index));
        }
        $this->request(
            'DELETE',
            "/v1/profiles/{$profileId}/objects/favorites/movie-1",
            '',
            $this->auth($account['token']),
        );

        $cursor = 0;
        $revisions = [];
        $pages = 0;
        do {
            $page = $this->json($this->request(
                'GET',
                '/v1/sync/changes?cursor=' . $cursor . '&limit=3',
                '',
                $this->auth($account['token']),
            ));
            foreach ($page['changes'] as $change) {
                $revisions[] = $change['revision'];
            }
            $cursor = $page['nextCursor'];
            $pages++;
        } while ($page['hasMore'] && $pages < 10);

        self::assertCount(8, $revisions);
        self::assertSame($revisions, array_values(array_unique($revisions)));
        $sorted = $revisions;
        sort($sorted);
        self::assertSame($sorted, $revisions, 'Revisions must be returned in ascending order.');
    }

    public function testRepeatedWritesOnTheSameKeyProduceOneRevisionEach(): void
    {
        $account = $this->createAccount();
        $profileId = $account['profileIds'][0];
        foreach ([1, 2, 3] as $version) {
            $this->putObject($account['token'], $profileId, 'playback', 'movie-1', (string) gzencode((string) $version));
        }

        $changes = $this->json($this->request('GET', '/v1/sync/changes', '', $this->auth($account['token'])))['changes'];
        self::assertCount(3, $changes);
        self::assertSame(['movie-1', 'movie-1', 'movie-1'], array_column($changes, 'key'));
        self::assertSame(
            '"' . $changes[2]['etag'] . '"',
            $this->putObject($account['token'], $profileId, 'playback', 'movie-1', (string) gzencode('3'))
                ->getHeaderLine('ETag'),
        );
    }

    public function testCursorAtTheEndReturnsAnEmptyStablePage(): void
    {
        $account = $this->createAccount();
        $this->putObject($account['token'], $account['profileIds'][0], 'favorites', 'movie-1', (string) gzencode('1'));

        $first = $this->json($this->request('GET', '/v1/sync/changes', '', $this->auth($account['token'])));
        $empty = $this->json($this->request(
            'GET',
            '/v1/sync/changes?cursor=' . $first['nextCursor'],
            '',
            $this->auth($account['token']),
        ));

        self::assertSame([], $empty['changes']);
        self::assertFalse($empty['hasMore']);
        self::assertSame($first['nextCursor'], $empty['nextCursor']);
    }

    /** @return list<array{0: string, 1: int}> */
    public static function limitProvider(): array
    {
        return [
            'zero' => ['0', 422],
            'negative' => ['-1', 422],
            'not a number' => ['abc', 422],
            'above maximum' => ['501', 422],
            'minimum' => ['1', 200],
            'maximum' => ['500', 200],
        ];
    }

    #[\PHPUnit\Framework\Attributes\DataProvider('limitProvider')]
    public function testLimitBoundaries(string $limit, int $expectedStatus): void
    {
        $account = $this->createAccount();
        $response = $this->request(
            'GET',
            '/v1/sync/changes?limit=' . urlencode($limit),
            '',
            $this->auth($account['token']),
        );

        self::assertSame($expectedStatus, $response->getStatusCode());
    }

    public function testNegativeCursorIsRefused(): void
    {
        $account = $this->createAccount();
        $response = $this->request('GET', '/v1/sync/changes?cursor=-1', '', $this->auth($account['token']));

        self::assertSame(422, $response->getStatusCode());
        self::assertSame('INVALID_QUERY_PARAMETER', $this->json($response)['error']['code']);
    }

    public function testObjectWriteRollsBackWhenChangeAppendFails(): void
    {
        $account = $this->createAccount();
        $this->pdo->exec(<<<'SQL'
CREATE FUNCTION test_fail_sync_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'forced sync failure';
END;
$$;
CREATE TRIGGER test_fail_sync_change BEFORE INSERT ON sync_changes
FOR EACH ROW EXECUTE FUNCTION test_fail_sync_change();
SQL);

        try {
            $response = $this->putObject(
                $account['token'],
                $account['profileIds'][0],
                'favorites',
                'movie-1',
                gzencode('{"id":1}') ?: '',
            );
            self::assertSame(500, $response->getStatusCode());
            self::assertSame(0, (int) $this->pdo->query('SELECT COUNT(*) FROM profile_objects')->fetchColumn());
            self::assertSame(0, (int) $this->pdo->query('SELECT COUNT(*) FROM sync_changes')->fetchColumn());
        } finally {
            $this->pdo->exec('DROP TRIGGER IF EXISTS test_fail_sync_change ON sync_changes');
            $this->pdo->exec('DROP FUNCTION IF EXISTS test_fail_sync_change()');
        }
    }
}
