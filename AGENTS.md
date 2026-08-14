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
- **Navigation unifiée :** L'application utilise désormais un seul système de navigation via `AppNavGraph` (navigation-compose, `presentation/navigation/NavGraph.kt`) partagé entre Mobile et TV. L'ancien double système (`AppScreen` + `when` manuel) est obsolète et a été entièrement supprimé.

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

- Base actuelle : `AppDatabase`, version **27** (voir `app/src/main/java/.../data/local/db/AppDatabase.kt`). Les migrations 25 → 26 ajoutent `ProfileEntity.remoteId` (index unique) et 26 → 27 créent `ProfileSyncStateEntity`, indexé par `profileId`, pour les ETags et bases de fusion cloud.
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
- **Exclusion des tests manuels ou sur device connecté** : TOUS les tests requis pour valider une tâche doivent être entièrement automatisés (tests unitaires locaux JVM exécutables via `./gradlew testDebugUnitTest`). Si une tâche ou une vérification requiert un appareil physique connecté (device), un émulateur actif, ou des tests utilisateurs manuels, elle est exclue et ne doit PAS être prise en compte dans les critères de validation finale de l'agent.

**Pièges Mockito/Kotlin rencontrés (à réappliquer)**
- Classe Kotlin `class Foo` avec méthode retournant type primitif (`Int`, `Boolean`, etc.) peut, une fois mockée, provoquer `NullPointerException` unboxing (`Callable.call()` retourne `null`) selon config Mockito projet (pas `mockito-inline`/`mockito-android` ici). Solution retenue : extraire **interface** (`ProfileManager`) + implémentation (`ProfileManagerImpl`), mocker interface.
- Jamais nommer fonction membre comme getter JVM généré par `val`/`StateFlow` même type (ex: property `val activeProfileId: StateFlow<Int>` + fonction `fun getActiveProfileId(): Int` génèrent toutes deux `getActiveProfileId()` côté bytecode → collision signature compilation). Utilise nom distinct fonction (ex: `currentProfileId()`).
- Pour stubber mock dont méthode aussi `@JvmName`/accesseur ambigu, préfère `doReturn(x).whenever(mock).method()` à `whenever(mock.method()).thenReturn(x)` si Mockito lève `WrongTypeOfReturnValue`.

**Boucles infinies de tests — règle absolue (déjà rencontré plusieurs fois)**

Une tâche périodique inconditionnelle (`while (true) { delay(...) }`, `flow { while (true) { emit(); delay() } }`) lancée dans un `init` de ViewModel garde en permanence une tâche planifiée sur le scheduler virtuel. Conséquence : `advanceUntilIdle()` **et** le drainage final de `runTest` bouclent pour toujours. Cette boucle de drainage n'est pas suspendable, donc **ni le timeout interne de `runTest`, ni une règle JUnit `Timeout` ne l'interrompent** : `./gradlew testDebugUnitTest` gèle indéfiniment, sans échec ni message.

- **Jamais** de tâche périodique inconditionnelle dans un `init` de ViewModel. Conditionne-la à `_state.subscriptionCount > 0` (`collectLatest`) : le sondage ne tourne que quand l'écran observe l'état — plus rien n'est planifié en test, et la prod évite un polling inutile en arrière-plan. Modèle de référence : le ticker EPG de `HomeViewModel`.
- Ne compte pas sur `viewModelScope.cancel()` en fin de test : le `@After` s'exécute **après** le retour de `runTest`, donc trop tard pour éviter le gel.
- Garde-fous en place, à ne pas retirer :
  - `tasks.withType<Test> { timeout }` dans `app/build.gradle.kts` (le build meurt au bout de 10 min au lieu de geler) ;
  - règle `@get:Rule val globalTimeout = Timeout.seconds(60)` dans chaque `presentation/**ViewModelTest.kt` (nomme le test coupable dans le rapport).
- Si un test gèle malgré tout : `jstack <pid du Gradle Test Executor>` puis lire la pile du thread `Test worker` — elle donne directement la classe et la méthode fautives.
- Attention aussi aux boucles de pagination `while (true) { ...; if (page.size < N) break }` (repositories) : un mock stubbé avec `any()` renvoyant une page pleine boucle sans fin. Stubbe toujours une dernière page plus courte.

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
6. Effectue systématiquement commit Git, puis livre la version avec `scripts/release-local.sh` (voir ci-dessous) : c'est lui qui pose le tag SemVer et pousse. Ne tague jamais à la main.

## Processus de Release et Tagging SemVer

La release se fabrique **sur le poste de développement**, plus en CI : depuis
v1.64.13, il n'y a plus de workflow GitHub Actions (dépôt privé, quota de
minutes, et 190 lignes de YAML dont la moitié n'existait que pour contourner
les contraintes de cache du runner). L'historique git garde l'ancien
`.github/workflows/release.yml` si besoin.

Pour livrer une nouvelle version :

1. Synchronise `versionCode`/`versionName` dans `app/build.gradle.kts` :
   `versionName` = le tag sans le `v` (ex: `1.15.2`), `versionCode` =
   `major*10_000 + minor*100 + patch` (ex: v1.15.2 → 11502). Vérifie toujours
   `git tag --sort=-v:refname | head -1` pour choisir le numéro suivant (patch
   pour un correctif, minor pour une phase/fonctionnalité) : ne te fie jamais à
   un numéro écrit dans la documentation, il périme vite.
2. Committe.
3. Lance :
   ```bash
   scripts/release-local.sh              # vérifie, compile, tague, pousse, publie
   scripts/release-local.sh --no-publish # s'arrête après l'APK, sans rien pousser
   ```

Le script refuse de continuer si l'arbre de travail est sale, si la branche
n'est pas `main`, si `versionCode` et `versionName` divergent, ou si le tag
existe déjà (localement ou sur `origin`) — le tag n'est posé qu'après une
compilation réussie. Il enchaîne `testDebugUnitTest`, `lintDebug`,
`:app:assembleRelease`, vérifie que l'APK est bien signé, pousse `main` et le
tag, puis crée la Release GitHub avec notes générées et APK attaché
(via `gh`, qui doit être authentifié).

## Déploiement du backend (alwaysdata)

La release Android ne déploie **pas** le backend. Après avoir committé et poussé
les modifications backend sur `main`, déploie explicitement avec :

```bash
scripts/deploy-backend.sh --dry-run # prévisualisation rsync, sans modification distante
scripts/deploy-backend.sh           # rsync + composer + migrations + healthcheck
```

Le script cible `cstv@ssh-cstv.alwaysdata.net:www`, exclut `.env` et `vendor/`,
puis charge `~/.cstv-production.env` sur le serveur, exécute `composer install
--no-dev --optimize-autoloader --no-interaction` et `php bin/migrate`. Il vérifie
enfin `https://cstv.alwaysdata.net/health` et les en-têtes de sécurité. Les
secrets et variables de production restent exclusivement dans ce fichier distant
(voir `backend/.env.example`) ; ne jamais les synchroniser ni les afficher.

`--skip-composer` ne s'utilise que si les dépendances distantes sont déjà à jour.
Ne jamais lancer `bin/fixtures` en production : le script de déploiement ne le
fait volontairement pas.

### Signature

Les paramètres de signature vivent dans `keystore.properties` à la racine,
jamais versionné — voir `keystore.properties.example`. Les variables
d'environnement (`KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
`KEY_PASSWORD`) gardent la priorité, pour qu'une machine de build automatisée
reste possible sans toucher au fichier.

**Le keystore est irremplaçable.** Perdu, plus aucune mise à jour ne s'installe
par-dessus l'APK existant : il faut désinstaller puis réinstaller sur chaque
appareil. Garde une sauvegarde de `app-release.jks` hors de cette machine (le
secret GitHub `KEYSTORE_BASE64` en tient lieu tant qu'il n'est pas supprimé).
