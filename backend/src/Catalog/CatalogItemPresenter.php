<?php

declare(strict_types=1);

namespace Cstv\Backend\Catalog;

/**
 * Turns a stored `tmdb_movies`/`tmdb_series` row (as returned by
 * `ExternalMediaRepository::getMovie()`/`getSeries()`, joined with `tmdb_id`) into the `/v1/catalog`
 * item contract (§8.7): old fields kept for legacy APKs, `externalId`/`ageRating` added for the new
 * app. Image fields stay as raw TMDB paths (`posterPath`/`backdropPath`) — never a URL — so the
 * caller can resolve them per `X-CSTV-Device-Type` at response time without duplicating cache
 * payloads per device (§8.8). `CatalogService` is responsible for that final path->URL step.
 */
final readonly class CatalogItemPresenter
{
    public function __construct(private TmdbCertificationMapper $certifications = new TmdbCertificationMapper()) {}

    /** @param array<string, mixed> $row @return array<string, mixed> */
    public function present(string $kind, string $externalId, array $row): array
    {
        $movie = $kind === 'movie';
        $ageRating = $row['age_rating'] !== null ? (int) $row['age_rating'] : null;
        return [
            'externalId' => $externalId,
            'id' => $kind . ':' . $row['tmdb_id'],
            'kind' => $kind,
            'title' => $movie ? $row['title'] : $row['name'],
            'originalTitle' => $movie ? $row['original_title'] : $row['original_name'],
            'releaseYear' => $this->year($movie ? $row['release_date'] : $row['first_air_date']),
            'overview' => $row['overview'],
            'rating' => $row['vote_average'] !== null ? round((float) $row['vote_average'], 1) : null,
            'posterPath' => $row['poster_path'],
            'backdropPath' => $row['backdrop_path'],
            'ageRating' => $ageRating,
            'ageRatingFr' => $this->certifications->legacyBucket($ageRating),
            'adult' => $row['adult'] ?? null,
            'originalLanguage' => $row['original_language'] ?? null,
            'genres' => $row['genres'] ?? [],
            'originCountries' => $row['origin_countries'] ?? [],
            'keywords' => $row['keywords'] ?? [],
            'alternativeTitles' => $row['alternative_titles'] ?? [],
            'recommendations' => $row['recommendations'] ?? [],
            'videos' => $row['videos'] ?? [],
            'status' => $row['status'] ?? null,
            'tagline' => $row['tagline'] ?? null,
            'voteCount' => ($row['vote_count'] ?? null) !== null ? (int) $row['vote_count'] : null,
            'runtimeMinutes' => $movie ? (($row['runtime_minutes'] ?? null) !== null ? (int) $row['runtime_minutes'] : null) : null,
            'firstAirDate' => !$movie ? $row['first_air_date'] : null,
            'lastAirDate' => !$movie ? $row['last_air_date'] : null,
            'numberOfEpisodes' => !$movie && ($row['number_of_episodes'] ?? null) !== null ? (int) $row['number_of_episodes'] : null,
            'numberOfSeasons' => !$movie && ($row['number_of_seasons'] ?? null) !== null ? (int) $row['number_of_seasons'] : null,
            'episodeRunTimes' => !$movie ? ($row['episode_run_times'] ?? []) : [],
            'inProduction' => !$movie ? ($row['in_production'] ?? null) : null,
            'nextEpisodeToAir' => !$movie ? ($row['next_episode_to_air'] ?? null) : null,
        ];
    }

    private function year(mixed $date): ?int
    {
        return is_string($date) && preg_match('/^(\d{4})-/', $date, $match) === 1 ? (int) $match[1] : null;
    }
}
