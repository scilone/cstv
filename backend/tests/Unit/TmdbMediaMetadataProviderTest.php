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

        self::assertSame([['id' => 'movie:42', 'kind' => 'movie', 'title' => 'Dune', 'originalTitle' => null, 'releaseYear' => 2021, 'overview' => null, 'rating' => null, 'posterUrl' => null, 'backdropUrl' => null, 'ageRating' => null, 'ageRatingFr' => null]], $provider->trending('fr-FR'));
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

    public function testSearchCandidatesNeverPicksAWinnerItReturnsEveryResult(): void
    {
        $provider = new TmdbMediaMetadataProvider(new TmdbClient('unused', static function (string $path): array {
            return match ($path) {
                'search/movie' => ['results' => [
                    ['id' => 42, 'title' => 'Dune', 'original_title' => 'Dune', 'release_date' => '2021-09-15', 'genre_ids' => [878, 12]],
                    ['id' => 99, 'title' => 'Dune: Part Two', 'release_date' => '2024-02-28', 'genre_ids' => [878]],
                ]],
                default => self::fail('Unexpected TMDB route: ' . $path),
            };
        }));

        $candidates = $provider->searchCandidates('movie', 'Dune', 2021, 'fr-FR');
        self::assertCount(2, $candidates);
        self::assertSame(42, $candidates[0]->tmdbId);
        self::assertSame([878, 12], $candidates[0]->genreIds);
        self::assertSame(99, $candidates[1]->tmdbId);
    }

    public function testCandidateDetailExtractsDirectorCastRuntimeTrailerAndAltTitlesWithoutPersistingCredits(): void
    {
        $provider = new TmdbMediaMetadataProvider(new TmdbClient('unused', static function (string $path): array {
            return match ($path) {
                'movie/42' => [
                    'runtime' => 155,
                    'credits' => [
                        'crew' => [['job' => 'Director', 'name' => 'Denis Villeneuve'], ['job' => 'Producer', 'name' => 'Someone Else']],
                        'cast' => [['name' => 'Timothée Chalamet'], ['name' => 'Zendaya']],
                    ],
                    'videos' => ['results' => [['site' => 'YouTube', 'key' => 'n9xhJrByW1U', 'type' => 'Trailer'], ['site' => 'Vimeo', 'key' => 'ignored']]],
                    'alternative_titles' => ['titles' => [['title' => 'Dune Part One'], ['title' => 'Dune']]],
                ],
                default => self::fail('Unexpected TMDB route: ' . $path),
            };
        }));

        $detail = $provider->candidateDetail('movie', 42, 'fr-FR');
        self::assertSame(['Denis Villeneuve'], $detail['directors']);
        self::assertSame(['Timothée Chalamet', 'Zendaya'], $detail['cast']);
        self::assertSame(155, $detail['runtimeMinutes']);
        self::assertSame(['n9xhJrByW1U'], $detail['trailerKeys']);
        self::assertSame(['Dune Part One', 'Dune'], $detail['alternativeTitles']);
    }

    public function testGenreNamesAreCachedAfterTheFirstCall(): void
    {
        // Cache statique process-wide (§8.16, un seul appel TMDB par (kind, locale) et par worker
        // PHP-FPM) : réinitialisée ici pour ne pas dépendre de l'ordre d'exécution des tests.
        $cache = new \ReflectionProperty(TmdbMediaMetadataProvider::class, 'genreCache');
        $cache->setValue(null, []);

        $calls = 0;
        $provider = new TmdbMediaMetadataProvider(new TmdbClient('unused', static function (string $path) use (&$calls): array {
            $calls++;
            return match ($path) {
                'genre/movie/list' => ['genres' => [['id' => 878, 'name' => 'Science Fiction']]],
                default => self::fail('Unexpected TMDB route: ' . $path),
            };
        }));

        self::assertSame(['Science Fiction'], array_values($provider->genreNames('movie', 'fr-FR')));
        self::assertSame(878, array_key_first($provider->genreNames('movie', 'fr-FR')));
        self::assertSame(1, $calls, 'the genre list must be fetched at most once per (kind, locale)');
    }

    public function testHydrateBuildsTheFullMovieRecordWithAgeRatingViaAppendToResponse(): void
    {
        $provider = new TmdbMediaMetadataProvider(new TmdbClient('unused', static function (string $path, array $query): array {
            self::assertSame('release_dates,keywords,recommendations,videos,alternative_titles', $query['append_to_response'] ?? null);
            return match ($path) {
                'movie/42' => [
                    'title' => 'Dune',
                    'original_title' => 'Dune',
                    'original_language' => 'en',
                    'overview' => 'A noble family...',
                    'poster_path' => '/poster.jpg',
                    'backdrop_path' => '/backdrop.jpg',
                    'release_date' => '2021-09-15',
                    'runtime' => 155,
                    'adult' => false,
                    'status' => 'Released',
                    'tagline' => 'Beyond fear, destiny awaits.',
                    'vote_average' => 8.023,
                    'vote_count' => 12345,
                    'genres' => [['id' => 878, 'name' => 'Science Fiction']],
                    'origin_country' => ['US'],
                    'release_dates' => ['results' => [['iso_3166_1' => 'FR', 'release_dates' => [['certification' => '12']]]]],
                    'keywords' => ['keywords' => [['id' => 1, 'name' => 'desert']]],
                    'recommendations' => ['results' => [['id' => 99], ['id' => 100]]],
                    'videos' => ['results' => [['site' => 'YouTube', 'key' => 'n9xhJrByW1U', 'type' => 'Trailer', 'name' => 'Official Trailer', 'official' => true]]],
                    'alternative_titles' => ['titles' => [['title' => 'Dune Part One']]],
                ],
                default => self::fail('Unexpected TMDB route: ' . $path),
            };
        }));

        $movie = $provider->hydrate('movie', 42, 'fr-FR');

        self::assertSame('Dune', $movie['title']);
        self::assertSame(12, $movie['ageRating']);
        self::assertSame(155, $movie['runtimeMinutes']);
        self::assertSame(['Science Fiction'], $movie['genres']);
        self::assertSame(['US'], $movie['originCountries']);
        self::assertSame(['desert'], $movie['keywords']);
        self::assertSame([99, 100], $movie['recommendations']);
        self::assertSame('n9xhJrByW1U', $movie['videos'][0]['key']);
        self::assertSame(['Dune Part One'], $movie['alternativeTitles']);
        self::assertArrayNotHasKey('credits', $movie);
    }

    public function testHydrateBuildsTheFullSeriesRecordWithContentRatings(): void
    {
        $provider = new TmdbMediaMetadataProvider(new TmdbClient('unused', static function (string $path, array $query): array {
            self::assertSame('content_ratings,keywords,recommendations,videos,alternative_titles', $query['append_to_response'] ?? null);
            return match ($path) {
                'tv/24' => [
                    'name' => 'Show',
                    'first_air_date' => '2020-01-01',
                    'last_air_date' => '2022-01-01',
                    'number_of_episodes' => 30,
                    'number_of_seasons' => 3,
                    'in_production' => false,
                    'episode_run_time' => [45],
                    'content_ratings' => ['results' => [['iso_3166_1' => 'FR', 'rating' => '16']]],
                    'genres' => [],
                    'keywords' => ['results' => []],
                    'recommendations' => ['results' => []],
                    'videos' => ['results' => []],
                    'alternative_titles' => ['results' => []],
                ],
                default => self::fail('Unexpected TMDB route: ' . $path),
            };
        }));

        $series = $provider->hydrate('series', 24, 'fr-FR');
        self::assertSame('Show', $series['name']);
        self::assertSame(16, $series['ageRating']);
        self::assertSame([45], $series['episodeRunTimes']);
        self::assertFalse($series['inProduction']);
    }

    public function testSeasonDetailReturnsEpisodesWithoutPerEpisodeCalls(): void
    {
        $provider = new TmdbMediaMetadataProvider(new TmdbClient('unused', static function (string $path): array {
            return match ($path) {
                'tv/24/season/1' => [
                    'name' => 'Season 1',
                    'episodes' => [
                        ['episode_number' => 1, 'name' => 'Pilot', 'still_path' => '/still.jpg', 'runtime' => 42],
                        ['episode_number' => 2, 'name' => 'Episode 2'],
                    ],
                ],
                default => self::fail('Unexpected TMDB route: ' . $path),
            };
        }));

        $season = $provider->seasonDetail(24, 1, 'fr-FR');
        self::assertSame('Season 1', $season['name']);
        self::assertCount(2, $season['episodes']);
        self::assertSame('Pilot', $season['episodes'][0]['name']);
        self::assertSame(42, $season['episodes'][0]['runtimeMinutes']);
    }
}
