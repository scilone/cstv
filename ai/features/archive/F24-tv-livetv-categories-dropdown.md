# F24 - TV LiveTV Categories Dropdown

## Informations générales

Status:
RELEASED

Created:
2026-08-05

---

# 1. Description

Sur l'écran Live TV (liste des chaînes), remplacer la ligne horizontale de jetons (CategoryFilterChips) par un système de sélecteur dropdown (TvCategorySelectorTrigger) ouvrant un dialogue de sélection de catégorie (TvCategoryPickerDialog), identique à celui des écrans Films (VOD) et Séries.

---

# 2. Contexte

Actuellement, l'écran Live TV affiche toutes ses catégories sous forme d'une ligne horizontale de puces défilantes en haut de l'écran. Ce comportement diffère des écrans Films et Séries qui utilisent un déclencheur de sélection ouvrant un dialogue de sélection plein écran pour une meilleure ergonomie TV.

---

# 3. Spécification fonctionnelle

### User story

En tant qu'utilisateur Android TV, je choisis rapidement une catégorie Live TV depuis un contrôle unique, stable au D-Pad, plutôt que de parcourir une rangée de puces horizontales.

### Parcours utilisateur

1. À l'entrée de Live TV, le bandeau affiche le sélecteur avec « TOUT » ou la catégorie active.
2. L'utilisateur focalise le sélecteur puis l'active ; une liste plein écran de catégories apparaît, avec le choix courant visible et focalisé.
3. Il navigue verticalement, lit le libellé et le nombre de chaînes, puis active une entrée.
4. La liste se ferme, la sélection est appliquée et le focus revient au sélecteur. L'utilisateur peut alors entrer dans les contenus filtrés.
5. S'il ferme la liste sans choisir, la catégorie et les résultats précédents restent intacts, puis le focus revient au sélecteur.

### Règles métier

- Le sélecteur TV contient « TOUT » et les catégories Live importées du serveur avec leur compteur actuel.
- « TOUT » conserve la vue agrégée : les sections « Récemment regardées » et « Favoris », lorsqu'elles ne sont pas vides, y restent disponibles ; elles ne deviennent pas des catégories de serveur dans le sélecteur.
- Une catégorie sans chaîne reste sélectionnable et affiche ensuite l'état vide normal de la vue filtrée.
- La liste doit rester utilisable quand les catégories ou compteurs sont en chargement : aucun libellé, compteur ou choix fictif n'est présenté.
- La présentation mobile garde les puces horizontales et leur comportement actuel.

### Critères d'acceptation

- En TV, aucune rangée de puces de catégorie n'est affichée ; un seul sélecteur affiche la sélection courante.
- La liste est utilisable intégralement au D-Pad, focalise le choix actif à son ouverture et affiche les compteurs quand ils sont connus.
- Choisir une catégorie applique précisément le filtre, ferme la liste et restitue le focus au sélecteur.
- Annuler ne change ni la catégorie ni la position de consultation ; le mobile ne régresse pas.

### Cas limites et erreurs

- Sans catégorie disponible, le sélecteur reste lisible mais ne propose aucun choix invalide ; l'écran conserve son état vide ou de chargement normal.
- Si les catégories sont rafraîchies pendant que la liste est ouverte, la sélection disparue revient de façon sûre à « TOUT » ou à l'état défini par l'écran, jamais vers une catégorie différente.
- Le retour système depuis la liste est équivalent à « Annuler ».

---

# 4. Spécification technique

## Point de départ favorable

Les deux composants cibles existent déjà et sont éprouvés : `TvCategorySelectorTrigger` et `TvCategoryPickerDialog` (`presentation/components/TvCategoryPicker.kt`), utilisés par `VodScreen.kt:360,426` et `SeriesScreen.kt:350,416`. Ce ticket est un **alignement**, pas une création : aucun composant nouveau n'est nécessaire.

Trois conditions préalables sont déjà remplies côté données :

1. **La catégorie « Tout » existe déjà dans le modèle.** `LiveTvViewModel.kt:202` injecte une catégorie synthétique en tête de liste :
   ```kotlin
   val finalCategories = listOf(LiveCategory("all", "Tout", 0)) + categories
   ```
   `state.categories` est donc directement projetable en entrées de sélecteur, exactement comme pour Films et Séries.
2. **Les compteurs sont disponibles** via `state.categoryCounts`.
3. **La projection en `CategorySheetEntry` est déjà écrite** pour la feuille mobile, à `LiveTvScreen.kt:549-553`. Le chemin TV reprend la même expression, y compris le traitement du total pour « Tout ».

## Modification

Dans `LiveTvScreen.TvLayout`, le bloc `LazyRow` de puces (lignes 269-291) est remplacé par le déclencheur, sur le modèle de `VodScreen.kt:355-368` :

```kotlin
var showCategoryPicker by remember { mutableStateOf(false) }
val categoryTriggerFocusRequester = remember { FocusRequester() }
...
Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
) {
    TvCategorySelectorTrigger(
        label = stringResource(
            R.string.livetv_category_selector_label,
            (state.selectedCategory?.categoryName ?: "Tout").uppercase()
        ),
        onClick = { showCategoryPicker = true },
        modifier = Modifier.weight(1f).focusRequester(categoryTriggerFocusRequester)
    )
    IconButton(onClick = onRefresh) {
        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.livetv_refresh), tint = Color.White)
    }
}
```

Le bouton de rafraîchissement est **conservé** : il n'entre pas dans le périmètre de ce ticket et reste la seule commande de resynchronisation manuelle de l'écran.

Le dialogue et la restitution du focus reprennent littéralement le patron de `VodScreen.kt:425-446`, dont le commentaire documente le motif (focus perdu à la fermeture, redemandé seulement après une ouverture réelle pour ne pas entrer en conflit avec le focus initial de B17) :

```kotlin
if (showCategoryPicker) {
    TvCategoryPickerDialog(
        entries = state.categories.map { category ->
            CategorySheetEntry(
                id = category.categoryId,
                label = category.categoryName,
                count = if (category.categoryId == "all") totalCount
                        else state.categoryCounts[category.categoryId]
            )
        },
        selectedId = state.selectedCategory?.categoryId,
        onSelect = { id ->
            state.categories.firstOrNull { it.categoryId == id }?.let(onCategorySelected)
            showCategoryPicker = false
        },
        onDismiss = { showCategoryPicker = false }
    )
}
var hasOpenedCategoryPicker by remember { mutableStateOf(false) }
LaunchedEffect(showCategoryPicker) {
    if (showCategoryPicker) hasOpenedCategoryPicker = true
    else if (hasOpenedCategoryPicker) runCatching { categoryTriggerFocusRequester.requestFocus() }
}
```

Les cas limites de l'étape 2 sont couverts sans code supplémentaire : `onDismiss` ne touche pas à la sélection (retour système équivalent à « Annuler ») ; `firstOrNull` protège d'une catégorie disparue pendant l'ouverture ; une liste d'entrées vide produit un dialogue vide sans choix invalide ; un `count` à `null` n'affiche aucun compteur (`entry.count?.let { ... }`, `TvCategoryPicker.kt:102`), ce qui satisfait la règle « aucun compteur fictif ».

## Code devenu mort

`CategoryFilterChip` (`presentation/livetv/components/LiveTvComponents.kt:356`) n'a qu'un seul appelant, `LiveTvScreen.kt:282`, qui disparaît. Le composable devient inutilisé et doit être **supprimé**.

Attention à ne pas confondre : `VodScreen.kt:1016` et `SeriesScreen.kt:989` déclarent chacun leur propre `CategoryFilterChip` **privé**, sans rapport avec celui-ci. Ils ne sont pas concernés.

## Nouvelle ressource

`app/src/main/res/values/strings.xml` — aligné sur les libellés voisins (`vod_category_selector_label` = `FILMS : %1$s`, ligne 155) :

```xml
<string name="livetv_category_selector_label">DIRECT : %1$s</string>
```

À décliner dans `values-en/` si le fichier existe (le projet déclare `resourceConfigurations = ["fr", "en"]`). Vérifier au passage l'existence de `livetv_refresh` : le `contentDescription` actuel est la chaîne codée en dur `"Rafraîchir"` (`LiveTvScreen.kt:289`), à externaliser à cette occasion.

## Composants impactés

| Fichier | Nature |
| --- | --- |
| `presentation/livetv/LiveTvScreen.kt` | `TvLayout` : puces → déclencheur + dialogue + restitution du focus |
| `presentation/livetv/components/LiveTvComponents.kt` | Suppression de `CategoryFilterChip` devenu mort |
| `res/values/strings.xml` (+ `values-en/`) | `livetv_category_selector_label`, `livetv_refresh` |

Aucune modification de `LiveTvViewModel`, de `LiveTvState`, de la couche `data` ni du schéma. Aucune nouvelle dépendance. **La disposition mobile n'est pas touchée** : `MobileLayout` conserve sa feuille de catégories (`LiveTvScreen.kt:549`) et ses puces.

## Risques techniques

1. **Conflit de fusion avec T12 et F25/F26.** Les trois tickets modifient `LiveTvScreen.kt`, dont deux le même bloc `Column` d'en-tête. À séquencer, pas à paralléliser.
2. **Focus initial.** `rememberTvInitialFocus` (`LiveTvScreen.kt:257`) place le focus sur le premier média, pas sur le bandeau. Le remplacement de la `LazyRow` — qui était un `focusGroup()` — par un déclencheur unique change la topologie de focus de l'en-tête. À vérifier : la remontée D-Pad depuis la première rangée doit atteindre le déclencheur, et non le sauter vers la barre latérale. Films et Séries ayant déjà cette structure et ne présentant pas le défaut, le risque reste faible.
3. **Écart de largeur.** Sur Films et Séries, le déclencheur partage sa ligne avec un champ de recherche et un bouton de filtres. Ici il ne côtoie que le bouton de rafraîchissement : avec `weight(1f)` il occupera donc presque toute la largeur. Conforme à la spécification (« un seul sélecteur affiche la sélection courante »), mais à confronter au référentiel `docs/design-reference/` avant livraison.

## Contraintes de performance

Favorable : la `LazyRow` composait et mesurait une puce par catégorie à chaque affichage de l'écran, sur des catalogues comptant couramment plusieurs dizaines de catégories Live. Le déclencheur est un `Row` unique, et la liste complète n'est composée qu'à l'ouverture du dialogue.

## Validation automatisable

Aucun test unitaire pertinent : la modification est déclarative et `LiveTvViewModelTest` n'est pas concerné, le ViewModel étant inchangé. Validation par `assembleDebug` + `lintDebug` + non-régression de `testDebugUnitTest`. Vérifier en review que `CategoryFilterChip` n'a plus aucun appelant avant suppression.

---

# 5. Architecture

Remplacement de la ligne de puces `CategoryFilterChips` par le sélecteur standardisé `TvCategorySelectorTrigger` dans l'écran de Live TV. Le dialogue `TvCategoryPickerDialog` s'occupera d'afficher la liste complète des catégories avec leur compteur de chaînes associé, puis de restituer proprement le focus au sélecteur après fermeture.

---

# 6. Plan de développement

## Liste des tâches

- [x] Tâche 1 — Déclarer le libellé de catégorie et la description de rafraîchissement dans les ressources

  **Objectif :**
  Ajouter la clé de chaîne `livetv_category_selector_label` dans les fichiers de ressources de strings pour le sélecteur de catégorie TV Live. Remplacer la chaîne en dur de la description de l'icône de rafraîchissement par une ressource `livetv_refresh`.

  **Fichiers :**
  - `app/src/main/res/values/strings.xml`

  **Validation :**
  - Ressources de strings accessibles sans erreur de compilation.

- [x] Tâche 2 — Intégrer le déclencheur de sélection et le dialogue de catégorie dans `LiveTvScreen`

  **Objectif :**
  Dans `LiveTvScreen.TvLayout`, remplacer le composant `LazyRow` de puces par le composant `TvCategorySelectorTrigger` à gauche du bouton de rafraîchissement. Ajouter le dialogue `TvCategoryPickerDialog` et le bloc `LaunchedEffect` associé pour restituer proprement le focus au sélecteur de catégorie après la fermeture du dialogue.

  **Fichiers :**
  - `presentation/livetv/LiveTvScreen.kt`

  **Validation :**
  - Le code compile avec succès.

- [x] Tâche 3 — Supprimer le composant mort `CategoryFilterChip`

  **Objectif :**
  Supprimer le composant inutilisé `CategoryFilterChip` dans les composants du Live TV pour éviter le code mort.

  **Fichiers :**
  - `presentation/livetv/components/LiveTvComponents.kt`

  **Validation :**
  - Compilation et tests unitaires d'origine réussis (`./gradlew testDebugUnitTest`).

---

# 7. Notes de développement

- 2026-08-05 : la rangée de puces TV a été remplacée par `TvCategorySelectorTrigger` et `TvCategoryPickerDialog`, avec restitution du focus au déclencheur après fermeture.
- Le composable Live `CategoryFilterChip` devenu sans appelant a été supprimé.
- Le libellé `livetv_category_selector_label` a été ajouté. La description du bouton de rafraîchissement réutilise la ressource générique existante `catalog_refresh_button_description` plutôt que de dupliquer une chaîne `livetv_refresh` identique.
- 2026-08-05 (étape 7) — Correction F24-R1 : extraction de la résolution pure de la cible de focus dans `resolveCategoryPickerFocusTarget` (`TvCategoryPicker.kt`), et `LaunchedEffect(Unit)` remplacé par `LaunchedEffect(focusTargetId)` pour redemander le focus chaque fois que la cible change (pas seulement à l'ouverture). Couvert par `TvCategoryPickerTest.kt` (sélection présente, sélection disparue, `selectedId` nul, liste vide). Le dialogue étant partagé, Films et Séries bénéficient du correctif sans modification de leur côté.

---

# 8. Review

Date : 2026-08-05

Status: RESOLVED

## Critique

Aucun.

## Majeur

### F24-R1 — Le focus actif n'est pas restauré si la liste change pendant que le dialogue est ouvert

**Description :** le cas limite du ticket exige qu'une catégorie disparue pendant l'ouverture revienne sûrement à « Tout » ou à l'état de l'écran. Le ViewModel recalcule bien `selectedCategory`, mais `TvCategoryPickerDialog` ne demande le focus que dans `LaunchedEffect(Unit)`. Si l'entrée actuellement focalisée est retirée après un rafraîchissement, `focusTargetId` et le modificateur `focusRequester` changent sans nouvelle demande de focus vers la cible de repli.

**Impact :** dans ce cas limite explicite, le dialogue TV peut perdre sa cible active ou laisser la navigation D-Pad dans un état non déterministe. La garantie de retour sûr vers « Tout » et d'utilisation intégrale au D-Pad n'est pas assurée.

**Correction attendue :** redemander le focus lorsque `focusTargetId` change, après composition de la nouvelle entrée cible, et couvrir la résolution pure de la cible de repli par un test JVM. Comme `TvCategoryPickerDialog` est partagé avec Films et Séries, vérifier leur non-régression à l'étape 7.

**Résolution (étape 7, 2026-08-05) :** `focusTargetId` résolu par la fonction pure `resolveCategoryPickerFocusTarget` (testée en JVM), et le `LaunchedEffect` clé désormais sur `focusTargetId` au lieu de `Unit` : toute disparition de l'entrée focalisée pendant l'ouverture redemande le focus vers la cible de repli. Signature de `TvCategoryPickerDialog` inchangée : Films et Séries héritent du correctif sans modification.

## Mineur

Aucun.

## Corrections demandées

- [x] F24-R1 — Restauration du focus rendue réactive aux changements d'entrées ou de sélection du dialogue.

## Points conformes

- La rangée de puces TV est supprimée et remplacée par le sélecteur commun ; le mobile conserve son parcours existant.
- Le dialogue projette « Tout » et les catégories serveur, avec compteurs facultatifs, et protège la sélection par `firstOrNull`.
- La fermeture ou l'annulation ne modifie pas la catégorie et redonne le focus au déclencheur après une ouverture réelle.
- Le bouton de rafraîchissement reste présent et sa description n'est plus codée en dur.
- `CategoryFilterChip` n'a plus d'appelant Live et a été retiré sans toucher aux composables privés homonymes de Films et Séries.

## Vérifications automatisées

- `./gradlew --no-daemon --max-workers=1 testDebugUnitTest assembleDebug lintDebug` : **BUILD SUCCESSFUL** (2026-08-05).
- `git diff --check` : aucune anomalie.
- `TvCategoryPickerTest.kt` covers the pure resolution of focus target (selection present, disappeared, selectedId null, empty list). The real D-Pad geometry remains outside the scope of JVM tests (rule 9).

---

# 9. Release

Version :
v1.73.0

Commit :
✨ feat(tv): sélecteur Live TV, masquage recherche et icône clear (F24, F27, B21)

Date :
2026-08-05
