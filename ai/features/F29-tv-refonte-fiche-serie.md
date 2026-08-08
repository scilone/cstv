# F29 - TV Refonte Fiche Série

## Informations générales

Status:
IDEA

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

# 4. Hypothèses

- **H1** : Le changement d'écran ou la transition "cran complet" vers les saisons/épisodes peut être gérée au sein du même composable `SeriesDetailsTvLayout` à l'aide d'un état d'affichage (ex: `enum class TvSeriesScreenState { Hero, Episodes }`) ou par une translation verticale complète (`translationY` animée de la hauteur de l'écran).
- **H2** : Les modèles de données existants (`SeriesDetails`, `SeriesEpisode`) contiennent déjà toutes les métadonnées requises pour les épisodes (vignettes, résumés, positions de reprise `resumePositionMs` et durées `durationMs`).
- **H3** : L'utilisation de `rememberTvInitialFocus` permettra d'affecter le focus initial sur le bouton de lecture principal lors de l'arrivée sur l'écran Hero, et de cibler la saison/le premier épisode lors de la transition vers l'écran Épisodes.

---

# 5. Questions ouvertes

1. **Bouton de retour physique** : Depuis l'écran des épisodes, la touche Retour de la télécommande doit-elle faire revenir à l'écran d'accueil Hero de la série, ou fermer complètement la fiche pour revenir à la navigation précédente ?
   - *Option recommandée* : Retour vers l'écran Hero si on est sur les épisodes, puis retour vers l'écran précédent si on est déjà sur l'écran Hero.
2. **Design de la barre de progression** : Quel est le style exact de la barre de progression sous le bouton de lecture ? Doit-elle utiliser `LinearProgressIndicator` avec `AccentLavande` et un fond discret, ou un tracé personnalisé plus fin ?
3. **Comportement des vignettes d'épisodes** : Si le serveur ne fournit pas de vignettes individuelles pour certains épisodes, doit-on afficher une image de substitution générique ou la pochette générale de la série ?
4. **Transition entre les saisons** : Quand on change de saison via les gélules horizontales, le focus doit-il automatiquement descendre sur le premier épisode de la nouvelle saison ?

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
