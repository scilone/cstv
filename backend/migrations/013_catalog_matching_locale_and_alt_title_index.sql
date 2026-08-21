-- T28 étape 7 (review R1) : le backend-first et la réutilisation d'une fiche déjà hydratée après un
-- `/search` TMDB ignoraient la locale de la requête — une fiche hydratée en `fr-FR` pouvait être
-- resservie à une requête `en-US`. On mémorise donc la locale d'hydratation sur `tmdb_media` (une
-- seule fiche par externalId, jamais une par locale — voir §4.2 "Fiche backend stale" : T28 ne
-- multiplie pas les copies, il refuse simplement de réutiliser une fiche dans la mauvaise langue).
ALTER TABLE tmdb_media ADD COLUMN locale TEXT;

-- T28 étape 7 (review R2) : la passe 2 (titre original/alternatif, §8.2) comparait
-- `LOWER(original_title)` et `unnest(alternative_titles)` sans aucun index exploitable — chaque miss
-- sur `normalized_title` forçait un scan complet de la table sur le chemin le plus fréquent (backfill
-- initial). Un index d'expression rend le titre original indexable ; une colonne générée + GIN rend
-- les titres alternatifs indexables via l'opérateur `<@` (containment), sans dupliquer la donnée
-- source ni la maintenir manuellement.
CREATE OR REPLACE FUNCTION cstv_lower_text_array(titles TEXT[]) RETURNS TEXT[] AS $$
    SELECT COALESCE(array_agg(LOWER(title)), ARRAY[]::TEXT[]) FROM unnest(titles) AS title;
$$ LANGUAGE sql IMMUTABLE PARALLEL SAFE;

ALTER TABLE tmdb_movies
    ADD COLUMN alternative_titles_lower TEXT[] GENERATED ALWAYS AS (cstv_lower_text_array(alternative_titles)) STORED;
CREATE INDEX tmdb_movies_original_title_lower_idx ON tmdb_movies (LOWER(original_title));
CREATE INDEX tmdb_movies_alt_titles_lower_gin_idx ON tmdb_movies USING GIN (alternative_titles_lower);

ALTER TABLE tmdb_series
    ADD COLUMN alternative_titles_lower TEXT[] GENERATED ALWAYS AS (cstv_lower_text_array(alternative_titles)) STORED;
CREATE INDEX tmdb_series_original_name_lower_idx ON tmdb_series (LOWER(original_name));
CREATE INDEX tmdb_series_alt_titles_lower_gin_idx ON tmdb_series USING GIN (alternative_titles_lower);
