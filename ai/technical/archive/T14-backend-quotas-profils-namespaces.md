# T14 - Quotas de profils, de namespaces et de stockage par compte

## Informations générales

Status:
REVIEW (étape 7 — corrections T14-R1–R4 appliquées, RESOLVED ; en attente d'une nouvelle review étape 6 ou de la validation étape 8)

Created:
2026-08-13

Sévérité:
MAJEUR (abus authentifié)

Reproduit en production:
Confirmé au niveau du code déployé (aucune logique de quota) ; démonstration en boîte noire vérifiée en local, non rejouée en prod faute de session authentifiée (toutes les routes concernées passent par `AuthMiddleware`).

---

# 1. Description

Aucune limite n'encadre la quantité de données qu'un compte authentifié peut stocker. Un compte peut créer un nombre illimité de profils, chaque profil un nombre illimité de namespaces, chaque namespace un blob d'un mégaoctet. Le stockage par titulaire est donc infini, et un compte s'obtient avec une simple adresse e-mail (l'OTP est envoyé, puis `verify` crée le compte à la volée).

---

# 2. Contexte

## Fait de code (branche `main`, code déployé)

Vérifié par lecture et recherche — aucune vérification de quota n'existe :

* `ProfileService::create()` (`backend/src/Profile/ProfileService.php`) insère sans compter les profils existants. Seule la suppression est bornée, par le bas (`LAST_PROFILE_REQUIRED`).
* `Validator::namespace()` (`backend/src/Shared/Validator.php`) accepte tout identifiant `[a-z0-9][a-z0-9._-]{0,63}`, soit un espace de noms libre, alors que l'application n'émet qu'un ensemble fini et connu de namespaces.
* `ObjectService::put()` borne le blob unitaire via `MAX_OBJECT_SIZE_BYTES` (1 Mio) mais ne totalise rien par profil ni par compte.
* Le schéma (`migrations/002_namespace_snapshots.sql`) ne pose aucune contrainte de cardinalité.

## Démonstration (local, 2026-08-13)

* 60 appels consécutifs à `POST /v1/profiles` sur un compte neuf : **60 × 201**, aucun refus.
* 40 appels `PUT /v1/profiles/{id}/objects/ns{0..39}` : **40 × 204**, listés ensuite tels quels.

## Pourquoi la reproduction prod n'a pas été faite

Les routes `/v1/profiles/...` exigent un jeton valide. L'OTP prod part par e-mail réel, non contrôlé ici → pas de session. Le code étant identique à celui testé en local, le comportement est le même ; seule la démonstration live manque. **Un jeton d'accès valide (ou l'OTP d'une adresse contrôlée) permet de rejouer les deux boucles ci-dessus en prod en quelques secondes.**

## Modèle de menace

Abus **authentifié**, mais la barrière d'entrée est faible : tout attaquant disposant d'une adresse e-mail obtient un compte valide en prod. Il peut ensuite faire croître `profile_objects` jusqu'à saturation du volume, ce qui dégrade aussi l'authentification (insertions dans `otp_codes`). Coût attaquant quasi nul, aucun signal côté service.

---

# 3. Spécification fonctionnelle

## Comportement attendu

* Un utilisateur légitime ne rencontre jamais ces limites : calées largement au-dessus de l'usage réel (profils type Netflix, quelques namespaces fixes).
* Au-delà, l'API répond par une erreur métier explicite que le client sait présenter, jamais par une erreur technique.

## Règles métier

1. Nombre maximal de profils par compte : **10** (valeur PO du 2026-08-13, dimensionnée pour l'écran de sélection ; livrée et testée).
2. Nombre maximal de **namespaces distincts par profil** : **32** (l'application n'en émet que 7). Choix d'un **plafond de cardinalité** plutôt que d'une liste blanche de noms — décision actée, voir Notes de développement §7.
3. Volume total par compte : plafond global **`MAX_STORAGE_BYTES_PER_ACCOUNT=20 Mio`** (valeur PO du 2026-08-13, dimensionnée pour 10 profils), vérifié à l'écriture.

## Critères d'acceptation

- [x] La création d'un profil au-delà du plafond renvoie 409 `PROFILE_LIMIT_REACHED`.
- [x] Un `PUT` créant un namespace au-delà du plafond de cardinalité renvoie 409 `NAMESPACE_LIMIT_REACHED` ; la mise à jour d'un namespace existant reste permise au plafond.
- [x] Un `PUT` qui dépasserait le quota de stockage renvoie 413 `STORAGE_QUOTA_EXCEEDED` et n'écrit rien ; le remplacement par un blob plus petit n'est jamais refusé.
- [x] Les namespaces génériques (forward-compat, ex. `future-domain.v2`) restent acceptés (non-régression `ObjectTest::testNamespaceValidationIsGenericAndSafe`).
- [ ] L'application Android gère les trois nouveaux codes sans afficher de trace technique. _(porté par [[T19-app-plafonds-items-et-hygiene-snapshots]], en cours — étape 7 de T19)_

## Cas limites et gestion des erreurs

* Le remplacement d'un blob existant par un plus petit ne doit jamais être refusé pour cause de quota.
* Le décompte de profils doit se faire sous le même verrou que l'insertion (sinon deux requêtes concurrentes franchissent la limite ensemble).
* La suppression d'un profil libère immédiatement son quota (cascade déjà en place sur `profile_objects`).

---

# 4. Spécification technique

## Composants impactés

* `backend/src/Profile/ProfileService.php` — décompte de profils sous `AdvisoryLock::account()` (verrou consultatif transactionnel par compte) avant `create()`, en réutilisant `ProfileRepository::lockIdsForAccount()`.
* `backend/src/Shared/Validator.php` — `namespace()` reste un contrôle de **format** pur (namespaces génériques conservés, pas de liste blanche — voir §7 Notes de développement) ; le plafond de cardinalité vit dans `ObjectService`.
* `backend/src/Sync/ObjectService.php` — `requireWithinQuota()` : plafond de cardinalité (`countNamespacesForProfile`) et somme des `compressed_size` du compte (`sumBytesForAccount`) avant `put()`/`delete()`, sous `AdvisoryLock::account()` puis le verrou namespace existant (ordre account→namespace).
* `backend/src/Sync/ObjectRepository.php` — `countNamespacesForProfile` et `sumBytesForAccount` (agrégat `SUM(compressed_size)` joint sur `profiles.account_id`).
* `backend/src/Database/AdvisoryLock.php` — `account()`, verrou consultatif par compte réutilisé par `ProfileService` et `ObjectService`.
* `backend/openapi.yaml` — documenter les trois codes d'erreur (étape 9, hors périmètre backend/étape 5).
* Application : mapping des codes dans la couche de synchronisation cloud — livré par [[T19-app-plafonds-items-et-hygiene-snapshots]].

## Modèles de données

Pas de migration pour la version minimale : décompte par agrégat. Index de couverture `profile_objects (profile_id) INCLUDE (compressed_size)` envisageable si l'agrégat devient coûteux (improbable à cette volumétrie).

## Sécurité

Décision actée (§7 Notes de développement) : pas de liste blanche de noms de namespaces — l'application accepte délibérément des namespaces génériques pour la compatibilité ascendante (tests `ValidatorTest::testNamespacesRemainGeneric`, `ObjectTest::testNamespaceValidationIsGenericAndSafe`). La mesure retenue est un **plafond de cardinalité** (`MAX_NAMESPACES_PER_PROFILE=32`) qui referme le vecteur d'explosion du nombre de namespaces sans restreindre les noms ni casser la généricité. Le durcissement du saut de ligne final sur `namespace()` est traité séparément par [[T18-backend-validation-entrees-ancres-regex]] (modificateur `D`).

---

# 5. Architecture

Les quotas sont des règles métier : couche service (`ProfileService`, `ObjectService`), sous transaction et verrou existants, jamais dans l'action HTTP. Valeurs exposées par `Config` (ajustables par variable d'environnement, bornées min/max).

---

# 6. Plan de développement

Backend (livré) :

- [x] Recenser les namespaces émis par l'application (7 via l'enum `SyncNamespace`) — informatif, cf. décision cardinalité.
- [x] Ajouter `MAX_PROFILES_PER_ACCOUNT=10`, `MAX_NAMESPACES_PER_PROFILE=32`, `MAX_STORAGE_BYTES_PER_ACCOUNT=20 Mio` dans `Config` (relayées au service `php`/`php-test` de Compose).
- [x] Décompte de profils sous `AdvisoryLock::account()` dans `ProfileService::create()` (transaction + `lockIdsForAccount`).
- [x] Plafond de cardinalité de namespaces + quota de stockage dans `ObjectService::put()`/`delete()` (`requireWithinQuota`, `AdvisoryLock::account()` puis verrou namespace, agrégats `ObjectRepository::countNamespacesForProfile` / `sumBytesForAccount`).
- [x] Tests d'intégration : franchissement des trois limites + non-régression sur remplacement de blob (`ProfileTest`, `ObjectTest`).
- [x] Test de concurrence : créations de profil simultanées sur la dernière place, courses namespace/stockage même profil et profils distincts (`ConcurrencyTest`).

Hors périmètre backend (portées par d'autres tickets/étapes) :

- [ ] Documenter les codes d'erreur dans `openapi.yaml`. _(étape 9)_
- [ ] Mapper les nouveaux codes côté application. _(porté par [[T19-app-plafonds-items-et-hygiene-snapshots]], en cours)_
- [ ] (Facultatif) Rejouer la démonstration en prod avec une session de test pour clore le volet reproduction.

---

# 7. Notes de développement

- **Décision majeure — liste blanche → plafond de cardinalité.** La spec initiale prévoyait une liste blanche de noms de namespaces. Trois tests existants (`ValidatorTest::testNamespacesRemainGeneric`, `ObjectTest::testNamespaceValidationIsGenericAndSafe`, `ObjectApiTest` avec `future-data`) montrent que le produit **accepte délibérément des namespaces génériques** pour la compatibilité ascendante. Une liste blanche stricte aurait cassé ce choix (et ces tests). Le vecteur réel étant l'**explosion du nombre** de namespaces, un **plafond de cardinalité par profil** (`MAX_NAMESPACES_PER_PROFILE=32`, l'app en émet 7) ferme le vecteur **sans** restreindre les noms ni casser la généricité. `Validator::namespace()` reste un contrôle de format pur ; le plafond vit dans `ObjectService::put()`.
- **Composants ajoutés** : `ProfileService` (compte sous verrou), `ObjectService::requireWithinQuota`, `ObjectRepository::countNamespacesForProfile` + `sumBytesForAccount`. Câblage dans `Bootstrap` depuis `Config`.
- **Codes d'erreur** : 409 `PROFILE_LIMIT_REACHED`, 409 `NAMESPACE_LIMIT_REACHED`, 413 `STORAGE_QUOTA_EXCEEDED`.
- **Quota de stockage** : projeté = `SUM(compressed_size) du compte − taille de l'objet remplacé + nouvelle taille`, donc un remplacement plus petit passe toujours. Vérifié sous la transaction et le verrou consultatif de namespace déjà tenus par `put()`.
- Suite complète verte : 132 tests / 643 assertions (étape 5).
- **Étape 7 (2026-08-13) — corrections R1–R3** : la « limite connue » de concurrence notée en étape 5 est **levée** — un verrou consultatif par compte (`AdvisoryLock::account`) rend atomiques le recompte de profils et les agrégats namespaces/stockage (y compris entre profils). Ordre de verrouillage documenté account→namespace. Courses E2E ajoutées dans `ConcurrencyTest`. Passthrough Compose complété.
- **Valeurs PO (2026-08-13)** : `MAX_PROFILES_PER_ACCOUNT` porté à **10**, `MAX_STORAGE_BYTES_PER_ACCOUNT` à **20 Mio** (dimensionné pour 10 profils, cf. mesures playback). Les **plafonds par item** (playback 10000, recently-watched 20, favoris 500) et le retrait de `plot` du snapshot playback sont **côté application** — nouveau ticket T19.
- Suite complète verte après corrections : **142 tests / 670 assertions**.
- **Étape 7 (2026-08-13) — correction T14-R4** : sections 3, 4 et 6 réconciliées avec les valeurs PO et l'implémentation réellement livrées (10 profils, 32 namespaces génériques par profil sans liste blanche, 20 Mio par compte, verrou consultatif par compte, courses E2E présentes). Les tâches backend terminées sont distinguées des tâches application/documentation restantes ([[T19-app-plafonds-items-et-hygiene-snapshots]], étape 9).

---

# 8. Review

Date : 2026-08-13

Status : CHANGES REQUESTED

## Périmètre relu

- `backend/src/Profile/ProfileService.php`
- `backend/src/Profile/ProfileRepository.php`
- `backend/src/Sync/ObjectService.php`
- `backend/src/Sync/ObjectRepository.php`
- `backend/src/Shared/Config.php`
- `backend/src/Bootstrap.php`
- `backend/docker-compose.yml`
- `backend/tests/Integration/ProfileTest.php`
- `backend/tests/Integration/ObjectTest.php`
- `backend/tests/Integration/ConcurrencyTest.php`

## Critique

Aucun constat.

## Majeur

Aucun constat. Les anciens T14-R1 et T14-R2 sont résolus : le verrou consultatif
par compte sérialise les créations et les agrégats, et les quatre courses E2E
correspondantes passent.

## Mineur

### T14-R4 — La spécification active contredit les valeurs PO et l'implémentation

**Description :** les règles métier annoncent encore 8 profils et 16 Mio, alors
que la décision PO, `Config`, Compose et les tests appliquent 10 profils et
20 Mio. La spécification technique impose encore une liste blanche de
namespaces, alors que la décision consignée et le code conservent volontairement
des noms génériques avec un plafond de cardinalité. Le plan affirme également
que la course de création n'est pas testée, bien que `ConcurrencyTest` la couvre.

**Impact :** le ticket, censé être la source de vérité, donne trois contrats
incompatibles à une future correction, à l'exploitation et à la validation.
Une implémentation ultérieure pourrait réintroduire la liste blanche ou revenir
aux anciens plafonds malgré un code actuellement correct.

**Correction attendue :** aligner les sections 3, 4 et 6 sur la décision
effective : 10 profils, 32 namespaces génériques par profil, 20 Mio par compte,
verrou consultatif par compte et courses E2E présentes. Distinguer clairement
les tâches backend terminées des tâches application/documentation encore
portées par T19 ou une étape ultérieure.

## Corrections demandées

- [x] T14-R1 — `ProfileService::create()` prend `AdvisoryLock::account()` (verrou consultatif transactionnel par compte) avant recompte + insertion : la course de phantom est fermée. Course E2E `ConcurrencyTest::testTwoSimultaneousProfileCreationsCannotExceedTheLimit` (seed 9, race 2 → `[201, 409]`, 10 lignes).
- [x] T14-R2 — `ObjectService::put()`/`delete()` prennent `AdvisoryLock::account()` **avant** le verrou namespace (ordre unique account→namespace, pas de deadlock) ; les agrégats cardinalité + stockage sont lus sous ce verrou. Courses E2E : dernière place de namespace, stockage sur un profil, et **stockage partagé entre deux profils** du compte.
- [x] T14-R3 — `MAX_PROFILES_PER_ACCOUNT`, `MAX_NAMESPACES_PER_PROFILE`, `MAX_STORAGE_BYTES_PER_ACCOUNT` relayées au service `php` (et `php-test`) de Compose.
- [x] T14-R4 — Spécification et plan réconciliés avec les valeurs PO et l'architecture réellement livrées (RESOLVED).

## Vérifications effectuées

- Les trois erreurs attendues sont bien renvoyées lors des franchissements séquentiels, et le remplacement d'un blob existant soustrait sa taille courante avant projection.
- La suppression d'un profil continue de libérer ses objets par cascade.
- `docker compose exec -T php-test composer test` : succès, **142 tests / 670 assertions**, sans test ignoré.
- `docker compose exec -T php-test composer validate --strict` : `composer.json` valide.
- Les courses profils, cardinalité et stockage (même profil et profils distincts) sont incluses dans cette exécution.
- `git diff --check` : aucun défaut d'espaces dans les changements suivis ; contrôle direct des tickets non suivis : aucun espace final.

## Limite de la review

La reproduction authentifiée en production reste ouverte et ne conditionne pas
la preuve automatisée locale. Aucune correction de code n'a été effectuée à
cette étape.

---

# 9. Release

Version :

Commit :

Date :
