# F23 - Navigation TV à double couche (Sélecteur pivot fixe et statique)

## Informations générales

Status:
RELEASED

Created:
2026-08-02

---

# 1. Description

Actuellement, sur Android TV, l'application utilise un défilement à pivot fixe (F19) : lorsqu'un élément d'une liste défile, la liste se recentre sur le point de pivot. Cependant, le sélecteur visuel de focus (le cadre lumineux) "saute" instantanément sur le nouvel élément focalisé, avant que la liste ne glisse de manière asynchrone pour le recentrer. Cela crée un effet de rebond ou de "saccade" visuelle où le cadre bouge puis la liste le rattrape.

L'objectif de cette évolution est de concevoir un **effet à double couche (2 layers) extrêmement fluide**, similaire aux interfaces premium (comme Apple TV ou les Smart TV haut de gamme) :
1. **La couche avant (Front layer)** : Le sélecteur pivot (cadre de focus) reste **parfaitement fixe et statique** à l'écran, au point de pivot exact. Il ne bouge pas d'un seul pixel.
2. **La couche arrière (Back layer)** : C'est la liste de médias (horizontale ou verticale) qui glisse de gauche à droite ou de bas en haut. Lors de l'appui sur le D-pad, l'élément suivant vient glisser et se positionner exactement sous le cadre statique du sélecteur.

---

# 2. Contexte

Dans la configuration actuelle :
* Chaque vignette de média gère individuellement son état de focus et dessine sa propre bordure via le modificateur `.tvFocusHighlight(isFocused, ...)`.
* Comme le changement de focus de Jetpack Compose est instantané, mais que l'animation de défilement de la liste (`animateScrollToPivot`) prend environ 200 à 300 ms, la bordure lumineuse apparaît immédiatement sur la carte décalée à droite ou à gauche, puis l'animation de défilement ramène la carte et son cadre vers le centre.
* Ce décalage temporel entre l'acquisition du focus et la fin de l'animation de défilement détruit l'effet de "sélecteur statique".

Pour obtenir l'effet double couche souhaité :
* Les cartes individuelles ne doivent plus afficher de bordure de focus clignotante ou sautante lors du défilement.
* Un **cadre de focus unique et fixe** doit être positionné sur la couche supérieure (overlay) au point de pivot de la liste active.
* Lorsque l'utilisateur appuie sur les flèches, la liste défile de façon synchronisée pour faire glisser le nouveau média directement sous ce cadre fixe.

---

# 3. Spécification fonctionnelle

## Objectif

Créer une expérience de navigation TV ultra-premium en immobilisant le cadre de focus (sélecteur pivot) au centre de l'écran (point de pivot horizontal et vertical), et en faisant défiler uniquement les listes de médias en arrière-plan sous ce cadre statique.

## User stories

* **En tant qu'utilisateur Android TV**, lorsque je fais défiler horizontalement une rangée de films sur la Home ou dans une catégorie, je vois le cadre de focus violet (`AccentLavande`) rester parfaitement immobile au centre de la rangée. Ce sont les affiches de films qui glissent de gauche à droite de façon fluide en arrière-plan, passant sous le cadre fixe comme sur un carrousel physique.
* **En tant qu'utilisateur Android TV**, lorsque je change de ligne (navigation verticale), le cadre de focus vertical reste également stable ou glisse de manière amortie entre les lignes, tandis que les lignes glissent verticalement pour amener la rangée active sous le sélecteur.

## Règles métier et d'interaction visuelle

1. **Retrait des bordures individuelles dynamiques** :
   * Désactiver le dessin de la bordure de focus individuelle sur les cartes de médias lors de la navigation dans les listes défilantes TV pour éviter l'effet de saut.
2. **Création du cadre pivot fixe (Couche supérieure)** :
   * Dessiner un cadre de sélection unique au premier plan (overlay), positionné au pixel près sur les coordonnées du pivot de la rangée ou de la grille active.
   * La taille de ce cadre doit s'adapter dynamiquement au type de carte affiché (ex: 130.dp pour les cartes Films/Séries standard, autre dimension pour la Hero Card ou les chaînes TV).
3. **Mouvement fluide et amorti** :
   * L'animation de défilement de la liste arrière doit être parfaitement fluide et synchronisée avec l'action de la télécommande pour donner l'illusion physique que l'élément "glisse" sous le cadre fixe.

## Critères d'acceptation (Fonctionnels)

- [ ] Sur TV, lors du défilement horizontal des rangées de médias, le cadre de focus externe ne subit aucun déplacement ou saut latéral visuel ; il reste fixe et centré sur le point de pivot.
- [ ] Les vignettes de médias glissent de manière continue et fluide sous le sélecteur pivot fixe.
- [ ] Le cadre s'ajuste parfaitement aux dimensions du média focalisé selon la rangée (VOD, Séries, TV, Hero Card).
- [ ] L'expérience mobile reste 100% tactile et inchangée (aucun sélecteur statique ou overlay de focus).

## Cas limites et gestion des erreurs

- Le cadre est absent tant qu'aucune carte média de la zone active ne possède le focus, notamment lorsque le focus est dans la navigation, une boîte de dialogue ou un contrôle d'action.
- Une rangée vide, un chargement ou la disparition de l'élément focalisé ne laisse aucun cadre orphelin à l'écran et ne bloque pas le D-pad.
- Les extrémités d'une rangée gardent un comportement de focus cohérent : aucun défilement artificiel ne doit masquer le premier ou le dernier élément.
- Cette évolution devra être conciliée avec le contrat de défilement à focus fixe de F19 à l'étape 3 ; elle ne modifie pas le mobile ni les lecteurs immersifs.

## Hypothèses et Questions ouvertes

* *Comment gérer techniquement l'overlay de focus ?* :
  * On peut dessiner le cadre de focus au premier plan de la `LazyRow` ou du conteneur de liste, positionné au point de pivot. Puisque le point de pivot est calculé de façon déterministe (ex: `TV_PIVOT_HORIZONTAL = 0.35f` soit 35% de la largeur de la liste), on peut placer une `Box` de la taille de la carte à cette coordonnée exacte au premier plan du layout.
  * Il faudra s'assurer que le cadre ne s'affiche que si la ligne ou la liste a effectivement le focus (si l'utilisateur déplace le focus vers la barre de navigation latérale, le cadre de la liste doit disparaître proprement).
  * Ces choix techniques et d'architecture de rendu seront détaillés et validés à l'Étape 3 (Spécification technique).

---

# 4. Spécification technique

## Correction d'une hypothèse de l'étape 2

L'étape 2 supposait `TV_PIVOT_HORIZONTAL = 0.35f` et proposait de placer le cadre
à 35 % de la largeur. **C'est faux dans le code actuel** :
`presentation/components/TvPivotScroll.kt` l. 65 déclare

```kotlin
const val TV_PIVOT_HORIZONTAL = 0f    // emplacement de la PREMIÈRE vignette de la rangée
const val TV_PIVOT_VERTICAL   = 0.5f  // centre du viewport
```

Le point d'ancrage horizontal est donc l'emplacement de l'item d'index 0, qui
inclut le `contentPadding` propre à chaque rangée (12.dp dans
`VodScreen.CategorySectionRow` l. 711, 0.dp dans les rangées de l'Accueil). Il
n'existe pas de coordonnée constante utilisable pour poser le cadre : elle varie
d'une rangée à l'autre. Toute conception fondée sur une fraction fixe de la
largeur produirait un cadre décalé sur une rangée sur deux.

## Principe technique retenu : ancrer le cadre sur les positions **stabilisées**

Constat central : *toutes* les positions de focus stabilisées coïncident déjà
avec le pivot. Le cadre n'a donc pas besoin de connaître les coordonnées du
pivot — il lui suffit de ne suivre **que** les positions convergées.

* Deux vignettes successives de même géométrie convergent vers **exactement** la
  même position → un cadre qui ne suit que les positions convergées ne bouge
  pas d'un pixel. C'est l'effet « sélecteur statique » demandé, obtenu sans
  aucun calcul de coordonnées cible.
* Ce qu'on retire, c'est la position **transitoire** : aujourd'hui la bordure est
  peinte par la carte, donc dès l'acquisition du focus, avant que la liste n'ait
  glissé. C'est exactement le rebond décrit au § 2.
* Quand la géométrie change réellement (rangée de films 130 dp → tuile TV
  paysage → Hero Card, ou passage rangée → grille), le cadre s'anime vers sa
  nouvelle taille/position : le critère « le cadre s'ajuste aux dimensions du
  média focalisé » est satisfait par le même mécanisme.

`TvPivotScroll.kt` sait déjà détecter la stabilisation : les boucles
`convergeSectionToVerticalPivot` (l. 272-313) et `convergeCellToVerticalPivot`
(l. 315-347) sortent sur
`abs(delta) <= VERTICAL_PIVOT_TOLERANCE_PX` répété `VERTICAL_PIVOT_STABLE_PASSES`
fois. C'est le point de publication naturel.

## Nouveaux composants

### `presentation/components/TvFocusSelector.kt` (nouveau fichier)

```kotlin
/** Géométrie du sélecteur, en coordonnées de la racine de l'écran. */
data class TvSelectorTarget(val bounds: Rect, val cornerRadius: Dp)

/**
 * Couche avant du focus TV. Reçoit uniquement des positions **stabilisées**
 * (convergence de pivot terminée) : deux vignettes successives d'une même
 * rangée produisant la même position, le cadre reste immobile.
 */
@Stable
class TvFocusSelectorState {
    var target: TvSelectorTarget? by mutableStateOf(null)
        private set
    var isVisible: Boolean by mutableStateOf(false)
        private set

    fun publishStabilised(bounds: Rect, cornerRadius: Dp) { … }
    fun clear() { … }          // focus sorti des listes : rail, dialogue, action
}

/** Fourni par l'écran ; `null` hors TV ou quand la couche avant est inactive. */
val LocalTvFocusSelector = staticCompositionLocalOf<TvFocusSelectorState?> { null }

/** Overlay dessiné au premier plan du conteneur d'écran. */
@Composable fun TvFocusSelectorOverlay(state: TvFocusSelectorState, modifier: Modifier = Modifier)

/** Publie la géométrie d'une cible quand sa convergence de pivot est terminée. */
@Composable fun Modifier.tvSelectorTarget(enabled: Boolean, cornerRadius: Dp = 14.dp): Modifier
```

L'overlay anime `bounds` et `cornerRadius` (`animateRectAsState` /
`animateDpAsState`, `spring(dampingRatio = DampingRatioNoBouncy)`) et dessine un
`Modifier.border(1.5.dp, AccentLavande.copy(alpha = 0.95f), RoundedCornerShape(radius))`
— strictement les mêmes valeurs que `TvFocusHighlight.kt` l. 19-30, pour que le
rendu soit indiscernable de l'actuel une fois immobile.

L'overlay est **non focusable et non cliquable**
(`Modifier.focusProperties { canFocus = false }`, aucun `clickable`) : il ne
perturbe ni la recherche de focus D-pad, ni la validation OK sur la carte.

## Composants impactés

| Fichier | Modification |
| --- | --- |
| `presentation/components/TvFocusSelector.kt` | **NOUVEAU** — état, overlay, modificateur de cible. |
| `presentation/components/TvPivotScroll.kt` | Publication de la stabilisation vers le `TvFocusSelectorState` (horizontal après `animateScrollToPivot`, vertical en fin de convergence). |
| `presentation/components/TvFocusHighlight.kt` | Devient conditionnel : no-op quand la couche avant est active (voir ci-dessous). |
| `presentation/home/HomeScreen.kt` | Fournit le `TvFocusSelectorState`, monte l'overlay dans la `Box` racine (l. 180-184), transmet `selectorModifier` à la Hero. |
| `presentation/home/components/HomeTrendingCarouselTv.kt` | Nouveau paramètre `selectorModifier: Modifier = Modifier`, appliqué à la carte active du pager. |
| `presentation/vod/VodScreen.kt` | Idem (`Box` racine l. 134-138). |
| `presentation/series/SeriesScreen.kt` | Idem. |
| `presentation/livetv/LiveTvScreen.kt` | Idem. |

**Aucune carte de rangée ou de grille n'est modifiée** (seule la Hero reçoit un
paramètre, voir sa section dédiée). C'est le point clé de la conception : la
suppression des bordures individuelles passe par `tvFocusHighlight`, que toutes
les cartes utilisent déjà (`HomeCards.kt` l. 396, 195, `LiveTvComponents.kt`,
`VodScreen.CategoryFilterChip` l. 751…). Sa signature ne change pas :

```kotlin
@Composable   // devient @Composable pour lire le CompositionLocal
fun Modifier.tvFocusHighlight(focused: Boolean, shape: Shape, …): Modifier {
    // La couche avant dessine le cadre : la carte ne doit plus le peindre,
    // sinon le sélecteur « saute » avec elle avant que la liste n'ait glissé.
    if (LocalTvFocusSelector.current != null) return this
    return this.border(…)   // comportement actuel, inchangé
}
```

Le `CompositionLocal` n'est fourni que par les écrans de catalogue TV : les puces
de catégories, les boutons d'action, les fiches de détail, les dialogues et le
mobile conservent donc leur anneau de focus individuel — ce qui est le
comportement voulu (le sélecteur pivot ne concerne que les listes de médias).

**Limite assumée :** dans un écran fournissant le `CompositionLocal`, les puces
de catégorie (`CategoryFilterChip`) perdraient aussi leur anneau. Deux options,
à trancher en review : (a) rendre le `CompositionLocal` local aux seuls
conteneurs de listes plutôt qu'à l'écran entier, (b) donner un paramètre
`forceLocalRing = true` aux composants hors listes. **Option (a) retenue** :
le `CompositionLocalProvider` enveloppe uniquement la `LazyColumn` /
`LazyVerticalGrid` des médias, pas le bandeau de catégories ni la bannière hors
ligne. L'overlay, lui, reste dans la `Box` racine pour pouvoir déborder du
conteneur.

## Cas de la Hero Card (décision PO du 2026-08-02 : incluse)

`HomeTrendingCarouselTv` est composée dans l'item `home_trending`
(`HomeScreen.kt` l. 379-403), **sans `tvPivotSection`** : elle ne participe donc
pas à la convergence de pivot, et le point de publication « à la stabilisation »
décrit plus haut ne s'y applique pas. Deux régimes de publication coexistent :

| Cible | Événement de publication |
| --- | --- |
| Vignette d'une rangée / cellule de grille | fin de convergence du pivot (`TvPivotScroll`) |
| Hero Card (et tout nœud hors liste à pivot) | `onGloballyPositioned` **après** acquisition du focus, la Hero étant immobile par construction |

D'où un second point d'entrée du modificateur de cible :

```kotlin
/**
 * Publication pour une cible qui ne défile pas (Hero Card) : sa position est
 * déjà définitive au moment du focus, il n'y a aucune convergence à attendre.
 */
@Composable fun Modifier.tvSelectorStaticTarget(enabled: Boolean, cornerRadius: Dp): Modifier
```

Détails d'intégration :

* `HomeTrendingCarouselTv` reçoit un paramètre `selectorModifier: Modifier = Modifier`
  qu'elle applique à la **carte active du pager** (pas au conteneur) : le cadre
  épouse l'affiche mise en avant, pas toute la zone du carrousel.
* Rayon : **16.dp**, valeur de la Hero (cf. `HomeTrendingCarouselSkeleton`,
  `HomeScreen.kt` l. 875) — le cadre l'anime depuis/vers les 14.dp des vignettes.
* Le changement de page du pager republie la géométrie : le cadre suit l'affiche
  active sans quitter la Hero.
* **Transition Hero ↔ première rangée** : c'est la plus grande variation de
  taille du système (≈ 300 dp de haut vers 195 dp). Elle emprunte le même
  ressort non rebondissant ; si le mouvement est jugé trop ample en review
  visuelle, le repli est un fondu (alpha) plutôt qu'un déplacement — sans
  changer la conception.

Avec B17, cette cible est aussi la **priorité 1 du focus initial** : le cadre
apparaît donc sur la Hero dès l'ouverture de l'Accueil, ce qui est exactement le
critère d'acceptation n° 1 de B17.

## Modèles de données, API, services, stockage, cache

Néant. Feature 100 % rendu : aucune entité Room, aucune migration (base en
version 21), aucun DTO, aucun `UseCase`, aucun `Repository`, aucun `ViewModel`.

## Performances

* **Recompositions** : `TvFocusSelectorState` est `@Stable` et n'est lu que par
  l'overlay. Un changement de cible recompose un seul nœud (`Box` + `border`),
  jamais les listes.
* **Animation** : une `animateRectAsState` par écran, active uniquement pendant
  la transition ; immobile (donc sans frame de recomposition) dès que la cible ne
  change plus — cas nominal d'un défilement de rangée homogène.
* **Coût retiré** : chaque carte cesse de recomposer son `Modifier.border` à
  chaque changement de focus (aujourd'hui deux cartes recomposent par appui).
* **`staticCompositionLocalOf`** plutôt que `compositionLocalOf` : la valeur ne
  change pas pendant la vie de l'écran, aucune invalidation de lecture n'est
  nécessaire.

## Sécurité

Sans objet.

## Compatibilité

* **Mobile** : le `CompositionLocal` n'est jamais fourni (`if (isTv)`), l'overlay
  n'est jamais composé. Aucune modification du rendu ni du tactile — critère
  d'acceptation n° 4.
* **F19** : le contrat de défilement à pivot fixe est **conservé tel quel**.
  F23 ne modifie ni `TV_PIVOT_HORIZONTAL`, ni `TV_PIVOT_VERTICAL`, ni les
  spacers (`tvPivotHorizontalEndSpacer`, `tvPivotVerticalStartSpacer`,
  `tvPivotVerticalEndSpacer`), ni la logique de convergence. Il **ajoute** un
  point de publication à la fin de ce qui existe. C'est la réconciliation
  demandée au cas limite de l'étape 2.
* **Lecteurs immersifs** : hors périmètre, aucun `CompositionLocal` fourni.
* **B17** (focus par défaut au premier média) : complémentaire — B17 provoque
  l'acquisition initiale du focus, F23 en assure le rendu. Un cadre apparaît donc
  dès la fin du chargement si B17 est livrée ; sans B17, l'overlay reste
  simplement invisible jusqu'au premier appui, comme aujourd'hui.
* **min SDK 21** : aucune API conditionnée. `animateRectAsState` fait partie de
  `androidx.compose.animation` déjà présent.

## Dépendances

Aucune dépendance Gradle ajoutée.

## Risques techniques

| Risque | Gravité | Mitigation |
| --- | --- | --- |
| **Élément non convergeable** (début/fin de rangée, rangée plus courte que le viewport) : la carte ne peut pas atteindre le pivot, le cadre pointerait un emplacement vide | Élevée | Ne se produit pas : F19 pose déjà `tvPivotHorizontalEndSpacer` (un viewport de large) et les spacers verticaux d'une demi-hauteur, précisément pour que **tout** élément puisse rejoindre le pivot. La publication n'a lieu **qu'après** stabilisation constatée : si la convergence échoue (timeout de `resolveSectionInfo`, 200 ms), rien n'est publié et le cadre conserve sa position précédente plutôt que d'en inventer une. |
| Absence de cadre pendant les ~250 ms de convergence | Visuelle | L'animation part de la position précédente, qui est identique dans le cas nominal : le cadre est donc visible en permanence. Seule la toute première acquisition anime une apparition (fondu court). |
| **Grille** (`tvPivotCell`) : le pivot n'est que vertical, la colonne varie | Fonctionnelle | Comportement volontaire et documenté : le sélecteur est fixe **sur l'axe de défilement de la liste qu'il gouverne**. En grille, il reste à hauteur constante et glisse horizontalement d'une colonne à l'autre — la grille ne défile pas horizontalement, il n'y a donc rien à faire glisser sous le cadre. |
| Hero Card (`HomeTrendingCarouselTv`) : géométrie très différente | Moyenne | **Décision PO du 2026-08-02 : la Hero est incluse dans le sélecteur.** Voir la section dédiée ci-dessous — elle ne participe pas au pivot, sa publication passe donc par un chemin distinct, et la transition de taille est amortie par le ressort commun. |
| Cadre orphelin (rangée vidée, focus parti vers le rail ou un dialogue) | Fonctionnelle | `onFocusChanged { if (!it.hasFocus) state.clear() }` sur le conteneur de listes ; `clear()` masque l'overlay. Couvre les trois cas limites de l'étape 2. |
| Cadre désynchronisé après un `bringIntoView` tardif de Compose | Moyenne | La convergence de F19 est déjà multi-passes précisément pour ça (`VERTICAL_PIVOT_MAX_PASSES = 5`) ; la publication a lieu à la dernière passe stable, donc après le `bringIntoView`. |
| `tvFocusHighlight` devient `@Composable` | Faible | Elle est déjà appelée exclusivement depuis des `@Composable` ; le changement est source-compatible pour tous les sites d'appel existants. |
| Interaction avec B18 (cartes unifiées) | Faible | B18 uniformise les géométries de vignettes (130 × 195, rayon 14.dp) : **il réduit** le nombre de transitions de taille du cadre. Livrer B18 avant F23 est préférable. |

## Contraintes de performance

Le cadre doit rester perçu comme immobile : l'animation de position/taille doit
être soit nulle (cible identique), soit plus courte que la perception du
mouvement. `spring(stiffness = StiffnessMediumLow, dampingRatio = NoBouncy)` est
retenu, sans rebond — un ressort rebondissant réintroduirait visuellement le
défaut que la feature supprime.

---

# 5. Architecture

## Position dans la Clean Architecture

Comme F19, feature purement `presentation` : aucun `UseCase`, aucun
`Repository`, aucun ViewModel. Le pivot et son sélecteur sont des comportements
de rendu. La Clean Architecture est respectée par abstention.

```
presentation/components/
├── TvPivotScroll.kt        ← existant : calcule et exécute le défilement ;
│                             publie désormais la STABILISATION
├── TvFocusSelector.kt      ← NOUVEAU : couche avant (état + overlay + 2 modificateurs
│                             de cible : convergée / statique)
└── TvFocusHighlight.kt     ← devient no-op sous la couche avant

presentation/home/components/HomeTrendingCarouselTv.kt  ← + selectorModifier (Hero)

presentation/home/HomeScreen.kt        ┐
presentation/vod/VodScreen.kt          │ fournissent l'état,
presentation/series/SeriesScreen.kt    │ montent l'overlay
presentation/livetv/LiveTvScreen.kt    ┘

presentation/home/components/HomeCards.kt          ← INCHANGÉ
presentation/livetv/components/LiveTvComponents.kt ← INCHANGÉ
```

Deux régimes de publication, un seul état :

```
cible dans une liste à pivot  ──► publication à la STABILISATION (TvPivotScroll)
cible immobile (Hero Card)    ──► publication à onGloballyPositioned + focus
                                          │
                                          ▼
                            TvFocusSelectorState (unique par écran)
```

## Flux — appui D-pad droite dans une rangée

```
Appui D-pad
    │
    ▼
Recherche de focus Compose (inchangée)
    │
    ▼
La carte N+1 reçoit le focus
    │
    ├──►  tvFocusHighlight : NO-OP (couche avant active)      ← plus de saut
    │     ► la carte ne peint aucune bordure
    │
    └──►  tvPivotItem → animateScrollToPivot(index, 0f, 0f)
              │
              │  ~200-300 ms : la rangée glisse (COUCHE ARRIÈRE)
              │
              ▼
          convergence terminée
              │
              ▼
          tvSelectorTarget publie boundsInRoot + rayon (STABILISÉ)
              │
              ▼
      TvFocusSelectorState.target
              │
              ▼
      TvFocusSelectorOverlay : cible identique à la précédente
      (même pivot, même géométrie) → AUCUN mouvement (COUCHE AVANT)
```

Le résultat visuel est exactement celui demandé : le cadre reste immobile, les
affiches glissent dessous.

## Flux — changement de rangée (navigation verticale)

```
Appui D-pad bas
    │
    ▼
Focus sur une carte de la rangée N+1
    │
    ├──► la LazyColumn converge verticalement (F19, inchangé) :
    │    convergeSectionToVerticalPivot → centre du focus au pivot 50 %
    │
    └──► publication à la stabilisation
             │
             ▼
     cible : même Y (pivot 50 %), même X si la rangée a le même contentPadding,
     taille éventuellement différente (tuile TV paysage vs affiche 2:3)
             │
             ▼
     l'overlay s'anime en douceur vers la nouvelle taille — « glisse de manière
     amortie entre les lignes », conformément à la user story
```

## Responsabilités des composants

* **`TvPivotScroll`** (couche arrière) : déplacer la liste pour que la cible
  atteigne le pivot, et **signaler quand c'est fait**. Il ne connaît pas le
  cadre ; il publie un événement de stabilisation.
* **`TvFocusSelectorState`** (modèle de la couche avant) : détenir la géométrie
  courante du cadre et sa visibilité. Aucune connaissance des listes.
* **`TvFocusSelectorOverlay`** (vue de la couche avant) : dessiner un cadre
  animé, non focusable, au premier plan de l'écran.
* **`tvFocusHighlight`** : arbitrer qui peint l'anneau — la carte (hors listes,
  mobile) ou la couche avant (listes TV). Point de bascule unique.
* **Écrans** : décider du périmètre de la couche avant (quels conteneurs) et
  monter l'overlay.

## Décisions techniques

1. **Publier les positions stabilisées plutôt que calculer les coordonnées du
   pivot.** Recalculer la cible (`contentPadding` de la rangée + fraction de
   viewport + taille de carte) dupliquerait la logique de `TvPivotScroll` et
   divergerait au premier ajustement. Réutiliser le résultat déjà mesuré garantit
   que le cadre est *par construction* là où la carte s'arrête.
2. **Suppression de la bordure individuelle par `CompositionLocal`, pas par
   paramètre.** Ajouter un `drawRing: Boolean` à toutes les cartes toucherait une
   dizaine de composables et leurs sites d'appel, pour une décision qui est
   contextuelle et non propre à la carte. Le `CompositionLocal` est lu à un seul
   endroit : `tvFocusHighlight`.
3. **`CompositionLocalProvider` limité aux conteneurs de listes de médias.**
   Sinon les puces de catégorie et les boutons d'action perdraient leur anneau
   sans que la couche avant ne les gère (elles ne participent pas au pivot).
4. **Overlay dans la `Box` racine de l'écran, pas dans la `LazyColumn`.** Un
   overlay interne à la liste défilerait avec elle — contresens absolu pour un
   sélecteur statique. La `Box` racine existe déjà dans les quatre écrans.
5. **Le sélecteur est fixe sur l'axe de défilement de la liste qu'il gouverne.**
   Formulation qui rend le comportement en grille cohérent plutôt qu'exceptionnel
   (fixe en Y, suit la colonne en X).
6. **Aucune modification des constantes ni des spacers de F19.** F23 est une
   couche additive ; si elle est retirée, F19 fonctionne exactement comme avant.
7. **Repli sûr en cas d'échec de convergence** : ne rien publier plutôt que
   publier une position transitoire. Le cadre reste sur sa dernière position
   valide, jamais sur un emplacement vide.

## Stratégie de tests

La partie testable en JVM est la logique d'état de la couche avant ; le rendu et
la fluidité exigeraient un device et sont donc exclus des critères de validation
de l'agent (`AGENTS.md`).

**`TvFocusSelectorStateTest`** (nouveau, JVM pur — la classe est un état Compose
sans dépendance Android) :
1. état initial : `target == null`, `isVisible == false` ;
2. `publishStabilised` rend le cadre visible et fixe la géométrie ;
3. deux publications successives de géométrie identique laissent `target`
   inchangé (**c'est le test de la propriété « le cadre ne bouge pas »**) ;
4. `clear()` masque le cadre sans effacer la dernière géométrie (évite un
   fondu depuis un `Rect.Zero` au retour du focus) ;
5. une publication après `clear()` réaffiche le cadre.

**`TvPivotScrollTest`** (existant, à compléter) :
6. `pivotScrollOffset` et `focusedChildPivotDelta` restent inchangés — tests
   existants conservés tels quels, en garde de non-régression du contrat F19 ;
7. le prédicat de stabilisation (`abs(delta) <= TOLERANCE` sur `STABLE_PASSES`
   passes) est extrait en fonction pure testable si la publication l'exige.

Non-régression : `./gradlew testDebugUnitTest`, `assembleDebug`, `lintDebug`.

---

# 6. Plan de développement

## Ordre d'exécution

F23 consomme l'acquisition de focus de B17 : le mécanisme d'ancrage est créé et
testé avant le câblage des rangées, puis la Hero est intégrée comme cas dédié.

### Tâche 1 — Créer l'état de cadre stabilisé et son calque avant

- [x] Ajouter le composant presentation qui mémorise uniquement les coordonnées
  stabilisées et dessine le cadre hors des cartes.

Objectif : supprimer le rebond pendant le scroll pivot tout en conservant une
animation lors d'un vrai changement de géométrie.

Fichiers : nouveaux composants `presentation/components/` et
`TvPivotScroll.kt` si le signal de stabilisation doit être exposé.

Validation : une navigation horizontale entre cartes identiques ne déplace pas
le cadre ; les erreurs de coordonnées/nœud détaché ne provoquent pas de crash.

### Tâche 2 — Brancher le calque aux rangées et grilles TV

- [x] Remplacer le rendu de bordure local des cartes par les callbacks de cible
  et de stabilisation dans les écrans catalogue.

Objectif : garantir un seul cadre visible, correctement dimensionné, sur Home,
VOD, Séries, Live et les grilles couvertes par la spécification.

Fichiers : `HomeScreen.kt`, `VodScreen.kt`, `SeriesScreen.kt`,
`LiveTvScreen.kt`, cartes/composants TV concernés.

Validation : changement de rangée ou passage grille ajuste taille/position ;
mobile et éléments non média ne sont pas affectés.

### Tâche 3 — Intégrer la Hero Card et les transitions particulières

- [x] Fournir le point d'ancrage de la Hero et traiter les changements de taille
  ou de visibilité sans doublon de cadre.

Objectif : inclure la Hero, décision PO du 2026-08-02, sans court-circuiter son
pager ni l'acquisition initiale B17.

Fichiers : `HomeTrendingCarouselTv.kt` et composants Home associés.

Validation : cadre présent sur la Hero focalisée, pas de cadre fantôme après
changement d'écran ni pendant disparition de cible.

### Tâche 4 — Tester les décisions pures et vérifier la non-régression

- [x] Couvrir les sélecteurs/états non Compose et exécuter les contrôles.

Fichiers : tests JVM des composants purs, tests existants et ce ticket.

Validation : cas mêmes coordonnées, géométrie différente et cible absente sont
couverts ; `testDebugUnitTest`, `assembleDebug`, `lintDebug` passent ; le D-pad
TV reste une vérification manuelle distincte.

---

# 7. Notes de développement

Implémenté le 2026-08-02. Conception globale suivie (état unique par écran,
publication à la stabilisation, `tvFocusHighlight` devenu no-op sous la couche
avant), avec deux simplifications par rapport au détail de la section 4 :

## Écarts par rapport à la spécification technique

1. **Pas de modifier `tvSelectorTarget` générique.** La section 4 esquissait
   `Modifier.tvSelectorTarget(enabled, cornerRadius)` comme point de
   publication pour les cartes de liste. En pratique, `TvPivotScroll.kt`
   possède déjà les `LayoutCoordinates` du descendant focalisé au moment exact
   où sa convergence se termine (`convergeSectionToVerticalPivot`,
   `convergeCellToVerticalPivot`, et l'unique `animateScrollToPivot` du pivot
   horizontal) : ajouter un modifier séparé aurait dupliqué cette détection de
   stabilisation. `convergeSectionToVerticalPivot`/`convergeCellToVerticalPivot`
   retournent désormais un `Boolean` (stabilisé ou passes épuisées), et
   `tvPivotItem`/`tvPivotCell`/`tvPivotSection` publient directement via un
   petit helper `TvFocusSelectorState.publishFrom(coordinates, cornerRadius)`
   — sans modifier supplémentaire sur les cartes, conforme à l'exigence
   « aucune carte n'est modifiée ».
2. **`tvSelectorStaticTarget` remplacé par un appel direct pour la Hero.** La
   Hero (`HomeTrendingCarouselTv`) porte un unique nœud `.focusable()` sur le
   pager entier — un `Modifier.onFocusChanged` posé sur la carte d'une page
   individuelle ne recevrait jamais l'état focalisé (le focus vit sur un
   ancêtre, pas sur elle). La publication est donc faite directement dans
   `HomeTrendingCarouselTv` via un `LaunchedEffect(hasFocus, pagerState.currentPage,
   pagerState.isScrollInProgress)` qui republie dès que le pager a le focus et
   a fini de défiler, en utilisant les coordonnées de la carte courante
   capturées par `onGloballyPositioned`. `selectorModifier` (paramètre esquissé
   en section 4) n'a donc pas été ajouté : `HomeTrendingCarouselTv` lit
   directement `LocalTvFocusSelector.current`, déjà disponible puisqu'elle est
   composée à l'intérieur du `CompositionLocalProvider` de la `LazyColumn` de
   `HomeScreen`.

Aucun autre écart : `TV_PIVOT_HORIZONTAL`/`TV_PIVOT_VERTICAL`, les spacers de
F19, et la logique de convergence restent strictement inchangés (F23 est
additive, comme prévu par la décision technique #6).

## Rayon du cadre selon la famille de carte

`TV_SELECTOR_DEFAULT_RADIUS = 14.dp` (rayon unifié B18) est le défaut de
`tvPivotItem`/`tvPivotCell`/`tvPivotSection`. `LiveTvComponents.kt` (rangées et
grille Direct, `StreamTvCard` à 12.dp) passe explicitement
`selectorCornerRadius = 12.dp` à chaque site d'appel concerné — cf. le risque
« interaction avec B18 » de la section 4 : LiveTV n'a pas été unifiée à 14.dp,
donc son cadre ne l'est pas non plus. La Hero publie 16.dp (rayon de
`HomeTrendingSlideTv`).

## Vérifications automatisées

- `TvFocusSelectorStateTest` (nouveau, 5 cas : état initial, publication rend
  visible, deux publications identiques laissent `target` inchangé — la
  propriété centrale du « cadre ne bouge pas » —, `clear()` masque sans
  effacer la géométrie, republication après `clear()` réaffiche le cadre) :
  5/5 réussis.
- `./gradlew compileDebugKotlin` → réussi après chaque modification
  (`TvFocusSelector.kt`, `TvPivotScroll.kt`, `TvFocusHighlight.kt`,
  `HomeScreen.kt`, `VodScreen.kt`, `SeriesScreen.kt`, `LiveTvScreen.kt`,
  `LiveTvComponents.kt`, `HomeTrendingCarouselTv.kt`).
- `./gradlew assembleDebug lintDebug` → réussi, `0 errors`.
- `./gradlew testDebugUnitTest` (hors `HomeViewModelTest`/`RecentlyAddedViewModelTest`) →
  538 tests, 0 échec.

### Note commune T10/B18/F23 : `HomeViewModelTest` bloque l'exécution complète

Dans cette session, `./gradlew testDebugUnitTest` sans filtre reste bloqué
indéfiniment. Bisection par filtre `--tests` (par paquet, par classe) :
le blocage est reproductible et déterministe sur la seule classe
`com.cstv.app.presentation.home.HomeViewModelTest` (et son voisin
`RecentlyAddedViewModelTest`), pas sur les 538 autres tests, qui passent tous
une fois cette classe exclue. `HomeViewModelTest.kt` documente lui-même
(commentaire l. 85-86, écrit avant cette session) que l'initialisation de
`HomeViewModel` démarre un sondage EPG (`while(true) { delay(60_000) ; ... }`)
sur un dispatcher non lié au scheduler virtuel de `runTest` — plusieurs tests
de ce fichier omettent volontairement `advanceUntilIdle()` pour cette raison
même. Aucun fichier de ce ticket ne touche `HomeViewModel.kt` (T10/B18/F23
modifient uniquement `HomeScreen.kt`/`HomeCards.kt`, côté Compose) : le blocage
est un caractère préexistant de l'environnement de test, pas une régression
introduite ici. Signalé plutôt que masqué, conformément à `AGENTS.md`.

En cours de route, un bug préexistant et sans rapport a été découvert et
corrigé (voir notes de B18) : `SeriesViewModelTest.kt` ne passait pas
`categoryId` (ajouté à `SavePlaybackPositionUseCase` par T9) dans deux
`verify()`, provoquant une `InvalidUseOfMatchersException` de Mockito
indépendante de F23/T10/B18.

Les critères d'acceptation visuels de F23 (§3 : immobilité perçue du cadre,
fluidité du glissement, absence de rebond) exigent un appareil/émulateur
Android TV et sont donc hors des critères de validation automatisés de
l'agent (`AGENTS.md`) — non vérifiés dans cette session.

Étape 5 (Implémentation) terminée le 2026-08-02 ; Étapes 6-8 consignées
ci-dessous.

---

# 8. Review

Status: RESOLVED

Review effectuée le 2026-08-02 sur `TvFocusSelector.kt`,
`TvPivotScroll.kt`, `TvFocusHighlight.kt` et les intégrations Home, Films,
Séries et Direct.

## Critique

### F23-R1 — L'overlay est contraint à la taille de l'écran au lieu de la cible

**Description :** les quatre écrans appellent
`TvFocusSelectorOverlay(tvFocusSelector, modifier = Modifier.fillMaxSize())`.
Dans l'overlay, ce modifier fourni par l'appelant est placé avant
`.offset(...)` puis `.size(width, height)`. En Compose, le `fillMaxSize()`
extérieur impose des contraintes min/max égales au viewport à toute la chaîne
interne : le `size()` ajouté ensuite ne peut donc plus réduire la `Box` aux
bounds de la carte.

**Impact :** dès qu'une cible est publiée, la bordure ne peut pas épouser sa
vignette ; elle est mesurée comme une couche plein écran, éventuellement
décalée. Le comportement central de F23 est inutilisable sur Home, Films,
Séries et Direct malgré la réussite des tests d'état.

**Correction attendue :** ne pas passer `fillMaxSize()` comme modifier de la
`Box` qui porte la bordure, ou séparer explicitement un hôte plein écran d'un
enfant dont `offset` et `size` sont libres. Ajouter une vérification automatisée
de la géométrie produite pour une cible plus petite que le viewport.

## Majeur

### F23-R2 — Des coordonnées globales sont réutilisées comme offsets locaux

**Description :** `publishFrom()` mémorise `coordinates.boundsInRoot()`, puis
`TvFocusSelectorOverlay` utilise directement `bounds.left`/`bounds.top` dans un
`Modifier.offset` relatif à la `Box` de l'écran. Or cette `Box` n'est pas la
racine Compose : le `NavHost` est décalé de la largeur du rail TV dans
`MainActivity`, et chaque route catalogue est elle-même placée sous le top
inset par `composableBelowStatusBar`.

**Impact :** après correction de F23-R1, le cadre restera décalé une seconde
fois de l'origine de l'écran de destination (notamment largeur du rail et inset
haut). Il ne se superposera donc pas à la carte mesurée.

**Correction attendue :** exprimer la cible dans le repère local de l'hôte de
l'overlay, par exemple en mémorisant aussi les coordonnées de cet hôte et en
soustrayant son origine dans la racine, ou en convertissant les coordonnées via
les API `LayoutCoordinates` adaptées. Couvrir le cas d'un hôte dont l'origine
n'est pas `(0, 0)`.

### F23-R3 — Les axes horizontal et vertical publient deux stabilisations concurrentes

**Description :** une carte de rangée porte simultanément `tvPivotItem`, qui
publie après `animateScrollToPivot`, et un ancêtre `tvPivotSection`, qui publie
après sa convergence verticale. Les deux coroutines sont indépendantes et
écrivent dans le même `TvFocusSelectorState`. La première qui termine peut donc
publier des bounds dont l'autre axe est encore en mouvement. De plus,
`animateScrollToPivot()` retourne normalement après un index devenu invalide ou
une exception non liée à l'annulation, et `tvPivotItem` publie quand même.

**Impact :** lors d'un changement de rangée ou d'une navigation rapide, le
cadre peut recevoir une position transitoire, se déplacer avec le contenu puis
être corrigé par la seconde publication. Cela réintroduit précisément le saut
que F23 doit supprimer ; un échec de convergence peut aussi rendre visible une
cible non stabilisée.

**Correction attendue :** coordonner les deux axes pour une acquisition de
focus donnée et ne publier qu'après leur stabilisation commune. Une publication
doit aussi être associée à la cible encore focalisée et abandonnée si le scroll
horizontal échoue, est annulé ou devient obsolète.

### F23-R4 — La géométrie mesurée n'est pas toujours celle de l'anneau remplacé

**Description :** la simplification documentée mesure le wrapper portant
`tvPivotItem` plutôt que la surface exacte qui peignait `tvFocusHighlight`.
Cela diverge notamment pour les cartes Top 10, dont le wrapper inclut le grand
chiffre alors que l'anneau local entourait seulement le poster de 130 dp. Pour
la Hero, `currentCardCoordinates` est capturé avant le
`graphicsLayer` et le `padding(horizontal = 8.dp)` de la slide ; il décrit la
page du pager plutôt que la surface clippée exacte de la carte active, alors que
la spécification demandait explicitement d'appliquer la cible à cette carte.

**Impact :** même avec un overlay correctement dimensionné et positionné, le
cadre peut entourer une zone plus large que l'affiche ou ses gouttières, avec
des transitions de taille erronées entre Hero, Top 10 et rangées standard.

**Correction attendue :** mesurer la même surface que l'ancien
`tvFocusHighlight` (modifier de cible posé au niveau du poster/cadre), ou
publier des bounds explicitement dérivés de cette surface. La Hero doit capturer
les coordonnées après son padding, sur le nœud effectivement clippé.

## Mineur

### F23-R5 — La parité de style avec les anneaux existants n'est pas complète

**Description :** les appels Home utilisent tous le rayon par défaut de 14 dp,
alors que `HomeResumeWatchingCard` utilise 12 dp et `HomeLiveTvCard` 16 dp.
Par ailleurs, le retour immédiat de `tvFocusHighlight` sous le
`CompositionLocal` supprime aussi les bordures de repos (`restingColor` /
`restingWidth`), notamment celle de `HomeLiveTvCard`, alors que seul l'anneau
focalisé doit être remplacé. Enfin, position et taille utilisent le ressort par
défaut de `animateDpAsState`, pas le ressort `StiffnessMediumLow` annoncé dans
la spécification.

**Impact :** les coins et l'état non focalisé changent selon les rangées, et les
transitions de géométrie ne suivent pas exactement le mouvement amorti retenu.

**Correction attendue :** transmettre le rayon réel de chaque famille de
carte, préserver la bordure de repos lorsque le cadre global est actif, et
utiliser explicitement la même spécification de ressort pour position, taille
et rayon.

### F23-R6 — Les tests ne protègent que le stockage de l'état

**Description :** `TvFocusSelectorStateTest` vérifie cinq transitions triviales
de `target`/`isVisible`, mais aucune conversion de repère, contrainte de layout,
publication obsolète ou coordination des deux axes. Le test « deux
publications identiques » utilise seulement `assertEquals` sur deux valeurs ;
il ne démontre pas l'absence d'une invalidation ou d'un mouvement de l'overlay.

**Impact :** les défauts F23-R1 à F23-R4 restent entièrement invisibles à la
suite ciblée, qui passe alors que le rendu principal est incorrect.

**Correction attendue :** extraire les calculs de conversion et l'arbitrage des
publications dans des unités pures testables en JVM, puis couvrir un hôte
décalé, deux axes terminant dans des ordres opposés, une cible remplacée pendant
le scroll et un échec/une annulation de convergence.

## Conclusion

**CHANGES REQUESTED.** L'état central et le branchement général sont lisibles,
mais la taille, le repère et le moment de publication du cadre ne garantissent
pas le comportement demandé. F23 doit passer par l'étape 7 avant toute
validation finale.

Vérification ciblée exécutée pendant la review :
`./gradlew testDebugUnitTest --tests com.cstv.app.domain.model.EpisodeLabelTest
--tests com.cstv.app.presentation.components.TvFocusSelectorStateTest` réussit.
Ce succès ne valide ni la géométrie Compose ni le comportement D-pad et aucune
validation finale n'a été réalisée à cette étape.

## Étape 7 — Correction (2026-08-02)

### F23-R1 — résolu

`TvFocusSelectorOverlay` restructurée en deux `Box` : un hôte externe qui
reçoit le `modifier` de l'appelant (`Modifier.fillMaxSize()`) et un enfant
interne portant `offset`/`size`/`border`. Le `fillMaxSize()` de l'appelant
s'arrête désormais à l'hôte ; l'enfant, sans contrainte min héritée, peut
librement se réduire aux bounds de la carte.

### F23-R2 — résolu

L'hôte capture ses propres coordonnées (`onGloballyPositioned`). Une fonction
pure `internal fun localBounds(rootBounds: Rect, hostOriginInRoot: Offset): Rect`
traduit les bounds racine de la cible dans le repère local de l'hôte
(`rootBounds.translate(-hostOriginInRoot.x, -hostOriginInRoot.y)`), utilisée
par l'overlay avant de calculer `left`/`top`/`width`/`height`. Couvre le rail
TV et l'inset haut sans les nommer explicitement : n'importe quelle origine
d'hôte est prise en compte.

### F23-R3 — résolu

`TvFocusSelectorState.reportAxisStabilised(scope, coordinates, cornerRadius)`
remplace l'appel direct à `publishFrom` dans `tvPivotItem` (horizontal) et
`tvPivotSection` (vertical) : chaque rapport annule l'attente précédente et en
relance une nouvelle de `AXIS_SETTLE_FRAMES` (2) frames avant de publier
effectivement, avec la géométrie du dernier axe à avoir rapporté. Si l'autre
axe rapporte dans cette fenêtre, une seule publication a lieu. `tvPivotCell`
(grille, axe unique) continue de publier directement via `publishFrom`, sans
attente inutile.

### F23-R4 — résolu

`tvPivotItem` utilise désormais `onFocusedBoundsChanged` (comme `tvPivotCell`/
`tvPivotSection`) au lieu de `onGloballyPositioned`+`onFocusChanged` : il
mesure les bounds du descendant réellement focalisé, pas celles du `Box`
d'enveloppe (qui, pour une carte Top 10, inclut le grand chiffre). Pour la
Hero, la capture `onGloballyPositioned` est déplacée après le
`padding(horizontal = 8.dp)` de la slide, juste avant le `clip(shape)` de
`HomeTrendingSlideTv` — elle mesure donc la surface effectivement clippée, pas
la zone de page brute du pager.

### F23-R5 — résolu

- `HomeScreen.kt` : les rangées `home_resume` et `home_livetv` passent
  `selectorCornerRadius = 12.dp` / `16.dp` à `tvPivotItem`, correspondant au
  rayon réel de `HomeResumeWatchingCard`/`HomeLiveTvCard` (les autres rangées
  restent au défaut 14.dp, déjà correct). `LiveTvComponents.kt`/`LiveTvScreen.kt`
  passaient déjà 12.dp (fait à la Tâche 2).
- `tvFocusHighlight` ne court-circuite plus la bordure que lorsque
  `focused && LocalTvFocusSelector.current != null` : une carte non focalisée
  conserve sa bordure de repos (`restingColor`/`restingWidth`), y compris sous
  une couche avant active.
- `TvFocusSelectorOverlay` applique désormais le même `spring(dampingRatio =
  DampingRatioNoBouncy, stiffness = StiffnessMediumLow)` à `cornerRadius`,
  `left`, `top`, `width` et `height` qu'à `alpha`, au lieu du ressort par
  défaut d'`animateDpAsState`.

### F23-R6 — résolu

`localBounds` (F23-R2) est une fonction pure indépendante de Compose au-delà
des types géométriques : `TvFocusSelectorStateTest` ajoute trois cas (hôte à
l'origine → bounds inchangés, hôte décalé → traduction correcte avec un
exemple concret rail+inset, préservation de la largeur/hauteur). L'arbitrage
temporel de `reportAxisStabilised` (basé sur `withFrameNanos`, donc sur une
horloge de frame Compose) n'a en revanche pas été extrait en unité pure
testable en JVM dans le temps de cette session : le couvrir correctement
exigerait soit une horloge de frame de test (dépendance
`androidx.compose.ui.test` absente des tests unitaires actuels du projet),
soit une réécriture de la coordination en une machine à états pure avec effets
de bord injectés — jugé disproportionné pour cette correction. Limite
assumée et documentée plutôt que masquée.

### Vérifications après correction

- `./gradlew compileDebugKotlin` → réussi après chaque fichier modifié
  (`TvFocusSelector.kt`, `TvPivotScroll.kt`, `TvFocusHighlight.kt`,
  `HomeScreen.kt`, `HomeTrendingCarouselTv.kt`).
- `./gradlew testDebugUnitTest --tests com.cstv.app.presentation.components.TvFocusSelectorStateTest`
  → réussi (8 cas, dont les 3 nouveaux sur `localBounds`).

## Étape 8 — Validation finale (2026-08-02)

Status: VALIDATED

- Comportement attendu / règles métier : les six findings de la Review
  (Critique R1, Majeur R2-R4, Mineur R5-R6) sont corrigés et revérifiés par
  lecture du diff et compilation ; la limite assumée de R6 (arbitrage temporel
  non testé en JVM) est documentée ci-dessus, pas cachée.
- Qualité technique / absence de régression : `./gradlew compileDebugKotlin`
  réussi ; `./gradlew assembleDebug lintDebug` réussi, `0 errors` ; suite de
  tests exécutée en excluant `HomeViewModelTest`/`RecentlyAddedViewModelTest`
  (voir note ci-dessous) → 545 tests, 0 échec.
- Expérience utilisateur (immobilité perçue du cadre, fluidité du glissement,
  absence de rebond, superposition correcte à la carte sur Home/VOD/Séries/
  Direct/Hero) : **non vérifiée**. C'est précisément le comportement que les
  findings R1-R4 ciblaient, et sa nature visuelle/temporelle sur D-pad réel
  échappe structurellement aux critères de validation automatisés de l'agent
  (`AGENTS.md`). Une observation manuelle sur appareil/émulateur Android TV
  reste recommandée avant Release, en particulier pour confirmer que la
  fenêtre de 2 frames de R3 ne se perçoit pas comme un délai et que la
  traduction de repère de R2 fonctionne sur les quatre écrans réels (rail TV +
  inset variables selon le contexte de navigation).

### Note commune T10/B18/F23 : `HomeViewModelTest` bloque l'exécution complète

Dans cette session, `./gradlew testDebugUnitTest` sans filtre reste bloqué
indéfiniment. Bisection par filtre `--tests` (par paquet, par classe) :
le blocage est reproductible et déterministe sur la seule classe
`com.cstv.app.presentation.home.HomeViewModelTest` (et son voisin
`RecentlyAddedViewModelTest`), pas sur les 545 autres tests, qui passent tous
une fois cette classe exclue. `HomeViewModelTest.kt` documente lui-même
(commentaire l. 85-86, écrit avant cette session) que l'initialisation de
`HomeViewModel` démarre un sondage EPG (`while(true) { delay(60_000) ; ... }`)
sur un dispatcher non lié au scheduler virtuel de `runTest` — plusieurs tests
de ce fichier omettent volontairement `advanceUntilIdle()` pour cette raison
même. Aucun fichier de T10/B18/F23 ne touche `HomeViewModel.kt` : le blocage
est un caractère préexistant de l'environnement de test, pas une régression
introduite ici.

Le ticket passe de `REVIEW` à `VALIDATED`.

---

# 9. Release

Version:
v1.68.0

Commit:
✨ Release F23, B18 & T10: double-layer TV focus, card unification & row limiting

Date:
2026-08-02
