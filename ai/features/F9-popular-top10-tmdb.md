# F9 - Refonte du Top 10 Films & Séries sur l'Accueil avec l'API TMDB (Popular)

## Informations générales

Type:
Feature

Status:
TASK BREAKDOWN

Created:
2026-07-21

---

# 1. Description

Ce ticket concerne la refonte des deux sections "Top 10 Films" et "Top 10 Séries" affichées sur l'écran d'accueil de l'application (mobile et Android TV).

Actuellement basées sur un filtrage local par note (`rating >= 8.0`), ces sections doivent désormais utiliser les données de popularité mondiale fournies par l'API **The Movie Database (TMDB)** via ses routes `/movie/popular` et `/tv/popular`. Les médias populaires renvoyés par TMDB seront croisés avec le catalogue local (IPTV) par rapprochement de titres pour afficher un Top 10 réel et dynamique correspondant aux tendances globales, avec un mécanisme de repli robuste et silencieux si nécessaire.

---

# 2. Contexte

Actuellement, les sections "Top 10" s'appuient sur l'algorithme local `TopRatedSelector.selectTop10` qui filtre les flux locaux ayant une note supérieure ou égale à 8.0 et les trie par date d'ajout décroissante.

Cette approche présente plusieurs limites :
1. **Qualité et pertinence :** Les notes fournies par les panels IPTV sont souvent de mauvaise qualité, incomplètes ou statiques. Le Top 10 ainsi constitué ne reflète pas du tout la popularité réelle ou l'actualité cinématographique mondiale.
2. **Volumétrie :** Dans certains catalogues IPTV restreints, très peu ou aucun média ne possède une note `>= 8.0`, ce qui peut laisser les carrousels "Top 10" vides ou très pauvres.
3. **Expérience utilisateur :** L'alignement visuel et fonctionnel sur les standards des plateformes de streaming modernes (Netflix, Disney+) nécessite d'afficher les véritables succès populaires du moment.

L'utilisation des routes populaires de l'API TMDB permettra de redynamiser l'écran d'accueil avec des contenus populaires réels, tout en garantissant un fallback local si l'API externe est inaccessible.

---

# 3. Spécification fonctionnelle

## Objectif

Alimenter les sections "Top 10 Films" et "Top 10 Séries" de l'Accueil à partir des médias les plus populaires du moment sur TMDB, rapprochés du catalogue IPTV de l'utilisateur, tout en conservant une expérience fluide en cas d'absence de clé API ou de panne réseau.

## User Stories

- En tant qu'utilisateur, je veux voir les 10 films les plus populaires du moment (disponibles dans mon catalogue) s'afficher dans la section "Top 10 Films" sur ma page d'accueil.
- En tant qu'utilisateur, je veux voir les 10 séries les plus populaires du moment (disponibles dans mon catalogue) s'afficher dans la section "Top 10 Séries" sur ma page d'accueil.
- En tant qu'utilisateur, je ne veux pas subir de ralentissement ou d'écran figé sur l'accueil si l'API TMDB met du temps à répondre ou s'il y a une panne de réseau.
- En tant qu'utilisateur, si l'API TMDB est inaccessible ou si aucune clé TMDB n'est configurée, je veux continuer à voir un Top 10 de repli basé sur les meilleures notes de mon catalogue local pour que l'interface reste complète.

## Parcours utilisateur

### Parcours principal (TMDB disponible et correspondances trouvées)
1. L'utilisateur ouvre l'application ou revient sur l'écran d'Accueil.
2. L'interface principale s'affiche immédiatement avec le spinner principal pour le Live TV local, tandis que les sections de recommandations TMDB (Tendances et Top 10) affichent un état de chargement léger ou ne sont pas visibles tant que le calcul asynchrone n'est pas terminé (chargement découplé).
3. En arrière-plan, l'application récupère la liste des films et séries populaires via l'API TMDB (ou depuis le cache local s'il a moins de 24 heures).
4. Le rapprochement (matching) textuel et par année (avec la règle `+/- 1 an` de B6) s'exécute de manière performante.
5. Une fois le matching terminé, les deux sections "Top 10 Films" et "Top 10 Séries" apparaissent sur l'Accueil, affichant les cartes des correspondances locales trouvées (jusqu'à 10 cartes par carrousel), classées par ordre de popularité TMDB.
6. L'utilisateur navigue au D-pad (sur TV) ou au tactile (sur mobile), sélectionne un média et clique dessus pour ouvrir sa fiche détaillée locale.

### Parcours de repli (Pas d'API TMDB ou zéro correspondance)
1. L'utilisateur ouvre l'application mais son appareil n'est pas connecté à Internet, la clé TMDB est manquante, ou l'API TMDB renvoie une erreur (timeout, quota dépassé).
2. L'application d'erreur détecte silencieusement cette indisponibilité sans afficher de pop-up d'erreur intrusive.
3. Les carrousels "Top 10 Films" et "Top 10 Séries" basculent automatiquement sur la logique de repli locale en utilisant `TopRatedSelector.selectTop10` (médias locaux ayant une note `>= 8.0` triés par ajout décroissant).
4. L'utilisateur dispose ainsi de carrousels toujours remplis et fonctionnels, sans aucune interruption de service.

## Règles métier

- **Sources de données TMDB (Popular) :**
  - **Films :** Utilisation de la route `/movie/popular` de l'API TMDB (Page 1 uniquement, contenant 20 éléments).
  - **Séries :** Utilisation de la route `/tv/popular` de l'API TMDB (Page 1 uniquement, contenant 20 éléments).
- **Processus de Matching & Filtrage :**
  - Le rapprochement doit s'appuyer sur la logique combinée de similarité textuelle (`ApproximateTitleMatcher.computeSimilarityNormalized(...) >= 0.8`) et de validation de l'année de sortie (tolérance `+/- 1 an` définie dans B6).
  - Seuls les médias présents dans le catalogue local de l'utilisateur (VOD / Séries) et **non masqués** (n'appartenant pas à des catégories cachées du profil actif) sont éligibles pour l'affichage final.
- **Règles d'ordre et de limitation :**
  - Chaque liste finale ("Top 10 Films" et "Top 10 Séries") doit contenir **au maximum 10 éléments**.
  - L'ordre retourné par TMDB (qui correspond à la popularité décroissante de l'œuvre sur la plateforme) doit être rigoureusement conservé de gauche à droite sur les carrousels.
- **Gestion du Cache :**
  - Le résultat du matching des populaires (films et séries) doit être conservé dans un cache global persistant pendant **24 heures** (similaire au mécanisme de `TrendingRepositoryImpl` pour les tendances).
  - Ce cache doit être immédiatement invalidé si une synchronisation réussie du catalogue (VOD ou Séries) se produit, permettant à de nouveaux médias locaux d'être candidats au rapprochement.
- **Règles de Fallback Silencieux :**
  - En cas d'erreur réseau, de clé API absente, de timeout ou d'absence totale de résultats matchés, les listes "Top 10" affichent le repli local basé sur `TopRatedSelector.selectTop10`.
  - Le basculement vers le repli local doit s'effectuer de manière transparente, sans bloquer la réactivité de l'écran d'accueil ni lever d'erreur bloquante dans l'UI.

## Critères d'acceptation

- Les sections "Top 10 Films" et "Top 10 Séries" chargent de manière asynchrone et n'induisent aucun ralentissement ou blocage sur le spinner principal de l'Accueil.
- Si l'API TMDB est fonctionnelle et que des correspondances existent localement, les listes affichent les médias correspondants dans l'ordre décroissant de leur popularité TMDB.
- L'affichage visuel des cartes de médias dans le Top 10 utilise les cartes existantes (`HomeVodMovieCard` pour les films, `HomeSeriesShowCard` pour les séries) sans introduire de rupture visuelle.
- En cas d'absence d'Internet, de clé TMDB ou de résultats de matching, l'Accueil continue d'afficher les carrousels "Top 10" avec les médias locaux filtrés à la note `>= 8.0` triés par date d'ajout.
- Le comportement fonctionnel (navigation D-pad, tactile, ouverture de la fiche de détail) est parfaitement préservé.

## Cas limites

- **Moins de 10 correspondances dans le catalogue :** Si le croisement avec TMDB ne produit que $N$ correspondances (avec $1 \le N < 10$), le carrousel affiche exactement ces $N$ correspondances dans l'ordre de popularité. On ne complète pas la liste avec du repli local pour éviter de mélanger des logiques de tri différentes, préservant ainsi la pureté du classement.
- **Zéro correspondance dans le catalogue ($N = 0$) :** Si le matching TMDB n'aboutit à aucun résultat pour un carrousel donné, alors ce carrousel applique intégralement la logique de repli local (qui garantit 10 éléments basés sur les meilleures notes locales).
- **Média supprimé du catalogue local :** Si un média matché dans le cache TMDB est supprimé par la suite de la base de données locale, il est filtré et retiré dynamiquement à la volée lors de la résolution du cache (résolution réactive), évitant tout crash ou ouverture de fiche vide.

## Gestion des erreurs

- **Timeout réseau :** L'appel aux endpoints populaires TMDB doit avoir une limite de temps stricte (timeout de 10 secondes). Si ce délai expire, l'appel est annulé et l'application bascule silencieusement sur le repli local.
- **Réponse HTTP ou JSON invalide :** En cas de réponse HTTP d'erreur (ex: Code 500, 401 Clé API invalide) ou de JSON malformé retourné par l'API externe, l'exception est interceptée et logguée (`IptvLog`), puis le repli local est appliqué.

---

# 4. Spécification technique

## État existant et principe retenu

- `HomeViewModel` calcule déjà les deux fallbacks avec `TopRatedSelector.selectTop10` dans son chargement local principal.
- Le client TMDB existant possède des timeouts OkHttp de 10 secondes et le chargement Tendances est découplé du spinner principal.
- F9 réutilise obligatoirement `TmdbCatalogMatcher`, introduit par B6, afin de ne pas dupliquer les règles de titre et d'année.
- Le socle B6 doit donc être implémenté avant le branchement Popular.

## API Retrofit
Ajout de deux routes dans `TmdbApiService.kt` :
```kotlin
@GET("movie/popular")
suspend fun getPopularMovies(
    @Query("api_key") apiKey: String,
    @Query("language") language: String = "fr-FR",
    @Query("page") page: Int = 1
): TmdbTrendingResponseDto

@GET("tv/popular")
suspend fun getPopularSeries(
    @Query("api_key") apiKey: String,
    @Query("language") language: String = "fr-FR",
    @Query("page") page: Int = 1
): TmdbTrendingResponseDto
```

Le DTO existant est réutilisé. Comme les endpoints Popular ne fournissent pas nécessairement `media_type`, le repository impose `isMovie` selon la route appelée. Les IDs nombre/chaîne restent parsés défensivement, les éléments sans titre/ID sont ignorés et l'année devient un `Int?` produit par `ReleaseYearParser` conformément à B6.

## Repository
Une interface domaine `PopularRepository` et une implémentation singleton dédiée isolent Popular de Trending. Elles gèrent :

- la récupération de la page 1 films et séries;
- deux caches globaux persistants de correspondances, versionnés dans le namespace `tmdb_popular_cache`;
- un TTL de 24 heures;
- une invalidation Films par `getVodAllStreamsSyncedAt()` et Séries par `getSeriesAllStreamsSyncedAt()`.

Les correspondances sont mises en cache avant filtrage par profil. Une liste vide n'est jamais persistée. Une synchronisation d'un type ne recalcule pas inutilement l'autre.

## Use case Popular

Un unique `GetPopularTop10InCatalogUseCase` orchestre indépendamment films et séries : cache, fetch, catalogue Room, `TmdbCatalogMatcher`, sauvegarde globale, résolution dynamique des IDs, catégories masquées, maintien de l'ordre TMDB et `take(10)`.

```kotlin
data class PopularTop10Result(
    val movies: List<VodStream>?,
    val series: List<SeriesStream>?
)
```

`null` signifie « conserver le fallback local » pour ce type. Une liste de 1 à 9 correspondances remplace intégralement le fallback et n'est jamais complétée. Les branches sont indépendantes : l'échec Films ne masque pas un résultat Séries valide, et inversement. Les `CancellationException` sont propagées; les autres erreurs sont journalisées puis converties en `null`.

## ViewModel et Couche UI
`HomeState` conserve les fallbacks locaux existants et ajoute :

```kotlin
val popularTopVodStreams: List<VodStream>? = null
val popularTopSeriesStreams: List<SeriesStream>? = null
```

`HomeScreen` choisit `popularTopVodStreams ?: topVodStreams` et `popularTopSeriesStreams ?: topSeriesStreams`, y compris pour les sections étendues. Cette séparation empêche la coroutine locale et la coroutine TMDB de s'écraser selon leur ordre de terminaison.

`HomeViewModel.loadHomeData()` lance Popular dans une coroutine dédiée, indépendante de `isLoading`, avec `withTimeoutOrNull(15_000L)`. Les champs Popular sont remis à `null` avant un nouveau chargement. Le fallback local apparaît donc sans attendre TMDB et reste disponible en cas d'échec.

Les cartes, callbacks et navigations mobile/TV existants sont conservés.

## DI, sécurité et compatibilité

- `AppModule` fournit `PopularRepositoryImpl`; le use case est injectable par constructeur.
- Aucune bibliothèque, clé secrète, table Room ou migration supplémentaire.
- `TmdbApiService` est déjà couvert par la règle `-keep` de `app/proguard-rules.pro`.
- Aucun nouvel écran ni changement de navigation.

## Contraintes de performance

- Page 1 uniquement, soit au plus 20 titres par type.
- Titres locaux pré-normalisés une fois par calcul dans `TmdbCatalogMatcher` sur `Dispatchers.Default`.
- Aucun appel réseau dans la coroutine qui pilote le spinner principal.

---

# 5. Architecture

```text
TmdbApiService
├── /movie/popular?page=1
└── /tv/popular?page=1
          |
PopularRepositoryImpl
  mapping défensif + caches 24 h par type
          |
GetPopularTop10InCatalogUseCase
  catalogues Room + TmdbCatalogMatcher(B6)
  résolution + catégories masquées + take(10)
          |
PopularTop10Result (branches nullables)
          |
HomeViewModel, coroutine indépendante de isLoading
          |
Popular TMDB ?: TopRatedSelector local
          |
cartes Home existantes mobile et TV
```

## Fichiers impactés

- `app/src/main/java/com/cstv/app/data/remote/api/TmdbApiService.kt`
- `app/src/main/java/com/cstv/app/domain/repository/PopularRepository.kt` (nouveau)
- `app/src/main/java/com/cstv/app/data/repository/PopularRepositoryImpl.kt` (nouveau)
- `app/src/main/java/com/cstv/app/domain/model/PopularTop10Result.kt` (nouveau)
- `app/src/main/java/com/cstv/app/domain/model/TmdbCatalogMatcher.kt` (fourni par B6)
- `app/src/main/java/com/cstv/app/domain/usecase/GetPopularTop10InCatalogUseCase.kt` (nouveau)
- `app/src/main/java/com/cstv/app/presentation/home/HomeViewModel.kt`
- `app/src/main/java/com/cstv/app/presentation/home/HomeScreen.kt`
- `app/src/main/java/com/cstv/app/di/AppModule.kt`
- tests repository, use case et `HomeViewModelTest.kt`.

---

# 6. Plan de développement

- [ ] **Task 1 : Ajouter et tester les endpoints Popular**

  **Objectif :**
  Ajouter les routes page 1, vérifier le mapping défensif films/séries et le parsing d'année fourni par B6.

  **Fichiers :**
  - `app/src/main/java/com/cstv/app/data/remote/api/TmdbApiService.kt`
  - tests DTO/repository concernés.

  **Validation :**
  Tests des IDs nombre/chaîne, champs absents et dates malformées.

- [ ] **Task 2 : Créer `PopularRepository` et ses caches séparés**

  **Objectif :**
  Isoler la source Popular et gérer les caches globaux 24 h avec invalidation VOD/Séries indépendante.

  **Fichiers :**
  - `app/src/main/java/com/cstv/app/domain/repository/PopularRepository.kt`
  - `app/src/main/java/com/cstv/app/data/repository/PopularRepositoryImpl.kt`
  - `app/src/main/java/com/cstv/app/di/AppModule.kt`
  - `app/src/test/java/com/cstv/app/data/repository/PopularRepositoryImplTest.kt`

  **Validation :**
  Tests du TTL, des cache hits, des resynchronisations indépendantes, de la clé absente et des erreurs réseau/JSON.

- [ ] **Task 3 : Créer le résultat et le use case Popular commun**

  **Objectif :**
  Orchestrer les deux types avec `TmdbCatalogMatcher`, préserver l'ordre, résoudre le cache, filtrer le profil et retourner deux branches nullables indépendantes.

  **Fichiers :**
  - `app/src/main/java/com/cstv/app/domain/model/PopularTop10Result.kt`
  - `app/src/main/java/com/cstv/app/domain/usecase/GetPopularTop10InCatalogUseCase.kt`
  - `app/src/test/java/com/cstv/app/domain/usecase/GetPopularTop10InCatalogUseCaseTest.kt`

  **Validation :**
  Tests de l'ordre, de la limite 10, des listes 1 à 9, du zéro match, des branches indépendantes, des catégories masquées et des médias supprimés.

- [ ] **Task 4 : Intégrer Popular dans `HomeViewModel` sans course**

  **Objectif :**
  Ajouter les deux champs Popular optionnels, conserver les fallbacks locaux et lancer le use case dans une coroutine découplée avec timeout.

  **Fichiers :**
  - `app/src/main/java/com/cstv/app/presentation/home/HomeViewModel.kt`
  - `app/src/test/java/com/cstv/app/presentation/home/HomeViewModelTest.kt`

  **Validation :**
  Tests du fallback immédiat, du remplacement indépendant, du rechargement et du timeout silencieux sans impact sur `isLoading`.

- [ ] **Task 5 : Brancher les listes effectives dans `HomeScreen`**

  **Objectif :**
  Utiliser la priorité Popular/fallback dans les deux carrousels et leurs sections étendues sans modifier les cartes ni la navigation.

  **Fichiers :**
  - `app/src/main/java/com/cstv/app/presentation/home/HomeScreen.kt`

  **Validation :**
  Vérification mobile et TV des clics, du tactile, du D-pad et de l'ordre affiché.

- [ ] **Task 6 : Validation complète de F9**

  **Objectif :**
  Exécuter toute la non-régression après intégration de B6 et F9.

  **Fichiers :**
  - Tous les fichiers F9 précédents.

  **Validation :**
  `./gradlew testDebugUnitTest`, `./gradlew assembleDebug` et `./gradlew lintDebug` passent.

---

# 7. Notes de développement

(Cette section sera enrichie au cours du développement).
