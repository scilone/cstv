<?php

declare(strict_types=1);

namespace Cstv\Backend\Catalog;

use Cstv\Backend\Shared\ApiException;
use Cstv\Backend\Database\AdvisoryLock;
use PDO;
use Throwable;

final readonly class CatalogService
{
    public function __construct(private PDO $pdo, private MediaMetadataCacheRepository $cache, private MediaMetadataProvider $provider, private CatalogMatchThrottleRepository $matchThrottle) {}
    /** @return array<string, mixed> */
    public function trending(string $locale): array { return $this->resolve('trending', ['locale' => $locale], 21600, fn () => ['items' => $this->provider->trending($locale)]); }
    /** @return array<string, mixed> */
    public function popular(string $kind, int $page, string $locale): array { return $this->resolve('popular', compact('kind', 'page', 'locale'), 21600, fn () => ['items' => $this->provider->popular($kind, $page, $locale)]); }
    /** @return array<string, mixed> */
    public function match(string $kind, string $title, ?int $year, string $locale, string $accountId, string $ipKey): array
    {
        $this->throttleMatch($accountId, $ipKey);
        return $this->resolve('match', compact('kind', 'title', 'year', 'locale'), 604800, fn () => ['item' => $this->provider->match($kind, $title, $year, $locale)], true);
    }
    /** @return array<string, mixed> */
    public function videos(string $canonicalId, string $locale): array { return $this->resolve('videos', compact('canonicalId', 'locale'), 604800, fn () => ['items' => $this->provider->videos($canonicalId, $locale)]); }
    /** @param array<string, mixed> $args @param callable(): array<string, mixed> $load @return array<string, mixed> */
    private function resolve(string $operation, array $args, int $ttl, callable $load, bool $match = false): array
    {
        $key = hash('sha256', 'v1|' . $operation . '|' . json_encode($this->normaliseArgs($args), JSON_THROW_ON_ERROR));
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
                throw new ApiException($error->status, $error->status === 502 ? 'CATALOG_PROVIDER_BAD_RESPONSE' : 'CATALOG_PROVIDER_UNAVAILABLE', 'Catalog enrichment is temporarily unavailable.');
            }
            if (!$match && isset($payload['items']) && is_array($payload['items']) && $payload['items'] === []) {
                if ($cached !== null && $cached['usableStale']) { $this->pdo->commit(); return $this->response($cached['payload'], $cached['status'], true, false); }
                throw new ApiException(503, 'CATALOG_PROVIDER_UNAVAILABLE', 'Catalog enrichment is temporarily unavailable.');
            }
            $status = $match && $payload['item'] === null ? 'not_found' : 'matched';
            $ttlSeconds = $status === 'not_found' ? 86400 : $ttl;
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
    private function response(array $payload, string $status, bool $stale, bool $match): array { return ($match ? ['status' => $status, 'item' => $payload['item']] : $payload) + ['cache' => ['stale' => $stale]]; }
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
    private function throttleMatch(string $accountId, string $ipKey): void
    {
        $this->pdo->beginTransaction();
        try {
            AdvisoryLock::account($this->pdo, $accountId);
            AdvisoryLock::verifyIp($this->pdo, $ipKey);
            if ($this->matchThrottle->countForAccount($accountId, 60) >= 30 || $this->matchThrottle->countForIp($ipKey, 60) >= 60) {
                throw new ApiException(429, 'CATALOG_MATCH_RATE_LIMITED', 'Too many catalog matching requests. Try again later.');
            }
            $this->matchThrottle->record($accountId, $ipKey);
            $this->pdo->commit();
        } catch (Throwable $error) { if ($this->pdo->inTransaction()) $this->pdo->rollBack(); throw $error; }
    }
}
