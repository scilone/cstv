<?php

declare(strict_types=1);

namespace Cstv\Backend\Auth;

use Cstv\Backend\Shared\ApiException;
use Cstv\Backend\Shared\Validator;
use Firebase\JWT\JWT;
use Firebase\JWT\Key;
use Throwable;

final readonly class JwtService
{
    public function __construct(private string $secret, private int $ttlSeconds)
    {
    }

    /** @return array{token: string, expiresIn: int} */
    public function issue(string $accountId): array
    {
        $now = time();
        return [
            'token' => JWT::encode([
                'sub' => $accountId,
                'iat' => $now,
                'exp' => $now + $this->ttlSeconds,
            ], $this->secret, 'HS256'),
            'expiresIn' => $this->ttlSeconds,
        ];
    }

    public function accountId(string $token): string
    {
        try {
            $claims = JWT::decode($token, new Key($this->secret, 'HS256'));
            return Validator::uuid($claims->sub ?? null, 'sub');
        } catch (Throwable) {
            throw new ApiException(401, 'INVALID_TOKEN', 'The access token is invalid or expired.');
        }
    }
}
