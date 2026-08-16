<?php

declare(strict_types=1);

namespace Cstv\Backend\Tests\Unit;

use Cstv\Backend\Catalog\TmdbClient;
use Cstv\Backend\Catalog\TmdbMediaMetadataProvider;
use PHPUnit\Framework\TestCase;

final class TmdbMediaMetadataProviderTest extends TestCase
{
    public function testTrendingDoesNotFetchAgeRatingAndFiltersPeople(): void
    {
        $provider = new TmdbMediaMetadataProvider(new TmdbClient('unused', static function (string $path): array {
            return match ($path) {
                'trending/all/week' => ['results' => [
                    ['id' => 42, 'media_type' => 'movie', 'title' => 'Dune', 'release_date' => '2021-09-15'],
                    ['id' => 7, 'media_type' => 'person', 'name' => 'Actor'],
                ]],
                default => self::fail('Unexpected TMDB route: ' . $path),
            };
        }));

        self::assertSame([['id' => 'movie:42', 'kind' => 'movie', 'title' => 'Dune', 'originalTitle' => null, 'releaseYear' => 2021, 'overview' => null, 'rating' => null, 'posterUrl' => null, 'backdropUrl' => null, 'ageRatingFr' => null]], $provider->trending('fr-FR'));
    }

    public function testPopularDoesNotFetchAgeRating(): void
    {
        $provider = new TmdbMediaMetadataProvider(new TmdbClient('unused', static function (string $path): array {
            return match ($path) {
                'tv/popular' => ['results' => [['id' => 24, 'name' => 'Show', 'first_air_date' => '2020-01-01']]],
                default => self::fail('Unexpected TMDB route: ' . $path),
            };
        }));

        self::assertNull($provider->popular('series', 1, 'fr-FR')[0]['ageRatingFr']);
    }

    public function testMatchFetchesFrenchMovieCertification(): void
    {
        $provider = new TmdbMediaMetadataProvider(new TmdbClient('unused', static function (string $path): array {
            return match ($path) {
                'search/movie' => ['results' => [
                    ['id' => 42, 'title' => 'Dune', 'release_date' => '2021-09-15'],
                ]],
                'movie/42/release_dates' => ['results' => [['iso_3166_1' => 'FR', 'release_dates' => [['certification' => '12']]]]],
                default => self::fail('Unexpected TMDB route: ' . $path),
            };
        }));

        self::assertSame(12, $provider->match('movie', 'Dune', 2021, 'fr-FR')['ageRatingFr']);
    }

    public function testMatchFetchesFrenchSeriesCertification(): void
    {
        $provider = new TmdbMediaMetadataProvider(new TmdbClient('unused', static function (string $path): array {
            return match ($path) {
                'search/tv' => ['results' => [
                    ['id' => 24, 'name' => 'Show', 'first_air_date' => '2020-01-01'],
                ]],
                'tv/24/content_ratings' => ['results' => [['iso_3166_1' => 'FR', 'rating' => '16']]],
                default => self::fail('Unexpected TMDB route: ' . $path),
            };
        }));

        self::assertSame(16, $provider->match('series', 'Show', 2020, 'fr-FR')['ageRatingFr']);
    }
}
