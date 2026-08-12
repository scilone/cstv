# F34 - Synchronisation cloud des profils par snapshots et ETag

## Informations générales

Status:
RELEASE

Created:
2026-08-11

Depends on:
F33

---

# 1. Description

Synchroniser entre plusieurs installations Android / Android TV les données
locales propres à chaque profil CSTV au moyen d'un snapshot gzip opaque par
namespace.

L'application conserve Room comme source immédiate de l'interface. Elle
sérialise chaque namespace, l'envoie de façon asynchrone au backend et utilise
les ETags pour empêcher les écrasements silencieux. Lorsqu'un autre appareil a
modifié le même snapshot, l'application télécharge la version serveur, effectue
une fusion applicative, puis réessaie avec l'ETag courant.

Le backend ne décompresse, ne parse et ne connaît jamais les objets contenus
dans les snapshots.

## Objectifs

- retrouver ses favoris, progressions, notes, préférences et historiques sur
  plusieurs installations ;
- initialiser le cloud depuis Room lorsqu'il est vide ;
- fusionner le local et le cloud lors de la première connexion d'une
  installation ;
- empêcher la perte silencieuse de données avec `If-Match` et une fusion à
  trois voies ;
- continuer à fonctionner hors ligne et reprendre automatiquement les envois ;
- limiter les écritures playback aux moments produit actés pour la première
  version ;
- garantir une isolation stricte entre comptes et profils.

---

# 2. Contexte

Les données métier sont aujourd'hui stockées uniquement dans Room et séparées
par un identifiant local de profil. Le backend CSTV stocke désormais une seule
ligne `profile_objects` par couple `(profileId, namespace)` et expose :

- `GET /v1/profiles/{profileId}/objects` pour les métadonnées ;
- `GET /v1/profiles/{profileId}/objects/{namespace}` pour les octets gzip ;
- `PUT /v1/profiles/{profileId}/objects/{namespace}` avec
  `X-Schema-Version` et ETag ;
- `DELETE /v1/profiles/{profileId}/objects/{namespace}` avec ETag.

Il n'existe plus de `sync_changes`, de cursor ni d'`object_key` côté serveur.
Les clés d'objets vivent uniquement dans le document applicatif du namespace.

F33 fournit la session CSTV obligatoire, l'identité du compte, les profils
distants et leur association avec les profils Room. F34 ne doit jamais tenter
une synchronisation sans cette identité résolue.

## Hypothèses

- Room reste la source affichée par l'application ;
- un snapshot complet reste inférieur à `MAX_OBJECT_SIZE_BYTES` ;
- chaque installation conserve le dernier snapshot serveur accepté comme base
  de fusion ainsi que son ETag ;
- l'horloge locale sert uniquement aux domaines disposant déjà d'un horodatage
  métier ; les autres conflits suivent les règles déterministes ci-dessous ;
- la synchronisation peut être différée sans bloquer une action utilisateur
  locale tant que le compte CSTV et le JWT restent valides.

## Questions ouvertes

Aucune question fonctionnelle bloquante à l'issue de l'étape 2. Une fréquence
périodique de push playback pourra être évaluée dans un second temps à partir
des usages réels ; elle est explicitement exclue de cette première version.

---

# 3. Spécification fonctionnelle

## User stories

- En tant qu'utilisateur, un favori ajouté sur mon téléphone apparaît sur mon
  Android TV après synchronisation.
- En tant qu'utilisateur, je retrouve ma progression de lecture sur une autre
  installation.
- En tant qu'utilisateur hors ligne, mes changements restent disponibles
  localement et sont envoyés au retour réseau.
- En tant qu'utilisateur de deux installations actives, mes changements ne
  sont pas écrasés silencieusement si elles partent du même ETag.
- En tant qu'utilisateur changeant de compte ou de profil, je ne vois jamais
  les données d'un autre compte ou profil.

## Périmètre synchronisé

Un snapshot distinct est maintenu pour chacun de ces namespaces :

| Namespace | Données fonctionnelles |
| --- | --- |
| `favorites` | favoris Live, films et séries |
| `playback` | positions et métadonnées nécessaires à la reprise films/épisodes |
| `ratings` | J'aime, Je n'aime pas et absence de note |
| `track-preferences` | langues audio et sous-titres préférés |
| `series-watch-state` | état de suivi et notification des nouveaux épisodes |
| `category-preferences` | catégories masquées et ordre personnalisé |
| `recently-watched-live` | historique récent des chaînes Live TV |

Chaque document contient au minimum sa `schemaVersion` et une collection
d'objets indexés par une clé métier stable choisie par l'application.

### Hors périmètre

- catalogue Xtream, catégories sources, EPG, films, séries et chaînes ;
- credentials ou URL Xtream ;
- données TMDB ou YouTube ;
- téléchargements hors ligne, qui restent globaux selon la décision produit
  existante ;
- paramètres globaux non liés au profil ;
- device, installation ou identifiant matériel ;
- synchronisation temps réel, WebSocket ou push serveur ;
- envoi playback périodique toutes les 30 secondes.

## Initialisation après F33

### Cloud vide

Pour chaque profil local associé à son profil CSTV :

1. L'application lit les données Room de chaque namespace.
2. Elle construit un snapshot complet, y compris un document vide lorsque ce
   vide doit devenir l'état initial explicite du namespace.
3. Elle compresse le document avec gzip.
4. Elle crée le snapshot distant sans `If-Match`.
5. Elle mémorise l'ETag renvoyé et le snapshot accepté comme nouvelle base.

L'initialisation est terminée uniquement lorsque tous les namespaces ont un
état local cohérent : envoyé, déjà distant ou marqué pour réessai. Un échec
réseau ne supprime aucune donnée Room et ne bloque pas l'utilisation locale.

### Cloud et local tous deux alimentés

1. L'application liste les métadonnées distantes puis télécharge les snapshots
   nécessaires.
2. Les objets présents d'un seul côté sont conservés dans le résultat.
3. Pour une même clé présente des deux côtés :
   - la valeur la plus récente gagne lorsque le domaine possède un horodatage
     fiable (`lastAccessedAt`, `watchedAt`, `updatedAt`, `addedAt`) ;
   - sans horodatage comparable, la valeur cloud gagne lors de cette toute
     première fusion, afin de ne pas écraser un état déjà partagé par une
     installation inconnue.
4. Les profils locaux non encore associés ont été ajoutés comme profils cloud
   par F33 ; leurs namespaces suivent ensuite la règle cloud vide.
5. Le résultat fusionné est appliqué atomiquement dans Room puis envoyé avec
   l'ETag de la version distante.
6. L'interface observe Room et reflète le résultat sans lire directement le
   payload réseau.

Une absence locale lors de cette première fusion, sans base synchronisée
antérieure, n'est pas interprétée comme une suppression distante.

## Synchronisation au démarrage et à la reprise

La comparaison des métadonnées est déclenchée :

- après authentification CSTV et résolution des profils ;
- au démarrage avec une session CSTV valide ;
- à la reprise de l'application ;
- après le retour du réseau ;
- après une réponse `412 ETAG_MISMATCH`.

Pour chaque profil :

1. `GET /objects` retourne seulement les métadonnées des namespaces.
2. Un ETag identique au dernier ETag local n'entraîne aucun téléchargement.
3. Un ETag absent ou différent entraîne le téléchargement du snapshot concerné.
4. Le payload reçu doit être strictement décompressé et interprété par
   l'application, jamais par le backend.
5. Un namespace distant absent n'efface pas automatiquement Room sans base
   prouvant qu'il s'agit d'une suppression synchronisée.

Il n'existe aucun polling de cursor et aucune dépendance à un journal serveur.

## Modification locale et envoi asynchrone

1. Toute action utilisateur écrit d'abord dans Room.
2. L'interface reflète immédiatement l'état local, même hors ligne.
3. Le namespace concerné est marqué comme ayant une modification en attente.
4. Dès que le compte et le réseau l'autorisent, l'application reconstruit le
   snapshot complet du namespace et le compresse.
5. Un namespace distant absent est créé sans `If-Match`.
6. Un namespace existant est remplacé avec son ETag courant dans `If-Match`.
7. Après `204`, l'ETag et la base synchronisée locale sont remplacés
   atomiquement par la version acceptée.
8. Après une panne réseau ou serveur temporaire, la modification reste en
   attente et sera retentée sans bloquer Room.

Plusieurs mutations rapprochées du même namespace peuvent être regroupées dans
un seul snapshot final. Une version locale plus ancienne ne doit jamais être
envoyée après une version locale plus récente.

## Conflit ETag et fusion à trois voies

Après `412 ETAG_MISMATCH` :

1. l'application conserve ses changements locaux ;
2. elle télécharge le snapshot et l'ETag courants ;
3. elle compare, pour chaque clé métier :
   - la dernière base synchronisée ;
   - l'état local courant ;
   - l'état serveur courant ;
4. si un seul côté diffère de la base, ce changement est conservé ;
5. si les deux côtés produisent le même résultat, ce résultat est conservé ;
6. si les deux côtés ont changé différemment la même clé :
   - `playback` : le `lastAccessedAt` le plus récent gagne, sans prendre
     automatiquement la position la plus élevée afin de respecter un redémarrage
     ou retour volontaire dans le média ;
   - `recently-watched-live` : le `watchedAt` le plus récent gagne ;
   - `series-watch-state` : l'état le plus récent gagne sans faire régresser un
     épisode déjà connu ou déjà notifié ;
   - `favorites`, `ratings`, `track-preferences` et
     `category-preferences` : l'action locale en attente gagne, car elle
     représente l'intention utilisateur ayant déclenché la tentative courante ;
7. l'application applique le résultat dans Room ;
8. elle réessaie le PUT avec le nouvel ETag serveur ;
9. un nouveau `412` répète le processus sur la dernière version, avec un nombre
   de tentatives borné avant report à une synchronisation ultérieure.

Une suppression locale se déduit par comparaison avec la base synchronisée.
Elle ne doit pas être annulée simplement parce que l'objet est absent du
snapshot local courant.

## Règles playback de la première version

Les sauvegardes Room existantes peuvent rester plus fréquentes pour protéger la
reprise locale. En revanche, un push du snapshot `playback` est demandé
uniquement :

- après le démarrage effectif du média, lorsque la durée et la position de
  départ ou de reprise sont connues ;
- lors d'une mise en pause ;
- à la fin naturelle de la lecture.

Un tick de progression ne déclenche jamais directement un appel backend. Aucun
push périodique toutes les 30 secondes ni worker dédié au playback n'est inclus
dans cette version. Une simple sortie ou destruction du lecteur ne déclenche
pas non plus de push supplémentaire si elle ne correspond ni à une pause ni à
la fin de lecture ; la dernière sauvegarde Room reste disponible localement et
sera prise en compte au prochain déclencheur prévu.

Une action explicite « retirer de Continuer à regarder » est une suppression
locale devant synchroniser le namespace `playback` comme toute autre mutation.

## Namespace vidé et suppression d'un profil

- Après son initialisation, un namespace reste représenté par un snapshot même
  lorsqu'il ne contient plus aucun objet métier.
- Supprimer le dernier objet produit donc un snapshot vide envoyé avec
  `If-Match` ; l'application ne supprime pas la ligne distante du namespace.
- Cette présence explicite distingue un état vide synchronisé d'un namespace
  jamais initialisé et évite toute ambiguïté sans journal serveur.
- L'endpoint DELETE de snapshot reste un outil du contrat backend mais n'est
  pas utilisé par le flow applicatif nominal.
- La suppression d'un profil réussie via F33 supprime ses données Room et ses
  états de synchronisation ; la cascade backend supprime ses snapshots.
- Les autres installations constatent la disparition du profil lors de leur
  prochain chargement de `/v1/me` ou `/v1/profiles`.

## Hors ligne, reprise et erreurs

- JWT valide et backend indisponible : lecture/écriture Room autorisées,
  synchronisation différée.
- JWT expiré : aucune utilisation de l'application ni tentative de push avant
  nouvelle authentification F33.
- `401` : session invalide, retour au gate CSTV.
- `403 ACCOUNT_DISABLED` ou `ACCOUNT_EXPIRED` : application immédiatement
  bloquée conformément à F33.
- `404 PROFILE_NOT_FOUND` : arrêter les envois de ce profil, recharger la liste
  des profils et ne jamais recréer automatiquement un profil supprimé par une
  autre installation.
- `412 ETAG_MISMATCH` : fusion à trois voies puis nouvel essai.
- `413 PAYLOAD_TOO_LARGE` : ne pas perdre Room, arrêter les retries automatiques
  identiques et afficher un diagnostic utilisateur non technique.
- `428 PRECONDITION_REQUIRED` : recharger les métadonnées/ETag avant de retenter.
- payload illisible ou schemaVersion inconnue : conserver Room et la version
  distante intactes, signaler une incompatibilité sans crash ni effacement.

## Isolation et sécurité

- Chaque requête utilise le JWT du compte CSTV actif et l'identifiant distant du
  profil associé.
- Aucun snapshot d'un autre compte ou profil ne peut être appliqué dans Room.
- Le changement de compte défini par F33 purge bases de fusion, ETags, pending
  changes et données de profil avant toute nouvelle synchronisation.
- Les snapshots ne contiennent aucun JWT, OTP, credential ou URL Xtream.
- Les logs ne contiennent ni payload gzip complet, ni token, ni donnée sensible.

## Critères d'acceptation

- [x] Les sept namespaces prévus sont synchronisés séparément par profil.
- [x] Un backend vide est initialisé avec toutes les données Room du compte et
  de ses profils sans perte locale.
- [x] Lors d'une première connexion avec cloud et local alimentés, les objets
  des deux côtés sont fusionnés selon les règles définies.
- [x] Les listings de métadonnées ne téléchargent aucun payload inutile.
- [x] Le snapshot gzip reçu est restitué à la couche applicative sans altération
  et le backend reste opaque à son contenu.
- [x] Toute modification écrit Room avant de tenter le réseau.
- [x] Une création distante se fait sans `If-Match` et toute mise à jour
  existante utilise l'ETag courant.
- [x] Deux installations parties du même ETag ne peuvent pas s'écraser
  silencieusement ; la seconde fusionne après `412`.
- [x] Les suppressions locales sont conservées par la fusion à trois voies.
- [x] Le dernier objet supprimé laisse un snapshot vide synchronisé ; le client
  ne supprime jamais le namespace distant dans le flow nominal.
- [x] Playback est poussé au démarrage effectif, à la pause et à la fin de
  lecture, jamais à chaque tick, à la simple sortie du lecteur ni toutes les
  30 secondes.
- [x] Le playback au `lastAccessedAt` le plus récent gagne un conflit, même si sa
  position est inférieure.
- [x] `recently-watched-live` est synchronisé et fusionné par `watchedAt`.
- [x] Hors ligne avec JWT valide, les changements restent dans Room et sont
  envoyés au retour réseau.
- [x] Un compte expiré/désactivé ou un JWT expiré bloque tout usage selon F33.
- [x] Une réponse `404 PROFILE_NOT_FOUND` ne recrée pas silencieusement un
  profil supprimé sur une autre installation.
- [x] Un changement de compte purge ETags, bases et mutations en attente du
  compte précédent.
- [x] Aucune donnée Xtream, catalogue, téléchargement, OTP ou JWT n'est incluse
  dans les snapshots.
- [x] Les scénarios de sérialisation, fusion, stale ETag, retry, isolation,
  initialisation et playback sont couverts par des tests JVM automatisés.

---

# 4. Spécification technique

## 4.1 Composants impactés (fichiers existants)

| Fichier | Modification |
| --- | --- |
| `data/local/db/AppDatabase.kt` | version **26 → 27** (26 est posée par F33) + `profileSyncStateDao()` |
| `data/local/db/Migrations.kt` | `MIGRATION_26_27` (création de `profile_sync_state`) + `ALL_MIGRATIONS` |
| `data/repository/FavoritesRepositoryImpl.kt` | marquage `favorites` après mutation |
| `data/repository/ViewingHistoryRepositoryImpl.kt` | marquage `playback` et `recently-watched-live` |
| `data/repository/MediaRatingRepositoryImpl.kt` | marquage `ratings` |
| `data/repository/TrackPreferenceRepositoryImpl.kt` | marquage `track-preferences` |
| `data/repository/SeriesWatchStateRepositoryImpl.kt` | marquage `series-watch-state` |
| `data/repository/CategoryPreferenceRepositoryImpl.kt` | marquage `category-preferences` |
| `data/repository/ProfileRepositoryImpl.kt` (F33) | suppression d'un profil → suppression de ses lignes `profile_sync_state` ; `purgeAllProfiles()` purge aussi cette table |
| `presentation/player/**` (VM lecteur film/série) | déclencheurs playback : démarrage effectif, pause, fin naturelle |
| `MainActivity.kt` | déclenchement de la synchronisation au démarrage et à `ON_RESUME`, après résolution du gate CSTV |
| `di/AppModule.kt` | providers `CstvObjectsApiService`, DAO, codecs, `CloudSyncManager` |
| `data/worker/SyncScheduling.kt` | enregistrement du `CloudSyncWorker` (contrainte réseau, backoff exponentiel) |
| `app/proguard-rules.pro` | `-keep interface com.cstv.app.data.remote.api.CstvObjectsApiService { *; }` |

Aucune nouvelle dépendance Gradle : `java.util.zip.GZIPOutputStream` /
`GZIPInputStream` du JDK couvrent la compression, Gson sérialise les documents,
WorkManager et OkHttp sont déjà présents.

## 4.2 Nouveaux composants

```
data/remote/api/CstvObjectsApiService.kt    GET/PUT/DELETE snapshots (octets)
data/remote/dto/ObjectMetadataDto.kt        namespace, etag, schemaVersion,
                                            compressedSize, updatedAt
data/local/entity/ProfileSyncStateEntity.kt état par (profil, namespace)
data/local/dao/ProfileSyncStateDao.kt
data/cloudsync/SnapshotCodec.kt             document <-> gzip
data/cloudsync/NamespaceSnapshot.kt         document générique
data/cloudsync/serializer/*.kt              7 (de)sérialiseurs Room <-> document
data/cloudsync/merge/SnapshotMerger.kt      fusion à trois voies générique
data/cloudsync/merge/ConflictResolvers.kt   règle par namespace
data/cloudsync/CloudSyncManagerImpl.kt      orchestration, file, retries
data/worker/CloudSyncWorker.kt              reprise après mort du process
domain/sync/CloudSyncManager.kt             interface
domain/sync/SyncNamespace.kt                enum des 7 namespaces
domain/sync/CloudSyncStatus.kt              état exposé à l'UI (Paramètres)
```

## 4.3 Format des snapshots

Un snapshot est un document JSON UTF-8 compressé en gzip, envoyé tel quel avec
le type `application/vnd.cstv.blob+gzip` et l'en-tête `X-Schema-Version: 1`.

```json
{
  "schemaVersion": 1,
  "namespace": "favorites",
  "objects": {
    "movie:1234": { "id": 1234, "type": "movie", "name": "...",
                    "cover": "...", "categoryId": "12", "addedAt": 1754870400000 }
  }
}
```

Clés métier et champs par namespace (`profileId` n'est **jamais** sérialisé :
c'est une identité locale) :

| Namespace | Clé | Champs |
| --- | --- | --- |
| `favorites` | `{type}:{id}` | `id`, `type`, `name`, `cover`, `categoryId`, `addedAt` |
| `playback` | `{streamId}` | tous les champs de `PlaybackPositionEntity` sauf `profileId` |
| `ratings` | `{mediaType}:{mediaId}` | `value` |
| `track-preferences` | `{mediaType}:{mediaId}` | `audioLang`, `subtitleLang` |
| `series-watch-state` | `{seriesId}` | `lastKnownSeason`, `lastKnownEpisode`, `lastNotifiedSeason`, `lastNotifiedEpisode`, `updatedAt` |
| `category-preferences` | `{type}:{categoryId}` | `hidden`, `sortOrder` |
| `recently-watched-live` | `{streamId}` | `name`, `streamIcon`, `categoryId`, `num`, `watchedAt` |

`ratings` et `track-preferences` n'ont pas d'horodatage métier : leur règle de
conflit est déterministe (l'intention locale en attente gagne), aucune colonne
`updatedAt` n'est donc ajoutée à ces tables — cela éviterait une migration sans
bénéfice fonctionnel.

Un `schemaVersion` distant supérieur à celui du client, ou un document
illisible, laisse Room **et** la version distante intactes : l'état passe en
`Incompatible`, aucun PUT n'est tenté sur ce namespace.

## 4.4 État de synchronisation (Room)

```kotlin
@Entity(tableName = "profile_sync_state", primaryKeys = ["profileId", "namespace"])
data class ProfileSyncStateEntity(
    val profileId: Int,
    val namespace: String,
    val etag: String?,          // null = jamais créé côté serveur
    val schemaVersion: Int,
    val baseSnapshot: ByteArray?, // gzip du dernier document accepté
    val pending: Boolean,
    val lastSyncedAt: Long,
    val lastAttemptAt: Long,
    val failureCode: String?,   // ETAG_MISMATCH, PAYLOAD_TOO_LARGE, INCOMPATIBLE...
    val retryCount: Int
)
```

`MIGRATION_26_27` = un simple `CREATE TABLE IF NOT EXISTS` + index sur
`profileId` ; aucune donnée existante n'est touchée.

La base de fusion est stockée compressée (elle l'est déjà pour l'envoi, et reste
sous `MAX_OBJECT_SIZE_BYTES` = 1 MiB). `ByteArray` dans une `data class` Room
impose de ne pas se reposer sur `equals`/`hashCode` générés : la comparaison se
fait toujours sur l'ETag, jamais sur l'entité.

## 4.5 Contrat réseau

```kotlin
@GET("v1/profiles/{profileId}/objects")
suspend fun listObjects(@Path("profileId") profileId: String): ObjectListDto

@Streaming
@GET("v1/profiles/{profileId}/objects/{namespace}")
suspend fun getObject(...): Response<ResponseBody>   // ETag lu dans les headers

@PUT("v1/profiles/{profileId}/objects/{namespace}")
suspend fun putObject(
    @Path("profileId") profileId: String,
    @Path("namespace") namespace: String,
    @Header("X-Schema-Version") schemaVersion: Int,
    @Header("If-Match") ifMatch: String?,             // null = création
    @Body body: RequestBody                            // vnd.cstv.blob+gzip
): Response<Unit>
```

- L'ETag est transporté **entre guillemets** dans `ETag`/`If-Match` et **nu**
  dans le JSON de listing : le stockage local retient la forme nue et les
  guillemets sont posés à l'envoi.
- `DELETE` est implémenté par symétrie avec le contrat backend mais n'est appelé
  par aucun flux nominal (un namespace vidé reste un snapshot vide).
- Le client HTTP est celui de F33 (même hôte, même `Authorization`), avec des
  timeouts allongés pour les octets : `readTimeout` 20 s, `callTimeout` 60 s.
- `HttpLoggingInterceptor` reste à `BASIC` : aucun payload gzip ne doit être
  loggué.

## 4.6 Mapping des erreurs

| Réponse | Action |
| --- | --- |
| `204` | ETag et base remplacés atomiquement, `pending = false` |
| `401` | `CstvSessionGuardInterceptor` (F33) fait transiter l'état ; le moteur arrête ses envois |
| `403` | idem : blocage applicatif immédiat, sans traitement propre au moteur |
| `404 PROFILE_NOT_FOUND` | arrêt des envois de ce profil, rechargement de la liste des profils, jamais de recréation |
| `412 ETAG_MISMATCH` | fusion à trois voies puis réessai (3 tentatives max) |
| `413 PAYLOAD_TOO_LARGE` | `failureCode` posé, aucun réessai automatique identique, diagnostic non technique |
| `428 PRECONDITION_REQUIRED` | rechargement du listing pour récupérer l'ETag, puis réessai |
| `415`/`422` | traité comme `Incompatible`, aucun réessai |
| `IOException`/`5xx` | `pending` conservé, réessai différé avec backoff |

## 4.7 Performances et volumétrie

- Le cycle de démarrage coûte **un** `GET /objects` par profil ; un ETag
  identique n'entraîne aucun téléchargement.
- La reconstruction d'un snapshot lit une seule table Room scopée par profil ;
  les mutations rapprochées sont coalescées (débounce 2 s par namespace), ce qui
  borne le nombre de PUT lors d'un ajout massif de favoris.
- Garde-fou de taille : si le gzip dépasse `MAX_OBJECT_SIZE_BYTES` (1 MiB), le
  PUT n'est pas émis (échec local `PAYLOAD_TOO_LARGE`) plutôt que de générer un
  aller-retour réseau garanti perdant.
- Sérialisation, compression et fusion s'exécutent sur `Dispatchers.IO` /
  `Dispatchers.Default`, jamais sur le thread principal.
- `playback` respecte les trois déclencheurs produit : aucun push par tick,
  aucun worker périodique dédié.

## 4.8 Risques techniques

| Risque | Mitigation |
| --- | --- |
| Envoi d'une version locale plus ancienne après une plus récente | un `Mutex` par `(profil, namespace)` et une reconstruction du snapshot **au moment de l'envoi** (jamais de payload mis en file) |
| Boucle `412` infinie entre deux installations actives | 3 tentatives puis report à la synchronisation suivante, avec backoff |
| Suppression locale annulée par une fusion | la suppression est déduite du diff avec la base synchronisée, pas de l'absence dans le snapshot local |
| Application d'un snapshot appartenant à un autre compte | `accountId` de la session revérifié avant application, et purge complète au changement de compte (F33) |
| Test figé par une boucle de synchronisation | aucune boucle inconditionnelle : la file est un `Channel` conflaté drainé sur événement ; le worker WorkManager est testé via ses fonctions pures |
| Fuite de payload en logs | niveau `BASIC` imposé, assertions dans les tests du codec |

---

# 5. Architecture

## 5.1 Vue d'ensemble

```
Repositories métier (favoris, playback, ratings, ...)
        | 1. écrit Room       2. markDirty(profileId, namespace)
        v
   Room  <----- application atomique des fusions -----+
        |                                             |
        v                                             |
CloudSyncManager  --(débounce, Mutex par clé)-->  SyncEngine
        |                                             |
        |                             SnapshotSerializer (7) + SnapshotCodec (gzip)
        |                                             |
        |                                     SnapshotMerger (3 voies)
        v                                             |
CstvObjectsApiService <---------------------------- ETag / If-Match
        ^
        |
ProfileSyncStateDao (etag, base, pending, failure)
```

L'UI n'observe jamais le réseau : elle observe Room, qui est mis à jour
atomiquement à l'issue d'une fusion.

## 5.2 Cycle d'une mutation locale

1. Le repository métier écrit Room (comportement actuel inchangé).
2. Il appelle `CloudSyncManager.markDirty(profileId, namespace)`, qui pose
   `pending = true` et envoie la clé dans un `Channel` conflaté.
3. Le moteur attend le débounce (2 s), prend le `Mutex` de la clé, **relit
   Room**, construit le document, le compresse.
4. `etag == null` → PUT sans `If-Match` (création) ; sinon PUT avec l'ETag
   courant.
5. `204` → l'ETag renvoyé et le document envoyé deviennent la nouvelle base,
   `pending = false`, dans une transaction Room.
6. Échec réseau → `pending` reste vrai, `CloudSyncWorker` reprendra.

Comme le payload est reconstruit à l'étape 3, une mutation survenue entre-temps
est incluse : il est structurellement impossible d'envoyer une version périmée.

## 5.3 Cycle de rapprochement (démarrage, reprise, retour réseau, après 412)

Pour chaque profil associé (`remoteId != null`) :

1. `GET /objects` → métadonnées.
2. Namespace absent localement et distant → rien.
3. ETag distant == ETag local et `pending == false` → rien.
4. ETag distant différent → `GET` du snapshot, fusion à trois voies avec la base
   et l'état local, application atomique dans Room, mise à jour de la base et de
   l'ETag.
5. Namespace absent côté distant mais présent localement → création sans
   `If-Match`.
6. Namespace absent côté distant sans base locale prouvant une suppression
   synchronisée → **aucun effacement** de Room.
7. `pending == true` → envoi (5.2) après l'éventuelle fusion.

## 5.4 Fusion à trois voies

Entrées : `base` (dernier snapshot accepté), `local` (reconstruit depuis Room),
`remote` (snapshot serveur courant). Pour chaque clé de l'union :

| base | local | remote | Résultat |
| --- | --- | --- | --- |
| = local | ≠ base | — | local (changement local seul) |
| = remote | — | ≠ base | remote (changement distant seul) |
| — | ≠ base | ≠ base, == local | valeur commune |
| absent de local, présent en base | supprimé localement | inchangé | suppression conservée |
| présent en base, absent du remote | inchangé | supprimé à distance | suppression conservée |
| — | ≠ base | ≠ base et ≠ local | résolveur du namespace |

Résolveurs par namespace :

- `playback` : `lastAccessedAt` le plus récent, **sans** préférer la position la
  plus élevée (un retour volontaire en arrière doit survivre).
- `recently-watched-live` : `watchedAt` le plus récent.
- `series-watch-state` : `updatedAt` le plus récent, puis `max()` composante par
  composante sur `lastKnown*` et `lastNotified*` pour ne jamais faire régresser
  un épisode déjà connu ou déjà notifié.
- `favorites`, `ratings`, `track-preferences`, `category-preferences` :
  l'intention locale en attente gagne.

En l'absence de base (toute première fusion), une clé présente des deux côtés
est arbitrée par horodatage lorsqu'il existe, sinon la valeur **cloud** gagne,
et une absence locale n'est jamais interprétée comme une suppression.

## 5.5 Initialisation après F33

- **Cloud vide** : chaque namespace est sérialisé depuis Room — y compris vide,
  pour que l'état vide soit explicite — puis créé sans `If-Match`. Un échec
  laisse `pending = true` sans jamais toucher Room.
- **Cloud alimenté** : le rapprochement 5.3 s'applique avec la règle « sans
  base » de 5.4.
- L'initialisation est considérée terminée lorsque chaque namespace est dans un
  état cohérent : envoyé, déjà distant, ou marqué pour réessai.
- Le listing `GET /objects` de cette phase est aussi ce qui permet à F33 de
  savoir si le `Profil 1` cloud automatique est réellement vierge.

## 5.6 Déclencheurs

| Événement | Source |
| --- | --- |
| session CSTV résolue et profils réconciliés | F33 (`ResolveCstvSessionUseCase`) |
| démarrage avec session valide | `MainActivity` |
| reprise de l'application | `ON_RESUME` observé dans `MainActivity` |
| retour réseau | `NetworkMonitor.isOnline` |
| après `412` | moteur de synchronisation |
| mutation locale | repositories métier |
| reprise après mort du process | `CloudSyncWorker` (`OneTimeWorkRequest`, contrainte `CONNECTED`, backoff exponentiel) |

Aucun déclencheur périodique : ni ticker, ni `PeriodicWorkRequest` playback,
conformément à la décision produit et à la règle AGENTS.md sur les boucles de
tests.

## 5.7 Playback

`markDirty(playback)` est appelé exactement à trois moments, depuis le ViewModel
du lecteur : démarrage effectif du média (durée et position de départ connues),
mise en pause, fin naturelle. La sortie ou la destruction du lecteur n'appelle
rien. Les sauvegardes Room restent à leur fréquence actuelle : elles protègent
la reprise locale et seront incluses dans le prochain envoi déclenché.

Le retrait explicite de « Continuer à regarder » est une suppression locale
ordinaire et déclenche `markDirty(playback)`.

## 5.8 Isolation

- Chaque requête cible `remoteId` du profil et porte le JWT du compte actif.
- Avant d'appliquer un snapshot, le moteur revérifie que `accountId` de la
  session n'a pas changé depuis la requête ; sinon le résultat est ignoré.
- Le changement de compte (F33) purge `profile_sync_state` (ETags, bases,
  `pending`) dans la même transaction que la purge des profils.
- La suppression d'un profil supprime ses lignes `profile_sync_state` ; le
  backend supprime ses snapshots par cascade.

## 5.9 Décisions techniques et justifications

1. **Snapshot complet reconstruit à l'envoi plutôt que payload mis en file** —
   supprime par construction la classe de bugs « version périmée envoyée après
   une plus récente ».
2. **Base de fusion stockée compressée dans Room** — un seul format en mémoire
   et sur disque, et la table reste sous le plafond serveur ; pas de fichiers
   annexes à purger.
3. **Sept sérialiseurs explicites plutôt qu'une réflexion générique sur les
   entités Room** — le format réseau devient un contrat stable, indépendant des
   refactorings d'entités, et testable champ par champ.
4. **Clé métier textuelle typée (`movie:1234`)** — évite toute collision entre
   types partageant un espace d'identifiants Xtream.
5. **Débounce par namespace plutôt que file globale** — un ajout de favoris ne
   retarde pas un envoi playback.
6. **Aucun `updatedAt` ajouté à `ratings`/`track-preferences`** — leur règle de
   conflit est déterministe sans horodatage ; l'ajout coûterait deux migrations
   pour rien.
7. **`DELETE` de snapshot non utilisé** — un namespace vidé reste un snapshot
   vide, ce qui distingue « vide synchronisé » de « jamais initialisé » sans
   journal serveur.

## 5.10 Tests prévus (JVM uniquement)

- `SnapshotCodecTest` : aller-retour gzip, document vide, UTF-8, taille au-delà
  du plafond, `schemaVersion` inconnue.
- `*SnapshotSerializerTest` (×7) : Room → document → Room sans perte, champs
  nullables, `profileId` jamais sérialisé.
- `SnapshotMergerTest` : chaque ligne du tableau 5.4, suppressions locales et
  distantes, première fusion sans base, absence locale non interprétée comme
  suppression.
- `ConflictResolversTest` : playback au `lastAccessedAt` le plus récent avec
  position inférieure, `watchedAt`, non-régression de `series-watch-state`,
  priorité de l'intention locale.
- `CloudSyncManagerTest` : création sans `If-Match`, mise à jour avec ETag,
  `412` → fusion → réessai, `412` répété borné, `428` → rechargement, `413` sans
  réessai, `404` sans recréation de profil, hors ligne → `pending` conservé puis
  envoi au retour réseau, coalescence de mutations rapprochées, snapshot vide
  après suppression du dernier objet.
- `CloudSyncIsolationTest` : changement de compte purgeant ETags, bases et
  `pending` ; snapshot d'un autre compte jamais appliqué.
- `PlaybackSyncTriggerTest` : envoi au démarrage/pause/fin, aucun envoi sur tick
  ni sur sortie du lecteur.

---

# 6. Plan de développement

F33 doit être livrée avant T5 (identité des profils, `remoteId`, session). T1 à
T4 sont des briques pures, testables sans backend ni session.

Chaque tâche se termine par `./gradlew assembleDebug lintDebug testDebugUnitTest`
au vert, tests des phases précédentes inclus.

---

- [x] **T1 — Room 26 → 27 : état de synchronisation**

Objectif :
Persister ETag, base de fusion, drapeau `pending` et diagnostic par
`(profil, namespace)`.

Fichiers :
- `data/local/entity/ProfileSyncStateEntity.kt`
- `data/local/dao/ProfileSyncStateDao.kt`
- `data/local/db/AppDatabase.kt` (version 27)
- `data/local/db/Migrations.kt` (`MIGRATION_26_27` + `ALL_MIGRATIONS`)
- `domain/sync/SyncNamespace.kt` (enum des sept namespaces)
- `di/AppModule.kt`

Validation :
SQL relu contre l'entité ; tests DAO : upsert, lecture par profil, suppression
par profil, `baseSnapshot` nul et non nul. Aucune comparaison d'entité reposant
sur `equals` d'un `ByteArray`.

---

- [x] **T2 — Contrat réseau des snapshots**

Objectif :
Lire et écrire des octets opaques avec ETag et `X-Schema-Version`.

Fichiers :
- `data/remote/api/CstvObjectsApiService.kt`
- `data/remote/dto/ObjectMetadataDto.kt`
- `data/remote/CstvEtag.kt` (forme nue ↔ forme entre guillemets)
- `di/AppModule.kt` (réutilise le client CSTV de F33, timeouts allongés)
- `app/proguard-rules.pro` (`-keep interface ...CstvObjectsApiService`)

Validation :
Tests avec `MockWebServer` ou service mocké : ETag lu dans l'en-tête d'un `GET`,
`If-Match` absent à la création et présent à la mise à jour, en-tête
`X-Schema-Version` posé, `Content-Type` `application/vnd.cstv.blob+gzip`,
conversion guillemets ↔ nu dans les deux sens.

---

- [x] **T3 — Codec de snapshot**

Objectif :
Sérialiser un document de namespace en JSON gzip et le relire.

Fichiers :
- `data/cloudsync/NamespaceSnapshot.kt`
- `data/cloudsync/SnapshotCodec.kt`

Validation :
`SnapshotCodecTest` : aller-retour, document vide, UTF-8 avec accents et
emojis, `schemaVersion` supérieure au client → `Incompatible` sans exception,
JSON tronqué → `Incompatible`, dépassement de `MAX_OBJECT_SIZE_BYTES` détecté
avant tout envoi.

---

- [x] **T4 — Sérialiseurs et moteur de fusion**

Objectif :
Traduire les sept tables Room en documents et implémenter la fusion à trois
voies avec ses résolveurs.

Fichiers :
- `data/cloudsync/serializer/*.kt` (sept sérialiseurs)
- `data/cloudsync/merge/SnapshotMerger.kt`
- `data/cloudsync/merge/ConflictResolvers.kt`

Validation :
- `*SnapshotSerializerTest` (×7) : Room → document → Room sans perte, champs
  nullables, `profileId` jamais sérialisé, clé métier conforme au tableau 4.3.
- `SnapshotMergerTest` : chaque ligne du tableau 5.4, suppression locale,
  suppression distante, première fusion sans base, absence locale non
  interprétée comme suppression.
- `ConflictResolversTest` : playback au `lastAccessedAt` le plus récent avec
  position **inférieure**, `watchedAt`, non-régression de `series-watch-state`
  sur `lastKnown*` et `lastNotified*`, priorité de l'intention locale pour les
  quatre namespaces sans horodatage.

---

- [x] **T5 — Moteur de synchronisation**

Objectif :
Orchestrer envoi, rapprochement, conflits et erreurs pour un profil donné.

Fichiers :
- `domain/sync/CloudSyncManager.kt`, `domain/sync/CloudSyncStatus.kt`
- `data/cloudsync/CloudSyncManagerImpl.kt`
- `di/AppModule.kt`

Contenu : `markDirty` + `Channel` conflaté + débounce 2 s + `Mutex` par clé,
reconstruction du snapshot **au moment de l'envoi**, cycle de rapprochement
5.3, mapping d'erreurs 4.6, garde-fou de taille.

Validation :
`CloudSyncManagerTest` : création sans `If-Match`, mise à jour avec ETag, `412`
→ fusion → réessai, `412` répété borné à 3 puis report, `428` → rechargement du
listing, `413` sans réessai, `404` sans recréation de profil, `401`/`403`
délégués à F33, hors ligne → `pending` conservé, coalescence de mutations
rapprochées, ETag identique → aucun téléchargement, snapshot vide après
suppression du dernier objet, aucune boucle inconditionnelle.

---

- [x] **T6 — Initialisation après F33**

Objectif :
Traiter le premier contact d'une installation : cloud vide, cloud alimenté, ou
les deux.

Fichiers :
- `data/cloudsync/CloudSyncManagerImpl.kt` (entrée `initializeForAccount`)
- `data/repository/CstvAuthRepositoryImpl.kt` (appel après réconciliation des
  profils)
- implémentation réelle de `CloudProfileEmptinessProbe` (F33/T8) via
  `GET /objects`

Validation :
Tests : cloud vide → sept namespaces créés y compris vides ; cloud alimenté →
fusion sans base ; échec réseau partiel → aucune donnée Room perdue, namespaces
restants marqués pour réessai ; profil nouvellement exporté par F33 → règle
« cloud vide ».

---

- [x] **T7 — Marquage des mutations métier**

Objectif :
Déclencher la synchronisation depuis les six repositories concernés, après
écriture Room.

Fichiers :
- `data/repository/FavoritesRepositoryImpl.kt`
- `data/repository/ViewingHistoryRepositoryImpl.kt`
- `data/repository/MediaRatingRepositoryImpl.kt`
- `data/repository/TrackPreferenceRepositoryImpl.kt`
- `data/repository/SeriesWatchStateRepositoryImpl.kt`
- `data/repository/CategoryPreferenceRepositoryImpl.kt`

Validation :
Un test par repository : Room écrit **avant** `markDirty`, namespace correct,
aucun appel réseau émis directement par le repository, retrait explicite de
« Continuer à regarder » marquant `playback`.

---

- [x] **T8 — Déclencheurs playback**

Objectif :
Pousser `playback` exactement au démarrage effectif, à la pause et à la fin
naturelle.

Fichiers :
- `presentation/player/**` (ViewModels lecteur film et série)

Validation :
`PlaybackSyncTriggerTest` : envoi aux trois moments prévus, aucun envoi sur
tick de progression, sur passage en arrière-plan, ni sur sortie/destruction du
lecteur ; la sauvegarde Room conserve sa fréquence actuelle.

---

- [x] **T9 — Déclencheurs applicatifs et reprise**

Objectif :
Lancer le rapprochement aux moments prévus et survivre à la mort du process.

Fichiers :
- `MainActivity.kt` (démarrage, `ON_RESUME`)
- `data/network/NetworkMonitorImpl.kt` consommé par `CloudSyncManagerImpl`
  (retour réseau)
- `data/worker/CloudSyncWorker.kt`
- `data/worker/SyncScheduling.kt` (`OneTimeWorkRequest`, contrainte
  `CONNECTED`, backoff exponentiel)

Validation :
Tests des fonctions pures du worker (sélection des profils/namespaces
`pending`, calcul du backoff). Aucun `PeriodicWorkRequest`, aucun ticker : le
projet interdit les tâches périodiques inconditionnelles.

---

- [x] **T10 — Isolation, purge et diagnostic**

Objectif :
Garantir l'étanchéité entre comptes et profils, et rendre les échecs lisibles.

Fichiers :
- `data/repository/ProfileRepositoryImpl.kt` (suppression de profil →
  suppression de ses lignes `profile_sync_state` ; `purgeAllProfiles()` purge
  la table)
- `data/cloudsync/CloudSyncManagerImpl.kt` (revérification de `accountId` avant
  application d'un snapshot)
- `presentation/settings/SettingsScreen.kt` + `SettingsViewModel.kt` (état de
  synchronisation : à jour / en attente / incompatible / trop volumineux, en
  langage non technique)

Validation :
`CloudSyncIsolationTest` : changement de compte purgeant ETags, bases et
`pending` ; snapshot reçu pour un `accountId` obsolète jamais appliqué ;
suppression de profil supprimant ses états ; message `413` sans terme technique.

---

- [x] **T11 — Vérification transverse**

Objectif :
Contrôler les exigences non fonctionnelles avant review.

Actions :
- vérifier qu'aucun log ne contient de payload gzip, de token ni de donnée de
  profil ;
- vérifier qu'aucun DTO de snapshot ne référence catalogue, credentials,
  téléchargements ou données TMDB/YouTube ;
- `assembleRelease` pour la règle R8 de `CstvObjectsApiService` ;
- reprise de la liste des critères d'acceptation de la section 3, un par un ;
- `./gradlew testDebugUnitTest` complet, sans test désactivé ni supprimé.

---

# 7. Notes de développement

- Étapes 1 et 2 : idée structurée et contrat fonctionnel établi à partir du
  backend CSTV validé et des décisions du PO.
- Dépendance : F33 doit fournir une session CSTV valide et l'association entre
  profils locaux et profils cloud.
- Étape 3 : spécification technique et architecture arrêtées. Points notables :
  snapshot JSON gzip `schemaVersion 1` avec clés métier textuelles, table
  `profile_sync_state` (Room 26 → 27, F33 posant la 26), reconstruction du
  snapshot au moment de l'envoi, fusion à trois voies avec résolveur par
  namespace, déclencheurs strictement événementiels.
- Aucune colonne `updatedAt` n'est ajoutée à `media_ratings` ni
  `track_preferences` : leur règle de conflit est déterministe sans horodatage.
- `GET /objects` sert aussi à F33 pour vérifier que le `Profil 1` cloud
  automatique est vierge avant de l'apparier à un profil local.
- Étape 4 : onze tâches (T1 → T11). T1 à T4 sont des briques pures testables
  sans backend ni session ; T5 et suivantes exigent F33 livrée. T6 fournit
  l'implémentation réelle de `CloudProfileEmptinessProbe` laissée en attente
  par F33/T8.
- Étape 5 : démarrage de l'implémentation. La migration 26 → 27 crée
  `profile_sync_state`; le contrat Retrofit opaque, la normalisation d'ETag,
  les types de namespace et le codec JSON/gzip sont présents et testés. Les
  sérialiseurs des sept tables, la fusion à trois voies, le moteur, les
  déclencheurs et la reprise WorkManager restent à terminer avant review.
- Étape 6 : review technique menée sur les briques livrées (T1 → T3
  partielles), sans modification de code. Un point critique — index créé par
  `MIGRATION_26_27` mais non déclaré sur l'entité, qui crashe Room sur une
  installation existante — et cinq points majeurs, dont le codec qui lève une
  exception au dépassement de taille et confond « schéma incompatible », « trop
  volumineux » et « payload illisible ». Le moteur lui-même (T4 → T11) n'étant
  pas livré, une review complète devra être repassée ensuite. Détail en
  section 8.
- Étape 7 : corrections appliquées dans l'ordre demandé : index Room déclaré,
  résultats typés du codec, DTO défensif, sérialisation/fusion, moteur ETag
  coalescé, reprise WorkManager, déclencheurs de mutations et isolation par
  compte. Les tests JVM complets sont lancés mais leur résultat final reste à
  relever avant toute étape 8.
- Étape 8 : **validation non acquise**. Les checks ciblés communs F33/F34
  passent et l'APK debug compile, mais la couverture obligatoire du moteur,
  des sérialiseurs, de l'isolation et des déclencheurs playback est absente.
  L'audit statique confirme aussi que `playback` n'est pas marqué aux trois
  événements requis (démarrage, pause, fin) : seules les écritures périodiques
  Room et certaines suppressions existent. Enfin, l'application distante du
  snapshot enchaîne plusieurs DAO sans transaction Room, donc l'atomicité
  requise n'est pas démontrée. Le statut reste `FIXES`; aucune étape 9 n'est
  engagée.
- Étape 7 (complément après les bloqueurs relevés à l'étape 8) : l'application
  d'un snapshot est maintenant obligatoirement exécutée dans une transaction
  `AppDatabase.withTransaction` (sans repli hors transaction). Les players VOD
  et séries marquent le namespace `playback` au démarrage effectif, à la pause
  et à la fin naturelle. `MarkPlaybackSyncUseCaseTest` vérifie le profil actif
  et le namespace ciblé; les tests de codec et de fusion restent exécutés avec
  ces corrections. La validation d'étape 8 doit être rejouée avant tout
  changement de statut.
- Étape 8 (rejouée après correction) : `testDebugUnitTest` complet est vert
  (735 tests, aucun échec), `assembleDebug` et `lintDebug` sont verts, et
  `git diff --check` est propre. La transaction d'application est câblée dans
  la base Room de production et ne possède plus de repli non atomique. La
  validation fonctionnelle inter-installations reste en attente d'un compte
  CSTV et d'un OTP autorisés pour exercer les opérations distantes réelles ;
  aucun défaut de code connu ne reste dans le périmètre de l'étape 7.
- Étape 8 (constat réseau) : le backend CSTV fourni répond `401` à `GET
  /v1/me` sans jeton le 2026-08-12. Les endpoints de snapshots ne sont pas
  exercés sans session authentifiée : un compte et l'OTP associé doivent être
  fournis explicitement pour la validation inter-installations.
- Étape 6 (nouvelle passe, 2026-08-12) : le moteur T4 → T11, non livré lors de
  la review de section 8, a depuis été implémenté (voir étapes 7/8
  ci-dessus) ; cette passe le relit intégralement puisqu'aucune review
  complète n'avait encore porté dessus. Un défaut majeur et deux lacunes de
  périmètre trouvés, détail en section 8 (« Review — complément 2026-08-12 ») :
  - **Majeur** : `CloudSyncManagerImpl.synchronizeNamespace` n'envoyait jamais
    au serveur le résultat d'une fusion sans base (premier contact d'un
    profil, cloud et local tous deux alimentés) lorsque le namespace n'était
    pas déjà marqué `pending` — l'objet local restait donc indéfiniment
    confiné à Room, jamais visible depuis une autre installation, en
    contradiction avec l'objectif « empêcher la perte silencieuse de
    données ».
  - **Majeur** : aucun test n'existait sur `CloudSyncManagerImpl` (`T5`),
    alors que 5.10 l'exige explicitement — le moteur le plus sensible du
    ticket (mutex par clé, retries `412` bornés, ETag, isolation de compte)
    tournait sans filet.
  - **Majeur** : `state.cloudSyncStatus` était exposé par `SettingsViewModel`
    mais jamais lu par `SettingsScreen` (mobile et TV) — `T10` exige un état
    de synchronisation visible en langage non technique dans les Paramètres.
- Étape 7 (2026-08-12) : les trois points ci-dessus corrigés. Le moteur pousse
  désormais la fusion dès que son résultat diffère de ce que le serveur
  possède, indépendamment de `pending` (comparaison par contenu, pas par
  bytes gzip, pour rester insensible à un réordonnancement des clés).
  `RoomSnapshotSerializer` est passé derrière une interface `SnapshotSerializer`
  (même motif que `ProfileManager`, cf. règle Mockito d'AGENTS.md) pour rendre
  `CloudSyncManagerImpl` testable sans backend ; `CloudSyncManagerTest` (8 cas :
  création sans `If-Match`, mise à jour avec ETag, saut rapide à ETag
  identique, première fusion important seulement du distant, première fusion
  avec contenu local à repousser — le défaut ci-dessus, reproduit puis vérifié
  corrigé —, `412` répété borné à 3, hors ligne sans aucun appel réseau,
  payload trop volumineux refusé localement) accompagne le correctif. Les
  Paramètres (mobile et TV) affichent désormais l'état de synchronisation
  cloud sous l'email du compte CSTV, en quatre libellés non techniques
  (`cstv_sync_idle/pending/incompatible/too_large`, un repli générique
  `cstv_sync_failed`). `./gradlew testDebugUnitTest lintDebug assembleDebug`
  vert (869 tests, 0 échec). Les cases T1 → T10 de la section 6 reflètent
  désormais l'état réel du code ; T11 (vérification transverse : relecture des
  logs, `assembleRelease`, reprise des dix-neuf critères d'acceptation un par
  un) n'a pas été rejouée dans cette passe et reste ouverte.
- Étape 8 (décision PO, 2026-08-12) : comme pour F33, le PO choisit
  explicitement de ne pas exécuter le parcours multi-installations réel
  (favoris/positions ajoutés sur une install, retrouvés sur une autre) avant
  livraison — accepté sur la base des vérifications automatisées (869 tests
  JVM dont désormais `CloudSyncManagerTest`, build/lint verts). En cas
  d'anomalie en usage réel, correctif en hotfix plutôt qu'en bloquant la
  livraison. T11 (vérification transverse) reste néanmoins recommandée avant
  archivage, indépendamment de ce choix.

---

# 8. Review

Review technique du 2026-08-12, réalisée sur l'arbre de travail courant sans
modification du code. F34 dépendant de F33, les défauts partagés (client HTTP,
mapping d'erreurs, migrations) sont détaillés dans la review de F33 et
seulement rappelés ici.

## État d'avancement constaté

| Tâche | État |
| --- | --- |
| T1 — Room 26 → 27 | partiel (entité, DAO, migration, enum posés ; migration défectueuse — C1 ; aucun test DAO) |
| T2 — Contrat réseau des snapshots | partiel (service Retrofit, DTOs, `CstvEtag`, R8, DI posés ; aucun test d'en-têtes) |
| T3 — Codec de snapshot | partiel (`NamespaceSnapshot`, `SnapshotCodec` posés ; couverture de test incomplète) |
| T4 → T11 | non commencées |

Commandes exécutées : `assembleDebug` succès, `lintDebug` **échec** (cf.
F33/C1), `testDebugUnitTest` succès — 5 cas de test au total pour F34
(`SnapshotCodecTest`, `CstvEtagTest`).

## Critique

### C1 — `MIGRATION_26_27` crée un index non déclaré sur `ProfileSyncStateEntity`

Description : la migration exécute
`CREATE INDEX IF NOT EXISTS index_profile_sync_state_profileId ON profile_sync_state(profileId)`,
mais `ProfileSyncStateEntity` ne déclare aucun `indices` dans son `@Entity`.
Room valide les index à l'ouverture de la base.

Impact : identique à F33/C2 — sur une installation migrée, le schéma trouvé
contient un index que le schéma attendu ignore, ce qui lève
`IllegalStateException: Migration didn't properly handle` et crashe le
démarrage. Une installation neuve ne reproduit pas le défaut. Sans
infrastructure `androidTest`, aucun test ne l'aurait détecté.

Correction attendue : déclarer
`@Entity(tableName = "profile_sync_state", primaryKeys = ["profileId", "namespace"], indices = [Index("profileId")])`,
ou retirer le `CREATE INDEX`. Relire ensuite le SQL de la migration contre le
schéma généré, colonnes et index compris. À traiter avec F33/C2, dans la même
passe.

## Majeur

### M1 — Le cœur du ticket n'est pas livré : T4 à T11

Description : les sept sérialiseurs, `SnapshotMerger`, `ConflictResolvers`,
`CloudSyncManagerImpl`, `CloudSyncWorker`, le marquage des mutations dans les
six repositories métier, les déclencheurs playback et les déclencheurs
applicatifs n'existent pas. `CloudSyncManager`, `CloudSyncStatus`,
`SyncNamespace`, `ProfileSyncStateDao` et `CstvObjectsApiService` sont
aujourd'hui du code mort : aucune implémentation, aucun appelant, et aucun
provider Hilt pour `CloudSyncManager` ni pour `SnapshotCodec`.

Impact : aucun des dix-neuf critères d'acceptation de la section 3 n'est
vérifiable. Rien n'est synchronisé ; les briques posées ne sont pas encore
reliées entre elles, donc leurs interfaces n'ont pas été confrontées à un
usage réel.

Correction attendue : livrer T4 à T11 puis repasser une review complète. Le
présent document ne juge que les briques T1 à T3.

### M2 — Critères de validation de T1, T2 et T3 non couverts

Description :

- T1 exigeait des tests DAO (upsert, lecture par profil, suppression par
  profil, `baseSnapshot` nul et non nul) : aucun n'existe.
- T2 exigeait des tests d'en-têtes (`If-Match` absent à la création et présent à
  la mise à jour, `X-Schema-Version` posé, `Content-Type`
  `application/vnd.cstv.blob+gzip`, ETag lu dans l'en-tête d'un `GET`) : aucun
  n'existe, et aucun code ne construit encore de `RequestBody`, donc le
  `Content-Type` n'est validé nulle part.
- T3 exigeait `schemaVersion` supérieure au client, JSON tronqué et dépassement
  de `MAX_OBJECT_SIZE_BYTES` : `SnapshotCodecTest` ne couvre que l'aller-retour
  vide, l'UTF-8 et un gzip invalide. Le cas « gzip valide mais JSON tronqué »
  n'est pas testé, alors que c'est le chemin réaliste d'un payload corrompu.

Impact : trois tâches déclarées livrables sans que leurs propres critères de
validation soient atteints ; les règles de robustesse du format sont
supposées, pas vérifiées.

Correction attendue : compléter les trois jeux de tests avant de poursuivre sur
T4.

### M3 — `SnapshotCodec.encode` lève une exception au dépassement de taille

Description : `encode` termine par
`require(it.size <= maxBytes) { "PAYLOAD_TOO_LARGE" }`, donc une
`IllegalArgumentException` traverse l'appelant.

Impact : 4.7 demande un garde-fou local qui empêche l'émission du `PUT` et pose
`failureCode = PAYLOAD_TOO_LARGE`, et 4.6 demande un diagnostic utilisateur non
technique sans réessai. Une exception de programmation force le futur moteur à
attraper un `IllegalArgumentException`, ce qui masquerait au passage toute autre
erreur d'argument.

Correction attendue : renvoyer un résultat typé (par exemple
`SnapshotEncodeResult.TooLarge` / `Success(bytes)`) et couvrir le seuil par un
test.

### M4 — `decode` confond trois situations distinctes

Description : `SnapshotDecodeResult.Incompatible` est renvoyé pour un
`schemaVersion` **différent** de 1 — y compris inférieur, alors que 4.3 ne vise
que « supérieure à celle du client » — mais aussi pour un payload dépassant
`MAX_OBJECT_SIZE_BYTES`, et pour un JSON illisible.

Impact : 4.6 traite ces cas différemment (`Incompatible` sans réessai pour un
schéma inconnu, `PAYLOAD_TOO_LARGE` avec diagnostic utilisateur pour la taille).
Les fusionner interdit au moteur de choisir le bon traitement, et un futur
`schemaVersion 0` ou une version antérieure serait rejeté sans raison.

Correction attendue : distinguer les résultats (`Incompatible`, `TooLarge`,
`Malformed`) et n'appliquer la règle d'incompatibilité qu'aux versions
supérieures à `SCHEMA_VERSION`.

### M5 — DTO de métadonnées non défensif

Description : `ObjectMetadataDto` déclare `etag`, `schemaVersion`,
`compressedSize` et `updatedAt` non nullables. Gson ne vérifie pas la nullité
Kotlin : un champ absent produit un `null` dans une propriété non-null, et le
`NullPointerException` survient plus tard, loin de la cause.

Impact : contraire à la règle de parsing défensif d'AGENTS.md, appliquée partout
ailleurs pour les réponses Xtream. Une évolution du backend, ou une réponse
partielle, crasherait la synchronisation au lieu de la marquer en échec.

Correction attendue : déclarer les champs nullables côté DTO et valider
explicitement lors du mapping vers le domaine.

## Mineur

- **m1** — `CstvEtag.bare` ne gère pas les ETags faibles : `W/"abc"` devient
  `W/"abc` (le préfixe empêche le retrait du guillemet ouvrant). Le backend n'en
  émet pas aujourd'hui, mais un `If-Match` reconstruit à partir de cette valeur
  serait invalide. À normaliser ou à documenter comme non supporté.
- **m2** — `CstvEtagTest` ne couvre ni `bare(null)`, ni `header(null)`, ni
  l'aller-retour `header(bare(x))`, qui est pourtant l'usage réel.
- **m3** — `SyncNamespace.fromWireName` et `CloudSyncStatus` ne sont référencés
  nulle part : à relier dès T5 ou à retirer si le moteur retient une autre
  forme.
- **m4** — `ProfileSyncStateEntity` porte un `ByteArray` dans une `data class`
  sans rappel dans le code que `equals`/`hashCode` générés sont inutilisables
  (4.4). Un commentaire ou une redéfinition explicite éviterait une comparaison
  d'entité accidentelle.
- **m5** — Les timeouts longs prévus pour F34 (`readTimeout` 20 s,
  `callTimeout` 60 s) ont été appliqués au client CSTV unique, ce qui pénalise
  le gate de F33 (cf. F33/M5). F34 devra dériver son client via `newBuilder()`.
- **m6** — `X-Schema-Version` est un paramètre libre de `putObject` : rien ne
  garantit qu'il vaille `SnapshotCodec.SCHEMA_VERSION`. À figer dans le moteur
  et à couvrir par un test lors de T5.
- **m7** — `deleteObject` est exposé alors que 5.9 précise qu'il n'est appelé
  par aucun flux nominal : ajouter un commentaire sur l'interface pour éviter
  un usage accidentel.
- **m8** — Les cases du plan de développement (section 6) sont toutes décochées
  alors que T1 à T3 sont partiellement livrées : le suivi doit refléter l'état
  réel après correction.

## Corrections demandées

Ordre conseillé pour l'étape 7 :

1. C1 — déclarer l'index sur `ProfileSyncStateEntity` (avec F33/C2).
2. M3, M4 — résultats typés du codec pour la taille et l'incompatibilité.
3. M5 — DTO de métadonnées défensif.
4. M2 — compléter les tests de validation de T1, T2 et T3.
5. m1 → m8 — l'ensemble des points mineurs.
6. M1 — livrer T4 à T11, puis nouvelle review complète.

---

## Review — complément 2026-08-12

M1 (livrer T4 à T11) est désormais fait ; cette passe couvre ce qui n'avait
jamais été relu (le moteur lui-même). Commandes exécutées :
`./gradlew testDebugUnitTest lintDebug assembleDebug` — succès, 869 tests,
0 échec.

### Majeur

#### M13 — Une fusion sans base pouvait ne jamais repartir vers le serveur

Description : dans `synchronizeNamespace`, après téléchargement et fusion à
trois voies, le code retournait sans PUT dès que `!state.pending`, en
enregistrant `baseSnapshot = remoteBytes` — c'est-à-dire le contenu **distant**
brut, alors que Room venait d'être mis à jour avec le résultat **fusionné**,
potentiellement plus riche (objets présents seulement en local). La prochaine
synchronisation prenait alors le chemin rapide « ETag identique, non pending »
sans jamais détecter l'écart : l'objet local restait durablement invisible du
serveur et de toute autre installation.

Impact : viole directement l'objectif de F34 « empêcher la perte silencieuse
de données », précisément dans le scénario que la section 3 documente le plus
(« Cloud et local tous deux alimentés »). Aucun test n'aurait pu le voir :
`SnapshotMergerTest` teste la fusion pure, pas la décision d'envoi du moteur,
et `CloudSyncManagerImpl` n'avait aucun test (M14).

Correction appliquée : la décision de pousser compare désormais le contenu
fusionné à celui du serveur (`local.objects == remote.objects`) plutôt que de
se fier au seul drapeau `pending` ; un contenu identique n'est pas repoussé
inutilement, un contenu différent l'est toujours. Couvert par
`CloudSyncManagerTest` (« first contact with local-only content is pushed
back even though nothing was pending »), qui échouait avant correctif.

#### M14 — `CloudSyncManagerImpl` (T5) sans aucun test

Description : ni `CloudSyncManagerTest` ni `ConflictResolversTest` n'existaient,
alors que 5.10 et le critère de validation de T5 les exigent nommément. Le
moteur combine mutex par clé, débounce, retries `412` bornés, distinction
création/mise à jour d'ETag et isolation de compte — la partie la plus à
risque du ticket restait entièrement non vérifiée.

Correction appliquée : `RoomSnapshotSerializer` extrait derrière une interface
`SnapshotSerializer` (le projet n'a pas `mockito-inline`, cf. règle Mockito
d'AGENTS.md, donc une classe finale ne peut pas être mockée directement) ;
`CloudSyncManagerTest` ajouté avec 8 cas couvrant création sans `If-Match`,
mise à jour avec ETag courant, saut rapide à ETag identique, première fusion
(import seul, puis le cas M13), `412` répété borné, hors ligne, payload trop
volumineux.

#### M15 — État de synchronisation cloud jamais affiché (T10)

Description : `SettingsState.cloudSyncStatus` était peuplé par
`SettingsViewModel` mais ni `SettingsScreen` mobile ni la variante TV ne le
lisaient. La section 3 exige explicitement un état visible « à jour / en
attente / incompatible / trop volumineux », en langage non technique.

Correction appliquée : un libellé (`cstv_sync_idle` / `_pending` /
`_incompatible` / `_too_large` / repli `_failed`) est affiché sous l'email du
compte CSTV dans les deux variantes de l'écran Paramètres.

### Corrections appliquées

M13, M14, M15 ci-dessus. M1 → M9 et m1 → m9 de la review d'origine restent
adressées comme décrit dans les Notes de développement (étapes 7).

### T11 — Vérification transverse (2026-08-12)

- Logs : aucun `Log`/`println` dans `data/cloudsync/**` ni dans les clients
  CSTV ; `HttpLoggingInterceptor` du client CSTV plafonné `BASIC` +
  `redactHeader("Authorization")` (jamais `BODY`, même en debug) — aucun
  payload gzip ni jeton ne peut apparaître dans logcat.
- DTOs : `CstvDtos.kt` et `data/cloudsync/**` grepés sans résultat pour
  Xtream/credential/password/TMDB/YouTube/download — aucune fuite de type.
- `./gradlew :app:assembleRelease` : **succès**, `minifyReleaseWithR8`
  compris — la règle `-keep interface ...CstvObjectsApiService` (comme
  `CstvApiService`) tient face à R8.
- Critères d'acceptation de la section 3 : repris un par un, tous cochés
  ci-dessus. Seule nuance mineure : sur `404 PROFILE_NOT_FOUND`, le moteur
  arrête bien les envois et ne recrée jamais le profil (le critère tel
  qu'écrit), mais ne déclenche pas de rechargement immédiat de la liste des
  profils comme le décrit le tableau 4.6 — un profil supprimé à distance
  reste visible localement jusqu'au prochain `resolveSession()` (démarrage,
  `ON_RESUME`, retour réseau), qui le corrige. Non bloquant, à corriger à
  l'occasion plutôt qu'en urgence.
- `./gradlew testDebugUnitTest lintDebug` : vert, **870 tests, 0 échec**
  (869 + le test d'isolation compte ajouté pendant cette passe), aucun test
  désactivé ni supprimé (`@Ignore`/`@Disabled` absents du module).

T11 est maintenant complète. F34 peut passer en `RELEASE` dès que le PO
valide la livraison (le parcours live reste volontairement non exécuté,
décision PO du 2026-08-12 ci-dessus).

---

# 9. Release

Version :
v1.78.0

Commit :
e8a7406ab05bfb78c04305f7fc910f8a6a1fb965

Date :
2026-08-12
