# Évolutions Techniques & Dette (Backlog Actif)

Ce document rassemble les tâches d'architecture, de sécurité, de performance, de refactoring ou de mise à niveau technique planifiées pour l'application.

Une fois qu'une tâche technique est réalisée et validée, sa description/son prompt doit être déplacé dans le fichier d'archive correspondant : `docs/archive/evolutions-techniques-terminees.md` afin d'éviter la surcharge de contexte lors des sessions d'IA.

---

## 🎯 Évolutions Techniques Actives

### 1. Pagination des catalogues volumineux — Effort L (Priorité Moyenne)

**Constat.** Aucune pagination (pas de Paging 3) : `getVodStreams`/`getSeriesStreams`/`getLiveStreams` chargent la catégorie entière (voire le catalogue "Tout" complet, 10k+ entrées) en `List<T>` en mémoire, mappée dans des LazyColumn/LazyGrid. La FTS (Phase 40) a réglé la recherche, mais l'affichage initial des grosses listes reste coûteux (parsing JSON complet + insert Room + liste mémoire). Sur Android TV bas de gamme, c'est le premier plafond de perf.

**Prompt.**
> Dans l'app Android cstv : introduis une pagination locale sur les listes de catalogue volumineuses, SANS toucher à la stratégie de sync réseau (l'API Xtream ne pagine pas : on continue de télécharger et cacher le catalogue entier en Room ; seule la LECTURE Room et l'UI paginent).
> Utilise Paging 3 (`androidx.paging:paging-runtime` + `paging-compose`, et `room-paging` pour les PagingSource générées par Room) :
> 1. Ajoute aux DAOs concernés (VodDao, SeriesDao, LiveTvDao) des requêtes `PagingSource<Int, Entity>` par catégorie et pour le mode "Tout".
> 2. Expose des `Flow<PagingData<Model>>` dans les repositories, câblés dans Vod/Series/LiveTv ViewModels via `cachedIn(viewModelScope)`.
> 3. Bascule les grilles/listes des écrans Films, Séries, TV et la grille "Voir tout" sur `collectAsLazyPagingItems`.
> 
> Contraintes : préserver la restauration de position de défilement (Phase 20) et les sections horizontales du mode "Tout" (celles-ci affichent peu d'items par rangée : ne paginer que les grilles verticales, garder les rangées horizontales en List simple si plus simple). Mesure avant/après (temps d'affichage + mémoire via Debug.getNativeHeapAllocatedSize ou profiler) sur la plus grosse catégorie et note les chiffres dans le message de commit.

---

## 🔧 Dette Technique Future / Améliorations de Code

*Ajoutez ici vos futures dettes techniques ou tâches d'architecture identifiées lors des prochaines sessions de développement.*
