# B14 - Échec de l'application de la tolérance d'année lors du rapprochement TMDB (Remakes/Homonymes)

## Informations générales

Type:
Bug

Status:
IMPLEMENTATION

Created:
2026-07-26

Target version:
v1.56.0

---

# 1. Description

Certains utilisateurs signalent qu'en parcourant les sections basées sur les données TMDB (Tendances et Top 10 populaires de l'Accueil), le rapprochement (matching) avec le catalogue local IPTV produit encore des faux positifs.

Des films ou des séries de type remake ou possédant des titres identiques (homonymes) mais sortis à des époques différentes (ex. Dune de 1984 vs Dune de 2021, ou un film classique et son remake moderne) sont associés de manière erronée. L'utilisateur clique sur l'affiche TMDB d'une œuvre récente et se retrouve à visionner une version beaucoup plus ancienne (ou inversement), ce qui indique que la tolérance de +/- 1 an sur l'année de sortie n'est pas correctement respectée ou appliquée dans l'algorithme de matching.

---

# 2. Contexte

Afin de lier les tendances et les populaires TMDB au catalogue local de l'utilisateur, l'application s'appuie sur un composant commun nommé `TmdbCatalogMatcher` (introduit initialement par le ticket B6). Cet objet pur compare la similarité textuelle des titres normalisés (`>= 0.8`) et doit valider que la différence entre l'année de sortie TMDB (`tmdbYear`) et l'année de sortie IPTV (`iptvYear`) n'excède pas +/- 1 an.

La méthode clé `isYearCompatible` est définie ainsi :
```kotlin
private fun isYearCompatible(tmdbYear: Int?, iptvYear: Int?): Boolean =
    tmdbYear == null || iptvYear == null || iptvYear <= 0 ||
        abs(tmdbYear.toLong() - iptvYear.toLong()) <= 1L
```

Si le matching applique théoriquement cette règle, les retours terrain démontrent que des faux positifs persistent. Plusieurs scénarios peuvent expliquer ce dysfonctionnement :
1. **Échec de parsing de l'année IPTV :** Si l'année locale n'est pas récupérée ou est mal parsée par `ReleaseYearParser`, elle est considérée comme nulle ou égale à 0. Dans ce cas, la validation d'année est totalement ignorée (fallback), et le matcher s'appuie uniquement sur la similarité textuelle (`>= 0.8`), ce qui associe indûment des homonymes d'époques éloignées.
2. **Échec de parsing de l'année TMDB :** Si la date de sortie envoyée par l'API TMDB n'est pas correctement convertie en entier, `tmdbYear` devient `null` et le contrôle de l'année est également contourné.
3. **Erreur d'intégration ou d'alimentation :** Lors de la préparation des candidats dans les cas d'utilisation (`GetTrendingInCatalogUseCase` ou `GetPopularTop10InCatalogUseCase`), il se peut que les champs d'années des flux locaux ne soient pas correctement propagés ou convertis.

---

# 3. Objectif

Ce ticket vise à investiguer et à corriger les failles de l'algorithme de matching d'années dans `TmdbCatalogMatcher` et ses services dépendants pour garantir un blocage strict des correspondances d'œuvres homonymes ou de remakes si leurs années de sortie respectives sont connues et diffèrent de plus d'un an.

L'objectif de cette Étape 1 est de poser les bases de l'analyse et d'identifier toutes les hypothèses de défaillance.

---

# 4. Hypothèses

- **Hypothèse 1 (Parsing de l'année IPTV) :** Le parser `ReleaseYearParser` ne parvient pas à extraire l'année de certains formats de chaînes de caractères de `releasedate` renvoyés par des panels IPTV spécifiques (par exemple, des formats contenant des fuseaux horaires ou des caractères spéciaux non gérés par la regex `(?:19|20)\d{2}`), ce qui renvoie un `releaseYear` nul et désactive la vérification d'année.
- **Hypothèse 2 (Extraction de l'année TMDB) :** Les dates de sortie renvoyées par TMDB (ex. `release_date` pour les films, `first_air_date` pour les séries) pour certains médias populaires ou futurs ne respectent pas le format attendu ou sont manquantes dans la réponse de l'API lors de l'appel aux tendances, forçant le matcher à ignorer le contrôle d'année.
- **Hypothèse 3 (Incohérence ou valeur sentinelle par défaut) :** L'année du catalogue IPTV local (`releaseYear`) est par défaut à `0` ou `null` pour de nombreux streams car la synchronisation initiale n'a pas encore enrichi les métadonnées de tous les médias, ouvrant grand la porte au matching par simple ressemblance de titre.
- **Hypothèse 4 (Cache persistant obsolète) :** Le cache persistant global des tendances ou des populaires n'invalide pas ou ne régénère pas correctement les matchings erronés lorsque les métadonnées IPTV locales (l'année de sortie notamment) sont enrichies ultérieurement par une synchronisation en arrière-plan.

---

# 5. Questions ouvertes

1. **Formats de date réels :** Quels sont les formats exacts de `releasedate` présents dans la base de données IPTV de l'utilisateur pour les titres qui créent des faux positifs ? Faut-il enrichir `ReleaseYearParser` pour qu'il soit encore plus résilient ?
2. **Couverture des tests de matching :** Les tests de `TmdbCatalogMatcherTest` couvrent-ils bien des cas où une des deux années est nulle mais où le titre est identique ? Avons-nous des tests spécifiques simulant la présence simultanée de "Dune (1984)" et "Dune (2021)" dans le catalogue ?
3. **Statut de l'enrichissement des métadonnées locales :** Les endpoints de détails des flux IPTV (`getVodInfo` et `getSeriesInfo`) sont-ils appelés de manière proactive pour renseigner l'année de sortie des films/séries locaux, ou l'année n'est-elle disponible que de manière sporadique ?
4. **Log de matching :** Devons-nous ajouter des traces de débogage plus verbeuses dans `TmdbCatalogMatcher` pour enregistrer précisément pourquoi un candidat a été accepté ou rejeté (ex : `"Match refusé pour Dune: TMDB=2021, IPTV=1984, écart=37"`) afin de faciliter l'analyse en production ?

---

# 6. Spécification fonctionnelle

## User stories

- En tant qu'utilisateur de l'Accueil, je veux qu'un titre TMDB soit relié à la bonne œuvre de mon catalogue IPTV afin de ne pas ouvrir un remake ou un homonyme d'une autre époque.
- En tant qu'utilisateur, je veux conserver les recommandations dont l'année n'est pas connue afin que l'absence de métadonnée ne masque pas inutilement du contenu potentiellement pertinent.

## Comportement attendu

Le rapprochement utilisé par les sections Accueil alimentées par TMDB (Tendances et Top 10 populaires) doit comparer l'année TMDB et l'année du candidat IPTV lorsque les deux sont connues et valides.

- Un candidat reste éligible lorsque l'écart absolu entre les deux années est inférieur ou égal à un an.
- Un candidat est exclu lorsque cet écart est supérieur à un an, même si son titre normalisé atteint le seuil de similarité textuelle.
- L'exclusion s'applique avant le choix du meilleur candidat : un homonyme plus ancien ou plus récent ne doit pas être retenu à la place d'un candidat compatible.
- Si l'une des deux années est absente, invalide ou inconnue, le rapprochement conserve son comportement de repli actuel fondé sur le titre ; ce ticket ne doit pas supprimer ces recommandations uniquement faute de date.
- La règle s'applique de manière identique aux films et aux séries, sans modifier le seuil de similarité des titres ni les autres règles de sélection existantes.

## Parcours utilisateur

1. L'utilisateur ouvre l'Accueil alors que les listes Tendances ou Top 10 sont affichées.
2. L'application rapproche chaque entrée TMDB avec les médias locaux de même nature.
3. Pour deux titres similaires dont les années sont connues, l'application écarte les candidats distants de plus d'un an.
4. Un appui sur l'affiche d'un titre récent ouvre donc le média local compatible, et jamais un remake ou homonyme à l'année incompatible.

## Règles métier et cas limites

- Les années positives sont les seules considérées comme connues ; `null`, `0` ou une valeur invalide suivent le comportement de repli.
- La limite est inclusive : 2021 est compatible avec 2020 et 2022, mais pas avec 2019 ou 2023.
- Un titre TMDB sans date ou un média IPTV sans année ne doit pas provoquer d'erreur, de crash ni faire disparaître la liste ; le rapprochement textuel existant reste applicable.
- Si tous les homonymes sont exclus par l'année, l'entrée TMDB n'est pas associée à un média local incompatible.
- Le périmètre est limité au rapprochement du catalogue local pour les listes TMDB concernées ; il ne modifie ni les détails des médias, ni les favoris, ni la recherche.

## Critères d'acceptation

- [ ] Avec « Dune » TMDB (2021) et deux candidats locaux « Dune » (1984 et 2021), seul le candidat 2021 peut être sélectionné.
- [ ] Un candidat local daté 2020 ou 2022 reste sélectionnable pour une entrée TMDB datée 2021 si son score de titre est suffisant.
- [ ] Un candidat local daté 2019 ou 2023 est exclu pour une entrée TMDB datée 2021, même avec un titre identique.
- [ ] Un rapprochement dont l'une des années est inconnue reste fonctionnel selon les règles textuelles existantes et ne provoque aucune erreur visible.
- [ ] La même règle est vérifiée pour au moins un film et une série dans Tendances et Top 10 populaires.

---

# 7. Spécification technique

## 7.1 Résultat de l'investigation — les 4 hypothèses de l'étape 1

L'étape 1 supposait que le contrôle d'année était **contourné**. La lecture du code montre que ce n'est pas le cas : la garde est bien appliquée, et en amont du scoring.

`TmdbCatalogMatcher.findBestMatches` (`domain/model/TmdbCatalogMatcher.kt:57-58`) :

```kotlin
for (candidate in catalog) {
    if (candidate.id in excludedIds || !isYearCompatible(tmdbYear, candidate.releaseYear)) continue
    val score = ApproximateTitleMatcher.computeSimilarityNormalized(...)
```

Statut de chaque hypothèse :

| # | Hypothèse | Statut | Preuve |
|---|---|---|---|
| 1 | `ReleaseYearParser` échoue sur certains formats | **Écartée** | `Regex("(?:19|20)\\d{2}")` couvre `1989`, `1989-05-12`, `2015-03`, `12 May 1989`, `05/12/1989`, ISO 8601 avec fuseau. Le parser renvoie `null` proprement sur `N/A` / `""` / `null`. Aucun format plausible identifié qui casserait. |
| 2 | L'année TMDB est perdue au mapping | **Écartée** | `TrendingRepositoryImpl.kt:57-58` et `PopularRepositoryImpl.kt:77-82` utilisent le **même** `ReleaseYearParser` sur `release_date` / `first_air_date`. Le seul cas `null` est une date réellement absente côté TMDB (titre non sorti), qui relève du repli assumé par la spécification fonctionnelle. |
| 3 | Beaucoup de flux locaux ont `releaseYear` nul | **Confirmée — facteur aggravant** | `VodDao.kt:114` / `SeriesDao.kt:157` : `SELECT * FROM vod_streams WHERE ... OR releaseYear IS NULL LIMIT :limit`. L'enrichissement est **borné par lot** et n'est déclenché qu'en fin de synchronisation (`CatalogSyncManagerImpl.kt:213`). À tout instant, une part importante du catalogue a `releaseYear = null`. |
| 4 | Le cache persistant ne se régénère pas après enrichissement | **Confirmée — portée réduite** | Voir §7.3. |

## 7.2 Cause racine — départage par ordre de catalogue, pas par proximité d'année

`findBestMatches` ne renvoie pas *un* candidat mais **tous les candidats à égalité de score** (`TmdbCatalogMatcher.kt:66-73`), et le score est purement textuel.

Conséquence, pour une entrée TMDB « Dune » (2021) sur un catalogue contenant :

| id | nom | `releaseYear` | passe `isYearCompatible` ? | score |
|---|---|---|---|---|
| 1 | Dune | `null` (non encore enrichi — hypothèse 3) | ✅ oui, par repli | 1.0 |
| 2 | Dune | 2021 | ✅ oui, écart 0 | 1.0 |

Les deux candidats sont retenus **à égalité parfaite**. Le choix final revient alors à l'ordre du catalogue, en aval du matcher :

- `GetTrendingInCatalogUseCase.kt:150` → `allowedMovies.first()`
- `GetTrendingInCatalogUseCase.kt:168` → `allowedSeries.first()`
- `GetPopularTop10InCatalogUseCase.kt:108-115` → premier `id` non masqué de `match.localIds`

Si le « Dune » de 1984 non enrichi arrive avant dans le catalogue, c'est **lui** qui est ouvert. Le repli « année inconnue » — voulu et légitime quand il n'existe *aucune* alternative datée — est ici appliqué alors qu'un candidat parfaitement daté et compatible existe dans le même lot.

Le test `TmdbCatalogMatcherTest.findBestMatches_keepsCatalogOrderForEqualScores` (`:99`) verrouille aujourd'hui exactement ce comportement d'ordre, ce qui explique que la régression soit passée entre les mailles de B6.

**Formulation de la cause racine :** le repli « année inconnue » est évalué *par candidat* et non *relativement au meilleur lot disponible*. Un candidat sans année ne devrait jamais concourir à armes égales avec un candidat dont l'année est connue et compatible.

## 7.3 Cause secondaire — fenêtre de cache entre synchronisation et enrichissement

`CatalogSyncManagerImpl.runSync()` estampille `VOD_STREAMS` / `SERIES_STREAMS` **avant** de lancer l'enrichissement (`:186-208` puis `:213`). `CatalogFreshness.vodSyncedAt()` ne lit que `VOD_STREAMS.lastSuccessAt`, et `TrendingRepositoryImpl.getCachedMatchedTrendsGlobal` invalide sur `lastFetchTime < lastCatalogSyncTime` (`:85`).

Chronologie du défaut :

```
T0        VOD_STREAMS estampillé T0
T0+1s     l'utilisateur ouvre l'Accueil → rematch (années encore nulles)
          → cache écrit à T0+1s, donc > T0 : considéré valide
T0+60s    l'enrichissement remplit les releaseYear
          → aucune estampille postérieure à T0 → le cache reste valide
```

Le mauvais appariement est alors figé jusqu'à l'expiration du TTL ou la synchronisation suivante. La fenêtre est bornée mais reproductible à chaque premier lancement sur un catalogue neuf — précisément le moment où l'hypothèse 3 est la plus forte.

## 7.4 Périmètre exclu

- Aucune modification de `ReleaseYearParser` (§7.1, hypothèse 1 écartée) ni du seuil `MIN_SIMILARITY = 0.8`.
- Aucune modification de schéma Room : la table `catalog_sync_state` porte déjà la section `ENRICHMENT` (`CatalogSyncStateEntity.kt`, `CatalogSection.ENRICHMENT`). **Pas de migration.**
- Aucun enrichissement proactif supplémentaire à la volée : appeler `getVodInfo` pendant le matching violerait le back-off `XtreamRequestGate` mis en place par les correctifs 403 (`b79967b`, `ad91dd8`).

---

# 8. Architecture

## 8.1 Décision D1 — départage par rang d'année dans `TmdbCatalogMatcher`

Le tri passe d'une clé unique `score` à une clé composite `(score, yearRank)`, `yearRank` étant croissant = moins bon :

| `yearRank` | Condition |
|---|---|
| `0` | `tmdbYear` et `candidate.releaseYear` connus, écart `== 0` |
| `1` | `tmdbYear` et `candidate.releaseYear` connus, écart `== 1` |
| `2` | `tmdbYear` inconnu **ou** `candidate.releaseYear` inconnu (repli) |

`isYearCompatible` reste la garde d'exclusion préalable ; `yearRank` n'ordonne que ce qui a déjà franchi cette garde. Un écart `> 1` reste éliminé, jamais rangé.

Ne sont renvoyés que les candidats du **meilleur couple `(score, yearRank)`**. À `yearRank` égal, l'ordre du catalogue est conservé — la garantie du test `:99` est donc intacte.

Signature ajustée :

```kotlin
data class Match<T>(
    val candidates: List<T>,
    val score: Double,
    val yearRank: Int   // nouveau : 0 exact, 1 tolérance ±1, 2 repli sans année
)
```

`yearRank` est exposé pour deux raisons : rendre la décision observable dans les logs des use cases (voir D3) et fournir aux tests un point d'assertion sur la *raison* du choix, pas seulement sur son résultat.

**Pourquoi ce choix plutôt qu'un rejet strict des candidats sans année.** Rejeter tout candidat non daté dès que `tmdbYear` est connu supprimerait des recommandations valides sur un catalogue peu enrichi, ce que la spécification fonctionnelle interdit explicitement (« ce ticket ne doit pas supprimer ces recommandations uniquement faute de date »). Le rang conserve ces candidats **en dernier recours** : ils ne gagnent que lorsqu'aucun candidat daté compatible n'existe.

**Pourquoi dans le matcher et non dans les use cases.** Le `.first()` aval est appliqué après le filtrage par catégories masquées ; y placer la logique d'année obligerait à repropager `tmdbYear` et les années locales jusque-là, et à la dupliquer dans les deux use cases. `TmdbCatalogMatcher` est déjà l'unique point de décision partagé — c'est le seul endroit où la règle ne peut pas diverger entre Tendances et Top 10.

## 8.2 Décision D2 — l'enrichissement participe à la fraîcheur du catalogue

`CatalogFreshness` intègre l'estampille `ENRICHMENT` :

```kotlin
open suspend fun vodSyncedAt(): Long =
    maxOf(syncedAt(CatalogSection.VOD_STREAMS), syncedAt(CatalogSection.ENRICHMENT))

open suspend fun seriesSyncedAt(): Long =
    maxOf(syncedAt(CatalogSection.SERIES_STREAMS), syncedAt(CatalogSection.ENRICHMENT))
```

L'enrichissement étant toujours estampillé **après** les sections catalogue de la même synchronisation, `maxOf` vaut l'estampille d'enrichissement dès qu'elle existe. Un cache d'appariement écrit après l'enrichissement reste donc valide ; un cache écrit dans la fenêtre décrite en §7.3 est invalidé. Aucune invalidation supplémentaire n'est introduite par rapport à aujourd'hui : le nombre de rafraîchissements TMDB ne change pas au régime établi.

`ENRICHMENT` est volontairement absent de `CatalogSection.CATALOG_SECTIONS` (complétude hors-ligne) — ce point n'est pas modifié, seule la lecture de fraîcheur des caches TMDB l'intègre.

## 8.3 Décision D3 — traçabilité de la décision (réponse à la question ouverte n°4)

Pas de log dans `TmdbCatalogMatcher` : `domain/model/` héberge des objets purs et testables sans dépendance (AGENTS.md, « Structure de dossiers attendue »), et `IptvLog` y introduirait une dépendance sur `di/`.

La trace est émise par les use cases, qui disposent déjà de `IptvLog`, à partir du `yearRank` renvoyé :

```
🎯 Match movie: 'Dune' (TMDB 2021) ↔ 2 version(s), score 1.0, yearRank 0 (année exacte)
```

Un `yearRank = 2` sur une entrée TMDB **datée** signale un appariement par repli — le symptôme exact de ce ticket, désormais lisible en production sans instrumentation supplémentaire.

## 8.4 Flux de données après correction

```
TMDB (release_date / first_air_date)
   └─ ReleaseYearParser.parseYear ──────────────┐
                                                 ▼
Xtream (releasedate)                    findBestMatches(tmdbTitle, tmdbYear, catalog)
   └─ ReleaseYearParser.parseYear                 │
      └─ Room releaseYear (0 = sentinelle)        ├─ 1. exclusion : excludedIds
         └─ prepareMovies/prepareSeries           │        + isYearCompatible (écart > 1 → rejet)
            └─ CatalogCandidate.releaseYear ──────┤─ 2. score textuel (seuil 0.8)
               (takeIf { it > 0 } → null)         ├─ 3. yearRank 0 / 1 / 2          ◄── NOUVEAU
                                                  └─ 4. meilleur (score, yearRank)  ◄── NOUVEAU
                                                          │
                                     ┌────────────────────┴────────────────────┐
                                     ▼                                         ▼
                      GetTrendingInCatalogUseCase                GetPopularTop10InCatalogUseCase
                        filtre catégories masquées                 filtre catégories masquées
                        → allowedMovies.first()                    → premier id non masqué
                        (départage désormais fiable : tous les candidats restants
                         partagent le même rang d'année)
                                     │
                                     ▼
                   cache persistant, invalidé par CatalogFreshness
                          (VOD/SERIES_STREAMS ⊔ ENRICHMENT)         ◄── NOUVEAU
```

## 8.5 Fichiers impactés

**Modifiés**

| Fichier | Nature |
|---|---|
| `domain/model/TmdbCatalogMatcher.kt` | `yearRank`, sélection sur `(score, yearRank)`, champ dans `Match` |
| `data/sync/CatalogFreshness.kt` | `maxOf(...)` avec `CatalogSection.ENRICHMENT` |
| `domain/usecase/GetTrendingInCatalogUseCase.kt` | log du `yearRank` et de `tmdbYear` |
| `domain/usecase/GetPopularTop10InCatalogUseCase.kt` | log du `yearRank` et de `tmdbYear` |

**Tests**

| Fichier | Nature |
|---|---|
| `domain/model/TmdbCatalogMatcherTest.kt` | + cas de la cause racine (§7.2) ; + rang exact préféré à ±1 ; + repli conservé si aucun candidat daté ; les 7 tests existants restent verts sans modification |
| `data/sync/CatalogFreshnessTest.kt` | nouveau ou étendu : `ENRICHMENT` postérieur l'emporte, `ENRICHMENT` absent (`0L`) n'écrase pas |
| `domain/usecase/GetTrendingInCatalogUseCaseTest.kt` | + non-régression bout en bout : catalogue mixte daté / non daté |
| `domain/usecase/GetPopularTop10InCatalogUseCaseTest.kt` | idem, films **et** séries (critère d'acceptation n°5) |

**Aucune nouvelle dépendance. Aucune migration Room. Aucune règle ProGuard.**

## 8.6 Risques et contraintes

| Risque | Portée | Traitement |
|---|---|---|
| Perte de recommandations sur catalogue peu enrichi | Fort si D1 était un rejet strict | Écarté par construction : `yearRank = 2` conserve les candidats non datés en dernier recours |
| Caches persistants existants encore porteurs d'appariements erronés | Tous les utilisateurs à la mise à jour | Traité par D2 : le premier enrichissement postérieur à l'installation invalide les caches. Aucun bump de clé (`_v3`) nécessaire |
| Doublons légitimes (VF/VOSTFR même année) départagés différemment | Moyen | Même `yearRank` → même lot, ordre du catalogue préservé : comportement inchangé |
| Coût CPU du rang | Négligeable | Une soustraction par candidat déjà retenu par `isYearCompatible`, sur un lot borné à la taille du catalogue déjà parcouru |
| Le test `keepsCatalogOrderForEqualScores` devient un faux garant | Faible | Il reste valide (années identiques) mais est complété par un test explicitement mixte |

---

# 9. Plan de développement

- [x] **Tâche 1 — `yearRank` dans `TmdbCatalogMatcher`**

  Objectif :
  Départager les candidats à score textuel égal par la proximité d'année (§8.1), au lieu de laisser l'ordre du catalogue trancher entre un candidat daté compatible et un candidat non daté.

  Fichiers :
  - `domain/model/TmdbCatalogMatcher.kt`

  Détail :
  - Ajouter `yearRank: Int` à `Match<T>`.
  - Calculer `yearRank` par candidat retenu (0 = écart exact, 1 = écart ±1, 2 = repli sans année), sans toucher à `isYearCompatible` qui reste la garde d'exclusion.
  - Sélectionner sur la clé composite `(score, yearRank)` : meilleur score d'abord, puis meilleur rang d'année ; à égalité stricte des deux, conserver l'ordre du catalogue.

  Validation :
  Compile ; aucun appelant cassé (`Match` gagne un champ, ses deux usages `GetTrendingInCatalogUseCase`/`GetPopularTop10InCatalogUseCase` ne lisent pas encore `yearRank` à ce stade).

- [x] **Tâche 2 — Tests `TmdbCatalogMatcherTest` de la cause racine**

  Objectif :
  Verrouiller le scénario exact du bug (§7.2) : un candidat daté compatible doit gagner face à un candidat non daté à score égal, quel que soit l'ordre du catalogue.

  Fichiers :
  - `domain/model/TmdbCatalogMatcherTest.kt`

  Détail :
  - Nouveau test : catalogue `[Dune non daté, Dune 2021]` puis `[Dune 2021, Dune non daté]`, entrée TMDB « Dune » (2021) → dans les deux ordres, seul le candidat 2021 est retenu.
  - Nouveau test : écart ±1 préféré à un repli sans année (rang 1 bat rang 2).
  - Nouveau test : aucun candidat daté disponible → le repli sans année reste retenu (non-régression du critère « ne pas supprimer faute de date »).
  - Vérifier que les 7 tests existants passent sans modification, y compris `keepsCatalogOrderForEqualScores` (années identiques, donc même `yearRank`).

  Validation :
  `./gradlew testDebugUnitTest` sur ce fichier — tous verts.

- [x] **Tâche 3 — `CatalogFreshness` intègre `ENRICHMENT`**

  Objectif :
  Fermer la fenêtre de cache figé décrite en §7.3 : un cache d'appariement écrit avant l'enrichissement doit être invalidé une fois l'enrichissement terminé.

  Fichiers :
  - `data/sync/CatalogFreshness.kt`

  Détail :
  `vodSyncedAt()` / `seriesSyncedAt()` deviennent `maxOf(syncedAt(VOD_STREAMS ou SERIES_STREAMS), syncedAt(ENRICHMENT))`, conformément à §8.2. Aucun changement de `CatalogSection.CATALOG_SECTIONS` (complétude hors-ligne non concernée).

  Validation :
  Compile ; `getCachedMatchedTrendsGlobal`/`getCachedMatchedMovies`/`getCachedMatchedSeries` consomment ces méthodes sans changement de signature.

- [x] **Tâche 4 — Tests `CatalogFreshness`**

  Objectif :
  Garantir que l'intégration de `ENRICHMENT` invalide bien la fenêtre de cache visée, sans introduire d'invalidation excessive.

  Fichiers :
  - `data/sync/CatalogFreshnessTest.kt` (nouveau si absent, sinon étendu)

  Détail :
  - `ENRICHMENT` postérieur à `VOD_STREAMS`/`SERIES_STREAMS` → la fraîcheur retenue est celle d'`ENRICHMENT`.
  - `ENRICHMENT` absent (jamais exécuté, `0L`) → n'écrase pas une estampille de section existante.
  - Section illisible (exception DAO) → repli `0L` inchangé (comportement déjà en place, non régressé).

  Validation :
  `./gradlew testDebugUnitTest` — tous verts.

- [x] **Tâche 5 — Traçabilité `yearRank` dans les use cases**

  Objectif :
  Rendre lisible en log la raison d'un appariement (§8.3), réponse à la question ouverte n°4 de l'étape 1.

  Fichiers :
  - `domain/usecase/GetTrendingInCatalogUseCase.kt`
  - `domain/usecase/GetPopularTop10InCatalogUseCase.kt`

  Détail :
  Étendre les lignes `IptvLog.d("TMDB", "🎯 Match ...")` existantes pour inclure `tmdbYear` et `match.yearRank`. Aucune nouvelle dépendance (`IptvLog` déjà importé dans les deux fichiers).

  Validation :
  Compile ; logs visibles en exécutant l'Accueil en debug (vérification manuelle, pas de test dédié — c'est un log, pas une règle métier).

- [x] **Tâche 6 — Tests bout en bout des deux use cases**

  Objectif :
  Non-régression sur un catalogue mixte (candidats datés et non datés), pour un film et une série dans Tendances **et** Top 10 populaires (critère d'acceptation n°5).

  Fichiers :
  - `domain/usecase/GetTrendingInCatalogUseCaseTest.kt`
  - `domain/usecase/GetPopularTop10InCatalogUseCaseTest.kt`

  Détail :
  Reprendre le scénario Dune 1984/2021 (un film, une série) à travers toute la chaîne use case (mocks `VodRepository`/`SeriesRepository`/`TrendingRepository`/`PopularRepository`), vérifier que l'élément résolu (`matchedMovie`/`matchedSeries`) est bien la version compatible.

  Validation :
  `./gradlew testDebugUnitTest` — tous verts, y compris les tests déjà existants de ces deux fichiers.

- [x] **Tâche 7 — Vérification finale**

  Objectif :
  Boucler la non-régression avant passage en `IMPLEMENTATION`.

  Détail :
  `./gradlew assembleDebug` puis `./gradlew testDebugUnitTest` sur l'ensemble du module (pas seulement les fichiers touchés) ; confirmer les 5 critères d'acceptation de la section 6 un par un.

  Validation :
  Build vert, suite complète verte, 5 critères cochés.

---

# 10. Notes de développement

Les 7 tâches ont été implémentées sans écart par rapport au plan de la section 9.

- `TmdbCatalogMatcher.Match` porte désormais `yearRank` (0/1/2) ; sélection sur `(score, yearRank)`. Les 7 tests existants passent sans modification, complétés par 3 nouveaux couvrant la cause racine (ordre du catalogue non déterminant, tolérance ±1 préférée au repli, repli conservé en dernier recours).
- `CatalogFreshness.vodSyncedAt()`/`seriesSyncedAt()` intègrent `maxOf(..., ENRICHMENT)`. Nouveau fichier `CatalogFreshnessTest.kt` (absent avant ce ticket) : 5 cas (enrichissement postérieur, enrichissement absent, enrichissement antérieur, section illisible).
- Logs `IptvLog.d("TMDB", ...)` étendus avec `tmdbYear`/`yearRank` dans `GetTrendingInCatalogUseCase` (mouvement, série) et `GetPopularTop10InCatalogUseCase` (aucun log n'existait avant sur cette ligne, ajouté par cohérence).
- Tests bout en bout ajoutés dans les deux suites de use cases existantes, reproduisant exactement le scénario Dune non enrichi / daté, pour un film et une série.
- `./gradlew assembleDebug` + `./gradlew testDebugUnitTest` : build et suite complète verts.

