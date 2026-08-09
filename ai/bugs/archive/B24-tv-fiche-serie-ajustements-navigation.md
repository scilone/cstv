# B24 — Ajustements de navigation sur les fiches TV (série et film)

**Statut** : ARCHIVE (livré en v1.77.2)
**Origine** : deuxième série de retours d'usage sur la refonte de la fiche série
Android TV, après [B23](B23-tv-fiche-serie-retours-refonte.md).

## Demandes

1. **Icônes des boutons de lecture** — les boutons TV (fiche film et fiche série,
   « relire depuis le début » compris) n'avaient que du texte, là où le mobile
   pose une icône.
2. **Saison présélectionnée** — la descente sur le panneau saisons/épisodes
   ouvrait toujours la saison 1, jamais celle de l'épisode en cours.
3. **Bloc « titres associés »** — son amorce n'apparaissait qu'à l'atteinte du
   dernier épisode ; elle doit être visible d'emblée, l'**ouverture** restant
   conditionnée à une descente depuis le dernier épisode.
4. **Trailer** — le couper en descendant sur les saisons/épisodes, le rétablir en
   remontant sur le hero.

## Correctifs

1. `PlayButton` (`VodDetailsTvLayout`) prend un paramètre `icon` : `PlayArrow`
   pour lire/reprendre, `Replay` pour « relire depuis le début » — les mêmes que
   `VodDetailsScreen` côté mobile. `TvSeriesPlayButton` reçoit `PlayArrow`.
2. `tvSeriesInitialSeason()` : la saison de l'épisode au `lastAccessedAt` le plus
   récent, la première à défaut. Le repère est `lastAccessedAt` et non la
   position de reprise — un épisode terminé remet celle-ci à zéro (cf. B23) mais
   reste le dernier consulté, cas le plus courant d'une série suivie épisode par
   épisode. La pastille correspondante peut être hors de la fenêtre de
   composition de la `LazyRow` des saisons : elle est ramenée dans le champ avant
   la demande de focus, même précaution que pour les titres associés en B23.
3. L'amorce de la rangée est visible en permanence : l'`alpha` conditionné à
   `lastEpisodeFocused` est retiré, ainsi que l'état et le rappel qui ne
   servaient qu'à lui. La transition `EPISODES_TO_RELATED` est inchangée.
4. `TvSeriesHeroArtwork` reçoit `trailerMuted || section != HERO`. Le choix de
   l'utilisateur est **forcé** le temps de la descente, jamais écrasé : la
   remontée sur le hero le restitue tel quel. Point signalé comme réversible par
   le PO — le retirer tient en la suppression du `|| section != HERO`.

## Tests

`SeriesDetailsTvLayoutTest` : `tvSeriesInitialSeason` — dernier épisode consulté,
épisode terminé compté comme consulté, repli sur la première saison sans
historique, et saison connue par ses seuls épisodes.

Non couvert automatiquement (exclu par AGENTS.md, requiert un device) : le rendu
des icônes, la coupure effective du son du trailer et la visibilité de l'amorce.
