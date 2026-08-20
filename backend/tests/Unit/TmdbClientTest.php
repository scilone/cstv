<?php

declare(strict_types=1);

namespace Cstv\Backend\Tests\Unit;

use Cstv\Backend\Catalog\TmdbClient;
use PHPUnit\Framework\TestCase;

/**
 * F45-R8 : `TmdbClient::get()` talks real curl against `api.themoviedb.org` when `$transport` is
 * null, with no injectable base URL — the retry loop itself isn't unit-testable without a live
 * server. `parseRetryAfter()` is the pure, I/O-free part of that fix (reading the `Retry-After`
 * header instead of always sleeping a fixed 100-250ms), kept `public static` specifically so it
 * stays covered here.
 */
final class TmdbClientTest extends TestCase
{
    public function testParsesANumericRetryAfterInSeconds(): void
    {
        self::assertSame(30, TmdbClient::parseRetryAfter(['Retry-After: 30']));
    }

    public function testParsesRetryAfterCaseInsensitivelyWithWhitespace(): void
    {
        self::assertSame(5, TmdbClient::parseRetryAfter(["retry-after:   5  \r\n"]));
    }

    public function testParsesAnHttpDateRetryAfterAsASecondsDelta(): void
    {
        $future = gmdate('D, d M Y H:i:s', time() + 120) . ' GMT';
        $delay = TmdbClient::parseRetryAfter(['Retry-After: ' . $future]);
        self::assertNotNull($delay);
        self::assertGreaterThan(110, $delay);
        self::assertLessThanOrEqual(120, $delay);
    }

    public function testReturnsNullWhenTheHeaderIsAbsent(): void
    {
        self::assertNull(TmdbClient::parseRetryAfter(['Content-Type: application/json', 'Date: Mon, 01 Jan 2024 00:00:00 GMT']));
    }

    public function testNeverReturnsZeroOrNegativeEvenForAPastDate(): void
    {
        self::assertSame(1, TmdbClient::parseRetryAfter(['Retry-After: 0']));
        $past = gmdate('D, d M Y H:i:s', time() - 100) . ' GMT';
        self::assertSame(1, TmdbClient::parseRetryAfter(['Retry-After: ' . $past]));
    }
}
