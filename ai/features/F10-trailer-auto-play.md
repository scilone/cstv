# F10 - Lecture automatique du trailer sur la Hero Card / Carrousel de l'accueil

## Informations générales

Type:
Feature

Status:
TASK BREAKDOWN

Created:
2026-07-22

---

# 1. Description

Description de l'idée : sur la Hero Card de l'accueil ou le carrousel des tendances de l'écran d'accueil (`HomeScreen`), après 5 secondes d'affichage, l'image de couverture (poster) est remplacée par la lecture automatique en boucle de la bande-annonce (trailer) si celle-ci est disponible via l'API Xtream.

---

# 2. Contexte

Pourquoi cet élément existe :
- **Modernisation de l'UI/UX de l'accueil** : S'aligner sur les standards de l'industrie des applications de streaming (Netflix, Prime Video, Disney+), en apportant du mouvement et un aperçu instantané du média vedette directement sur l'écran d'accueil.
- **Engagement utilisateur** : Donner envie de regarder un film ou une série mis en avant (notamment dans les tendances ou la reprise de lecture) sans imposer d'action de clic à l'utilisateur.

---

# 3. Spécification fonctionnelle

## User stories

- En tant qu'utilisateur mobile, je veux qu'un aperçu vidéo du contenu mis en avant démarre après un court temps d'arrêt sur l'accueil, afin d'en découvrir l'ambiance sans ouvrir sa fiche.
- En tant qu'utilisateur, je veux que cet aperçu reste silencieux par défaut et qu'il ne perturbe jamais ma navigation.
- En tant qu'utilisateur Android TV, je veux conserver l'expérience actuelle de navigation au D-pad, sans lecture automatique imposée lors du déplacement du focus.

## Périmètre

- Fonctionnalité limitée à l'écran Accueil mobile (`HomeScreen`). Elle concerne exclusivement l'élément actuellement visible du carrousel Tendances (`HomeTrendingCarousel`).
- La Hero Card « Reprendre » et l'accueil Android TV ne lancent pas de trailer automatiquement dans cette version : leur contenu est personnel (reprise) ou leur navigation est fondée sur le focus, ce qui ne permet pas d'identifier un état d'inactivité non ambigu.
- Un aperçu ne peut être proposé que pour un film ou une série associé à une bande-annonce exploitable et autorisée par le périmètre réseau du projet.
- La lecture du film, de l'épisode ou de la chaîne IPTV n'est jamais déclenchée par cette fonctionnalité.

## Parcours utilisateur

1. L'utilisateur ouvre l'Accueil mobile ; le carrousel Tendances affiche son poster, ses badges et son titre habituels.
2. Un délai de cinq secondes démarre uniquement pour la page active et stable du carrousel.
3. Au terme du délai, si l'utilisateur est toujours sur l'Accueil, que cette même page est toujours affichée et qu'un trailer exploitable est disponible, le poster est remplacé par son aperçu vidéo en boucle.
4. L'aperçu démarre muet. Un contrôle sonore visible mais discret permet à l'utilisateur d'activer ou de couper le son pour l'aperçu en cours ; son état ne doit pas être mémorisé ni réutilisé par le lecteur principal.
5. Un tap sur la carte conserve son comportement existant : ouverture de la fiche du film ou de la série. L'aperçu s'arrête avant cette navigation.
6. Un balayage vers une autre page du carrousel, un changement d'onglet, le passage de l'application en arrière-plan ou la disparition de l'Accueil interrompt immédiatement l'aperçu. La nouvelle page repart alors de son poster et, si applicable, d'un nouveau délai complet de cinq secondes.

## Règles métier

- Un seul aperçu peut être en cours dans l'application, et seulement pour la page active du carrousel Tendances.
- Le compteur est annulé et recommencé à chaque changement de page ; aucun aperçu ne doit démarrer pour une page qui n'est plus active.
- L'absence, le vide ou le format non reconnu de la donnée de bande-annonce signifie « trailer indisponible » : le poster demeure affiché et aucun contrôle sonore n'est présenté.
- Un échec de préparation ou de lecture est silencieux, ne bloque pas l'interface et restaure le poster. Il n'y a ni dialogue, ni toast, ni nouvelle tentative automatique en boucle.
- La préférence de son est limitée à l'aperçu courant ; tout nouvel aperçu commence muet.
- La fonctionnalité ne modifie ni les données de reprise de lecture, ni les favoris, ni l'historique, ni les recommandations.

## Contraintes produit et périmètre

- YouTube est une source réseau externe explicitement autorisée par le PO pour l'ensemble du projet, sans restriction aux trailers. L'intégration F10 peut donc utiliser les identifiants/URLs YouTube fournis par Xtream ou les métadonnées vidéo TMDB.
- La lecture doit passer par un lecteur YouTube intégré conforme ; l'application ne doit pas extraire, déchiffrer ni reconstruire les URLs CDN internes de YouTube pour les fournir directement à ExoPlayer.

## Critères d'acceptation

- [ ] Sur mobile, une page Tendances avec un trailer autorisé et exploitable lance un aperçu muet après cinq secondes continues sans changement de page.
- [ ] Avant l'expiration du délai, le poster et les informations existantes restent visibles et interactifs.
- [ ] Faire défiler le carrousel, quitter l'Accueil, mettre l'application en arrière-plan ou ouvrir la fiche arrête immédiatement l'aperçu ; aucun son ni aucune vidéo ne subsiste.
- [ ] Après changement de page, le délai redémarre à zéro pour la nouvelle page et l'ancien aperçu ne réapparaît pas.
- [ ] En l'absence de trailer, avec une valeur invalide, sans réseau ou en cas d'échec de lecture, le poster reste ou redevient visible sans erreur utilisateur et sans dégradation de navigation.
- [ ] Le son est coupé au démarrage de chaque aperçu et l'utilisateur peut le basculer uniquement pour l'aperçu courant.
- [ ] Sur Android TV et sur la Hero Card « Reprendre », le comportement reste identique à l'existant.

---

# 4. Spécification technique

## Conclusion de faisabilité et décision

- Les éléments de `HomeTrendingCarousel` sont des titres TMDB appariés à un `VodStream` ou un `SeriesStream` du catalogue local Xtream. Les objets de liste actuellement disponibles ne portent aucune URL de trailer.
- `get_vod_info` et `get_series_info` peuvent, selon le panel, exposer un champ `youtube_trailer`. Ce champ désigne YouTube (identifiant ou URL) ; il ne constitue pas un flux vidéo servi par le serveur Xtream.
- L'endpoint TMDB Tendances actuellement utilisé ne retourne pas de trailer. L'endpoint TMDB `/{movie|tv}/{id}/videos` peut fournir des métadonnées vidéo et servir de repli lorsque le panel Xtream ne fournit aucune référence exploitable.
- Aucun contrat Xtream Codes standard inspecté dans le projet ne fournit une URL de bande-annonce directe et authentifiée par le panel. Construire ou deviner une telle URL n'est pas fiable.
- **Décision PO du 2026-07-22 : YouTube est autorisé sans restriction particulière dans le projet. Le blocage de périmètre de F10 est levé.**
- La lecture F10 utilisera `com.pierfrancescosoffritti.androidyoutubeplayer:core:13.0.0`, wrapper Android maintenu de l'API IFrame YouTube, compatible minSdk 21. Seul le module `core` est requis ; aucun module Chromecast ne doit être ajouté.
- L'intégration conserve le player YouTube officiel embarqué. Elle n'utilise aucun extracteur de flux YouTube vers Media3, solution fragile et non conforme au contrat du service.

## Contrat de domaine proposé

- Introduire un modèle dédié `TrailerSource`, indépendant des DTOs et du lecteur, avec `YouTube(videoId: String)` comme source de F10. Une future variante pourra être ajoutée sans modifier la présentation.
- Introduire `TrailerPreview` contenant l'identité stable du média (`movie + streamId` ou `series + seriesId`) et sa `TrailerSource`.
- Exposer un `TrailerRepository` dans `domain/repository` et son implémentation dans `data/repository`. Le repository récupère d'abord `youtube_trailer` via les détails Xtream du média apparié, normalise les formes ID/URL vers un ID vidéo strict, puis interroge les vidéos TMDB en repli. Il retourne `null` si aucune bande-annonce YouTube exploitable n'existe.
- Ajouter un `GetTrailerPreviewUseCase`. La couche `presentation` ne doit connaître ni le champ Xtream brut, ni les règles d'URL, ni les DTO Retrofit.
- Ne pas ajouter le trailer à `PlaybackPosition` : F10 exclut la Hero « Reprendre », et dupliquer cette donnée dans l'historique créerait une information périssable sans bénéfice fonctionnel.
- Ne pas modifier Room pour la première version. Un cache mémoire borné par identité média dans le repository suffit pour la session ; aucune migration de `AppDatabase` n'est nécessaire.

## Acquisition et orchestration

- `HomeViewModel` demande le trailer uniquement pour l'élément Tendances actif, via une méthode telle que `selectTrendingPreview(itemId)`. Un nouveau changement d'élément annule le job précédent (`mapLatest`/Job annulable).
- Le délai de cinq secondes appartient à l'état d'interaction du carrousel et reste dans `HomeTrendingCarousel` avec un `LaunchedEffect` indexé par l'identité de la page active, l'état de défilement et la visibilité/lifecycle. Le fetch peut être anticipé pour la page active, mais la lecture ne démarre qu'après cinq secondes continues et une réponse valide.
- L'état UI explicite est limité à `Poster`, `Preparing`, `Playing(preview, muted)` et `Failed`. Tout échec revient à `Poster` pour l'identité courante, sans retry automatique.
- La clé des effets doit être l'identité stable du média et non le seul index du pager, afin qu'un recalcul/rétrécissement de `trendingItems` ne démarre pas le trailer d'un autre titre.
- `HomeScreen` transmet au carrousel l'état et les événements du ViewModel. Android TV continue d'utiliser `HomeTrendingRowTv` sans aucune branche trailer.

## Lecture et cycle de vie

- Utiliser un composable privé mobile `HomeYouTubeTrailerPreview`, adossé à un unique `YouTubePlayerView` de la bibliothèque et intégré à Compose par `AndroidView`. Le composable charge l'ID vidéo, démarre muet et relance la même vidéo sur l'événement de fin pour garantir la boucle.
- Le `YouTubePlayerView` est observateur du lifecycle et est explicitement libéré dans `DisposableEffect`. La lecture est arrêtée lors d'un swipe, d'un clic ouvrant la fiche, d'une disparition de la composition, d'un passage du lifecycle sous `STARTED` ou d'un changement d'identité média.
- Le contrôle muet/non muet est un état local au preview courant, initialisé à `true` à chaque nouvelle identité. Il ne lit ni n'écrit les préférences audio du lecteur principal.
- Le poster et le scrim restent le fallback visuel jusqu'à l'état prêt/lecture. Une erreur du player ou un blocage de l'autoplay conserve/restaure immédiatement le poster.
- La carte garde son gestionnaire de clic existant au-dessus de la surface vidéo. Le bouton son possède sa propre cible tactile et ne déclenche pas la navigation.

## Fichiers impactés (après levée du blocage)

- `app/src/main/java/com/cstv/app/data/remote/dto/VodInfoDto.kt` et `SeriesInfoDto.kt` (noms exacts à confirmer à l'étape 4) : mapper défensivement le champ fourni par le panel.
- `app/src/main/java/com/cstv/app/data/remote/api/TmdbApiService.kt` et un DTO vidéo TMDB dédié : ajouter `movie/{id}/videos` et `tv/{id}/videos` comme repli.
- `app/src/main/java/com/cstv/app/domain/model/TrailerPreview.kt` : nouveaux modèles `TrailerPreview` / `TrailerSource`.
- `app/src/main/java/com/cstv/app/domain/repository/TrailerRepository.kt` : nouveau contrat.
- `app/src/main/java/com/cstv/app/data/repository/TrailerRepositoryImpl.kt` : récupération, validation et cache mémoire.
- `app/src/main/java/com/cstv/app/domain/usecase/GetTrailerPreviewUseCase.kt` : orchestration métier.
- `app/src/main/java/com/cstv/app/presentation/home/HomeState.kt`, `HomeViewModel.kt` et `HomeScreen.kt` : état, annulation et branchement mobile.
- `app/src/main/java/com/cstv/app/presentation/home/components/HomeTrendingCarousel.kt` : délai stable, rendu poster/player, bouton son et nettoyage lifecycle.
- `app/src/main/java/com/cstv/app/di/AppModule.kt` : binding du repository si aucun binding Hilt par constructeur n'est possible.
- Tests unitaires correspondants sous `app/src/test/java/...` pour parsing/validation, annulation ViewModel et transitions d'état.
- `app/build.gradle.kts` : ajouter uniquement la dépendance `android-youtube-player:core:13.0.0`.
- `app/proguard-rules.pro` : vérifier les consumer rules de la bibliothèque ; aucune nouvelle interface Retrofit n'est créée, car `TmdbApiService` existant reçoit les endpoints supplémentaires et possède déjà sa règle `-keep`.

## Dépendances

- Nouvelle dépendance prévue : `com.pierfrancescosoffritti.androidyoutubeplayer:core:13.0.0` depuis Maven Central (minSdk 21).
- Aucun SDK Google Play Services, extracteur YouTube, module Chromecast ni clé YouTube Data API n'est requis pour lire un ID vidéo avec l'API IFrame embarquée.

## Risques et contraintes de performance

- Un appel `get_*_info` supplémentaire par page active consomme la connexion Xtream, souvent limitée par le panel. Les requêtes doivent rester strictement séquentielles/annulables et mises en cache en mémoire ; aucun préchargement de toutes les pages.
- Le trailer ne doit jamais être chargé sur Android TV, pour la Hero « Reprendre » ou pour une page hors écran.
- Le player ne doit exister qu'en un exemplaire. Toute fuite de player/WebView provoquerait consommation CPU, réseau et audio en arrière-plan.
- Les références Xtream doivent être normalisées par un parseur en liste blanche (`youtube.com`, `youtu.be`, ID brut valide). Toute autre URL ou forme ambiguë est rejetée.
- L'autoplay peut être refusé par le moteur Web/IFrame malgré le démarrage muet ; ce cas reste un fallback silencieux sur le poster.
- Le player YouTube embarqué exige une surface d'au moins 200 × 200 px ; le carrousel mobile actuel de 420 dp respecte cette contrainte, à préserver lors des évolutions de layout.
- Le chargement d'un embed YouTube déclenche des échanges de données avec YouTube. Ce comportement devra être cohérent avec la politique de confidentialité et le consentement applicables au mode de distribution de l'application.
- Le délai et la lecture doivent être testés avec une horloge/coroutines contrôlées ; les tests ne doivent pas attendre cinq secondes réelles.

---

# 5. Architecture

```text
HomeTrendingCarousel
  ├─ détecte page mobile stable + délai 5 s
  ├─ émet identité active / annulation / bascule son
  └─ rend Poster ou HomeTrailerPreview
                 │
                 ▼
HomeScreen → HomeViewModel → GetTrailerPreviewUseCase
                                │
                                ▼
                         TrailerRepository
                                │
                 ┌──────────────┴──────────────┐
                 ▼                             ▼
 XtreamApiService + TmdbApiService    cache mémoire borné
 détails Xtream puis vidéos TMDB
                 │
                 ▼
      parse + validation de TrailerSource
```

Responsabilités :

- **Data** : lire la valeur brute du panel, la parser défensivement, appliquer le repli TMDB et cacher le résultat de session.
- **Domain** : représenter une source de trailer sans dépendance au fournisseur et retourner `null` lorsqu'aucune source conforme n'existe.
- **Presentation/ViewModel** : garantir l'annulation entre médias et exposer un état UI déterministe.
- **Compose** : mesurer les cinq secondes de stabilité, gérer la visibilité/lifecycle et posséder/libérer l'unique player d'aperçu.

Cette séparation empêche les formats variables de `youtube_trailer` et les détails TMDB de fuiter jusqu'à la UI ; celle-ci ne manipule qu'un ID vidéo validé.

---

# 6. Plan de développement

- [ ] Tâche 1 — Ajouter les contrats et la récupération des métadonnées de trailer

Objectif :
Définir les modèles de domaine indépendants du fournisseur, lire de façon
défensive `youtube_trailer` dans les détails Xtream et exposer le repli vidéos
TMDB, sans encore déclencher de lecture dans l'accueil.

Fichiers :
- `app/build.gradle.kts`
- `app/src/main/java/com/cstv/app/data/remote/dto/VodInfoDto.kt`
- `app/src/main/java/com/cstv/app/data/remote/dto/SeriesInfoDto.kt`
- `app/src/main/java/com/cstv/app/data/remote/dto/TmdbVideoDto.kt` (nouveau)
- `app/src/main/java/com/cstv/app/data/remote/api/TmdbApiService.kt`
- `app/src/main/java/com/cstv/app/domain/model/TrailerPreview.kt` (nouveau)
- `app/proguard-rules.pro`

Validation :
- Tests unitaires des DTO/mappers pour valeur Xtream absente, ID YouTube brut,
  URL `youtube.com`, URL `youtu.be` et valeur malformée/rejetée.
- Vérifier que les endpoints TMDB ajoutés réutilisent `TmdbApiService` existant
  (et sa règle R8) et que seule la dépendance `android-youtube-player:core:13.0.0`
  est introduite.

- [ ] Tâche 2 — Implémenter la résolution de trailer dans les couches data et domain

Objectif :
Créer `TrailerRepository` et `GetTrailerPreviewUseCase` : détails Xtream en
premier, repli TMDB si nécessaire, cache mémoire borné par identité média et
retour `null` silencieux pour toute source non exploitable ou erreur réseau.

Fichiers :
- `app/src/main/java/com/cstv/app/domain/repository/TrailerRepository.kt` (nouveau)
- `app/src/main/java/com/cstv/app/data/repository/TrailerRepositoryImpl.kt` (nouveau)
- `app/src/main/java/com/cstv/app/domain/usecase/GetTrailerPreviewUseCase.kt` (nouveau)
- `app/src/main/java/com/cstv/app/di/AppModule.kt` (uniquement si l'injection par constructeur ne suffit pas)
- `app/src/test/java/com/cstv/app/data/repository/TrailerRepositoryImplTest.kt` (nouveau)
- `app/src/test/java/com/cstv/app/domain/usecase/GetTrailerPreviewUseCaseTest.kt` (nouveau)

Validation :
- Tests couvrant la priorité Xtream, le repli TMDB film/série, le rejet des
  hôtes/formats non autorisés, les erreurs silencieuses et le cache de session.
- Vérifier qu'aucune entité Room, migration ou donnée de reprise n'est ajoutée.

- [ ] Tâche 3 — Exposer l'état de preview annulable depuis `HomeViewModel`

Objectif :
Ajouter un état UI par média actif (`Poster`, `Preparing`, `Playing`, `Failed`)
et des événements de sélection/annulation afin qu'un changement de page ne
puisse jamais afficher ni démarrer la réponse d'un média précédent.

Fichiers :
- `app/src/main/java/com/cstv/app/presentation/home/HomeState.kt` ou l'état déclaré dans `HomeViewModel.kt` (emplacement exact à confirmer lors de l'implémentation)
- `app/src/main/java/com/cstv/app/presentation/home/HomeViewModel.kt`
- `app/src/main/java/com/cstv/app/presentation/home/HomeScreen.kt`
- `app/src/test/java/com/cstv/app/presentation/home/HomeViewModelTest.kt` (nouveau ou existant selon l'arborescence)

Validation :
- Tests à coroutines contrôlées : sélection du média actif, annulation lors d'un
  changement rapide, absence de retry après échec et remise à muet pour une
  nouvelle identité.
- Vérifier que `HomeTrendingRowTv` ne reçoit ni état ni événement F10.

- [ ] Tâche 4 — Intégrer le preview YouTube dans le carrousel mobile et son cycle de vie

Objectif :
Après cinq secondes de page stable, remplacer uniquement le poster actif par
un `YouTubePlayerView` Compose muet et en boucle ; conserver le clic vers la
fiche, le scrim, les badges et l'indicateur de pages, avec arrêt/libération à
chaque sortie de contexte.

Fichiers :
- `app/src/main/java/com/cstv/app/presentation/home/components/HomeTrendingCarousel.kt`
- `app/src/main/java/com/cstv/app/presentation/home/components/HomeYouTubeTrailerPreview.kt` (nouveau si l'extraction rend le carrousel plus lisible)
- ressources de chaîne/icône existantes sous `app/src/main/res/` si nécessaires au contrôle sonore

Validation :
- Test manuel mobile : attente de cinq secondes, swipe avant/après le délai,
  ouverture de fiche, changement d'onglet et mise en arrière-plan arrêtent
  immédiatement l'aperçu ; le bouton son ne déclenche pas le clic de carte.
- Vérifier que le poster est conservé/restauré si le player est indisponible ou
  refuse l'autoplay, et qu'un seul player est vivant à la fois.

- [ ] Tâche 5 — Vérifier la non-régression et les critères d'acceptation F10

Objectif :
Exécuter les validations automatisées et contrôler les cas fonctionnels sur
mobile et Android TV sans élargir la fonctionnalité à la Hero « Reprendre ».

Fichiers :
- `ai/features/F10-trailer-auto-play.md` (cocher les tâches réalisées et consigner les résultats aux étapes ultérieures)

Validation :
- Exécuter `./gradlew testDebugUnitTest`, `./gradlew assembleDebug` et
  `./gradlew lintDebug`.
- Sur appareil/émulateur : confirmer les critères d'acceptation mobile avec et
  sans trailer, puis confirmer l'absence de lecture automatique sur Android TV
  et sur la Hero « Reprendre ».

---

# 7. Notes de développement
(Vides pour le moment)

---

# 8. Review
(Vides pour le moment)

---

# 9. Release
(Vides pour le moment)
