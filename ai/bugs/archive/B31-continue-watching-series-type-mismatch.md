# B31 — Reprise série (Accueil) : mauvais écran, section absente, recos faussées

- **Statut** : RESOLVED
- **Type** : Bug data (mismatch de convention de type)
- **Écrans** : Accueil (« Continuer à regarder »), Séries (catégorie « Tout »),
  recommandations Accueil

## 1. Constat (PO)

1. Lancer un épisode de série depuis « Continuer à regarder » sur l'Accueil
   n'affichait jamais épisode suivant/précédent dans le lecteur.
2. La catégorie « Tout » de l'écran Séries n'affichait aucune section
   « Continuer à regarder », contrairement à l'Accueil.
3. Le clic sur la vignette d'une reprise série ne menait pas à la fiche série.

## 2. Cause racine

`PlaybackPosition.type` est peuplé depuis `media_refs.kind` (`VodDao.PLAYBACK_LIST_QUERY`
→ `VodRepositoryImpl.toDomain()`), dont les valeurs réelles sont **"movie" ou
"episode"** — jamais **"series"**. Plusieurs endroits du code comparaient
pourtant `type` à la chaîne littérale `"series"`, une comparaison qui ne
matche donc jamais rien :

- `HomeScreen.kt` (`handleResumeClick`) : `if (position.type == "series")`
  toujours faux → toute reprise de série tombait dans la branche film et
  ouvrait `vod_player` avec l'id de l'épisode comme si c'était un film. D'où
  le symptôme 1 (mauvais écran, donc aucune UI d'épisode suivant/précédent —
  pas juste des boutons manquants) et le symptôme 3 (aucune route vers la
  fiche, seulement vers un lecteur incorrect).
- `SeriesViewModel.kt` (`state.resumeSeries`) : `pos.type == "series"` toujours
  faux → la liste utilisée pour la section « Continuer à regarder » de l'écran
  Séries était systématiquement vide, alors que la section existait déjà dans
  le code (`CategorySectionRow` clé `resume_watching`, Tv/MobileLayout).
  D'où le symptôme 2.
- `GetRecommendationsUseCase.kt` : même filtre bogué → l'historique série
  n'entrait jamais dans le calcul des recommandations (bug silencieux,
  jamais signalé par le PO mais découvert au passage).
- `HomeCards.kt` (`HomeResumeWatchingCard`) : même filtre bogué → le badge
  « S01E03 » ne s'affichait jamais sur la carte de reprise (idem, non
  signalé).

Le bug a survécu parce que plusieurs fixtures de test construisaient
`PlaybackPosition` directement avec `type = "series"` (au lieu de passer par
le vrai mapping DAO), reproduisant la même valeur fausse côté attendu et côté
code testé — les tests passaient donc pour de mauvaises raisons.

## 3. Correctifs

- `HomeScreen.kt` : `handleResumeClick` discrimine désormais sur
  `position.seriesId != null` (fiable, déjà le motif utilisé par
  `HomeViewModel.groupResumeWatching`) plutôt que sur `type`. Le clic sur une
  reprise série ouvre la fiche série (`onSelectSeriesDetail`), comme toute
  autre vignette série de l'app, au lieu de lancer directement le lecteur —
  la fiche recharge la série complète (`selectStreamId`) et propose son
  propre CTA « Reprendre » ciblant le bon épisode à la bonne position, ce qui
  redonne gratuitement épisode suivant/précédent (chemin déjà correct depuis
  la fiche détail).
- `SeriesViewModel.kt`, `GetRecommendationsUseCase.kt`, `HomeCards.kt` :
  comparaison corrigée en `pos.type == "episode"`.
- `PlaybackPosition.kt` : commentaire de `type` corrigé ("movie" ou
  "episode", jamais "series") avec renvoi vers cette fiche.
- Nettoyage : `onPlayResumeWatchingSeries` (NavGraph/HomeScreen/HomeViewModel)
  devenu mort avec le nouveau routage a été retiré plutôt que laissé inerte.
- Tests : fixtures `SeriesViewModelTest`/`GetRecommendationsUseCaseTest`
  corrigées de `type = "series"` vers `"episode"` (elles masquaient le bug) ;
  `test_resumeSeries_observesAndFiltersCorrectly` sert désormais de non-
  régression réelle sur la section Séries.

## 4. Risque résiduel

Aucun autre site de comparaison `type == "series"` sur `PlaybackPosition`
trouvé (`grep` exhaustif). `FavoriteItem.type` utilise réellement la
convention "movie"/"series" (modèle différent, non concerné).
