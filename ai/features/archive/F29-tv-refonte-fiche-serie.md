# F29 - TV Refonte Fiche Série

## Informations générales

Status:
RELEASED

Version:
v1.77.0

Commit:
✨ feat(tv): refonte de la fiche série Android TV (F29)

Date:
2026-08-05

Created:
2026-08-05

---

# 1. Description

Refonte majeure de la fiche détail d'une **Série (VOD)** sur **Android TV** d'après les maquettes et exigences fournies par l'utilisateur :

- **Écran d'Accueil / Arrivée (Main view)** :
  - Identique au design cinéma des films : affiche grand format plein bord occupant la moitié gauche de l'écran avec fondu dégradé horizontal vers la droite.
  - Colonne de droite avec le titre en très grand, ligne de métadonnées épurée (année | genres | note), synopsis, réalisateur, acteurs et rangée d'actions (favoris / j'aime / je n'aime pas) séparées par des filets.
  - Bouton principal de lecture large et arrondi, sans icône, texte seul :
    - Si aucun historique de lecture : **« LIRE LA SÉRIE »**.
    - Si reprise possible d'un épisode : **« REPRENDRE SXXEXX »** (ex: "REPRENDRE S01E02").
    - **Sous ce bouton** de reprise, afficher une **barre de progression** de l'épisode ciblé.
  - Le bloc « Titres associés » est **totalement masqué** à l'ouverture.

- **Transition vers le sélecteur de saisons / épisodes (Dpad DOWN)** :
  - Depuis le bouton principal de lecture de l'écran d'arrivée, un appui sur le bouton **Bas (Dpad DOWN)** fait glisser/baisser l'écran d'un cran complet pour afficher un second écran/panneau dédié au choix des épisodes.
  - Ce panneau contient :
    - Un sélecteur de saisons sous forme de **gélules (pills) horizontales**.
    - La liste des épisodes de la saison sélectionnée, affichés avec : numéro d'épisode, titre, description, vignette, et barre de progression de lecture si entamé.
    - Tout en bas de cette liste, le bloc **« Titres associés » est visible et dépasse (peek)**.

- **Interaction de fin de liste vers les Titres associés (Dpad DOWN au dernier épisode)** :
  - Si l'utilisateur navigue jusqu'au **dernier épisode** de la saison et appuie sur **Bas (Dpad DOWN)**, le bloc « Titres associés » remonte complètement pour devenir entièrement visible, le focus s'y déplace pour permettre un défilement horizontal.
  - Fleuve inverse (Dpad UP) depuis le bloc remonte le focus sur les épisodes.

La fiche **mobile** et la fiche **série mobile** ne sont pas modifiées.

---

# 2. Contexte

La fiche série TV actuelle (`SeriesDetailsScreen.TvLayout`) est encombrée : elle affiche l'affiche à gauche (240 dp) avec les saisons listées verticalement juste en dessous dans un menu étroit, tandis que la colonne de droite contient à la fois les informations globales de la série, le bouton de lecture d'épisode ciblé, et la liste verticale complète de tous les épisodes de la saison courante. Cette mise en page est dense et s'éloigne de la charte moderne de l'application (AccentLavande). De plus, l'accès aux épisodes est direct et rigide sans transition fluide de type "double couche" ou changement d'écran cinéma vers sélecteur d'épisodes.

---

# 3. Objectif

Créer une expérience de navigation cinéma immersive haut de gamme sur Android TV pour les séries :
1. Séparer l'expérience en deux phases/écrans virtuels : l'**Accueil de fiche** (Focus principal et esthétique cinéma) et le **Sélecteur d'épisodes** (Navigation fonctionnelle précise).
2. Offrir un indicateur visuel de progression de lecture direct sous le bouton de reprise de l'épisode d'accueil.
3. Fluidifier le passage entre le sélecteur d'épisodes et les recommandations (Titres associés) à l'aide d'un effet de remontée dynamique animée sur le dernier item.

---

# 4. Spécification fonctionnelle

## User story

En tant qu'utilisateur Android TV, lorsque j'ouvre une série, je veux arriver
sur une fiche cinéma lisible à distance, centrée sur l'action de lecture. Je
veux pouvoir accéder d'un appui Bas au choix de la saison et des épisodes, puis
aux titres associés sans perdre le contexte ni le focus de la télécommande.

## Périmètre

- La refonte concerne uniquement `SeriesDetailsScreen` en mode Android TV.
- La fiche série mobile, les données de série, la lecture, les téléchargements,
  les favoris, les notes et la sélection des titres associés gardent leurs
  comportements existants.
- La maquette de référence est la fiche série et la planche
  `refonte-fiche-serie-episodes.png` dans `docs/design-reference/`. Les tokens
  existants de la charte (notamment `AccentLavande` et les surfaces sombres)
  sont conservés.

## Vue Hero — arrivée sur la fiche

1. L'ouverture d'une série affiche la vue Hero, jamais directement le
   sélecteur d'épisodes.
2. La moitié gauche de l'écran est occupée par l'affiche grand format de la
   série, fondue horizontalement vers le fond sombre à droite. La colonne de
   droite contient, dans cet ordre : titre, métadonnées (année, genres, note),
   synopsis, réalisateur, acteurs, actions puis action principale de lecture.
3. Les actions Favoris, J'aime et Je n'aime pas restent disponibles avec leur
   état actuel et sont séparées visuellement par des filets. Les crédits
   restent sélectionnables et déclenchent la recherche existante.
4. Le focus initial est placé sur le bouton de lecture. Aucun bloc « Titres
   associés » n'est visible dans cette vue.
5. Si un épisode repris est disponible selon les règles de reprise existantes,
   le libellé est `REPRENDRE SXXEXX` et la lecture ouvre cet épisode à sa
   position mémorisée. Une barre fine lavande sur piste sombre est affichée
   immédiatement sous ce bouton ; sa proportion correspond à la progression de
   l'épisode repris, comme les barres visibles dans la liste d'épisodes de la
   maquette.
6. Sinon, le libellé est `LIRE LA SÉRIE` et lance le premier épisode disponible
   de la série. Aucune barre de progression n'est affichée dans ce cas.
7. Le bouton de lecture est large, arrondi et textuel : il n'affiche pas
   d'icône.

## Vue Épisodes — navigation depuis la vue Hero

1. Un appui D-pad Bas depuis le bouton de lecture bascule vers la vue Épisodes
   par une transition verticale d'un écran complet. La vue Hero quitte la zone
   visible ; elle n'est pas mélangée à la liste des épisodes.
2. Le focus arrive sur la gélule de la saison courante. Les gélules de saisons
   sont disposées horizontalement ; Gauche/Droite change de saison et Bas mène
   à la liste d'épisodes.
3. Chaque épisode affiche son numéro, son titre, sa description, sa vignette
   au format paysage et, lorsqu'il est entamé, une barre de progression lavande
   sur piste sombre avec l'information de reprise pertinente.
4. Une vignette d'épisode manquante est remplacée par un visuel neutre. La
   pochette générale de la série ne doit pas être réutilisée comme vignette.
5. À la sélection d'une autre saison, la liste est remplacée par les épisodes
   de cette saison mais le focus reste sur sa gélule. L'appui Bas suivant place
   le focus sur son premier épisode. Si cette saison ne contient aucun épisode,
   le focus reste sur sa gélule et l'état vide non interactif est affiché à la
   place de la liste.
6. OK sur un épisode lance cet épisode selon le comportement de lecture
   existant. La saison sélectionnée et l'épisode ciblé restent cohérents avec
   les informations affichées dans la liste.

## Titres associés et navigation inverse

1. Si des titres associés existent, leur rangée est placée après le dernier
   épisode et n'est visible qu'en aperçu au bas de la vue Épisodes.
2. Depuis le dernier épisode, un appui D-pad Bas fait remonter la vue de façon
   animée jusqu'à rendre entièrement visible la rangée « Titres associés », puis
   place le focus sur sa première vignette.
3. Gauche/Droite parcourt la rangée ; OK ouvre la fiche du titre choisi avec le
   comportement de navigation existant.
4. D-pad Haut depuis la rangée remet la vue à sa position de repos et replace
   le focus sur le dernier épisode de la saison.
5. Sans titre associé, aucune rangée, aucun aperçu ni remontée ne sont créés ;
   D-pad Bas au dernier épisode ne déclenche pas de déplacement artificiel.

## Retour, cas limites et erreurs

- La touche Retour quitte toujours la fiche série vers l'écran précédent, que
  l'utilisateur se trouve dans la vue Hero ou Épisodes. Elle ne revient pas au
  Hero depuis la vue Épisodes ; le retour de navigation interne se fait avec
  D-pad Haut.
- Sans épisode disponible dans la série, l'action de lecture ne lance rien et
  un état vide explicite est affiché dans la vue Épisodes. Les informations de
  la série et les actions de la vue Hero restent accessibles.
- L'absence d'affiche, de synopsis, de métadonnée, de crédits ou de note ne
  casse pas la composition : la zone concernée est omise ou utilise le visuel
  neutre existant, sans libellé de remplacement trompeur.
- La transition, le changement de saison et la remontée des titres associés ne
  doivent jamais provoquer de lecture automatique, de requête réseau
  additionnelle ni de perte de la sélection courante.

## Critères d'acceptation

- [x] L'ouverture d'une série TV affiche exclusivement la vue Hero, avec
  l'affiche à gauche, les informations à droite et le focus sur l'action de
  lecture.
- [x] Le bouton affiche exactement `LIRE LA SÉRIE` sans reprise, ou
  `REPRENDRE SXXEXX` avec une barre fine lavande sur piste sombre lorsqu'une
  reprise est possible ; il ne contient aucune icône.
- [x] D-pad Bas depuis ce bouton affiche la vue Épisodes et focalise la saison
  courante.
- [x] Gauche/Droite sélectionne une saison sans quitter les gélules ; Bas place
  ensuite le focus sur son premier épisode. Une saison vide conserve le focus
  sur sa gélule et rend un état vide.
- [x] Chaque épisode conserve ses données disponibles et utilise un visuel
  neutre si sa vignette est absente.
- [x] D-pad Bas depuis le dernier épisode rend entièrement visible et focalise
  les titres associés lorsqu'ils existent ; D-pad Haut réalise le trajet
  inverse vers le dernier épisode.
- [x] Retour quitte la fiche directement vers l'écran précédent depuis les deux
  vues.
- [x] Les fiches série mobile et film ne changent pas.

---

# 5. Décisions fonctionnelles actées

1. Depuis la vue Épisodes, Retour ferme la fiche et revient à l'écran précédent.
2. La barre de reprise sous `REPRENDRE SXXEXX` est fine, lavande, sur une piste
   sombre, selon le langage visuel des épisodes de la maquette.
3. Une vignette d'épisode absente est représentée par un visuel neutre, jamais
   par la pochette de série.
4. Gauche/Droite sur les gélules change la saison affichée tout en gardant le
   focus dans la rangée de saisons. Bas entre ensuite sur le premier épisode de
   la saison courante. Il n'existe aucun autre moyen de changer de saison.
5. Dans la liste, Bas parcourt les épisodes un par un ; le focus courant est
   rendu par un état de survol visible. OK lance l'épisode focalisé.
6. Lorsque le dernier épisode est focalisé, un aperçu des titres associés est
   visible. Bas ouvre complètement cette rangée avec le même comportement que
   sur la fiche film.

---

# 6. Spécification technique

## Périmètre architectural

La refonte reste entièrement dans la couche `presentation`. Les données déjà
chargées dans `SeriesDetails`, `SeriesEpisode`, `SeriesState` et
`relatedSeries` suffisent : aucun changement `data`, `domain`, Room, Retrofit,
ViewModel ou navigation n'est nécessaire.

Le chemin mobile reste dans `SeriesDetailsScreen.kt` et conserve sa composition
et ses interactions actuelles. Le chemin TV est extrait dans un composable
dédié afin que la navigation D-pad, les mesures et les animations ne puissent
pas affecter la fiche mobile.

## Fichiers concernés

- `presentation/series/SeriesDetailsScreen.kt` : conserve l'orchestration
  commune, le `SnackbarHost`, le démarrage du trailer et le layout mobile ; la
  branche TV délègue au nouveau layout.
- `presentation/series/SeriesDetailsTvLayout.kt` — **nouveau** : Hero, panneau
  saisons/épisodes, titres associés, état de navigation TV, focus et animations.
- `presentation/components/RelatedTitlesRow.kt` : réutilisé avec son mode TV
  existant (`tvPivotEnabled`, `firstItemFocusRequester`, `LazyListState`) ;
  aucune modification n'est attendue si son contrat actuel suffit.
- `res/values/strings.xml` : libellés TV encore écrits en dur, notamment
  `LIRE LA SÉRIE`, `REPRENDRE SXXEXX`, saisons, épisodes et états vides.
- `test/.../presentation/series/SeriesDetailsTvLayoutTest.kt` — **nouveau** :
  tests JVM des transitions et calculs purs extractibles.

## État d'affichage

Le layout possède un état éphémère, réinitialisé pour chaque `seriesId` :

```kotlin
internal enum class TvSeriesDetailsSection {
    HERO,
    EPISODES,
    RELATED
}
```

`RELATED` n'est pas un troisième écran métier : c'est la position remontée du
panneau Épisodes lorsque sa rangée finale prend le focus. Cet état local ne va
pas dans `SeriesViewModel`, car il ne représente ni une donnée métier ni un état
à restaurer après avoir quitté la fiche.

Le contenu est composé verticalement sans défilement racine :

1. un panneau Hero de la hauteur exacte de l'écran ;
2. un panneau Épisodes placé immédiatement dessous ;
3. la rangée des titres associés placée sous le bloc principal du panneau
   Épisodes, dans une colonne mesurée avec
   `wrapContentHeight(unbounded = true)` afin de ne pas reproduire l'écrasement
   corrigé sur F28.

Une translation `graphicsLayer.translationY`, animée sur 300 ms, applique :

- `0` en `HERO` ;
- `-hauteurÉcran` en `EPISODES` ;
- `-(hauteurÉcran + remontéeAssociés)` en `RELATED`.

La remontée complémentaire reprend la formule de la fiche film, avec les
hauteurs réellement mesurées :

```text
remontéeAssociés = max(
    0,
    hauteurBlocEpisodes + hauteurRangée + réserveBasse - hauteurÉcran
)
```

L'aperçu de la rangée reste masqué tant que le dernier épisode n'est pas
focalisé. Il devient visible lorsque ce dernier reçoit le focus, puis reste
visible pendant l'état `RELATED`. Sans titre associé, la rangée n'est pas
composée et la remontée vaut toujours zéro.

## Vue Hero

- Le panneau gauche reprend la composition cinéma de la fiche film : affiche
  ou trailer dans le même emplacement, visuel neutre en absence d'image, puis
  fondu horizontal vers `Surface1`.
- La colonne droite utilise uniquement les tokens existants : `Surface1/2/3`,
  `AccentLavande`, `AccentLavandeHover`, `TextPrimary`, `TextSecondary`,
  `FavoriteGold`, `RatingLike` et `RatingDislike`.
- Le synopsis est borné par l'espace disponible afin qu'il ne chasse jamais
  les crédits, actions ou bouton de lecture hors écran. Un éventuel texte
  intégral réutilise le motif `Dialog` de la fiche film.
- Le bouton principal reste textuel, sans icône. La cible de lecture est
  calculée avec les règles existantes : épisode incomplet le plus récemment
  consulté, sinon premier épisode disponible par ordre saison/épisode.
- La progression Hero n'est rendue que si `resumePositionMs > 0` et
  `durationMs > 0`; sa fraction est bornée à `[0f, 1f]`. Une durée inconnue ne
  produit pas une barre trompeuse.

## Saisons et épisodes

La rangée des gélules est fixe en haut du panneau. La liste d'épisodes occupe
l'espace restant dans une `LazyColumn`, afin qu'une saison longue ne compose
que ses éléments visibles.

- Les saisons sont ordonnées par `seasonNumber`. Si un panel fournit des
  épisodes sans entrée correspondante dans `details.seasons`, les clés de
  `details.episodes` complètent défensivement la liste des gélules.
- Gauche/Droite déplace le focus entre les gélules. La gélule focalisée devient
  la saison sélectionnée et remplace la liste, mais le focus reste sur cette
  gélule.
- Chaque changement de saison ramène le `LazyListState` des épisodes au début.
  Bas depuis la gélule demande explicitement le focus du premier épisode trié
  par `episodeNum`.
- Une saison vide conserve le focus sur sa gélule : Bas est alors un
  non-événement et l'état vide reste non interactif.
- Si la série ne fournit aucune saison, l'entrée dans le panneau cible un
  conteneur d'état vide focalisable uniquement pour conserver une navigation
  D-pad déterministe ; Haut permet de revenir au Hero.
- Les cartes d'épisode sont identifiées par `episode.id`, affichent un état de
  focus lavande visible, et ne montrent une progression que lorsque position et
  durée sont exploitables. La vignette neutre utilise `Surface3` et
  `TextSecondary`, jamais `details.cover`.

## Contrat de focus D-pad

Des `FocusRequester` dédiés sont mémorisés par `seriesId` pour le bouton Hero,
les gélules, le premier et le dernier épisode courants, l'état vide et la
première vignette associée.

- Ouverture : `rememberTvInitialFocus` cible uniquement le bouton de lecture.
- Bas depuis le bouton : passage à `EPISODES`, puis focus sur la saison courante
  après composition du panneau.
- Gauche/Droite dans les gélules : changement de saison, sans descente.
- Bas depuis une gélule : premier épisode ; Haut : retour au bouton Hero avec
  le panneau en `HERO`.
- Bas dans la liste : épisode suivant. Haut depuis le premier épisode revient à
  la gélule de la saison courante. Bas depuis le dernier épisode, si des titres
  associés existent : passage à `RELATED`, remontée et focus explicite sur la
  première vignette.
- Haut depuis la rangée associée : retour à `EPISODES`, position de repos et
  focus sur le dernier épisode. Gauche/Droite et OK gardent le contrat de
  `RelatedTitlesRow`.
- Retour n'est jamais intercepté par cet état interne : `AppNavGraph` dépile la
  fiche depuis les trois positions.

Les événements directionnels gérés explicitement ne sont consommés que si la
cible existe et que `requestFocus()` réussit. Les contrôles du panneau hors
écran sont exclus de la recherche de focus pendant la transition.

La rangée associée active le sélecteur fixe TV déjà employé sur la fiche film.
Pendant la remontée, sa géométrie est republiée depuis `positionInRoot()` et la
taille de la vignette à chaque valeur de l'animation ; `boundsInRoot()` reste
exclu car il clippe les éléments encore partiellement hors champ.

## Données, réseau et dépendances

Les hypothèses de l'étape 2 sont confirmées :

- `SeriesDetails` fournit l'affiche, les métadonnées, les saisons et la carte
  d'épisodes ;
- `SeriesEpisode` fournit numéro, titre, résumé, durée textuelle,
  `movieImage`, `resumePositionMs`, `durationMs`, `lastAccessedAt` et
  `seasonNum` ;
- `SeriesViewModel` charge déjà les titres associés en parallèle du détail et
  les expose dans `SeriesState`.

Le passage Hero → Épisodes, le changement de saison et la remontée des titres
associés ne déclenchent donc aucun appel réseau, accès Room ou nouvel effet de
ViewModel. Aucune dépendance Gradle, migration Room ni règle ProGuard n'est
requise.

## Contraintes de performance

- Une seule saison est rendue à la fois ; ses épisodes utilisent une
  `LazyColumn` avec clés stables.
- Les listes triées, la cible de reprise et les `FocusRequester` sont mémorisés
  avec les identifiants de série/saison pertinents, sans recalcul à chaque frame.
- Les animations ne modifient que la couche graphique ; aucune mesure réseau ou
  reconstruction de WebView ne doit dépendre de la position affichée.
- Les hauteurs ne sont publiées que par `onSizeChanged`; la formule de remontée
  est pure et ne crée pas de boucle mesure → état → nouvelle mesure.

## Risques techniques et parades

- **Focus d'un panneau hors écran** : désactiver ses cibles jusqu'à ce que son
  état soit actif et utiliser des demandes de focus explicites aux frontières.
- **Saut implicite de `LazyColumn`** : intercepter les transitions premier/
  dernier élément et piloter la cible plutôt que dépendre du candidat
  géométrique choisi par Compose.
- **Rangée associée comprimée** : mesurer la colonne externe sans borne de
  hauteur, conformément à la correction F28.
- **Sélecteur décalé pendant l'animation** : republier les coordonnées pendant
  toute la translation, comme sur F30.
- **Saison vide ou données Xtream incohérentes** : dériver défensivement les
  saisons depuis les métadonnées et les clés d'épisodes, puis garder un chemin
  de focus valide même sans épisode.
- **Régression mobile** : ne déplacer dans le nouveau fichier que le chemin TV
  et ses helpers ; le `MobileLayout` et ses contrôles de téléchargement restent
  inchangés.

## Validation automatisable prévue

Les tests JVM couvriront les règles pures qui portent le risque :

- cible de lecture initiale ou de reprise et fraction de progression bornée ;
- résolution de la saison courante et tri des épisodes ;
- transitions Hero → saisons → premier épisode ;
- changement de saison sans descente automatique ;
- dernier épisode → titres associés et trajet inverse ;
- absence de transition en saison vide ou sans titre associé ;
- calcul de la remontée, y compris rangée absente et hauteur insuffisante.

Le rendu Compose, les contraintes réelles et la géométrie D-pad ne sont pas
testables par la suite JVM actuelle ; ils ne constituent donc pas des critères
de validation finale manuelle ou sur appareil, conformément à `AGENTS.md`.

---

# 7. Architecture

```text
AppNavGraph
  └─ SeriesDetailsScreen
       ├─ mobile → MobileLayout existant
       └─ TV → SeriesDetailsTvLayout
                ├─ HeroPanel
                │    ├─ affiche / trailer + fondu
                │    ├─ métadonnées / crédits / actions
                │    └─ action de lecture + progression
                └─ EpisodesPanel
                     ├─ SeasonPillsRow
                     ├─ EpisodeLazyColumn
                     └─ RelatedTitlesRow (optionnel, position remontée)
```

Flux de données :

```text
SeriesViewModel.state
  → SeriesDetailsScreen
  → SeriesDetailsTvLayout
      → état UI local (section, saison, focus, mesures)
      → callbacks existants
          ├─ onEpisodeSelected
          ├─ onToggleFavorite / onLike / onDislike
          ├─ onSearchQueryTriggered
          └─ onSelectRelated
```

Responsabilités :

- `SeriesViewModel` continue de charger et conserver les données métier ; il ne
  connaît ni panneau, ni gélule, ni position de focus.
- `SeriesDetailsScreen` reste la frontière mobile/TV et le point de branchement
  des callbacks existants.
- `SeriesDetailsTvLayout` possède exclusivement la composition et la machine de
  navigation visuelle TV.
- `RelatedTitlesRow`, `TvInitialFocus` et `TvFocusSelector` conservent leurs
  responsabilités génériques et sont réutilisés sans duplication.

Décisions techniques :

1. Un layout TV séparé est préféré à l'extension du fichier actuel de plus de
   900 lignes : il isole le risque et garantit le non-impact mobile.
2. Une translation mesurée est préférée à un `verticalScroll` racine ou à
   `bringIntoView`, dont les déplacements implicites sont incompatibles avec
   les crans Hero/Épisodes et Épisodes/Associés.
3. La saison et la section sont des états de présentation locaux, pas des états
   de ViewModel.
4. Les routes de focus aux frontières sont explicites ; la recherche
   géométrique de Compose reste utilisée seulement entre épisodes ordinaires.
5. Les contrats existants de lecture, notation, favoris, recherche de crédits,
   trailer et titres associés sont réutilisés, sans nouvelle source de données.

---

# 8. Plan de développement

- [x] F29-1 — Extraire le chemin Android TV et les règles pures

  Objectif : créer `SeriesDetailsTvLayout` sans modifier le rendu mobile et
  rendre testables le choix de lecture, les saisons défensives, la progression
  et la remontée mesurée.

  Fichiers :
  - `presentation/series/SeriesDetailsScreen.kt`
  - `presentation/series/SeriesDetailsTvLayout.kt` (nouveau)

  Validation : le point de branchement TV délègue au nouveau composable ; les
  helpers n'accèdent ni au ViewModel ni au réseau.

- [x] F29-2 — Construire le Hero cinéma et la reprise

  Objectif : composer l'affiche/trailer à gauche, la colonne d'informations à
  droite, les actions existantes et l'action textuelle `LIRE LA SÉRIE` ou
  `REPRENDRE SXXEXX`, avec une barre lavande strictement réservée à une reprise
  exploitable.

  Fichiers :
  - `presentation/series/SeriesDetailsTvLayout.kt`
  - `res/values/strings.xml`

  Validation : le focus initial est le bouton de lecture ; les valeurs nulles
  ou une durée inconnue ne produisent ni crash ni fausse barre de progression.

- [x] F29-3 — Implémenter la navigation Hero vers saisons/épisodes

  Objectif : appliquer la transition verticale mesurée et les routes D-pad
  explicites : Bas depuis Hero vers la gélule, Gauche/Droite entre gélules,
  Bas vers le premier épisode et Haut dans le trajet inverse.

  Fichiers :
  - `presentation/series/SeriesDetailsTvLayout.kt`

  Validation : les cibles d'un panneau hors écran ne sont pas focalisables ;
  une saison vide ou une série sans saison conserve un chemin D-pad stable.

- [x] F29-4 — Composer la liste d'épisodes enrichie

  Objectif : afficher une seule saison dans une `LazyColumn` avec clés stables,
  vignette paysage ou visuel neutre, résumé et progression bornée, puis lancer
  l'épisode ciblé avec le callback existant.

  Fichiers :
  - `presentation/series/SeriesDetailsTvLayout.kt`

  Validation : l'ordre est saison puis numéro d'épisode, le changement de
  saison revient au début sans déplacer le focus hors de la gélule, et la
  fiche mobile conserve ses contrôles de téléchargement.

- [x] F29-5 — Relier les titres associés et le sélecteur fixe

  Objectif : faire apparaître l'aperçu au dernier épisode, remonter la rangée
  sur Bas, focaliser sa première vignette et revenir au dernier épisode sur
  Haut, en réutilisant `RelatedTitlesRow` et `TvFocusSelector`.

  Fichiers :
  - `presentation/series/SeriesDetailsTvLayout.kt`
  - `presentation/components/RelatedTitlesRow.kt` (réutilisation sans
    modification attendue)

  Validation : sans titre associé la transition est absente ; avec titres, la
  formule de remontée utilise les hauteurs mesurées et republie la géométrie du
  sélecteur pendant l'animation.

- [x] F29-6 — Couvrir les règles et vérifier la non-régression

  Objectif : ajouter les tests JVM des règles pures puis exécuter les contrôles
  Gradle requis par l'implémentation.

  Fichiers :
  - `test/.../presentation/series/SeriesDetailsTvLayoutTest.kt` (nouveau)

  Validation : les tests couvrent choix reprise/premier épisode, fraction,
  saisons incomplètes, transitions limites et remontée ;
  `testDebugUnitTest`, `assembleDebug` et `lintDebug` terminent explicitement.

---

# 9. Notes de développement

- Étapes 1 à 6 réalisées le 2026-08-09. Le chemin TV appelle désormais
  `SeriesDetailsTvLayout`, isolé du layout mobile et des contrôles de
  téléchargement : les règles pures, le Hero, les routes D-pad, la liste
  d'épisodes, les titres associés et leurs tests JVM sont couverts.
- Le nouveau layout conserve les callbacks existants et porte localement la
  section visuelle, la saison, les routes de focus et les mesures de remontée.
  Aucun accès réseau, Room, ViewModel, route, migration ou dépendance Gradle
  n'a été ajouté.
- Tests JVM ajoutés : sélection reprise/premier épisode, progression bornée,
  saisons complétées défensivement et remontée des titres associés.
- Vérifications automatisées réussies :
  `./gradlew --no-daemon --max-workers=1 testDebugUnitTest assembleDebug lintDebug`.
  Aucune validation sur appareil ou émulateur n'est requise ni utilisée par ce
  workflow ; une revue dédiée reste nécessaire avant toute validation finale.

---

# 10. Review

Status: RESOLVED

Revue réalisée le 2026-08-09 sur `SeriesDetailsTvLayout.kt` (739 lignes),
la branche TV de `SeriesDetailsScreen.kt`, `strings.xml` et
`SeriesDetailsTvLayoutTest.kt`. Aucun code n'a été modifié pendant cette étape.

## Synthèse

L'architecture livrée respecte la spécification : le chemin TV est bien isolé
dans un fichier dédié, la couche `presentation` n'appelle ni ViewModel ni
réseau supplémentaire, les règles porteuses de risque (saisons défensives,
cible de lecture, fraction bornée, remontée mesurée) sont extraites en
fonctions pures `internal` et testées. Le contrat de focus est explicite aux
frontières et les panneaux hors écran sont neutralisés par
`focusProperties { canFocus = active }`, ce qui écarte le principal risque
identifié à l'étape 3. La touche Retour n'est jamais interceptée, conformément
à la décision 1.

Contrôles exécutés : `./gradlew --no-daemon --max-workers=1 testDebugUnitTest
lintDebug` → `BUILD SUCCESSFUL` ; `:app:compileDebugKotlin --rerun-tasks`
remonte 2 avertissements de dépréciation dans le nouveau fichier.

## Critique

Aucun problème critique. Aucun chemin de navigation ne laisse la télécommande
sans cible focalisable, et aucune fuite de couche ni accès réseau n'a été
introduit.

## Majeur

1. **Le focus est repris par la gélule après un changement de saison**
   - Description : `LaunchedEffect(section, selectedSeason, selectedEpisodes,
     relatedSeries.isNotEmpty())` (`SeriesDetailsTvLayout.kt:202`) redemande le
     focus de la gélule courante à chaque changement de `selectedSeason` ou
     d'identité de `selectedEpisodes`, et pas seulement à l'entrée dans la
     section.
   - Impact : après Gauche/Droite, si l'utilisateur enchaîne rapidement sur
     Bas, l'effet peut ramener le focus sur la gélule et annuler la descente
     vers le premier épisode. Le comportement dépend d'une course entre l'effet
     et la frappe.
   - Correction attendue : déclencher la demande de focus sur la seule
     transition de `section` (et sur le retour explicite via
     `returnToLastEpisode`), pas sur les changements de saison, qui sont déjà
     couverts par les routes clavier.

2. **`indexOf` dans le corps des items de la `LazyColumn`**
   - Description : `val index = selectedEpisodes.indexOf(episode)`
     (`SeriesDetailsTvLayout.kt:684`) fait une recherche linéaire par item et
     compare par `equals` de data class, pas par identité.
   - Impact : coût O(n²) sur une saison longue à chaque recomposition des items
     visibles, et attribution des `FocusRequester` premier/dernier au mauvais
     item si deux épisodes ont des champs identiques (données Xtream
     dupliquées). La spécification exige justement de piloter explicitement les
     bornes premier/dernier.
   - Correction attendue : utiliser `itemsIndexed(selectedEpisodes, key = { _, e
     -> e.id })` et supprimer `indexOf`.

3. **Barre de progression affichée sur tous les épisodes, y compris non vus**
   - Description : `LinearProgressIndicator` de la carte d'épisode
     (`SeriesDetailsTvLayout.kt:736`) est composée inconditionnellement, avec
     `progress = 0f` quand l'épisode n'est pas entamé.
   - Impact : contredit la spécification (§4 « lorsqu'il est entamé » et §6
     « ne montrent une progression que lorsque position et durée sont
     exploitables ») ; une piste sombre vide s'affiche sous chaque épisode
     jamais lu, ce qui brouille la lecture de la maquette.
   - Correction attendue : ne composer la barre que si
     `tvSeriesProgressFraction(episode) > 0f`, comme sur le Hero.

4. **Libellé de reprise trompeur dans la liste d'épisodes**
   - Description : `series_details_resume_info` vaut `%1$s restantes` mais
     reçoit `episode.duration` (`SeriesDetailsTvLayout.kt:734`), c'est-à-dire la
     durée totale de l'épisode, pas le temps restant.
   - Impact : information fausse affichée sur tout épisode entamé ; un épisode
     vu à 90 % annonce toujours la durée complète comme « restantes ».
   - Correction attendue : calculer le restant à partir de
     `durationMs - resumePositionMs` lorsque les deux sont exploitables, sinon
     retomber sur `episode.duration` sans le suffixe « restantes ».

5. **Libellés TV encore écrits en dur**
   - Description : `"Saison $seasonNumber"` (`:641`), `"Réalisateur"` /
     `"Acteurs"` (`:453-454`), `"J'aime"` / `"Je n'aime pas"` /
     `"Ajouter aux favoris"` / `"Dans les favoris"` (`:457-461`), `"Non vu"`
     (`:734`) ne passent pas par `strings.xml`, qui n'a reçu que 4 nouvelles
     clés.
   - Impact : contradiction avec le plan F29-2, qui liste `res/values/strings.xml`
     comme fichier concerné, et avec la convention projet ; localisation et
     relecture des textes impossibles.
   - Correction attendue : externaliser ces libellés, avec une chaîne
     paramétrée pour le nom de saison par défaut.

6. **Couverture de tests inférieure à la validation annoncée**
   - Description : la section « Validation automatisable prévue » et la note
     d'étape 6 annoncent des tests de transitions (Hero → gélule → premier
     épisode, changement de saison sans descente, dernier épisode → titres
     associés, saison vide). `SeriesDetailsTvLayoutTest.kt` ne couvre que
     `tvSeriesSeasonNumbers`, `tvSeriesPlaybackTarget`,
     `tvSeriesProgressFraction` et `tvSeriesRelatedShiftPx` (6 tests).
   - Impact : la machine de navigation, qui porte le risque fonctionnel
     principal, n'a aucune couverture JVM ; le document surestime la
     validation réalisée.
   - Correction attendue : soit extraire la transition en fonction pure
     (`section`, `saison`, `épisodes`, `titres associés` → nouvelle section) et
     la tester, soit corriger explicitement la spécification et les notes pour
     ne plus annoncer ces tests.

## Mineur

1. **Surcharge dépréciée de `LinearProgressIndicator`** (`:477`, `:736`) :
   2 avertissements de compilation ; le reste du dépôt (`HomeCards.kt`,
   `LiveTvComponents.kt`, `DownloadsScreen.kt`) utilise déjà
   `progress = { ... }`. Correction attendue : migrer vers la surcharge lambda.

2. **Remise à zéro du défilement de la rangée associée au changement de
   saison** (`:218`) : `relatedRowState.scrollToItem(0)` n'est demandé par
   aucune spécification et fait perdre la position horizontale des titres
   associés. Correction attendue : supprimer cet effet ou le limiter au
   changement de série.

3. **Noms pleinement qualifiés alors que l'import existe ou est possible** :
   `androidx.compose.foundation.lazy.rememberLazyListState()` (`:618`, déjà
   importé ligne 27), `com.cstv.app.presentation.components.TvInitialFocusState`
   (`:417`), `androidx.compose.ui.graphics.vector.ImageVector` (`:539`).
   Impact : lisibilité. Correction attendue : importer et raccourcir.

4. **Deux sources de vérité pour la césure du Hero** : l'affiche occupe
   `fillMaxWidth(0.48f)` (`:372`) tandis que la colonne d'informations dépend de
   poids `0.95f` / `1.1f` (`:421-424`). Impact : tout ajustement de maquette
   doit modifier deux valeurs indépendantes. Correction attendue : dériver la
   césure d'une constante unique.

5. **`TvSeriesEpisodesPanel` reçoit `details` entier** (`:602`) alors qu'il ne
   s'en sert que pour retrouver le nom d'une saison. Impact : couplage inutile
   et recomposition plus large que nécessaire. Correction attendue : passer une
   `Map<Int, String>` de noms de saisons.

6. **État vide « aucune saison » sans retour visuel de focus** (`:623-634`) :
   le texte est focusable pour garder un chemin D-pad, mais rien n'indique
   qu'il détient le focus. Impact : l'utilisateur ne sait pas que Haut est
   disponible. Correction attendue : ajouter un état de focus visible cohérent
   avec les autres cibles.

7. **Notes de développement partiellement obsolètes** : la section 9 indique
   « Étapes 4 et 5 réalisées » alors que les six sous-tâches sont cochées.
   Impact : historique de livraison peu fiable. Correction attendue : compléter
   les notes pour les étapes 1 à 3 et 6.

## Corrections appliquées — étape 7 (2026-08-09)

- Majeur 1 : la demande de focus du panneau Épisodes ne dépend plus que du
  changement de `section`; changer de saison ne peut plus relancer cet effet.
  Le trajet retour associé → dernier épisode reste explicite.
- Majeur 2 : `LazyColumn` utilise `itemsIndexed` et les indices stables pour
  attacher les `FocusRequester` de début et de fin.
- Majeur 3 et 4 : la barre et son libellé ne sont rendus que pour une reprise
  exploitable; le temps affiché est maintenant calculé depuis
  `durationMs - resumePositionMs`, sinon la durée source reste sans suffixe.
- Majeur 5 : les textes TV sont externalisés, avec les chaînes partagées de
  crédits, favoris et avis, plus les nouvelles clés de saison par défaut et
  d'épisode non vu.
- Majeur 6 : les transitions de panneaux et les destinations de focus aux
  frontières sont extraites en fonctions pures et testées (Hero → Épisodes,
  saison → premier épisode, saison vide, dernier épisode ↔ associés).
- Mineur 1 à 3 : les deux progressions utilisent la surcharge lambda, le
  défilement des associés est réinitialisé uniquement pour une nouvelle série,
  et les noms pleinement qualifiés sont remplacés par des imports.
- Mineur 4 et 5 : la césure Hero est portée par une constante unique et le
  panneau Épisodes reçoit une map de noms de saisons, pas `SeriesDetails`.
- Mineur 6 : l'état vide sans saison possède désormais un contour, fond et
  couleur de focus visibles.
- Mineur 7 : les notes de développement reflètent les six tâches réalisées.

Contrôles de non-régression de l'étape 7 réussis :
`./gradlew --no-daemon --max-workers=1 testDebugUnitTest assembleDebug lintDebug`
→ `BUILD SUCCESSFUL` (2026-08-09). Cette preuve vérifie les corrections mais
ne constitue pas l'étape 8 : le statut global reste `FIXES` jusqu'à la
validation finale demandée séparément.
