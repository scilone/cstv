<?php

declare(strict_types=1);

namespace Cstv\Backend\Http\Action;

use Cstv\Backend\Http\Json;
use Cstv\Backend\Profile\ProfilePresenter;
use Cstv\Backend\Profile\ProfileRepository;
use Cstv\Backend\Profile\ProfileService;
use Cstv\Backend\Shared\Validator;
use Psr\Http\Message\ResponseInterface;
use Psr\Http\Message\ServerRequestInterface;

final readonly class ProfileAction
{
    public function __construct(
        private ProfileRepository $profiles,
        private ProfileService $service,
    ) {
    }

    /** @param array<string, string> $args */
    public function list(ServerRequestInterface $request, ResponseInterface $response, array $args): ResponseInterface
    {
        $accountId = $this->accountId($request);
        return Json::response($response, [
            'profiles' => array_map(
                static fn (array $profile): array => ProfilePresenter::one($profile),
                $this->profiles->listForAccount($accountId),
            ),
        ]);
    }

    /** @param array<string, string> $args */
    public function create(ServerRequestInterface $request, ResponseInterface $response, array $args): ResponseInterface
    {
        $body = Json::body($request);
        $profile = $this->service->create(
            $this->accountId($request),
            $body['name'] ?? null,
            $body['avatarId'] ?? 0,
        );

        return Json::response($response, ProfilePresenter::one($profile), 201);
    }

    /** @param array<string, string> $args */
    public function update(ServerRequestInterface $request, ResponseInterface $response, array $args): ResponseInterface
    {
        $profileId = Validator::uuid($args['profileId'] ?? null, 'profileId');
        $profile = $this->service->update($this->accountId($request), $profileId, Json::body($request));

        return Json::response($response, ProfilePresenter::one($profile));
    }

    /** @param array<string, string> $args */
    public function delete(ServerRequestInterface $request, ResponseInterface $response, array $args): ResponseInterface
    {
        $profileId = Validator::uuid($args['profileId'] ?? null, 'profileId');
        $this->service->delete($this->accountId($request), $profileId);
        return $response->withStatus(204);
    }

    private function accountId(ServerRequestInterface $request): string
    {
        /** @var array<string, mixed> $account */
        $account = $request->getAttribute('account');
        return (string) $account['id'];
    }
}
