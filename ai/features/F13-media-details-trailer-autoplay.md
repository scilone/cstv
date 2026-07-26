# F13 - Lecture automatique du trailer YouTube sur les fiches de détail (Films/Séries)

## Informations générales

Type:
Feature

Status:
TASK BREAKDOWN

Created:
2026-07-25

Target version:
v1.55.0

Version:
v1.55.0

Date:
2026-07-25

---

# 1. Description

L'utilisateur doit pouvoir visionner la bande-annonce (trailer) d'un film ou d'une série directement depuis sa fiche détaillée (`VodDetailsScreen` et `SeriesDetailsScreen`), de manière automatique et immersive, sur le même modèle que la lecture de bande-annonce déjà présente sur la Hero Card de l'écran d'Accueil (Home).

Après quelques secondes passées sur la fiche, l'image de fond (Backdrop poster) doit s'estomper pour laisser place à la lecture du trailer YouTube en arrière-plan.

---

# 2. Contexte

Actuellement, l'application prend en charge la lecture automatique de trailers YouTube uniquement sur l'écran d'Accueil (`HomeScreen`) au sein de la Hero Card via le composant hautement optimisé `HomeYouTubeTrailerPreview`.

Ce composant contourne les limitations de l'API YouTube standard (erreurs de Referer et de configuration de lecteur sur WebViews Android depuis fin 2025) en :
- Utilisant un agent utilisateur (User-Agent) Desktop pour forcer le lecteur Desktop de YouTube (qui respecte l'instruction `controls=0` sans boutons centraux intrusifs).
- Chargeant l'Iframe YouTube au travers d'un wrapper HTML minimal servi sous un Referer simulé et valide (`https://cstv.app`).
- Appliquant un overscan CSS (`OVERSCAN_PX = 120`) pour clipper le bandeau de titre supérieur et la barre de contrôles inférieure de la vidéo YouTube, offrant un rendu "cover" parfait et propre.

Pour résoudre et récupérer les vidéos de trailers, l'application utilise `GetTrailerPreviewUseCase` qui interroge `TrailerRepository`. Ce repository :
1. Tente d'abord de récupérer le lien de bande-annonce directement depuis le serveur IPTV Xtream Codes via l'info du média (`getVodInfo` ou `getSeriesInfo`), qui contient parfois un champ `youtubeTrailer`.
2. En cas d'absence, il bascule sur une recherche de vidéos sur l'API TMDB (`tmdbFallback`) en utilisant l'identifiant TMDB (`tmdbId`).

---

# 3. Objectif

Rendre l'expérience des fiches de détails de l'application mobile et Android TV extrêmement dynamique et moderne (façon Netflix/Prime Video) en intégrant le composant de lecture automatique de bande-annonce YouTube en arrière-plan du bloc d'en-tête de la fiche détaillée du média.

---

# 4. Hypothèses

- **Réutilisation du composant d'arrière-plan :** Le composant `HomeYouTubeTrailerPreview` (ou une version généralisée comme `YouTubeTrailerPreview`) est parfaitement dimensionné et optimisé pour être intégré sur les écrans de détail. Il assurera un rendu fluide avec une transition en fondu croisé (crossfade) depuis l'image d'affiche (`AsyncImage`).
- **Résolution du TMDB ID manquant :** Sur l'écran d'Accueil, l'identifiant `tmdbId` requis pour la résolution de bande-annonce TMDB est fourni par la liste des tendances de l'API TMDB. Cependant, pour un média quelconque ouvert depuis le catalogue IPTV local, aucun `tmdbId` n'est disponible dans la base de données locale ni dans la réponse de détails Xtream Codes.
  - *Hypothèse technique :* Si le serveur IPTV ne renvoie aucun `youtubeTrailer` et qu'aucun `tmdbId` n'est associé au média, le repository devra effectuer une résolution dynamique en recherchant d'abord le titre et l'année de sortie sur TMDB via de nouveaux endpoints de recherche (`search/movie` et `search/tv`) à ajouter à `TmdbApiService.kt`. Une fois le `tmdbId` résolu, la recherche de bande-annonce standard pourra s'exécuter.
- **Contrôle audio et cycle de vie :** Le trailer sur la fiche de détails devra par défaut démarrer en mode muet (comme sur l'Accueil) pour ne pas agresser l'utilisateur, mais un bouton d'activation/désactivation du son (Mute/Unmute) devra être mis à disposition de l'utilisateur. De plus, la lecture doit immédiatement se couper dès que l'utilisateur quitte la fiche de détails ou lance la lecture plein écran du média principal.
- **Persistance de la résolution :** Le cache de bande-annonce actuel (hérité de F10) vit uniquement en mémoire et disparaît à chaque arrêt du processus. Or la résolution d'un média du catalogue IPTV coûte jusqu'à trois appels réseau (info Xtream, recherche TMDB, vidéos TMDB), et le cas le plus fréquent — aucun trailer disponible — coûte exactement le même prix.
  - *Hypothèse technique :* le résultat de la résolution (identifiant de vidéo retenu, source, identifiant TMDB résolu, absence de trailer) doit être conservé dans la base Room partagée du catalogue, avec une durée de validité, afin qu'une fiche déjà consultée n'émette plus aucun appel réseau. La donnée est attachée au catalogue (non au profil) et doit être invalidée au changement de compte Xtream et lorsqu'une vidéo mémorisée s'avère illisible.

---

# 5. Questions ouvertes

1. **Intégration sur Android TV :** Sur Android TV, l'affichage d'une WebView en arrière-plan de la fiche de détails peut-il poser des problèmes de performance ou de focus (le D-Pad risquant de cibler la WebView par accident si celle-ci n'est pas configurée de manière hermétique) ? Il conviendra de s'assurer que la WebView reste strictement non-focusable (`focusable = false` ou `pointer-events: none`).
2. **Boutons d'interaction :** Doit-on proposer un contrôle d'activation du son ainsi qu'un bouton de relecture (Replay) si le trailer se termine, ou conserve-t-on le comportement de boucle infinie (`loop=1`) sans contrôles apparents ?
3. **Consommation de données / Mode économie :** La lecture automatique de vidéos en arrière-plan sur les fiches de détails consomme de la bande passante. Faut-il introduire une option dans les paramètres de l'application pour activer/désactiver l'autoplay des trailers (ou restreindre cette fonctionnalité uniquement lorsque l'appareil est connecté en Wi-Fi) ?
4. **Mémorisation du résultat :** Faut-il conserver durablement la bande-annonce résolue (et l'absence de bande-annonce) d'une fiche déjà consultée ? Si oui, pendant combien de temps, et que se passe-t-il si la vidéo mémorisée devient indisponible (retirée de YouTube, bloquée dans le pays) ou si le média n'avait pas encore de bande-annonce au moment de la première consultation ?

---

# 6. Spécification fonctionnelle

## User stories

- En tant qu'utilisateur mobile, lorsque je consulte la fiche d'un film ou d'une
  série, je veux voir sa bande-annonce se lancer discrètement dans l'en-tête afin
  de mieux choisir quoi regarder.
- En tant qu'utilisateur Android TV, je veux bénéficier du même aperçu sans que
  la vidéo ne capture le focus ou empêche la navigation D-Pad vers les actions de
  la fiche.
- En tant qu'utilisateur, je veux pouvoir activer ou couper le son du trailer et
  lancer immédiatement le contenu principal sans que l'aperçu continue en arrière-plan.
- En tant qu'utilisateur dont le trailer est indisponible, je veux conserver une
  fiche lisible et entièrement utilisable avec son visuel statique habituel.
- En tant qu'utilisateur qui revient sur une fiche déjà consultée, je veux
  retrouver le même comportement sans nouvelle attente ni consommation de données
  inutile, y compris après avoir redémarré l'application.

## Parcours utilisateur

1. L'utilisateur ouvre la fiche détaillée d'un film ou d'une série sur mobile ou
   Android TV. Le backdrop/poster et toutes les actions habituelles sont affichés
   immédiatement ; aucune vidéo ne démarre à l'ouverture.
2. Après cinq secondes continues sur la même fiche, l'application détermine le
   trailer disponible pour ce média : elle réutilise le résultat déjà mémorisé
   pour ce média s'il existe, sinon elle en cherche un. Toute sortie de la fiche,
   sélection d'un autre média ou lancement de la lecture principale annule cet
   aperçu.
3. Lorsqu'un trailer valide est disponible, il démarre en boucle et sans son dans
   l'arrière-plan de l'en-tête. Le poster s'estompe progressivement vers la vidéo
   sans déplacer le titre, les métadonnées ni les boutons de la fiche.
4. L'utilisateur peut activer ou couper le son via un contrôle explicite. Le
   choix s'applique uniquement à l'aperçu en cours ; ouvrir une autre fiche
   redémarre systématiquement en muet.
5. L'utilisateur peut parcourir normalement les actions, lancer le média,
   ajouter aux favoris ou revenir en arrière. La vidéo n'est jamais une cible de
   clic ou de focus et ne déclenche aucune navigation.
6. Si aucun trailer n'est résolu, ne peut pas démarrer ou s'arrête de façon
   inattendue, le poster reste ou est restauré ; la fiche reste disponible sans
   erreur intrusive.

## Règles métier

- La fonctionnalité couvre uniquement l'en-tête des fiches `Films` et `Séries`.
  Elle ne change ni les lecteurs plein écran, ni les listes, ni les
  téléchargements, ni la lecture automatique d'épisodes. L'aperçu de la Hero de
  l'Accueil garde son comportement visible actuel ; il bénéficie seulement, sans
  changement d'apparence ni de règles, de la mémorisation décrite plus bas et de
  l'oubli d'une bande-annonce devenue illisible.
- Le délai est de cinq secondes de présence stable sur une fiche. Un changement de
  média, un retour, le passage en arrière-plan de l'application ou le lancement
  du contenu principal empêche le démarrage ou arrête immédiatement le trailer.
- Une seule tentative automatique est faite par ouverture de fiche et par média.
  Un échec ne produit ni boucle de tentatives ni message bloquant ; une nouvelle
  ouverture de la fiche autorise une nouvelle tentative.
- Le résultat de la recherche est mémorisé durablement pour le média concerné,
  y compris lorsqu'aucune bande-annonce n'existe. Une fiche déjà consultée
  affiche son aperçu sans nouvelle attente perceptible et sans nouvelle
  sollicitation des services externes, y compris après redémarrage de
  l'application ou hors connexion pour la partie recherche.
- Cette mémorisation n'est jamais définitive : elle est réévaluée après un délai
  raisonnable, plus court lorsqu'aucune bande-annonce n'avait été trouvée que
  lorsqu'une bande-annonce l'avait été. Un média sans trailer aujourd'hui peut
  donc en obtenir un plus tard sans action de l'utilisateur.
- Une bande-annonce mémorisée qui ne peut plus être lue est oubliée
  immédiatement : la fiche revient à son visuel statique et la consultation
  suivante relance une recherche complète.
- La mémorisation est commune à tous les profils (donnée de catalogue, au même
  titre que les films et séries) et n'expose aucune information personnelle. Elle
  est entièrement effacée lors d'un changement de compte Xtream, connexion comme
  déconnexion.
- Un trailer est éligible seulement s'il est associé au film ou à la série
  affichée et peut être lu sur YouTube. Le choix de sa source doit rester
  transparent pour l'utilisateur ; aucun identifiant de fournisseur, URL ou
  erreur réseau n'est affiché.
- Le trailer démarre toujours muet et tourne en boucle tant que la fiche reste
  active. Le contrôle sonore est accessible sur mobile et focusable au D-Pad sur
  TV, avec un libellé d'accessibilité indiquant l'action disponible.
- Sur Android TV, le focus reste exclusivement sur les contrôles de la fiche ;
  les touches D-Pad, Retour et les actions Lecture/Favori conservent leur
  comportement actuel.
- Le trailer est un décor d'en-tête : il ne masque pas les informations essentielles
  et respecte la lisibilité des titres, métadonnées, boutons, scrims et états de
  focus déjà présents.
- Aucune préférence utilisateur, restriction Wi-Fi ou mode économie de données
  n'est introduit dans F13. L'autoplay s'applique dans les mêmes conditions aux
  connexions prises en charge par l'application ; ce réglage potentiel constitue
  une évolution distincte.

## Critères d'acceptation

- Étant donné la fiche d'un film ou d'une série ayant un trailer disponible,
  lorsqu'elle reste visible cinq secondes, alors le trailer apparaît en arrière-
  plan, démarre muet et se boucle sans déplacer le contenu de la fiche.
- Étant donné une fiche ouverte depuis moins de cinq secondes, lorsqu'elle est
  quittée ou que la lecture principale est lancée, alors aucun trailer ne démarre
  ou ne reste actif après la transition.
- Étant donné un trailer déjà visible, lorsque l'utilisateur quitte la fiche,
  passe en arrière-plan ou lance le média principal, alors son son et sa lecture
  s'arrêtent immédiatement.
- Étant donné un trailer visible, lorsque l'utilisateur active le son puis ouvre
  une autre fiche, alors le nouveau trailer démarre à nouveau muet.
- Étant donné un média sans trailer, une résolution impossible ou une erreur de
  lecture, alors le poster est conservé ou restauré, aucune erreur intrusive
  n'est affichée et les actions de la fiche restent fonctionnelles.
- Étant donné une fiche Android TV, lorsque l'utilisateur navigue au D-Pad,
  alors la vidéo n'obtient jamais le focus et tous les contrôles existants restent
  atteignables et lisibles.
- Étant donné un trailer en cours, lorsque l'utilisateur utilise Retour, Favori,
  Lecture ou une autre action de fiche, alors l'action conserve son comportement
  actuel et n'est pas interceptée par l'aperçu.
- Étant donné une fiche déjà consultée dont la bande-annonce a été mémorisée,
  lorsqu'elle est rouverte, y compris après redémarrage de l'application, alors
  l'aperçu démarre selon les mêmes règles sans nouvelle recherche et sans appel
  aux services externes.
- Étant donné un média mémorisé comme dépourvu de bande-annonce, lorsqu'il est
  rouvert avant l'expiration du délai de réévaluation, alors aucune recherche
  n'est relancée et la fiche affiche directement son visuel statique.
- Étant donné une bande-annonce mémorisée devenue illisible, lorsqu'elle échoue à
  la lecture, alors le poster est restauré et la consultation suivante relance
  une recherche complète.
- Étant donné un changement de compte Xtream, lorsque l'utilisateur ouvre une
  fiche, alors aucune bande-annonce mémorisée pour l'ancien compte n'est
  réutilisée.

## Cas limites et gestion des erreurs

- Si la fiche change pendant la recherche ou le chargement, le résultat tardif de
  l'ancien média est ignoré : il ne doit jamais apparaître sur la nouvelle fiche.
- Si l'application passe en arrière-plan avant ou pendant l'aperçu, aucune audio
  ni vidéo ne doit continuer ; le comportement normal de la fiche est rétabli au
  retour selon son cycle de vie.
- Si le poster/backdrop est absent, l'en-tête conserve son fond de repli existant
  jusqu'à ce qu'un trailer valide soit effectivement prêt à être affiché.
- Si YouTube, TMDB ou le serveur IPTV est indisponible, lent ou retourne une
  réponse incomplète, la fiche ne présente pas d'erreur technique à l'utilisateur
  et ne bloque pas ses autres données ou actions.
- Si plusieurs interactions rapides surviennent (ouverture, retour, nouvelle
  fiche, Lecture), seule la fiche actuellement visible peut posséder un aperçu ;
  aucun lecteur ni son résiduel ne subsiste.
- Les contrôles du trailer respectent l'accessibilité : description explicite,
  cible utilisable et état sonore compréhensible sur mobile comme sur TV.
- Si la mémorisation est indisponible ou échoue (lecture ou écriture), la fiche
  se comporte comme lors d'une première consultation : recherche normale, aucun
  message d'erreur, aucun blocage des autres données ou actions.
- La mémorisation ne doit jamais faire apparaître la bande-annonce d'un autre
  média : elle est liée à l'identité du média dans le catalogue actif, et un
  changement de compte l'efface avant toute réutilisation.

## Décisions fonctionnelles issues des questions ouvertes

1. **Android TV :** F13 inclut Android TV, avec un aperçu strictement décoratif
   qui ne peut jamais prendre le focus ni recevoir les interactions D-Pad.
2. **Contrôles :** un unique contrôle Son/Couper le son est fourni. Il n'y a pas
   de bouton Replay : la boucle continue couvre ce besoin sans alourdir la fiche.
3. **Données :** aucune préférence d'autoplay ni restriction Wi-Fi n'est ajoutée
   dans cette fonctionnalité ; le sujet est explicitement hors périmètre F13.
4. **Mémorisation :** le résultat de résolution est conservé durablement, absence
   de bande-annonce comprise, avec réévaluation automatique après **7 jours** pour
   un résultat négatif et **30 jours** pour une bande-annonce trouvée. Une vidéo
   mémorisée qui échoue à la lecture est oubliée sur-le-champ. La mémorisation est
   partagée par tous les profils et effacée à tout changement de compte Xtream.

---

# 7. Spécification technique

## 7.1 Vue d'ensemble

F13 réutilise intégralement la chaîne trailer construite par F10 (résolution
`TrailerRepository` + lecteur WebView `HomeYouTubeTrailerPreview`) et l'étend sur
trois points seulement :

1. **Généralisation du lecteur** : le composable et l'état d'UI de l'aperçu
   quittent le package `presentation/home` pour `presentation/components`, sans
   changement de comportement de rendu.
2. **Résolution du `tmdbId` manquant** : les fiches de détail proviennent du
   catalogue Xtream, qui ne fournit aucun `tmdbId`. Le repository gagne une étape
   de résolution par recherche TMDB (`search/movie`, `search/tv`) sur titre + année,
   utilisée uniquement quand le panel IPTV ne fournit pas de `youtube_trailer` et
   qu'aucun `tmdbId` n'est connu.
3. **Cache persistant Room** : le résultat de résolution (identifiant vidéo,
   source, `tmdbId` résolu, ou absence de trailer) est écrit en base avec un TTL,
   de sorte qu'une fiche déjà consultée n'émette plus aucun appel réseau, même
   après redémarrage du processus.

L'orchestration (délai de 5 s, cycle de vie, annulation, réponse obsolète) est
recopiée du modèle Accueil : minuterie et observation du `Lifecycle` côté
composable, machine à états côté ViewModel.

## 7.2 Composants impactés et nouveaux composants

### Nouveaux

| Fichier | Rôle |
| --- | --- |
| `presentation/components/YouTubeTrailerPreview.kt` | Lecteur WebView partagé. Reprise à l'identique de `HomeYouTubeTrailerPreview` (UA desktop, wrapper HTML sous `https://cstv.app`, overscan 120 px, cover poster 5 s, `postMessage` mute/unMute), rendue `public` et renommée `YouTubeTrailerPreview`, plus neutralisation explicite du focus (§7.7). |
| `presentation/components/TrailerPreviewUiState.kt` | Déplacement de l'état `Poster / Preparing / Playing / Failed` hors de `HomeViewModel.kt`, pour être consommé par Home, VOD et Séries. |
| `presentation/components/MediaDetailsTrailerBackdrop.kt` | Couche de fond des fiches de détail : poster ↔ trailer, minuterie 5 s, observation `Lifecycle`, scrim de lisibilité, remontée des callbacks `onContextReady` / `onContextEnded` / `onPlaybackFailed`. |
| `domain/model/TrailerLookupMatcher.kt` | Objet pur : choix du meilleur résultat `search/movie`/`search/tv` (similarité de titre + compatibilité d'année). Testable sans réseau. |
| `data/local/entity/TrailerCacheEntity.kt` | Résultat de résolution persisté (table `trailer_cache`). |
| `data/local/dao/TrailerCacheDao.kt` | Lecture/écriture/suppression/purge du cache persistant. |
| `domain/util/TimeProvider.kt` + `data/util/SystemTimeProvider.kt` | Horloge injectable, indispensable pour tester les TTL sans attendre (et conforme au piège Mockito d'AGENTS.md : on mocke une interface, jamais une classe à retour primitif). |
| `domain/usecase/InvalidateTrailerPreviewUseCase.kt` | Oubli d'une bande-annonce devenue illisible, appelé depuis les ViewModels. |

### Modifiés

| Fichier | Modification |
| --- | --- |
| `domain/model/TrailerPreview.kt` | `TrailerMedia.tmdbId` passe en `Int?` ; ajout de `title: String?` et `releaseYear: Int?` pour la résolution dynamique. |
| `data/remote/api/TmdbApiService.kt` | Ajout de `searchMovie(...)` et `searchSeries(...)`. |
| `data/repository/TrailerRepositoryImpl.kt` | Recherche TMDB quand `tmdbId == null` + lecture/écriture du cache persistant + invalidation sur échec de lecture. |
| `domain/repository/TrailerRepository.kt` | Ajout de `invalidate(media)` ; `clearSessionCache()` purge désormais mémoire **et** table. |
| `data/local/db/AppDatabase.kt` | Entité `TrailerCacheEntity`, DAO, version **17 → 18**. |
| `data/local/db/Migrations.kt` | `MIGRATION_17_18` (`CREATE TABLE trailer_cache`) ajoutée à `ALL_MIGRATIONS`. |
| `di/AppModule.kt` | `provideTrailerCacheDao`, liaison `TimeProvider`, scope applicatif pour la purge, câblage du repository. |
| `data/repository/AuthRepositoryImpl.kt` | Inchangé dans ses appels : `clearSessionCache()` couvre déjà connexion et déconnexion (§7.5). |
| `presentation/home/HomeViewModel.kt` | Import de `TrailerPreviewUiState` déplacé ; `toTrailerMedia()` renseigne `title`/`releaseYear` (facultatif, garde le `tmdbId` connu). |
| `presentation/home/components/HomeTrendingCarousel.kt` | Appelle `YouTubeTrailerPreview` au lieu de `HomeYouTubeTrailerPreview`. |
| `presentation/home/components/HomeYouTubeTrailerPreview.kt` | Supprimé (contenu déplacé). |
| `presentation/vod/VodState.kt`, `presentation/series/SeriesState.kt` | Ajout de `trailerPreview: TrailerPreviewUiState = Poster`. |
| `presentation/vod/VodViewModel.kt`, `presentation/series/SeriesViewModel.kt` | Ajout de `GetTrailerPreviewUseCase` + `InvalidateTrailerPreviewUseCase` et des méthodes `startTrailerPreview()` / `cancelTrailerPreview()` / `reportTrailerPlaybackFailure()` ; annulation dans `selectStream()`. |
| `presentation/home/HomeViewModel.kt` | `reportTrailerPlaybackFailure` invalide aussi l'entrée persistée (même correctif pour l'Accueil). |
| `presentation/vod/VodDetailsScreen.kt`, `presentation/series/SeriesDetailsScreen.kt` | La couche backdrop actuelle (`AsyncImage` floutée) devient `MediaDetailsTrailerBackdrop` ; ajout du bouton Son dans la barre supérieure. |
| `presentation/navigation/NavGraph.kt` | Câblage des nouveaux paramètres des deux écrans de détail. |

`AppNavGraph` est le point d'entrée unique mobile **et** TV (`MainActivity` passe
`isTv` à `AppNavGraph`, les fiches de détail ne sont pas dupliquées côté TV) : le
piège « double système de navigation » d'AGENTS.md ne s'applique pas ici, un seul
câblage suffit. À vérifier au moment de l'implémentation.

## 7.3 Contrat de domaine

`TrailerMedia` doit désormais porter de quoi résoudre un média **sans** `tmdbId` :

```kotlin
sealed interface TrailerMedia {
    val catalogId: Int
    val tmdbId: Int?
    val title: String?
    val releaseYear: Int?

    data class Movie(
        override val catalogId: Int,
        override val tmdbId: Int? = null,
        override val title: String? = null,
        override val releaseYear: Int? = null
    ) : TrailerMedia

    data class Series(...) : TrailerMedia
}
```

Justification : `TrailerMedia` est déjà la clé du cache de session du repository.
En gardant une seule `data class` par type de média, la clé reste stable pour un
même titre (les champs sont dérivés des détails, invariants dans la session) et
Accueil comme fiches partagent la même chaîne de résolution. `tmdbId` devient
nullable ; le chemin Accueil continue de le fournir et court-circuite la
recherche.

`TrailerSource`, `TrailerPreview` et `GetTrailerPreviewUseCase` sont inchangés.

## 7.4 Résolution de la source (repository)

Ordre appliqué par `TrailerRepositoryImpl.getTrailerPreview(media)` :

1. **Cache mémoire** (`LinkedHashMap` LRU 32 entrées, valeurs `null`
   mémorisées) — inchangé. Il reste devant Room : sur l'Accueil, le carrousel
   interroge à chaque changement de page, inutile de toucher le disque.
2. **Cache persistant Room (nouveau)** : entrée `trailer_cache` non expirée →
   résultat rendu immédiatement, aucun appel réseau. Une entrée expirée est
   ignorée et supprimée (§7.5).
3. **Xtream** : `getVodInfo` / `getSeriesInfo` via `XtreamRequestGate`, champ
   `youtube_trailer` passé à `normalizeYouTubeId` — inchangé.
4. **TMDB direct** : si `media.tmdbId != null`, `getMovieVideos` /
   `getSeriesVideos` — inchangé.
5. **TMDB par recherche (nouveau)** : si `media.tmdbId == null` et
   `media.title` non vide, `searchMovie` / `searchSeries`, sélection du meilleur
   résultat par `TrailerLookupMatcher`, puis reprise de l'étape 4 avec le
   `tmdbId` résolu.

Le résultat des étapes 3 à 5 est écrit en base (§7.5) **et** en mémoire, y compris
le résultat négatif. Toute étape en échec renvoie `null` et laisse le poster en
place ; `CancellationException` est toujours relancée. La mutuelle
`resolutionMutex` existante couvre les nouvelles étapes sans modification.

Nuance importante : un **échec réseau** (panel injoignable, timeout, TMDB
indisponible) ne doit **pas** être écrit comme « pas de trailer » — sinon une
coupure réseau condamne le média pour 7 jours. Seule une réponse exploitable
concluant à l'absence de bande-annonce est persistée ; l'exception, elle, ne
remplit que le cache mémoire de la session courante.

### Endpoints ajoutés

```kotlin
@GET("search/movie")
suspend fun searchMovie(
    @Query("query") query: String,
    @Query("api_key") apiKey: String,
    @Query("language") language: String = "fr-FR",
    @Query("year") year: Int? = null,
    @Query("include_adult") includeAdult: Boolean = false
): TmdbTrendingResponseDto

@GET("search/tv")
suspend fun searchSeries(
    @Query("query") query: String,
    @Query("api_key") apiKey: String,
    @Query("language") language: String = "fr-FR",
    @Query("first_air_date_year") year: Int? = null,
    @Query("include_adult") includeAdult: Boolean = false
): TmdbTrendingResponseDto
```

`TmdbTrendingResponseDto` / `TmdbTrendingItemDto` sont réutilisés tels quels :
la charge utile de `search/*` expose les mêmes champs (`id`, `title`/`name`,
`release_date`/`first_air_date`) et `id` y est déjà déclaré `Any?`, donc tolérant
au panel qui renverrait une chaîne. Aucune nouvelle interface Retrofit n'est
créée : la règle `-keep interface com.cstv.app.data.remote.api.TmdbApiService { *; }`
de `proguard-rules.pro` couvre déjà ces méthodes. `year`/`first_air_date_year`
sont omis de la requête quand l'année locale est inconnue (paramètre `null`).

### Sélection du résultat (`TrailerLookupMatcher`, objet pur)

- Requête envoyée : `TitleNormalizer.normalize(title)` — retire `[MULTI]`, `VF`,
  `1080p`, l'année accolée, etc., déjà utilisé par le rapprochement TMDB de F1/F9.
- Filtrage : similarité `ApproximateTitleMatcher.computeSimilarityNormalized`
  ≥ **0,8** entre le titre normalisé local et le titre normalisé du résultat.
- Année : si l'année locale et l'année du résultat sont toutes deux connues,
  tolérance de **± 1 an** (mêmes règles que `TmdbCatalogMatcher.isYearCompatible`,
  qui gère les décalages de date de sortie France/US). Année inconnue d'un côté →
  pas de filtre.
- Départage : meilleure similarité, puis ordre TMDB (pertinence) en cas d'égalité.
- Aucun candidat retenu → `null` (poster conservé), aucune requête `videos`.

L'année locale est extraite par un parsing défensif du préfixe `yyyy` de
`VodDetails.releaseDate` / `SeriesDetails.releaseDate` (chaînes libres selon les
panels : `2019-06-12`, `2019`, vide ou absente), avec repli sur
`VodStream.releaseYear` / `SeriesStream.releaseYear` déjà présents dans le
catalogue local.

## 7.5 Cache persistant (Room)

### Entité

```kotlin
@Entity(tableName = "trailer_cache", primaryKeys = ["mediaType", "catalogId"])
data class TrailerCacheEntity(
    val mediaType: String,       // "movie" | "series"
    val catalogId: Int,          // streamId / seriesId du panel actif
    val videoId: String?,        // null = résolu comme "pas de bande-annonce"
    val source: String?,         // "XTREAM" | "TMDB" | null
    val resolvedTmdbId: Int?,    // évite de refaire search/* à l'expiration
    val resolvedAt: Long         // epoch ms, base du TTL
)
```

Décisions :

- **On ne stocke que le gagnant**, pas les deux sources. L'ordre de priorité est
  fixe (Xtream d'abord) et rien ne consomme le perdant : deux colonnes seraient
  deux vérités à maintenir cohérentes pour zéro usage. `source` sert au
  diagnostic et à une éventuelle invalidation ciblée.
- **`resolvedTmdbId` est conservé même quand `videoId` est `null`** : à
  l'expiration d'un résultat négatif, la réévaluation repart directement sur
  `videos` sans repayer la recherche.
- **Pas de `profileId`** : donnée de catalogue, partagée par tous les profils,
  conforme à la règle d'AGENTS.md (seules les données utilisateur sont scopées).
- La clé primaire est l'identité du média dans le panel **actif** ; les
  `streamId` se recyclant d'un panel à l'autre, la purge au changement de compte
  est obligatoire (voir plus bas).

### TTL et invalidation

| Situation | Traitement |
| --- | --- |
| `videoId != null`, âge < 30 j | Servie directement, aucun appel réseau |
| `videoId == null`, âge < 7 j | « Pas de trailer » servi directement, aucun appel réseau |
| Entrée expirée | Ligne supprimée, résolution complète relancée, nouvelle ligne écrite |
| Échec de lecture (`onPlaybackError`) | `invalidate(media)` : ligne supprimée **immédiatement**, entrée mémoire retirée ; la consultation suivante re-résout |
| Échec réseau pendant la résolution | Rien n'est écrit en base (§7.4) |
| Changement de compte Xtream | Table entièrement vidée |

TTL négatif plus court que positif parce que les deux échecs n'ont pas la même
nature : « TMDB n'a pas encore la bande-annonce » se corrige tout seul en
quelques jours, alors qu'un `videoId` valide ne change pas — il disparaît
(retrait YouTube, blocage géographique), et ce cas-là est traité par
l'invalidation sur échec de lecture, pas par le TTL.

L'échec de lecture est la raison pour laquelle `reportTrailerPlaybackFailure`
cesse d'être purement cosmétique : sans lui, un trailer mort resterait figé
30 jours. Le correctif vaut aussi pour l'Accueil, qui passe par le même
repository.

### Horloge

`TimeProvider` (interface `domain/util`) + `SystemTimeProvider`
(`System.currentTimeMillis()`) injecté dans `TrailerRepositoryImpl`. Sans cette
indirection les TTL ne sont pas testables ; le projet appelle aujourd'hui
`System.currentTimeMillis()` directement dans les repositories, F13 introduit
l'abstraction pour son propre besoin sans réécrire l'existant.

### Migration

`AppDatabase` est **actuellement en version 17** (`AppDatabase.kt` ; la valeur 16
citée dans AGENTS.md est périmée) → nouvelle version **18** :

```kotlin
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS trailer_cache (
                mediaType TEXT NOT NULL,
                catalogId INTEGER NOT NULL,
                videoId TEXT,
                source TEXT,
                resolvedTmdbId INTEGER,
                resolvedAt INTEGER NOT NULL,
                PRIMARY KEY(mediaType, catalogId)
            )
        """.trimIndent())
    }
}
```

Création pure, aucune copie de données, aucun risque de perte ; ajoutée à
`ALL_MIGRATIONS`. Le fallback destructif reste proscrit.

### Purge au changement de compte

`AuthRepositoryImpl.saveCredentials()` et `clearCredentials()` appellent déjà
`trailerRepository.clearSessionCache()`, qui doit désormais vider mémoire **et**
table. Ces deux méthodes ne sont pas `suspend` et les rendre `suspend`
propagerait la contrainte jusqu'aux ViewModels de connexion : la purge Room est
donc lancée en « tire-et-oublie » sur un `CoroutineScope` applicatif injecté
(`SupervisorJob() + Dispatchers.IO`, fourni par `AppModule`), le vidage mémoire
restant synchrone et immédiat. Conséquence assumée : entre l'appel et la fin de
la purge disque, seule la mémoire garantit l'isolement — elle est justement vidée
en premier, donc aucune lecture ne peut servir une entrée de l'ancien compte.

## 7.6 Orchestration côté ViewModel

`VodViewModel` et `SeriesViewModel` reprennent la machine à états de
`HomeViewModel` :

```kotlin
fun startTrailerPreview(media: TrailerMedia) {
    if (media == activeTrailerMedia) return
    trailerJob?.cancel()
    activeTrailerMedia = media
    _state.update { it.copy(trailerPreview = TrailerPreviewUiState.Preparing) }
    trailerJob = viewModelScope.launch {
        val preview = runCatching { getTrailerPreviewUseCase(media) }
            .getOrElse { if (it is CancellationException) throw it else null }
        if (activeTrailerMedia == media) {           // réponse obsolète ignorée
            _state.update {
                it.copy(trailerPreview = preview?.let(TrailerPreviewUiState::Playing)
                    ?: TrailerPreviewUiState.Poster)
            }
        }
    }
}

fun cancelTrailerPreview() { /* annule, remet Poster, vide activeTrailerMedia */ }
fun reportTrailerPlaybackFailure(media: TrailerMedia) { /* Failed si média courant */ }
```

`selectStream(...)` appelle `cancelTrailerPreview()` : changer de média pendant
une résolution ne peut jamais afficher le trailer du précédent.

## 7.7 Orchestration côté UI

`MediaDetailsTrailerBackdrop` porte toute la temporisation :

- `LaunchedEffect(mediaKey, lifecycleStarted)` : remet l'état local à zéro,
  `delay(TRAILER_START_DELAY_MS = 5_000)` puis `onContextReady()` — c'est cet
  appel qui déclenche la résolution réseau. Un changement de `mediaKey` ou un
  `ON_STOP` annule la coroutine avant l'échéance : aucune requête n'est émise.
- `DisposableEffect` : observateur `Lifecycle` (`ON_START` / `ON_STOP`) et
  `onDispose { onContextEnded() }`. Sortie de fiche, retour arrière et navigation
  vers `vod_player` / `series_player` sortent le composable de la composition →
  `onContextEnded()` → état `Poster` → la `WebView` est libérée par le
  `DisposableEffect` du lecteur (`about:blank` + `stopLoading()` + `destroy()`),
  donc plus aucun son résiduel.
- Rendu : `AsyncImage` du backdrop dans le traitement actuel (flou 20 dp,
  alpha 0,18) tant que l'état n'est pas `Playing` ; quand il l'est,
  `YouTubeTrailerPreview` plein cadre sous un scrim noir uniforme
  (`alpha 0,62`) qui rétablit une luminance comparable au backdrop flouté. Le
  fondu est assuré par le cover poster interne au lecteur (5 s) puis
  `animateFloatAsState` ; aucun élément de la fiche ne bouge, la couche vit dans
  le même `Box` de fond que l'`AsyncImage` remplacée.
- État `Failed` ou `Poster` après une lecture : retour immédiat au backdrop,
  aucun message utilisateur.

Le bouton Son est rendu par l'écran de détail, dans la `Row` supérieure qui porte
déjà le bouton Retour, uniquement quand l'état est `Playing` et que le lecteur a
signalé `onRevealed` — mêmes règles que le carrousel Accueil. `IconButton`
Material 3 sur mobile, `androidx.tv.material3.Button` sur TV pour rester dans
l'ordre de focus D-Pad. `contentDescription` : « Activer le son de l'aperçu » /
« Couper le son de l'aperçu ». L'état muet est local au composable et remis à
`true` à chaque changement de `videoId` : ouvrir une autre fiche redémarre en
muet, sans stockage.

## 7.8 Focus, interactions et Android TV

- La `WebView` est instanciée avec `isFocusable = false`,
  `isFocusableInTouchMode = false`,
  `descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS`, et le composable
  applique `Modifier.focusProperties { canFocus = false }`. Le D-Pad ne peut donc
  jamais l'atteindre.
- L'`<iframe>` conserve `pointer-events: none` (déjà présent dans le wrapper HTML).
- La couche trailer reste **sous** la colonne de contenu (`verticalScroll`,
  `fillMaxSize`) des deux écrans de détail : les gestes tactiles et les touches
  D-Pad continuent d'être servis par le contenu, sans overlay cliquable
  supplémentaire (contrairement à l'Accueil où la carte est elle-même cliquable).
- Aucun changement d'ordre de composition en dehors de l'ajout du bouton Son :
  Retour, Lecture, Reprendre, Favori, Note et Téléchargement gardent leur place
  et leur comportement.

## 7.9 Performances, réseau, compatibilité

- **Une seule `WebView` vivante** : Accueil et fiche de détail ne sont jamais
  composés simultanément (destinations distinctes du `NavHost`) ; la sortie de
  fiche détruit l'instance.
- **Coût réseau** : au pire 1 appel Xtream + 1 `search/*` + 1 `videos` par média,
  déclenché seulement après 5 s de présence stable, et **une seule fois par
  période de TTL** grâce au cache Room — y compris pour le cas majoritaire d'un
  catalogue IPTV, le média sans bande-annonce, qui coûtait sinon les trois appels
  à chaque ouverture. Les appels Xtream passent par `XtreamRequestGate`, inchangé.
- **Coût disque** : une ligne d'une trentaine d'octets par média consulté, écrite
  une fois par TTL, sur la base catalogue existante. Aucune purge périodique
  n'est prévue : le volume est borné par le nombre de fiches réellement ouvertes
  et la table est vidée à chaque changement de compte.
- **Accès disque hors du thread principal** : DAO `suspend`, appelé depuis le
  repository sur les dispatchers d'E/S ; le cache mémoire absorbe les lectures
  répétées du carrousel Accueil.
- **Clé TMDB absente** : `tmdbApiKey.isBlank()` court-circuite déjà les étapes
  TMDB → poster conservé, aucun message.
- **Min SDK 21** : `WebView` + autoplay muet sans geste utilisateur
  (`mediaPlaybackRequiresUserGesture = false`) sont déjà en production via F10 sur
  le même parc, aucune API nouvelle n'est introduite.
- **Sécurité** : aucune donnée d'identification ne transite par la `WebView` ; le
  wrapper HTML est construit localement avec un `videoId` validé par
  `normalizeYouTubeId` (11 caractères `[A-Za-z0-9_-]`), ce qui interdit toute
  injection dans le HTML généré.

## 7.10 Risques techniques

| Risque | Impact | Traitement |
| --- | --- | --- |
| Faux positif de recherche TMDB (mauvais trailer sur la fiche) | Fort côté perception utilisateur | Seuil de similarité 0,8 + compatibilité d'année, réutilisation d'objets déjà éprouvés (F1/F9) ; en cas de doute, `null` plutôt qu'un trailer approximatif |
| Titres IPTV très bruités (tags, langue, saison) | Recherche infructueuse | `TitleNormalizer` déjà rodé sur le catalogue ; échec silencieux, poster conservé |
| `WebView` en fond d'un écran scrollable sur TV d'entrée de gamme | Saccades au scroll | Aucune transformation Compose coûteuse appliquée à la vue (pas de `blur` sur la `WebView`), scrim statique, instance unique |
| Régression Accueil lors du déplacement du composable | Trailer Accueil cassé | Déplacement sans modification de logique de rendu ; tests Accueil existants rejoués |
| Échec réseau persisté comme « pas de trailer » | Média muet pendant 7 jours à cause d'une coupure passagère | Seules les réponses exploitables sont écrites ; les exceptions ne remplissent que le cache mémoire (§7.4) |
| Recyclage des `streamId` entre deux panels Xtream | Bande-annonce du mauvais film après changement de compte | Purge complète de la table dans `clearSessionCache()`, déjà appelée à la connexion et à la déconnexion ; mémoire vidée en premier |
| Trailer mémorisé retiré de YouTube | Fiche figée sur une vidéo morte jusqu'au TTL | `invalidate(media)` sur échec de lecture, branché sur `reportTrailerPlaybackFailure` (Accueil compris) |
| Migration Room supplémentaire | Perte de cache/favoris si mal écrite | `CREATE TABLE` pur, sans copie ni modification de table existante ; relecture manuelle du SQL (le projet n'a pas d'`androidTest`) |

## 7.11 Lecture de la règle « une nouvelle tentative par ouverture »

La règle fonctionnelle « une nouvelle ouverture de la fiche autorise une nouvelle
tentative » interdit une boucle de tentatives et un état d'erreur collant ; elle
n'impose pas de réémettre les trois appels réseau à chaque ouverture. Le cache
persistant sert un résultat déjà connu, et la réévaluation est garantie par trois
mécanismes complémentaires : le TTL (7 j négatif / 30 j positif), l'invalidation
immédiate sur échec de lecture, et la purge au changement de compte. Aucun état
n'est donc définitif, et l'utilisateur ne subit ni attente ni consommation
réseau inutile sur une fiche déjà vue.

## 7.12 Tests à écrire

**`TrailerRepositoryImplTest` (existant, à compléter)**
- `tmdbId` présent → aucun appel à `searchMovie`/`searchSeries`.
- `tmdbId` absent + titre → `searchMovie` appelé, `tmdbId` du meilleur résultat
  utilisé pour `getMovieVideos`.
- Résultats de recherche vides / similarité insuffisante / année incompatible →
  `null`, aucun appel `videos`.
- `youtube_trailer` Xtream présent → aucun appel TMDB (non-régression F10).
- Échec réseau de la recherche → `null`, pas de propagation d'exception.
- Résultat négatif mémorisé : deuxième appel identique sans nouvel appel réseau.

**Cache persistant (dans `TrailerRepositoryImplTest`, DAO simulé + `TimeProvider` contrôlé)**
- Entrée positive non expirée → résultat servi, zéro appel Xtream et TMDB.
- Entrée négative non expirée → `null` servi, zéro appel réseau.
- Entrée positive de plus de 30 j / négative de plus de 7 j → supprimée, chaîne
  complète relancée, nouvelle ligne écrite avec le `resolvedAt` courant.
- Résolution réussie → écriture de `videoId`, `source`, `resolvedTmdbId`.
- Résolution concluant à l'absence de trailer → écriture d'une ligne `videoId = null`.
- **Échec réseau → aucune écriture en base** (cas de non-régression critique).
- Réévaluation d'une entrée négative expirée disposant d'un `resolvedTmdbId` →
  aucun nouvel appel `search/*`.
- `invalidate(media)` → ligne supprimée et entrée mémoire retirée ; l'appel
  suivant re-résout.
- `clearSessionCache()` → mémoire vidée **et** table purgée.
- Erreur du DAO (lecture ou écriture) → comportement identique à une première
  consultation, aucune exception remontée à l'appelant.

**Migration `MIGRATION_17_18`**
- SQL relu manuellement contre l'entité (pas d'`androidTest` dans le projet, cf.
  AGENTS.md) ; vérification que `ALL_MIGRATIONS` contient la nouvelle migration
  et que la version de `AppDatabase` est bien 18.

**`TrailerLookupMatcherTest` (nouveau, objet pur)**
- Sélection par meilleure similarité ; égalité tranchée par l'ordre TMDB.
- Tolérance ± 1 an ; année inconnue d'un côté → pas de filtre.
- Titre bruité (`Le Film [MULTI] 1080p (2019)`) rapproché du titre TMDB propre.
- Aucun candidat au-dessus du seuil → `null`.

**`VodViewModelTest` / `SeriesViewModelTest` (nouveaux cas)**
- `startTrailerPreview` → `Preparing` puis `Playing` sur succès, `Poster` sur
  résultat `null`.
- Réponse tardive d'un média qui n'est plus actif → état inchangé.
- `cancelTrailerPreview` → `Poster` et job annulé.
- `reportTrailerPlaybackFailure` du média courant → `Failed` ; d'un autre média →
  état inchangé.
- `selectStream` d'un autre titre → l'aperçu précédent est annulé.

**Parsing de l'année**
- `2019-06-12`, `2019`, `""`, `null`, valeur non numérique → année attendue ou
  `null`, sans exception.

Les tests UI Compose ne sont pas couverts (politique AGENTS.md) : le focus TV et
le rendu sont validés manuellement à l'étape 8.

---

# 8. Architecture

## Flux de données

```
VodDetailsScreen / SeriesDetailsScreen
        │  (composition, média affiché)
        ▼
MediaDetailsTrailerBackdrop
        │  delay 5 s + Lifecycle STARTED
        ▼
onContextReady(TrailerMedia)          onContextEnded() / onPlaybackFailed()
        │                                        │
        ▼                                        ▼
VodViewModel / SeriesViewModel  ── state.trailerPreview (Poster/Preparing/Playing/Failed)
        │
        ▼
GetTrailerPreviewUseCase
        │
        ▼
TrailerRepositoryImpl
        │
        ├─ cache mémoire (LRU 32, nulls inclus)
        ├─ cache Room trailer_cache (TTL 30 j positif / 7 j négatif)
        ├─ Xtream getVodInfo / getSeriesInfo → youtube_trailer
        ├─ TMDB videos (tmdbId connu)
        └─ TMDB search/movie|search/tv → TrailerLookupMatcher → TMDB videos
                                    │
                                    ├──► écriture trailer_cache (résultat exploitable
                                    │     uniquement, absence de trailer comprise)
                                    ▼
                        TrailerPreview(media, YouTube(videoId))
                                    │
                                    ▼
                        YouTubeTrailerPreview (WebView, muet, boucle)
```

## Responsabilités

- **`MediaDetailsTrailerBackdrop`** : quand l'aperçu a le droit d'exister (délai,
  cycle de vie, présence en composition) et comment il se substitue visuellement
  au backdrop. Ne connaît ni le réseau ni TMDB.
- **`VodViewModel` / `SeriesViewModel`** : état de l'aperçu pour l'écran,
  annulation, rejet des réponses obsolètes, signalement d'un échec de lecture.
  Aucune logique de résolution.
- **`GetTrailerPreviewUseCase` / `InvalidateTrailerPreviewUseCase`** : points
  d'entrée du domaine — obtenir un aperçu, oublier un aperçu devenu illisible.
- **`TrailerRepositoryImpl`** : stratégie de résolution multi-sources, hiérarchie
  de caches (mémoire → Room → réseau), politique de TTL et d'écriture, protection
  du panel via `XtreamRequestGate`.
- **`TrailerCacheDao`** : accès à la table, sans règle métier — la décision
  « entrée encore valide ? » appartient au repository, qui détient l'horloge.
- **`TrailerLookupMatcher`** : décision pure « ce résultat TMDB correspond-il au
  média local ? », sans dépendance Android ni réseau.
- **`YouTubeTrailerPreview`** : lecture, contournement des contraintes YouTube
  (UA desktop, Referer, overscan), libération des ressources, exposition du
  basculement muet/son.

## Décisions techniques

1. **Réutiliser le lecteur F10 plutôt qu'une bibliothèque YouTube** : le
   contournement (UA desktop + Referer `https://cstv.app` + overscan) est le seul
   montage validé en production sur ce parc ; introduire une dépendance
   `android-youtube-player` réintroduirait l'erreur 153 documentée en F10.
2. **Étendre `TrailerMedia` plutôt que créer un second modèle** : garde une seule
   chaîne de résolution et un seul cache pour l'Accueil et les fiches, au prix
   d'un `tmdbId` nullable.
3. **Recherche TMDB dans la couche `data`, décision de correspondance dans la
   couche `domain`** : conforme à la convention projet (repository = accès,
   objet pur = règle testable) et cohérent avec `TmdbCatalogMatcher`.
4. **Minuterie dans l'UI, machine à états dans le ViewModel** : reprise exacte du
   découpage Accueil ; le ViewModel reste testable sans horloge simulée et la
   règle des 5 s suit naturellement la composition et le cycle de vie.
5. **Substitution du backdrop plutôt qu'un nouvel en-tête** : la spécification
   décrit l'estompage de l'image de fond ; réutiliser la couche existante évite
   tout déplacement du titre, des métadonnées et des boutons, et couvre mobile et
   TV avec un seul traitement.
6. **Bouton Son dans la barre supérieure existante** : pas d'overlay flottant
   au-dessus de la `WebView`, donc pas de conflit de focus D-Pad ni de zone
   tactile ambiguë ; place déjà occupée par le bouton Retour.
7. **Cache persistant plutôt que cache de session** : la résolution coûte jusqu'à
   trois appels réseau et le cas majoritaire d'un catalogue IPTV (aucune
   bande-annonce) coûte exactement le même prix ; un cache mémoire, mort à chaque
   arrêt du processus, ferait repayer ce coût à chaque session. La persistance est
   livrée avec F13 plutôt qu'après coup, pour ne pas construire la fiche sur une
   chaîne qu'il faudrait aussitôt refactorer.
8. **Un seul gagnant stocké, pas les deux sources** : l'ordre de priorité est fixe
   et aucun consommateur n'existe pour la source perdante. `resolvedTmdbId` est en
   revanche conservé, car il est coûteux à obtenir et réutilisable.
9. **TTL asymétrique + invalidation sur échec de lecture** : un résultat négatif
   se périme parce que TMDB s'enrichit ; un résultat positif ne se périme pas, il
   disparaît — deux causes différentes, deux mécanismes différents.
10. **Aucun stockage de préférence utilisateur** : l'état muet reste éphémère,
    aucune donnée personnelle n'entre dans la nouvelle table, qui est une donnée
    de catalogue partagée par tous les profils.

---

# 9. Plan de développement

## Tâche 1 — Étendre le contrat de résolution et le rapprochement TMDB

- [ ] Étendre `TrailerMedia` avec les informations de recherche locales et créer
  le matcher pur qui choisit un résultat TMDB fiable à partir du titre et de
  l'année.

Objectif : permettre aux fiches de détail de résoudre un trailer sans `tmdbId`
connu, sans faire remonter de DTO réseau dans le domaine.

Fichiers :
- `domain/model/TrailerPreview.kt`
- `domain/model/TrailerLookupMatcher.kt`
- `data/remote/api/TmdbApiService.kt`
- `presentation/home/HomeViewModel.kt`
- `test/.../domain/model/TrailerLookupMatcherTest.kt`
- tests existants affectés par le contrat `TrailerMedia`

Validation : tests du matcher (seuil 0,8, année ± 1, égalité et titre bruité)
passants ; les appels Home existants continuent à fournir leur `tmdbId`.

## Tâche 2 — Ajouter le cache persistant et la migration Room

- [ ] Créer la table de cache de trailer, son DAO, l'horloge injectable et la
  migration non destructive vers la version 18.

Objectif : persister un résultat positif ou négatif par média de catalogue et
fournir les primitives de lecture, écriture, suppression et purge nécessaires
au repository.

Fichiers :
- `data/local/entity/TrailerCacheEntity.kt`
- `data/local/dao/TrailerCacheDao.kt`
- `data/local/db/AppDatabase.kt`
- `data/local/db/Migrations.kt`
- `domain/util/TimeProvider.kt`
- `data/util/SystemTimeProvider.kt`
- `di/AppModule.kt`

Validation : compilation de Room ; SQL de `MIGRATION_17_18` relu contre
l'entité ; `ALL_MIGRATIONS` contient la migration et la version de base est 18.

## Tâche 3 — Implémenter la résolution et le cache dans le repository

- [ ] Étendre `TrailerRepository` et `TrailerRepositoryImpl` avec la chaîne
  mémoire → Room → Xtream → TMDB direct/recherche, les TTL et l'invalidation.

Objectif : résoudre et mettre en cache les trailers sans persister les erreurs
réseau, puis purger correctement lors d'un changement de compte ou d'une vidéo
illisible.

Fichiers :
- `domain/repository/TrailerRepository.kt`
- `domain/usecase/InvalidateTrailerPreviewUseCase.kt`
- `data/repository/TrailerRepositoryImpl.kt`
- `data/repository/AuthRepositoryImpl.kt`
- `di/AppModule.kt`
- `test/.../data/repository/TrailerRepositoryImplTest.kt`

Validation : tests couvrant cache positif/négatif, expiration 30/7 jours,
recherche TMDB, échec réseau non persisté, `invalidate` et
`clearSessionCache`; aucune régression des trailers Xtream et Home.

## Tâche 4 — Extraire le lecteur YouTube partagé sans régression Home

- [ ] Déplacer l'état d'aperçu et le lecteur WebView F10 dans les composants
  partagés, puis rebrancher l'écran Accueil et son invalidation sur erreur.

Objectif : rendre le lecteur réutilisable par les fiches tout en conservant à
l'identique le rendu, la libération WebView et les contrôles de la Hero Home.

Fichiers :
- `presentation/components/TrailerPreviewUiState.kt`
- `presentation/components/YouTubeTrailerPreview.kt`
- `presentation/home/components/HomeYouTubeTrailerPreview.kt` (suppression)
- `presentation/home/components/HomeTrendingCarousel.kt`
- `presentation/home/HomeViewModel.kt`
- tests Home affectés

Validation : `YouTubeTrailerPreview` est non focusable ; Home compile et ses
tests d'état passent ; un échec de lecture invalide désormais l'entrée du
repository.

## Tâche 5 — Orchestrer l'aperçu dans le ViewModel VOD

- [ ] Ajouter l'état, les actions et les protections contre réponses obsolètes
  dans `VodViewModel`.

Objectif : lancer une résolution à la demande de l'UI, l'annuler lors d'un
changement de film ou d'une sortie de contexte et oublier un trailer VOD
illisible.

Fichiers :
- `presentation/vod/VodState.kt`
- `presentation/vod/VodViewModel.kt`
- `test/.../presentation/vod/VodViewModelTest.kt`

Validation : tests `Preparing → Playing/Poster`, annulation, changement de
stream, réponse tardive ignorée et signalement d'échec limité au média actif.

## Tâche 6 — Orchestrer l'aperçu dans le ViewModel Séries

- [ ] Ajouter l'équivalent VOD au flux d'état et au ViewModel des séries.

Objectif : garantir le même comportement d'annulation, de résolution et
d'invalidation pour une série, sans modifier la lecture d'épisode.

Fichiers :
- `presentation/series/SeriesState.kt`
- `presentation/series/SeriesViewModel.kt`
- `test/.../presentation/series/SeriesViewModelTest.kt`

Validation : mêmes cas que VOD, y compris changement de série et absence de
régression des actions existantes de fiche/épisode.

## Tâche 7 — Créer la couche de backdrop et intégrer les fiches

- [ ] Créer le composant décoratif temporisé puis remplacer le backdrop statique
  des détails VOD et Séries, avec contrôle sonore accessible.

Objectif : après cinq secondes de présence active, afficher le trailer muet en
fond sans déplacer le contenu ni laisser la WebView recevoir le focus ou les
gestes.

Fichiers :
- `presentation/components/MediaDetailsTrailerBackdrop.kt`
- `presentation/vod/VodDetailsScreen.kt`
- `presentation/series/SeriesDetailsScreen.kt`
- `presentation/navigation/NavGraph.kt` (si les paramètres des écrans évoluent)

Validation : revue manuelle mobile/TV : délai, retour, lancement principal,
mise en arrière-plan, son, poster de repli et navigation D-Pad ; la WebView est
libérée à la sortie et ne capte jamais le focus.

## Tâche 8 — Vérifier l'intégration F13

- [ ] Exécuter la suite de non-régression et consigner les résultats avant la
review technique.

Objectif : confirmer que les changements transverses (Room, DI, Home, VOD et
Séries) restent cohérents et préparer les éléments de l'étape 6.

Fichiers :
- `ai/features/F13-media-details-trailer-autoplay.md` (notes de développement
  et validation ultérieures)

Validation : `./gradlew testDebugUnitTest`, `./gradlew assembleDebug` et
`./gradlew lintDebug` passent ; les vérifications manuelles mobile/TV sont
rapportées séparément avec leur résultat réel.
