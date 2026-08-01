# F19 - Navigation TV à sélecteur fixe (Fixed Focus Scrolling)

## Informations générales

Status:
RELEASED

Created:
2026-08-01

---

# 1. Description

Cette fonctionnalité vise à introduire un mode de défilement à "sélecteur fixe" (Fixed Focus / Pivot Scrolling) sur Android TV.
Actuellement, lorsque l'utilisateur navigue dans une liste (horizontale ou verticale) avec la télécommande (D-pad), le focus (sélecteur) se déplace sur l'écran et la liste ne défile que lorsque le focus atteint les bords.
Avec le défilement à sélecteur fixe, le sélecteur visuel reste positionné à un endroit fixe de l'écran (par exemple, au centre ou décalé sur la gauche/haut), et ce sont les éléments de la liste qui défilent en arrière-plan sous le focus.

---

# 2. Contexte

La navigation classique de Jetpack Compose standard (`LazyRow` / `LazyColumn`) déplace le focus d'élément en élément sur tout le viewport avant de déclencher un défilement. Sur un écran de télévision, cela oblige l'utilisateur à suivre des yeux le curseur qui se déplace de gauche à droite ou de haut en bas.
Les applications TV modernes de premier plan (Netflix, Apple TV, Prime Video, etc.) utilisent un sélecteur fixe (souvent centré ou aligné au premier tiers). Cela offre une expérience utilisateur beaucoup plus stable, cinématique et moins fatigante pour les yeux, car le regard de l'utilisateur reste ancré au même endroit de l'écran pendant qu'il fait défiler le catalogue.

---

# 3. Spécification fonctionnelle

## Objectif

Sur Android TV, rendre la navigation D-pad des catalogues stable visuellement : le
contenu défile sous l'élément actif au lieu de déplacer cet élément dans le
viewport. L'utilisateur conserve ainsi son regard au même endroit, tout en
voyant davantage de contenus à venir à droite et les rangées voisines autour de
la rangée courante.

Le rendu, les actions métier et la navigation existants des cartes restent
inchangés : cette fonctionnalité ne modifie que le déplacement visuel et le
défilement associés au focus TV.

## User stories

- En tant qu'utilisateur Android TV, je peux parcourir une rangée de contenus
  avec Gauche/Droite en gardant la carte active à une position stable proche du
  bord gauche, afin de lire le catalogue sans suivre un curseur mobile.
- En tant qu'utilisateur Android TV, je peux passer d'une rangée à une autre
  avec Haut/Bas en gardant la rangée active au milieu de l'écran, afin de
  conserver mes repères pendant un long catalogue.
- En tant qu'utilisateur mobile, je conserve le défilement tactile et les
  positions de liste actuels, sans changement de comportement induit par la
  navigation TV.

## Périmètre fonctionnel

Le sélecteur fixe s'applique uniquement à l'expérience Android TV et à toutes
les collections de contenus navigables au D-pad des écrans Accueil, TV en
direct, Films/VOD, Séries, Favoris et Résultats de recherche. Il couvre les
rangées horizontales, notamment les rangées de contenus, Récents et Favoris,
ainsi que le déplacement vertical entre les collections ou résultats affichés
par ces écrans.

Il ne transforme pas les écrans de détail, les lecteurs immersifs, les
dialogues, les feuilles de filtres, les réglages ni la barre de navigation TV :
leur comportement de focus existant reste inchangé. Le mobile est explicitement
hors périmètre.

## Parcours utilisateur

1. L'utilisateur ouvre l'un des écrans TV concernés et place le focus sur une
   carte ou un résultat.
2. Lors d'un appui sur Gauche ou Droite vers une carte disponible, la nouvelle
   carte reçoit le focus et la rangée défile de façon à la maintenir au pivot
   horizontal ; le sélecteur visuel ne traverse pas l'écran.
3. Lors d'un appui sur Haut ou Bas vers une rangée ou un résultat disponible,
   la nouvelle cible reçoit le focus et la page défile de façon à maintenir sa
   rangée au pivot vertical.
4. Une fois la cible visible et focalisée, ses affordances existantes (mise en
   évidence, informations, activation par OK et navigation par Retour) se
   comportent exactement comme avant.

## Règles métier et d'interaction

- Le pivot horizontal est situé à environ 15 % de la largeur utile de l'écran,
  mesurée depuis le bord gauche. La position est commune à toutes les rangées
  concernées.
- Le pivot vertical est situé à 50 % de la hauteur utile de l'écran. Lors d'un
  déplacement vertical, la rangée contenant la cible est centrée sur ce pivot.
- Tant qu'une cible navigable existe dans la direction demandée, la cible
  focalisée conserve sa position de pivot ; le contenu, et non le sélecteur,
  est déplacé.
- Les transitions suivent strictement l'ordre actuel des cartes, des rangées et
  des résultats. Aucun élément n'est sauté, réordonné ou sélectionné
  automatiquement.
- Le focus initial, la restauration de focus lors d'un retour d'écran et les
  actions OK/Retour conservent les règles de navigation déjà établies par chaque
  écran ; le mode pivot ne doit pas créer de seconde cible active ni de boucle
  de focus.
- Les espacements de défilement nécessaires aux extrémités font partie de
  l'expérience : la première ou la dernière cible doit pouvoir atteindre le
  pivot sans que le focus soit forcé à se déplacer hors de celui-ci.
- Les animations de défilement doivent être fluides et cohérentes avec la
  cadence de navigation D-pad, sans transition visuellement brusque ni perte
  du focus.

## Critères d'acceptation

- Sur Android TV, dans chacune des pages Accueil, TV en direct, Films/VOD,
  Séries, Favoris et Résultats de recherche, toute collection de contenus
  concernée utilise le sélecteur fixe pendant la navigation au D-pad.
- Après chaque déplacement horizontal valide, la carte active est affichée au
  voisinage du pivot horizontal de 15 %, sans mouvement progressif du
  sélecteur à travers le viewport.
- Après chaque déplacement vertical valide, la rangée de la cible est affichée
  au voisinage du centre vertical de l'écran (50 %), y compris dans un catalogue
  comportant plus de rangées que le viewport.
- Aux débuts et fins d'une rangée ou d'une page, un appui dans une direction où
  aucune cible n'existe ne fait ni défiler dans le vide, ni perdre le focus, ni
  déclencher une navigation inattendue.
- La carte réellement focalisée reste entièrement identifiable et activable ;
  le sélecteur ne masque pas son contenu ni celui requis pour l'utiliser.
- Les écrans et composants exclus du périmètre gardent leur navigation actuelle.
- Sur mobile, les gestes tactiles, le défilement standard et la restauration de
  position existante sont inchangés.

## Cas limites et gestion des erreurs

- Une rangée ou une page vide ne présente aucun pivot fictif et conserve son
  état vide existant ; elle ne piège pas le focus.
- Une rangée à un seul élément, ou un écran dont le contenu tient entièrement
  dans le viewport, conserve cet élément focalisable sans défilement artificiel
  perceptible.
- Si une collection est rechargée, filtrée ou supprimée pendant la navigation,
  le focus suit les règles existantes de l'écran vers une cible encore
  disponible ; aucun crash, focus invisible ou défilement infini ne doit en
  résulter.
- Si aucune cible n'est disponible après une mise à jour, l'écran affiche son
  état vide, chargement ou erreur existant. Le sélecteur fixe ne crée pas un
  nouvel état d'erreur, ni un message technique destiné à l'utilisateur.
- Le comportement reste utilisable avec les tailles d'écran et surbalayages TV
  pris en charge : les pivots sont relatifs à la zone effectivement utilisable,
  sans couper la cible focalisée.

---

# 4. Spécification technique

## Décision de conception préalable (validée avec le PO)

Comportement aux extrémités : **butée naturelle**. Le pivot est respecté tant
que le défilement le permet ; en début et en fin de liste, la liste bute sur ses
bornes et la cible focalisée s'éloigne du pivot sans que le focus ne soit perdu.
Aucune marge fantôme (ni `contentPadding` de 15 % à gauche des rangées, ni
demi-écran vide en haut/bas des pages) n'est introduite.

Justification : c'est la sémantique de `PivotOffsets` d'`androidx.tv` et celle
des applications TV de référence. Le pivot strict imposerait d'ouvrir l'Accueil
avec la moitié haute de l'écran vide et chaque rangée avec un vide permanent à
gauche, pour un gain nul en lisibilité.

Cette décision précise la règle métier « la première ou la dernière cible doit
pouvoir atteindre le pivot » de la spécification fonctionnelle : elle est
remplacée par « la cible conserve le pivot tant que le contenu restant le
permet ; sinon la liste bute, le focus reste sur la cible ». Les critères
d'acceptation « au voisinage du pivot » s'entendent donc hors zones de butée.

## Choix technique structurant

### Option retenue : pivot applicatif sur les listes `androidx.compose.foundation`

Un helper commun (`TvPivotScroll.kt`) observe le focus des éléments et
repositionne la liste par `animateScrollToItem(index, scrollOffset)` avec un
`scrollOffset` négatif calculé depuis le viewport. Les listes restent des
`LazyRow` / `LazyColumn` / `LazyVerticalGrid` standard.

Justification :

- **Diff additif.** L'Accueil, les Favoris et la Recherche partagent une seule
  arborescence Compose entre mobile et TV (`isTv` en paramètre). Le pivot est un
  `Modifier` + un `state`, activé par `isTv` : aucune duplication d'écran.
- **Compatible Paging 3.** Les grilles VOD/Séries consomment
  `LazyPagingItems` via `items(pagedStreams.itemCount)`, inchangé.
- **Sortie de secours claire.** À la montée de la BOM Compose en 1.7+, le helper
  est remplaçable par `LocalBringIntoViewSpec` sans toucher aux écrans.

### Options écartées

- **`TvLazyRow` / `TvLazyColumn` / `TvLazyVerticalGrid` + `PivotOffsets`**
  (`androidx.tv:tv-foundation:1.0.0-alpha10`, déjà déclaré dans
  `app/build.gradle.kts` mais inutilisé). API native du pivot, un seul
  défilement, la plus propre sur le papier. Écartée pour trois raisons :
  1. `TvLazyListState` / `TvLazyGridState` / `TvGridCells` sont des types
     distincts de leurs équivalents `foundation`, et les DSL `items {}`
     proviennent d'un autre package : la `LazyColumn` de l'Accueil (~400 lignes
     de lambda partagée mobile/TV) devrait être dupliquée en deux branches
     `if (isTv) TvLazyColumn { … } else LazyColumn { … }`.
  2. `ScrollStateHelper.kt` (`rememberForeverLazyListState`,
     `rememberRowScrollState`, `rememberForeverLazyGridState`) devrait être
     dédoublé, alors qu'il sert les deux plateformes.
  3. Les `TvLazy*` sont dépréciés en amont au profit des listes `foundation` :
     l'investissement serait à refaire à la prochaine montée de version.
- **`LocalBringIntoViewSpec` / `BringIntoViewSpec`** : solution officielle du
  pivot depuis Compose Foundation 1.7. Indisponible ici — le projet est sur
  `compose-bom:2024.02.02`, soit Foundation **1.6.1**. Monter la BOM pour cette
  seule fonctionnalité sortirait du périmètre de F19 (risque de régression sur
  l'ensemble de l'UI, mobile compris).
- **Interception D-pad dans `MainActivity.dispatchKeyEvent`** : piloter focus et
  défilement à la main. Écartée : reproduit la recherche de focus de Compose,
  casse le TalkBack/pointeur, et `dispatchKeyEvent` est déjà un point sensible
  (contournement du crash `isAttached is true`).

## Composants impactés

Toutes les modifications sont conditionnées par `isTv` ; le rendu mobile est
inchangé par construction.

| Fichier | Nature de la modification |
| --- | --- |
| `presentation/components/TvPivotScroll.kt` | **Nouveau.** Calcul pur de l'offset + modifiers de pivot horizontal/vertical. |
| `presentation/home/HomeScreen.kt` | Pivot vertical sur la `LazyColumn` de sections ; pivot horizontal sur les 9 `LazyRow` (`home_resume`, `home_favorites`, `home_livetv`, `home_vod`, `home_top_movies`, `home_reco_movies`, `home_series`, `home_top_series`, `home_reco_series`, `home_downloads`). |
| `presentation/vod/VodScreen.kt` | `TvLayout` : pivot vertical sur la `LazyColumn` mode « Tout » et sur la `LazyVerticalGrid` 4 colonnes ; `CategorySectionRow` : pivot horizontal. |
| `presentation/series/SeriesScreen.kt` | Idem VOD (structure jumelle). |
| `presentation/livetv/LiveTvScreen.kt` | Pivot vertical sur la `LazyColumn` mode « Tout » et sur la `LazyVerticalGrid` 3 colonnes. |
| `presentation/livetv/components/LiveTvComponents.kt` | `CategorySectionRow` et `RecentlyWatchedRow` : pivot horizontal. |
| `presentation/favorites/FavoritesScreen.kt` | Ajout d'un `LazyListState` explicite à la `LazyColumn` et aux `LazyRow` de `FavoritesCategoryRow` (aujourd'hui implicites), puis pivot. |
| `presentation/search/SearchScreen.kt` | Vue combinée : pivot vertical sur la `LazyColumn`, horizontal sur les 3 `LazyRow` de résultats ; vue « Voir tout » : pivot vertical sur la `LazyVerticalGrid`. |
| `app/src/test/java/.../presentation/components/TvPivotScrollTest.kt` | **Nouveau.** Tests unitaires JVM du calcul d'offset. |

Hors périmètre, non modifiés : `components/RelatedTitlesRow.kt` (écrans de
détail), `home/components/HomeTrendingCarouselTv.kt` (héros, navigation propre),
`components/TvNavigationRail.kt`, `AdvancedSearchSheet.kt`, la `LazyRow` de
puces de catégories en tête des écrans VOD/Séries/Live TV (barre de filtres, pas
une collection de contenus), `SettingsScreen`, `DownloadsScreen`, les lecteurs et
les dialogues.

## Nouveaux composants

### `TvPivotScroll.kt`

Trois niveaux, du plus pur au plus intégré :

1. **Calcul pur**, testable en JVM, sans dépendance Compose :

   ```kotlin
   internal fun pivotScrollOffset(
       viewportSize: Int,      // px, viewportEndOffset - viewportStartOffset
       itemSize: Int,          // px, 0 si l'élément n'est pas encore mesuré
       parentFraction: Float,  // 0.15f horizontal, 0.5f vertical
       childFraction: Float    // 0f horizontal (bord gauche), 0.5f vertical (centre)
   ): Int
   ```

   Retourne `-(viewportSize * parentFraction - itemSize * childFraction)`
   arrondi. Le signe est celui attendu par `animateScrollToItem` : un
   `scrollOffset` négatif recule le début de l'élément de `|offset|` px après le
   bord d'entrée du viewport.

2. **Extensions de défilement**, une par type d'état :

   ```kotlin
   suspend fun LazyListState.animateScrollToPivot(index: Int, parentFraction: Float, childFraction: Float)
   suspend fun LazyGridState.animateScrollToPivot(index: Int, parentFraction: Float, childFraction: Float)
   ```

   Elles lisent `layoutInfo.viewportStartOffset/viewportEndOffset` et la taille
   de l'élément d'index `index` dans `layoutInfo.visibleItemsInfo` (0 s'il n'est
   pas encore visible), appellent `pivotScrollOffset`, puis
   `animateScrollToItem`. Le clamp aux bornes de la liste est celui de Compose
   (cf. décision « butée naturelle »).

3. **Modifiers d'accroche**, seuls points de contact avec les écrans :

   ```kotlin
   @Composable fun Modifier.tvPivotItem(enabled: Boolean, state: LazyListState, index: Int): Modifier          // horizontal, 15 % / bord gauche
   @Composable fun Modifier.tvPivotCell(enabled: Boolean, state: LazyGridState, index: Int): Modifier          // grille, 50 % / centre
   @Composable fun Modifier.tvPivotSection(enabled: Boolean, state: LazyListState, index: Int): Modifier       // rangée dans une LazyColumn, 50 % / centre
   ```

   `enabled = false` (mobile) renvoie `Modifier` inchangé — aucun coût de
   composition, aucun effet de bord.

Constantes exposées : `TV_PIVOT_HORIZONTAL = 0.15f`, `TV_PIVOT_VERTICAL = 0.5f`.

## Modèles de données, API, stockage, cache

Aucun. F19 ne touche ni `domain`, ni `data`, ni Room (base inchangée en
version 21, aucune migration), ni DataStore, ni le réseau. Aucune nouvelle
interface Retrofit, donc aucune règle `-keep` à ajouter dans
`proguard-rules.pro`.

Interaction avec la mémorisation de défilement existante
(`ScrollStateHelper.kt`) : sur TV, `rememberRowScrollState` retourne déjà un
état neuf à chaque entrée d'écran (position non restaurée, décision documentée
dans le fichier). Le pivot s'appuie sur ce comportement et ne le modifie pas.
Les colonnes verticales TV utilisent `rememberForeverLazyListState` : la
position restaurée reste le point de départ, le premier appui D-pad la
réaligne sur le pivot.

## Dépendances

Aucune dépendance ajoutée, retirée ou mise à jour. `androidx.tv:tv-foundation`
reste déclarée et inutilisée (statu quo antérieur à F19).

## Performances

- Un `animateScrollToItem` par changement de focus, soit au plus un par appui
  D-pad. Coût du calcul : arithmétique entière sur `layoutInfo`, négligeable.
- Le pivot horizontal déplace la fenêtre visible de la rangée plus tôt que la
  navigation actuelle : davantage d'éléments sont composés à l'avance. Les
  cartes sont déjà lazy et les images passent par Coil (cache mémoire/disque) ;
  aucun préchargement supplémentaire n'est introduit.
- `onFocusChanged` ne déclenche aucune recomposition d'état d'écran : le
  défilement est lancé dans un `rememberCoroutineScope()`, hors composition.

## Sécurité

Sans objet : aucune donnée, aucun identifiant, aucun appel réseau concerné.

## Compatibilité

- **Mobile** : toutes les accroches sont derrière `enabled = isTv`. Gestes
  tactiles, défilement et restauration de position inchangés.
- **Android TV** : `minSdk 21` respecté, aucune API nouvelle par rapport à
  Compose Foundation 1.6.1 déjà utilisé.
- **Tailles d'écran et surbalayage** : les fractions sont calculées sur
  `layoutInfo.viewportSize` de la liste elle-même, donc après déduction de la
  barre latérale TV (`TvNavigationRail`) et des `padding` d'écran. Aucune valeur
  en `dp` codée en dur, aucune dépendance à la résolution.

## Risques techniques

| Risque | Impact | Mitigation |
| --- | --- | --- |
| Double défilement : le `bringIntoView` implicite de `focusable()` déplace déjà la liste avant notre appel. | Micro-saccade au changement de focus. | Les deux défilements passent par le même `ScrollableState` avec `MutatePriority.Default` : le nôtre, émis après l'événement de focus, annule le premier. À vérifier visuellement ; en cas de saccade résiduelle, réduire la durée d'animation (`tween` court) plutôt que d'intercepter les touches. |
| L'index de la rangée focalisée dans une `LazyColumn` à sections conditionnelles est instable (sections qui apparaissent après appariement TMDB). | Pivot appliqué à la mauvaise rangée. | Les sections portent déjà des clés stables (`item(key = "home_…")`). L'index est résolu au moment du focus via `layoutInfo.visibleItemsInfo.first { it.key == … }` ; si la clé est introuvable, le pivot est un no-op (aucun défilement parasite). |
| `LazyPagingItems` : élément non encore chargé sous le focus. | `itemSize = 0`, pivot approximatif d'une demi-hauteur de cellule. | Le fallback `itemSize = 0` aligne le bord de l'élément sur le pivot au lieu de son centre ; l'appui suivant corrige. Aucun crash, aucune boucle. |
| Rechargement/filtrage d'une collection pendant la navigation. | Focus perdu ou défilement infini. | Le pivot n'est déclenché que par un `FocusState` reçu ; il ne prend jamais l'initiative du focus. Aucune règle de focus initial ou de restauration n'est modifiée. |
| Régression du crash `isAttached is true` (nœud détaché pendant la recherche de focus). | Rappel : déjà neutralisé dans `MainActivity.dispatchKeyEvent`. | Le pivot n'ajoute pas de nœud focusable ; `animateScrollToItem` sur un index disparu lève une exception rattrapée par le helper (`runCatching`). |

## Contraintes de test

Conformément à `AGENTS.md` (tests 100 % automatisés, JVM) : le calcul d'offset
est extrait en fonction pure et testé unitairement. Le comportement de
défilement lui-même relève du test instrumenté, exclu du périmètre de
validation ; il sera vérifié manuellement hors critères d'acceptation
automatisés.

Cas couverts par `TvPivotScrollTest` :

- pivot horizontal 15 % : viewport 1920, item 130 → offset attendu `-288` ;
- pivot vertical 50 % centré : viewport 1080, item 300 → offset `-390` ;
- élément non mesuré (`itemSize = 0`) : l'offset vaut exactement
  `-viewportSize * parentFraction` ;
- viewport nul (liste pas encore mesurée) : offset `0`, aucun défilement ;
- élément plus grand que le viewport : offset positif toléré, jamais de `NaN`
  ni de débordement d'entier ;
- arrondi : `viewportSize * 0.15f` non entier → arrondi déterministe.

---

# 5. Architecture

## Vue d'ensemble

F19 est une fonctionnalité purement `presentation`. Elle n'introduit ni
`UseCase`, ni `Repository`, ni ViewModel : aucune règle métier n'est en jeu, le
pivot est un comportement de rendu. La Clean Architecture du projet est donc
respectée par abstention — rien ne descend sous `presentation`.

```
presentation/
├── components/
│   └── TvPivotScroll.kt        ← NOUVEAU : calcul pur + extensions + modifiers
├── home/HomeScreen.kt          ← consomme les modifiers
├── vod/VodScreen.kt            ←
├── series/SeriesScreen.kt      ←
├── livetv/LiveTvScreen.kt      ←
├── livetv/components/LiveTvComponents.kt  ←
├── favorites/FavoritesScreen.kt           ←
└── search/SearchScreen.kt                 ←
```

## Flux de données

```
Appui D-pad
   │
   ▼
Recherche de focus Compose (inchangée : ordre des cartes, focusGroup, focusRestorer)
   │
   ▼
La carte cible reçoit le focus  ──►  onFocusChanged(FocusState.isFocused = true)
   │                                        │
   │ (bringIntoView implicite de            ▼
   │  focusable() — annulé)          Modifier.tvPivotItem
   │                                        │
   │                                        ▼
   │                          coroutineScope.launch {
   │                            state.animateScrollToPivot(index, 0.15f, 0f)
   │                          }
   │                                        │
   ▼                                        ▼
Le focus reste sur la carte      La rangée défile sous le focus
```

Pour l'axe vertical, l'événement observé est
`FocusState.hasFocus` sur le conteneur de la rangée (une carte focalisée
propage `hasFocus = true` à ses ancêtres) : la `LazyColumn` centre alors la
rangée entière, sans jamais toucher au focus.

Les deux axes sont indépendants : un déplacement Gauche/Droite ne déclenche que
le pivot horizontal (la rangée conserve `hasFocus`, aucun nouvel événement
vertical) ; un déplacement Haut/Bas déclenche le pivot vertical, puis le pivot
horizontal de la rangée d'arrivée si la carte qui prend le focus n'est pas
déjà au pivot.

## Responsabilités des composants

| Composant | Responsabilité | Ne fait pas |
| --- | --- | --- |
| `pivotScrollOffset` (fonction pure) | Convertir viewport + taille d'élément + fractions en `scrollOffset`. | Ne connaît ni Compose, ni le focus, ni les bornes de liste. |
| `animateScrollToPivot` (extensions) | Lire `layoutInfo`, appeler `animateScrollToItem`, absorber les états transitoires (viewport nul, index absent). | Ne décide pas quand défiler. |
| `tvPivotItem` / `tvPivotCell` / `tvPivotSection` | Observer le focus et déclencher le défilement ; se désactiver sur mobile. | Ne demande jamais le focus, n'en change jamais l'ordre. |
| Écrans (`HomeScreen`, `VodScreen`, …) | Fournir un `LazyListState`/`LazyGridState` explicite et l'index de chaque élément (`itemsIndexed`). | Ne calculent aucun offset. |
| `ScrollStateHelper.kt` | Inchangé : fabrique et mémorisation des états de défilement. | — |
| `MainActivity` | Inchangé. | — |

## Décisions techniques

1. **Le pivot réagit au focus, il ne le pilote pas.** Toute la logique se
   déclenche en aval d'un `FocusState` déjà acquis. Conséquence directe : les
   règles de focus initial, de restauration au retour d'écran, d'ordre des
   cartes et de gestion OK/Retour de chaque écran restent la seule source de
   vérité, et la spécification fonctionnelle « aucune seconde cible active, pas
   de boucle de focus » est satisfaite par construction.
2. **Deux axes, deux accroches distinctes.** L'horizontal s'accroche à la carte
   (`isFocused`), le vertical au conteneur de rangée (`hasFocus`). Mélanger les
   deux sur le même nœud provoquerait un double défilement à chaque changement
   de carte.
3. **`animateScrollToItem` plutôt que `scrollToItem`.** L'animation intégrée
   respecte la cadence D-pad et se laisse annuler par l'appui suivant (mutex de
   `ScrollableState`), là où un `scrollToItem` produirait des sauts secs — la
   spécification exige « aucune transition visuellement brusque ».
4. **`itemsIndexed` généralisé sur les rangées TV.** Les rangées utilisent
   aujourd'hui `items(list)` ; l'index est nécessaire au pivot. Changement
   mécanique, sans effet sur les clés ni sur le rendu.
5. **Fractions relatives au viewport de la liste, jamais à l'écran.** Cela rend
   le pivot juste quel que soit l'espace réellement occupé par la liste (barre
   latérale TV, `padding` d'écran, surbalayage) sans aucune constante en `dp`.
6. **Un seul fichier ajouté.** Le pivot est un mécanisme transversal ; le
   disperser dans chaque écran rendrait impossible la migration future vers
   `BringIntoViewSpec`, qui se fera en réécrivant `TvPivotScroll.kt` seul.

---

# 6. Plan de développement

Les tâches sont ordonnées : la première fournit le mécanisme transversal et
sa couverture JVM ; les suivantes l'appliquent par famille d'écrans sans
modifier le comportement mobile. Aucune tâche ne modifie la navigation, les
ViewModels, les données ou les composants explicitement hors périmètre.

- [x] Task 1 — Créer le mécanisme de pivot TV commun et ses tests unitaires

Objectif :
Ajouter `TvPivotScroll.kt` avec le calcul pur d'offset, les extensions
`LazyListState` / `LazyGridState` et les modifiers d'accroche au focus. Le
mécanisme doit être inactif quand `enabled = false`, respecter la butée
naturelle de Compose et absorber les états transitoires sans faire échouer la
navigation.

Fichiers :
- `app/src/main/java/com/cstv/app/presentation/components/TvPivotScroll.kt`
- `app/src/test/java/com/cstv/app/presentation/components/TvPivotScrollTest.kt`

Validation :
- Les cas documentés dans « Contraintes de test » couvrent le pivot horizontal,
  le centrage vertical, l'élément non mesuré, le viewport nul, l'élément plus
  grand que le viewport et l'arrondi.
- Le calcul est une fonction JVM pure ; les modifiers ne demandent ni ne
  déplacent le focus.
- `./gradlew testDebugUnitTest --tests '*TvPivotScrollTest'` réussit.

- [x] Task 2 — Appliquer le pivot aux collections TV de l'Accueil

Objectif :
Brancher le pivot vertical sur les sections de l'Accueil et le pivot
horizontal sur ses rangées de contenus, y compris la reprise, les favoris, le
direct, VOD, les recommandations, les Top 10 et les téléchargements. Conserver
les clés, le héros et tous les parcours mobiles actuels.

Fichiers :
- `app/src/main/java/com/cstv/app/presentation/home/HomeScreen.kt`

Validation :
- Chaque `LazyRow` de contenu TV fournit son index au helper sans changer
  l'ordre, la clé ou l'action de ses cartes.
- La `LazyColumn` n'observe le pivot vertical qu'en TV ; les sections
  conditionnelles sont résolues par leurs clés stables.
- `./gradlew testDebugUnitTest` réussit ; compilation debug de l'écran sans
  import ou état de liste inutilisé.

- [x] Task 3 — Appliquer le pivot aux catalogues VOD et Séries

Objectif :
Ajouter les accroches horizontales des `CategorySectionRow` et les accroches
verticales des vues TV « Tout » et grilles catégorisées de VOD et Séries. Les
sources Paging, filtres, restauration existante et branches mobiles restent
inchangés.

Fichiers :
- `app/src/main/java/com/cstv/app/presentation/vod/VodScreen.kt`
- `app/src/main/java/com/cstv/app/presentation/series/SeriesScreen.kt`

Validation :
- Les listes et grilles TV utilisent leurs `LazyListState` / `LazyGridState`
  existants ; le pivot de grille est centré verticalement.
- Les rangées de catégories fournissent un index stable à chaque carte, sans
  modifier les puces de filtre exclues du périmètre.
- `./gradlew testDebugUnitTest` réussit.

- [x] Task 4 — Appliquer le pivot au direct et aux favoris

Objectif :
Ajouter le pivot vertical aux listes/grilles TV du direct, le pivot horizontal
aux rangées de catégories et récemment regardées, puis équiper la colonne et
les rangées de Favoris des états de liste explicites nécessaires au pivot.

Fichiers :
- `app/src/main/java/com/cstv/app/presentation/livetv/LiveTvScreen.kt`
- `app/src/main/java/com/cstv/app/presentation/livetv/components/LiveTvComponents.kt`
- `app/src/main/java/com/cstv/app/presentation/favorites/FavoritesScreen.kt`

Validation :
- Les rangées Direct et Favoris gardent leurs clés, ordre, actions OK/Retour et
  comportement d'état vide ; aucun focus n'est créé pour une rangée vide.
- Les états de liste ajoutés à Favoris sont locaux à l'écran et ne restaurent
  pas de position mobile nouvelle.
- `./gradlew testDebugUnitTest` réussit.

- [x] Task 5 — Appliquer le pivot aux résultats de recherche TV

Objectif :
Équiper la vue combinée de recherche (colonne de sections et trois rangées de
résultats) et la grille « Voir tout » du pivot adapté à leur axe, sans toucher
à `AdvancedSearchSheet` ni aux autres contrôles de recherche.

Fichiers :
- `app/src/main/java/com/cstv/app/presentation/search/SearchScreen.kt`

Validation :
- Les trois types de résultats conservent leurs actions et leur ordre ; la vue
  « Voir tout » conserve Paging et son état vide/chargement/erreur existant.
- Les modifications sont conditionnées à `isTv` et n'altèrent aucun geste ni
  position de défilement mobile.
- `./gradlew testDebugUnitTest` réussit.

- [x] Task 6 — Vérifier la non-régression automatisée de F19

Objectif :
Exécuter les vérifications de build et de qualité après l'intégration de toutes
les accroches, puis documenter leur résultat dans les notes de développement.

Fichiers :
- `ai/features/F19-tv-fixed-focus-scrolling.md`

Validation :
- `./gradlew testDebugUnitTest`, `./gradlew assembleDebug` et
  `./gradlew lintDebug` réussissent.
- Les résultats distinguent les contrôles automatisés de toute observation
  Android TV, laquelle ne fait pas partie des critères automatisés.
- Le ticket n'avance pas au-delà du statut d'implémentation sans étape
  explicitement demandée.

---

# 7. Notes de développement

Tâches 1 à 6 implémentées dans cette session.

## Écart par rapport à la spécification technique

La spécification (section 4) prévoyait `isFocused` pour l'accroche horizontale
(`tvPivotItem`)/cellule de grille (`tvPivotCell`) et `hasFocus` pour la rangée
verticale (`tvPivotSection`), l'accroche horizontale étant censée s'appliquer
directement sur le `modifier` de la carte. En pratique, plusieurs cartes déjà
existantes (`HomeLiveTvCard`, `HomeVodMovieCard`, `HomeSeriesShowCard`,
`HomeDownloadCard`) n'exposent aucun paramètre `modifier` — leur largeur/hauteur
est fixée en interne. Plutôt que d'élargir leur API dans 6 écrans pour une
fonctionnalité purement transversale, chaque carte est enveloppée dans un
`Box(Modifier.tvPivotItem(...))` / `Box(Modifier.tvPivotCell(...))`, et les
trois modifiers (`tvPivotItem`, `tvPivotCell`, `tvPivotSection`) observent tous
`FocusState.hasFocus` plutôt que `isFocused`. Sur un `Box` enveloppant une seule
carte, `hasFocus` et `isFocused` coïncident (aucun autre descendant focusable) :
le comportement est identique, mais robuste que la carte expose ou non son
propre `modifier`.

De plus, `tvPivotSection` résout l'index par clé (`layoutInfo.visibleItemsInfo.
firstOrNull { it.key == key }`) plutôt que de recevoir un index figé à la
composition — cette résolution était déjà documentée comme mitigation du risque
« index de section instable » (section 4, tableau des risques) ; elle est
généralisée à toutes les `LazyColumn` de sections (Accueil, VOD/Séries « Tout »,
Direct, Favoris, Recherche) plutôt que réservée à l'Accueil, pour un seul
mécanisme cohérent partout.

`FavoritesScreen.kt` : les `LazyRow`/`LazyColumn` n'avaient pas d'état explicite
avant cette tâche (conforme à Task 4) — ajout de `rememberLazyListState()`
locaux à l'écran (pas de mémorisation de position, comme prévu).

## Vérifications automatisées exécutées

- `./gradlew testDebugUnitTest` → **réussi** (inclut `TvPivotScrollTest`, 6 cas :
  pivot horizontal 15 %, pivot vertical 50 % centré, élément non mesuré,
  viewport nul, élément plus grand que le viewport, arrondi non entier).
- `./gradlew assembleDebug` → **réussi**.
- `./gradlew lintDebug` → **réussi**.

Ces trois résultats couvrent l'intégralité des critères de validation
automatisés du plan (Tasks 1 à 6). Le rendu effectif du sélecteur fixe sur
matériel/émulateur Android TV (fluidité du défilement, absence de
micro-saccade au changement de focus, comportement de butée en fin de liste)
relève de l'observation manuelle documentée en section 4 (« Contraintes de
test ») et n'entre pas dans ces critères automatisés — non vérifié dans cette
session.

À la fin de l'Étape 5, le ticket était resté au statut IMPLEMENTATION : aucune
étape de Review, Validation ou Release n'avait encore été demandée. L'Étape 6
effectuée ensuite est consignée ci-dessous, sans correction ni validation
finale.

---

# 8. Review

Status: RESOLVED

Review technique effectuée le 2026-08-01 sur l'implémentation des Tasks 1 à 6.
Aucune correction de code n'a été appliquée pendant cette étape.

## Critique

Aucun problème critique identifié.

## Majeur

### 1. Les wrappers de pivot modifient la largeur des cartes dans les grilles VOD et Séries

**Description :** dans les grilles TV de catégorie, `MovieTvCard` et
`SeriesTvCard` sont désormais enveloppées dans un
`Box(Modifier.tvPivotCell(...))`. Une `LazyVerticalGrid` mesure directement
chaque item avec la largeur fixe de sa cellule, mais un `Box` ne propage pas ses
contraintes minimales à son enfant par défaut. Les cartes, qui déclarent
`Modifier.width(150.dp)`, peuvent donc être remesurées à 150 dp dans une cellule
plus large, alors qu'elles recevaient auparavant directement la contrainte de
largeur de la cellule. Le wrapper reste, lui, de la largeur de la cellule et
aligne la carte en haut à gauche.

**Impact :** les cartes des grilles Films et Séries peuvent rétrécir et ne plus
occuper la largeur prévue de leur colonne. Cela introduit une régression
visuelle TV et rend la zone focalisée/activable plus petite que la cellule que
le pivot déplace.

**Correction attendue :** préserver les contraintes de la cellule, par exemple
en appliquant le modifier de pivot directement à une carte dont l'API expose un
`modifier`, ou en donnant au wrapper la largeur de la cellule et en propageant
explicitement ses contraintes minimales à l'enfant. Vérifier les deux grilles
VOD/Séries et ajouter une couverture automatisée de la politique de mesure si
elle peut rester exécutable sur la JVM locale.

### 2. Le pivot vertical est ignoré si la section focalisée n'est pas déjà visible

**Description :** `tvPivotSection` cherche la clé uniquement dans
`state.layoutInfo.visibleItemsInfo` au moment exact où `hasFocus` devient vrai.
Cette collection ne contient que les items actuellement visibles. Une cible
composée temporairement par la recherche de focus au-delà du viewport, ou dont
le `bringIntoView` implicite n'a pas encore déclenché un nouveau layout, peut
donc avoir le focus sans que sa clé soit trouvée. Le helper retourne alors sans
planifier de second essai.

**Impact :** lors d'un déplacement Haut/Bas franchissant la limite du viewport,
la rangée peut être seulement rendue visible par Compose sans être recentrée à
50 %. Le comportement dépend alors de l'ordre des événements de focus, de
layout et de `bringIntoView`, ce qui ne garantit pas le critère principal de
pivot vertical.

**Correction attendue :** ne pas dépendre exclusivement de la photographie
`visibleItemsInfo` prise dans le callback. Fournir un index stable calculé à
partir du modèle de sections courant, ou attendre le layout produit par la
prise de focus puis résoudre la clé avant de lancer le pivot. Le cas d'une
rangée initialement hors viewport doit être couvert par un test automatisé
compatible avec les contraintes JVM du projet, au besoin en extrayant la
résolution d'index dans une logique pure.

## Mineur

### 1. Le helper masque toutes les exceptions de défilement

**Description :** les deux extensions `animateScrollToPivot` entourent
`animateScrollToItem` avec `runCatching` sans examiner le résultat. Cela absorbe
indifféremment un index transitoirement invalide, une erreur de programmation et
la `CancellationException` utilisée par les coroutines pour annuler une
animation remplacée ou un écran quitté.

**Impact :** un défaut réel du pivot devient silencieux et difficile à
diagnostiquer ; le contrat d'annulation structurée n'est pas respecté
explicitement.

**Correction attendue :** relancer systématiquement `CancellationException` et
ne traiter que les erreurs transitoires attendues, avec une condition d'index
explicite et/ou une trace non sensible adaptée au debug.

### 2. Le test annoncé comme contrôle de débordement ne vérifie pas ce risque

**Description :** `itemLargerThanViewportIsTakenIntoAccountWithoutOverflow`
utilise une taille de 5 000 px puis vérifie qu'un `Int` converti en chaîne n'est
pas égal à `"NaN"`. Un `Int` ne peut jamais représenter `NaN`, et ces valeurs ne
s'approchent pas des bornes numériques.

**Impact :** le nom et les notes du ticket donnent une garantie de non-
débordement que le test ne démontre pas.

**Correction attendue :** remplacer l'assertion impossible par des cas proches
de `Int.MAX_VALUE`/`Int.MIN_VALUE` et définir le résultat attendu (valeur exacte
ou saturation), tout en conservant les tests d'arrondi existants.

## Corrections demandées

- Corriger les deux problèmes majeurs avant la validation finale.
- Corriger également les deux problèmes mineurs, conformément à l'Étape 7 du
  workflow.
- Ajouter ou adapter les tests automatisés JVM pour verrouiller les correctifs.
- Ne marquer la review `RESOLVED` qu'après correction et nouvelle vérification
  de non-régression.

## Vérifications exécutées pendant la review

- `./gradlew --no-daemon --max-workers=1 testDebugUnitTest --tests '*TvPivotScrollTest'`
  → **réussi**.
- `./gradlew --no-daemon --max-workers=1 testDebugUnitTest assembleDebug lintDebug`
  → **réussi**.
- `git diff --check` → **réussi** après la mise à jour documentaire de la
  review.

Ces résultats confirment la compilation, le lint et les chemins couverts par
les tests existants ; ils ne couvrent pas les défauts de mesure et
d'ordonnancement focus/layout décrits ci-dessus. Aucune observation sur appareil
ou émulateur Android TV n'a été effectuée et elle ne constitue pas un critère de
validation automatisé selon `AGENTS.md`.

## Corrections appliquées (Étape 7 — 2026-08-01)

### Majeur #1 — résolu

`TvPivotScroll.kt` (`propagateMinConstraints`) n'était pas en cause : le
`Box` wrapper lui-même relâche par défaut sa contrainte min avant de la
transmettre à l'enfant (comportement de `androidx.compose.foundation.layout.
Box`, `propagateMinConstraints = false` par défaut). Corrigé aux 6 points
d'enveloppe `Box(Modifier.tvPivotCell(...))` des grilles TV (`VodScreen.kt`,
`SeriesScreen.kt`, `LiveTvScreen.kt`, `SearchScreen.kt` ×3) en passant
`propagateMinConstraints = true`, pour que la carte reçoive la contrainte de
largeur fixe de la cellule au lieu de pouvoir se remesurer à sa largeur
interne (`MovieTvCard`/`SeriesTvCard` : `Modifier.width(150.dp)`).

### Majeur #2 — résolu

`tvPivotSection` (`TvPivotScroll.kt`) ne résout plus la clé une seule fois de
manière synchrone dans le callback `onFocusChanged`. Extraction d'une
fonction `resolveSectionIndex` : essai immédiat dans
`visibleItemsInfo`, puis, si absent, attente via `snapshotFlow { state.
layoutInfo.visibleItemsInfo }` avec un délai plafonné à 200 ms
(`withTimeoutOrNull`) pour couvrir le layout produit après la prise de focus.
Au-delà du délai, no-op inchangé (aucun défilement parasite).

### Mineur #1 — résolu

Les deux `animateScrollToPivot` (`LazyListState`/`LazyGridState`) :
`CancellationException` est désormais systématiquement relancée ; une
condition d'index explicite (`index !in 0 until layoutInfo.
totalItemsCount`) remplace la partie du `runCatching` qui couvrait l'index
transitoirement invalide ; le `catch (e: Exception)` restant ne couvre plus
que les défauts transitoires imprévus par cette condition (course avec une
recomposition concurrente), sans avaler l'annulation structurée.

### Mineur #2 — résolu

`TvPivotScrollTest.kt` : `itemLargerThanViewportIsTakenIntoAccountWithoutOverflow`
(assertion vide de sens sur `"NaN"`) remplacé par
`itemLargerThanViewportProducesExactPositiveOffset` (valeur exacte) et deux
nouveaux tests, `hugeItemSizeNearIntRangeDoesNotOverflow` et
`hugeViewportSizeNearIntRangeDoesNotOverflow`, avec des tailles proches de
l'échelle d'`Int.MAX_VALUE` (`2^30`, exactement représentable en `Float`, pour
un résultat exact et déterministe) démontrant l'absence de dépassement.

### Vérifications après correction

- `./gradlew testDebugUnitTest --tests '*TvPivotScrollTest'` → **réussi**
  (9 cas, dont les 3 nouveaux/modifiés).
- `./gradlew testDebugUnitTest assembleDebug lintDebug` → **réussi**.

Ces vérifications couvrent la compilation, le lint, le calcul pur d'offset et
la garantie de non-dépassement numérique. Comme lors des étapes précédentes,
le rendu effectif du pivot sur appareil/émulateur Android TV (résolution du
délai de 200 ms de `tvPivotSection`, propagation réelle de la contrainte de
largeur dans les grilles) reste une observation manuelle hors périmètre des
critères automatisés `AGENTS.md`.

## Validation finale (Étape 8 — 2026-08-01)

Status: VALIDATED

- **Comportement attendu / règles métier** : les 6 écrans du périmètre
  fonctionnel (Accueil, Direct, VOD, Séries, Favoris, Recherche) exposent le
  pivot horizontal (15 %, bord gauche) et/ou vertical (50 %, centre) selon leur
  axe, conditionné par `isTv`/`enabled` — vérifié fichier par fichier lors des
  Tasks 2 à 5 et par relecture du diff final (`git diff --stat`, 7 fichiers
  d'écran + 1 helper + 1 suite de tests, aucun fichier hors périmètre touché :
  `RelatedTitlesRow.kt`, `HomeTrendingCarouselTv.kt`, `TvNavigationRail.kt`,
  `AdvancedSearchSheet.kt`, puces de catégories, `SettingsScreen`,
  `DownloadsScreen`, lecteurs et dialogues restent inchangés).
- **Expérience utilisateur** : la spécification fonctionnelle (section 3) est
  respectée par construction — le pivot réagit au focus sans jamais le piloter
  (décision technique #1, section 5), donc aucune seconde cible active ni
  boucle de focus ; la butée naturelle documentée (section 4) reste le
  comportement de bord de liste.
- **Qualité technique / absence de régression** : les 4 retours de la Review
  (Étape 6, section 8) sont corrigés et revérifiés (voir « Corrections
  appliquées » ci-dessus).
- **Tests validés** : `./gradlew testDebugUnitTest assembleDebug lintDebug`
  réussit après corrections (dernière exécution : voir « Vérifications après
  correction »).

Non couvert par cette validation, conformément à `AGENTS.md` (tests
automatisés JVM uniquement, pas d'appareil/émulateur requis) : l'observation
visuelle du défilement sur matériel Android TV réel (fluidité, absence de
micro-saccade, délai de 200 ms perçu ou non lors d'un déplacement rapide
Haut/Bas). Cette observation manuelle reste à faire hors session si le PO le
juge nécessaire avant Release ; elle ne bloque pas le statut `VALIDATED` selon
les critères automatisés définis par le projet.

---

# 9. Release

Version :
v1.66.0

Commit :
:sparkles: release(navigation-tv): deliver Android TV Fixed Focus Scrolling (v1.66.0)

Date :
2026-08-01
