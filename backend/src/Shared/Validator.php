<?php

declare(strict_types=1);

namespace Cstv\Backend\Shared;

final class Validator
{
    public static function email(mixed $value): string
    {
        if (!is_string($value)) {
            throw new ApiException(422, 'INVALID_EMAIL', 'A valid email address is required.');
        }

        $email = strtolower(trim($value));
        if (strlen($email) > 320 || filter_var($email, FILTER_VALIDATE_EMAIL) === false) {
            throw new ApiException(422, 'INVALID_EMAIL', 'A valid email address is required.');
        }

        return $email;
    }

    public static function uuid(mixed $value, string $field = 'id'): string
    {
        if (!is_string($value) || !preg_match('/^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/iD', $value)) {
            throw new ApiException(422, 'INVALID_UUID', sprintf('%s must be a valid UUID.', $field));
        }

        return strtolower($value);
    }

    public static function profileName(mixed $value): string
    {
        if (!is_string($value)) {
            throw new ApiException(422, 'INVALID_PROFILE_NAME', 'Profile name must be a string.');
        }

        $name = trim($value);
        if ($name === '' || mb_strlen($name) > 80 || preg_match('/[\x00-\x1F\x7F]/u', $name)) {
            throw new ApiException(422, 'INVALID_PROFILE_NAME', 'Profile name must contain 1 to 80 printable characters.');
        }

        return $name;
    }

    public static function avatarId(mixed $value): int
    {
        if (!is_int($value) || $value < 0 || $value > 10_000) {
            throw new ApiException(422, 'INVALID_AVATAR_ID', 'avatarId must be an integer between 0 and 10000.');
        }

        return $value;
    }

    public static function maxAgeRating(mixed $value): ?int
    {
        if ($value === null) {
            return null;
        }
        if (!is_int($value) || !in_array($value, [0, 10, 12, 16, 18], true)) {
            throw new ApiException(422, 'INVALID_MAX_AGE_RATING', 'maxAgeRating must be one of 0, 10, 12, 16, 18 or null.');
        }

        return $value;
    }

    public static function namespace(mixed $value): string
    {
        if (!is_string($value) || !preg_match('/^[a-z0-9][a-z0-9._-]{0,63}$/D', $value)) {
            throw new ApiException(422, 'INVALID_NAMESPACE', 'namespace has an invalid format.');
        }

        return $value;
    }

    /** @return array{host: string, port: int, username: string, password: string} */
    public static function iptvCredentials(array $body): array
    {
        $host = $body['host'] ?? null;
        $port = $body['port'] ?? null;
        $username = $body['username'] ?? null;
        $password = $body['password'] ?? null;
        if (!is_string($host) || trim($host) === '' || strlen($host) > 255 || preg_match('/[\x00-\x1F\x7F]/', $host)) {
            throw new ApiException(422, 'INVALID_IPTV_CREDENTIALS', 'host is invalid.');
        }
        if (!is_int($port) || $port < 1 || $port > 65535) {
            throw new ApiException(422, 'INVALID_IPTV_CREDENTIALS', 'port is invalid.');
        }
        if (!is_string($username) || $username === '' || strlen($username) > 128 || preg_match('/[\x00-\x1F\x7F]/', $username)) {
            throw new ApiException(422, 'INVALID_IPTV_CREDENTIALS', 'username is invalid.');
        }
        if (!is_string($password) || $password === '' || strlen($password) > 256 || preg_match('/[\x00-\x1F\x7F]/', $password)) {
            throw new ApiException(422, 'INVALID_IPTV_CREDENTIALS', 'password is invalid.');
        }
        return ['host' => trim($host), 'port' => $port, 'username' => $username, 'password' => $password];
    }

    /** @return array{deviceId: string, deviceName: string, takeover: bool} */
    public static function playbackLockDevice(array $body): array
    {
        $deviceId = self::uuid($body['deviceId'] ?? null, 'deviceId');
        $name = $body['deviceName'] ?? null;
        if ($name === null) $name = '';
        if (!is_string($name)) throw new ApiException(422, 'INVALID_PLAYBACK_LOCK_DEVICE', 'deviceName must be a string.');
        $sanitized = preg_replace('/[\x00-\x1F\x7F]/u', '', $name);
        if ($sanitized === null) throw new ApiException(422, 'INVALID_PLAYBACK_LOCK_DEVICE', 'deviceName must be valid UTF-8.');
        $name = trim($sanitized);
        // An absent automatic name is intentionally stored as an empty value;
        // clients render their localised “another device” fallback instead.
        if ($name !== '') $name = mb_strimwidth($name, 0, 64, '');
        $takeover = $body['takeover'] ?? false;
        if (!is_bool($takeover)) throw new ApiException(422, 'INVALID_PLAYBACK_LOCK_DEVICE', 'takeover must be a boolean.');
        return ['deviceId' => $deviceId, 'deviceName' => $name, 'takeover' => $takeover];
    }

}
