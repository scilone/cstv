# F41 - Pause et reprise du direct (tampon local)

## Informations générales

Status:
ANALYSIS

Created:
2026-08-15

Dépendances:
Aucune. Bloquant partiel pour F42 (repli quand le panel n'expose pas de flux
décalé).

---

# 1. Description

Le lecteur de direct peut être mis en pause, puis repris là où il s'était
arrêté, avec un retour au direct à tout moment. Le retour en arrière dans ce
qui vient d'être diffusé est également possible.

L'enregistrement du tampon démarre **dès l'ouverture de la chaîne**, dans le
cache disque de l'application, sur une profondeur de **30 minutes réglable**
dans les Paramètres. Le tampon est purgé quand on quitte la chaîne.

La fonctionnalité est **entièrement locale** : elle ne dépend ni de l'EPG du
fournisseur, ni d'un service de reprise côté panel.

---

# 2. Contexte

Le direct est aujourd'hui strictement temps réel : quitter la pièce, c'est
manquer la scène. Le besoin est le plus fort sur le sport et l'information —
précisément les contenus où l'on veut aussi revoir une action.

Le choix d'un tampon local est délibéré : il ne dépend d'aucune capacité du
panel Xtream et fonctionne donc sur toutes les chaînes, y compris celles dont
l'EPG est absent ou faux.

**Écart de périmètre assumé.** AGENTS.md exclut « catch-up/timeshift » sauf
demande explicite du PO. La demande est faite : AGENTS.md doit être mis à jour
lors de la livraison.

---

# 3. Objectif

- Mettre le direct en pause et le reprendre sans perdre le fil.
- Revenir en arrière sur ce qui vient de passer, puis revenir au direct.
- Ne pas dépendre du fournisseur IPTV ni de la qualité de son EPG.
- Garder l'empreinte disque bornée, connue et réglable.
- Ne rien laisser derrière soi : le tampon disparaît quand on quitte la chaîne.

---

# 4. Décisions produit prises à l'étape 1

| Sujet | Décision |
|---|---|
| Début de l'enregistrement | Dès l'ouverture de la chaîne, ce qui autorise aussi le retour en arrière — pas seulement à partir de l'appui sur Pause. |
| Profondeur | 30 minutes par défaut, réglable dans les Paramètres. |
| Stockage | Cache disque de l'application, purgé en quittant la chaîne. Ni tampon en mémoire vive seule, ni conservation entre les chaînes (ce qui s'approcherait d'un enregistrement PVR, hors périmètre). |
| Dépendance à l'EPG | Aucune. Fonctionnalité entièrement locale. |
| Plateformes | Mobile et Android TV dès la première livraison. |

---

# 5. Hypothèses

- Media3 permet de conserver et de relire le flux de direct déjà reçu (cache de
  contenu déjà utilisé pour les téléchargements hors ligne, `data/download/`).
  **Hypothèse structurante : à valider techniquement avant l'étape 3.**
- L'écriture continue sur le cache disque reste tenable sur box Android TV : ni
  usure notable, ni saccades dues aux entrées/sorties.
- 30 minutes de flux HD représentent une empreinte disque acceptable (ordre du
  gigaoctet) et le réglage permet de l'ajuster sur les appareils contraints.
- Le zapping fréquent (purge et redémarrage du tampon à chaque changement de
  chaîne) n'introduit pas de latence perceptible.
- Un flux dont la lecture est différée reste valide côté panel : l'URL n'expire
  pas et la session ne se ferme pas parce que le lecteur ne consomme plus en
  temps réel.

---

# 6. Questions ouvertes

| Question | À trancher à l'étape |
|---|---|
| Que se passe-t-il quand le tampon est plein pendant une pause : reprise décalée du point le plus ancien disponible, ou retour forcé au direct ? | 2 |
| Quelle interface pour le retour en arrière : barre de progression avec fenêtre glissante, ou sauts de N secondes ? | 2 |
| Faut-il un bouton « Revenir au direct » explicite, et où l'afficher ? | 2 |
| Comportement quand l'application passe en arrière-plan ou que l'écran s'éteint : le tampon continue-t-il ? | 2 |
| Espace disque insuffisant : réduction automatique de la profondeur, ou désactivation avec message ? | 2 |
| Le tampon survit-il à un changement de qualité déclenché par F40 sur la même chaîne ? | 3 |
| Faisabilité réelle avec Media3 et le cache Media3 existant, sur flux HLS et flux TS bruts. | 3 |

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
