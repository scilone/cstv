<?php

declare(strict_types=1);

namespace Cstv\Backend\Tests\Unit;

use Cstv\Backend\Shared\ApiException;
use Cstv\Backend\Shared\Crypto\EnvelopeCipher;
use Cstv\Backend\Shared\Crypto\KeyRing;
use PHPUnit\Framework\TestCase;

final class EnvelopeCipherTest extends TestCase
{
    private const ACCOUNT_A = '11111111-1111-4111-8111-111111111111';
    private const ACCOUNT_B = '22222222-2222-4222-8222-222222222222';

    public function testRoundTripUsesFreshNonceAndBindsPayloadToAccount(): void
    {
        $cipher = new EnvelopeCipher(new KeyRing(['test' => base64_encode(str_repeat('a', 32))], 'test'));
        $credentials = ['host' => 'https://panel.example', 'port' => 8080, 'username' => 'user', 'password' => 'secret'];
        $first = $cipher->encrypt(self::ACCOUNT_A, $credentials);
        $second = $cipher->encrypt(self::ACCOUNT_A, $credentials);

        self::assertNotSame($first['payload'], $second['payload']);
        self::assertSame($credentials, $cipher->decrypt(self::ACCOUNT_A, $first['keyId'], $first['payload']));
        try {
            $cipher->decrypt(self::ACCOUNT_B, $first['keyId'], $first['payload']);
            self::fail('The AAD must bind the payload to its account.');
        } catch (ApiException $exception) {
            self::assertSame(422, $exception->status);
            self::assertSame('IPTV_CREDENTIALS_UNREADABLE', $exception->errorCode);
            self::assertStringNotContainsString('secret', $exception->getMessage());
        }
    }

    public function testAlteredPayloadAndKeyRotationAreHandledSafely(): void
    {
        $cipher = new EnvelopeCipher(new KeyRing(['old' => base64_encode(str_repeat('a', 32))], 'old'));
        $credentials = ['host' => 'https://panel.example', 'port' => 8080, 'username' => 'user', 'password' => 'secret'];
        $encrypted = $cipher->encrypt(self::ACCOUNT_A, $credentials);

        foreach ([
            substr($encrypted['payload'], 0, -1),
            "\x02" . substr($encrypted['payload'], 1),
        ] as $payload) {
            try {
                $cipher->decrypt(self::ACCOUNT_A, 'old', $payload);
                self::fail('An altered envelope must be rejected.');
            } catch (ApiException $exception) {
                self::assertSame('IPTV_CREDENTIALS_UNREADABLE', $exception->errorCode);
                self::assertStringNotContainsString('secret', $exception->getMessage());
            }
        }

        $rotated = new EnvelopeCipher(new KeyRing([
            'old' => base64_encode(str_repeat('a', 32)),
            'new' => base64_encode(str_repeat('b', 32)),
        ], 'new'));
        self::assertSame($credentials, $rotated->decrypt(self::ACCOUNT_A, 'old', $encrypted['payload']));
    }
}
