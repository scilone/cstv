# T23 - Autoréparation du lecteur (essai de décodeurs et de pistes alternatifs)

## Informations générales

Status:
SPECIFICATION

Created:
2026-08-15

Dépendances:
Aucune. Complémentaire de F40 (repli de qualité côté chaînes) : T23 répare la
lecture d'un flux, F40 change de flux.

---

# 1. Description

Quand la lecture d'un média échoue, le lecteur tente automatiquement une
séquence de réparations avant d'abandonner :

1. bascule sur le **décodeur logiciel FFmpeg** (NextLib, déjà embarqué) ;
2. **désactivation de la piste fautive** identifiée par l'erreur ;
3. sélection d'une **autre piste audio** disponible.

La séquence est silencieuse : l'utilisateur ne voit qu'un temps de chargement.
La configuration qui a permis la lecture est **mémorisée pour ce média**, afin
que la lecture suivante démarre directement dans le bon état.

---

# 2. Contexte

Ce catalogue contient massivement des pistes AC3, EAC3 et DTS, dont le support
matériel varie fortement d'un appareil à l'autre — c'est la raison d'être de
NextLib dans le projet, et c'est ce qui a fait échouer Chromecast (F4, retiré
définitivement). Un même fichier peut être parfaitement lisible sur un téléphone
et muet ou en erreur sur une box TV.

Aujourd'hui, un échec de décodage se solde par un message d'erreur : l'utilisateur
conclut que le média est « mort », alors qu'un simple changement de décodeur ou
de piste audio suffirait souvent.

T23 est indépendant du flux : il ne change pas de source, il change la façon de
la lire. F40, à l'inverse, change de source sans toucher au décodage. Les deux
mécanismes doivent se coordonner pour ne pas se déclencher en même temps sur une
chaîne en direct.

---

# 3. Objectif

- Un média lisible au prix d'un réglage différent doit être lu, sans intervention.
- La réparation reste imperceptible : pas de jargon technique exposé.
- Une réparation réussie ne se rejoue pas à chaque lecture du même média.
- Un média réellement illisible échoue toujours, mais après un délai borné et
  avec un message clair.

---

# 4. Décisions produit prises à l'étape 1

| Sujet | Décision |
|---|---|
| Séquence de réparation | Décodeur logiciel FFmpeg → désactivation de la piste fautive → autre piste audio. Pas de cascade étendue (redémarrage de flux, changement de conteneur). |
| Visibilité | Silencieuse. L'utilisateur ne voit qu'un chargement, aucun indicateur « tentative de réparation ». |
| Mémorisation | La configuration gagnante est mémorisée pour ce média et réappliquée aux lectures suivantes. |
| Périmètre | Films, séries et chaînes en direct — tout média passant par le lecteur. |
| Plateformes | Mobile et Android TV dès la première livraison. |

## Décisions produit prises à l'étape 2

| Sujet | Décision |
|---|---|
| Message final (échec complet) | Message bref indiquant que le contenu est illisible sur cet appareil, avec un bouton « Réessayer » uniquement. Pas de renvoi vers un sélecteur de version/qualité (F39/F40) : T23 reste découplé de ces tickets, qui changent de flux plutôt que la façon de le lire. |
| Portée de la mémorisation | Par média seul, au niveau de l'appareil — pas par profil. La cause d'un échec de décodage est matérielle et propre au fichier, pas au profil qui regarde ; une seule configuration par (appareil, média), partagée entre tous les profils locaux. |
| Oubli manuel de la configuration | Aucun moyen manuel en V1. La mémoire se corrige d'elle-même si un nouvel échec net se déclenche. |
| Contenus téléchargés hors ligne | Couverts par le même mécanisme : le lecteur (Media3/NextLib) rejoue un fichier local avec les mêmes causes possibles d'échec de décodage qu'en streaming. |

---

# 5. Hypothèses

- Les erreurs remontées par Media3 sont assez précises pour distinguer un échec
  de décodage d'un échec réseau, et pour identifier la piste responsable. À
  défaut, la séquence se déroule à l'aveugle, ce qui reste acceptable.
- NextLib (`nextlib-media3ext`) couvre les codecs audio problématiques du
  catalogue ; il n'est pas nécessaire d'ajouter une dépendance.
- Une réinitialisation du lecteur avec une autre configuration coûte quelques
  secondes : la séquence complète reste sous un délai acceptable avant abandon.
- La configuration gagnante est stable dans le temps pour un média donné sur un
  appareil donné : elle dépend du fichier et du matériel, pas des conditions
  réseau.
- La mémorisation est propre à l'appareil et n'a pas à être synchronisée dans le
  cloud.

---

# 6. Questions ouvertes

| Question | À trancher à l'étape |
|---|---|
| Comment articuler T23 et F40 sur une chaîne en direct : quel mécanisme s'essaie en premier ? | 3 |
| Où stocker la configuration mémorisée : extension de `TrackPreferenceEntity` ou nouvelle table ? | 3 |
| Comment tester la séquence de façon automatisée sans appareil connecté (contrainte AGENTS.md) ? | 3 |

---

# 7. Spécification fonctionnelle

## 7.1 Résultat attendu

Un média qui échouait auparavant avec un message d'erreur se lit désormais
directement, au prix d'un délai de chargement légèrement plus long la
première fois. Les lectures suivantes du même média démarrent instantanément
dans la configuration qui a fonctionné.

## 7.2 Parcours utilisateur

1. L'utilisateur lance la lecture d'un film, d'un épisode ou d'une chaîne.
2. Si la lecture échoue pour une cause de décodage (piste ou codec), le
   lecteur relance automatiquement la lecture avec le premier ajustement de
   la séquence (7.3), sans indicateur visible autre qu'un temps de
   chargement.
3. Si cet essai échoue à son tour, le lecteur passe à l'ajustement suivant,
   jusqu'à épuisement de la séquence.
4. Dès qu'un essai réussit, la lecture se poursuit normalement et la
   configuration gagnante est mémorisée pour ce média sur cet appareil
   (décision étape 2).
5. Aux lectures suivantes du même média sur le même appareil, le lecteur
   démarre directement avec la configuration mémorisée, sans rejouer la
   séquence d'essais.
6. Si tous les essais échouent, un message bref indique que le contenu est
   illisible sur cet appareil, avec un unique bouton « Réessayer » qui
   relance la séquence complète depuis le début (décision étape 2).

## 7.3 Règles métier

- Séquence de réparation, dans cet ordre, chaque étape n'étant tentée que si
  la précédente échoue :
  1. Bascule sur le décodeur logiciel FFmpeg (NextLib).
  2. Désactivation de la piste identifiée comme fautive par l'erreur remontée.
  3. Sélection d'une autre piste audio disponible.
- Aucune étape supplémentaire (pas de redémarrage de flux, pas de changement
  de conteneur) — décision étape 1.
- La séquence est silencieuse : aucun libellé technique (nom de décodeur,
  numéro de piste) n'est exposé à l'utilisateur à aucune étape.
- La configuration gagnante remplace toute configuration mémorisée
  précédente pour ce média — une seule configuration active par (appareil,
  média).
- Périmètre : films, séries, chaînes en direct et contenus téléchargés hors
  ligne (décision étape 2) — tout média rejoué par le lecteur de
  l'application.

## 7.4 Cas limites

- **Échec dès le premier essai pour une cause non liée au décodage** (ex.
  réseau, flux introuvable) : la séquence de réparation ne se déclenche pas
  — elle est réservée aux échecs de décodage identifiés comme tels (voir
  hypothèse étape 1 sur la précision des erreurs Media3). Le comportement
  d'erreur réseau existant s'applique sans changement.
- **Erreur remontée par Media3 imprécise** (impossible d'identifier la piste
  fautive) : la séquence se déroule à l'aveugle dans le même ordre fixe,
  conformément à l'hypothèse étape 1.
- **Bouton « Réessayer » qui échoue à nouveau** : la séquence complète est
  rejouée depuis le début à chaque appui, sans limite de tentatives
  manuelles.
- **Changement de configuration matérielle ou logicielle de l'appareil**
  (mise à jour système, par exemple) rendant une configuration mémorisée
  obsolète : sans moyen d'oubli manuel (décision étape 2), la correction se
  fait au prochain échec net de cette configuration, qui relance la
  séquence complète.

## 7.5 Critères d'acceptation

- Un média qui n'était lisible qu'avec un décodeur logiciel, une piste
  désactivée ou une autre piste audio se lit désormais sans intervention de
  l'utilisateur.
- La deuxième lecture d'un média réparé démarre directement dans la
  configuration gagnante, sans reproduire les échecs intermédiaires.
- Un média réellement illisible échoue après un délai borné (durée exacte
  des trois essais cumulés à définir étape 3) et affiche le message final
  avec le bouton « Réessayer ».
- Aucune étape de la séquence n'affiche de jargon technique à l'utilisateur.
- La réparation se comporte identiquement en streaming et en lecture d'un
  contenu téléchargé hors ligne.

## 7.6 Gestion des erreurs

- Toute erreur de lecture est d'abord qualifiée (décodage vs réseau vs flux
  introuvable) avant de déclencher ou non la séquence — seule une erreur de
  décodage la déclenche (voir 7.4).
- L'épuisement de la séquence ne doit jamais laisser un écran noir sans
  message : le message final et le bouton « Réessayer » sont systématiques
  (cohérent avec AGENTS.md § Gestion des erreurs — jamais de stack trace
  brute, toujours un état explicite).

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
