# B4 - Impossible d'ajouter rapidement en favoris sur TV dans la liste « Tout »

## Informations générales

Type:
Bug

Status:
ARCHITECTURE

Created:
2026-07-21

Target version:
v1.48.32

---

# 1. Description

Sur l'écran **TV en Direct** (Live TV) sur Android TV, lorsque l'utilisateur est sur la catégorie globale **« Tout »** (qui liste verticalement les catégories sous forme de rangées horizontales de chaînes), il est devenu impossible de mettre rapidement une chaîne en favoris.

La carte de chaîne s'étire sur toute la largeur de l'écran, ce qui casse la mise en page de la ligne horizontale (un seul élément visible au lieu d'un défilement de chaînes côte à côte) et rejette le bouton d'ajout rapide aux favoris (l'étoile) hors-écran ou à l'extrême droite, le rendant invisible et inaccessible à la télécommande.

---

# 2. Contexte

Le composant de carte TV pour une chaîne est défini par `StreamTvCard` dans `LiveTvComponents.kt`.

Ce composant est réutilisé à deux endroits sur Android TV :
1. **Dans la grille verticale d'une catégorie spécifique :** La grille comporte 3 colonnes fixes. Chaque cellule a donc une largeur restreinte. `StreamTvCard` utilise un modificateur hardcodé `Modifier.fillMaxWidth()`, ce qui fonctionne parfaitement ici car la largeur est contrainte par la colonne de la grille.
2. **Dans le carrousel horizontal des catégories de la liste « Tout » :** Le conteneur est un `LazyRow` horizontal. N'ayant pas de contrainte de largeur de colonne, le modificateur `Modifier.fillMaxWidth()` de `StreamTvCard` force la carte à s'étirer pour prendre toute la largeur de l'écran du téléviseur.

Par conséquent :
- Le design en swimlanes (lignes de défilement horizontal) est complètement brisé visuellement : une seule chaîne énorme remplit l'écran horizontalement.
- L'icône étoile de favoris rapide (située à l'extrémité droite de la carte) se retrouve isolée au bord de l'écran.
- La navigation au D-pad devient confuse et imprévisible.
- L'utilisateur ne peut plus cibler ou voir le bouton favori rapidement dans cette vue d'ensemble.

---

# 3. Spécification fonctionnelle

## Objectif

Restaurer, dans le mode Android TV de la catégorie « Tout », des rangées horizontales de chaînes compactes et défilables, dont chaque carte offre une action Favori immédiatement visible et utilisable au D-pad sans déclencher la lecture.

## User stories

- En tant qu'utilisateur Android TV, je vois plusieurs chaînes côte à côte dans chaque rangée de catégories de « Tout » afin de parcourir rapidement le catalogue.
- En tant qu'utilisateur Android TV, je peux atteindre l'étoile d'une chaîne focusée et l'ajouter ou la retirer des favoris sans lancer cette chaîne.
- En tant qu'utilisateur Android TV, je continue de lancer une chaîne en validant sa carte, comme avant la correction.
- En tant qu'utilisateur, je conserve la même présentation en grille lorsque j'ouvre une catégorie précise ; la correction du mode « Tout » ne doit pas dégrader cet écran.

## Parcours utilisateur

1. L'utilisateur ouvre TV en direct sur Android TV et sélectionne la catégorie globale « Tout ».
2. Chaque rangée horizontale de catégorie — y compris la rangée « Favoris » lorsqu'elle est présente — affiche des cartes de chaînes de largeur constante. Plusieurs cartes sont visibles simultanément lorsque l'espace de l'écran le permet ; les autres sont accessibles par défilement horizontal.
3. L'utilisateur déplace le focus sur une carte. Son contour de focus et l'action étoile deviennent visibles dans les limites de cette carte.
4. La validation sur la carte lance la chaîne, sans modifier son état de favori.
5. Avec la flèche droite, l'utilisateur peut déplacer le focus de la carte vers l'étoile. La validation sur l'étoile ajoute la chaîne aux favoris ou la retire des favoris ; la chaîne ne se lance pas.
6. Après bascule, l'étoile reflète immédiatement le nouvel état et conserve le focus. La flèche gauche ramène le focus à la carte de la même chaîne ; le reste de la navigation entre cartes et rangées reste naturel.
7. Si la chaîne est retirée des favoris depuis la rangée « Favoris », elle disparaît immédiatement de cette rangée, tandis qu'elle reste disponible dans sa catégorie fournisseur. Si c'était le dernier favori, la rangée « Favoris » disparaît.
8. Lorsque l'utilisateur sélectionne une catégorie spécifique, les chaînes restent dans la grille verticale existante à trois colonnes, avec leur comportement de focus et de favori déjà disponible.

## Règles métier

- La correction est limitée à Android TV et au rendu des cartes de chaînes dans les rangées horizontales du mode « Tout ». Le rendu mobile n'est pas modifié.
- Une carte de chaîne dans une rangée horizontale a une largeur fixe de **220.dp** et conserve sa hauteur de 84.dp. Elle ne s'étend jamais à la largeur disponible du téléviseur.
- Une carte de chaîne dans la grille d'une catégorie spécifique conserve son remplissage de la cellule à trois colonnes ; elle ne reçoit pas la largeur fixe destinée aux rangées.
- L'étoile est affichée lorsque la carte est focusée ou lorsque la chaîne est déjà favorite. Elle doit rester entièrement visible dans la carte et ne pas chevaucher ni tronquer les informations principales au point de les rendre inutilisables.
- L'étoile est une cible de focus distincte de la carte. Valider la carte exécute uniquement la lecture ; valider l'étoile exécute uniquement le basculement du favori.
- L'action conserve les règles de favoris existantes : elle est locale au profil actif, persistante et la chaîne ne peut avoir qu'un seul état favori ou non favori.
- Le changement de favori est immédiatement reflété dans toutes les rangées TV visibles qui utilisent le même état, sans modifier le catalogue, l'EPG, les chaînes récentes ni les favoris d'un autre profil.

## Critères d'acceptation

- Dans « Tout » sur Android TV, chaque rangée de catégories affiche plusieurs cartes de 220.dp côte à côte lorsque l'écran le permet, et se parcourt horizontalement au D-pad.
- Aucune carte de rangée horizontale ne remplit seule la largeur de l'écran ; l'étoile n'est ni hors-écran ni inaccessible.
- Au focus d'une carte, l'étoile est visible. Elle reste visible quand la chaîne est favorite, même si la carte n'a plus le focus.
- Le D-pad permet d'atteindre l'étoile depuis la carte, de la valider pour basculer le favori sans lancer la chaîne, puis de revenir à la carte.
- La validation sur la carte continue de lancer la chaîne et ne modifie pas le favori.
- L'ajout ou le retrait met à jour immédiatement l'icône et les rangées concernées ; le retrait du dernier favori masque la rangée dédiée.
- La grille Android TV d'une catégorie précise conserve ses trois colonnes et ses cartes occupant la largeur de leur cellule.
- Le comportement et la présentation des cartes Live TV sur mobile restent inchangés.

## Cas limites

- Si une chaîne n'a ni logo ni EPG, la largeur fixe et l'étoile restent utilisables ; le contenu de substitution existant est affiché sans modifier le focus.
- Si le nom de la chaîne ou du programme est long, il peut être tronqué selon les règles visuelles existantes mais ne pousse jamais l'étoile hors de la carte.
- Si la liste de favoris est modifiée pendant que son étoile est focusée, la carte garde un focus valide lorsque possible ; si la carte disparaît parce qu'elle était le dernier élément de la rangée Favoris, le focus revient à un élément voisin disponible, jamais à une zone vide.
- Si un changement de profil se produit, le nouvel état de favoris du profil est affiché ; aucun favori de l'ancien profil ne doit rester visible par erreur.
- Les rangées sans chaînes ne sont pas affichées et ne créent pas de destination de focus vide.

## Gestion des erreurs

- Le changement de favori ne requiert aucun appel réseau ; une indisponibilité Xtream ou l'absence d'Internet ne doit pas empêcher une bascule locale déjà accessible.
- Si l'enregistrement local du favori échoue, l'étoile revient à son dernier état persistant et un message non technique invite l'utilisateur à réessayer. La chaîne ne doit pas être lancée par effet de bord.
- Si les données de la chaîne ou le profil actif deviennent indisponibles avant validation, la cible est désactivée ou l'action est ignorée sans crash et sans état visuel incohérent.

---

# 4. Décisions de périmètre

- La largeur fonctionnelle des cartes de rangées Android TV est fixée à **220.dp**.
- L'étoile est accessible comme cible D-pad distincte ; aucun geste alternatif de clic long n'est ajouté.
- Le correctif s'applique aux rangées du mode « Tout » — y compris « Favoris » — et préserve la grille de catégorie spécifique ainsi que le mobile.

---

# 5. Notes de spécification

- La maquette de référence ne décrit pas cette interaction Android TV spécifique. L'étape 3 détaillera l'adaptation de composant nécessaire en réutilisant les tokens de focus, de surfaces et de rayons existants dans `docs/design-reference/`.

---

# 6. Spécification technique

## Diagnostic confirmé

- `CategorySectionRow` rend les cartes Android TV dans un `LazyRow`. Dans cet axe horizontal, aucun parent ne fournit de largeur de cellule à `StreamTvCard`.
- `StreamTvCard` impose actuellement `fillMaxWidth()` sur sa racine. Ce choix est correct dans la `LazyVerticalGrid` à trois colonnes, mais ambigu dans le `LazyRow` et produit la carte surdimensionnée observée.
- Les deux contextes Android TV appellent le même composable sans pouvoir lui transmettre une contrainte différente.
- L'étoile est aujourd'hui composée uniquement si `isFocused || isFavorite`. Un simple suivi de `FocusState.isFocused` sur la carte ne suffit pas pour une action enfant : lorsque l'étoile prend le focus, la carte peut perdre son focus direct et retirer l'étoile de la composition.
- La logique métier de bascule (`FavoritesViewModel.toggleFavorite` puis observation Room) et le callback `onToggleFavorite` sont déjà partagés et fonctionnels. B4 ne nécessite pas de nouvelle donnée ni de nouvel appel réseau.

## Adaptation de `StreamTvCard`

La signature reçoit un modificateur de placement injecté par le parent :

```kotlin
@Composable
fun StreamTvCard(
    stream: LiveStream,
    isFavorite: Boolean,
    epgProgram: LiveEpgProgram?,
    onLoadEpg: () -> Unit,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
)
```

- La racine applique le `modifier` reçu puis la hauteur commune de `84.dp`; elle n'appelle plus elle-même `fillMaxWidth()`.
- Un conteneur racine forme un groupe de focus regroupant la cible principale de lecture et l'`IconButton` Favori.
- L'état visuel suit le focus de tout le sous-arbre avec `FocusState.hasFocus`, et non uniquement `isFocused`. Le contour actif et l'affichage de l'étoile restent donc présents lorsque l'étoile elle-même est focusée.
- L'emplacement de l'étoile reste toujours réservé dans la `Row` pour empêcher tout changement de largeur du texte lors de l'entrée ou de la sortie du focus.
- Hors focus et pour une chaîne non favorite, l'étoile est transparente et explicitement exclue de la recherche de focus. Elle devient visible et focusable dès que le groupe de la carte possède le focus, ou reste visible si `isFavorite` vaut `true`.
- L'`IconButton` conserve son callback propre. Sa validation ne propage pas l'action vers la cible de lecture parente.
- Le libellé d'accessibilité devient dépendant de l'état : « Ajouter aux favoris » ou « Retirer des favoris ».

## Contraintes fournies par les parents

- Dans `CategorySectionRow`, l'appel Android TV fournit `Modifier.width(220.dp)`. Cette règle couvre toutes les rangées construites par ce composant dans « Tout », dont la rangée synthétique `favorites`.
- Dans la grille de catégorie spécifique de `TvLayout`, l'appel fournit explicitement `Modifier.fillMaxWidth()` afin de conserver les trois colonnes existantes.
- La branche mobile continue d'utiliser `MobileStreamCard` et ne reçoit aucun changement de dimension ou de focus.
- La constante de largeur est privée au fichier de composants (`TV_HORIZONTAL_STREAM_CARD_WIDTH = 220.dp`) pour éviter une valeur magique répétée, sans créer un token global qui n'aurait qu'un seul usage.

## Ressources

Deux chaînes localisées sont ajoutées dans `app/src/main/res/values/strings.xml` :

- `live_tv_add_favorite` : « Ajouter aux favoris » ;
- `live_tv_remove_favorite` : « Retirer des favoris ».

Aucune ressource graphique n'est ajoutée : l'icône `Icons.Default.Star`, les couleurs `Surface3`, primaire et jaune, le rayon de `12.dp` et le contour de `2.dp` existants sont conservés.

## Persistance et erreurs

- Le flux reste `StreamTvCard` → `onToggleFavorite` → `FavoritesViewModel.toggleFavorite` → use cases Favoris → Room → `favoritesList` observée → recomposition.
- Il n'y a pas d'état optimiste local dans la carte : l'étoile change d'état uniquement lorsque `favoritesList` reflète la donnée persistée, ce qui évite un état mensonger si l'écriture échoue.
- B4 ne modifie pas le contrat d'erreur global des favoris. L'ajout d'un canal de Snackbar et la refonte de la gestion d'exception de `FavoritesViewModel` affecteraient tous les écrans de favoris et sortent du correctif de dimension/focus. Le composant ne doit toutefois jamais lancer la chaîne ni inverser localement l'icône en cas d'échec du callback.

## Compatibilité et dépendances

- Kotlin et Jetpack Compose existants uniquement ; aucune nouvelle dépendance Gradle.
- Aucun changement de schéma Room, migration, repository, use case, navigation ou API Retrofit.
- Min SDK 21 et Android TV restent inchangés.
- Le correctif n'a aucun effet sur le rendu mobile, car `StreamTvCard` n'est appelé que dans les branches `isTv`.

---

# 7. Architecture

## Responsabilités

```text
TvLayout
├── mode Tout
│   └── CategorySectionRow
│       └── StreamTvCard(modifier = width(220.dp))
│           ├── cible carte : lecture
│           └── cible étoile : bascule favori
└── catégorie spécifique
    └── LazyVerticalGrid(3 colonnes)
        └── StreamTvCard(modifier = fillMaxWidth())
            ├── cible carte : lecture
            └── cible étoile : bascule favori
```

- Le parent reste responsable de la taille imposée par son type de conteneur.
- `StreamTvCard` reste responsable de sa hauteur, de son contenu, de son groupe de focus et de la séparation des actions lecture/favori.
- `FavoritesViewModel` et les couches domaine/data restent responsables de la persistance et de la diffusion réactive de l'état favori.

## Flux de focus

```text
carte chaîne ── D-pad droite ──> étoile Favori
      │                              │
 validation                    validation
      │                              │
 lecture chaîne                toggle favori
                                     │
                         D-pad gauche vers carte
```

Le groupe conserve `hasFocus = true` pour les deux cibles. L'étoile ne quitte donc jamais la composition ni le graphe de focus pendant le passage carte → étoile. Lorsqu'une carte disparaît de la rangée Favoris après retrait, la `LazyRow` et Compose résolvent le prochain élément disponible ; aucun `FocusRequester` persistant n'est introduit dans ce correctif.

## Fichiers impactés

- `app/src/main/java/com/cstv/app/presentation/livetv/components/LiveTvComponents.kt`
  - paramètre `modifier` de `StreamTvCard` ;
  - constante de largeur horizontale ;
  - groupe et état de focus du sous-arbre ;
  - étoile à emplacement stable, visibilité/focus conditionnels ;
  - largeur `220.dp` passée depuis `CategorySectionRow`.
- `app/src/main/java/com/cstv/app/presentation/livetv/LiveTvScreen.kt`
  - `Modifier.fillMaxWidth()` explicite à l'appel de la grille Android TV.
- `app/src/main/res/values/strings.xml`
  - libellés accessibles Ajouter/Retirer des favoris.
- `ai/bugs/B4-tv-quick-favorites-all-category.md`
  - suivi du cycle de vie et décisions de conception.

## Nouveaux composants

Aucun nouveau composant d'architecture, modèle, repository ou use case. La correction étend le contrat de présentation de `StreamTvCard` et réutilise intégralement la chaîne de favoris existante.

---

# 8. Validation prévue

## Vérifications automatisées

- `./gradlew testDebugUnitTest` pour la non-régression des favoris et de la présentation existante.
- `./gradlew assembleDebug` pour valider les signatures Compose, imports de focus et ressources.
- `./gradlew lintDebug` pour vérifier notamment les ressources et l'accessibilité statique.

Le projet ne dispose pas d'une infrastructure de tests UI Compose Android TV. Conformément à la stratégie de tests, aucun test unitaire artificiel n'est ajouté pour une contrainte `Modifier` pure ; le comportement de focus est validé manuellement sur émulateur ou appareil TV.

## Scénarios manuels obligatoires

1. Android TV, « Tout » : vérifier plusieurs cartes côte à côte et le défilement horizontal dans une catégorie fournisseur et dans Favoris.
2. Carte non favorite : focus carte → étoile visible → D-pad droite → validation étoile sans lecture → état jaune.
3. Carte favorite : retrait depuis une catégorie fournisseur puis depuis la rangée Favoris ; vérifier la mise à jour réactive et le focus restant valide.
4. Catégorie spécifique : vérifier les trois colonnes, la largeur de cellule, la lecture et le favori.
5. Chaîne sans logo/EPG et chaîne au nom long : vérifier absence de débordement et étoile entièrement visible.
6. Mobile : vérifier visuellement que les cartes et l'action Favori n'ont pas changé.

## Risques techniques et atténuations

- **Disparition de l'étoile lors du transfert de focus** : suivre `hasFocus` sur le groupe entier et garder l'emplacement de l'action composé.
- **Étoile invisible mais encore focusable** : coupler sa transparence à une propriété `canFocus = false`, pas seulement à `alpha(0f)`.
- **Action favorite déclenchant aussi la lecture** : conserver deux cibles cliquables distinctes et valider le callback de chaque scénario au D-pad.
- **Régression de la grille** : rendre le dimensionnement obligatoire et explicite aux deux appels Android TV.
- **Troncature accrue à 220.dp** : conserver `weight(1f)`, `maxLines = 1` et `TextOverflow.Ellipsis`; le logo et l'étoile gardent des largeurs fixes.
- **Perte de focus après retrait dans Favoris** : ne pas mémoriser un `FocusRequester` lié à une carte supprimée et vérifier le comportement sur premier, milieu, dernier et unique élément.

## Contraintes de performance

- Aucun nouveau collecteur, appel Room, appel réseau ou chargement d'image.
- L'état de focus reste local à chaque carte et ne provoque que sa recomposition.
- La largeur fixe réduit la surface composée par carte visible dans le `LazyRow`; la virtualisation paresseuse existante est conservée.
