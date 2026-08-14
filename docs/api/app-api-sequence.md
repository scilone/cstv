# Workflow application CSTV / API

L’application Android conserve la donnée métier dans Room. Le backend stocke les snapshots gzip opaques par `(profil, namespace)` et leur ETag. Une exception dédiée stocke au plus une sauvegarde d'identifiants IPTV chiffrée par compte CSTV ; elle ne partage ni le modèle par profil ni le format gzip opaque. L'API ne connaît ni appareil, ni installation ni catalogue IPTV.

## Authentification et chargement initial

```mermaid
sequenceDiagram
    autonumber
    actor U as Utilisateur
    participant App as App Android / TV
    participant API as Nginx + PHP-FPM + Slim
    participant DB as PostgreSQL
    participant OTP as Service OTP

    U->>App: Saisit email
    App->>API: POST /v1/auth/otp/request
    API->>DB: Stocke seulement le HMAC du code
    API->>OTP: Envoie le code
    API-->>App: 202 accepted
    OTP-->>U: Code à 6 chiffres

    U->>App: Saisit le code
    App->>API: POST /v1/auth/otp/verify
    API->>DB: Verrouille et valide l'OTP
    alt Premier accès
        API->>DB: Transaction compte + Profil 1
        Note over API,DB: enabled=true<br/>active_until=NOW()+1 an<br/>avatarId=0
    end
    API-->>App: JWT accessToken

    App->>API: GET /v1/me avec Bearer JWT
    API->>DB: Relit compte et profils
    API-->>App: Compte + profils

    App->>API: GET /v1/profiles/{profileId}/objects
    API->>DB: Métadonnées, sans BYTEA
    API-->>App: namespace + ETag + taille + version
    loop Chaque ETag absent ou différent localement
        App->>API: GET /objects/{namespace}
        API->>DB: Vérifie ownership et lit BYTEA
        API-->>App: Octets gzip exacts + ETag
        App->>App: Décompresse, fusionne et persiste dans Room
    end
```

Le même chargement des métadonnées est rejoué au démarrage, à la reprise de l’application et après un retour réseau. Il n’existe ni cursor ni journal `sync_changes`.

## Sauvegarde et restauration des identifiants IPTV

```mermaid
sequenceDiagram
    autonumber
    participant App as App Android / TV
    participant X as Panel Xtream
    participant API as API CSTV
    participant DB as PostgreSQL

    App->>X: Validation des identifiants saisis
    X-->>App: Connexion acceptée
    App->>App: Sauvegarde locale chiffrée, toujours
    opt Consentement CSTV coché
        App->>API: PUT /v1/account/iptv-credentials
        API->>DB: Chiffre XChaCha20-Poly1305 et UPSERT par compte
        API-->>App: 204 + ETag
    end

    Note over App: Nouvel appareil sans copie locale
    App->>API: GET /v1/account/iptv-credentials
    API->>DB: Déchiffre l'enveloppe liée au compte
    API-->>App: Identifiants + ETag, no-store
    App->>X: Vérification silencieuse
    X-->>App: Connexion acceptée ou refusée
    alt Refus explicite de la copie restaurée
        App->>API: DELETE avec If-Match ETag
        API-->>App: 204 ou 412 si une copie plus récente existe
    end
```

Une panne CSTV n'empêche jamais la connexion IPTV : l'intention d'écriture ou de suppression est persistée dans le stockage chiffré de l'application puis rejouée par un worker réseau. Une suppression en attente est prioritaire sur toute restauration ou écriture locale.

## Modification locale et envoi asynchrone

```mermaid
sequenceDiagram
    autonumber
    participant UI as UI / Player
    participant App as Couche sync app
    participant Room as Room locale
    participant API as API CSTV
    participant DB as PostgreSQL

    UI->>Room: Favori / rating / playback modifié
    Room-->>App: État local mis à jour immédiatement
    App->>App: Marque le namespace pending
    alt Namespace playback
        App->>App: Démarrage effectif / pause / fin naturelle
        Note over App: Aucun PUT à chaque tick, au passage en arrière-plan ou à la simple sortie du lecteur
    else Autre namespace
        App->>App: Envoi asynchrone dès que possible
    end
    App->>App: Sérialise tout le namespace puis gzip
    App->>API: PUT /objects/{namespace}<br/>X-Schema-Version + If-Match courant
    API->>DB: Relit compte, vérifie profil, verrouille namespace
    API->>DB: Vérifie ETag puis UPSERT snapshot
    Note over API,DB: Aucun gzdecode, parsing ou recompression
    DB-->>API: COMMIT
    API-->>App: 204 + nouvel ETag SHA-256
    App->>Room: Mémorise snapshot de base + ETag
```

Seul un namespace absent peut être créé sans `If-Match`. Pour un snapshot existant, l’absence du header renvoie `428 PRECONDITION_REQUIRED`.

## Fusion applicative après conflit ETag

```mermaid
sequenceDiagram
    autonumber
    participant A as Installation A
    participant API as API CSTV
    participant DB as PostgreSQL
    participant B as Installation B

    A->>API: PUT favorites If-Match ETag-1
    API->>DB: Snapshot ETag-1 -> ETag-2
    API-->>A: 204 ETag-2

    B->>API: PUT favorites If-Match ETag-1
    API->>DB: ETag courant = ETag-2
    API-->>B: 412 ETAG_MISMATCH
    B->>API: GET /objects/favorites
    API-->>B: Snapshot serveur + ETag-2
    B->>B: Fusion 3 voies<br/>base synchronisée + serveur + changements locaux
    B->>API: PUT snapshot fusionné If-Match ETag-2
    API->>DB: Snapshot ETag-2 -> ETag-3
    API-->>B: 204 ETag-3
```

La politique de fusion appartient à l’application, car elle seule comprend les données. Par exemple, les favoris peuvent fusionner des ajouts/retraits par clé métier et le playback peut retenir la progression la plus récente selon le contrat local. Le serveur reste volontairement aveugle au JSON compressé.

## Suppression d’un namespace

```mermaid
sequenceDiagram
    autonumber
    participant App as App Android / TV
    participant API as API CSTV
    participant DB as PostgreSQL

    App->>API: DELETE /objects/{namespace}<br/>If-Match courant
    API->>DB: Verrouille et vérifie l'ETag
    API->>DB: DELETE snapshot
    API-->>App: 204

    App->>API: DELETE du namespace déjà absent
    API->>DB: Snapshot absent
    API-->>App: 204 idempotent

    App->>API: DELETE absent avec ancien If-Match
    API-->>App: 412 ETAG_MISMATCH
```

## Désactivation ou expiration immédiate

```mermaid
sequenceDiagram
    autonumber
    actor Admin as Exploitant PostgreSQL
    participant DB as PostgreSQL
    participant App as App avec JWT existant
    participant API as API CSTV

    Admin->>DB: UPDATE accounts SET enabled=false
    App->>API: GET /v1/me avec le même JWT
    API->>DB: Relit le compte courant
    API-->>App: 403 ACCOUNT_DISABLED

    Admin->>DB: enabled=true, active_until dans le passé
    App->>API: Nouvelle requête, même JWT
    API->>DB: Relit le compte courant
    API-->>App: 403 ACCOUNT_EXPIRED
```

## Responsabilités

| Application Android / TV | API CSTV |
|---|---|
| Conserver Room comme état métier local | Stocker un blob opaque par profil et namespace |
| Garder le dernier snapshot synchronisé et son ETag | Calculer l’ETag SHA-256 sur les octets reçus |
| Sérialiser, compresser et décompresser | Restituer exactement les octets sans les inspecter |
| Fusionner les conflits 412 puis réessayer | Imposer `If-Match` sous verrou transactionnel |
| Envoyer playback uniquement au démarrage effectif, à la pause et à la fin naturelle | Borner chaque snapshot par `MAX_OBJECT_SIZE_BYTES` |
| Resynchroniser au démarrage/reprise/reconnexion | Relire le compte PostgreSQL à chaque requête |
| Continuer à appeler Xtream/TMDB/YouTube | Ne jamais stocker catalogue ou fournisseur IPTV ; ne stocker les credentials qu'en enveloppe chiffrée par compte |

Le contrat HTTP détaillé reste défini dans [`openapi.yaml`](openapi.yaml).
