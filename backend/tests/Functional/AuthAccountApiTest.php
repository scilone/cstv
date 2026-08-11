<?php

declare(strict_types=1);

namespace Cstv\Backend\Tests\Functional;

final class AuthAccountApiTest extends FunctionalTestCase
{
    public function testHealthTraversesHttpAndChecksPostgresql(): void
    {
        $response = $this->api->get('/health');

        self::assertSame(200, $response->status);
        self::assertSame(['status' => 'ok'], $response->json());
        self::assertStringStartsWith('application/json', $response->header('content-type'));
    }

    public function testOtpRequestIsGenericAndDoesNotCreateUnknownAccount(): void
    {
        $this->createAccount('known@example.com');
        $known = $this->requestOtp('known@example.com');
        $unknown = $this->requestOtp('unknown@example.com');

        self::assertSame(202, $known->status);
        self::assertSame($known->body, $unknown->body);
        self::assertSame(['status' => 'accepted'], $unknown->json());
        self::assertSame(1, (int) $this->pdo->query('SELECT COUNT(*) FROM accounts')->fetchColumn());
        $hash = $this->pdo->query("SELECT code_hash FROM otp_codes WHERE email = 'unknown@example.com'")->fetchColumn();
        self::assertSame(64, strlen((string) $hash));
        self::assertNotSame('123456', $hash);
        self::assertStringNotContainsString('123456', $unknown->body);
    }

    public function testInvalidEmailAndOtpFormatsReturnUniformValidationErrors(): void
    {
        $this->assertError($this->requestOtp('not-an-email'), 422, 'INVALID_EMAIL');
        $this->assertError(
            $this->api->postJson('/v1/auth/otp/verify', ['email' => 'valid@example.com', 'code' => '12345']),
            422,
            'INVALID_OTP_FORMAT',
        );
    }

    public function testInvalidExpiredAndConsumedOtpsAreRejected(): void
    {
        $this->requestOtp('otp@example.com');
        $this->assertError($this->verifyOtp('otp@example.com', '654321'), 400, 'INVALID_OTP');

        $this->pdo->exec("UPDATE otp_codes SET expires_at = NOW() - INTERVAL '1 second'");
        $this->assertError($this->verifyOtp('otp@example.com'), 400, 'OTP_EXPIRED');

        $this->requestOtp('once@example.com');
        self::assertSame(200, $this->verifyOtp('once@example.com')->status);
        $this->assertError($this->verifyOtp('once@example.com'), 400, 'OTP_CONSUMED');
    }

    public function testOtpAttemptLimitIsEnforced(): void
    {
        $this->requestOtp('attempts@example.com');
        for ($attempt = 0; $attempt < $this->config->otpMaxAttempts; $attempt++) {
            $this->assertError($this->verifyOtp('attempts@example.com', '000000'), 400, 'INVALID_OTP');
        }

        $this->assertError($this->verifyOtp('attempts@example.com'), 400, 'OTP_ATTEMPTS_EXCEEDED');
        self::assertSame(0, (int) $this->pdo->query('SELECT COUNT(*) FROM accounts')->fetchColumn());
    }

    public function testOtpRequestRateLimitIsEnforcedThroughHttp(): void
    {
        for ($request = 0; $request < $this->config->otpRequestLimitEmail; $request++) {
            self::assertSame(202, $this->requestOtp('rate-limited@example.com')->status);
        }

        $this->assertError(
            $this->requestOtp('rate-limited@example.com'),
            429,
            'OTP_RATE_LIMITED',
        );
    }

    public function testSuccessfulOtpCreatesActiveAccountAndDefaultProfileAtomically(): void
    {
        $email = 'new.account@example.com';
        self::assertSame(202, $this->requestOtp($email)->status);
        $verified = $this->verifyOtp($email);

        self::assertSame(200, $verified->status);
        $token = $verified->json()['accessToken'];
        self::assertIsString($token);
        $me = $this->api->get('/v1/me', $this->auth($token));
        self::assertSame(200, $me->status);
        self::assertSame($email, $me->json()['email']);
        self::assertTrue($me->json()['enabled']);
        self::assertSame('Profil 1', $me->json()['profiles'][0]['name']);
        self::assertSame(0, $me->json()['profiles'][0]['avatarId']);
        self::assertGreaterThan(time() + 360 * 86400, strtotime($me->json()['activeUntil']));
        self::assertLessThan(time() + 370 * 86400, strtotime($me->json()['activeUntil']));
        self::assertSame(1, (int) $this->pdo->query('SELECT COUNT(*) FROM accounts')->fetchColumn());
        self::assertSame(1, (int) $this->pdo->query('SELECT COUNT(*) FROM profiles')->fetchColumn());
    }

    public function testAccountAndFirstProfileCreationRollBackTogether(): void
    {
        $this->pdo->exec(<<<'SQL'
CREATE FUNCTION test_fail_default_profile() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'forced profile failure';
END;
$$;
CREATE TRIGGER test_fail_default_profile BEFORE INSERT ON profiles
FOR EACH ROW EXECUTE FUNCTION test_fail_default_profile();
SQL);

        try {
            $this->requestOtp('atomic@example.com');
            $this->assertError($this->verifyOtp('atomic@example.com'), 500, 'INTERNAL_ERROR');
            self::assertSame(0, (int) $this->pdo->query('SELECT COUNT(*) FROM accounts')->fetchColumn());
            self::assertSame(0, (int) $this->pdo->query('SELECT COUNT(*) FROM profiles')->fetchColumn());
        } finally {
            $this->pdo->exec('DROP TRIGGER IF EXISTS test_fail_default_profile ON profiles');
            $this->pdo->exec('DROP FUNCTION IF EXISTS test_fail_default_profile()');
        }
    }

    public function testExistingAccountAuthenticatesWithoutExtraProfile(): void
    {
        $account = $this->createAccount('existing@example.com', profileCount: 2);
        $this->requestOtp('EXISTING@example.com');
        $verified = $this->verifyOtp('existing@example.com');

        self::assertSame(200, $verified->status);
        self::assertSame(2, (int) $this->pdo->query('SELECT COUNT(*) FROM profiles')->fetchColumn());
        self::assertSame(
            $account['id'],
            $this->api->get('/v1/me', $this->auth($verified->json()['accessToken']))->json()['id'],
        );
    }

    public function testAlreadyIssuedJwtUsesCurrentDatabaseAccountState(): void
    {
        $account = $this->createAccount('state@example.com');
        self::assertSame(200, $this->api->get('/v1/me', $this->auth($account['token']))->status);

        $update = $this->pdo->prepare('UPDATE accounts SET enabled = FALSE WHERE id = :id');
        $update->execute(['id' => $account['id']]);
        $this->assertError(
            $this->api->get('/v1/me', $this->auth($account['token'])),
            403,
            'ACCOUNT_DISABLED',
        );

        $update = $this->pdo->prepare("UPDATE accounts SET enabled = TRUE, active_until = NOW() - INTERVAL '1 day' WHERE id = :id");
        $update->execute(['id' => $account['id']]);
        $this->assertError(
            $this->api->get('/v1/me', $this->auth($account['token'])),
            403,
            'ACCOUNT_EXPIRED',
        );
    }

    public function testMissingAndInvalidBearerTokensAreRejected(): void
    {
        $this->assertError($this->api->get('/v1/me'), 401, 'AUTHENTICATION_REQUIRED');
        $this->assertError(
            $this->api->get('/v1/me', ['Authorization' => 'Bearer not-a-jwt']),
            401,
            'INVALID_TOKEN',
        );
    }
}
