# F43 - Détection et saut de l'intro des séries par empreinte audio

## Informations générales

Status:
ANALYSIS

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
| Quand lancer l'analyse : à la première lecture d'un épisode de la saison, en tâche de fond après plusieurs épisodes vus, ou sur demande ? | 2 |
| Que faire du tout premier épisode analysé, sans point de comparaison ? | 2 |
| Combien de temps le bouton reste-t-il affiché, et disparaît-il si l'utilisateur ne l'utilise pas ? | 2 |
| Faut-il permettre de signaler une détection erronée pour l'oublier ? | 2 |
| Un plafond de données mobiles doit-il conditionner l'analyse (Wi-Fi uniquement) ? | 2 |
| Choix de l'algorithme d'empreinte et faisabilité sans nouvelle dépendance lourde. | 3 |
| Validation de faisabilité (coût CPU, données, précision) avant engagement de l'étape 3. | 3 |

---

# 7. Spécification fonctionnelle

_À compléter — étape 2._

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
