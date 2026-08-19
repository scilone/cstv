<?php

declare(strict_types=1);

namespace Cstv\Backend\Profile;

use Cstv\Backend\Database\AdvisoryLock;
use Cstv\Backend\Shared\ApiException;
use Cstv\Backend\Shared\Validator;
use PDO;
use Throwable;

final readonly class ProfileService
{
    public function __construct(
        private PDO $pdo,
        private ProfileRepository $profiles,
        private int $maxProfilesPerAccount,
    ) {
    }

    /** @return array<string, mixed> */
    public function create(string $accountId, mixed $name, mixed $avatarId): array
    {
        $name = Validator::profileName($name);
        $avatarId = Validator::avatarId($avatarId);

        $this->pdo->beginTransaction();
        try {
            // Serialize on the account itself: a row-level FOR UPDATE only locks the profiles that
            // already exist, so two concurrent creations could both count the same rows and both
            // insert (phantom). The advisory lock makes count-then-insert atomic per account.
            AdvisoryLock::account($this->pdo, $accountId);
            if (count($this->profiles->lockIdsForAccount($accountId)) >= $this->maxProfilesPerAccount) {
                throw new ApiException(409, 'PROFILE_LIMIT_REACHED', 'The account has reached its profile limit.');
            }

            $profile = $this->profiles->create($accountId, $name, $avatarId);
            $this->pdo->commit();

            return $profile;
        } catch (Throwable $exception) {
            if ($this->pdo->inTransaction()) {
                $this->pdo->rollBack();
            }
            throw $exception;
        }
    }

    /** @param array<string, mixed> $changes @return array<string, mixed> */
    public function update(string $accountId, string $profileId, array $changes): array
    {
        $maxAgeRatingProvided = array_key_exists('maxAgeRating', $changes);
        if (!array_key_exists('name', $changes) && !array_key_exists('avatarId', $changes) && !$maxAgeRatingProvided) {
            throw new ApiException(422, 'EMPTY_UPDATE', 'At least one profile field must be provided.');
        }

        $name = array_key_exists('name', $changes) ? Validator::profileName($changes['name']) : null;
        $avatarId = array_key_exists('avatarId', $changes) ? Validator::avatarId($changes['avatarId']) : null;
        $maxAgeRating = $maxAgeRatingProvided ? Validator::maxAgeRating($changes['maxAgeRating']) : null;
        $profile = $this->profiles->updateOwned($profileId, $accountId, $name, $avatarId, $maxAgeRatingProvided, $maxAgeRating);
        if ($profile === null) {
            throw new ApiException(404, 'PROFILE_NOT_FOUND', 'Profile was not found.');
        }

        return $profile;
    }

    public function delete(string $accountId, string $profileId): void
    {
        $this->pdo->beginTransaction();
        try {
            $profileIds = $this->profiles->lockIdsForAccount($accountId);
            if (!in_array($profileId, $profileIds, true)) {
                throw new ApiException(404, 'PROFILE_NOT_FOUND', 'Profile was not found.');
            }
            if (count($profileIds) <= 1) {
                throw new ApiException(409, 'LAST_PROFILE_REQUIRED', 'An account must keep at least one profile.');
            }

            $this->profiles->deleteOwned($profileId, $accountId);
            $this->pdo->commit();
        } catch (Throwable $exception) {
            if ($this->pdo->inTransaction()) {
                $this->pdo->rollBack();
            }
            throw $exception;
        }
    }
}
