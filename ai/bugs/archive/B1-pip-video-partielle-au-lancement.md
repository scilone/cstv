# B1 - Vidéo partielle à l'ouverture du Picture-in-Picture

## Informations générales

Type:
Bug

Status:
RELEASED

Created:
2026-07-21

Target version:
v1.48.30

---

# 1. Description

Après la factorisation des lecteurs T3 (v1.48.29), la vidéo n'occupe qu'une
partie de la fenêtre lors de l'entrée initiale en Picture-in-Picture. Redimensionner
manuellement la fenêtre PiP rétablit immédiatement la bonne surface vidéo.

---

# 2. Contexte

Le callback de transition PiP force actuellement un cycle
`INVISIBLE → post(VISIBLE)` du `PlayerView`. Ce premier `post` peut s'exécuter
avant que l'animation d'entrée PiP ait appliqué les dimensions finales de la
fenêtre. La surface conserve alors les dimensions intermédiaires jusqu'au
prochain événement de layout.

---

# 3. Spécification fonctionnelle

- À l'ouverture du PiP, la vidéo doit remplir immédiatement la zone prévue par
  le `PlayerView`, sans redimensionnement manuel.
- Le comportement doit rester identique pour Live, VOD et Séries.
- La sortie du PiP doit continuer à restaurer correctement la vidéo plein écran.

---

# 4. Spécification technique

Conserver le relayout immédiat existant, puis déclencher un second
`requestLayout` différé sur le `PlayerView` et sa surface après stabilisation de
la fenêtre PiP. Annuler le callback différé lorsque l'effet Compose est disposé.

---

# 5. Architecture

Correction localisée dans `presentation/player/core/PlayerLifecycleCore.kt` ;
aucun changement des trois écrans ni de leur logique métier.

---

# 6. Plan de développement

- [x] Ajouter le relayout différé et son nettoyage lifecycle.
- [x] Exécuter `testDebugUnitTest` et `assembleDebug`.
- [x] Obtenir le verdict final de `lintDebug` : `BUILD SUCCESSFUL`, 0 erreur
  après ajout de l'annotation `@androidx.annotation.OptIn(UnstableApi::class)`.
- [ ] Valider manuellement l'entrée/sortie PiP sur l'appareil ayant reproduit le bug.

---

# 7. Notes de développement

- 2026-07-21 — Le relayout immédiat est conservé et complété par un
  `requestLayout()` différé de 300 ms sur le `PlayerView` et sa surface, après
  stabilisation de l'animation PiP. Le callback est retiré au dispose Compose.
- 2026-07-21 — `testDebugUnitTest` et `assembleDebug` passent. `lintDebug`
  remontait `UnsafeOptInUsageError` sur les accès `PlayerView.videoSurfaceView`
  (API `@UnstableApi` media3). Correction : annotation
  `@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)`
  sur `rememberPipState` (convention déjà utilisée dans le reste du projet).
  `lintDebug` repasse alors à `BUILD SUCCESSFUL` (0 erreur). La validation
  fonctionnelle reste à effectuer sur l'appareil qui reproduit la régression.

---

# 8. Review

## Validation finale — 2026-07-21

Status: VALIDATED

- [x] `./gradlew testDebugUnitTest` — `BUILD SUCCESSFUL`.
- [x] `./gradlew assembleDebug` — `BUILD SUCCESSFUL`.
- [x] `./gradlew lintDebug` — `BUILD SUCCESSFUL`, aucune erreur bloquante.
- [x] `git diff --check` — aucune erreur de whitespace.
- [x] Relecture statique : correction mutualisée pour Live, VOD et Séries via
  `rememberPipState`; relayout immédiat et différé ; callback différé supprimé
  au `onDispose`.
- [x] Validation fonctionnelle sur l'appareil reproduisant le bug : la vidéo
  remplit la fenêtre dès la première entrée en PiP, sans redimensionnement.
- [x] Validation fonctionnelle de la sortie PiP vers le plein écran.

Le statut global est passé à `RELEASED` après validation et préparation de la livraison.

## Critique

## Majeur

## Mineur

## Corrections demandées

---

# 9. Release

Version:
v1.48.30

Commit:
v1.48.30

Date:
2026-07-21
