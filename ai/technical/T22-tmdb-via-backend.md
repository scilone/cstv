# T22 - Centralisation des appels TMDB dans le backend

## Informations générales

Status:
ANALYSIS

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
| Les images d'affiches passent-elles aussi par le backend, ou l'application continue-t-elle de charger les URL d'images du fournisseur directement ? | 2 |
| Faut-il conserver un cache local dans l'application en plus du cache serveur, et pour quelle durée ? | 2 |
| Quelles durées de validité précises par type de donnée ? | 3 |
| L'appariement titre/année reste-t-il calculé dans l'application (T21) avec un simple relais de recherche, ou passe-t-il entièrement côté serveur ? | 3 |
| Migration de la clé TMDB vers les secrets de production (`~/.cstv-production.env`) et retrait de `local.properties`. | 3 |
| Faut-il une règle `-keep` ProGuard pour la nouvelle interface Retrofit (obligation AGENTS.md) et un versionnage du contrat backend ? | 3 |

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
