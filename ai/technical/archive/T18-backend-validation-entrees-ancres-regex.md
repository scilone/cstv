# T18 - Ancres de validation non strictes (saut de ligne final accepté)

## Informations générales

Status:
REVIEW (étape 6 — APPROVED après relecture de T18-R1)

Created:
2026-08-13

Sévérité:
MINEUR

Reproduit en production:
Partiellement — classe de bug confirmée en prod via le chemin OTP ; conséquences à fort impact non vérifiables sans session authentifiée

---

# 1. Description

Les expressions régulières de validation utilisent l'ancre `$` sans le modificateur `D`. En PCRE, `$` accepte un saut de ligne final : ces validations sont franchissables avec un `\n` en fin de valeur. Le même idiome est utilisé pour le code OTP, l'UUID, le namespace et la version de schéma.

---

# 2. Contexte

## Reproduction (production, 2026-08-13)

Chemin OTP, non authentifié, donc testable directement en prod :

```
code "000000"    -> 400 / INVALID_OTP           (format valide, HMAC ne correspond pas)
code "000000\n"  -> 400 / INVALID_OTP           (le \n final passe la validation de format)
code "00000"     -> 422 / INVALID_OTP_FORMAT    (5 chiffres, correctement rejeté)
```

Le `\n` final aurait dû produire `422 INVALID_OTP_FORMAT`. Qu'il produise `400 INVALID_OTP` prouve que `preg_match('/^\d{6}$/', ...)` a laissé passer la valeur : **la classe de bug est présente dans le code déployé**.

## Motifs concernés (même faille)

`backend/src/Shared/Validator.php` :

```php
preg_match('/^[a-z0-9][a-z0-9._-]{0,63}$/', $value)   // namespace()
preg_match('/^[0-9a-f]{8}-...-[0-9a-f]{12}$/i', $value) // uuid()
```

`backend/src/Http/Action/ObjectAction.php` : `preg_match('/^[1-9]\d{0,9}$/', $rawSchemaVersion)`
`backend/src/Auth/AuthService.php` : `preg_match('/^\d{6}$/', $rawCode)` (celui reproduit ci-dessus)

## Conséquences non vérifiables en prod (nécessitent un jeton)

Les routes `/v1/profiles/...` passent par `AuthMiddleware` : sans jeton valide, elles renvoient 401 avant d'atteindre la validation. N'ayant pas de session prod (l'OTP part par e-mail réel), les impacts suivants n'ont **pas** pu être reproduits en production et restent au niveau revue de code :

* **Namespace** — un `namespace` avec `\n` final créerait un snapshot distinct de son homologue sans `\n` (constaté en local : `PUT .../objects/favorites%0A` → 204, puis listé comme namespace `"favorites\n"`). Duplication silencieuse d'un même namespace fonctionnel.
* **UUID** — un `profileId` d'URL avec `\n` final franchirait la validation puis échouerait au transtypage PostgreSQL, produisant un **500 INTERNAL_ERROR** (au lieu du 422 attendu) avec écriture au journal à chaque appel (constaté en local).

Ces deux points sont fournis comme motivation ; leur reproduction en prod dépend d'une session authentifiée (voir section 6).

---

# 3. Spécification fonctionnelle

## Comportement attendu

Une valeur d'entrée malformée produit toujours une erreur de validation 4xx documentée, jamais un caractère résiduel accepté ni un 500.

## Règles métier

Les validations sont strictes de bout en bout : aucun caractère après le motif, sauts de ligne compris.

## Critères d'acceptation

- [ ] `code "000000\n"` renvoie `422 INVALID_OTP_FORMAT`.
- [ ] Un `namespace` avec `\n` final renvoie `422 INVALID_NAMESPACE`.
- [ ] Un `profileId` avec `\n` final renvoie `422 INVALID_UUID`, jamais 500.
- [ ] Une version de schéma avec `\n` final est refusée avec le code documenté.

## Cas limites et gestion des erreurs

* Traiter aussi `\r\n` final, pas seulement `\n`.
* Auditer la base pour d'éventuels namespaces déjà malformés avant correction (suppression ou fusion).

---

# 4. Spécification technique

## Composants impactés

* `backend/src/Shared/Validator.php` — modificateur `D` (ou `\z` au lieu de `$`) sur `namespace()` et `uuid()`.
* `backend/src/Auth/AuthService.php` — même correction sur le motif du code OTP.
* `backend/src/Http/Action/ObjectAction.php` — même correction sur `X-Schema-Version`.

## Sécurité

Correction locale, sans effet de bord. La liste blanche de namespaces envisagée par ailleurs refermerait aussi le vecteur de duplication de namespace, mais le durcissement des ancres reste nécessaire pour l'UUID et le code OTP.

---

# 5. Architecture

Aucun impact architectural. Modification purement locale des motifs de validation.

---

# 6. Plan de développement

- [x] Corriger les quatre expressions régulières (modificateur `D`) — `Validator::namespace()`, `Validator::uuid()`, `AuthService` (code OTP), `ObjectAction` (X-Schema-Version).
- [x] Tests unitaires `Validator` : valeur valide + `\n`/`\r\n` final rejetés, pour `namespace()` et `uuid()`.
- [x] Test d'intégration du chemin OTP : `123456\n` → 422 `INVALID_OTP_FORMAT` (`AuthTest::testOtpCodeWithTrailingNewlineIsRejectedAsMalformed`).
- [ ] Avec une session de test fournie (ou un compte dont l'OTP est récupérable), vérifier en prod : namespace `\n` → 422, `profileId` `\n` → 422 (et non 500). _(hors étape 5 : nécessite un jeton prod)_
- [ ] Auditer la base pour d'éventuels namespaces malformés avant déploiement. _(tâche de déploiement)_

---

# 7. Notes de développement

- 2026-08-13 : les 4 motifs passent en anchré strict via le modificateur `D` (`$` PCRE accepte un `\n` final ; `D` le refuse). Aucune modification de comportement pour les entrées valides.
- Suite complète verte : 132 tests / 643 assertions (`docker compose exec php-test composer test`).
- La partie « rétention indéfinie des lignes `otp_codes` » évoquée dans une première version relève d'un fait de code (purge seulement par e-mail concerné) mais n'a pas d'observabilité en boîte noire ; elle est laissée hors de ce ticket faute de reproduction prod. À rouvrir si un accès base confirme l'accumulation.
- **Étape 7 (2026-08-13) — correction T18-R1** : ajout de tests d'intégration HTTP authentifiés (`ObjectTest`, DataProvider `%0A`/`%0D%0A`) prouvant namespace → 422 `INVALID_NAMESPACE` et `profileId` → 422 `INVALID_UUID` (exclut le 500) ; test OTP converti en DataProvider `\n`/`\r\n`. `X-Schema-Version` : non testable au niveau HTTP car slim-psr7 rejette les sauts de ligne dans les valeurs d'en-tête avant l'app — le `D` y demeure une ceinture-bretelles, documenté ici plutôt que couvert par un test infaisable. Suite : **142 tests / 670 assertions**.
- **Étape 7 bis (2026-08-13)** — relecture de la review du 2026-08-13 (§8, Status APPROVED) : Critique, Majeur et Mineur tous vides, T18-R1 déjà marqué `[x]` résolu. Aucun retour ouvert à corriger. Rien à faire à cette étape ; ticket prêt pour l'étape 8 (validation finale).

---

# 8. Review

Date : 2026-08-13

Status : APPROVED

## Périmètre relu

- `backend/src/Shared/Validator.php`
- `backend/src/Auth/AuthService.php`
- `backend/src/Http/Action/ObjectAction.php`
- `backend/tests/Unit/ValidatorTest.php`
- `backend/tests/Integration/AuthTest.php`
- `backend/tests/Integration/ObjectTest.php`
- `backend/tests/Functional/ObjectApiTest.php`

## Critique

Aucun constat.

## Majeur

Aucun constat. T18-R1 est résolu par les tests de routes authentifiées sur
`%0A` et `%0D%0A`, en plus des tests unitaires des validateurs et du chemin OTP.

## Mineur

Aucun constat supplémentaire.

## Corrections demandées

- [x] T18-R1 — Couvert automatiquement pour `\n` et `\r\n` : namespace (route → 422 `INVALID_NAMESPACE`), `profileId` (route → 422 `INVALID_UUID`, plus de 500), code OTP (422 `INVALID_OTP_FORMAT`). **Exception X-Schema-Version** : PSR-7 rejette une valeur d'en-tête contenant un saut de ligne avant l'application ; le modificateur `D` reste une défense en profondeur.

## Vérifications effectuées

- Les motifs de `Validator::namespace()`, `Validator::uuid()`, `AuthService::verify()` et `ObjectAction::put()` utilisent tous le modificateur PCRE `D`.
- Les tests unitaires et HTTP rejettent `\n` et `\r\n` pour namespace, UUID et OTP avec les codes exacts attendus.
- `docker compose exec -T php-test composer test` : succès, **142 tests / 670 assertions**, sans test ignoré.
- `docker compose exec -T php-test composer validate --strict` : `composer.json` valide.
- `git diff --check` : aucun défaut d'espaces dans les changements suivis ; contrôle direct des tickets non suivis : aucun espace final.

## Limite de la review

L'audit de la base et les reproductions en production restent des tâches de
déploiement hors de cette étape et ne remettent pas en cause l'approbation
technique locale. Aucun correctif de code n'a été appliqué.

---

# 9. Release

Version :

Commit :

Date :
