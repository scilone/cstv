# Fonctionnalités de CSTV IPTV

Ce document détaille l'ensemble des fonctionnalités implémentées dans l'application CSTV (Android et Android TV).

---

## 1. Authentification & Connexion Xtream Codes
L'accès au contenu IPTV s'effectue exclusivement via l'API **Xtream Codes** (`player_api.php`).
* **Saisie sécurisée** : Saisie de l'URL du serveur, du nom d'utilisateur (username) et du mot de passe (password).
* **Sécurité** : Les identifiants sont chiffrés et stockés localement de manière sécurisée (DataStore chiffré / EncryptedSharedPreferences). Ils ne sont jamais consignés dans les logs ni inclus dans des fichiers versionnés.
* **Gestion défensive** : Gestion robuste des cas d'erreur réseau (timeout, serveur injoignable), des identifiants erronés et des comptes expirés.
* **Multi-Profils locaux** : Possibilité de créer plusieurs profils locaux de type Netflix sous un même compte Xtream Codes (voir section dédiée).
* **Démarrage hors ligne après validation** : Après une connexion en ligne réussie et une synchronisation complète, l'application peut rouvrir le catalogue local sans réseau. Un refus explicite des identifiants ou une expiration révoque cet accès jusqu'à une nouvelle validation en ligne.

---

## Cache persistant du catalogue et mode hors ligne (T4)

* **Catalogue Room réactif** : Les catégories et listes Live, Films et Séries sont lues localement et se mettent à jour dès qu'une synchronisation écrit les nouvelles données.
* **Synchronisation maîtrisée** : Les rafraîchissements manuels, planifiés et au retour du réseau sont centralisés, avec conservation du dernier catalogue valide en cas d'erreur.
* **Détails consultés conservés** : Les fiches VOD et les saisons/épisodes déjà ouverts restent disponibles depuis le cache ; les programmes EPG récemment reçus sont aussi consultables hors connexion.
* **Lecture explicite** : Hors ligne, les téléchargements restent lisibles ; un flux Live ou distant affiche un message de connexion requise au lieu d'ouvrir un lecteur en erreur.

---

## 2. Multi-Profils Locaux
Introduits en Phase 27, les profils locaux permettent un partage personnalisé de l'application au sein d'un même foyer.
* **Séparation des données** : Chaque profil possède ses propres favoris, son historique de lecture, son avancement/reprise de lecture et ses préférences de pistes audio/sous-titres.
* **Données partagées** : Le catalogue de chaînes/VOD/Séries et le cache de la base de données Room restent partagés entre tous les profils locaux afin d'éviter la duplication de données et de réduire la consommation d'espace disque.

---

## 3. Live TV (Télévision en direct)
* **Grille des chaînes** : Navigation fluide par catégories de chaînes.
* **EPG (Electronic Program Guide)** :
  * Affichage des programmes "En cours / Suivant" directement sur la liste des chaînes.
  * Guide complet détaillé par chaîne pour planifier ses soirées.
* **Favoris Live** : Ajout et retrait rapide de chaînes en favoris, synchronisés par profil utilisateur.
* **Historique des chaînes récemment visionnées** : Accès rapide aux dernières chaînes regardées par le profil actif.
* **Zapping rapide** : Support du zapping fluide directement depuis le lecteur vidéo.

---

## 4. Vidéo à la Demande (VOD)
* **Catalogue de films** : Navigation par catégories avec affichage des affiches et des métadonnées (durée, genre, année de sortie, description).
* **Reprise de lecture** : Mémorisation automatique de la position de lecture dans la base de données Room (propre à chaque profil). Reprise fluide là où l'utilisateur s'est arrêté.
* **Recommandations & Titres similaires** : Moteur de recommandation local affichant des suggestions de titres similaires basées sur les genres, l'année ou les réalisateurs.
* **Tendances Accueil via TMDB** : Intégration de l'API externe TMDB pour afficher les films populaires mondiaux sur l'écran d'accueil, avec repli transparent si la clé API locale est absente ou si le réseau externe est indisponible. Le rapprochement (matching) avec le catalogue IPTV local est renforcé par la validation rigoureuse de l'année de sortie (tolérance maximale de +/- 1 an) pour éviter les faux positifs (comme les remakes ou homonymes d'autres époques), avec un repli textuel si l'année est inconnue.
  - **Élimination de la latence au démarrage (B15)** : L'Accueil de l'application se charge instantanément sans écran noir ni blocage par un loader plein écran.
  - **Fallback de cache tolérant** : Si le cache des tendances locales a expiré (plus de 24 heures), il est immédiatement affiché à l'utilisateur comme repli temporaire tandis que la mise à jour TMDB s'effectue en arrière-plan.
  - **Stabilité visuelle (Append on Refresh)** : Les tendances fraîches reçues en arrière-plan sont fusionnées et ajoutées à la fin de la liste affichée sans perturber le positionnement ou l'élément sous le focus de l'utilisateur. Le dédoublonnage s'appuie sur la clé unique `(tmdbId, isMovie)` pour éviter toute confusion entre des films et séries d'identifiants TMDB identiques.
  - **Skeleton Loader localisé** : Si aucun cache n'est disponible (très premier lancement), un skeleton non interactif (sans focus D-pad) occupe l'emplacement de la Hero Card, libérant le reste de l'Accueil pour une interaction immédiate.
* **Top 10 Populaire TMDB & Badge de Rang (F9)** : Les carrousels "Top 10 Films" et "Top 10 Séries" sur l'Accueil s'appuient désormais sur les flux populaires réels mondiaux récupérés via TMDB (routes `/movie/popular` et `/tv/popular`), croisés de façon asynchrone avec le catalogue IPTV local grâce au `TmdbCatalogMatcher`. Si l'API TMDB n'est pas configurée ou est indisponible, un fallback automatique et silencieux vers le repli local (`TopRatedSelector.selectTop10`, note >= 8.0) est appliqué de manière fluide. Chaque carte de ces Top 10 affiche un grand chiffre de rang en surimpression (style Netflix, de 1 à 10) sur le bord gauche, avec une lisibilité maximale garantie par un fond translucide et un liseré clair.

---

## 5. Séries TV
* **Navigation par saisons & épisodes** : Interface optimisée permettant de parcourir facilement les saisons d'une série et d'accéder à la liste de ses épisodes.
* **Métadonnées détaillées** : Résumé des épisodes, notes et dates de sortie.
* **Reprise de lecture par épisode** : Suivi de l'état de lecture propre à chaque épisode et à chaque profil local.
* **Enchaînement automatique** : Option pour lancer l'épisode suivant directement après la fin de la lecture de l'épisode en cours.

---

## 6. Recherche Locale Globalisée par sous-chaîne (F17)
Cette fonctionnalité remplace l'ancien moteur FTS par un système de recherche par sous-chaîne arbitraire (type `LIKE '%keyword%'`), garantissant une flexibilité totale de saisie.
* **Recherche par sous-chaîne flexible** : Saisir un fragment placé au début, au milieu ou à la fin d'un mot (ex: `pilami` ou `lami` trouve `Marsupilami`) permet de retrouver immédiatement le média sans connaître le début exact des mots.
* **Insensibilité complète à la casse et aux accents** : La normalisation de la recherche (minuscules, normalisation NFD, repli des diacritiques latins et des ligatures courantes comme `œ`→`oe`, `ß`→`ss`) garantit que des saisies comme `odysee`, `ODYSEE` ou `odysée` trouvent toutes `Odysée`, et que `rene` trouve `René`.
* **Recherche multi-mots d'ordre libre** : Les requêtes contenant plusieurs mots sont découpées et exigent la présence de chaque fragment dans le média (sur tous les champs recherchables combinés), quel que soit leur ordre de saisie (ex: `jean reno` ou `reno jean` trouve le média).
* **Champs indexés étendus** : La recherche s'effectue sur le titre, les catégories, ainsi que les acteurs, réalisateurs et genres pour les films et séries (lorsque disponibles localement).
* **Recherche par crédit (acteur/réalisateur)** : Un clic sur le nom d'un acteur ou réalisateur depuis une fiche détaillée (Film ou Série) déclenche une recherche dédiée. Lors de cette transition, tous les filtres avancés précédemment actifs sont réinitialisés pour éviter d'exclure par erreur des résultats de l'acteur (prévention des faux positifs de résultats vides). Le moteur effectue une recherche exhaustive sur le titre, les acteurs, le réalisateur et le genre.
* **Recherche Avancée** :
  - Bouton d'action principal « Voir les résultats » rendu collant (sticky) en bas du volet de recherche pour rester visible en permanence pendant le défilement indépendant de tous les critères de filtres au-dessus, réduisant la friction sur mobile et TV.
  - Filtrage précis par type de média (Live, VOD, Séries).
  - Filtrage multicritères par catégorie.
  - Tri par date d'ajout, note ou titre.
  - Matcher de titre approximatif (recherche tolérante aux fautes de frappe).
  - Application cumulative des filtres après le lancement initial d'une recherche.

---

## 7. Téléchargement Hors-Ligne (VOD)
* **Mode hors-ligne** : Possibilité de télécharger des films ou des épisodes de séries localement sur l'appareil.
* **Gestion des téléchargements** : Service de téléchargement en arrière-plan robuste basé sur le gestionnaire de téléchargement ExoPlayer / Media3.
* **Lecture locale** : Section "Téléchargements" dédiée permettant de regarder les vidéos téléchargées sans aucune connexion internet active, en utilisant le même lecteur vidéo performant.
* **Raccourci sur l'écran d'Accueil (F15)** : Intégration d'une nouvelle section horizontale « Téléchargements » en dernière position de l'écran d'Accueil (masquée si la liste est vide). Elle affiche un carrousel des 20 derniers téléchargements terminés (`COMPLETED`) par ordre antéchronologique. Les cartes affichent le titre, le sous-titre (repère saison/épisode pour les séries) et lancent directement la lecture locale hors-ligne, avec un bouton « Voir tout » qui redirige vers l'écran complet. La liste est réactive et optimisée pour ignorer les recompositions parasites lors de la progression d'autres téléchargements en cours d'écriture.

---

## 8. Paramètres de l'application
* **Préférences de pistes (Tracks)** : Choix de la langue audio et des sous-titres préférés par défaut (sauvegardé par profil local).
* **Masquage de catégories** : Possibilité de désactiver/masquer certaines catégories de chaînes ou de VOD (ex: catégories adultes ou étrangères) pour épurer l'interface de navigation.
* **Synchronisation du catalogue** : Contrôle sur la fréquence de synchronisation automatique en arrière-plan du catalogue IPTV via WorkManager.

---

## 9. Gestion de l'historique de visionnage local (F8)
Permet à l'utilisateur de nettoyer et de contrôler manuellement son historique local, assurant réactivité de l'interface et confidentialité par profil.
* **Suppression unifiée et réactive** : Possibilité de retirer manuellement un film ou un épisode de série de la section **« Continuer à regarder »** (Accueil, Films, Séries) et une chaîne de télévision de la section **« Récemment vus »** (Live TV). La disparition du contenu est instantanée grâce aux flux Room réactifs, sans rechargement manuel requis.
* **Ciblage exact d'un épisode** : La suppression d'une carte de série cible uniquement l'épisode représenté (son `streamId` exact), sans réinitialiser la progression des autres épisodes. Si d'autres épisodes sont en cours de lecture, la carte se met à jour pour afficher le prochain épisode à reprendre ; sinon, elle disparaît.
* **Geste universel** : Déclenchement intuitif via un appui long sur support tactile (mobile) ou un maintien prolongé du bouton de validation central (télécommande Android TV). Le geste sur TV est entièrement sécurisé pour consommer l'événement de touche relâchée (`KeyUp`) afin d'éviter tout lancement accidentel du lecteur.
* **Dialogue de confirmation sécurisé** : L'appui long ouvre un dialogue de confirmation stateless commun proposant de *Retirer de la liste* ou d'*Annuler*. Sur Android TV, le focus initial est placé de façon sécurisée sur le bouton *Annuler* pour prévenir toute suppression involontaire.
* **Invalidation dynamique des recommandations** : Retirer un film ou un épisode de la liste « Continuer à regarder » invalide immédiatement le cache des recommandations du profil de goûts pour refléter instantanément la modification sur l'écran d'Accueil.
* **Isolation complète** : La suppression est locale à l'appareil et strictement restreinte au profil actif.

---

## 10. Système d'évaluation J'aime / Je n'aime pas (F7)
Permet à chaque profil local d'exprimer des préférences explicites sur les films et les séries pour personnaliser et contrôler activement ses recommandations.
* **Évaluation explicite** : Intégration de boutons « J'aime » et « Je n'aime pas » sur les fiches détails des films et séries (mobile et Android TV). Les deux actions sont inactives par défaut et s'excluent mutuellement avec un état sélectionné visuellement clair.
* **Exclusion absolue du Rejet (DISLIKE)** : Marquer un média comme « Je n'aime pas » l'exclut immédiatement des sections de recommandation (« Recommandé pour vous ») et de son profil de goûts. De plus, cela retire automatiquement le média des Favoris et de la section « Continuer à regarder ».
* **Renforcement de l'Appréciation (LIKE)** : Un vote « J'aime » apporte un signal positif très fort (pondération x3.0) au profil de goûts de l'utilisateur, ce qui permet de générer des recommandations pertinentes, y compris pour les profils en démarrage à froid (cold start). Afin de privilégier la découverte de nouveaux contenus, les contenus aimés sont également exclus des carrousels de recommandations.
* **Gestion et Transaction Locale** : Tout le système de vote et ses effets collatéraux (`DISLIKE` entraînant le retrait des favoris et reprises) s'exécutent de façon atomique via une unique transaction Room locale, sans aucun appel réseau Xtream Codes ou TMDB externe, assurant un fonctionnement hors ligne complet.
* **Invalidation et Réactivité instantanée** : Toute modification d'évaluation invalide immédiatement le cache local de recommandations du profil, propageant un événement réactif à l'écran d'Accueil pour actualiser instantanément les carrousels de suggestions en cours de visionnage.
* **Isolation complète** : Les évaluations sont strictement liées au profil local actif de l'appareil ; les votes d'un profil n'ont aucune influence sur ceux des autres profils.

---

## 11. Visibilité de la barre de statut sur Mobile & Gestion du poinçon (F11)
Cette fonctionnalité rétablit l'affichage de la barre de statut système pendant la navigation sur mobile, tout en préservant l'immersion plein écran pendant la lecture vidéo.
* **Barre d'état visible hors lecture** : Permet à l'utilisateur de consulter l'heure, la batterie et les notifications système pendant la navigation (Connexion, Profils, Accueil, Catalogues, Détails, Paramètres) avec une apparence esthétique harmonieuse.
* **Mode immersif dynamique** : Masque automatiquement et de force les barres système supérieure (statut) et inférieure (navigation) dès le lancement de la lecture vidéo dans les 3 lecteurs (Live TV, VOD, Séries) pour garantir un plein écran complet.
* **Respect des zones sûres (Insets)** : Intégration de paddings de sécurité (`statusBarsPadding()`, `safeDrawingPadding()`, etc.) sur tous les écrans de navigation mobile pour éviter que les contrôles ou les titres cliquables ne soient masqués par l'encoche (notch) ou le poinçon de la caméra frontale.
* **Support du mode Cutout "shortEdges"** : Autorise le contenu vidéo des lecteurs à s'étendre derrière l'encoche en mode paysage pour utiliser 100 % de l'écran, tout en protégeant les contrôles tactiles.
* **Isolation complète de la TV** : Aucune incidence sur Android TV, où les barres système restent toujours invisibles et où le focus et la navigation existants sont préservés.

---

## 12. Lecture automatique du trailer sur l'accueil (F10)
Cette fonctionnalité apporte du mouvement et de l'interactivité à l'Accueil en remplaçant l'affiche statique (poster) du média vedette du carrousel par la lecture automatique en boucle de sa bande-annonce YouTube.
* **Lancement temporisé stable** : La lecture ne démarre qu'après 5 secondes d'arrêt continu et stable sur une page du carrousel de Tendances. Un changement rapide de page annule immédiatement le chargement.
* **Intégration YouTube sécurisée** : Utilise l'API IFrame YouTube officielle embarquée sans exiger de services Google Play ou de clés d'API tierces, garantissant la légalité et la robustesse de la lecture.
* **Gestion du cycle de vie rigoureuse** : Le lecteur vidéo YouTube est libéré instantanément dès un changement de page, un clic de carte, un changement d'onglet, une mise en arrière-plan ou si l'Accueil devient invisible.
* **Coupure sonore par défaut & Contrôle d'accessibilité** : Tout nouvel aperçu démarre silencieusement (muet) pour respecter le confort de navigation de l'utilisateur. Un bouton sonore sous forme d'icône d'accès rapide (avec descriptions d'accessibilité) permet d'activer ou de couper le son pour l'aperçu courant, sans impacter les paramètres globaux du lecteur principal.
* **Résilience & Repli transparent** : En cas d'erreur de chargement, d'échec du lecteur ou si aucun trailer n'est disponible (champ Xtream absent ou invalide, et échec du repli asynchrone TMDB `/{movie|tv}/{id}/videos`), le poster statique reste visible de façon transparente sans perturber l'utilisateur.
* **Préservation Android TV & Reprises** : La fonctionnalité est volontairement limitée à l'accueil tactile mobile. Android TV (navigation par focus D-pad) et le carrousel « Continuer à regarder » conservent intact leur fonctionnement d'origine.

---

## 13. Navigation vers la fiche détails depuis le clic sur la cover du player (F16)
Cette fonctionnalité permet d'accéder directement à la fiche détaillée (fiche média) d'un film ou d'une série depuis le lecteur vidéo en rendant sa cover cliquable.
* **Cover interactive et focusable** : La jaquette (cover) affichée dans le bloc inférieur gauche des contrôles des lecteurs `VodPlayerScreen` et `SeriesPlayerScreen` devient interactive. Sur mobile, elle est cliquable tactilement, et sur Android TV, elle est entièrement focusable au D-pad (avec mise en valeur par une bordure d'accentuation violette) et validable par appui central. Un placeholder cliquable prend le relais lorsque l'image de la cover est indisponible.
* **Navigation intelligente (Gestion du Backstack)** :
  - Si la fiche de détails correspondante est l'écran immédiatement précédent dans la pile de navigation, l'action effectue un simple retour arrière (`popBackStack`), évitant ainsi la duplication inutile d'écrans.
  - Si le lecteur a été ouvert depuis un autre écran (par exemple l'Accueil ou l'écran des Téléchargements), le lecteur est fermé puis la fiche détaillée du média est ouverte. Un retour depuis celle-ci ramène l'utilisateur à l'écran d'origine (et non au lecteur fermé).
* **Sauvegarde automatique et fermeture propre** : Le clic sur la cover applique exactement le même cycle d'arrêt et de libération des ressources que les boutons Fermer ou Retour, garantissant que la progression de lecture en cours est fidèlement mémorisée en base de données.
* **Fiabilisation des métadonnées de séries** : La colonne `seriesId` est désormais correctement écrite en base de données pour chaque position d'épisode de série, permettant de restaurer la fiche série associée même lors d'une reprise directe depuis « Continuer la lecture » de l'Accueil.
* **Traitement des cas d'erreur & Mode hors-ligne** : Si les métadonnées nécessaires à l'ouverture de la fiche ne peuvent être résolues, la lecture n'est pas interrompue et un message d'erreur transitoire non technique est affiché. Hors connexion, la fiche détaillée s'ouvre à partir des données mises en cache localement dans la base de données Room.

---

## 14. Lecture automatique du trailer YouTube sur les fiches de détail (Films/Séries) (F13)
Cette fonctionnalité intègre la lecture automatique et immersive de bandes-annonces YouTube directement en arrière-plan du bloc d'en-tête des fiches détaillées de films et séries, calquée sur l'expérience dynamique de l'Accueil.
* **Lecture automatique immersive** : Après 5 secondes passées de manière stable et continue sur la fiche d'un média (film ou série), le poster statique de fond (Backdrop) s'estompe délicatement pour laisser place à la lecture en boucle du trailer YouTube correspondant.
* **Contrôle sonore complet** : La bande-annonce démarre systématiquement en mode muet pour préserver le confort de navigation de l'utilisateur. Un bouton sonore d'activation/désactivation du son (Mute/Unmute) avec étiquettes d'accessibilité est intégré à la barre d'action supérieure des fiches (mobile et TV) pour permettre à l'utilisateur de l'activer d'un clic/touche.
* **Gestion hermétique du cycle de vie** : La lecture est interrompue de force et les ressources de la WebView sont libérées instantanément dès que l'utilisateur quitte la fiche détaillée, met l'application en arrière-plan (gestion des événements `ON_STOP` du Lifecycle), ou lance la lecture vidéo principale (plein écran).
* **Résolution de source à facettes** : En l'absence de bande-annonce fournie par le serveur IPTV local et de `tmdbId` de média, l'application recherche activement et dynamiquement le média sur TMDB via son titre normalisé et son année de sortie, appliquant un filtrage de correspondance strict (seuil de similarité >= 0,8 et tolérance d'année de ± 1 an) pour éviter les faux-positifs.
* **Cache persistant haute performance (v20)** : Le résultat de résolution (positif comme négatif) est conservé localement en base de données Room avec des durées de validité (TTL) asymétriques (30 jours pour un trailer trouvé, 7 jours pour un résultat négatif) pour éliminer les appels réseau réseau répétitifs lors des visites ultérieures, y compris hors ligne.
* **Résilience aux pannes de lecture** : Si une vidéo mémorisée devient illisible (retirée de YouTube, géo-bloquée, etc.), un échec de lecture invalide et purge immédiatement l'entrée du cache persistant local, forçant une nouvelle résolution lors de la prochaine consultation et restaurant l'affichage statique d'origine sans erreur intrusive.

---

## 15. Refonte de l'interface Android TV (F18)
Cette fonctionnalité modernise en profondeur l'expérience d'utilisation sur Android TV en introduisant une navigation latérale fluide, une Hero Card immersive avec lecture de bande-annonce, et une harmonisation de l'identité visuelle de la marque.
* **Barre de navigation latérale rétractable** : Remplacement de l'ancienne barre horizontale par une barre de navigation rétractable ancrée à gauche sur toute la hauteur de l'écran (accessible sur l'Accueil, TV en Direct, Films, Séries, Recherche, Paramètres).
  - *État plié par défaut* : Affiche uniquement les icônes de sections et l'avatar du profil pour maximiser l'espace du catalogue et éviter tout saut ou redimensionnement visuel du contenu.
  - *Dépliement fluide au focus D-pad* : Dès que le focus télécommande entre dans la barre, celle-ci s'ouvre pour révéler les libellés des 6 destinations, le nom du profil actif et de la session, et la date d'expiration de l'abonnement si elle est valide (filtrée pour masquer les valeurs erronées ou indéterminées).
  - *Gestion robuste de la touche Retour* : L'appui sur Retour lorsque la barre est ouverte la referme instantanément et restitue proprement le focus au contenu de l'écran, sans risque de déconnexion ou de fermeture inopinée de l'application.
* **Hero Card TV immersive en tête d'accueil** : Remplacement de la rangée de tendances TV par une Hero Card majestueuse présentant la tendance TMDB principale.
  - *Focus et métadonnées* : Contour d'accentuation lavande réactif, titre, année de sortie, badges d'identité ("TENDANCE", "FILM" ou "SÉRIE") lisibles sur un dégradé protecteur. Un clic au bouton OK de la télécommande ouvre directement la fiche de détails.
  - *Lecture de bande-annonce temporisée* : Un focus continu de 1,5 seconde déclenche automatiquement le remplacement de l'affiche par la lecture silencieuse en boucle de la bande-annonce YouTube. L'aperçu est instantanément libéré à la perte de focus, lors du changement d'écran ou lors de la mise en arrière-plan.
  - *Contrôle du volume (Mute/Unmute)* : Un bouton de gestion du son apparaît et devient focusable au D-pad pendant l'aperçu sans interrompre la lecture.
  - *Aucune Hero de reprise de lecture* : Règle stricte et partagée interdisant de promouvoir une reprise de lecture comme Hero Card sur mobile ou sur TV, sanctuarisant la mise en avant des tendances éditoriales TMDB.
* **Lien « Voir tout » unifié** : Remplacement du lourd rectangle de bouton gris par un composant partagé `SeeAllLink`. Sur TV, il se présente comme un lien textuel lavande discret sans arrière-plan au repos, se parant d'un fond lavande translucide à coins arrondis très visible à distance lors du focus.
* **Bannière TV Coffee Stream TV** : Remplacement du placeholder générique de l'application par un vecteur haute définition 320×180 reprenant le logo tasse de café CSTV et ses vapeurs lavande sur fond violet foncé, offrant une cohérence d'identité totale avec l'application mobile.

---

## 16. Synchronisation silencieuse du catalogue par fréquence (T7)
Cette amélioration aligne la fraîcheur du catalogue local sur la fréquence de synchronisation choisie par l'utilisateur et rend les tentatives de mise à jour totalement invisibles pour l'utilisateur.
* **Respect de la fréquence utilisateur** : La durée de fraîcheur du catalogue est calculée de manière dynamique à partir du réglage de fréquence (`DAILY`, `WEEKLY`, `MONTHLY`, `DISABLED`) défini dans les paramètres. Un catalogue sous configuration `DISABLED` n'est plus considéré comme périmé sur simple critère d'âge.
* **Synchronisation invisible en arrière-plan** : Lorsqu'un catalogue est périmé et que l'appareil est connecté à Internet (en ligne), l'application déclenche silencieusement la synchronisation en tâche de fond dès l'accès aux listes média (Live TV, VOD, Séries).
* **Bandeau hors-ligne non anxiogène** : Le composant `OfflineBanner` est masqué dès que l'appareil est connecté à Internet, quel que soit l'historique d'échecs de synchronisation passés. Il ne s'affiche plus pour signaler un échec technique, mais uniquement de manière informative pour indiquer la consultation de données locales en cache lorsque l'appareil est réellement hors ligne (`!isNetworkOnline`).
* **Absorbance d'erreurs** : Les échecs transitoires de la synchronisation silencieuse (timeout, serveurs injoignables) sont interceptés silencieusement sans propager d'alerte ou perturber l'affichage du cache existant.

---

## 17. Rafraîchissement silencieux des tendances en arrière-plan (T8)
Cette fonctionnalité élimine toute perturbation visuelle ou saut de cartes dans la section "Top 10" de l'Accueil en décalant l'affichage des données fraîches de tendances au démarrage suivant de l'application.
* **Données figées par session** : Dès que les listes "Top 10" (Films ou Séries) sont initialisées (que ce soit depuis le cache existant, ou depuis le réseau lors d'un premier démarrage à froid), elles sont figées pour l'intégralité de la session active du `HomeViewModel`.
* **Mise à jour silencieuse du cache** : Si le cache initialement affiché est périmé (> 24 heures), une actualisation réseau TMDB est exécutée de façon asynchrone en arrière-plan. Ses résultats sont écrits en base de données de manière persistante, mais l'état UI en cours n'est jamais modifié par ce retour réseau silencieux, prévenant tout décalage d'éléments sous le focus ou les yeux de l'utilisateur.
* **Consommation réseau et TMDB rationalisée** : Un cache populaire jugé encore frais ne déclenche aucun appel de rafraîchissement silencieux en tâche de fond, protégeant ainsi l'appareil et l'API TMDB de requêtes superflues.
* **Résilience et isolation** : En l'absence complète de cache (très premier lancement), les données réseau reçues sont appliquées immédiatement pour éviter une rangée vide persistante. Les traitements et la stabilisation sont gérés de manière strictement indépendante entre la rangée des films et celle des séries.

---

## 18. Correction de la corruption d'image vidéo sur Android TV (B16)
Cette correction résout le problème de rendu vidéo corrompu (bandes horizontales déchirées avec aplats de couleurs saturées YUV) rencontré sur certains modèles de téléviseurs (notamment Philips UHD Android TV sous Android 11).
* **Dissociation des priorités de décodage par piste** : Correction de la configuration globale du lecteur ExoPlayer / Media3 qui préférait à tort le décodage vidéo logiciel (FFmpeg d'appoint fourni par NextLib) au décodage matériel du téléviseur.
* **Décodage matériel prioritaire pour la vidéo** : Création d'une fabrique de renderers personnalisée (`VideoHardwarePreferredRenderersFactory`) qui force le mode `ON` pour le renderer vidéo. Le flux vidéo passe ainsi prioritairement par le décodeur matériel de l'appareil (`MediaCodecVideoRenderer`), assurant une restitution d'image fluide et correcte, identique à celle d'une application de référence. Le décodage logiciel FFmpeg reste disponible uniquement en ultime recours si le format n'est pas supporté par le matériel.
* **Maintien de la préférence FFmpeg pour l'audio** : Le mode d'extension global de la factory reste configuré sur `PREFER` pour la partie audio, garantissant que les codecs complexes EAC3, AC3 et DTS continuent d'être décodés de manière logicielle de façon transparente et sans perte de son sur les téléviseurs dépourvus de licence matérielle correspondante.
* **Tests unitaires et isolation** : L'implémentation de la politique de décodage asymétrique est isolée dans un composant pur (`PlayerDecoderPolicy`), validé unitairement sans dépendances de runtime Android.

---

## 🚫 Fonctionnalités hors périmètre (Exclusions validées)
Pour des raisons de performance, de stabilité ou d'expérience utilisateur, les fonctionnalités suivantes sont **strictement hors périmètre** :
* **Multi-comptes Xtream** : L'application gère un seul compte Xtream Codes actif à la fois (les profils sont purement locaux et rattachés à ce compte unique).
* **Support M3U/M3U8 bruts** : Zéro support des playlists au format fichier `.m3u` brut (nécessite obligatoirement un serveur Xtream Codes).
* **Catch-up / Timeshift / Enregistrement (PVR)** : Non supportés.
* **Chromecast / Google Cast** : **Retiré définitivement (Phase 27)**. Le récepteur Google Cast par défaut ne décode pas matériellement les codecs AC3, EAC3 et DTS (très fréquents sur les flux IPTV). Cela entraînait des vidéos lues sans le son. Le support a été abandonné au profit de la lecture locale exclusive optimisée par NextLib.
