# F14 - Rendre collant (sticky) le bouton de validation de la recherche avancée

## Informations générales

Type:
Feature

Status:
SPECIFICATION

Created:
2026-07-26

Target version:
v1.56.0

---

# 1. Description

Dans l'écran de recherche, le volet de recherche avancée (`AdvancedSearchSheet.kt`) permet à l'utilisateur d'appliquer divers filtres (type de média, catégorie, note minimale, années de sortie et genres).

Aujourd'hui, l'ensemble du contenu du volet est intégré dans un conteneur vertical déroulant unique (`Column` avec `.verticalScroll`). Le bouton de validation « Voir les résultats (X) » est situé tout à la fin de cette colonne.
Ce fonctionnement pose deux problèmes ergonomiques :
1. **Défaut de visibilité initial :** Lorsque le volet s'ouvre, le bouton de validation est invisible car repoussé en bas par le grand nombre de filtres disponibles. L'utilisateur peut ne pas comprendre immédiatement comment lancer la recherche.
2. **Frottement d'usage (friction) :** Même si l'utilisateur sait que le bouton se trouve en bas, il doit systématiquement faire défiler (scroller) tout le volet vers le bas pour pouvoir valider ses choix de filtres, ce qui s'avère fastidieux à l'usage quotidien.

L'évolution consiste à rendre le bouton de validation de recherche "collant" (sticky) afin qu'il reste toujours affiché et fixe au bas de la Bottom Sheet, indépendamment du défilement des filtres situés au-dessus de lui.

---

# 2. Contexte

Le composant `AdvancedSearchSheet` est affiché sous forme de `ModalBottomSheet` de Jetpack Compose (dans `AdvancedSearchSheet.kt`).
Son layout actuel est le suivant :

```kotlin
ModalBottomSheet(...) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 20.dp)
            .focusGroup()
    ) {
        // En-tête "Recherche avancée" et bouton "Réinitialiser"
        // ...
        // Boutons "Film" et "Série"
        // ...
        // Catégorie Dropdown
        // ...
        // Note minimum
        // ...
        // Année de sortie (Slider ou Steppers sur TV)
        // ...
        // Liste des Genres (FlowRow)
        // ...
        // Bouton de validation "Voir les résultats (X)"
    }
}
```

Parce que `.verticalScroll` est appliqué sur la `Column` racine de la bottom sheet, tout le contenu (y compris le bouton) défile ensemble. Lorsque les genres ou les catégories sont développés, la hauteur totale dépasse largement la hauteur d'écran, masquant complètement le bouton d'action principal.

Pour éliminer ce problème, nous devons restructurer le layout de la Bottom Sheet en séparant la zone de défilement (les options de filtres) et la zone fixe de validation (le bouton "Voir les résultats").

---

# 3. Objectif

Rendre l'interface de recherche avancée beaucoup plus intuitive et rapide à utiliser en gardant le bouton d'action principal « Voir les résultats » continuellement ancré en bas de l'écran (mode sticky), tout en conservant le défilement indépendant pour l'ensemble des filtres du dessus.

---

# 4. Hypothèses

- **Restructuration du Layout Compose :** Pour dissocier le défilement, le composant `ModalBottomSheet` devra contenir un conteneur principal non scrollable (comme une `Column` ou un `Box` de hauteur maximale).
  - La partie supérieure contenant l'en-tête et tous les filtres sera placée dans une sous-`Column` avec `Modifier.weight(1f).verticalScroll(rememberScrollState())` pour occuper l'espace disponible et défiler de manière fluide.
  - Le bouton d'action sera placé juste en dessous de cette zone scrollable, dans une section fixe avec son propre padding et éventuellement une légère ligne de séparation (ou un dégradé d'ombrage) pour marquer la transition visuelle.
- **Gestion du Focus sur Android TV :** Sur Android TV, le comportement du focus du D-pad doit être rigoureusement préservé. Le bouton d'action doit rester facilement accessible par le focus (en scrollant vers le bas ou en naviguant directement), et la navigation D-pad ne doit pas être perturbée par la séparation des conteneurs.
- **Préservation de la hauteur de la Bottom Sheet :** Sur les petits écrans de smartphones, il faudra veiller à ce que la zone déroulante conserve une taille suffisante et ne soit pas excessivement comprimée par le bouton fixe du bas, assurant une parfaite ergonomie sur tous les form-factors.

---

# 5. Questions ouvertes

1. **Style visuel de la transition :** Faut-il ajouter une fine ligne de démarcation (un `HorizontalDivider` discret) ou un effet de dégradé transparent au-dessus du bouton sticky pour indiquer visuellement que le contenu des filtres passe en dessous lors du défilement ?
2. **Bouton Réinitialiser :** Le bouton "Réinitialiser" (actuellement situé dans l'en-tête scrollable) doit-il rester en haut ou devrait-il également être déplacé à côté ou au-dessus du bouton de validation sticky en bas pour être toujours accessible ?
3. **Hauteur maximale sur mobile :** Comment réagit le composant standard `ModalBottomSheet` de Compose si la hauteur cumulée de l'en-tête + filtres + bouton fixe dépasse l'écran sur de très vieux téléphones ? Il faudra tester pour s'assurer que le clavier virtuel ou les dimensions de l'écran n'occultent pas la zone déroulante des filtres.

---

# 6. Spécification fonctionnelle

## User stories

- En tant qu'utilisateur mobile, je veux voir immédiatement l'action « Voir les résultats » et pouvoir l'utiliser sans parcourir tous les filtres.
- En tant qu'utilisateur Android TV, je veux pouvoir atteindre et activer cette action au D-pad sans perdre le focus ni bloquer le défilement des filtres.
- En tant qu'utilisateur, je veux que le nombre de résultats affiché sur le bouton reflète mes filtres en cours avant de les appliquer.

## Comportement attendu

À l'ouverture de la recherche avancée, le bouton « Voir les résultats (X) » est visible et ancré en bas de la Bottom Sheet. Il ne défile pas avec les filtres.

- L'en-tête, le choix du type de média, la catégorie, la note minimale, la période de sortie et les genres constituent la zone défilante située au-dessus du bouton.
- Le bouton conserve en permanence le libellé actuel et le compteur `X` correspondant aux filtres sélectionnés en cours.
- Modifier un filtre met à jour le compteur sans déplacer, masquer ou recréer visiblement le bouton.
- Appuyer sur le bouton applique les filtres en cours puis ferme le volet, conformément au comportement actuel.
- Le bouton « Réinitialiser » reste dans l'en-tête de la zone défilante et conserve son comportement actuel ; il n'est pas rendu sticky.

## Parcours utilisateur

1. L'utilisateur ouvre la recherche avancée : le bouton de résultats est déjà visible au bas du volet.
2. Il consulte et modifie les filtres en faisant défiler uniquement la zone supérieure.
3. Le bouton reste accessible pendant tout le défilement et affiche le nouveau nombre de résultats.
4. Il valide depuis ce bouton ; les résultats correspondent aux filtres visibles et le volet se ferme.

## Règles métier et cas limites

- Le changement est purement ergonomique : les filtres disponibles, leurs valeurs, leur ordre et leur logique de calcul ne changent pas.
- Lorsque les catégories ou genres rendent le contenu plus haut que l'écran, seul le contenu des filtres défile ; l'action principale demeure affichée.
- Sur un petit écran ou lorsque l'espace vertical est réduit, la zone défilante utilise l'espace restant sans recouvrir le bouton.
- Sur Android TV, tous les contrôles existants restent atteignables au D-pad. Le focus peut atteindre le bouton fixe et son indicateur visuel de focus est conservé.
- Fermer le volet par le geste, le retour ou le scrim ne doit pas appliquer les filtres tant que le bouton n'a pas été activé, comme aujourd'hui.
- Aucun nouveau séparateur, dégradé ou changement visuel hors du repositionnement du bouton n'est requis par ce ticket.

## Critères d'acceptation

- [ ] À l'ouverture sur mobile, « Voir les résultats (X) » est visible sans défilement initial.
- [ ] Après avoir fait défiler les filtres jusqu'aux genres, le bouton reste visible et activable.
- [ ] Changer le type, la catégorie, la note, l'année ou un genre met à jour le compteur sans déplacer le bouton.
- [ ] Valider applique exactement les filtres en cours et ferme le volet comme avant.
- [ ] Sur Android TV, la navigation D-pad atteint le bouton, l'active et conserve un retour de focus visible.
- [ ] Les petits écrans conservent une zone de filtres défilante utilisable sans que le bouton soit recouvert.
