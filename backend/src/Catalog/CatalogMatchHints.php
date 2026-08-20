<?php

declare(strict_types=1);

namespace Cstv\Backend\Catalog;

/** F45 §7.11: every hint is optional — IPTV metadata is often incomplete or wrong. */
final readonly class CatalogMatchHints
{
    /** @param list<string> $actors @param list<string> $genres */
    public function __construct(
        public ?string $director = null,
        public array $actors = [],
        public array $genres = [],
        public ?int $runtimeMinutes = null,
        public ?string $youtubeTrailerKey = null,
    ) {}

    /** @param array<string, mixed> $body */
    public static function fromArray(array $body): self
    {
        $hints = $body['hints'] ?? null;
        if (!is_array($hints)) return new self();
        return new self(
            director: is_string($hints['director'] ?? null) && $hints['director'] !== '' ? $hints['director'] : null,
            actors: self::stringList($hints['actors'] ?? null),
            genres: self::stringList($hints['genres'] ?? null),
            runtimeMinutes: is_int($hints['runtimeMinutes'] ?? null) && $hints['runtimeMinutes'] > 0 ? $hints['runtimeMinutes'] : null,
            youtubeTrailerKey: is_string($hints['youtubeTrailerKey'] ?? null) && $hints['youtubeTrailerKey'] !== '' ? $hints['youtubeTrailerKey'] : null,
        );
    }

    /** @return list<string> */
    private static function stringList(mixed $value): array
    {
        if (!is_array($value)) return [];
        // Hints bornés en taille/nombre (§8.15) : au-delà, un signal supplémentaire n'améliore pas le score.
        return array_values(array_filter(array_slice($value, 0, 20), static fn (mixed $item): bool => is_string($item) && $item !== '' && mb_strlen($item) <= 200));
    }
}
