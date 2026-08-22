<?php

declare(strict_types=1);

namespace Cstv\Backend\Tests\Integration;

use Cstv\Backend\Bootstrap;
use Cstv\Backend\Catalog\CatalogMatchCandidate;
use Cstv\Backend\Catalog\CatalogProviderException;
use Cstv\Backend\Catalog\MediaMetadataProvider;
use Cstv\Backend\Tests\Support\FakeMonotonicClock;

final class CatalogApiTest extends IntegrationTestCase
{
    /** T29 débit §4 : horloge du budget de batch, figée par défaut (aucune deadline hors tests dédiés). */
    private FakeMonotonicClock $clock;

    /**
     * T28 review R4 : instance conservée pour compter les appels provider depuis les tests de cache
     * (`object`, pas `MediaMetadataProvider` : `searchCalls`/`hydrateCalls` sont des compteurs de
     * double de test, hors interface).
     */
    private object $provider;

    protected function setUp(): void
    {
        parent::setUp();
        // T28 : `TestDatabase::reset()` ne vide pas les tables catalogue — nécessaire ici car le
        // backend-first (§8.2) et la réutilisation par tmdbId (§8.3) lisent désormais `tmdb_movies`
        // par titre/tmdbId ; ce double fournisseur hydrate systématiquement sous le titre "Dune" avec
        // un tmdbId dérivé de l'année, donc deux tests utilisant la même année collisionnaient sans
        // cet isolement.
        $this->pdo->exec('TRUNCATE TABLE external_media, tmdb_media, tmdb_movies, tmdb_series RESTART IDENTITY CASCADE');
        $this->provider = new class implements MediaMetadataProvider {
            public int $searchCalls = 0;
            public int $hydrateCalls = 0;

            public function trending(string $locale): array { return [['id' => 'movie:42', 'kind' => 'movie', 'title' => 'Dune', 'originalTitle' => 'Dune', 'releaseYear' => 2021, 'overview' => null, 'rating' => 8.0, 'posterUrl' => 'https://images.example/dune.jpg', 'backdropUrl' => null, 'ageRating' => null, 'ageRatingFr' => 12]]; }
            public function popular(string $kind, int $page, string $locale): array { return $this->trending($locale); }
            public function videos(string $canonicalId, string $locale): array { return [['site' => 'YouTube', 'key' => 'dQw4w9WgXcQ', 'type' => 'Trailer', 'official' => true]]; }

            /** Un seul candidat, exactement le titre demandé : `CatalogMatchEngine` l'accepte sans ambiguïté à scorer. */
            public function searchCandidates(string $kind, string $title, ?int $year, string $locale): array
            {
                $this->searchCalls++;
                if ($title === 'Missing') return [];
                // T29 §8.8 : simule un budget TMDB local épuisé / une indisponibilité transitoire —
                // le seul moyen, avec ce double de test, de déclencher le chemin `retry` par item.
                if ($title === 'Retry Me') throw new CatalogProviderException(429, retryAfterSeconds: 3);
                if ($title === 'Provider Down') throw new CatalogProviderException(502);
                // L'id fournisseur encode l'année pour que `hydrate()` puisse la restituer dans `releaseDate`
                // sans dépendre d'un état partagé entre les deux méthodes (double de test, pas de vraie recherche).
                return [new CatalogMatchCandidate($year ?? 999999, $title, $title, $year, [])];
            }

            public function candidateDetail(string $kind, int $tmdbId, string $locale): array { return []; }
            public function genreNames(string $kind, string $locale): array { return []; }

            public function hydrate(string $kind, int $tmdbId, string $locale): array
            {
                $this->hydrateCalls++;
                $year = $tmdbId === 999999 ? null : $tmdbId;
                $shared = [
                    'originalLanguage' => 'en', 'overview' => null, 'posterPath' => '/dune.jpg', 'backdropPath' => '/dune-backdrop.jpg',
                    'runtimeMinutes' => 155, 'ageRating' => 13, 'adult' => false, 'status' => 'Released', 'tagline' => null,
                    'voteAverage' => 8.0, 'voteCount' => 100, 'genres' => [], 'originCountries' => [], 'keywords' => [],
                    'alternativeTitles' => [], 'recommendations' => [], 'videos' => [],
                ];
                if ($kind === 'movie') {
                    return ['title' => 'Dune', 'originalTitle' => 'Dune', 'releaseDate' => $year !== null ? $year . '-01-01' : null, ...$shared];
                }
                return [
                    'name' => 'Dune', 'originalName' => 'Dune', 'firstAirDate' => $year !== null ? $year . '-01-01' : null,
                    'lastAirDate' => null, 'numberOfEpisodes' => null, 'numberOfSeasons' => null, 'inProduction' => false,
                    'nextEpisodeToAir' => null, 'episodeRunTimes' => [], ...$shared,
                ];
            }

            public function seasonDetail(int $seriesTmdbId, int $seasonNumber, string $locale): array
            {
                return [
                    'seasonNumber' => $seasonNumber, 'name' => 'Season ' . $seasonNumber, 'overview' => null,
                    'posterPath' => '/season.jpg', 'airDate' => '2020-01-01', 'voteAverage' => 7.5,
                    'episodes' => [[
                        'episodeNumber' => 1, 'name' => 'Pilot', 'overview' => null, 'stillPath' => '/still.jpg',
                        'airDate' => '2020-01-01', 'runtimeMinutes' => 42, 'voteAverage' => 7.0, 'voteCount' => 10,
                    ]],
                ];
            }
        };
        $this->clock = new FakeMonotonicClock();
        $this->app = Bootstrap::createApp($this->config, $this->pdo, $this->provider, $this->clock);
    }

    /**
     * Insère `$count` tentatives de matching datées de `$ageSeconds` dans le passé — la fenêtre du
     * throttle étant glissante, c'est cet âge qui détermine le `Retry-After` attendu.
     */
    private function seedMatchAttempts(string $accountId, int $count, int $ageSeconds, string $ip = '127.0.0.1'): void
    {
        $statement = $this->pdo->prepare(
            'INSERT INTO catalog_match_attempts (id, account_id, ip_key, created_at)'
            . " VALUES (:id, :account, :ip, NOW() - (:age || ' seconds')::interval)",
        );
        for ($index = 0; $index < $count; $index++) {
            $statement->execute(['id' => \Cstv\Backend\Shared\Uuid::v4(), 'account' => $accountId, 'ip' => $ip, 'age' => (string) $ageSeconds]);
        }
    }

    public function testCatalogRoutesAreAuthenticatedAndExposeProductContract(): void
    {
        $account = $this->createAccount();
        self::assertSame(401, $this->request('GET', '/v1/catalog/trending')->getStatusCode());
        $trending = $this->request('GET', '/v1/catalog/trending?locale=fr-FR', '', $this->auth($account['token']));
        self::assertSame(200, $trending->getStatusCode()); self::assertSame('movie:42', $this->json($trending)['items'][0]['id']);
        self::assertMatchesRegularExpression('/^[0-9a-f-]{36}$/', $this->json($trending)['items'][0]['externalId']);
        $popular = $this->request('GET', '/v1/catalog/popular?kind=movie&page=1', '', $this->auth($account['token'])); self::assertSame(200, $popular->getStatusCode());
        $match = $this->jsonRequest('POST', '/v1/catalog/matches', ['kind' => 'movie', 'title' => 'Dune', 'year' => 2021], $this->auth($account['token'])); self::assertSame('matched', $this->json($match)['status']); self::assertMatchesRegularExpression('/^[0-9a-f-]{36}$/', $this->json($match)['item']['externalId']);
        $videos = $this->request('GET', '/v1/catalog/items/movie%3A42/videos', '', $this->auth($account['token'])); self::assertSame('YouTube', $this->json($videos)['items'][0]['site']);
    }

    public function testCatalogRejectsInvalidInputAndDistinguishesNotFound(): void
    {
        $account = $this->createAccount();
        self::assertSame(422, $this->request('GET', '/v1/catalog/popular?kind=live', '', $this->auth($account['token']))->getStatusCode());
        $missing = $this->jsonRequest('POST', '/v1/catalog/matches', ['kind' => 'movie', 'title' => 'Missing'], $this->auth($account['token']));
        self::assertSame('not_found', $this->json($missing)['status']); self::assertNull($this->json($missing)['item']);
        self::assertSame(422, $this->request('GET', '/v1/catalog/trending?locale=de-DE', '', $this->auth($account['token']))->getStatusCode());
    }

    public function testMatchResponseCarriesHydrationWindowAndDeviceAwareImageSizes(): void
    {
        $account = $this->createAccount();
        $mobile = $this->jsonRequest('POST', '/v1/catalog/matches', ['kind' => 'movie', 'title' => 'Device Aware Movie', 'year' => 2015], $this->auth($account['token']));
        $mobileItem = $this->json($mobile)['item'];
        self::assertNotNull($this->json($mobile)['cache']['updatedAt']);
        self::assertNotNull($this->json($mobile)['cache']['refreshAfter']);
        self::assertSame(0, $this->json($mobile)['match']['confidence']); // premier résultat TMDB, sans score local
        self::assertStringContainsString('/w1280/', $mobileItem['backdropUrl']);

        $tv = $this->jsonRequest('POST', '/v1/catalog/matches', ['kind' => 'movie', 'title' => 'Device Aware Movie', 'year' => 2015], [...$this->auth($account['token']), 'X-CSTV-Device-Type' => 'tv']);
        self::assertStringContainsString('/original/', $this->json($tv)['item']['backdropUrl']);
    }

    public function testItemRecommendationsAndSeasonEndpointsExposeConsolidatedData(): void
    {
        $account = $this->createAccount();
        $match = $this->jsonRequest('POST', '/v1/catalog/matches', ['kind' => 'series', 'title' => 'Season Show', 'year' => 2020], $this->auth($account['token']));
        self::assertSame('matched', $this->json($match)['status']);
        $externalId = $this->json($match)['item']['externalId'];

        $item = $this->request('GET', '/v1/catalog/items/' . $externalId, '', $this->auth($account['token']));
        self::assertSame(200, $item->getStatusCode());
        self::assertSame($externalId, $this->json($item)['externalId']);
        self::assertSame('series', $this->json($item)['kind']);

        $recommendations = $this->request('GET', '/v1/catalog/items/' . $externalId . '/recommendations', '', $this->auth($account['token']));
        self::assertSame(200, $recommendations->getStatusCode());
        self::assertSame([], $this->json($recommendations)['items']);

        $season = $this->request('GET', '/v1/catalog/items/' . $externalId . '/seasons/1', '', $this->auth($account['token']));
        self::assertSame(200, $season->getStatusCode());
        self::assertSame(1, $this->json($season)['episodes'][0]['episodeNumber']);
        self::assertSame('Pilot', $this->json($season)['episodes'][0]['name']);
        self::assertSame(42, $this->json($season)['episodes'][0]['runtimeMinutes']);

        self::assertSame(404, $this->request('GET', '/v1/catalog/items/00000000-0000-4000-8000-000000000000', '', $this->auth($account['token']))->getStatusCode());
        self::assertSame(422, $this->request('GET', '/v1/catalog/items/not-a-uuid', '', $this->auth($account['token']))->getStatusCode());
    }

    /**
     * F45-R11 : l'ancien `preg_match('/^[0-9a-f-]{36}$/i')` acceptait n'importe quelle chaîne de 36
     * caractères hex/tirets — 36 tirets, ou un hex mal groupé — jusqu'à ce que PostgreSQL la
     * rejette avec un 500 au lieu du 422 contractuel. `Uuid::isValid()` (groupement 8-4-4-4-12 +
     * nibbles version/variant) doit désormais rejeter ces formes en amont, sur toutes les routes
     * catalogue prenant un `externalId`.
     */
    public function testMalformed36CharacterStringsAreRejectedAs422NotAServerError(): void
    {
        $account = $this->createAccount();
        $malformed = [
            str_repeat('-', 36), // 36 tirets : passait l'ancienne regex `[0-9a-f-]{36}`, aucune structure
            str_repeat('0', 35) . 'a', // 36 hex sans tirets : passait l'ancienne regex, groupement absent
            '00000000-0000-1000-8000-000000000000', // groupement 8-4-4-4-12 correct mais version 1, pas 4
        ];
        foreach ($malformed as $id) {
            self::assertSame(422, $this->request('GET', '/v1/catalog/items/' . $id, '', $this->auth($account['token']))->getStatusCode(), "items/$id should be 422");
            self::assertSame(422, $this->request('GET', '/v1/catalog/items/' . $id . '/recommendations', '', $this->auth($account['token']))->getStatusCode(), "recommendations/$id should be 422");
            self::assertSame(422, $this->request('GET', '/v1/catalog/items/' . $id . '/seasons/1', '', $this->auth($account['token']))->getStatusCode(), "seasons/$id should be 422");
            self::assertSame(422, $this->request('GET', '/v1/catalog/items/' . $id . '/videos', '', $this->auth($account['token']))->getStatusCode(), "videos/$id should be 422");
        }
    }

    public function testCatalogMatchIsRateLimitedPerAccount(): void
    {
        $account = $this->createAccount();
        for ($i = 0; $i < 120; $i++) {
            $this->pdo->prepare('INSERT INTO catalog_match_attempts (id, account_id, ip_key) VALUES (:id, :account, :ip)')
                ->execute(['id' => \Cstv\Backend\Shared\Uuid::v4(), 'account' => $account['id'], 'ip' => '127.0.0.1']);
        }
        $response = $this->jsonRequest('POST', '/v1/catalog/matches', ['kind' => 'movie', 'title' => 'Dune'], $this->auth($account['token']));
        self::assertSame(429, $response->getStatusCode());
        self::assertSame('CATALOG_MATCH_RATE_LIMITED', $this->json($response)['error']['code']);
    }

    /**
     * T29 §8.3/§11 : le nouveau seuil (30 requêtes/min/compte, hypothèse §4.2) s'applique
     * identiquement à une requête batch — un lot de 50 items ne consomme toujours qu'une seule unité
     * de quota, donc une seule requête supplémentaire suffit à faire dépasser un compte déjà à 30.
     */
    public function testCatalogMatchBatchIsRateLimitedPerAccountAtTheRequestLevel(): void
    {
        $account = $this->createAccount();
        for ($i = 0; $i < 30; $i++) {
            $this->pdo->prepare('INSERT INTO catalog_match_attempts (id, account_id, ip_key) VALUES (:id, :account, :ip)')
                ->execute(['id' => \Cstv\Backend\Shared\Uuid::v4(), 'account' => $account['id'], 'ip' => '127.0.0.1']);
        }
        $response = $this->jsonRequest('POST', '/v1/catalog/matches/batch', [
            'items' => [['kind' => 'movie', 'title' => 'Over Quota', 'year' => 2021]],
        ], $this->auth($account['token']));
        self::assertSame(429, $response->getStatusCode());
        self::assertSame('CATALOG_MATCH_RATE_LIMITED', $this->json($response)['error']['code']);
    }

    /**
     * Review T29/R3 : verrouille le seuil exact (30) sur l'endpoint unitaire — le test historique
     * (120 tentatives) prouve seulement qu'une valeur très supérieure est rejetée, pas que la
     * frontière est bien à 30 et pas ailleurs.
     */
    public function testCatalogMatchExactAccountThresholdOfThirtyPerMinute(): void
    {
        $account = $this->createAccount();
        for ($i = 0; $i < 29; $i++) {
            $this->pdo->prepare('INSERT INTO catalog_match_attempts (id, account_id, ip_key) VALUES (:id, :account, :ip)')
                ->execute(['id' => \Cstv\Backend\Shared\Uuid::v4(), 'account' => $account['id'], 'ip' => '127.0.0.1']);
        }
        // 29 déjà consommées + cette requête = 30 : encore autorisé (limite non dépassée).
        $allowed = $this->jsonRequest('POST', '/v1/catalog/matches', ['kind' => 'movie', 'title' => 'Threshold Ok', 'year' => 2021], $this->auth($account['token']));
        self::assertSame(200, $allowed->getStatusCode());

        // 30 consommées + cette requête = 31 : dépasse la limite.
        $blocked = $this->jsonRequest('POST', '/v1/catalog/matches', ['kind' => 'movie', 'title' => 'Threshold Over', 'year' => 2021], $this->auth($account['token']));
        self::assertSame(429, $blocked->getStatusCode());
        self::assertSame('CATALOG_MATCH_RATE_LIMITED', $this->json($blocked)['error']['code']);
    }

    /**
     * Review T29/R3 : le seuil IP (60/min, indépendant du compte) n'était pas verrouillé par un test
     * dédié — flood réalisé depuis plusieurs comptes distincts pour isoler la frontière IP de la
     * frontière compte (chaque compte reste sous 30, seule l'IP partagée atteint 60).
     */
    public function testCatalogMatchIsRateLimitedPerIpAtSixtyPerMinuteIndependentlyOfAccount(): void
    {
        $flooder = $this->createAccount('ip-flood@example.com');
        for ($i = 0; $i < 60; $i++) {
            $this->pdo->prepare('INSERT INTO catalog_match_attempts (id, account_id, ip_key) VALUES (:id, :account, :ip)')
                ->execute(['id' => \Cstv\Backend\Shared\Uuid::v4(), 'account' => $flooder['id'], 'ip' => '127.0.0.1']);
        }

        $victim = $this->createAccount('ip-victim@example.com');
        $response = $this->jsonRequest('POST', '/v1/catalog/matches', ['kind' => 'movie', 'title' => 'Dune'], $this->auth($victim['token']));

        self::assertSame(429, $response->getStatusCode());
        self::assertSame('CATALOG_MATCH_RATE_LIMITED', $this->json($response)['error']['code']);
    }

    /**
     * T29 débit §1 : un 429 de throttle CSTV doit porter un `Retry-After` **calculé** sur la fenêtre
     * glissante, pas une constante. Sans lui, le worker Android retombait sur son backoff exponentiel
     * (10 → 320 min) pour un simple problème de cadence, et le débit réel tombait à ~25 médias/min.
     * 30 tentatives vieilles de 10 s : la plus ancienne sort de la fenêtre de 60 s dans 50 s.
     */
    public function testAccountThrottleExposesARetryAfterComputedFromTheSlidingWindow(): void
    {
        $account = $this->createAccount();
        $this->seedMatchAttempts($account['id'], 30, 10);

        $response = $this->jsonRequest('POST', '/v1/catalog/matches', ['kind' => 'movie', 'title' => 'Quota Account', 'year' => 2021], $this->auth($account['token']));

        self::assertSame(429, $response->getStatusCode());
        self::assertSame('CATALOG_MATCH_RATE_LIMITED', $this->json($response)['error']['code']);
        $retryAfter = (int) $response->getHeaderLine('Retry-After');
        self::assertGreaterThanOrEqual(48, $retryAfter);
        self::assertLessThanOrEqual(51, $retryAfter);
    }

    /** T29 débit §1 : même exigence sur le quota IP (60/min), indépendant du compte. */
    public function testIpThrottleExposesARetryAfterComputedFromTheSlidingWindow(): void
    {
        $flooder = $this->createAccount('retry-after-ip-flood@example.com');
        $this->seedMatchAttempts($flooder['id'], 60, 20);

        $victim = $this->createAccount('retry-after-ip-victim@example.com');
        $response = $this->jsonRequest('POST', '/v1/catalog/matches', ['kind' => 'movie', 'title' => 'Quota Ip', 'year' => 2021], $this->auth($victim['token']));

        self::assertSame(429, $response->getStatusCode());
        $retryAfter = (int) $response->getHeaderLine('Retry-After');
        self::assertGreaterThanOrEqual(38, $retryAfter);
        self::assertLessThanOrEqual(41, $retryAfter);
    }

    /**
     * T29 débit §1 : boundary exacte. 29 tentatives + la requête courante = 30, autorisé et sans
     * `Retry-After` ; la suivante (30 déjà consommées) est refusée avec un délai borné par la fenêtre.
     * Verrouille aussi que les quotas restent 30/60 après ce correctif.
     */
    public function testThrottleBoundaryAtThirtyStillPassesAndTheNextRequestCarriesARetryAfter(): void
    {
        $account = $this->createAccount();
        $this->seedMatchAttempts($account['id'], 29, 5);

        $allowed = $this->jsonRequest('POST', '/v1/catalog/matches', ['kind' => 'movie', 'title' => 'Boundary Ok', 'year' => 2021], $this->auth($account['token']));
        self::assertSame(200, $allowed->getStatusCode());
        self::assertSame('', $allowed->getHeaderLine('Retry-After'));

        $blocked = $this->jsonRequest('POST', '/v1/catalog/matches', ['kind' => 'movie', 'title' => 'Boundary Over', 'year' => 2021], $this->auth($account['token']));
        self::assertSame(429, $blocked->getStatusCode());
        $retryAfter = (int) $blocked->getHeaderLine('Retry-After');
        self::assertGreaterThanOrEqual(1, $retryAfter);
        self::assertLessThanOrEqual(60, $retryAfter);
    }

    /**
     * T29 débit §1 : une fois la fenêtre logiquement expirée, la requête repasse — le quota est bien
     * glissant et le `Retry-After` annoncé mène réellement à une réouverture.
     */
    public function testRequestIsAcceptedAgainOnceTheThrottleWindowHasLogicallyExpired(): void
    {
        $account = $this->createAccount();
        $this->seedMatchAttempts($account['id'], 30, 61);

        $response = $this->jsonRequest('POST', '/v1/catalog/matches', ['kind' => 'movie', 'title' => 'Window Expired', 'year' => 2021], $this->auth($account['token']));

        self::assertSame(200, $response->getStatusCode());
    }

    /**
     * T29 débit §4 (A) : un batch entièrement servi par le cache ne doit jamais être tronqué par la
     * deadline, même avec une horloge qui dépasse le budget dès la première lecture — ces items ne
     * démarrent aucune résolution fournisseur.
     */
    public function testBatchOfCacheHitsIsNeverTruncatedByTheTimeBudget(): void
    {
        $account = $this->createAccount();
        $body = ['items' => array_map(
            static fn (int $index): array => ['kind' => 'movie', 'title' => 'Warm Cache ' . $index, 'year' => 2021],
            range(1, 50),
        )];
        $headers = [...$this->auth($account['token']), 'X-CSTV-Catalog-Capabilities' => 'retry'];

        // Préchauffage avec une horloge figée : aucune deadline, les 50 entrées entrent en cache.
        $warm = $this->jsonRequest('POST', '/v1/catalog/matches/batch', $body, $headers);
        self::assertSame(200, $warm->getStatusCode());
        self::assertSame(['matched'], array_values(array_unique(array_column($this->json($warm)['items'], 'status'))));

        // Budget dépassé dès la première lecture d'horloge : les hits cache doivent tout de même sortir.
        $this->clock->stepNanos = 10_000_000_000;
        $cached = $this->jsonRequest('POST', '/v1/catalog/matches/batch', $body, $headers);

        self::assertSame(200, $cached->getStatusCode());
        $items = $this->json($cached)['items'];
        self::assertCount(50, $items);
        self::assertSame(['matched'], array_values(array_unique(array_column($items, 'status'))));
    }

    /**
     * T29 débit §4 (B/C/D/E) : au-delà du budget, les items non encore traités deviennent `retry` à
     * leur position exacte, la taille de la réponse est conservée, et les premiers items restent
     * résolus normalement. Horloge à 1 s par lecture : la deadline de 7 s tombe en cours de lot.
     */
    public function testBatchTimeBudgetTurnsRemainingItemsIntoOrderedRetriesWithoutChangingResponseSize(): void
    {
        $account = $this->createAccount();
        $this->clock->stepNanos = 1_000_000_000;
        $titles = array_map(static fn (int $index): string => 'Budget Cold ' . $index, range(1, 12));

        $response = $this->jsonRequest('POST', '/v1/catalog/matches/batch', [
            'items' => array_map(static fn (string $title): array => ['kind' => 'movie', 'title' => $title, 'year' => 2021], $titles),
        ], [...$this->auth($account['token']), 'X-CSTV-Catalog-Capabilities' => 'retry']);

        self::assertSame(200, $response->getStatusCode());
        $items = $this->json($response)['items'];
        self::assertCount(12, $items, 'la taille de la réponse doit rester celle de la requête');

        $statuses = array_column($items, 'status');
        self::assertContains('matched', $statuses, 'les premiers items doivent être résolus normalement');
        self::assertContains('retry', $statuses, 'la deadline doit sacrifier les derniers items');
        // Ordre strictement conservé : une fois le premier `retry` rencontré, plus aucun `matched`.
        $firstRetry = array_search('retry', $statuses, true);
        self::assertSame(array_fill(0, 12 - $firstRetry, 'retry'), array_slice($statuses, $firstRetry));
        foreach (array_slice($items, $firstRetry) as $item) {
            self::assertNull($item['item']);
            self::assertNull($item['match']);
            self::assertSame(30, $item['cache']['retryAfter']);
            // T29 cycle backfill P0-1 : la cause doit être explicite — le client ne doit jamais avoir
            // à la déduire du délai, sous peine de compter une deadline comme un échec du média.
            self::assertSame('batch_deadline', $item['cache']['retryReason']);
        }
    }

    /**
     * T29 débit §4 (G) : un item sacrifié par la deadline n'est jamais persisté comme `unresolved`.
     * Preuve par le rejeu : les mêmes titres se résolvent en `matched` au passage suivant, donc rien
     * de définitif n'a été écrit pour eux.
     */
    public function testItemsDroppedByTheDeadlineAreNeverPersistedAsUnresolved(): void
    {
        $this->pdo->exec('TRUNCATE TABLE media_metadata_cache');
        $account = $this->createAccount();
        $this->clock->stepNanos = 1_000_000_000;
        $body = ['items' => array_map(
            static fn (int $index): array => ['kind' => 'movie', 'title' => 'Deadline Replay ' . $index, 'year' => 2021],
            range(1, 12),
        )];
        $headers = [...$this->auth($account['token']), 'X-CSTV-Catalog-Capabilities' => 'retry'];

        $first = $this->json($this->jsonRequest('POST', '/v1/catalog/matches/batch', $body, $headers))['items'];
        self::assertContains('retry', array_column($first, 'status'));

        $this->clock->stepNanos = 0;
        $second = $this->json($this->jsonRequest('POST', '/v1/catalog/matches/batch', $body, $headers))['items'];

        self::assertSame(['matched'], array_values(array_unique(array_column($second, 'status'))));
        self::assertSame(
            0,
            (int) $this->pdo->query("SELECT COUNT(*) FROM media_metadata_cache WHERE result_status IN ('unresolved', 'retry')")->fetchColumn(),
            'une deadline ne doit jamais laisser de trace définitive en cache',
        );
    }

    /**
     * T29 débit §4 (F) : un client legacy (pas de `X-CSTV-Catalog-Capabilities: retry`) ne peut pas
     * recevoir de statut `retry` par item — il le lirait comme un `unresolved` définitif (§7.3). La
     * requête échoue donc entièrement en 429 avec un `Retry-After`, un contrat que son worker connaît
     * déjà, et l'unique process FCGI est libéré immédiatement.
     */
    public function testLegacyClientGetsAWholeRequest429WithRetryAfterWhenTheBudgetExpires(): void
    {
        $account = $this->createAccount();
        $this->clock->stepNanos = 1_000_000_000;

        $response = $this->jsonRequest('POST', '/v1/catalog/matches/batch', [
            'items' => array_map(
                static fn (int $index): array => ['kind' => 'movie', 'title' => 'Legacy Budget ' . $index, 'year' => 2021],
                range(1, 12),
            ),
        ], $this->auth($account['token']));

        self::assertSame(429, $response->getStatusCode());
        self::assertSame('CATALOG_MATCH_BUDGET_EXCEEDED', $this->json($response)['error']['code']);
        self::assertSame('30', $response->getHeaderLine('Retry-After'));
    }

    /** Review T29/R3 : le plafond de payload (§7.4/§8.16) reste 50 — verrouille les deux bornes. */
    public function testCatalogMatchBatchAcceptsFiftyItemsAndRejectsFiftyOne(): void
    {
        $account = $this->createAccount();
        $item = ['kind' => 'movie', 'title' => 'Bulk Size Item', 'year' => 2021];

        $fifty = $this->jsonRequest('POST', '/v1/catalog/matches/batch', ['items' => array_fill(0, 50, $item)], $this->auth($account['token']));
        self::assertSame(200, $fifty->getStatusCode());
        self::assertCount(50, $this->json($fifty)['items']);

        $account2 = $this->createAccount('bulk-fifty-one@example.com');
        $fiftyOne = $this->jsonRequest('POST', '/v1/catalog/matches/batch', ['items' => array_fill(0, 51, $item)], $this->auth($account2['token']));
        self::assertSame(422, $fiftyOne->getStatusCode());
        self::assertSame('INVALID_CATALOG_BATCH', $this->json($fiftyOne)['error']['code']);
    }

    /**
     * T29 §8.3 : le throttle CSTV devient un quota de *requêtes*, plus un quota par média contenu
     * dans le batch — un lot de N items ne coûte plus qu'une seule unité de quota, quel que soit N.
     * Avant T29, cette même requête aurait inséré une ligne par item (`count($items)` passé à
     * `throttleMatch()`) ; désormais une seule ligne est insérée par requête HTTP.
     */
    public function testCatalogMatchBatchUsesOneHttpRequestAndCountsOnlyOneRequestAgainstTheQuota(): void
    {
        $account = $this->createAccount();
        $response = $this->jsonRequest('POST', '/v1/catalog/matches/batch', [
            'items' => [
                ['kind' => 'movie', 'title' => 'Batch One', 'year' => 2021],
                ['kind' => 'series', 'title' => 'Batch Two', 'year' => 2022],
            ],
        ], $this->auth($account['token']));

        self::assertSame(200, $response->getStatusCode());
        self::assertCount(2, $this->json($response)['items']);
        self::assertSame('matched', $this->json($response)['items'][0]['status']);
        self::assertSame(1, (int) $this->pdo->query("SELECT COUNT(*) FROM catalog_match_attempts WHERE account_id = '" . $account['id'] . "'")->fetchColumn(), 'a 2-item batch must count as a single throttle request');
    }

    /**
     * T29 §7.6/§8.8 : un item en échec technique (budget provider épuisé) doit devenir `retry` sans
     * faire perdre les résultats des autres items du même batch, et sans perturber leur ordre.
     */
    /** Review T29/R1 : seul un client ayant annoncé la capacité `retry` peut recevoir ce statut. */
    public function testCatalogMatchBatchIsolatesProviderErrorsPerItemAndPreservesOrder(): void
    {
        $account = $this->createAccount();
        $response = $this->jsonRequest('POST', '/v1/catalog/matches/batch', [
            'items' => [
                ['kind' => 'movie', 'title' => 'Batch Isolation Matched', 'year' => 2021],
                ['kind' => 'movie', 'title' => 'Retry Me', 'year' => 2021],
                ['kind' => 'movie', 'title' => 'Missing', 'year' => 2021],
            ],
        ], [...$this->auth($account['token']), 'X-CSTV-Catalog-Capabilities' => 'retry']);

        self::assertSame(200, $response->getStatusCode());
        $items = $this->json($response)['items'];
        self::assertCount(3, $items);
        self::assertSame('matched', $items[0]['status']);
        self::assertSame('retry', $items[1]['status']);
        self::assertNull($items[1]['item']);
        self::assertNull($items[1]['match']);
        self::assertSame(3, $items[1]['cache']['retryAfter']);
        // T29 cycle backfill P0-1 : erreur fournisseur = vraie tentative du média, contrairement à
        // `batch_deadline`. Le client doit pouvoir les distinguer sans heuristique sur le délai.
        self::assertSame('provider', $items[1]['cache']['retryReason']);
        self::assertSame('not_found', $items[2]['status']);
    }

    /**
     * Review T29/R1 : une APK pré-T29 n'envoie jamais `X-CSTV-Catalog-Capabilities` — son contrat ne
     * distingue pas `retry` d'un `unresolved` réel (§7.3). Le backend doit donc laisser l'erreur
     * provider faire échouer la requête entière, exactement le comportement pré-T29 que son worker
     * sait déjà reprogrammer intégralement, plutôt que de renvoyer un statut qu'elle interpréterait
     * à tort comme un résultat métier terminé.
     */
    public function testCatalogMatchBatchWithoutCapabilityHeaderFailsTheWholeRequestOnProviderError(): void
    {
        $account = $this->createAccount();
        $response = $this->jsonRequest('POST', '/v1/catalog/matches/batch', [
            'items' => [
                ['kind' => 'movie', 'title' => 'Batch Isolation Matched', 'year' => 2021],
                ['kind' => 'movie', 'title' => 'Retry Me', 'year' => 2021],
            ],
        ], $this->auth($account['token']));

        self::assertSame(429, $response->getStatusCode());
        self::assertSame('CATALOG_PROVIDER_UNAVAILABLE', $this->json($response)['error']['code']);
    }

    /** Review T29/R1 : une valeur de capacité inconnue/vide se comporte comme un client legacy. */
    public function testCatalogMatchBatchWithUnrelatedCapabilityHeaderIsTreatedAsLegacy(): void
    {
        $account = $this->createAccount();
        $response = $this->jsonRequest('POST', '/v1/catalog/matches/batch', [
            'items' => [['kind' => 'movie', 'title' => 'Retry Me', 'year' => 2021]],
        ], [...$this->auth($account['token']), 'X-CSTV-Catalog-Capabilities' => 'some-other-feature']);

        self::assertSame(429, $response->getStatusCode());
    }

    /**
     * Review T29/R1 : le filet de compatibilité legacy (`!$retryAware`) doit couvrir les trois
     * statuts provider retryables (429/502/503), pas seulement le 429 déjà exercé ci-dessus —
     * `CatalogService::matchBatchItem()` les traite via le même `in_array`, ce test verrouille le 502.
     */
    public function testCatalogMatchBatchWithoutCapabilityHeaderFailsWholeRequestOn502(): void
    {
        $account = $this->createAccount();
        $legacy = $this->jsonRequest('POST', '/v1/catalog/matches/batch', [
            'items' => [['kind' => 'movie', 'title' => 'Provider Down', 'year' => 2021]],
        ], $this->auth($account['token']));
        self::assertSame(502, $legacy->getStatusCode());
        self::assertSame('CATALOG_PROVIDER_BAD_RESPONSE', $this->json($legacy)['error']['code']);

        $account2 = $this->createAccount('provider-down-retry-aware@example.com');
        $retryAware = $this->jsonRequest('POST', '/v1/catalog/matches/batch', [
            'items' => [['kind' => 'movie', 'title' => 'Provider Down', 'year' => 2021]],
        ], [...$this->auth($account2['token']), 'X-CSTV-Catalog-Capabilities' => 'retry']);
        self::assertSame(200, $retryAware->getStatusCode());
        self::assertSame('retry', $this->json($retryAware)['items'][0]['status']);
    }

    /** T29 §8.4 : un batch entièrement servi par le cache ne doit jamais rappeler le moteur/le fournisseur. */
    public function testCatalogMatchBatchCacheHitsNeverCallTheEngineOrTheProvider(): void
    {
        $account = $this->createAccount();
        $body = ['items' => [['kind' => 'movie', 'title' => 'Bulk Cache Hit', 'year' => 2021]]];

        $first = $this->jsonRequest('POST', '/v1/catalog/matches/batch', $body, $this->auth($account['token']));
        self::assertSame(200, $first->getStatusCode());
        self::assertSame(1, $this->provider->searchCalls);
        self::assertSame(1, $this->provider->hydrateCalls);

        $second = $this->jsonRequest('POST', '/v1/catalog/matches/batch', $body, $this->auth($account['token']));

        self::assertSame(200, $second->getStatusCode());
        self::assertSame('matched', $this->json($second)['items'][0]['status']);
        self::assertSame(1, $this->provider->searchCalls, 'a fresh bulk cache hit must not call the provider again');
        self::assertSame(1, $this->provider->hydrateCalls, 'a fresh bulk cache hit must not call the provider again');
    }

    public function testLegacyHintsRemainAcceptedAndIgnored(): void
    {
        $account = $this->createAccount();
        $response = $this->jsonRequest('POST', '/v1/catalog/matches', [
            'kind' => 'movie', 'title' => 'Legacy Hints', 'year' => 2021,
            'hints' => ['director' => 'Ignored Director', 'actors' => ['Ignored Actor']],
        ], $this->auth($account['token']));

        self::assertSame(200, $response->getStatusCode());
        self::assertSame('tmdb-first-result', $this->json($response)['match']['method']);
    }

    /**
     * T28 review R4 : la Tâche 8 (§10) demandait un scénario "100 hits cache" au même titre que les
     * scénarios backend-first/tmdbId/média inconnu — non couvert par les notes d'implémentation de
     * l'étape 5. `CatalogService::resolve()` retourne avant tout appel à `$load()` (donc au moteur et
     * au provider) dès que l'entrée de cache est fraîche ; ce test verrouille cette garantie au
     * niveau HTTP plutôt que de la décrire seulement en prose.
     */
    public function testCatalogMatchCacheHitNeverCallsTheEngineOrTheProvider(): void
    {
        $account = $this->createAccount();
        $body = ['kind' => 'movie', 'title' => 'Cache Hit Guard', 'year' => 2021];

        $first = $this->jsonRequest('POST', '/v1/catalog/matches', $body, $this->auth($account['token']));
        self::assertSame(200, $first->getStatusCode());
        self::assertSame(1, $this->provider->searchCalls);
        self::assertSame(1, $this->provider->hydrateCalls);
        $externalId = $this->json($first)['item']['externalId'];

        $second = $this->jsonRequest('POST', '/v1/catalog/matches', $body, $this->auth($account['token']));

        self::assertSame(200, $second->getStatusCode());
        self::assertSame($externalId, $this->json($second)['item']['externalId']);
        self::assertSame(1, $this->provider->searchCalls, 'a fresh media_metadata_cache hit must not call the provider again');
        self::assertSame(1, $this->provider->hydrateCalls, 'a fresh media_metadata_cache hit must not call the provider again');
    }

    public function testCatalogMatchDynamicTtlCachingBasedOnReleaseYear(): void
    {
        $this->pdo->exec('TRUNCATE TABLE media_metadata_cache');

        $account = $this->createAccount();
        $currentYear = (int) date('Y');

        $cases = [
            [$currentYear, 604800],       // Age 0 (< 1 year) -> 7 days
            [$currentYear - 3, 2592000],  // Age 3 (1-5 years) -> 30 days
            [$currentYear - 5, 7776000],  // Age 5 (5-10 years) -> 90 days
            [$currentYear - 15, 15552000], // Age 15 (10+ years) -> 180 days
            [null, 1296000],              // Unknown -> 15 days
        ];

        foreach ($cases as [$year, $expectedTtl]) {
            $response = $this->jsonRequest('POST', '/v1/catalog/matches', [
                'kind' => 'movie',
                'title' => 'Dune' . ($year ?? 'Null'),
                'year' => $year
            ], $this->auth($account['token']));

            self::assertSame(200, $response->getStatusCode());

            $statement = $this->pdo->prepare('SELECT expires_at FROM media_metadata_cache ORDER BY updated_at DESC LIMIT 1');
            $statement->execute();
            $expiresAtStr = $statement->fetchColumn();
            self::assertNotFalse($expiresAtStr);

            $expiresAt = new \DateTimeImmutable($expiresAtStr);
            $now = new \DateTimeImmutable();

            $actualTtl = $expiresAt->getTimestamp() - $now->getTimestamp();

            self::assertGreaterThanOrEqual($expectedTtl - 5, $actualTtl);
            self::assertLessThanOrEqual($expectedTtl + 5, $actualTtl);
        }
    }
}
