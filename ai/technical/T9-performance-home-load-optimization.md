# T9 - Optimisation des performances et du temps de chargement de l'Accueil sur TV

## Informations générales

Status:
ANALYSIS

Created:
2026-08-02

---

# 1. Description

L'application souffre d'un temps de chargement relativement long (4 à 5 secondes de loader circulaire) après la sélection du profil sur Android TV. Ce ralentissement se produit alors même que l'intégralité du catalogue est en cache local dans Room et qu'aucun appel réseau bloquant n'est en cours.

L'analyse de l'initialisation de l'Accueil a identifié deux goulots d'étranglement majeurs :
1. **Le calcul des "Derniers ajouts" (Films et Séries)** : Il charge l'intégralité du catalogue (des dizaines de milliers d'objets) depuis Room en mémoire pour effectuer un tri et un filtrage CPU complexe dans Kotlin. (Ce goulot d'étranglement sera résolu de façon fonctionnelle par le ticket **F20**).
2. **Le filtrage de la liste "Continuer à regarder" (Playback Positions)** : Pour masquer immédiatement les médias en cours de lecture qui appartiennent à des catégories masquées par le profil, l'application charge à chaque démarrage l'intégralité du catalogue de films et de séries (`getCachedVodStreams("all")` et `getCachedSeriesStreams("all")`) uniquement pour obtenir la catégorie (`categoryId`) associée à chaque identifiant de média.

L'objectif de ce ticket technique est d'éliminer définitivement le goulot d'étranglement n°2 en stockant directement l'identifiant de catégorie (`categoryId`) dans la table des positions de lecture (`playback_positions`). Ainsi, la liste de reprise de lecture pourra être filtrée de façon instantanée (< 5 ms) au démarrage, sans jamais charger l'intégralité du catalogue.

---

# 2. Contexte

Dans l'état actuel :
* L'entité Room `PlaybackPositionEntity` contient de nombreuses métadonnées sur le média regardé (titre, jaquette, progression, durée, saison, épisode...), mais ne possède pas de colonne `categoryId`.
* Lors de la collecte réactive des positions de lecture dans `HomeViewModel.kt` :
  ```kotlin
  val vodMap = vodRepository.getCachedVodStreams("all").associate { it.streamId to it.categoryId }
  val seriesMap = seriesRepository.getCachedSeriesStreams("all").associate { it.seriesId to it.categoryId }
  ```
  Ces deux requêtes lisent des milliers de lignes en base de données, les instancient et les transforment en maps à chaque modification ou au démarrage de l'Accueil. Sur une TV de faible puissance, cette opération prend plusieurs secondes et bloque la réactivité de l'application.

En ajoutant une colonne `categoryId` à la table `playback_positions` et à son entité correspondante, nous pourrons filtrer directement les catégories masquées à partir de l'objet `PlaybackPosition` lui-même.

---

# 3. Spécification fonctionnelle et technique

## Objectifs techniques

1. **Évolution de la base de données (Room)** :
   * Ajouter une colonne optionnelle/nullable `categoryId: String?` à l'entité Room `PlaybackPositionEntity` (table `playback_positions`).
   * Réaliser une migration de schéma propre dans Room (version **21** vers **22**) sans perte de données utilisateur.
2. **Évolution du modèle de domaine** :
   * Mettre à jour le modèle `PlaybackPosition` dans la couche `domain` pour y ajouter le champ `categoryId: String?`.
   * Adapter les mappers d'entité vers domaine et inversement.
3. **Persistance de la catégorie lors de la lecture** :
   * S'assurer que lors de la sauvegarde ou de la mise à jour de la position de lecture (dans les lecteurs vidéo ou les repositories de mise à jour de position), l'identifiant de catégorie (`categoryId`) du média soit correctement renseigné et enregistré en base de données.
4. **Optimisation radicale du HomeViewModel** :
   * Dans `HomeViewModel.kt`, supprimer définitivement les appels lourds à `getCachedVodStreams("all")` et `getCachedSeriesStreams("all")` dans la coroutine d'observation des positions de lecture.
   * Utiliser directement le champ `categoryId` présent sur chaque objet `PlaybackPosition` pour appliquer le filtrage des catégories masquées.
   * Gérer de manière robuste les anciennes positions de lecture stockées en base (dont la colonne `categoryId` sera `null`) en effectuant un appel de recherche ponctuel et ultra-rapide par identifiant uniquement pour ces quelques éléments, ou en appliquant un repli sécurisé.

---

# 4. Spécification technique détaillée

*(À compléter à l'Étape 3)*

---

# 5. Architecture

*(À compléter à l'Étape 3)*

---

# 6. Plan de développement

*(À compléter à l'Étape 4)*

---

# 7. Notes de développement

*(À remplir au fil du développement)*

---

# 8. Review

*(À remplir à l'Étape 6)*

---

# 9. Release

*(À remplir à l'Étape 10)*
