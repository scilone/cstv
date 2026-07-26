# F14 - Rendre collant (sticky) le bouton de validation de la recherche avancée

## Informations générales

Type:
Feature

Status:
TASK BREAKDOWN

Created:
2026-07-26

Target version:
v1.56.0

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

Le bouton n'ajoutait aucune gestion d'inset de barre de navigation gestuelle jusqu'ici ; en tant que dernier élément défilant, la marge de 20 dp suffisait. Devenu fixe et ancré, il peut se retrouver sous la barre gestuelle selon l'appareil. **Point à vérifier à l'étape 8**, et à corriger le cas échéant par `Modifier.navigationBarsPadding()` sur le pied — pas appliqué à l'aveugle, car un padding en trop créerait une bande vide visible sur les appareils à navigation par boutons.

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
| Bouton fixe sous la barre de navigation gestuelle | Moyen — dépend de l'appareil | Vérification à l'étape 8, `navigationBarsPadding()` ajouté **seulement** si l'overlap est constaté (§7.4) |
| Focus D-pad piégé dans la zone défilante | Fort sur TV — le bouton deviendrait inatteignable | `.focusGroup()` déplacé sur la racine, aucun groupe imbriqué ajouté (§7.3) |
| `LazyColumn` des catégories (`heightIn(max = 260.dp)`, `:402`) imbriqué dans un parent défilant | Existant, non introduit par ce ticket | Hauteur déjà bornée, comportement inchangé par la scission — à ne pas régresser |
| Zone de filtres trop comprimée sur très petit écran | Faible | Le pied ne fait qu'une ligne (~56 dp + 20 dp) ; le reste revient aux filtres |

---

# 9. Plan de développement

- [ ] **Tâche 1 — Scinder `ModalBottomSheet` en zone défilante + pied fixe**

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
  Compile ; `./gradlew testDebugUnitTest` — suite existante intégralement verte (aucun test ne cible ce composable).

- [ ] **Tâche 2 — Vérification visuelle et fonctionnelle mobile**

  Objectif :
  Confirmer les critères d'acceptation n°1 à 4 et l'absence d'écart avec les maquettes hors repositionnement du bouton.

  Détail :
  - Comparer contre `docs/design-reference/screenshots/advanced-search-{filters-open-empty,filters-open-some-selected,category-open}.png` — seul écart attendu : la position du bouton.
  - À l'ouverture, contenu court (aucun genre) : bouton visible sans défilement initial, feuille pas anormalement étirée (vérifie `fill = false`, §7.2).
  - Défilement jusqu'aux genres : bouton toujours visible et activable.
  - Changer type/catégorie/note/année/genre : compteur `resultCount` se met à jour, bouton ne bouge pas, n'est pas recréé.
  - Valider : filtres en cours appliqués, volet fermé, comportement identique à avant.
  - Vérifier l'inset de barre de navigation gestuelle (§7.4) : si le bouton est recouvert par la barre système sur au moins un appareil testé, ajouter `Modifier.navigationBarsPadding()` sur le `Box` pied — pas avant, pas par précaution.

  Validation :
  Les 4 premiers critères d'acceptation de la section 6 cochés ; capture d'écran avant/après en cas d'ajustement d'inset.

- [ ] **Tâche 3 — Vérification focus D-pad Android TV**

  Objectif :
  Confirmer que le `.focusGroup()` déplacé sur la racine (§7.3) préserve une traversée continue filtres → bouton, sans piège de focus dans la zone défilante.

  Détail :
  - Naviguer au D-pad depuis l'en-tête jusqu'au bouton en traversant tous les filtres (y compris catégorie dépliée et genres) : la traversée doit rester continue.
  - Confirmer que le focus atteint bien le bouton (pas seulement défiler jusqu'à lui) et que son anneau de focus blanc 3dp s'affiche.
  - Confirmer que le `focusGroup()` interne du `LazyColumn` de catégories (`:407`) continue de fonctionner isolément, sans régression de la traversée de la liste déroulante des catégories.

  Validation :
  Critères d'acceptation n°5 de la section 6 coché.

- [ ] **Tâche 4 — Vérification petit écran**

  Objectif :
  Confirmer que la zone défilante reste utilisable et non recouverte sur petite hauteur (critère n°6).

  Détail :
  Tester sur le plus petit form-factor disponible (émulateur mobile bas de gamme ou fenêtre réduite) avec genres et catégorie dépliés simultanément : la zone défilante doit occuper l'espace restant sans que le pied ne la recouvre.

  Validation :
  Critère d'acceptation n°6 coché ; build final : `./gradlew assembleDebug` + `./gradlew lintDebug` sans erreur.

