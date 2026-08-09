# F32 - Paramètres TV : Refonte de l'écran des paramètres

## Informations générales

Status:
IDEA

Created:
2026-08-10

---

# 1. Description

Refonte ergonomique et visuelle de l'écran des paramètres sur Android TV afin de respecter la charte esthétique de l'application et de supprimer les éléments non pertinents pour l'expérience TV :
- Retrait définitif du bouton de retour ("Retour") inutile sur l'écran TV (la touche Back physique de la télécommande gère déjà ce comportement).
- Suppression complète de la section de téléchargements ("Téléchargements hors-ligne") sur TV.
- Refonte des boutons d'action clés (Gérer les catégories, Forcer la mise à jour, Extraire les logs de diagnostic, et Déconnexion) pour s'aligner sur le design de l'application (utilisation de `Box` + focus liseré/lumière avec `AccentLavande`, `Surface3` ou `RatingDislike` au lieu de `TvButton` brut).

---

# 2. Contexte

L'écran de paramètres TV actuel utilise la bibliothèque `androidx.tv.material3.Button` brute qui applique ses propres styles et schémas de couleurs par défaut, créant une disparité esthétique majeure par rapport au reste de l'application TV (qui utilise des liserés sur mesure avec `AccentLavande` et des angles arrondis uniformes). De plus, certaines fonctionnalités comme les téléchargements hors-ligne sont spécifiques aux smartphones et n'ont aucun sens sur Android TV. Enfin, la présence d'un bouton de retour visuel surcharge l'UI TV inutilement alors que l'intégralité de la navigation TV repose sur la télécommande.

---

# 3. Objectif

- Assurer une harmonisation visuelle complète des boutons de paramètres TV en adoptant le design moderne et unifié de la charte graphique de l'app (radius 8 dp, liseré lumineux `AccentLavandeHover` / `AccentLavande` au focus, couleurs et contrastes maîtrisés).
- Retirer le bloc ou la carte de gestion des téléchargements spécifiquement pour l'affichage TV.
- Retirer le bouton Retour en haut de l'écran TV pour maximiser l'espace et simplifier la navigation au D-pad.
- Améliorer l'expérience utilisateur globale en rendant le focus plus lisible de loin sur les cartes et les actions de paramètres.

---

# 4. Hypothèses

- La version mobile (`MobileSettingsLayout`) conserve toutes ses fonctionnalités (bouton Retour, section Téléchargements, design standard des boutons mobiles). Seul l'affichage TV (`TvSettingsLayout`) est impacté par ces simplifications et cette refonte.
- Les boutons personnalisés TV utiliseront la même mécanique d'affichage et de focus que le bouton de lecture (`PlayButton`) de la fiche film redessinée dans F30, garantissant un liseré net et réactif lors de la sélection au D-pad.
- Le retrait du bouton "Retour" n'altère en rien la navigation arrière, qui continuera d'être interceptée nativement par les handlers standards (BackHandler) de la télécommande.

---

# 5. Questions ouvertes

- Devrait-on regrouper certaines options de configuration pour épurer encore plus l'écran sur TV ?
- Quelle couleur de conteneur appliquer pour les boutons d'actions secondaires (ex: "Extraire les logs" ou "Gérer les catégories") par rapport aux boutons principaux d'options ? (ex: `Surface3` par défaut, `AccentLavande` ou liseré blanc au focus).
- Pour le bouton de déconnexion, utiliserons-nous un liseré rouge ou blanc au focus pour signaler la dangerosité de l'action ?
