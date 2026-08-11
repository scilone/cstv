<?php

declare(strict_types=1);

namespace Cstv\Backend\Http\Action;

use Cstv\Backend\Http\Json;
use Cstv\Backend\Sync\SyncService;
use Psr\Http\Message\ResponseInterface;
use Psr\Http\Message\ServerRequestInterface;

final readonly class SyncAction
{
    public function __construct(private SyncService $sync)
    {
    }

    /** @param array<string, string> $args */
    public function __invoke(ServerRequestInterface $request, ResponseInterface $response, array $args): ResponseInterface
    {
        /** @var array<string, mixed> $account */
        $account = $request->getAttribute('account');
        $query = $request->getQueryParams();

        return Json::response($response, $this->sync->changes(
            (string) $account['id'],
            $query['cursor'] ?? null,
            $query['limit'] ?? null,
        ));
    }
}
