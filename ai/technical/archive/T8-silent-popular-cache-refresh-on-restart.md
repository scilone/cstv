# T8 - Silent Background Popular TMDB Cache Refresh on Restart

## Informations générales

Type:
Enhancement (Performance / UX Stability)

Status:
RELEASED

Created:
2026-08-01

---

# 1. Description

Currently, when the TMDB Popular Top 10 cache has expired (older than 24 hours), the application fetches fresh results from the TMDB API in the background. Once resolved, these fresh streams immediately replace the displayed "Top 10" streams in the active UI session.

This causes a sudden visual pop / layout shift on the Home Screen where cards in the "Top 10" lists suddenly shift or change content while the user is actively browsing or preparing to click a card.

This enhancement introduces a **"Silent Background Cache Refresh"** strategy:
- If a popular cache exists (even if it is expired/yesterday's data), it is used **immediately** and remains the **only** displayed data for the entire active session.
- The fresh network update is executed silently in parallel to refresh and save the updated data in the persistent cache database/shared preferences.
- The UI is **never** updated with the fresh data during the active session. This ensures absolute stability for the user's focus and zero layout shift.
- The updated data will be seamlessly loaded and displayed only on the **next launch** of the application.

---

# 2. Contexte

* **`HomeViewModel`**: Located in `app/src/main/java/com/cstv/app/presentation/home/HomeViewModel.kt`. It initiates the popular loading job.
* **`GetPopularTop10InCatalogUseCase`**: Located in `app/src/main/java/com/cstv/app/domain/usecase/GetPopularTop10InCatalogUseCase.kt`.
* **`PopularRepository`**: Handles caching of matched popular movies and series.

---

# 3. Spécification fonctionnelle

## Objectif

Éliminer toute perturbation visuelle ou saut de cartes dans la section "Top 10" de l'Accueil pendant qu'un utilisateur parcourt activement l'écran, en décalant l'affichage des données fraîches au prochain démarrage de l'application.

## User stories

- En tant qu'utilisateur parcourant la rangée "Top 10", je ne veux pas que les cartes changent soudainement d'affiche, d'ordre ou de titre sous mes yeux ou sous mon focus de télécommande TV.
- En tant qu'utilisateur ouvrant l'application après 24 heures, je vois immédiatement le dernier Top 10 en cache. Pendant ma session, l'application met à jour ce cache en arrière-plan sans perturber mon écran actuel.
- Lors de ma prochaine ouverture de l'application, je bénéficie de ce Top 10 actualisé de manière transparente.

## Règles métier

1. Au chargement de l'accueil, si un cache du Top 10 (films ou séries) existe, il est renvoyé et affiché immédiatement, peu importe son âge (même s'il a dépassé la durée de fraîcheur nominale de 24h).
2. Si le cache est périmé, une requête d'actualisation réseau est lancée en arrière-plan.
3. Le résultat de cette actualisation réseau est sauvegardé silencieusement dans le cache persistant local.
4. L'état d'affichage de la session courante (`state.popularTopVodStreams` et `state.popularTopSeriesStreams`) n'est **jamais** modifié par cette actualisation réseau en arrière-plan si un cache initial était déjà affiché.
5. Si aucun cache n'existait du tout (très premier lancement), le résultat réseau est affiché dès réception pour éviter une ligne vide persistante.

6. La stabilité vaut pour toute la session du `HomeViewModel` : un nouveau résultat persistant, une recomposition ou un retour sur l'Accueil ne remplace pas les listes Top 10 initialement affichées pendant cette session si elles provenaient d'un cache.
7. La règle s'applique indépendamment aux listes Top 10 Films et Séries : la présence d'un cache pour l'une ne masque ni ne bloque le chargement initial de l'autre si elle est absente.
8. Une actualisation silencieuse ne dégrade pas les données affichées : en cas d'échec, le dernier cache lisible reste la référence de la session et aucune erreur technique n'est exposée dans la rangée Top 10.

## Parcours utilisateur

1. L'utilisateur ouvre l'application et arrive sur l'Accueil.
2. Pour chaque rangée Top 10, l'application lit d'abord le cache local, y compris s'il a dépassé la durée nominale de 24 heures.
3. Si des données sont disponibles, elles sont affichées immédiatement et figées pour la session en cours.
4. Si ce cache est périmé, l'application lance simultanément une mise à jour réseau qui persiste le résultat sans modifier la rangée visible.
5. L'utilisateur navigue, sélectionne une carte ou revient sur l'Accueil : les titres, leur ordre et le focus ne changent pas du fait de cette mise à jour.
6. À la prochaine création de session de l'Accueil après un redémarrage de l'application, le cache mis à jour est lu et devient la nouvelle liste initialement affichée.
7. Si aucune donnée locale n'existe pour une rangée, elle attend le premier résultat réseau disponible puis l'affiche normalement dans cette même session.

## Critères d'acceptation

- [ ] Avec un cache Top 10 expiré, la liste en cache est affichée sans attendre le réseau et conserve exactement son contenu et son ordre pendant toute la session.
- [ ] Une actualisation réseau est effectuée en arrière-plan pour un cache expiré et son résultat est sauvegardé de manière persistante.
- [ ] La fin réussie de cette actualisation ne modifie pas `popularTopVodStreams` ni `popularTopSeriesStreams` lorsqu'ils avaient été initialisés depuis un cache.
- [ ] Après fermeture puis nouveau lancement de l'application, les données sauvegardées par l'actualisation précédente peuvent être affichées comme cache initial.
- [ ] En premier lancement sans cache, le premier résultat réseau réussi est affiché afin que la rangée ne reste pas vide.
- [ ] Si seul le cache Films ou Séries est absent, seule la rangée concernée peut être mise à jour à réception réseau ; l'autre reste stable.
- [ ] Un échec réseau pendant une actualisation silencieuse conserve les données déjà affichées et n'affiche ni erreur intrusive ni état vide artificiel.

## Cas limites et gestion des erreurs

- Un cache présent mais vide ou invalide est traité comme absent pour la rangée concernée ; il ne doit pas figer un état vide au détriment d'un premier chargement réseau réussi.
- Si une actualisation silencieuse renvoie zéro correspondance dans le catalogue local, le cache existant affiché reste intact pour la session. La politique exacte de persistance d'un résultat vide sera définie à l'étape d'architecture afin d'éviter d'écraser involontairement un cache utile.
- En cas de perte de connectivité, timeout, quota TMDB ou réponse malformée, l'échec est silencieux et le cache valide le plus récent reste consultable.
- Si l'utilisateur quitte l'Accueil avant la fin de l'actualisation, la persistance peut se terminer selon le cycle de vie normal de l'application, mais aucun écran détruit ne doit recevoir de mise à jour d'UI.
- Plusieurs demandes concurrentes de chargement ne doivent pas produire un remplacement tardif d'une liste déjà figée pour la session ; la coordination précise sera définie à l'étape d'architecture.

---

# 4. Spécification technique

## Fichiers à modifier

- `app/src/main/java/com/cstv/app/presentation/home/HomeViewModel.kt`
- `app/src/main/java/com/cstv/app/domain/usecase/GetPopularTop10InCatalogUseCase.kt` (ou les repositories sous-jacents)
- Tests unitaires associés

## Modifications suggérées

1. **`GetPopularTop10InCatalogUseCase`** :
   - Modifier la signature d'appel ou introduire un paramètre/mode pour distinguer l'actualisation "silencieuse" (qui met à jour le cache mais ne renvoie rien à afficher de nouveau pour cette session) ou adapter la valeur retournée.
   - Alternativement, le `HomeViewModel` peut gérer cela : s'il détecte qu'un cache (périmé) a pu être chargé au début de la session, il lance le cas d'usage uniquement pour ré-alimenter le cache de manière asynchrone, mais n'applique pas le résultat dans son `HomeState` courant.

2. **`HomeViewModel.kt`** :
   - Avant de lancer le rafraîchissement TMDB, vérifier si un cache est déjà disponible.
   - Si oui :
     1. Afficher immédiatement ce cache.
     2. Lancer la coroutine d'actualisation réseau en mode silencieux : elle appelle le use case (qui résout et persiste le nouveau cache), mais le ViewModel **n'appelle pas** de mise à jour sur `_state.value.copy(popularTopVodStreams = ...)` avec les données fraîches.
   - Si non (premier lancement, cache vide) :
     1. Lancer le chargement réseau.
     2. Mettre à jour l'état de l'UI dès que le résultat est disponible.

---

# 5. Architecture

## Flux de données proposé

```
HomeViewModel.loadHomeData()
 ├─ Lire le cache de popular (Top 10)
 │   ├─ Si présent : 
 │   │   ├─ Afficher immédiatement dans l'UI (popularTopVodStreams / popularTopSeriesStreams)
 │   │   └─ Si expiré (> 24h) :
 │   │       └─ Lancer tâche d'arrière-plan asynchrone qui appelle l'API et met à jour le cache persistant local SILENCIEUSEMENT (sans mettre à jour l'UI)
 │   └─ Si absent (vide) :
 │       └─ Lancer tâche d'arrière-plan qui appelle l'API, persiste le cache ET met à jour l'UI
```

---

# 6. Plan de développement

- [ ] **Tâche 1 — Adapter la détection de présence du cache populaire**
  Permettre de savoir rapidement si un cache populaire est disponible localement (qu'il soit expiré ou non).

- [ ] **Tâche 2 — Ajuster la logique de mise à jour dans `HomeViewModel`**
  Modifier la coroutine de `popularJob` dans `HomeViewModel.loadHomeData()` pour appliquer la règle d'affichage sélective (mise à jour UI uniquement si le cache initial était vide).

- [ ] **Tâche 3 — Écrire les tests unitaires de non-régression**
  Vérifier dans `HomeViewModelTest` que :
  - Un cache préexistant périmé est immédiatement affiché.
  - La tâche asynchrone d'arrière-plan s'exécute mais ne modifie pas l'état UI.
  - Un démarrage à froid (sans cache) effectue bien la mise à jour de l'UI à la réception du résultat réseau.

---

# 7. Review

## Critique

Aucun problème critique identifié.

## Majeur

### T8-R1 — Tout cache présent déclenche un rafraîchissement réseau, même lorsqu'il est frais

- **Description :** les méthodes `cachedMovies()` et `cachedSeries()` lisent volontairement le cache en ignorant son âge, mais ne renvoient aucune information de fraîcheur. `HomeViewModel.loadPopularRow()` appelle alors systématiquement `refreshSilently()` dès que ce cache est non nul. La condition « si le cache est périmé » n'existe plus dans ce flux.
- **Impact :** chaque création ou rechargement de l'Accueil peut lancer deux parcours TMDB multi-pages alors que le cache est encore valide. Cela contourne le TTL de 24 heures, augmente le temps CPU/réseau, le risque de quota TMDB et les écritures persistantes, contrairement aux règles métier 2 et au flux d'architecture spécifié.
- **Correction attendue :** exposer avec le cache son état d'expiration (ou fournir une méthode dédiée `is...CacheExpired`) et ne lancer `refreshMoviesSilently()` / `refreshSeriesSilently()` que pour la rangée dont le cache est réellement expiré. Ajouter le test négatif « cache frais : aucun rafraîchissement ».

### T8-R2 — La liste n'est pas figée pendant toute la session du HomeViewModel

- **Description :** chaque appel à `loadHomeData()` remet les deux listes Popular à `null`, relit le cache persistant, puis le réapplique à l'état. Or `loadHomeData()` est rappelé dans la même instance de `HomeViewModel`, notamment lors d'un changement de préférences de catégories. Si l'actualisation silencieuse a déjà persisté de nouvelles données, ce second chargement les affiche pendant la session courante.
- **Impact :** les titres et leur ordre peuvent changer sans redémarrage de l'application, ce qui viole directement les règles métier 4 et 6 ainsi que le critère de stabilité du focus TV.
- **Correction attendue :** mémoriser séparément, pour Films et Séries, que la rangée a été initialisée pour la session et conserver son snapshot tant que le `HomeViewModel` vit. Un rechargement de la Home ne doit ni effacer ni relire cette rangée, sauf absence initiale toujours non résolue. Ajouter un test appelant deux fois `loadHomeData()` après simulation de la persistance d'un cache différent.

### T8-R3 — Les rafraîchissements silencieux échappent au job coordonné et peuvent se cumuler

- **Description :** `loadPopularRow()` lance le rafraîchissement via un nouveau `viewModelScope.launch`. Ce job n'est pas enfant de `popularJob` et n'est donc pas annulé lorsque `loadHomeData()` exécute `popularJob?.cancel()`. Plusieurs rechargements peuvent lancer plusieurs rafraîchissements Films/Séries concurrents, avec des résultats persistés dans un ordre non déterministe.
- **Impact :** appels TMDB dupliqués, consommation inutile, course à l'écriture du cache et impossibilité pour `popularJob` de remplir son rôle de garde contre les chargements concurrents. Le cas limite sur les demandes concurrentes n'est pas respecté.
- **Correction attendue :** exécuter le rafraîchissement dans la hiérarchie structurée de `popularJob`, ou conserver explicitement des jobs Films/Séries avec garde d'unicité et politique d'annulation claire. Le correctif doit empêcher un second rafraîchissement tant que le premier est actif.

## Mineur

### T8-R4 — Les tests valident le chemin nominal mais pas les invariants de fraîcheur et de session

- **Description :** les tests ajoutés prouvent qu'un cache est affiché, qu'un rafraîchissement est appelé et qu'un démarrage sans cache publie le réseau. Ils ne distinguent pas cache frais/cache expiré, ne rappellent pas `loadHomeData()` dans la même session et ne simulent pas deux demandes concurrentes.
- **Impact :** T8-R1, T8-R2 et T8-R3 restent compatibles avec une suite verte.
- **Correction attendue :** compléter `HomeViewModelTest` et les tests repository/use case avec les scénarios négatifs et concurrents décrits dans les constats majeurs.

## Corrections demandées

- [x] Corriger T8-R1.
- [x] Corriger T8-R2.
- [x] Corriger T8-R3.
- [x] Ajouter la couverture décrite dans T8-R4.

Status: RESOLVED (Étape 7 — 2026-08-01)

T8-R1 : `PopularRepository` expose `isMoviesCacheExpired`/`isSeriesCacheExpired`
(âge nominal + cohérence avec le catalogue courant, sans lire le contenu du
cache) ; `GetPopularTop10InCatalogUseCase` les relaie. `HomeViewModel` ne lance
`refreshMoviesSilently()`/`refreshSeriesSilently()` que si le cache affiché est
réellement périmé — un cache frais ne déclenche plus aucun appel TMDB.

T8-R2 : deux indicateurs de session (`popularVodResolvedForSession`,
`popularSeriesResolvedForSession`) figent chaque rangée dès qu'un résultat
(cache ou réseau à froid) lui a été appliqué. `loadHomeData()` ne relit et ne
republie une rangée déjà figée à aucun rechargement ultérieur dans la même
session (ex : changement de préférences de catégories) ; seule une absence
encore non résolue continue d'être retentée. Le reset de ces indicateurs et
des listes Popular a été déplacé dans le bloc `resetVisibleContent`
(changement de profil), qui seul doit ouvrir une nouvelle session Popular.

T8-R3 : `loadPopularRow` devient une extension de `CoroutineScope` et lance
`refreshSilently()` via ce receveur plutôt que `viewModelScope` — le job de
rafraîchissement est désormais un descendant structuré de `popularJob` et se
trouve annulé avec lui par `popularJob?.cancel()`, ce qui empêche deux
rafraîchissements concurrents pour une même rangée.

T8-R4 : `HomeViewModelTest` ajoute `test_popularCache_freshCacheDoesNotTriggerSilentRefresh`
(cache frais → aucun rafraîchissement) et
`test_popularCache_reloadInSameSessionDoesNotReplayAnAlreadyResolvedRow`
(deuxième `loadHomeData()` dans la même session → rangée déjà figée non
relue, pas de second rafraîchissement). `PopularRepositoryImplTest` et
`GetPopularTop10InCatalogUseCaseTest` couvrent `isMoviesCacheExpired`/
`isSeriesCacheExpired` (frais, périmé par âge, jamais sauvegardé, catalogue
resynchronisé après le cache).

## Vérifications de review

- `./gradlew --no-daemon --max-workers=1 testDebugUnitTest --tests com.cstv.app.data.sync.CatalogSyncManagerImplTest --tests com.cstv.app.domain.usecase.GetPopularTop10InCatalogUseCaseTest --tests com.cstv.app.presentation.home.HomeViewModelTest` : **SUCCESS**.
- `git diff --check` : **SUCCESS** avant consignation de la review.

## Vérifications de l'étape 7

- `./gradlew --no-daemon --max-workers=1 testDebugUnitTest --tests com.cstv.app.data.sync.CatalogSyncManagerImplTest --tests com.cstv.app.domain.usecase.GetPopularTop10InCatalogUseCaseTest --tests com.cstv.app.presentation.home.HomeViewModelTest --tests com.cstv.app.presentation.livetv.LiveTvViewModelTest --tests com.cstv.app.presentation.series.SeriesViewModelTest --tests com.cstv.app.presentation.vod.VodViewModelTest --tests com.cstv.app.data.repository.PopularRepositoryImplTest` : **SUCCESS**.

## Vérifications de l'étape 8

- Tests de non-régression T7/T8 : `./gradlew --no-daemon --max-workers=1 testDebugUnitTest --tests com.cstv.app.data.sync.CatalogSyncManagerImplTest --tests com.cstv.app.data.repository.PopularRepositoryImplTest --tests com.cstv.app.domain.usecase.GetPopularTop10InCatalogUseCaseTest --tests com.cstv.app.presentation.home.HomeViewModelTest --tests com.cstv.app.presentation.livetv.LiveTvViewModelTest --tests com.cstv.app.presentation.series.SeriesViewModelTest --tests com.cstv.app.presentation.vod.VodViewModelTest` : **SUCCESS**.
- `assembleDebug` : **SUCCESS**.
- `lintDebug` : **BLOCKED** par trois erreurs `UnsafeOptInUsageError` dans `presentation/player/core/PlayerDecoderPolicy.kt`, fichier non suivi lié à B16 et hors périmètre de T7/T8. Aucune correction appliquée dans cette étape.

La validation globale requiert un lint vert ; le statut reste donc `REVIEW` jusqu'à correction de ce blocage externe puis relance des vérifications.

---

# 8. Release

Version :
v1.65.0

Commit :
:sparkles: :technologist: :bug: release(catalog-popular-player): deliver dynamic sync, silent popular trends, and TV video rendering fix (v1.65.0)

Date :
2026-08-01
