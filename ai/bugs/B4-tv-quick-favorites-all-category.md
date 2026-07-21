# B4 - Impossible d'ajouter rapidement en favoris sur TV dans la liste « Tout »

## Informations générales

Type:
Bug

Status:
SPECIFICATION

Created:
2026-07-21

Target version:
v1.48.32

---

# 1. Description

Sur l'écran **TV en Direct** (Live TV) sur Android TV, lorsque l'utilisateur est sur la catégorie globale **« Tout »** (qui liste verticalement les catégories sous forme de rangées horizontales de chaînes), il est devenu impossible de mettre rapidement une chaîne en favoris.

La carte de chaîne s'étire sur toute la largeur de l'écran, ce qui casse la mise en page de la ligne horizontale (un seul élément visible au lieu d'un défilement de chaînes côte à côte) et rejette le bouton d'ajout rapide aux favoris (l'étoile) hors-écran ou à l'extrême droite, le rendant invisible et inaccessible à la télécommande.

---

# 2. Contexte

Le composant de carte TV pour une chaîne est défini par `StreamTvCard` dans `LiveTvComponents.kt`.

Ce composant est réutilisé à deux endroits sur Android TV :
1. **Dans la grille verticale d'une catégorie spécifique :** La grille comporte 3 colonnes fixes. Chaque cellule a donc une largeur restreinte. `StreamTvCard` utilise un modificateur hardcodé `Modifier.fillMaxWidth()`, ce qui fonctionne parfaitement ici car la largeur est contrainte par la colonne de la grille.
2. **Dans le carrousel horizontal des catégories de la liste « Tout » :** Le conteneur est un `LazyRow` horizontal. N'ayant pas de contrainte de largeur de colonne, le modificateur `Modifier.fillMaxWidth()` de `StreamTvCard` force la carte à s'étirer pour prendre toute la largeur de l'écran du téléviseur.

Par conséquent :
- Le design en swimlanes (lignes de défilement horizontal) est complètement brisé visuellement : une seule chaîne énorme remplit l'écran horizontalement.
- L'icône étoile de favoris rapide (située à l'extrémité droite de la carte) se retrouve isolée au bord de l'écran.
- La navigation au D-pad devient confuse et imprévisible.
- L'utilisateur ne peut plus cibler ou voir le bouton favori rapidement dans cette vue d'ensemble.

---

# 3. Spécification fonctionnelle

## Objectif

Restaurer, dans le mode Android TV de la catégorie « Tout », des rangées horizontales de chaînes compactes et défilables, dont chaque carte offre une action Favori immédiatement visible et utilisable au D-pad sans déclencher la lecture.

## User stories

- En tant qu'utilisateur Android TV, je vois plusieurs chaînes côte à côte dans chaque rangée de catégories de « Tout » afin de parcourir rapidement le catalogue.
- En tant qu'utilisateur Android TV, je peux atteindre l'étoile d'une chaîne focusée et l'ajouter ou la retirer des favoris sans lancer cette chaîne.
- En tant qu'utilisateur Android TV, je continue de lancer une chaîne en validant sa carte, comme avant la correction.
- En tant qu'utilisateur, je conserve la même présentation en grille lorsque j'ouvre une catégorie précise ; la correction du mode « Tout » ne doit pas dégrader cet écran.

## Parcours utilisateur

1. L'utilisateur ouvre TV en direct sur Android TV et sélectionne la catégorie globale « Tout ».
2. Chaque rangée horizontale de catégorie — y compris la rangée « Favoris » lorsqu'elle est présente — affiche des cartes de chaînes de largeur constante. Plusieurs cartes sont visibles simultanément lorsque l'espace de l'écran le permet ; les autres sont accessibles par défilement horizontal.
3. L'utilisateur déplace le focus sur une carte. Son contour de focus et l'action étoile deviennent visibles dans les limites de cette carte.
4. La validation sur la carte lance la chaîne, sans modifier son état de favori.
5. Avec la flèche droite, l'utilisateur peut déplacer le focus de la carte vers l'étoile. La validation sur l'étoile ajoute la chaîne aux favoris ou la retire des favoris ; la chaîne ne se lance pas.
6. Après bascule, l'étoile reflète immédiatement le nouvel état et conserve le focus. La flèche gauche ramène le focus à la carte de la même chaîne ; le reste de la navigation entre cartes et rangées reste naturel.
7. Si la chaîne est retirée des favoris depuis la rangée « Favoris », elle disparaît immédiatement de cette rangée, tandis qu'elle reste disponible dans sa catégorie fournisseur. Si c'était le dernier favori, la rangée « Favoris » disparaît.
8. Lorsque l'utilisateur sélectionne une catégorie spécifique, les chaînes restent dans la grille verticale existante à trois colonnes, avec leur comportement de focus et de favori déjà disponible.

## Règles métier

- La correction est limitée à Android TV et au rendu des cartes de chaînes dans les rangées horizontales du mode « Tout ». Le rendu mobile n'est pas modifié.
- Une carte de chaîne dans une rangée horizontale a une largeur fixe de **220.dp** et conserve sa hauteur de 84.dp. Elle ne s'étend jamais à la largeur disponible du téléviseur.
- Une carte de chaîne dans la grille d'une catégorie spécifique conserve son remplissage de la cellule à trois colonnes ; elle ne reçoit pas la largeur fixe destinée aux rangées.
- L'étoile est affichée lorsque la carte est focusée ou lorsque la chaîne est déjà favorite. Elle doit rester entièrement visible dans la carte et ne pas chevaucher ni tronquer les informations principales au point de les rendre inutilisables.
- L'étoile est une cible de focus distincte de la carte. Valider la carte exécute uniquement la lecture ; valider l'étoile exécute uniquement le basculement du favori.
- L'action conserve les règles de favoris existantes : elle est locale au profil actif, persistante et la chaîne ne peut avoir qu'un seul état favori ou non favori.
- Le changement de favori est immédiatement reflété dans toutes les rangées TV visibles qui utilisent le même état, sans modifier le catalogue, l'EPG, les chaînes récentes ni les favoris d'un autre profil.

## Critères d'acceptation

- Dans « Tout » sur Android TV, chaque rangée de catégories affiche plusieurs cartes de 220.dp côte à côte lorsque l'écran le permet, et se parcourt horizontalement au D-pad.
- Aucune carte de rangée horizontale ne remplit seule la largeur de l'écran ; l'étoile n'est ni hors-écran ni inaccessible.
- Au focus d'une carte, l'étoile est visible. Elle reste visible quand la chaîne est favorite, même si la carte n'a plus le focus.
- Le D-pad permet d'atteindre l'étoile depuis la carte, de la valider pour basculer le favori sans lancer la chaîne, puis de revenir à la carte.
- La validation sur la carte continue de lancer la chaîne et ne modifie pas le favori.
- L'ajout ou le retrait met à jour immédiatement l'icône et les rangées concernées ; le retrait du dernier favori masque la rangée dédiée.
- La grille Android TV d'une catégorie précise conserve ses trois colonnes et ses cartes occupant la largeur de leur cellule.
- Le comportement et la présentation des cartes Live TV sur mobile restent inchangés.

## Cas limites

- Si une chaîne n'a ni logo ni EPG, la largeur fixe et l'étoile restent utilisables ; le contenu de substitution existant est affiché sans modifier le focus.
- Si le nom de la chaîne ou du programme est long, il peut être tronqué selon les règles visuelles existantes mais ne pousse jamais l'étoile hors de la carte.
- Si la liste de favoris est modifiée pendant que son étoile est focusée, la carte garde un focus valide lorsque possible ; si la carte disparaît parce qu'elle était le dernier élément de la rangée Favoris, le focus revient à un élément voisin disponible, jamais à une zone vide.
- Si un changement de profil se produit, le nouvel état de favoris du profil est affiché ; aucun favori de l'ancien profil ne doit rester visible par erreur.
- Les rangées sans chaînes ne sont pas affichées et ne créent pas de destination de focus vide.

## Gestion des erreurs

- Le changement de favori ne requiert aucun appel réseau ; une indisponibilité Xtream ou l'absence d'Internet ne doit pas empêcher une bascule locale déjà accessible.
- Si l'enregistrement local du favori échoue, l'étoile revient à son dernier état persistant et un message non technique invite l'utilisateur à réessayer. La chaîne ne doit pas être lancée par effet de bord.
- Si les données de la chaîne ou le profil actif deviennent indisponibles avant validation, la cible est désactivée ou l'action est ignorée sans crash et sans état visuel incohérent.

---

# 4. Décisions de périmètre

- La largeur fonctionnelle des cartes de rangées Android TV est fixée à **220.dp**.
- L'étoile est accessible comme cible D-pad distincte ; aucun geste alternatif de clic long n'est ajouté.
- Le correctif s'applique aux rangées du mode « Tout » — y compris « Favoris » — et préserve la grille de catégorie spécifique ainsi que le mobile.

---

# 5. Notes de spécification

- La maquette de référence ne décrit pas cette interaction Android TV spécifique. L'étape 3 détaillera l'adaptation de composant nécessaire en réutilisant les tokens de focus, de surfaces et de rayons existants dans `docs/design-reference/`.
