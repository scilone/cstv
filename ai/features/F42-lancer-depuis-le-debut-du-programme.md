# F42 - Lancer une chaîne depuis le début du programme (appui long)

## Informations générales

Status:
ANALYSIS

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
| Faut-il afficher le titre du programme en cours dans l'action (« Depuis le début de *…* ») ? | 2 |
| Le repli local, qui ne remonte pas au vrai début, doit-il être proposé sous le même libellé ou sous un libellé distinct ? | 2 |
| Que se passe-t-il à la fin du programme repris : bascule automatique au direct, ou poursuite en différé ? | 2 |
| L'action doit-elle aussi exister depuis le lecteur, sur la chaîne déjà en cours ? | 2 |
| Décalage EPG connu du panel : faut-il une marge de sécurité avant l'heure de début ? | 2 |
| Vérification du support réel du flux décalé par le panel, et forme exacte de l'URL. | 3 |

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
