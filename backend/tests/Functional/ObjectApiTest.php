<?php

declare(strict_types=1);

namespace Cstv\Backend\Tests\Functional;

use PHPUnit\Framework\Attributes\DataProvider;

final class ObjectApiTest extends FunctionalTestCase
{
    public function testFavoriteGzipRoundTripIsStrictlyByteForByte(): void
    {
        $account = $this->createAccount();
        $profileId = $account['profileIds'][0];
        $document = [
            'schemaVersion' => 1,
            'id' => 12345,
            'type' => 'movie',
            'name' => 'Interstellar',
            'cover' => 'https://example.com/interstellar.jpg',
            'categoryId' => '42',
            'addedAt' => 1786441680000,
        ];
        $json = json_encode($document, JSON_THROW_ON_ERROR | JSON_UNESCAPED_SLASHES);
        $gzip = gzencode($json);
        self::assertNotFalse($gzip);

        $put = $this->putObject($account['token'], $profileId, 'favorites', 'movie-12345', $gzip);
        self::assertSame(204, $put->status);
        $expectedEtag = '"' . hash('sha256', $gzip) . '"';
        self::assertSame($expectedEtag, $put->header('etag'));

        $get = $this->api->get(
            '/v1/profiles/' . $profileId . '/objects/favorites/movie-12345',
            $this->auth($account['token']),
        );
        self::assertSame(200, $get->status);
        self::assertSame('application/vnd.cstv.blob+gzip', $get->header('content-type'));
        self::assertSame($expectedEtag, $get->header('etag'));
        self::assertSame($gzip, $get->body);

        $metadata = $this->api->get(
            '/v1/profiles/' . $profileId . '/objects?namespace=favorites',
            $this->auth($account['token']),
        )->json()['objects'][0];
        self::assertSame(1, $metadata['schemaVersion']);
        self::assertSame(strlen($gzip), $metadata['compressedSize']);
        self::assertSame(hash('sha256', $gzip), $metadata['etag']);
        self::assertSame(1, (int) $this->pdo->query('SELECT COUNT(*) FROM sync_changes')->fetchColumn());
    }

    public function testUnicodeGzipRoundTripSurvivesEveryTransportLayer(): void
    {
        $account = $this->createAccount();
        $profileId = $account['profileIds'][0];
        $gzip = gzencode(json_encode([
            'title' => 'Le Château ambulant 🎬',
            'languages' => ['français', '日本語', 'العربية'],
            'nul' => "\0",
        ], JSON_THROW_ON_ERROR | JSON_UNESCAPED_UNICODE));
        self::assertNotFalse($gzip);

        self::assertSame(204, $this->putObject($account['token'], $profileId, 'future-data', 'unicode-1', $gzip)->status);
        $received = $this->api->get(
            '/v1/profiles/' . $profileId . '/objects/future-data/unicode-1',
            $this->auth($account['token']),
        );
        self::assertSame($gzip, $received->body);
    }

    public function testListingReturnsMetadataOnlyAndFiltersNamespace(): void
    {
        $account = $this->createAccount();
        $profileId = $account['profileIds'][0];
        foreach ([
            ['favorites', 'movie-1', 'favorite'],
            ['playback', 'movie-1', 'playback'],
            ['ratings', 'movie-1', 'rating'],
        ] as [$namespace, $key, $value]) {
            $this->putObject($account['token'], $profileId, $namespace, $key, (string) gzencode($value));
        }

        $headers = $this->auth($account['token']);
        $all = $this->api->get('/v1/profiles/' . $profileId . '/objects', $headers)->json()['objects'];
        self::assertCount(3, $all);
        foreach ($all as $metadata) {
            self::assertSame(
                ['namespace', 'key', 'etag', 'schemaVersion', 'compressedSize', 'updatedAt'],
                array_keys($metadata),
            );
            self::assertArrayNotHasKey('payload', $metadata);
        }

        $favorites = $this->api->get(
            '/v1/profiles/' . $profileId . '/objects?namespace=favorites',
            $headers,
        )->json()['objects'];
        self::assertCount(1, $favorites);
        self::assertSame('favorites', $favorites[0]['namespace']);
    }

    public function testEtagIsDeterministicAndChangesOnlyWithPayloadBytes(): void
    {
        $account = $this->createAccount();
        $profileId = $account['profileIds'][0];
        $payloadA = (string) gzencode('{"version":"A"}');
        $payloadB = (string) gzencode('{"version":"B"}');

        $first = $this->putObject($account['token'], $profileId, 'playback', 'movie-1', $payloadA);
        $same = $this->putObject($account['token'], $profileId, 'playback', 'movie-1', $payloadA);
        $different = $this->putObject($account['token'], $profileId, 'playback', 'movie-1', $payloadB);

        self::assertSame($first->header('etag'), $same->header('etag'));
        self::assertNotSame($first->header('etag'), $different->header('etag'));
        self::assertSame($different->header('etag'), $this->api->get(
            '/v1/profiles/' . $profileId . '/objects/playback/movie-1',
            $this->auth($account['token']),
        )->header('etag'));
        self::assertSame(3, (int) $this->pdo->query('SELECT COUNT(*) FROM sync_changes')->fetchColumn());
    }

    public function testStaleIfMatchAllowsOnlyFirstClientUpdate(): void
    {
        $account = $this->createAccount();
        $profileId = $account['profileIds'][0];
        $etagA = $this->putObject(
            $account['token'],
            $profileId,
            'favorites',
            'movie-1',
            (string) gzencode('A'),
        )->header('etag');

        $clientOne = $this->putObject(
            $account['token'],
            $profileId,
            'favorites',
            'movie-1',
            (string) gzencode('B'),
            ['If-Match' => $etagA],
        );
        self::assertSame(204, $clientOne->status);
        self::assertNotSame($etagA, $clientOne->header('etag'));

        $clientTwo = $this->putObject(
            $account['token'],
            $profileId,
            'favorites',
            'movie-1',
            (string) gzencode('C'),
            ['If-Match' => $etagA],
        );
        $this->assertError($clientTwo, 412, 'ETAG_MISMATCH');
    }

    public function testDeleteWithIfMatchIsIdempotentAndProducesOneTombstone(): void
    {
        $account = $this->createAccount();
        $profileId = $account['profileIds'][0];
        $path = '/v1/profiles/' . $profileId . '/objects/favorites/movie-12345';
        $etag = $this->putObject(
            $account['token'], $profileId, 'favorites', 'movie-12345', (string) gzencode('favorite'),
        )->header('etag');

        $this->assertError(
            $this->api->delete($path, $this->auth($account['token']) + ['If-Match' => '"wrong"']),
            412,
            'ETAG_MISMATCH',
        );
        self::assertSame(204, $this->api->delete(
            $path,
            $this->auth($account['token']) + ['If-Match' => $etag],
        )->status);
        $this->assertError($this->api->get($path, $this->auth($account['token'])), 404, 'OBJECT_NOT_FOUND');

        $revisionCount = (int) $this->pdo->query('SELECT COUNT(*) FROM sync_changes')->fetchColumn();
        self::assertSame(204, $this->api->delete($path, $this->auth($account['token']))->status);
        self::assertSame($revisionCount, (int) $this->pdo->query('SELECT COUNT(*) FROM sync_changes')->fetchColumn());

        $changes = $this->api->get('/v1/sync/changes?cursor=1', $this->auth($account['token']))->json()['changes'];
        self::assertCount(1, $changes);
        self::assertSame('DELETE', $changes[0]['operation']);
        self::assertSame('favorites', $changes[0]['namespace']);
        self::assertSame('movie-12345', $changes[0]['key']);
    }

    public function testPayloadLimitUsesActualReceivedBytes(): void
    {
        $account = $this->createAccount();
        $profileId = $account['profileIds'][0];
        $accepted = $this->putObject(
            $account['token'], $profileId, 'opaque', 'at-limit', str_repeat('a', $this->config->maxObjectSizeBytes),
        );
        self::assertSame(204, $accepted->status);

        $rejected = $this->putObject(
            $account['token'],
            $profileId,
            'opaque',
            'over-limit',
            str_repeat('b', $this->config->maxObjectSizeBytes + 1),
            ['X-Compressed-Size' => '1'],
        );
        $this->assertError($rejected, 413, 'PAYLOAD_TOO_LARGE');
        self::assertSame(0, (int) $this->pdo->query("SELECT COUNT(*) FROM profile_objects WHERE object_key = 'over-limit'")->fetchColumn());
    }

    /** @return list<array{string}> */
    public static function invalidSchemaVersionProvider(): array
    {
        return [[''], ['0'], ['-1'], ['abc'], ['1.5'], ['2147483648']];
    }

    #[DataProvider('invalidSchemaVersionProvider')]
    public function testInvalidOrMissingSchemaVersionIsRejected(string $version): void
    {
        $account = $this->createAccount();
        $headers = $this->auth($account['token']) + ['Content-Type' => 'application/vnd.cstv.blob+gzip'];
        if ($version !== '') {
            $headers['X-Schema-Version'] = $version;
        }
        $response = $this->api->putBinary(
            '/v1/profiles/' . $account['profileIds'][0] . '/objects/favorites/movie-1',
            (string) gzencode('x'),
            $headers,
        );

        $this->assertError($response, 422, 'INVALID_SCHEMA_VERSION');
    }

    public function testNamespaceAndObjectKeyValidationRemainGenericAndSafe(): void
    {
        $account = $this->createAccount();
        $profileId = $account['profileIds'][0];
        self::assertSame(204, $this->putObject(
            $account['token'], $profileId, 'future-domain.v2', 'arbitrary:key-1', (string) gzencode('future'),
        )->status);

        foreach ([
            [str_repeat('n', 65), 'key', 'INVALID_NAMESPACE'],
            ['Favorites', 'key', 'INVALID_NAMESPACE'],
            ['valid', str_repeat('k', 129), 'INVALID_OBJECT_KEY'],
            ['valid', '-danger', 'INVALID_OBJECT_KEY'],
        ] as [$namespace, $key, $code]) {
            $this->assertError(
                $this->putObject($account['token'], $profileId, $namespace, $key, (string) gzencode('x')),
                422,
                $code,
            );
        }
    }

    public function testWrongContentTypeIsRejected(): void
    {
        $account = $this->createAccount();
        $response = $this->api->putBinary(
            '/v1/profiles/' . $account['profileIds'][0] . '/objects/favorites/movie-1',
            '{}',
            $this->auth($account['token']) + ['Content-Type' => 'application/json', 'X-Schema-Version' => '1'],
        );
        $this->assertError($response, 415, 'UNSUPPORTED_MEDIA_TYPE');
    }
}
