# F12 - Notification push lors de la sortie de nouveaux épisodes pour une série terminée

## Informations générales

Type:
Feature

Status:
TASK BREAKDOWN

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

L'utilisateur doit recevoir une notification (locale, se comportant comme une notification push) lorsqu'il a fini de regarder le dernier épisode d'une série et que de nouveaux épisodes de cette série sont ensuite publiés ou ajoutés sur le serveur IPTV.

Cette notification doit être déclenchée lors de la synchronisation automatique en arrière-plan du catalogue, permettant à l'utilisateur de savoir immédiatement qu'il peut reprendre le visionnage de sa série préférée.

---

# 2. Contexte

Actuellement, l'application propose une synchronisation périodique du catalogue en arrière-plan gérée par `DatabaseSyncWorker` (exécutant `SyncCacheUseCase`). Cette tâche rafraîchit les listes globales de catégories et de flux, mais ne récupère pas automatiquement les détails de chaque série (saisons et épisodes) pour des raisons d'économie de bande passante et de performance, les détails n'étant chargés que lorsque l'utilisateur visite la fiche détaillée d'une série (`GetSeriesDetailsUseCase`).

Le suivi de la progression de lecture est stocké localement dans la table Room `playback_positions` via `PlaybackPositionEntity`, qui contient les informations nécessaires pour identifier les épisodes vus (`seriesId`, `seasonNum`, `episodeNum`, `positionMs`, `durationMs`). L'application considère qu'un épisode est vu lorsqu'il a été visionné jusqu'à la fin ou à moins de 15 secondes du terme (`positionMs >= durationMs - 15000L`).

Il n'existe actuellement aucun mécanisme pour envoyer des notifications locales à l'utilisateur en dehors de l'indicateur de progression des téléchargements hors-ligne (`OfflineDownloadUtil`).

---

# 3. Objectif

Alerter l'utilisateur mobile par une notification système Android dès que de nouveaux épisodes sont détectés pour une série qu'il a précédemment terminée de visionner, en s'appuyant sur les synchronisations d'arrière-plan de l'application.

---

# 4. Hypothèses

- **Détection locale en arrière-plan :** Xtream Codes ne supportant pas de notifications push côté serveur, la détection des nouveaux épisodes doit s'effectuer entièrement sur l'appareil de l'utilisateur, au sein du worker de synchronisation en tâche de fond (`DatabaseSyncWorker`).
- **Définition d'une série terminée :** Une série est considérée comme entièrement visionnée si l'utilisateur possède un enregistrement de lecture complété (`positionMs >= durationMs - 15000L`) pour l'épisode ayant les numéros de saison et d'épisode les plus élevés parmi ceux disponibles localement au moment du visionnage.
- **Périmètre d'optimisation réseau :** Interroger l'API Xtream Codes pour obtenir les détails (saisons/épisodes) de l'intégralité des séries du catalogue IPTV à chaque synchronisation est inenvisageable (consommation réseau et risques de bannissement de l'abonnement). La détection de nouveaux épisodes sera donc limitée :
  - Aux séries ajoutées aux favoris du profil actif.
  - Aux séries présentes dans l'historique "Continuer à regarder" (playback positions actives).
- **Notification locale Android :** L'alerte sera envoyée via le `NotificationManager` Android sous un canal de notification dédié (ex: "Nouveautés séries"). Un clic sur la notification devra ouvrir l'application directement sur la fiche détaillée de la série concernée.

---

# 5. Questions ouvertes

1. **Gestion multi-profils :** Les notifications d'arrière-plan s'exécutant hors contexte utilisateur direct, comment associer correctement la notification au profil qui a effectivement terminé la série ? Doit-on parcourir l'historique de lecture de l'ensemble des profils locaux pour identifier les séries terminées ?
2. **Fréquence de vérification :** La vérification des nouveaux épisodes doit-elle être couplée à chaque exécution du `DatabaseSyncWorker` (généralement toutes les quelques heures), ou doit-on dédier une tâche périodique distincte et plus légère ?
3. **Persistance de l'état "notifié" :** Comment s'assurer qu'une notification n'est pas renvoyée à répétition à chaque synchronisation ultérieure si l'utilisateur n'a pas encore lancé la lecture du nouvel épisode ? Il sera nécessaire de stocker localement l'état "déjà notifié" ou le dernier ID d'épisode connu lors de la dernière notification.

---

# 6. Spécification fonctionnelle

## User stories

- En tant qu'utilisateur d'un profil local, après avoir terminé une série suivie, je veux être averti lorsqu'un nouvel épisode est disponible afin de pouvoir reprendre son visionnage.
- En tant qu'utilisateur de plusieurs profils, je veux que les alertes correspondent uniquement aux séries terminées ou suivies par mon profil, sans révéler l'activité d'un autre profil.
- En tant qu'utilisateur, je veux pouvoir toucher l'alerte et arriver sur la fiche de la série concernée.

## Parcours utilisateur

1. L'utilisateur ajoute une série à ses favoris ou possède au moins une position de lecture pour cette série dans « Continuer à regarder ».
2. Il termine le dernier épisode alors connu pour son profil. Un épisode est considéré terminé lorsque la position enregistrée atteint la durée moins 15 secondes ; une durée absente ou nulle ne permet pas de considérer l'épisode comme terminé.
3. Lors d'une synchronisation catalogue ultérieure, l'application vérifie les nouveaux épisodes uniquement pour les séries suivies par ce profil (favoris et/ou historique de lecture).
4. Si au moins un épisode postérieur au dernier épisode connu comme terminé est détecté, l'application affiche une notification locale « Nouveaux épisodes disponibles » identifiant la série.
5. Un toucher sur la notification ouvre l'application sur la fiche détaillée de cette série. Si une authentification est nécessaire, la navigation vers la fiche est effectuée après le retour à une session connectée.

## Règles métier

- La fonctionnalité concerne l'application mobile ; aucune notification équivalente n'est requise sur Android TV.
- La détection est exécutée à l'occasion des synchronisations de catalogue en arrière-plan et ne crée pas de vérification réseau indépendante.
- Une série est éligible si elle est dans les favoris du profil ou possède un historique de lecture pour ce profil. L'union des deux sources ne doit entraîner qu'une seule vérification et au plus une notification par série et par changement détecté.
- Une notification n'est produite que si la série avait été terminée avant l'apparition du ou des nouveaux épisodes. Une série seulement commencée, ou dont le dernier épisode connu n'est pas terminé, ne déclenche pas d'alerte.
- L'état de connaissance et de notification est isolé par profil et par série. Après notification, les mêmes épisodes ne doivent plus générer une nouvelle alerte, y compris après redémarrage de l'application ou synchronisations répétées.
- Si plusieurs nouveaux épisodes apparaissent entre deux synchronisations, une seule notification est affichée pour la série ; elle reste valable jusqu'à ce qu'un état plus récent soit détecté ou que l'utilisateur reprenne la série.
- Si l'utilisateur refuse ou désactive les notifications système, l'application continue la synchronisation sans affichage intrusif, sans erreur visible et sans réessai de permission automatique.
- Les informations de notification ne doivent pas contenir d'identifiants Xtream, d'URL de flux, ni d'informations liées à un autre profil.

## Critères d'acceptation

- Étant donné un profil ayant terminé le dernier épisode connu d'une série favorite, lorsqu'un épisode supplémentaire est trouvé pendant une synchronisation, alors une notification locale unique est reçue pour cette série.
- Étant donné une série terminée présente seulement dans l'historique de lecture, lorsqu'un nouvel épisode est trouvé, alors elle suit le même comportement qu'une favorite.
- Étant donné une série dont le dernier épisode est en cours de lecture, lorsqu'un nouvel épisode est trouvé, alors aucune notification n'est envoyée.
- Étant donné deux profils avec des séries terminées distinctes, lorsqu'une synchronisation s'exécute, alors chaque alerte éventuelle reste attribuable au seul profil concerné et ne divulgue pas l'autre série.
- Étant donné une synchronisation ultérieure sans nouvel épisode supplémentaire, alors aucune notification dupliquée n'est envoyée.
- Étant donné un toucher sur une notification, alors la fiche détaillée de la série concernée est affichée après l'ouverture de l'application et une session valide.

## Cas limites et gestion des erreurs

- Une série supprimée du catalogue, sans détail accessible ou dont la réponse est incomplète est ignorée pour cette synchronisation ; aucune notification erronée n'est créée.
- Une erreur réseau, une expiration de session ou un échec de synchronisation ne génère pas de notification ; la prochaine synchronisation normale pourra réessayer.
- L'absence de permission de notification, de canal disponible ou de capacité système à afficher l'alerte ne doit pas faire échouer la synchronisation du catalogue.
- Si l'utilisateur a plusieurs séries éligibles avec des nouveautés, une notification est créée par série, dans les limites imposées par Android ; les alertes ne doivent jamais être fusionnées avec une série différente.

---

# 7. Spécification technique

## 7.1 Vue d'ensemble

La détection est un post-traitement de la synchronisation catalogue, exécuté **après** `SyncCacheUseCase` et **hors** de celui-ci : le sync doit rester une opération de cache pure, et un échec de détection ne doit jamais provoquer un `Result.retry()` du worker.

Le cœur de la décision est isolé dans un objet **pur** (`NewEpisodeDetector`, `domain/model/`) sans dépendance Android ni Room, seul moyen d'obtenir une couverture de test réelle sur une logique dont les erreurs sont silencieuses (notification manquante ou notification en double, invisibles en debug).

## 7.2 Composants impactés et nouveaux composants

### Nouveaux

| Fichier | Rôle |
|---|---|
| `data/local/entity/SeriesWatchStateEntity.kt` | Table `series_watch_state` : dernier état connu et dernier état notifié, par (profil, série) |
| `data/local/dao/SeriesWatchStateDao.kt` | Lecture/écriture de l'état, requête des séries suivies par profil |
| `domain/model/EpisodeRef.kt` | `data class EpisodeRef(season, episode) : Comparable<EpisodeRef>` — ordre lexicographique (saison, épisode) |
| `domain/model/SeriesWatchState.kt` | Modèle domain de l'état persisté |
| `domain/model/NewEpisodeDetector.kt` | **Objet pur** : `decide(stored, completed, available) → Decision` |
| `domain/repository/SeriesWatchStateRepository.kt` | Interface : état + séries éligibles, **explicitement paramétrées par `profileId`** |
| `data/repository/SeriesWatchStateRepositoryImpl.kt` | Implémentation Room |
| `domain/usecase/DetectNewEpisodesUseCase.kt` | Orchestration : profils → séries éligibles → détails → décision → notification → persistance |
| `domain/notification/NewEpisodeNotifier.kt` | Interface (mockable) : `notifyNewEpisodes(profileId, series, latest)` |
| `data/notification/AndroidNewEpisodeNotifier.kt` | Implémentation `NotificationManagerCompat` + canal + `PendingIntent` |
| `presentation/navigation/SeriesDeepLink.kt` | Constantes d'extras d'intent et extraction (objet pur, testable) |
| `test/.../NewEpisodeDetectorTest.kt` | Tests de la logique de décision |
| `test/.../DetectNewEpisodesUseCaseTest.kt` | Tests d'orchestration (mocks) |
| `test/.../SeriesDeepLinkTest.kt` | Tests d'extraction des extras |

### Modifiés

| Fichier | Modification |
|---|---|
| `data/local/db/AppDatabase.kt` | Entité `SeriesWatchStateEntity`, DAO, **version 17 → 18** |
| `data/local/db/Migrations.kt` | `MIGRATION_17_18` + ajout à `ALL_MIGRATIONS` |
| `di/AppModule.kt` | `provideSeriesWatchStateDao`, `provideSeriesWatchStateRepository`, `provideNewEpisodeNotifier` |
| `data/worker/DatabaseSyncWorker.kt` | `detectNewEpisodesUseCase()` ajouté à l'`@EntryPoint` ; appel après le sync, encapsulé dans un `try/catch` |
| `MainActivity.kt` | `onNewIntent` + lecture du deep link, navigation différée vers `series_details`, demande unique de `POST_NOTIFICATIONS` (mobile) |
| `AndroidManifest.xml` | `android:launchMode="singleTop"` sur `MainActivity` |
| `res/values/strings.xml` | Nom/description du canal, titre et corps de la notification |
| `data/local/storage/SettingsManager.kt` | Indicateur « permission notifications déjà demandée » |

Aucune nouvelle dépendance Gradle (`androidx.core` fournit déjà `NotificationManagerCompat`). Aucune nouvelle interface Retrofit → **aucune règle ProGuard à ajouter**.

## 7.3 Modèle de données

### Entité Room

```kotlin
@Entity(tableName = "series_watch_state", primaryKeys = ["profileId", "seriesId"])
data class SeriesWatchStateEntity(
    val profileId: Int,
    val seriesId: Int,
    /** Dernier épisode existant au catalogue lors de la dernière vérification. */
    val lastKnownSeason: Int,
    val lastKnownEpisode: Int,
    /** Dernier épisode ayant déjà fait l'objet d'une notification (-1 = jamais). */
    val lastNotifiedSeason: Int,
    val lastNotifiedEpisode: Int,
    val updatedAt: Long
)
```

### Migration

```kotlin
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS series_watch_state (
                profileId INTEGER NOT NULL,
                seriesId INTEGER NOT NULL,
                lastKnownSeason INTEGER NOT NULL,
                lastKnownEpisode INTEGER NOT NULL,
                lastNotifiedSeason INTEGER NOT NULL,
                lastNotifiedEpisode INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                PRIMARY KEY(profileId, seriesId)
            )
        """.trimIndent())
    }
}
```

Création de table pure, sans copie de données : conforme à la règle « pas de `fallbackToDestructiveMigration` » d'AGENTS.md. À relire manuellement contre l'entité (le projet n'a pas d'infrastructure `androidTest` / `MigrationTestHelper`).

> Note : AGENTS.md documente encore « version 16 » ; le code est en version 17 (`AppDatabase.kt:54`, `MIGRATION_16_17`). Mettre AGENTS.md à jour en version 18 lors de l'étape 9.

### DAO

```kotlin
@Dao
interface SeriesWatchStateDao {
    @Query("SELECT * FROM series_watch_state WHERE profileId = :profileId")
    suspend fun getAllForProfile(profileId: Int): List<SeriesWatchStateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: SeriesWatchStateEntity)

    @Query("DELETE FROM series_watch_state WHERE profileId = :profileId")
    suspend fun deleteAllForProfile(profileId: Int)
}
```

`deleteAllForProfile` est appelée à la suppression d'un profil, au même endroit que `FavoritesDao.deleteAllForProfile` / `VodDao` (à câbler dans `ProfileRepositoryImpl`, sinon l'état survit à la suppression du profil et un futur profil réutilisant le même identifiant hériterait d'un historique de notifications).

### Requête des séries éligibles

Le worker s'exécute **hors contexte utilisateur** et doit couvrir tous les profils : il ne peut donc pas passer par `FavoritesRepository` / `VodRepository`, dont les flux sont rattachés au profil actif par `flatMapLatest`. Les DAO nécessaires sont déjà paramétrés par `profileId` :

- `FavoritesDao.getFavoritesByType("series", profileId)` — existe déjà.
- Nouvelle requête sur `playback_positions` (à ajouter au `VodDao`, qui détient déjà cette table) :

```kotlin
@Query("SELECT DISTINCT seriesId FROM playback_positions WHERE profileId = :profileId AND seriesId IS NOT NULL")
suspend fun getWatchedSeriesIds(profileId: Int): List<Int>
```

## 7.4 Logique de détection (objet pur)

```kotlin
data class EpisodeRef(val season: Int, val episode: Int) : Comparable<EpisodeRef> {
    override fun compareTo(other: EpisodeRef): Int =
        compareValuesBy(this, other, EpisodeRef::season, EpisodeRef::episode)
}

object NewEpisodeDetector {

    const val COMPLETION_THRESHOLD_MS = 15_000L

    sealed interface Decision {
        /** Premier passage ou rien à signaler : on mémorise l'état du catalogue sans alerter. */
        data class Remember(val latestAvailable: EpisodeRef) : Decision
        /** Nouveaux épisodes sur une série terminée et non encore notifiée. */
        data class Notify(val latestAvailable: EpisodeRef) : Decision
        /** Détail inexploitable : ne rien écrire, réessai au prochain sync. */
        data object Skip : Decision
    }

    /** Dernier épisode réellement terminé par le profil, ou null si aucun. */
    fun latestCompleted(positions: List<PlaybackPosition>): EpisodeRef? = positions
        .filter { it.seasonNum != null && it.episodeNum != null }
        .filter { it.durationMs > 0L && it.positionMs >= it.durationMs - COMPLETION_THRESHOLD_MS }
        .maxOfOrNull { EpisodeRef(it.seasonNum!!, it.episodeNum!!) }

    fun decide(
        stored: SeriesWatchState?,
        latestCompleted: EpisodeRef?,
        latestAvailable: EpisodeRef?
    ): Decision {
        if (latestAvailable == null) return Decision.Skip          // détail vide/incomplet
        val lastKnown = stored?.lastKnown ?: return Decision.Remember(latestAvailable)  // baseline
        val notify = latestCompleted != null &&
            latestCompleted >= lastKnown &&                        // avait tout rattrapé
            latestAvailable > latestCompleted &&                   // du neuf existe
            latestAvailable > (stored.lastNotified ?: EpisodeRef(-1, -1))  // pas déjà notifié
        return if (notify) Decision.Notify(latestAvailable) else Decision.Remember(latestAvailable)
    }
}
```

**Justification de chaque condition :**

| Condition | Règle métier couverte |
|---|---|
| `stored == null → Remember` | Premier passage : on ne connaît pas l'état du catalogue *au moment du visionnage*. Alerter ici notifierait d'un coup toute série où l'utilisateur s'est simplement arrêté en cours de route. |
| `latestCompleted >= lastKnown` | « Une série seulement commencée, ou dont le dernier épisode connu n'est pas terminé, ne déclenche pas d'alerte. » |
| `latestAvailable > latestCompleted` | Il existe réellement un épisode non vu. |
| `latestAvailable > lastNotified` | « Après notification, les mêmes épisodes ne doivent plus générer une nouvelle alerte » — y compris après redémarrage, l'état étant en base. |
| Une seule `Decision` par série et par passage | « Si plusieurs nouveaux épisodes apparaissent entre deux synchronisations, une seule notification est affichée. » |
| `durationMs > 0` | « Une durée absente ou nulle ne permet pas de considérer l'épisode comme terminé. » |

**Limite assumée à documenter** (étape 9) : lors de la toute première exécution après mise à jour, la table est vide → toutes les séries sont mises en baseline sans alerte. Un épisode publié *entre* la fin de visionnage et cette première exécution ne sera jamais signalé. Perte unique, non récurrente. Un pré-remplissage à l'installation serait moins fiable (il supposerait connaître l'état du catalogue au moment du visionnage, information non conservée).

## 7.5 Orchestration : `DetectNewEpisodesUseCase`

```
pour chaque profil (ProfileDao.getAll())
  ├─ éligibles = favoris(type="series", profileId) ∪ playback_positions.seriesId(profileId)
  ├─ états = SeriesWatchStateDao.getAllForProfile(profileId)
  ├─ positions = VodDao.getAllPlaybackPositions(profileId)   (une seule lecture, groupée par seriesId)
  │
  ├─ PRÉ-FILTRE SANS RÉSEAU — ne conserver que :
  │     • les séries sans état enregistré        (baseline à établir, une seule fois)
  │     • les séries dont latestCompleted >= lastKnown  (candidates réelles à notification)
  │   → toutes les séries « en cours de visionnage » sont écartées avant tout appel réseau
  │
  ├─ tri : favoris d'abord, puis lastAccessedAt décroissant
  ├─ troncature au budget restant (plafond GLOBAL partagé entre profils)
  │
  └─ pour chaque série retenue, SÉQUENTIELLEMENT :
        ├─ getSeriesDetails(seriesId)        ← 1 appel get_series_info
        ├─ latestAvailable = max EpisodeRef sur details.episodes
        ├─ decide(...) → Skip | Remember | Notify
        ├─ si Notify : notifier.notifyNewEpisodes(profileId, seriesName, latest)
        └─ upsert de l'état (sauf Skip)
```

Règles d'exécution :

- **Budget réseau**. `MAX_SERIES_CHECKS_PER_RUN = 20`, **global** (pas par profil) : le nombre de profils est libre et ne doit pas multiplier la charge Xtream. Le budget est consommé profil par profil ; l'ordre de parcours est décalé d'un cran à chaque exécution (curseur `lastCheckedProfileIndex` stocké dans `SettingsManager`) pour qu'un profil ne soit jamais systématiquement privé du budget. Toute troncature est tracée via `IptvLog.d("NEWEP", …)`.
- **Séquentiel, jamais parallèle.** De nombreux panels Xtream limitent les connexions simultanées par compte (`UserInfo.maxConnections`) ; `SeriesRepositoryImpl.getSeriesDetails` passe déjà par `requestGate`. Le parallélisme n'apporterait rien et risquerait le bannissement de l'abonnement (§4).
- **Priorité arrière-plan.** L'appel hérite du `RequestPriority.background` déjà posé par `DatabaseSyncWorker.doWork()` via `withContext` — aucune signature à modifier (structured concurrency).
- **Isolation des erreurs.** Chaque série est encapsulée dans un `try/catch` : une série retirée du catalogue, un détail incomplet ou un timeout n'interrompt pas le traitement des suivantes et ne modifie pas l'état stocké (`Skip`). `CancellationException` est systématiquement relancée (convention du projet).
- **Court-circuit.** Aucun profil, aucune série éligible, ou identifiants absents → retour immédiat sans appel réseau. L'absence de credentials est déjà détectée en amont par `SyncCacheUseCase` (`SKIPPED_NO_CREDENTIALS`) : la détection n'est lancée que sur `SUCCESS`.

## 7.6 Notification Android

- **Canal** : identifiant `new_episodes`, importance `IMPORTANCE_DEFAULT`, libellés dans `strings.xml` (`new_episodes_channel_name` / `new_episodes_channel_description`). Créé paresseusement au premier envoi (`NotificationManagerCompat.createNotificationChannel`), sur le modèle du canal de téléchargement (`OfflineDownloadUtil.DOWNLOAD_NOTIFICATION_CHANNEL_ID`).
- **Identifiant de notification** : `("newep:$profileId:$seriesId").hashCode()`. Déterministe → une nouvelle alerte pour la même série **remplace** la précédente (« une seule notification pour la série »), et ne peut pas fusionner avec une série différente.
- **Contenu** : titre `Nouveaux épisodes disponibles`, texte `<nom de la série>`. **Aucune** donnée Xtream (identifiants, URL de flux), **aucun** nom de profil dans le texte visible — le `profileId` voyage uniquement dans les extras de l'intent.
- **Android TV** : la règle métier exclut la TV. L'implémentation vérifie `UiModeManager.currentModeType == UI_MODE_TYPE_TELEVISION` et devient un no-op — la détection elle-même n'est pas exécutée sur TV, la vérification est une seconde barrière.
- **Permission `POST_NOTIFICATIONS`** : déjà déclarée au manifeste mais **jamais demandée** aujourd'hui (aucun `RequestPermission` dans le code). Sur Android 13+, `notify()` est silencieusement ignoré sans octroi. Deux mesures :
  1. Côté envoi : `if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return` → abandon silencieux, la synchronisation n'échoue pas, aucun réessai de permission (règle métier).
  2. Côté UI : **une seule** demande, sur mobile, après le gate de profil, mémorisée par un indicateur `SettingsManager` (`notification_permission_requested`). Jamais de nouvelle demande, y compris en cas de refus.
- **Robustesse** : tout l'envoi est encapsulé dans un `try/catch` (un `SecurityException` ou l'indisponibilité du `NotificationManager` sur certains OEM ne doit pas remonter jusqu'au worker).

## 7.7 Ouverture de la fiche série depuis la notification

Le graphe mobile utilise des routes **sans argument** et un état hissé dans `MainActivity` (`activeSeriesShow`), atteint via `onSelectSeriesDetail`. Le deep link réutilise ce mécanisme plutôt que d'introduire une route paramétrée :

1. `PendingIntent` → `Intent(context, MainActivity::class.java)` avec les extras `extra_series_id` / `extra_profile_id`, flags `FLAG_ACTIVITY_CLEAR_TOP or FLAG_ACTIVITY_SINGLE_TOP`, `PendingIntent.FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT`, `requestCode` = identifiant de notification.
2. `MainActivity` reçoit `android:launchMode="singleTop"` (à ajouter au manifeste) et implémente `onNewIntent` : sans cela, l'application déjà lancée recrée une activité et perd la session en cours.
3. L'identifiant en attente est exposé en `mutableStateOf<Int?>` et consommé par un effet **placé dans la branche connectée** (là où `navController` existe) :

```kotlin
LaunchedEffect(pendingSeriesId, loggedInUser, profileGateResolved) {
    val id = pendingSeriesId ?: return@LaunchedEffect
    if (loggedInUser == null || !profileGateResolved) return@LaunchedEffect  // attend une session valide
    val stream = seriesRepository.getStreamById(id)      // cache local, aucun appel réseau
    pendingSeriesId = null                                // consommé même en cas d'échec
    if (stream != null) {
        activeSeriesShow = stream
        navController.navigate("series_details")
    }
}
```

`SeriesRepository.getStreamById(seriesId)` existe déjà (`SeriesRepository.kt:61`) et lit le cache Room : la navigation fonctionne hors ligne, et une série disparue du catalogue est ignorée silencieusement (l'utilisateur reste sur l'Accueil).

L'attente de `loggedInUser != null && profileGateResolved` couvre la règle « la navigation vers la fiche est effectuée après le retour à une session connectée » : l'effet reste armé pendant le splash, l'auto-login, la connexion manuelle et le gate de sélection de profil.

**Bascule de profil au clic** : si `extra_profile_id` diffère du profil actif, `profileViewModel.selectProfile(profileId)` est appelé **avant** la navigation. Justification : la notification est intrinsèquement attachée à un profil ; ouvrir la fiche sous un autre profil afficherait un état de reprise de lecture et des favoris qui ne sont pas ceux de l'utilisateur destinataire. Effet de bord assumé et à documenter : toucher la notification change le profil actif.

## 7.8 Réponses aux questions ouvertes (§5)

1. **Gestion multi-profils** — Le worker parcourt **tous** les profils locaux (`ProfileDao.getAll()`) et non le seul profil actif, comme l'exigent les critères d'acceptation. Il n'emprunte donc aucun chemin passant par `ProfileManager` : toutes les lectures utilisent les DAO paramétrés par `profileId`. **Limite à arbitrer par le PO** : la règle « ne divulgue pas l'autre série » n'est pas techniquement atteignable — Android n'a qu'une seule zone de notifications par appareil, sans cloisonnement par profil applicatif. Toute personne tenant le téléphone verra le nom de la série d'un autre profil. La spécification est respectée au sens du *contenu* (aucun nom de profil, aucune donnée croisée) mais pas au sens de la *confidentialité d'affichage*. Repli possible si le PO le juge bloquant : restreindre la détection au profil actif (`ProfileManager.currentProfileId()`) — une ligne de code, mais les profils inactifs ne reçoivent alors plus rien.
2. **Fréquence de vérification** — **Couplée au `DatabaseSyncWorker`**, pas de tâche dédiée. Trois raisons : la détection exige un catalogue à jour (elle lit `series_streams` pour le nom et l'éligibilité) ; un worker distinct doublerait le nombre de fenêtres où l'application ouvre des connexions Xtream concurrentes ; le réglage de fréquence existant (`SyncFrequency`, par défaut quotidien à 6h — `SyncScheduling.DEFAULT_HOUR`) reste l'unique point de contrôle utilisateur. Conséquence acceptée : avec le réglage `WEEKLY` ou `MONTHLY`, l'alerte peut arriver plusieurs jours après la publication ; avec `DISABLED`, aucune alerte n'est produite. À documenter dans le guide utilisateur.
3. **Persistance de l'état « notifié »** — Table `series_watch_state`, clé `(profileId, seriesId)`, couple `lastNotifiedSeason/Episode`. La condition `latestAvailable > lastNotified` garantit l'absence de doublon à travers les redémarrages et les synchronisations répétées, sans dépendre de la présence de la notification dans la barre système (que l'utilisateur peut balayer).

## 7.9 Performances, sécurité, compatibilité

- **Réseau** : au plus 20 appels `get_series_info` par exécution de sync (soit au plus 20/jour en réglage par défaut), séquentiels, en priorité arrière-plan. Le pré-filtre sans réseau ramène en régime établi la charge aux seules séries réellement terminées — typiquement une poignée.
- **Base** : trois lectures Room par profil (favoris, positions, états) plus un `upsert` par série vérifiée. Table de quelques dizaines de lignes par profil.
- **Batterie** : aucun réveil supplémentaire ; les contraintes existantes (`NetworkType.CONNECTED`, `setRequiresBatteryNotLow`) s'appliquent.
- **Sécurité** : aucun identifiant Xtream ni URL de flux dans la notification, les extras d'intent ou les logs (seuls `profileId` et `seriesId` transitent). Le `PendingIntent` est `FLAG_IMMUTABLE` : une application tierce ne peut pas en altérer les extras.
- **Compatibilité** : `NotificationManagerCompat` couvre min SDK 21 ; le canal n'est créé qu'à partir d'API 26 par la couche compat ; `POST_NOTIFICATIONS` n'est vérifiée qu'à partir d'API 33. Aucune régression sur les versions antérieures.
- **Périmètre** : aucune API externe nouvelle ; `get_series_info` est un endpoint Xtream déjà utilisé. Conforme au périmètre strict d'AGENTS.md.

## 7.10 Risques techniques

| Risque | Mitigation |
|---|---|
| Bannissement de l'abonnement Xtream (rafale de `get_series_info`) | Plafond global dur, exécution séquentielle sous `requestGate`, pré-filtre écartant les séries en cours, priorité arrière-plan |
| Notification en double après désinstallation partielle / restauration de sauvegarde (`allowBackup="true"`) | L'état vit dans la base Room, sauvegardée avec l'application : une restauration conserve `lastNotified`. Pas d'action requise, mais à vérifier en validation |
| Numérotation d'épisodes incohérente selon les panels (saison `0`, épisodes non contigus, chaînes au lieu d'entiers) | `EpisodeRef` compare (saison, épisode) sans supposer la contiguïté ; le parsing défensif des DTO est déjà en place dans `SeriesRepositoryImpl` |
| Série renommée ou réindexée avec un nouvel identifiant | Traitée comme une série inconnue → baseline, aucune alerte erronée |
| Épisode supprimé du catalogue faisant *reculer* `latestAvailable` | `Remember` écrase `lastKnown` avec la valeur plus basse ; `lastNotified` n'est jamais abaissé → pas de re-notification du même épisode |
| Notification silencieusement ignorée (permission refusée Android 13+) | Vérification `areNotificationsEnabled()` + demande unique côté UI ; la fonctionnalité se dégrade sans erreur |
| `MainActivity` en `singleTop` modifie le cycle de vie existant | Vérifier explicitement en validation : rotation, retour depuis un lecteur, reprise depuis les récents |

## 7.11 Tests à écrire

**`NewEpisodeDetectorTest`** (pur, prioritaire) :
- premier passage sans état → `Remember`, aucune notification ;
- série terminée + un nouvel épisode → `Notify` ;
- série terminée + trois nouveaux épisodes → une seule `Notify` portant le plus récent ;
- passage suivant sans nouveauté → `Remember`, pas de doublon ;
- passage suivant avec les *mêmes* nouveaux épisodes (utilisateur n'a rien lancé) → pas de nouvelle `Notify` ;
- dernier épisode en cours de lecture (`positionMs < durationMs - 15 000`) → pas de `Notify` ;
- `durationMs == 0` → épisode non terminé ;
- nouvelle **saison** (S2E1 après S1E10 terminé) → `Notify` ;
- détail vide / épisodes absents → `Skip`, état inchangé ;
- épisode disparu du catalogue → pas de re-notification.

**`DetectNewEpisodesUseCaseTest`** (repositories et notifier mockés) :
- isolation par profil : une série terminée par le profil A ne notifie pas le profil B ;
- union favoris ∪ historique → une seule vérification et au plus une notification par série ;
- plafond global respecté sur un grand nombre de séries éligibles, et rotation du curseur entre profils ;
- pré-filtre : aucune requête réseau pour une série non terminée disposant déjà d'un état ;
- une exception réseau sur une série n'empêche pas le traitement des suivantes et laisse son état inchangé ;
- notifier indisponible / permission refusée → l'appel retourne normalement, l'état est tout de même mis à jour.

**`SeriesDeepLinkTest`** : extraction des extras, intent sans extra, identifiant invalide.

**Non-régression** : `./gradlew assembleDebug lintDebug testDebugUnitTest`, plus relecture manuelle de `MIGRATION_17_18` contre l'entité.

---

# 8. Architecture

## Flux de données

```
WorkManager (PeriodicWorkRequest "database_sync_work", 6h par défaut)
      │
      ▼
DatabaseSyncWorker.doWork()            [Dispatchers.IO + RequestPriority.background]
      │
      ├─ SyncCacheUseCase()  ──► SUCCESS / SKIPPED_NO_CREDENTIALS / FAILED
      │                              │
      │                     (si ≠ SUCCESS : fin, aucune détection)
      ▼
DetectNewEpisodesUseCase()             [try/catch : n'influence JAMAIS le Result du worker]
      │
      ├─ ProfileRepository.getAllProfiles()
      │
      └─ par profil :
           ├─ SeriesWatchStateRepository.eligibleSeries(profileId)
           │        └─ FavoritesDao.getFavoritesByType("series", profileId)
           │        └─ VodDao.getWatchedSeriesIds(profileId)
           ├─ SeriesWatchStateRepository.states(profileId)      (Room)
           ├─ VodDao.getAllPlaybackPositions(profileId)         (Room)
           │
           ├─ pré-filtre local (aucun réseau)
           │
           └─ par série retenue (séquentiel, budget global) :
                 ├─ SeriesRepository.getSeriesDetails(id) ──► requestGate ──► Xtream get_series_info
                 ├─ NewEpisodeDetector.decide(stored, completed, available)   [PUR]
                 │      ├─ Notify  ─► NewEpisodeNotifier ─► NotificationManagerCompat
                 │      │                                     canal "new_episodes"
                 │      │                                     PendingIntent → MainActivity
                 │      ├─ Remember ─► upsert de l'état
                 │      └─ Skip     ─► rien
                 └─ upsert (sauf Skip)

── Au clic sur la notification ──────────────────────────────────────────────

MainActivity.onNewIntent / onCreate  (launchMode = singleTop)
      └─ SeriesDeepLink.extract(intent) → (seriesId, profileId)
            └─ attente : loggedInUser != null && profileGateResolved
                  ├─ profil différent ? → ProfileViewModel.selectProfile(profileId)
                  ├─ SeriesRepository.getStreamById(seriesId)     (cache Room, hors ligne OK)
                  └─ activeSeriesShow = stream ; navController.navigate("series_details")
```

## Responsabilités

| Couche | Composant | Responsabilité |
|---|---|---|
| `data/worker` | `DatabaseSyncWorker` | Point d'entrée planifié. Enchaîne sync puis détection. Isole la détection de son `Result`. |
| `domain/usecase` | `DetectNewEpisodesUseCase` | Orchestration multi-profils, budget réseau, isolation des erreurs. Aucune logique de décision. |
| `domain/model` | `NewEpisodeDetector`, `EpisodeRef` | **Toute** la logique métier de décision. Pur, sans Android ni Room, entièrement testable. |
| `domain/repository` | `SeriesWatchStateRepository` | Contrat d'accès à l'état et aux séries éligibles, explicitement paramétré par profil. |
| `data/repository` | `SeriesWatchStateRepositoryImpl` | Room. Seule couche connaissant les DAO. |
| `domain/notification` | `NewEpisodeNotifier` (interface) | Contrat d'alerte. Permet de tester l'orchestration sans framework Android. |
| `data/notification` | `AndroidNewEpisodeNotifier` | Canal, `NotificationCompat`, `PendingIntent`, garde permission, garde TV. |
| `presentation` | `MainActivity`, `SeriesDeepLink` | Consommation du deep link, attente d'une session valide, navigation. |

## Décisions techniques

1. **Détection hors de `SyncCacheUseCase`.** `SyncCacheUseCase` a une responsabilité unique — rafraîchir le cache catalogue — et son résultat pilote le `Result.retry()` du worker. Y greffer la détection ferait retenter tout le sync catalogue pour un échec de notification. Séparation stricte : la détection est appelée *après*, sous `try/catch`, et ne peut jamais dégrader le sync (cas limite « ne doit pas faire échouer la synchronisation du catalogue »).

2. **Décision métier isolée dans un objet pur.** Les erreurs de cette fonctionnalité sont silencieuses par nature : une notification manquante ou en double ne produit ni exception ni trace. Le seul contre-poids réaliste, sans infrastructure `androidTest`, est une logique 100 % testable en JVM. C'est aussi ce qui rend vérifiables les six règles métier du §6.

3. **Accès direct aux DAO paramétrés par profil, pas aux repositories scopés.** Les repositories Favoris/Positions rattachent leurs flux au profil **actif** via `flatMapLatest` — inadapté à un worker devant couvrir tous les profils. Une interface `SeriesWatchStateRepository` dédiée, dont les méthodes prennent explicitement un `profileId`, préserve la règle « accès Room toujours via repository » d'AGENTS.md tout en rendant l'intention multi-profils lisible dans la signature.

4. **État persisté plutôt que dérivé.** On pourrait tenter de déduire « nouveaux épisodes » en comparant le catalogue aux positions de lecture sans table dédiée. Impossible : `series_streams` ne contient pas la liste des épisodes (seul `get_series_info` la fournit, et son résultat n'est pas mis en cache), et rien ne mémorise l'état du catalogue *au moment du visionnage* — exactement le repère qu'exige la définition de « série terminée » du §4.

5. **Baseline silencieuse au premier passage.** Choix conscient de rater au plus une notification par série au déploiement, plutôt que de risquer une rafale de fausses alertes sur toutes les séries simplement abandonnées en cours de route. La confiance de l'utilisateur dans les alertes est le critère décisif.

6. **Plafond global partagé, pas par profil.** Le nombre de profils locaux est libre. Un plafond par profil ferait croître linéairement la charge Xtream avec un paramètre que l'utilisateur contrôle sans en voir la conséquence — risque explicitement identifié au §4 (bannissement). Le curseur tournant garantit l'équité entre profils sur plusieurs exécutions.

7. **Deep link par état hissé, pas par route paramétrée.** Transformer `series_details` en `series_details/{seriesId}` serait plus propre à terme, mais impacte les six points de navigation existants vers cette route et les deux systèmes de navigation coexistants (mobile `AppNavGraph` / TV manuel, cf. AGENTS.md). Hors périmètre de F12 ; à traiter dans la tâche technique d'unification de la navigation déjà au backlog.

8. **Interface `NewEpisodeNotifier` dans `domain`.** Le use case n'a pas à connaître Android. Le motif suit celui de `ProfileManager` : interface mockable + implémentation, en réponse aux pièges Mockito/Kotlin documentés dans AGENTS.md.

---

# 9. Plan de développement

## Ordre d'exécution

Les tâches 1 et 2 établissent le contrat persistant et la décision pure. Les
tâches 3 à 5 les raccordent successivement à l'orchestration, à Android puis à
la navigation. La tâche 6 valide l'ensemble, y compris l'isolation de la
détection vis-à-vis de la synchronisation catalogue.

### Tâche 1 — Créer l'état de suivi de séries et son accès profilé

- [ ] Ajouter la table Room non destructive, les modèles, le DAO et le repository
  explicitement paramétré par profil.

Objectif :
Persister par `(profileId, seriesId)` le dernier épisode connu et le dernier
épisode notifié, exposer l'union dédupliquée des séries favorites et déjà vues,
et supprimer cet état lors de la suppression d'un profil.

Fichiers :

- `data/local/entity/SeriesWatchStateEntity.kt`
- `data/local/dao/SeriesWatchStateDao.kt`
- `data/local/dao/VodDao.kt`
- `data/local/db/AppDatabase.kt`
- `data/local/db/Migrations.kt`
- `domain/model/SeriesWatchState.kt`
- `domain/repository/SeriesWatchStateRepository.kt`
- `data/repository/SeriesWatchStateRepositoryImpl.kt`
- `data/repository/ProfileRepositoryImpl.kt`
- `di/AppModule.kt`
- tests repository/DAO concernés.

Validation :

- Version Room 18 et `MIGRATION_17_18` ajoutée à `ALL_MIGRATIONS`, sans fallback destructif.
- SQL relu contre l'entité ; la suppression d'un profil supprime aussi son état.
- Favorites séries et positions de lecture sont unifiées par profil sans doublon.
- `./gradlew testDebugUnitTest` passe.

### Tâche 2 — Implémenter et tester la décision pure de nouveaux épisodes

- [ ] Créer `EpisodeRef` et `NewEpisodeDetector` avec sa matrice de décisions.

Objectif :
Centraliser hors d'Android et de Room la comparaison saison/épisode, le seuil de
fin à 15 secondes, le baseline silencieux et la déduplication des notifications.

Fichiers :

- `domain/model/EpisodeRef.kt`
- `domain/model/NewEpisodeDetector.kt`
- `app/src/test/java/.../domain/model/NewEpisodeDetectorTest.kt`

Validation :

- Les cas sans durée, épisode inachevé, premier passage, nouvel épisode,
  épisodes multiples et état déjà notifié sont couverts.
- L'ordre est lexicographique saison puis épisode ; aucun composant Android ou
  Room n'est requis par les tests.
- `./gradlew testDebugUnitTest` passe.

### Tâche 3 — Orchestrer la détection multi-profils après la synchronisation

- [ ] Ajouter le use case, son budget global et son appel isolé dans le worker.

Objectif :
Parcourir tous les profils et leurs séries éligibles, pré-filtrer localement,
interroger Xtream séquentiellement dans le budget retenu, puis notifier et
persister les décisions sans pouvoir faire échouer la synchronisation.

Fichiers :

- `domain/usecase/DetectNewEpisodesUseCase.kt`
- `domain/notification/NewEpisodeNotifier.kt`
- `data/worker/DatabaseSyncWorker.kt`
- `di/AppModule.kt`
- `app/src/test/java/.../domain/usecase/DetectNewEpisodesUseCaseTest.kt`
- tests de `DatabaseSyncWorker` concernés.

Validation :

- Un profil ne peut ni lire ni produire une alerte au nom d'un autre profil.
- Les erreurs de détail, de notification ou de persistance sont isolées ; elles
  ne transforment jamais un succès de sync en retry.
- Le budget est global, équitable et les appels Xtream utilisent le mécanisme de
  limitation existant.
- `./gradlew testDebugUnitTest` passe.

### Tâche 4 — Afficher les notifications locales mobiles

- [ ] Fournir l'implémentation Android du notifier et les ressources associées.

Objectif :
Créer le canal dédié et une notification par série avec `PendingIntent` sûr,
permission Android 13+, garde TV et échec silencieux conformément au périmètre
mobile de F12.

Fichiers :

- `data/notification/AndroidNewEpisodeNotifier.kt`
- `app/src/main/res/values/strings.xml`
- `di/AppModule.kt`
- tests unitaires possibles du constructeur de notification / du notifier.

Validation :

- Aucun identifiant Xtream, URL ou donnée d'un autre profil n'est affiché.
- Permission absente, notifications désactivées et Android TV n'empêchent pas le
  worker de réussir.
- Le canal, l'identifiant par série et le `PendingIntent` ne créent pas de
  collision entre profils.

### Tâche 5 — Consommer le deep link de notification et demander la permission

- [ ] Ajouter l'extraction testable du deep link et le routage différé mobile.

Objectif :
Ouvrir la fiche de la série demandée après résolution de session, sélectionner
le profil cible si nécessaire et demander `POST_NOTIFICATIONS` une seule fois
sans modifier la navigation TV existante.

Fichiers :

- `presentation/navigation/SeriesDeepLink.kt`
- `MainActivity.kt`
- `app/src/main/AndroidManifest.xml`
- `data/local/storage/SettingsManager.kt`
- `app/src/test/java/.../presentation/navigation/SeriesDeepLinkTest.kt`

Validation :

- `onCreate` et `onNewIntent` traitent le même extra, y compris après une
  authentification différée.
- Une notification ouvre la bonne fiche et le bon profil ; un intent incomplet
  est ignoré sans crash.
- La permission n'est demandée qu'une fois sur mobile et jamais automatiquement
  après un refus.

### Tâche 6 — Valider la fonctionnalité complète et ses non-régressions

- [ ] Exécuter les contrôles automatisés et les parcours fonctionnels F12.

Objectif :
Vérifier le parcours de détection de bout en bout, la migration et l'absence de
régression de synchronisation, de profils et de navigation.

Fichiers :

- fichiers et tests des tâches 1 à 5 ;
- `ai/features/F12-series-new-episodes-push-notification.md` pour les résultats.

Validation :

- `./gradlew testDebugUnitTest`, `./gradlew assembleDebug` et `./gradlew lintDebug` passent.
- Sur mobile : nouvelle sortie après série terminée, absence d'alerte pour une
  série inachevée, déduplication après resynchronisation et clic vers la fiche.
- Sur TV : aucune alerte ni demande de permission ; la synchronisation reste
  fonctionnelle.
- La migration 17 → 18 est relue manuellement contre le schéma final.
