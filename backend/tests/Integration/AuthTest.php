<?php

declare(strict_types=1);

namespace Cstv\Backend\Tests\Integration;

use Cstv\Backend\Shared\Config;
use InvalidArgumentException;

final class AuthTest extends IntegrationTestCase
{
    public function testOtpRequestIsGenericAndNeverReturnsOrStoresPlainCode(): void
    {
        $this->createAccount('known@example.com');
        $known = $this->requestOtp('known@example.com');
        $unknown = $this->requestOtp('unknown@example.com');

        self::assertSame(202, $known->getStatusCode());
        self::assertSame($this->json($known), $this->json($unknown));
        self::assertSame(['status' => 'accepted'], $this->json($known));
        $hash = $this->pdo->query("SELECT code_hash FROM otp_codes WHERE email = 'unknown@example.com'")->fetchColumn();
        self::assertNotSame('123456', $hash);
        self::assertSame(64, strlen((string) $hash));
    }

    public function testInvalidOtpDecrementsAttemptCount(): void
    {
        $this->requestOtp('invalid@example.com');
        $response = $this->verifyOtp('invalid@example.com', '654321');

        self::assertSame(400, $response->getStatusCode());
        self::assertSame('INVALID_OTP', $this->json($response)['error']['code']);
        self::assertSame(
            $this->config->otpMaxAttempts - 1,
            (int) $this->pdo->query("SELECT attempts_left FROM otp_codes WHERE email = 'invalid@example.com'")->fetchColumn(),
        );
    }

    public function testExpiredOtpIsRejected(): void
    {
        $this->requestOtp('expired-otp@example.com');
        $this->pdo->exec("UPDATE otp_codes SET expires_at = NOW() - INTERVAL '1 second'");
        $response = $this->verifyOtp('expired-otp@example.com');

        self::assertSame(400, $response->getStatusCode());
        self::assertSame('OTP_EXPIRED', $this->json($response)['error']['code']);
    }

    public function testOtpCanBeConsumedOnlyOnce(): void
    {
        $this->requestOtp('once@example.com');
        self::assertSame(200, $this->verifyOtp('once@example.com')->getStatusCode());
        $second = $this->verifyOtp('once@example.com');

        self::assertSame(400, $second->getStatusCode());
        self::assertSame('OTP_CONSUMED', $this->json($second)['error']['code']);
    }

    public function testVerificationCreatesLowercaseAccountAndDefaultProfile(): void
    {
        $this->requestOtp('  New.User@Example.COM ');
        $response = $this->verifyOtp('new.user@example.com');

        self::assertSame(200, $response->getStatusCode());
        self::assertNotEmpty($this->json($response)['accessToken']);
        self::assertSame('new.user@example.com', $this->pdo->query('SELECT email FROM accounts')->fetchColumn());
        self::assertSame('Profil 1', $this->pdo->query('SELECT name FROM profiles')->fetchColumn());
        self::assertSame(1, (int) $this->pdo->query('SELECT COUNT(*) FROM accounts')->fetchColumn());
        self::assertSame(1, (int) $this->pdo->query('SELECT COUNT(*) FROM profiles')->fetchColumn());
    }

    public function testExistingAccountLogsInWithoutCreatingAnotherProfile(): void
    {
        $this->createAccount('existing@example.com', true, true, 2);
        $this->requestOtp('EXISTING@example.com');
        $response = $this->verifyOtp('existing@example.com');

        self::assertSame(200, $response->getStatusCode());
        self::assertSame(1, (int) $this->pdo->query('SELECT COUNT(*) FROM accounts')->fetchColumn());
        self::assertSame(2, (int) $this->pdo->query('SELECT COUNT(*) FROM profiles')->fetchColumn());
    }

    public function testRequestingAnOtpNeverCreatesAnAccount(): void
    {
        self::assertSame(202, $this->requestOtp('ghost@example.com')->getStatusCode());
        self::assertSame(202, $this->requestOtp('ghost@example.com')->getStatusCode());

        self::assertSame(0, (int) $this->pdo->query('SELECT COUNT(*) FROM accounts')->fetchColumn());
        self::assertSame(0, (int) $this->pdo->query('SELECT COUNT(*) FROM profiles')->fetchColumn());
    }

    public function testFailedVerificationNeverCreatesAnAccount(): void
    {
        $this->requestOtp('ghost2@example.com');
        self::assertSame(400, $this->verifyOtp('ghost2@example.com', '000000')->getStatusCode());

        self::assertSame(0, (int) $this->pdo->query('SELECT COUNT(*) FROM accounts')->fetchColumn());
    }

    public function testAttemptsAreCappedBeforeTheCodeCanBeGuessed(): void
    {
        $this->requestOtp('bruteforce@example.com');
        for ($attempt = 0; $attempt < $this->config->otpMaxAttempts; $attempt++) {
            self::assertSame(400, $this->verifyOtp('bruteforce@example.com', '000000')->getStatusCode());
        }

        $blocked = $this->verifyOtp('bruteforce@example.com');
        self::assertSame(400, $blocked->getStatusCode());
        self::assertSame('OTP_ATTEMPTS_EXCEEDED', $this->json($blocked)['error']['code']);
        self::assertSame(0, (int) $this->pdo->query('SELECT COUNT(*) FROM accounts')->fetchColumn());
    }

    public function testRequestingTooManyOtpsIsRateLimited(): void
    {
        for ($attempt = 0; $attempt < $this->config->otpRequestLimitEmail; $attempt++) {
            self::assertSame(202, $this->requestOtp('flood@example.com')->getStatusCode());
        }

        $blocked = $this->requestOtp('flood@example.com');
        self::assertSame(429, $blocked->getStatusCode());
        self::assertSame('OTP_RATE_LIMITED', $this->json($blocked)['error']['code']);
    }

    public function testPreviousCodeIsInvalidatedByANewRequest(): void
    {
        $this->requestOtp('rotate@example.com');
        $firstId = (string) $this->pdo->query("SELECT id FROM otp_codes WHERE email = 'rotate@example.com'")->fetchColumn();
        $this->requestOtp('rotate@example.com');

        $statement = $this->pdo->prepare('SELECT consumed_at IS NOT NULL FROM otp_codes WHERE id = :id');
        $statement->execute(['id' => $firstId]);
        self::assertTrue(filter_var($statement->fetchColumn(), FILTER_VALIDATE_BOOL));
        self::assertSame(200, $this->verifyOtp('rotate@example.com')->getStatusCode());
    }

    public function testMalformedCodeIsRejectedBeforeAnyDatabaseLookup(): void
    {
        $this->requestOtp('format@example.com');
        $response = $this->verifyOtp('format@example.com', '12345');

        self::assertSame(422, $response->getStatusCode());
        self::assertSame('INVALID_OTP_FORMAT', $this->json($response)['error']['code']);
        self::assertSame(
            $this->config->otpMaxAttempts,
            (int) $this->pdo->query("SELECT attempts_left FROM otp_codes WHERE email = 'format@example.com'")->fetchColumn(),
        );
    }

    public function testOtpTestCodeIsImpossibleInProduction(): void
    {
        $previous = [
            'APP_ENV' => getenv('APP_ENV'),
            'JWT_SECRET' => getenv('JWT_SECRET'),
            'OTP_HASH_SECRET' => getenv('OTP_HASH_SECRET'),
        ];
        putenv('APP_ENV=production');
        putenv('JWT_SECRET=' . str_repeat('a', 48));
        putenv('OTP_HASH_SECRET=' . str_repeat('b', 48));

        try {
            putenv('OTP_TEST_CODE=123456');
            $this->expectException(InvalidArgumentException::class);
            $this->expectExceptionMessage('OTP_TEST_CODE must not be configured in production.');
            Config::fromEnvironment();
        } finally {
            putenv('OTP_TEST_CODE=123456');
            foreach ($previous as $name => $value) {
                $value === false ? putenv($name) : putenv($name . '=' . $value);
            }
        }
    }

    public function testDevelopmentSecretsAreRefusedInProduction(): void
    {
        $previous = [
            'APP_ENV' => getenv('APP_ENV'),
            'OTP_TEST_CODE' => getenv('OTP_TEST_CODE'),
            'JWT_SECRET' => getenv('JWT_SECRET'),
            'OTP_HASH_SECRET' => getenv('OTP_HASH_SECRET'),
        ];
        putenv('APP_ENV=production');
        putenv('OTP_TEST_CODE');
        putenv('JWT_SECRET=change-this-dev-jwt-secret-at-least-32-chars');
        putenv('OTP_HASH_SECRET=change-this-dev-otp-secret-at-least-32-chars');

        try {
            $this->expectException(InvalidArgumentException::class);
            $this->expectExceptionMessage('Development secrets must be replaced in production.');
            Config::fromEnvironment();
        } finally {
            foreach ($previous as $name => $value) {
                $value === false ? putenv($name) : putenv($name . '=' . $value);
            }
        }
    }
}
