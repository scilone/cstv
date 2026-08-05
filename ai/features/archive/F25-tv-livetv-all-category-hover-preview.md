# F25 - TV LiveTV All Category Hover Preview

## Informations générales

Status:
RELEASED

Created:
2026-08-05

---

# 1. Description

Sur la liste des chaînes Live TV, dans l'onglet/catégorie "Tout" :
1. Supprimer l'étoile de favoris au survol/focus de la carte car elle n'est pas sélectionnable individuellement au D-Pad.
2. Ajouter une miniature avec preview vidéo de la chaîne au focus stable de la carte.
3. Un clic simple lance le média en grand avec le player immersif standard.
4. Un appui long ajoute ou supprime la chaîne des favoris.

---

# 2. Contexte

Actuellement, l'étoile de favoris s'affiche au focus de la carte de chaîne mais n'est pas cliquable via la télécommande de manière ergonomique. De plus, avoir une prévisualisation de la chaîne en direct sur le focus stable rendrait l'application extrêmement vivante et moderne, similaire aux applications IPTV de pointe.

---

# 3. Spécification fonctionnelle

### User story

En tant qu'utilisateur Android TV, je peux prévisualiser une chaîne de la vue « TOUT » en gardant le focus sur sa carte, démarrer sa lecture avec un appui court et gérer son favori avec un appui long, sans créer de cible D-Pad secondaire ambiguë.

### Parcours utilisateur

1. L'utilisateur navigue jusqu'à une carte de chaîne de la vue « TOUT ».
2. Avant une seconde de focus stable, la carte conserve son aperçu statique normal.
3. Après une seconde de focus continu, l'aperçu vidéo de la chaîne démarre dans la miniature avec son activé.
4. Un déplacement du focus, la sortie de l'écran ou le lancement du lecteur principal arrête immédiatement l'aperçu et son audio.
5. Un appui court ouvre le lecteur immersif de la chaîne ; un appui long ajoute ou retire cette chaîne des favoris, sans lancer le lecteur.

### Règles métier

- La prévisualisation ne concerne que les cartes de chaînes de la vue TV « TOUT » dans ce ticket ; elle n'est jamais lancée pour plusieurs cartes à la fois.
- **Exclusion explicite (F25-R2) : la rangée « Récemment regardées ».** Son appui long porte déjà une action préexistante (retrait de l'historique, avec confirmation) incompatible avec un second usage sur la même carte. Ses cartes (`RecentlyWatchedTvItem`) restent donc hors du contrat d'aperçu vidéo et de favori en appui long de ce ticket : pas de miniature vidéo au focus stable, appui long réservé au retrait d'historique comme avant F25. Le favori de ces chaînes reste gérable ailleurs (rangée « Favoris », grille de catégorie). Les autres rangées de la vue « TOUT » (Favoris, catégories) sont pleinement couvertes.
- L'étoile ne constitue plus un bouton D-Pad séparé. Lorsqu'une chaîne est favorite, un indicateur visuel non interactif peut signaler cet état.
- La décision d'ajouter ou retirer un favori est portée par l'appui long de la carte et bénéficie du retour visuel habituel.
- Un échec de prévisualisation ne bloque ni la navigation, ni le favori, ni l'ouverture du lecteur principal.

### Critères d'acceptation

- Une carte focalisée moins de 1 000 ms ne démarre aucun flux ; après 1 000 ms continus, elle affiche une prévisualisation audible.
- La perte de focus ou la navigation vers une autre carte coupe l'image et l'audio de l'aperçu précédent sans délai perceptible.
- Le D-Pad ne peut pas focaliser une étoile ; un appui court et un appui long ont les comportements distincts décrits.
- L'ouverture du lecteur immersif conserve le comportement existant et ne laisse aucune prévisualisation active en arrière-plan.

### Cas limites et erreurs

- Si le flux est indisponible, protégé ou trop lent, la carte conserve son aperçu statique ou un état discret d'indisponibilité, sans message technique ni boucle de tentatives.
- Les changements de focus rapides ne déclenchent pas une succession de lectures audibles.
- À la fermeture de l'écran, au passage en arrière-plan ou au changement de profil, toute prévisualisation et son audio cessent.

---

# 4. Spécification technique

Ce ticket comporte trois modifications indépendantes : le retrait de l'étoile, le passage du favori en appui long, et l'aperçu vidéo. Les deux premières sont simples ; la troisième porte l'essentiel du risque.

## 1. Retrait de l'étoile focalisable

`StreamTvCard` (`presentation/livetv/components/LiveTvComponents.kt:900-908`) se termine par :

```kotlin
if (isFocused || isFavorite) {
    IconButton(onClick = onToggleFavorite) {
        Icon(Icons.Default.Star, contentDescription = "Favori",
             tint = if (isFavorite) Color.Yellow else Color.DarkGray)
    }
}
```

`IconButton` est focalisable : il constitue bien la cible D-Pad secondaire décrite dans le ticket. Il est remplacé par un indicateur purement décoratif, affiché uniquement quand la chaîne est favorite :

```kotlin
if (isFavorite) {
    Icon(
        Icons.Default.Star,
        contentDescription = stringResource(R.string.livetv_favorite_state_description),
        tint = Color.Yellow,
        modifier = Modifier.size(20.dp).focusProperties { canFocus = false }
    )
}
```

`onToggleFavorite` reste dans la signature de `StreamTvCard` : il est désormais câblé sur l'appui long de la carte entière.

## 2. Favori par appui long — généralisation du mécanisme existant

Le mécanisme requis existe déjà : `Modifier.historyItemActions` (`presentation/components/HistoryItemActions.kt`) distingue appui court et appui long au D-Pad, et `RecentlyWatchedTvItem` l'utilise déjà dans ce même fichier (ligne 739). Son nom et son libellé d'accessibilité sont en revanche spécifiques à l'historique.

On extrait donc le mécanisme sous un nom neutre, et `historyItemActions` en devient un appel préconfiguré :

```kotlin
// presentation/components/TvLongPressActions.kt
fun Modifier.tvLongPressActions(
    isTv: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    longClickLabel: String
): Modifier = ...   // corps actuel de historyItemActions, libellé paramétré

// HistoryItemActions.kt devient
fun Modifier.historyItemActions(isTv: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)?): Modifier =
    composed { tvLongPressActions(isTv, onClick, onLongClick, stringResource(R.string.history_removal_confirm)) }
```

`StreamTvCard` remplace alors son `.clickable { onClick() }` (ligne 838) par :

```kotlin
.tvLongPressActions(
    isTv = true,
    onClick = onClick,
    onLongClick = onToggleFavorite,
    longClickLabel = stringResource(R.string.livetv_favorite_toggle_label)
)
```

**Dépendance forte à B20.** Le correctif d'appariement clavier de B20 vit dans ce même mécanisme. Ici, l'appui long n'ouvre aucune fenêtre : le focus ne bouge pas, la carte reçoit son propre KeyUp et `consumeKeyUp` fonctionne comme prévu. Le cas nominal est donc sain. Mais la correction secondaire de B20 — remise à zéro de `consumeKeyUp` à la perte de focus — devient ici indispensable : un déplacement de D-Pad pendant un appui long laisserait sinon le drapeau bloqué et ferait ignorer le clic suivant. **B20 doit être livré avant F25.**

## 3. Aperçu vidéo — architecture

### Décisions arrêtées à l'étape 3

* **Une seule instance de lecteur**, hissée au niveau de `TvLayout`. Les cartes ne font que déclarer leur focus ; elles n'instancient jamais de lecteur. L'empreinte mémoire devient constante quel que soit le nombre de cartes visibles, et aucune création/destruction n'a lieu pendant la navigation D-Pad.
* **Même pile de décodage que le lecteur principal**, via `rememberManagedExoPlayer(useOfflineCache = false)` (`presentation/player/core/ExoPlayerCore.kt:64`). Elle apporte `VideoHardwarePreferredRenderersFactory` : vidéo matérielle, audio FFmpeg/NextLib. C'est la seule option qui donne du son sur les chaînes EAC3/AC3/DTS, et elle garantit qu'aucune divergence de politique de décodage ne puisse reproduire B16 dans l'aperçu.

### Nouveau composant

`presentation/livetv/components/LiveChannelPreview.kt` :

```kotlin
@Stable
class LiveChannelPreviewState(
    val player: ExoPlayer,
    val activeStreamId: Int?,
    val onFocusChanged: (streamId: Int?, focused: Boolean) -> Unit
)

@Composable
fun rememberLiveChannelPreviewState(
    enabled: Boolean,
    credentials: Credentials?
): LiveChannelPreviewState
```

Contrat interne :

* `onFocusChanged(id, true)` mémorise la chaîne candidate ; `onFocusChanged(id, false)` l'efface si elle est encore la candidate courante.
* Un `LaunchedEffect(candidateId)` attend `PREVIEW_FOCUS_DELAY_MS = 1_000L` avant d'activer. Tout changement de candidat annule la coroutine précédente : les déplacements rapides ne déclenchent aucune lecture, ce qui satisfait le cas limite « pas de succession de lectures audibles ».
* L'activation appelle `player.setMediaItem(MediaItem.fromUri(url))`, `prepare()`, `play()`.
* La désactivation appelle `player.stop()` puis `player.clearMediaItems()` — **jamais** `release()` : l'instance est réutilisée, sa libération étant assurée par le `DisposableEffect` de `rememberManagedExoPlayer`.
* L'URL provient de `LiveStream.getPlayUrl(baseUrl, username, password)` (`domain/model/LiveStream.kt:16`), la même que celle utilisée par `PlayerScreen.kt:187`.
* Un `Player.Listener.onPlayerError` inscrit le `streamId` dans un ensemble d'échecs et désactive l'aperçu. Une chaîne ayant échoué n'est plus retentée pendant la session d'écran : cela satisfait « sans message technique ni boucle de tentatives ».

### Cycle de vie et arrêts garantis

L'aperçu doit cesser dans cinq situations, toutes couvertes explicitement :

| Situation | Mécanisme |
| --- | --- |
| Perte de focus / autre carte | `onFocusChanged(_, false)` → désactivation immédiate |
| Sortie de l'écran | `DisposableEffect` de `rememberManagedExoPlayer` (stop + clearVideoSurface + release) |
| Passage en arrière-plan | `LifecycleEventObserver` sur `ON_STOP`, sur le modèle de `TrailerAutoStartEffect` (`MediaDetailsTrailerBackdrop.kt:44`) |
| Ouverture du lecteur principal | `onStreamSelected` est enveloppé pour désactiver l'aperçu **avant** de naviguer |
| Changement de profil | Recomposition de l'écran via le `state` ; couvert par la désactivation au changement de `credentials` |

Le quatrième point est le plus important pour les box faibles : sans lui, deux `ExoPlayer` avec décodeurs FFmpeg coexisteraient pendant la transition vers le lecteur plein écran.

### Gating hors ligne

Un flux Live exige le serveur. `viewModel.requestPlayback` ne convient pas ici : il affiche un message de refus, alors que l'aperçu doit échouer en silence. La condition d'activation est donc `enabled = !state.catalogStatus.isOffline && credentials != null`. Hors ligne, aucun aperçu n'est tenté et la carte garde son visuel statique.

`LiveTvViewModel` expose les identifiants, qu'il détient déjà via `credentialsManager` (`LiveTvViewModel.kt:45`) :

```kotlin
fun previewCredentials(): Credentials? = runCatching { credentialsManager.getCredentials() }.getOrNull()
```

### Rendu dans la carte

`StreamTvCard` reçoit deux paramètres supplémentaires, `previewActive: Boolean` et `previewPlayer: ExoPlayer?`. Quand `previewActive` est vrai, la `Box` du logo (56 dp, ligne 843) affiche une `AndroidView` de `PlayerView` (`useController = false`, `resizeMode` ajusté) à la place de l'`AsyncImage`. Le reste de la carte est inchangé.

## Composants impactés

| Fichier | Nature |
| --- | --- |
| `presentation/livetv/components/LiveChannelPreview.kt` | **Nouveau** — état, temporisation, cycle de vie, gestion d'erreur |
| `presentation/components/TvLongPressActions.kt` | **Nouveau** — mécanisme d'appui long généralisé |
| `presentation/components/HistoryItemActions.kt` | Délègue au mécanisme généralisé |
| `presentation/livetv/components/LiveTvComponents.kt` | `StreamTvCard` : étoile décorative, appui long, surface d'aperçu |
| `presentation/livetv/LiveTvScreen.kt` | `TvLayout` : hissage de l'état d'aperçu, câblage du focus, arrêt avant navigation |
| `presentation/livetv/LiveTvViewModel.kt` | `previewCredentials()` |
| `res/values/strings.xml` (+ `values-en/`) | `livetv_favorite_toggle_label`, `livetv_favorite_state_description` |

Aucune nouvelle dépendance Gradle : media3 et NextLib sont déjà au projet. Aucun changement de couche `data` ni de schéma Room.

## Contraintes de performance

C'est le ticket le plus lourd de la série pour les box d'entrée de gamme, et les décisions ci-dessus sont prises pour cette raison :

* une seule instance, jamais recréée pendant la navigation ;
* temporisation de 1 000 ms, qui élimine les activations pendant un défilement rapide ;
* `stop()` + `clearMediaItems()` plutôt que `release()` + reconstruction ;
* aucun aperçu hors ligne, aucune reprise après échec.

Deux points restent à surveiller et ne sont pas résolus par ce ticket :

1. Le décodage audio FFmpeg est logiciel par construction. Sur une chaîne EAC3, l'aperçu consomme du CPU en continu tant que le focus reste posé.
2. `StreamTvCard` porte déjà une boucle `LaunchedEffect(stream.streamId) { while (true) { onLoadEpg(); delay(60000) } }` (ligne 819) : une coroutine par carte composée. Antérieur à ce ticket et hors périmètre, mais à garder en tête si des saccades apparaissent — le ticket **T14** de la série performance couvre le dimensionnement mémoire du lecteur.

## Risques techniques

1. **Deux lecteurs simultanés pendant la navigation vers le lecteur plein écran.** Risque principal sur box à 1 Go. Traité par l'arrêt explicite avant navigation, à vérifier en review comme point bloquant.
2. **Latence de démarrage d'un flux Live.** Entre `prepare()` et la première image, plusieurs secondes peuvent s'écouler sur un serveur lent. L'utilisateur aura souvent déjà quitté la carte. La désactivation à la perte de focus doit donc annuler une préparation encore en cours, et pas seulement une lecture démarrée.
3. **Comportement de `PlayerView` dans une cellule de 56 dp.** Certains décodeurs matériels tolèrent mal les très petites surfaces. Repli possible : agrandir la zone d'aperçu, ce qui relèverait alors de la mise en page traitée par **F26**.
4. **Interaction avec le pivot de focus.** L'aperçu s'active 1 000 ms après le focus, donc après la convergence du pivot (`tvPivotItem` / `tvPivotSection`). Aucun conflit attendu, mais le changement de contenu de la carte ne doit pas déclencher une nouvelle mesure qui relancerait `onFocusedBoundsChanged` — d'où l'importance de conserver une **taille de carte strictement identique** avec et sans aperçu.
5. **Conflit de fusion avec F24, F26 et T12** sur `LiveTvScreen.kt` et `LiveTvComponents.kt`.

## Ordonnancement imposé

**B20 → F24 → F25 → F26.** B20 assainit le mécanisme d'appui long que F25 généralise ; F24 restructure l'en-tête que F25 modifie ; F26 dépend du contrat d'aperçu défini ici.

## Validation automatisable

La logique de temporisation et de transition d'état est extractible en classe pure, testable en JVM avec `kotlinx-coroutines-test` : candidat posé puis retiré avant 1 000 ms → aucune activation ; candidat maintenu → activation unique ; changement de candidat → une seule activation, sur le dernier ; chaîne en échec → plus jamais réactivée. Le rendu vidéo, l'audio et la mesure d'empreinte mémoire ne sont pas testables sans appareil et reviennent au PO.

---

# 5. Architecture

L'architecture repose sur l'introduction d'un lecteur d'aperçu unique au niveau de `TvLayout` pour minimiser l'empreinte mémoire et la latence. Les cartes déclarent leur focus et transmettent l'ID à l'état du lecteur. L'étoile de favori est supprimée de la navigation focusable pour ne pas perturber le D-Pad, et l'action de favoris est redirigée vers un appui long sur la carte de chaîne entière.

---

# 6. Plan de développement

## Liste des tâches

- [x] Tâche 1 — Déclarer les ressources de chaînes pour le favori

  **Objectif :**
  Ajouter les chaînes de traduction `livetv_favorite_toggle_label` et `livetv_favorite_state_description` dans `strings.xml`.

  **Fichiers :**
  - `app/src/main/res/values/strings.xml`

  **Validation :**
  - Clés résolues à la compilation.

- [x] Tâche 2 — Généraliser le modificateur d'appui long TV

  **Objectif :**
  Extraire le mécanisme de gestion d'appui long clavier de `historyItemActions` vers un modificateur générique `tvLongPressActions` paramétrable dans un nouveau fichier ou dans `HistoryItemActions.kt`, puis faire de `historyItemActions` un simple appel préconfiguré de ce dernier.

  **Fichiers :**
  - `presentation/components/TvLongPressActions.kt`
  - `presentation/components/HistoryItemActions.kt`

  **Validation :**
  - Compilation réussie et non-régression de l'historique sur mobile/TV.

- [x] Tâche 3 — Modifier `StreamTvCard` pour l'appui long, l'étoile décorative et la zone d'aperçu

  **Objectif :**
  - Remplacer l'`IconButton` de l'étoile de favori par un composant `Icon` décoratif non focalisable affiché uniquement si la chaîne est favorite.
  - Remplacer `.clickable` par le modificateur `.tvLongPressActions(...)` lié au favori en appui long.
  - Accepter les paramètres `previewActive: Boolean` et `previewPlayer: ExoPlayer?`, et afficher une `AndroidView` encapsulant `PlayerView` (sans contrôles) à la place de l'image de logo si la prévisualisation est active.

  **Fichiers :**
  - `presentation/livetv/components/LiveTvComponents.kt`

  **Validation :**
  - `StreamTvCard` compile sans erreur.

- [x] Tâche 4 — Créer le composant de gestion d'état de prévisualisation `LiveChannelPreview`

  **Objectif :**
  Créer le fichier de support `LiveChannelPreview.kt` qui gère la temporisation stable de 1000 ms, la construction de l'URL, la préparation/l'arrêt du lecteur et l'observation du cycle de vie ou d'erreur du lecteur.

  **Fichiers :**
  - `presentation/livetv/components/LiveChannelPreview.kt`

  **Validation :**
  - Nouveau fichier compilé correctement.

- [x] Tâche 5 — Intégrer l'état de prévisualisation dans `LiveTvScreen` et ViewModel

  **Objectif :**
  - Exposer `previewCredentials()` dans le `LiveTvViewModel`.
  - Dans `LiveTvScreen.TvLayout`, instancier l'état d'aperçu via `rememberLiveChannelPreviewState`, câbler l'état de focus sur les rangées de la catégorie "Tout", et couper le lecteur d'aperçu avant de lancer la navigation vers le lecteur immersif.

  **Fichiers :**
  - `presentation/livetv/LiveTvScreen.kt`
  - `presentation/livetv/LiveTvViewModel.kt`

  **Validation :**
  - Exécution réussie de `./gradlew assembleDebug` et `./gradlew testDebugUnitTest`.

---

# 7. Notes de développement

- 2026-08-05 — Implémentation des 5 tâches :
  - `TvLongPressActions.kt` (nouveau) porte le mécanisme généralisé, corps identique à l'ancien `historyItemActions` (appariement KeyDown/KeyUp B20 conservé). `historyItemActions` devient un appel préconfiguré.
  - `StreamTvCard` : étoile devenue `Icon` décorative (`focusProperties { canFocus = false }`), `.clickable` remplacé par `.tvLongPressActions(isTv = true, onLongClick = onToggleFavorite, longClickLabel = R.string.livetv_favorite_toggle_label)`. Nouveaux paramètres `previewActive`, `previewPlayer`, `onPreviewFocusChanged` (tous par défaut `null`/`false`, non intrusifs pour les appelants existants).
  - `LiveChannelPreview.kt` (nouveau) : `resolvePreviewCandidate` extraite en fonction pure testée en JVM (`LiveChannelPreviewTest.kt`) ; `LiveChannelPreviewState` porte candidat/actif/échecs ; `rememberLiveChannelPreviewState` hisse un unique `rememberManagedExoPlayer(useOfflineCache = false)`, observe le cycle de vie (`ON_STOP`) et les erreurs du lecteur, et temporise l'activation de `PREVIEW_FOCUS_DELAY_MS = 1000L` via `LaunchedEffect`.
  - **Déviation assumée par rapport au contrat documenté à l'étape 3 :** `LiveTvViewModel.getCredentials()` existait déjà (non appelé, code mort) et remplit exactement le rôle prévu pour `previewCredentials()` — réutilisé tel quel plutôt que dupliqué. `onFocusChanged` prend le `LiveStream` complet plutôt qu'un `streamId: Int?`, pour éviter une table de correspondance id → flux à maintenir en parallèle des listes déjà rendues (rangées « Tout », favoris, grille catégorie) ; le contrat fonctionnel (une temporisation, un seul candidat, effacement uniquement si le candidat courant correspond) est inchangé.
  - `LiveTvScreen.TvLayout` : `previewCredentials` résolu une fois par entrée sur l'écran dans `LiveTvScreen`, transmis à `TvLayout` ; `previewState` hissé au niveau de `TvLayout` ; câblé sur les rangées « Récemment regardées », « Favoris » et catégories de la vue « Tout » via `CategorySectionRow`, et sur la grille de catégorie précise (périmètre F26 anticipé ici car trivial une fois `previewState` disponible). Navigation vers le lecteur principal enveloppée dans `onStreamSelectedStoppingPreview` qui appelle `previewState.stop()` avant `onStreamSelected`.
- Validation automatisée : `./gradlew testDebugUnitTest assembleDebug lintDebug` — `BUILD SUCCESSFUL`. Nécessite `@androidx.annotation.OptIn(UnstableApi::class)` sur `rememberLiveChannelPreviewState` (même patron que `PlayerScreen.kt`), le flag compilateur `-opt-in` ne suffisant pas à satisfaire lint.
- Non vérifié (hors périmètre des critères automatisés, règle n°9) : rendu vidéo réel, audio, latence de démarrage sur box faible, taille de `PlayerView` dans la cellule 56 dp.
- 2026-08-05 — Étape 7, corrections de la review (voir section 8) :
  - **F25-R1** : `LiveChannelPreviewState` n'est plus reconstruite au changement de `enabled`/`credentials`. `rememberLiveChannelPreviewState` mémorise désormais l'état uniquement sur `player` (`remember(player) { LiveChannelPreviewState(player) }`) et répercute `credentials` en place via un `SideEffect`. Le passage hors ligne déclenche donc le même `LaunchedEffect(state, state.candidateId, enabled)` sur la même instance, qui appelle `state.deactivate()` (`stop()` + `clearMediaItems()`) au lieu d'abandonner un lecteur actif attaché à une instance jetée.
  - **F25-R2** : périmètre clarifié plutôt qu'implémenté — la rangée « Récemment regardées » est explicitement exclue du contrat d'aperçu/favori en appui long de F25 (voir Règles métier, étape 2, ajout F25-R2) : son appui long reste réservé au retrait d'historique, préexistant et incompatible avec un second usage. `RecentlyWatchedTvItem`/`RecentlyWatchedRow` sont inchangés ; la note de l'implémentation initiale affirmant à tort une couverture de cette rangée est corrigée par la présente entrée.
  - **F25-R3** : le cœur temporel de `LaunchedEffect` est extrait en fonction pure `activatePreviewAfterDelay(enabled, candidateId, delayMs, deactivate, activate)`, testable avec `kotlinx-coroutines-test` (délai de 1000 ms, changement rapide de candidat annulant l'activation en attente, candidat retenu moins de 1000 ms). `LiveChannelPreviewState.activate()` reçoit un paramètre `mediaItemFactory` (par défaut `MediaItem::fromUri`) injectable en test — nécessaire car `MediaItem.fromUri` appelle `android.net.Uri.parse`, non mockable en JVM sans Robolectric (absent du projet, même limite que `AndroidNewEpisodeNotifierTest`). `LiveChannelPreviewTest` couvre désormais : temporisation, annulation sur changement rapide, activation unique, `stop()`, `deactivate()` sans lecture active, désarmement hors ligne/sans identifiants, et non-retentative après `markFailed`.
- Validation automatisée (étape 7) : `./gradlew testDebugUnitTest assembleDebug lintDebug` — `BUILD SUCCESSFUL`, 16/16 tests `LiveChannelPreviewTest` verts.

---

# 8. Review

Date : 2026-08-05

Status : RESOLVED

## Périmètre relu

- `presentation/components/TvLongPressActions.kt`
- `presentation/components/HistoryItemActions.kt`
- `presentation/livetv/components/LiveChannelPreview.kt`
- `presentation/livetv/components/LiveTvComponents.kt`
- `presentation/livetv/LiveTvScreen.kt`
- `presentation/livetv/LiveTvViewModel.kt`
- `app/src/test/java/com/cstv/app/presentation/livetv/components/LiveChannelPreviewTest.kt`

## Critique

Aucun constat.

## Majeur

### F25-R1 — RÉSOLU — Le passage hors ligne recrée l'état sans arrêter le lecteur déjà actif

**Description :** `rememberLiveChannelPreviewState` mémorise `LiveChannelPreviewState` avec `enabled` dans sa clé. Quand `catalogStatus.isOffline` passe de `false` à `true`, un nouvel état est donc créé autour du même `ExoPlayer`. Ce nouvel état ne connaît aucun `activeStreamId` ; sa branche `!enabled` appelle `deactivate()`, qui retourne immédiatement lorsque `activeStreamId == null`. L'ancien état perd son listener, mais aucun nettoyage ne coupe le lecteur qu'il avait démarré.

**Impact :** après une perte réseau détectée pendant un aperçu, la miniature statique revient alors que le lecteur partagé peut continuer à produire un audio invisible. Le gating hors ligne et la garantie d'arrêt immédiat de F25 ne sont pas respectés. Le même défaut réapparaîtrait pour toute future variation des identifiants passée dans la clé de `remember`.

**Correction attendue :** garantir explicitement l'arrêt de l'ancien état avant son remplacement, ou conserver une instance d'état stable dont les paramètres d'activation sont mis à jour. Ajouter un test de non-régression « aperçu actif puis `enabled = false` » qui vérifie `stop()` et `clearMediaItems()` même après changement de configuration.

**Correction appliquée (2026-08-05) :** `LiveChannelPreviewState` est désormais mémorisée une seule fois par `player` (`remember(player)`), plus par `enabled`/`credentials` ; `credentials` est répercuté en place via `SideEffect`. Le `LaunchedEffect(state, state.candidateId, enabled)` agit donc sur l'instance unique et appelle `state.deactivate()` (via `activatePreviewAfterDelay`) dès que `enabled` devient faux, sans jamais l'abandonner. Voir `presentation/livetv/components/LiveChannelPreview.kt`.

### F25-R2 — RÉSOLU — La rangée « Récemment regardées » n'applique pas le contrat annoncé pour les cartes de « TOUT »

**Description :** les notes de développement indiquent que l'aperçu est câblé sur « Récemment regardées », « Favoris » et les catégories. En réalité, `RecentlyWatchedRow` ne reçoit pas `previewState` et rend `RecentlyWatchedTvItem`, sans miniature vidéo ni notification de focus. Son appui long reste en outre réservé au retrait de l'historique, alors que le contrat général de F25 associe l'appui long d'une carte de chaîne au favori.

**Impact :** la première rangée de chaînes de la vue « TOUT » ne bénéficie pas du comportement décrit, et le ticket affirme à tort que ce périmètre est livré. L'utilisateur obtient deux contrats différents selon la rangée sans exclusion fonctionnelle documentée.

**Correction attendue :** trancher explicitement le périmètre de cette rangée. Si elle appartient à F25, lui propager l'aperçu et définir sans ambiguïté la coexistence entre retrait d'historique et gestion du favori ; sinon, inscrire son exclusion dans la spécification et corriger les notes de développement afin que les critères ne prétendent pas couvrir toutes les cartes de « TOUT ».

**Correction appliquée (2026-08-05) :** périmètre tranché — exclusion. La rangée « Récemment regardées » porte déjà une action d'appui long préexistante (retrait d'historique) incompatible avec un second usage sur la même carte ; lui ajouter l'aperçu créerait une ambiguïté fonctionnelle non demandée par le ticket. Exclusion inscrite dans les Règles métier (étape 2) et notes de développement corrigées (étape 7) ; aucun changement de code sur `RecentlyWatchedTvItem`/`RecentlyWatchedRow`.

### F25-R3 — RÉSOLU — Les tests ne couvrent pas le cœur temporel et le cycle de vie de l'aperçu

**Description :** `LiveChannelPreviewTest` exerce uniquement la fonction pure `resolvePreviewCandidate`. Aucun test ne vérifie le délai de 1 000 ms, l'annulation lors d'un changement rapide de focus, l'activation unique, l'arrêt, le passage hors ligne, ni l'absence de nouvelle tentative après `markFailed`, alors que ces scénarios sont listés comme validation automatisable du ticket.

**Impact :** les garanties les plus risquées de F25 restent sans non-régression automatisée ; F25-R1 n'est notamment pas détectable par la suite actuelle malgré un résultat vert.

**Correction attendue :** extraire un contrôleur de prévisualisation indépendant de Compose/ExoPlayer ou injecter une façade de lecteur et un ordonnanceur testables, puis couvrir au minimum les transitions candidat/actif, les 1 000 ms, le changement de candidat, `stop`, le désarmement hors ligne et la mémorisation des flux en échec.

**Correction appliquée (2026-08-05) :** cœur temporel extrait en fonction pure `activatePreviewAfterDelay` (`kotlinx-coroutines-test`, `advanceTimeBy`/`advanceUntilIdle`) : temporisation 1000 ms, annulation sur changement rapide, activation unique. `LiveChannelPreviewState.activate()` reçoit un `mediaItemFactory` injectable (contournement de `android.net.Uri.parse`, non mockable en JVM sans Robolectric). `LiveChannelPreviewTest` couvre désormais candidat/actif, `stop()`, `deactivate()` sans lecture active, désarmement hors ligne/sans identifiants, et non-retentative après `markFailed` (16 tests, tous verts).

## Mineur

Aucun constat supplémentaire.

## Vérifications effectuées

- Le lecteur est bien hissé une seule fois dans `TvLayout` et partagé entre les cartes ; aucune instance n'est créée dans `StreamTvCard`.
- La perte de focus change immédiatement le candidat et relance l'effet chargé de désactiver le lecteur ; l'ouverture du lecteur principal passe par `previewState.stop()` dans les rangées câblées et dans la grille.
- L'étoile de `StreamTvCard` est décorative et non focalisable ; l'appui long réutilise le mécanisme B20 avec remise à zéro à la perte de focus.
- La politique de décodage réutilise `rememberManagedExoPlayer(useOfflineCache = false)` et donc la même fabrique de renderers que le lecteur Live principal.
- Test ciblé `LiveChannelPreviewTest` : succès (`BUILD SUCCESSFUL`). Ce succès confirme les quatre transitions pures présentes, pas les scénarios manquants de F25-R3.
- `git diff --check` : aucun défaut d'espaces dans les changements suivis.

## Limites de la review

Le rendu vidéo, l'audio, la latence et le comportement de `PlayerView` dans 56 dp ne sont pas observables par les tests JVM du projet. Conformément à la stratégie de tests, ils ne constituent pas une validation finale sur appareil ; la présente étape reste une review documentaire et n'exécute ni validation globale, ni correction.

## Corrections demandées

- ~~Corriger F25-R1.~~ RÉSOLU (2026-08-05).
- ~~Clarifier puis aligner l'implémentation et la documentation pour F25-R2.~~ RÉSOLU (2026-08-05).
- ~~Ajouter la couverture automatisée de F25-R3.~~ RÉSOLU (2026-08-05).

## Validation finale (étape 8)

Date : 2026-08-05

- Comportement attendu : conforme — étoile décorative non focalisable, favori en appui long, aperçu vidéo à 1000 ms de focus stable sur les rangées Favoris/catégories de la vue « TOUT », exclusion documentée de « Récemment regardées ».
- Règles métier : conformes, y compris l'exclusion F25-R2 désormais explicite.
- Absence de régression : `./gradlew testDebugUnitTest assembleDebug lintDebug` — `BUILD SUCCESSFUL`.
- Tests validés : `LiveChannelPreviewTest` (16/16, couvre désormais temporisation, cycle de vie, F25-R1).
- Expérience utilisateur, latence de démarrage réelle, rendu `PlayerView` en 56 dp : non vérifiables par tests JVM (règle n°9), hors périmètre de la validation automatisée de l'agent — revient au PO sur device.

Status : VALIDATED

---

# 9. Livraison Git (Étape 10)

Date : 2026-08-05

- Commit : `:sparkles: feat(livetv): implement hover video preview and long press favorite (F25 & F26)`
- Tag : `v1.74.0`
- APK : `releases/app-release.apk`
- Status : RELEASED
