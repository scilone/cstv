<?php

declare(strict_types=1);

namespace Cstv\Backend\Catalog;

/**
 * F45 §7.8: resolves `ageRating` (exact age, no bucketing) from TMDB `release_dates`
 * (movies) / `content_ratings` (series) entries. FR is authoritative, then US, then GB;
 * when none has an exploitable certification, falls back to the median of every other
 * territory's numeric certification rather than giving up. Extracted out of
 * `TmdbMediaMetadataProvider` (was inline `mapCertification`/`ageRating`) so the mapping rules
 * are independently testable — see `backend/src/Catalog/MediaMetadataProvider.php` for the
 * adapter boundary this belongs behind (§8.1).
 */
final readonly class TmdbCertificationMapper
{
    /** @var array<string, int> */
    private const FR = ['TP' => 0, 'U' => 0, 'G' => 0, '10' => 10, '10+' => 10, '12' => 12, '12+' => 12, '16' => 16, '16+' => 16, '18' => 18, '18+' => 18];
    /**
     * F45-R10 : les codes cinéma (`G`/`PG`/`PG-13`/`R`/`NC-17`) et les codes TV Parental Guidelines
     * (`TV-Y`…`TV-MA`) partagent la même table — aucune collision de clé entre les deux jeux, et
     * `map()` ne connaît de toute façon pas `movie`/`series` au moment de la résolution (seul
     * `collect()` sait quel champ TMDB lire). Avant ce correctif, une série sans certification FR
     * exploitable tombait sur `null`/médiane au lieu de sa valeur US pourtant la plus courante
     * (`TV-14`, `TV-MA`…), forçant un PIN injustifié ou un âge moins fiable côté F44.
     * @var array<string, int>
     */
    private const US = [
        'G' => 0, 'PG' => 0, 'PG-13' => 13, 'R' => 17, 'NC-17' => 17,
        'TV-Y' => 0, 'TV-G' => 0, 'TV-Y7' => 7, 'TV-PG' => 10, 'TV-14' => 14, 'TV-MA' => 17,
    ];
    /** @var array<string, int> */
    private const GB = ['U' => 0, 'PG' => 0, '12' => 12, '12A' => 12, '15' => 15, '18' => 18, 'R18' => 18];

    /** @param list<array<string, mixed>> $entries TMDB `release_dates.results` @return int|null exact ageRating */
    public function fromReleaseDates(array $entries): ?int
    {
        return $this->resolve($this->collect($entries, movie: true));
    }

    /** @param list<array<string, mixed>> $entries TMDB `content_ratings.results` @return int|null exact ageRating */
    public function fromContentRatings(array $entries): ?int
    {
        return $this->resolve($this->collect($entries, movie: false));
    }

    /** Kept only for older APKs while they migrate to the exact `ageRating` (§8.7). */
    public function legacyBucket(?int $age): ?int
    {
        return match (true) {
            $age === null => null,
            $age <= 0 => 0,
            $age <= 10 => 10,
            $age <= 13 => 12,
            $age <= 16 => 16,
            default => 18,
        };
    }

    /** @param array<string, int> $byCountry most restrictive exploitable value already picked per country */
    private function resolve(array $byCountry): ?int
    {
        foreach (['FR', 'US', 'GB'] as $country) {
            if (isset($byCountry[$country])) return $byCountry[$country];
        }
        $others = array_values(array_diff_key($byCountry, ['FR' => true, 'US' => true, 'GB' => true]));
        if ($others === []) return null;
        sort($others);
        $count = count($others);
        $mid = intdiv($count, 2);
        // Médiane paire -> arrondi au supérieur (§4.2).
        return $count % 2 === 1 ? $others[$mid] : (int) ceil(($others[$mid - 1] + $others[$mid]) / 2);
    }

    /** @param list<array<string, mixed>> $entries @return array<string, int> most restrictive mapped value per ISO country */
    private function collect(array $entries, bool $movie): array
    {
        $byCountry = [];
        foreach ($entries as $entry) {
            if (!is_array($entry)) continue;
            $country = $entry['iso_3166_1'] ?? null;
            if (!is_string($country) || $country === '') continue;
            foreach ($this->rawCertifications($entry, $movie) as $raw) {
                if (!is_string($raw) || trim($raw) === '') continue; // pas de certification renseignée
                $mapped = $this->map($country, trim($raw));
                if ($mapped === null) continue;
                $byCountry[$country] = isset($byCountry[$country]) ? max($byCountry[$country], $mapped) : $mapped;
            }
        }
        return $byCountry;
    }

    /** @param array<string, mixed> $entry @return list<mixed> */
    private function rawCertifications(array $entry, bool $movie): array
    {
        if (!$movie) return [$entry['rating'] ?? null];
        $releaseDates = $entry['release_dates'] ?? null;
        if (!is_array($releaseDates)) return [];
        return array_map(static fn (mixed $rd): mixed => is_array($rd) ? ($rd['certification'] ?? null) : null, $releaseDates);
    }

    private function map(string $country, string $certification): ?int
    {
        return match ($country) {
            'FR' => self::FR[$certification] ?? null,
            'US' => self::US[$certification] ?? null,
            'GB' => self::GB[$certification] ?? null,
            default => $this->genericNumeric($certification),
        };
    }

    /**
     * Beaucoup de territoires (DE/FSK, NL/Kijkwijzer, ES, PT, BR, CH, pays nordiques...) publient
     * directement l'âge minimal sous forme numérique dans TMDB : couvre ce cas générique plutôt que
     * de maintenir une table par pays pour la médiane §7.8, qui n'est qu'un filet de repli.
     */
    private function genericNumeric(string $certification): ?int
    {
        if (preg_match('/^(\d{1,2})\+?$/', $certification, $matches) === 1) {
            $age = (int) $matches[1];
            return $age <= 21 ? $age : null;
        }
        return match (strtoupper($certification)) {
            'AL', 'G', 'U' => 0,
            '14A' => 14,
            'MA15+' => 15,
            '18A', 'R18+' => 18,
            default => null,
        };
    }
}
