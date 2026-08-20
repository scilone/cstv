<?php

declare(strict_types=1);

namespace Cstv\Backend\Catalog;

use DateTimeImmutable;
use Exception;

/**
 * F45 §7.10: computes `refresh_after` for movies/series. No worker ever scans for expired rows —
 * this is only ever called (a) right after a successful hydrate/refresh, and (b) by whatever reads
 * a row to decide if it should attempt a refresh. A bounded ±10% jitter avoids every media hydrated
 * in the same window expiring in lockstep (anti-stampede, §7.10/§8.5).
 */
final readonly class CatalogFreshnessPolicy
{
    public function movieRefreshAfter(?string $releaseDate, DateTimeImmutable $now): DateTimeImmutable
    {
        return $this->jittered($now, $this->movieTtlDays($releaseDate, $now));
    }

    public function seriesRefreshAfter(bool $inProduction, ?string $lastAirDate, DateTimeImmutable $now): DateTimeImmutable
    {
        return $this->jittered($now, $inProduction ? 7 : $this->seriesTtlDays($lastAirDate, $now));
    }

    /** §7.10 saisons/épisodes : TTL évalué uniquement à l'ouverture d'une fiche série. */
    public function seasonRefreshAfter(bool $seriesInProduction, ?string $seasonAirDate, DateTimeImmutable $now): DateTimeImmutable
    {
        if ($seriesInProduction) return $this->jittered($now, 7);
        $age = $this->ageInYears($seasonAirDate, $now);
        $days = match (true) {
            $age === null => 30,
            $age < 1 => 30,
            $age < 4 => 180,
            default => 365,
        };
        return $this->jittered($now, $days);
    }

    private function movieTtlDays(?string $releaseDate, DateTimeImmutable $now): int
    {
        $age = $this->ageInYears($releaseDate, $now);
        return match (true) {
            $age === null => 30,
            $age < 1 => 14,
            $age < 5 => 90,
            $age < 10 => 180,
            default => 365,
        };
    }

    private function seriesTtlDays(?string $lastAirDate, DateTimeImmutable $now): int
    {
        $age = $this->ageInYears($lastAirDate, $now);
        return match (true) {
            $age === null => 30,
            $age < 1 => 30,
            $age < 5 => 90,
            $age < 10 => 180,
            default => 365,
        };
    }

    private function ageInYears(?string $date, DateTimeImmutable $now): ?float
    {
        if ($date === null || $date === '') return null;
        try {
            $parsed = new DateTimeImmutable($date);
        } catch (Exception) {
            return null;
        }
        return $now->diff($parsed)->days / 365;
    }

    private function jittered(DateTimeImmutable $now, int $days): DateTimeImmutable
    {
        $jitterPercent = random_int(-10, 10);
        $seconds = (int) round($days * 86400 * (1 + $jitterPercent / 100));
        return $now->modify('+' . $seconds . ' seconds');
    }
}
