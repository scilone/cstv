# B4 - Action Favori difficilement accessible sur mobile dans TV en direct « Tout »

## Informations générales

Type:
Bug

Status:
ARCHITECTURE

Created:
2026-07-21

Target version:
v1.48.32

---

# 1. Description

Sur **mobile**, dans l'écran **TV en direct** et la catégorie globale **« Tout »**, l'action permettant d'ajouter ou de retirer rapidement une chaîne des favoris n'est pas correctement accessible depuis les cartes des rangées horizontales.

Le problème est confirmé sur mobile. Il n'est pas établi à ce stade qu'Android TV présente le même défaut : la plateforme TV doit seulement faire l'objet d'une vérification de non-régression, sans être présentée comme la source du ticket ni recevoir de modification préventive.

---

# 2. Contexte

La catégorie « Tout » affiche, sur mobile, une liste verticale de catégories contenant chacune une rangée horizontale de chaînes. Ces rangées sont construites par `CategorySectionRow`, qui choisit le composant de carte selon la plateforme :

- mobile : `MobileStreamCard`;
- Android TV : `StreamTvCard`.

Dans `MobileStreamCard`, toute la carte est cliquable pour lancer la chaîne. L'étoile Favori est placée dans une `Row` située après le logo, le nom et les informations EPG, au bas d'une carte dont la hauteur est fixée à `180.dp`. Sa position dépend donc de la quantité de contenu précédente et se retrouve en concurrence avec ce contenu dans un espace vertical contraint.

La grille mobile d'une catégorie spécifique utilise un autre composant, `MobileChannelGridCard`, qui adopte déjà une structure plus robuste : l'étoile est superposée en haut à droite du logo, reste visible et dispose de sa propre action distincte de celle de la carte.

Le diagnostic précédent de B4 analysait à tort `StreamTvCard`, `fillMaxWidth()` et la navigation D-pad Android TV. Ces éléments ne décrivent pas le défaut mobile signalé et sont retirés du périmètre confirmé.

---

# 3. Spécification fonctionnelle

## Objectif

Rendre l'action Favori immédiatement visible, stable et utilisable au tactile sur chaque carte mobile des rangées de la catégorie « Tout », sans lancer la chaîne par effet de bord et sans modifier le comportement de lecture de la carte.

## User stories

- En tant qu'utilisateur mobile, je peux ajouter une chaîne aux favoris directement depuis une rangée de « Tout » sans ouvrir la chaîne ni sa catégorie.
- En tant qu'utilisateur mobile, je peux retirer une chaîne des favoris depuis la même action rapide.
- En tant qu'utilisateur mobile, je distingue immédiatement l'état favori ou non favori de chaque chaîne.
- En tant qu'utilisateur mobile, je continue de lancer la chaîne en touchant le reste de sa carte.

## Parcours utilisateur

1. L'utilisateur ouvre **TV en direct** sur mobile et conserve ou sélectionne la catégorie « Tout ».
2. Il parcourt horizontalement les chaînes d'une catégorie fournisseur.
3. Chaque carte affiche une étoile dans une position fixe, en haut à droite de la zone de logo.
4. Il touche l'étoile d'une chaîne non favorite.
5. La chaîne n'est pas lancée; l'état est enregistré pour le profil actif et l'étoile devient jaune.
6. La rangée « Favoris » apparaît ou se met à jour immédiatement.
7. Un second toucher sur l'étoile retire la chaîne des favoris sans lancer la lecture.

## Règles métier et d'interaction

- Le cas fonctionnel confirmé concerne le rendu **mobile** des rangées horizontales de « Tout », y compris la rangée « Favoris ».
- L'étoile est toujours visible : jaune lorsque la chaîne est favorite, claire sur fond sombre sinon.
- La position de l'étoile ne dépend ni de la présence d'un programme EPG, ni de la longueur du nom, ni de l'état de chargement de l'image.
- Toucher l'étoile exécute uniquement `onToggleFavorite`.
- Toucher le reste de la carte exécute uniquement `onClick` et lance la chaîne.
- L'état favori reste local au profil actif, persistant dans Room et réactif dans toutes les rangées concernées.
- La correction ne modifie pas le catalogue, l'EPG, les chaînes récemment regardées ni les favoris d'un autre profil.
- Les cartes de la grille mobile d'une catégorie spécifique conservent leur comportement actuel.
- Android TV n'est pas modifié tant qu'un défaut équivalent n'y est pas reproduit et spécifié.

## Critères d'acceptation

- Dans « Tout » sur mobile, toutes les cartes de chaînes affichent une étoile entièrement visible au même emplacement.
- L'étoile reste accessible avec ou sans logo, avec ou sans EPG et avec un nom long.
- Un toucher sur l'étoile ajoute ou retire le favori sans lancer la chaîne.
- Un toucher hors de l'étoile continue de lancer la chaîne et ne modifie pas le favori.
- L'icône reflète immédiatement l'état persistant observé par l'écran.
- La rangée « Favoris » apparaît, se met à jour ou disparaît conformément à son contenu.
- Le retrait depuis la rangée « Favoris » ne supprime pas la chaîne de sa catégorie fournisseur.
- La grille mobile d'une catégorie spécifique ne régresse pas.
- Android TV conserve son rendu et ses interactions actuels; son éventuel défaut analogue reste à confirmer séparément.

## Cas limites

- Une chaîne sans logo affiche le visuel de substitution et conserve l'étoile au-dessus de cette zone.
- Une chaîne sans EPG garde la même hauteur et la même position d'action qu'une chaîne avec EPG.
- Un nom ou un programme long est tronqué sans recouvrir l'étoile.
- Si le dernier favori est retiré depuis la rangée « Favoris », la rangée disparaît sans laisser d'espace vide ni provoquer de crash.
- Après un changement de profil, les icônes reflètent uniquement les favoris du nouveau profil.
- Des touchers rapides répétés ne doivent pas lancer la chaîne par propagation du geste.

## Gestion des erreurs

- La bascule Favori est locale et ne dépend pas du réseau Xtream.
- Aucun état optimiste propre à la carte n'est introduit : l'icône reflète la liste persistée observée.
- Si l'écriture locale échoue, la chaîne ne doit pas être lancée et l'icône doit rester ou revenir à son dernier état persistant.
- B4 ne refond pas le canal global d'erreur de `FavoritesViewModel`; une évolution transversale de Snackbar relève d'un ticket distinct.

---

# 4. Décisions de périmètre

- **Plateforme confirmée : mobile.**
- **Écran confirmé : TV en direct, catégorie « Tout », rangées horizontales.**
- L'action adopte le placement superposé déjà utilisé par `MobileChannelGridCard` afin d'unifier les interactions mobiles.
- Le composant Android TV `StreamTvCard`, ses dimensions et son focus D-pad restent inchangés dans B4.
- Si une reproduction ultérieure confirme le même problème sur Android TV, son comportement D-pad et son architecture de focus devront être spécifiés explicitement avant modification.

---

# 5. Spécification technique

## Diagnostic confirmé dans le code

- `MobileLayout` appelle `CategorySectionRow(..., isTv = false)` dans le mode « Tout ».
- `CategorySectionRow` sélectionne alors `MobileStreamCard`, et non `StreamTvCard`.
- `MobileStreamCard` fixe sa taille à `150.dp × 180.dp` et place l'`IconButton` Favori dans la dernière `Row`, après le contenu EPG variable.
- La carte entière porte également un `Modifier.clickable { onClick() }`; l'action enfant doit rester une cible tactile clairement séparée et stable.
- `MobileChannelGridCard`, utilisé dans les catégories spécifiques, possède déjà une étoile superposée en haut à droite de la zone logo. Ce pattern existant constitue la référence technique de la correction.
- Le callback `onToggleFavorite` et la chaîne de persistance fonctionnent déjà; aucun changement de repository ou de ViewModel n'est nécessaire.

## Adaptation de `MobileStreamCard`

- Conserver la largeur `150.dp`, la hauteur `180.dp` et l'action principale de la carte.
- Déplacer l'`IconButton` Favori dans le `Box` du logo avec `Modifier.align(Alignment.TopEnd)`.
- Utiliser un fond sombre circulaire ou fortement arrondi afin de préserver le contraste sur les logos clairs.
- Garder une cible tactile stable et distincte, sans conditionner sa composition à l'état favori.
- Utiliser une étoile jaune quand `isFavorite == true` et blanche atténuée sinon.
- Employer un `contentDescription` dépendant de l'état : « Ajouter aux favoris » ou « Retirer des favoris ».
- Supprimer l'`IconButton` de la `Row` inférieure. Le numéro de chaîne peut rester seul dans cette zone sans influencer la position de l'action.
- Ne pas créer d'état favori local optimiste; `isFavorite` reste la source de vérité fournie par l'observation Room.

## Réutilisation et cohérence

La correction reprend le pattern de `MobileChannelGridCard` sans extraire immédiatement un nouveau composant partagé : les deux cartes ont des dimensions et contenus différents, et l'extraction d'une abstraction visuelle pour une seule icône ajouterait plus de complexité que de réutilisation. Les couleurs, rayons et dimensions doivent toutefois rester cohérents entre les deux cartes mobiles.

## Ressources

Deux chaînes localisées sont ajoutées ou réutilisées dans `app/src/main/res/values/strings.xml` :

- `live_tv_add_favorite` : « Ajouter aux favoris »;
- `live_tv_remove_favorite` : « Retirer des favoris ».

Aucune nouvelle icône ni ressource graphique n'est requise.

## Persistance et dépendances

- Flux inchangé : `MobileStreamCard` → `onToggleFavorite` → `FavoritesViewModel` → use cases Favoris → Room → `favoritesList` → recomposition.
- Aucun changement de modèle, repository, use case, base Room ou migration.
- Aucun appel réseau, dépendance Gradle, interface Retrofit ou règle ProGuard supplémentaire.
- Min SDK 21 inchangé.

## Contraintes de performance

- Aucun nouveau collecteur, accès Room, appel réseau ou chargement d'image.
- Le déplacement de l'icône ne change pas la virtualisation du `LazyRow`.
- La recomposition reste limitée aux cartes dont l'état favori change.

---

# 6. Architecture

## Responsabilités

```text
MobileLayout, mode « Tout »
└── CategorySectionRow(isTv = false)
    └── MobileStreamCard
        ├── carte : lecture de la chaîne
        └── étoile superposée : bascule du favori
            └── FavoritesViewModel -> Room -> favoritesList
```

- `MobileStreamCard` gère le placement et la séparation des deux cibles tactiles.
- `CategorySectionRow` continue de sélectionner la carte adaptée à la plateforme.
- `FavoritesViewModel` et les couches domaine/data restent responsables de la persistance et de la diffusion réactive.
- `StreamTvCard` reste hors du correctif tant que le problème Android TV n'est pas confirmé.

## Fichiers impactés

- `app/src/main/java/com/cstv/app/presentation/livetv/components/LiveTvComponents.kt`
  - déplacement et accessibilité de l'étoile dans `MobileStreamCard`;
  - aucune modification de `StreamTvCard`.
- `app/src/main/res/values/strings.xml`
  - libellés accessibles Ajouter/Retirer des favoris.
- `ai/bugs/B4-tv-quick-favorites-all-category.md`
  - correction du périmètre, du diagnostic et de l'architecture.

## Nouveaux composants

Aucun nouveau composant d'architecture. La correction adapte uniquement le composant mobile existant et réutilise la chaîne Favoris actuelle.

---

# 7. Validation prévue

## Vérifications automatisées

- `./gradlew testDebugUnitTest` pour la non-régression Favoris.
- `./gradlew assembleDebug` pour la compilation Compose et les ressources.
- `./gradlew lintDebug` pour les ressources et contrôles statiques d'accessibilité.

Le projet ne dispose pas d'une infrastructure de tests UI Compose adaptée à cette interaction tactile. Aucun test unitaire artificiel n'est ajouté pour un placement de `Modifier`; la validation fonctionnelle est manuelle sur mobile.

## Scénarios manuels obligatoires

1. Mobile, « Tout », catégorie fournisseur : ajouter puis retirer un favori depuis l'étoile sans lancer la chaîne.
2. Mobile, rangée « Favoris » : retirer une chaîne et vérifier la mise à jour ou la disparition de la rangée.
3. Cartes avec/sans logo, avec/sans EPG et textes longs : vérifier position, contraste et absence de recouvrement.
4. Toucher le reste de la carte : vérifier que la lecture fonctionne sans bascule du favori.
5. Catégorie mobile spécifique : vérifier la grille et son étoile existante.
6. Changement de profil : vérifier l'actualisation des états favoris.
7. Android TV, « Tout » et catégorie spécifique : contrôle de non-régression uniquement; consigner séparément tout défaut réellement observé.

## Risques techniques et atténuations

- **Propagation du toucher vers la carte** : conserver deux cibles cliquables distinctes et tester l'étoile sur plusieurs positions de rangée.
- **Contraste insuffisant sur un logo clair** : fond sombre sous l'étoile.
- **Action trop petite** : conserver une zone tactile stable sans réduire l'icône au seul glyphe visible.
- **Chevauchement du logo** : réserver le coin supérieur droit et appliquer le même pattern que la grille mobile.
- **Régression Android TV par composant partagé** : ne modifier ni `StreamTvCard` ni la branche `isTv`.
