<?php

declare(strict_types=1);

namespace Cstv\Backend\Http\Action;

use Cstv\Backend\Catalog\CatalogMatchHints;
use Cstv\Backend\Catalog\CatalogMatchRequest;
use Cstv\Backend\Catalog\CatalogService;
use Cstv\Backend\Catalog\DeviceType;
use Cstv\Backend\Http\Json;
use Cstv\Backend\Shared\ApiException;
use Cstv\Backend\Shared\ClientIp;
use Cstv\Backend\Shared\Uuid;
use Psr\Http\Message\ResponseInterface;
use Psr\Http\Message\ServerRequestInterface;

final readonly class CatalogAction
{
    public function __construct(private CatalogService $service) {}
    public function trending(ServerRequestInterface $request, ResponseInterface $response): ResponseInterface { return $this->respond($response, $this->service->trending($this->locale($request))); }
    public function popular(ServerRequestInterface $request, ResponseInterface $response): ResponseInterface { $query = $request->getQueryParams(); return $this->respond($response, $this->service->popular($this->kind($query['kind'] ?? null), $this->page($query['page'] ?? 1), $this->locale($request))); }

    public function match(ServerRequestInterface $request, ResponseInterface $response): ResponseInterface
    {
        if (strtolower(trim(explode(';', $request->getHeaderLine('Content-Type'), 2)[0])) !== 'application/json') throw new ApiException(415, 'UNSUPPORTED_MEDIA_TYPE', 'Content-Type must be application/json.');
        $body = Json::body($request);
        $account = $request->getAttribute('account');
        $accountId = is_array($account) && is_string($account['id'] ?? null) ? $account['id'] : throw new ApiException(401, 'AUTHENTICATION_REQUIRED', 'A bearer access token is required.');
        $ip = $request->getServerParams()['REMOTE_ADDR'] ?? '0.0.0.0';
        $matchRequest = new CatalogMatchRequest(
            $this->kind($body['kind'] ?? null),
            $this->title($body['title'] ?? null),
            $this->year($body['year'] ?? null),
            $this->locale($request, $body['locale'] ?? null),
            CatalogMatchHints::fromArray($body),
        );
        return $this->respond($response, $this->service->match($matchRequest, $accountId, ClientIp::rateLimitKey(is_string($ip) ? $ip : '0.0.0.0'), $this->device($request)));
    }

    public function matchBatch(ServerRequestInterface $request, ResponseInterface $response): ResponseInterface
    {
        if (strtolower(trim(explode(';', $request->getHeaderLine('Content-Type'), 2)[0])) !== 'application/json') throw new ApiException(415, 'UNSUPPORTED_MEDIA_TYPE', 'Content-Type must be application/json.');
        $body = Json::body($request);
        $items = $body['items'] ?? null;
        if (!is_array($items) || $items === [] || count($items) > 50) throw new ApiException(422, 'INVALID_CATALOG_BATCH', 'items must contain between 1 and 50 matches.');
        $account = $request->getAttribute('account');
        $accountId = is_array($account) && is_string($account['id'] ?? null) ? $account['id'] : throw new ApiException(401, 'AUTHENTICATION_REQUIRED', 'A bearer access token is required.');
        $ip = $request->getServerParams()['REMOTE_ADDR'] ?? '0.0.0.0';
        $matches = [];
        foreach ($items as $item) {
            if (!is_array($item)) throw new ApiException(422, 'INVALID_CATALOG_BATCH', 'Each item must be an object.');
            // Hints sent by APKs published before this hotfix are accepted but ignored.
            $matches[] = new CatalogMatchRequest(
                $this->kind($item['kind'] ?? null), $this->title($item['title'] ?? null),
                $this->year($item['year'] ?? null), $this->locale($request, $item['locale'] ?? null),
                new CatalogMatchHints(),
            );
        }
        return $this->respond($response, $this->service->matchBatch($matches, $accountId, ClientIp::rateLimitKey(is_string($ip) ? $ip : '0.0.0.0'), $this->device($request), $this->retryAware($request)));
    }

    public function videos(ServerRequestInterface $request, ResponseInterface $response, array $args): ResponseInterface
    {
        $id = $args['externalId'] ?? '';
        $isLegacy = is_string($id) && preg_match('/^(movie|series):\\d+$/D', $id) === 1;
        $isUuid = is_string($id) && Uuid::isValid($id);
        if (!$isLegacy && !$isUuid) throw new ApiException(422, 'INVALID_CATALOG_ID', 'externalId is invalid.');
        return $this->respond($response, $this->service->videos($id, $this->locale($request)));
    }

    public function item(ServerRequestInterface $request, ResponseInterface $response, array $args): ResponseInterface
    {
        return $this->respond($response, $this->service->item($this->externalId($args), $this->device($request)));
    }

    public function recommendations(ServerRequestInterface $request, ResponseInterface $response, array $args): ResponseInterface
    {
        return $this->respond($response, $this->service->recommendations($this->externalId($args)));
    }

    public function season(ServerRequestInterface $request, ResponseInterface $response, array $args): ResponseInterface
    {
        $seasonNumber = filter_var($args['seasonNumber'] ?? null, FILTER_VALIDATE_INT);
        if ($seasonNumber === false || $seasonNumber < 0) throw new ApiException(422, 'INVALID_CATALOG_SEASON', 'seasonNumber is invalid.');
        return $this->respond($response, $this->service->season($this->externalId($args), $seasonNumber, $this->device($request)));
    }

    /** @param array<string, mixed> $payload */ private function respond(ResponseInterface $response, array $payload): ResponseInterface { return Json::response($response, $payload); }
    private function kind(mixed $value): string { if (!is_string($value) || !in_array($value, ['movie', 'series'], true)) throw new ApiException(422, 'INVALID_CATALOG_KIND', 'kind must be movie or series.'); return $value; }
    private function page(mixed $value): int { if (filter_var($value, FILTER_VALIDATE_INT) === false || (int) $value < 1 || (int) $value > 20) throw new ApiException(422, 'INVALID_CATALOG_PAGE', 'page must be between 1 and 20.'); return (int) $value; }
    private function title(mixed $value): string { if (!is_string($value) || ($value = trim($value)) === '' || mb_strlen($value) > 200 || preg_match('/[\\x00-\\x1F\\x7F]/u', $value)) throw new ApiException(422, 'INVALID_CATALOG_TITLE', 'title is invalid.'); return $value; }
    private function year(mixed $value): ?int { if ($value === null) return null; if (!is_int($value) || $value < 1888 || $value > (int) date('Y') + 2) throw new ApiException(422, 'INVALID_CATALOG_YEAR', 'year is invalid.'); return $value; }
    private function locale(ServerRequestInterface $request, mixed $bodyLocale = null): string { $value = $bodyLocale ?? ($request->getQueryParams()['locale'] ?? 'fr-FR'); if (!is_string($value) || !in_array($value, ['fr-FR', 'en-US'], true)) throw new ApiException(422, 'INVALID_CATALOG_LOCALE', 'locale is not supported.'); return $value; }
    private function device(ServerRequestInterface $request): DeviceType { return DeviceType::fromHeader($request->getHeaderLine('X-CSTV-Device-Type')); }

    /**
     * Review T29/R1 : négociation de capacité explicite — seul un client annonçant comprendre le
     * statut `retry` par item (§8.9/§8.10) peut en recevoir un sur `/matches/batch`. Une APK
     * pré-T29 n'envoie jamais cet en-tête et conserve donc le comportement d'échec HTTP global déjà
     * connu de son worker (voir `CatalogService::matchBatch()`).
     */
    private function retryAware(ServerRequestInterface $request): bool
    {
        $capabilities = array_map(
            static fn (string $capability): string => strtolower(trim($capability)),
            explode(',', $request->getHeaderLine('X-CSTV-Catalog-Capabilities')),
        );
        return in_array('retry', $capabilities, true);
    }
    /** @param array<string, mixed> $args */
    private function externalId(array $args): string
    {
        $id = $args['externalId'] ?? '';
        if (!is_string($id) || !Uuid::isValid($id)) throw new ApiException(422, 'INVALID_CATALOG_ID', 'externalId is invalid.');
        return $id;
    }
}
