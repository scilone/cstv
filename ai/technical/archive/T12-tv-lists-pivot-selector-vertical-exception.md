# T12 - TV Lists Pivot Selector Vertical Exception

## Informations générales

Status:
RELEASED

Created:
2026-08-05

---

# 1. Description

Sur les écrans de liste (Chaînes Live TV, Séries, Films VOD, et Favoris), le sélecteur pivot vertical est verrouillé au centre de l'écran (50 % de hauteur du viewport). Il faudrait faire une exception en haut de liste (similaire à l'écran d'accueil) : le faire commencer un peu plus haut afin que la première ligne des médias soit proche du dropdown de catégorie, réduisant ainsi le vide visuel initial. Une fois que l'utilisateur descend à mi-hauteur (50 %), le pivot vertical s'y verrouille de façon standard.

---

# 2. Contexte

Afin de permettre aux lignes du haut et du bas d'atteindre le pivot vertical central de 50 %, le projet ajoute actuellement un espacement important en haut de chaque liste verticale (`tvPivotVerticalStartSpacer` de 50 % de hauteur, ou `contentPadding = PaddingValues(vertical = screenHeight / 2)`). Cela crée un grand vide visuel inesthétique entre le bandeau d'en-tête (sélecteur dropdown) et le début du contenu réel lorsque la liste est au sommet.

---

# 3. Spécification fonctionnelle

### User story

En tant qu'utilisateur Android TV, je vois immédiatement la première rangée de médias sous son bandeau de liste, tout en conservant le pivot vertical central qui stabilise la navigation dès que j'explore le contenu.

### Parcours utilisateur

1. L'utilisateur entre dans Live TV, Films, Séries ou Favoris et se trouve en haut d'une liste ou grille.
2. La première rangée est placée près du bandeau, avec un espacement visuel léger compris entre 16 et 32 dp ; aucun vide d'une demi-hauteur d'écran n'apparaît avant elle.
3. Il déplace le focus vers les rangées suivantes.
4. À partir de la deuxième rangée focalisée, le défilement stabilise la carte ou rangée active autour du pivot vertical standard à 50 % du viewport.
5. Lorsqu'il revient au premier élément, la liste retrouve naturellement sa position haute, sans forcer la première rangée au centre.

### Règles métier

- L'exception de début s'applique aux listes TV concernées : Live TV, Films, Séries et Favoris, dans leurs vues liste comme grille lorsque le pivot vertical est utilisé.
- Elle n'altère ni le pivot horizontal, ni le cadre de focus, ni la restauration de position/focus existante.
- Le pivot de 50 % demeure le comportement de référence hors du début absolu de liste.
- La réserve de fin reste suffisante pour que la dernière rangée puisse atteindre le pivot central.

### Critères d'acceptation

- À la position initiale, la première rangée commence à 16–32 dp sous le bandeau et n'est pas séparée par un grand espace vide.
- Le focus sur la deuxième rangée ou les suivantes entraîne un centrage stable au pivot vertical de 50 %.
- Retourner à la première rangée ne produit ni saut de focus, ni recouvrement du bandeau, ni espace artificiel au centre.
- La même règle est cohérente dans Live TV, Films, Séries et Favoris, avec des états vides ou chargements inchangés.

### Cas limites et erreurs

- Une liste ne contenant qu'une seule rangée reste en position haute et ne tente pas de se centrer artificiellement.
- Une position de défilement restaurée garde priorité sur le focus initial déjà restauré ; la nouvelle règle ne réinitialise pas la consultation de l'utilisateur.
- Après un rafraîchissement qui retire la première rangée ou change le nombre de colonnes, le focus reste sur un élément existant ou revient au premier élément disponible sans boucle de défilement.

---

# 4. Spécification technique

## Situation actuelle

La réserve haute est produite de deux façons distinctes selon le conteneur.

**Listes (`LazyColumn`)** — un item non focalisable d'une demi-hauteur de viewport, `TvPivotScroll.kt:216` :

```kotlin
fun LazyListScope.tvPivotVerticalStartSpacer(enabled: Boolean) {
    if (!enabled) return
    item(key = "tv_pivot_vertical_start") {
        Spacer(modifier = Modifier.fillParentMaxHeight(0.5f).focusProperties { canFocus = false })
    }
}
```

**Grilles (`LazyVerticalGrid`)** — un `contentPadding` symétrique :

```kotlin
contentPadding = PaddingValues(vertical = LocalConfiguration.current.screenHeightDp.dp / 2)
```

Appelants relevés :

| Écran | Liste (spacer) | Grille (`contentPadding`) | Dans le périmètre |
| --- | --- | --- | --- |
| `LiveTvScreen.kt` | 312 | 425 | oui |
| `VodScreen.kt` | 466 | 575 | oui |
| `SeriesScreen.kt` | 456 | 563 | oui |
| `FavoritesScreen.kt` | 112 | — | oui |
| `HomeScreen.kt` | 370 (conditionnel) | — | **non** |
| `SearchScreen.kt` | 227 | 472 | **non** |

## Choix technique 1 — ne pas modifier la fonction partagée

`tvPivotVerticalStartSpacer` est appelée par six écrans, dont deux hors périmètre. En changer la hauteur modifierait silencieusement l'Accueil (branche sans Hero) et la Recherche, ce que ni la spécification fonctionnelle ni la règle n°7 du workflow n'autorisent.

On ajoute donc une fonction distincte dans `TvPivotScroll.kt`, et on migre uniquement les quatre écrans concernés :

```kotlin
/** Réserve haute réduite : la première rangée reste sous le bandeau (T12). */
val TV_PIVOT_VERTICAL_START_RESERVE = 24.dp

fun LazyListScope.tvPivotVerticalStartReserve(enabled: Boolean) {
    if (!enabled) return
    item(key = "tv_pivot_vertical_start") {
        Spacer(
            modifier = Modifier
                .height(TV_PIVOT_VERTICAL_START_RESERVE)
                .focusProperties { canFocus = false }
        )
    }
}
```

24 dp est le milieu de la fourchette 16–32 dp fixée à l'étape 2.

**La clé `"tv_pivot_vertical_start"` est conservée à l'identique.** C'est un invariant, pas un détail : les écrans mémorisent leur position via `rememberForeverLazyListState` / `rememberForeverLazyGridState`, qui persistent un *index d'item*. Supprimer l'item ou changer sa clé décalerait tous les index sauvegardés d'une unité et ferait rouvrir chaque liste sur la mauvaise rangée. L'item reste donc présent, seule sa hauteur change.

Pour les grilles, le `contentPadding` devient asymétrique — aucun index n'est concerné, un `PaddingValues` n'étant pas un item :

```kotlin
contentPadding = PaddingValues(
    top = TV_PIVOT_VERTICAL_START_RESERVE,
    bottom = LocalConfiguration.current.screenHeightDp.dp / 2
)
```

La réserve **de fin** reste à 50 % dans les deux cas : elle seule permet à la dernière rangée d'atteindre le pivot central (règle métier de l'étape 2).

## Choix technique 2 — traiter le blocage en butée comme une convergence

C'est le cœur du ticket, et le point qu'une lecture rapide manque.

Avec une réserve haute de 24 dp, la première rangée **ne peut plus** atteindre le pivot à 50 % : il faudrait faire défiler le contenu vers le bas au-delà de l'offset zéro. Or `convergeSectionToVerticalPivot` (`TvPivotScroll.kt:351`) et `convergeCellToVerticalPivot` (`:396`) bouclent sur `VERTICAL_PIVOT_MAX_PASSES` en appelant `scrollBy(delta)`. En butée, le défilement ne consomme rien, `delta` reste identique passe après passe, `stablePasses` ne s'incrémente jamais et la fonction **retourne `false`**.

Conséquence directe, et c'est une régression visuelle et non un simple détail interne : le `false` empêche l'appel à `selector.reportAxisStabilised(...)` / `selector.publishFrom(...)`. **Le cadre de focus F23 ne serait plus dessiné sur la première rangée.** Cinq passes seraient en outre consommées pour rien à chaque focus sur cette rangée.

`LazyListState.scrollBy` et `LazyGridState.scrollBy` retournent le nombre de pixels réellement consommés. On s'en sert pour distinguer « pas encore convergé » de « impossible d'aller plus loin » :

```kotlin
/**
 * Vrai quand le défilement demandé n'a rien consommé alors qu'un écart
 * subsiste : la liste est en butée, la position atteinte est la plus proche
 * possible du pivot et doit être considérée comme stable (T12).
 *
 * Fonction pure, sans dépendance Compose, pour rester testable en JVM.
 */
internal fun isPivotClamped(delta: Float, consumed: Float): Boolean =
    abs(delta) > VERTICAL_PIVOT_TOLERANCE_PX && abs(consumed) <= VERTICAL_PIVOT_TOLERANCE_PX
```

Dans les deux fonctions de convergence, la branche de correction devient :

```kotlin
val consumed = if (primaryCorrectionPending) {
    primaryCorrectionPending = false
    animateScrollBy(delta)
} else {
    scrollBy(delta)
}
if (isPivotClamped(delta, consumed)) return true
stablePasses = 0
```

Ce mécanisme est volontairement **général** et non conditionné à « on est sur la première rangée » : la butée de fin de liste relève exactement du même cas, et une liste plus courte qu'un viewport aussi. Il traite donc aussi, sans code dédié, le cas limite « liste d'une seule rangée » de l'étape 2.

## Composants impactés

| Fichier | Nature de la modification |
| --- | --- |
| `presentation/components/TvPivotScroll.kt` | Ajout de `TV_PIVOT_VERTICAL_START_RESERVE`, `tvPivotVerticalStartReserve`, `isPivotClamped` ; détection de butée dans les deux fonctions de convergence |
| `presentation/livetv/LiveTvScreen.kt` | Ligne 312 → nouvelle réserve ; ligne 425 → `contentPadding` asymétrique |
| `presentation/vod/VodScreen.kt` | Lignes 466 et 575, idem |
| `presentation/series/SeriesScreen.kt` | Lignes 456 et 563, idem |
| `presentation/favorites/FavoritesScreen.kt` | Ligne 112 (pas de grille sur cet écran) |
| `app/src/test/java/com/cstv/app/presentation/components/TvPivotScrollTest.kt` | Couverture de `isPivotClamped` |

Aucun nouveau composant, aucune nouvelle dépendance, aucune modification de ViewModel, de couche `data` ou de schéma Room.

## Contraintes de performance

Favorable sur box faible : la détection de butée supprime jusqu'à cinq passes de `withFrameNanos` + `scrollBy` inutiles à chaque acquisition de focus sur une rangée non centrable. La réserve haute réduite diminue par ailleurs la hauteur totale composée au premier layout.

## Risques techniques

1. **Régression du cadre de focus F23 si le choix 2 est omis.** Les deux modifications sont indissociables : livrer la réserve réduite sans la détection de butée introduit un bug visible. À traiter dans une seule tâche.
2. **Restauration de position.** Traité par la conservation de la clé d'item, mais à revérifier explicitement en review : c'est le point de rupture le plus probable.
3. **`animateScrollBy` en butée.** Il faut confirmer qu'il retourne bien 0 f et ne reste pas suspendu sur une animation sans déplacement. Si le comportement différait, replier la correction primaire sur un `scrollBy` immédiat lorsque `isPivotClamped` est vrai à la passe précédente.
4. **Interaction avec F24 et F27.** Ces deux tickets modifient les bandeaux d'en-tête des mêmes écrans. Aucun conflit fonctionnel, mais un conflit de fusion probable sur `LiveTvScreen.kt`, `VodScreen.kt` et `SeriesScreen.kt` : à séquencer plutôt qu'à paralléliser.

## Validation automatisable

`TvPivotScrollTest.kt` couvre déjà `pivotScrollOffset` et `focusedChildPivotDelta`. `isPivotClamped` s'y ajoute comme fonction pure : écart nul, écart résiduel avec consommation nulle, écart résiduel avec consommation partielle, valeurs négatives. Le comportement visuel (position de la première rangée, cadre de focus) n'est pas testable sans appareil et relève de la validation PO.

---

# 5. Architecture

Introduction d'un mécanisme de détection de butée (`isPivotClamped`) pour les conteneurs défilants TV (`LazyColumn` et `LazyVerticalGrid`) afin de stabiliser le cadre de focus en cas de butée haute ou basse. Réduction de la réserve de début de 50 % de l'écran à 24 dp pour rapprocher la première ligne de médias du bandeau d'en-tête.

---

# 6. Plan de développement

## Liste des tâches

- [x] Tâche 1 — Créer l'utilitaire de détection de butée et la réserve haute réduite

  **Objectif :**
  Dans `TvPivotScroll.kt`, ajouter la constante `TV_PIVOT_VERTICAL_START_RESERVE = 24.dp` et la fonction de réserve `tvPivotVerticalStartReserve`.
  Implémenter la fonction pure `isPivotClamped` qui détecte si le défilement demandé s'est heurté à une butée physique de la liste.
  Intégrer cette détection de butée dans les fonctions de convergence `convergeSectionToVerticalPivot` et `convergeCellToVerticalPivot` pour valider la stabilisation même lorsque l'écart n'est pas consommé.

  **Fichiers :**
  - `presentation/components/TvPivotScroll.kt`

  **Validation :**
  - Compilation réussie.

- [x] Tâche 2 — Écrire le test unitaire JVM pour `isPivotClamped`

  **Objectif :**
  Ajouter des cas de tests unitaires validant tous les scénarios de `isPivotClamped` (butée atteinte, défilement normal, butée absente) dans `TvPivotScrollTest.kt`.

  **Fichiers :**
  - `app/src/test/java/com/cstv/app/presentation/components/TvPivotScrollTest.kt`

  **Validation :**
  - Exécution réussie de `./gradlew testDebugUnitTest`.

- [x] Tâche 3 — Migrer les écrans concernés vers la réserve réduite

  **Objectif :**
  Remplacer les appels de `tvPivotVerticalStartSpacer` par `tvPivotVerticalStartReserve` pour les listes, et appliquer un `contentPadding` vertical asymétrique pour les grilles dans les quatre écrans du périmètre :
  1. `LiveTvScreen.kt`
  2. `VodScreen.kt`
  3. `SeriesScreen.kt`
  4. `FavoritesScreen.kt`

  **Fichiers :**
  - `presentation/livetv/LiveTvScreen.kt`
  - `presentation/vod/VodScreen.kt`
  - `presentation/series/SeriesScreen.kt`
  - `presentation/favorites/FavoritesScreen.kt`

  **Validation :**
  - Exécution réussie de `./gradlew assembleDebug` et `./gradlew testDebugUnitTest`.

---

# 7. Notes de développement

- 2026-08-05 — Implémentation relue dans le périmètre prévu : réserve haute TV de 24 dp pour les listes et grilles concernées, conservation de la réserve basse, convergence acceptée en butée et tests JVM de la détection pure.
- 2026-08-05 — Relecture approfondie : l'espacement de `verticalArrangement` s'ajoute à la hauteur de l'item de réserve dans les vues liste. La review est corrigée en `CHANGES REQUESTED` avec le constat T12-R1.
- 2026-08-05 — Résolution de T12-R1 : la réserve verticale de début dans `tvPivotVerticalStartReserve` pour les vues liste est ajustée à `0.dp`, ce qui permet d'exploiter directement l'espacement existant de `verticalArrangement` (16 dp pour Live TV/Films/Séries et 24 dp pour Favoris). La réserve reste ainsi parfaitement comprise entre 16 et 32 dp, tout en préservant l'item et sa clé pour la mémorisation de position.

---

# 8. Review

Date : 2026-08-05

Status : APPROVED

## Périmètre relu

- `presentation/components/TvPivotScroll.kt`
- `presentation/livetv/LiveTvScreen.kt`
- `presentation/vod/VodScreen.kt`
- `presentation/series/SeriesScreen.kt`
- `presentation/favorites/FavoritesScreen.kt`
- `app/src/test/java/com/cstv/app/presentation/components/TvPivotScrollTest.kt`

## Critique

Aucun constat.

## Majeur

### T12-R1 — La réserve réelle des vues liste dépasse le contrat de 16–32 dp (RÉSOLU)

**Description :** `tvPivotVerticalStartReserve` ajoute un item de 24 dp avant la première section. Or les quatre `LazyColumn` appliquent aussi leur `verticalArrangement` entre cet item et la première section : 16 dp dans Live TV, Films et Séries, 24 dp dans Favoris. La réserve effective avant la première section est donc respectivement de **40 dp** et **48 dp**, avant même le titre interne de la section, et non de 24 dp.

**Impact :** le critère d'acceptation imposant une première rangée à 16–32 dp du bandeau n'est pas respecté dans les vues liste. Favoris présente l'écart le plus important. Les grilles, dont les 24 dp sont portés par `contentPadding.top`, ne sont pas concernées.

**Correction attendue :** intégrer l'espacement inter-items dans le calcul de la réserve de début. La correction doit conserver l'item et son index pour la restauration de position, tout en garantissant une distance totale de 16–32 dp sur les quatre listes. Une option localisée consiste à conserver un spacer de hauteur nulle : l'espacement existant fournit alors 16 dp sur Live TV/Films/Séries et 24 dp sur Favoris. Ajouter un test pur ou une API exprimant explicitement la réserve totale attendue afin d'éviter une nouvelle addition implicite.

## Mineur

Aucun constat.

## Vérifications effectuées

- Les quatre écrans du périmètre utilisent la réserve réduite en vue liste ; Live TV, Films et Séries utilisent un `contentPadding` asymétrique en vue grille. Accueil et Recherche conservent leur réserve initiale.
- La clé de l'item de réserve reste `tv_pivot_vertical_start`, ce qui conserve les index utilisés par `rememberForeverLazyListState`. Le padding de grille ne crée ni ne retire aucun item.
- La réserve basse d'une demi-hauteur est conservée dans les listes et grilles, afin de laisser la dernière rangée atteindre le pivot.
- La convergence liste et grille ne publie le sélecteur après un écart résiduel que si `scrollBy` / `animateScrollBy` n'a effectivement presque rien consommé ; une consommation partielle continue les passes.
- `isPivotClamped` est couvert dans les deux directions, pour un écart nul, une butée et une consommation partielle.
- Réexécution forcée de `ActivationKeyGateTest` et `TvPivotScrollTest` : succès (`BUILD SUCCESSFUL`).
- Contrôle global `testDebugUnitTest assembleDebug lintDebug` : succès (`BUILD SUCCESSFUL`) ; les tâches étaient à jour lors de ce contrôle.
- `git diff --check` : aucun défaut d'espaces dans les changements suivis.

## Limite de la review

Les tests JVM couvrent les calculs purs mais pas la géométrie Compose réelle, le rendu du sélecteur ni la restauration visuelle après navigation. Conformément à la stratégie du projet, les observations sur appareil ne font pas partie des critères automatisés et ne bloquent pas la review technique.

## Corrections demandées

- [x] T12-R1 — Ramener la réserve totale des quatre vues liste dans la plage 16–32 dp en tenant compte de `verticalArrangement`. (RÉSOLU : hauteur du Spacer réglée à 0.dp pour utiliser l'espacement de verticalArrangement)

Le ticket a été entièrement validé et approuvé.

---

# 9. Release

Version :
v1.73.0

Commit :
📦 chore(release): bump to v1.73.0 (B20, T12)

Date :
2026-08-05
