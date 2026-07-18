# Évolutions Fonctionnelles (Backlog Actif)

Ce document rassemble les évolutions fonctionnelles et les fonctionnalités (features) ouvertes ou planifiées pour l'application. 

Une fois qu'une fonctionnalité est implémentée et validée, sa description/son prompt doit être déplacé dans le fichier d'archive correspondant : `docs/archive/evolutions-fonctionnelles-terminees.md` afin de garder ce document léger et facile à lire par les agents de développement.

---

## 🎯 Évolutions Actives / Backlog

### 🔎 Feature : Recherche Avancée (filtres Films/Séries)

**Objectif** : ajouter une recherche avancée sur l'écran Recherche, ouverte via une icône de filtres à droite du champ de recherche. Elle ouvre une bottom sheet (mobile) « Recherche avancée » permettant de filtrer par **type de média** (Film **ou** Série — choix exclusif), **catégorie** (dropdown dépendant du type), **note minimum**, **année de sortie** (range slider) et **genres**. Les filtres actifs s'affichent en chips supprimables (×) sous la barre de recherche, et les cartes de résultat affichent année + note.

**Maquettes de référence** : `docs/design-reference/screenshots/advanced-search-{init,filters-open-empty,filters-open-some-selected,result,type-none,category-closed,category-open}.png`.
> ⚠️ Les 3 maquettes `type-none`, `category-closed`, `category-open` (dropdown catégorie) doivent être déposées dans ce dossier par le PO — elles montrent le nouveau sélecteur de catégorie et l'état désactivé « Choisir un type d'abord ».

**Décisions de cadrage validées avec le PO** :
- **Type de média = choix exclusif** : on sélectionne **soit** Film **soit** Série (jamais les deux, jamais aucun une fois qu'on filtre par catégorie). Tant qu'aucun type n'est choisi, le dropdown catégorie est **désactivé** avec le placeholder « Choisir un type d'abord ».
- **Catégorie** : dropdown dépendant du type sélectionné (catégories VOD si Film, catégories Séries si Série), listant « Toutes les catégories » puis chaque catégorie **avec son compteur d'items** (ex. « Action 640 »), comme le filtre catégorie des écrans de liste. Réutilise `getCategoryCounts()` (VodDao/SeriesDao) et le picker existant `presentation/components/CatalogFilterComponents.kt`.
- **Année de sortie** : non stockée sur le catalogue → nouvelle colonne + migration Room + backfill via l'enrichissement background existant (même appel `get_vod_info` / `get_series_info`, coût réseau nul).
- **Genres** : réutiliser la logique `GenreParser` et les genres **déjà stockés en base** (`vod_streams.genre` / `series_streams.genre`). N'afficher que le **top 20 des genres les plus fréquents** du catalogue. Pas de liste FR figée ni de mapping EN→FR.
- **Query vide autorisée** : les filtres seuls doivent produire des résultats (parcours de tout le catalogue Film/Série filtré), sans texte saisi. Le bouton « Voir les résultats (N) » reflète le compte en temps réel.
- **Périmètre** : Films (VOD) + Séries uniquement. La Live TV est exclue de la recherche avancée.

**État des lieux du code** (à connaître avant de commencer) :
- Écran : `presentation/search/SearchScreen.kt` (alimenté par `FavoritesViewModel`).
- Recherche actuelle : `domain/usecase/SearchUnifiedUseCase.kt` (filtre seulement les catégories masquées).
- Genre déjà enrichi en background : `data/repository/VodRepositoryImpl.enrichBatch()` + équivalent `SeriesRepositoryImpl` ; `infoDto.releaseDate` est disponible dans ce même appel.
- `GenreParser` (`domain/model/GenreParser.kt`) : split défensif virgule/slash, `matches()`, exclut les placeholders.
- Modèles domain `VodStream`/`SeriesStream` exposent `rating` mais **ni genre ni année** → à étendre.
- Room `AppDatabase` version **13**, migrations obligatoires (voir AGENTS.md, pas de `fallbackToDestructiveMigration`).

---

#### Tâche 1 — Data : année de sortie sur le catalogue + enrichissement
**Modèle : Opus 4.8 · Effort : M**

> Ajoute la persistance de l'année de sortie sur les entités catalogue Films et Séries pour permettre le futur filtre « année de sortie » de la recherche avancée.
> 1. Ajoute une colonne `releaseYear: Int?` (année parsée, ex. 1989) à `VodStreamEntity` et `SeriesStreamEntity`.
> 2. Écris une migration Room réelle `MIGRATION_13_14` (bump `AppDatabase` en 14, ajoutée à `ALL_MIGRATIONS`) : `ALTER TABLE vod_streams ADD COLUMN releaseYear INTEGER` + idem `series_streams`, sans perte de données. Respecte scrupuleusement les règles Room d'AGENTS.md.
> 3. Dans `VodRepositoryImpl.enrichBatch()` et l'équivalent Séries, parse `infoDto.releaseDate` en année (parsing défensif : formats `"1989"`, `"1989-05-12"`, `"12 May 1989"`, null, vide → `null`) et écris `releaseYear` en même temps que `genre`/`actors`/`director`. Crée un petit objet pur testable `ReleaseYearParser` dans `domain/model/`.
> 4. Élargis la requête `getStreamsNeedingEnrichment` (VodDao + SeriesDao) pour re-sélectionner aussi les lignes où `releaseYear IS NULL` afin de **backfiller** les lignes déjà enrichies (genre non null mais année absente).
> 5. Tests unitaires : `ReleaseYearParser` (cas sales), relecture manuelle de la migration.
> Périmètre strict : pas de changement UI dans cette tâche.

#### Tâche 2 — Domain : modèle de filtre, top 20 genres, use case de filtrage
**Modèle : Sonnet 5 · Effort : M**

> Ajoute la couche domain de la recherche avancée.
> 1. Étends `VodStream` et `SeriesStream` (domain) avec `genre: String?` et `releaseYear: Int?` ; mets à jour les mappers entity→domain correspondants (`rating` existe déjà).
> 2. Crée `domain/model/AdvancedSearchFilter.kt` avec un `enum MediaType { FILM, SERIE }` et `data class AdvancedSearchFilter(val mediaType: MediaType?, val categoryId: String?, val minRating: Int?, val yearRange: IntRange?, val genres: Set<String>)`. `mediaType` est **exclusif** (un seul, ou `null` = aucun type encore choisi). `categoryId = null` signifie « Toutes les catégories ». Ajoute `isActive`/`isEmpty` et une valeur `DEFAULT` (tout à null / plein range). Bornes année globales par défaut 1980–2025. Note la règle : changer `mediaType` **réinitialise** `categoryId` (catégories liées au type).
> 3. Crée `GetTopGenresUseCase` : agrège les genres du catalogue Films + Séries via `GenreParser`, compte les occurrences et renvoie le **top 20** (les plus fréquents), triés par fréquence décroissante. Insensible à la casse (utilise `GenreParser.normalize`), exclut les placeholders.
> 4. Crée `GetCategoriesForTypeUseCase` : pour un `MediaType` donné, renvoie la liste des catégories (nom depuis `VodCategory`/`SeriesCategory`) **avec leur compteur d'items** via `getCategoryCounts()` (VodDao/SeriesDao), plus une entrée « Toutes les catégories » avec le total. Exclut les catégories masquées (cohérent avec `SearchUnifiedUseCase`). Trie comme les écrans de liste.
> 5. Crée `AdvancedCatalogSearchUseCase` qui renvoie un `SearchResult` (vodResults + seriesResults) filtré. Périmètre selon `mediaType` : `null` → **les deux** catalogues (films + séries) ; `FILM` → VOD seul ; `SERIE` → séries seul. Applique une query texte **optionnelle** (si vide → tout le périmètre) PUIS le `AdvancedSearchFilter` : `categoryId` (uniquement si un type est choisi et non null), `minRating` (parse défensif de `rating` string → Double, comparaison `>=`), `yearRange` (`releaseYear in range`, null exclu si un range est défini), genres (`GenreParser.matches` sur au moins un genre sélectionné — OU logique). Réutilise la logique de masquage des catégories cachées.
>   Le compteur « Voir les résultats (N) » = taille totale du `SearchResult` renvoyé. Exemples attendus : query « dragon ball » sans type/filtre → N = matches films+séries ; aucune query + aucun type + aucun filtre → N = **total films + séries** du catalogue.
> 6. Tests unitaires : top 20 genres, catégories+compteurs par type, parsing note, application combinée des filtres, query vide, reset catégorie au changement de type.

#### Tâche 3 — Presentation : état ViewModel de la recherche avancée
**Modèle : Sonnet 5 · Effort : M**

> Câble l'état de la recherche avancée dans la couche présentation de l'écran Recherche (actuellement `FavoritesViewModel`).
> 1. Ajoute à l'état : `advancedFilter: AdvancedSearchFilter`, `isFilterSheetOpen: Boolean`, `availableGenres: List<String>` (top 20), `availableCategories: List<CategoryWithCount>` (dépend du `mediaType` sélectionné, vide si aucun type), `filteredResultCount: Int`.
> 2. Expose les actions : ouvrir/fermer la sheet, sélectionner le type de média (**exclusif** — sélectionner Film désélectionne Série et vice-versa ; recharge `availableCategories` via `GetCategoriesForTypeUseCase` et **remet `categoryId` à null**), sélectionner une catégorie dans le dropdown, set note min, set range année, toggle genre, `resetFilter()`, `applyFilter()`, et suppression d'un chip de filtre individuel.
> 3. Recalcule `filteredResultCount` en temps réel (débounce léger) à chaque changement de filtre ou de query — c'est le nombre affiché sur le bouton « Voir les résultats (N) ». Autorise la query vide (parcours catalogue filtré).
> 4. Applique `AdvancedCatalogSearchUseCase` et `GetTopGenresUseCase`. Aucune logique métier dans les Composables.
> 5. Tests ViewModel : transitions d'état (filtre → count, reset, apply), query vide.
> Note : si greffer ceci sur `FavoritesViewModel` l'alourdit trop, propose un `AdvancedSearchViewModel` dédié et signale-le avant de coder.

#### Tâche 4 — UI mobile : bottom sheet « Recherche avancée »
**Modèle : Opus 4.8 · Effort : L**

> Implémente la bottom sheet de recherche avancée (mobile) fidèle aux maquettes `advanced-search-filters-open-empty.png`, `advanced-search-filters-open-some-selected.png`, `advanced-search-type-none.png`, `advanced-search-category-closed.png` et `advanced-search-category-open.png`. Consulte `docs/design-reference/` pour les tokens exacts (couleurs, radius, typographie) — ne devine pas.
> Composable stateless `AdvancedSearchSheet` (state hoisting) dans un `ModalBottomSheet` :
> - En-tête : titre « Recherche avancée » (BricolageGrotesque) + lien « Réinitialiser » (accent lavande) à droite.
> - Section « CATÉGORIE DU MÉDIA » : deux chips toggle pleine largeur `Film` / `Série` en **sélection exclusive** (radio-like : un seul actif à la fois, état sélectionné = fond lavande).
> - **Dropdown catégorie** (juste sous les chips de type) : composant type select/dropdown fidèle aux maquettes `category-closed`/`category-open`.
>   - Tant qu'aucun type n'est sélectionné : **désactivé**, placeholder grisé « Choisir un type d'abord » (voir `type-none`).
>   - Type sélectionné : libellé « Toutes les catégories » par défaut ; à l'ouverture, liste déroulante des catégories (`availableCategories`) avec le **compteur d'items aligné à droite** (ex. « Action 640 », « Toutes les catégories 8420 »). Réutilise/aligne-toi sur le picker existant `presentation/components/CatalogFilterComponents.kt`.
> - Section « NOTE MINIMUM » : segmented `Toutes` / `6+` / `7+` / `8+` / `9+` (sélection unique, `Toutes` = pas de filtre).
> - Section « ANNÉE DE SORTIE » : `RangeSlider` 1980–2025 ; libellé à droite « Toutes les années » (lien accent) quand plein range, qui devient « 1986 – 2020 » quand restreint ; bornes affichées sous le slider.
> - Section « GENRES » : `FlowRow` de chips (les `availableGenres` top 20 fournis par le VM), multi-sélection, état sélectionné = fond lavande.
> - Bouton primaire bas « Voir les résultats (N) » (fond lavande, N = compte temps réel) qui applique et ferme.
> Câble tous les callbacks vers le VM (Tâche 3). Respecte les conventions Compose d'AGENTS.md.

#### Tâche 5 — UI mobile : bouton filtres, chips actifs, cartes de résultat
**Modèle : Sonnet 5 · Effort : M**

> Intègre la recherche avancée dans `SearchScreen.kt` (maquettes `advanced-search-init.png` et `advanced-search-result.png`).
> 1. Ajoute à droite du champ de recherche un bouton icône « filtres » (sliders/tune) qui ouvre la sheet ; état actif = fond lavande plein quand au moins un filtre est appliqué.
> 2. Sous la barre de recherche, une `LazyRow` de **chips de filtre actifs supprimables** (× à droite) : type de média (`Film` ou `Série`), catégorie sélectionnée (ex. `Action`), `Note 7+`, `1986–2020`, et un chip par genre sélectionné. Le clic sur × retire ce filtre (action VM) et rafraîchit les résultats. Note : retirer le chip de type doit aussi retirer la catégorie associée (dépendance type→catégorie).
> 3. Fais afficher aux cartes de résultat (Films/Séries) une méta ligne « année · ★ note » (ex. `2015 · ★ 7.4`) sous le titre, comme sur `advanced-search-result.png`. Masque proprement les valeurs absentes.
> 4. Les résultats restent groupés par type (n'affiche que les sections non vides — ex. « Séries (2) » seul).
> 5. Tests : rendu conditionnel des chips, action de suppression.

#### Tâche 6 — Adaptation Android TV
**Modèle : Sonnet 5 · Effort : M**

> Adapte la recherche avancée à Android TV (navigation D-pad). Les bottom sheets ne conviennent pas au 10-foot UI : propose un panneau latéral ou un dialog focusable avec `tv-material`.
> - Toutes les facettes (chips type de média exclusif, dropdown catégorie, note, année, genres, bouton résultats, chips actifs) doivent être focusables au D-pad avec un indicateur de focus contrasté (WCAG AA), cohérent avec le reste des écrans TV. Le dropdown catégorie doit s'ouvrir en liste focusable (pas de menu déroulant souris).
> - Réutilise l'état VM (Tâches 2-3) sans le dupliquer ; seule la présentation diffère (`isTv`).
> - Signale toute limite ergonomique du `RangeSlider` au D-pad et propose une alternative (steppers +/-) si nécessaire.
> Vérifie `assembleDebug` + `lintDebug` sur la variante TV.

**Ordre de livraison recommandé** : 1 → 2 → 3 → 4 → 5 → 6. Chaque tâche est livrable et testable indépendamment (build + tests unitaires + commit/tag SemVer selon AGENTS.md).

---

## 💡 Idées futures / Nouveau Backlog

*Ajoutez ici vos nouvelles idées de fonctionnalités pour les prochaines sessions de développement.*
