<?php

declare(strict_types=1);

namespace Cstv\Backend\Http\Action;

use Cstv\Backend\Account\IptvCredentialsService;
use Cstv\Backend\Http\Json;
use Cstv\Backend\Shared\ApiException;
use Cstv\Backend\Shared\Validator;
use Psr\Http\Message\ResponseInterface;
use Psr\Http\Message\ServerRequestInterface;

final readonly class IptvCredentialsAction
{
    public function __construct(private IptvCredentialsService $service, private int $maximumBytes) {}

    public function get(ServerRequestInterface $request, ResponseInterface $response): ResponseInterface
    {
        $result = $this->service->get($this->accountId($request));
        return Json::response($response, $result['credentials'] + ['updatedAt' => $result['updatedAt']])
            ->withHeader('ETag', '"' . $result['etag'] . '"')->withHeader('Cache-Control', 'no-store');
    }

    public function put(ServerRequestInterface $request, ResponseInterface $response): ResponseInterface
    {
        $contentType = strtolower(trim(explode(';', $request->getHeaderLine('Content-Type'), 2)[0]));
        if ($contentType !== 'application/json') throw new ApiException(415, 'UNSUPPORTED_MEDIA_TYPE', 'Content-Type must be application/json.');
        $contentLength = $request->getHeaderLine('Content-Length');
        if ($contentLength !== '' && !ctype_digit($contentLength)) throw new ApiException(400, 'INVALID_CONTENT_LENGTH', 'Content-Length must be numeric.');
        if ($contentLength !== '' && (int) $contentLength > $this->maximumBytes) throw new ApiException(413, 'PAYLOAD_TOO_LARGE', 'IPTV credentials payload is too large.');
        $raw = (string) $request->getBody();
        if (strlen($raw) > $this->maximumBytes) throw new ApiException(413, 'PAYLOAD_TOO_LARGE', 'IPTV credentials payload is too large.');
        $etag = $this->service->put($this->accountId($request), Validator::iptvCredentials(Json::body($request)), $request->hasHeader('If-Match') ? $request->getHeaderLine('If-Match') : null);
        return $response->withStatus(204)->withHeader('ETag', '"' . $etag . '"')->withHeader('Cache-Control', 'no-store');
    }

    public function delete(ServerRequestInterface $request, ResponseInterface $response): ResponseInterface
    {
        $this->service->delete($this->accountId($request), $request->hasHeader('If-Match') ? $request->getHeaderLine('If-Match') : null);
        return $response->withStatus(204)->withHeader('Cache-Control', 'no-store');
    }

    private function accountId(ServerRequestInterface $request): string
    {
        /** @var array<string, mixed> $account */
        $account = $request->getAttribute('account');
        return (string) $account['id'];
    }
}
