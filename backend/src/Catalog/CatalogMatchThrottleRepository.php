<?php

declare(strict_types=1);

namespace Cstv\Backend\Catalog;

use Cstv\Backend\Shared\Uuid;
use PDO;

final readonly class CatalogMatchThrottleRepository
{
    public function __construct(private PDO $pdo) {}
    public function countForAccount(string $accountId, int $window): int { return $this->count('account_id = :key', $accountId, $window); }
    public function countForIp(string $ipKey, int $window): int { return $this->count('ip_key = :key', $ipKey, $window); }
    public function record(string $accountId, string $ipKey, int $count = 1): void
    {
        $statement = $this->pdo->prepare('INSERT INTO catalog_match_attempts (id, account_id, ip_key) VALUES (:id, :account, :ip)');
        for ($index = 0; $index < $count; $index++) {
            $statement->execute(['id' => Uuid::v4(), 'account' => $accountId, 'ip' => $ipKey]);
        }
    }
    private function count(string $predicate, string $key, int $window): int
    {
        $statement = $this->pdo->prepare('SELECT COUNT(*) FROM catalog_match_attempts WHERE ' . $predicate . " AND created_at >= NOW() - (:window || ' seconds')::interval");
        $statement->execute(['key' => $key, 'window' => $window]);
        return (int) $statement->fetchColumn();
    }
}
