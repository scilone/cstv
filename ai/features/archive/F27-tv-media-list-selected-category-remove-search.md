# F27 - TV Media List Selected Category Remove Search

## Informations générales

Status:
RELEASED

Created:
2026-08-05

---

# 1. Description

Sur les listes de Films (VOD) et Séries, lorsqu'une catégorie spécifique est sélectionnée (mode grille vertical), retirer complètement la barre de recherche textuelle du bandeau d'en-tête, et ne conserver que le déclencheur de sélection de catégorie (TvCategorySelectorTrigger) et le bouton de filtres avancés.

---

# 2. Contexte

Actuellement, l'en-tête d'une catégorie sélectionnée affiche à la fois le dropdown de catégorie, un champ de recherche textuel, et un bouton de filtre. Sur un écran de télévision, la saisie textuelle est fastidieuse à la télécommande, et la présence d'une recherche locale à cet endroit encombre l'écran et fait doublon avec l'écran de recherche dédié global.

---

# 3. Spécification fonctionnelle

### User story

En tant qu'utilisateur Android TV, lorsqu'une catégorie Films ou Séries est ouverte, je dispose d'un bandeau simple consacré à la catégorie et aux filtres plutôt que d'une recherche textuelle encombrante et redondante.

### Parcours utilisateur

1. L'utilisateur ouvre une catégorie précise de Films ou Séries sur TV.
2. Le bandeau contient le sélecteur de catégorie, occupant l'espace disponible, et le bouton de filtres avancés à droite.
3. Il peut changer de catégorie ou ouvrir les filtres avancés au D-Pad.
4. Pour une recherche par texte, il rejoint l'écran Recherche global depuis la barre latérale.
5. En mode mobile, il retrouve la recherche de catégorie existante.

### Règles métier

- La suppression du champ texte s'applique uniquement aux vues TV de catégorie spécifique Films et Séries.
- Elle ne s'applique pas à « TOUT », à l'écran Recherche global, aux filtres avancés ni au mobile.
- Au sein d'une même catégorie, un filtre avancé actif continue de filtrer la grille ; son état visible et son bouton ne changent pas.
- Changer de catégorie réinitialise le filtre avancé à sa valeur par défaut (comportement F22 pré-existant, inchangé par ce ticket) : ce n'est pas une régression introduite ici.
- Lors d'un passage à une catégorie précise, toute requête texte locale résiduelle ne doit pas influencer les résultats TV sans champ pour la modifier.

### Critères d'acceptation

- En TV et dans une catégorie précise, le champ de recherche texte et sa croix ne sont plus présents sur Films et Séries.
- Le sélecteur et le bouton de filtres sont atteignables et correctement espacés au D-Pad.
- Les résultats restent filtrables par les critères avancés au sein d'une catégorie, et la recherche globale reste accessible.
- Le mode mobile conserve sa recherche locale de catégorie et le comportement de « TOUT » TV n'est pas régressé.

### Cas limites et erreurs

- Une catégorie sans résultat ou filtrée à zéro affiche l'état vide existant sans proposer de recherche locale cachée.
- Un filtre avancé actif reste signalé et peut être retiré avec le parcours existant tant que la catégorie ne change pas ; un changement de catégorie le réinitialise (F22).
- Les longs libellés de catégorie restent lisibles ou tronqués proprement sans recouvrir le bouton de filtres.

---

# 4. Spécification technique

## Emplacements exacts

Les deux en-têtes sont structurellement identiques, à la chaîne de libellé près :

| Fichier | Bloc à supprimer | Contexte |
| --- | --- | --- |
| `presentation/vod/VodScreen.kt` | 369-408 | `OutlinedTextField` dans `if (isSpecificCategory)` |
| `presentation/series/SeriesScreen.kt` | 359-398 | idem |

Structure actuelle de la ligne d'en-tête (`VodScreen.kt:354-424`) :

```kotlin
Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
    TvCategorySelectorTrigger(label = ..., onClick = { showCategoryPicker = true },
        modifier = Modifier.weight(1f).focusRequester(categoryTriggerFocusRequester))
    if (isSpecificCategory) {
        Spacer(modifier = Modifier.width(12.dp))
        OutlinedTextField(value = searchQuery, ...  modifier = Modifier.weight(1f))   // ← supprimé
        Spacer(modifier = Modifier.width(12.dp))
        IconButton(onClick = onFilterSheetOpen, ...) { Icon(Icons.Default.Tune, ...) }
    }
}
```

## Modification

Le `OutlinedTextField` et **un seul** des deux `Spacer` sont supprimés. Le résultat :

```kotlin
Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
    TvCategorySelectorTrigger(label = ..., onClick = { showCategoryPicker = true },
        modifier = Modifier.weight(1f).focusRequester(categoryTriggerFocusRequester))
    if (isSpecificCategory) {
        Spacer(modifier = Modifier.width(12.dp))
        IconButton(onClick = onFilterSheetOpen, ...) { Icon(Icons.Default.Tune, ...) }
    }
}
```

Le déclencheur porte déjà `Modifier.weight(1f)` : il absorbe automatiquement la largeur libérée, sans calcul de dimension. Le bouton de filtres conserve sa taille intrinsèque et son état actif (`state.advancedFilter.isActive`), inchangés. L'espacement de 12 dp entre les deux est préservé.

Aucune modification n'est apportée au `TvCategoryPickerDialog` ni au bloc de restitution du focus qui suit (`VodScreen.kt:425-446`).

## Requête textuelle résiduelle — déjà couverte

La règle métier « toute requête texte locale résiduelle ne doit pas influencer les résultats TV » est **déjà satisfaite par le code existant**. `VodScreen.kt:114-117` et `SeriesScreen.kt:115-118` contiennent :

```kotlin
// Reset search query when the selected category changes
LaunchedEffect(state.selectedCategory) {
    searchQuery = ""
}
```

Toute entrée dans une catégorie précise remet donc `searchQuery` à la chaîne vide. Sans champ pour la modifier en TV, elle y restera vide pour toute la durée de la consultation.

Il faut souligner que `searchQuery` **n'est pas** un paramètre de la requête paginée : `VodViewModel.pagedStreams` (`VodViewModel.kt:103-108`) n'observe que `selectedCategory`, et `getVodStreamsPaged(categoryId)` (`VodRepository.kt:14`) ne prend pas de requête. Le filtrage textuel est appliqué côté présentation, sur le flux paginé (`VodScreen.kt:135-143`) :

```kotlin
val pagedStreams = remember(viewModel.pagedStreams, searchQuery, state.advancedFilter) {
    if (searchQuery.isBlank() && state.advancedFilter.isEmpty) { ... }
    else { ...filter { (searchQuery.isBlank() || it.name.contains(searchQuery, true)) && ... } }
}
```

Avec `searchQuery` toujours vide en TV, la branche sans filtre textuel est empruntée et **les filtres avancés continuent de s'appliquer normalement** — critère d'acceptation « les résultats restent filtrables par les critères avancés ». Aucune variable, aucun état, aucune signature de fonction n'a besoin d'être retiré : `searchQuery` reste utilisé par la disposition mobile du même écran.

## Ce qui n'est pas touché

* **Le mobile** : `MobileLayout` conserve son `CategorySearchField` (`VodScreen.kt:819`) et son comportement.
* **La vue « TOUT » en TV** : le champ n'y a jamais été affiché, la condition `isSpecificCategory` l'excluait déjà.
* **L'écran Recherche global**, la feuille de filtres avancés, les chips de filtres actifs.
* **Live TV** : son champ de recherche de catégorie TV (`LiveTvScreen.kt:390`) n'entre pas dans le périmètre de ce ticket, restreint à Films et Séries par la règle métier de l'étape 2.

## Composants impactés

| Fichier | Nature |
| --- | --- |
| `presentation/vod/VodScreen.kt` | Suppression du champ de recherche de l'en-tête TV |
| `presentation/series/SeriesScreen.kt` | idem |

Aucun nouveau composant, aucune nouvelle dépendance, aucune ressource nouvelle, aucun changement de ViewModel, de couche `data` ni de schéma.

`R.string.vod_search_placeholder` et `R.string.series_search_placeholder` deviennent inutilisées en TV mais **restent utilisées par le mobile** : ne pas les supprimer.

## Conséquence sur B21

F27 supprimant le champ entier, il emporte la croix d'effacement des lignes `VodScreen.kt:389` et `SeriesScreen.kt:379`. Le périmètre de **B21** a été réduit en conséquence à l'écran Recherche global uniquement, et **F27 doit être livré avant B21** — arbitrage validé avec le PO à l'étape 3.

## Risques techniques

1. **Ordonnancement avec B21.** Voir ci-dessus : inverser l'ordre ferait écrire puis supprimer le même code.
2. **Conflit de fusion avec T12.** T12 modifie `VodScreen.kt:466,575` et `SeriesScreen.kt:456,563` — mêmes fichiers, blocs distincts. À séquencer.
3. **Navigation D-Pad de l'en-tête.** Le passage de trois cibles focalisables à deux modifie la chaîne de focus horizontale. Le déclencheur et le bouton de filtres restent tous deux atteignables, mais il faut vérifier que la descente du bouton de filtres vers la grille reste possible : c'était auparavant le champ de texte qui occupait la position centrale. Risque faible, la vue « TOUT » présentant déjà un en-tête à cible unique sans anomalie connue.
4. **Perte fonctionnelle assumée.** La recherche locale dans une catégorie disparaît en TV. C'est l'objet même du ticket, justifié à l'étape 2 par la redondance avec l'écran Recherche global et la pénibilité de la saisie à la télécommande. À rappeler au PO avant livraison, la fonctionnalité restant disponible en mobile.

## Contraintes de performance

Favorable, à la marge : un `OutlinedTextField` en moins composé et mesuré dans l'en-tête, et surtout la disparition de la recomposition de l'en-tête à chaque frappe. Le `remember(viewModel.pagedStreams, searchQuery, state.advancedFilter)` de la ligne 135 ne sera plus invalidé par la saisie en TV.

## Validation automatisable

Aucun test unitaire pertinent : la modification est déclarative et aucun ViewModel n'est touché. Validation par `assembleDebug`, `lintDebug` et non-régression de `testDebugUnitTest`. Vérifier en review que `searchQuery` reste bien déclarée et utilisée par la disposition mobile dans les deux fichiers.

---

# 5. Architecture

Suppression du champ `OutlinedTextField` de recherche textuelle dans l'en-tête de catégorie spécifique sur TV pour les écrans de Films (VOD) et Séries. Le bouton d'ouverture du dialogue de filtres avancés est conservé à côté du sélecteur de catégorie.

---

# 6. Plan de développement

## Liste des tâches

- [x] Tâche 1 — Retirer la barre de recherche TV dans l'en-tête des Films (VOD)

  **Objectif :**
  Supprimer le champ de recherche textuelle `OutlinedTextField` (et l'un des spacers associés) dans l'en-tête de catégorie spécifique TV de `VodScreen.kt`. Le sélecteur de catégorie s'agrandit pour occuper la largeur libérée.

  **Fichiers :**
  - `presentation/vod/VodScreen.kt`

  **Validation :**
  - `VodScreen` compile sans erreur.

- [x] Tâche 2 — Retirer la barre de recherche TV dans l'en-tête des Séries

  **Objectif :**
  Supprimer le champ de recherche textuelle `OutlinedTextField` (et l'un des spacers associés) dans l'en-tête de catégorie spécifique TV de `SeriesScreen.kt`.

  **Fichiers :**
  - `presentation/series/SeriesScreen.kt`

  **Validation :**
  - `SeriesScreen` compile sans erreur, et `./gradlew assembleDebug` réussit.

---

# 7. Notes de développement

- 2026-08-05 : suppression des deux `OutlinedTextField` TV de catégorie spécifique dans `VodScreen.kt` et `SeriesScreen.kt`. Le sélecteur conserve `weight(1f)` et un espacement de 12 dp avant le bouton de filtres.

---

# 8. Review

Date : 2026-08-05

Status: RESOLVED

## Critique

Aucun.

## Majeur

### F27-R1 — La conservation des filtres après un changement de catégorie n'est pas respectée

**Description :** la spécification fonctionnelle indique qu'un filtre avancé actif reste signalé après le changement de catégorie et continue de filtrer la grille. Or `VodViewModel.selectCategory()` et `SeriesViewModel.selectCategory()` remplacent explicitement `advancedFilter` par `AdvancedSearchFilter.DEFAULT`, ferment la feuille et remettent `filteredCount` à zéro. Ce comportement existant est antérieur à F27, mais il contredit directement le contrat du ticket revu.

**Impact :** un utilisateur qui change de catégorie perd tous ses critères avancés. L'état actif n'est plus visible et la nouvelle grille n'est pas filtrée comme annoncé dans les règles et cas limites de F27.

**Correction attendue :** arbitrer le contrat avec le PO avant l'étape 7. Soit F27 est corrigé pour refléter la réinitialisation volontaire définie par F22, soit les deux ViewModels et leurs tests sont adaptés pour conserver les filtres lors d'un changement de catégorie. Ne pas changer silencieusement ce comportement.

**Résolution (étape 7, 2026-08-05) :** arbitrage PO rendu — le reset de `advancedFilter` au changement de catégorie est le comportement voulu (décision F22), pas une régression. La spécification fonctionnelle de F27 (règles métier, critères d'acceptation, cas limites) a été corrigée en conséquence pour ne plus annoncer une persistance inexistante. Aucun changement de code dans `VodViewModel` / `SeriesViewModel`.

## Mineur

Aucun.

## Corrections demandées

- [x] F27-R1 — Contradiction résolue par correction de la documentation (arbitrage PO : comportement F22 conservé).

## Points conformes

- Les champs de recherche TV et leurs croix ont disparu uniquement des catégories spécifiques Films et Séries.
- Le sélecteur et le bouton de filtres restent présents, séparés de 12 dp ; le sélecteur absorbe la largeur libérée.
- Les dispositions mobiles et le mode TV « Tout » conservent leur comportement.
- La requête texte est toujours remise à vide à chaque changement de catégorie et ne peut donc pas filtrer invisiblement la grille TV.

## Vérifications automatisées

- `./gradlew --no-daemon --max-workers=1 testDebugUnitTest assembleDebug lintDebug` : **BUILD SUCCESSFUL** (2026-08-05).
- `git diff --check` : aucune anomalie.
- Aucun test UI Compose n'existe pour la géométrie ou la navigation D-Pad ; ce point n'est pas déclaré validé par les contrôles JVM.

## Validation finale (étape 8)

Date : 2026-08-18

- Comportement attendu : conforme — champ de recherche TV supprimé des catégories spécifiques Films et Séries ; sélecteur et filtres conservés.
- Règles métier : conformes — comportement de la recherche (reset au changement de catégorie) maintenu et aligné sur les décisions PO.
- Absence de régression : `./gradlew testDebugUnitTest assembleDebug lintDebug` — `BUILD SUCCESSFUL`.
- Tests validés : non-régression de l'état de recherche vérifiée.
- Expérience utilisateur : disposition visuelle (espacement 12 dp, masquage) non vérifiable en JVM (règle n°9) — revient au PO sur device.

Status : VALIDATED

---

# 9. Release

Version :
v1.73.0

Commit :
✨ feat(tv): sélecteur Live TV, masquage recherche et icône clear (F24, F27, B21)

Date :
2026-08-05
