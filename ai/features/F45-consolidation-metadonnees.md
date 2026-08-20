# F45 - Consolidation des données IPTV par métadonnées externes

## Informations générales

Status:
TASK BREAKDOWN

Created:
2026-08-20

Dépendances:
- T21 — Normalisation des titres, extraction des attributs et clé de liaison entre médias
- T22 — Centralisation des appels TMDB dans le backend
- T24 — Persistance locale du canonicalId pour Trending/Popular
- F44 — Restriction par âge sur un profil

> **Note d'identifiant** : F45 est utilisé pour la consolidation des données IPTV par métadonnées externes, fondation à long terme du catalogue enrichi.

---

# 1. Description

CSTV doit consolider progressivement les données de son catalogue IPTV à partir de métadonnées externes, actuellement fournies par TMDB côté backend, sans rendre l'application dépendante de TMDB ni d'aucun fournisseur particulier.

L'application ne connaît que des identifiants CSTV opaques (`externalId`) et des métadonnées métier. Aucun identifiant TMDB ni aucune règle liée au fournisseur ne doit traverser jusqu'au modèle applicatif.

Le backend conserve durablement les données externes utiles dans PostgreSQL. L'application conserve une copie locale relationnelle dans Room. L'enrichissement est opportuniste, progressif, silencieux et non bloquant, en particulier sur les box Android TV peu puissantes.

F45 constitue d'abord une fondation de données : les nouvelles métadonnées ne remplacent pas encore les informations visibles des fiches, de la recherche ou des recommandations existantes. Exception explicitement décidée : F44 migre vers la nouvelle classification d'âge exacte.

---

# 2. Contexte

Les fondations existantes sont utiles mais encore transitoires :

- T21 fournit `cleanTitle`, `releaseYear`, `linkKey` et les attributs techniques ;
- T22 centralise les appels TMDB derrière le backend et fournit `MediaMetadataProvider` ;
- T24 persiste localement l'association entre médias IPTV et `canonicalId` ;
- F44 consomme `ageRatingFr` pour le contrôle parental.

Limites actuelles :

1. `canonicalId` vaut encore en pratique `movie:<tmdbId>` ou `series:<tmdbId>` ;
2. le backend agit surtout comme cache de requêtes et non comme base média durable ;
3. plusieurs classes/champs Android restent nommés TMDB ;
4. `ageRatingFr` écrase des valeurs exactes comme 13, 15 ou 17 vers des paliers français ;
5. le matching actuel peut choisir le premier résultat de recherche, ce qui est insuffisant si ces données doivent devenir fiables à long terme ;
6. une collecte massive ou parallèle dégraderait les appareils modestes.

---

# 3. Objectif

- Donner à chaque film/série enrichi une identité CSTV stable, indépendante du fournisseur.
- Maximiser le taux de matching fiable en exploitant tous les indices IPTV disponibles.
- Conserver la qualité de chaque match (`confidence`, `matchMethod`, `matchVersion`) pour permettre sa revalidation.
- Persister durablement les métadonnées externes côté backend et localement côté app.
- Ne jamais bloquer ouverture, navigation ou lecture sur l'enrichissement.
- Hydrater séquentiellement pour protéger CPU, mémoire, réseau et Room sur box lentes.
- Préserver la donnée source avec le moins de transformations irréversibles possible.
- Permettre de remplacer/compléter TMDB sans modifier le contrat conceptuel de l'app.
- Migrer F44 vers un âge numérique exact tout en conservant les seuils de profils 0/10/12/16/18.
- Préparer les futurs usages : recherche enrichie, recommandations, fiches, saisons/épisodes, consolidation du catalogue.

---

# 4. Décisions produit

## 4.1 Étape 1

| Sujet | Décision |
|---|---|
| Indépendance fournisseur | L'app ne connaît pas TMDB ; elle manipule seulement `externalId` et des champs métier. |
| Identité | `externalId` CSTV stable ; IDs TMDB backend-only. |
| Persistance backend | Métadonnées utiles conservées durablement en PostgreSQL. |
| Persistance locale | Copie locale relationnelle dans Room. |
| Usage initial | Hydratation principalement ; pas encore de remplacement visuel/recherche/reco. |
| Déclenchement global | À la demande puis progressif en arrière-plan ; pas de backfill massif après sync IPTV. |
| Performance | Nice-to-have, basse priorité, jamais bloquant. |
| Donnée expirée | Une bonne donnée existante reste utilisable sans limite si le fournisseur est indisponible. |
| Source future | Après match fiable, externe prioritaire pour métadonnées communes ; IPTV reste prioritaire pour flux/version/lecture. |
| Matching | Révisé à l'étape 3 : ne pas abandonner tôt ; désambiguïser avec tous les indices disponibles avant `UNRESOLVED`. |
| External IDs fournisseur | Collectés backend-only. |
| Titres alternatifs | Collectés et exposés sous forme de chaînes dédupliquées ; pays non conservé côté app. |
| Recommandations | 20 premières, exposées uniquement comme `externalId`. |
| Images | DB = paths fournisseur ; API = URL finale. |
| Crédits | Non persistés comme métadonnées CSTV ; autorisés temporairement pour le matching. |
| Collections | Hors périmètre. |
| Classification | Âge numérique exact autant que possible ; pas de bucket 0/10/12/16/18. |
| Priorité classification | FR → US → GB → médiane des autres valeurs exploitables. |
| Refresh film | Décroissant avec l'âge du film. |
| Refresh série | Fréquent si active ; sinon basé sur `lastAirDate`. |

## 4.2 Étape 2

| Sujet | Décision |
|---|---|
| Trigger app | Ouverture de fiche uniquement ; jamais au simple scroll/affichage vignette. |
| Série | Série d'abord, saisons/épisodes ensuite progressivement. |
| Locale | `fr-FR` uniquement ; titres originaux/alternatifs conservés. |
| Plusieurs certifications FR/US/GB | Valeur exploitable la plus restrictive. |
| Médiane paire | Arrondi au supérieur. |
| UI | Aucun loader/snackbar/dialogue spécifique F45. |

## 4.3 Étape 3

| Sujet | Décision |
|---|---|
| Compatibilité API | Conserver `/v1/catalog`, ajouter `externalId` à côté de l'ancien `id` pour les anciennes APK. La nouvelle app ignore `id`. |
| Format `externalId` | UUID v4 ; PostgreSQL `uuid`, String opaque Android. |
| Réseau | Toute connexion disponible autorisée pour toute hydratation ; protection via sérialisation/throttling. |
| Images | Header `X-CSTV-Device-Type: mobile|tablet|tv`, invalide/absent → mobile. |
| Tailles mobile | poster w780, saison w500, backdrop w1280, still w500. |
| Tailles tablette | poster w780, saison w780, backdrop original, still w500. |
| Tailles TV | poster w780, saison w780, backdrop original, still w500. |
| Abstraction image | Les tailles TMDB restent internes à l'adapter ; le contrat manipule `ImageContext` + `DeviceType`. |
| Identité backend | `external_media` générique + `tmdb_media` + tables provider spécifiques. |
| Migration canonical | Supprimer `canonical_media_links`, reconstruire progressivement en `external_media_links`. |
| F44 | Migrer dès F45 vers `ageRating` exact. |
| Room | Métadonnées externes dans tables séparées des tables Xtream. |
| Hints de matching | Envoyer tous les indices disponibles : titre, année, réalisateur, acteurs, genres, durée, trailer YouTube ; tous optionnels. |
| Qualité du match | Backend retourne `confidence`, `matchMethod`, `matchVersion` ; Room les persiste. |
| Revalidation contradictoire | Toujours prendre le nouveau meilleur match **accepté**, même s'il remplace un ancien `STRONG/CERTAIN`. |
| Garde-fou remplacement | Le nouveau meilleur doit franchir un seuil absolu et une marge minimale sur le deuxième candidat. |

---

# 5. Hypothèses

- Les détails Xtream fournissent assez souvent année/réalisateur/acteurs/genre/durée/trailer pour améliorer nettement le matching.
- Les hints IPTV peuvent être faux : aucun indice faible isolé ne suffit comme preuve.
- Les crédits TMDB peuvent être lus temporairement pour le matching sans être persistés.
- L'enrichissement externe n'est jamais indispensable à la lecture IPTV.
- Le backend ne persiste ni catalogue IPTV complet, ni profil, ni `providerId` Xtream ; seuls les hints d'un média consulté transitent.
- Le volume Room reste acceptable si l'hydratation est progressive et non récursive.
- Les anciennes APK doivent rester compatibles pendant la transition `/v1`.
- Les profils F44 restent sur les seuils 0/10/12/16/18.

---

# 6. Questions ouvertes

Aucune question fonctionnelle ou architecturale bloquante ne reste ouverte.

Paramètres internes ajustables sans changement de contrat :

- poids exacts du score ;
- seuil d'acceptation et marge minimale ;
- fréquence de revalidation par niveau de confiance ;
- cooldown après échec fournisseur ;
- nombre de candidats détaillés en passe 2 (cible : 2–3) ;
- maintenance locale des métadonnées devenues orphelines.

---

# 7. Spécification fonctionnelle

## 7.1 User stories

- Une fiche s'ouvre immédiatement avec les données IPTV même si l'externe est absent.
- Une box faible ne subit pas de ralentissement dû à l'enrichissement.
- Une panne backend/fournisseur n'efface jamais les dernières bonnes données.
- CSTV doit chercher sérieusement le bon média externe plutôt que prendre arbitrairement le premier résultat.
- Un ancien match doit pouvoir être corrigé automatiquement lorsque l'algorithme ou les hints progressent.
- Le fournisseur doit être remplaçable sans changement conceptuel côté app.

## 7.2 Film

Champs conservés :

- adult
- title
- originalTitle
- originalLanguage
- backdrop
- genres
- originCountry
- overview
- poster
- releaseDate
- runtime
- status
- tagline
- voteAverage
- voteCount
- ageRating

Sous-ressources : keywords, 20 recommandations max, vidéos, titres alternatifs, external IDs backend-only.

`release_dates` sert uniquement à calculer `ageRating`.

Hors périmètre : popularité fournisseur, collections/sagas, crédits persistés, watch providers.

## 7.3 Série

Champs : adult, name, originalName, originalLanguage, overview, backdrop, poster, firstAirDate, lastAirDate, genres, numberOfEpisodes, numberOfSeasons, episodeRunTime, status, inProduction, nextEpisodeToAir, tagline, voteAverage, voteCount, ageRating.

Sous-ressources : keywords, 20 recommandations max, vidéos, titres alternatifs, external IDs backend-only.

`content_ratings` sert uniquement à calculer `ageRating`.

## 7.4 Saison / épisode

Saison : seasonNumber, airDate, name, overview, poster, voteAverage.

Épisode : seasonNumber, episodeNumber, airDate, name, overview, still, runtime, voteAverage, voteCount.

## 7.5 Déclenchement

### Film

1. Ouvrir la fiche.
2. Afficher immédiatement le comportement IPTV existant.
3. Vérifier localement lien/fraîcheur.
4. Si nécessaire, mettre en file une hydratation dédupliquée.
5. Travail réseau/Room silencieux.
6. Aucune erreur F45 affichée.

### Série

1. Ouvrir la fiche.
2. Hydrater le média série.
3. Une fois le match obtenu, traiter les saisons progressivement.
4. Une saison à la fois ; épisodes récupérés avec le détail de saison.
5. Navigation saisons/épisodes indépendante de ce processus.

## 7.6 Recommandations

- 20 premières uniquement.
- Ordre conservé.
- API app : externalIds uniquement.
- La cible d'une recommandation peut avoir une identité sans fiche hydratée.
- Aucun N+1 d'hydratation des 20 recommandations.

## 7.7 Images

Header :

```http
X-CSTV-Device-Type: mobile
```

| Contexte | Mobile | Tablette | TV |
|---|---|---|---|
| Poster média | w780 | w780 | w780 |
| Poster saison | w500 | w780 | w780 |
| Backdrop | w1280 | original | original |
| Still épisode | w500 | w500 | w500 |

Ces tailles sont une politique backend modifiable ; les paths seuls sont persistés.

## 7.8 Classification d'âge

`ageRating: Int?`.

Ordre : FR → US → GB → médiane des autres valeurs numériques exploitables → null.

Dans FR/US/GB, plusieurs valeurs exploitables → la plus restrictive.

Exemples attendus : PG-13 → 13, R → 17, NC-17 → 17, 12A → 12, 15 → 15, 16 → 16, 18 → 18.

## 7.9 F44 après F45

Profil : seuil fermé 0/10/12/16/18.

Média : entier exact nullable.

```text
maxAgeRating == null        -> autorisé
ageRating == null           -> PIN / UNCLASSIFIED
ageRating <= maxAgeRating   -> autorisé
ageRating > maxAgeRating    -> PIN / TOO_MATURE
```

Exemples : 13 vs 12 bloqué ; 13 vs 16 autorisé ; 17 vs 16 bloqué ; 17 vs 18 autorisé.

## 7.10 Fraîcheur

### Films

| Âge | Refresh |
|---|---:|
| inconnu | 15 j |
| <1 an | 7 j |
| 1–4 ans | 30 j |
| 5–9 ans | 90 j |
| 10+ ans | 180 j |

### Séries

`inProduction=true` → 7 j. Sinon même grille selon `lastAirDate`.

Saison courante/récente d'une série active : cible ~7 j ; anciennes saisons décroissantes selon dernier épisode.

Une expiration signifie « tenter un refresh », jamais « supprimer ».

## 7.11 Matching multi-passes

### Hints

```json
{
  "kind": "movie",
  "title": "The Thing",
  "year": 1982,
  "locale": "fr-FR",
  "hints": {
    "director": "John Carpenter",
    "actors": ["Kurt Russell", "Keith David"],
    "genres": ["Horror", "Science Fiction"],
    "runtimeMinutes": 109,
    "youtubeTrailerKey": "..."
  }
}
```

Tous optionnels.

### Passe 1

Prendre plusieurs résultats et scorer : titre principal/original/alternatif, année, genres. Ne jamais valider mécaniquement `results[0]`.

### Passe 2

Si ambigu, détailler seulement les 2–3 meilleurs candidats et comparer : réalisateur/crew pertinent, acteurs communs, durée, genres, trailer YouTube et titres supplémentaires. Les crédits utilisés ici sont jetés après scoring.

### Année

- exacte : très fort bonus ;
- ±1 : toléré ;
- ±2 : pénalité ;
- >2 : forte pénalité.

L'année n'est plus un rejet absolu si d'autres signaux très forts convergent.

### Confiance

Retour backend : `confidence` 0–100, `matchMethod`, `matchVersion`.

Niveaux conceptuels : CERTAIN, STRONG, PROBABLE, UNRESOLVED. Valeurs initiales indicatives : 90 / 75 / 65.

Acceptation = seuil absolu + marge suffisante sur le deuxième candidat, sauf preuve déterminante (par ex. trailer identique + titre compatible).

### Revalidation

- même externalId : mettre à jour confiance/méthode/version/date ;
- autre externalId : s'il devient le meilleur match **accepté**, remplacer automatiquement l'ancien ;
- score insuffisant : ne pas remplacer un lien valide existant ;
- unresolved : retenter plus tard.

## 7.12 Versions IPTV

Une fois une version matchée, appliquer le même externalId aux variantes locales partageant `linkKey` et une année compatible. Un seul matching réseau pour le groupe lorsque possible.

## 7.13 Mode dégradé

Backend indisponible → Room + IPTV. Fournisseur indisponible → dernière donnée backend. Match unresolved → IPTV. Erreur saison → continuer le reste. Erreur Room → lecture IPTV jamais bloquée.

## 7.14 Critères d'acceptation

- [ ] Aucun nouveau concept TMDB côté app.
- [ ] Nouvelle app : externalId uniquement.
- [ ] Anciennes APK : `/v1/catalog` encore compatible.
- [ ] Matching jamais basé mécaniquement sur le premier résultat.
- [ ] Désambiguïsation multi-signaux sur top 2/3 si nécessaire.
- [ ] confidence/method/version persistés localement.
- [ ] Revalidation peut remplacer automatiquement un ancien lien par un nouveau meilleur match accepté.
- [ ] Plusieurs versions IPTV peuvent partager un externalId.
- [ ] Ouverture de fiche indépendante du réseau externe.
- [ ] Série avant saisons ; saisons séquentielles.
- [ ] Aucun trigger au scroll.
- [ ] Tables externes séparées des tables Xtream.
- [ ] Bonne donnée stale conservée en cas de refresh KO.
- [ ] 20 recommandations max, externalIds seulement.
- [ ] Header device appliqué aux URLs d'image.
- [ ] ageRating accepte 13/15/17 etc.
- [ ] F44 compare l'âge exact au seuil.
- [ ] Panne enrichissement ne bloque jamais la lecture IPTV.

---

# 8. Spécification technique

## 8.1 Frontière fournisseur

Architecture backend :

```text
CatalogService
  +-- ExternalMediaRepository
  +-- CatalogMatchEngine
  +-- MediaMetadataProvider
        +-- TmdbMediaMetadataProvider
        +-- TmdbClient
        +-- TmdbCertificationMapper
        +-- TmdbImageUrlResolver
```

Seul le package/adapteur TMDB connaît routes, IDs, certifications et tailles TMDB.

## 8.2 PostgreSQL : identité

```sql
external_media (
  external_id UUID PRIMARY KEY,
  kind VARCHAR(16) NOT NULL CHECK (kind IN ('movie','series')),
  created_at TIMESTAMPTZ NOT NULL
)
```

UUID v4 sans nouvelle bibliothèque externe ; type natif `uuid` en DB.

```sql
tmdb_media (
  external_id UUID PRIMARY KEY REFERENCES external_media(external_id) ON DELETE CASCADE,
  kind VARCHAR(16) NOT NULL,
  tmdb_id BIGINT NOT NULL,
  hydrated_at TIMESTAMPTZ,
  refresh_after TIMESTAMPTZ,
  last_refresh_attempt_at TIMESTAMPTZ,
  UNIQUE(kind, tmdb_id)
)
```

Une recommandation peut créer seulement `external_media + tmdb_media` sans hydratation de fiche.

## 8.3 PostgreSQL : détails

Tables :

- `tmdb_movies`
- `tmdb_series`
- `tmdb_seasons` PK `(series_external_id, season_number)`
- `tmdb_episodes` PK `(series_external_id, season_number, episode_number)`
- `tmdb_genres`
- `tmdb_media_genres`
- `tmdb_keywords`
- `tmdb_media_keywords`
- `tmdb_media_origin_countries`
- `tmdb_series_episode_runtimes`
- `tmdb_recommendations`
- `tmdb_videos`
- `tmdb_external_ids`
- `tmdb_alternative_titles`

Pas de tables persistées `tmdb_release_dates`, `tmdb_content_ratings`, `tmdb_credits`, `tmdb_collections`.

Les films/séries stockent `age_rating` exact et uniquement les paths d'image.

## 8.4 Cache serveur

`media_metadata_cache` reste un cache d'opérations (trending/popular/search/configuration), pas la base média durable.

Versionner le préfixe des clés F45 pour ignorer les anciens payloads T22.

Stale illimité = métadonnées média durables uniquement ; Trending/Popular restent temporels et bornés.

## 8.5 Refresh transactionnel

Si frais → DB immédiate.

Si expiré → tentative provider dans un flux transactionnel ; collections remplacées seulement après réponse valide. Échec → rollback + ancienne donnée conservée + cooldown via `last_refresh_attempt_at`.

## 8.6 Matching backend

Nouveaux composants provider-agnostic :

- `CatalogMatchRequest`
- `CatalogMatchHints`
- `CatalogMatchCandidate`
- `CatalogMatchScore`
- `CatalogMatchResult`
- `CatalogMatchEngine`

`MATCH_ALGORITHM_VERSION = 1`, inclus dans la clé de cache match et renvoyé à l'app.

La popularité et le vote count ne sont jamais utilisés comme preuve d'identité.

## 8.7 API `/v1/catalog`

### Match

`POST /v1/catalog/matches` accepte ancien payload + nouveaux hints.

Réponse cible :

```json
{
  "status": "matched",
  "match": {
    "confidence": 96,
    "method": "title+year+director+cast",
    "version": 1
  },
  "item": {
    "externalId": "uuid",
    "id": "movie:12345",
    "kind": "movie",
    "title": "...",
    "ageRating": 13,
    "ageRatingFr": 12
  },
  "cache": {
    "updatedAt": "...",
    "refreshAfter": "...",
    "stale": false
  }
}
```

`id` et `ageRatingFr` sont legacy/deprecated et absents du nouveau modèle domain Android. `ageRatingFr` doit conserver autant que possible le comportement des anciennes APK pendant la transition.

Statuts : `matched`, `not_found`, `unresolved` ; `item` nullable.

### Nouvelles lectures

- `GET /v1/catalog/items/{externalId}`
- `GET /v1/catalog/items/{externalId}/recommendations`
- `GET /v1/catalog/items/{externalId}/videos`
- `GET /v1/catalog/items/{externalId}/seasons/{seasonNumber}`

Pendant la transition, la route vidéo accepte également l'ancien `movie:<id>` / `series:<id>`.

## 8.8 Images

Le cache interne conserve path + contexte, pas une URL dépendante du device.

À la sérialisation :

```text
ImageContext + DeviceType -> TmdbImageUrlResolver -> URL finale
```

Ainsi `X-CSTV-Device-Type` ne duplique pas les payloads de cache.

## 8.9 Room 38 → 39

Migration additive/non destructive pour les données utilisateur.

Supprimer/recréer uniquement les caches techniques devenus faux :

- `canonical_media_links` → `external_media_links`
- `content_classifications` devient obsolète au profit de l'âge exact externe
- `trailer_cache` recréé sans `resolvedTmdbId` (externalId nullable si nécessaire)

Nouvelles tables :

- `external_media`
- `external_movies`
- `external_series`
- `external_seasons`
- `external_episodes`
- `external_media_genres`
- `external_media_keywords`
- `external_media_origin_countries`
- `external_series_episode_runtimes`
- `external_alternative_titles`
- `external_recommendations`
- `external_videos`
- `external_media_links`
- file technique d'hydratation si nécessaire

`external_media_links` :

```text
kind
providerId
externalId
confidence
matchMethod
matchVersion
matchedAt
PRIMARY KEY(kind, providerId)
INDEX(externalId)
```

Les UUID sont des String en SQLite/Kotlin.

## 8.10 Propagation `linkKey`

Après match : rechercher les variantes locales de même `linkKey`, vérifier la compatibilité d'année T21 et créer les mêmes liens externalId sans toucher les tables Xtream.

## 8.11 Hydratation Android

Une file locale persistée/dédupliquée est drainée par un WorkManager unique :

- `NetworkType.CONNECTED`
- maximum une hydratation externe active
- reprise après process death
- déduplication `(kind, providerId)`
- média principal avant saisons
- saisons séquentielles
- retry/backoff
- aucun trigger au scroll

## 8.12 Revalidation

Échéance du lien distincte du refresh des métadonnées. Politique initiale ajustable : CERTAIN ~180j, STRONG ~90j, PROBABLE ~30j, UNRESOLVED ~1–7j.

À échéance, renvoyer les hints. Nouveau meilleur match accepté → remplacement automatique (choix 3B). Anciennes métadonnées deviennent orphelines mais non destructrices ; maintenance différée possible.

## 8.13 Migration F44

Séparer :

- seuil profil = enum/objet fermé 0/10/12/16/18
- classification œuvre = `Int?`

`ParentalAccessPolicy` compare l'entier exact. `ContentClassificationRepository` s'appuie sur la couche ExternalMetadata/Room et ne dépend plus de `ageRatingFr` comme source de vérité.

Règle défensive null → PIN inchangée.

## 8.14 Nommage Android

- `TmdbCatalogMatcher` → `ExternalCatalogMatcher`
- `TmdbSessionRefreshGate` → `CatalogSessionRefreshGate`
- aucun `resolvedTmdbId` local
- modèles DTO/domain du nouveau chemin sans concept TMDB

Le backend provider spécifique conserve naturellement les classes `Tmdb*`.

## 8.15 Sécurité / confidentialité

- Aucun catalogue IPTV complet ni providerId Xtream persisté backend.
- Hints bornés en taille/nombre.
- Token TMDB backend-only.
- Validation stricte UUID/device header.
- Matching throttle existant conservé/adapté.
- Ne pas journaliser inutilement cast/titres complets en production.

## 8.16 Performance

Backend : indexes sur `(kind, tmdb_id)`, refresh, FK/relations ; top 2/3 seulement en passe 2 ; `append_to_response` lorsque pertinent.

Android : aucune jointure externe dans les listes chaudes tant que F45 n'affiche pas ces données ; une hydratation active max ; écritures transactionnelles ; pas de duplication des métadonnées dans les recommandations.

## 8.17 Risques

1. Faux match — principal risque ; multi-pass + marge + revalidation.
2. Choix 3B — un nouvel algo peut remplacer un bon match ; seuil+marge+corpus de tests obligatoires.
3. Compatibilité vieilles APK — maintenir `id`/`ageRatingFr` pendant la transition.
4. Volume Room — limiter hydratation récursive et prévoir maintenance.
5. Box faibles — sérialisation WorkManager obligatoire.
6. Classification internationale — mapping fortement testé.
7. Images `original` — trafic potentiellement élevé mais politique serveur ajustable.
8. Cache T22 ancien — versionner les clés.

---

# 9. Architecture

## 9.1 Vue globale

```text
Android CSTV
  Xtream/Room
      |
      +-- title/linkKey/year/director/actors/genre/duration/trailer
      |
      v
  ExternalMetadataRepository
      +-- external_media_links
      +-- external_* metadata
      +-- hydration queue
      |
      v HTTPS /v1/catalog + X-CSTV-Device-Type

Backend CSTV
  CatalogAction
      |
  CatalogService
      +-- CatalogMatchEngine
      +-- ExternalMediaRepository
      +-- MediaMetadataCacheRepository
      +-- MediaMetadataProvider
              |
              +-- TmdbMediaMetadataProvider
                  +-- TmdbClient
                  +-- CertificationMapper
                  +-- ImageUrlResolver
                      |
                     TMDB
```

## 9.2 Premier matching

```text
Ouverture fiche
  +--> UI IPTV immédiate
  +--> enqueue
        -> hints Room
        -> POST /matches
        -> search top N
        -> score rapide
        -> si ambigu : détails top 2/3
        -> score enrichi
        -> match accepté
        -> external_media UUID + persistance provider
        -> réponse metadata + confiance
        -> transaction Room
        -> propagation linkKey
```

## 9.3 Série

```text
match série -> persist série -> saison 1 -> persist -> saison 2 -> ...
```

Jamais de parallélisme massif.

## 9.4 Responsabilités

### App

Connaît le catalogue IPTV, fabrique les hints, demande le match, persiste externalId/confiance/métadonnées, orchestre l'hydratation et applique le contrôle parental.

### Backend générique

Crée/résout l'identité CSTV, orchestre matching/refresh, persiste données partagées, expose contrat générique, choisit URLs image.

### Adapter TMDB

Connaît routes/IDs/champs/certifications/tailles TMDB et les transforme en modèles génériques. Aucun détail provider ne traverse vers Android.

---

# 10. Plan de développement

## Tâche 1 — Fondation PostgreSQL externe

**Objectif** : créer identité et stockage durable backend.

**Fichiers** :
- `backend/migrations/009_external_metadata.sql` (nouveau)
- `backend/src/Catalog/ExternalMediaRepository.php` (nouveau)
- `backend/src/Catalog/ExternalMediaIdFactory.php` (nouveau)
- tests backend repository/migration

**Travail** : tables `external_media`, `tmdb_media`, détails/relations, UUID v4, PK/FK/UNIQUE/index refresh, upserts transactionnels.

**Validation** : même TMDB id → même externalId ; rollback snapshot invalide ; migrations + PHPUnit verts.

---

## Tâche 2 — Adapter TMDB complet, classification et images

**Objectif** : hydrater toutes les données retenues sans fuite provider.

**Fichiers** :
- `MediaMetadataProvider.php`
- `TmdbMediaMetadataProvider.php`
- `TmdbClient.php`
- nouveaux `TmdbCertificationMapper.php`, `TmdbImageUrlResolver.php`, `DeviceType.php`, `ImageContext.php`
- tests provider

**Travail** : movie/series append, saisons, 20 reco sans N+1, âge exact, paths image, configuration image cachée, matrice device.

**Validation** : mappings sales, 13/15/17, médiane paire, null, aucune URL persistée, aucune hydration récursive reco.

---

## Tâche 3 — Matching multi-passes versionné

**Objectif** : remplacer le premier résultat par un score fiable et revalidable.

**Fichiers** : nouveaux `CatalogMatchEngine`, `CatalogMatchHints`, `CatalogMatchCandidate`, `CatalogMatchResult`; `TmdbMediaMetadataProvider.php`; `CatalogService.php`; corpus tests.

**Travail** : top N, passe 1, top 2/3 passe 2, normalisation noms, année ±1/±2, cast/director/runtime/trailer, seuil+marge, confidence/method/version, cache key versionnée.

**Validation** : homonymes, mauvais premier résultat, année décalée mais indices forts, trailer identique, scores trop proches → unresolved, changement version invalide cache.

---

## Tâche 4 — API `/v1/catalog` + compatibilité + OpenAPI

**Objectif** : exposer externalId/nouvelles routes sans casser les anciennes APK.

**Fichiers** : `CatalogAction.php`, `CatalogService.php`, `Bootstrap.php`, `backend/openapi.yaml`, `CatalogApiTest.php`.

**Travail** : hints match, match metadata, detail/reco/season, vidéo UUID+legacy id, garder `id`/`ageRatingFr` legacy, header device, bornes input, URLs images après cache.

**Validation** : ancien payload fonctionne, nouveau aussi, device fallback, URLs par device, legacy video + UUID, throttle, OpenAPI.

---

## Tâche 5 — Room 38→39 et stockage générique

**Objectif** : créer le modèle local externe et supprimer les caches provider-couplés sans perte utilisateur.

**Fichiers** : `AppDatabase.kt`, `Migrations.kt`, nouveaux `External*Entity/Dao`, remplacement `CanonicalMediaLink*`, migration `ContentClassificationEntity`, `TrailerCacheEntity`, tests SQL.

**Travail** : migration réelle, nouvelles tables/index, drop canonical cache, pas de conversion `movie:<id>`, préserver profils/favoris/historique/F44/autorisations.

**Validation** : sqlite-jdbc migration, données utilisateur intactes, nouvelles tables/index corrects, plusieurs providerIds → même externalId, aucun fallback destructif.

---

## Tâche 6 — Repository Android + contrat réseau générique

**Objectif** : créer `ExternalMetadataRepository` et le nouveau modèle domain.

**Fichiers** : `CstvCatalogApiService.kt`, `CatalogDtos.kt`, nouveaux modèles/domain repository, `ExternalMetadataRepositoryImpl.kt`, `AppModule.kt`, `proguard-rules.pro`, interceptor header device.

**Travail** : DTO externalId/age/cache/hints, type device, mapping DTO→domain, transactions Room, lookup lien, propagation linkKey, aucun `id` legacy dans domain.

**Validation** : lien frais = zéro réseau ; nouveau match persiste ; linkKey partage lien ; backend KO conserve local ; header device testé.

---

## Tâche 7 — File WorkManager séquentielle

**Objectif** : garantir une hydratation non bloquante sur box faibles.

**Fichiers** : nouveau `ExternalMetadataHydrationWorker.kt`, éventuelle entité/DAO de queue, repository, hooks ViewModel/use case à l'ouverture fiche.

**Travail** : queue persistée/dédupliquée, work global unique, CONNECTED, une hydration active, série puis saisons, retry/backoff, reprise process death, aucun scroll trigger.

**Validation** : 10 demandes rapides séquentielles, doublon = une entrée, interruption/reprise, erreur item ne bloque pas suivants, longue série non parallélisée, tests JVM.

---

## Tâche 8 — Migrer les consommateurs canonicalId et nettoyer TMDB côté app

**Objectif** : externalId devient la seule identité externe du nouveau code Android.

**Fichiers** : `TmdbCatalogMatcher.kt`→`ExternalCatalogMatcher.kt`, `TmdbSessionRefreshGate.kt`→`CatalogSessionRefreshGate.kt`, Trending/Popular use cases/repos, trailer, CanonicalMediaLink repository.

**Travail** : lookup externalId, fallback matcher générique, UUID opaque, trailer externalId, suppression noms/champs TMDB.

**Validation** : Trending/Popular non-régression, lookup batch, UUID jamais parsé, cache se reconstruit progressivement.

---

## Tâche 9 — Migrer F44 vers l'âge exact

**Objectif** : consommer `ageRating: Int?` sans changer les seuils/parcours parental.

**Fichiers** : `AgeRating.kt` ou séparation `ParentalAgeLimit`, `ParentalAccessPolicy.kt`, `ContentClassificationRepository.kt`, `CanPlayContentUseCase.kt`, `DownloadUseCases.kt`, tests F44.

**Travail** : séparer seuil/classification, source ExternalMetadata, null défensif, grants permanents/one-shot inchangés, plus de dépendance app `ageRatingFr`.

**Validation** : 13 vs 12/16, 15 vs 12/16, 17 vs 16/18, null, profil non bridé sans coût réseau, download, grants non régressés.

---

## Tâche 10 — Validation transversale et mesures

**Objectif** : préparer l'étape 6 de review.

**Travail** : PHPUnit backend, `./gradlew testDebugUnitTest`, `lintDebug`, `assembleDebug`, validation OpenAPI, absence migration destructive/appel TMDB direct côté app, logs, cache versionné, métriques PERF matching/hydratation.

**Validation** : tout vert ; aucun test manuel/device requis conformément à AGENTS.md.

---

# 11. Notes de développement

À compléter à l'étape 5.

Mesures à collecter : distribution des scores/marges, taux CERTAIN/STRONG/PROBABLE/UNRESOLVED, taux de passe 2, appels provider/match, durée hydratation, taille Room, volume image par device, taux de remplacement lors des revalidations.

---

# 12. Review

## Critique

## Majeur

## Mineur

## Corrections demandées

---

# 13. Release

Version:

Commit:

Date:
