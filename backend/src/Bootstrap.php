<?php

declare(strict_types=1);

namespace Cstv\Backend;

use Cstv\Backend\Account\AccountRepository;
use Cstv\Backend\Account\IptvCredentialsRepository;
use Cstv\Backend\Account\IptvCredentialsService;
use Cstv\Backend\Catalog\CatalogMatchEngine;
use Cstv\Backend\Catalog\CatalogService;
use Cstv\Backend\Catalog\CatalogMatchThrottleRepository;
use Cstv\Backend\Catalog\ExternalMediaIdFactory;
use Cstv\Backend\Catalog\ExternalMediaRepository;
use Cstv\Backend\Catalog\MediaMetadataProvider;
use Cstv\Backend\Catalog\MediaMetadataCacheRepository;
use Cstv\Backend\Catalog\TmdbClient;
use Cstv\Backend\Catalog\TmdbCertificationMapper;
use Cstv\Backend\Catalog\TmdbMediaMetadataProvider;
use Cstv\Backend\Catalog\TmdbProviderRateLimiter;
use Cstv\Backend\Auth\AuthService;
use Cstv\Backend\Auth\JwtService;
use Cstv\Backend\Auth\LogOtpSender;
use Cstv\Backend\Auth\MailOtpSender;
use Cstv\Backend\Auth\OtpRepository;
use Cstv\Backend\Database\Connection;
use Cstv\Backend\Http\Action\AuthAction;
use Cstv\Backend\Http\Action\CatalogAction;
use Cstv\Backend\Http\Action\HealthAction;
use Cstv\Backend\Http\Action\IptvCredentialsAction;
use Cstv\Backend\Http\Action\MeAction;
use Cstv\Backend\Http\Action\ObjectAction;
use Cstv\Backend\Http\Action\ProfileAction;
use Cstv\Backend\Http\Action\PlaybackLockAction;
use Cstv\Backend\Http\ApiErrorHandler;
use Cstv\Backend\Http\AuthMiddleware;
use Cstv\Backend\Http\SecurityHeadersMiddleware;
use Cstv\Backend\Profile\ProfileRepository;
use Cstv\Backend\Profile\ProfileService;
use Cstv\Backend\Playback\PlaybackLockRepository;
use Cstv\Backend\Playback\PlaybackLockService;
use Cstv\Backend\Shared\Config;
use Cstv\Backend\Shared\Crypto\EnvelopeCipher;
use Cstv\Backend\Shared\Crypto\KeyRing;
use Cstv\Backend\Sync\ObjectRepository;
use Cstv\Backend\Sync\ObjectService;
use PDO;
use Slim\App;
use Slim\Factory\AppFactory;
use Slim\Routing\RouteCollectorProxy;

final class Bootstrap
{
    public static function createApp(?Config $config = null, ?PDO $pdo = null, ?MediaMetadataProvider $catalogProvider = null): App
    {
        $config ??= Config::fromEnvironment();
        $pdo ??= Connection::create($config);

        $accounts = new AccountRepository($pdo);
        $profiles = new ProfileRepository($pdo);
        $profileService = new ProfileService($pdo, $profiles, $config->maxProfilesPerAccount);
        $jwt = new JwtService($config->jwtSecret);
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
        $objectService = new ObjectService(
            $pdo,
            $profiles,
            new ObjectRepository($pdo),
            $config->maxNamespacesPerProfile,
            $config->maxStorageBytesPerAccount,
        );
        $iptvCredentials = new IptvCredentialsService(
            $pdo,
            new IptvCredentialsRepository($pdo),
            new EnvelopeCipher(new KeyRing($config->iptvCredentialsKeys, $config->iptvCredentialsKeyId)),
        );
        $playbackLocks = new PlaybackLockService(
            $pdo,
            new PlaybackLockRepository($pdo),
            $config->playbackLockTtlSeconds,
            $config->playbackLockHeartbeatSeconds,
        );
        // Tests may boot the authenticated API without a provider token. The catalog endpoints
        // then fail as an enrichment outage instead of exposing a development secret.
        $externalMedia = new ExternalMediaRepository($pdo, new ExternalMediaIdFactory());
        $provider = $catalogProvider ?? new TmdbMediaMetadataProvider(
            new TmdbClient($config->tmdbApiToken ?? ''),
            new TmdbCertificationMapper(),
            new TmdbProviderRateLimiter($pdo),
            $pdo,
        );
        $catalog = new CatalogService(
            $pdo,
            new MediaMetadataCacheRepository($pdo),
            $provider,
            new CatalogMatchThrottleRepository($pdo),
            new CatalogMatchEngine($provider, $externalMedia),
            $externalMedia,
        );

        $app = AppFactory::create();
        $app->get('/health', new HealthAction($pdo));
        $authAction = new AuthAction($auth);
        $app->post('/v1/auth/otp/request', [$authAction, 'request']);
        $app->post('/v1/auth/otp/verify', [$authAction, 'verify']);

        $profileAction = new ProfileAction($profiles, $profileService);
        $objectAction = new ObjectAction($objectService, $config->maxObjectSizeBytes);
        $iptvCredentialsAction = new IptvCredentialsAction($iptvCredentials, $config->maxIptvCredentialsBytes);
        $playbackLockAction = new PlaybackLockAction($playbackLocks);
        $catalogAction = new CatalogAction($catalog);
        $app->group('/v1', function (RouteCollectorProxy $group) use ($profiles, $profileAction, $objectAction, $iptvCredentialsAction, $playbackLockAction, $catalogAction): void {
            $group->get('/me', new MeAction($profiles));
            $group->get('/account/iptv-credentials', [$iptvCredentialsAction, 'get']);
            $group->put('/account/iptv-credentials', [$iptvCredentialsAction, 'put']);
            $group->delete('/account/iptv-credentials', [$iptvCredentialsAction, 'delete']);
            $group->post('/account/playback-lock', [$playbackLockAction, 'acquire']);
            $group->post('/account/playback-lock/heartbeat', [$playbackLockAction, 'heartbeat']);
            $group->delete('/account/playback-lock', [$playbackLockAction, 'release']);
            $group->get('/catalog/trending', [$catalogAction, 'trending']);
            $group->get('/catalog/popular', [$catalogAction, 'popular']);
            $group->post('/catalog/matches', [$catalogAction, 'match']);
            $group->post('/catalog/matches/batch', [$catalogAction, 'matchBatch']);
            $group->get('/catalog/items/{externalId}', [$catalogAction, 'item']);
            $group->get('/catalog/items/{externalId}/recommendations', [$catalogAction, 'recommendations']);
            $group->get('/catalog/items/{externalId}/videos', [$catalogAction, 'videos']);
            $group->get('/catalog/items/{externalId}/seasons/{seasonNumber}', [$catalogAction, 'season']);
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

        // Added last so it is the outermost middleware: it wraps the error middleware above and
        // therefore stamps headers on error responses (401, 404, 429, 500, ...) as well as 2xx.
        $app->add(new SecurityHeadersMiddleware());

        return $app;
    }
}
