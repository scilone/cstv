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

✅ **TERMINÉE** — Opus 4.8. `SeriesDetails.episodes` propagée jusqu'à `SeriesPlayerScreen` (nouveau param `seriesEpisodes`, câblé dans NavGraph + MainActivity). Épisode courant géré en state interne (`currentEpisode`) : enchaînement sans repasser par la navigation. Fonction pure testable `computeNextEpisode` (`domain/model/SeriesEpisodeNavigation.kt`) : même saison `episodeNum+1`, sinon 1er épisode de la plus petite saison supérieure, sinon null. Autoplay sur `STATE_ENDED` (efface la position de reprise) + bouton `SkipNext` visible uniquement si un épisode suivant existe. 7 tests unitaires sur la logique de sélection (même saison / changement de saison / fin de série / map vide / trous de numérotation).

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

**Modèle recommandé : Sonnet 5, effort moyen** — API media3 standard, appliquée à 3 fichiers de façon répétitive, faible ambiguïté.

Ajoute un choix de mode de redimensionnement (fit/remplir/zoom/étirer) sur les players.

Contexte existant :
- Aucun usage de `resizeMode` ou `RESIZE_MODE_*` media3/ExoPlayer trouvé dans tout le repo — fonctionnalité totalement absente, à créer de zéro.
- 3 players concernés : `presentation/player/PlayerScreen.kt` (Live TV), `presentation/vod/VodPlayerScreen.kt`, `presentation/series/SeriesPlayerScreen.kt`.

À faire :
1. Détermine où chaque player instancie son `PlayerView` (Compose interop `AndroidView`, probablement) dans les 3 fichiers ci-dessus.
2. Ajoute un bouton dans les contrôles (icône type "aspect ratio") qui cycle ou ouvre un menu entre les modes media3 : `RESIZE_MODE_FIT`, `RESIZE_MODE_FILL`, `RESIZE_MODE_ZOOM` (les modes standards disponibles ; vérifie la liste exacte exposée par la version de media3 utilisée dans `build.gradle.kts`).
3. Persiste le choix utilisateur (SharedPreferences via `SettingsManager`, un réglage par type de player ou un seul réglage global — à trancher selon simplicité voulue ; recommandation : un seul réglage global partagé entre les 3 players, plus simple et cohérent pour l'utilisateur).
4. Applique le mode au lancement de chaque player selon la préférence sauvegardée.

Tests : persistance du choix, application correcte sur les 3 players, pas de régression sur les contrôles existants (skip, next episode, zapping chaînes).

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

## Notes transverses

- Toutes ces features touchent potentiellement Room (migrations) : respecter la convention du projet — pas de `fallbackToDestructiveMigration()`, migration explicite ajoutée à `ALL_MIGRATIONS` (`di/AppModule.kt`), voir `AGENTS.md`.
- Recette manuelle sur device/émulateur recommandée pour tout ce qui touche à ExoPlayer/media3 (players) — ces flux ne sont pas facilement testables unitairement.
- Convention de fin de tâche du projet (voir `AGENTS.md`) : commit + tag SemVer (minor pour une nouvelle feature, pas patch) + push après chaque feature terminée.
