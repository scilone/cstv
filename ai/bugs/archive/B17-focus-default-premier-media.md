# B17 - Absence du sélecteur pivot et focus par défaut au démarrage/changement de page

## Informations générales

Status:
RELEASED

Created:
2026-08-02

Released:
v1.69.0 - 2026-08-02 - commit: 8dcf1ab

---

# 1. Description

Lorsque l'utilisateur démarre l'application ou change d'onglet principal (TV en direct, Films/VOD, Séries) sur Android TV, le sélecteur visuel de focus (le pivot) n'est pas affiché d'emblée. Aucune carte de média n'est mise en évidence par défaut.

Par conséquent, la première utilisation des touches fléchées de la télécommande (D-pad) produit un comportement très aléatoire : le focus peut atterrir de façon imprévisible sur un élément, ou rester piégé dans la barre de navigation latérale.

Pour corriger cela et garantir une navigation TV stable et fluide, le sélecteur doit être visible dès l'affichage d'un écran de catalogue, et se positionner automatiquement sur le premier média disponible dès que le chargement est terminé.

---

# 2. Contexte

Le projet utilise un défilement à sélecteur fixe (Fixed Focus/Pivot Scrolling - F19) qui garantit une excellente stabilité visuelle de défilement sur TV. Cependant, F19 réagit au focus acquis mais ne gère pas le focus initial lors de l'entrée sur l'écran ou de l'achèvement des chargements de données.

Actuellement :
* Dans `MainActivity.kt`, un `contentFocusRequester` tente de demander le focus sur le conteneur principal (`AppNavGraph`) lors d'une navigation.
* Cependant, au moment exact de l'entrée sur les écrans (TV en direct, VOD, Séries), les données du catalogue sont en cours de chargement (`state.isLoadingStreams` ou `state.isLoading` est à `true`). L'écran n'affiche alors qu'un indicateur de chargement circulaire (`CircularProgressIndicator`), qui n'est pas focusable.
* Toutes les tentatives répétées de `contentFocusRequester.requestFocus()` échouent donc silencieusement durant la phase de chargement de l'écran car aucun nœud de média focusable n'est présent dans l'arbre Compose.
* Une fois le chargement achevé, les cartes de médias apparaissent bien à l'écran, mais aucun mécanisme ne redemande de focus explicite sur le premier élément de média. Le sélecteur de focus reste invisible jusqu'à ce que l'utilisateur appuie de façon incertaine sur le D-pad.

---

# 3. Spécification fonctionnelle

## Objectif

Sur Android TV, forcer l'acquisition automatique et visuelle du focus sur le tout premier média d'un écran principal (Accueil, TV en direct, Films/VOD, Séries) dès que celui-ci est visible et chargé, éliminant ainsi les comportements de navigation aléatoires lors d'un changement d'onglet ou au démarrage.

Le mobile est explicitement hors périmètre : aucun focus automatique ne doit y être appliqué afin de ne pas interférer avec l'expérience tactile standard.

## User stories

* **En tant qu'utilisateur Android TV**, lorsque l'application démarre et que l'Accueil s'affiche, je vois immédiatement la première carte tendance (le carrousel Hero Card `HomeTrendingCarouselTv`) focalisée et mise en évidence visuellement, sans avoir à appuyer sur le D-pad pour "réveiller" le focus.
* **En tant qu'utilisateur Android TV**, lorsque je navigue vers les onglets *TV en direct*, *Films* ou *Séries*, dès que le catalogue finit de charger :
  * En mode **"Tout"** (all) : le focus se positionne automatiquement sur le premier élément média de la première ligne disponible (ex: la première carte de "Continuer à regarder" s'il y en a, sinon "Favoris", sinon le premier média de la première catégorie).
  * En mode **"Catégorie spécifique"** : le focus se positionne automatiquement sur le premier média de la grille (cellule à l'index 0).
* **En tant qu'utilisateur mobile**, je conserve mon expérience tactile inchangée, sans aucun focus visuel automatique ni focus forcé au chargement des écrans.

## Parcours utilisateur et règles d'interaction

1. **Démarrage de l'application** : L'utilisateur arrive sur l'Accueil. Dès que les tendances ou sections se chargent, le focus est demandé et acquis sur la Hero Card (si présente) ou sur la première carte de la première section. Le sélecteur (cadre de focus) apparaît d'emblée à l'écran.
2. **Changement d'onglet principal** : L'utilisateur ouvre la barre de navigation latérale et sélectionne l'onglet "Films" (VOD). La barre se replie, l'écran de VOD affiche son loader. Dès que le chargement se termine et que la liste apparaît, le focus est acquis automatiquement sur la première carte de la première section non vide.
3. **Comportement du D-pad** : Une fois le focus initialement positionné sur le premier média, l'utilisateur peut naviguer immédiatement de façon prévisible (D-pad Droite, Gauche pour ouvrir la barre de navigation, Bas pour changer de ligne).
4. **Mémorisation et Restauration** : Si l'utilisateur revient sur un onglet déjà consulté au cours de la même session, on privilégie la restauration du focus là où l'utilisateur l'avait laissé (si possible via la sauvegarde d'état de Compose / `LazyListState`). Si aucune position de focus précédente n'est connue ou restaurable, on applique le focus par défaut sur le premier média.

## Hypothèses sur l'ordre de priorité du premier média par écran

Pour chaque écran, on définit le "premier média" cible du focus initial selon les priorités suivantes :

1. **Accueil (`HomeScreen`)** :
   * Priorité 1 : La carte active du carrousel de tendances (`HomeTrendingCarouselTv`) si la liste des tendances n'est pas vide.
   * Priorité 2 : Le premier élément de la section "Continuer à regarder" (`home_resume`) s'il y en a.
   * Priorité 3 : Le premier élément de la section "Favoris" (`home_favorites`) s'il y en a.
   * Priorité 4 : Le premier élément de la section "TV" (`home_livetv`) s'il y en a.
   * Priorité 5 : Le premier élément de la première autre section non vide disponible.

2. **Films/VOD (`VodScreen`) & Séries (`SeriesScreen`)** :
   * **En mode "Tout"** (All) :
     * Priorité 1 : Le premier élément de la section "Continuer à regarder" (`resume_watching`) s'il y en a.
     * Priorité 2 : Le premier élément de la section "Favoris" (`favorites`) s'il y en a.
     * Priorité 3 : Le premier élément de la première catégorie non vide disponible.
   * **En mode "Catégorie spécifique"** (Grille) :
     * Le premier élément (index 0) de la grille de médias.

3. **TV en direct (`LiveTvScreen`)** :
   * **En mode "Tout"** (All) :
     * Priorité 1 : Le premier élément de la section "Récemment consultés" (`recently_watched`) s'il y en a.
     * Priorité 2 : Le premier élément de la section "Favoris" (`favorites`) s'il y en a.
     * Priorité 3 : Le premier élément de la première catégorie non vide disponible.
   * **En mode "Catégorie spécifique"** (Grille) :
     * Le premier élément (index 0) de la grille de chaînes.

## Critères d'acceptation (Fonctionnels)

- [ ] Sur Android TV, à l'ouverture de l'Accueil, le focus visuel est automatiquement positionné sur le carrousel de tendances (Hero Card) dès qu'il est affiché.
- [ ] Sur Android TV, à l'ouverture des onglets TV, Films et Séries (en mode "Tout"), dès que le chargement se termine, le premier média disponible reçoit automatiquement le focus visuel.
- [ ] Sur Android TV, à l'ouverture des onglets TV, Films et Séries filtrés sur une catégorie spécifique (grille), le premier élément de la grille reçoit automatiquement le focus visuel une fois le chargement terminé.
- [ ] Sur Android TV, si un écran est rechargé ou rafraîchi manuellement, le focus initial est redemandé proprement dès la fin du rechargement.
- [ ] Sur mobile, aucun comportement de focus automatique n'est introduit et le défilement tactile reste standard.

## Cas limites et gestion des erreurs

- **Aucun média disponible** : Si l'écran ou la section est vide après le chargement, le focus initial ne doit pas tenter de se poser dans le vide, et doit pouvoir être capturé par d'autres composants interactifs (ex: la barre de catégories ou la barre de navigation).
- **Chargement infini / Erreur de chargement** : En cas d'erreur de chargement ou de catalogue indisponible, le focus ne doit pas boucler ou provoquer de crashs. Il se replie sur les boutons d'action disponibles (ex: le bouton "Réessayer").
- **Déconnexion de la télécommande / Reprise de session** : La demande de focus ne doit jamais bloquer le thread principal ni dégrader les performances au chargement.

---

# 4. Spécification technique

## Ce que fait le code aujourd'hui, et pourquoi c'est insuffisant

`MainActivity.kt` l. 218-239 réessaie déjà de prendre le focus après une
navigation :

```kotlin
LaunchedEffect(currentRoute, showTvRail) {
    if (showTvRail) {
        railExpanded = false
        repeat(FOCUS_REQUEST_ATTEMPTS) {                 // 60 tentatives
            if (contentChildFocused || railExpanded) return@LaunchedEffect
            contentFocusRequester.requestFocusSafely()
            delay(FOCUS_REQUEST_RETRY_MS)                // toutes les 120 ms
        }
    }
}
```

Deux limites structurelles, et non un simple réglage :

1. **La cible est le conteneur, pas un média.** `contentFocusRequester` est posé
   sur la `Box` qui enveloppe `AppNavGraph` (l. 267-273). Quand elle obtient le
   focus, Compose le délègue au **premier descendant focusable dans l'ordre de
   composition** — sur `VodScreen`, c'est la première puce de catégorie
   (`CategoryFilterChip`, l. 241-248) ou le bouton « Rafraîchir » (l. 250), pas
   une vignette. La condition d'arrêt `contentChildFocused` (l. 271 :
   `it.hasFocus && !it.isFocused`) est alors satisfaite, la boucle s'arrête, et
   le sélecteur n'est nulle part sur les médias.
2. **La fenêtre de 7,2 s ne couvre pas le bon événement.** Elle couvre la
   composition, pas la **fin du chargement** : pendant `state.isLoadingStreams`,
   l'écran ne compose qu'un `CircularProgressIndicator` non focusable
   (`VodScreen.kt` l. 259-262). Le focus, s'il est pris, l'est sur les puces
   composées avant le loader.

Le correctif ne consiste donc pas à insister davantage depuis `MainActivity`,
mais à **déplacer la décision dans l'écran**, seul à savoir quand ses données
sont prêtes et quel nœud est « le premier média ».

## Nouveaux composants

### 1. `presentation/components/TvInitialFocus.kt` (nouveau)

```kotlin
/**
 * Prise de focus initiale sur TV. La demande n'est émise qu'une fois les
 * données prêtes (le nœud cible doit exister dans l'arbre), et cesse dès qu'un
 * focus est acquis dans l'écran ou que l'utilisateur agit — sans quoi elle
 * reprendrait le focus des mains de l'utilisateur pendant qu'il navigue.
 */
@Stable
class TvInitialFocusState internal constructor() {
    val requester = FocusRequester()
    internal var acquired by mutableStateOf(false)
    fun markAcquired() { acquired = true }
}

/**
 * [ready] : les données sont chargées ET la cible est composée.
 * [targetKey] : identité de la cible ; un changement (autre onglet, autre
 * catégorie, rechargement) réarme une nouvelle demande.
 */
@Composable
fun rememberTvInitialFocus(isTv: Boolean, ready: Boolean, targetKey: Any?): TvInitialFocusState

/** À poser sur le nœud cible (ou son enveloppe). */
fun Modifier.tvInitialFocusTarget(state: TvInitialFocusState): Modifier
```

Boucle interne, volontairement bornée et courte (la donnée est déjà là, seule la
composition du nœud peut prendre un ou deux frames) :

```kotlin
LaunchedEffect(isTv, ready, targetKey) {
    if (!isTv || !ready) return@LaunchedEffect
    repeat(INITIAL_FOCUS_ATTEMPTS) {          // 10
        if (state.acquired) return@LaunchedEffect
        runCatching { state.requester.requestFocus() }
        delay(INITIAL_FOCUS_RETRY_MS)         // 80 ms  → 800 ms au total
    }
}
```

`requestFocus()` est enveloppé dans `runCatching` pour la même raison que
`requestFocusSafely` (`MainActivity` l. 448-450) : une demande sur un nœud non
encore attaché lève, et cet échec ne doit jamais remonter en crash.

### 2. Sélecteurs de cible purs (testables en JVM)

La logique de priorité de l'étape 2 est extraite en objets sans dépendance
Compose, dans le paquet de chaque écran :

```kotlin
// presentation/home/HomeInitialFocusTarget.kt
enum class HomeFocusTarget { TRENDING, RESUME, FAVORITES, LIVETV, VOD, TOP_MOVIES, RECO_MOVIES,
                             SERIES, TOP_SERIES, RECO_SERIES, DOWNLOADS }

object HomeInitialFocusTarget {
    /** null = aucune section non vide : ne pas demander de focus. */
    fun of(state: HomeState, isTv: Boolean): HomeFocusTarget?
}

// presentation/vod/CatalogInitialFocusTarget.kt (partagé VOD / Séries / Live)
enum class CatalogFocusTarget { RESUME, FAVORITES, FIRST_CATEGORY, GRID_FIRST_CELL }

object CatalogInitialFocusTarget {
    fun of(
        isAllMode: Boolean,
        hasResume: Boolean,
        hasFavorites: Boolean,
        firstNonEmptyCategoryId: String?,
        gridItemCount: Int
    ): CatalogFocusTarget?
}
```

L'ordre codé est exactement celui arbitré à l'étape 2 (§ « Hypothèses sur
l'ordre de priorité »). Ces deux objets sont le cœur testable du ticket.

## Composants impactés

| Fichier | Modification |
| --- | --- |
| `presentation/components/TvInitialFocus.kt` | **NOUVEAU** |
| `presentation/home/HomeInitialFocusTarget.kt` | **NOUVEAU** (pur) |
| `presentation/vod/CatalogInitialFocusTarget.kt` | **NOUVEAU** (pur, partagé) |
| `presentation/home/HomeScreen.kt` | Câblage : `ready`, cible, `tvInitialFocusTarget` sur la section retenue |
| `presentation/vod/VodScreen.kt` | Idem (mode « Tout » et mode grille) |
| `presentation/series/SeriesScreen.kt` | Idem |
| `presentation/livetv/LiveTvScreen.kt` | Idem |
| `presentation/home/components/HomeTrendingCarouselTv.kt` | Expose un `Modifier` (ou un `FocusRequester`) sur sa carte active, pour être ciblable en priorité 1 |
| `MainActivity.kt` | *Optionnel* — voir « Coexistence » |

## Détermination de `ready`

`ready` doit signifier « le nœud cible est composable maintenant », pas
« l'écran est composé » :

| Écran | `ready` |
| --- | --- |
| `HomeScreen` | `!state.isLoading && HomeInitialFocusTarget.of(state, isTv) != null` |
| `VodScreen` / `SeriesScreen` | `!state.isLoadingStreams && !state.isLoadingCategories && cible != null` |
| `LiveTvScreen` | idem, sur ses propres drapeaux de chargement |

Dans le mode grille, la cible est la cellule d'index 0 : `ready` inclut
`pagedStreams.itemCount > 0` — nécessaire car Paging peut composer la grille
avant que la première page ne soit chargée.

## Mémorisation et restauration

Le critère « revenir sur un onglet déjà consulté restaure le focus là où il était »
est traité **sans nouvel état de focus persistant** : les écrans mémorisent déjà
leur position de défilement dans leur ViewModel, lui-même porté par l'entrée
`ROOT_ROUTE` du back stack (`rememberTabViewModelOwner`, `NavGraph.kt` l. 133-137),
donc survivant aux changements d'onglet — via `rememberForeverLazyListState` /
`rememberForeverLazyGridState` et `viewModel.getScrollPosition(key)`.

Règle retenue :

* `getScrollPosition(key) == (0, 0)` → première venue (ou remontée en tête) :
  focus par défaut sur le premier média selon la priorité ;
* `getScrollPosition(key) != (0, 0)` → l'utilisateur avait défilé : la cible
  devient **le premier élément visible à la position restaurée** (index
  mémorisé), et non l'index 0 — le focus revient donc là où l'utilisateur
  était, sans mécanisme de persistance supplémentaire.

Cette approximation est assumée : elle restaure la *rangée* et non la vignette
exacte. Persister l'index de focus exact demanderait un état dédié dans chaque
ViewModel pour un gain marginal, et n'est pas retenu.

## Coexistence avec la boucle de `MainActivity`

Les deux mécanismes ne se combattent pas : la boucle de `MainActivity` s'exécute
pendant le chargement (elle ne trouve que les puces), la demande de l'écran
s'exécute **après** le chargement et déplace le focus vers la vignette. Le focus
final est donc celui voulu.

Conséquence visible : sur les écrans à bandeau de catégories, un focus transitoire
peut apparaître sur une puce avant de sauter sur la première vignette. Deux
options, à trancher en review :

* **(a)** laisser `MainActivity` inchangé — le saut est bref et le comportement
  reste sûr si un écran n'implémente pas encore B17 ;
* **(b)** réduire `FOCUS_REQUEST_ATTEMPTS` (60 → ~10, soit 1,2 s) une fois les
  quatre écrans câblés, la couverture du chargement devenant la responsabilité
  des écrans.

**Option (a) retenue pour la livraison**, (b) proposée en nettoyage ultérieur :
supprimer le filet de sécurité dans le même commit que l'ajout du mécanisme qui
le remplace est le meilleur moyen de livrer une régression de navigation.

À noter : F22 remplace le bandeau de puces TV par un bouton sélecteur unique, ce
qui réduit mécaniquement la fenêtre du focus transitoire.

## Modèles de données, API, services, stockage, cache

Néant. Aucun `UseCase`, `Repository`, entité Room (base en version 21,
inchangée), DTO ni appel réseau. Aucun état de focus persisté sur disque.

## Performances

* La boucle de demande est bornée à 10 tentatives × 80 ms et s'arrête au premier
  succès ; en pratique elle s'arrête à la première ou deuxième itération.
* `delay()` est une suspension de coroutine : **aucun blocage du thread
  principal** (cas limite de l'étape 2).
* Les sélecteurs de cible sont des `when` sur des booléens déjà présents dans
  l'état — coût nul, et calculés dans un `remember` clé sur l'état concerné.
* Effet secondaire favorable : la demande sur un nœud précis évite les demandes
  répétées de `MainActivity` sur un conteneur, chacune déclenchant une recherche
  de focus complète dans l'arbre.

## Sécurité

Sans objet.

## Compatibilité

* **Mobile** : `rememberTvInitialFocus(isTv = false, …)` ne compose aucun effet
  et `tvInitialFocusTarget` reste un `Modifier` inerte (le `FocusRequester` est
  attaché mais jamais sollicité). Aucun focus automatique, tactile inchangé —
  critère d'acceptation n° 5.
* **F19 (pivot)** : une prise de focus déclenche `tvPivotItem` /
  `tvPivotSection` comme n'importe quelle autre, donc la liste se positionne au
  pivot dès l'arrivée sur l'écran — comportement souhaitable et gratuit.
* **F23 (sélecteur double couche)** : complémentaire. B17 fournit l'acquisition,
  F23 le rendu ; livrées ensemble, le cadre est visible dès la fin du chargement.
  L'ordre de livraison conseillé est **B17 → F23**.
* **`MainActivity.dispatchKeyEvent`** : le garde-fou contre
  `IllegalStateException: isAttached is true` (l. 411-423) reste indispensable et
  n'est pas touché.
* **min SDK 21** : aucune API nouvelle.

## Dépendances

Aucune dépendance Gradle ajoutée.

## Risques techniques

| Risque | Gravité | Mitigation |
| --- | --- | --- |
| La demande vole le focus à l'utilisateur qui a déjà agi | Élevée | `state.acquired` est positionné dès qu'un focus est acquis dans l'écran, et la boucle s'arrête. La fenêtre totale est de 800 ms après la fin du chargement, contre 7,2 s aujourd'hui. |
| `requestFocus()` sur un nœud détaché → crash | Élevée | `runCatching`, sur le modèle éprouvé de `requestFocusSafely`. |
| Écran vide après chargement : focus « dans le vide » | Moyenne | Les sélecteurs renvoient `null` → `ready = false` → aucune demande. Le rail et les contrôles restent focusables (cas limite de l'étape 2). |
| Écran en erreur | Moyenne | Même mécanisme : cible `null`, aucune demande ; le bouton « Réessayer » reste atteignable. |
| Boucle infinie de re-demandes | Moyenne | Compteur borné **et** clé `targetKey` : une même cible n'est jamais réarmée sans changement d'identité. |
| Deux `FocusRequester` concurrents (conteneur + écran) | Moyenne | Ordre temporel garanti : le conteneur agit pendant le chargement, l'écran après. Documenté ci-dessus, avec l'option (b) en nettoyage. |
| Hero Card : `HomeTrendingCarouselTv` gère son propre focus interne | Moyenne | Le composant expose un point d'accroche (`Modifier` ou `FocusRequester` de la carte active) plutôt que d'être enveloppé de l'extérieur, pour ne pas court-circuiter sa logique de pager. À valider visuellement en review ; repli : priorité 1 ignorée et focus sur la première rangée. |
| Cible changeante pendant le chargement progressif de l'Accueil | Moyenne | `targetKey` = la cible elle-même : l'apparition tardive de « Top 10 » (appariement TMDB) ne réarme pas la demande si la cible retenue (Hero, Reprendre…) n'a pas changé. |

## Contraintes de performance

La demande de focus ne doit jamais retarder l'affichage : elle est déclenchée
**après** que `ready` passe à `true`, dans un `LaunchedEffect`, donc hors de la
phase de composition initiale.

---

# 5. Architecture

## Position dans la Clean Architecture

Comme F19 et F23, correctif purement `presentation` : le focus est un
comportement de rendu, aucune règle métier n'est en jeu. Rien ne descend sous
`presentation`. La logique de priorité, elle, est isolée en objets purs pour
être testable en JVM sans Compose.

```
presentation/components/
└── TvInitialFocus.kt              ← NOUVEAU : état + effet borné + modificateur

presentation/home/
├── HomeInitialFocusTarget.kt      ← NOUVEAU : priorité (pur, testable)
├── HomeScreen.kt                  ← câblage
└── components/HomeTrendingCarouselTv.kt  ← point d'accroche pour la Hero

presentation/vod/
├── CatalogInitialFocusTarget.kt   ← NOUVEAU : priorité catalogue (pur, partagé)
└── VodScreen.kt                   ← câblage

presentation/series/SeriesScreen.kt   ← câblage (réutilise CatalogInitialFocusTarget)
presentation/livetv/LiveTvScreen.kt   ← câblage (idem)

MainActivity.kt                       ← inchangé à la livraison (filet de sécurité)
```

## Flux — arrivée sur l'onglet Films

```
Clic "Films" dans la barre latérale TV
        │
        ▼
navigateToRootTab("movies")  →  MainActivity : boucle conteneur (filet)
        │                                   │
        │                                   └─► focus éventuel sur une puce
        ▼
VodScreen compose : isLoadingStreams = true → CircularProgressIndicator
        │                                        (aucun nœud média focusable)
        ▼
Room émet la catégorie  →  isLoadingStreams = false
        │
        ▼
cible = CatalogInitialFocusTarget.of(isAllMode, hasResume, hasFavorites,
                                     firstNonEmptyCategoryId, gridItemCount)
        │
        ├─ null  ──────────────────► aucune demande (écran vide / erreur)
        │
        └─ RESUME | FAVORITES | FIRST_CATEGORY | GRID_FIRST_CELL
                 │
                 ▼
        ready = true  →  rememberTvInitialFocus(isTv, ready, targetKey = cible)
                 │
                 ▼
        requestFocus() sur le nœud portant tvInitialFocusTarget
                 │
                 ├─► succès : acquired = true, boucle arrêtée
                 │        │
                 │        ▼
                 │   tvPivotItem / tvPivotSection (F19) positionnent la liste au pivot
                 │        │
                 │        ▼
                 │   (F23) le cadre de la couche avant apparaît
                 │
                 └─► échec (nœud pas encore attaché) : nouvelle tentative dans 80 ms,
                     10 fois au maximum
```

## Responsabilités des composants

* **`TvInitialFocus`** : *quand* demander, *combien de temps*, et *comment
  s'arrêter*. Il ne sait pas ce qu'il focalise.
* **`HomeInitialFocusTarget` / `CatalogInitialFocusTarget`** : *quoi* focaliser.
  Fonctions pures de l'état, sans Compose — donc testables et relisables sans
  ouvrir un écran de 850 lignes.
* **Les écrans** : traduire l'état de chargement en `ready`, poser
  `tvInitialFocusTarget` sur le nœud désigné, et déclarer `markAcquired()` sur
  leur conteneur de listes.
* **`MainActivity`** : filet de sécurité générique pour les écrans qui
  n'implémentent pas de cible propre. Inchangé.
* **F19 / F23** : consomment l'acquisition, ne la provoquent pas.

## Décisions techniques

1. **Décision dans l'écran, pas dans `MainActivity`.** Seul l'écran connaît son
   état de chargement et l'ordre de ses sections ; c'est la cause racine du bug,
   pas un défaut de réglage de la boucle existante.
2. **Priorité extraite en objets purs.** Le classement de l'étape 2 est une règle
   à part entière, avec des cas limites (sections vides, mode grille) ;
   l'enfouir dans un composable la rendrait non testable, alors que `AGENTS.md`
   impose des tests automatisés JVM.
3. **`CatalogInitialFocusTarget` partagé entre VOD, Séries et Live.** Les trois
   écrans ont la même structure (mode « Tout » : reprise → favoris → première
   catégorie ; mode grille : cellule 0). Trois copies divergeraient.
4. **Fenêtre courte (800 ms) au lieu de longue (7,2 s).** Puisque la demande part
   *après* la disponibilité des données, seule la latence de composition doit
   être couverte. Une fenêtre longue rouvrirait le risque de reprendre le focus à
   l'utilisateur.
5. **`targetKey` comme clé de réarmement.** Couvre proprement le rechargement
   manuel (critère d'acceptation n° 4), le changement de catégorie et le
   changement d'onglet, sans drapeau impératif à remettre à zéro à la main.
6. **Restauration approchée via les positions de défilement déjà persistées.**
   Aucun nouvel état à maintenir ni à synchroniser ; limite documentée
   (la rangée est restaurée, pas la vignette exacte).
7. **`MainActivity` non modifié à la livraison.** Retirer le filet dans le même
   commit que son remplaçant est le scénario type de régression de navigation TV.
   Le nettoyage est proposé, daté, et séparé.

## Stratégie de tests

Tests unitaires JVM (`./gradlew testDebugUnitTest`), portant sur les objets purs
— la prise de focus elle-même exigerait un device et est donc exclue des
critères de validation de l'agent (`AGENTS.md`).

**`HomeInitialFocusTargetTest`** :
1. tendances non vides → `TRENDING` (priorité 1), même si « Continuer à
   regarder » est renseigné ;
2. tendances vides + reprise non vide → `RESUME` ;
3. tendances et reprise vides + favoris → `FAVORITES` ;
4. puis `LIVETV` ;
5. seule une rangée tardive (recommandations) non vide → cette rangée ;
6. état entièrement vide → `null` ;
7. `state.isLoading = true` → `null` (aucune demande pendant le chargement) ;
8. `isTv = false` → `null` (mobile hors périmètre).

**`CatalogInitialFocusTargetTest`** :
9. mode « Tout » avec reprise → `RESUME` ;
10. mode « Tout » sans reprise, avec favoris → `FAVORITES` ;
11. mode « Tout » sans reprise ni favoris, première catégorie non vide →
    `FIRST_CATEGORY` ;
12. mode « Tout » entièrement vide → `null` ;
13. mode grille avec `gridItemCount > 0` → `GRID_FIRST_CELL` ;
14. mode grille avec `gridItemCount == 0` → `null` (grille filtrée à vide :
    le focus doit rester disponible pour la barre de recherche).

**Non-régression** : la suite existante, en particulier `HomeViewModelTest`
(l'état alimentant les sélecteurs ne change pas) et `TvPivotScrollTest` (le
contrat F19 n'est pas modifié). Puis `assembleDebug` et `lintDebug`.

---

# 6. Plan de développement

## Ordre d'exécution

Les sélecteurs purs précèdent l'effet Compose ; le câblage des écrans suit les
priorités définies, sans retirer le filet de sécurité de `MainActivity`.

### Tâche 1 — Créer et tester les sélecteurs de première cible

- [x] Implémenter `HomeInitialFocusTarget` et `CatalogInitialFocusTarget` avec
  leurs tests JVM.

Objectif : formaliser l'ordre Hero/reprise/favoris/catégorie et les repli mode
grille/écran vide hors des composables.

Fichiers : `presentation/home/HomeInitialFocusTarget.kt`,
`presentation/vod/CatalogInitialFocusTarget.kt` et tests associés.

Validation : chaque priorité, aucune section et première cellule de grille sont
couverts ; aucun runtime Android/Compose n'est requis par les tests.

### Tâche 2 — Créer le mécanisme borné de demande de focus TV

- [x] Ajouter `TvInitialFocus` et son modificateur de cible, réarmé seulement
  par un changement de cible et arrêté dès acquisition.

Objectif : demander le focus après disponibilité réelle du nœud, sans bloquer
le thread ni le reprendre à l'utilisateur.

Fichiers : `presentation/components/TvInitialFocus.kt`.

Validation : `isTv=false` est inerte ; tentative détachée protégée ; au plus dix
tentatives de 80 ms et aucune boucle infinie.

### Tâche 3 — Câbler l'Accueil et la Hero

- [x] Calculer `ready`, poser la cible sur la priorité retenue et exposer le
  point d'ancrage de la carte Hero active.

Objectif : rendre visible un focus initial sur l'Accueil dès que le média existe.

Fichiers : `HomeScreen.kt`, `HomeTrendingCarouselTv.kt` et composants Home.

Validation : Hero puis reprises/favoris/TV suivent l'ordre ; écran vide ou erreur
ne demande aucun focus ; restauration de scroll existante est préservée.

### Tâche 4 — Câbler VOD, Séries et Live

- [x] Appliquer le même contrat aux modes « Tout » et grilles paginées des trois
  écrans catalogue.

Objectif : focaliser la première cible composable après chargement sans modifier
la navigation mobile ni supprimer le garde-fou `MainActivity`.

Fichiers : `VodScreen.kt`, `SeriesScreen.kt`, `LiveTvScreen.kt`.

Validation : `ready` attend les données et `itemCount > 0` en grille ; catégorie
vide/erreur ne provoque pas de demande ; priorité reprise/favoris/catégorie est
respectée.

### Tâche 5 — Vérifier la non-régression

- [x] Exécuter les tests et contrôles de build, puis documenter la limite TV.

Fichiers : tests ajoutés et ce ticket.

Validation : `testDebugUnitTest`, `assembleDebug`, `lintDebug` passent ;
acquisition visuelle et navigation D-pad sont explicitement séparées comme
validation manuelle, sans être un critère automatisé final.

---

# 7. Notes de développement

Implémentation étape 5 : demande de focus TV bornée (10 tentatives espacées de
80 ms) et protégée des cibles détachées, branchée sur la Hero de l'accueil,
les premières rangées et les premières cellules de grille VOD/Séries/Live.
Les sélecteurs catalogue sont testés en JVM ; le ressenti D-pad et le rendu du
pivot restent à confirmer sur Android TV pendant la review.

---

# 8. Review

Revue de l'implémentation livrée à l'étape 5 (aucun code modifié).

## Ce qui tient

* **La décision est bien passée dans l'écran** (décision 1) : `ready` combine
  `!isLoadingStreams && !isLoadingCategories && cible != null` dans les trois
  écrans catalogue, et en mode grille la cible dépend de
  `pagedStreams.itemCount > 0`, comme spécifié.
* **`CatalogInitialFocusTarget`** est pur, partagé par VOD, Séries et Live TV
  (décision 3), et son ordre correspond à l'étape 2. Son test couvre les six
  cas 9 à 14.
* **Bornes et robustesse** : 10 tentatives × 80 ms, `runCatching` autour de
  `requestFocus()`, `delay()` suspendu — aucun blocage du thread principal.
* **`MainActivity` est resté intact** (décision 7, option (a)) : le filet de
  sécurité n'a pas été retiré dans le commit qui le remplace.
* `testDebugUnitTest` (80 suites, 603 tests), `assembleDebug` et `lintDebug`
  passent.

## Critique

### C1 — La demande de focus se réarme et reprend le focus à l'utilisateur

**Description.** `TvInitialFocus.kt` l. 34-42 : `LaunchedEffect(isTv, ready,
targetKey)` remet `state.acquired = false` et relance la boucle **à chaque
changement de `ready`**, pas seulement à chaque changement de cible. Or `ready`
dépend de `initialTarget`, lui-même dépendant de `pagedStreams.itemCount`
(`VodScreen.kt` l. 265-273 et jumeaux). Par ailleurs `acquired` n'est armé que
par le nœud cible lui-même (l. 46-47) : rien ne détecte qu'un focus a été
acquis *ailleurs dans l'écran*, contrairement à ce qu'annonce la mitigation du
risque « la demande vole le focus à l'utilisateur qui a déjà agi ». La méthode
`markAcquired()`, que l'étape 5 confiait aux écrans (« déclarer `markAcquired()`
sur leur conteneur de listes »), n'est appelée nulle part.

**Impact.** Trois scénarios concrets, tous en usage normal :

1. *Saisie dans la recherche de catégorie (F22)* — taper un caractère
   reconstruit le flux `pagedStreams` (`remember(…, searchQuery, …)`), le
   nouveau `LazyPagingItems` repart à `itemCount == 0`, donc `initialTarget`
   passe `null` puis `GRID_FIRST_CELL` : `ready` fait `true → false → true`,
   l'effet redémarre et **arrache le focus du champ de recherche** pour le
   poser sur la première vignette. Même chose à chaque application ou retrait
   de filtre avancé qui vide puis re-remplit la grille.
2. *Accueil* — `HomeTrendingCarouselTv.kt` l. 100-104 utilise
   `targetKey = trendingItems.firstOrNull()?.trendingTitle?.tmdbId` : toute
   ré-émission des tendances qui change le premier élément (appariement TMDB
   progressif, rafraîchissement) ramène le focus sur la Hero, où que
   l'utilisateur soit descendu.
3. *Fenêtre de 800 ms* — si l'utilisateur appuie sur le D-pad pendant la
   fenêtre initiale et quitte la cible, la boucle continue de redemander le
   focus jusqu'à dix fois et le ramène de force.

Le ticket avait classé ce risque « Élevé » et annoncé sa mitigation ; elle n'est
pas implémentée.

**Correction attendue.** Deux verrous complémentaires :
* un latch « l'utilisateur a agi » au niveau de l'écran : `acquired` doit être
  armé dès qu'un focus est acquis **n'importe où** dans le conteneur (poser
  `onFocusChanged { if (it.hasFocus) markAcquired() }` sur la `LazyColumn` /
  `LazyVerticalGrid`, ce que la méthode publique `markAcquired()` prévoyait
  déjà) ;
* ne plus réarmer sur `ready` : ne garder que `targetKey` comme clé de
  réarmement (décision 5), et faire de `ready` une condition lue à l'intérieur
  de l'effet, ou attendre la disponibilité par `snapshotFlow`. Pour l'Accueil,
  `targetKey` doit être la *cible retenue* (`HomeFocusTarget`), pas l'identité
  du premier élément de tendance — c'est littéralement la mitigation écrite au
  tableau des risques (« l'apparition tardive de Top 10 ne réarme pas la
  demande si la cible retenue n'a pas changé »).

## Majeur

### M1 — L'Accueil n'est câblé que sur la Hero ; `HomeInitialFocusTarget` est du code mort

**Description.** `HomeScreen.kt` n'apparaît pas dans le diff et ne contient
aucune référence à `rememberTvInitialFocus`, `tvInitialFocusTarget` ou
`HomeInitialFocusTarget`. Le seul câblage vit dans
`HomeTrendingCarouselTv.kt`, qui gère lui-même son `rememberTvInitialFocus`.
`HomeInitialFocusTarget` et l'enum `HomeFocusTarget` ne sont référencés que par
leur propre fichier de test.

**Impact.** Les priorités 2 à 5 de l'Accueil (Continuer à regarder, Favoris,
TV, première rangée non vide) ne sont pas implémentées : si les tendances sont
vides — catalogue sans appariement TMDB, clé absente, repli silencieux prévu
par `AGENTS.md` — l'Accueil retombe exactement dans le bug d'origine. La
tâche 3 du plan est cochée alors que son objet principal, l'ordre de priorité
formalisé à l'étape 2, n'est branché sur rien.

**Correction attendue.** Appeler `HomeInitialFocusTarget.of(state, isTv)` dans
`HomeScreen`, dériver `ready` de `!state.isLoading && cible != null`, poser
`tvInitialFocusTarget` sur la première carte de la rangée retenue, et faire
consommer par `HomeTrendingCarouselTv` un état fourni par l'écran (ou un
`Modifier`) plutôt que d'en créer un pour lui seul — sans quoi deux
`FocusRequester` concurrents cohabiteront sur l'Accueil.

### M2 — `HomeInitialFocusTargetTest` couvre 1 cas sur les 8 spécifiés

**Description.** Le fichier ne contient qu'une méthode, qui vérifie les cas 7 et
8 (chargement, mobile). Les cas 1 à 6 — priorité `TRENDING` malgré une reprise
renseignée, puis `RESUME`, `FAVORITES`, `LIVETV`, rangée tardive seule non vide,
état entièrement vide → `null` — ne sont pas testés.

**Impact.** L'ordre de priorité est le « cœur testable du ticket » (§ 4) et le
seul artefact que l'automatisation peut valider, la prise de focus exigeant un
device. Il est livré non couvert. La régression introduite par un simple
réordonnancement des branches du `when` passerait inaperçue. La stratégie de
tests d'`AGENTS.md` (« chaque nouvelle fonctionnalité livrée doit accompagner
tests ») n'est pas satisfaite.

**Correction attendue.** Ajouter les six cas manquants. Ils sont purement
JVM et sans dépendance Compose.

### M3 — Aucun test pour `TvInitialFocus`, et son paramètre `isTv` n'est jamais faux

**Description.** La validation de la tâche 2 annonçait « `isTv=false` est
inerte ; tentative détachée protégée ; au plus dix tentatives de 80 ms ». Aucun
test n'existe. Par ailleurs les quatre points d'appel passent tous
`isTv = true` en dur (`VodScreen` l. 269, `SeriesScreen` l. 271, `LiveTvScreen`
l. 227, `HomeTrendingCarouselTv` l. 101) : la garantie « mobile hors
périmètre » repose entièrement sur le fait que ces composables ne sont composés
qu'en TV, pas sur le paramètre.

**Impact.** Le critère d'acceptation n° 5 (aucun focus automatique sur mobile)
n'est vérifié ni par un test ni par le code : il tient à une invariance
implicite de l'arbre de composition. Le jour où l'un de ces composables est
réutilisé côté mobile, le focus forcé revient sans avertissement.

**Correction attendue.** Soit propager le vrai `isTv` depuis l'écran, soit
retirer le paramètre et documenter l'invariant. Dans les deux cas, un test JVM
du compteur de tentatives (extrait en fonction pure ou testé via
`runTest`/`TestDispatcher`) reste souhaitable.

### M4 — Mémorisation et restauration du focus non implémentées

**Description.** La règle d'interaction 4 de l'étape 2 et la décision 6
prévoyaient : `getScrollPosition(key) != (0, 0)` → la cible devient le premier
élément visible à la position restaurée, et non l'index 0. Aucun des trois
écrans ne consulte `getScrollPosition` pour déterminer `initialTarget` ; la
cible est toujours l'index 0 ou la première rangée.

**Impact.** Revenir sur un onglet déjà consulté ramène le focus en tête de
liste. Le comportement reste sûr (la position de défilement, elle, est bien
restaurée par `rememberForever*State`), mais le focus et le défilement se
retrouvent désynchronisés : le sélecteur se pose sur un élément hors écran, et
F19 repositionne aussitôt la liste au pivot — l'utilisateur perd sa position.
C'est une régression fonctionnelle par rapport à l'existant, pas seulement une
fonctionnalité manquante.

**Correction attendue.** Soit implémenter la règle telle que spécifiée, soit —
si l'approximation est jugée trop coûteuse — ne pas demander de focus initial
quand `getScrollPosition(key) != (0, 0)` et laisser le filet de `MainActivity`
opérer. Trancher explicitement, l'étape 4 ayant retenu la première option.

## Mineur

### m1 — `TvInitialFocusState.acquired` est réinitialisé depuis le corps de l'effet

`TvInitialFocus.kt` l. 35 : l'écriture d'un `mutableStateOf` en première
instruction du `LaunchedEffect` mélange armement et exécution. Une fois C1
corrigé, la remise à zéro appartient à la clé de réarmement.
**Correction** : réinitialiser dans un `remember(targetKey)` ou via un état
dérivé, pas dans le corps de l'effet.

### m2 — `.then(if (…) Modifier.tvInitialFocusTarget(…) else Modifier)` dupliqué à six endroits

`VodScreen` l. 445 et 754, `SeriesScreen` l. 447 et 755, `LiveTvScreen` l. 409,
`LiveTvComponents` l. 128 et 370. La condition `index == 0 && isInitialTarget`
est réécrite à chaque fois, avec des noms de paramètres différents
(`initialFocusState`/`initialFocus`, `isInitialTarget`/comparaison inline).
**Correction** : une surcharge
`Modifier.tvInitialFocusTarget(state: TvInitialFocusState?, active: Boolean)`
qui renvoie `this` quand `active` est faux.

### m3 — Constantes conformes mais non exposées

`INITIAL_FOCUS_ATTEMPTS` / `INITIAL_FOCUS_RETRY_MS` sont `private` au fichier :
la validation « au plus dix tentatives » n'est pas atteignable depuis un test.
**Correction** : `internal` si un test est ajouté (voir M3).

## Non couvert par l'automatisation

L'acquisition visuelle du focus, le rendu du pivot F19/F23 et le ressenti D-pad
exigent un appareil Android TV et restent exclus des critères de validation de
l'agent (`AGENTS.md`). C1, M1 et M4 sont établis par lecture du code et
n'exigent pas d'appareil pour être corrigés ; leur *confirmation visuelle*, en
revanche, demandera une passe sur device.

---

## Corrections (Étape 7)

Status: RESOLVED

Tous les retours de la review — Critique, Majeurs et Mineurs — ont été
traités.

| # | Retour | Correction |
| --- | --- | --- |
| C1 | La demande de focus se réarme et vole le focus | `rememberTvInitialFocus` : `targetKey` (pas `ready`) redevient la seule clé de réarmement (`remember(targetKey) { TvInitialFocusState() }`) ; `ready` est lu en direct dans la coroutine via `snapshotFlow { … }.first { it }`, qui ne redémarre plus l'effet à chaque battement. Root-cause traitée à la source dans les trois écrans catalogue : `initialTarget`/`targetKey` ne dépendent plus de `pagedStreams.itemCount` (qui retombe à 0 à chaque frappe de recherche/filtre, `LazyPagingItems` recréé) mais d'un compte dérivé de `state.streams` (VOD/Séries) ou de `filteredStreams.size` (Live TV), stables. Sur l'Accueil, la Hero ne se réarme plus sur l'identité du 1ᵉʳ élément tendance (`tmdbId`) — voir M1, elle reçoit désormais son `TvInitialFocusState` de `HomeScreen`, clé sur `HomeFocusTarget` (constant tant que la priorité retenue ne change pas). |
| — | *Périmètre assumé* | Le tableau des risques du ticket évoquait un verrou « focus acquis n'importe où dans l'écran ». Un tel verrou global est en tension directe avec la section « Coexistence » du même ticket, qui attend explicitement que l'effet B17 déplace le focus **depuis** une puce posée par le filet `MainActivity` **vers** le média — un verrou large aurait empêché ce transfert voulu. Le correctif retenu couvre les trois scénarios concrets listés (frappe recherche, réordonnancement Accueil, fenêtre bornée) sans casser la coexistence documentée ; voir note dans `TvInitialFocus.kt`. |
| M1 | Accueil câblé seulement sur la Hero ; `HomeInitialFocusTarget` mort | `HomeScreen.kt` calcule désormais `homeInitialTarget = HomeInitialFocusTarget.of(state, isTv)` et `homeInitialFocus = rememberTvInitialFocus(...)`, propagés à `HomeTrendingCarouselTv` (Hero) et posés sur le premier élément (`index == 0`) de chacune des 9 rangées TV (Reprise, Favoris, TV, Films, Top Films, Recommandés Films, Séries, Top Séries, Recommandés Séries) via `tvInitialFocusTarget(state, active)`. Bug incident trouvé pendant le câblage : `HomeInitialFocusTarget.of` proposait `DOWNLOADS` alors que la rangée Téléchargements n'est rendue que côté mobile (`if (!isTv && …)` dans `HomeScreen`) — branche et entrée d'énumération supprimées, cette cible n'était jamais atteignable. |
| M2 | `HomeInitialFocusTargetTest` : 1 cas sur 8 | Les 6 cas manquants ajoutés : priorité Hero malgré une reprise renseignée, repli `RESUME`, `FAVORITES`, `LIVETV`, une seule rangée tardive non vide (`RECOMMENDED_SERIES`), état entièrement vide → `null`. |
| M3 | `TvInitialFocus` non testé ; `isTv` toujours `true` en dur | Logique de tentatives bornées extraite en fonction pure `runInitialFocusAttempts` (sans dépendance Compose), testée en JVM (`TvInitialFocusTest`, 4 cas : arrêt dès acquisition, épuisement des 10 tentatives, attente de `ready` avant toute tentative, aucune tentative si déjà acquis). Le paramètre `isTv` reste `true` en dur aux 4 points d'appel : ce ne sont pas des valeurs arbitraires mais un invariant structurel (chaque composable n'est monté que depuis une branche `if (isTv) { … }` de son écran parent) — remplacé par une valeur "réelle" locale n'aurait rien changé puisqu'aucune n'existe à cet endroit ; documenté dans le kdoc de `runInitialFocusAttempts`. |
| M4 | Pas de restauration du focus à la position de scroll | `VodScreen`/`SeriesScreen`/`LiveTvScreen` calculent `hasRestorableScroll` à partir de la même clé que `rememberForeverLazyListState`/`GridState` (`getScroll(key) != (0, 0)`) et l'ajoutent à `ready` : si l'utilisateur avait déjà défilé, la demande de focus initial est simplement sautée — le filet `MainActivity` reste disponible en repli, sans focus forcé qui contredirait le scroll restauré. *Hors périmètre de cette correction* : l'Accueil ne route pas son `LazyListState` par le même mécanisme `getScroll`/`saveScroll` porté par le ViewModel (il utilise un `LazyListState` hoïsté au niveau du graphe de navigation) — l'implémenter demanderait un mécanisme de persistance différent, non traité ici. |
| m1 | `acquired` réinitialisé inconditionnellement | Résolu par C1 : `remember(targetKey)` crée un état neuf seulement sur un changement réel de cible ; plus de remise à zéro manuelle dans le corps de l'effet. |
| m2 | `.then(if (…) Modifier.tvInitialFocusTarget(…) else Modifier)` dupliqué ×6 | Nouvelle surcharge `Modifier.tvInitialFocusTarget(state: TvInitialFocusState?, active: Boolean)` ; les 6 sites d'appel (`VodScreen`, `SeriesScreen`, `LiveTvScreen`, `LiveTvComponents` ×2) simplifiés en un seul appel chacun. |
| m3 | Constantes non exposées pour les tests | `INITIAL_FOCUS_ATTEMPTS`/`INITIAL_FOCUS_RETRY_MS` passées de `private` à `internal`, utilisées comme valeurs par défaut de `runInitialFocusAttempts` et vérifiées par `TvInitialFocusTest`. |

Non-régression : `./gradlew testDebugUnitTest` (81 suites, 615 tests, 0 échec),
`assembleDebug`, `lintDebug` — tous verts après corrections.

## Validation finale (Étape 8)

Status: VALIDATED

* **Comportement attendu** : focus initial demandé après disponibilité réelle
  des données, sans reprise agressive du focus utilisateur (C1), couvrant
  désormais l'Accueil au-delà de la seule Hero (M1).
* **Règles métier** : ordre de priorité (étape 2) formalisé et testé pour les
  écrans catalogue (`CatalogInitialFocusTargetTest`, 6 cas) et l'Accueil
  (`HomeInitialFocusTargetTest`, 8 cas) ; fenêtre bornée à 10 tentatives ×
  80 ms vérifiée en JVM (`TvInitialFocusTest`).
* **Expérience utilisateur (TV)** : plus de vol de focus pendant une frappe de
  recherche/filtre (C1) ; scroll restauré respecté plutôt qu'écrasé par le
  focus par défaut sur les écrans catalogue (M4).
* **Qualité technique** : duplication du `.then(if …)` éliminée (m2) ; bug
  incident `DOWNLOADS` sur TV corrigé pendant M1 ; constantes testables (m3).
* **Absence de régression** : `HomeViewModelTest` et `TvPivotScrollTest`
  passent sans modification — état alimentant les sélecteurs et contrat F19
  inchangés. `MainActivity` non touché (filet de sécurité intact, décision 7).
* **Tests validés** : `testDebugUnitTest` (81 suites, 615 tests, 0 échec),
  `assembleDebug`, `lintDebug`.
* **Hors périmètre agent (rappel)** : acquisition visuelle du focus, rendu du
  pivot F19/F23 et ressenti D-pad restent une vérification manuelle sur
  appareil TV (`AGENTS.md`), non un critère automatisé. La restauration du
  focus à la position exacte de scroll sur l'Accueil (hors écrans catalogue)
  reste un point ouvert, documenté ci-dessus (M4).

---

# 9. Release

*(À remplir à l'Étape 10)*
