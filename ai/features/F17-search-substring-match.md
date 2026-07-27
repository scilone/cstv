# F17 - Recherche globale par sous-chaîne (LIKE "%keyword%")

## Informations générales

Type:
Feature

Status:
TASK BREAKDOWN

Created:
2026-07-27

---

# 1. Description

Actuellement, la recherche globale unifiée de l'application s'appuie sur des tables de recherche en texte intégral SQLite FTS4 (`live_streams_fts`, `vod_streams_fts`, `series_streams_fts`). Ce mécanisme construit une requête de correspondance de mots par préfixes (par exemple, `"marsu"*` si l'utilisateur saisit "marsu").

Cependant, ce fonctionnement se révèle trop restrictif dans l'usage quotidien :
1. **Défaut de tolérance de saisie :** Si l'utilisateur saisit un terme partiel qui ne correspond pas au début d'un mot indexé par FTS4, aucun résultat n'est retourné. Par exemple, si un média s'appelle "Le Marsupilami" ou simplement "Marsupilami", et que l'utilisateur saisit "marsu", le comportement de préfixe FTS4 de base peut échouer ou s'avérer trop strict selon le formatage des données. De même, si on recherche "pilami", FTS4 ne retournera aucun résultat car "pilami" est au milieu du mot "marsupilami" et FTS ne supporte pas les jokers de début (`*pilami`).
2. **Attente utilisateur moderne :** Sur les applications de streaming (IPTV, VOD, etc.), l'utilisateur s'attend à ce que la saisie d'un fragment d'un titre, d'un acteur ou d'un réalisateur retourne immédiatement tous les résultats correspondants, quel que soit l'emplacement de cette sous-chaîne dans la valeur.

L'évolution consiste à faire évoluer le moteur de recherche locale afin de remplacer ou d'enrichir le mécanisme actuel par une correspondance par sous-chaîne arbitraire (type `LIKE '%keyword%'`), assurant que n'importe quel morceau de texte recherché trouve correctement les chaînes, films ou séries correspondants.

---

# 2. Contexte

La recherche unifiée est orchestrée par le use case `SearchUnifiedUseCase` qui appelle la méthode `searchUnified(query)` du `FavoritesRepository` (implémentée par `FavoritesRepositoryImpl`). Celle-ci délègue les requêtes de recherche au DAO `FavoritesDao` :

- `favoritesDao.searchLiveStreams(matchQuery)`
- `favoritesDao.searchVodStreams(matchQuery)`
- `favoritesDao.searchSeriesStreams(matchQuery)`

Ces méthodes s'appuient sur des tables FTS4 :
- `live_streams_fts`
- `vod_streams_fts`
- `series_streams_fts`

Qui font des jointures sur les tables physiques contenant les vraies données :
- `live_streams`
- `vod_streams`
- `series_streams`

La méthode `buildFtsMatchQuery` dans `FavoritesRepositoryImpl` transforme la saisie de l'utilisateur en une requête FTS4 de préfixes (chaque mot devient `"mot"*`).
L'utilisation de FTS4 avait été introduite à la Phase 40 pour remplacer les requêtes `LIKE '%x%'` afin d'éviter des scans complets de tables (`full scan`) à chaque frappe sur de gros volumes de données locaux. Mais cela a introduit une régression d'usage sur la flexibilité de la recherche locale (impossibilité de rechercher par milieu de mot ou sous-chaîne).

---

# 3. Spécification fonctionnelle

## User stories

- En tant qu'utilisateur, je peux saisir un fragment placé au début, au milieu ou à la fin d'un libellé afin de retrouver le média correspondant sans connaître le début d'un mot.
- En tant qu'utilisateur, je peux rechercher un film ou une série par une portion du nom d'un acteur ou d'un réalisateur.
- En tant qu'utilisateur, je conserve une recherche unifiée : les chaînes, films et séries correspondant à ma saisie sont présentés dans leurs sections existantes.

## Parcours utilisateur

1. L'utilisateur ouvre la recherche globale et saisit ou modifie sa requête.
2. Après le délai de saisie déjà appliqué par l'écran, la recherche locale est actualisée avec la requête courante.
3. Chaque section existante affiche les éléments qui correspondent ; une section sans résultat ne présente aucun élément.
4. L'utilisateur peut ouvrir un résultat exactement comme aujourd'hui. Les filtres de catégories déjà appliqués à la recherche restent applicables.

## Règles métier

- La correspondance est insensible à la casse. Un fragment est une suite de caractères contigus : `marsu`, `pilami` et `lami` doivent tous trouver `Marsupilami`.
- La correspondance est **insensible aux accents et signes diacritiques**, dans les deux sens : `odysee`, `ODYSEE`, `Odysée` et `odysée` trouvent tous `Odysée`, et `rené` trouve `Rene`. Le repli couvre les diacritiques latins (accents, cédille, tréma) ainsi que les ligatures et lettres barrées courantes (`œ`→`oe`, `æ`→`ae`, `ß`→`ss`, `ø`→`o`).
- La recherche porte sur les données déjà disponibles dans le catalogue local :
  - chaînes : nom de la chaîne et catégorie ;
  - films et séries : titre, acteurs, réalisateur, genre et catégorie.
- Pour une requête d'un mot, un élément est retenu si ce mot est présent comme sous-chaîne dans au moins un de ses champs recherchables.
- Une requête contenant plusieurs mots est découpée sur les espaces. Un élément est retenu seulement si chaque mot non vide est trouvé, quel que soit l'ordre des mots et même s'ils sont présents dans des champs différents du même élément. Ainsi `marsu ami` trouve `Le Marsupilami`.
- Les espaces en début, fin ou en répétition ne modifient pas le résultat. Une requête vide ou composée uniquement d'espaces ne lance pas de recherche et affiche l'état vide habituel.
- Les caractères `%`, `_`, `\` et les autres caractères saisis par l'utilisateur sont recherchés littéralement ; ils ne doivent jamais élargir la recherche ni provoquer une erreur.
- Les résultats déjà exclus par les préférences de catégories restent exclus. Cette évolution ne modifie ni l'ordre de présentation existant, ni les favoris, ni les données du catalogue.

## Critères d'acceptation

- La recherche `pilami` retourne un film ou une série nommé `Marsupilami`.
- La recherche `marsu` retourne un résultat dont le nom contient `Marsupilami`, indépendamment de la casse de la saisie.
- Pour un élément nommé `Odysée`, les saisies `Ody`, `ody`, `odysee`, `ODYSEE`, `odysée` et `dysee` le retournent toutes.
- La recherche `rene` retourne un élément nommé `René`, et la recherche `rené` retourne un élément nommé `Rene`.
- La recherche d'un fragment d'acteur ou de réalisateur retourne les films et séries concernés lorsque cette information est disponible localement.
- La recherche multi-mots `jean reno` retourne un élément dont les deux fragments sont présents dans ses champs recherchables, quel que soit leur ordre dans la requête ou les données.
- Une chaîne, un film ou une série ne contenant pas tous les mots de la requête ne figure pas dans les résultats.
- Une requête vide, ou ne trouvant aucun élément, ne provoque ni erreur affichée ni résultat obsolète.
- Une saisie contenant `%`, `_` ou `\` ne provoque pas d'erreur et ne les interprète pas comme des jokers.
- La recherche reste locale : elle n'entraîne pas d'appel réseau ni de synchronisation du catalogue.

## Cas limites et gestion des erreurs

- Les champs facultatifs absents (acteurs, réalisateur ou genre) sont simplement ignorés pour l'élément concerné ; ils ne font pas échouer la recherche sur les autres champs.
- Si le catalogue local est vide ou indisponible pendant son initialisation, l'écran conserve son état de chargement ou d'absence de résultat existant, sans message technique ni crash.
- Les recherches successives ne doivent jamais afficher les résultats d'une ancienne requête à la place de la saisie la plus récente.
- L'équivalence accentué / non accentué est incluse dans F17 (voir règles métier). En revanche, la translittération entre alphabets (cyrillique, arabe, grec vers latin) reste hors périmètre : `Москва` ne se recherche pas par `moskva`.
- Sur une base installée avant F17, le repli d'accents n'est complet qu'après la première synchronisation du catalogue suivant la mise à jour (voir §4.4) ; entre-temps, il couvre les diacritiques latins courants repliés par la migration.

---

# 4. Spécification technique

## 4.1 Décisions techniques

| # | Décision | Justification |
|---|----------|---------------|
| D1 | **Suppression totale de FTS4** (tables virtuelles, entités Room, méthodes DAO de synchronisation) | FTS4 ne sait pas répondre à `%pilami%` (pas de joker de début). Le conserver signifierait maintenir une double écriture (table physique + table virtuelle + tables fantômes `_content`/`_segdir`/`_segments`/`_docsize`/`_stat`) pour un index devenu inutilisé : coût de sync catalogue, taille de base, et risque d'incohérence entre table réelle et index. |
| D2 | **Colonne dénormalisée `searchText`** (TEXT NOT NULL, défaut `''`) sur `live_streams`, `vod_streams`, `series_streams`, contenant la concaténation des champs recherchables **normalisée** (minuscules + diacritiques repliés) | Seule façon d'obtenir à la fois l'insensibilité à la casse et aux accents avec `LIKE`. SQLite ne replie la casse **que sur l'ASCII** (`É` ≠ `é`) et ne sait pas replier les diacritiques : chercher sur les colonnes brutes ferait échouer `odysee` sur `Odysée` **et** `ODYSEE` sur `Odysée`. La normalisation est faite en Kotlin à l'écriture et à la lecture, donc les deux opérandes du `LIKE` sont déjà normalisées et les limites de SQLite deviennent sans effet. Bonus : une seule colonne à interroger au lieu de 5 prédicats `OR` par ligne. |
| D2bis | **Normalisation = `lowercase()` + `Normalizer.normalize(NFD)` + suppression de `\p{Mn}` + table de ligatures** (`œ`→`oe`, `æ`→`ae`, `ß`→`ss`, `ø`→`o`, `ł`→`l`, `đ`→`d`) | `java.text.Normalizer` est disponible depuis l'API 9 (projet en minSdk 21), sans dépendance ajoutée. NFD décompose `é` en `e` + U+0301, que le strip des marques non espaçantes (`\p{Mn}`) élimine. Les ligatures et lettres barrées ne sont **pas** des caractères précomposés : NFD ne les décompose pas, d'où la table explicite. Appliquée symétriquement des deux côtés, la relation reste réflexive : `rene` trouve `René` et `rené` trouve `Rene`. |
| D3 | **Filtrage en deux temps** : le token **le plus long** de la requête filtre en SQL (`LIKE :pattern ESCAPE '\'`), les tokens restants sont vérifiés en Kotlin sur `searchText` | Garde une `@Query` Room **statique** (validée à la compilation, testable en JVM via mock DAO) tout en supportant un nombre arbitraire de mots. Le token le plus long est le plus sélectif : SQLite réduit le jeu de lignes en C, et le `AND` résiduel s'applique sur un petit sous-ensemble. |
| D4 | **Objet pur `LocalSearchQuery`** dans `domain/model/` (tokenisation, échappement `LIKE`, choix du token ancre, prédicat de correspondance) | Conforme à `AGENTS.md` (« modèles métier + objets purs testables : parsers/matchers », cf. `GenreParser`, `TitleNormalizer`). Toute la logique de recherche devient testable en tests unitaires JVM, sans device ni Room. |
| D5 | **`searchText` recalculé dans le DAO**, jamais par les appelants | Les chemins d'enrichissement (`VodRepositoryImpl:103/501`, `SeriesRepositoryImpl:100/531`) insèrent des `entity.copy(actors = …, genre = …)` : si la colonne était calculée côté appelant, un `copy()` oublié laisserait un `searchText` périmé. La recalculer dans le wrapper `@Transaction insertStreams(...)` rend l'oubli impossible. |
| D6 | **Migration réelle 20 → 21** (`ALTER TABLE ADD COLUMN` + backfill SQL + `DROP TABLE` des tables FTS) | Règle impérative `AGENTS.md` : pas de `fallbackToDestructiveMigration()`. Le catalogue, les favoris et les positions de lecture survivent. |
| D7 | **Unification du chemin « recherche avancée »** sur le même `LocalSearchQuery` | `AdvancedCatalogSearchUseCase.matchesTextQuery` (lignes 124-133) traite aujourd'hui la saisie comme **une seule** sous-chaîne. Sans unification, `jean reno` donnerait des résultats différents selon qu'un filtre est actif ou non, pour la même saisie. |

## 4.2 Modèle de données

Trois colonnes ajoutées, aucune supprimée, aucune clé primaire touchée :

| Table | Colonne ajoutée | Contenu (normalisé : minuscules + sans diacritiques, champs joints par `\n`) |
|-------|-----------------|-----------------------------------------------|
| `live_streams` | `searchText TEXT NOT NULL DEFAULT ''` | `name`, `categoryId` |
| `vod_streams` | `searchText TEXT NOT NULL DEFAULT ''` | `name`, `actors`, `director`, `genre`, `categoryId` |
| `series_streams` | `searchText TEXT NOT NULL DEFAULT ''` | `name`, `actors`, `director`, `genre`, `categoryId` |

Champs facultatifs `null` → chaîne vide, donc simplement absents du blob (règle « champs absents ignorés »). Le séparateur `\n` empêche qu'un fragment chevauche deux champs (`…director\ngenre…` ne peut pas produire un faux positif sur une sous-chaîne à cheval).

Le périmètre des champs indexés est **strictement celui de FTS4 aujourd'hui**, `categoryId` compris : pas de régression, pas d'élargissement involontaire.

Trois tables virtuelles supprimées : `live_streams_fts`, `vod_streams_fts`, `series_streams_fts` (et leurs tables fantômes, supprimées automatiquement par `DROP TABLE` sur une table virtuelle FTS4).

Version `AppDatabase` : **20 → 21**.

## 4.3 Requêtes SQL

DAO `FavoritesDao`, une requête statique par type (exemple VOD ; live et séries identiques au nom de table près) :

```sql
SELECT * FROM vod_streams
WHERE searchText LIKE :pattern ESCAPE '\'
ORDER BY name ASC
```

`:pattern` est produit côté Kotlin : `%` + token **normalisé** (D2bis) puis échappé + `%`. Échappement (dans l'ordre, après normalisation) : `\` → `\\`, `%` → `\%`, `_` → `\_`. Combiné à `ESCAPE '\'`, cela rend `%`, `_` et `\` littéraux — critère d'acceptation « une saisie contenant `%`, `_` ou `\` ne provoque pas d'erreur et ne les interprète pas comme des jokers ».

Ordre impératif : **normaliser puis échapper**. L'inverse insérerait des `\` d'échappement que NFD laisserait passer mais qui décaleraient la sémantique du motif.

La jointure `JOIN …_fts ON rowid = streamId` disparaît : lecture directe sur la table physique.

## 4.4 Migration 20 → 21

```kotlin
val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE live_streams ADD COLUMN searchText TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE vod_streams ADD COLUMN searchText TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE series_streams ADD COLUMN searchText TEXT NOT NULL DEFAULT ''")

        db.execSQL("UPDATE live_streams SET searchText = ${foldSql("name || char(10) || categoryId")}")
        db.execSQL("UPDATE vod_streams SET searchText = ${foldSql(VOD_CONCAT)}")
        db.execSQL("UPDATE series_streams SET searchText = ${foldSql(VOD_CONCAT)}")

        db.execSQL("DROP TABLE IF EXISTS live_streams_fts")
        db.execSQL("DROP TABLE IF EXISTS vod_streams_fts")
        db.execSQL("DROP TABLE IF EXISTS series_streams_fts")
    }
}

private const val VOD_CONCAT =
    "name || char(10) || ifnull(actors,'') || char(10) || ifnull(director,'') || " +
        "char(10) || ifnull(genre,'') || char(10) || categoryId"

// SQLite n'a ni NFD ni repli de diacritiques : le backfill empile des replace()
// sur la table de caractères latins courants. Source de vérité = la version
// Kotlin (LocalSearchQuery.normalize), réappliquée à chaque écriture ensuite.
private val FOLD_MAP = mapOf(
    "à" to "a", "á" to "a", "â" to "a", "ã" to "a", "ä" to "a", "å" to "a",
    "è" to "e", "é" to "e", "ê" to "e", "ë" to "e",
    "ì" to "i", "í" to "i", "î" to "i", "ï" to "i",
    "ò" to "o", "ó" to "o", "ô" to "o", "õ" to "o", "ö" to "o", "ø" to "o",
    "ù" to "u", "ú" to "u", "û" to "u", "ü" to "u",
    "ý" to "y", "ÿ" to "y", "ñ" to "n", "ç" to "c",
    "œ" to "oe", "æ" to "ae", "ß" to "ss", "ł" to "l", "đ" to "d"
)

// Les replace() s'appliquent AVANT lower() et couvrent les deux casses
// (lower() de SQLite étant ASCII-only, il ne saurait pas ramener 'É' à 'é').
// Une fois les diacritiques repliés, tout est ASCII et lower() suffit.
private fun foldSql(expr: String): String {
    val folded = FOLD_MAP.entries.fold(expr) { acc, (from, to) ->
        "replace(replace($acc, '$from', '$to'), '${from.uppercase()}', '$to')"
    }
    return "lower($folded)"
}
```

Aucune perte de données : les tables FTS ne sont qu'un index dérivé des tables physiques. `ALTER TABLE ADD COLUMN` suffit (pas de changement de clé primaire → pas besoin du pattern `<table>_new` de `MIGRATION_9_10`).

Ordre imposé : `replace()` d'abord (sur les deux casses), `lower()` ensuite. `lower()` de SQLite étant ASCII-only, l'appeler en premier laisserait `É` intact et le repli le manquerait. Après repli, le texte est ASCII et `lower()` fait son travail. Résultat attendu après migration : `searchText` ne contient que des minuscules ASCII pour tout caractère couvert par `FOLD_MAP`.

Limite connue et acceptée : la table SQL couvre le latin courant (français, espagnol, portugais, allemand, nordique), pas l'intégralité d'Unicode. Un caractère hors table (par ex. `ș` roumain, `ő` hongrois) reste non replié **jusqu'à la première synchronisation de catalogue**, qui réécrit `searchText` via la normalisation Kotlin exhaustive. Aucune alternative raisonnable : SQLite ne dispose pas de NFD, et parcourir toutes les lignes en Kotlin dans `migrate()` bloquerait le premier démarrage.

## 4.5 Performances

- `LIKE '%x%'` n'est pas indexable (joker de début) → scan séquentiel de la table. C'est exactement le compromis que la Phase 40 avait voulu éviter, réintroduit **volontairement** ici parce que la fonctionnalité l'exige (cf. contexte §2).
- Volumétrie : quelques milliers à quelques dizaines de milliers de lignes ; scan sur une colonne texte unique, exécuté en C par SQLite, ordre de grandeur : quelques millisecondes.
- La saisie est déjà protégée par le debounce existant (`FavoritesViewModel.performSearch` → `delay(SEARCH_DEBOUNCE_MILLIS)`, et `searchJob?.cancel()` avant chaque relance) : une seule requête part par pause de frappe, et le résultat d'une requête obsolète ne peut pas écraser le plus récent (critère « jamais afficher les résultats d'une ancienne requête »).
- Room exécute les `suspend fun` DAO hors du thread principal.
- Le scan porte sur **une** colonne au lieu de 5 prédicats `OR` : moins de travail par ligne que la variante « LIKE sur colonnes brutes ».
- Gain collatéral : la synchronisation de catalogue perd une écriture FTS par ligne (`upsert*Fts` était appelé item par item dans `insertStreamsWithFts`), remplacée par une concaténation de chaînes en mémoire.

## 4.6 Composants impactés

**Modifiés — couche `data`**

| Fichier | Changement |
|---------|-----------|
| `data/local/entity/LiveStreamEntity.kt` | + `val searchText: String = ""` |
| `data/local/entity/VodStreamEntity.kt` | + `val searchText: String = ""` |
| `data/local/entity/SeriesStreamEntity.kt` | + `val searchText: String = ""` |
| `data/local/db/AppDatabase.kt` | retrait des 3 entités FTS, `version = 21` |
| `data/local/db/Migrations.kt` | + `MIGRATION_20_21`, ajouté à `ALL_MIGRATIONS` |
| `data/local/dao/FavoritesDao.kt` | 3 `@Query` FTS `MATCH` → `LIKE … ESCAPE '\'` sur les tables physiques |
| `data/local/dao/LiveTvDao.kt` | suppression `upsertLiveFts` / `clearFtsByCategory` / `clearAllFts` ; `insertStreamsWithFts` → `insertStreams` (recalcule `searchText`), `replaceAllStreamsWithFts` → `replaceAllStreams`, `replaceStreamsByCategoryWithFts` → `replaceStreamsByCategory` |
| `data/local/dao/VodDao.kt` | idem (`upsertVodFts`) |
| `data/local/dao/SeriesDao.kt` | idem (`upsertSeriesFts`) |
| `data/repository/FavoritesRepositoryImpl.kt` | `buildFtsMatchQuery` supprimé ; `searchUnified` s'appuie sur `LocalSearchQuery` (requête SQL sur le token ancre + filtrage des tokens restants) |
| `data/repository/LiveTvRepositoryImpl.kt` | renommage des appels DAO (l. 124, 126) |
| `data/repository/VodRepositoryImpl.kt` | renommage des appels DAO (l. 103, 341, 343, 501) |
| `data/repository/SeriesRepositoryImpl.kt` | renommage des appels DAO (l. 100, 295, 297, 531) |

**Modifiés — couche `domain`**

| Fichier | Changement |
|---------|-----------|
| `domain/usecase/ClearCatalogCacheUseCase.kt` | retrait des 3 `clearAllFts()` (l. 38, 43, 47) |
| `domain/usecase/AdvancedCatalogSearchUseCase.kt` | `matchesTextQuery` (l. 124-133) remplacé par `LocalSearchQuery.matches(...)` — même sémantique multi-mots que la recherche unifiée (D7) |

**Nouveaux**

| Fichier | Rôle |
|---------|------|
| `domain/model/LocalSearchQuery.kt` | Objet pur : `parse(raw)` → tokens normalisés ; `escapeLike(token)` ; `likePattern` du token ancre ; `matches(searchText)` ; `buildSearchText(...)` par type de média |
| `app/src/test/java/.../domain/model/LocalSearchQueryTest.kt` | Tests unitaires du matcher |

**Supprimés**

`data/local/entity/LiveStreamFtsEntity.kt`, `VodStreamFtsEntity.kt`, `SeriesStreamFtsEntity.kt`.

**Tests existants à adapter**

`FavoritesRepositoryImplTest` (stubs de DAO : `MATCH` → `pattern` `LIKE`), `VodRepositoryImplTest` (l. 161, 401, 641, 662), `SeriesRepositoryImplTest` (l. 164, 314, 561) : renommage des méthodes DAO vérifiées. `CategoryFilteringUseCasesTest` inchangé (mocke `FavoritesRepository`, pas le DAO).

**Non impactés** : `SearchScreen.kt`, `AdvancedSearchSheet.kt`, `FavoritesViewModel` (hors comportement), navigation, favoris, préférences de catégories. Aucune dépendance Gradle ajoutée, aucune règle ProGuard (pas de nouvelle interface Retrofit).

## 4.7 Risques techniques

| Risque | Gravité | Mitigation |
|--------|---------|------------|
| `searchText` périmé après un `copy()` d'enrichissement (acteurs/genre récupérés via `get_vod_info`) | Élevée — résultats manquants et silencieux | D5 : recalcul systématique dans le wrapper DAO, jamais chez l'appelant. Test unitaire dédié : `insertStreams` d'une entité au `searchText` volontairement faux doit persister le blob recalculé. |
| Migration non testée automatiquement (pas d'infra `androidTest`, limite connue `AGENTS.md`) | Moyenne | Relecture manuelle du SQL contre le schéma des entités ; `ALTER TABLE ADD COLUMN` + `UPDATE` est le pattern le plus simple possible ; `DROP TABLE IF EXISTS` tolère l'absence des tables FTS. |
| Base existante non resynchronisée : repli d'accents SQL limité au latin courant | Faible | Documenté en §4.4 et §3 (cas limites) ; couvre le français et les langues latines voisines ; corrigé intégralement à la première sync de catalogue. |
| Divergence entre la normalisation Kotlin (NFD exhaustif) et la table SQL de la migration | Faible | Test unitaire de cohérence sur `FOLD_MAP` (§5.5). La divergence ne peut produire que des résultats manquants temporaires, jamais de faux positifs ni d'erreur. |
| Régression de latence sur très gros catalogue (> 100 k lignes) | Faible | Colonne unique + debounce existant ; si constaté, repli possible sur une limite `LIMIT` par section, hors périmètre F17. |
| Renommages DAO massifs cassant la compilation des tests | Faible | Purement mécanique, capté par `assembleDebug` + `testDebugUnitTest`. |

---

# 5. Architecture

## 5.1 Flux de données — recherche

```
SearchScreen
  └─ FavoritesViewModel.performSearch (debounce + cancel du job précédent)
       ├─ (filtre actif) AdvancedCatalogSearchUseCase ─┐
       └─ SearchUnifiedUseCase                          │  même prédicat
            └─ FavoritesRepository.searchUnified        │  LocalSearchQuery
                 ├─ LocalSearchQuery.parse(raw)  ───────┘
                 │     tokens (minuscules, non vides), token ancre = le plus long
                 ├─ FavoritesDao.searchLiveStreams(pattern)    → List<LiveStreamEntity>
                 ├─ FavoritesDao.searchVodStreams(pattern)     → List<VodStreamEntity>
                 ├─ FavoritesDao.searchSeriesStreams(pattern)  → List<SeriesStreamEntity>
                 ├─ filtre Kotlin : tous les tokens restants ⊂ entity.searchText
                 └─ mapping entity → modèle domain (inchangé)
                      └─ SearchUnifiedUseCase retire les catégories masquées (inchangé)
```

Requête vide ou uniquement composée d'espaces : `LocalSearchQuery.parse` ne produit aucun token, `searchUnified` retourne `SearchResult()` sans toucher au DAO — comportement identique à l'actuel (`matchQuery.isBlank()`), donc l'état vide de l'écran est conservé.

## 5.2 Flux de données — écriture du catalogue

```
XtreamApi → DTO → mapping repository → *StreamEntity(searchText = "")
                                          │
                        DAO.insertStreams / replaceAllStreams / replaceStreamsByCategory
                                          │  (@Transaction)
                        entity.copy(searchText = LocalSearchQuery.buildSearchText(entity))
                                          │
                                    INSERT OR REPLACE (table physique uniquement)
```

Une seule écriture par ligne au lieu de deux (table + table FTS). `ClearCatalogCacheUseCase` n'a plus qu'à vider les tables physiques.

## 5.3 Responsabilités

| Composant | Responsabilité | Ne fait pas |
|-----------|----------------|-------------|
| `LocalSearchQuery` (domain, pur) | Découper la saisie, normaliser en minuscules, échapper les métacaractères `LIKE`, choisir le token ancre, décider si un `searchText` satisfait **tous** les tokens, construire le `searchText` d'une entité | Aucun accès Room, aucune dépendance Android → 100 % testable en JVM |
| `FavoritesDao` | Exécuter le scan `LIKE` et recalculer `searchText` à l'insertion | Ne connaît ni la tokenisation ni l'ordre des mots |
| `FavoritesRepositoryImpl` | Orchestrer les 3 requêtes, appliquer le `AND` résiduel, mapper vers le domaine | Aucune règle de présentation |
| `SearchUnifiedUseCase` | Retirer les catégories masquées | Inchangé par F17 |
| `AdvancedCatalogSearchUseCase` | Filtres avancés (note, année, genres, catégorie) + prédicat texte délégué à `LocalSearchQuery` | Ne duplique plus la logique de correspondance texte |

## 5.4 Sémantique de correspondance retenue

- Tokens = `raw.trim().split(Regex("\\s+")).filter { it.isNotBlank() }`, chacun passé par `normalize()` (minuscules + NFD + strip `\p{Mn}` + table de ligatures, cf. D2bis). Un token qui devient vide après normalisation est ignoré.
- Un élément est retenu si **chaque** token est une sous-chaîne de son `searchText` — donc ordre libre et champs différents autorisés (`marsu ami` trouve `Le Marsupilami`, `jean reno` trouve un film dont le nom porte l'un et les acteurs l'autre).
- Token ancre = le plus long (départage : le premier rencontré) ; c'est le seul évalué en SQL, les autres en mémoire. Le résultat est strictement identique quel que soit l'ancre choisi : seule la quantité de lignes remontées change.
- Insensibilité à la casse **et** aux accents assurée des deux côtés par la même fonction `normalize()`, appliquée à l'écriture (construction de `searchText`) et à la lecture (tokens de la requête). L'ASCII-only de `LIKE` devient sans effet : les deux opérandes sont déjà en ASCII minuscule.
- La relation est symétrique : `Odysée` est trouvé par `ody`, `ODYSEE`, `odysee`, `odysée`, `dysee` ; et `Rene` est trouvé par `rené` comme `René` l'est par `rene`.
- La translittération entre alphabets reste hors périmètre (§3, cas limites).

## 5.5 Stratégie de tests (JVM uniquement)

- `LocalSearchQueryTest` : tokenisation (espaces multiples, début/fin, saisie vide), échappement `%` / `_` / `\` **après** normalisation, correspondance début/milieu/fin (`marsu`, `pilami`, `lami` → `marsupilami`), multi-mots dans l'ordre inverse, multi-mots répartis sur deux champs, token absent → rejet, champs `null` ignorés, construction de `searchText` par type de média.
- `LocalSearchQueryTest`, repli d'accents (cas nominal de la demande) : `Odysée` trouvé par `Ody`, `ody`, `ODYSEE`, `odysee`, `odysée`, `dysee` ; symétrie `rene` ↔ `René` ↔ `Rene` ; ligatures `Cœur` trouvé par `coeur`, `Straße` par `strasse` ; caractère hors table latine inchangé mais non bloquant ; token réduit à vide après normalisation ignoré.
- Cohérence migration / Kotlin : test unitaire vérifiant que `foldSql` et `LocalSearchQuery.normalize` produisent le même résultat sur la table `FOLD_MAP` (le SQL est vérifié par équivalence de la table de correspondance, pas par exécution SQLite).
- `FavoritesRepositoryImplTest` : pattern `LIKE` transmis au DAO pour le token ancre, filtrage résiduel des tokens restants, requête blanche → aucun appel DAO, mapping `releaseYear` inchangé.
- `VodDao` / `SeriesDao` / `LiveTvDao` : le recalcul de `searchText` étant dans un `@Transaction` DAO, il est couvert indirectement par les tests de repository qui capturent les entités passées ; la partie calcul pure est couverte par `LocalSearchQueryTest`.
- Non-régression : `VodRepositoryImplTest`, `SeriesRepositoryImplTest`, `CategoryFilteringUseCasesTest`, `FavoritesViewModelTest`.
- La migration 20 → 21 n'est pas testable automatiquement (limite d'infrastructure documentée dans `AGENTS.md`) : relecture manuelle du SQL.

---

# 6. Objectif

Permettre à l'utilisateur de trouver des chaînes de télévision, des films et des séries en saisissant n'importe quelle portion de leurs informations recherchables, tout en maintenant des performances d'exécution fluides et adaptées sur les terminaux Android de l'utilisateur.

---

# 7. Hypothèses

- **Retour au mécanisme `LIKE` ou modification des requêtes SQL :** Les requêtes SQL dans `FavoritesDao` devront être modifiées pour utiliser un opérateur `LIKE :query` au lieu de `MATCH` sur les tables FTS, ou alors faire des recherches directes sur les tables standard `live_streams`, `vod_streams` et `series_streams`.
- **Performance et volumétrie :** La base de données locale contient le catalogue IPTV synchronisé du serveur Xtream Codes. Bien que ce catalogue puisse contenir plusieurs milliers d'entrées, un scan de table SQLite (`LIKE '%marsu%'`) sur un index ou une colonne texte sur des appareils Android modernes est généralement extrêmement rapide (quelques millisecondes) et imperceptible pour l'utilisateur, d'autant plus que la recherche s'exécute en arrière-plan via des coroutines.
- **Support des mots multiples :** La règle fonctionnelle est désormais établie : chaque mot non vide doit être présent comme sous-chaîne, dans n'importe quel ordre et éventuellement dans des champs différents du même média.
- **Maintien des structures FTS :** Nous devons décider s'il convient de supprimer complètement les tables FTS et les Dao correspondants si nous n'en avons plus besoin, ou de conserver les tables FTS pour d'autres usages futurs, bien que la simplification du schéma de base de données (en évitant la double écriture FTS) soit une excellente opportunité de refactoring.

---

# 8. Questions ouvertes — tranchées à l'étape 3

1. **Remplacement complet de FTS ou hybride ?** → **Remplacement complet** (D1). Un mode hybride (FTS pour les préfixes + `LIKE` pour le reste) devrait fusionner et dédoublonner deux jeux de résultats aux classements différents, tout en conservant la double écriture et son risque d'incohérence — pour un gain nul, `LIKE` étant un sur-ensemble strict du préfixe. Les 3 entités FTS, les 9 méthodes DAO de synchronisation et les 3 appels de `ClearCatalogCacheUseCase` sont supprimés.
2. **Impact sur les migrations ?** → **Oui, `MIGRATION_20_21` réelle et sans perte** (D6, SQL complet en §4.4) : `ALTER TABLE ADD COLUMN searchText` ×3, backfill par `UPDATE … lower(…)`, puis `DROP TABLE` des 3 tables virtuelles. Pas de `fallbackToDestructiveMigration()`, pas de changement de clé primaire (donc pas de pattern `<table>_new`). Les tables FTS étant un index dérivé, leur suppression ne perd aucune donnée.

## Points restés ouverts (hors périmètre F17)

- **Repli d'accents** : initialement exclu, **réintégré dans le périmètre F17** à la demande du PO (cas `Odysée` recherché par `odysee` / `ODYSEE`). Implémenté par la normalisation de `searchText` et des tokens (D2 / D2bis), sans coût structurel supplémentaire puisque la colonne existait déjà pour la casse.
- **Translittération entre alphabets** (cyrillique/grec/arabe → latin) : hors périmètre, nécessiterait une table de correspondance par alphabet.
- **Pertinence / classement des résultats** : le tri reste alphabétique par `name`, comme aujourd'hui. FTS4 offrait un potentiel de scoring jamais exploité ; aucun classement par pertinence n'est introduit ici.

---

# 9. Plan de développement

## P1 — Créer le modèle pur de requête locale

- [ ] Ajouter `LocalSearchQuery` avec la tokenisation, la normalisation, l'échappement SQL et le prédicat multi-mots.

Objectif : centraliser une sémantique identique de recherche par sous-chaîne pour la recherche unifiée et la recherche avancée, sans dépendance Android ou Room.

Fichiers :
- `app/src/main/java/com/cstv/app/domain/model/LocalSearchQuery.kt`

Validation : `LocalSearchQueryTest` couvre requête vide, espaces, sous-chaîne début/milieu/fin, tokens multiples, casse, accents, ligatures et les caractères `%`, `_`, `\\` littéraux.

## P2 — Étendre les entités catalogue et préparer Room 21

- [ ] Ajouter le champ dénormalisé `searchText` aux trois entités catalogue et déclarer la version 21 de Room.

Objectif : stocker, sur chaque ligne physique, le texte de recherche normalisé afin que SQLite n'ait pas à gérer la casse Unicode ni les diacritiques.

Fichiers :
- `app/src/main/java/com/cstv/app/data/local/entity/LiveStreamEntity.kt`
- `app/src/main/java/com/cstv/app/data/local/entity/VodStreamEntity.kt`
- `app/src/main/java/com/cstv/app/data/local/entity/SeriesStreamEntity.kt`
- `app/src/main/java/com/cstv/app/data/local/db/AppDatabase.kt`

Validation : les entités conservent leurs clés et champs existants ; `searchText` est non nul avec une valeur par défaut compatible avec les données déjà insérées.

## P3 — Mettre en place la migration non destructive 20 → 21

- [ ] Ajouter `MIGRATION_20_21`, le backfill de `searchText` et le retrait des trois tables FTS4.

Objectif : préserver le catalogue existant tout en supprimant uniquement les index FTS dérivés et devenus inutiles.

Fichiers :
- `app/src/main/java/com/cstv/app/data/local/db/Migrations.kt`

Validation : la migration ajoute les trois colonnes, remplit le texte normalisé selon la table de repli documentée, supprime les tables virtuelles avec `IF EXISTS` et est ajoutée à `ALL_MIGRATIONS`, sans fallback destructif.

## P4 — Remplacer les lectures FTS par les requêtes LIKE statiques

- [ ] Adapter `FavoritesDao` pour chercher dans les tables physiques avec `LIKE :pattern ESCAPE '\\'`.

Objectif : faire filtrer en SQLite le token ancre le plus sélectif, en garantissant que les métacaractères saisis restent littéraux.

Fichiers :
- `app/src/main/java/com/cstv/app/data/local/dao/FavoritesDao.kt`

Validation : chaque type de média est lu depuis sa table physique, sans jointure FTS ni `MATCH`, avec le tri existant par nom conservé.

## P5 — Simplifier les écritures catalogue et supprimer la double écriture FTS

- [ ] Recalculer `searchText` dans les wrappers transactionnels des DAO et retirer les opérations de synchronisation FTS.

Objectif : garantir que les enrichissements ultérieurs (acteurs, réalisateur, genre) ne laissent jamais un texte de recherche périmé.

Fichiers :
- `app/src/main/java/com/cstv/app/data/local/dao/LiveTvDao.kt`
- `app/src/main/java/com/cstv/app/data/local/dao/VodDao.kt`
- `app/src/main/java/com/cstv/app/data/local/dao/SeriesDao.kt`
- `app/src/main/java/com/cstv/app/data/local/entity/LiveStreamFtsEntity.kt` (suppression)
- `app/src/main/java/com/cstv/app/data/local/entity/VodStreamFtsEntity.kt` (suppression)
- `app/src/main/java/com/cstv/app/data/local/entity/SeriesStreamFtsEntity.kt` (suppression)

Validation : chaque insertion, remplacement complet ou par catégorie reconstruit `searchText` avant persistance ; aucune méthode ni entité FTS ne subsiste.

## P6 — Raccorder les repositories et le nettoyage du catalogue

- [ ] Employer `LocalSearchQuery` dans la recherche unifiée, renommer les appels DAO et retirer le nettoyage FTS.

Objectif : appliquer l'AND de tous les tokens après la requête SQL ancre et conserver les filtres de catégories et le mapping existants.

Fichiers :
- `app/src/main/java/com/cstv/app/data/repository/FavoritesRepositoryImpl.kt`
- `app/src/main/java/com/cstv/app/data/repository/LiveTvRepositoryImpl.kt`
- `app/src/main/java/com/cstv/app/data/repository/VodRepositoryImpl.kt`
- `app/src/main/java/com/cstv/app/data/repository/SeriesRepositoryImpl.kt`
- `app/src/main/java/com/cstv/app/domain/usecase/ClearCatalogCacheUseCase.kt`

Validation : une requête blanche n'appelle aucun DAO ; un token ancre produit le motif LIKE échappé attendu ; tous les autres tokens sont exigés avant le mapping final.

## P7 — Unifier la recherche avancée

- [ ] Déléguer le filtrage texte de `AdvancedCatalogSearchUseCase` à `LocalSearchQuery`.

Objectif : éviter une divergence de résultat entre recherche unifiée et recherche avancée pour une même saisie multi-mots.

Fichiers :
- `app/src/main/java/com/cstv/app/domain/usecase/AdvancedCatalogSearchUseCase.kt`

Validation : les filtres avancés existants restent inchangés et le prédicat texte accepte tous les tokens, quel que soit leur ordre ou leur champ source.

## P8 — Ajouter et adapter la couverture JVM

- [ ] Créer les tests du modèle pur et adapter les tests impactés par la disparition des méthodes FTS.

Objectif : couvrir les règles fonctionnelles F17 et les renommages mécaniques sans nécessiter d'appareil ni de base Room instrumentée.

Fichiers :
- `app/src/test/java/com/cstv/app/domain/model/LocalSearchQueryTest.kt`
- `app/src/test/java/com/cstv/app/data/repository/FavoritesRepositoryImplTest.kt`
- `app/src/test/java/com/cstv/app/data/repository/VodRepositoryImplTest.kt`
- `app/src/test/java/com/cstv/app/data/repository/SeriesRepositoryImplTest.kt`
- tests DAO ou repository directement affectés par les wrappers d'écriture

Validation : les critères d'acceptation `Marsupilami`, `Odysée`/`Rene`, multi-mots et métacaractères sont automatisés ; les tests existants ne référencent plus les API FTS supprimées.
