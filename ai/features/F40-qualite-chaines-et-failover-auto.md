# F40 - Sélecteur de qualité des chaînes et mode automatique avec repli

## Informations générales

Status:
ANALYSIS

Created:
2026-08-15

Dépendances:
T21 (clé de liaison des chaînes) — bloquant.

---

# 1. Description

Un bouton « Qualité » dans le lecteur de direct liste les variantes de la chaîne
en cours (les entrées partageant sa clé de liaison T21 : `TF1 4K`, `TF1 FHD`,
`TF1 HD`, `TF1 SD`…) et permet d'en changer immédiatement. Le direct n'ayant pas
de position de lecture, la bascule est un simple changement de flux.

S'ajoute un **mode automatique** : l'application ouvre la meilleure qualité
disponible et, si le flux échoue ou se révèle instable, se replie sur la qualité
inférieure, puis de proche en proche. Si aucune variante n'est stable, elle se
fixe sur la moins mauvaise mesurée pendant les essais.

Le mode automatique n'est pas actif par défaut : un réglage dans les Paramètres
permet d'en faire le comportement par défaut de toutes les chaînes.

---

# 2. Contexte

La qualité des flux de direct est très inégale d'une variante à l'autre et varie
dans le temps : un flux 4K peut être parfait le matin et injouable le soir.
Aujourd'hui, l'utilisateur qui tombe sur un flux mort ou saccadé doit revenir à
la liste, deviner quelle autre entrée correspond à la même chaîne, et relancer —
en pleine émission.

Ce ticket transforme ce parcours en une action d'un geste, et offre à qui le
souhaite un repli entièrement automatique.

Le mode automatique reste opt-in : une bascule de flux non demandée pendant une
émission est perçue comme une panne, pas comme un service. Le réglage global
permet à l'utilisateur averti d'en faire son défaut.

---

# 3. Objectif

- Changer de qualité sur une chaîne sans quitter le lecteur.
- Offrir, en option, une lecture qui se répare seule sans intervention.
- Ne jamais s'acharner sur un flux défaillant, ni osciller entre deux flux.
- Quand rien ne fonctionne parfaitement, rester sur le flux le moins mauvais
  plutôt que sur un écran noir.

---

# 4. Décisions produit prises à l'étape 1

| Sujet | Décision |
|---|---|
| Activation du mode automatique | **Manuel par défaut.** Un réglage dans les Paramètres permet de choisir le mode automatique comme comportement par défaut. |
| Déclencheur du repli | Erreur de lecture **ou** plusieurs coupures de mise en mémoire tampon rapprochées (seuils à caler à l'étape 2). |
| Remontée en qualité | Aucune remontée automatique : la qualité stable est conservée jusqu'à ce qu'on quitte la chaîne. La meilleure qualité est retentée au prochain zapping. |
| Toutes les variantes instables | On se fixe sur la moins mauvaise mesurée pendant les essais (score fondé sur la réussite d'ouverture et le nombre de coupures), pas systématiquement sur la plus basse. |
| Périmètre | Chaînes en direct uniquement. Films et séries relèvent de F39. |
| Plateformes | Mobile et Android TV dès la première livraison. |

---

# 5. Hypothèses

- T21 regroupe correctement les variantes d'une chaîne, et l'ordre de qualité se
  déduit des attributs extraits (4K > FHD/1080p > HD > SD).
- Les indicateurs disponibles dans Media3 (erreurs de lecture, événements de mise
  en mémoire tampon, délai d'ouverture) suffisent à qualifier l'instabilité ; il
  n'est pas nécessaire de sonder le réseau séparément.
- Une bascule de flux coûte quelques secondes de noir : acceptable face à un flux
  défaillant, inacceptable sur un flux qui fonctionne — d'où l'absence de
  remontée automatique.
- Les mesures de stabilité n'ont pas besoin de survivre à la session : elles
  concernent des conditions réseau instantanées.
- Une chaîne sans variante n'affiche pas le bouton, ou l'affiche désactivé.

---

# 6. Questions ouvertes

| Question | À trancher à l'étape |
|---|---|
| Seuils exacts du déclencheur : combien de coupures, sur quelle fenêtre de temps ? | 2 |
| Le repli automatique doit-il être signalé à l'utilisateur (message bref) ou totalement silencieux ? | 2 |
| Choisir une qualité manuellement pendant une session en mode automatique désactive-t-il l'automatisme pour cette chaîne seulement, ou pour la session ? | 2 |
| Le mode automatique retient-il, au prochain zapping, la qualité qui avait fonctionné, ou repart-il toujours de la plus haute ? | 2 |
| Faut-il mémoriser une qualité préférée par chaîne et par profil, ou rester sans mémoire ? | 2 |
| Comment ordonner deux variantes dont les attributs sont identiques (ex. deux flux « HD ») ? | 3 |

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
