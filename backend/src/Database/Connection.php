<?php

declare(strict_types=1);

namespace Cstv\Backend\Database;

use Cstv\Backend\Shared\Config;
use PDO;

final class Connection
{
    public static function create(Config $config): PDO
    {
        $pdo = new PDO(
            $config->databaseDsn,
            $config->databaseUser,
            $config->databasePassword,
            [
                PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
                PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
                PDO::ATTR_EMULATE_PREPARES => false,
                PDO::ATTR_STRINGIFY_FETCHES => false,
                PDO::ATTR_TIMEOUT => 5,
            ],
        );
        $pdo->exec("SET TIME ZONE 'UTC'");

        return $pdo;
    }
}
