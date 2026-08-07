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
- Le mode « Tout » (rangées horizontales empilées) partage la même ancre depuis
  la v1.73.6, appliquée au bloc titre + vignettes (voir § 9).

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
- `tvPivotItem` (ancrage horizontal) est inchangé. `tvPivotSection` a d'abord été
  laissé au pivot 50 %, puis aligné sur l'ancre haute en v1.73.6 ; `TV_PIVOT_VERTICAL`
  a alors disparu, n'ayant plus d'usage.

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

# 9. Suites constatées à l'usage (v1.73.4)

L'ancre haute supprime bien le saut, mais deux effets de bord sont apparus.

## Descente glissée, remontée sèche

L'arrivée du focus sur une cellule déclenche le `bringIntoView` implicite de
Compose. À la **descente**, la cellule visée est déjà visible (la réserve basse
en expose plusieurs lignes) : aucune demande n'est émise, notre animation fait
tout le trajet — c'est le glissement apprécié. À la **remontée**, la cellule est
composée hors viewport, en réserve de recherche de focus : Compose l'amène d'un
bond sec, et il ne reste à notre animation qu'un résidu de la hauteur de la
réserve haute, imperceptible.

Foundation 1.6.3 n'expose pas `LocalBringIntoViewSpec` (arrivé en 1.7) et un
`BringIntoViewResponder` ne peut pas interrompre la propagation vers le
scrollable parent — les deux sont lancés en parallèle. Le levier retenu est donc
la **priorité de mutation** : toute la convergence d'une grille se déroule
désormais sous `scroll(MutatePriority.UserInput)`, que le défilement implicite
(priorité `Default`) ne peut ni devancer ni préempter. L'animation primaire
utilise `animate` dans ce `ScrollScope` déjà ouvert, avec le ressort par défaut
d'`animateScrollBy` : le ressenti de la descente est inchangé.

Tenir le verrou dès la première passe suppose de ne pas attendre que la cellule
devienne mesurable — attendre, c'est laisser Compose exécuter son bond.
`offscreenRowPivotDelta` estime donc la distance en comptant les lignes d'écart,
exact à quelques pixels près sur des cellules homogènes, le reliquat étant
corrigé dès que la position réelle est lisible. `VERTICAL_PIVOT_MAX_PASSES`
passe de 5 à 8, l'animation consommant une passe entière.

## Descente du déclencheur vers le milieu de la ligne

Le déclencheur de catégorie occupe toute la largeur : son centre tombe sur la
colonne du milieu, et la recherche de focus par défaut, qui choisit le candidat
géométriquement le plus proche, y faisait atterrir la descente.
`Modifier.tvFocusDownTo` demande explicitement le focus sur la première cellule.
L'échec est silencieux et non consommé (grille absente en mode « Tout », index
hors composition après un défilement restauré) : Compose reprend alors sa
recherche par défaut, aucun appui ne reste sans effet.

## Glissement étendu au mode « Tout » (v1.73.5)

Le verrou de priorité s'applique désormais aussi à `convergeSectionToVerticalPivot`,
qui pilote les listes de rangées : mode « Tout » des trois catalogues, Accueil,
Recherche, Favoris. Le symptôme y était encore plus systématique que dans les
grilles — la rangée active occupant le centre du viewport, ses voisines en
débordent toujours un peu, et une visibilité **partielle** suffit à déclencher le
`bringIntoView` implicite. Le déplacement était donc avalé d'un bond sec dans les
deux sens, alors que les grilles ne le subissaient qu'à la remontée.

La résolution de la rangée par sa clé reste **hors** du verrou : tant qu'elle
n'est pas posée, c'est le défilement implicite qui la rendra mesurable, et le
verrou ferait expirer l'attente pour rien. Une fois résolue, la boucle lit
`visibleItemsInfo` directement, sans réattendre sous verrou.

Aucun changement de mise en page : le pivot des rangées reste à 50 %
([TV_PIVOT_VERTICAL]), seule la façon d'y aller change. Les titres de section
n'ont rien demandé de particulier — ils vivent dans la même `Column` que la
`LazyRow`, sous le même modifier de pivot, donc dans le même item de liste : tout
défilement les déplace solidairement. Aucun `stickyHeader` nulle part, vérifié.

## Ancre haute généralisée et cadre strictement fixe (v1.73.6)

Deux demandes convergentes : plus **aucun** effet sur le cadre lui-même, quel que
soit le sens du déplacement, et l'ancre haute pour les rangées comme pour les
grilles.

- `TvFocusSelectorOverlay` n'amortit plus sa position : `left`/`top` sont
  appliqués tels quels, comme l'étaient déjà la taille et le rayon. Un pivot
  correctement ancré publie de toute façon deux fois la même position ; le
  ressort ne faisait que rendre visible, sous forme de rattrapage, l'écart des
  cas où l'ancrage bute. Le fondu d'apparition/disparition est conservé : il ne
  joue qu'à l'entrée et à la sortie des listes, jamais pendant un déplacement.
- `convergeSectionToVerticalPivot` passe du pivot 50 % à [topAnchoredPivotDelta],
  la même fonction que les grilles. L'ancrage porte sur l'**item de liste
  entier**, titre de section compris : vérifié sur les six écrans concernés, le
  titre vit toujours dans le bloc porteur du modifier de pivot
  (`CategorySectionRow`, `HomeSectionRow`, `SearchSectionHeader`,
  `RecentlyWatchedRow`, section des Favoris), donc il monte avec sa rangée et
  reste lisible. Ancrer la vignette elle-même l'aurait poussé hors de l'écran.
  Chaque écran n'employant qu'un seul composant de section, la hauteur de bandeau
  y est constante et la vignette retombe toujours à la même ordonnée — les
  hauteurs diffèrent d'un écran à l'autre (18 sp sur l'Accueil, 14 sp ailleurs),
  ce qui est sans effet puisqu'on ne navigue pas d'un écran à l'autre au D-pad.
- `tvPivotVerticalEndSpacer` passe d'un demi-viewport à un viewport entier, pour
  la même raison que la réserve des grilles : sans quoi la dernière rangée ne
  peut pas rejoindre l'ancre.
- Code mort supprimé : `focusedChildPivotDelta`, `TV_PIVOT_VERTICAL`, le suivi
  des coordonnées de section devenu inutile, et les cinq tests correspondants.

Portée : le changement touche aussi l'**Accueil**, qui partage ce composant. Sa
Hero Card défile donc entièrement hors champ dès la première rangée, au lieu de
rester à moitié visible sous le pivot 50 %. À arbitrer si l'effet déplaît.

## Publication comptée, amortissement horizontal, remontée réparée (v1.73.7)

Trois retours d'usage après la v1.73.6, dont deux avaient la **même** cause.

### Le cadre flottait encore (rangées, gauche-droite et haut-bas)

Une vignette de rangée relève de deux pivots simultanés : l'horizontal
(`tvPivotItem`, qui fait glisser la `LazyRow`) et le vertical (`tvPivotSection`).
Ils ne convergent pas au même rythme — le vertical en deux frames quand la
rangée est déjà en place, l'horizontal sur toute la durée du glissement. La
fenêtre d'attente de deux frames ([AXIS_SETTLE_FRAMES], supprimée) publiait donc
au premier axe stabilisé une position que l'autre déplaçait encore : le cadre
partait, puis se corrigeait. L'amortissement le lissait autrefois en « effet » ;
une fois celui-ci retiré, le défaut est apparu tel quel.

La publication est désormais **comptée** : chaque pivot s'annonce (`beginAxis`)
et se retire (`endAxis`, appelé depuis un `finally` pour survivre à une
annulation), et la géométrie n'est lue **et** posée qu'au retrait du dernier.

### Le glissement du cadre d'une vignette à l'autre, à retrouver en grille

L'amortissement est réintroduit sur la **seule abscisse**. Les deux axes ne
bougent pas pour les mêmes raisons : verticalement toutes les cibles convergent
vers la même ancre, donc l'ordonnée publiée ne change jamais et l'amortir ne
faisait qu'étaler des écarts résiduels ; horizontalement, une grille ne défile
pas — le cadre passe réellement d'une colonne à l'autre, et l'amortir donne le
glissement demandé. Dans une rangée, l'ancrage horizontal ramenant chaque
vignette au même emplacement, l'abscisse ne change pas : le ressort y reste sans
effet, sans qu'il faille distinguer les deux contextes.

### La remontée sautait plusieurs rangées

Symptôme rapporté : à la remontée seulement, le focus bondissait loin vers le
haut ; jamais à la descente. `convergeSectionToVerticalPivot` abandonnait
(`return false`) quand la clé de rangée restait introuvable après le délai de
résolution. Or c'est le cas **structurel** de la remontée : la rangée active
occupant l'ancre haute, tout ce qui la précède est hors champ. La liste restait
donc immobile alors que le focus, lui, était bien parti au-dessus ; les appuis
suivants le faisaient monter à l'aveugle et il ne réapparaissait que plusieurs
rangées plus haut. À la descente le cas ne se présente jamais, la réserve de fin
gardant visible ce qui suit — d'où l'asymétrie observée.

`offscreenSectionStepDelta` donne une foulée de rattrapage — hauteur de la
première rangée visible, espacement compris — appliquée **une seule fois** par
convergence, qui ramène la rangée précédente dans le champ ; sa position réelle
prend ensuite le relais. Une `LazyColumn` n'exposant que des clés et aucun index
hors champ, on ne peut pas compter les rangées d'écart comme le fait
`offscreenRowPivotDelta` pour une grille ; le sens unique du cas rend l'heuristique
sûre.

### Nettoyage

`tvPivotVerticalStartSpacer` (demi-viewport de tête) n'avait de sens que pour le
pivot 50 % : l'Accueil, son dernier appelant, adopte la réserve réduite des
catalogues et la fonction disparaît.

## Vérifications automatisées

- `./gradlew testDebugUnitTest lintDebug assembleDebug` : **BUILD SUCCESSFUL**
  (2026-08-07, à chaque étape). Tests ajoutés sur `offscreenRowPivotDelta` puis
  `offscreenSectionStepDelta`. Les avertissements Kotlin restants sur
  `HomeScreen` (paramètres de navigation inutilisés) sont préexistants.

---

# 10. Release

Version :
v1.73.3 (ancre haute des grilles), v1.73.4 (remontée animée, descente à gauche),
v1.73.5 (glissement du mode « Tout »), v1.73.6 (ancre haute généralisée),
v1.73.7 (publication comptée, amortissement horizontal, remontée réparée)

Commit :
🐛 fix(tv): ancre haute du sélecteur pivot dans les grilles de catégorie (B22)

Date :
2026-08-06, complété le 2026-08-07
