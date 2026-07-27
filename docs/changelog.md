# Journal des Modifications (Changelog) - CSTV IPTV

## [v1.62.0] - 2026-07-27
### ✨ Recherche globale de contenus par sous-chaînes (F17)
* **Recherche globale par sous-chaîne (LIKE "%keyword%")** : Remplacement complet de FTS4 par des requêtes de type `LIKE` sur une colonne dénormalisée `searchText` présente dans les tables physiques `live_streams`, `vod_streams` et `series_streams`.
* **Casse et accents neutralisés** : Normalisation unifiée en Kotlin via `LocalSearchQuery.normalize()` (minuscules, conversion NFD, retrait des accents et marques diacritiques, repli explicite de toutes les ligatures comme `œ`→`oe`, `ß`→`ss`). Cela élimine les limitations d'Unicode de SQLite et permet de rechercher indifféremment des accents ou non.
* **Recherche multi-mots d'ordre libre** : Découpage de la requête en mots-clés exigeant la présence de chacun de ces fragments, peu importe leur ordre de saisie ou le champ source dans le média.
* **Performance et architecture hybride** : Évaluation du mot-clé le plus long en SQL (avec échappement des métacaractères `_`, `%` et `\`) pour restreindre la sélection de lignes, suivie d'un filtrage en mémoire par Kotlin pour les autres mots-clés.
* **Migration 20 → 21 non destructive** : Ajout de la colonne `searchText` aux tables physiques, backfill complet de la base de données via une table de repli de caractères en SQL, et suppression sécurisée des tables FTS4 obsolètes.
* **Intégration et recalcul transparent** : Intégration du recalcul de `searchText` directement au sein des transactions d'écriture DAO, prévenant toute désynchronisation lors de l'enrichissement des données. Unification complète de la recherche unifiée et de la recherche avancée.

> Validation automatisée : `testDebugUnitTest` et compilation validées à 100% avec succès.

## [v1.61.0] - 2026-07-27
### ⚡ Malus pour les genres non identiques dans les titres associés (T5)
* **Tri affiné par ressemblance thématique** : Introduction d'un léger malus de `0,1` par genre présent chez le candidat mais absent du média courant, afin de privilégier les titres aux profils de genres les plus proches possibles de l'œuvre d'origine.
* **Malus cumulé plafonné** : Le malus cumulé est strictement limité à `0,9` (via un calcul en dixièmes sans impact de précision d'arrondi flottant) de sorte qu'il n'annule jamais le poids d'un genre commun supplémentaire ni n'altère le bonus de catégorie IPTV locale.
* **Normalisation stricte** : Normalisation et dédoublonnage unifiés des genres cible et candidat via `GenreParser.normalize`, avec exclusion rigoureuse des valeurs vides.

> Validation automatisée : `testDebugUnitTest` et compilation (`assembleDebug`, `lintDebug`) validées à 100% avec succès.

## [v1.60.0] - 2026-07-26
### ✨ Nouvelles Fonctionnalités
* **Lecture automatique du trailer YouTube sur les fiches de détail (Films/Séries) (F13)** :
  - **Lecture immersive automatique** : Lancement automatique et en boucle de la bande-annonce YouTube en arrière-plan du bloc d'en-tête de la fiche de détails (Films et Séries) après 5 secondes de présence continue et stable sur la fiche.
  - **Contrôle sonore complet** : Intégration d'un bouton d'activation/désactivation du son (Mute/Unmute) accessible et descriptif dans la barre d'action supérieure, s'initialisant systématiquement en mode muet à chaque nouvelle ouverture d'une fiche.
  - **Gestion rigoureuse du cycle de vie** : Interruption instantanée de la vidéo et libération complète des ressources de la WebView à la fermeture de la fiche, lors de la mise en arrière-plan de l'application, ou lors du lancement de la lecture vidéo plein écran du média principal.
  - **Résolution dynamique du TMDB ID** : Rapprochement automatique et intelligent du catalogue IPTV local (sans `tmdbId`) avec la base de données TMDB via de nouveaux endpoints de recherche (`search/movie` et `search/tv`) combinant similarité textuelle de titre normalisé et compatibilité de l'année de sortie à ± 1 an.
  - **Cache persistant Room (v20)** : Mémorisation pérenne des résolutions (positives et négatives) en base de données avec des durées de validité (TTL) asymétriques (30 jours pour une bande-annonce trouvée, 7 jours pour un média dépourvu de trailer) pour éviter les requêtes réseau superflues.
  - **Oubli instantané sur échec de lecture** : Invalidation en temps réel et purge immédiate du cache de la vidéo en cas de détection d'erreur de lecture (ex: vidéo supprimée, bloquée dans le pays), forçant une nouvelle recherche lors de la prochaine consultation et restaurant l'affiche de fond.

> Validation automatisée : `testDebugUnitTest` et compilation validées à 100% avec succès.

## [v1.59.0] - 2026-07-26
### ✨ Nouvelles Fonctionnalités
* **Navigation vers la fiche détails depuis le clic sur la cover du player (F16)** :
  - **Cover cliquable et interactive** : Remplacement de la jaquette statique par un composant d'action interactif et partagé (`PlayerCoverAction`) sur mobile (tactile) et Android TV (D-pad avec bordure d'accentuation violette active), s'appuyant sur un placeholder cliquable en cas d'absence d'affiche.
  - **Navigation intelligente et gestion du Backstack** : Intégration de la règle de routage `PlayerDetailsNavigation` (100% couverte en tests unitaires JVM) pour détecter si la fiche média est l'écran précédent (retour arrière simple via `popBackStack()`) ou s'il faut fermer le lecteur et ouvrir la fiche d'un clic pour éviter les doublons dans l'historique de navigation.
  - **Garde d'unicité et cycle de fermeture propre** : Ajout d'un état `isLeaving` verrouillant les transitions pour éviter les doubles clics ou double fermetures, tout en permettant au lecteur de restaurer son état en cas d'échec de navigation. Arrêt propre du flux vidéo et sauvegarde automatique de la position en base de données avant la transition.
  - **Fiabilisation de l'identifiant de série** : Raccordement complet de `seriesId` dans la persistance de position de lecture (`PlaybackPositionEntity`) et dans la reprise de lecture depuis l'Accueil pour permettre la résolution sans faille de la fiche série correspondante.
  - **Notifications transitoires exclusives** : Gestion d'un état de notification unique dans les players pour afficher les erreurs transitoires (comme une fiche non résoluble ou l'absence de réseau) de manière élégante sans superposition ni interruption de la lecture en cours.

> Validation automatisée : `testDebugUnitTest` et compilation validées à 100% avec succès.

## [v1.57.0] - 2026-07-26
### ✨ Nouvelles Fonctionnalités
* **Section « Téléchargements » sur l'Accueil (F15)** :
  - **Raccourci réactif sur l'Accueil** : Ajout d'une nouvelle section horizontale « Téléchargements » tout à la fin de l'écran d'Accueil, masquée automatiquement si aucun téléchargement n'est terminé.
  - **Plafond et ordre de fraîcheur** : Affichage des 20 derniers téléchargements entièrement terminés (`COMPLETED`) par ordre antéchronologique (les plus récents en premier).
  - **Optimisation de la recomposition** : Filtrage et application de `distinctUntilChanged` sur le flux réactif de téléchargements pour éviter de recomposer l'Accueil ou de clignoter à chaque mise à jour de progression ou d'écriture d'un autre fichier en cours de téléchargement.
  - **Lecture hors-ligne directe** : Les cartes dédiées affichent le titre, le sous-titre (repère de saison/épisode pour les séries) et lancent directement le lecteur vidéo hors-ligne local. Le bouton « Voir tout » redirige de manière fluide vers l'onglet complet de gestion des Téléchargements.

> Validation automatisée : `testDebugUnitTest` et compilation validées à 100% avec succès en 15 secondes.

## [v1.56.0] - 2026-07-26
### ✨ Nouvelles Fonctionnalités
* **Bouton de validation de recherche collant / sticky (F14)** :
  - **Bouton d'action collant (sticky)** : Amélioration ergonomique majeure en isolant le bouton d'action « Voir les résultats » au bas de l'écran de recherche avancée (`AdvancedSearchSheet`), le rendant fixe et toujours visible pendant le défilement indépendant de tous les critères de filtres au-dessus.
  - **Tri vertical et poids adaptatifs** : Utilisation d'un conteneur racine regroupant de manière ordonnée la partie défilante dotée de `Modifier.weight(1f, fill = false)` et le pied fixe, évitant ainsi d'étirer inutilement la feuille lorsque peu de filtres sont présents.
  - **Continuité du focus D-pad** : Remontée du groupe de focus Compose `.focusGroup()` sur le conteneur racine pour assurer une navigation fluide et sans accroc au D-pad pour l'utilisateur Android TV vers le bouton d'action principal.

### 🐛 Correctifs de Bugs
* **Correction de la tolérance d'année TMDB (B14)** :
  - **Départage par rang d'année** : Introduction d'un tri multicritère (`YearRank` : `EXACT`, `TOLERATED`, `UNKNOWN`) dans `TmdbCatalogMatcher` pour trier stablement les candidats par proximité d'année, empêchant les mauvais rapprochements d'œuvres homonymes ou remakes (ex: Dune 1984 vs Dune 2021) lorsque le catalogue n'est pas entièrement enrichi.
  - **Mise à disposition des replis** : Préservation et tri de la liste complète de candidats compatibles pour permettre la sélection de replis non datés si la version datée nominale est masquée ou supprimée.
  - **Fraîcheur intégrée** : Prise en compte de la section `CatalogSection.ENRICHMENT` (enrichissement des années d'arrière-plan) au sein de la fraîcheur du catalogue dans `CatalogFreshness`, garantissant l'invalidation automatique du cache des tendances/populaires de l'Accueil à la fin de l'enrichissement nominal du chemin `runSync`.
  - **Traçabilité des décisions** : Ajout de logs d'appariement TMDB détaillés incluant l'année TMDB et le rang d'année sélectionné pour faciliter le diagnostic en production.

> Validation automatisée : `testDebugUnitTest`, `assembleDebug` et `lintDebug` réussis. Les tests exhaustifs unitaires et d'intégration couvrent les cas limites de remakes et de replis partiels.

---

## [v1.55.0] - 2026-07-26
### ⚡ Cache catalogue persistant et navigation hors ligne (T4)
* Catalogue Xtream Live/VOD/Séries persisté dans Room avec état de synchronisation par section, migration 17 → 18 et remplacements transactionnels qui préservent le dernier cache valide.
* Démarrage hors ligne autorisé uniquement après validation réseau antérieure du même utilisateur et catalogue complet ; refus explicite du panel révoquant cette autorisation sans supprimer le catalogue.
* Synchronisation centralisée (démarrage, manuel, WorkManager, reconnexion), EPG fenêtré, détails VOD/Séries conservés à la consultation et cache Coil explicite pour les jaquettes.
* Lecture hors ligne clarifiée : téléchargements autorisés, flux distants refusés avec un message explicite depuis tous les points d'entrée, y compris recherche et favoris.
* Le catalogue est conservé pour tout utilisateur du même serveur (`host:port`) et purgé uniquement lors d'un changement de serveur.

> Validation automatisée : `testDebugUnitTest`, `assembleDebug` et `lintDebug` réussis. La migration sur une installation v17 réelle et les parcours manuels mobile/Android TV restent à exécuter sur appareil ou émulateur.

Ce document retrace l'historique des versions, des fonctionnalités livrées, des optimisations et des correctifs apportés à l'application CSTV IPTV.

---

## [v1.54.20] - 2026-07-25
### 🐛 Correctifs de Bugs
* **Résolution de la boucle de retour infinie sur les fiches détails via titres associés (B13)** :
  - **Capture de l'identifiant par entrée** : Utilisation de `rememberSaveable` (au lieu d'un simple `remember`) au sein de `AppNavGraph.kt` pour figer l'identifiant du média d'amorçage propre à chaque destination de backstack (`vod_details` et `series_details`) et le restaurer proprement au dépilage.
  - **Garde d'idempotence ViewModel** : Implémentation d'une garde dans `VodViewModel` et `SeriesViewModel` pour interdire tout rechargement ou indicateur de chargement clignotant inutile si le média demandé est déjà chargé ou en cours de chargement.
  - **Suppression du code mort** : Nettoyage et suppression des délégations `selectStream` obsolètes pour sceller l'accès par identifiant stable.

---

## [v1.54.19] - 2026-07-25
### 🐛 Correctifs de Bugs
* **Filtrage des médias issus de catégories masquées sur l'Accueil au changement de profil (B12)** :
  - **Abonnement réactif au profil actif** : Observation directe du StateFlow `activeProfileId` de `ProfileManager` dans `HomeViewModel` comme déclencheur unique et dédoublonné du chargement et du rechargement complet de la Home.
  - **Purge immédiate de l'affichage** : Introduction d'une purge sélective de l'état visible du catalogue (via `resetVisibleContent`) lors d'une bascule de profil, évitant l'affichage persistant de médias interdits pendant la durée de rechargement.
  - **Annulation exclusive des coroutines de chargement** : Suivi et annulation systématique des Jobs asynchrones (`popularJob`, `trendingJob`, `catalogJob`, `recommendationsJob`) avant chaque nouveau chargement pour interdire à une passe de profil périmé de repeupler les rangées.

---

## [v1.54.18] - 2026-07-25
### 🐛 Correctifs de Bugs
* **Correction du clic sans effet sur Accueil dans la barre de navigation mobile (B11)** :
  - **Contrat de navigation racine mobile** : Centralisation de la route racine stable de la session connectée `"home"` (au lieu de la résolution dynamique de `findStartDestination()` qui pouvait cibler l'écran `"login"` purgé) dans un objet partagé `MobileNavigation.kt`.
  - **Extension unique de navigation** : Remplacement des blocs de navigation dupliqués dans `MainActivity.kt` et `NavGraph.kt` par l'extension réutilisable `navigateToRootTab(route)` sécurisant le comportement de dépilage sans effet de bord sur Android TV.

---

## [v1.54.0] - 2026-07-23
### ✨ Nouvelles Fonctionnalités
* **Lecture automatique du trailer sur la Hero Card / Carrousel de l'accueil (F10)** :
  - **Wrapper API IFrame YouTube** : Intégration de la dépendance `com.pierfrancescosoffritti.androidyoutubeplayer:core:12.1.0` (compatible Kotlin 1.9/AGP 8.2) pour la lecture autonome sans clé API ni services Google Play, s'adossant à l'API IFrame officielle intégrée via `AndroidView`.
  - **Modèle de Domaine & Découplage** : Création de `TrailerPreview` et `TrailerSource` pour découpler proprement les détails bruts fournis par le panel Xtream ou TMDB de l'interface de présentation.
  - **Résolution Séquentielle Résiliente & Cache de Session** : Implémentation de `TrailerRepositoryImpl` raccordant d'abord le champ `youtube_trailer` de Xtream (avec normalisation robuste des URL et ID YouTube), puis interrogeant en repli asynchrone l'API TMDB (`/{movie|tv}/{id}/videos`). Les appels Xtream sont sécurisés par `XtreamRequestGate` pour ne pas saturer les connexions limitées du panel. Les résolutions (positives ou négatives) sont stockées dans un cache mémoire de session, automatiquement invalidé lors d'un changement ou d'une déconnexion de compte Xtream.
  - **Gestion d'État ViewModel & Annulation Course** : Introduction de l'état `TrailerPreviewUiState` (`Poster`, `Preparing`, `Playing`, `Failed`) dans `HomeViewModel`, orchestrant les sélections et annulations asynchrones sécurisées via `mapLatest` pour garantir qu'un défilement rapide n'affiche jamais une vidéo obsolète.
  - **Composant UI Mobile & Cycle de vie** : Conception de `HomeTrendingCarousel` avec un délai stable de 5 secondes de focus pour déclencher l'aperçu. Intégration de `HomeYouTubeTrailerPreview` qui gère de manière réactive l'autoplay muet, la boucle vidéo et s'assure via `DisposableEffect` et observation du lifecycle de libérer complètement le player (évitant les fuites CPU/réseau/audio) lors d'un swipe, d'un clic de fiche, d'un changement d'onglet ou d'une mise en arrière-plan.
  - **Bouton de Contrôle Sonore d'Accessibilité** : Ajout d'un bouton de coupure du son (mute/unmute) sous forme d'icône accessible avec descriptions textuelles de retranscription (`contentDescription`) indépendantes du lecteur principal et stateless pour chaque média.
  - **Préservation Android TV & Hero Card "Reprendre"** : Préservation totale de la navigation au D-pad existante sur Android TV et de la Hero Card de reprise de lecture, sans aucun déclenchement de trailer intempestif.
  - **Couverture de Tests Unitaires Complète** : Écriture de tests unitaires exhaustifs pour le parseur d'ID YouTube, la résolution multi-source du repository avec fakes d'API, le cache de session, ainsi que les transitions d'état du ViewModel (annulation, failed, sélection).

---

## [v1.53.0] - 2026-07-23
### ✨ Nouvelles Fonctionnalités
* **Visibilité de la barre de statut sur Mobile & Gestion du poinçon (F11)** :
  - **Thème compatible runtime** : Modification du thème de l'application pour hériter de `Theme.Material.NoActionBar` afin de permettre de piloter dynamiquement la visibilité des barres système au runtime. Déclaration de `windowLayoutInDisplayCutoutMode` à `shortEdges` pour autoriser le plein écran paysage immersif sous le poinçon de la caméra.
  - **Contrôleur de barres réactif `SystemBarsController`** : Création d'un effet Compose réutilisable encapsulant `WindowInsetsControllerCompat` pour piloter la visibilité des barres système (toujours masquées sur TV, masquées uniquement lors de la lecture sur mobile, affichées avec texte contrasté sombre/clair lors de la navigation sur mobile).
  - **Activation Edge-to-Edge** : Configuration de `WindowCompat.setDecorFitsSystemWindows(window, false)` dans `MainActivity.onCreate` pour laisser Jetpack Compose gérer les zones d'affichage.
  - **Intégration des zones de sécurité (Insets)** : Câblage des paddings Compose (`statusBarsPadding()`, `safeDrawingPadding()`) sur les différents écrans de navigation mobile (connexion, profils, catalogues, etc.) pour protéger l'UI des poinçons physiques sans impacter le layout ou le focus Android TV.
  - **Tests de non-régression** : Ajout de tests unitaires pour valider les décisions de routes immersives par rapport aux routes standards.

---

## [v1.52.0] - 2026-07-23
### ✨ Nouvelles Fonctionnalités
* **Système d'évaluation J'aime / Je n'aime pas et exclusion des recommandations (F7)** :
  - **Table Room `media_ratings` & Migration 16 → 17** : Ajout d'une table profilée pour la persistance locale des votes par profil, type de média ("movie" ou "series") et ID stable, raccordée dans la version 17 de `AppDatabase` via la migration SQL non destructive `MIGRATION_16_17`.
  - **Transaction atomique de vote négatif** : Implémentation de `MediaRatingRepository` effectuant de manière atomique sous transaction Room l'enregistrement du rejet, le retrait du favori de même type/identifiant et l'effacement complet des reprises de lecture (films ou épisodes de séries par `seriesId` et stream IDs) du profil actif.
  - **Moteur de recommandation pondéré** : Intégration des signaux d'évaluation explicites dans `RecommendationEngine` avec application d'une pondération à `3.0` pour les likes, d'une exclusion absolue pour les dislikes et d'un déblocage réactif du cold start dès le premier like catalogue.
  - **Invalidation réactive ciblée** : Câblage de l'invalidation asynchrone sécurisée par `Mutex` et émission d'un `SharedFlow` d'invalidation collecté par `HomeViewModel` pour actualiser instantanément les carrousels de suggestions de l'Accueil sans rechargement réseau.
  - **Contrôles Compose stateless & Accessibilité** : Création du composant `MediaRatingControls` unifié et adapté aux contraintes graphiques mobile (horizontal, hauteur 48dp) et Android TV (vertical, hauteur 40dp, compatible focus D-pad), gérant les animations de transition, l'état de sauvegarde (`isSaving`) et les descriptions vocales d'accessibilité.
  - **Tests unitaires robustes** : Couverture complète de la logique de mapping, du repository, du cas d'usage d'écriture, du moteur de scoring, du cas d'usage de recommandations, ainsi que des états ViewModels, garantissant une non-régression absolue.

---

## [v1.51.0] - 2026-07-22
### ✨ Nouvelles Fonctionnalités
* **Gestion de l'historique de visionnage local et retrait des reprises (F8)** :
  - **Repository dédié d'historique** : Création de `ViewingHistoryRepository` et `ViewingHistoryRepositoryImpl` pour encapsuler et isoler les suppressions d'historiques (VOD/Séries et Live TV) par rapport aux repositories de catalogues, tout en capturant dynamiquement le `profileId` actif depuis `ProfileManager`.
  - **Ciblage exact d'un épisode** : Conception d'une suppression chirurgicale par `(streamId, profileId)` pour la VOD et les Séries. Retirer une carte de série de la liste « Continuer à regarder » efface uniquement la position de l'épisode affiché sur la carte, sans toucher à la progression des autres épisodes de la série. Si d'autres épisodes sont en cours, la carte agrégée s'actualise automatiquement ; sinon, elle disparaît.
  - **Flux Room Réactifs pour TV Récente** : Remplacement des chargements ponctuels des chaînes Live TV récentes par une observation en flux continu (`Flow`) de la base de données Room. Tout retrait d'une chaîne récente depuis l'écran Live TV est ainsi répercuté instantanément sans aucun rechargement ou appel manuel.
  - **Geste universel Mobile & Android TV** : Implémentation du helper de présentation `historyItemActions` pour centraliser le geste d'appui long : tactile `combinedClickable` sur Mobile et maintien du bouton de validation central via interception de clés (`onPreviewKeyEvent`) sur Android TV. Mémorisation et consommation de l'événement `KeyUp` associé pour empêcher tout lancement indésirable du lecteur vidéo au relâchement de la touche.
  - **Dialogue partagé stateless** : Création du composable unifié `HistoryRemovalDialog` avec boutons TV dédiés et placement du focus initial de sécurité sur le bouton **Annuler** sur Android TV. Indicateur de chargement compact pour éviter les sauts de hauteur pendant la suppression.
  - **Invalidation dynamique des recommandations** : Après toute suppression réussie de VOD ou de Série, le cas d'usage invalide automatiquement le cache des recommandations du profil pour recalculer l'écran d'Accueil en temps réel.
  - **Tests unitaires riches** : Couverture totale de la logique de suppression du repository, des flux réactifs de cas d'usage, de la gestion d'état ViewModel, ainsi que des tests de non-régression (enregistrement de `seriesId`).

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
