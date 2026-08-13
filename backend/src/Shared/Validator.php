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

    public static function namespace(mixed $value): string
    {
        if (!is_string($value) || !preg_match('/^[a-z0-9][a-z0-9._-]{0,63}$/D', $value)) {
            throw new ApiException(422, 'INVALID_NAMESPACE', 'namespace has an invalid format.');
        }

        return $value;
    }

}
