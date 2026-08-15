# F44 - Restriction par âge sur un profil (contrôle parental)

## Informations générales

Status:
SPECIFICATION

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

## Décisions produit prises à l'étape 2

| Sujet | Décision |
|---|---|
| Portée du PIN | Un seul PIN pour l'appareil, commun à tous les profils non bridés. |
| Durée du déverrouillage ponctuel | La lecture en cours uniquement — la restriction se réapplique à la relecture ultérieure du même média. |
| Création d'un profil | Non bridé par défaut ; le bridage est une action explicite ultérieure. |
| Contenus téléchargés hors ligne | Le niveau autorisé est vérifié **au moment du téléchargement** (impossible de télécharger un contenu au-dessus du niveau du profil) ; **pas de revalidation à la lecture** d'un fichier déjà téléchargé. **Écart assumé** : un contenu téléchargé avant que le profil ne soit bridé (ou avant un abaissement du niveau autorisé) reste lisible hors ligne sans PIN — contredit partiellement l'objectif « restriction non contournable », accepté comme limite connue de la V1 après confirmation explicite. |
| Bandes-annonces et vignettes | Seule la lecture du média principal est bloquée ; les bandes-annonces ne sont pas concernées par la restriction. |
| PIN oublié | Réinitialisation via le compte CSTV : l'utilisateur principal, ré-authentifié, réinitialise le PIN depuis les Paramètres. |

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
| Le réglage d'âge et le PIN sont-ils synchronisés dans le cloud avec le profil (F34) ? | 3 |
| Combien de tentatives de PIN avant temporisation ? | 3 |
| Comment vérifier le niveau autorisé au moment du téléchargement quand la classification n'est pas encore connue (délai T22) : bloquer le téléchargement par défensif, ou l'autoriser en l'absence d'information ? | 3 |
| Faut-il, à terme, une action explicite pour re-vérifier/purger les téléchargements existants après un abaissement du niveau autorisé d'un profil, pour réduire l'écart documenté ci-dessus (étape 2) ? | 3 |

---

# 7. Spécification fonctionnelle

## 7.1 User stories

- En tant que parent, je veux confier un profil à mon enfant sans lui
  donner accès à l'ensemble du catalogue.
- En tant que parent, je veux que l'absence d'information sur un contenu le
  bloque par défaut plutôt que de l'autoriser par erreur.
- En tant qu'enfant sur un profil bridé, je ne dois pas pouvoir contourner
  la restriction depuis mon propre profil, ni en modifiant le niveau
  autorisé ni en devinant le PIN d'un adulte.
- En tant qu'adulte du foyer, je veux débloquer ponctuellement un contenu
  pour mon enfant sans devoir changer durablement les réglages du profil.

## 7.2 Parcours utilisateur

**Ouverture d'une fiche depuis un profil bridé**

1. Le profil bridé ouvre la fiche d'un film ou d'une série.
2. L'application récupère la classification d'âge de l'œuvre (via T22).
3. Si la classification dépasse le niveau autorisé du profil, ou si elle
   est inconnue (règle défensive, décision étape 1), la lecture est
   refusée : un écran explique le refus, avec un message distinct selon
   qu'il s'agit d'un contenu explicitement trop mature ou d'un contenu non
   classifié.
4. Le média reste visible et accessible depuis les listes et la recherche
   (décision étape 1) ; seule la lecture est bloquée.

**Déverrouillage ponctuel**

1. Depuis l'écran de refus, un adulte saisit le PIN à 4 chiffres de
   l'appareil (décision étape 2 : un seul PIN, commun à tous les profils
   non bridés).
2. Le PIN correct débloque la lecture en cours uniquement (décision
   étape 2) : rouvrir plus tard le même média depuis ce profil redemande le
   PIN.

**Modification du niveau autorisé d'un profil**

1. Un adulte accède aux réglages du profil bridé.
2. Modifier le niveau autorisé exige la saisie du PIN (décision étape 1).
3. Le nouveau niveau s'applique immédiatement aux prochaines ouvertures de
   fiche ; il ne revalide pas rétroactivement les téléchargements déjà
   présents (voir 7.3).

**Création d'un profil**

1. Un profil est créé non bridé par défaut (décision étape 2) : aucune
   étape supplémentaire n'est ajoutée au parcours de création existant.
2. Le bridage se fait ensuite, explicitement, depuis les réglages du
   profil, au moment où l'utilisateur en a l'usage réel.

**Téléchargement depuis un profil bridé**

1. Le profil bridé tente de télécharger un film ou un épisode.
2. Si la classification de l'œuvre dépasse le niveau autorisé (ou est
   inconnue — règle défensive), le téléchargement est refusé au même titre
   que la lecture (décision étape 2).
3. Un contenu déjà présent sur l'appareil avant le bridage du profil, ou
   téléchargé avant un abaissement ultérieur du niveau autorisé, reste
   lisible hors ligne sans revalidation (écart documenté en 7.3).

## 7.3 Règles métier

- Règle défensive : classification inconnue = lecture refusée, avec un
  message distinct de celui d'un contenu explicitement trop mature
  (décision étape 1).
- Échelle : Tous publics, 10, 12, 16, 18 (certifications françaises TMDB via
  T22) — décision étape 1.
- Visibilité : le média reste toujours visible dans les listes et la
  recherche, seule la lecture est bloquée (décision étape 1).
- Séries : classification de la série entière, pas de granularité par
  saison ou épisode (décision étape 1).
- Chaînes en direct hors périmètre (décision étape 1).
- PIN à 4 chiffres, unique par appareil, requis pour : débloquer
  ponctuellement une lecture, et modifier le niveau autorisé d'un profil
  (décisions étape 1 et 2).
- Le niveau autorisé est vérifié au téléchargement, pas revalidé à la
  lecture d'un contenu déjà téléchargé (décision étape 2) — voir écart
  assumé ci-dessous.
- Les bandes-annonces et les vignettes d'aperçu ne sont pas concernées par
  la restriction (décision étape 2) : seule la lecture du média principal
  est bloquée.
- PIN oublié : réinitialisation via le compte CSTV, après une nouvelle
  authentification de l'utilisateur principal (décision étape 2).

## 7.4 Cas limites et écarts assumés

- **Contenu téléchargé avant le bridage du profil, ou avant un abaissement
  du niveau autorisé** : reste lisible hors ligne sans PIN (décision
  étape 2, confirmée après signalement explicite de l'écart avec l'objectif
  « restriction non contournable »). Traité comme une limite connue de la
  V1, pas une omission — à documenter dans le contenu livré à l'utilisateur
  final (aide, notes de version) si le PO le juge utile à l'étape 9.
- **Classification indisponible au moment du téléchargement** (T22 non
  encore répondu, cache serveur froid) : traitement exact renvoyé à
  l'étape 3 (voir Questions ouvertes) — la règle défensive suggère un refus
  par défaut, cohérent avec le reste de la fonctionnalité.
- **Œuvre dont l'appariement T21/T22 échoue** (pas de correspondance
  trouvée) : traitée comme une classification inconnue, donc refusée
  (règle défensive, décision étape 1) — risque déjà identifié comme
  hypothèse à mesurer avant l'étape 3.
- **Profil bridé après que du contenu a déjà été visionné mais pas
  téléchargé** : la reprise de lecture en streaming applique la nouvelle
  restriction immédiatement, à la différence du hors ligne.
- **PIN saisi incorrectement plusieurs fois** : nombre de tentatives et
  temporisation renvoyés à l'étape 3.

## 7.5 Critères d'acceptation

- Un profil bridé ne peut pas lire un contenu dont la classification
  dépasse son niveau autorisé, ni un contenu non classifié.
- Le message affiché distingue un contenu explicitement trop mature d'un
  contenu non classifié.
- Le média reste visible dans les listes et la recherche depuis un profil
  bridé, seule sa lecture est bloquée.
- Le PIN correct débloque la lecture en cours uniquement ; rouvrir le même
  média plus tard redemande le PIN.
- Modifier le niveau autorisé d'un profil exige le PIN.
- Un profil nouvellement créé est non bridé.
- Un téléchargement au-dessus du niveau autorisé d'un profil bridé est
  refusé au moment de la demande.
- La réinitialisation du PIN passe par une nouvelle authentification du
  compte CSTV.

## 7.6 Gestion des erreurs

- Service de classification indisponible (T22 en échec) au moment
  d'ouvrir une fiche : traité comme une classification inconnue — lecture
  refusée par défaut (règle défensive), jamais un accès autorisé par erreur
  réseau.
- PIN incorrect : message clair de refus, sans indiquer si le profil ou le
  PIN lui-même est en cause — pas de stack trace, pas de détail technique
  (AGENTS.md § Gestion des erreurs).
- Échec de la réinitialisation du PIN via le compte CSTV (identifiants
  invalides, service injoignable) : message explicite, le PIN existant
  reste actif tant que la réinitialisation n'a pas abouti.

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
