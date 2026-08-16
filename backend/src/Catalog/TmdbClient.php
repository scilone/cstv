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
        for ($attempt = 0; $attempt < 2; $attempt++) {
            $curl = curl_init($url);
            curl_setopt_array($curl, [CURLOPT_RETURNTRANSFER => true, CURLOPT_CONNECTTIMEOUT => 3, CURLOPT_TIMEOUT => 8, CURLOPT_HTTPHEADER => ['Accept: application/json', 'Authorization: Bearer ' . $this->token], CURLOPT_FAILONERROR => false]);
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
            if (!($error !== 0 || $status === 429 || $status >= 500) || $attempt === 1) break;
            usleep(random_int(100_000, 250_000));
        }
        throw new CatalogProviderException($lastStatus);
    }
}
