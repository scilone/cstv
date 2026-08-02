# T9 - Optimisation des performances et du temps de chargement de l'Accueil sur TV

## Informations générales

Status:
VALIDATED

Created:
2026-08-02

---

# 1. Description

L'application souffre d'un temps de chargement relativement long (4 à 5 secondes de loader circulaire) après la sélection du profil sur Android TV. Ce ralentissement se produit alors même que l'intégralité du catalogue est en cache local dans Room et qu'aucun appel réseau bloquant n'est en cours.

L'analyse de l'initialisation de l'Accueil a identifié deux goulots d'étranglement majeurs :
1. **Le calcul des "Derniers ajouts" (Films et Séries)** : Il charge l'intégralité du catalogue (des dizaines de milliers d'objets) depuis Room en mémoire pour effectuer un tri et un filtrage CPU complexe dans Kotlin. (Ce goulot d'étranglement sera résolu de façon fonctionnelle par le ticket **F20**).
2. **Le filtrage de la liste "Continuer à regarder" (Playback Positions)** : Pour masquer immédiatement les médias en cours de lecture qui appartiennent à des catégories masquées par le profil, l'application charge à chaque démarrage l'intégralité du catalogue de films et de séries (`getCachedVodStreams("all")` et `getCachedSeriesStreams("all")`) uniquement pour obtenir la catégorie (`categoryId`) associée à chaque identifiant de média.

L'objectif de ce ticket technique est d'éliminer définitivement le goulot d'étranglement n°2 en stockant directement l'identifiant de catégorie (`categoryId`) dans la table des positions de lecture (`playback_positions`). Ainsi, la liste de reprise de lecture pourra être filtrée de façon instantanée (< 5 ms) au démarrage, sans jamais charger l'intégralité du catalogue.

---

# 2. Contexte

Dans l'état actuel :
* L'entité Room `PlaybackPositionEntity` contient de nombreuses métadonnées sur le média regardé (titre, jaquette, progression, durée, saison, épisode...), mais ne possède pas de colonne `categoryId`.
* Lors de la collecte réactive des positions de lecture dans `HomeViewModel.kt` :
  ```kotlin
  val vodMap = vodRepository.getCachedVodStreams("all").associate { it.streamId to it.categoryId }
  val seriesMap = seriesRepository.getCachedSeriesStreams("all").associate { it.seriesId to it.categoryId }
  ```
  Ces deux requêtes lisent des milliers de lignes en base de données, les instancient et les transforment en maps à chaque modification ou au démarrage de l'Accueil. Sur une TV de faible puissance, cette opération prend plusieurs secondes et bloque la réactivité de l'application.

En ajoutant une colonne `categoryId` à la table `playback_positions` et à son entité correspondante, nous pourrons filtrer directement les catégories masquées à partir de l'objet `PlaybackPosition` lui-même.

---

# 3. Spécification fonctionnelle et technique

## Résultat utilisateur attendu

* **En tant qu'utilisateur Android TV**, après avoir choisi mon profil, l'Accueil devient exploitable sans attendre le chargement complet du catalogue en mémoire uniquement pour filtrer mes reprises.
* **En tant qu'utilisateur**, mes médias de reprise appartenant à des catégories masquées restent invisibles ; l'optimisation ne modifie ni l'ordre ni le contenu fonctionnel des autres rangées.

La migration doit préserver toutes les positions existantes. Une position ancienne sans catégorie ne doit jamais faire afficher un média appartenant à une catégorie masquée : elle suit un repli sûr, sans rechargement global du catalogue.

## Objectifs techniques

1. **Évolution de la base de données (Room)** :
   * Ajouter une colonne optionnelle/nullable `categoryId: String?` à l'entité Room `PlaybackPositionEntity` (table `playback_positions`).
   * Réaliser une migration de schéma propre dans Room (version **22** vers **23**) sans perte de données utilisateur ; la version 22 est déjà utilisée par F12.
2. **Évolution du modèle de domaine** :
   * Mettre à jour le modèle `PlaybackPosition` dans la couche `domain` pour y ajouter le champ `categoryId: String?`.
   * Adapter les mappers d'entité vers domaine et inversement.
3. **Persistance de la catégorie lors de la lecture** :
   * S'assurer que lors de la sauvegarde ou de la mise à jour de la position de lecture (dans les lecteurs vidéo ou les repositories de mise à jour de position), l'identifiant de catégorie (`categoryId`) du média soit correctement renseigné et enregistré en base de données.
4. **Optimisation radicale du HomeViewModel** :
   * Dans `HomeViewModel.kt`, supprimer définitivement les appels lourds à `getCachedVodStreams("all")` et `getCachedSeriesStreams("all")` dans la coroutine d'observation des positions de lecture.
   * Utiliser directement le champ `categoryId` présent sur chaque objet `PlaybackPosition` pour appliquer le filtrage des catégories masquées.
   * Gérer de manière robuste les anciennes positions de lecture stockées en base (dont la colonne `categoryId` sera `null`) en effectuant un appel de recherche ponctuel et ultra-rapide par identifiant uniquement pour ces quelques éléments, ou en appliquant un repli sécurisé.

## Critères d'acceptation

- [ ] Après une mise à jour sans perte de données, les nouvelles positions de reprise disposent de leur `categoryId` lorsque le média en fournit un.
- [ ] L'Accueil ne déclenche plus de lecture globale des films et séries uniquement pour filtrer les positions de reprise.
- [ ] Les catégories masquées continuent d'être respectées pour les positions nouvelles comme existantes.
- [ ] En cas de catégorie absente ou non résoluble, l'application privilégie la confidentialité du profil et ne bloque pas l'affichage de l'Accueil.

## Cas limites et gestion des erreurs

- Les positions Live, les positions sans catégorie et les métadonnées historiques incomplètes restent compatibles avec le modèle de reprise actuel.
- Un échec de résolution ponctuelle d'une ancienne position ne doit ni relancer une synchronisation réseau ni échouer l'écran ; la position suit le repli sûr défini ci-dessus.

---

# 4. Spécification technique détaillée

## Fichiers modifiés

| Fichier | Modification |
| --- | --- |
| `data/local/entity/PlaybackPositionEntity.kt` | Nouvelle colonne `categoryId: String? = null`. |
| `data/local/db/AppDatabase.kt` | `version = 22` → `version = 23`. |
| `data/local/db/Migrations.kt` | Nouvelle `MIGRATION_22_23` + ajout à `ALL_MIGRATIONS`. |
| `data/local/dao/VodDao.kt` | Nouvelle requête de résolution de catégorie par `streamId`. |
| `data/local/dao/SeriesDao.kt` | Nouvelle requête de résolution de catégorie par `seriesId`. |
| `domain/model/PlaybackPosition.kt` | Nouveau champ `categoryId: String? = null`. |
| `domain/repository/VodRepository.kt` | Paramètre `categoryId: String? = null` sur `savePlaybackPosition`. |
| `domain/usecase/SavePlaybackPositionUseCase.kt` | Même paramètre, relayé. |
| `data/repository/VodRepositoryImpl.kt` | Résolution + persistance de `categoryId` ; les **deux** mappers entité → domaine (l. 621-640 `getAllPlaybackPositions`, l. 642-666 `observeAllPlaybackPositions`) propagent le champ. |
| `data/repository/SeriesRepositoryImpl.kt` | `savePlaybackPosition` (l. 621-630) renseigne `categoryId`. |
| `presentation/home/HomeViewModel.kt` | Suppression des deux lectures globales du collecteur de positions ; `groupResumeWatching` filtre sur `position.categoryId`. |

## 1. Évolution de la base de données

### Entité

```kotlin
@Entity(tableName = "playback_positions", primaryKeys = ["streamId", "profileId"])
data class PlaybackPositionEntity(
    val streamId: Int,
    val profileId: Int,
    …
    val releaseDate: String? = null,
    val categoryId: String? = null        // NOUVEAU
)
```

### Migration 22 → 23

`categoryId` n'appartient pas à la clé primaire (`streamId`, `profileId`) : un
`ALTER TABLE … ADD COLUMN` suffit, la recopie de table du patron
`MIGRATION_9_10` n'est pas nécessaire.

```kotlin
/**
 * MIGRATION_22_23 (T9) : `categoryId` sur `playback_positions`. La colonne
 * permet de filtrer les reprises appartenant à une catégorie masquée sans
 * relire l'intégralité des catalogues Films et Séries à chaque démarrage.
 *
 * Additive : aucune clé primaire touchée, aucune recopie de table, aucune
 * position perdue. Les deux UPDATE remplissent rétroactivement les positions
 * existantes depuis le cache catalogue déjà présent en base — un profil qui met
 * à jour l'application repart donc avec des reprises déjà catégorisées, sans
 * résolution différée au premier affichage.
 */
val MIGRATION_22_23 = object : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE playback_positions ADD COLUMN categoryId TEXT")
        db.execSQL(
            "UPDATE playback_positions SET categoryId = (" +
                "SELECT v.categoryId FROM vod_streams v WHERE v.streamId = playback_positions.streamId" +
            ") WHERE seriesId IS NULL"
        )
        db.execSQL(
            "UPDATE playback_positions SET categoryId = (" +
                "SELECT s.categoryId FROM series_streams s WHERE s.seriesId = playback_positions.seriesId" +
            ") WHERE seriesId IS NOT NULL"
        )
    }
}

val ALL_MIGRATIONS = arrayOf(…, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23)
```

Les deux `UPDATE` s'exécutent une seule fois, à la mise à jour, sur une table qui
contient au plus quelques centaines de lignes ; les sous-requêtes ciblent
`vod_streams.streamId` (clé primaire) et `series_streams.seriesId` (clé
primaire), donc index natif. Coût négligeable, et **pas** sur le chemin de
démarrage courant.

Si le cache catalogue est vide au moment de la migration (installation neuve
suivie d'une restauration, cache purgé), les sous-requêtes renvoient `NULL` et la
colonne reste nulle : le repli d'exécution décrit plus bas prend alors le relais.

Conformément à `AGENTS.md`, aucun `fallbackToDestructiveMigration()` n'est
introduit. Le projet n'ayant pas d'infrastructure `androidTest`, la migration est
relue manuellement : SQL vérifié contre le schéma de `PlaybackPositionEntity`,
`VodStreamEntity` et `SeriesStreamEntity`.

## 2. Modèle de domaine et mapping

```kotlin
data class PlaybackPosition(
    val streamId: Int,
    …
    val releaseDate: String? = null,
    val categoryId: String? = null        // NOUVEAU
)
```

Le champ est ajouté **en dernier avec valeur par défaut** : aucun site
d'appel construisant un `PlaybackPosition` par paramètres nommés n'est cassé.

Les deux mappers de `VodRepositoryImpl` sont strictement identiques et
dupliqués aujourd'hui (`getAllPlaybackPositions` et `observeAllPlaybackPositions`).
Le ticket en profite pour les factoriser en une seule fonction privée
`PlaybackPositionEntity.toDomain()`, sur le modèle des mappers déjà présents dans
le fichier (`VodStreamEntity.toDomain()`, l. 263) : sans cela, oublier le nouveau
champ dans une seule des deux copies produirait un bug invisible en debug
(la Home passe par `observeAllPlaybackPositions`, les tests pourraient passer par
l'autre).

## 3. Persistance de la catégorie à la sauvegarde

### Nouvelles requêtes DAO

```kotlin
// VodDao
@Query("SELECT categoryId FROM vod_streams WHERE streamId = :streamId LIMIT 1")
suspend fun getCategoryIdForStream(streamId: Int): String?

// SeriesDao
@Query("SELECT categoryId FROM series_streams WHERE seriesId = :seriesId LIMIT 1")
suspend fun getCategoryIdForSeries(seriesId: Int): String?
```

Les deux projettent une **seule colonne** sur une **clé primaire** : accès index,
aucune instanciation d'entité.

### Résolution dans `VodRepositoryImpl.savePlaybackPosition`

La signature reçoit `categoryId: String? = null` ; la résolution suit la même
logique de préservation que les autres champs (l. 581-590 : ne jamais écraser une
valeur connue par un `null`) :

```kotlin
val finalCategoryId = categoryId
    ?: existing?.categoryId
    ?: if (finalSeriesId != null) seriesDao.getCategoryIdForSeries(finalSeriesId)
       else vodDao.getCategoryIdForStream(streamId)
```

`SeriesDao` est injecté dans `VodRepositoryImpl` (constructeur Hilt, même
`AppDatabase`). C'est une dépendance intra-couche `data`, assumée : la table
`playback_positions` est portée par `VodDao` et sert **aussi** aux séries — le
couplage existe déjà de fait (`SeriesRepositoryImpl` écrit dans `vodDao`,
l. 629). L'alternative — faire descendre `categoryId` depuis chaque appelant —
imposerait de modifier `VodViewModel.savePosition` (l. 416-437),
`SeriesViewModel.savePosition` (l. 400-433) et les lecteurs, alors qu'aucun
d'eux ne dispose du `categoryId` de façon fiable (`VodDetails` ne le porte pas).

Le paramètre explicite reste disponible pour un appelant qui connaît déjà la
catégorie : il évite alors la requête de résolution.

### `SeriesRepositoryImpl.savePlaybackPosition`

Ce chemin (l. 621-630) construit un `PlaybackPositionEntity` neuf avec
`OnConflictStrategy.REPLACE` et ne renseigne que trois champs. Il doit lui aussi
résoudre et écrire `categoryId`, faute de quoi une écriture par ce chemin
**effacerait** la catégorie précédemment stockée. Correctif : relire l'existant
(`vodDao.getPlaybackPosition(episodeStreamId, profileId)`) et préserver
`categoryId` comme les autres métadonnées.

## 4. Optimisation du `HomeViewModel`

### Avant (l. 268-300)

```kotlin
val vodMap = vodRepository.getCachedVodStreams("all").associate { it.streamId to it.categoryId }
val seriesMap = seriesRepository.getCachedSeriesStreams("all").associate { it.seriesId to it.categoryId }
```

Deux lectures complètes du catalogue, réexécutées à **chaque** émission du flux
de positions et à chaque changement de préférences de catégories.

### Après

Les deux lectures et les deux paramètres `vodStreamCategoryMap` /
`seriesStreamCategoryMap` de `groupResumeWatching` disparaissent. Le filtrage
devient :

```kotlin
private fun groupResumeWatching(
    allPositions: List<PlaybackPosition>,
    hiddenVodCategories: Set<String>,
    hiddenSeriesCategories: Set<String>,
    catalogIsEmpty: Boolean
): List<PlaybackPosition> {
    val resumeWatchingRaw = allPositions.filter { pos ->
        pos.positionMs > 0 && pos.positionMs < (pos.durationMs - 15000L)
    }
    val filtered = resumeWatchingRaw.filter { pos ->
        val hidden = if (pos.seriesId != null) hiddenSeriesCategories else hiddenVodCategories
        when {
            pos.categoryId != null -> pos.categoryId !in hidden
            // Catégorie non résolue : masquer par défaut protège les
            // préférences du profil. Exception explicite quand le catalogue
            // n'est pas encore en cache — la reprise serait alors masquée pour
            // une raison purement transitoire.
            catalogIsEmpty -> true
            else -> false
        }
    }
    val seenSeriesIds = mutableSetOf<Int>()
    return filtered.filter { pos -> pos.seriesId?.let(seenSeriesIds::add) ?: true }
}
```

`catalogIsEmpty` est obtenu sans lecture lourde, via les compteurs déjà
disponibles (`vodDao.getCategoryCounts()` / équivalent Séries, ou un simple
`SELECT EXISTS`), et non par un chargement de listes.

Le repli « masquer si non résolue » satisfait le critère d'acceptation
« l'application privilégie la confidentialité du profil ». Il ne peut concerner
que des positions antérieures à la migration **et** absentes du cache catalogue
au moment de celle-ci — cas rare et transitoire, puisque toute nouvelle
sauvegarde de position renseigne la colonne.

## API, services

Aucun appel réseau ajouté ni retiré. Aucune interface Retrofit touchée, donc
aucune règle `-keep` à ajouter dans `proguard-rules.pro`.

## Cache

Le cache catalogue Room n'est ni invalidé, ni modifié, ni resynchronisé. T9
change uniquement *ce qui est lu* au démarrage, pas *ce qui est stocké* côté
catalogue.

## Performances

| Opération | Avant | Après |
| --- | --- | --- |
| Lecture Room au démarrage pour filtrer les reprises | 2 × catalogue complet (dizaines de milliers de lignes, instanciées en entités puis en modèles domaine puis en `Map`) | 0 |
| Idem à chaque changement de préférence de catégorie | Rejoué intégralement | 0 |
| Coût du filtrage | O(n) sur le catalogue + O(m) sur les positions | O(m) sur les positions (m ≈ quelques dizaines) |
| Coût ajouté à la sauvegarde d'une position | — | 1 requête mono-colonne sur clé primaire, uniquement si la catégorie est inconnue |

Cible : filtrage de la liste de reprise en moins de 5 ms, conformément à
l'objectif de la description.

## Sécurité

Aucune donnée sensible ajoutée : `categoryId` est un identifiant de catégorie
Xtream déjà présent en clair dans le cache catalogue. Aucun identifiant de
connexion n'est concerné.

## Compatibilité

* **Mise à jour sans perte** : migration additive, positions, favoris,
  historique et profils conservés.
* **Positions Live** : les chaînes ne créent pas de `playback_positions`
  (`RecentlyWatchedLiveEntity` est une table distincte) ; aucun impact.
* **Positions sans catégorie / métadonnées incomplètes** : couvertes par le
  repli ci-dessus, sans exception ni écran bloqué.
* **Multi-profils (Phase 27)** : `categoryId` n'est pas scopé par profil — c'est
  une propriété du média, pas du profil. La clé primaire
  (`streamId`, `profileId`) est inchangée.
* **min SDK 21, Room** : `ALTER TABLE ADD COLUMN` et les sous-requêtes
  corrélées sont supportés par toutes les versions de SQLite embarquées depuis
  l'API 21.

## Dépendances

Aucune dépendance Gradle ajoutée.

## Risques techniques

| Risque | Gravité | Mitigation |
| --- | --- | --- |
| Colonne oubliée dans un des deux mappers entité → domaine | Bug silencieux : les reprises masquées réapparaîtraient | Les deux mappers sont factorisés en une seule fonction dans le même commit. |
| `SeriesRepositoryImpl.savePlaybackPosition` écrase `categoryId` (REPLACE) | Régression du filtrage après lecture d'un épisode | Traité explicitement en section 3 : relecture de l'existant avant écriture. |
| Migration exécutée avant que le catalogue soit en cache | Positions historiques non catégorisées | Repli d'exécution documenté ; la première sauvegarde de position les recatégorise. |
| Média retiré du catalogue Xtream mais toujours en reprise | Position masquée alors qu'elle était visible | Comportement voulu : un média hors catalogue n'a pas à être proposé en reprise. L'exception `catalogIsEmpty` évite le faux positif au démarrage à froid. |
| Version de base bumpée sans migration ajoutée à `ALL_MIGRATIONS` | Crash au premier accès | Point de contrôle explicite de la review (étape 6) ; `AppModule.provideDatabase()` utilise `.addMigrations(*ALL_MIGRATIONS)`. |

## Contraintes de performance

L'écriture d'une position de lecture intervient périodiquement pendant la
lecture : la requête de résolution ajoutée ne doit pas s'exécuter à chaque tick.
C'est garanti par l'ordre de résolution (`existing?.categoryId` avant tout accès
DAO) — dès la première sauvegarde, la valeur est en base et les suivantes la
réutilisent.

---

# 5. Architecture

## Position dans la Clean Architecture

```
data/local/
├── entity/PlaybackPositionEntity.kt   ← + categoryId
├── dao/VodDao.kt                      ← + getCategoryIdForStream(streamId)
├── dao/SeriesDao.kt                   ← + getCategoryIdForSeries(seriesId)
└── db/{AppDatabase, Migrations}.kt    ← v23 + MIGRATION_22_23

data/repository/
├── VodRepositoryImpl.kt      ← résout et persiste categoryId ; mapper factorisé
└── SeriesRepositoryImpl.kt   ← préserve categoryId à l'écriture

domain/
├── model/PlaybackPosition.kt          ← + categoryId
├── repository/VodRepository.kt        ← + paramètre categoryId (défaut null)
└── usecase/SavePlaybackPositionUseCase.kt ← relais

presentation/home/HomeViewModel.kt     ← ne lit plus les catalogues complets
```

La couche `presentation` cesse de reconstituer une information qui appartient au
modèle : la catégorie d'un média en reprise devient une **propriété portée par la
position elle-même**, résolue une seule fois à l'écriture, dans la couche `data`.

## Flux de données

### Écriture (une fois par média, à la première sauvegarde)

```
Lecteur (VodPlayerScreen / SeriesPlayerScreen)
      │  savePosition(streamId, positionMs, durationMs, details)
      ▼
VodViewModel / SeriesViewModel
      │
      ▼
SavePlaybackPositionUseCase(…, categoryId = null)
      │
      ▼
VodRepositoryImpl.savePlaybackPosition
      │
      ├─ existing?.categoryId ────────────────► déjà connu : aucune requête
      │
      └─ sinon : seriesId != null ? seriesDao.getCategoryIdForSeries(seriesId)
                                   : vodDao.getCategoryIdForStream(streamId)
                                        (1 colonne, clé primaire)
      ▼
playback_positions.categoryId   (persisté)
```

### Lecture (à chaque démarrage de l'accueil)

```
vodDao.observeAllPlaybackPositions(profileId)     (Flow Room, inchangé)
      │
      ▼
PlaybackPositionEntity.toDomain()   ← categoryId propagé
      │
      ▼
HomeViewModel : combine(positions, categoryPreferenceRepository.changes)
      │
      │   AVANT : getCachedVodStreams("all") + getCachedSeriesStreams("all")   ← SUPPRIMÉ
      │
      ▼
groupResumeWatching(positions, hiddenVod, hiddenSeries, catalogIsEmpty)
      │   filtrage O(m) sur position.categoryId
      ▼
HomeState.resumeWatchingList
```

## Responsabilités des composants

* **`Migrations.kt`** : porter l'évolution du schéma **et** le remplissage
  rétroactif. Le backfill appartient à la migration, pas au code d'exécution :
  il se paie une fois, à froid, hors du chemin de démarrage.
* **`VodRepositoryImpl`** : unique point de résolution de la catégorie d'une
  position, et unique traduction entité ↔ domaine.
* **`HomeViewModel`** : appliquer les préférences de visibilité du profil.
  Il ne reconstitue plus de données catalogue.
* **`PlaybackPosition`** : transporter tout ce qui est nécessaire pour décider de
  l'affichage d'une reprise, sans dépendance à un second chargement.

## Décisions techniques

1. **Colonne dénormalisée plutôt qu'une jointure au moment de la lecture.** Une
   requête `JOIN` entre `playback_positions` et les deux tables catalogue serait
   possible, mais imposerait deux requêtes distinctes (films / séries) réévaluées
   à chaque émission du `Flow`, et ferait dépendre l'affichage des reprises de la
   présence du média dans le cache. La dénormalisation fige l'information au
   moment où elle est certaine (la lecture).
2. **Backfill dans la migration plutôt qu'à l'exécution.** Résoudre à froid les
   positions historiques évite tout code de rattrapage permanent dans le
   `HomeViewModel` — le repli d'exécution reste un filet, pas un mécanisme
   nominal.
3. **`categoryId` nullable et non `NOT NULL DEFAULT ''`.** `null` signifie
   « inconnue » et se distingue d'une catégorie réellement vide ; une chaîne vide
   par défaut rendrait les deux cas indiscernables et fausserait le filtrage.
4. **Repli « masquer » et non « afficher » quand la catégorie est inconnue**,
   avec l'exception `catalogIsEmpty`. Choix dicté par le critère d'acceptation
   (confidentialité du profil), tempéré pour ne pas vider la rangée sur un état
   transitoire.
5. **Résolution côté `data` plutôt que propagation depuis les ViewModels.** Voir
   section 4 : aucun appelant ne dispose du `categoryId` de façon fiable, et le
   propager imposerait de modifier toute la chaîne des lecteurs pour une
   information que la base connaît déjà.
6. **Factorisation des deux mappers dupliqués.** Prévention directe du principal
   risque silencieux du ticket.
7. **Le goulot n° 1 (« Derniers ajouts ») est entièrement traité par F20.**
   Suite à la décision PO du 2026-08-02, F20 supprime aussi le Top 10 de repli
   (`TopRatedSelector`) : plus aucune lecture globale du catalogue ne subsiste
   dans `catalogJob`. T9 traite donc le goulot n° 2 (positions de lecture).
   `refreshRecommendations()` reste un job asynchrone distinct et est suivi
   par T11 ; F20/T9 ne prétendent pas supprimer cette lecture hors périmètre.
   **F20 doit être livré avant T9** pour que le gain local soit mesurable.

## Stratégie de tests

Tests unitaires JVM (`./gradlew testDebugUnitTest`), en priorité haute selon
`AGENTS.md` (repositories + logique de reprise) :

**`VodRepositoryImplTest`** (DAO mockés) :
1. Sauvegarde d'un film sans `categoryId` fourni → `getCategoryIdForStream` est
   appelé une fois et l'entité écrite porte la catégorie retournée.
2. Sauvegarde d'un épisode (`seriesId != null`) → `getCategoryIdForSeries` est
   appelé, pas `getCategoryIdForStream`.
3. Sauvegarde suivante sur la même position → **aucune** requête de résolution
   (la valeur `existing` est réutilisée).
4. `categoryId` passé explicitement → aucune requête de résolution.
5. Mapping entité → domaine : `categoryId` propagé par
   `getAllPlaybackPositions` **et** par `observeAllPlaybackPositions`.

**`SeriesRepositoryImplTest`** :
6. `savePlaybackPosition` sur une position existante ne remet pas `categoryId`
   (ni les autres métadonnées) à `null`.

**`HomeViewModelTest`** :
7. Position dont `categoryId` est masqué → absente de `resumeWatchingList`.
8. Position dont `categoryId` est visible → présente.
9. `categoryId == null` avec catalogue non vide → masquée (repli sûr).
10. `categoryId == null` avec catalogue vide → conservée (exception transitoire).
11. **Non-régression de performance** : `getCachedVodStreams("all")` et
    `getCachedSeriesStreams("all")` ne sont **jamais** appelés par le collecteur
    de positions (`verify(vodRepository, never())`). C'est le test qui garde
    l'optimisation dans le temps.
12. Regroupement des épisodes par `seriesId` (comportement Phase 30) inchangé.

**Migration** : le projet n'a pas d'infrastructure `androidTest`
(`MigrationTestHelper`), la vérification automatisée n'est donc pas possible et
est explicitement exclue des critères de validation. La migration est relue
manuellement, SQL confronté au schéma des trois entités concernées, conformément
à la limite déjà documentée dans `AGENTS.md`.

---

# 6. Plan de développement

## Ordre d'exécution

T9 dépend de F20 pour la suppression complète des lectures globales de l'Accueil.
La migration et la propagation du champ précèdent l'optimisation du ViewModel.

### Tâche 1 — Ajouter et migrer la catégorie des positions de lecture

- [x] Ajouter `categoryId` nullable, la migration 22→23 avec backfill et le
  déclarer dans `ALL_MIGRATIONS`.

Objectif : rendre chaque reprise autonome pour son filtrage, sans migration
destructive.

Fichiers : `PlaybackPositionEntity.kt`, `PlaybackPosition.kt`,
`AppDatabase.kt`, `Migrations.kt`.

Validation : schéma et SQL sont relus manuellement ; le backfill couvre films et
séries ; aucune clé primaire ni donnée de profil n'est perdue.

### Tâche 2 — Propager et persister la catégorie sans surcoût par tick

- [x] Ajouter les projections DAO, factoriser le mapping et préserver la
  catégorie dans les deux chemins d'écriture.

Objectif : résoudre la catégorie une seule fois si elle est absente et éviter
qu'un `REPLACE` d'épisode ne l'efface.

Fichiers : `VodDao.kt`, `SeriesDao.kt`, `VodRepositoryImpl.kt`,
`SeriesRepositoryImpl.kt`, interfaces/use cases touchés.

Validation : film, épisode, valeur fournie et valeur existante suivent les
règles documentées ; aucune requête de résolution répétée.

### Tâche 3 — Simplifier le filtrage des reprises de l'Accueil

- [x] Retirer les lectures de catalogues complets et filtrer les positions par
  `categoryId`, avec le repli sûr pour la valeur inconnue.

Objectif : ramener le chemin de démarrage à O(m) sur les positions de lecture.

Fichiers : `HomeViewModel.kt` et compteurs/DAO minimalement nécessaires.

Validation : catégories masquées respectées, exception catalogue vide préservée
et aucun appel `getCached*Streams("all")` dans le collecteur de positions.

### Tâche 4 — Tester et valider l'évolution

- [x] Ajouter les tests repository et ViewModel ciblés et les exécuter ; les contrôles globaux de l'étape 8 restent à faire.

Fichiers : `VodRepositoryImplTest.kt`, `SeriesRepositoryImplTest.kt`,
`HomeViewModelTest.kt`.

Validation : la matrice de la spécification est couverte ; migration relue
manuellement ; `testDebugUnitTest`, `assembleDebug`, `lintDebug` passent.

---

# 7. Notes de développement

- Écart de plan : la base était déjà en version 22, donc T9 ajoute une migration additive `22→23` sans fallback destructif.
- La migration remplit les positions existantes depuis les tables catalogue ; les écritures résolvent ou préservent ensuite la catégorie.
- Le collecteur de reprises filtre désormais `PlaybackPosition.categoryId` et ne charge plus les catalogues complets.
- Vérification d’implémentation : compilation Kotlin et suites ciblées
  `HomeViewModelTest`, `VodRepositoryImplTest` et `SeriesRepositoryImplTest` réussies. Aucune validation finale ni lint global n'est déclaré à cette étape.

---

# 8. Review

Status: RESOLVED

Revue effectuée le 2026-08-02 sur le diff de travail (aucune modification de code
à cette étape). Périmètre relu : `PlaybackPositionEntity.kt`, `AppDatabase.kt`,
`Migrations.kt`, `VodDao.kt`, `SeriesDao.kt`, `VodRepositoryImpl.kt`,
`SeriesRepositoryImpl.kt`, `AppModule.kt`, `PlaybackPosition.kt`,
`VodRepository.kt`, `SavePlaybackPositionUseCase.kt`, `HomeViewModel.kt` et les
trois suites de tests touchées.

Note d'exécution : les suites n'ont pas pu être relancées pendant la revue, deux
processus Gradle `--no-daemon` antérieurs occupant le lock du projet. La
validation `testDebugUnitTest` / `assembleDebug` / `lintDebug` reste due à
l'étape 8.

## Points conformes

* Migration additive correcte : `ALTER TABLE … ADD COLUMN categoryId TEXT` sans
  toucher à la clé primaire (`streamId`, `profileId`), donc sans recopie de
  table ni perte de position. `MIGRATION_22_23` est bien déclarée dans
  `ALL_MIGRATIONS` (point de contrôle explicite du §4) et `version = 23` est
  cohérente ; aucun `fallbackToDestructiveMigration()` introduit.
* Backfill SQL relu contre le schéma : `vod_streams.streamId` et
  `series_streams.seriesId` sont bien les clés primaires des entités
  correspondantes ; la partition `WHERE seriesId IS NULL` / `IS NOT NULL` couvre
  l'intégralité des lignes.
* Les deux mappers dupliqués sont effectivement factorisés en
  `PlaybackPositionEntity.toDomain()` — le risque n° 1 du ticket est levé à la
  racine, pas contourné.
* Ordre de résolution respecté dans `VodRepositoryImpl.savePlaybackPosition` :
  `categoryId` explicite → `existing?.categoryId` → requête DAO ; les deux
  projections mono-colonne portent bien sur une clé primaire.
* `SeriesRepositoryImpl.savePlaybackPosition` ne détruit plus les métadonnées
  existantes lors du `REPLACE` — le correctif va au-delà de `categoryId` et
  couvre `title`, `coverUrl`, `seriesId`, etc.
* `groupResumeWatching` ne dépend plus d'aucune map catalogue et conserve le
  dédoublonnage par `seriesId` (comportement Phase 30 inchangé).

## Majeur

### M1 — `catalogIsEmpty` réintroduit un balayage complet du catalogue

**Description.** `HomeViewModel.kt` l. 283-288 calcule
`vodRepository.getCategoryCounts().isEmpty() && seriesRepository.getCategoryCounts().isEmpty()`.
La requête sous-jacente est
`SELECT categoryId, COUNT(*) AS count FROM vod_streams GROUP BY categoryId`
(`VodDao.kt` l. 41-42) et `VodStreamEntity` n'a **aucun index sur
`categoryId`** (seul `releaseYear` est indexé, `VodStreamEntity.kt` l. 11) : le
GROUP BY impose un balayage intégral de la table plus une agrégation, suivie
d'une allocation de `List<CategoryCount>` puis de `Map` dans le repository. Ce
calcul est refait à **chaque émission** du flux combiné positions ×
préférences — donc à chaque sauvegarde de position pendant la lecture et à
chaque changement de préférence de catégorie.

**Impact.** Le ticket visait « 0 lecture Room au démarrage pour filtrer les
reprises » et un filtrage sous 5 ms ; le chemin reste en O(catalogue) sur le
critère le plus coûteux (agrégation sans index) au lieu de O(m). Le gain réel
est nettement inférieur à celui annoncé au §4, et le tableau « Performances » du
ticket est inexact en l'état. La §4 prévoyait d'ailleurs explicitement « un
simple `SELECT EXISTS` » — c'est cette option qui n'a pas été retenue.

**Correction attendue.** Remplacer par une projection O(1) :
`@Query("SELECT EXISTS(SELECT 1 FROM vod_streams)")` (et l'équivalent
`series_streams`), exposée par une méthode de repository dédiée, ou calculer le
drapeau une seule fois par `loadHomeData()` et le passer au collecteur. Mettre à
jour le tableau des performances en conséquence.

### M2 — Granularité du repli : un seul drapeau pour deux catalogues

**Description.** `catalogIsEmpty` n'est vrai que si les catalogues Films **et**
Séries sont vides, alors que `groupResumeWatching` l'applique indifféremment aux
positions films et séries (l. 366-372).

**Impact.** Cas réel : catalogue Films déjà synchronisé, Séries pas encore (ou
inversement). `catalogIsEmpty` vaut `false`, donc toute position de série
antérieure à la migration et non backfillée est **masquée** alors que la cause
est purement transitoire — exactement le faux positif que l'exception
`catalogIsEmpty` était censée éviter (§5, décision 4).

**Correction attendue.** Deux drapeaux (`vodCatalogIsEmpty`,
`seriesCatalogIsEmpty`) sélectionnés comme les ensembles `hidden*` selon
`pos.seriesId != null`.

### M3 — Le chargement de l'Accueil lit toujours les deux catalogues complets

**Description.** `loadHomeData()` appelle `refreshRecommendations()`
(`HomeViewModel.kt` l. 565), qui exécute `GetRecommendationsUseCase` ; celui-ci
lit `vodRepository.getCachedVodStreams("all")` et
`seriesRepository.getCachedSeriesStreams("all")`
(`GetRecommendationsUseCase.kt` l. 96-108).

**Impact.** L'affirmation du §5 (« les deux tickets couvrent ensemble la totalité
des lectures globales du chargement de l'Accueil ») est fausse : un profil
disposant d'au moins 3 éléments d'historique déclenche encore deux chargements
complets du catalogue au démarrage de la Home. Le job est certes distinct et ne
bloque pas `isLoading`, mais sur la TV faible puissance visée par le ticket il
entre en concurrence directe (I/O + allocations) avec l'affichage — c'est
précisément la cause du symptôme décrit au §1. L'objectif « 4-5 s de loader »
n'est donc pas mesurable comme atteint sur la seule base de F20 + T9.

**Correction attendue.** Deux volets : (a) corriger le §5 du ticket pour ne pas
laisser croire que toutes les lectures globales ont disparu ; (b) ouvrir un
ticket technique dédié aux recommandations (le cache par profil existant y aide
déjà, mais la première résolution reste O(catalogue)). Aucun élargissement de
périmètre de T9 n'est demandé ici.

### M4 — Matrice de tests couverte à moitié, le repli sûr n'est pas testé

**Description.** Sur les douze cas listés au §5, sont présents : n° 1
(`resolvesMovieCategoryOnlyOnce`), n° 3
(`usesExistingCategoryWithoutResolutionQuery`), n° 6
(`preservesExistingCategoryAndMetadata`) et une variante du n° 7
(`filtersHiddenCategories`, positions désormais porteuses de `categoryId`).
Manquent :
* n° 2 — épisode (`seriesId != null`) → `getCategoryIdForSeries` appelé et
  `getCategoryIdForStream` **non** appelé ;
* n° 4 — `categoryId` fourni explicitement → aucune requête de résolution ;
* n° 5 — propagation du champ par `getAllPlaybackPositions` **et** par
  `observeAllPlaybackPositions` (justement le risque que la factorisation vise :
  rien ne garantit aujourd'hui qu'un futur retour en arrière soit détecté) ;
* n° 9 — `categoryId == null` + catalogue non vide → position masquée ;
* n° 10 — `categoryId == null` + catalogue vide → position conservée ;
* n° 11 — le `verify(never())` sur `getCached*Streams("all")` existe côté F20
  (`catalogJob`) mais **pas** pour le collecteur de positions, qui est l'objet du
  ticket.

**Impact.** Le comportement le plus délicat de T9 — le repli quand la catégorie
est inconnue, et son exception transitoire — n'est couvert par aucun test, alors
qu'il porte un critère d'acceptation (confidentialité du profil). M2 aurait été
détecté par les cas 9 et 10 correctement paramétrés.

**Correction attendue.** Compléter les six cas manquants ; le n° 11 doit viser le
collecteur de positions (émission du flux, pas seulement le chargement initial).

## Mineur

### m1 — Stub par défaut qui neutralise le repli dans tous les tests

`HomeViewModelTest.stubReactiveSources` (l. 154-155) renvoie `emptyMap()` pour
les deux `getCategoryCounts()`, donc `catalogIsEmpty == true` dans la quasi-
totalité des tests : le chemin nominal (« catégorie inconnue → masquée ») n'est
jamais exercé par défaut. **Correction attendue** : inverser le stub par défaut
(catalogue non vide, cas nominal) et ne forcer le catalogue vide que dans le test
dédié au cas n° 10.

### m2 — Résolution rejouée à chaque tick pour un média hors catalogue

`VodRepositoryImpl.savePlaybackPosition` l. 595-598 : si `existing != null` mais
`existing.categoryId == null` (média absent du cache catalogue — supprimé côté
panel, ou lu depuis les téléchargements), la chaîne `?:` retombe sur la requête
DAO à **chaque** sauvegarde périodique pendant toute la lecture. La contrainte du
§4 (« la requête de résolution ne doit pas s'exécuter à chaque tick ») n'est donc
garantie que dans le cas où la résolution aboutit. **Correction attendue** :
ne tenter la résolution que lorsque `existing == null` (première écriture), ou
mémoriser l'échec pour la session de lecture.

### m3 — `SeriesRepositoryImpl.savePlaybackPosition` n'a aucun appelant

Les deux ViewModels passent par `SavePlaybackPositionUseCase` →
`VodRepository`. La méthode de `SeriesRepository` (l. 621-643) n'est appelée que
par le nouveau test. Le correctif y est donc défensif — et incomplet : quand
`existing == null`, `categoryId` est écrit à `null` sans résolution, contrairement
au chemin VOD. **Correction attendue** : soit résoudre `categoryId` comme dans
`VodRepositoryImpl`, soit retirer la méthode de l'interface `SeriesRepository`
(ticket de nettoyage), pour ne pas laisser un second chemin d'écriture aux règles
divergentes.

### m4 — Repli silencieux sur erreur de comptage non documenté

`HomeViewModel.kt` l. 286-288 : en cas d'exception, `catalogIsEmpty = false`,
donc toute position sans catégorie est masquée. Choix cohérent avec le repli
sûr, mais ni commenté dans le code ni mentionné au §3 (« cas limites »).
**Correction attendue** : une ligne de commentaire explicitant l'intention.

### m5 — Ordre des imports

`VodRepositoryImpl.kt` l. 7-9 : `SeriesDao` est importé après `VodDao`, hors
ordre alphabétique du bloc. Cosmétique, mais `lintDebug` reste à passer à
l'étape 8. **Correction attendue** : réordonner.

---

## Corrections appliquées à l'étape 7

* M1/M2 : deux projections SQL `EXISTS`, une par catalogue, remplacent les
  agrégations ; le repli transitoire est appliqué au type de position concerné.
* M3 : T11 documente l'optimisation séparée des recommandations ; aucune
  promesse de suppression de cette lecture asynchrone ne reste dans T9.
* M4/m1 : tests ajoutés pour les repli sûr, catalogues VOD/Séries distincts,
  absence de lecture globale, résolution VOD/Série, valeur explicite et les
  deux mappers. Le stub nominal représente désormais un catalogue non vide.
* m2 : une position existante sans catégorie ne relance plus de requête à
  chaque tick. m3 : le chemin d'écriture mort de `SeriesRepository` est retiré.
  m4/m5 : le repli sûr est commenté et les imports sont ordonnés.

## Validation étape 8

Status: VALIDATED

Exécution complète obtenue le 2026-08-02 après purge des daemons Gradle restés
bloqués (`./gradlew --stop`) qui empêchaient les tentatives précédentes
d'aboutir :

- `testDebugUnitTest` → **562 tests, 0 échec, 0 erreur**
  (`app/build/test-results/testDebugUnitTest/*.xml`).
- `assembleDebug` → **réussi**, APK généré
  (`app/build/outputs/apk/debug/app-debug.apk`).
- `lintDebug` → **réussi**, `0 errors, 56 warnings`
  (`app/build/reports/lint-results-debug.txt`).

La première tentative de compilation des tests avait échoué :
`SeriesRepositoryImplTest.kt:244` référençait `PlaybackPositionEntity` sans
l'importer (test ajouté à l'Étape 7 pour la couverture M1/M2 de la review).
Import ajouté ; aucune autre modification de test nécessaire.

L'erreur `lintDebug` rencontrée pendant cette même exécution
(`UnrememberedGetBackStackEntry` sur `NavGraph.kt:389`/`:416`) est étrangère au
périmètre T9 (Home/VOD/Séries repositories et cache) — elle relève de F20 (m3
de sa Review) et a été corrigée dans ce ticket-là ; non répétée ici.

Le ticket passe donc de `VALIDATION` à `VALIDATED`.

# 9. Release

*(À remplir à l'Étape 10)*
