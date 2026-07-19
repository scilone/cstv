# Corrections de Bugs (Backlog Actif)

Ce document rassemble les rapports de bugs ouverts, les anomalies de comportement et les régressions constatées à corriger pour l'application.

Une fois qu'un bug est corrigé et validé, sa description/son prompt doit être déplacé dans le fichier d'archive correspondant : `docs/archive/bugs-termines.md` afin d'éviter la surcharge de contexte lors des sessions d'IA.

Chaque ticket de bug possède un identifiant unique préfixé par **B** (ex: `B-1`, `B-2`...).

---

## 🎯 Bugs Actifs

### B-3. Cache des tendances TMDB jamais invalidé quand le catalogue change — Effort S (Priorité Basse)
**Modèle : Haiku 4.5**

**Constat (review v1.48.6).** `TrendingRepositoryImpl` met en cache (SharedPreferences, 24 h) le résultat **matché** tendances↔catalogue, incluant les `VodStream`/`SeriesStream` sérialisés complets. Si le catalogue est resynchronisé pendant ces 24 h (film supprimé du panel, catégorie changée), le carrousel continue d'afficher des items périmés : clic → fiche détail en erreur, ou item d'une catégorie fraîchement masquée avec un `categoryId` obsolète qui échappe au filtre.

**Prompt.**
> Dans `TrendingRepositoryImpl`/`GetTrendingInCatalogUseCase` : invalide le cache global des tendances matchées quand le catalogue local change (au minimum après chaque sync réussie du catalogue — hook dans `SyncCacheUseCase` ou stockage d'un horodatage de sync comparé à celui du cache). En complément, au moment de l'affichage, revalide que chaque `streamId`/`seriesId` matché existe encore dans le cache Room et écarte silencieusement les disparus. Test unitaire : cache présent + catalogue resynchronisé → re-match.
