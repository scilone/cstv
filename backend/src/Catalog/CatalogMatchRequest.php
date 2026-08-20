<?php

declare(strict_types=1);

namespace Cstv\Backend\Catalog;

final readonly class CatalogMatchRequest
{
    public function __construct(
        public string $kind,
        public string $title,
        public ?int $year,
        public string $locale,
        public CatalogMatchHints $hints,
    ) {}
}
