<?php

declare(strict_types=1);

namespace Cstv\Backend\Tests\Unit;

use Cstv\Backend\Shared\ApiException;
use Cstv\Backend\Shared\Validator;
use PHPUnit\Framework\Attributes\DataProvider;
use PHPUnit\Framework\TestCase;

final class ValidatorTest extends TestCase
{
    public function testEmailIsTrimmedAndNormalized(): void
    {
        self::assertSame('person@example.com', Validator::email(' Person@Example.COM '));
    }

    /** @return list<array{mixed}> */
    public static function invalidEmailProvider(): array
    {
        return [[null], [''], ['not-an-email'], [str_repeat('a', 321)]];
    }

    #[DataProvider('invalidEmailProvider')]
    public function testInvalidEmailsAreRejected(mixed $email): void
    {
        $this->expectException(ApiException::class);
        Validator::email($email);
    }

    public function testNamespacesRemainGeneric(): void
    {
        self::assertSame('future-domain.v2', Validator::namespace('future-domain.v2'));
    }

    /** @return list<array{string}> */
    public static function unsafePathProvider(): array
    {
        return [[''], ['../'], ['/root'], ['with/slash'], [str_repeat('x', 129)]];
    }

    #[DataProvider('unsafePathProvider')]
    public function testUnsafeNamespacesAreRejected(string $key): void
    {
        $this->expectException(ApiException::class);
        Validator::namespace($key);
    }
}
