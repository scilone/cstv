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

    public function testTwoSimultaneousProfileCreationsCannotExceedTheLimit(): void
    {
        // php-test sets MAX_PROFILES_PER_ACCOUNT=10; seed nine, then race two for the last slot.
        $account = $this->createAccount('race-profile-cap@example.com', profileCount: 9);
        $headers = ['Authorization' => 'Bearer ' . $account['token'], 'Content-Type' => 'application/json'];
        $body = json_encode(['name' => 'Extra', 'avatarId' => 0], JSON_THROW_ON_ERROR);

        $results = $this->parallelRequests([
            ['method' => 'POST', 'path' => '/v1/profiles', 'body' => $body, 'headers' => $headers],
            ['method' => 'POST', 'path' => '/v1/profiles', 'body' => $body, 'headers' => $headers],
        ]);

        self::assertSame([201, 409], $this->statuses($results));
        self::assertSame(10, (int) $this->pdo->query(
            "SELECT COUNT(*) FROM profiles WHERE account_id = '{$account['id']}'",
        )->fetchColumn());
    }

    public function testTwoSimultaneousPutsCannotBothExceedAccountStorage(): void
    {
        // php-test sets MAX_STORAGE_BYTES_PER_ACCOUNT=1500: two 900-byte blobs cannot both fit.
        $account = $this->createAccount('race-storage@example.com');
        $profileId = $account['profileIds'][0];
        $headers = [
            'Authorization' => 'Bearer ' . $account['token'],
            'Content-Type' => 'application/vnd.cstv.blob+gzip',
            'X-Schema-Version' => '1',
        ];
        $blob = str_repeat('x', 900);

        $results = $this->parallelRequests([
            ['method' => 'PUT', 'path' => "/v1/profiles/{$profileId}/objects/favorites", 'body' => $blob, 'headers' => $headers],
            ['method' => 'PUT', 'path' => "/v1/profiles/{$profileId}/objects/playback", 'body' => $blob, 'headers' => $headers],
        ]);

        self::assertSame([204, 413], $this->statuses($results));
        self::assertSame(900, (int) $this->pdo->query(
            "SELECT COALESCE(SUM(o.compressed_size), 0) FROM profile_objects o "
            . "INNER JOIN profiles p ON p.id = o.profile_id WHERE p.account_id = '{$account['id']}'",
        )->fetchColumn());
    }

    public function testStorageQuotaIsSharedAcrossProfilesUnderConcurrency(): void
    {
        // The account-wide quota must hold even when the two writes target different profiles.
        $account = $this->createAccount('race-storage-cross@example.com', profileCount: 2);
        $headers = [
            'Authorization' => 'Bearer ' . $account['token'],
            'Content-Type' => 'application/vnd.cstv.blob+gzip',
            'X-Schema-Version' => '1',
        ];
        $blob = str_repeat('x', 900);

        $results = $this->parallelRequests([
            ['method' => 'PUT', 'path' => "/v1/profiles/{$account['profileIds'][0]}/objects/favorites", 'body' => $blob, 'headers' => $headers],
            ['method' => 'PUT', 'path' => "/v1/profiles/{$account['profileIds'][1]}/objects/favorites", 'body' => $blob, 'headers' => $headers],
        ]);

        self::assertSame([204, 413], $this->statuses($results));
    }

    public function testTwoSimultaneousPutsCannotBothTakeTheLastNamespaceSlot(): void
    {
        // php-test sets MAX_NAMESPACES_PER_PROFILE=5; seed four, then race two new namespaces.
        $account = $this->createAccount('race-namespace@example.com');
        $profileId = $account['profileIds'][0];
        $headers = [
            'Authorization' => 'Bearer ' . $account['token'],
            'Content-Type' => 'application/vnd.cstv.blob+gzip',
            'X-Schema-Version' => '1',
        ];
        foreach (['favorites', 'playback', 'ratings', 'track-preferences'] as $namespace) {
            self::assertSame(204, $this->parallelRequests([
                ['method' => 'PUT', 'path' => "/v1/profiles/{$profileId}/objects/{$namespace}", 'body' => (string) gzencode('x'), 'headers' => $headers],
            ])[0]['status']);
        }

        $results = $this->parallelRequests([
            ['method' => 'PUT', 'path' => "/v1/profiles/{$profileId}/objects/series-watch-state", 'body' => (string) gzencode('x'), 'headers' => $headers],
            ['method' => 'PUT', 'path' => "/v1/profiles/{$profileId}/objects/category-preferences", 'body' => (string) gzencode('x'), 'headers' => $headers],
        ]);

        self::assertSame([204, 409], $this->statuses($results));
        self::assertSame(5, (int) $this->pdo->query(
            "SELECT COUNT(*) FROM profile_objects WHERE profile_id = '{$profileId}'",
        )->fetchColumn());
    }

    public function testConcurrentVerificationsCannotExceedTheIpThrottle(): void
    {
        // php-test sets OTP_VERIFY_LIMIT_IP=15. Fire well above it from one IP; the atomic
        // admission must never let more than the limit through, however the burst interleaves.
        $request = [
            'method' => 'POST',
            'path' => '/v1/auth/otp/verify',
            'body' => json_encode(['email' => 'race-verify@example.com', 'code' => '000000'], JSON_THROW_ON_ERROR),
            'headers' => ['Content-Type' => 'application/json'],
        ];
        $results = $this->parallelRequests(array_fill(0, 30, $request));

        $statuses = array_map(static fn (array $r): int => $r['status'], $results);
        $throttled = count(array_filter($statuses, static fn (int $s): bool => $s === 429));
        $admitted = count($statuses) - $throttled;

        self::assertLessThanOrEqual(15, $admitted, 'throttle admitted more than the limit');
        self::assertGreaterThan(0, $throttled, 'the throttle never engaged');
        self::assertLessThanOrEqual(15, (int) $this->pdo->query(
            'SELECT COUNT(*) FROM auth_verify_attempts',
        )->fetchColumn());
    }

}
