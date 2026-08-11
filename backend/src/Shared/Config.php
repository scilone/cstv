<?php

declare(strict_types=1);

namespace Cstv\Backend\Shared;

use InvalidArgumentException;

final readonly class Config
{
    public function __construct(
        public string $appEnv,
        public bool $appDebug,
        public string $databaseDsn,
        public string $databaseUser,
        public string $databasePassword,
        public string $jwtSecret,
        public int $jwtTtlSeconds,
        public string $otpHashSecret,
        public ?string $otpTestCode,
        public int $otpTtlSeconds,
        public int $otpMaxAttempts,
        public int $otpRequestLimitEmail,
        public int $otpRequestLimitIp,
        public int $otpRateWindowSeconds,
        public int $maxObjectSizeBytes,
    ) {
    }

    public static function fromEnvironment(): self
    {
        $environment = self::string('APP_ENV', 'dev');
        $otpTestCode = self::nullableString('OTP_TEST_CODE');

        if ($environment === 'production' && $otpTestCode !== null) {
            throw new InvalidArgumentException('OTP_TEST_CODE must not be configured in production.');
        }

        if ($otpTestCode !== null && !preg_match('/^\d{6}$/', $otpTestCode)) {
            throw new InvalidArgumentException('OTP_TEST_CODE must contain exactly six digits.');
        }

        $defaultJwtSecret = 'change-this-dev-jwt-secret-at-least-32-chars';
        $defaultOtpSecret = 'change-this-dev-otp-secret-at-least-32-chars';
        $jwtSecret = self::string('JWT_SECRET', $defaultJwtSecret);
        $otpHashSecret = self::string('OTP_HASH_SECRET', $defaultOtpSecret);
        if (strlen($jwtSecret) < 32 || strlen($otpHashSecret) < 32) {
            throw new InvalidArgumentException('JWT_SECRET and OTP_HASH_SECRET must contain at least 32 characters.');
        }
        if ($environment === 'production' && ($jwtSecret === $defaultJwtSecret || $otpHashSecret === $defaultOtpSecret)) {
            throw new InvalidArgumentException('Development secrets must be replaced in production.');
        }

        $host = self::string('DB_HOST', 'postgres');
        $port = self::integer('DB_PORT', 5432, 1, 65535);
        $database = self::string('POSTGRES_DB', 'cstv');

        return new self(
            appEnv: $environment,
            appDebug: self::boolean('APP_DEBUG', false),
            databaseDsn: sprintf('pgsql:host=%s;port=%d;dbname=%s', $host, $port, $database),
            databaseUser: self::string('POSTGRES_USER', 'cstv'),
            databasePassword: self::string('POSTGRES_PASSWORD', 'cstv-dev-password'),
            jwtSecret: $jwtSecret,
            jwtTtlSeconds: self::integer('JWT_TTL_SECONDS', 3600, 60, 2_592_000),
            otpHashSecret: $otpHashSecret,
            otpTestCode: $otpTestCode,
            otpTtlSeconds: self::integer('OTP_TTL_SECONDS', 300, 60, 3600),
            otpMaxAttempts: self::integer('OTP_MAX_ATTEMPTS', 5, 1, 20),
            otpRequestLimitEmail: self::integer('OTP_REQUEST_LIMIT_EMAIL', 5, 1, 100),
            otpRequestLimitIp: self::integer('OTP_REQUEST_LIMIT_IP', 20, 1, 1000),
            otpRateWindowSeconds: self::integer('OTP_RATE_WINDOW_SECONDS', 3600, 60, 86_400),
            maxObjectSizeBytes: self::integer('MAX_OBJECT_SIZE_BYTES', 1_048_576, 1, 16_777_216),
        );
    }

    private static function string(string $name, string $default): string
    {
        $value = getenv($name);
        return $value === false || trim($value) === '' ? $default : trim($value);
    }

    private static function nullableString(string $name): ?string
    {
        $value = getenv($name);
        return $value === false || trim($value) === '' ? null : trim($value);
    }

    private static function integer(string $name, int $default, int $minimum, int $maximum): int
    {
        $raw = getenv($name);
        if ($raw === false || $raw === '') {
            return $default;
        }

        if (filter_var($raw, FILTER_VALIDATE_INT) === false) {
            throw new InvalidArgumentException(sprintf('%s must be an integer.', $name));
        }

        $value = (int) $raw;
        if ($value < $minimum || $value > $maximum) {
            throw new InvalidArgumentException(sprintf('%s must be between %d and %d.', $name, $minimum, $maximum));
        }

        return $value;
    }

    private static function boolean(string $name, bool $default): bool
    {
        $raw = getenv($name);
        if ($raw === false || $raw === '') {
            return $default;
        }

        $value = filter_var($raw, FILTER_VALIDATE_BOOL, FILTER_NULL_ON_FAILURE);
        if ($value === null) {
            throw new InvalidArgumentException(sprintf('%s must be a boolean.', $name));
        }

        return $value;
    }
}
