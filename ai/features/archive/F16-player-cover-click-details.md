# F16 - Navigation vers la fiche détails depuis le clic sur la cover du player (Films/Séries)

## Informations générales

Status:
RELEASED

Created:
2026-07-26

Version:
v1.59.0

Date:
2026-07-26

---

# 1. Description

L'utilisateur doit pouvoir cliquer sur la cover (l'affiche) du média affichée dans le panneau de contrôle inférieur du lecteur vidéo (pour les films et les séries) afin d'arrêter la lecture et de se rendre directement sur la fiche détaillée (fiche média) du contenu correspondant.

---

# 2. Contexte

Actuellement, lorsqu'on lit un film ou une série dans le player (`VodPlayerScreen` ou `SeriesPlayerScreen`), l'affiche (cover) du média est affichée de manière purement statique dans le bloc inférieur gauche de l'overlay de contrôle (à côté du titre, du genre et de la barre de progression).

L'accès à la fiche détaillée s'effectue généralement en amont, mais dans plusieurs parcours (ex: reprise de lecture depuis l'écran d'Accueil via la ligne "Continuer la lecture", ou lecture directe depuis l'écran "Téléchargements"), l'utilisateur est envoyé directement dans le lecteur vidéo sans passer par la fiche détaillée. Dans ce cas, la fiche détaillée n'est pas présente dans l'historique de navigation (backstack).

Rendre la cover cliquable offre un raccourci intuitif et direct pour :
- Consulter le synopsis complet ou la bande-annonce.
- Gérer les favoris (ajouter/retirer).
- Noter le contenu (J'aime / Je n'aime pas).
- Accéder aux autres épisodes d'une série (dans le cas des séries).
- Lancer un téléchargement local.

---

# 3. Objectif

Permettre la navigation directe vers la fiche détaillée du média depuis le lecteur en rendant la cover cliquable.

Cette action doit :
1. Sauvegarder la position de lecture courante.
2. Arrêter proprement le player vidéo et libérer ses ressources.
3. Rediriger l'utilisateur vers `VodDetailsScreen` (pour un film) ou `SeriesDetailsScreen` (pour une série).
4. Gérer intelligemment la pile de navigation (backstack) pour éviter d'empiler inutilement les écrans (si la fiche détails était déjà l'écran précédent, on effectue un simple retour arrière ; sinon, on ferme le lecteur et on ouvre la fiche détails).

---

# 4. Hypothèses

- **Intégration UI interactive :** L'image de la cover (`AsyncImage` dans `VodPlayerScreen` et `SeriesPlayerScreen`) sera rendue interactive à l'aide de modificateurs standards (`.clickable`). Sur Android TV, elle devra également être focusable par le D-Pad, avec des indicateurs visuels de focus clairs et cohérents avec la charte graphique de l'application (par exemple, une bordure colorée, une légère mise à l'échelle ou un effet d'opacité).
- **Callback de navigation unifié :** Nous pouvons étendre les signatures de `VodPlayerScreen` et `SeriesPlayerScreen` avec une fonction de retour d'événement comme `onNavigateToDetails` ou adapter le flux existant. `NavGraph.kt` interceptera cette action pour orchestrer la navigation.
- **Gestion intelligente du backstack :**
  - Si la fiche de détails est déjà présente immédiatement derrière le lecteur dans le backstack, un retour arrière (`popBackStack`) suffit.
  - Si le lecteur a été ouvert depuis un autre écran (Accueil, Téléchargements, etc.), nous devons fermer le player (`popBackStack`) puis naviguer vers la fiche de détails correspondante.
- **Persistance de l'état :** Le clic sur la cover doit déclencher le même cycle de fermeture propre que le bouton "Fermer" (croix) ou le bouton "Retour", garantissant que la progression de lecture est sauvegardée en base de données avant la redirection.

---

# 5. Questions ouvertes

1. **Focus Android TV & Navigation D-Pad :** Comment intégrer harmonieusement la cover dans le diagramme de focus du lecteur TV ? Le focus sur la cover doit être accessible facilement depuis les contrôles de transport (Play/Pause, Avance rapide) ou les boutons d'actions inférieurs, tout en évitant d'interférer avec la barre de progression.
2. **Identifiants de navigation :** Pour ouvrir la fiche détails d'un film, nous avons besoin de son ID de flux (`streamId`) et d'un objet `VodStream`. Pour les séries, nous avons besoin du `SeriesStream` ou du `seriesId`. Est-ce que toutes ces données sont directement et systématiquement disponibles dans le lecteur, y compris lors d'une lecture hors-ligne ou d'une reprise ?
3. **Cas de la lecture hors-ligne :** Si l'utilisateur clique sur la cover d'un contenu lu en mode hors-ligne (téléchargé), la fiche de détails doit s'ouvrir correctement en mode offline sans tenter d'effectuer d'appels réseau inutiles ou bloquants.

---

# 6. Spécification fonctionnelle

## Décisions tranchant les questions ouvertes de l'étape 1

- **Q1 — Focus Android TV :** la cover fait partie des contrôles interactifs du lecteur. Elle est atteignable au D-Pad, activable avec le bouton de validation et son focus est identifiable sans ambiguïté par un indicateur visuel cohérent avec les autres contrôles du player. L'ordre exact de focus relève de l'étape 3, mais la cover ne doit ni devenir inaccessible ni perturber l'usage de la barre de progression.
- **Q2 — Données de navigation :** l'action doit ouvrir la fiche du média effectivement lu : fiche film pour un film, fiche de la série propriétaire pour un épisode. Les données nécessaires doivent être conservées ou résolues depuis les données locales déjà disponibles ; l'utilisateur ne doit jamais être redirigé vers une fiche différente ou vide.
- **Q3 — Hors-ligne :** la navigation vers la fiche doit rester possible sans réseau pour un contenu téléchargé ou déjà disponible dans le catalogue local. Elle utilise les données locales disponibles ; elle ne déclenche pas de requête réseau bloquante. Les compléments habituellement dépendants du réseau peuvent rester indisponibles selon leur comportement existant, sans empêcher l'ouverture de la fiche.

## User stories

- En tant que spectateur d'un film, je veux sélectionner sa cover depuis le lecteur afin de retrouver sa fiche sans devoir quitter puis rechercher le film.
- En tant que spectateur d'un épisode, je veux sélectionner la cover depuis le lecteur afin d'accéder à la fiche de sa série et à ses autres épisodes.
- En tant qu'utilisateur arrivé directement au player depuis l'Accueil, les Téléchargements ou une reprise de lecture, je veux pouvoir atteindre la fiche même si elle ne figure pas dans mon historique de navigation.
- En tant qu'utilisateur Android TV, je veux pouvoir effectuer cette action au D-Pad avec un retour visuel de focus clair.
- En tant qu'utilisateur hors ligne, je veux accéder à la fiche d'un contenu téléchargé sans être bloqué par l'absence de réseau.

## Comportement attendu

- La cover affichée dans le panneau inférieur des contrôles de `VodPlayerScreen` et `SeriesPlayerScreen` devient une action interactive.
- Un appui tactile/clic sur mobile, ou une validation au D-Pad sur TV lorsque la cover est focusée, demande l'ouverture de la fiche correspondant au contenu en lecture.
- Avant de quitter le player, l'application vérifie que la cible de détail peut être identifiée. Une fois l'action acceptée, elle suit le même cycle de fermeture propre que Retour ou Fermer : mémorisation de la position courante, arrêt de la lecture et libération des ressources du player.
- Pour un film, la destination est sa `VodDetailsScreen`. Pour un épisode, la destination est la `SeriesDetailsScreen` de la série à laquelle il appartient ; l'épisode lu reste identifiable dans le contexte de la fiche selon les capacités existantes de celle-ci.
- Si la fiche correspondante est l'écran immédiatement précédent dans la pile, le player se ferme par un simple retour : aucune nouvelle fiche en double n'est ajoutée.
- Si la fiche n'est pas immédiatement précédente (lecture lancée depuis l'Accueil, Téléchargements, reprise ou autre point d'entrée direct), le player se ferme puis la fiche cible est ouverte. Un retour depuis cette fiche ramène alors au point d'entrée antérieur, et jamais à un player déjà fermé.
- L'action est à déclenchement unique : plusieurs clics/validations rapides ne doivent ni ouvrir plusieurs fiches, ni enregistrer plusieurs fermetures concurrentes, ni laisser une lecture active en arrière-plan.

## Parcours utilisateur

### Film ouvert depuis sa fiche

1. L'utilisateur ouvre la fiche d'un film puis lance sa lecture.
2. Dans les contrôles du player, il sélectionne la cover.
3. La position est sauvegardée et le player est fermé proprement.
4. L'utilisateur revient sur la même fiche film, sans nouvelle entrée dupliquée dans l'historique.

### Film ou épisode lancé directement

1. L'utilisateur lance un film ou épisode depuis l'Accueil, les Téléchargements ou « Continuer la lecture », sans passer par sa fiche.
2. Il sélectionne la cover dans les contrôles du player.
3. La position est sauvegardée, le player est fermé et la fiche cible s'ouvre.
4. Depuis cette fiche, le bouton Retour ramène l'utilisateur à l'écran qui avait lancé le player, pas à un écran de lecture fermé.

### Contenu téléchargé hors-ligne

1. Sans connexion, l'utilisateur lit un film ou épisode téléchargé.
2. Il sélectionne la cover.
3. Le player se ferme proprement et la fiche s'ouvre depuis les données locales disponibles, sans attente d'un appel réseau.

## Règles métier et cas limites

- Cette action concerne exclusivement les players VOD Films et Séries ; elle ne modifie pas le player Live TV ni ses contrôles.
- La cover conserve son rôle visuel actuel et reçoit en plus une sémantique d'action accessible (libellé compréhensible par les technologies d'assistance, par exemple « Ouvrir la fiche du média »).
- Les autres actions de contrôle (lecture/pause, progression, fermeture, pistes et sous-titres) conservent leur comportement existant.
- La position sauvegardée est la dernière position connue au moment de l'activation, suivant exactement les règles existantes de reprise de lecture ; l'action ne remet pas la progression à zéro.
- Le retour depuis la fiche ouverte ne relance pas automatiquement le player. L'utilisateur peut relancer la lecture depuis la fiche selon le comportement existant.
- Si les informations nécessaires à la fiche ne peuvent pas être résolues localement (contenu supprimé, données de téléchargement incohérentes ou catalogue absent), l'application ne ferme pas le player, ne lance pas de navigation incomplète et affiche un message d'erreur utilisateur non technique. La lecture en cours reste utilisable.
- Si la fermeture du player est déjà engagée (Retour, Fermer ou clic précédent), les activations ultérieures de la cover sont ignorées jusqu'à la fin de la transition.
- Le périmètre est limité au raccourci vers les fiches depuis la cover ; il n'ajoute ni contenu de fiche, ni téléchargement, ni nouvelle capacité de lecture.

## Critères d'acceptation

- [ ] Dans les lecteurs film et série, la cover des contrôles est interactive sur mobile et sur Android TV.
- [ ] Sur Android TV, elle est atteignable au D-Pad, validable et possède un état de focus visuellement identifiable.
- [ ] L'activation depuis un film sauvegarde la progression, ferme proprement le player et ouvre la fiche du film correspondant.
- [ ] L'activation depuis un épisode sauvegarde la progression, ferme proprement le player et ouvre la fiche de la série correspondante.
- [ ] Lorsque la fiche est immédiatement derrière le player, l'action revient à cette fiche sans créer de doublon dans la pile de navigation.
- [ ] Lorsque la fiche n'est pas dans la position précédente, l'action ouvre la bonne fiche après fermeture du player et le Retour depuis cette fiche revient au point d'entrée initial, sans repasser par le player.
- [ ] L'action ne provoque pas de double navigation, de double sauvegarde de progression, ni de lecture encore active en arrière-plan après transition.
- [ ] Depuis une lecture téléchargée sans réseau, la fiche s'ouvre à partir des données locales disponibles sans requête réseau bloquante.
- [ ] Si aucune fiche fiable ne peut être résolue, un message utilisateur non technique est affiché et la lecture en cours n'est pas interrompue.
- [ ] Le player Live TV et les autres contrôles des players VOD/Séries ne changent pas de comportement.

---

# 7. Spécification technique

## 7.1 État des lieux du code (constats vérifiés)

- `VodPlayerScreen` (`presentation/vod/VodPlayerScreen.kt`) affiche la jaquette en `AsyncImage` non interactive (bloc inférieur, `details.coverBig`, 64×92 dp, `RoundedCornerShape(8.dp)`), et ferme via `handleClose` = `isPlayerVisible=false` + `exoPlayer.stop()` + `clearVideoSurface()` + `onClose()`.
- `SeriesPlayerScreen` (`presentation/series/SeriesPlayerScreen.kt`) : structure identique, jaquette = `seriesCover` (paramètre), `handleClose` identique.
- La sauvegarde de progression n'est pas dans `handleClose` : elle est portée par `TrackPlayerPosition` (`presentation/player/core/PositionTrackerCore.kt`) — sauvegarde périodique (5 s) + `onTrackerDispose` au démontage du composable. Réutiliser `handleClose` tel quel donne donc exactement le même cycle de sauvegarde que le bouton Fermer / Retour, sans double écriture.
- **La navigation n'est plus dupliquée** : `MainActivity.kt` compose `AppNavGraph` pour TV **et** mobile (« Unified Jetpack Compose Navigation Layout for BOTH TV and Mobile »). Le piège « double système de navigation » encore documenté dans `AGENTS.md` (section Conventions de code) est obsolète pour ce périmètre : un seul câblage dans `NavGraph.kt` couvre les deux plateformes. À signaler hors F16.
- Les routes cibles lisent leur identifiant d'entrée dans un `rememberSaveable` figé à la première composition : `vod_details` ← `activeVodMovie?.streamId`, `series_details` ← `activeSeriesShow?.seriesId` (`NavGraph.kt`). Ouvrir une fiche depuis le player impose donc de **positionner l'état hoisté avant `navigate`**.
- Hors ligne, `VodRepositoryImpl.getVodDetails` sert la fiche depuis le cache Room quel que soit son âge quand `networkMonitor.isCurrentlyOnline()` est faux : l'ouverture de la fiche ne dépend d'aucun appel réseau bloquant (idem série). Aucune adaptation offline supplémentaire n'est requise.
- **Trou de données identifié (bloquant pour un critère d'acceptation)** : `SeriesViewModel.savePosition` persiste `seriesId = _state.value.selectedSeriesDetails?.seriesId`, or le ViewModel utilisé par la route `series_player` est scopé à cette `NavBackStackEntry` et n'appelle jamais `selectStreamId` → `selectedSeriesDetails` est toujours `null` dans le player → `playback_positions.seriesId` est écrit à `null`. De plus `NavGraph.onPlayResumeWatchingSeries` reconstruit `SeriesDetails(seriesId = 0, …)` en ignorant `position.seriesId`. Sans correction, l'action sur la cover depuis une reprise « Continuer la lecture » série ne peut jamais résoudre la fiche.

## 7.2 Fichiers modifiés

| Fichier | Nature de la modification |
| --- | --- |
| `presentation/navigation/PlayerDetailsNavigation.kt` | **Nouveau** — objet pur portant la règle de décision de navigation (testable JVM). |
| `presentation/player/PlayerUiComponents.kt` | **Nouveau composable partagé** `PlayerCoverAction` (jaquette focusable/cliquable + placeholder). |
| `presentation/vod/VodPlayerScreen.kt` | Paramètres `canOpenDetails` / `onOpenDetails`, garde `isLeaving`, remplacement de l'`AsyncImage` par `PlayerCoverAction`, notification d'erreur locale, lien de focus TV. |
| `presentation/series/SeriesPlayerScreen.kt` | Idem + passage explicite de `seriesId` à `viewModel.savePosition`. |
| `presentation/series/SeriesViewModel.kt` | `savePosition(...)` reçoit un paramètre `seriesId: Int?` explicite (repli sur l'état actuel si `null`). |
| `presentation/navigation/NavGraph.kt` | Câblage des deux routes player, propagation de `position.seriesId` dans `onPlayResumeWatchingSeries`. |
| `res/values/strings.xml` | 2 chaînes : libellé d'action accessible, message d'erreur non technique. |
| `app/src/test/.../presentation/navigation/PlayerDetailsNavigationTest.kt` | **Nouveau** — tests unitaires de la règle de décision. |
| `app/src/test/.../presentation/series/SeriesViewModelTest.kt` | Test de non-régression sur la persistance de `seriesId`. |

## 7.3 Nouveaux composants

### `PlayerDetailsNavigation` (objet pur, `presentation/navigation/`)

```kotlin
enum class PlayerDetailsAction { POP_TO_DETAILS, REPLACE_WITH_DETAILS, UNAVAILABLE }

object PlayerDetailsNavigation {
    const val VOD_DETAILS_ROUTE = "vod_details"
    const val SERIES_DETAILS_ROUTE = "series_details"

    fun resolve(
        detailsRoute: String,        // route cible ("vod_details" | "series_details")
        targetId: Int?,              // streamId du film / seriesId de la série
        previousRoute: String?,      // navController.previousBackStackEntry?.destination?.route
        previousTargetId: Int?       // identifiant figé par l'entrée fiche précédente
    ): PlayerDetailsAction
}
```

Règles (dans cet ordre) :
1. `targetId == null || targetId <= 0` → `UNAVAILABLE` ;
2. `previousRoute == detailsRoute && previousTargetId == targetId` → `POP_TO_DETAILS` ;
3. sinon → `REPLACE_WITH_DETAILS`.

### `PlayerCoverAction` (composable partagé, `presentation/player/PlayerUiComponents.kt`)

```kotlin
@Composable
fun PlayerCoverAction(
    coverUrl: String?,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
)
```

- Conserve le rendu actuel (64×92 dp, `ContentScale.Crop`, coins 8 dp, fond `Surface3`).
- `Modifier.onFocusChanged { … }.clickable(onClick = onClick).focusable()` + bordure de focus 2 dp `colorScheme.primary` (même grammaire visuelle que `PlayerTopButton`).
- `coverUrl` vide/nul → placeholder plein (fond `Surface3` + icône `Icons.Default.Movie`) **restant interactif** : le raccourci ne disparaît pas quand la jaquette manque.
- `contentDescription` porte la sémantique d'action (« Ouvrir la fiche du média »).

## 7.4 Contrat des écrans player

Signatures étendues (aucun autre paramètre modifié) :

```kotlin
fun VodPlayerScreen(…, onClose: () -> Unit, canOpenDetails: Boolean, onOpenDetails: () -> Unit, …)
fun SeriesPlayerScreen(…, onClose: () -> Unit, canOpenDetails: Boolean, onOpenDetails: () -> Unit, …)
```

- Le player **ne connaît pas** la navigation ni les routes : il reçoit un booléen de disponibilité et un callback opaque.
- Garde d'unicité : `var isLeaving by remember { mutableStateOf(false) }`.
  - `handleClose` devient `{ if (!isLeaving) { isLeaving = true; isPlayerVisible = false; exoPlayer.stop(); exoPlayer.clearVideoSurface(); onClose() } }` — protège aussi Retour / Fermer / fin de lecture contre une double fermeture.
  - `handleOpenDetails` = `{ if (!isLeaving) { if (canOpenDetails) { isLeaving = true; isPlayerVisible = false; exoPlayer.stop(); exoPlayer.clearVideoSurface(); onOpenDetails() } else { detailsErrorNotification = <message> } } }`.
- Message d'erreur : réutilise le patron transitoire existant (`resizeModeNotification` : `Box` centré, fond `0x99000000`, effacé par `LaunchedEffect` + `delay`). La lecture n'est ni arrêtée ni mise en pause.

## 7.5 Focus Android TV (tranche la question ouverte Q1)

- Contrainte existante : le `Box` racine du player consomme `DirectionLeft`/`DirectionRight` (recul/avance 10 s) ; la navigation D-pad dans l'overlay est donc **verticale**. `Up`/`Down` ne sont pas consommés (`else -> { showControls = true; false }`) et alimentent la recherche de focus 2D.
- La cover est le premier élément focusable du bloc inférieur (elle précède le Slider et la barre d'actions dans l'ordre de composition).
- Pour ne rien laisser à l'heuristique de la recherche 2D (la cover est à gauche, le transport est centré), on pose un lien explicite, uniquement sur TV :
  - `coverFocusRequester` sur `PlayerCoverAction` ;
  - `PlayPauseButton(modifier = Modifier.focusProperties { if (isTv) down = coverFocusRequester })` — les composants partagés acceptent déjà un `modifier`, aucune refonte n'est nécessaire.
- La barre de progression garde son comportement : le lien descendant vise la cover, pas le Slider ; depuis la cover, la remontée et le déplacement latéral restent la recherche de focus par défaut.
- L'activation `DPAD_CENTER`/`Enter` est consommée par le `clickable` de la cover focusée : elle ne déclenche pas le `togglePlayPause()` du `Box` racine (l'événement ne remonte qu'à défaut de consommation).

## 7.6 Résolution de la cible et données de navigation (Q2/Q3)

| Point d'entrée | Film | Série |
| --- | --- | --- |
| Depuis la fiche | `activeVodDetails.streamId` (fiable) | `activeSeriesDetails.seriesId` (fiable) |
| Téléchargements / Accueil (téléchargé) | `buildOfflineVodDetails(item).streamId` (fiable) | `buildOfflineSeriesDetails(item, …).seriesId = item.seriesId ?: 0` (fiable si `seriesId` a été stocké au téléchargement) |
| « Continuer la lecture » | `position.streamId` (fiable) | `position.seriesId` — **à propager** (cf. 7.1) |

Corrections de données associées, strictement au service du critère d'acceptation « épisode lancé depuis l'Accueil » :

1. `NavGraph.onPlayResumeWatchingSeries` : `SeriesDetails(seriesId = position.seriesId ?: 0, …)` au lieu de `0` en dur.
2. `SeriesViewModel.savePosition(…, seriesId: Int? = null)` : le paramètre explicite prime, repli sur `_state.value.selectedSeriesDetails?.seriesId`. `SeriesPlayerScreen` passe le `seriesId` qu'il reçoit déjà (`seriesId.takeIf { it > 0 }`).

Aucune migration Room : la colonne `seriesId` existe déjà dans `PlaybackPositionEntity` (nullable). Les lignes historiques restent à `null` → elles retombent sur le cas `UNAVAILABLE` (message utilisateur), et se réparent dès la prochaine lecture.

Hors ligne : la fiche est ouverte avec les mêmes chemins que le reste de l'app (cache Room servi sans TTL quand `networkMonitor` est hors ligne). Aucun appel réseau ajouté par F16.

## 7.7 API, stockage, cache, performances, sécurité, compatibilité

- **API** : aucune nouvelle interface Retrofit → aucune règle `-keep` à ajouter dans `proguard-rules.pro`. Aucun appel Xtream/TMDB supplémentaire.
- **Stockage / cache** : aucun schéma modifié, aucune entité ajoutée ; seule une valeur déjà prévue (`seriesId`) est désormais réellement écrite.
- **Performances** : coût nul en lecture (un `Modifier.clickable/focusable` et un `FocusRequester` par player). La fiche ouverte réutilise le cache Room quand il est frais ; sinon le comportement de chargement existant s'applique.
- **Sécurité** : aucun identifiant supplémentaire manipulé ; les URLs de lecture ne transitent pas dans la navigation ajoutée.
- **Compatibilité** : `clickable` / `focusable` / `focusProperties` sont disponibles sur tout le socle Compose du projet (min SDK 21). Mode PIP inchangé (l'overlay est masqué en PIP, donc la cover est inatteignable en PIP, ce qui est le comportement voulu).
- **Live TV** : `PlayerScreen` n'est pas touché.

---

# 8. Architecture

## 8.1 Flux de données

```
[VodPlayerScreen / SeriesPlayerScreen]
   PlayerCoverAction.onClick
      └─> handleOpenDetails()
            ├─ isLeaving == true            -> ignoré (règle « déclenchement unique »)
            ├─ canOpenDetails == false      -> notification d'erreur locale, lecture poursuivie
            └─ sinon : isLeaving = true
                       exoPlayer.stop() + clearVideoSurface()
                       onOpenDetails()
                          │
[NavGraph — route "vod_player" / "series_player"]
                          ▼
              PlayerDetailsNavigation.resolve(route, targetId, previousRoute, previousTargetId)
                          │
        ┌─────────────────┼──────────────────────────┐
        ▼                 ▼                          ▼
  POP_TO_DETAILS   REPLACE_WITH_DETAILS          UNAVAILABLE
  popBackStack()   onActiveVodMovieChanged(…)     (jamais atteint : canOpenDetails==false)
                   / onActiveSeriesShowChanged(…)
                   popBackStack()
                   navigate("vod_details" | "series_details")
                          │
                          ▼
        [démontage du player] -> TrackPlayerPosition.onTrackerDispose
                                  -> viewModel.savePosition(...) (cycle existant, une seule fois)
```

`canOpenDetails` est calculé dans `NavGraph` comme `PlayerDetailsNavigation.resolve(...) != UNAVAILABLE`, la décision étant mémorisée (`remember`) à la composition de l'entrée player pour que `previousBackStackEntry` soit lu avant toute transition.

Reconstruction de l'état hoisté avant `navigate` (obligatoire : les routes fiches figent leur identifiant à la première composition) :

- film → `onActiveVodMovieChanged(VodStream(streamId = details.streamId, name = details.name, streamIcon = details.coverBig, rating = details.rating, added = null, categoryId = "0"))` ;
- série → `onActiveSeriesShowChanged(SeriesStream(seriesId = seriesId, name = seriesName, cover = seriesCover, rating = null, added = null, categoryId = "0"))`.

Ces objets ne servent qu'à transporter l'identifiant : la fiche recharge ses données via `selectStreamId` (même mécanisme que les entrées Favoris/Recherche, qui utilisent déjà des stubs `VodStream(id, "Film Favori", …)`).

## 8.2 Responsabilités

| Composant | Responsabilité | Ce qu'il ne fait pas |
| --- | --- | --- |
| `PlayerCoverAction` | Rendu + focus + activation de la jaquette | Ne décide rien de la navigation |
| `VodPlayerScreen` / `SeriesPlayerScreen` | Cycle de fermeture propre, garde d'unicité, message d'erreur | Ne connaît ni routes ni `NavController` |
| `PlayerDetailsNavigation` | Règle de décision pure (pop vs replace vs indisponible) | Aucun accès à Compose / Android |
| `NavGraph` | Lecture du backstack, exécution de la décision, reconstruction de l'état hoisté | Aucune logique de lecture |
| `SeriesViewModel` | Persistance de la position avec un `seriesId` fiable | — |

## 8.3 Décisions techniques

- **D1 — Règle de navigation extraite en objet pur.** Le `NavController` n'est pas testable en JVM pure (aucun `androidTest` dans le projet, et les tests sur device sont exclus par `AGENTS.md`). Isoler la décision dans `PlayerDetailsNavigation` rend le seul point réellement risqué (pop vs replace) couvrable par `testDebugUnitTest`.
- **D2 — Le player reste ignorant de la navigation** (`canOpenDetails: Boolean` + `onOpenDetails: () -> Unit`). Cohérent avec `onClose`/`onStreamChanged` déjà en place, et conserve les composables player réutilisables. Alternative écartée : passer le `NavController` au player (couplage présentation ↔ navigation, non testable).
- **D3 — Composable partagé plutôt que duplication.** La jaquette est identique dans les deux players ; `PlayerUiComponents.kt` est déjà le lieu prévu pour éviter la triplication (Phase 60).
- **D4 — Lien de focus TV explicite** (`focusProperties { down = coverFocusRequester }` depuis le play/pause) plutôt que confiance à la recherche 2D : la cover est à gauche alors que le transport est centré et le Slider est interposé. Décision retenue faute de possibilité de valider le focus automatiquement (pas de tests instrumentés).
- **D5 — Placeholder interactif quand la jaquette est absente** : sans cela le raccourci disparaîtrait silencieusement sur les contenus sans affiche (notamment reprises et téléchargements anciens).
- **D6 — Garde `isLeaving` sur le cycle de sortie complet** (cover, Retour, Fermer, fin de lecture) : le critère « pas de double navigation, pas de double sauvegarde » vaut pour toutes les sorties, pas seulement pour la cover.
- **D7 — Erreur affichée dans le player, lecture préservée**, via le patron de notification transitoire déjà utilisé pour le changement de format d'image : pas de nouveau composant de dialogue, pas d'interruption de lecture (règle métier explicite).
- **D8 — Correction de la propagation de `seriesId`** limitée au strict nécessaire (deux points d'écriture/lecture) : sans elle, le critère « épisode lancé depuis l'Accueil » est structurellement infaisable. Le repli sur l'état du ViewModel préserve le comportement des autres appelants.
- **D9 — Périmètre gelé côté fiche** : aucune évolution de `VodDetailsScreen` / `SeriesDetailsScreen` (pas de pré-sélection de saison/épisode). La spécification fonctionnelle ne demande que l'ouverture de la fiche « selon les capacités existantes ».
- **D10 — Un seul câblage `NavGraph`** couvre TV et mobile (constat 7.1) ; aucune modification de `MainActivity.kt` autre que l'absence de modification.

## 8.4 Risques techniques

| # | Risque | Mitigation |
| --- | --- | --- |
| R1 | Positions série historiques sans `seriesId` → cover inopérante sur d'anciennes reprises | Cas `UNAVAILABLE` explicitement spécifié (message non technique) ; auto-réparation à la lecture suivante |
| R2 | Le lien de focus TV ne peut pas être validé automatiquement (pas d'`androidTest`) | Lien explicite plutôt qu'heuristique (D4) ; vérification visuelle hors critères de validation de l'agent, conformément à `AGENTS.md` |
| R3 | `previousBackStackEntry` évalué trop tard (après un début de transition) | Décision figée dans un `remember` à la composition de l'entrée player |
| R4 | `popBackStack()` + `navigate()` : si rien n'est dépilé (cas théorique où le player serait racine), la fiche s'empilerait sur le player | Le résultat booléen de `popBackStack()` est ignoré volontairement mais les deux routes player ne sont jamais `startDestination` ; état documenté ici |
| R5 | Le `clickable` de la cover masquerait le `clickable` plein écran (bascule des contrôles) | Comportement voulu : la zone de la jaquette devient une action ; le reste de l'écran est inchangé |

## 8.5 Stratégie de tests (automatisés uniquement)

Couvrable en `./gradlew testDebugUnitTest` :

- `PlayerDetailsNavigationTest` : `UNAVAILABLE` (id `null`, `0`, négatif) ; `POP_TO_DETAILS` (route précédente = fiche **et** même identifiant) ; `REPLACE_WITH_DETAILS` (route précédente autre, fiche d'un autre média, `previousRoute == null`) ; non-confusion des routes film/série.
- `SeriesViewModelTest` : `savePosition` avec `seriesId` explicite → persisté ; sans paramètre → repli sur l'état, comportement inchangé (non-régression).

Explicitement exclus des critères de validation (`AGENTS.md`, règle 9) : parcours de focus D-pad, rendu de la bordure de focus, transitions de navigation réelles — non couvrables sans device/émulateur.

## 8.6 Points laissés ouverts

- L'obsolescence de la note « double système de navigation » dans `AGENTS.md` est constatée mais **non corrigée ici** (hors périmètre F16) : à traiter par une fiche `ai/technical/`.
- La version cible initiale de la fiche (`v1.56.0`) était antérieure au dépôt (dernier tag `v1.58.2`) ; ramenée à **v1.59.0** (MINOR : nouvelle fonctionnalité compatible).

---

# 9. Plan de développement

## Ordre d'exécution et dépendances

1. Tâches 1 et 2 peuvent être réalisées indépendamment.
2. La tâche 3 dépend de la tâche 1.
3. La tâche 4 dépend de la tâche 2 ; la tâche 5 dépend de la tâche 4.
4. Les tâches 6 et 7 dépendent respectivement des tâches 3 et 5.
5. La tâche 8 dépend des tâches 6 et 7 ; elle clôt l'implémentation et la validation automatisée de F16.

### Tâche 1 — Formaliser et tester la décision de navigation

- [x] Créer `PlayerDetailsNavigation` et ses tests JVM.

**Objectif :** isoler la décision `POP_TO_DETAILS` / `REPLACE_WITH_DETAILS` / `UNAVAILABLE`, sans dépendance Android, afin de garantir le comportement de pile pour un film comme pour une série.

**Fichiers :**

- `app/src/main/java/com/cstv/app/presentation/navigation/PlayerDetailsNavigation.kt` (nouveau)
- `app/src/test/java/com/cstv/app/presentation/navigation/PlayerDetailsNavigationTest.kt` (nouveau)

**Validation :** les tests couvrent les identifiants `null`, nul et négatif, la fiche immédiatement précédente avec le même identifiant, les autres routes, une fiche d'un autre média et la non-confusion des routes film/série ; `./gradlew testDebugUnitTest` passe.

### Tâche 2 — Créer la cover interactive partagée

- [x] Ajouter le composable partagé `PlayerCoverAction`.

**Objectif :** remplacer la jaquette passive par une action réutilisable qui conserve le rendu 64×92 dp, reste utilisable sans image et expose une sémantique accessible ainsi qu'un focus TV visible.

**Fichiers :**

- `app/src/main/java/com/cstv/app/presentation/player/PlayerUiComponents.kt`

**Validation :** le composable accepte une URL nullable, un libellé et un callback ; le placeholder reste cliquable ; le focus applique une bordure `primary` sans modifier les autres composants player ; la compilation Kotlin réussit.

### Tâche 3 — Intégrer l'action dans le player VOD

- [x] Câbler la cover VOD, la garde de sortie et le message d'indisponibilité.

**Objectif :** rendre la cover du player film actionnable une seule fois, arrêter et nettoyer le lecteur selon le cycle de fermeture existant, ou laisser la lecture intacte quand aucune fiche fiable n'est disponible.

**Fichiers :**

- `app/src/main/java/com/cstv/app/presentation/vod/VodPlayerScreen.kt`

**Validation :** les paramètres `canOpenDetails` et `onOpenDetails` sont opaques au player ; `isLeaving` protège cover, Retour, Fermer et fin de lecture ; `PlayerCoverAction` reçoit le bon visuel et le message non technique est transitoire sans arrêt de lecture ; `./gradlew testDebugUnitTest` passe.

### Tâche 4 — Fiabiliser l'identifiant de série sauvegardé

- [x] Permettre à `savePosition` de recevoir explicitement le `seriesId` et ajouter la non-régression.

**Objectif :** garantir qu'une position d'épisode porte la série correspondante, y compris lorsque le ViewModel est scopé au player et n'a pas de fiche sélectionnée.

**Fichiers :**

- `app/src/main/java/com/cstv/app/presentation/series/SeriesViewModel.kt`
- `app/src/test/java/com/cstv/app/presentation/series/SeriesViewModelTest.kt`

**Validation :** un `seriesId` explicite est persisté ; sans paramètre, le repli sur l'état existant est conservé ; les tests unitaires passent sans migration Room ni changement de schéma.

### Tâche 5 — Intégrer l'action dans le player Séries

- [x] Câbler la cover Série et transmettre le `seriesId` à la sauvegarde de position.

**Objectif :** offrir le même comportement de raccourci et d'unicité que pour les films tout en rendant l'identifiant de la fiche série disponible à la reprise de lecture suivante.

**Fichiers :**

- `app/src/main/java/com/cstv/app/presentation/series/SeriesPlayerScreen.kt`

**Validation :** la cover utilise `PlayerCoverAction`, applique la même garde `isLeaving` et passe `seriesId.takeIf { it > 0 }` à `savePosition` ; l'absence d'identifiant affiche seulement le message utilisateur et préserve la lecture ; `./gradlew testDebugUnitTest` passe.

### Tâche 6 — Câbler la navigation film dans `NavGraph`

- [x] Relier la route `vod_player` à la décision de navigation et à la fiche VOD cible.

**Objectif :** revenir par `popBackStack()` vers la fiche immédiatement précédente sans doublon, ou fermer le player puis ouvrir la bonne fiche VOD en restaurant l'état hoisté avant la navigation.

**Fichiers :**

- `app/src/main/java/com/cstv/app/presentation/navigation/NavGraph.kt`

**Validation :** la décision est mémorisée à la composition de l'entrée player ; un identifiant indisponible n'appelle pas la navigation ; le chemin de remplacement positionne le `VodStream` minimal avant `navigate("vod_details")` ; `./gradlew testDebugUnitTest` passe.

### Tâche 7 — Câbler la navigation Série et la reprise dans `NavGraph`

- [x] Relier la route `series_player` à la fiche série et propager `PlaybackPosition.seriesId` lors d'une reprise.

**Objectif :** ouvrir ou retrouver la fiche de la série de l'épisode lu, y compris depuis « Continuer la lecture », sans recréer un player dans la pile.

**Fichiers :**

- `app/src/main/java/com/cstv/app/presentation/navigation/NavGraph.kt`

**Validation :** le chemin de remplacement initialise le `SeriesStream` minimal avant `navigate("series_details")` ; `onPlayResumeWatchingSeries` utilise `position.seriesId ?: 0` ; les lignes historiques sans identifiant sont bloquées par `UNAVAILABLE` ; `./gradlew testDebugUnitTest` passe.

### Tâche 8 — Finaliser les ressources et vérifier l'intégration

- [x] Ajouter les chaînes F16 et exécuter la validation automatisée ciblée.

**Objectif :** fournir des textes accessibles et non techniques, puis confirmer que les chemins Film/Série, les tests de décision et la persistance restent cohérents ensemble.

**Fichiers :**

- `app/src/main/res/values/strings.xml`
- fichiers des tâches 1 à 7, uniquement si une correction est révélée par la validation

**Validation :** les chaînes « Ouvrir la fiche du média » et l'erreur d'indisponibilité sont utilisées par les players ; `./gradlew testDebugUnitTest`, `./gradlew assembleDebug`, `./gradlew lintDebug` et `git diff --check` passent. Les vérifications manuelles mobile/Android TV (focus D-Pad, transition réelle, offline) restent explicitement hors critères de validation automatisée conformément à `AGENTS.md`.

## Résultat de l'étape 5

- Implémentation des huit tâches terminée, sans modification du player Live TV, du schéma Room ou des fiches de détail.
- Validations automatisées réussies le 2026-07-26 : `./gradlew testDebugUnitTest`, `./gradlew assembleDebug`, `./gradlew lintDebug` et `git diff --check`.
- La vérification manuelle mobile/Android TV n'a pas été exécutée : l'ADB du SDK est présent mais son daemon ne peut pas ouvrir son `smartsocket` dans cet environnement (`Operation not permitted`). Elle reste hors des critères automatisés et sera à traiter à l'étape de validation demandée ultérieurement.

---

# 10. Review technique (Étape 6 — 2026-07-26)

Revue de l'implémentation des tâches 1 à 8. **Aucune modification de code effectuée à cette étape.**

Status: RESOLVED (Étape 7 — 2026-07-26)

Périmètre relu : `PlayerDetailsNavigation.kt`, `PlayerDetailsNavigationTest.kt`, `PlayerUiComponents.kt` (`PlayerCoverAction`), diffs `VodPlayerScreen.kt`, `SeriesPlayerScreen.kt`, `SeriesViewModel.kt`, `NavGraph.kt`, `strings.xml`, `SeriesViewModelTest.kt`. Contexte croisé : `PlayerOverlayCore.kt`, `PositionTrackerCore.kt`, `MainActivity.kt` (`buildOfflineSeriesDetails`).

**Verdict : conforme aux spécifications §6/§7/§8. 0 Critique, 3 Majeur, 6 Mineur.** Les points Majeur sont à traiter à l'étape 7 (deux d'entre eux sont des écarts par rapport à ce que la fiche elle-même engage).

Vérifications de correctness confirmées :
- `PlayerDetailsNavigation.resolve` applique exactement l'ordre de règles spécifié en §7.3 (garde d'identifiant → pop → replace) et reste sans dépendance Android.
- Le cycle de sortie de la cover est strictement celui de `handleClose` (`isPlayerVisible=false` + `stop()` + `clearVideoSurface()`), donc la sauvegarde reste portée par `TrackPlayerPosition.onTrackerDispose` : aucune écriture supplémentaire ni double sauvegarde.
- La garde `isLeaving` couvre bien les quatre sorties (cover, Retour, Fermer, fin de lecture) : `handleClose` est le point de passage unique de tous les appels observés (`BackHandler`, bouton retour de la barre supérieure, bouton de fermeture, overlay d'erreur).
- Chaîne de propagation de `seriesId` cohérente de bout en bout : `SeriesPlayerScreen` (3 appels `savePosition`) → `SeriesViewModel.savePosition(seriesId ?: état)` → `PlaybackPositionEntity.seriesId` → `NavGraph.onPlayResumeWatchingSeries` (`position.seriesId ?: 0`) → `canOpenDetails`. Le chemin Téléchargements était déjà correct (`buildOfflineSeriesDetails` → `item.seriesId ?: 0`).
- `PlayerCoverAction` et `PlayPauseButton` appartiennent au même bloc `PlayerOverlayHost` : le `coverFocusRequester` visé par `focusProperties { down = … }` est toujours attaché quand le lien peut être résolu (contrôles masqués ou PIP ⇒ ni l'un ni l'autre n'est composé). Pas de risque de `FocusRequester is not initialized`.
- La bordure de focus reste visible malgré l'`AsyncImage` en `matchParentSize` : `Modifier.border` dessine après `drawContent()`.
- Aucun appel réseau ajouté, aucun schéma Room modifié, `PlayerScreen` (Live TV) intact.
- `./gradlew testDebugUnitTest` : `BUILD SUCCESSFUL` (état à jour, aucun test en échec).

## Critique

_(néant)_

## Majeur

### J1 — Test de non-régression du repli `seriesId` absent
- **Description** : §8.5 et la tâche 4 exigent deux tests sur `savePosition` : `seriesId` explicite persisté **et** repli sur `_state.value.selectedSeriesDetails?.seriesId` quand le paramètre est omis. Seul le premier existe (`savePositionPersistsExplicitSeriesIdWithoutSelectedDetails`, `SeriesViewModelTest.kt:170`). Aucun autre test du fichier n'appelle `savePosition`.
- **Impact** : la branche `?: _state.value.selectedSeriesDetails?.seriesId` n'est couverte par aucun test. Une régression sur les appelants historiques (fiche série ouverte, `savePosition` sans le nouveau paramètre) passerait inaperçue, alors que c'est précisément le risque introduit par le changement de signature.
- **Correction attendue** : ajouter un test `savePositionFallsBackToSelectedSeriesDetailsId` — `selectStreamId(42)` (stub `getSeriesDetailsUseCase`), puis `savePosition(...)` sans argument `seriesId`, et `verify(savePlaybackPositionUseCase).invoke(seriesId = eq(42), …)`.

### J2 — La décision de navigation n'est pas figée comme la fiche l'affirme
- **Description** : §8.1 et le risque R3 engagent une décision « mémorisée (`remember`) à la composition de l'entrée player pour que `previousBackStackEntry` soit lu avant toute transition ». Le code (`NavGraph.kt:657` et `NavGraph.kt:709`) utilise `remember(details.streamId, previousRoute, activeVodMovie?.streamId)` avec `previousRoute` relu à **chaque** recomposition depuis `navController.previousBackStackEntry` — qui n'est ni un `State` ni une valeur stable pendant une transition. La valeur n'est donc pas figée, et la clé n'est pas non plus fiable (elle change silencieusement au lieu de protéger).
- **Impact** : toute recomposition de l'entrée player survenant après le début d'un `popBackStack()` (retour, fin de lecture, changement d'épisode) peut recalculer `navigationAction` et basculer `POP_TO_DETAILS` → `REPLACE_WITH_DETAILS`, c'est-à-dire créer une fiche en doublon dans la pile — le défaut exact que le critère d'acceptation interdit. Fenêtre étroite mais réelle, et non détectable par les tests JVM.
- **Correction attendue** : capturer la décision une seule fois par entrée player, p. ex. `val navigationAction = remember { PlayerDetailsNavigation.resolve(…, previousRoute = navController.previousBackStackEntry?.destination?.route, …) }` (sans clés dérivées du backstack), ou aligner §8.1/R3 sur le comportement réellement implémenté si le recalcul est assumé.

### J3 — Sémantique d'accessibilité portée par l'image, pas par la zone cliquable
- **Description** : dans `PlayerCoverAction` (`PlayerUiComponents.kt`), `contentDescription` est passé à l'`AsyncImage`/`Icon` enfant, tandis que l'action (`clickable`) est sur le `Box` parent, sans `Role.Button`, sans `onClickLabel` et sans `semantics(mergeDescendants = true)`. De plus, le libellé reste « Ouvrir la fiche du média » même quand `canOpenDetails` est faux, où l'activation n'affiche qu'un message d'indisponibilité.
- **Impact** : la règle métier §6 (« sémantique d'action accessible … par exemple "Ouvrir la fiche du média" ») et le critère d'accessibilité ne sont que partiellement remplis : l'élément peut être annoncé comme une image et non comme une action, et l'annonce est trompeuse dans le cas indisponible.
- **Correction attendue** : porter la sémantique sur le nœud cliquable — `Modifier.semantics(mergeDescendants = true) { role = Role.Button; contentDescription = … }` (ou `clickable(onClickLabel = …, role = Role.Button)`) — et mettre `contentDescription = null` sur l'enfant décoratif. Optionnel mais cohérent : exposer un paramètre d'état (`isAvailable`) pour adapter le libellé annoncé.

## Mineur

### M1 — Imports `ContentScale` devenus inutilisés
- **Description** : `androidx.compose.ui.layout.ContentScale` reste importé dans `VodPlayerScreen.kt:41` et `SeriesPlayerScreen.kt:45` alors que l'unique usage (l'`AsyncImage` de la jaquette) a migré dans `PlayerCoverAction`. Une seule occurrence subsiste dans chaque fichier : l'import lui-même.
- **Impact** : bruit ; non détecté par `lintDebug` (avertissement IDE seulement).
- **Correction attendue** : supprimer les deux imports.

### M2 — Bloc de navigation dupliqué dans `NavGraph`
- **Description** : les branches `vod_player` et `series_player` répètent la même structure (~30 lignes chacune) : lecture de `previousBackStackEntry`, `remember` de la décision, `when` à trois branches, reconstruction du stub, `popBackStack()` + `navigate(route)`.
- **Impact** : maintenabilité — une correction (dont J2) doit être appliquée deux fois, avec risque de divergence entre le chemin film et le chemin série.
- **Correction attendue** (optionnelle) : extraire une fonction locale `openDetailsFrom(action, route, applyStub: () -> Unit)` dans `AppNavGraph`, ou un helper privé du fichier.

### M3 — `isLeaving` irréversible en cas de sortie sans effet
- **Description** : `isLeaving` ne repasse jamais à `false`. Si `onClose`/`onOpenDetails` n'aboutit pas (cas R4 : `popBackStack()` sans effet), le player reste composé avec la lecture arrêtée et toute nouvelle tentative de sortie est ignorée.
- **Impact** : théorique (aucune des deux routes player n'est `startDestination`), mais l'issue serait un écran figé sans sortie possible.
- **Correction attendue** (optionnelle) : soit documenter explicitement l'hypothèse dans la fiche (elle l'est partiellement en R4), soit faire remonter l'échec (`onClose: () -> Boolean`) et remettre `isLeaving = false` si la navigation n'a rien fait.

### M4 — Couverture de `PlayerDetailsNavigationTest` incomplète sur deux cas de bord
- **Description** : le cas `POP_TO_DETAILS` n'est testé que pour `VOD_DETAILS_ROUTE` (le chemin série n'est couvert que par des cas `REPLACE`), et le cas `previousRoute == detailsRoute` avec `previousTargetId == null` (fiche précédente dont l'identifiant n'a pas encore été figé) n'est pas testé.
- **Impact** : faible — la règle est symétrique par construction — mais la symétrie film/série n'est pas verrouillée par les tests.
- **Correction attendue** : ajouter deux assertions (`POP_TO_DETAILS` sur `SERIES_DETAILS_ROUTE` ; `REPLACE_WITH_DETAILS` pour `previousTargetId = null`).

### M5 — Notifications transitoires superposables
- **Description** : `detailsErrorNotification` et `resizeModeNotification` sont deux `Box` distincts, tous deux `align(Alignment.Center)` avec le même fond et le même délai de 2 s.
- **Impact** : cosmétique ; les deux messages se chevauchent si le format d'image est changé juste avant un clic sur une cover indisponible.
- **Correction attendue** (optionnelle) : un état de notification unique par player, ou un décalage vertical du message d'indisponibilité.

### M6 — Détails de rendu du placeholder et de l'indication de clic
- **Description** : (a) `Modifier.clip()` est appliqué après `clickable()` dans `PlayerCoverAction`, donc le ripple mobile n'est pas clippé aux coins arrondis sur une zone de 64×92 dp ; (b) le placeholder utilise `Icons.Default.Movie` y compris pour une série ; (c) le doublon `clickable().focusable()` crée deux cibles de focus (pattern déjà en place dans `PlayerTopButton`/`TransportButton`, donc non introduit par F16).
- **Impact** : cosmétique, aucun effet fonctionnel constaté.
- **Correction attendue** (optionnelle) : déplacer `.clip()` avant `.clickable()` ; le reste peut rester en l'état par cohérence avec les composants existants.

## Couverture de tests

- `PlayerDetailsNavigation` : 4 tests, règles principales couvertes (identifiants `null`/`0`/négatif, pop sur fiche identique, replace sur identifiant différent et sur `previousRoute == null`, non-confusion film/série). Adéquat, sous réserve de M4.
- `SeriesViewModel.savePosition` : branche « identifiant explicite » couverte ; branche de repli non couverte (J1).
- Câblage `NavGraph`, focus D-pad, rendu du focus et transitions réelles : non couvrables sans instrumentation — exclusion conforme à `AGENTS.md` (règle 9) et déjà actée en §8.5.

## Conformité aux critères d'acceptation (§6)

| Critère | État |
| --- | --- |
| Cover interactive dans les deux players (mobile + TV) | Conforme |
| Atteignable au D-pad, validable, focus identifiable | Conforme au code (lien explicite + bordure) ; validation visuelle hors périmètre automatisé |
| Film : sauvegarde + fermeture propre + fiche film | Conforme |
| Épisode : sauvegarde + fermeture propre + fiche série | Conforme |
| Fiche immédiatement derrière ⇒ pas de doublon | Conforme, **sous réserve de J2** |
| Fiche absente de la pile ⇒ ouverture + retour au point d'entrée | Conforme |
| Pas de double navigation / double sauvegarde / lecture résiduelle | Conforme (`isLeaving` + `TrackPlayerPosition` inchangé) |
| Hors ligne : ouverture depuis les données locales | Conforme (aucun appel réseau ajouté ; cache Room servi sans TTL hors ligne) |
| Fiche non résolue ⇒ message non technique, lecture préservée | Conforme ; libellé accessible à corriger (J3) |
| Live TV et autres contrôles inchangés | Conforme |

## Dette technique et périmètre

- Le `versionCode`/`versionName` reste à `1.58.2` et l'en-tête de la fiche à `Version: -` : le passage à **v1.59.0** annoncé en §8.6 est à réaliser à la clôture (étapes 8-10), pas à cette étape.
- Les modifications de `AGENTS.md` et `AI_DEVELOPMENT_WORKFLOW.md` présentes dans l'arbre de travail sont des évolutions de process hors F16 : elles ne font pas partie de ce périmètre.
- Rappel §8.6 : la note « double système de navigation » d'`AGENTS.md` est obsolète pour ce périmètre ; à traiter par une fiche `ai/technical/` distincte.

---

# 11. Corrections et validation finale (Étapes 7–8 — 2026-07-26)

## Corrections de la review

- **J1** : ajout du test de repli `savePositionFallsBackToSelectedSeriesDetailsId` ; la persistance garde le `seriesId` de la fiche sélectionnée lorsque le paramètre explicite est absent.
- **J2** : la décision `PlayerDetailsNavigation` est désormais capturée une seule fois avec `remember` à la composition de chaque entrée player ; aucun état du backstack n'est relu pendant la transition.
- **J3** : `PlayerCoverAction` expose maintenant une action `Role.Button` avec libellé de clic adapté à la disponibilité ; les images enfant sont décoratives pour éviter une annonce d'image à la place de l'action.
- **M1** : imports `ContentScale` inutilisés supprimés des deux players.
- **M2** : extraction de `NavHostController.openPlayerDetails` ; les chemins film et série partagent le même dépilage atomique avant la navigation.
- **M3** : les callbacks de sortie retournent un succès ; si le dépilage échoue, le player redevient visible, reprend sa préparation et accepte à nouveau les actions au lieu de rester figé.
- **M4** : ajout des cas de test `POP_TO_DETAILS` série et `previousTargetId = null`.
- **M5** : les notifications de format et d'indisponibilité utilisent un unique état `overlayNotification` dans chaque player ; elles ne peuvent plus se superposer.
- **M6** : le clipping de la cover précède le `clickable`, ce qui borne le ripple aux coins arrondis. Le placeholder et le pattern de focus existant restent cohérents avec les composants player partagés.

## Validation automatisée

- `./gradlew testDebugUnitTest` : **BUILD SUCCESSFUL**.
- `./gradlew assembleDebug` : **BUILD SUCCESSFUL**.
- `./gradlew lintDebug` : **BUILD SUCCESSFUL** dans le contexte Gradle autorisé (le sandbox restreint ne peut pas déterminer une IP utilisable).
- `git diff --check` : succès.

## Validation appareil

`/home/nnobre/Android/Sdk/platform-tools/adb devices` est accessible mais ne liste aucun appareil ni émulateur. Les parcours mobiles/Android TV visuels (focus D-pad, transition réelle et hors ligne) restent donc non exécutés et explicitement hors critères automatisés, conformément à `AGENTS.md`.

## Verdict

Les critères fonctionnels et techniques F16 sont validés par la revue corrigée et les contrôles automatisés. L'élément est **RELEASED** ; les étapes 9 (documentation) et 10 (livraison) sont effectuées et validées dans cette version.
