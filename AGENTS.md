# AGENTS.md

Fichier = règles permanentes chaque session travail projet. Lis tout avant modif code.

## Documents de référence et Organisation du Backlog

Le projet utilise un workflow standard de développement basé sur l'IA, décrit en détail dans le fichier **`AI_DEVELOPMENT_WORKFLOW.md`**.

- **Structure du Backlog** : Tous les éléments de développement (Features, Bugs, Technical) se trouvent sous le dossier **`ai/`** :
  - `ai/features/` : Nouvelles fonctionnalités du projet (format de fichier : `Fx-name.md`, ex : `F1-name.md`).
  - `ai/bugs/` : Rapports de bugs et anomalies à corriger (format de fichier : `Bx-name.md`, ex : `B1-name.md`).
  - `ai/technical/` : Tâches d'architecture, refactorings et dettes techniques (format de fichier : `Tx-name.md`, ex : `T3-name.md`).
- **Dossiers Archives** : Chaque catégorie de tâche possède son sous-dossier `archive/` contenant les tâches déjà réalisées (ex: `ai/features/archive/`).
- `docs/design-reference/` : source vérité visuelle refonte UI/UX
  (Phases 46-54). HTML/CSS brut exporté maquette Claude Design (couleurs, radius, typo exacts) + captures écran référence par écran quand dispo. Consulter systématiquement avant chaque phase plutôt que deviner valeurs design.

### 🔄 Flux de Travail de l'IA (AI Development Workflow)
Pour éviter la surcharge cognitive et de contexte, respecte scrupuleusement le cycle de vie décrit dans `AI_DEVELOPMENT_WORKFLOW.md` :
1. **Source de vérité unique** : Chaque tâche a son propre fichier Markdown (`ai/{category}/{id}-{name}.md`) qui contient tout son contexte (spécifications, plan de développement, notes, review, etc.).
2. **Cycle de vie standard** : Chaque élément évolue par étapes :
   `IDEA ➔ ANALYSIS ➔ SPECIFICATION ➔ ARCHITECTURE ➔ TASK BREAKDOWN ➔ IMPLEMENTATION ➔ REVIEW ➔ FIXES ➔ VALIDATION ➔ DOCUMENTATION ➔ RELEASE ➔ ARCHIVE`
3. **Commandes d'interaction** : Pour piloter le développement, utilise des instructions explicites comme :
   - *"Exécute l'étape 1 de F1"*
   - *"Exécute l'étape 5 de T3"*
4. **Archivage** : Une fois la tâche entièrement terminée, validée et livrée (avec commits Git/tag SemVer), déplace le fichier de la tâche vers son sous-dossier `archive/` respectif.

## Périmètre strict du projet

- App Android/Android TV native Kotlin uniquement.
- Connexion **API Xtream Codes uniquement** (`player_api.php`) source IPTV. Zéro support M3U/M3U8 brut source, zéro Stalker Portal, zéro autre protocole IPTV.
- Fonctionnalités couvertes : Live TV (avec EPG), VOD Films, Séries, Favoris, Recherche locale (FTS + avancée), Téléchargements hors-ligne, Profils locaux, Paramètres.
- **Exceptions réseau validées** en plus de Xtream : **TMDB** (tendances Accueil, Feature F1) et **YouTube** (API, métadonnées, intégration du lecteur et lecture de contenus). Leur usage n'est pas limité aux trailers afin de permettre les évolutions futures. Toute clé éventuelle reste dans `local.properties` ou les secrets CI (jamais versionnée), avec repli silencieux si elle est absente ou si le service est indisponible.
- Explicitement hors périmètre, jamais ajouter sans demande explicite PO : catch-up/timeshift, multi-comptes Xtream (plusieurs identifiants distincts), enregistrement (PVR), autre protocole IPTV, code PIN/restriction parentale par profil.
- **Chromecast : tenté (F4) puis retiré définitivement** (revert complet v1.47.10) : Default Media Receiver Google Cast décode pas AC3/EAC3/DTS, codecs fréquents ce catalogue (raison d'être NextLib côté local) → cast vidéo sans son, non corrigeable côté app. Pas re-proposer sans transcoding serveur.
- Depuis Phase 27 : profils **locaux** multiples (type Netflix) sur **un seul** compte Xtream dans périmètre (favoris/historique/reprise lecture séparés par profil ; catalogue/cache Room toujours partagé, non dupliqué). Pas confondre avec multi-comptes Xtream, hors périmètre.
- Tâche demandée semble sortir périmètre → signale avant coder.

## Stack technique imposée (pas dévier sans validation)

- Kotlin uniquement, pas Java.
- UI : Jetpack Compose (mobile) + Compose for TV (`androidx.tv:tv-material`, `tv-foundation`) Android TV.
- Architecture : Clean Architecture (`data` / `domain` / `presentation`) + MVVM.
- DI : Hilt.
- Réseau : Retrofit + OkHttp (instances séparées Xtream / TMDB / éventuelle API YouTube) ; lecteur YouTube intégré via l'API IFrame ou une bibliothèque Android dédiée validée.
- Lecteur vidéo : ExoPlayer / Media3 (support HLS) + NextLib (`nextlib-media3ext`, décodeurs FFmpeg logiciels EAC3/AC3/DTS — version alignée media3).
- Persistance : Room (cache API) + DataStore chiffré ou EncryptedSharedPreferences (identifiants Xtream).
- Tâches fond : WorkManager (sync planifiée catalogue).
- Images : Coil.
- Min SDK 21, target/compile SDK dernière stable dispo.
- Build release : R8/minify actif. **Toute nouvelle interface Retrofit doit avoir règle `-keep` dans `proguard-rules.pro`** (cf. XtreamApiService/TmdbApiService — sans elle, call adapter générique casse release, crash exécution, invisible debug).

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

Avant considérer phase terminée, exécute `assembleDebug` + `lintDebug`, corrige toute erreur avant livrer.

## Conventions de code

- Nommage : `PascalCase` classes/composables, `camelCase` fonctions/variables, `UPPER_SNAKE_CASE` constantes.
- Un ViewModel par écran, jamais logique métier direct dans Composable.
- Appels réseau + accès Room toujours via Repository (`domain` définit interface, `data` implémente).
- Modèles réseau (DTO Retrofit) jamais fuiter couche `presentation` : toujours mapper vers modèle `domain`.
- Tout champ JSON potentiellement incohérent entre panels Xtream (int vs string) doit parser façon défensive (voir cahier charges section 2.3).
- Jamais credentials (username/password Xtream) en dur code, en log, ou clair fichiers config versionnés.
- Compose : privilégier composables stateless (state hoisting), préfixer composables privés écran par nom écran (ex: `LiveTvChannelRow`).
- ⚠️ **Piège : double système navigation.** Mobile passe par `AppNavGraph` (navigation-compose, `presentation/navigation/NavGraph.kt`) mais TV passe navigation manuelle enum `AppScreen` + `when` dans `MainActivity.kt`. **Tout nouvel écran doit câbler DANS LES DEUX**, sinon apparaît qu'une plateforme. Unification prévue (voir backlog technique sous `ai/technical/`).

## Structure de dossiers attendue

```
app/src/main/java/com/cstv/app/
├── data/
│   ├── remote/        (Retrofit API Xtream + TMDB + éventuelle API YouTube, DTOs, TypeAdapters Gson)
│   ├── local/          (Room entities/DAO/migrations, DataStore)
│   ├── download/      (téléchargements hors-ligne, cache Media3)
│   ├── worker/        (WorkManager : sync planifiée)
│   └── repository/    (implémentations des repositories)
├── domain/
│   ├── model/          (modèles métier + objets purs testables : parsers/matchers)
│   ├── repository/    (interfaces)
│   └── usecase/
└── presentation/
    ├── components/    (composants partagés inter-écrans)
    ├── navigation/    (AppNavGraph — navigation-compose, côté mobile)
    ├── login/
    ├── profile/
    ├── home/          (+ home/components)
    ├── livetv/        (+ livetv/components)
    ├── vod/
    ├── series/
    ├── favorites/
    ├── search/
    ├── downloads/
    ├── settings/
    ├── player/        (lecteur Live + composants UI de lecteur partagés)
    └── theme/
```

## Base de données Room (schéma et migrations)

- Base actuelle : `AppDatabase`, version **16** (voir `app/src/main/java/.../data/local/db/AppDatabase.kt`).
- **Pas de `fallbackToDestructiveMigration()`** depuis Phase 27. `AppModule.provideDatabase()` utilise `.addMigrations(*ALL_MIGRATIONS)` (voir `data/local/db/Migrations.kt`). Cache catalogue, favoris, historique, positions lecture, profils **doivent survivre** mise à jour app.
- Règle impérative : toute nouvelle colonne/table/changement clé primaire sur entité Room doit accompagner `Migration(oldVersion, newVersion)` réelle dans `Migrations.kt`, ajoutée à `ALL_MIGRATIONS`, transformant schéma en SQL brut (`CREATE TABLE`/`ALTER TABLE`/copie données) sans perte. SQLite permet pas ajouter colonne à clé primaire via `ALTER TABLE`, pattern : créer `<table>_new` nouveau schéma, `INSERT INTO ... SELECT` depuis ancienne table (valeur backfill nouvelle colonne), `DROP TABLE` ancienne, `RENAME TO`. Voir `MIGRATION_9_10` référence.
- Fallback destructif (`fallbackToDestructiveMigration()`) réservé **breaking change majeur explicitement décidé avec utilisateur** (ex: refonte complète schéma jugée trop coûteuse migrer). Ce cas : signaler clairement en amont, obtenir confirmation, documenter commit + AGENTS.md, prévoir retirer bump suivant.
- Entités avec `profileId` clé primaire (données scopées par profil depuis Phase 27) : `FavoriteEntity`, `PlaybackPositionEntity`, `RecentlyWatchedLiveEntity`, `TrackPreferenceEntity`, `CategoryPreferenceEntity`. Entités catalogue (chaînes/films/séries/catégories/EPG) restent sans `profileId`, partagées tous profils.
- Limite connue : projet a pas infrastructure test instrumenté (`androidTest`) valider migrations avec `MigrationTestHelper`. Migrations donc relues manuellement (SQL vérifié contre schéma entités) plutôt testées automatiquement — améliorer si projet passe production.

## Stratégie de tests

Chaque nouvelle fonctionnalité livrée phase doit accompagner tests, pas juste code fonctionnel. Priorité couches où bug coûteux ou silencieux :

**Couverture obligatoire (priorité haute)**
- **Parsing réponses Xtream Codes** : tests unitaires mapping DTO → modèle domain, cas volontairement "sales" (champ string au lieu int, champ manquant, null, tableau vide) pour chaque endpoint (`login`, `get_live_streams`, `get_vod_streams`, `get_vod_info`, `get_series`, `get_series_info`).
- **Authentification** : tests cas identifiants invalides, compte expiré, timeout, réponse JSON malformée — vérifier chaque cas produit état erreur attendu (pas crash, pas faux positif "connecté").
- **Construction URLs lecture** (live/movie/series) : test unitaire vérifiant format exact URL générée depuis identifiants + ID flux.
- **Repositories** : tests avec client HTTP fake/mock (pas appel réseau réel) vérifiant logique cache (Room) — quand sert cache, quand rafraîchit.

**Couverture recommandée (priorité moyenne)**
- **ViewModels** écrans principaux (Login, Live TV, VOD, Séries) : tests vérifiant états UI (loading/succès/erreur) changent correct selon réponse repository (mocké).
- **Logique reprise lecture** (position mémorisée Room) films/séries.
- **Favoris + recherche locale** : tests opérations ajout/retrait + filtrage.

**Non prioritaire / pas sur-investir**
- Tests UI Compose bout en bout (screenshot/instrumentation) : que si temps une fois reste couvert, jamais au détriment tests unitaires ci-dessus.
- Pas test code layout pur sans logique (couleurs, dimensions).

**Pièges Mockito/Kotlin rencontrés (à réappliquer)**
- Classe Kotlin `class Foo` avec méthode retournant type primitif (`Int`, `Boolean`, etc.) peut, une fois mockée, provoquer `NullPointerException` unboxing (`Callable.call()` retourne `null`) selon config Mockito projet (pas `mockito-inline`/`mockito-android` ici). Solution retenue : extraire **interface** (`ProfileManager`) + implémentation (`ProfileManagerImpl`), mocker interface.
- Jamais nommer fonction membre comme getter JVM généré par `val`/`StateFlow` même type (ex: property `val activeProfileId: StateFlow<Int>` + fonction `fun getActiveProfileId(): Int` génèrent toutes deux `getActiveProfileId()` côté bytecode → collision signature compilation). Utilise nom distinct fonction (ex: `currentProfileId()`).
- Pour stubber mock dont méthode aussi `@JvmName`/accesseur ambigu, préfère `doReturn(x).whenever(mock).method()` à `whenever(mock.method()).thenReturn(x)` si Mockito lève `WrongTypeOfReturnValue`.

**Non-régression**
- Avant livrer phase, exécute `./gradlew testDebugUnitTest` en plus `assembleDebug`.
- Si test phase précédente échoue suite tes changements, corrige-le ou signale explicitement ta réponse — jamais supprimer ni désactiver pour faire passer build sans validation explicite de ma part.
- Chaque fois bug Xtream Codes réel découvert et corrigé (ex: panel renvoie champ format inattendu), ajoute test non-régression correspondant.

## Gestion des erreurs (rappel)

Toute fonctionnalité réseau doit gérer explicitement : identifiants invalides, compte expiré, serveur injoignable/timeout, absence connexion internet, flux vidéo non lisible. Jamais afficher stack trace brute utilisateur (voir cahier charges section 5).

## Avant de conclure une tâche

1. Vérifie build passe (`assembleDebug`).
2. Écris tests unitaires/fonctionnels feature livrée (voir section "Stratégie de tests"), puis exécute `./gradlew testDebugUnitTest`, corrige tout échec, y compris tests phases précédentes (non-régression).
3. Vérifie pas dépassé périmètre phase demandée.
4. Signale ta réponse tout point cahier charges resté ambigu ou non traité.
5. Pour les évolutions fonctionnelles, techniques ou correctifs de bugs, respecte scrupuleusement le workflow d'archivage systématique vers les dossiers `archive/` correspondants sous `ai/` dès la tâche livrée et validée.
6. Effectue systématiquement commit Git, crée tag Git associé (ex: v1.x.y respectant SemVer), pousse (y compris tags avec `git push origin --tags` ou ciblé) vers dépôt distant après chaque fonctionnalité/phase terminée.

## Processus de Release et Tagging SemVer

Pour livrer nouvelle version app, générer APK production signé automatiquement :
1. Assure-toi tous tests passent (`./gradlew testDebugUnitTest`).
2. Crée et pousse tag Git respectant SemVer (ex: `v1.0.0`) :
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

Ne te fie jamais à un numéro de version écrit dans la documentation (il périme vite) : vérifie toujours `git tag --sort=-v:refname | head -1` avant de choisir le prochain numéro (patch pour un fix/correction, minor pour une nouvelle phase/fonctionnalité), et synchronise `versionCode`/`versionName` dans `app/build.gradle.kts` avant de tagger.
