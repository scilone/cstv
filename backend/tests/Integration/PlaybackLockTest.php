<?php

declare(strict_types=1);

namespace Cstv\Backend\Tests\Integration;

final class PlaybackLockTest extends IntegrationTestCase
{
    private const PATH = '/v1/account/playback-lock';

    /** @return array{deviceId: string, deviceName: string, takeover: bool} */
    private function device(string $id, bool $takeover = false): array
    {
        return ['deviceId' => $id, 'deviceName' => 'Test device', 'takeover' => $takeover];
    }

    public function testAcquisitionConflictTakeoverAndOldTokenRevocation(): void
    {
        $account = $this->createAccount();
        $first = $this->jsonRequest('POST', self::PATH, $this->device('11111111-1111-4111-8111-111111111111'), $this->auth($account['token']));
        self::assertSame(200, $first->getStatusCode());
        $firstLock = $this->json($first);

        $conflict = $this->jsonRequest('POST', self::PATH, $this->device('22222222-2222-4222-8222-222222222222'), $this->auth($account['token']));
        self::assertSame(409, $conflict->getStatusCode());
        self::assertSame('PLAYBACK_LOCK_HELD', $this->json($conflict)['code']);

        $takeover = $this->jsonRequest('POST', self::PATH, $this->device('22222222-2222-4222-8222-222222222222', true), $this->auth($account['token']));
        self::assertSame(200, $takeover->getStatusCode());
        self::assertNotSame($firstLock['lockToken'], $this->json($takeover)['lockToken']);

        $revoked = $this->request('POST', self::PATH . '/heartbeat', '', $this->auth($account['token']) + ['If-Match' => '"' . $firstLock['lockToken'] . '"']);
        self::assertSame(409, $revoked->getStatusCode());
        self::assertSame('PLAYBACK_LOCK_REVOKED', $this->json($revoked)['code']);
    }

    public function testExpiredLockCanBeTakenAndReleaseIsIdempotent(): void
    {
        $account = $this->createAccount();
        $first = $this->jsonRequest('POST', self::PATH, $this->device('11111111-1111-4111-8111-111111111111'), $this->auth($account['token']));
        $token = $this->json($first)['lockToken'];
        $this->pdo->exec("UPDATE playback_locks SET last_seen_at = NOW() - INTERVAL '91 seconds'");

        $second = $this->jsonRequest('POST', self::PATH, $this->device('22222222-2222-4222-8222-222222222222'), $this->auth($account['token']));
        self::assertSame(200, $second->getStatusCode());
        self::assertNotSame($token, $this->json($second)['lockToken']);
        self::assertSame(204, $this->request('DELETE', self::PATH, '', $this->auth($account['token']) + ['If-Match' => '"' . $token . '"'])->getStatusCode());
        self::assertSame(1, (int) $this->pdo->query('SELECT COUNT(*) FROM playback_locks')->fetchColumn());
    }

    public function testSameDeviceReacquiresItsLockAndAccountOperationsStayIsolated(): void
    {
        $firstAccount = $this->createAccount('user1@example.com');
        $secondAccount = $this->createAccount('user2@example.com');
        $device = $this->device('11111111-1111-4111-8111-111111111111');
        $first = $this->jsonRequest('POST', self::PATH, $device, $this->auth($firstAccount['token']));
        $firstToken = $this->json($first)['lockToken'];

        $reacquired = $this->jsonRequest('POST', self::PATH, $device, $this->auth($firstAccount['token']));
        self::assertSame(200, $reacquired->getStatusCode());
        self::assertNotSame($firstToken, $this->json($reacquired)['lockToken']);

        $second = $this->jsonRequest('POST', self::PATH, $this->device('22222222-2222-4222-8222-222222222222'), $this->auth($secondAccount['token']));
        self::assertSame(200, $second->getStatusCode());
        self::assertSame(200, $this->request('POST', self::PATH . '/heartbeat', '', $this->auth($secondAccount['token']) + ['If-Match' => '"' . $this->json($second)['lockToken'] . '"'])->getStatusCode());
        self::assertSame(204, $this->request('DELETE', self::PATH, '', $this->auth($secondAccount['token']) + ['If-Match' => '"' . $firstToken . '"'])->getStatusCode());
        self::assertSame(2, (int) $this->pdo->query('SELECT COUNT(*) FROM playback_locks')->fetchColumn());
    }
}
