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
        /** T29 §8.7 : délai TMDB `Retry-After` connu, seulement renseigné pour `retry`. */
        public ?int $retryAfterSeconds = null,
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

    /**
     * T29 §7.3/§8.7 : résolution non aboutie pour une raison **technique** temporaire (429 provider,
     * budget TMDB local épuisé, erreur réseau transitoire) — jamais un résultat métier. Distinct de
     * `unresolved` : un client ne doit jamais persister ce statut comme un échec de matching définitif
     * (§7.3), seulement reprogrammer l'item.
     */
    public static function retry(int $version, ?int $retryAfterSeconds): self
    {
        return new self('retry', null, null, null, $version, null, $retryAfterSeconds);
    }
}
