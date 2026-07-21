# T3 - Factoriser les trois lecteurs vidéo

## Informations générales

Type:
Technical

Status:
RELEASED

Created:
2026-07-20

Target version:
v1.48.29

---

# 1. Description

Factorise le socle commun des trois lecteurs de CSTV sans changer le comportement visible.

---

# 2. Contexte

`PlayerScreen` (Live, ~950 l.), `VodPlayerScreen` (~1025 l.) et `SeriesPlayerScreen` (~1120 l.) dupliquent massivement : construction ExoPlayer/NextLib, gestion PiP (+ workaround relayout SurfaceView), KEEP_SCREEN_ON, overlay auto-masqué, resize mode, buffering/erreurs, sélection de pistes, boucle de suivi de position. Toute correction de lecteur doit être appliquée 3× (vécu sur la session Cast). ~3100 lignes pour ~60 % de code commun.

---

# 3. Spécification fonctionnelle

## 3.1 Objectif fonctionnel

T3 est un refactoring interne. Il ne doit introduire aucune fonctionnalité,
aucune suppression et aucun changement visible ou perceptible dans les lecteurs
Live, VOD et Séries. Après migration, chaque lecteur doit conserver son
interface, ses commandes, ses transitions, ses messages et ses règles de
persistance actuels sur mobile comme sur Android TV.

## 3.2 User stories

- En tant qu'utilisateur Live, je veux continuer à regarder et zapper entre les
  chaînes, consulter l'EPG en cours/suivant et ouvrir le tiroir des chaînes sans
  différence de comportement.
- En tant qu'utilisateur VOD, je veux continuer à lire un film en ligne ou
  téléchargé, reprendre ma lecture et piloter la lecture sans perdre ma
  progression ni mes préférences de pistes.
- En tant qu'utilisateur Séries, je veux continuer à reprendre un épisode,
  naviguer entre les épisodes et profiter de l'enchaînement automatique sans
  perdre la progression de chaque épisode.
- En tant qu'utilisateur mobile, je veux conserver le mode Picture-in-Picture
  quand il est pris en charge par Android, avec une vidéo correctement
  redimensionnée et sans contrôles superposés dans la fenêtre PiP.
- En tant qu'utilisateur Android TV, je veux conserver toutes les commandes
  D-pad et le comportement plein écran, sans exposer d'action PiP.

## 3.3 Parcours et comportements communs invariants

1. L'ouverture d'un contenu prépare le flux, lance automatiquement la lecture
   et maintient l'écran allumé pendant toute la durée du lecteur.
2. Un indicateur est affiché pendant la mise en tampon puis disparaît lorsque
   la lecture est prête ou qu'une erreur est affichée.
3. Les contrôles sont visibles à l'ouverture, peuvent être affichés ou masqués
   par l'interaction utilisateur et se masquent après 5 secondes d'inactivité
   lorsque la lecture est active. Ils restent masqués en PiP.
4. L'action de format parcourt, dans cet ordre, `FIT`, `FILL`, `ZOOM`, puis
   revient à `FIT`. Le choix est persisté et un libellé temporaire confirme le
   nouveau format.
5. Sur mobile compatible (API 24+), l'action PiP conserve la lecture et utilise
   le ratio de la vidéo lorsqu'il est valide, avec un repli 16:9. Le
   `SurfaceView` est relayouté à chaque entrée ou sortie du PiP.
6. Fermer le lecteur ou utiliser Retour arrête proprement la lecture, détache la
   surface vidéo, libère le player et restaure le comportement normal de mise en
   veille de l'écran.
7. La construction du player conserve NextLib/FFmpeg prioritaire et le fallback
   de décodeur afin de lire notamment les pistes EAC3, AC3 et DTS.
8. Le chargement, l'erreur de lecture, l'action Réessayer et l'action Retour
   conservent leur présentation et leur comportement actuels.

## 3.4 Règles propres au Live

- Le flux demandé est d'abord ouvert en HLS (`m3u8`). En cas d'échec, le
  lecteur tente une fois le format TS avant d'afficher l'erreur.
- La chaîne initiale et la chaîne active sont identifiées par `streamId`, y
  compris lorsqu'elles proviennent des contenus récemment regardés.
- Sur TV, Haut/Bas zappe respectivement vers la chaîne précédente/suivante. Sur
  mobile, le balayage vertical conserve le même comportement. Le zapping boucle
  aux extrémités de la liste et retente le nouveau flux en `m3u8`.
- Le tiroir des chaînes conserve la liste de zapping initiale, le choix d'une
  catégorie, le focus sur la chaîne active et la sélection d'une autre chaîne.
  Retour ferme d'abord ce tiroir avant de fermer le lecteur.
- L'overlay conserve le logo, le nom et la résolution de la chaîne, l'EPG en
  cours et suivant, l'heure et la progression du programme courant.
- L'EPG et sa jauge restent strictement informatifs : aucun seek, timeshift ou
  catch-up n'est ajouté.

## 3.5 Règles propres à la VOD

- Un film reprend à la position fournie lorsqu'elle est positive. Une VOD
  téléchargée reste lisible hors ligne via sa clé de cache stable ; sinon la
  lecture utilise le réseau.
- Les commandes lecture/pause, recul de 10 secondes, avance de 10 secondes,
  seek par la barre de progression et Recommencer restent disponibles, avec
  bornage entre le début et la fin du contenu.
- La position affichée est actualisée chaque seconde pendant la lecture et
  sauvegardée toutes les 5 secondes lorsque position et durée sont valides.
  Elle est aussi sauvegardée à la fermeture.
- Une lecture terminée, ou quittée à moins de 15 secondes de la fin, efface la
  position de reprise et ferme le lecteur en fin naturelle.
- Les pistes audio et sous-titres disponibles/supportées restent
  sélectionnables. La préférence du film est prioritaire, puis la préférence
  globale sert de repli. La désactivation des sous-titres reste mémorisée.

## 3.6 Règles propres aux Séries

- Un épisode reprend à sa position mémorisée et utilise sa clé de cache stable
  pour permettre la lecture hors ligne lorsqu'il est téléchargé.
- Les commandes et règles de suivi de position sont identiques à la VOD, mais
  la position est enregistrée pour l'épisode effectivement en cours.
- Les actions Épisode précédent et Épisode suivant ne sont proposées que si un
  épisode correspondant existe, y compris lors d'un changement de saison.
- Passer manuellement à un autre épisode sauvegarde la position significative
  de l'épisode quitté ; une position terminée ou invalide est effacée.
- À la fin naturelle d'un épisode, sa position est effacée et l'épisode suivant
  démarre automatiquement. En fin de série, le lecteur se ferme.
- Les préférences audio et sous-titres restent communes à la série et sont
  réappliquées à chaque épisode, avec repli sur la préférence globale.

## 3.7 Cas limites et gestion des erreurs

- Une erreur de flux ne doit jamais afficher de stack trace brute ni faire
  planter l'application : elle arrête l'indicateur de chargement et affiche le
  message utilisateur existant avec Réessayer et Retour.
- Réessayer relance le media courant sans recréer l'écran ni perdre son
  contexte (chaîne, film ou épisode actif).
- Une durée inconnue, négative ou nulle ne déclenche aucune sauvegarde de
  progression et ne produit pas de valeur hors limites dans l'interface.
- Une liste Live vide ne permet ni zapping ni ouverture du tiroir. Une chaîne
  initiale absente de la liste reste néanmoins lisible.
- L'absence d'EPG, d'image, de piste alternative ou d'épisode
  précédent/suivant masque uniquement l'information ou l'action concernée,
  sans bloquer la lecture. L'absence d'activité compatible PiP ne doit pas faire
  planter le lecteur.
- Une piste non supportée reste signalée comme telle et ne peut pas être
  sélectionnée.
- Les ressources et listeners sont libérés même si le composable disparaît sans
  passage par l'action Retour.

## 3.8 Critères d'acceptation

- [ ] Les trois types de contenus démarrent, lisent, mettent en pause le cas
  échéant, reprennent et se ferment comme avant le refactoring.
- [ ] Les overlays, leur masquage automatique, le buffering, les erreurs,
  Réessayer, Retour, le changement de format et la résolution affichée sont
  inchangés sur mobile et TV.
- [ ] Le PiP fonctionne sur mobile compatible pour Live, VOD et Séries ; aucune
  action PiP n'est affichée sur TV.
- [ ] Le zapping Live, le tiroir des chaînes et l'EPG informatif fonctionnent,
  y compris avec une chaîne issue des contenus récemment regardés.
- [ ] La reprise, le seek, la persistance de progression et l'effacement en fin
  de contenu fonctionnent pour un film et pour chaque épisode.
- [ ] Les pistes audio/sous-titres et leurs préférences fonctionnent pour la VOD
  et les Séries, y compris plusieurs pistes partageant la même langue.
- [ ] Les contenus VOD et Séries téléchargés restent lisibles hors ligne.
- [ ] Les transitions manuelles et automatiques entre épisodes conservent la
  bonne URL, le bon titre, les bonnes pistes et la bonne position.
- [x] Aucun support catch-up/timeshift, Cast ou protocole autre que Xtream Codes
  n'est introduit.
- [x] Les tests automatisés ciblant les comportements factorisés passent, ainsi
  que `testDebugUnitTest`, `assembleDebug` et `lintDebug`.

---

# 4. Spécification technique

## 4.1 Périmètre et principes

La factorisation est limitée à la mécanique de présentation et de cycle de vie
identique. Les règles métier, la construction des URL Xtream, les appels aux
ViewModels, les choix de piste, les décisions de reprise et la navigation restent
dans l'écran appelant. Le package `presentation/player/core/` ne dépend ni de
Room, ni d'un Repository, ni d'un ViewModel, ni des modèles Live/VOD/Séries.

Les composables du socle sont stateless dès que leur état représente une décision
fonctionnelle ; ils reçoivent cet état et leurs callbacks de l'écran. Seuls les
états techniques de cycle de vie nécessaires à leur fonctionnement (player,
listener PiP, temporisation interne) peuvent être mémorisés dans le socle.

## 4.2 Composants à extraire

### `ExoPlayerCore.kt`

`rememberManagedExoPlayer(useOfflineCache: Boolean): ExoPlayer` construit un
unique player par instance d'écran avec :

- `NextRenderersFactory` en mode `PREFER` et decoder fallback activé ;
- la `DefaultMediaSourceFactory` de cache en lecture seule seulement lorsque
  `useOfflineCache` vaut `true` ;
- `stop`, détachement de surface et `release` dans un unique `DisposableEffect`.

`PlayerScreen` passe `false`, afin de conserver exactement la source réseau Live.
`VodPlayerScreen` et `SeriesPlayerScreen` passent `true`, afin de conserver la
lecture transparente des téléchargements. La clé de cache (`movieContentId` ou
`episodeContentId`) et le `MediaItem` restent construits par l'écran, car ils
dépendent du contenu métier.

Le core est l'unique propriétaire du `release()`. Les écrans peuvent arrêter le
player et détacher sa surface lors d'une fermeture explicite, mais ne doivent pas
le libérer une seconde fois.

### `PlayerLifecycleCore.kt`

Le fichier fournit :

- `KeepScreenOnEffect()` pour poser et retirer `FLAG_KEEP_SCREEN_ON` ;
- `rememberPipState(playerView: PlayerView?): Boolean` pour lire l'état initial,
  écouter ses changements et relayout le `PlayerView` à l'entrée **et** à la
  sortie du PiP ;
- éventuellement un helper pur d'entrée PiP recevant l'activité et la taille
  vidéo, qui applique le ratio vidéo valide ou le repli 16:9.

L'effet PiP doit être reconfiguré lorsque la référence de `PlayerView` change ;
il ne doit donc pas capturer une référence nulle ou obsolète. L'état PiP est lu
par l'écran pour masquer ses overlays et ses éléments propres (tiroir Live,
dialogue de pistes), sans que le core connaisse ces éléments.

### `PlayerOverlayCore.kt`

`PlayerOverlayHost` ne fournit qu'un conteneur plein écran à slots : gestion de
la visibilité auto-masquée, gradients haut/bas et emplacements de contenu. Il
reçoit `isVisible`, `isPlaying`, `isInPipMode`, `onVisibilityChange` et des
slots `topBar`, `centerContent`, `bottomContent`.

Le host ne doit pas imposer de titre, de libellé, d'icône ni de disposition
spécifique : les barres existantes ont des structures différentes et doivent
conserver exactement leurs dimensions, positions et actions. L'écran conserve
les gestes qui lui sont propres (zapping vertical Live, D-pad, fermeture du
tiroir) et peut désactiver l'auto-masquage lorsqu'un sous-overlay est ouvert.

Les overlays de buffering et d'erreur peuvent être des composables visuels
stateless communs, recevant le message et les callbacks `onRetry`/`onClose`.
Ils ne relancent pas directement le player et ne connaissent pas le contenu.

### `PositionTrackerCore.kt`

Le tracker générique relève position et durée toutes les secondes uniquement
pendant la lecture. Son contrat doit permettre à l'écran de fournir :

- `onPositionUpdate(positionMs, durationMs)` pour l'UI ;
- `onPeriodicSave(positionMs, durationMs)` avec une cadence par défaut de cinq
  secondes ;
- `onDispose(positionMs, durationMs)` pour laisser l'écran appliquer sa règle de
  fin de contenu ou de sauvegarde.

Il est clé sur le player et l'identifiant du contenu actif : un changement
d'épisode réinitialise la cadence et ne peut pas sauvegarder l'épisode précédent
avec la position du suivant. Le tracker ignore les durées ou positions invalides
et n'applique jamais lui-même les règles Room de suppression à 15 secondes de la
fin.

## 4.3 Responsabilités par écran après migration

| Écran | Reste dans l'écran | Consomme le socle |
| --- | --- | --- |
| `PlayerScreen` | URL Live, fallback `m3u8` vers TS, zapping, tiroir, EPG, gestion Retour du tiroir | player réseau, keep-screen-on, PiP, overlay, états communs |
| `VodPlayerScreen` | URL/cache key film, reprise, transport et seek, pistes/préférences film, sauvegarde/effacement de position | player avec cache, keep-screen-on, PiP, overlay, tracker |
| `SeriesPlayerScreen` | URL/cache key épisode, changement et enchaînement d'épisodes, pistes/préférences série, sauvegarde/effacement par épisode | player avec cache, keep-screen-on, PiP, overlay, tracker clé par épisode |

La sélection de pistes et son dialogue restent hors du socle pour cette tâche :
ils dépendent des préférences film/série et leur extraction n'est pas nécessaire
pour factoriser le cycle de vie ciblé. Elle pourra faire l'objet d'une tâche
technique distincte si souhaitée.

## 4.4 Fichiers impactés

À créer ou finaliser :

- `presentation/player/core/ExoPlayerCore.kt`
- `presentation/player/core/PlayerLifecycleCore.kt`
- `presentation/player/core/PlayerOverlayCore.kt`
- `presentation/player/core/PositionTrackerCore.kt`

À migrer :

- `presentation/player/PlayerScreen.kt`
- `presentation/vod/VodPlayerScreen.kt`
- `presentation/series/SeriesPlayerScreen.kt`

À compléter côté tests : tests unitaires des helpers purs extraits (cadence et
filtrage de position si isolés), puis les tests existants de persistance du mode
de redimensionnement et de navigation entre épisodes. Aucun changement de
navigation, de dépendance Gradle, de Retrofit, de Room ou de migration n'est
requis.

## 4.5 Risques techniques et garde-fous

- Une `DefaultMediaSourceFactory` de cache appliquée au Live changerait son
  comportement de source : le cache est donc opt-in et réservé à VOD/Séries.
- Un double `release()` ou des listeners conservant un `PlayerView` périmé peut
  provoquer crash, fuite ou écran noir : ownership unique et clés d'effets
  explicites sont obligatoires.
- Le workaround PiP existant relayout le `PlayerView` entier à chaque transition.
  Le remplacer par un relayout limité à la seule sortie constitue une régression
  possible et est interdit.
- Un overlay générique trop prescriptif modifierait le design Phase 60. Le host
  utilise donc des slots et les écrans gardent leurs contenus et leurs tailles.
- Le passage d'un épisode à l'autre peut faire persister le mauvais contenu si
  la coroutine de suivi n'est pas annulée/recréée avec l'identifiant actif.
- Les `Player.Listener` spécifiques restent responsables des transitions Live,
  VOD et Séries ; leur déplacement dans le core étendrait le périmètre et est
  exclu.

---

# 5. Architecture

```text
PlayerScreen / VodPlayerScreen / SeriesPlayerScreen
        │
        ├── règles métier et état UI propres à chaque contenu
        ├── préparation du MediaItem, listener Media3 et callbacks ViewModel
        │
        └── presentation/player/core/
              ├── rememberManagedExoPlayer(cache: oui/non)
              ├── KeepScreenOnEffect + rememberPipState
              ├── PlayerOverlayHost (+ overlays visuels stateless)
              └── tracker de position paramétrable
```

Le flux de lecture reste piloté par l'écran : il obtient le player du core,
construit le `MediaItem` depuis ses données métier, le prépare, puis écoute les
événements Media3 pour mettre à jour son état hoisté. Le core renvoie uniquement
des primitives de cycle de vie et des callbacks de position ; il ne déclenche ni
navigation, ni appel ViewModel, ni écriture de base locale.

La libération suit un seul chemin : fermeture explicite → arrêt/détachement par
l'écran → sortie de composition → libération par `rememberManagedExoPlayer`.
La fermeture implicite du composable emprunte directement la dernière étape, ce
qui couvre également les changements de navigation.

---

# 6. Plan de développement

### [x] Tâche 1 — Extraire et intégrer le player géré

Objectif :
Créer le composable de création/configuration/lifecycle ExoPlayer commun, avec
NextLib, decoder fallback et cache de lecture optionnel.

Fichiers :

- `app/src/main/java/com/cstv/app/presentation/player/core/ExoPlayerCore.kt`
- `app/src/main/java/com/cstv/app/presentation/player/PlayerScreen.kt`
- `app/src/main/java/com/cstv/app/presentation/vod/VodPlayerScreen.kt`
- `app/src/main/java/com/cstv/app/presentation/series/SeriesPlayerScreen.kt`

Validation :

- Live utilise une source réseau sans cache de téléchargement.
- VOD et Séries utilisent le cache de lecture seule avec leur `customCacheKey`.
- Une seule instance est créée par écran et un seul chemin possède `release()`.
- Les trois écrans compilent sans modifier leurs URLs ni leur `MediaItem`.

### [x] Tâche 2 — Extraire le cycle de vie écran et PiP

Objectif :
Centraliser `KEEP_SCREEN_ON`, l'observation PiP et le workaround de relayout du
`PlayerView`, sans déplacer les actions propres aux écrans.

Fichiers :

- `app/src/main/java/com/cstv/app/presentation/player/core/PlayerLifecycleCore.kt`
- `app/src/main/java/com/cstv/app/presentation/player/PlayerScreen.kt`
- `app/src/main/java/com/cstv/app/presentation/vod/VodPlayerScreen.kt`
- `app/src/main/java/com/cstv/app/presentation/series/SeriesPlayerScreen.kt`

Validation :

- Les listeners sont ajoutés et retirés sans fuite, avec une clé d'effet qui
  prend en compte la référence `PlayerView`.
- Le relayout est déclenché à l'entrée et à la sortie du PiP.
- Le PiP reste masqué sur TV et l'activité incompatible ne provoque aucun crash.
- Le flag écran allumé est retiré à la sortie de composition.

### [x] Tâche 3 — Extraire l'hôte visuel d'overlay

Objectif :
Fournir un host à slots pour visibilité, auto-masquage et gradients, sans
imposer le contenu ou la disposition des overlays Live/VOD/Séries.

Fichiers :

- `app/src/main/java/com/cstv/app/presentation/player/core/PlayerOverlayCore.kt`
- `app/src/main/java/com/cstv/app/presentation/player/PlayerScreen.kt`
- `app/src/main/java/com/cstv/app/presentation/vod/VodPlayerScreen.kt`
- `app/src/main/java/com/cstv/app/presentation/series/SeriesPlayerScreen.kt`

Validation :

- Les contrôles restent visibles à l'ouverture puis se masquent après 5 secondes
  dans les mêmes conditions qu'avant.
- Les tailles, positions, actions, textes et gradients propres à chaque écran
  restent inchangés.
- Le host n'appelle ni ViewModel, ni navigation, ni player directement.
- Les sous-overlays Live (tiroir) et VOD/Séries (pistes) suspendent correctement
  l'auto-masquage.

### [x] Tâche 4 — Extraire le suivi et la sauvegarde de position

Objectif :
Créer une boucle paramétrable d'actualisation UI et de sauvegarde périodique,
indépendante des règles métier de chaque contenu.

Fichiers :

- `app/src/main/java/com/cstv/app/presentation/player/core/PositionTrackerCore.kt`
- `app/src/main/java/com/cstv/app/presentation/vod/VodPlayerScreen.kt`
- `app/src/main/java/com/cstv/app/presentation/series/SeriesPlayerScreen.kt`
- `app/src/test/java/com/cstv/app/presentation/player/core/PositionTrackerCoreTest.kt`
  (ou test du helper pur extrait)

Validation :

- Mise à jour UI toutes les secondes pendant la lecture et sauvegarde par défaut
  toutes les 5 secondes.
- Durée/position nulles, négatives ou inconnues ignorées.
- Pause, fermeture et changement d'épisode déclenchent les callbacks attendus
  sans sauvegarder le mauvais identifiant.
- Les règles d'effacement à la fin ou dans les 15 dernières secondes restent
  dans l'écran et sont couvertes par tests.

### [x] Tâche 5 — Migrer le lecteur Live

Objectif :
Remplacer les blocs communs de `PlayerScreen` par le socle, sans déplacer les
spécificités zapping, tiroir et EPG.

Fichiers :

- `app/src/main/java/com/cstv/app/presentation/player/PlayerScreen.kt`
- `app/src/main/java/com/cstv/app/presentation/player/core/*.kt`

Validation :

- Fallback `m3u8` → TS, zapping D-pad/balayage et boucle de liste inchangés.
- La chaîne issue des contenus récemment regardés reste la chaîne active.
- Tiroir, catégories, focus et EPG informatif restent fonctionnels.
- Aucun seek, timeshift ou catch-up n'est introduit.

### [x] Tâche 6 — Migrer le lecteur VOD

Objectif :
Remplacer les blocs communs de `VodPlayerScreen` en conservant reprise, seek,
cache hors ligne, pistes et préférences propres au film.

Fichiers :

- `app/src/main/java/com/cstv/app/presentation/vod/VodPlayerScreen.kt`
- `app/src/main/java/com/cstv/app/presentation/player/core/*.kt`

Validation :

- Lecture réseau et lecture d'un film téléchargé fonctionnent.
- Reprise, pause, seek ±10 secondes, slider et Recommencer restent inchangés.
- Préférences audio/sous-titres du film et fallback global restent appliqués.
- Sauvegarde périodique, fermeture et effacement en fin de film restent corrects.

### [x] Tâche 7 — Migrer le lecteur Séries

Objectif :
Remplacer les blocs communs de `SeriesPlayerScreen` en conservant la navigation
entre épisodes et les préférences partagées de la série.

Fichiers :

- `app/src/main/java/com/cstv/app/presentation/series/SeriesPlayerScreen.kt`
- `app/src/main/java/com/cstv/app/domain/model/SeriesEpisodeNavigation.kt`
- `app/src/main/java/com/cstv/app/presentation/player/core/*.kt`

Validation :

- Reprise et sauvegarde restent liées à l'épisode actif.
- Précédent/suivant, changement de saison et autoplay en fin d'épisode restent
  corrects, y compris en fin de série.
- Les préférences audio/sous-titres de la série sont réappliquées à chaque
  épisode sans écraser un choix manuel de piste.
- Un épisode téléchargé reste lisible hors ligne.

### [x] Tâche 8 — Tests et validation de non-régression

Objectif :
Vérifier le socle et les trois intégrations avant de passer en review.

Fichiers :

- `app/src/test/java/com/cstv/app/presentation/player/core/*Test.kt`
- Tests existants concernés par mode de redimensionnement et navigation séries
- Aucun fichier de production supplémentaire attendu, sauf corrections issues
  des tests

Validation :

- Tests unitaires du tracker et des helpers purs présents et passants.
- `./gradlew testDebugUnitTest` passe.
- `./gradlew assembleDebug` passe.
- `./gradlew lintDebug` passe.
- Aucun diff fonctionnel hors périmètre T3 n'est introduit.

---

# 7. Notes de développement

- 2026-07-21 — Socle `presentation/player/core/` intégré aux trois lecteurs.
  Le cache de lecture reste opt-in (VOD/Séries uniquement), le core est l'unique
  propriétaire de `release()` et le tracker est re-clé sur l'épisode actif.
- 2026-07-21 — Tests unitaires et `assembleDebug` validés ; tests unitaires
  ajoutés pour la cadence de sauvegarde. Les erreurs lint initialement
  introduites par le core Media3 ont été corrigées.
- 2026-07-21 — Tâche 8 close. Validation de non-régression exécutée :
  `testDebugUnitTest` OK (helper pur `PositionSaveCadence` couvert :
  cadence 5 ticks + filtrage des positions/durées invalides), `assembleDebug`
  OK. Les quatre erreurs `NewApi` de `RecommendationEngine.kt` ont ensuite été
  corrigées par un accès Kotlin compatible API 21 ; `lintDebug` est désormais
  vert. Aucun diff fonctionnel hors périmètre T3.
- 2026-07-21 — Corrections de review appliquées : sauvegarde finale routée par
  le tracker, cadence fondée sur le temps réel, intervalle exprimé en ms et
  `findActivity` rendu interne. `testDebugUnitTest` et `assembleDebug` OK.

---

# 8. Review

Revue technique du 2026-07-21 (Opus). Portée : socle `presentation/player/core/`
(`ExoPlayerCore`, `PlayerLifecycleCore`, `PlayerOverlayCore`, `PositionTrackerCore`)
et intégration dans les trois écrans migrés. Aucune modification de code effectuée.

Status: REVIEWED

## Points positifs

- Factorisation nette : -373/+85 lignes sur les trois écrans, imports du socle
  cohérents, aucune URL ni `MediaItem` déplacé (règles métier restées dans
  l'écran, conforme 4.1).
- Ownership `release()` unique : seul `rememberManagedExoPlayer` libère
  (`DisposableEffect(exoPlayer)`) ; les écrans n'appellent que `stop()` +
  `clearVideoSurface()`. Aucun double `release()` (garde-fou 4.5 respecté).
- Cache opt-in correct : Live `useOfflineCache = false`, VOD/Séries `true`.
- PiP : relayout `INVISIBLE → post(VISIBLE)` déclenché à l'entrée ET à la sortie
  via un `Consumer` unique ; ratio borné à `0.4184f..2.39f` avec repli 16:9
  (garde-fou 4.5 respecté).
- `isAutoHideBlocked` correctement câblé : Live → `showChannelList`,
  VOD/Séries → `showTrackDialog` (critère tâche 3 satisfait).
- `PositionSaveCadence` isolé, pur et testé.

## Critique

Aucun.

## Majeur

### MAJ-1 — `TrackPlayerPosition` n'implémente pas le callback `onDispose` du contrat 4.2

Description : la spec 4.2 (`PositionTrackerCore`) prévoit un callback
`onDispose(positionMs, durationMs)` pour laisser l'écran appliquer sa règle de
fin de contenu / sauvegarde à la fermeture. L'implémentation ne l'expose pas ; la
sauvegarde finale + règle des 15 dernières secondes reste dupliquée dans un
`DisposableEffect` propre à chaque écran (`VodPlayerScreen` l. 351-361,
`SeriesPlayerScreen` l. 412-419), quasi identique.

Impact : objectif de dé-duplication de T3 partiellement manqué sur la règle de
fermeture. Toute évolution de cette règle doit encore être appliquée 2× — exactement
le problème que T3 vise à supprimer (cf. contexte §2). Dette maintenue.

Correction attendue : soit exposer `onDispose(pos, dur)` dans `TrackPlayerPosition`
et y router les blocs de fermeture des deux écrans, soit acter la décision de
laisser cette règle dans l'écran et mettre à jour la spec 4.2 en conséquence.

Status: RESOLVED (2026-07-21)

`TrackPlayerPosition` expose désormais `onTrackerDispose(positionMs, durationMs)`
et exécute ce callback dans son `DisposableEffect`, clé sur le player et le
contenu. Les deux écrans lui confient leur règle métier de sauvegarde/effacement
à 15 secondes de la fin ; leurs listeners ne conservent plus que le retrait du
listener Media3.

### MAJ-2 — Couverture de test insuffisante sur la navigation entre épisodes

Description : seul `PositionSaveCadence` est testé (2 cas). `computeNextEpisode`
(`SeriesEpisodeNavigation`), central pour Précédent/Suivant, changement de saison
et autoplay de fin d'épisode, n'a aucun test unitaire, alors qu'il s'agit d'un
helper pur et que les critères 3.8 exigent des transitions manuelles/automatiques
correctes, y compris en fin de série.

Impact : régression possible non détectée sur les bornes (premier/dernier épisode,
saut de saison, épisode absent) — précisément les cas limites 3.6/3.7.

Correction attendue : ajouter `SeriesEpisodeNavigationTest` couvrant fin de saison,
fin de série (retour `null`) et épisode initial.

Status: RESOLVED (2026-07-21)

Faux positif de revue : [SeriesEpisodeNavigationTest] contient déjà 13 cas,
dont épisode initial, fin de saison, fin de série, carte vide, saisons trouées,
listes non triées et navigation précédente. La suite est exécutée par
`testDebugUnitTest`.

## Mineur

### MIN-1 — Cadence de sauvegarde couplée aux ticks, dérive possible sur pause/reprise

Description : `TrackPlayerPosition` re-clé son `LaunchedEffect` sur `isPlaying` ;
à chaque reprise, la première itération lit la position immédiatement (avant
`delay`), incrémentant `PositionSaveCadence`. La cadence compte des ticks, pas du
temps réel.

Impact : sur pauses/reprises répétées, la sauvegarde « toutes les 5 s » peut
dériver de quelques secondes. Faible (spec 3.5 approximative), pas de perte de
données.

Correction possible : aligner la cadence sur le temps écoulé réel, ou réinitialiser
proprement à la reprise.

Status: RESOLVED (2026-07-21)

La cadence utilise maintenant un temps monotone (`System.nanoTime`) et conserve
le dernier instant de sauvegarde par contenu. Les pauses/reprises ne peuvent plus
accélérer une sauvegarde par accumulation de ticks.

### MIN-2 — Couplage implicite `intervalTicks` × `updateIntervalMs`

Description : la garantie « 5 secondes » n'est vraie que si `updateIntervalMs` vaut
1000. Un futur appel modifiant `updateIntervalMs` casserait silencieusement la
cadence sans que rien ne le signale.

Correction possible : documenter le couplage sur `TrackPlayerPosition`, ou dériver
l'intervalle de sauvegarde d'une durée en ms plutôt que d'un nombre de ticks.

Status: RESOLVED (2026-07-21)

`PositionSaveCadence` et `TrackPlayerPosition` reçoivent désormais
`saveIntervalMs` (5 000 ms par défaut), indépendant de `updateIntervalMs`.

### MIN-3 — `findActivity()` exposé en public dans le package core

Description : `Context.findActivity()` (ExoPlayerCore l. 17) est une fonction
top-level sans visibilité restreinte ; elle élargit involontairement la surface
d'API publique du package `core`.

Correction possible : passer en `internal`.

Status: RESOLVED (2026-07-21)

`Context.findActivity()` est désormais `internal`.

## Corrections demandées

- [x] MAJ-1 : fermeture routée via `onTrackerDispose` du tracker.
- [x] MAJ-2 : couverture `computeNextEpisode` vérifiée dans la suite existante.
- [x] MIN-1 / MIN-2 / MIN-3 : cadence temporelle, intervalle en ms et visibilité
  interne appliqués.

Note de validation : les quatre erreurs `lintDebug` `NewApi` préexistantes dans
`domain/model/RecommendationEngine.kt` ont été corrigées le 2026-07-21 sans
changement de comportement (`map[key] ?: 0.0`, compatible minSdk 21).

## Validation finale du 2026-07-21

Status: PARTIAL — DEVICE REQUIRED

Validations réussies :

- `git diff --check` : OK.
- `./gradlew testDebugUnitTest` : OK.
- `./gradlew assembleDebug` : OK.
- `./gradlew lintDebug` : OK.
- Contrôle statique du périmètre : cache désactivé pour Live et activé pour
  VOD/Séries ; unique appel à `release()` dans le core ; callbacks de fermeture
  routés par le tracker ; aucun ajout Cast/catch-up/timeshift/autre protocole.

Validation non terminée :

- Validation fonctionnelle Live/VOD/Séries, mobile/TV, PiP et lecture hors ligne :
  non exécutable dans cet environnement, car la commande `adb` est absente et
  aucun appareil/émulateur n'est disponible.

Le ticket ne passe pas à `VALIDATED` tant que les scénarios fonctionnels
critiques n'ont pas été exécutés sur appareils.

---

# 9. Release

Version:
v1.48.29

Commit:
♻️ Refactor player architecture and factorize core components (T3)

Date:
2026-07-21
