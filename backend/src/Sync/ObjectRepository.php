<?php

declare(strict_types=1);

namespace Cstv\Backend\Sync;

use PDO;

final readonly class ObjectRepository
{
    public function __construct(private PDO $pdo)
    {
    }

    /** @return array<string, mixed>|null */
    public function findMetadataForUpdate(string $profileId, string $namespace): ?array
    {
        $statement = $this->pdo->prepare(
            'SELECT etag, schema_version, compressed_size, created_at, updated_at '
            . 'FROM profile_objects WHERE profile_id = :profile_id AND namespace = :namespace FOR UPDATE',
        );
        $statement->execute(['profile_id' => $profileId, 'namespace' => $namespace]);
        $row = $statement->fetch();

        return $row === false ? null : $row;
    }

    public function upsert(
        string $profileId,
        string $namespace,
        string $payload,
        string $etag,
        int $schemaVersion,
    ): void {
        $statement = $this->pdo->prepare(
            'INSERT INTO profile_objects '
            . '(profile_id, namespace, payload, etag, schema_version, compressed_size, created_at, updated_at) '
            . 'VALUES (:profile_id, :namespace, :payload, :etag, :schema_version, :compressed_size, NOW(), NOW()) '
            . 'ON CONFLICT (profile_id, namespace) DO UPDATE SET '
            . 'payload = EXCLUDED.payload, etag = EXCLUDED.etag, schema_version = EXCLUDED.schema_version, '
            . 'compressed_size = EXCLUDED.compressed_size, updated_at = NOW()',
        );
        $statement->bindValue(':profile_id', $profileId);
        $statement->bindValue(':namespace', $namespace);
        $statement->bindValue(':payload', $payload, PDO::PARAM_LOB);
        $statement->bindValue(':etag', $etag);
        $statement->bindValue(':schema_version', $schemaVersion, PDO::PARAM_INT);
        $statement->bindValue(':compressed_size', strlen($payload), PDO::PARAM_INT);
        $statement->execute();
    }

    /** Number of distinct namespace snapshots already stored for a profile. */
    public function countNamespacesForProfile(string $profileId): int
    {
        $statement = $this->pdo->prepare(
            'SELECT COUNT(*) FROM profile_objects WHERE profile_id = :profile_id',
        );
        $statement->execute(['profile_id' => $profileId]);
        return (int) $statement->fetchColumn();
    }

    /** Total stored bytes across every profile of an account, for the per-account storage quota. */
    public function sumBytesForAccount(string $accountId): int
    {
        $statement = $this->pdo->prepare(
            'SELECT COALESCE(SUM(o.compressed_size), 0) FROM profile_objects o '
            . 'INNER JOIN profiles p ON p.id = o.profile_id WHERE p.account_id = :account_id',
        );
        $statement->execute(['account_id' => $accountId]);
        return (int) $statement->fetchColumn();
    }

    public function delete(string $profileId, string $namespace): void
    {
        $statement = $this->pdo->prepare(
            'DELETE FROM profile_objects WHERE profile_id = :profile_id AND namespace = :namespace',
        );
        $statement->execute(['profile_id' => $profileId, 'namespace' => $namespace]);
    }

    /** @return list<array<string, mixed>> */
    public function listMetadata(string $profileId): array
    {
        $statement = $this->pdo->prepare(
            'SELECT namespace, etag, schema_version, compressed_size, updated_at '
            . 'FROM profile_objects WHERE profile_id = :profile_id ORDER BY namespace',
        );
        $statement->execute(['profile_id' => $profileId]);

        /** @var list<array<string, mixed>> $rows */
        $rows = $statement->fetchAll();
        return $rows;
    }

    /** @return array<string, mixed>|null */
    public function getOwned(string $accountId, string $profileId, string $namespace): ?array
    {
        $statement = $this->pdo->prepare(
            'SELECT o.payload, o.etag, o.schema_version, o.compressed_size, o.updated_at '
            . 'FROM profile_objects o INNER JOIN profiles p ON p.id = o.profile_id '
            . 'WHERE p.account_id = :account_id AND o.profile_id = :profile_id AND o.namespace = :namespace',
        );
        $statement->execute([
            'account_id' => $accountId,
            'profile_id' => $profileId,
            'namespace' => $namespace,
        ]);
        $row = $statement->fetch();
        if ($row === false) {
            return null;
        }

        if (is_resource($row['payload'])) {
            $payload = stream_get_contents($row['payload']);
            $row['payload'] = $payload === false ? '' : $payload;
        }

        return $row;
    }
}
