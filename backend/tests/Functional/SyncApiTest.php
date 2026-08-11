<?php

declare(strict_types=1);

namespace Cstv\Backend\Tests\Functional;

final class SyncApiTest extends FunctionalTestCase
{
    public function testSyncPaginationReturnsEveryRevisionExactlyOnceWithoutPayload(): void
    {
        $account = $this->createAccount();
        $profileId = $account['profileIds'][0];
        foreach ([
            ['favorites', 'movie-1', 'favorite'],
            ['playback', 'movie-1', 'position'],
            ['ratings', 'movie-1', 'rating'],
            ['playback', 'movie-1', 'position-updated'],
            ['category-preferences', 'snapshot', 'categories'],
        ] as [$namespace, $key, $payload]) {
            self::assertSame(204, $this->putObject(
                $account['token'], $profileId, $namespace, $key, (string) gzencode($payload),
            )->status);
        }

        $cursor = 0;
        $pages = [];
        do {
            $page = $this->api->get(
                '/v1/sync/changes?cursor=' . $cursor . '&limit=2',
                $this->auth($account['token']),
            )->json();
            $pages[] = $page;
            $cursor = $page['nextCursor'];
        } while ($page['hasMore']);

        self::assertSame([2, 2, 1], array_map(static fn (array $item): int => count($item['changes']), $pages));
        $changes = array_merge(...array_column($pages, 'changes'));
        $revisions = array_column($changes, 'revision');
        self::assertSame($revisions, array_values(array_unique($revisions)));
        $sorted = $revisions;
        sort($sorted);
        self::assertSame($sorted, $revisions);
        self::assertSame(['UPSERT', 'UPSERT', 'UPSERT', 'UPSERT', 'UPSERT'], array_column($changes, 'operation'));
        foreach ($changes as $change) {
            self::assertArrayNotHasKey('payload', $change);
        }
        self::assertFalse($pages[2]['hasMore']);
        self::assertSame($revisions[4], $pages[2]['nextCursor']);

        $empty = $this->api->get(
            '/v1/sync/changes?cursor=' . $cursor . '&limit=2',
            $this->auth($account['token']),
        )->json();
        self::assertSame([], $empty['changes']);
        self::assertSame($cursor, $empty['nextCursor']);
        self::assertFalse($empty['hasMore']);
    }

    public function testSameKeyKeepsAppendOnlyUpsertUpsertDeleteHistory(): void
    {
        $account = $this->createAccount();
        $profileId = $account['profileIds'][0];
        $path = '/v1/profiles/' . $profileId . '/objects/favorites/movie-123';
        $this->putObject($account['token'], $profileId, 'favorites', 'movie-123', (string) gzencode('v1'));
        $this->putObject($account['token'], $profileId, 'favorites', 'movie-123', (string) gzencode('v2'));
        self::assertSame(204, $this->api->delete($path, $this->auth($account['token']))->status);

        $changes = $this->api->get('/v1/sync/changes?cursor=0', $this->auth($account['token']))->json()['changes'];
        self::assertSame(['UPSERT', 'UPSERT', 'DELETE'], array_column($changes, 'operation'));
        self::assertSame(['movie-123', 'movie-123', 'movie-123'], array_column($changes, 'key'));
        self::assertNotSame($changes[0]['etag'], $changes[1]['etag']);
        self::assertNull($changes[2]['etag']);
    }

    public function testSyncIsStrictlyIsolatedBetweenAccounts(): void
    {
        $first = $this->createAccount('first-sync@example.com');
        $second = $this->createAccount('second-sync@example.com');
        $this->putObject($first['token'], $first['profileIds'][0], 'favorites', 'first', (string) gzencode('first'));
        $this->putObject($second['token'], $second['profileIds'][0], 'favorites', 'second', (string) gzencode('second'));

        $firstChanges = $this->api->get('/v1/sync/changes', $this->auth($first['token']))->json()['changes'];
        self::assertCount(1, $firstChanges);
        self::assertSame('first', $firstChanges[0]['key']);
        self::assertNotContains($second['profileIds'][0], array_column($firstChanges, 'profileId'));
    }

    public function testSyncQueryValidationIsBounded(): void
    {
        $account = $this->createAccount();
        foreach (['cursor=-1', 'cursor=abc', 'limit=0', 'limit=501'] as $query) {
            $this->assertError(
                $this->api->get('/v1/sync/changes?' . $query, $this->auth($account['token'])),
                422,
                'INVALID_QUERY_PARAMETER',
            );
        }
    }
}
