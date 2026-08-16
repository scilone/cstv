<?php

declare(strict_types=1);

namespace Cstv\Backend\Catalog;

use RuntimeException;

final class CatalogProviderException extends RuntimeException
{
    public function __construct(public readonly int $status, string $message = 'Catalog provider is unavailable.')
    {
        parent::__construct($message);
    }
}
