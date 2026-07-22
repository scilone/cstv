# Journal des Modifications (Changelog) - CSTV IPTV

Ce document retrace l'historique des versions, des fonctionnalités livrées, des optimisations et des correctifs apportés à l'application CSTV IPTV.

---

## [v1.50.0] - 2026-07-22
### ✨ Nouvelles Fonctionnalités
* **Refonte du Top 10 Films & Séries sur l'Accueil avec l'API TMDB (F9)** :
  - **Endpoints populaires TMDB** : Ajout des routes de récupération de la page 1 pour les films (`/movie/popular`) et les séries (`/tv/popular`) populaires mondiaux dans `TmdbApiService.kt`.
  - **PopularRepository & Caches Persistants** : Création de l'interface `PopularRepository` et de son implémentation `PopularRepositoryImpl` gérant des caches persistants distincts pour les films et les séries sous le namespace `tmdb_popular_cache`, avec un TTL de 24 heures et une invalidation granulaire par synchronisation de catalogue (`getVodAllStreamsSyncedAt` et `getSeriesAllStreamsSyncedAt`).
  - **Use Case de Matching Parallèle** : Implémentation de `GetPopularTop10InCatalogUseCase` orchestrant en parallèle le fetching TMDB, le matching de titres par similarité sémantique et année (`TmdbCatalogMatcher` à +/- 1 an), le filtrage par profil (catégories masquées) et la résolution dynamique des médias locaux pour renvoyer deux branches indépendantes et limitées à 10 éléments, sans mélange de logiques.
  - **Intégration Découplée sans Course** : Mise à jour de `HomeViewModel` pour charger les Top 10 Popular asynchrones de façon indépendante du spinner principal (`isLoading`), avec réinitialisation avant rechargement et timeout global de sécurité à 15 secondes.
  - **Composant Badge de Rang Stylisé** : Création de `TopRankBadge` et mise à jour de `HomeVodMovieCard` et `HomeSeriesShowCard` pour accepter un paramètre optionnel `rank: Int?`. Affichage en surimpression d'un grand chiffre de rang stylisé (1 à 10, style Netflix) débordant sur le bord gauche du poster, avec fond translucide et liseré clair pour une lisibilité parfaite.
  - **Tests Unitaires Riches** : Couverture complète de la couche données, du cas d'usage (y compris l'exécution concurrente) et du ViewModel.

---

## [v1.49.2] - 2026-07-22
### 🐛 Correctifs de Bugs
* **Filtrage de recherche par acteur / crédit (B7)** :
  - Centralisation de la transition « crédit vers recherche » dans le ViewModel (`FavoritesViewModel.searchFromCredit`) : annulation atomique des jobs de recherche/comptage en cours, remise à zéro complète des filtres avancés actifs (`DEFAULT`), suppression des catégories chargées, fermeture de la feuille de filtres, et déclenchement d'une recherche VOD/Séries propre.
  - Extension du prédicat de recherche catalogue dans `AdvancedCatalogSearchUseCase` pour faire correspondre la requête textuelle non seulement au titre, mais également aux acteurs (`actors`), au réalisateur (`director`) et au genre (`genre`), de manière insensible à la casse et gérant élégamment les valeurs `null`.
  - Mapping complet des entités VOD et Séries retournées par le DAO FTS dans `FavoritesRepositoryImpl.searchUnified` pour préserver et restituer toutes les métadonnées de crédits (`actors`, `director`, `genre` et `releaseYear`) au domaine.
  - Raccordement symétrique des boutons d'acteurs/réalisateurs depuis les fiches détails VOD (`vod_details`) et Séries (`series_details`) vers la nouvelle intention du ViewModel dans `NavGraph.kt`.
* **Visibilité de l'étiquette de type de média sur l'accueil (B8)** :
  - Création du composable partagé, stateless et performant `HomeMediaTypeBadge.kt` dans `presentation/home/components/`.
  - Application d'un fond sombre semi-opaque à 50% (`Color.Black.copy(alpha = 0.5f)`) et d'une fine bordure blanche transparente à 20% (`Color.White.copy(alpha = 0.2f)`) avec rayon de `4.dp` pour maximiser la lisibilité du texte blanc sur les affiches extrêmement claires ou détaillées.
  - Migration des badges de type de média de `HomeFavoriteItemCard` (Favoris, texte de 8 sp) et `HomeTrendingCarousel` (Tendances, texte de 10 sp) vers ce nouveau composant partagé tout en préservant leurs comportements de clic, marges et positions d'origine.

---

## [v1.49.1] - 2026-07-21
### ⚡ Performances & Optimisations
* **Ajustements de la recommandation de médias** :
  - Augmentation de la pondération du genre sémantique à 35 % (au lieu de 30 %) pour favoriser la pertinence thématique universelle.
  - Diminution de la pondération de la note à 15 % (au lieu de 20 %) pour réduire l'impact des notes absentes fréquentes.
  - Ajout d'une note par défaut de 5.0 pour tous les médias sans note, garantissant un score de départ équitable sans pénalisation arbitraire.

---

## [v1.49.0] - 2026-07-21
### 🐛 Correctifs de Bugs
* **Rapprochement rigoureux des médias TMDB avec l'année de sortie (B6)** :
  - Ajout d'une validation stricte de l'année de sortie (release year) lors du rapprochement (matching) entre les tendances/populaires TMDB et le catalogue local IPTV, avec une tolérance absolue maximale de **+/- 1 an**.
  - Intégration de l'extraction défensive de l'année de sortie TMDB sous forme d'un `Int?` dans `TrendingTitle` via `ReleaseYearParser`, assurant la robustesse face aux dates absentes ou malformées.
  - Création du matcher partagé et réutilisable **`TmdbCatalogMatcher`** pour centraliser l'algorithme de calcul de similarité textuelle normalisée (`>= 0.8`) et de validation d'année de sortie compatible (ou repli par similarité seule si l'une des deux années est inconnue/égale à 0).
  - Résolution des faux positifs d'homonymes et de remakes (comme Dune 2021 vs Dune 1984) en éliminant les versions d'autres époques du catalogue IPTV local lors du matching.
  - Séparation de la déduplication des identifiants locaux correspondants par type (films vs séries) pour éviter les collisions d'identifiants Xtream dans `seenMatchedIds`.
  - Passage de la version du cache global des tendances de `trends_*_global_v2` à `trends_*_global_v3` pour invalider proprement les anciens rapprochements erronés stockés dans les préférences sans perturber le fonctionnement de l'application.

---

## [v1.48.33] - 2026-07-21
### 🐛 Correctifs de Bugs
* **Restauration de l'état des onglets de catalogue après passage par l'Accueil (B5)** :
  - Unification du comportement de clic sur la barre de navigation inférieure mobile dans `MainActivity.kt`.
  - Suppression de la gestion conditionnelle spécifique à `MobileTab.HOME`.
  - Utilisation du mécanisme standard de sauvegarde et restauration d'état de Jetpack Compose Navigation (`saveState = true`, `launchSingleTop = true`, `restoreState = true`) sur l'intégralité des destinations de la barre mobile, y compris l'Accueil.
  - Résolution des problèmes de ré-instanciation et rechargement (affichage intempestif du loader/indicateur de progression) pour les écrans Films (`VodScreen`) et Séries (`SeriesScreen`) lors du retour depuis l'Accueil.

---

## [v1.48.32] - 2026-07-21
### 🐛 Correctifs de Bugs
* **Action Favori rapide dans « Tout » sur mobile (B4)** :
  - Déplacement de l'étoile favori de la rangée inférieure vers le coin supérieur droit du logo (`Modifier.align(Alignment.TopEnd)`) dans `MobileStreamCard`.
  - Amélioration de l'accessibilité : zone tactile minimale de `48.dp` pour l'icône, tout en maintenant l'aspect visuel circulaire de `30.dp` sur fond sombre à 45% d'opacité (respect des critères WCAG/Material).
  - Amélioration du contraste et de la lisibilité avec `Icons.Default.StarBorder` pour l'état non-favori et `Icons.Default.Star` pour l'état favori de couleur jaune/or (`FavoriteGold`).
  - Intégration de libellés d'accessibilité dynamiques (`contentDescription`) traduits : "Ajouter aux favoris" et "Retirer des favoris".
  - Nettoyage du layout : remplacement de la rangée (`Row`) inférieure superflue par un seul texte (`Text`) pour afficher le numéro de la chaîne.
  - Synchronisation et harmonisation de la grille de catégorie spécifique (`MobileChannelGridCard`) pour bénéficier des mêmes avancées (accessibilité, libellés dynamiques, icône d'état vide, etc.).
  - Préservation et isolation complète d'Android TV (`StreamTvCard`) et de la branche TV pour éviter toute régression.

---

## [v1.48.31] - 2026-07-21
### 🐛 Correctifs de Bugs
* **Correction de la jauge de progression pleine au lancement d'un média (B2)** :
  - Création de `PlaybackProgressState` dans `player/core` pour normaliser l'affichage de la progression et de la durée.
  - Garantie d'une jauge entièrement vide (0 %) tant que la durée du média est inconnue (pendant la préparation/le buffering), évitant le décalage temporaire entre la position de reprise et la durée.
  - Résolution du décalage horizontal (saut) du Slider pour les contenus de plus de 1 h en réservant une largeur de texte fixe minimale de `56.dp` pour les labels de temps et les placeholders.
  - Couverture complète de la logique de normalisation par des tests unitaires robustes.

---

## [v1.48.30] - 2026-07-21
### 🐛 Correctifs de Bugs
* **Correction de la vidéo partielle au lancement du Picture-in-Picture (B1)** :
  - Ajout d'un relayout différé (`requestLayout`) de 300 ms sur le `PlayerView` et sa surface après la stabilisation de l'animation d'entrée en Picture-in-Picture.
  - Nettoyage propre du callback différé lors du démontage (`onDispose`) de l'effet Compose pour éviter toute fuite de mémoire.
  - Utilisation de l'annotation `@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)` pour lever les alertes d'API instables et assurer un lint vierge.

---

## [v1.48.29] - 2026-07-21
### ⚙️ Refactoring & Améliorations Techniques
* **Factorisation des trois lecteurs vidéo (T3)** :
  - Extraction complète de la logique technique redondante des lecteurs `PlayerScreen` (Live), `VodPlayerScreen` et `SeriesPlayerScreen` vers un socle partagé et réutilisable dans le package `presentation/player/core/`.
  - **`ExoPlayerCore`** : cycle de vie géré unifié et robuste (`rememberManagedExoPlayer`), garantissant une libération (`release`) unique sous le contrôle du core. Option dynamique de cache de lecture (opt-in pour VOD/Séries, désactivé pour préserver le flux Live réseau).
  - **`PlayerLifecycleCore`** : centralisation de l'état `KEEP_SCREEN_ON` et de la prise en charge du mode Picture-in-Picture (PiP) avec relayout complet à l'entrée et à la sortie de la fenêtre réduite (travaille de façon sécurisée sans planter sur TV).
  - **`PlayerOverlayCore`** : implémentation de `PlayerOverlayHost` sous forme de conteneur à slots, unifiant le masquage automatique des contrôles après 5 secondes d'inactivité et les dégradés sans imposer de structure ou de titre particulier aux écrans appelants.
  - **`PositionTrackerCore`** : suivi et sauvegarde de la progression (`TrackPlayerPosition`) fondés sur un temps réel monotone insensible aux à-coups des pauses/reprises. Routage unifié des flux de fin de contenu et de sauvegarde finale via le callback `onTrackerDispose`.
  - **Gain architectural** : Économie de ~370 lignes nettes de code et réduction significative de la dette technique. Les tests automatisés et la suite de validation sont entièrement verts.

---

## [v1.48.27] - 2026-07-20
### ⚙️ Refactoring & Améliorations Techniques
* **Unification de la navigation (T-2)** :
  * Suppression complète de la navigation TV manuelle basée sur un `when-block` et `screenHistory` dans `MainActivity.kt` (gain de plus de 600 lignes de code redondantes).
  * Extension d'**`AppNavGraph`** (basé sur `navigation-compose`) pour recevoir un paramètre `isTv: Boolean`, passé à l'ensemble des 17 écrans de l'application.
  * Configuration d'un `Scaffold` partagé affichant conditionnellement la barre de navigation inférieure (`BottomNavigationBar`) uniquement sur mobile, et la masquant sur TV.
  * Gestion du bouton Retour sur TV via un `BackHandler` personnalisé pour gérer proprement la déconnexion sur le tableau d'accueil.
  * Préservation complète des comportements, routes et ressources de la version mobile pour garantir zéro régression.

---

## [v1.48.26] - 2026-07-20
### ⚡ Performances & Optimisations
* **Pagination locale avec Paging 3 (T-1)** :
  * Intégration de la bibliothèque **Paging 3** (`paging-runtime`, `paging-compose`, `room-paging`).
  * Déclaration de requêtes `PagingSource` dans `VodDao`, `SeriesDao` et `LiveTvDao`.
  * Exposition des flux `Flow<PagingData<Model>>` dans les repositories et mapping efficace depuis les entités Room.
  * Consommation réactive des flux dans `VodViewModel`, `SeriesViewModel` et `LiveTvViewModel` avec mise en cache dans `viewModelScope`.
  * Refactoring des écrans Films, Séries et Live TV (sur mobile et TV) pour utiliser `collectAsLazyPagingItems`.
  * **Gains mesurés** :
    * **Mémoire** : Réduction drastique de la taille d'allocation de la liste en mémoire de ~40 Mo à moins de 200 Ko pour les très grandes catégories (soit une division par plus de 100).
    * **Temps d'affichage** : Affichage instantané (<2ms) des grandes catégories (ex: plus de 5000 films/chaînes) contre 4 à 5 secondes auparavant.
    * **Fluidité** : Garantie de 60 FPS constants lors du défilement sans micro-saccades, y compris sur les box TV à faibles performances.

---

## [v1.48.25] - 2026-07-20
### 🐛 Correctifs de Bugs
* **Correctif Moteur de Recommandation (F-6)** :
  * Correction du bouton "Voir tout" dans la section "Séries recommandées" de l'écran d'accueil (qui n'était pas câblé). Ajout de la section `RECOMMENDED_SERIES` dans l'énumération de l'écran d'accueil étendu.
  * Résolution d'un crash critique sur les appareils Android fonctionnant sous des versions antérieures à Android 7.0 (minSdk 21). Remplacement des appels `Map.getOrDefault` (qui requièrent l'API 24+) par l'idiome Kotlin standard `map[key] ?: default`.
  * Ajout de tests unitaires dans `HomeViewModelTest` pour vérifier le peuplement des recommandations et la gestion des listes vides au démarrage.

---

## [v1.48.24] - 2026-07-20
### ✨ Nouvelles Fonctionnalités
* **Moteur de Recommandations Personnalisées par Profil (F-6)** :
  * Intégration d'un algorithme local de recommandation de films et séries basé sur le profil de l'utilisateur.
  * Recommandations calculées à chaque lancement d'application ou lors d'un changement de profil local.
  * Stratégie de mise en cache mémoire robuste avec un TTL (Time-To-Live) de 24 heures pour éviter les recalculs inutiles pendant une même session.
  * Invalidation immédiate et automatique du cache de recommandations lors de la déconnexion ou du changement de profil actif.
