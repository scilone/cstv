# F39 - Étiquettes de version dans les listes et sélecteur de versions dans le lecteur

## Informations générales

Status:
ANALYSIS

Created:
2026-08-15

Dépendances:
T21 (titre nettoyé, attributs extraits, clé de liaison) — bloquant.

---

# 1. Description

Deux usages de la donnée produite par T21, pour les films et les séries :

1. **Étiquettes.** Les vignettes des listes affichent la langue et la qualité de
   la version (deux badges au maximum, ex. « VF · 4K »), pour choisir sans ouvrir
   la fiche.
2. **Sélecteur de versions dans le lecteur.** Un bouton « Version » liste les
   autres entrées du catalogue partageant la même clé de liaison. Le choix d'une
   autre version relance la lecture **à la même position**, sans repasser par la
   fiche.

Le sélecteur reprend l'emplacement et les codes des sélecteurs de pistes audio
et de sous-titres déjà présents dans le lecteur, sur mobile comme sur Android TV.

---

# 2. Contexte

Le catalogue contient plusieurs versions de la même œuvre, réparties dans les
listes sans lien visible entre elles. Aujourd'hui, changer de version impose de
quitter la lecture, revenir à la liste ou à la recherche, retrouver l'autre
entrée à l'œil et relancer depuis le début — la position de lecture mémorisée
étant propre à chaque entrée, elle est perdue.

Ce parcours est fréquent : on découvre en cours de lecture que la version lancée
n'est pas dans la bonne langue, que sa qualité est mauvaise, ou que son flux est
défaillant.

Les étiquettes attaquent le problème en amont (choisir la bonne version du
premier coup), le sélecteur en aval (corriger sans perdre sa place).

---

# 3. Objectif

- Depuis une liste, identifier la langue et la qualité d'une entrée sans l'ouvrir.
- Depuis le lecteur, voir toutes les versions disponibles de l'œuvre en cours.
- Basculer sur une autre version en conservant la position de lecture.
- Ne jamais laisser l'utilisateur devant un écran noir : un changement de version
  qui échoue revient à la version précédente.
- Pour une série, ne pas rechoisir sa version à chaque épisode.

---

# 4. Décisions produit prises à l'étape 1

| Sujet | Décision |
|---|---|
| Emplacement du sélecteur | Dans le lecteur uniquement, aux côtés des sélecteurs de pistes existants. Pas de sélection préalable depuis la fiche média. |
| Contenu des étiquettes | Langue **et** qualité, deux badges au maximum (ex. « VF · 4K »). Ni la qualité seule, ni un compteur de versions. |
| Échec d'un changement de version | Retour automatique à la version précédente, à la même position, avec un message bref. Pas d'écran d'erreur, pas d'enchaînement automatique vers une troisième version. |
| Mémorisation (séries) | La version choisie est mémorisée pour toute la série, par profil — cohérent avec la mémorisation existante des pistes audio et sous-titres (`TrackPreferenceEntity`). |
| Périmètre média | Films et séries. Les chaînes en direct relèvent de F40. |
| Plateformes | Mobile et Android TV dès la première livraison. |

---

# 5. Hypothèses

- T21 est livré et la clé de liaison est fiable : les versions listées désignent
  bien la même œuvre.
- La position de lecture est transposable telle quelle entre deux versions : les
  fichiers d'une même œuvre partagent approximativement le même montage et la
  même durée. Les écarts (génériques distribués, coupures) restent de l'ordre de
  quelques secondes.
- Le nombre de versions par œuvre reste faible : une liste simple suffit, sans
  recherche ni pagination.
- La place disponible sur une vignette permet deux badges sans masquer l'affiche
  ni casser la grille existante.
- Les attributs extraits sont suffisamment homogènes pour être affichés bruts,
  sans table de correspondance de libellés.

---

# 6. Questions ouvertes

| Question | À trancher à l'étape |
|---|---|
| Que faire quand une entrée n'a aucun attribut détecté : aucun badge, ou un badge neutre ? | 2 |
| Comment nommer chaque version dans la liste du sélecteur (attributs seuls, ou libellé Xtream d'origine) ? | 2 |
| Si la version cible est plus courte que la position courante, faut-il reprendre à la fin, au début, ou refuser ? | 2 |
| La version en cours de lecture doit-elle apparaître dans la liste, marquée comme active ? | 2 |
| Le sélecteur doit-il aussi apparaître dans le lecteur des contenus téléchargés hors ligne ? | 2 |
| Où mémoriser la version préférée d'une série : extension de `TrackPreferenceEntity` ou nouvelle table ? | 3 |

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
