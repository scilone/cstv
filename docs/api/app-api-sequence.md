# Workflow application CSTV / API

Ces diagrammes décrivent le contrat actuellement implémenté. L'application Android conserve ses données locales dans Room et échange avec le backend uniquement des profils et des blobs gzip opaques. Le backend ne connaît ni appareil, ni installation, ni source ou credential IPTV.

## Vue d'ensemble

```mermaid
sequenceDiagram
    autonumber
    actor U as Utilisateur
    participant App as App Android / TV
    participant API as Nginx + PHP-FPM + Slim
    participant DB as PostgreSQL
    participant OTP as Service d'envoi OTP

    U->>App: Saisit son email
    App->>API: POST /v1/auth/otp/request
    API->>DB: Stocke uniquement le HMAC du code
    API->>OTP: Demande l'envoi du code
    API-->>App: 202 accepted
    OTP-->>U: Code à 6 chiffres

    U->>App: Saisit le code
    App->>API: POST /v1/auth/otp/verify
    API->>DB: Verrouille et valide l'OTP
    alt Premier accès
        API->>DB: Transaction compte + Profil 1
        Note over API,DB: enabled=true<br/>active_until=NOW()+1 an<br/>avatarId=0
    else Compte existant
        API->>DB: Recharge le compte existant
    end
    API-->>App: JWT accessToken

    App->>API: GET /v1/me avec Bearer JWT
    API->>API: Vérifie signature, exp et sub
    API->>DB: Relit enabled et active_until
    DB-->>API: Compte actuel + profils
    API-->>App: Compte et profils

    App->>API: GET /v1/sync/changes?cursor=X
    API->>DB: Révisions du compte supérieures à X
    DB-->>API: Métadonnées triées par revision
    API-->>App: changes + nextCursor + hasMore
    loop Chaque objet UPSERT utile
        App->>API: GET /profiles/{profileId}/objects/{namespace}/{key}
        API->>DB: Vérifie ownership et lit BYTEA
        API-->>App: Octets gzip exacts + ETag
        App->>App: Persiste le document dans Room
    end
    App->>App: Persiste nextCursor localement
```

Points importants :

- `/otp/request` ne crée jamais le compte et répond de manière générique pour limiter l'énumération d'adresses ;
- la création du compte et de `Profil 1` est atomique après un OTP valide ;
- le JWT ne contient que l'identité technique du compte (`sub`) et sa durée de validité ;
- chaque endpoint authentifié relit le compte dans PostgreSQL avant d'autoriser l'accès ;
- le change feed ne contient jamais les blobs : l'application télécharge séparément les objets nécessaires.

## Écriture locale, ETag et conflit optimiste

```mermaid
sequenceDiagram
    autonumber
    participant App as App Android / TV
    participant Room as Room locale
    participant API as API CSTV
    participant DB as PostgreSQL

    App->>Room: Favori / playback / rating modifié
    Room-->>App: Document métier local
    App->>App: Sérialise puis compresse en gzip
    App->>API: PUT objet + X-Schema-Version + If-Match
    Note over App,API: Content-Type application/vnd.cstv.blob+gzip

    API->>DB: Relit le compte et vérifie le profil
    API->>DB: Verrou journal du compte puis objet
    API->>DB: Lit l'ETag courant
    alt If-Match absent ou conforme
        API->>DB: Transaction UPSERT profile_objects
        API->>DB: INSERT sync_changes operation UPSERT
        Note over API,DB: Le payload reste opaque<br/>aucun gzdecode ni parsing JSON
        DB-->>API: COMMIT
        API-->>App: 204 + nouvel ETag SHA-256
        App->>Room: Mémorise le nouvel ETag
    else If-Match périmé
        API-->>App: 412 ETAG_MISMATCH
        App->>API: GET objet courant
        API-->>App: Version serveur + ETag courant
        App->>App: Résout le conflit puis réessaie
    end
```

Sans `If-Match`, la dernière écriture validée gagne. Avec `If-Match`, deux clients partis du même ETag ne peuvent pas écraser silencieusement leurs changements : le premier réussit et le second reçoit `412 ETAG_MISMATCH`.

## Suppression et propagation multi-installation

```mermaid
sequenceDiagram
    autonumber
    participant A as Installation A
    participant API as API CSTV
    participant DB as PostgreSQL
    participant B as Installation B

    A->>API: DELETE objet avec If-Match courant
    API->>DB: Vérifie compte, profil et ETag
    API->>DB: Transaction DELETE objet
    API->>DB: INSERT sync_changes operation DELETE
    DB-->>API: COMMIT
    API-->>A: 204

    B->>API: GET /v1/sync/changes?cursor=ancienCursor
    API->>DB: Lit les révisions suivantes du compte
    API-->>B: DELETE namespace + key, sans payload
    B->>B: Supprime la donnée locale Room
    B->>B: Persiste nextCursor

    A->>API: DELETE du même objet sans If-Match
    API->>DB: Objet déjà absent
    API-->>A: 204 idempotent
    Note over API,DB: Aucune nouvelle révision inutile
```

Pour un UPSERT effectué par l'installation A, l'installation B reçoit de la même façon la métadonnée dans `/v1/sync/changes`, puis télécharge les octets avec `GET /v1/profiles/{profileId}/objects/{namespace}/{key}`.

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
    API->>API: JWT valide, sub extrait
    API->>DB: SELECT compte courant
    DB-->>API: enabled=false
    API-->>App: 403 ACCOUNT_DISABLED

    Admin->>DB: enabled=true et active_until dans le passé
    App->>API: Nouvelle requête avec le même JWT
    API->>DB: SELECT compte courant
    DB-->>API: compte expiré
    API-->>App: 403 ACCOUNT_EXPIRED
```

Il n'est donc pas nécessaire de révoquer ou régénérer les JWT pour appliquer une intervention manuelle : PostgreSQL reste la source de vérité à chaque requête.

## Responsabilités

| Application Android / TV | API CSTV |
|---|---|
| Afficher le flow email + OTP | Générer, hacher, limiter et valider les OTP |
| Stocker le JWT de manière sûre | Signer le JWT et en valider la signature/expiration |
| Conserver le cursor de chaque synchronisation | Servir un journal ordonné et isolé par compte |
| Sérialiser et compresser les documents | Stocker et restituer les octets sans les inspecter |
| Conserver les ETags et gérer les réponses 412 | Appliquer `If-Match` sous verrou transactionnel |
| Appliquer UPSERT/DELETE dans Room | Garantir l'atomicité objet + événement de synchronisation |
| Continuer à appeler directement Xtream/TMDB/YouTube | Ne jamais stocker ni relayer les données ou credentials IPTV |

Le contrat HTTP détaillé reste défini dans [`openapi.yaml`](openapi.yaml).
