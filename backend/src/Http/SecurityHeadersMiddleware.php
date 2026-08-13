<?php

declare(strict_types=1);

namespace Cstv\Backend\Http;

use Psr\Http\Message\ResponseInterface;
use Psr\Http\Message\ServerRequestInterface;
use Psr\Http\Server\MiddlewareInterface;
use Psr\Http\Server\RequestHandlerInterface;

/**
 * Applies baseline security headers to every response, success or error alike. Wired last in
 * Bootstrap::createApp() so it becomes the outermost middleware and wraps Slim's error middleware
 * too: a response built by ApiErrorHandler (401, 404, 429, 500, ...) still passes back through
 * here on its way out and gets the same headers as a 200.
 *
 * Strict-Transport-Security is emitted unconditionally rather than gated on a detected request
 * scheme: per RFC 6797 a browser only honours the header when the response was actually delivered
 * over TLS, so sending it always is harmless and avoids depending on a forwarded-proto header the
 * alwaysdata frontal may or may not set reliably.
 */
final readonly class SecurityHeadersMiddleware implements MiddlewareInterface
{
    private const HSTS = 'max-age=31536000; includeSubDomains';

    public function process(ServerRequestInterface $request, RequestHandlerInterface $handler): ResponseInterface
    {
        $response = $handler->handle($request)
            ->withHeader('Strict-Transport-Security', self::HSTS)
            ->withHeader('X-Content-Type-Options', 'nosniff')
            ->withHeader('Referrer-Policy', 'no-referrer');

        $path = $request->getUri()->getPath();
        if ($path === '/v1' || str_starts_with($path, '/v1/')) {
            $response = $response->withHeader('Cache-Control', 'no-store');
        }

        return $response;
    }
}
