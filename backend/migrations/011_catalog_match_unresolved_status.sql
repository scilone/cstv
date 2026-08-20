-- F45 (Tâche 3) : `CatalogMatchEngine` distingue désormais `not_found` (aucun candidat côté
-- fournisseur) d'`unresolved` (des candidats existaient mais aucun n'a franchi le seuil de
-- confiance/marge, §7.11) — un troisième statut que `media_metadata_cache` doit accepter.
ALTER TABLE media_metadata_cache DROP CONSTRAINT media_metadata_cache_result_status_check;
ALTER TABLE media_metadata_cache ADD CONSTRAINT media_metadata_cache_result_status_check
    CHECK (result_status IN ('matched', 'not_found', 'unresolved'));
