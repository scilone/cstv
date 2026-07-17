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

**Modèle recommandé : Opus 4.8, effort élevé** — extension du mécanisme d'enrichissement background existant + migration Room + UI filtre, ambiguïté architecturale (a) vs (b) à trancher intelligemment.

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

**Modèle recommandé : Sonnet 5, effort moyen** — algorithme de sélection multi-palier bien spécifié, logique pure testable, parsing défensif classique.

Ajoute une section "Top 10" Films et une "Top 10" Séries sur la Home.

Contexte existant :
- `VodStream.rating: String?` et `VodStream.added: String?` existent déjà au niveau liste (`domain/model/VodStream.kt:7-8`), idem `SeriesStream.rating`/`added` (`domain/model/SeriesStream.kt:7-8`) — donc calculable sans fetch détail supplémentaire, uniquement à partir des listes déjà chargées.

Décision (algorithme de sélection, validé avec l'utilisateur) :
1. Prendre les items avec note > 8, triés par date d'ajout décroissante.
2. Si moins de 10 items, compléter avec les items notés > 7 (mais ≤ 8), toujours triés par date d'ajout décroissante, sans doublons avec l'étape précédente.
3. Si toujours moins de 10, continuer en baissant le palier de note (> 6, > 5, etc.) jusqu'à atteindre 10 ou épuiser les paliers pertinents.
4. S'il reste moins de 10 après avoir descendu jusqu'à un palier minimal raisonnable (à définir, ex. note > 0 ou pas de filtre note du tout en dernier recours), compléter avec le reste des items disponibles triés par date d'ajout décroissante, sans filtre de note, jusqu'à 10 ou épuisement du catalogue.

À faire :
1. Implémente cette logique de sélection en fonction pure testable (ex. `TopRatedSelector` ou logique directement dans `HomeViewModel`), prenant en entrée la liste complète des streams (VOD ou Séries) et retournant les 10 sélectionnés selon l'algorithme ci-dessus.
2. Parse `rating` (`String?`) en `Double` de façon défensive (valeurs vides/invalides exclues du tri, pas de crash).
3. Parse `added` (`String?`, probablement un timestamp epoch en secondes façon Xtream) en date comparable, défensivement aussi.
4. Ajoute deux nouvelles sections sur la Home ("Top 10 Films", "Top 10 Séries"), alimentées par cette sélection sur l'ensemble du catalogue VOD/Séries en cache (toutes catégories confondues, ou catégories visibles pour le profil si la feature #1 est faite avant).
5. Décide si ces sections remplacent ou s'ajoutent aux sections "Films"/"Séries" du point #2 (recommandation : les deux sections coexistent — "Films" = derniers ajouts bruts, "Top 10 Films" = derniers ajouts filtrés qualité — à valider visuellement une fois en place).

Tests : `TopRatedSelector` avec jeux de données couvrant chaque palier de fallback (assez de notes >8, besoin de compléter à >7, jusqu'à épuisement total du catalogue), parsing défensif de `rating`/`added`.

---

## 10. Live TV player : dropdown pour changer de catégorie dans le panneau de zapping

**Modèle recommandé : Sonnet 5, effort moyen** — réutilise entièrement l'infra existante (ViewModel déjà injecté, use case déjà là), juste de la UI + du câblage d'état.

Dans le panneau latéral de zapping du `PlayerScreen` (feature #4), ajoute un dropdown pour changer de catégorie et ainsi zapper sur des chaînes hors de la catégorie/liste initiale.

Contexte existant :
- `PlayerScreen` reçoit `streamsList: List<LiveStream>` figée (`presentation/player/PlayerScreen.kt:77`), remplie une fois par `MainActivity`/`NavGraph` selon la catégorie ou le favori/résultat de recherche sélectionné avant d'entrer dans le player (`activeStreamsList` dans `MainActivity.kt:170`). Le panneau de zapping (feature #4) affiche uniquement cette liste (`PlayerScreen.kt:596`, `itemsIndexed(streamsList)`) — impossible d'en sortir sans fermer le player.
- `PlayerScreen` reçoit déjà `viewModel: LiveTvViewModel` (ajouté par la feature #5, resize mode) — ce ViewModel expose déjà tout ce qu'il faut : `state.categories: List<LiveCategory>` (avec l'entrée synthétique `"all"` = "Tout", voir `LiveTvViewModel.kt:121-137`), `loadStreams(categoryId: String, forceRefresh: Boolean = false)` qui peuple `state.streams`, et `selectCategory(category: LiveCategory)` (`LiveTvViewModel.kt:146-149`).
- Attention : `selectCategory`/`loadStreams` écrivent dans `LiveTvState` (l'état de l'écran grille Live TV en arrière-plan), pas dans un état local au player — vérifie si c'est acceptable de réutiliser tel quel (ça resynchroniserait la grille Live TV avec la nouvelle catégorie au retour du player, ce qui est probablement le comportement désiré) ou s'il faut une méthode dédiée qui ne pollue pas `LiveTvState` pour ne pas surprendre l'utilisateur en revenant à l'écran Live TV. À trancher selon ce qui semble le plus cohérent après avoir regardé `LiveTvScreen.kt`.

Décision : le dropdown remplace la liste de chaînes affichée dans le panneau par celles de la catégorie choisie (pas un ajout à la suite) — comportement identique à un changement de catégorie sur l'écran Live TV normal.

À faire :
1. Ajoute un dropdown (menu déroulant, ex. `ExposedDropdownMenuBox` ou équivalent cohérent avec le reste de l'app) en haut du panneau de zapping existant, listant `viewModel.state.value.categories` (ou en observant le `StateFlow` proprement en `Composable`).
2. À la sélection d'une catégorie, déclenche le chargement des chaînes de cette catégorie via le ViewModel (`loadStreams`/`selectCategory`, ou nouvelle méthode dédiée si tu juges que réutiliser l'état de la grille Live TV est trompeur — voir point d'attention ci-dessus) et remplace la liste affichée dans le panneau par le résultat.
3. La sélection d'une chaîne dans cette nouvelle liste doit fonctionner exactement comme aujourd'hui (changement immédiat du flux en cours, `currentStreamIndex`/`streamsList` mis à jour en conséquence — attention, `streamsList` est un paramètre reçu de l'extérieur, il faudra probablement le dupliquer en state interne modifiable une fois qu'on quitte la liste initiale).
4. Conserve le focus D-pad/clavier fonctionnel sur le dropdown et sur la nouvelle liste (même pattern que le reste du panneau, feature #4).
5. Vérifie le comportement au retour du player vers l'écran Live TV : la grille doit-elle refléter la nouvelle catégorie sélectionnée dans le player, ou rester sur la catégorie d'origine ? Documente le choix fait dans le commit.

Tests : sélection d'une catégorie dans le dropdown charge les bonnes chaînes, sélection d'une chaîne dans la nouvelle liste change bien le flux, non-régression sur le zapping/panneau existant (feature #4) quand on ne touche pas au dropdown.

---

## 11. Bug : certaines pistes audio/sous-titres non sélectionnables dans le player

**Modèle recommandé : Sonnet 5, effort moyen** — bug ExoPlayer/Media3 précis à diagnostiquer (probablement absence de vérification du support réel de la piste), fix ciblé mais dupliqué dans 2 fichiers quasi identiques.

Dans le dialogue de sélection audio/sous-titres du player (VOD et Séries), certains choix affichés dans la liste ne réagissent pas au clic : la sélection ne change pas.

Contexte existant :
- Le dialogue est dupliqué à l'identique dans `presentation/series/SeriesPlayerScreen.kt:869` (`TrackSelectionDialog`, private) et `presentation/vod/VodPlayerScreen.kt:737` (idem).
- Construction de la liste des pistes : `updateTracksState` (`SeriesPlayerScreen.kt:208-248`, `VodPlayerScreen.kt:175` et suivantes) parcourt **tous** les indices de chaque `Tracks.Group` (`C.TRACK_TYPE_AUDIO`/`C.TRACK_TYPE_TEXT`) et construit un `TrackInfo` pour chacun, sans jamais vérifier si Media3/ExoPlayer considère la piste comme réellement lisible sur l'appareil (`group.isTrackSupported(tIndex)`, ou `group.getTrackSupport(tIndex) == C.FORMAT_HANDLED`). Une piste listée par Media3 dans un groupe peut être présente mais non supportée par les renderers du device (codec/format non géré) — Media3 la remonte quand même dans `Tracks.Group` mais refuse silencieusement l'override de sélection dessus.
- Le clic déclenche bien `onAudioTrackSelected`/`onSubtitleTrackSelected` → `exoPlayer.trackSelectionParameters = ...setOverrideForType(TrackSelectionOverride(track.mediaTrackGroup, track.trackIndex))...` (ex. `SeriesPlayerScreen.kt:820-834`) : l'appel ne plante pas, mais si la piste n'est pas supportée, ExoPlayer ignore l'override et la sélection réelle (et donc `group.isTrackSelected(tIndex)` au prochain `onTracksChanged`) ne change jamais → symptôme exact décrit : clic sans effet visible.

Décision : les pistes non supportées doivent rester visibles dans la liste (pour que l'utilisateur comprenne qu'elles existent) mais visuellement désactivées (grisées) et non cliquables, plutôt que masquées silencieusement — évite la confusion "je ne trouve pas ma piste" tout en supprimant le clic mort.

À faire :
1. Dans `updateTracksState` (les deux fichiers), calcule un flag `isSupported` par piste via `group.isTrackSupported(tIndex)` (ou l'équivalent correct de l'API Media3 utilisée dans le repo — vérifie la version de `media3` dans `gradle/libs.versions.toml`/`build.gradle.kts` pour l'API exacte disponible) et ajoute ce champ à `TrackInfo` (`data class TrackInfo`, dupliquée dans les 2 fichiers).
2. Dans `TrackSelectionDialog` (les 2 copies), désactive le `clickable`/`onClick` du `RadioButton` pour les pistes `!isSupported`, applique un style grisé (ex. `Color.Gray`, alpha réduit) et ajoute un indice visuel discret (ex. petite icône ou texte "non supporté" à côté du label) plutôt que de juste les rendre muettes.
3. Vérifie qu'aucune piste non supportée n'est jamais sélectionnée automatiquement par `applyPreferredLanguages` (préférence audio/sous-titres mémorisée) — si la langue préférée correspond à une piste non supportée, ne l'applique pas silencieusement (fallback sur la piste par défaut d'ExoPlayer).
4. Envisage d'extraire `TrackInfo`, `updateTracksState` et `TrackSelectionDialog` dans un fichier commun partagé (`presentation/player/` par exemple) puisqu'ils sont dupliqués mot pour mot entre VOD et Séries — à faire seulement si le fix seul rend la duplication trop pénible à maintenir en synchro, pas une obligation de cette tâche.

Tests : construction de `TrackInfo.isSupported` à partir d'un `Tracks.Group` mocké avec pistes supportées/non supportées mélangées, non-sélection automatique d'une piste préférée non supportée, non-régression sur la sélection des pistes supportées existantes.

---

## 12. Mode Picture-in-Picture (PIP) à la demande dans le player

**Modèle recommandé : Sonnet 5, effort moyen** — API Android bien documentée (`PictureInPictureParams`), mais à câbler correctement sur 3 écrans player + cycle de vie de l'Activity, quelques pièges classiques (rotation, contrôles qui doivent disparaître en mode PIP).

Ajoute un bouton dans les contrôles de chaque player (Live TV, VOD, Séries) qui bascule l'app en mode PIP à la demande — pas de déclenchement automatique en quittant l'app (`onUserLeaveHint`).

Contexte existant :
- Aucun support PIP dans le repo actuellement : aucune trace de `PictureInPictureParams`, `enterPictureInPictureMode`, `onUserLeaveHint`, ni d'attribut `android:supportsPictureInPicture` dans `AndroidManifest.xml`.
- `MainActivity` (`AndroidManifest.xml:23-27`) déclare déjà `android:configChanges="orientation|screenSize|screenLayout|keyboardHidden|smallestScreenSize"` — il manque `screenLayout` n'est pas suffisant seul pour PIP sans re-création : il faudra ajouter `smallestScreenSize` (déjà présent) et vérifier que `screenSize` (déjà présent) suffit ; teste en conditions réelles plutôt que de supposer.
- Les 3 écrans player (`PlayerScreen.kt`, `VodPlayerScreen.kt`, `SeriesPlayerScreen.kt`) ont chacun leur propre `Context.findActivity()` (utilitaire dupliqué en tête de fichier) et leur propre rangée de contrôles (boutons `IconButton` en haut/bas de l'overlay) — le nouveau bouton PIP doit suivre le même pattern visuel que les boutons existants (ex. le bouton `AspectRatio` ajouté par la feature #5).
- Décision produit déjà actée par l'utilisateur : PIP **sur demande uniquement** via bouton dédié, jamais automatique au `onUserLeaveHint`/passage en arrière-plan.

À faire :
1. `AndroidManifest.xml` : ajoute `android:supportsPictureInPicture="true"` et `android:resizeableActivity="true"` sur la déclaration de `MainActivity` (ligne 23-27), vérifie/ajuste `android:configChanges` pour couvrir les changements déclenchés par le mode PIP sans recréer l'Activity (teste concrètement sur device/émulateur).
2. Ajoute un bouton (icône, ex. `Icons.Default.PictureInPictureAlt` si disponible dans le set d'icônes du projet, sinon vérifie l'alternative la plus proche déjà utilisée ailleurs) dans les contrôles de chacun des 3 players, qui appelle `activity.enterPictureInPictureMode(PictureInPictureParams.Builder()...build())` (via le `findActivity()` déjà présent dans chaque fichier) avec un ratio d'aspect cohérent avec la vidéo en cours si récupérable depuis ExoPlayer (`exoPlayer.videoSize`), sinon un ratio par défaut 16:9.
3. Masque les contrôles custom (overlay Compose : boutons, titre, slider, tiroir de chaînes, etc.) quand l'Activity est en mode PIP (`Activity.isInPictureInPictureMode`, à observer via un `Configuration`/callback approprié en Compose — vérifie le pattern recommandé pour Compose + PIP, probablement un `DisposableEffect` avec un listener sur l'Activity) : en PIP, seule la vidéo doit rester visible, ExoPlayer continue de jouer.
4. Vérifie qu'en sortant du mode PIP (retour à la taille normale), les contrôles custom réapparaissent normalement et que l'état de lecture (position, piste sélectionnée, etc.) n'est pas perturbé.
5. Sur Android TV (`isTv == true`), le mode PIP n'a généralement pas de sens (pas de multi-fenêtrage utilisateur standard sur la plupart des launchers TV) — masque le bouton PIP si `isTv`, à moins que tu constates que ça fonctionne correctement en test sur l'émulateur TV du projet.

Tests : essentiellement recette manuelle sur device/émulateur (voir notes transverses) — le mode PIP n'est pas testable unitairement. Vérifie au minimum : le bouton bascule bien en PIP sur les 3 players mobile, les contrôles disparaissent en PIP, la lecture continue sans coupure, le retour au mode normal restaure l'UI, le bouton est absent sur TV.

---

## Notes transverses

- Toutes ces features touchent potentiellement Room (migrations) : respecter la convention du projet — pas de `fallbackToDestructiveMigration()`, migration explicite ajoutée à `ALL_MIGRATIONS` (`di/AppModule.kt`), voir `AGENTS.md`.
- Recette manuelle sur device/émulateur recommandée pour tout ce qui touche à ExoPlayer/media3 (players) — ces flux ne sont pas facilement testables unitairement.
- Convention de fin de tâche du projet (voir `AGENTS.md`) : commit + tag SemVer (minor pour une nouvelle feature, pas patch) + push après chaque feature terminée.
