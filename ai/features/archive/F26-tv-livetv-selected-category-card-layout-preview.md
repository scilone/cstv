# F26 - TV LiveTV Selected Category Card Layout & Preview

## Informations générales

Status:
RELEASED

Created:
2026-08-05

---

# 1. Description

Sur l'écran Live TV, lorsqu'une catégorie spécifique est sélectionnée :
1. Les informations textuelles de l'EPG (titre du programme, horaires, etc.) sont tronquées verticalement dans les cartes de chaînes (StreamTvCard). Il faut réduire l'espacement et adapter les dimensions pour que l'EPG s'affiche parfaitement.
2. Ajouter le même système de prévisualisation vidéo miniature en direct sur focus/survol stable que dans la catégorie "Tout".

---

# 2. Contexte

Les cartes de chaînes de la grille de catégorie (`LazyVerticalGrid`) affichent également l'EPG en cours. Cependant, à cause d'un espacement vertical trop grand, certains éléments textuels EPG sont tronqués en hauteur, ce qui nuit à l'esthétique et à l'utilisabilité. De plus, la prévisualisation vidéo est requise sur l'ensemble de la liste des chaînes pour une expérience homogène.

---

# 3. Spécification fonctionnelle

### User story

En tant qu'utilisateur Android TV, je peux lire l'ensemble des informations EPG d'une chaîne dans une catégorie précise et bénéficier de la même prévisualisation que dans « TOUT ».

### Parcours utilisateur

1. L'utilisateur choisit une catégorie Live TV puis parcourt la grille de chaînes.
2. Chaque carte affiche son numéro/nom, le programme courant, sa progression et sa plage horaire de façon lisible dans son contour, sans chevauchement ni découpe verticale.
3. Après 1 000 ms de focus continu, l'aperçu vidéo audible de la chaîne démarre ; il suit les mêmes règles d'arrêt que F25.
4. L'utilisateur peut lancer la chaîne avec un appui court ou modifier son favori avec un appui long, selon le contrat de F25.

### Règles métier

- La grille de catégorie applique le même contrat de prévisualisation, de favori et de lecture que F25 ; F26 dépend donc de la disponibilité de ce contrat.
- Avec ou sans EPG courant, la carte conserve une hauteur stable et ne masque aucun élément utile par manque d'espace vertical.
- L'absence d'EPG est indiquée de manière sobre ; elle ne crée ni carte vide ni changement de hauteur imprévisible.
- La lisibilité à distance prime sur toute réduction de typographie ; les valeurs de mise en page exactes relèvent de l'étape technique et du référentiel design.

### Critères d'acceptation

- Sur une carte avec EPG, titre du programme, barre de progression et horaire sont visibles simultanément, sans recouvrement ni troncature verticale.
- La carte respecte les mêmes actions D-Pad et délais de prévisualisation que la carte équivalente de « TOUT ».
- L'aperçu cesse immédiatement à la perte de focus et le lecteur principal ne subit aucune régression.
- Une carte sans EPG reste lisible et cohérente avec les autres cartes de la grille.

### Cas limites et erreurs

- Les titres EPG très longs peuvent être tronqués horizontalement sur une ligne, mais les informations de progression et d'horaire ne disparaissent pas verticalement.
- Une donnée EPG absente, tardive ou invalide n'empêche pas l'ouverture de la chaîne ni la navigation.
- En cas d'échec de l'aperçu, les garanties fonctionnelles de F25 s'appliquent.

---

# 4. Spécification technique

## Constat structurant : une seule carte pour les deux vues

`StreamTvCard` (`presentation/livetv/components/LiveTvComponents.kt:808`) est **le même composable** dans les deux contextes :

* rangées horizontales de la vue « TOUT », via `CategorySectionRow` (`LiveTvComponents.kt:139`) ;
* grille verticale d'une catégorie précise, via `LiveTvScreen.kt:445`.

Deux conséquences directes :

1. **Le volet « aperçu » de F26 est déjà livré par F25.** Corriger `StreamTvCard` pour la vue « TOUT » corrige mécaniquement la grille de catégorie. Il ne reste ici qu'à câbler l'état d'aperçu hissé jusqu'aux cellules de la grille — l'infrastructure, la temporisation, la politique de décodage et le cycle de vie sont intégralement définis par F25 et ne sont pas redéfinis ici.
2. **Le volet « mise en page » de F26 modifie une carte partagée.** Toute retouche de hauteur ou d'interlignes s'applique aussi aux rangées de « TOUT ». Ce n'est pas un effet de bord à subir mais un objectif : la règle métier de l'étape 2 impose la cohérence entre les deux vues.

## 1. Troncature verticale de l'EPG

### Mesure du problème

La carte est contrainte à `Modifier.height(84.dp)` avec un `Row` en `padding(8.dp)` : la colonne de texte dispose donc de **68 dp**. Elle empile quatre éléments dont aucun ne fixe son interligne :

| Élément | Ligne | `fontSize` | `lineHeight` | Marge |
| --- | --- | --- | --- | --- |
| `CH {num} {name}` | 863 | 13 sp | par défaut | — |
| Titre du programme | 874 | 11 sp | par défaut | `top = 2.dp` |
| `Spacer` | 882 | — | — | 4 dp |
| `EpgProgressBar` | 883 | — | 3 dp | — |
| Plage horaire | 890 | 9 sp | par défaut | `top = 2.dp` |

En l'absence de `lineHeight` explicite, Compose applique l'interligne de la police, soit environ 1,45 × la taille. Le cumul approche alors les 68 dp disponibles, et **tout dépassement se solde par une découpe verticale du dernier élément** — la plage horaire. Deux facteurs font basculer le calcul :

* `fontScale` supérieur à 1 dans les réglages d'accessibilité du téléviseur ;
* les métriques propres à la police du projet (`HankenGrotesk`, `BricolageGrotesque`), différentes de la police système.

Le voisin `RecentlyWatchedTvItem` (`LiveTvComponents.kt:700`) montre déjà le remède employé ailleurs dans le fichier : `fontSize = 10.sp, lineHeight = 12.sp`.

### Correction

Trois mesures combinées, à appliquer ensemble :

1. **Interligne explicite sur les trois textes**, pour rendre la hauteur déterministe et indépendante des métriques de police :
   ```kotlin
   Text(text = "CH ${stream.num} ${stream.name}", fontSize = 13.sp, lineHeight = 16.sp, maxLines = 1, ...)
   Text(text = epgProgram.title,                  fontSize = 11.sp, lineHeight = 13.sp, maxLines = 1, ...)
   Text(text = epgProgram.formattedTimeRange(),   fontSize = 9.sp,  lineHeight = 11.sp, maxLines = 1, ...)
   ```
2. **Resserrement des marges internes** : `Spacer` de 4 dp → 3 dp, `padding(top = 2.dp)` de la plage horaire → 1 dp.
3. **Hauteur de carte portée de 84 dp à 92 dp**, ce qui rétablit une marge de sécurité au lieu de compter sur un ajustement au pixel près.

Budget après correction : 16 + 2 + 13 + 3 + 3 + 1 + 11 = **49 dp** pour 76 dp disponibles (92 − 2 × 8 de padding). La réserve absorbe un `fontScale` jusqu'à environ 1,5.

**La lisibilité prime : aucune taille de police n'est réduite.** C'est la contrainte explicite de l'étape 2, et c'est pourquoi la hauteur augmente plutôt que la typographie ne diminue.

### Hauteur stable avec et sans EPG

La branche `else` (ligne 892) n'affiche qu'un texte « Aucune information de programme ». La carte étant à hauteur fixe, la stabilité est déjà acquise ; elle le reste après passage à 92 dp. Le critère « une carte sans EPG reste cohérente » est satisfait sans travail supplémentaire.

### Effet sur les cartes voisines

`SeeAllCard` (`LiveTvComponents.kt:186`) fixe en dur `Modifier.fillMaxWidth().height(84.dp)` en mode TV, précisément pour s'aligner sur `StreamTvCard` — son commentaire le documente. **Cette valeur doit passer à 92 dp dans le même changement**, sinon la carte « Voir tout » deviendra plus basse que ses voisines dans chaque rangée de la vue « TOUT ». C'est le piège le plus facile à manquer sur ce ticket.

Pour éviter la reconduction du problème, la hauteur est extraite en constante partagée :

```kotlin
/** Hauteur commune des tuiles de chaîne TV (StreamTvCard, SeeAllCard). */
internal val LIVE_TV_CARD_HEIGHT = 92.dp
```

## 2. Aperçu dans la grille de catégorie

`LiveTvScreen.kt:445` instancie déjà `StreamTvCard` dans la cellule. Il suffit de propager l'état hissé par F25 :

```kotlin
StreamTvCard(
    stream = stream,
    isFavorite = isFav,
    epgProgram = epgPrograms[stream.streamId],
    onLoadEpg = { onLoadEpg(stream.streamId) },
    onToggleFavorite = { onToggleFavorite(stream) },
    onClick = { onStreamSelected(stream) },
    previewActive = previewState.activeStreamId == stream.streamId,
    previewPlayer = previewState.player,
    onPreviewFocusChanged = { focused -> previewState.onFocusChanged(stream.streamId, focused) }
)
```

L'état `previewState` est celui déjà créé dans `TvLayout` par F25 : **une seule instance de lecteur pour tout l'écran**, quelle que soit la vue affichée. Aucun second lecteur n'est introduit ici.

Les contrats d'appui court, d'appui long et d'arrêt de l'aperçu sont ceux de F25, sans exception ni variante — conformément à la règle métier de l'étape 2.

## Composants impactés

| Fichier | Nature |
| --- | --- |
| `presentation/livetv/components/LiveTvComponents.kt` | `StreamTvCard` : interlignes explicites, marges resserrées, hauteur 92 dp ; `SeeAllCard` alignée ; constante `LIVE_TV_CARD_HEIGHT` |
| `presentation/livetv/LiveTvScreen.kt` | Grille de catégorie : propagation de l'état d'aperçu aux cellules |

Aucun nouveau composant, aucune nouvelle dépendance, aucune ressource, aucun changement de ViewModel, de couche `data` ou de schéma.

## Contraintes de performance

Marginales. La hauteur de carte accrue réduit d'environ une cellule le nombre d'éléments visibles simultanément dans la grille, ce qui allège plutôt la composition. Le coût de l'aperçu est intégralement porté par F25.

## Risques techniques

1. **Valeurs de mise en page à confirmer.** Les 92 dp et les interlignes proposés reposent sur un calcul de budget, pas sur une mesure à l'écran. `docs/design-reference/` doit être consulté avant application, et le PO valider le rendu réel. Si le référentiel impose 84 dp, l'alternative est de réduire l'interligne plutôt que la taille de police — jamais l'inverse.
2. **Oubli de `SeeAllCard`.** Signalé ci-dessus ; c'est la régression visuelle la plus probable de ce ticket.
3. **Dépendance dure à F25.** Sans le contrat d'aperçu, la moitié de ce ticket n'a pas d'objet. **F25 doit être livré avant F26.** Ils modifient de plus les deux mêmes fichiers : les paralléliser garantit un conflit de fusion.
4. **`fontScale` élevé.** La réserve calculée couvre environ 1,5. Au-delà, la troncature reviendrait. Si le cas doit être couvert, la parade est `maxLines = 1` associé à `TextOverflow.Ellipsis` — déjà en place sur les deux premiers textes, à ajouter sur la plage horaire.

## Validation automatisable

Aucune. Mise en page Compose et rendu vidéo ne sont pas vérifiables sans appareil (règle n°9). Validation limitée à `assembleDebug`, `lintDebug` et la non-régression de `testDebugUnitTest`. Le contrôle visuel — EPG complet, hauteurs homogènes entre `StreamTvCard` et `SeeAllCard` dans les deux vues — revient au PO.

---

# 5. Architecture

Ajustement de la mise en page de la carte partagée `StreamTvCard` en rendant les interlignes et hauteurs déterministes. Propagation de l'état d'aperçu de F25 à la grille verticale de la catégorie spécifique sur `LiveTvScreen.kt`.

---

# 6. Plan de développement

## Liste des tâches

- [x] Tâche 1 — Corriger les interlignes et augmenter la hauteur de la carte de chaîne

  **Objectif :**
  - Dans `LiveTvComponents.kt`, définir la constante globale `LIVE_TV_CARD_HEIGHT = 92.dp`.
  - Ajuster les interlignes (`lineHeight`) explicites de l'EPG dans `StreamTvCard` pour éviter la troncature verticale, et resserrer les espacements et marges.
  - Appliquer la hauteur `LIVE_TV_CARD_HEIGHT` à la fois à `StreamTvCard` et à `SeeAllCard`.

  **Fichiers :**
  - `presentation/livetv/components/LiveTvComponents.kt`

  **Validation :**
  - Les cartes de chaînes et "Voir tout" ont une hauteur homogène de 92 dp.

- [x] Tâche 2 — Propager la prévisualisation dans la grille de catégorie

  **Objectif :**
  Dans `LiveTvScreen.kt`, au niveau de la grille verticale (`LazyVerticalGrid`) affichant la catégorie spécifique, brancher les paramètres de prévisualisation de `StreamTvCard` sur le `previewState` partagé créé dans `TvLayout`.

  **Fichiers :**
  - `presentation/livetv/LiveTvScreen.kt`

  **Validation :**
  - Le code compile correctement et `./gradlew lintDebug` s'exécute sans erreur.

---

# 7. Notes de développement

- 2026-08-05 — Implémentation des 2 tâches :
  - `LIVE_TV_CARD_HEIGHT` portée de 84 dp à 92 dp, appliquée à `StreamTvCard` et `SeeAllCard` (constante déjà introduite lors de F25 pour éviter tout état intermédiaire non compilable).
  - `lineHeight` explicites ajoutés aux trois textes de `StreamTvCard` (16 sp / 13 sp / 11 sp), `Spacer` intermédiaire resserré à 3 dp, marge de la plage horaire resserrée à 1 dp — budget 49 dp sur 76 dp disponibles, conforme au calcul de l'étape 4. `maxLines = 1` + `TextOverflow.Ellipsis` ajoutés sur la plage horaire (risque technique n°4 de l'étape 4 : couverture au-delà de `fontScale` ≈ 1,5).
  - Propagation de l'aperçu dans la grille de catégorie déjà effectuée lors de F25 T5 (câblage trivial une fois `previewState` disponible au niveau de `TvLayout`) : `previewActive`, `previewPlayer` et `onPreviewFocusChanged` passés à `StreamTvCard` dans la `LazyVerticalGrid`, mêmes garanties de cycle de vie que F25 (aucune duplication de lecteur).
- Validation automatisée : `./gradlew testDebugUnitTest assembleDebug lintDebug` — `BUILD SUCCESSFUL`.
- Non vérifié (hors périmètre des critères automatisés, règle n°9) : rendu réel des hauteurs et interlignes à l'écran, absence de troncature visuelle, cohérence visuelle entre `StreamTvCard` et `SeeAllCard` dans les deux vues — revient au PO, conformément à l'étape 4.
- 2026-08-05 — Étape 7, correction de la review (voir section 8) :
  - **F26-R1** : résolu par la correction de F25-R1 dans l'état partagé (`presentation/livetv/components/LiveChannelPreview.kt`) — voir notes de développement F25 étape 7. `LiveChannelPreviewState` n'est plus reconstruite au changement de `enabled`/`credentials` (mémorisée uniquement sur `player`), donc la grille de catégorie de F26 hérite directement de la correction sans modification propre à `LiveTvScreen.kt` : elle continue de consommer l'unique `previewState` hissé dans `TvLayout`, désormais stable. Aucun second lecteur introduit.
- Validation automatisée (étape 7) : `./gradlew testDebugUnitTest assembleDebug lintDebug` — `BUILD SUCCESSFUL`.

---

# 8. Review

Date : 2026-08-05

Status : RESOLVED

## Périmètre relu

- `presentation/livetv/components/LiveTvComponents.kt`
- `presentation/livetv/LiveTvScreen.kt`
- dépendance d'aperçu `presentation/livetv/components/LiveChannelPreview.kt`
- `docs/design-reference/mockup-source/Refonte-IPTV.dc.html`
- `docs/design-reference/screenshots/tv.png`
- `docs/design-reference/screenshots/tv-category-filtered.png`

## Critique

Aucun constat.

## Majeur

### F26-R1 — RÉSOLU — La grille hérite de la fuite de lecture lors du passage hors ligne de F25

**Description :** la propagation dans la `LazyVerticalGrid` utilise correctement l'unique `previewState` de `TvLayout`, mais ce contrat dépend intégralement de F25. Comme documenté dans F25-R1, le remplacement de `LiveChannelPreviewState` lorsque `enabled` devient faux ne coupe pas nécessairement l'`ExoPlayer` déjà actif.

**Impact :** dans une catégorie précise, une perte réseau peut faire disparaître la miniature vidéo tout en laissant son audio actif. Le critère F26 imposant les mêmes règles d'arrêt que F25 n'est donc pas satisfait.

**Correction attendue :** résoudre F25-R1 dans l'état partagé, ajouter le test de non-régression correspondant, puis confirmer que la grille conserve ce même état corrigé sans introduire de second lecteur.

**Correction appliquée (2026-08-05) :** F25-R1 résolu dans `LiveChannelPreviewState`/`rememberLiveChannelPreviewState` (instance stable par `player`, `credentials` répercuté via `SideEffect`). La grille de catégorie (`LazyVerticalGrid` dans `LiveTvScreen.kt`) consomme sans changement l'unique `previewState` de `TvLayout`, désormais corrigé ; aucun second `ExoPlayer` introduit. Couverture de non-régression : `LiveChannelPreviewTest` (F25, `activatePreviewAfterDelay` + cycle de vie de `LiveChannelPreviewState`), partagée par construction avec F26 puisqu'il n'existe qu'un seul état.

## Mineur

Aucun constat propre à la mise en page.

## Vérifications effectuées

- `StreamTvCard` et `SeeAllCard` utilisent la même constante `LIVE_TV_CARD_HEIGHT = 92.dp` ; aucune hauteur voisine à 84 dp ne subsiste pour ces deux cartes TV.
- Les trois textes fixent leurs interlignes à 16 sp, 13 sp et 11 sp ; la plage horaire est limitée à une ligne avec ellipsis. Le budget reste inférieur aux 76 dp internes jusqu'au `fontScale` d'environ 1,5 annoncé dans le ticket.
- La grille de catégorie transmet bien `previewActive`, l'unique `previewPlayer` et `onPreviewFocusChanged` à chaque `StreamTvCard`, et l'appui court arrête l'aperçu avant la demande de lecture principale.
- Le référentiel visuel a été consulté. Il documente l'organisation et l'apparence des cartes Live, mais ne fournit pas de cote Android TV imposant 84 dp ou 92 dp ; le calcul du ticket reste donc la seule cote technique vérifiable sans rendu réel.
- Test ciblé de la logique pure partagée `LiveChannelPreviewTest` : succès (`BUILD SUCCESSFUL`).
- `git diff --check` : aucun défaut d'espaces dans les changements suivis.

## Limites de la review

L'absence réelle de troncature, la lisibilité à distance et l'homogénéité visuelle des cartes ne sont pas prouvées par les tests JVM. La stratégie du projet exclut l'observation manuelle sur appareil des critères de validation finale de l'agent ; aucun statut `VALIDATED` n'est donc attribué ici.

## Corrections demandées

- ~~Résoudre F26-R1 via la correction de F25-R1 avant l'étape 8.~~ RÉSOLU (2026-08-05).

## Validation finale (étape 8)

Date : 2026-08-05

- Comportement attendu : conforme — hauteur de carte homogène 92 dp (`StreamTvCard`/`SeeAllCard`), EPG lisible sans troncature verticale au calcul, aperçu vidéo propagé à la grille de catégorie avec le même contrat que F25.
- Règles métier : conformes ; dépendance dure à F25 satisfaite (F25 validé).
- Absence de régression : `./gradlew testDebugUnitTest assembleDebug lintDebug` — `BUILD SUCCESSFUL`.
- Tests validés : aucun test dédié à F26 (validation automatisable du ticket = « Aucune », mise en page Compose non testable JVM) ; non-régression couverte par `LiveChannelPreviewTest` côté état d'aperçu partagé.
- Rendu réel des hauteurs/interlignes, absence de troncature visuelle, cohérence visuelle `StreamTvCard`/`SeeAllCard` dans les deux vues : non vérifiables par tests JVM (règle n°9), hors périmètre de la validation automatisée de l'agent — revient au PO sur device.

Status : VALIDATED

---

# 9. Livraison Git (Étape 10)

Date : 2026-08-05

- Commit : `:sparkles: feat(livetv): implement hover video preview and long press favorite (F25 & F26)`
- Tag : `v1.74.0`
- APK : `releases/app-release.apk`
- Status : RELEASED
