<?php

declare(strict_types=1);

namespace Cstv\Backend\Http\Action;

use Cstv\Backend\Http\Json;
use Cstv\Backend\Profile\ProfilePresenter;
use Cstv\Backend\Profile\ProfileRepository;
use Cstv\Backend\Shared\DateFormatter;
use Psr\Http\Message\ResponseInterface;
use Psr\Http\Message\ServerRequestInterface;

final readonly class MeAction
{
    public function __construct(private ProfileRepository $profiles)
    {
    }

    /** @param array<string, string> $args */
    public function __invoke(ServerRequestInterface $request, ResponseInterface $response, array $args): ResponseInterface
    {
        /** @var array<string, mixed> $account */
        $account = $request->getAttribute('account');
        $profiles = array_map(
            static fn (array $profile): array => ProfilePresenter::one($profile),
            $this->profiles->listForAccount((string) $account['id']),
        );

        return Json::response($response, [
            'id' => (string) $account['id'],
            'email' => (string) $account['email'],
            'enabled' => (bool) $account['enabled'],
            'activeUntil' => DateFormatter::iso8601((string) $account['active_until']),
            'profiles' => $profiles,
        ]);
    }
}
