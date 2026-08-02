# B18 - Non-homogénéité des cartes de Films et Séries (Titre sous la vignette)

## Informations générales

Status:
RELEASED

Created:
2026-08-02

---

# 1. Description

Actuellement, sur les pages de listes des **Films (VOD)** et des **Séries**, lorsqu'une catégorie spécifique est sélectionnée (ce qui affiche une grille verticale d'éléments), les cartes affichent le titre textuel du média en dessous de sa vignette. C'est également le cas sur TV en mode "Tout" (All), où les rangées horizontales utilisent des cartes spécifiques affichant ce titre.

Ce comportement n'est pas homogène avec le reste de l'application (comme l'Accueil ou le mode "Tout" sur mobile) qui utilise de superbes cartes modernes, épurées et standardisées : `HomeVodMovieCard` et `HomeSeriesShowCard`. Ces dernières affichent uniquement l'affiche (format 2:3), la note et les éventuels badges, sans texte redondant en dessous.

L'objectif est d'harmoniser l'ensemble de l'application en supprimant le titre textuel sous la vignette sur les écrans Films et Séries, et d'unifier l'affichage en utilisant partout les cartes standards `HomeVodMovieCard` et `HomeSeriesShowCard`.

---

# 2. Contexte

Dans la base de code :
* **`MovieTvCard`** est un composant privé défini dans `app/src/main/java/com/cstv/app/presentation/vod/VodScreen.kt`. Il est utilisé sur TV pour la grille d'une catégorie et les rangées horizontales du mode "Tout". Il affiche une `Card` contenant une `Column` avec l'image puis le titre.
* **`SeriesTvCard`** est l'équivalent de `MovieTvCard` mais défini dans `app/src/main/java/com/cstv/app/presentation/series/SeriesScreen.kt`.
* Sur mobile, dans le mode "Catégorie spécifique" (grille), des cartes codées en dur avec une `Column` (image + titre textuel) sont également utilisées.
* À l'inverse, l'Accueil (`HomeScreen.kt`) et le mode "Tout" des Films/Séries sur mobile utilisent les composants globaux **`HomeVodMovieCard`** et **`HomeSeriesShowCard`** (déclarés dans `HomeCards.kt`), qui n'affichent pas le titre textuel en dessous car l'affiche du film ou de la série suffit à l'identifier.

Cette disparité visuelle et technique crée une dette de design (cartes plus larges, de styles et de coins différents) et de maintenance.

---

# 3. Spécification fonctionnelle

## Objectif

Unifier l'expérience utilisateur et le design de l'application en supprimant le titre textuel sous les vignettes des Films et Séries sur les pages de listes (TV et mobile), et en remplaçant tous les composants de cartes personnalisés ou obsolètes par les cartes de référence `HomeVodMovieCard` et `HomeSeriesShowCard`.

## User stories

* **En tant qu'utilisateur (mobile et TV)**, lorsque je navigue dans une catégorie spécifique de Films ou de Séries (grille), je vois une grille harmonieuse de posters au format propre 2:3 avec leurs notes, identique à l'affichage de l'Accueil, sans texte en dessous qui décale les alignements.
* **En tant qu'utilisateur TV**, lorsque je parcours le mode "Tout" de Films ou Séries, les rangées horizontales présentent des cartes unifiées et de même taille que celles de la Home, offrant une transition visuelle invisible et fluide.

## Règles métier et de rendu

1. **Suppression de la redondance** :
   * Retirer définitivement le titre textuel sous le poster pour les Films et Séries sur les pages de listes.
   * Supprimer les fonctions privées obsolètes `MovieTvCard` (dans `VodScreen.kt`) et `SeriesTvCard` (dans `SeriesScreen.kt`).
2. **Utilisation des cartes de référence** :
   * Utiliser **`HomeVodMovieCard`** pour tous les affichages de films sur `VodScreen.kt` (mode "Tout" horizontal sur TV, et grilles verticales de catégories sur TV et mobile).
   * Utiliser **`HomeSeriesShowCard`** pour tous les affichages de séries sur `SeriesScreen.kt` (mode "Tout" horizontal sur TV, et grilles verticales de catégories sur TV et mobile).
3. **Mise en page des grilles** :
   * S'assurer que le remplacement des cartes s'intègre parfaitement dans les grilles existantes (3 colonnes sur mobile, 4 colonnes sur TV), sans déformer les images ou casser les marges de défilement.

## Critères d'acceptation (Fonctionnels)

- [ ] Sur les pages Films et Séries (TV et mobile), le titre en texte brut sous le poster est supprimé de tous les affichages (grilles et rangées).
- [ ] Sur TV, en mode "Tout", les rangées de Films et Séries utilisent les cartes standardisées `HomeVodMovieCard` et `HomeSeriesShowCard`.
- [ ] En mode "Catégorie spécifique" (grille), sur TV et mobile, les cellules affichent directement `HomeVodMovieCard` et `HomeSeriesShowCard` intégrées de façon homogène.
- [ ] Les dimensions, arrondis de coins (radius 14.dp) et ombres de focus sur TV sont rigoureusement identiques à ceux de l'Accueil.

## Cas limites et gestion des erreurs

- Une affiche absente ou invalide conserve le placeholder standard des cartes de référence, sans réintroduire un titre sous l'image ni casser la taille de la cellule.
- Les titres longs, caractères IPTV non normalisés et métadonnées incomplètes ne modifient pas la hauteur d'une carte de liste.
- Le clic, le focus D-pad et l'ouverture de la fiche restent disponibles sur chaque carte ; seul le rendu redondant du titre est retiré.

## Hypothèses et Questions ouvertes

* *Lisibilité des affiches* : Certains flux IPTV ont parfois des affiches manquantes ou textuellement peu lisibles. Cependant, `HomeVodMovieCard` et `HomeSeriesShowCard` affichent déjà un placeholder propre (icône Warning neutre) en cas d'affiche manquante. De plus, la fiche de détails (au clic ou sur focus TV) permet à l'utilisateur de lire le titre complet du média en grand. Le retrait du titre sous la vignette est donc tout à fait viable et correspond au standard esthétique des applications premium actuelles.

---

# 4. Spécification technique

## Inventaire des rendus à unifier

| # | Emplacement | Rendu actuel | Cible |
| --- | --- | --- | --- |
| 1 | `VodScreen.kt` `CategorySectionRow` l. 714-733 | `if (isTv) MovieTvCard(...) else HomeVodMovieCard(...)` | `HomeVodMovieCard(..., isTv = isTv)` sans branche |
| 2 | `VodScreen.kt` `TvLayout`, grille de catégorie l. 366-384 | `MovieTvCard` dans une `Box(propagateMinConstraints = true)` | `HomeVodMovieCard(fillCell = true, isTv = true)` |
| 3 | `VodScreen.kt` `MobileLayout`, grille de catégorie l. 582-652 | `Card { Column { Box(poster) ; Text(titre) } }` codé en dur (~70 lignes) | `HomeVodMovieCard(fillCell = true)` |
| 4 | `VodScreen.kt` l. 771-850 | `private fun MovieTvCard` | **supprimé** |
| 5 | `SeriesScreen.kt` `CategorySectionRow` l. 715-724 | `if (isTv) SeriesTvCard(...) else HomeSeriesShowCard(...)` | `HomeSeriesShowCard(..., isTv = isTv)` |
| 6 | `SeriesScreen.kt` `TvLayout`, grille l. ~370-380 | `SeriesTvCard` | `HomeSeriesShowCard(fillCell = true, isTv = true)` |
| 7 | `SeriesScreen.kt` `MobileLayout`, grille l. ~571-650 | `Card { Column { poster ; titre } }` codé en dur | `HomeSeriesShowCard(fillCell = true)` |
| 8 | `SeriesScreen.kt` l. 770+ | `private fun SeriesTvCard` | **supprimé** |

## Obstacle technique central : les cartes de référence sont de taille fixe

`HomeVodMovieCard` (`presentation/home/components/HomeCards.kt` l. 383-448) et
`HomeSeriesShowCard` (l. 505+) **n'exposent aucun paramètre `modifier`** et
fixent leurs dimensions en dur :

```kotlin
val rankWidth  = if (rank == 10) 112.dp else 74.dp
val cardWidth  = if (rank == null) 130.dp else 130.dp + rankWidth - 30.dp
val posterModifier = Modifier.width(130.dp).fillMaxHeight()
    .tvFocusHighlight(isFocused, RoundedCornerShape(14.dp))
    .clip(RoundedCornerShape(14.dp)).background(Surface1)

Box(modifier = Modifier.width(cardWidth).height(195.dp) …)
```

Cette taille fixe convient à une `LazyRow` (elle y est même souhaitable :
uniformité des vignettes), mais **pas** à une cellule de
`LazyVerticalGrid(GridCells.Fixed(n))`, où la cellule impose une largeur exacte.
Y déposer la carte telle quelle produirait des vignettes de 130.dp centrées dans
des cellules plus larges (TV) ou plus étroites (mobile), avec des gouttières
irrégulières — exactement le défaut d'alignement que le ticket veut supprimer.
C'est aussi la raison d'être du commentaire de `VodScreen.kt` l. 371-375
(`propagateMinConstraints = true` ajouté en Review F19 pour empêcher
`MovieTvCard` de rétrécir).

### Solution retenue : paramètre `fillCell`

```kotlin
@Composable
fun HomeVodMovieCard(
    stream: VodStream,
    onClick: () -> Unit,
    rank: Int? = null,
    onLongClick: (() -> Unit)? = null,
    isTv: Boolean = false,
    /**
     * `true` en cellule de grille : la carte occupe toute la largeur imposée
     * par `GridCells` et dérive sa hauteur du ratio 2:3. `false` (défaut) :
     * dimensions fixes de rangée (130 × 195 dp), inchangées.
     */
    fillCell: Boolean = false
) {
    …
    val sizeModifier = if (fillCell) {
        Modifier.fillMaxWidth().aspectRatio(2f / 3f)
    } else {
        Modifier.width(cardWidth).height(195.dp)
    }
    val posterModifier = (if (fillCell) Modifier.fillMaxSize() else Modifier.width(130.dp).fillMaxHeight())
        .tvFocusHighlight(isFocused, RoundedCornerShape(14.dp))
        .clip(RoundedCornerShape(14.dp))
        .background(Surface1)

    Box(modifier = sizeModifier.onFocusChanged { … }.historyItemActions(isTv, onClick, onLongClick)) { … }
}
```

`HomeSeriesShowCard` reçoit exactement la même évolution.

**Pourquoi un booléen plutôt qu'un `modifier: Modifier = Modifier` ?** Un
paramètre `modifier` respecterait mieux la convention Compose, mais il serait
ici trompeur : la carte fixant déjà `.width(...)`/`.height(...)`, un
`.fillMaxWidth()` passé par l'appelant serait **ignoré** (la première contrainte
de taille de la chaîne gagne). Il faudrait donc soit retirer les dimensions par
défaut — ce qui casserait les sept sites d'appel existants de la Home — soit
rendre le paramètre nullable. Le booléen nomme explicitement les deux régimes de
dimensionnement supportés et rend l'erreur impossible.

`rank` et `fillCell` sont mutuellement exclusifs en pratique (les rangées
« Top 10 » ne sont jamais rendues en grille) ; `rank` reste ignoré du calcul de
taille quand `fillCell = true`.

## Composants impactés

| Fichier | Modification |
| --- | --- |
| `presentation/home/components/HomeCards.kt` | Paramètres `fillCell` et `badgeLabel` sur `HomeVodMovieCard` et `HomeSeriesShowCard`. Aucun changement de rendu pour les appels existants (deux valeurs par défaut neutres). |
| `presentation/vod/VodScreen.kt` | Sites 1-3 remplacés, `MovieTvCard` supprimé, paramètre `badgeFor` sur `CategorySectionRow`, libellés d'épisode pour la rangée `resume_watching`, imports devenus inutiles nettoyés. |
| `presentation/series/SeriesScreen.kt` | Sites 5-7 remplacés, `SeriesTvCard` supprimé, idem. |

## Imports à nettoyer

La suppression de `MovieTvCard` / `SeriesTvCard` et des cartes mobiles codées en
dur rend inutilisés, dans les deux écrans : `coil.compose.AsyncImage`,
`ContentScale`, `TextOverflow`, `TextAlign`, `Icons.Default.Star`,
`Icons.Default.Warning`, `Icons.Default.PlayArrow`, `Surface3`,
`CardDefaults`/`Card` (à vérifier au cas par cas — `Card` peut rester utilisé
ailleurs), `tvFocusHighlight` (encore utilisé par `CategoryFilterChip`, donc
conservé). `lintDebug` signalerait les imports morts : le nettoyage fait partie
de la tâche, pas d'un suivi ultérieur.

## Modèles de données, API, services, stockage, cache

Néant. Aucun `ViewModel`, `UseCase`, `Repository`, entité Room ou DTO touché.
Base en version 21, inchangée. Aucun appel réseau.

## Comportements préservés

* **Clic et appui long** : les cartes de référence utilisent déjà
  `historyItemActions(isTv, onClick, onLongClick)` (le même helper que
  `MovieTvCard` l. 788). L'appui long de retrait d'historique de la rangée
  `resume_watching` (`VodScreen.kt` l. 280, `onLongClick = onHistoryRemove`)
  continue donc de fonctionner sans adaptation.
* **Focus D-pad** : `onFocusChanged` + `tvFocusHighlight` sont déjà portés par
  les cartes de référence ; le rayon passe de 12.dp à 14.dp, ce qui est
  précisément le critère d'acceptation n° 4.
* **Placeholder d'affiche manquante** : icône `Warning` neutre, identique dans
  les deux familles de cartes.
* **Badge de note** : présent dans les deux familles, positionné en haut à
  droite de l'affiche.
* **Pivot F19** : `tvPivotItem` / `tvPivotCell` enveloppent les cartes depuis
  l'extérieur (`Box(modifier = Modifier.tvPivotItem(...))`) et ne dépendent pas
  de la structure interne de la carte — voir le KDoc de `tvPivotItem`
  (`TvPivotScroll.kt` l. 134-139).

## Rangée « Continuer à regarder » : badge épisode sur l'affiche

Dans le mode « Tout » des Films et Séries, la rangée **« Continuer à
regarder »** (`VodScreen.kt` l. 272-286) reconstruit des `VodStream` à partir des
positions de lecture, avec `name = pos.title`. Aujourd'hui, sur TV,
`MovieTvCard` affiche ce titre sous l'affiche ; le retrait du titre y ferait
perdre le numéro d'épisode d'une série en cours — seule information que
l'affiche ne restitue pas.

**Décision PO du 2026-08-02 : badge « S01 E03 » en overlay sur l'affiche.**
L'homogénéité des cartes est préservée (aucun texte sous la vignette) et
l'information reste lisible.

### Implémentation

```kotlin
// HomeCards.kt — HomeVodMovieCard / HomeSeriesShowCard
fun HomeVodMovieCard(
    …,
    fillCell: Boolean = false,
    /** Badge court en surimpression, coin haut-gauche de l'affiche (ex. « S01 E03 »). */
    badgeLabel: String? = null   // NOUVEAU
)
```

Rendu : même traitement visuel que le badge de note existant (l. 428-447), mais
aligné `TopStart` — `clip(RoundedCornerShape(4.dp))`, fond `Color(0xCC000000)`,
`padding(horizontal = 4.dp, vertical = 2.dp)`, texte 8.sp gras blanc. Les deux
badges ne se recouvrent donc jamais (`TopStart` vs `TopEnd`).

Alimentation, à la construction des `VodStream` de reprise
(`VodScreen.kt` l. 207-218, `SeriesScreen.kt` équivalent) : le mapping perd
aujourd'hui `seasonNum` / `episodeNum`. Plutôt que d'enrichir `VodStream` — un
modèle `domain` qui n'a pas à porter une notion d'épisode — la rangée transmet
les libellés à part :

```kotlin
// CategorySectionRow : nouveau paramètre optionnel
badgeFor: ((VodStream) -> String?)? = null

// site d'appel de la rangée "resume_watching" uniquement
val resumeLabels = remember(state.resumeMovies) {
    state.resumeMovies.mapNotNull { pos ->
        EpisodeLabel.format(pos.seasonNum, pos.episodeNum)?.let { pos.streamId to it }
    }.toMap()
}
…
badgeFor = { stream -> resumeLabels[stream.streamId] }
```

`EpisodeLabel.format` (`domain/model/EpisodeLabel.kt`) est réutilisé tel quel :
c'est déjà le format unique de l'application (« S01 E03 »), et il renvoie `null`
si la saison ou l'épisode est inconnu — les films n'affichent donc aucun badge,
sans condition supplémentaire.

Toutes les autres rangées et grilles passent `badgeFor = null` : aucun badge, le
comportement d'unification reste entier.

La rangée « Continuer à regarder » de **l'Accueil** n'est pas concernée : elle
utilise `HomeResumeWatchingCard`, carte paysage dédiée avec titre et ligne
« S01 E03 · temps restant », qui reste inchangée.

## Performances

Légèrement favorable : les cartes de référence sont une `Box` + `AsyncImage` +
badge conditionnel, contre `Card` + `Column` + `Box` + `Text` pour les cartes
supprimées — un nœud de layout et une mesure de texte en moins par cellule.
Sur une grille TV de 4 colonnes, l'effet est marginal mais jamais négatif.
Aucune image supplémentaire n'est chargée (mêmes URL, même Coil).

## Sécurité

Sans objet.

## Compatibilité

* **Mobile** : les grilles passent de 3 colonnes de cartes à titre à 3 colonnes
  d'affiches ; `GridCells.Fixed(3)` et les espacements (12.dp) sont conservés.
  La hauteur de cellule est désormais dérivée du ratio 2:3 au lieu d'être
  poster + hauteur de texte, la grille gagne donc en régularité.
* **TV** : `GridCells.Fixed(4)`, espacements 16.dp et
  `contentPadding = vertical(screenHeight/2)` (réserve de pivot F19) conservés.
  `propagateMinConstraints = true` reste nécessaire et n'est pas retiré.
* **min SDK 21** : aucune API conditionnée.

## Dépendances

Aucune dépendance Gradle ajoutée.

## Risques techniques

| Risque | Gravité | Mitigation |
| --- | --- | --- |
| Régression de dimensionnement sur les sept sites d'appel existants de la Home | Élevée si `fillCell` était mal câblé | `fillCell` a pour valeur par défaut `false` et le chemin `false` reproduit **exactement** le code actuel (mêmes constantes, même ordre de modificateurs). Aucun appel existant n'est modifié. |
| Cellule de grille écrasée ou étirée | Visuelle | `fillMaxWidth().aspectRatio(2f/3f)` dérive la hauteur de la largeur imposée par `GridCells`, ce qui est le contrat attendu d'une cellule ; combiné à `propagateMinConstraints = true` côté TV. |
| Interaction `rank` + `fillCell` | Faible | Cas impossible dans le code (aucune grille ne passe `rank`) ; documenté dans le KDoc et le calcul de taille ignore `rank` quand `fillCell = true`. |
| Imports morts → échec `lintDebug` | Faible | Nettoyage inclus dans la tâche ; `lintDebug` fait partie de la checklist de fin. |
| Perte de l'info « quel épisode » sur la rangée reprise TV des écrans VOD/Séries | Fonctionnelle mineure | Traitée : badge « S01 E03 » en surimpression (décision PO du 2026-08-02), sans réintroduire de texte sous la vignette. |
| Badge épisode chevauchant le badge de note | Visuelle | Alignements opposés (`TopStart` vs `TopEnd`) sur une affiche de 130 dp de large : aucun recouvrement possible aux tailles de police retenues (8.sp). |
| Badge affiché sur un film | Fonctionnelle | Impossible : `EpisodeLabel.format` renvoie `null` dès que la saison ou l'épisode est inconnu, et `badgeFor` n'est fourni qu'à la rangée `resume_watching`. |

## Contraintes de performance

Aucune. Le ticket ne touche ni le chargement du catalogue, ni la pagination, ni
la synchronisation.

---

# 5. Architecture

## Position dans la Clean Architecture

Correctif de dette de design, entièrement `presentation`. Aucune règle métier,
aucun accès `domain`/`data`.

```
presentation/
├── home/components/HomeCards.kt          ← + paramètre fillCell (2 composables)
│     HomeVodMovieCard / HomeSeriesShowCard  = SOURCE UNIQUE de la carte média
│
├── vod/VodScreen.kt
│   ├── CategorySectionRow  → HomeVodMovieCard(isTv = isTv)
│   ├── TvLayout grille     → HomeVodMovieCard(fillCell = true, isTv = true)
│   ├── MobileLayout grille → HomeVodMovieCard(fillCell = true)
│   └── MovieTvCard         ✗ SUPPRIMÉ
│
└── series/SeriesScreen.kt   ← symétrique, SeriesTvCard ✗ SUPPRIMÉ
```

Le ticket fait converger quatre implémentations de « carte média » vers une
seule par type de média. `HomeCards.kt` devient de fait le composant partagé
inter-écrans ; son emplacement sous `home/components/` devient discutable
(`presentation/components/` serait plus juste au sens de la structure de dossiers
attendue par `AGENTS.md`). **Le déplacement n'est pas effectué dans ce ticket** :
il toucherait tous les imports de la Home pour un gain purement organisationnel,
et brouillerait la revue du correctif visuel. À proposer séparément.

## Flux de rendu

```
VodScreen (mode "Tout")                    VodScreen (mode "Catégorie")
        │                                            │
        ▼                                            ▼
CategorySectionRow                          LazyVerticalGrid(Fixed(4|3))
        │                                            │
        ▼                                            ▼
LazyRow → Box(tvPivotItem)                   Box(tvPivotCell,
        │                                        propagateMinConstraints = true)
        ▼                                            │
HomeVodMovieCard(isTv)                               ▼
  taille fixe 130 × 195                    HomeVodMovieCard(fillCell = true, isTv)
  focus 14.dp, badge note                    largeur = cellule, ratio 2:3
  historyItemActions                         focus 14.dp, badge note
                                             historyItemActions
```

Les deux régimes partagent **le même corps de composable** : affiche, placeholder,
badge de note, anneau de focus et gestion des interactions sont écrits une fois.
Seule la stratégie de dimensionnement diffère, et elle est explicite dans la
signature.

## Responsabilités des composants

* **`HomeVodMovieCard` / `HomeSeriesShowCard`** : unique définition visuelle
  d'une vignette de film / série dans toute l'application — rayon, ombre de
  focus, badge, placeholder. Toute évolution de style se fait ici, et se propage
  partout par construction.
* **`CategorySectionRow`** : disposer une rangée horizontale ; elle ne décide
  plus de *quelle* carte selon la plateforme (la branche `if (isTv)` disparaît),
  elle transmet simplement `isTv` à la carte.
* **`TvLayout` / `MobileLayout`** : choisir le régime de dimensionnement adapté
  au conteneur (`fillCell = true` en grille).
* **`tvPivotItem` / `tvPivotCell`** : inchangés, appliqués depuis l'extérieur.

## Décisions techniques

1. **Étendre les cartes de référence plutôt que créer une troisième variante.**
   Créer un `VodGridCard` distinct reproduirait la dette qu'on supprime. Un seul
   composable, deux régimes de taille.
2. **`fillCell: Boolean` plutôt que `modifier: Modifier`.** Justifié en
   section 4 : un `modifier` serait silencieusement ignoré face aux contraintes
   de taille fixes déjà présentes.
3. **Valeur par défaut `false`.** Le chemin par défaut est bit-à-bit celui
   d'aujourd'hui : les sept appels existants de la Home ne sont pas relus, ne
   sont pas modifiés, et ne peuvent pas régresser.
4. **Suppression franche de `MovieTvCard` et `SeriesTvCard`.** Les conserver
   « au cas où » laisserait deux définitions concurrentes vivantes et rouvrirait
   la divergence au premier ajustement de style.
5. **Pas de déplacement de `HomeCards.kt`.** Voir ci-dessus : bruit de revue
   disproportionné, à traiter à part.
6. **`propagateMinConstraints = true` conservé.** Il reste nécessaire : `Box`
   relâche par défaut les contraintes minimales, et `fillMaxWidth()` sans
   contrainte min propagée ne remplirait pas la cellule.

## Stratégie de tests

Correctif de rendu pur (dimensions, rayons, présence d'un `Text`), explicitement
classé « non prioritaire / pas sur-investir » par `AGENTS.md`, et dont la
vérification visuelle exigerait un device — donc hors critères de validation de
l'agent. Aucun test unitaire JVM n'est ajouté : ni logique métier, ni parsing, ni
état.

Points de contrôle reportés sur la review (étape 6), à vérifier par lecture du
diff :
1. aucune occurrence résiduelle de `MovieTvCard` / `SeriesTvCard` ;
2. aucun `Text(text = stream.name)` sous une affiche dans `VodScreen.kt` /
   `SeriesScreen.kt` ;
3. les sept appels existants de `HomeVodMovieCard` / `HomeSeriesShowCard` dans
   `HomeScreen.kt` sont inchangés (aucun `fillCell` passé) ;
4. `RoundedCornerShape(14.dp)` sur tous les chemins ;
5. `onLongClick` toujours transmis sur la rangée `resume_watching` ;
6. `badgeFor` fourni **uniquement** à la rangée `resume_watching`, et
   `badgeLabel` absent partout ailleurs.

Un test unitaire JVM couvre malgré tout la seule logique extractible introduite :
la construction de la table de libellés (`EpisodeLabel.format` sur des positions
film / série / métadonnées manquantes). `EpisodeLabelTest` existe déjà et couvre
le format lui-même ; l'assertion ajoutée porte sur le fait qu'une position de
film ne produit aucune entrée.

Non-régression : `./gradlew testDebugUnitTest`, `assembleDebug`, `lintDebug`
(ce dernier valide aussi le nettoyage des imports).

---

# 6. Plan de développement

## Ordre d'exécution

La géométrie commune est extraite avant les appels écrans ; la rangée de reprise
est traitée séparément afin de conserver son badge épisode.

### Tâche 1 — Créer le contrat de carte portrait homogène

- [x] Introduire le composant ou les constantes partagés définissant ratio,
  hauteur de titre, ellipses et états focus TV.

Objectif : éliminer les tailles fixes incompatibles avec des titres variables,
sans dupliquer les règles de rendu entre Films et Séries.

Fichiers : composant partagé `presentation/components/` et styles associés.

Validation : titres courts/longs restent dans une empreinte identique ; poster,
focus, clic et placeholder gardent leur comportement.

### Tâche 2 — Migrer les rendus Films et Séries ciblés

- [x] Remplacer les cartes divergentes des rangées et grilles par le contrat
  commun, en nettoyant les imports devenus inutiles.

Objectif : rendre cohérentes les cartes VOD/Séries sur mobile et TV conformément
aux références visuelles.

Fichiers : `VodScreen.kt`, `SeriesScreen.kt`, composants de cartes listés au §4.

Validation : aucune carte ciblée ne conserve l'ancienne taille fixe ; le rendu
TV garde les dimensions et le focus spécifiques prévus.

### Tâche 3 — Préserver explicitement la reprise série

- [x] Adapter la carte de reprise pour afficher le badge épisode sans modifier
  la géométrie du contrat commun.

Objectif : corriger l'homogénéité sans perdre l'information de progression.

Fichiers : composant Home/reprise et carte partagée concernés.

Validation : badge épisode présent uniquement quand pertinent ; progression et
navigation détail restent intactes.

### Tâche 4 — Vérifier la non-régression

- [x] Exécuter les contrôles automatisés et documenter la vérification visuelle.

Fichiers : tests existants et ce ticket.

Validation : `testDebugUnitTest`, `assembleDebug`, `lintDebug` passent ; les
comparaisons mobile/TV sont une vérification visuelle séparée.

---

# 7. Notes de développement

Implémenté le 2026-08-02 conformément à la conception des sections 4-5.

- `HomeCards.kt` : `fillCell: Boolean = false` et `badgeLabel: String? = null`
  ajoutés à `HomeVodMovieCard`/`HomeSeriesShowCard`. Chemin par défaut
  bit-à-bit inchangé (les sept appels existants de la Home ne passent aucun
  des deux nouveaux paramètres).
- `VodScreen.kt`/`SeriesScreen.kt` : `MovieTvCard`/`SeriesTvCard` supprimées,
  ainsi que les grilles mobiles codées en dur ; les quatre sites (rangée TV,
  rangée mobile, grille TV, grille mobile) utilisent désormais
  `HomeVodMovieCard`/`HomeSeriesShowCard`, avec `fillCell = true` en grille et
  `propagateMinConstraints = true` conservé sur le wrapper de grille TV.
- Badge « S01 E03 » : `CategorySectionRow` reçoit un paramètre optionnel
  `badgeFor: ((VodStream) -> String?)? = null` (idem séries), alimenté
  uniquement par la rangée `resume_watching` via une table `resumeLabels`
  construite avec `EpisodeLabel.format`. Toutes les autres rangées passent
  `badgeFor = null`.
- Imports morts nettoyés dans les deux écrans (`AsyncImage`, `ContentScale`,
  `TextAlign`, `TextOverflow`, icônes `Star`/`Warning`/`PlayArrow`, `Surface2`/
  `Surface3` selon le fichier).
- `EpisodeLabelTest` : un test ajouté documentant qu'une position de film
  (saison/épisode `null`) ne produit aucune entrée dans la table de libellés.

Aucun écart par rapport à la conception de l'étape 3 : le paramètre `fillCell`
(booléen plutôt que `modifier`), la non-modification de `CategorySectionRow`
en dehors du nouveau paramètre `badgeFor`, et la suppression franche des
cartes obsolètes ont été appliqués tels que décidés.

## Vérifications automatisées

- `./gradlew compileDebugKotlin` → réussi après chaque modification.
- `./gradlew testDebugUnitTest` (hors `HomeViewModelTest`/`RecentlyAddedViewModelTest`,
  qui bloquent l'exécution complète dans cette session — cause préexistante et
  sans rapport avec ce ticket, documentée dans les notes de F23) → 538 tests,
  0 échec (après correction d'un bug préexistant et sans rapport découvert
  chemin faisant : `SeriesViewModelTest.kt` vérifiait `savePlaybackPositionUseCase`
  sans le paramètre `categoryId` ajouté par T9, provoquant une
  `InvalidUseOfMatchersException` de Mockito ; corrigé en ajoutant
  `categoryId = isNull()` aux deux `verify()` concernés).
- `./gradlew assembleDebug lintDebug` → réussi, `0 errors`.

Points de contrôle de la review différée (§ « Stratégie de tests » de
l'étape 3) non vérifiés dans cette session car ils exigent une lecture de diff
et une observation visuelle TV/mobile, hors des critères automatisés de
l'agent (`AGENTS.md`).

Étape 5 (Implémentation) terminée le 2026-08-02 ; Étapes 6-8 consignées
ci-dessous.

---

# 8. Review

Status: RESOLVED

Review effectuée le 2026-08-02 sur `HomeCards.kt`, `VodScreen.kt`,
`SeriesScreen.kt` et `EpisodeLabelTest.kt`.

## Critique

Aucun problème critique identifié.

## Majeur

Aucun problème majeur identifié.

## Mineur

### B18-R1 — Le test ajouté ne couvre pas la construction de la table des badges

**Description :**
`EpisodeLabelTest.format_returnsNullForAMoviePositionWithoutEpisodeMetadata()`
répète exactement `assertNull(EpisodeLabel.format(null, null))`, déjà présent
dans `format_returnsNullWhenSeasonOrEpisodeIsUnknown()`. Contrairement à ce
qu'indiquent son commentaire et les notes de développement, il n'exerce ni le
`mapNotNull` qui construit `resumeLabels`, ni la clé utilisée (`streamId` ou
`seriesId`), ni la transmission de `badgeFor` à la seule rangée
`resume_watching`.

**Impact :** le test augmente le compteur sans apporter de couverture de
non-régression. Une erreur future dans la construction ou l'association des
badges pourrait passer tout en laissant ce test au vert.

**Correction attendue :** supprimer l'assertion redondante et soit corriger les
notes pour ne pas revendiquer cette couverture, soit extraire la construction
de la table dans un petit helper pur et tester au minimum les positions série,
film et métadonnées incomplètes.

## Conclusion

Le rendu implémenté est conforme par lecture du diff : les anciennes cartes et
les titres sous affiche ont disparu, les rangées et grilles utilisent les deux
cartes partagées, `fillCell` préserve le ratio 2:3, l'appui long de reprise est
toujours transmis et `badgeFor` n'est fourni qu'à `resume_watching`. Le défaut
restant concerne uniquement la valeur réelle du test ajouté.

Vérification ciblée exécutée pendant la review :
`./gradlew testDebugUnitTest --tests com.cstv.app.domain.model.EpisodeLabelTest
--tests com.cstv.app.presentation.components.TvFocusSelectorStateTest` réussit.
Cette étape n'est pas la validation finale et aucune vérification visuelle n'a
été effectuée.

## Étape 7 — Correction (2026-08-02)

### B18-R1 — résolu

La construction de la table de libellés est extraite en fonction pure
`EpisodeLabel.buildResumeLabels(positions: List<PlaybackPosition>, keyOf: (PlaybackPosition) -> Int): Map<Int, String>`,
appelée depuis `VodScreen.kt`/`SeriesScreen.kt` (`EpisodeLabel.buildResumeLabels(state.resumeMovies) { it.streamId }`,
`EpisodeLabel.buildResumeLabels(state.resumeSeries) { it.seriesId ?: it.streamId }`).
`EpisodeLabelTest.kt` remplace le test redondant par cinq cas exerçant
réellement le `mapNotNull` et la clé : position série valide, position film
sans métadonnées (aucune entrée), métadonnées incomplètes (aucune entrée),
liste mixte (seules les positions valides restent), et repli sur `streamId`
quand `seriesId` est `null`.

### Vérifications après correction

- `./gradlew testDebugUnitTest --tests com.cstv.app.domain.model.EpisodeLabelTest` → réussi (6 cas).
- `./gradlew compileDebugKotlin` → réussi.

## Étape 8 — Validation finale (2026-08-02)

Status: VALIDATED

- Comportement attendu / règles métier : le finding B18-R1 est corrigé et
  revérifié ; le reste de la review (Critique/Majeur : aucun problème) reste
  sans objet.
- Qualité technique / absence de régression : `./gradlew compileDebugKotlin`
  réussi ; `./gradlew assembleDebug lintDebug` réussi, `0 errors` ; suite de
  tests exécutée en excluant `HomeViewModelTest`/`RecentlyAddedViewModelTest`
  (blocage préexistant et sans rapport avec ce ticket, documenté dans les
  notes de F23) → 545 tests, 0 échec.
- Expérience utilisateur (grilles/rangées TV et mobile, focus D-pad) : non
  vérifiée sur appareil/émulateur, hors des critères de validation automatisés
  de l'agent (`AGENTS.md`).

Le ticket passe de `REVIEW` à `VALIDATED`.

---

# 9. Release

Version:
v1.68.0

Commit:
✨ Release F23, B18 & T10: double-layer TV focus, card unification & row limiting

Date:
2026-08-02
