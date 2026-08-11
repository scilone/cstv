<?php

declare(strict_types=1);

namespace Cstv\Backend\Tests\Integration;

/**
 * Every route that takes a profileId in its path must prove ownership against the authenticated
 * account, not merely that the profile exists.
 */
final class IdorTest extends IntegrationTestCase
{
    /** @var array{id: string, profileIds: list<string>, token: string} */
    private array $victim;

    /** @var array{id: string, profileIds: list<string>, token: string} */
    private array $attacker;

    private string $victimProfileId;

    private string $victimEtag;

    protected function setUp(): void
    {
        parent::setUp();
        $this->victim = $this->createAccount('victim@example.com', profileCount: 2);
        $this->attacker = $this->createAccount('attacker@example.com');
        $this->victimProfileId = $this->victim['profileIds'][0];

        $payload = (string) gzencode('{"secret":true}');
        $this->victimEtag = $this->putObject(
            $this->victim['token'],
            $this->victimProfileId,
            'favorites',
            'movie-1',
            $payload,
        )->getHeaderLine('ETag');
        self::assertNotSame('', $this->victimEtag);
    }

    public function testForeignObjectCannotBeRead(): void
    {
        $response = $this->request(
            'GET',
            $this->victimObjectUri(),
            '',
            $this->auth($this->attacker['token']),
        );

        self::assertSame(404, $response->getStatusCode());
        self::assertSame('OBJECT_NOT_FOUND', $this->json($response)['error']['code']);
    }

    public function testForeignObjectCannotBeOverwritten(): void
    {
        $response = $this->putObject(
            $this->attacker['token'],
            $this->victimProfileId,
            'favorites',
            'movie-1',
            (string) gzencode('{"secret":false}'),
        );

        self::assertSame(404, $response->getStatusCode());
        self::assertSame('PROFILE_NOT_FOUND', $this->json($response)['error']['code']);
        self::assertSame($this->victimEtag, $this->currentEtag());
    }

    public function testForeignObjectCannotBeCreatedInAnotherAccountProfile(): void
    {
        $response = $this->putObject(
            $this->attacker['token'],
            $this->victimProfileId,
            'ratings',
            'movie-999',
            (string) gzencode('{"rating":1}'),
        );

        self::assertSame(404, $response->getStatusCode());
        self::assertSame(1, (int) $this->pdo->query(
            "SELECT COUNT(*) FROM profile_objects WHERE profile_id = '{$this->victimProfileId}'",
        )->fetchColumn());
    }

    public function testForeignObjectCannotBeDeleted(): void
    {
        $response = $this->request(
            'DELETE',
            $this->victimObjectUri(),
            '',
            $this->auth($this->attacker['token']),
        );

        self::assertSame(404, $response->getStatusCode());
        self::assertSame($this->victimEtag, $this->currentEtag());
    }

    public function testForeignObjectListingIsRefused(): void
    {
        $response = $this->request(
            'GET',
            '/v1/profiles/' . $this->victimProfileId . '/objects',
            '',
            $this->auth($this->attacker['token']),
        );

        self::assertSame(404, $response->getStatusCode());
        self::assertSame('PROFILE_NOT_FOUND', $this->json($response)['error']['code']);
    }

    public function testForeignProfileCannotBeDeleted(): void
    {
        $response = $this->request(
            'DELETE',
            '/v1/profiles/' . $this->victim['profileIds'][1],
            '',
            $this->auth($this->attacker['token']),
        );

        self::assertSame(404, $response->getStatusCode());
        self::assertSame(2, (int) $this->pdo->query(
            "SELECT COUNT(*) FROM profiles WHERE account_id = '{$this->victim['id']}'",
        )->fetchColumn());
    }

    public function testForeignChangesNeverAppearInTheSyncJournal(): void
    {
        $response = $this->request('GET', '/v1/sync/changes', '', $this->auth($this->attacker['token']));
        $page = $this->json($response);

        self::assertSame(200, $response->getStatusCode());
        self::assertSame([], $page['changes']);
        self::assertSame(0, $page['nextCursor']);
        self::assertFalse($page['hasMore']);
    }

    public function testForeignProfilesNeverAppearInMe(): void
    {
        $response = $this->request('GET', '/v1/me', '', $this->auth($this->attacker['token']));
        $ids = array_column($this->json($response)['profiles'], 'id');

        self::assertSame($this->attacker['profileIds'], $ids);
        self::assertNotContains($this->victimProfileId, $ids);
    }

    private function victimObjectUri(): string
    {
        return sprintf('/v1/profiles/%s/objects/favorites/movie-1', $this->victimProfileId);
    }

    private function currentEtag(): string
    {
        return '"' . (string) $this->pdo->query(
            "SELECT etag FROM profile_objects WHERE profile_id = '{$this->victimProfileId}'",
        )->fetchColumn() . '"';
    }
}
