# F17 - Recherche globale par sous-chaîne (LIKE "%keyword%")

## Informations générales

Type:
Feature

Status:
RELEASED

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

- [x] Ajouter `LocalSearchQuery` avec la tokenisation, la normalisation, l'échappement SQL et le prédicat multi-mots.

Objectif : centraliser une sémantique identique de recherche par sous-chaîne pour la recherche unifiée et la recherche avancée, sans dépendance Android ou Room.

Fichiers :
- `app/src/main/java/com/cstv/app/domain/model/LocalSearchQuery.kt`

Validation : `LocalSearchQueryTest` couvre requête vide, espaces, sous-chaîne début/milieu/fin, tokens multiples, casse, accents, ligatures et les caractères `%`, `_`, `\\` littéraux.

## P2 — Étendre les entités catalogue et préparer Room 21

- [x] Ajouter le champ dénormalisé `searchText` aux trois entités catalogue et déclarer la version 21 de Room.

Objectif : stocker, sur chaque ligne physique, le texte de recherche normalisé afin que SQLite n'ait pas à gérer la casse Unicode ni les diacritiques.

Fichiers :
- `app/src/main/java/com/cstv/app/data/local/entity/LiveStreamEntity.kt`
- `app/src/main/java/com/cstv/app/data/local/entity/VodStreamEntity.kt`
- `app/src/main/java/com/cstv/app/data/local/entity/SeriesStreamEntity.kt`
- `app/src/main/java/com/cstv/app/data/local/db/AppDatabase.kt`

Validation : les entités conservent leurs clés et champs existants ; `searchText` est non nul avec une valeur par défaut compatible avec les données déjà insérées.

## P3 — Mettre en place la migration non destructive 20 → 21

- [x] Ajouter `MIGRATION_20_21`, le backfill de `searchText` et le retrait des trois tables FTS4.

Objectif : préserver le catalogue existant tout en supprimant uniquement les index FTS dérivés et devenus inutiles.

Fichiers :
- `app/src/main/java/com/cstv/app/data/local/db/Migrations.kt`

Validation : la migration ajoute les trois colonnes, remplit le texte normalisé selon la table de repli documentée, supprime les tables virtuelles avec `IF EXISTS` et est ajoutée à `ALL_MIGRATIONS`, sans fallback destructif.

## P4 — Remplacer les lectures FTS par les requêtes LIKE statiques

- [x] Adapter `FavoritesDao` pour chercher dans les tables physiques avec `LIKE :pattern ESCAPE '\\'`.

Objectif : faire filtrer en SQLite le token ancre le plus sélectif, en garantissant que les métacaractères saisis restent littéraux.

Fichiers :
- `app/src/main/java/com/cstv/app/data/local/dao/FavoritesDao.kt`

Validation : chaque type de média est lu depuis sa table physique, sans jointure FTS ni `MATCH`, avec le tri existant par nom conservé.

## P5 — Simplifier les écritures catalogue et supprimer la double écriture FTS

- [x] Recalculer `searchText` dans les wrappers transactionnels des DAO et retirer les opérations de synchronisation FTS.

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

- [x] Employer `LocalSearchQuery` dans la recherche unifiée, renommer les appels DAO et retirer le nettoyage FTS.

Objectif : appliquer l'AND de tous les tokens après la requête SQL ancre et conserver les filtres de catégories et le mapping existants.

Fichiers :
- `app/src/main/java/com/cstv/app/data/repository/FavoritesRepositoryImpl.kt`
- `app/src/main/java/com/cstv/app/data/repository/LiveTvRepositoryImpl.kt`
- `app/src/main/java/com/cstv/app/data/repository/VodRepositoryImpl.kt`
- `app/src/main/java/com/cstv/app/data/repository/SeriesRepositoryImpl.kt`
- `app/src/main/java/com/cstv/app/domain/usecase/ClearCatalogCacheUseCase.kt`

Validation : une requête blanche n'appelle aucun DAO ; un token ancre produit le motif LIKE échappé attendu ; tous les autres tokens sont exigés avant le mapping final.

## P7 — Unifier la recherche avancée

- [x] Déléguer le filtrage texte de `AdvancedCatalogSearchUseCase` à `LocalSearchQuery`.

Objectif : éviter une divergence de résultat entre recherche unifiée et recherche avancée pour une même saisie multi-mots.

Fichiers :
- `app/src/main/java/com/cstv/app/domain/usecase/AdvancedCatalogSearchUseCase.kt`

Validation : les filtres avancés existants restent inchangés et le prédicat texte accepte tous les tokens, quel que soit leur ordre ou leur champ source.

## P8 — Ajouter et adapter la couverture JVM

- [x] Créer les tests du modèle pur et adapter les tests impactés par la disparition des méthodes FTS.

Objectif : couvrir les règles fonctionnelles F17 et les renommages mécaniques sans nécessiter d'appareil ni de base Room instrumentée.

Fichiers :
- `app/src/test/java/com/cstv/app/domain/model/LocalSearchQueryTest.kt`
- `app/src/test/java/com/cstv/app/data/repository/FavoritesRepositoryImplTest.kt`
- `app/src/test/java/com/cstv/app/data/repository/VodRepositoryImplTest.kt`
- `app/src/test/java/com/cstv/app/data/repository/SeriesRepositoryImplTest.kt`
- tests DAO ou repository directement affectés par les wrappers d'écriture

Validation : les critères d'acceptation `Marsupilami`, `Odysée`/`Rene`, multi-mots et métacaractères sont automatisés ; les tests existants ne référencent plus les API FTS supprimées.

## Notes d'implémentation — étape 5 (2026-07-27)

- FTS4 est remplacé par un blob `searchText` normalisé, reconstruit dans les DAO avant toute écriture catalogue.
- La migration 20 → 21 conserve les tables physiques, peuple les blobs existants puis supprime seulement les index virtuels FTS dérivés.
- Les tests JVM passent (446 tests, 0 échec). Les contrôles `assembleDebug` et `lintDebug` restent à confirmer séparément : l'exécuteur Gradle a interrompu leur remontée de résultat après démarrage.

---

# 10. Review

Revue technique du 2026-07-27 (Opus). Portée : les 22 fichiers du diff de travail
(`LocalSearchQuery`, `FavoritesDao`, `Migrations`, les 3 entités, les 3 DAO d'écriture,
les 4 repositories, `AdvancedCatalogSearchUseCase`, `ClearCatalogCacheUseCase` et les
tests). Aucune modification de code effectuée.

Status: RESOLVED — corrections appliquées et couvertes par les tests.

## Corrections — étape 7 (2026-07-27)

- CRIT-1 : les trois requêtes Room partagent ESCAPE avec un seul antislash et
  LocalSearchQueryTest l'exécute réellement avec SQLite JDBC, y compris les
  métacaractères %, _ et antislash.
- MAJ-1 / MAJ-2 : les tests couvrent le choix du token ancre, le filtrage résiduel dans
  le repository et l'évaluation SQL de chaque entrée de SEARCH_FOLD_MAP, comparée à la
  normalisation Kotlin.
- MAJ-3 : normalize emploie un StringBuilder et une regex compilée une seule fois ; la
  recherche avancée consomme searchText persisté. Le recalcul est limité au repli sûr des
  objets de domaine sans blob (anciens appelants et doubles de test).
- MIN-1 à MIN-6 : requête vide non correspondante, élargissement categoryId acté pour la
  recherche avancée, garde-fous DAO, valeur par défaut Room cohérente, commentaires FTS
  retirés, version Room et KDoc mis à jour.

## Validation finale — étape 8 (2026-07-27)

- Tests JVM : 450 tests, 0 échec, 0 erreur.
- Build debug : assembleDebug exécuté avec succès pendant la validation Gradle.
- git diff --check ne signale aucun défaut d'espacement.
- Lint : le dernier rapport disponible indique 0 erreur et 55 avertissements préexistants.
  Une relance complète après les corrections a été interrompue avant lintDebug par la limite
  d'exécution de l'environnement ; les tests et la compilation des sources modifiées ont
  bien été rejoués.

Vérifications exécutées :

- `./gradlew testDebugUnitTest assembleDebug lintDebug` → `BUILD SUCCESSFUL`.
  Agrégat des rapports JUnit : **446 tests, 0 échec, 0 erreur, 0 ignoré**.
  Lint : **0 erreur, 55 avertissements** (préexistants). Les contrôles laissés en suspens
  à l'étape 5 sont donc confirmés.
- Exécution du `LIKE … ESCAPE` du DAO sur un vrai moteur SQLite (3.40.1) — voir CRIT-1.
- Micro-benchmark JVM de `normalize()` — voir MAJ-3.

## Points positifs

- **Suppression de FTS4 complète et propre** : 3 entités, 9 méthodes DAO de
  synchronisation et les 3 `clearAllFts()` de `ClearCatalogCacheUseCase` ont disparu ;
  plus aucune référence FTS active dans le code (seuls subsistent des commentaires,
  cf. MIN-5, et le SQL historique de `MIGRATION_19_20`, normal). Bilan : **-223/+134 lignes**.
- **D5 tenu sans faille** : `searchText` est recalculé dans les wrappers `@Transaction`
  (`VodDao.insertStreams`, `SeriesDao`, `LiveTvDao`), jamais chez l'appelant. Vérifié :
  aucun `@Update` ni `UPDATE` SQL sur les 3 tables catalogue, et `insertStreamsRaw` n'est
  appelé que depuis le wrapper — les `copy(actors = …, genre = …)` d'enrichissement ne
  peuvent pas laisser un blob périmé.
- **Migration conforme aux règles impératives d'`AGENTS.md`** : `MIGRATION_20_21` réelle,
  ajoutée à `ALL_MIGRATIONS`, sans `fallbackToDestructiveMigration()`,
  `ALTER TABLE ADD COLUMN` + backfill + `DROP TABLE IF EXISTS`. `name` et `categoryId` étant
  `NOT NULL` dans les 3 entités, la concaténation du backfill ne peut pas produire de `NULL`
  et donc pas violer la contrainte `NOT NULL` de la nouvelle colonne.
- **Ordre `replace()` puis `lower()`** correctement appliqué dans `searchFoldSql`, comme
  exigé en §4.4 : `lower()` de SQLite étant ASCII-only, l'inverse aurait manqué les
  majuscules accentuées.
- **Symétrie de normalisation lecture/écriture** : la même fonction sert à construire le blob
  et à normaliser les tokens ; les deux opérandes du `LIKE` sont en ASCII minuscule, ce qui
  neutralise réellement les limites Unicode de SQLite (D2).
- **`escapeLike` appliqué après normalisation** (ordre imposé §4.3), et `lowercase(Locale.ROOT)`
  qui évite le piège du `I` turc.
- **D7 tenu** : `AdvancedCatalogSearchUseCase` ne duplique plus de prédicat texte ; la
  sémantique multi-mots est unique pour les deux chemins de recherche.

## Critique

### CRIT-1 — `ESCAPE '\\'` dans une raw string Kotlin : toute recherche unifiée échoue à l'exécution

Description : les 3 `@Query` de `FavoritesDao` sont écrites dans des raw strings
(`"""…"""`), où Kotlin **ne traite aucune séquence d'échappement**. Le SQL réellement
envoyé à SQLite contient donc `ESCAPE '\\'`, soit une chaîne de **deux** caractères. Or
SQLite exige un caractère unique. Vérifié sur le fichier (octets) et sur moteur réel :

```
DAO (octets) : SELECT * FROM live_streams WHERE searchText LIKE :pattern ESCAPE '\\' ORDER BY name ASC

sqlite 3.40.1 :
  … LIKE ? ESCAPE '\\'  -> OperationalError: ESCAPE expression must be a single character
  … LIKE ? ESCAPE '\'   -> [('marsupilami',)]
```

Pourquoi la chaîne de validation ne l'a pas vu : le vérificateur Room de KSP ne fait que
**préparer** la requête, et le contrôle du caractère d'échappement a lieu à **l'exécution**
de la fonction `like()` (confirmé : `EXPLAIN` avec le `ESCAPE` à deux caractères compile sans
erreur). Côté tests, `FavoritesRepositoryImplTest` **mocke `FavoritesDao`** : aucun test du
projet n'exécute ce SQL. D'où 446 tests verts, `assembleDebug` et `lintDebug` verts, et un
bug qui ne se manifeste que sur appareil.

Impact : **bloquant**. `searchLiveStreams`, `searchVodStreams` et `searchSeriesStreams`
lèvent une `SQLiteException` à chaque saisie. La recherche globale unifiée — l'objet même de
F17 — est intégralement hors service, et l'exception remonte dans `searchUnified` sans
`try/catch`. Aucun critère d'acceptation §3 n'est atteignable en l'état.

Correction attendue : écrire `ESCAPE '\'` dans les 3 raw strings (un seul antislash), et
ajouter au moins un test exécutant réellement la requête (Robolectric + Room in-memory, ou à
défaut un test JVM qui prépare *et exécute* le SQL du DAO sur un SQLite embarqué) pour que
cette classe de défaut ne puisse plus traverser la validation.

## Majeur

### MAJ-1 — Le mécanisme central « token ancre + filtrage résiduel » n'est couvert par aucun test

Description : §5.5 prévoyait explicitement « pattern `LIKE` transmis au DAO pour le token
ancre, filtrage résiduel des tokens restants ». Les 3 tests de `FavoritesRepositoryImplTest`
touchant `searchUnified` utilisent tous une requête **à un seul mot** (`"t"`, `"movie"`, `"  "`).
Aucun test ne vérifie que, pour `jean reno` :
1. le motif envoyé au DAO est bien celui du token **le plus long** ;
2. une ligne remontée par SQL mais ne contenant pas les autres tokens est bien écartée.
Côté modèle, `LocalSearchQueryTest` ne teste pas non plus le choix de l'ancre — le seul test
de `likePattern` porte sur un token unique.

Impact : la partie du dispositif la plus susceptible de régresser (répartition du filtrage
entre SQL et Kotlin) n'a pas de harnais. Une inversion `maxByOrNull` → `minByOrNull`, ou un
`filter` supprimé, passerait la CI sans être vue — alors que le résultat utilisateur serait
faux (résultats surnuméraires).

Correction attendue : un test repository multi-mots vérifiant le motif transmis **et**
l'élimination d'une entité ne contenant pas tous les tokens ; un test modèle sur le choix de
l'ancre.

### MAJ-2 — Test de cohérence entre la table de repli SQL et la normalisation Kotlin non implémenté

Description : la table de repli existe désormais **en double** — `SEARCH_FOLD_MAP`
(33 entrées, `Migrations.kt`) et `LocalSearchQuery.FOLD_MAP` (6 entrées + NFD). §4.7 et §5.5
prévoyaient un test unitaire vérifiant que `foldSql` et `normalize` produisent le même
résultat sur cette table ; il n'existe pas. `searchFoldSql` est de surcroît `private`, donc
non testable en l'état.

Impact : la divergence documentée comme « faible car couverte par un test » n'est en fait
couverte par rien. Ajouter un caractère d'un côté sans l'autre produit des résultats
manquants silencieux jusqu'à la resynchronisation du catalogue, sans aucun signal en CI.

Correction attendue : implémenter le test annoncé (rendre la fonction ou la table
`internal`), ou retirer la mitigation de §4.7 et assumer explicitement le risque.

### MAJ-3 — La recherche avancée recalcule tout le blob du catalogue à chaque requête

Description : `AdvancedCatalogSearchUseCase` appelle `LocalSearchQuery.buildCatalogSearchText(...)`
**par item et par requête** (l. 62 et 65), soit 5 `normalize()` par item sur l'intégralité du
catalogue en mémoire — alors que ce blob est déjà persisté dans la colonne `searchText`. Deux
inefficacités s'y ajoutent dans `normalize()` : la concaténation `fold("") { result, char -> result + … }`
est en O(n²), et `Regex("\\p{Mn}+")` est **recompilée à chaque appel**.

Mesuré (JVM desktop, réplique fidèle des deux variantes, 5 000 items × 5 champs) :
**23 ms** pour l'implémentation actuelle contre **8 ms** avec `StringBuilder` + `Pattern`
statique, soit un facteur **×2,8** — à multiplier par l'écart ART / box Android TV. L'ancien
prédicat ne faisait que 4 `contains(ignoreCase = true)` sans allocation.

Impact : jank potentiel sur le chemin recherche avancée pour les gros catalogues, sur un
travail intégralement redondant avec la base. Le chemin synchronisation, lui, reste gagnant
(il remplace une écriture FTS par ligne).

Correction attendue : `StringBuilder` + `Regex` hissée en constante dans `normalize()` ; et,
pour la recherche avancée, exposer le `searchText` déjà calculé jusqu'au modèle domaine
plutôt que le reconstruire (ou, a minima, filtrer en SQL comme le fait la recherche unifiée).

## Mineur

### MIN-1 — `matches()` et `likePattern` sont permissifs sur une requête vide

Description : `tokens.all { … }` sur une liste vide renvoie `true`, et `anchor` vide produit
le motif `"%%"`. Les deux appelants actuels gardent bien `isEmpty` en amont, donc le
comportement observable est correct.

Impact : nul aujourd'hui, mais l'objet pur est conçu pour être réutilisé et son API invite au
faux positif silencieux « tout matche ».

Correction attendue : faire renvoyer `false` à `matches()` quand `isEmpty`, ou documenter
explicitement la précondition dans le KDoc.

### MIN-2 — La recherche avancée indexe désormais `categoryId`, ce que l'ancien prédicat ne faisait pas

Description : l'ancien `matchesTextQuery` couvrait `name`, `actors`, `director`, `genre`. Le
nouveau blob y ajoute `categoryId` (§4.2 justifie ce périmètre pour la recherche **unifiée**,
au titre de la parité avec FTS4 ; pour la recherche avancée c'est un élargissement).

Impact : faible mais réel — les identifiants de catégorie étant numériques, une saisie courte
comme `12` peut ramener toute une catégorie en plus des titres contenant « 12 ».

Correction attendue : soit exclure `categoryId` du blob utilisé par la recherche avancée,
soit acter l'élargissement dans §4.2 / §5.3.

### MIN-3 — `insertStreamsRaw` est publique dans les 3 DAO

Description : Room interdisant les méthodes privées dans une interface `@Dao`, le point
d'entrée qui court-circuite le recalcul de `searchText` reste appelable. Vérifié : aucun
appel hors des wrappers aujourd'hui.

Impact : faible, mais c'est exactement le scénario que D5 cherche à rendre impossible.

Correction attendue : commentaire d'interdiction explicite au-dessus des trois déclarations
(« ne jamais appeler directement : contourne le calcul de searchText »).

### MIN-4 — `searchText` sans `@ColumnInfo(defaultValue = "''")`

Description : la migration crée la colonne avec `DEFAULT ''`, alors qu'une installation neuve
la crée sans clause `DEFAULT` (l'entité ne la déclare pas). Room 2.6.1 ne compare la valeur
par défaut que si l'entité en déclare une : la validation de schéma passe dans les deux cas.

Impact : nul fonctionnellement ; deux schémas physiques légèrement différents selon
l'historique de l'appareil, ce qui complique tout diagnostic ultérieur.

Correction attendue : déclarer `@ColumnInfo(defaultValue = "''")` sur les trois champs.

### MIN-5 — Commentaires obsolètes mentionnant FTS

Description : `VodRepositoryImpl.kt:338`, `LiveTvRepositoryImpl.kt:120` et
`SeriesRepositoryImpl.kt:292` décrivent encore un « remplacement atomique (FTS comprise) ».

Impact : documentation trompeuse sur du code qui n'a plus de table FTS.

Correction attendue : mise à jour des trois commentaires.

### MIN-6 — Incohérences documentaires résiduelles

Description : (a) `AGENTS.md` annonce toujours « `AppDatabase`, version **20** » alors que
F17 la porte à 21 ; (b) le KDoc de `LocalSearchQuery` est en anglais, là où les objets purs
voisins du même package (`GenreParser`, `RelatedTitlesSelector`) sont commentés en français.

Impact : faible ; le point (a) concerne le fichier de règles permanentes lu à chaque session,
donc à traiter au plus tard à l'étape Documentation.

Correction attendue : mettre à jour la version dans `AGENTS.md` ; harmoniser la langue du KDoc.

## Corrections demandées

1. **CRIT-1 — bloquant** : `ESCAPE '\'` dans les 3 `@Query` de `FavoritesDao`, et un test qui
   exécute réellement le SQL. À corriger avant toute validation ou livraison.
2. **MAJ-1** : tests multi-mots (motif de l'ancre + filtrage résiduel) au niveau repository et
   modèle.
3. **MAJ-2** : test de cohérence `SEARCH_FOLD_MAP` ↔ `LocalSearchQuery.normalize`, ou retrait
   assumé de la mitigation en §4.7.
4. **MAJ-3** : `StringBuilder` + `Regex` en constante dans `normalize()` ; supprimer le
   recalcul du blob pour tout le catalogue dans la recherche avancée.
5. **MIN-1 à MIN-6** : durcissement de l'API pure, périmètre `categoryId` de la recherche
   avancée, garde-fou sur `insertStreamsRaw`, `defaultValue` des colonnes, commentaires FTS
   obsolètes, version de base dans `AGENTS.md`.

---

# 11. Release

Version:
v1.62.0

Commit:
:sparkles: feat(search): Implement global unified local substring search (F17)

Date:
2026-07-27
