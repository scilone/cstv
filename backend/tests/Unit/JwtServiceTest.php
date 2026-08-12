<?php

declare(strict_types=1);

namespace Cstv\Backend\Tests\Unit;

use Cstv\Backend\Auth\JwtService;
use Cstv\Backend\Shared\Uuid;
use DateTimeImmutable;
use Firebase\JWT\JWT;
use Firebase\JWT\Key;
use InvalidArgumentException;
use PHPUnit\Framework\TestCase;

final class JwtServiceTest extends TestCase
{
    public function testExpirationIsTheAccountExpiration(): void
    {
        $secret = str_repeat('a', 48);
        $activeUntil = new DateTimeImmutable('+1 day');
        $token = (new JwtService($secret))->issue(Uuid::v4(), $activeUntil);

        $claims = JWT::decode($token['token'], new Key($secret, 'HS256'));
        self::assertSame($activeUntil->getTimestamp(), $claims->exp);
        self::assertSame($claims->exp - $claims->iat, $token['expiresIn']);
    }

    public function testExpiredAccountCannotBeIssuedAToken(): void
    {
        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('JWT expiration must be in the future.');
        (new JwtService(str_repeat('a', 48)))->issue(Uuid::v4(), new DateTimeImmutable('-1 second'));
    }
}
