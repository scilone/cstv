# B26 — Rangée favoris / j'aime / je n'aime pas en pleine largeur (fiches TV)

**Statut** : ARCHIVE (livré en v1.77.4)
**Origine** : retour PO sur les fiches film et série Android TV, après
[B25](B25-tv-fiche-serie-bande-fond-et-episode-focalise.md).

## Demande

Les trois actions n'occupaient qu'environ trois quarts du bloc, serrées à
gauche, alors que tout le reste de la colonne — synopsis, crédits, boutons de
lecture — la remplit. Consigne : les centrer dans le bloc **ou** leur faire
prendre tout l'espace.

## Choix retenu

Partage à parts égales (`Modifier.weight(1f)` sur chacune, rangée en
`fillMaxWidth`), plutôt qu'un centrage du groupe. Le centrage aurait décalé la
première action vers l'intérieur et rompu l'alignement à gauche que partagent le
titre, le synopsis et les boutons de lecture ; le partage garde ce bord et
remplit la largeur. Effet secondaire utile : le cadre de focus couvre un tiers
entier, nettement plus lisible à distance de canapé qu'une pastille serrée.

Le contenu de chaque action est centré dans son tiers (`Arrangement.Center` sur
une `Row` en `fillMaxWidth`), et les libellés sont bornés à une ligne : ils n'ont
plus la largeur qu'ils réclament mais un tiers de rangée, et « Ajouter aux
favoris » serait passé à la ligne en déformant la hauteur d'une seule des trois.

Les séparateurs verticaux gardent leur largeur fixe et se posent donc aux deux
tiers exacts.

## Fichiers

- `presentation/vod/VodDetailsTvLayout.kt` : `DetailActionButton` prend un
  `modifier`, la rangée passe en `fillMaxWidth`.
- `presentation/series/SeriesDetailsTvLayout.kt` : idem pour `TvSeriesAction`.

## Tests

Aucun test ajouté : changement de mise en page pur, sans logique — AGENTS.md
exclut explicitement les tests de code de layout (couleurs, dimensions). Rendu à
valider sur device.
