<?php

declare(strict_types=1);

namespace Cstv\Backend\Shared;

use DateTimeImmutable;
use DateTimeZone;

final class DateFormatter
{
    public static function iso8601(string $value): string
    {
        return (new DateTimeImmutable($value))
            ->setTimezone(new DateTimeZone('UTC'))
            ->format('Y-m-d\TH:i:s\Z');
    }
}
