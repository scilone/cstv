# CSTV — Android & Android TV IPTV App

**CSTV** est une application Android native (Mobile et Android TV) qui se connecte à un serveur de flux IPTV via l'**API Xtream Codes** et permet de visionner du contenu en direct (Live TV avec EPG), des films (VOD) et des séries.

---

## 🚀 Fonctionnalités

- **Connexion Xtream Codes** : authentification sécurisée (identifiants chiffrés), gestion d'erreurs complète (identifiants invalides, compte expiré, serveur injoignable).
- **Profils locaux multiples** (type Netflix) sur un seul compte Xtream : favoris, historique, reprises de lecture et préférences de pistes séparés par profil ; catalogue partagé.
- **Live TV** : catégories, zapping (swipe mobile / D-pad TV), EPG « en cours + suivant », chaînes récemment regardées, formats m3u8/ts avec fallback automatique.
- **Films (VOD) & Séries** : catalogue complet en cache Room (survit aux mises à jour via migrations), fiches détaillées enrichies (acteurs, réalisateur, genre, année), titres associés, lecture avec reprise de position, lecture auto de l'épisode suivant.
- **Accueil** : carrousel « Tendances du moment » (TMDB, rapproché du catalogue local par matching approximatif), « Continuer à regarder », favoris, dernières nouveautés, Top 10.
- **Recherche** : recherche globale plein texte (FTS) + **recherche avancée** (type Film/Série, catégorie, note minimum, plage d'années dynamique, genres en ET).
- **Téléchargements hors-ligne** : films et épisodes téléchargeables, lecture transparente depuis le cache.
- **Lecteur** : Media3/ExoPlayer + décodeurs FFmpeg logiciels (NextLib) pour EAC3/AC3/DTS, sélection pistes audio/sous-titres mémorisée par média, styles de sous-titres, PiP, redimensionnement.
- **Paramètres** : gestion des catégories (masquage/ordre par profil), synchronisation d'arrière-plan planifiée (WorkManager), gestion du cache, profils.

---

## 🛠️ Stack Technique

- **Langage** : Kotlin 100%
- **UI** : Jetpack Compose (Mobile) & Compose for TV (Android TV)
- **Architecture** : Clean Architecture + MVVM (Modèle-Vue-ViewModel)
- **Injection de dépendances** : Hilt
- **Réseau** : Retrofit + OkHttp (API Xtream Codes + API TMDB pour les tendances)
- **Persistance locale** : Room (cache catalogue, migrations non destructives) & DataStore / EncryptedSharedPreferences (identifiants chiffrés)
- **Lecteur vidéo** : Media3 / ExoPlayer (support HLS) + NextLib (décodeurs FFmpeg logiciels EAC3/AC3/DTS)
- **Tâches de fond** : WorkManager (sync planifiée du catalogue)
- **Images** : Coil Compose

---

## 🔑 Configuration TMDB (Tendances du moment)

L'affichage des "Tendances du moment" sur la page d'accueil utilise l'API de **The Movie Database (TMDB)**. L'obtention d'une clé API gratuite est requise pour activer cette fonctionnalité :

1. Créez un compte gratuit sur [The Movie Database (TMDB)](https://www.themoviedb.org/).
2. Accédez à vos paramètres de compte, section **API**, et demandez une clé API développeur.
3. Créez (ou ouvrez) le fichier `local.properties` à la racine de votre projet (ce fichier est exclu de Git).
4. Ajoutez votre clé de la manière suivante :
   ```properties
   TMDB_API_KEY=votre_cle_api_tmdb_ici
   ```
5. Recompilez le projet. Si la clé est absente ou vide, l'application se repliera automatiquement et silencieusement sur l'affichage du dernier média en cours de lecture, sans aucun dysfonctionnement.

---

## 📂 Architecture des Dossiers

L'application est structurée selon les principes de la **Clean Architecture** :

```
app/src/main/java/com/cstv/app/
├── IptvApplication.kt  (Point d'entrée de l'application, Hilt)
├── MainActivity.kt     (Activité principale adaptative Mobile/TV)
├── di/
│   └── AppModule.kt    (Module de dépendances Hilt global)
├── data/               (Couche d'implémentation des données)
│   ├── remote/         (Appels Retrofit : API Xtream + TMDB, DTOs, TypeAdapters Gson)
│   ├── local/          (Room Database, DAOs, Entités, migrations, DataStore chiffré)
│   ├── download/       (Téléchargements hors-ligne : cache Media3, utilitaires)
│   ├── worker/         (Workers WorkManager : sync planifiée du catalogue)
│   └── repository/     (Implémentations des interfaces de repository)
├── domain/             (Couche de logique métier pure - indépendante d'Android)
│   ├── model/          (Modèles métier + objets purs testables : parsers, matchers)
│   ├── repository/     (Définitions des interfaces de repository)
│   └── usecase/        (Cas d'utilisation métier spécifiques)
└── presentation/       (Couche UI - ViewModels & Composables Jetpack Compose)
    ├── components/     (Composants partagés : sélecteur de catégorie, champs)
    ├── navigation/     (AppNavGraph, navigation-compose côté mobile)
    ├── login/          (Écran d'authentification Xtream Codes)
    ├── profile/        (Sélection et gestion des profils locaux)
    ├── home/           (Accueil : tendances, reprises, favoris, top 10)
    ├── livetv/         (Navigation des catégories & chaînes en direct)
    ├── vod/            (Films : grilles, détails, lecteur)
    ├── series/         (Séries : saisons, épisodes, détails, lecteur)
    ├── favorites/      (Gestion des favoris)
    ├── search/         (Recherche globale FTS + recherche avancée)
    ├── downloads/      (Écran des téléchargements hors-ligne)
    ├── settings/       (Options, gestion des catégories, cache, sync)
    ├── player/         (Lecteur Live TV + composants UI de lecteur partagés)
    └── theme/          (Couleurs, typographies, thème Compose)
```

---

## 📋 Gestion du Projet & Organisation du Backlog

Le développement de l'application suit un flux de travail agile strict conçu pour maintenir les sessions de développement d'IA extrêmement rapides et efficaces :

* **Backlog Actif** :
  * `docs/evolutions-fonctionnelles.md` (Préfixe **F-X**) : Nouvelles fonctionnalités de l'application.
  * `docs/evolutions-techniques.md` (Préfixe **T-X**) : Chantiers d'architecture, refactorings, sécurité et dettes.
  * `docs/bugs.md` (Préfixe **B-X**) : Rapports d'anomalies de comportement et régressions ouvertes.

* **Dossier d'Archives** (`docs/archive/`) :
  * `docs/archive/evolutions-fonctionnelles-terminees.md` : Toutes les fonctionnalités fonctionnelles terminées et validées.
  * `docs/archive/evolutions-techniques-terminees.md` : Toutes les corrections, refactorings et optimisations techniques validés.
  * `docs/archive/bugs-termines.md` : Toutes les corrections de bugs archivées et validées.

* **Workflow d'IA systématique** : Dès qu'une tâche active (**F**, **T** ou **B**) est livrée et validée, sa description/son prompt est coupé-collé de son fichier actif vers le fichier d'archive correspondant afin de garder les backlogs actifs ultra-courts, ce qui évite toute surcharge cognitive et de contexte pour l'IA.

---

## 💻 Instructions de Build et d'Exécution

Assurez-vous d'avoir configuré le JDK 17 ou supérieur pour l'exécution des commandes.

### 1. Cloner et préparer le projet
Le projet intègre un Gradle Wrapper préconfiguré. Rendez le script exécutable si nécessaire :
```bash
chmod +x gradlew
```

### 2. Compiler l'application en mode Debug
```bash
./gradlew assembleDebug
```

### 3. Lancer les tests unitaires
```bash
./gradlew testDebugUnitTest
```

### 4. Lancer l'analyse statique du code (Lint)
```bash
./gradlew lintDebug
```

### 5. Installer l'application sur un appareil/émulateur connecté
```bash
./gradlew installDebug
```

---

## 📺 Spécificités Android TV
- **Focus visuel clair** : Chaque composant interactif réagit visuellement lors du focus par la télécommande (effet d'échelle, bordure lumineuse, ombres).
- **Navigation D-pad** : L'interface utilise `Compose for TV` pour s'assurer que tous les contrôles sont navigables de manière fluide avec les touches directionnelles `Haut/Bas/Gauche/Droite/OK/Retour`.
- **Bannière TV** : Une bannière TV (`@drawable/ic_tv_banner`) est configurée pour apparaître correctement dans le launcher Android TV / Leanback.
