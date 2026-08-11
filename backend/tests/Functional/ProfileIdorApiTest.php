<?php

declare(strict_types=1);

namespace Cstv\Backend\Tests\Functional;

final class ProfileIdorApiTest extends FunctionalTestCase
{
    public function testProfileCrudWorksThroughRealHttpStack(): void
    {
        $account = $this->createAccount();
        $created = $this->api->postJson('/v1/profiles', ['name' => 'Nico', 'avatarId' => 3], $this->auth($account['token']));
        self::assertSame(201, $created->status);
        $profileId = $created->json()['id'];

        $listed = $this->api->get('/v1/profiles', $this->auth($account['token']));
        self::assertSame(200, $listed->status);
        self::assertCount(2, $listed->json()['profiles']);
        self::assertContains($profileId, array_column($listed->json()['profiles'], 'id'));

        $renamed = $this->api->patchJson(
            '/v1/profiles/' . $profileId,
            ['name' => 'Nicolas'],
            $this->auth($account['token']),
        );
        self::assertSame(200, $renamed->status);
        self::assertSame('Nicolas', $renamed->json()['name']);
        self::assertSame(3, $renamed->json()['avatarId']);

        $avatar = $this->api->patchJson(
            '/v1/profiles/' . $profileId,
            ['avatarId' => 7],
            $this->auth($account['token']),
        );
        self::assertSame(7, $avatar->json()['avatarId']);

        self::assertSame(204, $this->api->delete('/v1/profiles/' . $profileId, $this->auth($account['token']))->status);
        self::assertNotContains(
            $profileId,
            array_column($this->api->get('/v1/profiles', $this->auth($account['token']))->json()['profiles'], 'id'),
        );
    }

    public function testLastProfileCannotBeDeleted(): void
    {
        $account = $this->createAccount();
        $this->assertError(
            $this->api->delete('/v1/profiles/' . $account['profileIds'][0], $this->auth($account['token'])),
            409,
            'LAST_PROFILE_REQUIRED',
        );
    }

    public function testProfileValidationAndMissingProfileErrorsAreStable(): void
    {
        $account = $this->createAccount();
        $headers = $this->auth($account['token']);

        $this->assertError($this->api->postJson('/v1/profiles', ['name' => ''], $headers), 422, 'INVALID_PROFILE_NAME');
        $this->assertError(
            $this->api->postJson('/v1/profiles', ['name' => str_repeat('x', 81)], $headers),
            422,
            'INVALID_PROFILE_NAME',
        );
        $this->assertError(
            $this->api->patchJson('/v1/profiles/not-a-uuid', ['name' => 'X'], $headers),
            422,
            'INVALID_UUID',
        );
        $this->assertError(
            $this->api->patchJson('/v1/profiles/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', ['name' => 'X'], $headers),
            404,
            'PROFILE_NOT_FOUND',
        );
        $this->assertError(
            $this->api->patchJson('/v1/profiles/' . $account['profileIds'][0], [], $headers),
            422,
            'EMPTY_UPDATE',
        );
        $this->assertError(
            $this->api->request('POST', '/v1/profiles', '{broken', ['Content-Type' => 'application/json'] + $headers),
            400,
            'INVALID_JSON',
        );
    }

    public function testCrossAccountProfileAndObjectOperationsAreAllRefused(): void
    {
        $owner = $this->createAccount('owner@example.com', profileCount: 2);
        $attacker = $this->createAccount('attacker@example.com');
        $profileId = $owner['profileIds'][0];
        $payload = (string) gzencode('{"secret":true}');
        self::assertSame(204, $this->putObject($owner['token'], $profileId, 'favorites', 'movie-1', $payload)->status);

        $attackerAuth = $this->auth($attacker['token']);
        $this->assertError(
            $this->api->get('/v1/profiles/' . $profileId . '/objects', $attackerAuth),
            404,
            'PROFILE_NOT_FOUND',
        );
        $this->assertError(
            $this->api->patchJson('/v1/profiles/' . $profileId, ['name' => 'Stolen'], $attackerAuth),
            404,
            'PROFILE_NOT_FOUND',
        );
        $this->assertError(
            $this->api->delete('/v1/profiles/' . $owner['profileIds'][1], $attackerAuth),
            404,
            'PROFILE_NOT_FOUND',
        );

        $uri = '/v1/profiles/' . $profileId . '/objects/favorites/movie-1';
        $this->assertError($this->api->get($uri, $attackerAuth), 404, 'OBJECT_NOT_FOUND');
        $this->assertError(
            $this->putObject($attacker['token'], $profileId, 'favorites', 'movie-1', (string) gzencode('{}')),
            404,
            'PROFILE_NOT_FOUND',
        );
        $this->assertError($this->api->delete($uri, $attackerAuth), 404, 'PROFILE_NOT_FOUND');

        $changes = $this->api->get('/v1/sync/changes', $attackerAuth)->json()['changes'];
        self::assertSame([], $changes);
        self::assertSame($payload, $this->api->get($uri, $this->auth($owner['token']))->body);
    }

    public function testDeletingProfileCreatesTombstonesBeforeCascade(): void
    {
        $account = $this->createAccount(profileCount: 2);
        $profileId = $account['profileIds'][1];
        $this->putObject($account['token'], $profileId, 'favorites', 'movie-1', (string) gzencode('one'));
        $this->putObject($account['token'], $profileId, 'playback', 'movie-1', (string) gzencode('two'));

        self::assertSame(204, $this->api->delete('/v1/profiles/' . $profileId, $this->auth($account['token']))->status);
        $page = $this->api->get('/v1/sync/changes?cursor=0', $this->auth($account['token']))->json();
        self::assertSame(['UPSERT', 'UPSERT', 'DELETE', 'DELETE'], array_column($page['changes'], 'operation'));
        self::assertSame([$profileId, $profileId, $profileId, $profileId], array_column($page['changes'], 'profileId'));
        self::assertSame(0, (int) $this->pdo->query("SELECT COUNT(*) FROM profile_objects WHERE profile_id = '{$profileId}'")->fetchColumn());
    }
}
