<?php

declare(strict_types=1);

namespace Cstv\Backend\Catalog;

final readonly class TmdbClient
{
    /** @param null|\Closure(string, array<string, scalar>): array<string, mixed> $transport */
    public function __construct(private string $token, private ?\Closure $transport = null) {}

    /** @return array<string, mixed> */
    public function get(string $path, array $query = []): array
    {
        if ($this->transport !== null) return ($this->transport)($path, $query);
        $url = 'https://api.themoviedb.org/3/' . ltrim($path, '/');
        if ($query !== []) $url .= '?' . http_build_query($query, '', '&', PHP_QUERY_RFC3986);
        $lastStatus = 503;
        $lastRetryAfter = null;
        for ($attempt = 0; $attempt < 2; $attempt++) {
            $curl = curl_init($url);
            // F45-R8 : capture les en-têtes pour lire un éventuel `Retry-After` sur 429 — avant ce
            // correctif, le client retentait après un délai fixe de 100-250ms sans jamais consulter
            // ce que TMDB demandait réellement.
            $headerLines = [];
            curl_setopt_array($curl, [
                CURLOPT_RETURNTRANSFER => true, CURLOPT_CONNECTTIMEOUT => 3, CURLOPT_TIMEOUT => 8,
                CURLOPT_HTTPHEADER => ['Accept: application/json', 'Authorization: Bearer ' . $this->token],
                CURLOPT_FAILONERROR => false,
                CURLOPT_HEADERFUNCTION => static function ($ch, string $header) use (&$headerLines): int {
                    $headerLines[] = $header;
                    return strlen($header);
                },
            ]);
            $body = curl_exec($curl);
            $status = (int) curl_getinfo($curl, CURLINFO_RESPONSE_CODE);
            $error = curl_errno($curl);
            curl_close($curl);
            $lastStatus = $status >= 500 || $status === 429 || $error !== 0 ? 503 : 502;
            if ($error === 0 && $status >= 200 && $status < 300 && is_string($body)) {
                try {
                    $decoded = json_decode($body, true, 512, JSON_THROW_ON_ERROR);
                    if (is_array($decoded)) return $decoded;
                } catch (\JsonException) {
                    throw new CatalogProviderException(502, 'Catalog provider returned invalid JSON.');
                }
                throw new CatalogProviderException(502, 'Catalog provider returned an invalid response.');
            }
            if ($status === 429) $lastRetryAfter = self::parseRetryAfter($headerLines);
            if (!($error !== 0 || $status === 429 || $status >= 500) || $attempt === 1) break;
            // `Retry-After` prime toujours (§8.16) ; borné à 5s pour ne jamais geler un worker
            // PHP-FPM sur une valeur agressive — au-delà, on abandonne cette tentative et laisse le
            // délai réel remonter via `CatalogProviderException::$retryAfterSeconds` au lieu d'attendre.
            if ($lastRetryAfter !== null && $lastRetryAfter > 5) break;
            usleep($lastRetryAfter !== null ? $lastRetryAfter * 1_000_000 : random_int(100_000, 250_000));
        }
        throw new CatalogProviderException($lastStatus, retryAfterSeconds: $lastRetryAfter);
    }

    /** Pure header-parsing, no I/O — kept `public static` so it stays directly unit-testable without curl/a live server. @param list<string> $headerLines raw CURLOPT_HEADERFUNCTION lines, one per header */
    public static function parseRetryAfter(array $headerLines): ?int
    {
        foreach ($headerLines as $line) {
            if (preg_match('/^retry-after:\s*(.+?)\s*$/i', $line, $match) !== 1) continue;
            $value = $match[1];
            if (ctype_digit($value)) return max(1, (int) $value);
            $timestamp = strtotime($value);
            if ($timestamp !== false) return max(1, $timestamp - time());
        }
        return null;
    }
}
