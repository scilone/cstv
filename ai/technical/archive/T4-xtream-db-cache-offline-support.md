# T4 - Persistance locale et cache base de données de l'API Xtream pour mode hors-ligne

## Informations générales

Type:
Technical

Status:
RELEASED

Created:
2026-07-25

Target version:
v1.55.0

Version:
v1.55.0

Date:
2026-07-25

---

# 1. Description

Cette évolution technique vise à introduire un stockage et cache database persistants pour l'intégralité des informations récupérées de l'API Xtream Codes (Live TV, VOD Films, Séries, EPG, etc.) dans Room. L'objectif est double : d'une part, limiter au strict nécessaire les requêtes réseau vers le panel Xtream de l'utilisateur (réduisant drastiquement le trafic et prévenant les bannissements de comptes pour excès de connexions simultanées), et d'autre part, proposer un **mode déconnecté (offline)** robuste. Dans ce mode hors-ligne, 80 % des fonctionnalités de l'application (navigation dans le catalogue, profils locaux, favoris, recherche locale FTS, historique, détails des fiches) restent entièrement opérationnels, seule la lecture de flux en direct ou à la demande n'étant pas disponible sans connexion.

---

# 2. Contexte

Actuellement, l'application effectue des appels périodiques ou à la demande vers le serveur Xtream Codes pour charger le catalogue (Live TV, VOD, Séries, catégories). Bien que des caches en mémoire ou partiels existent, un redémarrage de l'application ou un changement d'écran peut déclencher des rechargements réseau lourds de plusieurs milliers d'entrées.
- **Surcharge réseau** : Les serveurs IPTV (Xtream Codes) sont sensibles au nombre de connexions simultanées et aux requêtes volumineuses fréquentes, ce qui peut conduire à des blocages temporaires ou définitifs de comptes utilisateurs.
- **Absence de mode hors-ligne** : En l'absence de réseau internet, l'application se bloque sur l'écran d'accueil ou de connexion, rendant impossible la consultation de l'historique, des favoris ou des informations des fiches médias déjà téléchargées (Offline VOD) ou simplement indexées.
- **Expérience utilisateur dégradée** : Le temps d'attente lors de la synchronisation initiale du catalogue au démarrage ralentit l'accès à l'application.

L'introduction d'un cache de base de données unifié sous Room, servant de source unique de vérité, permettra de basculer de manière transparente entre un mode synchronisé en arrière-plan et un mode de lecture locale déconnecté.

---

# 3. Objectif

Permettre à l'application d'enregistrer l'intégralité de la structure et du contenu du catalogue Xtream Codes en base de données Room locale pour :
- Minimiser les requêtes réseau vers l'API Xtream (uniquement lors de synchronisations programmées ou d'actions manuelles explicites).
- Permettre à l'utilisateur d'ouvrir l'application, naviguer dans tout le catalogue, modifier ses favoris locaux, gérer ses profils, et effectuer des recherches FTS avancées même lorsqu'il est complètement déconnecté (mode avion / pas d'internet).
- Assurer un démarrage quasi instantané de l'application en servant immédiatement le contenu de la base de données locale pendant qu'une synchronisation en tâche de fond discrète vérifie les nouveautés si le réseau est actif.

---

# 4. Hypothèses

- **Room comme unique source de vérité** : Les Repositories ne retourneront plus jamais directement des données réseau brutes à la couche de présentation. Tous les appels réseau Xtream écriront d'abord dans Room, et la couche UI observera des `Flow` de base de données.
- **Optimisation des requêtes de synchronisation** : L'API Xtream Codes supporte des requêtes partielles (ex: `/player_api.php?action=get_live_streams` etc.). La synchronisation peut être incrémentale ou planifiée via `WorkManager`.
- **Mode hors-ligne transparent** : Le passage du mode en ligne au mode hors-ligne sera automatique et invisible pour l'utilisateur, détecté via la connectivité réseau ou la mise en échec des requêtes de synchronisation.
- **80% de l'application disponible** : Toutes les interfaces de navigation (Home, TV, Films, Séries, Recherche, Favoris, Profils, Détails de fiches) fonctionneront à l'identique en mode déconnecté. Seuls les boutons "Lire" afficheront un message d'erreur ou seront désactivés en mode hors-ligne (sauf pour les médias préalablement téléchargés localement).

---

# 5. Spécification fonctionnelle

## 5.1 User stories

- En tant qu'utilisateur déjà connecté ayant un catalogue synchronisé, je veux ouvrir l'application sans réseau et consulter le dernier catalogue disponible, afin de retrouver mes contenus sans attendre un serveur IPTV.
- En tant qu'utilisateur connecté avec un accès réseau, je veux voir immédiatement le catalogue local puis recevoir les nouveautés sans bloquer la navigation.
- En tant qu'utilisateur, je veux savoir si les données affichées sont hors ligne ou potentiellement anciennes, sans être empêché de naviguer.
- En tant qu'utilisateur hors ligne, je veux pouvoir consulter mes profils, favoris, historique, reprises de lecture, recherches et fiches médias, et lire mes téléchargements locaux.
- En tant qu'utilisateur hors ligne, je veux une explication claire lorsque j'essaie de lire un flux qui nécessite le serveur Xtream.
- En tant qu'utilisateur, je veux pouvoir demander explicitement une actualisation du catalogue lorsque la connexion est disponible.

## 5.2 Parcours utilisateur

### Démarrage avec un catalogue local

1. L'application ouvre immédiatement ses écrans habituels à partir du dernier catalogue synchronisé.
2. Les données locales sont utilisables pendant qu'une vérification de fraîcheur et, si nécessaire, une synchronisation se déroulent en arrière-plan.
3. La navigation, la recherche et l'ouverture des fiches ne doivent pas afficher d'écran de chargement bloquant lorsqu'une donnée locale est disponible.
4. Si la synchronisation réussit, les écrans déjà ouverts se mettent à jour avec les données locales actualisées, sans réinitialiser la navigation ni le profil actif.

### Démarrage sans réseau

1. Si un catalogue local existe, l'application reste accessible avec ce catalogue et affiche un état discret indiquant que les données sont hors ligne, ainsi que la date ou l'heure de la dernière synchronisation réussie.
2. Les favoris, l'historique, les reprises et les réglages de profil restent modifiables localement.
3. Les recherches locales et les fiches déjà indexées restent disponibles.
4. Les images déjà présentes dans le cache disque peuvent être affichées ; une image absente affiche le visuel de remplacement habituel, sans erreur bloquante.
5. La lecture d'un flux Live TV, VOD ou épisode qui n'est pas téléchargé localement est refusée avec un message clair indiquant qu'une connexion est requise. Une lecture téléchargée reste disponible.

### Première utilisation ou cache absent

1. Sans catalogue local et sans connexion, l'application ne présente pas un faux catalogue vide comme résultat valide.
2. Elle explique que la première synchronisation nécessite une connexion Internet et propose de réessayer lorsque le réseau est rétabli.
3. Sans session locale précédemment validée, la connexion au compte Xtream reste nécessaire ; elle ne peut pas être contournée hors ligne.

### Actualisation volontaire

1. L'utilisateur peut lancer une actualisation explicite depuis l'emplacement de rafraîchissement existant ou prévu par l'interface.
2. Si le réseau est disponible, la synchronisation s'exécute sans rendre le catalogue local inutilisable ; l'interface indique qu'elle est en cours puis son succès ou son échec.
3. Si le réseau est indisponible ou si le panel échoue, le catalogue local est conservé intact et l'utilisateur reçoit un retour non bloquant. Aucune donnée déjà synchronisée ne doit disparaître à cause d'un échec d'actualisation.

## 5.3 Règles métier

- Le catalogue local est la source affichée par l'application. Une réponse Xtream n'est visible qu'après avoir été enregistrée localement.
- Une synchronisation automatique ne doit jamais empêcher l'accès aux données déjà disponibles ni déclencher plusieurs synchronisations concurrentes pour un même compte.
- La fraîcheur du catalogue est évaluée à partir de la dernière synchronisation complète réussie. Une synchronisation en échec ne renouvelle pas cette date.
- Une donnée locale ancienne reste consultable hors ligne ; l'état de fraîcheur informe l'utilisateur, mais ne transforme pas le catalogue en écran indisponible.
- Les données liées au profil (favoris, historique, reprise) conservent leur séparation actuelle par profil, y compris hors ligne.
- Les données de catalogue restent partagées entre les profils du même compte Xtream ; elles ne sont pas retéléchargées lors d'un changement de profil.
- Les programmes EPG enregistrés sont consultables hors ligne avec leur dernière heure de mise à jour. Ils ne sont pas présentés comme des programmes en direct garantis à jour.
- Une déconnexion volontaire ne supprime pas le catalogue local : se déconnecter puis se reconnecter au **même** compte doit retrouver l'application immédiatement utilisable, sans resynchronisation complète. *(Règle révisée par le PO le 2026-07-26, étape 3 ; la version initiale purgeait à la déconnexion.)*
- Le catalogue est purgé **à la connexion, lorsque le compte connecté change** (serveur ou identifiant différent du dernier compte synchronisé). C'est ce moment, et lui seul, qui garantit qu'un catalogue n'est pas exposé à un autre utilisateur de l'appareil. Le comportement exact de conservation des téléchargements relève de la spécification technique et des règles existantes de téléchargements.

## 5.4 Critères d'acceptation

- Après au moins une synchronisation réussie, le redémarrage de l'application sans Internet donne accès à Accueil, Live TV, Films, Séries, Recherche, Favoris, Profils et fiches disponibles dans le cache local.
- L'ouverture des listes et fiches disponibles localement ne déclenche pas de chargement réseau bloquant.
- En ligne, le contenu local s'affiche avant la fin d'une synchronisation de catalogue déclenchée au démarrage.
- Un échec réseau, un timeout, des identifiants expirés ou une réponse Xtream invalide n'effacent ni le catalogue déjà synchronisé ni les données de profil locales.
- En l'absence de cache et de réseau, l'application affiche un état explicatif et une action de nouvelle tentative ; elle ne plante pas et ne prétend pas que le catalogue est vide.
- Hors ligne, un flux non téléchargé n'est pas lancé et un message compréhensible précise que la connexion est nécessaire.
- Hors ligne, un média téléchargé reste lisible par le parcours de téléchargement existant.
- L'utilisateur peut identifier que le catalogue est hors ligne ou ancien et connaître sa dernière synchronisation réussie.
- Une actualisation manuelle réussie actualise les listes et fiches à partir du stockage local sans perturber le profil actif ni la navigation en cours.

## 5.5 Cas limites et gestion des erreurs

- Une perte de réseau pendant une synchronisation conserve l'état local antérieur ; l'actualisation est considérée comme échouée et pourra être relancée plus tard.
- Un cache partiel issu d'une toute première synchronisation interrompue ne doit pas être considéré comme un catalogue complet prêt pour le mode hors ligne.
- Si le cache d'images est incomplet, les métadonnées et la navigation restent disponibles avec les placeholders existants.
- Si l'espace disponible est insuffisant pour terminer une synchronisation, l'application prévient l'utilisateur, conserve les données valides existantes et ne boucle pas silencieusement sur les tentatives.
- Une erreur de lecture hors ligne ne doit pas être confondue avec une erreur d'authentification : le message doit indiquer si le flux exige une connexion ou si la session doit être revalidée en ligne.
- En cas de données locales corrompues ou non lisibles, l'application affiche un état de récupération clair et propose une resynchronisation dès que la connexion est disponible, sans exposer de détail technique brut.

## 5.6 Hors périmètre de cette étape

- Le choix du TTL, le mécanisme concret de détection de connectivité, le schéma Room, les migrations, la stratégie de taille/purge et le paramétrage du cache Coil seront définis à l'étape 3.
- Cette étape ne modifie aucun écran, aucun comportement de lecture et aucun code de synchronisation.

---

# 6. Spécification technique

## 6.1 État des lieux (audit du code existant)

Le cache Room existe déjà mais n'est pas une source unique de vérité. Constats relevés dans le code au 2026-07-26 (base `AppDatabase` version **17**, `iptv_xtream_cache.db`) :

| Constat | Emplacement | Conséquence pour T4 |
|---|---|---|
| Les repositories catalogue exposent `suspend fun getX(categoryId, forceRefresh): List<X>` : lecture cache **ou** appel réseau dans la même méthode | `LiveTvRepositoryImpl.kt:42-154`, `VodRepositoryImpl.kt:249-369`, `SeriesRepositoryImpl.kt:189-309` | Lecture et écriture confondues ; impossible d'observer la base. À scinder (§6.3) |
| `CACHE_EXPIRY_MILLIS = 24h` est **déclaré mais jamais lu** dans les trois repositories | `LiveTvRepositoryImpl.kt:39`, `VodRepositoryImpl.kt:138`, `SeriesRepositoryImpl.kt:135` | Le cache n'expire jamais de lui-même : le rafraîchissement dépend entièrement du worker ou d'un `forceRefresh` explicite. Le TTL doit devenir effectif (§6.5) |
| Le remplacement de catalogue est en **deux appels non transactionnels** : `clearAllStreams()` + `clearAllFts()` puis `insertStreamsWithFts()` | `LiveTvRepositoryImpl.kt:135-145`, `VodRepositoryImpl.kt:348-358`, `SeriesRepositoryImpl.kt:288-298` | Un crash/annulation entre les deux laisse un catalogue **vide**. Viole directement le critère « un échec n'efface pas le catalogue » (§5.4). Correction obligatoire (§6.4) |
| Horodatages de sync stockés en `SharedPreferences` non chiffrées, uniquement pour la catégorie « Tout » | `SettingsManager.kt:39-64` | Pas de granularité par section, pas de notion d'échec, pas lié au compte, non purgé au logout. Remplacé par une table Room (§6.2) |
| `getSeriesDetails()` est **100 % réseau**, aucun repli cache | `SeriesRepositoryImpl.kt:391-489` | Fiche série totalement indisponible hors ligne. Bloquant pour §5.4 |
| `getVodDetails()` a un repli cache partiel (`cachedVodDetails`) mais sans `plot`, `duration` ni `containerExtension` réels | `VodRepositoryImpl.kt:457-493` | Fiche film dégradée hors ligne, et extension de conteneur devinée (`"mp4"`) |
| `epg_cache` ne garde **qu'un seul programme par chaîne** (`@PrimaryKey streamId`), TTL 5 min | `EpgCacheEntity.kt`, `LiveTvRepositoryImpl.kt:215-279` | `getLiveEpgNowNext()` ne lit même pas le cache : toujours réseau. Aucun EPG hors ligne |
| L'auto-login appelle systématiquement le réseau ; tout échec ⇒ écran de connexion | `LoginViewModel.kt:36-53`, `MainActivity.kt:89-99` | Sans réseau, l'application n'atteint jamais ses écrans. **Bloquant principal** du mode hors-ligne |
| Aucune détection de connectivité dans le projet (`ConnectivityManager` absent du code) | — | À créer (§6.6) |
| Coil utilisé sans `ImageLoader` applicatif : cache disque par défaut (2 % du disque, plafonné 250 Mo) et respect des en-têtes HTTP | 17 écrans via `coil.compose.AsyncImage` | Jaquettes potentiellement revalidées en réseau ⇒ écran gris hors ligne. À configurer (§6.9) |
| La lecture n'est jamais conditionnée : l'URL est construite et confiée à ExoPlayer | `presentation/player/*`, `VodPlayerScreen`, `SeriesPlayerScreen` | Hors ligne, échec brut du player au lieu d'un message explicite. À garder (§6.8) |

Acquis réutilisables, à **ne pas** refaire : `XtreamRequestGate` (priorité écran/arrière-plan + cooldown 403/429), `RequestPriority`, `DatabaseSyncWorker` + `SyncScheduling` (heure fixe 6 h), Paging 3 (`getXStreamsPaged`), tables FTS4, `DownloadedMediaEntity` + `OfflineDownloadUtil` (lecture hors-ligne des téléchargements déjà fonctionnelle), `ProfileManager` (interface, mockable).

## 6.2 Modèle de données — schéma Room v17 → v18

### 6.2.1 Nouvelle table `catalog_sync_state`

Remplace les trois clés `*_ALL_SYNCED_AT` de `SettingsManager`. En base plutôt qu'en préférences pour trois raisons : l'horodatage doit être écrit **dans la même transaction** que les données qu'il décrit, il doit être purgé avec le catalogue à la déconnexion, et il doit porter l'échec autant que le succès.

```
catalog_sync_state(
    section       TEXT NOT NULL,   -- live_categories | live_streams | vod_categories
                                   -- | vod_streams | series_categories | series_streams
                                   -- | enrichment | epg
    accountKey    TEXT NOT NULL,   -- SHA-256 tronqué de "host:port" (jamais le mot de passe)
    lastSuccessAt INTEGER NOT NULL DEFAULT 0,
    lastAttemptAt INTEGER NOT NULL DEFAULT 0,
    lastFailureAt INTEGER NOT NULL DEFAULT 0,
    lastFailureKind TEXT,          -- NETWORK | AUTH | PANEL | STORAGE | PARSE | UNKNOWN
    itemCount     INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY(section)
)
```

- `accountKey` matérialise la règle métier « le catalogue appartient au serveur » : **à la connexion**, seul un changement de `host:port` purge le catalogue avant toute lecture d'écran (§6.10). Un autre utilisateur du même serveur, une déconnexion et un changement de mot de passe conservent la base. La permission de session hors ligne reste séparément liée à `host:port:username`, dans `CredentialsManager` chiffré ; elle ne peut donc pas être transmise à un autre utilisateur du même serveur.
- `lastFailureKind` alimente les messages non bloquants (§6.8) et sert au critère « une synchronisation en échec ne renouvelle pas la date de fraîcheur ».
- La fraîcheur globale est dérivée, pas stockée : `lastFullSyncAt = MIN(lastSuccessAt)` sur les 6 sections catalogue. Un `MIN` à 0 ⇒ catalogue incomplet ⇒ mode hors-ligne non éligible (cas §5.5 « cache partiel »).

### 6.2.2 Colonnes ajoutées à `vod_streams`

Pour que la fiche film soit complète hors ligne sans table supplémentaire (les colonnes `actors`, `director`, `genre`, `releaseYear`, `rating`, `streamIcon` existent déjà) :

```
ALTER TABLE vod_streams ADD COLUMN plot TEXT;
ALTER TABLE vod_streams ADD COLUMN duration TEXT;               -- déjà formatée ("1h 47min")
ALTER TABLE vod_streams ADD COLUMN containerExtension TEXT;     -- provient de movie_data
ALTER TABLE vod_streams ADD COLUMN detailsCachedAt INTEGER;     -- NULL = jamais enrichie
```

`containerExtension` est la colonne critique : sans elle, `cachedVodDetails()` devine `"mp4"` et une reprise hors-ligne sur un `.mkv` échoue. Elle n'est renseignée que par `get_vod_info`, donc par l'enrichissement.

### 6.2.3 Colonnes ajoutées à `series_streams`

```
ALTER TABLE series_streams ADD COLUMN plot TEXT;
ALTER TABLE series_streams ADD COLUMN detailsCachedAt INTEGER;
```

### 6.2.4 Nouvelles tables `series_seasons` et `series_episodes`

`get_series_info` est aujourd'hui la seule source des saisons/épisodes et n'est jamais persistée. Deux tables plutôt qu'un blob JSON : les épisodes sont déjà joints aux positions de lecture (`playback_positions.streamId` = id d'épisode) et la navigation « épisode suivant » doit rester requêtable.

```
series_seasons(
    seriesId INTEGER NOT NULL,
    seasonNumber INTEGER NOT NULL,
    name TEXT NOT NULL,
    episodeCount INTEGER NOT NULL DEFAULT 0,
    cover TEXT,
    cachedAt INTEGER NOT NULL,
    PRIMARY KEY(seriesId, seasonNumber)
)

series_episodes(
    episodeId INTEGER NOT NULL,     -- id de lecture (= streamId côté playback_positions)
    seriesId INTEGER NOT NULL,
    seasonNum INTEGER NOT NULL,
    episodeNum INTEGER NOT NULL,
    title TEXT NOT NULL,
    containerExtension TEXT NOT NULL,
    plot TEXT,
    duration TEXT,
    releaseDate TEXT,
    movieImage TEXT,
    orderIndex INTEGER NOT NULL DEFAULT 0,
    cachedAt INTEGER NOT NULL,
    PRIMARY KEY(episodeId)
)
CREATE INDEX index_series_episodes_seriesId_seasonNum ON series_episodes(seriesId, seasonNum);
```

Ces tables ne sont pas peuplées par un balayage complet du catalogue (une requête `get_series_info` par série ⇒ des milliers d'appels, exactement le trafic que T4 cherche à supprimer). Elles sont peuplées **à la demande** : toute ouverture réussie d'une fiche série en ligne persiste ses saisons/épisodes. Hors ligne, seules les séries déjà consultées ont leur détail complet ; les autres retombent sur la fiche dégradée reconstruite depuis `series_streams` (même principe que `cachedVodDetails`, avec `isMetadataIncomplete = true`).

### 6.2.5 Refonte de `epg_cache`

Clé primaire actuelle `streamId` ⇒ un seul programme mémorisé. Nouvelle clé composite pour conserver une fenêtre :

```
epg_cache(
    streamId INTEGER NOT NULL,
    startTimestamp INTEGER NOT NULL,
    endTimestamp INTEGER NOT NULL,
    title TEXT NOT NULL,
    description TEXT,
    cachedAt INTEGER NOT NULL,
    PRIMARY KEY(streamId, startTimestamp)
)
CREATE INDEX index_epg_cache_streamId_endTimestamp ON epg_cache(streamId, endTimestamp);
```

SQLite ne permet pas d'ajouter une colonne à une clé primaire par `ALTER TABLE`. Le patron habituel serait `epg_cache_new` + `INSERT INTO … SELECT` + `DROP` + `RENAME` (référence `MIGRATION_9_10`, AGENTS.md), mais il n'a pas lieu d'être ici : **`epg_cache` est du cache pur et jetable**. Chaque ligne provient d'un `get_short_epg` et se retéléchargera à la première consultation de la chaîne ; son TTL en ligne est de 15 minutes, donc les lignes présentes au moment de la mise à jour sont de toute façon périmées sous le quart d'heure.

La migration fait donc simplement :

```sql
DROP TABLE IF EXISTS epg_cache;
CREATE TABLE epg_cache (…);   -- nouveau schéma, clé composite
CREATE INDEX index_epg_cache_streamId_endTimestamp ON epg_cache(streamId, endTimestamp);
```

C'est le seul endroit du schéma où une destruction est acceptable sans arbitrage, précisément parce que la donnée est reconstructible sans coût utilisateur. Toutes les autres tables (catalogue, profils, favoris, historique, positions, téléchargements) restent migrées sans perte.

### 6.2.6 Migration 17 → 18

Une seule `Migration(17, 18)` dans `data/local/db/Migrations.kt`, ajoutée à `ALL_MIGRATIONS`, dans cet ordre :

1. `CREATE TABLE IF NOT EXISTS catalog_sync_state (…)` ;
2. reprise des trois horodatages existants : `INSERT INTO catalog_sync_state` depuis les valeurs `SharedPreferences` **n'est pas possible en SQL** — la reprise est faite au premier démarrage par `CatalogSyncStateInitializer` (code Kotlin, §6.4), la migration se contente de créer la table vide ;
3. 4 × `ALTER TABLE vod_streams ADD COLUMN` (nullables, sans DEFAULT) ;
4. 2 × `ALTER TABLE series_streams ADD COLUMN` ;
5. `CREATE TABLE IF NOT EXISTS series_seasons`, `series_episodes` + index ;
6. `DROP TABLE IF EXISTS epg_cache` + `CREATE TABLE` au nouveau schéma + index (§6.2.5).

Aucune opération n'est risquée : les étapes 1, 3, 4 et 5 sont des `CREATE TABLE` et des `ALTER TABLE ADD COLUMN` de colonnes nullables — les deux formes de migration SQLite qui ne peuvent pas perdre de données existantes. L'étape 6 détruit volontairement un cache reconstructible. Aucune recopie de table, aucun `INSERT … SELECT`, donc aucun des cas de figure où une migration se trompe silencieusement de colonne.

Le catalogue déjà synchronisé, les favoris, l'historique, les positions, les profils et les téléchargements sont intouchés. `fallbackToDestructiveMigration()` reste proscrit (`AppModule.provideDatabase`, AGENTS.md) : la migration réelle ne coûte ici qu'une dizaine de lignes de SQL trivial, alors que le fallback destructif s'appliquerait à **toutes** les migrations futures, y compris celles où la perte des favoris et des profils ne serait pas acceptable.

Bump associé : `AppDatabase.version = 18`, et mise à jour de la ligne « Base actuelle : version 16 » d'AGENTS.md, périmée (le code est en 17).

## 6.3 Séparation lecture / écriture dans les repositories

Le contrat actuel `getX(categoryId, forceRefresh)` est remplacé par deux surfaces distinctes dans les interfaces `domain/repository/` :

**Lecture (jamais réseau, jamais d'exception réseau)**

```kotlin
fun observeLiveCategories(): Flow<List<LiveCategory>>
fun observeLiveStreams(categoryId: String): Flow<List<LiveStream>>
fun getLiveStreamsPaged(categoryId: String): Flow<PagingData<LiveStream>>   // existant, conservé
suspend fun getStreamById(streamId: Int): LiveStream?                       // existant, conservé
```

Idem VOD et Séries. Les `Flow` proviennent directement des DAO (`@Query` retournant `Flow<List<Entity>>`), donc toute écriture de sync ré-émet vers les écrans ouverts — c'est le mécanisme qui satisfait « les écrans déjà ouverts se mettent à jour sans réinitialiser la navigation » (§5.2).

**Écriture (réseau → Room, jamais consommée directement par l'UI)**

```kotlin
suspend fun syncLiveCategories(): SectionSyncOutcome
suspend fun syncLiveStreams(): SectionSyncOutcome
```

`SectionSyncOutcome` = `Success(itemCount)` | `Failure(kind: SyncFailureKind, cause: Throwable?)`. Une écriture ne lève plus d'exception vers l'appelant : elle la classe. `InvalidCredentialsException` ⇒ `AUTH`, `SocketTimeoutException`/`IOException` ⇒ `NETWORK`, `HttpException(403/429/5xx)` ⇒ `PANEL`, `SQLiteFullException` ⇒ `STORAGE`, `JsonSyntaxException` ⇒ `PARSE`.

**Fiches détail** : `getVodDetails(streamId)` / `getSeriesDetails(seriesId)` gardent leur signature mais changent de politique — cache d'abord, réseau ensuite si en ligne, persistance du résultat, et repli cache explicite en cas d'échec (le repli VOD existant est généralisé et étendu aux séries).

Les méthodes `getX(categoryId, forceRefresh)` sont **supprimées** une fois les appelants migrés (11 use cases + 5 ViewModels, cf. §6.11) plutôt que conservées en dépréciation : les laisser vivantes réintroduit le trafic réseau qu'on retire.

## 6.4 Orchestration de la synchronisation — `CatalogSyncManager`

Nouveau composant `data/sync/CatalogSyncManagerImpl.kt` (interface `domain/sync/CatalogSyncManager.kt`), `@Singleton`. Remplace la logique linéaire de `SyncCacheUseCase`.

```kotlin
interface CatalogSyncManager {
    val syncState: StateFlow<SyncState>        // Idle | Running(section, done, total) | Success(at) | Failed(kind, at)
    val catalogStatus: Flow<CatalogStatus>     // isComplete, lastFullSyncAt, isStale, isOffline, isSyncing
    suspend fun syncIfStale(): SyncOutcome     // no-op si frais ou hors ligne
    suspend fun syncNow(trigger: SyncTrigger): SyncOutcome   // MANUAL | SCHEDULED | STARTUP | RECONNECT
    suspend fun clearCatalogForAccountChange()
}
```

Garanties :

- **Unicité** : un `Mutex` non réentrant + `tryLock()`. Une demande concurrente ne met pas en file d'attente, elle retourne `AlreadyRunning` (règle 5.3 « jamais plusieurs synchronisations concurrentes »). Côté WorkManager, l'unicité inter-déclenchements reste assurée par `enqueueUniqueWork`/`enqueueUniquePeriodicWork` déjà en place ; le worker tourne dans le process de l'app, donc le `Mutex` couvre bien les deux chemins.
- **Isolation des échecs** : chaque section est synchronisée puis committée indépendamment. L'échec de `vod_streams` n'annule pas `live_streams` déjà écrit, et laisse le `lastSuccessAt` de la section fautive inchangé.
- **Priorité réseau** : tout le corps s'exécute sous `RequestPriority.background` (mécanisme existant), donc `XtreamRequestGate` fait céder la sync devant toute navigation utilisateur, et le cooldown 403/429 s'applique.
- **Ordonnancement** : catégories avant flux pour chaque type (une catégorie manquante rend des flux invisibles), Live → VOD → Séries, enrichissement en dernier et borné (`maxBatches`), interrompu si `syncState` est annulé.
- **Amorçage** : `CatalogSyncStateInitializer` (appelé une fois au premier accès après migration) reprend les trois horodatages de `SettingsManager` dans `catalog_sync_state` pour ne pas déclencher une resynchronisation complète inutile sur les installations existantes, puis les clés `*_ALL_SYNCED_AT` deviennent mortes et sont supprimées de `SettingsManager`.

**Écriture atomique par section** (correction du défaut relevé en §6.1). Nouvelle méthode DAO :

```kotlin
@Transaction
suspend fun replaceAllStreams(streams: List<VodStreamEntity>) {
    if (streams.isEmpty()) return          // un panel qui répond vide n'efface pas le catalogue
    clearAllStreams(); clearAllFts()
    streams.chunked(500).forEach { insertStreamsWithFts(it) }
}
```

Le garde `isEmpty()` traite le cas « réponse Xtream valide mais vide » : elle est comptée comme un échec `PANEL` plutôt que comme un catalogue vide légitime, conformément au critère « ne prétend pas que le catalogue est vide ». Le découpage en lots de 500 borne le pic mémoire d'une transaction sur un catalogue de plusieurs dizaines de milliers d'entrées.

## 6.5 TTL et déclenchement

| Donnée | TTL | Justification |
|---|---|---|
| Catégories (live/vod/séries) | 24 h | Change très rarement ; alignée sur le catalogue pour n'avoir qu'un seul cycle |
| Flux (live/vod/séries) | 24 h | Reprend la constante `CACHE_EXPIRY_MILLIS` déjà écrite dans le code (aujourd'hui inerte) et la fréquence par défaut du worker (`SyncFrequency.DAILY`) |
| Détail film / série | 7 jours | Métadonnées quasi immuables ; un TTL court multiplierait les `get_vod_info`, principal contributeur au bannissement |
| EPG | 15 min en ligne, illimité hors ligne | 5 min actuellement : trop agressif pour un programme qui dure une heure. Hors ligne, on sert la donnée quel que soit son âge, avec sa date de mise à jour (règle 5.3) |
| Enrichissement casting | pas de TTL | Piloté par « champ manquant » (`getStreamsNeedingEnrichment`), inchangé |

Les TTL sont regroupés dans un unique `data/sync/CacheTtl.kt` (`object CacheTtl { val CATALOG = 24.hours … }`) plutôt que dispersés en `companion object` par repository, pour rester révisables d'un seul endroit.

Déclencheurs de synchronisation :

1. **Démarrage** (`STARTUP`) — après résolution du profil, `syncIfStale()` : ne part que si en ligne **et** catalogue périmé. Jamais bloquant, jamais attendu par l'UI.
2. **Planifié** (`SCHEDULED`) — `DatabaseSyncWorker` existant, inchangé côté planification (heure fixe 6 h, contraintes `CONNECTED` + `BATTERY_NOT_LOW`, backoff exponentiel 10 min). Son `doWork()` délègue désormais à `CatalogSyncManager.syncNow(SCHEDULED)`.
3. **Manuel** (`MANUAL`) — `SettingsViewModel.forceSyncNow()` existant, plus un geste de rafraîchissement sur les écrans catalogue. Ignore le TTL, respecte l'unicité.
4. **Reconnexion** (`RECONNECT`) — sur passage hors ligne → en ligne, si le catalogue est périmé. Débounce 5 s pour absorber le battement d'une connexion instable ; jamais plus d'un déclenchement par fenêtre de 15 min.

`SyncCacheUseCase` est conservé comme façade mince déléguant à `CatalogSyncManager` (il est référencé par `DatabaseSyncWorker` via un `@EntryPoint` Hilt) ou supprimé au profit d'un `@EntryPoint` sur le manager — arbitrage laissé au découpage de l'étape 4, sans impact fonctionnel.

## 6.6 Détection de connectivité — `NetworkMonitor`

Interface `domain/network/NetworkMonitor.kt`, implémentation `data/network/NetworkMonitorImpl.kt`, `@Singleton` :

```kotlin
interface NetworkMonitor {
    val isOnline: StateFlow<Boolean>
    fun isCurrentlyOnline(): Boolean
}
```

- Implémentation par `ConnectivityManager.registerNetworkCallback(NetworkRequest(NET_CAPABILITY_INTERNET), callback)` — la variante `registerDefaultNetworkCallback` exige l'API 24 alors que le projet est en **minSdk 21** ; la forme `NetworkRequest` couvre 21+.
- Un réseau est considéré en ligne s'il porte `NET_CAPABILITY_INTERNET` **et** `NET_CAPABILITY_VALIDATED` (API 23+ ; sur 21-22, `INTERNET` seul). `VALIDATED` écarte le cas du portail captif, où le transport est up mais le panel injoignable.
- Interface plutôt que classe concrète : `isCurrentlyOnline()` retourne un `Boolean` primitif, et AGENTS.md documente le piège Mockito/Kotlin de l'unboxing sur type primitif mocké (même motif que `ProfileManager`/`ProfileManagerImpl`).
- La connectivité n'est **pas** l'unique signal d'état hors ligne : elle est optimiste (réseau up ≠ panel joignable). L'état hors ligne effectif exposé à l'UI est `!isOnline || lastFailureKind == NETWORK` sur la dernière tentative de sync. C'est ce que consomme la bannière (§6.8).
- Aucune nouvelle permission : `android.permission.ACCESS_NETWORK_STATE` est déjà déclarée (`AndroidManifest.xml:6`), le manifeste n'est pas modifié.

## 6.7 Démarrage hors ligne — auto-login

Le blocage principal est `LoginViewModel.checkAutoLogin()` : tout échec réseau bascule sur l'écran de connexion. Nouvelle politique, portée par `AuthRepository` :

```kotlin
suspend fun autoLogin(): AutoLoginOutcome
// NoCredentials | Online(UserInfo) | OfflineSession(UserInfo) | Rejected(reason)
```

Arbre de décision :

1. Pas d'identifiants sauvegardés ou `rememberMe == false` ⇒ `NoCredentials` (inchangé).
2. En ligne ⇒ appel `player_api.php` (validation actuelle, inchangée, y compris compte expiré/inactif).
3. Hors ligne, **ou** échec réseau typé `ServerUnreachableException` / `NetworkTimeoutException` ⇒ session hors ligne accordée **si et seulement si** une validation réseau a réussi auparavant (`lastSuccessfulLoginAt > 0`, nouvelle clé dans `CredentialsManager`, donc chiffrée) **et** `catalog_sync_state` indique un catalogue complet pour ce compte. Sinon ⇒ `Rejected(NO_LOCAL_SESSION)` avec l'écran explicatif (§5.2 « première utilisation »).
4. `InvalidCredentialsException` / `AccountExpiredException` ⇒ `Rejected` sans repli. Ce sont des réponses du serveur, pas des pannes : la règle « la connexion au compte ne peut pas être contournée hors ligne » l'impose.

`UserInfo` (domain) reçoit un champ `isOfflineSession: Boolean = false`, et `AutoLoginState.Success` un champ `offline: Boolean = false` — ajouts avec valeur par défaut, donc `MainActivity.kt:89-99` et les branches `when` existantes compilent sans modification structurelle. Une session hors ligne affiche les compteurs de connexions/expiration issus du dernier `UserInfo` connu, marqués comme datés.

## 6.8 Gating de lecture et états UI hors ligne

**Gating.** Nouveau `domain/usecase/CanPlayContentUseCase.kt` :

```kotlin
sealed interface PlaybackAvailability {
    object Allowed : PlaybackAvailability                 // en ligne, ou média téléchargé
    object RequiresConnection : PlaybackAvailability      // hors ligne, non téléchargé
    object RequiresReauthentication : PlaybackAvailability // session locale sans validation possible
}
```

Ordre d'évaluation : téléchargé (`DownloadDao`, statut terminé) ⇒ `Allowed` quelle que soit la connectivité — c'est le chemin `getReadOnlyCacheDataSourceFactory` déjà opérationnel ; sinon en ligne ⇒ `Allowed` ; sinon `RequiresConnection`. La distinction avec `RequiresReauthentication` répond explicitement au cas limite §5.5 (« ne pas confondre erreur hors ligne et erreur d'authentification »).

Points d'accrochage : actions « Lire »/« Reprendre » de `VodDetailsScreen`, `SeriesDetailsScreen`, sélection de chaîne dans `LiveTvScreen`, et lecture depuis Favoris/Historique/Accueil. `mediaLoadErrorMessage()` (`presentation/MediaErrorMessage.kt`) reçoit une branche hors ligne pour uniformiser le message.

**Bannière et états.** Nouveau composable partagé `presentation/components/OfflineBanner.kt`, alimenté par `CatalogStatus` (via un `CatalogStatusViewModel` léger ou l'injection du `Flow` dans les ViewModels existants — arbitrage étape 4). Discrète, non bloquante, en tête des écrans Accueil / TV / Films / Séries / Recherche / Favoris. Contenu : « Hors ligne — catalogue du {date} » + action « Réessayer » active dès le retour du réseau.

**État vide qualifié.** Nouveau `presentation/components/CatalogUnavailableState.kt`, affiché uniquement lorsque `!isComplete && !isOnline` : explique qu'une première synchronisation nécessite Internet et propose une nouvelle tentative. Il ne doit jamais se substituer à une liste simplement filtrée à vide.

**Chargement non bloquant.** Les ViewModels catalogue (`VodViewModel.kt:185-227` et équivalents) passent `isLoading = true` uniquement quand la collecte du `Flow` Room n'a encore rien émis. Une synchronisation en cours n'a plus le droit de placer l'écran en chargement : elle n'alimente que l'indicateur de la bannière.

## 6.9 Cache d'images Coil

`IptvApplication` implémente `coil.ImageLoaderFactory` :

```kotlin
override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
    .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
    .diskCache {
        DiskCache.Builder()
            .directory(cacheDir.resolve("image_cache"))
            .maxSizeBytes(256L * 1024 * 1024)
            .build()
    }
    .respectCacheHeaders(false)
    .build()
```

`respectCacheHeaders(false)` est le réglage décisif : les panels Xtream servent les jaquettes sans `Cache-Control` ou avec un âge très court, ce qui pousse Coil à revalider en réseau — hors ligne, l'image en cache serait ignorée et remplacée par le placeholder. 256 Mo est un plafond explicite (le défaut, 2 % du disque, varie d'un appareil à l'autre et n'est pas prévisible). Le cache image reste dans `cacheDir` : purgeable par le système sous pression disque, sans conséquence fonctionnelle (dégradation en placeholder, conforme à §5.5).

## 6.10 Purge, taille et déconnexion

- **Catalogue** : aucune purge automatique par ancienneté — le catalogue périmé *est* le mode hors-ligne. Le seul remplacement est celui d'une synchronisation réussie (transactionnel, §6.4).
- **EPG** : `DELETE FROM epg_cache WHERE endTimestamp < :now - 6h` à la fin de chaque synchronisation, plus un plafond de rétention par chaîne. Sans cela, la clé composite fait croître la table indéfiniment.
- **Ordre de grandeur** : un gros panel (~15 000 chaînes, ~25 000 films, ~4 000 séries) donne ≈ 45 000 lignes catalogue + index FTS4, soit **20 à 30 Mo** de SQLite. Les tables `series_episodes` n'étant peuplées qu'à la consultation, leur croissance est bornée par l'usage réel. Acceptable sans purge.
- **Espace insuffisant** : `SQLiteFullException` ⇒ `SectionSyncOutcome.Failure(STORAGE)`, données existantes conservées, message non bloquant, et `Result.failure()` côté worker (pas `retry()`) pour ne pas boucler silencieusement sur un disque plein (§5.5).
- **Déconnexion** : `LogoutUseCase` est **inchangé** — il efface les identifiants, pas le catalogue. Une déconnexion suivie d'une reconnexion au même compte retrouve un catalogue immédiatement exploitable, sans resynchronisation (règle 5.3 révisée).
- **Purge à la connexion sur changement de serveur** — seul moment de purge du catalogue. `accountKey` (§6.2.1) est recalculé depuis le `host:port` qui vient d'être validé et comparé à celui stocké dans `catalog_sync_state` :

  | Cas | Action |
  |---|---|
  | Aucune ligne en base (première installation) | Écrire la clé, rien à purger |
  | Même `host:port` | Aucune purge, y compris si le nom d'utilisateur change |
  | `host:port` différent | `ClearCatalogCacheUseCase` puis écriture de la nouvelle clé, **avant** toute lecture d'écran |

  Un changement de mot de passe ou de nom d'utilisateur seul ne purge donc pas : c'est le comportement voulu, le catalogue est partagé par serveur. La première synchronisation du nouvel utilisateur le remplacera si son bouquet diffère.

  `ClearCatalogCacheUseCase` = transaction unique supprimant `live_categories`, `live_streams`, `vod_categories`, `vod_streams`, `series_categories`, `series_streams`, les trois tables FTS, `series_seasons`, `series_episodes`, `epg_cache`, `catalog_sync_state`. **Non supprimés** : profils, favoris, historique, positions de lecture, préférences, notes et téléchargements.

  Point de vigilance : sur changement de serveur, les favoris et l'historique conservent des titres issus de l'ancien catalogue et pointent vers des `streamId` qui n'existent plus sur le nouveau panel. Les écrans concernés doivent tolérer une entrée orpheline (masquage ou état « indisponible ») plutôt que planter. Purger aussi ces données reste possible mais relève d'une décision produit distincte, non retenue ici.

- **Point d'accrochage technique** : la comparaison de clé s'exécute dans `AuthRepository` après une validation réseau réussie **et** après l'octroi d'une session hors ligne (§6.7) — dans ce second cas, la clé ne peut par construction pas avoir changé, puisque la session hors ligne repose sur les identifiants déjà enregistrés. Un seul point d'appel, avant que `MainActivity` ne laisse composer les écrans.

## 6.11 Fichiers impactés

**Créés**

```
data/network/NetworkMonitorImpl.kt
domain/network/NetworkMonitor.kt
data/sync/CatalogSyncManagerImpl.kt
data/sync/CacheTtl.kt
data/sync/CatalogSyncStateInitializer.kt
data/sync/AccountKey.kt
domain/sync/CatalogSyncManager.kt
domain/sync/SyncModels.kt                 (SyncState, SyncOutcome, SectionSyncOutcome, SyncFailureKind, SyncTrigger, CatalogStatus)
domain/usecase/CanPlayContentUseCase.kt
domain/usecase/ClearCatalogCacheUseCase.kt
domain/usecase/ObserveCatalogStatusUseCase.kt
data/local/entity/CatalogSyncStateEntity.kt
data/local/entity/SeriesSeasonEntity.kt
data/local/entity/SeriesEpisodeEntity.kt
data/local/dao/CatalogSyncStateDao.kt
presentation/components/OfflineBanner.kt
presentation/components/CatalogUnavailableState.kt
```

**Modifiés**

```
data/local/db/AppDatabase.kt              version 17 → 18, +3 entités, +1 DAO
data/local/db/Migrations.kt               +MIGRATION_17_18, +ALL_MIGRATIONS
data/local/entity/VodStreamEntity.kt      +plot, duration, containerExtension, detailsCachedAt
data/local/entity/SeriesStreamEntity.kt   +plot, detailsCachedAt
data/local/entity/EpgCacheEntity.kt       clé primaire composite
data/local/dao/VodDao.kt                  +observe*, +replaceAllStreams transactionnel
data/local/dao/SeriesDao.kt               idem + saisons/épisodes
data/local/dao/LiveTvDao.kt               idem + EPG fenêtré
data/local/storage/SettingsManager.kt     retrait des 3 clés *_ALL_SYNCED_AT
data/local/storage/CredentialsManager.kt  +lastSuccessfulLoginAt
data/repository/LiveTvRepositoryImpl.kt   scission lecture/écriture
data/repository/VodRepositoryImpl.kt      scission + persistance du détail
data/repository/SeriesRepositoryImpl.kt   scission + persistance saisons/épisodes
data/repository/AuthRepositoryImpl.kt     +autoLogin() avec repli hors ligne, +purge sur changement de compte
data/worker/DatabaseSyncWorker.kt         délégation à CatalogSyncManager
domain/repository/{LiveTv,Vod,Series,Auth}Repository.kt   nouveaux contrats
domain/usecase/SyncCacheUseCase.kt        façade sur CatalogSyncManager
domain/usecase/Get{Live,Vod,Series}*UseCase.kt (11 fichiers)  retrait de forceRefresh, passage en Flow
domain/model/UserInfo.kt                  +isOfflineSession
di/AppModule.kt                           providers NetworkMonitor, CatalogSyncManager, CatalogSyncStateDao
IptvApplication.kt                        +ImageLoaderFactory
MainActivity.kt                           session hors ligne dans le routage auto-login
presentation/login/LoginViewModel.kt      autoLogin() typé
presentation/login/LoginState.kt          AutoLoginState.Success(offline)
presentation/{home,livetv,vod,series,search,favorites}/*ViewModel.kt   collecte de Flow, chargement non bloquant
presentation/MediaErrorMessage.kt         branche hors ligne
```

`AndroidManifest.xml` n'est **pas** modifié : `ACCESS_NETWORK_STATE` y est déjà déclarée (ligne 6).

**Dépendances** : aucune nouvelle. Room 2.6.1, Paging 3.2.1, WorkManager 2.9.0, Coil 2.6.0, security-crypto sont déjà au build. Aucune interface Retrofit nouvelle, donc pas de règle `-keep` supplémentaire dans `proguard-rules.pro`.

## 6.12 Performances

- **Démarrage** : la première frame de contenu ne dépend plus que d'une requête Room indexée. Cible : contenu affiché < 300 ms après résolution du profil, contre plusieurs secondes aujourd'hui quand le cache est vide ou que `forceRefresh` est demandé.
- **Trafic Xtream** : en régime nominal, une synchronisation complète par 24 h = 6 requêtes de liste + N requêtes d'enrichissement espacées de 500 ms (mécanisme existant). Aucune requête déclenchée par la navigation, contre une par changement de catégorie non encore en cache aujourd'hui.
- **Mémoire** : les lectures « Tout » (`getAllStreams()`) chargent aujourd'hui l'intégralité du catalogue en mémoire ; elles restent nécessaires à la recherche avancée et aux recommandations, mais quittent le chemin d'affichage des listes, qui passe par Paging. Les écritures de sync sont découpées en lots de 500.
- **Réactivité des `Flow` Room** : une écriture de sync invalide la table et ré-émet vers tous les collecteurs. Les `Flow` de liste complète sont donc réservés aux écrans qui en ont besoin ; les listes paginées absorbent l'invalidation par elles-mêmes. `distinctUntilChanged()` et `flowOn(Dispatchers.IO)` sur les mappings entité → domaine.

## 6.13 Sécurité et compatibilité

- Aucun identifiant ne transite vers la nouvelle table : `accountKey` est un SHA-256 tronqué de `host:port:username`, le mot de passe reste exclusivement dans `EncryptedSharedPreferences`.
- `lastSuccessfulLoginAt` est stocké dans `CredentialsManager` (chiffré) et non dans `SettingsManager` (préférences en clair) : c'est ce marqueur qui autorise une session hors ligne.
- La session hors ligne ne contourne aucune vérification serveur : elle prolonge une validation déjà obtenue, et toute réponse négative du panel la révoque au retour du réseau.
- minSdk 21 respecté (`registerNetworkCallback` avec `NetworkRequest`, pas `registerDefaultNetworkCallback`).
- Aucune régression de périmètre : pas de nouveau protocole, pas de nouvel appel externe, pas de nouvelle permission au-delà de `ACCESS_NETWORK_STATE`.

---

# 7. Architecture

## 7.1 Vue d'ensemble

```
┌──────────────── presentation ────────────────┐
│ Screens ─ ViewModels                          │
│   collectent Flow<List<X>> / PagingData       │
│   collectent CatalogStatus (bannière)         │
│   n'appellent jamais le réseau                │
└───────────────────┬──────────────────────────┘
                    │ use cases (Flow + suspend)
┌───────────────────▼──────────────────────────┐
│                 domain                        │
│  Repository (lecture=Flow / écriture=sync)    │
│  CatalogSyncManager · NetworkMonitor          │
│  CanPlayContentUseCase                        │
└───────────────────┬──────────────────────────┘
                    │
┌───────────────────▼──────────────────────────┐
│                   data                        │
│                                               │
│   Xtream API ──► Sync ──► Room ──► Flow ──► UI│
│   (jamais de chemin direct API → UI)          │
│                                               │
│   XtreamRequestGate (priorité + cooldown)     │
│   catalog_sync_state (fraîcheur + échecs)     │
└──────────────────────────────────────────────┘
```

La règle structurante tient en une phrase : **une réponse Xtream n'est visible qu'après avoir été écrite dans Room**. Il n'existe plus de chemin où une réponse réseau remonte directement à un ViewModel.

## 7.2 Flux — démarrage avec catalogue local

```
MainActivity → LoginViewModel.autoLogin()
   ├─ en ligne  → validation panel → Success(offline=false)
   └─ hors ligne + session locale validée + catalogue complet → Success(offline=true)
                                    │
                        gate profil (inchangé)
                                    │
        ViewModels ──collect──► Room  ─────► écrans peuplés immédiatement
                                    │
        CatalogSyncManager.syncIfStale()  (en ligne + périmé uniquement)
                                    │
                    écritures par section ──► Room ré-émet ──► écrans mis à jour
                                                                (navigation et profil intacts)
```

## 7.3 Flux — synchronisation

```
Déclencheur (STARTUP | SCHEDULED | MANUAL | RECONNECT)
   │
   ├─ Mutex.tryLock() échoue ─────────────────► AlreadyRunning (aucun effet)
   │
   └─ pour chaque section, séquentiellement :
        requête Xtream (RequestPriority.background → XtreamRequestGate)
           ├─ succès  → mapping défensif → replaceAllX() @Transaction
           │            → catalog_sync_state.lastSuccessAt = now
           └─ échec   → classification (NETWORK | AUTH | PANEL | STORAGE | PARSE)
                        → lastFailureAt/Kind mis à jour, lastSuccessAt INCHANGÉ
                        → section suivante (pas d'abandon global, sauf AUTH)
        puis enrichissement borné, puis purge EPG
```

`AUTH` est le seul cas d'arrêt immédiat : continuer avec des identifiants rejetés ne produit que du trafic refusé et rapproche du bannissement — exactement le risque que T4 combat.

## 7.4 Flux — lecture

```
Action « Lire »
   └─ CanPlayContentUseCase
        ├─ téléchargé (DownloadDao) ────────► lecture locale (cache media3, hors ligne OK)
        ├─ en ligne ───────────────────────► lecture réseau (URL construite comme aujourd'hui)
        └─ hors ligne, non téléchargé ─────► message « connexion requise », pas de lancement player
```

## 7.5 Responsabilités

| Composant | Responsabilité | Ne fait pas |
|---|---|---|
| `NetworkMonitor` | État de transport observable | Décider si le panel répond |
| `CatalogSyncManager` | Unicité, ordonnancement, classification des échecs, fraîcheur | Mapper les DTO, écrire en base |
| Repositories | Mapping DTO → entité → domaine, lecture `Flow`, écriture par section | Décider *quand* synchroniser |
| DAO | Requêtes et transactions, atomicité du remplacement | Logique réseau |
| `XtreamRequestGate` | Priorité écran/arrière-plan, cooldown 403/429 (existant) | Retenter, classer |
| ViewModels | Collecte, états UI, chargement non bloquant | Appeler le réseau |
| `CanPlayContentUseCase` | Autorisation de lecture | Construire l'URL |

## 7.6 Décisions techniques et justifications

1. **Table Room plutôt que `SharedPreferences` pour la fraîcheur** — l'horodatage doit être écrit dans la transaction qui écrit les données, purgé avec elles, et lié au compte. Trois propriétés qu'une préférence ne donne pas.
2. **Séparation lecture/écriture plutôt que `forceRefresh`** — le paramètre actuel rend chaque lecture potentiellement réseau et rend impossible la garantie « aucun chargement bloquant quand la donnée locale existe ». La séparation est le seul moyen d'y satisfaire structurellement.
3. **Remplacement transactionnel par section** — corrige un défaut réel du code actuel (clear puis insert non atomiques) qui peut vider le catalogue sur une simple annulation. C'est la condition du critère « un échec n'efface rien ».
4. **Épisodes persistés à la consultation, pas au balayage** — persister l'intégralité des séries exigerait un `get_series_info` par série, soit des milliers de requêtes : le remède serait pire que le mal que T4 traite.
5. **Colonnes ajoutées plutôt que tables `*_details` séparées** — `vod_streams` et `series_streams` portent déjà l'essentiel des métadonnées ; trois colonnes évitent une table, une jointure et une source de désynchronisation.
6. **Session hors ligne conditionnée à une validation antérieure** — traduit littéralement la règle « sans session locale précédemment validée, la connexion reste nécessaire », tout en levant le blocage au démarrage.
7. **`respectCacheHeaders(false)` sur Coil** — sans ce réglage, le mode hors-ligne afficherait des placeholders malgré un cache disque rempli. C'est le point de configuration qui décide de l'aspect réel du mode déconnecté.
8. **`NetworkMonitor` comme interface** — impose l'injection d'un double en test et évite le piège Mockito/Kotlin sur retour primitif documenté dans AGENTS.md.
9. **Purge à la connexion sur changement de serveur, jamais à la déconnexion** (décision PO du 2026-07-26) — le catalogue doit survivre au changement d'utilisateur lorsque `host:port` reste identique. La clé du catalogue couvre donc uniquement le serveur ; une clé distincte, chiffrée et incluant le nom d'utilisateur, protège la session hors ligne.

## 7.7 Risques techniques

| Risque | Impact | Atténuation |
|---|---|---|
| Surface de refactoring large (3 repositories, 11 use cases, 6 ViewModels) | Régressions sur des écrans non liés au hors-ligne | Découpage en tâches par couche à l'étape 4 : schéma+migration, puis `NetworkMonitor`, puis sync, puis repositories, puis écrans. Chaque tâche compile et passe les tests seule |
| Migration 17 → 18 | Crash au démarrage sur base existante | Risque ramené au minimum par construction : uniquement `CREATE TABLE`, `ALTER TABLE ADD COLUMN` nullables, et un `DROP`/`CREATE` sur un cache jetable (§6.2.5-6.2.6). Aucune recopie de données. **Le projet n'a pas d'infrastructure `androidTest`**, donc pas de `MigrationTestHelper` — limite connue d'AGENTS.md ; compensée ici par la trivialité du SQL et un test manuel de mise à jour (installer la release courante, peupler, installer le build T4 par-dessus) |
| Cohérence FTS lors du remplacement | Recherche renvoyant des titres disparus ou en manquant | Effacement et repeuplement FTS dans la **même** `@Transaction` que les flux |
| `Flow` sur liste complète ré-émettant à chaque écriture de sync | Recompositions coûteuses sur 25 000 entrées | Paging pour l'affichage, `Flow` de liste complète réservé aux usages qui l'exigent, `distinctUntilChanged` + `flowOn(IO)` |
| Session hors ligne masquant une expiration de compte réelle | Utilisateur croyant son compte valide | Aucune session hors ligne accordée sur `AccountExpiredException` ; révocation à la première réponse serveur négative |
| Déclenchements de sync multipliés à la reconnexion (réseau instable) | Trafic panel, risque de bannissement — l'inverse de l'objectif | `Mutex.tryLock()`, débounce 5 s, plafond d'un déclenchement `RECONNECT` par 15 min |
| Enrichissement long consommant la fenêtre du worker | Sync planifié tué par le système avant la fin | Enrichissement en dernière position et borné par `maxBatches` ; les sections catalogue sont déjà committées quand il démarre |

## 7.8 Stratégie de tests (cadrage pour l'étape 4)

Couverture obligatoire, alignée sur AGENTS.md :

- **`CatalogSyncManager`** — unicité (deuxième appel concurrent sans effet), isolation par section (échec VOD n'altère pas `lastSuccessAt` Live), classification des échecs, `lastSuccessAt` non renouvelé sur échec, arrêt sur `AUTH`.
- **DAO transactionnels** — `replaceAllStreams` sur liste vide ne supprime rien ; FTS cohérente après remplacement.
- **Repositories** (extension des `VodRepositoryImplTest`/`SeriesRepositoryImplTest`/`LiveTvRepositoryImplTest` existants) — lecture strictement locale sans appel réseau, détail servi depuis le cache quand le panel échoue, persistance des saisons/épisodes.
- **`AuthRepositoryImpl.autoLogin()`** — les 4 branches : pas d'identifiants, en ligne, hors ligne avec session locale, hors ligne sans session locale ; plus le non-contournement de `InvalidCredentials`/`AccountExpired`.
- **Purge sur changement de compte** — clé absente ⇒ pas de purge + écriture ; clé identique ⇒ catalogue intact (test de non-régression du cas « déconnexion/reconnexion au même compte ») ; clé différente ⇒ catalogue purgé, profils/favoris/téléchargements intacts ; mot de passe seul modifié ⇒ pas de purge.
- **`CanPlayContentUseCase`** — les 3 verdicts, dont téléchargé + hors ligne ⇒ autorisé.
- **`NetworkMonitor`** — via l'interface, avec un double, dans les tests des composants qui la consomment.
- **`CacheTtl`** — péremption aux bornes (exactement TTL, TTL−1 ms, TTL+1 ms).

Non-régression : `./gradlew assembleDebug`, `./gradlew testDebugUnitTest`, `./gradlew lintDebug` avant clôture de chaque tâche.

---

# 8. Plan de développement

## Notes d'implémentation

- 2026-07-26 — Étape 5 démarrée : le socle VOD introduit les lectures Room
  observables, les synchronisations explicites, le remplacement atomique
  protégé des réponses vides et la persistance des détails nécessaires à une
  fiche hors ligne. La migration des appelants UI reste volontairement dans la
  tâche 9, conformément au découpage.

- 2026-07-26 — Étape 5 terminée (tâches 1 à 11). `assembleDebug`,
  `testDebugUnitTest` et `lintDebug` passent. Écarts assumés par rapport à la
  spécification technique, tous documentés ici plutôt que silencieux :

  1. **Les trois clés `*_ALL_SYNCED_AT` ne sont plus une source de fraîcheur.**
     Elles étaient aussi l'estampille d'invalidation des caches TMDB de
     `GetTrendingInCatalogUseCase` et `GetPopularTop10InCatalogUseCase` : ces
     deux use cases lisent désormais `catalog_sync_state` via `CatalogFreshness`,
     et les repositories n'écrivent plus les clés. Elles ne subsistent que pour
     la reprise unique de `CatalogSyncStateInitializer` sur une installation
     mise à jour, et pour leur neutralisation lors d'une purge — sans quoi
     l'initialiseur ferait passer un catalogue purgé pour un catalogue frais.
     À supprimer une fois le parc migré.

  2. **`mediaLoadErrorMessage()` reçoit un paramètre `isOffline` explicite**
     plutôt que de déduire l'état hors ligne du type d'exception. Un
     `SocketTimeoutException` survient aussi en ligne : le déduire aurait produit
     un message faux, exactement la confusion que §5.5 demande d'éviter.

  3. **`onAccountAuthenticated()` purge aussi lorsque la clé de compte est
     absente**, alors que §6.10 prévoyait « écrire la clé, rien à purger ». Sur
     une installation neuve la purge est sans effet (tables vides) ; sur une base
     migrée dont l'initialiseur n'a jamais tourné, la clé absente signifie un
     catalogue d'origine indéterminée. Purger est le seul comportement qui ne
     risque pas d'exposer le catalogue d'un compte à un autre — c'est la raison
     d'être de ce contrôle. Ce cas n'est pas rapporté comme un changement de
     compte à l'appelant.

  4. **`ClearCatalogCacheUseCase` est `open`.** La transaction Room ne peut pas
     s'exécuter contre une base mockée et le projet n'a pas `mockito-inline`
     (AGENTS.md). Ouvrir la classe permet de doubler la purge dans les tests du
     `CatalogSyncManager`, qui vérifient *quand* elle se déclenche.

  5. **Le contenu exact de la purge n'a pas de test unitaire** : il repose sur
     `AppDatabase.withTransaction`, non exécutable sans base réelle, et le projet
     n'a pas d'infrastructure `androidTest` (limite déjà consignée dans
     AGENTS.md). Le déclenchement est couvert par `CatalogSyncManagerImplTest` ;
     la préservation des profils, favoris, historique, positions et
     téléchargements reste à vérifier manuellement en tâche 12.

  6. **Gating de lecture sur tous les points d'entrée** : fiche film (Lire /
     Reprendre), fiche série (épisode), sélection de chaîne Live TV, « Continuer
     à regarder » et rangées de l'Accueil, chaîne favorite. L'écran
     Téléchargements reste volontairement non gaté : ses médias sont locaux par
     construction.

  7. **Les quatre déclencheurs de synchronisation sont en place** : STARTUP
     (après un auto-login en ligne), SCHEDULED (worker), MANUAL (rafraîchissement
     des écrans catalogue) et RECONNECT. Ce dernier est armé à la construction
     du singleton `CatalogSyncManagerImpl`, forcée au démarrage par une injection
     dans `IptvApplication`.

     `syncIfStale()` est également appelé après une **connexion manuelle
     réussie**, ajout non prévu par §6.5. C'est le seul moment où le catalogue
     peut être vide alors que le réseau est disponible : première installation,
     ou purge qui vient de suivre un changement de compte. Sans lui, la
     suppression du chemin `forceRefresh` laissait les écrans vides jusqu'au
     worker planifié ou à un rafraîchissement manuel — le rattrapage passait
     jusqu'ici par la lecture réseau des écrans, que T4 supprime.

     Rappel de la sémantique STARTUP : la synchronisation ne part que si au
     moins une des 6 sections a plus de 24 h, donc un démarrage sur catalogue
     frais n'émet aucune requête. En revanche la péremption est évaluée par
     section alors que la synchronisation les rejoue toutes : une seule section
     périmée relance les 6 requêtes de liste.

## Ordre et règles d'exécution

Les tâches doivent être réalisées dans l'ordre ci-dessous. Les changements de schéma, de contrat et d'orchestration précèdent la migration des appelants afin qu'aucun écran ne dépende temporairement d'un chemin réseau direct. Chaque tâche reste livrable seule : elle comprend ses tests ciblés et conserve le catalogue antérieur si son écriture échoue. Les validations Gradle globales sont requises à la clôture de chaque tâche, conformément à `AGENTS.md`.

### Tâche 1 — Modèle Room, DAO et migration 17 → 18

- [x] Créer les entités et DAO de persistance T4, faire évoluer les tables existantes, puis livrer la migration non destructive et les transactions de remplacement atomiques.

Objectif : rendre le schéma capable de représenter l'état de synchronisation par section et par compte, les détails VOD/séries, les saisons/épisodes et une fenêtre EPG, sans perdre le catalogue, les profils ou les données utilisateur existants.

Fichiers :
- `data/local/entity/CatalogSyncStateEntity.kt`, `SeriesSeasonEntity.kt`, `SeriesEpisodeEntity.kt`
- `data/local/entity/VodStreamEntity.kt`, `SeriesStreamEntity.kt`, `EpgCacheEntity.kt`
- `data/local/dao/CatalogSyncStateDao.kt`, `VodDao.kt`, `SeriesDao.kt`, `LiveTvDao.kt`
- `data/local/db/AppDatabase.kt`, `Migrations.kt`

Validation : migration 17 → 18 relue contre les entités ; `replaceAll*` est transactionnel, insère par lots de 500 et ne vide jamais une section sur une liste vide ; les tests DAO ciblés couvrent la cohérence FTS et le garde liste vide ; `assembleDebug`, `testDebugUnitTest`, `lintDebug` passent.

### Tâche 2 — Fondations réseau, fraîcheur et identité de compte

- [x] Introduire la connectivité observable, les modèles de synchronisation, les TTL centralisés et la clé de compte sans mot de passe.

Objectif : fournir des primitives testables, compatibles minSdk 21, pour décider de la fraîcheur et du statut hors ligne sans lier l'UI à `ConnectivityManager` ni exposer d'identifiants.

Fichiers :
- `domain/network/NetworkMonitor.kt`, `data/network/NetworkMonitorImpl.kt`
- `domain/sync/SyncModels.kt`, `data/sync/CacheTtl.kt`, `data/sync/AccountKey.kt`
- `di/AppModule.kt`

Validation : tests de bornes `CacheTtl` (TTL−1, TTL, TTL+1), double `NetworkMonitor` dans les tests consommateurs, test de stabilité de la clé quand seul le mot de passe change ; état `VALIDATED` pris en compte à partir d'API 23 ; `assembleDebug`, `testDebugUnitTest`, `lintDebug` passent.

### Tâche 3 — État de synchronisation et purge ciblée du catalogue

- [x] Initialiser l'état depuis les préférences historiques, remplacer ces clés, et mettre en place la purge transactionnelle réservée au changement de compte.

Objectif : transférer la fraîcheur vers Room sans resynchronisation complète inutile après migration et garantir qu'une déconnexion ne supprime jamais le catalogue.

Fichiers :
- `data/sync/CatalogSyncStateInitializer.kt`
- `domain/usecase/ClearCatalogCacheUseCase.kt`, `ObserveCatalogStatusUseCase.kt`
- `data/local/storage/SettingsManager.kt`, `CredentialsManager.kt`
- DAO et DI concernés de la tâche 1

Validation : tests première initialisation, clé identique et clé différente ; la purge efface exclusivement catalogue/FTS/détails/EPG/état de sync, mais préserve profils, favoris, historique, positions et téléchargements ; test de non-régression déconnexion/reconnexion au même compte ; `assembleDebug`, `testDebugUnitTest`, `lintDebug` passent.

### Tâche 4 — Contrats lecture/écriture et repository Live TV

- [x] Scinder le contrat Live TV en lecture Room observable et synchronisation explicite, avec EPG fenêtré et repli local.

Objectif : supprimer tout appel Xtream depuis une lecture UI Live TV tout en conservant Paging et en écrivant chaque réponse avant son exposition.

Fichiers :
- `domain/repository/LiveTvRepository.kt`
- `data/repository/LiveTvRepositoryImpl.kt`
- `data/local/dao/LiveTvDao.kt`, use cases Live TV concernés

Validation : tests repository vérifiant qu'une lecture locale ne déclenche aucun appel réseau, qu'une sync remplit Room puis réémet, que l'EPG local reste consultable hors ligne et qu'un échec conserve les données précédentes ; `assembleDebug`, `testDebugUnitTest`, `lintDebug` passent.

### Tâche 5 — Contrats lecture/écriture et repository VOD

- [x] Migrer VOD vers la lecture Flow/Paging locale et la synchronisation explicite ; persister les détails nécessaires à la lecture et à la fiche hors ligne.

Objectif : rendre les listes et fiches film disponibles d'abord depuis Room, y compris le `containerExtension` réellement fourni par le détail.

Fichiers :
- `domain/repository/VodRepository.kt`
- `data/repository/VodRepositoryImpl.kt`
- `data/local/dao/VodDao.kt`, use cases VOD concernés

Validation : tests de lecture sans réseau, de repli détail cache lors d'un échec panel, de persistance `plot`/`duration`/`containerExtension`, et de conservation du catalogue sur une sync échouée ; `assembleDebug`, `testDebugUnitTest`, `lintDebug` passent.

### Tâche 6 — Contrats lecture/écriture et repository Séries

- [x] Migrer Séries vers la lecture Flow/Paging locale et persister à la demande les saisons et épisodes d'une fiche consultée en ligne.

Objectif : fournir une fiche série complète hors ligne lorsqu'elle a déjà été ouverte, sans balayage réseau massif de `get_series_info`.

Fichiers :
- `domain/repository/SeriesRepository.kt`
- `data/repository/SeriesRepositoryImpl.kt`
- `data/local/dao/SeriesDao.kt`, use cases Séries concernés

Validation : tests de lecture locale sans appel réseau, de repli fiche dégradée si jamais enrichie, de persistance/relecture des saisons et épisodes, et de conservation du cache sur erreur ; `assembleDebug`, `testDebugUnitTest`, `lintDebug` passent.

### Tâche 7 — Orchestrateur de synchronisation et worker

- [x] Implémenter `CatalogSyncManager`, raccorder le worker et conserver `SyncCacheUseCase` comme façade seulement si l'`@EntryPoint` existant le nécessite.

Objectif : centraliser l'unicité, l'ordre catégories puis flux, la priorité arrière-plan, la classification des erreurs et les déclenchements startup/planifié/manuels/reconnexion.

Fichiers :
- `domain/sync/CatalogSyncManager.kt`
- `data/sync/CatalogSyncManagerImpl.kt`
- `data/worker/DatabaseSyncWorker.kt`, `domain/usecase/SyncCacheUseCase.kt`
- DI et use cases de synchronisation concernés

Validation : tests d'appel concurrent (`AlreadyRunning`), d'isolation entre sections, de non-renouvellement de `lastSuccessAt` sur échec, d'arrêt immédiat sur `AUTH`, de classification `NETWORK`/`PANEL`/`STORAGE`/`PARSE`, et de délégation worker ; `assembleDebug`, `testDebugUnitTest`, `lintDebug` passent.

### Tâche 8 — Authentification et démarrage sans réseau

- [x] Rendre l'auto-login typé et autoriser une session hors ligne uniquement après validation antérieure et catalogue complet du même compte.

Objectif : atteindre les écrans à partir du cache sans contourner un refus explicite du panel ni une expiration de compte.

Fichiers :
- `domain/repository/AuthRepository.kt`, `data/repository/AuthRepositoryImpl.kt`
- `domain/model/UserInfo.kt`
- `presentation/login/LoginViewModel.kt`, `LoginState.kt`, `MainActivity.kt`
- `data/local/storage/CredentialsManager.kt`

Validation : tests des branches sans identifiants, en ligne, hors ligne avec session locale valide, hors ligne sans session, `InvalidCredentials` et compte expiré ; vérification manuelle du redémarrage hors réseau après sync réussie ; `assembleDebug`, `testDebugUnitTest`, `lintDebug` passent.

### Tâche 9 — Migration des use cases et ViewModels catalogue

- [x] Remplacer les lectures `forceRefresh` restantes par la collecte locale et raccorder la synchronisation non bloquante aux écrans catalogue.

Objectif : garantir que toute liste existante s'affiche depuis Room avant la fin d'une sync et se met à jour sans réinitialiser navigation ni profil.

Fichiers :
- `domain/usecase/Get{Live,Vod,Series}*UseCase.kt` (11 fichiers identifiés)
- `presentation/{home,livetv,vod,series,search,favorites}/*ViewModel.kt`
- use cases/appelants de recherche, favoris et recommandations concernés

Validation : aucun appelant ne conserve `getX(categoryId, forceRefresh)` ; tests ViewModel de premier état Room, de mise à jour Flow et d'absence de chargement bloquant pendant une sync ; vérification manuelle mobile et TV des listes, recherche, favoris et retour navigation ; `assembleDebug`, `testDebugUnitTest`, `lintDebug` passent.

### Tâche 10 — États UI hors ligne et autorisation de lecture

- [x] Ajouter le verdict de lecture hors ligne, les messages associés, la bannière de catalogue et l'état qualifié de premier démarrage sans cache.

Objectif : empêcher le lancement d'un flux indisponible tout en laissant lire un téléchargement et en distinguant clairement connexion requise et réauthentification.

Fichiers :
- `domain/usecase/CanPlayContentUseCase.kt`
- `presentation/components/OfflineBanner.kt`, `CatalogUnavailableState.kt`
- `presentation/MediaErrorMessage.kt`
- actions de lecture `VodDetailsScreen`, `SeriesDetailsScreen`, `LiveTvScreen` et chemins Accueil/Favoris/Historique

Validation : tests `Allowed` en ligne, `Allowed` téléchargé hors ligne, `RequiresConnection` et `RequiresReauthentication` ; vérification manuelle mobile et TV de chaque point d'entrée lecture, de la bannière datée et de l'état sans cache ; `assembleDebug`, `testDebugUnitTest`, `lintDebug` passent.

### Tâche 11 — Cache d'images et raccordement application

- [x] Configurer explicitement le cache disque Coil pour conserver les jaquettes déjà consultées lorsque le réseau est indisponible.

Objectif : compléter le cache de métadonnées par un repli visuel prévisible, sans rendre les images critiques au fonctionnement.

Fichiers :
- `IptvApplication.kt`

Validation : `ImageLoaderFactory` utilise un cache disque de 256 Mo, mémoire à 25 %, et `respectCacheHeaders(false)` ; vérification manuelle après consultation en ligne puis redémarrage hors ligne, placeholders acceptés pour les images jamais chargées ; `assembleDebug`, `testDebugUnitTest`, `lintDebug` passent.

### Tâche 12 — Validation intégrée et migration sur installation existante

- [ ] Exécuter la validation complète T4, incluant la mise à jour d'une base v17 réelle et les parcours mobile/TV connectés et déconnectés.

> Non réalisée : cette tâche demande un appareil ou un émulateur (installer un
> build v17, le peupler, installer T4 par-dessus, puis parcourir mobile et
> Android TV en ligne et hors ligne). Les validations Gradle, elles, passent.

Objectif : démontrer que la migration conserve les données non jetables, que le catalogue reste navigable sans réseau après sync, et que les régressions de lecture/navigation sont absentes.

Fichiers :
- tests unitaires ajoutés par les tâches 1 à 11
- documentation T4 (résultats consignés aux étapes de validation ultérieures)

Validation : `./gradlew assembleDebug`, `./gradlew testDebugUnitTest`, `./gradlew lintDebug` réussissent ; installer un build v17, peupler des données, installer T4 par-dessus et vérifier migration ; essais mobile et Android TV en ligne/hors ligne, sync manuelle, reconnexion, changement de compte et déconnexion/reconnexion même compte. Les éventuelles limites de device/emulateur restent explicitement consignées, sans les déclarer réussies sans preuve.

---

# 9. Review

Date :
2026-07-26

Périmètre :
review technique de l'implémentation T4 (étape 6), sans modification du code.

Status:
RESOLVED

Validation automatisée :
`./gradlew testDebugUnitTest assembleDebug lintDebug` réussi le 2026-07-26.
`git diff --check` réussi. La migration sur une base v17 réelle et les parcours
mobile/TV restent hors du périmètre de cette review automatisée et ne sont pas
considérés comme validés.

## Critique

### T4-R1 — Une réponse d'authentification négative ne révoque pas la session locale

Description :
`AuthRepositoryImpl.autoLogin()` transforme correctement
`InvalidCredentialsException` et `AccountExpiredException` en
`AutoLoginOutcome.Rejected`, mais conserve
`lastSuccessfulLoginAt`, le dernier `UserInfo` et les identifiants mémorisés.
Au démarrage suivant sans réseau, `offlineSessionOrRejection()` retrouve donc
toujours ces marqueurs et peut accorder `OfflineSession` si le catalogue est
complet. La validation locale survit ainsi à une réponse explicite du panel qui
l'a invalidée.

Impact :
un compte dont les identifiants ont été refusés ou dont l'abonnement est expiré
peut de nouveau entrer dans l'application hors ligne. Cela contredit les règles
§5.3, §6.7 et §6.13 selon lesquelles une réponse négative ne doit jamais être
contournée et doit révoquer la session locale.

Correction attendue :
ajouter une révocation ciblée des marqueurs de session locale lors de ces deux
réponses, sans purger le catalogue, puis couvrir les séquences
« validation en ligne → refus/expiration → redémarrage hors ligne » par des
tests de repository.

Correction appliquée (étape 7) :
`CredentialsManager.clearOfflineSessionValidation()` efface uniquement les
marqueurs de validation et le dernier `UserInfo`; les identifiants et le
catalogue restent en place. Les deux branches de refus du panel l'appellent et
les tests vérifient la révocation.

## Majeur

### T4-R2 — Le worker reboucle aussi sur les erreurs non récupérables

Description :
`CatalogSyncManager` conserve le `SyncFailureKind`, mais `SyncCacheUseCase` le
réduit à `SyncCacheResult.FAILED`. `DatabaseSyncWorker` répond ensuite
`Result.retry()` à tous les échecs, y compris `STORAGE`, `AUTH`, `PARSE` ou un
refus durable du panel.

Impact :
un disque plein ou des identifiants rejetés déclenchent des retries WorkManager
inutiles. Pour `STORAGE`, cela viole explicitement §6.10, qui impose
`Result.failure()` afin d'éviter une boucle silencieuse ; pour `AUTH` et certains
échecs panel, cela génère aussi le trafic que T4 cherche à réduire.

Correction attendue :
préserver le `SyncFailureKind` jusqu'au worker et mapper au minimum
`NETWORK` vers `Result.retry()`, `STORAGE`/`AUTH` vers `Result.failure()`, avec
une politique explicite et testée pour `PANEL`, `PARSE` et `UNKNOWN`.

Correction appliquée (étape 7) :
`NETWORK` et `UNKNOWN` sont retryables ; `AUTH`, `PANEL`, `STORAGE` et `PARSE`
sont permanents et produisent `Result.failure()`. Cette traduction est testée
au niveau de `SyncCacheUseCase`.

### T4-R3 — La lecture Live depuis la recherche globale contourne le gating hors ligne

Description :
dans `AppNavGraph`, le callback `SearchScreen.onPlayLive` renseigne le flux actif
et navigue directement vers `live_player`. Contrairement aux chemins Accueil,
Live TV et Favoris, il n'appelle pas `FavoritesViewModel.requestPlayback()`.

Impact :
hors ligne, sélectionner une chaîne depuis la recherche ouvre ExoPlayer et
produit l'échec brut que §5.4 et §6.8 demandent précisément d'éviter. Le
comportement varie donc selon le point d'entrée vers le même contenu.

Correction attendue :
faire passer ce callback par le même `requestPlayback(null)` que les autres
entrées Live et ajouter un test de non-navigation avec message explicite lorsque
le réseau est indisponible.

Correction appliquée (étape 7) :
la recherche passe désormais par `FavoritesViewModel.requestPlayback(null)` et
affiche le message via un `SnackbarHost`; le même retour explicite a été ajouté
aux favoris. Le test ViewModel confirme le refus sans callback de navigation.

## Mineur

Aucun constat mineur autonome. Les lacunes de tests observées sont directement
couvertes par les corrections attendues de T4-R1 à T4-R3.

---

# 10. Validation

Date :
2026-07-26

Status:
VALIDATED

Validation automatisée réussie :

- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`
- `./gradlew lintDebug`
- `git diff --check`

Les tests couvrent notamment la révocation de session hors ligne après refus du
panel, le maintien du catalogue pour deux utilisateurs du même `host:port`, la
purge lors d'un changement de serveur, la politique retry/failure du worker et
le refus explicite de lecture Live hors ligne depuis la recherche.

Validation manuelle restant à exécuter sur appareil ou émulateur : migration
d'une installation v17 réelle, navigation mobile et Android TV en ligne/hors
ligne, synchronisation manuelle/reconnexion et lecture des téléchargements. ADB
n'est pas disponible dans cet environnement ; ces parcours restent donc ouverts
et ne sont pas déclarés réalisés.

---

# 11. Documentation

Date :
2026-07-26

Documentation mise à jour :

- `docs/features.md` — comportement du catalogue persistant et de la lecture hors ligne ;
- `docs/architecture.md` — flux Room / `CatalogSyncManager` et séparation clé serveur/session ;
- `docs/user-guide.md` — parcours utilisateur sans connexion ;
- `docs/changelog.md` — entrée v1.55.0 avec limite explicite de validation manuelle.

---

# 12. Release

Version :
v1.55.0

Commit / tag :
v1.55.0

Date :
2026-07-26

Livraison préparée après validation automatisée complète. Les vérifications
manuelles sur installation v17, mobile et Android TV restent consignées dans la
section Validation et devront être réalisées sur un appareil ou émulateur.
