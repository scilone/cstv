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

F45 vise désormais une **convergence progressive du catalogue local** : à terme, tous les films et toutes les séries présents dans le catalogue IPTV local doivent disposer de leur matching et de leurs métadonnées de niveau média lorsqu'un match fiable est possible, y compris sur les installations existantes.

Le dimensionnement de référence est un catalogue d'environ **40 000 films et 14 000 séries**. Le remplissage doit donc être persistant, dédupliqué, sérialisé et basse priorité. Le backend doit mutualiser les résultats entre installations afin que plusieurs clients ne répètent pas inutilement les mêmes appels fournisseur.

Les saisons et épisodes constituent une exception volontaire : ils ne participent jamais au backfill global ni à la synchronisation des nouveaux médias. Ils sont hydratés uniquement lorsqu'une fiche série est réellement ouverte.

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
| Déclenchement global | Trois producteurs alimentent la même file : ouverture de fiche, nouveaux médias après sync IPTV, rattrapage des médias sans métadonnées. |
| Backfill | Première installation **et installations existantes** : tous les films/séries sans métadonnées de niveau média sont progressivement mis en file. Pas de burst massif/concurrent. |
| Nouveaux médias | Tout nouveau film/série réellement ajouté lors d'une sync IPTV est mis en file après récupération des hints Xtream disponibles. |
| Métadonnées expirées | **Aucun worker/script périodique de refresh.** Une donnée stale est rafraîchie uniquement lors de l'ouverture du média. |
| Saisons/épisodes | Hydratation uniquement à l'ouverture de la fiche série ; jamais lors du sync/backfill. |
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
| Trigger interactif | L'ouverture de fiche reste le trigger prioritaire ; jamais au simple scroll/affichage vignette. |
| Triggers de fond | Nouveaux médias après sync + rattrapage des médias sans données. Aucun refresh périodique des métadonnées stale. |
| Série | Le niveau série peut être hydraté par sync/backfill ; saisons/épisodes uniquement après ouverture réelle de la fiche série. |
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
| PostgreSQL-first | Avant d'appeler le provider, le backend tente de résoudre le média contre son catalogue consolidé déjà connu. |
| Mutualisation | Un média déjà connu du backend est réutilisé par toutes les installations ; une nouvelle installation ne refait pas inutilement le travail fournisseur des précédentes. |
| Single-flight | Un même match/hydratation/refresh demandé simultanément par plusieurs clients ne déclenche qu'un seul travail fournisseur. |
| Rate limit provider | Budget global côté backend, configurable et conservateur ; `429`/`Retry-After` et backoff priment toujours. |
| Priorité backend | Les appels interactifs issus d'une fiche ouverte doivent pouvoir passer devant le trafic de backfill. |
| Anti-stampede | `refreshAfter` reçoit un jitter ; il ne provoque cependant aucun refresh tant que le média n'est pas ouvert. |

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
- Le dimensionnement de référence est ~40 000 films + ~14 000 séries sur une installation ; saisons/épisodes sont donc exclus du backfill global.
- Le backend et sa clé fournisseur sont partagés entre installations : rate limiting, mutualisation et déduplication doivent être globaux côté serveur.
- Une donnée stale reste utile et n'est jamais entretenue en arrière-plan ; son refresh est opportuniste à l'ouverture.

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
- Une installation existante doit progressivement enrichir les médias déjà présents avant F45, sans devoir attendre leur ouverture.
- Après convergence, une synchronisation IPTV normale n'ajoute au backfill que les nouveaux films/séries détectés.

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

Ces données ne sont jamais recherchées pendant le backfill global. Elles sont créées ou rafraîchies uniquement lorsqu'une fiche série est ouverte.

## 7.5 Déclenchement

Tous les producteurs alimentent la **même file persistée, priorisée et dédupliquée**.

### Priorités

Ordre :

1. `DETAIL_OPEN`
   - média réellement ouvert par l'utilisateur ;
   - si données absentes : matching + hydratation ;
   - si `refreshAfter` dépassé : refresh opportuniste ;
   - si revalidation du match due : revalidation opportuniste ;
   - pour une série : saisons/épisodes manquants ou stale ensuite.
2. `NEW_IPTV_MEDIA`
   - nouveau film/série détecté lors d'une sync ;
   - niveau média uniquement.
3. `MISSING_METADATA`
   - média déjà présent mais jamais enrichi ;
   - couvre première installation et installations existantes ;
   - niveau média uniquement.

Il n'existe **aucune priorité `STALE_METADATA` de fond** : les données expirées ne sont pas scannées ni mises en file périodiquement.

### Ouverture de fiche

1. Afficher immédiatement les données IPTV/Room disponibles.
2. Promouvoir ou insérer le média en `DETAIL_OPEN`.
3. Si la donnée de niveau média est absente ou stale, la rafraîchir en arrière-plan.
4. Pour une série seulement, l'ouverture autorise l'hydratation des saisons/épisodes manquants ou stale.
5. Aucun loader/erreur F45 ne bloque l'écran.

### Synchronisation IPTV

Après une synchronisation distante réussie :

1. déterminer les films/séries réellement nouveaux par rapport à Room avant sync ;
2. laisser l'enrichissement Xtream existant fournir autant que possible année/réalisateur/acteurs/genre/durée/trailer ;
3. regrouper les variantes compatibles par `linkKey` ;
4. mettre en file les nouveaux films/séries en `NEW_IPTV_MEDIA` ;
5. ne jamais mettre saisons/épisodes en file depuis ce chemin.

L'échec de ce post-traitement F45 ne transforme jamais une synchronisation IPTV réussie en échec.

### Backfill / rattrapage

Après migration F45 et au démarrage si nécessaire :

- parcourir Room par pages ;
- sélectionner films/séries sans lien/métadonnées ou jamais tentés ;
- dédupliquer par `linkKey` ;
- `INSERT OR IGNORE` dans la file avec priorité `MISSING_METADATA` ;
- reprendre naturellement après process death jusqu'à convergence.

Le même mécanisme couvre une installation fraîche et une installation existante. Il ne charge jamais ~54 000 médias en mémoire d'un coup.

Un média `UNRESOLVED` n'est pas considéré comme « jamais tenté » : il conserve son état. Sans nouvel indice ni ouverture du média, il n'est pas retenté en boucle.

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
| inconnu | 30 j |
| <1 an | 14 j |
| 1–4 ans | 90 j |
| 5–9 ans | 180 j |
| 10+ ans | 365 j |

### Séries

`inProduction=true` → 7 j.

Pour une série terminée, selon `lastAirDate` :

| Âge depuis fin | Refresh |
|---|---:|
| inconnu | 30 j |
| <1 an | 30 j |
| 1–4 ans | 90 j |
| 5–9 ans | 180 j |
| 10+ ans | 365 j |

### Saisons / épisodes

TTL évalué **uniquement à l'ouverture d'une fiche série** :

| Saison | Refresh cible |
|---|---:|
| série active / saison courante ou récente | 7 j |
| terminée <1 an | 30 j |
| terminée 1–4 ans | 180 j |
| terminée >4 ans | 365 j |

Le détail d'une saison ramène les épisodes attendus par F45 ; aucun appel par épisode n'est planifié.

Une expiration signifie « tenter un refresh », jamais « supprimer ».

**Aucun script/worker périodique ne recherche les lignes expirées.** Le client compare `refreshAfter` à `now` seulement lorsqu'un média est ouvert.

Le backend ajoute un jitter borné autour de `refreshAfter` (cible initiale ±10 %) pour éviter des expirations synchronisées si plusieurs médias sont consultés/hydratés dans la même période.

Le client ne duplique pas les règles de TTL : il respecte uniquement `refreshAfter`.

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

- la revalidation n'est **pas** déclenchée par un worker quotidien ;
- à l'ouverture d'un média, si `revalidateAfter`/`matchVersion` l'exige, renvoyer les hints ;
- même externalId : mettre à jour confiance/méthode/version/date ;
- autre externalId : s'il devient le meilleur match **accepté**, remplacer automatiquement l'ancien ;
- score insuffisant : ne pas remplacer un lien valide existant.

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
- [ ] Première installation : tous les films/séries convergent progressivement vers des métadonnées de niveau média.
- [ ] Installation existante : tous les films/séries déjà présents sont rattrapés progressivement.
- [ ] Nouveau média IPTV : matching + métadonnées racine mis en file après sync.
- [ ] Aucune saison/épisode n'est hydraté lors du sync ou du backfill.
- [ ] Aucune donnée stale n'est rafraîchie en arrière-plan sans ouverture du média.
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

Si expiré **et qu'un client demande le média** → tentative provider dans un flux transactionnel ; collections remplacées seulement après réponse valide. Échec → rollback + ancienne donnée conservée + cooldown via `last_refresh_attempt_at`.

Il n'existe aucun job serveur/client chargé de parcourir périodiquement toutes les lignes stale.

Le refresh est protégé par single-flight par identité : plusieurs installations ouvrant simultanément le même média stale partagent une seule tentative provider.

Le nouveau `refresh_after` reçoit le jitter défini §7.10.

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

### Résolution PostgreSQL-first

Avant une recherche fournisseur, le backend tente de résoudre la requête contre les médias déjà consolidés en PostgreSQL à partir des signaux génériques disponibles (titres, titres alternatifs, année, etc.).

- match interne suffisamment fiable → réutiliser l'`externalId`, sans appel provider ;
- match interne absent/ambigu → passer au provider et au matching multi-passes ;
- les notions de seuil et de marge restent obligatoires pour éviter de propager un mauvais match déjà stocké.

Les demandes concurrentes équivalentes sont dédupliquées en single-flight côté backend.

Cette résolution PostgreSQL-first est essentielle au backfill des grandes installations : plus le backend connaît d'œuvres, moins une nouvelle installation génère d'appels provider.

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
- `external_hydration_queue`
- état de résolution des médias non matchés, intégré au lien ou table dédiée

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

`external_hydration_queue` porte au minimum :

```text
kind
providerId
reason            // DETAIL_OPEN, NEW_IPTV_MEDIA, MISSING_METADATA
priority
createdAt
nextAttemptAt
attemptCount
PRIMARY KEY(kind, providerId)
```

L'état `UNRESOLVED` conserve `lastMatchAttemptAt`, `matchVersion` et éventuellement `retryAfter` même sans externalId afin qu'un média impossible à matcher ne soit pas repris en boucle comme « jamais tenté ».

Les requêtes de backfill sont paginées/indexées ; aucun scan ne matérialise ~54 000 lignes en mémoire.

## 8.10 Propagation `linkKey`

Après match : rechercher les variantes locales de même `linkKey`, vérifier la compatibilité d'année T21 et créer les mêmes liens externalId sans toucher les tables Xtream.

## 8.11 Hydratation Android

Une file locale persistée/dédupliquée est drainée par un WorkManager unique :

- `NetworkType.CONNECTED`
- maximum une hydratation externe active
- reprise après process death
- déduplication `(kind, providerId)`
- priorité `DETAIL_OPEN > NEW_IPTV_MEDIA > MISSING_METADATA`
- retry/backoff
- aucun trigger au scroll

Producteurs :

1. ouverture de fiche ;
2. post-traitement d'une sync IPTV pour les nouveaux films/séries ;
3. seeder de backfill/rattrapage pour les films/séries sans données.

Il n'existe **aucun `ExternalMetadataFreshnessWorker`** et aucun periodic work F45 chargé de rafraîchir les métadonnées stale.

L'ouverture d'une fiche peut promouvoir une demande déjà présente dans la file en `DETAIL_OPEN` sans créer de doublon. C'est également à cette occasion que `refreshAfter` et la revalidation du match sont évalués.

Le worker ne traite les saisons/épisodes que lorsqu'une demande `DETAIL_OPEN` concerne une série. Les demandes `NEW_IPTV_MEDIA` et `MISSING_METADATA` s'arrêtent au niveau série.

### Intégration à la sync catalogue

`CatalogSyncManagerImpl`/repositories calculent un delta des médias réellement ajoutés.

Le post-traitement F45 s'exécute après la persistance catalogue et après la collecte des hints Xtream disponibles. Comme les autres post-traitements non critiques, un échec d'enqueue F45 ne transforme pas une sync catalogue réussie en retry/échec.

### Backfill installations fraîches et existantes

Un `ExternalMetadataBackfillSeeder` (ou équivalent) parcourt les films/séries sans état de résolution et alimente la file par petits lots.

Le même mécanisme couvre :

- première installation : tous les médias sont initialement manquants ;
- upgrade d'une installation existante : le catalogue présent avant F45 est rattrapé ;
- interruption : le prochain démarrage/reprise continue depuis l'état Room réel.

Le seeder ne cherche que les **données absentes**. Il ignore toute ligne déjà hydratée même si `refreshAfter` est dépassé.

## 8.12 Revalidation

Échéance du lien distincte du refresh des métadonnées. Politique initiale ajustable : CERTAIN ~180j, STRONG ~90j, PROBABLE ~30j, UNRESOLVED ~1–7j.

Cette échéance n'est pas scannée périodiquement. À l'ouverture du média, si la revalidation est due, renvoyer les hints. Nouveau meilleur match accepté → remplacement automatique (choix 3B). Anciennes métadonnées deviennent orphelines mais non destructrices ; maintenance différée possible.

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

Backend :

- indexes sur `(kind, tmdb_id)`, refresh, FK/relations ;
- top 2/3 seulement en passe 2 ;
- `append_to_response` lorsque pertinent ;
- PostgreSQL-first avant provider ;
- single-flight pour match/hydratation/refresh identiques ;
- rate limiter **global** autour de `TmdbClient`, partagé par toutes les installations ;
- débit initial conservateur et configurable (cible de départ ~2 req/s soutenues, petit burst borné ~5), sans dépendre contractuellement d'un quota provider fixe ;
- toute réponse `429` respecte `Retry-After` lorsqu'il est présent puis applique un backoff ;
- le trafic interactif doit être prioritaire sur le backfill.

Android :

- aucune jointure externe dans les listes chaudes tant que F45 n'affiche pas ces données ;
- une hydratation active max ;
- backfill paginé ;
- aucune saison/épisode pendant le backfill ;
- aucun scan périodique des metadata stale ;
- écritures transactionnelles ;
- pas de duplication des métadonnées dans les recommandations.

## 8.17 Risques

1. Faux match — principal risque ; multi-pass + marge + revalidation.
2. Choix 3B — un nouvel algo peut remplacer un bon match ; seuil+marge+corpus de tests obligatoires.
3. Compatibilité vieilles APK — maintenir `id`/`ageRatingFr` pendant la transition.
4. Volume Room — limiter hydratation récursive et prévoir maintenance.
5. Box faibles — sérialisation WorkManager obligatoire.
6. Classification internationale — mapping fortement testé.
7. Images `original` — trafic potentiellement élevé mais politique serveur ajustable.
8. Cache T22 ancien — versionner les clés.
9. Backfill ~54 000 médias — file persistée, pagination, déduplication `linkKey` et rate limit obligatoires.
10. Plusieurs installations simultanées — PostgreSQL-first + single-flight + priorité interactive nécessaires pour protéger le provider partagé.
11. Données stale longtemps non consultées — choix assumé : elles ne coûtent rien tant que le média n'est pas ouvert.

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
      +-- external_hydration_queue
      |      ^
      |      +-- DETAIL_OPEN
      |      +-- nouveaux médias après sync IPTV
      |      +-- backfill médias sans metadata
      |
      v HTTPS /v1/catalog + X-CSTV-Device-Type

Backend CSTV
  CatalogAction
      |
  CatalogService
      +-- CatalogMatchEngine
      +-- ExternalMediaRepository
      +-- MediaMetadataCacheRepository
      +-- ProviderSingleFlight
      +-- ProviderRateLimiter
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
trigger
  |-- ouverture fiche
  |-- nouveau média IPTV
  |-- média existant sans metadata
  |
  +--> enqueue dédupliqué/priorisé
        -> hints Room
        -> POST /matches
        -> recherche PostgreSQL consolidée
        -> si match interne fiable : réutiliser externalId
        -> sinon provider sous rate limiter global
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
sync/backfill
  -> match série
  -> persist métadonnées série
  -> STOP

ouverture fiche série
  -> vérifier série + refreshAfter
  -> saisons/épisodes manquants ou stale
  -> hydratation séquentielle à la demande
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

**Validation** : mappings sales, 13/15/17, médiane paire, null, aucune URL persistée, aucune hydration récursive reco ; rate limiter global, priorité interactive, `429/Retry-After` et single-flight couverts par tests.

---

## Tâche 3 — Matching multi-passes versionné

**Objectif** : remplacer le premier résultat par un score fiable et revalidable.

**Fichiers** : nouveaux `CatalogMatchEngine`, `CatalogMatchHints`, `CatalogMatchCandidate`, `CatalogMatchResult`; `TmdbMediaMetadataProvider.php`; `CatalogService.php`; corpus tests.

**Travail** : résolution PostgreSQL-first, puis top N provider si nécessaire, passe 1, top 2/3 passe 2, normalisation noms, année ±1/±2, cast/director/runtime/trailer, seuil+marge, confidence/method/version, cache key versionnée, single-flight de recherches équivalentes.

**Validation** : média déjà connu → zéro appel provider ; homonymes, mauvais premier résultat, année décalée mais indices forts, trailer identique, scores trop proches → unresolved, changement version invalide cache, concurrence identique dédupliquée.

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

**Objectif** : garantir une hydratation priorisée, persistante et non bloquante sur box faibles.

**Fichiers** : nouveau `ExternalMetadataHydrationWorker.kt`, `ExternalHydrationRequestEntity/Dao` ou équivalent, repository, hooks ViewModel/use case à l'ouverture fiche.

**Travail** : queue persistée/dédupliquée, raisons/priorités, promotion DETAIL_OPEN, work global unique, CONNECTED, une hydratation active, retry/backoff, reprise process death, aucun scroll trigger ; saisons/épisodes autorisés uniquement pour DETAIL_OPEN ; refresh/revalidation stale évalués uniquement à l'ouverture.

**Validation** : 10 demandes rapides séquentielles, doublon = une entrée, promotion de priorité, interruption/reprise, erreur item ne bloque pas suivants, longue série non parallélisée, aucun détail saison depuis les priorités de fond, aucune metadata stale mise en file sans ouverture, tests JVM.

---

## Tâche 8 — Sync des nouveaux médias et backfill des installations

**Objectif** : faire converger installations fraîches et existantes sans conditionner le niveau média à l'ouverture d'une fiche.

**Fichiers** :
- `CatalogSyncManagerImpl.kt`
- `DatabaseSyncWorker.kt`
- repositories/DAO VOD + séries pour delta/pagination
- nouveau `ExternalMetadataBackfillSeeder.kt` ou équivalent
- hooks de démarrage/reprise
- tests JVM sync/backfill

**Travail** :
- calculer les films/séries réellement nouveaux après sync ;
- enqueue `NEW_IPTV_MEDIA` après hints Xtream disponibles ;
- parcourir par pages les films/séries sans état F45 et enqueue `MISSING_METADATA` ;
- dédupliquer par `linkKey` ;
- préserver `UNRESOLVED` pour éviter les boucles ;
- aucune saison/épisode dans ces chemins ;
- aucun scan de `refreshAfter`.

**Validation** :
- fresh install : backlog racine créé progressivement ;
- upgrade installation existante : catalogue précédent rattrapé ;
- sync sans nouveau média : aucun enqueue inutile ;
- sync avec variantes même `linkKey` : un matching réseau représentatif ;
- process death : reprise ;
- metadata stale existante : ignorée par le seeder ;
- échec F45 post-sync : sync IPTV reste réussie.

---

## Tâche 9 — Migrer les consommateurs canonicalId et nettoyer TMDB côté app

**Objectif** : externalId devient la seule identité externe du nouveau code Android.

**Fichiers** : `TmdbCatalogMatcher.kt`→`ExternalCatalogMatcher.kt`, `TmdbSessionRefreshGate.kt`→`CatalogSessionRefreshGate.kt`, Trending/Popular use cases/repos, trailer, CanonicalMediaLink repository.

**Travail** : lookup externalId, fallback matcher générique, UUID opaque, trailer externalId, suppression noms/champs TMDB.

**Validation** : Trending/Popular non-régression, lookup batch, UUID jamais parsé, cache se reconstruit progressivement.

---

## Tâche 10 — Migrer F44 vers l'âge exact

**Objectif** : consommer `ageRating: Int?` sans changer les seuils/parcours parental.

**Fichiers** : `AgeRating.kt` ou séparation `ParentalAgeLimit`, `ParentalAccessPolicy.kt`, `ContentClassificationRepository.kt`, `CanPlayContentUseCase.kt`, `DownloadUseCases.kt`, tests F44.

**Travail** : séparer seuil/classification, source ExternalMetadata, null défensif, grants permanents/one-shot inchangés, plus de dépendance app `ageRatingFr`.

**Validation** : 13 vs 12/16, 15 vs 12/16, 17 vs 16/18, null, profil non bridé sans coût réseau, download, grants non régressés.

---

## Tâche 11 — Validation transversale et mesures

**Objectif** : préparer l'étape 6 de review.

**Travail** : PHPUnit backend, `./gradlew testDebugUnitTest`, `lintDebug`, `assembleDebug`, validation OpenAPI, absence migration destructive/appel TMDB direct côté app, logs, cache versionné, métriques PERF matching/hydratation.

**Validation** : tout vert ; aucun test manuel/device requis conformément à AGENTS.md.

Ajouter au bilan : volume de backlog initial, débit de convergence, taux PostgreSQL-first, appels provider évités, `429`, temps estimé de convergence, et vérification qu'aucun refresh stale ne part sans ouverture de fiche.

---

# 11. Notes de développement

À compléter à l'étape 5.

Mesures à collecter : distribution des scores/marges, taux CERTAIN/STRONG/PROBABLE/UNRESOLVED, taux de passe 2, taux de résolution PostgreSQL-first, appels provider/match, `429`/backoff, profondeur du backlog, débit de convergence, durée hydratation, taille Room, volume image par device, taux de remplacement lors des revalidations.

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
