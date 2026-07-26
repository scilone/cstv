# B14 - Échec de l'application de la tolérance d'année lors du rapprochement TMDB (Remakes/Homonymes)

## Informations générales

Type:
Bug

Status:
SPECIFICATION

Created:
2026-07-26

Target version:
v1.56.0

---

# 1. Description

Certains utilisateurs signalent qu'en parcourant les sections basées sur les données TMDB (Tendances et Top 10 populaires de l'Accueil), le rapprochement (matching) avec le catalogue local IPTV produit encore des faux positifs.

Des films ou des séries de type remake ou possédant des titres identiques (homonymes) mais sortis à des époques différentes (ex. Dune de 1984 vs Dune de 2021, ou un film classique et son remake moderne) sont associés de manière erronée. L'utilisateur clique sur l'affiche TMDB d'une œuvre récente et se retrouve à visionner une version beaucoup plus ancienne (ou inversement), ce qui indique que la tolérance de +/- 1 an sur l'année de sortie n'est pas correctement respectée ou appliquée dans l'algorithme de matching.

---

# 2. Contexte

Afin de lier les tendances et les populaires TMDB au catalogue local de l'utilisateur, l'application s'appuie sur un composant commun nommé `TmdbCatalogMatcher` (introduit initialement par le ticket B6). Cet objet pur compare la similarité textuelle des titres normalisés (`>= 0.8`) et doit valider que la différence entre l'année de sortie TMDB (`tmdbYear`) et l'année de sortie IPTV (`iptvYear`) n'excède pas +/- 1 an.

La méthode clé `isYearCompatible` est définie ainsi :
```kotlin
private fun isYearCompatible(tmdbYear: Int?, iptvYear: Int?): Boolean =
    tmdbYear == null || iptvYear == null || iptvYear <= 0 ||
        abs(tmdbYear.toLong() - iptvYear.toLong()) <= 1L
```

Si le matching applique théoriquement cette règle, les retours terrain démontrent que des faux positifs persistent. Plusieurs scénarios peuvent expliquer ce dysfonctionnement :
1. **Échec de parsing de l'année IPTV :** Si l'année locale n'est pas récupérée ou est mal parsée par `ReleaseYearParser`, elle est considérée comme nulle ou égale à 0. Dans ce cas, la validation d'année est totalement ignorée (fallback), et le matcher s'appuie uniquement sur la similarité textuelle (`>= 0.8`), ce qui associe indûment des homonymes d'époques éloignées.
2. **Échec de parsing de l'année TMDB :** Si la date de sortie envoyée par l'API TMDB n'est pas correctement convertie en entier, `tmdbYear` devient `null` et le contrôle de l'année est également contourné.
3. **Erreur d'intégration ou d'alimentation :** Lors de la préparation des candidats dans les cas d'utilisation (`GetTrendingInCatalogUseCase` ou `GetPopularTop10InCatalogUseCase`), il se peut que les champs d'années des flux locaux ne soient pas correctement propagés ou convertis.

---

# 3. Objectif

Ce ticket vise à investiguer et à corriger les failles de l'algorithme de matching d'années dans `TmdbCatalogMatcher` et ses services dépendants pour garantir un blocage strict des correspondances d'œuvres homonymes ou de remakes si leurs années de sortie respectives sont connues et diffèrent de plus d'un an.

L'objectif de cette Étape 1 est de poser les bases de l'analyse et d'identifier toutes les hypothèses de défaillance.

---

# 4. Hypothèses

- **Hypothèse 1 (Parsing de l'année IPTV) :** Le parser `ReleaseYearParser` ne parvient pas à extraire l'année de certains formats de chaînes de caractères de `releasedate` renvoyés par des panels IPTV spécifiques (par exemple, des formats contenant des fuseaux horaires ou des caractères spéciaux non gérés par la regex `(?:19|20)\d{2}`), ce qui renvoie un `releaseYear` nul et désactive la vérification d'année.
- **Hypothèse 2 (Extraction de l'année TMDB) :** Les dates de sortie renvoyées par TMDB (ex. `release_date` pour les films, `first_air_date` pour les séries) pour certains médias populaires ou futurs ne respectent pas le format attendu ou sont manquantes dans la réponse de l'API lors de l'appel aux tendances, forçant le matcher à ignorer le contrôle d'année.
- **Hypothèse 3 (Incohérence ou valeur sentinelle par défaut) :** L'année du catalogue IPTV local (`releaseYear`) est par défaut à `0` ou `null` pour de nombreux streams car la synchronisation initiale n'a pas encore enrichi les métadonnées de tous les médias, ouvrant grand la porte au matching par simple ressemblance de titre.
- **Hypothèse 4 (Cache persistant obsolète) :** Le cache persistant global des tendances ou des populaires n'invalide pas ou ne régénère pas correctement les matchings erronés lorsque les métadonnées IPTV locales (l'année de sortie notamment) sont enrichies ultérieurement par une synchronisation en arrière-plan.

---

# 5. Questions ouvertes

1. **Formats de date réels :** Quels sont les formats exacts de `releasedate` présents dans la base de données IPTV de l'utilisateur pour les titres qui créent des faux positifs ? Faut-il enrichir `ReleaseYearParser` pour qu'il soit encore plus résilient ?
2. **Couverture des tests de matching :** Les tests de `TmdbCatalogMatcherTest` couvrent-ils bien des cas où une des deux années est nulle mais où le titre est identique ? Avons-nous des tests spécifiques simulant la présence simultanée de "Dune (1984)" et "Dune (2021)" dans le catalogue ?
3. **Statut de l'enrichissement des métadonnées locales :** Les endpoints de détails des flux IPTV (`getVodInfo` et `getSeriesInfo`) sont-ils appelés de manière proactive pour renseigner l'année de sortie des films/séries locaux, ou l'année n'est-elle disponible que de manière sporadique ?
4. **Log de matching :** Devons-nous ajouter des traces de débogage plus verbeuses dans `TmdbCatalogMatcher` pour enregistrer précisément pourquoi un candidat a été accepté ou rejeté (ex : `"Match refusé pour Dune: TMDB=2021, IPTV=1984, écart=37"`) afin de faciliter l'analyse en production ?

---

# 6. Spécification fonctionnelle

## User stories

- En tant qu'utilisateur de l'Accueil, je veux qu'un titre TMDB soit relié à la bonne œuvre de mon catalogue IPTV afin de ne pas ouvrir un remake ou un homonyme d'une autre époque.
- En tant qu'utilisateur, je veux conserver les recommandations dont l'année n'est pas connue afin que l'absence de métadonnée ne masque pas inutilement du contenu potentiellement pertinent.

## Comportement attendu

Le rapprochement utilisé par les sections Accueil alimentées par TMDB (Tendances et Top 10 populaires) doit comparer l'année TMDB et l'année du candidat IPTV lorsque les deux sont connues et valides.

- Un candidat reste éligible lorsque l'écart absolu entre les deux années est inférieur ou égal à un an.
- Un candidat est exclu lorsque cet écart est supérieur à un an, même si son titre normalisé atteint le seuil de similarité textuelle.
- L'exclusion s'applique avant le choix du meilleur candidat : un homonyme plus ancien ou plus récent ne doit pas être retenu à la place d'un candidat compatible.
- Si l'une des deux années est absente, invalide ou inconnue, le rapprochement conserve son comportement de repli actuel fondé sur le titre ; ce ticket ne doit pas supprimer ces recommandations uniquement faute de date.
- La règle s'applique de manière identique aux films et aux séries, sans modifier le seuil de similarité des titres ni les autres règles de sélection existantes.

## Parcours utilisateur

1. L'utilisateur ouvre l'Accueil alors que les listes Tendances ou Top 10 sont affichées.
2. L'application rapproche chaque entrée TMDB avec les médias locaux de même nature.
3. Pour deux titres similaires dont les années sont connues, l'application écarte les candidats distants de plus d'un an.
4. Un appui sur l'affiche d'un titre récent ouvre donc le média local compatible, et jamais un remake ou homonyme à l'année incompatible.

## Règles métier et cas limites

- Les années positives sont les seules considérées comme connues ; `null`, `0` ou une valeur invalide suivent le comportement de repli.
- La limite est inclusive : 2021 est compatible avec 2020 et 2022, mais pas avec 2019 ou 2023.
- Un titre TMDB sans date ou un média IPTV sans année ne doit pas provoquer d'erreur, de crash ni faire disparaître la liste ; le rapprochement textuel existant reste applicable.
- Si tous les homonymes sont exclus par l'année, l'entrée TMDB n'est pas associée à un média local incompatible.
- Le périmètre est limité au rapprochement du catalogue local pour les listes TMDB concernées ; il ne modifie ni les détails des médias, ni les favoris, ni la recherche.

## Critères d'acceptation

- [ ] Avec « Dune » TMDB (2021) et deux candidats locaux « Dune » (1984 et 2021), seul le candidat 2021 peut être sélectionné.
- [ ] Un candidat local daté 2020 ou 2022 reste sélectionnable pour une entrée TMDB datée 2021 si son score de titre est suffisant.
- [ ] Un candidat local daté 2019 ou 2023 est exclu pour une entrée TMDB datée 2021, même avec un titre identique.
- [ ] Un rapprochement dont l'une des années est inconnue reste fonctionnel selon les règles textuelles existantes et ne provoque aucune erreur visible.
- [ ] La même règle est vérifiée pour au moins un film et une série dans Tendances et Top 10 populaires.
