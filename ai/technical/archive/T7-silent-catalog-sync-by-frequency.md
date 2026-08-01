# T7 - Silent Catalog Sync based on User Setting and Banner Hiding

## Informations générales

Type:
Technical

Status:
RELEASED

Created:
2026-07-30

---

# 1. Description

Currently, the freshness of the local catalog uses a hardcoded expiration of 24 hours (`CacheTtl.CATALOG_MILLIS`). When the catalog is considered "stale" or "offline" (due to a previous failed sync), a prominent `OfflineBanner` is displayed at the top of media lists (VOD, Series, Live TV), informing the user about the catalog's date and asking them to manual "Réessayer" (retry).

This has several design and user experience drawbacks:
1. It ignores the **Sync Frequency** selected by the user in the settings (`SyncFrequency`: Daily, Weekly, Monthly, Disabled).
2. Telling the user about catalog synchronization failures is "not sexy" and is not their problem.
3. If the user has an active internet connection, and the scheduled 6 AM background sync failed, the app should silently retry updating the catalog in the background, rather than popping up error alerts and asking the user to manually click "Réessayer".

This technical task aims to:
- Dynamically determine catalog staleness in `CatalogSyncManagerImpl` by checking the user's selected sync frequency in `SettingsManager`.
- Hide the `OfflineBanner` from media lists whenever the device is connected to the internet (online), as the application will silently trigger and execute catalog synchronization in the background.
- Show the `OfflineBanner` **only** when the device is truly offline (no internet connection), as a purely informative banner indicating that they are browsing cached data offline.

---

# 2. Contexte

* **`SettingsManager`**: Gathers the user's desired background sync frequency (`DAILY`, `WEEKLY`, `MONTHLY`, `DISABLED`).
* **`CatalogSyncManagerImpl`**: Calculates the global `CatalogStatus` and handles background re-connections and startup synchronizations via `syncIfStale()`.
* **`OfflineBanner`**: Composable banner displayed at the top of VOD, Series, and Live TV screens.

---

# 3. Spécification fonctionnelle

## Objectifs

- Respecter la fréquence de synchronisation choisie par l'utilisateur pour évaluer la fraîcheur du catalogue.
- Réactualiser un catalogue périmé en ligne de façon silencieuse, sans alerte ni action manuelle demandée.
- Réserver le bandeau hors ligne à l'information utile : l'utilisateur consulte un catalogue local pendant que l'accès Internet est indisponible.

## User stories

- En tant qu'utilisateur ayant choisi une synchronisation quotidienne, hebdomadaire ou mensuelle, mon catalogue est considéré périmé selon ce choix et non après un délai fixe de 24 heures.
- En tant qu'utilisateur en ligne, je peux ouvrir Live TV, Films ou Séries sans voir un avertissement de synchronisation ; l'application tente la mise à jour nécessaire en arrière-plan.
- En tant qu'utilisateur sans accès Internet, je vois clairement que je consulte les données locales et leur date, sans faux espoir de mise à jour immédiate.
- En tant qu'utilisateur ayant désactivé la synchronisation, je peux continuer à consulter le catalogue en cache sans que l'application déclenche une synchronisation automatique liée à sa péremption.

## Parcours utilisateur

1. L'utilisateur choisit dans les réglages une fréquence `DAILY`, `WEEKLY`, `MONTHLY` ou `DISABLED`.
2. Au démarrage ou à l'entrée sur Live TV, Films ou Séries, l'application consulte l'état et la date du catalogue local.
3. Si le catalogue est encore frais au regard de ce réglage, les données locales restent affichées et aucune synchronisation supplémentaire n'est déclenchée.
4. S'il est périmé et que l'appareil est en ligne, l'application lance une synchronisation en arrière-plan. L'utilisateur continue de consulter l'écran sans bannière d'erreur, dialogue ni bouton de relance.
5. S'il est périmé et que l'appareil est hors ligne, l'écran reste utilisable avec le cache disponible et affiche `OfflineBanner` comme indication informative.

## Règles métier

1. La durée de fraîcheur est déterminée uniquement par la fréquence de synchronisation active : quotidienne, hebdomadaire ou mensuelle. Une fréquence désactivée n'autorise pas de synchronisation automatique fondée sur l'âge du cache.
2. La même règle de fraîcheur s'applique à l'état global du catalogue et à la décision de `syncIfStale()` ; ces deux résultats ne doivent pas diverger.
3. L'absence de catalogue local reste un besoin de synchronisation, indépendamment de la fréquence ; si l'appareil est hors ligne, aucune donnée ne doit être inventée.
4. Une tentative de synchronisation automatique en ligne est silencieuse : aucune erreur réseau, d'authentification ou serveur ne produit de bannière d'échec, de boîte de dialogue ou de demande de relance dans les listes média.
5. `OfflineBanner` est visible uniquement lorsque l'état réseau indique une absence réelle d'accès Internet et qu'un catalogue local est consulté. Il indique un contexte hors ligne et non un échec de synchronisation.
6. Le retour de connectivité permet une prochaine tentative silencieuse si le catalogue reste périmé ; il ne doit pas exiger une action explicite de l'utilisateur.

## Critères d'acceptation

- [ ] Avec `DAILY`, un catalogue devient périmé à l'échéance quotidienne ; avec `WEEKLY` et `MONTHLY`, il reste frais jusqu'à leur échéance respective.
- [ ] Avec `DISABLED`, un catalogue existant n'entraîne pas de synchronisation automatique uniquement parce qu'il est ancien.
- [ ] Quand le catalogue est périmé et l'appareil en ligne, Live TV, Films et Séries restent consultables sans `OfflineBanner`, sans alerte et sans action manuelle requise ; une synchronisation est tentée en arrière-plan.
- [ ] Quand l'appareil est hors ligne et qu'un cache existe, ces écrans affichent le cache et le bandeau hors ligne informatif.
- [ ] Quand l'appareil est hors ligne sans cache exploitable, l'application présente son état d'absence de données existant sans prétendre qu'une synchronisation a été effectuée.
- [ ] Un échec de la synchronisation silencieuse ne remplace pas les données locales visibles et ne fait pas réapparaître une bannière d'échec tant que l'appareil est en ligne.

## Cas limites et gestion des erreurs

- Un changement de fréquence prend effet pour les évaluations suivantes de fraîcheur ; il ne doit ni effacer le catalogue ni lancer une synchronisation intrusive.
- Un cache dont la date est absente ou illisible est traité de manière sûre comme non fiable : l'application tente une synchronisation seulement si la politique active et la connectivité le permettent.
- Les échecs temporaires (timeout, serveur indisponible, erreur TMDB/Xtream nécessaire au catalogue) sont absorbés par le flux silencieux et conservent le dernier cache valide.
- Des identifiants invalides ou un compte expiré suivent le traitement d'authentification déjà prévu par l'application ; cette évolution ne divulgue pas le détail technique dans les listes média.
- La connectivité est évaluée au moment de décider l'affichage : une perte de réseau après le lancement de l'écran peut faire apparaître le bandeau informatif lors de la prochaine mise à jour d'état, sans interrompre la consultation du cache.

---

# 4. Spécification technique

## 4.1 Décisions techniques

| # | Décision | Justification |
|---|----------|---------------|
| D1 | **Calcul dynamique du TTL du catalogue** | Dans `CatalogSyncManagerImpl.kt`, utiliser la fréquence configurée de `settingsManager` au lieu d'une valeur brute pour `isStale` et dans `syncIfStale()`. |
| D2 | **Masquage de la bannière si connecté** | Modifier `OfflineBanner.kt` pour masquer complètement le bandeau si l'appareil est connecté (`!status.isOffline`), car une synchronisation invisible s'occupe de mettre à jour le catalogue. |
| D3 | **Lancement de synchro silencieuse en arrière-plan** | Lorsque l'utilisateur entre dans l'un des onglets principaux (Live TV, VOD, Séries) et que le catalogue est jugé périmé, déclencher silencieusement `catalogSyncManager.syncIfStale()`. |

---

# 5. Plan de Développement

### Étape 1 : Alignement de la fraîcheur sur les paramètres utilisateur
- Dans `CatalogSyncManagerImpl.kt`, modifier le calcul de `isStale` en calculant le TTL dynamique basé sur la valeur retournée par `settingsManager.getSyncFrequency()`.
- Adapter `syncIfStale()` pour utiliser le même TTL dynamique.

### Étape 2 : Masquage et affinement d'OfflineBanner
- Mettre à jour `OfflineBanner.kt` pour masquer la bannière si `!status.isOffline` (l'utilisateur est en ligne).
- S'assurer que la bannière s'affiche uniquement si l'appareil est hors ligne (`status.isOffline` est vrai), devenant un simple indicateur informatif non anxiogène.

### Étape 3 : Déclenchement de la synchronisation silencieuse et tests
- S'assurer qu'au lancement de l'application ou à l'ouverture des onglets, si le catalogue est périmé, la synchronisation est lancée silencieusement en arrière-plan.
- Mettre à jour les tests unitaires de `CatalogSyncManagerImplTest` pour valider le TTL dynamique selon la configuration de fréquence et l'état silencieux.

---

# 6. Review

## Critique

Aucun problème critique identifié.

## Majeur

### T7-R1 — Un ancien échec réseau continue d'afficher le bandeau alors que l'appareil est connecté

- **Description :** `CatalogSyncManagerImpl.catalogStatus` calcule toujours `isOffline` avec `!isOnline || lastFailureKind == SyncFailureKind.NETWORK`. Un échec réseau historique suffit donc à maintenir `status.isOffline == true` même lorsque `NetworkMonitor.isOnline` vaut `true`. La garde ajoutée dans `OfflineBanner` ne distingue pas ces deux causes et affiche encore le bandeau.
- **Impact :** le critère « aucun `OfflineBanner` quand l'appareil est en ligne » et les règles métier 4 et 5 ne sont pas respectés. Après l'échec d'une synchronisation planifiée, l'utilisateur peut précisément revoir l'avertissement que T7 doit supprimer, malgré une connectivité active et une nouvelle tentative silencieuse.
- **Correction attendue :** séparer l'état de connectivité réelle de l'historique des échecs (ou faire dépendre la visibilité du bandeau directement de `NetworkMonitor`) afin que `OfflineBanner` ne s'affiche que lorsque l'accès réseau courant est absent. Conserver `lastFailureKind` pour le diagnostic sans l'utiliser comme équivalent de l'état hors ligne affiché.

## Mineur

### T7-R2 — Les nouveaux comportements UI et de déclenchement ne sont pas couverts par des tests

- **Description :** les tests ajoutés vérifient correctement les TTL `DAILY`, `WEEKLY`, `MONTHLY`, `DISABLED` et la cohérence de `syncIfStale()`, mais aucun test ne couvre le cas `isOnline == true` avec un dernier échec `NETWORK`, ni l'appel silencieux depuis les trois ViewModels.
- **Impact :** la régression T7-R1 passe inaperçue et le branchement Live TV / Films / Séries peut régresser sans alerte automatisée.
- **Correction attendue :** ajouter un test de statut/visibilité pour une connectivité active après échec réseau et des tests ciblés vérifiant que chaque ViewModel appelle `syncIfStale()` sans propager d'erreur à l'état UI.

## Corrections demandées

- [x] Corriger T7-R1.
- [x] Ajouter la couverture décrite dans T7-R2.

Status: RESOLVED (Étape 7 — 2026-08-01)

T7-R1 : `CatalogStatus` distingue désormais la connectivité réelle
(`isNetworkOnline`, alimentée directement par `NetworkMonitor.isOnline`) de
l'historique combiné d'échec (`isOffline`, inchangé, toujours utilisé par les
écrans d'état vide Live/VOD/Séries). `OfflineBanner` se fonde uniquement sur
`isNetworkOnline` : un échec réseau passé ne maintient plus le bandeau une
fois la connexion revenue.

T7-R2 : `CatalogSyncManagerImplTest` couvre le cas `isOnline == true` après un
dernier échec `NETWORK` (`catalogStatusReportsNetworkOnlineDespiteAPastNetworkFailure`).
`LiveTvViewModelTest`, `SeriesViewModelTest` et `VodViewModelTest` vérifient
chacun que `catalogSyncManager.syncIfStale()` est appelé à l'entrée de
l'onglet et qu'un échec de cet appel ne remonte jamais dans l'état UI.

## Vérifications de review

- `./gradlew --no-daemon --max-workers=1 testDebugUnitTest --tests com.cstv.app.data.sync.CatalogSyncManagerImplTest --tests com.cstv.app.domain.usecase.GetPopularTop10InCatalogUseCaseTest --tests com.cstv.app.presentation.home.HomeViewModelTest` : **SUCCESS**.
- `git diff --check` : **SUCCESS** avant consignation de la review.

## Vérifications de l'étape 7

- `./gradlew --no-daemon --max-workers=1 testDebugUnitTest --tests com.cstv.app.data.sync.CatalogSyncManagerImplTest --tests com.cstv.app.domain.usecase.GetPopularTop10InCatalogUseCaseTest --tests com.cstv.app.presentation.home.HomeViewModelTest --tests com.cstv.app.presentation.livetv.LiveTvViewModelTest --tests com.cstv.app.presentation.series.SeriesViewModelTest --tests com.cstv.app.presentation.vod.VodViewModelTest --tests com.cstv.app.data.repository.PopularRepositoryImplTest` : **SUCCESS**.

## Vérifications de l'étape 8

- Tests de non-régression T7/T8 : `./gradlew --no-daemon --max-workers=1 testDebugUnitTest --tests com.cstv.app.data.sync.CatalogSyncManagerImplTest --tests com.cstv.app.data.repository.PopularRepositoryImplTest --tests com.cstv.app.domain.usecase.GetPopularTop10InCatalogUseCaseTest --tests com.cstv.app.presentation.home.HomeViewModelTest --tests com.cstv.app.presentation.livetv.LiveTvViewModelTest --tests com.cstv.app.presentation.series.SeriesViewModelTest --tests com.cstv.app.presentation.vod.VodViewModelTest` : **SUCCESS**.
- `assembleDebug` : **SUCCESS**.
- `lintDebug` : **BLOCKED** par trois erreurs `UnsafeOptInUsageError` dans `presentation/player/core/PlayerDecoderPolicy.kt`, fichier non suivi lié à B16 et hors périmètre de T7/T8. Aucune correction appliquée dans cette étape.

La validation globale requiert un lint vert ; le statut reste donc `FIXES` jusqu'à correction de ce blocage externe puis relance des vérifications.

---

# 7. Release

Version :
v1.65.0

Commit :
:sparkles: :technologist: :bug: release(catalog-popular-player): deliver dynamic sync, silent popular trends, and TV video rendering fix (v1.65.0)

Date :
2026-08-01
