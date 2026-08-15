# F42 - Lancer une chaîne depuis le début du programme (appui long)

## Informations générales

Status:
SPECIFICATION

Created:
2026-08-15

Dépendances:
F41 (tampon local) pour le mode de repli. Le mode principal dépend du support
du flux décalé par le panel Xtream.

---

# 1. Description

Dans les listes de chaînes, un appui long sur une chaîne propose, en plus des
actions existantes, **« Lancer depuis le début du programme »**. L'action ouvre
la chaîne au début de l'émission en cours plutôt qu'au direct — cas d'usage
principal : arriver en cours de match ou de film.

Deux sources, par ordre de préférence :

1. le **flux décalé du panel Xtream**, quand il est disponible, qui permet de
   vraiment remonter au début du programme ;
2. à défaut, le **tampon local de F41**, qui ne remonte qu'au moment où la
   chaîne a été ouverte.

Si aucune des deux sources ne permet de remonter, l'action est proposée
désactivée ou absente.

---

# 2. Contexte

Le zapping arrive rarement au bon moment. L'EPG affiche déjà l'heure de début du
programme en cours : l'information nécessaire est là, mais aucune action ne
l'exploite.

L'appui long sur une chaîne est déjà un point d'entrée connu de l'application
(l'ajout et le retrait de favori s'y font depuis les correctifs récents), ce qui
en fait l'emplacement naturel pour cette action.

**Écart de périmètre assumé.** Comme F41, cette fonctionnalité relève du
catch-up/timeshift, exclu par AGENTS.md sauf demande explicite du PO. La demande
est faite : AGENTS.md doit être mis à jour lors de la livraison.

---

# 3. Objectif

- Rattraper le début d'un programme déjà commencé, d'un seul geste depuis la
  liste des chaînes.
- Fonctionner au mieux des capacités disponibles, sans jamais promettre ce qui
  n'est pas possible.
- Ne pas encombrer l'interface quand la fonctionnalité n'est pas disponible.

---

# 4. Décisions produit prises à l'étape 1

| Sujet | Décision |
|---|---|
| Source | Flux décalé du panel Xtream en priorité, repli sur le tampon local de F41. Action grisée ou masquée si aucune source ne permet de remonter. |
| Point d'entrée | Appui long sur une chaîne dans les listes, aux côtés des actions existantes. |
| Détermination du début du programme | EPG (déjà en cache, `EpgCacheEntity`). |
| Plateformes | Mobile et Android TV dès la première livraison. |

## Décisions produit prises à l'étape 2

| Sujet | Décision |
|---|---|
| Titre du programme dans l'action | Affiché quand l'EPG le fournit (« Depuis le début de *Nom du programme* »). |
| Libellé du repli local (F41) | Distinct du mode principal — ne promet pas le vrai début, seulement le moment d'ouverture de la chaîne. |
| Fin du programme repris | Poursuite en différé au-delà de la fin du programme, dans la limite de la source (flux décalé Xtream ou tampon F41) — pas de bascule automatique au direct. |
| Accessible depuis le lecteur | Oui, en plus de l'appui long dans les listes, sur la chaîne déjà en cours de lecture. |
| Marge de sécurité EPG | Une marge fixe modeste est appliquée avant l'heure de début annoncée, pour absorber les imprécisions connues de l'EPG (valeur exacte à l'étape 3). |

---

# 5. Hypothèses

- Le panel Xtream utilisé expose un mécanisme de flux décalé exploitable par
  l'API `player_api.php` ou par une URL dérivée. **Hypothèse la plus risquée du
  ticket : à vérifier sur le panel réel avant l'étape 3.** Si elle est fausse,
  la fonctionnalité se réduit au repli local, dont l'intérêt est nettement plus
  faible.
- L'EPG en cache donne une heure de début fiable pour le programme en cours ; un
  décalage de quelques minutes est acceptable, un décalage systématique ne l'est
  pas.
- L'appui long dispose encore de place pour une action supplémentaire sans
  surcharger le menu contextuel existant.
- Les chaînes sans EPG ne proposent pas l'action : le début du programme est
  alors inconnu.

---

# 6. Questions ouvertes

| Question | À trancher à l'étape |
|---|---|
| Vérification du support réel du flux décalé par le panel, et forme exacte de l'URL. | 3 |
| Valeur exacte de la marge de sécurité appliquée avant l'heure de début EPG. | 3 |
| Comment le contrôle de lecture se comporte-t-il en poursuite différée au-delà de la fin du programme (bouton « Revenir au direct » équivalent à celui de F41, barre de progression) ? | 3 |

---

# 7. Spécification fonctionnelle

## 7.1 User stories

- En tant qu'utilisateur qui arrive en cours de match ou de film, je veux
  lancer la chaîne depuis le début du programme en cours, pour ne rien
  avoir manqué.
- En tant qu'utilisateur déjà en train de regarder une chaîne, je veux
  pouvoir revenir au début du programme sans quitter le lecteur pour
  retourner à la liste.
- En tant qu'utilisateur sur un panel ou une chaîne sans flux décalé, je
  veux que l'action reste honnête sur ce qu'elle peut m'offrir, plutôt que
  de promettre un vrai début qu'elle ne peut pas fournir.

## 7.2 Parcours utilisateur

**Depuis les listes de chaînes**

1. L'utilisateur fait un appui long sur une chaîne dans une liste.
2. Le menu contextuel propose, en plus des actions existantes (favoris),
   « Depuis le début de *Nom du programme* » si l'EPG fournit un titre pour
   le programme en cours (décision étape 2), ou un libellé générique sinon.
3. L'utilisateur sélectionne l'action.
4. La chaîne s'ouvre au début du programme en cours :
   - si le panel expose un flux décalé exploitable, au vrai début du
     programme (avec la marge de sécurité EPG, décision étape 2) ;
   - sinon, si le tampon local F41 couvre déjà ce moment (chaîne ouverte
     depuis assez longtemps), au point le plus ancien qu'il contient, sous
     un libellé distinct annonçant explicitement ce repli (décision
     étape 2) ;
   - si aucune des deux sources ne permet de remonter, l'action n'apparaît
     pas ou est proposée désactivée (décision étape 1).

**Depuis le lecteur, sur la chaîne déjà ouverte**

1. Pendant la lecture d'une chaîne, l'utilisateur accède à la même action
   directement dans le lecteur (décision étape 2), sans revenir à la liste.
2. Le comportement de bascule est identique au parcours depuis les listes.

**Poursuite après la fin du programme**

1. Le programme rattrapé en différé se termine (heure de fin connue via
   l'EPG).
2. La lecture se poursuit en différé au-delà de cette fin plutôt que de
   basculer automatiquement au direct (décision étape 2), dans la limite de
   ce que permet la source (flux décalé du panel, ou tampon F41).
3. Le contrôle explicite du retour au direct (bouton dédié, comportement
   exact renvoyé à l'étape 3) reste à la main de l'utilisateur, à l'image du
   bouton « Revenir au direct » de F41.

## 7.3 Règles métier

- Deux sources par ordre de préférence : flux décalé du panel Xtream en
  priorité, repli sur le tampon local F41 sinon (décision étape 1).
- L'action est masquée ou désactivée si aucune des deux sources ne permet de
  remonter dans le temps pour cette chaîne (décision étape 1) — jamais
  proposée pour promettre un résultat impossible.
- Le repli local (F41) porte un libellé distinct du mode principal, pour ne
  jamais annoncer un vrai début de programme qu'il ne peut pas fournir
  (décision étape 2).
- L'heure de début du programme provient de l'EPG en cache
  (`EpgCacheEntity`), avec une marge de sécurité fixe avant cette heure pour
  absorber ses imprécisions connues (décision étape 2).
- L'action est accessible aux deux points d'entrée : appui long dans les
  listes de chaînes, et depuis le lecteur sur la chaîne déjà ouverte
  (décision étape 2).

## 7.4 Cas limites

- **Chaîne sans EPG** : aucune heure de début connue, l'action n'apparaît
  pas (décision étape 1, hypothèse étape 1).
- **Chaîne tout juste ouverte** (tampon F41 quasi vide) : le repli local ne
  peut remonter qu'à quelques secondes avant le direct — comportement
  attendu, pas une erreur ; si ce repli n'apporte aucun bénéfice réel par
  rapport au direct, l'action reste néanmoins proposée (elle reste correcte,
  juste peu utile dans ce cas précis).
- **Flux décalé du panel qui échoue à l'ouverture** malgré sa disponibilité
  annoncée : traité comme un échec de source, avec repli sur le tampon
  local F41 si disponible, sinon comportement d'erreur de lecture standard.
- **Programme en cours dont l'heure de début EPG est manifestement fausse**
  (ex. dans le futur) : l'action ne doit pas lancer une lecture à un
  instant qui n'existe pas encore — traitement exact renvoyé à l'étape 3.

## 7.5 Critères d'acceptation

- Sur une chaîne avec EPG et flux décalé supporté par le panel, l'action
  ouvre la chaîne au début réel du programme en cours (± la marge de
  sécurité).
- Sur une chaîne avec EPG mais sans flux décalé, l'action propose le repli
  local sous un libellé distinct, ouvrant au point le plus ancien du
  tampon F41.
- Sur une chaîne sans EPG, ou sans aucune des deux sources exploitables,
  l'action n'apparaît pas ou est visiblement désactivée.
- L'action est accessible à l'identique depuis l'appui long dans les listes
  et depuis le lecteur sur la chaîne déjà ouverte.
- À la fin du programme rattrapé, la lecture continue en différé plutôt que
  de basculer automatiquement au direct.

## 7.6 Gestion des erreurs

- Échec d'ouverture du flux décalé malgré sa disponibilité annoncée par le
  panel : repli automatique sur le tampon local F41 si la chaîne le permet,
  sinon message d'erreur de lecture standard — jamais d'écran noir sans
  explication.
- EPG absent ou incohérent au moment de l'appui long : l'action se comporte
  comme si aucune heure de début n'était connue (masquée ou désactivée),
  jamais un lancement à une heure incorrecte.

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
