# F30 - TV Fiche Film : retours PO sur la refonte

## Informations générales

Status:
RELEASED

Created:
2026-08-09

---

# 1. Description

Six ajustements demandés par le PO après l'essai sur appareil de la fiche film
TV livrée en v1.75.0 (F28) :

1. synopsis long : le bouton de lecture sort de l'écran — rétablir une mécanique
   « voir plus » sans jamais chasser le reste du contenu ;
2. bouton de lecture à la largeur pleine de son bloc (il occupe ~75 %) ;
3. boutons de lecture au design de la fiche mobile : plus carrés, texte blanc,
   avec le doublon « relire depuis le début » ;
4. trailer joué dans l'emplacement de l'affiche, pas en fond plein écran ;
5. rangée « Titres associés » : sélecteur fixe sur la première vignette, c'est
   la rangée qui défile — comme partout ailleurs sur TV ;
6. rangée « Titres associés » moins large : elle touche les bords de l'écran.

---

# 2. Contexte

F28 a posé la fiche mais trois choix se révèlent faux à l'usage. La colonne de
droite a une hauteur fixe (`écran − 110 dp`) et empile des enfants de hauteur
naturelle : un synopsis de plus de six lignes pousse mécaniquement les boutons
hors de cette hauteur. Le trailer, hérité du fond plein écran de F13, écrase
toute la page alors que la maquette réserve la moitié gauche à l'affiche. Enfin
la rangée de titres associés garde l'anneau de focus par vignette, alors que
tout le reste de l'application TV (Accueil, Live TV, Films, Séries, Recherche)
utilise le sélecteur fixe F23 sous lequel le contenu défile.

---

# 3. Spécification fonctionnelle

### Règles métier

- Le synopsis ne peut jamais réduire la place des crédits, des actions ni des
  boutons de lecture : il prend l'espace qui reste et s'y tronque.
- S'il est tronqué, une action « VOIR PLUS » ouvre le texte intégral dans une
  fenêtre par-dessus la fiche. Rien ne bouge derrière : la fiche reste
  entièrement visible, et fermer la fenêtre rend le focus au bouton.
- Les boutons de lecture reprennent le dessin mobile : rayon 8 dp, texte blanc,
  fond `AccentLavande` pour l'action principale et `Surface3` pour la
  secondaire, sur toute la largeur du bloc.
- Le trailer occupe exactement l'emplacement de l'affiche, avec le même fondu
  horizontal vers `Surface1`.
- La rangée « Titres associés » adopte le sélecteur fixe (F23) et le défilement
  à ancre de début (F19/B22), et se tient à 48 dp des bords.

### Critères d'acceptation

- Synopsis de 30 lignes : les deux boutons de lecture restent visibles.
- « VOIR PLUS » n'apparaît que si le texte est réellement tronqué.
- Le bouton de lecture occupe toute la largeur de la colonne de droite.
- Un film avec trailer joue celui-ci dans le panneau gauche, l'affiche du
  panneau restant le poster de repli du lecteur.
- Descente sur les titres associés : le cadre se pose sur la première vignette
  et n'en bouge plus ; gauche/droite font défiler la rangée sous lui.
- La rangée ne touche plus les bords de l'écran.

---

# 4. Spécification technique

## Emplacements

- `presentation/vod/VodDetailsTvLayout.kt` — tous les changements de la fiche.
- `presentation/components/RelatedTitlesRow.kt` — paramètre `tvPivotEnabled`
  optionnel (défaut `false`) : la rangée câble alors `tvPivotItem` et
  `tvPivotHorizontalEndSpacer`. Mobile et fiche série ne passent pas le
  paramètre et ne changent donc pas.

## Synopsis

`Modifier.weight(1f, fill = false)` sur le texte : un `Column` mesure ses
enfants non pondérés en premier, le synopsis n'obtient donc que la place
restante et ne peut plus chasser les boutons. `onTextLayout` remonte
`hasVisualOverflow` pour n'afficher « VOIR PLUS » qu'en cas de troncature. La
fenêtre est un `Dialog` plein écran, ce qui évite le défaut relevé en § 3 de
F28 (un dépliement en place déplacerait le contenu sous le focus).

## Sélecteur fixe et remontée

La difficulté est la cohabitation du sélecteur F23 avec la remontée de F28 :
`TvFocusSelectorOverlay` dessine un cadre à une position **publiée**, tandis que
la remontée translate la colonne par `graphicsLayer`. Le cadre resterait donc
sur la position d'avant la remontée.

La fiche republie la géométrie de la vignette focalisée à chaque valeur de
l'animation de remontée (`snapshotFlow` sur le décalage animé), à partir de
`positionInRoot()` et de la taille — et non de `boundsInRoot()`, qui clippe par
les parents et renverrait un rectangle vide pour une vignette encore hors
champ (leçon de B22). Le cadre suit ainsi la vignette pendant toute la
remontée.

---

# 5. Architecture

Aucun impact `data`/`domain`.

---

# 6. Plan de développement

- [x] 1. Synopsis flexible + « VOIR PLUS » en fenêtre.
- [x] 2. Boutons de lecture pleine largeur, dessin mobile.
- [x] 3. Trailer dans l'emplacement de l'affiche.
- [x] 4. Sélecteur fixe et défilement d'ancre sur les titres associés.
- [x] 5. Marges latérales de la rangée.
- [x] 6. `assembleDebug`, `lintDebug`, `testDebugUnitTest`.

---

# 7. Notes de développement

- `RelatedTitlesRow` gagne un `tvPivotEnabled` opt-in (défaut `false`) : elle
  câble alors `tvPivotItem` et `tvPivotHorizontalEndSpacer`. Seule la fiche film
  TV le passe ; mobile et fiche série sont inchangés.
- La cohabitation du sélecteur fixe et de la remontée s'est réglée en
  republiant la géométrie de la vignette focalisée à chaque valeur de
  l'animation (`snapshotFlow` sur le décalage), à partir de `positionInRoot()`
  et de la taille — `boundsInRoot()` clippe par les parents et rend un
  rectangle vide pour une vignette encore hors champ (B22).
- Le trailer partage désormais l'emplacement de l'affiche, avec le même fondu
  horizontal par-dessus, et n'a plus de scrim : la colonne de texte ne passe
  plus sur la vidéo.
- Le « voir plus » ouvre un `Dialog` plein écran plutôt que de déplier en
  place : c'est ce qui permet de tenir à la fois la demande du PO et la règle
  de F28 selon laquelle rien ne doit bouger sous le focus.

# 8. Release

| | |
| --- | --- |
| Version | **v1.76.0** (`versionCode` 17600) |
| Date | 2026-08-09 |
