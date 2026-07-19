# Évolutions Fonctionnelles (Backlog Actif)

Ce document rassemble les évolutions fonctionnelles et les fonctionnalités (features) ouvertes ou planifiées pour l'application. 

Une fois qu'une fonctionnalité est implémentée et validée, sa description/son prompt doit être déplacé dans le fichier d'archive correspondant : `docs/archive/evolutions-fonctionnelles-terminees.md` afin de garder ce document léger et facile à lire par les agents de développement.

---

## 🎯 Évolutions Actives / Backlog

### 🎬 Feature F1 : Tuile « Tendances du moment » sur l'Accueil (TMDB)

**Objectif** : remplacer la tuile hero de l'Accueil (actuellement `HomeHeroCard` = dernier média lu, `state.resumeWatchingList.first()`) par un carrousel des **5–10 films/séries tendances du moment**, récupérés via l'API **TMDB**, puis **rapprochés du catalogue IPTV local** par recherche approximative. Seuls les titres réellement présents dans le catalogue de l'utilisateur sont affichés (clic → ouvre le média ; jamais de lien mort).

**Décisions de cadrage validées** :
- **API : TMDB** (The Movie Database). Endpoint `/trending/all/week` (ou `/movie` + `/tv`). Clé API gratuite requise.
- **N'afficher que les titres présents** dans le catalogue local après matching.
- Repli : si pas de clé, hors-ligne, ou aucun match → réafficher `HomeHeroCard` (dernier média lu) tel quel. Pas de tuile vide.

> ⚠️ **Alerte périmètre (AGENTS.md)** : introduit une **API réseau externe** en plus de Xtream. Ce n'est pas un protocole IPTV concurrent (donc pas interdit par le périmètre strict), mais c'est une nouvelle dépendance. Points de vigilance imposés par AGENTS :
> - **Clé TMDB jamais en dur ni versionnée** : la stocker dans `local.properties` (gitignored) → exposée via `BuildConfig.TMDB_API_KEY` dans `build.gradle.kts`. Documenter l'obtention de la clé dans le README.
> - Gestion d'erreur réseau complète (timeout, pas de clé, pas d'internet) → repli silencieux sur `HomeHeroCard`, jamais de crash ni de stack trace.
> - Le titre IPTV est « sale » (ex. `|FR| Dragon Ball Z 1080p MULTI`) : le matching doit normaliser (retirer tags langue/qualité/résolution/crochets) des deux côtés.

**Ordre de livraison : 1 → 2 → 3 → 4.**

#### Tâche 1 — Data : client TMDB + configuration de la clé
**Modèle : Sonnet 5 · Effort : M**
> Ajoute l'accès à l'API TMDB, sans jamais versionner la clé.
> 1. Déclare `TMDB_API_KEY` dans `local.properties` (déjà gitignored) et expose-la via `buildConfigField` dans `app/build.gradle.kts` (`BuildConfig.TMDB_API_KEY`), avec une valeur vide par défaut si absente. Documente l'obtention de la clé dans le README.
> 2. Crée un `TmdbApiService` Retrofit (nouvelle instance OkHttp/Retrofit dédiée, base `https://api.themoviedb.org/3/`) + DTOs pour `/trending/all/week` (titre, `media_type`, `poster_path`, `release_date`/`first_air_date`, `id`), parsing défensif (champs int/string incohérents, null).
> 3. Modèle domain `TrendingTitle(tmdbId, title, isMovie, year, posterUrl)`. Repository `TrendingRepository` (interface `domain`, impl `data`) : `getTrending(): List<TrendingTitle>` ; renvoie liste vide si clé absente/erreur réseau (jamais d'exception propagée à la présentation). Cache court en mémoire (ex. 3 h) pour éviter de spammer TMDB.
> 4. Tests : parsing DTO TMDB (cas sales), comportement clé vide → liste vide.

#### Tâche 2 — Domain : matching approximatif tendances ↔ catalogue local
**Modèle : Opus 4.8 · Effort : M**
> Crée la logique pure de rapprochement entre un titre TMDB propre et les titres IPTV « sales ».
> 1. Objet pur testable `TitleNormalizer` dans `domain/model/` : normalise un titre IPTV en retirant tags langue (`FR`, `MULTI`, `VOSTFR`…), qualité/résolution (`1080p`, `720p`, `4K`, `HDR`, `x265`…), crochets/pipes/séparateurs, année entre parenthèses, puis lowercase + trim + espaces multiples. Réutilise l'esprit de `GenreParser`/`ReleaseYearParser` (défensif, testable).
> 2. Objet `ApproximateTitleMatcher` : score de similarité entre titre TMDB normalisé et titre IPTV normalisé (ex. égalité après normalisation, sinon distance de Levenshtein ou ratio de tokens communs au-dessus d'un seuil). Documente le seuil retenu.
> 3. `GetTrendingInCatalogUseCase` : `getTrending()` (Tâche 1) → pour chaque `TrendingTitle`, cherche le meilleur `VodStream`/`SeriesStream` du cache local (respecte le type movie/tv et exclut les catégories masquées comme `AdvancedCatalogSearchUseCase`) au-dessus du seuil ; garde au plus 10 résultats dédupliqués, dans l'ordre TMDB. Exécution hors thread Main (`Dispatchers.Default`).
> 4. Tests unitaires : normalisation (titres sales variés), matching (vrais/faux positifs — « War » ne matche pas « Warrior »), sélection top 10 + exclusion catégories masquées.

#### Tâche 3 — Presentation : carrousel tendances (remplace le hero)
**Modèle : Sonnet 5 · Effort : M**
> Remplace la tuile hero de l'Accueil par le carrousel tendances.
> 1. `HomeViewModel` : charge les tendances-en-catalogue au démarrage (état `trendingList`), avec repli sur `resumeWatchingList.first()` si vide.
> 2. `HomeScreen` (mobile) : si `trendingList` non vide, afficher un carrousel horizontal (poster TMDB via Coil, titre, badge « Tendance ») à la place de `HomeHeroCard` ; sinon garder `HomeHeroCard` (dernier média lu) inchangé. Clic → ouvre le détail Film/Série existant.
> 3. TV : variante focusable D-pad cohérente avec le reste (cf. patterns TV existants), ou repli hero si trop complexe — signaler le choix.
> 4. Tests ViewModel : trending non vide → carrousel, vide → repli.

#### Tâche 4 — Vérification & fallback bout-en-bout
**Modèle : Haiku 4.5 · Effort : S**
> Valide les chemins de repli sans clé/hors-ligne (pas de crash, hero affiché), `assembleDebug` + `lintDebug` + `testDebugUnitTest`, et documente la clé TMDB dans le README.

---

### ❌ Feature F2 : Bouton « effacer » (croix) dans les champs de saisie
**Modèle : Sonnet 5 · Effort : S**

> Ajoute une petite croix (×) à droite de chaque champ texte pour vider la saisie en un clic, visible uniquement quand le champ est non vide.
> - Cible tous les `OutlinedTextField` de saisie : le champ de recherche de `SearchScreen`, le `CategorySearchField` partagé (`presentation/components/CatalogFilterComponents.kt`), et les champs de l'écran de connexion (`presentation/login/`).
> - Implémentation : `trailingIcon` conditionnel (`if (value.isNotEmpty())`) avec `Icons.Default.Close`, `onClick` = `onValueChange("")`. Factoriser si un composant de champ partagé s'y prête, sinon appliquer champ par champ.
> - Accessibilité : `contentDescription` (string ressource, ex. « Effacer »). Sur TV, rendre la croix focusable au D-pad seulement si le champ est utilisé sur TV.
> - Tests : non prioritaires (UI pure), mais vérifier le rendu conditionnel si un test de composant existe déjà.

---

## 💡 Idées futures / Nouveau Backlog

*Ajoutez ici vos nouvelles idées de fonctionnalités pour les prochaines sessions de développement.*
