# F37 - Verrou de lecture : un seul appareil à la fois

## Informations générales

Status:
RELEASED

Created:
2026-08-14

---

# 1. Description

L'abonnement IPTV du compte n'autorise **qu'une seule connexion simultanée** au
panel Xtream. Aujourd'hui, rien dans l'application n'empêche deux appareils du
même foyer de lancer un flux en même temps : le panel arbitre seul, brutalement,
et le résultat est une lecture qui se fige ou s'arrête sans explication sur l'un
des deux appareils — parfois sur celui qui regardait *avant*.

F37 introduit un **verrou de lecture partagé entre les appareils d'un même
compte CSTV** :

- un appareil qui démarre une lecture Xtream (Live, VOD ou épisode) prend le
  verrou pour la durée de la lecture ;
- un second appareil qui tente de lire pendant ce temps est **bloqué**, avec un
  message nommant l'appareil occupant, et peut choisir de **prendre la main** —
  ce qui coupe proprement la lecture sur le premier appareil, avec un message
  explicite de son côté ;
- le verrou est maintenu par un **heartbeat** et expire tout seul si l'appareil
  qui le détient disparaît (crash, coupure de courant, batterie vide).

Le verrou ne remplace pas la limite du panel : il la rend **lisible et
maîtrisée** côté application, au lieu de la subir.

F37 couvre **le backend CSTV et l'application Android**, indissociables : le
verrou n'a de sens que partagé, et le backend est le seul canal existant entre
les appareils d'un même compte. La livraison se fait donc en deux temps —
déploiement backend d'abord (`scripts/deploy-backend.sh`), release Android
ensuite (`scripts/release-local.sh`) — avec un backend rétrocompatible : une
version d'application antérieure à F37 continue de lire sans verrou.

---

# 2. Contexte

## Ce qui existe

- **Compte CSTV obligatoire** (F33) : chaque utilisateur possède un compte
  backend authentifié en OTP e-mail, avec JWT. Tous les appareils d'un même
  foyer partagent ce compte.
- **Backend CSTV** (T14/T19/T20, F34, F36) : Slim + PostgreSQL, déployé sur
  alwaysdata, routes `/v1/...` derrière `AuthMiddleware`. Il porte déjà les
  profils, les blobs de synchronisation par namespace et, depuis F36, les
  identifiants IPTV chiffrés du compte.
- **Identifiants Xtream uniques par compte** : le multi-comptes Xtream est hors
  périmètre projet. Un compte CSTV = un abonnement panel = une connexion
  simultanée.
- **Profils locaux multiples** (Phase 27) sur ce compte unique : favoris,
  historique et reprise de lecture sont séparés par profil, mais les identifiants
  panel sont communs.
- **`CanPlayContentUseCase`**
  (`app/src/main/java/com/cstv/app/domain/usecase/CanPlayContentUseCase.kt`) :
  gardien de lecture déjà en place. Il répond `Allowed`, `RequiresConnection` ou
  `RequiresReauthentication` avant qu'une URL de flux ne soit construite, et
  laisse déjà passer les médias téléchargés quelle que soit la connectivité.
- **Trois écrans de lecture** consomment ce gardien : `PlayerScreen` (Live),
  `VodPlayerScreen`, `SeriesPlayerScreen`.
- **Aucune notion d'appareil côté backend** : ni table, ni identifiant, ni
  session. Tout est aujourd'hui scopé au compte ou au profil.

## Problèmes

1. **La coupure est subie et incompréhensible.** Quand la limite du panel est
   atteinte, l'utilisateur voit une lecture qui se fige, un buffering infini ou
   une erreur de flux générique. Rien ne lui dit que la cause est un autre
   appareil du foyer, ni lequel. Le réflexe naturel est d'incriminer
   l'application ou la connexion.
2. **Le mauvais appareil peut perdre.** Selon les panels, c'est parfois la
   *première* session qui saute quand la seconde arrive. Quelqu'un qui regarde
   un film depuis vingt minutes peut être éjecté par un appareil resté allumé
   dans une autre pièce.
3. **Un appareil oublié bloque tout.** Une TV laissée en lecture sur une chaîne,
   écran éteint, consomme la connexion en continu. Aucun moyen, depuis un autre
   appareil, de savoir qu'elle la retient ni de la libérer sans se déplacer.
4. **Le problème est invisible au diagnostic.** Les erreurs de flux liées à la
   limite de connexions ressemblent à des erreurs réseau ou à des flux morts :
   impossible de les distinguer dans les logs ou dans les retours utilisateur.

---

# 3. Objectif

- **Rendre la limite explicite** : remplacer une coupure silencieuse par un
  message qui nomme la cause et l'appareil occupant.
- **Rendre l'arbitrage volontaire** : c'est l'utilisateur qui décide de prendre
  la main, pas le panel qui tranche au hasard.
- **Protéger la session en cours** : par défaut, l'appareil qui lit déjà garde
  sa lecture ; le nouvel arrivant est celui qui est bloqué.
- **Ne jamais transformer le verrou en panne** : une indisponibilité du backend
  CSTV ne doit pas empêcher de regarder la télévision.
- **Se libérer tout seul** : aucun état bloquant ne doit survivre à un crash ou
  à une coupure de courant.

---

# 4. Décisions produit prises à l'étape 1

| Sujet | Décision |
|---|---|
| **Comportement en conflit** | **Blocage + prise de main explicite.** Le second appareil affiche une popin nommant l'occupant et la durée de sa lecture, avec deux issues : *Annuler* ou *Prendre la main*. La prise de main coupe la lecture sur le premier appareil, qui affiche à son tour un message explicite (pas une erreur de flux). |
| **Périmètre des contenus** | **Tout flux Xtream : Live, VOD et épisodes de séries.** Toute lecture qui ouvre une connexion au panel prend le verrou. |
| **Médias téléchargés** | **Exclus du verrou.** Un média lu depuis le cache Media3 hors-ligne ne touche pas le panel : il ne prend pas le verrou et n'est jamais bloqué par lui. Cohérent avec la règle déjà appliquée par `CanPlayContentUseCase`. |
| **Backend injoignable** | **Fail-open.** Panne backend, timeout ou absence de réseau vers CSTV → la lecture démarre normalement, sans verrou. Le contrôle est un confort, pas une licence : il ne doit jamais devenir un point de panne unique devant le catalogue. Le risque assumé est une double lecture pendant la panne, arbitrée par le panel comme aujourd'hui. |
| **Détection des appareils morts** | **Heartbeat toutes les 30 s, expiration du verrou à 90 s.** L'appareil qui détient le verrou le rafraîchit pendant la lecture. Sans rafraîchissement pendant 90 s, le verrou est considéré mort et libérable par n'importe quel autre appareil. Délai maximal d'attente après un crash : ~1 min 30. |
| **Portée du verrou** | **Le compte CSTV, tous profils confondus.** Deux profils locaux différents partagent les mêmes identifiants Xtream, donc la même connexion panel : leur donner un verrou chacun ne résoudrait rien. |
| **Nom d'appareil affiché** | **Déduit automatiquement** du modèle et du form factor (ex. « SHIELD Android TV », « Pixel 7 (Mobile) »). Pas de saisie utilisateur, pas de champ à synchroniser. |
| **Désactivation** | **Aucune.** Pas d'interrupteur dans les Paramètres, pas de nombre de connexions configurable. Le contrôle protège d'un problème subi ; le désactiver ne rendrait pas deux flux possibles, cela ramènerait simplement le comportement chaotique actuel. |

---

# 5. Hypothèses

- **H1** — Le compte CSTV est bien le bon dénominateur. Il est obligatoire depuis
  F33 et porte déjà les identifiants IPTV du foyer (F36) : aucun appareil ne peut
  lire sans être authentifié auprès du backend.
- **H2** — Un abonnement = une connexion simultanée. C'est la contrainte connue
  du panel actuel. Si elle évoluait, la valeur serait à revoir — la décision
  « pas de réglage utilisateur » n'interdit pas de faire évoluer la constante
  côté serveur plus tard.
- **H3** — Le verrou est **coopératif**, pas contraignant : il vit entre les
  applications CSTV. Un lecteur tiers (VLC, autre application IPTV) utilisant les
  mêmes identifiants consommera la connexion panel sans que le verrou en sache
  rien. F37 ne prétend pas couvrir ce cas.
- **H4** — Le backend est le seul canal de communication inter-appareils
  disponible. Il n'existe ni push direct entre appareils, ni découverte réseau
  local, et les appareils d'un même compte ne sont pas nécessairement sur le même
  réseau.
- **H5** — Une latence de quelques centaines de millisecondes avant le démarrage
  de la lecture (aller-retour de prise de verrou) est acceptable, y compris au
  zapping. À confirmer à l'étape 3 : c'est le principal risque d'expérience
  utilisateur de cette feature.
- **H6** — L'infrastructure de test du projet reste purement unitaire JVM. Le
  scénario réel « deux appareils » n'est pas automatisable ici : la validation
  portera sur la logique de verrou (backend + use case + ViewModels) avec des
  doubles de test, jamais sur un essai sur device.

---

# Décisions produit prises à l'étape 2

| Sujet | Décision |
|---|---|
| **Zapping Live** | **Conserver le verrou pendant le changement de chaîne.** Le zapping met à jour le contenu lu sans relâcher puis reprendre le verrou, afin qu'un autre appareil ne puisse pas prendre la place au milieu de la navigation. |
| **Pause et arrière-plan** | **Libérer le verrou dès que le flux est effectivement arrêté.** Le verrou suit la connexion Xtream réellement ouverte, pas l'écran ni une session de lecteur en mémoire. |
| **Appareil dépossédé** | **Conserver l'écran de lecture arrêté.** Un message explique qu'un autre appareil a pris la main et propose « Reprendre », qui relance la tentative de lecture et l'arbitrage normal. |
| **Visibilité dans les Paramètres** | **Aucun écran dédié dans F37.** La résolution se fait uniquement lors d'une tentative de lecture ; consulter ou libérer un appareil à distance est hors périmètre. |
| **Erreur du panel après fail-open** | **Message explicite mais prudent.** Si le backend est indisponible, puis que le panel refuse le flux, l'application indique : « Lecture impossible. La limite de connexions simultanées de votre abonnement est peut-être atteinte. » Elle ne présente pas cette cause comme certaine. |

---

# Décisions techniques prises à l'étape 3

| Sujet | Décision |
|---|---|
| **Délai de propagation de la prise de main** (question ouverte 1) | **Heartbeat conservé à 30 s, chevauchement accepté.** L'ancien détenteur apprend la perte à son heartbeat suivant, soit ≤ 30 s. Pendant ce laps, deux flux peuvent coexister : c'est exactement la situation actuelle, arbitrée par le panel, et elle est bornée au lieu d'être permanente. Aucun état « demande en attente » côté backend, aucun rythme de heartbeat variable côté application. |
| **Nettoyage des verrous expirés** (question ouverte 3) | **Expiration passive à la lecture.** Un verrou dont le dernier heartbeat dépasse 90 s est traité comme absent lors de la demande suivante, et écrasé. Aucune tâche planifiée sur alwaysdata ; au pire une ligne périmée par compte reste en base, sans effet fonctionnel. |
| **Surface d'API backend** | **Table `playback_locks` (migration `005`) + trois routes REST** sous `/v1/account/playback-lock` : `POST` (acquérir / prendre la main), `POST .../heartbeat`, `DELETE` (libérer). Aligné sur le style des routes `iptv-credentials` existantes, avec une sémantique HTTP explicite (409 = conflit) plutôt qu'un aiguillage par champ `action`. |
| **Identité d'appareil** | **UUID local persistant + nom déduit.** UUID v4 généré au premier lancement et conservé en `SharedPreferences`, envoyé avec un nom dérivé de `Build.MODEL` et du form factor. Aucune donnée personnelle, aucun identifiant matériel, survit aux mises à jour ; une désinstallation laisse un verrou qui expire seul en 90 s. |

---

# 6. Questions ouvertes

Les trois questions laissées par l'étape 2 sont tranchées :

1. **Délai de propagation de la prise de main** — tranché à l'étape 3 :
   heartbeat maintenu à 30 s, chevauchement ≤ 30 s assumé (voir *Décisions
   techniques prises à l'étape 3*).
2. **Bande passante et batterie du heartbeat** — chiffré à l'étape 3 (voir
   § 8.7) : ~240 requêtes de moins de 300 octets pour un film de deux heures,
   soit ≈ 150 Ko aller-retour, négligeables devant le flux vidéo lui-même et
   devant la charge d'alwaysdata (au plus une requête par appareil et par 30 s).
   Aucun réveil radio supplémentaire n'est provoqué : le flux vidéo maintient
   déjà l'interface active pendant toute la lecture.
3. **Nettoyage des verrous expirés** — tranché à l'étape 3 : expiration passive,
   sans tâche planifiée.

Aucune question ne reste ouverte pour l'étape 4.

---

# 7. Spécification fonctionnelle

## 7.1 Périmètre et vocabulaire

- Un **flux Xtream** est une lecture Live, d'un film VOD ou d'un épisode de
  série qui ouvre une connexion au panel. Il est soumis au verrou.
- Un **média téléchargé** est lu depuis le cache local : il n'ouvre pas de flux
  Xtream, n'acquiert aucun verrou et reste lisible même si un autre appareil
  tient le verrou.
- Le **détenteur** est l'appareil qui possède le verrou actif du compte CSTV.
  Le **demandeur** est l'appareil qui veut démarrer une nouvelle lecture.
- Un verrou concerne tout le compte CSTV, indépendamment du profil local, du
  type de contenu et de l'appareil. Il n'est ni réglable ni désactivable.
- F37 est coopératif : il améliore l'arbitrage entre les versions compatibles de
  CSTV, sans pouvoir empêcher un lecteur tiers ou une ancienne version de
  l'application d'utiliser directement les identifiants Xtream.

## 7.2 Parcours utilisateur

### Démarrer une lecture sans conflit

1. L'utilisateur lance un flux Xtream depuis Live, un film ou un épisode.
2. Après les contrôles de lecture existants (connexion et réauthentification),
   l'application vérifie le verrou partagé.
3. Si le verrou est libre ou expiré, l'application le prend puis démarre la
   lecture normalement. Aucun écran ni message supplémentaire n'est affiché.
4. Tant que le flux est réellement en cours, l'application maintient ce verrou.

### Tenter une lecture pendant qu'un autre appareil lit

1. Le demandeur ne démarre pas le flux et affiche une popin de conflit.
2. La popin indique qu'une lecture est en cours, le nom automatique de
   l'appareil détenteur et sa durée approximative (par exemple « depuis 12 min »).
3. Elle propose exactement deux actions : **Annuler** et **Prendre la main**.
   Annuler abandonne la tentative, ferme la popin et revient à l’écran précédent ;
   elle ne modifie aucune lecture sur l’appareil détenteur.
4. Prendre la main transfère le verrou au demandeur. En cas de succès, celui-ci
   démarre le contenu initialement demandé ; il n'a pas à appuyer une seconde
   fois sur Lecture.
5. Aucune prise de main n'est automatique. Revenir plus tard sur le lecteur,
   rouvrir l'application ou changer de profil ne transfère jamais le verrou à
   l'insu de l'utilisateur.

### Appareil auquel la main est prise

1. L'appareil auparavant détenteur détecte que son verrou n'est plus à lui.
2. Il arrête proprement le flux Xtream et conserve l'écran de lecteur visible.
3. Un message distinct d'une erreur de lecture indique qu'un autre appareil a
   pris la main. Il identifie ce nouvel appareil lorsque son nom est disponible.
4. L'utilisateur peut choisir **Reprendre**. Cette action ne relance pas le
   flux aveuglément : elle repasse par le contrôle de verrou et affiche de
   nouveau la popin de conflit si l'autre appareil lit toujours.
5. L'application ne réessaie jamais automatiquement après une dépossession ;
   cela évite un ping-pong entre appareils.

### Pendant, après et entre deux lectures

- Le verrou est conservé lors d'un zapping Live : l'utilisateur peut changer de
  chaîne sans nouvelle popin et sans fenêtre de disponibilité pour un autre
  appareil.
- Le verrou est libéré dès que le flux est effectivement arrêté : mise en pause,
  arrêt explicite, fin naturelle du contenu, sortie du lecteur ou mise en
  arrière-plan qui interrompt le flux. Il ne dépend donc pas de la simple
  présence de l'écran du lecteur en mémoire.
- Si l'application disparaît sans pouvoir libérer le verrou (crash, batterie ou
  coupure de courant), celui-ci expire après 90 secondes sans heartbeat. Un
  appareil demandeur peut alors le prendre normalement.
- Le changement de profil local ne contourne pas le verrou, car tous les profils
  utilisent le même abonnement Xtream du compte CSTV.

## 7.3 Règles métier

| Référence | Règle |
|---|---|
| F37-R1 | Au plus un verrou actif est accordé par compte CSTV à un instant donné, tous profils et contenus Xtream confondus. |
| F37-R2 | Seuls les flux Xtream sont concernés. Les téléchargements locaux, y compris hors ligne, sont exclus. |
| F37-R3 | Le détenteur conserve la priorité. Un demandeur n'obtient le verrou que s'il est libre, expiré, ou si l'utilisateur confirme explicitement « Prendre la main ». |
| F37-R4 | Un changement de chaîne Live conserve le même verrou et ne déclenche pas de nouvel arbitrage. |
| F37-R5 | Le verrou existe seulement pendant un flux réellement ouvert. Une pause ou toute interruption effective du flux le libère. |
| F37-R6 | L'expiration après 90 secondes sans heartbeat doit rendre le verrou de nouveau acquérable, sans intervention utilisateur. |
| F37-R7 | Si le backend CSTV est indisponible, lent ou injoignable, la lecture reste autorisée sans verrou (fail-open). |
| F37-R8 | Après un fail-open, un refus ultérieur du panel est présenté comme une limite de connexions **possible**, jamais certaine. Les autres erreurs de lecture gardent leur message générique existant. |
| F37-R9 | La prise de main arrête la lecture du détenteur dès qu'il constate le transfert. Le demandeur ne tente pas de forcer directement le panel avant ce transfert. |
| F37-R10 | Aucun réglage ne permet de désactiver ou d'ajuster le verrou ; aucun écran Paramètres ne permet de consulter ou libérer un appareil à distance dans F37. |

## 7.4 États, messages et actions

| Situation | Information présentée | Actions utilisateur |
|---|---|---|
| Verrou libre ou expiré | Aucun message. | La lecture démarre. |
| Verrou détenu par un autre appareil | Popin « Lecture déjà en cours » : « Une lecture est en cours sur {nomAppareil} depuis {durée}. » | **Annuler** ; **Prendre la main**. |
| Transfert accepté sur le demandeur | Aucun écran intermédiaire durable. | La lecture demandée démarre. |
| Transfert détecté sur l'ancien détenteur | Lecteur arrêté : « La lecture a été arrêtée car {nomAppareil} a pris la main. » | **Reprendre** ; retour normal du lecteur. |
| Backend CSTV indisponible avant le démarrage | Aucun message de verrou. | La lecture est tentée normalement. |
| Panel refuse après ce fail-open | « Lecture impossible. La limite de connexions simultanées de votre abonnement est peut-être atteinte. » | Réessayer ou revenir en arrière, selon les contrôles déjà disponibles du lecteur. |
| Erreur de flux sans indice lié au verrou | Message générique existant du lecteur. | Réessayer ou revenir en arrière. |

Les libellés restent localisables. Sur TV, la popin et le message de
dépossession sont entièrement utilisables à la télécommande ; sur mobile, ils
sont utilisables au toucher. Ils ne masquent jamais l'action de retour normale.

## 7.5 Cas limites et comportement dégradé

- Si deux appareils demandent un verrou libre au même instant, un seul démarre.
  L'autre reçoit la popin de conflit avec l'appareil qui l'a obtenu.
- Si le détenteur s'arrête pendant que la popin de conflit est affichée, la
  confirmation de prise de main réévalue l'état courant : elle ne coupe pas une
  lecture déjà terminée et peut démarrer le demandeur directement.
- Si le détenteur tombe hors ligne, cesse d'envoyer son heartbeat ou disparaît,
  aucun autre appareil n'est bloqué au-delà de l'expiration de 90 secondes.
- Si le demandeur annule, échoue à lancer son flux ou quitte le lecteur avant le
  démarrage effectif, il ne doit pas laisser un verrou inutilisable derrière lui.
- Si le nom automatique de l'appareil n'est pas exploitable, le message emploie
  « un autre appareil » sans donnée personnelle ni identifiant de compte.
- La tentative de « Reprendre » après dépossession est volontairement un nouvel
  arbitrage : elle peut être refusée à son tour si l'autre appareil lit encore.
- Les versions de l'application antérieures à F37 restent compatibles avec le
  backend, mais ne participent pas au verrou et conservent donc le comportement
  historique du panel.

## 7.6 Hors périmètre

- Gérer plus d'une connexion simultanée, proposer un réglage du nombre de flux
  ou déduire automatiquement cette limite du panel.
- Bloquer un lecteur tiers ou une ancienne version de l'application qui utilise
  directement les identifiants Xtream.
- Créer une liste d'appareils, un écran de surveillance ou une libération du
  verrou à distance depuis les Paramètres.
- Modifier les règles de téléchargement, de cache local, de profils ou de
  connexion Xtream existantes.

## 7.7 Critères d'acceptation

| Référence | Critère |
|---|---|
| F37-CA1 | Une lecture Live, VOD ou d'épisode sur un appareil sans détenteur démarre sans étape visible supplémentaire. |
| F37-CA2 | Un téléchargement local démarre sans vérification ni acquisition du verrou, y compris quand un autre appareil lit un flux Xtream. |
| F37-CA3 | Un second appareil ne démarre pas son flux tant que l'utilisateur n'a pas choisi « Prendre la main » dans la popin de conflit. |
| F37-CA4 | La popin de conflit affiche le nom de l'appareil détenteur et une durée de lecture approximative, avec les actions Annuler et Prendre la main. |
| F37-CA5 | Annuler laisse intacte la lecture du détenteur et ne crée aucun verrou pour le demandeur. |
| F37-CA6 | Confirmer la prise de main démarre le contenu demandé sur le demandeur et conduit l'ancien détenteur à arrêter son flux avec un message explicite. |
| F37-CA7 | « Reprendre » sur l'ancien détenteur relance le parcours d'arbitrage ; aucune relance ni prise de main n'est automatique. |
| F37-CA8 | Le zapping Live n'affiche pas de conflit et ne libère pas le verrou du même appareil. |
| F37-CA9 | Une pause, un arrêt, une fin de lecture, une sortie du lecteur ou un arrière-plan qui arrête le flux libère le verrou. |
| F37-CA10 | Après 90 secondes sans heartbeat, un verrou abandonné ne bloque plus une nouvelle lecture. |
| F37-CA11 | Une indisponibilité du backend CSTV n'empêche jamais de tenter une lecture Xtream. |
| F37-CA12 | Si le panel refuse après un fail-open, l'utilisateur voit le message prudent sur la limite de connexions possiblement atteinte ; une erreur sans ce contexte reste générique. |
| F37-CA13 | Deux profils locaux distincts du même compte ne peuvent pas lire simultanément deux flux Xtream. |
| F37-CA14 | Les interactions de conflit et de dépossession sont utilisables sur mobile et à la télécommande Android TV. |
| F37-CA15 | Aucun réglage ni écran Paramètres relatif aux appareils ou au verrou n'est ajouté dans F37. |

---

# 8. Spécification technique

## 8.1 Vue d'ensemble

Le verrou vit dans une **ligne unique par compte** en base PostgreSQL, exposée
par trois routes authentifiées. L'application Android l'acquiert avant de
construire l'URL du flux Xtream, le rafraîchit pendant la lecture et le libère
dès l'arrêt effectif. Tout échec de communication avec le backend est traité
comme « pas de verrou » et laisse la lecture partir (F37-R7).

Constantes partagées, définies côté serveur et **renvoyées dans chaque réponse**
pour que la valeur puisse évoluer sans nouvelle release Android (H2) :

| Constante | Valeur | Rôle |
|---|---|---|
| `PLAYBACK_LOCK_TTL_SECONDS` | 90 | Au-delà de ce délai sans heartbeat, le verrou est réputé mort. |
| `PLAYBACK_LOCK_HEARTBEAT_SECONDS` | 30 | Intervalle de rafraîchissement demandé au détenteur. |

## 8.2 Modèle de données (backend)

Nouvelle migration `backend/migrations/005_playback_locks.sql` :

```sql
CREATE TABLE playback_locks (
    account_id   UUID PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE,
    device_id    UUID        NOT NULL,
    device_name  VARCHAR(64) NOT NULL,
    lock_token   UUID        NOT NULL,
    started_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

Justifications :

- `account_id` en **clé primaire** : la règle F37-R1 (au plus un verrou par
  compte) devient une contrainte de schéma, pas une discipline applicative.
- `ON DELETE CASCADE` : cohérent avec `account_iptv_credentials`, un compte
  supprimé n'abandonne pas de ligne.
- `lock_token` : jeton régénéré à **chaque** acquisition. Heartbeat et libération
  l'exigent, ce qui rend impossibles deux courses classiques — un appareil
  dépossédé qui rafraîchit le verrou de son successeur, et un `DELETE` tardif du
  dépossédé qui libérerait la lecture d'un autre appareil.
- `started_at` : sert la durée affichée dans la popin (« depuis 12 min ») ;
  seul le serveur horodate, aucune horloge d'appareil n'est utilisée.
- `device_name` limité à 64 caractères, validé et tronqué côté serveur : il est
  réaffiché à un autre utilisateur du foyer, donc traité comme une entrée non
  fiable.
- Aucun index supplémentaire : chaque accès se fait par clé primaire.

## 8.3 API backend

Toutes les routes sont ajoutées au groupe `/v1` existant, donc **derrière
`AuthMiddleware`** ; le compte provient du JWT, jamais du corps de la requête
(pas d'IDOR possible). Rien n'est modifié sur les routes existantes : une
application antérieure à F37 continue de fonctionner à l'identique.

### `POST /v1/account/playback-lock` — acquérir ou prendre la main

Corps : `{"deviceId": "<uuid>", "deviceName": "SHIELD Android TV", "takeover": false}`

| Cas | Réponse |
|---|---|
| Aucun verrou, verrou expiré (`last_seen_at < NOW() - 90 s`), verrou déjà détenu par le même `deviceId`, ou `takeover: true` | `200` `{"lockToken", "deviceId", "deviceName", "startedAt", "heartbeatSeconds": 30, "ttlSeconds": 90}` |
| Verrou vivant détenu par un autre appareil, sans `takeover` | `409` `{"code": "PLAYBACK_LOCK_HELD", "message": ..., "holder": {"deviceName", "startedAt", "heldForSeconds"}}` |

Le corps de conflit porte des données que `ApiException` ne sait pas transporter
(elle n'a que `status`, `errorCode`, `message`) : l'action construit donc
directement cette réponse `409` via `Json::response(...)->withStatus(409)`, au
lieu de lever une exception. Les autres erreurs (validation, JSON invalide)
restent des `ApiException` classiques.

Ré-acquérir avec le **même** `deviceId` renouvelle le jeton et le heartbeat sans
conflit : un redémarrage d'application ne bloque jamais l'appareil contre
lui-même. `takeover: true` écrase le détenteur et régénère `lock_token`,
`started_at` et `last_seen_at`.

### `POST /v1/account/playback-lock/heartbeat` — prolonger

En-tête `If-Match: "<lockToken>"`, sans corps — même convention que les routes
`iptv-credentials`, et le jeton ne transite pas par la ligne de requête, donc ne
se retrouve pas dans les journaux d'accès.

| Cas | Réponse |
|---|---|
| Jeton courant et non expiré | `200`, `last_seen_at = NOW()`, mêmes champs que l'acquisition |
| Ligne absente, autre `lock_token`, ou verrou expiré | `409` `{"code": "PLAYBACK_LOCK_REVOKED", "holder": {...} ou null}` |

Le `holder` est renseigné quand un autre appareil détient désormais le verrou :
c'est lui qui nomme l'appareil dans le message de dépossession (F37-CA6).

### `DELETE /v1/account/playback-lock` — libérer

En-tête `If-Match: "<lockToken>"`. Réponse `204` **dans tous les cas**, y compris
si la ligne a disparu ou appartient à un autre appareil — la suppression est
alors simplement ignorée. Idempotence volontaire : la libération part souvent
d'un écran qui se ferme, elle ne doit jamais produire d'erreur visible ni
supprimer le verrou d'autrui.

### Codes d'erreur

Deux nouveaux codes, `PLAYBACK_LOCK_HELD` et `PLAYBACK_LOCK_REVOKED`, ajoutés au
`when (code)` de `CstvErrorMapper` **avant** le repli par statut (le `409`
générique y signifie aujourd'hui `LastProfile`).

## 8.4 Composants backend

| Fichier | Nature | Rôle |
|---|---|---|
| `backend/migrations/005_playback_locks.sql` | nouveau | Table du verrou. |
| `backend/src/Playback/PlaybackLockRepository.php` | nouveau | Accès SQL : `findForUpdate`, `upsert`, `touch`, `deleteOwned`. |
| `backend/src/Playback/PlaybackLockService.php` | nouveau | Règles d'acquisition, d'expiration et de prise de main, dans une transaction protégée par `AdvisoryLock::account()`. |
| `backend/src/Http/Action/PlaybackLockAction.php` | nouveau | Trois actions HTTP, extraction du compte depuis l'attribut `account`, lecture de `If-Match`. |
| `backend/src/Shared/Validator.php` | modifié | `playbackLockDevice(array $body): array` — `deviceId` UUID, `deviceName` non vide, tronqué à 64 caractères et nettoyé des caractères de contrôle, `takeover` booléen optionnel. |
| `backend/src/Bootstrap.php` | modifié | Instanciation et enregistrement des trois routes dans le groupe `/v1`. |
| `backend/src/Shared/Config.php` | modifié | Exposition des deux constantes (TTL et heartbeat), surchargeables par variable d'environnement. |

La concurrence est traitée exactement comme pour les identifiants IPTV :
`beginTransaction()` → `AdvisoryLock::account()` → lecture `FOR UPDATE` →
écriture → `commit()`. Deux acquisitions simultanées sur un verrou libre sont
donc sérialisées : la première gagne, la seconde reçoit le `409` avec le nom de
la gagnante (cas limite § 7.5, F37-CA3).

## 8.5 Composants Android

### Nouveaux

| Fichier | Rôle |
|---|---|
| `data/local/storage/DeviceIdentityManager.kt` | UUID v4 persistant en `SharedPreferences` (`device_prefs`, non chiffrées : ce n'est pas un secret) et nom d'appareil déduit de `Build.MODEL` + form factor (`UiModeManager.currentModeType == UI_MODE_TYPE_TELEVISION` → « … (TV) », sinon « … (Mobile) » / « … (Tablette) »). Nom vide ou illisible → l'UI retombe sur « un autre appareil » (§ 7.5). |
| `data/remote/api/CstvPlaybackLockApiService.kt` | Interface Retrofit des trois routes. |
| `data/remote/dto/PlaybackLockDtos.kt` | `PlaybackLockRequestDto`, `PlaybackLockDto`, `PlaybackLockConflictDto` (avec `holder`). |
| `domain/model/PlaybackLock.kt` | `PlaybackLockHolder(deviceName: String?, heldForSeconds: Long)` et `PlaybackLockResult` : `Acquired`, `Held(holder)`, `Revoked(holder)`, `Unavailable` (fail-open). |
| `domain/repository/PlaybackLockRepository.kt` + `data/repository/PlaybackLockRepositoryImpl.kt` | Traduction HTTP → modèle domain. Toute `IOException`, tout dépassement de délai et tout `5xx` deviennent `Unavailable`, jamais une erreur remontée à l'utilisateur. |
| `data/playback/PlaybackLockManager.kt` | Singleton, seul détenteur de l'état du verrou et de la boucle de heartbeat. |
| `domain/usecase/RequestPlaybackLockUseCase.kt` | Portail de lecture : renvoie `NotRequired` pour un média téléchargé, sinon délègue au manager. |
| `domain/usecase/ReleasePlaybackLockUseCase.kt` | Libération explicite depuis les ViewModels. |
| `presentation/components/PlaybackLockConflictDialog.kt` | Popin de conflit partagée par les trois lecteurs, focusable à la télécommande (F37-CA14). |
| `presentation/components/PlaybackTakenOverOverlay.kt` | Bandeau de dépossession + action « Reprendre ». |

### Modifiés

| Fichier | Modification |
|---|---|
| `di/AppModule.kt` | `provideCstvPlaybackLockApiService` sur le Retrofit `@Named("cstv")` existant, liaison du repository. |
| `app/proguard-rules.pro` | `-keep interface com.cstv.app.data.remote.api.CstvPlaybackLockApiService { *; }` — obligatoire (règle AGENTS.md, sinon crash en release uniquement). |
| `data/remote/CstvErrorMapper.kt` | Deux nouveaux codes. |
| `domain/model/CstvError.kt` | `PlaybackLockHeld`, `PlaybackLockRevoked`. |
| `presentation/livetv/LiveTvViewModel.kt` | Après `PlaybackAvailability.Allowed` (ligne ~267), demande de verrou ; états `conflict` / `takenOver` dans l'état d'écran ; libération sur arrêt du flux. |
| `presentation/vod/VodViewModel.kt` (portail ligne ~395), `presentation/series/SeriesViewModel.kt` (ligne ~379) | Même greffe sur leur portail de lecture, avec le `contentId` (`movie_<id>` / `episode_<id>`) pour l'exclusion des téléchargements. |
| `presentation/player/PlayerScreen.kt` | Écoute `onPlayWhenReadyChanged(false)` pour libérer dès la pause explicite, sans libérer pendant le buffering ; affichage popin et bandeau de dépossession ; `zapNext`/`zapPrev` inchangés — ils ne repassent pas par l'acquisition. |
| `presentation/vod/VodPlayerScreen.kt`, `presentation/series/SeriesPlayerScreen.kt` | Libération depuis `onPlayWhenReadyChanged(false)`, `STATE_ENDED` et le `DisposableEffect` de sortie ; mêmes surfaces d'UI. |
| `res/values/strings.xml` (+ variantes) | Libellés de la popin, du bandeau de dépossession et du message prudent après fail-open. |

Aucune nouvelle dépendance Gradle, aucune migration Room : le verrou est un
état de session, il n'a rien à persister localement hormis l'identité
d'appareil.

## 8.6 Cycle de vie du verrou côté application

`PlaybackLockManager` expose un `StateFlow<PlaybackLockState>` :
`Idle`, `Held(token, sinceMs)`, `FailOpen` (backend injoignable, lecture
autorisée sans verrou), `Revoked(holder)`.

- **Acquisition** — `acquire(takeover = false)`. Si l'état est déjà `Held`, aucun
  appel réseau n'est émis : le zapping Live et un simple changement d'épisode ne
  produisent ni requête ni fenêtre de disponibilité (F37-R4, F37-CA8).
  L'appel est encadré par un `withTimeoutOrNull(3 s)` : au-delà, `FailOpen` et la
  lecture part (H5 — le budget de latence au zapping reste borné).
- **Heartbeat** — une seule coroutine, lancée sur le scope applicatif **au moment
  où le verrou est obtenu** et annulée à sa libération. Jamais dans un `init` de
  ViewModel, jamais de boucle périodique inconditionnelle : la règle « boucles
  infinies de tests » d'AGENTS.md s'applique directement ici, et les tests
  pilotent l'horloge virtuelle.
- **Échec de heartbeat** — une erreur réseau ou un `5xx` **n'arrête pas** la
  lecture (F37-R7 vaut aussi pendant la lecture) : le manager retente au cycle
  suivant. Seul un `409 PLAYBACK_LOCK_REVOKED` fait passer en `Revoked` et
  déclenche l'arrêt du flux (F37-R9).
- **Libération** — `release()` annule la boucle puis émet le `DELETE` en
  meilleur effort sur le scope applicatif : la coupure ne doit pas dépendre de la
  survie du ViewModel ou de l'écran (F37-R5, F37-CA9). Un échec est ignoré, le
  verrou expirera de lui-même.
- **Mort du processus** — rien à faire : `last_seen_at` cesse d'avancer et le
  verrou devient acquérable après 90 s (F37-R6, F37-CA10).

## 8.7 Performances, réseau et batterie

- **Latence ajoutée** : un aller-retour avant la première image, uniquement à la
  première lecture d'une session (~150 à 400 ms sur alwaysdata), plafonné à 3 s
  par le délai de garde. Le zapping et le passage à l'épisode suivant n'en
  paient aucun.
- **Volume** : requêtes et réponses sous 300 octets. Un film de deux heures
  représente ~240 heartbeats, soit ≈ 150 Ko cumulés — quatre ordres de grandeur
  sous le flux vidéo lui-même.
- **Charge serveur** : au plus une requête toutes les 30 s par appareil en
  lecture, chacune résolue par un accès en clé primaire ; sans commune mesure
  avec les synchronisations de blobs déjà en place.
- **Batterie** : le flux vidéo maintient déjà la radio active en continu pendant
  toute la lecture ; le heartbeat ne provoque aucun réveil supplémentaire. Hors
  lecture, aucune coroutine ne tourne.

## 8.8 Sécurité

- Le compte vient exclusivement du JWT (`AuthMiddleware`) ; `deviceId` ne sert
  qu'à se reconnaître soi-même à l'intérieur du compte, jamais à autoriser.
- `lock_token` est un UUID v4 côté serveur : il empêche un appareil dépossédé de
  prolonger ou de supprimer le verrou de son successeur.
- `device_name` est validé, tronqué et nettoyé côté serveur avant stockage : il
  est réaffiché sur un autre appareil du foyer.
- Aucune donnée d'abonnement, aucun identifiant Xtream, aucune adresse ni aucun
  contenu regardé ne transite par ces routes : le titre lu n'est jamais envoyé.
- Aucun identifiant matériel n'est collecté (pas d'`ANDROID_ID`) ; l'UUID local
  disparaît à la désinstallation.
- `Cache-Control: no-store` sur les trois routes, comme pour `iptv-credentials`.

## 8.9 Compatibilité et livraison

- **Rétrocompatibilité stricte** : ajout d'une table et de trois routes, aucune
  modification des routes existantes. Une application antérieure à F37 ignore le
  verrou et conserve le comportement historique (§ 7.5, F37-CA15).
- **Ordre de livraison imposé** : `scripts/deploy-backend.sh` (rsync + migration
  `005`) **puis** `scripts/release-local.sh`. L'inverse ferait échouer toutes les
  acquisitions — sans casser la lecture, grâce au fail-open, mais la
  fonctionnalité serait inerte.
- `backend/composer.json` n'est pas touché : aucun `composer.lock` à régénérer.
- Aucun réglage, aucun écran Paramètres n'est ajouté (F37-R10).

## 8.10 Stratégie de tests

Purement automatisée, aucun essai sur appareil (H6, règle AGENTS.md).

**Backend (PHPUnit)**

- `tests/Integration/PlaybackLockTest.php` : acquisition sur verrou libre ;
  conflit sur verrou vivant ; acquisition sur verrou expiré (`last_seen_at`
  reculé) ; ré-acquisition par le même `deviceId` ; prise de main et rotation du
  jeton ; heartbeat avec jeton périmé → `PLAYBACK_LOCK_REVOKED` ; heartbeat sur
  verrou expiré → refus ; `DELETE` idempotent et refus de supprimer le verrou
  d'un autre appareil.
- `tests/Integration/ConcurrencyTest.php` (étendu) : deux acquisitions
  simultanées sur un verrou libre → une seule gagnante.
- `tests/Functional/PlaybackLockApiTest.php` : contrat HTTP des trois routes,
  `401` sans jeton, forme du corps `409` (présence de `holder`), en-têtes de
  sécurité, isolation entre comptes (IDOR).
- `tests/Integration/MigrationTest.php` : la migration `005` s'applique sur une
  base déjà migrée.

**Android (`./gradlew testDebugUnitTest`)**

- `PlaybackLockManagerTest` : acquisition, idempotence quand le verrou est déjà
  détenu (aucun appel réseau supplémentaire), heartbeat cadencé sur horloge
  virtuelle, arrêt de la boucle à la libération, tolérance aux erreurs réseau du
  heartbeat, passage en `Revoked` sur `409`, `FailOpen` sur dépassement de délai.
- `RequestPlaybackLockUseCaseTest` : `NotRequired` pour un téléchargement
  terminé (F37-CA2), fail-open quand le backend est injoignable (F37-CA11).
- `PlaybackLockRepositoryImplTest` : mapping des réponses, dont le corps `409`
  avec et sans `holder`.
- `CstvErrorMapperTest` (étendu) : les deux nouveaux codes priment sur le repli
  par statut `409`.
- Tests de ViewModel (Live, VOD, Séries) : conflit → aucun démarrage de flux ;
  annulation → aucun verrou ; prise de main → démarrage du contenu initialement
  demandé ; dépossession → état arrêté et absence de relance automatique
  (F37-CA3 à CA7) ; zapping → aucune nouvelle acquisition (F37-CA8) ; pause →
  libération (F37-CA9).

Chaque test de ViewModel conserve la règle `@get:Rule Timeout.seconds(60)`
imposée par AGENTS.md.

## 8.11 Risques techniques

| Risque | Portée | Traitement |
|---|---|---|
| Chevauchement de ≤ 30 s après une prise de main | Le panel peut couper l'un des deux flux pendant ce laps | Assumé (décision d'étape 3) ; borné, contre un chevauchement aujourd'hui illimité |
| Latence d'acquisition ressentie au lancement | Expérience utilisateur (H5) | Un seul aller-retour, jamais au zapping, plafonné à 3 s puis fail-open |
| Pause qui ne coupe pas réellement le flux Xtream | Le verrou serait libéré alors que la connexion panel reste ouverte | Libération pilotée par `onPlayWhenReadyChanged(false)` (pause explicite), pas par la présence de l'écran ni par le buffering |
| Verrou orphelin après un crash | Blocage temporaire des autres appareils | Expiration passive à 90 s, sans intervention |
| Backend déployé après l'application | Fonctionnalité inerte | Ordre de livraison documenté ; fail-open garantit qu'aucune lecture n'est empêchée |
| Lecteur tiers ou version antérieure | Consomme la connexion panel hors verrou | Hors périmètre assumé (H3) ; le message prudent après fail-open couvre le symptôme |

---

# 9. Architecture

## 9.1 Répartition des responsabilités

```
Android                                        Backend (Slim + PostgreSQL)
─────────────────────────────────────          ──────────────────────────────
presentation/                                   Http/Action/
  LiveTv / Vod / Series ViewModel                 PlaybackLockAction
  PlayerScreen / VodPlayerScreen /                  ├─ POST   /v1/account/playback-lock
  SeriesPlayerScreen                                ├─ POST   .../heartbeat
  PlaybackLockConflictDialog                        └─ DELETE /v1/account/playback-lock
  PlaybackTakenOverOverlay                                │
        │ états & intentions                             ▼
        ▼                                       Playback/PlaybackLockService
domain/usecase/                                   transaction + AdvisoryLock::account
  CanPlayContentUseCase        (inchangé)         règles d'expiration / prise de main
  RequestPlaybackLockUseCase   (nouveau)                 │
  ReleasePlaybackLockUseCase   (nouveau)                 ▼
        │                                       Playback/PlaybackLockRepository
        ▼                                                │
data/playback/PlaybackLockManager                        ▼
  état du verrou + boucle heartbeat            table playback_locks
        │                                        (1 ligne par compte)
        ▼
data/repository/PlaybackLockRepositoryImpl ──── HTTPS ───┘
  fail-open sur erreur réseau / 5xx
        │
        ▼
data/local/storage/DeviceIdentityManager
  UUID persistant + nom déduit
```

Responsabilités, une par composant :

- **ViewModels** : décident *quand* une lecture est demandée et portent l'état
  d'écran (conflit, dépossession). Ils ne connaissent ni HTTP ni heartbeat.
- **Use cases** : appliquent les règles métier d'exclusion (téléchargement) et
  d'arbitrage, sans état.
- **`PlaybackLockManager`** : unique détenteur de l'état du verrou pour tout le
  processus, donc unique endroit où le zapping peut être distingué d'une
  nouvelle lecture. Singleton parce que trois écrans le partagent et qu'un
  changement d'écran ne doit ni relâcher ni redemander le verrou.
- **Repository** : frontière du fail-open. Aucune couche au-dessus ne voit une
  exception réseau.
- **`PlaybackLockService`** (backend) : seul arbitre. Expiration, priorité au
  détenteur et rotation du jeton sont décidées là, dans une transaction ; aucune
  de ces règles n'est confiée au client.

## 9.2 Flux — lecture sans conflit

```
ViewModel        RequestPlaybackLock   LockManager      Backend
    │  play()          │                   │               │
    ├─ CanPlayContent ─┤ (Allowed)         │               │
    ├─────────────────►│ contentId         │               │
    │                  ├─ téléchargé ? non ┤               │
    │                  ├──────────────────►│ acquire()     │
    │                  │                   ├──────────────►│ POST playback-lock
    │                  │                   │◄──────────────┤ 200 {lockToken}
    │                  │                   ├─ Held, boucle heartbeat (30 s)
    │◄─────────────────┴───────────────────┤ Acquired      │
    ├─ construit l'URL Xtream, lecture ────┤               │
```

Un média téléchargé sort du diagramme à la deuxième ligne : `NotRequired`,
aucune requête réseau (F37-R2).

## 9.3 Flux — conflit puis prise de main

```
Demandeur                    Backend                    Détenteur
   │ POST playback-lock         │                          │  (lecture en cours)
   ├───────────────────────────►│                          │
   │◄─── 409 HELD {holder} ─────┤                          │
   ├─ popin « Lecture en cours sur {nom} depuis {durée} »   │
   │        ├─ Annuler ────► aucun appel, aucun verrou      │
   │        └─ Prendre la main                              │
   ├─ POST playback-lock {takeover:true} ─►│               │
   │◄─── 200 {nouveau lockToken} ──────────┤ (jeton pivoté) │
   ├─ démarre le contenu demandé            │              │
   │                                        │◄─ heartbeat (ancien jeton)
   │                                        ├── 409 REVOKED {holder} ──►│
   │                                        │   arrêt du flux, bandeau  │
   │                                        │   « Reprendre »           │
```

Le délai entre la prise de main et l'arrêt effectif chez le détenteur est
inférieur ou égal à 30 s (décision d'étape 3). « Reprendre » rejoue exactement
le parcours § 9.2 depuis le début, arbitrage compris (F37-CA7).

Si le détenteur s'est arrêté pendant l'affichage de la popin, la confirmation
retombe sur un verrou libre : elle ne coupe rien et démarre directement (§ 7.5).

## 9.4 Flux — fin de lecture et disparition

```
pause / arrêt / fin / sortie du lecteur / arrière-plan coupant le flux
        └─► onIsPlayingChanged(false) │ STATE_ENDED │ onDispose
                └─► release() : boucle annulée, DELETE en meilleur effort

crash / batterie / coupure
        └─► plus aucun heartbeat ─► last_seen_at gèle
                └─► après 90 s, la ligne est traitée comme absente
                        └─► le prochain demandeur acquiert sans conflit
```

## 9.5 Décisions techniques et justifications

| Décision | Justification | Alternative écartée |
|---|---|---|
| Une ligne par compte, `account_id` en clé primaire | La règle « un seul verrou » devient une contrainte de base, pas un invariant à maintenir en code | Table d'historique de sessions : utile pour un écran d'appareils, explicitement hors périmètre (F37-R10) |
| Jeton de verrou tournant | Neutralise les deux courses de la prise de main (heartbeat et libération tardifs du dépossédé) | Comparaison sur `deviceId` seul : un `DELETE` en retard supprimerait le verrou du successeur |
| `If-Match` pour le jeton | Réutilise la convention déjà en place sur `iptv-credentials` et garde le jeton hors des journaux d'accès | Jeton en paramètre d'URL, ou `DELETE` avec corps |
| Expiration passive | Aucune planification à exploiter sur alwaysdata pour un gain nul | Cron de purge |
| `PlaybackLockManager` singleton | Le zapping et le changement d'écran doivent voir le même verrou ; c'est aussi le seul endroit où la boucle de heartbeat peut être unique | Verrou porté par chaque ViewModel : trois cycles de vie concurrents, verrou relâché en changeant d'écran |
| Fail-open dans le repository | Aucune couche supérieure ne peut oublier de traiter l'erreur réseau : la panne devient un état métier (`Unavailable`), pas une exception | `try/catch` répété dans chaque ViewModel |
| Use case dédié plutôt qu'extension de `CanPlayContentUseCase` | `CanPlayContentUseCase` est une consultation sans effet de bord, réutilisée par Accueil et Favoris ; l'acquisition est une mutation. Les garder distincts évite qu'une simple vérification prenne un verrou | Nouvel état dans `PlaybackAvailability` : casse les `when` exhaustifs de cinq ViewModels et mélange consultation et mutation |
| Constantes renvoyées par le serveur | Le rythme et le TTL peuvent évoluer sans release Android (H2) | Constantes figées dans l'application |
| Aucun stockage Room | Le verrou est un état de session : le persister créerait un état incohérent au redémarrage | Entité Room + migration 27 → 28 |

---

# 10. Plan de développement

## Ordre de livraison

Une seule stratégie est défendable et elle est déjà actée : **backend complet et
déployé d'abord** (`scripts/deploy-backend.sh`), **puis** une unique release
Android (`scripts/release-local.sh`, SemVer MINOR). Le backend est
rétrocompatible et l'application est en fail-open, donc chaque moitié reste
inoffensive tant que l'autre n'est pas là. Aucune question d'étape 4 n'a donc
été posée.

Les tâches 1 à 4 forment le lot backend, les tâches 5 à 13 le lot Android. À
l'intérieur du lot Android, les tâches 10, 11 et 12 (les trois lecteurs) sont
indépendantes entre elles et peuvent être menées dans n'importe quel ordre une
fois la tâche 9 terminée.

## Lot backend

- [x] **T1 — Créer la table `playback_locks`**

Objectif :
Ajouter le schéma du verrou, sans code applicatif.

Fichiers :
- `backend/migrations/005_playback_locks.sql`

Validation :
- `tests/Integration/MigrationTest.php` passe : la migration s'applique sur une
  base déjà migrée et est idempotente au second passage.
- Colonnes, clé primaire `account_id` et `ON DELETE CASCADE` conformes au § 8.2.

- [x] **T2 — Règles d'arbitrage du verrou**

Objectif :
Implémenter acquisition, expiration passive, prise de main, heartbeat et
libération, transaction et verrou consultatif compris.

Fichiers :
- `backend/src/Playback/PlaybackLockRepository.php` (nouveau)
- `backend/src/Playback/PlaybackLockService.php` (nouveau)
- `backend/src/Shared/Validator.php` (ajout `playbackLockDevice`)
- `backend/src/Shared/Config.php` (constantes TTL 90 s / heartbeat 30 s)
- `backend/tests/Integration/PlaybackLockTest.php` (nouveau)
- `backend/tests/Integration/ConcurrencyTest.php` (étendu)

Validation :
- Tests d'intégration : verrou libre, verrou vivant refusé, verrou expiré
  repris, ré-acquisition par le même `deviceId`, prise de main avec rotation du
  jeton, heartbeat au jeton périmé refusé, heartbeat sur verrou expiré refusé,
  `DELETE` idempotent et incapable de supprimer le verrou d'un autre appareil.
- Deux acquisitions concurrentes sur un verrou libre : une seule gagnante.

- [x] **T3 — Exposer les trois routes HTTP**

Objectif :
Brancher l'arbitrage sur `/v1/account/playback-lock` derrière `AuthMiddleware`.

Fichiers :
- `backend/src/Http/Action/PlaybackLockAction.php` (nouveau)
- `backend/src/Bootstrap.php` (enregistrement des routes)
- `backend/tests/Functional/PlaybackLockApiTest.php` (nouveau)

Validation :
- Contrat HTTP conforme au § 8.3 : `200` avec `heartbeatSeconds`/`ttlSeconds`,
  `409 PLAYBACK_LOCK_HELD` avec `holder`, `409 PLAYBACK_LOCK_REVOKED`,
  `204` sur `DELETE`.
- `401` sans jeton ; un compte ne voit ni ne libère le verrou d'un autre (IDOR).
- En-têtes de sécurité et `Cache-Control: no-store` présents.

- [ ] **T4 — Déployer le backend**

Objectif :
Mettre les routes en production avant toute release Android.

Fichiers :
- Aucun (exécution de `scripts/deploy-backend.sh`)

Validation :
- `scripts/deploy-backend.sh --dry-run` relu, puis déploiement réel.
- Migration `005` appliquée, `https://cstv.alwaysdata.net/health` vert.
- `backend/composer.json` non modifié : aucun `composer.lock` à régénérer.

## Lot Android

- [x] **T5 — Identité d'appareil**

Objectif :
Fournir un identifiant stable et un nom lisible, sans donnée personnelle.

Fichiers :
- `app/src/main/java/com/cstv/app/data/local/storage/DeviceIdentityManager.kt`
- `app/src/test/java/.../DeviceIdentityManagerTest.kt`

Validation :
- UUID généré une seule fois puis relu à l'identique.
- Nom déduit de `Build.MODEL` et du form factor, tronqué à 64 caractères ;
  modèle vide ou illisible → nom nul, laissé au repli « un autre appareil ».

- [x] **T6 — Accès réseau au verrou**

Objectif :
Parler aux trois routes et transformer toute panne en état métier (fail-open).

Fichiers :
- `data/remote/api/CstvPlaybackLockApiService.kt` (nouveau)
- `data/remote/dto/PlaybackLockDtos.kt` (nouveau)
- `domain/model/PlaybackLock.kt` (nouveau)
- `domain/repository/PlaybackLockRepository.kt` (nouveau)
- `data/repository/PlaybackLockRepositoryImpl.kt` (nouveau)
- `data/remote/CstvErrorMapper.kt`, `domain/model/CstvError.kt` (deux codes)
- `di/AppModule.kt`, `app/proguard-rules.pro`
- `app/src/test/java/.../PlaybackLockRepositoryImplTest.kt`,
  `CstvErrorMapperTest.kt` (étendu)

Validation :
- Mapping des réponses `200`, `409 HELD` (avec et sans `holder`),
  `409 REVOKED`, `204`.
- `IOException`, dépassement de délai et `5xx` → `Unavailable`, jamais
  d'exception propagée.
- Les nouveaux codes priment sur le repli par statut `409`.
- Règle `-keep` présente pour la nouvelle interface Retrofit (AGENTS.md).

- [x] **T7 — Gestionnaire de verrou et heartbeat**

Objectif :
Porter l'état du verrou pour tout le processus et rafraîchir pendant la lecture.

Fichiers :
- `data/playback/PlaybackLockManager.kt` (nouveau)
- `di/AppModule.kt` (scope applicatif si absent)
- `app/src/test/java/.../PlaybackLockManagerTest.kt`

Validation :
- `acquire()` sur état `Held` n'émet aucune requête (base du zapping).
- Heartbeat cadencé à l'intervalle renvoyé par le serveur, sur horloge
  virtuelle ; boucle démarrée à l'acquisition, annulée à la libération — aucune
  tâche périodique inconditionnelle (règle AGENTS.md sur les gels de tests).
- Erreur réseau de heartbeat : lecture conservée, nouvelle tentative au cycle
  suivant. `409 REVOKED` : passage en `Revoked`.
- Dépassement de 3 s à l'acquisition : `FailOpen`.
- `release()` annule la boucle puis émet le `DELETE` en meilleur effort.

- [x] **T8 — Use cases de lecture**

Objectif :
Exposer l'arbitrage aux ViewModels et exclure les téléchargements.

Fichiers :
- `domain/usecase/RequestPlaybackLockUseCase.kt` (nouveau)
- `domain/usecase/ReleasePlaybackLockUseCase.kt` (nouveau)
- `app/src/test/java/.../RequestPlaybackLockUseCaseTest.kt`

Validation :
- `contentId` d'un téléchargement terminé → `NotRequired`, aucun appel réseau.
- Backend injoignable → résultat autorisant la lecture.
- `CanPlayContentUseCase` reste inchangé, ses appelants existants aussi.

- [x] **T9 — Surfaces d'interface partagées**

Objectif :
Fournir la popin de conflit et le bandeau de dépossession, utilisables au
toucher et à la télécommande.

Fichiers :
- `presentation/components/PlaybackLockConflictDialog.kt` (nouveau)
- `presentation/components/PlaybackTakenOverOverlay.kt` (nouveau)
- `app/src/main/res/values/strings.xml` (et variantes)

Validation :
- Popin : nom de l'appareil, durée approximative, actions Annuler et Prendre la
  main ; repli « un autre appareil » quand le nom manque.
- Bandeau : message distinct d'une erreur de lecture, action Reprendre.
- Composables sans logique métier (état hoisté), focus initial défini pour la
  télécommande, action de retour jamais masquée.
- Aucun libellé en dur : tout passe par `strings.xml`.

- [x] **T10 — Greffe sur le lecteur Live**

Objectif :
Arbitrer avant un flux Live, conserver le verrou au zapping, libérer à l'arrêt.

Fichiers :
- `presentation/livetv/LiveTvViewModel.kt` (portail ligne ~267)
- `presentation/player/PlayerScreen.kt`
- `app/src/test/java/.../LiveTvViewModelTest.kt`

Validation :
- Verrou libre → lecture sans écran supplémentaire.
- Conflit → aucun flux démarré ; Annuler → aucun verrou ; Prendre la main →
  démarrage de la chaîne demandée.
- Zapping → aucune nouvelle acquisition, aucun conflit.
- `onPlayWhenReadyChanged(false)`, sortie du lecteur et arrière-plan coupant le flux
  → libération.
- Dépossession → flux arrêté, bandeau affiché, aucune relance automatique.

- [x] **T11 — Greffe sur le lecteur VOD**

Objectif :
Même arbitrage pour un film, avec exclusion des téléchargements.

Fichiers :
- `presentation/vod/VodViewModel.kt` (portail ligne ~395)
- `presentation/vod/VodPlayerScreen.kt`
- `app/src/test/java/.../VodViewModelTest.kt`

Validation :
- Film téléchargé → lecture immédiate même si un autre appareil détient le
  verrou, aucun appel réseau de verrou.
- Conflit, prise de main, dépossession et libération : mêmes attentes que T10.
- Pause et fin de film libèrent le verrou.

- [x] **T12 — Greffe sur le lecteur Séries**

Objectif :
Même arbitrage pour un épisode, y compris au passage à l'épisode suivant.

Fichiers :
- `presentation/series/SeriesViewModel.kt` (portail ligne ~379)
- `presentation/series/SeriesPlayerScreen.kt`
- `app/src/test/java/.../SeriesViewModelTest.kt`

Validation :
- Épisode téléchargé exclu du verrou.
- Enchaînement d'épisodes : le verrou déjà détenu n'est pas relâché puis
  repris.
- Conflit, prise de main, dépossession et libération : mêmes attentes que T10.

- [x] **T13 — Message prudent après fail-open**

Objectif :
Distinguer un refus de flux survenu après une acquisition impossible d'une
erreur de lecture ordinaire (F37-R8).

Fichiers :
- `data/playback/PlaybackLockManager.kt` (exposition de l'état `FailOpen`)
- `presentation/player/PlayerScreen.kt`, `presentation/vod/VodPlayerScreen.kt`,
  `presentation/series/SeriesPlayerScreen.kt`
- `app/src/main/res/values/strings.xml`
- Tests de ViewModel correspondants

Validation :
- Erreur de flux après un fail-open → message « … est peut-être atteinte »,
  formulé sans certitude.
- Erreur de flux sans fail-open → message générique existant, inchangé.

- [x] **T14 — Non-régression et cohérence finale**

Objectif :
Vérifier l'ensemble avant la review d'étape 6.

Fichiers :
- Aucun (exécution et corrections éventuelles)

Validation :
- `./gradlew assembleDebug`, `./gradlew lintDebug`, `./gradlew testDebugUnitTest`
  sans erreur, tests des phases précédentes compris.
- Suite backend PHPUnit verte.
- Aucun réglage ni écran Paramètres ajouté (F37-R10, F37-CA15).
- Chaque critère d'acceptation couvert par au moins un test automatisé, selon
  la table ci-dessous.

## Couverture des critères d'acceptation

| Critère | Tâche(s) | Vérification |
|---|---|---|
| F37-CA1 | T10, T11, T12 | Lecture sans conflit, aucun écran supplémentaire |
| F37-CA2 | T8, T11, T12 | `NotRequired` pour un téléchargement terminé |
| F37-CA3 | T2, T10, T11, T12 | `409` serveur + aucun flux démarré côté client |
| F37-CA4 | T3, T9 | `holder` renvoyé et rendu dans la popin |
| F37-CA5 | T9, T10 | Annuler : aucun appel, aucun verrou |
| F37-CA6 | T2, T7, T10 | Prise de main, rotation du jeton, `Revoked` au heartbeat |
| F37-CA7 | T9, T10 | Reprendre rejoue l'arbitrage, aucune relance automatique |
| F37-CA8 | T7, T10 | `acquire()` sans requête quand le verrou est déjà détenu |
| F37-CA9 | T7, T10, T11, T12 | Libération sur pause, arrêt, fin, sortie, arrière-plan |
| F37-CA10 | T2 | Verrou expiré repris sans intervention |
| F37-CA11 | T6, T7, T8 | Fail-open sur panne, timeout et `5xx` |
| F37-CA12 | T13 | Message prudent contre message générique |
| F37-CA13 | T1, T2 | Clé primaire `account_id` : un verrou par compte, tous profils |
| F37-CA14 | T9 | Focus télécommande et utilisation au toucher |
| F37-CA15 | T14 | Aucun écran ni réglage ajouté |

Rappel H6 : le scénario réel « deux appareils » n'est pas automatisable ici. Il
est validé par des doubles de test aux deux extrémités (suite backend pour
l'arbitrage, gestionnaire et ViewModels pour le client), jamais sur appareil.

---

# 11. Notes de développement

## Étape 5 — 2026-08-14

- Implémentés : T1 à T3 et T5 à T13. Le backend crée une unique ligne de
  verrou par compte, arbitre les courses via transaction et verrou consultatif,
  puis expose acquisition, heartbeat et libération derrière le JWT.
- Le client conserve une identité locale non sensible, applique le fail-open,
  ne lance le heartbeat qu'après acquisition et réutilise le verrou pendant le
  zapping Live et l'enchaînement d'épisodes. Les lecteurs affichent le conflit,
  arrêtent la lecture à la révocation et libèrent sur pause/arrêt/sortie.
- Validations automatisées exécutées : suites backend Integration (80 tests) et
  Functional (41 tests), puis `./gradlew testDebugUnitTest assembleDebug
  lintDebug`.
- T4 reste volontairement ouverte : le déploiement alwaysdata exige un commit
  et un push distincts ; aucune action de release ou de production n'est menée
  dans l'étape 5.

## Décisions produit prises à l’étape 7

| Sujet | Décision |
|---|---|
| Annulation du conflit | « Annuler » abandonne la tentative et revient à l’écran précédent ; le verrou et la lecture de l’autre appareil restent inchangés. |

## Étapes 7 et 8 — 2026-08-14

- L’arbitrage est désormais porté par les trois ViewModels : les écrans ne font
  qu’afficher l’état hoisté et commander Media3. Reprise, prise de main et
  ré-acquisition après pause passent chacune par une intention explicite.
- Le gestionnaire sérialise les acquisitions, conserve le TTL serveur,
  annule réellement l’appel dépassant son délai et se réinitialise lors d’une
  déconnexion CSTV ou d’un changement de compte. Le nom d’appareil absent est
  traité côté interface, jamais envoyé comme libellé français.
- Le backend calcule ses durées avec l’horloge PostgreSQL, accepte le nom
  d’appareil facultatif et couvre la ré-acquisition, l’isolation des comptes
  et le contrat `204`.
- Validations automatisées : `testDebugUnitTest`, `assembleDebug`, `lintDebug`
  et PHPUnit Docker (162 tests, 803 assertions) verts. La validation sur
  appareil est explicitement hors critères automatisés du projet.

---

# 12. Review

Status: RESOLVED

Revue d'étape 6 — 2026-08-14. Périmètre : lot backend (T1–T3) et lot Android
(T5–T13) tels que présents dans l'arbre de travail. Aucun code modifié.
Vérifications exécutées : lecture intégrale des fichiers créés et des diffs,
`./gradlew testDebugUnitTest` (BUILD SUCCESSFUL).

## Critique

### C1 — La logique d'arbitrage vit dans les Composables, pas dans les ViewModels

**Description.** `PlayerScreen.kt`, `VodPlayerScreen.kt` et
`SeriesPlayerScreen.kt` portent l'intégralité du parcours de verrou :
`LaunchedEffect(lockAttempt)` pour l'acquisition, `lockGranted` pour
conditionner la préparation du flux, `lockConflict` / `takenOverBy` pour les
popins, la prise de main et la reprise. Les ViewModels n'exposent que trois
passe-plats (`acquirePlaybackLock`, `releasePlaybackLock`, `playbackLockState`)
et leur portail de lecture (`LiveTvViewModel` ~l.267, `VodViewModel` ~l.395,
`SeriesViewModel` ~l.379) n'a pas été touché.

**Impact.** Contraire à AGENTS.md l.74 (« jamais logique métier direct dans
Composable ») et à §8.5 / T10–T12 qui imposaient la greffe sur le portail de
lecture. Conséquence directe : la logique n'est pas testable par la suite JVM,
ce qui explique l'absence totale de tests ViewModel (C5) ; elle est triplée
à l'identique dans trois écrans, avec déjà trois divergences de comportement
(clé d'effet, garde `STATE_ENDED`, `collectAsState` vs
`collectAsStateWithLifecycle`) ; et l'état de conflit repose sur des `remember`
non `rememberSaveable`, donc perdu à toute recréation d'activité.

**Correction attendue.** Déplacer l'arbitrage dans les trois ViewModels
(états `conflict` / `takenOver` dans l'état d'écran, intentions
`onTakeOver()` / `onCancel()` / `onResume()`), ne laisser aux Composables que
l'affichage des deux surfaces et la commande du lecteur, puis couvrir par des
tests de ViewModel.

### C2 — « Reprendre » ne relance jamais la lecture, et l'écran est sans issue

**Description.** Dans les trois lecteurs :
`onResume = { takenOverBy = null; lockAttempt = 0 }`. Dans le cas nominal
(dépossession après une acquisition normale), `lockAttempt` vaut déjà `0` : la
clé de `LaunchedEffect(lockAttempt)` ne change pas, l'effet n'est pas
relancé, aucune nouvelle acquisition n'est tentée et `lockGranted` reste
`false`. En parallèle, `PlaybackTakenOverOverlay` déclare
`onDismissRequest = {}` et n'offre aucune action de retour.

**Impact.** F37-CA7 non satisfait (« Reprendre relance le parcours
d'arbitrage ») et §7.4 violé (« Ils ne masquent jamais l'action de retour
normale ») : après une prise de main, l'utilisateur reste sur un lecteur arrêté
dont le seul bouton est inopérant. Le comportement n'est correct que dans le
sous-cas où une prise de main avait déjà eu lieu (`lockAttempt > 0`).

**Correction attendue.** Déclencher explicitement une nouvelle tentative
d'arbitrage (compteur incrémenté ou appel direct au ViewModel), et rendre le
bandeau de dépossession dismissible / laisser le retour lecteur accessible.

### C3 — Après une pause, la lecture reprend sans verrou

**Description.** `onPlayWhenReadyChanged(false)` appelle
`releasePlaybackLock()` dans les trois lecteurs, mais aucun chemin ne
ré-acquiert le verrou quand `playWhenReady` repasse à `true` : `lockGranted`
reste `true`, donc l'effet de préparation n'est pas rejoué et le manager est
en `Idle`.

**Impact.** F37-R1 et F37-R5 violés de façon durable : après la première pause,
l'appareil lit un flux Xtream sans détenir de verrou, un autre appareil peut
l'acquérir et lire simultanément sans qu'aucun des deux ne le détecte — le
scénario exact que la fonctionnalité doit supprimer. Ni le heartbeat (annulé)
ni la révocation ne peuvent plus arrêter cette lecture.

**Correction attendue.** Re-passer par l'arbitrage à la reprise
(`playWhenReady == true` après une libération), en réutilisant le même chemin
que l'acquisition initiale — donc en réinitialisant l'état qui autorise la
préparation du flux.

### C4 — Prise de main automatique après la première prise de main

**Description.** `takeover = lockAttempt > 0` est évalué à chaque exécution de
l'effet d'acquisition. Or `lockAttempt` n'est jamais remis à zéro après un
succès, et les clés incluent le contenu (`details.streamId` en VOD,
`currentEpisode.id` en Séries). Après une première prise de main, tout
changement de film ou d'épisode ré-émet une acquisition avec `takeover: true`,
qui contourne aussi la garde d'idempotence de `PlaybackLockManager.acquire`.

**Impact.** F37-R3 et F37-CA3 violés : « Aucune prise de main n'est
automatique ». Si un autre appareil a récupéré le verrou entre-temps, sa
lecture est coupée sans confirmation de l'utilisateur, et une requête réseau
inutile est émise à chaque enchaînement d'épisode.

**Correction attendue.** Rendre le drapeau de prise de main ponctuel (consommé
par la tentative qui le porte), et le dissocier de la clé de contenu.

### C5 — Couverture de tests très en deçà de §8.10 et des critères de validation

**Description.** Fichiers de test réellement livrés : `PlaybackLockManagerTest`
(un seul test), extension de `CstvErrorMapperTest`,
`Integration/PlaybackLockTest` (deux tests) et `Functional/PlaybackLockApiTest`
(un test). Manquent, alors qu'ils sont nommés dans §8.10 et dans la
« Validation » des tâches cochées : `PlaybackLockRepositoryImplTest` (T6),
`RequestPlaybackLockUseCaseTest` (T8), `DeviceIdentityManagerTest` (T5), les
tests de ViewModel Live/VOD/Séries (T10–T13), l'extension de
`ConcurrencyTest` (T2), et côté backend la ré-acquisition par le même
`deviceId`, le heartbeat sur verrou expiré, le `401` et l'isolation entre
comptes sur `heartbeat`/`DELETE`, le contrat `204`. Le manager n'est testé ni
en `FailOpen` (dépassement de délai), ni en `Revoked` (409), ni en tolérance à
une erreur réseau de heartbeat, qui sont pourtant les trois comportements que
T7 devait démontrer.

**Impact.** Sur les 15 critères d'acceptation, seuls CA10, CA13 (backend) et
partiellement CA3/CA4/CA8 sont couverts par un test ; CA1, CA2, CA5, CA6, CA7,
CA9, CA11, CA12, CA14, CA15 ne le sont par aucun. Les défauts C2, C3 et C4
auraient été détectés par les tests demandés. Les tâches T5 à T13 sont cochées
`[x]` alors que leur bloc « Validation » n'est pas rempli, et la note d'étape 5
présente la validation automatisée comme complète.

**Correction attendue.** Écrire les tests listés au §8.10 après C1 (qui les
rend possibles), puis ne recocher les tâches qu'une fois leur validation
effective.

## Majeur

### M1 — Nom d'appareil de repli « Cet appareil », en dur dans la couche data

**Description.** `PlaybackLockManager.acquire` :
`val name = identity.deviceName() ?: "Cet appareil"`. Ce libellé français est
codé en dur hors de `strings.xml`, dans `data/playback`, et il est envoyé au
serveur puis stocké dans `device_name`.

**Impact.** L'autre appareil du foyer affiche « Une lecture est en cours sur
Cet appareil depuis 12 min », message absurde du point de vue du lecteur.
§7.5 et T9 prévoyaient exactement l'inverse : nom absent côté serveur, repli
« un autre appareil » côté interface (`playback_lock_other_device` existe déjà
et devient inatteignable). Viole aussi la règle « aucun libellé en dur » de T9.

**Correction attendue.** Ne pas fabriquer de nom localisé côté client :
envoyer une valeur neutre non affichable ou faire accepter l'absence de nom par
la route, et laisser l'interface appliquer son repli.

### M2 — Aucun focus initial sur la popin et le bandeau (Android TV)

**Description.** `PlaybackLockConflictDialog` et `PlaybackTakenOverOverlay`
utilisent `AlertDialog` Material3 sans `FocusRequester` ni
`focusProperties`, alors que les autres surfaces TV du projet en définissent un
(correctif B28 sur la popin de mise à jour).

**Impact.** F37-CA14 et le critère de validation de T9 (« focus initial défini
pour la télécommande ») non satisfaits : sur SHIELD, la popin de conflit risque
d'apparaître sans cible focalisée, donc inutilisable à la télécommande — sur
l'écran même qui bloque la lecture.

**Correction attendue.** Demander le focus sur « Prendre la main » (popin) et
sur « Reprendre » (bandeau) à l'affichage, et vérifier le comportement dans un
test de rendu comparable à ceux déjà présents (`*TvLayoutTest`).

### M3 — État du verrou jamais réinitialisé au changement de compte

**Description.** `PlaybackLockManager` est un `@Singleton` dont l'état ne
retombe à `Idle` que par `release()`. Aucun point d'accroche sur la
déconnexion, le changement de compte CSTV ou la réauthentification.

**Impact.** Un `Held` résiduel fait court-circuiter l'acquisition suivante
(retour anticipé sans appel réseau) : le nouveau compte lit sans jamais poser
de verrou. Un `Revoked` résiduel déclenche, dès l'ouverture du lecteur suivant,
le bandeau « un autre appareil a pris la main » sans qu'aucune prise de main
n'ait eu lieu, et le heartbeat résiduel continue d'envoyer un jeton
appartenant à l'ancien compte.

**Correction attendue.** Réinitialiser explicitement le manager (annulation du
heartbeat + `Idle`) sur déconnexion et changement de compte.

### M4 — Le dépassement de délai laisse un verrou orphelin

**Description.** `withTimeoutOrNull(3_000L)` autour de `repository.acquire`.
Au-delà de 3 s, le client passe en `FailOpen`, mais la requête peut avoir
abouti côté serveur : la ligne existe, avec un `lock_token` que le client
n'a jamais reçu.

**Impact.** Le verrou n'est ni rafraîchi ni libérable (le `DELETE` exige le
jeton) : il bloque tout autre appareil pendant 90 s, alors que l'appareil
demandeur croit être en fail-open. Cas d'autant plus probable qu'alwaysdata est
mutualisé et que le budget est fixé à 3 s.

**Correction attendue.** Annuler réellement la requête au dépassement (délai
porté par l'appel réseau) et/ou tenter une ré-acquisition avec le même
`deviceId` au démarrage de lecture suivant, qui reprend la ligne existante sans
conflit.

## Mineur

- **m1** — `PlaybackLockManager.heldResult()` renvoie `ttlSeconds = 90L` en dur
  alors que §8.1 impose que la valeur vienne du serveur, précisément pour
  pouvoir évoluer sans release. Conserver le TTL reçu dans l'état `Held`.
- **m2** — `PlaybackLockAction::ifMatch()` :
  `trim($h, ' \t\"')` est en guillemets simples, la liste de coupe contient donc
  les caractères `\` et `t`, pas une tabulation. Sans effet sur un UUID
  hexadécimal, mais l'intention est fausse. Utiliser `" \t\"" `.
- **m3** — `PlaybackLockService::expired()` et `holder()` comparent des
  horodatages écrits par PostgreSQL (`NOW()`) à l'horloge PHP (`time()`), alors
  que §8.2 pose « seul le serveur horodate ». Calculer l'expiration et
  `heldForSeconds` en SQL.
- **m4** — `Validator::playbackLockDevice` : `preg_replace('/[\x00-\x1F\x7F]/u',
  …)` renvoie `null` sur une chaîne UTF-8 invalide, ce qui produit un `422`
  « deviceName must contain printable characters » trompeur. Distinguer
  l'encodage invalide du nom vide.
- **m5** — La détection « téléchargement terminé » est dupliquée mot pour mot
  entre `CanPlayContentUseCase` et `RequestPlaybackLockUseCase` (même DAO, même
  clé de compte, même statut). Extraire un `IsContentDownloadedUseCase` partagé.
- **m6** — `PlaybackLockRequestResult.Held` réemballe un `PlaybackLockResult.Held`
  (double enveloppe consommée par `result.result.holder`), et la branche
  `is PlaybackLockResult.Revoked -> Allowed` est morte : `acquire` ne mappe
  jamais `PLAYBACK_LOCK_REVOKED`. Simplifier en transportant directement le
  `PlaybackLockHolder`.
- **m7** — La popin affiche `(heldForSeconds / 60).coerceAtLeast(1)` : une
  lecture démarrée depuis 3 secondes est annoncée « depuis 1 min ». Prévoir un
  libellé « à l'instant » sous la minute.
- **m8** — Annuler dans la popin appelle `handleClose()`, donc ferme le
  lecteur, alors que §7.2 dit « Annuler ferme la popin et ne modifie aucune
  lecture ». Comportement défendable mais non spécifié : trancher et aligner
  spec et code.
- **m9** — §8.11 et T10 désignaient `onIsPlayingChanged(false)` comme
  déclencheur de libération ; l'implémentation utilise
  `onPlayWhenReadyChanged`. Le choix est meilleur (il évite de libérer à chaque
  mise en tampon), mais il n'est documenté nulle part. Mettre la spec à jour.
- **m10** — Incohérence entre lecteurs : `collectAsStateWithLifecycle` en Live,
  `collectAsState` en VOD et Séries ; garde `STATE_ENDED` présente en Séries
  seulement. Uniformiser (conséquence directe de C1).
- **m11** — `releasePlaybackLock()` est appelé sans condition, y compris sur le
  chemin `NotRequired` d'un média téléchargé, où l'écran ne détient rien.
  Inoffensif aujourd'hui (l'état est `Idle`), fragile dès qu'un autre porteur
  de verrou coexistera.
- **m12** — `PlaybackLockManager.acquire` n'est pas sérialisé : deux appels
  concurrents (deux effets, ou un enchaînement rapide) peuvent émettre deux
  acquisitions réseau et écraser mutuellement l'état. Un `Mutex` suffit.
- **m13** — `PlaybackLockRepositoryImpl.responseToResult` traite tout `4xx`
  non reconnu (dont `401`) en `Unavailable`, donc en fail-open silencieux. Le
  comportement est conforme à F37-R7, mais mérite un commentaire explicite :
  une session expirée devient indistinguable d'une panne.
- **m14** — `PlaybackLockManagerTest` couvre un seul scénario et dépend de
  `release()` pour arrêter la boucle `while (true)` du heartbeat : un futur
  test qui l'oublie gèlera `testDebugUnitTest` (AGENTS.md §boucles infinies).
  Ajouter la libération en `@After` ou une garde explicite.

## Corrections demandées

Étape 7 — à traiter dans cet ordre, C1 conditionnant la testabilité du reste :

1. C1 — remonter l'arbitrage du verrou dans les trois ViewModels, ne laisser
   que le rendu dans les Composables.
2. C3 — ré-acquérir le verrou à la reprise de lecture après libération.
3. C2 — rendre « Reprendre » effectif et le bandeau de dépossession quittable.
4. C4 — rendre la prise de main ponctuelle, jamais reconduite implicitement.
5. M1 à M4 — nom de repli, focus télécommande, réinitialisation au changement
   de compte, verrou orphelin après dépassement de délai.
6. m1 à m14 — corrections mineures, y compris les deux écarts de spec (m8, m9)
   qui doivent se solder par une mise à jour de §7.2 / §8.11 ou du code.
7. C5 — écrire l'ensemble des tests du §8.10, vérifier que chaque critère
   d'acceptation est couvert, puis mettre à jour les cases de T5 à T13.

---

# 13. Release

Version : v1.82.0

Commit : commit de release tagué `v1.82.0` par `scripts/release-local.sh`

Date : 2026-08-14
