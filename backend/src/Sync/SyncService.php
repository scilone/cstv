<?php

declare(strict_types=1);

namespace Cstv\Backend\Sync;

use Cstv\Backend\Shared\ApiException;
use Cstv\Backend\Shared\DateFormatter;

final readonly class SyncService
{
    public function __construct(private SyncRepository $changes, private int $maximumLimit)
    {
    }

    /** @return array{changes: list<array<string, mixed>>, nextCursor: int, hasMore: bool} */
    public function changes(string $accountId, mixed $rawCursor, mixed $rawLimit): array
    {
        $cursor = $this->integer($rawCursor, 0, 0, PHP_INT_MAX, 'cursor');
        $limit = $this->integer($rawLimit, 100, 1, $this->maximumLimit, 'limit');
        $rows = $this->changes->changes($accountId, $cursor, $limit + 1);
        $hasMore = count($rows) > $limit;
        if ($hasMore) {
            array_pop($rows);
        }

        $formatted = array_map(static fn (array $change): array => [
            'revision' => (int) $change['revision'],
            'profileId' => (string) $change['profile_id'],
            'namespace' => (string) $change['namespace'],
            'key' => (string) $change['object_key'],
            'operation' => (string) $change['operation'],
            'etag' => $change['etag'] === null ? null : (string) $change['etag'],
            'changedAt' => DateFormatter::iso8601((string) $change['changed_at']),
        ], $rows);

        $last = $formatted === [] ? $cursor : (int) $formatted[array_key_last($formatted)]['revision'];
        return ['changes' => $formatted, 'nextCursor' => $last, 'hasMore' => $hasMore];
    }

    private function integer(mixed $value, int $default, int $minimum, int $maximum, string $name): int
    {
        if ($value === null || $value === '') {
            return $default;
        }
        if (!is_string($value) || !preg_match('/^\d+$/', $value)) {
            throw new ApiException(422, 'INVALID_QUERY_PARAMETER', sprintf('%s must be an integer.', $name));
        }

        $integer = filter_var($value, FILTER_VALIDATE_INT);
        if ($integer === false || $integer < $minimum || $integer > $maximum) {
            throw new ApiException(
                422,
                'INVALID_QUERY_PARAMETER',
                sprintf('%s must be between %d and %d.', $name, $minimum, $maximum),
            );
        }

        return $integer;
    }
}
