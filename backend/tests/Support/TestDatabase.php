<?php

declare(strict_types=1);

namespace Cstv\Backend\Tests\Support;

use Cstv\Backend\Shared\Config;
use PDO;
use RuntimeException;

final class TestDatabase
{
    private const ALLOWED_DATABASE = 'cstv_test';

    public static function assertSafe(PDO $pdo, Config $config): void
    {
        $database = (string) $pdo->query('SELECT current_database()')->fetchColumn();
        self::assertSafeName($config->appEnv, $database);
    }

    public static function assertSafeName(string $appEnv, string $database): void
    {
        if ($appEnv !== 'test') {
            throw new RuntimeException('Refusing destructive test operation: APP_ENV must be test.');
        }
        if ($database !== self::ALLOWED_DATABASE || !str_ends_with($database, '_test')) {
            throw new RuntimeException(sprintf(
                'Refusing destructive test operation on database "%s"; only cstv_test is allowed.',
                $database,
            ));
        }
    }

    public static function reset(PDO $pdo, Config $config): void
    {
        self::assertSafe($pdo, $config);
        $pdo->exec(
            'TRUNCATE TABLE sync_changes, profile_objects, profiles, otp_codes, accounts RESTART IDENTITY CASCADE',
        );
    }
}
