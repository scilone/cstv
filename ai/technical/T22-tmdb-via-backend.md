# T22 - Centralisation des appels TMDB dans le backend

## Informations générales

Status:
SPECIFICATION

Created:
2026-08-15

Dépendances:
Aucune. Bloquant pour F44 (restriction par âge).

---

# 1. Description

L'application interroge TMDB directement (`data/remote/api/TmdbApiService.kt`,
clé lue depuis `local.properties`). Cette tâche déplace **l'intégralité** de ces
appels derrière le backend CSTV (`backend/`, déjà déployé sur alwaysdata) :

- l'application n'appelle plus que le backend, avec son authentification
  existante ;
- le backend interroge TMDB, met en cache les réponses et sert tous les
  utilisateurs depuis ce cache ;
- la clé TMDB disparaît de l'application et de ses artefacts de build ;
- le contrat exposé à l'application est **indépendant du fournisseur** : changer
  TMDB pour une autre source ne nécessite plus de mise à jour de l'application.

---

# 2. Contexte

Trois limites de la situation actuelle :

1. **Volume d'appels.** Chaque installation interroge TMDB pour son propre
   compte : tendances, fiches, notes, bandes-annonces, appariement du catalogue.
   Les mêmes données sont retéléchargées par chaque appareil, et les quotas TMDB
   sont consommés sans mutualisation.
2. **Fournisseur figé dans l'application.** Le format TMDB est propagé jusqu'aux
   DTO. Changer de source imposerait une nouvelle version de l'application, avec
   le délai d'adoption que cela suppose.
3. **Clé embarquée.** La clé vit dans l'APK. Sa rotation impose une livraison.

Le backend existe déjà, avec authentification, quotas et durcissement HTTP
(T14, T16, T17, T18) : il fournit le socle nécessaire.

---

# 3. Objectif

- Aucun appel réseau de l'application vers TMDB, ni aucune clé TMDB dans l'APK.
- Le nombre d'appels sortants vers TMDB devient indépendant du nombre
  d'utilisateurs pour les données partagées.
- Le contrat backend est exprimé dans le vocabulaire du produit, pas dans celui
  de TMDB.
- Une indisponibilité du backend ne dégrade que les enrichissements, jamais la
  navigation ni la lecture.
- Aucun changement visible pour l'utilisateur en fonctionnement nominal.

---

# 4. Décisions produit prises à l'étape 1

| Sujet | Décision |
|---|---|
| Périmètre | **Tous** les appels TMDB existants, migrés d'un bloc : tendances, fiches, notes, bandes-annonces, appariement. Pas de migration progressive, pas de double chemin durable. |
| Backend injoignable | Dégradation silencieuse : cache local, puis absence d'enrichissement. Pas de repli sur un appel TMDB direct (qui imposerait de conserver la clé), pas de message d'erreur. |
| Cache serveur | Cache partagé entre tous les utilisateurs, avec des durées de validité différenciées selon le type de donnée (tendances : heures ; fiches et classifications : semaines). |
| Contrat d'API | Exprimé dans le vocabulaire du produit, sans exposer la forme des réponses TMDB. |
| Plateformes | Sans objet côté UI ; s'applique à mobile et TV par construction. |

## Décisions produit prises à l'étape 2

| Sujet | Décision |
|---|---|
| Images (affiches, jaquettes) | Chargées directement par l'application depuis le CDN d'images du fournisseur, sans passer par le backend. Le backend construit et renvoie l'URL complète (pas un chemin brut), donc l'app ne connaît pas le fournisseur — le contrat reste indépendant du fournisseur malgré ce contournement du proxy. Aucune clé n'est nécessaire pour charger une image (URLs publiques) : l'objectif « zéro clé dans l'APK » reste respecté. |
| Cache local applicatif | Conservé, en plus du cache serveur, mais à courte durée (quelques heures — détail exact à l'étape 3). Objectif : éviter de resolliciter le backend à chaque navigation dans une même session et améliorer la résilience aux coupures réseau courtes, sans dupliquer la logique fine de fraîcheur du cache serveur qui reste la source de vérité. |

---

# 5. Hypothèses

- L'hébergement alwaysdata supporte le volume d'appels et le stockage de cache
  nécessaires, sans dépasser les quotas du plan actuel.
- Les conditions d'utilisation de TMDB autorisent un relais serveur mutualisé
  avec cache. **À vérifier explicitement avant l'étape 3.**
- Le cache peut être partagé sans donnée personnelle : les requêtes portent sur
  des titres et des identifiants d'œuvres, pas sur des profils.
- Les données d'appariement (recherche par titre et année) sont assez répétitives
  entre utilisateurs pour que le cache soit efficace.
- L'authentification backend existante suffit ; aucun nouveau mécanisme d'accès
  n'est requis.
- La liste exhaustive des appels TMDB actuels est identifiable dans le dépôt
  (`TmdbApiService`, `TmdbCatalogMatcher`, écrans Accueil, fiches, bandes-annonces).

---

# 6. Questions ouvertes

| Question | À trancher à l'étape |
|---|---|
| Quelles durées de validité précises par type de donnée ? | 3 |
| L'appariement titre/année reste-t-il calculé dans l'application (T21) avec un simple relais de recherche, ou passe-t-il entièrement côté serveur ? | 3 |
| Migration de la clé TMDB vers les secrets de production (`~/.cstv-production.env`) et retrait de `local.properties`. | 3 |
| Faut-il une règle `-keep` ProGuard pour la nouvelle interface Retrofit (obligation AGENTS.md) et un versionnage du contrat backend ? | 3 |
| Durée exacte du cache local applicatif (décision étape 2 : « quelques heures ») et faut-il l'aligner sur les mêmes clés de cache que le serveur ou utiliser un TTL fixe indépendant ? | 3 |
| Quel contrat de réponse quand le backend n'a pas d'enrichissement à fournir (TMDB indisponible en interne, œuvre non trouvée) : code HTTP dédié, champ « statut » explicite, ou réponse vide ? | 3 |

---

# 7. Spécification fonctionnelle

## 7.1 Résultat attendu

En fonctionnement nominal, aucun changement perceptible pour l'utilisateur :
mêmes écrans, mêmes données affichées. Ce qui change est invisible — la
source des données (backend CSTV au lieu de TMDB direct) — et se vérifie par
l'absence de tout appel réseau de l'app vers un domaine TMDB et l'absence de
clé TMDB dans l'APK.

## 7.2 Parcours utilisateur (nominal)

- **Accueil (tendances, F1)** : la section tendances demande sa liste
  d'œuvres au backend au lieu de TMDB directement. Aucun changement visible
  à l'écran.
- **Fiche film/série** : les enrichissements TMDB (note, bande-annonce)
  proviennent d'un appel backend paramétré avec le vocabulaire produit
  (titre, année, éventuellement identifiants du catalogue T21) plutôt que
  d'un appel direct à l'API TMDB.
- **Bande-annonce** : l'identifiant ou l'URL YouTube nécessaire à
  l'intégration du lecteur (périmètre validé AGENTS.md) provient de la
  réponse backend.
- **Appariement catalogue** (T21 → TMDB, utilisé pour F44 notamment) : la
  recherche par titre/année passe par un point d'entrée backend dédié, qui
  interroge TMDB et sert depuis son cache partagé.

## 7.3 Règles métier

- Migration en un seul bloc de tous les appels existants (décision étape 1) :
  après livraison, aucun code applicatif n'appelle plus directement l'API
  TMDB — seul le backend le fait.
- Le contrat backend expose des champs produit (ex. note, synopsis, URL de
  bande-annonce, URL d'affiche) plutôt que la structure brute des réponses
  TMDB, pour rester indépendant du fournisseur.
- Cache serveur partagé entre tous les utilisateurs, avec des durées
  différenciées par type de donnée (tendances : heures ; fiches et
  classifications : semaines — valeurs précises à l'étape 3).
- Cache local applicatif court terme en complément (décision étape 2),
  purgé automatiquement par expiration, jamais présenté comme à jour
  au-delà de sa durée de vie.

## 7.4 Cas limites

- **Backend injoignable ou en erreur** : dégradation silencieuse (décision
  étape 1) — l'écran s'affiche sans les enrichissements concernés, sans
  bandeau d'erreur, sans jamais bloquer la navigation ni la lecture. Si le
  cache local contient encore une réponse valide, elle est utilisée en
  attendant.
- **Backend joignable mais TMDB indisponible côté serveur** : le backend
  applique lui-même la dégradation silencieuse en interne (répond sans
  l'enrichissement plutôt que de propager une erreur à l'app) — contrat de
  réponse exact renvoyé à l'étape 3.
- **Œuvre absente de TMDB** (pas de correspondance trouvée) : réponse
  backend distincte d'une erreur technique. L'app affiche la fiche sans
  enrichissement, sans retenter en boucle.
- **Cache serveur froid** pour une donnée jamais demandée : la latence de
  l'appel TMDB initial est assumée par le premier appelant ; les suivants
  bénéficient du cache. Aucun préchauffage explicite prévu en V1.

## 7.5 Critères d'acceptation

- Aucun artefact de build (APK) ne contient de clé TMDB ni n'émet de requête
  réseau vers un domaine TMDB.
- Les écrans Accueil (tendances), fiche film/série et bande-annonce
  fonctionnent à l'identique en usage nominal, sans changement perceptible.
- Une indisponibilité du backend n'empêche jamais l'affichage d'une fiche,
  la navigation ni la lecture — seuls les enrichissements TMDB sont absents.
- L'appariement catalogue (T21) obtient ses correspondances via le backend,
  sans régression du taux de correspondance par rapport au comportement
  actuel (appel TMDB direct).

## 7.6 Gestion des erreurs

- Timeout ou erreur réseau vers le backend : traité comme une
  indisponibilité (dégradation silencieuse), jamais de message d'erreur
  technique affiché à l'utilisateur (cohérent avec AGENTS.md § Gestion des
  erreurs, même si ce flux reste secondaire par rapport à l'authentification
  ou à la lecture).
- Réponse backend malformée : traitée comme un enrichissement absent,
  journalisée côté application pour diagnostic (log, jamais affichée à
  l'utilisateur).

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
