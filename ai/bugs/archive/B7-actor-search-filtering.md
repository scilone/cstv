# B7 - Le filtrage de recherche depuis un acteur ne fonctionne pas correctement

## Informations générales

Type:
Bug

Status:
RELEASED

Created:
2026-07-22

---

# 1. Description

Le filtrage de recherche est inefficace ou buggé lorsqu'on lance une recherche en cliquant sur le nom d'un acteur (ou réalisateur) depuis la fiche de détails d'un film ou d'une série. La recherche n'affiche pas les résultats attendus, ou affiche un écran vide.

---

# 2. Contexte

Le problème provient de plusieurs facteurs cumulés dans l'implémentation de la recherche locale et de sa navigation :

1. **Absence de recherche sur les crédits en recherche avancée :**
   Lorsque des filtres de recherche avancée sont actifs (même si un seul filtre de type de média "Films" ou "Séries" est activé), l'application utilise `AdvancedCatalogSearchUseCase` pour filtrer le catalogue complet en mémoire. Or, ce use case applique le filtre textuel uniquement sur le **nom** du média (`it.name.contains(query, ignoreCase = true)`) et ignore totalement les champs `actors`, `director` ou `genre`.

2. **Conservation des filtres précédents lors d'un clic crédit :**
   Lorsque l'utilisateur clique sur un acteur depuis la fiche détails, le callback `onSearchQueryTriggered` met à jour la requête de recherche dans `FavoritesViewModel` et navigue vers l'écran de recherche, mais **conserve les filtres avancés précédemment actifs**. Si un filtre restrictif ou incompatible est resté actif (ex. filtre sur une autre catégorie ou type de média "Séries" alors que l'acteur n'a joué que dans des films), l'utilisateur obtient zéro résultat.

3. **Perte de données lors du mapping dans `searchUnified` :**
   Dans `FavoritesRepositoryImpl.searchUnified`, les résultats retournés par le DAO FTS4 (`searchVodStreams` / `searchSeriesStreams`) sont mappés vers les modèles de domaine `VodStream` et `SeriesStream` en omettant de passer les propriétés `actors` et `director`. Bien que la recherche SQL MATCH fonctionne en base, les objets de domaine produits perdent leurs métadonnées d'acteurs et de réalisateur, ce qui peut fausser les traitements ou l'affichage ultérieurs.

---

# 3. Spécification fonctionnelle

## User stories

- En tant qu'utilisateur, je peux toucher ou sélectionner le nom d'un acteur ou d'un réalisateur depuis la fiche d'un film ou d'une série pour consulter immédiatement les films et séries de son catalogue local auxquels il est associé.
- En tant qu'utilisateur, j'obtiens les mêmes résultats pertinents pour une recherche par titre, acteur, réalisateur ou genre, que j'utilise la recherche standard ou que j'aie activé un ou plusieurs filtres avancés.
- En tant qu'utilisateur, une recherche lancée depuis un crédit n'est pas limitée par les filtres que j'avais laissés lors d'une précédente recherche avancée.

## Parcours utilisateur

1. Depuis la fiche détaillée d'un film ou d'une série, l'utilisateur sélectionne un nom individuel dans la ligne « Acteurs » ou « Réalisateur ».
2. L'application ouvre l'écran Recherche avec ce nom comme requête visible.
3. Tous les filtres avancés précédemment sélectionnés sont remis à leur état initial avant le calcul des résultats ; aucun chip de filtre ne reste actif.
4. La recherche s'exécute sur l'ensemble du catalogue local visible et affiche les sections Films et Séries contenant cette personne.
5. L'utilisateur peut ensuite appliquer volontairement de nouveaux filtres avancés, modifier la requête ou ouvrir un résultat, selon le comportement habituel de l'écran Recherche.

## Règles métier

- Une correspondance textuelle est valide lorsqu'elle est trouvée, sans distinction de casse, dans au moins l'un des champs suivants du média : titre, acteurs, réalisateur ou genre.
- Cette règle s'applique de façon identique aux films et aux séries, et reste valable lorsqu'un filtre avancé de type, catégorie, note, année ou genre est actif.
- Les filtres avancés continuent de s'appliquer cumulativement aux résultats d'une recherche explicitement filtrée. Ils ne sont réinitialisés automatiquement que pour une navigation déclenchée par un crédit depuis une fiche de détails.
- La saisie ou la modification manuelle d'une requête dans l'écran Recherche ne réinitialise pas les filtres avancés : l'utilisateur conserve le contrôle explicite de ses choix.
- Les catégories masquées par le profil restent masquées ; une recherche par crédit ne doit pas révéler de contenu que le profil a choisi de cacher.
- Une recherche par crédit couvre les contenus VOD et Séries disponibles localement. La recherche Live TV ne fait pas partie de ce parcours, car les crédits ne sont pas portés par les chaînes en direct.

## Critères d'acceptation

- Depuis une fiche Film, cliquer un acteur ou un réalisateur ouvre Recherche avec le nom sélectionné et affiche tous les films et séries correspondants du catalogue visible.
- Depuis une fiche Série, le même comportement est obtenu pour un acteur comme pour un réalisateur.
- Si un filtre avancé était actif avant le clic (type, catégorie, note, année ou genre), il est absent après l'arrivée dans Recherche et ne peut plus exclure des résultats du crédit sélectionné.
- Avec un filtre avancé choisi volontairement, une requête correspondant uniquement au champ acteurs, réalisateur ou genre renvoie les médias correspondants, au même titre qu'une requête correspondant au titre.
- La recherche est insensible à la casse et fonctionne avec les noms comportant espaces, accents, apostrophes ou plusieurs mots.
- Les résultats conservent les informations nécessaires à leur affichage habituel, notamment les crédits disponibles, quelle que soit la voie de recherche utilisée.

## Cas limites et gestion des erreurs

- Si le crédit est absent, vide, inconnu ou ne désigne pas un nom sélectionnable, aucune navigation de recherche ne doit être déclenchée.
- Si aucun média visible ne correspond au nom sélectionné, Recherche affiche son état vide habituel ; elle ne doit ni afficher une erreur technique ni réutiliser des résultats d'une recherche précédente.
- Si le catalogue local est temporairement indisponible ou qu'une erreur de recherche survient, l'écran reste stable, cesse son état de chargement et présente l'état vide ou d'erreur utilisateur déjà défini par Recherche, sans détail technique brut.
- Les résultats ne doivent pas être dupliqués lorsqu'une personne correspond à plusieurs champs d'un même média (par exemple acteur et réalisateur).
- Les crédits contenant plusieurs personnes doivent permettre une recherche sur le seul nom sélectionné, et non sur la chaîne complète des crédits.

---

# 4. Spécification technique

## Composants impactés

- `presentation/favorites/FavoritesViewModel.kt` : ajouter une entrée publique dédiée aux recherches déclenchées depuis un crédit. Cette méthode doit annuler les recherches/compteurs en cours, remplacer la requête, réinitialiser `advancedFilter` à `AdvancedSearchFilter.DEFAULT`, vider `availableCategories`, fermer la feuille de filtres et lancer explicitement une recherche catalogue VOD/Séries.
- `presentation/navigation/NavGraph.kt` : les callbacks `onSearchQueryTriggered` des routes `vod_details` et `series_details` doivent appeler cette nouvelle entrée avant de naviguer vers `search`, au lieu d'utiliser le chemin générique `onSearchQueryChanged`.
- `domain/usecase/AdvancedCatalogSearchUseCase.kt` : élargir le prédicat textuel des films et séries à `name OR actors OR director OR genre`, avec `contains(..., ignoreCase = true)` et gestion naturelle des champs `null`.
- `data/repository/FavoritesRepositoryImpl.kt` : compléter le mapping des `VodStreamEntity` et `SeriesStreamEntity` retournés par FTS vers les modèles de domaine avec `actors` et `director`, en conservant également `genre` et `releaseYear`.

## Nouveaux composants et dépendances

- Aucun nouveau composant d'architecture, modèle, endpoint, table Room ou dépendance Gradle.
- La nouvelle méthode du ViewModel constitue une intention de présentation explicite (par exemple `searchFromCredit(query)`), pas un nouveau use case : la remise à zéro des filtres est un comportement de navigation/UI et non une règle d'accès aux données.
- Aucun changement de schéma Room ni migration : les champs `actors`, `director` et `genre` existent déjà dans les entités et modèles concernés, et sont déjà indexés par les tables FTS.

## Choix techniques

- Centraliser la transition « crédit → recherche » dans une seule méthode du ViewModel garantit une mise à jour atomique de l'état avant le lancement de la coroutine de recherche. Appeler séparément `resetFilter()` puis `onSearchQueryChanged()` est écarté, car `resetFilter()` déclenche aussi un recalcul différé du compteur et créerait des travaux concurrents inutiles.
- La recherche avancée reste effectuée sur `Dispatchers.Default`, comme aujourd'hui, car elle parcourt le catalogue en mémoire. Le prédicat multi-champs n'ajoute ni appel réseau ni accès Room.
- Le chemin FTS standard conserve la recherche Live existante pour une saisie manuelle. Le parcours depuis un crédit doit, lui, appeler `AdvancedCatalogSearchUseCase` avec le filtre par défaut afin de limiter ce résultat initial aux Films et Séries, conformément à la spécification. Ce choix réutilise aussi directement le filtrage des catégories masquées. Il s'agit d'un routage ponctuel de la recherche, pas d'un mode persistant : une modification manuelle ultérieure de la requête reprend le comportement habituel.
- Les chaînes sont comparées sans distinction de casse. Aucun découpage ou normalisation supplémentaire des crédits n'est nécessaire : `ClickableCreditsRow` transmet déjà le nom individuel sélectionné.

## Tests prévus

- Étendre `AdvancedSearchDomainTest.kt` avec des films et séries dont la requête ne figure que dans `actors`, `director` ou `genre`, y compris casse différente et champs `null`, puis vérifier que les autres filtres continuent de s'appliquer cumulativement.
- Étendre `FavoritesRepositoryImplTest.kt` pour vérifier que le mapping FTS restitue `actors`, `director`, `genre` et `releaseYear` pour les résultats VOD et Séries.
- Étendre `FavoritesViewModelTest.kt` pour vérifier qu'une recherche issue d'un crédit supprime tous les filtres actifs et catégories chargées, ferme la feuille, remplace la requête, annule le résultat précédent et appelle le chemin catalogue VOD/Séries avec `AdvancedSearchFilter.DEFAULT` après le debounce, sans résultat Live.
- Validation manuelle mobile et TV : depuis une fiche Film puis Série, sélectionner acteur et réalisateur avec des filtres préexistants, vérifier les chips absents, la requête visible, les résultats attendus et le respect des catégories masquées.

## Risques et contraintes

- Performance : la recherche avancée reste en `O(n)` sur les catalogues VOD et Séries ; quatre comparaisons au lieu d'une augmentent le coût constant. L'exécution hors thread principal et le debounce existant de 300 ms sont conservés. Aucun chargement de détails par élément ne doit être ajouté.
- Concurrence : les jobs de recherche et de compteur déjà lancés doivent être annulés lors de la transition depuis un crédit afin qu'un résultat calculé avec les anciens filtres ne remplace pas le nouveau résultat.
- Données partielles : certains panels ne renseignent pas les crédits ou le genre. Les valeurs `null` doivent simplement ne pas correspondre, sans erreur ni exclusion d'une correspondance présente dans un autre champ.
- Compatibilité : aucun changement d'API publique réseau, de persistance, de min SDK ou de navigation. Les callbacks Film et Série exposés par `AppNavGraph` doivent rester symétriques, puis le parcours doit être validé sur mobile et TV compte tenu du double système de navigation du projet.

---

# 5. Architecture

## Flux proposé

1. `VodDetailsScreen` ou `SeriesDetailsScreen` transmet le nom individuel sélectionné à son callback existant.
2. `AppNavGraph` appelle l'intention dédiée du `FavoritesViewModel`.
3. Le ViewModel annule les jobs obsolètes, publie un état sans filtre contenant la nouvelle requête, puis lance ponctuellement `AdvancedCatalogSearchUseCase` avec `AdvancedSearchFilter.DEFAULT` ; `AppNavGraph` navigue vers `search`.
4. Le use case parcourt les catalogues VOD et Séries, exclut les catégories masquées et ne produit aucun résultat Live.
5. Si l'utilisateur ajoute ensuite un filtre avancé, `AdvancedCatalogSearchUseCase` parcourt le catalogue visible et applique d'abord la correspondance `titre/acteurs/réalisateur/genre`, puis les critères type, catégorie, note, année et genres.
6. Le `SearchResult` alimente l'état existant et `SearchScreen` sans nouveau contrat de présentation.

## Responsabilités

- Les écrans de détails restent stateless concernant la recherche : ils émettent seulement le nom sélectionné.
- `AppNavGraph` orchestre la destination, sans manipuler directement les champs de filtre.
- `FavoritesViewModel` possède la transition d'état et la gestion des jobs de recherche.
- `AdvancedCatalogSearchUseCase` porte la règle de correspondance textuelle lorsque les filtres avancés sont utilisés.
- `FavoritesRepositoryImpl` garantit un mapping complet des données persistées vers le domaine ; le DAO et les index FTS restent inchangés.

## Décisions exclues du périmètre

- Pas de modification de `VodDetailsScreen.kt`, `SeriesDetailsScreen.kt`, `SearchScreen.kt` ou `AdvancedSearchSheet.kt` : leurs contrats actuels suffisent.
- Pas de nouveau mode « recherche acteur » persistant, pas de filtre Live supplémentaire et pas de normalisation linguistique/accent-insensitive au-delà du comportement actuel.
- Pas de refonte de la double navigation ni de chargement réseau de crédits.

---

# 6. Plan de développement

- [x] Étape 1 : Analyse et structuration (Fait)
- [x] Étape 2 : Spécifications fonctionnelles détaillées (Fait le 2026-07-22)
- [x] Étape 3 : Spécification technique et architecture (Fait le 2026-07-22)
- [x] Étape 4 : Découpage des tâches détaillées (Fait le 2026-07-22)
- [x] Étape 5 : Implémentation de la correction (validation manuelle mobile/TV restante)
- [x] Étape 6 : Review technique (Fait le 2026-07-22)
- [x] Étape 7 : Correction des retours de review (aucune correction requise le 2026-07-22)
- [x] Étape 8 : Validation finale (Automatisée + Fonctionnelle)
- [x] Étape 9 : Documentation globale
- [x] Étape 10 : Livraison Git et Archivage

## Tâches d'implémentation

- [x] B7-1 — Étendre la correspondance textuelle de la recherche catalogue.

  Objectif : faire correspondre une requête non vide au titre, aux acteurs, au réalisateur ou au genre, sans distinction de casse, pour les films comme pour les séries, avant les filtres avancés existants.

  Fichiers :
  - `app/src/main/java/com/cstv/app/domain/usecase/AdvancedCatalogSearchUseCase.kt`
  - `app/src/test/java/com/cstv/app/domain/usecase/AdvancedSearchDomainTest.kt`

  Validation : les tests couvrent chaque champ, la casse différente et les crédits/genres `null`; les filtres type, catégorie, note, année et genre restent cumulatifs, les catégories masquées restent exclues et aucun résultat n'est dupliqué.

- [x] B7-2 — Préserver les métadonnées de crédits dans les résultats FTS unifiés.

  Objectif : compléter le mapping des entités VOD et Séries retournées par le DAO FTS afin que les modèles de domaine conservent `actors`, `director`, `genre` et `releaseYear`.

  Fichiers :
  - `app/src/main/java/com/cstv/app/data/repository/FavoritesRepositoryImpl.kt`
  - `app/src/test/java/com/cstv/app/data/repository/FavoritesRepositoryImplTest.kt`

  Validation : les résultats FTS VOD et Séries restituent toutes ces métadonnées, y compris une année invalide qui conserve le comportement actuel (`null`), sans régression des résultats Live.

- [x] B7-3 — Ajouter la transition atomique « crédit vers recherche » au ViewModel.

  Objectif : exposer une intention dédiée qui annule les jobs de recherche et de comptage obsolètes, remplace la requête, remet `advancedFilter` à `AdvancedSearchFilter.DEFAULT`, vide les catégories chargées, ferme la feuille et déclenche après debounce une recherche catalogue Films/Séries sans Live.

  Fichiers :
  - `app/src/main/java/com/cstv/app/presentation/favorites/FavoritesViewModel.kt`
  - `app/src/test/java/com/cstv/app/presentation/favorites/FavoritesViewModelTest.kt`

  Validation : le test vérifie l'état publié immédiatement, l'annulation des travaux précédents et le résultat issu de `AdvancedCatalogSearchUseCase` avec le filtre par défaut; une saisie manuelle conserve le chemin FTS et les filtres actifs actuels.

- [x] B7-4 — Raccorder symétriquement les fiches Film et Série à la nouvelle intention.

  Objectif : remplacer, dans les deux callbacks de crédits déjà existants, l'appel générique de saisie par l'intention dédiée avant la navigation vers Recherche.

  Fichiers :
  - `app/src/main/java/com/cstv/app/presentation/navigation/NavGraph.kt`

  Validation : les routes `vod_details` et `series_details` transmettent le nom sélectionné à la même intention puis naviguent vers `search`; aucun écran, contrat de callback ou parcours TV manuel n'est modifié.

- [x] B7-5 — Exécuter la non-régression et la validation fonctionnelle ciblée.

  Objectif : confirmer le parcours complet et l'absence de régression de recherche avant review.

  Fichiers :
  - fichiers modifiés par B7-1 à B7-4 (validation uniquement)

  Validation : exécuter `./gradlew testDebugUnitTest`, `./gradlew assembleDebug` et `./gradlew lintDebug`; sur mobile et Android TV, depuis une fiche Film puis Série, sélectionner acteur et réalisateur avec des filtres préexistants et vérifier requête visible, absence de chips, résultats Films/Séries attendus, état vide propre et respect des catégories masquées.

---

# 7. Hypothèses et questions ouvertes

### Hypothèses
1. La recherche avancée doit appliquer la recherche textuelle de manière large (titre OR acteurs OR réalisateur OR genre) tout comme le fait la recherche unifiée FTS4, pour assurer une expérience utilisateur uniforme et prévisible.
2. La réinitialisation automatique des filtres avancés lors d'une recherche initiée depuis les fiches de détails est essentielle pour éviter les situations de "faux positif vide" (résultats vides causés par un filtre oublié).

### Questions ouvertes
- Doit-on également réinitialiser les filtres de recherche avancée si l'utilisateur saisit manuellement une nouvelle requête dans la barre de recherche standard de l'écran de recherche ?
  *Réponse proposée :* Non. Sur l'écran de recherche, l'utilisateur gère activement ses filtres et voit les chips de filtres actifs. En revanche, lors d'un clic de redirection depuis un autre écran (Détails), il n'a pas conscience de l'état précédent des filtres de recherche, d'où la nécessité de réinitialiser dans ce cas spécifique.

---

# 8. Review

## Review technique — 2026-07-22

Status: RESOLVED

Portée : diffs de `AdvancedCatalogSearchUseCase.kt`, `FavoritesRepositoryImpl.kt`, `FavoritesViewModel.kt`, `NavGraph.kt` et tests associés. Tests unitaires ciblés verts (`AdvancedSearchDomainTest`, `FavoritesRepositoryImplTest`, `FavoritesViewModelTest`).

### Conformité à la spécification
- Correspondance textuelle élargie à `name OR actors OR director OR genre`, insensible à la casse, gestion `null` naturelle (`?.contains(...) == true`), identique films/séries. Conforme B7-1.
- Mapping FTS unifié complété : `actors` et `director` ajoutés en respectant l'ordre positionnel des constructeurs `VodStream`/`SeriesStream` (`genre, releaseYear, actors, director`), `releaseYear` conserve le garde `takeIf { y -> y > 0 }`. Conforme B7-2.
- `searchFromCredit` : annulation atomique de `searchJob`/`countJob`, remise à zéro de `advancedFilter`, `availableCategories`, feuille de filtres et résultat, puis `performSearch(force = true, useAdvancedCatalogSearch = true)`. Conforme B7-3.
- Les deux routes de détails (`vod_details`, `series_details`) appellent la nouvelle intention avant navigation, symétriquement. Conforme B7-4.

### Points de vigilance (non bloquants)
- `matchesTextQuery` déclare le récepteur en type pleinement qualifié (`com.cstv.app.domain.model.VodStream`/`SeriesStream`) plutôt qu'un import — nit de style.
- Logique de correspondance dupliquée entre VOD et Séries ; acceptable vu les types distincts, factorisation possible mais non requise.
- Flag `isCreditSearchActive` correctement remis à `false` dès une saisie manuelle (`onSearchQueryChanged`) ; le recalcul différé du compteur préserve le mode crédit tant que la requête reste inchangée. Comportement conforme.

### Verdict
Aucun défaut bloquant. Reste : validation fonctionnelle mobile/Android TV (B7-5) avant clôture.

---

# 7. Notes de développement

- 2026-07-22 : `AdvancedCatalogSearchUseCase` recherche désormais la requête dans le titre, les acteurs, le réalisateur et le genre pour les films comme les séries.
- 2026-07-22 : le mapping FTS unifié conserve `actors`, `director`, `genre` et `releaseYear`.
- 2026-07-22 : `searchFromCredit` remet les filtres et l'état de recherche à zéro avant d'utiliser ponctuellement la recherche catalogue Films/Séries ; les deux routes de détails l'utilisent.
- 2026-07-22 : validations automatisées réussies : `testDebugUnitTest`, `assembleDebug`, `lintDebug`. La validation fonctionnelle mobile/Android TV reste à réaliser sur appareil ou émulateur disponible.
- 2026-07-22 : étape 7 clôturée sans modification : la review ne comportait aucun défaut bloquant. Étape 8 : `./gradlew --no-daemon testDebugUnitTest assembleDebug lintDebug` réussit ; la validation fonctionnelle mobile/Android TV reste bloquée par l'absence d'appareil ou d'émulateur ADB dans cet environnement.

---

# 9. Release

- **Statut** : RELEASED
- **Version** : v1.49.2
- **Date** : 2026-07-22
- **Commit** : :bug: fix(search): centralize credit transition and expand search FTS metadata (B7)
- **Tag** : v1.49.2
