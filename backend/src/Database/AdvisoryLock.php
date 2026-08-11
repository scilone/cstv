<?php

declare(strict_types=1);

namespace Cstv\Backend\Database;

use PDO;

/**
 * Transaction scoped PostgreSQL advisory locks.
 *
 * Every mutation takes them in the same order — account journal first, then object —
 * so two concurrent requests can never deadlock against each other.
 */
final class AdvisoryLock
{
    /**
     * Serializes the sync_changes appends of one account.
     *
     * BIGSERIAL hands out a revision at INSERT time, not at COMMIT time. Without this lock two
     * concurrent writes can commit in the reverse order of their revisions: a client that reads
     * the journal in between advances its cursor past a revision that is still uncommitted and
     * never sees it again. Holding the lock until COMMIT makes revision order = commit order.
     */
    public static function accountJournal(PDO $pdo, string $accountId): void
    {
        self::acquire($pdo, 'sync:' . $accountId);
    }

    /** Serializes concurrent PUT/DELETE on the same object key so If-Match stays authoritative. */
    public static function object(PDO $pdo, string $profileId, string $namespace, string $objectKey): void
    {
        self::acquire($pdo, sprintf('object:%s:%s:%s', $profileId, $namespace, $objectKey));
    }

    private static function acquire(PDO $pdo, string $key): void
    {
        $statement = $pdo->prepare('SELECT pg_advisory_xact_lock(hashtextextended(:lock_key, 0))');
        $statement->execute(['lock_key' => $key]);
    }
}
