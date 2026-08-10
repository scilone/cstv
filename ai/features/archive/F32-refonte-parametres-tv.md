# F32 - Paramètres TV : Refonte de l'écran des paramètres

## Informations générales

Status:
RELEASED

Created:
2026-08-10

Version:
v1.77.5

Date:
2026-08-10

---

# 1. Description

Refonte ergonomique et visuelle de l'écran des paramètres sur Android TV afin de respecter la charte esthétique de l'application et de supprimer les éléments non pertinents pour l'expérience TV :
- Retrait définitif du bouton de retour ("Retour") inutile sur l'écran TV (la touche Back physique de la télécommande gère déjà ce comportement).
- Suppression complète de la section de téléchargements ("Téléchargements hors-ligne") sur TV.
- Refonte des boutons d'action clés (Gérer les catégories, Forcer la mise à jour, Extraire les logs de diagnostic, et Déconnexion) pour s'aligner sur le design de l'application (utilisation de `Box` + focus liseré/lumière avec `AccentLavande`, `Surface3` ou `RatingDislike` au lieu de `TvButton` brut).

---

# 2. Contexte

L'écran de paramètres TV actuel utilise la bibliothèque `androidx.tv.material3.Button` brute qui applique ses propres styles et schémas de couleurs par défaut, créant une disparité esthétique majeure par rapport au reste de l'application TV (qui utilise des liserés sur mesure avec `AccentLavande` et des angles arrondis uniformes). De plus, certaines fonctionnalités comme les téléchargements hors-ligne sont spécifiques aux smartphones et n'ont aucun sens sur Android TV. Enfin, la présence d'un bouton de retour visuel surcharge l'UI TV inutilement alors que l'intégralité de la navigation TV repose sur la télécommande.

---

# 3. Objectif

- Assurer une harmonisation visuelle complète des boutons de paramètres TV en adoptant le design moderne et unifié de la charte graphique de l'app (radius 8 dp, liseré lumineux `AccentLavandeHover` / `AccentLavande` au focus, couleurs et contrastes maîtrisés).
- Retirer le bloc ou la carte de gestion des téléchargements spécifiquement pour l'affichage TV.
- Retirer le bouton Retour en haut de l'écran TV pour maximiser l'espace et simplifier la navigation au D-pad.
- Améliorer l'expérience utilisateur globale en rendant le focus plus lisible de loin sur les cartes et les actions de paramètres.

---

# 4. Spécification fonctionnelle

## User stories

- En tant qu'utilisateur Android TV, je consulte les paramètres sans bouton
  Retour redondant et utilise la touche Retour de la télécommande pour revenir
  à l'écran précédent.
- En tant qu'utilisateur TV, je ne vois pas une section de téléchargements
  hors-ligne qui ne correspond pas à mon usage.
- En tant qu'utilisateur naviguant au D-pad, je distingue immédiatement
  l'action focalisée et je reconnais visuellement qu'une déconnexion est une
  action destructive.

## Périmètre

- La refonte concerne exclusivement l'écran Paramètres en Android TV.
- La version mobile conserve son bouton Retour, sa section Téléchargements,
  ses actions, son ordre et son design actuels.
- Sur TV, seules les Téléchargements hors-ligne sont retirées. Les sections
  Gestion des catégories, mise à jour du cache, apparence des sous-titres et
  Diagnostic & logs restent présentes, dans leur ordre actuel et avec leurs
  fonctionnalités inchangées.

## Parcours utilisateur TV

### Arrivée et retour

1. L'utilisateur ouvre Paramètres depuis la navigation TV.
2. Le titre et les sections de réglages s'affichent sans bouton visuel
   « Retour ».
3. La touche Retour physique ferme l'écran ou revient à l'écran précédent selon
   le parcours de navigation existant ; elle ne déclenche ni déconnexion ni
   modification de réglage.

### Actions et focus

1. L'utilisateur navigue au D-pad entre les contrôles existants.
2. Les actions secondaires « Gérer les catégories » et « Extraire les logs de
   diagnostic » reposent sur une surface `Surface3` et un texte lisible.
3. Quand l'une de ces actions est focalisée, un liseré lumineux
   `AccentLavande`/`AccentLavandeHover` net et suffisamment contrasté indique
   la cible ; quitter le focus rétablit son état de repos.
4. « Forcer la mise à jour maintenant » conserve son rôle d'action principale
   de sa section : son état normal, indisponible pendant la synchronisation et
   son indicateur de progression restent explicites. Le focus applique le même
   langage lavande cohérent.
5. Les options de fréquence, de sous-titres et de mode debug gardent leur état
   sélectionné et leur comportement actuel ; leur refonte visuelle ne change
   aucune valeur ni règle de préférence.

### Déconnexion

1. L'action « Se déconnecter » est placée après les sections de réglages, sans
   devenir accessible par erreur lors de l'arrivée sur l'écran.
2. Elle utilise la couleur destructive `RatingDislike` et un liseré rouge au
   focus, afin de ne pas être confondue avec une action de configuration.
3. Un appui OK conserve le flux de déconnexion existant ; aucun identifiant,
   téléchargement mobile ou réglage n'est supprimé par la seule prise de
   focus.

## Règles d'interface et métier

- Aucun autre regroupement, déplacement ou retrait de réglage n'est effectué :
  l'écran est simplifié uniquement par l'absence des Téléchargements hors-ligne
  et du bouton Retour TV.
- Les actions secondaires ont `Surface3` comme conteneur au repos ; elles ne
  deviennent pas des boutons lavande pleins. La lavande sert au repère de focus
  et à l'action principale de synchronisation.
- L'état focalisé est visible à distance sans dépendre d'une variation de
  graisse du texte uniquement.
- Une action désactivée, notamment une synchronisation déjà en cours, ne peut
  pas être déclenchée au D-pad et conserve un rendu visuellement indisponible.
- La suppression du bloc TV de téléchargements ne retire pas la route, les
  téléchargements, la lecture hors-ligne ou les contrôles mobiles existants.

## Cas limites et gestion des erreurs

- Si la synchronisation est déjà en cours, l'action force-sync reste désactivée
  et affiche son état de progression existant ; des appuis répétés ne créent
  pas de seconde synchronisation.
- Si l'extraction des logs échoue, le retour utilisateur existant est conservé
  et aucune erreur technique brute n'est affichée.
- Si aucune catégorie ne peut être gérée temporairement, l'accès conserve le
  comportement d'erreur ou d'état vide existant, sans bloquer les autres
  réglages.
- La touche Retour de la télécommande reste disponible depuis chaque position
  de focus, y compris depuis la dernière action destructive.
- Le retrait des Téléchargements hors-ligne est strictement TV : le même compte
  conserve ses téléchargements et leur gestion sur mobile.

## Critères d'acceptation

- [ ] L'écran Paramètres TV ne contient aucun bouton visuel « Retour », tandis
  que la touche Retour de la télécommande conserve son comportement existant.
- [ ] L'écran Paramètres mobile est inchangé, y compris son bouton Retour et
  son bloc Téléchargements hors-ligne.
- [ ] Aucun bloc ni bouton de gestion des Téléchargements hors-ligne n'est
  visible sur Android TV.
- [ ] Les sections Catégories, mise à jour du cache, sous-titres et diagnostic
  restent disponibles et conservent leurs actions actuelles.
- [ ] Les actions secondaires TV ont `Surface3` au repos et un liseré lavande
  clairement visible au focus.
- [ ] L'action de synchronisation garde un état principal cohérent, ne peut pas
  être relancée pendant son exécution et reste focalisable de manière lisible
  lorsqu'elle est disponible.
- [ ] « Se déconnecter » possède un rendu destructif rouge et un liseré rouge
  au focus, sans changer son flux fonctionnel.
- [ ] La navigation D-pad et les comportements de préférences existants ne
  régressent pas.

---

# 5. Décisions fonctionnelles actées

1. Aucun regroupement ou retrait supplémentaire n'est fait sur TV au-delà des
   Téléchargements hors-ligne et du bouton Retour visuel.
2. Les actions secondaires utilisent `Surface3` avec un liseré lavande au
   focus.
3. La déconnexion utilise `RatingDislike` avec un liseré rouge au focus.
4. Le mobile est hors périmètre et conserve intégralement son écran actuel.

# 6. Spécification technique

## Composants impactés

| Fichier | Nature du changement |
| --- | --- |
| `presentation/settings/SettingsScreen.kt` | Seul fichier de production modifié : refonte de `TvSettingsLayout` et des actions TV |
| `presentation/navigation/NavGraph.kt` | Aucun changement : la signature de `SettingsScreen` est conservée |

Détail des modifications dans `SettingsScreen.kt` :

- `TvSettingsLayout` — retrait du bloc `Row` d'en-tête contenant le `TvButton`
  « Retour » (le titre reste, aligné à gauche) ; retrait de l'appel à
  `TvManageDownloadsCard` ; le paramètre `onBack` et `onManageDownloads`
  disparaissent de **cette fonction privée** uniquement.
- `TvManageDownloadsCard` — **supprimée** (plus aucun appelant ; la laisser
  produirait un avertissement de code mort).
- `TvManageCategoriesCard`, `TvDiagnosticCard`, `TvSyncFrequencyCard` — leurs
  `TvButton` internes sont remplacés par le nouveau composable d'action.
- Bouton de déconnexion TV — remplacé par la variante destructive du même
  composable, avec la couleur de thème `RatingDislike` à la place du littéral
  `Color(0xFFCF6679)` actuellement écrit en dur.

## Nouveaux composants

Deux composables privés, définis dans `SettingsScreen.kt` et préfixés du nom de
l'écran conformément aux conventions (ils ne sont utilisés que par cet écran,
donc pas de promotion dans `presentation/components/`) :

```kotlin
@Composable
private fun TvSettingsActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    containerColor: Color = Surface3,
    contentColor: Color = Color.White,
    focusBorderColor: Color = AccentLavandeHover
)

@Composable
private fun TvSettingsDestructiveButton(   // délègue au précédent
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
)
```

Structure interne : `Box` → `clip(RoundedCornerShape(8.dp))` →
`background(containerColor)` → `tvFocusHighlight(...)` →
`clickable(enabled = enabled, interactionSource = ...)` →
`Row` centré (icône optionnelle + `TvText`).

L'état focalisé provient d'un `MutableInteractionSource` +
`collectIsFocusedAsState()`, comme les autres surfaces TV du projet.

## Réutilisation de l'existant

Le liseré de focus réutilise `Modifier.tvFocusHighlight()`
(`presentation/components/TvFocusHighlight.kt`) plutôt qu'un `border`
manuscrit : c'est le point unique qui gère déjà l'interaction avec le sélecteur
pivot (`LocalTvFocusSelector`) et évite le double cadre décrit dans sa
documentation. Paramètres retenus :

- forme : `RoundedCornerShape(8.dp)` (radius de la charte) ;
- largeur focalisée : `2.dp` (les vignettes média utilisent `1.5.dp` ; les
  actions de paramètres sont plus petites et lues de plus loin, la
  spécification exige un repère net à distance) ;
- couleur focalisée : `AccentLavandeHover` pour les actions de réglage,
  `RatingDislike` pour la déconnexion ;
- repos : `restingWidth = 0.dp` (`Surface3` porte seul l'état de repos).

Couleurs prises dans `presentation/theme/Color.kt` : `Surface1`, `Surface3`,
`AccentLavande`, `AccentLavandeHover`, `RatingDislike`. Aucune nouvelle couleur
n'est introduite.

## Modèles de données, API, stockage

Aucun. `SettingsState`, `SettingsViewModel`, `SettingsManager`, les
préférences (fréquence de synchronisation, style de sous-titres, mode debug) et
les routes de navigation sont strictement inchangés. La modification est
exclusivement présentationnelle et locale à un fichier.

La route `downloads` et `DownloadsScreen` restent en place et atteignables
depuis le mobile : seule la carte d'entrée TV disparaît.

## Comportement du retour TV

Le bouton visuel supprimé, le retour repose sur la gestion native de
`NavHost` (`popBackStack` sur la touche Back), déjà active sur toutes les
routes TV. `SettingsScreen(onBack = ...)` conserve son paramètre — il reste
utilisé par la branche mobile — donc `NavGraph.kt` n'est pas touché.

## Accessibilité et focus

- Le premier élément focusable de l'écran TV devient « Gérer les catégories »,
  ce qui satisfait l'exigence « la déconnexion ne doit pas être atteignable par
  erreur à l'arrivée » sans code de focus initial supplémentaire.
- L'ordre de parcours vertical du `Column` est conservé, donc la navigation
  D-pad reste linéaire et prévisible.
- Une action désactivée (`enabled = false`, cas de la synchronisation en cours)
  passe `clickable(enabled = false)` : le nœud n'est plus cliquable et le rendu
  utilise un contenu grisé. Le rendu et le libellé de progression existants
  (`state.isSyncingNow`) sont conservés tels quels.
- Les `contentDescription` existants sont conservés sur les icônes.

## Performances

Un `Box` + `Row` remplace un `androidx.tv.material3.Button` : composition plus
légère, aucun impact mesurable. Pas de recomposition supplémentaire —
l'`interactionSource` est mémorisé par bouton.

## Risques techniques

1. **Focus perdu ou piégé** après le retrait du premier élément focusable de la
   colonne (le bouton Retour). Risque faible : le `Column` reste
   `verticalScroll` et contient plusieurs cibles focusables. À vérifier en
   relecture : aucune `FocusRequester` de l'écran ne visait le bouton Retour
   (ce n'est pas le cas aujourd'hui, l'écran n'en utilise aucune).
2. **Perte du comportement de clic au D-pad** : `Modifier.clickable` réagit à
   `KEYCODE_DPAD_CENTER`/`ENTER` sur TV, comme les autres surfaces `Box`
   focalisables déjà en place dans l'application (rail de navigation, vignettes
   catalogue). Le patron est donc déjà éprouvé dans le projet.
3. **Import résiduel** de `androidx.tv.material3.Button as TvButton` s'il ne
   reste plus d'usage : à retirer pour éviter l'avertissement lint.
4. **Débordement de périmètre** : la tentation de rationaliser aussi les
   options de fréquence / sous-titres (`TvSortingOptionButton`) est écartée —
   la spécification interdit tout autre déplacement ou refonte.

## Dépendances

Aucune nouvelle dépendance, aucune règle ProGuard, aucune migration.

---

# 7. Architecture

## Positionnement

La modification est entièrement contenue dans la couche `presentation`, au
niveau le plus bas de la hiérarchie (composables privés d'un seul écran). Les
couches `domain` et `data` ne sont pas traversées : aucun use case, repository
ou DAO n'est sollicité différemment.

## Structure cible de l'écran TV

```
SettingsScreen(isTv = true)
 └─ TvSettingsLayout                      [Column scrollable, 85 % de largeur]
      ├─ Titre « PARAMÈTRES DE L'APPLICATION »        (sans bouton Retour)
      ├─ Texte d'introduction
      ├─ TvManageCategoriesCard      → TvSettingsActionButton (Surface3 / lavande)
      ├─ TvSyncFrequencyCard         → options existantes
      │                                + TvSettingsActionButton (action principale, enabled = !isSyncingNow)
      ├─ TvSubtitleStyleCard         → inchangée
      ├─ TvDiagnosticCard            → TvSettingsActionButton (Surface3 / lavande)
      └─ TvSettingsDestructiveButton → « Se déconnecter » (RatingDislike)
                                        ⟵ TvManageDownloadsCard supprimée
```

## Responsabilités

- `SettingsScreen` — aiguillage mobile/TV, inchangé (mêmes paramètres, même
  `viewModel`).
- `TvSettingsLayout` — composition et ordre des sections TV ; ne connaît plus
  ni le retour visuel ni les téléchargements.
- `TvSettingsActionButton` — unique porteur du langage visuel des actions TV :
  forme, surface de repos, liseré de focus, état désactivé. Toute évolution
  future du style d'action TV se fait à ce seul endroit.
- `MobileSettingsLayout` et ses cartes — inchangés, hors périmètre.

## Décisions techniques actées

1. Un composable d'action unique et paramétré, plutôt que quatre boutons
   stylés indépendamment : c'est ce qui rend l'homogénéité vérifiable et non
   pas seulement constatée.
2. Réutilisation de `Modifier.tvFocusHighlight()` au lieu d'un `border`
   spécifique, pour rester compatible avec le sélecteur de focus TV existant.
3. Les couleurs viennent du thème ; le littéral `Color(0xFFCF6679)` en dur dans
   l'écran est remplacé par `RatingDislike` (même valeur, source unique).
4. `TvManageDownloadsCard` est supprimée et non masquée par une condition : le
   périmètre TV exclut la fonctionnalité, une carte morte derrière un `if`
   serait de la dette immédiate.
5. La signature publique de `SettingsScreen` est préservée pour ne pas toucher
   `NavGraph.kt`.

## Stratégie de tests

Conformément à AGENTS.md (« pas de test de code de layout pur : couleurs,
dimensions », « tests UI Compose non prioritaires »), cette tâche n'introduit
pas de test unitaire : elle ne contient aucune logique métier, aucun état, aucun
calcul testable en JVM.

Validation retenue :

- `./gradlew assembleDebug` et `./gradlew lintDebug` sans erreur (dont absence
  d'import ou de composable inutilisé) ;
- `./gradlew testDebugUnitTest` en non-régression, notamment les tests
  existants de `presentation/settings` — aucun ne doit être impacté, ce qui
  constitue précisément la preuve que la refonte est purement visuelle.

Point signalé au PO : les critères d'acceptation portant sur la lisibilité du
focus « à distance » et sur la navigation D-pad ne sont pas automatisables dans
ce projet (pas d'infrastructure `androidTest`) ; ils relèvent d'une validation
visuelle de votre côté et ne bloquent pas la livraison technique.

---

# 8. Plan de développement

- [x] F32-1 — Créer le composable d'action TV unifié

  Objectif : introduire le composable privé réutilisable qui porte les états
  normal, focalisé et désactivé des actions de paramètres TV, ainsi que sa
  variante destructive, en réutilisant le surlignage de focus du projet.

  Fichiers :
  - `presentation/settings/SettingsScreen.kt`
  - `presentation/components/TvFocusHighlight.kt` (réutilisation, aucune
    modification attendue)

  Validation : l'action secondaire a `Surface3` au repos et un liseré lavande
  au focus ; la variante destructive utilise `RatingDislike` et un liseré
  rouge ; une action désactivée ne peut pas être cliquée au D-pad.

- [x] F32-2 — Recomposer exclusivement la branche Paramètres TV

  Objectif : enlever le bouton Retour visuel et la carte Téléchargements de
  `TvSettingsLayout`, puis raccorder le nouveau composable aux actions
  Catégories, synchronisation, logs et déconnexion en préservant leurs
  callbacks et états existants.

  Fichiers :
  - `presentation/settings/SettingsScreen.kt`

  Validation : le premier contrôle TV reste « Gérer les catégories », la touche
  Retour conserve le `popBackStack` existant, aucune référence morte à la carte
  TV de téléchargements ou à `TvButton` ne subsiste, et `MobileSettingsLayout`
  ainsi que la route `downloads` ne changent pas.

- [x] F32-3 — Vérifier la non-régression de l'écran Paramètres

  Objectif : contrôler que cette refonte de présentation ne casse ni les
  préférences existantes ni la compilation des deux branches mobile/TV.

  Fichiers :
  - `presentation/settings/SettingsScreen.kt`
  - suites de tests existantes concernées par les paramètres, si présentes

  Validation : `./gradlew testDebugUnitTest`, `./gradlew assembleDebug` et
  `./gradlew lintDebug` réussissent ; le lint ne remonte aucun import, composable
  privé ou API TV inutilisé.

---

# 9. Review

Date : 2026-08-10

Status : RESOLVED

## Périmètre relu

- `presentation/settings/SettingsScreen.kt`
- `presentation/components/TvFocusHighlight.kt` (réutilisation)
- `presentation/navigation/NavGraph.kt` (contrat de retour et route downloads)
- `docs/design-reference/mockup-source/Refonte-IPTV.dc.html`
- `docs/design-reference/screenshots/settings.png`
- `app/src/main/res/values/strings.xml`

## Critique

Aucun constat.

## Majeur

### F32-R1 — Les actions principale et destructive ont perdu les aplats de couleur de la charte

**Description :** `TvSettingsActionButton` utilise `Surface3` par défaut.
`TvSyncFrequencyCard` appelle ce composable sans fournir de `containerColor` :
« Forcer la mise à jour maintenant » devient donc une action sombre, alors que
la spécification réserve aussi la lavande à cette action principale et que la
référence design la représente remplie avec `AccentLavande`. La variante
`TvSettingsDestructiveButton` conserve également le conteneur `Surface3` et ne
passe `RatingDislike` qu'au texte et au liseré. La référence design montre au
contraire un bouton de déconnexion rempli en `RatingDislike`, avec un contenu
foncé contrasté.

**Impact :** les deux actions qui doivent être immédiatement distinguées des
actions secondaires ont désormais le même fond que ces dernières — et même le
même fond que leurs cartes parentes. La hiérarchie visuelle demandée disparaît :
la synchronisation n'est plus identifiable comme action principale et la
déconnexion n'a pas le rendu destructif rouge attendu au repos.

**Correction attendue :** fournir explicitement l'aplat `AccentLavande` à
l'action de synchronisation et `RatingDislike` à la variante destructive, avec
des couleurs de contenu contrastées conformes à la maquette. Conserver un état
de focus nettement distinct, l'état désactivé de la synchronisation et le
composable unifié; les actions secondaires Catégories et Diagnostic restent
sur `Surface3`.

## Mineur

Aucun constat supplémentaire.

## Vérifications effectuées

- Le bouton Retour visuel et `TvManageDownloadsCard` ont bien disparu de la
  branche TV uniquement.
- `MobileSettingsLayout`, sa carte de téléchargements et son callback `onBack`
  sont conservés; la route `downloads` existe toujours dans `NavGraph.kt`.
- Catégories, fréquence/synchronisation, sous-titres, diagnostic/debug et
  déconnexion conservent leurs callbacks et leur ordre.
- Le composable unifié utilise `RoundedCornerShape(8.dp)`, un liseré de focus
  de 2 dp via `tvFocusHighlight` et `clickable(enabled = false)` pendant la
  synchronisation.
- `./gradlew --no-daemon --max-workers=1 testDebugUnitTest assembleDebug lintDebug`
  : succès (`BUILD SUCCESSFUL`).
- `git diff --check` : aucun défaut d'espaces.

## Limites de la review

La lisibilité à distance, la navigation D-pad et le défilement automatique
jusqu'à la dernière action ne sont pas observables par les tests JVM du projet.
Ils ne sont pas présentés ici comme validés. F32-R1 est en revanche établi par
la comparaison statique des couleurs câblées avec le ticket et la source de
vérité visuelle.

## Corrections demandées

- Corriger F32-R1 avant l'étape 8.

## Corrections appliquées à l'étape 7

### F32-R1 — Résolu

- Ajout de `OnRatingDislike` (`presentation/theme/Color.kt`) : contenu foncé
  contrasté (`#1A0D10`, valeur de la maquette) pour un aplat `RatingDislike`.
- `TvSyncFrequencyCard` fournit désormais explicitement `containerColor =
  AccentLavande` à l'action « Forcer la mise à jour maintenant » (texte et
  icône restent blancs par défaut, conforme à la maquette), avec un liseré de
  focus blanc (au lieu de `AccentLavandeHover`, peu contrasté sur un fond déjà
  lavande).
- `TvSettingsDestructiveButton` passe `containerColor = RatingDislike` et
  `contentColor = OnRatingDislike` (au lieu de ne colorer que le liseré et le
  texte), avec un liseré de focus blanc pour la même raison de contraste — ce
  choix reprend la convention déjà en place dans `TvSortingOptionButton`, où le
  focus est toujours marqué en blanc quel que soit le fond.
- Les actions secondaires Catégories et Diagnostic restent inchangées
  (`Surface3` au repos, liseré `AccentLavandeHover` au focus).
- `./gradlew --no-daemon --max-workers=1 testDebugUnitTest assembleDebug lintDebug`
  : succès (`BUILD SUCCESSFUL`) — aucun test n'est requis pour ce changement
  purement visuel (AGENTS.md), la non-régression est garantie par la
  compilation et le lint.
- `git diff --check` : aucun défaut d'espaces.
