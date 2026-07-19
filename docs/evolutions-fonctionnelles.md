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

### 📺 Feature F4 : Partage Chromecast dans le lecteur

**Objectif** : permettre de caster le flux en cours de lecture (Live, Film, Série) vers un appareil Chromecast depuis le lecteur, avec bascule lecture locale ↔ Cast et contrôles de base (play/pause, position).

> ⚠️ **Alerte périmètre (AGENTS.md)** : le Chromecast est **explicitement listé hors périmètre** dans AGENTS.md (« Explicitement hors périmètre, à ne jamais ajouter sans qu'on le demande : … Chromecast »). Il est ici **demandé explicitement par le PO**, ce qui lève l'exclusion pour cette feature. Impacts à connaître :
> - Nouvelles dépendances : `androidx.media3:media3-cast` (aligné sur media3 1.4.0) + `com.google.android.gms:play-services-cast-framework`. Nécessite les Google Play Services → **indisponible sur Android TV/AOSP sans GMS** : ne proposer le bouton Cast que sur mobile avec Cast dispo (feature `com.google.android.feature.services` / `CastContext` OK), le masquer proprement sinon.
> - Un `CastOptionsProvider` + déclaration manifest sont requis. Receiver = **Default Media Receiver** (pas d'app receiver custom à héberger).
> - Les URLs de lecture Xtream contiennent les identifiants (username/password) : elles transitent vers l'appareil Cast. Acceptable (réseau local) mais à ne jamais logger.

**Ordre de livraison : 1 → 2 → 3.**

#### Tâche 1 — Setup Cast SDK + détection de disponibilité
**Modèle : Sonnet 5 · Effort : M**
> Intègre le Cast SDK sans casser les builds TV/sans-GMS.
> 1. Ajoute `media3-cast` + `play-services-cast-framework` dans `build.gradle.kts`.
> 2. Crée un `CastOptionsProvider` (receiver = `CastMediaControlIntents.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID`) et déclare-le dans le manifest (`OPTIONS_PROVIDER_CLASS_NAME`).
> 3. Expose une détection robuste « Cast disponible » (GMS présent + `CastContext` initialisable) via un provider injecté (Hilt), pour conditionner l'affichage du bouton. Sur TV/sans-GMS → indisponible, aucun crash.
> 4. Vérifie `assembleDebug` (mobile + variante TV) : aucune régression au démarrage sans Chromecast sur le réseau.

#### Tâche 2 — Lecteur : bascule ExoPlayer ↔ CastPlayer + bouton Cast
**Modèle : Opus 4.8 · Effort : L**
> Câble le Cast dans le lecteur (`presentation/player/`), réutilisant l'item Media3 en cours.
> 1. Introduis un `CastPlayer` (Media3) à côté de l'`ExoPlayer` existant ; un gestionnaire bascule le `Player` actif selon l'état de session Cast (`SessionAvailabilityListener`), en transférant l'item courant et la position (handoff local→cast et cast→local).
> 2. Ajoute le `MediaRouteButton` (ou équivalent Compose) dans l'UI du lecteur, visible uniquement si Cast dispo (Tâche 1). Pendant le cast : afficher un état « Lecture sur <appareil> » + contrôles play/pause/seek pilotant le `CastPlayer`.
> 3. Construis le `MediaItem` casté avec les métadonnées (titre, poster) et le bon type MIME (HLS `application/x-mpegurl` / mp4) à partir de l'URL Xtream. Ne jamais logger l'URL (credentials).
> 4. Gère proprement le cycle de vie (perte de session, mise en arrière-plan, fin de flux) et libère les ressources. Respecte « un ViewModel par écran, pas de logique métier dans le Composable ».
> 5. Tests : logique de sélection du player actif / handoff position (extraire la logique testable hors du Composable).

#### Tâche 3 — Vérification bout-en-bout & non-régression
**Modèle : Haiku 4.5 · Effort : S**
> Valide : lecture locale inchangée sans Chromecast ; bascule cast/local sur mobile avec un appareil réel ; bouton absent sur TV/sans-GMS ; `assembleDebug` + `lintDebug` + `testDebugUnitTest` verts. Documente la limite « mobile + GMS uniquement » dans le README.

---

## 💡 Idées futures / Nouveau Backlog

*Ajoutez ici vos nouvelles idées de fonctionnalités pour les prochaines sessions de développement.*
