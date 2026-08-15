# F43 - Détection et saut de l'intro des séries par empreinte audio

## Informations générales

Status:
SPECIFICATION

Created:
2026-08-15

Dépendances:
Aucune. Ticket le plus exploratoire du lot, livré en dernier.

---

# 1. Description

Pendant la lecture d'un épisode de série, l'application détecte le générique
d'ouverture et affiche un bouton **« Passer l'intro »** le temps de sa durée.

La détection repose sur une **empreinte audio calculée localement** : le
générique est le segment sonore commun à plusieurs épisodes d'une même saison.
Aucun service externe n'est sollicité.

---

# 2. Contexte

Les génériques durent souvent une à deux minutes et se répètent à chaque
épisode. En visionnage enchaîné, l'avance manuelle devient un réflexe fastidieux,
et la fonction est devenue un standard chez les plateformes de streaming.

Aucune source de métadonnées du projet ne fournit les bornes d'un générique :
ni Xtream, ni TMDB. L'information doit donc être déduite du contenu lui-même.

L'empreinte audio est retenue parce qu'elle est robuste aux différences
d'encodage entre versions d'un même épisode, là où une comparaison d'images
serait plus coûteuse et plus fragile.

Ce ticket est explicitement exploratoire : son coût en téléchargement et en
calcul sur box Android TV conditionne sa faisabilité, et le passage à l'étape 3
doit être précédé d'une validation technique.

---

# 3. Objectif

- Passer le générique d'un épisode d'un seul geste, sans chercher sa fin à la
  main.
- Ne jamais dégrader l'expérience : une détection ratée reste sans conséquence
  puisque le saut n'est jamais automatique.
- Rester entièrement local, sans nouvelle dépendance réseau.
- Garder le coût en données et en calcul sous contrôle.

---

# 4. Décisions produit prises à l'étape 1

| Sujet | Décision |
|---|---|
| Méthode de détection | Empreinte audio calculée localement, comparée entre épisodes d'une même saison. Ni service externe, ni heuristique fondée sur les avances manuelles de l'utilisateur. |
| Comportement à la détection | Bouton « Passer l'intro » affiché pendant le générique. Pas de saut automatique, même annulable, et pas d'option de saut automatique dans les Paramètres. |
| Périmètre média | Séries uniquement. Ni films, ni direct. |
| Portée de la détection | Générique d'ouverture. Le générique de fin n'est pas couvert. |
| Plateformes | Mobile et Android TV dès la première livraison. |

## Décisions produit prises à l'étape 2

| Sujet | Décision |
|---|---|
| Déclenchement de l'analyse | Dès la première lecture d'un épisode de la saison, en tâche de fond, sans action de l'utilisateur. |
| Premier épisode analysé | Aucun bouton « Passer l'intro » tant qu'aucun point de comparaison n'est disponible — pas d'estimation par défaut. |
| Durée d'affichage du bouton | Toute la durée du générique détecté, disparition automatique à la fin. |
| Signalement d'une détection erronée | Action simple disponible pour effacer la borne mémorisée et relancer l'analyse. |
| Restriction réseau | Aucune : l'analyse tourne quelle que soit la connexion (Wi-Fi ou données mobiles), sans réglage dédié. |

---

# 5. Hypothèses

- Le générique est bien identique d'un épisode à l'autre au sein d'une saison :
  vrai pour la plupart des séries, faux pour celles qui font varier leur intro.
- Il est possible d'extraire l'audio d'une portion d'un autre épisode sans
  télécharger le fichier entier (lecture par plage HTTP), et le panel supporte
  les requêtes partielles. **À valider avant l'étape 3.**
- Le calcul d'empreinte reste tenable sur box Android TV en tâche de fond,
  pendant la lecture, sans provoquer de saccades.
- Le générique se situe dans les premières minutes de l'épisode : l'analyse peut
  se limiter à cette fenêtre.
- Les bornes détectées pour une saison sont réutilisables pour tous ses épisodes,
  donc calculées une fois et mises en cache.
- Le résultat est propre à l'appareil et n'a pas à être synchronisé dans le cloud.

---

# 6. Questions ouvertes

| Question | À trancher à l'étape |
|---|---|
| Choix de l'algorithme d'empreinte et faisabilité sans nouvelle dépendance lourde. | 3 |
| Validation de faisabilité (coût CPU, données, précision) avant engagement de l'étape 3. | 3 |
| Où et sous quelle forme exposer l'action « Signaler une détection erronée » (menu contextuel sur la série, écran Paramètres, les deux) ? | 3 |

---

# 7. Spécification fonctionnelle

## 7.1 User stories

- En tant qu'utilisateur qui enchaîne les épisodes d'une série, je veux
  passer le générique d'ouverture d'un geste, sans avancer manuellement à
  l'aveugle.
- En tant qu'utilisateur qui commence une nouvelle saison, je veux que la
  détection s'affine au fil des épisodes sans que j'aie à la déclencher
  moi-même.
- En tant qu'utilisateur confronté à une détection erronée, je veux pouvoir
  la corriger sans attendre qu'elle se corrige toute seule.

## 7.2 Parcours utilisateur

1. L'utilisateur lance la lecture d'un épisode d'une série.
2. En tâche de fond, pendant la lecture, l'application calcule l'empreinte
   audio du début de l'épisode et la compare à celle des épisodes déjà
   analysés de la même saison (décision étape 2 : démarre dès la première
   lecture, pas d'attente d'un nombre minimal d'épisodes).
3. **Premier épisode analysé d'une saison** : aucun point de comparaison
   n'existe encore, donc aucun bouton « Passer l'intro » n'apparaît
   (décision étape 2) — pas d'estimation approximative.
4. **Dès qu'un second épisode de la même saison est analysé** et qu'un
   segment commun est détecté au début des deux, les bornes du générique
   sont déduites et mises en cache pour la saison entière.
5. Aux lectures suivantes d'un épisode de cette saison (y compris rétro-
   activement pour le premier épisode déjà vu), un bouton « Passer l'intro »
   s'affiche pendant toute la durée détectée du générique, puis disparaît
   automatiquement à sa fin (décision étape 2).
6. L'utilisateur appuie sur le bouton : la lecture saute directement à la
   fin du générique.
7. Si la détection est manifestement erronée (le bouton apparaît au mauvais
   moment, ou saute une partie du contenu), l'utilisateur dispose d'une
   action pour l'effacer et provoquer une nouvelle analyse (décision
   étape 2 ; emplacement exact renvoyé à l'étape 3).

## 7.3 Règles métier

- Analyse strictement locale, sans service externe, fondée sur une
  empreinte audio comparée entre épisodes d'une même saison (décision
  étape 1).
- Aucun saut automatique, même annulable : le bouton reste une action
  volontaire de l'utilisateur (décision étape 1).
- Une fois calculées, les bornes d'une saison sont réutilisées pour tous
  ses épisodes sans recalcul (décision étape 1) — sauf effacement explicite
  suite à un signalement d'erreur (décision étape 2).
- Aucune restriction réseau (Wi-Fi/données mobiles) ne conditionne
  l'analyse (décision étape 2) : elle se déclenche dans les mêmes
  conditions quelle que soit la connexion.
- Le résultat est propre à l'appareil, non synchronisé dans le cloud
  (hypothèse étape 1).

## 7.4 Cas limites

- **Série dont le générique varie d'un épisode à l'autre** : la comparaison
  ne trouve pas de segment commun fiable — aucun bouton n'apparaît plutôt
  que d'afficher une détection incorrecte (hypothèse étape 1 assumée comme
  limite connue).
- **Deux premiers épisodes visionnés dans le désordre** (ex. épisode 3 puis
  épisode 1) : la comparaison fonctionne dès que deux épisodes de la même
  saison ont été analysés, sans dépendre de l'ordre de visionnage.
- **Signalement d'une détection erronée puis nouvelle analyse toujours
  fausse** : aucune limite au nombre de signalements ; chaque signalement
  efface et relance, sans mécanisme de blocage après plusieurs échecs (V1).
- **Génériques de durée variable au sein d'une même saison** (rare) : les
  bornes mémorisées, calculées une fois, peuvent devenir imprécises pour un
  épisode atypique — corrigible via le signalement d'erreur, pas de
  détection par épisode individuel en V1 (hypothèse étape 1 : bornes
  réutilisables pour tous les épisodes de la saison).

## 7.5 Critères d'acceptation

- Aucun bouton « Passer l'intro » n'apparaît sur le tout premier épisode
  analysé d'une saison.
- Dès qu'un second épisode de la saison a été analysé avec succès, le
  bouton apparaît sur les épisodes suivants (et rétroactivement sur le
  premier, à sa prochaine lecture) pendant la durée exacte du générique
  détecté.
- Appuyer sur le bouton saute directement à la fin du générique détecté.
- Une action de signalement efface la détection mémorisée pour la saison et
  déclenche une nouvelle analyse.
- L'analyse se déclenche identiquement en Wi-Fi et en données mobiles.

## 7.6 Gestion des erreurs

- Échec du calcul d'empreinte pour un épisode (fichier illisible en tâche
  de fond, erreur de lecture par plage) : n'affecte pas la lecture normale
  de l'épisode en cours ; l'épisode est simplement ignoré pour la
  comparaison, sans bouton affiché tant qu'aucune autre paire ne fonctionne.
- Détection ratée ou absente : sans conséquence pour l'utilisateur au-delà
  de l'absence du bouton — jamais un saut incorrect, conformément à
  l'objectif « une détection ratée reste sans conséquence ».
- Détection signalée comme erronée : traitement immédiat de l'effacement,
  sans attendre une confirmation supplémentaire ni bloquer l'écran de
  lecture.

---

# 8. Spécification technique

_À compléter — étape 3._

---

# 9. Architecture

_À compléter — étape 3._

---

# 10. Plan de développement

_À compléter — étape 4._

---

# 11. Notes de développement

---

# 12. Review

## Critique

## Majeur

## Mineur

## Corrections demandées

---

# 13. Release

Version :

Commit :

Date :
