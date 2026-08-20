-- F45 (Tâches 2/3) : adapter TMDB complet + matching multi-passes.
-- Sous-ressources (genres/mots-clés/pays d'origine/titres alternatifs/vidéos/recommandations)
-- stockées en colonnes array/jsonb plutôt qu'en tables de jointure séparées : même information,
-- sans fuite d'identifiant fournisseur, sans multiplier les migrations pour un gain relationnel
-- dont rien ici n'a besoin (jamais interrogées indépendamment de leur média).
ALTER TABLE tmdb_movies
    ADD COLUMN adult BOOLEAN,
    ADD COLUMN original_language VARCHAR(8),
    ADD COLUMN status TEXT,
    ADD COLUMN tagline TEXT,
    ADD COLUMN vote_average NUMERIC(3, 1),
    ADD COLUMN vote_count INTEGER,
    ADD COLUMN genres TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN origin_countries TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN keywords TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN alternative_titles TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN recommendations UUID[] NOT NULL DEFAULT '{}',
    ADD COLUMN videos JSONB NOT NULL DEFAULT '[]',
    ADD COLUMN normalized_title TEXT NOT NULL DEFAULT '';

CREATE INDEX tmdb_movies_normalized_title_idx ON tmdb_movies (normalized_title);

ALTER TABLE tmdb_series
    ADD COLUMN adult BOOLEAN,
    ADD COLUMN original_language VARCHAR(8),
    ADD COLUMN number_of_episodes INTEGER,
    ADD COLUMN number_of_seasons INTEGER,
    ADD COLUMN status TEXT,
    ADD COLUMN tagline TEXT,
    ADD COLUMN vote_average NUMERIC(3, 1),
    ADD COLUMN vote_count INTEGER,
    ADD COLUMN next_episode_to_air DATE,
    ADD COLUMN episode_run_times SMALLINT[] NOT NULL DEFAULT '{}',
    ADD COLUMN genres TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN origin_countries TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN keywords TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN alternative_titles TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN recommendations UUID[] NOT NULL DEFAULT '{}',
    ADD COLUMN videos JSONB NOT NULL DEFAULT '[]',
    ADD COLUMN normalized_title TEXT NOT NULL DEFAULT '';

CREATE INDEX tmdb_series_normalized_title_idx ON tmdb_series (normalized_title);

-- §7.4/§7.5 : jamais peuplées par le sync/backfill, uniquement à l'ouverture d'une fiche série.
CREATE TABLE tmdb_seasons (
    series_external_id UUID NOT NULL REFERENCES external_media (external_id) ON DELETE CASCADE,
    season_number SMALLINT NOT NULL,
    name TEXT NOT NULL,
    overview TEXT,
    poster_path TEXT,
    air_date DATE,
    vote_average NUMERIC(3, 1),
    hydrated_at TIMESTAMPTZ,
    refresh_after TIMESTAMPTZ,
    PRIMARY KEY (series_external_id, season_number)
);

CREATE TABLE tmdb_episodes (
    series_external_id UUID NOT NULL REFERENCES external_media (external_id) ON DELETE CASCADE,
    season_number SMALLINT NOT NULL,
    episode_number SMALLINT NOT NULL,
    name TEXT NOT NULL,
    overview TEXT,
    still_path TEXT,
    air_date DATE,
    runtime_minutes SMALLINT,
    vote_average NUMERIC(3, 1),
    vote_count INTEGER,
    PRIMARY KEY (series_external_id, season_number, episode_number)
);

CREATE INDEX tmdb_episodes_series_season_idx ON tmdb_episodes (series_external_id, season_number);

-- §8.16 : budget fournisseur global, une seule ligne, partagé par toutes les installations.
CREATE TABLE catalog_provider_rate_limit (
    id SMALLINT PRIMARY KEY DEFAULT 1,
    tokens NUMERIC NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT catalog_provider_rate_limit_singleton CHECK (id = 1)
);

INSERT INTO catalog_provider_rate_limit (id, tokens, updated_at) VALUES (1, 5, NOW());
