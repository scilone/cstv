<?php

declare(strict_types=1);

namespace Cstv\Backend\Tests\Functional;

final class SecurityHeadersApiTest extends FunctionalTestCase
{
    public function testJsonSuccessResponseCarriesBaselineSecurityHeaders(): void
    {
        $account = $this->createAccount();
        $response = $this->api->get('/v1/me', $this->auth($account['token']));

        self::assertSame(200, $response->status);
        self::assertSame('max-age=31536000; includeSubDomains', $response->header('strict-transport-security'));
        self::assertSame('nosniff', $response->header('x-content-type-options'));
        self::assertSame('no-referrer', $response->header('referrer-policy'));
        self::assertSame('no-store', $response->header('cache-control'));
    }

    public function testBlobResponseCarriesSecurityHeadersAndContentDisposition(): void
    {
        $account = $this->createAccount();
        $profileId = $account['profileIds'][0];
        $this->putObject($account['token'], $profileId, 'favorites', (string) gzencode('x'));

        $response = $this->api->get(
            '/v1/profiles/' . $profileId . '/objects/favorites', $this->auth($account['token']),
        );

        self::assertSame(200, $response->status);
        self::assertSame('attachment', $response->header('content-disposition'));
        self::assertSame('max-age=31536000; includeSubDomains', $response->header('strict-transport-security'));
        self::assertSame('nosniff', $response->header('x-content-type-options'));
        self::assertSame('no-referrer', $response->header('referrer-policy'));
        self::assertSame('no-store', $response->header('cache-control'));
    }

    public function testErrorResponseCarriesTheSameSecurityHeadersAsSuccess(): void
    {
        // No Authorization header: AuthMiddleware throws before the route runs, and the response
        // is built by ApiErrorHandler. Proves SecurityHeadersMiddleware wraps error middleware too.
        $response = $this->api->get('/v1/me');

        self::assertSame(401, $response->status);
        self::assertSame('max-age=31536000; includeSubDomains', $response->header('strict-transport-security'));
        self::assertSame('nosniff', $response->header('x-content-type-options'));
        self::assertSame('no-referrer', $response->header('referrer-policy'));
        self::assertSame('no-store', $response->header('cache-control'));
    }

    public function testHealthResponseCarriesSecurityHeadersButNoForcedNoStore(): void
    {
        // /health is outside /v1: the app-wide headers still apply, but the /v1-only
        // Cache-Control: no-store rule is scoped to the authenticated sync/profile surface.
        $response = $this->api->get('/health');

        self::assertSame(200, $response->status);
        self::assertSame('max-age=31536000; includeSubDomains', $response->header('strict-transport-security'));
        self::assertSame('nosniff', $response->header('x-content-type-options'));
        self::assertSame('no-referrer', $response->header('referrer-policy'));
        self::assertSame('', $response->header('cache-control'));
    }
}
