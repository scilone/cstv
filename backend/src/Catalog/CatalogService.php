<?php

declare(strict_types=1);

namespace Cstv\Backend\Catalog;

use Cstv\Backend\Shared\ApiException;
use Cstv\Backend\Shared\Uuid;
use Cstv\Backend\Database\AdvisoryLock;
use PDO;
use Throwable;

final readonly class CatalogService
{
    // T29 §11 : valeurs de tuning initiales (hypothèse §4.2), à confirmer par métriques post-release
    // — le plafond de 50 items/batch borne déjà le coût maximum d'une requête (§7.4).
    private const MATCH_REQUESTS_PER_MINUTE_ACCOUNT = 30;
    private const MATCH_REQUESTS_PER_MINUTE_IP = 60;

    public function __construct(
        private PDO $pdo,
        private MediaMetadataCacheRepository $cache,
        private MediaMetadataProvider $provider,
        private CatalogMatchThrottleRepository $matchThrottle,
        private CatalogMatchEngine $matchEngine,
        private ExternalMediaRepository $externalMedia,
        private TmdbImageUrlResolver $images = new TmdbImageUrlResolver(),
        private CatalogItemPresenter $presenter = new CatalogItemPresenter(),
    ) {}

    /** @return array<string, mixed> */
    public function trending(string $locale): array { return $this->resolve('trending', ['locale' => $locale], 21600, fn () => ['items' => $this->withExternalBrowseIds($this->provider->trending($locale))]); }
    /** @return array<string, mixed> */
    public function popular(string $kind, int $page, string $locale): array { return $this->resolve('popular', compact('kind', 'page', 'locale'), 21600, fn () => ['items' => $this->withExternalBrowseIds($this->provider->popular($kind, $page, $locale))]); }

    /** @return array<string, mixed> */
    public function match(CatalogMatchRequest $request, string $accountId, string $ipKey, DeviceType $device): array
    {
        $this->throttleMatchRequest($accountId, $ipKey);
        return $this->matchWithoutThrottle($request, $device);
    }

    /**
     * T29 §8.3/§8.6 : le throttle CSTV compte désormais une seule unité de requête, indépendamment
     * du nombre d'items — protéger le endpoint, pas le fournisseur (§7.4). Le pipeline lui-même
     * (cache bulk → PostgreSQL-first bulk → provider par item, isolé) vit dans `matchBatchItems()`.
     * @param list<CatalogMatchRequest> $requests @return array{items: list<array<string, mixed>>}
     */
    public function matchBatch(array $requests, string $accountId, string $ipKey, DeviceType $device): array
    {
        $this->throttleMatchRequest($accountId, $ipKey);
        return ['items' => $this->matchBatchItems($requests, $device)];
    }

    /**
     * §7.2 Flux batch cible : cache bulk → PostgreSQL backend-first bulk (misses seulement) →
     * provider par item (misses restants, isolé §8.8) → ordre d'origine préservé (§7.6).
     * @param list<CatalogMatchRequest> $requests @return list<array<string, mixed>>
     */
    private function matchBatchItems(array $requests, DeviceType $device): array
    {
        if ($requests === []) return [];

        // Tâche 4 §8.4 : un SELECT bulk au lieu de N pour les hits déjà frais en cache.
        $keys = array_map(fn (CatalogMatchRequest $request): string => $this->matchCacheKey($request), $requests);
        $cacheHits = $this->cache->findMany($keys);

        $results = array_fill(0, count($requests), null);
        $pending = [];
        foreach ($requests as $index => $request) {
            $hit = $cacheHits[$keys[$index]] ?? null;
            if ($hit !== null && $hit['fresh']) {
                $results[$index] = $this->finishMatchPayload($this->response($hit['payload'], $hit['status'], false, true), $device);
            } else {
                $pending[$index] = $request;
            }
        }

        if ($pending !== []) {
            // Tâche 5 §8.5 : lookup backend-first bulk pour les misses restants (année connue
            // uniquement — même contrainte que le chemin unitaire, §8.2 R3).
            $strictInputs = [];
            foreach ($pending as $index => $request) {
                if ($request->year !== null) {
                    $strictInputs[] = ['index' => $index, 'kind' => $request->kind, 'title' => $request->title, 'year' => $request->year, 'locale' => $request->locale];
                }
            }
            $strictMatches = $strictInputs !== [] ? $this->externalMedia->findStrictConsolidatedMatchBatch($strictInputs) : [];

            foreach ($pending as $index => $request) {
                $results[$index] = $this->matchBatchItem($request, $device, $strictMatches[$index] ?? []);
            }
        }

        return $results;
    }

    /**
     * Tâche 2/8.8 : isole les erreurs provider par item — un 429 fournisseur (budget local épuisé,
     * `TmdbProviderRateLimiter`) ou une indisponibilité TMDB transitoire (`CatalogProviderException`
     * remontée par `resolve()` sous forme d'`ApiException` 502/503/429) devient un résultat `retry`
     * pour cet item seul, sans invalider les succès déjà calculés dans le lot (§7.6). Toute autre
     * erreur (bug interne, 4xx inattendu) continue de se propager — §8.8 "erreurs non retryables/bugs
     * internes ne doivent pas être silencieusement converties en retry".
     * @param array{externalId: string, method: string}|array{} $precomputedStrict
     * @return array<string, mixed>
     */
    private function matchBatchItem(CatalogMatchRequest $request, DeviceType $device, array $precomputedStrict): array
    {
        try {
            return $this->matchWithoutThrottle($request, $device, $precomputedStrict);
        } catch (ApiException $error) {
            if (!in_array($error->status, [429, 502, 503], true)) throw $error;
            $result = CatalogMatchResult::retry(CatalogMatchEngine::ALGORITHM_VERSION, $error->retryAfterSeconds);
            return ['status' => $result->status, 'match' => null, 'item' => null, 'cache' => ['retryAfter' => $result->retryAfterSeconds]];
        }
    }

    /**
     * @param array{externalId: string, method: string}|array{}|null $precomputedStrict `null` (défaut,
     *     chemin unitaire `match()`) laisse `CatalogMatchEngine` calculer lui-même le lookup backend-first.
     * @return array<string, mixed>
     */
    private function matchWithoutThrottle(CatalogMatchRequest $request, DeviceType $device, ?array $precomputedStrict = null): array
    {
        $args = $this->matchCacheArgs($request);
        $payload = $this->resolve('match', $args, 604800, fn () => $this->fromMatchResult($this->matchEngine->resolve($request, $precomputedStrict)), true);
        return $this->finishMatchPayload($payload, $device);
    }

    /** Résolution du poster/backdrop selon le device + fenêtre d'hydratation — factorisé pour être partagé par le hit cache bulk (§8.4) et le chemin de résolution complet. @param array<string, mixed> $payload @return array<string, mixed> */
    private function finishMatchPayload(array $payload, DeviceType $device): array
    {
        $item = $payload['item'] ?? null;
        if (!is_array($item)) return $payload;
        $payload['item'] = $this->withDeviceImages($item, $device);
        $window = is_string($item['externalId'] ?? null) ? $this->externalMedia->hydrationWindow($item['externalId']) : null;
        if ($window !== null) $payload['cache'] = [...$payload['cache'], ...$window];
        return $payload;
    }

    /** @return array<string, mixed> */
    private function matchCacheArgs(CatalogMatchRequest $request): array
    {
        return ['kind' => $request->kind, 'title' => $request->title, 'year' => $request->year, 'locale' => $request->locale, 'hints' => 'ignored', 'v' => CatalogMatchEngine::ALGORITHM_VERSION];
    }

    /** T29 §8.4 : même formule de clé que `resolve()`, calculable sans exécuter le chargement — nécessaire pour le lookup bulk `MediaMetadataCacheRepository::findMany()`. */
    private function matchCacheKey(CatalogMatchRequest $request): string
    {
        return $this->cacheKey('match', $this->matchCacheArgs($request));
    }

    /** Accepts an opaque `externalId` UUID (new app) or the legacy `movie:<id>`/`series:<id>` form (§8.7). @return array<string, mixed> */
    public function videos(string $identifier, string $locale): array
    {
        $canonicalId = Uuid::isValid($identifier) ? $this->externalMedia->tmdbCanonicalId($identifier) : $identifier;
        if ($canonicalId === null) throw new ApiException(404, 'CATALOG_ITEM_NOT_FOUND', 'Unknown catalog identifier.');
        return $this->resolve('videos', compact('canonicalId', 'locale'), 604800, fn () => ['items' => $this->provider->videos($canonicalId, $locale)]);
    }

    /** @return array<string, mixed> */
    public function item(string $externalId, DeviceType $device): array
    {
        $found = $this->findItemRow($externalId) ?? throw new ApiException(404, 'CATALOG_ITEM_NOT_FOUND', 'Unknown externalId.');
        return $this->withDeviceImages($this->presenter->present($found['kind'], $externalId, $found['row']), $device);
    }

    /** §7.6 : 20 premières, ordre conservé, externalIds seulement — la cible peut ne pas être hydratée. @return array<string, mixed> */
    public function recommendations(string $externalId): array
    {
        $found = $this->findItemRow($externalId) ?? throw new ApiException(404, 'CATALOG_ITEM_NOT_FOUND', 'Unknown externalId.');
        return ['items' => array_map(static fn (string $id): array => ['externalId' => $id], $found['row']['recommendations'])];
    }

    /** @return array<string, mixed> */
    public function season(string $externalId, int $seasonNumber, DeviceType $device): array
    {
        $tmdbId = $this->externalMedia->seriesTmdbId($externalId) ?? throw new ApiException(404, 'CATALOG_ITEM_NOT_FOUND', 'Unknown externalId.');
        return $this->presentSeason($this->refreshSeasonIfStale($externalId, $seasonNumber, $tmdbId), $device);
    }

    /**
     * F45-R8 : verrouille (`AdvisoryLock::season`) et transactionne tout le cycle
     * lecture-fraîcheur-fournisseur-remplacement — avant ce correctif, `season()` n'avait ni
     * single-flight (deux ouvertures simultanées de la même fiche pouvaient chacune appeler le
     * fournisseur puis écrire) ni transaction englobant `persistSeason()` (une erreur à mi-parcours
     * pouvait laisser des épisodes obsolètes à côté d'une saison déjà marquée fraîche). Le verrou
     * étant transaction-scoped (`pg_advisory_xact_lock`), un second appelant bloqué ici retrouve, une
     * fois débloqué, une saison déjà rafraîchie par le premier et relit simplement — aucun second
     * appel fournisseur.
     * @return array<string, mixed>
     */
    private function refreshSeasonIfStale(string $externalId, int $seasonNumber, int $tmdbId): array
    {
        $this->pdo->beginTransaction();
        try {
            AdvisoryLock::season($this->pdo, $externalId, $seasonNumber);
            $stored = $this->externalMedia->getSeason($externalId, $seasonNumber);
            if ($stored === null || $this->externalMedia->seasonIsStale($stored)) {
                $seriesInProduction = (bool) ($this->externalMedia->getSeries($externalId)['in_production'] ?? false);
                try {
                    $fresh = $this->provider->seasonDetail($tmdbId, $seasonNumber, 'fr-FR');
                    $this->externalMedia->persistSeason($externalId, $seasonNumber, $fresh, $seriesInProduction);
                    $stored = $this->externalMedia->getSeason($externalId, $seasonNumber);
                } catch (CatalogProviderException $error) {
                    // §7.13 mode dégradé : bonne donnée stale conservée si le fournisseur échoue —
                    // rien n'a été écrit sur ce chemin, seule la lecture précédente est réutilisée.
                    if ($stored === null) { $this->pdo->rollBack(); throw new ApiException($error->status, 'CATALOG_PROVIDER_UNAVAILABLE', 'Catalog enrichment is temporarily unavailable.'); }
                }
            }
            $this->pdo->commit();
            return $stored;
        } catch (Throwable $error) {
            if ($this->pdo->inTransaction()) $this->pdo->rollBack();
            throw $error;
        }
    }

    /** @return array{kind: string, row: array<string, mixed>}|null */
    private function findItemRow(string $externalId): ?array
    {
        $row = $this->externalMedia->getMovie($externalId);
        if ($row !== null) return ['kind' => 'movie', 'row' => $row];
        $row = $this->externalMedia->getSeries($externalId);
        return $row !== null ? ['kind' => 'series', 'row' => $row] : null;
    }

    /** @param array<string, mixed> $season @return array<string, mixed> */
    private function presentSeason(array $season, DeviceType $device): array
    {
        $season['posterUrl'] = $this->images->resolve($season['poster_path'] ?? null, ImageContext::PosterSeason, $device);
        return [
            'seasonNumber' => (int) $season['season_number'],
            'name' => $season['name'],
            'overview' => $season['overview'],
            'posterUrl' => $this->images->resolve($season['poster_path'] ?? null, ImageContext::PosterSeason, $device),
            'airDate' => $season['air_date'],
            'voteAverage' => $season['vote_average'] !== null ? (float) $season['vote_average'] : null,
            'episodes' => array_map(fn (array $episode): array => $this->presentEpisode($episode, $device), $season['episodes'] ?? []),
        ];
    }

    /** @param array<string, mixed> $episode @return array<string, mixed> */
    private function presentEpisode(array $episode, DeviceType $device): array
    {
        return [
            'episodeNumber' => (int) $episode['episode_number'],
            'name' => $episode['name'],
            'overview' => $episode['overview'],
            'stillUrl' => $this->images->resolve($episode['still_path'] ?? null, ImageContext::StillEpisode, $device),
            'airDate' => $episode['air_date'],
            'runtimeMinutes' => $episode['runtime_minutes'] !== null ? (int) $episode['runtime_minutes'] : null,
            'voteAverage' => $episode['vote_average'] !== null ? (float) $episode['vote_average'] : null,
            'voteCount' => $episode['vote_count'] !== null ? (int) $episode['vote_count'] : null,
        ];
    }

    /** @param array<string, mixed> $args @return array<string, mixed> */
    private function fromMatchResult(CatalogMatchResult $result): array
    {
        return ['status' => $result->status, 'item' => $result->item, 'confidence' => $result->confidence, 'method' => $result->method, 'version' => $result->version];
    }

    /** @param array<string, mixed> $args @param callable(): array<string, mixed> $load @return array<string, mixed> */
    private function resolve(string $operation, array $args, int $ttl, callable $load, bool $match = false): array
    {
        $key = $this->cacheKey($operation, $args);
        $cached = $this->cache->find($key);
        if ($cached !== null && $cached['fresh']) return $this->response($cached['payload'], $cached['status'], false, $match);
        $this->pdo->beginTransaction();
        try {
            if (!$this->cache->tryLock($key)) {
                $this->pdo->commit();
                if ($cached !== null && $cached['usableStale']) return $this->response($cached['payload'], $cached['status'], true, $match);
                throw new ApiException(503, 'CATALOG_PROVIDER_UNAVAILABLE', 'Catalog enrichment is temporarily unavailable.');
            }
            $cached = $this->cache->find($key);
            if ($cached !== null && $cached['fresh']) { $this->pdo->commit(); return $this->response($cached['payload'], $cached['status'], false, $match); }
            try { $payload = $load(); } catch (CatalogProviderException $error) {
                if ($cached !== null && $cached['usableStale']) { $this->pdo->commit(); return $this->response($cached['payload'], $cached['status'], true, $match); }
                // F45-R8 : republie le `Retry-After` TMDB (429) reçu par `TmdbClient` — sans donnée
                // stale à servir, le client sait désormais réellement quand réessayer plutôt que de
                // marteler immédiatement un provider déjà à quota.
                throw new ApiException($error->status, $error->status === 502 ? 'CATALOG_PROVIDER_BAD_RESPONSE' : 'CATALOG_PROVIDER_UNAVAILABLE', 'Catalog enrichment is temporarily unavailable.', $error->retryAfterSeconds);
            }
            if (!$match && isset($payload['items']) && is_array($payload['items']) && $payload['items'] === []) {
                if ($cached !== null && $cached['usableStale']) { $this->pdo->commit(); return $this->response($cached['payload'], $cached['status'], true, false); }
                throw new ApiException(503, 'CATALOG_PROVIDER_UNAVAILABLE', 'Catalog enrichment is temporarily unavailable.');
            }
            $status = $match ? $payload['status'] : 'matched';
            $ttlSeconds = in_array($status, ['not_found', 'unresolved'], true) ? 86400 : $ttl;
            if ($operation === 'match' && $status === 'matched') {
                $ttlSeconds = $this->calculateDynamicTtl($payload['item']['releaseYear'] ?? null);
            }
            $this->cache->put($key, $payload, $status, $ttlSeconds, 604800);
            $this->cache->purge();
            $this->pdo->commit();
            return $this->response($payload, $status, false, $match);
        } catch (Throwable $error) { if ($this->pdo->inTransaction()) $this->pdo->rollBack(); throw $error; }
    }
    private function calculateDynamicTtl(?int $releaseYear): int
    {
        if ($releaseYear === null || $releaseYear <= 0) return 1296000; // 15 jours par défaut si inconnu
        $currentYear = (int) date('Y');
        $age = $currentYear - $releaseYear;
        if ($age < 1) return 604800; // 7 jours (moins d'un an)
        if ($age < 5) return 2592000; // 30 jours (1 à 4 ans inclus)
        if ($age < 10) return 7776000; // 90 jours (5 à 9 ans inclus)
        return 15552000; // 180 jours (10 ans et +)
    }
    /** @param array<string, mixed> $payload @return array<string, mixed> */
    private function response(array $payload, string $status, bool $stale, bool $match): array
    {
        if (!$match) return $payload + ['cache' => ['stale' => $stale]];
        return [
            'status' => $status,
            'match' => $payload['confidence'] !== null ? ['confidence' => $payload['confidence'], 'method' => $payload['method'], 'version' => $payload['version']] : null,
            'item' => $payload['item'],
            'cache' => ['stale' => $stale],
        ];
    }
    /** @param array<string, mixed> $args */
    private function cacheKey(string $operation, array $args): string
    {
        return hash('sha256', 'v1|' . $operation . '|' . json_encode($this->normaliseArgs($args), JSON_THROW_ON_ERROR));
    }
    /** @param array<string, mixed> $args @return array<string, mixed> */
    private function normaliseArgs(array $args): array
    {
        foreach ($args as $key => $value) {
            if (!is_string($value)) continue;
            $value = trim(preg_replace('/\\s+/u', ' ', $value) ?? $value);
            $args[$key] = in_array($key, ['title', 'locale'], true)
                ? mb_strtolower(iconv('UTF-8', 'ASCII//TRANSLIT//IGNORE', $value) ?: $value)
                : $value;
        }
        ksort($args);
        return $args;
    }
    /**
     * T29 §8.3 : quota de **requêtes** de matching plutôt que de médias — un batch de `MAX_ITEMS_PER_BATCH`
     * items ne coûte qu'une unité, indépendamment de sa taille (§7.4). Protège le endpoint HTTP contre
     * l'abus (fréquence/compte/IP), pas le budget fournisseur TMDB (`TmdbProviderRateLimiter`, §9.2 —
     * responsabilité séparée et volontairement indépendante).
     */
    private function throttleMatchRequest(string $accountId, string $ipKey): void
    {
        $this->pdo->beginTransaction();
        try {
            AdvisoryLock::account($this->pdo, $accountId);
            AdvisoryLock::verifyIp($this->pdo, $ipKey);
            if ($this->matchThrottle->countForAccount($accountId, 60) + 1 > self::MATCH_REQUESTS_PER_MINUTE_ACCOUNT
                || $this->matchThrottle->countForIp($ipKey, 60) + 1 > self::MATCH_REQUESTS_PER_MINUTE_IP) {
                throw new ApiException(429, 'CATALOG_MATCH_RATE_LIMITED', 'Too many catalog matching requests. Try again later.');
            }
            $this->matchThrottle->record($accountId, $ipKey, 1);
            $this->pdo->commit();
        } catch (Throwable $error) { if ($this->pdo->inTransaction()) $this->pdo->rollBack(); throw $error; }
    }

    /** @param array<string, mixed> $item @return array<string, mixed> */
    private function withDeviceImages(array $item, DeviceType $device): array
    {
        $item['posterUrl'] = $this->images->resolve($item['posterPath'] ?? null, ImageContext::PosterMedia, $device);
        $item['backdropUrl'] = $this->images->resolve($item['backdropPath'] ?? null, ImageContext::Backdrop, $device);
        unset($item['posterPath'], $item['backdropPath']);
        return $item;
    }

    /**
     * F45-R7 : Trending/Popular reçoivent désormais une identité CSTV UUID avant de traverser
     * Android. `id` est conservé pour les anciennes APK mais le nouveau client n'a plus à connaître
     * le format `movie:<tmdbId>`/`series:<tmdbId>`.
     * @param list<array<string, mixed>> $items @return list<array<string, mixed>>
     */
    private function withExternalBrowseIds(array $items): array
    {
        return array_map(function (array $item): array {
            $legacy = $item['id'] ?? null;
            if (!is_string($legacy) || preg_match('/^(movie|series):(\d+)$/D', $legacy, $match) !== 1) return $item;
            $item['externalId'] = $this->externalMedia->findOrCreateForTmdb($match[1], (int) $match[2]);
            return $item;
        }, $items);
    }
}
