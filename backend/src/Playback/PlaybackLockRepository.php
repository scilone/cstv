<?php

declare(strict_types=1);

namespace Cstv\Backend\Playback;

use PDO;

final readonly class PlaybackLockRepository
{
    public function __construct(private PDO $pdo) {}

    /** @return array<string, mixed>|null */
    public function findForUpdate(string $accountId, int $ttlSeconds): ?array
    {
        $statement = $this->pdo->prepare("SELECT account_id, device_id, device_name, lock_token, started_at, last_seen_at, (last_seen_at < NOW() - (:ttl_seconds * INTERVAL '1 second')) AS expired, GREATEST(0, EXTRACT(EPOCH FROM NOW() - started_at)::integer) AS held_for_seconds FROM playback_locks WHERE account_id = :account_id FOR UPDATE");
        $statement->execute(['account_id' => $accountId, 'ttl_seconds' => $ttlSeconds]);
        $row = $statement->fetch();
        return $row === false ? null : $row;
    }

    public function upsert(string $accountId, string $deviceId, string $deviceName, string $token): void
    {
        $statement = $this->pdo->prepare('INSERT INTO playback_locks (account_id, device_id, device_name, lock_token, started_at, last_seen_at) VALUES (:account_id, :device_id, :device_name, :lock_token, NOW(), NOW()) ON CONFLICT (account_id) DO UPDATE SET device_id = EXCLUDED.device_id, device_name = EXCLUDED.device_name, lock_token = EXCLUDED.lock_token, started_at = NOW(), last_seen_at = NOW()');
        $statement->execute(['account_id' => $accountId, 'device_id' => $deviceId, 'device_name' => $deviceName, 'lock_token' => $token]);
    }

    public function touch(string $accountId): void
    {
        $statement = $this->pdo->prepare('UPDATE playback_locks SET last_seen_at = NOW() WHERE account_id = :account_id');
        $statement->execute(['account_id' => $accountId]);
    }

    public function deleteOwned(string $accountId, string $token): void
    {
        $statement = $this->pdo->prepare('DELETE FROM playback_locks WHERE account_id = :account_id AND lock_token = :lock_token');
        $statement->execute(['account_id' => $accountId, 'lock_token' => $token]);
    }
}
