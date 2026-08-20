-- F45: CSTV-owned identity, independent from the metadata provider.
CREATE TABLE external_media (
    external_id UUID PRIMARY KEY,
    kind VARCHAR(16) NOT NULL CHECK (kind IN ('movie', 'series')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE tmdb_media (
    external_id UUID PRIMARY KEY REFERENCES external_media(external_id) ON DELETE CASCADE,
    kind VARCHAR(16) NOT NULL CHECK (kind IN ('movie', 'series')),
    tmdb_id BIGINT NOT NULL,
    hydrated_at TIMESTAMPTZ,
    refresh_after TIMESTAMPTZ,
    last_refresh_attempt_at TIMESTAMPTZ,
    UNIQUE(kind, tmdb_id)
);

CREATE INDEX tmdb_media_refresh_after_idx ON tmdb_media(refresh_after);

CREATE TABLE tmdb_movies (
    external_id UUID PRIMARY KEY REFERENCES external_media(external_id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    original_title TEXT,
    overview TEXT,
    poster_path TEXT,
    backdrop_path TEXT,
    release_date DATE,
    runtime_minutes SMALLINT,
    age_rating SMALLINT
);

CREATE TABLE tmdb_series (
    external_id UUID PRIMARY KEY REFERENCES external_media(external_id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    original_name TEXT,
    overview TEXT,
    poster_path TEXT,
    backdrop_path TEXT,
    first_air_date DATE,
    last_air_date DATE,
    in_production BOOLEAN,
    age_rating SMALLINT
);
