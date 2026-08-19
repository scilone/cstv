<?php

declare(strict_types=1);

namespace Cstv\Backend\Catalog;

final readonly class TmdbMediaMetadataProvider implements MediaMetadataProvider
{
    public function __construct(private TmdbClient $client) {}

    public function trending(string $locale): array { return $this->items($this->client->get('trending/all/week', ['language' => $locale]), $locale); }
    public function popular(string $kind, int $page, string $locale): array { return $this->items($this->client->get(($kind === 'movie' ? 'movie' : 'tv') . '/popular', ['language' => $locale, 'page' => $page]), $locale); }
    public function match(string $kind, string $title, ?int $year, string $locale): ?array
    {
        $query = ['query' => $title, 'language' => $locale];
        if ($year !== null) $query[$kind === 'movie' ? 'year' : 'first_air_date_year'] = $year;
        $response = $this->client->get('search/' . ($kind === 'movie' ? 'movie' : 'tv'), $query);
        $items = $this->items($response, $locale, true);
        return $items[0] ?? null;
    }
    public function videos(string $canonicalId, string $locale): array
    {
        [$kind, $id] = explode(':', $canonicalId, 2) + [null, null];
        if (!in_array($kind, ['movie', 'series'], true) || !ctype_digit((string) $id)) throw new CatalogProviderException(502, 'Catalog identifier is invalid.');
        $response = $this->client->get(($kind === 'movie' ? 'movie' : 'tv') . '/' . $id . '/videos', ['language' => $locale]);
        $videos = $response['results'] ?? [];
        if (!is_array($videos)) return [];
        return array_values(array_filter(array_map(static function (mixed $video): ?array {
            if (!is_array($video) || !is_string($video['key'] ?? null) || !is_string($video['site'] ?? null)) return null;
            return ['site' => $video['site'], 'key' => $video['key'], 'type' => is_string($video['type'] ?? null) ? $video['type'] : null, 'official' => (bool) ($video['official'] ?? false)];
        }, $videos)));
    }
    /** @param array<string, mixed> $response @return list<array<string, mixed>> */
    private function items(array $response, string $locale, bool $includeAgeRating = false): array
    {
        $results = $response['results'] ?? [];
        if (!is_array($results)) return [];
        return array_values(array_filter(array_map(fn (mixed $row): ?array => $this->item($row, $locale, $includeAgeRating), $results)));
    }
    /** @return array<string, mixed>|null */
    private function item(mixed $row, string $locale, bool $includeAgeRating): ?array
    {
        if (!is_array($row) || !is_numeric($row['id'] ?? null)) return null;
        $mediaType = $row['media_type'] ?? null;
        if ($mediaType !== null && !in_array($mediaType, ['movie', 'tv'], true)) return null;
        $kind = $mediaType === 'tv' || ($mediaType === null && isset($row['name'])) ? 'series' : 'movie';
        $title = $row[$kind === 'movie' ? 'title' : 'name'] ?? null;
        if (!is_string($title) || $title === '') return null;
        $date = $row[$kind === 'movie' ? 'release_date' : 'first_air_date'] ?? null;
        $year = is_string($date) && preg_match('/^(\\d{4})-/', $date, $match) ? (int) $match[1] : null;
        $image = static fn (mixed $path, string $size): ?string => is_string($path) && $path !== '' ? 'https://image.tmdb.org/t/p/' . $size . $path : null;
        return ['id' => $kind . ':' . (int) $row['id'], 'kind' => $kind, 'title' => $title, 'originalTitle' => is_string($row['original_title'] ?? $row['original_name'] ?? null) ? ($row['original_title'] ?? $row['original_name']) : null, 'releaseYear' => $year, 'overview' => is_string($row['overview'] ?? null) ? $row['overview'] : null, 'rating' => is_numeric($row['vote_average'] ?? null) ? round((float) $row['vote_average'], 1) : null, 'posterUrl' => $image($row['poster_path'] ?? null, 'w500'), 'backdropUrl' => $image($row['backdrop_path'] ?? null, 'w780'), 'ageRatingFr' => $includeAgeRating ? $this->ageRatingFr($kind, (int) $row['id'], $locale) : null];
    }

    /**
     * FR est la source d'autorité. Quand TMDb n'a pas de certification FR
     * (fréquent pour des œuvres hors France), on retombe sur US puis GB pour
     * ne pas priver le contrôle parental d'une info disponible ailleurs.
     * Absence totale d'info -> `null`, jamais `0` : `0` signifie « tout
     * public confirmé », pas « inconnu » (règle défensive F44, §8.2).
     */
    private function ageRatingFr(string $kind, int $id, string $locale): ?int
    {
        $response = $this->client->get(($kind === 'movie' ? 'movie' : 'tv') . '/' . $id . ($kind === 'movie' ? '/release_dates' : '/content_ratings'), ['language' => $locale]);
        $entries = $response['results'] ?? [];
        if (!is_array($entries)) return null;
        foreach (['FR', 'US', 'GB'] as $country) {
            foreach ($entries as $entry) {
                if (!is_array($entry) || ($entry['iso_3166_1'] ?? null) !== $country) continue;
                $raw = $kind === 'movie' ? ($entry['release_dates'][0]['certification'] ?? null) : ($entry['rating'] ?? null);
                if (!is_string($raw) || trim($raw) === '') continue; // pas de certification renseignée pour ce pays
                $mapped = $this->mapCertification($country, trim($raw));
                if ($mapped !== null) return $mapped;
            }
        }
        return null;
    }

    /** Convertit une certification pays vers l'échelle FR (0/10/12/16/18). */
    private function mapCertification(string $country, string $certification): ?int
    {
        return match ($country) {
            'FR' => match ($certification) {
                'TP', 'U', 'G' => 0,
                '10', '10+' => 10,
                '12', '12+' => 12,
                '16', '16+' => 16,
                '18', '18+' => 18,
                default => null,
            },
            'US' => match ($certification) {
                'G', 'PG' => 0,
                'PG-13' => 12,
                'R' => 16,
                'NC-17' => 18,
                default => null,
            },
            'GB' => match ($certification) {
                'U', 'PG' => 0,
                '12', '12A' => 12,
                '15' => 16,
                '18', 'R18' => 18,
                default => null,
            },
            default => null,
        };
    }
}
