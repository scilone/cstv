<?php

declare(strict_types=1);

namespace Cstv\Backend\Tests\Functional;

use PHPUnit\Framework\Attributes\DataProvider;

final class ObjectApiTest extends FunctionalTestCase
{
    public function testFavoritesNamespaceGzipRoundTripIsStrictlyByteForByte(): void
    {
        $account = $this->createAccount();
        $profileId = $account['profileIds'][0];
        $document = [
            'schemaVersion' => 1,
            'objects' => [
                'movie-12345' => [
                    'id' => 12345,
                    'type' => 'movie',
                    'name' => 'Interstellar',
                    'cover' => 'https://example.com/interstellar.jpg',
                    'categoryId' => '42',
                    'addedAt' => 1786441680000,
                ],
            ],
        ];
        $gzip = gzencode(json_encode($document, JSON_THROW_ON_ERROR | JSON_UNESCAPED_SLASHES));
        self::assertNotFalse($gzip);

        $put = $this->putObject($account['token'], $profileId, 'favorites', $gzip);
        self::assertSame(204, $put->status);
        $expectedEtag = '"' . hash('sha256', $gzip) . '"';
        self::assertSame($expectedEtag, $put->header('etag'));

        $get = $this->api->get(
            '/v1/profiles/' . $profileId . '/objects/favorites', $this->auth($account['token']),
        );
        self::assertSame(200, $get->status);
        self::assertSame('application/vnd.cstv.blob+gzip', $get->header('content-type'));
        self::assertSame($expectedEtag, $get->header('etag'));
        self::assertSame($gzip, $get->body);

        $metadata = $this->api->get(
            '/v1/profiles/' . $profileId . '/objects', $this->auth($account['token']),
        )->json()['objects'][0];
        self::assertSame(['namespace', 'etag', 'schemaVersion', 'compressedSize', 'updatedAt'], array_keys($metadata));
        self::assertSame(strlen($gzip), $metadata['compressedSize']);
    }

    public function testUnicodeAndBinaryBytesSurviveNginxPhpFpmSlimAndPostgresql(): void
    {
        $account = $this->createAccount();
        $profileId = $account['profileIds'][0];
        $gzip = gzencode(json_encode([
            'schemaVersion' => 1,
            'objects' => ['unicode' => ['title' => 'Le Château ambulant 🎬', 'text' => "日本語\0العربية"]],
        ], JSON_THROW_ON_ERROR | JSON_UNESCAPED_UNICODE));
        self::assertNotFalse($gzip);

        self::assertSame(204, $this->putObject($account['token'], $profileId, 'future-data', $gzip)->status);
        self::assertSame($gzip, $this->api->get(
            '/v1/profiles/' . $profileId . '/objects/future-data', $this->auth($account['token']),
        )->body);
    }

    public function testListingReturnsOneMetadataEntryPerNamespaceWithoutPayloadOrKey(): void
    {
        $account = $this->createAccount();
        $profileId = $account['profileIds'][0];
        foreach (['favorites', 'playback', 'ratings'] as $namespace) {
            $this->putObject($account['token'], $profileId, $namespace, (string) gzencode($namespace));
        }

        $listed = $this->api->get(
            '/v1/profiles/' . $profileId . '/objects', $this->auth($account['token']),
        )->json()['objects'];
        self::assertSame(['favorites', 'playback', 'ratings'], array_column($listed, 'namespace'));
        foreach ($listed as $metadata) {
            self::assertArrayNotHasKey('payload', $metadata);
            self::assertArrayNotHasKey('key', $metadata);
        }
    }

    public function testExistingSnapshotCannotBeBlindlyOverwritten(): void
    {
        $account = $this->createAccount();
        $profileId = $account['profileIds'][0];
        $etagA = $this->putObject($account['token'], $profileId, 'playback', (string) gzencode('A'))->header('etag');

        $this->assertError(
            $this->putObject($account['token'], $profileId, 'playback', (string) gzencode('B')),
            428,
            'PRECONDITION_REQUIRED',
        );
        $updated = $this->putObject(
            $account['token'], $profileId, 'playback', (string) gzencode('B'), ['If-Match' => $etagA],
        );
        self::assertSame(204, $updated->status);
        self::assertNotSame($etagA, $updated->header('etag'));
        $this->assertError(
            $this->putObject(
                $account['token'], $profileId, 'playback', (string) gzencode('C'), ['If-Match' => $etagA],
            ),
            412,
            'ETAG_MISMATCH',
        );
    }

    public function testDeleteRequiresCurrentEtagAndIsIdempotentAfterDeletion(): void
    {
        $account = $this->createAccount();
        $profileId = $account['profileIds'][0];
        $path = '/v1/profiles/' . $profileId . '/objects/favorites';
        $etag = $this->putObject($account['token'], $profileId, 'favorites', (string) gzencode('favorite'))
            ->header('etag');

        $this->assertError($this->api->delete($path, $this->auth($account['token'])), 428, 'PRECONDITION_REQUIRED');
        $this->assertError(
            $this->api->delete($path, $this->auth($account['token']) + ['If-Match' => '"wrong"']),
            412,
            'ETAG_MISMATCH',
        );
        self::assertSame(204, $this->api->delete(
            $path, $this->auth($account['token']) + ['If-Match' => $etag],
        )->status);
        $this->assertError($this->api->get($path, $this->auth($account['token'])), 404, 'OBJECT_NOT_FOUND');
        self::assertSame(204, $this->api->delete($path, $this->auth($account['token']))->status);
    }

    public function testPayloadLimitUsesActualReceivedBytes(): void
    {
        $account = $this->createAccount();
        $profileId = $account['profileIds'][0];
        self::assertSame(204, $this->putObject(
            $account['token'], $profileId, 'opaque', str_repeat('a', $this->config->maxObjectSizeBytes),
        )->status);
        $this->assertError($this->putObject(
            $account['token'], $profileId, 'too-large', str_repeat('b', $this->config->maxObjectSizeBytes + 1),
            ['X-Compressed-Size' => '1'],
        ), 413, 'PAYLOAD_TOO_LARGE');
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
            '/v1/profiles/' . $account['profileIds'][0] . '/objects/favorites', 'x', $headers,
        );
        $this->assertError($response, 422, 'INVALID_SCHEMA_VERSION');
    }

    public function testNamespaceValidationRemainsGenericAndSafe(): void
    {
        $account = $this->createAccount();
        $profileId = $account['profileIds'][0];
        self::assertSame(204, $this->putObject(
            $account['token'], $profileId, 'future-domain.v2', (string) gzencode('future'),
        )->status);
        foreach ([str_repeat('n', 65), 'Favorites', '_hidden'] as $namespace) {
            $this->assertError(
                $this->putObject($account['token'], $profileId, $namespace, 'x'), 422, 'INVALID_NAMESPACE',
            );
        }
    }

    public function testWrongContentTypeIsRejected(): void
    {
        $account = $this->createAccount();
        $response = $this->api->putBinary(
            '/v1/profiles/' . $account['profileIds'][0] . '/objects/favorites',
            '{}',
            $this->auth($account['token']) + ['Content-Type' => 'application/json', 'X-Schema-Version' => '1'],
        );
        $this->assertError($response, 415, 'UNSUPPORTED_MEDIA_TYPE');
    }

    public function testLegacySyncJournalAndObjectKeyRoutesAreRemoved(): void
    {
        $account = $this->createAccount();
        $headers = $this->auth($account['token']);

        self::assertSame(404, $this->api->get('/v1/sync/changes', $headers)->status);
        self::assertSame(404, $this->api->get(
            '/v1/profiles/' . $account['profileIds'][0] . '/objects/favorites/movie-1',
            $headers,
        )->status);
    }
}
