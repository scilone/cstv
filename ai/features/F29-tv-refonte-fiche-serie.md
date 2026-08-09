# F29 - TV Refonte Fiche Série

## Informations générales

Status:
SPECIFICATION

Created:
2026-08-05

---

# 1. Description

Refonte majeure de la fiche détail d'une **Série (VOD)** sur **Android TV** d'après les maquettes et exigences fournies par l'utilisateur :

- **Écran d'Accueil / Arrivée (Main view)** :
  - Identique au design cinéma des films : affiche grand format plein bord occupant la moitié gauche de l'écran avec fondu dégradé horizontal vers la droite.
  - Colonne de droite avec le titre en très grand, ligne de métadonnées épurée (année | genres | note), synopsis, réalisateur, acteurs et rangée d'actions (favoris / j'aime / je n'aime pas) séparées par des filets.
  - Bouton principal de lecture large et arrondi, sans icône, texte seul :
    - Si aucun historique de lecture : **« LIRE LA SÉRIE »**.
    - Si reprise possible d'un épisode : **« REPRENDRE SXXEXX »** (ex: "REPRENDRE S01E02").
    - **Sous ce bouton** de reprise, afficher une **barre de progression** de l'épisode ciblé.
  - Le bloc « Titres associés » est **totalement masqué** à l'ouverture.

- **Transition vers le sélecteur de saisons / épisodes (Dpad DOWN)** :
  - Depuis le bouton principal de lecture de l'écran d'arrivée, un appui sur le bouton **Bas (Dpad DOWN)** fait glisser/baisser l'écran d'un cran complet pour afficher un second écran/panneau dédié au choix des épisodes.
  - Ce panneau contient :
    - Un sélecteur de saisons sous forme de **gélules (pills) horizontales**.
    - La liste des épisodes de la saison sélectionnée, affichés avec : numéro d'épisode, titre, description, vignette, et barre de progression de lecture si entamé.
    - Tout en bas de cette liste, le bloc **« Titres associés » est visible et dépasse (peek)**.

- **Interaction de fin de liste vers les Titres associés (Dpad DOWN au dernier épisode)** :
  - Si l'utilisateur navigue jusqu'au **dernier épisode** de la saison et appuie sur **Bas (Dpad DOWN)**, le bloc « Titres associés » remonte complètement pour devenir entièrement visible, le focus s'y déplace pour permettre un défilement horizontal.
  - Fleuve inverse (Dpad UP) depuis le bloc remonte le focus sur les épisodes.

La fiche **mobile** et la fiche **série mobile** ne sont pas modifiées.

---

# 2. Contexte

La fiche série TV actuelle (`SeriesDetailsScreen.TvLayout`) est encombrée : elle affiche l'affiche à gauche (240 dp) avec les saisons listées verticalement juste en dessous dans un menu étroit, tandis que la colonne de droite contient à la fois les informations globales de la série, le bouton de lecture d'épisode ciblé, et la liste verticale complète de tous les épisodes de la saison courante. Cette mise en page est dense et s'éloigne de la charte moderne de l'application (AccentLavande). De plus, l'accès aux épisodes est direct et rigide sans transition fluide de type "double couche" ou changement d'écran cinéma vers sélecteur d'épisodes.

---

# 3. Objectif

Créer une expérience de navigation cinéma immersive haut de gamme sur Android TV pour les séries :
1. Séparer l'expérience en deux phases/écrans virtuels : l'**Accueil de fiche** (Focus principal et esthétique cinéma) et le **Sélecteur d'épisodes** (Navigation fonctionnelle précise).
2. Offrir un indicateur visuel de progression de lecture direct sous le bouton de reprise de l'épisode d'accueil.
3. Fluidifier le passage entre le sélecteur d'épisodes et les recommandations (Titres associés) à l'aide d'un effet de remontée dynamique animée sur le dernier item.

---

# 4. Spécification fonctionnelle

## User story

En tant qu'utilisateur Android TV, lorsque j'ouvre une série, je veux arriver
sur une fiche cinéma lisible à distance, centrée sur l'action de lecture. Je
veux pouvoir accéder d'un appui Bas au choix de la saison et des épisodes, puis
aux titres associés sans perdre le contexte ni le focus de la télécommande.

## Périmètre

- La refonte concerne uniquement `SeriesDetailsScreen` en mode Android TV.
- La fiche série mobile, les données de série, la lecture, les téléchargements,
  les favoris, les notes et la sélection des titres associés gardent leurs
  comportements existants.
- La maquette de référence est la fiche série et la planche
  `refonte-fiche-serie-episodes.png` dans `docs/design-reference/`. Les tokens
  existants de la charte (notamment `AccentLavande` et les surfaces sombres)
  sont conservés.

## Vue Hero — arrivée sur la fiche

1. L'ouverture d'une série affiche la vue Hero, jamais directement le
   sélecteur d'épisodes.
2. La moitié gauche de l'écran est occupée par l'affiche grand format de la
   série, fondue horizontalement vers le fond sombre à droite. La colonne de
   droite contient, dans cet ordre : titre, métadonnées (année, genres, note),
   synopsis, réalisateur, acteurs, actions puis action principale de lecture.
3. Les actions Favoris, J'aime et Je n'aime pas restent disponibles avec leur
   état actuel et sont séparées visuellement par des filets. Les crédits
   restent sélectionnables et déclenchent la recherche existante.
4. Le focus initial est placé sur le bouton de lecture. Aucun bloc « Titres
   associés » n'est visible dans cette vue.
5. Si un épisode repris est disponible selon les règles de reprise existantes,
   le libellé est `REPRENDRE SXXEXX` et la lecture ouvre cet épisode à sa
   position mémorisée. Une barre fine lavande sur piste sombre est affichée
   immédiatement sous ce bouton ; sa proportion correspond à la progression de
   l'épisode repris, comme les barres visibles dans la liste d'épisodes de la
   maquette.
6. Sinon, le libellé est `LIRE LA SÉRIE` et lance le premier épisode disponible
   de la série. Aucune barre de progression n'est affichée dans ce cas.
7. Le bouton de lecture est large, arrondi et textuel : il n'affiche pas
   d'icône.

## Vue Épisodes — navigation depuis la vue Hero

1. Un appui D-pad Bas depuis le bouton de lecture bascule vers la vue Épisodes
   par une transition verticale d'un écran complet. La vue Hero quitte la zone
   visible ; elle n'est pas mélangée à la liste des épisodes.
2. Le focus arrive sur la gélule de la saison courante. Les gélules de saisons
   sont disposées horizontalement ; Gauche/Droite change de saison et Bas mène
   à la liste d'épisodes.
3. Chaque épisode affiche son numéro, son titre, sa description, sa vignette
   au format paysage et, lorsqu'il est entamé, une barre de progression lavande
   sur piste sombre avec l'information de reprise pertinente.
4. Une vignette d'épisode manquante est remplacée par un visuel neutre. La
   pochette générale de la série ne doit pas être réutilisée comme vignette.
5. À la sélection d'une autre saison, la liste est remplacée par les épisodes
   de cette saison et le focus descend automatiquement sur son premier épisode.
   Si cette saison ne contient aucun épisode, le focus reste sur sa gélule et
   l'état vide non interactif est affiché à la place de la liste.
6. OK sur un épisode lance cet épisode selon le comportement de lecture
   existant. La saison sélectionnée et l'épisode ciblé restent cohérents avec
   les informations affichées dans la liste.

## Titres associés et navigation inverse

1. Si des titres associés existent, leur rangée est placée après le dernier
   épisode et n'est visible qu'en aperçu au bas de la vue Épisodes.
2. Depuis le dernier épisode, un appui D-pad Bas fait remonter la vue de façon
   animée jusqu'à rendre entièrement visible la rangée « Titres associés », puis
   place le focus sur sa première vignette.
3. Gauche/Droite parcourt la rangée ; OK ouvre la fiche du titre choisi avec le
   comportement de navigation existant.
4. D-pad Haut depuis la rangée remet la vue à sa position de repos et replace
   le focus sur le dernier épisode de la saison.
5. Sans titre associé, aucune rangée, aucun aperçu ni remontée ne sont créés ;
   D-pad Bas au dernier épisode ne déclenche pas de déplacement artificiel.

## Retour, cas limites et erreurs

- La touche Retour quitte toujours la fiche série vers l'écran précédent, que
  l'utilisateur se trouve dans la vue Hero ou Épisodes. Elle ne revient pas au
  Hero depuis la vue Épisodes ; le retour de navigation interne se fait avec
  D-pad Haut.
- Sans épisode disponible dans la série, l'action de lecture ne lance rien et
  un état vide explicite est affiché dans la vue Épisodes. Les informations de
  la série et les actions de la vue Hero restent accessibles.
- L'absence d'affiche, de synopsis, de métadonnée, de crédits ou de note ne
  casse pas la composition : la zone concernée est omise ou utilise le visuel
  neutre existant, sans libellé de remplacement trompeur.
- La transition, le changement de saison et la remontée des titres associés ne
  doivent jamais provoquer de lecture automatique, de requête réseau
  additionnelle ni de perte de la sélection courante.

## Critères d'acceptation

- [ ] L'ouverture d'une série TV affiche exclusivement la vue Hero, avec
  l'affiche à gauche, les informations à droite et le focus sur l'action de
  lecture.
- [ ] Le bouton affiche exactement `LIRE LA SÉRIE` sans reprise, ou
  `REPRENDRE SXXEXX` avec une barre fine lavande sur piste sombre lorsqu'une
  reprise est possible ; il ne contient aucune icône.
- [ ] D-pad Bas depuis ce bouton affiche la vue Épisodes et focalise la saison
  courante.
- [ ] Une saison sélectionnée amène le focus sur son premier épisode ; une
  saison vide conserve le focus sur sa gélule et rend un état vide.
- [ ] Chaque épisode conserve ses données disponibles et utilise un visuel
  neutre si sa vignette est absente.
- [ ] D-pad Bas depuis le dernier épisode rend entièrement visible et focalise
  les titres associés lorsqu'ils existent ; D-pad Haut réalise le trajet
  inverse vers le dernier épisode.
- [ ] Retour quitte la fiche directement vers l'écran précédent depuis les deux
  vues.
- [ ] Les fiches série mobile et film ne changent pas.

---

# 5. Hypothèses à examiner à l'étape 3

- **H1** : Le changement d'écran ou la transition "cran complet" vers les saisons/épisodes peut être gérée au sein du même composable `SeriesDetailsTvLayout` à l'aide d'un état d'affichage (ex: `enum class TvSeriesScreenState { Hero, Episodes }`) ou par une translation verticale complète (`translationY` animée de la hauteur de l'écran).
- **H2** : Les modèles de données existants (`SeriesDetails`, `SeriesEpisode`) contiennent déjà toutes les métadonnées requises pour les épisodes (vignettes, résumés, positions de reprise `resumePositionMs` et durées `durationMs`).
- **H3** : L'utilisation de `rememberTvInitialFocus` permettra d'affecter le focus initial sur le bouton de lecture principal lors de l'arrivée sur l'écran Hero, et de cibler la saison/le premier épisode lors de la transition vers l'écran Épisodes.

---

# 6. Décisions fonctionnelles actées

1. Depuis la vue Épisodes, Retour ferme la fiche et revient à l'écran précédent.
2. La barre de reprise sous `REPRENDRE SXXEXX` est fine, lavande, sur une piste
   sombre, selon le langage visuel des épisodes de la maquette.
3. Une vignette d'épisode absente est représentée par un visuel neutre, jamais
   par la pochette de série.
4. Un changement de saison place automatiquement le focus sur l'épisode 1 de
   la saison nouvellement sélectionnée lorsqu'il existe.

---

# 6. Plan de développement (Ébauche)

*(À détailler lors de l'Étape 4)*
1. Création de `presentation/series/SeriesDetailsTvLayout.kt`.
2. Implémentation du mode cinéma initial (Hero) avec boutons de lecture "Lire" / "Reprendre" et la barre de progression.
3. Implémentation du glissement vertical de type "cran complet" au clic ou Dpad DOWN depuis le bouton de lecture.
4. Implémentation du sélecteur d'épisodes (gélules de saisons + grille/liste d'épisodes enrichie).
5. Ajout de la remontée dynamique du bloc de titres associés au Dpad DOWN depuis le dernier épisode.
6. Intégration dans `SeriesDetailsScreen.kt` et nettoyage de l'ancien code.
7. Validation et tests unitaires JVM des transitions.
