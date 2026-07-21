# F7 - Système d'évaluation J'aime / Je n'aime pas (Exclusion des recommandations)

## Informations générales

Type:
Feature

Status:
SPECIFICATION

Created:
2026-07-21

Target version:
v1.49.0

---

# 1. Description

Cette fonctionnalité introduit la possibilité pour l'utilisateur de marquer un film (VOD) ou une série par un vote positif ("J'aime" / Pouce levé) ou négatif ("Je n'aime pas" / Pouce baissé). 

L'objectif principal est de permettre un contrôle actif de l'utilisateur sur son profil de recommandations :
- Un média marqué **"Je n'aime pas"** doit être immédiatement exclu des sections "Recommandé pour vous" sur l'Accueil.
- Un média marqué **"Je n'aime pas"** doit également être ignoré lors du calcul de son profil de goûts (genres, catégories préférés), même s'il est présent dans l'historique de lecture (reprise de lecture).
- Un média marqué **"J'aime"** doit activement contribuer à renforcer positivement son profil de goûts, signalant un intérêt fort pour ses caractéristiques (genres, acteurs, réalisateurs).

---

# 2. Contexte

Le système de recommandation de l'application (introduit dans la Feature F-6 via `RecommendationEngine` et `GetRecommendationsUseCase`) repose aujourd'hui exclusivement sur l'historique de lecture (`PlaybackPositionEntity`). 

Cependant, l'historique de lecture seul comporte d'importantes limites :
1. **Faux positifs :** Un utilisateur peut démarrer un film d'horreur par curiosité, s'apercevoir au bout de 15 minutes qu'il déteste le film, et l'arrêter définitivement. Actuellement, cet item est mémorisé dans son historique et vient polluer positivement son score pour le genre "Horreur".
2. **Absence de retour explicite :** Il n'existe aucun moyen d'indiquer à l'application qu'un contenu de l'historique a été une déception ou une excellente surprise.
3. **Contrôle utilisateur :** L'utilisateur n'a aucun moyen de nettoyer ou de filtrer manuellement les suggestions qui lui sont faites sur l'écran d'Accueil, créant de la frustration s'il continue de se voir proposer des contenus similaires à ceux qu'il n'apprécie pas.

En fournissant un retour explicite (Thumbs Up/Down), on améliore considérablement la pertinence des recommandations locales de l'application.

---

# 3. Spécification fonctionnelle

## Objectif

Permettre à chaque profil local d'exprimer une préférence explicite sur un film ou une série, puis de l'utiliser immédiatement pour améliorer les recommandations personnelles, sans modifier le catalogue Xtream partagé ni appeler de service externe.

## User stories

- En tant qu'utilisateur, je peux indiquer sur la fiche d'un film ou d'une série que je l'aime ou ne l'aime pas afin que l'application comprenne mieux mes goûts.
- En tant qu'utilisateur, je peux annuler ou remplacer mon vote afin de corriger une préférence exprimée précédemment.
- En tant qu'utilisateur, je ne vois plus un contenu que j'ai rejeté dans les recommandations personnalisées de l'Accueil.
- En tant qu'utilisateur, un contenu aimé renforce les recommandations similaires, même si je ne l'ai pas encore beaucoup regardé.
- En tant qu'utilisateur de profil local, je ne vois ni n'influence les votes des autres profils de l'appareil.

## Parcours utilisateur

1. Depuis une fiche Film ou Série, sur mobile ou Android TV, l'utilisateur trouve deux actions clairement identifiées : **J'aime** et **Je n'aime pas**.
2. Sans vote existant, les deux actions sont inactives. L'utilisateur choisit l'une d'elles.
3. L'action choisie devient active et l'autre reste inactive ; le changement est confirmé visuellement par l'état sélectionné et une animation légère conforme à la charte de la refonte.
4. Un appui sur l'action active annule le vote et ramène le média à l'état neutre. Un appui sur l'autre action remplace directement le vote courant.
5. Après une modification réussie, les recommandations du profil sont recalculées : les rangées et la liste étendue « Recommandé pour vous » reflètent le nouveau choix lors de leur prochain affichage, y compris si l'Accueil est déjà ouvert.
6. Si l'utilisateur choisit **Je n'aime pas**, le média disparaît aussi de ses Favoris et de « Continuer à regarder » pour ce profil. Annuler ultérieurement ce vote ne restaure pas automatiquement ces éléments ; l'utilisateur peut les ajouter ou reprendre la lecture de nouveau.

## Règles métier

- Les votes ne concernent que les médias à la demande : films VOD et séries. Live TV, épisodes individuels, catégories et chaînes ne proposent pas cette action.
- Un vote est lié au profil local actif et à l'identifiant stable du média, en distinguant obligatoirement film et série. Un média ne possède qu'un état par profil : `NEUTRAL`, `LIKE` ou `DISLIKE`.
- Les votes sont exclusivement locaux à l'appareil ; ils ne sont ni envoyés à Xtream Codes ni partagés entre profils.
- `DISLIKE` est une exclusion absolue : le média ne peut jamais figurer dans une recommandation personnalisée de ce profil, quelle que soit sa popularité, son score ou sa présence dans l'historique.
- Un média `DISLIKE` est exclu de la construction du profil de goûts, même s'il existe encore une trace d'historique antérieure.
- `LIKE` apporte un signal positif fort au profil de goûts, même si la lecture est absente, très courte ou incomplète.
- Pour privilégier la découverte, les médias `LIKE` sont eux aussi exclus des recommandations personnalisées. Ils restent accessibles depuis le catalogue, les Favoris et l'Historique selon leur état propre.
- Les médias sans vote continuent de suivre les règles de recommandation existantes fondées sur l'historique et les préférences de catégories.
- Le passage à `DISLIKE` retire uniquement les données personnelles du profil concerné : favori et position de reprise du même type et identifiant. Il ne modifie pas le catalogue partagé, les téléchargements, ni les données des autres profils.
- La suppression d'un profil supprime également tous ses votes, au même titre que ses autres données personnelles.
- Lors de plusieurs actions rapides, le dernier choix confirmé par l'utilisateur est l'état retenu.

## Critères d'acceptation

- Les fiches de détail Film et Série affichent les deux actions sur mobile et Android TV, avec un état neutre, aimé ou non aimé lisible et accessible au focus TV.
- Un appui suit exactement le cycle : neutre → aimé/non aimé ; aimé → neutre ou non aimé ; non aimé → neutre ou aimé.
- Après fermeture et réouverture de l'application, chaque profil retrouve exactement ses votes ; un autre profil voit l'état neutre pour le même média s'il n'a pas lui-même voté.
- Un film ou une série `DISLIKE` n'apparaît dans aucune sortie « Recommandé pour vous » du profil, y compris la liste étendue, et ne contribue pas à ses goûts.
- Un film ou une série `LIKE` influence positivement les recommandations similaires mais n'est pas lui-même recommandé.
- Voter `DISLIKE` sur un favori le retire des Favoris et supprime sa reprise de lecture ; l'action ne touche pas aux autres profils.
- Une modification de vote invalide immédiatement les résultats de recommandation mis en cache, sans nécessiter de redémarrage ou de synchronisation Xtream.
- L'absence de connexion Internet n'empêche pas la consultation ou la modification des votes déjà accessibles localement.

## Cas limites

- Si un média n'est plus présent dans le catalogue après une synchronisation, son vote local peut être conservé jusqu'à la suppression du profil ; il n'a simplement aucun effet tant que le média n'est pas de nouveau recommandable.
- Si une série aimée ou rejetée comporte plusieurs épisodes, le vote s'applique à la série entière, pas à un épisode précis.
- Si le même identifiant numérique existe exceptionnellement pour un film et une série, leurs votes restent indépendants.
- Une reprise de lecture créée après un `DISLIKE` reste possible si l'utilisateur relance explicitement le média ; elle ne fait toutefois pas réintégrer ce média aux recommandations tant que le vote négatif est actif.
- Le vote ne crée pas automatiquement un favori ni une entrée d'historique.

## Gestion des erreurs

- La fonctionnalité ne dépend d'aucun appel réseau ; une indisponibilité Xtream ou TMDB ne doit pas empêcher un vote local.
- Si l'enregistrement local échoue, l'état affiché est restauré à sa dernière valeur persistée, les favoris et la reprise ne sont pas modifiés, et un message non technique informe l'utilisateur qu'il doit réessayer.
- Tant que le profil actif ou l'identifiant du média ne sont pas disponibles, les actions d'évaluation ne sont pas exécutables et aucun vote n'est créé.
- Une erreur de recalcul des recommandations ne doit jamais annuler un vote déjà sauvegardé ; l'Accueil conserve son dernier résultat valide et retentera son actualisation ultérieurement.

---

# 4. Décisions de périmètre

- La première version ne fournit pas d'écran global listant les contenus aimés ou rejetés ; cette évolution pourra faire l'objet d'une feature dédiée.
- Les deux décisions précédemment ouvertes sont retenues : les contenus `LIKE` sont exclus des recommandations pour favoriser la découverte, et un `DISLIKE` retire le favori ainsi que la reprise de lecture du profil.
- L'invalidation des recommandations après chaque vote est requise afin de rendre le changement perceptible immédiatement.

---

# 5. Notes de spécification

- La position exacte des boutons et leurs dimensions seront définies à l'étape 3 en s'appuyant sur les écrans de détail de `docs/design-reference/`. La maquette actuelle ne comporte pas encore de contrôle d'évaluation ; aucun nouveau token visuel n'est introduit à cette étape.
