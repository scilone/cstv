# T29 - Débit batch, quotas de matching et retries fournisseur

## Informations générales

Status:
REVIEW

Created:
2026-08-21

Dépendances:
- F45 — Consolidation des données IPTV par métadonnées externes
- T22 — Centralisation des appels TMDB dans le backend
- T28 — Matching backend-first et réutilisation des métadonnées consolidées

Observation recommandée:
- F46 — Indicateur de couverture de l’enrichissement des médias

---

# 1. Description

Augmenter fortement le débit du backfill de métadonnées sans augmenter agressivement la pression sur TMDB.

Le backend doit distinguer :

1. le coût d’une requête CSTV servie depuis le cache/PostgreSQL;
2. le coût d’un véritable appel au fournisseur TMDB.

Aujourd’hui, le quota de matching est consommé par média avant de savoir si le média nécessitera réellement TMDB.

Un batch de médias déjà présents en PostgreSQL est donc limité comme un batch entièrement froid.

T29 doit :

- séparer le throttle anti-abus CSTV du budget fournisseur TMDB;
- faire du endpoint batch un traitement réellement orienté bulk;
- augmenter la taille des lots Android de 20 à 50;
- conserver le rate limit TMDB global à 4 req/s / burst 12 dans un premier temps;
- gérer les erreurs/rate limits fournisseur par média afin qu’un seul miss froid ne fasse pas échouer tout le batch;
- éviter qu’un retry technique soit persisté comme `UNRESOLVED`.

---

# 2. Contexte

Le endpoint actuel accepte jusqu’à 50 médias :

```text
POST /v1/catalog/matches/batch
```

mais l’application en envoie actuellement 20 par lot.

Le backend exécute ensuite essentiellement les éléments de manière séquentielle.

Le throttle applicatif actuel fonctionne en nombre de médias :

```text
120 médias / minute / compte
240 médias / minute / IP
```

et il est appliqué avant résolution.

Donc :

```text
20 hits PostgreSQL
```

et :

```text
20 médias nécessitant TMDB
```

consomment le même quota.

En parallèle, le fournisseur possède déjà son propre token bucket :

```text
4 requêtes TMDB / seconde
burst 12
```

Ces deux limites répondent à des besoins différents :

- throttle CSTV : protection anti-abus / charge HTTP;
- rate limiter TMDB : protection du token fournisseur partagé.

Les confondre limite artificiellement le débit des hits backend.

Autre problème : un batch provider qui rencontre une erreur globale peut actuellement reprogrammer tout le groupe côté Android.

Avec des lots plus grands, ce comportement devient coûteux.

---

# 3. Objectif

- Permettre au backend de traiter rapidement les hits cache/PostgreSQL.
- Faire en sorte que seuls les véritables appels TMDB consomment le token bucket TMDB.
- Réduire le nombre de requêtes HTTP Android ↔ backend.
- Utiliser la capacité actuelle du endpoint de 50 éléments.
- Éviter qu’un 429 ou une panne TMDB sur un élément invalide tout un batch.
- Conserver l’ordre des réponses.
- Reprogrammer uniquement les éléments réellement retryables.
- Ne jamais enregistrer un échec technique comme `UNRESOLVED`.
- Ne pas modifier le rate limit fournisseur tant que le gain backend-first n’a pas été mesuré.
- Préserver la consommation faible sur Android TV.

---

# 4. Décisions prises

## 4.1 Décisions prises à l’étape 1

| Sujet | Décision |
|---|---|
| Dépendance | T29 est livré après T28 afin que les gros batchs profitent d’abord du backend-first. |
| Rate limit TMDB | Conserver 4 req/s sustained / burst 12 initialement. |
| Accélération | Augmenter le débit via le backend et le batching avant de toucher au budget TMDB. |
| Batch Android | Passer de 20 à 50 éléments, ce qui correspond déjà au maximum accepté par l’API actuelle. |
| API | Conserver le même endpoint `/v1/catalog/matches/batch`. |
| Robustesse | Les erreurs fournisseur doivent être isolées par média. |
| F46 | Utiliser F46 pour observer le gain de traitement, sans créer de dépendance fonctionnelle. |

## 4.2 Décisions prises à l’étape 2

| Sujet | Décision |
|---|---|
| Throttle CSTV | Le throttle applicatif devient un quota de **requêtes de matching** plutôt qu’un quota par média contenu dans le batch. |
| Protection | Le plafond du batch reste 50, ce qui borne le coût maximum d’une requête. |
| Limites initiales | Hypothèse recommandée : 30 requêtes/minute/compte et 60/minute/IP. |
| Capacité théorique | Avec 50 éléments : jusqu’à 1 500 médias/min/compte servis par le backend, avant autres contraintes. |
| Provider quota | Chaque appel réel `search`, `hydrate`, etc. continue de passer par `TmdbProviderRateLimiter`. |
| Retry provider | Un élément temporairement limité/indisponible doit retourner un état retryable et non `unresolved`. |
| Partial success | Un batch peut contenir simultanément des `matched`, `not_found`, `unresolved` et `retry`. |
| Ordre | La position des réponses correspond exactement à la position des demandes. |
| Android | Seuls les éléments `retry` sont replacés dans `external_hydration_queue`. |
| Erreur globale backend | Une panne backend/DB complète peut toujours faire échouer la requête entière. |
| Erreur individuelle provider | Ne doit pas annuler les succès déjà calculés dans le batch. |

## 4.3 Décisions prises à l’étape 3

| Sujet | Décision |
|---|---|
| Migration quota | Aucune migration nécessaire : `catalog_match_attempts` peut enregistrer une ligne par requête au lieu d’une ligne par média. |
| Bulk cache | Ajouter une lecture multi-clés dans `MediaMetadataCacheRepository` pour éviter N SELECT de cache. |
| Bulk PostgreSQL | Ajouter un lookup batch pour les candidats backend-first introduits par T28. |
| Provider misses | Les misses non servis localement sont traités individuellement, sous token bucket TMDB. |
| 429 provider | Converti en résultat retryable par item avec `Retry-After`; ne doit pas faire tomber les autres résultats. |
| Contrat Android | Étendre la gestion du `status` existant plutôt que créer un second endpoint. |
| WorkManager | Ne pas augmenter `MAX_ITEMS_PER_RUN` dans la première livraison ; mesurer d’abord. |
| Batch Android | `MAX_ITEMS_PER_BATCH = 50`. |
| Mémoire | Le batch est plafonné à 50 ; aucune collection de catalogue complet. |
| Parallélisme TMDB | Pas de parallélisme massif. Le budget fournisseur reste l’autorité. |

## 4.4 Décisions prises à l’étape 4

| Sujet | Décision |
|---|---|
| Ordre | Backend contrat retry → bulk cache/DB → quota request → app mapping retry → batch 50 → tests/perf. |
| Livraison | Une seule release T29, mais tâches indépendantes et testables. |
| Rollback | Le batch Android peut revenir à 20 sans rollback backend si un problème device apparaît. |
| Tuning | Le rate limit TMDB et `MAX_ITEMS_PER_RUN` ne sont ajustés qu’après mesures réelles. |

---

# 5. Hypothèses

- T28 réduit suffisamment les misses fournisseur pour que des batchs de 50 soient majoritairement servis localement.
- 30 requêtes/minute/compte reste un garde-fou anti-abus suffisant compte tenu du plafond de 50 médias par batch.
- La majorité des installations n’exécute qu’un backfill actif par compte/appareil.
- Le backend Alwaysdata/PostgreSQL peut absorber quelques centaines à quelques milliers de lookups indexés par minute plus facilement que TMDB.
- Le token bucket TMDB actuel est volontairement conservateur et doit rester indépendant du débit local.
- Un état `retry` peut être ajouté à la sémantique du `status` sans casser les anciens clients, à condition que les nouvelles réponses batch ne soient utilisées que par une APK compatible.
- La release backend doit précéder ou accompagner l’APK T29 pour garantir la compatibilité du nouveau comportement retry.

---

# 6. Questions ouvertes

Aucune question bloquante.

Valeurs à confirmer par métriques après release :

- 30 req/min/compte;
- 60 req/min/IP;
- maintien de `MAX_ITEMS_PER_RUN = 200`;
- taux réel de batchs contenant un retry TMDB.

Ces valeurs sont des paramètres de tuning, pas des choix d’architecture.

---

# 7. Spécification fonctionnelle

## 7.1 User stories techniques

- En tant qu’installation CSTV, je veux envoyer moins de requêtes HTTP pour traiter mon catalogue plus rapidement.
- En tant que backend, je veux répondre très vite aux médias déjà connus sans être artificiellement limité par le quota fournisseur.
- En tant qu’opérateur, je veux conserver une protection TMDB stricte même si le débit backend augmente.
- En tant que worker Android, je veux reprogrammer uniquement les médias réellement en échec temporaire.

## 7.2 Flux batch cible

```text
50 demandes
    ↓
throttle HTTP CSTV : +1 requête
    ↓
cache bulk
    ├── hits
    ↓ misses
PostgreSQL backend-first bulk
    ├── hits
    ↓ misses
TMDB
    ├── matched
    ├── not_found
    ├── unresolved
    └── retry/provider-limited
    ↓
50 réponses dans l’ordre
```

## 7.3 États par item

### `matched`

Le média possède une identité CSTV et une fiche exploitable.

### `not_found`

TMDB n’a retourné aucun candidat.

### `unresolved`

La résolution métier a réellement terminé sans match accepté.

### `retry`

La résolution n’a pas pu être menée à son terme pour une raison temporaire :

- rate limit TMDB;
- indisponibilité provider;
- erreur réseau transitoire;
- autre erreur explicitement retryable.

Un `retry` ne doit jamais écrire :

```text
lastMatchAttemptAt = résultat métier unresolved
```

## 7.4 Règles du throttle CSTV

Le throttle protège le endpoint, pas le fournisseur.

### Une requête unitaire

```text
POST /matches
=> +1
```

### Un batch de 50

```text
POST /matches/batch
=> +1
```

Le plafond de payload reste :

```text
1..50 items
```

Hypothèse initiale :

```text
30 requêtes/min/compte
60 requêtes/min/IP
```

## 7.5 Règles TMDB

Le token bucket existant reste :

```text
capacity = 12
refill = 4/s
```

Chaque opération provider existante continue d’appeler :

```text
TmdbProviderRateLimiter::acquire()
```

Un hit :

```text
cache
PostgreSQL
```

ne consomme aucun token fournisseur.

## 7.6 Partial success

Exemple de batch :

```text
#0 matched backend
#1 matched backend
#2 matched TMDB
#3 retry 429
#4 not_found
#5 matched backend
```

La réponse doit conserver exactement cet ordre.

Le client :

- persiste #0/#1/#2/#5;
- persiste le résultat métier #4;
- reprogramme uniquement #3.

## 7.7 Retry-After

Lorsqu’un retry provider possède une échéance connue :

```text
retryAfterSeconds
```

le client calcule son `nextAttemptAt` à partir de cette information.

Sinon il utilise le backoff F45 existant.

## 7.8 Critères d’acceptation

- Un batch de 50 hits PostgreSQL peut être traité en une requête HTTP et zéro appel TMDB.
- Ce batch consomme une seule unité de throttle CSTV.
- Le token bucket TMDB reste inchangé.
- Un 429 sur un élément ne supprime pas les résultats des éléments déjà traités.
- Un retry n’est pas persisté comme `UNRESOLVED`.
- Seuls les items retry sont reprogrammés.
- L’ordre des réponses est stable.
- Le cache de match peut être lu en bulk.
- Le lookup PostgreSQL-first peut être effectué en bulk.
- Le endpoint unitaire continue de fonctionner.
- Les anciennes règles F45 de priorité de queue restent valides.
- `DETAIL_OPEN` reste prioritaire sur `MISSING_METADATA`.
- Aucun parallélisme TMDB massif.
- Android n’envoie jamais plus de 50 items.

## 7.9 Cas limites

### Batch 100 % backend

```text
50 in
50 matched local
0 TMDB
```

### Batch 100 % froid

Le backend peut rencontrer le budget TMDB.

Les items non traitables dans la fenêtre deviennent `retry`, sans transformer le batch entier en erreur métier.

### Token bucket vide avant premier miss

Tous les hits locaux réussissent.

Les misses provider deviennent retryables.

### Erreur PostgreSQL globale

HTTP global en erreur : le worker peut reprogrammer le batch selon la stratégie technique existante.

### Item supprimé localement pendant la requête

Le worker vérifie toujours la présence de la source au moment de l’application; le comportement F45 existant reste la référence.

---

# 8. Spécification technique

## 8.1 Composants backend impactés

- `backend/src/Catalog/CatalogService.php`
- `backend/src/Catalog/CatalogMatchThrottleRepository.php`
- `backend/src/Catalog/MediaMetadataCacheRepository.php`
- `backend/src/Catalog/CatalogMatchEngine.php`
- `backend/src/Catalog/ExternalMediaRepository.php`
- `backend/src/Http/Action/CatalogAction.php` si sérialisation/status nécessaire
- DTO/résultats backend existants
- tests API/intégration

## 8.2 Composants Android impactés

- `app/src/main/java/com/cstv/app/data/worker/ExternalMetadataHydrationWorker.kt`
- `app/src/main/java/com/cstv/app/data/repository/ExternalMetadataRepositoryImpl.kt`
- `app/src/main/java/com/cstv/app/domain/model/...` pour résultat de batch si nécessaire
- DTO catalog
- tests worker/repository

## 8.3 Throttle request-level

Modifier :

```php
throttleMatch($accountId, $ipKey, $count)
```

vers une sémantique request-level.

Conceptuellement :

```php
throttleMatchRequest($accountId, $ipKey);
```

Puis :

```php
record($accountId, $ipKey, 1);
```

Le repository n’insère donc plus 50 lignes pour un batch de 50.

## 8.4 Bulk cache

Ajouter :

```php
findMany(array $keys): array
```

à `MediaMetadataCacheRepository`.

Objectif :

```text
1 SELECT
WHERE cache_key IN (...)
```

ou mécanisme équivalent paramétré.

Retour :

```text
key => cache entry
```

Les hits frais sont immédiatement résolus.

Les entrées stale restent gérées selon les règles existantes de fallback/single-flight.

## 8.5 Bulk PostgreSQL-first

Ajouter une méthode batch dans `ExternalMediaRepository`.

Conceptuellement :

```php
findStrictConsolidatedMatches(array $requests): array
```

Le résultat doit être indexé par position ou identifiant de requête.

Une approche possible sans migration :

```sql
WITH input AS (
  SELECT *
  FROM jsonb_to_recordset(CAST(:items AS jsonb))
  AS x(idx int, kind text, normalized_title text, year int)
)
...
```

ou deux requêtes bulk séparées films/séries.

Contraintes :

- requête paramétrée;
- unicité détectée;
- pas de concaténation SQL de valeurs utilisateur;
- résultat borné au batch de 50.

## 8.6 Pipeline CatalogService batch

Le batch ne doit plus être un simple :

```php
array_map(matchWithoutThrottle)
```

Pipeline cible :

```text
build cache keys
↓
cache findMany
↓
collect unresolved indexes
↓
backend-first batch
↓
collect provider indexes
↓
provider path per item
↓
assemble original order
```

## 8.7 Résultat retryable

Étendre le résultat de matching avec une représentation technique explicite.

Exemple conceptuel backend :

```php
CatalogMatchResult::retry(
    version: ...,
    retryAfterSeconds: ...
)
```

Le JSON peut rester dans la forme actuelle :

```json
{
  "status": "retry",
  "match": null,
  "item": null,
  "cache": {
    "retryAfter": 2
  }
}
```

Le nom exact du champ doit rester cohérent avec le contrat déjà utilisé pour `Retry-After`.

## 8.8 Gestion provider par item

Dans le chemin batch :

```text
try provider resolution
catch provider rate limited/transient
    => retry item
continue batch
```

Les erreurs non retryables/bugs internes ne doivent pas être silencieusement converties en retry.

## 8.9 Android : résultat de matching

Le modèle actuel :

```text
ExternalMetadataMatch?
```

ne suffit plus pour distinguer :

```text
null = not_found/unresolved
```

de :

```text
retry technique
```

Introduire un résultat explicite, par exemple :

```kotlin
sealed interface ExternalMetadataMatchOutcome {
    data class Matched(...)
    data class Unresolved(...)
    data class Retry(val retryAfterMillis: Long?)
}
```

Le nom exact reste interne.

## 8.10 Android : persistance

### Matched

Comportement actuel.

### Unresolved / not_found

Persister la tentative métier selon F45.

### Retry

Ne pas écrire de ligne `external_media_links` de résultat métier.

Conserver/réinsérer la demande dans :

```text
external_hydration_queue
```

avec :

```text
nextAttemptAt
attemptCount
```

## 8.11 Batch Android

Modifier :

```kotlin
MAX_ITEMS_PER_BATCH = 20
```

en :

```kotlin
MAX_ITEMS_PER_BATCH = 50
```

Conserver initialement :

```kotlin
MAX_ITEMS_PER_RUN = 200
```

Motif :

- isoler le gain du batch;
- éviter un changement simultané de durée WorkManager;
- le worker sait déjà enchaîner la convergence.

## 8.12 Provider limiter

Aucune modification des constantes dans cette première livraison :

```php
CAPACITY = 12
REFILL_PER_SECOND = 4.0
```

Les métriques post-release décideront d’un éventuel ticket ultérieur.

## 8.13 Transaction / single-flight

Les optimisations bulk ne doivent pas supprimer :

- cache single-flight;
- advisory locks existants;
- protection contre les doubles hydratations.

Un hit local bulk n’a pas besoin de lock fournisseur.

## 8.14 Stockage

Aucune migration prévue.

`catalog_match_attempts` change seulement de sémantique d’écriture :

```text
avant : une ligne / média
après : une ligne / requête HTTP
```

La table reste compatible techniquement.

Documenter ce changement car les anciennes métriques historiques ne seront plus comparables directement.

## 8.15 Performances

Objectifs :

- 50 hits cache → O(1) requête cache bulk;
- 50 hits PostgreSQL → nombre constant de requêtes bulk par kind;
- 0 boucle d’INSERT throttle par média;
- 0 token TMDB sur hit local;
- pas de catalogue complet en mémoire;
- aucune coroutine provider parallèle par dizaine.

## 8.16 Sécurité

- batch max 50 inchangé;
- validation titre/année/kind inchangée;
- throttle compte + IP conservé;
- token TMDB partagé conservé;
- requêtes bulk paramétrées;
- aucun ID fournisseur exposé à l’app.

---

# 9. Architecture

## 9.1 Flux complet

```text
ExternalMetadataHydrationWorker
          |
          | 50 items
          v
POST /matches/batch
          |
          v
CSTV request throttle (+1)
          |
          v
MediaMetadataCacheRepository.findMany()
          |
     hits | misses
          v
ExternalMediaRepository.findStrict...Batch()
          |
     hits | misses
          v
CatalogMatchEngine/provider fallback
          |
          +---- TmdbProviderRateLimiter
          |          |
          |       4 req/s
          |
          +-- matched
          +-- unresolved
          +-- not_found
          +-- retry
          |
          v
ordered batch response
          |
          v
Android outcomes
  |        |        |
matched unresolved retry
  |        |        |
persist   persist   queue/backoff
```

## 9.2 Séparation des responsabilités

### Quota CSTV

Protège :

- fréquence HTTP;
- abuse d’un compte;
- abuse d’une IP.

Ne protège pas directement TMDB.

### Rate limiter TMDB

Protège :

- token fournisseur partagé;
- toutes les routes provider;
- débit réel sortant vers TMDB.

### Backend-first

T28 protège indirectement le budget provider en évitant les appels.

### Worker Android

Gère :

- priorités;
- retry;
- persistance locale;
- taille des lots.

## 9.3 Pourquoi ne pas augmenter TMDB maintenant

Avant tuning :

```text
gain potentiel = supprimer les appels inutiles
```

Après T28/T29, mesurer :

```text
provider calls / média traité
```

Seulement ensuite décider si 4 req/s reste limitant.

## 9.4 Risques

### Changement sémantique du throttle

Les statistiques historiques de `catalog_match_attempts` changent de sens.

Mitigation :
documenter la date/version.

### Clients anciens et status retry

Mitigation :
le backend peut conserver le comportement global précédent pour les endpoints/clients non compatibles si nécessaire pendant rollout, ou déployer backend juste avant APK.

### Batch froid

50 médias froids peuvent dépasser le burst TMDB.

Mitigation :
partial success + retry par item.

### Charge PostgreSQL

Le débit de hits backend augmente.

Mitigation :
bulk queries, index existants, mesure.

---

# 10. Plan de développement

## Tâche 1 — Introduire le résultat retryable backend

- [x] Ajouter un outcome `retry` par item.

Objectif:
Distinguer une impossibilité temporaire d’un vrai résultat métier.

Fichiers:
- résultat/catalog models backend
- `CatalogService.php`
- tests

Validation:
- provider 429 → retry;
- provider transient → retry;
- not_found reste not_found;
- unresolved reste unresolved.

---

## Tâche 2 — Isoler les erreurs provider par item dans le batch

- [x] Rendre `/matches/batch` partiellement réussi.

Objectif:
Un item en erreur ne fait pas perdre les succès des autres.

Fichiers:
- `backend/src/Catalog/CatalogService.php`
- tests API

Validation:
Batch mixte :

```text
matched / retry / matched / not_found
```

ordre identique.

---

## Tâche 3 — Passer le throttle CSTV au niveau requête

- [x] Modifier la sémantique du quota.

Objectif:
Un batch compte une unité de throttle, indépendamment de son nombre d’items.

Fichiers:
- `CatalogService.php`
- `CatalogMatchThrottleRepository.php`
- tests quota

Validation:
- batch 50 = +1;
- match unitaire = +1;
- limite compte;
- limite IP;
- aucune boucle d’INSERT x50.

---

## Tâche 4 — Ajouter le cache bulk

- [x] Implémenter `MediaMetadataCacheRepository::findMany()`.

Objectif:
Supprimer N SELECT cache pour un batch.

Fichiers:
- `MediaMetadataCacheRepository.php`
- tests repository

Validation:
- hits/misses mappés par clé;
- statut fresh correct;
- payload identique à `find()`.

---

## Tâche 5 — Ajouter le backend-first bulk

- [x] Implémenter le lookup T28 en lot.

Objectif:
Résoudre les hits PostgreSQL avec un nombre constant de requêtes.

Fichiers:
- `ExternalMediaRepository.php`
- tests intégration

Validation:
- 50 films;
- mélange films/séries;
- homonymes;
- années;
- ordre/index de mapping.

---

## Tâche 6 — Refactorer `CatalogService::matchBatch`

- [x] Construire le pipeline cache → PostgreSQL → provider.

Objectif:
Éviter `array_map(matchWithoutThrottle)` comme stratégie principale.

Validation:
- 50 hits backend = 0 provider;
- misses seuls atteignent le provider;
- ordre stable.

---

## Tâche 7 — Adapter le contrat Android de résultat

- [x] Introduire un outcome explicite.

Fichiers:
- DTO catalog Android
- repository externe
- modèles domaine
- tests repository

Validation:
- matched;
- unresolved;
- not_found;
- retry;
- Retry-After.

---

## Tâche 8 — Adapter le worker pour retry par item

- [x] Ne reprogrammer que les items retryables.

Fichiers:
- `ExternalMetadataHydrationWorker.kt`
- tests worker

Validation:
Dans un batch de 20/50 :

```text
15 matched
3 unresolved
2 retry
```

attendu :

```text
18 retirés de queue
2 reprogrammés
```

Les 2 retry ne créent aucune ligne unresolved.

---

## Tâche 9 — Passer les batchs Android à 50

- [x] Modifier `MAX_ITEMS_PER_BATCH`.

Objectif:
Réduire les allers-retours backend.

Fichiers:
- `ExternalMetadataHydrationWorker.kt`
- tests

Validation:
- batch max 50;
- run max reste 200;
- pas de régression source manquante;
- pas de parallélisme provider.

---

## Tâche 10 — Tests anti-abus et compatibilité

- [x] Compléter tests backend/API.

Scénarios:
- limite compte;
- limite IP;
- batch 51 rejeté;
- batch 50 accepté;
- endpoint unitaire;
- erreurs temporaires;
- client contract.

---

## Tâche 11 — Benchmark avant/après

- [ ] Mesurer T29 avec T28 actif. **Non exécutable en étape 5** : mesure sur backend/APK réels en
  conditions live, exclue des critères de validation automatisés (AGENTS.md — pas de test manuel/
  device). À faire après déploiement, par le PO ou en observation post-release.

Mesures minimales :

```text
médias traités/min
requêtes HTTP/min
hits cache
hits PostgreSQL
TMDB search/min
TMDB hydrate/min
retries provider
```

Scénarios :
- catalogue backend chaud;
- catalogue mixte;
- catalogue majoritairement froid.

Validation:
Le débit backend doit augmenter sans hausse du plafond TMDB.

---

## Tâche 12 — Vérification F46

- [ ] Vérifier le comportement réel sur une APK de test. **Non exécutable en étape 5** : nécessite un
  appareil/émulateur et une APK installée (AGENTS.md exclut explicitement ce type de vérification des
  critères de validation d'agent). À faire manuellement après release.

Objectif:
Confirmer que “À traiter” diminue plus rapidement.

Validation:
Comparer sur fenêtres équivalentes :

```text
avant T28/T29
après T28/T29
```

Ne pas modifier F46 dans ce ticket sauf bug découvert.

---

# 11. Notes de développement

## Étape 5 — implémentation

### Backend (`backend/src/Catalog/`)

- `CatalogMatchResult` : nouveau statut `retry` (+ `retryAfterSeconds`), factory `retry()`.
- `CatalogMatchEngine::resolve()` : second paramètre optionnel `$precomputedStrict` (`null` = calcule
  lui-même, comme avant ; `[]` = déjà tenté par le lot, aucun match ; tableau non vide = réutiliser).
  Le chemin unitaire (`match()`) reste inchangé (aucun argument passé).
- `CatalogService` :
  - `throttleMatch(count)` → `throttleMatchRequest()` : une unité de quota par requête HTTP
    (`match()` et `matchBatch()`), plus par média. Seuils passés à 30 req/min/compte, 60 req/min/IP
    (`MATCH_REQUESTS_PER_MINUTE_ACCOUNT`/`_IP`).
  - Nouveau pipeline `matchBatchItems()` : cache bulk (`MediaMetadataCacheRepository::findMany()`) →
    backend-first bulk (`ExternalMediaRepository::findStrictConsolidatedMatchBatch()`, misses
    seulement, année connue) → provider par item (`matchBatchItem()`, isolé).
  - `matchBatchItem()` catch `ApiException` avec `status` ∈ {429, 502, 503} → item `retry` ; toute
    autre exception continue de se propager (pas de conversion silencieuse d'un bug interne).
  - Le endpoint unitaire (`match()`) n'a **pas** ce filet : un échec provider y reste une erreur HTTP
    classique (429/502/503), comportement inchangé — seul le chemin batch isole par item (§8.8).
- `MediaMetadataCacheRepository::findMany()` : un SELECT `= ANY(:keys::text[])` au lieu de N.
- `ExternalMediaRepository::findStrictConsolidatedMatchBatch()` : 2 passes (titre normalisé, puis
  titre original/alternatif) groupées par `kind` × `locale`, résolues en PHP pour respecter la même
  règle d'unicité stricte que la version unitaire (jamais de résolution ambiguë).
- Aucune migration (§4.3) — `retry` n'est jamais écrit dans `media_metadata_cache` (transitoire par
  nature, la contrainte `result_status` CHECK n'a donc pas besoin de l'accepter).

### Android (`app/src/main/java/com/cstv/app/`)

- `domain/model/ExternalMetadata.kt` : `ExternalMetadataMatchOutcome` (sealed interface `Matched` /
  `Unresolved` / `Retry(retryAfterMillis)`) remplace `ExternalMetadataMatch?` en retour de
  `ExternalMetadataRepository.match()`/`matchBatch()`. Extension `matchOrNull` pour l'accès pratique
  au match résolu (équivalent de l'ancien `?.`).
- `data/remote/dto/CatalogDtos.kt` : `CatalogCacheDto.retryAfter` (secondes, `§8.7`).
- `data/repository/ExternalMetadataRepositoryImpl.kt` : `persistNetworkMatch()` retourne `Retry` sans
  toucher Room (§7.3 — jamais persisté comme `unresolved`) quand `response.status == "retry"`.
- `data/repository/ContentClassificationRepository.kt` : un `Retry` est traité comme une erreur
  réseau (jamais mis en cache 30 min comme "inconnu"), contrairement à `Unresolved` qui l'est.
- `data/worker/ExternalMetadataHydrationWorker.kt` : `processOne()`/`drainQueue()` branchent sur le
  scellé (`when`) — `Matched`/`Unresolved` retirent la demande de la file comme avant, `Retry`
  reprogramme via `requeueWithBackoff(retryAfterMillis)` (délai backend si connu, sinon le backoff
  exponentiel F45 existant). `MAX_ITEMS_PER_BATCH` 20 → 50 ; `MAX_ITEMS_PER_RUN` inchangé (200).

### Tests

- Backend : `CatalogApiTest` (throttle requête vs média, isolation retry par item, cache hit bulk),
  `CatalogMatchEngineTest` (`findStrictConsolidatedMatchBatch` — unique/ambigu/cross-kind/locale).
  Suite complète (`vendor/bin/phpunit`) : 249 tests, verte.
- Android : `ExternalMetadataRepositoryImplTest`, `ContentClassificationRepositoryTest`,
  `ExternalMetadataHydrationWorkerTest` mis à jour pour le type scellé + nouveaux cas retry.
  `./gradlew testDebugUnitTest` (suite complète), `assembleDebug`, `lintDebug` : verts.

### Hors périmètre étape 5 (nécessitent mesures live/device, exclus par AGENTS.md)

Tâche 11 (benchmark avant/après) et Tâche 12 (vérification F46 sur APK réelle) — voir notes dans
leurs sections respectives (§10).

Paramètres initiaux :

```text
API batch max                 50
Android MAX_ITEMS_PER_BATCH  50
Android MAX_ITEMS_PER_RUN    200
Throttle account             30 req/min
Throttle IP                  60 req/min
TMDB capacity                12
TMDB refill                  4 req/s
```

Les deux premiers quotas sont ajustables après mesure.

Ne pas augmenter simultanément :

```text
MAX_ITEMS_PER_RUN
+
TMDB refill
+
batch size
```

afin de conserver une attribution claire des gains/régressions.

---

# 12. Review

Review effectuée le 2026-08-22 sur le commit d’implémentation
`1b36d77db0f6d8d2ebe5d30e6b23ecb5dbc306e0`.

Verdict:
CHANGES_REQUESTED

## Critique

Aucun problème critique identifié.

## Majeur

### R1 — Le nouveau statut `retry` n’est pas compatible avec les APK déjà publiées

Description:

T29 renvoie désormais un succès HTTP contenant un item :

```json
{
  "status": "retry",
  "match": null,
  "item": null
}
```

sur le même endpoint `/v1/catalog/matches/batch` déjà consommé par les anciennes APK.

Or le client pré-T29 ne connaît pas `retry` : son contrat repose sur
`ExternalMetadataMatch?` et `persistNetworkMatch()` interprète toute réponse sans `item` comme un
résultat métier non résolu. Il persiste alors une tentative `unresolved`, puis le worker retire la
demande de `external_hydration_queue`.

La mitigation actuellement documentée (« déployer le backend juste avant/accompagner l’APK »)
n’est pas suffisante : les installations qui n’ont pas encore reçu la nouvelle APK continuent
d’appeler le même endpoint.

Impact:

- un 429/502/503 fournisseur peut être transformé en faux `unresolved` sur une ancienne APK ;
- l’item n’est plus reprogrammé comme échec technique ;
- le comportement dépend de la vitesse de rollout de l’APK ;
- la règle centrale de T29 « ne jamais enregistrer un échec technique comme `UNRESOLVED` » n’est
  donc garantie que pour les clients déjà migrés.

Correction attendue:

Introduire une négociation explicite de capacité/version avant d’émettre `status=retry` par item.

Solution recommandée :

- la nouvelle APK annonce qu’elle comprend le retry par item via un header/capability explicite ;
- le backend n’émet `status=retry` que pour ces clients ;
- pour un client legacy, conserver le comportement compatible précédent sur une erreur provider
  retryable, c’est-à-dire une erreur HTTP globale afin que l’ancien worker reprogramme le batch au
  lieu de persister un faux `unresolved` ;
- ajouter des tests backend couvrant les deux contrats : client legacy et client T29.

### R2 — Le lookup PostgreSQL bulk n’est pas borné par les couples titre + année

Description:

Les méthodes batch de `ExternalMediaRepository` regroupent correctement les résultats par année en
PHP, mais leurs requêtes SQL ne filtrent pas l’année demandée :

- `strictMatchByNormalizedTitleBatch()` filtre seulement
  `normalized_title = ANY(:titles)` + locale ;
- `strictMatchByAlternativeTitleBatch()` filtre seulement les titres originaux/alternatifs + locale.

PostgreSQL renvoie donc toutes les œuvres portant l’un des titres demandés, pour toutes les années,
puis PHP élimine les années inutiles.

Avec un titre fréquent ou un catalogue consolidé important, un batch de 50 entrées peut ainsi
charger un nombre de lignes sans rapport avec la taille du batch. La seconde passe est
particulièrement sensible car elle combine plusieurs titres via l’index GIN des alternatives.

Impact:

- consommation mémoire et trafic PostgreSQL non bornés par les 50 entrées du batch ;
- risque de déplacer le goulet d’étranglement de TMDB vers PostgreSQL ;
- comportement contraire à l’objectif T29 de lookup bulk borné et performant ;
- le coût augmente avec la taille historique du catalogue, même lorsque seules quelques années sont
  demandées.

Correction attendue:

Faire porter le couple attendu par la requête SQL, pas uniquement par le regroupement PHP.

Solution recommandée :

- construire une relation d’entrée `(idx, title, year)` avec `jsonb_to_recordset`, `VALUES` ou une
  stratégie équivalente paramétrée ;
- joindre les tables TMDB sur titre **et année** dès PostgreSQL ;
- conserver ensuite la détection d’unicité par `idx` ;
- appliquer le même principe à la passe titre original/alternatif ;
- ajouter un test montrant que des dizaines d’homonymes sur d’autres années ne sont pas ramenés dans
  le jeu de candidats du batch, ainsi qu’un `EXPLAIN (ANALYZE, BUFFERS)` sur un volume représentatif.

## Mineur

### R3 — La Tâche 10 est marquée terminée alors que plusieurs frontières anti-abus ne sont pas verrouillées par des tests dédiés

Description:

La review retrouve les nouveaux tests :

- quota compte au niveau requête ;
- batch compté comme une seule unité ;
- isolation `retry` par item ;
- cache bulk.

En revanche, les scénarios explicitement demandés par la Tâche 10 ne sont pas tous couverts par un
test T29 dédié :

- limite IP à 60 req/min ;
- batch de 51 rejeté ;
- batch de 50 accepté ;
- seuil exact du endpoint unitaire à 30 req/min.

Le test unitaire historique du quota compte injecte encore 120 tentatives : il prouve qu’une valeur
très supérieure est rejetée, mais ne verrouille pas la nouvelle frontière à 30.

Impact:

Faible sur le code actuel — le plafond HTTP de 50 existait déjà — mais ces valeurs sont maintenant
des invariants structurants de T29 et peuvent régresser sans alerte.

Correction attendue:

Compléter les tests d’intégration avec les quatre frontières ci-dessus et ne considérer la Tâche 10
comme totalement validée qu’après leur ajout.

## Corrections demandées

- [ ] R1 — Ajouter une capability/version explicite pour le contrat `retry` par item.
- [ ] R1 — Préserver un comportement compatible pour les anciennes APK.
- [ ] R1 — Tester séparément client legacy et client T29 sur 429/502/503.
- [ ] R2 — Filtrer les lookups bulk PostgreSQL par titre + année directement en SQL.
- [ ] R2 — Vérifier le plan/coût des deux passes bulk sur un volume représentatif.
- [ ] R2 — Ajouter un test avec de nombreux homonymes répartis sur plusieurs années.
- [ ] R3 — Ajouter les tests limite IP 60, batch 50 accepté et batch 51 rejeté.
- [ ] R3 — Verrouiller le seuil exact de 30 req/min sur l’endpoint unitaire.

---

# 13. Release

Version:

Commit:

Date:
