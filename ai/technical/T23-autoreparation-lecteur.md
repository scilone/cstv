# T23 - Autoréparation du lecteur (essai de décodeurs et de pistes alternatifs)

## Informations générales

Status:
ANALYSIS

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
| Quel message final si toutes les tentatives échouent, et propose-t-il une action (réessayer, autre version via F39/F40) ? | 2 |
| La mémorisation est-elle par média, ou par profil et par média ? | 2 |
| Faut-il un moyen d'oublier la configuration mémorisée (bouton dans les Paramètres) ? | 2 |
| La réparation s'applique-t-elle aussi aux contenus téléchargés hors ligne ? | 2 |
| Comment articuler T23 et F40 sur une chaîne en direct : quel mécanisme s'essaie en premier ? | 3 |
| Où stocker la configuration mémorisée : extension de `TrackPreferenceEntity` ou nouvelle table ? | 3 |
| Comment tester la séquence de façon automatisée sans appareil connecté (contrainte AGENTS.md) ? | 3 |

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
