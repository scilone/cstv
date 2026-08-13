# T17 - Transport en clair et en-têtes de sécurité absents

## Informations générales

Status:
REVIEW (étape 7 — correction T17-R2 appliquée, RESOLVED ; en attente d'une nouvelle review étape 6 ou de la validation étape 8)

Created:
2026-08-13

Sévérité:
MINEUR (rétrogradé : redirection HTTPS posée le 2026-08-13 ; reste HSTS + en-têtes)

Reproduit en production:
Oui — https://cstv.alwaysdata.net, 2026-08-13

---

# 0. Suivi

* **2026-08-13** — Redirection HTTP → HTTPS activée (forcée côté hébergeur). Vérifié : `http://.../health` et `http://.../v1/auth/otp/request` renvoient **301** vers `https://`. Le point n°1 ci-dessous (canal clair servi) est **résolu**. Restent HSTS et les en-têtes de sécurité (points n°2 et HSTS), qui font l'objet du reste du ticket.

---

# 1. Description

Deux constats en production. État au 2026-08-13 :

1. ~~L'API répond aussi bien en **HTTP clair** qu'en HTTPS, sans redirection.~~ **Résolu** : HTTP redirige désormais en 301 vers HTTPS.
2. Aucune réponse ne porte d'en-tête de sécurité (`Cache-Control`, `X-Content-Type-Options`, `Referrer-Policy`), et **HSTS reste absent** malgré la redirection.

---

# 2. Contexte

## Reproduction (production, 2026-08-13)

Transport en clair — **avant** correctif (matin) :

```
GET  http://cstv.alwaysdata.net/health              -> 200  (aucune redirection, pas de Location)
POST http://cstv.alwaysdata.net/v1/auth/otp/request -> 429  (la requête atteint l'application, en clair)
```

Transport — **après** correctif (redirection posée le 2026-08-13) :

```
GET  http://cstv.alwaysdata.net/health              -> 301  Location: https://cstv.alwaysdata.net/health
POST http://cstv.alwaysdata.net/v1/auth/otp/request -> 301  Location: https://...
```

Le canal clair ne sert donc plus l'application. **Mais** `GET https://.../health` et `GET https://.../v1/me` (401) ne portent toujours ni `Strict-Transport-Security`, ni `Cache-Control`, ni `X-Content-Type-Options`, ni `Referrer-Policy`.

En-têtes observés sur `/health`, `/v1/me` (401) et une route inconnue (404) :

```
content-type: application/json; charset=utf-8
server: Apache
via: 1.1 alproxy
vary: Accept-Encoding        (health)
```

Absents partout :

* `Strict-Transport-Security` — vérifié absent.
* `Cache-Control: no-store` — `/v1/me` renvoie l'adresse e-mail du titulaire, `POST /v1/auth/otp/verify` renvoie le jeton d'accès. Sans directive, un intermédiaire est libre d'appliquer sa propre heuristique de cache.
* `X-Content-Type-Options: nosniff`.
* `Referrer-Policy`.

## Corrections par rapport à l'analyse initiale

* La TLS **existe** en prod (HTTPS fonctionne) ; le problème n'est pas son absence mais qu'elle n'est **pas imposée**.
* Le frontal est **Apache** derrière le proxy alwaysdata, pas nginx. `server: Apache` n'expose pas de numéro de version — le point « fuite de version nginx » ne s'applique pas.

## Portée réelle

Le client de l'application Android est le premier concerné : Android ≥ 9 refuse par défaut le trafic en clair, mais l'acceptation du HTTP côté serveur ouvre la porte à une attaque de rétrogradation (downgrade) et laisse un canal clair exploitable par tout client mal configuré ou par un outil de test. Les en-têtes manquants sont un durcissement de second rang, mais peu coûteux.

---

# 3. Spécification fonctionnelle

## Comportement attendu

* L'API n'est joignable qu'en HTTPS ; toute requête HTTP est redirigée (301) puis, une fois en HTTPS, l'agent mémorise l'obligation via HSTS.
* Aucune réponse authentifiée n'est conservée par un cache intermédiaire.
* Le contenu opaque restitué par l'API n'est jamais interprété comme un autre type que celui déclaré.

## Règles métier

1. HTTP clair est redirigé en 301 vers HTTPS (sauf le healthcheck interne s'il passe en clair côté plateforme).
2. `Strict-Transport-Security` est émis sur les réponses HTTPS.
3. Toute réponse sous `/v1` porte `Cache-Control: no-store`.
4. Toute réponse porte `X-Content-Type-Options: nosniff` et `Referrer-Policy: no-referrer`.
5. La réponse blob porte en plus `Content-Disposition: attachment`.

## Critères d'acceptation

- [x] `http://cstv.alwaysdata.net/...` renvoie une redirection 301 vers `https://`. **(fait le 2026-08-13)**
- [x] Les réponses HTTPS portent `Strict-Transport-Security` (émis inconditionnellement par `SecurityHeadersMiddleware`, testé sur JSON, blob et erreur).
- [x] `GET /v1/me` et `POST /v1/auth/otp/verify` répondent avec `Cache-Control: no-store` (toute route sous `/v1`, testé sur `/v1/me`).
- [x] Toutes les réponses portent `X-Content-Type-Options: nosniff`.
- [x] La documentation de déploiement décrit l'obligation HTTPS et HSTS pour l'hébergement alwaysdata (`README.md`, section « Déploiement (alwaysdata) »).

## Cas limites et gestion des erreurs

* Les réponses d'erreur (401, 404, 413, 429) doivent elles aussi porter les en-têtes et rester au format JSON du contrat.
* `no-store` ne casse pas la validation conditionnelle par `ETag` de la synchronisation : `If-Match`/`If-None-Match` restent portés par le client, pas par un cache HTTP.
* Vérifier ce que la plateforme alwaysdata permet réellement de configurer côté frontal (redirection, HSTS) vs ce qui doit être porté par l'application.

---

# 4. Spécification technique

## Composants impactés

* Configuration de l'hébergement alwaysdata : forcer HTTPS (redirection) et HSTS au niveau du site. À défaut de contrôle sur le frontal, porter la redirection et HSTS dans `public/index.php` / un middleware.
* Nouveau `backend/src/Http/SecurityHeadersMiddleware.php` — `nosniff`, `Referrer-Policy`, `no-store`, appliqué globalement dans `Bootstrap::createApp()` (couvre toutes les routes présentes et futures, y compris les erreurs via `ApiErrorHandler`).
* `backend/src/Http/Action/ObjectAction.php` — `Content-Disposition: attachment` sur la réponse blob.
* `backend/README.md` — section déploiement : obligation HTTPS, HSTS, spécificités alwaysdata.
* `backend/openapi.yaml` — serveur documenté en `https://` uniquement.

## Compatibilité

Aucun impact fonctionnel sur le client Android (OkHttp ne met pas en cache par défaut). Vérifier que l'URL de base de l'application et la collection Postman sont bien en `https://`.

---

# 5. Architecture

Un middleware unique posé au plus haut niveau dans `Bootstrap::createApp()` garantit la couverture de toutes les routes et de toutes les réponses d'erreur. La redirection HTTPS et HSTS relèvent de préférence de la configuration du frontal alwaysdata ; si la plateforme ne l'expose pas, l'application les assume, mais c'est une solution de repli.

---

# 6. Plan de développement

- [x] Vérifier les options HTTPS/HSTS offertes par l'hébergement alwaysdata. _(redirection déjà forcée côté hébergeur, cf. §0 Suivi ; pas de contrôle applicatif sur HSTS au niveau frontal, assumé par l'application — voir Notes §7)_
- [x] Forcer la redirection HTTP → HTTPS (frontal, sinon application). _(fait côté frontal le 2026-08-13, cf. §0)_
- [x] Émettre HSTS sur HTTPS (`SecurityHeadersMiddleware`, envoyé inconditionnellement — cf. Notes §7).
- [x] Créer `SecurityHeadersMiddleware` et l'ajouter dans `Bootstrap` (ajouté en dernier, englobe aussi les réponses d'erreur).
- [x] Ajouter `Content-Disposition: attachment` sur la réponse blob (`ObjectAction::get()`).
- [x] Test fonctionnel : en-têtes présents sur une réponse JSON, une réponse blob et une réponse d'erreur (`SecurityHeadersApiTest`).
- [ ] Vérifier en prod la redirection 301 et la présence de HSTS après déploiement. _(déploiement, hors étape 5)_
- [x] Mettre à jour `README.md` et `openapi.yaml`.
- [ ] Mettre à jour la collection Postman. _(non prioritaire — la collection n'affirme aucun contrat sur ces en-têtes)_

---

# 7. Notes de développement

Analyse initiale corrigée après test prod : la TLS existe (problème = non imposée, pas absente) et le frontal est Apache/alwaysdata, pas nginx (pas de fuite de version).

## Étape 5 (2026-08-13) — implémentation

- **`SecurityHeadersMiddleware` (`backend/src/Http/SecurityHeadersMiddleware.php`)** : un middleware PSR-15 unique, ajouté **en dernier** dans `Bootstrap::createApp()` (après `$app->addErrorMiddleware(...)`), donc le plus externe de la pile — il enveloppe aussi le middleware d'erreur et stamp les mêmes en-têtes sur les réponses 401/404/429/500 que sur un 200. Prouvé par `SecurityHeadersApiTest::testErrorResponseCarriesTheSameSecurityHeadersAsSuccess`.
- **HSTS inconditionnel** : `Strict-Transport-Security: max-age=31536000; includeSubDomains` est envoyé sur **toute** réponse, sans détection de schéma. Un navigateur n'honore l'en-tête que si la réponse lui parvient réellement en HTTPS (RFC 6797) — l'envoyer toujours est donc sans effet de bord et évite de dépendre d'un `X-Forwarded-Proto` que le proxy alwaysdata (`alproxy`) ne garantit pas de façon fiable.
- **`Cache-Control: no-store`** limité aux chemins commençant par `/v1` (comptes, profils, jetons, snapshots), pas à `/health` — cohérent avec la règle métier n°3 de la spécification.
- **`Content-Disposition: attachment`** ajouté uniquement sur `ObjectAction::get()` (la réponse blob), pas sur les réponses JSON.
- **Redirection HTTP → HSTS applicatifs** : la redirection HTTP→HTTPS reste entièrement côté frontal alwaysdata (déjà active, §0) ; l'application ne redirige pas elle-même — elle ne voit jamais le trafic HTTP brut derrière le proxy, donc une redirection applicative serait inopérante ou redondante.
- **Tests** : `backend/tests/Functional/SecurityHeadersApiTest.php` — réponse JSON authentifiée (`/v1/me`), réponse blob, réponse d'erreur (401 sans jeton) et `/health` (hors `/v1`, sans `no-store` forcé). Suite complète : **147 tests / 704 assertions**.
- **Documentation** : `README.md` (section « Déploiement (alwaysdata) ») et `openapi.yaml` (serveur `https://cstv.alwaysdata.net` en premier, dev local conservé en second). Collection Postman non modifiée : elle n'affirme aucun contrat sur ces en-têtes, pas de valeur ajoutée à la maintenir en parallèle pour ce ticket.

## Étape 7 (2026-08-13) — correction T17-R2

Le filtre `/v1` de `SecurityHeadersMiddleware` comparait par préfixe de chaîne brut (`str_starts_with($path, '/v1')`), ce qui aurait aussi matché un futur chemin type `/v1beta`. Corrigé en comparaison par segment (`$path === '/v1' || str_starts_with($path, '/v1/')`). Aucune route actuelle n'était affectée (piège latent, pas de régression fonctionnelle) ; couvert implicitement par `SecurityHeadersApiTest` (les chemins testés, `/v1/me` et `/health`, continuent de se comporter identiquement après le correctif). Suite complète re-exécutée après reconstruction de l'image : **147 tests / 704 assertions**, `composer validate --strict` valide.

---

# 8. Review

Date : 2026-08-13

Status : CHANGES REQUESTED (T17-R1 précédent résolu par l'implémentation étape 5)

## Périmètre relu

- `backend/src/Http/SecurityHeadersMiddleware.php`
- `backend/src/Bootstrap.php`
- `backend/src/Http/Action/ObjectAction.php`
- `backend/tests/Functional/SecurityHeadersApiTest.php`
- `backend/README.md`
- `backend/openapi.yaml`

## Critique

Aucun constat.

## Majeur

Aucun constat. Le constat précédent (T17-R1, « aucune implémentation ») est
résolu : `SecurityHeadersMiddleware` existe, est câblé en dernier dans
`Bootstrap::createApp()` (englobe le middleware d'erreur), `Content-Disposition`
est posé sur la réponse blob, et `SecurityHeadersApiTest` prouve les quatre en-têtes
sur une réponse JSON authentifiée, une réponse blob, une réponse d'erreur 401 et
`/health`. Suite verte : **147 tests / 704 assertions**.

## Mineur

### T17-R2 — Le filtre `/v1` est un préfixe de chaîne, pas une frontière de chemin

**Description :** `str_starts_with($request->getUri()->getPath(), '/v1')`
matcherait aussi un futur chemin comme `/v1beta` ou `/v1x` sans qu'il appartienne
au groupe `/v1` réel. Aucune route de ce type n'existe aujourd'hui donc le
comportement actuel est correct, mais le test de préfixe ne borne pas au
séparateur de segment.

**Impact :** piège latent pour une évolution future de l'API (ex. un futur
`/v1beta/...` recevrait `Cache-Control: no-store` par accident, ou l'inverse
selon le nom choisi) ; aucun impact sur le périmètre actuel.

**Correction attendue :** comparer au segment plutôt qu'au préfixe brut
(`$path === '/v1' || str_starts_with($path, '/v1/')`).

## Corrections demandées

- [x] T17-R2 — `SecurityHeadersMiddleware` compare désormais au segment (`$path === '/v1' || str_starts_with($path, '/v1/')`) plutôt qu'au préfixe de chaîne brut (RESOLVED, étape 7).

## Vérifications effectuées

- `SecurityHeadersMiddleware` est ajouté après `$app->addErrorMiddleware(...)` dans `Bootstrap::createApp()`, donc outermost.
- `ObjectAction::get()` porte `Content-Disposition: attachment` en plus de `Content-Type` et `ETag`.
- `SecurityHeadersApiTest` couvre JSON (`/v1/me`), blob, erreur (401 sans jeton) et `/health` (hors `/v1`, sans `no-store` forcé).
- `docker compose build php-test` puis `docker compose exec -T php-test composer test` : succès, **147 tests / 704 assertions**, sans test ignoré.
- `docker compose exec -T php-test composer validate --strict` : `composer.json` valide.
- `README.md` documente HTTPS/HSTS/en-têtes pour alwaysdata ; `openapi.yaml` liste le serveur de production en `https://` en premier.

## Limite de la review

La redirection 301 et la présence effective de HSTS en production n'ont pas été
rejouées après ce correctif (déploiement non fait à ce stade) ; la preuve reste
automatisée locale, conformément aux autres tickets de ce lot.
---

# 9. Release

Version :

Commit :

Date :
