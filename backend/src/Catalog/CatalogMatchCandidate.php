<?php

declare(strict_types=1);

namespace Cstv\Backend\Catalog;

/** F45 §8.6: a lightweight passe-1 search hit — no detail call spent yet. */
final readonly class CatalogMatchCandidate
{
    /** @param list<int> $genreIds */
    public function __construct(
        public int $tmdbId,
        public string $title,
        public ?string $originalTitle,
        public ?int $year,
        public array $genreIds,
    ) {}
}
