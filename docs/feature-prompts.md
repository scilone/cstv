# Prompts de features — backlog idées 2026-07-16

Chaque bloc est un prompt autonome, prêt à être donné tel quel dans une session dédiée. Ancré dans l'état actuel du code (vérifié le 2026-07-16) : fichiers, entités, champs API réellement présents. Décisions déjà tranchées avec l'utilisateur incluses pour éviter les allers-retours.

---

## 1. Gestion des catégories (masquer / réordonner) par profil

✅ **TERMINÉE** — Fable 5. Table Room `category_preferences` (clé composite `categoryId, type, profileId`, migration 12→13), repository `CategoryPreferenceRepository` (avec flux `changes` pour recharger les grilles/Home au retour des Paramètres), filtrage+ordre appliqués dans les 3 use cases de catégories et sur la Home, écran « Gestion des catégories » dans les Paramètres (onglets TV/Films/Séries, toggle masquer, réordonnancement par boutons haut/bas compatibles D-pad), suppression de `CategorySorting.ALPHABETICAL`, nettoyage des préférences à la suppression d'un profil.

Ajoute la possibilité de masquer et réordonner les catégories Live TV / VOD / Séries, avec une configuration **par profil** (pas globale).

Contexte existant :
- 3 entités Room sans notion de visibilité : `LiveCategoryEntity`, `VodCategoryEntity`, `SeriesCategoryEntity` (`data/local/entity/`), chacune avec un `orderIndex` rempli automatiquement à l'ordre de retour de l'API Xtream (`LiveTvRepositoryImpl.kt:66`, `VodRepositoryImpl.kt:258`, `SeriesRepositoryImpl.kt:197`). Les DAO trient déjà `ORDER BY orderIndex ASC` (`VodDao.kt:17`, `LiveTvDao.kt:16`, `SeriesDao.kt:15`).
- Scoping par profil déjà établi ailleurs : `FavoriteEntity` (clé composite `id, type, profileId`) et `PlaybackPositionEntity` (clé composite `streamId, profileId`), voir `data/local/entity/`. Réutilise ce pattern.
- `ProfileEntity.id: Int` est la clé à référencer.

Décision : l'ordre par défaut (avant toute personnalisation par l'utilisateur) reste l'ordre API actuel (`orderIndex`), pas un tri alphabétique.

À faire :
1. Nouvelle table Room (ex. `category_preferences`), clé composite `(categoryId, type, profileId)` où `type` distingue live/vod/series (ou 3 tables séparées si plus simple avec le schéma actuel qui a déjà 3 entités distinctes par média — à toi de juger selon la cohérence avec l'existant). Champs : `hidden: Boolean`, `sortOrder: Int` (nullable/absent = ordre par défaut).
2. Migration Room (voir `AGENTS.md` — pas de `fallbackToDestructiveMigration`, migration explicite requise, `ALL_MIGRATIONS` dans `di/AppModule.kt`).
3. Écran de gestion dans Paramètres (`presentation/settings/`) : un onglet ou une section par média (TV/Films/Séries), liste des catégories avec toggle masquer + réordonnancement (drag & drop, ou boutons haut/bas si le drag & drop est trop complexe à intégrer proprement en Compose — à évaluer).
4. Les requêtes qui alimentent les écrans de grille (LiveTvScreen, VodScreen, SeriesScreen) et la Home doivent filtrer les catégories masquées et respecter l'ordre personnalisé pour le profil actif, en retombant sur `orderIndex` si pas de personnalisation.
5. Retirer l'option de tri alphabétique des catégories dans les Paramètres : supprime `CategorySorting.ALPHABETICAL` et tout ce qui s'y rattache — `SettingsManager.kt:12-15` (enum), `KEY_TV_SORTING`/`KEY_VOD_SORTING`/`KEY_SERIES_SORTING` (`SettingsManager.kt:32-34`), getters/setters lignes 83-120, `SettingsState.tvSorting/vodSorting/seriesSorting`, logique dans `SettingsViewModel.kt:47-49,74-86`, UI dans `SettingsScreen.kt` (ex. lignes 228-238, 442-452). Vérifie qu'aucun autre code ne dépend de ce sorting avant de supprimer.

Tests : migration Room, DAO (masquage + tri par profil), ViewModel de gestion des catégories, régression sur les écrans de grille existants.

---

## 2. Home : sections par derniers ajouts + renommage

✅ **TERMINÉE** — Sonnet 5, effort moyen. Logique de chargement par "derniers ajouts" (tri par `added` décroissant, limite à 20 items) implémentée pour les sections Films et Séries. Renommage des sections en "TV en direct", "Films", et "Séries" dans `strings.xml`.

Sur la Home, remplace la logique "première catégorie de chaque média" par "derniers ajouts", et renomme les sections.

Contexte existant :
- `HomeViewModel.kt:159` (`loadHomeData()`) : pattern répété 3 fois, un seul `firstOrNull()` par média puis fetch des streams de cette unique catégorie — Live TV (l.174-183), VOD (l.191-200), Séries (l.208-217).
- Le champ `added` (timestamp d'ajout, format string à vérifier — probablement epoch en secondes façon Xtream) existe déjà au niveau liste : `VodStream.added` (`domain/model/VodStream.kt:8`), `SeriesStream.added` (`domain/model/SeriesStream.kt:8`). Pas présent sur Live TV (les chaînes n'ont pas de notion d'ajout pertinente côté Xtream — pour "TV en direct" il garder la logique de prendre ceux de la premiere categories des live tv.

À faire :
1. Remplace le fetch VOD/Séries de `loadHomeData()` par : parcourir les streams (toutes catégories confondues, ou catégories visibles pour le profil si la feature #1 est faite avant) triés par `added` décroissant, prendre les N premiers (garder le nombre actuel affiché en row).
2. Pour "TV en direct" : comme il n'y a pas de date d'ajout exploitable sur les chaînes live, garder la logique actuel.
3. Renomme les titres de section : "TV en direct", "Films", "Séries" (chaînes de `strings.xml`, cherche les clés actuelles utilisées dans `HomeScreen.kt` pour les titres de section et remplace leurs valeurs FR — vérifier aussi la version anglaise si elle existe).

Tests : `HomeViewModelTest` (tri par added, fallback live TV), non-régression sur le reste de la Home (resume watching, favoris).

---

## 3. Lecture auto de l'épisode suivant + bouton "épisode suivant"

✅ **TERMINÉE** — Opus 4.8, corrections Sonnet 5. `SeriesDetails.episodes` propagée jusqu'à `SeriesPlayerScreen` (nouveau param `seriesEpisodes`, câblé dans NavGraph + MainActivity). Épisode courant géré en state interne (`currentEpisode`) : enchaînement sans repasser par la navigation. Fonctions pures testables `computeNextEpisode`/`computePreviousEpisode` (`domain/model/SeriesEpisodeNavigation.kt`) : même saison ±1, sinon saison suivante/précédente adjacente présente, sinon null. Autoplay sur `STATE_ENDED` (efface la position de reprise). Contrôles du player : bouton « épisode précédent » (gauche), play/pause (centre, toujours aligné), « épisode suivant » (droite), chacun visible seulement si applicable. Boutons avance/recul rapide texte retirés (ils débordaient de l'écran sur mobile, cassés visuellement) — remplacés par un double-tap sur la moitié gauche/droite de la vidéo (recul/avance 10s), avec feedback visuel transitoire. 14 tests unitaires sur la logique de sélection prev/next (même saison / changement de saison / fin ou début de série / map vide / trous de numérotation / ordre non trié).

Sur `SeriesPlayerScreen`, enchaîne automatiquement sur l'épisode suivant à la fin de la lecture, et ajoute un bouton explicite pour y aller manuellement.

Contexte existant :
- `SeriesPlayerScreen(episode: SeriesEpisode, ...)` (`presentation/series/SeriesPlayerScreen.kt:75-76`) ne reçoit qu'**un seul** épisode — pas de liste, pas d'index, pas de callback next/previous. Seuls contrôles existants : `skipForward()`/`skipBackward()` (l.330-337, seek intra-vidéo) et sauvegarde de position (l.258/306).
- La liste triée des épisodes existe en amont : `SeriesDetails.episodes: Map<Int, List<SeriesEpisode>>` (`domain/model/SeriesDetails.kt:9`, clé = numéro de saison) — mais n'est jamais propagée jusqu'au player.

Décision : à la fin de la dernière saison, s'il existe une saison suivante dans `SeriesDetails.episodes`, enchaîne sur son épisode 1. S'il n'y a plus de saison suivante, ne rien faire (retour normal aux détails / pas d'autoplay).

À faire :
1. Fais remonter `SeriesDetails` (ou au minimum la liste ordonnée saison/épisode + le numéro de saison courant) jusqu'à `SeriesPlayerScreen`, via la navigation existante (`NavGraph.kt` pour la route series player).
2. Calcule l'épisode suivant : même saison, `episodeNum + 1` si présent dans la map ; sinon saison suivante (numéro de saison + 1 dans la map), épisode 1 ; sinon rien.
3. Détecte la fin de lecture (écoute déjà probablement présente sur l'état du player ExoPlayer/media3, ex. `Player.STATE_ENDED` — vérifie comment la position est trackée actuellement dans ce fichier) et déclenche automatiquement la lecture de l'épisode suivant s'il existe (recharge le player avec la nouvelle URL/stream, réinitialise position).
4. Ajoute un bouton "épisode suivant" dans les contrôles du player (visible seulement si un épisode suivant existe), qui déclenche la même transition manuellement.
5. Gère le cas où il n'y a pas d'épisode suivant : ni autoplay ni bouton visible.

Tests : logique de calcul du prochain épisode (même saison / changement de saison / fin de série), comportement du bouton, non-régression sur la sauvegarde de position de lecture.

---

## 4. Live TV : accès rapide aux autres chaînes pendant la lecture

✅ **TERMINÉE** — Sonnet 5, effort moyen. Ajout d'un panneau latéral (drawer) semi-transparent affichant la liste des chaînes (`streamsList`) avec logos et numéros. Intégration d'un bouton menu à côté du bouton de fermeture. Auto-défilement vers la chaîne active, gestion intelligente du focus et navigation clavier/D-pad sur TV, ainsi que fermeture par clic extérieur ou touche retour/back.

Sur `PlayerScreen` (Live TV), ajoute un accès rapide à la liste des chaînes pour zapper sans quitter le lecteur.

Contexte existant — **la base est déjà là** :
- `PlayerScreen` reçoit déjà `streamsList: List<LiveStream>` (`presentation/player/PlayerScreen.kt:62`) et gère déjà le zapping via `currentStreamIndex` avec next/previous circulaires (l.106-108, 171-172, 179-180).
- `MainActivity.kt` maintient déjà `activeStreamsList` en state (l.169), rempli à la sélection d'une catégorie ou d'un favori/résultat de recherche, transmis via `NavGraph.kt:68` (paramètre) → `NavGraph.kt:456` (`streamsList = activeStreamsList`).

Donc la demande "changer de chaîne sans quitter le player" est probablement déjà en partie possible via next/previous. Ce qui manque : un **accès visuel rapide à la liste complète** (pas juste chaîne suivante/précédente une par une) pendant que la vidéo continue.

À faire :
1. Ajoute un overlay/panneau (ex. tiroir latéral ou bottom sheet, cohérent avec le style TV/mobile de l'app) affichant `streamsList` avec nom + logo de chaîne, accessible par un bouton dédié dans les contrôles du player existant.
2. Sélectionner une chaîne dans ce panneau change immédiatement le flux en cours (réutilise la logique de changement de `currentStreamIndex` déjà présente) sans fermer le player.
3. Le panneau doit surligner la chaîne en cours de lecture et permettre la navigation clavier/télécommande (contexte Android TV — vérifie comment les autres écrans de grille gèrent le focus D-pad dans ce repo et réutilise le même pattern).
4. Se ferme automatiquement après sélection, ou via bouton retour/back.

Tests : sélection dans le panneau change bien le flux, focus D-pad fonctionnel sur TV, pas de régression sur next/previous existants.

---

## 5. Players : plusieurs modes de redimensionnement d'image

✅ **TERMINÉE** — Sonnet 5, effort moyen. Ajout de la gestion du mode de redimensionnement de l'image (Ajuster / Étirer / Zoom) persistée globalement via `SettingsManager` et `ResizeMode` enum. Intégration d'un bouton d'aspect ratio (`Icons.Default.AspectRatio`) dans le panneau de contrôle supérieur de chaque lecteur (`PlayerScreen`, `VodPlayerScreen`, `SeriesPlayerScreen`), qui cycle dynamiquement entre les modes standard Media3 (`RESIZE_MODE_FIT`, `RESIZE_MODE_FILL`, `RESIZE_MODE_ZOOM`) et affiche un overlay animé au centre de l'écran avec le format choisi. Tests unitaires ajoutés dans `SettingsManagerResizeModeTest`.

---

## 6. Description film/série : troncature + "voir plus"

✅ **TERMINÉE** — Haiku 4.5, effort faible. Composant `ExpandableText` créé + appliqué à 5 emplacements (VOD TV/Mobile + Séries TV/Mobile + episode plot).

Limite la taille affichée du synopsis (plot) et ajoute un "voir plus" / "voir moins".

Contexte existant :
- VOD : `VodDetailsScreen.kt:235` et `:360` affichent `details.plot` en entier, sans troncature.
- Séries : `SeriesDetailsScreen.kt:299` et `:476` affichent `details.plot ?: "Aucun résumé disponible."`, en entier également. Le plot d'**épisode** (`SeriesDetailsScreen.kt:717-723`) est déjà tronqué à `maxLines = 2` mais sans bouton "voir plus" (juste coupé).
- Aucun composant "expand/collapse" réutilisable n'existe dans le repo — à créer.

À faire :
1. Crée un composable réutilisable (ex. `ExpandableText` dans `presentation/components/` ou équivalent existant) : affiche le texte avec `maxLines` limité (ex. 3-4 lignes, à caler visuellement avec le design existant), détecte via `onTextLayout` si le texte est tronqué (`TextLayoutResult.hasVisualOverflow`), et si oui affiche un bouton/lien "voir plus" qui bascule vers l'affichage complet (avec "voir moins" pour revenir en arrière).
2. Utilise ce composable aux 4 emplacements identifiés : `VodDetailsScreen.kt:235,360`, `SeriesDetailsScreen.kt:299,476`.
3. Applique aussi au plot d'épisode (`SeriesDetailsScreen.kt:717-723`) qui n'a actuellement qu'une troncature sans "voir plus" — remplace par le même composable pour cohérence.
4. Vérifie le rendu sur les deux variantes desktop/mobile déjà présentes dans ces écrans (les paires de lignes suggèrent une duplication desktop/mobile — applique aux deux).

Tests : troncature correcte, bouton apparaît seulement si overflow réel, bascule expand/collapse fonctionnelle.

---

## 7. Icône "relire depuis le début"

✅ **TERMINÉE** — Haiku 4.5, effort faible. Icons.Default.Replay appliqué en remplacement de PlayArrow sur VodDetailsScreen (TV et mobile).

Remplace l'icône actuelle (triangle play) par une flèche en boucle pour l'action "relire depuis le début".

Contexte : aucune icône `Icons.Default.Replay`/`RestartAlt` trouvée dans le repo — cette fonctionnalité utilise actuellement une icône play générique (triangle), à localiser précisément.

À faire :
1. Localise l'endroit exact où l'action "relire depuis le début" est déclenchée (probablement dans le composant "Continuer à regarder" de la Home, `HomeScreen.kt`, ou dans les détails VOD/Séries — grep sur le texte du bouton/contentDescription associé, ou sur la logique qui reset `resumePositionMs` à 0).
2. Remplace l'icône utilisée par `Icons.Default.Replay` (androidx material icons — boucle antihoraire standard, déjà utilisée dans l'écosystème Android pour "recommencer"). Si `Icons.Default.Replay` ne rend pas bien visuellement, `Icons.Default.RestartAlt` est l'alternative material.
3. Vérifie qu'il n'y a pas d'autre occurrence du même triangle play réutilisée à tort pour cette action ailleurs dans l'app (Home + éventuellement détails VOD/Séries).

Tests : visuel seulement, pas de logique métier à tester au-delà de la non-régression du clic.

---

## 8. Recherche par genre (films/séries)

✅ **TERMINÉE** — Opus 4.8, effort élevé. **Révision** (après retour utilisateur) : le filtre par genre dans la recherche (chips) a été **abandonné** au profit d'une section « Titres associés » en bas des détails VOD/Séries. Le genre était déjà enrichi en arrière-plan et stocké (colonnes présentes + FTS4 depuis la Phase 40) — aucune migration Room (DB reste v13). Livré :
- `GenreParser` (`domain/model/`, objet pur) : split **virgule ET slash** (`Action/Aventure`), trim, exclusion placeholders (« Inconnu »/« N/A » reconnu en entier avant split), `matches` token-à-token insensible à la casse, `sharedGenreCount`.
- `RelatedTitlesSelector` (`domain/model/`, objet pur générique) : tri par **rang décroissant** où la catégorie commune vaut un genre commun de plus — `rang = genres communs + (même categoryId que le média courant ? 1 : 0)` (categoryId courant récupéré via `getStreamById`) ; à rang égal, départage par score `0.7*note + 0.3*fraîcheur d'ajout` (année de sortie non stockée en base → écartée) ; exclut les candidats sans genre commun (section ancrée sur les genres) ; limité à 10.
- DAO `VodDao`/`SeriesDao`.`getStreamsByGenre(pattern)` (préfiltre SQL `LIKE`, le match exact évite le sur-match « War »⊂« Warrior »).
- `VodRepository.getRelatedMovies` / `SeriesRepository.getRelatedSeries` (union des candidats par genre, dédup, exclusion de l'item courant) + `GetRelatedMoviesUseCase`/`GetRelatedSeriesUseCase`.
- État `relatedStreams`/`relatedSeries` chargé après les détails (échec silencieux) dans `VodViewModel`/`SeriesViewModel`.
- UI : composant partagé `RelatedTitlesRow` (`presentation/components/`) branché en bas des détails VOD et Séries (TV + mobile), clic → détails du titre associé (via `activeVodMovie`/`activeSeriesShow` dans `NavGraph` mobile et `MainActivity` TV).
- Tests : `GenreParserTest` (split slash, placeholders, `sharedGenreCount`) + `RelatedTitlesSelectorTest` (ordre par genres communs, départage note/ajout, limite, cas vides).

Ajoute une recherche/filtre par genre pour VOD et Séries.

Contexte existant :
- Pas de champ genre normalisé : le genre existe uniquement comme **string libre** dans `VodDetails.genre` (`domain/model/VodDetails.kt:9`) et `SeriesDetails.genre` (`domain/model/SeriesDetails.kt:12`), remplie depuis l'API Xtream (`VodInfoDto.kt:17`, `SeriesInfoMetadataDto.kt:21`) — typiquement une chaîne du type `"Action, Thriller"`.
- Ce champ n'existe que dans les **détails** (fetch à la demande par film/série), pas dans la liste de streams (`VodStream`/`SeriesStream` n'ont pas de genre) — donc pas directement disponible en masse sans un fetch détail par item.
- Pas de champ genre au niveau catégorie non plus (`VodCategory`/`SeriesCategory` n'ont que `categoryId`, `categoryName`, `parentId`).

Décision : parser la string `genre` existante (split par virgule + trim) plutôt que de s'appuyer sur les catégories.

À faire :
1. Comme le genre n'est connu qu'après fetch des détails d'un item, et que ceux-ci sont mis en cache (vérifie la stratégie de cache existante dans `VodRepositoryImpl`/`SeriesRepositoryImpl`, `forceRefresh` etc.), la recherche par genre nécessite soit :
   - (a) un enrichissement progressif en arrière-plan qui fetch les détails de tous les items et stocke le genre parsé en DB (voir le pattern d'enrichissement déjà existant dans `VodRepositoryImpl` — `enrichmentDispatcher`, `startBackgroundEnrichment()`, mentionné dans le repo — à réutiliser/étendre plutôt que réinventer), soit
   - (b) limiter la recherche par genre aux items déjà présents en cache local (dont les détails ont déjà été consultés au moins une fois) — solution plus simple mais résultats partiels.
   Recommandation : partir sur (a) en étendant le mécanisme d'enrichissement déjà en place, pour une couverture complète à terme.
2. Ajoute une colonne `genre: String?` (texte brut ou déjà splitté selon ce qui est le plus simple à requêter en SQL Room — un champ texte avec `LIKE '%genre%'` suffit probablement) aux entités VOD/Séries concernées (celles qui stockent déjà les détails enrichis), avec migration Room associée.
3. UI : dans l'écran de recherche existant (`presentation/search/SearchScreen.kt` si c'est le nom — vérifie), ajoute un filtre par genre (liste des genres distincts extraits des données en cache, sélection simple ou multiple).
4. La recherche par genre filtre sur les items dont le genre stocké contient le genre sélectionné (après split par virgule + trim, comparaison insensible à la casse).

Tests : parsing de la string genre (cas avec espaces, casse variable, un seul genre, plusieurs), filtrage correct, non-régression sur la recherche texte existante.

---

## 9. Top 10 Films/Séries sur la Home (derniers ajouts, note décroissante avec palier)

✅ **TERMINÉE** — Sonnet 5, effort moyen. Implémentation du sélecteur algorithmique multi-palier générique et purement testé `TopRatedSelector`. Ce sélecteur extrait, filtre et trie défensivement les flux par note (rating > 8.0, puis > 7.0, > 6.0, > 5.0, > 0.0, puis sans filtre) tout en respectant l'ordre décroissant d'ajout (`added`). Ajout des propriétés `topVodStreams` et `topSeriesStreams` dans `HomeState` et `HomeViewModel` (alimentées en filtrant les catégories masquées du profil actif). Ajout des sections horizontales correspondantes "Top 10 Films" et "Top 10 Séries" sur la page d'accueil avec des clés d'état de défilement indépendantes (`"home_top_vod"`, `"home_top_series"`). Ajout de tests unitaires exhaustifs dans `TopRatedSelectorTest`.

---

## 10. Live TV player : dropdown pour changer de catégorie dans le panneau de zapping

✅ **TERMINÉE** — Sonnet 5, effort moyen. Ajout d'un sélecteur de catégorie sous forme de menu déroulant (`DropdownMenu`) au-dessus de la liste du panneau de zapping latéral. Le sélecteur permet de choisir la liste de zapping initiale ou n'importe quelle catégorie chargée réactivement depuis le `LiveTvViewModel`. La sélection d'une nouvelle catégorie charge immédiatement ses chaînes associées en arrière-plan sans perturber la lecture courante. Cliquer sur une chaîne de la nouvelle liste bascule immédiatement le flux principal, met à jour l'index courant et synchronise la liste de zapping principale (`activeStreamsList`) afin que le zapping standard (haut/bas) s'effectue au sein de cette nouvelle catégorie. Le comportement est entièrement synchronisé et répercuté sur la grille Live TV principale au retour du lecteur.

---

## 11. Bug : certaines pistes audio/sous-titres non sélectionnables dans le player

✅ **TERMINÉE** — Sonnet 5, effort moyen. Résolution du bug de sélection des pistes audio et sous-titres non supportées par l'appareil. Ajout d'un attribut `isSupported: Boolean` dans `TrackInfo`. Utilisation de `group.isTrackSupported(tIndex)` dans `updateTracksState` afin de détecter le support réel de chaque piste. Dans `TrackSelectionDialog`, désactivation du clic (`clickable`) et du bouton d'option (`RadioButton.enabled = false`) pour les pistes non supportées, application d'un style grisé (`Color.Gray` et alpha réduit à `0.5f`), et ajout de la mention `(non supporté)` sur l'interface. Empêchement de l'auto-sélection de pistes non supportées par `applyPreferredLanguages`. Ajout de tests unitaires complets dans `TrackSelectionTest.kt`.

---

## 12. Mode Picture-in-Picture (PIP) à la demande dans le player

✅ **TERMINÉE** — Sonnet 5, effort moyen. Support Picture-in-Picture déclaré dans le manifest (`supportsPictureInPicture="true"`, `resizeableActivity="true"`). Ajout d'un bouton PIP (`Icons.Default.PictureInPictureAlt`) sur les 3 lecteurs mobiles (`isTv == false`). Le bouton bascule l'application en mode PIP à la demande avec un ratio dynamique extrait de la taille vidéo active d'ExoPlayer. Les contrôles custom (overlays, tiroir, etc.) sont automatiquement masqués en mode PIP grâce à l'observation réactive du changement de mode (`OnPictureInPictureModeChangedListener`) via un `DisposableEffect` sécurisé qui évite le name shadowing.

---

## 13. Home : "voir tout" Films/Séries → 100 derniers médias ajoutés

Sonnet 5, effort faible-moyen.

Sur la Home, les sections "Films" et "Séries" (derniers ajouts, [feature #2](#2-home--sections-par-derniers-ajouts--renommage)) ont un bouton "voir tout" qui navigue actuellement vers `onNavigateToVod`/`onNavigateToSeries` (l'onglet complet, `HomeScreen.kt:403,427,451,475`). Remplace cette navigation par un écran listant les **100 derniers médias ajoutés** (tri par `added` décroissant), et non l'onglet catégories habituel.

Contexte existant :
- `HomeScreen.kt` : 4 `onSeeAll` pointent vers `onNavigateToVod`/`onNavigateToSeries` — 2 pour la section "Films"/"Séries" (derniers ajouts), 2 pour "Top 10 Films"/"Top 10 Séries" (feature #9, à laisser inchangés — ces "voir tout" sont retirés par la feature #14 ci-dessous).
- Pas d'écran existant listant "tous les derniers ajouts" au-delà de la limite de 20 affichée en row (`HomeViewModel.kt:159` et suivants).
- `VodStream`/`SeriesStream` ont déjà `added` (voir feature #2).

À faire :
1. Crée un nouvel écran (ex. `presentation/home/RecentlyAddedScreen.kt` ou équivalent, réutilise le style grille des écrans VOD/Séries existants) affichant les 100 items les plus récents par `added` décroissant, pour le type concerné (VOD ou Séries), en respectant les catégories masquées du profil actif si [feature #1](#1-gestion-des-catégories-masquer--réordonner-par-profil) est active côté requête.
2. Ajoute la route de navigation correspondante (`NavGraph.kt` pour mobile, `MainActivity.kt`/`NavGraph.kt` pour TV selon le pattern existant).
3. Requête : soit un nouveau DAO query `getRecentlyAdded(limit: Int)` (VOD/Séries), soit réutilisation de la logique déjà en place dans `HomeViewModel` en changeant juste la limite (20 → 100) et le nombre de colonnes/mode grille pour l'écran dédié.
4. Change `onSeeAll` des sections "Films" et "Séries" (derniers ajouts) sur `HomeScreen.kt` pour naviguer vers ce nouvel écran au lieu de `onNavigateToVod`/`onNavigateToSeries`.
5. Clic sur un item → détails du média (comportement standard, réutilise le pattern déjà en place ailleurs).

Tests : requête "100 derniers" (tri, limite, respect des catégories masquées), navigation, non-régression sur les autres `onSeeAll` de la Home.

---

## 14. Home : Top 10 sans "voir tout", note ≥ 8 uniquement (sans palier de repli)

Sonnet 5, effort faible.

Sur la Home, les sections "Top 10 Films"/"Top 10 Séries" ([feature #9](#9-top-10-filmsséries-sur-la-home-derniers-ajouts-note-décroissante-avec-palier)) doivent : (a) ne plus avoir de bouton "voir tout", (b) n'afficher que des médias avec note **≥ 8**, sans le système de paliers de repli actuel.

Contexte existant :
- `TopRatedSelector.kt:21-45` (`selectTop10`) : système à 5 paliers (`rating > 8.0`, sinon `> 7.0`, `> 6.0`, `> 5.0`, `> 0.0`, sinon tout) qui complète jusqu'à 10 items en descendant les seuils si le palier supérieur n'en fournit pas assez.
- `HomeScreen.kt:421-489` : sections "Top 10 Films"/"Top 10 Séries" avec `onSeeAll = onNavigateToVod`/`onNavigateToSeries` (via `SectionHeader`, `HomeScreen.kt:427,475`).

Décision : note **≥ 8** strictement (pas de repli sur les paliers inférieurs) — si moins de 10 items qualifient, afficher moins de 10 (voire masquer la section si vide, cohérent avec `state.topVodStreams.isNotEmpty()` déjà en place `HomeScreen.kt:422,470`).

À faire :
1. Simplifie `TopRatedSelector.selectTop10` (ou crée une nouvelle fonction dédiée si `selectTop10` est utilisé ailleurs avec l'ancien comportement — vérifie les appels) : filtre unique `rating >= 8.0`, tri par `added` décroissant, `take(10)`. Supprime la logique de paliers (tier1-tier5) devenue inutile.
2. Sur `HomeScreen.kt`, retire le `onSeeAll` des `SectionHeader` des sections "Top 10 Films" (l.427) et "Top 10 Séries" (l.475) — passe `onSeeAll = null` (le composant `SectionHeader` gère déjà ce cas, voir `HomeScreen.kt:590` `if (onSeeAll != null)`).
3. Adapte les tests existants `TopRatedSelectorTest` (retire les cas de paliers, ajoute le cas seuil ≥ 8 strict).

Tests : filtrage note ≥ 8 (limite exacte à 8.0 incluse), tri par added, absence de bouton "voir tout" dans la section, non-régression sur les autres sections Home.

---

## 15. Téléchargement hors-ligne des films et épisodes de séries

Opus 4.8, effort élevé.

Ajoute la possibilité de télécharger un film ou un épisode de série pour le regarder hors-ligne.

Contexte existant :
- **Aucune infrastructure de téléchargement dans le repo** — pas de `DownloadManager` Media3, pas d'entité Room liée, pas de worker/service de téléchargement. Feature entièrement à construire.
- Lecture actuelle exclusivement en streaming via ExoPlayer/media3 (`presentation/vod/VodPlayerScreen.kt`, `presentation/series/SeriesPlayerScreen.kt`) à partir des URLs Xtream (construction d'URL à localiser dans les repositories VOD/Séries).
- Room DB déjà en place avec pattern de migrations explicites (`di/AppModule.kt`, `ALL_MIGRATIONS`), voir `AGENTS.md`.
- Le player gère déjà des cas où le flux vient d'une URL réseau — pour la lecture hors-ligne il faudra pouvoir pointer ExoPlayer vers un fichier local à la place.

Décisions à valider avec l'utilisateur avant de coder (poser la question en session dédiée si ambiguïté) :
- Bibliothèque : `androidx.media3.exoplayer.offline` (`DownloadManager`, `DownloadService`) est le choix standard media3, cohérent avec le player déjà utilisé — recommandé plutôt qu'une solution maison.
- Stockage : dossier app-privé (`context.getExternalFilesDir` ou interne) pour respecter le scoped storage Android, pas de permission stockage externe supplémentaire à demander si évitable.

À faire :
1. Intègre `androidx.media3.exoplayer.offline` : dépendance Gradle (`media3-exoplayer-dash`/`hls` selon le format de flux Xtream déjà utilisé — vérifie le format actuel avant de choisir), `DownloadService` (foreground service Android, notification de progression obligatoire côté OS), `DownloadManager` avec un `Cache` dédié (ex. `SimpleCache` sur `context.getExternalFilesDir`).
2. Nouvelle table Room (ex. `downloaded_media`) : `streamId`, `type` (VOD/episode), `profileId` (si le téléchargement doit être par profil — à trancher, cohérent avec le pattern favoris/reprise de lecture existant), chemin fichier local, statut (en cours/terminé/échoué), taille, date. Migration Room explicite.
3. UI : bouton téléchargement sur les écrans détails VOD (`VodDetailsScreen.kt`) et détails/épisode Séries (`SeriesDetailsScreen.kt`), avec indicateur de progression et état (télécharger/en cours/téléchargé/supprimer).
4. Nouvel écran "Téléchargements" (liste des médias téléchargés, accès à la lecture hors-ligne, suppression) — accessible depuis la Home ou les Paramètres (à définir).
5. Adapte `VodPlayerScreen`/`SeriesPlayerScreen` pour détecter si le média demandé est téléchargé localement et lire depuis le fichier local plutôt que streamer (media3 gère ça nativement via son `Cache`/`DownloadManager` si bien branché — la lecture "hors-ligne" et "en ligne" partagent le même `MediaItem` avec media3 si le cache est correctement configuré).
6. Gestion de l'espace disque : affichage de l'espace utilisé, suppression manuelle, pas de purge automatique sauf demande explicite.
7. Gestion des erreurs réseau pendant le téléchargement (pause/reprise), respect de `AGENTS.md` sur la gestion d'erreurs.

Tests : DAO téléchargements, ViewModel écran téléchargements (statuts, suppression), non testable unitairement pour la partie ExoPlayer/media3 `DownloadManager` — recette manuelle obligatoire (démarrage, pause réseau coupé, reprise, lecture hors-ligne réelle avec Wi-Fi désactivé).

---

## 16. Catégorie masquée = médias invisibles partout (Home, titres associés, recherche)

Sonnet 5, effort moyen.

Le masquage de catégorie ([feature #1](#1-gestion-des-catégories-masquer--réordonner-par-profil)) filtre aujourd'hui les écrans de grille (Live TV/VOD/Séries) et probablement une partie de la Home, mais **pas** la section "Titres associés" ([feature #8](#8-recherche-par-genre-filmsséries)) ni la recherche. Étend le filtrage des catégories masquées à ces deux endroits, et vérifie la couverture complète sur la Home.

Contexte existant :
- `CategoryPreferenceRepository`/`CategoryPreferenceDao` (voir feature #1) exposent déjà le masquage par profil, déjà consommé par `GetLiveCategoriesUseCase`, `GetVodCategoriesUseCase`, `GetSeriesCategoriesUseCase`, et `HomeViewModel.kt` (à vérifier précisément quelles sections Home l'appliquent déjà — les sections "derniers ajouts" (feature #2) et "Top 10" (feature #9) ont été ajoutées après/en parallèle de la feature #1, vérifier qu'elles filtrent bien par catégories masquées).
- `RelatedTitlesSelector.kt` (feature #8) est un objet **pur** sans accès DB : il reçoit une liste de `Candidate` déjà construite en amont. Le filtrage des catégories masquées doit donc se faire **avant** l'appel à `RelatedTitlesSelector.select` — au niveau de `GetRelatedMoviesUseCase`/`GetRelatedSeriesUseCase` ou du repository (`VodRepository.getRelatedMovies`/`SeriesRepository.getRelatedSeries`), pas dans le sélecteur lui-même.
- Recherche : `SearchScreen.kt`/le ViewModel associé (localiser le fichier, probablement `SearchViewModel.kt`) — vérifie si la requête de recherche actuelle (FTS4, mentionnée en feature #8) filtre déjà par catégorie masquée ou non.

À faire :
1. Vérifie précisément (grep/lecture) quelles requêtes Home actuelles (sections derniers ajouts, Top 10, TV en direct) appliquent déjà le filtrage catégories masquées du profil actif, et complète celles qui ne le font pas.
2. `GetRelatedMoviesUseCase`/`GetRelatedSeriesUseCase` (ou repository sous-jacent) : récupère les catégories masquées du profil actif (via `CategoryPreferenceRepository`), exclut les candidats appartenant à une catégorie masquée **avant** de les passer à `RelatedTitlesSelector.select`.
3. ViewModel de recherche : applique le même filtrage (catégories masquées du profil actif) sur les résultats VOD/Séries (et Live TV si categorisé) avant affichage — que ce soit en filtrant la requête SQL ou en filtrant la liste de résultats côté Kotlin.
4. Attention au profil actif : récupère-le de la même façon que le reste du code (voir comment `HomeViewModel`/`VodViewModel` accèdent au profil courant) pour rester cohérent.

Tests : titres associés n'incluent pas de candidat d'une catégorie masquée, recherche n'affiche pas de résultat d'une catégorie masquée, non-régression Home sur les sections déjà couvertes par la feature #1.

---

## 17. Recherche : bouton "voir tout" violet et aligné à droite

Haiku 4.5, effort faible.

Dans l'écran de recherche, le bouton "voir tout" de chaque section de résultats doit être stylé différemment du reste de l'app : **violet**, et aligné **à droite** (au lieu d'à gauche, collé au titre de section).

Contexte existant :
- `SearchSectionHeader` (`SearchScreen.kt:214-252`) : `Row` avec titre puis `Spacer(16.dp)` puis le bouton "voir tout" juste à sa droite (donc actuellement aligné à gauche avec le titre, pas à droite de la Row) — couleurs actuelles : repos = blanc sur `Surface3`, focus = noir sur `AccentLavande` (commentaire l.234-235 : "Même contraste que la Home").
- `AccentLavande` est déjà défini quelque part dans le thème (grep pour sa définition, probablement `presentation/theme/Color.kt` ou équivalent) — c'est déjà une teinte violette/lavande, à réutiliser comme couleur de fond du bouton **au repos** (pas seulement au focus comme actuellement), pour le distinguer visuellement du "voir tout" utilisé ailleurs (Home, VOD, Séries, Live TV — `HomeScreen.kt`, `VodScreen.kt`, `SeriesScreen.kt`, `LiveTvComponents.kt` qui ont leur propre style à ne PAS modifier, seule la recherche change).

À faire :
1. Dans `SearchSectionHeader` (`SearchScreen.kt:220-251`), change le `Row` en `Modifier.fillMaxWidth()` avec `horizontalArrangement = Arrangement.SpaceBetween` (titre à gauche, bouton à droite) au lieu du `Spacer(16.dp)` actuel qui colle le bouton au titre.
2. Change les couleurs du `Button` (l.236-239) : fond violet (`AccentLavande` ou variante) au repos, garder une distinction visuelle claire au focus (ex. inverser en noir sur lavande plus clair, ou lavande plus saturé — à ajuster visuellement).
3. Vérifie que ce changement ne touche QUE `SearchSectionHeader` dans `SearchScreen.kt` — ne pas propager aux autres écrans qui ont leur propre composant "voir tout" avec un style différent.
4. Recette visuelle manuelle (TV + mobile) pour confirmer le contraste et la lisibilité en focus D-pad.

Tests : visuel uniquement, pas de logique métier — vérifier la non-régression du clic/navigation existante.

---

## Notes transverses

- Toutes ces features touchent potentiellement Room (migrations) : respecter la convention du projet — pas de `fallbackToDestructiveMigration()`, migration explicite ajoutée à `ALL_MIGRATIONS` (`di/AppModule.kt`), voir `AGENTS.md`.
- Recette manuelle sur device/émulateur recommandée pour tout ce qui touche à ExoPlayer/media3 (players) — ces flux ne sont pas facilement testables unitairement.
- Convention de fin de tâche du projet (voir `AGENTS.md`) : commit + tag SemVer (minor pour une nouvelle feature, pas patch) + push après chaque feature terminée.
