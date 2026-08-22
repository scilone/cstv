<?php

declare(strict_types=1);

namespace Cstv\Backend\Shared;

/** Implémentation de production : `hrtime(true)`, garantie monotone par le noyau. */
final readonly class HrtimeMonotonicClock implements MonotonicClock
{
    public function nanos(): int
    {
        return hrtime(true);
    }
}
