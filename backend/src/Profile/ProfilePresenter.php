<?php

declare(strict_types=1);

namespace Cstv\Backend\Profile;

use Cstv\Backend\Shared\DateFormatter;

final class ProfilePresenter
{
    /** @param array<string, mixed> $profile @return array<string, mixed> */
    public static function one(array $profile): array
    {
        return [
            'id' => (string) $profile['id'],
            'name' => (string) $profile['name'],
            'avatarId' => (int) $profile['avatar_id'],
            'createdAt' => DateFormatter::iso8601((string) $profile['created_at']),
            'updatedAt' => DateFormatter::iso8601((string) $profile['updated_at']),
        ];
    }
}
