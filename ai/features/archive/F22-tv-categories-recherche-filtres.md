# F22 - Liste déroulante de catégories, recherche et filtres avancés sur TV (Films et Séries)

## Informations générales

Status:
RELEASED

Created:
2026-08-02

Released:
v1.69.0 - 2026-08-02 - commit: 8dcf1ab

---

# 1. Description

Sur Android TV, la navigation et le filtrage des **Films (VOD)** et des **Séries** manquent actuellement d'ergonomie et d'homogénéité :
1. **Sélection de catégorie** : Les catégories sont présentées sous forme de puces horizontales (`LazyRow`). Faire défiler des dizaines de catégories horizontalement à la télécommande (D-pad) est lent et fastidieux. L'objectif est de remplacer ce bandeau par un bouton sélecteur/déroulant (Dropdown) épuré qui ouvre une liste de sélection de catégories (verticale et focusable).
2. **Recherche et filtres avancés** : Dans les catégories spécifiques (autres que "Tout"), l'utilisateur dispose d'une simple barre de recherche textuelle, mais n'a aucun moyen de trier ou filtrer (par note minimale, année de sortie, genre), alors que l'écran de Recherche globale dispose déjà d'un superbe système de filtre avancé (bouton Tune). L'objectif est d'intégrer à côté de la barre de recherche un bouton de filtres avancés, identique à celui de la Recherche, pour appliquer des filtres multicritères sur la catégorie active.

Cette évolution s'applique aux écrans **Films (VOD)** et **Séries** en mode TV pour unifier l'expérience avec l'écran de Recherche.

---

# 2. Contexte

Actuellement :
* Dans `VodScreen.kt` et `SeriesScreen.kt` en mode `TvLayout` :
  * Le bandeau de catégories est un `LazyRow` horizontal de `CategoryFilterChip`s.
  * Si une catégorie spécifique est active, un `OutlinedTextField` de recherche textuelle s'affiche seul sur toute la largeur, sans bouton de filtres.
* Dans `SearchScreen.kt` :
  * On a une barre de recherche à côté d'un `IconButton` de filtres avancés (`Icons.Default.Tune`) qui ouvre une bottom sheet (sur mobile) ou un panneau de filtres.
  * Les filtres appliqués sont affichés sous forme de chips d'actions supprimables (`ActiveFilterChipsRow`).
  * Les filtres avancés gèrent le type de média, la catégorie, la note minimale, la plage d'années, et les genres.

Nous souhaitons réutiliser et adapter ces composants de filtre et de recherche dans `VodScreen.kt` et `SeriesScreen.kt` pour le mode TV.

---

# 3. Spécification fonctionnelle

## Objectif

Améliorer radicalement l'ergonomie de navigation sur TV en remplaçant le défilement horizontal des catégories par un bouton de liste déroulante (Dropdown) vertical, et en dotant les catégories spécifiques d'une barre de recherche couplée à un bouton de filtres avancés identique à celui de la Recherche globale.

## User stories

* **En tant qu'utilisateur Android TV**, lorsque je consulte la page des Films ou des Séries, je vois en haut de l'écran un bouton sélecteur indiquant la catégorie active (ex: "FILMS : TOUT" ou "SÉRIES : ACTION"). En cliquant dessus au D-pad, une liste verticale et focusable de toutes mes catégories s'ouvre, me permettant de changer instantanément de catégorie sans défilement horizontal fastidieux.
* **En tant qu'utilisateur Android TV**, lorsque je sélectionne une catégorie spécifique (ex: "Action"), je vois apparaître une barre de recherche à côté d'un bouton de filtres (icône d'égaliseur/Tune). 
* **En tant qu'utilisateur Android TV**, en cliquant sur ce bouton de filtres, je peux affiner la liste des films de cette catégorie en sélectionnant par exemple "Note minimale : 4★" ou "Années : 2020 - 2026", de façon identique à la recherche globale. Les filtres actifs apparaissent sous forme de badges cliquables pour être facilement retirés.

## Règles métier et d'interaction

1. **Sélecteur de catégorie (Dropdown TV)** :
   * Remplacer la `LazyRow` de puces par un composant de type bouton (semblable à `CategorySelectorTrigger` mais adapté pour la TV avec focus et gestion D-pad).
   * Au clic, ouvrir un menu déroulant ou un dialogue modal contenant la liste verticale des catégories (focusable au D-pad).
2. **Barre de recherche & Filtres (Catégories spécifiques uniquement)** :
   * S'afficher uniquement si la catégorie sélectionnée n'est pas "Tout" (All).
   * Afficher la barre de recherche textuelle côte à côte avec le bouton d'ouverture des filtres (icône `Tune`).
   * Le bouton de filtre doit afficher un état visuel "actif" (fond lavande ou indicateur) si au moins un filtre avancé est appliqué.
3. **Application des filtres** :
   * Les filtres de note, d'années de sortie et de genres s'appliquent en temps réel sur la liste ou la grille des médias de la catégorie courante.
   * Afficher la rangée de chips de filtres actifs sous la barre de recherche.

## Critères d'acceptation (Fonctionnels)

- [ ] Sur TV, le bandeau horizontal de puces de catégories est remplacé par un bouton sélecteur déroulant (Dropdown) vertical et focusable.
- [ ] Sur TV, en mode "Catégorie spécifique", une barre de recherche est affichée à côté d'un bouton de filtres avancés (icône `Tune`).
- [ ] Cliquer sur le bouton de filtres ouvre le panneau de sélection de filtres avancés.
- [ ] Les filtres appliqués affichent des puces actives supprimables en dessous de la barre de recherche.
- [ ] Le filtrage s'applique en temps réel et met à jour la grille de médias.
- [ ] Le mobile conserve ses composants de filtrage actuels (Bottom sheet de catégories et filtre simple de recherche).

## Cas limites et gestion des erreurs

- Une catégorie sans média affiche l'état vide existant ; la liste de catégories reste navigable et le panneau de filtres ne bloque pas le retour au sélecteur.
- Changer de catégorie réinitialise la recherche textuelle et les filtres propres à la catégorie précédente afin qu'aucun critère ne soit appliqué silencieusement à une autre catégorie.
- Fermer le panneau par Retour D-pad ou en choisissant « Annuler » conserve la liste telle qu'elle était avant son ouverture ; seule une validation explicite applique les changements.
- Les catégories, années, genres ou notes absents des métadonnées ne provoquent pas d'erreur : ils ne satisfont simplement pas le filtre correspondant.

## Hypothèses et Questions ouvertes

* *Architecture des filtres* : Les filtres de la Recherche sont gérés par `FavoritesViewModel`. Pour les catégories spécifiques, il faudra adapter la logique de filtrage dans `VodViewModel` et `SeriesViewModel` (ou créer une logique commune/Usecases réutilisables). Ceci sera détaillé à l'Étape 3 (Spécification technique).

---

# 4. Spécification technique

## Ce qui est réutilisable en l'état

| Composant | Emplacement | Réutilisable ? |
| --- | --- | --- |
| `AdvancedSearchSheet` | `presentation/search/AdvancedSearchSheet.kt` l. 89-104 | **Oui**, tel quel ou presque : déjà `public`, déjà stateless (filtre + 8 callbacks), et déjà doté d'un paramètre `isTv` qui remplace le `RangeSlider` par des steppers focusables au D-pad. |
| `AdvancedSearchFilter` | `domain/model/AdvancedSearchFilter.kt` | **Oui**, tel quel (`mediaType`, `categoryId`, `minRating`, `yearRange`, `genres`, `isActive`). |
| `GetTopGenresUseCase`, `GetCatalogYearRangeUseCase` | `domain/usecase/` | **Oui**, déjà utilisés par `FavoritesViewModel` (l. 105). |
| `ActiveFilterChipsRow` | `presentation/search/SearchScreen.kt` l. 654 | **Non en l'état** : déclaré `private`. À extraire (voir ci-dessous). |
| Prédicats de filtrage | `AdvancedCatalogSearchUseCase` l. 82-115 | **Non en l'état** : logique inline dans le `UseCase`, non réutilisable. À extraire en objet pur. |
| `CategorySelectorTrigger` | `presentation/components/CatalogFilterComponents.kt` l. 54 | Style réutilisable, mais conçu pour un clic tactile ; une variante TV focusable est nécessaire. |
| `CategoryFilterSheet` | `CatalogFilterComponents.kt` l. 158 | **Non** : `ModalBottomSheet` mobile. Le pendant TV est un dialogue plein écran (voir décision 2). |

## Nouveaux composants

### 1. `domain/model/CatalogFilterMatcher.kt` (nouveau, pur, testable)

Extraction des prédicats aujourd'hui inline dans `AdvancedCatalogSearchUseCase`
(note minimale, plage d'années, genres), afin que la Recherche globale **et** les
écrans Films/Séries appliquent rigoureusement les mêmes règles :

```kotlin
/**
 * Prédicats de filtrage avancé, partagés entre la Recherche globale
 * (AdvancedCatalogSearchUseCase) et le filtrage par catégorie des écrans
 * Films/Séries (F22). Objet pur, sans dépendance Android ni coroutine :
 * deux implémentations divergentes des mêmes critères produiraient des
 * résultats incohérents entre les deux écrans.
 */
object CatalogFilterMatcher {
    fun matchesRating(rating: String?, minRating: Int?): Boolean
    fun matchesYear(releaseYear: Int?, range: IntRange?): Boolean
    fun matchesGenres(genre: String?, selected: Set<String>): Boolean   // via GenreParser

    /** Note + année + genres. Ni le type de média ni la catégorie (portés par le contexte). */
    fun matchesContent(
        rating: String?, releaseYear: Int?, genre: String?,
        filter: AdvancedSearchFilter
    ): Boolean
}
```

Sémantique reprise **à l'identique** de l'existant, y compris ses subtilités :
note absente ou illisible → `0.0` (donc exclue dès qu'un minimum est demandé) ;
`releaseYear == null` exclu **uniquement** si un filtre année est actif (cf.
commentaire l. 108-110 du `UseCase`).

`AdvancedCatalogSearchUseCase` est réécrit pour déléguer à cet objet. C'est le
seul point de refactorisation d'un composant existant fonctionnel : il est
couvert par les tests unitaires du `UseCase`, qui doivent passer **sans
modification** — c'est le critère de non-régression.

### 2. `presentation/components/TvCategoryPicker.kt` (nouveau)

```kotlin
/** Bouton sélecteur TV : libellé « FILMS : ACTION », focusable, anneau D-pad. */
@Composable fun TvCategorySelectorTrigger(label: String, onClick: () -> Unit, modifier: Modifier = Modifier)

/**
 * Liste verticale focusable des catégories, présentée en `Dialog` plein écran.
 * Le focus est demandé sur la catégorie active à l'ouverture ; Retour D-pad
 * ferme sans rien changer.
 */
@Composable fun TvCategoryPickerDialog(
    entries: List<CategorySheetEntry>,   // réutilise le modèle existant (id, label, count)
    selectedId: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
)
```

### 3. `presentation/components/ActiveFilterChipsRow.kt` (extraction)

`ActiveFilterChipsRow` est déplacé de `SearchScreen.kt` vers
`presentation/components/`, rendu `public`, signature inchangée. `SearchScreen`
l'importe ; aucun changement de rendu.

### 4. `AdvancedSearchSheet` — deux paramètres ajoutés

```kotlin
fun AdvancedSearchSheet(
    …,
    showMediaTypeFilter: Boolean = true,   // NOUVEAU
    showCategoryFilter: Boolean = true,    // NOUVEAU
    …
)
```

Dans un contexte « catégorie de l'écran Films », le type de média est implicite
(Film) et la catégorie est déjà choisie par le sélecteur : afficher ces deux
sections permettrait à l'utilisateur de se contredire (filtrer sur « Séries »
depuis l'écran Films). Valeurs par défaut `true` → `SearchScreen` inchangé.

## Composants impactés

| Fichier | Modification |
| --- | --- |
| `domain/model/CatalogFilterMatcher.kt` | **NOUVEAU** |
| `domain/usecase/AdvancedCatalogSearchUseCase.kt` | Délègue au matcher (comportement identique) |
| `presentation/components/TvCategoryPicker.kt` | **NOUVEAU** |
| `presentation/components/ActiveFilterChipsRow.kt` | **NOUVEAU** (extraction) |
| `presentation/search/SearchScreen.kt` | Suppression du `private fun ActiveFilterChipsRow`, import du composant extrait |
| `presentation/search/AdvancedSearchSheet.kt` | 2 paramètres optionnels |
| `presentation/vod/VodViewModel.kt` | État et actions de filtre par catégorie |
| `presentation/series/SeriesViewModel.kt` | Idem |
| `presentation/vod/VodScreen.kt` | `TvLayout` : bandeau de puces → sélecteur ; barre de recherche + bouton `Tune` + chips actifs ; filtrage appliqué à `pagedStreams` |
| `presentation/series/SeriesScreen.kt` | Idem |

## État et actions ajoutés aux ViewModels

```kotlin
data class VodState(
    …
    val advancedFilter: AdvancedSearchFilter = AdvancedSearchFilter.DEFAULT,
    val isFilterSheetOpen: Boolean = false,
    val availableGenres: List<String> = emptyList(),   // genres de la catégorie active
    val categoryYearRange: IntRange? = null,           // bornes de la catégorie active
    val filteredCount: Int = 0                         // aperçu « N résultats » de la sheet
)
```

Actions calquées sur `FavoritesViewModel` (l. 169-296) : `setFilterSheetOpen`,
`setMinRating`, `setYearRange`, `toggleGenre`, `resetFilter`, `applyFilter`, et
les `removeXxxFilter` alimentant les chips. `setMediaType`/`setCategory` ne sont
**pas** répliqués (sections masquées).

**Réinitialisation au changement de catégorie** (règle métier de l'étape 2) :
`selectCategory()` (`VodViewModel` l. 238-241) remet `advancedFilter` à
`AdvancedSearchFilter.DEFAULT` et ferme la sheet. La recherche textuelle est déjà
réinitialisée côté écran (`LaunchedEffect(state.selectedCategory) { searchQuery = "" }`,
`VodScreen.kt` l. 101-103) ; elle le reste.

`availableGenres` et `categoryYearRange` sont dérivés de `state.streams` — qui
contient déjà **exactement** les flux de la catégorie active
(`observeStreams(categoryId)`, l. 243-254) — et non du catalogue complet : les
genres proposés sont donc ceux réellement présents dans la catégorie, et aucune
lecture supplémentaire n'est nécessaire. Calcul dans un `map` du flux existant,
hors thread principal.

## Application du filtre à la grille

En mode « Catégorie spécifique », la grille est alimentée par Paging 3
(`pagedStreams`, `VodScreen.kt` l. 121-129) et le filtre texte est déjà appliqué
par `pagingData.filter { … }`. Les filtres avancés suivent le même chemin :

```kotlin
val pagedStreams = remember(viewModel.pagedStreams, searchQuery, state.advancedFilter) {
    val filter = state.advancedFilter
    if (searchQuery.isBlank() && filter.isEmpty) viewModel.pagedStreams
    else viewModel.pagedStreams.map { pagingData ->
        pagingData.filter { stream ->
            (searchQuery.isBlank() || stream.name.contains(searchQuery, ignoreCase = true)) &&
            CatalogFilterMatcher.matchesContent(stream.rating, stream.releaseYear, stream.genre, filter)
        }
    }
}.collectAsLazyPagingItems()
```

`VodStream` porte déjà `rating`, `releaseYear` et `genre`
(`VodRepositoryImpl.toDomain()` l. 263-266) : aucun accès base supplémentaire,
aucune requête modifiée, la pagination est préservée. Le filtrage reste donc
**en temps réel** (critère d'acceptation n° 5) sans recharger la catégorie.

`filteredCount`, affiché par la sheet (`resultCount`), est calculé sur
`state.streams` (liste non paginée de la catégorie, déjà en mémoire) et non sur
`pagedStreams` — `LazyPagingItems.itemCount` ne connaît que les pages chargées.

## Modèles de données, API, services, stockage, cache

Aucune entité Room, aucune migration (base en version 21), aucun DTO, aucun
endpoint Xtream, aucune interface Retrofit (donc aucune règle `-keep` à ajouter).
Aucun appel réseau. Lecture du cache local existant uniquement.

## Performances

* **Filtrage** : prédicat O(1) par item, appliqué paresseusement par Paging à
  l'intérieur des pages chargées. Aucune matérialisation de la catégorie
  complète.
* **Genres et bornes d'années** : dérivés une fois par changement de catégorie,
  dans le `map` du flux `state.streams` (hors thread principal), sur une liste
  déjà chargée.
* **Compteur de résultats** : parcours O(n) de `state.streams`, recalculé à
  chaque modification de filtre. Sur une catégorie plafonnée par T10 côté
  affichage mais complète en mémoire, cela peut représenter quelques milliers
  d'éléments — à exécuter dans un `derivedStateOf` côté ViewModel avec
  `Dispatchers.Default` si un jank est constaté. Le même compromis existe déjà
  dans `FavoritesViewModel.triggerRealtimeCount()` (l. 297-317).
* **Sélecteur de catégorie** : remplace une `LazyRow` de N puces
  **toutes composées et focusables** par un bouton unique + un dialogue composé
  à la demande. Gain net sur l'arbre de focus TV.

## Sécurité

Sans objet.

## Compatibilité

* **Mobile strictement inchangé** (critère d'acceptation n° 6) : toutes les
  modifications d'écran sont dans les composables `TvLayout` ; `MobileLayout`
  conserve `CategorySelectorTrigger` + `CategoryFilterSheet` + `CategorySearchField`.
  Les deux nouveaux paramètres d'`AdvancedSearchSheet` ont des valeurs par défaut
  qui reproduisent le comportement actuel de `SearchScreen`.
* **Recherche globale** : `SearchScreen` conserve son rendu ; seules l'origine de
  `ActiveFilterChipsRow` (import) et l'implémentation interne des prédicats
  changent.
* **F19/F23 (focus TV)** : le dialogue de catégories et la sheet de filtres sont
  des surfaces modales, hors des listes à pivot. Avec F23, le focus quittant les
  listes déclenche `clear()` du sélecteur — le cadre disparaît proprement à
  l'ouverture d'un panneau, ce qui est le comportement attendu.
* **min SDK 21** : `ModalBottomSheet` (Material3) est déjà utilisé sur TV par
  `SearchScreen`, aucune API nouvelle.

## Dépendances

Aucune dépendance Gradle ajoutée.

## Risques techniques

| Risque | Gravité | Mitigation |
| --- | --- | --- |
| Régression de la Recherche globale par l'extraction des prédicats | Élevée | Les tests existants d'`AdvancedCatalogSearchUseCase` doivent passer **sans être modifiés** ; la sémantique exacte (note absente = 0.0, `releaseYear` null exclu seulement si filtre année actif) est reprise littéralement. |
| `ModalBottomSheet` peu ergonomique au D-pad | Moyenne | Déjà en production sur TV via `SearchScreen` avec `isTv = true` (steppers, anneaux de focus). Aucune régression introduite ; si l'ergonomie est jugée insuffisante, c'est un sujet transverse à la Recherche, pas à F22. |
| Focus perdu à la fermeture du dialogue de catégories | Moyenne | `FocusRequester` sur le déclencheur, focus redemandé à la fermeture — même patron que `MainActivity.contentFocusRequester` (l. 206, 248-251). |
| Filtre silencieusement conservé d'une catégorie à l'autre | Fonctionnelle | Réinitialisation explicite dans `selectCategory()`, testée unitairement. |
| Compteur de résultats coûteux sur très grosse catégorie | Perf | Calcul hors thread principal ; patron déjà éprouvé dans `FavoritesViewModel`. |
| Duplication VOD/Séries | Maintenance | La logique est identique mais les types (`VodStream`/`SeriesStream`) et les ViewModels diffèrent. Le partage passe par `CatalogFilterMatcher` (prédicats) et les composants UI ; le reste est du câblage symétrique assumé, à l'image de ce qui existe déjà entre `VodScreen` et `SeriesScreen`. |
| Chevauchement avec T10 et B18 sur les mêmes fichiers | Conflits | Ordre de livraison conseillé : **T10 → B18 → F22** (du plus mécanique au plus structurant), F22 en dernier car il réécrit l'en-tête de `TvLayout`. |

## Contraintes de performance

Le filtrage doit rester perçu comme instantané : aucun appel suspendu, aucune
requête base sur le chemin de l'application d'un filtre. Seul le compteur de
résultats est asynchrone.

---

# 5. Architecture

## Position dans la Clean Architecture

Les critères de filtrage sont une règle métier : ils descendent en `domain`
sous forme d'objet pur (`CatalogFilterMatcher`), au même titre que
`GenreParser`, `ReleaseYearParser` ou `TopRatedSelector`, conformément à la
structure attendue (`domain/model` : « objets purs testables : parsers/matchers »).
Aucun accès `data` nouveau : les écrans consomment les flux existants.

```
domain/model/
├── AdvancedSearchFilter.kt      ← existant, inchangé
├── CatalogFilterMatcher.kt      ← NOUVEAU : prédicats purs partagés
└── GenreParser.kt               ← existant, utilisé par le matcher

domain/usecase/
├── AdvancedCatalogSearchUseCase ← délègue au matcher (comportement identique)
├── GetTopGenresUseCase          ← existant
└── GetCatalogYearRangeUseCase   ← existant

presentation/components/
├── TvCategoryPicker.kt          ← NOUVEAU (trigger TV + dialogue focusable)
├── ActiveFilterChipsRow.kt      ← EXTRAIT de SearchScreen
└── CatalogFilterComponents.kt   ← existant (chemin mobile), inchangé

presentation/search/AdvancedSearchSheet.kt   ← + showMediaTypeFilter / showCategoryFilter
presentation/vod/{VodViewModel, VodScreen}.kt       ← état de filtre + en-tête TV
presentation/series/{SeriesViewModel, SeriesScreen}.kt
```

## Flux de données — sélection de catégorie (TV)

```
TvCategorySelectorTrigger  ("FILMS : ACTION")
        │ clic OK
        ▼
TvCategoryPickerDialog (LazyColumn focusable, focus sur la catégorie active)
        │
        ├─ Retour D-pad / Annuler ──► onDismiss : aucun changement d'état
        │
        └─ OK sur une entrée ──► onSelect(categoryId)
                                      │
                                      ▼
                        VodViewModel.selectCategory(category)
                          • streams = emptyList() ; observeStreams(id)
                          • advancedFilter = DEFAULT      ← NOUVEAU
                          • isFilterSheetOpen = false     ← NOUVEAU
                                      │
                                      ▼
                        VodScreen : searchQuery = "" (LaunchedEffect existant)
```

## Flux de données — filtres avancés (catégorie spécifique, TV)

```
state.streams (catégorie active, déjà chargée)
        │
        ├──► GetTopGenresUseCase / bornes d'années  ──► availableGenres, categoryYearRange
        │
        ▼
Bouton Tune (fond AccentLavande si filter.isActive)
        │
        ▼
AdvancedSearchSheet(showMediaTypeFilter = false, showCategoryFilter = false, isTv = true)
        │  onMinRatingSelected / onYearRangeChanged / onGenreToggled
        ▼
VodViewModel.advancedFilter
        │
        ├──► ActiveFilterChipsRow (chips supprimables sous la barre de recherche)
        │
        ├──► filteredCount ──► "N résultats" dans la sheet
        │
        └──► VodScreen : pagedStreams.filter { texte && CatalogFilterMatcher.matchesContent(...) }
                    │
                    ▼
            LazyVerticalGrid (Paging 3, pivot F19 inchangé)
```

## Responsabilités des composants

* **`CatalogFilterMatcher`** : définir *ce que filtrer signifie*, une fois pour
  toute l'application. Ni la Recherche ni les écrans catalogue ne réimplémentent
  un critère.
* **`AdvancedSearchSheet`** : présenter et éditer un `AdvancedSearchFilter`.
  Elle ignore d'où vient le filtre et à quoi il s'applique — d'où sa
  réutilisabilité directe.
* **`VodViewModel` / `SeriesViewModel`** : détenir le filtre de la catégorie
  active, le réinitialiser à chaque changement de catégorie, exposer genres,
  bornes d'années et compteur.
* **`VodScreen` / `SeriesScreen` (`TvLayout`)** : composer l'en-tête TV
  (sélecteur, recherche, bouton `Tune`, chips) et appliquer le prédicat au flux
  Paging.
* **`TvCategoryPicker`** : ergonomie D-pad de la sélection de catégorie. Aucune
  connaissance des films ni des séries (il consomme `CategorySheetEntry`).

## Décisions techniques

1. **Extraire les prédicats en `domain/model` plutôt que dupliquer.** Deux
   implémentations de « note minimale 4★ » divergeraient tôt ou tard, et
   l'utilisateur verrait des résultats différents entre la Recherche et l'écran
   Films pour un même critère.
2. **Dialogue plein écran plutôt que `DropdownMenu` pour les catégories TV.**
   `DropdownMenu` est ancré au déclencheur, plafonné en hauteur et sa gestion du
   focus D-pad n'est pas fiable en `androidx.compose.material3` ; le projet a déjà
   un précédent de liste verticale focusable plein écran qui fonctionne bien en
   D-pad (`ProfileSelectionScreen`).
3. **Réutiliser `AdvancedSearchSheet` plutôt qu'écrire un panneau TV dédié.**
   Elle est déjà stateless, déjà paramétrée `isTv`, déjà utilisée sur TV. Deux
   panneaux de filtres divergeraient visuellement — l'inverse de l'objectif
   d'unification du ticket.
4. **Masquer type de média et catégorie dans la sheet contextuelle.** Le contexte
   les détermine ; les laisser modifiables permettrait à l'utilisateur de se
   contredire et créerait un état incohérent à réconcilier.
5. **Filtre dans le ViewModel, recherche textuelle laissée dans l'écran.** Le
   filtre doit survivre à la recomposition et être testable en JVM ; la recherche
   textuelle a déjà son mécanisme de réinitialisation qui fonctionne, le déplacer
   serait une modification gratuite hors périmètre.
6. **Filtrage sur `PagingData` plutôt que reconstruction d'une liste.** Conserve
   la pagination, évite toute requête et donne un rendu instantané.
7. **Genres et années dérivés de la catégorie, pas du catalogue.** Proposer un
   genre absent de la catégorie active produirait systématiquement zéro
   résultat.
8. **`ActiveFilterChipsRow` extrait plutôt que dupliqué.** Composant purement
   présentationnel, déjà générique ; sa duplication créerait un second endroit à
   corriger.

## Stratégie de tests

**`CatalogFilterMatcherTest`** (JVM pur, priorité haute — objet `domain`) :
1. note absente / vide / non numérique → exclue dès qu'un `minRating` est fixé ;
2. note ≥ seuil incluse, note < seuil exclue ;
3. `releaseYear == null` **inclus** si `yearRange == null`, **exclu** si un
   `yearRange` est actif (préservation de la subtilité existante) ;
4. année dans / hors plage ;
5. genres : correspondance via `GenreParser`, casse et séparateurs
   hétérogènes des panels Xtream ; ensemble vide = pas de filtre ;
6. filtre `DEFAULT` → tout passe.

**`AdvancedCatalogSearchUseCaseTest`** (existant) :
7. **doit passer sans aucune modification** après la délégation au matcher —
   c'est la garantie de non-régression de la Recherche globale.

**`VodViewModelTest` / `SeriesViewModelTest`** :
8. `selectCategory()` réinitialise `advancedFilter` à `DEFAULT` et ferme la
   sheet ;
9. `toggleGenre` ajoute puis retire un genre ;
10. `setYearRange` normalise une plage inversée (comportement déjà présent dans
    `FavoritesViewModel` l. 211-222) ;
11. `removeMinRatingFilter` / `removeYearRangeFilter` / `removeGenreFilter`
    laissent les autres critères intacts ;
12. `availableGenres` et `categoryYearRange` sont dérivés des flux de la
    catégorie active et se mettent à jour au changement de catégorie ;
13. `filteredCount` reflète le filtre courant.

Le rendu TV (dialogue de catégories, focus D-pad, bottom sheet) exigerait un
device : exclu des critères de validation de l'agent (`AGENTS.md`).

Non-régression : `./gradlew testDebugUnitTest`, `assembleDebug`, `lintDebug`.

---

# 6. Plan de développement

## Ordre d'exécution

Les prédicats sont mutualisés avant toute UI ; le sélecteur TV est ensuite
câblé, puis les filtres de catégorie. Cette séquence empêche une divergence
avec la Recherche globale.

### Tâche 1 — Extraire et tester le matcher de filtres commun

- [x] Créer `CatalogFilterMatcher` pur et faire déléguer
  `AdvancedCatalogSearchUseCase` aux prédicats partagés.

Objectif : conserver une sémantique identique note/année/genre entre Recherche,
VOD et Séries.

Fichiers : `domain/model/CatalogFilterMatcher.kt`,
`AdvancedCatalogSearchUseCase.kt` et tests domaine.

Validation : valeurs absentes, bornes de note/année et genres normalisés sont
couverts ; la recherche globale conserve ses résultats.

### Tâche 2 — Exposer l'état et les actions de filtre par écran catalogue

- [x] Ajouter les états temporaires/appliqués et les actions de sélection,
  réinitialisation de catégorie et application du filtre aux ViewModels.

Objectif : filtrer une catégorie spécifique en temps réel sans fuite d'un état
vers une autre catégorie.

Fichiers : `VodViewModel.kt`, `SeriesViewModel.kt`, états UI et use cases de
genres/années réutilisés.

Validation : changement de catégorie réinitialise recherche et filtres ; annuler
ne modifie pas la grille ; type/catégorie restent imposés par le contexte.

### Tâche 3 — Créer les composants TV de sélection et filtres

- [x] Ajouter le déclencheur de catégorie focusable, le dialogue vertical TV et
  extraire les chips actives réutilisables.

Objectif : remplacer les puces horizontales TV sans toucher au bottom sheet
mobile existant.

Fichiers : `CatalogFilterComponents.kt`, composant TV nouveau,
`SearchScreen.kt` (extraction `ActiveFilterChipsRow`) et `AdvancedSearchSheet.kt`
si adaptation minimale nécessaire.

Validation : Retour D-pad ferme le dialogue ; la cible focus est restaurée ;
mobile garde ses composants et interactions actuels.

### Tâche 4 — Câbler VOD/Séries TV et les états de rendu

- [x] Remplacer les `LazyRow` de catégories TV et composer recherche, bouton
  Tune, panneau et chips seulement en catégorie spécifique.

Objectif : livrer le parcours complet sur les deux écrans, sans modifier le
mode « Tout » ni les grilles mobiles.

Fichiers : `VodScreen.kt`, `SeriesScreen.kt` et composants TV nouveaux.

Validation : filtres actifs sont visibles et supprimables ; grille vide sûre ;
les résultats se mettent à jour sans requête réseau.

### Tâche 5 — Tester et vérifier l'ensemble

- [x] Ajouter les tests ViewModel/domaine puis exécuter les contrôles automatisés.

Fichiers : tests du matcher, `VodViewModelTest.kt`, `SeriesViewModelTest.kt`.

Validation : recherche, filtre, changement de catégorie et annulation sont
couverts ; `testDebugUnitTest`, `assembleDebug`, `lintDebug` passent ; la
navigation D-pad reste une validation visuelle séparée.

---

# 7. Notes de développement

Implémentation étape 5 : prédicats partagés (`CatalogFilterMatcher`), état de
filtre isolé par catégorie dans les ViewModels VOD/Séries, sélecteur TV
vertical, bouton Tune, panneau de filtres contextuel et chips actifs. Le mobile
conserve son sélecteur et sa recherche actuels. Les tests JVM couvrent les
prédicats ; la vérification D-pad reste à traiter en review sur un appareil TV.

Couverture ViewModel (points 8 à 13 de la stratégie de tests), ajoutée à
`VodViewModelTest` et `SeriesViewModelTest` — 5 tests symétriques par écran :
changement de catégorie remettant `advancedFilter` à `DEFAULT` et fermant la
sheet (genres et bornes suivant la nouvelle catégorie), `toggleGenre`
ajout/retrait et `applyFilter` ne fermant que la sheet, `setYearRange`,
`removeMinRating` / `removeYearRange` / `removeGenre` laissant les autres
critères intacts, `availableGenres` / `filteredCount` dérivés de la catégorie
active.

Écart assumé avec le point 10 de la stratégie de tests : la normalisation
implémentée est celle de `FavoritesViewModel` (l. 211-222) — une plage couvrant
toutes les bornes de la catégorie est ramenée à `null`, pour ne pas afficher un
chip « actif » qui ne filtre rien. Les plages inversées ne sont pas retournées :
`AdvancedSearchSheet` ne peut pas en produire (steppers bornés), et l'ajouter
ici ferait diverger les deux écrans de la Recherche globale.

Validation : `testDebugUnitTest` (80 suites, 603 tests, 0 échec),
`assembleDebug` et `lintDebug` passent.

---

# 8. Review

Revue de l'implémentation livrée à l'étape 5 (aucun code modifié).

## Ce qui tient

* **Extraction des prédicats** : `CatalogFilterMatcher` est pur, sans dépendance
  Android, et `AdvancedCatalogSearchUseCase` s'y ramène sans changer de
  sémantique (note absente → `0.0`, `releaseYear` null exclu seulement si un
  filtre année est actif). `AdvancedSearchDomainTest` passe **sans avoir été
  modifié** : le critère de non-régression de la Recherche globale est tenu.
* **Isolation du filtre par catégorie** : `selectCategory()` remet
  `advancedFilter`, `isFilterSheetOpen` et `filteredCount` à zéro dans les deux
  ViewModels, et `availableGenres` / `categoryYearRange` sont dérivés du flux de
  la catégorie active — pas du catalogue complet, conformément à la décision 7.
* **Filtrage sur `PagingData`** : appliqué dans le `map`/`filter` du flux Paging,
  sans requête ni matérialisation de la catégorie (décision 6 respectée).
* **Mobile** : le diff ne touche que les composables `TvLayout` ;
  `CategorySelectorTrigger`, `CategoryFilterSheet` et `CategorySearchField`
  restent en place. Les deux nouveaux paramètres d'`AdvancedSearchSheet` ont
  bien des valeurs par défaut qui laissent `SearchScreen` inchangé.
* **Tests** : `CatalogFilterMatcherTest` + 5 tests par ViewModel (points 8 à 13).
  `testDebugUnitTest` (80 suites, 603 tests), `assembleDebug` et `lintDebug`
  passent.

## Majeur

### M1 — `ActiveFilterChipsRow` a été dupliqué, pas extrait

**Description.** La décision 8 et le tableau « Composants impactés » prévoyaient
de *déplacer* `ActiveFilterChipsRow` de `SearchScreen.kt` vers
`presentation/components/` et de faire importer `SearchScreen`. Dans le code
livré, `SearchScreen.kt` l. 654 conserve son `private fun ActiveFilterChipsRow`
et l'utilise toujours (l. 180) ; le nouveau
`presentation/components/ActiveFilterChipsRow.kt` est une **seconde
implémentation**, utilisée seulement par `VodScreen` et `SeriesScreen`.
`SearchScreen.kt` n'apparaît pas dans le diff.

**Impact.** Exactement ce que la décision 8 voulait éviter : deux endroits à
corriger, et un rendu des chips qui divergera dès la première retouche — alors
que l'unification visuelle avec la Recherche est l'objectif du ticket. Les deux
versions divergent d'ailleurs *déjà* (voir M2).

**Correction attendue.** Supprimer le `private fun ActiveFilterChipsRow` et
`ActiveFilterChip` de `SearchScreen.kt`, importer le composant partagé, et lui
ajouter les paramètres dont la Recherche a besoin (`availableCategories`,
`onRemoveMediaType`, `onRemoveCategory`) avec des valeurs par défaut qui
laissent l'appel des écrans catalogue inchangé.

### M2 — La copie TV des chips perd l'anneau de focus et crée une cible D-pad de 16 dp

**Description.** `components/ActiveFilterChipsRow.kt` l. 50-66 :
* aucun `onFocusChanged` ni bordure d'état — la version de `SearchScreen`
  entoure le chip focalisé d'une bordure `AccentLavande` de 3 dp ;
* le `Row` porte `clickable(enabled = isTv, …)` **et** l'icône `Close` porte un
  second `clickable(onClick = onRemove)` sans condition.

**Impact.** Sur TV, chaque chip expose deux nœuds focusables imbriqués, dont la
croix de 16 dp — précisément ce que le commentaire de `SearchScreen` décrit
comme « trop petit/impraticable » —, et rien n'indique visuellement lequel est
focalisé. Le critère d'acceptation n° 4 (« puces actives supprimables ») est
inatteignable au D-pad dans la pratique.

**Correction attendue.** Résolue mécaniquement par M1 (réutiliser
`ActiveFilterChip` de `SearchScreen`, qui gère déjà focus unique + anneau). À
défaut : un seul `clickable` sur le `Row`, l'icône purement décorative, et une
bordure de focus.

### M3 — `TvCategoryPicker` ne suit pas la décision 2 et n'est pas navigable au D-pad

**Description.** `TvCategoryPicker.kt` :
* c'est un `AlertDialog` Material3, pas le dialogue plein écran arbitré en
  décision 2 sur le précédent de `ProfileSelectionScreen` — la liste est donc
  contrainte à la hauteur du slot `text` d'un `AlertDialog` ;
* aucun `FocusRequester` : la spécification du composant demande explicitement
  que « le focus est demandé sur la catégorie active à l'ouverture ». Rien ne
  prend le focus quand le dialogue s'ouvre ;
* ni `TvCategorySelectorTrigger` (l. 34-44) ni les lignes du dialogue
  (l. 58-68) n'ont d'indicateur de focus, alors que la spécification demande
  « focusable, anneau D-pad ». Le fond lavande de la ligne signale la catégorie
  *sélectionnée*, pas la ligne *focalisée*.

**Impact.** Le premier critère d'acceptation (« bouton sélecteur déroulant
vertical et **focusable** ») n'est vérifiable sur aucun appareil : à l'ouverture
le focus reste où il était, et l'utilisateur déplace un curseur invisible. Le
gain ergonomique qui justifie le ticket n'est pas livré. Le risque « focus perdu
à la fermeture du dialogue », classé Moyen dans l'étape 4, n'a pas non plus reçu
sa mitigation (`FocusRequester` sur le déclencheur, focus redemandé à la
fermeture).

**Correction attendue.** `FocusRequester` sur l'entrée sélectionnée + demande
au premier passage ; état de focus visible (bordure `AccentLavande`) sur le
déclencheur et sur chaque ligne ; `FocusRequester` sur le déclencheur pour
restaurer le focus à la fermeture. Le passage en dialogue plein écran est
souhaitable mais reste secondaire par rapport au focus.

## Mineur

### m1 — État vide trompeur quand un filtre avancé est actif

`VodScreen.kt` l. 417-423 (et jumeau dans `SeriesScreen`) : le message dépend
uniquement de `searchQuery`. Avec une recherche vide et un filtre « Note 4+ »
qui ne laisse rien, l'utilisateur lit « Aucun film dans cette catégorie » alors
que la catégorie est pleine. **Correction** : distinguer le cas
`advancedFilter.isActive` et proposer de réinitialiser les filtres.

### m2 — Chaînes en dur dans le code neuf

« Catégories », « Sélectionné » (`TvCategoryPicker`), « Filtres avancés »,
« Rafraîchir », « Aucun film dans cette catégorie », `"FILMS : …"`
(`VodScreen`), « Note $rating+ », « Retirer $label »
(`ActiveFilterChipsRow`) — dans des écrans qui utilisent `stringResource` à la
ligne d'à côté (`R.string.vod_search_placeholder`, `R.string.home_resume`).
**Correction** : basculer ces libellés dans `strings.xml`.

### m3 — `AdvancedSearchSheet` : corps des nouveaux `if` non réindenté

l. 154-203 : `if (showMediaTypeFilter) {` et `if (showCategoryFilter) {`
enveloppent des blocs restés à leur indentation d'origine, accolade fermante
isolée. **Correction** : réindenter (aucun changement de comportement).

### m4 — `CatalogFilterMatcherTest` couvre 4 des 6 cas de la stratégie de tests

Manquent le cas positif de la note (`"8.0"` avec `minRating = 7` → vrai ; seul
le cas faux est asserté) et le cas « ensemble de genres vide = pas de filtre ».
**Correction** : deux assertions à ajouter.

### m5 — Libellé du déclencheur non capitalisé

`VodScreen.kt` : `"FILMS : ${state.selectedCategory?.categoryName ?: "TOUT"}"`
produit « FILMS : Tout » / « FILMS : Action ». La user story demande
« FILMS : TOUT ». **Correction** : `.uppercase()` sur le nom de catégorie.

## Écart de spécification assumé

Le point 10 de la stratégie de tests annonçait que `setYearRange` « normalise
une plage inversée ». L'implémentation reprend la normalisation réelle de
`FavoritesViewModel` (l. 211-222) : une plage couvrant les bornes de la
catégorie est ramenée à `null`. Les plages inversées ne sont pas retournées —
`AdvancedSearchSheet` ne peut pas en produire (steppers bornés) et l'ajouter
ferait diverger les écrans catalogue de la Recherche globale. Les tests
couvrent le comportement réel ; c'est le texte du ticket qui était imprécis.

## Non couvert par l'automatisation

Rendu du dialogue de catégories, anneaux de focus, navigation D-pad et
ergonomie de la bottom sheet sur TV : exclus des critères de validation de
l'agent (`AGENTS.md`, « exclusion des tests manuels ou sur device »). M2 et M3
sont établis par lecture du code, pas par observation sur appareil.

---

## Corrections (Étape 7)

Status: RESOLVED

Tous les retours de la review — Majeurs et Mineurs — ont été traités.

| # | Retour | Correction |
| --- | --- | --- |
| M1 | `ActiveFilterChipsRow` dupliqué, pas extrait | Suppression de la copie `private` dans `SearchScreen.kt` (composable + `ActiveFilterChip`) ; le composant partagé `presentation/components/ActiveFilterChipsRow.kt` gagne les paramètres optionnels `availableCategories`/`onRemoveMediaType`/`onRemoveCategory` (défauts neutres) pour couvrir aussi la Recherche globale. `SearchScreen` importe désormais le composant partagé — un seul rendu des chips dans toute l'app. |
| M2 | Anneau de focus perdu, croix de 16 dp cliquable en double sur TV | Résolu mécaniquement par M1 : le composant partagé est maintenant l'implémentation de `SearchScreen` (anneau `AccentLavande` sur focus, un seul `clickable` sur toute la puce en TV, croix cliquable seulement hors TV). |
| M3 | `TvCategoryPickerDialog` : `AlertDialog` sans focus, sans anneau | Remplacé par un `Dialog` plein écran (`DialogProperties(usePlatformDefaultWidth = false)`, patron `ProfileSelectionScreen`). `FocusRequester` posé sur l'entrée sélectionnée (repli sur la première si absente) et demandé à l'ouverture (`LaunchedEffect(Unit)`). Anneau `AccentLavande` ajouté sur `TvCategorySelectorTrigger` et sur chaque ligne. Le focus est également redemandé sur le déclencheur à la fermeture du dialogue (`VodScreen`/`SeriesScreen`), couvrant le risque « focus perdu à la fermeture » resté ouvert à l'étape 4. |
| m1 | État vide trompeur avec un filtre actif | Le message distingue maintenant recherche texte / filtre actif sans texte / catégorie réellement vide (`vod_empty_filtered` / `series_empty_filtered` vs `vod_empty_category` / `series_empty_category`). |
| m2 | Chaînes en dur dans le code neuf | Ajout de 11 entrées dans `strings.xml` (`tv_category_picker_title`, `catalog_filters_button_description`, `catalog_refresh_button_description`, `vod_category_selector_label`, `series_category_selector_label`, `catalog_no_search_result`, états vides, etc.) et remplacement des littéraux correspondants dans `TvCategoryPicker.kt`, `VodScreen.kt`, `SeriesScreen.kt`. |
| m3 | Indentation des blocs `if` dans `AdvancedSearchSheet` | Réindenté, aucun changement de comportement. |
| m4 | `CatalogFilterMatcherTest` : 4 cas sur 6 | Ajout de `ratingAboveThresholdIsIncludedAndBelowIsExcluded` et `emptyGenreSetMeansNoFilter`. |
| m5 | Libellé du déclencheur non capitalisé (« FILMS : Tout ») | Le nom de catégorie est mis en majuscules avant interpolation dans `vod_category_selector_label`/`series_category_selector_label` → « FILMS : TOUT » / « FILMS : ACTION ». |

Non-régression : `./gradlew testDebugUnitTest` (81 suites, 615 tests, 0 échec),
`assembleDebug`, `lintDebug` — tous verts après corrections.

## Validation finale (Étape 8)

Status: VALIDATED

* **Comportement attendu** : sélecteur TV plein écran et focusable, recherche +
  filtres avancés en catégorie spécifique, chips supprimables partagés avec la
  Recherche globale — conforme aux user stories et critères d'acceptation.
* **Règles métier** : réinitialisation du filtre au changement de catégorie,
  genres/bornes dérivés de la catégorie active, filtrage `PagingData` sans
  requête — vérifiés par les tests ViewModel (5 par écran, points 8-13 de la
  stratégie de tests).
* **Expérience utilisateur (TV)** : focus visible sur le déclencheur, la liste
  de catégories et les chips (M2/M3 corrigés) ; état vide distingue désormais
  catégorie vide / recherche sans résultat / filtre trop restrictif (m1).
* **Qualité technique** : plus de duplication `ActiveFilterChipsRow` (M1) ;
  chaînes i18n (m2) ; indentation propre (m3).
* **Absence de régression** : `AdvancedCatalogSearchUseCaseTest` /
  `AdvancedSearchDomainTest` passent sans modification — la Recherche globale
  n'a pas changé de comportement. Mobile intact (aucun fichier `MobileLayout`
  touché).
* **Tests validés** : `testDebugUnitTest` (81 suites, 615 tests, 0 échec),
  `assembleDebug`, `lintDebug`.
* **Hors périmètre agent (rappel)** : rendu visuel du dialogue plein écran,
  anneaux de focus et navigation D-pad restent une vérification manuelle sur
  appareil TV (`AGENTS.md`), non un critère automatisé.

---

# 9. Release

*(À remplir à l'Étape 10)*
