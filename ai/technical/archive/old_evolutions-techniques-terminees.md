# Archives des Évolutions Techniques Terminées

Ce document rassemble l'historique des audits, correctifs techniques, mises à niveau d'architecture, de sécurité et de performances déjà implémentés et validés dans l'application.

---

## 📅 Évolutions Techniques de l'Audit de Juillet 2026

### 1. Exclure les credentials du backup Android

✅ **TERMINÉE** — `fix(security): exclure les credentials chiffrés des backups Android` (tag `v1.20.1` ou antérieur)

**Constat.** `AndroidManifest.xml` a `android:allowBackup="true"` sans `fullBackupContent`/`dataExtractionRules`. Les SharedPreferences chiffrées de `CredentialsManager` (EncryptedSharedPreferences) partent donc dans les backups cloud/ADB. Double problème : (a) surface d'exfiltration des identifiants Xtream, (b) après restauration sur un autre appareil, la MasterKey (Android Keystore, matérielle, non sauvegardée) n'existe pas — le déchiffrement échoue et l'app peut crasher au premier accès aux prefs restaurées.

**Prompt originel.**
> Dans l'app Android cstv : les credentials Xtream sont stockés via EncryptedSharedPreferences (`data/local/storage/CredentialsManager.kt`) et le manifest a `android:allowBackup="true"` sans règles d'exclusion. Mets en place des règles de backup excluant TOUTES les SharedPreferences chiffrées (fichier de prefs du CredentialsManager) des backups : crée `res/xml/backup_rules.xml` (API ≤ 30, attribut `android:fullBackupContent`) ET `res/xml/data_extraction_rules.xml` (API 31+, attribut `android:dataExtractionRules`, sections cloud-backup et device-transfer), référencés dans le manifest. Vérifie le nom exact du fichier de prefs dans CredentialsManager pour l'exclusion. En complément, blinde le CredentialsManager : si le déchiffrement échoue à l'initialisation (AEADBadTagException/GeneralSecurityException, cas d'une restauration de backup sur un autre appareil), purger le fichier de prefs corrompu et repartir sur des prefs vides (l'utilisateur se reconnecte) plutôt que de crasher. Build + tests + lint verts, commit.

---

### 2. Tester les migrations Room (exportSchema + tests instrumentés)

✅ **TERMINÉE** — `test(db): schémas Room exportés et tests instrumentés des migrations`

**Constat.** `AppDatabase` est en `version = 12` avec `exportSchema = false`, 3 migrations manuelles (9→10, 10→11, 11→12) écrites à la main dans `data/local/db/Migrations.kt`, **zéro test instrumenté** (`androidTest/` vide) et pas de `fallbackToDestructiveMigration` (choix assumé — AGENTS.md). Conséquence : une migration buggée = crash au lancement pour tout utilisateur qui met à jour, sans filet. Le risque grandit à chaque nouvelle version de schéma.

**Prompt originel.**
> Dans l'app Android cstv (Room 2.6.1, KSP) : active `exportSchema = true` sur `AppDatabase` et configure l'export des schémas JSON dans `app/schemas/` via l'option KSP `room.schemaLocation` dans `app/build.gradle.kts` ; commite les JSON générés (ils documentent chaque version du schéma et sont requis par MigrationTestHelper). Ajoute la dépendance `androidx.room:room-testing` et crée un test instrumenté `MigrationsTest` (dans `app/src/androidTest/`) avec `MigrationTestHelper` qui vérifie chaque migration existante de `data/local/db/Migrations.kt` (9→10, 10→11, 11→12) : création de la base en version N, insertion de données représentatives, migration vers N+1, assertions sur les données et le schéma. Ajoute aussi un test "migrate all" 9→12 enchaîné. Documente dans AGENTS.md que toute nouvelle migration doit arriver avec son test et son schéma JSON commité. Attention : la base ne pourra être créée en version 9 par le helper que si le schéma 9 existe — si les JSON des anciennes versions ne peuvent pas être régénérés, documente que la couverture démarre à la première version exportée et teste au minimum la plus récente. Les tests instrumentés nécessitent un émulateur/device : documente la commande (`./gradlew connectedDebugAndroidTest`) dans AGENTS.md sans l'ajouter à la checklist de build standard.

---

### 3. Migrer hors de security-crypto (déprécié)

✅ **TERMINÉE** — `refactor(security): chiffrement Keystore maison à la place de security-crypto`

**Constat.** `androidx.security:security-crypto:1.1.0-alpha06` : bibliothèque officiellement **dépréciée par Google** (abandonnée en alpha, plus de maintenance, Tink sous-jacent figé). Elle fonctionne aujourd'hui mais ne recevra plus de correctifs de sécurité, et chaque montée d'AGP/Kotlin augmente le risque d'incompatibilité binaire.

**Prompt originel.**
> Dans l'app Android cstv : `CredentialsManager` (`data/local/storage/CredentialsManager.kt`) utilise EncryptedSharedPreferences de `androidx.security:security-crypto` (déprécié par Google). Remplace-le par un chiffrement maison minimal et éprouvé : clé AES-256-GCM générée dans Android Keystore (`KeyGenParameterSpec`, purpose ENCRYPT/DECRYPT, sans authentification utilisateur), valeurs chiffrées stockées en Base64 dans des SharedPreferences standards (IV aléatoire préfixé au ciphertext). Contraintes : (1) migration transparente — au premier lancement post-mise à jour, lire les credentials existants via l'ancien EncryptedSharedPreferences, les ré-écrire via le nouveau mécanisme, puis supprimer l'ancien fichier de prefs ; l'utilisateur ne doit PAS avoir à se reconnecter ; (2) même API publique de CredentialsManager (aucun appelant modifié) ; (3) gérer proprement l'échec de déchiffrement (purge + retour null) ; (4) ne retirer la dépendance security-crypto qu'après une période de transition d'une version (le code de migration en a besoin) — ajoute un commentaire TODO daté pour la retirer. minSdk 21 : Android Keystore AES-GCM requiert API 23+, donc pour API 21-22 fallback assumé en clair ou montée de minSdk à 23 — analyse la distribution réelle (app perso) et propose ; monter minSdk à 23 est acceptable si documenté dans AGENTS.md. Tests unitaires du nouveau manager (logique hors Keystore mockée), build + lint verts.

---

### 4. Mettre à niveau la stack (AGP / Kotlin 2 / Compose BOM)

✅ **TERMINÉE** — `build: AGP 8.2.2 -> 8.7.3`, `build: Kotlin 1.9.24 -> 2.0.21`, `build: Compose BOM 2024.12.01`

**Constat.** AGP 8.2.2 (début 2024) avec le workaround `android.suppressUnsupportedCompileSdk=35` dans `gradle.properties` (compileSdk 35 non supporté officiellement par cet AGP), Kotlin 1.9.24, Compose BOM 2024.02.02, compose compiler 1.5.14, lifecycle 2.7.0, activity-compose 1.8.2, core-ktx 1.12.0. Rien n'est cassé, mais la dette s'accumule : chaque trimestre de retard rend la montée plus risquée, et le workaround compileSdk masque de vrais avertissements de compatibilité.

**Prompt originel.**
> Dans l'app Android cstv : monte la stack de build par étapes, en validant build + tests + lint + un lancement manuel à CHAQUE étape (ne pas tout monter d'un coup). Étape 1 : AGP 8.2.2 → dernière 8.x stable supportant compileSdk 35, puis retire `android.suppressUnsupportedCompileSdk=35` de gradle.properties. Étape 2 : Kotlin 1.9.24 → 2.0.x avec le nouveau plugin `org.jetbrains.kotlin.plugin.compose` (le champ `composeOptions.kotlinCompilerExtensionVersion` disparaît) ; vérifier la compatibilité KSP (version alignée 2.0.x-x.x.x) pour Hilt 2.51.1 et Room 2.6.1, et les monter si nécessaire. Étape 3 : Compose BOM → dernière stable, lifecycle 2.8+, activity-compose et core-ktx récents ; corriger les APIs dépréciées signalées. Étape 4 : Media3 1.3.1 → dernière stable (attention aux breaking changes d'API player, vérifier les 3 écrans player). Contrainte : la branche TV (tv-material alpha10) est fragile — ne monte tv-material que si la compilation l'exige, sinon ne pas y toucher. Un commit par étape, message explicite, aucun tag intermédiaire.

---

### 5. Compléter les tests ViewModels

✅ **TERMINÉE** — `test(presentation): couvrir les 5 ViewModels manquants (32 tests)`

**Constat.** 18 fichiers de test pour 142 fichiers source. Côté présentation, seuls `HomeViewModel`, `LoginViewModel` et `SettingsViewModel` sont testés. Manquent : `FavoritesViewModel` (recherche debounce + favoris réactifs — logique la plus riche), `LiveTvViewModel` (EPG, catégories, récemment regardées), `VodViewModel`, `SeriesViewModel`, `ProfileViewModel` (gate de sélection, garde-fou dernier profil). Les régressions UI récentes (bug "récemment regardées") auraient été attrapées par un test de câblage.

**Prompt originel.**
> Dans l'app Android cstv : ajoute des tests unitaires pour les 5 ViewModels non couverts, en suivant les conventions des tests existants (`HomeViewModelTest` : StandardTestDispatcher + runCurrent(), MockitoAnnotations, stubs `doReturn(flowOf(...)).whenever(...)` pour les Flows Room, `viewModelScope.cancel()` en tearDown). Par ordre de valeur : (1) `FavoritesViewModelTest` — debounce de recherche (avancer le temps virtuel de 300ms, vérifier qu'une frappe annule la recherche précédente et qu'un résultat obsolète n'écrase pas un plus récent), collecte du Flow de favoris, toggleFavorite add/remove. (2) `LiveTvViewModelTest` — chargement catégories/chaînes, sélection de catégorie, garde EPG (pas de double fetch in-flight), saveRecentlyWatched rafraîchit la liste. (3) `ProfileViewModelTest` — ensureInitializedAndNeedsSelection (0, 1, N profils), interdiction de supprimer le dernier profil. (4/5) `VodViewModelTest` et `SeriesViewModelTest` — chargement catégories/streams/détails, propagation d'erreur en state, re-throw des CancellationException non avalées. Chaque test vérifie le STATE émis, pas les mocks internes. Build + tests verts, commit par ViewModel ou en une fois, au choix.

---

### 6. Factoriser les 3 players

✅ **TERMINÉE** — `refactor(player): factoriser les players VOD et Séries (audit #6)`

**Constat.** `VodPlayerScreen.kt` (848 lignes), `SeriesPlayerScreen.kt` (838 lignes) et `PlayerScreen.kt` (live) partagent l'essentiel : setup ExoPlayer/Media3, overlay de contrôles, sélection de pistes audio/sous-titres, gestion du resume, styles de sous-titres. Toute correction (ex : chevauchement titre/boutons de la Phase 25) doit être appliquée 2-3 fois — le bug l'a d'ailleurs prouvé. C'est le plus gros gisement de dette du projet (~2500 lignes pour ~3 variantes d'un même écran).

**Prompt originel.**
> Dans l'app Android cstv : factorise les trois écrans player (`presentation/player/PlayerScreen.kt` [live], `presentation/vod/VodPlayerScreen.kt`, `presentation/series/SeriesPlayerScreen.kt`) SANS changer aucun comportement. Démarche : (1) commence par un diff des trois fichiers pour cartographier commun vs spécifique ; (2) extrais dans `presentation/player/common/` les briques partagées — création/release de l'ExoPlayer, overlay de contrôles (play/pause, seek, titre, fermeture), dialogue de sélection de pistes audio/sous-titres, application du style de sous-titres (CaptionStyleCompat depuis SettingsManager), sauvegarde périodique de la position ; (3) chaque écran devient une composition fine de ces briques + sa logique propre (zapping et EPG pour le live, épisode suivant pour les séries, resume pour VOD/séries). Contraintes : ne touche PAS aux ViewModels ni aux routes de navigation ; préserve les préférences de piste par film/série (Phase 29) ; refactor mécanique par étapes avec build+tests verts à chaque commit intermédiaire (pas de big-bang). C'est le refactor le plus risqué du backlog : si un comportement divergent entre les 3 players s'avère intentionnel, le préserver et le documenter en commentaire.

---

### 8. Finir la migration couleurs vers le thème

✅ **TERMINÉE** — `refactor(theme): migrer les 90 couleurs en dur restantes vers les tokens`

**Constat.** Le design system existe (Phase 46 : `presentation/theme/`, palette + typo) mais **99 occurrences de `Color(0x…)` en dur** subsistent dans `presentation/` — surtout les écrans player, Settings, TV (branche Android TV) et des restes dans les composants refondus. Deux sources de vérité couleur = dérive garantie à la prochaine retouche visuelle.

**Prompt originel.**
> Dans l'app Android cstv : recense tous les `Color(0x…)` en dur sous `presentation/` (hors `presentation/theme/`) et migre-les vers les tokens du thème (`presentation/theme/Color.kt`) ou vers `MaterialTheme.colorScheme.*`. Règles : (1) une couleur qui correspond exactement à un token existant (Surface1/2/3, TextPrimary/Secondary, AccentLavande…) est remplacée par ce token ; (2) une couleur récurrente sans token (ex : rouge badge DIRECT #E50914, bleu FILM #0070F3, violet SÉRIE #8A2BE2, jaune favori) devient un nouveau token nommé dans Color.kt ; (3) une couleur one-shot justifiée (scrim, overlay alpha) peut rester locale mais avec un commentaire. Périmètre : écrans mobile ET TV (les tokens sont indépendants du MaterialTheme, utilisables par la branche TV sans lui appliquer IptvXtreamTheme). Aucun changement de rendu attendu : les valeurs restent identiques, seule la source change. Vérifie ensuite qu'aucune régression visuelle n'apparaît sur Home/TV/Films/Séries/players (build + lancement), build + tests + lint verts.

---

### 9. Restreindre réellement le cleartext HTTP

✅ **TERMINÉE** — `docs(security): documenter la décision cleartext HTTP permissif`

**Constat.** `network_security_config.xml` contient `<base-config cleartextTrafficPermitted="true"/>` : strictement équivalent à l'ancien flag global `usesCleartextTraffic="true"` que la Phase 45 était censée restreindre. Le HTTP en clair est inhérent aux panels Xtream (la plupart n'ont pas de TLS), mais on peut au moins borner : autoriser le cleartext uniquement vers le domaine du serveur configuré n'est pas possible statiquement (domaine dynamique), en revanche on peut interdire le cleartext pour tout le reste si l'app n'en a pas besoin (images posters ?).

**Prompt originel.**
> Dans l'app Android cstv : la config réseau autorise le cleartext globalement. Analyse d'abord quels hôtes ont réellement besoin de HTTP en clair : (1) le panel Xtream (host dynamique saisi par l'utilisateur — souvent HTTP pur) ; (2) les URLs d'images (streamIcon/cover renvoyées par le panel — vérifier si elles pointent vers le panel lui-même ou des CDN externes, greper les modèles/DTOs et tester sur données réelles). Si tout le trafic cleartext légitime vise le panel (host dynamique), alors la restriction statique par domaine est impossible et le base-config permissif est le bon choix : dans ce cas, documente-le explicitement en commentaire dans network_security_config.xml et dans AGENTS.md (décision assumée, ne plus y revenir). Si en revanche les images viennent de domaines HTTPS, envisage `cleartextTrafficPermitted="false"` par défaut + l'app ne peut pas déclarer le domaine du panel dynamiquement — donc conclure honnêtement : soit rester permissif documenté, soit basculer la couche réseau en autorisant explicitement le cleartext uniquement sur OkHttp (connectionSpecs) pour le client API/player et l'interdire pour le reste. Choisis l'option la plus simple qui borne réellement quelque chose ; si rien ne peut être borné, conclure par la documentation (pas de changement cosmétique inutile).

---

### 10. Version catalog Gradle

✅ **TERMINÉE** — `build: version catalog Gradle (libs.versions.toml)`

**Constat.** Pas de `gradle/libs.versions.toml` : versions éparpillées dans deux build.gradle.kts, certaines dupliquées (Hilt en racine + module), `roomVersion`/`media3Version` en variables locales. Freine les montées de version propres (item 4).

**Prompt originel.**
> Dans l'app Android cstv : introduis un version catalog Gradle (`gradle/libs.versions.toml`) et migre TOUTES les dépendances et plugins des deux build.gradle.kts vers `libs.…` (plugins compris : AGP, Kotlin, KSP, Hilt). Aucune montée de version dans ce commit — uniquement le déplacement à versions strictement identiques (diff de `./gradlew :app:dependencies` avant/après vide). Grouper logiquement (compose, room, media3, hilt, test). Build + tests + lint verts.

---

### 11. Finir l'externalisation i18n

✅ **TERMINÉE** — `refactor(i18n): externaliser les ~85 chaînes UI restantes vers strings.xml`

**Constat.** ~55 `text = "…"` hardcodés restent hors `strings.xml` (surtout players, Settings, fiches détail, branche TV, et les fallbacks type "Inconnu", "Aucun résumé disponible.", "Chaîne Favorie" — avec au moins une faute : "Favorie" → "Favorite"). La Phase 45 a couvert ~50 % des écrans.

**Prompt originel.**
> Dans l'app Android cstv : termine l'externalisation des chaînes UI vers `res/values/strings.xml` + `stringResource()`, commencée en Phase 45. Recense tous les `text = "…"` et littéraux FR sous `presentation/` et `navigation/` (y compris fallbacks : "Inconnu", "Aucun résumé disponible.", "Série", "Chaîne Favorie" — corriger au passage la faute → "Chaîne favorite"). Attention aux contextes non-composables (fallbacks dans NavGraph/ViewModels) : y passer le string via `stringResource` côté composable appelant quand c'est possible, sinon `context.getString`. Conserver la convention de nommage existante (`home_*`, `login_*`, `search_*`…). Ne pas toucher aux chaînes techniques (routes, clés de prefs, tags). Build + tests + lint verts.

---

### 12. Configurer le cache images Coil

✅ **TERMINÉE** — `perf(images): ImageLoader Coil applicatif (cache disque 250 Mo)`

**Constat.** Aucun `ImageLoader` custom : Coil utilise ses défauts (25 % de la RAM en cache mémoire, cache disque 2 % du stockage). Avec des milliers de posters 2:3 et de logos de chaînes, un réglage explicite améliore le scroll des grilles et évite les re-téléchargements après éviction.

**Prompt originel.**
> Dans l'app Android cstv (Coil 2.6, Hilt) : fournis un `ImageLoader` applicatif configuré — cache disque dédié (ex : 250 Mo, `diskCache { directory(cacheDir/"image_cache") }`), cache mémoire ~25 %, `respectCacheHeaders(false)` (les panels Xtream renvoient des headers de cache incohérents ; les posters sont immuables par URL), crossfade activé. Implémente `ImageLoaderFactory` sur la classe Application (`IptvApplication`) pour que tous les `AsyncImage` existants en profitent sans modification. Vérifie visuellement le scroll d'une grosse grille et l'affichage offline d'images déjà vues. Build + tests + lint verts.

---

### 13. Mettre à jour les statuts de la feuille de route

* `docs: marquer [TERMINE] les phases livrées de la feuille de route`

**Constat.** `feuille-de-route-phases.md` ne marque `[TERMINE]` que les phases 29 et 42-45, alors que les phases 18-28, 30-41, 46-53 et 55-57 sont livrées (vérifiable par les tags git v1.x et le code). Le document ment sur l'état du projet — gênant pour toute session IA qui s'y fie.

**Prompt originel.**
> Dans l'app Android cstv : mets à jour `feuille-de-route-phases.md` pour refléter l'état réel. Pour chaque phase sans `[TERMINE]`, vérifie dans le code et l'historique git (`git log --oneline --all | grep -i "phase N"`, tags v1.x) si elle est livrée ; si oui, ajoute `[TERMINE]` à son titre comme sur les phases 42-45. Cas à vérifier de près plutôt que supposer : Phase 19 (recherche acteur/réalisateur), Phase 21 (autocomplétion — a priori NON faite, laisser ouverte), Phase 54 (accent réglable — a priori NON faite, laisser ouverte). Ne modifie aucun contenu de phase, uniquement les marqueurs de statut. Commit docs.

---

### 14. Externaliser les chaînes UI codées en dur (T-4)

✅ **TERMINÉE** — `refactor(i18n): externaliser toutes les chaînes UI codées en dur vers strings.xml` (tag `v1.48.18`)

**Constat (review v1.48.6).** ~54 appels `Text("...")` avec littéraux français hors `strings.xml` (recherche avancée, lecteurs, sheets, libellés divers), alors que le reste de l'app passe par `stringResource`. Bloque toute future localisation et crée des incohérences (mêmes libellés dupliqués).

**Prompt originel.**
> Externalise toutes les chaînes UI codées en dur de CSTV vers `res/values/strings.xml` : recense les `Text("...")`/`contentDescription = "..."` à littéraux dans `presentation/`, crée des ressources nommées par écran (`advanced_search_title`, `player_retry`, etc.), remplace par `stringResource(...)`. Ne touche pas aux chaînes de log/technique. Vérifie `assembleDebug` + `lintDebug` (lint `HardcodedText` doit être silencieux sur les fichiers traités).

---

### 15. Réduire la fréquence d'écriture des positions de lecture (T-5)

✅ **TERMINÉE** — `perf(player): réduire la fréquence de sauvegarde de la position de lecture à 5 secondes` (tag `v1.48.19`)

**Constat (review v1.48.6).** Les boucles de suivi des lecteurs VOD/Séries appellent `viewModel.savePosition(...)` **à chaque seconde** de lecture (le commentaire dit « every 5 seconds » mais le code écrit bien 1×/s) → une écriture Room + FTS par seconde pendant toute la lecture. Inutilement coûteux (I/O, batterie), surtout sur TV bas de gamme.

**Prompt originel.**
> Dans les boucles de suivi de position de `VodPlayerScreen` et `SeriesPlayerScreen` (et l'équivalent Live si applicable) : garde la mise à jour de l'UI (position/durée) à 1 s, mais n'écris la position en Room que toutes les 5 s (compteur/modulo) ET aux moments critiques déjà gérés (pause, dispose, fin de lecture). Aligne le commentaire sur le comportement réel. Non-régression : la reprise de lecture doit rester précise à ≤ 5 s.

---

### 16. Pagination des catalogues volumineux (T-1)

✅ **TERMINÉE** — `perf(paging): introduction de la pagination locale de Room à l'UI avec Paging 3` (tag `v1.48.26`)

**Constat.** Aucune pagination (pas de Paging 3) : `getVodStreams`/`getSeriesStreams`/`getLiveStreams` chargent la catégorie entière (voire le catalogue "Tout" complet, 10k+ entrées) en `List<T>` en mémoire, mappée dans des LazyColumn/LazyGrid. La FTS (Phase 40) a réglé la recherche, mais l'affichage initial des grosses listes reste coûteux (parsing JSON complet + insert Room + liste mémoire). Sur Android TV bas de gamme, c'est le premier plafond de perf.

**Prompt originel.**
> Dans l'app Android cstv : introduis une pagination locale sur les listes de catalogue volumineuses, SANS toucher à la stratégie de sync réseau (l'API Xtream ne pagine pas : on continue de télécharger et cacher le catalogue entier en Room ; seule la LECTURE Room et l'UI paginent).
> Utilise Paging 3 (`androidx.paging:paging-runtime` + `paging-compose`, et `room-paging` pour les PagingSource générées par Room) :
> 1. Ajoute aux DAOs concernés (VodDao, SeriesDao, LiveTvDao) des requêtes `PagingSource<Int, Entity>` par catégorie et pour le mode "Tout".
> 2. Expose des `Flow<PagingData<Model>>` dans les repositories, câblés dans Vod/Series/LiveTv ViewModels via `cachedIn(viewModelScope)`.
> 3. Bascule les grilles/listes des écrans Films, Séries, TV et la grille "Voir tout" sur `collectAsLazyPagingItems`.
> 
> Contraintes : préserver la restauration de position de défilement (Phase 20) et les sections horizontales du mode "Tout" (celles-ci affichent peu d'items par rangée : ne paginer que les grilles verticales, garder les rangées horizontales en List simple si plus simple). Mesure avant/après (temps d'affichage + mémoire via Debug.getNativeHeapAllocatedSize ou profiler) sur la plus grosse catégorie et note les chiffres dans le message de commit.

---

### 17. Unifier la navigation TV et mobile (T-2)

✅ **TERMINÉE** — `refactor(navigation): unification de la navigation TV et mobile avec navigation-compose` (tag `v1.48.27`)

**Constat.** Deux systèmes de navigation coexistent : le mobile passe par `AppNavGraph` (navigation-compose, `presentation/navigation/NavGraph.kt`, ~600 lignes) tandis que la TV passe par une navigation manuelle (enum `AppScreen` + `screenHistory` + gros `when` dans `MainActivity.kt`, ~890 lignes). Chaque nouvel écran doit être câblé **deux fois**, avec deux logiques de back différentes — source répétée de régressions (écrans présents sur une plateforme seulement, comportements back divergents) et premier facteur de la taille de MainActivity.

**Prompt originel.**
> Unifie la navigation de l'app CSTV sur navigation-compose pour les deux plateformes (mobile + TV), en supprimant la navigation manuelle par enum `AppScreen` de `MainActivity.kt`.
> 1. Étends `AppNavGraph` pour couvrir tous les écrans encore gérés manuellement côté TV, avec les mêmes routes que le mobile. Les différences TV (focus D-pad, layouts) restent dans les écrans (`isTv`), pas dans la navigation.
> 2. Reproduis fidèlement le comportement back TV actuel (BackHandler : retour hiérarchique, déconnexion depuis le dashboard) avec `navController` (popBackStack + logique de racine).
> 3. Réduis `MainActivity` à : détection isTv, thème, état de session, hôte du NavGraph.
> 4. Aucune régression : parcours complet mobile + TV (login → home → chaque écran → back), `assembleDebug` + `lintDebug` + `testDebugUnitTest`.

---

### 18. Optimisation des performances de l'Accueil sur TV (T9)

✅ **TERMINÉE** — `perf(home): élimination du goulot d'étranglement au chargement des reprises de lecture (T9)`

**Constat.** L'application souffrait d'un long temps de chargement (4 à 5 secondes) sur Android TV après la sélection du profil, causé par le calcul CPU complexe et des requêtes lourdes (`getCachedVodStreams("all")` et `getCachedSeriesStreams("all")`) à chaque démarrage de l'Accueil pour filtrer la liste "Continuer à regarder" (Playback Positions) par rapport aux catégories masquées du profil.

**Prompt originel.**
> Dans l'app Android cstv : élimine le chargement complet du catalogue pour le filtrage des reprises de lecture en stockant directement l'identifiant de catégorie (`categoryId`) dans la table des positions de lecture (`playback_positions`).
> 1. Évolution de Room : ajouter la colonne `categoryId: String? = null` sur `PlaybackPositionEntity` et réaliser la migration `22→23` (sans perte de données).
> 2. Mettre à jour le modèle `PlaybackPosition` et propager `categoryId` via les mappers entité ⇄ domaine.
> 3. Enregistrer systématiquement `categoryId` lors de la mise à jour de la position de lecture depuis les lecteurs de médias.
> 4. Optimiser `HomeViewModel.kt` : supprimer définitivement les lectures globales `"all"` du collecteur de positions et filtrer directement à partir du champ `categoryId` de la position de lecture, avec un repli ultra-rapide par identifiant pour les anciennes entrées migrées.



