# B21 - TV Search Clear Icon Unreachable

## Informations générales

Status:
RELEASED

Created:
2026-08-05

---

# 1. Description

La croix/icône de nettoyage (clear icon) dans la barre de recherche TV n'est pas reachable via la navigation D-Pad de la télécommande. Il est donc inutile et confus de l'afficher en mode TV.

---

# 2. Contexte

Le composant de recherche (`OutlinedTextField`) possède un bouton d'effacement de texte (`trailingIcon`) contenant une icône de croix. En mode TV, la navigation D-Pad cible l'ensemble du champ de texte mais ne permet pas d'accéder au bouton interne d'effacement, le rendant inutilisable.

---

# 3. Spécification fonctionnelle

### User story

En tant qu'utilisateur Android TV, je n'ai pas à rencontrer une croix visible mais inatteignable dans les champs de recherche ; l'interface n'expose que des actions réellement utilisables au D-Pad.

### Parcours utilisateur

1. Sur l'écran Recherche, ou dans l'en-tête d'une catégorie Films/Séries, l'utilisateur donne le focus au champ et saisit du texte avec le clavier TV physique ou virtuel.
2. Tant que du texte est présent, aucune croix d'effacement individuelle n'est affichée en mode TV.
3. L'utilisateur peut corriger ou effacer son texte avec les mécanismes standard du clavier. Les résultats et filtres réagissent comme avant.
4. En mode mobile, lorsqu'il y a du texte, la croix tactile reste affichée et vide le champ au toucher.

### Règles métier

- Le retrait concerne uniquement les croix internes aux champs de recherche des écrans Recherche, Films et Séries en mode TV.
- Il ne retire ni le champ, ni son icône de recherche, ni les boutons de filtres avancés, ni les actions de suppression de filtres.
- L'absence de croix ne modifie pas la requête, les résultats ou les filtres déjà actifs.

### Critères d'acceptation

- En TV, aucune croix d'effacement n'apparaît dans ces champs, vide ou non.
- En mobile, la croix apparaît avec une requête non vide et vide effectivement la requête au toucher.
- La navigation D-Pad entre les contrôles voisins reste continue et ne cible plus une action interne inaccessible.

### Cas limites et erreurs

- Une requête vide ne présente aucune action d'effacement dans les deux modes.
- Un changement de catégorie ou un retour à la recherche globale conserve ses règles existantes de réinitialisation de requête.
- Les dialogues ou feuilles possédant leur propre recherche ne changent pas de comportement dans ce ticket.

---

# 4. Spécification technique

## Relevé des champs concernés

Le relevé demandé à l'étape 2 a été fait. Les croix d'effacement (`trailingIcon` + `Icons.Default.Close`) des champs de recherche TV sont exactement au nombre de trois :

| Fichier | Ligne | Écran |
| --- | --- | --- |
| `presentation/search/SearchScreen.kt` | 132-143 | Recherche globale |
| `presentation/vod/VodScreen.kt` | 389-395 | En-tête de catégorie Films (TV) |
| `presentation/series/SeriesScreen.kt` | 379-385 | En-tête de catégorie Séries (TV) |

Les autres `trailingIcon` du projet sont hors périmètre et confirmés comme tels :

* `LoginScreen.kt:226,258,304` — champs de connexion (bascule de visibilité du mot de passe), pas des recherches.
* `CatalogFilterComponents.kt:113,189` — `CategorySearchField`, utilisé uniquement par les dispositions **mobiles** (`LiveTvScreen.kt:651`, `VodScreen.kt:735`) ; explicitement exclu par la règle métier.
* `LiveTvScreen.kt:390` — champ de recherche de catégorie Live en TV : il n'a **qu'un** `leadingIcon`, aucune croix. Rien à corriger.

## Réduction de périmètre décidée à l'étape 3

**F27** supprime intégralement le champ de recherche des en-têtes de catégorie Films et Séries en mode TV — donc aussi sa croix. Traiter ici les lignes `VodScreen.kt:389` et `SeriesScreen.kt:379` reviendrait à écrire du code que F27 supprimerait ensuite.

Arbitrage validé avec le PO : **F27 est livré en premier**, et B21 se limite à l'écran Recherche global. Le périmètre passe de trois fichiers à un seul. Les critères d'acceptation de l'étape 2 restent tous satisfaits, la couverture des écrans Films et Séries étant assurée par F27 — de façon plus radicale encore, puisque le champ entier disparaît.

Ce ticket porte donc **une seule modification**.

## Modification

`SearchScreen.kt` dispose déjà de la variable `isTv` dans la portée du champ ; elle est utilisée plus bas dans le même composable (`tvPivotVerticalStartSpacer(isTv)`, ligne 227). Aucune plomberie supplémentaire n'est nécessaire.

Le paramètre `trailingIcon` d'`OutlinedTextField` est de type `@Composable (() -> Unit)?` : lui passer `null` est le moyen normal de ne rien afficher, et supprime le composant du layout plutôt que de le rendre transparent.

```kotlin
// SearchScreen.kt, remplace le bloc 132-143
trailingIcon = if (isTv) null else {
    {
        if (state.searchQuery.isNotEmpty()) {
            IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.common_clear),
                    tint = Color.Gray
                )
            }
        }
    }
}
```

Le comportement mobile est préservé au caractère près : condition `isNotEmpty()`, action `onSearchQueryChanged("")`, libellé `R.string.common_clear`.

## Composants impactés

| Fichier | Nature |
| --- | --- |
| `presentation/search/SearchScreen.kt` | `trailingIcon` conditionné à `isTv` |

Aucun nouveau composant, aucune nouvelle dépendance, aucun changement de ViewModel, de couche `data`, de ressource ni de schéma. `R.string.common_clear` reste utilisé par le chemin mobile.

## Risques techniques

1. **Dépendance d'ordonnancement.** Si B21 était livré avant F27, les écrans Films et Séries garderaient leur croix inatteignable jusqu'à la livraison de F27, et le critère d'acceptation « en TV, aucune croix dans ces champs » serait temporairement faux. L'ordre F27 → B21 doit être tenu, ou les deux livrés ensemble.
2. **Aucun impact sur la largeur utile du champ en TV.** Retirer le `trailingIcon` élargit légèrement la zone de texte. Sans conséquence : le champ occupe déjà `Modifier.weight(1f)` et le placeholder est en `maxLines = 1` avec `TextOverflow.Ellipsis`.
3. **Pas de risque de régression du focus.** La croix n'a jamais été atteignable au D-Pad — c'est le motif du ticket. La supprimer ne retire donc aucune cible de navigation existante.

## Contraintes de performance

Négligeables, avec un gain marginal : un `IconButton` de moins composé et mesuré à chaque frappe en mode TV.

## Validation automatisable

Aucune. La modification est purement déclarative dans un `@Composable`, et le projet ne dispose pas de tests d'interface Compose (règle n°9 : toute vérification exigeant un appareil est exclue des critères de validation de l'agent). La validation se limite à `./gradlew assembleDebug` et `lintDebug` sans erreur, plus la non-régression de `testDebugUnitTest`. Le contrôle visuel revient au PO.

---

# 5. Architecture

Modification au niveau de la présentation de l'écran de recherche global. Le paramètre `trailingIcon` du champ `OutlinedTextField` de recherche est désactivé (passé à `null`) lorsque `isTv` est vrai, empêchant l'affichage et la présence du bouton de croix inatteignable.

---

# 6. Plan de développement

## Liste des tâches

- [x] Tâche 1 — Conditionner le `trailingIcon` à `isTv` dans `SearchScreen`

  **Objectif :**
  Désactiver l'icône de nettoyage (`trailingIcon` passé à `null`) de l'OutlinedTextField de recherche dans `SearchScreen.kt` si l'application s'exécute en mode TV (`isTv == true`).

  **Fichiers :**
  - `presentation/search/SearchScreen.kt`

  **Validation :**
  - L'arbre de compilation passe et `./gradlew lintDebug` est sans erreur.

---

# 7. Notes de développement

- 2026-08-05 : `SearchScreen` passe `trailingIcon` à `null` en mode TV et conserve sans changement le bouton d'effacement mobile.
- La couverture des en-têtes Films et Séries dépend de F27, qui supprime leurs champs de recherche TV complets conformément à l'ordonnancement décidé.

---

# 8. Review

Date : 2026-08-05

Status: ACCEPTED

## Critique

Aucun.

## Majeur

Aucun.

## Mineur

Aucun.

## Corrections demandées

Aucune.

## Points conformes

- En TV, `SearchScreen` fournit bien `null` à `trailingIcon` : l'action inaccessible n'est ni composée ni présente dans le layout.
- En mobile, la croix reste conditionnée à une requête non vide et appelle toujours `onSearchQueryChanged("")`.
- L'icône de recherche, la requête, les résultats et les filtres ne sont pas modifiés.
- F27 retire parallèlement les champs complets des catégories TV Films et Séries ; aucune croix résiduelle n'y subsiste.

## Vérifications automatisées

- `./gradlew --no-daemon --max-workers=1 testDebugUnitTest assembleDebug lintDebug` : **BUILD SUCCESSFUL** (2026-08-05).
- `git diff --check` : aucune anomalie.
- Aucun test UI Compose n'est ajouté pour ce changement purement déclaratif.

---

# 9. Release

Version :
v1.73.0

Commit :
✨ feat(tv): sélecteur Live TV, masquage recherche et icône clear (F24, F27, B21)

Date :
2026-08-05
