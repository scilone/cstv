# T16 - Absence de limitation de débit sur POST /v1/auth/otp/verify

## Informations générales

Status:
REVIEW (étape 7 — corrections T16-R1–R6 appliquées, RESOLVED ; en attente d'une nouvelle review étape 6 ou de la validation étape 8)

Created:
2026-08-13

Sévérité:
MOYEN

Reproduit en production:
Oui — https://cstv.alwaysdata.net, 2026-08-13 (confirmé par l'access log Apache)

---

# 1. Description

L'endpoint `POST /v1/auth/otp/verify` ne porte aucune limite de débit. Un client peut l'appeler autant de fois qu'il veut. Chaque appel ouvre une transaction PostgreSQL et prend un verrou consultatif (`pg_advisory_xact_lock` via `OtpRepository::lockEmail()`), donc une consommation de ressources déclenchable à volonté par un client anonyme.

---

# 2. Contexte

## Reproduction (production, 2026-08-13)

30 appels consécutifs à `POST /v1/auth/otp/verify`, adresses distinctes, code bidon, tous en environ une seconde. Extrait de l'access log Apache :

```
88.160.129.224 - [13/Aug/2026:02:23:35 +0200] "POST /v1/auth/otp/verify HTTP/1.1" 400 65 ... 0.019
88.160.129.224 - [13/Aug/2026:02:23:35 +0200] "POST /v1/auth/otp/verify HTTP/1.1" 400 65 ... 0.015
... (30 lignes, toutes 400, aucune 429)
```

Aucune requête refusée pour débit. Toutes traversent la transaction, le verrou et la comparaison HMAC.

## Ce qui est déjà correct (vérifié — ne pas re-traiter)

* **`REMOTE_ADDR` contient l'IP réelle du client.** L'access log montre `%h = 88.160.129.224` (mon IP publique) pour mes requêtes, et l'IP réelle d'un client applicatif (`okhttp/4.12.0`) pour son trafic. Le proxy alwaysdata (`via: 1.1 alproxy`) transmet donc l'adresse réelle. **Les limites par IP opèrent bien par client**, pas sur une adresse partagée. Le risque de « bucket partagé » envisagé initialement est écarté.
* **Limite par e-mail sur `otp/request`** : 6ᵉ appel sur la même adresse → 429 (`OTP_REQUEST_LIMIT_EMAIL=5`). Fonctionnel.
* **Limite par IP sur `otp/request`** : présente, se déclenche, et — puisque `REMOTE_ADDR` est fiable — protège réellement par client.
* **`X-Forwarded-For` n'est pas honoré** : sans effet sur les compteurs. Sain, d'autant que l'IP réelle est déjà disponible.
* **`attempts_left` (5)** borne les essais sur un code donné : la force brute d'un code précis reste limitée à 5 tentatives.

Le seul manque avéré est l'absence totale de plafond sur `verify` lui-même.

## Portée réelle

L'intérêt d'un attaquant n'est pas de deviner un code (borné par `attempts_left`) mais de **saturer la capacité de traitement**. La prod tourne en `mod_fcgid` (Apache 2.4.68), pas en pool FPM à taille fixe : un flot sur `verify` fait tourner des processus fcgid et, surtout, mobilise une transaction PostgreSQL + un verrou consultatif par appel. Un flot suffisant dégrade la base et le service, authentification comprise.

Note : l'endpoint distingue aussi les états d'une adresse par ses codes d'erreur (`INVALID_OTP` / `OTP_EXPIRED` / `OTP_CONSUMED` / `OTP_ATTEMPTS_EXCEEDED`), ce qui permet, sans limite de débit, de sonder si une demande d'OTP est en cours pour une adresse donnée.

---

# 3. Spécification fonctionnelle

## Comportement attendu

* Un client légitime vérifie son code sans jamais être bloqué en usage normal.
* Un flot anormal sur `verify` est freiné avant de mobiliser une transaction par appel.

## Règles métier

1. `POST /v1/auth/otp/verify` est soumis à une limite de débit par IP cliente (fiable, cf. §2), indépendante d'`attempts_left`.
2. La limite s'applique au plus tôt : au frontal si la plateforme alwaysdata le permet, sinon en applicatif comme source de vérité.

## Critères d'acceptation

- [ ] Un flot de requêtes `verify` depuis une même IP est ralenti (429) avant de mobiliser une transaction pour chacune.
- [ ] Un client légitime effectuant une poignée de vérifications n'est jamais impacté.
- [ ] Le refus renvoie le format d'erreur JSON du contrat (le 429 d'`otp/request` est déjà au bon format, à répliquer).

## Cas limites et gestion des erreurs

* NAT opérateur : garder la limite assez large pour ne pas pénaliser des utilisateurs mobiles partageant une adresse. La limite par e-mail sur `request` reste la protection fine côté compte.
* IPv6 : l'access log montre des clients en IPv6 ; normaliser l'adresse (préfixe) pour ne pas compter séparément deux écritures de la même origine, et éviter qu'un attaquant tourne trivialement sur un /64.

---

# 4. Spécification technique

## Composants impactés

* `backend/src/Auth/AuthService.php` — limite de débit sur le chemin `verify`, sur l'IP fournie par `REMOTE_ADDR`.
* Nouvelle table `auth_throttle (ip, window_start, failures)` avec purge par fenêtre, ou décompte des échecs récents par IP.
* `backend/src/Shared/Config.php` — paramètres de la limite `verify` (plafond, fenêtre), bornés min/max comme le reste.
* Frontal alwaysdata : activer une limitation sur `/v1/auth/otp/verify` si la plateforme l'expose (à vérifier ; à défaut, applicatif seul).

## Compatibilité

Aucun impact sur le contrat HTTP côté application, hormis l'ajout du code 429 déjà documenté pour `otp/request`.

---

# 5. Architecture

Comme `REMOTE_ADDR` est fiable, la clé de limitation est directement l'adresse du pair. Pas besoin de composant de résolution d'IP de confiance : la limite `verify` réutilise le même mécanisme de fenêtre glissante que `request`, sur la même source d'adresse.

---

# 6. Plan de développement

- [x] Ajouter la table de throttle (migration `003_verify_throttle.sql` → `auth_verify_attempts`) et le décompte par IP (`OtpRepository::countRecentVerifyForIp` / `recordVerifyAttempt` / `purgeStaleVerifyAttempts`).
- [x] Implémenter la limite sur `verify` dans `AuthService::throttleVerify()` (avant toute transaction ; l'IP est passée par `AuthAction::verify`).
- [x] Paramétrer plafond/fenêtre dans `Config` (`OTP_VERIFY_LIMIT_IP=30`, `OTP_VERIFY_WINDOW_SECONDS=60`).
- [x] Tests d'intégration : limite `verify` atteinte (429 `OTP_VERIFY_RATE_LIMITED`), code malformé ne consomme pas de créneau, non-régression sur la vérification légitime.
- [x] Normalisation IPv6 /64 (`Shared\ClientIp::rateLimitKey`) + `ClientIpTest`.
- [ ] Vérifier si le frontal alwaysdata permet une limitation en amont ; documenter le choix. _(infra/déploiement, hors étape 5)_

---

# 7. Notes de développement

- 2026-08-13 : throttle par IP posé **avant** `beginTransaction` — sous flot, la vérification coûte un `SELECT COUNT` + un `INSERT` au lieu d'une transaction + verrou consultatif + HMAC. Seules les tentatives acceptées sont enregistrées : la table est bornée à ≈`limit` lignes par IP par fenêtre, et chaque appel purge ses propres lignes périmées.
- Clé de limitation = `ClientIp::rateLimitKey(REMOTE_ADDR)` : IPv4 inchangée, IPv6 réduite au /64 pour empêcher la rotation triviale des bits d'hôte.
- Le code malformé (422) est rejeté **avant** le throttle, donc ne consomme pas de créneau (couvert par test).
- Suite complète verte : 132 tests / 643 assertions.
- Constats initiaux écartés après examen de l'access log prod : contournement par `X-Forwarded-For` (non honoré) et hypothèse de bucket IP partagé (`REMOTE_ADDR` = IP réelle du client, transmise par alproxy). Seule l'absence de limite sur `verify` est retenue.
- **Étape 7 (2026-08-13)** — corrections R1–R4 appliquées : admission atomique sous verrou consultatif par IP (avant `beginTransaction` du chemin OTP, dans sa propre transaction courte), purge globale amortie + index `created_at`, passthrough Compose, `429` OpenAPI. Nouvelle course E2E dédiée. Suite : **142 tests / 670 assertions**.
- **Étape 7 bis (2026-08-13) — corrections R5–R6** : `OtpRepository::purgeExpiredVerifyAttempts()` supprime désormais par lot strictement borné (`DELETE ... WHERE id IN (SELECT id ... ORDER BY created_at ASC LIMIT :limit)`, `AuthService::VERIFY_PURGE_BATCH_LIMIT=500`) et retourne le nombre de lignes effacées ; un pic d'IP expirées ne peut plus coûter une suppression illimitée en une requête, et des appels successifs (échantillonnage existant) finissent la purge par lots sans dépendre du retour d'une IP précise. Test `AuthTest::testPurgeOfExpiredVerifyAttemptsIsBoundedAcrossMultipleIps` (12 IP distinctes, lots de 5) prouve le plafond et la convergence. OpenAPI documente désormais `OTP_VERIFY_RATE_LIMITED` (exemple sur la réponse 429 de `verifyOtp`). Migration et commentaires de code alignés sur le mécanisme réel (purge globale échantillonnée et bornée en lignes, plus « par IP appelante »). Suite : **143 tests / 675 assertions**.

---

# 8. Review

Date : 2026-08-13

Status : CHANGES REQUESTED

## Périmètre relu

- `backend/migrations/003_verify_throttle.sql`
- `backend/src/Auth/AuthService.php`
- `backend/src/Auth/OtpRepository.php`
- `backend/src/Http/Action/AuthAction.php`
- `backend/src/Shared/ClientIp.php`
- `backend/src/Shared/Config.php`
- `backend/src/Bootstrap.php`
- `backend/docker-compose.yml`
- `backend/openapi.yaml`
- `backend/tests/Integration/AuthTest.php`
- `backend/tests/Unit/ClientIpTest.php`

## Critique

Aucun constat.

## Majeur

### T16-R5 — La purge dite « bornée » peut supprimer toute la table expirée en une requête

**Description :** l'admission est désormais atomique, mais
`OtpRepository::purgeExpiredVerifyAttempts()` exécute un `DELETE` global sans
limite de lignes. L'échantillonnage à 1/50 borne seulement la fréquence, pas le
volume ni la durée d'une purge. Après une rotation massive d'IP, une requête
anonyme tirée au sort peut donc payer la suppression de toutes les lignes
expirées, le WAL et les verrous associés. Aucun test ne couvre la purge de
plusieurs IP ni un traitement par lots.

**Impact :** le mécanisme anti-DoS peut produire périodiquement une opération
base très lourde sur le chemin HTTP qu'il protège. Cela remplace la croissance
indéfinie de T16-R2 par un pic de charge non borné et contredit explicitement la
correction annoncée comme « purge globale bornée ».

**Correction attendue :** supprimer les lignes expirées par lots strictement
bornés (ou déplacer la rétention dans une maintenance planifiée observable),
en conservant l'index `created_at`. Ajouter un test repository/intégration avec
plusieurs IP prouvant qu'un lot est borné et que des appels successifs finissent
la purge sans nécessiter le retour de chaque IP.

## Mineur

### T16-R6 — Le contrat et les commentaires décrivent une correction différente du code

**Description :** OpenAPI expose désormais le status 429 sur `verifyOtp`, mais
ne documente toujours pas le code `OTP_VERIFY_RATE_LIMITED` demandé par T16-R4.
Par ailleurs, la migration et les notes affirment encore que chaque tentative
purge les lignes de sa propre IP, alors que le code effectue une purge globale
échantillonnée. Le ticket qualifie cette purge de bornée alors qu'elle ne l'est
pas en nombre de lignes.

**Impact :** clients et exploitants ne disposent pas du code métier exact ni
du bon modèle de rétention. Le statut « R4 corrigé » et la source de vérité sont
donc trompeurs malgré une réponse HTTP fonctionnelle.

**Correction attendue :** documenter explicitement `OTP_VERIFY_RATE_LIMITED`
sur l'opération OpenAPI (description ou exemple du schéma d'erreur), puis
aligner migration, notes et checklist sur le mécanisme de purge réellement
retenu après correction de T16-R5.

## Corrections demandées

- [x] T16-R1 — Admission atomique par IP : `throttleVerify()` ouvre une transaction, prend `AdvisoryLock::verifyIp()` (verrou consultatif transactionnel sur la clé IP) puis purge/compte/insère. Course E2E `ConcurrencyTest::testConcurrentVerificationsCannotExceedTheIpThrottle` (30 simultanés, limite 15) : admis ≤ limite, lignes ≤ limite.
- [ ] T16-R2 — La purge est désormais globale, mais reste non bornée en volume ; remplacé par T16-R5.
- [x] T16-R3 — `OTP_VERIFY_LIMIT_IP` / `OTP_VERIFY_WINDOW_SECONDS` relayées au service `php` (et `php-test`) de Compose.
- [ ] T16-R4 — Le status `429` est présent, mais le code métier exact reste à documenter ; remplacé par T16-R6.
- [x] T16-R5 — Purge bornée par lot (`LIMIT`), test multi-IP prouvant le plafond et la convergence par appels successifs (RESOLVED).
- [x] T16-R6 — OpenAPI documente `OTP_VERIFY_RATE_LIMITED` ; migration et notes alignées sur la purge globale échantillonnée et bornée en lignes (RESOLVED).

## Vérifications effectuées

- Le throttle est appelé avant `beginTransaction()` du chemin OTP et les codes malformés sont rejetés avant de consommer une tentative.
- `ClientIp::rateLimitKey()` conserve IPv4 et regroupe les IPv6 par `/64` dans les cas unitaires prévus.
- La migration crée la table et son index composite ; le migrateur reste idempotent dans la suite.
- `docker compose exec -T php-test composer test` : succès, **142 tests / 670 assertions**, sans test ignoré.
- `docker compose exec -T php-test composer validate --strict` : `composer.json` valide.
- La course E2E de 30 vérifications confirme que l'admission par IP ne dépasse pas 15 entrées.
- `git diff --check` : aucun défaut d'espaces dans les changements suivis ; contrôle direct des tickets non suivis : aucun espace final.

**Étape 7 (2026-08-13) — re-vérification après corrections R5/R6** : `docker compose build php-test` (image reconstruite, source copiée à l'étape build) puis `docker compose exec -T php-test composer test` : succès, **143 tests / 675 assertions**, sans test ignoré. `composer validate --strict` : valide.

## Limite de la review

La review n'a pas rejoué le flot en production et n'a pas vérifié les
capacités de limitation du frontal alwaysdata. Aucun correctif de code n'a été
appliqué.

---

# 9. Release

Version :

Commit :

Date :
