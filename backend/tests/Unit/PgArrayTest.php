<?php

declare(strict_types=1);

namespace Cstv\Backend\Tests\Unit;

use Cstv\Backend\Catalog\PgArray;
use PHPUnit\Framework\TestCase;

final class PgArrayTest extends TestCase
{
    public function testEmptyArrayRoundTrips(): void
    {
        self::assertSame('{}', PgArray::encode([]));
        self::assertSame([], PgArray::decode('{}'));
        self::assertSame([], PgArray::decode(null));
    }

    public function testSimpleValuesRoundTrip(): void
    {
        $values = ['Action', 'Science Fiction', 'Horror'];
        self::assertSame($values, PgArray::decode(PgArray::encode($values)));
    }

    public function testValuesWithCommasQuotesAndBackslashesRoundTrip(): void
    {
        // Un titre alternatif réel peut contenir n'importe lequel de ces caractères.
        $values = ['Alien, le 8ème passager', 'The "Real" Deal', 'Back\\slash'];
        self::assertSame($values, PgArray::decode(PgArray::encode($values)));
    }

    public function testSingleValueRoundTrips(): void
    {
        self::assertSame(['Dune'], PgArray::decode(PgArray::encode(['Dune'])));
    }

    public function testUuidListRoundTrips(): void
    {
        $uuids = ['5e37ba2a-1cda-4faf-9f10-335b2f6556a7', '6f48cb3b-2ddb-5fbf-a021-446c3f6667b8'];
        self::assertSame($uuids, PgArray::decode(PgArray::encode($uuids)));
    }
}
