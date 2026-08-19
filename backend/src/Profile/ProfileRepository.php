<?php

declare(strict_types=1);

namespace Cstv\Backend\Profile;

use Cstv\Backend\Shared\Uuid;
use PDO;

final readonly class ProfileRepository
{
    public function __construct(private PDO $pdo)
    {
    }

    /** @return list<array<string, mixed>> */
    public function listForAccount(string $accountId): array
    {
        $statement = $this->pdo->prepare(
            'SELECT id, name, avatar_id, max_age_rating, created_at, updated_at FROM profiles '
            . 'WHERE account_id = :account_id ORDER BY created_at, id',
        );
        $statement->execute(['account_id' => $accountId]);

        /** @var list<array<string, mixed>> $rows */
        $rows = $statement->fetchAll();
        return $rows;
    }

    /** @return array<string, mixed>|null */
    public function findOwned(string $profileId, string $accountId): ?array
    {
        $statement = $this->pdo->prepare(
            'SELECT id, account_id, name, avatar_id, max_age_rating, created_at, updated_at FROM profiles '
            . 'WHERE id = :id AND account_id = :account_id',
        );
        $statement->execute(['id' => $profileId, 'account_id' => $accountId]);
        $row = $statement->fetch();

        return $row === false ? null : $row;
    }

    /** @return array<string, mixed>|null */
    public function findOwnedForShare(string $profileId, string $accountId): ?array
    {
        $statement = $this->pdo->prepare(
            'SELECT id, account_id, name, avatar_id, max_age_rating, created_at, updated_at FROM profiles '
            . 'WHERE id = :id AND account_id = :account_id FOR SHARE',
        );
        $statement->execute(['id' => $profileId, 'account_id' => $accountId]);
        $row = $statement->fetch();

        return $row === false ? null : $row;
    }

    /** @return array<string, mixed> */
    public function create(string $accountId, string $name, int $avatarId): array
    {
        // max_age_rating est volontairement omis : la colonne défaut à NULL, et un
        // profil est toujours créé non bridé (décision F44 étape 2).
        $statement = $this->pdo->prepare(
            'INSERT INTO profiles (id, account_id, name, avatar_id, created_at, updated_at) '
            . 'VALUES (:id, :account_id, :name, :avatar_id, NOW(), NOW()) '
            . 'RETURNING id, name, avatar_id, max_age_rating, created_at, updated_at',
        );
        $statement->execute([
            'id' => Uuid::v4(),
            'account_id' => $accountId,
            'name' => $name,
            'avatar_id' => $avatarId,
        ]);

        /** @var array<string, mixed> $row */
        $row = $statement->fetch();
        return $row;
    }

    /**
     * maxAgeRating utilise COALESCE comme name/avatarId (null = inchangé) sauf si
     * $maxAgeRatingProvided vaut true : un déverrouillage explicite (retour à
     * `null`, profil non bridé) doit alors écraser la colonne, ce que COALESCE ne
     * permet pas de distinguer d'une absence de changement.
     *
     * @return array<string, mixed>|null
     */
    public function updateOwned(
        string $profileId,
        string $accountId,
        ?string $name,
        ?int $avatarId,
        bool $maxAgeRatingProvided,
        ?int $maxAgeRating,
    ): ?array {
        $maxAgeRatingAssignment = $maxAgeRatingProvided ? ':max_age_rating' : 'max_age_rating';
        $statement = $this->pdo->prepare(
            'UPDATE profiles SET '
            . 'name = COALESCE(:name, name), avatar_id = COALESCE(:avatar_id, avatar_id), '
            . "max_age_rating = {$maxAgeRatingAssignment}, updated_at = NOW() "
            . 'WHERE id = :id AND account_id = :account_id '
            . 'RETURNING id, name, avatar_id, max_age_rating, created_at, updated_at',
        );
        $statement->bindValue(':name', $name, $name === null ? PDO::PARAM_NULL : PDO::PARAM_STR);
        $statement->bindValue(':avatar_id', $avatarId, $avatarId === null ? PDO::PARAM_NULL : PDO::PARAM_INT);
        if ($maxAgeRatingProvided) {
            $statement->bindValue(':max_age_rating', $maxAgeRating, $maxAgeRating === null ? PDO::PARAM_NULL : PDO::PARAM_INT);
        }
        $statement->bindValue(':id', $profileId);
        $statement->bindValue(':account_id', $accountId);
        $statement->execute();
        $row = $statement->fetch();

        return $row === false ? null : $row;
    }

    /** @return list<string> */
    public function lockIdsForAccount(string $accountId): array
    {
        $statement = $this->pdo->prepare(
            'SELECT id FROM profiles WHERE account_id = :account_id ORDER BY id FOR UPDATE',
        );
        $statement->execute(['account_id' => $accountId]);

        return array_map(static fn (array $row): string => (string) $row['id'], $statement->fetchAll());
    }

    public function deleteOwned(string $profileId, string $accountId): bool
    {
        $statement = $this->pdo->prepare('DELETE FROM profiles WHERE id = :id AND account_id = :account_id');
        $statement->execute(['id' => $profileId, 'account_id' => $accountId]);

        return $statement->rowCount() === 1;
    }
}
