# B23 — Retours PO sur la refonte de la fiche série Android TV

**Statut** : ARCHIVE (livré en v1.77.1)
**Origine** : retours d'usage sur la refonte livrée en v1.77.0 (commit `9224b61`).

## Symptômes rapportés

1. **Ligne de droite des épisodes incohérente** — certains épisodes affichent une
   durée, d'autres « Non vu », sans logique apparente.
2. **Panneau saisons/épisodes trop haut** — moins de quatre épisodes visibles
   sans défiler, alors qu'un vide occupe le bas de l'écran (réserve des titres
   associés).
3. **Trailer visible sous le panneau des épisodes** — un bandeau du visuel/trailer
   du hero dépasse au bas de l'écran une fois descendu sur les saisons/épisodes.
4. **Aucune chronologie sur un épisode déjà vu** — seule la durée s'affiche, rien
   n'indique que l'épisode a été regardé.
5. **Titres associés inutilisables à la réouverture** — à la deuxième ouverture,
   la rangée s'ouvre sans cadre de focus et ne répond plus au D-pad.

## Analyse

1 et 4 ont la même racine : la ligne de droite mélangeait deux axes, « durée du
média » et « état de visionnage ». Elle affichait `episode.duration` quand le
panel la renseignait, et « Non vu » **uniquement** quand elle était vide — donc
un état de visionnage déduit d'une donnée qui n'en parle pas. Symétriquement,
aucun état « vu » n'existait : le lecteur remet la position à zéro en fin de
lecture (`SeriesPlayerScreen.onTrackerDispose` → `SeriesViewModel.clearPosition`),
et un épisode terminé retombait donc à `resumePositionMs = 0`, indiscernable d'un
épisode jamais lancé.

Point clé : `clearPosition` **réécrit** la ligne à zéro au lieu de la supprimer.
La présence d'une ligne à `positionMs = 0` distingue donc « déjà vu » de « jamais
lancé », qui n'a aucune ligne — aucune colonne Room ni migration nécessaires.

3 : le visuel du hero occupe toute la hauteur de l'écran derrière les panneaux
qui défilent par-dessus ; le panneau des épisodes ne le couvrait que sur sa
propre hauteur, amputée de l'amorce des titres associés.

5 : à la fermeture, la rangée reste défilée là où le pivot l'a laissée. La
première vignette peut alors être sortie de la fenêtre de composition de la
`LazyRow` ; son `FocusRequester` n'a plus de nœud, `requestFocus()` lève et
l'échec était avalé par le `runCatching`. Le focus ne partant nulle part, le
cadre (`TvFocusSelectorState`) n'était jamais republié — d'où l'absence de
sélecteur **et** l'impossibilité de naviguer.

## Correctifs

- `SeriesEpisode.watched`, dérivé dans `SeriesRepositoryImpl` via
  `isEpisodeWatched()` (ligne de position présente et remise à zéro).
- `tvSeriesEpisodeStatus()` : la ligne de droite ne parle plus que de temps —
  temps restant, « Vu », durée totale, ou rien quand le panel ne la renseigne
  pas (`"00:00"` étant sa valeur de repli, elle vaut absence de donnée).
- `tvSeriesEpisodeProgress()` : barre pleine pour un épisode vu.
- Vignettes d'épisode ramenées à 120 × 68 dp et en-tête resserré : quatre cartes
  tiennent sans défiler sur un écran 1080p ; amorce des titres associés réduite
  de 96 à 40 dp.
- Voile plein `Surface1` sur l'arrière-plan dès que la section quitte le hero.
- Réouverture des titres associés : `scrollToItem(0)` puis demande de focus
  rejouée sur quelques frames jusqu'à ce que la rangée l'ait effectivement pris.

## Tests

- `SeriesDetailsTvLayoutTest` : statut d'épisode (vu / restant / durée / aucune)
  et chronologie pleine pour un épisode vu.
- `SeriesRepositoryImplTest` : `isEpisodeWatched` — ligne à zéro = vu, absence de
  ligne = jamais lancé.

Non couvert automatiquement (exclu par AGENTS.md, requiert un device) : le rendu
du voile, la tenue des quatre épisodes à l'écran et la reprise du focus D-pad
dans les titres associés.
