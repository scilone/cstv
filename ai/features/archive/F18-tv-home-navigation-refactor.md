# F18 - Refonte de l'interface TV : navigation latérale, Hero Card, logo et « Voir tout »

## Informations générales

Type :
Feature

Status :
RELEASED

Created :
2026-07-28

Target version :
v1.63.0

Released :
2026-07-28

---

## Description

L'accueil Android TV doit devenir plus fluide, lisible et cohérent avec l'identité Coffee Stream TV. La navigation actuelle par puces horizontales, le logo TV générique, l'absence de mise en avant éditoriale et le bouton « Voir tout » trop lourd ne répondent pas à ce besoin.

F18 couvre quatre résultats visibles sur Android TV : une navigation latérale rétractable, une bannière de marque CSTV cohérente, une Hero Card alimentée par la tendance TMDB principale avec aperçu de bande-annonce, et un lien « Voir tout » aligné sur le style mobile.

La carte de reprise de lecture ne doit plus être utilisée comme Hero Card, ni sur TV ni sur mobile : la mise en avant éditoriale repose exclusivement sur les tendances TMDB.

## Contexte

- L'accueil TV utilise aujourd'hui des accès rapides horizontaux et ne propose pas de navigation persistante depuis les autres sections.
- Le visuel de bannière TV ne reprend pas le logo tasse de café présent sur mobile.
- Les tendances TMDB existent mais ne sont pas présentées comme une Hero Card immersive sur TV.
- Le bouton TV « Voir tout » est visuellement plus lourd que le lien lavande discret utilisé sur mobile.

## Objectif

Permettre à un utilisateur Android TV de se déplacer naturellement au D-pad entre les sections principales, d'identifier immédiatement CSTV, de découvrir une tendance mise en avant et d'accéder aux catalogues complets avec des contrôles lisibles à distance.

## Hypothèses et décisions fonctionnelles

- La barre latérale est présente sur les écrans principaux TV : Accueil, TV en direct, Films, Séries, Recherche et Paramètres.
- Elle est masquée dans les lecteurs immersifs et dans les fiches de détails afin de laisser la priorité au contenu.
- Elle est pliée par défaut. Son ouverture dépend du focus D-pad et ne doit pas déplacer ni redimensionner visiblement le contenu principal.
- L'expiration du compte est affichée lorsqu'une valeur exploitable est disponible ; une valeur absente ou illisible ne doit jamais afficher une date erronée.
- La Hero Card utilise la première tendance TMDB disponible. Elle ne représente jamais une reprise de lecture.
- L'aperçu de bande-annonce est une amélioration facultative : son absence ou son indisponibilité ne doit pas empêcher l'accès à la tendance ni dégrader l'accueil.

## Spécification fonctionnelle

### User stories

- En tant qu'utilisateur Android TV, je veux accéder aux sections principales depuis une navigation latérale afin de me déplacer rapidement au D-pad.
- En tant qu'utilisateur, je veux voir clairement le profil actif et les informations de mon abonnement afin de savoir quelle session j'utilise.
- En tant qu'utilisateur, je veux découvrir une tendance TMDB mise en avant sur l'accueil afin de choisir rapidement un film ou une série.
- En tant qu'utilisateur, je veux pouvoir couper ou activer le son de l'aperçu avec le D-pad afin de garder le contrôle de la lecture automatique.
- En tant qu'utilisateur, je veux reconnaître la même identité CSTV sur mobile et sur TV.
- En tant qu'utilisateur, je veux que « Voir tout » soit discret au repos mais sans ambiguïté lorsqu'il reçoit le focus.

### Navigation latérale TV

- La barre est ancrée sur le bord gauche et couvre toute la hauteur de l'écran TV.
- Au repos, elle est pliée : elle affiche l'avatar du profil actif et les icônes des sections, sans texte.
- Dès que le focus D-pad entre dans la barre, elle se déplie et révèle les libellés ; l'ouverture est visuellement fluide.
- Quand le focus revient au contenu principal, elle se replie sans modifier la position apparente du contenu.
- La section active est identifiable dans les états plié et déplié par une mise en avant lavande ou équivalente conforme au thème.
- La partie profil est en tête de barre. Dépliée, elle affiche l'avatar, le nom du profil actif, le nom d'utilisateur de la session et, si disponible, « Expire le : DD/MM/YYYY ».
- Une séparation visuelle distingue le profil des destinations.
- Les destinations sont proposées dans cet ordre : Accueil, TV en direct, Films, Séries, Recherche, Paramètres.
- L'activation d'une destination mène à la section racine correspondante et met immédiatement à jour l'état sélectionné.
- Depuis la barre ouverte, la touche Retour la ferme et rend le focus au contenu. Elle ne déclenche pas de déconnexion ni de sortie de l'application.

### Identité visuelle TV

- La bannière Android TV présente le logo tasse de café CSTV, avec les lignes de vapeur violettes caractéristiques de l'identité mobile.
- Le fond violet foncé de la bannière est conservé.
- Le visuel doit rester lisible et reconnaissable dans le format de bannière Android TV.

### Hero Card TV et aperçu de bande-annonce

- La Hero Card est affichée en tête de l'accueil TV lorsqu'au moins une tendance TMDB est disponible.
- Elle met en avant la première tendance disponible, avec son image, un dégradé assurant la lisibilité, le badge « TENDANCE », le type « FILM » ou « SÉRIE », le titre et l'année lorsque celle-ci est connue.
- La carte est accessible au D-pad et son focus est clairement visible par une bordure lavande lumineuse ou un indicateur visuel équivalent.
- Son activation ouvre la fiche de détails correspondant au film ou à la série mis en avant.
- Lorsqu'elle conserve le focus pendant 1,5 seconde, l'application tente de remplacer progressivement l'image par l'aperçu de bande-annonce associé.
- Un contrôle Mute/Unmute accessible au D-pad est affiché sur la carte pendant l'aperçu. L'état sonore est explicite.
- Dès que la carte perd le focus, que l'utilisateur quitte l'accueil, navigue vers une autre fiche, ouvre un lecteur, ou que l'application passe en arrière-plan, l'aperçu s'arrête immédiatement et l'image statique redevient visible.
- Aucune reprise de lecture ne doit être affichée comme Hero Card. Cette règle est commune aux accueils mobile et TV.

### Lien « Voir tout »

- Sur TV, « VOIR TOUT » remplace le bouton gris rectangulaire.
- Au repos, il se présente comme un lien textuel lavande sans arrière-plan lourd.
- Au focus, il reçoit un fond lavande translucide et des coins arrondis ; cet état doit rester très visible à distance.
- Son activation conserve la destination déjà attendue pour la section concernée.

## Parcours utilisateur

1. L'utilisateur ouvre une section principale TV : la barre latérale est visible, pliée, et la section courante est indiquée.
2. Il déplace le focus vers la gauche : la barre se déplie, affiche son profil et les libellés de navigation.
3. Il sélectionne une destination : la section choisie s'ouvre, puis l'état sélectionné de la barre correspond à cette destination.
4. Sur l'accueil, il atteint la Hero Card : la tendance principale est lisible et activable.
5. S'il conserve le focus 1,5 seconde, un aperçu de bande-annonce peut démarrer ; il peut en contrôler le son.
6. Il quitte la carte ou l'accueil : l'aperçu s'arrête sans continuer en arrière-plan.
7. Depuis une rangée, il atteint « VOIR TOUT », reconnaît son focus et ouvre le catalogue complet associé.

## Règles métier

- Les données de profil et de session affichées sont celles de la session active ; aucun autre compte ou profil ne doit être exposé.
- Une date d'expiration n'est affichée qu'après conversion fiable. Si elle est indisponible ou invalide, la ligne est masquée.
- La navigation latérale ne doit pas être proposée dans les lecteurs immersifs ni les fiches de détails.
- La Hero Card n'est visible que si une tendance exploitable est disponible ; l'accueil reste utilisable sans elle.
- L'aperçu est lancé uniquement après 1,5 seconde de focus continu sur la Hero Card. Une perte de focus avant ce délai annule son lancement.
- Un aperçu ne doit pas continuer hors de son contexte : perte de focus, navigation, arrière-plan et ouverture du lecteur imposent son arrêt.
- L'absence d'aperçu, une erreur de chargement ou une vidéo non lisible conservent le poster et la navigation vers la fiche.
- Le contenu « Reprendre » conserve ses usages éventuels dans les listes dédiées mais ne peut pas être promu en Hero Card.

## Critères d'acceptation

- [ ] Sur un écran principal TV, la barre latérale est pliée par défaut, montre les icônes et identifie la section courante.
- [ ] Le focus de la barre l'ouvre avec l'avatar, les libellés, le profil et les informations de session disponibles, sans saut visuel du contenu.
- [ ] Les six destinations sont affichées dans l'ordre défini et chacune ouvre sa section racine correcte.
- [ ] Retour ferme la barre ouverte et restitue le focus au contenu.
- [ ] La barre est absente des fiches de détails et des lecteurs immersifs.
- [ ] La bannière TV affiche le logo tasse de café CSTV sur fond violet foncé.
- [ ] Une tendance disponible apparaît en Hero Card en tête de l'accueil TV et ouvre la fiche de détails correcte.
- [ ] Aucun contenu de reprise n'apparaît comme Hero Card sur mobile ou TV.
- [ ] Après 1,5 seconde de focus continu, la Hero Card tente un aperçu ; celui-ci s'arrête immédiatement à la perte de focus ou à la sortie de son contexte.
- [ ] Le contrôle Mute/Unmute de l'aperçu est atteignable et compréhensible au D-pad.
- [ ] Si l'aperçu est indisponible, le poster et l'accès aux détails restent fonctionnels.
- [ ] « VOIR TOUT » est un lien lavande au repos et possède un focus très visible, tout en conservant sa destination.

## Cas limites et gestion des erreurs

- Sans profil actif, la barre ne doit pas afficher d'informations périmées ; elle utilise un état neutre cohérent avec l'état de session existant.
- Sans nom d'utilisateur ou date d'expiration valide, seules les informations disponibles sont affichées.
- Sans tendance TMDB, la Hero Card est masquée ; les autres sections de l'accueil restent accessibles.
- Si l'image de tendance est absente ou indisponible, la Hero Card utilise le repli visuel déjà prévu par l'application sans masquer le titre ni bloquer l'ouverture des détails.
- Si la bande-annonce est absente, inaccessible, en erreur ou interrompue, l'utilisateur reste sur le poster ; aucun message technique brut n'est affiché.
- Des déplacements rapides du focus ne doivent ni déclencher un aperçu tardif ni laisser un son ou une vidéo en cours après le départ de la Hero Card.
- Si la destination associée à une tendance n'est plus disponible localement, l'utilisateur reçoit le traitement d'indisponibilité déjà établi par l'application, sans interrompre l'accueil.

## Questions ouvertes

Aucune question fonctionnelle bloquante. Le formatage concret des dates, les composants de navigation et le mécanisme de lecture seront décidés à l'étape 3 sans modifier les règles ci-dessus.

---

## Spécification technique

### État des lieux (vérifié dans le code au 2026-07-28)

- **La navigation est déjà unifiée** : `MainActivity` compose un unique `AppNavGraph` (navigation-compose) pour TV **et** mobile. L'ancien double système « enum `AppScreen` + `when` » décrit dans `AGENTS.md` n'existe plus. Toutes les routes visées par F18 existent déjà : `home`, `tv`, `movies`, `series`, `search`, `settings` (+ `vod_details`, `series_details`, `live_player`, `vod_player`, `series_player`).
- **L'accueil TV** (`HomeScreen.kt`) affiche aujourd'hui : un en-tête profil, une rangée de 6 puces `TvButton` (lignes ~300-342), puis, si des tendances existent, une `HomeTrendingRowTv` (rangée d'affiches 130×195 focusables). Aucune Hero Card TV.
- **L'accueil mobile** affiche `HomeTrendingCarousel` si des tendances existent, **sinon** `HomeHeroCard` construite sur `state.resumeWatchingList.first()` (lignes 371-381) : c'est exactement ce que F18 interdit.
- **L'aperçu de bande-annonce existe déjà de bout en bout** et est réutilisable tel quel :
  - `HomeViewModel.selectTrendingPreview(item)` / `cancelTrendingPreview()` / `reportTrailerPlaybackFailure(media)` ;
  - `HomeUiState.trailerPreview : TrailerPreviewUiState` (`Poster` / `Preparing` / `Playing(preview)` / `Failed`) ;
  - `GetTrailerPreviewUseCase` + `TrailerRepository` (cache + invalidation) ;
  - `YouTubeTrailerPreview` (WebView + iframe YouTube, `isFocusable = false` et `FOCUS_BLOCK_DESCENDANTS` → ne vole pas le focus D-pad, condition nécessaire côté TV).
  Aucune nouvelle dépendance réseau ni nouveau `UseCase` n'est requis pour la Hero Card.
- **`UserInfo.expiryDate` est déjà une chaîne formatée** par `AuthRepositoryImpl.formatExpiryDate()` : `"dd/MM/yyyy"`, ou `"Illimité"` (timestamp nul/0), ou `"Inconnu"` (parsing en échec). La règle « n'afficher qu'une date fiable » se traite donc **en présentation**, sans toucher à la couche data ni à Room.
- **Bannière TV** : `res/drawable/ic_tv_banner.xml` est un placeholder générique (rectangle `#FF3700B3` + rond blanc + triangle « play »). Le logo tasse CSTV existe déjà en vecteur dans `res/drawable/ic_launcher_foreground.xml` (viewport 108×108 : 3 lignes de vapeur `#9C86FF`, corps de tasse blanc, liseré et soucoupe `#9C86FF`).
- **« Voir tout »** : `HomeScreen.HomeSectionRow` (lignes 780-842) branche déjà sur `isTv` — bouton `Surface3`/`AccentLavande` sur TV, lien texte lavande sur mobile. C'est la seule occurrence TV : `LiveTvComponents` et `SeriesScreen` masquent explicitement le lien quand `isTv`, et restent hors périmètre F18.

### Composants nouveaux

| Composant | Fichier | Rôle |
|---|---|---|
| `TvNavigation` (objet + fonctions pures) | `presentation/navigation/TvNavigation.kt` | Liste ordonnée des destinations, whitelist des routes affichant la barre, sélection de la destination courante, libellé d'expiration. **100 % testable en JVM.** |
| `TvNavigationRail` | `presentation/components/TvNavigationRail.kt` | Barre latérale Compose : état plié/déplié piloté par le focus, en-tête profil, 6 destinations. |
| `HomeTrendingHeroTv` | `presentation/home/components/HomeTrendingHeroTv.kt` | Hero Card TV : image + dégradé + badges + titre/année, focus D-pad, temporisation 1,5 s, aperçu trailer, contrôle Mute. |
| `SeeAllLink` | `presentation/components/SeeAllLink.kt` | Lien « Voir tout » partagé mobile/TV, avec l'état de focus TV. |

### Composants modifiés

| Fichier | Modification |
|---|---|
| `MainActivity.kt` | Héberge la barre latérale TV autour du `Scaffold`/`AppNavGraph` ; porte l'état `railExpanded` ; désarme le `BackHandler` de déconnexion tant que la barre est ouverte. |
| `presentation/home/HomeScreen.kt` | Supprime la rangée de puces TV ; insère la Hero Card en tête sur TV ; supprime le repli « reprise en Hero » sur mobile ; délègue « Voir tout » à `SeeAllLink`. |
| `presentation/home/components/HomeCards.kt` | Suppression de `HomeHeroCard` (devient mort après retrait du repli mobile). |
| `presentation/home/components/HomeTrendingCarousel.kt` | Suppression de `HomeTrendingRowTv` et de `HomeTrendingPosterCardTv`, remplacés par la Hero Card (voir D-4). Le carrousel mobile est inchangé. |
| `res/drawable/ic_tv_banner.xml` | Remplacé par le logo tasse CSTV sur fond violet foncé. |
| `res/values/strings.xml` | Ajout des libellés de la barre, du badge Hero et des descriptions d'accessibilité ; retrait des 6 chaînes `home_nav_*` et de `home_trending`, devenues inutilisées. |

### Modèles de données, API, stockage

Aucun changement : pas de nouvelle entité Room, **pas de migration** (`AppDatabase` reste en version 21), pas de nouvel endpoint Xtream/TMDB/YouTube, pas de nouvelle interface Retrofit (donc pas de règle `-keep` supplémentaire dans `proguard-rules.pro`). F18 est une refonte présentation qui recâble des flux existants.

### Performances

- La barre latérale n'anime qu'une largeur (`animateDpAsState`, 200 ms) sur un `Box` de premier niveau : aucune recomposition des écrans hébergés (le contenu du `NavHost` ne dépend pas de `railExpanded`).
- L'aperçu Hero réutilise l'unique WebView déjà employée sur mobile, jamais plus d'une instance à la fois (l'état `trailerPreview` est mono-valué dans `HomeViewModel`), et elle est détruite dans `onDispose` (`loadUrl("about:blank")` + `destroy()`).
- La temporisation de 1,5 s évite d'instancier une WebView pendant un défilement rapide du focus, ce qui est le point de charge critique sur box TV bas de gamme.

### Sécurité et compatibilité

- Aucune donnée d'identification supplémentaire n'est exposée : la barre n'affiche que `UserInfo.username` (déjà affiché aujourd'hui en en-tête d'accueil TV) et le profil actif. Aucun mot de passe, aucun autre compte.
- Min SDK 21 respecté : `animateDpAsState`, `focusGroup`, `onFocusChanged` et `WebView` sont déjà utilisés par le projet.
- `androidx.tv:tv-material:1.0.0-alpha10` et `tv-foundation` restent en place ; F18 n'ajoute aucune dépendance.

---

## Architecture

### Vue d'ensemble

```
MainActivity (TV)
└─ Box(fillMaxSize)
   ├─ Scaffold { AppNavGraph }        ← padding start = RAIL_COLLAPSED_WIDTH (68.dp) quand la barre est visible
   └─ TvNavigationRail                ← superposé (zIndex au-dessus), largeur animée 68 ↔ 260 dp
        ├─ En-tête profil (avatar, nom, username, "Expire le : …")
        ├─ Séparateur
        └─ 6 destinations (Accueil, TV en direct, Films, Séries, Recherche, Paramètres)
```

Le padding du contenu est **constant** (largeur pliée) : l'ouverture superpose la barre au lieu de repousser le contenu, ce qui satisfait « sans saut visuel du contenu » sans avoir à animer quoi que ce soit côté `NavHost`.

### Flux de données

1. `MainActivity` connaît déjà `profileState` (profil actif), `loggedInUser` (`UserInfo`) et `currentRoute` (via `currentBackStackEntryAsState`). La barre est donc alimentée **sans nouveau ViewModel** ni nouvelle injection Hilt.
2. `TvNavigation.railDestinationFor(currentRoute)` calcule la destination sélectionnée ; `TvNavigation.isRailRoute(currentRoute)` décide de l'affichage.
3. Un clic sur une destination appelle `navController.navigateToRootTab(route)` (chemin unique déjà en place, qui dépile vers une entrée vivante quand elle existe et préserve ViewModel + position de défilement).
4. La Hero Card TV consomme `HomeUiState.trendingList` / `trailerPreview` et pilote `HomeViewModel.selectTrendingPreview` / `cancelTrendingPreview` / `reportTrailerPlaybackFailure` — strictement les mêmes entrées que le carrousel mobile.

### Responsabilités

- **`TvNavigation` (pur, sans Compose)** : ordre des destinations, mapping route → destination, whitelist d'affichage, formatage du libellé d'expiration. Toute la logique testable de F18 vit ici.
- **`TvNavigationRail` (Compose, stateless)** : reçoit `expanded`, `selected`, `profil`, `onExpandedChange`, `onDestinationClick`. Aucune connaissance du `NavController`.
- **`MainActivity`** : hoisting de `railExpanded`, câblage navigation, arbitrage du bouton Retour.
- **`HomeTrendingHeroTv` (Compose, stateless sauf focus local)** : rendu, temporisation, contrôle Mute. Aucune décision métier : le choix « première tendance » est fait par l'appelant.
- **`HomeViewModel`** : inchangé. Il porte déjà le cycle de vie de l'aperçu.

### Décisions techniques

**D-1 — Barre latérale maison plutôt que `NavigationDrawer` de `tv-material`.**
`androidx.tv.material3.NavigationDrawer` (alpha10) est un `Row` : il **redimensionne** le contenu à l'ouverture, ce que la spécification interdit explicitement. `ModalNavigationDrawer` superpose bien, mais imposerait d'envelopper l'intégralité du `NavHost` dans son `content`, expose une API alpha instable et ne permet pas de contrôler finement le repli au retour du focus. Un `Box` + `animateDpAsState` + `focusGroup()` couvre le besoin en ~150 lignes, dans le style des composants TV déjà écrits (`HomeTrendingRowTv`). Les composants `tv-material` (`Button`, `Text`) restent utilisés à l'intérieur.

**D-2 — Ouverture/fermeture pilotée par `onFocusChanged { it.hasFocus }` sur le `focusGroup` de la barre.**
`hasFocus` (et non `isFocused`) inclut les descendants : le déplacement du focus d'une destination à l'autre ne referme pas la barre. Le repli est donc obtenu « gratuitement » quand le focus repart vers le contenu, sans écouteur global.

**D-3 — Bouton Retour : désarmement explicite plutôt qu'empilement de `BackHandler`.**
L'ordre de priorité entre plusieurs `BackHandler` actifs est un détail d'implémentation ; on ne s'y fie pas. Le `BackHandler` de déconnexion existant (`MainActivity`, `enabled = isTv && currentRoute == "home"`) devient `enabled = isTv && currentRoute == "home" && !railExpanded`, et un `BackHandler(enabled = isTv && railExpanded)` referme la barre et redonne le focus au contenu via un `FocusRequester`. Comportement déterministe, et la règle « Retour ne déconnecte pas depuis la barre ouverte » est garantie par construction.

**D-4 — La Hero Card remplace la rangée « Tendances du moment » sur TV.**
La spécification impose la Hero en tête sans se prononcer sur la rangée existante ; laisser les deux produirait un doublon de la première tendance. **Décision PO (2026-07-28) : la rangée TV est supprimée**, la Hero Card devient l'unique représentation des tendances sur l'accueil TV. Conséquences : `HomeTrendingRowTv` et son composable privé `HomeTrendingPosterCardTv` sont retirés de `HomeTrendingCarousel.kt` (plus aucun appelant), et la chaîne `home_trending` est retirée de `strings.xml` (plus aucun usage — le carrousel mobile n'affiche pas de titre de section). Les tendances au-delà de la première ne sont plus atteignables depuis l'accueil TV : c'est un choix assumé de sobriété. `HomeUiState.trendingList` reste inchangé (le mobile s'en sert intégralement).

**D-5 — Temporisation de 1,5 s par `LaunchedEffect(hasFocus)` plutôt que par un `Handler`/timer manuel.**
`LaunchedEffect` est annulé automatiquement quand la clé change (perte de focus) ou quand le composable est retiré (navigation, ouverture d'un lecteur) : « une perte de focus avant le délai annule le lancement » et « l'aperçu ne survit pas hors de son contexte » deviennent des propriétés structurelles, pas des cas à traiter. Le passage en arrière-plan est couvert en réutilisant l'observateur `Lifecycle` déjà écrit pour le carrousel mobile (`ON_STOP` → `cancelTrendingPreview()`), et `onDispose` appelle également `cancelTrendingPreview()`.

**D-6 — Le bouton Mute est un enfant focusable de la Hero, à l'intérieur du même `focusGroup`.**
Il n'est composé que lorsque l'aperçu est réellement révélé (`onRevealed` de `YouTubeTrailerPreview`), pour ne pas exposer un contrôle sans effet pendant la phase poster. Comme le suivi de focus de la Hero utilise `hasFocus` (D-2), passer sur le bouton Mute n'interrompt pas l'aperçu.

**D-7 — Date d'expiration filtrée en présentation par une fonction pure.**
`TvNavigation.expiryLabel(expiryDate: String?): String?` ne renvoie `"Expire le : DD/MM/YYYY"` que si l'entrée correspond à `^\d{2}/\d{2}/\d{4}$`. `"Illimité"`, `"Inconnu"`, `null` et le vide renvoient `null` → la ligne est masquée. On ne touche ni à `UserInfo` ni à `AuthRepositoryImpl` : aucune régression possible sur l'authentification ou la session hors ligne.

**D-8 — Bannière TV : vecteur unique, chemins repris du logo lanceur.**
`ic_tv_banner.xml` passe en viewport 320×180 : fond `#FF1A1330` (violet foncé de `DarkBackgroundGradientStart`, cohérent avec le thème mobile), puis un `<group>` `translate`/`scale` qui replace tel quel le tracé 108×108 de `ic_launcher_foreground` (vapeur `#9C86FF`, tasse blanche, soucoupe lavande). Aucun asset bitmap, aucune duplication de tracé à maintenir en double — et le rendu reste net à toutes les densités de bannière Android TV.

**D-9 — « VOIR TOUT » : un seul composable pour les deux plateformes.**
`SeeAllLink(isTv, onClick)` remplace les deux branches actuelles de `HomeSectionRow`. Sur TV, le lien est rendu focusable par `Modifier.clickable` (qui rend le nœud focusable), au repos en `AccentLavande` sans fond, et au focus avec `AccentLavande.copy(alpha = 0.22f)` + `RoundedCornerShape(8.dp)` + libellé en capitales (`stringResource(R.string.home_see_all).uppercase()`), en 14 sp pour la lisibilité à distance. La destination (`onSeeAll`) n'est pas touchée.

**D-10 — Suppression des puces de navigation TV de l'accueil.**
La barre latérale rend la rangée `TvButton` redondante et lui volerait le focus au démarrage. Les 6 chaînes `home_nav_*` sont retirées de `strings.xml` dans le même mouvement. L'icône Paramètres de l'en-tête d'accueil est conservée (elle sert aussi au mobile).

### Fichiers concernés

**Créés**
- `app/src/main/java/com/cstv/app/presentation/navigation/TvNavigation.kt`
- `app/src/main/java/com/cstv/app/presentation/components/TvNavigationRail.kt`
- `app/src/main/java/com/cstv/app/presentation/components/SeeAllLink.kt`
- `app/src/main/java/com/cstv/app/presentation/home/components/HomeTrendingHeroTv.kt`
- `app/src/test/java/com/cstv/app/presentation/navigation/TvNavigationTest.kt`

**Modifiés**
- `app/src/main/java/com/cstv/app/MainActivity.kt`
- `app/src/main/java/com/cstv/app/presentation/home/HomeScreen.kt`
- `app/src/main/java/com/cstv/app/presentation/home/components/HomeCards.kt`
- `app/src/main/java/com/cstv/app/presentation/home/components/HomeTrendingCarousel.kt`
- `app/src/main/res/drawable/ic_tv_banner.xml`
- `app/src/main/res/values/strings.xml`
- `app/build.gradle.kts` (`versionCode`/`versionName` à la livraison)

**Non modifiés (vérifié)** : `AppDatabase`/`Migrations.kt`, `proguard-rules.pro`, `AndroidManifest.xml` (`android:banner` pointe déjà sur `@drawable/ic_tv_banner`), `HomeViewModel`, `TrailerRepository`, `GetTrailerPreviewUseCase`, `AuthRepositoryImpl`, `UserInfo`.

### Dépendances

Aucune nouvelle dépendance Gradle. Tout s'appuie sur ce qui est déjà déclaré : `androidx.tv:tv-material`/`tv-foundation` 1.0.0-alpha10, compose-bom 2024.02.02, navigation-compose 2.7.7, Coil 2.6.0, WebView plateforme.

### Risques techniques

| # | Risque | Impact | Parade |
|---|---|---|---|
| R-1 | Le focus initial d'un écran TV atterrit dans la barre au lieu du contenu, ou reste piégé dedans. | Navigation D-pad cassée sur tous les écrans principaux. | Barre en **fin** d'ordre de composition dans le `Box` mais contenu doté d'un `FocusRequester` demandé à l'entrée de route ; `focusGroup()` sur la barre pour que la sortie latérale droite retrouve le contenu. À valider écran par écran (`home`, `tv`, `movies`, `series`, `search`, `settings`). |
| R-2 | `WebView` + D-pad sur box TV : vol de focus ou lecture erratique. | L'aperçu bloque la navigation. | `YouTubeTrailerPreview` neutralise déjà le focus (`isFocusable = false`, `FOCUS_BLOCK_DESCENDANTS`, `pointer-events:none` côté iframe). Aucun changement à y apporter ; l'échec de chargement retombe sur le poster via `onPlaybackError`. |
| R-3 | Autoplay YouTube indisponible ou lent sur certaines box (WebView ancienne, réseau lent). | Hero « vide » pendant plusieurs secondes. | Le poster reste affiché au-dessus de la WebView jusqu'à `REVEAL_DELAY_MS` puis fondu ; `TrailerPreviewUiState.Failed` conserve le poster et l'accès aux détails. Comportement déjà éprouvé sur mobile. |
| R-4 | Superposition de la barre dépliée sur le contenu (260 dp) masquant un élément focusé. | Gêne visuelle. | Acceptable et voulu : la barre n'est dépliée que lorsque le focus y est, donc l'élément de contenu concerné n'est pas actif. |
| R-5 | La suppression de `HomeHeroCard` casse une utilisation résiduelle. | Compilation. | Vérifié : une seule utilisation, à la ligne 373 de `HomeScreen.kt`, elle-même supprimée par F18. |
| R-6 | `AGENTS.md` documente encore le « double système de navigation » (enum `AppScreen`), obsolète. | Décisions futures fondées sur une doc fausse. | Corriger `AGENTS.md` à l'étape 9 (documentation) de F18. |

### Contraintes de performance retenues

- Ouverture/fermeture de la barre : animation ≤ 250 ms, aucune recomposition du `NavHost`.
- Lancement de l'aperçu : strictement après 1 500 ms de focus continu, une seule WebView vivante à la fois, détruite au `onDispose`.
- Accueil TV utilisable et complet sans tendance, sans image et sans bande-annonce.

### Stratégie de tests (JVM uniquement)

Conformément à `AGENTS.md`, aucun test sur device/émulateur n'entre dans les critères de validation. La logique testable est volontairement extraite dans `TvNavigation` :

- ordre exact des 6 destinations ;
- `isRailRoute` : vrai pour `home`/`tv`/`movies`/`series`/`search`/`settings`, faux pour `vod_details`, `series_details`, `live_player`, `vod_player`, `series_player`, `login`, `null` ;
- `railDestinationFor` : route racine → destination correspondante, route hors barre → `null` ;
- `expiryLabel` : `"31/12/2026"` → `"Expire le : 31/12/2026"` ; `"Illimité"`, `"Inconnu"`, `""`, `null`, `"31-12-2026"` → `null`.

Les comportements purement visuels (largeur animée, bordure de focus, dégradé) ne sont pas testés : `AGENTS.md` exclut explicitement les tests de layout sans logique.

---

## Découpage des tâches

### 1. Créer la logique pure de navigation TV et ses tests JVM

- [x] Créer `TvNavigation` et `TvNavigationTest`.

Objectif :
Centraliser l'ordre des six destinations, le mapping route → destination, la whitelist des routes affichant la barre et le libellé d'expiration fiable. Cette tâche isole toute la logique non visuelle afin de la rendre testable sans appareil.

Fichiers :
- `app/src/main/java/com/cstv/app/presentation/navigation/TvNavigation.kt`
- `app/src/test/java/com/cstv/app/presentation/navigation/TvNavigationTest.kt`

Validation :
- Les six destinations sont retournées dans l'ordre fonctionnel défini.
- Les routes racines sont mappées correctement et les routes détails/lecteurs/login sont exclues.
- `expiryLabel` n'affiche que les dates au format `DD/MM/YYYY`.
- Les tests unitaires ciblés passent.

### 2. Créer la barre latérale TV stateless

- [x] Créer `TvNavigationRail`.

Objectif :
Rendre la barre ancrée à gauche, pliée/dépliée selon le focus, avec en-tête profil/session, séparation et destinations, sans dépendre directement de `NavController`.

Fichiers :
- `app/src/main/java/com/cstv/app/presentation/components/TvNavigationRail.kt`
- `app/src/main/res/values/strings.xml`

Validation :
- La barre reçoit son état, sa destination sélectionnée et ses callbacks de l'appelant.
- Le focus d'un descendant maintient la barre ouverte ; sa sortie demande le repli.
- L'expiration absente, invalide, « Illimité » ou « Inconnu » n'est pas rendue.
- Les six libellés et descriptions d'accessibilité sont localisés.

### 3. Intégrer la barre au conteneur de navigation TV

- [x] Câbler la barre dans `MainActivity`.

Objectif :
Héberger la barre au-dessus du contenu TV avec un décalage constant correspondant à sa largeur pliée, synchroniser route et sélection, et garantir le comportement Retour attendu.

Fichiers :
- `app/src/main/java/com/cstv/app/MainActivity.kt`

Validation :
- La barre est affichée uniquement sur `home`, `tv`, `movies`, `series`, `search` et `settings` en mode TV.
- Une destination utilise le chemin existant vers la racine et actualise immédiatement la sélection.
- Retour ferme d'abord une barre ouverte et rend le focus au contenu ; la déconnexion ne peut pas se déclencher dans cet état.
- La largeur du contenu ne varie pas entre les états plié et déplié.

### 4. Créer la Hero Card TV des tendances et son cycle d'aperçu

- [x] Créer `HomeTrendingHeroTv`.

Objectif :
Afficher la première tendance exploitable en Hero Card TV, déclencher l'aperçu existant après 1,5 seconde de focus continu et exposer le contrôle audio au D-pad sans voler le focus.

Fichiers :
- `app/src/main/java/com/cstv/app/presentation/home/components/HomeTrendingHeroTv.kt`
- `app/src/main/res/values/strings.xml`

Validation :
- Poster, dégradé, badge, type, titre, année et état de focus sont rendus à partir de la tendance fournie.
- L'activation appelle l'ouverture de la fiche de détails prévue.
- La temporisation est annulée à la perte de focus et l'aperçu est annulé à la sortie du composable ou de l'arrière-plan.
- Le bouton Mute/Unmute n'est disponible qu'une fois l'aperçu révélé et reste atteignable au D-pad.
- Une erreur ou l'absence de bande-annonce conserve poster et navigation.

### 5. Remplacer les tendances TV actuelles et retirer la Hero de reprise mobile

- [x] Câbler la Hero TV dans l'accueil et supprimer les composants devenus obsolètes.

Objectif :
Faire de la Hero l'unique représentation des tendances sur TV, supprimer les puces de navigation TV et empêcher définitivement une reprise de lecture de devenir Hero sur mobile.

Fichiers :
- `app/src/main/java/com/cstv/app/presentation/home/HomeScreen.kt`
- `app/src/main/java/com/cstv/app/presentation/home/components/HomeCards.kt`
- `app/src/main/java/com/cstv/app/presentation/home/components/HomeTrendingCarousel.kt`
- `app/src/main/res/values/strings.xml`

Validation :
- Sans tendance, l'accueil TV reste utilisable et aucune Hero n'est affichée.
- Avec une tendance, seule la Hero TV est affichée ; la rangée d'affiches TV n'est plus composée.
- Le carrousel mobile de tendances reste inchangé.
- Le repli mobile basé sur `resumeWatchingList.first()` et `HomeHeroCard` n'existe plus.
- Les importations, composables et chaînes devenus sans usage sont supprimés.

### 6. Unifier le lien « Voir tout »

- [x] Créer `SeeAllLink` et le brancher à `HomeSectionRow`.

Objectif :
Remplacer les branches mobile/TV par un composant partagé qui conserve les destinations existantes et rend le focus TV lisible à distance.

Fichiers :
- `app/src/main/java/com/cstv/app/presentation/components/SeeAllLink.kt`
- `app/src/main/java/com/cstv/app/presentation/home/HomeScreen.kt`

Validation :
- Le lien mobile garde son apparence et son action existantes.
- Sur TV, le lien est focusable, lavande sans fond au repos et possède un fond lavande translucide avec coins arrondis au focus.
- L'action `onSeeAll` et les destinations associées ne changent pas.

### 7. Remplacer la bannière Android TV par l'identité CSTV

- [x] Mettre à jour le vecteur `ic_tv_banner`.

Objectif :
Remplacer le placeholder générique par la tasse CSTV, ses vapeurs lavande et le fond violet foncé, en réemployant les tracés du logo existant.

Fichiers :
- `app/src/main/res/drawable/ic_tv_banner.xml`

Validation :
- La bannière conserve un vecteur 320×180 et un fond violet foncé.
- Le logo tasse et les trois vapeurs lavande sont présents et lisibles.
- Aucun asset bitmap ni modification du manifeste n'est nécessaire.

### 8. Effectuer la validation automatisée et la revue de cohérence

- [x] Vérifier compilation, tests, lint et absence de régression documentaire.

Objectif :
Confirmer que la refonte de présentation compile, que la logique pure est couverte et que les suppressions ne laissent aucune référence morte.

Fichiers :
- Tous les fichiers F18 modifiés aux tâches 1 à 7.
- `app/build.gradle.kts` uniquement lors de la livraison, pour la version cible validée.

Validation :
- `./gradlew testDebugUnitTest`, `./gradlew assembleDebug` et `./gradlew lintDebug` passent.
- `git diff --check` ne remonte aucun espace invalide.
- La recherche ne trouve plus de référence active à `HomeHeroCard`, `HomeTrendingRowTv`, `HomeTrendingPosterCardTv`, `home_trending` ni aux six `home_nav_*` supprimés.
- La vérification manuelle TV, si elle est réalisée ultérieurement, est rapportée séparément des contrôles automatisés.

## Ordre d'implémentation retenu

1. Tâche 1 : logique pure et tests, socle de sélection/visibilité de la barre.
2. Tâches 2 et 3 : composant puis intégration de navigation, afin de sécuriser le focus et Retour avant les changements de l'accueil.
3. Tâches 4 et 5 : Hero puis remplacement ciblé des composants existants.
4. Tâches 6 et 7 : finition des deux éléments visuels indépendants.
5. Tâche 8 : validation intégrée après l'ensemble de l'implémentation.

---

## Correction (Étape 7)

Statut : sans correction requise.

Le ticket ne contenait aucun retour de review classé Critique, Majeur ou Mineur à corriger. Aucun changement de code n'a donc été effectué à cette étape.

## Validation finale (Étape 8)

Statut : VALIDATED

- `./gradlew --no-daemon testDebugUnitTest assembleDebug --console=plain -q` : succès (code de sortie 0).
- `./gradlew --no-daemon lintDebug --console=plain -q` : succès (code de sortie 0).
- `git diff --check` : succès, aucun espace invalide.
- Les références actives à `HomeHeroCard`, `HomeTrendingRowTv`, `HomeTrendingPosterCardTv`, `home_trending` et aux six chaînes `home_nav_*` supprimées sont absentes ; les seules occurrences restantes sont les mentions historiques du présent ticket.

Les validations sur appareil ou émulateur Android TV ne font pas partie des critères automatisés du projet et n'ont pas été exécutées.

---

## Étape 10 — Livraison (2026-07-28)

Statut : RELEASED

- Version : `v1.63.0` (SemVer minor pour une nouvelle fonctionnalité)
- Code de version : `16300`
- Date : 2026-07-28
- Commit : `:sparkles: feat(tv-navigation): implement collapsible navigation rail, trending hero card with trailer preview, and see-all styling (F18)`
- Tag : `v1.63.0`

