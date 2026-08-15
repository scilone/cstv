# Plan de releases — lot T21-T23 / F39-F44

Découpage par release du lot de 9 tickets (T21, T22, T23, F39, F40, F43, F44,
F42, F41), dans l'ordre de livraison déjà établi et validé lors de l'étape 3.
Ce document n'est pas un ticket : il ne suit pas le cycle de vie
`ai/AI_DEVELOPMENT_WORKFLOW.md`, il sert de plan de coordination entre eux.

**Granularité retenue : une release MINOR par ticket.** Conforme au
fonctionnement déjà en place du projet (AGENTS.md — « minor pour une
phase/fonctionnalité », un ticket = un tag = une entrée d'archive). Chaque
release reste petite, testable et réversible indépendamment des autres — en
particulier pour F41, dont l'arbitrage d'étape 3 exige explicitement de ne
pas se mélanger avec le reste du lot pour isoler son risque de régression.

---

## ⚠️ Les numéros ci-dessous sont indicatifs, pas à copier tels quels

Conformément à AGENTS.md : **vérifier `git tag --sort=-v:refname | head -1`
au moment de chaque release réelle**, jamais se fier à un numéro écrit dans
ce document — il périme dès qu'une release hors-lot (hotfix, correctif)
s'intercale. Point de départ à la rédaction de ce plan : `v1.83.10`.

Même règle pour les numéros de migration Room et PostgreSQL indiqués
ci-dessous : vérifier `AppDatabase.kt` et `backend/migrations/` avant
d'écrire quoi que ce soit (règle déjà posée dans chaque fiche, T21 §8.5).

---

## Séquence

| # | Release | Ticket | Contenu visible | Backend à déployer avant l'app |
|---|---|---|---|---|
| 1 | v1.84.0 | T21 | Aucun (fondation données) | Non |
| 2 | v1.85.0 | T22 | Aucun en nominal (source TMDB devenue invisible) | **Oui** |
| 3 | v1.86.0 | T23 | Médias auparavant en erreur deviennent lisibles | Non |
| 4 | v1.87.0 | F39 | Badges langue/qualité + sélecteur de version VOD/séries | Non |
| 5 | v1.88.0 | F40 | Sélecteur de qualité chaînes + mode automatique | Non |
| 6 | v1.89.0 | F43 | Bouton « Passer l'intro » sur les séries | Non |
| 7 | v1.90.0 | F44 | Restriction d'âge par profil (PIN) | **Oui** |
| 8 | v1.91.0 | F42 | Appui long « Depuis le début du programme » | Non |
| 9 | v1.92.0 | F41 | Pause/reprise et retour arrière sur le direct | Non |

---

## Détail par release

### 1 — T21 · v1.84.0 · Normalisation des titres et clé de liaison

**Migration Room** : `MIGRATION_28_29` (schéma seul — colonnes + index, voir
T21 §8.5). Aucune migration backend.

**Contenu** : titre nettoyé, attributs extraits, clé de liaison persistés en
base pour films/séries/chaînes. Recalcul du catalogue existant en tâche de
fond après le premier démarrage suivant la mise à jour (§8.5.1).

**Aucun changement visible** en nominal — c'est la donnée que consomment F39
et F40, qui vient ensuite. Un utilisateur qui compare avant/après ne doit
rien remarquer, hormis un `CatalogNormalizationWorker` transitoire en tâche
de fond au premier lancement.

**Checklist avant tag** (T21 §10 tâche 9) :
- [ ] `./gradlew assembleDebug` / `testDebugUnitTest` / `lintDebug` verts
- [ ] Tests de migration + tests du worker (reprise, idempotence) verts
- [ ] `TmdbCatalogMatcher` consomme le titre stocké, sans régression du taux
      de correspondance
- [ ] AGENTS.md : corriger la mention « version 27 » déjà périmée par cette
      migration (28 → 29), indépendamment du reste du lot

---

### 2 — T22 · v1.85.0 · TMDB centralisé derrière le backend

**Migration PostgreSQL** : `006_media_metadata_cache.sql` (le dépôt s'arrête
à `005_playback_locks.sql` à la rédaction de ce plan).

**Ordre de déploiement obligatoire** : `scripts/deploy-backend.sh` **avant**
`scripts/release-local.sh`. Le contrat `/v1/catalog` doit être en place côté
serveur avant qu'une app qui l'appelle ne soit publiée — sinon les premiers
utilisateurs à mettre à jour tombent sur un backend qui ne sait pas encore
répondre.

**Contenu** : aucun appel direct de l'app vers TMDB, aucune clé dans l'APK.
Écran « À propos » : vérifier l'attribution TMDB déjà en place ou l'ajouter
(§8.5 — obligation de licence, pas optionnelle).

**Checklist avant tag** (T22 §10 tâche 11) :
- [ ] `scripts/deploy-backend.sh --dry-run` puis déploiement réel, healthcheck
      OK
- [ ] Suite PHPUnit verte (aucun test ne contacte TMDB réel)
- [ ] `./gradlew assembleRelease` : recherche dans les artefacts textuels
      générés confirmant l'absence de `TMDB_API_KEY` et `api.themoviedb.org`
      — critère d'acceptation central du ticket (§7.5)
- [ ] `ext-curl` confirmé actif sur l'hébergement alwaysdata

---

### 3 — T23 · v1.86.0 · Autoréparation du lecteur

**Migration Room** : nouvelle table `playback_repair_profiles` — vérifier le
numéro réel (`29` si T21 a bien pris `29` sans qu'un hotfix ne s'intercale,
sinon le suivant disponible).

**Contenu** : un média qui échouait par défaillance de décodage se répare
silencieusement (décodeur logiciel → piste désactivée → autre piste audio).
Périmètre complet dès cette release : films, séries, direct, téléchargements.

**Point de coordination laissé en place** : le point d'extension pour F39/F40
(T23 §10 tâche 6) est câblé mais **sans consommateur réel** avant F40
(release 5) — comportement testé par défaut (aucune régression), rien à
signaler côté QA pour cette release.

**Checklist avant tag** (T23 §10 tâche 8) :
- [ ] Tests `FakePlaybackEngine` verts (ordre de séquence, timeout, annulation)
- [ ] Aucune boucle infinie de test (AGENTS.md § Boucles infinies) — vigilance
      particulière sur le timer 24 s
- [ ] Non-régression sur les trois lecteurs (VOD, série, direct)

---

### 4 — F39 · v1.87.0 · Étiquettes et sélecteur de versions

**Migration Room** : nouvelle table `series_version_preferences` — vérifier
le numéro réel (`30` a priori).

**Dépend de** : T21 (release 1, livré).

**Contenu** : badges langue/qualité sur les vignettes VOD et séries, bouton
« Version » dans le lecteur pour basculer entre entrées d'une même œuvre sans
perdre la position.

**Checklist avant tag** (F39 §10 tâche 6) :
- [ ] `EXPLAIN QUERY PLAN` confirmant que l'index couvrant T9 n'est pas perdu
      par l'ajout des badges (F39 §8.4) — sinon revenir étendre l'index côté
      T21, pas ici
- [ ] Interaction avec la réparation T23 (un seul contrôleur de moteur,
      §9.3) testée explicitement

---

### 5 — F40 · v1.88.0 · Qualité des chaînes et mode automatique

**Aucune migration** (Room ni backend).

**Dépend de** : T21 (release 1), T23 (release 3, coordination réelle).

**Contenu** : bouton « Qualité » dans le lecteur de direct, mode automatique
optionnel avec repli en cascade sur les variantes.

**Point de coordination laissé en place** : le point d'extension pour F42
(catch-up, F40 §10 tâche 6) et pour F41 (purge du tampon) est câblé mais sans
consommateur réel avant leurs releases respectives (8 et 9) — comportement
par défaut testé, aucune régression, mais **la règle « ne jamais faire
perdre une capacité de rattrapage en cours » n'a pas encore d'effet observable**
puisque F42 n'existe pas encore. Rien à signaler côté QA pour cette release
en particulier — juste ne pas s'étonner que l'arbitrage F40×F42 documenté
dans la fiche ne soit pas testable en conditions réelles avant la release 8.

**Checklist avant tag** (F40 §10 tâche 7) :
- [ ] Tests avec `Clock` faux pour la fenêtre 5 coupures/120 s
- [ ] Coordination T23 réelle testée (pas un faux consommateur) — décodage
      délégué à T23 avant tout repli F40

---

### 6 — F43 · v1.89.0 · Saut d'intro par empreinte audio

**Migration Room** : nouvelles tables `episode_audio_fingerprints` et
`season_intro_detections` — vérifier le numéro réel (`31` a priori).

**Dépend de** : rien (ticket isolé).

**Contenu** : bouton « Passer l'intro » sur les épisodes de série, détecté
localement dès le deuxième épisode analysé d'une saison.

**Porte de sortie déjà posée** (F43 §10 tâche 7) : si le benchmark CPU dépasse
le seuil fixé (2 s pour 12 min de signatures), la fonctionnalité part
**derrière un feature flag désactivé** plutôt que dégrader l'expérience. Si
c'est le cas, cette release peut sortir avec le flag OFF — à consigner
explicitement dans les notes de release, pas silencieusement.

**Checklist avant tag** :
- [ ] Benchmark JVM exécuté, décision de flag consignée (§11 de la fiche)
- [ ] Vérification manuelle absence de saccade audio sur box bas de gamme
      (hors critères automatisés)

---

### 7 — F44 · v1.90.0 · Restriction d'âge par profil (PIN)

**Migrations** : Room (colonne `maxAgeRating` sur `profiles`, numéro réel à
vérifier, `32` a priori) **et** PostgreSQL (`007_...` — après le `006` de T22).

**Dépend de** : T22 (release 2, contrat de classification réel).

**Ordre de déploiement obligatoire**, plus strict qu'ailleurs dans ce lot
(F44 §9.3, risque explicitement identifié) : **backend avant app, sans
exception**. Un ancien client face à un nouveau backend doit continuer à
fonctionner (`maxAgeRating` absent = non bridé) ; c'est l'inverse qui casse
— une app qui attend le champ face à un backend pas encore migré.

**Écart de périmètre à documenter** : cette release est le moment de mettre
à jour AGENTS.md (§2 Périmètre strict) pour lever l'exclusion « code PIN /
restriction parentale par profil », comme engagé dans la fiche F44 dès sa
création.

**Checklist avant tag** (F44 §10 tâche 9) :
- [ ] Déploiement backend, healthcheck, **puis seulement** `release-local.sh`
- [ ] Test explicite : ancien client + nouveau backend ne casse rien
- [ ] Aucun surcoût mesuré sur le parcours d'un profil non bridé (pas d'appel
      classification déclenché)
- [ ] AGENTS.md mis à jour (périmètre)

---

### 8 — F42 · v1.91.0 · Lancer depuis le début du programme

**Migration Room** : colonnes `catchupAvailable`/`catchupRetentionDays` sur
`live_streams`, plus `has_archive` sur `epg_cache` — numéro réel à vérifier
(`33` a priori).

**Dépend de** : rien de bloquant pour son mode principal (le flux décalé du
panel a été vérifié directement sur le panel réel à l'étape 3).

**⚠️ Écart d'acceptation temporaire, assumé et documenté** : le critère
d'acceptation §7.5 « depuis le lecteur, sans flux décalé, le repli local F41
fonctionne » **ne peut pas être validé à cette release** — F41, qui fournit
le tampon local, se livre *après* (release 9). Ce n'est pas un bug de cette
release : la fiche F42 (Arbitrages structurants, « Ordre F42 avant F41 »)
documente explicitement que F42 s'appuie sur le `PlaybackEngineController`
de T23 pour son mode principal, et que le repli local n'existe que depuis la
release F41. **Ne pas bloquer cette release sur ce critère** ; le
mentionner dans les notes de release comme limitation connue et temporaire,
levée à la release suivante sans nouveau déploiement de F42.

**Checklist avant tag** (F42 §10 tâche 7) :
- [ ] Format d'URL timeshift calibré et validé contre le panel réel (pas
      seulement supposé — F42 §10 tâche 3)
- [ ] Mode principal (catch-up panel) validé de bout en bout sur au moins une
      chaîne réelle avec rétention connue

---

### 9 — F41 · v1.92.0 · Pause et reprise du direct

**Aucune nouvelle migration** (§8.11 — aucune table Room, aucune dépendance
tierce).

**Dépend de** : T23 (`PlaybackEngineController`, release 3), et referme les
points d'extension laissés par T23 (release 3), F40 (release 5) et F42
(release 8).

**Cette release complète rétroactivement** :
- F42 : le critère d'acceptation laissé en attente à la release 8 (repli
  local) devient vérifiable — pas de nouveau code F42, juste le tampon qui
  existe enfin derrière le point d'extension déjà posé ;
- F40 : la purge du tampon lors d'une bascule de qualité devient réelle ;
- T23 : la position de tampon en repli pour le direct devient disponible.

**Isolement de risque** (arbitrage étape 3, à respecter) : cette release ne
doit rien contenir d'autre. C'est le ticket qui concentre le plus gros
refactor du lot (`LivePlaybackService`, propriété du lecteur live) — la
garder seule permet un rollback ciblé si un problème apparaît en production,
sans emporter les huit tickets précédents.

**Checklist avant tag** (F41 §10 tâche 10, la plus longue du lot) :
- [ ] **Spike go/no-go déjà tranché** avant d'arriver ici (F41 §10 tâche 1) —
      cette release ne doit pas découvrir en cours de route que le TS n'a pas
      de PCR/PTS exploitables
- [ ] Tests de lecture live, VOD et série tous verts (le refactor
      `LivePlaybackService` ne doit rien casser sur VOD/série, qui restent
      sur le contrôleur T23 direct)
- [ ] Points d'extension T23/F40/F42 câblés avec de vraies implémentations,
      pas les faux consommateurs de test des releases précédentes
- [ ] Vérification manuelle box bas de gamme (écriture continue du tampon)
- [ ] AGENTS.md : coordonner avec la mise à jour déjà faite à la release 8
      (F42) — ne pas dupliquer l'entrée de périmètre catch-up/timeshift

---

## Résumé des interdépendances actives par release

```
1. T21  ────────────┐
2. T22  ──────┐     │
3. T23  ────┐ │     │
             │ │     │
4. F39  ◄────┼─┴─────┘   (T21)
5. F40  ◄────┘             (T21 + T23 réel)
6. F43  (isolé)
7. F44  ◄──────────────    (T22 réel)
8. F42  (isolé pour son mode principal)
9. F41  ◄── referme T23 (3), F40 (5), F42 (8)
```

Deux chaînes de déploiement backend-avant-app dans ce lot (T22 → release 2,
F44 → release 7) : à ne jamais inverser, sous peine de casser les premiers
clients à jour avant le serveur.
