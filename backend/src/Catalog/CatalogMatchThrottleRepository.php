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

    /**
     * T29 débit : délai réel, en secondes, avant qu'une tentative supplémentaire repasse sous
     * `$limit` sur la fenêtre glissante — jamais une constante arbitraire. La fenêtre étant glissante,
     * il suffit que les `count - $limit + 1` tentatives les plus anciennes en sortent : la dernière à
     * expirer est celle d'`OFFSET count - $limit` (tri croissant), et elle sort à
     * `created_at + $window`. `null` quand le quota n'est pas atteint (rien à attendre).
     *
     * Le calcul est fait par PostgreSQL contre son propre `NOW()` (= `transaction_timestamp()`, donc
     * exactement l'instant utilisé par `count()` dans la même transaction) : aucune comparaison entre
     * l'horloge PHP et l'horloge SQL, donc aucun décalage possible. Arrondi supérieur, plancher 1 s —
     * un `Retry-After: 0` inviterait le client à réessayer immédiatement.
     */
    public function secondsUntilAccountSlot(string $accountId, int $window, int $limit): ?int
    {
        return $this->secondsUntilSlot('account_id = :key', $accountId, $window, $limit);
    }

    public function secondsUntilIpSlot(string $ipKey, int $window, int $limit): ?int
    {
        return $this->secondsUntilSlot('ip_key = :key', $ipKey, $window, $limit);
    }

    private function secondsUntilSlot(string $predicate, string $key, int $window, int $limit): ?int
    {
        $count = $this->count($predicate, $key, $window);
        if ($count < $limit) return null;
        // `$window`/`$offset` sont des entiers internes (constantes de service, jamais une entrée
        // client) : interpolés car PDO n'autorise pas de paramètre lié dans un OFFSET ni la
        // réutilisation d'un même placeholder nommé.
        $statement = $this->pdo->prepare(sprintf(
            'SELECT GREATEST(1, CEIL(EXTRACT(EPOCH FROM (created_at + make_interval(secs => %d)) - NOW())))::int'
            . ' FROM catalog_match_attempts WHERE %s AND created_at >= NOW() - make_interval(secs => %d)'
            . ' ORDER BY created_at ASC OFFSET %d LIMIT 1',
            $window,
            $predicate,
            $window,
            $count - $limit,
        ));
        $statement->execute(['key' => $key]);
        $seconds = $statement->fetchColumn();
        return $seconds === false ? null : (int) $seconds;
    }

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
