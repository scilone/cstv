<?php

declare(strict_types=1);

namespace Cstv\Backend\Catalog;

/** F45 §7.7: image sizing is device-aware. Invalid/absent header always falls back to MOBILE. */
enum DeviceType: string
{
    case Mobile = 'mobile';
    case Tablet = 'tablet';
    case Tv = 'tv';

    public static function fromHeader(?string $value): self
    {
        return self::tryFrom(strtolower(trim($value ?? ''))) ?? self::Mobile;
    }
}
