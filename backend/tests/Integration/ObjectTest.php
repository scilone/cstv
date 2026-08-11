<?php

declare(strict_types=1);

namespace Cstv\Backend\Tests\Integration;

final class ObjectTest extends IntegrationTestCase
{
    public function testPutAndGetPreserveExactBytesAndCalculateEtag(): void
    {
        $account = $this->createAccount();
        $profileId = $account['profileIds'][0];
        $payload = gzencode("{\"schemaVersion\":1,\"binary\":\"\\u0000\\u00ff\"}", 6);
        self::assertNotFalse($payload);

        $put = $this->putObject($account['token'], $profileId, 'favorites', 'movie-12345', $payload);
        self::assertSame(204, $put->getStatusCode());
        self::assertSame('"' . hash('sha256', $payload) . '"', $put->getHeaderLine('ETag'));

        $get = $this->request(
            'GET',
            "/v1/profiles/{$profileId}/objects/favorites/movie-12345",
            '',
            $this->auth($account['token']),
        );
        self::assertSame(200, $get->getStatusCode());
        self::assertSame('application/vnd.cstv.blob+gzip', $get->getHeaderLine('Content-Type'));
        self::assertSame($payload, (string) $get->getBody());
        self::assertSame(strlen($payload), (int) $this->pdo->query('SELECT compressed_size FROM profile_objects')->fetchColumn());
    }

    public function testListReturnsMetadataWithoutPayloadAndSupportsNamespaceFilter(): void
    {
        $account = $this->createAccount();
        $profileId = $account['profileIds'][0];
        $this->putObject($account['token'], $profileId, 'favorites', 'movie-1', gzencode('{"id":1}') ?: '');
        $this->putObject($account['token'], $profileId, 'ratings', 'movie-1', gzencode('{"rating":8}') ?: '');

        $response = $this->request(
            'GET',
            "/v1/profiles/{$profileId}/objects?namespace=favorites",
            '',
            $this->auth($account['token']),
        );
        $objects = $this->json($response)['objects'];
        self::assertCount(1, $objects);
        self::assertSame('favorites', $objects[0]['namespace']);
        self::assertArrayNotHasKey('payload', $objects[0]);
    }

    public function testReplacementSupportsValidIfMatchAndRejectsInvalidIfMatch(): void
    {
        $account = $this->createAccount();
        $profileId = $account['profileIds'][0];
        $firstPayload = gzencode('{"version":1}') ?: '';
        $first = $this->putObject($account['token'], $profileId, 'playback', 'movie-1', $firstPayload);
        $firstEtag = $first->getHeaderLine('ETag');

        $secondPayload = gzencode('{"version":2}') ?: '';
        $second = $this->putObject(
            $account['token'],
            $profileId,
            'playback',
            'movie-1',
            $secondPayload,
            ['If-Match' => $firstEtag],
        );
        self::assertSame(204, $second->getStatusCode());
        self::assertNotSame($firstEtag, $second->getHeaderLine('ETag'));

        $rejected = $this->putObject(
            $account['token'],
            $profileId,
            'playback',
            'movie-1',
            gzencode('{"version":3}') ?: '',
            ['If-Match' => '"deadbeef"'],
        );
        self::assertSame(412, $rejected->getStatusCode());
        self::assertSame('ETAG_MISMATCH', $this->json($rejected)['error']['code']);

        $get = $this->request(
            'GET',
            "/v1/profiles/{$profileId}/objects/playback/movie-1",
            '',
            $this->auth($account['token']),
        );
        self::assertSame($secondPayload, (string) $get->getBody());
    }

    public function testDeleteIsIdempotentAndOptionalIfMatchIsEnforcedForExistingObject(): void
    {
        $account = $this->createAccount();
        $profileId = $account['profileIds'][0];
        $put = $this->putObject($account['token'], $profileId, 'ratings', 'movie-1', gzencode('{"rating":9}') ?: '');
        $uri = "/v1/profiles/{$profileId}/objects/ratings/movie-1";

        $mismatch = $this->request('DELETE', $uri, '', array_merge(
            $this->auth($account['token']),
            ['If-Match' => '"incorrect"'],
        ));
        self::assertSame(412, $mismatch->getStatusCode());

        $deleted = $this->request('DELETE', $uri, '', array_merge(
            $this->auth($account['token']),
            ['If-Match' => $put->getHeaderLine('ETag')],
        ));
        self::assertSame(204, $deleted->getStatusCode());
        $revisionCount = (int) $this->pdo->query('SELECT COUNT(*) FROM sync_changes')->fetchColumn();

        self::assertSame(204, $this->request('DELETE', $uri, '', $this->auth($account['token']))->getStatusCode());
        self::assertSame($revisionCount, (int) $this->pdo->query('SELECT COUNT(*) FROM sync_changes')->fetchColumn());
    }

    public function testOversizedPayloadIsRejectedBeforePersistence(): void
    {
        $account = $this->createAccount();
        $response = $this->putObject(
            $account['token'],
            $account['profileIds'][0],
            'favorites',
            'movie-1',
            str_repeat('x', $this->config->maxObjectSizeBytes + 1),
        );

        self::assertSame(413, $response->getStatusCode());
        self::assertSame(0, (int) $this->pdo->query('SELECT COUNT(*) FROM profile_objects')->fetchColumn());
    }

    public function testSizeLimitIsMeasuredOnTheReceivedBytesNotOnTheAnnouncedLength(): void
    {
        $account = $this->createAccount();
        $response = $this->putObject(
            $account['token'],
            $account['profileIds'][0],
            'favorites',
            'movie-1',
            str_repeat('x', $this->config->maxObjectSizeBytes + 1),
            ['Content-Length' => '12', 'X-Compressed-Size' => '12'],
        );

        self::assertSame(413, $response->getStatusCode());
        self::assertSame(0, (int) $this->pdo->query('SELECT COUNT(*) FROM profile_objects')->fetchColumn());
    }

    public function testPayloadOfExactlyTheMaximumSizeIsAccepted(): void
    {
        $account = $this->createAccount();
        $payload = str_repeat('x', $this->config->maxObjectSizeBytes);
        $response = $this->putObject($account['token'], $account['profileIds'][0], 'favorites', 'movie-1', $payload);

        self::assertSame(204, $response->getStatusCode());
        self::assertSame(
            $this->config->maxObjectSizeBytes,
            (int) $this->pdo->query('SELECT compressed_size FROM profile_objects')->fetchColumn(),
        );
    }

    public function testBinaryRoundTripIsExactForEveryByteValue(): void
    {
        $account = $this->createAccount();
        $profileId = $account['profileIds'][0];
        $payload = '';
        for ($byte = 0; $byte < 256; $byte++) {
            $payload .= chr($byte);
        }

        $this->putObject($account['token'], $profileId, 'favorites', 'binary', $payload);
        $get = $this->request(
            'GET',
            "/v1/profiles/{$profileId}/objects/favorites/binary",
            '',
            $this->auth($account['token']),
        );

        $returned = (string) $get->getBody();
        self::assertSame(strlen($payload), strlen($returned));
        self::assertSame(0, strcmp($payload, $returned));
        self::assertSame('"' . hash('sha256', $payload) . '"', $get->getHeaderLine('ETag'));
    }

    public function testRewritingTheSameContentKeepsTheEtagAndStillJournalsARevision(): void
    {
        $account = $this->createAccount();
        $profileId = $account['profileIds'][0];
        $payload = (string) gzencode('{"stable":true}');

        $first = $this->putObject($account['token'], $profileId, 'favorites', 'movie-1', $payload);
        $second = $this->putObject($account['token'], $profileId, 'favorites', 'movie-1', $payload);

        self::assertSame($first->getHeaderLine('ETag'), $second->getHeaderLine('ETag'));
        self::assertSame(2, (int) $this->pdo->query('SELECT COUNT(*) FROM sync_changes')->fetchColumn());
    }

    public function testIfMatchStarRequiresAnExistingObject(): void
    {
        $account = $this->createAccount();
        $profileId = $account['profileIds'][0];

        $absent = $this->putObject(
            $account['token'],
            $profileId,
            'favorites',
            'movie-1',
            (string) gzencode('{"id":1}'),
            ['If-Match' => '*'],
        );
        self::assertSame(412, $absent->getStatusCode());

        $this->putObject($account['token'], $profileId, 'favorites', 'movie-1', (string) gzencode('{"id":1}'));
        $present = $this->putObject(
            $account['token'],
            $profileId,
            'favorites',
            'movie-1',
            (string) gzencode('{"id":2}'),
            ['If-Match' => '*'],
        );
        self::assertSame(204, $present->getStatusCode());
    }

    public function testDeleteWithAStaleIfMatchFailsEvenWhenTheObjectIsAlreadyGone(): void
    {
        $account = $this->createAccount();
        $profileId = $account['profileIds'][0];
        $uri = "/v1/profiles/{$profileId}/objects/favorites/movie-1";
        $stale = $this->putObject($account['token'], $profileId, 'favorites', 'movie-1', (string) gzencode('{"id":1}'))
            ->getHeaderLine('ETag');

        self::assertSame(204, $this->request('DELETE', $uri, '', $this->auth($account['token']))->getStatusCode());
        $revisions = (int) $this->pdo->query('SELECT COUNT(*) FROM sync_changes')->fetchColumn();

        $replayed = $this->request('DELETE', $uri, '', array_merge(
            $this->auth($account['token']),
            ['If-Match' => $stale],
        ));

        self::assertSame(412, $replayed->getStatusCode());
        self::assertSame('ETAG_MISMATCH', $this->json($replayed)['error']['code']);
        self::assertSame($revisions, (int) $this->pdo->query('SELECT COUNT(*) FROM sync_changes')->fetchColumn());
    }

    public function testWrongContentTypeIsRefused(): void
    {
        $account = $this->createAccount();
        $response = $this->putObject(
            $account['token'],
            $account['profileIds'][0],
            'favorites',
            'movie-1',
            (string) gzencode('{"id":1}'),
            ['Content-Type' => 'application/json'],
        );

        self::assertSame(415, $response->getStatusCode());
        self::assertSame('UNSUPPORTED_MEDIA_TYPE', $this->json($response)['error']['code']);
    }

    /** @return list<array{0: string}> */
    public static function invalidSchemaVersionProvider(): array
    {
        return [['0'], ['-1'], ['1.5'], ['abc'], ['2147483648']];
    }

    public function testMissingSchemaVersionIsRefused(): void
    {
        $account = $this->createAccount();
        $response = $this->request(
            'PUT',
            sprintf('/v1/profiles/%s/objects/favorites/movie-1', $account['profileIds'][0]),
            (string) gzencode('{"id":1}'),
            array_merge($this->auth($account['token']), ['Content-Type' => 'application/vnd.cstv.blob+gzip']),
        );

        self::assertSame(422, $response->getStatusCode());
        self::assertSame('INVALID_SCHEMA_VERSION', $this->json($response)['error']['code']);
    }

    #[\PHPUnit\Framework\Attributes\DataProvider('invalidSchemaVersionProvider')]
    public function testInvalidSchemaVersionIsRefused(string $schemaVersion): void
    {
        $account = $this->createAccount();
        $response = $this->putObject(
            $account['token'],
            $account['profileIds'][0],
            'favorites',
            'movie-1',
            (string) gzencode('{"id":1}'),
            ['X-Schema-Version' => $schemaVersion],
        );

        self::assertSame(422, $response->getStatusCode());
        self::assertSame('INVALID_SCHEMA_VERSION', $this->json($response)['error']['code']);
    }

    /** @return list<array{0: string, 1: string, 2: string}> */
    public static function invalidPathProvider(): array
    {
        return [
            'namespace uppercase' => ['Favorites', 'movie-1', 'INVALID_NAMESPACE'],
            'namespace leading underscore' => ['_hidden', 'movie-1', 'INVALID_NAMESPACE'],
            'namespace too long' => [str_repeat('n', 65), 'movie-1', 'INVALID_NAMESPACE'],
            'key leading dash' => ['favorites', '-movie', 'INVALID_OBJECT_KEY'],
            'key too long' => ['favorites', str_repeat('k', 129), 'INVALID_OBJECT_KEY'],
        ];
    }

    #[\PHPUnit\Framework\Attributes\DataProvider('invalidPathProvider')]
    public function testInvalidNamespaceOrKeyIsRefused(string $namespace, string $key, string $expected): void
    {
        $account = $this->createAccount();
        $response = $this->putObject(
            $account['token'],
            $account['profileIds'][0],
            $namespace,
            $key,
            (string) gzencode('{"id":1}'),
        );

        self::assertSame(422, $response->getStatusCode());
        self::assertSame($expected, $this->json($response)['error']['code']);
    }

    public function testArbitraryNamespacesAreAccepted(): void
    {
        $account = $this->createAccount();
        $profileId = $account['profileIds'][0];
        $namespaces = [
            'favorites',
            'playback',
            'ratings',
            'track-preferences',
            'series-watch-state',
            'category-preferences',
            'a-namespace-invented-later',
        ];

        foreach ($namespaces as $namespace) {
            self::assertSame(204, $this->putObject(
                $account['token'],
                $profileId,
                $namespace,
                'key-1',
                (string) gzencode('{"n":"' . $namespace . '"}'),
            )->getStatusCode());
        }

        $listed = $this->json($this->request(
            'GET',
            "/v1/profiles/{$profileId}/objects",
            '',
            $this->auth($account['token']),
        ))['objects'];
        $listedNamespaces = array_values(array_unique(array_column($listed, 'namespace')));
        sort($namespaces);
        self::assertSame($namespaces, $listedNamespaces);
    }
}
