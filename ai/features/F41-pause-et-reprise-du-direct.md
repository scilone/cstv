# F41 - Pause et reprise du direct (tampon local)

## Informations générales

Status:
SPECIFICATION

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

## Décisions produit prises à l'étape 2

| Sujet | Décision |
|---|---|
| Pause au-delà de la profondeur du tampon | Reprise au point le plus ancien encore disponible, plutôt qu'un retour forcé au direct. |
| Interface de retour en arrière | Barre de progression à fenêtre glissante représentant le tampon disponible, pas des sauts fixes de N secondes. |
| Bouton « Revenir au direct » | Explicite, visible dès que la position n'est plus la plus récente, près des contrôles de lecture. |
| Application en arrière-plan / écran éteint | Le tampon continue d'enregistrer tant que la chaîne reste ouverte ; seule sa fermeture réelle le purge. |
| Espace disque insuffisant | Réduction automatique et silencieuse de la profondeur effective, sans désactiver la fonctionnalité ni afficher d'erreur. |

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
| Le tampon survit-il à un changement de qualité déclenché par F40 sur la même chaîne ? | 3 |
| Faisabilité réelle avec Media3 et le cache Media3 existant, sur flux HLS et flux TS bruts. | 3 |

---

# 7. Spécification fonctionnelle

## 7.1 User stories

- En tant qu'utilisateur qui regarde le direct, je veux pouvoir mettre sur
  pause pour répondre à une interruption, sans manquer ce qui se joue
  pendant ce temps.
- En tant qu'utilisateur qui vient de rater une action (but, réplique,
  scène), je veux revenir de quelques instants en arrière sans quitter le
  direct pour de bon.
- En tant qu'utilisateur en différé, je veux revenir au direct d'un geste
  simple dès que je le souhaite.

## 7.2 Parcours utilisateur

**Mise en pause et reprise**

1. Dès l'ouverture d'une chaîne, l'application enregistre le flux reçu dans
   un tampon local de 30 minutes par défaut (réglable dans les Paramètres).
2. L'utilisateur met la lecture en pause à tout moment.
3. Il la reprend : la lecture continue exactement où elle a été arrêtée,
   pas au direct.
4. Si la pause a duré plus longtemps que la profondeur du tampon (le point
   de pause a été évincé), la reprise se fait au point le plus ancien
   encore disponible dans le tampon (décision étape 2), pas un retour forcé
   au direct.

**Retour en arrière**

1. Pendant la lecture (en pause ou non), l'utilisateur ouvre une barre de
   progression à fenêtre glissante représentant le tampon disponible
   (jusqu'à 30 minutes, moins si la chaîne vient d'être ouverte ou si
   l'espace disque a réduit la profondeur effective — décision étape 2).
2. Il déplace le curseur vers un instant déjà diffusé et reprend la lecture
   à cet instant.
3. Tant que la position n'est pas la plus récente disponible, un bouton
   « Revenir au direct » reste visible près des contrôles de lecture
   (décision étape 2).
4. L'utilisateur appuie sur ce bouton pour revenir instantanément au direct.

**Continuité en arrière-plan**

1. L'utilisateur quitte l'écran de lecture (mise en veille, changement
   d'application) sans fermer la chaîne.
2. Le tampon continue d'enregistrer tant que la chaîne reste ouverte
   (décision étape 2).
3. À son retour, l'utilisateur retrouve le tampon intact : le direct et les
   30 dernières minutes (ou la profondeur réglée) sont toujours disponibles.
4. Fermer réellement la chaîne (quitter le lecteur, changer de chaîne) purge
   le tampon (décision étape 1) : rouvrir la même chaîne redémarre un
   tampon vide.

## 7.3 Règles métier

- L'enregistrement démarre dès l'ouverture de la chaîne, pas seulement au
  premier appui sur Pause (décision étape 1) — c'est ce qui permet le retour
  en arrière sans avoir mis en pause au préalable.
- Profondeur par défaut : 30 minutes, réglable dans les Paramètres (décision
  étape 1).
- Le tampon est strictement local à l'appareil et à la session de visionnage
  de la chaîne ; il ne dépend d'aucun service ni EPG du fournisseur.
- Le tampon est purgé au changement ou à la fermeture de la chaîne — jamais
  conservé entre deux chaînes différentes (décision étape 1, pour rester
  distinct d'un enregistrement PVR hors périmètre).
- Une réduction automatique de la profondeur effective, en cas d'espace
  disque insuffisant, ne désactive jamais la fonctionnalité (décision
  étape 2) : le retour en arrière reste possible sur une fenêtre plus
  courte que le réglage choisi.

## 7.4 Cas limites

- **Pause dépassant la profondeur du tampon** : reprise au point le plus
  ancien disponible (décision étape 2), pas un saut forcé au direct — voir
  7.2.
- **Réglage de la profondeur modifié dans les Paramètres pendant une chaîne
  déjà ouverte** : s'applique à la prochaine ouverture de chaîne, pas
  rétroactivement au tampon déjà en cours de constitution.
- **Zapping fréquent** : chaque changement de chaîne purge et redémarre le
  tampon (décision étape 1) — pas de retour en arrière possible dans les
  premières secondes suivant l'ouverture d'une nouvelle chaîne, le temps que
  le tampon se constitue.
- **Espace disque qui se libère après une réduction automatique** : la
  profondeur effective peut se réétendre progressivement vers le réglage
  choisi, sans action de l'utilisateur (détail exact du comportement
  progressif renvoyé à l'étape 3, sans impact sur le principe).

## 7.5 Critères d'acceptation

- Mettre en pause puis reprendre restitue exactement la même image, sans
  saut vers le direct.
- Une barre de progression à fenêtre glissante permet de revenir à un
  instant précis des dernières minutes diffusées, jusqu'à la profondeur
  réglée.
- Le bouton « Revenir au direct » est visible dès que la position n'est
  plus la plus récente, et ramène instantanément au direct.
- Quitter l'écran de lecture sans fermer la chaîne (arrière-plan, écran
  éteint) ne fait perdre aucune portion du tampon.
- Fermer la chaîne purge le tampon ; la rouvrir démarre un nouveau tampon
  vide.
- Un espace disque insuffisant réduit silencieusement la profondeur
  effective sans jamais désactiver la fonctionnalité ni afficher d'erreur.

## 7.6 Gestion des erreurs

- Espace disque insuffisant : dégradation silencieuse par réduction de la
  profondeur effective (décision étape 2), jamais de message d'erreur
  bloquant.
- Écriture sur le cache disque en échec (device plein malgré la réduction,
  erreur système) : le direct continue d'être lisible normalement, seule la
  fonctionnalité de pause/retour en arrière se dégrade — jamais
  d'interruption du direct lui-même pour une cause liée au tampon.
- Perte du flux source pendant l'enregistrement (déconnexion réseau) :
  traitée comme une erreur de lecture classique du direct (comportement
  existant), le tampon conserve ce qui a été enregistré jusque-là.

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
