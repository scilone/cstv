-- Le cache statique en mémoire de `TmdbMediaMetadataProvider::genreNames()` ne survit pas d'une
-- requête PHP à l'autre sur de l'hébergement mutualisé (un process par requête) : chaque match
-- ayant des hints de genre retapait TMDB pour une liste de genres qui ne change quasiment jamais.
-- TTL volontairement long (30 jours, appliqué en PHP) : cette donnée est de la taxonomie TMDB fixe.
CREATE TABLE catalog_genre_cache (
    kind VARCHAR(16) NOT NULL CHECK (kind IN ('movie', 'series')),
    locale VARCHAR(8) NOT NULL,
    genres JSONB NOT NULL,
    fetched_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (kind, locale)
);
