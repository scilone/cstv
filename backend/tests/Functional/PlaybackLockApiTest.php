<?php

declare(strict_types=1);

namespace Cstv\Backend\Tests\Functional;

final class PlaybackLockApiTest extends FunctionalTestCase
{
    private const PATH = '/v1/account/playback-lock';

    public function testHttpContractIsAuthenticatedAndNoStore(): void
    {
        $account = $this->createAccount();
        $payload = ['deviceId' => '11111111-1111-4111-8111-111111111111', 'deviceName' => 'Living room TV', 'takeover' => false];
        $created = $this->api->postJson(self::PATH, $payload, $this->auth($account['token']));
        self::assertSame(200, $created->status);
        self::assertSame('no-store', $created->header('cache-control'));
        self::assertArrayHasKey('lockToken', $created->json());

        $blocked = $this->api->postJson(self::PATH, array_replace($payload, ['deviceId' => '22222222-2222-4222-8222-222222222222']), $this->auth($account['token']));
        self::assertSame(409, $blocked->status);
        self::assertSame('PLAYBACK_LOCK_HELD', $blocked->json()['code']);
        self::assertArrayHasKey('holder', $blocked->json());
        $this->assertError($this->api->postJson(self::PATH, $payload), 401, 'AUTHENTICATION_REQUIRED');
        self::assertSame(204, $this->api->delete(self::PATH, $this->auth($account['token']) + ['If-Match' => '"' . $created->json()['lockToken'] . '"'])->status);
    }
}
