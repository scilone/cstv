<?php

declare(strict_types=1);

namespace Cstv\Backend\Catalog;

interface MediaMetadataProvider
{
    /** @return list<array<string, mixed>> */
    public function trending(string $locale): array;
    /** @return list<array<string, mixed>> */
    public function popular(string $kind, int $page, string $locale): array;
    /** @return array<string, mixed>|null */
    public function match(string $kind, string $title, ?int $year, string $locale): ?array;
    /** @return list<array<string, mixed>> */
    public function videos(string $canonicalId, string $locale): array;
}
