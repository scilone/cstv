# B28 — TV : popin de mise à jour tronquée et actions de Paramètres invisibles

- **Statut** : VALIDATION
- **Type** : Bug UI (hotfix, suite de B27)
- **Écrans** : dialogue de mise à jour (TV), Paramètres (TV)
- **Fichiers** : `presentation/update/AppUpdateDialog.kt`,
  `presentation/settings/SettingsScreen.kt`, `presentation/theme/Color.kt`

## 1. Constat (PO)

1. La popin « Mise à jour disponible » sur TV est cassée : le troisième bouton
   (« Ignorer cette version ») est réduit à une pastille tronquée affichant un
   « I ».
2. Sur TV, « EXTRAIRE LES LOGS DE DIAGNOSTIC » et « Rechercher une mise à jour »
   ne ressemblent pas à des boutons — ils devraient avoir le même rendu que
   « MODE DEBUG ACTIF ».

## 2. Cause

1. `ActionsRow` utilisait un `Row` et `buttonModifier` fixe la largeur des
   boutons TV à 160 dp. Un `Row` mesure chaque enfant sans poids avec la largeur
   **restante** : dans une surface de 480 dp moins 2 × 28 dp de padding, soit
   424 dp utiles, les deux premiers boutons consomment 160 + 12 + 160 + 12 dp et
   il ne reste que ~80 dp au troisième, dont la `width(160.dp)` est écrasée par
   la contrainte. L'état `Available` non obligatoire est le seul à porter trois
   actions : le défaut n'existait que là.
2. `TvSettingsActionButton` avait `Surface3` comme conteneur au repos, soit
   exactement le fond des cartes qui le portent : aucun contraste, l'action se
   lisait comme une ligne de texte. `TvSortingOptionButton` (« MODE DEBUG
   ACTIF ») utilisait, lui, `0xFF2C2C35` — un cran au-dessus.

## 3. Correctif

1. `ActionsRow` passe en `FlowRow` sur TV (passage à la ligne au lieu
   d'écrasement, robuste à un libellé plus long ou à une action supplémentaire)
   et la surface du dialogue TV passe de 480 à 600 dp, largeur qui contient les
   trois actions sur une seule ligne.
2. Nouvelle couleur nommée `Surface4` (`0xFF2C2C35`, valeur déjà employée en dur
   dans une dizaine d'écrans) : c'est l'aplat des contrôles posés sur une carte
   `Surface3`. `TvSettingsActionButton` l'adopte au repos, les options de tri des
   Paramètres l'utilisent par la constante plutôt qu'en dur. Le liseré de focus
   `AccentLavandeHover` est inchangé.

## 4. Validation

- `./gradlew assembleDebug` + `testDebugUnitTest` + `lintDebug`.
- Pas de test unitaire : correctif de mise en page pure (cf. AGENTS.md,
  « Non prioritaire / pas sur-investir »). Vérification visuelle PO sur TV.
