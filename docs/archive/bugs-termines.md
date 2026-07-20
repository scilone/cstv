# Archives des Bugs Corrigés et Terminés

Ce document rassemble l'historique de tous les bugs, anomalies et régressions corrigés, validés et archivés.

Chaque ticket possède un identifiant unique préfixé par **B** (ex: `B-1`, `B-2`...).

---

## 📅 Historique des Bugs Corrigés

### B-3. Cache des tendances TMDB jamais invalidé quand le catalogue change — Effort S (Priorité Basse)
**Modèle : Haiku 4.5**

**Constat (review v1.48.6).** `TrendingRepositoryImpl` met en cache (SharedPreferences, 24 h) le résultat **matché** tendances↔catalogue, incluant les `VodStream`/`SeriesStream` sérialisés complets. Si le catalogue est resynchronisé pendant ces 24 h (film supprimé du panel, catégorie changée), le carrousel continue d'afficher des items périmés : clic → fiche détail en erreur, ou item d'une catégorie fraîchement masquée avec un `categoryId` obsolète qui échappe au filtre.

**Correctif (v1.48.17).**
- Ajout de `getStreamById` dans `VodRepository` et `SeriesRepository` pour valider l'existence des films/séries en cache Room.
- Comparaison dynamique de l'horodatage du cache global `trends_time_global_v2` avec le dernier horodatage de synchronisation complète récupéré depuis `SettingsManager`.
- Invalidation automatique du cache global si une resynchronisation de catalogue plus récente a eu lieu.
- Revalidation en direct de chaque média d'accueil au moment du chargement pour filtrer silencieusement les disparus.
- Test unitaire écrit et validé.
