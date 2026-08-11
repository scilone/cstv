<?php

declare(strict_types=1);

namespace Cstv\Backend\Tests\Functional\Support;

use RuntimeException;

final readonly class ApiClient
{
    public function __construct(private string $baseUrl)
    {
    }

    /** @param array<string, string> $headers */
    public function get(string $path, array $headers = []): ApiResponse
    {
        return $this->request('GET', $path, '', $headers);
    }

    /** @param array<string, mixed> $body @param array<string, string> $headers */
    public function postJson(string $path, array $body, array $headers = []): ApiResponse
    {
        return $this->jsonRequest('POST', $path, $body, $headers);
    }

    /** @param array<string, mixed> $body @param array<string, string> $headers */
    public function patchJson(string $path, array $body, array $headers = []): ApiResponse
    {
        return $this->jsonRequest('PATCH', $path, $body, $headers);
    }

    /** @param array<string, mixed> $body @param array<string, string> $headers */
    public function putJson(string $path, array $body, array $headers = []): ApiResponse
    {
        return $this->jsonRequest('PUT', $path, $body, $headers);
    }

    /** @param array<string, string> $headers */
    public function putBinary(string $path, string $body, array $headers = []): ApiResponse
    {
        return $this->request('PUT', $path, $body, $headers);
    }

    /** @param array<string, string> $headers */
    public function delete(string $path, array $headers = []): ApiResponse
    {
        return $this->request('DELETE', $path, '', $headers);
    }

    /** @param array<string, mixed> $body @param array<string, string> $headers */
    private function jsonRequest(string $method, string $path, array $body, array $headers): ApiResponse
    {
        $headers['Content-Type'] = 'application/json';
        return $this->request($method, $path, json_encode($body, JSON_THROW_ON_ERROR), $headers);
    }

    /** @param array<string, string> $headers */
    public function request(string $method, string $path, string $body = '', array $headers = []): ApiResponse
    {
        $responseHeaders = [];
        $handle = curl_init(rtrim($this->baseUrl, '/') . '/' . ltrim($path, '/'));
        if ($handle === false) {
            throw new RuntimeException('Unable to initialize cURL.');
        }

        $formattedHeaders = [];
        foreach ($headers as $name => $value) {
            $formattedHeaders[] = $name . ': ' . $value;
        }

        curl_setopt_array($handle, [
            CURLOPT_CUSTOMREQUEST => $method,
            CURLOPT_POSTFIELDS => $body,
            CURLOPT_HTTPHEADER => $formattedHeaders,
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_TIMEOUT => 20,
            CURLOPT_HEADERFUNCTION => static function ($curl, string $line) use (&$responseHeaders): int {
                $length = strlen($line);
                $separator = strpos($line, ':');
                if ($separator !== false) {
                    $name = strtolower(trim(substr($line, 0, $separator)));
                    $responseHeaders[$name][] = trim(substr($line, $separator + 1));
                }
                return $length;
            },
        ]);

        $responseBody = curl_exec($handle);
        if ($responseBody === false) {
            $message = curl_error($handle);
            throw new RuntimeException('HTTP test request failed: ' . $message);
        }
        $status = (int) curl_getinfo($handle, CURLINFO_RESPONSE_CODE);

        return new ApiResponse($status, $responseHeaders, (string) $responseBody);
    }
}
