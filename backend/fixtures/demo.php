<?php

declare(strict_types=1);

return [
    'accounts' => [
        [
            'id' => '11111111-1111-4111-8111-111111111101',
            'email' => 'demo@cstv.local',
            'enabled' => true,
            'active' => true,
        ],
        [
            'id' => '11111111-1111-4111-8111-111111111102',
            'email' => 'expired@cstv.local',
            'enabled' => true,
            'active' => false,
        ],
        [
            'id' => '11111111-1111-4111-8111-111111111103',
            'email' => 'disabled@cstv.local',
            'enabled' => false,
            'active' => true,
        ],
    ],
    'profiles' => [
        [
            'id' => '22222222-2222-4222-8222-222222222201',
            'account_id' => '11111111-1111-4111-8111-111111111101',
            'name' => 'Nico',
            'avatar_id' => 3,
        ],
        [
            'id' => '22222222-2222-4222-8222-222222222202',
            'account_id' => '11111111-1111-4111-8111-111111111101',
            'name' => 'Enfant',
            'avatar_id' => 7,
        ],
        [
            'id' => '22222222-2222-4222-8222-222222222203',
            'account_id' => '11111111-1111-4111-8111-111111111102',
            'name' => 'Profil 1',
            'avatar_id' => 0,
        ],
        [
            'id' => '22222222-2222-4222-8222-222222222204',
            'account_id' => '11111111-1111-4111-8111-111111111103',
            'name' => 'Profil 1',
            'avatar_id' => 0,
        ],
    ],
    'objects' => [
        [
            'profile_id' => '22222222-2222-4222-8222-222222222201',
            'namespace' => 'favorites',
            'key' => 'movie-12345',
            'schema_version' => 1,
            'value' => [
                'schemaVersion' => 1,
                'id' => 12345,
                'type' => 'movie',
                'name' => 'Interstellar',
                'cover' => 'https://images.example.test/interstellar.jpg',
                'categoryId' => '42',
                'addedAt' => 1786441680000,
            ],
        ],
        [
            'profile_id' => '22222222-2222-4222-8222-222222222201',
            'namespace' => 'favorites',
            'key' => 'series-456',
            'schema_version' => 1,
            'value' => ['schemaVersion' => 1, 'id' => 456, 'type' => 'series', 'name' => 'The Expanse'],
        ],
        [
            'profile_id' => '22222222-2222-4222-8222-222222222201',
            'namespace' => 'playback',
            'key' => 'movie-12345',
            'schema_version' => 1,
            'value' => ['schemaVersion' => 1, 'positionMs' => 4120000, 'durationMs' => 10140000],
        ],
        [
            'profile_id' => '22222222-2222-4222-8222-222222222201',
            'namespace' => 'playback',
            'key' => 'episode-456',
            'schema_version' => 1,
            'value' => ['schemaVersion' => 1, 'positionMs' => 1320000, 'durationMs' => 2700000],
        ],
        [
            'profile_id' => '22222222-2222-4222-8222-222222222201',
            'namespace' => 'ratings',
            'key' => 'movie-12345',
            'schema_version' => 1,
            'value' => ['schemaVersion' => 1, 'rating' => 9, 'ratedAt' => 1786441700000],
        ],
        [
            'profile_id' => '22222222-2222-4222-8222-222222222201',
            'namespace' => 'category-preferences',
            'key' => 'snapshot',
            'schema_version' => 1,
            'value' => ['schemaVersion' => 1, 'hiddenCategoryIds' => ['18', '99'], 'updatedAt' => 1786441800000],
        ],
    ],
];
