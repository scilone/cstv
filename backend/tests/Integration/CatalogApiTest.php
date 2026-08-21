<?php

declare(strict_types=1);

namespace Cstv\Backend\Tests\Integration;

use Cstv\Backend\Bootstrap;
use Cstv\Backend\Catalog\CatalogMatchCandidate;
use Cstv\Backend\Catalog\MediaMetadataProvider;

final class CatalogApiTest extends IntegrationTestCase
{
    protected function setUp(): void
    {
        parent::setUp();
        $this->app = Bootstrap::createApp($this->config, $this->pdo, new class implements MediaMetadataProvider {
            public function trending(string $locale): array { return [['id' => 'movie:42', 'kind' => 'movie', 'title' => 'Dune', 'originalTitle' => 'Dune', 'releaseYear' => 2021, 'overview' => null, 'rating' => 8.0, 'posterUrl' => 'https://images.example/dune.jpg', 'backdropUrl' => null, 'ageRating' => null, 'ageRatingFr' => 12]]; }
            public function popular(string $kind, int $page, string $locale): array { return $this->trending($locale); }
            public function videos(string $canonicalId, string $locale): array { return [['site' => 'YouTube', 'key' => 'dQw4w9WgXcQ', 'type' => 'Trailer', 'official' => true]]; }

            /** Un seul candidat, exactement le titre demandé : `CatalogMatchEngine` l'accepte sans ambiguïté à scorer. */
            public function searchCandidates(string $kind, string $title, ?int $year, string $locale): array
            {
                if ($title === 'Missing') return [];
                // L'id fournisseur encode l'année pour que `hydrate()` puisse la restituer dans `releaseDate`
                // sans dépendre d'un état partagé entre les deux méthodes (double de test, pas de vraie recherche).
                return [new CatalogMatchCandidate($year ?? 999999, $title, $title, $year, [])];
            }

            public function candidateDetail(string $kind, int $tmdbId, string $locale): array { return []; }
            public function genreNames(string $kind, string $locale): array { return []; }

            public function hydrate(string $kind, int $tmdbId, string $locale): array
            {
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
        });
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

    public function testCatalogMatchBatchUsesOneHttpRequestAndCountsEveryMediaAgainstTheQuota(): void
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
        self::assertSame(2, (int) $this->pdo->query("SELECT COUNT(*) FROM catalog_match_attempts WHERE account_id = '" . $account['id'] . "'")->fetchColumn());
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
