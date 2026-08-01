# B17 - Absence du sélecteur pivot et focus par défaut au démarrage/changement de page

## Informations générales

Status:
ANALYSIS

Created:
2026-08-02

---

# 1. Description

Lorsque l'utilisateur démarre l'application ou change d'onglet principal (TV en direct, Films/VOD, Séries) sur Android TV, le sélecteur visuel de focus (le pivot) n'est pas affiché d'emblée. Aucune carte de média n'est mise en évidence par défaut.

Par conséquent, la première utilisation des touches fléchées de la télécommande (D-pad) produit un comportement très aléatoire : le focus peut atterrir de façon imprévisible sur un élément, ou rester piégé dans la barre de navigation latérale.

Pour corriger cela et garantir une navigation TV stable et fluide, le sélecteur doit être visible dès l'affichage d'un écran de catalogue, et se positionner automatiquement sur le premier média disponible dès que le chargement est terminé.

---

# 2. Contexte

Le projet utilise un défilement à sélecteur fixe (Fixed Focus/Pivot Scrolling - F19) qui garantit une excellente stabilité visuelle de défilement sur TV. Cependant, F19 réagit au focus acquis mais ne gère pas le focus initial lors de l'entrée sur l'écran ou de l'achèvement des chargements de données.

Actuellement :
* Dans `MainActivity.kt`, un `contentFocusRequester` tente de demander le focus sur le conteneur principal (`AppNavGraph`) lors d'une navigation.
* Cependant, au moment exact de l'entrée sur les écrans (TV en direct, VOD, Séries), les données du catalogue sont en cours de chargement (`state.isLoadingStreams` ou `state.isLoading` est à `true`). L'écran n'affiche alors qu'un indicateur de chargement circulaire (`CircularProgressIndicator`), qui n'est pas focusable.
* Toutes les tentatives répétées de `contentFocusRequester.requestFocus()` échouent donc silencieusement durant la phase de chargement de l'écran car aucun nœud de média focusable n'est présent dans l'arbre Compose.
* Une fois le chargement achevé, les cartes de médias apparaissent bien à l'écran, mais aucun mécanisme ne redemande de focus explicite sur le premier élément de média. Le sélecteur de focus reste invisible jusqu'à ce que l'utilisateur appuie de façon incertaine sur le D-pad.

---

# 3. Spécification fonctionnelle

## Objectif

Sur Android TV, forcer l'acquisition automatique et visuelle du focus sur le tout premier média d'un écran principal (Accueil, TV en direct, Films/VOD, Séries) dès que celui-ci est visible et chargé, éliminant ainsi les comportements de navigation aléatoires lors d'un changement d'onglet ou au démarrage.

Le mobile est explicitement hors périmètre : aucun focus automatique ne doit y être appliqué afin de ne pas interférer avec l'expérience tactile standard.

## User stories

* **En tant qu'utilisateur Android TV**, lorsque l'application démarre et que l'Accueil s'affiche, je vois immédiatement la première carte tendance (le carrousel Hero Card `HomeTrendingCarouselTv`) focalisée et mise en évidence visuellement, sans avoir à appuyer sur le D-pad pour "réveiller" le focus.
* **En tant qu'utilisateur Android TV**, lorsque je navigue vers les onglets *TV en direct*, *Films* ou *Séries*, dès que le catalogue finit de charger :
  * En mode **"Tout"** (all) : le focus se positionne automatiquement sur le premier élément média de la première ligne disponible (ex: la première carte de "Continuer à regarder" s'il y en a, sinon "Favoris", sinon le premier média de la première catégorie).
  * En mode **"Catégorie spécifique"** : le focus se positionne automatiquement sur le premier média de la grille (cellule à l'index 0).
* **En tant qu'utilisateur mobile**, je conserve mon expérience tactile inchangée, sans aucun focus visuel automatique ni focus forcé au chargement des écrans.

## Parcours utilisateur et règles d'interaction

1. **Démarrage de l'application** : L'utilisateur arrive sur l'Accueil. Dès que les tendances ou sections se chargent, le focus est demandé et acquis sur la Hero Card (si présente) ou sur la première carte de la première section. Le sélecteur (cadre de focus) apparaît d'emblée à l'écran.
2. **Changement d'onglet principal** : L'utilisateur ouvre la barre de navigation latérale et sélectionne l'onglet "Films" (VOD). La barre se replie, l'écran de VOD affiche son loader. Dès que le chargement se termine et que la liste apparaît, le focus est acquis automatiquement sur la première carte de la première section non vide.
3. **Comportement du D-pad** : Une fois le focus initialement positionné sur le premier média, l'utilisateur peut naviguer immédiatement de façon prévisible (D-pad Droite, Gauche pour ouvrir la barre de navigation, Bas pour changer de ligne).
4. **Mémorisation et Restauration** : Si l'utilisateur revient sur un onglet déjà consulté au cours de la même session, on privilégie la restauration du focus là où l'utilisateur l'avait laissé (si possible via la sauvegarde d'état de Compose / `LazyListState`). Si aucune position de focus précédente n'est connue ou restaurable, on applique le focus par défaut sur le premier média.

## Hypothèses sur l'ordre de priorité du premier média par écran

Pour chaque écran, on définit le "premier média" cible du focus initial selon les priorités suivantes :

1. **Accueil (`HomeScreen`)** :
   * Priorité 1 : La carte active du carrousel de tendances (`HomeTrendingCarouselTv`) si la liste des tendances n'est pas vide.
   * Priorité 2 : Le premier élément de la section "Continuer à regarder" (`home_resume`) s'il y en a.
   * Priorité 3 : Le premier élément de la section "Favoris" (`home_favorites`) s'il y en a.
   * Priorité 4 : Le premier élément de la section "TV" (`home_livetv`) s'il y en a.
   * Priorité 5 : Le premier élément de la première autre section non vide disponible.

2. **Films/VOD (`VodScreen`) & Séries (`SeriesScreen`)** :
   * **En mode "Tout"** (All) :
     * Priorité 1 : Le premier élément de la section "Continuer à regarder" (`resume_watching`) s'il y en a.
     * Priorité 2 : Le premier élément de la section "Favoris" (`favorites`) s'il y en a.
     * Priorité 3 : Le premier élément de la première catégorie non vide disponible.
   * **En mode "Catégorie spécifique"** (Grille) :
     * Le premier élément (index 0) de la grille de médias.

3. **TV en direct (`LiveTvScreen`)** :
   * **En mode "Tout"** (All) :
     * Priorité 1 : Le premier élément de la section "Récemment consultés" (`recently_watched`) s'il y en a.
     * Priorité 2 : Le premier élément de la section "Favoris" (`favorites`) s'il y en a.
     * Priorité 3 : Le premier élément de la première catégorie non vide disponible.
   * **En mode "Catégorie spécifique"** (Grille) :
     * Le premier élément (index 0) de la grille de chaînes.

## Critères d'acceptation (Fonctionnels)

- [ ] Sur Android TV, à l'ouverture de l'Accueil, le focus visuel est automatiquement positionné sur le carrousel de tendances (Hero Card) dès qu'il est affiché.
- [ ] Sur Android TV, à l'ouverture des onglets TV, Films et Séries (en mode "Tout"), dès que le chargement se termine, le premier média disponible reçoit automatiquement le focus visuel.
- [ ] Sur Android TV, à l'ouverture des onglets TV, Films et Séries filtrés sur une catégorie spécifique (grille), le premier élément de la grille reçoit automatiquement le focus visuel une fois le chargement terminé.
- [ ] Sur Android TV, si un écran est rechargé ou rafraîchi manuellement, le focus initial est redemandé proprement dès la fin du rechargement.
- [ ] Sur mobile, aucun comportement de focus automatique n'est introduit et le défilement tactile reste standard.

## Cas limites et gestion des erreurs

- **Aucun média disponible** : Si l'écran ou la section est vide après le chargement, le focus initial ne doit pas tenter de se poser dans le vide, et doit pouvoir être capturé par d'autres composants interactifs (ex: la barre de catégories ou la barre de navigation).
- **Chargement infini / Erreur de chargement** : En cas d'erreur de chargement ou de catalogue indisponible, le focus ne doit pas boucler ou provoquer de crashs. Il se replie sur les boutons d'action disponibles (ex: le bouton "Réessayer").
- **Déconnexion de la télécommande / Reprise de session** : La demande de focus ne doit jamais bloquer le thread principal ni dégrader les performances au chargement.

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
