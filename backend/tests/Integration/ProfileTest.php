<?php

declare(strict_types=1);

namespace Cstv\Backend\Tests\Integration;

final class ProfileTest extends IntegrationTestCase
{
    public function testProfileCanBeCreatedAndUpdated(): void
    {
        $account = $this->createAccount();
        $created = $this->json($this->jsonRequest(
            'POST',
            '/v1/profiles',
            ['name' => 'Nico', 'avatarId' => 3],
            $this->auth($account['token']),
        ));

        self::assertSame('Nico', $created['name']);
        $response = $this->jsonRequest(
            'PATCH',
            '/v1/profiles/' . $created['id'],
            ['name' => 'Nicolas', 'avatarId' => 4],
            $this->auth($account['token']),
        );
        self::assertSame(200, $response->getStatusCode());
        self::assertSame('Nicolas', $this->json($response)['name']);
        self::assertSame(4, $this->json($response)['avatarId']);
    }

    public function testProfileCreationIsCappedPerAccount(): void
    {
        $this->withOverriddenConfig(['MAX_PROFILES_PER_ACCOUNT' => '3'], function (): void {
            $account = $this->createAccount(); // starts with one profile
            for ($index = 0; $index < 2; $index++) {
                self::assertSame(201, $this->jsonRequest(
                    'POST',
                    '/v1/profiles',
                    ['name' => 'Extra ' . $index, 'avatarId' => 0],
                    $this->auth($account['token']),
                )->getStatusCode());
            }

            $blocked = $this->jsonRequest(
                'POST',
                '/v1/profiles',
                ['name' => 'Over the limit', 'avatarId' => 0],
                $this->auth($account['token']),
            );
            self::assertSame(409, $blocked->getStatusCode());
            self::assertSame('PROFILE_LIMIT_REACHED', $this->json($blocked)['error']['code']);
            self::assertSame(3, (int) $this->pdo->query(
                "SELECT COUNT(*) FROM profiles WHERE account_id = '{$account['id']}'",
            )->fetchColumn());
        });
    }

    public function testLastProfileCannotBeDeleted(): void
    {
        $account = $this->createAccount();
        $response = $this->request(
            'DELETE',
            '/v1/profiles/' . $account['profileIds'][0],
            '',
            $this->auth($account['token']),
        );

        self::assertSame(409, $response->getStatusCode());
        self::assertSame('LAST_PROFILE_REQUIRED', $this->json($response)['error']['code']);
    }

    public function testProfileDeletionCascadesNamespaceSnapshots(): void
    {
        $account = $this->createAccount(profileCount: 2);
        $deletedProfile = $account['profileIds'][1];
        self::assertSame(204, $this->putObject(
            $account['token'],
            $deletedProfile,
            'favorites',
            gzencode('{"id":1}') ?: '',
        )->getStatusCode());

        $response = $this->request(
            'DELETE',
            '/v1/profiles/' . $deletedProfile,
            '',
            $this->auth($account['token']),
        );
        self::assertSame(204, $response->getStatusCode());
        self::assertSame(0, (int) $this->pdo->query(
            "SELECT COUNT(*) FROM profiles WHERE id = '{$deletedProfile}'",
        )->fetchColumn());
        self::assertSame(0, (int) $this->pdo->query(
            "SELECT COUNT(*) FROM profile_objects WHERE profile_id = '{$deletedProfile}'",
        )->fetchColumn());
    }

    public function testOtherAccountsProfileCannotBeReadOrModified(): void
    {
        $first = $this->createAccount('first@example.com');
        $second = $this->createAccount('second@example.com');

        $list = $this->request(
            'GET',
            '/v1/profiles/' . $second['profileIds'][0] . '/objects',
            '',
            $this->auth($first['token']),
        );
        $patch = $this->jsonRequest(
            'PATCH',
            '/v1/profiles/' . $second['profileIds'][0],
            ['name' => 'Stolen'],
            $this->auth($first['token']),
        );

        self::assertSame(404, $list->getStatusCode());
        self::assertSame(404, $patch->getStatusCode());
    }
}
