<?php

declare(strict_types=1);

namespace Cstv\Backend\Catalog;

final class TmdbMediaMetadataProvider implements MediaMetadataProvider
{
    /** @var array<string, array<int, string>> in-process genre id->name cache; rarely changes, one call/worker/locale */
    private static array $genreCache = [];

    public function __construct(
        private readonly TmdbClient $client,
        private readonly TmdbCertificationMapper $certifications = new TmdbCertificationMapper(),
        private readonly ?TmdbProviderRateLimiter $rateLimiter = null,
    ) {
    }

    // F45-R8 : `trending()`/`popular()`/`videos()` appelaient `TmdbClient` directement, hors du
    // budget global — seules `searchCandidates()`/`candidateDetail()`/`genreNames()`/`hydrate()`/
    // `seasonDetail()` passaient par `acquire()`. Plusieurs installations pouvaient donc dépasser le
    // quota partagé via ces trois routes malgré un rate limiter "global" sur le papier.
    public function trending(string $locale): array { $this->rateLimiter?->acquire(); return $this->items($this->client->get('trending/all/week', ['language' => $locale])); }
    public function popular(string $kind, int $page, string $locale): array { $this->rateLimiter?->acquire(); return $this->items($this->client->get(($kind === 'movie' ? 'movie' : 'tv') . '/popular', ['language' => $locale, 'page' => $page])); }

    public function videos(string $canonicalId, string $locale): array
    {
        [$kind, $id] = explode(':', $canonicalId, 2) + [null, null];
        if (!in_array($kind, ['movie', 'series'], true) || !ctype_digit((string) $id)) throw new CatalogProviderException(502, 'Catalog identifier is invalid.');
        $this->rateLimiter?->acquire();
        $response = $this->client->get(($kind === 'movie' ? 'movie' : 'tv') . '/' . $id . '/videos', ['language' => $locale]);
        return $this->videoList($response);
    }

    public function searchCandidates(string $kind, string $title, ?int $year, string $locale): array
    {
        $this->rateLimiter?->acquire();
        $query = ['query' => $title, 'language' => $locale];
        if ($year !== null) $query[$kind === 'movie' ? 'year' : 'first_air_date_year'] = $year;
        $response = $this->client->get('search/' . ($kind === 'movie' ? 'movie' : 'tv'), $query);
        $results = $response['results'] ?? [];
        if (!is_array($results)) return [];
        return array_values(array_filter(array_map(fn (mixed $row): ?CatalogMatchCandidate => $this->candidate($kind, $row), $results)));
    }

    public function candidateDetail(string $kind, int $tmdbId, string $locale): array
    {
        $this->rateLimiter?->acquire();
        $response = $this->client->get(($kind === 'movie' ? 'movie' : 'tv') . '/' . $tmdbId, [
            'language' => $locale,
            'append_to_response' => 'credits,videos,alternative_titles',
        ]);
        $crew = is_array($response['credits']['crew'] ?? null) ? $response['credits']['crew'] : [];
        $castRows = is_array($response['credits']['cast'] ?? null) ? array_slice($response['credits']['cast'], 0, 10) : [];
        $runtime = $kind === 'movie'
            ? ($response['runtime'] ?? null)
            : (is_array($response['episode_run_time'] ?? null) ? ($response['episode_run_time'][0] ?? null) : null);

        return [
            'directors' => array_values(array_filter(array_map(
                static fn (mixed $member): ?string => is_array($member) && ($member['job'] ?? null) === 'Director' && is_string($member['name'] ?? null) ? $member['name'] : null,
                $crew,
            ))),
            'cast' => array_values(array_filter(array_map(
                static fn (mixed $member): ?string => is_array($member) && is_string($member['name'] ?? null) ? $member['name'] : null,
                $castRows,
            ))),
            'runtimeMinutes' => is_numeric($runtime) ? (int) $runtime : null,
            'trailerKeys' => $this->videoKeys($response['videos'] ?? null),
            'alternativeTitles' => $this->alternativeTitleStrings($kind, $response['alternative_titles'] ?? null),
        ];
    }

    public function genreNames(string $kind, string $locale): array
    {
        $key = $kind . '|' . $locale;
        if (isset(self::$genreCache[$key])) return self::$genreCache[$key];
        $this->rateLimiter?->acquire();
        $response = $this->client->get('genre/' . ($kind === 'movie' ? 'movie' : 'tv') . '/list', ['language' => $locale]);
        $map = [];
        foreach (is_array($response['genres'] ?? null) ? $response['genres'] : [] as $genre) {
            if (is_array($genre) && is_numeric($genre['id'] ?? null) && is_string($genre['name'] ?? null)) $map[(int) $genre['id']] = $genre['name'];
        }
        return self::$genreCache[$key] = $map;
    }

    public function hydrate(string $kind, int $tmdbId, string $locale): array
    {
        $this->rateLimiter?->acquire();
        // `append_to_response` : la fiche + toutes les sous-ressources retenues en un seul appel (§8.16).
        $append = $kind === 'movie' ? 'release_dates,keywords,recommendations,videos,alternative_titles' : 'content_ratings,keywords,recommendations,videos,alternative_titles';
        $response = $this->client->get(($kind === 'movie' ? 'movie' : 'tv') . '/' . $tmdbId, ['language' => $locale, 'append_to_response' => $append]);

        $ageRating = $kind === 'movie'
            ? $this->certifications->fromReleaseDates(is_array($response['release_dates']['results'] ?? null) ? $response['release_dates']['results'] : [])
            : $this->certifications->fromContentRatings(is_array($response['content_ratings']['results'] ?? null) ? $response['content_ratings']['results'] : []);

        $shared = [
            'overview' => is_string($response['overview'] ?? null) ? $response['overview'] : null,
            'posterPath' => is_string($response['poster_path'] ?? null) ? $response['poster_path'] : null,
            'backdropPath' => is_string($response['backdrop_path'] ?? null) ? $response['backdrop_path'] : null,
            'originalLanguage' => is_string($response['original_language'] ?? null) ? $response['original_language'] : null,
            'ageRating' => $ageRating,
            'adult' => (bool) ($response['adult'] ?? false),
            'status' => is_string($response['status'] ?? null) ? $response['status'] : null,
            'tagline' => is_string($response['tagline'] ?? null) && $response['tagline'] !== '' ? $response['tagline'] : null,
            'voteAverage' => is_numeric($response['vote_average'] ?? null) ? round((float) $response['vote_average'], 1) : null,
            'voteCount' => is_numeric($response['vote_count'] ?? null) ? (int) $response['vote_count'] : null,
            'genres' => $this->genreNameList($response['genres'] ?? null),
            'originCountries' => is_array($response['origin_country'] ?? null) ? array_values(array_map('strval', $response['origin_country'])) : [],
            'keywords' => $this->keywordList($kind, $response['keywords'] ?? null),
            'alternativeTitles' => $this->alternativeTitleStrings($kind, $response['alternative_titles'] ?? null),
            // §7.6 : 20 premières, ordre conservé, IDs bruts — `CatalogMatchEngine` les résout en externalId.
            'recommendations' => array_slice($this->recommendationTmdbIds($response['recommendations'] ?? null), 0, 20),
            'videos' => $this->videoList($response['videos'] ?? null),
        ];

        return $kind === 'movie' ? [
            'title' => is_string($response['title'] ?? null) ? $response['title'] : '',
            'originalTitle' => is_string($response['original_title'] ?? null) ? $response['original_title'] : null,
            'releaseDate' => is_string($response['release_date'] ?? null) && $response['release_date'] !== '' ? $response['release_date'] : null,
            'runtimeMinutes' => is_numeric($response['runtime'] ?? null) ? (int) $response['runtime'] : null,
            ...$shared,
        ] : [
            'name' => is_string($response['name'] ?? null) ? $response['name'] : '',
            'originalName' => is_string($response['original_name'] ?? null) ? $response['original_name'] : null,
            'firstAirDate' => is_string($response['first_air_date'] ?? null) && $response['first_air_date'] !== '' ? $response['first_air_date'] : null,
            'lastAirDate' => is_string($response['last_air_date'] ?? null) && $response['last_air_date'] !== '' ? $response['last_air_date'] : null,
            'numberOfEpisodes' => is_numeric($response['number_of_episodes'] ?? null) ? (int) $response['number_of_episodes'] : null,
            'numberOfSeasons' => is_numeric($response['number_of_seasons'] ?? null) ? (int) $response['number_of_seasons'] : null,
            'inProduction' => (bool) ($response['in_production'] ?? false),
            'nextEpisodeToAir' => is_array($response['next_episode_to_air'] ?? null) && is_string($response['next_episode_to_air']['air_date'] ?? null) ? $response['next_episode_to_air']['air_date'] : null,
            'episodeRunTimes' => is_array($response['episode_run_time'] ?? null) ? array_values(array_filter(array_map(static fn (mixed $rt): ?int => is_numeric($rt) ? (int) $rt : null, $response['episode_run_time']))) : [],
            ...$shared,
        ];
    }

    public function seasonDetail(int $seriesTmdbId, int $seasonNumber, string $locale): array
    {
        $this->rateLimiter?->acquire();
        $response = $this->client->get('tv/' . $seriesTmdbId . '/season/' . $seasonNumber, ['language' => $locale]);
        $episodes = is_array($response['episodes'] ?? null) ? $response['episodes'] : [];
        return [
            'seasonNumber' => $seasonNumber,
            'name' => is_string($response['name'] ?? null) ? $response['name'] : ('Season ' . $seasonNumber),
            'overview' => is_string($response['overview'] ?? null) ? $response['overview'] : null,
            'posterPath' => is_string($response['poster_path'] ?? null) ? $response['poster_path'] : null,
            'airDate' => is_string($response['air_date'] ?? null) && $response['air_date'] !== '' ? $response['air_date'] : null,
            'voteAverage' => is_numeric($response['vote_average'] ?? null) ? round((float) $response['vote_average'], 1) : null,
            'episodes' => array_values(array_filter(array_map($this->episode(...), $episodes))),
        ];
    }

    /** @return array<string, mixed>|null */
    private function episode(mixed $episode): ?array
    {
        if (!is_array($episode) || !is_numeric($episode['episode_number'] ?? null)) return null;
        return [
            'episodeNumber' => (int) $episode['episode_number'],
            'name' => is_string($episode['name'] ?? null) ? $episode['name'] : '',
            'overview' => is_string($episode['overview'] ?? null) ? $episode['overview'] : null,
            'stillPath' => is_string($episode['still_path'] ?? null) ? $episode['still_path'] : null,
            'airDate' => is_string($episode['air_date'] ?? null) && $episode['air_date'] !== '' ? $episode['air_date'] : null,
            'runtimeMinutes' => is_numeric($episode['runtime'] ?? null) ? (int) $episode['runtime'] : null,
            'voteAverage' => is_numeric($episode['vote_average'] ?? null) ? round((float) $episode['vote_average'], 1) : null,
            'voteCount' => is_numeric($episode['vote_count'] ?? null) ? (int) $episode['vote_count'] : null,
        ];
    }

    private function candidate(string $kind, mixed $row): ?CatalogMatchCandidate
    {
        if (!is_array($row) || !is_numeric($row['id'] ?? null)) return null;
        $title = $row[$kind === 'movie' ? 'title' : 'name'] ?? null;
        if (!is_string($title) || $title === '') return null;
        $date = $row[$kind === 'movie' ? 'release_date' : 'first_air_date'] ?? null;
        $year = is_string($date) && preg_match('/^(\d{4})-/', $date, $match) === 1 ? (int) $match[1] : null;
        $genreIds = is_array($row['genre_ids'] ?? null)
            ? array_values(array_filter(array_map(static fn (mixed $g): ?int => is_numeric($g) ? (int) $g : null, $row['genre_ids'])))
            : [];
        $originalTitle = $row['original_title'] ?? $row['original_name'] ?? null;
        return new CatalogMatchCandidate((int) $row['id'], $title, is_string($originalTitle) ? $originalTitle : null, $year, $genreIds);
    }

    /** @return list<array<string, mixed>> */
    private function items(array $response): array
    {
        $results = $response['results'] ?? [];
        if (!is_array($results)) return [];
        return array_values(array_filter(array_map(fn (mixed $row): ?array => $this->item($row), $results)));
    }

    /** @return array<string, mixed>|null */
    private function item(mixed $row): ?array
    {
        if (!is_array($row) || !is_numeric($row['id'] ?? null)) return null;
        $mediaType = $row['media_type'] ?? null;
        if ($mediaType !== null && !in_array($mediaType, ['movie', 'tv'], true)) return null;
        $kind = $mediaType === 'tv' || ($mediaType === null && isset($row['name'])) ? 'series' : 'movie';
        $title = $row[$kind === 'movie' ? 'title' : 'name'] ?? null;
        if (!is_string($title) || $title === '') return null;
        $date = $row[$kind === 'movie' ? 'release_date' : 'first_air_date'] ?? null;
        $year = is_string($date) && preg_match('/^(\d{4})-/', $date, $match) ? (int) $match[1] : null;
        $image = static fn (mixed $path, string $size): ?string => is_string($path) && $path !== '' ? 'https://image.tmdb.org/t/p/' . $size . $path : null;
        return ['id' => $kind . ':' . (int) $row['id'], 'kind' => $kind, 'title' => $title, 'originalTitle' => is_string($row['original_title'] ?? $row['original_name'] ?? null) ? ($row['original_title'] ?? $row['original_name']) : null, 'releaseYear' => $year, 'overview' => is_string($row['overview'] ?? null) ? $row['overview'] : null, 'rating' => is_numeric($row['vote_average'] ?? null) ? round((float) $row['vote_average'], 1) : null, 'posterUrl' => $image($row['poster_path'] ?? null, 'w500'), 'backdropUrl' => $image($row['backdrop_path'] ?? null, 'w780'), 'ageRating' => null, 'ageRatingFr' => null];
    }

    /** @return list<string> */
    private function videoKeys(mixed $videos): array
    {
        $results = is_array($videos) && is_array($videos['results'] ?? null) ? $videos['results'] : [];
        return array_values(array_filter(array_map(
            static fn (mixed $v): ?string => is_array($v) && ($v['site'] ?? null) === 'YouTube' && is_string($v['key'] ?? null) ? $v['key'] : null,
            $results,
        )));
    }

    /** @param mixed $videos TMDB `{results: [...]}` node — either the top-level `/videos` response or `hydrate()`'s appended `videos` sub-node @return list<array<string, mixed>> */
    private function videoList(mixed $videos): array
    {
        $results = is_array($videos) && is_array($videos['results'] ?? null) ? $videos['results'] : [];
        return array_values(array_filter(array_map(static function (mixed $video): ?array {
            if (!is_array($video) || !is_string($video['key'] ?? null) || !is_string($video['site'] ?? null)) return null;
            return ['key' => $video['key'], 'site' => $video['site'], 'type' => is_string($video['type'] ?? null) ? $video['type'] : null, 'name' => is_string($video['name'] ?? null) ? $video['name'] : null, 'official' => (bool) ($video['official'] ?? false)];
        }, $results)));
    }

    /** @return list<string> */
    private function alternativeTitleStrings(string $kind, mixed $altTitles): array
    {
        $rows = is_array($altTitles) ? ($altTitles[$kind === 'movie' ? 'titles' : 'results'] ?? []) : [];
        if (!is_array($rows)) return [];
        return array_values(array_unique(array_filter(array_map(
            static fn (mixed $row): ?string => is_array($row) && is_string($row['title'] ?? null) && $row['title'] !== '' ? $row['title'] : null,
            $rows,
        ))));
    }

    /** @return list<string> */
    private function genreNameList(mixed $genres): array
    {
        if (!is_array($genres)) return [];
        return array_values(array_filter(array_map(
            static fn (mixed $g): ?string => is_array($g) && is_string($g['name'] ?? null) ? $g['name'] : null,
            $genres,
        )));
    }

    /** @return list<string> */
    private function keywordList(string $kind, mixed $keywords): array
    {
        $rows = is_array($keywords) ? ($keywords[$kind === 'movie' ? 'keywords' : 'results'] ?? []) : [];
        if (!is_array($rows)) return [];
        return array_values(array_filter(array_map(
            static fn (mixed $k): ?string => is_array($k) && is_string($k['name'] ?? null) ? $k['name'] : null,
            $rows,
        )));
    }

    /** @return list<int> */
    private function recommendationTmdbIds(mixed $recommendations): array
    {
        $rows = is_array($recommendations) && is_array($recommendations['results'] ?? null) ? $recommendations['results'] : [];
        return array_values(array_filter(array_map(
            static fn (mixed $r): ?int => is_array($r) && is_numeric($r['id'] ?? null) ? (int) $r['id'] : null,
            $rows,
        )));
    }
}
