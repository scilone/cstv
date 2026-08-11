<?php

declare(strict_types=1);

namespace Cstv\Backend\Http;

use Cstv\Backend\Shared\ApiException;
use Psr\Http\Message\ServerRequestInterface;

final class BinaryBody
{
    public static function read(ServerRequestInterface $request, int $maximumBytes): string
    {
        $contentLength = $request->getHeaderLine('Content-Length');
        if ($contentLength !== '' && (!ctype_digit($contentLength) || (int) $contentLength > $maximumBytes)) {
            throw new ApiException(413, 'PAYLOAD_TOO_LARGE', 'Payload exceeds the configured maximum size.');
        }

        $stream = $request->getBody();
        $payload = '';
        while (!$stream->eof()) {
            $chunk = $stream->read(min(8192, $maximumBytes + 1 - strlen($payload)));
            if ($chunk === '') {
                break;
            }
            $payload .= $chunk;
            if (strlen($payload) > $maximumBytes) {
                throw new ApiException(413, 'PAYLOAD_TOO_LARGE', 'Payload exceeds the configured maximum size.');
            }
        }

        return $payload;
    }
}
