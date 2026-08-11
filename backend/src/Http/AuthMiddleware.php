<?php

declare(strict_types=1);

namespace Cstv\Backend\Http;

use Cstv\Backend\Account\AccountRepository;
use Cstv\Backend\Auth\JwtService;
use Cstv\Backend\Shared\ApiException;
use Psr\Http\Message\ResponseInterface;
use Psr\Http\Message\ServerRequestInterface;
use Psr\Http\Server\MiddlewareInterface;
use Psr\Http\Server\RequestHandlerInterface;

final readonly class AuthMiddleware implements MiddlewareInterface
{
    public function __construct(private JwtService $jwt, private AccountRepository $accounts)
    {
    }

    public function process(ServerRequestInterface $request, RequestHandlerInterface $handler): ResponseInterface
    {
        $authorization = $request->getHeaderLine('Authorization');
        if (!preg_match('/^Bearer\s+([^\s]+)$/i', $authorization, $matches)) {
            throw new ApiException(401, 'AUTHENTICATION_REQUIRED', 'A bearer access token is required.');
        }

        $accountId = $this->jwt->accountId($matches[1]);
        $account = $this->accounts->findById($accountId);
        if ($account === null) {
            throw new ApiException(401, 'INVALID_TOKEN', 'The access token is invalid or expired.');
        }
        if ($account['enabled'] !== true) {
            throw new ApiException(403, 'ACCOUNT_DISABLED', 'Account is disabled.');
        }
        if ($account['not_expired'] !== true) {
            throw new ApiException(403, 'ACCOUNT_EXPIRED', 'Account activation has expired.');
        }

        return $handler->handle($request->withAttribute('account', $account));
    }
}
