# T3 - Factoriser les trois lecteurs vidéo

## Informations générales

Type:
Technical

Status:
IDEA

Created:
2026-07-20

Target version:

---

# 1. Description

Factorise le socle commun des trois lecteurs de CSTV sans changer le comportement visible.

---

# 2. Contexte

`PlayerScreen` (Live, ~950 l.), `VodPlayerScreen` (~1025 l.) et `SeriesPlayerScreen` (~1120 l.) dupliquent massivement : construction ExoPlayer/NextLib, gestion PiP (+ workaround relayout SurfaceView), KEEP_SCREEN_ON, overlay auto-masqué, resize mode, buffering/erreurs, sélection de pistes, boucle de suivi de position. Toute correction de lecteur doit être appliquée 3× (vécu sur la session Cast). ~3100 lignes pour ~60 % de code commun.

---

# 3. Spécification fonctionnelle

Aucune régression, validation complète sur les trois types de flux.

---

# 4. Spécification technique

1. Extrais dans `presentation/player/core/` :
   (a) un `rememberManagedExoPlayer(...)` (construction NextLib/cache offline, release au dispose),
   (b) un état/gestionnaire PiP réutilisable (listener + workaround SurfaceView),
   (c) un hôte d'overlay commun (visibilité auto-masquée, gradients, top bar),
   (d) la boucle de suivi/sauvegarde de position paramétrable.
2. Migre les trois écrans sur ce socle, en gardant leurs spécificités (zapping live + EPG, pistes/reprise VOD, épisode suivant Séries).
3. Interdit de mélanger de la logique métier dans les composables du socle (état hoisté).

---

# 5. Architecture

À définir lors de la phase ARCHITECTURE.

---

# 6. Plan de développement

- [ ] Extrait `rememberManagedExoPlayer`
- [ ] Extrait état/gestionnaire PiP
- [ ] Extrait hôte d'overlay commun
- [ ] Extrait boucle de suivi/sauvegarde de position
- [ ] Migre `PlayerScreen` (Live)
- [ ] Migre `VodPlayerScreen` (VOD)
- [ ] Migre `SeriesPlayerScreen` (Séries)

---

# 7. Notes de développement

-

---

# 8. Review

Résultats des revues.

## Critique

## Majeur

## Mineur

## Corrections demandées

---

# 9. Release

Version:

Commit:

Date:
