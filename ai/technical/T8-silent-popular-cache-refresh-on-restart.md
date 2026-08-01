# T8 - Silent Background Popular TMDB Cache Refresh on Restart

## Informations générales

Type:
Enhancement (Performance / UX Stability)

Status:
BACKLOG

Created:
2026-08-01

---

# 1. Description

Currently, when the TMDB Popular Top 10 cache has expired (older than 24 hours), the application fetches fresh results from the TMDB API in the background. Once resolved, these fresh streams immediately replace the displayed "Top 10" streams in the active UI session.

This causes a sudden visual pop / layout shift on the Home Screen where cards in the "Top 10" lists suddenly shift or change content while the user is actively browsing or preparing to click a card.

This enhancement introduces a **"Silent Background Cache Refresh"** strategy:
- If a popular cache exists (even if it is expired/yesterday's data), it is used **immediately** and remains the **only** displayed data for the entire active session.
- The fresh network update is executed silently in parallel to refresh and save the updated data in the persistent cache database/shared preferences.
- The UI is **never** updated with the fresh data during the active session. This ensures absolute stability for the user's focus and zero layout shift.
- The updated data will be seamlessly loaded and displayed only on the **next launch** of the application.

---

# 2. Contexte

* **`HomeViewModel`**: Located in `app/src/main/java/com/cstv/app/presentation/home/HomeViewModel.kt`. It initiates the popular loading job.
* **`GetPopularTop10InCatalogUseCase`**: Located in `app/src/main/java/com/cstv/app/domain/usecase/GetPopularTop10InCatalogUseCase.kt`.
* **`PopularRepository`**: Handles caching of matched popular movies and series.

---

# 3. Spécification fonctionnelle

## Objectif

Éliminer toute perturbation visuelle ou saut de cartes dans la section "Top 10" de l'Accueil pendant qu'un utilisateur parcourt activement l'écran, en décalant l'affichage des données fraîches au prochain démarrage de l'application.

## User stories

- En tant qu'utilisateur parcourant la rangée "Top 10", je ne veux pas que les cartes changent soudainement d'affiche, d'ordre ou de titre sous mes yeux ou sous mon focus de télécommande TV.
- En tant qu'utilisateur ouvrant l'application après 24 heures, je vois immédiatement le dernier Top 10 en cache. Pendant ma session, l'application met à jour ce cache en arrière-plan sans perturber mon écran actuel.
- Lors de ma prochaine ouverture de l'application, je bénéficie de ce Top 10 actualisé de manière transparente.

## Règles métier

1. Au chargement de l'accueil, si un cache du Top 10 (films ou séries) existe, il est renvoyé et affiché immédiatement, peu importe son âge (même s'il a dépassé la durée de fraîcheur nominale de 24h).
2. Si le cache est périmé, une requête d'actualisation réseau est lancée en arrière-plan.
3. Le résultat de cette actualisation réseau est sauvegardé silencieusement dans le cache persistant local.
4. L'état d'affichage de la session courante (`state.popularTopVodStreams` et `state.popularTopSeriesStreams`) n'est **jamais** modifié par cette actualisation réseau en arrière-plan si un cache initial était déjà affiché.
5. Si aucun cache n'existait du tout (très premier lancement), le résultat réseau est affiché dès réception pour éviter une ligne vide persistante.

---

# 4. Spécification technique

## Fichiers à modifier

- `app/src/main/java/com/cstv/app/presentation/home/HomeViewModel.kt`
- `app/src/main/java/com/cstv/app/domain/usecase/GetPopularTop10InCatalogUseCase.kt` (ou les repositories sous-jacents)
- Tests unitaires associés

## Modifications suggérées

1. **`GetPopularTop10InCatalogUseCase`** :
   - Modifier la signature d'appel ou introduire un paramètre/mode pour distinguer l'actualisation "silencieuse" (qui met à jour le cache mais ne renvoie rien à afficher de nouveau pour cette session) ou adapter la valeur retournée.
   - Alternativement, le `HomeViewModel` peut gérer cela : s'il détecte qu'un cache (périmé) a pu être chargé au début de la session, il lance le cas d'usage uniquement pour ré-alimenter le cache de manière asynchrone, mais n'applique pas le résultat dans son `HomeState` courant.

2. **`HomeViewModel.kt`** :
   - Avant de lancer le rafraîchissement TMDB, vérifier si un cache est déjà disponible.
   - Si oui :
     1. Afficher immédiatement ce cache.
     2. Lancer la coroutine d'actualisation réseau en mode silencieux : elle appelle le use case (qui résout et persiste le nouveau cache), mais le ViewModel **n'appelle pas** de mise à jour sur `_state.value.copy(popularTopVodStreams = ...)` avec les données fraîches.
   - Si non (premier lancement, cache vide) :
     1. Lancer le chargement réseau.
     2. Mettre à jour l'état de l'UI dès que le résultat est disponible.

---

# 5. Architecture

## Flux de données proposé

```
HomeViewModel.loadHomeData()
 ├─ Lire le cache de popular (Top 10)
 │   ├─ Si présent : 
 │   │   ├─ Afficher immédiatement dans l'UI (popularTopVodStreams / popularTopSeriesStreams)
 │   │   └─ Si expiré (> 24h) :
 │   │       └─ Lancer tâche d'arrière-plan asynchrone qui appelle l'API et met à jour le cache persistant local SILENCIEUSEMENT (sans mettre à jour l'UI)
 │   └─ Si absent (vide) :
 │       └─ Lancer tâche d'arrière-plan qui appelle l'API, persiste le cache ET met à jour l'UI
```

---

# 6. Plan de développement

- [ ] **Tâche 1 — Adapter la détection de présence du cache populaire**
  Permettre de savoir rapidement si un cache populaire est disponible localement (qu'il soit expiré ou non).

- [ ] **Tâche 2 — Ajuster la logique de mise à jour dans `HomeViewModel`**
  Modifier la coroutine de `popularJob` dans `HomeViewModel.loadHomeData()` pour appliquer la règle d'affichage sélective (mise à jour UI uniquement si le cache initial était vide).

- [ ] **Tâche 3 — Écrire les tests unitaires de non-régression**
  Vérifier dans `HomeViewModelTest` que :
  - Un cache préexistant périmé est immédiatement affiché.
  - La tâche asynchrone d'arrière-plan s'exécute mais ne modifie pas l'état UI.
  - Un démarrage à froid (sans cache) effectue bien la mise à jour de l'UI à la réception du résultat réseau.
