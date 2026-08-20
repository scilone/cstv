<?php

declare(strict_types=1);

namespace Cstv\Backend\Catalog;

use DateTimeImmutable;
use PDO;

/** Durable, provider-neutral identity registry and metadata store for consolidated catalog items. */
final readonly class ExternalMediaRepository
{
    public function __construct(
        private PDO $pdo,
        private ExternalMediaIdFactory $ids,
        private CatalogFreshnessPolicy $freshness = new CatalogFreshnessPolicy(),
    ) {}

    public function findByTmdb(string $kind, int $tmdbId): ?string
    {
        $query = $this->pdo->prepare('SELECT external_id::text FROM tmdb_media WHERE kind = :kind AND tmdb_id = :tmdb_id');
        $query->execute(['kind' => $kind, 'tmdb_id' => $tmdbId]);
        $id = $query->fetchColumn();
        return is_string($id) ? $id : null;
    }

    /** Creates only the identity (`external_media` + `tmdb_media`), no detail hydration — used for recommendations (§8.2). */
    public function findOrCreateForTmdb(string $kind, int $tmdbId): string
    {
        if (($known = $this->findByTmdb($kind, $tmdbId)) !== null) return $known;
        $externalId = $this->ids->create();
        $media = $this->pdo->prepare('INSERT INTO external_media(external_id, kind) VALUES (:id, :kind) ON CONFLICT DO NOTHING');
        $media->execute(['id' => $externalId, 'kind' => $kind]);
        $link = $this->pdo->prepare('INSERT INTO tmdb_media(external_id, kind, tmdb_id) VALUES (:id, :kind, :tmdb_id) ON CONFLICT (kind, tmdb_id) DO NOTHING');
        $link->execute(['id' => $externalId, 'kind' => $kind, 'tmdb_id' => $tmdbId]);
        return $this->findByTmdb($kind, $tmdbId) ?? $externalId;
    }

    /**
     * F45-R3 : candidats bruts pour la résolution PostgreSQL-first (§8.6) — plus une décision. Le
     * SQL ne fait que rassembler ce qui *pourrait* correspondre (titre normalisé, titre original,
     * ou un titre alternatif, sans filtre année) ; `CatalogMatchEngine` score ensuite chaque ligne
     * exactement comme un candidat fournisseur (titre/année/genres + seuil + marge) avant de
     * réutiliser quoi que ce soit — l'ancien `count($rows) === 1` acceptait un homonyme unique sans
     * aucun score ni marge, et tolérait même une date de sortie absente comme si l'année concordait.
     *
     * @return list<array{external_id: string, title: string, original_title: ?string, alternative_titles: list<string>, media_date: ?string, genres: list<string>}>
     */
    public function findConsolidatedCandidates(string $kind, string $title, ?int $year): array
    {
        $table = $kind === 'movie' ? 'tmdb_movies' : 'tmdb_series';
        $dateColumn = $kind === 'movie' ? 'release_date' : 'first_air_date';
        // `tmdb_series` porte `name`/`original_name`, jamais `title`/`original_title` (colonnes
        // propres à `tmdb_movies`) — un SELECT non aliasé y échouait avec `column "title" does not
        // exist` sur *tout* match série (§8.6 PostgreSQL-first), avant même d'atteindre TMDB.
        $titleColumn = $kind === 'movie' ? 'title' : 'name';
        $originalTitleColumn = $kind === 'movie' ? 'original_title' : 'original_name';
        $normalized = TitleNormalizer::normalize($title);
        if ($normalized === '' && trim($title) === '') return [];
        $statement = $this->pdo->prepare(
            "SELECT external_id::text AS external_id, {$titleColumn} AS title, {$originalTitleColumn} AS original_title, alternative_titles, genres, {$dateColumn} AS media_date " .
            "FROM {$table} WHERE normalized_title = :normalized OR LOWER({$originalTitleColumn}) = LOWER(:raw_title) " .
            'OR EXISTS (SELECT 1 FROM unnest(alternative_titles) AS alt WHERE LOWER(alt) = LOWER(:raw_title)) ' .
            'LIMIT 20',
        );
        $statement->execute(['normalized' => $normalized, 'raw_title' => $title]);
        return array_map(static fn (array $row): array => [
            'external_id' => (string) $row['external_id'],
            'title' => (string) $row['title'],
            'original_title' => $row['original_title'] !== null ? (string) $row['original_title'] : null,
            'alternative_titles' => PgArray::decode($row['alternative_titles']),
            'genres' => PgArray::decode($row['genres']),
            'media_date' => $row['media_date'] !== null ? (string) $row['media_date'] : null,
        ], $statement->fetchAll(PDO::FETCH_ASSOC));
    }

    /** @param array<string, mixed> $movie §7.2 fields, `recommendations` already resolved to externalIds */
    public function persistMovie(string $externalId, array $movie): void
    {
        $now = new DateTimeImmutable();
        $refreshAfter = $this->freshness->movieRefreshAfter($movie['releaseDate'] ?? null, $now);

        $this->pdo->prepare(
            'UPDATE tmdb_media SET hydrated_at = NOW(), refresh_after = :refresh_after, last_refresh_attempt_at = NOW() WHERE external_id = :id',
        )->execute(['id' => $externalId, 'refresh_after' => $refreshAfter->format(DateTimeImmutable::ATOM)]);

        $this->pdo->prepare(
            'INSERT INTO tmdb_movies (external_id, title, original_title, original_language, overview, poster_path, ' .
            'backdrop_path, release_date, runtime_minutes, age_rating, adult, status, tagline, vote_average, vote_count, ' .
            'genres, origin_countries, keywords, alternative_titles, recommendations, videos, normalized_title) VALUES ' .
            '(:external_id, :title, :original_title, :original_language, :overview, :poster_path, :backdrop_path, ' .
            ':release_date, :runtime_minutes, :age_rating, :adult, :status, :tagline, :vote_average, :vote_count, ' .
            ':genres, :origin_countries, :keywords, :alternative_titles, :recommendations, CAST(:videos AS jsonb), :normalized_title) ' .
            'ON CONFLICT (external_id) DO UPDATE SET title = EXCLUDED.title, original_title = EXCLUDED.original_title, ' .
            'original_language = EXCLUDED.original_language, overview = EXCLUDED.overview, poster_path = EXCLUDED.poster_path, ' .
            'backdrop_path = EXCLUDED.backdrop_path, release_date = EXCLUDED.release_date, runtime_minutes = EXCLUDED.runtime_minutes, ' .
            'age_rating = EXCLUDED.age_rating, adult = EXCLUDED.adult, status = EXCLUDED.status, tagline = EXCLUDED.tagline, ' .
            'vote_average = EXCLUDED.vote_average, vote_count = EXCLUDED.vote_count, genres = EXCLUDED.genres, ' .
            'origin_countries = EXCLUDED.origin_countries, keywords = EXCLUDED.keywords, alternative_titles = EXCLUDED.alternative_titles, ' .
            'recommendations = EXCLUDED.recommendations, videos = EXCLUDED.videos, normalized_title = EXCLUDED.normalized_title',
        )->execute([
            'external_id' => $externalId,
            'title' => $movie['title'],
            'original_title' => $movie['originalTitle'] ?? null,
            'original_language' => $movie['originalLanguage'] ?? null,
            'overview' => $movie['overview'] ?? null,
            'poster_path' => $movie['posterPath'] ?? null,
            'backdrop_path' => $movie['backdropPath'] ?? null,
            'release_date' => $movie['releaseDate'] ?? null,
            'runtime_minutes' => $movie['runtimeMinutes'] ?? null,
            'age_rating' => $movie['ageRating'] ?? null,
            'adult' => self::pgBool($movie['adult'] ?? null),
            'status' => $movie['status'] ?? null,
            'tagline' => $movie['tagline'] ?? null,
            'vote_average' => $movie['voteAverage'] ?? null,
            'vote_count' => $movie['voteCount'] ?? null,
            'genres' => PgArray::encode($movie['genres'] ?? []),
            'origin_countries' => PgArray::encode($movie['originCountries'] ?? []),
            'keywords' => PgArray::encode($movie['keywords'] ?? []),
            'alternative_titles' => PgArray::encode($movie['alternativeTitles'] ?? []),
            'recommendations' => PgArray::encode($movie['recommendations'] ?? []),
            'videos' => json_encode($movie['videos'] ?? [], JSON_THROW_ON_ERROR),
            'normalized_title' => TitleNormalizer::normalize($movie['title']),
        ]);
    }

    /** @param array<string, mixed> $series §7.3 fields, `recommendations` already resolved to externalIds */
    public function persistSeries(string $externalId, array $series): void
    {
        $now = new DateTimeImmutable();
        $refreshAfter = $this->freshness->seriesRefreshAfter((bool) ($series['inProduction'] ?? false), $series['lastAirDate'] ?? null, $now);

        $this->pdo->prepare(
            'UPDATE tmdb_media SET hydrated_at = NOW(), refresh_after = :refresh_after, last_refresh_attempt_at = NOW() WHERE external_id = :id',
        )->execute(['id' => $externalId, 'refresh_after' => $refreshAfter->format(DateTimeImmutable::ATOM)]);

        $this->pdo->prepare(
            'INSERT INTO tmdb_series (external_id, name, original_name, original_language, overview, poster_path, ' .
            'backdrop_path, first_air_date, last_air_date, number_of_episodes, number_of_seasons, in_production, ' .
            'next_episode_to_air, age_rating, adult, status, tagline, vote_average, vote_count, episode_run_times, ' .
            'genres, origin_countries, keywords, alternative_titles, recommendations, videos, normalized_title) VALUES ' .
            '(:external_id, :name, :original_name, :original_language, :overview, :poster_path, :backdrop_path, ' .
            ':first_air_date, :last_air_date, :number_of_episodes, :number_of_seasons, :in_production, ' .
            ':next_episode_to_air, :age_rating, :adult, :status, :tagline, :vote_average, :vote_count, :episode_run_times, ' .
            ':genres, :origin_countries, :keywords, :alternative_titles, :recommendations, CAST(:videos AS jsonb), :normalized_title) ' .
            'ON CONFLICT (external_id) DO UPDATE SET name = EXCLUDED.name, original_name = EXCLUDED.original_name, ' .
            'original_language = EXCLUDED.original_language, overview = EXCLUDED.overview, poster_path = EXCLUDED.poster_path, ' .
            'backdrop_path = EXCLUDED.backdrop_path, first_air_date = EXCLUDED.first_air_date, last_air_date = EXCLUDED.last_air_date, ' .
            'number_of_episodes = EXCLUDED.number_of_episodes, number_of_seasons = EXCLUDED.number_of_seasons, ' .
            'in_production = EXCLUDED.in_production, next_episode_to_air = EXCLUDED.next_episode_to_air, ' .
            'age_rating = EXCLUDED.age_rating, adult = EXCLUDED.adult, status = EXCLUDED.status, tagline = EXCLUDED.tagline, ' .
            'vote_average = EXCLUDED.vote_average, vote_count = EXCLUDED.vote_count, episode_run_times = EXCLUDED.episode_run_times, ' .
            'genres = EXCLUDED.genres, origin_countries = EXCLUDED.origin_countries, keywords = EXCLUDED.keywords, ' .
            'alternative_titles = EXCLUDED.alternative_titles, recommendations = EXCLUDED.recommendations, videos = EXCLUDED.videos, ' .
            'normalized_title = EXCLUDED.normalized_title',
        )->execute([
            'external_id' => $externalId,
            'name' => $series['name'],
            'original_name' => $series['originalName'] ?? null,
            'original_language' => $series['originalLanguage'] ?? null,
            'overview' => $series['overview'] ?? null,
            'poster_path' => $series['posterPath'] ?? null,
            'backdrop_path' => $series['backdropPath'] ?? null,
            'first_air_date' => $series['firstAirDate'] ?? null,
            'last_air_date' => $series['lastAirDate'] ?? null,
            'number_of_episodes' => $series['numberOfEpisodes'] ?? null,
            'number_of_seasons' => $series['numberOfSeasons'] ?? null,
            'in_production' => self::pgBool($series['inProduction'] ?? null),
            'next_episode_to_air' => $series['nextEpisodeToAir'] ?? null,
            'age_rating' => $series['ageRating'] ?? null,
            'adult' => self::pgBool($series['adult'] ?? null),
            'status' => $series['status'] ?? null,
            'tagline' => $series['tagline'] ?? null,
            'vote_average' => $series['voteAverage'] ?? null,
            'vote_count' => $series['voteCount'] ?? null,
            'episode_run_times' => PgArray::encode(array_map(strval(...), $series['episodeRunTimes'] ?? [])),
            'genres' => PgArray::encode($series['genres'] ?? []),
            'origin_countries' => PgArray::encode($series['originCountries'] ?? []),
            'keywords' => PgArray::encode($series['keywords'] ?? []),
            'alternative_titles' => PgArray::encode($series['alternativeTitles'] ?? []),
            'recommendations' => PgArray::encode($series['recommendations'] ?? []),
            'videos' => json_encode($series['videos'] ?? [], JSON_THROW_ON_ERROR),
            'normalized_title' => TitleNormalizer::normalize($series['name']),
        ]);
    }

    /** @return array<string, mixed>|null includes `tmdb_id` (joined) for the legacy `id` field */
    public function getMovie(string $externalId): ?array
    {
        $statement = $this->pdo->prepare(
            'SELECT m.*, t.tmdb_id FROM tmdb_movies m JOIN tmdb_media t ON t.external_id = m.external_id WHERE m.external_id = :id',
        );
        $statement->execute(['id' => $externalId]);
        $row = $statement->fetch(PDO::FETCH_ASSOC);
        return $row === false ? null : $this->hydrateRow($row, movie: true);
    }

    /** @return array<string, mixed>|null includes `tmdb_id` (joined) for the legacy `id` field */
    public function getSeries(string $externalId): ?array
    {
        $statement = $this->pdo->prepare(
            'SELECT s.*, t.tmdb_id FROM tmdb_series s JOIN tmdb_media t ON t.external_id = s.external_id WHERE s.external_id = :id',
        );
        $statement->execute(['id' => $externalId]);
        $row = $statement->fetch(PDO::FETCH_ASSOC);
        return $row === false ? null : $this->hydrateRow($row, movie: false);
    }

    /**
     * PDO's pgsql driver binds every `execute()` array value as `PARAM_STR` by default — a PHP
     * `false` then becomes `''`, which Postgres rejects for a `boolean` column
     * (`invalid input syntax for type boolean: ""`). Encode explicitly instead of trusting the
     * implicit string cast.
     */
    private static function pgBool(?bool $value): ?string
    {
        return $value === null ? null : ($value ? 't' : 'f');
    }

    /** @param array<string, mixed> $row @return array<string, mixed> */
    private function hydrateRow(array $row, bool $movie): array
    {
        $row['genres'] = PgArray::decode($row['genres']);
        $row['origin_countries'] = PgArray::decode($row['origin_countries']);
        $row['keywords'] = PgArray::decode($row['keywords']);
        $row['alternative_titles'] = PgArray::decode($row['alternative_titles']);
        $row['recommendations'] = PgArray::decode($row['recommendations']);
        $row['videos'] = json_decode((string) $row['videos'], true, 512, JSON_THROW_ON_ERROR);
        $row['adult'] = self::pgBoolToPhp($row['adult'] ?? null);
        if (!$movie) {
            $row['episode_run_times'] = array_map(intval(...), PgArray::decode($row['episode_run_times']));
            $row['in_production'] = self::pgBoolToPhp($row['in_production'] ?? null);
        }
        return $row;
    }

    /** PDO returns a pgsql `boolean` as the string `'t'`/`'f'` — `(bool) 'f'` is truthy in PHP, decode explicitly. */
    private static function pgBoolToPhp(mixed $value): ?bool
    {
        if ($value === null) return null;
        return $value === true || $value === 't' || $value === '1';
    }

    /** `kind:tmdbId` legacy identifier for an externalId — bridges the UUID and legacy video routes (§8.7). */
    public function tmdbCanonicalId(string $externalId): ?string
    {
        $statement = $this->pdo->prepare('SELECT kind, tmdb_id FROM tmdb_media WHERE external_id = :id');
        $statement->execute(['id' => $externalId]);
        $row = $statement->fetch(PDO::FETCH_ASSOC);
        return $row === false ? null : $row['kind'] . ':' . $row['tmdb_id'];
    }

    /** @return array{updatedAt: ?string, refreshAfter: ?string}|null §8.7 `cache.updatedAt`/`cache.refreshAfter`. */
    public function hydrationWindow(string $externalId): ?array
    {
        $statement = $this->pdo->prepare('SELECT hydrated_at, refresh_after FROM tmdb_media WHERE external_id = :id');
        $statement->execute(['id' => $externalId]);
        $row = $statement->fetch(PDO::FETCH_ASSOC);
        return $row === false ? null : ['updatedAt' => $row['hydrated_at'], 'refreshAfter' => $row['refresh_after']];
    }

    public function seriesTmdbId(string $externalId): ?int
    {
        $statement = $this->pdo->prepare("SELECT tmdb_id FROM tmdb_media WHERE external_id = :id AND kind = 'series'");
        $statement->execute(['id' => $externalId]);
        $id = $statement->fetchColumn();
        return $id === false ? null : (int) $id;
    }

    /** @return array<string, mixed>|null null when never hydrated OR expired (caller decides: refresh or keep serving stale on provider failure). */
    public function getSeason(string $seriesExternalId, int $seasonNumber): ?array
    {
        $statement = $this->pdo->prepare(
            'SELECT * FROM tmdb_seasons WHERE series_external_id = :id AND season_number = :season',
        );
        $statement->execute(['id' => $seriesExternalId, 'season' => $seasonNumber]);
        $row = $statement->fetch(PDO::FETCH_ASSOC);
        if ($row === false) return null;

        $episodes = $this->pdo->prepare(
            'SELECT * FROM tmdb_episodes WHERE series_external_id = :id AND season_number = :season ORDER BY episode_number',
        );
        $episodes->execute(['id' => $seriesExternalId, 'season' => $seasonNumber]);
        $row['episodes'] = $episodes->fetchAll(PDO::FETCH_ASSOC);
        return $row;
    }

    public function seasonIsStale(array $season): bool
    {
        $refreshAfter = $season['refresh_after'] ?? null;
        return $refreshAfter === null || new DateTimeImmutable($refreshAfter) <= new DateTimeImmutable();
    }

    /** @param array<string, mixed> $season §7.4 fields (`seasonDetail()` shape) */
    public function persistSeason(string $seriesExternalId, int $seasonNumber, array $season, bool $seriesInProduction): void
    {
        $now = new DateTimeImmutable();
        $refreshAfter = $this->freshness->seasonRefreshAfter($seriesInProduction, $season['airDate'] ?? null, $now);

        $this->pdo->prepare(
            'INSERT INTO tmdb_seasons (series_external_id, season_number, name, overview, poster_path, air_date, ' .
            'vote_average, hydrated_at, refresh_after) VALUES (:id, :season, :name, :overview, :poster_path, :air_date, ' .
            ':vote_average, NOW(), :refresh_after) ON CONFLICT (series_external_id, season_number) DO UPDATE SET ' .
            'name = EXCLUDED.name, overview = EXCLUDED.overview, poster_path = EXCLUDED.poster_path, ' .
            'air_date = EXCLUDED.air_date, vote_average = EXCLUDED.vote_average, hydrated_at = EXCLUDED.hydrated_at, ' .
            'refresh_after = EXCLUDED.refresh_after',
        )->execute([
            'id' => $seriesExternalId,
            'season' => $seasonNumber,
            'name' => $season['name'],
            'overview' => $season['overview'] ?? null,
            'poster_path' => $season['posterPath'] ?? null,
            'air_date' => $season['airDate'] ?? null,
            'vote_average' => $season['voteAverage'] ?? null,
            'refresh_after' => $refreshAfter->format(DateTimeImmutable::ATOM),
        ]);

        $episode = $this->pdo->prepare(
            'INSERT INTO tmdb_episodes (series_external_id, season_number, episode_number, name, overview, ' .
            'still_path, air_date, runtime_minutes, vote_average, vote_count) VALUES ' .
            '(:id, :season, :episode, :name, :overview, :still_path, :air_date, :runtime_minutes, :vote_average, :vote_count) ' .
            'ON CONFLICT (series_external_id, season_number, episode_number) DO UPDATE SET name = EXCLUDED.name, ' .
            'overview = EXCLUDED.overview, still_path = EXCLUDED.still_path, air_date = EXCLUDED.air_date, ' .
            'runtime_minutes = EXCLUDED.runtime_minutes, vote_average = EXCLUDED.vote_average, vote_count = EXCLUDED.vote_count',
        );
        foreach ($season['episodes'] ?? [] as $row) {
            $episode->execute([
                'id' => $seriesExternalId,
                'season' => $seasonNumber,
                'episode' => $row['episodeNumber'],
                'name' => $row['name'],
                'overview' => $row['overview'] ?? null,
                'still_path' => $row['stillPath'] ?? null,
                'air_date' => $row['airDate'] ?? null,
                'runtime_minutes' => $row['runtimeMinutes'] ?? null,
                'vote_average' => $row['voteAverage'] ?? null,
                'vote_count' => $row['voteCount'] ?? null,
            ]);
        }
    }
}
