<?php

declare(strict_types=1);

namespace Cstv\Backend\Http;

use Cstv\Backend\Shared\ApiException;
use Psr\Http\Message\ResponseFactoryInterface;
use Psr\Http\Message\ResponseInterface;
use Psr\Http\Message\ServerRequestInterface;
use Slim\Exception\HttpBadRequestException;
use Slim\Exception\HttpMethodNotAllowedException;
use Slim\Exception\HttpNotFoundException;
use Throwable;

final readonly class ApiErrorHandler
{
    public function __construct(private ResponseFactoryInterface $responseFactory)
    {
    }

    public function __invoke(
        ServerRequestInterface $request,
        Throwable $exception,
        bool $displayErrorDetails,
        bool $logErrors,
        bool $logErrorDetails,
    ): ResponseInterface {
        if ($exception instanceof ApiException) {
            $status = $exception->status;
            $code = $exception->errorCode;
            $message = $exception->getMessage();
        } elseif ($exception instanceof HttpNotFoundException) {
            [$status, $code, $message] = [404, 'NOT_FOUND', 'The requested resource was not found.'];
        } elseif ($exception instanceof HttpMethodNotAllowedException) {
            [$status, $code, $message] = [405, 'METHOD_NOT_ALLOWED', 'The HTTP method is not allowed.'];
        } elseif ($exception instanceof HttpBadRequestException) {
            [$status, $code, $message] = [400, 'INVALID_REQUEST', 'The request is invalid.'];
        } else {
            [$status, $code, $message] = [500, 'INTERNAL_ERROR', 'An unexpected error occurred.'];
            error_log(sprintf('[CSTV] %s: %s', $exception::class, $exception->getMessage()));
        }

        return Json::response($this->responseFactory->createResponse(), [
            'error' => [
                'code' => $code,
                'message' => $message,
            ],
        ], $status);
    }
}
