<?php

declare(strict_types=1);

namespace Cstv\Backend\Sync;

use Cstv\Backend\Database\AdvisoryLock;
use Cstv\Backend\Profile\ProfileRepository;
use Cstv\Backend\Shared\ApiException;
use Cstv\Backend\Shared\DateFormatter;
use PDO;
use Throwable;

final readonly class ObjectService
{
    public function __construct(
        private PDO $pdo,
        private ProfileRepository $profiles,
        private ObjectRepository $objects,
    ) {
    }

    /** @return array{etag: string, created: bool} */
    public function put(
        string $accountId,
        string $profileId,
        string $namespace,
        string $payload,
        int $schemaVersion,
        ?string $ifMatch,
    ): array {
        $etag = hash('sha256', $payload);
        $this->pdo->beginTransaction();
        try {
            $this->requireOwnedProfileForMutation($profileId, $accountId);
            AdvisoryLock::namespace($this->pdo, $profileId, $namespace);
            $current = $this->objects->findMetadataForUpdate($profileId, $namespace);
            $this->requireMatchingPrecondition($ifMatch, $current === null ? null : (string) $current['etag']);

            $this->objects->upsert($profileId, $namespace, $payload, $etag, $schemaVersion);
            $this->pdo->commit();

            return ['etag' => $etag, 'created' => $current === null];
        } catch (Throwable $exception) {
            if ($this->pdo->inTransaction()) {
                $this->pdo->rollBack();
            }
            throw $exception;
        }
    }

    public function delete(string $accountId, string $profileId, string $namespace, ?string $ifMatch): void
    {
        $this->pdo->beginTransaction();
        try {
            $this->requireOwnedProfileForMutation($profileId, $accountId);
            AdvisoryLock::namespace($this->pdo, $profileId, $namespace);
            $current = $this->objects->findMetadataForUpdate($profileId, $namespace);
            $this->requireMatchingPrecondition($ifMatch, $current === null ? null : (string) $current['etag']);
            if ($current === null) {
                $this->pdo->commit();
                return;
            }

            $this->objects->delete($profileId, $namespace);
            $this->pdo->commit();
        } catch (Throwable $exception) {
            if ($this->pdo->inTransaction()) {
                $this->pdo->rollBack();
            }
            throw $exception;
        }
    }

    /** @return list<array<string, mixed>> */
    public function list(string $accountId, string $profileId): array
    {
        $this->requireOwnedProfile($profileId, $accountId);
        return array_map(static fn (array $object): array => [
            'namespace' => (string) $object['namespace'],
            'etag' => (string) $object['etag'],
            'schemaVersion' => (int) $object['schema_version'],
            'compressedSize' => (int) $object['compressed_size'],
            'updatedAt' => DateFormatter::iso8601((string) $object['updated_at']),
        ], $this->objects->listMetadata($profileId));
    }

    /** @return array<string, mixed> */
    public function get(string $accountId, string $profileId, string $namespace): array
    {
        $object = $this->objects->getOwned($accountId, $profileId, $namespace);
        if ($object === null) {
            throw new ApiException(404, 'OBJECT_NOT_FOUND', 'Namespace snapshot was not found.');
        }

        return $object;
    }

    private function requireOwnedProfile(string $profileId, string $accountId): void
    {
        if ($this->profiles->findOwned($profileId, $accountId) === null) {
            throw new ApiException(404, 'PROFILE_NOT_FOUND', 'Profile was not found.');
        }
    }

    private function requireOwnedProfileForMutation(string $profileId, string $accountId): void
    {
        if ($this->profiles->findOwnedForShare($profileId, $accountId) === null) {
            throw new ApiException(404, 'PROFILE_NOT_FOUND', 'Profile was not found.');
        }
    }

    private function requireMatchingPrecondition(?string $ifMatch, ?string $currentEtag): void
    {
        if ($currentEtag === null) {
            if ($ifMatch !== null && trim($ifMatch) !== '') {
                throw new ApiException(412, 'ETAG_MISMATCH', 'If-Match requires an existing namespace snapshot.');
            }
            return;
        }

        if ($ifMatch === null || trim($ifMatch) === '') {
            throw new ApiException(428, 'PRECONDITION_REQUIRED', 'If-Match is required for an existing namespace snapshot.');
        }

        foreach (explode(',', $ifMatch) as $candidate) {
            $candidate = trim($candidate);
            if ($candidate === '*' || hash_equals('"' . $currentEtag . '"', $candidate)) {
                return;
            }
        }

        throw new ApiException(412, 'ETAG_MISMATCH', 'If-Match does not match the current ETag.');
    }
}
