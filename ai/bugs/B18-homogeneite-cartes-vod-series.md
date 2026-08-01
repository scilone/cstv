# B18 - Non-homogénéité des cartes de Films et Séries (Titre sous la vignette)

## Informations générales

Status:
ANALYSIS

Created:
2026-08-02

---

# 1. Description

Actuellement, sur les pages de listes des **Films (VOD)** et des **Séries**, lorsqu'une catégorie spécifique est sélectionnée (ce qui affiche une grille verticale d'éléments), les cartes affichent le titre textuel du média en dessous de sa vignette. C'est également le cas sur TV en mode "Tout" (All), où les rangées horizontales utilisent des cartes spécifiques affichant ce titre.

Ce comportement n'est pas homogène avec le reste de l'application (comme l'Accueil ou le mode "Tout" sur mobile) qui utilise de superbes cartes modernes, épurées et standardisées : `HomeVodMovieCard` et `HomeSeriesShowCard`. Ces dernières affichent uniquement l'affiche (format 2:3), la note et les éventuels badges, sans texte redondant en dessous.

L'objectif est d'harmoniser l'ensemble de l'application en supprimant le titre textuel sous la vignette sur les écrans Films et Séries, et d'unifier l'affichage en utilisant partout les cartes standards `HomeVodMovieCard` et `HomeSeriesShowCard`.

---

# 2. Contexte

Dans la base de code :
* **`MovieTvCard`** est un composant privé défini dans `app/src/main/java/com/cstv/app/presentation/vod/VodScreen.kt`. Il est utilisé sur TV pour la grille d'une catégorie et les rangées horizontales du mode "Tout". Il affiche une `Card` contenant une `Column` avec l'image puis le titre.
* **`SeriesTvCard`** est l'équivalent de `MovieTvCard` mais défini dans `app/src/main/java/com/cstv/app/presentation/series/SeriesScreen.kt`.
* Sur mobile, dans le mode "Catégorie spécifique" (grille), des cartes codées en dur avec une `Column` (image + titre textuel) sont également utilisées.
* À l'inverse, l'Accueil (`HomeScreen.kt`) et le mode "Tout" des Films/Séries sur mobile utilisent les composants globaux **`HomeVodMovieCard`** et **`HomeSeriesShowCard`** (déclarés dans `HomeCards.kt`), qui n'affichent pas le titre textuel en dessous car l'affiche du film ou de la série suffit à l'identifier.

Cette disparité visuelle et technique crée une dette de design (cartes plus larges, de styles et de coins différents) et de maintenance.

---

# 3. Spécification fonctionnelle

## Objectif

Unifier l'expérience utilisateur et le design de l'application en supprimant le titre textuel sous les vignettes des Films et Séries sur les pages de listes (TV et mobile), et en remplaçant tous les composants de cartes personnalisés ou obsolètes par les cartes de référence `HomeVodMovieCard` et `HomeSeriesShowCard`.

## User stories

* **En tant qu'utilisateur (mobile et TV)**, lorsque je navigue dans une catégorie spécifique de Films ou de Séries (grille), je vois une grille harmonieuse de posters au format propre 2:3 avec leurs notes, identique à l'affichage de l'Accueil, sans texte en dessous qui décale les alignements.
* **En tant qu'utilisateur TV**, lorsque je parcours le mode "Tout" de Films ou Séries, les rangées horizontales présentent des cartes unifiées et de même taille que celles de la Home, offrant une transition visuelle invisible et fluide.

## Règles métier et de rendu

1. **Suppression de la redondance** :
   * Retirer définitivement le titre textuel sous le poster pour les Films et Séries sur les pages de listes.
   * Supprimer les fonctions privées obsolètes `MovieTvCard` (dans `VodScreen.kt`) et `SeriesTvCard` (dans `SeriesScreen.kt`).
2. **Utilisation des cartes de référence** :
   * Utiliser **`HomeVodMovieCard`** pour tous les affichages de films sur `VodScreen.kt` (mode "Tout" horizontal sur TV, et grilles verticales de catégories sur TV et mobile).
   * Utiliser **`HomeSeriesShowCard`** pour tous les affichages de séries sur `SeriesScreen.kt` (mode "Tout" horizontal sur TV, et grilles verticales de catégories sur TV et mobile).
3. **Mise en page des grilles** :
   * S'assurer que le remplacement des cartes s'intègre parfaitement dans les grilles existantes (3 colonnes sur mobile, 4 colonnes sur TV), sans déformer les images ou casser les marges de défilement.

## Critères d'acceptation (Fonctionnels)

- [ ] Sur les pages Films et Séries (TV et mobile), le titre en texte brut sous le poster est supprimé de tous les affichages (grilles et rangées).
- [ ] Sur TV, en mode "Tout", les rangées de Films et Séries utilisent les cartes standardisées `HomeVodMovieCard` et `HomeSeriesShowCard`.
- [ ] En mode "Catégorie spécifique" (grille), sur TV et mobile, les cellules affichent directement `HomeVodMovieCard` et `HomeSeriesShowCard` intégrées de façon homogène.
- [ ] Les dimensions, arrondis de coins (radius 14.dp) et ombres de focus sur TV sont rigoureusement identiques à ceux de l'Accueil.

## Hypothèses et Questions ouvertes

* *Lisibilité des affiches* : Certains flux IPTV ont parfois des affiches manquantes ou textuellement peu lisibles. Cependant, `HomeVodMovieCard` et `HomeSeriesShowCard` affichent déjà un placeholder propre (icône Warning neutre) en cas d'affiche manquante. De plus, la fiche de détails (au clic ou sur focus TV) permet à l'utilisateur de lire le titre complet du média en grand. Le retrait du titre sous la vignette est donc tout à fait viable et correspond au standard esthétique des applications premium actuelles.

---

# 4. Spécification technique

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
