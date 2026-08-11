<?php

declare(strict_types=1);

namespace Cstv\Backend\Profile;

use Cstv\Backend\Shared\ApiException;
use Cstv\Backend\Shared\Validator;
use PDO;
use Throwable;

final readonly class ProfileService
{
    public function __construct(private PDO $pdo, private ProfileRepository $profiles)
    {
    }

    /** @return array<string, mixed> */
    public function create(string $accountId, mixed $name, mixed $avatarId): array
    {
        return $this->profiles->create(
            $accountId,
            Validator::profileName($name),
            Validator::avatarId($avatarId),
        );
    }

    /** @param array<string, mixed> $changes @return array<string, mixed> */
    public function update(string $accountId, string $profileId, array $changes): array
    {
        if (!array_key_exists('name', $changes) && !array_key_exists('avatarId', $changes)) {
            throw new ApiException(422, 'EMPTY_UPDATE', 'At least one profile field must be provided.');
        }

        $name = array_key_exists('name', $changes) ? Validator::profileName($changes['name']) : null;
        $avatarId = array_key_exists('avatarId', $changes) ? Validator::avatarId($changes['avatarId']) : null;
        $profile = $this->profiles->updateOwned($profileId, $accountId, $name, $avatarId);
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
