<?php

declare(strict_types=1);

namespace Cstv\Backend\Tests\Functional\Support;

use JsonException;

final readonly class ApiResponse
{
    /** @param array<string, list<string>> $headers */
    public function __construct(
        public int $status,
        public array $headers,
        public string $body,
    ) {
    }

    public function header(string $name): string
    {
        $values = $this->headers[strtolower($name)] ?? [];
        return $values === [] ? '' : $values[array_key_last($values)];
    }

    /** @return array<string, mixed> */
    public function json(): array
    {
        /** @var array<string, mixed> $decoded */
        $decoded = json_decode($this->body, true, 512, JSON_THROW_ON_ERROR);
        return $decoded;
    }
}
