<?php

declare(strict_types=1);

namespace Cstv\Backend\Shared;

final class ClientIp
{
    /**
     * Normalizes an IP into the key used for per-client rate limiting.
     *
     * IPv4 is returned unchanged. IPv6 is collapsed to its /64 prefix so an attacker who
     * controls a routed /64 (the common allocation) cannot rotate the host bits to reset the
     * counter on every request. An unparseable value is returned as-is: the caller is expected
     * to have validated it, and a raw string still bounds a single origin.
     */
    public static function rateLimitKey(string $ip): string
    {
        $packed = @inet_pton($ip);
        if ($packed === false || strlen($packed) !== 16) {
            return $ip;
        }

        $prefix = inet_ntop(substr($packed, 0, 8) . str_repeat("\0", 8));
        return $prefix === false ? $ip : $prefix . '/64';
    }
}
