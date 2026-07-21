# B5 - Temps de chargement et loader lors du retour sur l'onglet Films depuis l'Accueil

## Informations générales

Type:
Bug

Status:
TASK BREAKDOWN

Created:
2026-07-21

Target version:
v1.48.32

---

# 1. Description

Lorsque l'utilisateur se trouve sur l'onglet **Films** (VOD), bascule sur l'onglet **Accueil** (Home) dans la barre de navigation inférieure mobile, puis revient sur l'onglet **Films**, un court temps de chargement accompagné d'un indicateur de progression (loader) s'affiche.

Normalement, ce retour devrait être instantané et l'état de l'écran (catégorie sélectionnée, liste de films, position de défilement) devrait être intégralement préservé.

Ce comportement de rechargement se produit uniquement lors du passage par l'onglet **Accueil**. Le basculement direct entre tous les autres onglets (ex: TV <-> Films <-> Séries <-> Recherche) est parfaitement instantané et préserve correctement l'état.

---

# 2. Contexte

L'application utilise Jetpack Compose Navigation (`navController` et `AppNavGraph`) pour gérer les transitions d'écrans de la barre de navigation inférieure.

Pour préserver l'état des écrans (catégorie sélectionnée, listes chargées, scroll, etc.) lors du changement d'onglet, l'application s'appuie sur le comportement standard de restauration d'état de Compose Navigation :
- `saveState = true` lors du pop de l'ancienne destination.
- `restoreState = true` lors de la navigation vers la nouvelle destination.

Cependant, dans `MainActivity.kt`, l'événement de clic sur la barre de navigation inférieure gère l'onglet **Accueil** de manière dérogatoire :
```kotlin
if (tab == MobileTab.HOME) {
    navController.navigate(tab.route) {
        popUpTo("home") {
            inclusive = false
        }
        launchSingleTop = true
    }
} else { ... }
```

En raison de cette exception :
1. Lorsqu'on quitte l'onglet **Films** en cliquant sur l'onglet **Accueil**, la navigation n'indique pas à Compose de sauvegarder l'état de la destination courante (`saveState = true` est manquant sur l'action de pop).
2. L'état de l'onglet **Films** (dont son `VodViewModel` associé) est alors intégralement détruit.
3. Lors du retour ultérieur sur l'onglet **Films**, comme l'état n'a pas été sauvegardé, le `VodViewModel` est instancié à nouveau. Son bloc `init` déclenche la méthode `loadCategories()`, ce qui fait passer l'interface par un état de chargement (`isLoadingCategories = true`), affichant brièvement le loader graphique pendant que les données sont rechargées depuis la base de données Room locale.

---

# 3. Objectif

1. **Restauration d'état instantanée depuis l'Accueil :**
   - Éliminer le temps de chargement et l'affichage du loader lors du retour sur l'onglet **Films** (ou tout autre onglet comme **Séries**) après être passé par l'onglet **Accueil**.
   - Garantir que l'écran se réaffiche instantanément dans l'état exact où l'utilisateur l'avait laissé (même catégorie sélectionnée, même position de scroll).

2. **Unification du comportement de navigation :**
   - Supprimer le comportement d'exception pour l'onglet Accueil dans la barre de navigation de `MainActivity.kt`.
   - Appliquer le pattern de navigation standardisé et recommandé par Google pour Jetpack Compose Navigation sur l'ensemble des onglets (y compris l'onglet Accueil) afin d'assurer une sauvegarde et une restauration d'état symétriques et fluides.

---

# 4. Hypothèses

- **Origine technique de la régression :** Le rechargement est causé uniquement par l'absence des options `saveState = true` et `restoreState = true` lors de la navigation vers l'onglet Accueil dans `MainActivity.kt`.
- **Portée de la correction :** Corriger ce point dans `MainActivity.kt` résoudra instantanément et proprement le problème pour l'onglet **Films**, mais également pour l'onglet **Séries** (`SeriesScreen`), qui souffrait du même symptôme de destruction d'état lors du passage par la Home.
- **Absence d'effet secondaire :** L'alignement sur le standard officiel de navigation de Google est sans risque et s'intègre parfaitement avec l'architecture MVVM et DI de l'application.

---

# 5. Questions ouvertes

1. **Pourquoi l'exception `if (tab == MobileTab.HOME)` a-t-elle été codée ainsi ?**
   - *Analyse :* C'était probablement une tentative maladroite d'implémenter un comportement de "retour à la racine" de l'application sans accumuler de pile d'écrans, tout en ignorant le fait que `navController.graph.findStartDestination().id` résout déjà dynamiquement vers la route `"home"` (ou `"login"` si non connecté) et que le bloc standard gère cela de manière robuste tout en préservant les états.

2. **Y a-t-il d'autres cas où l'état des ViewModels est détruit ?**
   - *Analyse :* Non, la navigation entre TV, Films, Séries et Recherche fonctionne déjà parfaitement grâce à la branche `else` de `MainActivity.kt` qui utilise correctement `saveState = true` et `restoreState = true`. L'alignement de l'onglet Accueil viendra parfaire l'expérience utilisateur globale.

---

# 6. Spécification fonctionnelle

## User stories

- En tant qu'utilisateur mobile, lorsque je quitte Films pour Accueil puis reviens à Films, je retrouve immédiatement Films exactement là où je l'avais laissé.
- En tant qu'utilisateur mobile, lorsque je passe par Accueil, je retrouve de la même manière l'état précédemment consulté des autres onglets de catalogue, notamment Séries.
- En tant qu'utilisateur mobile, je bénéficie du même comportement de conservation d'état quel que soit l'onglet choisi dans la barre de navigation inférieure.

## Parcours utilisateur

1. L'utilisateur ouvre l'onglet **Films**.
2. Il choisit une catégorie et fait défiler la liste de films jusqu'à une position donnée.
3. Il sélectionne l'onglet **Accueil** dans la barre de navigation inférieure.
4. Il sélectionne à nouveau l'onglet **Films**.
5. L'écran Films s'affiche sans écran de chargement transitoire, avec la même catégorie, les mêmes contenus déjà affichés et la position de défilement précédentes.

Le même parcours doit être valide lorsqu'il part de **Séries**, ainsi que pour tout onglet dont l'état est déjà pris en charge par la navigation standard.

## Règles métier et de navigation

- Tous les onglets de la barre de navigation inférieure utilisent une règle de navigation homogène pour préserver et restaurer l'état de leur destination.
- Le passage par Accueil ne doit pas réinitialiser l'état d'un onglet précédemment visité.
- Un second appui sur l'onglet déjà affiché ne doit pas créer une nouvelle instance de l'écran ni provoquer un rechargement inutile.
- Cette correction concerne uniquement la navigation mobile gérée par la barre inférieure ; elle ne modifie ni le catalogue partagé, ni les données de profil, ni le comportement de navigation Android TV.
- Un rechargement reste autorisé si l'utilisateur effectue explicitement une action qui le demande (par exemple une actualisation prévue par l'écran) ou si l'état n'est plus disponible après recréation complète du processus Android.

## Critères d'acceptation

- Après le trajet **Films → Accueil → Films**, aucun loader de catégories ou de films n'apparaît du seul fait de la navigation.
- Après ce même trajet, la catégorie Films sélectionnée est conservée.
- Après ce même trajet, la position de défilement Films est conservée dans la limite de l'état géré par Compose Navigation.
- Après le trajet **Séries → Accueil → Séries**, aucun loader induit par la navigation n'apparaît et l'état de consultation est conservé selon les mêmes règles.
- Les trajets directs existants entre les autres onglets restent instantanés et sans régression visible.
- La navigation vers Accueil reste fonctionnelle depuis chaque onglet, et aucun empilement de destinations identiques n'est créé.

## Cas limites

- Si Films ou Séries n'a encore jamais été ouvert dans la session, son premier affichage conserve son comportement normal de chargement.
- Si l'utilisateur change rapidement plusieurs fois d'onglet, l'application ne doit pas afficher d'état incohérent, d'écran vide persistant ni de destination dupliquée.
- Après recréation du processus par Android, l'application peut recharger les données nécessaires ; B5 garantit la conservation d'état pendant la navigation normale au sein d'une même session, pas une persistance supplémentaire après arrêt du processus.
- Si les données du catalogue sont mises à jour pendant que l'utilisateur consulte Accueil, le retour vers Films ou Séries doit rester stable ; toute mise à jour déjà prévue par l'écran peut s'appliquer sans affichage de loader causé uniquement par le passage par Accueil.

## Gestion des erreurs

- B5 n'introduit aucun nouvel appel réseau ni nouveau message d'erreur utilisateur.
- Si un chargement réel est nécessaire (premier accès, données absentes, actualisation explicite ou recréation de processus), les états de chargement et d'erreur existants des écrans concernés restent inchangés.
- Une erreur de chargement réelle ne doit jamais être masquée par le mécanisme de restauration d'état ; elle doit continuer à être présentée selon le comportement actuel de l'écran.

---

# 7. Spécification technique

## Composants impactés

### `MainActivity.kt`

Le gestionnaire `onClick` des éléments de la barre de navigation mobile est l'unique composant de production à modifier.

La branche spéciale suivante doit être supprimée :

```kotlin
if (tab == MobileTab.HOME) {
    navController.navigate(tab.route) {
        popUpTo("home") {
            inclusive = false
        }
        launchSingleTop = true
    }
} else {
    // Navigation standard actuelle
}
```

Tous les onglets, y compris `MobileTab.HOME`, doivent utiliser une seule navigation :

```kotlin
navController.navigate(tab.route) {
    popUpTo(navController.graph.findStartDestination().id) {
        saveState = true
    }
    launchSingleTop = true
    restoreState = true
}
```

Rôle de chaque option :

- `popUpTo(findStartDestination().id)` ramène la pile au point de départ du graphe et évite l'accumulation des destinations principales ; pour une session authentifiée, le `NavHost` est créé avec `home` comme destination initiale.
- `saveState = true` sauvegarde l'état des destinations retirées de la pile, notamment l'entrée de navigation et le `ViewModelStore` associé à Films ou Séries.
- `launchSingleTop = true` évite de créer une seconde instance lorsque la destination demandée est déjà au sommet.
- `restoreState = true` restaure l'état précédemment sauvegardé lorsque l'utilisateur revient sur un onglet.

### `AppNavGraph.kt`

Aucune modification n'est prévue. Les routes principales (`home`, `tv`, `movies`, `series`, `search`) existent déjà et les actions de navigation depuis l'Accueil appliquent déjà le même triplet `saveState` / `launchSingleTop` / `restoreState`.

### ViewModels et écrans

Aucune modification n'est prévue dans `VodViewModel`, `SeriesViewModel`, `VodScreen` ou `SeriesScreen`. Leur cycle de vie doit être conservé par la pile de navigation au lieu d'être compensé par un cache ou par une suppression artificielle du loader.

## Données, réseau et persistance

- Aucun modèle de données n'est ajouté ou modifié.
- Aucun appel Xtream Codes ou TMDB n'est ajouté.
- Aucun changement Room, migration de base de données ou modification DataStore n'est nécessaire.
- Aucun changement de dépendance Gradle ou de règle R8/ProGuard n'est nécessaire.

## Compatibilité et périmètre plateforme

- La correction s'applique uniquement à la barre de navigation inférieure mobile, masquée sur Android TV par `showBottomBar`.
- Le graphe de navigation partagé reste inchangé ; les parcours TV, login, profil, lecteurs plein écran et écrans de détail conservent leur comportement actuel.
- La correction utilise les API déjà disponibles dans `androidx.navigation:navigation-compose:2.7.7` et reste compatible avec le min SDK 21 du projet.

## Stratégie de validation

L'absence actuelle d'infrastructure `androidTest`, de Robolectric et de `navigation-testing` ne permet pas de reproduire fidèlement, dans un test unitaire local, le cycle de vie d'un `NavHostController` Compose et de ses ViewModels. B5 n'ajoutera pas une nouvelle infrastructure instrumentée pour ce correctif localisé.

La validation devra comprendre :

- vérification manuelle sur mobile des parcours **Films → Accueil → Films** et **Séries → Accueil → Séries**, avec catégorie et position de défilement non initiales ;
- vérification d'appuis rapides et répétés sur les onglets afin de détecter les destinations dupliquées ou les écrans vides ;
- vérification de non-régression des navigations directes entre TV, Films, Séries et Recherche ;
- exécution de `testDebugUnitTest`, `assembleDebug` et `lintDebug` pour couvrir la non-régression globale et la compilation de la DSL Navigation.

## Risques techniques

### Restauration d'une sous-destination

Si l'utilisateur quitte un onglet depuis un écran de détail, Compose Navigation peut restaurer la pile sauvegardée de cet onglet et donc sa sous-destination, selon l'état courant de la pile. Ce comportement est cohérent avec la restauration d'état demandée, mais il devra être vérifié manuellement pour `vod_details` et `series_details` afin de confirmer qu'il reste conforme à l'expérience existante.

### Destination initiale conditionnelle

Le `NavHost` choisit `login` ou `home` comme destination initiale selon l'état d'authentification au moment de sa création. La correction ne doit pas remplacer `findStartDestination()` par une route codée en dur : conserver la résolution dynamique protège le parcours de connexion et évite de coupler la barre mobile à une hypothèse sur la pile.

### Limite de restauration

`saveState` / `restoreState` garantit la restauration des piles d'onglets dans le cadre pris en charge par Navigation Compose. Il ne remplace pas une persistance métier après arrêt forcé ou destruction complète du processus Android ; cette limite reste celle définie dans la spécification fonctionnelle.

## Contraintes de performance

- La correction ne doit déclencher aucun nouvel accès réseau, Room ou calcul de catalogue lors d'un simple retour sur un onglet sauvegardé.
- La mémoire conservée correspond aux piles et ViewModels déjà pris en charge par Navigation Compose pour les autres onglets ; aucun cache applicatif supplémentaire n'est créé.
- Le retour sur Films ou Séries doit réutiliser l'état en mémoire sans repasser par l'état initial de chargement du ViewModel.

---

# 8. Architecture

## Décision

La conservation des onglets reste sous la responsabilité de Jetpack Compose Navigation. B5 aligne Accueil sur le mécanisme multi-piles déjà en place au lieu d'introduire une gestion d'état parallèle dans les écrans ou les ViewModels.

## Flux avant correction

```text
Films / Séries
    ↓ clic Accueil
popUpTo("home") sans saveState
    ↓
destruction de l'entrée et du ViewModel de l'onglet
    ↓ retour sur l'onglet
nouvelle instance du ViewModel → chargement initial → loader
```

## Flux après correction

```text
Films / Séries
    ↓ clic Accueil
popUpTo(destination initiale) avec saveState
    ↓
pile et état de l'onglet sauvegardés
    ↓ retour sur l'onglet avec restoreState
restauration de la pile et du ViewModel → affichage immédiat de l'état précédent
```

## Responsabilités

- `MainActivity` applique une politique uniforme lors des clics sur la barre mobile.
- `NavHostController` sauvegarde, sélectionne et restaure les piles de navigation.
- `AppNavGraph` continue de déclarer les routes et d'associer les ViewModels aux destinations.
- `VodViewModel` et `SeriesViewModel` continuent de charger les données uniquement lorsqu'une véritable nouvelle instance est nécessaire.

## Fichiers prévus pour l'implémentation

- `app/src/main/java/com/cstv/app/MainActivity.kt` : suppression de la branche spéciale Accueil et utilisation du bloc de navigation commun.
- `ai/bugs/B5-movies-reload-transition-home.md` : suivi du cycle de vie, des tâches, de la validation et de la livraison.

Aucun nouveau fichier de production, test, ressource ou configuration n'est prévu à cette étape.

## Dépendances

Aucune dépendance nouvelle. La solution repose exclusivement sur Navigation Compose déjà présent dans le projet.

---

# 9. Plan de développement

- [ ] Tâche 1 — Unifier la navigation de la barre mobile

Objectif :
Supprimer la dérogation appliquée à `MobileTab.HOME` afin que tous les onglets de la barre inférieure utilisent la même politique de sauvegarde et restauration d'état.

Fichiers :

- `app/src/main/java/com/cstv/app/MainActivity.kt`

Implémentation attendue :

- remplacer le bloc conditionnel `if (tab == MobileTab.HOME) { ... } else { ... }` par l'unique appel `navigate(tab.route)` documenté dans la spécification technique ;
- conserver `popUpTo(navController.graph.findStartDestination().id) { saveState = true }`, `launchSingleTop = true` et `restoreState = true` ;
- ne modifier ni les routes, ni les ViewModels, ni `AppNavGraph`.

Validation :

- le bloc `onClick` ne contient plus de traitement distinct pour Accueil ;
- le projet compile avec `./gradlew assembleDebug` ;
- le lint ne relève aucune erreur avec `./gradlew lintDebug`.

- [ ] Tâche 2 — Vérifier la restauration d'état de la navigation mobile

Objectif :
Confirmer sur un appareil ou émulateur mobile que le changement supprime le rechargement induit par le passage par Accueil sans introduire de régression de navigation.

Fichiers :

- Aucun fichier de production supplémentaire.
- `ai/bugs/B5-movies-reload-transition-home.md` : consigner le résultat de validation dans les notes de développement et les étapes ultérieures du workflow.

Parcours à vérifier :

1. Ouvrir Films, choisir une catégorie, faire défiler la liste, puis effectuer **Films → Accueil → Films**.
2. Ouvrir Séries, choisir une catégorie, faire défiler la liste, puis effectuer **Séries → Accueil → Séries**.
3. Tester les bascules rapides entre Accueil, TV, Films, Séries et Recherche.
4. Depuis `vod_details` et `series_details`, passer par Accueil puis revenir à l'onglet concerné afin de vérifier le comportement de restauration de la sous-pile.

Validation :

- aucun loader de catégories ou de contenu ne s'affiche uniquement en raison du passage par Accueil ;
- catégorie et position de défilement sont restaurées pour Films et Séries ;
- aucune destination principale dupliquée, écran vide ou crash n'apparaît ;
- les parcours existants depuis Accueil et vers les lecteurs restent fonctionnels.

- [ ] Tâche 3 — Exécuter la non-régression automatisée

Objectif :
Vérifier que la modification de la DSL Navigation ne casse ni la compilation ni les tests unitaires existants.

Fichiers :

- Aucun fichier supplémentaire, sauf correction strictement nécessaire révélée par les outils de validation.

Validation :

- `./gradlew testDebugUnitTest` réussit ;
- `./gradlew assembleDebug` réussit ;
- `./gradlew lintDebug` réussit ;
- tout échec introduit par B5 est corrigé avant la review.

## Ordre d'exécution

1. Tâche 1 : correction localisée.
2. Tâche 2 : validation fonctionnelle mobile après compilation.
3. Tâche 3 : validation automatisée finale.

Les tâches 2 et 3 ne modifient pas l'architecture définie ; elles confirment la correction et sa non-régression.
