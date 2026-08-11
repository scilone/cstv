<?php

declare(strict_types=1);

namespace Cstv\Backend\Tests\Integration;

final class AccountAccessTest extends IntegrationTestCase
{
    public function testActiveAccountCanReadMe(): void
    {
        $account = $this->createAccount('active@example.com');
        $response = $this->request('GET', '/v1/me', '', $this->auth($account['token']));

        self::assertSame(200, $response->getStatusCode());
        self::assertSame('active@example.com', $this->json($response)['email']);
        self::assertCount(1, $this->json($response)['profiles']);
    }

    public function testExpiredAccountIsRejectedFromDatabaseState(): void
    {
        $account = $this->createAccount('expired@example.com', true, false);
        $response = $this->request('GET', '/v1/me', '', $this->auth($account['token']));

        self::assertSame(403, $response->getStatusCode());
        self::assertSame('ACCOUNT_EXPIRED', $this->json($response)['error']['code']);
    }

    public function testDisabledAccountIsRejectedFromDatabaseState(): void
    {
        $account = $this->createAccount('disabled@example.com', false, true);
        $response = $this->request('GET', '/v1/me', '', $this->auth($account['token']));

        self::assertSame(403, $response->getStatusCode());
        self::assertSame('ACCOUNT_DISABLED', $this->json($response)['error']['code']);
    }

    public function testDatabaseChangeTakesEffectWithAlreadyIssuedToken(): void
    {
        $account = $this->createAccount('changed@example.com');
        self::assertSame(200, $this->request('GET', '/v1/me', '', $this->auth($account['token']))->getStatusCode());

        $statement = $this->pdo->prepare('UPDATE accounts SET enabled = FALSE WHERE id = :id');
        $statement->execute(['id' => $account['id']]);

        $response = $this->request('GET', '/v1/me', '', $this->auth($account['token']));
        self::assertSame(403, $response->getStatusCode());
        self::assertSame('ACCOUNT_DISABLED', $this->json($response)['error']['code']);
    }
}
