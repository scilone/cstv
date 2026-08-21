<?php

declare(strict_types=1);

namespace Cstv\Backend\Catalog;

/**
 * F45 hotfix : TMDB ordonne déjà les résultats de recherche. CSTV hydrate donc le premier résultat
 * avec les seules données de matching utiles : type, titre et année.
 */
final readonly class CatalogMatchEngine
{
    public const ALGORITHM_VERSION = 2;

    public function __construct(
        private MediaMetadataProvider $provider,
        private ExternalMediaRepository $externalMedia,
        private CatalogItemPresenter $presenter = new CatalogItemPresenter(),
    ) {}

    public function resolve(CatalogMatchRequest $request): CatalogMatchResult
    {
        $candidates = $this->provider->searchCandidates($request->kind, $request->title, $request->year, $request->locale);
        if ($candidates === []) return CatalogMatchResult::notFound(self::ALGORITHM_VERSION);

        $candidate = $candidates[0];
        $externalId = $this->externalMedia->findOrCreateForTmdb($request->kind, $candidate->tmdbId);
        $hydrated = $this->provider->hydrate($request->kind, $candidate->tmdbId, $request->locale);
        $hydrated['recommendations'] = array_map(
            fn (int $tmdbId): string => $this->externalMedia->findOrCreateForTmdb($request->kind, $tmdbId),
            $hydrated['recommendations'] ?? [],
        );

        if ($request->kind === 'movie') {
            $this->externalMedia->persistMovie($externalId, $hydrated);
        } else {
            $this->externalMedia->persistSeries($externalId, $hydrated);
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
}
