# F11 - Visibilité de la barre de statut sur Mobile (Gestion du poinçon de caméra)

## Informations générales

Type:
Feature

Status:
TASK BREAKDOWN

Created:
2026-07-23

---

# 1. Description

Cette fonctionnalité vise à restaurer l'affichage de la barre de statut système (heure, batterie, notifications) lors de la navigation générale sur l'application mobile, tout en adaptant l'interface utilisateur pour que le poinçon de la caméra frontale (notch / punch hole) ne gêne plus et ne masque plus les contrôles d'interface (boutons, en-têtes, etc.).

Lors de la lecture d'un contenu vidéo (dans les lecteurs Live TV, VOD ou Séries), l'application basculera automatiquement en mode plein écran immersif pour garantir une expérience de visionnage optimale sans distractions.

---

# 2. Contexte

Pourquoi cet élément existe :
- **Confort de l'utilisateur** : Durant la navigation dans les catalogues volumineux, les utilisateurs mobile aiment pouvoir consulter l'heure, l'état de charge de leur batterie ou leurs notifications système en un clin d'œil sans avoir à quitter l'application ou dérouler manuellement le volet système.
- **Problème d'obstruction par le poinçon (Notch)** : Actuellement, le mode plein écran forcé de l'application mobile (`NoActionBar.Fullscreen`) fait dessiner l'interface sous la zone physique réservée à la caméra frontale. Sur de nombreux téléphones modernes (écrans à poinçon ou encoche), cette caméra vient masquer des éléments d'interface essentiels de l'application, tels que les boutons de retour en haut à gauche, les barres d'onglets de recherche, ou le titre de la fiche en cours.
- **Préservation de l'expérience TV** : Sur Android TV, le concept de barre de statut ou de poinçon de caméra n'existe pas. Le plein écran et le focus doivent y rester inchangés.

---

# 3. Objectif

- Afficher la barre de statut système (heure, réseau, batterie, icônes système) sur les appareils mobiles lors de toutes les phases de navigation (écrans de Connexion, Sélection de Profil, Accueil, Grilles, Fiches Détails, Paramètres).
- Assurer une intégration esthétique et cohérente de la barre de statut (ex : fond transparent ou noir pour se fondre dans le thème sombre global de l'application).
- Adapter l'interface graphique mobile pour que tous les écrans et barres d'en-tête (TopBar, titres) respectent les marges de sécurité système (insets), poussant automatiquement les contrôles cliquables sous la zone physique du poinçon / de l'encoche de l'appareil.
- Masquer dynamiquement la barre de statut et de navigation système uniquement lorsque l'utilisateur lance une lecture vidéo, afin de lui offrir un plein écran total immersif (style Netflix / YouTube).

---

# 4. Spécification fonctionnelle

## User stories

- En tant qu’utilisateur mobile, je veux voir l’heure, l’état de la batterie et les notifications pendant que je navigue dans l’application, afin de ne pas devoir quitter l’écran courant.
- En tant qu’utilisateur mobile possédant un écran avec encoche ou poinçon, je veux que les boutons et informations interactives restent hors de cette zone physique, afin de pouvoir les voir et les utiliser sans gêne.
- En tant que spectateur, je veux que le lecteur vidéo occupe l’écran sans les barres système, afin de regarder un contenu sans distraction.
- En tant qu’utilisateur Android TV, je veux conserver le comportement visuel et de navigation actuel, afin que cette amélioration mobile n’introduise aucune régression sur TV.

## Périmètre et comportement attendu

### Navigation mobile

- La barre d’état Android est visible sur tous les écrans de navigation mobile : connexion, sélection de profil, accueil, Live TV hors lecture, films, séries, recherche, favoris, téléchargements, fiches détail et paramètres.
- La barre de navigation système inférieure reste elle aussi visible ou accessible selon le mode de navigation configuré par Android ; l’application ne la masque pas de force hors lecture.
- L’application conserve son thème sombre sous la barre d’état : son fond est transparent lorsque le contenu sombre lui sert d’arrière-plan, ou sombre lorsqu’un fond opaque est nécessaire à la lisibilité. Les icônes système doivent rester lisibles.
- Aucun contrôle tactile, texte important, champ de saisie, onglet, bouton de retour ou titre ne doit être placé sous la découpe d’écran, le poinçon de caméra, la barre d’état ou la barre de navigation.
- Les écrans défilants et leurs contenus respectent les zones sûres sans introduire de zone interactive inaccessible ni modifier le comportement attendu du défilement.

### Lecture vidéo mobile

- Dès l’ouverture d’une lecture Live TV, VOD ou d’un épisode de série, les barres d’état et de navigation système sont masquées afin d’offrir un affichage immersif.
- En mode immersif, la vidéo peut s’étendre derrière une encoche ou un poinçon, y compris en paysage. Cette légère obstruction éventuelle est acceptée pour privilégier la surface de visionnage.
- La sortie du lecteur, l’arrêt de la lecture ou le retour à un écran de navigation rétablit immédiatement les barres système et le respect des zones sûres de cet écran.
- La remise au premier plan de l’application pendant une lecture conserve le mode immersif ; son passage en arrière-plan ne doit pas laisser les barres système dans un état incohérent au retour.

### Android TV

- Cette fonctionnalité ne modifie ni le plein écran, ni le focus, ni la navigation Android TV.

## Parcours utilisateur

1. L’utilisateur ouvre l’application mobile : la barre d’état est visible et l’écran de connexion commence sous sa zone sûre.
2. Il navigue entre les écrans et utilise leurs en-têtes, champs et boutons sans qu’ils soient masqués par une découpe ou par les barres système.
3. Il lance un flux Live TV, un film ou un épisode : l’application passe en affichage immersif et masque les barres système.
4. Il quitte le lecteur : il retrouve l’écran de navigation avec les barres système visibles et des contrôles correctement décalés des zones dangereuses.

## Règles métier

- Le mode immersif est réservé strictement aux écrans de lecture vidéo mobile.
- Les overlays, dialogues, feuilles modales et états de chargement affichés pendant la navigation respectent les mêmes zones sûres que l’écran qui les porte.
- L’orientation ou la présence/absence d’une encoche, d’un poinçon ou d’une barre de navigation gestuelle ne doit jamais empêcher l’accès à une action de navigation.

## Critères d’acceptation

- [ ] Sur un téléphone mobile, l’heure et les icônes système sont visibles sur chaque écran de navigation couvert par le périmètre.
- [ ] Sur un appareil à encoche ou poinçon, aucun contrôle interactif ni information essentielle de navigation n’est caché derrière la découpe ou la barre d’état.
- [ ] La barre de navigation système n’est pas masquée de force pendant la navigation mobile.
- [ ] Le lancement de chacun des lecteurs Live TV, VOD et Série masque les deux barres système.
- [ ] La sortie de chacun de ces lecteurs réaffiche les barres système sans action supplémentaire de l’utilisateur.
- [ ] Le lecteur mobile reste immersif en paysage et peut utiliser toute la surface de l’écran, y compris derrière une découpe.
- [ ] Le comportement Android TV existant est inchangé.

## Cas limites et gestion des erreurs

- Sur un appareil sans encoche, les mêmes marges système sont appliquées sans créer d’espace visuel anormal ni masquer de contenu.
- Sur un appareil utilisant la navigation gestuelle ou les boutons système, l’interface reste entièrement atteignable et les gestes système conservent leur fonctionnement natif.
- Une rotation d’écran, une interruption temporaire, un verrouillage/déverrouillage ou un retour depuis l’arrière-plan ne doit pas laisser les barres système dans le mauvais état pour l’écran affiché.
- Si Android ne permet pas d’appliquer immédiatement l’état demandé des barres système, l’application doit conserver un écran utilisable : aucun bouton essentiel ne doit rester sous une zone système.

---

# 5. Hypothèses pour l’étape d’architecture

- **Thème système Android** : Le thème actuel `@style/Theme.IptvXtream` hérite de `android:Theme.Material.NoActionBar.Fullscreen`. Nous pourrions modifier l'Activity principale (`MainActivity.kt`) pour désactiver dynamiquement le plein écran système uniquement sur mobile via les API de fenêtrage de l'activité, ou découpler le thème mobile de celui d'Android TV.
- **Jetpack Compose WindowInsets** : Nous pourrons nous appuyer sur les mécanismes d'insets officiels de Jetpack Compose (`WindowInsets.statusBars`, `WindowInsets.systemBars` ou les modificateurs `statusBarsPadding()`, `systemBarsPadding()`) pour insérer des espacements de sécurité sur mobile là où c'est nécessaire.
- **Contrôle du lecteur** : Pendant l'affichage de l'un des trois lecteurs vidéo, les API système (`WindowInsetsControllerCompat` ou équivalents) seront utilisées pour masquer temporairement les barres système et passer en mode immersif complet.

---

# 6. Décisions fonctionnelles prises

- La barre de navigation système inférieure est conservée hors lecture ; elle n’est jamais masquée de force pendant la navigation.
- En lecture paysage, la vidéo s’étend derrière le poinçon ou l’encoche afin de privilégier le plein écran immersif.

---

# 7. Spécification technique

## 7.1 État actuel (constats codebase)

- **Thème** : `res/values/styles.xml` définit un seul `Theme.IptvXtream` héritant de `android:Theme.Material.NoActionBar.Fullscreen`. Le suffixe `.Fullscreen` force le flag système de plein écran (barre d'état masquée) sur **toutes** les plateformes, mobile comme TV.
- **Activity unique** : `MainActivity.kt` (single-activity) sert TV **et** mobile ; `isTvDevice()` distingue les deux via `UiModeManager`. Le layout est unifié en Jetpack Compose (`Scaffold` + `AppNavGraph`) pour les deux plateformes.
- **Aucun usage existant** de `WindowInsets` (Compose), `WindowCompat`, `WindowInsetsControllerCompat`, `setDecorFitsSystemWindows` ou de constantes immersives dans tout `app/src/main` : la fonctionnalité est greenfield.
- **Routes lecteurs** : les 3 routes plein écran sont déjà connues de `MainActivity.kt` (`live_player`, `vod_player`, `series_player`, cf. calcul de `showBottomBar` ligne 174). Écrans correspondants : `presentation/player/PlayerScreen.kt` (Live), `presentation/vod/VodPlayerScreen.kt`, `presentation/series/SeriesPlayerScreen.kt`.
- **Manifest** : `MainActivity` sans `screenOrientation` (rotation libre), `configChanges="orientation|screenSize|screenLayout|keyboardHidden|smallestScreenSize"` → pas de recréation d'Activity à la rotation ; le contrôleur d'insets doit donc réappliquer l'état des barres à chaud, pas seulement dans `onCreate`.

## 7.2 Composants impactés

| Fichier | Modification |
|---|---|
| `app/src/main/res/values/styles.xml` | Retirer `.Fullscreen` du parent de thème ; ajouter `android:windowLayoutInDisplayCutoutMode` = `shortEdges` (API 27+, ignoré avant) pour autoriser le dessin derrière le poinçon en immersif. |
| `app/src/main/java/com/cstv/app/MainActivity.kt` | Activer l'edge-to-edge (`WindowCompat.setDecorFitsSystemWindows(window, false)`) dans `onCreate` ; piloter la visibilité des barres via un effet Compose keyé sur `(isTv, currentRoute)`. |
| **Nouveau** `presentation/theme/SystemBarsController.kt` (ou `presentation/components/`) | Composable/effet réutilisable encapsulant `WindowInsetsControllerCompat` : masque les barres si `isTv` **ou** route lecteur, les réaffiche sinon. |
| En-têtes / TopBars mobile des écrans du périmètre | Vérifier l'application des `paddingValues` du `Scaffold` (ou `statusBarsPadding()`) pour ne pas passer sous la barre d'état / le poinçon. Audit ciblé lors du découpage (étape 4). |

## 7.3 Dépendances

- **Aucune nouvelle dépendance.** `WindowCompat` / `WindowInsetsControllerCompat` proviennent d'`androidx.core` (déjà présent via core-ktx). Les insets Compose (`WindowInsets.statusBars`, `systemBarsPadding()`, `statusBarsPadding()`) sont fournis par `compose.foundation` déjà utilisé.

## 7.4 Compatibilité

- **Min SDK 21** : `WindowInsetsControllerCompat` fournit un repli propre sur les anciennes API (bascule automatique vers les anciens flags `SYSTEM_UI_FLAG_*`). L'attribut `windowLayoutInDisplayCutoutMode` (poinçon) n'existe qu'en API 27/28+ : sur appareils plus anciens (sans poinçon), il est simplement ignoré → aucun effet indésirable.
- **Android TV** : le comportement plein écran est **préservé** en forçant le masquage des barres quand `isTv == true`, quel que soit l'écran. Aucune régression de focus/navigation (le code TV ne touche pas aux insets).

## 7.5 Performance / sécurité

- Impact négligeable : opérations fenêtre O(1) déclenchées uniquement au changement de route. Aucun accès réseau, Room, ni credential concerné.

---

# 8. Architecture

## 8.1 Solution proposée

Découpler le plein écran système (aujourd'hui figé par le thème) et le rendre **piloté dynamiquement par le contexte de navigation**, à partir de l'Activity unique.

1. **Thème** — passer le parent de `...NoActionBar.Fullscreen` à `...NoActionBar`. La barre d'état redevient donc affichable par défaut ; le plein écran n'est plus une contrainte de thème mais une décision runtime.

2. **Edge-to-edge** — dans `MainActivity.onCreate`, appeler `WindowCompat.setDecorFitsSystemWindows(window, false)`. Le contenu Compose est alors responsable des insets (via `Scaffold` + modificateurs `statusBarsPadding()`/`systemBarsPadding()`), ce qui permet un fond sombre continu sous une barre d'état transparente.

3. **Contrôleur de barres système** — un effet Compose réutilisable (`SystemBarsController`) obtient le `WindowInsetsControllerCompat` depuis `LocalView` et applique, dans un `LaunchedEffect` keyé sur `(isTv, isPlayerRoute)` :
   - `isTv || isPlayerRoute` → `hide(systemBars())` + `systemBarsBehavior = BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` (immersif).
   - sinon (navigation mobile) → `show(systemBars())`, icônes système claires (`isAppearanceLightStatusBars = false`, thème sombre).

   `isPlayerRoute = currentRoute in { live_player, vod_player, series_player }` — la valeur `currentRoute` est déjà calculée dans `MainActivity` (réutilisée depuis le bloc `showBottomBar`).

4. **Zones sûres mobile** — le `Scaffold` (déjà en place) applique les insets de barres système à son contenu et à sa `bottomBar`. Les en-têtes des écrans qui dessinent au bord haut ajoutent `statusBarsPadding()` là où les `paddingValues` ne suffisent pas. Les lecteurs, eux, **ignorent** volontairement les insets pour remplir l'écran (y compris derrière le poinçon en paysage grâce au cutout mode `shortEdges`).

## 8.2 Flux de contrôle

```
onCreate
  └─ setDecorFitsSystemWindows(false)          // edge-to-edge

Recomposition (à chaque navigation / rotation)
  ├─ currentRoute  ← navController backstack
  ├─ isPlayerRoute ← currentRoute ∈ {live_player, vod_player, series_player}
  └─ SystemBarsController(isTv, isPlayerRoute)
        LaunchedEffect(isTv, isPlayerRoute):
          barres masquées  si  isTv || isPlayerRoute   (immersif)
          barres visibles  sinon                        (zones sûres)
```

Le keying sur `(isTv, isPlayerRoute)` garantit la réapplication après retour d'arrière-plan, rotation ou verrouillage/déverrouillage — répondant aux cas limites §4 (état des barres jamais incohérent).

## 8.3 Responsabilités

- **`MainActivity`** : activer l'edge-to-edge, dériver `isPlayerRoute`, monter `SystemBarsController`. Ne contient aucune logique métier.
- **`SystemBarsController`** : seule surface qui touche `WindowInsetsControllerCompat` ; source unique de vérité de la visibilité des barres.
- **`styles.xml`** : neutralité — fournit un thème non-plein-écran + cutout `shortEdges`, sans décider de la visibilité runtime.
- **Écrans/TopBars mobile** : consommer les insets (padding) ; les lecteurs les ignorer.

## 8.4 Décisions techniques justifiées

- **Un seul thème modifié plutôt que deux thèmes (mobile vs TV)** : le masquage TV est déjà couvert au runtime par la branche `isTv` du contrôleur ; dupliquer le thème par qualificateur de ressource ajouterait de la surface de maintenance sans gain (TV n'a pas de barre d'état physique). Réversible si une divergence TV apparaît.
- **`WindowInsetsControllerCompat` (androidx.core) plutôt que flags manuels** : gère nativement le repli min SDK 21 → 30+, évite le code déprécié `SYSTEM_UI_FLAG_*`.
- **Pilotage par route plutôt que par un flag applicatif partagé** : `currentRoute` est déjà la source de vérité de `showBottomBar` ; réutiliser le même signal évite un second état à synchroniser et garantit la cohérence barre système ↔ barre de navigation basse.
- **Cutout `shortEdges` global** : autorise la vidéo à s'étendre derrière le poinçon en lecture (décision §6) ; en navigation, les insets de padding empêchent tout contrôle de passer sous la découpe → un seul réglage sert les deux besoins.

## 8.5 Risques techniques

- **Régression TV** (barres réapparaissant) : mitigée par la branche `isTv` forçant le masquage ; à valider explicitement sur cible TV.
- **En-têtes passant sous la barre d'état** après retrait de `.Fullscreen` : chaque écran du périmètre doit être audité pour l'application effective des `paddingValues`/`statusBarsPadding()` (tâche dédiée à l'étape 4).
- **Fond de barre d'état incohérent** selon écrans (dégradé `mobileBackground` vs fonds opaques) : lisibilité des icônes système à vérifier (contraste), éventuellement via `isAppearanceLightStatusBars`.
- **Pas de test instrumenté UI** (cf. AGENTS.md) : validation visuelle manuelle mobile + TV requise ; couverture unitaire limitée à la logique `isPlayerRoute` si extraite en fonction pure testable.

---

# 9. Plan de développement

## Task 1 — Rendre le thème compatible avec une barre système pilotée au runtime

Objectif :
Retirer le plein écran imposé par le thème afin que la visibilité des barres système soit décidée par l'Activity, et autoriser l'affichage edge-to-edge derrière une découpe uniquement lorsque le contrôleur le demande.

Fichiers :
- `app/src/main/res/values/styles.xml`

Validation :
- Le thème ne dérive plus de `android:Theme.Material.NoActionBar.Fullscreen`.
- `android:windowLayoutInDisplayCutoutMode="shortEdges"` est déclaré de façon compatible avec les API qui le supportent.
- Aucun thème ou manifeste spécifique TV n'est ajouté.

## Task 2 — Centraliser le contrôle dynamique des barres système

Objectif :
Créer le composable `SystemBarsController` qui applique l'état immersif à la fenêtre : toujours masqué sur TV, masqué uniquement pour les trois routes de lecteur sur mobile, visible pour toute autre navigation mobile.

Fichiers :
- `app/src/main/java/com/cstv/app/presentation/theme/SystemBarsController.kt` (nouveau)
- `app/src/main/java/com/cstv/app/MainActivity.kt`

Validation :
- `WindowCompat.setDecorFitsSystemWindows(window, false)` est appelé une seule fois à l'initialisation de l'Activity.
- Le contrôleur utilise `WindowInsetsControllerCompat`, des icônes claires sur fond sombre et `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` en mode immersif.
- Les seules routes mobile immersives sont `live_player`, `vod_player` et `series_player` ; toute autre route réaffiche les barres système.
- Un retour arrière, une rotation ou un retour au premier plan réapplique l'état correspondant à la route courante, sans modifier le comportement TV.

## Task 3 — Appliquer et vérifier les zones sûres de navigation mobile

Objectif :
Faire respecter les insets de barres système à toutes les surfaces de navigation mobile, sans appliquer de padding aux lecteurs et sans perturber le focus Android TV.

Fichiers :
- `app/src/main/java/com/cstv/app/MainActivity.kt`
- `app/src/main/java/com/cstv/app/presentation/navigation/NavGraph.kt`
- `app/src/main/java/com/cstv/app/presentation/login/SplashScreen.kt`
- `app/src/main/java/com/cstv/app/presentation/login/LoginScreen.kt`
- `app/src/main/java/com/cstv/app/presentation/profile/ProfileSelectionScreen.kt`
- `app/src/main/java/com/cstv/app/presentation/profile/ProfileManagementScreen.kt`
- Écrans mobile du graphe qui ne consommeraient pas effectivement les `paddingValues` du `Scaffold`, identifiés pendant l'audit ciblé.

Validation :
- Le `Scaffold` et le `NavHost` transmettent les insets haut/bas aux écrans de navigation, à la navigation basse et à leurs contenus défilants.
- Le splash et les parcours de sélection/gestion de profil, rendus hors du `Scaffold`, respectent aussi les zones sûres sur mobile.
- Les lecteurs `PlayerScreen`, `VodPlayerScreen` et `SeriesPlayerScreen` ne reçoivent pas de padding d'insets qui réduirait la vidéo plein écran.
- Aucun changement de layout, de focus ou de navigation n'est appliqué sur TV.

## Task 4 — Couvrir la décision de route par un test unitaire ciblé

Objectif :
Extraire si nécessaire la décision pure « route immersive ou non » afin de verrouiller le périmètre des trois lecteurs sans dépendre d'un test UI instrumenté.

Fichiers :
- `app/src/main/java/com/cstv/app/presentation/theme/SystemBarsController.kt` ou fichier de helper dédié
- `app/src/test/java/com/cstv/app/presentation/theme/SystemBarsControllerTest.kt` (nouveau)

Validation :
- Les routes `live_player`, `vod_player` et `series_player` sont reconnues comme immersives.
- Les routes de connexion, profil, accueil, catalogues, détails, recherche, favoris, paramètres, téléchargements et gestion de catégories ne le sont pas.
- `./gradlew testDebugUnitTest` passe.

## Task 5 — Valider visuellement les appareils mobile et TV

Objectif :
Vérifier les comportements système qui ne peuvent pas être couverts par les tests unitaires : barres, découpe, rotation et focus.

Fichiers :
- Aucun fichier de production supplémentaire ; consignation des résultats dans ce ticket, section validation.

Validation :
- `./gradlew assembleDebug`, `./gradlew testDebugUnitTest` et `./gradlew lintDebug` passent.
- Sur mobile avec barre gestuelle ou boutons, les barres sont visibles en navigation et masquées dans chacun des trois lecteurs ; elles reviennent immédiatement à la sortie.
- Sur mobile à encoche/poinçon, aucun contrôle de navigation, de connexion ou de profil n'est masqué ; la vidéo peut s'étendre derrière la découpe en paysage.
- Après rotation, arrière-plan/premier plan et verrouillage/déverrouillage, l'état des barres correspond à l'écran visible.
- Sur Android TV, les barres restent masquées et le focus/navigation existants sont inchangés.
