CREATE TABLE account_iptv_credentials (
    account_id UUID PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE,
    key_id     VARCHAR(32) NOT NULL,
    payload    BYTEA       NOT NULL,
    etag       VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
