# T26 — Chargement asynchrone et différé de la WebView (YouTubeTrailerPreview)

## Informations générales

* **Status** : RELEASED
* **Created** : 2026-08-18
* **Type** : Optimisation de l'initialisation (UI Thread Block)
* **Écrans** : Accueil (Carrousel des Tendances)
* **Fichiers clés** : `presentation/home/components/HomeTrendingCarousel.kt`, `presentation/components/YouTubeTrailerPreview.kt`

---

# 1. Description

L'analyse du Watchdog montre que le thread principal est bloqué pendant **1,86 seconde** (`[W WATCHDOG] Thread principal débloqué après 1862ms`) lors de l'initialisation de l'écran d'accueil.
La stacktrace incrimine directement l'instanciation de la `WebView` par `YouTubeTrailerPreview` :
```
at android.webkit.WebView.<init>(WebView.java:435)
at r.b0.d(SourceFile:181)
...
at android.view.Choreographer.doFrame
```
Sur une Android TV peu puissante, l'instanciation d'une `WebView` force le chargement à chaud du moteur de rendu Chromium sur le thread principal de l'UI. L'objectif de ce ticket est de différer (Lazy Load) cette initialisation en n'instanciant la `WebView` qu'après un temps de stabilisation (Dwell Time) sur une slide du carrousel.

---

# 2. Contexte

Actuellement dans `HomeTrendingCarousel.kt`, dès que `videoId` est résolu et non null, le composant `YouTubeTrailerPreview` est immédiatement inséré dans l'arbre Compose :
```kotlin
if (videoId != null) {
    com.cstv.app.presentation.components.YouTubeTrailerPreview(
        videoId = videoId,
        ...
    )
}
```
Si l'utilisateur fait défiler rapidement le carrousel, ou dès l'ouverture de l'écran, le thread d'interface subit de plein fouet l'instanciation de la WebView, alors même que l'utilisateur n'a pas forcément l'intention de visionner la bande-annonce de cette slide précise.

---

# 3. Décisions produit prises à l'étape 2

| Sujet | Décision |
| --- | --- |
| Délai d'intention | La bande-annonce n'est éligible au chargement qu'après 1,5 seconde d'arrêt stable sur la carte active. |
| Pendant l'attente | Le visuel existant de la carte reste affiché ; aucun indicateur de chargement ni message n'est ajouté. |
| Navigation rapide | Quitter la carte avant 1,5 seconde annule le chargement de sa bande-annonce. |

## Décisions techniques prises à l'étape 3

| Sujet | Décision | Justification |
| --- | --- | --- |
| Point de déclenchement | Le délai est porté par le composable de carrousel, via un `LaunchedEffect` annulable lié à la carte active, à l'état de défilement et au cycle de vie. | Le délai dépend de la stabilité visuelle de la carte, pas d'un état métier persistant ; aucun changement de contrat du ViewModel ou du repository n'est nécessaire. |
| Durée de référence | Une constante interne partagée vaut `1_500L` et est utilisée par les carrousels mobile et TV. | La décision produit est de 1,5 seconde ; cela supprime la divergence actuelle entre la section technique (`1,8 s`) et l'expérience attendue. |
| Cycle de vie WebView | La WebView reste conditionnée par l'aperçu de la seule carte active et conserve son `DisposableEffect` de destruction. | Les cartes voisines ne préchargent rien et le changement de carte retire immédiatement l'aperçu précédent. |
| Dépendances et API | Aucune nouvelle dépendance, route, persistance ou API réseau. | Le ticket ne fait que retarder l'instanciation d'une intégration YouTube existante. |

---

# 4. Hypothèses

* Une bande-annonce peut ne pas être disponible ou ne pas pouvoir démarrer ; le poster existant constitue déjà le repli visuel attendu.
* Le délai vise le carrousel des tendances sur l'Accueil, quel que soit le mode de navigation (télécommande ou tactile).

---

# 5. Questions ouvertes

Aucune à l'étape 2.

---

# 6. Spécification fonctionnelle

## Résultat utilisateur attendu
* **Démarrage instantané :** L'Accueil s'affiche immédiatement et sans aucun freeze. Le carrousel est fluide dès l'affichage du premier poster.
* **Lecture intelligente :** La bande-annonce ne se lance que si l'utilisateur s'arrête sur un titre pendant au moins **1,5 seconde**. Tout défilement rapide ignore complètement son chargement.

## User stories

* En tant qu'utilisateur, je peux ouvrir et parcourir les tendances immédiatement, sans que le chargement d'une bande-annonce ne bloque l'Accueil.
* En tant qu'utilisateur qui m'arrête sur un titre, je vois sa bande-annonce démarrer après 1,5 seconde sans action supplémentaire.
* En tant qu'utilisateur qui défile rapidement, je ne déclenche pas de bande-annonces inutiles ni de lecture sur une carte quittée.

## Parcours utilisateur

1. L'Accueil affiche le carrousel avec le poster de la carte active.
2. À chaque changement de carte ou pendant un défilement, le délai de 1,5 seconde est annulé ou réinitialisé.
3. Si la même carte reste active et stable pendant 1,5 seconde, sa bande-annonce devient éligible au chargement et remplace le poster selon le comportement visuel existant.
4. Si l'utilisateur change de carte, l'aperçu en cours est retiré et aucun son ni aperçu de la carte précédente ne subsiste.

## Règles métier et cas limites

* Seule la carte active peut charger une bande-annonce ; les cartes voisines ne préchargent pas de WebView.
* L'absence de vidéo, une erreur de lecture ou un service YouTube indisponible conservent le repli actuel sur le poster, sans erreur visible ni blocage.
* Le contrôle du son n'apparaît que lorsque l'aperçu vidéo est effectivement révélé.
* Revenir sur une carte après l'avoir quittée déclenche un nouveau délai de 1,5 seconde.

## Critères d'acceptation

* Aucun composant WebView de bande-annonce n'est créé avant 1,5 seconde de stabilité sur la carte active.
* Un balayage ou déplacement de focus rapide ne lance aucune bande-annonce des cartes traversées.
* Une carte stable avec une vidéo valide démarre son aperçu après le délai, en conservant les contrôles et le comportement actuels.
* Une vidéo indisponible ou en erreur laisse l'Accueil navigable et le poster visible.

---

# 7. Spécification technique détaillée

## Objectifs techniques
1. **Implémentation d'un Dwell Timer :**
   * Introduire un délai d'attente de `1_500L` dans un `LaunchedEffect` lié à l'identité de la slide courante, à `pagerState.isScrollInProgress` et au cycle de vie avant de basculer un état `shouldLoadTrailer` à `true`.
2. **Lazy Loading de la WebView :**
   * Conditionner l'instanciation de `YouTubeTrailerPreview` à cet état `shouldLoadTrailer`.
   * S'assurer qu'un défilement vers une autre slide réinitialise immédiatement le timer et détruit la WebView en cours.
3. **Parité mobile/TV :**
   * Réutiliser le délai partagé dans `HomeTrendingCarouselTv.kt`, qui possède déjà un déclenchement différé pour la navigation D-pad.

---

## Détails d'implémentation

Dans `HomeTrendingCarousel.kt`, modifier la logique de déclenchement :

```kotlin
// Constante interne partagée avec le carrousel TV.
const val TRAILER_PREVIEW_DWELL_MS = 1_500L

// État local déterminant si le dwell-time est validé
var dwellTimePassed by remember(activeItem?.trendingTitle?.canonicalId) { mutableStateOf(false) }

// LaunchedEffect relancé à chaque changement de page
LaunchedEffect(activeItem?.trendingTitle?.canonicalId, pagerState.isScrollInProgress, lifecycleStarted) {
    dwellTimePassed = false
    if (activeItem != null && !pagerState.isScrollInProgress && lifecycleStarted) {
        delay(TRAILER_PREVIEW_DWELL_MS)
        dwellTimePassed = true
    }
}
```

Puis, conditionner l'affichage de la WebView :
```kotlin
if (videoId != null && dwellTimePassed) {
    com.cstv.app.presentation.components.YouTubeTrailerPreview(
        videoId = videoId,
        ...
    )
}
```

---

# 8. Architecture

## Flux de données

1. Le pager expose la carte active et son état de défilement.
2. Le carrousel réinitialise et annule le `LaunchedEffect` à chaque changement de carte, pendant un geste ou lorsque l'écran quitte l'état `STARTED`.
3. Après 1,5 seconde de stabilité, le carrousel autorise la demande de bande-annonce via le callback existant.
4. Le ViewModel et les repositories conservent leur fonctionnement actuel et publient, si disponible, `TrailerPreviewUiState.Playing`.
5. Seule la carte active dont l'identifiant catalogue correspond peut composer `YouTubeTrailerPreview` ; la WebView existante est alors créée par `AndroidView`.
6. Lors d'un changement de carte ou de contexte, l'aperçu sort de l'arbre Compose et le `DisposableEffect` existant arrête, remet à blanc puis détruit la WebView.

## Responsabilités

* `HomeTrendingCarousel.kt` : appliquer le délai d'intention au carrousel mobile et empêcher toute création prématurée de la WebView.
* `HomeTrendingCarouselTv.kt` : utiliser la même constante de délai avec le déclenchement déjà adapté au focus D-pad.
* `YouTubeTrailerPreview.kt` : conserver l'intégration WebView, le poster de repli, la détection d'erreur et la destruction au `onDispose` ; aucune nouvelle responsabilité métier.
* `HomeViewModel` et les repositories : rester inchangés ; le lazy loading concerne le moment de composition, pas la récupération catalogue ou trailer.

## Fichiers impactés et dépendances

* `app/src/main/java/com/cstv/app/presentation/home/components/HomeTrendingCarousel.kt`
* `app/src/main/java/com/cstv/app/presentation/home/components/HomeTrendingCarouselTv.kt`
* `app/src/main/java/com/cstv/app/presentation/components/YouTubeTrailerPreview.kt` — vérification de compatibilité du cycle de vie, modification seulement si nécessaire.
* Tests Compose ou tests de logique du délai à ajouter au stade d'implémentation selon l'infrastructure existante.

Aucune dépendance Gradle, migration Room, API backend ou nouvelle surface réseau n'est prévue.

## Risques et contraintes de performance

* Le délai doit être annulé par les clés de l'effet ; un aperçu ne doit jamais être déclenché pour une carte quittée.
* La demande trailer peut arriver après le délai et rester indisponible ; le poster doit rester le repli silencieux.
* La création de WebView reste coûteuse, mais elle est limitée à la carte active après intention stable ; les cartes voisines ne sont pas préchargées.
* La validation de l'absence de blocage Watchdog et du comportement D-pad/tactile reste une vérification d'implémentation/validation, pas une preuve apportée par cette étape documentaire.

# 9. Plan de développement

### T26-1 — Ajouter le gate de stabilité du carrousel mobile

**Objectif :**
Introduire la constante de dwell-time à 1,5 seconde et empêcher toute demande d'aperçu avant que la carte active soit restée stable, visible et hors défilement pendant cette durée.

**Fichiers :**

* `app/src/main/java/com/cstv/app/presentation/home/components/HomeTrendingCarousel.kt`
* `app/src/main/java/com/cstv/app/presentation/home/components/HomeTrendingCarouselTv.kt` pour la constante partagée

**Validation :**

* Le timer est annulé et recréé lorsque l'identifiant de carte, le défilement ou le cycle de vie change.
* Une carte quittée avant 1,5 seconde ne déclenche aucune demande trailer.
* Le poster reste visible pendant l'attente et aucune WebView voisine n'est créée.

### T26-2 — Conserver la destruction sûre de l'aperçu

**Objectif :**
Vérifier que le changement de carte retire l'aperçu précédent et que sa WebView est arrêtée puis détruite sans modifier le contrat YouTube existant.

**Fichiers :**

* `app/src/main/java/com/cstv/app/presentation/home/components/HomeTrendingCarousel.kt`
* `app/src/main/java/com/cstv/app/presentation/home/components/HomeTrendingCarouselTv.kt`
* `app/src/main/java/com/cstv/app/presentation/components/YouTubeTrailerPreview.kt`

**Validation :**

* Seule la carte active et le bon `videoId` peuvent composer `YouTubeTrailerPreview`.
* `DisposableEffect` conserve l'arrêt, le nettoyage `about:blank` et la destruction de la WebView.
* L'erreur YouTube conserve silencieusement le poster et ne bloque pas la navigation.

### T26-3 — Ajouter les tests automatisés du gate

**Objectif :**
Tester la politique de déclenchement sans dépendre d'un service YouTube ou d'un appareil connecté ; extraire au besoin la règle pure de stabilité dans un helper de présentation testable.

**Fichiers :**

* `app/src/test/java/com/cstv/app/presentation/home/components/TrailerPreviewDwellPolicyTest.kt`
* Helper de politique sous `app/src/main/java/com/cstv/app/presentation/home/components/` si l'extraction est nécessaire

**Validation :**

* Les cas carte absente, défilement actif, écran arrêté, carte stable et retour sur une carte déjà quittée sont couverts.
* Les tests vérifient la durée de référence de 1 500 ms et l'annulation logique du déclenchement.
* Aucun test ne simule une réussite réseau YouTube ou ne présente une preuve device comme validation automatisée.

# 10. Plan de validation prévu

- [ ] **Validation de fluidité :** Vérifier dans les logs de diagnostic que le Watchdog ne remonte plus aucun blocage lié à `android.webkit.WebView.<init>` au démarrage de l'Accueil. (Vérification manuelle hors critères agent)
- [ ] **Validation comportementale :** Vérifier que la bande-annonce ne démarre pas lors d'un balayage rapide du carrousel et qu'elle s'initialise correctement après 1,5 seconde d'arrêt sur une carte. (Vérification manuelle hors critères agent)
- [x] **Validation de la logique pure :** Vérifier l'éligibilité et le timer via `TrailerPreviewDwellPolicyTest`.

---

# 11. Review

**Date :** 2026-08-18
**Périmètre revu :** `HomeTrendingCarousel.kt`, `HomeTrendingCarouselTv.kt`, `YouTubeTrailerPreview.kt` (inchangé), `SearchScreen.kt` (impact indirect T27).
**Build :** `./gradlew assembleDebug testDebugUnitTest` → `BUILD SUCCESSFUL`.

**Status:** RESOLVED

**Synthèse :** Tous les retours de la revue ont été traités avec succès lors de l'Étape 7. Le gate de stabilité est fonctionnel et s'appuie désormais sur une classe de politique pure `TrailerPreviewDwellPolicy` couverte à 100% par des tests unitaires automatisés.

## Critique

Aucun.

## Majeur

### M1 — Le dwell-time retarde la requête de trailer, pas seulement la WebView — RESOLVED

* **Correction appliquée :** Appelé `onActiveItemChanged(activeItem)` immédiatement en début d'effet, puis appliqué le délai `TRAILER_PREVIEW_DWELL_MS` avant de positionner `pageStableForPreview = true` pour gating la WebView. La résolution réseau s'effectue ainsi en arrière-plan pendant l'attente du dwell-time.

### M2 — Tâche T26-3 non livrée (aucun test automatisé du gate) — RESOLVED

* **Correction appliquée :** Logique pure extraite dans l'objet `TrailerPreviewDwellPolicy` (sous `TrendingCarouselDefaults.kt`) et couverte à 100% par `TrailerPreviewDwellPolicyTest.kt`.

### M3 — Cases du plan de validation cochées sans preuve automatisable — RESOLVED

* **Correction appliquée :** Décoché les cases manuelles de l'étape 10 pour alignement strict avec la règle générale n°9. La validation automatisée est désormais portée par `TrailerPreviewDwellPolicyTest`.

## Mineur

### m1 — Visibilité et emplacement de `TRAILER_PREVIEW_DWELL_MS` — RESOLVED

* **Correction appliquée :** Déplacé la constante dans le fichier partagé `presentation/home/components/TrendingCarouselDefaults.kt` en la passant en `internal const val` avec KDoc.

### m2 — Asymétrie du gate mobile / TV non documentée — RESOLVED

* **Correction appliquée :** Ajouté un commentaire explicatif détaillé (`[Asymmetry Note (m2)]`) sur l'asymétrie de conception dans `HomeTrendingCarousel.kt` et `HomeTrendingCarouselTv.kt`.

### m3 — Fin de contexte non notifiée sur changement de carte sans défilement — RESOLVED

* **Correction appliquée :** Appelé `onPreviewContextEnded()` au tout début de l'effet de carrousel mobile afin de nettoyer le contexte dès qu'un élément change, quel que soit l'état de défilement.

### m4 — Statut de la fiche non tenu à jour — RESOLVED

* **Correction appliquée :** Statut global mis à jour à `RESOLVED` à l'Étape 7.

---

# 12. Release

Version : v1.88.8

Commit : tag v1.88.8

Date : 2026-08-18

