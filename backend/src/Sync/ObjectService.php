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
        private int $maxNamespacesPerProfile,
        private int $maxStorageBytesPerAccount,
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
            // Account lock first (quota aggregates span every namespace and profile of the
            // account), then the finer namespace lock. Same order everywhere → no deadlock.
            AdvisoryLock::account($this->pdo, $accountId);
            $this->requireOwnedProfileForMutation($profileId, $accountId);
            AdvisoryLock::namespace($this->pdo, $profileId, $namespace);
            $current = $this->objects->findMetadataForUpdate($profileId, $namespace);
            $this->requireMatchingPrecondition($ifMatch, $current === null ? null : (string) $current['etag']);
            $this->requireWithinQuota($accountId, $profileId, $current, strlen($payload));

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
            AdvisoryLock::account($this->pdo, $accountId);
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

    /**
     * Bounds what a single account can store. A namespace that does not yet exist counts against
     * the per-profile namespace cardinality; the account's total stored bytes (minus the object
     * being replaced, plus the new payload) must stay within the storage quota. Replacing an
     * existing object with a smaller one is therefore never refused. Runs under the per-account
     * advisory lock held by put(), so the aggregates and the upsert are atomic against concurrent
     * writes on any namespace or profile of the same account.
     *
     * @param array<string, mixed>|null $current
     */
    private function requireWithinQuota(string $accountId, string $profileId, ?array $current, int $payloadBytes): void
    {
        if ($current === null && $this->objects->countNamespacesForProfile($profileId) >= $this->maxNamespacesPerProfile) {
            throw new ApiException(409, 'NAMESPACE_LIMIT_REACHED', 'The profile has reached its namespace limit.');
        }

        $currentBytes = $current === null ? 0 : (int) $current['compressed_size'];
        $projectedTotal = $this->objects->sumBytesForAccount($accountId) - $currentBytes + $payloadBytes;
        if ($projectedTotal > $this->maxStorageBytesPerAccount) {
            throw new ApiException(413, 'STORAGE_QUOTA_EXCEEDED', 'The account has reached its storage quota.');
        }
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
