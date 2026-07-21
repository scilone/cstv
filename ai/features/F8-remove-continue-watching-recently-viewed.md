# F8 - Retirer des listes « Continuer à regarder » et « Récemment vus »

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

Cette fonctionnalité donne à l'utilisateur le contrôle total sur son historique de visionnage local. Elle permet de :
1. **Retirer manuellement un film ou une série** de la section **« Continuer à regarder »** (Reprendre) sur l'écran d'Accueil, l'écran des Films et l'écran des Séries.
2. **Retirer manuellement une chaîne de télévision** de la section **« Récemment vus »** (Chaînes récentes) sur l'écran TV en direct.

La suppression doit s'effectuer de manière intuitive et homogène à l'aide d'un geste universel (appui long / clic long), avec rafraîchissement réactif instantané de l'interface graphique sur mobile et sur Android TV.

---

# 2. Contexte

Actuellement, dès qu'un utilisateur lance la lecture d'un film, d'une série ou d'une chaîne de télévision, cet élément s'inscrit de manière permanente dans l'historique :
- Les films et séries apparaissent dans la ligne **« Continuer à regarder »** (reprise de lecture).
- Les chaînes apparaissent dans la ligne **« Récemment vus »** de l'onglet TV en Direct.

Il n'existe aujourd'hui aucun moyen pour l'utilisateur de nettoyer ces listes de l'historique. Cela pose plusieurs problèmes d'expérience utilisateur :
1. **Encombrement de l'Accueil :** L'écran d'Accueil et les têtes de catégories se retrouvent encombrés d'affiches de médias que l'utilisateur a simplement "testés" pendant quelques minutes sans intention de les poursuivre.
2. **Confidentialité :** Un profil partagé au sein d'un foyer ne peut pas masquer ou effacer un contenu qu'il a visionné.
3. **Erreurs de manipulation :** Un clic accidentel sur un média l'ajoute indéfiniment à sa liste de reprise.

Fournir une option de suppression manuelle de l'historique est une fonctionnalité standard essentielle pour redonner le contrôle aux utilisateurs et garder une interface épurée.

---

# 3. Spécification fonctionnelle

## Objectif

Permettre à un profil local de retirer lui-même un contenu de ses listes de reprise ou de chaînes récemment regardées, avec un comportement uniforme au tactile et au D-pad, persistant après redémarrage et immédiatement visible dans l'interface.

## User stories

- En tant qu'utilisateur, je peux retirer un film que j'ai seulement essayé de ma liste « Continuer à regarder » afin d'alléger mon Accueil.
- En tant qu'utilisateur, je peux retirer une série de ma reprise afin de remettre à zéro la progression de tous ses épisodes et de ne plus la voir suggérée comme à terminer.
- En tant qu'utilisateur TV, je peux retirer une chaîne de « Récemment regardées » afin de garder cette rangée privée et pertinente.
- En tant qu'utilisateur de profil local, je nettoie exclusivement mon historique : les autres profils conservent le leur.
- En tant qu'utilisateur, je dois confirmer la suppression afin de ne pas perdre une reprise par inadvertance.

## Parcours utilisateur

1. L'utilisateur repère une carte dans « Continuer à regarder » sur l'Accueil, l'écran Films ou l'écran Séries, y compris lorsque la rangée est affichée en vue étendue ; ou une carte de « Récemment regardées » sur l'écran TV en direct.
2. Sur mobile, il effectue un appui long sur la carte. Sur Android TV, il maintient le bouton central de validation alors que la carte est focusée. L'appui court conserve son comportement actuel : ouvrir ou reprendre le média/la chaîne.
3. L'appui long ouvre une boîte de dialogue adaptée à la plateforme, nommant le contenu et proposant **Annuler** et **Retirer de la liste**. Le choix par défaut au focus TV est **Annuler**.
4. **Annuler**, un retour système ou la fermeture du dialogue ne modifie aucune donnée et laisse la carte visible.
5. **Retirer de la liste** supprime l'historique du profil actif. Une fois l'opération réussie, le dialogue se ferme et la carte disparaît immédiatement de toutes les représentations visibles de la même liste.
6. Si la suppression laisse une liste vide, son titre et son carrousel disparaissent sans laisser d'espace vide. Le focus TV est déplacé vers un élément encore disponible, sans rester sur une carte supprimée.
7. Si l'utilisateur relance ultérieurement le média ou la chaîne, une nouvelle position ou entrée récente peut être créée selon les règles de lecture existantes.

## Règles métier

- La suppression est locale à l'appareil et strictement limitée au profil actif. Elle ne modifie ni le catalogue Xtream, ni l'historique, les favoris ou les reprises d'un autre profil.
- Pour un film, supprimer la carte supprime sa seule position de lecture du profil actif. Le film ne figure plus dans « Continuer à regarder » et une nouvelle lecture recommence sans reprise antérieure.
- Pour une série, la carte représente la série, indépendamment du dernier épisode affiché. La suppression retire toutes les positions de lecture de ses épisodes pour le profil actif ; les jauges de progression de tous les épisodes de cette série reviennent donc à l'état non commencé.
- Pour une chaîne Live TV, la suppression retire uniquement l'entrée « récemment regardée » de cette chaîne pour le profil actif ; elle ne modifie pas les favoris de chaînes, les préférences de catégories ni les données EPG.
- Retirer un contenu de « Continuer à regarder » le retire de l'historique utilisé pour le profil de goûts. Il peut redevenir éligible aux recommandations personnalisées ; un éventuel vote négatif F7 conserve toutefois son exclusion absolue.
- Chaque suppression de film ou série invalide les recommandations personnelles mises en cache afin qu'elles reflètent le nouvel historique au prochain rendu de l'Accueil.
- La suppression ne supprime jamais un téléchargement hors-ligne, son fichier physique, un favori, un vote J'aime/Je n'aime pas ou une métadonnée de catalogue.
- La fonctionnalité concerne uniquement les cartes présentes dans les listes ciblées ; elle n'ajoute pas de commande de suppression à l'historique global ni aux fiches de détail.

## Critères d'acceptation

- Les cartes de reprise de l'Accueil, Films et Séries, y compris leur vue étendue, ainsi que les cartes « Récemment regardées » de Live TV, prennent en charge l'appui long sur mobile et Android TV.
- Un appui court ne change aucun comportement de navigation ou de lecture existant.
- Le dialogue de confirmation propose toujours Annuler et Retirer de la liste ; Annuler ne provoque aucune écriture locale.
- Après confirmation, le film, la série ou la chaîne disparaît sans rechargement manuel ni changement d'onglet ; la rangée entière disparaît lorsqu'elle ne contient plus aucun élément.
- Supprimer un film efface uniquement sa reprise du profil actif. Supprimer une série efface toutes ses positions d'épisodes du profil actif et réinitialise leurs indicateurs de progression.
- Après redémarrage de l'application, les éléments supprimés restent absents pour le profil concerné ; un autre profil conserve ses propres entrées.
- Les recommandations du profil ne tiennent plus compte des films et séries supprimés de la reprise et sont invalidées après confirmation.
- Aucune suppression n'efface les téléchargements, favoris, votes F7, données EPG ou contenu catalogue.

## Cas limites

- Si l'utilisateur tente un appui long sur une carte qui vient de disparaître à la suite d'une autre action ou d'un changement de profil, aucun dialogue d'action n'est présenté.
- Si plusieurs épisodes d'une même série ont une reprise, une seule confirmation supprime toutes leurs positions ; il n'existe pas de suppression d'un seul épisode depuis la carte de série agrégée.
- Une carte déjà supprimée dans une autre vue ne doit pas réapparaître dans une rangée encore affichée ; toutes les vues du profil reflètent la même donnée locale.
- Si un contenu supprimé reste visible brièvement parce qu'une liste est en cours de composition, il ne doit plus être sélectionnable après la confirmation et doit disparaître dès la mise à jour d'état suivante.
- Une lecture ultérieure du même film, épisode ou Live TV recrée normalement l'entrée de reprise/récente, sans restaurer les anciennes positions ou l'ancien ordre.
- Une série ou un film retiré, mais marqué `DISLIKE` par F7, reste exclu des recommandations lorsqu'il est relu ; retirer la reprise ne modifie pas ce vote.

## Gestion des erreurs

- La fonctionnalité n'exige aucun accès réseau : l'absence de connexion, une erreur Xtream ou une erreur TMDB ne doivent pas empêcher d'ouvrir le dialogue ni de supprimer une donnée locale.
- Si la suppression locale échoue, le dialogue est fermé, la carte et toutes ses progressions restent inchangées, et un message simple invite l'utilisateur à réessayer. Aucun message technique ni stack trace ne doit être affiché.
- Si l'actualisation des recommandations échoue après une suppression réussie, la suppression reste définitive ; l'Accueil conserve son dernier résultat valide jusqu'à une prochaine actualisation.
- Si le profil actif ou l'identifiant du contenu devient indisponible avant confirmation, l'action est annulée sans modification de données.

---

# 4. Décisions de périmètre

- Le dialogue de confirmation est retenu pour le MVP, plutôt qu'une suppression immédiate avec action « Annuler », afin de rester fiable et accessible au D-pad.
- La suppression d'une carte de série est globale à la série et remet à zéro les reprises de tous ses épisodes pour le profil actif.
- La commande est volontairement limitée aux rangées visées, y compris la vue étendue de « Continuer à regarder » ; aucun écran de gestion d'historique supplémentaire n'est créé.

---

# 5. Notes de spécification

- La maquette de référence ne définit pas encore de dialogue ou de geste de suppression pour ces cartes. L'étape 3 précisera les composants et l'intégration visuelle en réutilisant les tokens existants de `docs/design-reference/`, sans introduire de charte parallèle.
