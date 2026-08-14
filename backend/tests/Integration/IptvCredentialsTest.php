<?php

declare(strict_types=1);

namespace Cstv\Backend\Tests\Integration;

final class IptvCredentialsTest extends IntegrationTestCase
{
    private const PATH = '/v1/account/iptv-credentials';

    /** @return array{host: string, port: int, username: string, password: string} */
    private function credentials(string $suffix = 'a'): array
    {
        return [
            'host' => 'https://panel-' . $suffix . '.example',
            'port' => 8080,
            'username' => 'user-' . $suffix,
            'password' => 'secret-' . $suffix,
        ];
    }

    public function testPutGetOverwriteAndConditionalDeleteKeepOnlyCiphertext(): void
    {
        $account = $this->createAccount();
        $first = $this->credentials('first');
        $put = $this->jsonRequest('PUT', self::PATH, $first, $this->auth($account['token']));
        self::assertSame(204, $put->getStatusCode());
        $etag = $put->getHeaderLine('ETag');
        self::assertMatchesRegularExpression('/^"[a-f0-9]{64}"$/', $etag);

        $get = $this->request('GET', self::PATH, '', $this->auth($account['token']));
        self::assertSame(200, $get->getStatusCode());
        self::assertSame('no-store', $get->getHeaderLine('Cache-Control'));
        self::assertSame($etag, $get->getHeaderLine('ETag'));
        self::assertSame($first, array_diff_key($this->json($get), ['updatedAt' => true]));

        $second = $this->credentials('second');
        $overwrite = $this->jsonRequest('PUT', self::PATH, $second, $this->auth($account['token']));
        self::assertSame(204, $overwrite->getStatusCode());
        self::assertSame(1, (int) $this->pdo->query('SELECT COUNT(*) FROM account_iptv_credentials')->fetchColumn());

        $payload = (string) $this->pdo->query("SELECT encode(payload, 'escape') FROM account_iptv_credentials")->fetchColumn();
        foreach ($second as $value) {
            self::assertStringNotContainsString((string) $value, $payload);
        }

        $staleDelete = $this->request('DELETE', self::PATH, '', $this->auth($account['token']) + ['If-Match' => $etag]);
        self::assertSame(412, $staleDelete->getStatusCode());
        self::assertSame(204, $this->request('DELETE', self::PATH, '', $this->auth($account['token']))->getStatusCode());
        self::assertSame(204, $this->request('DELETE', self::PATH, '', $this->auth($account['token']))->getStatusCode());
    }

    public function testAccountCascadeRemovesTheCredentials(): void
    {
        $account = $this->createAccount();
        self::assertSame(204, $this->jsonRequest('PUT', self::PATH, $this->credentials(), $this->auth($account['token']))->getStatusCode());
        $this->pdo->prepare('DELETE FROM accounts WHERE id = :id')->execute(['id' => $account['id']]);
        self::assertSame(0, (int) $this->pdo->query('SELECT COUNT(*) FROM account_iptv_credentials')->fetchColumn());
    }
}
