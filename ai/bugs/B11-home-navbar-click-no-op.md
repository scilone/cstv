# B11 - Clic sur Accueil dans la barre de navigation sans effet depuis les fiches de détail

## Informations générales

Type:
Bug

Status:
TASK BREAKDOWN

Created:
2026-07-25

Target version:
v1.54.18

Version:
v1.54.18

Date:
2026-07-25

---

# 1. Description

Sur mobile, lorsque l'utilisateur navigue sur l'Accueil (Home), clique sur un film ou une série pour ouvrir sa fiche détaillée, puis clique sur l'icône de l'onglet **Accueil** dans la barre de navigation inférieure mobile pour revenir en arrière, rien ne se passe. L'utilisateur reste bloqué sur la fiche détaillée du média et le clic sur l'onglet Accueil est totalement sans effet.

---

# 2. Contexte

L'application utilise Jetpack Compose Navigation pour gérer la navigation mobile. Dans `MainActivity.kt`, la barre de navigation inférieure (`NavigationBar`) est affichée uniquement pour les utilisateurs mobiles connectés.

Dans le gestionnaire de clic `onClick` de chaque élément de la barre, le comportement standardisé suivant est appliqué pour éviter l'accumulation d'écrans dans la backstack et restaurer l'état des onglets :
```kotlin
navController.navigate(tab.route) {
    popUpTo(navController.graph.findStartDestination().id) {
        saveState = true
    }
    launchSingleTop = true
    restoreState = true
}
```

Cependant, il existe une particularité majeure lors du premier lancement de l'application :
1. Au démarrage, `loggedInUser` est initialisé à `null`. Le `NavHost` se compose donc une première fois avec `startDestination` fixé à `"login"`.
2. Même si l'auto-login réussit ou que l'utilisateur se connecte manuellement, le point de départ statique (`startDestination`) de la structure du graphe de navigation reste définitivement latché sur `"login"`.
3. Lorsque l'utilisateur est connecté et sur l'Accueil, l'écran de login est retiré de la pile de retour de manière inclusive via :
   ```kotlin
   navController.navigate("home") {
       popUpTo("login") { inclusive = true }
   }
   ```
4. Dès lors, `navController.graph.findStartDestination().id` continue d'indiquer l'identifiant du nœud `"login"`, alors que celui-ci a été entièrement purgé de la backstack !
5. Ainsi, lors du clic sur l'onglet Accueil depuis un écran de détail (ex: `vod_details`), l'instruction `popUpTo("login")` échoue car `"login"` n'existe plus dans la pile. Aucun écran n'est dépilé. `"vod_details"` reste actif au sommet, et comme `"home"` est déjà présent plus bas dans la pile et que `launchSingleTop = true` est spécifié, l'action est tout simplement ignorée.

---

# 3. Objectif

Permettre à l'utilisateur de revenir instantanément à la racine de l'Accueil en cliquant sur l'onglet **Accueil** de la barre inférieure mobile lorsqu'il se trouve sur un écran de détail (fiche VOD ou fiche série), en dépilant correctement les écrans superposés.

---

# 4. Hypothèses

- **Rupture causée par le point de départ "login" :** Le fait que `findStartDestination()` résout vers `"login"` (absent de la pile) au lieu de `"home"` (racine réelle de la session connectée) est l'unique cause de ce dysfonctionnement de dépilage.
- **Résolution par substitution de racine :** Remplacer le ciblage dynamique de `findStartDestination().id` par la route `"home"` (ou intercepter dynamiquement si la racine résout vers `"login"` pour forcer `"home"` lorsque l'utilisateur est connecté) dans le comportement du clic de la barre de navigation inférieure mobile résoudra proprement le bug.
- **Absence d'effets de bord :** La correction n'affectera en rien le comportement de navigation sur Android TV (qui n'affiche pas la barre inférieure) ni le flux de login initial.

---

# 5. Questions ouvertes

1. **Impact sur les autres onglets :** Ce problème de résolution de `findStartDestination()` vers `"login"` affecte-t-il également la navigation vers les autres onglets (TV, Films, Séries, Recherche) lorsqu'on clique dessus depuis une fiche de détail ? Si oui, le fait de purger la pile jusqu'à `"home"` au lieu d'un `"login"` absent corrigera-t-il également des fuites de backstack ou des comportements anormaux sur ces onglets ?
2. **Réinitialisation des variables d'état de détail :** Lorsque l'utilisateur revient à l'Accueil par le clic de la barre de navigation, doit-on réinitialiser explicitement les états `activeVodMovie` et `activeSeriesShow` à `null` dans `MainActivity` pour nettoyer proprement la mémoire de l'écran quitté ?

---

# 6. Spécification fonctionnelle

## User stories

- En tant qu'utilisateur mobile consultant une fiche de film ou de série, je veux toucher Accueil dans la barre inférieure pour revenir immédiatement à l'Accueil.
- En tant qu'utilisateur mobile, je veux que chaque onglet de la barre inférieure ouvre sa racine, quel que soit l'écran secondaire depuis lequel je viens.

## Parcours utilisateur

1. L'utilisateur connecté ouvre l'Accueil puis une fiche VOD ou série.
2. Il touche l'onglet Accueil de la barre de navigation inférieure.
3. La fiche est quittée et l'écran Accueil devient l'écran visible, sans nécessiter le bouton Retour.
4. L'utilisateur touche ensuite un autre onglet ; la racine de cet onglet est ouverte normalement.

## Règles métier

- Le comportement concerne uniquement la navigation mobile affichant la barre inférieure ; la navigation Android TV reste inchangée.
- Un clic sur un onglet doit toujours ramener à la racine de l'onglet demandé depuis une fiche de détail ou tout autre écran secondaire accessible avec la barre inférieure.
- La pile ne doit pas conserver la fiche quittée au-dessus de la racine sélectionnée : le bouton Retour depuis l'Accueil ne doit pas réafficher la fiche précédemment quittée.
- Le comportement existant de restauration d'état des onglets est conservé lorsque cela est compatible avec le retour à leur racine ; un second clic sur l'onglet déjà visible ne doit pas créer de doublon.
- Les flux de connexion, déconnexion et l'écran de connexion ne sont pas modifiés par ce correctif.

## Critères d'acceptation

- Depuis une fiche VOD ouverte depuis l'Accueil, toucher Accueil affiche l'Accueil immédiatement.
- Depuis une fiche série ouverte depuis l'Accueil, toucher Accueil affiche l'Accueil immédiatement.
- Après chacun de ces retours, le bouton Retour ne réouvre pas la fiche quittée.
- Depuis une fiche VOD ou série, toucher TV, Films, Séries ou Recherche ouvre la racine de l'onglet choisi et ne laisse pas la fiche visible ou active au premier plan.
- Les actions précédentes fonctionnent après une connexion manuelle comme après une reconnexion automatique.
- Sur Android TV, la navigation actuelle reste inchangée.

## Cas limites et gestion des erreurs

- Si la route demandée est déjà la route visible, l'action est idempotente : pas de nouvel écran, de clignotement ni de doublon dans la pile.
- Si l'état d'un onglet ne peut pas être restauré, sa racine est affichée plutôt qu'un écran de détail obsolète ou une interface inactive.
- Une navigation interrompue par une déconnexion revient au flux de connexion existant sans afficher d'erreur brute.

---

# 7. Spécification technique

## 7.1 Révision du diagnostic (hypothèse §4 partiellement invalidée)

L'hypothèse « `findStartDestination()` reste latché sur `login` » n'est **pas confirmée en l'état du code**. Deux mécanismes en place la contredisent :

1. `MainActivity.kt:144-145` maintient déjà le `SplashScreen` tant que `autoLoginState is Success && loggedInUser == null`. Le `NavHost` n'est donc jamais composé avec `startDestination = "login"` sur le chemin auto-login — le commentaire du code documente explicitement ce garde-fou.
2. Sur le chemin login manuel, `NavGraph.kt:98` recompose le `NavHost` avec `startDestination = "home"` dès que `loggedInUser` devient non nul. `NavController.setGraph()` détecte un graphe différent (`NavGraph.equals()` compare les nœuds et la start destination), dépile l'intégralité de l'ancien graphe puis repositionne la pile sur la nouvelle start destination. `findStartDestination()` résout alors vers `home`, pas vers `login`.

La cause exacte n'est donc **pas prouvable par lecture statique** : elle dépend du comportement runtime de `navigation-compose` (recréation du graphe à chaque recomposition — le lambda `builder` de `remember(route, startDestination, builder)` change d'instance à chaque passage) et de l'état réel de la pile au moment du clic.

Conséquences pour l'étape 5 :

- Le correctif retenu ci-dessous **supprime la dépendance à `findStartDestination()`** et est donc correct quelle que soit la valeur réellement latchée. Il traite le symptôme de manière déterministe sans dépendre de la validation de l'hypothèse.
- Une **vérification runtime est obligatoire** avant de clore (voir §7.6) : instrumenter temporairement le `onClick` avec `IptvLog.d("NAV", navController.currentBackStack.value.joinToString { it.destination.route ?: "?" })` afin de confirmer la pile réelle avant/après clic. Si la pile réelle montre que `home` n'est pas dans la pile depuis `vod_details`, la cause est différente et l'analyse doit être reprise.
- Cause secondaire candidate à écarter pendant cette vérification : la combinaison `saveState = true` / `restoreState = true` peut restaurer l'état sauvegardé d'un onglet au lieu de sa racine (cas explicitement couvert par les critères d'acceptation « sa racine est affichée plutôt qu'un écran de détail obsolète »).

## 7.2 Composants impactés

| Fichier | Nature de la modification |
|---|---|
| `presentation/navigation/MobileNavigation.kt` | **Nouveau** — objet pur : route racine de session, extension `NavController.navigateToRootTab()`, helper `isTabSelected()` |
| `MainActivity.kt:216-222` | Remplace le bloc `navigate { popUpTo(findStartDestination().id) ... }` par `navController.navigateToRootTab(tab.route)` |
| `MainActivity.kt:210-212` | Remplace le calcul inline de `selected` par `MobileNavigation.isTabSelected(currentRoute, tab.route)` (rend la logique testable unitairement) |
| `NavGraph.kt:124-128, 131-135, 138-142, 151-155` | Mêmes 4 occurrences de `popUpTo(findStartDestination().id)` sur les raccourcis Home → TV / Films / Séries / Recherche : remplacées par `navigateToRootTab` |
| `test/.../MobileNavigationTest.kt` | **Nouveau** — tests unitaires du helper pur |

Aucune dépendance nouvelle. Aucun changement Room, réseau, DI ou ProGuard.

## 7.3 Nouveaux composants

`app/src/main/java/com/cstv/app/presentation/navigation/MobileNavigation.kt` :

```kotlin
object MobileNavigation {
    /**
     * Racine réelle d'une session connectée. Volontairement codée en dur plutôt
     * que résolue via navController.graph.findStartDestination() : la start
     * destination du graphe vaut "login" tant que loggedInUser est nul, et
     * "login" est purgé de la pile (popUpTo inclusive) dès la connexion. Un
     * popUpTo ciblant une destination absente de la pile échoue silencieusement
     * et ne dépile rien (B11).
     */
    const val ROOT_ROUTE = "home"

    /** Écrans de détail rattachés visuellement à un onglet de la barre inférieure. */
    private val DETAIL_ROUTE_TO_TAB = mapOf(
        "vod_details" to "movies",
        "series_details" to "series"
    )

    fun isTabSelected(currentRoute: String?, tabRoute: String): Boolean =
        currentRoute == tabRoute || DETAIL_ROUTE_TO_TAB[currentRoute] == tabRoute
}

fun NavController.navigateToRootTab(route: String) {
    navigate(route) {
        popUpTo(MobileNavigation.ROOT_ROUTE) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
```

`popUpTo` prend ici une **route** (surcharge `popUpTo(route: String)`) et non un identifiant de nœud : plus lisible et indépendant de la résolution du graphe.

## 7.4 Choix techniques et justifications

- **Route racine constante plutôt que résolution dynamique.** `home` est la seule racine possible d'une session connectée : la barre inférieure n'est affichée que si `loggedInUser != null && currentRoute != "login"` (`MainActivity.kt:183`), et `login` est purgé de la pile dès la connexion (`NavGraph.kt:109`). Une résolution conditionnelle du type « si la racine vaut `login`, forcer `home` » (variante évoquée en §4) ajouterait une branche non testable pour un résultat identique.
- **`popUpTo` non inclusif.** `home` reste dans la pile : la fiche quittée est dépilée (le bouton Retour ne la réaffiche pas) sans détruire la racine, et un second clic sur l'onglet déjà visible ne dépile rien de plus (`launchSingleTop` bloque le doublon) → idempotence exigée par les cas limites.
- **`saveState` / `restoreState` conservés.** Le comportement de restauration d'état des onglets est explicitement conservé par les règles métier. Ils ne sont pas la cause du symptôme principal et les retirer dégraderait l'expérience (perte du scroll des grilles Films/Séries).
- **Extraction de `isTabSelected`.** `navigation-compose` n'est pas testable en test unitaire JVM (pas d'infrastructure `androidTest` sur le projet, cf. AGENTS.md). Extraire la partie *pure* de la logique de navigation est le seul moyen d'obtenir une couverture automatisée non nulle sur ce correctif.
- **Aucune modification du flux TV.** La barre inférieure n'est composée que si `!isTv`. Les 4 occurrences corrigées dans `NavGraph.kt` sont des rappels de `HomeScreen`, partagés TV/mobile : leur comportement passe de « ne dépile rien » à « dépile jusqu'à `home` », ce qui est le comportement attendu sur les deux plateformes (la racine TV est également `home`, cf. `BackHandler` `MainActivity.kt:186`).

## 7.5 Réponses aux questions ouvertes (§5)

1. **Impact sur les autres onglets** — Oui. Les cinq onglets partagent le même bloc `onClick` (`MainActivity.kt:216-222`) et les quatre raccourcis Home (`NavGraph.kt:124-155`) utilisent la même construction. Si `popUpTo` échoue, chaque navigation vers un onglet depuis une fiche empile une entrée supplémentaire au lieu de dépiler → croissance monotone de la backstack et bouton Retour imprévisible. La correction unique traite les six emplacements et supprime cette fuite potentielle.
2. **Réinitialisation de `activeVodMovie` / `activeSeriesShow`** — **Non**, ne pas réinitialiser. Ces états ne sont lus qu'à l'entrée sur les routes de détail, et toujours réécrits avant navigation (`onSelectMovieDetail` / `onSelectSeriesDetail`, `NavGraph.kt:214-221`). Les mettre à `null` au clic sur un onglet ferait recomposer la fiche encore visible pendant la transition avec `activeSeriesShow == null` : le `LaunchedEffect` ne relancerait pas `selectStream`, l'écran afficherait un `CircularProgressIndicator` le temps de l'animation de sortie → clignotement gratuit. Le coût mémoire conservé est celui d'un `SeriesStream`/`VodStream` (métadonnées de liste), négligeable.

## 7.6 Vérification, risques et non-régression

- **Vérification runtime obligatoire** (l'étape 5 ne peut pas être close sans) : depuis `home` → fiche VOD → onglet Accueil ; puis fiche série → onglet Accueil ; puis fiche VOD → onglets TV / Films / Séries / Recherche. Vérifier à chaque fois que la fiche est quittée et que le bouton Retour ne la réaffiche pas. Rejouer la séquence après connexion manuelle **et** après reconnexion automatique (les deux chemins produisent des piles différentes).
- **Risque : `popUpTo("home")` sans effet si `home` est absent de la pile.** Ne peut survenir que si l'utilisateur atteint un écran secondaire sans jamais passer par `home`, ce que le graphe interdit (toutes les routes de détail sont atteintes depuis `home`, `tv`, `movies`, `series`, `search` ou `favorites`, elles-mêmes atteintes depuis `home`). Dégradation en cas contraire : comportement actuel inchangé, pas de régression.
- **Risque : restauration d'état.** Si `restoreState` réaffiche un onglet dans un état obsolète, le cas limite « sa racine est affichée plutôt qu'un écran de détail obsolète » impose de retirer `restoreState`/`saveState` de la correction. À trancher pendant la vérification runtime, pas avant.
- **Non-régression** : `./gradlew assembleDebug lintDebug testDebugUnitTest`. Aucune suite existante ne couvre la navigation ; le seul ajout est `MobileNavigationTest`.
- **Performance** : nulle. Une constante remplace une résolution de graphe par clic.

---

# 8. Architecture

## Flux de données (avant / après)

```
AVANT
NavigationBarItem.onClick
  └─> navController.navigate(tab.route) {
        popUpTo( navController.graph.findStartDestination().id )   ← nœud potentiellement absent de la pile
        launchSingleTop = true ; restoreState = true
      }
      └─> popUpTo sans effet → vod_details reste au sommet
          → home déjà présent + launchSingleTop → navigate ignoré → aucun changement visible

APRÈS
NavigationBarItem.onClick
  └─> navController.navigateToRootTab(tab.route)          (presentation/navigation/MobileNavigation.kt)
        └─> popUpTo(MobileNavigation.ROOT_ROUTE = "home")  ← route toujours présente en session connectée
            saveState = true ; launchSingleTop = true ; restoreState = true
            └─> dépile vod_details (et l'onglet courant si distinct) jusqu'à home inclus-exclu
                → home redevient le sommet, ou l'onglet demandé est empilé sur home
```

## Responsabilités

| Composant | Responsabilité |
|---|---|
| `MobileNavigation` (objet pur) | Détient la racine de session et la correspondance route de détail → onglet. Aucune dépendance Android : testable en JVM. |
| `NavController.navigateToRootTab()` (extension) | Point d'entrée unique de toute navigation « retour à la racine d'un onglet ». Interdit la duplication du bloc d'options de navigation, à l'origine du bug répliqué en six endroits. |
| `MainActivity` | Rend la barre inférieure ; délègue le calcul de l'onglet sélectionné et l'action de navigation. Ne construit plus d'options de navigation. |
| `AppNavGraph` | Déclare les destinations et délègue les raccourcis inter-onglets de `HomeScreen` à la même extension. |

## Décisions techniques

1. **Un seul point de vérité pour la navigation d'onglet.** Le défaut est présent à l'identique dans six blocs copiés. Corriger sur place laisserait le motif fautif disponible pour le prochain écran ajouté. L'extension est le point de passage obligé.
2. **Ne pas toucher au double système de navigation.** AGENTS.md signale la coexistence `AppNavGraph` (mobile) / navigation manuelle `AppScreen` (TV) et son unification prévue au backlog technique. B11 ne l'anticipe pas : périmètre strictement limité au dépilage.
3. **Pas de deep link ni d'argument de route.** Les routes de détail restent sans argument et s'appuient sur l'état hissé dans `MainActivity` ; les transformer en routes paramétrées (`series_details/{id}`) réglerait aussi la classe de problèmes visée mais dépasse largement le périmètre d'un correctif de bug.
4. **Statut de l'hypothèse §4.** Conservée comme piste principale mais explicitement non validée (§7.1) ; le correctif ne dépend pas de sa validation. Toute conclusion définitive sur la cause racine est renvoyée à la vérification runtime de l'étape 5.

---

# 9. Plan de développement

## Ordre d'exécution

La tâche 1 extrait le contrat de navigation testable. Les tâches 2 et 3
migrent ensuite les deux points d'entrée mobiles vers ce contrat. La tâche 4
verrouille le comportement par tests et vérification sur appareil ; aucune ne
modifie la navigation manuelle Android TV.

### Tâche 1 — Créer le contrat de navigation racine mobile

- [ ] Ajouter l'objet de routes et l'extension unique de retour vers un onglet.

Objectif :
Définir la racine de session stable et centraliser les options `popUpTo`,
`saveState`, `launchSingleTop` et `restoreState`, avec une correspondance pure
des routes de détail vers leur onglet.

Fichiers :

- `presentation/navigation/MobileNavigation.kt`
- `app/src/test/java/.../presentation/navigation/MobileNavigationTest.kt`

Validation :

- La racine est présente dans toute session connectée et le contrat est testable
  sans `NavController` Android réel.
- Les routes de détail VOD/Séries sont associées au bon onglet ; les routes
  inconnues ne produisent pas une sélection erronée.
- Aucun code TV ni route paramétrée n'est introduit.

### Tâche 2 — Raccorder la barre de navigation de `MainActivity`

- [ ] Remplacer les blocs de navigation dupliqués de la barre mobile par l'extension.

Objectif :
Faire de l'extension le seul point de passage lors d'un clic sur Accueil, TV,
Films, Séries, Recherche ou Profil, tout en conservant la restauration d'état
et l'onglet visuellement sélectionné.

Fichiers :

- `MainActivity.kt`
- `presentation/navigation/MobileNavigation.kt`

Validation :

- Depuis une fiche VOD ou Série, un clic sur chaque onglet produit une
  destination visible et ne laisse plus la fiche au sommet.
- Les clics répétés ne dupliquent pas les destinations principales.
- Le comportement et les écrans Android TV restent inchangés.

### Tâche 3 — Raccorder les raccourcis inter-onglets de la Home

- [ ] Remplacer les appels de navigation concernés de `AppNavGraph` par la même extension.

Objectif :
Empêcher que les raccourcis Home réintroduisent le bloc fautif ou divergent de
la politique appliquée par la barre inférieure.

Fichiers :

- `presentation/navigation/NavGraph.kt`
- `presentation/navigation/MobileNavigation.kt`

Validation :

- Tous les six points de navigation identifiés utilisent le contrat partagé.
- Les raccourcis Home ouvrent le bon onglet sans empiler de détail résiduel.
- Aucune route, aucun état hissé et aucun ViewModel ne change de responsabilité.

### Tâche 4 — Tester et valider la non-régression de navigation mobile

- [ ] Vérifier les invariants automatisés puis les parcours de reproduction sur cible mobile.

Objectif :
Confirmer la correction du no-op et préserver les piles, restaurations d'état,
clics de cartes et retours existants.

Fichiers :

- tests de navigation ajoutés ou ajustés ;
- `ai/bugs/B11-home-navbar-click-no-op.md` pour consigner les résultats.

Validation :

- `./gradlew testDebugUnitTest`, `./gradlew assembleDebug` et `./gradlew lintDebug` passent.
- Sur mobile : depuis `vod_details` et `series_details`, chaque onglet de la
  barre réagit ; les bascules et clics répétés ne créent ni écran vide ni crash.
- Vérifier explicitement que la navigation TV manuelle est inchangée.
