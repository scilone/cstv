CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    email VARCHAR(320) UNIQUE NOT NULL CHECK (email = lower(email)),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    active_until TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE otp_codes (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL CHECK (email = lower(email)),
    code_hash VARCHAR(64) NOT NULL,
    request_ip VARCHAR(45) NOT NULL,
    attempts_left INTEGER NOT NULL CHECK (attempts_left >= 0),
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX otp_codes_email_created_idx ON otp_codes (email, created_at DESC);
CREATE INDEX otp_codes_ip_created_idx ON otp_codes (request_ip, created_at DESC);

CREATE TABLE profiles (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    name VARCHAR(80) NOT NULL,
    avatar_id INTEGER NOT NULL DEFAULT 0 CHECK (avatar_id >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX profiles_account_idx ON profiles (account_id, created_at, id);

CREATE TABLE profile_objects (
    profile_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    namespace VARCHAR(64) NOT NULL,
    object_key VARCHAR(128) NOT NULL,
    payload BYTEA NOT NULL,
    etag VARCHAR(64) NOT NULL,
    schema_version INTEGER NOT NULL CHECK (schema_version > 0),
    compressed_size INTEGER NOT NULL CHECK (compressed_size >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (profile_id, namespace, object_key)
);

CREATE INDEX profile_objects_listing_idx
    ON profile_objects (profile_id, namespace, updated_at DESC, object_key);

CREATE TABLE sync_changes (
    revision BIGSERIAL PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    profile_id UUID NOT NULL,
    namespace VARCHAR(64) NOT NULL,
    object_key VARCHAR(128) NOT NULL,
    operation VARCHAR(6) NOT NULL CHECK (operation IN ('UPSERT', 'DELETE')),
    etag VARCHAR(64) NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX sync_changes_account_revision_idx ON sync_changes (account_id, revision);
