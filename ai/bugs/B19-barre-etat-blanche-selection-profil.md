# B19 - Barres système blanches au démarrage (Splash et Sélection de Profil) sur Mobile

## Informations générales

Status:
ANALYSIS

Created:
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

## Règles de rendu et d'intégration

1. **Unification du thème** : Le thème sombre de l'application (`IptvXtreamTheme`) doit envelopper la totalité de l'application dans `MainActivity.kt`, et pas seulement le contenu post-sélection de profil.
2. **Couleur de la Surface racine** : La `Surface` racine doit hériter du schéma de couleurs sombres (ou être configurée explicitement avec un fond noir/sombre), évitant ainsi tout flash ou débordement blanc sous les barres système transparentes.
3. **Consommation des insets** : Préserver le comportement de `safeDrawingPadding` pour que les textes et boutons importants ne soient pas coupés par les encoches de l'écran, tout en s'assurant que le fond sombre s'étend sur toute la hauteur physique de l'écran (edge-to-edge).

## Critères d'acceptation (Fonctionnels)

- [ ] Sur mobile, au lancement de l'application (Splash screen), la barre d'état et la barre de navigation sont sombres/transparentes sur fond noir.
- [ ] Sur mobile, lors de l'affichage de l'écran de sélection de profil, la barre d'état et la barre de navigation se fondent parfaitement dans le thème sombre sans bande blanche.
- [ ] Sur mobile, l'écran de gestion des profils (ProfileManagementScreen) hérite également de barres système sombres et intégrées.

---

# 4. Spécification technique

*(À compléter à l'Étape 3)*

---

# 5. Architecture

*(À compléter à l'Étape 3)*

---

# 6. Plan de développement

*(À compléter à l'Étape 4)*

---

# 7. Notes de développement

*(À remplir au fil du développement)*

---

# 8. Review

*(À remplir à l'Étape 6)*

---

# 9. Release

*(À remplir à l'Étape 10)*
