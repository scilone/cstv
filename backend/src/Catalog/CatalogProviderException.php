<?php

declare(strict_types=1);

namespace Cstv\Backend\Catalog;

use RuntimeException;

final class CatalogProviderException extends RuntimeException
{
    /** F45-R8 : `retryAfterSeconds` porte le `Retry-After` TMDB (429) jusqu'à `CatalogService`, qui le republie sur l'`ApiException` renvoyée au client — avant ce champ, le délai lu depuis TMDB s'arrêtait à `TmdbClient`. */
    public function __construct(public readonly int $status, string $message = 'Catalog provider is unavailable.', public readonly ?int $retryAfterSeconds = null)
    {
        parent::__construct($message);
    }
}
