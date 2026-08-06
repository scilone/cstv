# B22 - Saut vertical du sélecteur pivot dans les grilles de catégorie TV

## Informations générales

Status:
RELEASED

Created:
2026-08-06

---

# 1. Description

En mode TV, avec une catégorie sélectionnée (Films, Séries, Chaînes), le cadre du
sélecteur pivot fait un saut vertical à une transition de rangée précise : entre la
première et la deuxième rangée sur Films et Séries, entre la deuxième et la troisième
sur les chaînes. Passée cette transition, le cadre ne bouge plus.

---

# 2. Contexte

Les grilles de catégorie utilisent `Modifier.tvPivotCell`, qui alignait la cellule
focalisée sur le pivot vertical `TV_PIVOT_VERTICAL` (50 % du viewport, centre de la
cellule), comme le fait `tvPivotSection` pour les rangées du mode « Tout ».

Ce pivot est **hors d'atteinte pour les premières rangées** : la grille est déjà en
butée haute, aucun défilement ne peut descendre la rangée 1 jusqu'au centre du
viewport. La convergence s'arrêtait donc sur la position naturelle de ces rangées
(en haut, sous la réserve `TV_PIVOT_VERTICAL_START_RESERVE`), puis atteignait
réellement le centre à la première rangée capable d'y arriver — d'où un saut unique,
à une transition qui dépend de la hauteur de carte et donc de l'écran : rangée 1→2
avec des posters (Films/Séries), rangée 2→3 avec les cartes plus basses des chaînes.

Le correctif v1.73.2 (`isPivotBlocked`, reverté) ne portait que sur le *délai* de
publication de la couche avant du focus (F23) dans ce cas de butée, pas sur le saut
lui-même : le cadre continuait de changer d'ordonnée à la même transition.

---

# 3. Spécification fonctionnelle

### User story

En tant qu'utilisateur Android TV parcourant une catégorie, je veux que le cadre de
sélection reste immobile pendant que la grille défile sous lui, pour suivre mon
déplacement au D-pad sans à-coup visuel.

### Parcours utilisateur

1. L'utilisateur ouvre Films, Séries ou Chaînes et choisit une catégorie précise.
2. Le focus arrive sur la première vignette ; le cadre l'entoure, sous l'en-tête.
3. L'utilisateur descend au D-pad : la grille remonte d'une rangée à chaque appui et
   le cadre reste exactement à la même hauteur, y compris de la rangée 1 à la 2.
4. Il remonte : le mouvement est symétrique, le cadre ne bouge toujours pas.
5. Arrivé à la dernière rangée, celle-ci rejoint la même hauteur que les autres.

### Règles métier

- L'ancre verticale d'une grille est l'emplacement qu'occupe sa première rangée au
  repos, soit l'origine du contenu, sous la réserve haute (T12).
- Le déplacement horizontal dans une rangée ne provoque aucun défilement vertical.
- Le mode « Tout » (rangées horizontales empilées) conserve son pivot 50 % : ce
  ticket ne change que les grilles.

### Critères d'acceptation

- Dans une grille TV, le cadre du sélecteur garde la même ordonnée sur toutes les
  rangées, de la première à la dernière.
- La première rangée reste sous l'en-tête, jamais collée au bord du viewport.
- Aucune animation verticale du cadre n'est perceptible lors d'un déplacement.

### Cas limites et erreurs

- Grille d'une seule rangée : aucun défilement, le cadre est publié immédiatement.
- Reprise du focus sur une position mémorisée (`rememberForeverLazyGridState`) : la
  cellule restaurée rejoint l'ancre comme n'importe quelle autre.
- Grille sans réserve haute : l'ancre se confond avec le bord du viewport.

---

# 4. Spécification technique

## Modification

- `TvPivotScroll.kt` : nouvelle fonction pure `topAnchoredPivotDelta(viewportStartOffset,
  beforeContentPadding, itemOffset)`, distance à défiler pour ramener une cellule sur
  l'ancre haute. L'ancre est calculée `viewportStartOffset + beforeContentPadding`
  plutôt qu'écrite `0` : les deux termes se compensent avec la convention Compose
  (`viewportStartOffset == -beforeContentPadding`, offsets d'items comptés depuis
  l'origine du contenu) et l'expression reste juste sous la convention inverse.
- `convergeCellToVerticalPivot` consomme ce delta au lieu de `focusedChildPivotDelta`.
  Le reste de la boucle est inchangé : passes multiples, `isPivotClamped`, publication
  F23 conditionnée à la stabilisation.
- Nouvelle réserve basse `tvPivotGridEndReserve()` (une hauteur d'écran, au lieu d'un
  demi-viewport) : sans elle, les dernières rangées ne peuvent pas remonter jusqu'à
  l'ancre et le saut réapparaîtrait en fin de grille.
- `tvPivotSection`, `tvPivotItem` et `TV_PIVOT_VERTICAL` sont inchangés.

## Composants impactés

- `presentation/components/TvPivotScroll.kt`
- `presentation/vod/VodScreen.kt`, `presentation/series/SeriesScreen.kt`,
  `presentation/livetv/LiveTvScreen.kt` : grilles de catégorie.
- `presentation/search/SearchScreen.kt` : grille « Voir tout », même classe de saut.

## Risques techniques

- La réserve basse d'une hauteur d'écran ajoute une zone vide en fin de grille. Rien
  n'y est focalisable, donc rien ne l'y fait défiler au-delà du strict nécessaire
  pour amener la dernière rangée sur l'ancre.
- Dépendance à `LazyGridLayoutInfo.beforeContentPadding` (Compose Foundation ≥ 1.3 ;
  BOM 2024.02.02 ici).

## Validation automatisable

Tests JVM sur `topAnchoredPivotDelta` (première rangée, rangée suivante, remontée,
effet de la réserve haute, convention d'offsets alternative, grille sans réserve).
Le rendu lui-même relève du device, donc hors critères (AGENTS.md).

---

# 5. Architecture

Aucun changement d'architecture : correction locale à la couche `presentation`.

---

# 6. Plan de développement

## Liste des tâches

1. Revert de `isPivotBlocked` (v1.73.2) — sans effet sur le saut. **Fait**
2. Ancre haute des grilles + réserve basse adaptée. **Fait**
3. Tests unitaires de la fonction pure. **Fait**
4. `testDebugUnitTest` + `lintDebug` + `assembleDebug`. **Fait**

---

# 7. Notes de développement

Le pivot horizontal des rangées (`TV_PIVOT_HORIZONTAL = 0f`) reposait déjà sur ce
principe : « la carte active reste ancrée sur l'emplacement initial de la première
vignette de la rangée ». L'ancre haute en est la transposition verticale pour les
grilles. Un pivot exprimé en fraction de viewport ne peut pas être tenu par les
rangées de bord, un pivot exprimé comme un emplacement fixe le peut toujours.

---

# 8. Review

## Vérifications automatisées

- `./gradlew testDebugUnitTest lintDebug assembleDebug` : **BUILD SUCCESSFUL**
  (2026-08-06). Les seuls avertissements Kotlin restants sont préexistants
  (`title`, `onSearchQueryChanged` inutilisés).

---

# 9. Release

Version :
v1.73.3

Commit :
🐛 fix(tv): ancre haute du sélecteur pivot dans les grilles de catégorie (B22)

Date :
2026-08-06
