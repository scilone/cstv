<?php

declare(strict_types=1);

namespace Cstv\Backend\Tests\Unit;

use Cstv\Backend\Shared\ClientIp;
use PHPUnit\Framework\Attributes\DataProvider;
use PHPUnit\Framework\TestCase;

final class ClientIpTest extends TestCase
{
    public function testIpv4IsKeptAsIs(): void
    {
        self::assertSame('203.0.113.7', ClientIp::rateLimitKey('203.0.113.7'));
    }

    public function testIpv6IsCollapsedToItsSixtyFourPrefix(): void
    {
        // Two addresses inside the same routed /64 must share a rate-limit key, so an attacker
        // cannot reset the counter by rotating the host bits.
        $first = ClientIp::rateLimitKey('2a01:e0a:820:7810:2a:9a59:cd8d:acc9');
        $second = ClientIp::rateLimitKey('2a01:e0a:820:7810:ffff:ffff:ffff:0001');

        self::assertSame($first, $second);
        self::assertStringEndsWith('/64', $first);
    }

    public function testDifferentSixtyFourPrefixesStayDistinct(): void
    {
        self::assertNotSame(
            ClientIp::rateLimitKey('2a01:e0a:820:7810::1'),
            ClientIp::rateLimitKey('2a01:e0a:820:7811::1'),
        );
    }

    /** @return list<array{string}> */
    public static function unparseableProvider(): array
    {
        return [['0.0.0.0'], ['not-an-ip'], ['']];
    }

    #[DataProvider('unparseableProvider')]
    public function testUnparseableValuesAreReturnedUnchanged(string $value): void
    {
        self::assertSame($value, ClientIp::rateLimitKey($value));
    }
}
