<?php

declare(strict_types=1);

use Cstv\Backend\Database\Connection;
use Cstv\Backend\Database\Migrator;
use Cstv\Backend\Shared\Config;
use Cstv\Backend\Tests\Support\TestDatabase;

require dirname(__DIR__) . '/vendor/autoload.php';

$config = Config::fromEnvironment();
$pdo = Connection::create($config);
TestDatabase::assertSafe($pdo, $config);
(new Migrator($pdo, dirname(__DIR__) . '/migrations'))->migrate();

fwrite(STDOUT, "Safe test database ready: cstv_test.\n");
