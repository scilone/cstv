<?php

declare(strict_types=1);

namespace Cstv\Backend\Tests\Functional;

final class IptvCredentialsApiTest extends FunctionalTestCase
{
    private const PATH = '/v1/account/iptv-credentials';

    /** @return array{host: string, port: int, username: string, password: string} */
    private function credentials(): array
    {
        return ['host' => 'https://panel.example', 'port' => 8080, 'username' => 'alice', 'password' => 'top-secret'];
    }

    public function testAuthenticatedRoundTripHasNoStoreAndEtag(): void
    {
        $account = $this->createAccount();
        $put = $this->api->putJson(self::PATH, $this->credentials(), $this->auth($account['token']));
        self::assertSame(204, $put->status);
        self::assertMatchesRegularExpression('/^"[a-f0-9]{64}"$/', $put->header('etag'));
        self::assertSame('no-store', $put->header('cache-control'));

        $get = $this->api->get(self::PATH, $this->auth($account['token']));
        self::assertSame(200, $get->status);
        self::assertSame('no-store', $get->header('cache-control'));
        self::assertSame($put->header('etag'), $get->header('etag'));
        self::assertSame($this->credentials(), array_diff_key($get->json(), ['updatedAt' => true]));
    }

    public function testValidationAndHeadersNeverReflectASecret(): void
    {
        $account = $this->createAccount();
        $secret = 'do-not-reflect-this-secret';
        $invalid = $this->api->putJson(self::PATH, [
            'host' => 'https://panel.example', 'port' => 8080, 'username' => 'alice', 'password' => $secret . "\n",
        ], $this->auth($account['token']));
        $this->assertError($invalid, 422, 'INVALID_IPTV_CREDENTIALS');
        self::assertStringNotContainsString($secret, $invalid->body);

        $wrongType = $this->api->putBinary(self::PATH, '{}', $this->auth($account['token']) + ['Content-Type' => 'text/plain']);
        $this->assertError($wrongType, 415, 'UNSUPPORTED_MEDIA_TYPE');

        $tooLarge = $this->api->putBinary(
            self::PATH,
            str_repeat('a', $this->config->maxIptvCredentialsBytes + 1),
            $this->auth($account['token']) + ['Content-Type' => 'application/json'],
        );
        $this->assertError($tooLarge, 413, 'PAYLOAD_TOO_LARGE');
    }

    public function testMissingCredentialsAndUnauthenticatedCallsHaveTheContractStatus(): void
    {
        $account = $this->createAccount();
        $this->assertError($this->api->get(self::PATH, $this->auth($account['token'])), 404, 'IPTV_CREDENTIALS_NOT_FOUND');
        $this->assertError($this->api->get(self::PATH), 401, 'AUTHENTICATION_REQUIRED');
    }
}
