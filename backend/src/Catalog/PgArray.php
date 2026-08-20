<?php

declare(strict_types=1);

namespace Cstv\Backend\Catalog;

/** PDO has no native bind type for PostgreSQL `text[]`/`uuid[]` — encode/decode the literal form. */
final class PgArray
{
    /** @param list<string> $values */
    public static function encode(array $values): string
    {
        if ($values === []) return '{}';
        return '{' . implode(',', array_map(
            static fn (string $value): string => '"' . str_replace(['\\', '"'], ['\\\\', '\\"'], $value) . '"',
            $values,
        )) . '}';
    }

    /** @return list<string> */
    public static function decode(?string $raw): array
    {
        if ($raw === null || $raw === '{}') return [];
        $inner = substr($raw, 1, -1);
        $values = [];
        $current = '';
        $inQuotes = false;
        $escaped = false;
        for ($i = 0, $length = strlen($inner); $i < $length; $i++) {
            $char = $inner[$i];
            if ($escaped) { $current .= $char; $escaped = false; continue; }
            if ($char === '\\') { $escaped = true; continue; }
            if ($char === '"') { $inQuotes = !$inQuotes; continue; }
            if ($char === ',' && !$inQuotes) { $values[] = $current; $current = ''; continue; }
            $current .= $char;
        }
        $values[] = $current;
        return $values;
    }
}
