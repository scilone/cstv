# B13 - Boucle de retour arrière infinie sur les fiches de détails lors de la navigation dans les titres associés

## Informations générales

Type:
Bug

Status:
RELEASED

Created:
2026-07-25

Target version:
v1.54.20

Version:
v1.54.20

Date:
2026-07-25

---

# 1. Description

Sur l'écran de détails d'un film (VOD) ou d'une série, l'utilisateur a la possibilité de cliquer sur un élément de la ligne "Titres associés" (RelatedTitlesRow) pour consulter sa fiche détaillée.

Cependant, s'il tente ensuite d'utiliser le bouton de retour arrière (système ou bouton de l'UI) pour revenir à la fiche du média d'origine, l'écran reste bloqué sur la fiche du titre associé. L'utilisateur est pris au piège dans une boucle et ne peut jamais revenir à la fiche du média précédent dans l'historique de navigation.

---

# 2. Contexte

L'application utilise Jetpack Compose Navigation pour gérer la navigation sur mobile et Android TV via `AppNavGraph` (dans `NavGraph.kt`).

Les états représentant le film sélectionné (`activeVodMovie`) et la série sélectionnée (`activeSeriesShow`) sont déclarés globalement dans `MainActivity.kt` au niveau de l'activité, puis passés en paramètres à `AppNavGraph`.

Dans `NavGraph.kt`, les destinations `"vod_details"` et `"series_details"` sont déclarées ainsi :

```kotlin
composable("vod_details") {
    val vodViewModel: VodViewModel = hiltViewModel()
    val state by vodViewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(activeVodMovie) {
        activeVodMovie?.let {
            vodViewModel.selectStream(it)
        }
    }
    // ...
}
```

Lorsqu'un utilisateur clique sur un titre associé, l'application effectue l'action suivante :
1. Elle met à jour la variable globale `activeVodMovie` (ou `activeSeriesShow`) avec le nouveau flux sélectionné via `onActiveVodMovieChanged(stream)`.
2. Elle appelle `navController.navigate("vod_details")` pour pousser une nouvelle instance de l'écran de détails sur la pile de navigation.

### Le Problème :
Jetpack Compose Navigation empile correctement une nouvelle destination `"vod_details"` sur la pile de retour (backstack).
Cependant, les deux instances de `"vod_details"` présentes dans la backstack observent et réagissent au **même** état global `activeVodMovie` déclaré dans l'activité.

Lorsque l'utilisateur clique sur Retour :
1. La destination du haut (Titre Associé) est correctement dépilée et détruite.
2. La destination précédente (Titre Original) redevient active.
3. Cependant, la variable globale `activeVodMovie` dans `MainActivity` est toujours positionnée sur le Titre Associé.
4. L'effet de lancement `LaunchedEffect(activeVodMovie)` de l'écran restauré se déclenche à nouveau car l'état a changé par rapport au début de son cycle de vie, ou se ré-exécute avec la valeur actuelle (`activeVodMovie` = Titre Associé).
5. Le ViewModel de l'écran d'origine recharge alors les détails du Titre Associé.
6. L'utilisateur a l'impression d'être resté sur la même fiche de titre associé, alors que la pile de navigation a bien été dépilée.

Le même problème se produit de manière identique sur l'écran des détails de séries (`"series_details"`).

---

# 3. Spécification fonctionnelle

## User stories

- En tant qu'utilisateur consultant la fiche d'un film, je veux ouvrir un titre associé puis revenir exactement au film précédemment consulté afin de poursuivre ma navigation sans perdre mon contexte.
- En tant qu'utilisateur consultant la fiche d'une série, je veux obtenir le même comportement de retour entre les séries associées.
- En tant qu'utilisateur mobile ou Android TV, je veux que le bouton Retour disponible sur ma plateforme respecte l'historique réel des fiches consultées.

## Parcours utilisateur

1. Depuis un écran d'origine (Accueil, recherche, favoris ou catalogue), l'utilisateur ouvre la fiche du média A.
2. Il sélectionne le média B dans la ligne « Titres associés » de cette fiche.
3. La fiche de B est affichée et A reste l'élément précédent de l'historique de navigation.
4. L'utilisateur utilise le retour système ou le contrôle Retour proposé par l'interface.
5. La fiche de A est réaffichée avec son contenu propre : titre, visuel, description, métadonnées, casting et titres associés de A.
6. Un second retour quitte la fiche de A et ramène à l'écran d'origine, conformément au comportement déjà attendu de cet écran.

Le même parcours doit fonctionner pour les films (VOD) et les séries.

## Règles métier

- Chaque ouverture depuis « Titres associés » crée une étape distincte dans l'historique des fiches ; elle ne remplace pas la fiche depuis laquelle le titre a été ouvert.
- Le retour doit restaurer le média correspondant à l'étape dépilée, sans conserver ni réafficher les données du dernier titre associé consulté.
- La règle est identique quelle que soit la source de la première fiche : Accueil, recherche, favoris, catalogue ou autre écran déjà autorisé à ouvrir une fiche de détail.
- La règle s'applique aux deux types de médias concernés, VOD et séries, sans modifier le comportement de lecture ni la navigation vers les écrans de lecteur.
- Aucun état visuel transitoire ne doit laisser croire que le retour a échoué : après le retour, le titre affiché et le contenu de la fiche doivent désigner le média précédent.

## Critères d'acceptation

- Étant donné la fiche VOD A, lorsqu'un utilisateur ouvre le titre associé B puis revient en arrière, alors la fiche VOD A est affichée avec les informations de A, et non celles de B.
- Étant donné la fiche série A, lorsqu'un utilisateur ouvre le titre associé B puis revient en arrière, alors la fiche série A est affichée avec les informations de A, et non celles de B.
- Étant donné le parcours A → B depuis une fiche ouverte à partir de l'Accueil, lorsqu'un utilisateur effectue deux retours successifs, alors il voit A après le premier retour puis l'Accueil après le second.
- Étant donné le parcours A → B → C dans les titres associés, lorsqu'un utilisateur effectue des retours successifs, alors il voit B, puis A, puis l'écran d'origine, dans cet ordre.
- Le résultat est identique avec le bouton Retour système et avec tout bouton Retour fourni par l'interface sur la plateforme concernée.
- Le correctif ne crée pas de boucle de retour, de doublon de fiche ni de nouvelle navigation automatique pendant le dépilage.
- L'ouverture d'une fiche associée et la navigation de détail existantes restent utilisables sur mobile et Android TV.

## Cas limites et gestion des erreurs

- Si un média associé ne peut pas charger ses détails (réseau indisponible, réponse incomplète ou média devenu indisponible), l'erreur existante est affichée pour ce média uniquement ; le retour reste capable de restaurer la fiche précédente.
- Si l'utilisateur revient avant la fin du chargement du média associé, il doit retrouver la fiche précédente plutôt qu'un chargement ou une erreur appartenant au média associé.
- Si un titre associé est ouvert plusieurs fois dans une même chaîne de navigation, chaque retour restaure l'occurrence immédiatement précédente dans cette chaîne.
- Si la ligne « Titres associés » est absente ou vide, le comportement existant de la fiche est inchangé.
- Le correctif ne doit pas modifier les données de favoris, d'historique, de reprise de lecture ou de catalogue.

---

# 4. Spécification technique

## 4.1 Révision du diagnostic (hypothèse initiale invalidée)

Le diagnostic de cause racine du §2 est confirmé : les deux entrées `"vod_details"` de la backstack lisent le même état hissé `activeVodMovie` (`MainActivity.kt:107`), transmis en paramètre à `AppNavGraph` (`NavGraph.kt:78`) et utilisé comme clé de `LaunchedEffect` (`NavGraph.kt:401`). Au retour, l'écran A recompose avec `activeVodMovie` = B et recharge B.

En revanche, la correction proposée (`remember { activeVodMovie }`) **ne corrige pas le bug** et doit être écartée :

- `NavHost` ne compose que la (ou les, pendant une transition) destination(s) visible(s). Quand l'utilisateur empile le titre associé B, l'entrée A **quitte la composition** : tous ses `remember` sont détruits.
- Au dépilage, l'entrée A **ré-entre en composition** : le bloc `remember { activeVodMovie }` est ré-évalué et relit l'état hissé, qui vaut toujours B. La valeur « figée » est donc refigée sur B → symptôme inchangé.
- Seul l'état `rememberSaveable` survit à la sortie de composition : `NavHost` enveloppe chaque destination dans `SaveableStateProvider(entry.id)` d'un `rememberSaveableStateHolder()`, qui sérialise le registre saveable de l'entrée à sa disparition et le restaure à son retour (c'est le mécanisme qui restaure déjà le scroll des grilles Films/Séries).

Le point 7 de l'hypothèse initiale reste valide et est conservé : au retour sur A, `onActiveVodDetailsChanged(details)` est ré-invoqué avec les détails de A (`NavGraph.kt:413`), ce qui repositionne correctement `activeVodDetails` pour le lecteur.

Correction retenue : **capture de l'identifiant du média par entrée de backstack via `rememberSaveable`**, complétée par une garde d'idempotence dans les ViewModels.

## 4.2 Composants impactés

| Fichier | Nature de la modification |
|---|---|
| `presentation/navigation/NavGraph.kt:397-405` | `composable("vod_details")` : capture `rememberSaveable { activeVodMovie?.streamId ?: -1 }` ; `LaunchedEffect` clé sur cet identifiant ; appel `vodViewModel.selectStreamId(id)` ; dépilage défensif si identifiant absent |
| `presentation/navigation/NavGraph.kt:469-477` | `composable("series_details")` : idem avec `activeSeriesShow?.seriesId` et `seriesViewModel.selectStreamId(id)` |
| `presentation/vod/VodViewModel.kt:243-254` | Nouvelle `selectStreamId(streamId: Int?)` portant la logique ; `selectStream(stream)` devient un délégué ; garde d'idempotence en entrée |
| `presentation/series/SeriesViewModel.kt:252-263` | Idem côté séries |
| `presentation/vod/VodState.kt:13` | `selectedStream: VodStream?` → `selectedStreamId: Int?` (le champ n'est lu que pour comparer l'identifiant, `VodViewModel.kt:249`) |
| `presentation/series/SeriesState.kt:13` | `selectedStream: SeriesStream?` → `selectedStreamId: Int?` (lu uniquement en `SeriesViewModel.kt:258`) |
| `test/.../presentation/vod/VodViewModelTest.kt` | Tests de la garde d'idempotence et de `selectStreamId` |
| `test/.../presentation/series/SeriesViewModelTest.kt` | Idem côté séries |

Périmètre inchangé par ailleurs : aucune nouvelle dépendance, aucun changement de route, de graphe, de schéma Room, de DI, de règle ProGuard, ni de comportement de lecture. `MainActivity.kt` n'est pas modifié : les états `activeVodMovie` / `activeSeriesShow` restent la valeur d'amorçage lue à la première composition de chaque fiche.

## 4.3 Nouveaux composants

Aucun composant nouveau. La correction est locale à deux destinations du graphe et à deux ViewModels existants.

Forme cible de la destination VOD (identique côté séries) :

```kotlin
composable("vod_details") {
    val vodViewModel: VodViewModel = hiltViewModel()
    val state by vodViewModel.state.collectAsStateWithLifecycle()

    // B13 : l'identifiant du film est figé à la PREMIÈRE composition de CETTE entrée
    // de backstack. rememberSaveable et non remember : NavHost retire l'écran de la
    // composition dès qu'un titre associé est empilé, seul l'état saveable de
    // l'entrée est restauré au retour. Un remember relirait activeVodMovie, qui
    // pointe alors sur le titre associé → boucle de retour.
    val entryStreamId = rememberSaveable { activeVodMovie?.streamId ?: NO_STREAM_ID }

    LaunchedEffect(entryStreamId) {
        if (entryStreamId != NO_STREAM_ID) {
            vodViewModel.selectStreamId(entryStreamId)
        } else {
            navController.popBackStack()
        }
    }
    // ... reste inchangé
}
```

Garde d'idempotence côté ViewModel :

```kotlin
fun selectStream(stream: VodStream?) = selectStreamId(stream?.streamId)

fun selectStreamId(streamId: Int?) {
    // Le LaunchedEffect se redéclenche à chaque retour sur la fiche (nouvelle
    // composition). Le ViewModel, lui, survit dans le scope de la NavBackStackEntry :
    // si le média demandé est déjà chargé ou en cours de chargement, ne rien refaire.
    val current = _state.value
    if (streamId != null && current.selectedStreamId == streamId &&
        (current.isLoadingDetails || current.selectedVodDetails != null)
    ) return

    ratingObservation?.cancel()
    _state.update {
        it.copy(
            selectedStreamId = streamId,
            selectedVodDetails = null,
            relatedStreams = emptyList(),
            mediaRating = null,
            ratingError = null
        )
    }
    if (streamId != null) {
        ratingObservation = viewModelScope.launch { /* observeRating(streamId, MOVIE) */ }
        loadVodDetails(streamId)
    }
}
```

La garde n'inclut volontairement pas le cas `error != null` : une fiche en erreur reste rechargeable au retour (cas limite « média associé indisponible » du §3).

## 4.4 Choix techniques et justifications

- **`rememberSaveable` plutôt que `remember`.** Seule forme de mémorisation restaurée quand une entrée de backstack ré-entre en composition (§4.1). Bénéfice supplémentaire : après mort du processus, `activeVodMovie` (simple `remember` d'activité, non saveable) est perdu alors que l'identifiant capté est restauré — la fiche se recharge au lieu d'afficher un indicateur de chargement infini, comportement aujourd'hui cassé.
- **Capturer un `Int` et non le flux complet.** `VodStream` / `SeriesStream` ne sont ni `Parcelable` ni `Serializable` ; les rendre saveables imposerait le plugin `kotlin-parcelize` ou une contamination du modèle `domain` pour un besoin de navigation. L'analyse des ViewModels montre que seul l'identifiant est utilisé : `selectStream` ne lit que `stream.streamId` / `stream.seriesId` (`VodViewModel.kt:248-252`, `SeriesViewModel.kt:257-261`), le reste de la fiche provenant de `getVodDetailsUseCase` / `getSeriesDetailsUseCase`. Certains appelants passent d'ailleurs déjà un flux fictif réduit à l'identifiant (`NavGraph.kt:305`, `NavGraph.kt:309`, depuis les Favoris).
- **`selectedStream` remplacé par `selectedStreamId` dans les états.** Le champ n'est consommé nulle part dans l'UI (vérifié sur `app/src/main` et `app/src/test`) : il ne sert qu'à comparer un identifiant lors de la collecte de la note. Le conserver obligerait à reconstruire un `VodStream` fictif à partir du seul identifiant.
- **Garde d'idempotence dans le ViewModel plutôt que dans le Composable.** Au retour sur la fiche A, `LaunchedEffect(entryStreamId)` se relance nécessairement (nouvelle composition). Sans garde, A serait rechargé à chaque retour : indicateur de chargement clignotant (interdit par le critère « aucun état visuel transitoire ne doit laisser croire que le retour a échoué ») et appel réseau inutile. Placer la garde dans le ViewModel la rend testable en JUnit, seule couverture automatisée possible ici (pas d'infrastructure `androidTest`, cf. AGENTS.md).
- **Pas de routes paramétrées (`vod_details/{id}`).** Ce serait le correctif structurel : l'argument appartient à l'entrée, il est immuable et l'état hissé disparaîtrait. Il impose en revanche de modifier les 10 appels de navigation vers les fiches, la sélection d'onglet de la barre mobile (`MainActivity.kt:211-212`, comparaison par égalité stricte de route), le mappage `DETAIL_ROUTE_TO_TAB` introduit par B11 (non encore livré) et les listes de routes de `SystemBarsControllerTest`. Décision cohérente avec B11 §8.3 qui écarte déjà les routes paramétrées du périmètre d'un correctif de bug. À reverser au backlog technique (`ai/technical/`) comme unification « navigation par arguments » plutôt que de le traiter en correctif patch.
- **États hissés conservés.** `activeVodMovie` / `activeSeriesShow` restent la valeur d'amorçage écrite par les écrans appelants avant navigation. Les mettre à `null` après capture ferait recomposer la fiche encore visible pendant la transition et casserait l'amorçage des transitions de sortie.

## 4.5 Réponses aux questions ouvertes (§ précédent)

- **D'autres écrans ou flux dépendent-ils de la valeur instantanée d'`activeVodMovie` / `activeSeriesShow` ?**
  *Non.* Analyse statique exhaustive : ces deux états ne sont lus que dans `NavGraph.kt:401` et `NavGraph.kt:473`, et écrits que dans les 10 rappels de navigation vers les fiches (`NavGraph.kt:215, 219, 253, 266, 282, 286, 305, 309, 447, 514`). Les écrans de lecture s'appuient sur `activeVodDetails` / `activeSeriesDetails` / `activeEpisode`, alimentés indépendamment. La correction n'a donc pas d'effet de bord hors des deux fiches de détail.
- **Les lecteurs restent-ils cohérents après un retour ?**
  *Oui.* `onActiveVodDetailsChanged(details)` / `onActiveSeriesDetailsChanged(details)` sont invoqués pendant la composition de la fiche restaurée (`NavGraph.kt:413`, `NavGraph.kt:485`), donc réécrits avec les détails de A avant toute action de lecture.

## 4.6 Risques, vérification et non-régression

- **Risque : entrée sans identifiant (`NO_STREAM_ID`).** Survient si la fiche est atteinte sans amorçage (`activeVodMovie == null`) — aujourd'hui l'écran affiche un indicateur de chargement infini. Le dépilage défensif (`popBackStack()`) est un changement de comportement volontaire et strictement meilleur ; il ne peut pas boucler puisque l'écran d'origine n'est pas une fiche.
- **Risque : divergence entre l'amorçage et l'identifiant capté.** Si un futur écran écrit `activeVodMovie` **après** la première composition de la fiche, la mise à jour serait ignorée. Comportement voulu (c'est la correction), à documenter par le commentaire du code pour éviter une régression de compréhension.
- **Risque : régression de la note (like/dislike).** Le passage de `selectedStream` à `selectedStreamId` touche la condition de garde de `observeRating`. Couvrir par test unitaire : une note émise pour un ancien média ne doit pas écraser l'état du média courant.
- **Vérification runtime obligatoire avant clôture** (mobile **et** Android TV, les deux plateformes partageant `AppNavGraph`) : parcours A → B → retour ; A → B → C → retours successifs ; retour pendant le chargement de B ; retour après erreur de chargement de B ; ouverture de la fiche depuis Accueil, Recherche, Favoris, Films/Séries et « Ajouts récents » ; lecture après retour (le lecteur doit démarrer A).
- **Non-régression** : `./gradlew assembleDebug lintDebug testDebugUnitTest`. Suites existantes exposées au changement de signature : `VodViewModelTest`, `SeriesViewModelTest`.
- **Performance** : gain net. La garde d'idempotence supprime un rechargement complet de fiche (détails + titres associés) à chaque retour arrière.

---

# 5. Architecture

## Flux de données (avant / après)

```
AVANT
MainActivity : var activeVodMovie                    (état hissé unique, partagé)
   │  écrit par onSelectRelated(B)
   ▼
NavGraph composable("vod_details")  ── entrée A ──┐
   LaunchedEffect(activeVodMovie)                 │  même source lue par
NavGraph composable("vod_details")  ── entrée B ──┘  les deux entrées
   LaunchedEffect(activeVodMovie)

   pop entrée B → entrée A ré-entre en composition
   → LaunchedEffect(activeVodMovie = B) → selectStream(B) → fiche B réaffichée (boucle)

APRÈS
MainActivity : var activeVodMovie                    (valeur d'AMORÇAGE uniquement)
   │  lue une seule fois par entrée, à sa première composition
   ▼
NavGraph composable("vod_details")  ── entrée A : rememberSaveable → entryStreamId = A
   LaunchedEffect(A) → vodViewModel(A).selectStreamId(A)
NavGraph composable("vod_details")  ── entrée B : rememberSaveable → entryStreamId = B
   LaunchedEffect(B) → vodViewModel(B).selectStreamId(B)

   pop entrée B → entrée A ré-entre en composition
   → SaveableStateProvider(entry.id) restaure entryStreamId = A
   → LaunchedEffect(A) → selectStreamId(A) → garde : A déjà chargé → no-op
   → fiche A réaffichée instantanément, sans rechargement ni clignotement
   → onActiveVodDetailsChanged(A) repositionne l'état de lecture sur A
```

## Responsabilités

| Composant | Responsabilité |
|---|---|
| `MainActivity` (`activeVodMovie` / `activeSeriesShow`) | Transporter le média sélectionné de l'écran appelant vers la **prochaine** fiche ouverte. N'est plus une source de vérité pendant la vie d'une fiche. |
| `composable("vod_details")` / `composable("series_details")` | Figer, à la première composition de l'entrée de backstack, l'identifiant du média dont cette entrée est responsable, et le restaurer au dépilage. |
| `VodViewModel` / `SeriesViewModel` (scopés à la `NavBackStackEntry`) | Détenir l'état d'une seule fiche pour toute la durée de vie de son entrée ; ignorer toute demande de sélection redondante. |
| `NavBackStackEntry` + `SaveableStateHolder` | Support de persistance de l'identifiant capté : survit à la sortie de composition et à la mort du processus. |

## Décisions techniques

1. **L'entrée de backstack devient propriétaire de son média.** Le correctif déplace la source de vérité de « un état d'activité partagé » vers « un identifiant par entrée ». C'est ce déplacement, et non la seule capture, qui supprime la classe de bugs.
2. **`rememberSaveable` est un choix contraint, pas cosmétique.** `remember` échoue par construction (§4.1) ; toute relecture de code doit conserver le commentaire justificatif sous peine de régression silencieuse.
3. **Idempotence côté ViewModel, pas côté UI.** Le seul point capable de savoir qu'un média est déjà chargé est le ViewModel de l'entrée ; c'est aussi le seul point testable automatiquement sur ce projet.
4. **Routes paramétrées renvoyées au backlog technique.** Correctif structurel reconnu supérieur mais hors périmètre d'un patch de bug, et en collision avec B11 non encore livré (§4.4). À créer comme tâche `Tx` d'unification de la navigation par arguments.
5. **Aucune modification du double système de navigation.** Mobile et Android TV partagent aujourd'hui `AppNavGraph` : la correction couvre les deux plateformes sans traitement spécifique.

---

# 6. Plan de développement

- [x] **Tâche 1 — Rendre l'état de détail VOD piloté par identifiant et idempotent**

  Objectif :
  Remplacer la conservation du `VodStream` sélectionné par son identifiant, exposer `selectStreamId`, et empêcher un rechargement d'une fiche déjà chargée ou en cours de chargement pour la même entrée de navigation.

  Fichiers :
  - `app/src/main/java/com/cstv/app/presentation/vod/VodState.kt`
  - `app/src/main/java/com/cstv/app/presentation/vod/VodViewModel.kt`

  Validation :
  - `selectStream(VodStream?)` reste compatible avec les appelants existants en déléguant à `selectStreamId`.
  - Une seconde sélection du même identifiant, avec détails présents ou chargement en cours, ne réinitialise pas l'état et ne déclenche pas de nouvel appel de détails.
  - Une erreur de chargement du même média demeure rechargeable et une émission de note d'un ancien média ne peut pas modifier la fiche courante.

- [x] **Tâche 2 — Rendre l'état de détail Série piloté par identifiant et idempotent**

  Objectif :
  Appliquer strictement le même contrat que la tâche VOD à la sélection d'une série, sans modifier la lecture des épisodes.

  Fichiers :
  - `app/src/main/java/com/cstv/app/presentation/series/SeriesState.kt`
  - `app/src/main/java/com/cstv/app/presentation/series/SeriesViewModel.kt`

  Validation :
  - `selectStream(SeriesStream?)` délègue à `selectStreamId` sans régression des appelants existants.
  - Une seconde sélection du même identifiant déjà chargée ou en cours de chargement est un no-op ; une fiche en erreur reste rechargeable.
  - L'observation de note reste limitée à l'identifiant de la série courante.

- [x] **Tâche 3 — Capturer le média propriétaire de chaque entrée de détail**

  Objectif :
  Dans les deux destinations de détail, mémoriser de façon saveable l'identifiant d'amorçage de l'entrée de backstack, charger ce seul identifiant et dépiler défensivement une entrée sans identifiant.

  Fichiers :
  - `app/src/main/java/com/cstv/app/presentation/navigation/NavGraph.kt`

  Validation :
  - Chaque entrée `vod_details` et `series_details` utilise `rememberSaveable`, avec un commentaire expliquant pourquoi `remember` est insuffisant.
  - Le retour A → B → Retour restaure A sans nouvelle navigation ni rechargement visible ; la chaîne A → B → C se dépile dans l'ordre inverse.
  - Les rappels `onActiveVodDetailsChanged` et `onActiveSeriesDetailsChanged` continuent d'alimenter les lecteurs avec la fiche restaurée.

- [x] **Tâche 4 — Couvrir les gardes de sélection par des tests unitaires**

  Objectif :
  Ajouter une couverture de non-régression des ViewModels pour les chemins idempotents et le filtrage des émissions de note périmées.

  Fichiers :
  - `app/src/test/java/com/cstv/app/presentation/vod/VodViewModelTest.kt`
  - `app/src/test/java/com/cstv/app/presentation/series/SeriesViewModelTest.kt`

  Validation :
  - Les tests vérifient qu'une seconde sélection du même identifiant chargé ou en chargement ne réappelle pas le use case de détails.
  - Les tests vérifient qu'une nouvelle sélection recharge bien le nouveau média et qu'une erreur du média courant reste retentable.
  - Les tests vérifient qu'une émission de note provenant d'un ancien identifiant est ignorée.

- [x] **Tâche 5 — Vérifier l'intégration et les parcours réels**

  Objectif :
  Confirmer que le correctif est compilable, sans régression automatisée, puis exécuter la matrice de navigation décrite par le ticket sur mobile et Android TV.

  Fichiers :
  - `ai/bugs/B13-related-titles-back-navigation.md`

  Validation :
  - `./gradlew assembleDebug lintDebug testDebugUnitTest` réussit.
  - Les parcours VOD et Série A → B → Retour, A → B → C → Retours, retour pendant chargement ou après erreur, et lecture après retour sont validés manuellement sur mobile et Android TV.
  - Les résultats automatisés et les éventuelles limites de validation manuelle sont consignés dans le ticket lors de l'étape 8, sans les anticiper ici.

---

# 7. Plan de test

1. **Test unitaire ou de comportement manuel** :
   - Naviguer sur l'Accueil, ouvrir le film A.
   - Cliquer sur le titre associé B.
   - Cliquer sur Retour arrière. Vérifier qu'on revient bien sur la fiche du film A.
   - Cliquer sur Retour arrière à nouveau. Vérifier qu'on revient bien sur l'Accueil.
2. **Idem pour les séries** :
   - Ouvrir la série A, cliquer sur la série associée B, faire retour, s'assurer du retour sur la série A.

---

# 8. Review

Date : 2026-07-25
Périmètre relu : `presentation/navigation/NavGraph.kt` (`vod_details`,
`series_details`), `presentation/vod/VodViewModel.kt` + `VodState.kt`,
`presentation/series/SeriesViewModel.kt` + `SeriesState.kt`, et les deux suites
de tests associées.

Status: CORRECTIONS APPLIQUÉES — validation automatisée à finaliser

## Conforme à la spécification

- `rememberSaveable` est bien utilisé (et non `remember`) dans les deux
  destinations, avec la sentinelle `NO_STREAM_ID` et le dépilage défensif prévus
  en §4.3. La correction retenue est donc celle validée en §4.1, pas
  l'hypothèse initiale invalidée.
- `selectStreamId` porte la logique, `selectStream` délègue, et la garde
  d'idempotence exclut volontairement le cas `error != null` : une fiche en
  erreur reste rechargeable, conformément au cas limite « média associé
  indisponible ».
- La migration `selectedStream` → `selectedStreamId` est complète et sans reste :
  aucune autre lecture de ces champs n'existe dans `app/src/main`
  (`LiveTvState.selectedStream` est un champ homonyme sans rapport). La garde
  de `observeRating` a été portée sur l'identifiant dans les deux ViewModels.
- `MainActivity.kt` n'est pas modifié et les états hissés restent des valeurs
  d'amorçage : périmètre §4.2 respecté, aucune route paramétrée introduite.
- `onActiveVodDetailsChanged` / `onActiveSeriesDetailsChanged` restent invoqués
  pendant la composition de la fiche restaurée (`NavGraph.kt:404` et `:480`) :
  la cohérence des lecteurs après retour, affirmée en §4.5, tient toujours.

## Critique

Aucun.

## Majeur

### M1 — Couverture de tests incomplète au regard de la tâche 4 et du risque §4.6

Description :
Un seul test par ViewModel a été livré
(`selectingAnAlreadyLoadedDetailDoesNotReloadIt`), qui couvre la garde
d'idempotence. Les deux autres cas exigés par la tâche 4 manquent :
« une nouvelle sélection recharge bien le nouveau média et une erreur du média
courant reste retentable », et surtout « une émission de note provenant d'un
ancien identifiant est ignorée ». §4.6 classait ce dernier point comme risque
explicite de régression à couvrir par test unitaire.

Impact :
La condition de garde de `observeRating` a été réécrite
(`current.selectedStream?.streamId == stream.streamId` →
`current.selectedStreamId == streamId`) sans aucun test. Une note émise pour un
média quitté pourrait écraser `mediaRating` sur la fiche courante — like/dislike
affiché sur le mauvais titre — sans que la suite le détecte. Le chemin
« nouvelle sélection » n'est pas non plus verrouillé : une garde trop large
introduite plus tard empêcherait le chargement de B depuis A sans test rouge.

Correction attendue :
Ajouter, côté VOD et côté Séries : (a) `selectStreamId(A)` puis
`selectStreamId(B)` → le use case de détails est appelé pour B ;
(b) une fiche en erreur reste rechargeable pour le même identifiant ;
(c) une émission de `observeRating` liée à un identifiant périmé ne modifie pas
`mediaRating`.

### M2 — Vérification runtime §4.6 ni exécutée ni consignée

Description :
§4.6 rend obligatoire, avant clôture et **sur les deux plateformes** (mobile et
Android TV, qui partagent `AppNavGraph`), la matrice : A → B → retour ;
A → B → C → retours successifs ; retour pendant le chargement de B ; retour
après erreur de chargement de B ; ouverture de la fiche depuis Accueil,
Recherche, Favoris, Films/Séries et « Ajouts récents » ; lecture après retour.
Aucun résultat n'est consigné et le ticket n'a pas de section « Notes de
développement ».

Impact :
Le correctif repose sur un comportement de `SaveableStateProvider` non
observable en test unitaire sur ce projet (pas d'infrastructure `androidTest`) :
sans la passe manuelle, rien ne prouve que le symptôme est corrigé.

Correction attendue :
Exécuter la matrice §4.6 et consigner les résultats à l'étape 9, en incluant
explicitement le cas « retour pendant le chargement de B », qui exerce la
branche `isLoadingDetails` de la garde.

## Mineur

### m1 — `selectStream(...)` est devenu du code mort

Description :
Après la migration, plus aucun appelant de `VodViewModel.selectStream` ni de
`SeriesViewModel.selectStream` ne subsiste dans `app/src/main` : les deux
destinations appellent désormais `selectStreamId`. La tâche 1 justifiait le
délégué par la compatibilité « avec les appelants existants », qui n'existent
plus.

Impact :
Dette morte, et surtout point d'entrée qui réautorise le passage d'un
`VodStream`/`SeriesStream` issu de l'état hissé — c'est-à-dire le motif à
l'origine de B13.

Correction attendue :
Supprimer les deux délégués, ou les conserver en les documentant comme
commodité de test uniquement.

### m2 — Commentaire justificatif incomplet côté séries

Description :
La décision technique §5.2 exige que le commentaire expliquant *pourquoi
`remember` est insuffisant* soit conservé, et la validation de la tâche 3 le
demande pour **chaque** entrée. Côté VOD (`NavGraph.kt:387-388`), l'explication
est présente. Côté séries (`NavGraph.kt:464`), le commentaire se réduit à
« Keep the series identifier with this entry for correct A -> B -> back. » et
n'explique pas la contrainte `rememberSaveable`.

Impact :
Un contributeur simplifiant la destination séries en `remember` réintroduirait
B13 pour les séries uniquement — régression asymétrique, difficile à repérer.

Correction attendue :
Aligner le commentaire séries sur celui de la destination VOD.

### m3 — Sentinelle `NO_STREAM_ID` plutôt qu'un type nullable

Description :
`private const val NO_STREAM_ID = -1` sert de valeur d'absence parce que
`rememberSaveable` porte un `Int`. La valeur est confondable avec un
identifiant réel si un panel Xtream renvoyait un identifiant négatif, et duplique
la convention déjà utilisée par `ProfileManager.NO_PROFILE`.

Impact :
Faible : les identifiants Xtream observés sont positifs.

Correction attendue :
À défaut de changement, documenter la contrainte en une ligne. Un
`rememberSaveable { activeVodMovie?.streamId }` typé `Int?` fonctionne également
(les types nullables sont supportés par le `Saver` par défaut) et supprimerait
la sentinelle.

### m4 — Plan de développement non mis à jour

Description :
Les cinq tâches du §6 restent `- [ ]` alors que les tâches 1 à 4 sont livrées, et
le ticket n'a pas de section « Notes de développement ».

Impact :
Traçabilité du workflow.

Correction attendue :
Cocher les tâches livrées et consigner les notes à l'étape 7.

## Point d'attention inter-tickets

`MobileNavigation.isTabSelected` (livré par B11) mappe `vod_details` → `movies`
et `series_details` → `series` par égalité stricte de route. Ce mappage reste
valide ici puisque B13 n'introduit pas de route paramétrée, comme §4.4 le
prévoyait. Toute reprise ultérieure du sujet « navigation par arguments »
(renvoyée au backlog `ai/technical/`) devra traiter B11 et B13 ensemble.

## Non vérifié à cette étape

- `./gradlew assembleDebug lintDebug testDebugUnitTest` relève de l'étape 9 et
  n'a pas été exécuté pendant cette review. Suites exposées au changement de
  signature : `VodViewModelTest`, `SeriesViewModelTest`.
- Restauration après mort du processus (bénéfice annoncé de `rememberSaveable`
  en §4.4) : à vérifier avec « Ne pas conserver les activités ».

---

## Notes de corrections et validation — 2026-07-25

- Revue : les délégations `selectStream` mortes sont supprimées ; les deux
  destinations documentent la nécessité de `rememberSaveable`, et la
  sentinelle est explicitement documentée. Les tests VOD/Séries couvrent aussi
  la sélection d'un nouvel identifiant et la reprise après erreur.
- Contrôle compilateur : `compileDebugKotlin` et `compileDebugUnitTestKotlin`
  ont abouti pendant les lancements Gradle.
- Limite : les tâches Gradle de test, assemble et lint se sont interrompues
  avant leur résultat final dans cet environnement ; elles restent à rejouer
  sur une machine de développement.
- Les parcours A -> B -> retour, chaînes A -> B -> C et les lecteurs mobile/TV
  ne sont pas vérifiés ici : l'ADB SDK est présent, mais son daemon est bloqué
  par le sandbox.

---

# 9. Release

Version : v1.54.20

Commit : v1.54.20

Date : 2026-07-25
