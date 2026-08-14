<?php

declare(strict_types=1);

namespace Cstv\Backend\Account;

use PDO;

final readonly class IptvCredentialsRepository
{
    public function __construct(private PDO $pdo) {}

    /** @return array<string, mixed>|null */
    public function findForUpdate(string $accountId): ?array
    {
        $statement = $this->pdo->prepare('SELECT account_id, key_id, payload, etag, created_at, updated_at FROM account_iptv_credentials WHERE account_id = :account_id FOR UPDATE');
        $statement->execute(['account_id' => $accountId]);
        return $this->row($statement->fetch());
    }

    /** @return array<string, mixed>|null */
    public function find(string $accountId): ?array
    {
        $statement = $this->pdo->prepare('SELECT account_id, key_id, payload, etag, created_at, updated_at FROM account_iptv_credentials WHERE account_id = :account_id');
        $statement->execute(['account_id' => $accountId]);
        return $this->row($statement->fetch());
    }

    public function upsert(string $accountId, string $keyId, string $payload, string $etag): void
    {
        $statement = $this->pdo->prepare('INSERT INTO account_iptv_credentials (account_id, key_id, payload, etag, created_at, updated_at) VALUES (:account_id, :key_id, :payload, :etag, NOW(), NOW()) ON CONFLICT (account_id) DO UPDATE SET key_id = EXCLUDED.key_id, payload = EXCLUDED.payload, etag = EXCLUDED.etag, updated_at = NOW()');
        $statement->bindValue(':account_id', $accountId);
        $statement->bindValue(':key_id', $keyId);
        $statement->bindValue(':payload', $payload, PDO::PARAM_LOB);
        $statement->bindValue(':etag', $etag);
        $statement->execute();
    }

    public function delete(string $accountId): void
    {
        $statement = $this->pdo->prepare('DELETE FROM account_iptv_credentials WHERE account_id = :account_id');
        $statement->execute(['account_id' => $accountId]);
    }

    /** @return array<string, mixed>|null */
    private function row(mixed $row): ?array
    {
        if ($row === false) return null;
        if (is_resource($row['payload'])) $row['payload'] = stream_get_contents($row['payload']) ?: '';
        return $row;
    }
}
