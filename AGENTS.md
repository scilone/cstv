# AGENTS.md

Ce fichier contient les règles permanentes à suivre à chaque session de travail sur ce projet. Lis-le en entier avant toute modification de code.

## Documents de référence

- `prompt-app-iptv-xtream.md` : cahier des charges fonctionnel complet (fonctionnalités, endpoints Xtream Codes, écrans, gestion d'erreurs).
- `feuille-de-route-phases.md` : ordre de développement par phases. Ne développe jamais une fonctionnalité hors de la phase en cours sans confirmation explicite de l'utilisateur.
- `docs/design-reference/` : source de vérité visuelle pour la refonte UI/UX
  (Phases 46-54, voir feuille-de-route-phases.md). Contient le HTML/CSS brut
  exporté de la maquette Claude Design (couleurs, radius, typographie exacts)
  et, quand disponibles, des captures d'écran de référence par écran. À
  consulter systématiquement en amont de chaque phase 46-54 plutôt que de
  deviner les valeurs de design.

## Périmètre strict du projet

- Application Android/Android TV native Kotlin uniquement.
- Connexion **API Xtream Codes uniquement** (`player_api.php`). Aucun support M3U/M3U8 brut en tant que source, aucun Stalker Portal, aucun autre protocole IPTV.
- Fonctionnalités couvertes : Live TV, VOD Films, Séries, Favoris, Recherche locale, Paramètres.
- Explicitement hors périmètre, à ne jamais ajouter sans qu'on le demande : catch-up/timeshift, multi-comptes Xtream (plusieurs identifiants/mots de passe distincts), enregistrement (PVR), Chromecast, autre protocole IPTV, code PIN/restriction parentale par profil.
- Depuis la Phase 27 : profils **locaux** multiples (type Netflix) sur un **seul** compte Xtream sont dans le périmètre (favoris/historique/reprise de lecture séparés par profil ; catalogue/cache Room toujours partagé et non dupliqué). Ne pas confondre avec du multi-comptes Xtream, qui reste hors périmètre.
- Si une tâche demandée semble sortir de ce périmètre, signale-le avant de coder.

## Stack technique imposée (ne pas dévier sans validation)

- Kotlin uniquement, pas de Java.
- UI : Jetpack Compose (mobile) + Compose for TV (`androidx.tv:tv-material`, `tv-foundation`) pour Android TV.
- Architecture : Clean Architecture (`data` / `domain` / `presentation`) + MVVM.
- DI : Hilt.
- Réseau : Retrofit + OkHttp.
- Lecteur vidéo : ExoPlayer / Media3 (support HLS).
- Persistance : Room (cache API) + DataStore chiffré ou EncryptedSharedPreferences (identifiants Xtream).
- Images : Coil.
- Min SDK 21, target/compile SDK la dernière version stable disponible.

## Commandes de build et de test

```bash
# Build debug
./gradlew assembleDebug

# Lancer les tests unitaires
./gradlew testDebugUnitTest

# Lint
./gradlew lintDebug

# Installer sur un device/émulateur connecté
./gradlew installDebug
```

Avant de considérer une phase comme terminée, exécute `assembleDebug` et `lintDebug` et corrige toute erreur avant de livrer.

## Conventions de code

- Nommage : `PascalCase` pour les classes/composables, `camelCase` pour les fonctions/variables, `UPPER_SNAKE_CASE` pour les constantes.
- Un ViewModel par écran, jamais de logique métier directement dans un Composable.
- Les appels réseau et l'accès Room passent toujours par un Repository (`domain` définit l'interface, `data` l'implémente).
- Les modèles réseau (DTO Retrofit) ne doivent jamais fuiter dans la couche `presentation` : toujours mapper vers un modèle `domain`.
- Tout champ JSON potentiellement incohérent entre panels Xtream (int vs string) doit être parsé de façon défensive (voir cahier des charges section 2.3).
- Jamais de credentials (username/password Xtream) en dur dans le code, en log, ou en clair dans les fichiers de config versionnés.
- Compose : privilégier des composables stateless (state hoisting), préfixer les composables privés d'écran par le nom de l'écran (ex: `LiveTvChannelRow`).

## Structure de dossiers attendue

```
app/src/main/java/<package>/
├── data/
│   ├── remote/        (Retrofit API, DTOs)
│   ├── local/          (Room entities/DAO, DataStore)
│   └── repository/    (implémentations des repositories)
├── domain/
│   ├── model/          (modèles métier)
│   ├── repository/    (interfaces)
│   └── usecase/
└── presentation/
    ├── login/
    ├── home/
    ├── livetv/
    ├── vod/
    ├── series/
    ├── favorites/
    ├── search/
    ├── settings/
    └── player/
```

## Base de données Room (schéma et migrations)

- Base actuelle : `AppDatabase`, version **10** (voir `app/src/main/java/.../data/local/db/AppDatabase.kt`).
- **Pas de `fallbackToDestructiveMigration()`** depuis la Phase 27. `AppModule.provideDatabase()` utilise `.addMigrations(*ALL_MIGRATIONS)` (voir `data/local/db/Migrations.kt`). Le cache catalogue, les favoris, l'historique, les positions de lecture et les profils **doivent survivre** à une mise à jour de l'app.
- Règle impérative : toute nouvelle colonne/table/changement de clé primaire sur une entité Room doit être accompagné d'une `Migration(oldVersion, newVersion)` réelle dans `Migrations.kt`, ajoutée à `ALL_MIGRATIONS`, qui transforme le schéma en SQL brut (`CREATE TABLE`/`ALTER TABLE`/copie de données) sans perte. SQLite ne permettant pas d'ajouter une colonne à une clé primaire via `ALTER TABLE`, le pattern est : créer `<table>_new` avec le nouveau schéma, `INSERT INTO ... SELECT` depuis l'ancienne table (avec valeur de backfill pour la nouvelle colonne), `DROP TABLE` l'ancienne, `RENAME TO`. Voir `MIGRATION_9_10` comme référence.
- Le fallback destructif (`fallbackToDestructiveMigration()`) est réservé à un **breaking change majeur explicitement décidé avec l'utilisateur** (ex: refonte complète du schéma jugée trop coûteuse à migrer). Dans ce cas : le signaler clairement en amont, obtenir confirmation, documenter dans le commit et dans AGENTS.md, et prévoir de le retirer au bump suivant.
- Entités avec `profileId` dans leur clé primaire (données scopées par profil depuis la Phase 27) : `FavoriteEntity`, `PlaybackPositionEntity`, `RecentlyWatchedLiveEntity`. Les entités de catalogue (chaînes/films/séries/catégories/EPG) restent sans `profileId`, partagées entre tous les profils.
- Limite connue : le projet n'a pas d'infrastructure de test instrumenté (`androidTest`) pour valider les migrations avec `MigrationTestHelper`. Les migrations sont donc relues manuellement (SQL vérifié contre le schéma des entités) plutôt que testées automatiquement — à améliorer si le projet passe en production.

## Stratégie de tests

Chaque nouvelle fonctionnalité livrée dans une phase doit être accompagnée de tests, pas seulement de code fonctionnel. Priorité aux couches où un bug est coûteux ou silencieux :

**Couverture obligatoire (priorité haute)**
- **Parsing des réponses Xtream Codes** : tests unitaires sur le mapping DTO → modèle domain, avec des cas volontairement "sales" (champ en string au lieu d'int, champ manquant, champ null, tableau vide) pour chaque endpoint (`login`, `get_live_streams`, `get_vod_streams`, `get_vod_info`, `get_series`, `get_series_info`).
- **Authentification** : tests sur les cas identifiants invalides, compte expiré, timeout, réponse JSON malformée — vérifier que chaque cas produit l'état d'erreur attendu (pas de crash, pas de faux positif "connecté").
- **Construction des URLs de lecture** (live/movie/series) : test unitaire vérifiant le format exact de l'URL générée à partir des identifiants et de l'ID de flux.
- **Repositories** : tests avec un client HTTP fake/mock (pas d'appel réseau réel) vérifiant la logique de cache (Room) — quand on sert le cache, quand on rafraîchit.

**Couverture recommandée (priorité moyenne)**
- **ViewModels** des écrans principaux (Login, Live TV, VOD, Séries) : tests vérifiant que les états UI (loading/succès/erreur) changent correctement selon la réponse du repository (mocké).
- **Logique de reprise de lecture** (position mémorisée en Room) pour films/séries.
- **Favoris et recherche locale** : tests sur les opérations d'ajout/retrait et sur le filtrage.

**Non prioritaire / à ne pas sur-investir**
- Tests UI Compose bout en bout (screenshot/instrumentation) : uniquement si tu as le temps une fois le reste couvert, jamais au détriment des tests unitaires ci-dessus.
- Pas de test sur du code de layout pur sans logique (couleurs, dimensions).

**Pièges Mockito/Kotlin rencontrés (à réappliquer)**
- Une classe Kotlin `class Foo` avec une méthode retournant un type primitif (`Int`, `Boolean`, etc.) peut, une fois mockée, provoquer un `NullPointerException` sur unboxing (`Callable.call()` retourne `null`) selon la config Mockito du projet (pas de `mockito-inline`/`mockito-android` ici). Solution retenue : extraire une **interface** (`ProfileManager`) + une implémentation (`ProfileManagerImpl`), et mocker l'interface.
- Ne jamais nommer une fonction membre comme le getter JVM généré par une `val`/`StateFlow` du même type (ex: property `val activeProfileId: StateFlow<Int>` + fonction `fun getActiveProfileId(): Int` génèrent toutes les deux `getActiveProfileId()` côté bytecode → collision de signature à la compilation). Utilise un nom distinct pour la fonction (ex: `currentProfileId()`).
- Pour stubber un mock dont la méthode est aussi un `@JvmName`/accesseur ambigu, préfère `doReturn(x).whenever(mock).method()` à `whenever(mock.method()).thenReturn(x)` si Mockito lève `WrongTypeOfReturnValue`.

**Non-régression**
- Avant de livrer une phase, exécute `./gradlew testDebugUnitTest` en plus de `assembleDebug`.
- Si un test d'une phase précédente échoue suite à tes changements, corrige-le ou signale-le explicitement dans ta réponse — ne le supprime ni ne le désactive jamais pour faire passer le build sans validation explicite de ma part.
- Chaque fois qu'un bug Xtream Codes réel est découvert et corrigé (ex: un panel qui renvoie un champ dans un format inattendu), ajoute un test de non-régression correspondant.

## Gestion des erreurs (rappel)

Toute fonctionnalité réseau doit gérer explicitement : identifiants invalides, compte expiré, serveur injoignable/timeout, absence de connexion internet, flux vidéo non lisible. Ne jamais afficher de stack trace brute à l'utilisateur (voir cahier des charges section 5).

## Avant de conclure une tâche

1. Vérifie que le build passe (`assembleDebug`).
2. Écris les tests unitaires/fonctionnels de la feature livrée (voir section "Stratégie de tests"), puis exécute `./gradlew testDebugUnitTest` et corrige tout échec, y compris sur les tests des phases précédentes (non-régression).
3. Vérifie que tu n'as pas dépassé le périmètre de la phase demandée.
4. Signale dans ta réponse tout point du cahier des charges resté ambigu ou non traité.
5. Ne modifie pas les fichiers `prompt-app-iptv-xtream.md` ou `feuille-de-route-phases.md` sauf demande explicite.
6. Exécute `./gradlew assembleDebug` et donne-moi le chemin de l'APK généré.
7. Effectue systématiquement un commit Git, crée un tag Git associé (ex: v1.x.y respectant SemVer) et pousse-les (y compris les tags avec `git push origin --tags` ou de manière ciblée) vers le dépôt distant après chaque fonctionnalité ou phase terminée.

## Processus de Release et Tagging SemVer

Pour livrer une nouvelle version de l'application et générer un APK de production signé automatiquement :
1. Assure-toi que tous les tests passent (`./gradlew testDebugUnitTest`).
2. Crée et pousse un tag Git respectant SemVer (ex: `v1.0.0`) :
   ```bash
   git tag -a v1.0.0 -m "Release v1.0.0 - Fin de la Phase X"
   git push origin v1.0.0
   ```
   Avant de tagger, synchronise `versionCode`/`versionName` dans
   `app/build.gradle.kts` (Phase 39) : `versionName` = le tag sans le
   `v` (ex: `1.15.2`), `versionCode` = `major*10_000 + minor*100 + patch`
   (ex: v1.15.2 → 11502).
3. La pipeline **GitHub Actions** (`.github/workflows/release.yml`) interceptera automatiquement ce tag pour :
   - Compiler l'APK de release.
   - Le signer à l'aide des clés sécurisées de production fournies dans les secrets GitHub.
   - Créer une Release GitHub officielle.
   - Attacher l'APK de release signé à la Release.

Dernier tag poussé : `v1.19.1` (ajustements Live TV et accueil, suite Phase 56). Vérifie toujours `git tag --sort=-v:refname | head -1` avant de choisir le prochain numéro (patch pour un fix/correction, minor pour une nouvelle phase/fonctionnalité).
