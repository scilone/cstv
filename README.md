# IPTV Xtream Codes - Android & Android TV App (POC)

Ce projet est un Proof of Concept (POC) d'une application Android native (Mobile et Android TV) permettant de se connecter à un serveur de flux IPTV via l'**API Xtream Codes** et de visionner du contenu en direct (Live TV), des films (VOD) et des séries.

---

## 🚀 État d'avancement : Phase 0 — Initialisation

La **Phase 0** est entièrement réalisée et validée :
- **Structure Clean Architecture** mise en place (`data` / `domain` / `presentation`).
- **Configuration Hilt** opérationnelle (Application class `@HiltAndroidApp`, module de base, annotation `@AndroidEntryPoint` sur l'activité principale).
- **Dépendances de base** déclarées et prêtes (Jetpack Compose, Compose for TV, Room, Retrofit, OkHttp, Coil, Media3/ExoPlayer, DataStore, Security Crypto, tests unitaires).
- **Support Android TV** configuré dans `AndroidManifest.xml` (permissions, fonctionnalités matérielles optionnelles, bannière TV, intent-filters dédiés).
- **Écran de démarrage adaptatif** (mobile/TV) fonctionnel :
  - Détection automatique du type de périphérique (`UiModeManager`).
  - Interface Jetpack Compose optimisée pour smartphone.
  - Interface Compose for TV optimisée pour la navigation au D-pad de la télécommande.

---

## 🛠️ Stack Technique

- **Langage** : Kotlin 100%
- **UI** : Jetpack Compose (Mobile) & Compose for TV (Android TV)
- **Architecture** : Clean Architecture + MVVM (Modèle-Vue-ViewModel)
- **Injection de dépendances** : Hilt
- **Réseau** : Retrofit + OkHttp
- **Persistance locale** : Room (cache local) & DataStore / EncryptedSharedPreferences (identifiants chiffrés)
- **Lecteur vidéo** : Media3 / ExoPlayer (avec support HLS)
- **Images** : Coil Compose

---

## 📂 Architecture des Dossiers

L'application est structurée selon les principes de la **Clean Architecture** :

```
app/src/main/java/com/poc/iptvxtream/
├── IptvApplication.kt  (Point d'entrée de l'application, Hilt)
├── MainActivity.kt     (Activité principale adaptative Mobile/TV)
├── di/
│   └── AppModule.kt    (Module de dépendances Hilt global)
├── data/               (Couche d'implémentation des données)
│   ├── remote/         (Appels Retrofit, DTOs de l'API Xtream)
│   ├── local/          (Room Database, DAOs, Entités, DataStore chiffré)
│   └── repository/     (Implémentations des interfaces de repository)
├── domain/             (Couche de logique métier pure - indépendante d'Android)
│   ├── model/          (Modèles de données du domaine)
│   ├── repository/     (Définitions des interfaces de repository)
│   └── usecase/        (Cas d'utilisation métier spécifiques)
└── presentation/       (Couche UI - ViewModels & Composables Jetpack Compose)
    ├── login/          (Écran d'authentification Xtream Codes)
    ├── home/           (Dashboard / Menu d'accueil)
    ├── livetv/         (Navigation des catégories & chaînes en direct)
    ├── vod/            (Films, grilles et détails)
    ├── series/         (Séries, saisons et épisodes)
    ├── favorites/      (Gestion des favoris)
    ├── search/         (Recherche unifiée locale)
    ├── settings/       (Options & Configuration du cache)
    └── player/         (Lecteur vidéo ExoPlayer Mobile & TV)
```

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
