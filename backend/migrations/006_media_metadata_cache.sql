CREATE TABLE media_metadata_cache (
    cache_key VARCHAR(255) PRIMARY KEY,
    payload JSONB NOT NULL,
    result_status VARCHAR(16) NOT NULL CHECK (result_status IN ('matched', 'not_found')),
    expires_at TIMESTAMPTZ NOT NULL,
    stale_until TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX media_metadata_cache_expiry_idx ON media_metadata_cache (expires_at);
CREATE INDEX media_metadata_cache_stale_until_idx ON media_metadata_cache (stale_until);
