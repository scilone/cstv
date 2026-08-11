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
        string $objectKey,
        string $payload,
        int $schemaVersion,
        ?string $ifMatch,
    ): array {
        $etag = hash('sha256', $payload);
        $this->pdo->beginTransaction();
        try {
            AdvisoryLock::accountJournal($this->pdo, $accountId);
            $this->requireOwnedProfileForMutation($profileId, $accountId);
            AdvisoryLock::object($this->pdo, $profileId, $namespace, $objectKey);
            $current = $this->objects->findMetadataForUpdate($profileId, $namespace, $objectKey);
            if (!$this->matches($ifMatch, $current === null ? null : (string) $current['etag'])) {
                throw new ApiException(412, 'ETAG_MISMATCH', 'If-Match does not match the current ETag.');
            }

            $this->objects->upsert($profileId, $namespace, $objectKey, $payload, $etag, $schemaVersion);
            $this->objects->addChange($accountId, $profileId, $namespace, $objectKey, 'UPSERT', $etag);
            $this->pdo->commit();

            return ['etag' => $etag, 'created' => $current === null];
        } catch (Throwable $exception) {
            if ($this->pdo->inTransaction()) {
                $this->pdo->rollBack();
            }
            throw $exception;
        }
    }

    public function delete(
        string $accountId,
        string $profileId,
        string $namespace,
        string $objectKey,
        ?string $ifMatch,
    ): void {
        $this->pdo->beginTransaction();
        try {
            AdvisoryLock::accountJournal($this->pdo, $accountId);
            $this->requireOwnedProfileForMutation($profileId, $accountId);
            AdvisoryLock::object($this->pdo, $profileId, $namespace, $objectKey);
            $current = $this->objects->findMetadataForUpdate($profileId, $namespace, $objectKey);
            // RFC 9110: If-Match on a resource that no longer exists must fail, otherwise a client
            // holding a stale ETag would believe it deleted the version it still had in hand.
            if (!$this->matches($ifMatch, $current === null ? null : (string) $current['etag'])) {
                throw new ApiException(412, 'ETAG_MISMATCH', 'If-Match does not match the current ETag.');
            }
            if ($current === null) {
                $this->pdo->commit();
                return;
            }

            $this->objects->delete($profileId, $namespace, $objectKey);
            $this->objects->addChange($accountId, $profileId, $namespace, $objectKey, 'DELETE', null);
            $this->pdo->commit();
        } catch (Throwable $exception) {
            if ($this->pdo->inTransaction()) {
                $this->pdo->rollBack();
            }
            throw $exception;
        }
    }

    /** @return list<array<string, mixed>> */
    public function list(string $accountId, string $profileId, ?string $namespace): array
    {
        $this->requireOwnedProfile($profileId, $accountId);
        return array_map(static fn (array $object): array => [
            'namespace' => (string) $object['namespace'],
            'key' => (string) $object['object_key'],
            'etag' => (string) $object['etag'],
            'schemaVersion' => (int) $object['schema_version'],
            'compressedSize' => (int) $object['compressed_size'],
            'updatedAt' => DateFormatter::iso8601((string) $object['updated_at']),
        ], $this->objects->listMetadata($profileId, $namespace));
    }

    /** @return array<string, mixed> */
    public function get(string $accountId, string $profileId, string $namespace, string $objectKey): array
    {
        $object = $this->objects->getOwned($accountId, $profileId, $namespace, $objectKey);
        if ($object === null) {
            throw new ApiException(404, 'OBJECT_NOT_FOUND', 'Object was not found.');
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

    private function matches(?string $ifMatch, ?string $currentEtag): bool
    {
        if ($ifMatch === null || trim($ifMatch) === '') {
            return true;
        }
        if ($currentEtag === null) {
            return false;
        }

        foreach (explode(',', $ifMatch) as $candidate) {
            $candidate = trim($candidate);
            if ($candidate === '*' || hash_equals('"' . $currentEtag . '"', $candidate)) {
                return true;
            }
        }

        return false;
    }
}
