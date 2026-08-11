<?php

declare(strict_types=1);

namespace Cstv\Backend\Tests\Integration;

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
        $path = sprintf('/v1/profiles/%s/objects/favorites', $profileId);
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
        self::assertSame(1, (int) $this->pdo->query(
            "SELECT COUNT(*) FROM profile_objects WHERE profile_id = '{$profileId}' AND namespace = 'favorites'",
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

}
