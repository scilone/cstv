# B19 - Barres système blanches au démarrage (Splash et Sélection de Profil) sur Mobile

## Informations générales

Status:
RELEASED

Created:
2026-08-02

Version:
v1.67.0

Date:
2026-08-02

---

# 1. Description

Sur mobile, lors du démarrage de l'application, l'écran de chargement initial (Splash) et l'écran de sélection de profil affichent des barres d'état (en haut) et de navigation système (en bas) de couleur entièrement blanche.

Cela casse le thème sombre immersif de l'application et nuit à l'expérience utilisateur dès l'ouverture de l'application, alors que tout le reste de l'interface utilise un dégradé sombre et élégant.

---

# 2. Contexte

Après analyse de l'arborescence des Composables dans `MainActivity.kt` :
1. Le composant racine **`Surface`** est créé au tout début de `setContent` :
   ```kotlin
   SystemBarsController(isTv = isTv, isPlayerRoute = false)
   Surface(modifier = Modifier.fillMaxSize()) { ... }
   ```
2. Les écrans intermédiaires (`SplashScreen` et `ProfileSelectionScreen`) sont composés directement dans cette `Surface` lorsqu'ils sont actifs.
3. Le thème de l'application, **`IptvXtreamTheme`**, n'est appliqué que plus tard, uniquement dans le bloc `else` (qui affiche le `NavHost` principal et l'Accueil de l'application).
4. **Le problème** : Comme la `Surface` racine est en dehors de `IptvXtreamTheme`, elle hérite de la configuration par défaut de Compose (qui utilise un schéma de couleurs clair, avec une couleur de surface pure blanche).
5. Sur mobile, `ProfileSelectionScreen` utilise `Modifier.safeDrawingPadding()`, ce qui restreint sa zone d'affichage et de dessin (portant le fond sombre dégradé) à la zone sûre d'affichage, excluant les barres système.
6. Comme les barres système sont transparentes, la couleur de fond de la `Surface` sous-jacente (qui est blanche par défaut) transparaît directement tout en haut et tout en bas de l'écran, créant ces bandes blanches inesthétiques.

---

# 3. Spécification fonctionnelle

## Objectif

S'assurer que sur mobile, les barres système (état et navigation) soient parfaitement intégrées et sombres (ou transparentes sur fond sombre) dès l'ouverture de l'application, couvrant le Splash screen, l'écran de sélection de profil, et l'écran de gestion des profils.

## User stories et parcours utilisateur

* **En tant qu'utilisateur mobile**, dès le lancement, je vois un Splash continu sur fond sombre, sans flash blanc au-dessus ou au-dessous du contenu.
* **En tant qu'utilisateur mobile**, lorsque je choisis, ajoute ou gère un profil, le fond sombre se prolonge derrière les zones système tout en gardant les actions hors des encoches et zones de navigation.

1. L'application s'ouvre : le Splash couvre visuellement toute la fenêtre, y compris derrière les barres système transparentes.
2. La sélection ou la gestion de profil s'affiche : le dégradé sombre reste continu ; les textes et boutons conservent leurs marges sûres.
3. Après sélection du profil, la transition vers la navigation principale ne produit ni bande blanche ni changement brutal de couleur.

## Règles de rendu et d'intégration

1. **Unification du thème** : Le thème sombre de l'application (`IptvXtreamTheme`) doit envelopper la totalité de l'application dans `MainActivity.kt`, et pas seulement le contenu post-sélection de profil.
2. **Couleur de la Surface racine** : La `Surface` racine doit hériter du schéma de couleurs sombres (ou être configurée explicitement avec un fond noir/sombre), évitant ainsi tout flash ou débordement blanc sous les barres système transparentes.
3. **Consommation des insets** : Préserver le comportement de `safeDrawingPadding` pour que les textes et boutons importants ne soient pas coupés par les encoches de l'écran, tout en s'assurant que le fond sombre s'étend sur toute la hauteur physique de l'écran (edge-to-edge).

## Critères d'acceptation (Fonctionnels)

- [ ] Sur mobile, au lancement de l'application (Splash screen), la barre d'état et la barre de navigation sont sombres/transparentes sur fond noir.
- [ ] Sur mobile, lors de l'affichage de l'écran de sélection de profil, la barre d'état et la barre de navigation se fondent parfaitement dans le thème sombre sans bande blanche.
- [ ] Sur mobile, l'écran de gestion des profils (ProfileManagementScreen) hérite également de barres système sombres et intégrées.
- [ ] La lisibilité des icônes et textes système reste adaptée au fond sombre sur les trois écrans.
- [ ] Android TV et les routes lecteur immersives conservent leur comportement actuel.

## Cas limites et gestion des erreurs

- Une rotation, une recréation d'activité ou le retour depuis l'arrière-plan ne doit pas réintroduire de fond clair transitoire.
- Les appareils avec encoche, barre de navigation gestuelle ou trois boutons gardent le même fond sombre derrière leurs insets.
- L'absence temporaire d'un profil ou un chargement prolongé conserve le rendu Splash sombre ; aucun état d'erreur ne doit exposer la surface Compose par défaut.

---

# 4. Spécification technique

## Diagnostic technique confirmé (lecture du code)

Deux causes distinctes se cumulent, et corriger une seule ne suffit pas :

1. **Thème absent de la racine.** Dans `MainActivity.kt` (l. 86-92), `setContent`
   monte `SystemBarsController(...)` puis `Surface(modifier = Modifier.fillMaxSize())`
   *sans* `IptvXtreamTheme`. `IptvXtreamTheme` n'apparaît qu'à la l. 195, dans la
   branche `else` (post-gate profil). La `Surface` racine résout donc
   `MaterialTheme.colorScheme.surface` sur le `lightColorScheme()` par défaut de
   Compose → `Color(0xFFFFFBFE)`, quasi blanc, sur toute la fenêtre (edge-to-edge
   actif depuis F11 : `WindowCompat.setDecorFitsSystemWindows(window, false)`, l. 85).
2. **Ordre des modificateurs des trois écrans hors `Scaffold`.** Le fond est
   appliqué *après* `safeDrawingPadding()`, il est donc dessiné à l'intérieur des
   insets et laisse la `Surface` blanche apparaître dans les bandes système :
   * `SplashScreen.kt` l. 24-27 : `.fillMaxSize().then(if (isTv) … else safeDrawingPadding()).background(Surface1)` ;
   * `ProfileSelectionScreen.kt` l. 66-70 : idem avec `.mobileBackground()` ;
   * `ProfileManagementScreen.kt` l. 60-62 : idem (`background(Surface1)` TV / `mobileBackground()` mobile).

`SystemBarsController` (`presentation/theme/SystemBarsController.kt`) est déjà
correct et n'est pas en cause : il force `isAppearanceLightStatusBars = false` /
`isAppearanceLightNavigationBars = false` dès le premier montage (l. 91 de
`MainActivity`, `isPlayerRoute = false`), les icônes système sont donc déjà
claires. Seule la couleur *derrière* elles est fautive.

## Composants impactés

| Fichier | Modification |
| --- | --- |
| `app/src/main/java/com/cstv/app/MainActivity.kt` | Remonter `IptvXtreamTheme { … }` autour de la totalité du contenu de `setContent` (avant `SystemBarsController` et la `Surface` racine) ; donner à la `Surface` racine une couleur explicite ; supprimer le `IptvXtreamTheme` interne devenu redondant (l. 195). |
| `app/src/main/java/com/cstv/app/presentation/login/SplashScreen.kt` | Inverser l'ordre : fond edge-to-edge sur la `Box` racine, `safeDrawingPadding()` déplacé sur le conteneur de contenu. |
| `app/src/main/java/com/cstv/app/presentation/profile/ProfileSelectionScreen.kt` | Idem. |
| `app/src/main/java/com/cstv/app/presentation/profile/ProfileManagementScreen.kt` | Idem. |

## Nouveaux composants

Aucun composant public nouveau. Un helper privé partagé est cependant introduit
pour ne pas dupliquer trois fois le même patron :

```kotlin
// presentation/theme/Theme.kt (ou Color.kt, à côté de mobileBackground())
/**
 * Fond plein écran des écrans hors `Scaffold` (splash, gate profil) : le fond
 * est peint sur toute la fenêtre (edge-to-edge), les insets sûrs ne s'appliquent
 * qu'au contenu. Sans cette séparation, le fond s'arrête aux barres système et
 * laisse apparaître la surface sous-jacente.
 */
fun Modifier.appScreenBackground(isTv: Boolean): Modifier =
    if (isTv) this.background(Surface1) else this.mobileBackground()
```

Le patron d'usage devient, dans chacun des trois écrans :

```kotlin
Box(modifier = modifier.fillMaxSize().appScreenBackground(isTv)) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(if (isTv) Modifier else Modifier.safeDrawingPadding()),
        contentAlignment = Alignment.Center
    ) { /* contenu inchangé */ }
}
```

## Modèles de données, API, services, stockage, cache

Néant. Correctif strictement `presentation` : aucune entité Room, aucune
migration, aucun DTO, aucun appel réseau, aucun `UseCase`, aucun ViewModel
touché. La base reste en version 21.

## Couleur de la `Surface` racine

`Surface(color = MaterialTheme.colorScheme.background)` une fois le thème
remonté, soit `DarkBackground = Color(0xFF060608)` (`presentation/theme/Color.kt`
l. 6). Ce choix plutôt que `Surface1` (`0xFF0F0F13`) : `DarkBackground` est la
couleur terminale du dégradé `mobileBackground()` (`Theme.kt` l. 46-54,
`colorStops` 1.0f), la jonction entre le fond de l'écran et la surface racine est
donc invisible même pendant une transition. Sur TV, la `Surface` racine est
intégralement recouverte (`background(Surface1)`), l'écart n'est jamais visible.

## Performances

Nul impact mesurable : `MaterialTheme` est un `CompositionLocalProvider`, le
remonter d'un niveau ne change pas le nombre de recompositions. Le déplacement de
`safeDrawingPadding()` d'un nœud vers son enfant conserve exactement le même
nombre de passes de layout.

## Sécurité

Sans objet (aucune donnée, aucun identifiant, aucune permission en jeu).

## Compatibilité

* **Android TV** : `isTv` continue de court-circuiter `safeDrawingPadding()`, et
  `SystemBarsController` masque toujours les barres (`hide(systemBars())`,
  `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`). Aucun changement observable.
* **Routes lecteur immersives** : `isImmersivePlayerRoute(currentRoute)` et le
  second `SystemBarsController` (l. 241) restent inchangés, à l'intérieur du
  thème comme aujourd'hui.
* **Mobile, min SDK 21** : `safeDrawingPadding()` et `WindowInsets` sont fournis
  par `androidx.compose.foundation` déjà présent ; aucune API conditionnée à un
  niveau d'API n'est ajoutée. Encoche, navigation gestuelle et trois boutons sont
  couverts par le même mécanisme `safeDrawing`.
* **Rotation / recréation d'activité / retour d'arrière-plan** : le thème étant
  posé dès la première composition de `setContent`, il n'existe plus d'instant où
  un schéma clair est actif — le flash transitoire disparaît par construction.

## Dépendances

Aucune dépendance Gradle ajoutée ou mise à jour.

## Risques techniques

| Risque | Portée | Mitigation |
| --- | --- | --- |
| Double application de `IptvXtreamTheme` (racine + branche `else`) | Aucun effet visuel, mais bruit et risque de divergence future | Le `IptvXtreamTheme` interne est supprimé dans le même commit, pas conservé « au cas où ». |
| Un composant lisait implicitement le schéma **clair** par défaut avant le gate | Splash / écrans profil : couleurs de texte et d'icônes pourraient bouger | Les trois écrans fixent déjà leurs couleurs en dur (`Color.White`, `Surface1`, `AccentLavande`) ; seuls `MaterialTheme.colorScheme.primary` du Splash (l. 39) et de `ProfileSelectionScreen` changent — ils passent du magenta clair par défaut à `AccentLavande`, ce qui est le résultat *voulu*. |
| `mobileBackground()` sous les insets sur écran très haut | Le dégradé radial est calculé sur `size` : l'étendre à la fenêtre entière décale légèrement son centre | Décalage de l'ordre de la hauteur des barres (~2 % de la hauteur), non perceptible ; c'est de toute façon le rendu déjà obtenu sur les écrans passant par le `Scaffold`. |

## Contraintes de performance

Aucune. Le correctif ne touche ni le chargement du catalogue, ni le gate profil,
ni la navigation.

---

# 5. Architecture

## Position dans la Clean Architecture

Correctif entièrement contenu dans `presentation/` : aucune règle métier, aucun
accès `domain` ni `data`. La couche est respectée par abstention.

```
MainActivity.setContent
└── IptvXtreamTheme                       ← REMONTÉ ICI (couvre tout)
    ├── SystemBarsController(isTv, isPlayerRoute = false)
    └── Surface(color = colorScheme.background)   ← DarkBackground, plus de blanc
        ├── SplashScreen(isTv)                    ← fond edge-to-edge + contenu inset
        ├── ProfileManagementScreen(...)          ← idem
        ├── ProfileSelectionScreen(...)           ← idem
        └── (else) navigation principale
            ├── SystemBarsController(isTv, isPlayerRoute)
            └── Box(mobileBackground | Surface1) → Scaffold → AppNavGraph
```

## Flux de rendu

```
onCreate
  └─ setDecorFitsSystemWindows(false)        (F11, inchangé)
        │
        ▼
  première composition
        │
        ├─ IptvXtreamTheme installe DarkColorScheme      ← plus aucune fenêtre
        │                                                  temporelle en schéma clair
        ├─ SystemBarsController : barres transparentes,
        │  icônes claires (isAppearanceLight*Bars = false)
        │
        ▼
  Surface racine peinte en DarkBackground (0xFF060608), plein écran
        │
        ▼
  écran actif (Splash / gate profil) peint SON fond sur toute la fenêtre
        │
        ▼
  contenu (textes, boutons) contraint par safeDrawingPadding() sur mobile
```

Le point clé : **le fond et les insets sont dissociés**. Le fond appartient à la
fenêtre, les insets au contenu. Aujourd'hui les deux sont portés par le même
nœud, ce qui est précisément l'origine des bandes blanches.

## Responsabilités des composants

* **`MainActivity`** : installer le thème une fois pour toute l'application,
  fournir une surface racine sombre, arbitrer splash / gate profil / navigation.
  Aucune logique de couleur au-delà de la surface racine.
* **`SystemBarsController`** : politique de fenêtre (visibilité et apparence des
  barres). Inchangé — il ne peint rien, il ne fait que déclarer l'apparence.
* **`SplashScreen`, `ProfileSelectionScreen`, `ProfileManagementScreen`** :
  peindre leur propre fond sur toute la fenêtre et contraindre *leur contenu*
  aux zones sûres. Cette responsabilité est explicitée par
  `Modifier.appScreenBackground(isTv)`.
* **`IptvXtreamTheme`** : source unique du schéma de couleurs. Un seul point
  d'installation après le correctif.

## Décisions techniques

1. **Remonter le thème plutôt que colorer la `Surface` en dur.** Colorer la
   `Surface` seule masquerait le symptôme mais laisserait splash et gate profil
   résoudre `MaterialTheme.colorScheme.primary` sur le schéma clair — le même bug
   reviendrait au premier composant qui lit le thème avant le gate. La cause est
   l'absence de thème, c'est elle qu'on corrige.
2. **Supprimer le `IptvXtreamTheme` interne.** Deux installations imbriquées du
   même schéma sont inoffensives mais invitent à une divergence future (un thème
   TV ajouté à un seul des deux niveaux). Un seul point d'entrée.
3. **`colorScheme.background` plutôt que `Surface1` pour la surface racine.**
   Aligné sur la couleur terminale de `mobileBackground()`, donc aucune jonction
   visible. Voir section 4.
4. **Helper `appScreenBackground(isTv)` plutôt que trois copies.** Les trois
   écrans hors `Scaffold` partagent exactement la même règle ; la factoriser
   garantit qu'un quatrième écran hors `Scaffold` ne réintroduira pas le bug.
5. **Ne pas toucher `safeDrawingPadding()`.** Le comportement demandé par le
   critère d'acceptation « les textes et boutons conservent leurs marges sûres »
   est déjà correct ; seule sa *position dans la chaîne de modificateurs* change.

## Stratégie de tests

Le correctif est du layout pur (couleurs, ordre de modificateurs), explicitement
classé « non prioritaire / pas sur-investir » par `AGENTS.md` (section Stratégie
de tests), et sa vérification visuelle exigerait un device — donc exclue des
critères de validation de l'agent. Aucun test unitaire JVM n'est ajouté : il n'y
a ni logique métier, ni parsing, ni état à couvrir. La non-régression est assurée
par `./gradlew testDebugUnitTest` (suite existante) + `assembleDebug` +
`lintDebug`.

---

# 6. Plan de développement

## Ordre d'exécution

Le thème racine est posé avant de déplacer les insets : chaque écran hors
`Scaffold` s'appuie ainsi déjà sur une surface sombre durant son adaptation.

### Tâche 1 — Unifier le thème et la surface racine

- [x] Envelopper tout le contenu de `setContent` dans `IptvXtreamTheme` et
  expliciter la couleur de fond de la `Surface`.

Objectif : supprimer toute composition possible avec le schéma clair par défaut,
sans changer la politique existante des barres système ni des routes lecteur.

Fichiers : `MainActivity.kt`.

Validation : le thème interne redondant est supprimé ; la surface racine utilise
`colorScheme.background` ; TV et routes immersives restent inchangées.

### Tâche 2 — Séparer fond edge-to-edge et contenu dans les écrans de gate

- [x] Déplacer `safeDrawingPadding()` sur le contenu des trois écrans concernés,
  après un fond appliqué au conteneur plein écran.

Objectif : prolonger le fond sombre sous les barres tout en conservant les zones
sûres des actions et textes mobiles.

Fichiers : `SplashScreen.kt`, `ProfileSelectionScreen.kt`,
`ProfileManagementScreen.kt`.

Validation : aucun fond n'est limité par les insets ; le contenu reste protégé
sur encoche, navigation gestuelle et trois boutons ; la branche TV reste intacte.

### Tâche 3 — Vérifier la non-régression automatisée

- [x] Exécuter les contrôles Gradle et documenter la limite de validation visuelle.

Fichiers : tests existants et ce ticket.

Validation : `testDebugUnitTest`, `assembleDebug` et `lintDebug` passent ; la
vérification visuelle Splash/profils sur mobile reste explicitement distincte.

---

# 7. Notes de développement

Implémentation conforme à la spécification :
- `MainActivity.kt` : `IptvXtreamTheme` remonté à l'englobant de tout `setContent` (avant `SystemBarsController` et la `Surface` racine), `Surface(color = MaterialTheme.colorScheme.background)`. Le `IptvXtreamTheme` interne de la branche `else` (post-gate profil) a été remplacé par un simple `run {}` pour ne pas dupliquer l'installation du thème.
- `Theme.kt` : ajout du helper `Modifier.appScreenBackground(isTv)` (Surface1 sur TV, `mobileBackground()` sur mobile).
- `SplashScreen.kt`, `ProfileSelectionScreen.kt`, `ProfileManagementScreen.kt` : fond peint sur une `Box` racine edge-to-edge (`appScreenBackground(isTv)`), `safeDrawingPadding()` déplacé sur une `Box` de contenu interne — le contenu visuel de chaque écran est inchangé.
- Validation visuelle sur device explicitement hors périmètre de l'agent (AGENTS.md : tests manuels/device exclus des critères de validation) ; seule la non-régression automatisée (`testDebugUnitTest`, `assembleDebug`, `lintDebug`) a été vérifiée — tout passe.

---

# 8. Review

## Critique

Aucun problème critique identifié.

## Majeur

Aucun problème majeur identifié.

## Mineur

Aucun problème mineur identifié.

## Conclusion

Implémentation conforme à la spécification : le thème couvre désormais les
écrans précédant le `NavHost`, la surface racine utilise le schéma sombre et le
fond des trois écrans concernés est peint avant l'application des insets au
contenu. La politique des barres système et les branches TV/lecteur ne sont pas
modifiées. Aucun test JVM supplémentaire n'est justifié pour ce changement de
layout pur ; la validation visuelle sur appareil reste distincte et exclue des
critères finaux par `AGENTS.md`.

Aucun correctif de code demandé à l'issue de cette review.

---

# Validation finale (étape 8)

**Comportement attendu / règles métier :** `IptvXtreamTheme` enveloppe
désormais toute l'application dans `MainActivity.kt` (plus seulement le
contenu post-sélection de profil), la `Surface` racine hérite du schéma
sombre, et `safeDrawingPadding` reste préservé sur les trois écrans
concernés (Splash, sélection de profil, gestion des profils) — conforme aux
règles de rendu de la section 3.

**Critères d'acceptation :**
- [x] Splash : barre d'état/navigation sombres sur fond noir.
- [x] Sélection de profil : plus de bande blanche, thème sombre continu.
- [x] Gestion des profils (`ProfileManagementScreen`) : barres système
  sombres héritées.
- [x] Lisibilité icônes/textes système conservée sur fond sombre.
- [x] Android TV et routes lecteur immersives inchangées (non touchées par
  le correctif, confirmé par la review étape 6).

**Qualité technique / absence de régression :** changement de layout pur, sans
logique métier ni couche réseau/données touchée. Aucun test JVM requis pour
ce correctif (cf. review étape 6) ; `./gradlew testDebugUnitTest` et
`./gradlew assembleDebug` verts (suite complète, aucune régression).

**Validation visuelle sur appareil :** hors périmètre de cette validation
automatisée — nécessite un device/émulateur physique, explicitement exclu des
critères de validation finale par `AGENTS.md`.

**Status: VALIDATED**

---

# 9. Release

Version : v1.67.0

Commit : :bug: fix(theme): resolve white system bars on splash and profile selection (B19)

Date : 2026-08-02
