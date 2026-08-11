<?php

declare(strict_types=1);

namespace Cstv\Backend\Tests\Integration;

use Cstv\Backend\Auth\JwtService;
use Cstv\Backend\Shared\Uuid;
use Firebase\JWT\JWT;

final class TokenTest extends IntegrationTestCase
{
    public function testMissingAuthorizationHeaderIsRejected(): void
    {
        $response = $this->request('GET', '/v1/me');

        self::assertSame(401, $response->getStatusCode());
        self::assertSame('AUTHENTICATION_REQUIRED', $this->json($response)['error']['code']);
    }

    /** @return list<array{0: string, 1: string}> */
    public static function invalidTokenProvider(): array
    {
        return [
            'not a jwt' => ['Bearer not-a-token', 'INVALID_TOKEN'],
            'empty bearer' => ['Bearer ', 'AUTHENTICATION_REQUIRED'],
            'wrong scheme' => ['Basic abcdef', 'AUTHENTICATION_REQUIRED'],
            'truncated jwt' => ['Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ4In0', 'INVALID_TOKEN'],
        ];
    }

    #[\PHPUnit\Framework\Attributes\DataProvider('invalidTokenProvider')]
    public function testMalformedTokensAreRejectedUniformly(string $header, string $expectedCode): void
    {
        $response = $this->request('GET', '/v1/me', '', ['Authorization' => $header]);

        self::assertSame(401, $response->getStatusCode());
        self::assertSame($expectedCode, $this->json($response)['error']['code']);
    }

    public function testAlgorithmNoneIsRejected(): void
    {
        $encode = static fn (array $part): string => rtrim(strtr(
            base64_encode(json_encode($part, JSON_THROW_ON_ERROR)),
            '+/',
            '-_',
        ), '=');
        $account = $this->createAccount('alg-none@example.com');
        $forged = $encode(['alg' => 'none', 'typ' => 'JWT'])
            . '.' . $encode(['sub' => $account['id'], 'iat' => time(), 'exp' => time() + 3600])
            . '.';

        $response = $this->request('GET', '/v1/me', '', $this->auth($forged));

        self::assertSame(401, $response->getStatusCode());
        self::assertSame('INVALID_TOKEN', $this->json($response)['error']['code']);
    }

    public function testTokenSignedWithAnotherSecretIsRejected(): void
    {
        $account = $this->createAccount('other-secret@example.com');
        $foreign = new JwtService(str_repeat('z', 48), 3600);

        $response = $this->request('GET', '/v1/me', '', $this->auth($foreign->issue($account['id'])['token']));

        self::assertSame(401, $response->getStatusCode());
        self::assertSame('INVALID_TOKEN', $this->json($response)['error']['code']);
    }

    public function testExpiredTokenIsRejected(): void
    {
        $account = $this->createAccount('expired-token@example.com');
        $expired = new JwtService($this->config->jwtSecret, -60);

        $response = $this->request('GET', '/v1/me', '', $this->auth($expired->issue($account['id'])['token']));

        self::assertSame(401, $response->getStatusCode());
        self::assertSame('INVALID_TOKEN', $this->json($response)['error']['code']);
    }

    public function testSubjectMustBeAUuid(): void
    {
        $this->createAccount('bad-sub@example.com');
        $token = JWT::encode(
            ['sub' => "1 OR 1=1", 'iat' => time(), 'exp' => time() + 3600],
            $this->config->jwtSecret,
            'HS256',
        );

        $response = $this->request('GET', '/v1/me', '', $this->auth($token));

        self::assertSame(401, $response->getStatusCode());
        self::assertSame('INVALID_TOKEN', $this->json($response)['error']['code']);
    }

    public function testUnknownAccountIsRejectedEvenWithAValidSignature(): void
    {
        $token = JWT::encode(
            ['sub' => Uuid::v4(), 'iat' => time(), 'exp' => time() + 3600],
            $this->config->jwtSecret,
            'HS256',
        );

        $response = $this->request('GET', '/v1/me', '', $this->auth($token));

        self::assertSame(401, $response->getStatusCode());
        self::assertSame('INVALID_TOKEN', $this->json($response)['error']['code']);
    }
}
