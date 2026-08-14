<?php

declare(strict_types=1);

namespace Cstv\Backend\Http\Action;

use Cstv\Backend\Http\Json;
use Cstv\Backend\Playback\PlaybackLockService;
use Cstv\Backend\Shared\ApiException;
use Cstv\Backend\Shared\Validator;
use Psr\Http\Message\ResponseInterface;
use Psr\Http\Message\ServerRequestInterface;

final readonly class PlaybackLockAction
{
    public function __construct(private PlaybackLockService $service) {}

    public function acquire(ServerRequestInterface $request, ResponseInterface $response): ResponseInterface
    {
        $this->requireJson($request);
        $result = $this->service->acquire($this->accountId($request), Validator::playbackLockDevice(Json::body($request)));
        if ($result['kind'] === 'held') return Json::response($response, ['code' => 'PLAYBACK_LOCK_HELD', 'message' => 'A playback lock is held by another device.', 'holder' => $result['holder']])->withStatus(409)->withHeader('Cache-Control', 'no-store');
        return Json::response($response, $result['lock'])->withHeader('Cache-Control', 'no-store');
    }

    public function heartbeat(ServerRequestInterface $request, ResponseInterface $response): ResponseInterface
    {
        $result = $this->service->heartbeat($this->accountId($request), $this->ifMatch($request));
        if ($result['kind'] === 'revoked') return Json::response($response, ['code' => 'PLAYBACK_LOCK_REVOKED', 'message' => 'The playback lock is no longer owned by this device.', 'holder' => $result['holder']])->withStatus(409)->withHeader('Cache-Control', 'no-store');
        return Json::response($response, $result['lock'])->withHeader('Cache-Control', 'no-store');
    }

    public function release(ServerRequestInterface $request, ResponseInterface $response): ResponseInterface
    {
        $this->service->release($this->accountId($request), $this->ifMatch($request));
        return $response->withStatus(204)->withHeader('Cache-Control', 'no-store');
    }

    private function requireJson(ServerRequestInterface $request): void
    {
        if (strtolower(trim(explode(';', $request->getHeaderLine('Content-Type'), 2)[0])) !== 'application/json') throw new ApiException(415, 'UNSUPPORTED_MEDIA_TYPE', 'Content-Type must be application/json.');
    }

    private function ifMatch(ServerRequestInterface $request): ?string
    {
        if (!$request->hasHeader('If-Match')) return null;
        return trim($request->getHeaderLine('If-Match'), " \t\"");
    }

    private function accountId(ServerRequestInterface $request): string
    {
        /** @var array<string, mixed> $account */
        $account = $request->getAttribute('account');
        return (string) $account['id'];
    }
}
