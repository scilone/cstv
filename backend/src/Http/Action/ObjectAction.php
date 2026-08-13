<?php

declare(strict_types=1);

namespace Cstv\Backend\Http\Action;

use Cstv\Backend\Http\BinaryBody;
use Cstv\Backend\Http\Json;
use Cstv\Backend\Shared\ApiException;
use Cstv\Backend\Shared\Validator;
use Cstv\Backend\Sync\ObjectService;
use Psr\Http\Message\ResponseInterface;
use Psr\Http\Message\ServerRequestInterface;

final readonly class ObjectAction
{
    private const CONTENT_TYPE = 'application/vnd.cstv.blob+gzip';

    public function __construct(private ObjectService $objects, private int $maximumBytes)
    {
    }

    /** @param array<string, string> $args */
    public function list(ServerRequestInterface $request, ResponseInterface $response, array $args): ResponseInterface
    {
        $profileId = Validator::uuid($args['profileId'] ?? null, 'profileId');
        return Json::response($response, [
            'objects' => $this->objects->list($this->accountId($request), $profileId),
        ]);
    }

    /** @param array<string, string> $args */
    public function get(ServerRequestInterface $request, ResponseInterface $response, array $args): ResponseInterface
    {
        [$profileId, $namespace] = $this->path($args);
        $object = $this->objects->get($this->accountId($request), $profileId, $namespace);
        $response->getBody()->write((string) $object['payload']);

        return $response
            ->withHeader('Content-Type', self::CONTENT_TYPE)
            ->withHeader('Content-Disposition', 'attachment')
            ->withHeader('ETag', '"' . (string) $object['etag'] . '"');
    }

    /** @param array<string, string> $args */
    public function put(ServerRequestInterface $request, ResponseInterface $response, array $args): ResponseInterface
    {
        $contentType = strtolower(trim(explode(';', $request->getHeaderLine('Content-Type'), 2)[0]));
        if ($contentType !== self::CONTENT_TYPE) {
            throw new ApiException(415, 'UNSUPPORTED_MEDIA_TYPE', 'Content-Type must be application/vnd.cstv.blob+gzip.');
        }

        $rawSchemaVersion = $request->getHeaderLine('X-Schema-Version');
        if (!preg_match('/^[1-9]\d{0,9}$/D', $rawSchemaVersion) || (int) $rawSchemaVersion > 2_147_483_647) {
            throw new ApiException(422, 'INVALID_SCHEMA_VERSION', 'X-Schema-Version must be a positive integer.');
        }

        [$profileId, $namespace] = $this->path($args);
        $result = $this->objects->put(
            $this->accountId($request),
            $profileId,
            $namespace,
            BinaryBody::read($request, $this->maximumBytes),
            (int) $rawSchemaVersion,
            $request->hasHeader('If-Match') ? $request->getHeaderLine('If-Match') : null,
        );

        return $response
            ->withStatus(204)
            ->withHeader('ETag', '"' . $result['etag'] . '"');
    }

    /** @param array<string, string> $args */
    public function delete(ServerRequestInterface $request, ResponseInterface $response, array $args): ResponseInterface
    {
        [$profileId, $namespace] = $this->path($args);
        $this->objects->delete(
            $this->accountId($request),
            $profileId,
            $namespace,
            $request->hasHeader('If-Match') ? $request->getHeaderLine('If-Match') : null,
        );

        return $response->withStatus(204);
    }

    /** @param array<string, string> $args @return array{string, string} */
    private function path(array $args): array
    {
        return [
            Validator::uuid($args['profileId'] ?? null, 'profileId'),
            Validator::namespace($args['namespace'] ?? null),
        ];
    }

    private function accountId(ServerRequestInterface $request): string
    {
        /** @var array<string, mixed> $account */
        $account = $request->getAttribute('account');
        return (string) $account['id'];
    }
}
