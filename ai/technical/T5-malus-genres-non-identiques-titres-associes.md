# T5 - Malus pour les genres non identiques dans les titres associés

## Informations générales

Type:
Technical

Status:
TASK BREAKDOWN

Created:
2026-07-27

---

# 1. Description

Lorsque l'utilisateur consulte la fiche détaillée d'un média (film ou série), l'application affiche une section de "Titres associés" (titres similaires). Actuellement, l'algorithme de sélection et de tri (`RelatedTitlesSelector`) attribue des points aux médias candidats en fonction du nombre de genres en commun avec le média courant, avec un bonus si le candidat appartient à la même catégorie (catégorie IPTV locale).

Cependant, un média candidat qui partage un genre mais possède également de nombreux autres genres non partagés est actuellement classé au même niveau qu'un média qui possède uniquement le genre partagé.
Par exemple, si le média courant a pour unique genre **"Drame"** :
* Le candidat A a pour unique genre **"Drame"**.
* Le candidat B a pour genres **"Drame, Thriller, Action"**.

Actuellement, les deux candidats partagent exactement 1 genre commun ("Drame") et obtiennent donc le même rang de départ. Pourtant, le candidat A est thématiquement plus proche et plus pertinent pour l'utilisateur que le candidat B, qui est plus éloigné en raison de ses genres additionnels ("Thriller" et "Action").

Cette évolution technique vise à introduire un **léger malus pour les genres présents sur le candidat mais non partagés** avec le média courant, afin d'affiner la pertinence du classement des titres associés.

---

# 2. Contexte

Le calcul et le tri des titres associés sont centralisés dans l'objet pur domaine `RelatedTitlesSelector.kt` (dans `com.cstv.app.domain.model`).
Cet objet est utilisé par deux repositories lors de l'affichage des détails :
1. `VodRepositoryImpl.kt` via la méthode `getRelatedMovies(...)`
2. `SeriesRepositoryImpl.kt` via la méthode `getRelatedSeries(...)`

La fonction principale de sélection s'appuie sur la signature suivante :
```kotlin
fun <T> select(
    currentGenres: List<String>,
    currentCategoryId: String?,
    candidates: List<Candidate<T>>,
    limit: Int
): List<T>
```

Actuellement, le rang (rank) d'un candidat est calculé de la manière suivante :
```kotlin
val shared = c.genres.map { it.trim().lowercase() }.toSet().count { it in target }
if (shared >= 1) {
    val sameCat = currentCategoryId != null && c.categoryId == currentCategoryId
    Scored(c.item, shared + if (sameCat) 1 else 0, c.rating, c.added)
} else null
```

Le tri est ensuite effectué par :
1. Le rang décroissant (`rank` qui est un entier).
2. Le score secondaire décroissant (combinaison pondérée de la note de notation et de la date d'ajout).

Comme le rang est un entier (`Int`), toute différence subtile due à des genres non partagés ne peut pas être exprimée de manière continue si l'on conserve un rang de type entier, à moins d'adapter le type du rang en valeur flottante (`Double`) ou d'intégrer ce malus à un autre niveau (par exemple dans le calcul du score secondaire, ou en adaptant le tri).

---

# 3. Spécification fonctionnelle et Objectifs

## Objectifs
* Améliorer la pertinence thématique des titres associés en privilégiant les médias dont le profil de genre est le plus proche possible de celui du média consulté.
* Pénaliser légèrement les candidats qui possèdent des genres superflus par rapport au média d'origine.

## User stories
* En tant qu'utilisateur consultant la fiche d'un film ou d'une série, je veux voir en premier les titres dont les genres correspondent le plus précisément au titre courant, afin de découvrir des contenus réellement pertinents.
* En tant qu'utilisateur, je veux qu'un titre ayant plusieurs genres additionnels reste proposé lorsqu'il partage au moins un genre pertinent, mais qu'il soit moins prioritaire qu'un titre au profil de genres plus proche.

## Parcours utilisateur
1. L'utilisateur ouvre la fiche détaillée d'un film ou d'une série.
2. L'application calcule les titres associés parmi les candidats du même type de média.
3. Les candidats sans genre commun sont exclus, comme aujourd'hui.
4. Parmi les candidats éligibles, l'application favorise ceux qui cumulent le plus de genres communs, puis ceux qui ont le moins de genres additionnels non partagés, tout en conservant l'avantage de la catégorie IPTV identique.
5. La section « Titres associés » affiche la liste ainsi ordonnée, sans nouveau réglage ni indication spécifique dans l'interface.

## Règles métier
1. **Maintien du préfiltre :** Un candidat doit toujours avoir au moins un genre en commun pour être éligible à la liste des titres associés (règle existante `shared >= 1`).
2. **Normalisation :** Les comparaisons de genres ignorent les espaces périphériques et la casse. Les genres dupliqués après normalisation ne sont comptés qu'une fois.
3. **Calcul du malus :** Chaque genre unique présent sur le candidat mais absent du média courant ajoute un malus de `0,1` au rang du candidat.
4. **Plafond du malus :** Le malus cumulé est plafonné à `0,9`. Il ne peut donc jamais annuler entièrement la valeur d'un genre partagé ni faire passer, à catégorie égale, un candidat avec davantage de genres communs derrière un candidat qui en a moins.
5. **Préservation du bonus de catégorie :** La présence dans la même catégorie IPTV conserve son bonus existant de `+1`. Ce bonus est appliqué indépendamment du malus de genres additionnels.
6. **Stabilité hors critère de genre :** À rang de genre et de catégorie identique, les critères secondaires existants (note et date d'ajout) conservent leur ordre et leur comportement actuels.
7. **Portée :** La règle s'applique de façon identique aux titres associés de films et de séries. Elle ne modifie ni les genres affichés, ni le catalogue, ni les résultats de recherche.

## Critères d'acceptation (Exemples de comportement)

Soit un média courant de genre **"Drame"** et appartenant à la catégorie **"Films Populaires"**.
Soient les candidats suivants (tous ayant la même note et date d'ajout pour isoler l'effet des genres) :

| Candidat | Genres | Catégorie | Genres communs | Genres non partagés | Catégorie commune |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **A** | Drame | Films Populaires | 1 ("Drame") | 0 | Oui |
| **B** | Drame | Autre Catégorie | 1 ("Drame") | 0 | Non |
| **C** | Drame, Action | Films Populaires | 1 ("Drame") | 1 ("Action") | Oui |
| **D** | Drame, Action, Thriller | Films Populaires | 1 ("Drame") | 2 ("Action", "Thriller") | Oui |
| **E** | Action, Aventure | Films Populaires | 0 | 2 | Oui |

* **Candidat E** doit être exclu car il n'a aucun genre en commun (0 genre commun).
* Avec le bonus de catégorie existant de `+1` et un malus de `0,1` par genre additionnel, l'ordre final attendu pour les éligibles (A, B, C, D) est :
  1. **A** (1 genre commun, même catégorie, 0 genre non partagé)
  2. **C** (1 genre commun, même catégorie, mais 1 genre non partagé -> pénalisé par rapport à A)
  3. **D** (1 genre commun, même catégorie, mais 2 genres non partagés -> pénalisé par rapport à C)
  4. **B** (1 genre commun, catégorie différente, 0 genre non partagé).

## Cas limites
* Une liste de genres vide ou ne contenant que des valeurs vides côté média courant ne rend aucun candidat éligible.
* Un candidat dont la liste de genres est vide, absente ou ne contient que des valeurs vides est exclu.
* Des variantes de casse ou d'espaces, par exemple `" Drame "` et `"drame"`, représentent le même genre ; elles ne créent ni partage ni malus supplémentaires.
* Les genres additionnels du média courant ne pénalisent jamais le candidat : seul l'ensemble des genres du candidat absent de la cible est concerné.
* Un très grand nombre de genres additionnels sur un candidat applique au maximum le plafond de `0,9` ; le candidat reste éligible tant qu'il partage au moins un genre.

## Gestion des erreurs
* Cette règle est purement locale et ne déclenche aucun appel réseau ni écriture de données.
* Une donnée de genre absente ou mal formée est traitée comme une liste vide après normalisation : l'écran de détail continue de s'afficher et le candidat concerné est simplement exclu s'il ne possède aucun genre commun valide.
* L'absence de titres associés après filtrage reste un résultat normal : la section conserve son comportement actuel lorsqu'aucun candidat n'est éligible.

---

# 4. Spécification technique

## 4.1 Décisions techniques

| # | Décision | Justification |
|---|----------|---------------|
| D1 | **Rang exprimé en dixièmes, sur un `Int`** : `rankTenths = (shared + catBonus) × 10 − malusTenths`, avec `malusTenths = min(9, extras × 1)` | Exprime exactement les valeurs métier `0,1` et `0,9` sans introduire de `Double`. Les comparaisons de rang restent entières donc exactes : deux candidats au profil identique produisent strictement le même rang, sans dépendre de l'arithmétique flottante binaire (`0.1 × 3 = 0.30000000000000004`). Le plafond `9 < 10` garantit **structurellement** la règle métier 4 : un rang appartient toujours à l'intervalle `]k×10 − 10 ; k×10]`, donc aucun candidat ne peut franchir le palier entier inférieur. |
| D2 | **`extras` déduit du même ensemble que `shared`** : `extras = candidateSet.size − shared` | L'ensemble normalisé des genres du candidat est déjà construit pour calculer `shared`. La déduction évite un second parcours et rend impossible une divergence entre les deux compteurs (par ex. normalisations appliquées différemment). Complexité inchangée. |
| D3 | **Normalisation déléguée à `GenreParser.normalize`** au lieu du `trim().lowercase()` inline actuel | Une seule définition de la clé de comparaison dans le domaine ; `GenreParser` est déjà la référence pour `sharedGenreCount` et `matches`. Satisfait la règle métier 2 (casse, espaces, dédoublonnage). |
| D4 | **Filtrage des genres vides** dans l'ensemble cible **et** dans l'ensemble candidat | Requis par les cas limites §3. Corrige un écart du code actuel : `currentGenres = listOf(" ")` produit aujourd'hui une cible `{""}`, et tout candidat portant un genre vide devient éligible avec `shared = 1`. Après filtrage, la cible vide court-circuite et aucun candidat n'est retourné. |
| D5 | **Signature publique de `select(...)` inchangée** | Le malus est une règle de classement interne au sélecteur. `VodRepositoryImpl.getRelatedMovies` et `SeriesRepositoryImpl.getRelatedSeries` ne sont pas touchés, ce qui limite le périmètre à un seul fichier de production et son test. |
| D6 | **Malus appliqué au rang, pas au score secondaire** | Le score secondaire combine note et fraîcheur sur `[0 ; 1]` : y intégrer le malus le rendrait commensurable avec la note et le ferait absorber par un simple écart de notation, en violation des règles métier 4 et 6. |

Alternatives écartées :

* **Rang en `Double`** (`shared + catBonus − 0.1 × extras`) : lisible et direct, mais les égalités de rang deviennent des comparaisons de flottants issus de chemins de calcul différents. Le tri resterait correct en pratique, mais la propriété « même profil ⇒ même rang » cesserait d'être garantie par construction. D1 offre la même sémantique sans ce risque.
* **Tri lexicographique à trois clés** (`shared+cat` décroissant, puis `extras` croissant, puis score) : évite tout arbitrage numérique, mais rend le plafond de `0,9` inexprimable — `extras` deviendrait un critère absolu, alors que la spécification veut un ajustement *borné*, incapable de renverser un genre commun supplémentaire.

## 4.2 Formule de rang

```
rankTenths = (shared + catBonus) * RANK_SCALE - min(MALUS_CAP_TENTHS, extras * MALUS_PER_EXTRA_TENTHS)

RANK_SCALE             = 10   // un genre commun (ou la catégorie) vaut 1,0
MALUS_PER_EXTRA_TENTHS = 1    // 0,1 par genre additionnel du candidat
MALUS_CAP_TENTHS       = 9    // plafond cumulé 0,9
catBonus               = 1 si currentCategoryId != null && candidate.categoryId == currentCategoryId, sinon 0
shared                 = |candidateSet ∩ target|
extras                 = |candidateSet \ target| = candidateSet.size - shared
```

Vérification sur les critères d'acceptation §3 (média courant : genre « Drame », catégorie « Films Populaires ») :

| Candidat | shared | catBonus | extras | malus | `rankTenths` | Rang final |
|---|---|---|---|---|---|---|
| **A** — Drame, même catégorie | 1 | 1 | 0 | 0 | `20 − 0` = **20** | 1er |
| **C** — Drame + Action, même catégorie | 1 | 1 | 1 | 1 | `20 − 1` = **19** | 2e |
| **D** — Drame + Action + Thriller, même catégorie | 1 | 1 | 2 | 2 | `20 − 2` = **18** | 3e |
| **B** — Drame, autre catégorie | 1 | 0 | 0 | 0 | `10 − 0` = **10** | 4e |
| **E** — Action + Aventure | 0 | — | — | — | exclu (`shared < 1`) | — |

Ordre obtenu : **A, C, D, B** — conforme.

Vérification du plafond (règle métier 4) : un candidat à `shared + catBonus = 2` et 40 genres additionnels obtient `20 − 9 = 11`, qui reste strictement supérieur au `10` d'un candidat à `shared + catBonus = 1` sans aucun genre additionnel. Le malus ne peut jamais renverser un palier.

## 4.3 Composants impactés

**Modifié**

| Fichier | Changement |
|---------|-----------|
| `domain/model/RelatedTitlesSelector.kt` | 3 constantes privées ajoutées ; `Scored.rank` devient `rankTenths` (reste `Int`) ; l'ensemble normalisé des genres du candidat est extrait en variable pour dériver `shared` **et** `extras` ; normalisation via `GenreParser.normalize` avec filtrage des valeurs vides ; KDoc de l'objet mis à jour (la formule de rang y est documentée). |

**Test modifié**

| Fichier | Changement |
|---------|-----------|
| `app/src/test/java/.../domain/model/RelatedTitlesSelectorTest.kt` | Cas ajoutés (§5.4). Les 12 tests existants restent valides **sans modification** — vérifié en §5.3. |

**Non impactés** : `VodRepositoryImpl` (l. 408-417), `SeriesRepositoryImpl` (l. 360-369), `GenreParser`, les DAO, les écrans de détail, la navigation. Aucune dépendance Gradle, aucune migration Room, aucune règle ProGuard, aucun changement de schéma ni de cache.

## 4.4 Performances

* Le sélecteur reste un objet pur, appelé une fois par ouverture de fiche, sur le pool déjà préfiltré en SQL par les repositories (`getStreamsByGenre("%$g%")` dédupliqué par identifiant).
* `extras` est obtenu par soustraction (D2) : **aucun** parcours supplémentaire par candidat. La complexité reste `O(n × g)` où `n` = candidats et `g` = genres par candidat, avec en pratique `g` de l'ordre de 1 à 5.
* Le tri, le score secondaire et la troncature `take(limit)` sont inchangés.

## 4.5 Risques techniques

| Risque | Gravité | Mitigation |
|--------|---------|------------|
| Changement d'ordre visible sur des fiches déjà consultées | Faible — c'est l'objet même de T5 | Les titres associés sont recalculés à chaque ouverture de fiche, sans persistance ni cache dédié : aucune donnée à invalider, aucun état incohérent possible. |
| Régression silencieuse du bonus de catégorie ou du départage note/fraîcheur | Moyenne | Les 12 tests existants couvrent ces comportements et restent inchangés (§5.3) ; ils font office de harnais de non-régression. |
| Mauvaise lecture de l'échelle en dixièmes lors d'une évolution ultérieure (ajout d'un critère « valant 1 » écrit `+ 1` au lieu de `+ RANK_SCALE`) | Moyenne | Nommer la constante `RANK_SCALE`, l'utiliser systématiquement, et documenter la formule dans le KDoc de l'objet. Le suffixe `Tenths` sur le champ rend l'unité explicite au point d'usage. |
| Le filtrage des genres vides (D4) modifie l'éligibilité au-delà du strict énoncé du malus | Faible | Comportement explicitement exigé par les cas limites §3 ; couvert par deux tests dédiés (§5.4). À signaler dans les notes de développement comme correction incluse. |

---

# 5. Architecture

## 5.1 Position dans le flux

```
DetailsScreen (film ou série)
  └─ ViewModel de détail
       └─ VodRepositoryImpl.getRelatedMovies / SeriesRepositoryImpl.getRelatedSeries
            ├─ préfiltre SQL : getStreamsByGenre("%genre%") ∪ …, dédoublonné,
            │    média courant et catégories masquées exclus          ← inchangé
            ├─ mapping entité → RelatedTitlesSelector.Candidate       ← inchangé
            └─ RelatedTitlesSelector.select(genres, categoryId, candidates, limit)
                 ├─ target = genres normalisés non vides
                 ├─ par candidat : candidateSet → shared, extras       ← T5
                 ├─ éligibilité : shared >= 1                          ← inchangé
                 ├─ rankTenths = (shared + catBonus) × 10 − malus      ← T5
                 └─ tri : rankTenths ↓, puis score(note, fraîcheur) ↓  ← inchangé
```

T5 ne touche qu'au calcul du rang, à l'intérieur du sélecteur. Les frontières amont (préfiltre SQL, exclusion des catégories masquées, mapping) et aval (troncature, mapping vers modèle domaine) sont inchangées.

## 5.2 Responsabilités

| Composant | Responsabilité | Ne fait pas |
|-----------|----------------|-------------|
| `RelatedTitlesSelector` (domaine, pur) | Éligibilité, rang (genres communs, bonus catégorie, malus plafonné), départage secondaire, limite | Aucun accès Room/réseau ; ne connaît ni les entités ni les catégories masquées |
| `GenreParser` | Découpe des chaînes Xtream et clé de normalisation | Ne classe pas |
| `VodRepositoryImpl` / `SeriesRepositoryImpl` | Préfiltre SQL, exclusion du média courant et des catégories masquées, mapping entité ↔ domaine | Ne calcule aucun rang — inchangés par T5 |

## 5.3 Compatibilité avec les tests existants

Les 12 tests de `RelatedTitlesSelectorTest` ont été rejoués manuellement contre la nouvelle formule ; aucun ne change de résultat, car aucun ne met en scène de candidat porteur d'un genre non partagé :

| Test | Rangs avant | Rangs après | Verdict |
|---|---|---|---|
| `ordersBySharedGenreCountDescending` | 1 / 3 / 2 | 10 / 30 / 20 | ordre `[2, 3, 1]` conservé |
| `excludesCandidatesWithNoSharedGenre` | exclusion sur `shared < 1` | idem | inchangé |
| `matchingIsCaseAndWhitespaceInsensitive` | `" action "` → `action` | idem, via `GenreParser.normalize` | inchangé |
| `respectsLimit` | tous à rang égal | tous à `10` | inchangé |
| `tieBreakPrefersHigherRating` | rangs égaux | `10` / `10` | départage par note conservé |
| `sameCategoryCountsAsOneExtraGenre` | 1 / 2 | 10 / 20 | ordre `[2, 1]` conservé |
| `categoryBonusTiesWithAnExtraCommonGenre` | 2 / 2 | 20 / 20 | égalité conservée, note départage |
| `tieBreakPrefersMoreRecentWhenRatingEqual` | rangs égaux | `10` / `10` | inchangé |
| `moreCommonGenresStillOutranksCategoryBonus` | 3 / 2 | 30 / 20 | ordre `[1, 2]` conservé |
| `emptyCurrentGenres` / `emptyCandidates` / `zeroLimit` | court-circuits | idem | inchangés |

Point d'attention : `categoryBonusTiesWithAnExtraCommonGenre` conserve son égalité parce que le candidat 1 (`Action, Thriller`) a une cible qui contient **les deux** genres, donc `extras = 0`. C'est le test le plus sensible au malus ; il documente précisément la frontière « genre commun supplémentaire ≡ bonus de catégorie ».

## 5.4 Couverture de tests à ajouter

Tous en JVM pur (`RelatedTitlesSelectorTest`), conformément à `AGENTS.md` — aucun device, aucun émulateur.

1. **Ordre nominal des critères d'acceptation** : les 4 candidats A/C/D/B, notes et dates identiques, résultat attendu `[A, C, D, B]` ; le candidat E exclu.
2. **Malus discriminant à profil égal** : deux candidats à 1 genre commun et même catégorie, l'un sans genre additionnel, l'autre avec un — le premier passe devant, alors que la note du second est meilleure (prouve que le malus agit sur le rang, pas sur le score secondaire).
3. **Plafond du malus** : candidat à 2 genres communs et 40 genres additionnels vs candidat à 1 genre commun sans genre additionnel — le premier reste devant (`11 > 10`).
4. **Le malus n'exclut pas** : un candidat lourdement pénalisé reste présent dans la liste tant que `shared >= 1`.
5. **Normalisation sans malus parasite** : candidat `[" Drame "]` face à une cible `["drame"]` → `extras = 0`, rang identique à un candidat `["Drame"]`.
6. **Doublons comptés une fois** : candidat `["Drame", "drame", " DRAME "]` → `shared = 1`, `extras = 0`.
7. **Genres additionnels de la cible sans effet** : cible `["Drame", "Action", "Thriller"]`, candidat `["Drame"]` → `extras = 0`, aucun malus.
8. **Cible composée uniquement de valeurs vides** (`[" ", ""]`) → aucun candidat éligible (D4).
9. **Candidat composé uniquement de valeurs vides** → exclu, même face à une cible non vide (D4).

Validation finale : `./gradlew testDebugUnitTest` (non-régression complète) puis `./gradlew assembleDebug` et `./gradlew lintDebug`, sans désactivation d'un test existant.

---

# 6. Plan de développement

- [ ] Tâche 1 — Écrire les tests de non-régression du classement par genres

Objectif :
Encoder dans les tests unitaires le malus de `0,1`, son plafond à `0,9`, l'ordre A/C/D/B des critères d'acceptation, ainsi que les cas de normalisation, doublons, listes vides et exclusion des candidats sans genre commun.

Fichiers :
- `app/src/test/java/com/cstv/app/domain/model/RelatedTitlesSelectorTest.kt`

Validation :
- Les tests distinguent un candidat au même nombre de genres communs selon son nombre de genres additionnels.
- Les tests vérifient que le bonus de catégorie est conservé et que des genres communs supplémentaires restent prioritaires à catégorie égale malgré le malus plafonné.
- Les tests vérifient que casse, espaces, doublons et genres vides ne modifient pas indûment le classement ni l'éligibilité.

- [ ] Tâche 2 — Appliquer le malus dans le sélecteur de titres associés

Objectif :
Faire évoluer le calcul de rang de `RelatedTitlesSelector` pour retirer `0,1` par genre candidat non partagé, avec un plafond cumulé de `0,9`, tout en conservant le préfiltre, le bonus de catégorie, le départage secondaire et la limite existants.

Fichiers :
- `app/src/main/java/com/cstv/app/domain/model/RelatedTitlesSelector.kt`

Validation :
- Un candidat sans genre commun reste exclu.
- À nombre de genres communs et catégorie identiques, celui ayant moins de genres additionnels passe avant.
- Le malus ne fait pas reculer, à catégorie égale, un candidat ayant un genre commun supplémentaire derrière un candidat qui en a moins.
- Aucun changement n'est requis dans `VodRepositoryImpl` ni `SeriesRepositoryImpl`, qui continuent d'appeler la même signature publique.

- [ ] Tâche 3 — Vérifier la non-régression automatisée ciblée

Objectif :
Exécuter la suite unitaire ciblée puis la non-régression JVM du projet après l'implémentation.

Fichiers :
- `app/src/test/java/com/cstv/app/domain/model/RelatedTitlesSelectorTest.kt`

Validation :
- `./gradlew testDebugUnitTest --tests com.cstv.app.domain.model.RelatedTitlesSelectorTest` réussit.
- `./gradlew testDebugUnitTest` réussit, sans désactivation de test existant.

---

# 7. Hypothèses et questions ouvertes

## Hypothèses
* Les règles fonctionnelles arrêtées sont un malus de `0,1` par genre additionnel et un plafond de `0,9`.
* Le bonus de catégorie existant est conservé sans changement de valeur ni de priorité métier.
* La manière de représenter ce rang et de l'intégrer au tri relève de la spécification technique — **tranchée à l'étape 3** : rang entier exprimé en dixièmes (§4.1 D1), malus appliqué au rang et non au score secondaire (§4.1 D6).

## Questions ouvertes
Aucune question fonctionnelle ouverte. Les choix de représentation du rang, de composants modifiés et de couverture de tests sont définis en §4 et §5.

Points signalés à l'étape 3, hors périmètre de T5 :
* **Malus symétrique** (pénaliser aussi les genres du média courant absents du candidat, façon distance de Jaccard) : explicitement écarté par les cas limites §3, qui posent que les genres additionnels de la cible ne pénalisent jamais le candidat.
* **Valeurs `0,1` / `0,9` non paramétrables** : constantes privées du sélecteur, sans réglage exposé dans l'interface, conformément au parcours utilisateur §3.
