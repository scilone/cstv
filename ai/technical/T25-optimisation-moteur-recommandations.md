# T25 — Optimisation du moteur de recommandations (GetRecommendationsUseCase)

## Informations générales

* **Status** : VALIDATION
* **Created** : 2026-08-18
* **Type** : Optimisation Performance (Refactoring & Room Projection)
* **Écrans** : Accueil (Recommandations)
* **Fichiers clés** : `domain/usecase/GetRecommendationsUseCase.kt`, `domain/model/RecommendationEngine.kt`, `data/local/dao/VodDao.kt`, `data/local/dao/SeriesDao.kt`, `data/repository/VodRepositoryImpl.kt`, `data/repository/SeriesRepositoryImpl.kt`

---

# 1. Description

L'analyse des logs de diagnostic a révélé que le calcul des recommandations prend **19,7 secondes** (`[PERF] RECO calcul complet en 19705ms`) sur un processeur d'Android TV d'entrée de gamme (Philips 2021/22).

Deux goulots d'étranglement majeurs ont été identifiés dans la fonction `GetRecommendationsUseCase.compute` :
1. **La lecture SQL et la désérialisation du catalogue complet (12,2 secondes) :** L'application lit l'intégralité du catalogue, soit **39 206 films** et **12 566 séries** (`51 772 éléments au total`). Room charge l'entièreté des colonnes lourdes de la table (comme le synopsis `plot`, l'index de recherche textuelle `searchText`, ou le titre nettoyé `cleanTitle`), saturant le `CursorWindow` d'Android (limité à 2 Mo) et provoquant d'innombrables allocations et lectures disque lentes.
2. **La redondance de parsing et d'allocations CPU dans le scoring (7,5 secondes) :** Pour chacun des 51 000 candidats, l'algorithme appelle à la volée `scoreCandidate`. À l'intérieur, les champs de genres (ex: `"Action, Thriller"`) et d'acteurs sont splités, nettoyés et normalisés à chaque comparaison. L'utilisation de `Regex("[,/]")` et de chaînes de caractères temporaires sur 51 000 éléments génère des millions de micro-allocations en mémoire vive, provoquant une famine CPU et d'importantes pauses du Garbage Collector (GC).

**L'objectif de ce ticket est d'optimiser ces deux aspects pour réduire le temps total sous la barre de la seconde (réduction de 95% du temps de traitement) sans altérer la pertinence fonctionnelle des recommandations.**

---

# 2. Contexte

### A. La lecture lourde
Dans `GetRecommendationsUseCase.kt` :
```kotlin
val allMovies = vodRepository.getCachedVodStreams("all") // Appelle vodDao.getAllStreams()
val allSeries = seriesRepository.getCachedSeriesStreams("all") // Appelle seriesDao.getAllSeries()
```
Ces requêtes renvoient des listes d'objets `VodStream` et `SeriesStream` de domaine complets contenant des dizaines de propriétés inutiles pour le calcul des recommandations (comme `plot`, `containerExtension`, `searchText`, etc.).

### B. Le parsing en boucle fermée
Dans `RecommendationEngine.kt` :
```kotlin
fun scoreCandidate(candidate: RecommendableItem, taste: ProfileTaste, currentTimeMs: Long): Double {
    // 1. Genre Score
    val candidateGenres = GenreParser.parseGenres(candidate.genres) // <-- Split Regex + Allocations à chaque élément !
    ...
    // 3. Actors Score
    val candidateActors = parseNamesList(candidate.actors) // <-- Split + Lowercase + Allocations à chaque élément !
    ...
}
```
Puisque le scoring s'applique sur les 51 000 candidats, ces deux parseurs allouent des listes intermédiaires de chaînes de caractères à la volée de manière synchrone, ce qui est extrêmement lourd sur un processeur TV.

---

# 3. Décisions produit prises à l'étape 2

| Sujet | Décision |
| --- | --- |
| Seuil de performance | Sur la TV Philips 2021/22 ayant produit le diagnostic, un calcul complet doit prendre moins de 500 ms. |
| Pertinence | L'optimisation ne doit pas modifier les règles de recommandation ni le classement produit par les données identiques. |

---

# 4. Hypothèses

* Le catalogue, l'historique, les favoris, les notes et les catégories masquées disponibles localement sont suffisants : ce calcul ne déclenche aucune requête réseau.
* Le seuil de moins de 500 ms est évalué sur un catalogue comparable à celui du diagnostic (environ 39 206 films et 12 566 séries) et sur le même profil TV de référence.

---

# 5. Questions ouvertes

Aucune à l'étape 2.

---

# 6. Spécification fonctionnelle

## Résultat utilisateur attendu
* **Temps de chargement réduit :** Les recommandations personnalisées s'affichent sur l'Accueil en moins d'une seconde après le lancement ou le changement de profil, au lieu d'arriver après 20 secondes.
* **Aucun gel d'interface :** Le processeur de la TV n'étant plus surchargé, la navigation dans l'Accueil reste parfaitement fluide à 60 FPS (aucun ralentissement d'interface d'affichage pendant le calcul).
* **Fidélité fonctionnelle stricte :** Les scores calculés et le classement final (Top 100 films et Top 100 séries) restent rigoureusement identiques aux résultats actuels.

## User stories

* En tant qu'utilisateur d'un profil ayant assez d'activité, je vois mes recommandations personnalisées sans attente perceptible après l'ouverture de l'Accueil ou le changement de profil.
* En tant qu'utilisateur qui navigue pendant ce calcul, je peux continuer à parcourir l'Accueil sans blocage ni changement de comportement des autres contenus.
* En tant qu'utilisateur, je retrouve les mêmes recommandations pour les mêmes données de profil ; cette optimisation ne rend ni de nouveaux titres éligibles ni ne modifie leur ordre.

## Parcours utilisateur

1. L'utilisateur ouvre l'Accueil ou sélectionne un autre profil.
2. Si le profil est éligible aux recommandations, l'application calcule la sélection personnalisée à partir des données locales.
3. La section « Recommandé pour vous » s'affiche dès que le résultat est disponible, en moins de 500 ms sur l'appareil de référence.
4. Pendant ce délai, l'Accueil reste utilisable ; aucun indicateur supplémentaire ni message d'erreur n'est introduit.

## Règles métier et cas limites

* Les règles existantes de démarrage à froid, d'exclusion de l'historique, des favoris, des avis négatifs et des catégories masquées restent inchangées.
* Le résultat reste limité à 100 films et 100 séries, dans le même ordre que le moteur actuel à données et horodatage identiques.
* Une source de données locale indisponible ou vide conserve le comportement dégradé actuel : les recommandations concernées sont absentes, sans empêcher l'Accueil de s'afficher.
* Un changement de profil ne doit jamais afficher, même brièvement, les recommandations du profil précédent.

## Critères d'acceptation

* Avec le catalogue de référence et un profil éligible, le log de calcul complet est strictement inférieur à 500 ms sur la TV Philips 2021/22.
* À données identiques, les identifiants et l'ordre des recommandations sont identiques avant et après optimisation.
* Le démarrage à froid et les données incomplètes ne provoquent ni crash ni affichage trompeur.
* La navigation dans l'Accueil reste réactive durant le calcul.

---

# 7. Spécification technique détaillée

## Objectifs techniques

1. **Projections SQL légères (Room) :**
   * Créer deux classes de projection Room (`RecommendableVodProjection` et `RecommendableSeriesProjection`) ne contenant **uniquement** que les colonnes nécessaires au scoring : `streamId` / `seriesId`, `categoryId`, `genre`, `actors`, `director`, `rating`, `added`, `releaseYear`.
   * Ajouter des requêtes Room optimisées retournant ces projections légères au lieu de l'entité globale.
2. **Mappage de domaine léger :**
   * Étendre l'interface `RecommendableItem` pour intégrer des propriétés de **pre-parsing** (évaluation paresseuse / Lazy).
   * Mettre en œuvre le pre-parsing des genres et des acteurs lors de l'instanciation de l'objet (exactement une fois par candidat), permettant à `scoreCandidate` d'effectuer uniquement des lectures O(1) directes dans des listes pré-calculées.
3. **Mise à jour des Repositories et de `GetRecommendationsUseCase` :**
   * Ajouter des méthodes d'accès légères dans les interfaces `VodRepository` et `SeriesRepository` pour retourner directement des listes de `RecommendableItem`.
   * Mettre à jour la méthode `compute` pour consommer ces listes sans intermédiaire.

---

## Détails d'implémentation

## Fichiers modifiés / créés

| Fichier | Modification / Rôle |
| --- | --- |
| `data/local/dao/VodDao.kt` | Ajout de la projection `RecommendableVodProjection` et de la requête `@Query` dédiée. |
| `data/local/dao/SeriesDao.kt` | Ajout de la projection `RecommendableSeriesProjection` et de la requête `@Query` dédiée. |
| `domain/model/RecommendationEngine.kt` | Évolution de l'interface `RecommendableItem` pour exposer `parsedGenres`, `parsedActors`, et `normalizedDirector` pré-calculés. Implémentation du caching `by lazy`. |
| `domain/repository/VodRepository.kt` | Nouvelle méthode `getRecommendableVodItems(): List<RecommendationEngine.RecommendableItem>`. |
| `domain/repository/SeriesRepository.kt` | Nouvelle méthode `getRecommendableSeriesItems(): List<RecommendationEngine.RecommendableItem>`. |
| `data/repository/VodRepositoryImpl.kt` | Implémentation utilisant la projection légère et mappant vers `RecommendableVod`. |
| `data/repository/SeriesRepositoryImpl.kt` | Implémentation utilisant la projection légère et mappant vers `RecommendableSeries`. |
| `domain/usecase/GetRecommendationsUseCase.kt` | Remplacement de la lecture catalogue complète par les requêtes de projections légères. |

---

## Détails de l'implémentation

### 1. Projections légères Room (ex: `VodDao.kt`)

```kotlin
data class RecommendableVodProjection(
    val streamId: Int,
    val categoryId: String,
    val genre: String?,
    val actors: String?,
    val director: String?,
    val rating: String?,
    val added: String?,
    val releaseYear: Int?
)

// Dans VodDao.kt
@Query("SELECT streamId, categoryId, genre, actors, director, rating, added, releaseYear FROM vod_streams")
suspend fun getRecommendableVodStreams(): List<RecommendableVodProjection>
```
*(Idem pour `SeriesDao.kt` avec `seriesId` à la place de `streamId`)*

### 2. Évolution de `RecommendableItem` (dans `RecommendationEngine.kt`)

```kotlin
interface RecommendableItem {
    val uniqueId: String
    val genres: String?
    val categoryId: String
    val rating: String?
    val addedEpoch: String?
    val releaseYear: Int?
    val actors: String?
    val director: String?

    // Propriétés pré-calculées de manière paresseuse (Lazy)
    val parsedGenres: List<String>
    val parsedActors: List<String>
    val normalizedDirector: String?
}
```

Mise en place de l'évaluation paresseuse dans `RecommendableVod` :
```kotlin
class RecommendableVod(val stream: RecommendableVodProjection) : RecommendableItem {
    override val uniqueId: String = stream.streamId.toString()
    override val genres: String? = stream.genre
    override val categoryId: String = stream.categoryId
    override val rating: String? = stream.rating
    override val addedEpoch: String? = stream.added
    override val releaseYear: Int? = stream.releaseYear
    override val actors: String? = stream.actors
    override val director: String? = stream.director

    // Le split et la normalisation s'exécutent au plus UNE fois par élément, et non à chaque comparaison de score
    override val parsedGenres: List<String> by lazy {
        GenreParser.parseGenres(stream.genre)
    }
    override val parsedActors: List<String> by lazy {
        parseNamesList(stream.actors)
    }
    override val normalizedDirector: String? by lazy {
        stream.director?.trim()?.lowercase()
    }
}
```

### 3. Allègement de `scoreCandidate`

Grâce aux propriétés lazy pré-calculées, la méthode `scoreCandidate` devient une simple suite d'opérations d'accès direct extrêmement rapides :

```kotlin
internal fun scoreCandidate(candidate: RecommendableItem, taste: ProfileTaste, currentTimeMs: Long): Double {
    // 1. Genre Score (0.0 to 1.0)
    var genreScore = 0.0
    val candidateGenres = candidate.parsedGenres // <-- Lecture O(1) directe !
    if (candidateGenres.isNotEmpty()) {
        var sumWeights = 0.0
        for (normalized in candidateGenres) {
            sumWeights += taste.genreWeights[normalized] ?: 0.0
        }
        genreScore = sumWeights.coerceAtMost(1.0)
    }

    // 2. Category Score (0.0 to 1.0)
    val categoryScore = taste.categoryWeights[candidate.categoryId] ?: 0.0

    // 3. Actors Score (0.0 to 1.0)
    var actorsScore = 0.0
    val candidateActors = candidate.parsedActors // <-- Lecture O(1) directe !
    if (candidateActors.isNotEmpty()) {
        var sumWeights = 0.0
        for (actor in candidateActors) {
            sumWeights += taste.actorWeights[actor] ?: 0.0
        }
        actorsScore = sumWeights.coerceAtMost(1.0)
    }

    ... // Reste identique
}
```

---

# 8. Plan de validation prévu

- [x] **Validation automatisée :** `./gradlew --no-daemon testDebugUnitTest assembleDebug lintDebug` réussi le 2026-08-18 (1 241 tests, 0 échec ; APK debug et lint verts).
- [ ] **Comparaison avant/après :** dérogation explicitement validée par le PO pour cette livraison ; aucune comparaison bit à bit ajoutée.
- [ ] **Vérification des performances :** dérogation explicitement validée par le PO ; la mesure du seuil de 500 ms sera effectuée sur l'APK release.
- [ ] **Vérification de non-régression sur APK release :** à effectuer par le PO ; un hotfix sera ouvert si le comportement ou les performances observés le nécessitent.

---

# 9. Notes de développement

L'implémentation présente dans l'arbre de travail remplace la lecture des
entités catalogue complètes par deux projections Room légères, construit des
objets de recommandation dédiés, puis recharge uniquement les 100 identifiants
retenus par type afin de restituer les modèles complets à l'Accueil.

La comparaison avant/après et la mesure du seuil de 500 ms sont volontairement
reportées à l'essai de l'APK release, sur décision du PO (2026-08-18). Cette
dérogation ne constitue pas une preuve de performance ou d'identité du
classement ; elle autorise seulement la poursuite vers la livraison locale.

---

# 10. Review

## Critique

Aucun problème critique identifié par la compilation, les tests JVM et le
lint.

## Majeur

Aucun problème majeur bloquant identifié dans le câblage Room → repository →
use case. La preuve de performance TV et la comparaison fonctionnelle restent
à faire sur l'APK release conformément à la dérogation ci-dessus.

## Mineur

Les tests existants ont été adaptés aux nouvelles méthodes de repository, mais
aucun benchmark dédié ni test de comparaison n'a été ajouté.

## Corrections demandées

Aucune correction supplémentaire demandée avant l'essai de l'APK release.

---

# 11. Validation

**Statut : PARTIAL — validation APK release différée.**

La validation automatisée est verte. La validation fonctionnelle et la mesure
de performance sont laissées à l'essai release du PO ; un hotfix pourra être
appliqué ensuite si nécessaire.

---

# 12. Release

Version : en attente

Commit : en attente

Date : en attente
