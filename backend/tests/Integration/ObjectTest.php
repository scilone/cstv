<?php

declare(strict_types=1);

namespace Cstv\Backend\Tests\Integration;

use PHPUnit\Framework\Attributes\DataProvider;

final class ObjectTest extends IntegrationTestCase
{
    public function testNamespaceSnapshotRoundTripIsByteExactAndMetadataDoesNotLoadPayload(): void
    {
        $account = $this->createAccount();
        $profileId = $account['profileIds'][0];
        $payload = gzencode('{"schemaVersion":1,"objects":{"movie-1":{"title":"Cinéma 🎬"}}}', 6);
        self::assertNotFalse($payload);

        $put = $this->putObject($account['token'], $profileId, 'favorites', $payload);
        self::assertSame(204, $put->getStatusCode());
        self::assertSame('"' . hash('sha256', $payload) . '"', $put->getHeaderLine('ETag'));

        $get = $this->request('GET', "/v1/profiles/{$profileId}/objects/favorites", '', $this->auth($account['token']));
        self::assertSame(200, $get->getStatusCode());
        self::assertSame($payload, (string) $get->getBody());
        self::assertSame('application/vnd.cstv.blob+gzip', $get->getHeaderLine('Content-Type'));

        $listed = $this->json($this->request(
            'GET', "/v1/profiles/{$profileId}/objects", '', $this->auth($account['token']),
        ))['objects'];
        self::assertSame(['namespace', 'etag', 'schemaVersion', 'compressedSize', 'updatedAt'], array_keys($listed[0]));
        self::assertSame(strlen($payload), $listed[0]['compressedSize']);
        self::assertArrayNotHasKey('payload', $listed[0]);
        self::assertArrayNotHasKey('key', $listed[0]);
    }

    public function testExistingSnapshotRequiresIfMatchAndRejectsStaleEtag(): void
    {
        $account = $this->createAccount();
        $profileId = $account['profileIds'][0];
        $etagA = $this->putObject($account['token'], $profileId, 'playback', (string) gzencode('A'))
            ->getHeaderLine('ETag');

        $missing = $this->putObject($account['token'], $profileId, 'playback', (string) gzencode('B'));
        self::assertSame(428, $missing->getStatusCode());
        self::assertSame('PRECONDITION_REQUIRED', $this->json($missing)['error']['code']);

        $updated = $this->putObject(
            $account['token'], $profileId, 'playback', (string) gzencode('B'), ['If-Match' => $etagA],
        );
        self::assertSame(204, $updated->getStatusCode());

        $stale = $this->putObject(
            $account['token'], $profileId, 'playback', (string) gzencode('C'), ['If-Match' => $etagA],
        );
        self::assertSame(412, $stale->getStatusCode());
        self::assertSame('ETAG_MISMATCH', $this->json($stale)['error']['code']);
    }

    public function testIfMatchStarRequiresExistingSnapshot(): void
    {
        $account = $this->createAccount();
        $profileId = $account['profileIds'][0];
        self::assertSame(412, $this->putObject(
            $account['token'], $profileId, 'ratings', (string) gzencode('A'), ['If-Match' => '*'],
        )->getStatusCode());
        $this->putObject($account['token'], $profileId, 'ratings', (string) gzencode('A'));
        self::assertSame(204, $this->putObject(
            $account['token'], $profileId, 'ratings', (string) gzencode('B'), ['If-Match' => '*'],
        )->getStatusCode());
    }

    public function testSamePayloadWithCurrentEtagKeepsDeterministicEtag(): void
    {
        $account = $this->createAccount();
        $profileId = $account['profileIds'][0];
        $payload = (string) gzencode('stable');
        $first = $this->putObject($account['token'], $profileId, 'favorites', $payload);
        $second = $this->putObject(
            $account['token'], $profileId, 'favorites', $payload, ['If-Match' => $first->getHeaderLine('ETag')],
        );
        self::assertSame($first->getHeaderLine('ETag'), $second->getHeaderLine('ETag'));
        self::assertSame(1, (int) $this->pdo->query('SELECT COUNT(*) FROM profile_objects')->fetchColumn());
    }

    public function testDeleteRequiresCurrentEtagThenIsIdempotentWhenAbsent(): void
    {
        $account = $this->createAccount();
        $profileId = $account['profileIds'][0];
        $uri = "/v1/profiles/{$profileId}/objects/favorites";
        $etag = $this->putObject($account['token'], $profileId, 'favorites', (string) gzencode('x'))
            ->getHeaderLine('ETag');

        $missing = $this->request('DELETE', $uri, '', $this->auth($account['token']));
        self::assertSame(428, $missing->getStatusCode());
        $wrong = $this->request('DELETE', $uri, '', $this->auth($account['token']) + ['If-Match' => '"wrong"']);
        self::assertSame(412, $wrong->getStatusCode());
        self::assertSame(204, $this->request(
            'DELETE', $uri, '', $this->auth($account['token']) + ['If-Match' => $etag],
        )->getStatusCode());
        self::assertSame(204, $this->request('DELETE', $uri, '', $this->auth($account['token']))->getStatusCode());
        self::assertSame(412, $this->request(
            'DELETE', $uri, '', $this->auth($account['token']) + ['If-Match' => $etag],
        )->getStatusCode());
    }

    public function testPayloadLimitUsesBytesActuallyReceived(): void
    {
        $account = $this->createAccount();
        $profileId = $account['profileIds'][0];
        self::assertSame(204, $this->putObject(
            $account['token'], $profileId, 'opaque', str_repeat('a', $this->config->maxObjectSizeBytes),
        )->getStatusCode());
        self::assertSame(413, $this->putObject(
            $account['token'], $profileId, 'too-large', str_repeat('b', $this->config->maxObjectSizeBytes + 1),
            ['Content-Length' => '1'],
        )->getStatusCode());
        self::assertSame(0, (int) $this->pdo->query(
            "SELECT COUNT(*) FROM profile_objects WHERE namespace = 'too-large'",
        )->fetchColumn());
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
        $response = $this->request(
            'PUT', '/v1/profiles/' . $account['profileIds'][0] . '/objects/favorites', 'x', $headers,
        );
        self::assertSame(422, $response->getStatusCode());
        self::assertSame('INVALID_SCHEMA_VERSION', $this->json($response)['error']['code']);
    }

    public function testNamespaceValidationIsGenericAndSafe(): void
    {
        $account = $this->createAccount();
        $profileId = $account['profileIds'][0];
        self::assertSame(204, $this->putObject(
            $account['token'], $profileId, 'future-domain.v2', (string) gzencode('future'),
        )->getStatusCode());
        foreach ([str_repeat('n', 65), 'Favorites', '_hidden'] as $namespace) {
            $response = $this->putObject($account['token'], $profileId, $namespace, 'x');
            self::assertSame(422, $response->getStatusCode());
            self::assertSame('INVALID_NAMESPACE', $this->json($response)['error']['code']);
        }
    }
}
