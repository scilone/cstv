<?php

declare(strict_types=1);

namespace Cstv\Backend\Catalog;

/**
 * F45 hotfix : TMDB ordonne déjà les résultats de recherche. CSTV hydrate donc le premier résultat
 * avec les seules données de matching utiles : type, titre et année.
 *
 * T28 §8.4 : le backend consolidé (PostgreSQL) devient la première source lorsqu'il possède déjà
 * une correspondance sûre — titre+année exact et unique (§8.2). TMDB reste le fallback dès qu'aucune
 * résolution backend stricte n'est possible, et conserve sa règle premier-résultat inchangée. Un
 * `tmdbId` retourné par TMDB `/search` mais déjà hydraté en PostgreSQL est resservi sans appel
 * `hydrate()` (§8.3) ; seule une identité connue sans fiche complète déclenche encore l'hydratation.
 *
 * Review T28/R1 : toute réutilisation (backend-first ou par `tmdbId` après `/search`) est
 * conditionnée à la locale de la requête — une fiche hydratée en `fr-FR` n'est jamais resservie à une
 * requête `en-US`, elle est réhydratée dans la bonne locale (`reuseStored()`).
 */
final readonly class CatalogMatchEngine
{
    public const ALGORITHM_VERSION = 3;

    public function __construct(
        private MediaMetadataProvider $provider,
        private ExternalMediaRepository $externalMedia,
        private CatalogItemPresenter $presenter = new CatalogItemPresenter(),
    ) {}

    /**
     * @param array{externalId: string, method: string}|array{}|null $precomputedStrict T29 §8.5/§8.6 :
     *     résultat déjà calculé par le lookup PostgreSQL-first en lot (`CatalogService::matchBatchItems()`).
     *     `null` (défaut, chemin unitaire) : ce calcul est fait ici. Tableau vide `[]` : le lot l'a déjà
     *     tenté sans succès, ne pas relancer une seconde requête SQL. Tableau non vide : réutiliser tel quel.
     */
    public function resolve(CatalogMatchRequest $request, ?array $precomputedStrict = null): CatalogMatchResult
    {
        $strict = $precomputedStrict !== null
            ? ($precomputedStrict !== [] ? $precomputedStrict : null)
            : ($request->year !== null ? $this->externalMedia->findStrictConsolidatedMatch($request->kind, $request->title, $request->year, $request->locale) : null);
        if ($strict !== null) {
            $reused = $this->reuseStored($request->kind, $strict['externalId'], $strict['method'], $request->locale);
            if ($reused !== null) return $reused;
        }

        $candidates = $this->provider->searchCandidates($request->kind, $request->title, $request->year, $request->locale);
        if ($candidates === []) return CatalogMatchResult::notFound(self::ALGORITHM_VERSION);

        $candidate = $candidates[0];
        $existingId = $this->externalMedia->findByTmdb($request->kind, $candidate->tmdbId);
        if ($existingId !== null) {
            $reused = $this->reuseStored($request->kind, $existingId, 'tmdb-first-result-existing', $request->locale);
            if ($reused !== null) return $reused;
        }

        // Identité déjà connue (`tmdb_media`) mais sans fiche `tmdb_movies`/`tmdb_series` (R6), ou
        // fiche existante dans une autre locale (R1) : on la réutilise plutôt que d'en recréer une,
        // mais l'hydratation reste nécessaire.
        $externalId = $existingId ?? $this->externalMedia->findOrCreateForTmdb($request->kind, $candidate->tmdbId);
        $hydrated = $this->provider->hydrate($request->kind, $candidate->tmdbId, $request->locale);
        $hydrated['recommendations'] = array_map(
            fn (int $tmdbId): string => $this->externalMedia->findOrCreateForTmdb($request->kind, $tmdbId),
            $hydrated['recommendations'] ?? [],
        );

        if ($request->kind === 'movie') {
            $this->externalMedia->persistMovie($externalId, $hydrated, $request->locale);
        } else {
            $this->externalMedia->persistSeries($externalId, $hydrated, $request->locale);
        }

        $row = $request->kind === 'movie' ? $this->externalMedia->getMovie($externalId) : $this->externalMedia->getSeries($externalId);
        if ($row === null) return CatalogMatchResult::unresolved(self::ALGORITHM_VERSION);

        return CatalogMatchResult::matched(
            $externalId,
            0,
            'tmdb-first-result',
            self::ALGORITHM_VERSION,
            $this->presenter->present($request->kind, $externalId, $row),
        );
    }

    /**
     * T28 §8.6/R6 : ne retourne un résultat que si la fiche complète (`tmdb_movies`/`tmdb_series`)
     * existe déjà — une simple ligne `tmdb_media` ne suffit pas et laisse l'appelant hydrater.
     *
     * Review T28/R1 : et seulement si sa locale d'hydratation correspond à celle demandée — le
     * lookup backend-first (`findStrictConsolidatedMatch()`) filtre déjà sur la locale, mais la
     * réutilisation par `tmdbId` après `/search` (§8.3) n'a aucune garantie de ce type, donc ce
     * contrôle reste nécessaire ici pour couvrir les deux appelants.
     */
    private function reuseStored(string $kind, string $externalId, string $method, string $locale): ?CatalogMatchResult
    {
        $row = $kind === 'movie' ? $this->externalMedia->getMovie($externalId) : $this->externalMedia->getSeries($externalId);
        if ($row === null || $row['locale'] !== $locale) return null;

        return CatalogMatchResult::matched($externalId, 0, $method, self::ALGORITHM_VERSION, $this->presenter->present($kind, $externalId, $row));
    }
}
