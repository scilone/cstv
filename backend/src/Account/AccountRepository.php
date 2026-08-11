<?php

declare(strict_types=1);

namespace Cstv\Backend\Account;

use Cstv\Backend\Shared\Uuid;
use PDO;

final readonly class AccountRepository
{
    public function __construct(private PDO $pdo)
    {
    }

    /** @return array<string, mixed>|null */
    public function findById(string $id): ?array
    {
        $statement = $this->pdo->prepare(
            'SELECT id, email, enabled, active_until, created_at, updated_at, '
            . '(active_until > NOW()) AS not_expired FROM accounts WHERE id = :id',
        );
        $statement->execute(['id' => $id]);
        $account = $statement->fetch();

        return $account === false ? null : $this->normalize($account);
    }

    /** @return array<string, mixed>|null */
    public function findByEmailForUpdate(string $email): ?array
    {
        $statement = $this->pdo->prepare(
            'SELECT id, email, enabled, active_until, created_at, updated_at, '
            . '(active_until > NOW()) AS not_expired FROM accounts WHERE email = :email FOR UPDATE',
        );
        $statement->execute(['email' => $email]);
        $account = $statement->fetch();

        return $account === false ? null : $this->normalize($account);
    }

    /** @return array<string, mixed> */
    public function createWithDefaultProfile(string $email): array
    {
        $accountId = Uuid::v4();
        $profileId = Uuid::v4();
        $account = $this->pdo->prepare(
            "INSERT INTO accounts (id, email, enabled, active_until, created_at, updated_at) "
            . "VALUES (:id, :email, TRUE, NOW() + INTERVAL '1 year', NOW(), NOW()) "
            . 'RETURNING id, email, enabled, active_until, created_at, updated_at, TRUE AS not_expired',
        );
        $account->execute(['id' => $accountId, 'email' => $email]);

        $profile = $this->pdo->prepare(
            'INSERT INTO profiles (id, account_id, name, avatar_id, created_at, updated_at) '
            . "VALUES (:id, :account_id, 'Profil 1', 0, NOW(), NOW())",
        );
        $profile->execute(['id' => $profileId, 'account_id' => $accountId]);

        /** @var array<string, mixed> $row */
        $row = $account->fetch();
        return $this->normalize($row);
    }

    /** @param array<string, mixed> $account @return array<string, mixed> */
    private function normalize(array $account): array
    {
        $account['enabled'] = filter_var($account['enabled'], FILTER_VALIDATE_BOOL);
        $account['not_expired'] = filter_var($account['not_expired'], FILTER_VALIDATE_BOOL);
        return $account;
    }
}
