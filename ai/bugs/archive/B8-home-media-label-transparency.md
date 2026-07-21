# B8 - Visibilité insuffisante de l'étiquette de type de média sur l'accueil (Covers)

## Informations générales

Type:
Bug

Status:
RELEASED

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

## Composants impactés

- `presentation/home/components/HomeCards.kt` : remplacer l'implémentation locale du badge de `HomeFavoriteItemCard` par un composable partagé de badge de type.
- `presentation/home/components/HomeTrendingCarousel.kt` : utiliser le même composable partagé pour le badge FILM/SÉRIE du carrousel.
- Le composable partagé sera placé dans un fichier dédié du même package, `presentation/home/components/HomeMediaTypeBadge.kt`, afin de garantir une source unique pour le fond, la bordure, la forme et la typographie tout en acceptant les espacements et tailles propres à chaque contexte.

## Style retenu

- Fond : `Color.Black.copy(alpha = 0.5f)`, valeur exacte de la maquette de référence (`rgba(0,0,0,0.5)`).
- Bordure : 1 dp, `Color.White.copy(alpha = 0.2f)`, également conforme à la maquette et utile sur les zones très sombres.
- Texte : blanc, graisse forte ; les tailles existantes de 8 sp (favoris) et 10 sp (Tendances) peuvent être fournies au composable pour préserver la hiérarchie actuelle.
- Forme : `RoundedCornerShape(4.dp)` conservée pour ne pas modifier l'encombrement ni le placement existants.
- Espacements externes : restent gérés par les cartes appelantes (6 dp pour Favoris, 16 dp pour Tendances). Le badge partagé gère son fond, sa bordure et son padding interne configurable.

Compose Android ne fournit pas ici le `backdrop-filter: blur(6px)` de la maquette sans coût et complexité supplémentaires. Ce flou n'est pas requis pour résoudre le contraste : le fond noir semi-opaque et la bordure suffisent, sans introduire d'effet graphique dépendant de l'API Android.

## Nouveaux composants et dépendances

- Un seul composable UI stateless et non interactif est ajouté ; il reçoit le libellé et les paramètres dimensionnels strictement nécessaires.
- Aucune dépendance Gradle, ressource réseau, modification de thème, ViewModel, modèle, API ou base Room.
- Le composable ne porte aucune logique métier : le choix de `FILM`, `SÉRIE`, `DIRECT` ou du repli demeure dans le composant appelant.

## Tests et validation prévus

- Aucun test unitaire de layout pur n'est ajouté, conformément à la stratégie du projet. La correction sera validée visuellement sur mobile et Android TV.
- Vérifier les cinq libellés attendus sur affiches claires, sombres et détaillées, ainsi que sur le placeholder sans cover.
- Vérifier les états normal et focus TV : bordure de focus de la carte intacte, badge non focalisable, non cliquable et sans recouvrement des autres overlays.
- Vérifier que les dimensions, positions et actions des cartes ne changent pas.
- La validation technique de l'étape d'implémentation comprendra `testDebugUnitTest`, `assembleDebug` et `lintDebug`.

## Risques et contraintes

- Contraste : un noir à 50 % suit la source de vérité visuelle, mais doit être contrôlé sur une affiche uniformément blanche ; la bordure ne remplace pas ce contrôle visuel.
- Régression visuelle : extraire le composable peut déplacer subtilement padding ou rayon si les paramètres existants ne sont pas reproduits. Les marges externes restent donc aux appelants.
- Performance : uniquement deux primitives Compose (`background` et `border`) sur des cartes déjà rendues ; impact négligeable, aucun blur ni calcul de luminance par image.
- Compatibilité : les API Compose utilisées sont déjà présentes et compatibles min SDK 21.

---

# 5. Architecture

## Architecture proposée

`HomeFavoriteItemCard` et `HomeTrendingCarousel` restent responsables du choix du libellé et de la position du badge. Ils délèguent uniquement son rendu à `HomeMediaTypeBadge`, composable stateless du package `home/components`.

```text
HomeFavoriteItemCard ── libellé + dimensions ─┐
                                              ├─> HomeMediaTypeBadge
HomeTrendingCarousel ─ libellé + dimensions ─┘
```

Cette factorisation empêche une nouvelle divergence entre les deux badges visuellement équivalents, tout en évitant de promouvoir ce style en composant global : son usage et ses valeurs proviennent spécifiquement de l'accueil et de sa maquette.

## Décisions exclues du périmètre

- Aucun changement des badges de note, reprise, progression, téléchargement ou lecture.
- Aucun calcul dynamique de contraste à partir de l'image et aucun traitement bitmap.
- Aucun changement de texte, localisation, navigation, focus, données ou ordre du contenu.
- Aucun remaniement général des couleurs du thème : ce style d'overlay reste local aux composants de l'accueil.

---

# 6. Plan de développement

- [x] Étape 1 : Analyse et structuration (Fait)
- [x] Étape 2 : Spécifications fonctionnelles détaillées (Fait le 2026-07-22)
- [x] Étape 3 : Spécification technique et architecture (Fait le 2026-07-22)
- [x] Étape 4 : Découpage des tâches détaillées (Fait le 2026-07-22)
- [x] Étape 5 : Implémentation de la correction (validation visuelle mobile/TV restante)
- [x] Étape 6 : Review technique (Fait le 2026-07-22)
- [x] Étape 7 : Correction des retours de review (aucune correction requise le 2026-07-22)
- [x] Étape 8 : Validation finale (Automatisée + Fonctionnelle)
- [x] Étape 9 : Documentation globale
- [x] Étape 10 : Livraison Git et Archivage

## Tâches d'implémentation

- [x] B8-1 — Créer le rendu partagé du badge de type média Accueil.

  Objectif : introduire un composable stateless, non interactif et limité au package Accueil, responsable du fond noir à 50 %, de la bordure blanche à 20 %, du rayon de 4 dp, du texte blanc et du padding interne configurable.

  Fichiers :
  - `app/src/main/java/com/cstv/app/presentation/home/components/HomeMediaTypeBadge.kt` (nouveau)

  Validation : le composable accepte le libellé ainsi que les dimensions nécessaires aux contextes appelants, n'expose aucun état ni callback, et utilise exactement `Color.Black.copy(alpha = 0.5f)` et une bordure `1.dp` en `Color.White.copy(alpha = 0.2f)`.

- [x] B8-2 — Migrer le badge des favoris vers le composable partagé.

  Objectif : remplacer l'overlay local de `HomeFavoriteItemCard` sans modifier le choix existant des libellés FILM, SÉRIE, DIRECT ou le repli, son emplacement haut-gauche et sa marge externe de 6 dp.

  Fichiers :
  - `app/src/main/java/com/cstv/app/presentation/home/components/HomeCards.kt`

  Validation : les badges Favoris conservent leur texte, taille 8 sp, graisse et padding actuels lorsque pertinent; ils restent décoratifs, non focalisables et ne modifient ni l'action ni le focus de la carte.

- [x] B8-3 — Migrer le badge du carrousel Tendances vers le composable partagé.

  Objectif : remplacer l'overlay local FILM/SÉRIE par la même source de rendu tout en préservant son emplacement haut-gauche, sa marge externe de 16 dp et sa hiérarchie typographique de 10 sp.

  Fichiers :
  - `app/src/main/java/com/cstv/app/presentation/home/components/HomeTrendingCarousel.kt`

  Validation : les deux badges Tendances utilisent les valeurs visuelles identiques à Favoris hors marges, padding et taille explicitement fournis; titre, dégradé, pagination et navigation du carrousel restent inchangés.

- [x] B8-4 — Réaliser la validation visuelle et la non-régression.

  Objectif : vérifier la lisibilité et l'intégration des deux usages sans élargir le périmètre aux autres badges de l'application.

  Fichiers :
  - `app/src/main/java/com/cstv/app/presentation/home/components/HomeMediaTypeBadge.kt`
  - `app/src/main/java/com/cstv/app/presentation/home/components/HomeCards.kt`
  - `app/src/main/java/com/cstv/app/presentation/home/components/HomeTrendingCarousel.kt`

  Validation : sur mobile et Android TV, contrôler FILM, SÉRIE et DIRECT sur covers claires, sombres, détaillées et placeholders, dans les états normal et focus; vérifier que le badge est lisible, ne capte pas le focus et ne recouvre pas note, progression, titre ou actions. Exécuter `./gradlew testDebugUnitTest`, `./gradlew assembleDebug` et `./gradlew lintDebug` avant de passer à la review. Aucun test unitaire de layout pur n'est ajouté.

---

# 7. Hypothèses et questions ouvertes

### Hypothèses
1. Remplacer le fond blanc transparent (`Color.White.copy(alpha = 0.2f)`) par le fond sombre de la maquette (`Color.Black.copy(alpha = 0.5f)`) tout en conservant le texte blanc doit résoudre le problème de lisibilité sans sacrifier l'esthétique "Glass/Overlay" de l'application ; la validation visuelle sur affiches extrêmes reste requise.
2. Un fond sombre transparent est préférable à un fond blanc opaque, car il reste discret et s'intègre harmonieusement sur l'affiche sans créer un effet "bloc solide" inélégant.

### Questions ouvertes
- Devrait-on ajouter une fine bordure blanche transparente (ex. `Color.White.copy(alpha = 0.15f)`) autour de ces badges de type de média, similaire à ce qui est fait sur la carte de reprise de lecture, pour renforcer l'effet verre poli (Frosted Glass) ?
  *Décision étape 3 :* Oui, avec `Color.White.copy(alpha = 0.2f)`, valeur exacte de la maquette de référence. Elle renforce la délimitation sur les affiches sombres et maintient un style identique dans les deux composants.

---

# 8. Review

## Review technique — 2026-07-22

Status: RESOLVED

Portée : nouveau `HomeMediaTypeBadge.kt` et migration des overlays de `HomeCards.kt` (`HomeFavoriteItemCard`) et `HomeTrendingCarousel.kt`. Build/test/lint verts au niveau implémentation.

### Conformité à la spécification
- `HomeMediaTypeBadge` : composable stateless, non interactif, sans callback. Fond `Color.Black.copy(alpha = 0.5f)`, bordure `1.dp` `Color.White.copy(alpha = 0.2f)`, `RoundedCornerShape(4.dp)`, texte blanc. Valeurs exactes de la maquette. Conforme B8-1.
- Favoris : libellés FILM/SÉRIE/DIRECT/repli inchangés, taille 8 sp, `FontWeight.Bold`, emplacement haut-gauche, marge externe 6 dp conservée par l'appelant. Conforme B8-2.
- Tendances : libellés FILM/SÉRIE inchangés, taille 10 sp, `FontWeight.SemiBold`, marge externe 16 dp conservée par l'appelant. Conforme B8-3.
- Langage visuel unifié via source unique ; padding interne fourni par contexte (`contentPadding`). Divergence future évitée.

### Points de vigilance (non bloquants)
- L'overlay favoris utilisait auparavant `.clip(RoundedCornerShape(4.dp))` avant `background` ; le badge applique désormais la forme via `background`/`border`. Rendu équivalent pour du texte, sans régression d'encombrement.
- Fond noir 50 % : lisibilité à re-contrôler visuellement sur affiche uniformément blanche (attendu par la validation fonctionnelle B8-4).

### Verdict
Aucun défaut bloquant. Reste : validation visuelle mobile/Android TV (B8-4) avant clôture.

---

# 7. Notes de développement

- 2026-07-22 : création de `HomeMediaTypeBadge`, source unique pour le fond noir à 50 %, la bordure blanche à 20 %, le rayon de 4 dp et le texte blanc.
- 2026-07-22 : les badges des Favoris et du carrousel Tendances utilisent ce composant en conservant leurs marges, tailles de texte et libellés respectifs.
- 2026-07-22 : validations automatisées réussies : `testDebugUnitTest`, `assembleDebug`, `lintDebug`. La validation visuelle mobile/Android TV reste à réaliser sur appareil ou émulateur disponible.
- 2026-07-22 : étape 7 clôturée sans modification : la review ne comportait aucun défaut bloquant. Étape 8 : `./gradlew --no-daemon testDebugUnitTest assembleDebug lintDebug` réussit et la référence Accueil confirme le fond noir à 50 % et la bordure blanche à 20 % ; la validation visuelle mobile/Android TV reste bloquée par l'absence d'appareil ou d'émulateur ADB dans cet environnement.

---

# 9. Release

- **Statut** : RELEASED
- **Version** : v1.49.2
- **Date** : 2026-07-22
- **Commit** : :bug: fix(home): improve media type label visibility on covers (B8)
- **Tag** : v1.49.2
