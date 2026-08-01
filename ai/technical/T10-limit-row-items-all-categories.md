# T10 - Limitation du nombre d'éléments par ligne en mode "Tout" pour les Films et Séries

## Informations générales

Status:
ANALYSIS

Created:
2026-08-02

---

# 1. Description

Dans les pages de listes des **Films (VOD)** et des **Séries**, le mode d'affichage **"Tout"** (All) présente les médias sous forme de lignes horizontales défilantes (`LazyRow`) regroupées par catégories.

Actuellement, l'application ne limite pas le nombre de médias affichés dans ces lignes horizontales. Si une catégorie contient des milliers d'éléments (ce qui est très fréquent pour l'IPTV), la `LazyRow` se retrouve chargée avec toutes ces milliers d'entrées. Bien que la liste soit "lazy", cela dégrade fortement la mémoire RAM et les performances de défilement ou d'acquisition du focus sur TV.

L'objectif de cette tâche d'optimisation technique est de :
1. Limiter l'affichage à un maximum de **250 éléments** pour chaque ligne horizontale de catégorie en mode "Tout".
2. S'assurer que le bouton **"Voir tout"** à côté du titre de la ligne (sur mobile) ou l'action équivalente sélectionne directement la catégorie pour l'ouvrir en mode grille complète (où l'intégralité du contenu est affiché de façon optimisée).

---

# 2. Contexte

Dans `VodScreen.kt` et `SeriesScreen.kt` :
* La liste complète des flux filtrés (`filteredStreams`) est groupée par catégorie en mémoire Kotlin :
  ```kotlin
  val groupedStreams = remember(filteredStreams) {
      filteredStreams.groupBy { it.categoryId }
  }
  ```
* Ensuite, pour chaque catégorie, le composant `CategorySectionRow` est instancié en lui passant la totalité de la liste des médias de cette catégorie (`catMovies`).
* Sur mobile, le bouton "Voir tout" appelle `onSeeAll = { onCategorySelected(category) }`, ce qui sélectionne la catégorie et bascule l'affichage de l'écran vers le mode "Catégorie spécifique" (grille complète). Sur TV, l'utilisateur peut également sélectionner la puce de catégorie en haut pour afficher la grille de cette catégorie.

En limitant la liste transmise à la `LazyRow` en mode "Tout" à un maximum de 250 éléments, nous garantissons d'excellentes performances d'affichage et de défilement, tout en laissant la possibilité de voir l'intégralité du catalogue au clic sur "Voir tout" ou via la sélection de la catégorie.

---

# 3. Spécification technique

## Objectifs et modifications

1. **Limitation à 250 éléments** :
   * Dans `VodScreen.kt` (mode "Tout" TV et mobile), remplacer l'appel à `CategorySectionRow` en limitant la liste des films transmis à la ligne à un maximum de 250 éléments :
     ```kotlin
     movies = catMovies.take(250)
     ```
   * Faire exactement de même dans `SeriesScreen.kt` (mode "Tout" TV et mobile) pour limiter le nombre de séries transmises à un maximum de 250 éléments :
     ```kotlin
     series = catSeries.take(250)
     ```

2. **Comportement de "Voir tout" (See All)** :
   * Confirmer que sur mobile, le lien "Voir tout" appelle bien `onCategorySelected(category)`. C'est déjà le cas et cela fonctionne parfaitement en redirigeant vers le mode grille où 100% des films/séries de la catégorie sont alors affichés de manière performante et paginée.
   * Sur TV, les lignes n'ont pas de bouton "Voir tout", le parcours standard et optimal consiste à cliquer sur le bandeau de catégories en haut pour basculer sur la grille complète.

## Critères d'acceptation (Techniques)

- [ ] Sur l'écran des Films (VOD), en mode "Tout" (TV et mobile), aucune ligne horizontale de catégorie ne contient plus de 250 éléments.
- [ ] Sur l'écran des Séries, en mode "Tout" (TV et mobile), aucune ligne horizontale de catégorie ne contient plus de 250 éléments.
- [ ] Cliquer sur "Voir tout" à côté d'une catégorie sur mobile redirige bien vers le mode grille de cette catégorie, où la totalité du catalogue associé est disponible.
- [ ] Les performances de défilement vertical et horizontal sur TV en mode "Tout" sont améliorées, sans aucune saccade.

## Cas limites et gestion des erreurs

* Si une catégorie contient moins de 250 éléments, la totalité de ces éléments est affichée sans modification.
* Si le filtre de recherche de l'écran (`searchQuery`) est actif, le filtrage est appliqué d'abord, puis la liste filtrée est limitée à 250 éléments maximum par ligne, ce qui est robuste et instantané.

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
