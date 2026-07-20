# Évolutions Fonctionnelles (Backlog Actif)

Ce document rassemble les évolutions fonctionnelles et les fonctionnalités (features) ouvertes ou planifiées pour l'application. 

Une fois qu'une fonctionnalité est implémentée et validée, sa description/son prompt doit être déplacé dans le fichier d'archive correspondant : `docs/archive/evolutions-fonctionnelles-terminees.md` afin de garder ce document léger et facile à lire par les agents de développement.

---

## 🎯 Évolutions Actives / Backlog

### 🎯 Feature F-6 : Recommandations personnalisées par profil (« Recommandé pour vous »)

**Objectif** : ajouter sur l'Accueil **deux sections « Recommandé pour vous »** (une Films, une Séries), personnalisées pour le **profil actif** à partir de son historique de visionnage. Principe : déduire les goûts de l'utilisateur (genres + catégories les plus regardés, pondérés par fréquence), scorer tout le catalogue non vu avec ces poids combinés à la **note** et à la **fraîcheur**, retenir le **top 100** par type. Exemple de comportement attendu : un profil qui a regardé 80 % de westerns et 1 seul film d'horreur doit voir les westerns fortement favorisés dans ses recommandations.

**Décisions de cadrage validées avec le PO** :
- **Toute lecture compte** : dès qu'une position de lecture existe pour un média, il nourrit le profil de goûts ET est exclu des recommandations (« déjà vu »). Pas de seuil de progression.
- **Cold start : sections masquées** tant que le profil n'a pas assez d'historique (< 3 médias distincts regardés). Pas de fallback générique (le Top 10 existant joue déjà ce rôle).
- **Cache & rafraîchissement** : le calcul est relancé **à chaque lancement de l'app** (cache mémoire, donc vide au démarrage du process) ; si l'app reste vivante longtemps sans être tuée, un recalcul est **forcé au bout de 24 h** (TTL sur le cache). Entre les deux, les retours sur la Home servent le cache — pas de recalcul à chaque navigation ni pendant la lecture. Un **changement de profil invalide** immédiatement le cache (recos strictement par profil).
- Périmètre : Films + Séries uniquement (pas de recommandations Live TV).

**État des lieux du code** (tout est déjà en place, **aucune migration Room nécessaire**) :
- Historique : `playback_positions` (Room), scopé par profil, exposé via `VodRepository.getAllPlaybackPositions()` / `observeAllPlaybackPositions()` ; `type` = "movie"/"series", `seriesId` renseigné pour les épisodes, `lastAccessedAt` disponible.
- Genres : `vod_streams.genre` / `series_streams.genre` (enrichis en background), à parser avec `GenreParser` (`parseGenres`/`normalize`/`matches`) — jamais de comparaison brute.
- Note : `rating` (string, parse défensif `toDoubleOrNull`). Fraîcheur : `added` (epoch string, parse défensif) ; `releaseYear` en secours.
- Catégories masquées : exclure via `CategoryPreferenceRepository.getPreferences(type).filterValues { it.hidden }` (même pattern que `AdvancedCatalogSearchUseCase`).
- Objets purs de référence dans `domain/model/` : `TopRatedSelector` (score note+added), `RelatedTitlesSelector` (matching par genres partagés) — s'en inspirer, ne pas dupliquer leur logique.
- Sections Home : pattern `HomeSectionRow` + vue développée « Voir tout » (`HomeExpandedSection`) déjà en place dans `HomeScreen`/`HomeViewModel`, variantes mobile + TV.

**Ordre de livraison : 1 → 2.**

#### Tâche 1 — Domain : moteur de recommandation + use case
**Modèle : Opus 4.8 · Effort : L**
> Crée la logique de recommandation, pure et testable, sans toucher à l'UI.
> 1. Objet pur `RecommendationEngine` dans `domain/model/` (aucune dépendance Android/Room, même esprit que `TopRatedSelector`/`RelatedTitlesSelector`) :
>    - **Profil de goûts** : à partir de la liste des médias regardés (genres bruts + `categoryId` de chaque média **distinct** — dédupliquer par média, pas par épisode, pour qu'une série de 50 épisodes ne pèse pas 50×), calcule des poids de fréquence relative par genre normalisé (`GenreParser.normalize`) et par catégorie. 80 % de westerns ⇒ poids western ≈ 0,8.
>    - **Scoring d'un candidat** : `score = 0.40 × genres + 0.20 × catégorie + 0.25 × note + 0.15 × fraîcheur`, où : genres = somme des poids des genres du candidat matchés via `GenreParser` (plafonnée à 1) ; catégorie = poids de sa catégorie ; note = `rating/10` borné 0..1 (0 si illisible) ; fraîcheur = décroissance linéaire sur 180 jours depuis `added` (1.0 aujourd'hui → 0.0 à 180 j, 0 si absent). Constantes nommées et documentées (ajustables).
>    - **Sélection** : exclut les déjà-vus, trie par score décroissant, retourne le **top 100**. Départage stable (score égal → note puis added décroissants).
> 2. `GetRecommendationsUseCase` : lit l'historique du profil actif (positions type "movie" → streamIds vus ; type "series" → seriesIds vus distincts), charge les catalogues Films/Séries du cache (`getVodStreams("all", false)` / `getSeriesStreams("all", false)`), exclut les catégories masquées, applique le moteur séparément pour Films et Séries et retourne les deux top 100. Renvoie des listes vides si l'historique compte < 3 médias distincts (cold start). Exécution intégrale sous `Dispatchers.Default` (catalogue 10k+, parsing genres par item — même précaution que `AdvancedCatalogSearchUseCase`).
> 3. **Cache mémoire dans le use case** (`@Singleton`, ou repository dédié si plus propre) : clé = `profileId`, valeur = résultat + horodatage. Un appel sert le cache si `profileId` identique ET âge < 24 h (constante `TTL` nommée) ; sinon recalcule et remplace. Cache **en mémoire uniquement** (pas de persistance) ⇒ recalcul garanti à chaque lancement d'app ; TTL 24 h ⇒ recalcul forcé si le process survit longtemps. Changement de profil ⇒ clé différente ⇒ recalcul immédiat. Protéger contre les recalculs concurrents (Mutex, même pattern que `TrendingRepositoryImpl`).
> 4. Tests unitaires : pondération (80 % western ⇒ westerns devant), exclusion des déjà-vus et des catégories masquées, dédup par média (série multi-épisodes), cold start < 3, parse défensif rating/added sales, plafond 100, stabilité du tri, **cache : 2e appel même profil ne recalcule pas, TTL expiré recalcule, changement de profil recalcule** (injecter une horloge/`clock` pour tester le TTL sans `System.currentTimeMillis` en dur).

#### Tâche 2 — Presentation : sections « Recommandé pour vous » sur l'Accueil
**Modèle : Sonnet 5 · Effort : M**
> Câble les recommandations dans la Home (mobile + TV).
> 1. `HomeViewModel` : ajoute `recommendedMovies`/`recommendedSeries` au `HomeState`, alimentés par `GetRecommendationsUseCase` à chaque `loadHomeData()` — le cache du use case (Tâche 1) fait le tri : recalcul seulement au premier appel du process, après 24 h, ou après changement de profil ; les autres appels servent le cache instantanément. **Découplé du bloc isLoading principal** (comme le fetch TMDB — ne doit jamais bloquer l'affichage de la Home). ⚠️ Ne PAS brancher le calcul sur `observeAllPlaybackPositions()` (il ré-émet **chaque seconde** pendant une lecture via savePosition) — aucun déclencheur réactif sur l'historique : la stratégie lancement + TTL 24 h + changement de profil suffit (cadrage PO).
> 2. `HomeScreen` : deux `HomeSectionRow` « Recommandé pour vous » (une sous la section Films, une sous la section Séries — rangée d'environ 20 cartes, réutilise `HomeVodMovieCard`/`HomeSeriesShowCard`), masquées si liste vide (cold start). « Voir tout » → vue développée existante (`HomeExpandedSection`) affichant le top 100 en grille. Titres via `strings.xml` (ex. `home_recommended_movies` / `home_recommended_series`).
> 3. TV : mêmes rangées focusables au D-pad (pattern des sections existantes). ⚠️ Rappel : la Home est partagée, mais si un nouvel écran s'avérait nécessaire, il devrait être câblé dans les DEUX navigations (cf. piège AGENTS.md) — ici rester dans HomeScreen pour l'éviter.
> 4. Tests ViewModel : listes remplies → sections visibles, cold start → masquées, changement de profil → nouvelles recos, retours répétés sur la Home → le use case est bien rappelé mais sert son cache (pas de recalcul), pas de recalcul pendant une simple progression de lecture.

> Note : aucune nouvelle table/migration Room, aucun appel réseau nouveau (tout depuis le cache local). Vérifier `assembleDebug` + `lintDebug` + `testDebugUnitTest`.

---

## 💡 Idées futures / Nouveau Backlog

*Ajoutez ici vos nouvelles idées de fonctionnalités pour les prochaines sessions de développement.*
