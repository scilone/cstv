# B8 - Visibilité insuffisante de l'étiquette de type de média sur l'accueil (Covers)

## Informations générales

Type:
Bug

Status:
SPECIFICATION

Created:
2026-07-22

---

# 1. Description

Les étiquettes (badges) indiquant le type de média (FILM / SÉRIE) affichées en superposition sur les affiches (covers) de l'écran d'accueil sont difficiles à lire sur certaines images de fond. La transparence actuelle du badge est trop prononcée, ce qui nuit gravement à la lisibilité sur les pochettes claires ou très détaillées.

---

# 2. Contexte

Le problème provient de l'utilisation d'un fond blanc extrêmement transparent en superposition avec du texte blanc :

1. **Badge des favoris (`HomeFavoriteItemCard` dans `HomeCards.kt`) :**
   Applique un fond `Color.White.copy(alpha = 0.2f)` (seulement 20 % d'opacité blanche) avec du texte blanc.

2. **Badge du carrousel de tendances (`HomeTrendingCarousel.kt`) :**
   Applique également un fond `Color.White.copy(alpha = 0.2f)` avec du texte blanc.

Cette opacité de 20 % blanche laisse passer la quasi-totalité de l'image de fond. Si l'affiche comporte des blancs ou des teintes claires dans le coin supérieur gauche, le texte blanc du badge devient totalement illisible par manque de contraste.

À titre de comparaison, d'autres badges dans l'application utilisent des fonds sombres plus opaques pour garantir la lisibilité :
- Le badge de note (top-right) utilise `Color(0xCC000000)` (noir à 80 % d'opacité).
- Le badge de reprise de lecture utilise `Color.Black.copy(alpha = 0.5f)` avec une fine bordure blanche transparente à 20 %.

---

# 3. Spécification fonctionnelle

## User stories

- En tant qu'utilisateur, je distingue immédiatement le type d'un contenu sur une affiche de l'accueil, quelle que soit l'image utilisée en arrière-plan.
- En tant qu'utilisateur, je retrouve le même repère visuel sur les favoris et dans le carrousel Tendances, sans qu'il concurrence les actions ou le contenu principal de l'écran.

## Parcours utilisateur

1. L'utilisateur ouvre l'accueil avec une ou plusieurs affiches, y compris des visuels très clairs, très sombres ou détaillés.
2. Sur chaque carte de favori, l'étiquette en haut à gauche identifie le contenu comme « FILM », « SÉRIE » ou « DIRECT ».
3. Dans le carrousel Tendances, l'étiquette au même rôle identifie le contenu comme « FILM » ou « SÉRIE ».
4. L'utilisateur lit l'étiquette sans devoir sélectionner la carte, attendre un changement d'état ou interpréter l'affiche.

## Règles métier et visuelles

- Le libellé du type de média reste inchangé et localisé comme aujourd'hui : « FILM », « SÉRIE » et « DIRECT » sur les favoris ; « FILM » et « SÉRIE » dans Tendances.
- Le contraste du texte blanc avec son fond doit rester suffisant sur toute image de couverture, indépendamment de la luminance, des couleurs ou des détails de l'image.
- Le fond du badge doit être sombre, neutre et suffisamment opaque pour assurer cette lisibilité, tout en laissant percevoir discrètement l'image sous-jacente ; aucune couleur vive ne doit être introduite.
- Les badges de type dans `HomeFavoriteItemCard` et `HomeTrendingCarousel` utilisent le même langage visuel : fond, texte, forme, rayon et hiérarchie typographique cohérents. Les différences d'espacement imposées par la taille respective des deux cartes peuvent être conservées.
- Le badge reste placé en haut à gauche et ne masque ni le titre, ni les indicateurs de note ou de progression, ni l'état de focus TV.
- Cette correction ne modifie pas les badges ayant une autre responsabilité (note, progression, téléchargement ou état de lecture), qui conservent leur style propre.

## Critères d'acceptation

- Sur une affiche majoritairement blanche ou très claire, chaque lettre du badge est immédiatement lisible.
- Sur une affiche sombre, colorée ou très détaillée, le badge reste lisible et ne donne pas l'impression de disparaître dans l'image.
- Les trois libellés de favoris (FILM, SÉRIE, DIRECT) et les deux libellés de Tendances (FILM, SÉRIE) respectent la même lisibilité.
- Le fond et le texte des badges de type sont visuellement identiques dans les deux composants concernés, hors dimensions et marges nécessaires au contexte de la carte.
- La consultation et la navigation au clavier/télécommande sur Android TV conservent un focus clairement visible sur la carte ; le badge ne le recouvre pas et ne capte pas le focus.
- L'affichage sans image de couverture conserve un badge lisible sur le fond de remplacement de la carte.

## Cas limites et gestion des erreurs

- Si le type d'un favori est inconnu, le libellé de repli existant est conservé et bénéficie du même traitement visuel lisible.
- Si une image se charge tardivement, échoue ou est remplacée par le fond de secours, l'étiquette reste présente et lisible sans clignotement fonctionnel.
- Si la liste de favoris ou Tendances est vide, aucun emplacement de badge isolé ne doit être affiché.
- La correction est purement visuelle : elle ne change ni l'action au clic, ni l'ordre des contenus, ni les données affichées par l'accueil.

---

# 4. Spécification technique

*(Non requise à l'étape 1 - Sera complétée à l'étape 3)*

---

# 5. Architecture

*(Non requise à l'étape 1 - Sera complétée à l'étape 3)*

---

# 6. Plan de développement

- [ ] Étape 1 : Analyse et structuration (Fait)
- [x] Étape 2 : Spécifications fonctionnelles détaillées (Fait le 2026-07-22)
- [ ] Étape 3 : Spécification technique et architecture (Identification des modifications de fichiers et tests)
- [ ] Étape 4 : Découpage des tâches détaillées
- [ ] Étape 5 : Implémentation de la correction
- [ ] Étape 6 : Review technique
- [ ] Étape 7 : Correction des retours de review
- [ ] Étape 8 : Validation finale (Automatisée + Fonctionnelle)
- [ ] Étape 9 : Documentation globale
- [ ] Étape 10 : Livraison Git et Archivage

---

# 7. Hypothèses et questions ouvertes

### Hypothèses
1. Remplacer le fond blanc transparent (`Color.White.copy(alpha = 0.2f)`) par un fond sombre transparent (ex. `Color.Black.copy(alpha = 0.65f)` ou `Color.Black.copy(alpha = 0.7f)`) tout en conservant le texte blanc résoudra de manière optimale et définitive le problème de lisibilité, sans sacrifier l'esthétique "Glass/Overlay" moderne de l'application.
2. Un fond sombre transparent est préférable à un fond blanc opaque, car il reste discret et s'intègre harmonieusement sur l'affiche sans créer un effet "bloc solide" inélégant.

### Questions ouvertes
- Devrait-on ajouter une fine bordure blanche transparente (ex. `Color.White.copy(alpha = 0.15f)`) autour de ces badges de type de média, similaire à ce qui est fait sur la carte de reprise de lecture, pour renforcer l'effet verre poli (Frosted Glass) ?
  *Réponse proposée :* L'effet sans bordure est plus épuré pour des badges aussi petits (8sp/10sp), mais nous pourrons évaluer visuellement les deux variantes lors de l'implémentation.

---

# 8. Review

*(À remplir lors des étapes ultérieures)*

---

# 9. Release

*(À remplir lors de la livraison)*
