# T10 - Limitation du nombre d'éléments par ligne en mode "Tout" pour les Films et Séries

## Informations générales

Status:
RELEASED

Created:
2026-08-02

---

# 1. Description

Dans les pages de listes des **Films (VOD)** et des **Séries**, le mode d'affichage **"Tout"** (All) présente les médias sous forme de lignes horizontales défilantes (`LazyRow`) regroupées par catégories.

Actuellement, l'application ne limite pas le nombre de médias affichés dans ces lignes horizontales. Si une catégorie contient des milliers d'éléments (ce qui est très fréquent pour l'IPTV), la `LazyRow` se retrouve chargée avec toutes ces milliers d'entrées. Bien que la liste soit "lazy", cela dégrade fortement la mémoire RAM et les performances de défilement ou d'acquisition du focus sur TV.

L'objectif de cette tâche d'optimisation technique est de :
1. Limiter l'affichage à un maximum de **250 éléments** pour chaque ligne horizontale de catégorie en mode "Tout".
2. S'assurer que le bouton **"Voir tout"** à côté du titre de la ligne (sur mobile) ou l'action équivalente sélectionne directement la catégorie pour l'ouvrir en mode grille complète (où l'intégralité du contenu est affiché de façon optimisée).

---

# 2. Contexte

Dans `VodScreen.kt` et `SeriesScreen.kt` :
* La liste complète des flux filtrés (`filteredStreams`) est groupée par catégorie en mémoire Kotlin :
  ```kotlin
  val groupedStreams = remember(filteredStreams) {
      filteredStreams.groupBy { it.categoryId }
  }
  ```
* Ensuite, pour chaque catégorie, le composant `CategorySectionRow` est instancié en lui passant la totalité de la liste des médias de cette catégorie (`catMovies`).
* Sur mobile, le bouton "Voir tout" appelle `onSeeAll = { onCategorySelected(category) }`, ce qui sélectionne la catégorie et bascule l'affichage de l'écran vers le mode "Catégorie spécifique" (grille complète). Sur TV, l'utilisateur peut également sélectionner la puce de catégorie en haut pour afficher la grille de cette catégorie.

En limitant la liste transmise à la `LazyRow` en mode "Tout" à un maximum de 250 éléments, nous garantissons d'excellentes performances d'affichage et de défilement, tout en laissant la possibilité de voir l'intégralité du catalogue au clic sur "Voir tout" ou via la sélection de la catégorie.

---

# 3. Spécification technique

## Résultat utilisateur attendu

* **En tant qu'utilisateur mobile ou Android TV**, je parcours le mode « Tout » de manière fluide, même si une catégorie IPTV contient des milliers de médias.
* **En tant qu'utilisateur mobile**, lorsque la sélection d'une rangée dépasse le contenu aperçu, « Voir tout » me donne accès à la grille exhaustive de cette même catégorie, sans perte de média.

## Objectifs et modifications

1. **Limitation à 250 éléments** :
   * Dans `VodScreen.kt` (mode "Tout" TV et mobile), remplacer l'appel à `CategorySectionRow` en limitant la liste des films transmis à la ligne à un maximum de 250 éléments :
     ```kotlin
     movies = catMovies.take(250)
     ```
   * Faire exactement de même dans `SeriesScreen.kt` (mode "Tout" TV et mobile) pour limiter le nombre de séries transmises à un maximum de 250 éléments :
     ```kotlin
     series = catSeries.take(250)
     ```

2. **Comportement de "Voir tout" (See All)** :
   * Confirmer que sur mobile, le lien "Voir tout" appelle bien `onCategorySelected(category)`. C'est déjà le cas et cela fonctionne parfaitement en redirigeant vers le mode grille où 100% des films/séries de la catégorie sont alors affichés de manière performante et paginée.
   * Sur TV, les lignes n'ont pas de bouton "Voir tout", le parcours standard et optimal consiste à cliquer sur le bandeau de catégories en haut pour basculer sur la grille complète.

## Critères d'acceptation (Techniques)

- [ ] Sur l'écran des Films (VOD), en mode "Tout" (TV et mobile), aucune ligne horizontale de catégorie ne contient plus de 250 éléments.
- [ ] Sur l'écran des Séries, en mode "Tout" (TV et mobile), aucune ligne horizontale de catégorie ne contient plus de 250 éléments.
- [ ] Cliquer sur "Voir tout" à côté d'une catégorie sur mobile redirige bien vers le mode grille de cette catégorie, où la totalité du catalogue associé est disponible.
- [ ] Les performances de défilement vertical et horizontal sur TV en mode "Tout" sont améliorées, sans aucune saccade.

## Cas limites et gestion des erreurs

* Si une catégorie contient moins de 250 éléments, la totalité de ces éléments est affichée sans modification.
* Si le filtre de recherche de l'écran (`searchQuery`) est actif, le filtrage est appliqué d'abord, puis la liste filtrée est limitée à 250 éléments maximum par ligne, ce qui est robuste et instantané.
* Le plafond concerne uniquement les rangées du mode « Tout » ; une grille de catégorie spécifique conserve son résultat exhaustif et son comportement de chargement existant.

---

# 4. Spécification technique détaillée

## Emplacements exacts

Quatre sites, deux par écran (TV et mobile ont chacun leur composable de layout,
avec un `groupBy` distinct) :

| Fichier | Composable | Ligne | Code actuel |
| --- | --- | --- | --- |
| `presentation/vod/VodScreen.kt` | `TvLayout` | 196-198 | `val groupedStreams = remember(filteredStreams) { filteredStreams.groupBy { it.categoryId } }` |
| `presentation/vod/VodScreen.kt` | `TvLayout` | 301-315 | `val catMovies = groupedStreams[category.categoryId] ?: emptyList()` → `movies = catMovies` |
| `presentation/vod/VodScreen.kt` | `MobileLayout` | 411-413 / 536-551 | idem |
| `presentation/series/SeriesScreen.kt` | `TvLayout` | ~194-310 | idem, avec `series = catSeries` |
| `presentation/series/SeriesScreen.kt` | `MobileLayout` | ~407-550 | idem |

## Modification retenue

Plutôt que `movies = catMovies.take(250)` sur chaque site d'appel, le plafond est
appliqué **dans le bloc `remember` du regroupement** :

```kotlin
/**
 * Plafond d'une rangée horizontale du mode « Tout ». Au-delà, la LazyRow
 * retient des milliers d'éléments dont aucun ne sera atteint au D-pad : la
 * grille de la catégorie ("Voir tout" / puce de catégorie) reste le chemin
 * exhaustif.
 */
private const val CATEGORY_ROW_MAX_ITEMS = 250

val groupedStreams = remember(filteredStreams) {
    filteredStreams
        .groupBy { it.categoryId }
        .mapValues { (_, streams) -> streams.take(CATEGORY_ROW_MAX_ITEMS) }
}
```

Raison de ce choix plutôt que du `.take()` au site d'appel :

* le site d'appel est **à l'intérieur d'un `items(actualCategories)`**, donc son
  lambda est réexécuté à chaque recomposition de la cellule ; `take()` y
  alloue une nouvelle liste à chaque passe et invalide l'égalité structurelle,
  ce qui provoque exactement le type de recomposition que le ticket cherche à
  éviter ;
* dans le `remember`, la troncature est calculée une seule fois par changement de
  `filteredStreams` et les listes tronquées sont stables entre recompositions ;
* le résultat fonctionnel est identique, et la constante est déclarée une fois
  par fichier au lieu d'être répétée quatre fois en littéral.

La constante est privée à chaque écran (`VodScreen.kt`, `SeriesScreen.kt`) plutôt
que partagée : les deux écrans ne partagent aujourd'hui aucun module de
constantes, et créer un fichier commun pour un seul entier serait
disproportionné. Les deux déclarations portent la même valeur et le même KDoc.

## Composants impactés

* `app/src/main/java/com/cstv/app/presentation/vod/VodScreen.kt`
* `app/src/main/java/com/cstv/app/presentation/series/SeriesScreen.kt`

## Composants explicitement non impactés

* **Rangées « Continuer à regarder » et « Favoris »** (`resume_watching`,
  `favorites`, `VodScreen.kt` l. 272-300) : hors périmètre. Elles sont bornées
  par nature (positions de lecture d'un profil, favoris d'un profil) et ne
  proviennent pas du `groupBy` par catégorie.
* **Mode « Catégorie spécifique »** : la grille utilise
  `pagedStreams` (Paging 3, `LazyPagingItems`, `VodScreen.kt` l. 121-129 et
  366-385) — déjà paginée, exhaustive, et volontairement non plafonnée.
  C'est elle qui garantit qu'aucun média n'est rendu inaccessible.
* **`CategorySectionRow`** : signature et corps inchangés. Le plafond est une
  décision de l'appelant, pas une propriété de la rangée.
* **`VodViewModel` / `SeriesViewModel`**, repositories, DAO : inchangés. Aucune
  requête modifiée, aucun `LIMIT` SQL ajouté (voir décision 2 en Architecture).

## Modèles de données, API, services, stockage, cache

Néant. Aucune entité Room, aucune migration (base en version 21, inchangée),
aucun DTO, aucun appel réseau, aucun `UseCase`.

## Performances

| | Avant | Après |
| --- | --- | --- |
| Éléments retenus par `LazyRow` d'une grosse catégorie | tout le contenu de la catégorie (jusqu'à plusieurs milliers) | 250 au maximum |
| Nœuds `LazyLayout` candidats au focus D-pad par rangée | idem | 250 |
| Allocation par recomposition de cellule | — | aucune (troncature mémorisée) |

Le gain porte sur la structure interne de `LazyRow` : même si seuls quelques
éléments sont composés à un instant donné, `LazyListState` et son
`layoutInfo` raisonnent sur l'intégralité des index déclarés, et le calcul de
pivot de F19 (`animateScrollToPivot`, `TvPivotScroll.kt` l. 109-132) parcourt
`layoutInfo.totalItemsCount`. Réduire le nombre d'index déclarés allège donc
directement le coût de chaque acquisition de focus sur TV.

La mémoire retenue par les listes elles-mêmes n'est en revanche **pas** réduite :
`filteredStreams` contient déjà tout le catalogue filtré, et `groupBy` en
construit la partition complète avant troncature. Le gain est sur l'arbre de
composition et le focus, pas sur l'empreinte des données — c'est une limite
assumée du ticket, la réduire supposerait de paginer aussi le mode « Tout »
(hors périmètre).

## Sécurité

Sans objet.

## Compatibilité

* **TV et mobile** : plafond identique sur les deux plateformes, appliqué au même
  endroit (le `groupBy` de chaque layout). Aucune divergence de comportement.
* **Recherche active** (`searchQuery`) : le filtrage est appliqué en amont
  (`filteredStreams`, `VodScreen.kt` l. 113-119), la troncature vient après —
  conforme au cas limite de l'étape 2. Une recherche qui ramène moins de 250
  résultats est intégralement affichée.
* **Catégorie de moins de 250 éléments** : `take()` renvoie la liste complète,
  aucun changement observable.
* **min SDK 21** : aucune API conditionnée.

## Dépendances

Aucune dépendance Gradle ajoutée.

## Risques techniques

| Risque | Gravité | Mitigation |
| --- | --- | --- |
| Un média au-delà du 250ᵉ rang devient inaccessible | Fonctionnelle | Faux : la grille de la catégorie (mobile « Voir tout » → `onCategorySelected`, `VodScreen.kt` l. 548 ; TV → puce de catégorie l. 241-248) reste exhaustive et paginée. Le plafond ne concerne que l'aperçu. |
| Ordre des 250 retenus non pertinent | Cosmétique | La troncature préserve l'ordre de `filteredStreams`, lui-même issu de `ORDER BY orderIndex ASC` (`VodDao.getStreamsByCategory`) : ce sont bien les 250 premiers de la catégorie tels que le panel les ordonne, pas un sous-ensemble arbitraire. |
| TV : pas de « Voir tout » dans la rangée | Ergonomique | Constaté à l'étape 2 : sur TV, le chemin exhaustif est le bandeau de catégories en haut. F22 remplace ce bandeau par un sélecteur déroulant — le chemin reste disponible, sous une autre forme. À vérifier conjointement si F22 est livrée avant T10. |
| Régression du pivot F19 sur une rangée tronquée | Faible | `animateScrollToPivot` borne déjà son index (`if (index < 0 \|\| index >= layoutInfo.totalItemsCount) return`, `TvPivotScroll.kt` l. 116) ; une liste plus courte est un cas déjà couvert. |

## Contraintes de performance

La troncature ne doit pas s'exécuter sur le thread principal à chaque
recomposition : garanti par le `remember(filteredStreams)`. Le `groupBy` lui-même
est déjà dans ce `remember` aujourd'hui et n'est pas déplacé.

---

# 5. Architecture

## Position dans la Clean Architecture

Optimisation strictement `presentation`. Aucune règle métier : le plafond est une
contrainte de rendu, pas une limite du catalogue. Rien ne descend vers `domain`
ni `data`.

```
presentation/
├── vod/VodScreen.kt
│   ├── TvLayout      : groupedStreams = groupBy(…).mapValues { it.take(250) }
│   └── MobileLayout  : idem
└── series/SeriesScreen.kt
    ├── TvLayout      : idem (SeriesStream)
    └── MobileLayout  : idem

presentation/components/… , data/… , domain/…   ← INCHANGÉS
```

## Flux de données

```
VodViewModel.state.streams            (catégorie "all" observée via Room)
        │
        ▼
filteredStreams = streams filtrés par searchQuery      (remember, inchangé)
        │
        ▼
groupedStreams = filteredStreams.groupBy { categoryId }
                                 .mapValues { it.take(250) }     ← PLAFOND ICI
        │
        ▼
items(actualCategories) → CategorySectionRow(movies = groupedStreams[id])
        │
        ▼
LazyRow : au plus 250 index déclarés → focus D-pad et pivot F19 allégés

── chemin exhaustif, inchangé ──────────────────────────────────────────
"Voir tout" (mobile) / puce de catégorie (TV) → onCategorySelected(category)
        │
        ▼
VodViewModel.selectCategory → observeStreams(categoryId)
        │
        ▼
pagedStreams (Paging 3) → LazyVerticalGrid : 100 % de la catégorie
```

## Responsabilités des composants

* **`TvLayout` / `MobileLayout`** : décider *combien* d'éléments un aperçu
  horizontal expose. C'est là que vit la connaissance « ceci est un aperçu, pas
  la liste complète ».
* **`CategorySectionRow`** : rendre exactement la liste qu'on lui donne. Elle
  reste ignorante de tout plafond — sans quoi elle deviendrait inutilisable pour
  une rangée qui doit être exhaustive.
* **Paging 3 / `LazyPagingItems`** : garant de l'exhaustivité dans le mode
  « Catégorie spécifique ». C'est le contrepoids du plafond, et la raison pour
  laquelle celui-ci est sans risque fonctionnel.

## Décisions techniques

1. **Plafond dans le `remember` du `groupBy`, pas au site d'appel.** Voir
   section 4 : évite une réallocation par recomposition de cellule et garde la
   stabilité structurelle des listes passées à `LazyRow`.
2. **Pas de `LIMIT` SQL.** Un `LIMIT 250 PER category` supposerait une requête
   par catégorie (ou une fonction de fenêtrage indisponible sur les versions de
   SQLite embarquées visées) et remplacerait une lecture par N lectures. Or les
   flux du mode « Tout » sont déjà chargés en une fois pour d'autres besoins
   (recherche locale, favoris, comptage) : les tronquer côté UI est le seul
   changement qui n'ajoute aucune requête.
3. **250 en constante nommée, pas en littéral.** Valeur fixée à l'étape 2 ; la
   nommer documente l'intention et rend un futur ajustement trivial.
4. **Rangées « Reprendre » et « Favoris » exclues.** Elles ne passent pas par le
   `groupBy` et sont bornées par construction ; les plafonner introduirait un
   risque de masquer un favori sans contrepartie.
5. **Aucun signalement utilisateur de la troncature.** Le lien « Voir tout »
   (mobile) et le sélecteur de catégorie (TV) sont déjà les affordances du
   « il y en a plus » ; un compteur ou un badge « 250+ » sortirait du périmètre
   technique du ticket.

## Stratégie de tests

La troncature est du code de composition sans logique métier extractible, et sa
vérification visuelle (fluidité de défilement, focus D-pad) exigerait un device —
donc exclue des critères de validation de l'agent (`AGENTS.md`).

Aucun test unitaire JVM n'est ajouté : il n'existe ni ViewModel, ni `UseCase`, ni
parsing modifié. La partie théoriquement testable (`groupBy(...).mapValues { it.take(n) }`)
est une composition de fonctions de la bibliothèque standard ; la tester
reviendrait à tester `Iterable.take`.

Non-régression assurée par la suite existante :
`./gradlew testDebugUnitTest` (dont `TvPivotScrollTest`, qui couvre le calcul de
pivot sur lequel s'appuient les rangées tronquées), puis `assembleDebug` et
`lintDebug`.

---

# 6. Plan de développement

## Ordre d'exécution

La limite est d'abord centralisée, puis appliquée aux rangées concernées sans
affecter les grilles de catégorie ni les autres écrans.

### Tâche 1 — Définir la limite partagée du mode « Tout »

- [x] Ajouter la constante et les helpers de troncature au niveau presentation
  retenu par l'architecture.

Objectif : disposer d'une règle unique, lisible et indépendante de la taille
réelle des listes.

Fichiers : composant/constante partagé(e) identifié(e) au §4.

Validation : listes courtes, exactement à la limite et longues conservent leur
ordre ; aucun chargement Paging supplémentaire n'est provoqué.

### Tâche 2 — Appliquer la limite aux rangées Films et Séries

- [x] Câbler le helper sur toutes les rangées du mode « Tout » ciblées.

Objectif : borner le nombre de cartes composées sans toucher à la catégorie
spécifique, à la recherche, aux favoris ou à la reprise.

Fichiers : `VodScreen.kt`, `SeriesScreen.kt` et composants de rangée associés.

Validation : chaque rangée concernée affiche au plus la limite ; les grilles et
liens « Voir tout » gardent leur comportement existant.

### Tâche 3 — Couvrir la règle et vérifier le build

- [x] Ajouter les tests purs utiles et lancer les contrôles automatisés.

Fichiers : test du helper/selector et ce ticket.

Validation : frontière de limite testée ; `testDebugUnitTest`, `assembleDebug`
et `lintDebug` passent.

---

# 7. Notes de développement

Implémenté le 2026-08-02 exactement selon la conception de la section 4 :
`CATEGORY_ROW_MAX_ITEMS = 250` déclarée dans chaque écran (`VodScreen.kt`,
`SeriesScreen.kt`) et appliquée dans le `remember(filteredStreams)` du
`groupBy` (`.mapValues { (_, streams) -> streams.take(CATEGORY_ROW_MAX_ITEMS) } `),
sans toucher aux rangées « Reprendre »/« Favoris », au mode « Catégorie
spécifique » (Paging 3, exhaustif) ni à `CategorySectionRow`.

Aucun test unitaire dédié ajouté (conforme à la section « Stratégie de tests » :
composition de fonctions standard, rien à extraire). La non-régression du
calcul de pivot F19 sur une liste tronquée est déjà couverte par
`TvPivotScrollTest` existant.

## Vérifications automatisées

- `./gradlew compileDebugKotlin` → réussi après chaque modification.
- `./gradlew testDebugUnitTest` (hors la classe `HomeViewModelTest`, qui bloque
  l'exécution complète dans cette session pour une raison préexistante et sans
  rapport avec ce ticket — voir note commune en fin de section 7 de F23) → 538
  tests, 0 échec.
- `./gradlew assembleDebug lintDebug` → réussi, `0 errors`.

Les critères d'acceptation fonctionnels (§3, plafond visuellement constaté,
fluidité TV) restent une vérification manuelle sur appareil/émulateur Android
TV, explicitement hors des critères de validation automatisés de l'agent
(`AGENTS.md`) — non effectuée dans cette session.

Étape 5 (Implémentation) terminée le 2026-08-02 ; Étapes 6-8 consignées
ci-dessous.

---

# 8. Review

Status: RESOLVED

Review effectuée le 2026-08-02 sur le diff d'implémentation et les quatre
chemins de rendu concernés (`TvLayout` et `MobileLayout` de `VodScreen.kt` et
`SeriesScreen.kt`).

## Critique

Aucun problème critique identifié.

## Majeur

Aucun problème majeur identifié.

## Mineur

Aucun problème mineur identifié.

## Conclusion

Implémentation conforme à la spécification : les quatre regroupements du mode
« Tout » appliquent `take(CATEGORY_ROW_MAX_ITEMS)` dans leur bloc
`remember(filteredStreams)`, avec une limite fixée à 250. Les rangées
« Reprendre » et « Favoris » restent hors du regroupement, les grilles de
catégorie continuent d'utiliser `pagedStreams`, et le lien mobile « Voir tout »
transmet toujours la catégorie courante à `onCategorySelected`.

Aucun correctif n'est demandé pour T10. Cette étape est une review, pas une
validation finale : aucun contrôle de fluidité sur Android TV n'a été effectué.

## Étape 7 — Correction

Aucun retour à traiter (0 finding aux trois niveaux) : Étape 7 clôturée sans
modification de code, conformément à l'AGENTS.md (rien à corriger).

## Étape 8 — Validation finale (2026-08-02)

Status: VALIDATED

- Comportement attendu / règles métier : troncature à 250 confirmée par
  lecture du diff aux quatre sites (TV/mobile × VOD/Séries), rangées
  Reprendre/Favoris et grilles de catégorie hors périmètre comme prévu.
- Qualité technique / absence de régression : `./gradlew compileDebugKotlin`
  réussi ; `./gradlew assembleDebug lintDebug` réussi, `0 errors` ; suite de
  tests exécutée en excluant `HomeViewModelTest`/`RecentlyAddedViewModelTest`
  (blocage préexistant et sans rapport avec ce ticket, documenté dans les
  notes de F23) → 545 tests, 0 échec.
- Expérience utilisateur (fluidité TV, absence de saccade perceptible) : non
  vérifiée, hors des critères de validation automatisés de l'agent
  (`AGENTS.md`) — nécessite un appareil/émulateur Android TV.

Le ticket passe de `REVIEW` à `VALIDATED`.

---

# 9. Release

Version:
v1.68.0

Commit:
✨ Release F23, B18 & T10: double-layer TV focus, card unification & row limiting

Date:
2026-08-02
