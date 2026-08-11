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
    public function findMetadataForUpdate(string $profileId, string $namespace, string $objectKey): ?array
    {
        $statement = $this->pdo->prepare(
            'SELECT etag, schema_version, compressed_size, created_at, updated_at '
            . 'FROM profile_objects WHERE profile_id = :profile_id AND namespace = :namespace '
            . 'AND object_key = :object_key FOR UPDATE',
        );
        $statement->execute([
            'profile_id' => $profileId,
            'namespace' => $namespace,
            'object_key' => $objectKey,
        ]);
        $row = $statement->fetch();

        return $row === false ? null : $row;
    }

    public function upsert(
        string $profileId,
        string $namespace,
        string $objectKey,
        string $payload,
        string $etag,
        int $schemaVersion,
    ): void {
        $statement = $this->pdo->prepare(
            'INSERT INTO profile_objects '
            . '(profile_id, namespace, object_key, payload, etag, schema_version, compressed_size, created_at, updated_at) '
            . 'VALUES (:profile_id, :namespace, :object_key, :payload, :etag, :schema_version, :compressed_size, NOW(), NOW()) '
            . 'ON CONFLICT (profile_id, namespace, object_key) DO UPDATE SET '
            . 'payload = EXCLUDED.payload, etag = EXCLUDED.etag, schema_version = EXCLUDED.schema_version, '
            . 'compressed_size = EXCLUDED.compressed_size, updated_at = NOW()',
        );
        $statement->bindValue(':profile_id', $profileId);
        $statement->bindValue(':namespace', $namespace);
        $statement->bindValue(':object_key', $objectKey);
        $statement->bindValue(':payload', $payload, PDO::PARAM_LOB);
        $statement->bindValue(':etag', $etag);
        $statement->bindValue(':schema_version', $schemaVersion, PDO::PARAM_INT);
        $statement->bindValue(':compressed_size', strlen($payload), PDO::PARAM_INT);
        $statement->execute();
    }

    public function delete(string $profileId, string $namespace, string $objectKey): void
    {
        $statement = $this->pdo->prepare(
            'DELETE FROM profile_objects WHERE profile_id = :profile_id '
            . 'AND namespace = :namespace AND object_key = :object_key',
        );
        $statement->execute([
            'profile_id' => $profileId,
            'namespace' => $namespace,
            'object_key' => $objectKey,
        ]);
    }

    public function addChange(
        string $accountId,
        string $profileId,
        string $namespace,
        string $objectKey,
        string $operation,
        ?string $etag,
    ): void {
        $statement = $this->pdo->prepare(
            'INSERT INTO sync_changes '
            . '(account_id, profile_id, namespace, object_key, operation, etag, changed_at) '
            // clock_timestamp() keeps changed_at monotonic with the BIGSERIAL revision.
            . 'VALUES (:account_id, :profile_id, :namespace, :object_key, :operation, :etag, clock_timestamp())',
        );
        $statement->execute([
            'account_id' => $accountId,
            'profile_id' => $profileId,
            'namespace' => $namespace,
            'object_key' => $objectKey,
            'operation' => $operation,
            'etag' => $etag,
        ]);
    }

    /** @return list<array<string, mixed>> */
    public function listMetadata(string $profileId, ?string $namespace): array
    {
        if ($namespace === null) {
            $statement = $this->pdo->prepare(
                'SELECT namespace, object_key, etag, schema_version, compressed_size, updated_at '
                . 'FROM profile_objects WHERE profile_id = :profile_id ORDER BY namespace, object_key',
            );
            $statement->execute(['profile_id' => $profileId]);
        } else {
            $statement = $this->pdo->prepare(
                'SELECT namespace, object_key, etag, schema_version, compressed_size, updated_at '
                . 'FROM profile_objects WHERE profile_id = :profile_id AND namespace = :namespace '
                . 'ORDER BY namespace, object_key',
            );
            $statement->execute(['profile_id' => $profileId, 'namespace' => $namespace]);
        }

        /** @var list<array<string, mixed>> $rows */
        $rows = $statement->fetchAll();
        return $rows;
    }

    /** @return array<string, mixed>|null */
    public function getOwned(
        string $accountId,
        string $profileId,
        string $namespace,
        string $objectKey,
    ): ?array {
        $statement = $this->pdo->prepare(
            'SELECT o.payload, o.etag, o.schema_version, o.compressed_size, o.updated_at '
            . 'FROM profile_objects o INNER JOIN profiles p ON p.id = o.profile_id '
            . 'WHERE p.account_id = :account_id AND o.profile_id = :profile_id '
            . 'AND o.namespace = :namespace AND o.object_key = :object_key',
        );
        $statement->execute([
            'account_id' => $accountId,
            'profile_id' => $profileId,
            'namespace' => $namespace,
            'object_key' => $objectKey,
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
