<?php

declare(strict_types=1);

namespace Cstv\Backend\Tests\Integration;

use Cstv\Backend\Bootstrap;
use Cstv\Backend\Catalog\MediaMetadataProvider;

final class CatalogApiTest extends IntegrationTestCase
{
    protected function setUp(): void
    {
        parent::setUp();
        $this->app = Bootstrap::createApp($this->config, $this->pdo, new class implements MediaMetadataProvider {
            public function trending(string $locale): array { return [['id' => 'movie:42', 'kind' => 'movie', 'title' => 'Dune', 'originalTitle' => 'Dune', 'releaseYear' => 2021, 'overview' => null, 'rating' => 8.0, 'posterUrl' => 'https://images.example/dune.jpg', 'backdropUrl' => null, 'ageRatingFr' => 12]]; }
            public function popular(string $kind, int $page, string $locale): array { return $this->trending($locale); }
            public function match(string $kind, string $title, ?int $year, string $locale): ?array {
                if ($title === 'Missing') return null;
                $item = $this->trending($locale)[0];
                $item['releaseYear'] = $year;
                return $item;
            }
            public function videos(string $canonicalId, string $locale): array { return [['site' => 'YouTube', 'key' => 'dQw4w9WgXcQ', 'type' => 'Trailer', 'official' => true]]; }
        });
    }

    public function testCatalogRoutesAreAuthenticatedAndExposeProductContract(): void
    {
        $account = $this->createAccount();
        self::assertSame(401, $this->request('GET', '/v1/catalog/trending')->getStatusCode());
        $trending = $this->request('GET', '/v1/catalog/trending?locale=fr-FR', '', $this->auth($account['token']));
        self::assertSame(200, $trending->getStatusCode()); self::assertSame('movie:42', $this->json($trending)['items'][0]['id']);
        $popular = $this->request('GET', '/v1/catalog/popular?kind=movie&page=1', '', $this->auth($account['token'])); self::assertSame(200, $popular->getStatusCode());
        $match = $this->jsonRequest('POST', '/v1/catalog/matches', ['kind' => 'movie', 'title' => 'Dune', 'year' => 2021], $this->auth($account['token'])); self::assertSame('matched', $this->json($match)['status']);
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

    public function testCatalogMatchIsRateLimitedPerAccount(): void
    {
        $account = $this->createAccount();
        for ($i = 0; $i < 30; $i++) {
            $this->pdo->prepare('INSERT INTO catalog_match_attempts (id, account_id, ip_key) VALUES (:id, :account, :ip)')
                ->execute(['id' => \Cstv\Backend\Shared\Uuid::v4(), 'account' => $account['id'], 'ip' => '127.0.0.1']);
        }
        $response = $this->jsonRequest('POST', '/v1/catalog/matches', ['kind' => 'movie', 'title' => 'Dune'], $this->auth($account['token']));
        self::assertSame(429, $response->getStatusCode());
        self::assertSame('CATALOG_MATCH_RATE_LIMITED', $this->json($response)['error']['code']);
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
