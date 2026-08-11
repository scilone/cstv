<?php

declare(strict_types=1);

namespace Cstv\Backend\Http\Action;

use Cstv\Backend\Http\Json;
use PDO;
use PDOException;
use Psr\Http\Message\ResponseInterface;
use Psr\Http\Message\ServerRequestInterface;

final readonly class HealthAction
{
    public function __construct(private PDO $pdo)
    {
    }

    /** @param array<string, string> $args */
    public function __invoke(ServerRequestInterface $request, ResponseInterface $response, array $args): ResponseInterface
    {
        try {
            $this->pdo->query('SELECT 1')->fetchColumn();
            return Json::response($response, ['status' => 'ok']);
        } catch (PDOException) {
            return Json::response($response, [
                'error' => [
                    'code' => 'DATABASE_UNAVAILABLE',
                    'message' => 'Database is unavailable.',
                ],
            ], 503);
        }
    }
}
