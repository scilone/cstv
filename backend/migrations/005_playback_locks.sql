CREATE TABLE playback_locks (
    account_id   UUID PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE,
    device_id    UUID        NOT NULL,
    device_name  VARCHAR(64) NOT NULL,
    lock_token   UUID        NOT NULL,
    started_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
