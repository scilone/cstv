# CSTV Backend POC

API HTTP légère pour l’authentification CSTV par email/OTP et la synchronisation de petits objets opaques par profil. Elle ne stocke ni fournisseur, ni identifiants, ni catalogue, ni installation ou device IPTV.

## Architecture

- Slim 4 et actions HTTP dans `src/Http/Action` ;
- services métier dans `src/Auth`, `src/Profile` et `src/Sync` ;
- repositories SQL PDO proches de chaque domaine ;
- connexion et migrations simples dans `src/Database` ;
- PostgreSQL 17, PHP-FPM 8.5, Nginx et OPcache via Docker Compose.

Le middleware JWT recharge systématiquement `accounts` depuis PostgreSQL. Un changement direct de `enabled` ou `active_until` prend donc effet dès la requête suivante. Les blobs gzip restent opaques : aucune décompression, aucun parsing et aucune recherche dans leur contenu.

## Démarrage

Docker et Docker Compose sont les seuls prérequis. Le port HTTP par défaut est `18080` pour éviter les ports de développement usuels ; `HTTP_PORT` permet de le changer.

```bash
cp .env.example .env
docker compose up -d --build
docker compose exec php bin/migrate
docker compose exec php bin/fixtures
curl http://localhost:18080/health
```

La copie de `.env.example` est recommandée, mais Compose possède des valeurs de développement par défaut et démarre aussi sans fichier `.env`. Les commandes équivalentes sont `make up`, `make migrate` et `make fixtures`.

## Configuration

| Variable | Rôle | Défaut de développement |
|---|---|---|
| `APP_ENV` | `dev`, `test` ou `production` | `dev` |
| `APP_DEBUG` | détails internes Slim, toujours à désactiver en production | `0` |
| `HTTP_PORT` | port Nginx exposé | `18080` |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | base et accès PostgreSQL | valeurs dans `.env.example` |
| `DB_HOST`, `DB_PORT` | connexion PHP vers PostgreSQL | `postgres`, `5432` |
| `JWT_SECRET`, `JWT_TTL_SECONDS` | signature HS256 et durée de l’access token | 3600 s |
| `OTP_HASH_SECRET` | clé HMAC des OTP stockés | aucune valeur de production fournie |
| `OTP_TEST_CODE` | OTP déterministe hors production | `123456` |
| `OTP_TTL_SECONDS`, `OTP_MAX_ATTEMPTS` | validité et essais OTP | 300 s, 5 |
| `OTP_REQUEST_LIMIT_EMAIL`, `OTP_REQUEST_LIMIT_IP`, `OTP_RATE_WINDOW_SECONDS` | quotas PostgreSQL | 5, 20, 3600 s |
| `MAX_OBJECT_SIZE_BYTES` | taille compressée maximale mesurée côté serveur | 1 MiB |
| `SYNC_MAX_LIMIT` | limite maximale d’une page de changements | 500 |
| `API_TEST_BASE_URL` | cible HTTP des tests fonctionnels permanents | `http://nginx-test` dans la stack de test |
| `E2E_BASE_URL` | cible HTTP des tests de concurrence réelle | `http://nginx-test` dans la stack de test |

`JWT_SECRET` et `OTP_HASH_SECRET` doivent faire au moins 32 caractères et les valeurs de développement sont refusées en production. En production, définir des secrets aléatoires et `OTP_TEST_CODE=` ; toute valeur de test provoque une erreur de configuration.

## Migrations et fixtures de démonstration

`bin/migrate` crée `schema_migrations`, prend un verrou PostgreSQL et exécute chaque fichier `migrations/*.sql` une seule fois. Il est idempotent.

`bin/fixtures` est refusé en production. Il nettoie les tables fonctionnelles puis recrée les trois comptes déterministes et les profils affichés en sortie. `demo@cstv.local` possède Nico et Enfant, six blobs réalistes et des révisions cohérentes.

Les fixtures ne sont jamais un prérequis de PHPUnit et ne sont jamais chargées automatiquement par la commande de test.

## Collection Postman

Une collection prête à importer, son environnement local et deux payloads gzip réels sont disponibles dans `postman/`. Elle utilise les comptes de démonstration, récupère automatiquement le profil Nico, puis teste l'authentification, les profils, les objets binaires, ETag/If-Match et la synchronisation.

Voir `postman/README.md` pour la préparation et le réglage du *working directory* Postman.

Le workflow complet entre l'application, l'API et PostgreSQL est illustré dans [`docs/api/app-api-sequence.md`](../docs/api/app-api-sequence.md) avec les parcours d'authentification, de synchronisation, de conflit ETag et de désactivation immédiate.

## Base et architecture des tests

Docker Compose fournit une stack de test indépendante de la stack de développement :

- `postgres-test`, PostgreSQL réel avec la base éphémère `cstv_test` ;
- `php-test`, PHP-FPM en `APP_ENV=test` ;
- `nginx-test`, point d'entrée HTTP interne des tests fonctionnels.

Les tests sont séparés en trois suites :

- `tests/Unit` : logique pure, sans HTTP ni infrastructure ;
- `tests/Integration` : repositories, transactions, migrations, contraintes et concurrence sur PostgreSQL réel ;
- `tests/Functional` : appels réels `PHPUnit → HTTP → Nginx → PHP-FPM → Slim → PostgreSQL` via le client commun `tests/Functional/Support/ApiClient.php`. La couche HTTP n'est pas mockée.

Chaque test Integration ou Functional remet lui-même les tables fonctionnelles à zéro et crée uniquement ses propres données. `TestDatabase::reset()` refuse toute destruction sauf si `APP_ENV=test` **et** si PostgreSQL confirme que la base courante est exactement `cstv_test`. Une configuration visant `cstv`, une autre base suffixée `_test`, ou un autre environnement échoue avant tout `TRUNCATE`.

Depuis l'hôte, la commande globale prépare et migre automatiquement la base dédiée, puis lance les trois suites :

```bash
make test
make test-unit
make test-integration
make test-functional
```

Dans le conteneur `php-test`, la commande équivalente est :

```bash
docker compose exec -T php-test composer test
```

Un environnement PHP 8.5 externe peut aussi lancer `composer test` s'il fournit explicitement `APP_ENV=test`, `POSTGRES_DB=cstv_test`, les autres paramètres PostgreSQL et `API_TEST_BASE_URL` vers la stack de test. Le script Composer vérifie la cible, applique les migrations idempotentes et refuse de continuer sur une base non autorisée. Il ne charge jamais les fixtures de démonstration.

Les scénarios de concurrence réelle passent par `curl_multi` vers `E2E_BASE_URL` et complètent les tests fonctionnels séquentiels de stale ETag.

## Flow OTP

1. `POST /v1/auth/otp/request` normalise l’email, purge ses codes plus vieux que la fenêtre de quota, applique les quotas par email et IP, invalide l’ancien code actif, stocke uniquement son HMAC et renvoie toujours `202 {"status":"accepted"}`. `created_at` utilise `clock_timestamp()` et non `NOW()` : deux demandes concurrentes sur le même email s’ordonnent par leur INSERT réel, pas par le début de leur transaction.
2. En `dev`, l’expéditeur remplaçable écrit le code dans stdout ; en `test`, `OTP_TEST_CODE` rend le scénario déterministe. Le code n’apparaît jamais dans une réponse HTTP.
3. `POST /v1/auth/otp/verify` verrouille le dernier OTP, contrôle les cinq minutes, les essais et l’usage unique. Une réussite crée atomiquement le compte et `Profil 1` si nécessaire, puis renvoie un JWT contenant `sub`, `iat` et `exp`.
4. Chaque route protégée valide le JWT puis relit l’état courant du compte en base.

## Flow de synchronisation

Le client compresse son document avec gzip et envoie les octets avec `Content-Type: application/vnd.cstv.blob+gzip`, sans `Content-Encoding`. Le serveur mesure les octets, calcule SHA-256, écrit l’objet et une ligne append-only `sync_changes` dans la même transaction. `GET /v1/sync/changes?cursor=0&limit=100` liste uniquement les métadonnées du compte ; le client télécharge ensuite les objets voulus.

Les PUT/DELETE concurrents d’une même clé sont sérialisés par un verrou consultatif PostgreSQL. Chaque mutation garde aussi un verrou partagé sur son profil jusqu’au commit, afin qu’une suppression de profil attende les objets en cours. Sans `If-Match`, la dernière transaction validée gagne. Avec `If-Match`, l’ETag doit correspondre ou l’API renvoie `412 ETAG_MISMATCH`.

Toute écriture dans `sync_changes` prend d’abord un verrou consultatif propre au compte, gardé jusqu’au commit. `revision` est un `BIGSERIAL` attribué à l’INSERT et non au COMMIT : sans cette sérialisation, deux écritures concurrentes peuvent valider dans l’ordre inverse de leurs révisions, et un client qui lit le journal entre les deux avance son curseur au-delà d’une révision encore invisible qu’il ne reverra jamais. Les verrous sont toujours pris dans le même ordre — journal du compte, puis objet — donc aucun interblocage n’est possible. Le prix est que les écritures d’un même compte sont sérialisées, ce qui correspond au trafic réel : quelques installations d’un seul utilisateur.

DELETE sans `If-Match` est idempotent et ne journalise rien si l’objet était déjà absent. Avec `If-Match`, la précondition est évaluée avant tout : un objet absent ou porteur d’un autre ETag renvoie `412`, afin qu’un client en retard ne croie pas avoir supprimé la version qu’il détenait encore. Réécrire un contenu identique réussit, renvoie le même ETag et journalise malgré tout une révision UPSERT.

La suppression d’un profil est interdite s’il est le dernier ; sinon, elle crée dans la même transaction un tombstone DELETE pour chacun de ses objets, puis la cascade supprime le profil et ses blobs. Les tombstones conservent le `profileId`, car `sync_changes.profile_id` n’a volontairement pas de clé étrangère vers `profiles`. Le client apprend la disparition du profil lui-même via `/v1/me` ou `/v1/profiles`, pas via le journal.

## Exemple unique : ajout puis suppression d’un favori

Le JSON est produit localement, compressé, puis envoyé tel quel. Remplacer `$TOKEN` et `$PROFILE_ID` par les valeurs obtenues via l’OTP et `/v1/me`.

```bash
printf '%s' '{"schemaVersion":1,"id":12345,"type":"movie","name":"Interstellar","cover":"https://images.example.test/interstellar.jpg","categoryId":"42","addedAt":1786441680000}' > /tmp/cstv-favorite.json
gzip -c /tmp/cstv-favorite.json > /tmp/cstv-favorite.json.gz
curl -i -X PUT "http://localhost:18080/v1/profiles/$PROFILE_ID/objects/favorites/movie-12345" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/vnd.cstv.blob+gzip' \
  -H 'X-Schema-Version: 1' \
  --data-binary @/tmp/cstv-favorite.json.gz

curl -i -X DELETE "http://localhost:18080/v1/profiles/$PROFILE_ID/objects/favorites/movie-12345" \
  -H "Authorization: Bearer $TOKEN"
```

## ETags et erreurs

La valeur stockée est le SHA-256 hexadécimal du payload compressé. Les réponses HTTP utilisent sa forme d’entity-tag forte entre guillemets ; les listings JSON utilisent la valeur hexadécimale nue. `If-Match` accepte un ETag courant entre guillemets, une liste séparée par des virgules ou `*` pour une ressource existante.

Toutes les erreurs applicatives sont JSON :

```json
{
  "error": {
    "code": "ACCOUNT_EXPIRED",
    "message": "Account activation has expired."
  }
}
```

Aucune stack trace n’est exposée. Le contrat complet et les statuts effectivement implémentés sont décrits dans `openapi.yaml` (OpenAPI 3.1).
