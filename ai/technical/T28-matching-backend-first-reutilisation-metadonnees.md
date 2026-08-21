# T28 - Matching backend-first et réutilisation des métadonnées consolidées

## Informations générales

Status:
FIXES

Created:
2026-08-21

Dépendances:
- F45 — Consolidation des données IPTV par métadonnées externes
- T22 — Centralisation des appels TMDB dans le backend

Suite:
- T29 — Débit batch, quotas de matching et retries fournisseur

---

# 1. Description

Réduire fortement le nombre d’appels TMDB nécessaires au matching des films et séries en exploitant en priorité les données déjà consolidées dans PostgreSQL.

Le backend CSTV doit devenir la première source de résolution lorsqu’il possède déjà suffisamment d’informations pour identifier une œuvre de manière fiable.

TMDB reste le fallback lorsqu’aucune correspondance backend suffisamment sûre n’est disponible.

Le ticket couvre deux optimisations principales :

1. **Backend-first strict** :
   - tenter une résolution directe sur les médias déjà hydratés en PostgreSQL ;
   - ne réutiliser un média que lorsque la correspondance est non ambiguë.

2. **Réutilisation après `/search` TMDB** :
   - si TMDB retourne un `tmdbId` déjà connu et déjà hydraté dans PostgreSQL ;
   - retourner directement la fiche stockée ;
   - ne pas refaire inutilement l’appel TMDB de détail/hydratation.

Le comportement visible de l’application ne change pas.

---

# 2. Contexte

Le matching actuel, simplifié par le hotfix F45 v1.91.2, suit essentiellement ce chemin :

```text
titre + année
    ↓
TMDB /search/movie|tv
    ↓
premier résultat
    ↓
findOrCreateForTmdb()
    ↓
TMDB /movie|tv/{id}?append_to_response=...
    ↓
persistance PostgreSQL
```

Cette simplification a amélioré la robustesse et le débit par rapport au moteur multi-passes précédent, mais elle a supprimé la résolution PostgreSQL-first.

Deux appels TMDB peuvent donc être consommés pour une œuvre déjà entièrement connue du backend :

```text
/search
+
/movie|tv/{id}
```

Le backend possède pourtant déjà :

- `tmdb_media` : association `externalId` ↔ `tmdbId`;
- `tmdb_movies`;
- `tmdb_series`;
- `normalized_title`;
- l’année de sortie/première diffusion;
- les titres originaux;
- les titres alternatifs;
- les métadonnées hydratées;
- les index sur `normalized_title`.

Plus le backend CSTV est utilisé, plus cette base consolidée devient riche.

L’objectif est que cette mutualisation profite directement aux installations suivantes.

---

# 3. Objectif

- Réduire le nombre d’appels TMDB consommés par le backfill F45.
- Accélérer le traitement des médias déjà connus par le backend.
- Faire du backend CSTV la première source de matching lorsque la correspondance est suffisamment sûre.
- Éviter tout nouvel appel d’hydratation TMDB lorsqu’un `tmdbId` retourné par `/search` possède déjà une fiche complète en PostgreSQL.
- Conserver le comportement actuel TMDB-first-result lorsqu’aucune résolution backend sûre n’est possible.
- Ne pas augmenter le rate limit TMDB dans ce ticket.
- Ne pas introduire de faux positifs au nom de la performance.
- Ne pas introduire de dépendance TMDB dans l’application Android.

---

# 4. Décisions prises

## 4.1 Décisions prises à l’étape 1

| Sujet | Décision |
|---|---|
| Découpage | L’optimisation est séparée en deux tickets : T28 traite l’évitement de TMDB ; T29 traite le débit, les batchs et les quotas. |
| Priorité | Chercher d’abord à supprimer les appels TMDB inutiles avant d’augmenter le budget fournisseur. |
| Backend-first | Réintroduire une résolution PostgreSQL-first, mais volontairement plus stricte et plus simple que l’ancien moteur de scoring F45. |
| Sécurité du matching | En cas d’ambiguïté, le backend ne choisit pas : il retombe sur TMDB. |
| Fallback | Le fallback TMDB conserve la règle actuelle : premier résultat retourné par TMDB. |
| UI | Aucun changement visible dans l’application. |
| Provider neutrality | L’application continue de manipuler uniquement les `externalId` CSTV. |
| Rate limit TMDB | Les 4 req/s sustained / burst 12 restent inchangés dans T28. |

## 4.2 Décisions prises à l’étape 2

| Sujet | Décision |
|---|---|
| Match backend principal | Un match direct est accepté si `kind`, titre normalisé et année correspondent exactement et que la correspondance est unique. |
| Année absente | Si l’année du média IPTV est inconnue, aucune résolution PostgreSQL-first stricte n’est acceptée ; fallback TMDB. |
| Titres alternatifs | Après échec du titre principal, un titre alternatif exact + année exacte + résultat unique peut être utilisé. |
| Titres originaux | Un titre original exact peut être traité comme une variante de titre backend, avec la même exigence d’année exacte et d’unicité. |
| Plusieurs candidats | Deux candidats ou plus correspondant aux critères → fallback TMDB. |
| Fiche backend stale | Le matching ne déclenche pas de refresh stale. La politique F45 de fraîcheur reste inchangée. |
| Identité seule | Si `tmdb_media` connaît le `tmdbId` mais que `tmdb_movies`/`tmdb_series` n’est pas hydraté, TMDB hydrate normalement la fiche. |
| Fiche déjà hydratée | Si `/search` retourne un `tmdbId` dont la fiche existe déjà, la fiche PostgreSQL est utilisée sans appel de détail TMDB. |
| Cache de match | Le cache `media_metadata_cache` reste le premier niveau implicite géré par `CatalogService::resolve()`. |
| Erreur DB backend-first | Une impossibilité de résolution backend ne doit pas empêcher le fallback TMDB, sauf véritable erreur PostgreSQL globale. |

## 4.3 Décisions prises à l’étape 3

| Sujet | Décision |
|---|---|
| Migration backend | Aucune migration requise pour la première version. |
| Index | Réutiliser l’index existant `normalized_title`; ne créer un nouvel index qu’après mesure démontrant un besoin. |
| Architecture | La logique reste dans `CatalogMatchEngine` + `ExternalMediaRepository`; pas de nouveau microservice ou couche externe. |
| Algorithme | Le backend-first est un chemin rapide déterministe, pas un nouveau moteur de scoring. |
| Hydratation | `hydrate()` n’est appelé que si la fiche complète n’existe pas en PostgreSQL. |
| Freshness | La présence d’une fiche stockée suffit à éviter une hydratation pendant le matching de fond ; le refresh reste géré par les règles F45. |
| Métriques | Les tests doivent pouvoir vérifier le nombre d’appels `search` et `hydrate`; aucune nouvelle infrastructure d’observabilité obligatoire. |
| Compatibilité API | Aucun changement de contrat HTTP. |

## 4.4 Décisions prises à l’étape 4

| Sujet | Décision |
|---|---|
| Ordre de livraison | 1) repository backend-first, 2) moteur de matching, 3) réutilisation après search TMDB, 4) tests/performance. |
| Lot | T28 doit être livrable indépendamment de T29. |
| Validation | Le ticket n’est terminé que si les tests prouvent explicitement qu’un hit PostgreSQL provoque zéro appel TMDB. |

---

# 5. Hypothèses

- Le `cleanTitle` envoyé par l’application est suffisamment propre pour produire une normalisation comparable à `TitleNormalizer`.
- La majorité des collisions de titres problématiques peuvent être évitées en exigeant une année exacte.
- Les fiches déjà présentes dans `tmdb_movies` / `tmdb_series` sont suffisamment complètes pour être resservies directement.
- Les données stale peuvent être resservies pendant un matching de fond conformément à F45 ; la fraîcheur n’est pas le rôle de T28.
- Le volume actuel de `alternative_titles` permet une seconde passe exacte sans migration d’index immédiate.
- Si cette hypothèse devient fausse à grande échelle, l’optimisation des titres alternatifs sera mesurée avant ajout d’un index ou d’une table spécialisée.

---

# 6. Questions ouvertes

Aucune question bloquante pour l’implémentation.

Point à mesurer pendant T28 :

- coût réel de la passe `alternative_titles` lorsque le catalogue backend grossit fortement.

Ce point ne bloque pas la V1 : la passe n’est exécutée qu’après échec du titre principal indexé.

---

# 7. Spécification fonctionnelle

## 7.1 User stories techniques

- En tant qu’installation CSTV, je veux réutiliser les œuvres déjà connues du backend afin de ne pas solliciter inutilement TMDB.
- En tant que backend CSTV, je veux conserver TMDB comme fallback lorsqu’une correspondance locale est ambiguë.
- En tant qu’administrateur du service, je veux que chaque nouvelle installation bénéficie automatiquement du cache consolidé par les installations précédentes.

## 7.2 Flux attendu

### Cas A — cache de match déjà frais

```text
match(title, year)
    ↓
media_metadata_cache
    ↓ hit
réponse immédiate
```

Aucun changement T28.

### Cas B — PostgreSQL-first strict

```text
match(title, year)
    ↓
cache miss
    ↓
PostgreSQL
kind + normalized_title + year
    ↓
1 candidat unique
    ↓
externalId + fiche stockée
    ↓
réponse
```

Appels TMDB :

```text
0
```

### Cas C — titre alternatif backend

```text
titre principal : miss
    ↓
titre original / alternatif exact
+ année exacte
    ↓
1 candidat unique
    ↓
réponse PostgreSQL
```

Appels TMDB :

```text
0
```

### Cas D — backend ambigu

```text
PostgreSQL
    ↓
2 candidats compatibles
    ↓
aucun choix backend
    ↓
TMDB /search
```

### Cas E — `/search` retourne un `tmdbId` déjà hydraté

```text
TMDB /search
    ↓
tmdbId = 155
    ↓
tmdb_media connaît movie:155
    ↓
tmdb_movies possède la fiche
    ↓
réponse PostgreSQL
```

Appels TMDB :

```text
1 search
0 hydrate
```

### Cas F — `/search` retourne une identité connue mais non hydratée

```text
TMDB /search
    ↓
tmdb_media connaît l’identité
    ↓
aucune fiche tmdb_movies/tmdb_series
    ↓
TMDB hydrate
    ↓
persist
```

### Cas G — média totalement inconnu

```text
PostgreSQL miss
    ↓
TMDB search
    ↓
nouveau tmdbId
    ↓
create externalId
    ↓
TMDB hydrate
    ↓
persist
```

## 7.3 Règles métier

### R1 — Genre de média

Un film ne peut jamais correspondre à une série et inversement.

### R2 — Titre principal

Le titre est comparé via la normalisation backend existante.

Le match rapide doit être exact sur le titre normalisé.

Pas de `similar_text`, fuzzy matching ou score dans la fast lane T28.

### R3 — Année

Une année IPTV connue doit être égale à l’année backend.

```text
IPTV 2024
Backend 2024
=> compatible

IPTV 2024
Backend 2023
=> pas de backend-first
```

Le fallback TMDB conserve ses propres règles actuelles.

### R4 — Unicité

Le backend-first ne peut accepter que si une seule œuvre reste candidate.

### R5 — Titres alternatifs

Les titres alternatifs/originaux ne sont consultés qu’après échec du titre principal.

Ils doivent être exacts après normalisation ou comparaison insensible à la casse selon la fonction retenue.

L’année reste obligatoire.

### R6 — Fiche hydratée

Un `externalId` n’est resservable directement que si la table spécifique existe :

```text
movie  -> tmdb_movies
series -> tmdb_series
```

Une simple ligne `tmdb_media` ne suffit pas.

### R7 — Freshness

T28 ne rafraîchit pas une fiche simplement parce que `refresh_after` est dépassé.

### R8 — Fallback

Tout cas non prouvé par les règles précédentes retombe sur le flux TMDB actuel.

## 7.4 Critères d’acceptation

- Un titre+année exact et unique déjà hydraté est résolu sans appel provider.
- Deux homonymes de même titre empêchent le backend-first si l’unicité n’est pas démontrée.
- Une année différente empêche le backend-first.
- Une année IPTV absente empêche le backend-first.
- Un titre alternatif exact + année exacte + unique peut être réutilisé.
- Un `tmdbId` trouvé par `/search` et déjà hydraté ne déclenche pas `hydrate()`.
- Un `tmdbId` connu mais non hydraté déclenche `hydrate()`.
- Un nouveau `tmdbId` conserve le comportement actuel.
- Le contrat HTTP du endpoint de matching est inchangé.
- Les `externalId` existants sont réutilisés.
- Les recommandations continuent de créer uniquement des identités sans N+1 d’hydratation.
- Aucune nouvelle dépendance externe.
- Aucun changement Android obligatoire.

## 7.5 Cas limites

### Remakes

Même titre, années différentes :

```text
Dune 1984
Dune 2021
```

L’année exacte permet de sélectionner la bonne œuvre.

### Même titre, même année

Deux œuvres différentes partageant même titre et même année :

```text
2 candidats
=> fallback TMDB
```

### Année inconnue dans PostgreSQL

Pas de backend-first.

### Titre alternatif partagé

Si plusieurs œuvres possèdent le même titre alternatif compatible :

```text
fallback TMDB
```

### Ligne tmdb_media orpheline de fiche

Identité réutilisée, mais détail hydraté via TMDB avant réponse complète.

### Fiche stale

Réutilisée pour le matching ; aucune hydratation opportuniste dans T28.

## 7.6 Gestion des erreurs

- Une absence de candidat PostgreSQL n’est pas une erreur.
- Une ambiguïté PostgreSQL n’est pas une erreur.
- Une erreur SQL réelle suit la gestion d’erreur backend existante.
- Les erreurs TMDB gardent la gestion F45/T22 actuelle.
- Aucune erreur technique ne doit être convertie en faux `not_found`.

---

# 8. Spécification technique

## 8.1 Composants impactés

Backend :

- `backend/src/Catalog/CatalogMatchEngine.php`
- `backend/src/Catalog/ExternalMediaRepository.php`
- `backend/src/Catalog/CatalogMatchResult.php` si adaptation mineure des méthodes/source
- `backend/tests/Integration/CatalogMatchEngineTest.php`
- tests repository/API associés si nécessaire

Pas de changement Android attendu.

## 8.2 Repository : lookup strict

Ajouter une méthode dédiée plutôt que réactiver directement l’ancien scoring complexe.

Exemple conceptuel :

```php
public function findStrictConsolidatedMatch(
    string $kind,
    string $title,
    int $year
): ?array
```

La méthode doit distinguer :

```text
0 candidat -> null
1 candidat -> row
2+ candidats -> null
```

L’unicité doit être déterminée avant acceptation.

### Passe 1

```text
normalized_title exact
+
année exacte
```

### Passe 2

Seulement si passe 1 vide :

```text
original title / alternative title exact
+
année exacte
```

La seconde passe doit conserver une limite défensive suffisante pour détecter l’ambiguïté (`LIMIT 2` minimum), pas `LIMIT 1`.

## 8.3 Réutilisation d’une fiche par tmdbId

Ajouter une lecture pratique permettant :

```php
$externalId = findByTmdb($kind, $tmdbId);

if ($externalId !== null) {
    $stored = getMovie|getSeries($externalId);

    if ($stored !== null) {
        return stored;
    }
}
```

Le moteur doit appeler `hydrate()` uniquement lorsque `$stored === null`.

## 8.4 CatalogMatchEngine

Pipeline proposé :

```text
resolve(request)
 |
 +-- resolveStrictStored(request)
 |      |
 |      +-- hit -> CatalogMatchResult::reused(...)
 |
 +-- provider.searchCandidates(...)
        |
        +-- aucun -> not_found
        |
        +-- first candidate
              |
              +-- findByTmdb()
              |      |
              |      +-- fiche présente -> reused
              |      |
              |      +-- identité seule -> hydrate
              |
              +-- inconnu -> findOrCreate + hydrate
```

## 8.5 Match method

Méthodes recommandées :

```text
postgresql-exact-title-year
postgresql-alternative-title-year
tmdb-first-result-existing
tmdb-first-result
```

Le champ reste informatif.

## 8.6 Algorithm version

Le moteur change de stratégie.

Recommandation :

```text
ALGORITHM_VERSION = 3
```

La clé de cache de match intégrant la version, les nouvelles demandes utiliseront le nouveau pipeline.

Les médias déjà liés localement ne sont pas rematchés pour cette seule raison.

## 8.7 Cache

`CatalogService::resolve('match', ...)` reste inchangé comme premier niveau.

Ordre global :

```text
media_metadata_cache
→ PostgreSQL strict
→ TMDB search
→ PostgreSQL by tmdbId
→ TMDB hydrate
```

## 8.8 Stockage

Aucune nouvelle table.

Aucune nouvelle colonne.

Aucune migration.

Réutilisation de :

```text
tmdb_media
tmdb_movies
tmdb_series
media_metadata_cache
```

## 8.9 Performances

Le lookup principal doit s’appuyer sur :

```text
tmdb_movies_normalized_title_idx
tmdb_series_normalized_title_idx
```

La passe alternative ne doit être faite qu’en cas de miss principal.

Ne pas effectuer de scan de toutes les données en PHP.

Ne jamais charger le catalogue PostgreSQL en mémoire.

## 8.10 Sécurité / qualité du matching

La performance ne doit jamais réduire les garde-fous :

- année exacte;
- unicité;
- kind exact;
- fallback dès ambiguïté.

## 8.11 Compatibilité

- API Android inchangée.
- Anciennes APK compatibles.
- Backend provider-neutral vis-à-vis de l’application.
- Pas de changement de schema PostgreSQL.
- T29 peut être développé après release de T28.

---

# 9. Architecture

## 9.1 Flux

```text
Android F45
   |
   v
POST /v1/catalog/matches[/batch]
   |
   v
CatalogService cache
   |
   +-- HIT -----------------------------> réponse
   |
   v
CatalogMatchEngine
   |
   +-- PostgreSQL strict
   |      |
   |      +-- HIT ----------------------> réponse
   |
   v
TMDB /search
   |
   v
tmdbId
   |
   +-- fiche déjà PostgreSQL -----------> réponse
   |
   +-- identité seule
   |      |
   |      v
   |   TMDB hydrate
   |
   +-- nouveau
          |
          v
      create identity
          |
          v
      TMDB hydrate
          |
          v
      persist
          |
          v
       réponse
```

## 9.2 Responsabilités

### CatalogService

- cache du résultat complet;
- contrat HTTP indirect;
- aucun scoring.

### CatalogMatchEngine

- orchestration de la stratégie de résolution;
- choix backend-first vs provider;
- maintien du fallback actuel.

### ExternalMediaRepository

- lecture stricte des œuvres consolidées;
- lookup `tmdbId`;
- lecture des fiches;
- persistance existante.

### MediaMetadataProvider

- uniquement provider externe;
- aucune logique PostgreSQL.

## 9.3 Risques

### Faux positif backend-first

Mitigation :

```text
titre exact + année exacte + unique
```

### Scan titres alternatifs coûteux

Mitigation :

- seconde passe seulement;
- mesurer avant migration/index.

### Cache versionné

Le bump d’algorithme peut réduire temporairement les hits `media_metadata_cache`, mais les médias déjà liés localement ne repassent pas par le backend.

### Donnée stale

Assumé : T28 ne change pas la politique de fraîcheur F45.

---

# 10. Plan de développement

## Tâche 1 — Ajouter le lookup PostgreSQL strict

- [x] Implémenter la résolution exacte backend-first.

Objectif:
Retourner une œuvre uniquement si `kind + titre + année` identifient une ligne unique.

Fichiers:
- `backend/src/Catalog/ExternalMediaRepository.php`
- tests d’intégration repository / moteur

Validation:
- 0 candidat;
- 1 candidat;
- 2 candidats;
- année différente;
- année absente;
- film/série séparés.

---

## Tâche 2 — Ajouter le fallback titre original / alternatif

- [x] Ajouter la seconde passe stricte.

Objectif:
Réutiliser une œuvre connue sous un autre titre sans introduire de fuzzy matching.

Fichiers:
- `backend/src/Catalog/ExternalMediaRepository.php`
- tests associés

Validation:
- alt exact + année = hit;
- alt exact + mauvaise année = miss;
- alt partagé = miss/fallback;
- passe alternative non appelée si le titre principal a déjà matché.

---

## Tâche 3 — Brancher le PostgreSQL-first dans le moteur

- [x] Modifier `CatalogMatchEngine::resolve()`.

Objectif:
Exécuter la fast lane avant tout appel TMDB.

Fichiers:
- `backend/src/Catalog/CatalogMatchEngine.php`
- `backend/src/Catalog/CatalogMatchResult.php` si nécessaire
- `backend/tests/Integration/CatalogMatchEngineTest.php`

Validation:
- provider fake `searchCalls == 0` sur hit PostgreSQL;
- provider fake `hydrateCalls == 0`;
- ambiguity → provider appelé.

---

## Tâche 4 — Éviter `hydrate()` après un search sur un tmdbId déjà stocké

- [x] Réutiliser la fiche existante après `/search`.

Objectif:
Diviser par deux le nombre d’appels fournisseur dans ce scénario.

Fichiers:
- `backend/src/Catalog/CatalogMatchEngine.php`
- `backend/src/Catalog/ExternalMediaRepository.php`
- tests moteur

Validation:
- `searchCalls == 1`;
- `hydrateCalls == 0`;
- même `externalId`;
- fiche complète retournée.

---

## Tâche 5 — Préserver l’hydratation des identités incomplètes

- [x] Tester et sécuriser le chemin identité-only.

Objectif:
Ne pas considérer une simple ligne `tmdb_media` comme une fiche hydratée.

Validation:
- identité connue sans `tmdb_movies/tmdb_series`;
- `searchCalls == 1`;
- `hydrateCalls == 1`;
- persistance correcte.

---

## Tâche 6 — Versionner et sécuriser le cache de matching

- [x] Adapter la version du moteur et les tests de cache.

Objectif:
Éviter la collision sémantique entre ancien et nouveau pipeline.

Fichiers:
- `backend/src/Catalog/CatalogMatchEngine.php`
- tests API/cache

Validation:
- version attendue dans le résultat;
- cache frais évite tout appel moteur/provider;
- nouvelle version ne casse pas les anciennes données persistées.

---

## Tâche 7 — Tests API de non-régression

- [x] Compléter les tests de `/matches` et `/matches/batch`.

Fichiers:
- `backend/tests/Integration/CatalogApiTest.php`
- tests existants pertinents

Validation:
- contrat JSON inchangé;
- `externalId` opaque;
- match PostgreSQL identique au format TMDB;
- erreurs fournisseur inchangées.

---

## Tâche 8 — Validation performance

- [x] Mesurer les chemins principaux.

Scénarios:
- 100 hits cache;
- 100 hits PostgreSQL exacts;
- 100 misses PostgreSQL avec tmdbId déjà hydraté;
- 100 médias totalement inconnus.

Validation:
Documenter au minimum :

```text
search provider calls
hydrate provider calls
temps total
```

Attendu:
- hit PostgreSQL → 0 provider;
- search + DB hit → 1 provider;
- nouveau média → comportement actuel.

---

# 11. Notes de développement

## Implémentation (étape 5)

- `ExternalMediaRepository::findStrictConsolidatedMatch()` ajouté : passe 1 (`normalized_title` +
  année exacte), passe 2 si passe 1 vide (titre original/alternatif exact + année exacte), chaque
  passe en `LIMIT 2` pour distinguer 1 candidat de 2+ sans jamais tolérer l'ambiguïté (R4).
  `strictMatchByNormalizedTitle()`/`strictMatchByAlternativeTitle()` restent privées, l'unicité est
  centralisée dans `soleMatch()`.
- `CatalogMatchEngine::resolve()` réordonné : backend-first PostgreSQL (si année connue) → recherche
  TMDB → réutilisation par `tmdbId` déjà hydraté (`reuseStored()`, factorisé pour les deux cas) →
  hydratation TMDB classique. `findByTmdb()` est appelé une seule fois et son résultat réutilisé pour
  éviter un second aller-retour `findOrCreateForTmdb()` quand l'identité existe déjà sans fiche
  (chemin R6).
- `ALGORITHM_VERSION` passé à `3` (bump volontaire, §8.6) : la clé de cache `media_metadata_cache`
  l'intègre déjà (`CatalogService::matchWithoutThrottle()`), donc les requêtes en cache sous
  l'ancienne version ne sont pas invalidées à tort, elles cessent simplement d'être des hits.
- `CatalogMatchResult::reused()` (dead code du moteur de scoring F45 retiré par le hotfix, jamais
  rappelé depuis) supprimé plutôt qu'adapté : son préfixe `postgresql-first:` aurait produit des
  valeurs `method` différentes de celles spécifiées en §8.5. Les quatre méthodes
  (`postgresql-exact-title-year`, `postgresql-alternative-title-year`, `tmdb-first-result-existing`,
  `tmdb-first-result`) passent maintenant directement par `CatalogMatchResult::matched()`.
- **Piège PDO découvert en écrivant les tests** : `EXTRACT(YEAR FROM ...) = :year::int` ne filtre pas
  correctement — PDO (mode émulé, pgsql) interprète mal le `::` collé juste après un placeholder
  nommé et le paramètre reste lié en texte, ce qui a d'abord produit un faux positif (l'un des tests
  d'ambiguïté échouait en sens inverse : 0 appel provider au lieu de 1). Remplacé par
  `CAST(:year AS integer)`, déjà le style utilisé ailleurs dans ce fichier (`CAST(:videos AS jsonb)`).
- **Bug de test préexistant découvert par T28, pas introduit par T28** : `TestDatabase::reset()` ne
  vide que les tables auth (`accounts`, `profiles`, …), jamais `external_media`/`tmdb_media`/
  `tmdb_movies`/`tmdb_series`. Sans lecture par titre, cette omission n'avait jamais d'impact
  observable ; avec le backend-first, un titre/tmdbId réutilisé d'un test à l'autre (ex. `CatalogApiTest`
  hydrate systématiquement sous le titre `"Dune"` avec un `tmdbId` dérivé de l'année) faisait
  apparaître une réutilisation inattendue. Corrigé en ajoutant un `TRUNCATE` ciblé (même pattern que
  le `TRUNCATE TABLE media_metadata_cache` déjà présent dans `CatalogApiTest`) dans les `setUp()` de
  `CatalogMatchEngineTest` et `CatalogApiTest` — pas touché `TestDatabase::reset()` global pour ne pas
  élargir le périmètre à des tests hors T28.
- Suite complète : `242 tests, 1116 assertions`, verte y compris en ordre aléatoire
  (`--order-by=random`).

## Mesure de performance (étape 5, Tâche 8)

Mesuré directement contre PostgreSQL (conteneur `backend-php-test-1`), moteur réel + provider factice
comptant `search`/`hydrate`, 100 résolutions par scénario :

| Scénario | Temps total | `search` | `hydrate` |
|---|---|---|---|
| 100 hits PostgreSQL exacts (titre+année déjà consolidés) | 29.3 ms | 0 | 0 |
| 100 misses PostgreSQL, `tmdbId` déjà hydraté après `/search` | 75.6 ms | 100 | 0 |
| 100 médias totalement inconnus | 213.9 ms | 100 | 100 |

Conforme aux attentes §10 Tâche 8 : hit PostgreSQL → 0 appel fournisseur ; search + fiche déjà stockée
→ 1 appel fournisseur (`search` seul) ; média neuf → comportement inchangé (`search` + `hydrate`).

Mesures à conserver pour préparer T29 :

```text
% cache match
% PostgreSQL exact
% PostgreSQL alt
% TMDB search → fiche déjà connue
% TMDB search → hydrate
```

Ces ratios permettront de dimensionner le débit batch sans deviner la proportion réelle de misses fournisseur.

## Corrections (étape 7)

Corrige les 4 points de la review (§12) — 2 majeurs, 2 mineurs. Suite complète : **249 tests, 1149
assertions**, verte en ordre fixe et `--order-by=random`.

### R1 — Réutilisation locale-safe

- `tmdb_media.locale` ajouté (migration `013_...sql`) : mémorise la locale d'hydratation. `NULL` pour
  les fiches antérieures à ce correctif — traité comme incompatible avec toute locale demandée
  plutôt que supposé `fr-FR`, donc ces fiches se réhydratent proprement au prochain miss plutôt que
  de rester bloquées avec une locale inconnue.
- `persistMovie()`/`persistSeries()` reçoivent désormais `$locale` et l'écrivent.
- `findStrictConsolidatedMatch()` filtre par locale dans le SQL (passe 1 et passe 2, jointure sur
  `tmdb_media`) : une fiche dans la mauvaise locale n'est même pas candidate au backend-first.
- `CatalogMatchEngine::reuseStored()` (chemin `tmdbId` après `/search`, qui n'a pas ce filtre SQL en
  amont) compare explicitement `$row['locale'] !== $locale` et retourne `null` sinon — l'appelant
  retombe alors sur l'hydratation normale dans la locale demandée.
- Tests ajoutés : fiche `fr-FR` non réutilisée pour une requête `en-US` (backend-first **et**
  réutilisation par `tmdbId`), plus un test de non-régression prouvant que le cas nominal
  (locale identique) n'est pas cassé par ce contrôle.

### R2 — Passe titre alternatif indexée

- Migration `013_...sql` : fonction SQL immuable `cstv_lower_text_array()`, colonne générée
  `alternative_titles_lower TEXT[] STORED` sur `tmdb_movies`/`tmdb_series`, index `GIN` dessus, et
  index d'expression `LOWER(original_title|original_name)`.
- Requête réécrite : `LOWER(original_title) = LOWER(:raw_title) OR ARRAY[LOWER(:raw_title)] <@
  alternative_titles_lower` — les deux branches deviennent indexables (l'opérateur `<@` est celui
  que `GIN` sait accélérer sur un tableau, contrairement à `EXISTS unnest(...) WHERE LOWER(alt) =
  ...`).
- Mesuré avec `EXPLAIN (ANALYZE, BUFFERS)` sur 30 000 lignes `tmdb_movies` (volume représentatif),
  requête passe 2 sur un titre absent du catalogue (le pire cas — celui qui scannait toute la table) :

  | | Plan | Buffers | Temps |
  |---|---|---|---|
  | Avant (index désactivés, mêmes prédicats) | `Seq Scan` sur `tmdb_movies` (30000 lignes filtrées) + `Seq Scan` sur `tmdb_media` | 1220 | 5.3 ms |
  | Après (index d'expression + GIN) | `Bitmap Heap Scan` (`BitmapOr` sur les 2 index) + `Index Scan` sur `tmdb_media` | 212 | 1.6 ms |

  Le point important n'est pas le facteur (~3.5×) à 30k lignes mais la forme du plan : `Seq Scan`
  avec `Rows Removed by Filter: 30000` scale linéairement avec la taille du catalogue consolidé,
  tandis que le plan indexé reste borné par la sélectivité du titre recherché — la question ouverte
  du ticket (§6, coût de la passe alternative à grande échelle) est donc close.

### R3 — Couverture backend-first séries

- `seedStoredSeries()`/`series()` ajoutés au double de test (mêmes formes que `seedStoredMovie()`/
  `movie()`, adaptées aux colonnes `name`/`original_name`/`first_air_date`).
- 3 tests série : titre exact + année (0 provider), titre alternatif + année (0 provider), `tmdbId`
  déjà hydraté après `/search` (1 `search`, 0 `hydrate`) — verrouille la frontière `first_air_date`/
  `original_name` déjà fautive une fois (régression de noms de colonnes en F45).

### R4 — Scénario cache de la Tâche 8

- `CatalogApiTest` : le double `MediaMetadataProvider` compte désormais `searchCalls`/`hydrateCalls`
  et est conservé (`$this->provider`) pour être interrogé depuis les tests.
- Nouveau test `testCatalogMatchCacheHitNeverCallsTheEngineOrTheProvider` : deux appels HTTP
  identiques à `/v1/catalog/matches`, le second doit laisser les compteurs provider inchangés — preuve
  au niveau HTTP que `CatalogService::resolve()` retourne avant `$load()` (donc avant le moteur et le
  provider) sur un hit `media_metadata_cache` frais, plutôt qu'une simple affirmation en prose.

---

# 12. Review

Review effectuée le 2026-08-21 sur le commit `8b49c03e920afa1f5b245fbe7aaa96ee2b5678ad`.

Verdict:
CHANGES REQUESTED

Status:
RESOLVED (étape 7, voir §11 pour le détail des corrections)

L’architecture générale est conforme à T28 : le moteur passe bien par PostgreSQL avant TMDB, exige
titre + année + unicité, réutilise une fiche déjà hydratée après `/search`, conserve le fallback
TMDB et versionne le matching en `ALGORITHM_VERSION = 3`. Les tests ajoutés couvrent correctement
les principaux chemins film et prouvent les gains attendus sur le nombre d’appels fournisseur.

## Critique

Aucun problème critique identifié.

## Majeur

### R1 — La réutilisation PostgreSQL ignore la locale demandée

Description:

`CatalogMatchEngine::resolve()` appelle `findStrictConsolidatedMatch()` puis `reuseStored()` sans
tenir compte de `CatalogMatchRequest::locale`. Le même problème existe après `/search` TMDB :
si le `tmdbId` est déjà hydraté, la fiche PostgreSQL est resservie immédiatement quelle que soit la
locale de la requête.

Or le contrat catalogue accepte au moins `fr-FR` et `en-US`, tandis que `tmdb_media`,
`tmdb_movies` et `tmdb_series` ne mémorisent pas la locale d’hydratation. Une fiche hydratée en
français peut donc être renvoyée à une requête `en-US` (ou l’inverse) sans nouvel appel fournisseur.

Impact:

- changement de comportement par rapport au chemin TMDB antérieur, qui hydrait avec la locale
  demandée ;
- métadonnées potentiellement dans la mauvaise langue ;
- cache de match pourtant séparé par locale, mais alimenté par une ligne PostgreSQL qui ne l’est pas ;
- le problème concerne les deux optimisations principales de T28 : backend-first et réutilisation
  après `/search`.

Correction attendue:

Rendre la réutilisation locale-safe avant validation de T28.

Solution recommandée : mémoriser la locale d’hydratation de la fiche (par exemple dans
`tmdb_media`) et ne réutiliser une fiche que si sa locale est compatible avec la requête. Ajouter
des tests `fr-FR` / `en-US` couvrant le backend-first et le cas `/search` + `tmdbId` déjà hydraté.

Si le produit décide au contraire que les métadonnées consolidées sont toujours canoniquement
`fr-FR`, cette règle doit être explicitement actée et le contrat `en-US` adapté ; elle ne doit pas
être implicite dans T28.

### R2 — La passe titre original/alternatif est un scan non indexé sur le chemin chaud du backfill

Description:

`strictMatchByAlternativeTitle()` est exécutée après chaque miss sur `normalized_title` et filtre
avec :

- `EXTRACT(YEAR FROM release_date|first_air_date)` ;
- `LOWER(original_title|original_name)` ;
- `unnest(alternative_titles)` + `LOWER(...)`.

Aucun de ces prédicats ne bénéficie des index `normalized_title` existants. Sur un catalogue
consolidé qui grossit, chaque média absent du backend peut donc provoquer un scan de la table et des
tableaux de titres alternatifs avant même d’atteindre TMDB.

Le benchmark de l’étape 5 a été réalisé sur la base de test et mesure les chemins fonctionnels, mais
ne valide pas le coût de cette passe à une cardinalité représentative. La question ouverte du ticket
sur le coût réel des `alternative_titles` reste donc non résolue.

Impact:

- risque de déplacer le goulet d’étranglement de TMDB vers PostgreSQL ;
- coût croissant précisément sur les misses, très nombreux pendant une première convergence ;
- T29 augmentera ensuite le débit des batchs et amplifiera ce chemin si le point n’est pas corrigé.

Correction attendue:

Mesurer avec `EXPLAIN (ANALYZE, BUFFERS)` et un volume représentatif avant validation.

La correction doit garantir une recherche bornée/indexable : index d’expression adaptés pour
l’année/titre original et stratégie indexable pour les titres alternatifs (ou retrait de cette passe
du chemin chaud tant qu’elle ne l’est pas). Ajouter un test/benchmark dédié au scénario
« miss normalized_title → recherche alternative ».

## Mineur

### R3 — Le chemin backend-first série n’est pas testé en succès

Description:

Les nouveaux tests prouvent le backend-first sur les films et vérifient qu’un film ne traverse pas
la frontière `movie` / `series`, mais aucun test ne seed une `tmdb_series` puis ne vérifie un succès
backend-first ou un succès par `original_name` / titre alternatif.

Impact:

La branche série utilise des colonnes différentes (`first_air_date`, `original_name`) et cette zone
a déjà connu une régression de noms de colonnes dans F45. Le code actuel paraît correct, mais la
non-régression n’est pas verrouillée.

Correction attendue:

Ajouter au minimum :

- série titre exact + année → 0 `search`, 0 `hydrate` ;
- série titre original/alternatif + année → 0 provider ;
- série `tmdbId` déjà hydraté après `/search` → `search = 1`, `hydrate = 0`.

### R4 — Le benchmark déclaré par la Tâche 8 ne couvre pas le scénario cache

Description:

La Tâche 8 demandait quatre scénarios, dont `100 hits cache`. Les notes d’implémentation documentent
les hits PostgreSQL, les `tmdbId` déjà hydratés et les médias inconnus, mais pas les 100 hits cache.

Impact:

Faible, car le cache n’est pas modifié directement par T28, mais `ALGORITHM_VERSION = 3` change les
clés de matching et le scénario faisait explicitement partie du critère de validation.

Correction attendue:

Ajouter la mesure manquante ou documenter explicitement pourquoi le scénario est couvert par un test
existant avec une assertion garantissant zéro appel moteur/provider.

## Corrections demandées

- [x] R1 — Rendre la réutilisation backend-first et post-`/search` compatible avec la locale.
- [x] R1 — Ajouter les tests de non-régression `fr-FR` / `en-US`.
- [x] R2 — Mesurer le plan/coût de la passe titre alternatif sur un volume représentatif.
- [x] R2 — Rendre cette passe indexable/bornée avant T29.
- [x] R3 — Ajouter les tests backend-first spécifiques aux séries.
- [x] R4 — Compléter la validation du scénario cache de la Tâche 8.

---

# 13. Release

Version:

Commit:

Date:
