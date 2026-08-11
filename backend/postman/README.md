# Collection Postman CSTV

La collection couvre le parcours local complet avec les fixtures : santé, OTP/JWT, compte, CRUD profils, blobs gzip opaques, ETag/If-Match, suppression idempotente, journal de synchronisation et comptes expiré/désactivé.

## Préparation

Depuis `backend/` :

```bash
docker compose up -d --build
docker compose exec php bin/migrate
docker compose exec php bin/fixtures
```

Importer dans Postman :

1. `CSTV-Backend.postman_collection.json` ;
2. `CSTV-local.postman_environment.json` ;
3. sélectionner l'environnement **CSTV Backend - Local fixtures** ;
4. dans les réglages Postman des fichiers, choisir le dossier `backend/` comme *working directory* et autoriser sa lecture ;
5. lancer la collection dans son ordre naturel.

Les requêtes d'objets lisent réellement :

- `postman/fixtures/favorite.json.gz` ;
- `postman/fixtures/favorite-updated.json.gz`.

Les sources JSON lisibles sont conservées à côté. Les archives sont générées avec `gzip -n`, donc déterministes.

Après modification des sources JSON, les régénérer avec :

```bash
make postman-fixtures
```

Le flow d'authentification demande d'abord un OTP puis utilise `123456`, valeur de développement fournie par Docker Compose. Ce code est volontairement refusé par la configuration de production. Après `/v1/me`, la collection sélectionne automatiquement le profil fixture **Nico**.

Le profil créé pendant le scénario est supprimé en fin de dossier. L'objet Postman utilise la clé dédiée `movie-postman-12345`, puis est supprimé ; les six objets de démonstration restent donc intacts. Le journal `sync_changes` conserve normalement les révisions produites par le scénario. Relancer `bin/fixtures` permet de restaurer exactement l'état initial de démonstration.
