<?php

declare(strict_types=1);

namespace Cstv\Backend\Http\Action;

use Cstv\Backend\Auth\AuthService;
use Cstv\Backend\Http\Json;
use Psr\Http\Message\ResponseInterface;
use Psr\Http\Message\ServerRequestInterface;

final readonly class AuthAction
{
    public function __construct(private AuthService $auth)
    {
    }

    /** @param array<string, string> $args */
    public function request(ServerRequestInterface $request, ResponseInterface $response, array $args): ResponseInterface
    {
        $body = Json::body($request);
        $ip = (string) ($request->getServerParams()['REMOTE_ADDR'] ?? '0.0.0.0');
        $this->auth->request($body['email'] ?? null, $ip);

        return Json::response($response, ['status' => 'accepted'], 202);
    }

    /** @param array<string, string> $args */
    public function verify(ServerRequestInterface $request, ResponseInterface $response, array $args): ResponseInterface
    {
        $body = Json::body($request);
        return Json::response($response, $this->auth->verify($body['email'] ?? null, $body['code'] ?? null));
    }
}
