<?php

declare(strict_types=1);

namespace Cstv\Backend\Database;

use PDO;

/**
 * Transaction scoped PostgreSQL advisory locks.
 *
 * The namespace lock is held until COMMIT so If-Match remains authoritative even
 * when two PHP-FPM workers update the same snapshot concurrently.
 */
final class AdvisoryLock
{
    /** Serializes concurrent PUT/DELETE on the same namespace snapshot. */
    public static function namespace(PDO $pdo, string $profileId, string $namespace): void
    {
        self::acquire($pdo, sprintf('namespace:%s:%s', $profileId, $namespace));
    }

    private static function acquire(PDO $pdo, string $key): void
    {
        $statement = $pdo->prepare('SELECT pg_advisory_xact_lock(hashtextextended(:lock_key, 0))');
        $statement->execute(['lock_key' => $key]);
    }
}
