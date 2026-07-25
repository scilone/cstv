# B12 - Présence de médias issus de catégories masquées dans les carrousels de l'Accueil

## Informations générales

Type:
Bug

Status:
TASK BREAKDOWN

Created:
2026-07-25

Target version:
v1.54.19

Version:
v1.54.19

Date:
2026-07-25

---

# 1. Description

Sur l'écran d'Accueil (Home), l'utilisateur signale que des médias appartenant à des catégories qu'il a explicitement masquées dans ses préférences continuent de s'afficher dans la liste des "Derniers ajoutés" (carrousels Films et Séries).

Ce problème s'avère être une régression d'intégration qui se manifeste notamment après un changement de profil utilisateur (via l'écran de sélection de profils) ou suite à une reconconnexion, où les préférences de masquage du nouveau profil ne sont pas correctement appliquées sur les listes de la Home.

---

# 2. Contexte

Les préférences de masquage et d'ordre des catégories par profil (introduites en Phase 58) sont stockées dans la table Room `category_preferences` et exposées par `CategoryPreferenceRepository`.

Dans `HomeViewModel.kt`, la méthode `loadHomeData()` est chargée d'alimenter les carrousels de l'Accueil :
- `firstVodStreams` (Derniers ajouts Films)
- `firstSeriesStreams` (Derniers ajouts Séries)

Le filtrage y est théoriquement implémenté correctement :
```kotlin
val hiddenVodCategories = hiddenCategoryIds(CategoryType.VOD)
val filteredVodStreams = allVodStreams.filter { it.categoryId !in hiddenVodCategories }
val firstVodStreams = filteredVodStreams.sortedByDescending { ... }.take(20)
```

Cependant, `HomeViewModel` est instancié au niveau de l'activité dans `MainActivity.kt` :
```kotlin
val homeViewModel: HomeViewModel = hiltViewModel()
```
Cela signifie que le ViewModel survit tout au long de la session de l'activité, y compris lorsque l'utilisateur bascule d'un profil à un autre via `ProfileSelectionScreen` ou l'onglet de sélection de profils.

Or, dans son bloc `init`, `HomeViewModel` :
1. Déclenche un chargement unique de l'Accueil via `loadHomeData()`.
2. Observe uniquement les modifications directes de préférences via `categoryPreferenceRepository.changes`.
3. **N'observe aucunement les changements de profil actif.**

Lorsque l'utilisateur change de profil :
- `ProfileManager.currentProfileId()` change.
- `categoryPreferenceRepository.changes` n'émet aucun événement car aucune préférence n'est modifiée à cet instant.
- `HomeViewModel` ne réexécute pas `loadHomeData()`.
- Les listes `firstVodStreams` and `firstSeriesStreams` conservent en mémoire les médias et les filtres de catégories masquées correspondant au **profil précédent** ! Si le profil précédent n'avait aucune catégorie masquée, alors tous les médias (y compris ceux censés être masqués pour le nouveau profil) s'affichent sans aucun filtre.

---

# 3. Objectif

Garantir que l'ensemble des carrousels et des listes de l'Accueil (notamment "Derniers ajoutés", "Top 10" et "Recommandations") reflètent instantanément et de façon stricte les filtres de catégories masquées du profil utilisateur actuellement sélectionné, en réagissant dynamiquement à chaque changement de profil.

---

# 4. Hypothèses

- **Défaut de réactivité au profil actif :** Le maintien en mémoire cache de l'état du profil précédent par le cycle de vie du ViewModel est l'unique cause de cette fuite de catégories masquées.
- **Ré-écoute réactive du profil :** Injecter le `ProfileManager` dans `HomeViewModel` et observer son flux réactif `activeProfileId` (StateFlow) pour déclencher automatiquement un rafraîchissement complet (`loadHomeData()`) à chaque changement de profil résoudra définitivement et proprement la régression.
- **Invalidation des autres caches :** Le rafraîchissement complet devra également invalider les caches asynchrones comme les recommandations de films et séries (`GetRecommendationsUseCase`) pour garantir qu'aucune recommandation du profil précédent ne persiste sur la Home.

---

# 5. Questions ouvertes

1. **Impact sur les favoris et la reprise de lecture :** Les flux réactifs d'observation des favoris (`observeFavorites()`) et de reprise de lecture (`observeAllPlaybackPositions()`) sont-ils correctement actualisés lors du changement de profil ? (Oui, car leurs repositories sous-jacents s'appuient déjà sur un `flatMapLatest` basé sur le profileId actif de `ProfileManager`).
2. **Couplage avec RecentlyAddedViewModel :** L'écran indépendant "Voir tout" (`RecentlyAddedScreen` / `RecentlyAddedViewModel`) souffre-t-il également de ce symptôme s'il reste en backstack ? (Non, car son ViewModel est généralement détruit et recréé lors du retour à l'écran ou réinterrogé via `loadRecentlyAdded` déclenché dans le `LaunchedEffect` de l'écran Compose).

---

# 6. Spécification fonctionnelle

## User stories

- En tant qu'utilisateur, je veux que les médias des catégories que j'ai masquées ne soient jamais proposés sur mon Accueil.
- En tant qu'utilisateur de plusieurs profils, je veux que l'Accueil se mette à jour dès que je sélectionne un autre profil et applique immédiatement ses propres préférences.

## Parcours utilisateur

1. Le profil A masque une ou plusieurs catégories Films et/ou Séries.
2. Depuis les préférences, l'utilisateur revient à l'Accueil ou bascule vers le profil A.
3. Les rangées de l'Accueil sont recalculées avec les catégories visibles du profil A ; aucun média d'une catégorie masquée n'est affiché.
4. L'utilisateur change pour le profil B, ayant des préférences différentes.
5. L'Accueil abandonne l'état calculé pour le profil A et affiche les contenus autorisés pour le profil B, sans conserver de média filtré selon les règles de A.

## Règles métier

- Les préférences de catégories sont strictement propres à chaque profil local.
- Un média rattaché à une catégorie masquée pour le profil actif ne doit apparaître dans aucune rangée ou liste de l'Accueil alimentée par le catalogue : notamment Derniers ajoutés Films, Derniers ajoutés Séries, Top 10, tendances et recommandations.
- Lors d'un changement de profil, le filtrage du nouveau profil a priorité sur l'affichage éventuellement déjà chargé. Un contenu du profil précédent ne doit pas rester visible pendant le rafraîchissement s'il est masqué pour le nouveau profil.
- Une modification de masquage ou de réaffichage de catégorie pour le profil actif actualise l'Accueil avec les mêmes règles, sans nécessiter de redémarrer l'application.
- Les contenus sans catégorie connue ne sont pas assimilés arbitrairement à une catégorie masquée ; ils restent éligibles tant qu'aucune règle de masquage ne les vise.
- Ce correctif ne modifie ni les préférences enregistrées, ni le comportement propre de l'écran « Voir tout », déjà rechargé lors de son affichage.

## Critères d'acceptation

- Étant donné un profil qui masque une catégorie Films, aucun film de cette catégorie n'apparaît dans « Derniers ajoutés », Top 10, tendances ou recommandations de l'Accueil.
- Étant donné un profil qui masque une catégorie Séries, aucune série de cette catégorie n'apparaît dans les rangées équivalentes de l'Accueil.
- Étant donné le passage d'un profil sans catégorie masquée vers un profil qui en masque, les médias désormais interdits disparaissent de l'Accueil sans fermeture ni redémarrage de l'application.
- Étant donné le passage inverse, les médias de catégories autorisées par le nouveau profil redeviennent éligibles selon les règles normales de tri et de disponibilité.
- Étant donné un changement de préférence de catégorie pour le profil actif, les rangées concernées s'actualisent sans conserver d'élément contraire à la nouvelle préférence.
- Les favoris et la reprise de lecture continuent de respecter le profil actif et les catégories masquées après ce rafraîchissement.

## Cas limites et gestion des erreurs

- Si le profil actif n'a aucune préférence enregistrée, l'Accueil applique le comportement par défaut : toutes les catégories connues sont visibles.
- Si une catégorie ou un média a été supprimé du catalogue entre deux chargements, il est simplement absent des rangées, sans message d'erreur utilisateur.
- Si une source enrichie (Top 10, tendance ou recommandation) ne peut pas être associée à un média de catalogue ou à sa catégorie, elle est ignorée plutôt que d'exposer un média d'une catégorie potentiellement masquée.
- Un échec temporaire de chargement ne doit jamais réinjecter des contenus du profil précédent pour contourner le filtrage du profil actif.

---

# 7. Spécification technique

## 7.1 Diagnostic confirmé et périmètre réel du défaut

L'analyse du §2 est confirmée par le code. `HomeViewModel` (`presentation/home/HomeViewModel.kt:179-261`) observe trois sources dans son bloc `init` — `getRecommendationsUseCase.invalidations`, `categoryPreferenceRepository.changes`, et les flux favoris/positions — mais **aucune ne réagit à un changement de profil**.

Le filtrage lui-même est correct partout ; c'est uniquement le **déclenchement** qui manque. Vérifié un par un :

| Rangée Home | Source | Filtrage des catégories masquées | Réagit au changement de profil ? |
|---|---|---|---|
| Derniers ajouts Films / Séries | `loadHomeData()` (`:411-446`) | ✅ `hiddenCategoryIds()` avant tri et `take(20)` | ❌ **non** |
| Top 10 Films / Séries | `TopRatedSelector.selectTop10(filteredVodStreams…)` (`:423`, `:442`) | ✅ dérivé des listes déjà filtrées | ❌ **non** |
| Popular Top 10 (TMDB) | `GetPopularTop10InCatalogUseCase:46-63,112,129` | ✅ filtre appliqué **après** le cache de correspondances | ❌ **non** |
| Tendances (TMDB) | `GetTrendingInCatalogUseCase:133-199` | ✅ filtre appliqué **après** le cache global | ❌ **non** |
| Recommandations | `GetRecommendationsUseCase:110-115` | ✅ + cache déjà clé-é par `cachedProfileId` (`:40,54,166`) | ❌ **non** |
| Rangée TV (1re catégorie live) | `getLiveCategoriesUseCase` (`:394`) | ✅ catégories filtrées/ordonnées Phase 58 | ❌ **non** |
| Continuer à regarder | `combine(observeAllPlaybackPositions, changes)` (`:195-227`) | ✅ | ✅ **oui** (`flatMapLatest` sur `activeProfileId` côté repository) |
| Favoris | `combine(observeFavorites, changes)` (`:228-249`) | ✅ | ✅ **oui** (idem) |

Deux conséquences pour la conception :

- **Aucun use case n'est à corriger.** Les caches TMDB (`getCachedMatchedTrendsGlobal`, `getCachedMatchedMovies/Series`) mémorisent des correspondances *titre TMDB ↔ catalogue*, indépendantes du profil, et le masquage est appliqué en aval à chaque appel. Le cache de `GetRecommendationsUseCase` est déjà invalidé par comparaison `cachedProfileId != currentProfileId`. **Un simple ré-appel de `loadHomeData()` suffit** : l'hypothèse §4 « invalidation des caches asynchrones » est déjà satisfaite par le code existant, aucune API d'invalidation supplémentaire n'est nécessaire.
- **Un second défaut, non couvert par l'hypothèse §4, doit être traité** : `loadHomeData()` ne met `isLoading = true` que si l'état est vide (`:383-386`) et n'efface jamais les listes existantes. Après changement de profil, les rangées du profil précédent **restent affichées** pendant tout le rechargement (dont les appels TMDB, plafonnés à 15 s). Cela viole directement la règle métier « Un contenu du profil précédent ne doit pas rester visible pendant le rafraîchissement s'il est masqué pour le nouveau profil ». Corriger seulement le déclenchement laisserait donc une fenêtre de fuite de plusieurs secondes.

## 7.2 Composants impactés

| Fichier | Modification |
|---|---|
| `presentation/home/HomeViewModel.kt` | Injection de `ProfileManager` ; observation de `activeProfileId` comme unique déclencheur du chargement initial et des rechargements ; ajout du paramètre `resetVisibleContent` à `loadHomeData()` |
| `test/.../HomeViewModelTest.kt` (ou nouveau) | Tests : rechargement au changement de profil, purge de l'état, absence de double chargement au démarrage |

Aucun changement Room / migration, aucun changement réseau, DI ou ProGuard. `ProfileManager` est déjà fourni en `@Singleton` (`di/AppModule.kt:143-149`) et déjà injecté dans d'autres use cases (`GetRecommendationsUseCase:30`).

## 7.3 Modification détaillée de `HomeViewModel`

**Constructeur** — ajout de `private val profileManager: ProfileManager`.

**Bloc `init`** — l'appel direct `loadHomeData()` (`:183`) est remplacé par une collecte du `StateFlow` de profil :

```kotlin
init {
    viewModelScope.launch {
        getRecommendationsUseCase.invalidations.collect { refreshRecommendations() }
    }
    // B12 : le ViewModel est instancié au niveau de l'activité (MainActivity.kt:83)
    // et survit donc au changement de profil. activeProfileId est un StateFlow :
    // sa première émission (valeur courante) assure le chargement initial —
    // elle remplace l'appel direct à loadHomeData(), il ne doit pas être conservé
    // en plus sous peine de doubler le chargement au démarrage.
    viewModelScope.launch {
        profileManager.activeProfileId
            .distinctUntilChanged()
            .collectIndexed { index, _ ->
                loadHomeData(resetVisibleContent = index > 0)
            }
    }
    viewModelScope.launch {
        categoryPreferenceRepository.changes.collect { loadHomeData() }
    }
    // … collectes favoris / positions / ticker EPG inchangées
}
```

`resetVisibleContent = index > 0` : la toute première émission est le chargement initial (rien à purger, et purger provoquerait un `isLoading` inutile) ; les suivantes sont des changements de profil réels et purgent l'affichage.

**Purge de l'état** — nouveau paramètre sur `loadHomeData` :

```kotlin
fun loadHomeData(resetVisibleContent: Boolean = false) {
    if (resetVisibleContent) {
        // Le nouveau profil peut masquer des catégories que le précédent affichait :
        // tout contenu issu du catalogue est abandonné avant le rechargement plutôt
        // que laissé visible pendant les appels TMDB (jusqu'à 15 s).
        _state.update {
            it.copy(
                isLoading = true,
                error = null,
                firstLiveCategory = null,
                firstLiveStreams = emptyList(),
                firstVodStreams = emptyList(),
                firstSeriesStreams = emptyList(),
                topVodStreams = emptyList(),
                topSeriesStreams = emptyList(),
                recommendedMovies = emptyList(),
                recommendedSeries = emptyList(),
                trendingList = emptyList(),
                epgPrograms = emptyMap(),
                trailerPreview = TrailerPreviewUiState.Poster
            )
        }
        cancelTrendingPreview()
    }
    _state.update { it.copy(popularTopVodStreams = null, popularTopSeriesStreams = null) }
    // … suite inchangée
}
```

`resumeWatchingList` et `favoritesList` ne sont **pas** purgées : leurs flux réactifs sont déjà scopés au profil actif via `flatMapLatest` côté repository et ré-émettent d'eux-mêmes la valeur du nouveau profil (cf. tableau §7.1). Les purger provoquerait un vidage puis un re-remplissage visible sans bénéfice.

`cancelTrendingPreview()` est appelé pour couper un chargement de bande-annonce en vol : la bande-annonce visée peut appartenir à une tendance devenue masquée pour le nouveau profil.

## 7.4 Choix techniques et justifications

- **`activeProfileId` comme unique source du chargement, plutôt qu'un `drop(1)` ajouté à côté de l'appel direct.** Un `collect` de `StateFlow` émet immédiatement la valeur courante : conserver `loadHomeData()` en plus déclencherait deux chargements complets au démarrage (dont deux séries d'appels TMDB). Un seul chemin d'entrée supprime cette classe d'erreur.
- **`distinctUntilChanged()` explicite.** `MutableStateFlow` déduplique déjà par `equals`, mais `ProfileManager.setActiveProfileId` est appelé sans garde depuis `ProfileViewModel.selectProfile` : la garde explicite documente l'intention et protège d'un changement d'implémentation (passage à un `SharedFlow`, par exemple).
- **Ordre de déclaration préservé.** `epgInFlight` / `epgLastAttempt` sont déclarés **avant** `init` (`:176-177`) car `viewModelScope` utilise `Dispatchers.Main.immediate` et exécute la coroutine en ligne pendant `init` — le commentaire du code documente le NPE silencieux encouru sinon. La collecte de `activeProfileId` déclenche `loadHomeData()` selon le même mécanisme : **ne pas déplacer ces champs**, et ne pas insérer la nouvelle collecte avant eux.
- **`ProfileManager` (interface) et non `ProfileRepository`.** L'interface est mockable en test unitaire — contrainte explicite d'AGENTS.md, qui documente le NPE d'unboxing Mockito ayant justifié l'extraction de cette interface. `ProfileRepository` exposerait la liste des profils, inutile ici.
- **Pas de `flatMapLatest` autour de tout le chargement.** Élégant sur le papier, mais `loadHomeData()` lance cinq coroutines indépendantes (Popular, Tendances, Recommandations, catalogue local, EPG) avec des durées de vie et des politiques d'erreur distinctes ; les replier dans un unique flux imposerait une réécriture complète du ViewModel, hors périmètre d'un correctif de bug.

## 7.5 Réponses aux questions ouvertes (§5)

1. **Favoris et reprise de lecture** — Confirmé : `FavoritesDao.observeFavorites(profileId)` et `VodDao.observeAllPlaybackPositions(profileId)` sont paramétrées par profil et les repositories les rattachent au `StateFlow` `activeProfileId` (`flatMapLatest`). Ces deux rangées se corrigent seules au changement de profil ; elles sont d'ailleurs le seul endroit de la Home qui fonctionne aujourd'hui. Elles ne sont pas modifiées.
2. **Couplage avec `RecentlyAddedViewModel`** — Confirmé : `RecentlyAddedScreen` est atteinte par la route paramétrée `recently_added/{isSeries}` (`NavGraph.kt:598`), son ViewModel est scopé à l'entrée de navigation et détruit au retour ; le `LaunchedEffect` de l'écran relance `loadRecentlyAdded` à chaque affichage, donc avec les préférences du profil courant. Hors périmètre — **à re-vérifier en validation** néanmoins : le titre de la fiche B12 mentionne cet écran alors que le défaut décrit et corrigé porte sur les carrousels de la Home.

## 7.6 Performances, risques et non-régression

- **Coût du rechargement.** Un changement de profil déclenche un `loadHomeData()` complet : lectures Room (catalogue complet `getVodStreams("all")` / `getSeriesStreams("all")`, plusieurs milliers de lignes), plus les appels TMDB de Popular et Tendances (bornés à 15 s, déjà découplés du spinner). Ce coût est déjà payé à chaque démarrage d'application ; il devient payé aussi à chaque bascule de profil — action rare et explicitement initiée par l'utilisateur. Acceptable.
- **Risque : double chargement au démarrage.** Écarté par construction (un seul point d'entrée), mais à vérifier explicitement en test.
- **Risque : rechargement parasite.** `ProfileManagerImpl` initialise son `StateFlow` depuis les SharedPreferences à la construction ; le passage de `NO_PROFILE` (-1) au profil réel lors du gate de sélection (`MainActivity.kt:129-138`) constitue un changement légitime et déclenchera un rechargement supplémentaire au premier lancement. Sans conséquence fonctionnelle (l'état est de toute façon vide à cet instant, donc `resetVisibleContent` n'induit aucun clignotement).
- **Non-régression** : `./gradlew assembleDebug lintDebug testDebugUnitTest`. Les tests existants de `HomeViewModel`, s'il y en a, doivent être complétés d'un mock `ProfileManager` renvoyant un `MutableStateFlow(1)` — sinon la construction du ViewModel échoue.

## 7.7 Tests à écrire

- Changement de `activeProfileId` → `loadHomeData()` rejoué, et les listes exposées ne contiennent aucun média des catégories masquées du **nouveau** profil.
- Une seule passe de chargement au démarrage (compteur d'appels sur les repositories mockés).
- Purge : entre l'émission du nouveau profil et la fin du rechargement, `firstVodStreams` / `firstSeriesStreams` / `topVodStreams` / `topSeriesStreams` / `trendingList` / `recommendedMovies` / `recommendedSeries` sont vides et `isLoading == true`.
- Passage vers un profil sans préférence enregistrée → aucune catégorie masquée (comportement par défaut du cas limite §6).
- Émission d'une valeur identique de `activeProfileId` → aucun rechargement (`distinctUntilChanged`).
- Non-régression : une modification de préférence sur le profil actif (`categoryPreferenceRepository.changes`) recharge toujours la Home.

---

# 8. Architecture

## Flux de données (après correctif)

```
ProfileSelectionScreen / ProfileViewModel.selectProfile(id)
        │
        ▼
ProfileManagerImpl.setActiveProfileId(id)
        │  (SharedPreferences + MutableStateFlow)
        ▼
ProfileManager.activeProfileId : StateFlow<Int>
        │
        ├──────────────► FavoritesRepositoryImpl / VodRepositoryImpl   (déjà en place)
        │                    flatMapLatest → DAO(profileId)
        │                    └─► HomeState.favoritesList / resumeWatchingList
        │
        └──────────────► HomeViewModel.init  ★ NOUVEAU
                             distinctUntilChanged().collectIndexed
                             └─► loadHomeData(resetVisibleContent = index > 0)
                                   │
                                   ├─ purge des rangées issues du catalogue
                                   │
                                   ├─ getLiveCategoriesUseCase            ─┐
                                   ├─ vod/seriesRepository.getStreams("all")│ filtrage par
                                   ├─ GetPopularTop10InCatalogUseCase      ├─ hiddenCategoryIds()
                                   ├─ GetTrendingInCatalogUseCase          │ du profil ACTIF
                                   └─ GetRecommendationsUseCase           ─┘ (déjà implémenté)
```

## Responsabilités

| Composant | Responsabilité | Changement |
|---|---|---|
| `ProfileManager` | Source de vérité réactive du profil actif | Aucun |
| `HomeViewModel` | S'abonne au profil actif ; purge l'affichage puis relance le chargement complet | **Modifié** |
| `CategoryPreferenceRepository` | Émet sur `changes` lors d'une modification de préférence du profil actif | Aucun |
| Use cases Home (Popular / Trending / Recommandations) | Appliquent le masquage du profil actif au moment de l'appel ; leurs caches sont soit indépendants du profil, soit clé-és par profil | Aucun |
| Repositories Favoris / Positions | Rattachent leurs flux au profil actif via `flatMapLatest` | Aucun |

## Décisions techniques

1. **Corriger le déclencheur, pas le filtrage.** Le masquage est implémenté correctement dans les six sources de la Home ; y ajouter des filtres serait de la duplication défensive. Le défaut est un unique manque d'abonnement.
2. **Purger avant de recharger.** La règle métier interdit qu'un contenu du profil précédent reste visible pendant le rafraîchissement. La stratégie « garder l'ancien contenu tant que le nouveau n'est pas prêt », correcte pour un simple rafraîchissement de catalogue, est incorrecte lors d'une bascule de profil — les deux cas sont désormais distingués par `resetVisibleContent`.
3. **Le ViewModel reste scopé à l'activité.** Le scoper à l'entrée de navigation `home` (ce qui le détruirait au changement de profil et réglerait aussi le symptôme) casserait la conservation d'état voulue de la Home (position de scroll partagée via `homeLazyListState`, EPG déjà chargé, cache des `scrollPositions`) et modifierait le comportement de tous les autres écrans passant par `MainActivity`. Hors périmètre.
4. **Aucun couplage nouveau vers `presentation` depuis `data`.** `ProfileManager` est une interface de `data.local.storage` déjà consommée par la couche `domain` ; l'injecter dans un ViewModel suit le motif existant.

---

# 9. Plan de développement

## Ordre d'exécution

La tâche 1 rend le chargement de la Home réactif au profil et purge les données
potentiellement interdites. La tâche 2 verrouille ce comportement par tests.
La tâche 3 vérifie les rangées réelles et les non-régressions, sans modifier
`RecentlyAddedViewModel`, les use cases Home ou les repositories déjà réactifs.

### Tâche 1 — Recharger et purger la Home lors d'un changement de profil

- [ ] Observer `ProfileManager.activeProfileId` dans `HomeViewModel` et appliquer le reset ciblé.

Objectif :
Remplacer le chargement initial direct par l'unique collecte du profil actif,
recharger à chaque changement réel et vider immédiatement les rangées issues du
catalogue avant que celles du nouveau profil soient disponibles.

Fichiers :

- `presentation/home/HomeViewModel.kt`

Validation :

- La première émission déclenche un seul chargement ; une même valeur de profil
  ne déclenche pas de passe supplémentaire.
- Une bascule de profil vide les listes catalogue, réinitialise l'aperçu de
  tendance et affiche `isLoading` avant le rechargement.
- Favoris et reprise ne sont pas artificiellement purgés ; leurs flux existants
  restent responsables de leur changement de profil.
- Aucun filtre ou cache use case n'est dupliqué ou élargi.

### Tâche 2 — Ajouter les tests de non-régression du `HomeViewModel`

- [ ] Couvrir le changement de profil, la purge et les déclencheurs existants.

Objectif :
Prouver avec un `ProfileManager` mockable que les contenus masqués du nouveau
profil ne restent pas visibles et que la collecte ne double pas le chargement
initial.

Fichiers :

- `app/src/test/java/.../presentation/home/HomeViewModelTest.kt`
- helpers/fakes de test strictement nécessaires.

Validation :

- Changement `activeProfileId` : rechargement, purge immédiate et contenu
  final filtré selon les préférences du nouveau profil.
- Démarrage : une seule passe ; valeur identique : aucune passe supplémentaire.
- Changement de préférence du profil actif : le rechargement existant demeure.
- `./gradlew testDebugUnitTest` passe.

### Tâche 3 — Valider les rangées Home et la non-régression complète

- [ ] Exécuter les contrôles automatisés et les parcours multi-profils sur mobile et TV.

Objectif :
Confirmer que les dernières additions, Top 10, tendances et recommandations
respectent immédiatement le profil actif, sans régression de favoris, reprise,
aperçu trailer ou navigation vers « Voir tout ».

Fichiers :

- fichiers et tests des tâches 1 et 2 ;
- `ai/bugs/B12-recently-added-masked-categories.md` pour les résultats.

Validation :

- `./gradlew testDebugUnitTest`, `./gradlew assembleDebug` et `./gradlew lintDebug` passent.
- Sur mobile et TV : passer d'un profil sans masque à un profil masquant des
  catégories Films et Séries ; aucun média interdit ne reste visible pendant ou
  après le rechargement.
- Vérifier le passage inverse, les favoris, la reprise, la modification d'un
  masque actif et le rechargement indépendant de « Voir tout ».
