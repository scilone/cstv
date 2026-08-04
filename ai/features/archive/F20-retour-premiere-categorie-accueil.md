# F20 - Retour à l'affichage de la première catégorie pour les Films et Séries sur l'Accueil

## Informations générales

Status:
VALIDATED

Created:
2026-08-02

---

# 1. Description

Actuellement, sur l'Accueil, les sections dédiées aux **Films (VOD)** et aux **Séries** affichent les 20 derniers ajouts (les nouveautés de l'ensemble du catalogue, triées par date d'ajout décroissante).

L'objectif de cette évolution est de revenir à la version antérieure de l'Accueil : les rangées de Films et de Séries doivent à nouveau présenter le contenu de leur **première catégorie** respective (au lieu des derniers ajouts). La logique d'affichage de la TV en direct (qui présente déjà sa première catégorie) reste inchangée.

---

# 2. Contexte

La modification vers les "derniers ajouts" avait été introduite par le commit `f691c80` (Feature #2 / v1.25.0) pour remplacer le chargement de la première catégorie.

Depuis lors, l'application a grandement évolué (notamment avec la Phase 58 et les préférences de profil) :
* L'ordre des catégories et les catégories masquées sont désormais gérés de façon dynamique et persistés par profil via `CategoryPreferenceRepository`.
* Des Use Cases dédiés (`GetVodCategoriesUseCase` et `GetSeriesCategoriesUseCase`) existent aujourd'hui pour récupérer et trier les catégories d'un profil de façon réactive.
* Utiliser ces Use Cases pour charger le contenu de la première catégorie de Films et Séries permettra de respecter automatiquement les préférences de tri et d'affichage (catégories masquées) du profil actif.

---

# 3. Spécification fonctionnelle

## Objectif

Sur l'Accueil (mobile et TV), remplacer la rangée "Derniers ajouts" des Films et des Séries par une rangée affichant les médias de leur toute première catégorie disponible (non masquée et triée selon les préférences de l'utilisateur).

## User stories

* **En tant qu'utilisateur (mobile et TV)**, lorsque j'ouvre l'Accueil, la section "Films" affiche les films appartenant à ma première catégorie préférée de VOD (ex: "Action" si c'est la première catégorie active de mon profil), plutôt que les nouveautés globales.
* **En tant qu'utilisateur (mobile et TV)**, lorsque j'ouvre l'Accueil, la section "Séries" affiche les séries appartenant à ma première catégorie préférée de Séries, au lieu des nouveautés globales.
* **En tant qu'utilisateur**, si je modifie l'ordre de mes catégories ou que je masque certaines catégories dans les paramètres de mon profil, la Home s'actualise pour présenter le contenu de la nouvelle "première" catégorie active.

## Règles métier et d'interaction

1. **Identification de la première catégorie** :
   * Pour les Films (VOD), récupérer les catégories via `GetVodCategoriesUseCase.once()`. Prendre la première catégorie de la liste obtenue (qui exclut déjà les catégories masquées et respecte l'ordre personnalisé du profil).
   * Pour les Séries, faire de même via `GetSeriesCategoriesUseCase.once()`.
2. **Chargement du contenu** :
   * Si une première catégorie est identifiée, charger ses flux associés depuis le cache local (`vodRepository.getCachedVodStreams(categoryId)` / `seriesRepository.getCachedSeriesStreams(categoryId)`).
   * Si aucune catégorie n'est disponible (ex: toutes masquées ou catalogue vide), la section correspondante sur la Home reste vide ou masquée.
3. **Limite d'éléments** :
   * Conserver la limite d'affichage standard de la rangée sur l'Accueil (ex: 20 éléments).

## Critères d'acceptation (Fonctionnels)

- [ ] Sur l'Accueil, la section Films affiche les films de la première catégorie triée et visible du profil.
- [ ] Sur l'Accueil, la section Séries affiche les séries de la première catégorie triée et visible du profil.
- [ ] Modifier l'ordre des catégories ou masquer la première catégorie actuelle dans les Paramètres met bien à jour la Home avec la nouvelle première catégorie lors du retour sur l'Accueil.
- [ ] Le lien "Voir tout" à côté de ces sections redirige correctement vers la catégorie concernée.

## Décisions de conception et choix utilisateur

* **Format du titre de la section** :
  * **Décision (Validée par l'utilisateur)** : **Option B (Titres génériques)**. Conserver les titres de section génériques actuels (`"Films"`, `"Séries"`), même si le contenu affiché est celui de la première catégorie (ce qui est parfaitement cohérent avec la TV en direct qui affiche sa première catégorie sous le titre générique `"TV en direct"`). Les fichiers de ressources `strings.xml` ne seront donc pas altérés pour cette partie.

---

# 4. Spécification technique

## État actuel du code

Dans `presentation/home/HomeViewModel.kt`, `loadHomeData()` → `catalogJob`
(l. 596-632) fait aujourd'hui, pour les Films puis à l'identique pour les Séries :

```kotlin
val allVodStreams = vodRepository.getCachedVodStreams("all")          // tout le catalogue
val hiddenVodCategories = hiddenCategoryIds(CategoryType.VOD)
val filteredVodStreams = allVodStreams.filter { it.categoryId !in hiddenVodCategories }
val firstVodStreams = filteredVodStreams
    .sortedByDescending { it.added?.toLongOrNull() ?: 0L }            // tri CPU global
    .take(20)
val topVodStreams = TopRatedSelector.selectTop10(filteredVodStreams, …)
```

La rangée TV en direct suit déjà, elle, le modèle cible (l. 580-594) :
`getLiveCategoriesUseCase.once()` → `firstOrNull()` →
`liveTvRepository.getCachedLiveStreams(firstLiveCat.categoryId)`. F20 aligne
Films et Séries sur ce modèle.

## Composants impactés

| Fichier | Modification |
| --- | --- |
| `presentation/home/HomeViewModel.kt` | Injection de `GetVodCategoriesUseCase` et `GetSeriesCategoriesUseCase` ; remplacement du calcul « derniers ajouts » par la lecture de la première catégorie ; deux nouveaux champs d'état ; **suppression du Top 10 de repli et des deux lectures `"all"`**. |
| `presentation/home/HomeScreen.kt` | `onSeeAll` des sections `home_vod` / `home_series` : cible la catégorie au lieu de `recently_added`. Nouveaux paramètres de callback. **`displayedTopVodStreams` / `displayedTopSeriesStreams` ne lisent plus que la source TMDB.** |
| `domain/model/TopRatedSelector.kt` | **Supprimé** (plus aucun appelant). |
| `app/src/test/.../TopRatedSelectorTest.kt` | **Supprimé** avec l'objet qu'il couvre. |
| `presentation/navigation/NavGraph.kt` | Implémentation des nouveaux callbacks (sélection de catégorie + navigation d'onglet) ; lecture/consommation de la catégorie en attente dans les routes `movies` et `series`. |
| `presentation/vod/VodViewModel.kt` | Nouvelle fonction `selectCategoryById(categoryId: String)`. |
| `presentation/series/SeriesViewModel.kt` | Idem. |
| `presentation/navigation/MobileNavigation.kt` | Deux constantes de clés `savedStateHandle` (voir Architecture). |

## Modèles de données

Aucun changement Room, aucune migration (base inchangée en version 21), aucun
DTO, aucun modèle `domain` modifié. Seul `HomeState` évolue :

```kotlin
data class HomeState(
    …
    val firstVodCategory: VodCategory? = null,        // NOUVEAU
    val firstSeriesCategory: SeriesCategory? = null,  // NOUVEAU
    val firstVodStreams: List<VodStream> = emptyList(),      // sémantique changée
    val firstSeriesStreams: List<SeriesStream> = emptyList(),// sémantique changée
    // val topVodStreams / topSeriesStreams                  ← SUPPRIMÉS
)
```

Les deux nouveaux champs servent exclusivement à la cible du lien « Voir tout ».
Ils sont remis à `null` par le bloc `resetVisibleContent` de `loadHomeData()`
(l. 429-447), au même titre que `firstLiveCategory`.

## Nouveau calcul dans `catalogJob`

```kotlin
// Films — aligné sur le traitement déjà en place pour la TV en direct
val vodCategories = try { getVodCategoriesUseCase.once() } catch (e: Exception) {
    if (e is CancellationException) throw e; emptyList()
}
val firstVodCat = vodCategories.firstOrNull()
val firstVodStreams = if (firstVodCat != null) {
    try { vodRepository.getCachedVodStreams(firstVodCat.categoryId).take(HOME_ROW_LIMIT) }
    catch (e: Exception) { if (e is CancellationException) throw e; emptyList() }
} else emptyList()
```

`HOME_ROW_LIMIT = 20`, constante privée du fichier, reprenant la limite
actuelle (règle métier 3 de l'étape 2). Le traitement Séries est symétrique via
`getSeriesCategoriesUseCase.once()` et `seriesRepository.getCachedSeriesStreams(...)`.

**Le filtrage par catégories masquées disparaît de ce chemin** : `once()` de
`GetVodCategoriesUseCase` applique déjà `applyCategoryPreferences(...)`, qui
exclut les catégories masquées et applique l'ordre du profil. Une catégorie
retenue par `firstOrNull()` est donc par construction visible ; refiltrer ses
flux serait redondant.

## API, services

Aucun appel réseau ajouté ni retiré. `GetVodCategoriesUseCase` et
`GetSeriesCategoriesUseCase` sont des lectures locales strictes (leur KDoc :
« lecture locale observable, jamais réseau »). Aucune nouvelle règle
`-keep` proguard (aucune interface Retrofit touchée).

## Stockage et cache

Lecture seule du cache Room existant, via
`vodRepository.getCachedVodStreams(categoryId)` /
`seriesRepository.getCachedSeriesStreams(categoryId)`, qui délèguent à
`vodDao.getStreamsByCategory(categoryId)` (`VodRepositoryImpl` l. 281-283).
Aucune écriture, aucune invalidation, aucun TTL modifié.

## Performances

Gain direct et mesurable côté Films/Séries de l'accueil :

| | Avant | Après |
| --- | --- | --- |
| Lignes Room lues | tout le catalogue VOD + tout le catalogue Séries | les seules lignes des deux premières catégories |
| Tri CPU | `sortedByDescending` sur des dizaines de milliers d'éléments, ×2, **plus** `TopRatedSelector.selectTop10` sur les mêmes listes, ×2 | aucun |
| Allocation | 2 listes complètes du catalogue en mémoire | 2 listes bornées par la taille d'une catégorie |

## Suppression du Top 10 de repli — extension de périmètre validée

Le calcul du Top 10 de repli (`TopRatedSelector.selectTop10`, `HomeViewModel`
l. 609-613 et 628-632) consommait la même liste complète du catalogue : sans
autre décision, `getCachedVodStreams("all")` / `getCachedSeriesStreams("all")`
seraient restés nécessaires et F20 n'aurait supprimé que le **tri** global, pas
la **lecture** globale — laissant le goulot n° 1 de T9 à moitié levé.

**Décision PO du 2026-08-02 : le Top 10 de repli est supprimé.** Extension
explicite du périmètre de F20, actée ici plutôt qu'appliquée en silence.

Conséquences précises :

1. Les rangées « Top 10 Films » et « Top 10 Séries » ne sont plus alimentées que
   par TMDB (`popularTopVodStreams` / `popularTopSeriesStreams`, feature F9/T8).
   `HomeScreen.kt` l. 103-104 devient :
   ```kotlin
   val displayedTopVodStreams    = state.popularTopVodStreams.orEmpty()
   val displayedTopSeriesStreams = state.popularTopSeriesStreams.orEmpty()
   ```
   Les sections restent conditionnées par `isNotEmpty()` (l. 527, 618) : **sans
   réponse TMDB exploitable, les rangées Top 10 n'apparaissent tout simplement
   pas.** C'est le changement fonctionnel à assumer (hors ligne, quota TMDB
   atteint, aucun appariement).
2. `HomeState.topVodStreams` et `HomeState.topSeriesStreams` sont supprimés,
   ainsi que leur remise à zéro dans `resetVisibleContent` (l. 437-438).
3. `TopRatedSelector` (`domain/model/TopRatedSelector.kt`) n'a plus aucun
   appelant en production. Il est **supprimé**, avec son test
   `TopRatedSelectorTest` — conserver un objet mort et son test donnerait
   l'illusion d'une fonctionnalité vivante.
4. `HomeViewModelTest` l. 323
   (`assertEquals(listOf(fallbackMovie), viewModel.state.value.topVodStreams)`)
   couvre précisément le repli supprimé : ce test est retiré. **Signalé
   explicitement** conformément à `AGENTS.md` (« jamais supprimer ni désactiver
   un test pour faire passer le build sans validation explicite ») — la
   suppression découle de la décision PO ci-dessus, pas d'un échec de build.
5. Les deux `getCachedVodStreams("all")` / `getCachedSeriesStreams("all")` de
   `catalogJob` disparaissent complètement : **plus aucune lecture globale du
   catalogue au chargement de l'Accueil**. Le goulot n° 1 de T9 est donc
   entièrement levé par F20, et T9 n'a plus qu'à traiter le goulot n° 2
   (positions de lecture).

## Sécurité

Sans objet — lecture de cache local, aucune donnée sensible, aucun identifiant.

## Compatibilité

* **Mobile et TV** : comportement identique sur les deux plateformes (la règle
  est portée par le ViewModel, partagé). Sur TV le lien « Voir tout » n'est de
  toute façon pas rendu (`HomeSectionRow`, `HomeScreen.kt` l. 849 :
  `if (onSeeAll != null && !isTv)`) ; le changement de cible ne concerne donc
  que le mobile.
* **Préférences de catégories (Phase 58)** : la Home est déjà rechargée sur
  `categoryPreferenceRepository.changes` (`HomeViewModel` l. 257-261). Masquer
  ou réordonner une catégorie recalcule donc « la première catégorie » sans
  code supplémentaire — le critère d'acceptation n° 3 est satisfait par le
  mécanisme existant.
* **Profils** : `once()` lit les préférences du profil actif ; le changement de
  profil déclenche déjà `loadHomeData(resetVisibleContent = true)` (l. 242-254).
* **Base Room** : version 21, inchangée.

## Dépendances

Aucune dépendance Gradle ajoutée. Les deux `UseCase` injectés existent déjà et
sont fournis par Hilt.

## Risques techniques

| Risque | Mitigation |
| --- | --- |
| Toutes les catégories masquées → `firstOrNull()` = `null` | `firstVodStreams` reste vide, et la section est déjà conditionnée par `if (state.firstVodStreams.isNotEmpty())` (`HomeScreen.kt` l. 498) : la rangée disparaît proprement. Comportement identique à celui déjà en place pour la TV en direct. |
| Catégorie très volumineuse (plusieurs milliers de films) | `.take(20)` borne la liste transmise à l'UI ; la lecture Room reste celle d'une seule catégorie, très inférieure au `"all"` actuel. |
| Route `recently_added/{isSeries}` devenue orpheline | `RecentlyAddedScreen` reste atteignable et n'est **pas** supprimé (hors périmètre F20) ; seule la cible du lien change. À réévaluer dans un ticket ultérieur si l'écran n'a plus aucun point d'entrée. |
| Catégorie en attente rejouée au retour sur l'onglet | Consommation explicite (remise à `null` du `savedStateHandle` après application) — voir Architecture, décision 3. |
| Rangées « Top 10 » absentes hors ligne ou si TMDB échoue | Conséquence directe et acceptée de la décision PO du 2026-08-02. Atténuation existante : T8 fige le cache Popular pour la session et le persiste, donc une réponse TMDB obtenue une fois reste affichée aux démarrages suivants, y compris hors ligne. Le cas réellement dégradé est le tout premier lancement sans réseau. |
| Suppression d'un test existant (`HomeViewModelTest` l. 323) | Signalée explicitement en section 4 ; elle accompagne la suppression de la fonctionnalité couverte, pas un contournement d'échec de build. |
| Double sémantique du nom `firstVodStreams` | Le champ redevient conforme à son nom (« flux de la première catégorie ») : c'est l'usage « derniers ajouts » introduit par `f691c80` qui était le contresens. Aucun renommage nécessaire. |

## Contraintes de performance

Le chargement de l'accueil doit rester non bloquant : les deux nouveaux
`once()` sont des lectures Room `suspend` exécutées dans `catalogJob`
(`viewModelScope`), au même endroit et dans le même ordre que l'appel
`getLiveCategoriesUseCase.once()` existant. Aucun appel sur le thread principal.

---

# 5. Architecture

## Position dans la Clean Architecture

La règle « la Home montre la première catégorie visible du profil » est une
règle de présentation appliquée à des `UseCase` `domain` existants. Aucune
nouvelle interface `domain`, aucune implémentation `data`, aucun accès direct au
DAO depuis `presentation`.

```
domain/usecase/
├── GetVodCategoriesUseCase.once()      ← existant, réutilisé
├── GetSeriesCategoriesUseCase.once()   ← existant, réutilisé
└── GetLiveCategoriesUseCase.once()     ← modèle de référence, inchangé

domain/repository/
├── VodRepository.getCachedVodStreams(categoryId)        ← existant
└── SeriesRepository.getCachedSeriesStreams(categoryId)  ← existant

presentation/
├── home/HomeViewModel.kt   ← applique la règle, publie firstVod/SeriesCategory
├── home/HomeScreen.kt      ← "Voir tout" cible la catégorie
├── navigation/NavGraph.kt  ← relaie la catégorie vers l'onglet
├── vod/VodViewModel.kt     ← selectCategoryById()
└── series/SeriesViewModel.kt
```

## Flux de données — chargement de la rangée

```
loadHomeData()  (démarrage, changement de profil, changement de préférences)
      │
      ▼ catalogJob (viewModelScope)
      │
      ├─ getLiveCategoriesUseCase.once() ──► firstLiveCat ──► getCachedLiveStreams(id)
      │                                                        (inchangé)
      ├─ getVodCategoriesUseCase.once()  ──► firstVodCat  ──► getCachedVodStreams(id).take(20)
      │        ▲                                                        │
      │        └── applique déjà : catégories masquées exclues,         │
      │            ordre du profil respecté                             │
      │                                                                 ▼
      └─ getSeriesCategoriesUseCase.once() ─► firstSeriesCat ─► getCachedSeriesStreams(id).take(20)
                                                                        │
                                                                        ▼
                                          _state.update { firstVodCategory, firstVodStreams,
                                                          firstSeriesCategory, firstSeriesStreams }
                                                                        │
                                                                        ▼
                                    HomeScreen : sections "home_vod" / "home_series"
                                    (titres génériques "Films"/"Séries" — décision B, étape 2)
```

## Flux de données — lien « Voir tout » (mobile uniquement)

```
HomeScreen : onSeeAll = { onNavigateToVodCategory(state.firstVodCategory) }
      │
      ▼
NavGraph :  rootEntry.savedStateHandle[PENDING_VOD_CATEGORY] = category.categoryId
            navController.navigateToRootTab("movies")
      │
      ▼
route "movies" :
      val rootEntry = remember { navController.getBackStackEntry(MobileNavigation.ROOT_ROUTE) }
      val pending by rootEntry.savedStateHandle
            .getStateFlow<String?>(PENDING_VOD_CATEGORY, null)
            .collectAsStateWithLifecycle()
      LaunchedEffect(pending) {
          pending?.let {
              vodViewModel.selectCategoryById(it)
              rootEntry.savedStateHandle[PENDING_VOD_CATEGORY] = null   // consommé une fois
          }
      }
      │
      ▼
VodViewModel.selectCategoryById(id) :
      state.categories.find { it.categoryId == id }?.let(::selectCategory)
      → VodScreen bascule en mode "Catégorie spécifique" (grille paginée complète)
```

`rememberTabViewModelOwner` résout déjà `navController.getBackStackEntry(MobileNavigation.ROOT_ROUTE)`
(`NavGraph.kt` l. 133-137) : `VodViewModel` et `SeriesViewModel` sont donc portés
par l'entrée racine et **survivent à la navigation entre onglets**. La catégorie
sélectionnée reste appliquée au retour, exactement comme une sélection manuelle.

## Responsabilités des composants

* **`GetVodCategoriesUseCase` / `GetSeriesCategoriesUseCase`** : source unique de
  la liste ordonnée et filtrée des catégories du profil. Ni le ViewModel ni
  l'écran ne réimplémentent le masquage ou le tri.
* **`HomeViewModel`** : traduire « première catégorie » en contenu affichable et
  publier la catégorie retenue pour que l'UI sache où pointer. Aucune
  connaissance de la navigation.
* **`HomeScreen`** : rendre les rangées et déclencher un callback typé, sans
  connaître ni la route ni le ViewModel cible.
* **`NavGraph`** : seul endroit qui connaît à la fois les routes, les
  `ViewModelStoreOwner` et le transport de la catégorie en attente.
* **`VodViewModel` / `SeriesViewModel`** : appliquer une sélection de catégorie
  par identifiant, en réutilisant `selectCategory()` déjà existant.

## Décisions techniques

1. **Réutiliser `once()` plutôt que le `Flow` `invoke()`.** `catalogJob` est un
   chargement ponctuel ; la réactivité aux changements de préférences est déjà
   assurée en amont par le collecteur de `categoryPreferenceRepository.changes`
   (l. 257-261). Utiliser le `Flow` ici créerait deux mécanismes de
   rafraîchissement concurrents pour le même événement.
2. **Ne pas refiltrer les catégories masquées après `once()`.** Le filtrage est
   la responsabilité du `UseCase` (`applyCategoryPreferences`) ; le dupliquer
   dans le ViewModel serait une seconde source de vérité à maintenir.
3. **Transport de la catégorie par `savedStateHandle` de l'entrée racine plutôt
   que par argument de route.** Ajouter `movies?categoryId={id}` obligerait à
   modifier `navigateToRootTab` (qui compare des routes littérales et fait un
   `popBackStack(route)`, `MobileNavigation.kt` l. 22-38) et rendrait la
   catégorie persistante dans la route — donc réappliquée à chaque retour sur
   l'onglet. Le `savedStateHandle` donne une sémantique « consommé une fois »
   explicite, sans toucher au graphe de navigation.
4. **Ne pas instancier `VodViewModel` depuis la route `home`.** Obtenir le
   ViewModel via `hiltViewModel(...)` dans le composable d'accueil pour appeler
   `selectCategory` directement le construirait dès l'affichage de la Home
   (`observeCategories`, `triggerSilentSyncIfStale`) : exactement la latence de
   démarrage que T9 cherche à réduire. Le relais par `savedStateHandle` est
   inerte tant que l'onglet n'est pas ouvert.
5. **Titres génériques conservés.** Décision B validée à l'étape 2 : aucune
   ressource `strings.xml` modifiée, cohérent avec la rangée « TV en direct »
   qui affiche déjà sa première catégorie sous un titre générique.
6. **Top 10 sans repli local.** Décision PO du 2026-08-02 : les rangées Top 10
   restent rendues, mais uniquement depuis TMDB ; le repli catalogue et
   `TopRatedSelector` sont supprimés pour éliminer les dernières lectures
   globales de `catalogJob`.

## Stratégie de tests

Couverture ciblée sur la logique ViewModel, entièrement en JVM
(`./gradlew testDebugUnitTest`), dans `HomeViewModelTest` existant :

1. **Première catégorie affichée** — `GetVodCategoriesUseCase.once()` mocké
   renvoie `[Action, Comédie]`, `getCachedVodStreams("Action")` renvoie 30 films
   → `state.firstVodStreams` contient les 20 premiers films d'`Action`, et
   `state.firstVodCategory?.categoryId == "Action"`.
2. **Idem Séries** avec `GetSeriesCategoriesUseCase`.
3. **Aucune catégorie disponible** — `once()` renvoie une liste vide →
   `firstVodStreams` vide et `firstVodCategory == null`, sans exception.
4. **Aucune lecture globale du catalogue au chargement de l'Accueil** —
   `verify(vodRepository, never()).getCachedVodStreams("all")` et l'équivalent
   Séries, sur l'intégralité de `loadHomeData()`. C'est le test qui garde
   l'optimisation dans le temps, et le pendant du test n° 11 de T9.
4 bis. **Top 10 sans repli** — `popularTopVodStreams = null` (TMDB muet) →
   `displayedTopVodStreams` vide, aucune rangée Top 10, et aucun appel catalogue
   déclenché pour tenter de la remplir.
5. **Réordonnancement des préférences** — une nouvelle émission de
   `categoryPreferenceRepository.changes` avec `[Comédie, Action]` fait basculer
   `firstVodCategory` sur `Comédie`.
6. **Repli sur erreur** — `getCachedVodStreams` lève : la rangée reste vide et
   `state.error` n'est pas positionné (repli silencieux, cohérent avec le
   traitement Live existant).

Le lien « Voir tout » relève de la navigation Compose : sa vérification de bout
en bout exigerait un device/instrumentation, donc exclue des critères de
validation de l'agent (`AGENTS.md`). La partie testable en JVM est
`selectCategoryById()` : un test dans `VodViewModelTest` vérifiant qu'un
identifiant connu sélectionne la catégorie et qu'un identifiant inconnu est un
no-op sans exception.

---

# 6. Plan de développement

## Ordre d'exécution

La donnée de première catégorie est établie avant le rendu et la navigation ;
F20 est à livrer avant T9, dont il élimine le premier chargement global.

### Tâche 1 — Exposer la première catégorie et ses médias dans l'état Accueil

- [x] Charger les catégories filtrées, lire seulement la première catégorie et
  publier ses médias limités ainsi que son identifiant dans `HomeState`.

Objectif : remplacer les lectures globales Films/Séries et supprimer le repli
Top 10 sur le catalogue lorsque TMDB ne répond pas.

Fichiers : `HomeViewModel.kt`, `HomeState.kt`, use cases et repositories déjà
identifiés dans la spécification technique.

Validation : catégorie vide/erreur = rangée silencieusement absente ; aucun
`getCached*Streams("all")` n'est déclenché par `catalogJob`.

### Tâche 2 — Rendre les rangées et relier « Voir tout » à la catégorie

- [x] Adapter les sections Films/Séries de l'Accueil, avec callback typé et
  navigation mobile consommant une catégorie en attente une seule fois.

Objectif : afficher le contenu de la première catégorie sous les titres
génériques et ouvrir cette même catégorie au clic sans instancier prématurément
les ViewModels catalogue.

Fichiers : `HomeScreen.kt`, composants Home concernés, `NavGraph.kt`,
`VodViewModel.kt`, `SeriesViewModel.kt`.

Validation : TV ne reçoit pas de lien « Voir tout » ; un identifiant connu est
sélectionné, un inconnu est un no-op, et le retour d'onglet conserve l'état.

### Tâche 3 — Couvrir l'état et la non-régression

- [x] Ajouter les tests ViewModel ciblés et les exécuter ; les contrôles globaux de l'étape 8 restent à faire.

Fichiers : `HomeViewModelTest.kt`, `VodViewModelTest.kt`,
`SeriesViewModelTest.kt` si nécessaire.

Validation : première catégorie, absence de catégorie, réordonnancement,
erreur silencieuse et absence de lecture globale sont testés ;
`testDebugUnitTest`, `assembleDebug`, `lintDebug` passent.

---

# 7. Notes de développement

- `HomeViewModel` utilise les use cases de catégories, lit uniquement la première catégorie visible et borne chaque rangée à 20 médias.
- Les lectures globales et le repli `TopRatedSelector` sont supprimés ; les rangées Top 10 dépendent uniquement de TMDB.
- La catégorie est transportée une fois par `savedStateHandle` de l'entrée racine, puis appliquée par `selectCategoryById()` dans l'onglet cible.
- Vérification d’implémentation : compilation Kotlin et suites ciblées
  `HomeViewModelTest`, `VodRepositoryImplTest` et `SeriesRepositoryImplTest` réussies. Aucune validation finale ni lint global n'est déclaré à cette étape.

---

# 8. Review

Status: RESOLVED

Revue effectuée le 2026-08-02 sur le diff de travail (aucune modification de code
à cette étape). Périmètre relu : `HomeViewModel.kt`, `HomeScreen.kt`,
`NavGraph.kt`, `MobileNavigation.kt`, `VodViewModel.kt`, `SeriesViewModel.kt`,
suppression de `TopRatedSelector`, `HomeViewModelTest.kt`.

Note d'exécution : les suites n'ont pas pu être relancées pendant la revue, deux
processus Gradle `--no-daemon` antérieurs occupant le lock du projet. La
validation `testDebugUnitTest` / `assembleDebug` / `lintDebug` reste due à
l'étape 8.

## Points conformes

* Le calcul de `catalogJob` suit exactement le modèle TV en direct : `once()`,
  `firstOrNull()`, lecture d'une seule catégorie, `take(HOME_ROW_LIMIT)`, avec
  repli `emptyList()` et re-throw de `CancellationException` sur chaque bloc.
* Le filtrage des catégories masquées n'est pas dupliqué dans le ViewModel :
  la responsabilité reste dans `applyCategoryPreferences` du use case
  (décision technique 2 respectée).
* `firstVodCategory` / `firstSeriesCategory` sont bien remis à `null` dans
  `resetVisibleContent`, au même titre que `firstLiveCategory`.
* Les deux `getCached*Streams("all")` de `catalogJob` ont disparu, ainsi que
  `TopRatedSelector` et son test — suppression conforme à la décision PO,
  signalée dans le ticket et non dissimulée derrière un échec de build.
* `HomeScreen` n'affiche plus de lien « Voir tout » quand aucune catégorie n'est
  disponible (`onSeeAll` nul), et la cible ne dépend plus de `recently_added`.
* Aucun `hiltViewModel` de catalogue n'est instancié depuis la route `home` :
  la décision technique 4 (latence de démarrage) est tenue.

## Critique

### C1 — « Voir tout » n'applique pas la catégorie au premier clic

**Description.** Dans `NavGraph.kt` (routes `movies` l. 380-391 et `series`
l. 403-414), le `LaunchedEffect(pendingCategory)` appelle
`selectCategoryById(...)` puis remet immédiatement le `savedStateHandle` à
`null`. Or `VodViewModel.selectCategoryById` (l. 243-245) résout la catégorie
dans `_state.value.categories`, alimenté de façon asynchrone par
`observeCategories()` (`VodViewModel.kt` l. 199-216, `getVodCategoriesUseCase()`
collecté dans `viewModelScope`). Au premier passage sur l'onglet, le ViewModel
vient d'être créé par `hiltViewModel(...)` : `categories` est encore vide quand
le `LaunchedEffect` s'exécute. `firstOrNull { … }` renvoie `null`, l'appel est un
no-op silencieux — et la valeur en attente est malgré tout consommée. Identique
pour `SeriesViewModel`.

**Impact.** Le critère d'acceptation n° 4 (« Le lien "Voir tout" redirige
correctement vers la catégorie concernée ») échoue dans le cas nominal : au
premier clic depuis l'Accueil après le démarrage, l'onglet Films (ou Séries)
s'ouvre sur « Tout » au lieu de la catégorie affichée sur la Home. Le
comportement n'est correct qu'aux visites suivantes, une fois les catégories en
mémoire — donc un bug intermittent, non reproductible en test manuel rapide et
invisible pour les tests actuels.

**Correction attendue.** Ne consommer la catégorie en attente qu'après
application effective. Deux options :
1. `selectCategoryById(categoryId: String): Boolean` renvoyant `false` si la
   catégorie n'est pas encore connue ; le `LaunchedEffect` ne remet à `null`
   que sur `true`, et se redéclenche via `state.categories` (clé supplémentaire
   du `LaunchedEffect`).
2. Mémoriser l'identifiant en attente dans le ViewModel (`pendingCategoryId`) et
   l'appliquer dans `observeCategories()` à la première émission qui le contient
   — la consommation côté navigation devient alors sûre immédiatement.

L'option 2 garde toute la logique dans le ViewModel et reste testable en JVM,
ce qui répond aussi à M2 ci-dessous.

## Majeur

### M1 — Couverture de tests inférieure à la stratégie annoncée (§5)

**Description.** La stratégie de tests du ticket liste six cas ViewModel plus
`selectCategoryById`. Sont réellement présents :
`test_loadHomeData_usesFirstVisibleCategoriesWithoutGlobalCatalogReads`
(cas 1, 2 et 4) et `test_loadHomeData_popularRowsHaveNoCatalogFallback`
(cas 4 bis, partiel). Manquent :
* cas 3 — `once()` renvoie une liste vide : aucune assertion sur
  `firstVodCategory == null` / `firstVodStreams` vide (les stubs par défaut
  couvrent le chemin mais rien n'est vérifié) ;
* cas 5 — réordonnancement via `categoryPreferenceRepository.changes` faisant
  basculer `firstVodCategory` ;
* cas 6 — `getCachedVodStreams` qui lève : rangée vide et `state.error` non
  positionné ;
* `selectCategoryById` : aucun test dans `VodViewModelTest` ni
  `SeriesViewModelTest`, alors que le §5 le désigne explicitement comme « la
  partie testable en JVM » du lien « Voir tout ».

**Impact.** Les deux comportements les plus fragiles du ticket — réactivité aux
préférences et sélection par identifiant — ne sont couverts par rien. C1 serait
détecté par le test `selectCategoryById` manquant.

**Correction attendue.** Ajouter les quatre cas listés, dont deux tests
`selectCategoryById` (identifiant connu → `selectedCategory` mis à jour ;
identifiant inconnu → no-op sans exception), et un test couvrant l'ordre
d'arrivée des catégories (catégories vides au moment de l'appel) une fois C1
corrigé.

### M2 — Assertion incomplète sur la rangée Séries

**Description.** Dans
`test_loadHomeData_usesFirstVisibleCategoriesWithoutGlobalCatalogReads`, seul
`firstSeriesCategory?.categoryId` est vérifié ; `firstSeriesStreams` ne l'est
pas, et la borne `HOME_ROW_LIMIT` n'est testée que côté Films (21 → 20).

**Impact.** Une régression sur le chemin Séries (mauvaise catégorie lue, limite
non appliquée) passerait le test.

**Correction attendue.** Assertion symétrique sur `firstSeriesStreams`, et un jeu
de données Séries dépassant la limite pour couvrir `take(HOME_ROW_LIMIT)` des
deux côtés.

## Mineur

### m1 — Écran « Derniers ajouts » devenu injoignable

`grep` ne trouve plus aucun `navigate("recently_added/…")` : la route
(`NavGraph.kt` l. 842-848), `RecentlyAddedScreen` et `RecentlyAddedViewModel`
n'ont plus de point d'entrée. Ce dernier contient encore deux
`getCached*Streams("all")` (`RecentlyAddedViewModel.kt` l. 41 et 51), donc du
code mort coûteux. Le §4 du ticket avait anticipé le cas et renvoyé la décision
à un ticket ultérieur — **correction attendue** : ouvrir ce ticket
explicitement (suppression de l'écran + des ressources `recently_added_*`, ou
réintroduction d'un point d'entrée), plutôt que de laisser l'arbitrage implicite.

### m2 — Lambda `onSeeAll` recréée à chaque recomposition

`HomeScreen.kt` l. 507 et 598 :
`state.firstVodCategory?.let { category -> { onNavigateToVodCategory(category) } }`
alloue une nouvelle lambda à chaque recomposition, rendant le paramètre instable
pour `HomeSectionRow`. Impact faible (la section est déjà non skippable via son
`content`), mais la Home TV est justement l'écran où les recompositions coûtent.
**Correction attendue** : envelopper dans un `remember(state.firstVodCategory)`.

### m3 — `getBackStackEntry(ROOT_ROUTE)` sans garde

`NavGraph.kt` l. 250 et 382 : `getBackStackEntry` lève
`IllegalArgumentException` si la route `home` n'est pas sur la pile. Le risque
préexiste (`rememberTabViewModelOwner`, l. 133-137) et n'est donc pas introduit
ici, mais le nombre d'appels augmente. **Correction attendue** : au minimum un
commentaire renvoyant à l'invariant « `home` est toujours sur la pile en mode
mobile », sinon un `runCatching` sur le chemin de navigation.

### m4 — Cohérence du nommage `firstVodStreams`

Le champ retrouve sa sémantique d'origine, mais aucun KDoc ne le dit et la Home
affiche ce contenu sous le titre générique « Films ». **Correction attendue** :
une ligne de commentaire sur les deux champs de `HomeState` rappelant qu'ils
portent la **première catégorie visible du profil**, décision B de l'étape 2.

---

## Corrections appliquées à l'étape 7

* C1 : la catégorie en attente est consommée seulement après une sélection
  effective ; les routes se redéclenchent lorsque les catégories arrivent.
* M1/M2 : tests ajoutés pour les catégories absentes, les erreurs locales, le
  changement de préférences, les limites VOD/Séries et les identifiants connus
  ou inconnus des ViewModels.
* m1 : T12 ouvre explicitement la décision de suppression ou de réexposition de
  l'écran Derniers ajouts. m2 à m4 sont traités par callbacks mémorisés,
  invariant de pile documenté et KDoc de sémantique des rangées.

## Validation étape 8

Status: VALIDATED

Exécution complète obtenue le 2026-08-02 après purge des daemons Gradle restés
bloqués (`./gradlew --stop`) qui empêchaient les tentatives précédentes
d'aboutir :

- `testDebugUnitTest` → **562 tests, 0 échec, 0 erreur**
  (`app/build/test-results/testDebugUnitTest/*.xml`).
- `assembleDebug` → **réussi**, APK généré
  (`app/build/outputs/apk/debug/app-debug.apk`).
- `lintDebug` → **réussi**, `0 errors, 56 warnings`
  (`app/build/reports/lint-results-debug.txt`).

`lintDebug` avait d'abord échoué avec 2 erreurs `UnrememberedGetBackStackEntry`
sur `NavGraph.kt:389` et `:416` (`remember { navController.
getBackStackEntry(...) }` sans clé), directement liées à l'invariant de pile
signalé au m3 de la Review (Étape 6) : corrigé en alignant sur le pattern déjà
validé par lint de `rememberTabViewModelOwner`, `remember(tabEntry) { ... }`,
dans les branches "movies" et "series" de `AppNavGraph`.

Le ticket passe donc de `VALIDATION` à `VALIDATED`.

# 9. Release

*(À remplir à l'Étape 10)*
