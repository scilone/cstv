<?php

declare(strict_types=1);

namespace Cstv\Backend\Catalog;

/** Shared "fold to comparable ASCII" used by both the match cache key and PostgreSQL-first lookup. */
final class TitleNormalizer
{
    public static function normalize(string $title): string
    {
        $ascii = iconv('UTF-8', 'ASCII//TRANSLIT//IGNORE', $title) ?: $title;
        return trim(preg_replace('/\s+/u', ' ', mb_strtolower($ascii)) ?? mb_strtolower($ascii));
    }
}
