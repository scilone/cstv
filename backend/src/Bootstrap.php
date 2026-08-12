<?php

declare(strict_types=1);

namespace Cstv\Backend;

use Cstv\Backend\Account\AccountRepository;
use Cstv\Backend\Auth\AuthService;
use Cstv\Backend\Auth\JwtService;
use Cstv\Backend\Auth\LogOtpSender;
use Cstv\Backend\Auth\MailOtpSender;
use Cstv\Backend\Auth\OtpRepository;
use Cstv\Backend\Database\Connection;
use Cstv\Backend\Http\Action\AuthAction;
use Cstv\Backend\Http\Action\HealthAction;
use Cstv\Backend\Http\Action\MeAction;
use Cstv\Backend\Http\Action\ObjectAction;
use Cstv\Backend\Http\Action\ProfileAction;
use Cstv\Backend\Http\ApiErrorHandler;
use Cstv\Backend\Http\AuthMiddleware;
use Cstv\Backend\Profile\ProfileRepository;
use Cstv\Backend\Profile\ProfileService;
use Cstv\Backend\Shared\Config;
use Cstv\Backend\Sync\ObjectRepository;
use Cstv\Backend\Sync\ObjectService;
use PDO;
use Slim\App;
use Slim\Factory\AppFactory;
use Slim\Routing\RouteCollectorProxy;

final class Bootstrap
{
    public static function createApp(?Config $config = null, ?PDO $pdo = null): App
    {
        $config ??= Config::fromEnvironment();
        $pdo ??= Connection::create($config);

        $accounts = new AccountRepository($pdo);
        $profiles = new ProfileRepository($pdo);
        $profileService = new ProfileService($pdo, $profiles);
        $jwt = new JwtService($config->jwtSecret, $config->jwtTtlSeconds);
        $otpSender = $config->appEnv === 'production'
            ? new MailOtpSender($config->otpFromEmail, $config->otpFromName)
            : new LogOtpSender($config->appEnv);
        $auth = new AuthService(
            $pdo,
            $config,
            new OtpRepository($pdo),
            $accounts,
            $otpSender,
            $jwt,
        );
        $objectService = new ObjectService($pdo, $profiles, new ObjectRepository($pdo));

        $app = AppFactory::create();
        $app->get('/health', new HealthAction($pdo));
        $authAction = new AuthAction($auth);
        $app->post('/v1/auth/otp/request', [$authAction, 'request']);
        $app->post('/v1/auth/otp/verify', [$authAction, 'verify']);

        $profileAction = new ProfileAction($profiles, $profileService);
        $objectAction = new ObjectAction($objectService, $config->maxObjectSizeBytes);
        $app->group('/v1', function (RouteCollectorProxy $group) use ($profiles, $profileAction, $objectAction): void {
            $group->get('/me', new MeAction($profiles));
            $group->get('/profiles', [$profileAction, 'list']);
            $group->post('/profiles', [$profileAction, 'create']);
            $group->patch('/profiles/{profileId}', [$profileAction, 'update']);
            $group->delete('/profiles/{profileId}', [$profileAction, 'delete']);
            $group->get('/profiles/{profileId}/objects', [$objectAction, 'list']);
            $group->get('/profiles/{profileId}/objects/{namespace}', [$objectAction, 'get']);
            $group->put('/profiles/{profileId}/objects/{namespace}', [$objectAction, 'put']);
            $group->delete('/profiles/{profileId}/objects/{namespace}', [$objectAction, 'delete']);
        })->add(new AuthMiddleware($jwt, $accounts));

        $app->addBodyParsingMiddleware();
        $app->addRoutingMiddleware();
        $errorMiddleware = $app->addErrorMiddleware(
            $config->appDebug && $config->appEnv !== 'production',
            true,
            true,
        );
        $errorMiddleware->setDefaultErrorHandler(new ApiErrorHandler($app->getResponseFactory()));

        return $app;
    }
}
