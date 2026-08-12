# CSTV Backend POC

API HTTP légère pour l’authentification CSTV par email/OTP et la synchronisation de snapshots gzip opaques par profil et namespace. Elle ne stocke ni fournisseur, ni identifiants, ni catalogue, ni installation ou device IPTV.

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
| `JWT_SECRET` | signature HS256 ; le JWT expire à `active_until` du compte | aucune valeur de production fournie |
| `OTP_HASH_SECRET` | clé HMAC des OTP stockés | aucune valeur de production fournie |
| `OTP_TEST_CODE` | OTP déterministe hors production | `123456` |
| `OTP_FROM_EMAIL`, `OTP_FROM_NAME` | expéditeur OTP requis en production | aucun, `CSTV` |
| `OTP_TTL_SECONDS`, `OTP_MAX_ATTEMPTS` | validité et essais OTP | 300 s, 5 |
| `OTP_REQUEST_LIMIT_EMAIL`, `OTP_REQUEST_LIMIT_IP`, `OTP_RATE_WINDOW_SECONDS` | quotas PostgreSQL | 5, 20, 3600 s |
| `MAX_OBJECT_SIZE_BYTES` | taille compressée maximale mesurée côté serveur | 1 MiB |
| `API_TEST_BASE_URL` | cible HTTP des tests fonctionnels permanents | `http://nginx-test` dans la stack de test |
| `E2E_BASE_URL` | cible HTTP des tests de concurrence réelle | `http://nginx-test` dans la stack de test |

`JWT_SECRET` et `OTP_HASH_SECRET` doivent faire au moins 32 caractères et les valeurs de développement sont refusées en production. En production, définir des secrets aléatoires, `OTP_TEST_CODE=` et une adresse valide `OTP_FROM_EMAIL` ; toute valeur de test ou expéditeur absent provoque une erreur de configuration.

## Migrations et fixtures de démonstration

`bin/migrate` crée `schema_migrations`, prend un verrou PostgreSQL et exécute chaque fichier `migrations/*.sql` une seule fois. Il est idempotent.

`bin/fixtures` est refusé en production. Il nettoie les tables fonctionnelles puis recrée les trois comptes déterministes et les profils affichés en sortie. `demo@cstv.local` possède Nico et Enfant, avec quatre snapshots de namespace réalistes pour Nico.

La migration `002_namespace_snapshots.sql` supprime les anciens blobs par clé et le journal `sync_changes`. Cette perte ponctuelle est volontaire pour le POC : le serveur ne peut pas agréger des blobs opaques sans les décompresser et les interpréter. Après migration, recharger les fixtures en développement ; une application conserve sa source locale et réenvoie ses snapshots.

Les fixtures ne sont jamais un prérequis de PHPUnit et ne sont jamais chargées automatiquement par la commande de test.

## Collection Postman

Une collection prête à importer, son environnement local et deux payloads gzip réels sont disponibles dans `postman/`. Elle utilise les comptes de démonstration, récupère automatiquement le profil Nico, puis teste l'authentification, les profils, les snapshots binaires et ETag/If-Match.

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
2. En `dev`, l’expéditeur remplaçable écrit le code dans stdout ; en `test`, `OTP_TEST_CODE` rend le scénario déterministe. En production, l’expéditeur utilise `mail()` et le relais local de l’hébergeur avec `OTP_FROM_EMAIL`. Le code n’apparaît jamais dans une réponse HTTP ni dans les logs de production.
3. `POST /v1/auth/otp/verify` verrouille le dernier OTP, contrôle les cinq minutes, les essais et l’usage unique. Une réussite crée atomiquement le compte et `Profil 1` si nécessaire, puis renvoie un JWT contenant `sub`, `iat` et un `exp` égal à `active_until`. Un compte désactivé ou expiré ne reçoit pas de nouveau JWT.
4. Chaque route protégée valide le JWT puis relit l’état courant du compte en base.

## Flow de synchronisation

`profile_objects` contient au maximum une ligne par couple `(profile_id, namespace)`. Chaque ligne est un snapshot gzip opaque de toutes les données applicatives du namespace ; les clés métier restent à l’intérieur du document géré par l’application. Il n’existe plus de table `sync_changes`, de cursor ni de journal append-only.

Au démarrage, à la reprise de l’application et après un retour réseau, le client appelle `GET /v1/profiles/{profileId}/objects`, compare les ETags avec son état local, puis télécharge chaque snapshot nécessaire avec `GET /v1/profiles/{profileId}/objects/{namespace}`. Le serveur ne décompresse et ne parse jamais ces octets.

La création d’un namespace absent se fait sans `If-Match`. Toute réécriture ou suppression d’un snapshot existant exige son ETag courant : l’absence du header renvoie `428 PRECONDITION_REQUIRED`, un ETag périmé renvoie `412 ETAG_MISMATCH`. Dans ce dernier cas, l’application télécharge le snapshot serveur, effectue une fusion applicative avec sa base synchronisée et ses changements locaux, puis réessaie avec le nouvel ETag.

Les mutations concurrentes du même `(profil, namespace)` sont sérialisées par un verrou consultatif PostgreSQL tenu jusqu’au commit. Deux clients partis du même ETag ne peuvent donc pas écraser silencieusement leurs changements. Une suppression déjà effectuée reste idempotente sans `If-Match`; fournir un ancien ETag pour une ressource absente renvoie 412.

Pour `playback`, l’application doit mettre à jour Room immédiatement, mais envoyer le snapshot uniquement au démarrage effectif du média, à la pause et à la fin naturelle de lecture. Il ne faut pas pousser à chaque tick, lors du passage en arrière-plan, ni à la simple sortie ou destruction du lecteur. Un envoi périodique pourra être évalué dans un second temps.

La suppression d’un profil est interdite s’il est le dernier. Sinon PostgreSQL supprime ses snapshots par cascade ; les autres installations constatent la liste de profils et les snapshots actuels lors de leur prochaine synchronisation complète.

## ETags et erreurs

La valeur stockée est le SHA-256 hexadécimal du payload compressé. Les réponses HTTP utilisent sa forme d’entity-tag forte entre guillemets ; les listings JSON utilisent la valeur hexadécimale nue. `If-Match` accepte un ETag courant entre guillemets, une liste séparée par des virgules ou `*` pour une ressource existante. Il est obligatoire pour modifier ou supprimer un snapshot existant.

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
