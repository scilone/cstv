<?php

declare(strict_types=1);

namespace Cstv\Backend\Tests\Support;

use Cstv\Backend\Shared\MonotonicClock;

/**
 * Horloge monotone déterministe : chaque lecture avance de `$stepNanos`. Permet de faire expirer le
 * budget de batch (T29 débit §4) sans qu'aucun test n'attende réellement 7 secondes.
 *
 * `$stepNanos` est mutable pour qu'un même test puisse préchauffer le cache avec une horloge figée
 * (aucune deadline) puis rejouer la même requête avec une horloge qui dépasse immédiatement le budget.
 */
final class FakeMonotonicClock implements MonotonicClock
{
    private int $current = 0;

    public function __construct(public int $stepNanos = 0)
    {
    }

    public function nanos(): int
    {
        $value = $this->current;
        $this->current += $this->stepNanos;

        return $value;
    }
}
