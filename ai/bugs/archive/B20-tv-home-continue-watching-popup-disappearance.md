# B20 - TV Home Continue Watching Popup Disappearance

## Informations générales

Status:
RELEASED

Created:
2026-08-05

---

# 1. Description

Sur l'écran d'accueil TV, l'appui long sur un média de la section "Continuer à regarder" (Resume Watching) affiche la popin de confirmation de retrait (HistoryRemovalDialog) mais celle-ci disparaît instantanément. L'utilisateur ne peut donc pas interagir avec le dialogue pour retirer le média.

---

# 2. Contexte

Le projet permet de supprimer un élément de l'historique de lecture ("Continuer à regarder") via un appui long sur TV. Cependant, lors de l'appui long, le dialogue de confirmation s'affiche mais est immédiatement dismissed sans action de l'utilisateur.

---

# 3. Spécification fonctionnelle

### User story

En tant qu'utilisateur Android TV, je peux retirer un élément de « Continuer à regarder » après un appui long et une confirmation explicite, sans risquer une suppression ou une fermeture involontaire.

### Parcours utilisateur

1. L'utilisateur place le focus sur une carte de la rangée « Continuer à regarder » de l'accueil TV et effectue un appui long avec la touche centrale.
2. La boîte de confirmation s'ouvre et garde le focus dans la boîte ; « Annuler » est l'action initialement focalisée.
3. L'utilisateur choisit « Annuler » ou « Confirmer » avec le D-Pad puis la touche centrale.
4. « Annuler » referme la boîte et laisse la carte et l'historique inchangés. « Confirmer » lance la suppression et désactive les deux actions pendant le traitement.
5. En cas de succès, la boîte se ferme et la rangée reflète immédiatement l'historique mis à jour. En cas d'échec, aucune suppression locale trompeuse n'est affichée, un message compréhensible est présenté et la boîte est fermée.

### Règles métier

- L'ouverture d'une confirmation ne doit jamais être interprétée comme une annulation ni comme une confirmation.
- Le relâchement de la touche qui a déclenché l'appui long ne doit déclencher aucune action de la boîte.
- Une seule demande de suppression peut être active à la fois ; pendant celle-ci, aucun double envoi n'est possible.
- Le comportement mobile existant, y compris l'appui long tactile, reste inchangé.

### Critères d'acceptation

- Après un appui long TV, la boîte reste visible jusqu'à une action utilisateur explicite, la fin de la suppression ou une erreur.
- Le focus initial est visible sur « Annuler » et la navigation D-Pad atteint « Annuler » et « Confirmer ».
- « Annuler » n'altère pas l'historique ; « Confirmer » retire uniquement le média demandé après succès.
- Un appui court conserve l'ouverture/lecture normale du média.

### Cas limites et erreurs

- Si la carte disparaît entre l'ouverture et la confirmation (rafraîchissement ou autre action), la boîte se ferme sans supprimer un autre élément.
- Si l'opération échoue, l'élément reste dans « Continuer à regarder » et l'utilisateur reçoit un retour d'erreur non technique.
- La fermeture système de la boîte respecte les mêmes règles qu'« Annuler » lorsqu'aucune suppression n'est en cours.

---

# 4. Spécification technique

## Cause racine

Le geste d'appui long TV est traité par `presentation/components/HistoryItemActions.kt:43-64` :

```kotlin
var consumeKeyUp by remember { mutableStateOf(false) }
accessibilityActions.onPreviewKeyEvent { event ->
    ...
    KeyEventType.KeyDown -> {
        if (native.isLongPress || native.repeatCount > 0) {
            if (!consumeKeyUp) onLongClick()
            consumeKeyUp = true
            true
        } else false
    }
    KeyEventType.KeyUp -> {
        if (consumeKeyUp) { consumeKeyUp = false; true } else false
    }
}.clickable(onClick = onClick)
```

L'intention est correcte : `onLongClick()` est déclenché sur le **KeyDown**, et le drapeau `consumeKeyUp` doit ensuite absorber le **KeyUp** de la même pression pour qu'il ne soit pas interprété comme un clic.

Cette absorption ne se produit jamais. La séquence réelle est la suivante :

1. **KeyDown** long → `onLongClick()` → l'écran positionne `pendingRemoval`, la boîte est composée.
2. `HistoryRemovalDialog` (`HistoryRemovalDialog.kt:27-29`) exécute immédiatement `LaunchedEffect(isTv) { cancelFocusRequester.requestFocus() }`. **Le focus quitte la carte** pour le bouton « Annuler », qui vit dans la fenêtre de dialogue.
3. **KeyUp** → l'événement est routé le long du chemin de focus *courant*. La carte n'y est plus : son `onPreviewKeyEvent` n'est jamais rappelé, et `consumeKeyUp` reste à `true` sans effet.
4. Le KeyUp atteint le bouton « Annuler » focalisé. Le `clickable` de Compose Foundation traite un KeyUp de `KEYCODE_DPAD_CENTER` / `KEYCODE_ENTER` comme une activation, **sans exiger d'avoir observé le KeyDown correspondant**. `onDismiss()` est appelé.
5. La boîte se ferme dans la même pression que celle qui l'a ouverte : l'utilisateur la voit apparaître puis disparaître.

Le défaut n'est donc pas dans la carte mais dans la boîte : elle hérite d'un KeyUp **orphelin**, dont le KeyDown appartenait à un autre composant.

Cela explique aussi pourquoi le mobile n'est pas touché : il emprunte la branche `combinedClickable` (`HistoryItemActions.kt:37-41`), sans événements clavier.

## Choix technique — invariant d'appariement, pas de temporisation

La spécification fonctionnelle interdit de faire d'un délai un contrat. On n'en utilise donc aucun : on rétablit l'invariant naturel d'une activation clavier — **une activation exige un KeyDown puis un KeyUp observés par le même sous-arbre**. Tant qu'aucun KeyDown n'a été vu dans la boîte, tout KeyUp d'activation y est considéré comme orphelin et consommé.

La décision est extraite dans une classe pure, testable en JVM sans Compose ni appareil :

```kotlin
// presentation/components/ActivationKeyGate.kt

/**
 * Filtre les activations clavier orphelines (B20).
 *
 * Une fenêtre ouverte par un appui long hérite du KeyUp de la pression qui l'a
 * ouverte : le KeyDown a été reçu par la carte, le KeyUp par le bouton
 * nouvellement focalisé. Sans appariement, `clickable` l'interprète comme un
 * clic et referme la fenêtre aussitôt.
 */
class ActivationKeyGate {
    private var sawKeyDown = false

    fun onKeyDown() { sawKeyDown = true }

    /** @return true si le KeyUp doit être consommé (orphelin). */
    fun onKeyUp(): Boolean {
        if (!sawKeyDown) return true
        sawKeyDown = false
        return false
    }
}
```

Le modificateur associé, dans le même fichier :

```kotlin
fun Modifier.consumeOrphanActivationKeys(): Modifier = composed {
    val gate = remember { ActivationKeyGate() }
    onPreviewKeyEvent { event ->
        val code = event.nativeKeyEvent.keyCode
        if (code != AndroidKeyEvent.KEYCODE_DPAD_CENTER && code != AndroidKeyEvent.KEYCODE_ENTER) {
            return@onPreviewKeyEvent false
        }
        when (event.type) {
            KeyEventType.KeyDown -> { gate.onKeyDown(); false }
            KeyEventType.KeyUp -> gate.onKeyUp()
            else -> false
        }
    }
}
```

Appliqué à la boîte (`HistoryRemovalDialog.kt`) :

```kotlin
AlertDialog(
    modifier = if (isTv) Modifier.consumeOrphanActivationKeys() else Modifier,
    onDismissRequest = { if (!isRemoving) onDismiss() },
    ...
)
```

Les événements de prévisualisation descendent de la racine vers la feuille le long du chemin de focus. Le modificateur porté par la racine de la boîte voit donc le KeyUp **avant** le bouton focalisé, et le consomme. Le KeyDown suivant, lui, est bien observé par la boîte : la deuxième pression — la première véritable action de l'utilisateur — passe normalement.

## Correction secondaire

`consumeKeyUp` (`HistoryItemActions.kt:43`) reste bloqué à `true` lorsque le focus part pendant l'appui long. Si la carte le récupère plus tard, le premier KeyUp légitime sera avalé et l'appui court sera sans effet. On remet l'état à zéro à la perte du focus :

```kotlin
.onFocusChanged { if (!it.isFocused) consumeKeyUp = false }
```

Ce défaut est indépendant du symptôme principal mais vit dans le même geste ; le laisser produirait un « premier clic ignoré » aléatoire après fermeture de la boîte.

## Composants impactés

| Fichier | Nature |
| --- | --- |
| `presentation/components/ActivationKeyGate.kt` | **Nouveau** — classe pure + modificateur |
| `presentation/components/HistoryRemovalDialog.kt` | Applique `consumeOrphanActivationKeys()` en mode TV |
| `presentation/components/HistoryItemActions.kt` | Réinitialise `consumeKeyUp` à la perte de focus |
| `app/src/test/java/com/cstv/app/presentation/components/ActivationKeyGateTest.kt` | **Nouveau** — couverture de l'appariement |

Le correctif étant porté par la boîte partagée, il couvre d'emblée les quatre appelants : `HomeScreen.kt:770`, `LiveTvScreen.kt:183`, `VodScreen.kt:210`, `SeriesScreen.kt:211`. Aucun de ces fichiers n'est modifié.

Aucune nouvelle dépendance, aucun changement de ViewModel, de couche `data` ni de schéma.

## Risques techniques

1. **Le modificateur de l'`AlertDialog` pourrait ne pas se trouver sur le chemin de focus.** `AlertDialog` de material3 applique son `modifier` à la surface interne, et la fenêtre de dialogue héberge son propre arbre de focus. Si la prévisualisation ne se déclenchait pas, repli : appliquer `consumeOrphanActivationKeys()` sur un `Box` enveloppant le contenu de `confirmButton` **et** de `dismissButton`, chacun avec son propre `ActivationKeyGate`. À vérifier en premier lors de l'implémentation, avant tout autre travail sur ce ticket.
2. **Comportement de `clickable` dépendant de la version.** L'analyse suppose que le `clickable` de Compose Foundation 1.6 (BOM 2024.02.02) active sur un KeyUp non apparié. Si une version ultérieure ajoutait ce garde, le correctif deviendrait redondant mais resterait inoffensif. La solution ne dépend d'aucun détail interne de Compose : elle consomme l'événement avant lui.
3. **Portée volontairement étroite.** Seules les touches `KEYCODE_DPAD_CENTER` et `KEYCODE_ENTER` sont filtrées, uniquement en mode TV. Le bouton « Retour », la navigation directionnelle et le tactile mobile ne sont pas touchés.

## Contraintes de performance

Nulles : un test de code de touche sur des événements déjà routés, sans allocation par événement.

## Validation automatisable

`ActivationKeyGateTest` couvre : KeyUp seul → consommé ; KeyDown puis KeyUp → non consommé ; KeyUp orphelin puis paire complète → seul le premier est consommé ; deux KeyUp orphelins consécutifs → les deux consommés. Le comportement à l'écran n'est pas testable sans appareil et revient au PO.

---

# 5. Architecture

L'architecture s'appuie sur la création d'un garde-fou clavier (`ActivationKeyGate`) dans la couche de présentation. Ce garde-fou intercepte les KeyUp "orphelins" (qui n'ont pas eu de KeyDown correspondant au sein du même composant) et les consomme avant qu'ils n'activent les boutons nouvellement focalisés de la boîte de dialogue de confirmation de retrait.

---

# 6. Plan de développement

## Liste des tâches

- [x] Tâche 1 — Créer `ActivationKeyGate` et son test unitaire JVM

  **Objectif :**
  Créer la classe utilitaire pure `ActivationKeyGate` et le modificateur Compose `consumeOrphanActivationKeys()`. Ajouter des tests unitaires complets pour valider le comportement de la classe pure sous diverses séquences d'événements.

  **Fichiers :**
  - `presentation/components/ActivationKeyGate.kt`
  - `app/src/test/java/com/cstv/app/presentation/components/ActivationKeyGateTest.kt`

  **Validation :**
  - `./gradlew testDebugUnitTest` passe avec succès sur la nouvelle suite de tests.

- [x] Tâche 2 — Intégrer la barrière d'activation clavier et réinitialiser `consumeKeyUp`

  **Objectif :**
  Appliquer le modificateur `consumeOrphanActivationKeys()` à l'AlertDialog de `HistoryRemovalDialog.kt` pour consommer les KeyUp orphelins en TV.
  Dans `HistoryItemActions.kt`, réinitialiser le flag `consumeKeyUp` à `false` dans `onFocusChanged` de la carte lorsque celle-ci perd le focus, afin d'éviter de bloquer des clics ultérieurs.

  **Fichiers :**
  - `presentation/components/HistoryRemovalDialog.kt`
  - `presentation/components/HistoryItemActions.kt`

  **Validation :**
  - Build réussi avec `./gradlew assembleDebug` et `./gradlew lintDebug`.

---

# 7. Notes de développement

- 2026-08-05 — Implémentation relue dans le périmètre prévu : ajout du garde pur `ActivationKeyGate`, interception TV sur la racine de `HistoryRemovalDialog`, remise à zéro de l'état de l'appui long à la perte de focus et tests JVM associés.

---

# 8. Review

Date : 2026-08-05

Status : APPROVED

## Périmètre relu

- `presentation/components/ActivationKeyGate.kt`
- `presentation/components/HistoryRemovalDialog.kt`
- `presentation/components/HistoryItemActions.kt`
- `app/src/test/java/com/cstv/app/presentation/components/ActivationKeyGateTest.kt`

## Critique

Aucun constat.

## Majeur

Aucun constat.

## Mineur

Aucun constat.

## Vérifications effectuées

- L'appariement `KeyDown` / `KeyUp` est limité aux touches d'activation déjà reconnues par le geste TV (`DPAD_CENTER` et `ENTER`) ; la branche tactile mobile reste inchangée.
- Le `modifier` fourni à `AlertDialog` est appliqué, avec Material3 1.2.1 utilisé par le projet, au `Box` parent du contenu du dialogue : son `onPreviewKeyEvent` appartient donc bien au chemin de focus des deux boutons.
- La perte de focus de la carte remet `consumeKeyUp` à `false`, sans transformer le relâchement orphelin en clic puisque celui-ci est désormais absorbé par le dialogue.
- Réexécution forcée de `ActivationKeyGateTest` et `TvPivotScrollTest` : succès (`BUILD SUCCESSFUL`).
- Contrôle global `testDebugUnitTest assembleDebug lintDebug` : succès (`BUILD SUCCESSFUL`) ; les tâches étaient à jour lors de ce contrôle.
- `git diff --check` : aucun défaut d'espaces dans les changements suivis.

## Limite de la review

La séquence réelle de routage clavier dans une fenêtre Android TV et le focus visuel initial ne sont pas reproduits par les tests JVM. Conformément à la stratégie du projet, cette observation sur appareil n'est pas un critère automatisé de validation et ne bloque pas la review technique.

## Corrections demandées

Aucune. Le ticket peut passer à l'étape 8 de validation finale.

---

# 9. Release

Version :
v1.73.0

Commit :
📦 chore(release): bump to v1.73.0 (B20, T12)

Date :
2026-08-05
