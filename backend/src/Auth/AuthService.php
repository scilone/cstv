<?php

declare(strict_types=1);

namespace Cstv\Backend\Auth;

use Cstv\Backend\Account\AccountRepository;
use Cstv\Backend\Shared\ApiException;
use Cstv\Backend\Shared\Config;
use Cstv\Backend\Shared\Validator;
use PDO;
use Throwable;

final readonly class AuthService
{
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
    public function verify(mixed $rawEmail, mixed $rawCode): array
    {
        $email = Validator::email($rawEmail);
        if (!is_string($rawCode) || !preg_match('/^\d{6}$/', $rawCode)) {
            throw new ApiException(422, 'INVALID_OTP_FORMAT', 'OTP code must contain exactly six digits.');
        }

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

            $this->otps->consume((string) $otp['id']);
            $account = $this->accounts->findByEmailForUpdate($email);
            if ($account === null) {
                $account = $this->accounts->createWithDefaultProfile($email);
            }
            $this->pdo->commit();
        } catch (Throwable $exception) {
            if ($this->pdo->inTransaction()) {
                $this->pdo->rollBack();
            }
            throw $exception;
        }

        $token = $this->jwt->issue((string) $account['id']);
        return [
            'accessToken' => $token['token'],
            'tokenType' => 'Bearer',
            'expiresIn' => $token['expiresIn'],
        ];
    }
}
