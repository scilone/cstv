<?php

declare(strict_types=1);

namespace Cstv\Backend\Catalog;

interface MediaMetadataProvider
{
    /** @return list<array<string, mixed>> */
    public function trending(string $locale): array;
    /** @return list<array<string, mixed>> */
    public function popular(string $kind, int $page, string $locale): array;
    /** @return list<array<string, mixed>> */
    public function videos(string $canonicalId, string $locale): array;

    /** Passe 1 (§7.11) : recherche large, sans appel détail. @return list<CatalogMatchCandidate> */
    public function searchCandidates(string $kind, string $title, ?int $year, string $locale): array;

    /**
     * Passe 2 (§7.11) : détail ciblé d'un candidat ambigu pour désambiguïser (director/cast/runtime/
     * trailer/titres alternatifs). Les crédits ne sont jamais persistés (§4.1/§8.15), seulement lus
     * pour scorer. @return array<string, mixed>
     */
    public function candidateDetail(string $kind, int $tmdbId, string $locale): array;

    /** @return array<int, string> id TMDB -> nom de genre, pour interpréter les `genre_ids` de recherche */
    public function genreNames(string $kind, string $locale): array;

    /**
     * Fiche complète prête à persister (§7.2/§7.3). `recommendations` reste une liste d'IDs TMDB
     * bruts — c'est `CatalogMatchEngine` qui les résout en externalId (§8.2), jamais l'adapter.
     * @return array<string, mixed>
     */
    public function hydrate(string $kind, int $tmdbId, string $locale): array;

    /** Saison + épisodes (§7.4) — jamais appelé pendant sync/backfill, uniquement à l'ouverture d'une fiche série. @return array<string, mixed> */
    public function seasonDetail(int $seriesTmdbId, int $seasonNumber, string $locale): array;
}
