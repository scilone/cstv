<?php

declare(strict_types=1);

putenv('APP_ENV=test');
putenv('APP_DEBUG=0');
putenv('POSTGRES_DB=' . (getenv('POSTGRES_DB') ?: 'cstv_test'));
putenv('OTP_TEST_CODE=123456');
putenv('MAX_OBJECT_SIZE_BYTES=1024');

require dirname(__DIR__) . '/vendor/autoload.php';
