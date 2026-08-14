<?php

declare(strict_types=1);

namespace Cstv\Backend\Playback;

use Cstv\Backend\Database\AdvisoryLock;
use Cstv\Backend\Shared\DateFormatter;
use PDO;
use Throwable;

final readonly class PlaybackLockService
{
    public function __construct(private PDO $pdo, private PlaybackLockRepository $repository, private int $ttlSeconds, private int $heartbeatSeconds) {}

    /** @param array{deviceId: string, deviceName: string, takeover: bool} $device @return array{kind: string, lock?: array<string, mixed>, holder?: array<string, mixed>|null} */
    public function acquire(string $accountId, array $device): array
    {
        return $this->transaction(function () use ($accountId, $device): array {
            $current = $this->locked($accountId);
            if ($current !== null && !$this->expired($current) && (string) $current['device_id'] !== $device['deviceId'] && !$device['takeover']) {
                return ['kind' => 'held', 'holder' => $this->holder($current)];
            }
            $token = $this->uuid();
            $this->repository->upsert($accountId, $device['deviceId'], $device['deviceName'], $token);
            $updated = $this->repository->findForUpdate($accountId, $this->ttlSeconds);
            return ['kind' => 'acquired', 'lock' => $this->lock($updated ?? throw new \LogicException('Playback lock missing after upsert.'))];
        });
    }

    /** @return array{kind: string, lock?: array<string, mixed>, holder?: array<string, mixed>|null} */
    public function heartbeat(string $accountId, ?string $token): array
    {
        return $this->transaction(function () use ($accountId, $token): array {
            $current = $this->locked($accountId);
            if ($current === null || $this->expired($current) || $token === null || !hash_equals((string) $current['lock_token'], $token)) {
                return ['kind' => 'revoked', 'holder' => $current !== null && !$this->expired($current) ? $this->holder($current) : null];
            }
            $this->repository->touch($accountId);
            $updated = $this->repository->findForUpdate($accountId, $this->ttlSeconds);
            return ['kind' => 'acquired', 'lock' => $this->lock($updated ?? throw new \LogicException('Playback lock missing after touch.'))];
        });
    }

    public function release(string $accountId, ?string $token): void
    {
        if ($token === null) return;
        $this->transaction(function () use ($accountId, $token): void {
            $this->locked($accountId);
            $this->repository->deleteOwned($accountId, $token);
        });
    }

    /** @return array<string, mixed> */
    private function locked(string $accountId): array|null
    {
        AdvisoryLock::account($this->pdo, $accountId);
        return $this->repository->findForUpdate($accountId, $this->ttlSeconds);
    }

    /** @param array<string, mixed> $row */
    private function expired(array $row): bool
    {
        return (bool) $row['expired'];
    }

    /** @param array<string, mixed> $row @return array{deviceName: string, startedAt: string, heldForSeconds: int} */
    private function holder(array $row): array
    {
        return ['deviceName' => (string) $row['device_name'], 'startedAt' => DateFormatter::iso8601((string) $row['started_at']), 'heldForSeconds' => (int) $row['held_for_seconds']];
    }

    /** @param array<string, mixed> $row @return array{lockToken: string, deviceId: string, deviceName: string, startedAt: string, heartbeatSeconds: int, ttlSeconds: int} */
    private function lock(array $row): array
    {
        return ['lockToken' => (string) $row['lock_token'], 'deviceId' => (string) $row['device_id'], 'deviceName' => (string) $row['device_name'], 'startedAt' => DateFormatter::iso8601((string) $row['started_at']), 'heartbeatSeconds' => $this->heartbeatSeconds, 'ttlSeconds' => $this->ttlSeconds];
    }

    private function uuid(): string
    {
        $bytes = random_bytes(16);
        $bytes[6] = chr((ord($bytes[6]) & 0x0f) | 0x40);
        $bytes[8] = chr((ord($bytes[8]) & 0x3f) | 0x80);
        return vsprintf('%s%s-%s-%s-%s-%s%s%s', str_split(bin2hex($bytes), 4));
    }

    private function transaction(callable $operation): mixed
    {
        $this->pdo->beginTransaction();
        try {
            $result = $operation();
            $this->pdo->commit();
            return $result;
        } catch (Throwable $e) {
            if ($this->pdo->inTransaction()) $this->pdo->rollBack();
            throw $e;
        }
    }
}
