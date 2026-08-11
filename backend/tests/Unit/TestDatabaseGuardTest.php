<?php

declare(strict_types=1);

namespace Cstv\Backend\Tests\Unit;

use Cstv\Backend\Tests\Support\TestDatabase;
use PHPUnit\Framework\Attributes\DataProvider;
use PHPUnit\Framework\TestCase;
use RuntimeException;

final class TestDatabaseGuardTest extends TestCase
{
    public function testOnlyExactDedicatedDatabaseIsAccepted(): void
    {
        TestDatabase::assertSafeName('test', 'cstv_test');
        self::addToAssertionCount(1);
    }

    /** @return list<array{string, string}> */
    public static function unsafeTargetProvider(): array
    {
        return [
            ['dev', 'cstv_test'],
            ['production', 'cstv_test'],
            ['test', 'cstv'],
            ['test', 'production'],
            ['test', 'another_test'],
        ];
    }

    #[DataProvider('unsafeTargetProvider')]
    public function testUnsafeTargetsAreRejected(string $environment, string $database): void
    {
        $this->expectException(RuntimeException::class);
        TestDatabase::assertSafeName($environment, $database);
    }
}
