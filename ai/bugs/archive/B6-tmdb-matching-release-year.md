# B6 - Faux positifs lors du rapprochement (matching) des médias TMDB avec la base de données locale

## Informations générales

Type:
Bug

Status:
RELEASED (v1.49.0 - 2026-07-21)

Created:
2026-07-21

Target version:
v1.49.0

---

# 1. Description

Ce ticket traite de la correction des faux positifs générés par l'algorithme actuel de rapprochement (matching) entre les données de l'API TMDB (tendances et futurs populaires) et le catalogue de médias local (IPTV).

Pour éliminer ces faux positifs, nous devons intégrer l'année de sortie (release year) comme critère de validation strict lors du rapprochement, avec une tolérance maximale admise de **+/- 1 an**.

---

# 2. Contexte

Actuellement, le croisement entre les tendances TMDB et le catalogue local est effectué dans `GetTrendingInCatalogUseCase.kt`. L'algorithme se base uniquement sur le score de similarité textuelle calculé par `ApproximateTitleMatcher.computeSimilarityNormalized(...) >= 0.8`.

Cette méthode uniquement textuelle présente des faiblesses importantes :
1. **Faux positifs de remakes :** Si un film ou une série populaire sur TMDB est un remake récent (par exemple, "Dune" en 2021), l'algorithme peut le rapprocher par erreur avec la version originale de 1984 présente dans le catalogue IPTV de l'utilisateur en raison de la similitude parfaite ou forte du titre.
2. **Homonymes et titres similaires :** Des films ou séries ayant des titres très proches ou identiques mais sortis à des époques complètement différentes (par exemple, un film de 1950 et un autre de 2025 portant le même nom) sont indûment considérés comme des correspondances.
3. **Expérience utilisateur dégradée :** L'utilisateur clique sur une affiche de tendance récente TMDB (par exemple, un film sorti cette année) et s'attend à regarder ce film récent, mais se retrouve à lancer un vieux classique ou une tout autre œuvre homonyme présente dans sa playlist IPTV.

En ajoutant la comparaison de l'année de sortie entre TMDB et la base de données IPTV, nous supprimerons ces associations erronées et améliorerons grandement la fiabilité du catalogue d'accueil.

---

# 3. Spécification fonctionnelle

## Objectif

Fiabiliser le matching de médias entre TMDB et le catalogue local en validant l'année de sortie, tout en restant robuste et tolérant face aux variations d'une année courantes sur les métadonnées (par exemple, année de production vs année de sortie en salle, décalages de calendrier).

## User Stories

- En tant qu'utilisateur, lorsque je parcours les "Tendances" ou les "Top 10" sur l'Accueil basés sur TMDB, je veux cliquer sur un film ou une série récente et lancer effectivement l'œuvre correspondante (et non une version homonyme plus ancienne ou un vieux film datant de plusieurs décennies).
- En tant qu'utilisateur, si mon catalogue ne contient pas la version exacte d'une œuvre récente mais possède une œuvre homonyme d'une autre époque, je ne veux pas que l'application fasse d'association erronée afin de ne pas fausser ma navigation.
- En tant qu'utilisateur, je veux que la comparaison d'année tolère de légers décalages (par exemple si TMDB liste un film en 2024 et mon fournisseur IPTV en 2023) car je sais que les dates de sortie peuvent varier selon les pays.

## Parcours utilisateur

1. L'utilisateur ouvre l'application ou l'écran d'Accueil.
2. L'application charge en arrière-plan les tendances/populaires de TMDB et les croise avec le catalogue local.
3. Pour chaque tendance TMDB (ex: "Gladiator II" sorti en 2024), l'algorithme parcourt les films locaux et compare les titres et les années.
4. Si le catalogue local contient "Gladiator" (1999) et "Gladiator II" (2024), l'algorithme de matching rejette "Gladiator" (1999) car l'écart d'année est supérieur à 1. Il accepte uniquement "Gladiator II" (2024) qui correspond parfaitement.
5. L'utilisateur voit l'affiche de "Gladiator II" sur son carrousel d'accueil TMDB. En cliquant dessus, il ouvre directement la fiche du film "Gladiator II" (2024) de son catalogue local.

## Règles métier

- **Règle d'extraction de l'année TMDB :**
  - L'année TMDB doit être extraite sous forme d'un entier `Int` à partir de la chaîne de date (`release_date` pour les films et `first_air_date` pour les séries). Cette extraction doit prendre uniquement les 4 premiers caractères (ex: `"2024-11-22"` -> `2024`).
- **Règle d'extraction de l'année IPTV :**
  - L'année locale doit provenir du champ `releaseYear` (Int?) de `VodStream` et `SeriesStream`, qui est enrichi à partir des métadonnées de l'API Xtream Codes. Un `releaseYear` égal à `0` ou `null` signifie que l'année est inconnue.
- **Règle de matching strict et de tolérance (+/- 1 an) :**
  - Le matching d'un média ne peut être validé que si **les deux conditions** suivantes sont remplies :
    1. **Similarité textuelle :** Le score de similarité textuelle normalisée entre le titre TMDB et le titre local doit être supérieur ou égal à `0.8` (règle existante).
    2. **Validation de l'année :**
       - Si l'année TMDB est connue (`tmdbYear != null`) **ET** que l'année IPTV locale est connue (`iptvYear != null && iptvYear > 0`), la différence absolue entre ces deux années doit être **inférieure ou égale à 1** :
         `abs(tmdbYear - iptvYear) <= 1`
       - Si cette condition d'année n'est pas respectée (écart > 1 an), le candidat est rejeté et l'algorithme continue sa recherche parmi d'autres candidats du catalogue local.
- **Règle de robustesse (fallback sans année) :**
  - Si l'année TMDB est inconnue (`null`), **OU** si l'année du média local IPTV est inconnue (`null` ou `0`), la validation de l'année est ignorée et considérée comme réussie par défaut. Le matching se fie alors exclusivement au score de similarité textuelle (`>= 0.8`).
  - Cette règle évite les faux négatifs sur des catalogues mal renseignés.

## Critères d'acceptation

- L'algorithme de matching n'associe jamais un média TMDB récent (ex: 2024) à un média local IPTV d'une autre époque (ex: 1984) si les deux possèdent des années de sortie valides dans la base de données.
- L'algorithme accepte un rapprochement si l'écart d'année est exactement de `-1`, `0` ou `+1` (ex: TMDB 2024 matche IPTV 2023, 2024 ou 2025).
- L'algorithme accepte un rapprochement par similarité textuelle seule si le média local ou TMDB ne possède pas d'année de sortie renseignée.
- Les tests unitaires de `GetTrendingInCatalogUseCase` doivent être enrichis avec des scénarios incluant :
  - Un match exact de titre mais rejeté pour cause d'années incompatibles (ex: Dune 2021 vs Dune 1984).
  - Un match de titre accepté avec écart d'un an toléré (ex: 2024 vs 2023).
  - Un match de titre accepté avec une des deux années absente (0 ou null).

## Cas limites

- **Homonymes multiples dans le catalogue :**
  Si le catalogue local contient plusieurs homonymes (par exemple, "Dune" (1984) et "Dune" (2021)) et que la tendance TMDB recherchée est "Dune" (2021) :
  - L'algorithme doit correctement exclure la version de 1984.
  - Si l'algorithme trouve la version de 2021, il doit la retenir comme meilleure correspondance.
- **Média local enrichi ultérieurement :**
  Si le catalogue local ne possédait pas d'année (0) au moment du premier matching et qu'il est ensuite synchronisé et enrichi avec l'année exacte de l'API Xtream Codes, le cache de matching TMDB (invalide au resync) doit se recalculer pour appliquer la règle stricte de l'année lors du prochain affichage.

## Gestion des erreurs

- **Chaînes de date malformées :** Si la date renvoyée par TMDB ou l'IPTV ne respecte pas le format attendu (ex: `"N/A"`, `"TBD"`), l'année extraite doit être considérée comme nulle (`null`), activant le mode de robustesse textuel plutôt que de faire crasher l'application.

---

# 4. Spécification technique

## Diagnostic confirmé

- `TrendingRepositoryImpl` extrait actuellement l'année TMDB avec `fullDate?.take(4)` et la conserve sous forme de `String?` dans `TrendingTitle.year`; une date malformée traverse donc la couche data sans validation.
- `GetTrendingInCatalogUseCase` pré-normalise les titres et applique le seuil `>= 0.8`, mais ne consulte aucune année.
- `VodStream.releaseYear` et `SeriesStream.releaseYear` sont déjà des `Int?`; les repositories convertissent la sentinelle persistée `0` en `null`.
- Le cache global 24 h doit changer de version afin de ne pas resservir des faux positifs calculés avant B6.

## Modèle d'année TMDB

`TrendingTitle.year` devient `Int?`. `TrendingRepositoryImpl` utilise le parseur défensif existant :

```kotlin
val year = ReleaseYearParser.parseYear(fullDate)
```

Une valeur absente ou inexploitable produit `null` sans exception.

## Matcher partagé

Un objet domaine pur `TmdbCatalogMatcher` centralise le rapprochement pour les Tendances et F9. Il pré-normalise les titres locaux une fois, applique le seuil textuel, filtre par année avant la sélection du meilleur score, conserve les ex aequo et respecte l'ordre du catalogue.

## Algorithme de validation de l'année
Pour chaque candidat IPTV (film ou série) dont le score de similarité est `>= 0.8` :
1. L'année TMDB est déjà exposée comme `Int?` par le repository.
2. Extraction de l'année IPTV :
   ```kotlin
   val iptvYear = movie.releaseYear?.takeIf { it > 0 } // Ou series.releaseYear?.takeIf { it > 0 }
   ```
3. Validation de l'année :
   ```kotlin
   val isYearCompatible = tmdbYear == null || iptvYear == null || iptvYear <= 0 ||
       kotlin.math.abs(tmdbYear.toLong() - iptvYear.toLong()) <= 1L
   ```
4. Seuls les candidats compatibles sont comparés au meilleur score puis ajoutés à `seenMatchedIds`. Un homonyme rejeté ne bloque donc jamais la bonne version.

## Cache et compatibilité

- Les clés `trends_*_global_v2` passent en `trends_*_global_v3`.
- L'ancien cache n'est pas migré : il devient simplement illisible et sera remplacé.
- L'invalidation existante par `maxOf(lastVodSync, lastSeriesSync)` est conservée.
- Aucun changement Room, migration, endpoint Retrofit, règle ProGuard ou dépendance Gradle.

## Performances
La comparaison d'année est en `O(1)` et la pré-normalisation reste unique. La complexité demeure `O(T * (V + S))`, sans requête Room dans les boucles.

---

# 5. Architecture

```text
TmdbApiService.getTrending()
        |
TrendingRepositoryImpl
  date String? -> ReleaseYearParser -> Int?
        |
GetTrendingInCatalogUseCase
        |
TmdbCatalogMatcher
  titre >= 0.8 ET année compatible
        |
cache global v3 -> résolution Room -> filtre catégories du profil -> Home
```

- `TrendingRepositoryImpl` reste responsable de l'appel TMDB, du mapping et du cache.
- `ReleaseYearParser` reste l'unique règle d'extraction défensive.
- `TmdbCatalogMatcher` porte le calcul pur commun à B6 et F9.
- `GetTrendingInCatalogUseCase` conserve l'orchestration, le filtrage des catégories masquées et la résolution des médias supprimés.

## Fichiers impactés

- `app/src/main/java/com/cstv/app/domain/model/TrendingTitle.kt`
- `app/src/main/java/com/cstv/app/data/repository/TrendingRepositoryImpl.kt`
- `app/src/main/java/com/cstv/app/domain/model/TmdbCatalogMatcher.kt` (nouveau)
- `app/src/main/java/com/cstv/app/domain/usecase/GetTrendingInCatalogUseCase.kt`
- `app/src/test/java/com/cstv/app/domain/model/TmdbCatalogMatcherTest.kt` (nouveau)
- `app/src/test/java/com/cstv/app/data/repository/TrendingRepositoryImplTest.kt`
- `app/src/test/java/com/cstv/app/domain/usecase/GetTrendingInCatalogUseCaseTest.kt`

---

# 6. Plan de développement

- [x] **Task 1 : Fiabiliser le modèle, le mapping et le cache TMDB**

  **Objectif :**
  Passer `TrendingTitle.year` à `Int?`, utiliser `ReleaseYearParser` et versionner le cache Trending en v3.

  **Fichiers :**
  - `app/src/main/java/com/cstv/app/domain/model/TrendingTitle.kt`
  - `app/src/main/java/com/cstv/app/data/repository/TrendingRepositoryImpl.kt`
  - `app/src/test/java/com/cstv/app/data/repository/TrendingRepositoryImplTest.kt`

  **Validation :**
  Tests des dates valides, absentes et malformées, et vérification qu'un cache v2 n'est plus relu.

- [x] **Task 2 : Créer le matcher TMDB/catalogue partagé**

  **Objectif :**
  Extraire la similarité, la tolérance `+/- 1`, les années inconnues et la sélection stable dans un composant domaine pur réutilisable par F9.

  **Fichiers :**
  - `app/src/main/java/com/cstv/app/domain/model/TmdbCatalogMatcher.kt`
  - `app/src/test/java/com/cstv/app/domain/model/TmdbCatalogMatcherTest.kt`

  **Validation :**
  Tests des écarts `-1`, `0`, `+1`, des années incompatibles/absentes, des homonymes et des ex aequo.

- [x] **Task 3 : Brancher le matcher dans les Tendances**

  **Objectif :**
  Remplacer les boucles du use case par le matcher partagé sans modifier le filtrage du profil ni la résolution dynamique du cache.

  **Fichiers :**
  - `app/src/main/java/com/cstv/app/domain/usecase/GetTrendingInCatalogUseCase.kt`
  - `app/src/test/java/com/cstv/app/domain/usecase/GetTrendingInCatalogUseCaseTest.kt`

  **Validation :**
  Tests du scénario Dune 2021/1984, du cache, de l'ordre, des catégories masquées et des médias supprimés.

- [x] **Task 4 : Validation complète de B6**

  **Objectif :**
  Valider le correctif et le contrat partagé attendu par F9.

  **Fichiers :**
  - Tous les fichiers B6 précédents.

  **Validation :**
  `./gradlew testDebugUnitTest`, `./gradlew assembleDebug` et `./gradlew lintDebug` passent.

---

# 7. Notes de développement

- 2026-07-21 — L'année TMDB est désormais normalisée en `Int?` avec
  `ReleaseYearParser`; les caches de matching passent de v2 à v3 afin que les
  associations calculées avant B6 ne soient jamais relues.
- 2026-07-21 — `TmdbCatalogMatcher` prépare les titres locaux une seule fois,
  filtre les années incompatibles avant la sélection par score et reste
  tolérant lorsqu'une année est inconnue.
- 2026-07-21 — Étape 7 : la déduplication des tendances est désormais séparée
  entre films et séries afin que deux identifiants Xtream identiques, issus de
  leurs espaces respectifs, ne s'excluent pas mutuellement. Le matcher défend
  aussi la sentinelle `0`, utilise une tolérance epsilon pour les ex aequo et
  couvre la validation d'année des séries.
- 2026-07-21 — Étape 8 : `testDebugUnitTest`, `assembleDebug` et `lintDebug`
  passent. Les avertissements lint existants ne sont pas introduits par B6.

---

# 8. Review

Revue technique (étape 6) réalisée le 2026-07-21 sur l'implémentation des Tasks 1
à 3. Périmètre : `TmdbCatalogMatcher`, `TrendingTitle`, `TrendingRepositoryImpl`,
`GetTrendingInCatalogUseCase` et leurs tests. Aucune modification de code.

## Synthèse

L'implémentation respecte la spécification fonctionnelle et technique : année
TMDB en `Int?` via `ReleaseYearParser`, matcher pur partagé, tolérance `+/- 1`,
robustesse sur années inconnues, cache versionné en v3, filtrage année avant
sélection du score. Les critères d'acceptation sont couverts par les tests
(Dune 2021/1984, écart `+/-1`, année absente, ex aequo, ordre catalogue). Les
problèmes ci-dessous sont périphériques et n'invalident pas le correctif B6
lui-même.

## Critique

Aucun.

## Majeur

### M1 — Collision d'ID entre films et séries dans `seenMatchedIds`

**Description :** `GetTrendingInCatalogUseCase` partage un unique `Set<Int>`
(`seenMatchedIds`) passé comme `excludedIds` aux deux appels
`findBestMatches` (films *et* séries). Or `VodStream.streamId` et
`SeriesStream.seriesId` proviennent de deux espaces d'identifiants Xtream
indépendants qui se recouvrent fréquemment (les deux séquences démarrent bas).
Un film dont le `streamId = 42` a matché ajoute `42` au set ; une série dont le
`seriesId = 42` sera alors exclue à tort de son propre matching
(`GetTrendingInCatalogUseCase.kt:81,89,92,103,106`).

**Impact :** faux négatif silencieux — une tendance série légitime peut ne pas
s'afficher sur l'Accueil parce que son id a été « consommé » par un film sans
rapport. Aléatoire selon le recouvrement des ids du fournisseur. Problème
probablement préexistant à B6 mais reconduit par le refactor.

**Correction attendue :** séparer la déduplication par type, p. ex. deux sets
distincts (`seenMovieIds` / `seenSeriesIds`), ou préfixer les ids. Ajouter un
test couvrant un `streamId` et un `seriesId` identiques.

**Status: RESOLVED (2026-07-21).** Deux ensembles distincts sont utilisés et
un test de use case couvre un film et une série portant tous deux l'id `42`.

## Mineur

### m1 — `isYearCompatible` ne neutralise plus la sentinelle `0`

**Description :** la spec (§4) prévoit `iptvYear == null || iptvYear <= 0 || ...`
dans la validation d'année. L'implémentation retire le garde `<= 0` de
`isYearCompatible` (`TmdbCatalogMatcher.kt:78-79`) et s'appuie sur la
normalisation `releaseYear?.takeIf { it > 0 }` faite dans `prepareMovies`/
`prepareSeries`. Correct pour le chemin actuel, mais `findBestMatches` /
`CatalogCandidate` sont publics et destinés à être réutilisés par F9. Un
appelant F9 qui construirait un `CatalogCandidate(releaseYear = 0)` sans passer
par les helpers verrait `abs(tmdbYear - 0)` rejeter le candidat au lieu de le
traiter comme année inconnue.

**Impact :** fragilité de contrat pour le composant partagé (F9) ; nul sur B6.

**Correction attendue :** ajouter `iptvYear <= 0` dans `isYearCompatible` (défense
en profondeur, coût O(1)) ou documenter explicitement que `CatalogCandidate`
doit être créé uniquement via `prepareMovies`/`prepareSeries`.

**Status: RESOLVED (2026-07-21).** `isYearCompatible` traite explicitement
`0` comme une année inconnue ; un test construit directement un
`CatalogCandidate` avec cette sentinelle.

### m2 — Branche « legacy cache » désormais inatteignable

**Description :** le passage du cache en v3 (`trends_*_global_v3`) garantit que
tout item désérialisé possède les listes `matchedMovies`/`matchedSeriesList`
(toujours écrites par `saveMatchedTrendsGlobal`). La branche de compatibilité
v1.47.25 (`matchedMovie`/`matchedSeries` singuliers,
`GetTrendingInCatalogUseCase.kt:171-206`) ne peut plus être atteinte via le
cache v3 : aucune ancienne structure n'est relue après le bump de version.

**Impact :** dette technique — ~35 lignes de code mort, bruit de maintenance.

**Correction attendue :** supprimer la branche legacy (hors périmètre B6, à
planifier) ou ajouter un commentaire justifiant sa conservation temporaire.

**Status: DEFERRED.** La suppression est volontairement reportée : elle est
hors du périmètre du rapprochement d'année et relève d'un nettoyage technique
à planifier séparément.

### m3 — Comparaison d'égalité sur `Double` pour les ex aequo

**Description :** la sélection des ex aequo utilise `score == bestScore`
(`TmdbCatalogMatcher.kt:71`) sur des `Double`. Fonctionne pour les scores
discrets (1.0, 0.9) mais deux candidats au score issu de Levenshtein
(`1.0 - distance/maxLength`) pourraient différer d'un epsilon flottant et ne pas
être groupés comme ex aequo.

**Impact :** très faible — cas marginal, dégrade au pire le regroupement de
doublons, jamais un faux positif.

**Correction attendue :** tolérance epsilon (`abs(score - bestScore) < 1e-9`) si
le comportement ex aequo doit être strictement garanti.

**Status: RESOLVED (2026-07-21).** La comparaison utilise désormais un epsilon
de `1e-9`.

### m4 — Couverture de tests : filtrage par année côté séries non testé

**Description :** `TmdbCatalogMatcherTest` ne couvre le filtrage d'année que via
`prepareMovies`. Le chemin `prepareSeries` (`SeriesStream.releaseYear`) n'a aucun
test de compatibilité d'année.

**Impact :** régression possible non détectée sur les tendances séries.

**Correction attendue :** dupliquer au moins un scénario année incompatible /
tolérance `+/-1` sur `prepareSeries`.

**Status: RESOLVED (2026-07-21).** Un test vérifie que la version série de
1984 est écartée au profit de celle de 2021.

### m5 — Clés de cache v2 non purgées

**Description :** les anciennes clés `trends_*_global_v2` restent stockées dans
`SharedPreferences` (jamais supprimées), conformément à la décision « pas de
migration » (§4). Simple résidu, sans impact fonctionnel.

**Impact :** négligeable (quelques Ko de SharedPrefs orphelins).

**Correction attendue :** aucune requise ; nettoyage opportuniste possible.

**Status: ACCEPTED.** Aucun changement requis conformément à la décision de
non-migration du cache v2.
