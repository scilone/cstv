# Prompt — Application IPTV Android / Android TV (API Xtream Codes)

## Contexte et objectif

Tu es un développeur Android senior. Je veux que tu développes une application **native Kotlin**, compatible **Android mobile** et **Android TV**, permettant de se connecter à un serveur IPTV via l'**API Xtream Codes** et de regarder du contenu Live TV et VOD (films + séries). L'application ne doit gérer **aucun autre protocole** (pas de M3U/M3U8 brut, pas de Stalker Portal) : uniquement Xtream Codes.

Livre le projet complet, compilable, avec l'arborescence des fichiers, le code source intégral, les fichiers Gradle, le manifeste, et un README expliquant comment builder et lancer le projet.

---

## 1. Stack technique imposée

- **Langage** : Kotlin 100% (pas de Java).
- **UI** :
  - Mobile : Jetpack Compose.
  - Android TV : Compose for TV (androidx.tv:tv-material, tv-foundation) — pas de Leanback legacy (XML), sauf si tu juges qu'un composant Leanback précis est strictement nécessaire (justifie-le dans le README).
- **Architecture** : Clean Architecture + MVVM.
  - Couches : `data` (remote/local), `domain` (use cases, models métier), `presentation` (ViewModels + UI Compose).
  - Injection de dépendances : Hilt.
- **Réseau** : Retrofit + OkHttp (avec interceptor de logs en debug uniquement).
- **Sérialisation** : kotlinx.serialization ou Moshi (au choix, reste cohérent sur tout le projet).
- **Lecteur vidéo** : ExoPlayer (Media3 — `androidx.media3.exoplayer`), avec support HLS (`media3-exoplayer-hls`) et DASH si besoin. Gestion du `TrackSelector` pour choisir qualité/langue/sous-titres si disponibles dans le flux.
- **Persistance locale** :
  - Room : cache des catégories, chaînes, films, séries (pour navigation hors-ligne partielle et rapidité).
  - DataStore (Preferences) **chiffré** ou EncryptedSharedPreferences pour stocker les identifiants Xtream (host, port, username, password) — jamais en clair, jamais loggés.
- **Images** : Coil (compatible Compose) pour les logos de chaînes et affiches VOD/séries.
- **Min SDK** : 21 (Android 5.0) — compatible avec les box Android TV anciennes.
- **Target/Compile SDK** : dernière version stable (35 ou la plus récente disponible au moment du build).
- **Gestion des builds** : un seul module `app` avec build variants ou flavors si tu juges pertinent de séparer mobile/TV, sinon une seule APK universelle avec detection de form factor (`UiModeManager` pour détecter TV).

---

## 2. Authentification et intégration API Xtream Codes

### 2.1 Écran de connexion

Champs requis :
- URL du serveur (host, avec ou sans `http://`/`https://`)
- Port (optionnel si inclus dans l'URL)
- Nom d'utilisateur
- Mot de passe

Au submit :
1. Construire l'URL de base : `http(s)://HOST:PORT`
2. Appeler `player_api.php` pour valider les identifiants :
   ```
   GET {base_url}/player_api.php?username={user}&password={pass}
   ```
3. Vérifier dans la réponse JSON le champ `user_info.auth` (doit être `1`) et `user_info.status` (doit être `Active`).
4. Afficher les informations utiles à l'utilisateur si besoin : date d'expiration (`exp_date`, timestamp Unix à convertir), nombre de connexions max (`max_connections`), statut.
5. En cas d'erreur (401, timeout, JSON invalide, `auth: 0`) : afficher un message clair et explicite (ne jamais afficher une stack trace brute à l'utilisateur).
6. Stocker les identifiants de façon chiffrée uniquement après validation réussie.
7. Proposer une case "Se souvenir de moi" (connexion auto au prochain lancement).

### 2.2 Endpoints Xtream Codes à implémenter (Retrofit interface)

Toutes les requêtes passent par `{base_url}/player_api.php?username={user}&password={pass}&action={action}`.

**Live TV**
- `action=get_live_categories` → liste des catégories de chaînes (`category_id`, `category_name`, `parent_id`)
- `action=get_live_streams&category_id={id}` → liste des chaînes d'une catégorie (`stream_id`, `name`, `stream_icon`, `epg_channel_id`, `num`, `added`)
- Construction de l'URL de lecture d'une chaîne live :
  ```
  {base_url}/live/{username}/{password}/{stream_id}.{extension}
  ```
  (extension généralement `m3u8` ou `ts` — utiliser `m3u8` par défaut, avec fallback `ts` si le flux échoue)

**VOD (Films)**
- `action=get_vod_categories`
- `action=get_vod_streams&category_id={id}` → liste des films (`stream_id`, `name`, `stream_icon`, `rating`, `added`, `container_extension`)
- `action=get_vod_info&vod_id={id}` → détails complets du film (synopsis, casting, durée, année, genre, bande-annonce si dispo dans `info`)
- URL de lecture :
  ```
  {base_url}/movie/{username}/{password}/{stream_id}.{container_extension}
  ```

**Séries**
- `action=get_series_categories`
- `action=get_series&category_id={id}` → liste des séries (`series_id`, `name`, `cover`, `plot`, `cast`, `rating`, `last_modified`)
- `action=get_series_info&series_id={id}` → retourne les saisons et épisodes (`episodes` groupés par numéro de saison, chaque épisode a `id`, `episode_num`, `title`, `container_extension`, `info` avec durée/synopsis)
- URL de lecture d'un épisode :
  ```
  {base_url}/series/{username}/{password}/{episode_id}.{container_extension}
  ```

### 2.3 Gestion technique des appels

- Timeout raisonnable (ex: 15s connexion, 20s lecture) avec retry configurable (1 retry automatique sur erreur réseau).
- Toutes les réponses JSON doivent être parsées de façon défensive (champs optionnels, valeurs parfois en string au lieu de int selon les panels Xtream — gérer les deux cas).
- Mettre en cache les listes (catégories, chaînes, films, séries) en base Room avec une logique de rafraîchissement (pull-to-refresh manuel + rafraîchissement auto si cache > X heures, configurable dans les paramètres).
- Ne jamais exposer username/password dans les logs, crash reports, ou captures d'écran (`FLAG_SECURE` optionnel sur l'écran de lecture si tu le juges pertinent).

---

## 3. Fonctionnalités et écrans attendus

1. **Écran de connexion** (décrit ci-dessus).
2. **Accueil / Dashboard** : accès rapide à Live TV, Films, Séries, Favoris, Recherche, Paramètres. Sur TV : navigation D-pad complète, mise en avant du focus.
3. **Live TV**
   - Liste des catégories (menu latéral sur TV, liste sur mobile).
   - Liste des chaînes par catégorie avec logo, nom, numéro.
   - Lecture plein écran avec ExoPlayer.
   - Zapping rapide (chaîne suivante/précédente au D-pad haut/bas pendant la lecture, ou boutons dédiés sur mobile).
   - Overlay pendant la lecture : nom de la chaîne, logo, indicateur de chargement/buffering, gestion des erreurs de flux avec retry.
4. **VOD Films**
   - Liste des catégories, puis grille des films (poster, titre, note).
   - Écran détail : synopsis, année, genre, casting, bouton lecture.
   - Lecture avec reprise de lecture (mémoriser la position via Room, proposer "reprendre" ou "recommencer").
5. **Séries**
   - Liste des catégories, puis grille des séries.
   - Écran détail série : synopsis, saisons disponibles.
   - Sélection saison → liste des épisodes avec titre, résumé, durée.
   - Lecture épisode avec reprise de lecture + bouton "épisode suivant" en fin de lecture.
6. **Recherche** : recherche unifiée (locale, sur le cache Room) sur chaînes, films et séries par nom.
7. **Favoris** : possibilité d'ajouter/retirer chaînes, films et séries en favoris (stockage local Room), écran dédié listant les favoris.
8. **Paramètres**
   - Déconnexion / changement de compte.
   - Fréquence de rafraîchissement du cache.
   - Choix de l'extension de flux préférée (m3u8/ts) si pertinent.
   - Effacer le cache local.
   - Informations du compte (statut, date d'expiration, connexions max) issues de `user_info`.

---

## 4. Exigences UX spécifiques Android TV

- Toute la navigation doit être 100% utilisable au D-pad (haut/bas/gauche/droite/OK/retour), sans nécessiter de télécommande tactile.
- États de focus visuellement clairs (agrandissement léger, bordure, ombre) sur chaque élément navigable.
- Pas d'éléments cliquables trop petits ou rapprochés (contraintes TV : cibles larges, safe zones respectées — éviter le contenu dans les 5% de marge écran).
- Chargement des listes progressif / pagination pour éviter les ralentissements sur les box TV bas de gamme (mémoire limitée).
- Thème sombre par défaut (adapté à un usage salon).

---

## 5. Gestion des erreurs (obligatoire, à traiter explicitement)

- Identifiants invalides → message explicite, retour à l'écran de connexion.
- Compte expiré (`status != Active` ou `exp_date` dépassée) → message clair avec date d'expiration si disponible.
- Serveur injoignable / timeout → message + bouton "réessayer".
- Flux vidéo qui ne se charge pas (format non supporté, chaîne down) → message + bouton "réessayer" + possibilité de revenir à la liste.
- Pas de connexion internet → écran dédié détectant l'état réseau (`ConnectivityManager`).

---

## 6. Permissions et manifeste

- `INTERNET`
- `ACCESS_NETWORK_STATE`
- Déclarer le support Android TV : `<uses-feature android:name="android.software.leanback" android:required="false" />` et `<uses-feature android:name="android.hardware.touchscreen" android:required="false" />`
- Icône et bannière dédiées pour le launcher Android TV (`android:banner`).

---

## 7. Livrables attendus de ta part

1. Arborescence complète du projet Android Studio (Gradle Kotlin DSL `.kts`).
2. Code source intégral de toutes les couches (data/domain/presentation), pas de pseudo-code ni de "TODO" sur les parties critiques (auth, appels API, lecteur vidéo).
3. Fichiers `build.gradle.kts` (projet + module app) avec toutes les dépendances nécessaires et versions précises.
4. `AndroidManifest.xml` complet.
5. Un README.md expliquant : comment builder le projet, comment configurer un serveur Xtream de test, les choix d'architecture faits, et les limitations connues.
6. Si des points de mon cahier des charges te semblent ambigus ou incomplets, pose-moi la question avant de faire une supposition qui engagerait une refonte importante.

---

## Rappel du périmètre strict

- ✅ Xtream Codes uniquement (`player_api.php`).
- ✅ Live TV + VOD Films + Séries.
- ❌ Pas de Catch-up/Timeshift.
- ❌ Pas de multi-comptes/profils utilisateurs.
- ❌ Pas de support M3U/Stalker/autres protocoles IPTV.
