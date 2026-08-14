<?php

declare(strict_types=1);

namespace Cstv\Backend\Tests\Unit;

use Cstv\Backend\Shared\Config;
use InvalidArgumentException;
use PHPUnit\Framework\Attributes\DataProvider;
use PHPUnit\Framework\TestCase;

final class ConfigTest extends TestCase
{
    /** @return list<array{array<string, string>}> */
    public static function invalidKeyRingProvider(): array
    {
        return [
            [['IPTV_CREDENTIALS_KEYS' => '', 'IPTV_CREDENTIALS_KEY_ID' => 'dev']],
            [['IPTV_CREDENTIALS_KEYS' => 'dev:' . base64_encode('short'), 'IPTV_CREDENTIALS_KEY_ID' => 'dev']],
            [['IPTV_CREDENTIALS_KEYS' => 'other:' . base64_encode(str_repeat('a', 32)), 'IPTV_CREDENTIALS_KEY_ID' => 'dev']],
            [
                [
                    'APP_ENV' => 'production',
                    'OTP_TEST_CODE' => '',
                    'OTP_FROM_EMAIL' => 'ops@example.com',
                    'JWT_SECRET' => str_repeat('j', 32),
                    'OTP_HASH_SECRET' => str_repeat('o', 32),
                    'IPTV_CREDENTIALS_KEYS' => 'dev:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=',
                    'IPTV_CREDENTIALS_KEY_ID' => 'dev',
                ],
            ],
        ];
    }

    #[DataProvider('invalidKeyRingProvider')]
    public function testInvalidEncryptionConfigurationFailsAtStartup(array $environment): void
    {
        $previous = [];
        foreach ($environment as $name => $value) {
            $previous[$name] = getenv($name);
            putenv($name . '=' . $value);
        }

        try {
            $this->expectException(InvalidArgumentException::class);
            Config::fromEnvironment();
        } finally {
            foreach ($previous as $name => $value) {
                $value === false ? putenv($name) : putenv($name . '=' . $value);
            }
        }
    }
}
