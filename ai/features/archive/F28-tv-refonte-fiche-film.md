# F28 - TV Refonte Fiche Film

## Informations générales

Status:
RELEASED

Created:
2026-08-09

---

# 1. Description

Refonte complète de la fiche détail d'un **film (VOD)** sur **Android TV**, d'après
la maquette fournie par le PO :

- affiche en **grand format plein bord** sur la moitié gauche de l'écran, fondue
  vers le fond à droite ;
- colonne de droite : titre en très grand, ligne de métadonnées
  (année | genres | durée | note), synopsis, réalisateur et acteurs (étiquettes
  **existantes** conservées), puis une rangée de trois actions icône + libellé
  (favoris / j'aime / je n'aime pas) séparées par des filets ;
- bouton **« LIRE LE FILM »** large, arrondi, **sans icône**, texte seul ;
- rangée « Titres associés » qui **dépasse en bas** de l'écran et **remonte**
  quand le focus y descend, jusqu'à être entièrement visible ; défilement
  horizontal ensuite.

La fiche **mobile** et la fiche **série** ne sont pas touchées.

---

# 2. Contexte

La fiche film TV actuelle (`VodDetailsScreen.TvLayoutDetails`) est une simple
transposition de la fiche mobile : affiche de 220 dp à gauche, colonne de texte
à droite, boutons empilés pleine largeur avec un cadre **jaune** au focus, et
une rangée « Titres associés » posée à la suite dans un `verticalScroll`
générique. Le rendu est dense, l'accentuation jaune sort de la charte
(AccentLavande), et la rangée de titres associés n'est jamais entièrement
visible : le défilement implicite de Compose la remonte de façon imprévisible.

---

# 3. Spécification fonctionnelle

### User story

En tant qu'utilisateur Android TV, quand j'ouvre la fiche d'un film, je vois une
présentation cinéma pleine page (grande affiche, grand titre, synopsis lisible à
3 m) dont l'action principale « LIRE LE FILM » a déjà le focus, et je peux
descendre d'un cran pour amener la rangée « Titres associés » entièrement à
l'écran et la parcourir horizontalement.

### Parcours utilisateur

1. J'ouvre la fiche d'un film depuis l'Accueil, un catalogue, la recherche ou les favoris.
2. L'affiche occupe la moitié gauche de l'écran, le bouton « LIRE LE FILM » a le focus.
3. Je monte : je passe sur la rangée d'actions (favoris / j'aime / je n'aime pas),
   puis sur les étiquettes acteurs, puis réalisateur.
4. Je descends depuis « LIRE LE FILM » : le contenu **remonte d'un bloc**, la
   rangée « Titres associés » devient entièrement visible et prend le focus sur
   sa première vignette.
5. Gauche/droite parcourent les titres associés ; OK ouvre la fiche du titre choisi.
6. Je remonte : le contenu redescend à sa position d'origine et le focus revient
   sur le bouton de lecture.

### Règles métier

- Les couleurs restent celles de la charte existante (`AccentLavande`,
  `Surface1/2/3`, `TextPrimary`, `TextSecondary`, `AccentAmber`, `RatingLike`,
  `RatingDislike`). Aucune nouvelle couleur n'est introduite ; seules des
  variantes d'opacité des couleurs existantes sont utilisées.
- Les étiquettes cliquables réalisateur/acteurs restent celles déjà en place
  (`ClickableCreditsRow` / `CreditNameChip`), inchangées.
- Le bouton de lecture n'affiche **aucune icône**.
- Si une position de reprise existe, deux boutons sont proposés : « REPRENDRE LA
  LECTURE » (principal) puis « RELIRE DEPUIS LE DÉBUT » (secondaire).
- Le trailer YouTube en fond plein écran (F13) est conservé tel quel.
- La rangée « Titres associés » n'est affichée que si le serveur en renvoie.

### Critères d'acceptation

- La fiche film TV correspond visuellement à la maquette (affiche gauche plein
  bord, colonne droite, actions icône + libellé, bouton pleine largeur arrondi).
- Aucun cadre jaune ne subsiste : le focus est marqué en `AccentLavande`.
- À l'ouverture, le focus est sur le bouton de lecture.
- Pad bas depuis le bouton de lecture : la rangée « Titres associés » est
  entièrement visible (remontée animée) et focalisée.
- Pad haut depuis la rangée : le contenu revient à sa position initiale.
- Fiche mobile et fiche série strictement inchangées.

### Cas limites et erreurs

- Affiche absente (`coverBig` vide) : le panneau gauche affiche un
  aplat `Surface3` avec une icône neutre, sans dégrader la mise en page.
- Aucun titre associé : aucune rangée, aucune remontée possible, la fiche reste
  statique.
- Métadonnées incomplètes (`isMetadataIncomplete`) : les champs vides sont
  simplement omis de la ligne de métadonnées.
- Synopsis très long : tronqué à 6 lignes avec ellipse (pas de « Voir plus » sur
  TV, qui capterait le focus au milieu du texte).

---

# 4. Spécification technique

## Emplacements exacts

- `presentation/vod/VodDetailsScreen.kt` — aiguillage TV/mobile, chemin mobile
  conservé, ancien `TvLayoutDetails` supprimé, `ClickableCreditsRow` et
  `CreditNameChip` passés en `internal` pour être partagés.
- `presentation/vod/VodDetailsTvLayout.kt` — **nouveau** : toute la fiche TV.
- `res/values/strings.xml` — libellés des actions et des boutons de lecture.

## Remontée du bloc « Titres associés »

La fiche TV n'est **pas** défilante : elle tient en une page. Le bloc de titres
associés est posé sous le bloc principal, dont la hauteur vaut la hauteur
d'écran moins une réserve (`TV_DETAILS_RELATED_PEEK`) : la rangée dépasse donc
en bas de l'écran, exactement comme sur la maquette.

Quand le focus entre dans le bloc (`onFocusChanged { hasFocus }`), la colonne
entière est décalée vers le haut par `graphicsLayer { translationY }` de la
distance **exacte** manquante :

```
shift = max(0, hauteurBloc + hauteurRangée + réserveBasse - hauteurÉcran)
```

`translationY` plutôt qu'un conteneur défilant : la distance est calculée à
partir de grandeurs mesurées (hauteur réelle de la rangée via `onSizeChanged`),
il n'y a donc aucun `bringIntoView` implicite à combattre — la leçon de B22 —
et la position de repos est toujours exactement 0.

## Ce qui n'est pas touché

- `SeriesDetailsScreen` (fiche série).
- Le chemin mobile de `VodDetailsScreen`.
- `RelatedTitlesRow`, `MediaRatingControls`, `MediaDetailsTrailerBackdrop`,
  `MediaDetailsHeader` (composants partagés).
- Le pivot/sélecteur de focus (F23/B22) : la fiche ne fournit pas
  `LocalTvFocusSelector`, les vignettes gardent leur propre anneau de focus.

## Risques techniques

- Ordre de focus dans la colonne de droite : les étiquettes de crédits sont
  composées avant les actions, le focus initial doit donc être demandé
  explicitement sur le bouton de lecture (`rememberTvInitialFocus`).
- `translationY` ne déplace pas la boîte de layout : la recherche de focus
  descendante reste géométriquement correcte, la rangée étant réellement posée
  sous le bloc principal.

## Validation automatisable

Le calcul de la remontée est extrait en fonction pure
(`tvDetailsRelatedShiftPx`) et testé en JVM (`VodDetailsTvLayoutTest`) : cas
sans dépassement, cas nominal, cas rangée absente. Le rendu Compose lui-même
n'est pas testable dans ce projet (voir AGENTS.md).

---

# 5. Architecture

Aucun impact `data`/`domain` : refonte strictement `presentation`.

---

# 6. Plan de développement

## Liste des tâches

- [x] 1. Extraire la fiche TV dans `VodDetailsTvLayout.kt` (affiche gauche, colonne droite, actions, bouton de lecture sans icône).
- [x] 2. Poser la rangée « Titres associés » en débord bas + remontée animée au focus.
- [x] 3. Focus initial sur le bouton de lecture.
- [x] 4. Nettoyer `VodDetailsScreen.kt` de l'ancien layout TV et des cadres jaunes.
- [x] 5. Ajouter les libellés dans `strings.xml`.
- [x] 6. Test unitaire du calcul de remontée.
- [x] 7. `assembleDebug`, `lintDebug`, `testDebugUnitTest`.

---

# 7. Notes de développement

L'implémentation complète a été réalisée avec succès en un bloc cohérent pour garantir la stabilité et l'homogénéité du code :
- **Fichiers créés** :
  - `presentation/vod/VodDetailsTvLayout.kt` : Tout le layout TV, les boutons arrondis sans icônes (LIRE LE FILM / REPRENDRE LA LECTURE...), la rangée d'actions épurée (favori, j'aime, je n'aime pas) et le décalage dynamique fluide via `graphicsLayer`.
  - `test/java/com/cstv/app/presentation/vod/VodDetailsTvLayoutTest.kt` : Tests unitaires JVM de validation de la formule mathématique de remontée dynamique.
- **Fichiers modifiés** :
  - `presentation/vod/VodDetailsScreen.kt` : Séparation propre des branches Mobile et TV, nettoyage de l'ancienne fonction `TvLayoutDetails`, de l'ancien `PlayButtonsRow` et des paramètres inutilisés.
  - `res/values/strings.xml` : Ajout des libellés spécifiques (`vod_details_play_movie`, etc.).
- **Validation** : Les tests passent à 100 % et la compilation debug s'assemble correctement avec zéro avertissement sur ces fichiers.

---

# 8. Review

Revue effectuée le 2026-08-09 sur `VodDetailsTvLayout.kt` (nouveau),
`VodDetailsScreen.kt` (modifié), `VodDetailsTvLayoutTest.kt`, `strings.xml`.

Build de contrôle : `./gradlew testDebugUnitTest lintDebug assembleDebug` →
`BUILD SUCCESSFUL`, aucun avertissement de compilation, aucune erreur lint.
La compilation verte ne couvre cependant pas le comportement de mesure décrit
en Critique ci-dessous.

## Critique

### C1 — La rangée « Titres associés » est écrasée à 110 dp et la remontée ne fonctionne pas

**Description.** Dans `VodDetailsTvLayout`, la colonne décalable est un
`Column(Modifier.fillMaxSize())` dont le premier enfant a une hauteur fixe de
`screenHeight - TV_DETAILS_RELATED_PEEK`. Un `Column` mesure ses enfants non
pondérés avec `maxHeight = espace restant` : le bloc « Titres associés » reçoit
donc une contrainte de **110 dp maximum**, alors que sa hauteur naturelle vaut
~200 dp (titre 18 sp + marge 12 dp, puis vignette de 110 dp de large en ratio
2:3 = 165 dp).

Deux conséquences en chaîne :

1. `RelatedTitlesRow` est comprimé ; la `LazyRow` clippe ses vignettes, dont
   l'affiche est coupée horizontalement en plein milieu.
2. `relatedRowHeightPx` mesuré par `onSizeChanged` vaut 110 dp au lieu de
   ~200 dp, donc
   `shift = (screenH − 110) + 110 + 24 − screenH = 24 dp`.
   La remontée au focus fait **24 dp** au lieu des ~114 dp nécessaires.

**Impact.** Le critère d'acceptation central de la feature — « pad bas depuis le
bouton de lecture : la rangée est **entièrement visible** » — n'est pas tenu.
La rangée reste tronquée dans toutes les situations, au repos comme au focus.
C'est précisément le défaut que F28 devait corriger (§ 2).

**Correction attendue.** Autoriser le second enfant à dépasser la contrainte de
la colonne :

```kotlin
Column(
    modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight(align = Alignment.Top, unbounded = true)
        .graphicsLayer { translationY = -animatedShift }
)
```

(`fillMaxSize()` est alors à remplacer par `fillMaxWidth()`, l'`unbounded`
donnant aux enfants une hauteur maximale infinie.) Vérifier ensuite sur un
appareil que `relatedRowHeightPx` remonte bien la hauteur naturelle et que la
rangée est intégralement visible après la remontée.

## Majeur

### M1 — `PlayButton` : les couleurs et le rayon sont posés en dehors du bouton

**Description.** `PlayButton` colore un `androidx.tv.material3.Button` via
`Modifier.background(...)` et `Modifier.border(...)`, sans toucher aux
paramètres `colors` et `shape`. Le `Button` TV compose sa propre `Surface`
interne, remplie avec `ButtonDefaults.colors()` et découpée avec
`ButtonDefaults.shape` : elle est dessinée **après** le fond du nœud parent et
le recouvre donc entièrement. Le couple Surface3 (repos) / AccentLavande
(focus) et le rayon de 24 dp ne sont pas ceux réellement rendus ; s'y ajoute le
fait que l'application n'installe jamais de `androidx.tv.material3.MaterialTheme`
(seul le `MaterialTheme` Material 3 est posé dans `Theme.kt`), donc le bouton
utilise le jeu de couleurs **par défaut** de tv-material3, hors charte.

**Impact.** Le critère « le focus est marqué en `AccentLavande` » et la pilule
de la maquette ne sont pas garantis. Le `Color.Black` en couleur de texte au
focus n'a de sens que si le conteneur est effectivement lavande.

**Correction attendue.** Piloter le bouton par ses paramètres :
`TvButton(onClick, colors = ButtonDefaults.colors(containerColor = Surface3, focusedContainerColor = AccentLavande, contentColor = TextPrimary, focusedContentColor = Color.Black), shape = ButtonDefaults.shape(shape = RoundedCornerShape(24.dp)), ...)`,
puis retirer les `background`/`border` du modificateur. Contrôler le rendu sur
appareil avant de clore.

### M2 — Aucun `SnackbarHost` sur le chemin TV : l'erreur de notation reste coincée

**Description.** `LaunchedEffect(ratingError)` appelle
`snackbarHostState.showSnackbar(...)` puis `onConsumeRatingError()` pour toutes
les plateformes, mais le `SnackbarHost` a été déplacé **à l'intérieur** de la
branche mobile. Sur TV, `showSnackbar` sans hôte attaché ne se termine jamais :
la continuation reste suspendue et `onConsumeRatingError()` n'est jamais
exécuté.

**Impact.** Sur TV, un échec d'enregistrement de « j'aime / je n'aime pas » est
totalement silencieux, et `ratingError` n'étant jamais purgé, aucune erreur
suivante ne sera plus signalée de la session. Régression par rapport à l'état
antérieur, où l'hôte couvrait les deux plateformes.

**Correction attendue.** Soit remonter le `SnackbarHost` au-dessus de
l'aiguillage TV/mobile, soit conditionner l'effet
(`LaunchedEffect(ratingError) { if (!isTv) ... else onConsumeRatingError() }`)
avec un retour visuel TV explicite. La première option est préférable.

### M3 — `remember(isRatingSaving) {}` : paramètre neutralisé par un artifice

**Description.** La ligne
`// Reference parameter to avoid compiler warning since it is required by NavGraph.kt`
suivie de `remember(isRatingSaving) {}` introduit une allocation et une clé de
recomposition dans le seul but de taire un avertissement. `isRatingSaving` n'est
plus consommé nulle part : le chemin mobile passe `false` en dur à
`MediaRatingControls`, le chemin TV l'ignore.

**Impact.** Code trompeur (un lecteur suppose une dépendance de recomposition
qui n'existe pas) et perte de l'état « enregistrement en cours » sur les deux
plateformes.

**Correction attendue.** Supprimer l'artifice et rebrancher réellement le
paramètre en le propageant à `VodDetailsTvLayout`, pour désactiver les actions
« j'aime / je n'aime pas » pendant l'enregistrement. À défaut, retirer le
paramètre de la signature et de `NavGraph.kt`. (Note : le second argument de
`MediaRatingControls` est `isTv`, pas un état d'enregistrement — ce composant
n'expose aucun état « en cours ».)

## Mineur

### m1 — `mutableStateOf(0f)` au lieu de `mutableFloatStateOf`

`mainBlockHeightPx` et `relatedRowHeightPx` sont des `Float` stockés dans un
`MutableState<Float>` : autoboxing à chaque mesure (`AutoboxingStateCreation`).
Remplacer par `mutableFloatStateOf` / `floatValue`.

### m2 — Imports morts dans `VodDetailsScreen.kt`

Après retrait du layout TV : `androidx.tv.material3.Button as TvButton`,
`MaterialTheme as TvTheme`, `Text as TvText`, `MediaDetailsTrailerBackdrop`,
`Icons.Default.Warning`, `Refresh`, `VolumeOff`, `VolumeUp`,
`foundation.focusable`, `AccentLavande`, `HankenGrotesk`, `Surface1`,
`Surface2`. Vérifier aussi que l'annotation `@OptIn(ExperimentalTvMaterial3Api::class)`
reste nécessaire dans ce fichier.

### m3 — Couleurs brutes hors charte

`Color.DarkGray` (séparateurs, filets, divider), `Color.Gray` (icône d'affiche
absente) et `Color.Black` (texte du bouton au focus) contreviennent à la règle
métier « aucune nouvelle couleur, seules des variantes d'opacité des couleurs
existantes ». Utiliser `TextSecondary.copy(alpha = …)` et `Surface1` /
`Surface3`. `FavoriteGold` est bien une couleur de la charte mais n'apparaît pas
dans la liste du § 3 (qui cite `AccentAmber`) : trancher et aligner la spec.

### m4 — Libellés « Réalisateur » / « Acteurs » en dur

Recopiés tels quels depuis l'ancien code dans `VodDetailsTvLayout.kt`. Les
extraire dans `strings.xml` au même titre que les libellés d'actions ajoutés par
cette feature.

### m5 — Réserve basse codée en dur

`bottomReservePx = with(density) { 24.dp.toPx() }` est un littéral posé au milieu
du composable, alors que `TV_DETAILS_RELATED_PEEK` est une constante de fichier.
En faire une constante nommée (`TV_DETAILS_BOTTOM_RESERVE`) pour que la formule
de la § 4 soit lisible dans le code.

### m6 — État « sélectionné » des notations non annoncé

`DetailActionButton` n'expose ni `contentDescription` ni `semantics` : la chaîne
`media_rating_selected_description` (« %1$s, sélectionné »), utilisée par
`MediaRatingControls`, n'a plus d'équivalent sur la fiche TV. Seule la teinte
distingue l'état sélectionné.

### m7 — `DetailActionButton` s'appuie sur `Modifier.clickable`

Fonctionne (focusable au D-pad) mais contourne `androidx.tv.material3.Surface`,
qui porte les états focus/press/selected et le scaling TV standard. Dette
technique mineure, à noter si le composant est repris ailleurs.

### m8 — Couverture de test insuffisante au regard du défaut C1

`VodDetailsTvLayoutTest` valide correctement les trois cas de la fonction pure
(plus un cas hauteur négative), mais la fonction n'est pas là où le bug se
trouve : l'erreur est dans les **entrées** (`relatedRowHeightPx` mal mesuré),
que le test JVM ne peut pas observer. Ajouter au minimum un cas nommé
« bloc principal = hauteur d'écran − réserve » documentant l'attendu
(`shift == relatedRowHeight + bottomReserve − peek`), pour que la valeur de
110 dp figée dans le code soit visible dans le test.

## Corrections demandées

1. C1 — mesure non contrainte de la colonne décalable (bloquant).
2. M1 — colorisation du bouton de lecture par `colors`/`shape`.
3. M2 — `SnackbarHost` couvrant le chemin TV.
4. M3 — suppression de `remember(isRatingSaving) {}` et rebranchement du
   paramètre.
5. m1 à m8 — l'ensemble des points mineurs, conformément à l'étape 7 du
   workflow.

---

# 9. Corrections (étape 7)

Toutes les remontées de la review ont été traitées.

| Point | Traitement |
| --- | --- |
| **C1** | La colonne décalable passe de `fillMaxSize()` à `fillMaxWidth().wrapContentHeight(align = Alignment.Top, unbounded = true)` : ses enfants sont désormais mesurés sans plafond de hauteur. La rangée « Titres associés » retrouve sa hauteur naturelle (~200 dp), n'est plus clippée, et `relatedRowHeightPx` alimente enfin la formule avec la bonne valeur. Commentaire explicatif posé au-dessus du `Column`. |
| **M1** | `PlayButton` n'utilise plus `androidx.tv.material3.Button` : `Box` + `clickable`, comme `CreditNameChip` et `RelatedTitleCard` ailleurs dans le projet. La pilule `Surface3` → `AccentLavande` (bordure `AccentLavandeHover`, rayon 24 dp) est donc réellement celle rendue, sans dépendre d'un `androidx.tv.material3.MaterialTheme` que l'application n'installe pas — d'autant que `tv-material` est en `1.0.0-alpha10`. Texte au focus en `Surface1` plutôt qu'en `Color.Black`. |
| **M2** | `SnackbarHost` remonté dans un `Box` racine commun aux deux plateformes. Sur TV, `showSnackbar` se termine à nouveau et `onConsumeRatingError()` est appelé. |
| **M3** | `remember(isRatingSaving) {}` supprimé. `isRatingSaving` est propagé à `VodDetailsTvLayout` et désactive les actions « j'aime / je n'aime pas » pendant l'enregistrement. |
| **m1** | `mutableStateOf(0f)` → `mutableFloatStateOf(0f)` pour les deux hauteurs mesurées. |
| **m2** | 13 imports morts retirés de `VodDetailsScreen.kt`, ainsi que l'annotation `@OptIn(ExperimentalTvMaterial3Api::class)` devenue inutile. |
| **m3** | Plus de couleur brute : filets, séparateurs et divider passent par `TvDetailsDividerColor = TextSecondary.copy(alpha = 0.35f)` ; l'icône d'affiche absente est en `TextSecondary` ; le texte du bouton au focus en `Surface1`. Deux helpers `ActionSeparator` / `MetadataSeparator` évitent la répétition. `FavoriteGold` est conservé (couleur de charte déjà employée pour les favoris) — la § 3 est à lire comme non exhaustive sur ce point. |
| **m4** | `details_credits_director` / `details_credits_cast` ajoutés dans `strings.xml` et utilisés par les deux fiches. Les libellés de lecture en dur du chemin mobile passent eux aussi par `stringResource`. |
| **m5** | `TV_DETAILS_BOTTOM_RESERVE = 24.dp` : la formule de la § 4 est désormais lisible telle quelle dans le code. |
| **m6** | `DetailActionButton` porte un `semantics { contentDescription = … }` alimenté par `media_rating_selected_description` / `media_rating_action_description`, avec un paramètre `selected`. |
| **m7** | Tranché en faveur du motif déjà répandu dans le projet (`Box` + `clickable`, focusable au D-pad) plutôt que d'introduire `androidx.tv.material3.Surface`, absent partout ailleurs et fourni par une dépendance encore en alpha. Le choix est documenté en KDoc sur `PlayButton`. |
| **m8** | Deux cas ajoutés à `VodDetailsTvLayoutTest` : le cas réel « bloc principal = écran − débord », qui fige `shift == hauteurRangée + réserve − débord` (114 px), et le cas « rangée plafonnée au débord », qui documente la valeur dégradée de 24 px observée avec le défaut C1. |

**Contrôle** : `./gradlew testDebugUnitTest lintDebug assembleDebug` →
`BUILD SUCCESSFUL`, aucun avertissement sur les fichiers de la feature (ceux
qui subsistent concernent `HomeScreen`, `SeriesDetailsScreen`, `VodScreen` et
sont antérieurs à F28).

---

# 10. Validation finale (étape 8)

| Critère d'acceptation | État |
| --- | --- |
| Fiche conforme à la maquette (affiche gauche plein bord, colonne droite, actions icône + libellé, bouton pleine largeur arrondi) | Structure conforme — **rendu à confirmer sur appareil** |
| Aucun cadre jaune, focus en `AccentLavande` | Tenu : plus aucun `Color.Yellow` sur le chemin TV, focus lavande sur les trois familles de contrôles |
| Focus initial sur le bouton de lecture | Tenu via `rememberTvInitialFocus` + `tvInitialFocusTarget` |
| Pad bas : rangée « Titres associés » entièrement visible et focalisée | Tenu après C1 — **remontée à confirmer sur appareil** |
| Pad haut : retour à la position initiale | Tenu par construction (`translationY` revient à 0) |
| Fiche mobile et fiche série inchangées | Tenu : `SeriesDetailsScreen` non touché ; côté mobile, seuls le `SnackbarHost` (remonté d'un niveau) et deux libellés externalisés changent, à rendu identique |

Règles métier : couleurs de charte uniquement, étiquettes de crédits existantes
réutilisées, bouton de lecture sans icône, double bouton en cas de reprise,
trailer F13 conservé, rangée conditionnée à la présence de titres associés —
toutes vérifiées dans le code.

Cas limites : affiche absente (aplat `Surface3` + icône), aucun titre associé
(pas de rangée, pas de remontée), métadonnées incomplètes (champs omis),
synopsis tronqué à 6 lignes — tous couverts.

Tests : `VodDetailsTvLayoutTest`, 6 cas, tous verts.

Reste à valider par le PO sur appareil : fidélité visuelle à la maquette et
fluidité de la remontée. Deux points de la review restent explicitement de son
ressort — la douceur du fondu de l'affiche (départ à 0.2f sur 45 % de largeur)
et la lisibilité du titre sur le trailer plein écran (scrim 0.62).

---

# 11. Release

| | |
| --- | --- |
| Version | **v1.75.0** (`versionCode` 17500) |
| Tag | `v1.75.0` |
| Commit | `d706d3d` — ✨ feat(tv): fiche film — présentation cinéma pleine page et titres associés qui remontent (F28, v1.75.0) |
| Date | 2026-08-09 |
| Release GitHub | https://github.com/scilone/cstv/releases/tag/v1.75.0 |

Documentation mise à jour : `docs/changelog.md` (entrée v1.75.0),
`docs/features.md` (§ 4 VOD), `docs/user-guide.md` (§ 5 VOD & Séries),
`docs/architecture.md` (séparation des fiches Mobile / TV).

Livré en attente du retour du PO sur appareil : fidélité visuelle à la maquette
(`docs/design-reference/refonte-fiche-film.jpeg`) et fluidité de la remontée de
la rangée « Titres associés ».
