<?php

declare(strict_types=1);

namespace Cstv\Backend\Catalog;

/** F45 §7.11/§8.6: what `CatalogMatchEngine::resolve()` hands back to `CatalogService`. */
final readonly class CatalogMatchResult
{
    private function __construct(
        public string $status,
        public ?string $externalId,
        public ?int $confidence,
        public ?string $method,
        public int $version,
        /** @var array<string, mixed>|null full item payload (§8.7) once matched */
        public ?array $item,
    ) {
    }

    /** @param array<string, mixed> $item */
    public static function matched(string $externalId, int $confidence, string $method, int $version, array $item): self
    {
        return new self('matched', $externalId, $confidence, $method, $version, $item);
    }

    /** No candidate at all — the provider genuinely has nothing under that title (legacy status). */
    public static function notFound(int $version): self
    {
        return new self('not_found', null, null, null, $version, null);
    }

    /** Candidates existed but none cleared the acceptance bar (§7.11) — distinct from `notFound`. */
    public static function unresolved(int $version): self
    {
        return new self('unresolved', null, null, null, $version, null);
    }
}
