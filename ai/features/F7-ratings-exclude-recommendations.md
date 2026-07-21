# F7 - Système d'évaluation J'aime / Je n'aime pas (Exclusion des recommandations)

## Informations générales

Type:
Feature

Status:
TASK BREAKDOWN

Created:
2026-07-21

Target version:
v1.49.0

---

# 1. Description

Cette fonctionnalité introduit la possibilité pour l'utilisateur de marquer un film (VOD) ou une série par un vote positif ("J'aime" / Pouce levé) ou négatif ("Je n'aime pas" / Pouce baissé). 

L'objectif principal est de permettre un contrôle actif de l'utilisateur sur son profil de recommandations :
- Un média marqué **"Je n'aime pas"** doit être immédiatement exclu des sections "Recommandé pour vous" sur l'Accueil.
- Un média marqué **"Je n'aime pas"** doit également être ignoré lors du calcul de son profil de goûts (genres, catégories préférés), même s'il est présent dans l'historique de lecture (reprise de lecture).
- Un média marqué **"J'aime"** doit activement contribuer à renforcer positivement son profil de goûts, signalant un intérêt fort pour ses caractéristiques (genres, acteurs, réalisateurs).

---

# 2. Contexte

Le système de recommandation de l'application (introduit dans la Feature F-6 via `RecommendationEngine` et `GetRecommendationsUseCase`) repose aujourd'hui exclusivement sur l'historique de lecture (`PlaybackPositionEntity`). 

Cependant, l'historique de lecture seul comporte d'importantes limites :
1. **Faux positifs :** Un utilisateur peut démarrer un film d'horreur par curiosité, s'apercevoir au bout de 15 minutes qu'il déteste le film, et l'arrêter définitivement. Actuellement, cet item est mémorisé dans son historique et vient polluer positivement son score pour le genre "Horreur".
2. **Absence de retour explicite :** Il n'existe aucun moyen d'indiquer à l'application qu'un contenu de l'historique a été une déception ou une excellente surprise.
3. **Contrôle utilisateur :** L'utilisateur n'a aucun moyen de nettoyer ou de filtrer manuellement les suggestions qui lui sont faites sur l'écran d'Accueil, créant de la frustration s'il continue de se voir proposer des contenus similaires à ceux qu'il n'apprécie pas.

En fournissant un retour explicite (Thumbs Up/Down), on améliore considérablement la pertinence des recommandations locales de l'application.

---

# 3. Spécification fonctionnelle

## Objectif

Permettre à chaque profil local d'exprimer une préférence explicite sur un film ou une série, puis de l'utiliser immédiatement pour améliorer les recommandations personnelles, sans modifier le catalogue Xtream partagé ni appeler de service externe.

## User stories

- En tant qu'utilisateur, je peux indiquer sur la fiche d'un film ou d'une série que je l'aime ou ne l'aime pas afin que l'application comprenne mieux mes goûts.
- En tant qu'utilisateur, je peux annuler ou remplacer mon vote afin de corriger une préférence exprimée précédemment.
- En tant qu'utilisateur, je ne vois plus un contenu que j'ai rejeté dans les recommandations personnalisées de l'Accueil.
- En tant qu'utilisateur, un contenu aimé renforce les recommandations similaires, même si je ne l'ai pas encore beaucoup regardé.
- En tant qu'utilisateur de profil local, je ne vois ni n'influence les votes des autres profils de l'appareil.

## Parcours utilisateur

1. Depuis une fiche Film ou Série, sur mobile ou Android TV, l'utilisateur trouve deux actions clairement identifiées : **J'aime** et **Je n'aime pas**.
2. Sans vote existant, les deux actions sont inactives. L'utilisateur choisit l'une d'elles.
3. L'action choisie devient active et l'autre reste inactive ; le changement est confirmé visuellement par l'état sélectionné et une animation légère conforme à la charte de la refonte.
4. Un appui sur l'action active annule le vote et ramène le média à l'état neutre. Un appui sur l'autre action remplace directement le vote courant.
5. Après une modification réussie, les recommandations du profil sont recalculées : les rangées et la liste étendue « Recommandé pour vous » reflètent le nouveau choix lors de leur prochain affichage, y compris si l'Accueil est déjà ouvert.
6. Si l'utilisateur choisit **Je n'aime pas**, le média disparaît aussi de ses Favoris et de « Continuer à regarder » pour ce profil. Annuler ultérieurement ce vote ne restaure pas automatiquement ces éléments ; l'utilisateur peut les ajouter ou reprendre la lecture de nouveau.

## Règles métier

- Les votes ne concernent que les médias à la demande : films VOD et séries. Live TV, épisodes individuels, catégories et chaînes ne proposent pas cette action.
- Un vote est lié au profil local actif et à l'identifiant stable du média, en distinguant obligatoirement film et série. Un média ne possède qu'un état par profil : `NEUTRAL`, `LIKE` ou `DISLIKE`.
- Les votes sont exclusivement locaux à l'appareil ; ils ne sont ni envoyés à Xtream Codes ni partagés entre profils.
- `DISLIKE` est une exclusion absolue : le média ne peut jamais figurer dans une recommandation personnalisée de ce profil, quelle que soit sa popularité, son score ou sa présence dans l'historique.
- Un média `DISLIKE` est exclu de la construction du profil de goûts, même s'il existe encore une trace d'historique antérieure.
- `LIKE` apporte un signal positif fort au profil de goûts, même si la lecture est absente, très courte ou incomplète.
- Pour privilégier la découverte, les médias `LIKE` sont eux aussi exclus des recommandations personnalisées. Ils restent accessibles depuis le catalogue, les Favoris et l'Historique selon leur état propre.
- Les médias sans vote continuent de suivre les règles de recommandation existantes fondées sur l'historique et les préférences de catégories.
- Le passage à `DISLIKE` retire uniquement les données personnelles du profil concerné : favori et position de reprise du même type et identifiant. Il ne modifie pas le catalogue partagé, les téléchargements, ni les données des autres profils.
- La suppression d'un profil supprime également tous ses votes, au même titre que ses autres données personnelles.
- Lors de plusieurs actions rapides, le dernier choix confirmé par l'utilisateur est l'état retenu.

## Critères d'acceptation

- Les fiches de détail Film et Série affichent les deux actions sur mobile et Android TV, avec un état neutre, aimé ou non aimé lisible et accessible au focus TV.
- Un appui suit exactement le cycle : neutre → aimé/non aimé ; aimé → neutre ou non aimé ; non aimé → neutre ou aimé.
- Après fermeture et réouverture de l'application, chaque profil retrouve exactement ses votes ; un autre profil voit l'état neutre pour le même média s'il n'a pas lui-même voté.
- Un film ou une série `DISLIKE` n'apparaît dans aucune sortie « Recommandé pour vous » du profil, y compris la liste étendue, et ne contribue pas à ses goûts.
- Un film ou une série `LIKE` influence positivement les recommandations similaires mais n'est pas lui-même recommandé.
- Voter `DISLIKE` sur un favori le retire des Favoris et supprime sa reprise de lecture ; l'action ne touche pas aux autres profils.
- Une modification de vote invalide immédiatement les résultats de recommandation mis en cache, sans nécessiter de redémarrage ou de synchronisation Xtream.
- L'absence de connexion Internet n'empêche pas la consultation ou la modification des votes déjà accessibles localement.

## Cas limites

- Si un média n'est plus présent dans le catalogue après une synchronisation, son vote local peut être conservé jusqu'à la suppression du profil ; il n'a simplement aucun effet tant que le média n'est pas de nouveau recommandable.
- Si une série aimée ou rejetée comporte plusieurs épisodes, le vote s'applique à la série entière, pas à un épisode précis.
- Si le même identifiant numérique existe exceptionnellement pour un film et une série, leurs votes restent indépendants.
- Une reprise de lecture créée après un `DISLIKE` reste possible si l'utilisateur relance explicitement le média ; elle ne fait toutefois pas réintégrer ce média aux recommandations tant que le vote négatif est actif.
- Le vote ne crée pas automatiquement un favori ni une entrée d'historique.

## Gestion des erreurs

- La fonctionnalité ne dépend d'aucun appel réseau ; une indisponibilité Xtream ou TMDB ne doit pas empêcher un vote local.
- Si l'enregistrement local échoue, l'état affiché est restauré à sa dernière valeur persistée, les favoris et la reprise ne sont pas modifiés, et un message non technique informe l'utilisateur qu'il doit réessayer.
- Tant que le profil actif ou l'identifiant du média ne sont pas disponibles, les actions d'évaluation ne sont pas exécutables et aucun vote n'est créé.
- Une erreur de recalcul des recommandations ne doit jamais annuler un vote déjà sauvegardé ; l'Accueil conserve son dernier résultat valide et retentera son actualisation ultérieurement.

---

# 4. Décisions de périmètre

- La première version ne fournit pas d'écran global listant les contenus aimés ou rejetés ; cette évolution pourra faire l'objet d'une feature dédiée.
- Les deux décisions précédemment ouvertes sont retenues : les contenus `LIKE` sont exclus des recommandations pour favoriser la découverte, et un `DISLIKE` retire le favori ainsi que la reprise de lecture du profil.
- L'invalidation des recommandations après chaque vote est requise afin de rendre le changement perceptible immédiatement.

---

# 5. Notes de spécification

- La position exacte des boutons et leurs dimensions seront définies à l'étape 3 en s'appuyant sur les écrans de détail de `docs/design-reference/`. La maquette actuelle ne comporte pas encore de contrôle d'évaluation ; aucun nouveau token visuel n'est introduit à cette étape.

---

# 6. Spécification technique

## Modèles métier

Deux enums de domaine évitent de propager des chaînes ou entiers bruts :

```kotlin
enum class RatedMediaType(val storageValue: String) {
    MOVIE("movie"),
    SERIES("series")
}

enum class MediaRatingValue(val storageValue: Int) {
    LIKE(1),
    DISLIKE(-1)
}

data class MediaRating(
    val mediaId: Int,
    val mediaType: RatedMediaType,
    val value: MediaRatingValue
)
```

L'état neutre est représenté par l'absence de ligne (`null`), pas par une troisième valeur persistée. Cela garantit une seule source de vérité et évite d'accumuler des lignes sans préférence.

## Stockage Room

### Entité

Une table profilée `media_ratings` est ajoutée :

```kotlin
@Entity(
    tableName = "media_ratings",
    primaryKeys = ["profileId", "mediaType", "mediaId"]
)
data class MediaRatingEntity(
    val profileId: Int,
    val mediaType: String,
    val mediaId: Int,
    val value: Int
)
```

- La clé commence par `profileId`, car les lectures principales chargent toutes les évaluations d'un profil ou une évaluation précise de ce profil.
- `mediaType` distingue un film et une série partageant exceptionnellement le même identifiant numérique.
- Seules les valeurs `1` et `-1` sont produites par le mapper data ; toute valeur inconnue lue est ignorée défensivement plutôt que de faire planter la présentation.
- Aucun lien SQLite `FOREIGN KEY` vers le catalogue n'est créé : les entrées doivent survivre à une disparition temporaire du média lors d'une synchronisation.

### DAO

`MediaRatingDao` fournit :

- `observeRating(profileId, mediaType, mediaId): Flow<MediaRatingEntity?>` ;
- `getAllForProfile(profileId): List<MediaRatingEntity>` ;
- `upsert(entity)` avec `OnConflictStrategy.REPLACE` ;
- `delete(profileId, mediaType, mediaId)` pour revenir à neutre ;
- `deleteAllForProfile(profileId)` pour la suppression d'un profil.

`VodDao` reçoit deux suppressions ciblées supplémentaires :

- suppression de toutes les positions ayant un `seriesId` donné pour un profil ;
- suppression des positions dont le `streamId` appartient à la liste d'épisodes chargée, appelée uniquement si cette liste n'est pas vide.

La seconde requête couvre les positions de séries historiques ou actuellement créées avec `seriesId = null`. En parallèle, `SeriesViewModel.savePosition` transmet désormais le `seriesId` de `selectedSeriesDetails` afin que toutes les futures positions soient correctement rattachées à leur série.

### Migration 16 → 17

`AppDatabase` passe de la version 16 à 17 et déclare `MediaRatingEntity` ainsi que `mediaRatingDao()`.

```sql
CREATE TABLE IF NOT EXISTS media_ratings (
    profileId INTEGER NOT NULL,
    mediaType TEXT NOT NULL,
    mediaId INTEGER NOT NULL,
    value INTEGER NOT NULL,
    PRIMARY KEY(profileId, mediaType, mediaId)
)
```

`MIGRATION_16_17` est ajoutée à `ALL_MIGRATIONS`. Aucune donnée existante n'est transformée et aucun fallback destructif n'est introduit.

## Repository et transaction métier

L'interface `MediaRatingRepository` expose :

```kotlin
fun observeRating(mediaId: Int, mediaType: RatedMediaType): Flow<MediaRatingValue?>
suspend fun getAllRatings(): List<MediaRating>
suspend fun setRating(
    mediaId: Int,
    mediaType: RatedMediaType,
    value: MediaRatingValue?,
    seriesEpisodeIds: Set<Int> = emptySet()
)
```

`MediaRatingRepositoryImpl` capture une seule fois le `profileId` actif au début de l'écriture, puis utilise `AppDatabase.withTransaction` :

1. `value == null` : suppression de la ligne d'évaluation uniquement ;
2. `LIKE` : upsert de l'évaluation uniquement ;
3. `DISLIKE` : upsert, retrait du favori de même type/identifiant, puis suppression de la reprise correspondante ;
4. pour une série : suppression par `seriesId`, complétée par les `streamId` des épisodes présents dans la fiche chargée.

Le vote et ses effets `DISLIKE` sont donc atomiques : si une requête échoue, Room annule l'ensemble et les Flows conservent le dernier état persistant. Les téléchargements ne sont jamais consultés ni supprimés.

`ProfileRepositoryImpl.deleteProfile` appelle également `mediaRatingDao.deleteAllForProfile(id)` avant de supprimer le profil.

## Use case d'écriture

`SetMediaRatingUseCase` reçoit un état cible exact (`LIKE`, `DISLIKE` ou `null`) calculé par le ViewModel. Il :

1. délègue la transaction au repository ;
2. invalide le cache de `GetRecommendationsUseCase` uniquement après succès ;
3. émet ensuite un événement de rafraîchissement des recommandations.

`GetRecommendationsUseCase.invalidateCache()` devient une opération suspendue protégée par son `Mutex`. Le use case expose un `SharedFlow<Unit>` d'invalidations. `HomeViewModel` le collecte et relance uniquement le calcul des recommandations, sans recharger les catalogues, l'EPG ou TMDB.

Les boutons sont désactivés pendant une écriture. Le ViewModel accepte donc une seule transition confirmée à la fois ; l'état cible est toujours dérivé de la dernière valeur persistée :

- neutre + J'aime → `LIKE` ;
- `LIKE` + J'aime → `null` ;
- `LIKE` + Je n'aime pas → `DISLIKE` ;
- règles symétriques pour `DISLIKE`.

## Intégration au moteur de recommandations

`GetRecommendationsUseCase` injecte `MediaRatingRepository` et charge les évaluations du profil en même temps que l'historique local.

Pour chaque type, il construit :

- les identifiants `LIKE` ;
- les identifiants `DISLIKE` ;
- l'historique positif, après retrait défensif des identifiants `DISLIKE` ;
- les identifiants exclus des résultats = historique ∪ `LIKE` ∪ `DISLIKE`.

Un média évalué mais absent du catalogue est simplement ignoré lors de la construction du goût. Son identifiant reste néanmoins exclu s'il réapparaît dans le catalogue.

### Pondération explicite

`RecommendationEngine.buildProfileTaste` reçoit des signaux pondérés :

```kotlin
data class TasteSignal(
    val item: RecommendableItem,
    val weight: Double
)
```

- historique neutre : poids `1.0` ;
- média `LIKE` : poids `3.0` ;
- média `DISLIKE` : aucun signal.

Un média à la fois aimé et présent dans l'historique n'est ajouté qu'une fois, avec le poids `3.0`. Les dénominateurs des poids de genre, catégorie, acteurs et réalisateur deviennent des sommes de poids `Double`, et non des nombres d'items. Le scoring final des candidats conserve les coefficients existants.

La protection de démarrage à froid évolue ainsi :

- sans aucun `LIKE`, le seuil actuel de trois médias distincts dans l'historique reste inchangé ;
- dès qu'au moins un `LIKE` correspond à un média du catalogue, le profil peut produire des recommandations même avec moins de trois lectures.

Cela permet à un vote positif d'être utile sans historique tout en conservant le comportement antérieur pour les profils sans retour explicite.

## Présentation et états ViewModel

`VodState` et `SeriesState` reçoivent :

```kotlin
val mediaRating: MediaRatingValue? = null
val isRatingSaving: Boolean = false
val ratingError: String? = null
```

À chaque sélection de film ou série, le ViewModel annule l'observation précédente puis collecte `observeRating` pour le nouvel identifiant et le profil actif. Le repository utilise `flatMapLatest` sur `ProfileManager.activeProfileId`, ce qui remet correctement l'état à jour lors d'un changement de profil.

`VodViewModel.setRating(value)` transmet l'identifiant du film. `SeriesViewModel.setRating(value)` transmet l'identifiant de série et l'ensemble des identifiants d'épisodes de `selectedSeriesDetails`. Les deux méthodes :

- ignorent une action si une écriture est déjà en cours ;
- positionnent `isRatingSaving` ;
- exposent un message utilisateur générique en cas d'échec ;
- ne modifient jamais optimistement `mediaRating` ; le Flow Room confirme la nouvelle valeur.

## Composant Compose partagé

Un composable stateless `MediaRatingControls` est créé dans `presentation/components/MediaRatingControls.kt` avec :

```kotlin
@Composable
fun MediaRatingControls(
    value: MediaRatingValue?,
    isSaving: Boolean,
    isTv: Boolean,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    modifier: Modifier = Modifier
)
```

- Mobile : une `Row` de deux boutons de même largeur, icône + libellé « J'aime » / « Je n'aime pas », hauteur 48.dp et espacement 10.dp.
- Android TV : une `Column` de deux boutons pleine largeur dans la colonne latérale, hauteur 40.dp et espacement 8.dp, afin de conserver les libellés lisibles et deux cibles D-pad stables.
- État neutre : `Surface3`, bordure blanche à faible opacité, texte `TextPrimary`.
- État sélectionné : fond `AccentLavande`, contenu blanc et icône remplie. Aucun rouge ou vert inédit n'est ajouté à la charte.
- Focus TV : bordure de 2.dp avec `AccentLavande`/`AccentHover` existant ; chaque action est une cible indépendante.
- Une animation courte via `animateColorAsState` et `animateFloatAsState` accompagne la sélection, sans nouvelle dépendance.
- Pendant `isSaving`, les deux actions sont désactivées et un indicateur compact commun signale l'écriture en cours.
- Les descriptions d'accessibilité indiquent l'action et l'état sélectionné.

Placement :

- Film TV : sous le bouton Favori dans la colonne de 220.dp, avant le contenu de droite ;
- Série TV : sous le bouton Favori et avant la liste des saisons ;
- Film mobile : sous les métadonnées et avant le synopsis, en conservant l'étoile Favori au niveau du titre ;
- Série mobile : sous les métadonnées et avant le synopsis, sans modifier la hiérarchie saisons/épisodes.

Ce placement réutilise les surfaces, rayons et espacements des fiches de référence. La maquette ne prévoit pas encore ces actions : elles sont ajoutées sans déplacer l'action Favori ni le bouton principal de lecture.

`VodDetailsScreen` et `SeriesDetailsScreen` reçoivent l'état et les callbacks de vote. Un `SnackbarHost` local affiche `ratingError`, puis appelle un callback de consommation pour éviter une répétition après recomposition.

## Navigation

La navigation actuelle a été vérifiée : `AppNavGraph` est désormais commun à mobile et Android TV, malgré l'ancien avertissement de double navigation dans `AGENTS.md`. Un seul câblage est donc nécessaire dans les routes `vod_details` et `series_details` : état du ViewModel, callbacks de vote et consommation d'erreur. `MainActivity` ne reçoit aucun nouvel écran ni branche de navigation.

## Dépendances, réseau et sécurité

- Aucune nouvelle dépendance Gradle : Room KTX, Hilt, Compose Material/TV et les icônes sont déjà présents.
- Aucun appel Xtream ou TMDB n'est ajouté. Les votes fonctionnent hors ligne dès que la fiche et le profil sont disponibles.
- Aucune donnée sensible ni credential n'est stocké ou journalisé.
- Aucune interface Retrofit ni règle ProGuard supplémentaire.

---

# 7. Architecture

## Flux d'écriture

```text
VodViewModel / SeriesViewModel
        │ état cible + média + épisodes éventuels
        ▼
SetMediaRatingUseCase
        │
        ▼
MediaRatingRepositoryImpl
        │ AppDatabase.withTransaction
        ├── MediaRatingDao : upsert/delete
        ├── FavoritesDao : delete si DISLIKE
        └── VodDao : delete reprise si DISLIKE
        │ succès
        ▼
GetRecommendationsUseCase.invalidateCache()
        │ événement SharedFlow
        ▼
HomeViewModel.refreshRecommendations()
```

Les Flows Room mettent parallèlement à jour l'état du vote, les Favoris et « Continuer à regarder ». L'événement de recommandation ne transporte aucune donnée : la Home relit les sources locales après invalidation.

## Flux de calcul

```text
historique du profil ── retrait DISLIKE ──┐
                                         ├── TasteSignal(1.0 / 3.0)
LIKE du profil ───────────────────────────┘            │
                                                      ▼
                                            RecommendationEngine

candidats autorisés - historique - LIKE - DISLIKE ──> résultats
```

## Responsabilités

- `MediaRatingDao` : requêtes Room sans règle métier.
- `MediaRatingRepositoryImpl` : mapping, scoping par profil et atomicité multi-DAO.
- `SetMediaRatingUseCase` : orchestration après persistance et invalidation des recommandations.
- `GetRecommendationsUseCase` : assemblage des signaux par type, exclusions et cache.
- `RecommendationEngine` : calcul pur et testable des poids et scores.
- `VodViewModel` / `SeriesViewModel` : état écran, observation du vote, sérialisation UI et erreurs.
- `MediaRatingControls` : rendu stateless mobile/TV et accessibilité.

## Fichiers nouveaux

- `domain/model/MediaRating.kt`
- `domain/repository/MediaRatingRepository.kt`
- `domain/usecase/SetMediaRatingUseCase.kt`
- `data/local/entity/MediaRatingEntity.kt`
- `data/local/dao/MediaRatingDao.kt`
- `data/repository/MediaRatingRepositoryImpl.kt`
- `presentation/components/MediaRatingControls.kt`
- tests unitaires correspondants dans `app/src/test/`.

## Fichiers modifiés

- `data/local/db/AppDatabase.kt` : entité, DAO, version 17.
- `data/local/db/Migrations.kt` : `MIGRATION_16_17` et `ALL_MIGRATIONS`.
- `data/local/dao/VodDao.kt` : suppressions de reprises de série.
- `data/repository/ProfileRepositoryImpl.kt` : nettoyage des votes du profil.
- `di/AppModule.kt` : providers DAO/repository et dépendances de `ProfileRepositoryImpl`.
- `domain/model/RecommendationEngine.kt` : signaux pondérés.
- `domain/usecase/GetRecommendationsUseCase.kt` : évaluations, exclusions, invalidation observable.
- `presentation/home/HomeViewModel.kt` : rafraîchissement ciblé après invalidation.
- `presentation/vod/VodState.kt`, `presentation/vod/VodViewModel.kt`, `presentation/vod/VodDetailsScreen.kt`.
- `presentation/series/SeriesState.kt`, `presentation/series/SeriesViewModel.kt`, `presentation/series/SeriesDetailsScreen.kt`.
- `presentation/navigation/NavGraph.kt` : câblage des deux fiches commun aux plateformes.
- `app/src/main/res/values/strings.xml` : libellés, accessibilité et erreur locale.

## Décisions techniques justifiées

- **Ligne absente = neutre** : stockage minimal et requêtes sans valeur sentinelle.
- **Transaction dans le repository data** : seule couche ayant accès à tous les DAO et à `RoomDatabase`; garantit que `DISLIKE`, favori et reprise ne divergent pas.
- **Enums au domaine, valeurs primitives en Room** : type safety sans `TypeConverter` global ni migration fragile.
- **Poids LIKE = 3.0** : signal explicitement fort mais borné, facilement testable et ajustable sans modifier le modèle stocké.
- **Exclusion défensive en plus de la suppression de reprise** : un résidu historique ou une future reprise ne peut jamais contourner un `DISLIKE`.
- **Invalidation observable ciblée** : retour immédiat sur la Home sans rechargement coûteux du reste de l'écran.
- **Composant partagé stateless** : comportement identique Film/Série, logique conservée dans les ViewModels.

---

# 8. Validation prévue

## Tests unitaires obligatoires

- Mapping `MediaRatingEntity` ↔ domaine : `LIKE`, `DISLIKE`, valeur inconnue ignorée.
- `SetMediaRatingUseCase` : succès invalide le cache ; échec n'invalide pas et propage l'erreur au ViewModel.
- Repository : neutre supprime uniquement le vote ; `LIKE` upsert uniquement ; `DISLIKE` film retire favori/reprise ; `DISLIKE` série retire par `seriesId` et IDs d'épisodes ; profil capturé une fois.
- `RecommendationEngine` : poids `3.0`, normalisation pondérée et absence de contribution négative.
- `GetRecommendationsUseCase` :
  - `DISLIKE` exclu du goût et des résultats ;
  - `LIKE` renforce le goût et est exclu des résultats ;
  - un seul `LIKE` permet de sortir du cold start ;
  - un média aimé et vu n'est pas compté deux fois ;
  - séparation film/série pour un même identifiant ;
  - invalidation empêche de servir l'ancien cache.
- `VodViewModelTest` et `SeriesViewModelTest` : observation par média/profil, transitions, état saving, erreur sans état optimiste et IDs d'épisodes transmis.
- `ProfileRepositoryImplTest` : suppression des votes lors de la suppression d'un profil.
- Non-régression `SeriesViewModelTest` : les nouvelles positions contiennent le `seriesId` actif.

Le projet ne disposant pas d'infrastructure `androidTest`, la migration 16→17 est relue manuellement contre l'entité et le SQL, conformément à `AGENTS.md`. Aucun test UI Compose n'est prioritaire pour le layout pur.

## Vérifications manuelles

1. Mobile et TV, film puis série : vérifier les trois états, le changement direct LIKE↔DISLIKE et la persistance après redémarrage.
2. Changer de profil sur le même média : vérifier des états indépendants.
3. `DISLIKE` sur un favori avec reprise : vérifier disparition immédiate des Favoris, de « Continuer à regarder » et des recommandations.
4. Série avec plusieurs épisodes repris, dont une position ancienne sans `seriesId` : vérifier remise à zéro complète.
5. `LIKE` sans historique suffisant : vérifier l'apparition de recommandations similaires sans réafficher le média aimé.
6. Home déjà ouverte : voter puis revenir à la Home et vérifier les rangées recalculées sans synchronisation réseau.
7. Hors ligne : changer et annuler un vote local.
8. Simuler une erreur Room : vérifier rollback atomique, état précédent et message non technique.

## Risques et atténuations

- **Course cache/invalidation** : vider le cache sous le même `Mutex`, puis émettre l'événement.
- **Reprises série sans `seriesId`** : supprimer aussi par IDs d'épisodes chargés et corriger les sauvegardes futures.
- **Collision d'identifiants Film/Série** : conserver le type dans la clé Room, les ensembles d'exclusion et les appels repository.
- **Double contribution aimé + historique** : retirer les `LIKE` de l'historique avant de créer leur signal pondéré.
- **Échec partiel d'un DISLIKE** : transaction Room unique sur les trois DAO.
- **Actions rapides** : désactiver les contrôles pendant l'écriture et ne refléter que le Flow persistant.
- **Média évalué absent du catalogue** : conserver le vote, ignorer son signal tant que les métadonnées manquent.
- **Recalcul coûteux** : collecter uniquement un événement après écriture réussie, conserver le calcul sur `Dispatchers.Default` et réutiliser le cache ensuite.
- **Cycle DI** : `MediaRatingRepository` ne dépend pas des use cases ; seul `SetMediaRatingUseCase` dépend du repository et de `GetRecommendationsUseCase`.

## Contraintes de performance

- Une lecture des évaluations du profil par recalcul ; volume attendu très inférieur au catalogue.
- Requêtes indexées par la clé primaire commençant par `profileId`.
- Une transaction locale courte par vote, sans accès réseau ni scan du catalogue.
- Aucun collecteur par carte : seuls les écrans de détail actifs observent un vote ; la Home observe un flux global d'invalidation.
- Le recalcul complet n'est déclenché qu'après un changement confirmé, jamais à chaque recomposition.

---

# 9. Plan de développement

## Ordre d'exécution

Les tâches 1 à 3 construisent le contrat de données et de recommandations. Les tâches 4 et 5 dépendent de ce contrat. La tâche 6 valide l'ensemble. F8 réutilisera l'invalidation des recommandations créée à la tâche 3.

### Tâche 1 — Modèle d'évaluation et migration Room

- [ ] Créer le stockage profilé des évaluations et sa migration non destructive 16 → 17.

Objectif :
Ajouter les modèles domaine/data, le DAO, l'entité `media_ratings`, la migration et le nettoyage des votes lors de la suppression d'un profil, sans changer le comportement de l'UI.

Fichiers :

- `domain/model/MediaRating.kt`
- `data/local/entity/MediaRatingEntity.kt`
- `data/local/dao/MediaRatingDao.kt`
- `data/local/db/AppDatabase.kt`
- `data/local/db/Migrations.kt`
- `data/repository/ProfileRepositoryImpl.kt`
- `di/AppModule.kt`
- tests de mapping et de `ProfileRepositoryImpl`.

Validation :

- Schéma Room version 17 et `MIGRATION_16_17` présents dans `ALL_MIGRATIONS`.
- SQL relu contre l'entité ; aucun fallback destructif.
- États `LIKE`, `DISLIKE`, neutre et valeur inconnue couverts par test.
- La suppression d'un profil appelle aussi le nettoyage des votes.
- `./gradlew testDebugUnitTest` passe.

### Tâche 2 — Repository atomique et commande de vote

- [ ] Implémenter l'écriture/lecture d'évaluation et les effets atomiques d'un `DISLIKE`.

Objectif :
Créer `MediaRatingRepository` et `SetMediaRatingUseCase`. Un vote négatif doit, dans une unique transaction, enregistrer le vote, retirer le favori et supprimer la reprise du profil ; un vote neutre ou positif ne modifie que l'évaluation.

Fichiers :

- `domain/repository/MediaRatingRepository.kt`
- `data/repository/MediaRatingRepositoryImpl.kt`
- `domain/usecase/SetMediaRatingUseCase.kt`
- `data/local/dao/VodDao.kt`
- `data/local/dao/FavoritesDao.kt` si une requête existante ne couvre pas le cas transactionnel.
- `di/AppModule.kt`
- `app/src/test/.../MediaRatingRepositoryImplTest.kt`
- `app/src/test/.../SetMediaRatingUseCaseTest.kt`.

Validation :

- Repository scopé par le `profileId` capturé une seule fois.
- `DISLIKE` film/série retire les bonnes données personnelles et jamais un téléchargement.
- Série : suppression par `seriesId` et par IDs d'épisodes fournis.
- Une erreur DAO annule toute la transaction ; le cache n'est pas invalidé.
- `./gradlew testDebugUnitTest` passe.

### Tâche 3 — Recommandations pondérées et invalidation réactive

- [ ] Intégrer les évaluations au moteur de recommandations et exposer un rafraîchissement ciblé de la Home.

Objectif :
Appliquer les exclusions absolues, le poids `LIKE = 3.0`, la sortie du cold start par like et l'invalidation observable du cache, sans recharger le reste de la Home.

Fichiers :

- `domain/model/RecommendationEngine.kt`
- `domain/usecase/GetRecommendationsUseCase.kt`
- `presentation/home/HomeViewModel.kt`
- `app/src/test/.../RecommendationEngineTest.kt`
- `app/src/test/.../GetRecommendationsUseCaseTest.kt`
- `app/src/test/.../HomeViewModelTest.kt`.

Validation :

- Les `LIKE` et `DISLIKE` sont absents des résultats ; seul `LIKE` renforce le goût.
- Un media aimé et vu n'est compté qu'une fois ; film/série du même ID restent distincts.
- Un like catalogue permet des recommandations sans trois lectures.
- L'invalidation ne sert jamais l'ancien cache et n'entraîne pas de rechargement TMDB/EPG.
- `./gradlew testDebugUnitTest` passe.

### Tâche 4 — États d'écran et observation des votes

- [ ] Ajouter l'état de vote, les transitions et la gestion d'erreur aux ViewModels Film et Série.

Objectif :
Observer le vote du média sélectionné, calculer les trois transitions utilisateur, bloquer les doubles écritures et transmettre les données nécessaires à la commande atomique.

Fichiers :

- `presentation/vod/VodState.kt`
- `presentation/vod/VodViewModel.kt`
- `presentation/series/SeriesState.kt`
- `presentation/series/SeriesViewModel.kt`
- tests `VodViewModelTest.kt` et `SeriesViewModelTest.kt`.

Validation :

- Changement de média ou de profil met à jour l'état observé sans fuite de l'ancien vote.
- Transitions neutre/like/dislike exactes, sans état optimiste.
- Écriture en cours désactive les actions ; échec affiche un message générique et préserve l'état persisté.
- Les nouvelles reprises de séries contiennent le `seriesId` actif.
- `./gradlew testDebugUnitTest` passe.

### Tâche 5 — Contrôles de détail et câblage navigation

- [ ] Ajouter les contrôles J'aime/Je n'aime pas aux fiches Film et Série sur mobile et Android TV.

Objectif :
Créer le composant stateless partagé, l'intégrer aux quatre layouts de détail et relier les routes unifiées aux états/callbacks des ViewModels.

Fichiers :

- `presentation/components/MediaRatingControls.kt`
- `presentation/vod/VodDetailsScreen.kt`
- `presentation/series/SeriesDetailsScreen.kt`
- `presentation/navigation/NavGraph.kt`
- `app/src/main/res/values/strings.xml`.

Validation :

- Deux contrôles lisibles respectent les tokens de la maquette, sans déplacer Favori ni Lecture.
- Mobile : boutons horizontaux ; TV : deux cibles D-pad verticales avec focus visible.
- Les états sélectionnés, l'animation légère, l'accessibilité et le Snackbar d'erreur fonctionnent.
- Les deux plateformes passent par les mêmes routes `vod_details` / `series_details`.
- `./gradlew assembleDebug` et `./gradlew lintDebug` passent.

### Tâche 6 — Validation fonctionnelle et non-régression

- [ ] Vérifier le parcours complet, les profils et la migration avant passage en review.

Objectif :
Exécuter les vérifications finales, corriger toute régression puis consigner les résultats dans F7.

Fichiers :

- `ai/features/F7-ratings-exclude-recommendations.md` (notes et résultats de validation)
- fichiers de tests ajustés par les anomalies constatées.

Validation :

- `./gradlew testDebugUnitTest`, `./gradlew assembleDebug` et `./gradlew lintDebug` passent.
- Vérification manuelle mobile/TV des trois états, changement de profil, persistance, offline, exclusions et suppression favori/reprise sur dislike.
- Migration 16 → 17 relue manuellement contre le schéma final.
- Aucun élément hors périmètre : pas d'écran global de notes, pas d'API externe, pas de téléchargement supprimé.
