<?php

declare(strict_types=1);

namespace Cstv\Backend\Tests\Integration;

use Cstv\Backend\Bootstrap;
use Cstv\Backend\Catalog\TmdbCertificationMapper;
use Cstv\Backend\Catalog\TmdbClient;
use Cstv\Backend\Catalog\TmdbMediaMetadataProvider;
use Cstv\Backend\Catalog\TmdbProviderRateLimiter;
use Cstv\Backend\Shared\ApiException;

/**
 * F45-R1 : contrairement à `CatalogApiTest`, qui injecte un faux `MediaMetadataProvider` et ne
 * traverse donc jamais le câblage réel, ce test construit le VRAI `TmdbMediaMetadataProvider` +
 * `TmdbProviderRateLimiter($this->pdo)` — le même `PDO` que `CatalogService` — exactement comme
 * `Bootstrap::createApp()` le fait en production. Seul `TmdbClient` reçoit un transport factice
 * (pas de vrai réseau), donc `TmdbProviderRateLimiter::acquire()` s'exécute réellement pendant que
 * `CatalogService::resolve()` tient sa propre transaction sur ce même `PDO`. Avant la correction,
 * `acquire()` tentait un second `beginTransaction()` sur une transaction déjà ouverte et levait une
 * `PDOException` avant même l'appel fournisseur — ce test échouait avec un 500 au lieu du 200
 * attendu.
 */
final class CatalogProviderWiringTest extends IntegrationTestCase
{
    protected function setUp(): void
    {
        parent::setUp();
        // La ligne singleton `catalog_provider_rate_limit` n'est pas couverte par `TestDatabase::reset()`
        // (elle vit hors des tables de compte truncatées entre tests) : la remettre à pleine capacité
        // ici rend chaque test de cette classe déterministe, indépendamment de ce qu'un test précédent
        // a consommé ou du temps réel écoulé entre deux exécutions.
        $this->pdo->exec('UPDATE catalog_provider_rate_limit SET tokens = 5, updated_at = NOW() WHERE id = 1');
        $transport = function (string $path, array $query): array {
            if ($path === 'search/movie') {
                return ['results' => [[
                    'id' => 918273, 'title' => 'Real Wiring Movie', 'original_title' => 'Real Wiring Movie',
                    'release_date' => '2015-01-01', 'genre_ids' => [],
                ]]];
            }
            if ($path === 'movie/918273') {
                return [
                    'title' => 'Real Wiring Movie', 'original_title' => 'Real Wiring Movie', 'release_date' => '2015-01-01',
                    'runtime' => 120, 'overview' => null, 'poster_path' => null, 'backdrop_path' => null,
                    'original_language' => 'en', 'adult' => false, 'status' => 'Released', 'tagline' => null,
                    'vote_average' => 7.0, 'vote_count' => 10, 'genres' => [], 'origin_country' => [],
                    'release_dates' => ['results' => []], 'keywords' => ['keywords' => []],
                    'alternative_titles' => ['titles' => []], 'recommendations' => ['results' => []], 'videos' => ['results' => []],
                ];
            }
            throw new \RuntimeException('Unexpected TMDB path in CatalogProviderWiringTest: ' . $path);
        };

        $realProviderRealRateLimiter = new TmdbMediaMetadataProvider(
            new TmdbClient('test-token', $transport),
            new TmdbCertificationMapper(),
            new TmdbProviderRateLimiter($this->pdo),
        );
        $this->app = Bootstrap::createApp($this->config, $this->pdo, $realProviderRealRateLimiter);
    }

    public function testUncachedMatchSucceedsThroughTheRealRateLimiterInsideCatalogServicesTransaction(): void
    {
        $account = $this->createAccount();

        $response = $this->jsonRequest(
            'POST',
            '/v1/catalog/matches',
            ['kind' => 'movie', 'title' => 'Real Wiring Movie', 'year' => 2015],
            $this->auth($account['token']),
        );

        self::assertSame(200, $response->getStatusCode());
        $payload = $this->json($response);
        self::assertSame('matched', $payload['status']);
        self::assertMatchesRegularExpression('/^[0-9a-f-]{36}$/', $payload['item']['externalId']);
    }

    public function testConsecutiveUncachedMatchesShareTheSameGlobalBudgetWithoutCrashing(): void
    {
        $account = $this->createAccount();

        // Deuxième titre distinct pour éviter le cache `match` (§8.6) : chaque appel refait
        // réellement `searchCandidates()` + `hydrate()`, donc deux `acquire()` de plus dans la même
        // requête HTTP, chacune sa propre transaction `CatalogService::resolve()`.
        $first = $this->jsonRequest('POST', '/v1/catalog/matches', ['kind' => 'movie', 'title' => 'Real Wiring Movie', 'year' => 2015], $this->auth($account['token']));
        self::assertSame(200, $first->getStatusCode());

        $second = $this->jsonRequest('POST', '/v1/catalog/matches', ['kind' => 'movie', 'title' => 'Real Wiring Movie', 'year' => 2015, 'hints' => ['director' => 'Someone Else']], $this->auth($account['token']));
        self::assertSame(200, $second->getStatusCode());
        self::assertSame('matched', $this->json($second)['status']);
    }

    /**
     * F45-R8 : `trending()`/`popular()`/`videos()` appelaient `TmdbClient` directement, hors du
     * budget global partagé — cette assertion prouve qu'elles le consomment désormais comme les
     * autres routes, en épuisant la capacité (5, §8.16) avec un mélange des trois puis en vérifiant
     * que l'appel suivant est bien throttlé.
     */
    public function testTrendingPopularAndVideosNowConsumeTheSharedRateLimitBudget(): void
    {
        $transport = static fn (string $path): array => match (true) {
            $path === 'trending/all/week' => ['results' => []],
            $path === 'movie/popular' => ['results' => []],
            $path === 'movie/918273/videos' => ['results' => []],
            default => throw new \RuntimeException('Unexpected TMDB path: ' . $path),
        };
        $provider = new TmdbMediaMetadataProvider(new TmdbClient('test-token', $transport), new TmdbCertificationMapper(), new TmdbProviderRateLimiter($this->pdo));

        $provider->trending('fr-FR');
        $provider->popular('movie', 1, 'fr-FR');
        $provider->videos('movie:918273', 'fr-FR');
        $provider->trending('fr-FR');
        $provider->popular('movie', 1, 'fr-FR');

        $this->expectException(ApiException::class);
        $this->expectExceptionMessage('The catalog provider budget is temporarily exhausted.');
        $provider->videos('movie:918273', 'fr-FR');
    }
}
