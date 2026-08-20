<?php

declare(strict_types=1);

namespace Cstv\Backend\Tests\Unit;

use Cstv\Backend\Catalog\TmdbCertificationMapper;
use PHPUnit\Framework\TestCase;

final class TmdbCertificationMapperTest extends TestCase
{
    private TmdbCertificationMapper $mapper;

    protected function setUp(): void
    {
        $this->mapper = new TmdbCertificationMapper();
    }

    public function testFrenchCertificationsMapToExactAges(): void
    {
        // Liste de paires, pas de tableau associatif : une clé numérique comme '10' serait
        // silencieusement convertie en entier par PHP et casserait le `is_string()` de collect().
        foreach ([['TP', 0], ['10', 10], ['12', 12], ['16', 16], ['18', 18]] as [$certification, $expected]) {
            self::assertSame($expected, $this->mapper->fromReleaseDates([
                ['iso_3166_1' => 'FR', 'release_dates' => [['certification' => $certification]]],
            ]));
        }
    }

    public function testUsCertificationsMapToExactAges(): void
    {
        foreach (['G' => 0, 'PG-13' => 13, 'R' => 17, 'NC-17' => 17] as $certification => $expected) {
            self::assertSame($expected, $this->mapper->fromReleaseDates([
                ['iso_3166_1' => 'US', 'release_dates' => [['certification' => $certification]]],
            ]));
        }
    }

    public function testGbCertificationsMapToExactAges(): void
    {
        foreach ([['U', 0], ['12A', 12], ['15', 15], ['R18', 18]] as [$certification, $expected]) {
            self::assertSame($expected, $this->mapper->fromReleaseDates([
                ['iso_3166_1' => 'GB', 'release_dates' => [['certification' => $certification]]],
            ]));
        }
    }

    public function testFrTakesPriorityOverUsAndGb(): void
    {
        self::assertSame(12, $this->mapper->fromReleaseDates([
            ['iso_3166_1' => 'FR', 'release_dates' => [['certification' => '12']]],
            ['iso_3166_1' => 'US', 'release_dates' => [['certification' => 'R']]],
            ['iso_3166_1' => 'GB', 'release_dates' => [['certification' => '18']]],
        ]));
    }

    public function testMultipleFrEntriesKeepTheMostRestrictive(): void
    {
        self::assertSame(16, $this->mapper->fromReleaseDates([
            ['iso_3166_1' => 'FR', 'release_dates' => [['certification' => '12'], ['certification' => '16']]],
        ]));
    }

    public function testFallsBackToMedianOfOtherNumericTerritoriesWhenFrUsGbAreMissing(): void
    {
        // DE=12, NL=16, PT=6 -> médiane impaire = 12
        self::assertSame(12, $this->mapper->fromReleaseDates([
            ['iso_3166_1' => 'DE', 'release_dates' => [['certification' => '12']]],
            ['iso_3166_1' => 'NL', 'release_dates' => [['certification' => '16']]],
            ['iso_3166_1' => 'PT', 'release_dates' => [['certification' => '6']]],
        ]));
    }

    public function testEvenMedianRoundsUp(): void
    {
        // DE=12, NL=16 -> moyenne 14, déjà entier donc pas d'arrondi à vérifier séparément
        self::assertSame(14, $this->mapper->fromReleaseDates([
            ['iso_3166_1' => 'DE', 'release_dates' => [['certification' => '12']]],
            ['iso_3166_1' => 'NL', 'release_dates' => [['certification' => '16']]],
        ]));
        // DE=13 (non numérique standard mais accepté générique), NL=16 -> moyenne 14.5 -> arrondi supérieur 15
        self::assertSame(15, $this->mapper->fromReleaseDates([
            ['iso_3166_1' => 'DE', 'release_dates' => [['certification' => '13']]],
            ['iso_3166_1' => 'NL', 'release_dates' => [['certification' => '16']]],
        ]));
    }

    public function testUnmappableCertificationsAreIgnoredNotZero(): void
    {
        self::assertNull($this->mapper->fromReleaseDates([
            ['iso_3166_1' => 'FR', 'release_dates' => [['certification' => '']]],
            ['iso_3166_1' => 'ZZ', 'release_dates' => [['certification' => 'Unrated']]],
        ]));
    }

    public function testNoExploitableEntryReturnsNullNotZero(): void
    {
        self::assertNull($this->mapper->fromReleaseDates([]));
    }

    public function testContentRatingsUseTheSameFrUsGbPriority(): void
    {
        self::assertSame(16, $this->mapper->fromContentRatings([
            ['iso_3166_1' => 'FR', 'rating' => '16'],
            ['iso_3166_1' => 'US', 'rating' => 'TV-MA'],
        ]));
    }

    /** F45-R10 : sans certification FR exploitable, les codes TV Parental Guidelines US doivent se mapper eux-mêmes, pas tomber sur `null`/médiane. */
    public function testUsOnlyTvCertificationsMapToExactAges(): void
    {
        foreach (['TV-Y' => 0, 'TV-Y7' => 7, 'TV-G' => 0, 'TV-PG' => 10, 'TV-14' => 14, 'TV-MA' => 17] as $certification => $expected) {
            self::assertSame($expected, $this->mapper->fromContentRatings([
                ['iso_3166_1' => 'US', 'rating' => $certification],
            ]), "US $certification should map to $expected");
        }
    }

    public function testUsTvCertificationsStillLoseToFrPriority(): void
    {
        self::assertSame(12, $this->mapper->fromContentRatings([
            ['iso_3166_1' => 'FR', 'rating' => '12'],
            ['iso_3166_1' => 'US', 'rating' => 'TV-MA'],
        ]));
    }

    public function testMultipleUsTvRatingsKeepTheMostRestrictive(): void
    {
        self::assertSame(17, $this->mapper->fromContentRatings([
            ['iso_3166_1' => 'US', 'rating' => 'TV-14'],
            ['iso_3166_1' => 'US', 'rating' => 'TV-MA'],
        ]));
    }

    public function testLegacyBucketMatchesPreF45Behaviour(): void
    {
        self::assertNull($this->mapper->legacyBucket(null));
        self::assertSame(0, $this->mapper->legacyBucket(0));
        self::assertSame(10, $this->mapper->legacyBucket(10));
        self::assertSame(12, $this->mapper->legacyBucket(12));
        self::assertSame(12, $this->mapper->legacyBucket(13));
        self::assertSame(16, $this->mapper->legacyBucket(16));
        self::assertSame(18, $this->mapper->legacyBucket(17));
    }
}
