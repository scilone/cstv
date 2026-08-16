CREATE TABLE catalog_match_attempts (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    ip_key VARCHAR(45) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX catalog_match_attempts_account_created_idx ON catalog_match_attempts (account_id, created_at DESC);
CREATE INDEX catalog_match_attempts_ip_created_idx ON catalog_match_attempts (ip_key, created_at DESC);
