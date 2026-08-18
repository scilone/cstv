# Journal des Modifications (Changelog) - CSTV IPTV

## À venir — non publiée

## [v1.88.8] - 2026-08-18
### ⚡ Optimisations de Performance, Fluidité et Stabilité (T25, T26, T27)
* **Optimisation du moteur de recommandations (T25)** :
  - **Projections Room légères** : Remplacement du chargement complet des tables de catalogue `vod_streams` (39 206 films) et `series_streams` (12 566 séries) par des projections SQL minimales (`RecommendableVodProjection` / `RecommendableSeriesProjection`) ciblant uniquement les colonnes requises pour le scoring, libérant le thread principal et évitant la saturation de `CursorWindow`.
  - **Pre-parsing paresseux (Lazy Loading CPU)** : Élimination de millions de micro-allocations mémoires et du parsing répétitif par Expressions Régulières (`Regex`) au sein de la boucle de scoring fermée. Le split et le nettoyage des genres et des acteurs se font de manière paresseuse une seule fois par candidat (`by lazy`), ramenant le calcul complet sous la barre des 500 ms (soit 95% de gain de performance).
* **Chargement différé (Lazy Loading) de la WebView des bandes-annonces (T26)** :
  - **Dwell Time Gate (Politique de temporisation de 1,5s)** : Introduction d'une politique de stabilité `TrailerPreviewDwellPolicy` qui retarde l'instanciation de la `WebView` lourde de YouTube. L'aperçu ne se charge que si l'utilisateur s'arrête de manière continue pendant 1,5 seconde sur une carte active du carrousel de l'Accueil, prévenant ainsi le blocage de 1,86s du thread UI constaté lors d'un défilement ou démarrage rapide.
  - **Nettoyage et Cycle de vie hermétiques** : Les gestes rapides de navigation ignorent ou annulent les WebView, et la sortie d'un élément de l'écran libère instantanément ses ressources en réinitialisant le contexte.
* **Stabilisation et skippabilité de la grille Live TV de plus de 3 000 flux (T27)** :
  - **Modèles et Collections UI stables (`@Immutable`)** : Introduction de `LiveStreamUiState` et de conteneurs de collections (`LiveStreamList`, `LiveCategoryList`, `FavoriteList`) explicitement immutables. Cela permet à Jetpack Compose d'ignorer la recomposition (skippability) des cellules dont l'EPG ou l'état de focus n'a pas changé.
  - **Clés uniques namespacées et virtualisation de rendu** : Ajout systématique de clés de rendu uniques et stables (ex. `stream_$streamId` ou `placeholder_$index` via `LiveTvGridKeyGenerator`) sur les grilles TV et mobiles, éliminant les lenteurs `LayoutNode` d'environ une seconde et garantissant un défilement ultra-fluide à 60 FPS sans scintillement ni perte de position de focus.

## [v1.87.0] - 2026-08-18
### ✨ Sélecteur de qualité des chaînes et mode automatique avec repli (F40)
* **Changement de qualité à la volée** : Bouton « Qualité » intégré directement dans la barre d'actions du lecteur de chaînes de télévision en direct. Il liste toutes les variantes disponibles d'une même chaîne (partageant la clé de liaison T21) et permet une bascule instantanée sans interruption inutile. Le bouton est automatiquement masqué s'il n'existe qu'une seule variante exploitable.
* **Mode automatique avec repli (opt-in)** : Un nouveau réglage dans les Paramètres permet de faire du mode automatique le comportement par défaut pour toutes les chaînes. Au zapping, le lecteur tente la meilleure qualité disponible ; en cas d'échec d'ouverture ou d'instabilité (5 coupures de buffering en moins de 120 s), il se replie automatiquement sur la qualité immédiatement inférieure.
* **Signalement discret** : Chaque repli de qualité est notifié à l'utilisateur de manière brève et discrète (« Qualité réduite pour stabiliser la lecture ») sans interrompre ou bloquer la lecture.
* **Aucune remontée oscillatoire** : Afin d'éviter des bascules de flux réseau intempestives et insidieuses, le lecteur conserve la qualité stable trouvée pendant toute la session de visionnage. La meilleure qualité est retentée de zéro lors du prochain zapping.
* **Priorité au contrôle utilisateur** : Une sélection manuelle de la qualité désactive d'office le mode automatique pour cette chaîne le temps de la session en cours.
* **Bascule sur le flux le « moins mauvais »** : En cas de pannes réseau sur tous les flux d'une même chaîne, l'application se fixe intelligemment sur le flux mesuré comme le moins mauvais (score calculé sur l'atteinte de l'état prêt, le buffering, le délai d'ouverture et la qualité) plutôt que d'abandonner l'utilisateur devant un écran noir.
* **Architecture robuste et découplée** : Intégration transparente et résiliente avec l'auto-réparation technique de lecture (T23) et mise en place de points d'extension propres et testables pour les tickets futurs F41 (timeshift) et F42 (catch-up).

## [v1.86.3] - 2026-08-17
### ✨ Étiquettes de version et sélecteur de versions dans le lecteur (F39)
* **Étiquettes dans les listes** : Affiche automatiquement des badges de langue et de qualité (ex. « VF · 4K ») sur les vignettes de films et de séries dans toutes les listes (Accueil, catalogues, recherche, favoris) pour faciliter le choix dès le premier coup d'œil.
* **Sélecteur de versions dans le lecteur** : Bouton « Version » intégré dans les barres d'actions des lecteurs de films et d'épisodes de séries, à côté des sélecteurs de pistes audio/sous-titres. Il permet de basculer instantanément d'une version à l'autre en conservant de façon transparente la position de lecture (seek borné).
* **Mémorisation de la préférence de série** : La version choisie pour une série est automatiquement enregistrée par profil utilisateur, assurant que les épisodes enchaînés (mode binge-watching) ou lancés ultérieurement s'ouvrent directement dans la version préférée.
* **Sélecteur de versions sur les fiches média (Évolution PO)** : Un bouton dédié « Versions » apparaît sur les fiches de détails des films et des séries possédant plusieurs alternatives de flux. Cliquer dessus ouvre une bottom sheet (modale sur TV) permettant d'actualiser entièrement la fiche avec les caractéristiques de la version choisie avant de lancer la lecture.
* **Résilience et robustesse transactionnelle** : Protection absolue contre les écrans noirs. En cas d'échec technique lors d'un changement de version (flux injoignable, erreur décodeur, etc.), le contrôleur effectue un rollback automatique et silencieux vers la version et la position d'origine avec affichage d'un message localisé clair.
* **Intégration d'architecture unifiée** : Coexistence harmonieuse avec le contrôleur d'auto-réparation technique de lecture (T23) en veillant à ce qu'un seul pilote de moteur soit actif à la fois.
* **Précision géométrique des badges** : Les étiquettes ne recouvrent pas le grand chiffre des rangs Top 10 mais se décalent proprement en haut à droite des vignettes pour une lisibilité impeccable.

## [v1.85.0] - 2026-08-16
### ♻️ Centralisation des appels TMDB dans le backend (T22)
* **Retrait de la clé API TMDB de l'application** : l'APK n'embarque plus de clé API externe et ne communique plus directement avec `api.themoviedb.org`. Tous les appels (tendances, populor, bandes-annonces, appariements) sont désormais centralisés, sécurisés et rationalisés par le backend CSTV via le préfixe `/v1/catalog`.
* **Identifiants canoniques opacifiés** : découplage total de l'application vis-à-vis du fournisseur de métadonnées. L'application manipule désormais uniquement un `canonicalId` sous forme de chaîne (`String`), s'abstenant de toute interprétation ou dépendance envers les identifiants numériques TMDB sous-jacents.
* **Cache serveur intelligent par paliers d'âge** : optimisation majeure de la rétention des informations sur le serveur PostgreSQL pour maximiser les performances et la fraîcheur. Les durées de vie de cache sont calculées dynamiquement selon l'âge réel de l'œuvre (7 jours pour les films/séries de moins d'un an, 30 jours de 1 à 4 ans inclus, 90 jours de 5 à 9 ans inclus, et 180 jours pour les œuvres de 10 ans et plus).
* **Résilience et protection concurrentielle (Anti-Stampede)** : intégration d'un mécanisme de verrou logique consultatif Postgres pour éviter que des requêtes concurrentes froides ne mitraillent TMDB. Support du mode dégradé *Stale-If-Error* pour servir une copie périmée si le fournisseur externe est en panne ou limité par quota.
* **Sécurité & Rate Limiting** : sécurisation systématique des routes d'enrichissement par middleware JWT et bridage strict des tentatives d'appariement (`/matches`) par compte et par adresse IP afin d'interdire tout abuse du serveur comme proxy.

## [v1.84.0] - 2026-08-16
### ♻️ Normalisation des titres, extraction des attributs et clé de liaison entre médias (T21)
* **Fondation données, aucun écran modifié** : chaque film, série et chaîne du cache Room porte désormais un titre nettoyé (`cleanTitle`), ses attributs extraits (langue/version, qualité) et une clé de liaison (`linkKey`) calculés une seule fois à la synchronisation — prépare le sélecteur de versions et les badges qualité des tickets suivants (F39, F40), sans aucun changement visible dans cette version.
* **`MediaTitleParser`** : nouveau composant pur qui détecte les marqueurs connus (`VF`, `VOSTFR`, `MULTI`, `4K`, `HD`…), quelle que soit leur position et leur casse, produit un titre nettoyé fidèle au libellé source et une clé de liaison canonique (SHA-256 tronqué) qui rapproche les entrées désignant la même œuvre (« Film X VF 1080p » / « Film X MULTI 4K ») sans jamais confondre deux œuvres d'années différentes.
* **Migration Room 28 → 29** : ajoute six colonnes et un index `linkKey` sur `vod_streams`, `series_streams` et `live_streams`, strictement structurelle (aucune ligne parcourue, démarrage non ralenti). Le catalogue déjà en cache est rattrapé par un `CatalogNormalizationWorker` en tâche de fond après le démarrage, repris par pages en cas d'interruption, sans appel réseau ni perte des favoris, positions de lecture ou téléchargements existants.
* **Appariement TMDB accéléré** : `TmdbCatalogMatcher` consomme désormais le titre nettoyé déjà stocké au lieu de le recalculer à chaque appel.

### 🐛 Correctifs de Bugs
* **HUD du lecteur VOD qui se refermait aussitôt sur pause (télécommande TV)** : sur Android TV, la touche OK ouvrait bien le HUD lors d'une mise en pause, mais son relâchement (`KeyUp`) tombait dans un `Modifier.clickable` resté câblé sur l'ancien geste tactile, qui rebasculait aussitôt `showControls` à `false` — le HUD se refermait donc instantanément au lieu de rester ouvert le temps de la pause. Remplacé par un `pointerInput`/`detectTapGestures` qui ne réagit qu'aux événements pointeur, à l'image du lecteur Live qui n'avait pas ce défaut.

## [v1.83.0] - 2026-08-14
### ✨ Écran de chargement du premier remplissage du catalogue (F38)
* **Écran de chargement dédié** : Affichage d'un écran de chargement plein écran indiquant l'étape en cours et la progression (de 1/6 à 6/6) entre l'authentification/sélection de profil et l'accès au catalogue.
* **Garantie anti-écrans vides** : Bloque totalement l'accès aux écrans de l'application tant que les six sections fondamentales du catalogue ne sont pas validées au moins une fois (les chaînes, les films et les séries, catégories + flux). L'enrichissement reste géré asynchronement en tâche de fond.
* **Gestion résiliente des erreurs** : Traitement transparent de la perte réseau temporaire avec reprise automatique au retour du réseau. Affichage de messages d'erreurs clairs et localisés en cas d'échec (authentification, panel, parsing, stockage, inconnu) proposant de réessayer ou de se déconnecter de la session IPTV.
* **Expérience utilisateur mobile et Android TV** : Design et parcours unifiés avec message d'alerte en cas d'attente prolongée au-delà de 30 secondes, et gestion robuste de la capture du focus initial au D-pad sur l'action primaire en cas d'erreur sur TV.

## [v1.82.0] - 2026-08-14
### ✨ Verrou de lecture simultanée par compte (F37)
* **Gestion coopérative et explicite** : Bloque le démarrage d'une nouvelle lecture en cas de conflit avec un autre appareil actif du foyer, en affichant un dialogue détaillant le nom de l'appareil occupant et la durée.
* **Prise de main volontaire (Takeover)** : Permet à l'utilisateur de couper proprement la lecture sur le premier appareil pour y substituer son flux en un clic.
* **Message de dépossession** : Présente un bandeau d'information clair à l'utilisateur dépossédé avec un bouton de reconquête rapide.
* **Heartbeat de maintien & fail-open** : Maintient le verrou actif par heartbeat toutes les 30s et libère automatiquement le verrou en cas d'inactivité ou de pause. En cas d'indisponibilité du backend, le système bascule en fail-open pour garantir la continuité d'accès au catalogue.

## [v1.81.0] - 2026-08-14
### ✨ Identifiants IPTV toujours locaux et sauvegarde CSTV explicite (F36)
* **Mémoire locale durable** : après une authentification Xtream réussie, les identifiants sont conservés localement dans le stockage chiffré, indépendamment du choix de sauvegarde cloud.
* **Sauvegarde CSTV volontaire et révocable** : la nouvelle case de connexion, visible uniquement avec un compte CSTV lié et décochée par défaut, sauvegarde une copie chiffrée par compte CSTV. La copie est restaurable silencieusement sur un nouvel appareil ; décochage et déconnexions demandent sa suppression sans confondre session IPTV locale et session CSTV.
* **Protection serveur** : les quatre champs Xtream sont chiffrés côté API avec XChaCha20-Poly1305 et une clé hors PostgreSQL ; aucune valeur en clair n'est enregistrée dans la table, les logs ou les réponses d'erreur.

## [v1.80.0] - 2026-08-14
### ♻️ Normalisation relationnelle de Room et allègement de la synchronisation cloud (T20)
* **Fin de la duplication de métadonnées dans les tables d'état** : favoris, reprises de lecture, historique Live, notes, préférences de piste, suivi de séries, préférences de catégorie et téléchargements ne stockent plus ni titre, ni jaquette, ni catégorie — uniquement une référence vers le catalogue et leurs valeurs métier propres. L'affichage résout toujours la métadonnée depuis le catalogue courant ; un média disparu ou inactif n'apparaît simplement dans aucune liste, sans carte incomplète.
* **Identités relationnelles (`media_refs`/`category_refs`)** : deux nouvelles tables portent de vraies clés étrangères Room vers les états utilisateur, cloisonnées par compte Xtream. Une identité survit à la disparition de son média/sa catégorie (état conservé, invisible) et redevient active si le média revient avec la même identité.
* **Catalogue Live/VOD/Séries en remplacement différentiel** : fin du cycle systématique purge-puis-réinsertion, remplacé par un upsert (`INSERT … ON CONFLICT DO UPDATE`) suivi d'une suppression ciblée des seuls éléments réellement disparus — l'EPG, les saisons et les épisodes déjà chargés ne sont plus détruits à chaque synchronisation.
* **Réconciliation applicative (`CatalogReconciler`)** : après un cycle catalogue complet et réussi, nettoyage rejouable des caches de bandes-annonces orphelines et des téléchargements dont le média source a disparu (fichier local et ligne Room), avec reprise automatique en cas d'échec partiel.
* **Format cloud v2, plus léger et versionné par namespace** : `favorites`, `playback` et `recently-watched-live` n'envoient plus la moindre métadonnée de catalogue (une reprise passe d'environ 200 à moins de 60 octets JSON) ; les quatre autres namespaces gardent leur format v1 inchangé. Un ancien document reçu est normalisé avant toute fusion, sans jamais réémettre les anciens champs.
* **Migration unique Room 27 → 28** : les données existantes (favoris, reprises, historique, notes, préférences) sont conservées et rattachées au nouveau modèle ; les lignes déjà orphelines sont supprimées ; un compactage automatique de la base peut s'exécuter au premier démarrage suivant la mise à jour.

## [v1.79.0] - 2026-08-13
### 🔒 Durcissement sécurité backend et hygiène de la synchronisation cloud (T14/T16/T17/T18/T19)
* **Quotas de compte (T14)** : un compte est désormais limité à 10 profils, 32 namespaces par profil et 20 Mio de stockage synchronisé, appliqués sous verrou pour rester corrects même en cas de créations simultanées.
* **Limitation de débit sur la vérification OTP (T16)** : `POST /v1/auth/otp/verify` est maintenant plafonné par adresse IP, comme l'était déjà la demande de code — empêche un flot de vérifications de saturer la base de données.
* **En-têtes de sécurité HTTP (T17)** : toutes les réponses de l'API (y compris les erreurs) portent désormais HSTS, `X-Content-Type-Options: nosniff` et `Referrer-Policy` ; les réponses de synchronisation ne sont plus mises en cache par un intermédiaire.
* **Validation d'entrée renforcée (T18)** : les champs validés par expression régulière (code OTP, identifiants, namespaces) rejettent maintenant un saut de ligne final au lieu de l'accepter silencieusement.
* **Hygiène des données synchronisées (T19)** : les listes synchronisées (favoris, reprises de lecture, chaînes récemment regardées) sont bornées et allégées avant chaque envoi au cloud, y compris après une fusion entre appareils — évite que la synchronisation ne pousse indéfiniment plus de données que nécessaire.

## [v1.78.0] - 2026-08-10
### ✨ Connexion automatique au profil au démarrage (F31)
* **Contournement du sélecteur de profils ("Qui regarde ?")** : Si un profil est désigné comme profil de démarrage automatique, l'application s'y connecte directement et ouvre l'Accueil au démarrage, évitant l'écran de sélection de profils.
* **Option dans l'édition de profil** : Une option d'activation/désactivation de connexion automatique est ajoutée dans le dialogue d'édition commun mobile/TV.
* **Unicité automatique** : Activer l'option sur un profil la désactive automatiquement sur tout autre profil.
* **Prise en charge de la robustesse** : La suppression du profil de démarrage automatique retire proprement le marquage pour éviter les crashs, les boucles de navigation ou l'activation de profils inexistants. Les tests JVM et le linter valident à 100 % cette fonctionnalité.

### 🎨 Refonte esthétique de l'écran des paramètres Android TV (F32)
* **Élimination des distractions** : Suppression du bouton "Retour" visuel (inutile sur TV grâce à la télécommande Back) et du bloc de téléchargements mobiles ("Téléchargements hors-ligne").
* **Boutons personnalisés TV unifiés** : Remplacement des boutons standards de la bibliothèque TV Material3 par des surfaces interactives uniformes, de rayon 8 dp avec un liseré lumineux au focus (en `AccentLavande` pour les réglages généraux et `RatingDislike` pour la déconnexion).
* **Hiérarchie visuelle préservée** : Utilisation d'aplats de couleur au repos pour différencier les actions : l'action principale de synchronisation arbore un fond lavande (`AccentLavande`) et l'action de déconnexion un fond rouge destructif (`RatingDislike`) au repos avec un focus blanc à fort contraste.

## [v1.77.0] - 2026-08-05
### ✨ Refonte de la fiche série Android TV (F29)
* **Présentation cinéma sur l'écran Hero** : À l'arrivée sur la fiche d'une série, affichage d'un visuel cinéma immersif (affiche grand format ou trailer dans la moitié gauche sous un fondu dégradé horizontal vers la droite, informations détaillées à droite). Le focus initial est sur le bouton principal, arrondi, sans icône, textuel (« LIRE LA SÉRIE » ou « REPRENDRE SXXEXX »).
* **Barre de progression directe** : Une barre fine de progression lavande sur piste sombre apparaît directement sous le bouton de lecture d'accueil Hero si l'épisode ciblé est entamé.
* **Sélecteur double couche (Dpad DOWN)** : Un appui Bas fait glisser verticalement l'écran de la hauteur d'un écran complet pour dévoiler le panneau des épisodes. Les saisons sont sous forme de gélules horizontales et les épisodes sont présentés de manière exhaustive (numéro, titre, résumé, vignette et progression).
* **Remontée animée des titres associés** : Le bloc des titres associés est masqué par défaut, visible en aperçu au dernier épisode, et remonte complètement pour prendre le focus horizontal sur une impulsion Bas depuis le dernier épisode.
* **Isolation et non-régression** : Le layout mobile et la fiche série mobile restent strictement inchangés et isolés du nouveau layout TV.

## [v1.76.6] - 2026-08-09
### 💄 Barre de navigation TV repliée resserrée à la largeur de l'avatar
* La barre latérale repliée passe de 68 à 60 dp : la largeur de l'avatar de profil (42 dp, plus 3 dp de réserve de chaque côté pour son anneau de focus) et 6 dp de marge latérale. C'est le plancher réel — descendre plus bas demanderait de rapetisser l'avatar lui-même. La marge de la barre reste à 10 dp une fois dépliée.
* Les icônes de destination sont centrées quand la barre est repliée : à 48 dp de largeur utile, leur retrait latéral de 10 dp les poussait contre le bord gauche au lieu de les aligner sous l'avatar.

## [v1.76.5] - 2026-08-09
### 🐛 Fiche film TV : la descente depuis « reprendre la lecture » sautait « relire depuis le début »
* Sur un film déjà commencé, descendre depuis « REPRENDRE LA LECTURE » ouvrait le bloc « Titres associés » au lieu de passer sur « RELIRE DEPUIS LE DÉBUT » — la remontée, elle, revenait correctement sur ce dernier. La descente explicite vers la première affiche (`tvFocusDownTo`, introduite en v1.76.1 pour empêcher le focus d'atterrir au milieu de la rangée) était posée sur la **colonne** des boutons : elle interceptait donc la touche bas quel que soit le bouton focalisé. Elle est désormais portée par le dernier bouton de la colonne, le seul sous lequel il n'y a plus rien.

## [v1.76.4] - 2026-08-09
### ⚡ Premier catalogue ouvert : le moteur de recommandation affamait la navigation
* **Cause identifiée** : le rapport de diagnostic v1.76.3 est sans ambiguïté. L'ouverture de la base prend 20 ms — ce n'était donc ni elle ni le journal SQLite. En revanche, pendant les seize secondes où le moteur de recommandation tourne au lancement, **tout** ralentit dans les mêmes proportions : les catégories du premier catalogue ouvert mettent 13,0 s à s'afficher (contre 75 ms une fois le calcul fini), et `syncIfStale`, qui ne fait que lire trois lignes, passe de 9 ms à 8,0 s. Ce n'est pas une requête lente, c'est une famine de temps processeur.
* **Un fil dédié, de priorité minimale** : le moteur lit tout le catalogue (3 929 films, 2 268 séries), construit un profil de goûts puis note chaque titre. Il s'exécutait sur `Dispatchers.Default`, qui lui donnait autant de fils que l'appareil a de cœurs — quatre ici — et il les prenait tous. Il tourne désormais sur un fil unique en `MIN_PRIORITY` : trois cœurs restent à la navigation, et l'ordonnanceur fait céder le moteur devant les fils d'interface. Les recommandations arrivent un peu plus tard sur l'Accueil ; c'est une garniture, pas un contenu qu'on attend.
* **Plus de calcul jeté puis refait** : `HomeViewModel.refreshRecommendations` annule le travail en cours avant d'en relancer un. Au lancement, deux déclencheurs se suivent de près — les traces montraient deux « Calculating recommendations » pour un seul résultat, soit treize secondes de calcul perdues. Le calcul vit maintenant dans la portée applicative et survit à l'annulation de son appelant : un second appel rejoint celui qui court au lieu d'en ouvrir un autre.
* **Mesure** : durée totale du calcul et durée de la lecture du catalogue sont tracées séparément.

## [v1.76.3] - 2026-08-09
### ⚡ Premier catalogue ouvert : journal WAL, ouverture anticipée et mesure fine
* **Journal WAL explicite** : la base était construite avec le mode `AUTOMATIC` par défaut, qui retombe sur le journal TRUNCATE dès que le système déclare l'appareil « low RAM » — le cas des téléviseurs d'entrée de gamme, dont le tas applicatif plafonne à 192 Mo. Avec TRUNCATE, une écriture prend le verrou de la base et bloque **toutes** les lectures le temps de sa transaction : un enrichissement de métadonnées ou une insertion de milliers de lignes gèle les requêtes qui alimentent l'écran, sans qu'aucune trace ne l'indique. WAL laisse lecteurs et écrivain avancer de front.
* **Ouverture de la base hors du chemin critique** : le premier accès à Room paie l'ouverture du fichier, la vérification d'empreinte du schéma (vingt tables), l'installation des déclencheurs d'invalidation et, le cas échéant, la reprise d'un journal laissé par une session interrompue. Ce coût est désormais payé au démarrage, pendant que l'Accueil s'affiche depuis ses caches, plutôt que par le premier catalogue ouvert. Sa durée est tracée.
* **Mesure fine des catégories VOD** : la trace globale (12,6 s de première émission sur un téléviseur, contre 70 ms pour Séries et Live ouverts juste après) ne disait pas qui attendait. La requête Room et la lecture des préférences de catégories sont maintenant chronométrées séparément.

## [v1.76.2] - 2026-08-09
### 🐛 Fiche film Android TV : le défilement automatique revenait après un aller-retour (F30)
* **Titres associés qui déroulent seuls à la réouverture du bloc** : corrigé en v1.76.1 pour la première ouverture, le défaut réapparaissait dès qu'on avait navigué vers la droite puis quitté le bloc. `LazyListState` conserve sa position de défilement : la rangée rouvrait donc défilée, sa première vignette n'était plus composée, le `requestFocus` de `tvFocusDownTo` échouait silencieusement — son échec est muet par conception, pour qu'aucun appui ne reste sans effet — et Compose reprenait sa recherche géométrique, qui vise de nouveau le milieu de l'écran. L'état de la rangée est hoissé (`RelatedTitlesRow` accepte un `state`) et ramené à l'index 0 dès que le focus quitte le bloc.

## [v1.76.1] - 2026-08-09
### 🐛 Fiche film Android TV : navigation des titres associés et lisibilité du résumé (F30)
* **Défilement de six affiches à chaque ouverture du bloc « Titres associés »** : le bouton de lecture occupe toute la largeur de la colonne, son centre tombe donc vers 72 % de l'écran. La recherche de focus par défaut retenant le candidat géométriquement le plus proche, la descente atterrissait sur la sixième vignette — que le pivot ramenait ensuite à l'ancre de début, d'où un défilement systématique et une première affiche à retrouver en allant à gauche. La descente est désormais explicite (`tvFocusDownTo` vers un `FocusRequester` posé sur la première vignette), le dispositif déjà écrit pour ce cas en B22.
* **Pression vers la droite en fin de rangée** : aucune vignette à droite de la dernière, la recherche de focus sortait donc de la rangée pour la cible la plus proche ailleurs dans l'arbre — les étiquettes de crédits, posées plus haut à droite. Le focus quittant le bloc, celui-ci redescendait tandis que le cadre restait affiché en l'air. `focusProperties { right = FocusRequester.Cancel }` sur la dernière vignette (et `left` sur la première) fait de la butée un non-événement, et la sortie de focus efface le cadre.
* **Résumé lisible par défaut** : la pondération du synopsis ne lui laissait qu'une ligne, faute de place restante. De la hauteur lui est rendue en amont — titre de 38 à 30 sp, réserve haute de 36 à 20 dp, débord de la rangée de 110 à 96 dp, marges internes resserrées.
* **« Voir plus » discret** : plus de pastille bordée, mais un simple texte souligné en `TextSecondary` qui passe en `AccentLavande` au focus, dans le fil du résumé.

## [v1.76.0] - 2026-08-09
### ✨ Fiche film Android TV : retours PO sur la refonte (F30)
* **Synopsis qui ne chasse plus les boutons** : le texte passe en `weight(1f, fill = false)` dans la colonne de droite. Un `Column` mesurant ses enfants non pondérés en premier, le synopsis n'obtient que la place restante et se tronque : crédits, actions et boutons de lecture restent visibles quelle que soit la longueur du résumé. S'il est effectivement tronqué (`hasVisualOverflow`), une action **« VOIR PLUS »** apparaît et ouvre le texte intégral dans une fenêtre plein écran — un dépliement en place aurait déplacé le contenu sous le focus, ce que F28 refusait précisément.
* **Boutons de lecture au dessin mobile et pleine largeur** : rayon 8 dp au lieu de la pilule, texte blanc, fond `AccentLavande` pour l'action principale et `Surface3` pour la secondaire, sur toute la largeur de la colonne (ils s'arrêtaient à 360 dp). Comme sur mobile, une position de reprise fait apparaître « REPRENDRE LA LECTURE » puis « RELIRE DEPUIS LE DÉBUT ».
* **Trailer à la place de l'affiche** : le trailer YouTube ne prend plus le fond de toute la page mais l'emplacement de l'affiche, dans le panneau gauche, sous le même fondu horizontal vers `Surface1`. Le scrim de 0,62 disparaît : la colonne de texte ne passe plus par-dessus la vidéo.
* **Sélecteur fixe sur les titres associés** : la rangée adopte le dispositif TV commun au reste de l'application (F19/F23/B22) — le cadre se pose sur l'emplacement de la première vignette et n'en bouge plus, c'est la rangée qui défile dessous. `RelatedTitlesRow` gagne un `tvPivotEnabled` opt-in ; mobile et fiche série gardent leur anneau par vignette. La cohabitation avec la remontée de F28 demandait un soin particulier : l'overlay dessine un cadre à une position publiée alors que la remontée translate la colonne par `graphicsLayer`, si bien que le cadre serait resté à sa place d'avant. La fiche republie donc la géométrie de la vignette focalisée à chaque valeur de l'animation, à partir de `positionInRoot()` et de la taille — jamais de `boundsInRoot()`, qui clippe par les parents et rendrait un rectangle vide pour une vignette encore hors champ.
* **Marges de la rangée** : 48 dp de retrait latéral, la rangée touchait les bords de l'écran.

## [v1.75.0] - 2026-08-09
### ✨ Refonte de la fiche film Android TV (F28)
* **Présentation cinéma pleine page** : `VodDetailsScreen.TvLayoutDetails` — une simple transposition de la fiche mobile (affiche de 220 dp, boutons empilés, cadre **jaune** au focus hors charte) — est remplacé par un fichier dédié `presentation/vod/VodDetailsTvLayout.kt`. L'affiche occupe la moitié gauche de l'écran, plein bord, fondue horizontalement vers `Surface1` ; la colonne de droite porte le titre en 38 sp, la ligne de métadonnées (année, genres, durée, note), le synopsis tronqué à six lignes, les étiquettes cliquables réalisateur/acteurs (inchangées, simplement passées en `internal` pour être partagées), une rangée d'actions icône + libellé séparées par des filets, puis le bouton de lecture en pilule pleine largeur **sans icône**. Le chemin mobile et la fiche série ne sont pas touchés.
* **Rangée « Titres associés » en débord et remontée exacte** : la fiche TV n'est pas défilante. Le bloc principal mesure `hauteur d'écran − 110 dp`, si bien que la rangée dépasse en bas de l'écran comme sur la maquette. Quand le focus y descend, la colonne entière est décalée par `graphicsLayer { translationY }` de la distance **exacte** manquante (`tvDetailsRelatedShiftPx`), calculée sur des hauteurs réellement mesurées via `onSizeChanged`. Pas de conteneur défilant, donc aucun `bringIntoView` implicite à combattre (leçon de B22) et une position de repos toujours strictement nulle.
* **Focus initial sur le bouton de lecture** : les étiquettes de crédits étant composées avant les actions, le focus est demandé explicitement via `rememberTvInitialFocus` / `tvInitialFocusTarget`. Un appui sur OK à l'ouverture lance donc le film.
* **Charte respectée** : plus aucun `Color.Yellow` sur le chemin TV ; le focus est marqué en `AccentLavande`, les filets et séparateurs sont des variantes d'opacité de `TextSecondary`, et le texte du bouton au focus passe sur `Surface1`.
### 🐛 Correctifs issus de la review F28
* **Rangée associée écrasée et remontée inopérante** : un `Column` mesure ses enfants non pondérés avec l'espace restant en hauteur maximale. Le bloc principal occupant `écran − 110 dp`, la rangée « Titres associés » se voyait plafonnée à 110 dp : vignettes clippées en plein milieu, hauteur mesurée fausse, et remontée réduite à la seule réserve basse (24 dp au lieu de ~114 dp). La colonne décalable passe en `wrapContentHeight(align = Alignment.Top, unbounded = true)`.
* **Bouton de lecture aux couleurs de la bibliothèque, pas de la charte** : `androidx.tv.material3.Button` compose sa propre `Surface`, qui recouvrait le fond posé par modificateur et se colorait avec le jeu par défaut de tv-material3 — l'application n'installant aucun `androidx.tv.material3.MaterialTheme`, et la dépendance étant en `1.0.0-alpha10`. Le bouton est reconstruit sur `Box` + `clickable`, le motif déjà employé par `CreditNameChip` et `RelatedTitleCard`.
* **Erreur de notation muette puis bloquante sur TV** : le `SnackbarHost` avait été laissé dans la seule branche mobile alors que l'effet qui l'alimente est commun. Sur TV, `showSnackbar` restait suspendu indéfiniment et `onConsumeRatingError()` n'était jamais appelé, si bien qu'aucune erreur de notation ne s'affichait ni ne se purgeait de toute la session. L'hôte est remonté dans un `Box` racine commun aux deux plateformes.
* **Paramètre `isRatingSaving` neutralisé** : le `remember(isRatingSaving) {}` posé pour taire un avertissement du compilateur est supprimé ; le paramètre est propagé à la fiche TV et désactive réellement les actions « j'aime / je n'aime pas » pendant l'enregistrement.
* **Nettoyage** : `mutableFloatStateOf` à la place de `mutableStateOf(0f)` pour les hauteurs mesurées (autoboxing), treize imports morts et un `@OptIn` inutile retirés de `VodDetailsScreen.kt`, réserve basse extraite en constante nommée, libellés « Réalisateur »/« Acteurs » et libellés de lecture du chemin mobile externalisés dans `strings.xml`, description d'accessibilité rétablie sur les actions de notation.

> Validation automatisée : `testDebugUnitTest` (dont `VodDetailsTvLayoutTest`, 6 cas couvrant le calcul de remontée), `lintDebug` et `assembleDebug` au vert, sans avertissement sur les fichiers de la feature.

## [v1.73.1] - 2026-08-06
### 🐛 Correctifs d'ergonomie Android TV
* **Popin « Retirer de Continuer à regarder » qui se refermait seule (accueil TV)** : `ActivationKeyGate` n'apparie plus qu'une pression **neuve** (`repeatCount == 0`). La fenêtre s'ouvrant alors que la touche centrale est toujours enfoncée, Android continuait d'y livrer les KeyDown de répétition de cette même pression ; comptés comme un appui légitime, ils rendaient le KeyUp de relâchement indiscernable d'un clic sur « Annuler ». Les répétitions héritées sont désormais consommées, et `tvLongPressActions` réarme son drapeau à chaque nouvelle pression pour ne plus avaler le clic suivant.
* **Sélecteur pivot figé en descendant d'une rangée (grille de catégorie TV)** : ajout de `isPivotBlocked`, qui détecte la butée **avant** d'animer. Les premières rangées d'un catalogue ne peuvent pas être amenées au pivot 50 % ; `animateScrollBy` s'y déroulait alors entièrement sans rien déplacer, et la couche avant du focus (F23) ne publiait qu'à son terme — d'où un cadre resté plusieurs centaines de millisecondes sur la vignette précédente.
* **Retrait de l'aperçu vidéo des chaînes (F25)** : suppression complète de `LiveChannelPreview` et de son câblage (rangées du mode « Tout » et grille de catégorie). Le rendu de la miniature vidéo dans la carte n'était pas satisfaisant et le démarrage du décodeur pesait sur la fluidité de la navigation au D-pad. Le favori en appui long (F26) et l'étoile décorative non focalisable sont conservés.
* **En-tête Live TV allégé (TV)** : suppression du champ de recherche affiché en catégorie filtrée — il captait le focus à l'arrivée et faisait doublon avec la recherche globale, comme sur Films et Séries — et du bouton « Rafraîchir » voisin du sélecteur de catégorie, le rafraîchissement manuel vivant dans les Paramètres depuis la Phase 56.
* **Bouton retour retiré en mode TV** : la recherche globale, la fiche film et la fiche série n'affichent plus de bouton retour ; la touche Retour de la télécommande remplit ce rôle. La vue « Voir tout » de la recherche se referme désormais via un `BackHandler` dédié.
* **Recherche globale TV alignée sur les catalogues** : réserve haute réduite (T12) au lieu d'un demi-viewport — la liste démarre en tête d'écran et non au milieu — et adoption de la couche avant du sélecteur pivot fixe (F23), si bien que le marquage de focus y est identique à celui de Live TV, Films et Séries.
### ✨ Ajustement hauteur et interlignes des cartes Live TV (F26) & assainissement D-Pad (F25)
* **Assainissement du D-Pad (F25)** : remplacement de l'étoile interactive de favori (cible D-pad secondaire ambiguë) par un indicateur décoratif purement visuel non focalisable (`focusProperties { canFocus = false }`).
* **Favori en appui long (F25)** : câblage de l'action de favori sur l'appui long de la carte entière (maintien du bouton de validation central de la télécommande) via le modificateur générique `tvLongPressActions` (extrait de l'historique), réutilisant la sécurité d'appariement clavier B20.
* **Exclusion de l'historique (F25-R2)** : la rangée « Récemment regardées » reste explicitement exclue du contrat d'appui long de F25 pour préserver son action de retrait d'historique préexistante.
* **Ajustement de la hauteur et des interlignes EPG sur les cartes Live TV (F26)** :
  - **Ajustement géométrique & Fin de troncature** : Augmentation de la hauteur des cartes de chaînes TV de 84 dp à 92 dp combinée à un resserrement des marges internes (Spacer à 3 dp, marge horaire à 1 dp) et à l'application d'interlignes explicites (`lineHeight` fixés à 16 sp, 13 sp et 11 sp) pour immuniser l'affichage contre les forts grossissements d'accessibilité (`fontScale` jusqu'à ~1,5).
  - **Alignement avec la carte « Voir tout »** : Partage de la hauteur via la constante globale `LIVE_TV_CARD_HEIGHT = 92.dp` appliquée de manière uniforme à `StreamTvCard` et `SeeAllCard` pour prévenir tout désalignement horizontal.

## [v1.73.0] - 2026-08-05
### ✨ Appariement D-Pad (B20), Réserve (T12), Sélecteur Live TV (F24), Masquage recherche (F27) et Croix d'effacement (B21)
* **Appariement D-Pad robuste pour dialogue de confirmation d'historique (B20)** :
  - **Filtre d'activation orpheline (`ActivationKeyGate`)** : Résolution du problème de fermeture intempestive instantanée du dialogue de confirmation de retrait de l'historique lors d'un appui long TV. Introduction d'une barrière d'événements clavier exigeant d'avoir observé la touche enfoncée (`KeyDown`) au sein du dialogue avant d'autoriser une touche relâchée (`KeyUp`) à déclencher des actions interactives de type clic.
  - **Correctif d'état de perte de focus** : Réinitialisation propre du drapeau `consumeKeyUp` lors de la perte de focus de la carte de média. Cela évite d'avaler un clic légitime ultérieur si la carte is de nouveau focalisée après la fermeture du dialogue.
  - **Couverture par tests unitaires** : Validation complète via `ActivationKeyGateTest` couvrant l'absorption de KeyUp orphelin, la non-absorption de séquences KeyDown-KeyUp appariées, et la persistance de l'absorption après plusieurs événements orphelins.
* **Réserve de défilement vertical TV et détection de butée (T12)** :
  - **Réserve haute optimisée** : Remplacement de l'espace vide de 50 % du viewport par une réserve fixe et élégante de 24 dp en haut des écrans de liste (Live TV, Films, Séries, Favoris). Cela élimine l'important vide visuel initial, plaçant la première rangée de médias juste sous l'en-tête de catégorie.
  - **Détection de butée (`isPivotClamped`)** : Intégration d'un mécanisme de convergence asymétrique. Si la liste bute sur son sommet (offset 0), l'écart restant est considéré comme stable, forçant la publication géométrique correcte pour le cadre de focus de surimpression (F23) sur la première rangée.
  - **Couverture par tests unitaires** : Enrichissement de `TvPivotScrollTest.kt` avec des tests unitaires dédiés validant le calcul et la détection de butée (`isPivotClamped`) avec écart nul, résiduel avec consommation nulle ou partielle.
* **Sélecteur de catégories standardisé pour le Live TV sur TV (F24)** :
  - **Sélecteur et dialogue standardisés** : Remplacement de la ligne horizontale de puces de catégories défilantes sur Android TV par un déclencheur stable (`TvCategorySelectorTrigger`) ouvrant un dialogue de sélection plein écran (`TvCategoryPickerDialog`). Cela aligne l'ergonomie de l'écran Live TV avec celle des écrans Films (VOD) et Séries, améliorant la navigation au D-pad.
  - **Suppression de composants morts** : Suppression du composant `CategoryFilterChip` de `LiveTvComponents.kt` devenu inutilisé.
* **Masquage de la recherche textuelle locale par catégorie sur TV (F27)** :
  - **Suppression du champ de recherche d'en-tête** : Retrait complet de la barre de recherche textuelle locale `OutlinedTextField` dans l'en-tête de catégorie spécifique sur TV pour les Films (VOD) et les Séries. La saisie à la télécommande étant ardue, cette suppression épure le bandeau supérieur pour ne conserver que le déclencheur de sélection de catégorie et le bouton de filtres avancés, évitant l'encombrement et la double saisie avec la recherche globale (le mobile conserve pleinement son fonctionnement).
* **Masquage de la croix d'effacement inatteignable sur TV (B21)** :
  - **Croix invisible et non focusable** : Désactivation du `trailingIcon` (passé à `null`) dans le champ de recherche globale de l'écran Recherche si l'application tourne en mode TV. La croix étant inatteignable à la télécommande, sa suppression évite la confusion de présenter un bouton non interactif (le mobile conserve la croix tactile).

> Validation automatisée : `testDebugUnitTest` (tout au vert), `./gradlew lintDebug` et compilation réussies avec succès.

## [v1.69.0] - 2026-08-02
### ✨ Sélecteur de catégories TV et filtres avancés (F22) et Focus initial D-pad robuste (B17)
* **Sélecteur TV plein écran et filtres avancés sur Films/Séries (F22)** :
  - **Dialogue plein écran focusable (M3)** : Remplacement de `DropdownMenu` par un `Dialog` plein écran (`DialogProperties(usePlatformDefaultWidth=false)`) exploitant le même patron que `ProfileSelectionScreen`. Focus demandé sur l'entrée sélectionnée à l'ouverture ; anneau `AccentLavande` 3dp sur le déclencheur et chaque ligne.
  - **Composant partagé `ActiveFilterChipsRow` (M1)** : Extraction complète du composant de `SearchScreen.kt`, suppression de la duplication. Paramètres optionnels (`availableCategories`, `onRemoveMediaType`, `onRemoveCategory`) permettent l'utilisation tant en Recherche globale qu'en filtres de catégories VOD/Séries.
  - **États vides et chaînes i18n (m1, m2)** : Distinction des états vides (catégorie réellement vide vs. recherche texte vs. filtre trop restrictif). 11 entrées `strings.xml` remplacent les littéraux (`vod_category_selector_label`, `series_category_selector_label`, `catalog_no_search_result`, états vides, etc.). Libellé du déclencheur capitalisé (« FILMS : TOUT »).
  - **Indentation et couverture tests (m3, m4)** : Réindentatation des blocs `if` de `AdvancedSearchSheet`. Ajout de 2 cas de test manquants dans `CatalogFilterMatcherTest` (`ratingAboveThresholdIsIncludedAndBelowIsExcluded`, `emptyGenreSetMeansNoFilter`).
* **Focus initial D-pad robuste et sans vol de focus (B17)** :
  - **Découplage du flicker Paging (C1)** : `rememberTvInitialFocus` : `targetKey` découplé du flicker transitoire de `pagedStreams.itemCount` (qui retombe à 0 chaque frappe recherche/filtre). VOD/Séries/Live TV utilisent un compte stable dérivé de `state.streams` au lieu de `LazyPagingItems`. `ready` lu en direct via `snapshotFlow` sans redémarrage de l'effet — le focus ne vole plus pendant la frappe de l'utilisateur.
  - **Câblage complet Accueil (M1)** : `HomeScreen` propage `homeInitialFocus` via `HomeInitialFocusTarget.of(state, isTv)` à la Hero et aux 9 premières lignes (Reprise, Favoris, TV, Films, Top Films, Recommandés Films, Séries, Top Séries, Recommandés Séries). Bug incident : branche `DOWNLOADS` supprimée (rangée n'existe que mobile, jamais focusable sur TV).
  - **Respect du scroll restauré (M4)** : VOD/Séries/Live TV : focus initial bypassé si `getScroll(key) != (0, 0)` — scroll restauré prime sur focus forcé en index 0. Accueil reste hors périmètre (documenté).
  - **Tests JVM et core robuste (M2, M3)** : 6 cas manquants ajoutés à `HomeInitialFocusTargetTest` (priorité Hero, repli RESUME/FAVORITES/LIVETV, rangée tardive seule, état vide). Logique de tentatives extraite en fonction pure `runInitialFocusAttempts` (4 cas JVM : arrêt dès acquisition, épuisement des 10 tentatives, attente de `ready`, aucune si déjà acquis). Constantes `INITIAL_FOCUS_ATTEMPTS`/`INITIAL_FOCUS_RETRY_MS` promues `internal` pour testabilité.
  - **Variante no-op (m2)** : Surcharge `Modifier.tvInitialFocusTarget(state?, active: Boolean)` élimine la duplication du `.then(if (…) … else Modifier)` ×6 sites.

> Validation automatisée : `testDebugUnitTest` (81 suites, 615 tests, 0 échec), `assembleDebug`, `lintDebug` tous verts.

## [v1.68.0] - 2026-08-02
### ✨ Sélecteur TV à double couche (F23), Unification des cartes (B18) et Limitation des rangées (T10)
* **Double couche de navigation TV à sélecteur pivot fixe (F23)** :
  - **Couche avant parfaitement statique (`TvFocusSelector.kt`)** : Introduction d'un cadre de focus global, dessiné dans une surimpression (overlay) non focusable et non cliquable à la racine de l'écran. Lors de la navigation, le cadre reste parfaitement immobile, et les cartes d'arrière-plan glissent sous lui de manière fluide et amortie.
  - **Publication à la convergence du pivot** : Les extensions de défilement de `TvPivotScroll.kt` publient les coordonnées géométriques de la cible uniquement lorsque la convergence sur l'axe du pivot est stabilisée. Les cartes n'affichent plus individuellement leur cadre au moment de l'acquisition de focus Compose, supprimant définitivement l'effet de "saut" ou de "rebond".
  - **Coordination multi-axes temporelle** : Pour les rangées horizontales (mouvement en X) situées dans une liste verticale (mouvement en Y), un mécanisme d'arbitrage de publication (`reportAxisStabilised`) attend que les deux mouvements soient convergés dans une fenêtre de 2 frames Compose avant de mettre à jour le sélecteur, empêchant tout tracé transitoire inesthétique.
  - **Mesure géométrique précise** : Le sélecteur mesure précisément les dimensions du descendant Compose réellement focalisé (`onFocusedBoundsChanged`), prenant en compte les marges et formes spécifiques (comme les cartes Top 10 ou les tuiles de Live TV à coins arrondis).
  - **Superposition de la Hero Card** : La Hero Card sur l'Accueil publie ses coordonnées réelles de l'affiche clippée immédiatement après sa prise de focus (nœud immobile), permettant d'entourer l'affiche d'un anneau de 16.dp sans inclure les gouttières et paddings du pager.
  - **Ressort de transition amorti** : Toutes les dimensions, positions et rayons du sélecteur partagent une même spécification d'animation amortie (`spring(dampingRatio = DampingRatioNoBouncy, stiffness = StiffnessMediumLow)`), sans aucun rebond visuel.
  - **Repli et masquage sûrs** : Le cadre est automatiquement masqué lors de la perte de focus de la section média (navigation vers le rail latéral, un dialogue ou un contrôle d'action) et ne laisse aucun cadre orphelin.
  - **Tests unitaires dédiés** : Ajout de la classe `TvFocusSelectorStateTest` vérifiant la stabilité de l'état, l'invisibilité par défaut, le masquage et la conversion géométrique de repères d'hôte décalés (largeur de rail, insets de statut, etc.) via la fonction pure `localBounds`.
* **Unification esthétique des cartes de Films et Séries (B18)** :
  - **Carte unique réutilisable (`HomeCards.kt`)** : Unification complète du rendu visuel des cartes médias sous `HomeVodMovieCard` et `HomeSeriesShowCard`. Suppression définitive des anciennes cartes personnalisées `MovieTvCard` et `SeriesTvCard` ainsi que des grilles codées en dur avec titres textuels inesthétiques en dessous.
  - **Régime adaptatif par paramètre (`fillCell`)** : Ajout du mode `fillCell = true` pour les grilles verticales de catégories (mobile et TV). La carte s'adapte automatiquement à la largeur imposée par les colonnes et déduit dynamiquement sa hauteur selon le ratio premium 2:3, sans déformation d'affiche ni gouttières asymétriques.
  - **Badge de progression de reprise de lecture** : Remplacement du titre textuel TV pour la rangée de reprise de lecture par un badge d'overlay esthétique « S01 E03 » en haut à gauche de la vignette (obtenu de façon réactive via le mapping pure `EpisodeLabel.buildResumeLabels` couvert par tests), préservant l'homogénéité visuelle absolue (aucune écriture sous l'affiche) tout en conservant l'information cruciale d'avancement.
  - **Nettoyage exhaustif des imports** : Élimination complète de tous les composants de mise en page et imports obsolètes au sein de `VodScreen.kt` et `SeriesScreen.kt` (`AsyncImage`, `ContentScale`, `TextOverflow`, etc.), validée par un lint vert de 0 erreur.
* **Limitation des lignes horizontales du mode Tout à 250 éléments (T10)** :
  - **Optimisation dans le remember du groupBy** : Limitation stricte du nombre de médias transmis aux rangées horizontales du mode "Tout" à un maximum de 250 éléments. Le calcul est effectué dans le `remember(filteredStreams)` du partitionnement pour éviter toute réallocation de liste à chaque recomposition d'item de la `LazyRow`.
  - **Garantie de fluidité et de focus D-pad** : Allègement considérable du coût d'acquisition de focus et du parcours de défilement horizontal, tout en conservant l'accès au catalogue exhaustif via les grilles paginées complètes au clic sur "Voir tout" ou la sélection de catégorie.
  - **Préservation des rangées bornées** : Les lignes de Favoris et de reprise de lecture ("Continuer à regarder") restent volontairement en dehors de ce plafond, car elles sont structurellement limitées par l'activité du profil utilisateur.

## [v1.67.0] - 2026-08-02
### ✨ Notification de nouveaux épisodes (F12) et Retrait téléchargement TV (F21)
* **Notification de nouveaux épisodes de séries suivies (F12)** :
  - **Alerte locale intelligente** : Déclenchement d'une notification système sur mobile dès que de nouveaux épisodes d'une série terminée par l'utilisateur sont détectés sur le serveur IPTV.
  - **Détection réseau optimisée d'arrière-plan** : Détection intégrée au sein de `DatabaseSyncWorker` en post-traitement du catalogue, restreinte de façon performante aux séries favorites ou présentes dans l'historique "Continuer à regarder" du profil actif.
  - **Schéma Room versionné (v22)** : Ajout de la table physique `series_watch_states` via l'entité `SeriesWatchStateEntity` et `SeriesWatchStateDao` pour mémoriser de manière pérenne et par profil le dernier état d'épisodes visionné/notifié (Migration 21 → 22 non destructive).
  - **Deep-linking direct** : Un tap sur la notification ouvre l'application en naviguant automatiquement vers la fiche détaillée de la série concernée.
  - **Tests JVM exhaustifs** : Couverture complète de la détection, de l'orchestration multi-profils, des gardes de notification et des routes de redirection via `NewEpisodeDetectorTest`, `DetectNewEpisodesUseCaseTest`, `SeriesDeepLinkTest`, `DatabaseSyncWorkerTest`, et `AndroidNewEpisodeNotifierTest`.
* **Retrait des fonctionnalités de téléchargement sur Android TV (F21)** :
  - **Épuration visuelle TV** : Masquage complet de toutes les portes d'entrée de téléchargements sur Android TV (`isTv == true`) afin de libérer de la place et de simplifier l'interface sur les téléviseurs (qui sont connectés en permanence).
  - **Contrôles visés** : Retrait de la section `"home_downloads"` sur l'Accueil, du bouton `"DownloadActionButton"` sur la fiche Film, et du composant `"EpisodeDownloadControl"` sur chaque ligne de la liste d'épisodes de la fiche Série.
  - **Préservation mobile** : Maintien de l'intégralité des fonctionnalités, boutons et sections de téléchargement sur mobile à 100 %.
* **Correctif des barres système blanches au démarrage sur Mobile (B19)** :
  - **Thème sombre unifié à la racine** : Application de `IptvXtreamTheme` englobant l'intégralité du `setContent` de `MainActivity.kt` dès le premier démarrage pour éliminer la surface blanche par défaut sous-jacente.
  - **Rapprochement du fond edge-to-edge** : Correction de l'ordre d'application de `safeDrawingPadding()` sur `SplashScreen`, `ProfileSelectionScreen` et `ProfileManagementScreen` afin que le fond sombre soit peint sur l'intégralité de la hauteur physique de l'écran sous les barres système transparentes.
  - **Respect d'Android TV** : Maintien intact du comportement d'origine sur TV et sur les routes de lecteurs plein écran immersifs.

## [v1.66.0] - 2026-08-01
### ✨ Navigation TV à sélecteur fixe (Fixed Focus Scrolling - F19)
* **Défilement à sélecteur fixe (Fixed Focus)** :
  - Alignement horizontal à 15 % du bord gauche pour toutes les rangées de contenus (Accueil, TV en Direct, VOD, Séries, Favoris, Résultats de recherche). Le contenu défile sous le sélecteur, réduisant la fatigue oculaire.
  - Réalignement vertical automatique de la rangée focalisée au centre de l'écran (50 % de la hauteur).
  - Transition fluide et continue via `animateScrollToPivot` de manière asynchrone, neutralisant les saccades visuelles ou double défilement par défaut de Compose.
* **Butée naturelle aux bornes** :
  - Respect du pivot tant que le défilement le permet. Aux extrémités (début et fin de liste), la liste bute sur ses bornes naturelles et le sélecteur se déplace sans perte de focus ni débordement visuel, sans ajouter d'espaces blancs inutiles.
* **Résolution de Layout et SnapshotFlow asynchrone** :
  - Le pivot vertical intègre un callback d'observation dynamique réactif via `snapshotFlow` avec un timeout de 200 ms. Cela garantit le recentrage de la section focalisée même si celle-ci n'est pas encore présente dans la photographie instantanée du layout de Compose lors de la prise de focus (par exemple, lors d'apparitions conditionnelles ou de défilements rapides).
* **Propagation de contraintes sur les grilles TV (Majeur #1)** :
  - Ajout de `propagateMinConstraints = true` on l'ensemble des enveloppes `Box` de cellules de grilles pour VOD, Séries et Résultats de recherche. Cela force la transmission des contraintes de taille de cellules et empêche les cartes à largeur fixe de rétrécir.
* **Tests unitaires et JVM robustes** :
  - Création de `TvPivotScrollTest` avec une couverture exhaustive à 100 % (calcul d'offset optimal, arrondi, viewport nul, et protection contre les débordements numériques d'échelle pour les tailles géantes).

> Validation automatisée : `testDebugUnitTest` et compilation validées à 100% avec succès, lint vert.

## [v1.65.0] - 2026-08-01
### ✨ Synchronisation dynamique de catalogue (T7), rafraîchissement des tendances par session (T8) et correctif de décodage vidéo Android TV (B16)
* **Synchronisation dynamique du catalogue par préférence (T7)** : 
  - La durée de fraîcheur (TTL) du catalogue local est désormais résolue dynamiquement à partir de la fréquence configurée par l'utilisateur (`DAILY`, `WEEKLY`, `MONTHLY`, `DISABLED`).
  - Lancement silencieux et asynchrone de `syncIfStale()` en tâche de fond lors de l'accès aux onglets Live TV, VOD et Séries, évitant tout loader plein écran ou blocage visuel de navigation.
  - Découplage complet de `OfflineBanner` de l'historique d'échecs passés. Le bandeau se fonde uniquement sur l'état de connexion réseau réelle (`!isNetworkOnline`), s'affichant de manière non intrusive pour signaler la consultation de données en cache lorsque l'accès Internet est indisponible, et se masquant instantanément dès que l'appareil est connecté.
  - Interception hermétique et absorption des exceptions réseau ou serveurs transitoires lors du cycle de rafraîchissement automatique en ligne.
* **Rafraîchissement silencieux des tendances en arrière-plan (T8)** :
  - Élimination des sauts visuels (layout shifts) et perturbations de focus au D-pad sur l'Accueil grâce au figeage par session.
  - Tout cache populaire (Films ou Séries) existant est chargé instantanément et fige sa liste pour toute la session active de `HomeViewModel`.
  - La mise à jour TMDB s'effectue en arrière-plan de façon asynchrone et silencieuse, écrivant les données fraîches en cache local persistant sans altérer l'UI en cours. Les modifications sont prises en compte au prochain démarrage de l'application.
  - Un cache populaire encore frais n'émet plus de requêtes inutiles vers l'API TMDB.
  - Les indicateurs de session (`popularVodResolvedForSession` et `popularSeriesResolvedForSession`) bloquent toute relecture ou double chargement lors de rechargements ultérieurs (par exemple lors du changement de préférences de catégories). Premier démarrage à froid préservé (affichage direct dès réception réseau).
* **Correctif du décodage vidéo sur Android TV (B16)** :
  - Résolution définitive du problème de rendu vidéo corrompu (lignes horizontales déchirées avec aplats de couleurs YUV saturées) constaté sur certains téléviseurs Android TV (notamment Philips UHD API 30).
  - Introduction d'une politique de décodage asymétrique (`PlayerDecoderPolicy`) isolant les extensions de décodage par type de piste.
  - Création d'une fabrique de renderers vidéo matériels prioritaires (`VideoHardwarePreferredRenderersFactory`) forçant le mode `ON` côté vidéo. Les pistes vidéo passent ainsi en priorité par les décodeurs matériels de l'appareil (`MediaCodecVideoRenderer`) pour restituer une image correcte et fluide.
  - Maintien du mode `PREFER` côté audio global de la factory, préservant la priorité au décodage logiciel FFmpeg de NextLib pour continuer de lire EAC3, AC3, et DTS de manière transparente et sans coupure de son sur les matériels dépourvus de puces de décodage d'appoint.
  - Nettoyage des imports reliquats obsolètes dans les trois écrans de lecture (`PlayerScreen`, `VodPlayerScreen`, `SeriesPlayerScreen`) pour sceller la construction unique du lecteur dans `ExoPlayerCore.kt`.

> Validation automatisée : `testDebugUnitTest` et compilation validées à 100% avec succès, lint vert.

## [v1.64.14] - 2026-08-01
### 🐛 Élimination de la latence de l'Accueil, cache toléré et Skeleton Loader (B15)
* **Affichage immédiat sur cache expiré** : Remplacement du blocage initial par un cache périmé-toléré (fallback temporaire au-delà de 24h). L'Accueil se charge instantanément au lieu d'afficher un écran noir bloqué par un spinner pendant 2 secondes.
* **Fusion stable sans sauts visuels (Append on Refresh)** : Lors du rafraîchissement asynchrone des tendances, les nouvelles cartes sont ajoutées à la suite du cache périmé existant sans retirer ni réordonner les éléments vus par l'utilisateur. Le dédoublonnage utilise la paire sémantique unique `(tmdbId, isMovie)` pour éviter toute collision d'identifiants entre films et séries de TMDB.
* **Skeleton Loader Hero dédié** : En l'absence totale de cache (premier lancement), un composant de chargement Skeleton non interactif occupe le créneau exact de la Hero Card (470dp sur mobile, 300dp sur TV), laissant le reste de l'Accueil (autres sections) immédiatement disponible et navigable sans blocage ni focus parasite au D-pad.
* **Désaccouplement du loader plein écran** : Le spinner global de l'Accueil est désormais restreint au chargement nominal de la structure (`isLoading`), libérant le flux des tendances asynchrones (`awaitingTrending`) pour un rendu fluide et progressif.

> Validation automatisée : `testDebugUnitTest` et compilation validées à 100% avec succès.

## [v1.64.13] - 2026-07-30
### 🔧 Build local de production et automatisation de signature
* **Script de release locale unifiée** : Ajout de `scripts/release-local.sh` pour piloter la chaîne complète (tests, lint, compilation de l'APK signé de release, tagging Git local, push et création automatisée de la release GitHub avec APK attaché).
* **Délocalisation de la pipeline CI** : Retrait du workflow GitHub Actions lourd pour économiser les quotas et accélérer le build de release via un cache Gradle chaud en local.
* **Configuration sécurisée du Keystore** : Chargement dynamique des secrets de signature via `keystore.properties` (non versionné) avec priorité aux variables d'environnement.

> Validation automatisée : Compilation et signature validées en local avec succès.

## [v1.63.0] - 2026-07-28
### ✨ Refonte de l'interface TV : navigation latérale, Hero Card, logo et « Voir tout » (F18)
* **Barre de navigation latérale rétractable (TV)** : Remplacement de la navigation supérieure par une barre latérale animée ancrée à gauche (68dp ↔ 260dp), s'ouvrant automatiquement lors du focus D-pad et affichant le profil actif, l'expiration de session filtrée et les destinations.
* **Gestion sécurisée de la touche Retour** : Fermeture de la barre latérale sans déconnexion accidentelle de l'utilisateur, avec restitution propre du focus D-pad au contenu principal de l'écran.
* **Hero Card TV immersive** : Mise en avant de la première tendance TMDB disponible sur l'accueil, avec dégradé de protection visuelle, badge de type, titre et année.
* **Aperçu de bande-annonce temporisé** : Lancement automatique de l'aperçu silencieux YouTube WebView après 1,5 seconde de focus continu sur la Hero Card. Libération instantanée des ressources lors du changement de focus, d'écran ou de mise en arrière-plan.
* **Contrôle Mute/Unmute au D-pad** : Bouton d'action dynamique focusable sur la Hero Card pour activer ou couper le son pendant la lecture de l'aperçu.
* **Bouton « Voir tout » unifié** : Remplacement des boutons TV lourds par le composant unifié `SeeAllLink`, discret au repos et s'ornant d'un fond lavande translucide réactif et visible lors du focus TV.
* **Identité visuelle Android TV rafraîchie** : Remplacement de la bannière TV par un vecteur unique HD combinant le logo de tasse CSTV à vapeur violette caractéristique de la marque sur fond violet foncé.
* **Unification complète de la navigation** : Suppression de l'ancienne navigation par enum `AppScreen` et boucle `when` manuelle de `MainActivity.kt` au profit du système de navigation `AppNavGraph` (navigation-compose) unifié pour mobile et TV.

> Validation automatisée : `testDebugUnitTest` et compilation validées à 100% avec succès.

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
