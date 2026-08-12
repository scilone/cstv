# F33 - Compte CSTV obligatoire et profils cloud

## Informations générales

Status:
VALIDATION

Created:
2026-08-11

---

# 1. Description

Intégrer dans l'application Android / Android TV l'authentification obligatoire
au backend CSTV par email et code OTP, la validation de l'accès au service et
la gestion des profils CSTV distants.

Le compte CSTV devient le premier niveau d'accès à l'application. Un utilisateur
sans compte peut en créer un implicitement en validant son premier OTP ; son
compte est alors activé pendant un an. L'authentification Xtream existante reste
nécessaire pour accéder au fournisseur IPTV, mais elle ne peut commencer qu'une
fois l'accès CSTV autorisé.

Ce ticket couvre également l'initialisation et la fusion des profils locaux avec
les profils du compte CSTV. La synchronisation du contenu des namespaces est
traitée séparément par F34.

## Objectifs

- rendre l'authentification CSTV obligatoire avant toute utilisation de
  l'application ;
- créer automatiquement le compte et son premier profil après le premier OTP
  valide ;
- aligner l'expiration du JWT sur la date de validité du compte ;
- bloquer l'application lorsqu'un compte est désactivé, expiré ou lorsque son
  JWT est expiré ;
- conserver un usage hors ligne tant que le JWT local n'est pas expiré ;
- préserver et fusionner les profils locaux lors de la première association au
  compte CSTV ;
- empêcher tout mélange de données lors d'un changement de compte CSTV.

---

# 2. Contexte

L'application authentifie actuellement uniquement le compte Xtream et gère des
profils locaux identifiés dans Room. Ces profils et leurs données ne sont pas
partagés entre installations.

Le backend CSTV existe désormais dans `backend/`. Il fournit :

- `POST /v1/auth/otp/request` ;
- `POST /v1/auth/otp/verify` ;
- `GET /v1/me` ;
- le CRUD des profils ;
- une vérification de `enabled` et `active_until` depuis PostgreSQL à chaque
  requête authentifiée.

Le backend ne connaît aucun mot de passe, credential Xtream, fournisseur IPTV,
device ou installation. Cette séparation doit rester visible pour
l'utilisateur : le compte CSTV autorise l'application et la synchronisation,
tandis que le compte Xtream donne accès au contenu IPTV.

Les décisions produit actées sont les suivantes :

- l'accès CSTV est obligatoire ;
- un nouveau compte reçoit un an de validité ;
- le JWT expire à `active_until` et nécessite ensuite un nouvel OTP ;
- l'application reste utilisable sans backend tant que le JWT n'est pas expiré ;
- un compte désactivé ou expiré rend l'application inutilisable dès que cet
  état est connu ;
- les profils et données locaux sont préservés lors de l'initialisation ;
- un changement de compte CSTV purge les profils et données locales associés au
  compte précédent.

## Hypothèses

- une seule session CSTV est active à la fois sur une installation ;
- les identifiants Xtream restent indépendants du compte CSTV ;
- les téléchargements et le catalogue partagés, qui ne sont pas des données de
  profil synchronisées, conservent leur comportement actuel ;
- l'application peut déterminer localement l'expiration du JWT sans considérer
  son contenu comme une preuve de `enabled` ou de la date serveur courante ;
- F34 prend en charge l'envoi et la fusion du contenu associé aux profils.

## Questions ouvertes

Aucune question fonctionnelle bloquante à l'issue de l'étape 2.

---

# 3. Spécification fonctionnelle

## User stories

- En tant que nouvel utilisateur, je peux saisir mon email et un OTP pour créer
  automatiquement un compte CSTV valide un an, sans mot de passe.
- En tant qu'utilisateur existant, je peux retrouver mon compte CSTV et ses
  profils en validant un OTP reçu par email.
- En tant qu'utilisateur authentifié, je ne dois pas ressaisir un OTP à chaque
  lancement tant que mon JWT reste valide.
- En tant qu'utilisateur temporairement hors ligne, je peux continuer à utiliser
  mes données locales tant que mon JWT n'a pas expiré.
- En tant qu'utilisateur dont le compte est désactivé ou expiré, je ne peux plus
  utiliser l'application.
- En tant qu'utilisateur possédant déjà des profils locaux, je les conserve lors
  de ma première connexion CSTV et ils sont ajoutés ou associés aux profils cloud.
- En tant qu'utilisateur changeant de compte CSTV, je ne dois jamais voir les
  profils ou données privées du compte précédent.

## Périmètre

### Inclus

- écran email puis écran de saisie OTP sur mobile et Android TV ;
- demande et validation OTP ;
- stockage sûr de la session CSTV ;
- création automatique du compte et de `Profil 1` côté backend ;
- durée initiale du compte d'un an ;
- JWT expirant à la date `activeUntil` du compte ;
- gate CSTV précédant le flux Xtream existant ;
- chargement de `/v1/me` et des profils distants ;
- sélection, création, renommage, avatar et suppression des profils via le
  backend lorsque le réseau est disponible ;
- fusion initiale des profils locaux et cloud ;
- purge des profils et données associées lors d'un changement de compte CSTV ;
- états bloquants compte désactivé, expiré ou session expirée ;
- repli hors ligne avec JWT non expiré.

### Exclus

- mot de passe CSTV ou récupération de mot de passe ;
- gestion de device ou d'installation côté backend ;
- stockage des credentials, catalogues ou sources IPTV sur le backend ;
- endpoint ou interface d'administration ;
- renouvellement silencieux du JWT ;
- synchronisation du contenu des namespaces, couverte par F34.

## Parcours 1 — première création de compte CSTV

1. Au premier lancement sans session CSTV, l'application affiche l'écran de
   saisie d'email avant tout écran Xtream, profil ou contenu.
2. L'utilisateur saisit un email valide et demande un code.
3. L'application confirme que la demande est prise en compte sans révéler si le
   compte existait déjà.
4. L'utilisateur saisit le code à six chiffres reçu.
5. Après validation, si le compte n'existait pas, le backend crée atomiquement :
   - le compte avec `enabled = true` ;
   - `activeUntil = date courante + 1 an` ;
   - le profil `Profil 1`, avatar `0`.
6. Le backend renvoie un JWT dont l'expiration correspond exactement à
   `activeUntil`.
7. L'application charge `/v1/me`, initialise ou fusionne les profils, puis F34
   initialise leurs données cloud.
8. Une fois le gate CSTV résolu, l'application poursuit le flow Xtream existant.

Une simple demande OTP ne crée jamais le compte. Seule une validation OTP
réussie autorise cette création.

## Parcours 2 — reconnexion à un compte existant

1. Sans JWT local valide, l'utilisateur suit le même flow email + OTP.
2. Après validation, aucun nouveau compte n'est créé.
3. Le JWT expire à la date de validité courante du compte.
4. L'application récupère le compte et les profils distants avant de poursuivre
   vers l'authentification Xtream ou l'accueil.
5. Si l'administrateur a prolongé le compte après l'émission d'un ancien JWT,
   l'expiration de cet ancien token nécessite tout de même un nouvel OTP ; le
   nouveau JWT prend alors la nouvelle date `activeUntil`.

## Parcours 3 — démarrage avec une session locale

### Backend joignable

1. L'application vérifie localement que le JWT stocké n'a pas atteint son
   expiration connue ; la signature reste validée exclusivement par le backend.
2. Elle appelle `/v1/me` avec ce JWT.
3. Le backend valide sa signature puis relit `enabled` et `active_until` dans
   PostgreSQL.
4. Si le compte est valide, les profils sont réconciliés et l'application
   continue son démarrage.

### Backend temporairement injoignable

1. Si le JWT local est encore valide, l'application utilise les profils et
   données Room déjà associés à ce compte.
2. Elle affiche un état hors ligne non bloquant et diffère les opérations cloud.
3. L'authentification Xtream conserve son comportement hors ligne existant.
4. La vérification serveur et la synchronisation reprennent automatiquement au
   prochain retour réseau.

Cette tolérance implique qu'une désactivation effectuée pendant que
l'installation est hors ligne n'est détectée qu'au prochain contact avec le
backend.

### JWT expiré

- L'application est bloquée même si des données locales sont disponibles.
- Elle exige un nouveau flow email + OTP.
- Sans réseau, aucun contournement hors ligne n'est autorisé après l'expiration.

## Parcours 4 — compte désactivé ou expiré

- `403 ACCOUNT_DISABLED` affiche un écran bloquant indiquant que le compte est
  désactivé.
- `403 ACCOUNT_EXPIRED` affiche un écran bloquant indiquant que la validité du
  compte est terminée.
- Ces états interrompent l'accès aux écrans métier et à toute lecture en cours.
- Un retour au flow OTP est proposé pour revérifier ultérieurement le compte,
  notamment après une intervention manuelle en PostgreSQL.
- Aucun JWT local ne permet de contourner un refus reçu du backend.
- Aucun détail SQL, stack trace ou secret n'est affiché.

## Fusion initiale des profils

### Compte cloud sans donnée utilisateur

- Tous les profils locaux existants sont conservés et créés ou associés côté
  backend.
- Le `Profil 1` automatique et encore vide est réutilisé pour le premier profil
  local afin d'éviter un profil supplémentaire artificiel.
- Les autres profils locaux sont créés côté backend avec leur nom et avatar.
- Leur ordre fonctionnel est conservé.
- Les données propres à chaque profil sont ensuite initialisées par F34.

### Compte cloud possédant déjà des profils

- Tous les profils cloud sont importés localement.
- Un profil local déjà lié à un identifiant cloud conserve cette association.
- Un profil local sans association distante est ajouté comme nouveau profil
  cloud ; il n'est jamais supprimé silencieusement.
- Un nom ou un avatar identique ne suffit pas à fusionner automatiquement deux
  profils sans identifiant commun : les doublons apparents sont préférables à
  un mélange de données entre personnes.
- La suppression ou la consolidation manuelle d'un doublon reste possible via
  la gestion normale des profils.

Le profil distant est la référence pour l'identité cloud, tandis que Room reste
la source immédiatement consultée par l'interface.

## Gestion des profils

- En ligne, créer, renommer, modifier l'avatar ou supprimer un profil doit être
  confirmé par le backend avant d'être présenté comme définitivement synchronisé.
- Le dernier profil ne peut jamais être supprimé.
- La suppression d'un profil supprime aussi ses données locales après succès du
  backend.
- Hors ligne, les profils existants restent sélectionnables et utilisables.
- Pour la première version, le CRUD des profils est indisponible hors ligne afin
  d'éviter une identité locale impossible à réconcilier sans confirmation
  serveur ; l'utilisateur reçoit un message non technique et peut continuer à
  utiliser le profil courant.
- Le réglage de connexion automatique à un profil existant continue à
  fonctionner avec l'identité locale associée au profil cloud.

## Déconnexion et changement de compte CSTV

Les Paramètres exposent **deux actions distinctes**, jamais couplées :

- « Se déconnecter » conserve son comportement actuel : il déconnecte le compte
  **Xtream** seul et renvoie à l'écran de connexion Xtream. La session CSTV est
  intacte, aucun OTP n'est redemandé.
- Une section « Compte CSTV » affiche l'email du compte et propose « Se
  déconnecter du compte CSTV », qui renvoie au gate email + OTP.

Motif : changer de fournisseur IPTV ou tester un autre panel ne doit pas coûter
un nouvel OTP, et la séparation des deux comptes doit rester visible pour
l'utilisateur (cf. section 2).

1. Une déconnexion CSTV supprime le JWT et les informations de session.
2. Avant d'associer un autre email CSTV, l'application purge les profils locaux,
   leurs associations cloud et toutes les données Room propres à ces profils.
3. Le nouveau compte démarre ensuite sa propre fusion initiale.
4. Les données de l'ancien compte ne doivent être visibles à aucun instant,
   même brièvement pendant la navigation.
5. Les credentials Xtream ne sont pas envoyés au backend. Leur éventuelle
   conservation suit le comportement de déconnexion Xtream existant.

## Validation et erreurs OTP

- email invalide : message de validation local puis réponse API gérée sans
  crash ;
- code invalide, expiré, consommé ou essais épuisés : message explicite et
  possibilité de demander un nouveau code ;
- quota de demandes atteint : message temporaire invitant à réessayer plus
  tard ;
- panne réseau : conservation de l'email saisi et action Réessayer ;
- double validation du même OTP : une seule réussite, sans création de compte
  ou profil en double ;
- aucune réponse ni aucun log applicatif ne doit exposer l'OTP ou le JWT.

## Critères d'acceptation

- [x] Sans session CSTV, aucun écran Xtream, profil ou contenu n'est accessible.
  *(MainActivity : `cstvGateResolved` avant `startAutoLogin()` et le gate profil ; T6)*
- [ ] Un OTP valide crée un compte inexistant avec un an de validité et un
  premier profil atomiquement. *(comportement backend, déjà livré par les
  commits `feat(backend)` — non retesté depuis ce dépôt Android à l'étape 5)*
- [ ] Une demande OTP seule ne crée aucun compte. *(idem, backend)*
- [x] Le JWT retourné expire à `activeUntil`, et non après une durée fixe.
  *(`CstvAuthRepositoryImplTest.verifyOtp_success_persistsExpiresInAsAnUpperBound_thenAdoptsBackendActiveUntil`)*
- [x] Un JWT expiré impose un nouvel OTP et ne permet aucun accès hors ligne.
  *(`resolveSession_locallyExpiredToken_isSignedOutWithTokenExpired_evenOffline`)*
- [x] Avec un JWT non expiré et le backend indisponible, l'application reste
  utilisable depuis Room et indique l'état hors ligne.
  *(`resolveSession_validTokenButBackendUnreachable_isOffline_withoutTouchingProfiles`)*
- [x] Un refus `ACCOUNT_DISABLED` ou `ACCOUNT_EXPIRED` bloque immédiatement
  l'application et toute lecture en cours.
  *(`CstvSessionGuardInterceptorTest`, tous endpoints ; `MainActivity` observe
  `CstvSessionState` en continu)*
- [x] Le gate CSTV précède toujours le flow Xtream existant.
  *(`LoginViewModelTest.test_autoLogin_doesNotStart_withoutAnExplicitCall`)*
- [x] Un compte cloud vide est initialisé avec tous les profils locaux sans
  laisser un `Profil 1` artificiel supplémentaire.
  *(`ProfileCloudReconcilerTest.blankCloudAccount_pairsTheAutomaticProfile1_...`)*
- [x] Un compte cloud existant et les profils locaux sont fusionnés sans perte
  silencieuse ni association par simple égalité de nom.
  *(`ProfileCloudReconcilerTest.sameDisplayName_withoutAMatchingRemoteId_neverMergesAsOneProfile`)*
- [x] Le dernier profil ne peut pas être supprimé.
  *(`ProfileRepositoryImplTest.test_deleteProfile_refusesToDeleteLastRemainingProfile` +
  `test_deleteProfile_lastRemainingProfile_neverReachesTheBackend`)*
- [x] Les profils restent utilisables hors ligne mais leur CRUD exige le réseau
  dans cette première version.
  *(`ProfileViewModelTest.test_offlineCstv_disablesProfileCrud_...` +
  `ProfileRepositoryImplTest` : 3 CRUD offline → `Unavailable` sans écriture Room)*
- [x] Changer de compte CSTV purge les profils et données de profil du compte
  précédent avant d'afficher le nouveau compte.
  *(`resolveSession_differentAccountId_purgesBeforePublishingActive_thenReconciles`)*
- [x] Les credentials Xtream, catalogues et téléchargements ne sont jamais
  envoyés au backend CSTV. *(aucun DTO CSTV ne référence ces types — vérifié
  par lecture de `CstvDtos.kt`)*
- [x] Les erreurs utilisateur ne contiennent ni OTP, JWT, stack trace, chemin
  local ou détail SQL. *(les messages sont des `@StringRes Int`, jamais du
  texte ; `HttpLoggingInterceptor` CSTV plafonné `BASIC` + `redactHeader`)*
- [x] Les comportements sont couverts par des tests JVM automatisés sans device
  ni émulateur requis. *(800 tests, 0 échec — `./gradlew testDebugUnitTest` ;
  exception documentée en T2 pour `CstvSessionManagerImpl`, non testable JVM
  comme `CredentialsManager`)*

---

# 4. Spécification technique

## 4.1 Composants impactés (fichiers existants)

| Fichier | Modification |
| --- | --- |
| `app/src/main/java/com/cstv/app/MainActivity.kt` | insertion du gate CSTV **avant** le gate Xtream et le gate profil ; observation de l'état de session pour bloquer aussi une lecture en cours |
| `presentation/login/LoginViewModel.kt` | l'auto-login Xtream ne doit plus démarrer dans `init` mais sur appel explicite `startAutoLogin()` déclenché une fois le gate CSTV résolu |
| `data/local/entity/ProfileEntity.kt` | ajout de `remoteId: String?` (UUID du profil cloud, `null` tant que non associé) |
| `data/local/dao/ProfileDao.kt` | `getByRemoteId`, `getAllWithoutRemoteId`, `setRemoteId`, `deleteAll` |
| `data/local/db/AppDatabase.kt` | version **25 → 26** |
| `data/local/db/Migrations.kt` | `MIGRATION_25_26` + ajout à `ALL_MIGRATIONS` |
| `domain/repository/ProfileRepository.kt` | les mutations deviennent suspendues et faillibles (`Result`) ; ajout de `purgeAllProfiles()` |
| `data/repository/ProfileRepositoryImpl.kt` | CRUD confirmé par le backend avant Room ; purge complète ; `ensureInitialized()` ne crée plus `Profil 1` localement lorsqu'une session CSTV existe (le profil vient de `/v1/me`) |
| `presentation/profile/ProfileViewModel.kt`, `ProfileManagementScreen.kt`, `ProfileSelectionScreen.kt` | états d'erreur réseau, CRUD désactivé hors ligne avec message non technique |
| `presentation/settings/SettingsScreen.kt` (+ variante TV) et `SettingsViewModel.kt` | nouvelle section « Compte CSTV » : email affiché, action `onCstvLogout` distincte de `onLogout` (Xtream), inchangé par ailleurs |
| `presentation/navigation/NavGraph.kt`, `MainActivity.kt` | câblage de `onCstvLogout` vers `CstvAuthViewModel.signOut()` |
| `app/src/main/res/values/strings.xml` | libellés de la section compte CSTV et des écrans OTP/bloquants |
| `di/AppModule.kt` | providers CSTV (OkHttp, Retrofit, service, session, repositories, use cases) |
| `app/build.gradle.kts` | `buildConfigField("String", "CSTV_BASE_URL", ...)` alimenté par `local.properties` / variable d'environnement `CSTV_BASE_URL`, comme `TMDB_API_KEY` |
| `app/proguard-rules.pro` | `-keep interface com.cstv.app.data.remote.api.CstvApiService { *; }` (règle obligatoire AGENTS.md) |

Aucune nouvelle dépendance Gradle : Retrofit, OkHttp, Gson, `security-crypto`
et Hilt sont déjà présents. La reprise d'application (`ON_RESUME`) est observée
depuis `MainActivity` via `LocalLifecycleOwner`, ce qui évite d'ajouter
`androidx.lifecycle:lifecycle-process`.

## 4.2 Nouveaux composants

```
data/remote/api/CstvApiService.kt          Retrofit (auth, me, profils)
data/remote/api/CstvAuthInterceptor.kt     Bearer + refus si session absente
data/remote/api/CstvSessionGuardInterceptor.kt
                                           observe TOUTE réponse : 401/403 ->
                                           mise à jour de CstvSessionState
data/remote/dto/CstvDtos.kt                OtpRequestDto, OtpVerifyDto,
                                           AccessTokenDto, AccountDto,
                                           ProfileDto, ProfileCreateDto,
                                           ProfileUpdateDto, ApiErrorDto
data/remote/CstvErrorMapper.kt             HTTP + code JSON -> CstvError
data/local/storage/CstvSessionManager.kt   session chiffrée (interface + impl)
data/repository/CstvAuthRepositoryImpl.kt  OTP, /v1/me, état de session
data/repository/CstvProfileGateway.kt      CRUD profils distants
data/repository/ProfileCloudReconciler.kt  fusion initiale locale <-> cloud
domain/model/CstvSession.kt                accountId, email, activeUntil,
                                           tokenExpiresAt
domain/model/CstvError.kt                  hiérarchie d'erreurs typées
domain/model/CstvSessionState.kt           Unknown | SignedOut(reason) |
                                           Active | Offline | Blocked(reason)
domain/repository/CstvAuthRepository.kt
domain/usecase/RequestOtpUseCase.kt
domain/usecase/VerifyOtpUseCase.kt
domain/usecase/ResolveCstvSessionUseCase.kt
presentation/cstv/CstvAuthViewModel.kt
presentation/cstv/CstvEmailScreen.kt
presentation/cstv/CstvOtpScreen.kt
presentation/cstv/CstvBlockedScreen.kt
```

Les écrans sont des composables communs Mobile/TV (mêmes composants que
`LoginScreen`, focus TV géré par `Modifier.focusRequester`) : le formulaire est
trop simple pour justifier deux implémentations.

## 4.3 Modèles de données

### Session locale — `CstvSessionManager`

Stockée dans un `EncryptedSharedPreferences` **dédié** (`cstv_session_prefs`,
`MasterKey` AES256_GCM), séparé de `secret_shared_prefs` afin qu'une
déconnexion Xtream (`clearCredentials()` fait un `clear()` global) n'efface pas
la session CSTV, et réciproquement.

| Clé | Contenu |
| --- | --- |
| `access_token` | JWT brut, jamais loggué |
| `token_expires_at` | `TimeProvider.nowMillis() + expiresIn * 1000` calculé à la réception |
| `account_id` | UUID du compte, sert à détecter un changement de compte |
| `account_email` | email affiché dans les Paramètres |
| `account_active_until` | date `activeUntil` du dernier `/v1/me`, affichage seul |
| `last_me_success_at` | horodatage de la dernière vérification serveur réussie |

`expiresIn` est utilisé plutôt que le décodage du JWT : la spécification exige
de ne pas traiter le contenu du token comme une preuve, et l'app n'a besoin que
d'une borne locale. Aucun parsing ni vérification de signature côté client.

### Room

`ProfileEntity` gagne `remoteId: String?`. L'identifiant local `Int` reste la
clé primaire et la clé de scoping de toutes les tables par profil : aucune des
sept tables scopées n'est retouchée, ce qui évite sept migrations de clé
primaire pour un gain nul.

```kotlin
// MIGRATION_25_26
ALTER TABLE profiles ADD COLUMN remoteId TEXT DEFAULT NULL
```
Colonne nullable hors clé primaire : un `ALTER TABLE` simple suffit, le pattern
`table_new` de `MIGRATION_9_10` n'est pas nécessaire.

## 4.4 Contrat réseau

| Usage | Appel |
| --- | --- |
| demande OTP | `POST /v1/auth/otp/request` `{email}` → `202 {status:"accepted"}` |
| validation OTP | `POST /v1/auth/otp/verify` `{email, code}` → `200 {accessToken, tokenType, expiresIn}` |
| session + profils | `GET /v1/me` → `{id, email, enabled, activeUntil, profiles[]}` |
| liste profils | `GET /v1/profiles` |
| création | `POST /v1/profiles` `{name, avatarId}` → `201 Profile` |
| renommage / avatar | `PATCH /v1/profiles/{id}` `{name?, avatarId?}` → `200 Profile` |
| suppression | `DELETE /v1/profiles/{id}` → `204`, `409` si dernier profil |

`GET /v1/me` renvoyant déjà les profils, `GET /v1/profiles` n'est utilisé que
pour un rafraîchissement ciblé (retour d'écran de gestion, `404` après
suppression distante).

Client HTTP dédié (le client Xtream porte `DynamicBaseUrlInterceptor` et le
throttle Xtream, inapplicables ici) : `connectTimeout` 10 s, `readTimeout` 10 s,
`callTimeout` 20 s, `retryOnConnectionFailure(true)`.

## 4.5 Sécurité

- `HttpLoggingInterceptor` du client CSTV plafonné à `BASIC` **même en debug**,
  avec `redactHeader("Authorization")` : le niveau `BODY` exposerait l'OTP et le
  JWT dans logcat.
- Le JWT ne transite jamais hors de l'en-tête `Authorization` ; aucune erreur
  utilisateur ne contient de code, de token, de chemin local ni de corps de
  réponse brut.
- `CstvAuthInterceptor` échoue vite (`IOException` typée) si la session est
  absente ou localement expirée, plutôt que d'émettre une requête anonyme.
- Aucun credential Xtream, URL de panel, catalogue ou téléchargement n'est
  sérialisé vers le backend — garanti par le fait qu'aucun DTO CSTV ne référence
  ces types.

## 4.6 Mapping des erreurs

| Réponse | `CstvError` | Traitement UI |
| --- | --- | --- |
| `202` / `200` | — | succès |
| `400`/`422` sur verify | `InvalidOtp` | « Code incorrect ou expiré », nouveau code possible |
| `429` | `RateLimited` | « Trop de demandes, réessayez plus tard » |
| `401 AUTHENTICATION_REQUIRED` / `401 INVALID_TOKEN` | `Unauthenticated` | purge du token, retour au gate email |
| `403 ACCOUNT_DISABLED` | `AccountDisabled` | écran bloquant dédié |
| `403 ACCOUNT_EXPIRED` | `AccountExpired` | écran bloquant dédié |
| `404` (profil) | `ProfileNotFound` | rechargement de la liste, pas de recréation |
| `409` (suppression) | `LastProfile` | « Le dernier profil ne peut pas être supprimé » |
| `503` / `IOException` | `Unavailable` | mode hors ligne ou action Réessayer |
| autre `5xx` | `ServerError` | message générique + Réessayer |

Le code JSON (`error.code`) prime sur le statut quand les deux sont
disponibles ; un `403` inconnu est traité comme `AccountDisabled` (fail-closed).

## 4.7 Performances

- Le gate ajoute **un** appel `/v1/me` au démarrage, plafonné à 20 s de
  `callTimeout` ; en l'absence de réseau (`NetworkMonitor`) l'appel n'est même
  pas tenté et le démarrage bascule immédiatement en mode hors ligne.
- L'auto-login Xtream et la synchronisation catalogue restent séquencés après le
  gate : le coût réseau supplémentaire au démarrage est borné à cet appel.
- La fusion initiale des profils est bornée par le nombre de profils (quelques
  unités) et n'exécute qu'un `POST /v1/profiles` par profil local non associé.

## 4.8 Compatibilité et reprise d'installation existante

- Une installation existante conserve ses profils et données ; `remoteId` vaut
  `null` jusqu'à la première association, réalisée par la fusion initiale.
- Le compte CSTV devenant obligatoire, une installation déjà connectée à Xtream
  passe par le gate au premier lancement de la version, puis retrouve son état.
- Un `Profil 1` local existant et vide n'est pas un cas particulier : c'est le
  `Profil 1` **cloud** créé automatiquement qui est réutilisé, par appariement
  d'ordre, pour éviter un profil artificiel supplémentaire.

## 4.9 Risques techniques

| Risque | Mitigation |
| --- | --- |
| Purge de compte incomplète laissant des données de l'ancien compte | purge exécutée dans **une** transaction Room avant toute écriture du nouveau compte, et avant que le gate ne rende la main ; test dédié |
| `clear()` global de `CredentialsManager` effaçant la session CSTV | fichier de préférences séparé (4.3) |
| Horloge locale reculée prolongeant artificiellement la session | l'expiration locale est une borne *supplémentaire* ; le backend reste seul juge à chaque `/v1/me`, un `401`/`403` prime toujours |
| CRUD confirmé côté backend puis échec Room | l'écriture Room suit immédiatement la réponse, dans une transaction ; un échec local est retenté au prochain `/v1/me` qui réimporte la liste distante (le backend est la référence d'identité) |
| Règle R8 manquante sur `CstvApiService` | ligne `-keep` ajoutée dans la même tâche que l'interface |
| Boucle de tests figée par un ticker de session | aucune tâche périodique : la revérification est déclenchée par événement (démarrage, `ON_RESUME`, retour réseau), conformément à AGENTS.md |

---

# 5. Architecture

## 5.1 Vue d'ensemble

```
presentation/cstv (Email, OTP, Blocked)      MainActivity (gates)
        |                                            |
        v                                            v
CstvAuthViewModel  --->  ResolveCstvSessionUseCase / Request-VerifyOtpUseCase
        |                                            |
        v                                            v
              domain/repository/CstvAuthRepository (interface)
                              |
        data/repository/CstvAuthRepositoryImpl ----+---- CstvProfileGateway
                 |                    |                        |
      CstvSessionManager       CstvApiService (Retrofit) <------+
      (EncryptedSharedPrefs)          |
                                 CstvErrorMapper
                                      |
                          ProfileCloudReconciler ---> ProfileRepository (Room)
```

Room reste la source lue par l'interface : aucun écran n'observe directement une
réponse réseau. Le backend est en revanche la référence de l'**identité** des
profils (`remoteId`).

## 5.2 Chaîne de gates au démarrage

L'ordre est strict et rendu hors du `NavHost`, comme le gate profil actuel :

```
Splash
  -> Gate CSTV      (SignedOut(*) -> Email + OTP ; Blocked(*) -> écran bloquant)
  -> Auto-login Xtream (comportement existant, démarré seulement ici)
  -> Gate profil    (sélection/auto-start, comportement existant)
  -> NavHost applicatif
```

`MainActivity` observe `CstvSessionState` en continu : un passage à
`Blocked(...)` recompose l'écran bloquant par-dessus le `NavHost`, ce qui
interrompt de fait la lecture en cours puisque la destination lecteur quitte la
composition.

## 5.3 Machine d'états de session

```
Unknown ──resolve──> SignedOut(NoSession)     (aucun token)
                 └─> SignedOut(TokenExpired)  (token présent mais expiré localement)
                 └─> Active                   (/v1/me OK)
                 └─> Offline                  (token non expiré, backend injoignable)
                 └─> Blocked(Disabled | Expired)
Active   ──401──────────────> SignedOut(SessionRejected)
Active   ──403──────────────> Blocked(Disabled | Expired)
Active   ──expiration locale atteinte au prochain resolve──> SignedOut(TokenExpired)
Offline  ──expiration locale atteinte──────> SignedOut(TokenExpired)
Offline  ──retour réseau───────────────────> resolve
```

`ResolveCstvSessionUseCase` est l'unique point d'entrée de ces transitions ; il
est appelé au démarrage, à `ON_RESUME` et au retour réseau. Aucun sondage
périodique.

### Où la validité du compte est réellement vérifiée

Le backend revérifie `enabled` et `active_until` en base **à chaque requête
authentifiée** (`AuthMiddleware`), sans faire confiance au contenu du JWT.
`/v1/me`, le CRUD des profils et chaque `GET`/`PUT` de snapshot F34 sont donc
tous des points de contrôle.

Le client, lui, n'émet **pas** d'appel de vérification dédié par action : un
`/v1/me` explicite n'a lieu qu'au `resolve` (démarrage, `ON_RESUME`, retour
réseau, après validation d'OTP). Ce serait un aller-retour réseau par action
pour une information que le backend renvoie déjà sur la requête utile.

Pour que ce contrôle serveur profite à toutes les requêtes,
`CstvSessionGuardInterceptor` est posé sur le client HTTP CSTV et observe
**toutes** les réponses, quelle que soit la couche appelante :

| Réponse observée | Effet immédiat sur `CstvSessionState` |
| --- | --- |
| `401` (quel que soit l'appel) | purge du token → `SignedOut(SessionRejected)` |
| `403 ACCOUNT_DISABLED` | `Blocked(Disabled)` |
| `403 ACCOUNT_EXPIRED` | `Blocked(Expired)` |
| `403` inconnu | `Blocked(Disabled)` (fail-closed) |

Conséquence : une désactivation faite en base est détectée dès la **première**
requête suivante — souvent un envoi F34 déclenché par une simple mise en pause —
sans attendre un redémarrage. Aucun appelant ne peut oublier de traiter le
refus, puisque la transition ne dépend pas de lui. Un `403` interrompt la
lecture en cours conformément au parcours 4, `MainActivity` observant cet état.

**`SignedOut` et `Blocked` sont deux écrans différents**, pour deux situations
que l'utilisateur ne peut pas traiter de la même façon :

| État | Écran | Sortie possible |
| --- | --- | --- |
| `SignedOut(NoSession)` | saisie email, champ vide | valider un OTP |
| `SignedOut(TokenExpired)` | saisie email **préremplie** avec `account_email`, message « Votre session a expiré, veuillez vous reconnecter » | valider un OTP |
| `SignedOut(SessionRejected)` | idem, message « Votre session n'est plus valide » | valider un OTP |
| `Blocked(Disabled)` | écran bloquant « compte désactivé » | action « Revérifier » (relance `resolve`) ou retour au flow OTP |
| `Blocked(Expired)` | écran bloquant « validité du compte terminée » | idem |

Un JWT expiré ne conduit donc **pas** à un écran bloquant statique : il ramène
directement au gate email + OTP, l'email étant prérempli pour éviter une
ressaisie. Il reste bloquant au sens fonctionnel — aucun écran métier, aucune
lecture, aucune donnée Room n'est accessible tant qu'un nouvel OTP n'a pas été
validé, y compris sans réseau.

Hors ligne avec un token expiré, l'écran OTP s'affiche mais la demande de code
échoue en `Unavailable` : le message indique qu'une connexion internet est
nécessaire, avec une action Réessayer. Aucun repli local n'est proposé — c'est
la seule situation où l'absence de réseau rend l'application inutilisable, et
c'est voulu.

L'état `Offline` (token non expiré, backend injoignable) **franchit le gate** :
il n'affiche aucun écran propre, seulement l'indicateur hors ligne non bloquant.
La suite du démarrage est alors intégralement régie par le repli Xtream
existant (`AuthRepositoryImpl.offlineSessionOrRejection` : validation réseau
antérieure, même compte, catalogue complet), qui n'est pas modifié par ce
ticket. Sont indisponibles dans cet état, et seulement eux : la demande et la
validation d'OTP, le CRUD des profils (5.6) et les envois cloud de F34, qui
restent en attente. La sélection de profil, le catalogue Room, les favoris,
l'historique et la reprise de lecture fonctionnent normalement.

L'expiration locale n'est jamais détectée par un minuteur : elle est évaluée à
chaque `resolve` et avant chaque requête (`CstvAuthInterceptor`). Une session qui
expire pendant que l'utilisateur regarde un flux n'interrompt donc pas la
lecture en cours ; le gate s'applique au prochain `ON_RESUME`, retour réseau ou
démarrage. Le blocage immédiat reste réservé aux refus explicites du backend
(`401`, `403`), conformément au parcours 4.

## 5.4 Flux — première validation OTP

1. `RequestOtpUseCase(email)` → `202`, message neutre (aucune divulgation
   d'existence de compte).
2. `VerifyOtpUseCase(email, code)` → `AccessTokenDto` ; `CstvSessionManager`
   persiste token + `token_expires_at`.
3. `GET /v1/me` → `AccountDto`.
4. Comparaison `account_id` reçu / `account_id` mémorisé :
   - identique ou absent → poursuite ;
   - différent → `purgeAllProfiles()` (profils + sept tables scopées + états de
     synchronisation F34) dans une transaction, puis poursuite.
5. `ProfileCloudReconciler.reconcile(account)` (5.5).
6. F34 initialise ou fusionne le contenu des namespaces.
7. Le gate se résout, l'auto-login Xtream démarre.

## 5.5 Réconciliation des profils

Entrée : profils cloud (`AccountDto.profiles`, triés par `createdAt`) et profils
locaux (triés par `createdAt`).

1. **Association par `remoteId`** : tout profil local dont le `remoteId`
   correspond à un profil cloud est mis à jour (nom, avatar cloud font foi).
2. **Import** : tout profil cloud sans correspondance locale est inséré dans
   Room avec `remoteId`, `createdAt` = `createdAt` distant converti en epoch
   millis, ce qui reproduit localement l'ordre fonctionnel cloud.
3. **Cas « cloud vierge »** : si le compte ne possède qu'un seul profil cloud,
   nommé `Profil 1`, sans aucun namespace distant (information fournie par F34)
   et qu'il existe des profils locaux non associés, ce profil cloud est
   **apparié** au premier profil local (`remoteId` posé, puis `PATCH` du nom et
   de l'avatar locaux) au lieu d'être importé — c'est ce qui évite le profil
   artificiel supplémentaire.
4. **Export** : chaque profil local restant sans `remoteId` est créé via
   `POST /v1/profiles` dans l'ordre de `createdAt`, et son `remoteId` est
   enregistré.
5. Aucun appariement par nom ou avatar : deux profils sans identifiant commun
   restent distincts, y compris homonymes.
6. Aucune suppression locale n'est déduite d'une absence côté cloud lors de
   cette première réconciliation ; seule une suppression explicite, ou un
   `404 PROFILE_NOT_FOUND` sur un profil déjà associé, supprime un profil.

En cas d'échec réseau au milieu de l'étape 4, les profils déjà associés le
restent : la réconciliation est **idempotente** et reprend au prochain
`resolve`.

## 5.6 CRUD des profils

`ProfileRepositoryImpl` séquence systématiquement backend puis Room :

| Action | Séquence |
| --- | --- |
| créer | `POST /v1/profiles` → insertion Room avec `remoteId` |
| renommer / avatar | `PATCH` → `update` Room |
| supprimer | `DELETE` (`409` = dernier profil) → suppression des données locales du profil puis de la ligne `profiles` |

Hors ligne, ces trois actions retournent `CstvError.Unavailable` et l'UI affiche
un message non technique ; la sélection de profil et l'usage restent possibles.
Motif : une identité locale créée hors ligne serait impossible à réconcilier
sans confirmation serveur, et produirait des doublons entre installations.

## 5.7 Deux déconnexions indépendantes

| Action | Effet | Ne touche pas |
| --- | --- | --- |
| « Se déconnecter » (existant) | `LogoutUseCase` → `trailerRepository.clearSessionCache()` + `CredentialsManager.clearCredentials()` ; retour au login Xtream | session CSTV, profils, données de profil |
| « Se déconnecter du compte CSTV » (nouveau) | `CstvAuthViewModel.signOut()` → `CstvSessionManager.clear()` (token, expiration, `last_me_success_at`) ; `CstvSessionState` passe à `SignedOut` ; retour au gate email | credentials Xtream, profils, données de profil, `account_id` mémorisé |

L'indépendance est structurelle : les deux sessions vivent dans deux fichiers
`EncryptedSharedPreferences` distincts (4.3), donc le `clear()` global de
`CredentialsManager` ne peut pas emporter la session CSTV.

La déconnexion CSTV **ne purge rien** : `account_id` est conservé afin qu'une
reconnexion au même compte retrouve ses profils intacts. La purge n'a lieu qu'au
moment où un `/v1/me` renvoie un `account_id` différent de celui mémorisé
(5.4, étape 4) — c'est-à-dire lors d'un véritable changement de compte, y
compris quand il passe par cette déconnexion.

## 5.8 Décisions techniques et justifications

1. **Client HTTP séparé du client Xtream** — le client Xtream réécrit l'URL de
   base et applique un throttle propre au panel ; les réutiliser exposerait le
   backend CSTV à des réécritures d'hôte.
2. **Expiration locale dérivée de `expiresIn`, jamais du contenu du JWT** —
   évite d'embarquer un décodeur JWT et respecte la règle « le token n'est pas
   une preuve ».
3. **Identifiant local `Int` conservé, `remoteId` en colonne annexe** — préserve
   les sept tables scopées et leurs clés primaires, donc aucune migration
   destructrice ni recopie de table.
4. **Préférences chiffrées dédiées** — isole le cycle de vie de la session CSTV
   de celui des credentials Xtream.
5. **Gate rendu hors du `NavHost`** — même mécanique que le gate profil existant
   (Phase 27) : aucune route applicative n'est atteignable tant que le gate n'est
   pas résolu, y compris par deep link de notification.
6. **Revérification événementielle et non périodique** — exigence AGENTS.md sur
   les boucles infinies de tests, et suffisant fonctionnellement (le blocage doit
   être détecté au prochain contact backend, pas en temps réel).
7. **`403` inconnu traité comme bloquant** — en cas d'évolution du backend, mieux
   vaut bloquer à tort que laisser passer un compte refusé.
8. **Déconnexions Xtream et CSTV séparées** — décision PO ; changer de panel IPTV
   ne doit pas coûter un OTP, et la purge des données reste conditionnée au
   changement effectif de compte, pas à la déconnexion.

## 5.9 Tests prévus (JVM uniquement)

- `CstvSessionManagerTest` : persistance, expiration locale, purge.
- `CstvErrorMapperTest` : chaque statut/code du tableau 4.6, corps JSON
  malformé, corps vide.
- `CstvAuthRepositoryImplTest` : OTP invalide, quota, `401`, `403` désactivé,
  `403` expiré, backend injoignable avec token valide (→ `Offline`), token
  expiré hors ligne (→ `SignedOut(TokenExpired)` et non `Offline`), token
  expiré pendant une session `Offline`, double validation du même OTP.
- `ProfileCloudReconcilerTest` : cloud vierge + profils locaux, cloud alimenté +
  local vide, appariement `Profil 1`, homonymes non fusionnés, reprise après
  échec partiel, ordre préservé.
- `ProfileRepositoryImplTest` (étendu) : CRUD confirmé backend, `409` dernier
  profil, indisponibilité hors ligne, purge complète des sept tables au
  changement de compte.
- `CstvAuthViewModelTest` : états loading/erreur/succès, conservation de l'email
  après panne réseau, aucun secret dans les messages exposés.
- `LogoutIsolationTest` : la déconnexion Xtream laisse la session CSTV, les
  profils et leurs données intacts ; la déconnexion CSTV laisse les credentials
  Xtream intacts, ne purge aucun profil, et une reconnexion au même compte
  retrouve les profils existants.

---

# 6. Plan de développement

Les tâches sont ordonnées par dépendance. T1 à T4 sont indépendantes entre
elles une fois T1 livrée ; T6 exige T2, T3 et T5 ; T8 exige T4 et T7.

Chaque tâche se termine par `./gradlew assembleDebug lintDebug testDebugUnitTest`
au vert, tests des phases précédentes inclus.

---

- [x] **T1 — Contrat réseau CSTV**

Objectif :
Exposer le backend CSTV à l'application : URL de base configurable, service
Retrofit, client HTTP dédié, DTOs et traduction des erreurs.

Fichiers :
- `app/build.gradle.kts` (`buildConfigField "CSTV_BASE_URL"`, source
  `local.properties` / variable d'environnement)
- `data/remote/api/CstvApiService.kt`
- `data/remote/api/CstvAuthInterceptor.kt`
- `data/remote/api/CstvSessionGuardInterceptor.kt`
- `data/remote/dto/CstvDtos.kt`
- `data/remote/CstvErrorMapper.kt`
- `domain/model/CstvError.kt`
- `di/AppModule.kt` (OkHttp + Retrofit CSTV, logging `BASIC` +
  `redactHeader("Authorization")`)
- `app/proguard-rules.pro` (`-keep interface ...CstvApiService`)

Validation :
`CstvErrorMapperTest` couvre chaque ligne du tableau 4.6, plus corps JSON vide,
tronqué et code inconnu. `CstvSessionGuardInterceptorTest` : un `401` ou un
`403` reçu sur **n'importe quel** endpoint (y compris un endpoint F34) fait
transiter l'état de session sans intervention de l'appelant ; un `2xx` ou une
erreur réseau ne le modifient pas. Le client CSTV ne porte ni
`DynamicBaseUrlInterceptor` ni le throttle Xtream.

Note d'ordre : l'état de session est publié par un holder singleton injecté
dans l'interceptor, pas par `CstvAuthRepositoryImpl`, afin d'éviter un cycle de
dépendances entre le client HTTP et le repository qui l'utilise.

---

- [x] **T2 — Session CSTV chiffrée**

Objectif :
Persister la session dans un `EncryptedSharedPreferences` dédié et calculer
l'expiration locale sans décoder le JWT.

Fichiers :
- `data/local/storage/CstvSessionManager.kt` (interface + impl, cf. règle
  Mockito d'AGENTS.md)
- `domain/model/CstvSession.kt`
- `domain/model/CstvSessionState.kt`
- `di/AppModule.kt`

Validation :
`CstvSessionManagerTest` : persistance, `expiresIn` → `token_expires_at` via
`TimeProvider`, expiration franchie, `clear()` ne touchant pas
`secret_shared_prefs`, et `CredentialsManager.clearCredentials()` ne touchant
pas la session CSTV.

> **Note (étape 5)** : pas de `CstvSessionManagerTest` dédié — `CstvSessionManagerImpl`
> construit son `EncryptedSharedPreferences` en interne à partir d'un `Context`
> Android réel (Keystore), même limitation que `CredentialsManager` (non testé
> non plus dans ce projet, absence de Robolectric, cf. AGENTS.md). Le calcul
> `expiresIn` → `token_expires_at` ne vit pas ici mais dans
> `CstvAuthRepositoryImpl.verifyOtp()`, couvert par `CstvAuthRepositoryImplTest`
> (T3). L'isolation `clear()`/`secret_shared_prefs` est garantie par construction
> (deux fichiers de préférences chiffrées distincts, 4.3) et vérifiée
> fonctionnellement par `LogoutIsolationTest` (T10).

---

- [x] **T3 — Authentification OTP et résolution de session**

Objectif :
Implémenter la demande d'OTP, sa validation, l'appel `/v1/me` et la machine
d'états de session de 5.3.

Fichiers :
- `domain/repository/CstvAuthRepository.kt`
- `data/repository/CstvAuthRepositoryImpl.kt`
- `domain/usecase/RequestOtpUseCase.kt`, `VerifyOtpUseCase.kt`,
  `ResolveCstvSessionUseCase.kt`
- `di/AppModule.kt`

Validation :
`CstvAuthRepositoryImplTest` avec `CstvApiService` mocké : OTP invalide, quota
`429`, `401`, `403 ACCOUNT_DISABLED`, `403 ACCOUNT_EXPIRED`, `403` inconnu
(fail-closed), backend injoignable avec token valide → `Offline`, token expiré
hors ligne → `SignedOut(TokenExpired)`, expiration atteinte pendant `Offline`,
double validation du même OTP. Aucune tâche périodique introduite.

---

- [x] **T4 — Room 25 → 26 : `remoteId` sur les profils**

Objectif :
Associer un profil local à son profil cloud sans toucher aux sept tables
scopées par `profileId`.

Fichiers :
- `data/local/entity/ProfileEntity.kt`
- `data/local/dao/ProfileDao.kt` (`getByRemoteId`, `getAllWithoutRemoteId`,
  `setRemoteId`, `deleteAll`)
- `data/local/db/AppDatabase.kt` (version 26)
- `data/local/db/Migrations.kt` (`MIGRATION_25_26` + `ALL_MIGRATIONS`)
- `domain/model/Profile.kt`

Validation :
SQL de la migration relu contre le schéma de l'entité (pas d'`androidTest` dans
ce projet, cf. AGENTS.md). Tests DAO existants adaptés ; un profil créé avant
migration conserve ses données et obtient `remoteId = null`.

---

- [x] **T5 — Écrans email, OTP et compte bloqué**

Objectif :
Fournir l'interface du gate, commune Mobile et TV.

Fichiers :
- `presentation/cstv/CstvAuthViewModel.kt`
- `presentation/cstv/CstvEmailScreen.kt`, `CstvOtpScreen.kt`,
  `CstvBlockedScreen.kt`
- `app/src/main/res/values/strings.xml`

Validation :
`CstvAuthViewModelTest` : états loading/erreur/succès, email conservé après
panne réseau, email prérempli en `SignedOut(TokenExpired)`, aucun OTP/JWT/stack
trace dans les messages exposés, `@get:Rule Timeout.seconds(60)` présent.

---

- [x] **T6 — Gate CSTV au démarrage**

Objectif :
Placer le gate CSTV avant l'auto-login Xtream et le gate profil, et le
réappliquer sur refus backend.

Fichiers :
- `MainActivity.kt` (chaîne de gates 5.2, observation de `CstvSessionState`,
  `resolve` sur `ON_RESUME` et retour réseau via `NetworkMonitor`)
- `presentation/login/LoginViewModel.kt` (auto-login retiré de `init`, exposé
  en `startAutoLogin()`)

Validation :
`LoginViewModelTest` : l'auto-login ne part pas tant que `startAutoLogin()`
n'est pas appelé. Vérifier qu'aucun écran métier ni deep link de notification
n'est atteignable avant résolution du gate.

---

- [x] **T7 — CRUD des profils confirmé par le backend**

Objectif :
Faire précéder toute mutation de profil par sa confirmation distante, et
désactiver ce CRUD hors ligne.

Fichiers :
- `data/repository/CstvProfileGateway.kt`
- `domain/repository/ProfileRepository.kt` (mutations faillibles)
- `data/repository/ProfileRepositoryImpl.kt`
- `presentation/profile/ProfileViewModel.kt`, `ProfileManagementScreen.kt`,
  `ProfileSelectionScreen.kt`

Validation :
`ProfileRepositoryImplTest` étendu : création/renommage/avatar/suppression
confirmés backend, `409` → « dernier profil », hors ligne →
`CstvError.Unavailable` sans écriture Room, suppression nettoyant les sept
tables scopées.

---

- [x] **T8 — Réconciliation initiale des profils**

Objectif :
Fusionner profils locaux et profils cloud selon 5.5, de façon idempotente.

Fichiers :
- `data/repository/ProfileCloudReconciler.kt`
- `data/repository/CstvAuthRepositoryImpl.kt` (appel après `/v1/me`)

Validation :
`ProfileCloudReconcilerTest` : cloud vierge + profils locaux, cloud alimenté +
local vide, appariement du `Profil 1` automatique, homonymes non fusionnés,
ordre préservé, reprise après échec réseau en cours d'export, exécution
répétée sans effet de bord.

Note : le caractère « vierge » du `Profil 1` cloud s'appuie sur
`GET /v1/profiles/{id}/objects` livré par F34. Tant que F34 n'est pas
disponible, cette tâche se limite au cas « profil cloud sans namespace connu »
via une abstraction injectée (`CloudProfileEmptinessProbe`) dont
l'implémentation par défaut renvoie « inconnu » et désactive l'appariement.

---

- [x] **T9 — Purge au changement de compte CSTV**

Objectif :
Garantir qu'aucune donnée d'un compte précédent ne subsiste ni n'apparaît, même
brièvement.

Fichiers :
- `data/repository/ProfileRepositoryImpl.kt` (`purgeAllProfiles()`
  transactionnel)
- `data/repository/CstvAuthRepositoryImpl.kt` (comparaison `account_id`)
- `data/local/dao/ProfileDao.kt`

Validation :
Test dédié : `account_id` différent → profils et sept tables scopées vidés dans
une transaction, avant toute écriture du nouveau compte et avant la résolution
du gate ; `account_id` identique → aucune purge.

---

- [x] **T10 — Section « Compte CSTV » dans les Paramètres**

Objectif :
Afficher l'email du compte et proposer une déconnexion CSTV distincte de la
déconnexion Xtream.

Fichiers :
- `presentation/settings/SettingsScreen.kt` (+ variante TV)
- `presentation/settings/SettingsViewModel.kt`
- `presentation/navigation/NavGraph.kt`, `MainActivity.kt` (câblage
  `onCstvLogout`)
- `app/src/main/res/values/strings.xml`

Validation :
`LogoutIsolationTest` : déconnexion Xtream → session CSTV, profils et données
intacts ; déconnexion CSTV → credentials Xtream intacts, aucune purge,
`account_id` conservé, reconnexion au même compte retrouvant les profils.

---

- [x] **T11 — Vérification transverse**

Objectif :
Contrôler les exigences non fonctionnelles avant review.

Actions :
- [x] relecture des logs : `provideCstvOkHttpClient` (AppModule) plafonne
  `HttpLoggingInterceptor` à `BASIC` **même en debug** et applique
  `redactHeader("Authorization")` ; l'OTP et le JWT ne transitent que dans le
  corps de requête/l'en-tête `Authorization`, jamais loggués en clair.
- [x] `assembleRelease` : `BUILD SUCCESSFUL` (`--no-configuration-cache` ;
  une première tentative a échoué uniquement sur la sérialisation du
  *configuration cache* Gradle, panne préexistante au projet et sans rapport
  avec R8/CstvApiService — l'APK avait déjà été produit avec succès avant cet
  échec de cache). La règle `-keep interface ...CstvApiService` tient.
- [x] critères d'acceptation section 3 repris un par un (voir cases cochées
  ci-dessus) ; deux critères restent non cochés car purement backend
  (création atomique du compte, demande OTP seule sans création) — déjà
  livrés par les commits `feat(backend)` mais non retestés depuis ce dépôt.
- [x] `./gradlew testDebugUnitTest` complet : **800 tests, 0 échec**, aucun
  test désactivé ni supprimé.

---

# 7. Notes de développement

## Étape 5 (implémentation) — 2026-08-12

Le code source de T1 à T10 existait déjà (non commité) au démarrage de cette
étape ; le travail a consisté à auditer chaque tâche contre ses critères de
validation, combler les tests manquants et corriger les régressions trouvées
en cours de route.

**Tests ajoutés ou étendus** :
- `CstvErrorMapperTest` — couverture complète du tableau 4.6, corps vide/tronqué.
- `CstvSessionGuardInterceptorTest`, `CstvAuthInterceptorTest` — nouveaux,
  aucun test d'interceptor n'existait dans le projet.
- `CstvAuthRepositoryImplTest` — 1 test → 22 tests (machine d'états 5.3 au
  complet, purge sur changement de compte, double validation OTP).
- `ProfileCloudReconcilerTest` — nouveau, 8 tests (les 6 scénarios de 5.9 +
  reprise après échec + idempotence).
- `ProfileRepositoryImplTest` — extension T7 (CRUD confirmé backend, offline
  → `Unavailable` sans écriture Room, purge incluant `profile_sync_state`).
- `LogoutIsolationTest` — nouveau ; + 1 test dans `SettingsViewModelTest`.
- `CstvAuthViewModelTest` — 1 test → 13 tests (loading/erreur/succès, email
  conservé/préremplli, aucun secret exposé).
- `LoginViewModelTest` — ajout du test manquant sur `startAutoLogin()`.

**Écart documenté (T2)** : pas de `CstvSessionManagerTest`. `CstvSessionManagerImpl`
construit son `EncryptedSharedPreferences` à partir d'un `Context` Android réel
(Keystore) — même limitation que `CredentialsManager`, non testé non plus dans
ce projet (pas de Robolectric). Le calcul d'expiration vit dans
`CstvAuthRepositoryImpl` et y est testé.

**Déviation mineure vs 4.1** : les mutations de `ProfileRepository` restent des
`suspend fun` qui lèvent `CstvException` plutôt qu'un retour explicite en
`Result<T>`. Fonctionnellement équivalent (faillible, géré par l'appelant),
non corrigé faute de justifier une signature publique différente pour un
gain nul.

- Étapes 1 et 2 : idée structurée et contrat fonctionnel établi à partir du
  backend CSTV validé et des décisions du PO.
- F34 dépend de la session et de l'identité des profils définies par ce ticket.
- Étape 3 : spécification technique et architecture arrêtées. Points notables :
  identifiant local `Int` conservé avec `remoteId` en colonne annexe (Room
  25 → 26, `ALTER TABLE` simple), session dans un `EncryptedSharedPreferences`
  dédié, client HTTP CSTV séparé du client Xtream, gate rendu hors du `NavHost`,
  revérification événementielle sans tâche périodique.
- L'appariement du `Profil 1` cloud automatique nécessite de savoir si le compte
  possède déjà des namespaces : cette information est fournie par le listing
  `GET /v1/profiles/{id}/objects` de F34, seul point où F33 dépend de F34.
- Étape 4 : onze tâches (T1 → T11). Livrable indépendamment de F34 : la
  dépendance ci-dessus est isolée derrière `CloudProfileEmptinessProbe` (T8),
  dont l'implémentation par défaut renvoie « inconnu » et désactive
  l'appariement jusqu'à la livraison de F34/T6.
- Étape 5 : démarrage de l'implémentation. Le socle CSTV est ajouté (session
  chiffrée séparée, client Retrofit dédié, gardes `401`/`403`, DTOs, migrations
  Room 25 → 26 pour `remoteId` et gate email/OTP avant l'auto-login Xtream).
  Les tâches de réconciliation cloud, CRUD distant, purge de changement de
  compte et Paramètres restent à terminer avant toute review.
- Étape 6 : review technique menée sur l'état livré (T1 → T6 partielles, T7 →
  T11 non commencées), sans modification de code. Quatre points critiques :
  `java.time` sous `minSdk 21` qui fait échouer `lintDebug` et casse les API
  21-25, index créés par les migrations mais non déclarés sur les entités
  (crash Room sur installation existante), `account_id` écrasé avant de pouvoir
  détecter un changement de compte, et absence totale de tests sur le code CSTV.
  Détail et ordre de correction en section 8.
- Étape 7 : corrections appliquées dans l'ordre de la section 8 : compatibilité
  API 21 sans `java.time`, index Room déclarés, conservation de `account_id`
  jusqu'à la purge transactionnelle, mapping d'erreurs CSTV, fail-fast,
  budgets réseau, gate OTP/focus TV, URL CSTV obligatoire en release,
  CRUD/réconciliation/purge des profils et déconnexion CSTV isolée. Les tests
  JVM de non-régression ont été lancés mais n'ont pas encore atteint leur
  résultat final ; aucune étape 8 n'est engagée.
- Étape 8 : **validation non acquise**. `:app:compileDebugKotlin` et
  `:app:assembleDebug` sont passés ; les tests ciblés CSTV (mapper, ETag,
  codec, fusion et ViewModel OTP) passent ; `git diff --check` est propre et
  l'hôte CSTV répond en HTTP (404 à la racine, endpoint non attendu). La suite
  JVM complète et `lintDebug` n'ont pas produit de résultat final exploitable
  dans cette exécution. Surtout, l'audit statique relève deux critères non
  satisfaits : l'expiration de session reste calculée depuis `expiresIn` au lieu
  de `activeUntil`, et le CRUD profils n'expose ni état ni désactivation hors
  ligne à l'UI. Le statut reste `FIXES`; aucune étape 9 n'est engagée.
- Étape 7 (complément après les bloqueurs relevés à l'étape 8) : la durée de
  session persistée est désormais remplacée par `activeUntil` après `/v1/me`
  (et non laissée sur le `expiresIn` transitoire de l'OTP). Le CRUD des profils
  observe l'état CSTV : les actions d'ajout et d'édition sont désactivées hors
  ligne, le ViewModel refuse aussi toute mutation en défense, et l'erreur
  explicite est affichée. `CstvAuthRepositoryImplTest` couvre l'autorité de
  `activeUntil`; `ProfileViewModelTest` couvre le refus hors ligne.
- Étape 8 (rejouée après correction) : `testDebugUnitTest` complet est vert
  (735 tests, aucun échec), `assembleDebug` et `lintDebug` sont verts, et
  `git diff --check` est propre. Lint ne remonte que des avertissements
  existants (manifest API 24 et contrôle Compose obsolète). La validation reste
  en attente d'un parcours authentifié sur le backend CSTV réel : il nécessite
  l'adresse email qui recevra l'OTP, non fournie. Aucun défaut de code connu ne
  reste dans le périmètre de l'étape 7.
- Étape 8 (constat réseau) : le 2026-08-12, `GET /v1/me` sur l'URL CSTV
  fournie répond `401` sans jeton, ce qui confirme que le backend joint protège
  bien la route. Le parcours réel OTP, puis `/v1/me` authentifié, reste à
  effectuer avec une adresse email explicitement autorisée par le PO.
- Étape 6 (nouvelle passe, 2026-08-12) : review ciblée sur le delta non encore
  relu (T7 → T11 : `ProfileCloudReconciler`, `CstvProfileGateway`,
  `CloudProfileEmptinessProbe`, purge de compte, section « Compte CSTV »),
  code lu intégralement sans modification. Aucun point critique ni majeur
  trouvé : la réconciliation reste idempotente et sans appariement par nom,
  la purge est transactionnelle et s'exécute avant que le nouveau compte ne
  soit publié, le CRUD est bien confirmé par le backend avant Room. Les
  corrections C1 → C4 et M1 → M12 de la review précédente (section 8) ont été
  vérifiées présentes dans le code actuel (index déclarés, ordre d'écriture de
  `account_id`, timeouts F33 distincts de F34, focus TV, `Regex` au lieu de
  `Patterns`, etc.) : aucune n'a régressé.
- Étape 7 (2026-08-12) : rien à corriger à l'issue de cette passe — voir
  étape 6 ci-dessus. `./gradlew testDebugUnitTest lintDebug assembleDebug`
  relancé au vert (869 tests, 0 échec, périmètre F33 + F34 + F35). Le statut
  passe à `VALIDATION` : il ne reste que le parcours OTP réel avec un compte
  autorisé par le PO (étape 8, déjà signalé ci-dessus), non automatisable.
- Étape 8 (décision PO, 2026-08-12) : le PO choisit explicitement de ne pas
  exécuter le parcours OTP réel (email + code + `/v1/me` authentifié) avant
  livraison — accepté sur la base des vérifications automatisées ci-dessus
  (869 tests JVM, build/lint verts) et du constat réseau déjà fait (backend
  joint, route protégée). En cas d'anomalie sur le parcours réel une fois en
  usage, correctif en hotfix plutôt qu'en bloquant la livraison.

---

# 8. Review

Review technique du 2026-08-12, réalisée sur l'arbre de travail courant sans
modification du code.

## État d'avancement constaté

| Tâche | État |
| --- | --- |
| T1 — Contrat réseau CSTV | partiel (service, interceptors, DTOs, DI, R8 posés ; mapping d'erreurs incomplet, aucun test) |
| T2 — Session chiffrée | partiel (`CstvSessionManager` posé ; deux clés de 4.3 manquantes, aucun test) |
| T3 — OTP et résolution de session | partiel (repository posé ; aucun use case, aucun test) |
| T4 — Room 25 → 26 | livré, mais migration défectueuse (C2) |
| T5 — Écrans email / OTP / bloqué | partiel (composables minimaux ; `strings.xml`, préremplissage, focus TV et tests absents) |
| T6 — Gate CSTV au démarrage | partiel (gate posé avant l'auto-login Xtream ; pas de `resolve` sur `ON_RESUME` ni au retour réseau) |
| T7 → T11 | non commencées |

Commandes exécutées :

- `./gradlew assembleDebug` — succès ;
- `./gradlew lintDebug` — **échec**, 4 erreurs `NewApi` (voir C1) ;
- `./gradlew testDebugUnitTest` — succès, mais aucun test ne couvre le code CSTV
  livré (seuls `SnapshotCodecTest` et `CstvEtagTest`, rattachés à F34, existent).

## Critique

### C1 — `java.time` sous `minSdk 21` : lint en échec et crash sur API 21-25

Description : `CstvAuthRepositoryImpl.kt:65` utilise `Instant.parse(...)` et
`toEpochMilli()` alors que `minSdk = 21` et que `coreLibraryDesugaring` n'est
pas activé dans `app/build.gradle.kts`. C'est le seul usage de `java.time` de
tout le module.

Impact : `lintDebug` échoue avec 4 erreurs `NewApi`, donc la checklist AGENTS.md
« `assembleDebug` + `lintDebug` verts » n'est pas satisfaite et
`scripts/release-local.sh` refuserait de livrer. Sur un appareil API 21-25, la
résolution de session lève `NoClassDefFoundError` : le gate étant obligatoire,
l'application est intégralement inutilisable sur ces versions.

Correction attendue : parser sans `java.time` le format émis par le backend
(`Y-m-d\TH:i:s\Z`, cf. `backend/src/Shared/DateFormatter.php`) — par exemple un
`SimpleDateFormat` en UTC dédié — ou activer le desugaring. Décision à acter
dans les notes. `lintDebug` doit repasser au vert.

### C2 — Migrations 25 → 26 et 26 → 27 : index non déclarés sur les entités

Description : `MIGRATION_25_26` crée `index_profiles_remoteId` et
`MIGRATION_26_27` crée `index_profile_sync_state_profileId`, mais ni
`ProfileEntity` ni `ProfileSyncStateEntity` ne déclarent d'`indices` dans leur
annotation `@Entity`. Room compare le schéma trouvé au schéma attendu à
l'ouverture, index compris (`TableInfo.equals` ne saute la comparaison que si
l'un des deux jeux d'index est `null`, ce qui n'arrive pas ici).

Impact : sur une **installation existante** qui migre, la base contient deux
index que le schéma généré n'attend pas →
`IllegalStateException: Migration didn't properly handle ...` au premier accès
Room, donc crash au démarrage. Une installation neuve ne reproduit pas le
problème (les index n'y sont jamais créés), ce qui rend le défaut invisible en
développement. Le projet n'ayant pas d'`androidTest`, aucun garde-fou
automatique ne l'aurait attrapé.

Correction attendue : déclarer les index sur les entités —
`@Entity(..., indices = [Index(value = ["remoteId"], unique = true)])` pour
`ProfileEntity` et `@Entity(..., indices = [Index("profileId")])` pour
`ProfileSyncStateEntity` — ou retirer les `CREATE INDEX` des migrations. Relire
ensuite le SQL des deux migrations contre le schéma généré, colonne par colonne
et index par index.

### C3 — `verifyOtp` détruit l'`account_id` mémorisé avant de pouvoir le comparer

Description : `CstvAuthRepositoryImpl.verifyOtp` enregistre
`CstvSession(token, accountId = null, ...)` **avant** d'appeler `/v1/me`, puis
`resolveSession()` réécrit l'`account_id` avec celui du compte fraîchement
authentifié. La seule trace du compte précédent est donc effacée au moment
exact où 5.4 (étape 4) et T9 en ont besoin.

Impact : la détection d'un changement de compte devient structurellement
impossible, et avec elle la purge. Le critère « Changer de compte CSTV purge
les profils et données de profil du compte précédent » et l'exigence
« les données de l'ancien compte ne doivent être visibles à aucun instant » ne
peuvent pas être satisfaits tant que cet ordre d'écriture reste en place.

Correction attendue : ne pas écraser `account_id`/`account_email` lors de la
sauvegarde du token (conserver les valeurs déjà stockées), puis comparer dans
`resolveSession()` l'identifiant reçu à celui mémorisé **avant** toute écriture,
et déclencher la purge transactionnelle avant que le gate ne rende la main. À
traiter avec T9.

### C4 — Aucun test sur le code CSTV livré

Description : les critères de validation de T1, T2, T3 et T5 exigent
`CstvErrorMapperTest`, `CstvSessionGuardInterceptorTest`,
`CstvSessionManagerTest`, `CstvAuthRepositoryImplTest` et
`CstvAuthViewModelTest`. Aucun de ces fichiers n'existe.

Impact : aucune des règles les plus sensibles du ticket n'est vérifiée —
mapping des refus `401`/`403`, expiration locale, `Offline` vs
`SignedOut(TokenExpired)`, double validation d'un OTP, isolation des deux
fichiers de préférences chiffrées, absence de secret dans les messages. La
stratégie de tests d'AGENTS.md rend cette couverture obligatoire.

Correction attendue : écrire les cinq classes de tests listées dans 5.9, avec
`@get:Rule Timeout.seconds(60)` pour le test de ViewModel. Voir aussi M7, qui
bloque aujourd'hui l'écriture du test de ViewModel.

## Majeur

### M1 — Le code d'erreur réel de l'OTP invalide n'est pas traité

Description : `CstvErrorMapper` ne connaît pas le statut `400`. Or le backend
renvoie **`400`** pour `INVALID_OTP`, `OTP_CONSUMED`, `OTP_EXPIRED` et
`OTP_ATTEMPTS_EXCEEDED` (`backend/src/Auth/AuthService.php:82-98`) ; `422` n'est
utilisé que pour `INVALID_OTP_FORMAT`.

Impact : le cas d'erreur le plus fréquent du parcours tombe dans
`else -> CstvError.Unknown()` et affiche « Une erreur est survenue. Réessayez. »
au lieu du message explicite exigé par la section 3 et le tableau 4.6.
Symétriquement, `422` est mappé aveuglément sur `InvalidOtp`, alors qu'il sert
aussi à la validation des profils et à `INVALID_SCHEMA_VERSION` (F34) : un
renommage de profil refusé afficherait « Le code est invalide ou a expiré ».

Correction attendue : mapper d'abord sur `error.code` (déjà lu par le mapper) et
n'utiliser le statut qu'en repli, conformément à 4.6 (« le code JSON prime sur
le statut »). Couvrir chaque ligne du tableau par un test.

### M2 — Tableau 4.6 incomplet et `CstvError` amputé

Description : `404` (`ProfileNotFound`), la distinction `503` / autres `5xx`
(`ServerError`) et l'ensemble des codes F34 (`412`, `413`, `415`, `428`) ne sont
ni mappés ni représentés dans la hiérarchie `CstvError`.

Impact : T1 ne satisfait pas son critère de validation (« couvre chaque ligne du
tableau 4.6 »), et F34/T5 ne pourra pas s'appuyer sur ce mapper — il devra soit
le contourner, soit le compléter en urgence.

Correction attendue : compléter `CstvError` et `CstvErrorMapper` sur tout le
tableau 4.6, y compris les codes consommés par F34.

### M3 — `CstvAuthInterceptor` n'échoue pas vite

Description : quand la session est absente ou localement expirée,
l'interceptor laisse partir la requête **sans** en-tête `Authorization` au lieu
de lever une `IOException` typée (4.5).

Impact : une requête inutile part sur le réseau ; le backend répond `401` ; le
`CstvSessionGuardInterceptor` purge le token et publie
`SignedOut(SessionRejected)`. L'utilisateur voit donc « Votre session n'est plus
valide » alors que 5.3 prévoit « Votre session a expiré, veuillez vous
reconnecter » pour un token expiré localement — deux messages que la spec
distingue explicitement.

Correction attendue : échouer avant émission pour les endpoints authentifiés,
avec une exception typée que le repository traduit en
`SignedOut(TokenExpired)`.

### M4 — L'état `Unknown` affiche le formulaire email

Description : `MainActivity` calcule
`cstvGateResolved = state is Active || state is Offline`. À froid, l'état
initial est `Unknown` : la branche `else` de `CstvGateScreen` rend donc
`CstvEmailScreen` pendant toute la durée de `/v1/me`.

Impact : à **chaque** lancement, y compris avec une session parfaitement valide,
l'utilisateur voit un formulaire de saisie d'email avant que le splash
n'apparaisse. La chaîne de 5.2 (`Splash → Gate CSTV`) est inversée. Combiné à
M5, l'exposition peut durer plusieurs dizaines de secondes sur un réseau
dégradé.

Correction attendue : traiter `Unknown` comme un état de chargement (splash),
et ne rendre les écrans du gate que sur `SignedOut(*)` ou `Blocked(*)`.

### M5 — Budget réseau du démarrage non respecté

Description : le client CSTV unique est configuré avec `connectTimeout` 15 s,
`readTimeout` 20 s et `callTimeout` 60 s — ce sont les valeurs prévues par F34
pour le transfert d'octets, pas celles de F33 (10 s / 10 s / 20 s). Par
ailleurs, `resolveSession()` appelle `/v1/me` sans consulter `NetworkMonitor`,
alors que 4.7 précise « en l'absence de réseau l'appel n'est même pas tenté ».

Impact : le gate peut retenir le démarrage jusqu'à une minute, sur un écran qui
est aujourd'hui le formulaire email (M4). La borne de 20 s annoncée en 4.7 n'est
pas tenue.

Correction attendue : conserver le client F33 à 20 s de `callTimeout` et laisser
F34 dériver le sien via `newBuilder()`. Court-circuiter l'appel et basculer
directement en `Offline` quand `NetworkMonitor` indique l'absence de réseau.

### M6 — `resolveSession()` ne capture que `IOException`

Description : `Instant.parse` (`DateTimeParseException`), le convertisseur Gson
(`JsonSyntaxException`) ou un champ `activeUntil` absent remontent en exception
non gérée hors du `try`.

Impact : une réponse malformée ou un changement de format côté backend crashe
l'application au démarrage au lieu de produire un état d'erreur, ce que la
section 3 interdit explicitement (« réponse API gérée sans crash »).

Correction attendue : traiter tout échec de désérialisation comme une réponse
inexploitable (`ServerError`/`Offline` selon le cas) et le couvrir par un test.

### M7 — `android.util.Patterns` rend le test de ViewModel impossible

Description : `CstvAuthViewModel.requestOtp()` valide l'email avec
`android.util.Patterns.EMAIL_ADDRESS`, classe du framework Android non
instrumentée en test JVM (`Patterns.EMAIL_ADDRESS` y vaut `null` →
`NullPointerException`).

Impact : `CstvAuthViewModelTest`, exigé par T5, ne peut pas être écrit sans
Robolectric ou un mock de classe statique, ce que la stratégie de tests du
projet exclut (tests JVM purs uniquement).

Correction attendue : remplacer par une `Regex` Kotlin dans la couche
présentation ou domaine, testable sans device.

### M8 — Libellés en dur, y compris dans la couche domaine

Description : tous les textes de `CstvGateScreens.kt` sont écrits en dur, et
`CstvError` porte directement les messages utilisateur français
(`userMessage`). Aucune entrée n'a été ajoutée à `strings.xml`, alors que T5
l'impose et que l'application déclare `resourceConfigurations = ["fr", "en"]`.

Impact : écrans du gate non traduits en anglais, et couche `domain` porteuse de
libellés d'interface — contraire à la séparation Clean Architecture appliquée
partout ailleurs dans le projet.

Correction attendue : déplacer les libellés dans `strings.xml` et faire porter
à `CstvError` un identifiant de ressource ou un type d'erreur, la traduction
étant résolue côté présentation.

### M9 — Écrans du gate incomplets vis-à-vis de 5.3

Description : `SignedOut.reason` n'est jamais lu par l'interface. Manquent en
conséquence le préremplissage de l'email en `SignedOut(TokenExpired)`, les
messages distincts « session expirée » / « session plus valide », l'action
« demander un nouveau code », le retour de l'écran OTP vers l'écran email, et
l'indicateur hors ligne non bloquant de l'état `Offline`.

Impact : le tableau des sorties possibles de 5.3 n'est pas réalisé ; un
utilisateur dont la session a expiré doit ressaisir son email, ce que la spec
cherchait justement à éviter.

Correction attendue : câbler `SignedOutReason` sur l'état d'interface, alimenter
l'email depuis `account_email`, et ajouter les actions manquantes.

### M10 — Aucune gestion du focus sur Android TV

Description : `CstvGateScreens.kt` utilise les composants Material 3 mobiles
sans `Modifier.focusRequester` ni point d'entrée de focus, alors que 4.2 prévoit
explicitement « focus TV géré par `Modifier.focusRequester` ».

Impact : sur Android TV, aucun élément n'a le focus à l'ouverture du gate ; le
premier appui directionnel est perdu et la saisie de l'email n'est pas
atteignable de façon déterministe. Le gate étant obligatoire, le risque porte
sur le démarrage complet de l'application TV.

Correction attendue : poser le focus initial sur le champ de saisie et vérifier
l'ordre de navigation D-pad, comme le fait déjà `LoginScreen`.

### M11 — Repli d'URL de développement embarqué dans le binaire

Description : `provideCstvRetrofit` retombe silencieusement sur
`http://10.0.2.2:18080/` quand `BuildConfig.CSTV_BASE_URL` est vide.

Impact : contrairement à `TMDB_API_KEY`, dont l'absence n'entraîne qu'un repli
silencieux sur une fonctionnalité optionnelle, l'URL CSTV conditionne un gate
**obligatoire**. Une release construite sans la propriété produirait une
application qui interroge l'adresse de bouclage de l'émulateur, échoue toujours,
et bascule en `Offline` sans jamais permettre de valider un OTP — panne
silencieuse et difficile à diagnostiquer.

Correction attendue : rendre l'absence de `CSTV_BASE_URL` explicite (échec de
build en release, ou état d'erreur non technique dédié au démarrage).

### M12 — Périmètre non livré : T7 à T11

Description : `ProfileRepository` est inchangé (mutations non faillibles, pas de
`purgeAllProfiles()`), `ensureInitialized()` crée toujours `Profil 1`
localement, et `CstvProfileGateway`, `ProfileCloudReconciler`,
`CloudProfileEmptinessProbe`, la section « Compte CSTV » des Paramètres et les
trois use cases de 4.2 (`RequestOtpUseCase`, `VerifyOtpUseCase`,
`ResolveCstvSessionUseCase`) n'existent pas — le ViewModel appelle le repository
directement.

Impact : la fusion initiale des profils, le CRUD confirmé par le backend, la
purge au changement de compte et la déconnexion CSTV distincte sont absents ;
au moins huit des quinze critères d'acceptation de la section 3 restent
inatteignables. La review de ces parties devra être refaite après livraison.

Correction attendue : livrer T7 à T11 puis repasser une review ciblée sur ces
tâches.

## Mineur

- **m1** — Fichier de préférences nommé `cstv_session_shared_prefs` au lieu de
  `cstv_session_prefs` (4.3) : sans impact fonctionnel, mais l'écart doit être
  soit corrigé, soit acté dans la spec.
- **m2** — Clés `account_active_until` et `last_me_success_at` de 4.3 non
  persistées : T10 ne pourra pas afficher la validité du compte dans les
  Paramètres, et aucun horodatage de dernière vérification serveur n'est
  disponible.
- **m3** — `CstvSessionStateHolder` et `CstvErrorMapper` sont des classes
  finales injectées : conformément à la règle Mockito d'AGENTS.md, prévoir une
  interface si un test doit les substituer.
- **m4** — `CstvSessionGuardInterceptor` détecte `ACCOUNT_EXPIRED` par un
  `contains()` sur le corps brut ; réutiliser le décodage de `error.code` déjà
  présent dans `CstvErrorMapper` serait plus sûr et éviterait de lire deux fois
  le corps.
- **m5** — Les trois écrans prévus en 4.2 (`CstvEmailScreen.kt`,
  `CstvOtpScreen.kt`, `CstvBlockedScreen.kt`) sont regroupés dans un unique
  `CstvGateScreens.kt` : sans conséquence technique, à aligner ou à acter.
- **m6** — `CstvError.Unavailable` sert à la fois l'absence de réseau et les
  `5xx`, avec le message « Une connexion internet est nécessaire. » : trompeur
  lors d'une panne serveur (voir M2, qui introduit `ServerError`).
- **m7** — En cas de bascule en `Offline` juste après un OTP valide,
  `verifyOtp` renvoie un échec `Unavailable` alors que la session est
  enregistrée et que le gate est franchi : l'utilisateur voit un message
  d'erreur sur un parcours qui a réussi.
- **m8** — AGENTS.md n'est pas à jour : la base Room y est documentée en version
  **25** alors qu'elle passe à **27**, et ni `ProfileSyncStateEntity` ni les deux
  nouvelles migrations n'y figurent.
- **m9** — La modification de `backend/docker-compose.yml` (exposition de
  PostgreSQL sur `127.0.0.1:15432`) est hors périmètre de F33 : à sortir du lot
  de livraison ou à justifier dans les notes.
- **m10** — Les cases du plan de développement (section 6) sont toutes
  décochées alors que T1 à T6 sont partiellement livrées : le suivi doit refléter
  l'état réel après correction.

## Corrections demandées

Ordre conseillé pour l'étape 7 :

1. C1 — supprimer l'usage de `java.time`, remettre `lintDebug` au vert.
2. C2 — déclarer les index sur les entités et relire les deux migrations.
3. C3 — corriger l'ordre d'écriture de `account_id` (avec T9).
4. M1, M2, M6 — mapping d'erreurs complet et robuste, avec tests.
5. M3, M4, M5 — fail-fast de l'interceptor, état `Unknown` en chargement,
   timeouts et court-circuit hors ligne.
6. M7, M8, M9, M10 — validation d'email testable, `strings.xml`, écrans conformes
   à 5.3, focus TV.
7. M11 — traitement explicite de l'absence de `CSTV_BASE_URL`.
8. C4 — écrire les cinq classes de tests de 5.9.
9. m1 → m10 — l'ensemble des points mineurs, y compris la mise à jour d'AGENTS.md.
10. M12 — livrer T7 à T11, puis nouvelle review ciblée.

---

# 9. Release

Version :

Commit :

Date :
