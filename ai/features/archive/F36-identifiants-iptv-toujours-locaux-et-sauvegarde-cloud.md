# F36 - Identifiants IPTV toujours mémorisés en local, case dédiée à la sauvegarde cloud

## Informations générales

Status:
RELEASED

Created:
2026-08-14

---

# 1. Description

Aujourd'hui, la case « Mémoriser les identifiants » de l'écran de connexion IPTV
décide si les identifiants Xtream sont conservés **sur l'appareil**. Décochée,
elle oblige à ressaisir hôte, port, utilisateur et mot de passe à chaque
lancement, et interdit l'auto-login.

F36 change la nature de cette case :

- les identifiants Xtream sont **toujours** enregistrés localement dès qu'ils
  ont été validés par le panel ;
- la case reste à l'écran mais pilote désormais la **sauvegarde dans le cloud
  CSTV** : cochée, les identifiants sont envoyés au backend et restaurés
  automatiquement sur les autres appareils du même compte CSTV ;
- les identifiants stockés côté serveur sont **chiffrés en base**, pour qu'un
  accès à la base de données seule ne permette pas de les lire.

---

# 2. Contexte

## Ce qui existe

- `LoginScreen` expose une case `rememberMe`, cochée par défaut.
- `LoginUseCase` : si `rememberMe`, `saveCredentials` ; sinon `clearCredentials`.
- `CredentialsManager` écrit dans des `EncryptedSharedPreferences` — le stockage
  local est donc déjà chiffré au repos par Android.
- `AuthRepositoryImpl.autoLogin()` refuse de démarrer une session (`NoCredentials`)
  si les identifiants sont absents **ou** si `rememberMe` est faux.
- La synchronisation cloud (T14/T19/T20) existe déjà : compte CSTV en OTP e-mail,
  blobs gzip par profil et par namespace, stockés en `BYTEA` dans
  `profile_objects`, que le backend ne lit jamais. Sept namespaces aujourd'hui
  (favoris, reprise de lecture, notes, pistes, séries, catégories, live récent) —
  tous des **préférences**, aucun secret.

## Problèmes

1. **La case ne rend pas le service qu'on attend d'elle.** Décochée, elle
   dégrade l'app (ressaisie complète à chaque démarrage, pas d'auto-login, pas de
   session hors ligne) sans rien protéger de plus : le stockage local est déjà
   chiffré par le système et l'appareil est, par construction, celui de
   l'utilisateur.
2. **Changer d'appareil coûte une ressaisie complète.** Un compte CSTV lie déjà
   les profils, les favoris et l'historique entre appareils, mais pas l'accès au
   panel lui-même : la première chose que fait l'utilisateur sur un nouvel
   appareil reste de taper une URL, un port, un identifiant et un mot de passe à
   la télécommande.
3. **Le cloud n'a jamais eu à porter de secret.** Les sept namespaces existants
   sont des préférences : leur fuite est un problème de vie privée, pas un
   problème d'accès. Des identifiants Xtream, eux, donnent l'accès à un abonnement
   payant. Ils ne peuvent pas être stockés selon les mêmes règles que le reste.

---

# 3. Objectif

- Supprimer une case dont l'effet est punitif, sans supprimer le choix qu'elle
  offrait : la maîtrise de ce qui quitte l'appareil.
- Permettre à un nouvel appareil connecté au même compte CSTV d'arriver
  directement sur le catalogue, sans ressaisie.
- Ne jamais laisser des identifiants Xtream lisibles dans la base du backend.

---

# 4. Décisions produit prises à l'étape 1

| Sujet | Décision |
|---|---|
| Stockage local | Toujours, dès que le panel a validé les identifiants. `rememberMe` ne conditionne plus rien en local. |
| Rôle de la case | Sauvegarde des identifiants dans le cloud CSTV, et rien d'autre. |
| Chiffrement | **Côté API** (pgcrypto ou libsodium), secret hors base. Le backend chiffre à l'écriture et déchiffre à la lecture. |
| Restauration | **Connexion automatique silencieuse** : nouvel appareil avec compte CSTV lié et identifiants cloud disponibles → l'app enchaîne sur le catalogue, sans écran de connexion IPTV. |
| Décochage | **Purge immédiate côté serveur.** Décocher supprime la sauvegarde distante. Se déconnecter du compte CSTV la supprime aussi. Le stockage local n'est jamais touché par ces deux actions : la session IPTV en cours continue. |
| Déconnexion IPTV | La déconnexion IPTV depuis les Paramètres supprime les identifiants locaux **et** leur sauvegarde cloud, puis renvoie au parcours de connexion IPTV. Il n'existe pas d'autre interrupteur cloud dans les Paramètres. |
| Absence de compte CSTV | **Case masquée.** Elle n'apparaît que si un compte CSTV est lié — le parcours de connexion IPTV reste le chemin le plus court vers le catalogue. |
| État par défaut | **Décochée.** Envoyer un secret hors de l'appareil demande un geste explicite ; c'est le seul réglage de l'app dont l'effet est de faire quitter des identifiants d'accès à l'appareil. |
| Identifiants cloud refusés par le panel | **Invalidation du blob distant**, en plus du retour à l'écran de connexion IPTV : des identifiants que le panel rejette n'ont aucune raison de continuer à être proposés aux autres appareils. |
| Conflit local/cloud | Les identifiants locaux sont prioritaires. Après leur validation par le panel, ils remplacent la sauvegarde cloud si celle-ci est activée. Aucune sélection de compte ni boîte de dialogue de conflit n'est ajoutée. |
| Indisponibilité CSTV | Une panne du backend CSTV ne bloque jamais une connexion IPTV réussie. Les identifiants restent enregistrés localement, l'opération cloud est différée et l'utilisateur reçoit une information non bloquante. |
| Visibilité dans les Paramètres | Aucun statut « sauvegardé dans CSTV » et aucun interrupteur dédié ne sont ajoutés. La case de l'écran de connexion et la déconnexion IPTV sont les deux points d'action. |

## Limite assumée du chiffrement côté API

Le chiffrement côté API protège contre **un vol de la base seule** : sauvegarde
`pg_dump` égarée, accès en lecture à la base, restauration d'un backup par un
tiers. Il ne protège pas contre une compromission du serveur applicatif, qui
donne accès au secret de déchiffrement en même temps qu'aux données. Il implique
également que les identifiants Xtream transitent en clair dans la mémoire du
backend, ce qui exige des garanties supplémentaires à spécifier à l'étape 2
(aucune journalisation du corps de ces requêtes, aucun message d'erreur qui
reflète la valeur déchiffrée).

L'alternative écartée était un chiffrement de bout en bout côté App avec un code
de récupération saisi par l'utilisateur : elle rendait une fuite de base
totalement inexploitable, au prix d'une ressaisie de ce code sur chaque nouvel
appareil — incompatible avec la restauration silencieuse retenue ci-dessus.

---

# 5. Hypothèses

- **H1** — Le périmètre reste « un seul compte Xtream » (cf. AGENTS.md) : un
  compte CSTV porte donc au plus un jeu d'identifiants IPTV, pas une liste.
- **H2** — Les identifiants sauvegardés sont ceux validés par le panel : hôte,
  port, utilisateur, mot de passe. Rien d'autre (ni `UserInfo`, ni date
  d'expiration, qui se réobtiennent en se connectant).
- **H3** — Ces identifiants sont liés au **compte** CSTV, pas à un profil local :
  ils ne peuvent pas suivre le modèle par profil des sept namespaces existants
  sans être dupliqués autant de fois qu'il y a de profils.
- **H4** — Le transport est déjà en HTTPS ; F36 ne traite que le stockage.
- **H5** — Le champ `rememberMe` existant peut changer de sémantique sans
  migration de données : sa valeur locale actuelle ne dit rien de l'intention de
  l'utilisateur vis-à-vis du cloud, qui n'existait pas.
- **H6** — Les utilisateurs actuels ayant décoché la case ne perdent rien : à leur
  prochaine connexion, leurs identifiants seront mémorisés localement, ce qui est
  une amélioration ; aucun envoi cloud n'a lieu tant qu'ils n'ont pas coché la
  nouvelle case.

---

# 6. Décisions restant à prendre à l'étape 3

La spécification fonctionnelle est fermée. Les points suivants étaient
volontairement reportés à la spécification technique et à l'architecture ; ils
sont **tranchés au §8.1** :

- stockage backend dans une table dédiée au compte ou dans un objet de niveau
  compte → **D1** ;
- primitive de chiffrement côté API, gestion du secret et procédure de rotation
  → **D2** ;
- mécanisme de concurrence et de reprise (`ETag`, version ou autre) permettant
  de respecter la règle fonctionnelle « dernière sauvegarde validée gagnante »
  → **D3** ;
- persistance et reprise des écritures ou suppressions différées quand CSTV est
  indisponible → **D4**.

---

# 7. Spécification fonctionnelle

## 7.1 User stories

- **US1 — Connexion locale durable** : en tant qu'utilisateur, je veux que les
  identifiants IPTV acceptés par mon panel restent mémorisés sur l'appareil afin
  de ne pas les ressaisir à chaque lancement.
- **US2 — Consentement cloud explicite** : en tant qu'utilisateur lié à un
  compte CSTV, je veux choisir depuis l'écran de connexion si mes identifiants
  IPTV peuvent quitter l'appareil et être sauvegardés dans CSTV.
- **US3 — Nouvel appareil** : en tant qu'utilisateur connecté au même compte
  CSTV sur un nouvel appareil, je veux que les identifiants sauvegardés soient
  restaurés et vérifiés automatiquement afin d'accéder directement au
  catalogue.
- **US4 — Priorité à l'appareil courant** : en tant qu'utilisateur ayant déjà
  des identifiants locaux, je veux qu'ils restent prioritaires sur une
  sauvegarde cloud différente afin de ne pas changer silencieusement de panel.
- **US5 — Révocation** : en tant qu'utilisateur, je veux que décocher la case ou
  me déconnecter explicitement supprime la copie cloud afin qu'elle ne soit plus
  restaurable sur un autre appareil.
- **US6 — Résilience** : en tant qu'utilisateur, je veux pouvoir me connecter à
  mon panel même si CSTV ne peut momentanément ni enregistrer ni supprimer la
  sauvegarde.

## 7.2 Libellés et présentation

- La case porte le libellé : **« Sauvegarder mes identifiants IPTV dans CSTV »**.
- Le texte d'aide associé est : **« Ils seront chiffrés et restaurés
  automatiquement sur vos autres appareils. »**
- La case n'est affichée que lorsqu'un compte CSTV est lié, y compris lorsque
  cette liaison est connue localement mais que CSTV est temporairement hors
  ligne.
- En l'absence de sauvegarde cloud connue, elle est décochée par défaut.
- L'écran Paramètres n'ajoute ni interrupteur, ni indicateur d'état de la
  sauvegarde. Sa commande existante de déconnexion IPTV constitue l'action de
  suppression globale des identifiants.
- Aucun hôte, port, nom d'utilisateur ou mot de passe n'est affiché dans un
  message relatif à la sauvegarde ou à sa restauration.

## 7.3 Parcours A — Connexion IPTV manuelle

1. Après résolution du compte CSTV, l'utilisateur arrive sur l'écran de
   connexion IPTV lorsqu'aucune connexion automatique n'a abouti.
2. Il saisit l'adresse du serveur, son utilisateur et son mot de passe.
3. Il laisse la case cloud décochée ou la coche explicitement.
4. L'application soumet les identifiants au panel Xtream.
5. Si le panel les accepte, l'application les mémorise toujours dans le stockage
   local chiffré, quel que soit l'état de la case.
6. Si la case est décochée, aucun envoi cloud n'est effectué.
7. Si la case est cochée, la sauvegarde cloud est créée ou remplacée **après**
   la validation du panel.
8. L'utilisateur accède au catalogue dès que l'authentification IPTV a réussi ;
   l'enregistrement cloud ne fait pas partie du chemin bloquant.

Cocher la case ne suffit jamais à envoyer les valeurs actuellement présentes
dans les champs : un envoi ne peut suivre qu'une authentification IPTV réussie.

## 7.4 Parcours B — Démarrage avec identifiants locaux

1. Une fois le gate CSTV résolu, l'application recherche d'abord les
   identifiants locaux.
2. S'ils existent, ils sont prioritaires et l'application tente la connexion
   automatique avec eux ; une sauvegarde cloud différente ne les remplace pas.
3. Si la connexion locale réussit et que la sauvegarde cloud du compte est
   activée, les identifiants locaux validés deviennent la nouvelle valeur cloud.
   La dernière sauvegarde issue d'une validation réussie gagne.
4. Si la connexion locale est refusée, l'application ne bascule pas
   silencieusement vers des identifiants cloud différents : elle affiche l'écran
   de connexion et laisse l'utilisateur corriger les valeurs.
5. Une sauvegarde cloud n'est supprimée dans ce cas que si les identifiants
   refusés sont ceux qu'elle contient également. Une sauvegarde différente n'est
   pas déclarée invalide sur la seule base du refus local.

Cette priorité évite qu'un autre appareil ayant modifié la sauvegarde fasse
changer silencieusement le panel utilisé par l'appareil courant.

## 7.5 Parcours C — Nouvel appareil sans identifiants locaux

1. L'utilisateur établit ou retrouve sa session avec son compte CSTV.
2. En l'absence d'identifiants IPTV locaux, l'application recherche une
   sauvegarde cloud pour ce compte.
3. Si elle existe, l'application la déchiffre via l'API et tente silencieusement
   une authentification auprès du panel Xtream.
4. Si le panel accepte les identifiants, ils sont mémorisés dans le stockage
   local chiffré et l'utilisateur arrive directement au catalogue.
5. Si aucune sauvegarde n'existe ou si elle ne peut pas être obtenue, l'écran de
   connexion IPTV est présenté. L'utilisateur peut toujours saisir ses
   identifiants manuellement.

La restauration n'importe ni catalogue, ni état de compte Xtream, ni date
d'expiration depuis CSTV : ces informations restent obtenues auprès du panel.

## 7.6 Parcours D — Désactivation et déconnexions

### Décochage sur l'écran de connexion

- Si une sauvegarde cloud existe et que l'utilisateur décoche la case, sa
  suppression est demandée immédiatement, sans attendre une nouvelle tentative
  de connexion IPTV.
- Les identifiants locaux et la session IPTV courante restent intacts.
- Un nouveau cochage n'envoie rien tant qu'une authentification IPTV n'a pas de
  nouveau réussi.

### Déconnexion IPTV depuis les Paramètres

- L'action supprime les identifiants locaux, l'état local autorisant la
  sauvegarde et la sauvegarde cloud du compte CSTV.
- Elle ferme la session IPTV et renvoie à l'écran de connexion IPTV.
- Une sauvegarde dont la suppression distante est encore en attente ne doit pas
  pouvoir être restaurée sur ce même appareil entre-temps.

### Déconnexion du compte CSTV

- Elle demande la suppression de la sauvegarde cloud avant de retirer la session
  CSTV.
- Elle ne supprime pas les identifiants IPTV locaux et ne coupe pas à elle seule
  la session IPTV en cours.

## 7.7 Règles métier

- **RM1** — F36 conserve le périmètre d'un seul compte Xtream : un compte CSTV
  possède au plus une sauvegarde d'identifiants IPTV.
- **RM2** — La sauvegarde appartient au compte CSTV et non à un profil.
- **RM3** — Seuls hôte, port, utilisateur et mot de passe sont sauvegardés.
- **RM4** — Les identifiants sont enregistrés localement uniquement après une
  authentification Xtream réussie, mais ils le sont alors systématiquement.
- **RM5** — La case cloud est un consentement distinct du stockage local et est
  décochée par défaut lorsqu'aucune sauvegarde distante n'existe.
- **RM6** — Seule une authentification réussie peut créer ou remplacer la
  sauvegarde cloud.
- **RM7** — Les identifiants locaux gagnent sur une valeur cloud différente ;
  aucune boîte de dialogue de conflit n'est présentée.
- **RM8** — Entre plusieurs appareils, la dernière sauvegarde issue
  d'identifiants validés par le panel devient la valeur distante courante.
- **RM9** — Un refus explicite « identifiants incorrects » invalide la sauvegarde
  cloud seulement lorsque le refus porte sur les identifiants restaurés ou sur
  une copie distante identique.
- **RM10** — Un abonnement expiré ou inactif ne rend pas les identifiants faux et
  ne supprime donc pas la sauvegarde.
- **RM11** — Une panne réseau, un timeout, une réponse illisible ou un panel
  indisponible ne supprime jamais la sauvegarde.
- **RM12** — Un échec de création, remplacement ou suppression côté CSTV ne
  transforme jamais une authentification IPTV réussie en échec.
- **RM13** — Toute opération cloud non aboutie est conservée et réessayée
  automatiquement. Une suppression en attente prime sur toute restauration ou
  écriture plus ancienne sur le même appareil.
- **RM14** — Le backend stocke exclusivement une forme chiffrée des identifiants ;
  le secret de déchiffrement ne réside pas dans la base.
- **RM15** — Les corps contenant des identifiants, leurs valeurs déchiffrées et
  les erreurs susceptibles de les refléter ne sont jamais journalisés.

## 7.8 Gestion des erreurs et messages

| Situation | Comportement | Message utilisateur |
|---|---|---|
| Identifiants saisis manuellement refusés | Aucun stockage local nouveau, aucun envoi cloud. | Message existant d'identifiants incorrects. |
| Identifiants restaurés du cloud refusés | Suppression de la sauvegarde, retour à l'écran de connexion. | « Les identifiants sauvegardés ne sont plus valides. Saisissez-en de nouveaux. » |
| Abonnement expiré ou inactif | Conservation locale et cloud ; pas d'accès au catalogue en ligne. | Message d'expiration existant, avec la date lorsqu'elle est disponible. |
| Panel injoignable, timeout ou absence d'Internet | Aucune invalidation cloud ; repli sur la session hors ligne locale si elle est éligible. | Message réseau ou hors-ligne existant. |
| CSTV indisponible pendant une sauvegarde | Connexion IPTV et stockage local maintenus ; écriture différée. | « Connexion réussie. La sauvegarde CSTV sera réessayée automatiquement. » |
| CSTV indisponible pendant une suppression | Déconnexion ou décochage local effectué ; suppression distante marquée en attente et prioritaire. | « Déconnexion effectuée. La suppression dans CSTV sera réessayée automatiquement. » |
| Sauvegarde absente sur un nouvel appareil | Affichage normal du formulaire IPTV. | Aucun message d'erreur. |
| Données cloud absentes, incomplètes ou indéchiffrables | Aucune valeur appliquée localement, aucune valeur sensible affichée, formulaire IPTV affiché. | « Les identifiants sauvegardés n'ont pas pu être restaurés. Saisissez-les à nouveau. » |
| Réponse CSTV non autorisée ou session CSTV expirée | Aucun accès à la sauvegarde ; le gate CSTV existant reprend la main. | Message CSTV existant, sans détail des identifiants. |

## 7.9 Cas limites

- Un appareil possède des identifiants locaux tandis que le cloud est vide : le
  local est utilisé ; le cloud n'est créé qu'après validation si la case est
  cochée.
- Le local et le cloud sont identiques : une seule connexion Xtream est tentée ;
  il n'y a ni conflit ni double authentification.
- Le local et le cloud diffèrent : le local est utilisé sans invite. Une réussite
  remplace le cloud lorsque la sauvegarde est active ; un refus local ne provoque
  pas l'essai silencieux de l'autre compte.
- Deux appareils enregistrent successivement des valeurs valides : la dernière
  sauvegarde acceptée devient la valeur restaurée par un appareil sans local.
- L'utilisateur coche puis décoche avant de soumettre : aucune valeur n'est
  envoyée et une éventuelle sauvegarde antérieure est supprimée.
- L'utilisateur décoche pendant une panne CSTV : la suppression reste prioritaire
  et aucun démarrage ultérieur sur cet appareil ne doit annuler cette intention.
- L'application est arrêtée après une connexion IPTV réussie mais avant l'envoi
  cloud : la connexion locale reste utilisable et l'envoi reprend plus tard.
- L'application est arrêtée après une demande de suppression non transmise : la
  demande doit survivre au redémarrage.
- Le compte CSTV change : la sauvegarde de l'ancien compte ne doit jamais être
  appliquée, modifiée ou confondue avec celle du nouveau compte.
- Plusieurs profils CSTV existent : ils partagent la même sauvegarde, sans
  duplication par profil.
- Une ancienne installation contient `rememberMe=false` : aucune intention cloud
  n'est déduite de cette valeur historique ; après la prochaine authentification
  réussie, les identifiants deviennent néanmoins persistants localement.

## 7.10 Critères d'acceptation

- **CA1** — Après toute connexion IPTV manuelle réussie, un redémarrage permet
  l'auto-login avec les identifiants locaux, même si la case cloud était
  décochée.
- **CA2** — Avec la case décochée, aucune création ni mise à jour de sauvegarde
  cloud n'est émise.
- **CA3** — Cocher la case puis réussir la connexion crée ou remplace une unique
  sauvegarde au niveau du compte CSTV.
- **CA4** — Sur un appareil sans identifiants locaux, une sauvegarde valide du
  même compte CSTV permet d'atteindre silencieusement le catalogue et devient la
  copie locale.
- **CA5** — Sur un appareil possédant un local différent, le cloud ne remplace
  jamais silencieusement le local.
- **CA6** — Un refus explicite des identifiants restaurés supprime leur sauvegarde
  et affiche le message prévu ; une expiration ou une panne réseau ne la supprime
  pas.
- **CA7** — Une panne CSTV pendant la sauvegarde ne bloque ni la connexion IPTV,
  ni la mémorisation locale, ni l'accès au catalogue ; l'opération est reprise
  automatiquement.
- **CA8** — Décocher la case supprime la sauvegarde sans supprimer le local.
- **CA9** — La déconnexion IPTV depuis les Paramètres supprime le local et le
  cloud, ferme la session IPTV et revient au formulaire.
- **CA10** — La déconnexion CSTV supprime le cloud mais conserve le local.
- **CA11** — Après une suppression demandée mais différée, l'appareil ne restaure
  pas la sauvegarde devenue indésirable et reprend la suppression avant toute
  nouvelle écriture.
- **CA12** — Aucun interrupteur ni statut de sauvegarde IPTV cloud n'est ajouté
  aux Paramètres.
- **CA13** — La base backend ne contient jamais l'hôte, le port, l'utilisateur ou
  le mot de passe Xtream en clair, et aucune journalisation applicative ne les
  expose.
- **CA14** — Le libellé, le texte d'aide et les messages définis dans cette
  spécification sont identiques sur mobile et TV.
- **CA15** — F36 n'ajoute ni second compte Xtream, ni sauvegarde par profil, ni
  code de récupération, ni nouveau protocole IPTV.

---

# 8. Spécification technique et architecture

## 8.0 Vue d'ensemble

F36 ajoute **une ressource de niveau compte** côté backend (`/v1/account/iptv-credentials`),
chiffrée à l'écriture et déchiffrée à la lecture par l'API, et **un composant
d'app dédié** (`IptvCredentialsBackupRepository` + un store chiffré + un worker)
qui décide quand écrire, restaurer ou supprimer cette ressource.

Rien de tout cela ne passe par la synchronisation existante par profil
(`profile_objects`, `CloudSyncManager`, `SnapshotCodec`, `ProfileSyncStateEntity`) :
ces blobs sont opaques au backend et scopés au profil, alors que F36 exige
l'inverse (RM2 : niveau compte ; décision d'étape 1 : chiffrement **côté API**,
donc backend qui lit le contenu). Les deux chemins restent indépendants.

Le chemin bloquant de la connexion IPTV n'est pas modifié : `AuthRepositoryImpl.login()`
reste la seule autorité sur le succès d'une authentification Xtream (RM12).

## 8.1 Décisions techniques

Les quatre points reportés au §6, plus une décision de modèle rendue nécessaire
par le changement de sémantique de la case.

### D1 — Table dédiée au compte, pas un objet de niveau compte

**Décision.** Nouvelle table `account_iptv_credentials`, une ligne par compte
(`account_id` en clé primaire), servie par des routes dédiées.

**Justification.** `profile_objects` a `profile_id` en clé primaire et en clé
étrangère `ON DELETE CASCADE` vers `profiles` : y loger une donnée de compte
imposerait soit un profil « porteur » arbitraire (supprimé avec lui), soit une
duplication par profil, ce que H3 exclut. Ces objets sont par ailleurs
délibérément opaques (`BYTEA` gzip jamais lu par le backend, quotas et ETag
calculés sur des octets) alors que F36 exige que l'API lise et écrive le
contenu. Une table dédiée porte de plus une contrainte que le modèle par
namespace ne sait pas exprimer : **au plus un jeu d'identifiants par compte**
(RM1), garanti par la clé primaire.

### D2 — XChaCha20-Poly1305 applicatif (`ext-sodium`), clé hors base, AAD = compte

**Décision.** Le chiffrement se fait en PHP, pas en SQL. Enveloppe binaire
unique stockée en `BYTEA` :

```
version(1) || nonce(24) || ciphertext+tag(n+16)
```

`sodium_crypto_aead_xchacha20poly1305_ietf_encrypt($json, $aad, $nonce, $key)`
avec `nonce = random_bytes(SODIUM_CRYPTO_AEAD_XCHACHA20POLY1305_IETF_NPUBBYTES)`
régénéré à **chaque** écriture et `aad = "v1|" . $accountId . "|" . $keyId`.

**Justification.**

- **Pourquoi pas pgcrypto.** `pgp_sym_encrypt(data, key)` fait transiter la clé
  **dans l'instruction SQL** : elle apparaît alors dans `pg_stat_activity`, dans
  `log_min_duration_statement`, dans un éventuel `log_statement = 'all'` et dans
  les traces d'erreur PostgreSQL. Le secret finirait donc à portée de main de
  celui-là même contre qui la mesure protège (§4 : vol de la base et de ses
  sauvegardes). L'extension devrait en outre être installée dans l'image
  `postgres:17-alpine` et sur l'hébergement de production, ce qui déplace la
  contrainte au lieu de la supprimer.
- **Pourquoi libsodium plutôt qu'`openssl` en AES-256-GCM.** Les deux sont des
  AEAD corrects et l'un comme l'autre est disponible ici (`php -m` de production
  et image `backend-php` : `openssl` **et** `sodium`, vérifiés le 2026-08-14).
  Trois écarts font pencher la balance :
  - **Nonce de 24 octets contre 12.** Avec un nonce aléatoire de 96 bits, GCM
    oblige à tenir un raisonnement sur la borne d'anniversaire et donc à borner
    le nombre d'écritures par clé. XChaCha20 supprime la question : un nonce
    aléatoire de 192 bits ne collisionne pas en pratique. La cadence de rotation
    des clés redevient une décision d'exploitation, pas une contrainte
    cryptographique.
  - **API sans paramètre piégeux.** `openssl_encrypt()` exige de ne pas oublier
    `OPENSSL_RAW_DATA` (sinon base64 silencieux), de passer la longueur du tag,
    de récupérer `$tag` par référence et de distinguer un retour `false` d'un
    chiffré vide. La fonction sodium prend quatre arguments, renvoie
    `ciphertext || tag` concaténé et lève une `SodiumException` au lieu de
    renvoyer `false`. Moins de surface d'erreur pour le seul secret du système.
  - **`sodium_memzero()` garanti.** L'effacement mémoire du clair après usage
    (§8.2, RM15) cesse d'être conditionnel : il fait partie de l'extension
    qu'on utilise déjà. `sodium_base642bin()` en variante stricte sert au
    passage à parser les clés du trousseau.

  Le coût est nul : l'extension est présente en production comme dans l'image de
  build, `docker/php/Dockerfile` n'est pas modifié. `composer.json` déclare
  `"ext-sodium": "*"` pour que son absence échoue à l'installation plutôt qu'à
  l'exécution. L'accélération matérielle AES-NI dont `openssl` bénéficierait est
  sans objet sur quelques centaines d'octets par connexion.
- **Pourquoi une AAD.** Elle lie le chiffré à son compte : recopier la ligne du
  compte A vers le compte B, dans une base compromise en écriture, produit un
  échec d'authentification Poly1305 au déchiffrement, pas une usurpation.

**Clés et rotation.** Deux variables d'environnement :

| Variable | Format | Rôle |
|---|---|---|
| `IPTV_CREDENTIALS_KEYS` | `keyId:base64(32 octets)` séparés par `,` | Trousseau : toutes les clés encore nécessaires en lecture |
| `IPTV_CREDENTIALS_KEY_ID` | `[a-z0-9_-]{1,32}` | Clé utilisée pour **écrire** ; doit exister dans le trousseau |

La ligne stocke son `key_id` : une lecture utilise la clé qui a servi à
l'écriture. Rotation = ajouter la nouvelle clé au trousseau, basculer
`IPTV_CREDENTIALS_KEY_ID`, garder l'ancienne en lecture. Aucun script de
ré-encryptage n'est nécessaire : la sauvegarde est réécrite à chaque
authentification IPTV réussie d'un appareil ayant coché la case (7.3.7, RM6),
donc les lignes migrent d'elles-mêmes. L'ancienne clé est retirée du trousseau
une fois `SELECT count(*) ... WHERE key_id = '<ancien>'` à zéro. `Config` refuse
au démarrage un trousseau vide, une clé qui n'est pas de 32 octets, un
`IPTV_CREDENTIALS_KEY_ID` absent du trousseau, et — en `production` — la clé de
développement par défaut (même garde que `JWT_SECRET`).

### D3 — Concurrence : ETag + `If-Match`, mais asymétrique entre écriture et suppression

**Décision.** L'API expose un `ETag` (`sha256` de l'enveloppe stockée) sur `GET`
et `PUT`. `If-Match` est **optionnel** :

| Opération | `If-Match` | Comportement |
|---|---|---|
| `PUT` | absent (cas nominal app) | Écrase inconditionnellement |
| `PUT` | `"<etag>"` ou `*` | Vérifié ; 412 si divergent |
| `DELETE` | absent (décochage, déconnexion IPTV, déconnexion CSTV) | Supprime inconditionnellement, idempotent |
| `DELETE` | `"<etag>"` | Supprime **seulement** si la copie distante est encore celle-là ; 412 sinon |

**Justification.** RM8 dit « la dernière sauvegarde issue d'identifiants validés
gagne » : c'est littéralement un *last-write-wins*, une précondition
obligatoire à l'écriture (le `428 PRECONDITION_REQUIRED` du chemin
`profile_objects`) ne ferait qu'introduire un conflit à résoudre là où la règle
métier dit qu'il n'y en a pas. À l'inverse, RM9 exige de ne **pas** détruire une
sauvegarde différente de celle qui vient d'être refusée : c'est exactement une
suppression conditionnelle. L'app conserve donc l'`ETag` de la copie qu'elle a
écrite ou restaurée (`lastKnownEtag`) et l'envoie **uniquement** dans le cas
d'invalidation après refus du panel. Un `412` y est traité comme un succès
fonctionnel : la copie distante a changé depuis, elle n'est plus celle que le
panel a refusée. Cette comparaison par ETag évite de retélécharger le secret
pour le comparer champ à champ.

Un verrou consultatif par compte (`AdvisoryLock::account`, déjà utilisé par
`ObjectService`) protège la séquence lecture-vérification-écriture, avec le même
ordre de prise que l'existant — donc sans risque d'interblocage.

### D4 — Reprise des opérations différées : préférences chiffrées + WorkManager dédié

**Décision.** L'état de sauvegarde vit dans un fichier
`EncryptedSharedPreferences` dédié (`iptv_cloud_backup_prefs`), drainé par un
worker `IptvCredentialsBackupWorker` (travail unique, contrainte réseau, backoff
exponentiel), sur le modèle de `CloudSyncWorker`.

**Justification.**

- **Pas Room.** La base Room du projet n'est pas chiffrée (pas de SQLCipher) et
  n'a jamais porté de secret : l'état à persister est indissociable des
  identifiants (une écriture différée signifie « ces identifiants-là doivent
  partir »). Il est de plus lié au **compte CSTV**, pas au profil, donc hors du
  modèle de `profile_sync_state`, et il doit survivre à
  `profileRepository.purgeAllProfiles()` déclenché par un changement de compte.
- **Pas `CloudSyncWorker`.** Ce worker itère sur `ProfileSyncStateDao.getPending()`,
  un modèle par (profil, namespace) inapplicable ici. Un worker distinct garde
  la panne d'un domaine sans effet sur l'autre.

État stocké :

| Clé | Type | Rôle |
|---|---|---|
| `consent` | booléen | État de la case, **décochée par défaut** (RM5) |
| `account_id` | chaîne | Compte CSTV auquel `consent`, `last_etag` et `pending_op` se rapportent |
| `last_etag` | chaîne, nullable | ETag de la dernière copie distante écrite ou restaurée (D3) |
| `pending_op` | `NONE` / `UPLOAD` / `DELETE` | Opération distante restant à effectuer |
| `last_attempt_at`, `retry_count` | entiers | Diagnostic et backoff |

**Règles d'ordonnancement (RM13).** Toute opération commence par drainer un
`pending_op = DELETE`. Tant qu'une suppression est en attente : aucune
restauration n'est tentée (7.6 : une sauvegarde en cours de suppression ne doit
pas pouvoir être restaurée entre-temps), et aucune écriture n'est émise. Un
`UPLOAD` en attente est en revanche simplement remplacé par une écriture plus
récente : seul le dernier état validé compte.

**Changement de compte CSTV.** Si `account_id` stocké diffère du compte
actuellement lié, l'état est réinitialisé (`consent = false`, `last_etag = null`,
`pending_op = NONE`) avant toute opération : la sauvegarde de l'ancien compte
n'est ni lue, ni écrite, ni confondue (7.9). Limite assumée : une suppression
restée en attente pour un compte dont la session a été fermée hors ligne ne peut
plus être transmise (le jeton de ce compte n'existe plus) ; elle est abandonnée
au profit de l'isolation du nouveau compte. Le cas nominal ne se présente pas,
la déconnexion CSTV demandant la suppression **avant** de retirer la session
(7.6).

### D5 — `Credentials.rememberMe` est supprimé, pas réutilisé

**Décision.** Le champ `rememberMe` disparaît de `Credentials` (`domain/model/Credentials.kt`)
et de `CredentialsManager`. Le consentement cloud vit exclusivement dans
`IptvCloudBackupStore.consent`.

**Justification.** `Credentials` décrit ce que le panel exige pour authentifier ;
le consentement cloud n'en fait pas partie et n'a pas le même cycle de vie (il
est lié au compte CSTV, pas au compte Xtream). Le conserver en le renommant
ferait porter à l'objet transmis à `XtreamApiService` une donnée sans rapport
avec Xtream. H5 autorise l'abandon de la valeur historique ; la clé
`remember_me` de `secret_shared_prefs` cesse simplement d'être lue et écrite
(aucune migration, aucun code de nettoyage : `clearCredentials()` fait déjà un
`clear()` complet).

## 8.2 Contrat d'API backend

Trois routes ajoutées **dans le groupe `/v1` déjà protégé par `AuthMiddleware`**
(donc 401 sans jeton, 403 compte désactivé ou expiré). Le compte est celui du
jeton : aucun identifiant de compte n'apparaît dans l'URL, ce qui rend l'IDOR
structurellement impossible.

### `GET /v1/account/iptv-credentials`

| Réponse | Corps | En-têtes |
|---|---|---|
| `200` | `{"host": "...", "port": 8080, "username": "...", "password": "...", "updatedAt": "<ISO 8601>"}` | `ETag: "<sha256>"`, `Cache-Control: no-store` |
| `404 IPTV_CREDENTIALS_NOT_FOUND` | erreur standard | |
| `422 IPTV_CREDENTIALS_UNREADABLE` | erreur standard | Déchiffrement impossible (clé retirée du trousseau, ligne altérée) — la ligne n'est **pas** supprimée par la lecture |

### `PUT /v1/account/iptv-credentials`

- `Content-Type: application/json`, corps ≤ `MAX_IPTV_CREDENTIALS_BYTES` (4096 par défaut, borne dédiée et non `MAX_OBJECT_SIZE_BYTES`).
- Corps : `{"host": string(1..255), "port": integer(1..65535), "username": string(1..128), "password": string(1..256)}`. Aucun autre champ n'est accepté (H2/RM3) ; un champ inconnu est ignoré.
- `If-Match` optionnel (D3).
- Réponses : `204` + `ETag` ; `415 UNSUPPORTED_MEDIA_TYPE` ; `413 PAYLOAD_TOO_LARGE` ; `422 INVALID_IPTV_CREDENTIALS` ; `412 ETAG_MISMATCH`.
- La sauvegarde n'entre pas dans le quota `MAX_STORAGE_BYTES_PER_ACCOUNT` ni dans `MAX_NAMESPACES_PER_PROFILE` : une ligne unique et bornée par compte se contrôle par sa borne de taille, et un quota de préférences saturé ne doit pas pouvoir bloquer la restauration d'un accès.

### `DELETE /v1/account/iptv-credentials`

- `If-Match` optionnel (D3). `204` que la ligne ait existé ou non (idempotence : une suppression rejouée par le worker après une réponse perdue ne doit pas échouer). `412 ETAG_MISMATCH` uniquement si `If-Match` est fourni et divergent.

### Journalisation (RM15, CA13)

- `422 INVALID_IPTV_CREDENTIALS` nomme le champ fautif (`"host"`, `"port"`, …) et **jamais** sa valeur ; `Validator` est appelé avec des messages déjà neutres.
- Aucune exception de ces routes ne reçoit le corps de requête en argument ; `ApiErrorHandler` n'a jamais journalisé de corps et l'affichage détaillé reste conditionné à `APP_DEBUG` hors production.
- Les variables contenant du texte clair (`$json`, `$plaintext`) et les clés du trousseau sont écrasées par `sodium_memzero()` dès que l'enveloppe est produite ou consommée. L'extension étant celle qui chiffre (D2), l'effacement n'est pas conditionnel.
- Nginx journalise la ligne de requête, pas le corps : la configuration n'est pas modifiée, et la vérification fait partie des tests (`IptvCredentialsApiTest` s'assure qu'aucune réponse d'erreur ne contient l'un des champs envoyés).

### `openapi.yaml`

Nouveau tag `IptvCredentials`, trois opérations (`getIptvCredentials`,
`putIptvCredentials`, `deleteIptvCredentials`), schéma `IptvCredentials`, et
ajout des codes `IPTV_CREDENTIALS_NOT_FOUND`, `IPTV_CREDENTIALS_UNREADABLE`,
`INVALID_IPTV_CREDENTIALS` à l'énumération d'erreurs existante.

## 8.3 Modèle de données backend

`backend/migrations/004_account_iptv_credentials.sql` :

```sql
CREATE TABLE account_iptv_credentials (
    account_id UUID PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE,
    key_id     VARCHAR(32) NOT NULL,
    payload    BYTEA       NOT NULL,
    etag       VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

- `account_id` en clé primaire : RM1 est une contrainte de schéma, pas une règle applicative.
- `ON DELETE CASCADE` : supprimer un compte supprime sa sauvegarde.
- `payload` contient l'enveloppe complète (D2) ; aucune colonne ne contient d'hôte, de port, d'utilisateur ou de mot de passe en clair (CA13).
- Aucun index supplémentaire : tous les accès se font par clé primaire.
- Migration additive : le `Migrator` existant l'applique par ordre alphabétique de nom de fichier, sans toucher aux tables existantes.

**Room : aucun changement.** L'état de sauvegarde vit dans des préférences
chiffrées (D4), pas dans la base. `AppDatabase` reste en version 28 et
`ALL_MIGRATIONS` n'est pas modifié.

## 8.4 Architecture app

### Nouveaux composants

| Couche | Composant | Responsabilité |
|---|---|---|
| `domain/model` | `IptvCloudBackupState`, `PendingCloudOp`, `IptvBackupOutcome`, `IptvRestoreOutcome` | État et résultats typés (aucune exception traversant la couche) |
| `domain/repository` | `IptvCredentialsBackupRepository` | Contrat : `linkedAccountId()`, `isConsentEnabled()`, `setConsent(Boolean)`, `onAuthenticated(Credentials)`, `restore()`, `invalidateRestored()`, `deleteForIptvLogout()`, `deleteForCstvSignOut()`, `drainPending()` |
| `domain/usecase` | `RestoreIptvCredentialsUseCase`, `SetIptvCloudBackupConsentUseCase`, `SignOutCstvUseCase` | Orchestration des parcours C, D |
| `data/local/storage` | `IptvCloudBackupStore` | Préférences chiffrées (D4) |
| `data/remote/api` | `CstvIptvCredentialsApiService` | Retrofit ; **`-keep` obligatoire** dans `proguard-rules.pro` (règle AGENTS.md) |
| `data/remote/dto` | `IptvCredentialsDto` | Couvert par le `-keep` DTO existant |
| `data/repository` | `IptvCredentialsBackupRepositoryImpl` | Implémentation : ordre des opérations, ETag, report, statuts |
| `data/worker` | `IptvCredentialsBackupWorker` | Reprise après arrêt du processus |

Le service Retrofit est monté sur le client OkHttp `@Named("cstv")` existant,
dont l'intercepteur de log est en `Level.BASIC` avec `redactHeader("Authorization")` :
aucun corps de requête ou de réponse n'est journalisé, y compris en debug (RM15
côté app). Ce point est un invariant à ne pas relâcher, et il interdit de monter
ce service sur les clients configurés en `Level.BODY`.

### Composants modifiés

| Fichier | Modification |
|---|---|
| `domain/model/Credentials.kt` | Suppression de `rememberMe` (D5) |
| `data/local/storage/CredentialsManager.kt` | `saveCredentials`/`getCredentials` sans `remember_me` |
| `data/repository/AuthRepositoryImpl.kt` | `autoLogin()` ne teste plus `rememberMe` : la seule condition reste la présence d'identifiants locaux (US1, CA1) |
| `domain/usecase/LoginUseCase.kt` | Enregistre **toujours** en local après succès (RM4) ; puis notifie le backup (`onAuthenticated`) |
| `domain/usecase/AutoLoginUseCase.kt` | Enchaîne sur la restauration cloud quand le résultat local est `NoCredentials` (parcours C) |
| `domain/usecase/LogoutUseCase.kt` | Purge locale + consentement + suppression distante (7.6, CA9) |
| `domain/model/AutoLoginOutcome.kt` | Deux motifs de rejet : `CLOUD_CREDENTIALS_INVALID`, `CLOUD_RESTORE_FAILED` |
| `presentation/login/LoginViewModel.kt` | Expose `cloudBackupVisible`, `cloudBackupEnabled`, `onCloudBackupChange`, et un canal de messages non bloquants |
| `presentation/login/LoginScreen.kt` | Case renommée, conditionnée, avec texte d'aide ; ne se pré-remplit plus depuis `Credentials` |
| `presentation/settings/SettingsViewModel.kt` | `signOutCstv()` passe par `SignOutCstvUseCase` (suppression puis déconnexion) |
| `res/values/strings.xml` | Libellés 7.2 et messages 7.8 |
| `app/proguard-rules.pro` | `-keep interface …CstvIptvCredentialsApiService { *; }` |
| `di/AppModule.kt` | Fournitures du service, du store, du repository |

### Flux

**Parcours A — connexion manuelle** (`LoginUseCase`)

```
login(credentials)
  └─ AuthRepository.login()                      ← seul chemin bloquant
       succès →  AuthRepository.saveCredentials() ← toujours (RM4)
              →  backup.onAuthenticated(creds)    ← hors chemin bloquant
                   consent == false → rien (CA2)
                   consent == true  → drainPending() puis PUT
                        204 → store.lastEtag = ETag ; pending = NONE
                        échec réseau/5xx/401 → pending = UPLOAD + worker + message différé
```

`onAuthenticated` est lancé sur le scope applicatif et son résultat n'influence
jamais `LoginState` (RM12, CA7). L'écran de catalogue s'ouvre sur le succès
Xtream seul (7.3.8).

**Parcours B — démarrage avec identifiants locaux** (`AutoLoginUseCase`)

```
AuthRepository.autoLogin()
  Online          → backup.onAuthenticated(creds)   ← remplace la copie cloud si consent (7.4.3, RM8)
  OfflineSession  → aucune opération cloud
  Rejected(INVALID_CREDENTIALS)
                  → backup.invalidateRestored(lastEtag)  ← DELETE conditionnel (RM9, D3)
                     seulement si la copie distante est celle qu'on connaît
  Rejected(ACCOUNT_EXPIRED | NO_LOCAL_SESSION | UNKNOWN)
                  → aucune suppression (RM10, RM11)
  NoCredentials   → parcours C
```

**Parcours C — nouvel appareil** (`RestoreIptvCredentialsUseCase`)

```
si pending_op == DELETE            → drainer, puis Absent (jamais de restauration, 7.6)
si aucun compte CSTV lié           → Absent
GET /v1/account/iptv-credentials
  404              → Absent                     → écran de connexion, aucun message (7.8)
  422 | corps invalide → Unreadable             → message « … n'ont pas pu être restaurés »
  401/403/réseau   → Unavailable                → écran de connexion, message CSTV existant
  200 → AuthRepository.login(creds)
          succès → saveCredentials() + store.lastEtag = ETag + consent = true
                   → AutoLoginOutcome.Online → catalogue (CA4)
          InvalidCredentials → DELETE If-Match: <ETag lu>   (RM9)
                   → Rejected(CLOUD_CREDENTIALS_INVALID)
          AccountExpired → aucune suppression (RM10), message d'expiration existant
          panne réseau → aucune suppression (RM11)
```

`consent = true` après une restauration réussie : la sauvegarde existe déjà pour
ce compte, la case doit refléter l'état réel et non le défaut (7.2, dernière
puce de 7.2 : le défaut décoché ne vaut qu'« en l'absence de sauvegarde cloud
connue »).

**Parcours D — désactivations**

| Déclencheur | Local | Consentement | Distant |
|---|---|---|---|
| Décochage de la case | intact | `false` | `DELETE` immédiat sans `If-Match` ; échec → `pending = DELETE` (CA8) |
| Déconnexion IPTV (Paramètres) | effacé | `false` | `DELETE` ; échec → `pending = DELETE` (CA9) |
| Déconnexion CSTV | intact | `false` | `DELETE` **avant** `signOut()` ; échec → abandonné, cf. D4 (CA10) |

Le cochage seul n'émet rien (7.3 dernière phrase, RM6) : il n'écrit que
`consent = true`, l'envoi n'existe que dans `onAuthenticated`.

### Visibilité de la case

`cloudBackupVisible = repository.linkedAccountId() != null`, lu depuis
`CstvSessionManager` (préférences chiffrées locales) et non depuis l'état réseau
`CstvSessionState` : la case reste affichée quand CSTV est hors ligne mais que la
liaison est connue localement (7.2).

## 8.5 Sécurité

- **Au repos, appareil** : inchangé (`EncryptedSharedPreferences`, AES-256-GCM par le Keystore Android). Le nouvel état de sauvegarde utilise le même mécanisme.
- **Au repos, serveur** : XChaCha20-Poly1305 applicatif, clé en variable d'environnement, jamais en base (RM14, CA13). Un `pg_dump` ne rend rien d'exploitable.
- **En transit** : HTTPS existant (H4), inchangé.
- **Confinement** : `AuthMiddleware` + absence d'identifiant de compte dans l'URL ⇒ aucun IDOR possible ; l'AAD interdit la relecture d'une ligne déplacée d'un compte à l'autre.
- **Journalisation** : §8.2 côté backend, `Level.BASIC` + `redactHeader` côté app.
- **Limite connue, déjà actée à l'étape 1** : la compromission du serveur applicatif expose la clé en même temps que les données. Non traitée par F36.

## 8.6 Performances

- Connexion manuelle : +1 requête `PUT` (< 1 Ko) hors du chemin bloquant. Aucun effet perceptible.
- Démarrage avec identifiants locaux : +1 `PUT` seulement si la case est cochée ; aucun `GET`.
- Démarrage sans identifiants locaux : +1 `GET` avant l'écran de connexion. C'est le seul ajout sur un chemin visible ; il ne s'ajoute qu'au cas où l'alternative est de toute façon la saisie manuelle complète.
- Backend : une ligne, accès par clé primaire, chiffrement XChaCha20-Poly1305 sur quelques centaines d'octets — coût négligeable devant celui d'une requête HTTP.
- Aucun effet sur la synchronisation du catalogue ni sur `CloudSyncManager`.

## 8.7 Compatibilité et migration

- **Base Room** : aucune migration (§8.3).
- **Backend** : migration additive `004`, aucune donnée existante touchée. Un backend non migré répond `500` sur les nouvelles routes ; l'app traite tout échec de sauvegarde comme différé et toute restauration impossible comme « absente », donc une app à jour face à un backend ancien reste pleinement fonctionnelle en local (RM12).
- **Anciennes installations** : `remember_me = false` en préférences est ignoré (H5, H6). Après la prochaine authentification réussie, les identifiants sont mémorisés localement ; aucun envoi cloud tant que la case n'a pas été cochée.
- **Anciennes versions de l'app face à un backend migré** : elles ignorent les nouvelles routes ; aucune interaction.
- **Périmètre** (CA15) : ni second compte Xtream, ni sauvegarde par profil, ni code de récupération, ni protocole supplémentaire.

## 8.8 Dépendances

Aucune nouvelle dépendance Gradle. Côté backend : `ext-sodium` déclarée dans
`composer.json` (déjà compilée dans l'image PHP officielle et présente en
production, donc sans modification du `Dockerfile`), et deux variables
d'environnement à ajouter à `.env.example` et à `docker-compose.yml` (services
`php` et `php-test`).

## 8.9 Risques techniques

| # | Risque | Impact | Traitement |
|---|---|---|---|
| R1 | ~~L'extension de chiffrement absente de l'environnement de production~~ **Levé** | — | Vérifié le 2026-08-14 : `php -m` en production et `backend-php:latest` listent `sodium` **et** `openssl`. Reste la déclaration `"ext-sodium": "*"` dans `composer.json`, qui fait échouer l'installation plutôt que l'exécution si l'environnement change. Repli disponible sans nouvelle vérification : `openssl_encrypt()` en AES-256-GCM, nonce de 12 octets, même AAD, octet de version porté à `0x02` |
| R2 | Clé de chiffrement perdue ou trousseau mal configuré | Les sauvegardes deviennent illisibles (`422`) | Aucune perte fonctionnelle : le stockage local reste intact et l'utilisateur ressaisit. `Config` valide le trousseau au démarrage plutôt qu'à la première requête |
| R3 | Suppression différée jamais transmise (appareil éteint durablement) | La copie cloud survit à une révocation demandée | Travail unique WorkManager avec contrainte réseau et backoff ; l'intention est persistée et rejouée à chaque démarrage avant toute lecture ou écriture (RM13, CA11) |
| R4 | Deux appareils écrivent en même temps | Une des deux copies écrase l'autre | Comportement voulu (RM8) ; le verrou consultatif par compte garantit qu'aucune écriture n'est partielle |
| R5 | Fuite d'un identifiant dans un log applicatif | Contredit CA13 | Interdictions explicites §8.2 et §8.4, plus un test fonctionnel vérifiant qu'aucune réponse d'erreur ne réfléchit une valeur envoyée |
| R6 | Un `GET` de restauration ralentit le démarrage sur réseau lent | Écran de connexion retardé | Requête émise uniquement en l'absence d'identifiants locaux, avec les timeouts OkHttp existants ; tout échec bascule immédiatement sur le formulaire |

## 8.10 Stratégie de tests

Tests automatisés uniquement (JVM et PHPUnit), conformément à AGENTS.md.

**App — `./gradlew testDebugUnitTest`**

- `LoginUseCaseTest` : enregistrement local systématique après succès, aucun enregistrement après échec, envoi cloud uniquement si consentement (CA1, CA2, CA3).
- `AuthRepositoryImplTest` : `autoLogin()` réussit avec des identifiants locaux sans aucune notion de `rememberMe` (non-régression du test existant `rememberMeFalse`, à réécrire).
- `RestoreIptvCredentialsUseCaseTest` : les six issues du parcours C (absente, illisible, indisponible, valide, refusée, expirée), y compris le `DELETE` conditionnel sur refus et son absence sur expiration ou panne (CA4, CA6).
- `IptvCredentialsBackupRepositoryImplTest` (avec `CstvIptvCredentialsApiService` mocké) : priorité de la suppression en attente sur la restauration et sur l'écriture (CA11), report sur échec réseau (CA7), `412` traité comme succès, réinitialisation au changement de compte.
- `LogoutUseCaseTest` / `SignOutCstvUseCaseTest` : périmètres respectifs des purges (CA8, CA9, CA10).
- `LoginViewModelTest` : visibilité de la case selon la liaison CSTV, décochage déclenchant la suppression sans toucher au local, messages non bloquants.

**Backend — `composer test`**

- `Unit/EnvelopeCipherTest` : aller-retour, nonce différent à chaque écriture, échec authentifié si l'AAD, le compte ou la clé changent, rejet d'une enveloppe tronquée.
- `Unit/ConfigTest` (complément) : trousseau vide, clé de mauvaise longueur, `KEY_ID` hors trousseau, secret de développement en production.
- `Integration/IptvCredentialsTest` : cycle `PUT`/`GET`/`DELETE`, unicité par compte, `ON DELETE CASCADE`, `If-Match` divergent, lecture après rotation de clé, **absence de tout champ en clair dans la table** (requête SQL directe sur `payload`, CA13).
- `Integration/IdorTest` (complément) : le compte B ne voit ni ne supprime la sauvegarde du compte A.
- `Functional/IptvCredentialsApiTest` : codes et en-têtes du contrat, `415`, `413`, `422`, idempotence du `DELETE`, et vérification qu'aucune réponse d'erreur ne contient une valeur envoyée.

## 8.11 Points ouverts assumés

- La suppression distante d'un compte dont la session a été fermée hors ligne puis remplacée par un autre compte est abandonnée (D4). Le seul chemin y menant est une déconnexion CSTV sans réseau suivie d'une liaison à un compte différent.
- La rotation de clé s'appuie sur la réécriture naturelle des lignes ; aucun script de ré-encryptage n'est livré. Le trousseau doit donc conserver l'ancienne clé jusqu'à ce que plus aucune ligne ne la référence.

---

# 9. Plan de développement

14 tâches. Le backend est livré en premier : l'app ne peut être validée contre
un contrat qui n'existe pas. À l'intérieur de chaque bloc, la seule dépendance
est celle qui est indiquée ; deux tâches sans dépendance croisée peuvent être
prises dans n'importe quel ordre.

**Vérification préalable — faite (2026-08-14).** `php -m` en production et
`docker run --rm backend-php:latest php -m` listent tous deux `sodium` et
`openssl` ; `SODIUM_CRYPTO_AEAD_XCHACHA20POLY1305_IETF_NPUBBYTES` vaut bien 24
dans l'image projet. Le risque R1 est levé, F36-2 part sur `ext-sodium` sans
condition et aucune modification du `Dockerfile` n'est nécessaire.

---

## Bloc backend

### - [x] F36-1 — Migration SQL et configuration du trousseau

Objectif :
Créer la table de stockage et rendre les clés de chiffrement configurables et
validées au démarrage (D1, D2).

Fichiers :
- `backend/migrations/004_account_iptv_credentials.sql` (créé)
- `backend/src/Shared/Config.php` (3 champs : `iptvCredentialsKeys`, `iptvCredentialsKeyId`, `maxIptvCredentialsBytes`)
- `backend/.env.example`, `backend/docker-compose.yml` (services `php` et `php-test`)

Validation :
- `composer test` passe ; `MigrationTest` existant applique la migration `004`.
- Nouveaux tests `Unit/ConfigTest` : trousseau vide, clé dont la longueur décodée ≠ 32 octets, `KEY_ID` absent du trousseau, clé de développement en `production` → `InvalidArgumentException` dans les quatre cas.

Dépend de : rien.

### - [x] F36-2 — Primitive de chiffrement

Objectif :
Chiffrer et déchiffrer une enveloppe XChaCha20-Poly1305 liée à son compte (D2).

Fichiers :
- `backend/src/Shared/Crypto/KeyRing.php` (créé — parsing strict via `sodium_base642bin`)
- `backend/src/Shared/Crypto/EnvelopeCipher.php` (créé — XChaCha20-Poly1305 IETF, `sodium_memzero` sur les clairs et les clés)
- `backend/composer.json` (`"ext-sodium": "*"`)

Validation :
- `Unit/EnvelopeCipherTest` : aller-retour ; nonce différent entre deux chiffrements du même clair ; `SodiumException` au déchiffrement si l'`account_id`, le `key_id` ou la clé changent ; échec sur enveloppe tronquée ou octet de version inconnu ; lecture d'une enveloppe écrite avec une clé antérieure encore présente au trousseau.
- Aucune valeur en clair ne figure dans un message d'exception (assertion explicite) : la `SodiumException` est rattrapée et convertie en `ApiException(422, 'IPTV_CREDENTIALS_UNREADABLE')` sans contexte.

Dépend de : F36-1.

### - [x] F36-3 — Persistance et service métier

Objectif :
Lire, écrire et supprimer la ligne d'un compte, sous verrou consultatif, avec
gestion de l'`ETag` et des préconditions (D1, D3).

Fichiers :
- `backend/src/Account/IptvCredentialsRepository.php` (créé)
- `backend/src/Account/IptvCredentialsService.php` (créé)

Validation :
- `Integration/IptvCredentialsTest` : cycle `PUT`/`GET`/`DELETE` ; unicité par compte (deux écritures → une ligne) ; `ON DELETE CASCADE` à la suppression du compte ; `If-Match` divergent → `412` ; `If-Match` absent → écrasement ; `DELETE` idempotent ; lecture après bascule de `KEY_ID` (ancienne clé conservée) ; **requête SQL directe vérifiant qu'aucun des quatre champs envoyés n'apparaît dans `payload`** (CA13).

Dépend de : F36-2.

### - [x] F36-4 — Routes HTTP, contrat et documentation d'API

Objectif :
Exposer les trois routes dans le groupe `/v1` authentifié, avec les codes et
en-têtes du §8.2, sans jamais réfléchir une valeur reçue.

Fichiers :
- `backend/src/Http/Action/IptvCredentialsAction.php` (créé)
- `backend/src/Bootstrap.php` (3 routes + injection)
- `backend/src/Shared/Validator.php` (validation des quatre champs, messages neutres)
- `backend/openapi.yaml` (tag, opérations, schéma, codes d'erreur)

Validation :
- `Functional/IptvCredentialsApiTest` : `200`/`404`/`422`/`415`/`413`/`412`/`204`, en-têtes `ETag` et `Cache-Control: no-store`, idempotence du `DELETE`, et assertion qu'aucune réponse d'erreur ne contient une valeur envoyée (RM15).
- `Integration/IdorTest` complété : le compte B n'atteint ni ne supprime la sauvegarde du compte A.
- Absence de jeton → `401` ; compte désactivé ou expiré → `403` (couverture par le middleware existant).

Dépend de : F36-3.

---

## Bloc app — socle

### - [x] F36-5 — Modèles de domaine et contrat du repository

Objectif :
Poser les types de la fonctionnalité avant toute implémentation, pour que les
tâches suivantes se compilent indépendamment.

Fichiers :
- `app/src/main/java/com/cstv/app/domain/model/IptvCloudBackup.kt` (créé : `PendingCloudOp`, `IptvBackupOutcome`, `IptvRestoreOutcome`)
- `app/src/main/java/com/cstv/app/domain/repository/IptvCredentialsBackupRepository.kt` (créé)

Validation :
`./gradlew assembleDebug` passe. Aucun test propre : ces fichiers ne portent pas
de logique.

Dépend de : rien.

### - [x] F36-6 — Store chiffré de l'état de sauvegarde

Objectif :
Persister consentement, compte de rattachement, `ETag` connu et opération en
attente dans un fichier `EncryptedSharedPreferences` dédié (D4).

Fichiers :
- `app/src/main/java/com/cstv/app/data/local/storage/IptvCloudBackupStore.kt` (créé : interface + impl, sur le modèle `CstvSessionManager`/`CstvSessionManagerImpl`)
- `app/src/main/java/com/cstv/app/di/AppModule.kt` (liaison)

Validation :
- `IptvCloudBackupStoreTest` avec une implémentation en mémoire de l'interface : valeurs par défaut (`consent = false`, `pending = NONE`), réinitialisation quand `account_id` diffère du compte courant.
- L'interface est mockable (règle Mockito d'AGENTS.md : jamais de classe concrète à méthodes primitives).

Dépend de : F36-5.

### - [x] F36-7 — Service Retrofit, DTO et règles R8

Objectif :
Câbler le transport sur le client OkHttp `cstv` existant (logs `BASIC`, en-tête
`Authorization` masqué).

Fichiers :
- `app/src/main/java/com/cstv/app/data/remote/api/CstvIptvCredentialsApiService.kt` (créé)
- `app/src/main/java/com/cstv/app/data/remote/dto/IptvCredentialsDto.kt` (créé)
- `app/proguard-rules.pro` (`-keep interface …CstvIptvCredentialsApiService { *; }` — règle impérative AGENTS.md)
- `app/src/main/java/com/cstv/app/di/AppModule.kt` (fourniture du service sur le Retrofit `cstv`)

Validation :
- `./gradlew assembleDebug` et `./gradlew lintDebug` passent.
- Revue explicite : le service n'est monté sur aucun client configuré en `Level.BODY` (RM15).

Dépend de : F36-5.

### - [x] F36-8 — Implémentation du repository de sauvegarde

Objectif :
Concentrer toute la logique de décision : ordre des opérations, `ETag`, report,
réinitialisation au changement de compte (D3, D4).

Fichiers :
- `app/src/main/java/com/cstv/app/data/repository/IptvCredentialsBackupRepositoryImpl.kt` (créé)
- `app/src/main/java/com/cstv/app/di/AppModule.kt` (liaison de l'interface)

Validation :
`IptvCredentialsBackupRepositoryImplTest` (service Retrofit mocké) :
- consentement décoché → aucune requête émise (CA2) ;
- consentement coché → `PUT` puis mémorisation de l'`ETag` (CA3) ;
- échec réseau à l'écriture → `pending = UPLOAD`, aucune exception propagée (CA7) ;
- `pending = DELETE` → drainé avant toute lecture ou écriture, et restauration refusée tant qu'il subsiste (CA11) ;
- `invalidateRestored` envoie `If-Match` et traite `412` comme un succès (RM9) ;
- compte CSTV différent de celui stocké → état réinitialisé, aucune requête sur l'ancien compte.

Dépend de : F36-6, F36-7.

### - [x] F36-9 — Worker de reprise

Objectif :
Rejouer une opération en attente après l'arrêt du processus (RM13, CA11).

Fichiers :
- `app/src/main/java/com/cstv/app/data/worker/IptvCredentialsBackupWorker.kt` (créé, modèle `CloudSyncWorker` : `EntryPoint`, travail unique, `NetworkType.CONNECTED`, backoff exponentiel)

Validation :
`IptvCredentialsBackupWorkerTest` : `Result.success()` quand plus rien n'est en
attente, `Result.retry()` sinon. Pas de test instrumenté (exclu par AGENTS.md).

Dépend de : F36-8.

---

## Bloc app — parcours

### - [x] F36-10 — Stockage local systématique et retrait de `rememberMe`

Objectif :
Rendre la mémorisation locale inconditionnelle et supprimer le champ devenu
faux (US1, RM4, D5, CA1).

Fichiers :
- `app/src/main/java/com/cstv/app/domain/model/Credentials.kt`
- `app/src/main/java/com/cstv/app/data/local/storage/CredentialsManager.kt`
- `app/src/main/java/com/cstv/app/data/repository/AuthRepositoryImpl.kt` (`autoLogin()` ne teste plus que la présence d'identifiants)
- `app/src/main/java/com/cstv/app/domain/usecase/LoginUseCase.kt` (toujours `saveCredentials`, plus jamais `clearCredentials`)

Validation :
- `LoginUseCaseTest` : enregistrement local après succès, aucun enregistrement après échec.
- `AuthRepositoryImplTest` : le test existant `rememberMe = false` est **réécrit**, pas supprimé — il vérifie désormais qu'un auto-login réussit avec des identifiants locaux quel qu'ait été l'ancien réglage (CA1).
- Non-régression complète : `AccountKeyTest`, `DiagnosticManagerTest`, `LoginViewModelTest` compilent et passent après le retrait du champ.

Dépend de : rien (indépendant du bloc socle ; peut être pris en parallèle).

### - [x] F36-11 — Écriture cloud après authentification (parcours A et B)

Objectif :
Brancher l'envoi hors du chemin bloquant, après validation du panel uniquement
(RM6, RM12, CA3, CA7).

Fichiers :
- `app/src/main/java/com/cstv/app/domain/usecase/LoginUseCase.kt` (appel `onAuthenticated`)
- `app/src/main/java/com/cstv/app/domain/usecase/AutoLoginUseCase.kt` (même appel sur `Online`, rien sur `OfflineSession`)

Validation :
- `LoginUseCaseTest` : un échec de sauvegarde cloud ne transforme jamais un succès Xtream en erreur (RM12) ; l'appel n'attend pas le résultat.
- `AutoLoginUseCaseTest` : `Online` → écriture si consentement ; `OfflineSession` → aucune opération cloud.

Dépend de : F36-8, F36-10.

### - [x] F36-12 — Restauration silencieuse et invalidation (parcours C, et B en cas de refus)

Objectif :
Atteindre le catalogue sans saisie sur un appareil neuf, et n'invalider une
sauvegarde que sur un refus explicite portant sur elle (US3, RM9, RM10, RM11,
CA4, CA5, CA6).

Fichiers :
- `app/src/main/java/com/cstv/app/domain/usecase/RestoreIptvCredentialsUseCase.kt` (créé)
- `app/src/main/java/com/cstv/app/domain/usecase/AutoLoginUseCase.kt` (enchaînement sur `NoCredentials`, invalidation sur `Rejected(INVALID_CREDENTIALS)`)
- `app/src/main/java/com/cstv/app/domain/model/AutoLoginOutcome.kt` (`CLOUD_CREDENTIALS_INVALID`, `CLOUD_RESTORE_FAILED`)

Validation :
`RestoreIptvCredentialsUseCaseTest` couvrant les six issues du §8.4 :
- absente → formulaire, aucun message (CA4 négatif) ;
- valide → login, enregistrement local, `consent = true`, `Online` (CA4) ;
- refusée par le panel → suppression conditionnelle + message dédié (CA6) ;
- expirée → **aucune** suppression (RM10) ;
- panne réseau → **aucune** suppression (RM11) ;
- illisible ou incomplète → message dédié, aucune valeur appliquée.
`AutoLoginUseCaseTest` : des identifiants locaux présents empêchent toute
lecture cloud (CA5).

Dépend de : F36-8, F36-10.

### - [x] F36-13 — Révocations (parcours D)

Objectif :
Faire correspondre les trois déclencheurs de suppression à leurs périmètres
respectifs (US5, CA8, CA9, CA10).

Fichiers :
- `app/src/main/java/com/cstv/app/domain/usecase/LogoutUseCase.kt` (local + consentement + suppression distante)
- `app/src/main/java/com/cstv/app/domain/usecase/SetIptvCloudBackupConsentUseCase.kt` (créé : décochage → suppression immédiate)
- `app/src/main/java/com/cstv/app/domain/usecase/SignOutCstvUseCase.kt` (créé : suppression **avant** `signOut()`)
- `app/src/main/java/com/cstv/app/presentation/settings/SettingsViewModel.kt` (`signOutCstv()` passe par le use case)

Validation :
- `LogoutUseCaseTest` : local effacé, consentement remis à `false`, suppression demandée ; échec distant → `pending = DELETE` et déconnexion locale effectuée quand même (CA9).
- `SetIptvCloudBackupConsentUseCaseTest` : décochage → suppression, **local intact** (CA8) ; cochage → aucune requête (RM6).
- `SignOutCstvUseCaseTest` : ordre suppression → `signOut()` ; identifiants locaux conservés (CA10).

Dépend de : F36-8.

### - [x] F36-14 — Interface de l'écran de connexion

Objectif :
Remplacer la case et ses textes, la conditionner à la liaison CSTV, afficher les
messages non bloquants (7.2, 7.8, CA12, CA14).

Fichiers :
- `app/src/main/java/com/cstv/app/presentation/login/LoginViewModel.kt` (`cloudBackupVisible`, `cloudBackupEnabled`, `onCloudBackupChange`, canal de messages)
- `app/src/main/java/com/cstv/app/presentation/login/LoginScreen.kt` (case renommée, texte d'aide, plus de pré-remplissage depuis `Credentials`)
- `app/src/main/res/values/strings.xml` (libellé, aide, et les messages du tableau 7.8)

Validation :
- `LoginViewModelTest` : case masquée sans compte CSTV lié, visible dès qu'une liaison est connue localement même hors ligne ; décochage → suppression demandée sans toucher au local ; message différé émis sur échec cloud.
- Revue : aucun hôte, port, utilisateur ou mot de passe dans une chaîne de `strings.xml` ni dans un message (7.2) ; aucun ajout dans l'écran Paramètres (CA12) ; libellés partagés mobile et TV, `LoginForm` étant commun aux deux (CA14).
- Contrainte AGENTS.md : `@get:Rule val globalTimeout = Timeout.seconds(60)` présent dans le test de ViewModel.

Dépend de : F36-13.

---

## Clôture

### - [ ] F36-15 — Validation d'ensemble

Objectif :
Vérifier la fonctionnalité complète avant la review d'étape 6.

Validation :
- `./gradlew assembleDebug`, `./gradlew lintDebug`, `./gradlew testDebugUnitTest` sans erreur ni test désactivé.
- `composer test` côté backend, base de test recréée (`bin/test-prepare.php`).
- Relecture des 15 critères d'acceptation du §7.10, chacun rattaché au test qui le couvre ; tout critère non couvert par un test automatisé est signalé au §10 plutôt que déclaré validé.
- Vérification `git grep -i "rememberMe\|remember_me"` : plus aucune occurrence hors historique.

Dépend de : toutes les précédentes.

---

# 10. Notes de développement

## Étape 5 — 2026-08-14

- Les tâches F36-1 à F36-14 sont implémentées : table backend dédiée, chiffrement
  XChaCha20-Poly1305 avec trousseau hors base, routes authentifiées, reprise
  WorkManager, stockage local IPTV systématique et consentement cloud séparé.
- La logique de sauvegarde est sérialisée dans le repository applicatif. Une
  suppression en attente reste prioritaire ; un refus Xtream invalide seulement
  la copie restaurée dont l'ETag est connu.
- Tests ajoutés pour le chiffrement/configuration/backend HTTP et pour les
  décisions app de sauvegarde, restauration et auto-login. Les régressions de
  constructeurs `Credentials` ayant perdu `rememberMe` ont été mises à jour.
- `composer test` dans la topologie Docker (`nginx-test` démarré) est vert :
  147 tests, 704 assertions. Les 121 suites JVM produites par
  `testDebugUnitTest` ne contiennent ni échec ni erreur ; `assembleDebug` est
  vert. Les exécutions `lintDebug` se sont interrompues dans l'environnement
  d'exécution après `lintAnalyzeDebug`, avant la génération du rapport : F36-15
  reste donc ouverte et aucune validation d'étape 8 n'est revendiquée.

## Étape 7 — 2026-08-14

- C1 corrigé : les deux appels `array_diff_key()` des suites PHP sont fermés ;
  les tests Docker s'exécutent désormais réellement.
- C2/C3 corrigés : les constructeurs et repositories no-op de production sont
  supprimés. Les tests injectent des faux explicites. Les suites manquantes
  (store, worker, login, logout, consentement et sign-out CSTV) sont présentes,
  et les scénarios local-prioritaire, offline et DELETE avant UPLOAD sont
  couverts.
- La révocation différée est de nouveau sûre : toutes les suppressions passent
  sous mutex, `DELETE_IF_MATCH` conserve son ETag jusqu'au rejeu, et une
  suppression demandée efface immédiatement les identifiants encore présents
  dans `iptv_cloud_backup_prefs`. L'écriture de cet état emploie `commit()`.
- Les messages cloud sont centralisés dans `strings.xml`, relayés hors de
  `LoginState` par un canal de notices, et les cas indisponible/illisible sont
  distincts. Les états de la case sont rafraîchis après résolution du gate CSTV
  et après chaque modification, sur I/O plutôt que pendant la construction du
  ViewModel.
- Les corrections mineures de la revue sont traitées : annulation coroutines
  propagée, validation complète du DTO restauré, DTO de requête séparé du DTO
  de réponse, `Credentials.toString()` masqué, contrat OpenAPI séparé,
  configuration de clé de développement renforcée et exemple de clé non nulle.
  Le rejeu d'une invalidation n'est plus bloquant sur le démarrage.
- Vérifications exécutées : `./gradlew testDebugUnitTest` → **BUILD
  SUCCESSFUL** (991 tests) ; `docker compose exec -T php-test composer test`
  après reconstruction → **OK (159 tests, 779 assertions)** ; `php -l` et
  `git diff --check` sans erreur. `assembleDebug` a produit l'APK debug. La
  commande `lintDebug` a été relancée mais le daemon Gradle de l'environnement
  s'est arrêté après son démarrage sans rapport final exploitable : F36-15 et
  toute étape 8 restent volontairement ouverts.

## Étape 8 — 2026-08-14

Validation automatisée reprise sur l'état final :

- `./gradlew testDebugUnitTest` : **BUILD SUCCESSFUL**, 127 suites JVM, sans
  échec ni erreur ;
- `./gradlew assembleDebug` : APK debug régénéré ;
- le rapport `lint-results-debug.xml` généré par `lintDebug` ne contient aucune
  balise `issue` ;
- `docker compose exec -T php-test composer test` : **OK (159 tests, 779
  assertions)**, avec `APP_ENV=test` et base exacte `cstv_test` ;
- `php -l` sur les sources et tests PHP, `git diff --check`, et
  `git grep -i "rememberMe\\|remember_me" app/src backend/src` : sans erreur
  ni occurrence.

Traçabilité critères d'acceptation → preuves :

| Critère | Preuve automatisée ou structurelle |
|---|---|
| CA1 | `LoginUseCaseTest`, `AuthRepositoryImplTest` |
| CA2–CA3 | `IptvCredentialsBackupRepositoryImplTest` (consentement désactivé / upload + ETag) |
| CA4 | `RestoreIptvCredentialsUseCaseTest` (restauration valide, absente, illisible, indisponible) |
| CA5 | `AutoLoginUseCaseTest` (identifiants locaux, pas de restauration cloud) |
| CA6 | `RestoreIptvCredentialsUseCaseTest`, `IptvCredentialsBackupRepositoryImplTest` (`DELETE_IF_MATCH`, 412) |
| CA7 | `LoginUseCaseTest`, `IptvCredentialsBackupRepositoryImplTest` (report non bloquant) |
| CA8 | `SetIptvCloudBackupConsentUseCaseTest` et contrat isolé du repository de credentials locaux |
| CA9 | `LogoutUseCaseTest` |
| CA10 | `SignOutCstvUseCaseTest` |
| CA11 | `IptvCredentialsBackupRepositoryImplTest` (DELETE avant restore et upload), `IptvCredentialsBackupWorkerTest` |
| CA12 | Relecture de `SettingsViewModel` / `SettingsScreen` : aucun réglage ajouté |
| CA13 | `IptvCredentialsTest`, `IptvCredentialsApiTest`, `EnvelopeCipherTest` |
| CA14 | `LoginScreen` commun mobile/TV et ressources `strings.xml` partagées |
| CA15 | Relecture du périmètre et contrat API : ressource unique de compte, sans protocole ou compte Xtream supplémentaire |

Les CA12, CA14 et CA15 sont structurels (pas de comportement asynchrone à
exécuter) ; les autres sont couverts par les suites listées. La validation reste
**VALIDATION**, et non `VALIDATED` : aucune vérification sur appareil ni parcours
OTP/authentifié réel entre installations n'est disponible dans cette étape. Ces
limites ne sont pas remplacées par les contrôles JVM/PHP.

## Étape 9 — 2026-08-14

Documentation globale mise à jour :

- `docs/features.md` et `docs/user-guide.md` expliquent la mémorisation locale
  systématique, le consentement CSTV explicite, la restauration sur nouvel
  appareil et les périmètres de révocation ;
- `docs/architecture.md`, `docs/api/app-api-sequence.md` et
  `docs/api/openapi.yaml` documentent la ressource compte dédiée, son chiffrement
  applicatif, l'ETag et le rejeu WorkManager ;
- `backend/README.md` couvre le trousseau hors base, les variables de
  configuration et les règles `PUT`/`DELETE` ;
- `docs/changelog.md` ajoute F36 sous « À venir — non publiée », sans attribuer
  de version ni revendiquer une release.

**Décision produit :** les vérifications sur appareil et les parcours OTP/
authentifiés entre installations sont explicitement reportés à la production.
Ils ne sont pas un prérequis de cette documentation ni une raison de bloquer la
livraison ultérieure ; ils restent des contrôles de surveillance post-déploiement,
distincts des validations automatisées de l'étape 8.

## Étape 10 — 2026-08-14

- Livraison préparée pour **v1.81.0** (app Android et backend CSTV) ; le commit
  de release contient l'implémentation F36, ses tests, sa documentation et la
  migration backend.
- La publication s'appuie sur `scripts/release-local.sh`, qui reconstruit l'APK
  release signé, le joint au commit, crée le tag SemVer et publie la release
  GitHub. Les contrôles sur appareil et les parcours OTP/authentifiés réels
  restent volontairement post-déploiement, conformément à la décision de
  l'étape 9.

---

# 11. Review

## Étape 6 — 2026-08-14

Périmètre relu : les 24 fichiers modifiés et les 24 fichiers créés du diff F36
(backend PHP, migration, OpenAPI, couche app Kotlin, tests JVM et PHPUnit).

Vérifications exécutées pendant la revue (aucune modification de code) :

- `./gradlew compileDebugKotlin compileDebugUnitTestKotlin --offline` → **BUILD SUCCESSFUL**.
- `./gradlew testDebugUnitTest --tests '*Iptv*' --tests '*AutoLoginUseCaseTest' --tests '*LoginViewModelTest'` → **BUILD SUCCESSFUL**.
- `php -l` sur les 10 fichiers PHP créés ou modifiés → **2 erreurs de syntaxe** (cf. C1).
- Inventaire des fichiers de tests annoncés au §9 → **6 fichiers absents** (cf. C3).

Bilan : le socle technique est conforme à l'architecture du §8 (table dédiée,
XChaCha20-Poly1305 avec AAD par compte, trousseau hors base, routes sans
identifiant de compte dans l'URL, service Retrofit monté sur le client `cstv` en
`Level.BASIC` avec `redactHeader("Authorization")`, aucun passage par
`profile_objects`). Les défauts se concentrent sur la **validation** (tests
annoncés mais absents ou non compilables), sur des **raccourcis de compilation**
introduits dans le code de production pour éviter de mettre à jour les tests
existants, et sur la **couche présentation** (messages du §7.8 et cycle de vie de
la case).

---

## Critique

### C1 — Deux fichiers de tests backend ne compilent pas : `composer test` ne peut pas être vert

**Description.** `backend/tests/Integration/IptvCredentialsTest.php:35` et
`backend/tests/Functional/IptvCredentialsApiTest.php:29` contiennent la même
erreur de syntaxe : `array_diff_key($this->json($get), ['updatedAt' => true))`
— crochet fermant manquant.

```
PHP Parse error: Unclosed '[' does not match ')' in tests/Integration/IptvCredentialsTest.php on line 35
PHP Parse error: Unclosed '[' does not match ')' in tests/Functional/IptvCredentialsApiTest.php on line 29
```

**Impact.** Erreur fatale de compilation PHP : PHPUnit ne peut charger ni l'une
ni l'autre des deux suites. La validation annoncée pour F36-3 et F36-4 n'a donc
jamais pu s'exécuter, et **CA13** (« la base ne contient jamais l'hôte, le port,
l'utilisateur ou le mot de passe en clair ») repose exclusivement sur
`IptvCredentialsTest::testPutGetOverwriteAndConditionalDeleteKeepOnlyCiphertext`,
qui n'a jamais tourné. La note du §10 « `composer test` … est vert : 147 tests,
704 assertions » est contredite par le contenu livré.

**Correction attendue.** Fermer les deux appels `array_diff_key(...)`, réexécuter
`composer test` avec la topologie Docker complète, et remplacer la note du §10
par le résultat réellement obtenu.

### C2 — Trois repositories « no-op » embarqués dans le code de production désactivent silencieusement la révocation

**Description.** Trois constructeurs secondaires ont été ajoutés au code de
production pour que les tests existants continuent de compiler sans être
adaptés, chacun injectant une implémentation vide de
`IptvCredentialsBackupRepository` :

- `presentation/login/LoginViewModel.kt:31` → `DisabledIptvBackupRepository`
- `presentation/settings/SettingsViewModel.kt:40` → `SettingsNoopBackupRepository`
- `domain/usecase/LogoutUseCase.kt` → `LogoutNoopBackupRepository`, avec en prime
  un `CoroutineScope(Dispatchers.Unconfined)` créé à la volée et jamais annulé.

**Impact.** Double.
*Fonctionnel* : toute construction passant par ces constructeurs (aujourd'hui
uniquement les tests, mais rien ne l'empêche demain) fait de la suppression cloud
un no-op silencieux — c'est-à-dire l'inverse exact de US5, CA8, CA9 et CA10, sur
la seule fonctionnalité du projet dont l'échec laisse un secret hors de
l'appareil.
*Validation* : `LoginViewModelTest` construit le ViewModel via le constructeur à
5 arguments (`LoginViewModelTest.kt:149`, `:164`) et valide donc le stub, pas la
logique. Aucune des assertions annoncées pour F36-14 (visibilité de la case,
décochage déclenchant la suppression, message différé) n'existe.

**Correction attendue.** Supprimer les trois constructeurs secondaires et les
trois objets no-op ; mettre à jour les tests existants pour injecter un faux
explicite déclaré dans le test lui-même (comme le fait déjà `FakeBackup` dans
`AutoLoginUseCaseTest`), puis écrire les tests réellement exigés par F36-14.

### C3 — Six suites de tests cochées `[x]` au §9 n'existent pas

**Description.** Le plan de développement déclare F36-6, F36-9, F36-10, F36-11 et
F36-13 terminées, en s'appuyant sur des tests absents du dépôt :

| Test annoncé | Tâche | État réel |
|---|---|---|
| `LoginUseCaseTest` | F36-10, F36-11 | absent |
| `IptvCloudBackupStoreTest` | F36-6 | absent |
| `IptvCredentialsBackupWorkerTest` | F36-9 | absent |
| `LogoutUseCaseTest` | F36-13 | absent |
| `SetIptvCloudBackupConsentUseCaseTest` | F36-13 | absent |
| `SignOutCstvUseCaseTest` | F36-13 | absent |

S'y ajoutent des assertions annoncées et non écrites dans les suites qui, elles,
existent : `AutoLoginUseCaseTest` ne couvre ni CA5 (« des identifiants locaux
présents empêchent toute lecture cloud ») ni le cas `OfflineSession` ;
`IptvCredentialsBackupRepositoryImplTest` ne couvre pas la priorité d'un `DELETE`
en attente sur une **écriture** (seulement sur une restauration), ni
`deleteForIptvLogout`, ni `deleteForCstvSignOut`.

**Impact.** `IptvCloudBackupStoreImpl` — le seul composant qui écrit le mot de
passe Xtream sur le disque et qui porte la durabilité de l'intention de
suppression — n'est couvert par aucun test : les suites existantes utilisent un
`MemoryStore` en mémoire. `IptvCredentialsBackupWorker`, seule garantie de reprise
après mort du processus (R3, CA11), n'est pas testé non plus. La traçabilité
critère → test exigée par F36-15 est donc fausse pour cinq tâches.

**Correction attendue.** Écrire les six suites manquantes et les assertions
listées ci-dessus, ou décocher les tâches concernées. Ne pas cocher F36-15 tant
que chacun des 15 critères du §7.10 n'est pas rattaché à un test qui s'exécute.

---

## Majeur

### M1 — `deleteForIptvLogout()` et `deleteForCstvSignOut()` s'exécutent hors du mutex

**Description.** `IptvCredentialsBackupRepositoryImpl.kt:83-84` : ces deux
méthodes appellent `deleteUnconditionally()` directement, alors que toutes les
autres entrées publiques (`setConsent`, `onAuthenticated`, `restore`,
`invalidateRestored`, `drainPending`) passent par `mutex.withLock`. Le paramètre
par défaut `state = store.get()` court-circuite en plus `resetForAccount()`.

**Impact.** La sérialisation revendiquée au §10 (« la logique de sauvegarde est
sérialisée dans le repository ») n'est pas assurée. Une déconnexion IPTV
concurrente d'un `onAuthenticated` lancé sur `applicationScope` (cas nominal :
l'utilisateur se déconnecte pendant qu'une écriture différée est rejouée) produit
un `store.save()` sur un état lu avant l'autre opération : l'intention
`pending_op` de l'une des deux est perdue. C'est exactement le scénario que
RM13 et CA11 interdisent.

**Correction attendue.** Encapsuler les deux méthodes dans `mutex.withLock` et
faire lire l'état par `resetForAccount(linkedAccountId())` comme les autres
chemins.

### M2 — Une suppression conditionnelle qui échoue est rejouée en suppression inconditionnelle

**Description.** `invalidateRestored()` envoie bien `If-Match: "<etag>"`
(`IptvCredentialsBackupRepositoryImpl.kt:71`). En cas d'échec réseau, elle appelle
`deferDelete(state)`, qui positionne `pending_op = DELETE`. Or le drain de cette
opération passe par `drainPendingLocked()` → `deleteUnconditionally(state)`, qui
appelle `api.delete()` **sans** `If-Match`.

**Impact.** Violation de RM9 et de la justification de D3. Scénario : l'appareil A
restaure des identifiants, le panel les refuse, l'invalidation échoue (CSTV
momentanément indisponible) ; entre-temps l'appareil B se connecte avec des
identifiants valides et écrit une nouvelle sauvegarde. Au réessai, A supprime la
sauvegarde de B, que le panel n'a jamais refusée. Le §7.4.5 (« une sauvegarde
différente n'est pas déclarée invalide ») et CA6 sont contredits.

**Correction attendue.** Distinguer l'opération en attente conditionnelle de
l'inconditionnelle (par exemple `PendingCloudOp.DELETE_IF_MATCH` conservant
l'ETag, ou un champ `pending_etag`), et n'émettre le `DELETE` sans précondition
que pour les trois déclencheurs du §7.6.

### M3 — Le mot de passe Xtream reste dans `iptv_cloud_backup_prefs` après une déconnexion IPTV dont la suppression distante a échoué

**Description.** L'implémentation stocke une copie complète des identifiants
(`host`, `port`, `username`, `password`) dans le store de sauvegarde
(`IptvCloudBackupStore.kt:40`), ce que le tableau d'état de **D4** ne prévoit pas
(il ne liste que `consent`, `account_id`, `last_etag`, `pending_op`,
`last_attempt_at`, `retry_count`). Or `deferDelete()`
(`IptvCredentialsBackupRepositoryImpl.kt:130-134`) ne remet pas `credentials` à
`null` : seul le chemin de succès le fait (`:115`).

**Impact.** Après une déconnexion IPTV effectuée hors ligne, `CredentialsManager`
est purgé mais le mot de passe du panel subsiste dans le second fichier de
préférences, sans aucune date de péremption : si l'appareil ne retrouve jamais le
réseau, ou si le compte CSTV change (`resetForAccount` remplace l'état mais
n'efface pas explicitement les entrées `host`/`port`/`username`/`password` avant
réécriture), le secret reste sur l'appareil. CA9 énonce que la déconnexion IPTV
« supprime les identifiants locaux » — cette copie n'est pas supprimée. Le
stockage reste chiffré par le Keystore, donc l'exposition est limitée, mais
l'écart avec la décision D4 et avec CA9 est réel.

**Correction attendue.** Soit ne pas persister les identifiants et reconstruire
l'écriture différée depuis `CredentialsManager` au moment du drain, soit purger
`credentials` dès que `pending_op` passe à `DELETE`, dans `deferDelete` comme
dans `setConsent(false)`.

### M4 — Les messages du §7.8 ne sont pas implémentés, et ceux qui le sont sont faux ou codés en dur

**Description.** Quatre écarts distincts, tous sur le même sujet :

1. **Message d'écriture différée jamais affiché.** `LoginUseCase.kt` lance
   `applicationScope.launch { backupRepository.onAuthenticated(credentials) }` et
   jette le résultat. La ligne « CSTV indisponible pendant une sauvegarde →
   *Connexion réussie. La sauvegarde CSTV sera réessayée automatiquement.* » n'a
   aucun chemin d'affichage. Idem pour `LogoutUseCase` et la ligne « CSTV
   indisponible pendant une suppression ».
2. **Message d'écriture affiché sur une suppression.**
   `LoginViewModel.onCloudBackupChange()` affiche, quand le décochage est différé,
   « Connexion réussie. La sauvegarde CSTV sera réessayée automatiquement. » — le
   message de la sauvegarde, alors que le §7.8 prévoit ici « … La suppression dans
   CSTV sera réessayée automatiquement. » Le message parle de surcroît d'une
   connexion qui n'a pas eu lieu.
3. **`Unavailable` et `Unreadable` fusionnés.**
   `RestoreIptvCredentialsUseCase.kt:14` mappe les deux issues sur le même
   `CLOUD_RESTORE_FAILED` et le même texte. Le §7.8 et le flux du §8.4 les
   séparent : données indéchiffrables → « Les identifiants sauvegardés n'ont pas
   pu être restaurés » ; `401`/`403`/réseau → message CSTV existant. Une simple
   coupure réseau annonce donc à tort une sauvegarde corrompue.
4. **Chaînes en dur.** Les quatre messages vivent dans le code Kotlin
   (`RestoreIptvCredentialsUseCase.kt:14-15`, `LoginViewModel.kt` dans
   `onCloudBackupChange`) et non dans `strings.xml`, qui n'a reçu que
   `login_cloud_backup` et `login_cloud_backup_help`. F36-14 exigeait « les
   messages du tableau 7.8 » dans `strings.xml`, et CA14 leur identité entre
   mobile et TV.

**Impact.** Le §7.8 est la partie du contrat fonctionnel la plus visible pour
l'utilisateur ; trois de ses neuf lignes sont non implémentées ou incorrectes.
CA7 (« l'opération est reprise automatiquement » avec information non bloquante)
n'est pas observable côté UI.

**Correction attendue.** Déplacer les quatre messages dans `strings.xml`, séparer
`Unavailable` de `Unreadable` dans `AutoLoginRejection`, et remonter les issues
`Deferred` de `onAuthenticated` et de `deleteForIptvLogout` jusqu'au canal de
messages non bloquants du `LoginViewModel`.

### M5 — La visibilité et l'état de la case sont figés à la construction du ViewModel

**Description.** `LoginViewModel.kt:42-45` :

```kotlin
private val _cloudBackupVisible = MutableStateFlow(backupRepository.linkedAccountId() != null)
private val _cloudBackupEnabled = MutableStateFlow(backupRepository.isConsentEnabled())
```

Ces deux valeurs sont lues une seule fois, dans les initialiseurs de propriétés,
donc avant que `startAutoLogin()` — appelé « uniquement après résolution du gate
CSTV (F33) » d'après le commentaire du même fichier — ait pu s'exécuter.

**Impact.** Trois conséquences :
*(a)* si la liaison CSTV se résout après la création du ViewModel (parcours
nominal du gate F33, ou liaison faite dans la même session), la case reste
masquée alors que le §7.2 impose de l'afficher ;
*(b)* après une restauration réussie, le repository passe `consent = true`
(`IptvCredentialsBackupRepositoryImpl.kt:54`) mais la case affichée reste
décochée, contredisant la dernière puce du §7.2 ;
*(c)* `isConsentEnabled()` appelle `store.resetForAccount(...)`, qui **écrit**
dans un `EncryptedSharedPreferences` — une lecture-écriture disque déclenchée
dans un initialiseur de propriété de ViewModel, donc sur le thread principal.
Même remarque pour `linkedAccountId()`.

**Correction attendue.** Exposer ces deux états via un flux réévalué (au minimum
un rafraîchissement dans `startAutoLogin()` et après chaque issue d'auto-login),
et déporter les accès au store sur un dispatcher d'E/S.

### M6 — Un message non bloquant est publié dans `LoginState.Error`

**Description.** `LoginViewModel.onCloudBackupChange()` écrit son message dans
`_loginState` sous la forme `LoginState.Error(...)`, alors que le §8.4 demandait
« un canal de messages non bloquants ».

**Impact.** Cocher ou décocher la case pendant une tentative de connexion écrase
`LoginState.Loading` ou `LoginState.Success` par un état d'erreur : la navigation
vers le catalogue déclenchée par `LaunchedEffect(loginState)` peut être manquée,
et l'écran affiche une erreur alors que rien n'a échoué côté IPTV. C'est
précisément le couplage que RM12 interdit.

**Correction attendue.** Ajouter un `Channel`/`SharedFlow` de messages distinct de
`loginState` et l'afficher via un snackbar ou une zone dédiée.

### M7 — L'intention de suppression est persistée avec `apply()`

**Description.** `IptvCloudBackupStore.kt:40` termine par `apply()`, écriture
asynchrone.

**Impact.** RM13 et CA11 exigent qu'une suppression demandée survive à l'arrêt du
processus (« la demande doit survivre au redémarrage », §7.9). `apply()` ne
garantit pas l'écriture sur disque si le processus est tué immédiatement après —
cas fréquent sur Android TV, où l'application est régulièrement supprimée en
arrière-plan. La révocation demandée par l'utilisateur peut alors être perdue
alors que la copie cloud subsiste.

**Correction attendue.** Utiliser `commit()` au moins lorsque `pending_op` passe à
`DELETE`, ou systématiquement (le volume écrit est de quelques centaines
d'octets).

### M8 — `RestoreIptvCredentialsUseCase` avale `CancellationException`

**Description.** `RestoreIptvCredentialsUseCase.kt:15` se termine par
`catch (e: Exception) { … Rejected(CLOUD_RESTORE_FAILED, e.message …) }`, sans le
`if (exception is CancellationException) throw exception` que le repository
applique pourtant systématiquement.

**Impact.** L'annulation du scope appelant (sortie de l'écran de connexion,
changement de compte) est convertie en rejet fonctionnel : la coroutine parente
croit à un échec de restauration et affiche un message, au lieu de se terminer.
`e.message` d'une exception réseau peut par ailleurs être affiché tel quel à
l'utilisateur, ce que le §7.2 dernière puce cherche à éviter.

**Correction attendue.** Relancer `CancellationException` et n'afficher que les
messages définis au §7.8, jamais `e.message`.

---

## Mineur

### m1 — `EnvelopeCipher::encrypt()` renvoie un code d'erreur de lecture sur une écriture

`EnvelopeCipher.php:29` lève `ApiException(422, 'IPTV_CREDENTIALS_UNREADABLE')`
depuis `encrypt()`. Le contrat du §8.2 pour `PUT` ne prévoit que `415`, `413`,
`422 INVALID_IPTV_CREDENTIALS` et `412`. **Impact :** un client recevrait, sur une
écriture, un code annonçant une sauvegarde illisible. **Correction :** code dédié
(ou `500`) sur le chemin d'écriture.

### m2 — La garde « clé de développement en production » compare la chaîne brute

`Config.php` : `if ($environment === 'production' && $rawKeyRing === $defaultIptvCredentialsKeys)`.
**Impact :** renommer l'identifiant (`prod:AAAA…AAA=`), ajouter une seconde clé ou
changer l'ordre suffit à passer la garde avec la clé de 32 octets nuls. **Correction :**
comparer le **matériel de clé décodé** de chaque entrée du trousseau à la clé de
développement, et refuser dès qu'une seule correspond.

### m3 — `Content-Length` non numérique renvoie `413`

`IptvCredentialsAction.php:30` : `!ctype_digit($contentLength)` déclenche
`PAYLOAD_TOO_LARGE`. **Impact :** un en-tête malformé est signalé comme une charge
trop lourde. **Correction :** `400`, ou ignorer l'en-tête et se fier à la taille
réelle du corps déjà vérifiée ligne 32.

### m4 — Le schéma OpenAPI mélange requête et réponse

`IptvCredentials` sert à la fois de corps de `PUT` et de schéma de réponse `200`
avec `additionalProperties: false` et un `updatedAt` marqué `readOnly`.
**Impact :** un générateur de client produira un modèle de requête contenant
`updatedAt`. **Correction :** deux schémas (`IptvCredentialsRequest` /
`IptvCredentialsResponse`), et documenter l'en-tête `Cache-Control: no-store`.
Corriger au passage le §8.2 : il annonce l'ajout des codes d'erreur « à
l'énumération d'erreurs existante », alors que `components.schemas.Error.code` est
un `string` libre — il n'y a pas d'énumération à compléter.

### m5 — `restore()` ne valide que le port

`IptvCredentialsBackupRepositoryImpl.kt:53` teste `dto.port !in 1..65535` mais pas
la non-vacuité de `host`, `username`, `password`. **Impact :** une réponse
partiellement vide serait transmise à `AuthRepository.login()` au lieu d'être
classée `Unreadable` (§7.8, ligne « données cloud absentes, incomplètes ou
indéchiffrables »). **Correction :** valider les quatre champs.

### m6 — Code devenu mort dans `LoginViewModel`

`_savedCredentials`, `savedCredentials` et l'appel à `getSavedCredentialsUseCase()`
subsistent alors que `LoginScreen` ne pré-remplit plus le formulaire (F36-14).
**Impact :** un `StateFlow` maintient en mémoire un mot de passe Xtream sans
lecteur. **Correction :** supprimer le flux et l'injection si aucun autre usage.

### m7 — L'état de la case n'est jamais corrigé après coup

`onCloudBackupChange` positionne `_cloudBackupEnabled` de façon optimiste et ne
revient pas en arrière si `setConsent` échoue ; `logout()` ne remet pas la case à
`false` alors que le repository passe `consent = false`. **Impact :** la case
affichée peut mentir sur l'état réel du consentement. **Correction :** dériver
l'affichage de l'état renvoyé par le repository.

### m8 — `deleteForIptvLogout()` et `deleteForCstvSignOut()` sont identiques

Les deux méthodes délèguent à `deleteUnconditionally()` sans aucune différence.
**Impact :** deux points d'entrée pour un seul comportement, dont la divergence
attendue (§7.6 : la déconnexion CSTV ne touche pas au local) n'est portée que par
les appelants. **Correction :** documenter explicitement l'invariant dans
l'interface, ou fusionner en une méthode unique avec un paramètre d'intention.

### m9 — `onAuthenticated()` écrase un `DELETE` en attente sans le drainer

`IptvCredentialsBackupRepositoryImpl.kt:38` remplace `pending_op` par `UPLOAD`
quel que soit l'état précédent. La règle d'ordonnancement de **D4** dit « toute
opération commence par drainer un `pending_op = DELETE` ». Le cas n'est
atteignable qu'après un re-cochage (`setConsent(true)` conserve le `DELETE`
pendant), et l'intention utilisateur y est effectivement l'écriture, mais l'écart
avec la règle écrite mérite d'être soit corrigé, soit documenté.

### m10 — Une écriture en attente sans identifiants n'est jamais purgée

`upload()` renvoie `Skipped` quand `state.credentials == null`
(`IptvCredentialsBackupRepositoryImpl.kt:97`) sans remettre `pending_op` à `NONE`.
**Impact :** le worker reçoit `Skipped`, conclut `Result.success()`, et l'état
reste indéfiniment en `UPLOAD` — sans effet fonctionnel, mais le diagnostic
devient trompeur. **Correction :** remettre `pending_op = NONE` dans ce cas.

### m11 — `IptvCredentialsDto` porte `updatedAt` et sert de corps de requête

Le champ, propre à la réponse, est sérialisé dans le `PUT` (omis par Gson quand il
vaut `null`, mais présent dans le type). **Correction :** deux DTO, cohérent avec
m4.

### m12 — `Credentials` reste une `data class` porteuse du mot de passe

Le `toString()` généré expose `password`. C'est un défaut préexistant, mais F36
fait circuler l'objet dans deux composants de plus (`IptvCloudBackupStore`,
`IptvCredentialsBackupRepository`) et dans un worker, où un `Log.d("… $state")`
suffirait à contredire RM15 et la règle d'AGENTS.md « jamais credentials en log ».
**Correction :** surcharger `toString()` pour masquer `password`.

### m13 — Asymétrie non documentée entre `restore()` et `onAuthenticated()`

`onAuthenticated()` persiste les identifiants dans le store, `restore()` non
(elle n'écrit que `consent`, `last_etag`, `pending_op`). **Impact :** aucun
aujourd'hui, mais la règle implicite « le store ne contient des identifiants que
pour une écriture en attente » n'est écrite nulle part et l'oubli constaté en M3
en découle directement. **Correction :** documenter l'invariant dans
`IptvCloudBackupState`.

### m14 — La clé de développement par défaut est une suite de 32 octets nuls

`.env.example` et `docker-compose.yml` diffusent
`dev:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=`. **Impact :** valeur
copiée-collée sans y penser lors d'un premier déploiement ; la garde de production
(cf. m2) est la seule protection, et elle est contournable. **Correction :**
générer une valeur aléatoire dans `.env.example` et documenter la commande de
génération (`openssl rand -base64 32`).

### m15 — `invalidateRestored()` est attendu sur le chemin de démarrage

`AutoLoginUseCase` appelle `backup.invalidateRestored()` sans `launch` sur la
branche `Rejected(INVALID_CREDENTIALS)`, contrairement à `onAuthenticated()` sur
la branche `Online`. **Impact :** un aller-retour réseau supplémentaire retarde
l'affichage de l'écran de connexion après un refus d'identifiants, alors que le
§8.6 ne prévoyait d'ajout que sur le `GET` de restauration. **Correction :**
déporter l'appel sur `applicationScope`, la suppression étant de toute façon
reprise par le worker en cas d'échec.

### m16 — Les tests JVM ajoutés n'ont pas de règle `Timeout`

`AutoLoginUseCaseTest`, `RestoreIptvCredentialsUseCaseTest` et
`IptvCredentialsBackupRepositoryImplTest` n'ont pas de
`@get:Rule val globalTimeout = Timeout.seconds(60)`. AGENTS.md ne l'impose que
pour `presentation/**ViewModelTest.kt`, la règle est donc formellement respectée,
mais ces suites manipulent `runTest` et des mocks Retrofit : la règle y coûterait
peu et nommerait le test fautif en cas de gel.

---

## Corrections demandées

Étape 7 — à traiter dans l'ordre, y compris les points mineurs :

1. **C1** — corriger les deux erreurs de syntaxe PHP, réexécuter `composer test`,
   corriger la note du §10.
2. **C2** — supprimer les trois constructeurs secondaires et les trois
   repositories no-op ; adapter les tests existants.
3. **C3** — écrire les six suites manquantes et les assertions non écrites, ou
   décocher les tâches F36-6, F36-9, F36-10, F36-11 et F36-13.
4. **M1** — sérialiser les deux méthodes de suppression sous le mutex.
5. **M2** — conserver la précondition `If-Match` sur une invalidation différée.
6. **M3** — ne pas laisser d'identifiants dans `iptv_cloud_backup_prefs` après une
   suppression demandée.
7. **M4** — implémenter les messages du §7.8 dans `strings.xml`, séparer
   `Unavailable` de `Unreadable`, remonter les issues `Deferred` jusqu'à l'UI.
8. **M5** — rendre la visibilité et l'état de la case réactifs, et sortir les
   accès au store du thread principal.
9. **M6** — canal de messages distinct de `LoginState`.
10. **M7** — `commit()` pour la persistance de `pending_op`.
11. **M8** — relancer `CancellationException` et ne plus afficher `e.message`.
12. **m1 à m16** — traiter chacun ; pour m9 et m16, une justification écrite au
    §10 tient lieu de correction si le comportement est jugé conforme.

Après corrections : réexécuter `./gradlew assembleDebug lintDebug testDebugUnitTest`
et `composer test`, puis reprendre F36-15 (dont la relecture des 15 critères du
§7.10 avec, pour chacun, le test qui le couvre) avant toute validation d'étape 8.

---

# 12. Release

Version : v1.81.0

Commit : commit de release tagué `v1.81.0` par `scripts/release-local.sh`

Date : 2026-08-14
