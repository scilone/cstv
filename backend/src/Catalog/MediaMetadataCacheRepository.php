<?php

declare(strict_types=1);

namespace Cstv\Backend\Catalog;

use PDO;

final readonly class MediaMetadataCacheRepository
{
    public function __construct(private PDO $pdo) {}
    /** @return array<string, mixed>|null */
    public function find(string $key): ?array
    {
        $statement = $this->pdo->prepare('SELECT payload, result_status, expires_at > NOW() AS fresh, stale_until > NOW() AS usable_stale FROM media_metadata_cache WHERE cache_key = :key');
        $statement->execute(['key' => $key]);
        $row = $statement->fetch(PDO::FETCH_ASSOC);
        if ($row === false) return null;
        $payload = json_decode((string) $row['payload'], true, 512, JSON_THROW_ON_ERROR);
        return ['payload' => $payload, 'status' => (string) $row['result_status'], 'fresh' => (bool) $row['fresh'], 'usableStale' => (bool) $row['usable_stale']];
    }

    /**
     * T29 §8.4 : lecture multi-clés — un seul SELECT indexé au lieu de N pour un batch, remplaçant
     * `find()` appelé en boucle. Ne retourne que les clés trouvées (clés absentes simplement omises,
     * comme `find()` renvoie `null` pour elles) ; l'appelant décide du sort des misses/stale (§8.6, le
     * chemin unitaire `resolve()` reste la seule source de vérité pour le verrouillage/single-flight).
     *
     * @param list<string> $keys @return array<string, array<string, mixed>> indexé par cache_key
     */
    public function findMany(array $keys): array
    {
        if ($keys === []) return [];
        $statement = $this->pdo->prepare(
            'SELECT cache_key, payload, result_status, expires_at > NOW() AS fresh, stale_until > NOW() AS usable_stale ' .
            'FROM media_metadata_cache WHERE cache_key = ANY(:keys::text[])',
        );
        $statement->execute(['keys' => PgArray::encode($keys)]);
        $result = [];
        foreach ($statement->fetchAll(PDO::FETCH_ASSOC) as $row) {
            $result[(string) $row['cache_key']] = [
                'payload' => json_decode((string) $row['payload'], true, 512, JSON_THROW_ON_ERROR),
                'status' => (string) $row['result_status'],
                'fresh' => (bool) $row['fresh'],
                'usableStale' => (bool) $row['usable_stale'],
            ];
        }
        return $result;
    }

    /** @param array<string, mixed> $payload */
    public function put(string $key, array $payload, string $status, int $ttlSeconds, int $staleSeconds): void
    {
        $statement = $this->pdo->prepare("INSERT INTO media_metadata_cache (cache_key,payload,result_status,expires_at,stale_until) VALUES (:key,CAST(:payload AS jsonb),:status,NOW() + (:ttl || ' seconds')::interval,NOW() + (:stale || ' seconds')::interval) ON CONFLICT (cache_key) DO UPDATE SET payload=EXCLUDED.payload,result_status=EXCLUDED.result_status,expires_at=EXCLUDED.expires_at,stale_until=EXCLUDED.stale_until,updated_at=NOW()");
        $statement->execute(['key' => $key, 'payload' => json_encode($payload, JSON_THROW_ON_ERROR), 'status' => $status, 'ttl' => $ttlSeconds, 'stale' => $ttlSeconds + $staleSeconds]);
    }
    public function tryLock(string $key): bool
    {
        $this->pdo->exec("SET LOCAL lock_timeout = '250ms'");
        $statement = $this->pdo->prepare('SELECT pg_try_advisory_xact_lock(hashtext(:key))');
        $statement->execute(['key' => $key]);
        return $statement->fetchColumn() === true;
    }
    public function purge(): void { $this->pdo->exec('DELETE FROM media_metadata_cache WHERE stale_until <= NOW()'); }
}
