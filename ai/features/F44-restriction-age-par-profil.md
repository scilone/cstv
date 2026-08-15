# F44 - Restriction par âge sur un profil (contrôle parental)

## Informations générales

Status:
ANALYSIS

Created:
2026-08-15

Dépendances:
T22 (classification d'âge servie par le backend) — bloquant.

---

# 1. Description

Un profil local peut être **bridé sur un âge** (classification française : Tous
publics, 10, 12, 16, 18). Quand ce profil ouvre la fiche d'un film ou d'une
série, l'application récupère la classification d'âge de l'œuvre. Si elle
dépasse le niveau autorisé, la lecture est refusée et l'écran l'explique.

La règle est **défensive** : si la classification ne peut pas être déterminée
(œuvre inconnue de la source, service indisponible, appariement impossible), la
lecture est refusée.

Le déverrouillage ponctuel d'un contenu, comme la modification du niveau
autorisé, exige un **code PIN à 4 chiffres**.

---

# 2. Contexte

Le projet gère depuis la Phase 27 plusieurs profils locaux de type Netflix
(favoris, historique et reprise de lecture séparés), mais aucun n'a de
restriction de contenu : le catalogue IPTV, qui contient des catégories adultes
et des œuvres non adaptées, est intégralement accessible depuis un profil enfant.

TMDB expose des certifications par pays, y compris la classification française.
T22 rendant ces données accessibles via le backend avec cache partagé, la donnée
nécessaire devient disponible sans multiplier les appels.

**Écart de périmètre assumé.** AGENTS.md exclut explicitement « code PIN /
restriction parentale par profil » sauf demande explicite du PO. La demande est
faite et le PIN est retenu : AGENTS.md doit être mis à jour lors de la livraison.

---

# 3. Objectif

- Confier un profil à un enfant sans lui donner accès à l'ensemble du catalogue.
- Ne jamais autoriser par défaut : l'absence d'information vaut refus.
- Rendre la restriction non contournable depuis le profil bridé lui-même.
- Ne pas dégrader la navigation des profils non bridés (aucun surcoût, aucun
  appel supplémentaire).

---

# 4. Décisions produit prises à l'étape 1

| Sujet | Décision |
|---|---|
| Échelle d'âge | Classification française : Tous publics, 10, 12, 16, 18 — alignée sur les certifications FR de TMDB. |
| Visibilité des contenus interdits | Le média reste **visible** dans les listes et la recherche ; seule la lecture est bloquée, avec une explication. Pas de masquage ni de cadenas (la classification n'est connue qu'après consultation de la fiche). |
| Contenu non classifié | Lecture **refusée** (règle défensive), avec un message distinct de celui du contenu explicitement trop mature. |
| Déverrouillage | Code PIN à 4 chiffres, exigé pour débloquer ponctuellement un contenu **et** pour modifier le niveau autorisé d'un profil. Écart assumé au périmètre AGENTS.md, à répercuter dans le document. |
| Séries | Classification de la série entière ; pas de granularité par saison ni par épisode. |
| Chaînes en direct | Hors périmètre : aucune source de classification fiable pour le direct. |
| Plateformes | Mobile et Android TV dès la première livraison. |

---

# 5. Hypothèses

- T22 est livré et expose la classification d'âge d'un film ou d'une série,
  avec un cache serveur suffisant pour que la consultation d'une fiche
  n'introduise pas de latence perceptible.
- La certification française est disponible pour une part significative du
  catalogue ; à défaut, la règle défensive rendrait un profil bridé inutilisable.
  **À mesurer avant l'étape 3.**
- L'appariement œuvre ↔ source (T21/T22) est assez fiable pour ne pas bloquer
  massivement des contenus par simple échec de correspondance.
- Le PIN protège d'un enfant, pas d'un adversaire : un stockage local chiffré
  (DataStore chiffré, déjà en place pour les identifiants) suffit.
- Le PIN est un réglage d'appareil ou de compte, pas un secret synchronisé dans
  le cloud (à confirmer étape 2).

---

# 6. Questions ouvertes

| Question | À trancher à l'étape |
|---|---|
| Le PIN est-il unique pour l'appareil, ou propre à chaque profil non bridé ? | 2 |
| Un déverrouillage ponctuel vaut-il pour la seule lecture en cours, pour la session, ou définitivement pour ce média ? | 2 |
| Que se passe-t-il à la création d'un profil : niveau demandé d'emblée, ou profil non bridé par défaut ? | 2 |
| Que faire des contenus téléchargés hors ligne et de la reprise de lecture d'un média devenu interdit ? | 2 |
| Le blocage s'applique-t-il aussi aux bandes-annonces et aux vignettes d'aperçu ? | 2 |
| PIN oublié : quelle procédure de récupération (compte CSTV, réinitialisation de l'application) ? | 2 |
| Le réglage d'âge et le PIN sont-ils synchronisés dans le cloud avec le profil (F34) ? | 3 |
| Combien de tentatives de PIN avant temporisation ? | 3 |

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
