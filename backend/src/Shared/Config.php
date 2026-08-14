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
        public string $otpHashSecret,
        public ?string $otpTestCode,
        public string $otpFromEmail,
        public string $otpFromName,
        public int $otpTtlSeconds,
        public int $otpMaxAttempts,
        public int $otpRequestLimitEmail,
        public int $otpRequestLimitIp,
        public int $otpRateWindowSeconds,
        public int $otpVerifyLimitIp,
        public int $otpVerifyWindowSeconds,
        public int $maxObjectSizeBytes,
        public int $maxProfilesPerAccount,
        public int $maxNamespacesPerProfile,
        public int $maxStorageBytesPerAccount,
        /** @var array<string, string> key id => base64-encoded 32-byte key */
        public array $iptvCredentialsKeys,
        public string $iptvCredentialsKeyId,
        public int $maxIptvCredentialsBytes,
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

        $otpFromEmail = self::nullableString('OTP_FROM_EMAIL') ?? '';
        if ($environment === 'production' && $otpFromEmail === '') {
            throw new InvalidArgumentException('OTP_FROM_EMAIL must be configured in production.');
        }
        if ($otpFromEmail !== '' && filter_var($otpFromEmail, FILTER_VALIDATE_EMAIL) === false) {
            throw new InvalidArgumentException('OTP_FROM_EMAIL must be a valid email address.');
        }

        $otpFromName = self::string('OTP_FROM_NAME', 'CSTV');
        if (strlen($otpFromName) > 100 || preg_match('/[\r\n\x00]/', $otpFromName)) {
            throw new InvalidArgumentException('OTP_FROM_NAME must be a single header line of at most 100 bytes.');
        }

        $host = self::string('DB_HOST', 'postgres');
        $port = self::integer('DB_PORT', 5432, 1, 65535);
        $database = self::string('POSTGRES_DB', 'cstv');
        $defaultIptvCredentialsKeys = 'dev:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=';
        // An explicitly empty keyring is a configuration error, not an invitation
        // to fall back to the development key. Only an absent variable gets the
        // development default used by local/test installations.
        $configuredKeyRing = getenv('IPTV_CREDENTIALS_KEYS');
        $rawKeyRing = $configuredKeyRing === false ? $defaultIptvCredentialsKeys : trim($configuredKeyRing);
        $configuredKeyId = getenv('IPTV_CREDENTIALS_KEY_ID');
        $keyId = $configuredKeyId === false ? 'dev' : trim($configuredKeyId);
        $keyRing = self::iptvCredentialsKeyRing($rawKeyRing);
        if (!isset($keyRing[$keyId])) {
            throw new InvalidArgumentException('IPTV_CREDENTIALS_KEY_ID must exist in IPTV_CREDENTIALS_KEYS.');
        }
        $developmentKey = sodium_base642bin(explode(':', $defaultIptvCredentialsKeys, 2)[1], SODIUM_BASE64_VARIANT_ORIGINAL);
        $containsDevelopmentKey = false;
        foreach ($keyRing as $encodedKey) {
            $containsDevelopmentKey = $containsDevelopmentKey || hash_equals($developmentKey, sodium_base642bin($encodedKey, SODIUM_BASE64_VARIANT_ORIGINAL));
        }
        sodium_memzero($developmentKey);
        if ($environment === 'production' && $containsDevelopmentKey) {
            throw new InvalidArgumentException('Development IPTV credentials encryption key must be replaced in production.');
        }

        return new self(
            appEnv: $environment,
            appDebug: self::boolean('APP_DEBUG', false),
            databaseDsn: sprintf('pgsql:host=%s;port=%d;dbname=%s', $host, $port, $database),
            databaseUser: self::string('POSTGRES_USER', 'cstv'),
            databasePassword: self::string('POSTGRES_PASSWORD', 'cstv-dev-password'),
            jwtSecret: $jwtSecret,
            otpHashSecret: $otpHashSecret,
            otpTestCode: $otpTestCode,
            otpFromEmail: $otpFromEmail,
            otpFromName: $otpFromName,
            otpTtlSeconds: self::integer('OTP_TTL_SECONDS', 300, 60, 3600),
            otpMaxAttempts: self::integer('OTP_MAX_ATTEMPTS', 5, 1, 20),
            otpRequestLimitEmail: self::integer('OTP_REQUEST_LIMIT_EMAIL', 5, 1, 100),
            otpRequestLimitIp: self::integer('OTP_REQUEST_LIMIT_IP', 20, 1, 1000),
            otpRateWindowSeconds: self::integer('OTP_RATE_WINDOW_SECONDS', 3600, 60, 86_400),
            otpVerifyLimitIp: self::integer('OTP_VERIFY_LIMIT_IP', 30, 1, 10_000),
            otpVerifyWindowSeconds: self::integer('OTP_VERIFY_WINDOW_SECONDS', 60, 10, 86_400),
            maxObjectSizeBytes: self::integer('MAX_OBJECT_SIZE_BYTES', 1_048_576, 1, 16_777_216),
            maxProfilesPerAccount: self::integer('MAX_PROFILES_PER_ACCOUNT', 10, 1, 100),
            maxNamespacesPerProfile: self::integer('MAX_NAMESPACES_PER_PROFILE', 32, 1, 1_000),
            maxStorageBytesPerAccount: self::integer('MAX_STORAGE_BYTES_PER_ACCOUNT', 20_971_520, 1_024, 1_073_741_824),
            iptvCredentialsKeys: $keyRing,
            iptvCredentialsKeyId: $keyId,
            maxIptvCredentialsBytes: self::integer('MAX_IPTV_CREDENTIALS_BYTES', 4096, 1, 65_536),
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

    /** @return array<string, string> */
    private static function iptvCredentialsKeyRing(string $raw): array
    {
        $keys = [];
        foreach (explode(',', $raw) as $entry) {
            [$id, $encoded] = array_pad(explode(':', trim($entry), 2), 2, null);
            if (!is_string($id) || !preg_match('/^[a-z0-9_-]{1,32}$/D', $id) || !is_string($encoded)) {
                throw new InvalidArgumentException('IPTV_CREDENTIALS_KEYS has an invalid key entry.');
            }
            try {
                $key = sodium_base642bin($encoded, SODIUM_BASE64_VARIANT_ORIGINAL);
            } catch (\SodiumException) {
                throw new InvalidArgumentException('IPTV_CREDENTIALS_KEYS must contain valid base64 keys.');
            }
            if (strlen($key) !== SODIUM_CRYPTO_AEAD_XCHACHA20POLY1305_IETF_KEYBYTES || isset($keys[$id])) {
                throw new InvalidArgumentException('IPTV_CREDENTIALS_KEYS must contain unique 32-byte keys.');
            }
            $keys[$id] = $encoded;
        }
        if ($keys === []) {
            throw new InvalidArgumentException('IPTV_CREDENTIALS_KEYS must not be empty.');
        }
        return $keys;
    }
}
