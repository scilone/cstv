<?php

declare(strict_types=1);

use Cstv\Backend\Bootstrap;

require dirname(__DIR__) . '/vendor/autoload.php';

try {
    $app = Bootstrap::createApp();
} catch (PDOException) {
    // The PDO connection is opened while wiring the app, so a database outage has to be answered
    // here to keep /health returning the documented 503 instead of a generic 500.
    http_response_code(503);
    header('Content-Type: application/json; charset=utf-8');
    echo '{"error":{"code":"DATABASE_UNAVAILABLE","message":"Database is unavailable."}}';

    return;
}

$app->run();
