-- T16: rate limiting on POST /v1/auth/otp/verify. One append-only row per accepted
-- verification attempt, counted within a sliding window per client IP. The window count
-- ignores older rows. Growth is bounded by a sampled purge (see AuthService::throttleVerify /
-- OtpRepository::purgeExpiredVerifyAttempts) that deletes expired rows across all IPs in a
-- capped batch per call, never the whole backlog at once.
CREATE TABLE auth_verify_attempts (
    id UUID PRIMARY KEY,
    request_ip VARCHAR(45) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Per-IP sliding-window count.
CREATE INDEX auth_verify_attempts_ip_created_idx ON auth_verify_attempts (request_ip, created_at DESC);

-- Leading created_at for the bounded global purge of expired rows (all IPs).
CREATE INDEX auth_verify_attempts_created_idx ON auth_verify_attempts (created_at);
