# T19 - Plafonds par item et hygiène des snapshots de sync (côté application)

## Informations générales

Status:
REVIEW (étape 7 — corrections T19-R1–R4 appliquées, RESOLVED ; en attente d'une nouvelle review étape 6 ou de la validation étape 8)

Created:
2026-08-13

Sévérité:
MOYEN (hygiène de données / UX ; complète la frontière dure de T14)

---

# 1. Description

Le backend borne désormais l'abus de stockage par des frontières **dures** (T14 : 10 profils/compte, 32 namespaces/profil, 20 Mio/compte, 1 Mio/blob). Ces frontières sont incontournables mais grossières : elles comptent des octets, pas des items, car chaque namespace est un **blob gzip opaque** que le backend ne lit jamais.

Ce ticket ajoute, **côté application**, les plafonds par item et l'hygiène des snapshots pour : (1) garder l'UX propre (pas de listes qui enflent sans fin), (2) garder les blobs petits et bien sous le plafond backend, (3) présenter proprement les nouvelles erreurs de quota du backend.

Ce n'est **pas** une frontière de sécurité (un client modifié la contourne) ; la sécurité reste assurée par les plafonds backend de T14. Voir [[T14-backend-quotas-profils-namespaces]].

---

# 2. Contexte

## Ce que le backend voit / ne voit pas

Le backend stocke un blob opaque par namespace (`favorites`, `playback`, `recently-watched-live`, …). Il ne peut donc pas plafonner le **nombre** de favoris ou de chaînes vues. Seule l'app, qui construit le snapshot depuis Room (`RoomSnapshotSerializer`), peut le faire.

## Mesures de dimensionnement (gzip level 6, 2026-08-13)

| Snapshot | Items | Gzip |
|---|---|---|
| `playback` **avec** `plot` | 10 000 | ~0,80 Mio (marge fine sous 1 Mio) |
| `playback` **sans** `plot` | 10 000 | ~0,30 Mio |
| `recently-watched-live` | 20 | ~1 Kio |

`plot`, `releaseDate`, `coverUrl` dans une position de reprise sont des métadonnées ré-hydratables depuis le catalogue : les synchroniser pour 10 000 items est du gaspillage et rapproche dangereusement du plafond blob.

## Décisions PO (2026-08-13)

- `playback` : **10 000** items max.
- `recently-watched-live` : **20** items max.
- `favorites` : **500** items max.
- **Retirer `plot`** (et champs lourds ré-hydratables) du snapshot `playback`.

---

# 3. Spécification fonctionnelle

## Règles métier

1. Avant chaque sync (ou à l'écriture Room), élaguer chaque liste au plus récent : `playback` → 10 000, `recently-watched-live` → 20, `favorites` → 500. L'ordre d'élagage suit l'horodatage (`lastAccessedAt`, `watchedAt`, `addedAt`).
2. Le snapshot `playback` ne sérialise plus `plot` (ni les autres champs purement d'affichage ré-hydratables retenus lors du design). Les positions restent complètes en Room ; seul le document syncé est allégé.
3. Les nouveaux codes d'erreur backend sont traités sans trace technique : `PROFILE_LIMIT_REACHED` (409), `NAMESPACE_LIMIT_REACHED` (409), `STORAGE_QUOTA_EXCEEDED` (413), `OTP_VERIFY_RATE_LIMITED` (429).

## Critères d'acceptation

- [ ] Une liste dépassant son plafond est élaguée au plus récent avant sync ; les items les plus anciens sont retirés localement de façon cohérente (pas de réapparition au prochain merge cloud).
- [ ] Le document `playback` uploadé ne contient plus `plot` ; une restauration sur un nouvel appareil réaffiche la reprise, le `plot` se re-remplissant depuis le catalogue.
- [ ] `PROFILE_LIMIT_REACHED` à la création de profil affiche un message clair (« nombre maximal de profils atteint »).
- [ ] `STORAGE_QUOTA_EXCEEDED` / `NAMESPACE_LIMIT_REACHED` en sync n'affichent pas de stack trace et n'interrompent pas l'app ; statut de sync explicite.
- [ ] `OTP_VERIFY_RATE_LIMITED` à la connexion affiche « trop de tentatives, réessayez plus tard ».

## Cas limites

- L'élagage ne doit pas supprimer un item que l'utilisateur vient de créer au profit d'un ancien encore pertinent : trier strictement par horodatage décroissant, garder les N premiers.
- Le merge cloud (F34, ETags) doit rester cohérent après élagage : élaguer **avant** de calculer le snapshot à pousser, pas après réception.
- Retrait de `plot` : vérifier qu'aucun écran ne lit `plot` **uniquement** depuis la position de reprise sans repli catalogue.

---

# 4. Spécification technique

## Composants impactés (app)

- `data/cloudsync/RoomSnapshotSerializer.kt` — exclure `plot` (et champs retenus) du `playback` sérialisé ; appliquer l'élagage au plus récent par namespace lors du `snapshot()`.
- DAO playback / recently-watched / favorites — requêtes d'élagage (`DELETE ... WHERE id NOT IN (SELECT ... ORDER BY <ts> DESC LIMIT N)`), ou élagage en mémoire avant sérialisation.
- `data/remote/CstvErrorMapper.kt` — mapper les nouveaux codes vers des `CstvError` présentables.
- Écrans concernés (login, sélection de profil, statut de sync) — messages utilisateur.

## Composants backend

Aucun — les frontières dures sont livrées par T14. Les valeurs `MAX_PROFILES_PER_ACCOUNT=10` et `MAX_STORAGE_BYTES_PER_ACCOUNT=20 Mio` sont déjà en place.

## Tests (app, JVM local)

- Sérialiseur : un jeu de données > plafond produit un snapshot élagué au bon nombre, trié par horodatage ; le `playback` sérialisé ne contient pas `plot`.
- Mapper d'erreurs : chaque nouveau code → l'état d'erreur attendu.

---

# 5. Architecture

L'élagage et l'allègement vivent dans la couche `data/cloudsync` (frontière de sérialisation), jamais dans l'UI. Room reste la source complète ; seul le document poussé est borné. Les plafonds sont des constantes de l'app (miroir informel des attentes backend), ajustables au même endroit que `SnapshotCodec`.

---

# 6. Plan de développement

- [x] Constantes de plafonds (`SnapshotLimits` : playback 10000, recently-watched 20, favorites 500).
- [x] Élagage au plus récent par namespace avant sérialisation (`List.capMostRecent` pure ; recently-watched capé par la requête DAO).
- [x] Retrait de `plot` du snapshot `playback` (`SyncNamespace.strippedFields()` + `JsonElement.withoutFields`).
- [x] Mapping des 4 codes backend (`CstvErrorMapper` → `ProfileLimit` / `StorageQuota` / `RateLimited`).
- [x] Messages utilisateur : `ProfileViewModel` (ProfileLimit/StorageQuota → chaînes dédiées) ; `OTP_VERIFY_RATE_LIMITED` → `RateLimited` déjà couvert par `CstvAuthViewModel`. Échec de sync `StorageQuota` rendu terminal dans `CloudSyncManagerImpl.recordFailure`.
- [x] Tests unitaires JVM : `SnapshotLimitsTest` (cap, strip, constantes) + `CstvErrorMapperTest` (4 nouveaux codes). Suite app : **887 tests**.

---

# 7. Notes de développement

Dépend de [[T14-backend-quotas-profils-namespaces]] (backend livré, étape 7). Ticket créé après décision PO du 2026-08-13 (valeurs d'items + retrait `plot`).

## Étape 5 (2026-08-13) — décisions de conception

- **Élagage au niveau du sérialiseur (en mémoire), pas de suppression Room.** Le cap est appliqué dans `RoomSnapshotSerializer.snapshot()` via des fonctions **pures** (`capMostRecent`, `strippedFields`, `withoutFields`), donc testables en JVM — conforme à la stratégie du projet (pas de tests instrumentés Room, cf. AGENTS.md). Cela borne ce qui est **poussé** ; comme `apply()` remplace la table (`deleteAll` + insert du snapshot), un round-trip de sync **borne aussi le local**. Une suppression Room dure a été écartée : elle ne serait couverte par aucun test JVM et le quota d'octets backend (T14) + la sémantique replace d'`apply()` bornent déjà l'état synchronisé/restauré.
- **Nuance merge** : le résultat fusionné (local capé ⊕ remote capé) peut transitoirement dépasser N si les deux ensembles sont disjoints ; il reste largement sous le plafond blob (playback 20000 sans `plot` ≈ 0,6 Mio < 1 Mio) et reconverge au push suivant. Réaliste car deux appareils d'un même utilisateur se recouvrent fortement.
- **Retrait de `plot` uniquement** : champ mesuré dominant (10000 positions : 0,80 → 0,30 Mio). `title`/`coverUrl` conservés (rendu de la tuile de reprise sans lookup catalogue) ; `releaseDate`/`duration` négligeables.
- **Codes d'erreur** : `PROFILE_LIMIT_REACHED` → `ProfileLimit`, `STORAGE_QUOTA_EXCEEDED`/`NAMESPACE_LIMIT_REACHED` → `StorageQuota`, `OTP_VERIFY_RATE_LIMITED` → `RateLimited`. La sync `StorageQuota` est **terminale** (pas de re-push en boucle).
- **Validation** : `assembleDebug` + `lintDebug` + `testDebugUnitTest` (887 tests) — voir Release.

## Étape 7 (2026-08-13) — corrections R1–R4

- **T19-R1 (normalisation post-merge)** : ajout de `SyncNamespace.itemLimit()` / `timestampField()` et de l'extension `NamespaceSnapshot.normalized(namespace)` dans `SnapshotLimits.kt` — réapplique cap-par-horodatage et retrait de champs à un snapshot déjà construit, y compris le résultat d'un merge. Câblée dans `CloudSyncManagerImpl` immédiatement après `SnapshotMerger.merge(...)`, avant que `local` ne serve à `serializer.apply()` (Room) et `codec.encode()` (corps poussé). `withoutFields()` et le tri par horodatage sont devenus défensifs (no-op si l'élément n'est pas un `JsonObject`) pour rester purs face à des fixtures de test ou des formes de valeur non prévues, sans changer le comportement en production (les valeurs réelles sont toujours des `JsonObject` issus de `gson.toJsonTree(entity)`).
- **T19-R2 (élagage persistant)** : trois nouvelles requêtes DAO bornées par lot (`FavoritesDao.pruneToMostRecent`, `VodDao.prunePlaybackToMostRecent`, `LiveTvDao.pruneRecentlyWatchedToMostRecent`, toutes basées sur `rowid` — aucune des trois tables n'a de clé primaire à colonne unique), appelées en tête de chaque branche de `RoomSnapshotSerializer.snapshot()` avant la lecture. Room reste donc borné à la limite réelle même quand `serializer.apply()` n'est jamais invoqué (premier envoi sans divergence distante). L'élagage en mémoire (`capMostRecent`) reste en place en ceinture-bretelles, désormais un no-op la plupart du temps.
- **T19-R3 (statut de quota explicite)** : nouveau `CstvError.NamespaceLimit`, distinct de `StorageQuota`, propagé par `CstvErrorMapper` (`NAMESPACE_LIMIT_REACHED` → `NamespaceLimit`), `CloudSyncManagerImpl.recordFailure` (code fidèle au wire, toujours terminal) et `SettingsScreen.cloudSyncStatusStringRes` (deux nouvelles chaînes `cstv_sync_storage_quota` / `cstv_sync_namespace_limit`, ni génériques ni « temporaire »).
- **T19-R4 (tests traversant le vrai câblage)** : `RoomSnapshotSerializerTest` (DAO mockés, prouve l'appel des trois requêtes de purge + le cap/retrait sur un vrai `NamespaceSnapshot`), `CloudSyncManagerMergeNormalizationTest` (un objet playback distant portant encore `plot` le perd à la fois dans ce qui est appliqué à Room et dans le corps HTTP réellement poussé), deux nouveaux tests de statut terminal dans `CloudSyncManagerTest`, `SnapshotLimitsTest` étendu (cap réel sur `RECENTLY_WATCHED_LIVE`, idempotence, namespace non plafonné), `ProfileViewModelTest` (`CstvException(ProfileLimit)` / `StorageQuota` → libellé dédié, cas générique → fallback), `CstvAuthViewModelTest` (`verifyOtp` + `RateLimited` → message T16 dédié), `CstvErrorMapperTest` corrigé sur la nouvelle distinction R3.
- **Piège rencontré** : `Mockito.thenThrow(CstvException(...))` échoue (`MockitoException: Checked exception is invalid for this method!`) — `CstvException` étend `Exception`, pas `RuntimeException`, et la méthode mockée ne la déclare pas. Résolu avec `.thenAnswer { throw CstvException(...) }`, pattern déjà utilisé ailleurs dans la suite (`CstvAuthRepositoryImplTest`, `ProfileRepositoryImplTest`) pour les mêmes raisons.
- Suite complète verte : **902 tests** (`./gradlew testDebugUnitTest`). `assembleDebug` et `lintDebug` : BUILD SUCCESSFUL.

---

# 8. Review

Date : 2026-08-13

Status : CHANGES REQUESTED

## Périmètre relu

- `app/src/main/java/com/cstv/app/data/cloudsync/SnapshotLimits.kt`
- `app/src/main/java/com/cstv/app/data/cloudsync/RoomSnapshotSerializer.kt`
- `app/src/main/java/com/cstv/app/data/cloudsync/CloudSyncManagerImpl.kt`
- `app/src/main/java/com/cstv/app/data/cloudsync/merge/SnapshotMerger.kt`
- `app/src/main/java/com/cstv/app/data/remote/CstvErrorMapper.kt`
- `app/src/main/java/com/cstv/app/presentation/profile/ProfileViewModel.kt`
- `app/src/main/java/com/cstv/app/presentation/cstv/CstvAuthViewModel.kt`
- `app/src/main/java/com/cstv/app/presentation/settings/SettingsScreen.kt`
- tests JVM associés

## Critique

Aucun constat.

## Majeur

### T19-R1 — Le merge contourne les plafonds et peut réintroduire `plot`

**Description :** `RoomSnapshotSerializer.snapshot()` normalise seulement le
snapshot local initial. Quand l'ETag distant a changé,
`CloudSyncManagerImpl` remplace ensuite cette valeur par le résultat brut de
`SnapshotMerger.merge()`, l'applique à Room puis l'encode sans repasser par les
plafonds ni par `strippedFields()`. Deux ensembles disjoints de N éléments sont
donc poussés avec jusqu'à 2N éléments. De même, un objet playback distant plus
récent est choisi avec son ancien champ `plot`, puis ré-uploadé tel quel.

**Impact :** les plafonds PO ne sont pas garantis « avant chaque push » et le
document playback peut encore contenir `plot`. Le dépassement est même écrit en
Room avant upload ; la note qui l'accepte comme transitoire contredit les
critères d'acceptation et peut rapprocher à nouveau le blob de la limite 1 Mio.

**Correction attendue :** introduire une normalisation de
`NamespaceSnapshot` réutilisable après chaque merge et avant `apply()`/`encode()`
(cap par horodatage et retrait des champs). Ajouter des tests avec ensembles
local/distant disjoints au plafond et avec un playback distant récent contenant
`plot`, en vérifiant le snapshot appliqué et le corps réellement poussé.

### T19-R2 — Les éléments élagués ne sont pas supprimés localement et peuvent réapparaître

**Description :** les caps utilisent `take(N)` en mémoire ou un `LIMIT` de
lecture, mais aucune ligne Room excédentaire n'est supprimée. Lors d'un premier
upload sans divergence distante, `serializer.apply()` n'est jamais appelé :
Room reste donc au-dessus du plafond. Si un élément récent est ensuite supprimé,
un ancien élément resté en base rentre de nouveau dans les N premiers et est
repoussé au cloud.

**Impact :** le critère « items les plus anciens retirés localement, pas de
réapparition au prochain merge » n'est pas satisfait. L'état local peut croître
sans fin et ressusciter des favoris ou historiques que le snapshot avait écartés.

**Correction attendue :** rendre l'élagage persistant et atomique à la frontière
choisie (écriture Room ou préparation de sync), avec un ordre déterministe en
cas d'horodatages égaux. Couvrir la suppression d'un item récent puis une
nouvelle sync afin de prouver qu'un item précédemment élagué ne réapparaît pas.

### T19-R3 — Une erreur de quota terminale est présentée comme un incident temporaire

**Description :** `recordFailure()` convertit les deux erreurs de quota en
`CloudSyncStatus.Failed("STORAGE_QUOTA_EXCEEDED")` et les rend terminales, mais
`SettingsScreen.cloudSyncStatusStringRes()` ne distingue que
`PAYLOAD_TOO_LARGE`. L'utilisateur voit donc
`cstv_sync_failed` (« problème temporaire »), sans statut explicite ni action
possible. Le code namespace est également perdu dans le regroupement.

**Impact :** le worker cesse de réessayer alors que l'UI annonce un problème
temporaire ; l'utilisateur ne sait pas qu'il doit réduire ses données ou ses
namespaces. Le quatrième critère d'acceptation de T19 n'est pas rempli.

**Correction attendue :** conserver un code de statut explicite pour stockage
et cardinalité, ajouter les ressources utilisateur correspondantes et les
router dans l'écran de statut. Tester que ces erreurs sont terminales et que le
libellé affiché n'est ni générique ni présenté comme temporaire.

### T19-R4 — Les tests ne traversent pas le câblage de production ajouté par le ticket

**Description :** `SnapshotLimitsTest` ne teste que trois fonctions pures ; il
n'instancie pas `RoomSnapshotSerializer`. Aucun test ne prouve le cap et le
retrait de `plot` sur un vrai `NamespaceSnapshot`, le comportement post-merge,
la persistance locale, le statut terminal de `CloudSyncManagerImpl`, le message
`ProfileLimit` de `ProfileViewModel`, ni le chemin `verifyOtp` avec
`RateLimited`. Les seuls tests ajoutés au mapper ne couvrent pas ces consommateurs.

**Impact :** les 887 tests restent verts malgré T19-R1 à R3 et malgré l'absence
de preuve des messages d'acceptation. La couverture annoncée dans le plan est
donc inférieure au contrat de test du ticket.

**Correction attendue :** ajouter des tests JVM du sérialiseur avec DAO mockés,
du manager avec réponse quota, du ViewModel profil avec `CstvException`, et du
ViewModel OTP sur `verifyOtp`. Les scénarios post-merge de T19-R1 et de
non-réapparition de T19-R2 doivent faire partie de cette couverture permanente.

## Mineur

Aucun constat supplémentaire.

## Corrections demandées

- [x] T19-R1 — `NamespaceSnapshot.normalized(namespace)` (cap + retrait de champs, réutilisable et idempotente) réappliquée au résultat de `SnapshotMerger.merge()` avant `serializer.apply()` et `codec.encode()` (RESOLVED).
- [x] T19-R2 — Élagage rendu persistant en base : `FavoritesDao.pruneToMostRecent`, `VodDao.prunePlaybackToMostRecent`, `LiveTvDao.pruneRecentlyWatchedToMostRecent`, appelés depuis `RoomSnapshotSerializer.snapshot()` avant lecture (RESOLVED).
- [x] T19-R3 — `CstvError.NamespaceLimit` distinct de `StorageQuota` de bout en bout (mapper → `CloudSyncManagerImpl` → `SettingsScreen`), statuts `STORAGE_QUOTA_EXCEEDED`/`NAMESPACE_LIMIT_REACHED` terminaux avec libellés dédiés non génériques (RESOLVED).
- [x] T19-R4 — Tests JVM traversant le vrai câblage : `RoomSnapshotSerializerTest` (DAO mockés), `CloudSyncManagerMergeNormalizationTest` (post-merge, snapshot appliqué + corps poussé), tests de statut terminal dans `CloudSyncManagerTest`, `ProfileViewModelTest` (CstvException ProfileLimit/StorageQuota), `CstvAuthViewModelTest` (verifyOtp RateLimited) (RESOLVED).

## Vérifications effectuées

- `./gradlew --no-daemon --max-workers=1 testDebugUnitTest assembleDebug lintDebug` : succès (`testDebugUnitTest`, `assembleDebug` et analyse lint à jour ; 887 tests, 0 échec dans les rapports).
- Les quatre nouveaux codes backend sont bien distingués par `CstvErrorMapper`.
- Le message de création de profil est câblé dans `ProfileViewModel`, mais n'est pas testé.
- `git diff --check` et le contrôle des tickets non suivis passent.

## Limite de la review

Conformément à la stratégie du projet, aucun test device ou manuel n'a été pris
en compte. Aucun correctif de code n'a été appliqué et l'étape 7 n'est pas
engagée.
---

# 9. Release

Version :

Commit :

Date :
