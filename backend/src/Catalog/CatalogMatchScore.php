<?php

declare(strict_types=1);

namespace Cstv\Backend\Catalog;

/**
 * F45 §7.11/§8.6: a candidate's score plus which signals produced it, for `matchMethod`.
 *
 * `value` is intentionally NOT clamped to [0, 100] while scoring: two candidates can each pass 100
 * on raw signal strength, and clamping early would erase the very margin `CatalogMatchEngine` needs
 * to tell them apart (a +15 director bonus on top of a 90 must still read as a 15-point gap, not get
 * swallowed by a 100 ceiling). Only `confidence()` clamps, for the value actually returned to callers.
 */
final readonly class CatalogMatchScore
{
    /** @param list<string> $signals contributing signal names in evaluation order */
    public function __construct(
        public int $value,
        public array $signals,
        public bool $decisive = false,
    ) {
    }

    public function method(): string
    {
        return $this->signals === [] ? 'title' : implode('+', $this->signals);
    }

    public function confidence(): int
    {
        return max(0, min(100, $this->value));
    }

    public function addingSignal(string $signal, int $delta, bool $decisive = false): self
    {
        return new self($this->value + $delta, [...$this->signals, $signal], $this->decisive || $decisive);
    }
}
