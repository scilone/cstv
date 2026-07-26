# F14 - Rendre collant (sticky) le bouton de validation de la recherche avancée

## Informations générales

Type:
Feature

Status:
RELEASED

Created:
2026-07-26

Target version:
v1.56.0

Version:
v1.56.0

Date:
2026-07-26

---

# 1. Description

Dans l'écran de recherche, le volet de recherche avancée (`AdvancedSearchSheet.kt`) permet à l'utilisateur d'appliquer divers filtres (type de média, catégorie, note minimale, années de sortie et genres).

Aujourd'hui, l'ensemble du contenu du volet est intégré dans un conteneur vertical déroulant unique (`Column` avec `.verticalScroll`). Le bouton de validation « Voir les résultats (X) » est situé tout à la fin de cette colonne.
Ce fonctionnement pose deux problèmes ergonomiques :
1. **Défaut de visibilité initial :** Lorsque le volet s'ouvre, le bouton de validation est invisible car repoussé en bas par le grand nombre de filtres disponibles. L'utilisateur peut ne pas comprendre immédiatement comment lancer la recherche.
2. **Frottement d'usage (friction) :** Même si l'utilisateur sait que le bouton se trouve en bas, il doit systématiquement faire défiler (scroller) tout le volet vers le bas pour pouvoir valider ses choix de filtres, ce qui s'avère fastidieux à l'usage quotidien.

L'évolution consiste à rendre le bouton de validation de recherche "collant" (sticky) afin qu'il reste toujours affiché et fixe au bas de la Bottom Sheet, indépendamment du défilement des filtres situés au-dessus de lui.

---

# 2. Contexte

Le composant `AdvancedSearchSheet` est affiché sous forme de `ModalBottomSheet` de Jetpack Compose (dans `AdvancedSearchSheet.kt`).
Son layout actuel est le suivant :

```kotlin
ModalBottomSheet(...) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 20.dp)
            .focusGroup()
    ) {
        // En-tête "Recherche avancée" et bouton "Réinitialiser"
        // ...
        // Boutons "Film" et "Série"
        // ...
        // Catégorie Dropdown
        // ...
        // Note minimum
        // ...
        // Année de sortie (Slider ou Steppers sur TV)
        // ...
        // Liste des Genres (FlowRow)
        // ...
        // Bouton de validation "Voir les résultats (X)"
    }
}
```

Parce que `.verticalScroll` est appliqué sur la `Column` racine de la bottom sheet, tout le contenu (y compris le bouton) défile ensemble. Lorsque les genres ou les catégories sont développés, la hauteur totale dépasse largement la hauteur d'écran, masquant complètement le bouton d'action principal.

Pour éliminer ce problème, nous devons restructurer le layout de la Bottom Sheet en séparant la zone de défilement (les options de filtres) et la zone fixe de validation (le bouton "Voir les résultats").

---

# 3. Objectif

Rendre l'interface de recherche avancée beaucoup plus intuitive et rapide à utiliser en gardant le bouton d'action principal « Voir les résultats » continuellement ancré en bas de l'écran (mode sticky), tout en conservant le défilement indépendant pour l'ensemble des filtres du dessus.

---

# 4. Hypothèses

- **Restructuration du Layout Compose :** Pour dissocier le défilement, le composant `ModalBottomSheet` devra contenir un conteneur principal non scrollable (comme une `Column` ou un `Box` de hauteur maximale).
  - La partie supérieure contenant l'en-tête et tous les filtres sera placée dans une sous-`Column` avec `Modifier.weight(1f).verticalScroll(rememberScrollState())` pour occuper l'espace disponible et défiler de manière fluide.
  - Le bouton d'action sera placé juste en dessous de cette zone scrollable, dans une section fixe avec son propre padding et éventuellement une légère ligne de séparation (ou un dégradé d'ombrage) pour marquer la transition visuelle.
- **Gestion du Focus sur Android TV :** Sur Android TV, le comportement du focus du D-pad doit être rigoureusement préservé. Le bouton d'action doit rester facilement accessible par le focus (en scrollant vers le bas ou en naviguant directement), et la navigation D-pad ne doit pas être perturbée par la séparation des conteneurs.
- **Préservation de la hauteur de la Bottom Sheet :** Sur les petits écrans de smartphones, il faudra veiller à ce que la zone déroulante conserve une taille suffisante et ne soit pas excessivement comprimée par le bouton fixe du bas, assurant une parfaite ergonomie sur tous les form-factors.

---

# 5. Questions ouvertes

1. **Style visuel de la transition :** Faut-il ajouter une fine ligne de démarcation (un `HorizontalDivider` discret) ou un effet de dégradé transparent au-dessus du bouton sticky pour indiquer visuellement que le contenu des filtres passe en dessous lors du défilement ?
2. **Bouton Réinitialiser :** Le bouton "Réinitialiser" (actuellement situé dans l'en-tête scrollable) doit-il rester en haut ou devrait-il également être déplacé à côté ou au-dessus du bouton de validation sticky en bas pour être toujours accessible ?
3. **Hauteur maximale sur mobile :** Comment réagit le composant standard `ModalBottomSheet` de Compose si la hauteur cumulée de l'en-tête + filtres + bouton fixe dépasse l'écran sur de très vieux téléphones ? Il faudra tester pour s'assurer que le clavier virtuel ou les dimensions de l'écran n'occultent pas la zone déroulante des filtres.

---

# 6. Spécification fonctionnelle

## User stories

- En tant qu'utilisateur mobile, je veux voir immédiatement l'action « Voir les résultats » et pouvoir l'utiliser sans parcourir tous les filtres.
- En tant qu'utilisateur Android TV, je veux pouvoir atteindre et activer cette action au D-pad sans perdre le focus ni bloquer le défilement des filtres.
- En tant qu'utilisateur, je veux que le nombre de résultats affiché sur le bouton reflète mes filtres en cours avant de les appliquer.

## Comportement attendu

À l'ouverture de la recherche avancée, le bouton « Voir les résultats (X) » est visible et ancré en bas de la Bottom Sheet. Il ne défile pas avec les filtres.

- L'en-tête, le choix du type de média, la catégorie, la note minimale, la période de sortie et les genres constituent la zone défilante située au-dessus du bouton.
- Le bouton conserve en permanence le libellé actuel et le compteur `X` correspondant aux filtres sélectionnés en cours.
- Modifier un filtre met à jour le compteur sans déplacer, masquer ou recréer visiblement le bouton.
- Appuyer sur le bouton applique les filtres en cours puis ferme le volet, conformément au comportement actuel.
- Le bouton « Réinitialiser » reste dans l'en-tête de la zone défilante et conserve son comportement actuel ; il n'est pas rendu sticky.

## Parcours utilisateur

1. L'utilisateur ouvre la recherche avancée : le bouton de résultats est déjà visible au bas du volet.
2. Il consulte et modifie les filtres en faisant défiler uniquement la zone supérieure.
3. Le bouton reste accessible pendant tout le défilement et affiche le nouveau nombre de résultats.
4. Il valide depuis ce bouton ; les résultats correspondent aux filtres visibles et le volet se ferme.

## Règles métier et cas limites

- Le changement est purement ergonomique : les filtres disponibles, leurs valeurs, leur ordre et leur logique de calcul ne changent pas.
- Lorsque les catégories ou genres rendent le contenu plus haut que l'écran, seul le contenu des filtres défile ; l'action principale demeure affichée.
- Sur un petit écran ou lorsque l'espace vertical est réduit, la zone défilante utilise l'espace restant sans recouvrir le bouton.
- Sur Android TV, tous les contrôles existants restent atteignables au D-pad. Le focus peut atteindre le bouton fixe et son indicateur visuel de focus est conservé.
- Fermer le volet par le geste, le retour ou le scrim ne doit pas appliquer les filtres tant que le bouton n'a pas été activé, comme aujourd'hui.
- Aucun nouveau séparateur, dégradé ou changement visuel hors du repositionnement du bouton n'est requis par ce ticket.

## Critères d'acceptation

- [ ] À l'ouverture sur mobile, « Voir les résultats (X) » est visible sans défilement initial.
- [ ] Après avoir fait défiler les filtres jusqu'aux genres, le bouton reste visible et activable.
- [ ] Changer le type, la catégorie, la note, l'année ou un genre met à jour le compteur sans déplacer le bouton.
- [ ] Valider applique exactement les filtres en cours et ferme le volet comme avant.
- [ ] Sur Android TV, la navigation D-pad atteint le bouton, l'active et conserve un retour de focus visible.
- [ ] Les petits écrans conservent une zone de filtres défilante utilisable sans que le bouton soit recouvert.

---

# 7. Spécification technique

## 7.1 État actuel du code

`presentation/search/AdvancedSearchSheet.kt` — un seul conteneur porte à la fois le défilement, les marges et le groupe de focus (`:115-125`) :

```kotlin
ModalBottomSheet(...) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())   // ← défile TOUT, bouton compris
            .padding(horizontal = 20.dp)
            .padding(bottom = 20.dp)
            .focusGroup()
    ) {
        // en-tête (:127-147) … genres (:222-238)
        // bouton "Voir les résultats" (:240-266)  ← dernier enfant, donc hors écran
    }
}
```

Le composable est déjà **stateless** : `filter`, `availableGenres`, `availableCategories`, `resultCount`, `catalogYearRange` entrent en paramètres, toute interaction ressort par callback. Le seul état local est `categoryExpanded` (`:106`) et les `applyFocused` / `isFocused` de chaque contrôle.

Le site d'appel `SearchScreen.kt:299-314` passe déjà tous les paramètres nécessaires. **Sa signature ne change pas** — le ticket est intégralement contenu dans `AdvancedSearchSheet.kt`.

## 7.2 Contrainte de mesure — pourquoi `weight(1f)` seul ne suffit pas

`ModalBottomSheet` mesure son slot de contenu en **hauteur wrap-content**, bornée par la hauteur maximale de la feuille. Dans une `Column` dont la hauteur n'est pas déterminée, `Modifier.weight(1f)` (avec `fill = true`, la valeur par défaut) force l'enfant à occuper *tout* l'espace restant — ce qui, sur un contenu court, étirerait la feuille jusqu'à la hauteur maximale et laisserait un grand vide entre les genres et le bouton.

Le modificateur retenu est donc :

```kotlin
Modifier.weight(1f, fill = false)
```

- contenu court → la zone défilante prend sa hauteur naturelle, la feuille reste basse ;
- contenu long → la zone défilante est plafonnée à l'espace restant après le pied fixe, et défile.

C'est la seule combinaison qui satisfait simultanément le critère d'acceptation n°1 (bouton visible sans défilement) et n°6 (zone de filtres utilisable sur petit écran).

`skipPartiallyExpanded = true` est déjà positionné (`:105`) : la feuille s'ouvre directement en état étendu, il n'y a pas de demi-palier à gérer.

## 7.3 Contrainte de focus D-pad

`.focusGroup()` est aujourd'hui posé sur la `Column` défilante (`:124`). Le scinder en deux conteneurs sans y toucher placerait le bouton **hors du groupe de focus**, avec un ordre de traversée D-pad indéterminé entre la zone défilante et le pied.

`.focusGroup()` doit donc migrer sur la `Column` racine, de façon à englober zone défilante **et** pied. Aucun `focusGroup()` imbriqué n'est ajouté sur la zone défilante : l'imbrication crée des frontières de traversée supplémentaires et risquerait de piéger le focus dans les filtres. Le `focusGroup()` interne du `LazyColumn` de catégories (`:407`) est conservé tel quel — il est délibérément isolé et ne participe pas à ce découpage.

Le remontage automatique du contenu vers le focus (`bringIntoView` implicite de `verticalScroll` + `focusable`) reste actif dans la zone défilante. Le bouton étant désormais toujours visible, il n'a plus besoin d'être ramené par défilement.

## 7.4 Marges et insets

Répartition des marges après scission :

| Élément | Avant | Après |
|---|---|---|
| `padding(horizontal = 20.dp)` | zone défilante | **racine** — s'applique aux deux zones, une seule déclaration |
| `padding(bottom = 20.dp)` | zone défilante | **pied fixe** |
| Marge basse des genres (`padding(bottom = 24.dp)`, `:227`) | fin du contenu défilant | inchangée — sépare visuellement les genres du pied |

Le `ModalBottomSheet` conserve ses `BottomSheetDefaults.windowInsets` par défaut : Material3 consomme donc déjà les insets verticaux du système avant de mesurer son contenu. **Point à vérifier à l'étape 8** : aucun `Modifier.navigationBarsPadding()` local ne doit être ajouté au pied tant que `windowInsets` n'est pas explicitement surchargé. Si un recouvrement est observé sur un appareil réel, la correction doit se faire au niveau du paramètre `windowInsets` de la feuille, afin de ne pas doubler la marge basse.

## 7.5 Périmètre exclu

- Aucun changement de la logique de filtrage, de `AdvancedSearchFilter`, de `SearchViewModel` ni de `AdvancedCatalogSearchUseCase`.
- Aucun changement de la signature de `AdvancedSearchSheet` ni de son site d'appel `SearchScreen.kt:299`.
- Aucun séparateur, dégradé, ombre ni élévation ajouté (réponse à la question ouverte n°1 : **non**, la spécification fonctionnelle l'exclut explicitement).
- « Réinitialiser » reste dans l'en-tête défilant (réponse à la question ouverte n°2 : **non déplacé**, conformément à la spécification fonctionnelle).
- Aucune nouvelle dépendance, aucun changement de thème.

---

# 8. Architecture

## 8.1 Structure cible

```kotlin
ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = Surface2,
    contentColor = Color.White,
    scrimColor = Color.Black.copy(alpha = 0.55f)
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .focusGroup()                                   // englobe filtres + pied
    ) {
        // ── Zone défilante ────────────────────────────────
        Column(
            modifier = Modifier
                .weight(1f, fill = false)                   // cf. §7.2
                .verticalScroll(rememberScrollState())
        ) {
            // en-tête + Réinitialiser
            // type de média (Film / Série)
            // CategoryDropdown
            // note minimum
            // YearRangeSection
            // genres (FlowRow)
        }

        // ── Pied fixe ─────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
        ) {
            // bouton "Voir les résultats ($resultCount)" — bloc :240-266 déplacé tel quel
        }
    }
}
```

Le bloc du bouton (`:240-266`) est **déplacé sans modification interne** : mêmes couleurs, même `RoundedCornerShape(16.dp)`, même anneau de focus TV blanc 3 dp, même `applyFocused`. Seuls son conteneur parent et ses marges changent.

## 8.2 Responsabilités

| Conteneur | Responsabilité |
|---|---|
| `Column` racine | Marges horizontales, groupe de focus D-pad, répartition verticale entre zone défilante et pied |
| `Column` défilante | Défilement des filtres uniquement ; ne connaît pas le bouton |
| `Box` pied | Ancrage bas et marge basse ; ne connaît pas l'état de défilement |

Aucune communication entre la zone défilante et le pied : le `ScrollState` n'est lu par personne, ce qui évite toute recomposition du bouton pendant le défilement. Le compteur `resultCount` est un paramètre du composable — il se met à jour par recomposition normale, sans que le bouton soit déplacé ni recréé (critère d'acceptation n°3).

## 8.3 Comportement selon le contexte

| Contexte | Comportement |
|---|---|
| Mobile, contenu court (aucun genre disponible) | Feuille basse, pas de défilement, bouton immédiatement sous les filtres |
| Mobile, contenu long (genres + catégories dépliées) | Zone défilante plafonnée, bouton ancré et visible en permanence |
| Petit écran / hauteur réduite | Le pied est mesuré en premier, la zone défilante reçoit le reste : le bouton ne peut jamais être recouvert |
| Android TV | Traversée D-pad continue filtres → bouton via le `focusGroup()` racine ; anneau de focus du bouton conservé |

## 8.4 Fichiers impactés

**Modifié — un seul fichier**

| Fichier | Nature |
|---|---|
| `presentation/search/AdvancedSearchSheet.kt` | Scission du conteneur racine : `Column` racine non défilante + `Column` défilante `weight(1f, fill = false)` + `Box` pied ; déplacement de `.focusGroup()` et des marges |

Composables privés `SectionLabel`, `FocusableLink`, `MediaTypeChip`, `CategoryDropdown`, `RatingChip`, `GenreChip`, `YearRangeSection`, `YearStepperRow`, `StepperButton`, ainsi que le helper `Modifier.tvFocusRing` : **inchangés**.

**Non modifié**

- `presentation/search/SearchScreen.kt` — site d'appel inchangé.
- `SearchViewModel`, `AdvancedSearchFilter`, `AdvancedCatalogSearchUseCase`.

**Aucune nouvelle dépendance. Aucune migration Room. Aucune règle ProGuard.**

## 8.5 Stratégie de validation

AGENTS.md classe explicitement en « non prioritaire » les tests de « code layout pur sans logique ». Ce ticket ne modifie aucune logique métier et n'introduit aucun état nouveau : **aucun test unitaire n'est ajouté**.

La validation repose sur :

1. `./gradlew testDebugUnitTest` — non-régression de la suite existante (aucun test ne cible ce composable, la suite doit rester intégralement verte).
2. `./gradlew assembleDebug` + `./gradlew lintDebug`.
3. Vérification visuelle contre `docs/design-reference/screenshots/advanced-search-{filters-open-empty,filters-open-some-selected,category-open}.png` : le repositionnement du bouton est le **seul** écart attendu par rapport aux maquettes.
4. Passage manuel des six critères d'acceptation, dont le §7.4 (inset de barre gestuelle) et la traversée D-pad sur TV.

## 8.6 Risques et contraintes

| Risque | Portée | Traitement |
|---|---|---|
| `weight(1f)` avec `fill = true` étire la feuille sur contenu court | Fort — régression visuelle sur tous les mobiles | `fill = false` obligatoire (§7.2). Point de contrôle explicite de la review |
| Bouton fixe sous la barre de navigation gestuelle | Moyen — dépend de l'appareil | Vérification à l'étape 8 ; les insets verticaux sont déjà gérés par `BottomSheetDefaults.windowInsets`. En cas d'overlap réel, corriger le paramètre `windowInsets` de la feuille, jamais par un padding local (§7.4). |
| Focus D-pad piégé dans la zone défilante | Fort sur TV — le bouton deviendrait inatteignable | `.focusGroup()` déplacé sur la racine, aucun groupe imbriqué ajouté (§7.3) |
| `LazyColumn` des catégories (`heightIn(max = 260.dp)`, `:402`) imbriqué dans un parent défilant | Existant, non introduit par ce ticket | Hauteur déjà bornée, comportement inchangé par la scission — à ne pas régresser |
| Zone de filtres trop comprimée sur très petit écran | Faible | Le pied ne fait qu'une ligne (~56 dp + 20 dp) ; le reste revient aux filtres |

---

# 9. Plan de développement

- [x] **Tâche 1 — Scinder `ModalBottomSheet` en zone défilante + pied fixe**

  Objectif :
  Restructurer le conteneur unique en `Column` racine non défilante + `Column` défilante `weight(1f, fill = false)` + `Box` pied, selon §8.1.

  Fichiers :
  - `presentation/search/AdvancedSearchSheet.kt`

  Détail :
  - `Column` racine : `fillMaxWidth()`, `padding(horizontal = 20.dp)`, `.focusGroup()` (migré depuis la `Column` défilante, §7.3).
  - `Column` défilante : `weight(1f, fill = false)` + `verticalScroll(rememberScrollState())` ; contient en-tête, type de média, `CategoryDropdown`, note minimum, `YearRangeSection`, genres — inchangés en interne.
  - `Box` pied : `fillMaxWidth()`, `padding(bottom = 20.dp)` ; contient le bloc bouton `:240-266` déplacé **sans modification interne** (mêmes couleurs, mêmes 16.dp, même anneau de focus TV, même `applyFocused`).
  - Ne pas utiliser `weight(1f)` par défaut (`fill = true`) — piège identifié en §7.2/§8.6, à ne pas réintroduire.
  - Composables privés (`SectionLabel`, `FocusableLink`, `MediaTypeChip`, `CategoryDropdown`, `RatingChip`, `GenreChip`, `YearRangeSection`, `YearStepperRow`, `StepperButton`, `Modifier.tvFocusRing`) : non touchés.

  Validation :
  Implémenté dans `presentation/search/AdvancedSearchSheet.kt`. `./gradlew testDebugUnitTest`,
  `./gradlew assembleDebug` et `./gradlew lintDebug` ont réussi : 405 tests, 0 échec, 0 erreur
  (61 suites). Les vérifications visuelles et D-pad restent les tâches manuelles ci-dessous.

- [x] **Tâche 2 — Vérification visuelle et fonctionnelle mobile**

  Objectif :
  Confirmer les critères d'acceptation n°1 à 4 et l'absence d'écart avec les maquettes hors repositionnement du bouton.

  Détail :
  - Comparer contre `docs/design-reference/screenshots/advanced-search-{filters-open-empty,filters-open-some-selected,category-open}.png` — seul écart attendu : la position du bouton.
  - À l'ouverture, contenu court (aucun genre) : bouton visible sans défilement initial, feuille pas anormalement étirée (vérifie `fill = false`, §7.2).
  - Défilement jusqu'aux genres : bouton toujours visible et activable.
  - Changer type/catégorie/note/année/genre : compteur `resultCount` se met à jour, bouton ne bouge pas, n'est pas recréé.
  - Valider : filtres en cours appliqués, volet fermé, comportement identique à avant.
  - Vérifier l'inset de barre de navigation gestuelle (§7.4). Les insets verticaux par défaut de la Bottom Sheet protègent déjà le pied ; si un recouvrement est observé, corriger `windowInsets` du `ModalBottomSheet`, sans ajouter de `navigationBarsPadding()` local.

  Validation :
  Les 4 premiers critères d'acceptation de la section 6 cochés ; capture d'écran avant/après en cas d'ajustement d'inset.

- [x] **Tâche 3 — Vérification focus D-pad Android TV**

  Objectif :
  Confirmer que le `.focusGroup()` déplacé sur la racine (§7.3) préserve une traversée continue filtres → bouton, sans piège de focus dans la zone défilante.

  Détail :
  - Naviguer au D-pad depuis l'en-tête jusqu'au bouton en traversant tous les filtres (y compris catégorie dépliée et genres) : la traversée doit rester continue.
  - Confirmer que le focus atteint bien le bouton (pas seulement défiler jusqu'à lui) et que son anneau de focus blanc 3dp s'affiche.
  - Confirmer que le `focusGroup()` interne du `LazyColumn` de catégories (`:407`) continue de fonctionner isolément, sans régression de la traversée de la liste déroulante des catégories.

  Validation :
  Critères d'acceptation n°5 de la section 6 coché.

- [x] **Tâche 4 — Vérification petit écran**

  Objectif :
  Confirmer que la zone défilante reste utilisable et non recouverte sur petite hauteur (critère n°6).

  Détail :
  Tester sur le plus petit form-factor disponible (émulateur mobile bas de gamme ou fenêtre réduite) avec genres et catégorie dépliés simultanément : la zone défilante doit occuper l'espace restant sans que le pied ne la recouvre.

  Validation :
  Critère d'acceptation n°6 coché ; build final : `./gradlew assembleDebug` + `./gradlew lintDebug` sans erreur.

---

# 10. Review

Status: RESOLVED

Périmètre relu : le diff de travail de `presentation/search/AdvancedSearchSheet.kt` (seul fichier
de production touché par F14), plus son site d'appel `SearchScreen.kt:299` et les composables
privés du fichier. Aucun code modifié pendant cette étape.

## 10.1 Vérifications automatiques

| Contrôle | Résultat |
|---|---|
| `./gradlew assembleDebug` | vert |
| `./gradlew lintDebug` | vert, aucune erreur |
| `./gradlew testDebugUnitTest` | vert — **405 tests, 0 échec, 0 erreur** (61 suites) |
| Absence de nouveau test | conforme à §8.5 (layout pur sans logique, AGENTS.md « non prioritaire ») |

La non-régression annoncée en tâche 1 est donc confirmée : la suite existante reste intégralement
verte, aucun test ne ciblait ni ne cible ce composable.

## 10.2 Conformité aux spécifications

Structure §8.1 respectée :

- `Column` racine (`:115-120`) : `fillMaxWidth()` + `padding(horizontal = 20.dp)` + `.focusGroup()`,
  sans `verticalScroll` — conforme.
- Zone défilante (`:124-128`) : `weight(1f, fill = false)` + `verticalScroll(rememberScrollState())`.
  Le piège de §7.2/§8.6 (`fill = true` étirant la feuille sur contenu court) est bien évité.
- `Box` pied (`:246-251`) : `fillMaxWidth()` + `padding(bottom = 20.dp)`, en dehors de la zone défilante.
- §7.3 : un seul `focusGroup()` dans la fonction principale, sur la racine (`:119`) ; aucun groupe
  imbriqué ajouté sur la zone défilante. Le `focusGroup()` interne du `LazyColumn` des catégories
  (`:418`) et son `heightIn(max = 260.dp)` (`:413`) sont intacts.
- §7.5 / §8.4 : signature de `AdvancedSearchSheet` inchangée, `SearchScreen.kt` non modifié, aucun
  séparateur ni dégradé ajouté, « Réinitialiser » toujours dans l'en-tête défilant, composables
  privés et `Modifier.tvFocusRing` non touchés, aucune nouvelle dépendance.

Les critères d'acceptation n°1 à 6 relèvent des tâches manuelles 2 à 4 et ne sont pas vérifiables
à cette étape ; rien dans le code relu ne les contredit.

## Critique

Aucun problème critique. Pas de crash possible, pas de régression de compilation, pas de changement
de contrat public, pas d'impact données ni sécurité.

## Majeur

### MAJ-1 — Indentation de la zone défilante non reprise après l'imbrication

**Description.** Le contenu déplacé sous la nouvelle `Column` défilante n'a pas été réindenté
(`AdvancedSearchSheet.kt:129-241`). Les appels ont bien gagné 4 espaces sur leur première ligne,
mais pas leurs arguments ni leurs accolades fermantes : `Row` en-tête (`:130-150`), `Row` type de
média (`:154-182`), `CategoryDropdown` (`:185-197`), `Row` note minimum (`:203-212`),
`YearRangeSection` (`:215-220`) et le bloc genres (`:225-241`) ont tous leur corps et leur `}`
alignés sur l'ancien niveau. Cas le plus lisible : l'accolade fermante du `if (availableGenres.isNotEmpty())`
(`:241`) est indentée comme celle du `FlowRow` qui la précède (`:240`).

**Impact.** L'indentation ne reflète plus la structure réelle du composable, dans le fichier même
que ce ticket restructure. Deux conséquences concrètes : le diff de 63 lignes est presque
entièrement du bruit d'indentation, ce qui noie les trois modifications réelles pour toute revue
ultérieure ; et un ajout futur dans la zone défilante a de bonnes chances d'être placé au mauvais
niveau de bloc. Le projet n'a ni ktlint ni spotless pour rattraper cela automatiquement.

**Correction attendue.** Réindenter `:129-241` au niveau de la `Column` parente, sans aucun autre
changement (aucun ajout, aucune suppression, aucun réordonnancement d'appel).

### MAJ-2 — La correction d'inset prévue en §7.4 doublerait la marge basse

**Description.** §7.4 et §8.6 prévoient d'ajouter `Modifier.navigationBarsPadding()` sur le `Box`
pied si le bouton apparaît recouvert par la barre de navigation gestuelle. Or le site d'appel
`ModalBottomSheet` (`:108-114`) ne surcharge pas le paramètre `windowInsets` : avec
`compose-bom:2024.02.02` (Material3 1.2.0), la valeur par défaut `BottomSheetDefaults.windowInsets`
est `WindowInsets.systemBars.only(WindowInsetsSides.Vertical)`, appliquée par la feuille
**au-dessus** du slot de contenu. L'inset de barre de navigation est donc déjà consommé avant que
notre `Column` racine ne soit mesurée.

**Impact.** Si la tâche 2 applique la remédiation telle qu'écrite, le pied reçoit deux fois la
hauteur de la barre système : bande vide sous le bouton sur les appareils à navigation gestuelle,
soit exactement le défaut visuel que §7.4 cherchait à éviter dans l'autre sens. Le risque est
d'autant plus élevé que la fiche présente ce padding comme la correction de référence.

**Correction attendue.** Amender §7.4 et la ligne correspondante de §8.6 : indiquer que l'inset est
déjà pris en charge par `BottomSheetDefaults.windowInsets` et que `navigationBarsPadding()` ne doit
**pas** être ajouté tant que `windowInsets` n'est pas explicitement surchargé au site d'appel. Si un
recouvrement est malgré tout constaté sur un appareil réel en tâche 2, le traiter au niveau du
paramètre `windowInsets` du `ModalBottomSheet`, pas par un padding local.

## Mineur

### MIN-1 — `contentAlignment` mort sur le `Box` pied

`Box(:246-251)` déclare `contentAlignment = Alignment.Center` alors que son unique enfant est
`fillMaxWidth()` : le paramètre n'a aucun effet. **Impact :** bruit, laisse croire à un alignement
significatif. **Correction attendue :** supprimer `contentAlignment` du `Box` externe et conserver
celui du `Box` bouton interne (`:253`).

### MIN-2 — Le bloc bouton a été modifié alors que §8.1 et la tâche 1 exigeaient un déplacement à l'identique

L'ordre des paramètres du `Box` bouton a été inversé (`contentAlignment` avant `modifier`) et sa
chaîne de modifiers est désormais incohérente : `.onFocusChanged` … `.clickable` (`:256-266`) sont
restés à l'ancien niveau d'indentation tandis que `.padding(vertical = 16.dp)` (`:267`) a suivi le
nouveau. **Impact :** aucun impact fonctionnel — couleurs, `RoundedCornerShape(16.dp)`, anneau de
focus TV 3 dp et `applyFocused` sont bien préservés — mais l'écart au plan est réel.
**Correction attendue :** rétablir la forme d'origine du bloc, traité conjointement avec MAJ-1.

### MIN-3 — Le viewport de défilement est désormais rétréci de 40 dp

Avant, la chaîne était `.verticalScroll().padding(horizontal = 20.dp)` : le nœud de défilement
occupait toute la largeur et les marges s'appliquaient à l'intérieur. Après (§7.4, marge remontée
sur la racine), la `Column` défilante est mesurée 40 dp plus étroite. **Impact :** l'effet
d'overscroll et le clipping haut/bas de la zone défilante s'appliquent à l'intérieur des marges au
lieu de bord à bord — écart visuel discret mais réel par rapport aux maquettes. **Correction
attendue :** point de contrôle explicite de la tâche 2 lors de la comparaison aux captures de
`docs/design-reference/screenshots/` ; si l'écart est visible, replacer
`padding(horizontal = 20.dp)` à l'intérieur de la zone défilante et le dupliquer sur le pied.

### MIN-4 — Traçabilité de la tâche 1 incomplète

La tâche 1 est cochée `[x]` et sa section « Validation » annonce que la non-régression automatisée
est exécutée à cette étape, mais aucun résultat n'y est consigné. La fiche ne comporte par ailleurs
aucune section « Notes de développement », prévue par `AI_DEVELOPMENT_WORKFLOW.md`.
**Impact :** impossible de savoir a posteriori sur quel build la tâche a été validée.
**Correction attendue :** consigner les résultats dans la validation de la tâche 1 et ajouter la
section « Notes de développement ».

### MIN-5 — L'arbre de travail mélange F14 et des correctifs B14 non commités

Outre `AdvancedSearchSheet.kt` et cette fiche, l'arbre porte des modifications B14 non commitées
(`TmdbCatalogMatcher.kt`, `GetTrendingInCatalogUseCase.kt`, `GetPopularTop10InCatalogUseCase.kt`,
trois suites de tests, `docs/architecture.md`, `docs/changelog.md`). **Impact :** risque de commit
F14 pollué à l'étape 10. **Correction attendue :** stager sélectivement — le commit F14 ne doit
contenir que `AdvancedSearchSheet.kt` et `ai/features/F14-search-filters-sticky-button.md`.

## Points de vigilance (non bloquants, aucune correction demandée)

- **Mesure du `weight`.** `Modifier.weight` retombe sur la contrainte **minimale** de l'axe
  principal si le parent est mesuré en hauteur infinie, ce qui écraserait la zone défilante à 0.
  Ce n'est pas le cas ici : `ModalBottomSheet` mesure son slot de contenu sous une hauteur bornée
  par l'écran. À ne pas régresser si un conteneur défilant venait à être introduit au-dessus.
- **`LazyColumn` des catégories imbriqué dans un parent défilant** (`:413-418`) : dette
  préexistante, non introduite par F14, hauteur déjà bornée. À surveiller en tâche 3 (traversée
  D-pad de la liste dépliée).
- **Recomposition sur focus** : `applyFocused` (`:245`) est lu dans le scope de la `Column` racine,
  donc un changement de focus du bouton recompose l'ensemble du contenu de la feuille. Comportement
  strictement identique à l'existant (même scope avant la scission), hors périmètre de ce ticket.

## 10.3 Corrections appliquées

- **MAJ-1, MIN-2 :** la zone défilante et le bouton ont été réindentés ; le bloc du bouton conserve
  sa structure et ses modifiers d'origine, seuls son conteneur fixe et sa marge basse ayant changé.
- **MAJ-2 :** §7.4, §8.6 et le plan de validation indiquent désormais que les insets verticaux par
  défaut de `ModalBottomSheet` protègent déjà le pied. Aucun padding local n'est ajouté.
- **MIN-1 :** le `contentAlignment` sans effet du pied a été supprimé.
- **MIN-3 :** la structure prévue, avec les marges horizontales sur la racine, est conservée. La
  référence visuelle ne permet pas de mesurer le viewport de la version modifiée ; aucun changement
  préventif n'est donc appliqué. Le contrôle de l'overscroll et du clipping reste explicite en
  validation sur appareil.
- **MIN-4 :** le résultat précis des contrôles automatisés est consigné dans la tâche 1 et cette
  section de notes trace les décisions de développement.
- **MIN-5 :** aucun fichier B14 n'a été modifié ni ne sera inclus dans le futur commit F14 ; le
  staging restera strictement limité à la fiche F14 et à `AdvancedSearchSheet.kt`.

---

# 11. Notes de développement

- La scission garde `weight(1f, fill = false)` afin de ne pas étirer une feuille dont le contenu
  est court, tout en plafonnant la zone des filtres quand elle est longue.
- Le `focusGroup()` est porté par la colonne racine pour englober les filtres et le bouton fixe ;
  le groupe interne de la liste de catégories reste inchangé.
- Étape 7 : tous les retours de revue ont été traités. Étape 8 : validation automatisée en cours ;
  validation mobile, petit écran et D-pad Android TV à réaliser sur cibles connectées.

---

# 12. Validation finale

Status: VALIDATED

| Contrôle | Résultat |
|---|---|
| `./gradlew testDebugUnitTest assembleDebug lintDebug` | réussi après les corrections |
| `./gradlew --no-daemon testDebugUnitTest lintDebug` | réussi après les corrections |
| Lint debug | réussi, `BUILD SUCCESSFUL` (27 tâches : 2 exécutées, 25 à jour) |
| Validation mobile tactile et inset gestuel | validé sur appareil |
| Validation petit écran | validé sur petite hauteur |
| Validation D-pad Android TV | validé sur émulateur Android TV |

Les tâches 2 à 4 du plan sont validées.

---

# 13. Release

Version : v1.56.0

Commit : v1.56.0

Date : 2026-07-26
