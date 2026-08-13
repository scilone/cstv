<?php

declare(strict_types=1);

namespace Cstv\Backend\Auth;

use Cstv\Backend\Account\AccountRepository;
use Cstv\Backend\Database\AdvisoryLock;
use Cstv\Backend\Shared\ApiException;
use Cstv\Backend\Shared\ClientIp;
use Cstv\Backend\Shared\Config;
use Cstv\Backend\Shared\Validator;
use DateTimeImmutable;
use PDO;
use Throwable;

final readonly class AuthService
{
    /** Roughly 1 verify in this many triggers a bounded global purge of expired throttle rows. */
    private const VERIFY_PURGE_SAMPLING = 50;

    /** Hard cap on rows removed by a single sampled purge, so one unlucky request never pays for
     * deleting an entire backlog after a mass IP rotation. */
    private const VERIFY_PURGE_BATCH_LIMIT = 500;

    public function __construct(
        private PDO $pdo,
        private Config $config,
        private OtpRepository $otps,
        private AccountRepository $accounts,
        private OtpSender $sender,
        private JwtService $jwt,
    ) {
    }

    public function request(mixed $rawEmail, string $ip): void
    {
        $email = Validator::email($rawEmail);
        $ip = filter_var($ip, FILTER_VALIDATE_IP) === false ? '0.0.0.0' : $ip;
        $code = $this->config->otpTestCode ?? str_pad((string) random_int(0, 999_999), 6, '0', STR_PAD_LEFT);
        $hash = hash_hmac('sha256', $code, $this->config->otpHashSecret);

        $this->pdo->beginTransaction();
        try {
            $this->otps->lockEmail($email);
            $this->otps->purgeStaleForEmail(
                $email,
                max($this->config->otpRateWindowSeconds, $this->config->otpTtlSeconds),
            );
            if (
                $this->otps->countRecentForEmail($email, $this->config->otpRateWindowSeconds)
                    >= $this->config->otpRequestLimitEmail
                || $this->otps->countRecentForIp($ip, $this->config->otpRateWindowSeconds)
                    >= $this->config->otpRequestLimitIp
            ) {
                throw new ApiException(429, 'OTP_RATE_LIMITED', 'Too many OTP requests. Try again later.');
            }

            $this->otps->consumeActiveForEmail($email);
            $this->otps->insert(
                $email,
                $hash,
                $ip,
                $this->config->otpMaxAttempts,
                $this->config->otpTtlSeconds,
            );
            $this->pdo->commit();
        } catch (Throwable $exception) {
            if ($this->pdo->inTransaction()) {
                $this->pdo->rollBack();
            }
            throw $exception;
        }

        $this->sender->send($email, $code);
    }

    /** @return array{accessToken: string, tokenType: string, expiresIn: int} */
    public function verify(mixed $rawEmail, mixed $rawCode, string $ip = '0.0.0.0'): array
    {
        $email = Validator::email($rawEmail);
        if (!is_string($rawCode) || !preg_match('/^\d{6}$/D', $rawCode)) {
            throw new ApiException(422, 'INVALID_OTP_FORMAT', 'OTP code must contain exactly six digits.');
        }

        $this->throttleVerify($ip);

        $this->pdo->beginTransaction();
        try {
            $this->otps->lockEmail($email);
            $otp = $this->otps->latestForUpdate($email);
            if ($otp === null) {
                throw new ApiException(400, 'INVALID_OTP', 'OTP code is invalid.');
            }
            if ($otp['consumed_at'] !== null) {
                throw new ApiException(400, 'OTP_CONSUMED', 'OTP code has already been used.');
            }
            if ($otp['not_expired'] !== true) {
                throw new ApiException(400, 'OTP_EXPIRED', 'OTP code has expired.');
            }
            if ((int) $otp['attempts_left'] <= 0) {
                throw new ApiException(400, 'OTP_ATTEMPTS_EXCEEDED', 'OTP attempt limit has been reached.');
            }

            $actualHash = hash_hmac('sha256', $rawCode, $this->config->otpHashSecret);
            if (!hash_equals((string) $otp['code_hash'], $actualHash)) {
                $this->otps->decrementAttempts((string) $otp['id']);
                $this->pdo->commit();
                throw new ApiException(400, 'INVALID_OTP', 'OTP code is invalid.');
            }

            $account = $this->accounts->findByEmailForUpdate($email);
            if ($account === null) {
                $account = $this->accounts->createWithDefaultProfile($email);
            }
            if ($account['enabled'] !== true) {
                throw new ApiException(403, 'ACCOUNT_DISABLED', 'This account is disabled.');
            }
            if ($account['not_expired'] !== true) {
                throw new ApiException(403, 'ACCOUNT_EXPIRED', 'This account has expired.');
            }
            $this->otps->consume((string) $otp['id']);
            $this->pdo->commit();
        } catch (Throwable $exception) {
            if ($this->pdo->inTransaction()) {
                $this->pdo->rollBack();
            }
            throw $exception;
        }

        $token = $this->jwt->issue(
            (string) $account['id'],
            new DateTimeImmutable((string) $account['active_until']),
        );
        return [
            'accessToken' => $token['token'],
            'tokenType' => 'Bearer',
            'expiresIn' => $token['expiresIn'],
        ];
    }

    /**
     * Caps verification attempts per client IP before any OTP transaction is opened, so a flood
     * cannot mobilise the heavy path for every request. A transaction-scoped advisory lock on the
     * IP key makes the count-then-insert atomic: a parallel burst cannot all read a below-limit
     * count and all be admitted. Only accepted attempts are recorded, bounding the table at roughly
     * the limit per IP per window; a sampled purge drops rows left by IPs that never return, one
     * bounded batch (VERIFY_PURGE_BATCH_LIMIT rows) at a time — never the whole expired backlog in
     * one request.
     */
    private function throttleVerify(string $ip): void
    {
        $ipKey = ClientIp::rateLimitKey(
            filter_var($ip, FILTER_VALIDATE_IP) === false ? '0.0.0.0' : $ip,
        );
        $window = $this->config->otpVerifyWindowSeconds;

        $this->pdo->beginTransaction();
        try {
            AdvisoryLock::verifyIp($this->pdo, $ipKey);
            if (random_int(1, self::VERIFY_PURGE_SAMPLING) === 1) {
                $this->otps->purgeExpiredVerifyAttempts($window, self::VERIFY_PURGE_BATCH_LIMIT);
            }
            $over = $this->otps->countRecentVerifyForIp($ipKey, $window) >= $this->config->otpVerifyLimitIp;
            if (!$over) {
                $this->otps->recordVerifyAttempt($ipKey);
            }
            $this->pdo->commit();
        } catch (Throwable $exception) {
            if ($this->pdo->inTransaction()) {
                $this->pdo->rollBack();
            }
            throw $exception;
        }

        if ($over) {
            throw new ApiException(429, 'OTP_VERIFY_RATE_LIMITED', 'Too many verification attempts. Try again later.');
        }
    }
}
