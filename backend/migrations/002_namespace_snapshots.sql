-- profile_objects used to store one opaque blob per object key and sync_changes
-- recorded an append-only journal. Opaque blobs cannot be safely aggregated by SQL,
-- so this POC migration intentionally discards the old synchronized blobs. Reload the
-- demo fixtures (or let the application upload its local state) after migrating.
DROP TABLE sync_changes;

DROP TABLE profile_objects;

CREATE TABLE profile_objects (
    profile_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    namespace VARCHAR(64) NOT NULL,
    payload BYTEA NOT NULL,
    etag VARCHAR(64) NOT NULL,
    schema_version INTEGER NOT NULL CHECK (schema_version > 0),
    compressed_size INTEGER NOT NULL CHECK (compressed_size >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (profile_id, namespace)
);
