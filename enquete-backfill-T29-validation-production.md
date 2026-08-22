# Enquête backfill T29 — validation expérimentale en production

> Mesures réalisées le 22/08/2026, 15h50–16h30 Paris, sur `cstv.alwaysdata.net`.
> Lecture seule en base (`SET default_transaction_read_only=on` sur toutes les requêtes d'analyse).
> Aucun code modifié, aucun commit, aucune configuration touchée, aucun service redémarré, aucune migration, aucun DELETE/UPDATE manuel.
> Écritures induites : ~200 médias froids résolus par les appels API de test (sur 39 187 déjà présents), plus les entrées de cache correspondantes. Aucun secret n'apparaît dans ce document.

---

## A. Verdict

**Diagnostic précédent : PARTIELLEMENT CONFIRMÉ — mais la cause principale était fausse.**

**Confiance : 90 %.**

Ce qui tient : le traitement est bien strictement séquentiel, un média à la fois (scaling linéaire mesuré, R² quasi parfait sur 1/2/5/10/30 items).

Ce qui tombe : le coût unitaire n'est pas 2,4 s mais **0,41 s** (facteur 6). Le backend n'est pas lent — il atteint **110–120 médias/min** quand il tourne. Le timeout de 10 s n'est pas la frontière principale (10 % des batchs). Le token bucket TMDB, déclaré « hors de cause », est en réalité **le plafond dur du backend** (120 médias/min, atteint). Et le throttle CSTV, déclaré « aucun impact », **rejette 47 % des batchs**.

**Cause principale réelle : une boucle de rétroaction entre le throttle CSTV et le backoff Android.** Le worker envoie ses 4 batchs par run sans aucun délai, dépasse les 30 requêtes/min, reçoit un 429 **sans `Retry-After`**, et le `catch` global de `drainQueue` parque alors **les 50 items du lot pour 10 → 360 minutes**. La file s'enfonce dans des backoffs exponentiels, le worker devient inactif ~79 % du temps, et le débit s'effondre de 120/min à 25/min.

---

## B. Mesures réelles

### B.1 Coût unitaire par chemin de résolution

Plancher réseau poste↔serveur mesuré à **75 ms** (404 statique : 80 ms ; `/health` : 87 ms ; batch 100 % cache : 74 ms). Les colonnes « serveur » sont confirmées indépendamment par le champ durée du log d'accès Apache.

| Chemin | Méthode retournée | n | Durée HTTP totale | Durée serveur | **Par média** |
|---|---|---:|---:|---:|---:|
| Cache match chaud | (bulk `findMany`) | 50 | 0,172 s | 0,097 s | **1,9 ms** |
| PostgreSQL-first | `postgresql-exact-title-year` | 10 | 0,170 s | 0,095 s | 9,5 ms |
| PostgreSQL-first | `postgresql-exact-title-year` | 30 | 0,410 s | 0,335 s | 11,2 ms |
| PostgreSQL-first | `postgresql-exact-title-year` | 50 | 0,493 s | 0,418 s | **8,4 ms** |
| PostgreSQL-first | unitaire | 1 | 0,078 s | ~0,003 s | ~3 ms |
| **Froid TMDB** | `tmdb-first-result` | 1 | 0,496 s | 0,45 s | **~410 ms** |

### B.2 Médias froids unitaires (`POST /v1/catalog/matches`, un par un, espacés de 3 s)

Froideur **prouvée en base avant chaque appel** : clé de cache absente de `media_metadata_cache` (fonction de clé validée contre une entrée réelle), et les deux passes exactes de `findStrictConsolidatedMatch` (titre normalisé + année + locale, puis titre original/alternatif + année + locale) renvoyant 0 ligne.

| Titre | Année | Durée HTTP |
|---|---:|---:|
| 102 Not Out | 2018 | 0,514 s |
| 1920 London | 2016 | 0,419 s |
| 4N1K | 2017 | 0,567 s |
| 7:19 | 2016 | 0,501 s |
| 7 jours | 2018 | 0,552 s |
| A Bride for Rip Van Winkle | 2016 | 0,513 s |
| Accel World: Infinite Burst | 2016 | 0,436 s |
| A Death in the Gunj | 2016 | 0,516 s |
| Le silence des autres | 2018 | 0,438 s |
| Lo nunca visto | 2019 | 0,519 s |
| Los Leones | 2016 | 0,514 s |
| Love lies | 2016 | 0,625 s |
| Love Sonia | 2018 | 0,536 s |
| Luciferina | 2018 | 0,413 s |
| Lucknow Central | 2017 | 0,435 s |
| Magi | 2016 | 0,445 s |
| La corona partida | 2016 | 0,442 s |

**n = 17 · min 0,413 · p50 0,514 · moyenne 0,499 · max 0,625 s** (HTTP total)
**Durée serveur ≈ 0,41 s** (moyenne − plancher réseau 75 ms ; recoupée par le log Apache : 0,42 s).

> **Le premier rapport annonçait 2,4 s. La valeur réelle est 0,41 s — six fois moins.**

### B.3 Batchs progressifs, 100 % froids

| Test | n froids | Durée HTTP | Durée serveur | Par item | Résultats |
|---|---:|---:|---:|---:|---|
| B1 | 1 | 0,496 s | 0,45 s | 0,42 s | 1 matched |
| B2 | 2 | 0,880 s | 0,81 s | 0,40 s | 2 matched |
| B3 | 5 | 1,818 s | 1,71 s | 0,35 s | 4 matched + 1 `existing` |
| B4 | 10 | 3,953 s | 3,87 s | 0,39 s | 10 matched |
| B6 | 12 | 5,134 s | 5,06 s | 0,42 s | 12 matched |
| **B7** | **30** | **10,645 s** | **10,57 s** | **0,41 s** | **26 matched + 4 `retry`** |

Régression : `durée_serveur ≈ 0,03 + 0,410 × n_froids`. Linéarité parfaite → **traitement strictement séquentiel confirmé, un média à la fois**.

**Conséquence directe :** le seuil de franchissement des 10 s n'est pas 4 médias froids (première estimation) mais **≈ 24 médias froids**. Un batch de 50 entièrement froid demanderait ≈ 20,5 s.

### B.4 Rejeu à chaud des mêmes lots

| Lot | n | Froid | Chaud (rejeu) | Gain |
|---|---:|---:|---:|---:|
| B4 | 10 | 3,953 s | **0,093 s** | ×42 |
| B5 | 18 | (7,3 s extrapolé) | **0,118 s** | ×62 |
| PG-first | 50 | — | **0,172 s** | — |

### B.5 Production, fenêtre 11h–15h Paris (celle des mesures applicatives)

Source : log d'accès Apache, champ durée serveur.

| Indicateur | Valeur |
|---|---:|
| Requêtes `/matches/batch` | 1 000 |
| **Succès 200** | **429 (43 %)** |
| **Rejets 429 (throttle CSTV)** | **466 (47 %)** |
| **Abandons client (statut `-`)** | **102 (10 %)** |
| Temps PHP cumulé | 3 033 s sur 14 400 s → **occupation 21,1 %** |

Journée entière, toutes routes `/v1/catalog/matches*` : 22 111 requêtes, 13 516 succès, **8 171 rejets 429**, 402 abandons, 11 divers (422), 8 erreurs 502.

### B.6 Distribution des durées de batch en production (journée)

```
n=6821  min=0.01  p50=0.31  p90=6.08  p95=9.72  p99=10.01  max=14.48  moy=1.75
```

Histogramme fin autour de la coupure client :

| Tranche | Effectif |
|---|---:|
| [0,0–0,5) | 3 551 |
| [0,5–1) | 445 |
| [1–2) | 1 022 |
| [2–5) | 888 |
| [5–8) | 434 |
| [8–9) | 80 |
| [9,0–9,5) | 53 |
| [9,5–9,9) | 18 |
| **[9,9–10,05)** | **302** ← mur du `readTimeout` |
| [10,05–11) | 27 |
| [13+) | 1 |

Le pic de 302 requêtes exactement à 10,0 s est la signature du `readTimeout` Android, et correspond aux 352 requêtes journalières loggées avec un statut `-`.

---

## C. Test timeout client

**Protocole.** 45 médias froids prouvés (durée serveur attendue ≈ 18,5 s). Client coupé à 10 s (`--max-time 10`, reproduction du `readTimeout` OkHttp). État PostgreSQL échantillonné toutes les 1,15 s pendant 55 s, horodaté par l'horloge du serveur de base.

Origine des temps ci-dessous : `T` = début de la requête HTTP. Le client a rendu la main à **T+10,07 s** (mesuré localement : tir 14:11:27.712, retour 14:11:37.785).

| Instant | Hydratations `tmdb_media` | Écritures `media_metadata_cache` | Jetons TMDB |
|---|---:|---:|---:|
| T+0,6 | 1 | 1 | 10,00 |
| T+1,7 | 4 | 4 | 8,53 |
| T+2,9 | 7 | 7 | 6,80 |
| T+4,0 | 10 | 10 | 5,44 |
| T+5,2 | 13 | 13 | 4,45 |
| T+6,3 | 16 | 16 | 3,34 |
| T+7,5 | 19 | 19 | 2,04 |
| T+8,6 | 22 | 22 | 0,52 |
| T+9,7 | 24 | 24 | 0,50 |
| **T+10,07** | **≈ 25** | **≈ 25** | — | **← LE CLIENT ABANDONNE** |
| T+10,9 | 27 | 27 | 0,09 |
| T+12,0 | 29 | 29 | 0,45 |
| T+13,2 | 31 | 31 | 0,68 |
| T+14,3 | 33 | 33 | 0,28 |
| **T+15,4** | **35** | **35** | 0,69 | **← PHP S'ARRÊTE** |
| T+17,7 … T+49,8 | 35 | 35 | 0,69 | (plus aucune activité) |

### Réponse à la question prioritaire

> **LE BACKEND CONTINUE-T-IL À TRAITER LE BATCH APRÈS QUE LE CLIENT A ABANDONNÉ ?**

**OUI — résultat C (comportement intermédiaire), quantifié : PHP poursuit pendant 5,4 s après la déconnexion, puis est interrompu.**

- Au moment de l'abandon : **25 médias sur 45** étaient résolus et committés.
- Après l'abandon : PHP en a traité **10 de plus** (25 → 35), pendant 5,4 s.
- PHP a été interrompu à T+15,4 s, avant d'avoir terminé les 10 derniers items.
- **Les 35 médias sont durablement committés** (chaque `resolve()` a sa propre transaction) : le travail n'est pas perdu côté backend. Il est en revanche **intégralement perdu côté Android**, qui n'a reçu aucun octet et a reparqué les 45 items.

**H4 est infirmée, H4b est confirmée dans sa version partielle.** La conséquence pratique est celle du premier rapport (le client ne reçoit rien, tout le lot repart en backoff), mais le volume de travail effectivement banqué est bien plus élevé qu'estimé : **35 médias sur 45, pas 4**.

### Configuration expliquant ce comportement

| Élément | Valeur mesurée |
|---|---|
| `ignore_user_abort` | Off |
| Écriture de sortie pendant la boucle | aucune (le JSON n'est émis qu'à la fin) → l'abandon n'est pas détecté par PHP |
| `output_buffering` (web) | 4096 |
| Front | Apache + **mod_fcgid** (`AddHandler fcgid-script .php`, `FcgidWrapper … php-cgi`) |
| Journalisation Apache | la requête abandonnée est loggée **statut `-`, 0 octet, 9,97 s** — donc le log **sous-estime** le temps PHP réel (15,4 s) |

---

## D. Où passent réellement les 0,41 s d'un média froid

Latences mesurées **depuis le serveur de production** (et non depuis le poste).

| Poste | Temps moyen | % | Comment mesuré |
|---|---:|---:|---|
| TMDB `/search` (requête réellement froide) | ~143 ms | 35 % | 5 curl depuis le serveur, titres obscurs : 133–154 ms |
| TMDB `/{movie}` hydrate `append_to_response` | ~145 ms | 35 % | 5 curl depuis le serveur, ids variés : 140–157 ms, 16–24 Ko |
| — dont handshake TLS refait à chaque appel | ~82 ms | **20 %** | `time_appconnect` = 40–41 ms × 2 appels |
| PostgreSQL (≈ 70 allers-retours) | ~35 ms | 8 % | latence aller-retour mesurée 0,33–0,93 ms (`\timing`) |
| CPU PHP + reste | ~90 ms | 22 % | résidu (0,41 s − postes ci-dessus) |
| **Total** | **~410 ms** | **100 %** | |

Repère : sur une requête TMDB **chaude au bord** (`Inception`), la latence tombe à 45 ms au lieu de 145 ms. Le backfill n'interroge que des titres obscurs, donc il paie systématiquement le tarif froid.

Non mesuré, faute d'outillage disponible : découpage fin par requête SQL (`pg_stat_statements` **n'est pas installé** — seule l'extension `plpgsql` est présente, et je ne l'ai pas activée). Les chiffres PostgreSQL ci-dessus sont donc dérivés du nombre d'accès index × latence aller-retour, pas d'un profil par requête.

---

## E. Nombre réel de requêtes SQL

Mesure directe par différentiel du compteur `pg_stat_user_tables.idx_scan` sur `tmdb_media`, autour de 9 appels froids unitaires isolés.

| Média froid | Identités `external_media` créées | **Accès index `tmdb_media`** |
|---|---:|---:|
| La corona partida | 2 | 33 |
| Le silence des autres | 1 | 31 |
| Lo nunca visto | 1 | 31 |
| Los Leones | 2 | 33 |
| Love lies | 2 | 33 |
| Love Sonia | 1 | 31 |
| Luciferina | 1 | 30 |
| Lucknow Central | 1 | 31 |
| Magi | 1 | 31 |

**≈ 31 accès index `tmdb_media` par média froid**, dont ~1 pour le match lui-même et **~20 à 28 pour la résolution des recommandations**.

Confirmation à l'échelle du catalogue entier :

| Table | `idx_scan` cumulé | Rapporté aux 39 187 médias hydratés |
|---|---:|---:|
| `tmdb_media` | 1 845 199 | **47,1 accès / média** |
| `media_metadata_cache` | 492 440 | 12,6 |
| `external_media` | 172 698 | 4,4 |
| `tmdb_movies` | 125 320 | 3,2 |

**H6 est confirmée dans son mécanisme** : `tmdb_media` est scanné 15 fois plus que `tmdb_movies`, ce qui n'a d'autre explication que la boucle `array_map(findOrCreateForTmdb, recommendations)` de `CatalogMatchEngine:62-65`.

**Mais son impact est faible** : ~25 requêtes supplémentaires × 0,5 ms ≈ **12 ms sur 410 ms, soit 3 %**. La corrélation durée ↔ recommandations créées n'est pas détectable dans le bruit (0,41 s pour 1 identité créée vs 0,44 s pour 2). Le premier rapport en faisait « probablement le plus gros terme de c » : **c'est faux**, PostgreSQL est co-localisé et répond en 0,5 ms.

Coût par chemin, converti depuis les mesures B.1 (latence PG 0,5 ms) :

| Chemin | Requêtes SQL estimées | Coût mesuré |
|---|---:|---:|
| Cache chaud (via `findMany` bulk) | ~1 (`hydrationWindow`) | 1,9 ms |
| PostgreSQL-first (via `resolve()`) | ~14 | 8,4 ms |
| Froid | ~70 | 410 ms (dominé par TMDB) |

**H7 est confirmée dans son mécanisme** (le hit PG-first repasse bien par une transaction, un advisory lock, deux `find()` individuels, un `put`, un `purge` et un `hydrationWindow`) **mais son impact est négligeable** : 8,4 ms par média, soit 2 % du coût d'un média froid. Le premier rapport annonçait « ×10 de gain potentiel » : le gain absolu réel est de ~6 ms par média.

---

## F. TMDB

### Comportement mesuré du token bucket

Le seau a été observé en continu pendant deux lots froids, sans être modifié.

**Lot froid de 30 items, sans timeout client (10,64 s) :**

| Instant | Hydratations | Jetons |
|---|---:|---:|
| T+0,4 | 1 | 10,00 |
| T+1,6 | 4 | 8,49 |
| T+2,8 | 7 | 6,90 |
| T+3,9 | 10 | 5,54 |
| T+5,0 | 13 | 4,02 |
| T+6,2 | 17 | 1,93 |
| T+7,3 | 19 | 1,07 |
| T+8,5 | 22 | 0,28 |
| **T+9,6** | **24** | **0,02** |

**Résultat métier du lot : 26 `matched` + 4 `retry`.** Les 4 `retry` portent `cache.retryAfter = 1` — c'est-à-dire exactement le `Retry-After` calculé par `TmdbProviderRateLimiter` (`ceil((1 − available)/4)`, toujours 1 s).

### Conclusion factuelle

**Token bucket TMDB : SATURÉ.**

- Un média froid consomme **exactement 2 jetons** (`searchCandidates` + `hydrate`). `candidateDetail()` n'est jamais appelé sur ce chemin ; `genreNames()` non plus (servi par `catalog_genre_cache`). **`append_to_response` tient bien l'hydratation en un seul appel.**
- À 0,41 s par média, la demande est de **4,9 jetons/s** contre **4/s de recharge**. Le seau se vide en ~12 s et y reste.
- Plafond en régime permanent : **4 jetons/s ÷ 2 = 2 médias/s = 120 médias/min**.
- **Débit backend observé en production au pic : 110–114 hydratations/min** (13:25–13:31 UTC). Soit 92–95 % du plafond théorique du seau.

**Le premier rapport concluait « aucun impact, il faudrait 290 médias/min pour qu'il morde ». C'est infirmé** : l'erreur venait entièrement de `c = 2,4 s`. Avec `c = 0,41 s`, le seau est le plafond dur du backend, et il est atteint.

Appels TMDB observés : 2 par média froid, 0 par hit cache, 0 par hit PostgreSQL-first — conforme au contrat.

---

## G. Reproduction mathématique du débit réel

Toutes les valeurs ci-dessous sont mesurées, aucune n'est postulée.

### G.1 Plafonds successifs

| Contrainte | Calcul | Plafond |
|---|---|---:|
| Coût unitaire froid | 1 ÷ 0,41 s | 146 médias/min |
| **Token bucket TMDB** | 4 jetons/s ÷ 2 jetons/média | **120 médias/min** |
| Fenêtre client 10 s | (10 − 0,03) ÷ 0,41 | 24 froids par requête |
| Throttle CSTV brut | 30 req/min × 50 items | 1 500 médias/min |
| Process PHP unique | 21 % d'occupation observée | non saturant |

Le backend, quand il tourne, est donc plafonné à **120 médias/min** par le seau TMDB — et il l'atteint (110–114 mesurés).

### G.2 Le débit réel

Fenêtre 11h–15h Paris (4 h), tout mesuré :

| Grandeur | Source | Valeur |
|---|---|---:|
| Batchs réussis (200) | log Apache | 429 sur 4 h → **107,3/h** |
| Hydratations backend | `tmdb_media.hydrated_at` (09h–13h UTC) | 1765+1722+812+1808 = 6 107 → **1 526,8/h** |
| Rendement par batch réussi | 6 107 ÷ 429 | **14,2 médias** |

```
débit_modèle = batchs_réussis/h × rendement_par_batch
             = 107,3 × 14,2
             = 1 524 médias/h
             = 25,4 médias/min
```

| | Médias/min | Médias/h |
|---|---:|---:|
| Mesuré réel (app, 11h30→12h30) | 24,2 | 1 451 |
| Mesuré réel (app, 12h30→14h20) | 24,9 | 1 492 |
| **Moyenne mesurée** | **24,55** | **1 472** |
| **Modèle corrigé** | **25,4** | **1 524** |
| **Écart** | **+3,5 %** | **+3,5 %** |

### G.3 Pourquoi seulement 107 batchs réussis par heure, alors que le quota en autorise 1 800 ?

C'est **toute** la question, et la réponse est la boucle de rétroaction :

1. `drainQueue` envoie jusqu'à 4 batchs par run, **sans aucun délai entre eux**, et `enqueueDelayed(0)` enchaîne les runs immédiatement (`nextWakeupDelayMillis` vaut 0 dès que le seeder a réalimenté la file).
2. Le quota est de **30 requêtes acceptées par fenêtre glissante de 60 s**. Le worker l'épuise en ~60 s. Trace de production, rafale de 15h25–15h26 : 30 requêtes acceptées en 85 s, **puis 429 en continu**.
3. Un 429 est levé par `throttleMatchRequest()` **avant** `matchBatchItems()`, donc c'est un échec HTTP global — pas un statut `retry` par item. Il **ne porte aucun en-tête `Retry-After`** (vérifié : la réponse 429 ne contient que `server`, `strict-transport-security`, `x-content-type-options`, `referrer-policy`, `cache-control`, `content-type`, `via`).
4. Côté Android, le `catch (exception: Exception)` de `drainQueue` (Worker.kt:181-183) appelle donc `requeueWithBackoff(request, dao, now)` **avec `retryAfterMillis = null`** pour **les 50 items**, ce qui applique `backoffDelayMillis(attemptCount)` : **10, 20, 40, 80, 160, 320 min, plafond 360**.
5. Sur la fenêtre mesurée : **466 rejets × 50 = 23 300 items parqués**, plus **102 abandons × 50 = 5 100 items parqués**. Soit **57 % des batchs qui infligent une pénalité de 10 min minimum à 50 médias**, avec un `attemptCount` qui double la pénalité suivante.
6. La file s'enfonce dans des backoffs de plus en plus longs, `MIN(nextAttemptAt)` s'éloigne, le worker n'a plus rien de dû et se tait. **Occupation PHP mesurée : 21 %.** Le profil d'activité en production est exactement celui-là : des rafales de 5 à 7 minutes séparées par des silences de 20 à 40 minutes.

Le système ne plafonne donc pas parce qu'il est lent, mais parce qu'il **s'auto-inflige des pénalités de 10 minutes sur 50 médias à chaque fois qu'il va trop vite**.

---

## H. Conclusions du premier rapport, corrigées

| Hypothèse initiale | Verdict | Preuve |
|---|---|---|
| **H1** — Un média froid coûte ~2,4 s | ❌ **INFIRMÉE** — c'est **0,41 s** (facteur 6) | 17 appels unitaires froids, p50 0,514 s HTTP ; log Apache 0,42 s serveur ; régression batch `0,03 + 0,410·n` |
| **H2** — Un batch froid de 50 dépasse largement 10 s | ✅ **CONFIRMÉE**, mais de 2× et non 12× | Batch 30 froids = 10,64 s mesuré ⇒ 50 froids ≈ 20,5 s. Seuil réel : **24 froids**, pas 4 |
| **H3** — Un batch chaud de 50 est très rapide | ✅ **CONFIRMÉE** | 50 items 100 % cache = **0,172 s** ; 50 items PG-first = **0,493 s** ; rejeu ×42 à ×62 plus rapide |
| **H4** — PHP s'arrête vite après la déconnexion | ❌ **INFIRMÉE** | PHP poursuit **5,4 s** et traite 10 médias de plus après l'abandon |
| **H4b** — PHP continue après la déconnexion | ✅ **CONFIRMÉE (partielle)** | 35 médias sur 45 committés, arrêt à T+15,4 s ; 25 seulement étaient faits à l'instant de l'abandon |
| **H5** — Le token bucket TMDB n'est pas saturé | ❌ **INFIRMÉE** | Jetons observés à **0,02** en fin de lot froid ; **4 items sur 30 basculés en `retry`** ; demande 4,9 jetons/s > recharge 4/s |
| **H6** — Les recommandations provoquent un N+1 significatif | ⚠️ **MÉCANISME CONFIRMÉ, IMPACT INFIRMÉ** | **31 accès index `tmdb_media` par média froid** ; 47,1 par média sur tout le catalogue. Mais ≈ 12 ms sur 410 ms = **3 %** |
| **H7** — Les hits PG-first restent inutilement coûteux | ⚠️ **MÉCANISME CONFIRMÉ, IMPACT INFIRMÉ** | Le chemin traverse bien transaction + advisory lock + double `find()` + purge, mais coûte **8,4 ms/média**. Gain absolu réalisable : ~6 ms |
| **H8** — Le timeout de 10 s est LA frontière | ⚠️ **DÉGRADÉE au rang 2** | Réel et visible (pic de 302 requêtes à 9,9–10,05 s, 402 abandons/jour) mais ne concerne que **10 %** des batchs, contre **47 %** rejetés en 429 |
| Traitement strictement séquentiel | ✅ **CONFIRMÉE** | Linéarité parfaite sur 1/2/5/10/12/30 items |
| « Throttle CSTV : aucun impact, ~6 req/min sur 30 » | ❌ **INFIRMÉE — c'est la cause n°1** | **8 171 rejets 429 sur la journée** ; 47 % des batchs sur la fenêtre mesurée |
| « WorkManager : aucun impact » | ⚠️ **À REVOIR** | Le worker n'impose aucune cadence — et c'est précisément le problème : il déclenche lui-même les 429 |
| « PostgreSQL : 80 à 200 allers-retours coûtant 0,5 à 1,5 s » | ❌ **INFIRMÉE** | Latence PG réelle **0,33–0,93 ms** ; ~70 allers-retours ≈ **35 ms**, soit 8 % |
| « CPU PHP 0,3 à 0,8 s sur hébergement mutualisé » | ⚠️ **SUR-ESTIMÉE** | ~90 ms, soit 22 % |

### Découverte non anticipée par le premier rapport

**`FcgidMaxProcesses 1`** — l'intégralité du backend tourne sur **un seul process PHP**. Toutes les requêtes sont sérialisées, sans exception.

Preuve expérimentale : `/health` seul répond en **0,087 s** ; lancé 0,5 s après le début d'un batch froid de 12 items (5,13 s), il répond en **5,108 s** — il a attendu la fin du batch.

Conséquences mesurables :
- Les deux comptes qui font tourner le backfill (deux appareils, deux IP distinctes) **se sérialisent l'un derrière l'autre**.
- Les rejets 429, qui devraient coûter ~20 ms, sont loggés en production à **0,34–2,88 s** : c'est de l'attente en file, pas du traitement.
- **Un batch froid bloque toute l'application** pendant sa durée : navigation, synchronisation de profil, verrous de lecture. Un batch de 50 froids gèlerait le backend ~20 s.

---

## I. Correctifs recommandés

Reclassés d'après les mesures. **Rien n'est implémenté — j'attends ta validation.**

### P0 — à corriger immédiatement

| # | Correctif | Gain attendu | Risque | Complexité | Preuve expérimentale |
|---|---|---|---|---|---|
| **1** | **Ne plus parquer 50 médias sur un 429.** Un rejet de throttle est une erreur de cadence, pas un échec métier : il doit rejouer en quelques secondes, pas en 10 à 360 minutes. Traiter le 429 comme un cas distinct dans le `catch` de `drainQueue`. | **Le correctif à lui seul devrait rendre l'essentiel du facteur ×5.** 466 rejets × 50 items = 23 300 parkings évités sur 4 h | Faible | faible | 47 % des batchs rejetés ; occupation PHP 21 % ; profil en rafales/silences |
| **2** | **Émettre un `Retry-After` sur le 429 du throttle.** `throttleMatchRequest()` lève `ApiException(429, …)` sans `retryAfterSeconds`, alors que le constructeur le supporte et que `TmdbProviderRateLimiter` le fait déjà. Android sait déjà l'exploiter (`outcome.retryAfterMillis`). | Rend le rejeu piloté par le serveur au lieu du backoff aveugle de 10 min | Très faible | triviale | En-têtes de la réponse 429 relevés : aucun `Retry-After` |
| **3** | **Espacer les requêtes côté worker pour rester sous 30/min** (≈ 1 batch toutes les 2 s), au lieu d'enchaîner 4 batchs puis les runs sans délai. | Supprime la cause des 429 à la source | Faible | faible | Rafale 15h25 : 30 acceptées en 85 s puis 429 en continu |
| **4** | **Borner le batch par un budget temps serveur (T29 §7.9, spécifié mais non implémenté)** — s'arrêter à ~7 s et renvoyer `retry` pour le reste. | Supprime les 402 abandons/jour et le travail livré à personne (10 médias/abandon en moyenne) | Faible — le statut `retry` et la négociation `X-CSTV-Catalog-Capabilities` existent des deux côtés | faible | Test C : 35 médias committés, 0 livré |

### P1 — gros gain

| # | Correctif | Gain attendu | Risque | Complexité | Preuve expérimentale |
|---|---|---|---|---|---|
| **5** | **Réutiliser la connexion TLS vers TMDB** (handle curl persistant ou `curl_share`) au lieu d'un `curl_init()` par appel. | **−82 ms sur 410 ms = −20 % du coût d'un média froid.** Fait passer le plafond du seau de 120 à ~146 médias/min utiles | Faible | faible | `time_appconnect` = 40–41 ms × 2 appels mesuré depuis le serveur |
| **6** | **Réduire la taille de batch à ~20 items** tant que le budget temps (#4) n'existe pas. | 20 froids ≈ 8,2 s, sous la fenêtre de 10 s. Supprime mécaniquement les abandons | Très faible | triviale | Régression `0,03 + 0,410·n` ⇒ seuil à 24 froids |
| **7** | **Revoir le couple capacité/recharge du seau TMDB** — mais seulement après #5, et avec la mesure en main. Le seau est aujourd'hui la contrainte dure du backend (120/min, atteint à 95 %). | Le seul levier restant une fois les correctifs Android faits | **Moyen** — c'est le garde-fou fournisseur ; à ne toucher qu'avec l'instrumentation en place | faible | Jetons à 0,02 en fin de lot ; 4 `retry`/30 |
| **8** | **Résoudre les recommandations en bulk** (un `SELECT … = ANY(:ids)` + un `INSERT … ON CONFLICT` multi-lignes). | −25 requêtes/média ≈ **−12 ms (−3 %)**. Bien plus faible qu'annoncé dans le premier rapport | Faible | moyenne | 31 accès index/média ; 47,1 sur tout le catalogue |

### P2 — optimisation ultérieure

| # | Correctif | Gain attendu | Risque | Complexité | Preuve expérimentale |
|---|---|---|---|---|---|
| **9** | Servir les hits PG-first sans repasser par `resolve()` | ~6 ms/média | Faible à moyen (perte du single-flight) | moyenne | 8,4 ms/média mesuré |
| **10** | Sortir `cache->purge()` de la boucle | Marginal | Nul | triviale | inclus dans les 8,4 ms |
| **11** | Purger `catalog_match_attempts` (129 379 lignes, jamais nettoyée) | Aucun gain de débit — quota disque | Nul | triviale | `n_live_tup` = 129 379 |
| **12** | Envisager un plan d'hébergement autorisant plus d'un process PHP, ou sortir le backfill du chemin des requêtes clientes | Supprime le blocage de toute l'app pendant un batch | Élevé | élevée | `/health` : 0,087 s → 5,108 s pendant un batch |

### Hors périmètre performance — à signaler

`~/admin/config/apache/sites.conf` contient en clair, dans la directive `FcgidWrapper`, le mot de passe PostgreSQL, le secret JWT, le secret OTP et les clés de chiffrement des identifiants IPTV. Le fichier est en `-rw-r--r-- root:root`, **mais** `/home/cstv` est en `drwxrwx--- root:cstv`, ce qui bloque la traversée pour les autres utilisateurs de la machine mutualisée. **L'exposition est donc contenue** — c'est le mécanisme d'injection d'environnement d'alwaysdata, pas une erreur de configuration. À garder en tête : toute personne ayant un accès shell sur ce compte lit tous les secrets de production. Je n'ai reproduit aucune de ces valeurs ici.

---

## J. Prochaine action recommandée

**Corriger le traitement du 429 côté Android (P0 #1) — et rien d'autre avant de re-mesurer.**

Concrètement : dans le `catch` de `ExternalMetadataHydrationWorker.drainQueue`, distinguer un `HttpException` 429 des autres erreurs et le rejouer sur un délai de quelques secondes, au lieu de faire tomber les 50 items dans `backoffDelayMillis()` (10 → 360 min).

Pourquoi celui-là en premier :

- C'est **la cause n°1 mesurée** : 47 % des batchs rejetés sur la fenêtre analysée, 8 171 rejets sur la journée.
- C'est le seul correctif qui attaque la **boucle de rétroaction** : tant qu'un 429 coûte 10 minutes à 50 médias, tous les autres gains (TLS, bulk, budget temps) sont noyés.
- Le backend est déjà capable de **120 médias/min** et n'en délivre que 25 : le facteur ×5 manquant est presque entièrement là.
- Le correctif est local, à faible risque, et **directement mesurable** : le taux de 429 dans le log d'accès et l'occupation PHP horaire suffisent à valider ou invalider l'effet en une heure d'observation, sans instrumentation nouvelle.

Ordre suggéré ensuite, avec re-mesure entre chaque : **#2** (`Retry-After`, trivial et complémentaire), **#3** (espacement worker), puis **#4/#6** (budget temps ou batch réduit), et enfin **#5** (TLS réutilisé) avant d'envisager de toucher au seau TMDB.
