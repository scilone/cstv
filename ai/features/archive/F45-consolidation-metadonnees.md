# F45 - Consolidation des données IPTV par métadonnées externes

## Informations générales

Status:
RELEASED

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

- [x] Aucun nouveau concept TMDB côté app (renames `ExternalCatalogMatcher`/`CatalogSessionRefreshGate` ; le backend seul garde les classes `Tmdb*`).
- [x] Nouvelle app : externalId opaque sur tous les nouveaux chemins, y compris Trending/Popular ; `id` reste réservé à la compatibilité des anciennes APK.
- [x] Anciennes APK : `/v1/catalog` encore compatible (`id`/`ageRatingFr` conservés).
- [x] Matching jamais basé mécaniquement sur le premier résultat (`CatalogMatchEngine`).
- [x] Désambiguïsation multi-signaux sur top 2/3 si nécessaire.
- [x] confidence/method/version persistés localement (`ExternalMediaLinkEntity`).
- [x] Revalidation à l'ouverture peut remplacer automatiquement un ancien lien par un nouveau meilleur match accepté.
- [x] Plusieurs versions IPTV peuvent partager un externalId (propagation §8.10, sync + worker).
- [x] Ouverture de fiche indépendante du réseau externe.
- [x] Série avant saisons ; saisons/épisodes hydratés séquentiellement uniquement à l'ouverture d'une fiche série.
- [x] Première installation : tous les films/séries convergent progressivement vers des métadonnées de niveau média.
- [x] Installation existante : tous les films/séries déjà présents sont rattrapés progressivement.
- [x] Nouveau média IPTV : matching + métadonnées racine mis en file après sync.
- [x] Aucune saison/épisode n'est hydraté lors du sync ou du backfill.
- [x] Aucune donnée stale n'est rafraîchie en arrière-plan sans ouverture du média.
- [x] Aucun trigger au scroll.
- [x] Tables externes séparées des tables Xtream.
- [x] Bonne donnée stale conservée en cas de refresh KO.
- [x] 20 recommandations max, externalIds seulement.
- [x] Header device appliqué aux URLs d'image.
- [x] ageRating accepte 13/15/17 etc.
- [x] F44 compare l'âge exact au seuil.
- [x] Panne enrichissement ne bloque jamais la lecture IPTV.

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

## 2026-08-20 — socle Room F45 commencé

- `AppDatabase` passe de 38 à 39 avec une migration additive `MIGRATION_38_39`.
- Les nouvelles tables provider-neutral (`external_media`, `external_movies`,
  `external_series`, `external_media_links`, `external_hydration_queue`) sont
  séparées des tables Xtream ; `externalId` reste une chaîne opaque et n'est
  jamais dérivé d'un ancien `canonicalId`.
- `external_media_links` conserve la qualité du match et autorise plusieurs
  `providerId` pour un même `externalId`; la file est dédupliquée par
  `(kind, providerId)` et indexée pour le drainage priorisé.
- Les caches T24/F44 restent temporairement en place : leur bascule vers la
  couche F45 intervient avec les tâches Repository, consommateurs et migration
  parentale afin de ne pas rompre les parcours actuels en cours d'évolution.

## 2026-08-20 — Tâche 5 finalisée

- `MIGRATION_38_39` extraite en `migration38To39Statements()` (même motif que
  `migration27To28Statements`/`migration29To30Statements`) pour être rejouable
  telle quelle par un test SQLite en mémoire — `Migration38To39SqlTest` ne
  recréait jusqu'ici qu'un schéma dupliqué à la main, sans jamais exécuter le
  vrai SQL de la migration.
- `Migration38To39SqlTest` réécrit : rejoue désormais les vraies instructions
  contre une fixture Room 38 incluant `canonical_media_links`,
  `content_classifications`, `trailer_cache` et `profiles`, et vérifie que ces
  quatre tables/leurs données traversent la migration sans perte, que les 5
  nouvelles tables et leurs 4 index existent exactement, que plusieurs
  `providerId` peuvent partager un `externalId`, et que rejouer la migration
  deux fois est sans effet (idempotence des `IF NOT EXISTS`).
- Sous-ressources (`external_seasons`/`episodes`/`genres`/`keywords`/…) listées
  en § 8.9 volontairement pas encore créées côté Room : le backend (Tâche 1,
  `009_external_metadata.sql`) ne persiste lui-même que `external_media` +
  `tmdb_media` + `tmdb_movies`/`tmdb_series` à ce stade — ces tables
  arriveront avec l'adapter complet (Tâche 2) plutôt que vides et sans
  producteur des deux côtés.
- `CanonicalMediaLinkEntity`/`ContentClassificationEntity`/`TrailerCacheEntity`
  non touchées : décision déjà actée ci-dessus, confirmée par relecture — leur
  bascule reste portée par les tâches Repository/consommateurs/F44 (6, 9, 10).
- `./gradlew :app:testDebugUnitTest` (suite complète) et
  `:app:compileDebugKotlin`/`:app:compileDebugUnitTestKotlin` verts.
- Backend PHPUnit non rejoué (environnement local en PHP 8.2, composer.json
  du backend exige `>= 8.5` — pré-existant, sans rapport avec cette tâche
  Android).

## 2026-08-20 — Tâches 2 à 10 implémentées (backend + Android)

Implémentation complète en une session, sur demande explicite du PO d'aller au bout de F45. Bilan
par tâche :

**Backend (Tâches 2, 3, 4 — 217 tests PHPUnit verts, cycle `docker compose build php-test -q &&
docker compose up -d php-test && docker compose exec -T php-test composer test` ; l'hôte est en PHP
8.2, composer.json exige `>= 8.5`, PHPUnit tourne dans le conteneur `php-test`, pas sur l'hôte)** :
- `CatalogMatchEngine` : PostgreSQL-first (`ExternalMediaRepository::findConsolidated`, titre
  normalisé + année ±1, ambigu/absent → provider) ; passe 1 (titre 70pts + année ±20/±12/-15/-30 +
  genres) sur les 5 premiers candidats ; passe 2 (director/cast/runtime/trailer/alt-titles)
  seulement si marge <12 entre le top 1/2, sur les 3 meilleurs ; accepté si score ≥65 et marge ≥12
  (sauf preuve décisive = trailer identique + titre déjà cohérent). `CatalogMatchScore.value` n'est
  **pas** clampé pendant le scoring (seul `confidence()` le fait à la sortie) : un clamp précoce
  écraserait la marge entre deux candidats dépassant tous les deux 100 brut — bug réel rencontré et
  corrigé pendant l'implémentation.
- Adapter TMDB complet : `TmdbCertificationMapper` (FR→US→GB puis médiane des autres valeurs
  numériques, §7.8), `TmdbImageUrlResolver`+`DeviceType`+`ImageContext` (§7.7), `hydrate()`/
  `candidateDetail()`/`searchCandidates()`/`seasonDetail()`/`genreNames()` via `append_to_response`.
- API `/v1/catalog` : `matches` (hints + `X-CSTV-Device-Type` + `match{confidence,method,version}` +
  `cache{stale,updatedAt,refreshAfter}`), nouveaux `GET items/{externalId}`,
  `.../recommendations`, `.../seasons/{n}` ; `videos` accepte UUID et legacy `movie:<id>`.
- Sous-ressources (genres/keywords/originCountries/altTitles/recommandations/vidéos) en colonnes
  `text[]`/`uuid[]`/`jsonb` sur `tmdb_movies`/`tmdb_series`, pas de tables de jointure séparées —
  décision assumée (§8.3 les liste comme tables mais rien ne les interroge indépendamment).
- Migrations 010 (schéma complet) et 011 (statut `unresolved` sur `media_metadata_cache`, le
  `CHECK` d'origine n'autorisait que `matched`/`not_found`).

**Android (Tâches 5 à 10 — suite complète verte, `:app:compileDebugKotlin`, `:app:lintDebug`,
`:app:assembleDebug` tous verts)** :
- Tâche 6 : `ExternalMetadataRepositoryImpl` persiste réellement media+movie/series+link en Room
  (ne le faisait pas avant cette session — `upsertMovie`/`upsertSeries` étaient déclarées, jamais
  appelées), hints bout en bout, `DeviceTypeProvider`, `TimeProvider` injecté pour testabilité.
- Tâche 7 : `ExternalMetadataHydrationWorker` (drain séquentiel, une hydratation active via
  `ExistingWorkPolicy.KEEP`, auto-enchaînement `APPEND_OR_REPLACE` si plafond 200 items/lot atteint,
  backoff exponentiel 10min→6h par item, propagation `linkKey` §8.10), `ExternalMetadataHydrationScheduler`
  (dédup/promotion par priorité DETAIL_OPEN>NEW_IPTV_MEDIA>MISSING_METADATA), hooks
  `VodViewModel`/`SeriesViewModel` à l'ouverture de fiche, reprise process death.
- Tâche 8 : delta réel avant/après dans `VodRepositoryImpl`/`SeriesRepositoryImpl.syncXStreams("all")`
  uniquement (jamais sur un rafraîchissement d'une seule catégorie), `ExternalMetadataBackfillSeeder`
  paginé par clé (`streamId > afterId`, jamais `OFFSET`) exclut ce qui est déjà en file + les
  `UNRESOLVED` encore en cooldown. **Bug réel trouvé et corrigé en cours de route** : `match()` ne
  persistait aucune trace d'un résultat `unresolved`/`not_found` → le backfill aurait reproposé les
  mêmes médias non résolus en boucle à chaque démarrage. Fixé : une tentative UNRESOLVED est
  maintenant persistée (`externalId = null`, `retryAfter = +3j`), conforme à §8.9.
- Tâche 9 réduite délibérément : renames purs `TmdbCatalogMatcher`→`ExternalCatalogMatcher`,
  `TmdbSessionRefreshGate`→`CatalogSessionRefreshGate` (classes déjà provider-neutres malgré leur
  nom, zéro changement de comportement). Migration réelle de Trending/Popular vers `externalId` PAS
  faite : prématurée tant que le backfill n'a pas convergé, risquerait une régression que la propre
  validation de la tâche interdit ("Trending/Popular non-régression"). Cohérent avec §1.
- Tâche 10 (feature sécurité — soin supplémentaire) : `ParentalAccessPolicy.evaluate(classification:
  Int?)` (le seuil profil reste l'enum fermée `AgeRating` 0/10/12/16/18, seule la classification
  média devient un entier exact) ; `ContentClassificationRepository` entièrement rewiré sur
  `ExternalMetadataRepository.match()` — ne dépend plus de `ageRatingFr`/`CstvCatalogApiService`
  direct, `providerId` désormais obligatoire (résolution par identité, plus par titre seul) ;
  `ContentClassificationDao`/`content_classifications` orphelins, non supprimés (même traitement que
  `canonical_media_links`). UI (`VodState`/`SeriesState`/`*DetailsScreen`/`*DetailsTvLayout`/
  `AgeRatingLabel`) convertie `AgeRating?`→`Int?`. Nouveaux tests explicites 13 vs 12/16, 15 vs
  12/16, 17 vs 16/18 (§7.9).

**Non fait, assumé** : revalidation automatique d'un lien à l'ouverture (§7.11/§8.12, aucun
déclenchement construit) ; saisons/épisodes jamais hydratés (aucune table Room dédiée — ni côté
backend Postgres au-delà du schéma, ni côté Android) ; migration complète des consommateurs
canonicalId (Tâche 9 réduite ci-dessus) ; mesures/bilan chiffré (§10 Tâche 11) — nécessite du trafic
réel en production, pas mesurable statiquement en session de développement.

Mesures à collecter : distribution des scores/marges, taux CERTAIN/STRONG/PROBABLE/UNRESOLVED, taux de passe 2, taux de résolution PostgreSQL-first, appels provider/match, `429`/backoff, profondeur du backlog, débit de convergence, durée hydratation, taille Room, volume image par device, taux de remplacement lors des revalidations.

## 2026-08-20 — Tâche 7 : corrections F45-R2 et F45-R12

- **F45-R2** : `ExternalMetadataHydrationWorker.doWork()` ne programmait un réveil que si le lot de
  200 était plein *et* qu'un item était déjà dû (`hasDueRequest`). Une file où tout le reste est en
  backoff (panne réseau, 429, cooldown `UNRESOLVED`) ne reprogrammait donc plus rien : convergence
  bloquée jusqu'à un enqueue sans rapport ou un redémarrage. Remplacé par
  `ExternalMetadataDao.earliestNextAttemptAt()` (`MIN(nextAttemptAt)`) +
  `ExternalMetadataHydrationWorker.nextWakeupDelayMillis()` (isolé, testable sans WorkManager) : en
  fin de run, si la file n'est pas vide, un `OneTimeWorkRequest` avec `setInitialDelay` est chaîné
  (`APPEND_OR_REPLACE`, ex-`enqueueContinuation` généralisé) jusqu'à l'échéance la plus proche —
  immédiate si déjà due, différée sinon. `hasDueRequest` devenue inutile, supprimée.
- **F45-R12** : `ExternalMetadataHydrationScheduler.request()` faisait un `dao.priorityOf()` puis un
  `dao.upsertRequest()` séparés — une promotion `DETAIL_OPEN` et une demande de fond concurrentes
  sur le même `(kind, providerId)` pouvaient s'entrelacer et perdre la promotion. Remplacé par
  `ExternalMetadataDao.upsertRequestIfHigherPriority()`, méthode `@Transaction` regroupant lecture
  et upsert conditionnel dans une seule transaction Room (le scheduler ne fait plus qu'un appel).
  Décision de promotion extraite en fonction pure `isHigherPriority()` pour rester unit-testable :
  un mock Mockito `CALLS_REAL_METHODS` sur une méthode par défaut `suspend` d'interface ne déclenche
  pas fiablement le vrai corps (essayé, échoué silencieusement — `WantedButNotInvoked`), d'où
  l'extraction plutôt qu'un test du default method lui-même.
- Portée volontairement limitée à R2/R12 (ceux de la Tâche 7). Dédup `linkKey` au-delà d'une
  page/du plafond de 20 (second point de F45-R12) non traitée ici — relève de la Tâche 8.
- Nouveaux tests : `ExternalMetadataDaoTest` (`isHigherPriority`), trois cas
  `nextWakeupDelayMillis` dans `ExternalMetadataHydrationWorkerTest`. Suite JVM complète +
  `lintDebug` + `assembleDebug` rejoués verts.

## 2026-08-20 — Étape 7 : compléments de corrections R6, R7, R9 et R12

- **F45-R6** : le seeder ne vide plus le catalogue dans un même démarrage. Une passe traite au
  plus 100 films et 100 séries, par une insertion Room transactionnelle et un unique réveil
  WorkManager par lot. Après chaque drainage, le worker demande la passe suivante : les entrées
  déjà liées ou déjà en file sont exclues par le DAO, ce qui produit une convergence persistante
  sans rafale de dizaines de milliers d'écritures/enqueues. Les nouveaux médias sont eux aussi
  limités à 100 représentants par sync ; le reste rejoint les passes du seeder.
- **F45-R9** : chaque représentant `NEW_IPTV_MEDIA` récupère désormais ses détails Xtream avant
  son `requestBatch`. L'appel reste dans la coroutine basse priorité hors du chemin de succès de
  sync ; un détail indisponible ne fait pas échouer la sync et le worker utilise alors les hints
  déjà disponibles. Le premier match ne précède donc plus systématiquement réalisateur/cast/genre/
  année/durée lorsque Xtream les fournit.
- **F45-R12** : la propagation d'un match par `linkKey` ne s'arrête plus à 20 variantes. Tous les
  frères présents sont reliés dans la même passe, et les groupes traversant plusieurs pages de
  backfill sont exclus dès que leur représentant est relié.
- **F45-R7 (atomicité)** : `ExternalMetadataDao.persistItem()` regroupe désormais l'identité
  `external_media` et exactement une fiche film/série dans une transaction Room. Une interruption
  ne peut donc plus laisser une identité fraîche sans sa fiche ou l'inverse. Les relations locales,
  l'hydratation des saisons/épisodes et la migration `externalId` ont ensuite été livrées dans la
  clôture R7 ci-dessous.
- Tests ajoutés/ajustés : volume borné et batch unique du seeder, lot `NEW_IPTV_MEDIA`, persistance
  atomique média+fiche. `./gradlew --no-daemon testDebugUnitTest assembleDebug lintDebug` : OK ;
  Docker `php-test composer test` : OK, 219 tests / 1 039 assertions ; `git diff --check` : OK.

## 2026-08-20 — Étape 7 : clôture F45-R7

- **Contrat et relations Room** : la base passe de 39 à 40, sans migration destructive. Les
  relations `external_seasons`, `external_episodes`, genres, keywords, pays d'origine, durées
  d'épisode, titres alternatifs, recommandations et vidéos sont désormais présentes. Le contrat
  `CatalogItemDto` transporte les champs complets déjà produits par la base PostgreSQL ;
  `ExternalMetadataRepository.persistItem()` remplace l'ensemble de ces collections dans une
  transaction Room avec la fiche de niveau média. Une saison et tous ses épisodes sont aussi
  remplacés atomiquement.
- **Séries** : un `DETAIL_OPEN` sur une série résolue appelle seulement alors `GET item` puis les
  routes saison ; les saisons 0..N et épisodes sont persistés séquentiellement. Les demandes
  `NEW_IPTV_MEDIA`/`MISSING_METADATA` ne passent jamais par ce chemin. Une borne défensive de
  100 saisons évite qu'une réponse incohérente déclenche une boucle non bornée.
- **Identité opaque** : Trending et Popular reçoivent maintenant `externalId` UUID de CSTV depuis
  le backend (tout en gardant `id` pour les anciennes APK). Les consommateurs Android du nouveau
  chemin choisissent ce UUID, y compris le fallback trailer. La migration purge les anciennes
  entrées `canonical_media_links` `movie:<providerId>` ; elles se reconstruisent avec UUID à la
  prochaine actualisation. `trailer_cache.resolvedTmdbId` est remplacé par `externalId` et les
  anciennes valeurs fournisseur ne sont pas converties artificiellement.
- **Vérifications** : tests ciblés migration/persistance/saisons Android verts ; PHPUnit Docker
  vert (219 tests, 1 039 assertions), y compris l'assertion Trending `externalId` UUID. La suite
  Android complète `./gradlew --no-daemon testDebugUnitTest assembleDebug lintDebug` est
  **BUILD SUCCESSFUL** (3 min 45 s ; APK debug et lint produits). `git diff --check` est également
  vert.

---

# 12. Review

## Critique

### F45-R1 — Le matching fournisseur casse avec le câblage backend de production

**Description** : `CatalogService.resolve()` ouvre une transaction avant d'appeler
le chargeur de cache (`backend/src/Catalog/CatalogService.php:149-158`). Un match
non caché descend ensuite dans `TmdbMediaMetadataProvider`, dont
`TmdbProviderRateLimiter.acquire()` appelle à nouveau `beginTransaction()` sur le
même `PDO` (`backend/src/Catalog/TmdbProviderRateLimiter.php:29`). `Bootstrap`
injecte précisément cette même connexion dans les deux composants. Les tests API
injectent un faux `MediaMetadataProvider` et ne traversent donc jamais ce câblage.

**Impact** : le premier match non caché qui nécessite TMDB lève une erreur de
transaction imbriquée avant l'appel fournisseur. Le backfill, l'ouverture d'une
fiche non connue et la résolution F44 peuvent répondre 500 au lieu d'enrichir le
média.

**Correction attendue** : rendre le budget global compatible avec une transaction
déjà ouverte (opération SQL atomique dans la transaction courante, connexion
dédiée ou autre stratégie sans transaction imbriquée), puis ajouter un test
d'intégration qui construit le câblage de production avec rate limiter réel dans
le flux `CatalogService.match()`.

### F45-R2 — Les retries différés de la file Android ne sont jamais réveillés

**Description** : sur erreur d'un item, le worker conserve la ligne avec un
`nextAttemptAt` futur (`ExternalMetadataHydrationWorker.kt:131-169`), puis rend
`Result.success()`. Une continuation n'est programmée que si le lot de 200 est
plein **et** qu'une ligne est déjà due au même instant
(`ExternalMetadataHydrationWorker.kt:54-60`). Lorsque toutes les lignes restantes
sont en backoff, aucun WorkRequest différé n'est posé pour la prochaine échéance.

**Impact** : une panne réseau, un 429 ou une indisponibilité CSTV laisse les
demandes en Room mais interrompt la convergence jusqu'à un redémarrage de l'app ou
un nouvel enqueue sans rapport. La reprise après erreur/process death annoncée par
F45 n'est donc pas garantie.

**Correction attendue** : après chaque drainage, lire la plus proche échéance et
programmer un unique work avec `initialDelay`, ou faire porter le retry/backoff par
WorkManager sans bloquer les items suivants. Couvrir par un test où la file ne
contient plus que des lignes futures, puis devient drainable sans redémarrage ni
nouvelle action utilisateur.

### F45-R3 — Le moteur peut accepter un faux match sans confronter tous les indices

**Description** : la résolution PostgreSQL-first accepte l'unique titre normalisé
et tolère même une date stockée absente, sans scorer les hints ni établir de marge
(`ExternalMediaRepository.php:44-56`), puis lui attribue arbitrairement une
confiance 85. Côté fournisseur, la passe 2 n'est exécutée que si la marge
titre/année/genres est déjà inférieure à 12
(`CatalogMatchEngine.php:47-60`) : un réalisateur, un cast, une durée ou un trailer
capable de renverser un classement initial plus écarté n'est jamais consulté.

**Impact** : un homonyme ou remake peut recevoir durablement le mauvais
`externalId`. Comme F44 consomme ensuite l'âge de ce match, une mauvaise
classification plus basse peut autoriser une œuvre au-dessus du seuil du profil ;
le principal risque sécurité identifié par F45 n'est donc pas suffisamment borné.

**Correction attendue** : appliquer le seuil et la marge à toute réutilisation
PostgreSQL-first, exploiter aussi titres originaux/alternatifs et indices
disponibles, et déclencher la passe 2 dès qu'un signal fort peut modifier
l'acceptation ou l'ordre. Ajouter des corpus de non-régression avec année absente,
remake, premier candidat initialement en tête mais contredit par réalisateur/cast,
et classification d'âge différente entre candidats.

## Majeur

### F45-R4 — Un lien local existant perd la classification exacte utilisée par F44

**Description** : `ExternalMetadataRepositoryImpl.match()` court-circuite tout lien
local en renvoyant un `ExternalMetadataMatch` dont `ageRating`, confiance, méthode
et version valent tous `null` (`ExternalMetadataRepositoryImpl.kt:34-36`). Le DAO
ne relit jamais `external_movies`/`external_series`, bien que ces tables contiennent
l'âge persisté. `ContentClassificationRepository` consomme directement ce résultat
nullable.

**Impact** : après un backfill, une expiration du cache mémoire de 30 minutes ou un
redémarrage du process, un média pourtant classifié redevient `UNCLASSIFIED` pour
un profil bridé. Sa lecture/téléchargement exige alors indûment le PIN et son badge
d'âge disparaît.

**Correction attendue** : faire un lookup Room transactionnel lien + fiche et
renvoyer l'âge et la qualité persistés sur un hit local ; ajouter un test de
classification après redémarrage/cache mémoire vide, sans réseau.

### F45-R5 — `refreshAfter` et la revalidation sont stockés en façade mais jamais appliqués

**Description** : le DTO parse `cache.updatedAt`/`refreshAfter`, mais
`ExternalMetadataRepositoryImpl` les ignore et persiste systématiquement
`refreshAfter = null` (`ExternalMetadataRepositoryImpl.kt:42-84`). Tout lien local
est ensuite retourné immédiatement. La promotion `DETAIL_OPEN` appelle ce même
repository sans raison/force particulière, tandis que le backend renvoie un hit de
cache ou PostgreSQL-first sans vérifier si la fiche durable est stale. Aucune
échéance distincte de revalidation du lien n'est mise en œuvre.

**Impact** : une métadonnée hydratée ne se rafraîchit jamais et un ancien match ne
peut pas être corrigé quand les hints ou l'algorithme progressent. Les TTL, le
jitter, la conservation stale sur panne et le remplacement 3B restent inopérants.

**Correction attendue** : persister les fenêtres backend, distinguer clairement
lookup local, refresh metadata et revalidation de lien, évaluer ces échéances
uniquement sur `DETAIL_OPEN`, puis effectuer côté backend un refresh/re-match
single-flight transactionnel conservant l'ancienne donnée en cas d'échec. Ajouter
les tests frais/stale, refresh KO et remplacement par un meilleur match accepté.

### F45-R6 — Le backfill initial est massif et non cadencé

**Description** : `ExternalMetadataBackfillSeeder.seedKind()` boucle jusqu'à vider
toutes les pages du catalogue lors d'un même démarrage et appelle
`scheduler.request()` pour chaque représentant (`ExternalMetadataBackfillSeeder.kt:34-49`).
Chaque appel écrit Room puis sollicite `enqueueUniqueWork`, soit potentiellement
des dizaines de milliers d'opérations WorkManager. Le worker traite ensuite jusqu'à
200 requêtes réseau sans cadence, alors que le backend limite un compte à 30
matches/minute.

**Impact** : sur le catalogue cible d'environ 54 000 œuvres, le démarrage peut
remplir toute la file et marteler Room/WorkManager, puis provoquer une rafale de
429 dont la majorité des items partent en backoff. Cela contredit la convergence
progressive et la protection des box faibles.

**Correction attendue** : borner chaque passe du seeder, insérer les demandes par
lot dans une transaction, ne déclencher WorkManager qu'une fois par lot, cadencer
le drain selon le budget partagé et arrêter/reprogrammer la file sur 429 en
respectant `Retry-After`. Ajouter un test de volume prouvant le nombre borné
d'écritures/enqueues et la priorité immédiate de `DETAIL_OPEN`.

### F45-R7 — La fondation de données et la migration provider-neutral restent incomplètes

**Description** : Room ne contient que cinq tables F45 et les entités film/série
n'exposent qu'un sous-ensemble réduit des champs
(`ExternalMetadataEntities.kt:6-76`). Les saisons, épisodes, genres, keywords,
pays, titres alternatifs, recommandations et vidéos ne sont pas persistés côté
Android ; les routes `item`, `recommendations` et `season` de
`CstvCatalogApiService` n'ont aucun appelant de production. Les écritures
media/fiche/lien Android ne sont pas transactionnelles. Enfin Trending/Popular,
trailer et `canonical_media_links` continuent d'utiliser l'identité legacy
`movie:<tmdbId>`/`series:<tmdbId>` ; la Tâche 9 a été réduite unilatéralement dans
les notes d'implémentation.

**Impact** : F45 ne fournit pas la copie relationnelle complète promise, ne peut
pas hydrater saisons/épisodes à l'ouverture et laisse encore des identifiants
fournisseur traverser l'app. Les futures fiches/recherche/recommandations ne
peuvent pas s'appuyer sur cette fondation et un process death peut laisser une
écriture partielle.

**Correction attendue** : terminer les Tâches 5, 7 et 9 selon la spécification :
contrat API complet, tables/relations Room et transactions réelles, hydratation
série `DETAIL_OPEN`, bascule des consommateurs vers `externalId` opaque et retrait
des champs/noms TMDB côté app. Tout écart structurel durable, notamment le
stockage backend en arrays à la place des relations actées en §8.3, doit être
arbitré explicitement avant d'être conservé.

### F45-R8 — La protection fournisseur globale est partielle et ignore les 429 TMDB

**Description** : même après correction de F45-R1, le token bucket n'entoure que
search/detail/genres/hydrate/saison ; `trending()`, `popular()` et `videos()`
appellent encore `TmdbClient` directement (`TmdbMediaMetadataProvider.php:19-27`).
`TmdbClient` ne collecte pas le header `Retry-After` d'un 429 et retente après
seulement 100–250 ms (`TmdbClient.php:19-37`). Aucun mécanisme backend ne donne
réellement priorité au trafic interactif sur le backfill, et la route saison
n'utilise ni single-flight ni transaction englobant le remplacement de ses
épisodes.

**Impact** : le budget n'est pas global, plusieurs installations peuvent encore
dépasser le quota, les 429 fournisseur sont retraités trop tôt et des ouvertures de
fiche peuvent rester derrière le trafic de fond. Deux ouvertures simultanées d'une
saison peuvent dupliquer l'appel et une persistance partielle peut conserver des
épisodes obsolètes.

**Correction attendue** : centraliser le rate limiting autour de tous les appels
`TmdbClient`, propager et honorer `Retry-After`, introduire la priorité interactive
et les single-flights match/hydratation/refresh/saison, puis remplacer les
collections saison/épisodes dans une transaction. Couvrir par de vrais tests de
concurrence et de 429.

### F45-R9 — Les nouveaux médias partent au matching avant leurs hints Xtream

**Description** : après la sync, les repositories lancent
`startBackgroundEnrichment()` puis mettent immédiatement les nouveaux médias en
file (`VodRepositoryImpl.kt:437-438`, `SeriesRepositoryImpl.kt:373-374`). Le premier
appel est une coroutine asynchrone ; les nouvelles lignes n'ont donc généralement
encore ni réalisateur, acteurs, genres, durée ni année issue du détail quand le
worker F45 construit sa requête.

**Impact** : le chemin `NEW_IPTV_MEDIA` utilise surtout le titre, précisément au
moment où le risque de faux match est le plus élevé. Le résultat accepté est
ensuite court-circuité localement et ne profite pas des hints arrivés plus tard.

**Correction attendue** : enchaîner l'enqueue F45 après l'enrichissement Xtream de
chaque média, ou faire collecter les détails manquants par un producteur bas-priorité
avant le match, sans rendre la sync bloquante. Tester que les hints disponibles
sont effectivement présents dans la première requête de matching.

### F45-R10 — Les classifications TV américaines courantes ne sont pas mappées

**Description** : la table US de `TmdbCertificationMapper` ne connaît que les
certifications cinéma (`G`, `PG`, `PG-13`, `R`, `NC-17`). Le chemin série utilise
la même table, donc `TV-Y`, `TV-Y7`, `TV-G`, `TV-PG`, `TV-14` et `TV-MA` sont tous
ignorés. Le test `fromContentRatings` contient `TV-MA` uniquement derrière une
valeur FR prioritaire et ne vérifie jamais son mapping propre.

**Impact** : lorsqu'une série n'a pas de certification FR exploitable, sa valeur
US la plus courante devient `null` ou laisse gagner un fallback moins pertinent.
F44 demande alors inutilement un PIN ou applique un âge moins fiable.

**Correction attendue** : faire valider la correspondance numérique exacte des
codes TV ambigus, implémenter tous les codes usuels et ajouter des tests unitaires
US seuls ainsi que priorité FR/US/GB et valeurs multiples restrictives.

## Mineur

### F45-R11 — La validation UUID accepte des chaînes invalides jusqu'à PostgreSQL

**Description** : `CatalogAction` accepte toute chaîne de 36 caractères composée
d'hexadécimal et de tirets (`CatalogAction.php:43-45` et `:74-78`), sans vérifier
la position des tirets ni la version UUID v4. Une valeur comme 36 tirets franchit
donc l'action puis est liée à une colonne PostgreSQL `uuid`.

**Impact** : une entrée authentifiée malformée peut produire une erreur SQL/500 au
lieu du 422 contractuel.

**Correction attendue** : centraliser un validateur UUID canonique strict (v4 si
le contrat le requiert) pour toutes les routes et couvrir les formes malformées de
36 caractères.

### F45-R12 — Promotion de priorité et déduplication `linkKey` ne sont pas atomiques/globales

**Description** : le scheduler fait un `SELECT priority` puis un `@Upsert` séparé
(`ExternalMetadataHydrationScheduler.kt:25-35`) ; une demande de fond concurrente
peut donc écraser une promotion `DETAIL_OPEN`. Le seeder ne déduplique `linkKey`
qu'à l'intérieur d'une page de 500 et la propagation est plafonnée à 20 variantes,
ce qui laisse des doublons réseau pour un groupe traversant plusieurs pages ou
dépassant ce plafond.

**Impact** : un cas concurrent rare peut rétrograder l'ouverture utilisateur, et
les catalogues comportant beaucoup de variantes perdent une partie de la
mutualisation promise.

**Correction attendue** : utiliser un upsert SQL atomique conservant le maximum de
priorité et rendre la déduplication durable par `linkKey` (ou propager/purger toute
la file du groupe sans plafond silencieux). Ajouter les tests concurrents et
multi-pages correspondants.

## Corrections demandées

- [x] F45-R1 — supprimer la transaction PDO imbriquée et tester le câblage provider réel.
- [x] F45-R2 — programmer réellement le réveil de chaque retry différé.
- [x] F45-R3 — renforcer PostgreSQL-first et la passe 2 contre les faux matchs.
- [x] F45-R4 — relire et restituer la classification exacte depuis Room.
- [x] F45-R5 — implémenter refresh metadata et revalidation de lien à l'ouverture.
- [x] F45-R6 — rendre le backfill borné, batché, cadencé et respectueux des 429.
- [x] F45-R7 — terminer la persistance, les saisons/épisodes et la migration `externalId`.
- [x] F45-R8 — globaliser rate limit, priorité, single-flight et `Retry-After`.
- [x] F45-R9 — attendre/collecter les hints Xtream avant le premier matching.
- [x] F45-R10 — compléter et tester les classifications TV américaines.
- [x] F45-R11 — valider strictement les UUID des routes catalogue.
- [x] F45-R12 — atomiciser la priorité et dédupliquer `linkKey` au-delà d'une page/du plafond de 20.

## Vérifications automatisées de la review — 2026-08-20

- `docker compose build php-test -q`, `docker compose up -d php-test`, puis
  `docker compose exec -T php-test composer test` : **OK**, 217 tests et 1 033
  assertions. La suite injecte un faux provider dans `CatalogApiTest` et ne couvre
  donc pas F45-R1.
- `./gradlew --no-daemon --max-workers=1 --rerun-tasks testDebugUnitTest assembleDebug lintDebug` :
  **BUILD SUCCESSFUL**, 56 tâches exécutées ; 1 377 tests JVM, 0 échec, 0 erreur,
  0 ignoré ; APK debug et lint générés avec succès.
- `git diff --check` : **OK**.
- Ces résultats prouvent uniquement les commandes automatisées exécutées. Ils ne
  lèvent aucun constat ci-dessus et ne valent pas validation finale de F45.

---

# 13. Validation

## 2026-08-20 — Étape 8

Validation automatisée complète, effectuée après les corrections F45-R1 à F45-R12 :

- Backend : `docker compose exec -T php-test composer test` — **OK**, 219 tests,
  1 039 assertions, dans le runtime Docker de référence (`cstv_test`).
- Android : `./gradlew --no-daemon --max-workers=1 --rerun-tasks testDebugUnitTest assembleDebug lintDebug` — **BUILD SUCCESSFUL** ; 1 398 tests JVM, 0 échec, 0 ignoré ; APK debug et rapport lint générés.
- Intégrité : `git diff --check` — **OK**.

Les critères fonctionnels et techniques F45 sont couverts par les tests backend/JVM,
dont les scénarios de revalidation à l'ouverture, persistance des saisons/épisodes,
propagation de l'`externalId` opaque, migration Room, file séquentielle, backfill et
comportement dégradé. Conformément à `AGENTS.md`, aucune validation manuelle sur
appareil ou émulateur n'est requise pour cette étape.

Les seuls avertissements de build observés sont préexistants (API Media3 instable,
paramètres inutilisés, dépréciations Kotlin/Gradle) et n'empêchent aucune commande.

Cette étape ne réalise ni commit, ni release, ni archivage.

## 2026-08-20 — Validation après clôture de la tâche 9

- `./gradlew --no-daemon --max-workers=1 testDebugUnitTest assembleDebug lintDebug` :
  **BUILD SUCCESSFUL** (3 min 14 s). Les tests JVM, l'APK debug et lint sont verts après la
  migration complète des consommateurs T24 vers `externalId`.
- `Migration38To39SqlTest` vérifie également que `canonical_media_links` est supprimée à la
  migration 39→40 et que le cache `external_catalog_links` repart vide, sans conversion d'un
  ancien identifiant fournisseur en UUID CSTV.

---

# 14. Documentation

## 2026-08-20 — Étape 9

- `docs/architecture.md` décrit la frontière provider-neutral, l'identité `externalId`, les tables
  Room `external_*`, le cache Trending/Popular et la file séquentielle de convergence.
- `docs/features.md` documente l'enrichissement non bloquant, le rattrapage des installations
  existantes et l'hydratation des saisons/épisodes uniquement à l'ouverture d'une série.
- `docs/changelog.md` annonce F45 et la migration de l'âge exact consommé par F44.
- La clôture de la tâche 9 remplace les derniers consommateurs T24 : `TrendingTitle`, trailers,
  use cases, DAO et repository manipulent désormais un `externalId`; la migration 39→40 supprime
  `canonical_media_links` sans convertir les anciens IDs fournisseur et recrée le cache
  `external_catalog_links` sous UUID CSTV.

# 15. Release

Version:
v1.91.0

Commit:
✨ feat(catalog): consolidate external metadata (F45)

Date:
2026-08-20
