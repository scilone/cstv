<?php

declare(strict_types=1);

namespace Cstv\Backend\Catalog;

use Cstv\Backend\Shared\ApiException;
use PDO;
use Throwable;

/**
 * F45 §8.16: global, conservative token bucket shared by every installation, protecting the one
 * TMDB token this backend holds. ~2 req/s sustained, small bounded burst (~5) — deliberately not
 * tied to any specific provider quota (§7.3 T22 dependency note: replaceable without a contract
 * change). A single shared row (`catalog_provider_rate_limit`) is refilled and consumed by one
 * atomic `UPDATE ... FROM (refill CTE) ... RETURNING` statement.
 *
 * F45-R1 : `acquire()` used to open its own `beginTransaction()`/`commit()`. `CatalogService::match()`
 * already runs inside its own transaction on the same `PDO` before calling down into this class
 * through the provider — a second `beginTransaction()` on an already-open PDO transaction throws
 * ("There is already an active transaction"), so every uncached match needing TMDB failed before the
 * provider was even called. A single statement has no transaction boundaries of its own: it commits
 * immediately when called standalone (PDO auto-commit), and simply joins whatever transaction the
 * caller already has open otherwise — Postgres row-locks `id = 1` for the statement's duration either
 * way, so concurrent callers still serialize exactly as the old `SELECT ... FOR UPDATE` did.
 */
final readonly class TmdbProviderRateLimiter
{
    // Relevé du budget initial (5 / 2 req/s) — trop conservateur pour un backfill initial de
    // catalogue (retour utilisateur 2026-08-21) : TMDB v3 ne publie plus de limite stricte
    // depuis 2020, la pratique courante observée tolère largement plus. Reste prudent (4 req/s
    // sustained, burst 12) plutôt que d'aligner sur le maximum rapporté par la communauté.
    private const CAPACITY = 12.0;
    private const REFILL_PER_SECOND = 4.0;

    public function __construct(private PDO $pdo) {}

    /** @throws ApiException 429 with Retry-After when the shared budget is exhausted. */
    public function acquire(): void
    {
        $statement = $this->pdo->prepare(
            'WITH refill AS (' .
            '  SELECT LEAST(:capacity, tokens + GREATEST(EXTRACT(EPOCH FROM (NOW() - updated_at)), 0) * :refill) AS available' .
            '  FROM catalog_provider_rate_limit WHERE id = 1' .
            ') ' .
            'UPDATE catalog_provider_rate_limit AS t ' .
            'SET tokens = CASE WHEN refill.available >= 1.0 THEN refill.available - 1.0 ELSE refill.available END, updated_at = NOW() ' .
            'FROM refill WHERE t.id = 1 ' .
            'RETURNING refill.available AS available, (refill.available >= 1.0) AS granted',
        );
        $statement->execute(['capacity' => self::CAPACITY, 'refill' => self::REFILL_PER_SECOND]);
        $row = $statement->fetch(PDO::FETCH_ASSOC);

        if ($row === false || $this->toBool($row['granted']) === false) {
            $available = (float) ($row['available'] ?? 0.0);
            $retryAfter = (int) ceil((1.0 - $available) / self::REFILL_PER_SECOND);
            throw new ApiException(429, 'CATALOG_PROVIDER_RATE_LIMITED', 'The catalog provider budget is temporarily exhausted.', max(1, $retryAfter));
        }
    }

    /** PDO's pgsql driver returns Postgres `boolean` as the strings `'t'`/`'f'`, not a PHP bool. */
    private function toBool(mixed $value): bool
    {
        return $value === true || $value === 't' || $value === '1' || $value === 1;
    }
}
