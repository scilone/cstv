# T7 - Silent Catalog Sync based on User Setting and Banner Hiding

## Informations générales

Type:
Technical

Status:
ANALYSIS

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

# 3. Spécification fonctionnelle et Objectifs

## Objectifs
* **Respect Settings**: Align catalog freshness and staleness with the frequency requested by the user in settings.
* **Silent Synchronization**: If the catalog is stale and we are online, automatically and silently synchronize the catalog in the background without bothering the user.
* **Non-intrusive Banners**: Eliminate warning banners on active internet connections. Only show the banner when the device is completely offline to clearly state "Hors ligne - catalogue du...".

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
