<?php

declare(strict_types=1);

namespace Cstv\Backend\Tests\Unit;

use Cstv\Backend\Catalog\CatalogFreshnessPolicy;
use DateTimeImmutable;
use PHPUnit\Framework\Attributes\DataProvider;
use PHPUnit\Framework\TestCase;

final class CatalogFreshnessPolicyTest extends TestCase
{
    private CatalogFreshnessPolicy $policy;
    private DateTimeImmutable $now;

    protected function setUp(): void
    {
        $this->policy = new CatalogFreshnessPolicy();
        $this->now = new DateTimeImmutable('2026-08-20T00:00:00+00:00');
    }

    #[DataProvider('movieAgeBuckets')]
    public function testMovieRefreshAfterFollowsAgeBuckets(?string $releaseDate, int $expectedDays): void
    {
        $this->assertWithinJitter($this->policy->movieRefreshAfter($releaseDate, $this->now), $expectedDays);
    }

    /** @return list<array{0: ?string, 1: int}> */
    public static function movieAgeBuckets(): array
    {
        return [
            'unknown' => [null, 30],
            '<1 year' => ['2026-01-01', 14],
            '1-4 years' => ['2023-01-01', 90],
            '5-9 years' => ['2019-01-01', 180],
            '10+ years' => ['2010-01-01', 365],
        ];
    }

    public function testInProductionSeriesAlwaysRefreshesWeekly(): void
    {
        $this->assertWithinJitter($this->policy->seriesRefreshAfter(true, '2010-01-01', $this->now), 7);
    }

    #[DataProvider('endedSeriesAgeBuckets')]
    public function testEndedSeriesRefreshAfterFollowsAgeBuckets(?string $lastAirDate, int $expectedDays): void
    {
        $this->assertWithinJitter($this->policy->seriesRefreshAfter(false, $lastAirDate, $this->now), $expectedDays);
    }

    /** @return list<array{0: ?string, 1: int}> */
    public static function endedSeriesAgeBuckets(): array
    {
        return [
            'unknown' => [null, 30],
            '<1 year' => ['2026-01-01', 30],
            '1-4 years' => ['2023-01-01', 90],
            '5-9 years' => ['2019-01-01', 180],
            '10+ years' => ['2010-01-01', 365],
        ];
    }

    public function testActiveSeasonRefreshesWeekly(): void
    {
        $this->assertWithinJitter($this->policy->seasonRefreshAfter(true, '2026-08-01', $this->now), 7);
    }

    #[DataProvider('endedSeasonAgeBuckets')]
    public function testEndedSeasonRefreshAfterFollowsAgeBuckets(?string $airDate, int $expectedDays): void
    {
        $this->assertWithinJitter($this->policy->seasonRefreshAfter(false, $airDate, $this->now), $expectedDays);
    }

    /** @return list<array{0: ?string, 1: int}> */
    public static function endedSeasonAgeBuckets(): array
    {
        return [
            'unknown' => [null, 30],
            '<1 year' => ['2026-01-01', 30],
            '1-4 years' => ['2023-01-01', 180],
            '4+ years' => ['2010-01-01', 365],
        ];
    }

    private function assertWithinJitter(DateTimeImmutable $refreshAfter, int $expectedDays): void
    {
        $expectedSeconds = $expectedDays * 86400;
        $actualSeconds = $refreshAfter->getTimestamp() - $this->now->getTimestamp();
        // ±10% de jitter (§7.10) : la borne de tolérance suit le même pourcentage.
        self::assertGreaterThanOrEqual((int) ($expectedSeconds * 0.9) - 1, $actualSeconds);
        self::assertLessThanOrEqual((int) ($expectedSeconds * 1.1) + 1, $actualSeconds);
    }
}
