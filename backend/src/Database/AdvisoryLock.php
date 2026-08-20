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

    /**
     * Serializes every quota-bearing mutation of one account (profile creation, snapshot writes).
     * A per-account lock is required because those checks aggregate over rows the caller does not
     * hold — a row-level FOR UPDATE cannot stop a concurrent phantom insert from slipping past the
     * count. Always taken before the finer namespace lock to keep a single lock order.
     */
    public static function account(PDO $pdo, string $accountId): void
    {
        self::acquire($pdo, sprintf('account:%s', $accountId));
    }

    /** Serializes the verify throttle admission (purge/count/insert) for one client IP. */
    public static function verifyIp(PDO $pdo, string $ipKey): void
    {
        self::acquire($pdo, sprintf('verify-ip:%s', $ipKey));
    }

    /**
     * F45-R8 : single-flight pour une saison — `CatalogService::season()` ne passe pas par
     * `CatalogService::resolve()` (sa mise en cache/verrou générique), donc deux installations
     * ouvrant la même fiche série au même instant pouvaient toutes deux détailer le fournisseur puis
     * remplacer les épisodes en parallèle, sans transaction commune. Le second appelant, bloqué ici
     * jusqu'au COMMIT du premier, retrouve ensuite une saison déjà fraîche et n'a plus besoin
     * d'appeler le fournisseur du tout.
     */
    public static function season(PDO $pdo, string $seriesExternalId, int $seasonNumber): void
    {
        self::acquire($pdo, sprintf('season:%s:%d', $seriesExternalId, $seasonNumber));
    }

    private static function acquire(PDO $pdo, string $key): void
    {
        $statement = $pdo->prepare('SELECT pg_advisory_xact_lock(hashtextextended(:lock_key, 0))');
        $statement->execute(['lock_key' => $key]);
    }
}
