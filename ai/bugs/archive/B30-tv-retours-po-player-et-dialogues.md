# B30 — TV : retours PO sur le lecteur et les dialogues

- **Statut** : RESOLVED (hotfix, suite de B29)
- **Type** : Bug UI
- **Écrans** : lecteurs Film / Série / Live, dialogue de mise à jour, dialogue
  « Retirer de la liste »

## 1. Constat (PO)

1. La jaquette affichée dans le lecteur média est trop petite sur TV.
2. Les boutons de la popin « Mise à jour disponible » ne sont pas accessibles et
   on ne distingue pas le bouton sélectionné.
3. Même défaut sur la popin « Retirer de la liste ? » : rendu hors charte (fond
   lavande plein, boutons blancs) et focus illisible.
4. Dans le lecteur live, le sélecteur de catégorie propose « Tout », doublon
   inutile de « Liste de zapping ».
5. Les boutons pause / avance / recul du lecteur portent un aplat gris carré ;
   seul le contour violet est souhaité.
6. Quand le focus est sur la barre de progression, gauche/droite doivent faire
   avancer et reculer la lecture.

## 2. Causes

1. `PlayerCoverAction` avait une taille fixe 64 × 92 dp, dimensionnée pour le
   mobile.
2. `AppUpdateDialog` demandait le focus initial une seule fois : si le bouton
   n'était pas encore attaché, plus aucune action n'était focalisée et le pad
   restait sans effet. Les composants `androidx.tv.material3` n'avaient par
   ailleurs aucune couleur de focus explicite : le défaut repeint le conteneur en
   clair, sans distinguer action principale et secondaire.
3. `HistoryRemovalDialog` utilisait `AlertDialog` de Material 3, dont le
   conteneur prend la couleur du thème (lavande) et dont les `Button` n'ont pas
   d'état de focus distinct au D-pad.
4. Le sélecteur du tiroir concaténait `liveTvState.categories`, qui contient la
   pseudo-catégorie `LiveCategory("all", "Tout")` ajoutée par `LiveTvViewModel`.
5. Dans `TransportButton`, `clickable` précédait tout `clip` : l'indication de
   clic se peignait sur les bornes carrées du nœud, autour du bouton rond.
6. La barre de progression n'était jamais focalisable en pratique (le `Box`
   racine consommait les flèches, cf. B29) et n'avait aucun traitement de touche.

## 3. Correctif

1. `PlayerCoverAction` gagne un paramètre `large` (128 × 184 dp), activé sur TV
   dans les lecteurs Film et Série. Le mobile garde le format compact : le bloc
   bas y partage une hauteur d'écran en paysage.
2. `AppUpdateDialog` réutilise `rememberTvInitialFocus` (tentatives bornées, déjà
   employé ailleurs) et fixe des couleurs de focus explicites : lavande éclairci
   pour l'action principale, `Surface4` pour les secondaires, liseré blanc 2 dp
   dans les deux cas.
3. Nouveau `presentation/components/CstvDialog.kt` : dialogue de confirmation
   conforme à la charte (fond `Surface2`, titre `TextPrimary`, corps
   `TextSecondary`) et action `CstvDialogAction` dont le focus change le fond
   **et** ajoute un liseré. `HistoryRemovalDialog` est reconstruit dessus, focus
   initial sur « Annuler » (l'action destructrice ne doit pas être celle qu'un
   appui réflexe déclenche).
4. Le sélecteur de catégorie du tiroir live filtre `categoryId == "all"`.
5. `TransportButton` clippe en cercle avant `clickable` : l'aplat carré disparaît,
   le contour violet du focus reste.
6. Les barres de progression Film et Série traitent gauche/droite en
   `onPreviewKeyEvent` (avant le pas par défaut du `Slider`) et appliquent le
   ±10 s existant.

**Fichiers :** `presentation/components/CstvDialog.kt` (nouveau),
`presentation/components/HistoryRemovalDialog.kt`,
`presentation/update/AppUpdateDialog.kt`,
`presentation/player/PlayerUiComponents.kt`,
`presentation/player/PlayerScreen.kt`,
`presentation/vod/VodPlayerScreen.kt`,
`presentation/series/SeriesPlayerScreen.kt`.

## 4. Validation

- `./gradlew testDebugUnitTest`, `./gradlew assembleDebug`, `./gradlew lintDebug`
  passent ; `git diff --check` propre.
- Pas de nouveau test unitaire : correctifs de mise en page et de focus, non
  vérifiables hors device (`AGENTS.md`, « Non prioritaire / pas sur-investir » et
  exclusion des vérifications sur device). La table de touches introduite par B29
  reste couverte par `PlayerRemoteKeysTest`.
- Reste à harmoniser sur `CstvDialog` (hors périmètre de ce hotfix, aucun retour
  PO à ce jour) : `PlaybackLockConflictDialog`, dialogues de
  `ProfileManagementScreen` et de `SettingsScreen`, sélecteurs de pistes des
  lecteurs.

## 5. Release

Version : v1.83.2

Date : 2026-08-14
