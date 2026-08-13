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
        return [
            [''],
            ['../'],
            ['/root'],
            ['with/slash'],
            [str_repeat('x', 129)],
            // A trailing newline must not slip through: PCRE '$' matches before a final \n, so the
            // pattern needs the 'D' modifier. Without it "favorites\n" would be a second namespace.
            ["favorites\n"],
            ["favorites\r\n"],
        ];
    }

    #[DataProvider('unsafePathProvider')]
    public function testUnsafeNamespacesAreRejected(string $key): void
    {
        $this->expectException(ApiException::class);
        Validator::namespace($key);
    }

    public function testUuidIsNormalizedToLowercase(): void
    {
        self::assertSame(
            '11111111-1111-4111-8111-111111111101',
            Validator::uuid('11111111-1111-4111-8111-111111111101'),
        );
    }

    /** @return list<array{mixed}> */
    public static function invalidUuidProvider(): array
    {
        return [
            [null],
            ['not-a-uuid'],
            ['11111111-1111-4111-8111-11111111110'],
            // Trailing newline: rejected only once the pattern is anchored with 'D'.
            ["11111111-1111-4111-8111-111111111101\n"],
            ["11111111-1111-4111-8111-111111111101\r\n"],
        ];
    }

    #[DataProvider('invalidUuidProvider')]
    public function testInvalidUuidsAreRejected(mixed $value): void
    {
        $this->expectException(ApiException::class);
        Validator::uuid($value);
    }
}
