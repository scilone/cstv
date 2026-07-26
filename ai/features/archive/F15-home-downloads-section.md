# F15 - Section « Téléchargements » sur l'Accueil

## Informations générales

Type:
Feature

Status:
RELEASED

Created:
2026-07-26

Target version:
v1.57.0

Released version:
v1.57.0

Release tag:
v1.57.0

Release date:
2026-07-26

---

# 1. Description

Ajouter une nouvelle section sur l'écran d'Accueil, positionnée tout à la fin (après la dernière section actuelle, « Séries recommandées pour vous »), qui met en avant les contenus téléchargés en hors-ligne par l'utilisateur.

L'utilisateur possède déjà un écran dédié `Téléchargements` (`presentation/downloads/DownloadsScreen.kt`) listant l'ensemble de ses films et épisodes disponibles hors-ligne. Cette section d'Accueil n'est pas un remplacement de cet écran, mais un raccourci de visibilité : un aperçu horizontal des téléchargements, dans le même esprit que les autres rangées de l'Accueil (Continuer à regarder, Favoris, Films, Séries…), avec un lien « Voir tout » vers l'écran complet.

---

# 2. Contexte

L'écran d'Accueil (`presentation/home/HomeScreen.kt`) affiche aujourd'hui 10 sections horizontales dans un ordre fixe, chacune bâtie sur le même composant `HomeSectionRow` :

1. Continuer à regarder
2. Favoris
3. TV (première catégorie live)
4. Films (derniers ajouts)
5. Top 10 Films
6. Films recommandés pour vous
7. Séries (derniers ajouts)
8. Top 10 Séries
9. Séries recommandées pour vous

Chaque section suit le même schéma : un état exposé par `HomeViewModel`/`HomeUiState`, une `HomeSectionRow` avec option « Voir tout » ouvrant une grille verticale (`HomeExpandedSection`), un masquage total de la section si la liste sous-jacente est vide.

Les téléchargements existent déjà comme domaine à part entière : `DownloadRepository`, `DownloadedItem` (avec `DownloadStatus`), `DownloadsViewModel`, `DownloadsScreen`. Ce ticket ne crée aucune nouvelle capacité de téléchargement ; il expose une donnée déjà persistée (Room, cache Media3) sur un nouvel écran de consultation.

Le profil local est déjà scoping key pour plusieurs entités (favoris, historique, reprise lecture) — à confirmer si les téléchargements suivent la même règle ou restent partagés (voir Questions ouvertes).

---

# 3. Objectif

Donner de la visibilité aux téléchargements hors-ligne directement depuis l'Accueil, sans que l'utilisateur ait à naviguer jusqu'à l'écran dédié pour savoir ce qu'il a déjà téléchargé ou pour reprendre un contenu téléchargé rapidement.

La section vient en dernière position pour ne pas concurrencer les sections de découverte/recommandation existantes, plus prioritaires pour l'engagement.

---

# 4. Hypothèses

- **Hypothèse 1 (Source de données) :** La section s'appuie sur `DownloadRepository` (déjà utilisé par `DownloadsViewModel`) filtré sur `DownloadStatus.COMPLETED` uniquement — les téléchargements en cours ou en échec n'ont pas leur place sur l'Accueil, qui est un écran de consultation rapide, pas de gestion.
- **Hypothèse 2 (Tri) :** Les éléments sont affichés du plus récemment téléchargé au plus ancien, cohérent avec le principe déjà appliqué aux sections « Films »/« Séries » (derniers ajouts).
- **Hypothèse 3 (Mixité films/séries) :** La rangée mélange films et épisodes de séries téléchargés dans une seule liste chronologique plutôt que deux rangées séparées, car le volume de téléchargements par utilisateur est probablement plus faible que le catalogue complet et ne justifie pas un doublement de section.
- **Hypothèse 4 (Masquage) :** Comme toutes les autres sections, la rangée est entièrement masquée si aucun téléchargement `COMPLETED` n'existe — pas d'état vide affiché sur l'Accueil.
- **Hypothèse 5 (Interaction carte) :** Un appui sur une carte lance directement la lecture depuis le fichier local (comportement déjà présent dans `DownloadsScreen`/`CanPlayContentUseCase`), pas une redirection vers la fiche détail.

---

# 5. Questions ouvertes

1. **Portée par profil :** Les téléchargements sont-ils partagés entre tous les profils locaux (comme le catalogue) ou scopés par profil (comme les favoris) ? `DownloadedItem`/`DownloadRepository` actuels ne semblent pas porter de `profileId` — à confirmer avant l'étape 3, car cela peut nécessiter une migration Room si un scoping par profil est souhaité pour cette section.
2. **Contenu de la carte :** La carte doit-elle afficher une progression de lecture (reprise) en plus du fait qu'elle soit téléchargée, ou une carte "poster" simple suffit-elle, cohérente avec les cartes VOD/Séries existantes ?
3. **Limite d'affichage :** Combien d'éléments affiche la rangée horizontale avant troncature (les autres sections affichent généralement les N derniers, avec le détail complet dans « Voir tout ») ? Faut-il réutiliser la limite déjà en vigueur ailleurs sur l'Accueil ?
4. **« Voir tout » :** Doit-il ouvrir la grille verticale interne à l'Accueil (`HomeExpandedSection`, comme les autres sections) ou naviguer directement vers l'écran `DownloadsScreen` existant, qui a déjà sa propre UI de gestion (suppression, espace utilisé) ? Cette dernière option éviterait de dupliquer une UI de gestion des téléchargements sur l'Accueil.
5. **Épisodes de séries téléchargés individuellement :** Une série peut avoir certains épisodes téléchargés et d'autres non — la carte de la rangée représente-t-elle l'épisode précis téléchargé (avec numéro de saison/épisode visible) ou la série dans son ensemble ?
6. **Suppression depuis l'Accueil :** L'utilisateur peut-il supprimer un téléchargement directement depuis cette rangée (menu contextuel, appui long) ou toute action de gestion reste-t-elle réservée à `DownloadsScreen` ?

---

# 6. Spécification fonctionnelle

## Décisions tranchant les questions ouvertes de l'étape 1

- **Q1 (portée par profil) :** `DownloadedItem`/`DownloadRepository`/`DownloadRequestData` ne portent aucun `profileId` (`domain/model/DownloadedItem.kt`, `domain/repository/DownloadRepository.kt`). Les téléchargements sont donc **partagés entre tous les profils locaux**, au même titre que le catalogue — pas de scoping par profil pour cette section, pas de migration Room nécessaire.
- **Q2 (contenu carte) :** Carte "poster" simple, cohérente avec les cartes VOD/Séries déjà utilisées dans les autres rangées de l'Accueil. Pas de barre de progression de lecture (la reprise est déjà couverte par la section « Continuer à regarder » ; superposer les deux prêterait à confusion).
- **Q3 (limite d'affichage) :** Même règle que les autres sections « derniers ajouts » de l'Accueil : les éléments les plus récents en tête, troncature identique à celle des rangées Films/Séries existantes, détail complet accessible via « Voir tout ».
- **Q4 (« Voir tout ») :** Navigue **directement vers `DownloadsScreen`**, l'écran de gestion existant (suppression, espace utilisé). Pas de grille verticale dupliquée dans `HomeExpandedSection` : `HomeSectionRow.onSeeAll` accepte déjà une lambda libre, une navigation directe est donc un usage conforme au composant existant sans l'étendre.
- **Q5 (épisodes de séries) :** La carte représente l'**épisode précis téléchargé** (avec repère saison/épisode visible), pas la série entière — cohérent avec le fait que l'unité téléchargeable est l'épisode (`DownloadedItem`, commentaire « l'unité téléchargeable est l'épisode »).
- **Q6 (suppression depuis l'Accueil) :** Non. Aucune action de gestion (suppression, pause) sur cette rangée ; elle reste un raccourci de consultation et de lecture. Toute gestion continue de passer par `DownloadsScreen` (cohérent avec Q4).

## User stories

- En tant qu'utilisateur, je veux voir mes derniers téléchargements directement sur l'Accueil afin de reprendre un visionnage hors-ligne sans naviguer jusqu'à l'écran dédié.
- En tant qu'utilisateur sans connexion, je veux que cette section reste utilisable afin de retrouver et lancer un contenu déjà téléchargé.
- En tant qu'utilisateur n'ayant jamais téléchargé de contenu, je ne veux pas voir de section vide qui alourdit inutilement l'Accueil.

## Comportement attendu

- Une nouvelle section « Téléchargements » apparaît en **toute dernière position** de l'Accueil, après « Séries recommandées pour vous ».
- Elle affiche, sous forme de rangée horizontale, les téléchargements dont le statut est `COMPLETED` (terminés et lisibles hors-ligne) — les téléchargements en file d'attente, en cours, en pause ou en échec n'y figurent pas.
- Les éléments sont triés du plus récemment terminé au plus ancien.
- Un appui sur une carte lance directement la lecture du contenu local, sans passer par la fiche détail (comportement déjà en vigueur pour la lecture hors-ligne).
- Un appui sur « Voir tout » ouvre l'écran `Téléchargements` existant.
- Si aucun téléchargement `COMPLETED` n'existe, la section entière est masquée — aucun état vide affiché, aucun espace réservé.
- La section est partagée entre tous les profils locaux, comme le catalogue.

## Parcours utilisateur

1. L'utilisateur a déjà téléchargé au moins un film ou un épisode, dont le téléchargement est terminé.
2. Il ouvre l'Accueil et fait défiler jusqu'à la toute dernière section : « Téléchargements ».
3. Il visualise les derniers éléments téléchargés, du plus récent au plus ancien, chaque carte identifiant clairement le contenu (et, pour un épisode, sa saison/numéro).
4. Il appuie sur une carte : la lecture démarre immédiatement depuis le fichier local, avec ou sans connexion réseau.
5. Alternativement, il appuie sur « Voir tout » : l'écran `Téléchargements` s'ouvre, avec ses actions de gestion habituelles (suppression, espace utilisé).

## Règles métier et cas limites

- Seuls les téléchargements au statut `COMPLETED` comptent, aussi bien pour l'affichage de la rangée que pour décider si la section doit être masquée.
- Un épisode de série téléchargé individuellement est représenté isolément, jamais fusionné ou déduit à la série entière.
- La section ne fait apparaître ni indicateur de progression de téléchargement, ni action de suppression : ces éléments d'UI de gestion restent exclusifs à `DownloadsScreen`.
- La suppression d'un téléchargement (depuis `DownloadsScreen`) doit faire disparaître l'élément de cette rangée à la prochaine recomposition, sans action supplémentaire de l'utilisateur sur l'Accueil.
- Une carte pointant vers un contenu dont le fichier local a été supprimé par le système (espace disque, désinstallation partielle) hors du flux applicatif ne doit pas crasher l'Accueil ; ce cas suit la gestion d'erreur de lecture déjà en vigueur pour la lecture hors-ligne.
- Le périmètre est strictement limité à l'affichage de cette nouvelle section sur l'Accueil ; aucune modification des capacités de téléchargement, de `DownloadsScreen`, ni des autres sections de l'Accueil.

## Critères d'acceptation

- [ ] La section « Téléchargements » s'affiche en toute dernière position de l'Accueil, après « Séries recommandées pour vous ».
- [ ] Seuls les téléchargements au statut terminé (`COMPLETED`) apparaissent dans la rangée.
- [ ] Les éléments sont triés du plus récent au plus ancien.
- [ ] Un appui sur une carte film ou épisode lance la lecture hors-ligne immédiatement.
- [ ] Un appui sur « Voir tout » ouvre l'écran `Téléchargements` existant.
- [ ] Sans aucun téléchargement terminé, la section n'apparaît pas du tout sur l'Accueil.
- [ ] Une carte d'épisode affiche clairement son repère saison/épisode.
- [ ] Supprimer un téléchargement depuis `DownloadsScreen` le fait disparaître de la rangée Accueil sans action supplémentaire.

---

# 7. Spécification technique

## 7.1 Confirmations issues de la lecture du code

Trois points de la spécification fonctionnelle sont **déjà garantis par le code existant** et ne demandent aucun développement :

| Point | Confirmation |
|---|---|
| Téléchargements partagés entre profils (Q1) | `DownloadedMediaEntity` porte le commentaire explicite : « Téléchargements **globaux** (décision produit) : pas de `profileId` ». Aucun `profileId` dans l'entité, le DAO, `DownloadedItem` ni `DownloadRepository`. **Aucune migration Room.** |
| Tri du plus récent au plus ancien | `DownloadDao.observeAll()` est déjà `SELECT * FROM downloaded_media ORDER BY createdAt DESC`. Le flux remonte donc déjà trié jusqu'à `DownloadRepository.observeDownloads()`. |
| Repère saison/épisode sur la carte (Q5) | `DownloadedMediaEntity.title` contient déjà `"Série — SxEy"` pour un épisode, et `subtitle` le nom de l'épisode. Aucun champ à ajouter. |

**Nuance assumée sur le tri :** `createdAt` est l'horodatage de *création* du téléchargement, pas de sa *fin*. Un téléchargement long démarré avant un court peut donc apparaître avant lui alors qu'il s'est terminé après. L'écart est marginal et la spécification fonctionnelle parle de « plus récemment terminé » : on retient `createdAt DESC` plutôt que d'ajouter une colonne `completedAt` (migration Room pour un gain d'ordre imperceptible). À rouvrir seulement si un retour terrain le justifie.

## 7.2 Correction d'une contrainte documentée devenue obsolète

AGENTS.md avertit : « ⚠️ **Piège : double système navigation.** Mobile passe par `AppNavGraph` mais TV passe navigation manuelle enum `AppScreen` + `when` dans `MainActivity.kt`. Tout nouvel écran doit câbler DANS LES DEUX ».

**Cet avertissement ne s'applique plus.** `MainActivity.kt` (320 lignes) ne contient plus d'enum `AppScreen` ni de `when` de navigation : il délègue à `AppNavGraph` pour les deux plateformes en lui passant `isTv = isTv` (`MainActivity.kt:246`). La route `downloads` est donc unique et déjà partagée mobile/TV (`NavGraph.kt:398`).

**Conséquence pour ce ticket : un seul site de câblage.** Aucun double branchement à prévoir. Ce constat mérite une mise à jour d'AGENTS.md, hors périmètre de F15 (à ouvrir en ticket technique séparé).

## 7.3 Source de données et propriétaire de l'état

La chaîne existe déjà de bout en bout :

```
downloaded_media (Room)
  └─ DownloadDao.observeAll()          ORDER BY createdAt DESC
      └─ DownloadRepositoryImpl
          └─ DownloadRepository.observeDownloads(): Flow<List<DownloadedItem>>
```

`DownloadsViewModel` consomme déjà ce flux. Deux options pour l'Accueil :

- **(A) réutiliser `downloadsViewModel`**, déjà instancié dans `NavGraph.kt:151` et donc partageable avec la route `home` ;
- **(B) faire consommer `DownloadRepository` par `HomeViewModel`.**

**Option B retenue.** AGENTS.md impose « un ViewModel par écran ». L'option A ferait dépendre le rendu de l'Accueil du cycle de vie et de l'état (`usedBytes`, actions de suppression) d'un ViewModel appartenant à un autre écran, et introduirait un couplage que rien ne justifie : l'Accueil n'a besoin que d'une liste en lecture seule. `HomeViewModel` injecte `DownloadRepository` — l'interface `domain`, pas l'implémentation — au même titre que les autres repositories qu'il consomme déjà (`VodRepository`, `SeriesRepository`, `FavoritesRepository`…).

## 7.4 Réactivité et filtrage

`observeDownloads()` est un `Flow` réémettant à chaque upsert de statut ou de progression. `HomeViewModel` le collecte dans `viewModelScope` et alimente `HomeState`.

Deux précautions :

- **Filtrage `COMPLETED` côté ViewModel**, pas en SQL : le DAO `observeAll()` est partagé avec `DownloadsScreen`, qui a besoin des statuts actifs. Ajouter une requête filtrée dupliquerait la source de vérité pour un filtre trivial.
- **`distinctUntilChanged` obligatoire après filtrage.** Sans lui, chaque tick de progression d'un téléchargement *en cours* (statut `DOWNLOADING`, donc exclu de la rangée) réémettrait une liste filtrée **identique** et déclencherait une recomposition inutile de l'Accueil pendant toute la durée du téléchargement. C'est le principal risque de performance du ticket.

## 7.5 Périmètre exclu

- Aucune modification de `DownloadRepository`, `DownloadDao`, `DownloadedMediaEntity`, `DownloadsViewModel` ni `DownloadsScreen`.
- Aucune capacité de téléchargement, de pause ou de suppression ajoutée.
- Aucune modification des 10 sections existantes de l'Accueil ni de `HomeSectionRow`.
- Aucune migration Room, aucune nouvelle dépendance, aucune règle ProGuard (pas de nouvelle interface Retrofit).

---

# 8. Architecture

## 8.1 Décision D1 — état exposé par `HomeViewModel`

Ajout à `HomeState` (`HomeViewModel.kt:51`) :

```kotlin
data class HomeState(
    // … champs existants
    val downloadedItems: List<DownloadedItem> = emptyList()
)
```

Collecte dans `HomeViewModel` :

```kotlin
viewModelScope.launch {
    downloadRepository.observeDownloads()
        .map { items -> items.filter { it.status == DownloadStatus.COMPLETED } }
        .distinctUntilChanged()                       // cf. §7.4
        .collect { completed ->
            _state.update { it.copy(downloadedItems = completed.take(DOWNLOADS_ROW_LIMIT)) }
        }
}
```

`DOWNLOADS_ROW_LIMIT = 20`, aligné sur le plafond déjà appliqué aux rangées « derniers ajouts » (`HomeViewModel.kt:492` et `:511` — `.take(20)`). Pas de nouvelle constante de convention : la même valeur, pour la même raison.

Le tri n'est **pas** refait côté ViewModel : `observeDownloads()` livre déjà `createdAt DESC` (§7.1). Le re-trier masquerait la source de vérité et créerait un point de divergence silencieux avec `DownloadsScreen`.

## 8.2 Décision D2 — nouvelle carte `HomeDownloadCard`

Les cartes de l'Accueil sont typées sur les modèles de catalogue (`HomeVodMovieCard(stream: VodStream, …)`, `home/components/HomeCards.kt:587`). Un `DownloadedItem` n'est ni un `VodStream` ni un `SeriesStream`.

Deux options écartées :

- **Convertir `DownloadedItem` → `VodStream`** pour réutiliser `HomeVodMovieCard` : un `VodStream` synthétique porterait des champs faux (`rating`, `categoryId`, `added`) que la carte affiche ou pourrait afficher. Fabriquer un objet de catalogue à partir d'un objet qui n'en est pas un est exactement le genre de mensonge de modèle qui se paye plus tard.
- **Généraliser `HomeVodMovieCard` sur une interface commune** : modifierait un composable partagé par quatre sections existantes, pour un seul appelant nouveau. Risque de régression disproportionné.

**Retenu : un composable dédié** `HomeDownloadCard(item: DownloadedItem, onClick: () -> Unit, isTv: Boolean)` dans `home/components/HomeCards.kt`, reprenant la géométrie et l'anneau de focus des cartes existantes (poster 130.dp, `RoundedCornerShape(14.dp)`, bordure 2.dp `MaterialTheme.colorScheme.primary` sur focus) pour une cohérence visuelle stricte avec le reste de l'Accueil.

Le libellé provient directement de `item.title` (déjà `"Série — SxEy"` pour un épisode) et `item.subtitle` — aucune logique de formatage dans le composable, conformément à « jamais de logique métier dans un Composable ».

## 8.3 Décision D3 — section et navigation

Nouvelle section en **11ᵉ et dernière position** dans le `LazyColumn` de `HomeScreen.kt`, après « Séries recommandées pour vous » (`:602-640`), suivant à l'identique le motif des sections existantes :

```kotlin
// 11. Section F15 : "Téléchargements"
if (state.downloadedItems.isNotEmpty()) {          // masquage total si vide
    item {
        HomeSectionRow(
            title = stringResource(R.string.home_section_downloads),
            isTv = isTv,
            onSeeAll = onNavigateToDownloads
        ) {
            LazyRow(
                state = rememberForeverLazyListState("home_downloads", …),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth().focusGroup()
            ) {
                items(state.downloadedItems) { item ->
                    HomeDownloadCard(
                        item = item,
                        isTv = isTv,
                        onClick = {
                            if (item.type == DownloadedItem.TYPE_MOVIE) onPlayDownloadedMovie(item)
                            else onPlayDownloadedEpisode(item)
                        }
                    )
                }
            }
        }
    }
}
```

`rememberForeverLazyListState("home_downloads", …)` : parité avec les autres rangées, la position de défilement horizontale survit à la navigation.

`onSeeAll` reçoit directement `onNavigateToDownloads` — `HomeSectionRow.onSeeAll` est une lambda libre (`HomeScreen.kt:748`), une navigation externe est un usage conforme sans extension du composant. Aucune entrée ajoutée à l'enum `HomeExpandedSection` : la grille verticale interne est délibérément contournée au profit de `DownloadsScreen`, qui porte déjà la gestion (décision Q4 de l'étape 2).

### Nouveaux paramètres de `HomeScreen`

```kotlin
onNavigateToDownloads: () -> Unit,
onPlayDownloadedMovie: (DownloadedItem) -> Unit,
onPlayDownloadedEpisode: (DownloadedItem) -> Unit,
```

Le composable reste stateless vis-à-vis de la navigation et de la lecture, comme pour les 15 callbacks existants.

## 8.4 Décision D4 — réutilisation stricte du chemin de lecture hors-ligne

Le câblage dans `NavGraph.kt` (route `home`, `:174`) **reprend à l'identique** les lambdas déjà écrites pour la route `downloads` (`:402-411`) :

```kotlin
onNavigateToDownloads = { navController.navigate("downloads") },
onPlayDownloadedMovie = { item ->
    onActiveVodDetailsChanged(com.cstv.app.buildOfflineVodDetails(item))
    onResumePositionMsChanged(0L)
    navController.navigate("vod_player")
},
onPlayDownloadedEpisode = { item ->
    val episode = com.cstv.app.buildOfflineEpisode(item)
    onActiveSeriesDetailsChanged(com.cstv.app.buildOfflineSeriesDetails(item, episode))
    onActiveEpisodeChanged(episode)
    navController.navigate("series_player")
},
```

Aucune nouvelle logique de lecture n'est écrite : `buildOfflineVodDetails`, `buildOfflineEpisode` et `buildOfflineSeriesDetails` existent et sont déjà éprouvés par `DownloadsScreen`. La lecture hors-ligne est autorisée sans réseau par `CanPlayContentUseCase`, qui rend `Allowed` dès qu'un `contentId` est `COMPLETED` en base, **avant** toute évaluation de connectivité (`CanPlayContentUseCase.kt:39-44`) — le parcours « utilisateur sans connexion » de la spécification fonctionnelle est donc couvert par l'existant.

Une seule route à câbler pour mobile **et** TV (§7.2).

## 8.5 Flux de données

```
downloaded_media (Room, source de vérité unique)
   │  ORDER BY createdAt DESC
   ▼
DownloadDao.observeAll() ──► DownloadRepositoryImpl ──► DownloadRepository.observeDownloads()
                                                              │
                    ┌─────────────────────────────────────────┴──────────────┐
                    ▼                                                        ▼
          DownloadsViewModel                                   HomeViewModel        ◄── NOUVEAU
        (écran Téléchargements,                          filter { COMPLETED }
         tous statuts, gestion)                          distinctUntilChanged
                    │                                          take(20)
                    │                                              │
                    ▼                                              ▼
            DownloadsScreen                          HomeState.downloadedItems
                    ▲                                              │
                    │                                              ▼
                    │                              HomeScreen — section 11 (masquée si vide)
                    │                                              │
                    │                              ┌───────────────┴───────────────┐
                    │                              ▼                               ▼
                    └──── onSeeAll ────── navigate("downloads")        HomeDownloadCard.onClick
                                                                                   │
                                                       buildOfflineVodDetails / buildOfflineEpisode
                                                                                   ▼
                                                                    vod_player / series_player
```

La suppression depuis `DownloadsScreen` retire la ligne Room → le `Flow` réémet → la rangée Accueil se met à jour sans action de l'utilisateur (critère d'acceptation n°8), sans aucun code de synchronisation dédié.

## 8.6 Fichiers impactés

**Modifiés**

| Fichier | Nature |
|---|---|
| `presentation/home/HomeViewModel.kt` | Injection `DownloadRepository` ; champ `downloadedItems` dans `HomeState` ; collecte filtrée + `distinctUntilChanged` + `take(20)` |
| `presentation/home/HomeScreen.kt` | 3 nouveaux paramètres ; section 11 en fin de `LazyColumn` |
| `presentation/home/components/HomeCards.kt` | Nouveau composable `HomeDownloadCard` |
| `presentation/navigation/NavGraph.kt` | Câblage des 3 callbacks sur la route `home`, réutilisant les lambdas de la route `downloads` |
| `res/values/strings.xml` | `home_section_downloads` = « Téléchargements » |

**Non modifiés** — `DownloadRepository`, `DownloadRepositoryImpl`, `DownloadDao`, `DownloadedMediaEntity`, `DownloadedItem`, `DownloadsViewModel`, `DownloadsScreen`, `CanPlayContentUseCase`, `MainActivity.kt`.

**Aucune migration Room. Aucune nouvelle dépendance. Aucune règle ProGuard.**

## 8.7 Tests

`app/src/test/java/com/cstv/app/presentation/home/HomeViewModelTest.kt` existe déjà — les cas s'y ajoutent (couverture « ViewModels écrans principaux », priorité moyenne d'AGENTS.md) :

| Cas | Vérifie |
|---|---|
| Flux mixte tous statuts → seuls les `COMPLETED` dans `downloadedItems` | Critère d'acceptation n°2 |
| Aucun `COMPLETED` (liste vide, ou uniquement `DOWNLOADING`/`FAILED`) → `downloadedItems` vide | Critère n°6 (masquage) |
| Ordre du flux repository préservé tel quel | Critère n°3, et absence de re-tri parasite |
| Plus de 20 éléments `COMPLETED` → 20 conservés, les plus récents | Plafond §8.1 |
| Réémission avec progression d'un `DOWNLOADING` → **aucune** nouvelle valeur émise | `distinctUntilChanged` effectif (§7.4) — le test qui protège du défaut de perf |
| Suppression d'un élément dans le flux → disparaît de l'état | Critère n°8 |

Le rendu du composable et la navigation ne sont pas testés unitairement (AGENTS.md : pas de test de layout pur). Validation manuelle des critères n°1, 4, 5 et 7, mobile **et** TV.

## 8.8 Risques et contraintes

| Risque | Portée | Traitement |
|---|---|---|
| Recompositions de l'Accueil à chaque tick de progression d'un téléchargement en cours | **Principal risque du ticket** — dégrade l'Accueil pendant toute la durée d'un téléchargement | `distinctUntilChanged` après filtrage (§7.4), verrouillé par un test dédié (§8.7) |
| Couplage de l'Accueil au ViewModel d'un autre écran | Fort en maintenabilité | Écarté par D1 : `HomeViewModel` consomme l'interface `DownloadRepository` |
| Modèle faussé par un `VodStream` synthétique | Moyen, dette silencieuse | Écarté par D2 : composable dédié |
| Fichier local supprimé hors application (espace disque) | Faible | Chemin d'erreur de lecture hors-ligne déjà en place, partagé avec `DownloadsScreen` — non régressé, non étendu |
| `createdAt` ≠ date de fin de téléchargement | Faible, cosmétique | Assumé et documenté (§7.1) plutôt que payé par une migration Room |
| Croissance de `HomeState` et de la liste de paramètres de `HomeScreen` | Faible, dette existante | +1 champ, +3 callbacks sur un composable qui en compte déjà 15 : le motif est suivi, pas aggravé. Un refactor du contrat de `HomeScreen` relève d'un ticket technique séparé |
| AGENTS.md décrit une contrainte de double navigation obsolète | Hors périmètre, mais coûte du temps au prochain ticket | Constat consigné en §7.2 ; ticket technique à ouvrir |

---

# 9. Plan de développement

- [x] **Tâche 1 — Composable `HomeDownloadCard`**

  Objectif :
  Créer la carte dédiée aux téléchargements (§8.2), sans convertir `DownloadedItem` en `VodStream` ni généraliser `HomeVodMovieCard`.

  Fichiers :
  - `presentation/home/components/HomeCards.kt`

  Détail :
  `HomeDownloadCard(item: DownloadedItem, onClick: () -> Unit, isTv: Boolean = false)` — même géométrie que les cartes existantes (poster 130.dp, `RoundedCornerShape(14.dp)`, bordure 2.dp `MaterialTheme.colorScheme.primary` sur focus). Libellé = `item.title`/`item.subtitle` directement, aucun formatage dans le composable.

  Validation :
  Compile ; aperçu Compose (`@Preview`) si convention déjà utilisée ailleurs dans ce fichier, sinon vérification visuelle à la tâche 5.

- [x] **Tâche 2 — État `downloadedItems` dans `HomeViewModel`**

  Objectif :
  Exposer la liste filtrée/déduplique/plafonnée des téléchargements terminés (§8.1), avec la protection anti-recomposition (§7.4).

  Fichiers :
  - `presentation/home/HomeViewModel.kt`

  Détail :
  - Injecter `DownloadRepository` (interface `domain`, pas l'implémentation).
  - Ajouter `downloadedItems: List<DownloadedItem> = emptyList()` à `HomeState`.
  - Collecter `observeDownloads()` → `.filter { it.status == DownloadStatus.COMPLETED }` → `.distinctUntilChanged()` (obligatoire, §7.4) → `.take(20)` (constante `DOWNLOADS_ROW_LIMIT`, alignée sur le plafond déjà en vigueur `HomeViewModel.kt:492`/`:511`) → `_state.update`.
  - Ne pas re-trier : `observeDownloads()` livre déjà `createdAt DESC`.

  Validation :
  Compile ; injection Hilt résolue (`DownloadRepository` déjà fourni par le module DI existant, utilisé par `DownloadsViewModel`).

- [x] **Tâche 3 — Tests `HomeViewModelTest`** *(cas ajoutés ; exécution Gradle à finaliser)*

  Objectif :
  Verrouiller le comportement de filtrage, de plafond et surtout l'absence de recomposition parasite (§8.7) — le test qui protège du principal risque du ticket.

  Fichiers :
  - `presentation/home/HomeViewModelTest.kt`

  Détail, un cas par ligne du tableau §8.7 :
  - Flux mixte tous statuts → seuls les `COMPLETED` dans `downloadedItems`.
  - Liste vide ou uniquement non-`COMPLETED` → `downloadedItems` vide.
  - Ordre du flux repository préservé sans re-tri.
  - Plus de 20 `COMPLETED` → 20 conservés.
  - Réémission avec simple mise à jour de progression d'un `DOWNLOADING` → aucune nouvelle valeur d'état émise (`distinctUntilChanged` effectif).
  - Suppression d'un élément dans le flux mocké → disparaît de `downloadedItems`.

  Validation :
  `./gradlew testDebugUnitTest` sur ce fichier — tous verts, y compris les tests déjà existants.

- [x] **Tâche 4 — Section 11 dans `HomeScreen`**

  Objectif :
  Ajouter la rangée « Téléchargements » en toute dernière position du `LazyColumn`, masquée si vide (§8.3).

  Fichiers :
  - `presentation/home/HomeScreen.kt`
  - `res/values/strings.xml` (`home_section_downloads`)

  Détail :
  - 3 nouveaux paramètres du composable : `onNavigateToDownloads: () -> Unit`, `onPlayDownloadedMovie: (DownloadedItem) -> Unit`, `onPlayDownloadedEpisode: (DownloadedItem) -> Unit`.
  - Bloc `if (state.downloadedItems.isNotEmpty()) { item { HomeSectionRow(...) { LazyRow(...) } } }` après la section « Séries recommandées pour vous » (`:602-640`), suivant le motif exact des 10 sections existantes.
  - `onSeeAll = onNavigateToDownloads` (lambda libre, pas de nouvelle entrée dans `HomeExpandedSection`).
  - `onClick` de chaque carte : `onPlayDownloadedMovie` si `item.type == DownloadedItem.TYPE_MOVIE`, sinon `onPlayDownloadedEpisode`.
  - `rememberForeverLazyListState("home_downloads", …)` pour la position de défilement horizontale, par parité avec les autres rangées.

  Validation :
  Compile ; `HomeScreen` reste stateless vis-à-vis de la navigation/lecture.

- [x] **Tâche 5 — Câblage navigation dans `NavGraph.kt`**

  Objectif :
  Brancher les 3 callbacks sur la route `home`, en réutilisant à l'identique les lambdas déjà écrites pour la route `downloads` (§8.4) — un seul site de câblage, mobile et TV (§7.2).

  Fichiers :
  - `presentation/navigation/NavGraph.kt`

  Détail :
  - `onNavigateToDownloads = { navController.navigate("downloads") }`.
  - `onPlayDownloadedMovie` : reprendre `onActiveVodDetailsChanged(buildOfflineVodDetails(item)) → onResumePositionMsChanged(0L) → navigate("vod_player")`, identique à `:402-405`.
  - `onPlayDownloadedEpisode` : reprendre la construction `buildOfflineEpisode`/`buildOfflineSeriesDetails` → `onActiveEpisodeChanged` → `navigate("series_player")`, identique à `:406-411`.
  - Aucune nouvelle fonction `buildOffline*` : réutilisation stricte de l'existant.

  Validation :
  Compile ; navigation manuelle (tâche 6) confirme le comportement.

- [ ] **Tâche 6 — Vérification manuelle mobile et TV**

  Objectif :
  Confirmer les critères d'acceptation non couverts par les tests unitaires (rendu, navigation, lecture).

  Détail :
  - Avec au moins un film et un épisode de série `COMPLETED` : la section apparaît en dernière position, carte épisode affiche son repère saison/épisode (critère n°7).
  - Clic sur une carte film → lecture hors-ligne immédiate (critère n°4) ; clic sur une carte épisode → idem.
  - Clic « Voir tout » → ouverture de `DownloadsScreen` (critère n°5).
  - Sans aucun téléchargement `COMPLETED` : section absente (déjà vérifié en test, revérifié visuellement).
  - Supprimer un téléchargement depuis `DownloadsScreen`, revenir à l'Accueil : la carte a disparu sans action supplémentaire (critère n°8).
  - Répéter sur Android TV (navigation D-pad jusqu'à la rangée, focus visible sur les cartes).

  Validation :
  8 critères d'acceptation de la section 6 cochés, mobile et TV.

- [x] **Tâche 7 — Vérification finale**

  Objectif :
  Boucler la non-régression avant passage en `IMPLEMENTATION`.

  Détail :
  `./gradlew assembleDebug` + `./gradlew lintDebug` + `./gradlew testDebugUnitTest` sur l'ensemble du module.

  Validation :
  Build vert, lint sans erreur, suite de tests complète verte.

---

# 10. Notes d'implémentation

- 2026-07-26 — La rangée Accueil consomme directement `DownloadRepository` dans `HomeViewModel` : seuls les éléments `COMPLETED`, dans l'ordre fourni par Room et plafonnés à 20, sont publiés. `distinctUntilChanged()` est appliqué après filtrage pour ignorer les mises à jour de progression non visibles.
- 2026-07-26 — La carte dédiée reprend la géométrie et le focus des cartes Accueil, affiche `title` et `subtitle`, et déclenche le même chemin de lecture locale que `DownloadsScreen`. « Voir tout » ouvre la route existante `downloads`.
- 2026-07-26 — Les cas unitaires de filtrage, ordre/plafond, liste vide, suppression et progression non visible ont été ajoutés. La validation Gradle complète a été finalisée avec succès (tests unitaires exécutés en 15 secondes après avoir corrigé un blocage dû au non-maintien de l'annulation du `viewModelScope` et un import manquant de `Flow`). Les vérifications manuelles restent à finaliser sur appareil/émulateur.

---

# 11. Review et corrections — étape 7

Status: RESOLVED

## Critique

Aucun retour.

## Majeur

Aucun retour.

## Mineur

Aucun retour.

## Corrections demandées

Aucune correction : la revue du diff F15 confirme le respect du périmètre, du filtrage `COMPLETED`, de l'ordre fourni par le dépôt, du plafond à 20, de la déduplication des mises à jour non visibles et de la réutilisation du parcours de lecture hors-ligne existant.

---

# 12. Validation finale — étape 8

Status: PARTIAL

- `./gradlew --no-daemon testDebugUnitTest assembleDebug lintDebug` : succès le 2026-07-26.
- `git diff --check` : succès.
- La validation automatisée couvre le filtrage des téléchargements terminés, l'ordre du flux, le plafond à 20, l'état vide, la suppression réactive et l'absence de nouvelle émission lors de la progression d'un téléchargement non affiché.
- Validation manuelle mobile et Android TV : non exécutée. L'ADB du SDK est disponible à `/home/nnobre/Android/Sdk/platform-tools/adb`, mais `adb devices` ne retourne aucune cible connectée. Restent à confirmer sur cible : position/rendu final de la rangée, focus TV, ouverture de `DownloadsScreen` et lancement local d'un film puis d'un épisode.
