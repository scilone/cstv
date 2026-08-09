# B25 — Bande de couleur au bas de la fiche série TV, et épisode focalisé à l'entrée

**Statut** : ARCHIVE (livré en v1.77.3)
**Origine** : troisième série de retours sur la refonte de la fiche série
Android TV, après [B23](B23-tv-fiche-serie-retours-refonte.md) et
[B24](B24-tv-fiche-serie-ajustements-navigation.md). Capture fournie par le PO.

## Demandes

1. **Bande de couleur au bas de l'écran saisons/épisodes** — visible sous la
   rangée des titres associés, alors que le fond doit y être totalement noir.
2. **Épisode focalisé à l'entrée** — l'arrivée sur le panneau ouvre bien la bonne
   saison (B24) mais toujours en tête de liste ; sur une saison de trente
   épisodes, atteindre celui qu'on suit demande autant d'appuis.

## Analyse

1. Le voile posé en B23 est **interne** à `SeriesDetailsTvLayout`, mais celui-ci
   n'occupe pas toute la hauteur : dans `SeriesDetailsScreen`, la colonne qui
   l'accueille porte un `padding(bottom = 24.dp)` appliqué aux deux plateformes.
   Ces 24 dp laissaient voir le calque du dessous — le visuel de couverture
   flouté à `alpha 0.18f`, posé plein écran en tête de `SeriesDetailsScreen`.
   D'où une bande **colorée**, et non simplement plus claire : elle prend les
   teintes de l'affiche. Ni le voile ni le fond du layout ne pouvaient la couvrir
   puisqu'elle est en dehors d'eux.

## Correctifs

1. La marge basse de la colonne d'accueil devient mobile-seule
   (`bottom = if (isTv) 0.dp else 24.dp`). Le layout TV pose lui-même son fond
   plein et gère ses propres réserves ; il récupère au passage 24 dp de hauteur,
   qui vont aux épisodes. La fiche film n'est pas concernée : sa marge est dans
   la branche mobile.
2. `tvSeriesInitialEpisodeIndex()` : rang de l'épisode au `lastAccessedAt` le
   plus récent dans la saison affichée — même repère que `tvSeriesInitialSeason`,
   pour que les deux présélections désignent le même épisode. Nouvelle cible
   d'entrée `RESUME_EPISODE`, qui défile la liste jusqu'à lui avant de demander
   le focus (sans quoi son `FocusRequester` n'a pas de nœud sur une longue
   saison). Le retour depuis les titres associés continue de primer.

   L'épisode repris pouvant être aussi le premier ou le dernier de la saison, et
   deux `Modifier.focusRequester` ne se cumulant pas sur un même nœud,
   `tvSeriesEpisodeSlot()` porte la précédence unique partagée par la pose du
   modificateur et par la résolution de la cible.

   À noter : le repère est « dernier épisode **consulté** », donc un épisode
   terminé garde le focus plutôt que de le passer au suivant. C'est la lecture
   symétrique de la règle de saison ; à revoir si l'usage montre qu'on attend
   plutôt le premier épisode non vu.

## Tests

`SeriesDetailsTvLayoutTest` : `tvSeriesInitialEpisodeIndex` (épisode le plus
récent, saison vierge), précédence du retour depuis les titres associés, et
`tvSeriesEpisodeSlot` sur les recouvrements premier/dernier/repris, saison à un
seul épisode comprise.

Non couvert automatiquement (exclu par AGENTS.md, requiert un device) : la
disparition de la bande et le rendu du focus à l'entrée.
