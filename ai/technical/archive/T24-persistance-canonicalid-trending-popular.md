# T24 — Persistance locale du canonicalId (Trending/Popular)

- **Statut** : IMPLEMENTED
- **Type** : Technique (perf)
- **Objectif** : éviter de rejouer le matching complet `TmdbCatalogMatcher`
  contre tout le catalogue local à chaque chargement Trending/Popular, en
  persistant l'association `canonicalId` (backend CSTV, T22) ↔ média local
  déjà résolue.

## 1. Constat / analyse préalable

- **T21** (`linkKey`) est une clé interne de regroupement des versions d'une
  même œuvre au sein du catalogue local, dérivée (hash) du titre normalisé.
  Non réversible, non prévue pour porter un id externe stable — **non
  réutilisée** ici, conformément à sa vocation d'origine.
- **T22** (`canonicalId`) est un identifiant opaque renvoyé par le backend
  (`movie:438631`, `series:...`) — jamais stocké en Room aujourd'hui, ne vit
  qu'en mémoire (`TrendingTitle.canonicalId`) puis sérialisé tel quel dans le
  blob JSON `SharedPreferences` de `TrendingRepositoryImpl`/
  `PopularRepositoryImpl` (TTL local, invalidé en bloc).
- **`TmdbCatalogMatcher`** : scan linéaire en mémoire, aucun index, aucune
  persistance du résultat par item — rejoué intégralement à chaque cache-miss
  (TTL 24h catalogue / TTL local T22 §8.4).
- **Risque déterminant** : `VodDao.insertStreamsRaw`/`SeriesDao` équivalent
  utilisent `@Upsert` (T20-R4, remplace `INSERT OR REPLACE` pour éviter les
  cascades `ON DELETE CASCADE`), qui régénère **toutes** les colonnes de
  chaque entité à chaque sync Xtream. Un `canonicalId` stocké directement sur
  `vod_streams`/`series_streams` serait donc écrasé à `null` à la prochaine
  resynchronisation, contrairement à `cleanTitle`/`linkKey` (T21) qui
  survivent parce qu'ils sont recalculables depuis le titre à chaque sync.
  `canonicalId` ne l'est pas → **ne pas** ajouter de colonne sur ces tables.

## 2. Architecture retenue

Nouvelle table découplée du catalogue, sur le même principe que `media_refs`
(déjà utilisé pour découpler positions de lecture/téléchargements/favoris du
`streamId` brut) :

```
canonical_media_links
  kind         TEXT NOT NULL      -- "movie" | "series"
  providerId   INTEGER NOT NULL   -- streamId (movie) ou seriesId (series)
  canonicalId  TEXT NOT NULL      -- opaque, jamais parsé
  updatedAt    INTEGER NOT NULL
  PRIMARY KEY (kind, providerId)
  INDEX (canonicalId)
```

- Jamais touchée par la sync catalogue (`VodDao`/`SeriesDao` upsert) → aucun
  risque d'écrasement silencieux à la resynchronisation.
- `canonicalId` non unique en base : plusieurs `providerId` peuvent partager
  le même `canonicalId` (versions multiples d'une même œuvre, exigence §5).
- Résolution batch : `SELECT kind, providerId, canonicalId FROM
  canonical_media_links WHERE canonicalId IN (:ids)` — une requête indexée,
  pas de scan catalogue.
- Écriture : upsert `(kind, providerId) -> canonicalId` après un match réussi
  du fallback `TmdbCatalogMatcher`. Aucun appel backend supplémentaire :
  réutilise les `canonicalId` déjà reçus par Trending/Popular.
- **Orphelins / réattribution de `streamId`** : purge greffée sur
  `CatalogReconciler` (T20 §4.5), qui nettoie déjà `trailer_cache`/
  `downloaded_media` orphelins après une sync réussie — ajout d'une passe
  `DELETE FROM canonical_media_links WHERE (kind, providerId) NOT IN
  (SELECT ... FROM vod_streams/series_streams)`. Borne la fenêtre de
  staleness à l'intervalle entre deux syncs plutôt que de la laisser
  indéfinie (aucun mécanisme de suivi de réattribution d'id n'existe côté
  Xtream, ni ici ni ailleurs dans le projet).

## 3. Flux de résolution (Trending/Popular)

Pour chaque item reçu du backend :
1. Lookup batch Room (`WHERE canonicalId IN (...)`) → hits directs.
2. Miss → `TmdbCatalogMatcher` (comportement actuel, inchangé) sur le
   sous-ensemble catalogue candidat.
3. Match réussi → persistance `(kind, providerId, canonicalId)` +
   révalidation existante (`filterItem`, Bug B-3) inchangée.
4. Sans match → comportement actuel inchangé (pas d'item affiché).

## 4. Fichiers touchés (prévisionnel)

- `data/local/entity/CanonicalMediaLinkEntity.kt` (nouveau)
- `data/local/dao/CanonicalMediaLinkDao.kt` (nouveau)
- `data/local/db/AppDatabase.kt` (version 29 → 30, à revérifier au moment de
  coder — ne jamais se fier au numéro écrit ici)
- `data/local/db/Migrations.kt` (`MIGRATION_29_30`, ajout à `ALL_MIGRATIONS`)
- `domain/usecase/GetTrendingInCatalogUseCase.kt`,
  `GetPopularTop10InCatalogUseCase.kt` (résolution batch avant matcher)
- `data/sync/CatalogReconciler.kt` (purge orphelins)
- Tests unitaires (voir §9 de la demande PO) + migration testée façon
  `MIGRATION_9_10`/sqlite-jdbc

## 5. Résultat de l'implémentation

### Fichiers ajoutés
- `domain/model/CanonicalMediaLink.kt`, `domain/repository/CanonicalMediaLinkRepository.kt`
- `data/local/entity/CanonicalMediaLinkEntity.kt`, `data/local/dao/CanonicalMediaLinkDao.kt`
- `data/repository/CanonicalMediaLinkRepositoryImpl.kt`
- Tests : `GetTrendingInCatalogUseCaseCanonicalTest.kt`,
  `GetPopularTop10InCatalogUseCaseCanonicalTest.kt`, `Migration29To30SqlTest.kt`
  + tests ajoutés à `CatalogReconcilerTest.kt`

### Fichiers modifiés
- `data/local/db/AppDatabase.kt` (v29 → **v30**), `data/local/db/Migrations.kt`
  (`MIGRATION_29_30` / `migration29To30Statements`, ajoutée à `ALL_MIGRATIONS`)
- `di/AppModule.kt` (providers DAO + repository)
- `domain/usecase/GetTrendingInCatalogUseCase.kt`,
  `GetPopularTop10InCatalogUseCase.kt` (résolution batch avant matcher,
  persistance après match réussi, logs `PERF`)
- `data/sync/CatalogReconciler.kt` (purge orphelins, inconditionnelle comme
  `trailer_cache`)
- Tests existants adaptés au nouveau paramètre constructeur (comportement
  par défaut inchangé : `findByCanonicalIds` stubbé vide → chemin matcher
  intégralement exercé, aucune régression fonctionnelle).

### Migration
`MIGRATION_29_30` : `CREATE TABLE canonical_media_links (...)` +
`CREATE INDEX index_canonical_media_links_canonicalId`. Additive uniquement,
aucune table existante touchée — testée via sqlite-jdbc
(`Migration29To30SqlTest`, motif `Migration28To29SqlTest`) : catalogue
existant intact, table créée vide, index présent, plusieurs `providerId`
peuvent partager un `canonicalId`, un même `providerId` coexiste sous deux
`kind` différents (namespaces Xtream distincts).

### Index
- `PRIMARY KEY (kind, providerId)` sur `canonical_media_links` (recherche
  d'orphelins/écriture).
- `INDEX (canonicalId)` (résolution batch `WHERE canonicalId IN (...)`, le
  chemin chaud de la fonctionnalité).

### Tests ajoutés (couvrent la liste demandée §9)
- Média sans `canonicalId` connu → matcher puis persistance de l'association.
- Média déjà associé → résolu sans dépendre du matcher (catalogue construit
  pour que seul le hit canonicalId puisse produire le résultat attendu).
- Plusieurs `providerId` locaux partagent le même `canonicalId`.
- `canonicalId` inconnu → repli sur le matcher (comportement par défaut de
  tous les tests existants, préservé).
- `canonicalId` traité uniquement comme clé opaque (format non conforme
  testé explicitement, jamais interprété).
- Résolution batch : un seul appel `findByCanonicalIds` pour toute la liste
  Trending/Popular, jamais un par item.
- Resynchronisation catalogue : purge des orphelins greffée sur
  `CatalogReconciler`, testée indépendamment (toujours exécutée, échec non
  bloquant, comme `trailer_cache`).
- Migration Room : `Migration29To30SqlTest`.
- Non-régression fonctionnelle Trending/Popular : suite de tests existante
  (`GetTrendingInCatalogUseCaseTest`, `GetPopularTop10InCatalogUseCaseTest`)
  intégralement verte sans modification de leurs attentes.

### Observabilité
Logs `PERF` ajoutés au format demandé, ex. :
`[PERF] Trending canonical lookup: 18/20 hits Room en 4ms, 2 fallback match en 35ms`
`[PERF] Popular movies canonical lookup: 46/50 hits Room en 7ms`

### Mesures avant/après
Non mesurées en conditions réelles (pas d'infra de profiling embarquée dans
ce projet) — le gain attendu est structurel : un item déjà associé passe
d'un scan `O(candidats catalogue filtrés par année)` à une unique requête
Room indexée par lot, batchée pour toute la liste Trending/Popular. Les logs
`PERF` ajoutés permettront de le confirmer en usage réel (ratio hits/misses,
durée lookup vs durée matcher).

### Risques / limites restants
- Le lookup batch case l'index sur `canonicalId` uniquement ; un lookup par
  `(kind, providerId)` (ex. savoir si un média déjà connu a un
  `canonicalId`) resterait un scan si jamais nécessaire plus tard — pas de
  besoin identifié aujourd'hui, non ajouté pour ne pas sur-indexer.
- Fenêtre de staleness bornée à l'intervalle entre deux syncs réussies (purge
  `CatalogReconciler`) : un `streamId` réattribué à une autre œuvre entre
  deux syncs pourrait théoriquement hériter à tort d'un ancien `canonicalId`
  jusqu'à la prochaine réconciliation — risque déjà présent structurellement
  (aucune identité durable côté Xtream), juste rendu visible ici.
- `/v1/catalog/matches` (résolution titre+année côté backend) reste non
  appelée par l'app (état hérité de T22) — non modifié, hors périmètre.

## 6. Hors périmètre / non modifié

- `TmdbCatalogMatcher` lui-même : inchangé, reste le fallback.
- Format/parsing de `canonicalId` : jamais interprété, inchangé (régression
  M2 de T22 à ne pas réintroduire).
- Aucun appel TMDB direct, aucun nouvel appel backend.
- UI : aucun changement visible.
