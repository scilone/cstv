<?php

declare(strict_types=1);

namespace Cstv\Backend\Shared;

final class Uuid
{
    /**
     * F45-R11 : validateur canonique unique pour toutes les routes catalogue — remplace le
     * `preg_match('/^[0-9a-f-]{36}$/i')` de `CatalogAction`, qui acceptait n'importe quelle chaîne
     * de 36 caractères hexadécimaux/tirets (y compris 36 tirets) jusqu'à ce que PostgreSQL la
     * rejette avec un 500 au lieu du 422 contractuel, et le regroupement `8-4-4-4-12` moins strict
     * de `CatalogService::isUuid()`. Vérifie aussi le nibble de version (`4`) et de variant
     * (`8`/`9`/`a`/`b`) — `externalId` n'est jamais qu'un UUID v4 généré par [v4] (§8.2/§7.3).
     */
    public static function isValid(string $value): bool
    {
        return preg_match('/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/iD', $value) === 1;
    }

    public static function v4(): string
    {
        $bytes = random_bytes(16);
        $bytes[6] = chr((ord($bytes[6]) & 0x0f) | 0x40);
        $bytes[8] = chr((ord($bytes[8]) & 0x3f) | 0x80);
        $hex = bin2hex($bytes);

        return sprintf(
            '%s-%s-%s-%s-%s',
            substr($hex, 0, 8),
            substr($hex, 8, 4),
            substr($hex, 12, 4),
            substr($hex, 16, 4),
            substr($hex, 20),
        );
    }
}
