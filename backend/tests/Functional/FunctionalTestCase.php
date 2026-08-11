<?php

declare(strict_types=1);

namespace Cstv\Backend\Tests\Functional;

use Cstv\Backend\Auth\JwtService;
use Cstv\Backend\Database\Connection;
use Cstv\Backend\Shared\Config;
use Cstv\Backend\Shared\Uuid;
use Cstv\Backend\Tests\Functional\Support\ApiClient;
use Cstv\Backend\Tests\Functional\Support\ApiResponse;
use Cstv\Backend\Tests\Support\TestDatabase;
use DateTimeImmutable;
use PDO;
use PHPUnit\Framework\TestCase;

abstract class FunctionalTestCase extends TestCase
{
    protected PDO $pdo;
    protected Config $config;
    protected ApiClient $api;
    protected JwtService $jwt;

    protected function setUp(): void
    {
        parent::setUp();
        $this->config = Config::fromEnvironment();
        $this->pdo = Connection::create($this->config);
        TestDatabase::reset($this->pdo, $this->config);
        $baseUrl = getenv('API_TEST_BASE_URL');
        self::assertIsString($baseUrl, 'API_TEST_BASE_URL must target the dedicated test Nginx service.');
        self::assertNotSame('', trim($baseUrl));
        $this->api = new ApiClient($baseUrl);
        $this->jwt = new JwtService($this->config->jwtSecret, $this->config->jwtTtlSeconds);
    }

    /** @return array{id: string, email: string, profileIds: list<string>, token: string} */
    protected function createAccount(
        ?string $email = null,
        bool $enabled = true,
        bool $active = true,
        int $profileCount = 1,
    ): array {
        $accountId = Uuid::v4();
        $email ??= 'test-' . bin2hex(random_bytes(6)) . '@example.com';
        $statement = $this->pdo->prepare(
            'INSERT INTO accounts (id, email, enabled, active_until) VALUES (:id, :email, :enabled, :active_until)',
        );
        $statement->bindValue(':id', $accountId);
        $statement->bindValue(':email', strtolower($email));
        $statement->bindValue(':enabled', $enabled, PDO::PARAM_BOOL);
        $statement->bindValue(':active_until', (new DateTimeImmutable($active ? '+1 year' : '-1 day'))->format('c'));
        $statement->execute();

        $profileIds = [];
        $insert = $this->pdo->prepare(
            'INSERT INTO profiles (id, account_id, name, avatar_id) VALUES (:id, :account_id, :name, :avatar_id)',
        );
        for ($index = 1; $index <= $profileCount; $index++) {
            $profileId = Uuid::v4();
            $insert->execute([
                'id' => $profileId,
                'account_id' => $accountId,
                'name' => 'Profile ' . $index,
                'avatar_id' => $index - 1,
            ]);
            $profileIds[] = $profileId;
        }

        return [
            'id' => $accountId,
            'email' => strtolower($email),
            'profileIds' => $profileIds,
            'token' => $this->jwt->issue($accountId)['token'],
        ];
    }

    /** @return array<string, string> */
    protected function auth(string $token): array
    {
        return ['Authorization' => 'Bearer ' . $token];
    }

    protected function requestOtp(string $email): ApiResponse
    {
        return $this->api->postJson('/v1/auth/otp/request', ['email' => $email]);
    }

    protected function verifyOtp(string $email, string $code = '123456'): ApiResponse
    {
        return $this->api->postJson('/v1/auth/otp/verify', ['email' => $email, 'code' => $code]);
    }

    protected function putObject(
        string $token,
        string $profileId,
        string $namespace,
        string $payload,
        array $headers = [],
    ): ApiResponse {
        return $this->api->putBinary(
            sprintf('/v1/profiles/%s/objects/%s', $profileId, $namespace),
            $payload,
            array_merge($this->auth($token), [
                'Content-Type' => 'application/vnd.cstv.blob+gzip',
                'X-Schema-Version' => '1',
            ], $headers),
        );
    }

    protected function assertError(ApiResponse $response, int $status, string $code): void
    {
        self::assertSame($status, $response->status);
        $body = $response->json();
        self::assertSame($code, $body['error']['code'] ?? null);
        self::assertIsString($body['error']['message'] ?? null);
        self::assertStringNotContainsStringIgnoringCase('exception', $response->body);
        self::assertStringNotContainsString('/var/www', $response->body);
    }
}
