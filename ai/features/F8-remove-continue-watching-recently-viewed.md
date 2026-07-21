# F8 - Retirer des listes « Continuer à regarder » et « Récemment vus »

## Informations générales

Type:
Feature

Status:
ARCHITECTURE

Created:
2026-07-21

---

# 1. Description

Cette fonctionnalité donne à l'utilisateur le contrôle total sur son historique de visionnage local. Elle permet de :
1. **Retirer manuellement un film ou l'épisode de série affiché** de la section **« Continuer à regarder »** (Reprendre) sur l'écran d'Accueil, l'écran des Films et l'écran des Séries.
2. **Retirer manuellement une chaîne de télévision** de la section **« Récemment vus »** (Chaînes récentes) sur l'écran TV en direct.

La suppression doit s'effectuer de manière intuitive et homogène à l'aide d'un geste universel (appui long / clic long), avec rafraîchissement réactif instantané de l'interface graphique sur mobile et sur Android TV.

---

# 2. Contexte

Actuellement, dès qu'un utilisateur lance la lecture d'un film, d'une série ou d'une chaîne de télévision, cet élément s'inscrit de manière permanente dans l'historique :
- Les films et séries apparaissent dans la ligne **« Continuer à regarder »** (reprise de lecture).
- Les chaînes apparaissent dans la ligne **« Récemment vus »** de l'onglet TV en Direct.

Il n'existe aujourd'hui aucun moyen pour l'utilisateur de nettoyer ces listes de l'historique. Cela pose plusieurs problèmes d'expérience utilisateur :
1. **Encombrement de l'Accueil :** L'écran d'Accueil et les têtes de catégories se retrouvent encombrés d'affiches de médias que l'utilisateur a simplement "testés" pendant quelques minutes sans intention de les poursuivre.
2. **Confidentialité :** Un profil partagé au sein d'un foyer ne peut pas masquer ou effacer un contenu qu'il a visionné.
3. **Erreurs de manipulation :** Un clic accidentel sur un média l'ajoute indéfiniment à sa liste de reprise.

Fournir une option de suppression manuelle de l'historique est une fonctionnalité standard essentielle pour redonner le contrôle aux utilisateurs et garder une interface épurée.

---

# 3. Spécification fonctionnelle

## Objectif

Permettre à un profil local de retirer lui-même un contenu de ses listes de reprise ou de chaînes récemment regardées, avec un comportement uniforme au tactile et au D-pad, persistant après redémarrage et immédiatement visible dans l'interface.

## User stories

- En tant qu'utilisateur, je peux retirer un film que j'ai seulement essayé de ma liste « Continuer à regarder » afin d'alléger mon Accueil.
- En tant qu'utilisateur, je peux indiquer que l'épisode de série proposé dans ma reprise n'est pas vu afin de remettre à zéro uniquement cet épisode, sans perdre la progression des autres épisodes de la série.
- En tant qu'utilisateur TV, je peux retirer une chaîne de « Récemment regardées » afin de garder cette rangée privée et pertinente.
- En tant qu'utilisateur de profil local, je nettoie exclusivement mon historique : les autres profils conservent le leur.
- En tant qu'utilisateur, je dois confirmer la suppression afin de ne pas perdre une reprise par inadvertance.

## Parcours utilisateur

1. L'utilisateur repère une carte dans « Continuer à regarder » sur l'Accueil, l'écran Films ou l'écran Séries, y compris lorsque la rangée est affichée en vue étendue ; ou une carte de « Récemment regardées » sur l'écran TV en direct.
2. Sur mobile, il effectue un appui long sur la carte. Sur Android TV, il maintient le bouton central de validation alors que la carte est focusée. L'appui court conserve son comportement actuel : ouvrir ou reprendre le média/la chaîne.
3. L'appui long ouvre une boîte de dialogue adaptée à la plateforme, nommant le contenu et proposant **Annuler** et **Retirer de la liste**. Le choix par défaut au focus TV est **Annuler**.
4. **Annuler**, un retour système ou la fermeture du dialogue ne modifie aucune donnée et laisse la carte visible.
5. **Retirer de la liste** supprime l'historique ciblé du profil actif. Une fois l'opération réussie, le dialogue se ferme et toutes les représentations visibles se mettent immédiatement à jour ; pour une série, la carte peut afficher un autre épisode encore à reprendre.
6. Si la suppression laisse une liste vide, son titre et son carrousel disparaissent sans laisser d'espace vide. Le focus TV est déplacé vers un élément encore disponible, sans rester sur une carte supprimée.
7. Si l'utilisateur relance ultérieurement le média ou la chaîne, une nouvelle position ou entrée récente peut être créée selon les règles de lecture existantes.

## Règles métier

- La suppression est locale à l'appareil et strictement limitée au profil actif. Elle ne modifie ni le catalogue Xtream, ni l'historique, les favoris ou les reprises d'un autre profil.
- Pour un film, supprimer la carte supprime sa seule position de lecture du profil actif. Le film ne figure plus dans « Continuer à regarder » et une nouvelle lecture recommence sans reprise antérieure.
- Pour une série, la carte agrégée représente l'épisode de reprise affiché. La suppression retire uniquement la position de lecture de cet épisode pour le profil actif ; les positions et jauges de progression des autres épisodes de la série sont conservées.
- Si un autre épisode de la même série possède encore une position de reprise, la carte de la série reste dans « Continuer à regarder » ou se met à jour pour représenter cet autre épisode selon les règles d'agrégation existantes. Elle disparaît uniquement lorsqu'aucun autre épisode de la série n'est à reprendre.
- Pour une chaîne Live TV, la suppression retire uniquement l'entrée « récemment regardée » de cette chaîne pour le profil actif ; elle ne modifie pas les favoris de chaînes, les préférences de catégories ni les données EPG.
- Retirer un contenu de « Continuer à regarder » le retire de l'historique utilisé pour le profil de goûts. Il peut redevenir éligible aux recommandations personnalisées ; un éventuel vote négatif F7 conserve toutefois son exclusion absolue.
- Chaque suppression de film ou d'épisode invalide les recommandations personnelles mises en cache afin qu'elles reflètent le nouvel historique au prochain rendu de l'Accueil.
- La suppression ne supprime jamais un téléchargement hors-ligne, son fichier physique, un favori, un vote J'aime/Je n'aime pas ou une métadonnée de catalogue.
- La fonctionnalité concerne uniquement les cartes présentes dans les listes ciblées ; elle n'ajoute pas de commande de suppression à l'historique global ni aux fiches de détail.

## Critères d'acceptation

- Les cartes de reprise de l'Accueil, Films et Séries, y compris leur vue étendue, ainsi que les cartes « Récemment regardées » de Live TV, prennent en charge l'appui long sur mobile et Android TV.
- Un appui court ne change aucun comportement de navigation ou de lecture existant.
- Le dialogue de confirmation propose toujours Annuler et Retirer de la liste ; Annuler ne provoque aucune écriture locale.
- Après confirmation, le film, l'épisode représenté ou la chaîne disparaît ou est remplacé sans rechargement manuel ni changement d'onglet ; la rangée entière disparaît lorsqu'elle ne contient plus aucun élément.
- Supprimer un film efface uniquement sa reprise du profil actif. Indiquer qu'un épisode de série n'est pas vu efface uniquement la position de cet épisode et réinitialise son indicateur de progression ; les autres épisodes restent inchangés.
- Après redémarrage de l'application, les éléments supprimés restent absents pour le profil concerné ; un autre profil conserve ses propres entrées.
- Les recommandations du profil ne tiennent plus compte de la position de film ou d'épisode supprimée et sont invalidées après confirmation ; les autres épisodes conservés continuent de contribuer à l'historique de la série.
- Aucune suppression n'efface les téléchargements, favoris, votes F7, données EPG ou contenu catalogue.

## Cas limites

- Si l'utilisateur tente un appui long sur une carte qui vient de disparaître à la suite d'une autre action ou d'un changement de profil, aucun dialogue d'action n'est présenté.
- Si plusieurs épisodes d'une même série ont une reprise, une confirmation supprime seulement la position de l'épisode affiché sur la carte agrégée. Après la mise à jour réactive, un autre épisode de la série peut devenir l'épisode représentatif et maintenir la carte visible.
- Une carte déjà supprimée dans une autre vue ne doit pas réapparaître dans une rangée encore affichée ; toutes les vues du profil reflètent la même donnée locale.
- Si un contenu supprimé reste visible brièvement parce qu'une liste est en cours de composition, il ne doit plus être sélectionnable après la confirmation et doit disparaître dès la mise à jour d'état suivante.
- Une lecture ultérieure du même film, épisode ou Live TV recrée normalement l'entrée de reprise/récente, sans restaurer les anciennes positions ou l'ancien ordre.
- Un film retiré ou la série de l'épisode retiré, s'il est marqué `DISLIKE` par F7, reste exclu des recommandations lorsqu'il est relu ; retirer la reprise ne modifie pas ce vote.

## Gestion des erreurs

- La fonctionnalité n'exige aucun accès réseau : l'absence de connexion, une erreur Xtream ou une erreur TMDB ne doivent pas empêcher d'ouvrir le dialogue ni de supprimer une donnée locale.
- Si la suppression locale échoue, le dialogue est fermé, la carte et toutes ses progressions restent inchangées, et un message simple invite l'utilisateur à réessayer. Aucun message technique ni stack trace ne doit être affiché.
- Si l'actualisation des recommandations échoue après une suppression réussie, la suppression reste définitive ; l'Accueil conserve son dernier résultat valide jusqu'à une prochaine actualisation.
- Si le profil actif ou l'identifiant du contenu devient indisponible avant confirmation, l'action est annulée sans modification de données.

---

# 4. Décisions de périmètre

- Le dialogue de confirmation est retenu pour le MVP, plutôt qu'une suppression immédiate avec action « Annuler », afin de rester fiable et accessible au D-pad.
- La suppression d'une carte de série cible uniquement l'épisode représenté par cette carte et ne remet à zéro aucune autre reprise de la série.
- La commande est volontairement limitée aux rangées visées, y compris la vue étendue de « Continuer à regarder » ; aucun écran de gestion d'historique supplémentaire n'est créé.

---

# 5. Notes de spécification

- Changement de périmètre validé le 2026-07-21 : l'action « non vu » sur une série cible désormais uniquement l'épisode affiché. La suppression globale de toutes les reprises d'une série est retirée du périmètre F8.
- La maquette de référence ne définit pas encore de dialogue ou de geste de suppression pour ces cartes. L'étape 3 précisera les composants et l'intégration visuelle en réutilisant les tokens existants de `docs/design-reference/`, sans introduire de charte parallèle.

---

# 6. Spécification technique

## Périmètre de données

F8 réutilise les tables existantes :

- `playback_positions` pour les films et épisodes de séries ;
- `recently_watched_live` pour les chaînes récentes.

Aucune colonne, table ou clé primaire n'est ajoutée. `AppDatabase` reste en version 16 tant que F8 est développé indépendamment ; si F7 est intégré avant F8, F8 conserve naturellement la version 17 introduite par F7. Aucune migration spécifique à F8 n'est requise.

## Modèle de cible

Les ViewModels transmettent les modèles de domaine déjà disponibles :

- `PlaybackPosition` pour un film ou l'épisode représentatif d'une série ;
- `LiveStream` pour une chaîne récente.

Le type du `PlaybackPosition` et son `streamId` permettent au repository d'effectuer une suppression exacte. Le `seriesId` reste utile à l'agrégation réactive existante, mais n'élargit jamais la portée de suppression. Aucun DTO ou modèle Room ne remonte en présentation.

## Repository d'historique

Une interface de domaine dédiée évite de placer les règles de suppression dans les Composables ou dans les repositories de catalogue :

```kotlin
interface ViewingHistoryRepository {
    fun observeRecentlyWatched(limit: Int = 10): Flow<List<LiveStream>>
    suspend fun removeFromContinueWatching(position: PlaybackPosition)
    suspend fun removeRecentlyWatched(streamId: Int)
}
```

`ViewingHistoryRepositoryImpl` injecte `VodDao`, `LiveTvDao` et `ProfileManager`.

- Chaque opération capture une seule fois le `profileId` actif.
- Un film ou un épisode de série déclenche une suppression exacte `(streamId, profileId)`.
- Une chaîne déclenche une suppression exacte `(streamId, profileId)` dans `recently_watched_live`.
- Une suppression déjà effectuée est idempotente : zéro ligne affectée est considérée comme un succès.

`VodDao` reçoit ou réutilise une suppression exacte `deletePlaybackPosition(streamId, profileId)`. Aucune requête groupée par `seriesId` ou liste de `streamId` n'est ajoutée pour F8.

`LiveTvDao` reçoit :

- `observeRecentlyWatched(profileId, limit): Flow<List<RecentlyWatchedLiveEntity>>` ;
- `deleteRecentlyWatched(streamId, profileId)`.

L'observation utilise la même limite de dix chaînes et le même tri `watchedAt DESC` que la lecture ponctuelle actuelle.

## Ciblage exact des épisodes de série

L'épisode affiché sur une carte de série agrégée est déjà porté par une `PlaybackPosition` possédant son propre `streamId`. F8 transmet cette position sans la remplacer par un identifiant de série, puis supprime exactement la clé composite `(streamId, profileId)`.

Aucun rapprochement par `seriesId`, titre ou préfixe textuel n'est effectué. Cette règle vaut aussi pour les anciennes lignes sans `seriesId` : leur `streamId` suffit pour remettre à zéro l'épisode sélectionné sans risque d'effacer les autres épisodes.

F8 partage avec F7 la correction de `SeriesViewModel.savePosition` consistant à enregistrer le `seriesId` actif afin de préserver l'agrégation fiable de « Continuer à regarder ». Si F8 est implémenté en premier, cette correction fait partie de F8 ; si F7 l'a déjà apportée, elle n'est pas dupliquée. Ce champ n'est toutefois jamais utilisé pour étendre une suppression à la série entière.

## Use cases

Trois use cases explicites sont ajoutés :

- `RemoveFromContinueWatchingUseCase(position)` ;
- `RemoveRecentlyWatchedUseCase(streamId)` ;
- `ObserveRecentlyWatchedUseCase()`.

Après une suppression réussie de film ou d'épisode, `RemoveFromContinueWatchingUseCase` appelle `GetRecommendationsUseCase.invalidateCache()`. Le contrat d'invalidation observable défini dans l'architecture F7 est réutilisé : `HomeViewModel` recalcule uniquement les recommandations à partir des positions restantes. Si F8 est intégré avant F7, cette API d'invalidation est introduite sans logique d'évaluation puis réutilisée par F7.

La suppression Live TV n'invalide pas les recommandations, puisque les chaînes n'entrent pas dans le profil de goûts.

## Réactivité

- Les listes « Continuer à regarder » de `HomeViewModel`, `VodViewModel` et `SeriesViewModel` collectent déjà `VodRepository.observeAllPlaybackPositions()`. Toute suppression Room les met donc à jour sans rechargement manuel.
- `LiveTvViewModel` remplace le chargement ponctuel des chaînes récentes par la collecte de `ObserveRecentlyWatchedUseCase`. L'appel de sauvegarde existant n'a plus besoin de rappeler `loadRecentlyWatched()` après chaque insertion.
- Les sections sont déjà conditionnées par `isNotEmpty()` ; elles disparaissent naturellement au passage à une liste vide.
- La vue étendue « Continuer à regarder » de la Home se ferme automatiquement si sa liste devient vide, plutôt que d'afficher une grille vide.

## État des écrans

`HomeState`, `VodState`, `SeriesState` et `LiveTvState` reçoivent :

```kotlin
val isRemovingHistory: Boolean = false
val historyRemovalError: String? = null
```

Chaque ViewModel expose une méthode de suppression et une méthode de consommation d'erreur. La cible en attente de confirmation reste un état UI local à l'écran (`rememberSaveable` n'est pas requis pour une boîte de dialogue éphémère) :

- `PlaybackPosition?` dans Home, Films et Séries ;
- `LiveStream?` dans Live TV.

À la confirmation, l'écran conserve la cible jusqu'à la fin de l'opération :

- succès observé dans la liste → fermeture du dialogue ;
- échec → fermeture, élément conservé et Snackbar non technique ;
- changement de profil ou disparition préalable de la cible → fermeture sans seconde suppression.

Les boutons du dialogue sont désactivés pendant `isRemovingHistory` afin d'éviter une double validation.

## Geste partagé mobile/TV

Un helper de présentation `historyItemActions` centralise les interactions sans changer l'action courte :

```kotlin
fun Modifier.historyItemActions(
    isTv: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
): Modifier
```

- Mobile : `combinedClickable` fournit clic, appui long et sémantique d'accessibilité.
- Android TV : le clic normal reste porté par `clickable`; un `onPreviewKeyEvent` intercepte uniquement Entrée/D-pad centre lorsque `nativeKeyEvent.isLongPress` ou `repeatCount > 0`.
- Après déclenchement long, les répétitions et le `KeyUp` correspondant sont consommés pour empêcher le clic court de lancer le contenu.
- Un appui centre relâché sans répétition traverse vers `clickable` et conserve la lecture existante.
- La sémantique expose « Retirer de la liste » comme action longue pour les services d'accessibilité.

Le helper mémorise localement si le long press a été consommé. Aucun timer ou coroutine n'est créé par carte : Android fournit le seuil et la répétition de touche.

## Cartes impactées

Les callbacks `onLongClick` sont optionnels sur les cartes réutilisées hors historique. Ils ne sont fournis que dans les sections ciblées :

- `HomeHeroCard` lorsque le hero est une reprise ;
- `HomeResumeWatchingCard` dans la rangée et la vue étendue Home ;
- `HomeVodMovieCard` / `MovieTvCard` dans la seule rangée `resume_watching` de Films ;
- `HomeSeriesShowCard` / `SeriesTvCard` dans la seule rangée `resume_watching` de Séries ;
- `MobileRecentlyWatchedItem` / `RecentlyWatchedTvItem` dans Live TV.

`CategorySectionRow` de Films et Séries reçoit un callback long optionnel et le transmet aux cartes uniquement pour la rangée de reprise. Les cartes de Nouveautés, catégories, Favoris, recommandations et recherche restent inchangées.

Toutes les listes concernées utilisent une clé stable (`streamId` pour film/live, `seriesId ?: streamId` pour série) afin de limiter les recompositions et d'aider Compose à restaurer le focus vers un voisin après suppression.

## Dialogue partagé

`HistoryRemovalDialog` est un composable stateless commun aux quatre écrans :

```kotlin
@Composable
fun HistoryRemovalDialog(
    title: String,
    isTv: Boolean,
    isRemoving: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
)
```

- `Dialog` personnalisé sur `Surface2`, rayon 16.dp, bordure blanche faible et largeur maximale 360.dp mobile / 480.dp TV.
- Titre « Retirer de la liste ? », nom du contenu, texte expliquant que la progression ou l'entrée récente sera effacée.
- Actions « Annuler » et « Retirer de la liste » ; aucune action Undo.
- Mobile : boutons Material 3 ; TV : boutons Compose for TV avec `FocusRequester` initial sur Annuler.
- Retour système, clic extérieur et Annuler appellent `onDismiss` uniquement hors suppression en cours.
- Un indicateur compact remplace le contenu du bouton de confirmation pendant l'écriture.

Les couleurs `Surface2`, `TextPrimary`, `TextSecondary` et `AccentLavande`, ainsi que les rayons existants, proviennent de la référence design. Aucun token ou asset nouveau n'est introduit.

Un `SnackbarHost` local à chaque écran consomme `historyRemovalError`. La chaîne affichée est générique et ne contient jamais l'exception brute.

## Navigation et plateformes

`AppNavGraph` étant aujourd'hui unifié pour mobile et Android TV, aucun nouvel écran ni route n'est ajouté. Les écrans reçoivent déjà leur ViewModel ; les callbacks de suppression restent internes à `HomeScreen`, `VodScreen`, `SeriesScreen` et `LiveTvScreen`. `MainActivity` n'est pas modifié.

## Dépendances et sécurité

- Aucune nouvelle dépendance Gradle ; Foundation, Compose Material 3, Compose for TV, Room KTX et Hilt sont déjà présents.
- Aucun appel réseau, permission ou règle ProGuard supplémentaire.
- Aucun fichier téléchargé, favori, vote F7, donnée EPG ou métadonnée catalogue n'est touché.
- Le `profileId` n'est jamais fourni par la présentation : il est capturé par le repository depuis `ProfileManager` au moment de l'opération.

---

# 7. Architecture

## Flux de suppression VOD/Série

```text
carte -- appui long --> HistoryRemovalDialog
                              │ confirmer
                              ▼
                  ViewModel de l'écran
                              │
                              ▼
          RemoveFromContinueWatchingUseCase
                              │
                              ▼
             ViewingHistoryRepositoryImpl
                 └── VodDao : DELETE streamId exact
                              │
             Flow playback_positions réémis
                 ├── HomeViewModel
                 ├── VodViewModel
                 └── SeriesViewModel
                              │
                              ▼
                    disparition des cartes
```

Après la suppression, le use case invalide les recommandations. Cette invalidation est secondaire : son échec ne restaure pas l'historique supprimé.

## Flux de suppression Live TV

```text
carte récente -- appui long --> dialogue --> LiveTvViewModel
                                                │
                                                ▼
                              RemoveRecentlyWatchedUseCase
                                                │
                                                ▼
                                    LiveTvDao.delete exact
                                                │
                                  Flow recently_watched_live
                                                │
                                                ▼
                                    LiveTvState.recentlyWatched
```

## Responsabilités

- `ViewingHistoryRepositoryImpl` : scoping profil, suppression exacte film/épisode et mapping des chaînes récentes.
- Use cases : intention de suppression et invalidation éventuelle des recommandations.
- ViewModels : état de progression/erreur et appel des use cases.
- Écrans : cible temporaire, dialogue et Snackbar.
- Cartes/helper : distinction clic court/appui long et sémantique.

## Fichiers nouveaux

- `domain/repository/ViewingHistoryRepository.kt`
- `domain/usecase/RemoveFromContinueWatchingUseCase.kt`
- `domain/usecase/RemoveRecentlyWatchedUseCase.kt`
- `domain/usecase/ObserveRecentlyWatchedUseCase.kt`
- `data/repository/ViewingHistoryRepositoryImpl.kt`
- `presentation/components/HistoryItemActions.kt`
- `presentation/components/HistoryRemovalDialog.kt`
- tests unitaires correspondants sous `app/src/test/`.

## Fichiers modifiés

- `data/local/dao/VodDao.kt` : suppression exacte d'une position par épisode ou film.
- `data/local/dao/LiveTvDao.kt` : observation réactive et suppression ciblée.
- `di/AppModule.kt` : provider du repository d'historique.
- `domain/usecase/GetRecentlyWatchedUseCase.kt` : remplacé dans `LiveTvViewModel` par l'observation réactive ; conservé seulement s'il reste un consommateur.
- `presentation/home/HomeState` dans `HomeViewModel.kt`, `HomeViewModel.kt`, `HomeScreen.kt` et `home/components/HomeCards.kt`.
- `presentation/vod/VodState.kt`, `VodViewModel.kt`, `VodScreen.kt`.
- `presentation/series/SeriesState.kt`, `SeriesViewModel.kt`, `SeriesScreen.kt`.
- `presentation/livetv/LiveTvState.kt`, `LiveTvViewModel.kt`, `LiveTvScreen.kt` et `livetv/components/LiveTvComponents.kt`.
- `domain/repository/LiveTvRepository.kt` et `data/repository/LiveTvRepositoryImpl.kt` uniquement pour retirer le rechargement ponctuel devenu inutile, sans modifier la sauvegarde des chaînes récentes.
- `presentation/home/components/HomeCards.kt` : callbacks longs des cartes Home partagées.
- `app/src/main/res/values/strings.xml` : dialogue, actions, descriptions d'accessibilité et erreur.
- `ai/features/F8-remove-continue-watching-recently-viewed.md` : suivi du cycle de vie.

## Décisions techniques justifiées

- **Repository dédié** : les suppressions traversent VOD, séries et Live sans mélanger cette règle utilisateur aux repositories de catalogue.
- **Ciblage exact d'épisode** : le `streamId` de la position représentative empêche toute remise à zéro involontaire des autres épisodes, y compris pour l'historique legacy sans `seriesId`.
- **Flow Room pour les récents** : aligne Live TV sur les reprises déjà réactives et supprime les refresh manuels.
- **Cible de dialogue locale** : état purement visuel, tandis que l'écriture et les erreurs restent dans le ViewModel.
- **Gestion explicite de la touche TV** : garantit qu'un long centre ne déclenche pas ensuite la lecture.
- **Callbacks longs optionnels** : aucune nouvelle action ne fuite vers les cartes hors historique.

---

# 8. Validation prévue

## Tests unitaires

- `ViewingHistoryRepositoryImplTest` : profil capturé une fois, suppression exacte film/épisode/live, conservation des autres épisodes de la même série, ligne legacy sans `seriesId`, cible absente idempotente et erreur DAO.
- `RemoveFromContinueWatchingUseCaseTest` : invalidation après succès film/épisode, aucune invalidation après échec.
- `RemoveRecentlyWatchedUseCaseTest` : aucune invalidation de recommandation.
- `ObserveRecentlyWatchedUseCaseTest` : ordre, limite, mise à jour après suppression et changement de profil via Flow fake.
- `HomeViewModelTest`, `VodViewModelTest`, `SeriesViewModelTest`, `LiveTvViewModelTest` : état loading, succès, erreur, consommation du message et listes réémises.
- Non-régression `SeriesViewModelTest` : `seriesId` enregistré pour les nouvelles positions.

Les gestes Compose et le focus D-pad sont du layout/interactif pur. Le projet n'ayant pas d'infrastructure UI instrumentée, ils sont validés manuellement plutôt que par un test unitaire artificiel.

## Vérifications automatisées finales

- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`
- `./gradlew lintDebug`

## Scénarios manuels obligatoires

1. Mobile et TV : clic court sur chaque type de carte conserve la lecture/navigation actuelle.
2. Mobile : appui long sur hero Home, rangée Home, vue étendue, Reprendre Films/Séries et récente Live ouvre le bon dialogue.
3. TV : maintien D-pad centre ouvre une seule fois le dialogue et ne lance jamais le média au relâchement.
4. Annuler, retour et clic extérieur ne modifient aucune donnée ; Annuler reçoit le focus initial sur TV.
5. Film : disparition simultanée de Home et Films, sans toucher favori/téléchargement/vote.
6. Série multi-épisodes avec et sans `seriesId` historique : remise à zéro du seul épisode affiché ; les autres positions restent intactes et la carte se met à jour vers un autre épisode encore reprenable, ou disparaît s'il n'en reste aucun.
7. Live : disparition immédiate de la chaîne récente, sans toucher son favori ni l'EPG.
8. Dernier élément : disparition de la section ; vue étendue Home fermée ; focus TV replacé sur un élément valide.
9. Changement de profil : historique indépendant et dialogue obsolète fermé.
10. Hors ligne et après redémarrage : suppression toujours effective et persistante.
11. Erreur Room simulée : dialogue fermé, carte conservée, message générique, aucune stack trace.

## Risques et atténuations

- **Long press TV suivi d'un clic court** : mémoriser la consommation et intercepter le `KeyUp` associé.
- **Suppression trop large d'une série** : ne jamais supprimer par `seriesId`, titre ou liste d'épisodes ; utiliser uniquement `(streamId, profileId)`.
- **Profil changé pendant le dialogue** : fermer la cible lorsque le Flow de liste/profil change avant confirmation ; repository capture toujours le profil courant une seule fois.
- **Perte de focus après retrait** : clés stables, dialogue conservé jusqu'à la réémission Room et tests premier/milieu/dernier/unique.
- **Double confirmation** : désactivation des actions pendant l'écriture et repository idempotent.
- **Recommandations encore en cache** : réutiliser l'invalidation mutex + événement définie par F7.
- **Régression des cartes partagées** : callback long nullable et fourni uniquement par les rangées `resume_watching`.
- **Série créée sans `seriesId`** : corriger la sauvegarde future et maintenir le fallback legacy.

## Contraintes de performance

- Les suppressions film/live sont indexées par leurs clés primaires composites.
- La suppression d'un épisode exécute un unique `DELETE` indexé sur la clé composite `(streamId, profileId)`, sans parcourir l'historique ni le catalogue.
- Aucun scan du catalogue, appel réseau, chargement d'image ou worker.
- Un seul collecteur de chaînes récentes dans `LiveTvViewModel`, en remplacement des lectures ponctuelles répétées.
- Aucun timer de long press par carte ; les événements natifs de touche TV sont utilisés.
