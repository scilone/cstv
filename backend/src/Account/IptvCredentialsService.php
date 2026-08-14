<?php

declare(strict_types=1);

namespace Cstv\Backend\Account;

use Cstv\Backend\Database\AdvisoryLock;
use Cstv\Backend\Shared\ApiException;
use Cstv\Backend\Shared\Crypto\EnvelopeCipher;
use Cstv\Backend\Shared\DateFormatter;
use PDO;
use Throwable;

final readonly class IptvCredentialsService
{
    public function __construct(private PDO $pdo, private IptvCredentialsRepository $repository, private EnvelopeCipher $cipher) {}

    /** @return array{credentials: array{host: string, port: int, username: string, password: string}, etag: string, updatedAt: string} */
    public function get(string $accountId): array
    {
        $row = $this->repository->find($accountId);
        if ($row === null) throw new ApiException(404, 'IPTV_CREDENTIALS_NOT_FOUND', 'IPTV credentials were not found.');
        return ['credentials' => $this->cipher->decrypt($accountId, (string) $row['key_id'], (string) $row['payload']), 'etag' => (string) $row['etag'], 'updatedAt' => DateFormatter::iso8601((string) $row['updated_at'])];
    }

    /** @param array{host: string, port: int, username: string, password: string} $credentials */
    public function put(string $accountId, array $credentials, ?string $ifMatch): string
    {
        $this->pdo->beginTransaction();
        try {
            AdvisoryLock::account($this->pdo, $accountId);
            $current = $this->repository->findForUpdate($accountId);
            $this->requireMatch($ifMatch, $current === null ? null : (string) $current['etag']);
            $encrypted = $this->cipher->encrypt($accountId, $credentials);
            $etag = hash('sha256', $encrypted['payload']);
            $this->repository->upsert($accountId, $encrypted['keyId'], $encrypted['payload'], $etag);
            $this->pdo->commit();
            return $etag;
        } catch (Throwable $e) {
            if ($this->pdo->inTransaction()) $this->pdo->rollBack();
            throw $e;
        }
    }

    public function delete(string $accountId, ?string $ifMatch): void
    {
        $this->pdo->beginTransaction();
        try {
            AdvisoryLock::account($this->pdo, $accountId);
            $current = $this->repository->findForUpdate($accountId);
            $this->requireMatch($ifMatch, $current === null ? null : (string) $current['etag']);
            if ($current !== null) $this->repository->delete($accountId);
            $this->pdo->commit();
        } catch (Throwable $e) {
            if ($this->pdo->inTransaction()) $this->pdo->rollBack();
            throw $e;
        }
    }

    private function requireMatch(?string $ifMatch, ?string $current): void
    {
        if ($ifMatch === null || trim($ifMatch) === '') return;
        foreach (explode(',', $ifMatch) as $candidate) {
            $candidate = trim($candidate);
            if ($current !== null && ($candidate === '*' || hash_equals('"' . $current . '"', $candidate))) return;
        }
        throw new ApiException(412, 'ETAG_MISMATCH', 'If-Match does not match the current IPTV credentials.');
    }
}
