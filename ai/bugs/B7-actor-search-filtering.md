# B7 - Le filtrage de recherche depuis un acteur ne fonctionne pas correctement

## Informations générales

Type:
Bug

Status:
SPECIFICATION

Created:
2026-07-22

---

# 1. Description

Le filtrage de recherche est inefficace ou buggé lorsqu'on lance une recherche en cliquant sur le nom d'un acteur (ou réalisateur) depuis la fiche de détails d'un film ou d'une série. La recherche n'affiche pas les résultats attendus, ou affiche un écran vide.

---

# 2. Contexte

Le problème provient de plusieurs facteurs cumulés dans l'implémentation de la recherche locale et de sa navigation :

1. **Absence de recherche sur les crédits en recherche avancée :**
   Lorsque des filtres de recherche avancée sont actifs (même si un seul filtre de type de média "Films" ou "Séries" est activé), l'application utilise `AdvancedCatalogSearchUseCase` pour filtrer le catalogue complet en mémoire. Or, ce use case applique le filtre textuel uniquement sur le **nom** du média (`it.name.contains(query, ignoreCase = true)`) et ignore totalement les champs `actors`, `director` ou `genre`.

2. **Conservation des filtres précédents lors d'un clic crédit :**
   Lorsque l'utilisateur clique sur un acteur depuis la fiche détails, le callback `onSearchQueryTriggered` met à jour la requête de recherche dans `FavoritesViewModel` et navigue vers l'écran de recherche, mais **conserve les filtres avancés précédemment actifs**. Si un filtre restrictif ou incompatible est resté actif (ex. filtre sur une autre catégorie ou type de média "Séries" alors que l'acteur n'a joué que dans des films), l'utilisateur obtient zéro résultat.

3. **Perte de données lors du mapping dans `searchUnified` :**
   Dans `FavoritesRepositoryImpl.searchUnified`, les résultats retournés par le DAO FTS4 (`searchVodStreams` / `searchSeriesStreams`) sont mappés vers les modèles de domaine `VodStream` et `SeriesStream` en omettant de passer les propriétés `actors` et `director`. Bien que la recherche SQL MATCH fonctionne en base, les objets de domaine produits perdent leurs métadonnées d'acteurs et de réalisateur, ce qui peut fausser les traitements ou l'affichage ultérieurs.

---

# 3. Spécification fonctionnelle

## User stories

- En tant qu'utilisateur, je peux toucher ou sélectionner le nom d'un acteur ou d'un réalisateur depuis la fiche d'un film ou d'une série pour consulter immédiatement les films et séries de son catalogue local auxquels il est associé.
- En tant qu'utilisateur, j'obtiens les mêmes résultats pertinents pour une recherche par titre, acteur, réalisateur ou genre, que j'utilise la recherche standard ou que j'aie activé un ou plusieurs filtres avancés.
- En tant qu'utilisateur, une recherche lancée depuis un crédit n'est pas limitée par les filtres que j'avais laissés lors d'une précédente recherche avancée.

## Parcours utilisateur

1. Depuis la fiche détaillée d'un film ou d'une série, l'utilisateur sélectionne un nom individuel dans la ligne « Acteurs » ou « Réalisateur ».
2. L'application ouvre l'écran Recherche avec ce nom comme requête visible.
3. Tous les filtres avancés précédemment sélectionnés sont remis à leur état initial avant le calcul des résultats ; aucun chip de filtre ne reste actif.
4. La recherche s'exécute sur l'ensemble du catalogue local visible et affiche les sections Films et Séries contenant cette personne.
5. L'utilisateur peut ensuite appliquer volontairement de nouveaux filtres avancés, modifier la requête ou ouvrir un résultat, selon le comportement habituel de l'écran Recherche.

## Règles métier

- Une correspondance textuelle est valide lorsqu'elle est trouvée, sans distinction de casse, dans au moins l'un des champs suivants du média : titre, acteurs, réalisateur ou genre.
- Cette règle s'applique de façon identique aux films et aux séries, et reste valable lorsqu'un filtre avancé de type, catégorie, note, année ou genre est actif.
- Les filtres avancés continuent de s'appliquer cumulativement aux résultats d'une recherche explicitement filtrée. Ils ne sont réinitialisés automatiquement que pour une navigation déclenchée par un crédit depuis une fiche de détails.
- La saisie ou la modification manuelle d'une requête dans l'écran Recherche ne réinitialise pas les filtres avancés : l'utilisateur conserve le contrôle explicite de ses choix.
- Les catégories masquées par le profil restent masquées ; une recherche par crédit ne doit pas révéler de contenu que le profil a choisi de cacher.
- Une recherche par crédit couvre les contenus VOD et Séries disponibles localement. La recherche Live TV ne fait pas partie de ce parcours, car les crédits ne sont pas portés par les chaînes en direct.

## Critères d'acceptation

- Depuis une fiche Film, cliquer un acteur ou un réalisateur ouvre Recherche avec le nom sélectionné et affiche tous les films et séries correspondants du catalogue visible.
- Depuis une fiche Série, le même comportement est obtenu pour un acteur comme pour un réalisateur.
- Si un filtre avancé était actif avant le clic (type, catégorie, note, année ou genre), il est absent après l'arrivée dans Recherche et ne peut plus exclure des résultats du crédit sélectionné.
- Avec un filtre avancé choisi volontairement, une requête correspondant uniquement au champ acteurs, réalisateur ou genre renvoie les médias correspondants, au même titre qu'une requête correspondant au titre.
- La recherche est insensible à la casse et fonctionne avec les noms comportant espaces, accents, apostrophes ou plusieurs mots.
- Les résultats conservent les informations nécessaires à leur affichage habituel, notamment les crédits disponibles, quelle que soit la voie de recherche utilisée.

## Cas limites et gestion des erreurs

- Si le crédit est absent, vide, inconnu ou ne désigne pas un nom sélectionnable, aucune navigation de recherche ne doit être déclenchée.
- Si aucun média visible ne correspond au nom sélectionné, Recherche affiche son état vide habituel ; elle ne doit ni afficher une erreur technique ni réutiliser des résultats d'une recherche précédente.
- Si le catalogue local est temporairement indisponible ou qu'une erreur de recherche survient, l'écran reste stable, cesse son état de chargement et présente l'état vide ou d'erreur utilisateur déjà défini par Recherche, sans détail technique brut.
- Les résultats ne doivent pas être dupliqués lorsqu'une personne correspond à plusieurs champs d'un même média (par exemple acteur et réalisateur).
- Les crédits contenant plusieurs personnes doivent permettre une recherche sur le seul nom sélectionné, et non sur la chaîne complète des crédits.

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
1. La recherche avancée doit appliquer la recherche textuelle de manière large (titre OR acteurs OR réalisateur OR genre) tout comme le fait la recherche unifiée FTS4, pour assurer une expérience utilisateur uniforme et prévisible.
2. La réinitialisation automatique des filtres avancés lors d'une recherche initiée depuis les fiches de détails est essentielle pour éviter les situations de "faux positif vide" (résultats vides causés par un filtre oublié).

### Questions ouvertes
- Doit-on également réinitialiser les filtres de recherche avancée si l'utilisateur saisit manuellement une nouvelle requête dans la barre de recherche standard de l'écran de recherche ?
  *Réponse proposée :* Non. Sur l'écran de recherche, l'utilisateur gère activement ses filtres et voit les chips de filtres actifs. En revanche, lors d'un clic de redirection depuis un autre écran (Détails), il n'a pas conscience de l'état précédent des filtres de recherche, d'où la nécessité de réinitialiser dans ce cas spécifique.

---

# 8. Review

*(À remplir lors des étapes ultérieures)*

---

# 9. Release

*(À remplir lors de la livraison)*
