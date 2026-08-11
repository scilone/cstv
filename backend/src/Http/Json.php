<?php

declare(strict_types=1);

namespace Cstv\Backend\Http;

use Cstv\Backend\Shared\ApiException;
use JsonException;
use Psr\Http\Message\ResponseInterface;
use Psr\Http\Message\ServerRequestInterface;

final class Json
{
    /** @param array<string, mixed> $data */
    public static function response(ResponseInterface $response, array $data, int $status = 200): ResponseInterface
    {
        try {
            $json = json_encode($data, JSON_THROW_ON_ERROR | JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
        } catch (JsonException) {
            throw new ApiException(500, 'INTERNAL_ERROR', 'The response could not be encoded.');
        }

        $response->getBody()->write($json);
        return $response
            ->withStatus($status)
            ->withHeader('Content-Type', 'application/json; charset=utf-8');
    }

    /** @return array<string, mixed> */
    public static function body(ServerRequestInterface $request): array
    {
        $body = $request->getParsedBody();
        if (!is_array($body)) {
            throw new ApiException(400, 'INVALID_JSON', 'A JSON object body is required.');
        }

        return $body;
    }
}
