<?php

declare(strict_types=1);

namespace Cstv\Backend\Auth;

use Cstv\Backend\Shared\Uuid;
use PDO;

final readonly class OtpRepository
{
    public function __construct(private PDO $pdo)
    {
    }

    public function lockEmail(string $email): void
    {
        $statement = $this->pdo->prepare('SELECT pg_advisory_xact_lock(hashtextextended(:email, 0))');
        $statement->execute(['email' => 'otp:' . $email]);
    }

    /**
     * Drops the codes of one email that are older than the rate-limit window and the OTP TTL, so
     * neither the quota counters nor the verification path can observe them. Scoped to a single
     * email to stay on otp_codes_email_created_idx and inside the advisory lock already held.
     */
    public function purgeStaleForEmail(string $email, int $retentionSeconds): void
    {
        $statement = $this->pdo->prepare(
            'DELETE FROM otp_codes WHERE email = :email '
            . 'AND created_at < NOW() - make_interval(secs => :retention)',
        );
        $statement->bindValue(':email', $email);
        $statement->bindValue(':retention', $retentionSeconds, PDO::PARAM_INT);
        $statement->execute();
    }

    public function countRecentForEmail(string $email, int $windowSeconds): int
    {
        $statement = $this->pdo->prepare(
            'SELECT COUNT(*) FROM otp_codes '
            . 'WHERE email = :email AND created_at >= NOW() - make_interval(secs => :window)',
        );
        $statement->bindValue(':email', $email);
        $statement->bindValue(':window', $windowSeconds, PDO::PARAM_INT);
        $statement->execute();
        return (int) $statement->fetchColumn();
    }

    public function countRecentForIp(string $ip, int $windowSeconds): int
    {
        $statement = $this->pdo->prepare(
            'SELECT COUNT(*) FROM otp_codes '
            . 'WHERE request_ip = :ip AND created_at >= NOW() - make_interval(secs => :window)',
        );
        $statement->bindValue(':ip', $ip);
        $statement->bindValue(':window', $windowSeconds, PDO::PARAM_INT);
        $statement->execute();
        return (int) $statement->fetchColumn();
    }

    public function consumeActiveForEmail(string $email): void
    {
        $statement = $this->pdo->prepare(
            'UPDATE otp_codes SET consumed_at = NOW() '
            . 'WHERE email = :email AND consumed_at IS NULL AND expires_at > NOW()',
        );
        $statement->execute(['email' => $email]);
    }

    public function insert(string $email, string $codeHash, string $ip, int $attempts, int $ttlSeconds): void
    {
        // clock_timestamp(), not NOW(): NOW() is the transaction start time, so a request that
        // waited on the advisory lock could be stamped earlier than the one it followed and
        // latestForUpdate() would then return an already invalidated code.
        $statement = $this->pdo->prepare(
            'INSERT INTO otp_codes '
            . '(id, email, code_hash, request_ip, attempts_left, expires_at, consumed_at, created_at) '
            . 'VALUES (:id, :email, :code_hash, :request_ip, :attempts, '
            . 'clock_timestamp() + make_interval(secs => :ttl), NULL, clock_timestamp())',
        );
        $statement->bindValue(':id', Uuid::v4());
        $statement->bindValue(':email', $email);
        $statement->bindValue(':code_hash', $codeHash);
        $statement->bindValue(':request_ip', $ip);
        $statement->bindValue(':attempts', $attempts, PDO::PARAM_INT);
        $statement->bindValue(':ttl', $ttlSeconds, PDO::PARAM_INT);
        $statement->execute();
    }

    /** @return array<string, mixed>|null */
    public function latestForUpdate(string $email): ?array
    {
        $statement = $this->pdo->prepare(
            'SELECT id, code_hash, attempts_left, expires_at, consumed_at, '
            . '(expires_at > NOW()) AS not_expired '
            . 'FROM otp_codes WHERE email = :email ORDER BY created_at DESC, id DESC LIMIT 1 FOR UPDATE',
        );
        $statement->execute(['email' => $email]);
        $row = $statement->fetch();
        if ($row === false) {
            return null;
        }

        $row['not_expired'] = filter_var($row['not_expired'], FILTER_VALIDATE_BOOL);
        return $row;
    }

    public function decrementAttempts(string $id): void
    {
        $statement = $this->pdo->prepare(
            'UPDATE otp_codes SET attempts_left = GREATEST(attempts_left - 1, 0) WHERE id = :id',
        );
        $statement->execute(['id' => $id]);
    }

    public function consume(string $id): void
    {
        $statement = $this->pdo->prepare('UPDATE otp_codes SET consumed_at = NOW() WHERE id = :id');
        $statement->execute(['id' => $id]);
    }

    /**
     * Drops up to $limit verification attempts older than the sliding window, across all IPs, so
     * rows left by origins that never call again cannot accumulate. Bounded by row count (not just
     * sampling frequency): after a mass IP rotation, no single call pays for deleting every expired
     * row at once — repeated sampled calls each clear one more bounded batch until the backlog is
     * gone, without depending on any specific IP calling back. Relies on
     * auth_verify_attempts_created_idx to stay cheap. Returns the number of rows actually deleted,
     * so a caller can tell whether a backlog remains.
     */
    public function purgeExpiredVerifyAttempts(int $retentionSeconds, int $limit): int
    {
        $statement = $this->pdo->prepare(
            'DELETE FROM auth_verify_attempts WHERE id IN ('
            . 'SELECT id FROM auth_verify_attempts '
            . 'WHERE created_at < NOW() - make_interval(secs => :retention) '
            . 'ORDER BY created_at ASC LIMIT :limit'
            . ')',
        );
        $statement->bindValue(':retention', $retentionSeconds, PDO::PARAM_INT);
        $statement->bindValue(':limit', $limit, PDO::PARAM_INT);
        $statement->execute();
        return $statement->rowCount();
    }

    public function countRecentVerifyForIp(string $ipKey, int $windowSeconds): int
    {
        $statement = $this->pdo->prepare(
            'SELECT COUNT(*) FROM auth_verify_attempts '
            . 'WHERE request_ip = :ip AND created_at >= NOW() - make_interval(secs => :window)',
        );
        $statement->bindValue(':ip', $ipKey);
        $statement->bindValue(':window', $windowSeconds, PDO::PARAM_INT);
        $statement->execute();
        return (int) $statement->fetchColumn();
    }

    public function recordVerifyAttempt(string $ipKey): void
    {
        // clock_timestamp(), not NOW(): verification runs outside the OTP transaction, but keeping
        // wall-clock stamps makes the sliding window immune to transaction-start skew all the same.
        $statement = $this->pdo->prepare(
            'INSERT INTO auth_verify_attempts (id, request_ip, created_at) '
            . 'VALUES (:id, :ip, clock_timestamp())',
        );
        $statement->bindValue(':id', Uuid::v4());
        $statement->bindValue(':ip', $ipKey);
        $statement->execute();
    }
}
