# Évolutions Techniques & Dette (Backlog Actif)

Ce document rassemble les tâches d'architecture, de sécurité, de performance, de refactoring ou de mise à niveau technique planifiées pour l'application.

Une fois qu'une tâche technique est réalisée et validée, sa description/son prompt doit être déplacé dans le fichier d'archive correspondant : `docs/archive/evolutions-techniques-terminees.md` afin d'éviter la surcharge de contexte lors des sessions d'IA.

---

## 🎯 Évolutions Techniques Actives

### T-2. Unifier la navigation TV et mobile — Effort L (Priorité Haute)
**Modèle : Opus 4.8**

**Constat (review v1.48.6).** Deux systèmes de navigation coexistent : le mobile passe par `AppNavGraph` (navigation-compose, `presentation/navigation/NavGraph.kt`, ~600 lignes) tandis que la TV passe par une navigation manuelle (enum `AppScreen` + `screenHistory` + gros `when` dans `MainActivity.kt`, ~890 lignes). Chaque nouvel écran doit être câblé **deux fois**, avec deux logiques de back différentes — source répétée de régressions (écrans présents sur une plateforme seulement, comportements back divergents) et premier facteur de la taille de MainActivity.

**Prompt.**
> Unifie la navigation de l'app CSTV sur navigation-compose pour les deux plateformes (mobile + TV), en supprimant la navigation manuelle par enum `AppScreen` de `MainActivity.kt`.
> 1. Étends `AppNavGraph` pour couvrir tous les écrans encore gérés manuellement côté TV, avec les mêmes routes que le mobile. Les différences TV (focus D-pad, layouts) restent dans les écrans (`isTv`), pas dans la navigation.
> 2. Reproduis fidèlement le comportement back TV actuel (BackHandler : retour hiérarchique, déconnexion depuis le dashboard) avec `navController` (popBackStack + logique de racine).
> 3. Réduis `MainActivity` à : détection isTv, thème, état de session, hôte du NavGraph.
> 4. Aucune régression : parcours complet mobile + TV (login → home → chaque écran → back), `assembleDebug` + `lintDebug` + `testDebugUnitTest`.
> Procède écran par écran (commits intermédiaires) plutôt qu'en big-bang.

---

### T-3. Factoriser les trois lecteurs vidéo — Effort L (Priorité Moyenne)
**Modèle : Opus 4.8**

**Constat (review v1.48.6).** `PlayerScreen` (Live, ~950 l.), `VodPlayerScreen` (~1025 l.) et `SeriesPlayerScreen` (~1120 l.) dupliquent massivement : construction ExoPlayer/NextLib, gestion PiP (+ workaround relayout SurfaceView), KEEP_SCREEN_ON, overlay auto-masqué, resize mode, buffering/erreurs, sélection de pistes, boucle de suivi de position. Toute correction de lecteur doit être appliquée 3× (vécu sur la session Cast). ~3100 lignes pour ~60 % de code commun.

**Prompt.**
> Factorise le socle commun des trois lecteurs de CSTV sans changer le comportement visible.
> 1. Extrais dans `presentation/player/core/` : (a) un `rememberManagedExoPlayer(...)` (construction NextLib/cache offline, release au dispose), (b) un état/gestionnaire PiP réutilisable (listener + workaround SurfaceView), (c) un hôte d'overlay commun (visibilité auto-masquée, gradients, top bar), (d) la boucle de suivi/sauvegarde de position paramétrable.
> 2. Migre les trois écrans sur ce socle, en gardant leurs spécificités (zapping live + EPG, pistes/reprise VOD, épisode suivant Séries).
> 3. Interdit de mélanger de la logique métier dans les composables du socle (état hoisté).
> 4. Aucune régression, validation complète sur les trois types de flux.
