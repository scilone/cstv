# Fonctionnalités de CSTV IPTV

Ce document détaille l'ensemble des fonctionnalités implémentées dans l'application CSTV (Android et Android TV).

---

## 1. Authentification & Connexion Xtream Codes
L'accès au contenu IPTV s'effectue exclusivement via l'API **Xtream Codes** (`player_api.php`).
* **Saisie sécurisée** : Saisie de l'URL du serveur, du nom d'utilisateur (username) et du mot de passe (password).
* **Sécurité** : Les identifiants sont chiffrés et stockés localement de manière sécurisée (DataStore chiffré / EncryptedSharedPreferences). Ils ne sont jamais consignés dans les logs ni inclus dans des fichiers versionnés.
* **Gestion défensive** : Gestion robuste des cas d'erreur réseau (timeout, serveur injoignable), des identifiants erronés et des comptes expirés.
* **Multi-Profils locaux** : Possibilité de créer plusieurs profils locaux de type Netflix sous un même compte Xtream Codes (voir section dédiée).

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
* **Tendances Accueil via TMDB** : Intégration de l'API externe TMDB pour afficher les films populaires mondiaux sur l'écran d'accueil, avec repli transparent si la clé API locale est absente ou si le réseau externe est indisponible.

---

## 5. Séries TV
* **Navigation par saisons & épisodes** : Interface optimisée permettant de parcourir facilement les saisons d'une série et d'accéder à la liste de ses épisodes.
* **Métadonnées détaillées** : Résumé des épisodes, notes et dates de sortie.
* **Reprise de lecture par épisode** : Suivi de l'état de lecture propre à chaque épisode et à chaque profil local.
* **Enchaînement automatique** : Option pour lancer l'épisode suivant directement après la fin de la lecture de l'épisode en cours.

---

## 6. Recherche Locale & Avancée (FTS)
* **Moteur de recherche performant** : Utilisation des fonctionnalités FTS (Full-Text Search) de SQLite/Room pour une recherche instantanée dans tout le catalogue hors-ligne.
* **Recherche Avancée** :
  * Filtrage précis par type de média (Live, VOD, Séries).
  * Filtrage multicritères par catégorie.
  * Tri par date d'ajout, note ou titre.
  * Matcher de titre approximatif (recherche tolérante aux fautes de frappe).

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

## 🚫 Fonctionnalités hors périmètre (Exclusions validées)
Pour des raisons de performance, de stabilité ou d'expérience utilisateur, les fonctionnalités suivantes sont **strictement hors périmètre** :
* **Multi-comptes Xtream** : L'application gère un seul compte Xtream Codes actif à la fois (les profils sont purement locaux et rattachés à ce compte unique).
* **Support M3U/M3U8 bruts** : Zéro support des playlists au format fichier `.m3u` brut (nécessite obligatoirement un serveur Xtream Codes).
* **Catch-up / Timeshift / Enregistrement (PVR)** : Non supportés.
* **Chromecast / Google Cast** : **Retiré définitivement (Phase 27)**. Le récepteur Google Cast par défaut ne décode pas matériellement les codecs AC3, EAC3 et DTS (très fréquents sur les flux IPTV). Cela entraînait des vidéos lues sans le son. Le support a été abandonné au profit de la lecture locale exclusive optimisée par NextLib.
