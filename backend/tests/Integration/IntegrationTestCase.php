<?php

declare(strict_types=1);

namespace Cstv\Backend\Tests\Integration;

use Cstv\Backend\Auth\JwtService;
use Cstv\Backend\Bootstrap;
use Cstv\Backend\Database\Connection;
use Cstv\Backend\Database\Migrator;
use Cstv\Backend\Shared\Config;
use Cstv\Backend\Shared\Uuid;
use Cstv\Backend\Tests\Support\TestDatabase;
use DateTimeImmutable;
use PDO;
use PHPUnit\Framework\TestCase;
use Psr\Http\Message\ResponseInterface;
use Slim\App;
use Slim\Psr7\Factory\ServerRequestFactory;
use Slim\Psr7\Factory\StreamFactory;

abstract class IntegrationTestCase extends TestCase
{
    protected PDO $pdo;
    protected Config $config;
    protected App $app;
    protected JwtService $jwt;

    protected function setUp(): void
    {
        parent::setUp();
        $this->config = Config::fromEnvironment();
        $this->pdo = Connection::create($this->config);
        (new Migrator($this->pdo, dirname(__DIR__, 2) . '/migrations'))->migrate();
        TestDatabase::reset($this->pdo, $this->config);
        $this->app = Bootstrap::createApp($this->config, $this->pdo);
        $this->jwt = new JwtService($this->config->jwtSecret, $this->config->jwtTtlSeconds);
    }

    /** @param array<string, string> $headers */
    protected function request(string $method, string $uri, string $body = '', array $headers = []): ResponseInterface
    {
        $request = (new ServerRequestFactory())->createServerRequest($method, $uri, [
            'REMOTE_ADDR' => '127.0.0.1',
        ])->withBody((new StreamFactory())->createStream($body));

        foreach ($headers as $name => $value) {
            $request = $request->withHeader($name, $value);
        }

        return $this->app->handle($request);
    }

    /** @param array<string, mixed> $body @param array<string, string> $headers */
    protected function jsonRequest(string $method, string $uri, array $body, array $headers = []): ResponseInterface
    {
        $headers['Content-Type'] = 'application/json';
        return $this->request($method, $uri, json_encode($body, JSON_THROW_ON_ERROR), $headers);
    }

    /** @return array<string, mixed> */
    protected function json(ResponseInterface $response): array
    {
        /** @var array<string, mixed> $decoded */
        $decoded = json_decode((string) $response->getBody(), true, 512, JSON_THROW_ON_ERROR);
        return $decoded;
    }

    /** @return array{id: string, profileIds: list<string>, token: string} */
    protected function createAccount(
        string $email = 'user@example.com',
        bool $enabled = true,
        bool $active = true,
        int $profileCount = 1,
    ): array {
        $accountId = Uuid::v4();
        $activeUntil = (new DateTimeImmutable($active ? '+1 year' : '-1 day'))->format('c');
        $statement = $this->pdo->prepare(
            'INSERT INTO accounts (id, email, enabled, active_until, created_at, updated_at) '
            . 'VALUES (:id, :email, :enabled, :active_until, NOW(), NOW())',
        );
        $statement->bindValue(':id', $accountId);
        $statement->bindValue(':email', strtolower($email));
        $statement->bindValue(':enabled', $enabled, PDO::PARAM_BOOL);
        $statement->bindValue(':active_until', $activeUntil);
        $statement->execute();

        $profileIds = [];
        $profile = $this->pdo->prepare(
            'INSERT INTO profiles (id, account_id, name, avatar_id, created_at, updated_at) '
            . 'VALUES (:id, :account_id, :name, 0, NOW(), NOW())',
        );
        for ($index = 1; $index <= $profileCount; $index++) {
            $profileId = Uuid::v4();
            $profile->execute([
                'id' => $profileId,
                'account_id' => $accountId,
                'name' => 'Profile ' . $index,
            ]);
            $profileIds[] = $profileId;
        }

        return [
            'id' => $accountId,
            'profileIds' => $profileIds,
            'token' => $this->jwt->issue($accountId)['token'],
        ];
    }

    /** @return array<string, string> */
    protected function auth(string $token): array
    {
        return ['Authorization' => 'Bearer ' . $token];
    }

    protected function requestOtp(string $email): ResponseInterface
    {
        return $this->jsonRequest('POST', '/v1/auth/otp/request', ['email' => $email]);
    }

    protected function verifyOtp(string $email, string $code = '123456'): ResponseInterface
    {
        return $this->jsonRequest('POST', '/v1/auth/otp/verify', ['email' => $email, 'code' => $code]);
    }

    /**
     * Base URL of the running Docker stack. Concurrency can only be observed with real parallel
     * requests, so the tests that need it are skipped when the stack is not reachable.
     */
    protected function e2eBaseUrl(): string
    {
        $base = getenv('E2E_BASE_URL');
        if ($base === false || trim($base) === '') {
            self::markTestSkipped('E2E_BASE_URL is not configured.');
        }

        return rtrim(trim($base), '/');
    }

    /**
     * Fires every request at the same time through curl_multi and returns them in input order.
     *
     * @param list<array{method: string, path: string, body?: string, headers?: array<string, string>}> $requests
     * @return list<array{status: int, body: string}>
     */
    protected function parallelRequests(array $requests): array
    {
        $base = $this->e2eBaseUrl();
        $multi = curl_multi_init();
        $handles = [];

        foreach ($requests as $index => $request) {
            $headers = [];
            foreach ($request['headers'] ?? [] as $name => $value) {
                $headers[] = $name . ': ' . $value;
            }

            $handle = curl_init($base . $request['path']);
            curl_setopt_array($handle, [
                CURLOPT_CUSTOMREQUEST => $request['method'],
                CURLOPT_POSTFIELDS => $request['body'] ?? '',
                CURLOPT_HTTPHEADER => $headers,
                CURLOPT_RETURNTRANSFER => true,
                CURLOPT_TIMEOUT => 20,
            ]);
            curl_multi_add_handle($multi, $handle);
            $handles[$index] = $handle;
        }

        do {
            $status = curl_multi_exec($multi, $running);
            if ($running > 0) {
                curl_multi_select($multi, 1.0);
            }
        } while ($running > 0 && $status === CURLM_OK);

        $results = [];
        foreach ($handles as $index => $handle) {
            $results[$index] = [
                'status' => (int) curl_getinfo($handle, CURLINFO_RESPONSE_CODE),
                'body' => (string) curl_multi_getcontent($handle),
            ];
            curl_multi_remove_handle($multi, $handle);
        }
        return $results;
    }

    /** @param list<array{status: int, body: string}> $results @return list<int> */
    protected function statuses(array $results): array
    {
        $statuses = array_map(static fn (array $result): int => $result['status'], $results);
        sort($statuses);

        return $statuses;
    }

    /** @param array<string, string> $extraHeaders */
    protected function putObject(
        string $token,
        string $profileId,
        string $namespace,
        string $key,
        string $payload,
        array $extraHeaders = [],
    ): ResponseInterface {
        return $this->request(
            'PUT',
            sprintf('/v1/profiles/%s/objects/%s/%s', $profileId, $namespace, $key),
            $payload,
            array_merge($this->auth($token), [
                'Content-Type' => 'application/vnd.cstv.blob+gzip',
                'X-Schema-Version' => '1',
            ], $extraHeaders),
        );
    }
}
