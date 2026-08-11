<?php

declare(strict_types=1);

namespace Cstv\Backend\Sync;

use PDO;

final readonly class SyncRepository
{
    public function __construct(private PDO $pdo)
    {
    }

    /** @return list<array<string, mixed>> */
    public function changes(string $accountId, int $cursor, int $limitPlusOne): array
    {
        $statement = $this->pdo->prepare(
            'SELECT revision, profile_id, namespace, object_key, operation, etag, changed_at '
            . 'FROM sync_changes WHERE account_id = :account_id AND revision > :cursor '
            . 'ORDER BY revision ASC LIMIT :limit',
        );
        $statement->bindValue(':account_id', $accountId);
        $statement->bindValue(':cursor', $cursor, PDO::PARAM_INT);
        $statement->bindValue(':limit', $limitPlusOne, PDO::PARAM_INT);
        $statement->execute();

        /** @var list<array<string, mixed>> $rows */
        $rows = $statement->fetchAll();
        return $rows;
    }
}
