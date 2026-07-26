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
* **Top 10 Populaire TMDB & Badge de Rang (F9)** : Les carrousels "Top 10 Films" et "Top 10 Séries" sur l'Accueil s'appuient désormais sur les flux populaires réels mondiaux récupérés via TMDB (routes `/movie/popular` et `/tv/popular`), croisés de façon asynchrone avec le catalogue IPTV local grâce au `TmdbCatalogMatcher`. Si l'API TMDB n'est pas configurée ou est indisponible, un fallback automatique et silencieux vers le repli local (`TopRatedSelector.selectTop10`, note >= 8.0) est appliqué de manière fluide. Chaque carte de ces Top 10 affiche un grand chiffre de rang en surimpression (style Netflix, de 1 à 10) sur le bord gauche, avec une lisibilité maximale garantie par un fond translucide et un liseré clair.

---

## 5. Séries TV
* **Navigation par saisons & épisodes** : Interface optimisée permettant de parcourir facilement les saisons d'une série et d'accéder à la liste de ses épisodes.
* **Métadonnées détaillées** : Résumé des épisodes, notes et dates de sortie.
* **Reprise de lecture par épisode** : Suivi de l'état de lecture propre à chaque épisode et à chaque profil local.
* **Enchaînement automatique** : Option pour lancer l'épisode suivant directement après la fin de la lecture de l'épisode en cours.

---

## 6. Recherche Locale & Avancée (FTS)
* **Moteur de recherche performant** : Utilisation des fonctionnalités FTS (Full-Text Search) de SQLite/Room pour une recherche instantanée dans tout le catalogue hors-ligne.
* **Recherche par crédit (acteur/réalisateur)** : Un clic sur le nom d'un acteur ou réalisateur depuis une fiche détaillée (Film ou Série) déclenche une recherche dédiée. Lors de cette transition, tous les filtres avancés précédemment actifs sont réinitialisés pour éviter d'exclure par erreur des résultats de l'acteur (prévention des faux positifs de résultats vides). Le moteur effectue une recherche exhaustive sur le titre, les acteurs, le réalisateur et le genre.
* **Recherche Avancée** :
  - Bouton d'action principal « Voir les résultats » rendu collant (sticky) en bas du volet de recherche pour rester visible en permanence pendant le défilement indépendant de tous les critères de filtres, réduisant la friction sur mobile et TV.
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

## 🚫 Fonctionnalités hors périmètre (Exclusions validées)
Pour des raisons de performance, de stabilité ou d'expérience utilisateur, les fonctionnalités suivantes sont **strictement hors périmètre** :
* **Multi-comptes Xtream** : L'application gère un seul compte Xtream Codes actif à la fois (les profils sont purement locaux et rattachés à ce compte unique).
* **Support M3U/M3U8 bruts** : Zéro support des playlists au format fichier `.m3u` brut (nécessite obligatoirement un serveur Xtream Codes).
* **Catch-up / Timeshift / Enregistrement (PVR)** : Non supportés.
* **Chromecast / Google Cast** : **Retiré définitivement (Phase 27)**. Le récepteur Google Cast par défaut ne décode pas matériellement les codecs AC3, EAC3 et DTS (très fréquents sur les flux IPTV). Cela entraînait des vidéos lues sans le son. Le support a été abandonné au profit de la lecture locale exclusive optimisée par NextLib.
