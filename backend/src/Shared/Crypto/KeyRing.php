<?php

declare(strict_types=1);

namespace Cstv\Backend\Shared\Crypto;

use Cstv\Backend\Shared\ApiException;

final readonly class KeyRing
{
    /** @param array<string, string> $encodedKeys */
    public function __construct(private array $encodedKeys, public string $writeKeyId)
    {
    }

    public function writeKey(): string
    {
        return $this->key($this->writeKeyId);
    }

    public function key(string $id): string
    {
        $encoded = $this->encodedKeys[$id] ?? null;
        if ($encoded === null) {
            throw new ApiException(422, 'IPTV_CREDENTIALS_UNREADABLE', 'Saved IPTV credentials cannot be restored.');
        }
        try {
            return sodium_base642bin($encoded, SODIUM_BASE64_VARIANT_ORIGINAL);
        } catch (\SodiumException) {
            throw new ApiException(422, 'IPTV_CREDENTIALS_UNREADABLE', 'Saved IPTV credentials cannot be restored.');
        }
    }
}
