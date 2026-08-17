package com.cstv.app.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migrations Room réelles, à préserver et compléter à chaque bump de version
 * du schéma (voir AGENTS.md § "Base de données Room"). Objectif : ne jamais
 * effacer le cache/les données utilisateur (favoris, historique, positions de
 * lecture, profils) lors d'une mise à jour de l'app, sauf décision explicite
 * et documentée de breaking change majeur.
 *
 * MIGRATION_9_10 (Phase 27 - profils multiples) :
 * - Crée la table `profiles` et y insère un profil par défaut ("Profil 1",
 *   id=1) pour donner un foyer aux données existantes.
 * - Ajoute `profileId` à la clé primaire de `favorites`, `playback_positions`
 *   et `recently_watched_live` (SQLite ne supporte pas l'ajout d'une colonne
 *   à une clé primaire via ALTER TABLE : recréation de table + copie des
 *   données, backfill profileId=1 pour tout ce qui existait avant profils).
 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS profiles (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                avatarId INTEGER NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "INSERT INTO profiles (id, name, avatarId, createdAt) VALUES (1, 'Profil 1', 0, ${System.currentTimeMillis()})"
        )

        // --- favorites: PK (id, type) -> (id, type, profileId) ---
        db.execSQL(
            """
            CREATE TABLE favorites_new (
                id INTEGER NOT NULL,
                type TEXT NOT NULL,
                name TEXT NOT NULL,
                cover TEXT,
                categoryId TEXT NOT NULL,
                addedAt INTEGER NOT NULL,
                profileId INTEGER NOT NULL,
                PRIMARY KEY(id, type, profileId)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO favorites_new (id, type, name, cover, categoryId, addedAt, profileId)
            SELECT id, type, name, cover, categoryId, addedAt, 1 FROM favorites
            """.trimIndent()
        )
        db.execSQL("DROP TABLE favorites")
        db.execSQL("ALTER TABLE favorites_new RENAME TO favorites")

        // --- playback_positions: PK streamId -> (streamId, profileId) ---
        db.execSQL(
            """
            CREATE TABLE playback_positions_new (
                streamId INTEGER NOT NULL,
                profileId INTEGER NOT NULL,
                positionMs INTEGER NOT NULL,
                durationMs INTEGER NOT NULL,
                lastAccessedAt INTEGER NOT NULL,
                title TEXT,
                coverUrl TEXT,
                type TEXT,
                containerExtension TEXT,
                seriesId INTEGER,
                episodeNum INTEGER,
                seasonNum INTEGER,
                plot TEXT,
                duration TEXT,
                releaseDate TEXT,
                PRIMARY KEY(streamId, profileId)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO playback_positions_new (
                streamId, profileId, positionMs, durationMs, lastAccessedAt, title, coverUrl,
                type, containerExtension, seriesId, episodeNum, seasonNum, plot, duration, releaseDate
            )
            SELECT
                streamId, 1, positionMs, durationMs, lastAccessedAt, title, coverUrl,
                type, containerExtension, seriesId, episodeNum, seasonNum, plot, duration, releaseDate
            FROM playback_positions
            """.trimIndent()
        )
        db.execSQL("DROP TABLE playback_positions")
        db.execSQL("ALTER TABLE playback_positions_new RENAME TO playback_positions")

        // --- recently_watched_live: PK streamId -> (streamId, profileId) ---
        db.execSQL(
            """
            CREATE TABLE recently_watched_live_new (
                streamId INTEGER NOT NULL,
                profileId INTEGER NOT NULL,
                name TEXT NOT NULL,
                streamIcon TEXT,
                categoryId TEXT,
                num INTEGER,
                watchedAt INTEGER NOT NULL,
                PRIMARY KEY(streamId, profileId)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO recently_watched_live_new (streamId, profileId, name, streamIcon, categoryId, num, watchedAt)
            SELECT streamId, 1, name, streamIcon, categoryId, num, watchedAt FROM recently_watched_live
            """.trimIndent()
        )
        db.execSQL("DROP TABLE recently_watched_live")
        db.execSQL("ALTER TABLE recently_watched_live_new RENAME TO recently_watched_live")
    }
}

/**
 * MIGRATION_10_11 (Phase 29 - préférence audio/sous-titres par média) :
 * crée la table `track_preferences`. Aucune donnée existante à transformer
 * (l'ancienne préférence globale reste dans SettingsManager comme fallback).
 */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS track_preferences (
                profileId INTEGER NOT NULL,
                mediaType TEXT NOT NULL,
                mediaId INTEGER NOT NULL,
                audioLang TEXT,
                subtitleLang TEXT,
                PRIMARY KEY(profileId, mediaType, mediaId)
            )
            """.trimIndent()
        )
    }
}

/**
 * MIGRATION_11_12 (Phase 40 - recherche globale plein texte) :
 * crée 3 tables virtuelles FTS4 (une par type de média) utilisées par la
 * recherche unifiée à la place de `LIKE '%x%'` (non indexable, full scan
 * à chaque frappe). Déclarées en @Entity (LiveStreamFtsEntity/VodStreamFtsEntity/
 * SeriesStreamFtsEntity) pour que Room valide les @Query au compile-time et
 * les crée automatiquement sur une installation neuve ; cette migration ne
 * sert qu'à amener une DB déjà existante (< v12) au même schéma, plus le
 * backfill depuis les tables sources déjà peuplées.
 *
 * Le rowid de chaque table FTS est backfillé sur la clé primaire de la
 * table source (streamId/seriesId), qui est un INTEGER PRIMARY KEY donc
 * alias du rowid SQLite : la jointure `fts.rowid = source.streamId` est
 * directe, sans colonne supplémentaire.
 *
 * Pas de triggers de synchronisation automatique (FTS4 external content) :
 * la synchro était faite explicitement côté app à chaque écriture, ce qui
 * restait cohérent avec le pattern clear+insert déjà utilisé par les
 * repositories pour les tables sources.
 *
 * OBSOLÈTE depuis MIGRATION_20_21 : les 3 tables FTS4 y sont supprimées au
 * profit d'une colonne `searchText` sur chaque table source, et les méthodes
 * DAO décrites ci-dessus (`insertStreamsWithFts`/`clearAllFts`/
 * `clearFtsByCategory`) n'existent plus. L'écriture du catalogue passe
 * aujourd'hui par `insertStreams` (@Transaction, calcule `searchText`) et
 * `insertStreamsRaw` (@Upsert). Ce bloc ne décrit que l'état du schéma en
 * v12 ; ne pas s'en servir comme référence de l'API DAO courante.
 */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS live_streams_fts USING FTS4(name, categoryId)")
        db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS vod_streams_fts USING FTS4(name, actors, director, genre, categoryId)")
        db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS series_streams_fts USING FTS4(name, actors, director, genre, categoryId)")

        db.execSQL(
            "INSERT INTO live_streams_fts(rowid, name, categoryId) SELECT streamId, name, categoryId FROM live_streams"
        )
        db.execSQL(
            """
            INSERT INTO vod_streams_fts(rowid, name, actors, director, genre, categoryId)
            SELECT streamId, name, actors, director, genre, categoryId FROM vod_streams
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO series_streams_fts(rowid, name, actors, director, genre, categoryId)
            SELECT seriesId, name, actors, director, genre, categoryId FROM series_streams
            """.trimIndent()
        )
    }
}

/**
 * MIGRATION_12_13 (Phase 58 - gestion des catégories par profil) :
 * crée la table `category_preferences` (masquage + ordre personnalisé des
 * catégories Live/VOD/Séries, scopés par profil). Aucune donnée existante à
 * transformer : une catégorie sans ligne reste visible à l'ordre API.
 */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS category_preferences (
                categoryId TEXT NOT NULL,
                type TEXT NOT NULL,
                profileId INTEGER NOT NULL,
                hidden INTEGER NOT NULL,
                sortOrder INTEGER,
                PRIMARY KEY(categoryId, type, profileId)
            )
            """.trimIndent()
        )
    }
}

/**
 * MIGRATION_13_14 (Phase 61 - téléchargement hors-ligne, feature #15) :
 * crée la table `downloaded_media` (métadonnées + statut/progression des
 * médias téléchargés). Téléchargements globaux : pas de `profileId`. Aucune
 * donnée existante à transformer.
 */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS downloaded_media (
                contentId TEXT NOT NULL,
                type TEXT NOT NULL,
                streamId INTEGER NOT NULL,
                seriesId INTEGER,
                seasonNum INTEGER,
                episodeNum INTEGER,
                title TEXT NOT NULL,
                subtitle TEXT,
                coverUrl TEXT,
                containerExtension TEXT NOT NULL,
                status TEXT NOT NULL,
                percent INTEGER NOT NULL,
                bytesDownloaded INTEGER NOT NULL,
                totalBytes INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                PRIMARY KEY(contentId)
            )
            """.trimIndent()
        )
    }
}

/**
 * MIGRATION_14_15 (Tri par ordre brut de l'API pour les films et séries) :
 * Ajoute la colonne `orderIndex` aux tables `vod_streams` et `series_streams`
 * afin de préserver l'ordre d'origine renvoyé par le serveur.
 */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE vod_streams ADD COLUMN orderIndex INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE series_streams ADD COLUMN orderIndex INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * MIGRATION_15_16 (Recherche avancée - filtre année de sortie) :
 * Ajoute la colonne nullable `releaseYear` aux tables `vod_streams` et
 * `series_streams`. Elle est backfillée par l'enrichissement background
 * existant (get_vod_info / get_series_info) qui parse `releasedate` via
 * ReleaseYearParser. Aucune valeur par défaut : `null` = pas encore enrichi.
 */
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE vod_streams ADD COLUMN releaseYear INTEGER")
        db.execSQL("ALTER TABLE series_streams ADD COLUMN releaseYear INTEGER")
    }
}

val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS media_ratings (
                profileId INTEGER NOT NULL,
                mediaType TEXT NOT NULL,
                mediaId INTEGER NOT NULL,
                value INTEGER NOT NULL,
                PRIMARY KEY(profileId, mediaType, mediaId)
            )
        """.trimIndent())
    }
}

/**
 * T4 : le cache Room devient la source unique de vérité et doit survivre à une
 * absence de réseau. La migration n'utilise que des `CREATE TABLE` et des
 * `ALTER TABLE ADD COLUMN` de colonnes nullables — les deux formes de migration
 * SQLite qui ne peuvent pas perdre de données existantes. Aucune recopie de
 * table, donc aucun risque de se tromper silencieusement de colonne.
 *
 * Seule exception : `epg_cache` passe à une clé primaire composite, impossible
 * par `ALTER TABLE`. Le patron `_new` + `INSERT … SELECT` n'a pas lieu d'être
 * ici parce que c'est du cache pur et jetable, périmé sous le quart d'heure et
 * reconstruit au premier `get_short_epg`. Catalogue, profils, favoris,
 * historique, positions et téléchargements sont intouchés.
 */
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Fraîcheur par section et par compte (remplace les clés *_ALL_SYNCED_AT
        //    de SettingsManager). Créée vide : la reprise des horodatages existants
        //    ne peut pas se faire en SQL, elle est faite par CatalogSyncStateInitializer.
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS catalog_sync_state (
                section TEXT NOT NULL,
                accountKey TEXT NOT NULL,
                lastSuccessAt INTEGER NOT NULL DEFAULT 0,
                lastAttemptAt INTEGER NOT NULL DEFAULT 0,
                lastFailureAt INTEGER NOT NULL DEFAULT 0,
                lastFailureKind TEXT,
                itemCount INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(section)
            )
            """.trimIndent()
        )

        // 2. Détail film complet hors ligne. containerExtension est la colonne
        //    critique : sans elle, la fiche de repli devine "mp4" et une reprise
        //    hors ligne sur un .mkv échoue.
        db.execSQL("ALTER TABLE vod_streams ADD COLUMN plot TEXT")
        db.execSQL("ALTER TABLE vod_streams ADD COLUMN duration TEXT")
        db.execSQL("ALTER TABLE vod_streams ADD COLUMN containerExtension TEXT")
        db.execSQL("ALTER TABLE vod_streams ADD COLUMN detailsCachedAt INTEGER")

        // 3. Détail série.
        db.execSQL("ALTER TABLE series_streams ADD COLUMN plot TEXT")
        db.execSQL("ALTER TABLE series_streams ADD COLUMN detailsCachedAt INTEGER")

        // 4. Saisons et épisodes, peuplés à la consultation d'une fiche en ligne.
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS series_seasons (
                seriesId INTEGER NOT NULL,
                seasonNumber INTEGER NOT NULL,
                name TEXT NOT NULL,
                episodeCount INTEGER NOT NULL DEFAULT 0,
                cover TEXT,
                cachedAt INTEGER NOT NULL,
                PRIMARY KEY(seriesId, seasonNumber)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS series_episodes (
                episodeId INTEGER NOT NULL,
                seriesId INTEGER NOT NULL,
                seasonNum INTEGER NOT NULL,
                episodeNum INTEGER NOT NULL,
                title TEXT NOT NULL,
                containerExtension TEXT NOT NULL,
                plot TEXT,
                duration TEXT,
                releaseDate TEXT,
                movieImage TEXT,
                orderIndex INTEGER NOT NULL DEFAULT 0,
                cachedAt INTEGER NOT NULL,
                PRIMARY KEY(episodeId)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_series_episodes_seriesId_seasonNum " +
                "ON series_episodes(seriesId, seasonNum)"
        )

        // 5. Fenêtre EPG : clé composite (streamId, startTimestamp) au lieu du
        //    streamId seul, qui ne mémorisait qu'un unique programme par chaîne.
        db.execSQL("DROP TABLE IF EXISTS epg_cache")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS epg_cache (
                streamId INTEGER NOT NULL,
                startTimestamp INTEGER NOT NULL,
                endTimestamp INTEGER NOT NULL,
                title TEXT NOT NULL,
                description TEXT,
                cachedAt INTEGER NOT NULL,
                PRIMARY KEY(streamId, startTimestamp)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_epg_cache_streamId_endTimestamp " +
                "ON epg_cache(streamId, endTimestamp)"
        )
    }
}

/**
 * MIGRATION_18_19 (appariement TMDB par année) : index sur `releaseYear` des
 * tables `vod_streams` et `series_streams`. L'appariement des tendances ne
 * charge plus tout le catalogue mais seulement les titres de l'année cherchée
 * et ceux dont l'année n'est pas encore enrichie.
 *
 * Purement additif : aucun changement de colonne ni de clé primaire, donc
 * aucune recopie de table. Les noms d'index reprennent la convention Room
 * (`index_<table>_<colonne>`), sans quoi la validation de schéma échouerait au
 * premier accès.
 */
val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS index_vod_streams_releaseYear ON vod_streams(releaseYear)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_series_streams_releaseYear ON series_streams(releaseYear)")
    }
}

val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS trailer_cache (mediaType TEXT NOT NULL, catalogId INTEGER NOT NULL, videoId TEXT, source TEXT, resolvedTmdbId INTEGER, resolvedAt INTEGER NOT NULL, PRIMARY KEY(mediaType, catalogId))")
    }
}

internal val SEARCH_FOLD_MAP = listOf(
    "à" to "a", "á" to "a", "â" to "a", "ã" to "a", "ä" to "a", "å" to "a",
    "è" to "e", "é" to "e", "ê" to "e", "ë" to "e", "ì" to "i", "í" to "i", "î" to "i", "ï" to "i",
    "ò" to "o", "ó" to "o", "ô" to "o", "õ" to "o", "ö" to "o", "ø" to "o",
    "ù" to "u", "ú" to "u", "û" to "u", "ü" to "u", "ý" to "y", "ÿ" to "y", "ñ" to "n", "ç" to "c",
    "œ" to "oe", "æ" to "ae", "ß" to "ss", "ł" to "l", "đ" to "d"
)

internal fun searchFoldSql(expression: String): String =
    "lower(" + SEARCH_FOLD_MAP.fold(expression) { value, (from, to) ->
        "replace(replace($value, '$from', '$to'), '${from.uppercase()}', '$to')"
    } + ")"

val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE live_streams ADD COLUMN searchText TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE vod_streams ADD COLUMN searchText TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE series_streams ADD COLUMN searchText TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE live_streams SET searchText = ${searchFoldSql("name || char(10) || categoryId")}")
        val catalogFields = "name || char(10) || ifnull(actors,'') || char(10) || ifnull(director,'') || char(10) || ifnull(genre,'') || char(10) || categoryId"
        db.execSQL("UPDATE vod_streams SET searchText = ${searchFoldSql(catalogFields)}")
        db.execSQL("UPDATE series_streams SET searchText = ${searchFoldSql(catalogFields)}")
        db.execSQL("DROP TABLE IF EXISTS live_streams_fts")
        db.execSQL("DROP TABLE IF EXISTS vod_streams_fts")
        db.execSQL("DROP TABLE IF EXISTS series_streams_fts")
    }
}

/**
 * MIGRATION_21_22 (F12 - notification de nouveaux épisodes) : crée la table
 * `series_watch_state`, qui mémorise par (profil, série) le dernier épisode
 * connu du catalogue et le dernier épisode notifié. Création de table pure,
 * sans copie de données ni changement de colonne existante.
 */
val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS series_watch_state (
                profileId INTEGER NOT NULL,
                seriesId INTEGER NOT NULL,
                lastKnownSeason INTEGER NOT NULL,
                lastKnownEpisode INTEGER NOT NULL,
                lastNotifiedSeason INTEGER NOT NULL,
                lastNotifiedEpisode INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                PRIMARY KEY(profileId, seriesId)
            )
            """.trimIndent()
        )
    }
}

/**
 * MIGRATION_22_23 (T9) : mémorise la catégorie d'une reprise pour que
 * l'Accueil puisse respecter les catégories masquées sans relire les deux
 * catalogues complets. La colonne est additive et le backfill s'exécute une
 * seule fois sur les positions existantes.
 */
val MIGRATION_22_23 = object : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE playback_positions ADD COLUMN categoryId TEXT")
        db.execSQL(
            "UPDATE playback_positions SET categoryId = (" +
                "SELECT v.categoryId FROM vod_streams v WHERE v.streamId = playback_positions.streamId" +
            ") WHERE seriesId IS NULL"
        )
        db.execSQL(
            "UPDATE playback_positions SET categoryId = (" +
                "SELECT s.categoryId FROM series_streams s WHERE s.seriesId = playback_positions.seriesId" +
            ") WHERE seriesId IS NOT NULL"
        )
    }
}

/**
 * MIGRATION_23_24 (latence de l'onglet « Tout ») : ajoute `categoryRank` aux
 * deux tables de catalogue et les index qui permettent de servir les rangées
 * sans parcourir toute la table.
 *
 * Sur Android TV, `SELECT … FROM vod_streams ORDER BY orderIndex` mettait
 * 2 secondes pour 38 947 lignes : aucun index ne portait `orderIndex`, donc
 * SQLite balayait la table entière — colonnes `plot`/`searchText` comprises —
 * puis construisait un tri B-tree temporaire. Trois changements l'évitent :
 *
 * 1. `categoryRank`, le rang d'un flux dans sa catégorie, permet de plafonner
 *    la requête à cent éléments par catégorie (voir `ALL_MODE_ROWS_PER_CATEGORY`) ;
 * 2. l'index couvrant sur `categoryRank` porte les huit colonnes de la
 *    projection de liste : la requête se résout dans l'index, sans accès table
 *    ni tri ;
 * 3. l'index `(categoryId, orderIndex)` fait de même pour la vue d'une seule
 *    catégorie, jusqu'ici logée à la même enseigne.
 *
 * Purement additif : une colonne avec valeur par défaut et trois index, donc
 * aucune recopie de table. Les noms d'index reprennent la convention Room
 * (`index_<table>_<colonnes jointes par _>`), sans quoi la validation de schéma
 * échouerait au premier accès.
 *
 * Le remplissage de `categoryRank` s'appuie sur `ROW_NUMBER()`, absent des
 * SQLite antérieurs à 3.25 (Android < 11). Sur ces appareils la colonne reste à
 * 0 : toutes les lignes passent alors le filtre `categoryRank < 100`, ce qui
 * redonne exactement le comportement actuel — sans régression — jusqu'à la
 * première synchronisation du catalogue, qui écrit les vrais rangs.
 */
val MIGRATION_23_24 = object : Migration(23, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE vod_streams ADD COLUMN categoryRank INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE series_streams ADD COLUMN categoryRank INTEGER NOT NULL DEFAULT 0")

        db.execSQL(
            "CREATE INDEX IF NOT EXISTS " +
                "index_vod_streams_categoryRank_streamId_name_streamIcon_rating_added_categoryId_genre_releaseYear " +
                "ON vod_streams(categoryRank, streamId, name, streamIcon, rating, added, categoryId, genre, releaseYear)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_vod_streams_categoryId_orderIndex ON vod_streams(categoryId, orderIndex)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS " +
                "index_series_streams_categoryRank_seriesId_name_cover_rating_added_categoryId_genre_releaseYear " +
                "ON series_streams(categoryRank, seriesId, name, cover, rating, added, categoryId, genre, releaseYear)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_series_streams_categoryId_orderIndex ON series_streams(categoryId, orderIndex)")

        if (!supportsWindowFunctions(db)) return
        categoryRankBackfillStatements(table = "vod_streams", keyColumn = "streamId").forEach(db::execSQL)
        categoryRankBackfillStatements(table = "series_streams", keyColumn = "seriesId").forEach(db::execSQL)
    }
}

/**
 * `ROW_NUMBER() OVER (PARTITION BY …)` n'existe qu'à partir de SQLite 3.25,
 * livré avec Android 11. Le `minSdk` du projet étant 21, la version est lue à
 * l'exécution plutôt que déduite du niveau d'API — le SQLite embarqué n'est pas
 * strictement lié à ce dernier.
 */
private fun supportsWindowFunctions(db: SupportSQLiteDatabase): Boolean =
    db.query("SELECT sqlite_version()").use { cursor ->
        if (!cursor.moveToFirst()) return false
        val parts = cursor.getString(0).split('.')
        val major = parts.getOrNull(0)?.toIntOrNull() ?: return false
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: return false
        major > 3 || (major == 3 && minor >= 25)
    }

/**
 * Instructions remplissant `categoryRank` en une passe, à exécuter dans
 * l'ordre. La table temporaire est indispensable : un
 * `UPDATE … SET x = (SELECT … ROW_NUMBER() …)` corrélé rematérialiserait la
 * sous-requête pour chacune des 39 000 lignes.
 *
 * Renvoyées plutôt qu'exécutées ici pour que `MigrationsSqlTest` puisse les
 * rejouer sur un SQLite en mémoire — le projet n'a pas d'infrastructure de test
 * instrumenté pour valider les migrations (voir AGENTS.md).
 */
internal fun categoryRankBackfillStatements(
    table: String,
    keyColumn: String,
    orderColumn: String = "orderIndex"
): List<String> = listOf(
    "DROP TABLE IF EXISTS temp.catalog_rank",
    "CREATE TEMP TABLE catalog_rank AS SELECT $keyColumn AS id, " +
        "ROW_NUMBER() OVER (PARTITION BY categoryId ORDER BY $orderColumn) - 1 AS rk FROM $table",
    "CREATE INDEX temp.index_catalog_rank_id ON catalog_rank(id)",
    "UPDATE $table SET categoryRank = " +
        "ifnull((SELECT rk FROM temp.catalog_rank WHERE id = $table.$keyColumn), 0)",
    "DROP TABLE temp.catalog_rank"
)

/**
 * MIGRATION_24_25 : applique à `live_streams` le traitement que
 * [MIGRATION_23_24] a réservé aux catalogues VOD et séries.
 *
 * La table n'avait aucun index, et `SELECT * … ORDER BY num` la parcourait donc
 * intégralement avant de trier. À 4 347 chaînes le coût restait supportable
 * (178 ms mesurées), mais le plan était le même que celui qui coûtait deux
 * secondes en VOD : il grandit avec le bouquet.
 *
 * Seule différence avec les catalogues : le rang suit `num`, le numéro de
 * chaîne, et non l'ordre de la réponse — c'est `num` qui décide de l'affichage.
 * Même repli que pour la migration précédente si `ROW_NUMBER()` manque.
 */
val MIGRATION_24_25 = object : Migration(24, 25) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE live_streams ADD COLUMN categoryRank INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS " +
                "index_live_streams_categoryRank_streamId_name_streamIcon_epgChannelId_num_categoryId " +
                "ON live_streams(categoryRank, streamId, name, streamIcon, epgChannelId, num, categoryId)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_live_streams_categoryId_num ON live_streams(categoryId, num)")

        if (!supportsWindowFunctions(db)) return
        categoryRankBackfillStatements(table = "live_streams", keyColumn = "streamId", orderColumn = "num")
            .forEach(db::execSQL)
    }
}

/** F33: keeps local profile keys stable while associating them with CSTV UUIDs. */
val MIGRATION_25_26 = object : Migration(25, 26) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE profiles ADD COLUMN remoteId TEXT")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_profiles_remoteId ON profiles(remoteId)")
    }
}

/** F34: durable ETags, merge base and pending marker for every namespace. */
val MIGRATION_26_27 = object : Migration(26, 27) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS profile_sync_state (
                profileId INTEGER NOT NULL,
                namespace TEXT NOT NULL,
                etag TEXT,
                schemaVersion INTEGER NOT NULL,
                baseSnapshot BLOB,
                pending INTEGER NOT NULL,
                lastSyncedAt INTEGER NOT NULL,
                lastAttemptAt INTEGER NOT NULL,
                failureCode TEXT,
                retryCount INTEGER NOT NULL,
                PRIMARY KEY(profileId, namespace)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_profile_sync_state_profileId ON profile_sync_state(profileId)")
    }
}

/**
 * T20 — normalisation relationnelle. Reconstruit les huit tables d'état autour d'identités
 * partagées (`media_refs` / `category_refs`), pose de vraies clés étrangères vers `profiles` et
 * vers ces identités, ajoute les cascades catalogue (`series_seasons`, `series_episodes`,
 * `epg_cache`) et celle de `profile_sync_state`, retire les métadonnées dupliquées et supprime les
 * lignes déjà orphelines de Room 27. Migration unique, non destructive : `defer_foreign_keys`
 * repousse la vérification des clés étrangères à la fin de la transaction, et un
 * `PRAGMA foreign_key_check` explicite fait échouer la migration (sans rien écrire, Room 27 reste
 * ouvrable) au moindre résidu incohérent plutôt que de laisser SQLite l'accepter silencieusement.
 *
 * Sentinelle `accountKey = ''` : le compte Xtream vit dans des préférences chiffrées hors de Room,
 * illisible depuis une migration SQL. Les identités héritées sont rattachées au premier compte
 * authentifié après la mise à jour par [com.cstv.app.data.sync.MediaRefAccountBinder] — jusque-là
 * elles restent invisibles (accord "cloud restauré avant le catalogue", § 4.6/4.7 du ticket).
 */
val MIGRATION_27_28 = object : Migration(27, 28) {
    override fun migrate(db: SupportSQLiteDatabase) {
        migration27To28Statements().forEach(db::execSQL)

        db.query("PRAGMA foreign_key_check").use { cursor ->
            if (cursor.moveToFirst()) {
                error("T20 migration 27->28 left a foreign key violation: PRAGMA foreign_key_check returned rows.")
            }
        }
    }
}

/** T21: schema-only migration. Existing catalogue rows are normalized lazily by WorkManager. */
val MIGRATION_28_29 = object : Migration(28, 29) {
    override fun migrate(db: SupportSQLiteDatabase) {
        migration28To29Statements().forEach(db::execSQL)
    }
}

internal fun migration28To29Statements(): List<String> = listOf(
    "ALTER TABLE live_streams ADD COLUMN cleanTitle TEXT NOT NULL DEFAULT ''",
    "ALTER TABLE live_streams ADD COLUMN linkKey TEXT NOT NULL DEFAULT ''",
    "ALTER TABLE live_streams ADD COLUMN languageTag TEXT",
    "ALTER TABLE live_streams ADD COLUMN languageRaw TEXT",
    "ALTER TABLE live_streams ADD COLUMN qualityTag TEXT",
    "ALTER TABLE live_streams ADD COLUMN qualityRaw TEXT",
    "CREATE INDEX IF NOT EXISTS index_live_streams_linkKey ON live_streams(linkKey)",
    "ALTER TABLE vod_streams ADD COLUMN cleanTitle TEXT NOT NULL DEFAULT ''",
    "ALTER TABLE vod_streams ADD COLUMN linkKey TEXT NOT NULL DEFAULT ''",
    "ALTER TABLE vod_streams ADD COLUMN languageTag TEXT",
    "ALTER TABLE vod_streams ADD COLUMN languageRaw TEXT",
    "ALTER TABLE vod_streams ADD COLUMN qualityTag TEXT",
    "ALTER TABLE vod_streams ADD COLUMN qualityRaw TEXT",
    "CREATE INDEX IF NOT EXISTS index_vod_streams_linkKey ON vod_streams(linkKey)",
    "ALTER TABLE series_streams ADD COLUMN cleanTitle TEXT NOT NULL DEFAULT ''",
    "ALTER TABLE series_streams ADD COLUMN linkKey TEXT NOT NULL DEFAULT ''",
    "ALTER TABLE series_streams ADD COLUMN languageTag TEXT",
    "ALTER TABLE series_streams ADD COLUMN languageRaw TEXT",
    "ALTER TABLE series_streams ADD COLUMN qualityTag TEXT",
    "ALTER TABLE series_streams ADD COLUMN qualityRaw TEXT",
    "CREATE INDEX IF NOT EXISTS index_series_streams_linkKey ON series_streams(linkKey)"
)

/**
 * Instructions de la migration 27→28, renvoyées plutôt qu'exécutées ici pour que
 * `Migration27To28SqlTest` puisse les rejouer sur un SQLite en mémoire via sqlite-jdbc — motif déjà
 * utilisé par [categoryRankBackfillStatements] (le projet n'a pas d'infrastructure de test
 * instrumenté, voir AGENTS.md).
 */
internal fun migration27To28Statements(): List<String> = listOf(
    "PRAGMA defer_foreign_keys = TRUE",

    // --- Tables d'identité (idempotentes : peuvent déjà exister, cf. AppModule) ---
    "CREATE TABLE IF NOT EXISTS media_refs (mediaUid INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, accountKey TEXT NOT NULL, kind TEXT NOT NULL, providerId INTEGER NOT NULL)",
    "CREATE UNIQUE INDEX IF NOT EXISTS index_media_refs_accountKey_kind_providerId ON media_refs(accountKey, kind, providerId)",
    "CREATE TABLE IF NOT EXISTS category_refs (catUid INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, accountKey TEXT NOT NULL, kind TEXT NOT NULL, providerCategoryId TEXT NOT NULL)",
    "CREATE UNIQUE INDEX IF NOT EXISTS index_category_refs_accountKey_kind_providerCategoryId ON category_refs(accountKey, kind, providerCategoryId)",
    "CREATE TABLE IF NOT EXISTS db_maintenance (task TEXT NOT NULL, requestedAt INTEGER NOT NULL, PRIMARY KEY(task))",

    // --- Peuplement des identités depuis les états existants (accountKey = '', rattachée plus tard) ---
    "INSERT OR IGNORE INTO media_refs (accountKey, kind, providerId) SELECT '', type, id FROM favorites",
    "INSERT OR IGNORE INTO media_refs (accountKey, kind, providerId) SELECT '', " +
        "CASE WHEN seriesId IS NOT NULL OR type = 'series' THEN 'episode' " +
        "WHEN EXISTS (SELECT 1 FROM series_episodes e WHERE e.episodeId = playback_positions.streamId) THEN 'episode' " +
        "ELSE 'movie' END, streamId FROM playback_positions",
    "INSERT OR IGNORE INTO media_refs (accountKey, kind, providerId) SELECT '', 'live', streamId FROM recently_watched_live",
    "INSERT OR IGNORE INTO media_refs (accountKey, kind, providerId) SELECT '', mediaType, mediaId FROM media_ratings",
    "INSERT OR IGNORE INTO media_refs (accountKey, kind, providerId) SELECT '', mediaType, mediaId FROM track_preferences",
    "INSERT OR IGNORE INTO media_refs (accountKey, kind, providerId) SELECT '', 'series', seriesId FROM series_watch_state",
    "INSERT OR IGNORE INTO media_refs (accountKey, kind, providerId) SELECT '', type, streamId FROM downloaded_media",
    "INSERT OR IGNORE INTO category_refs (accountKey, kind, providerCategoryId) SELECT '', type, categoryId FROM category_preferences",

    // --- favorites: (id, type, name, cover, categoryId, addedAt, profileId) -> (profileId, mediaUid, addedAt) ---
    "CREATE TABLE favorites_new (" +
        "profileId INTEGER NOT NULL, mediaUid INTEGER NOT NULL, addedAt INTEGER NOT NULL, " +
        "PRIMARY KEY(profileId, mediaUid), " +
        "FOREIGN KEY(profileId) REFERENCES profiles(id) ON DELETE CASCADE, " +
        "FOREIGN KEY(mediaUid) REFERENCES media_refs(mediaUid) ON DELETE CASCADE)",
    "INSERT INTO favorites_new (profileId, mediaUid, addedAt) " +
        "SELECT f.profileId, r.mediaUid, f.addedAt FROM favorites f " +
        "JOIN profiles p ON p.id = f.profileId " +
        "JOIN media_refs r ON r.accountKey = '' AND r.kind = f.type AND r.providerId = f.id",
    "DROP TABLE favorites",
    "ALTER TABLE favorites_new RENAME TO favorites",
    "CREATE INDEX index_favorites_mediaUid ON favorites(mediaUid)",
    "CREATE INDEX index_favorites_profileId_addedAt ON favorites(profileId, addedAt)",

    // --- playback_positions: strips every catalogue field, keeps only resume state ---
    "CREATE TABLE playback_positions_new (" +
        "profileId INTEGER NOT NULL, mediaUid INTEGER NOT NULL, positionMs INTEGER NOT NULL, " +
        "durationMs INTEGER NOT NULL, lastAccessedAt INTEGER NOT NULL, " +
        "PRIMARY KEY(profileId, mediaUid), " +
        "FOREIGN KEY(profileId) REFERENCES profiles(id) ON DELETE CASCADE, " +
        "FOREIGN KEY(mediaUid) REFERENCES media_refs(mediaUid) ON DELETE CASCADE)",
    "INSERT INTO playback_positions_new (profileId, mediaUid, positionMs, durationMs, lastAccessedAt) " +
        "SELECT pp.profileId, r.mediaUid, pp.positionMs, pp.durationMs, pp.lastAccessedAt FROM playback_positions pp " +
        "JOIN profiles p ON p.id = pp.profileId " +
        "JOIN media_refs r ON r.accountKey = '' AND r.providerId = pp.streamId AND r.kind = " +
        "(CASE WHEN pp.seriesId IS NOT NULL OR pp.type = 'series' THEN 'episode' " +
        "WHEN EXISTS (SELECT 1 FROM series_episodes e WHERE e.episodeId = pp.streamId) THEN 'episode' " +
        "ELSE 'movie' END)",
    "DROP TABLE playback_positions",
    "ALTER TABLE playback_positions_new RENAME TO playback_positions",
    "CREATE INDEX index_playback_positions_mediaUid ON playback_positions(mediaUid)",
    "CREATE INDEX index_playback_positions_profileId_lastAccessedAt ON playback_positions(profileId, lastAccessedAt)",

    // --- recently_watched_live ---
    "CREATE TABLE recently_watched_live_new (" +
        "profileId INTEGER NOT NULL, mediaUid INTEGER NOT NULL, watchedAt INTEGER NOT NULL, " +
        "PRIMARY KEY(profileId, mediaUid), " +
        "FOREIGN KEY(profileId) REFERENCES profiles(id) ON DELETE CASCADE, " +
        "FOREIGN KEY(mediaUid) REFERENCES media_refs(mediaUid) ON DELETE CASCADE)",
    "INSERT INTO recently_watched_live_new (profileId, mediaUid, watchedAt) " +
        "SELECT rw.profileId, r.mediaUid, rw.watchedAt FROM recently_watched_live rw " +
        "JOIN profiles p ON p.id = rw.profileId " +
        "JOIN media_refs r ON r.accountKey = '' AND r.kind = 'live' AND r.providerId = rw.streamId",
    "DROP TABLE recently_watched_live",
    "ALTER TABLE recently_watched_live_new RENAME TO recently_watched_live",
    "CREATE INDEX index_recently_watched_live_mediaUid ON recently_watched_live(mediaUid)",
    "CREATE INDEX index_recently_watched_live_profileId_watchedAt ON recently_watched_live(profileId, watchedAt)",

    // --- media_ratings ---
    "CREATE TABLE media_ratings_new (" +
        "profileId INTEGER NOT NULL, mediaUid INTEGER NOT NULL, value INTEGER NOT NULL, " +
        "PRIMARY KEY(profileId, mediaUid), " +
        "FOREIGN KEY(profileId) REFERENCES profiles(id) ON DELETE CASCADE, " +
        "FOREIGN KEY(mediaUid) REFERENCES media_refs(mediaUid) ON DELETE CASCADE)",
    "INSERT INTO media_ratings_new (profileId, mediaUid, value) " +
        "SELECT mr.profileId, r.mediaUid, mr.value FROM media_ratings mr " +
        "JOIN profiles p ON p.id = mr.profileId " +
        "JOIN media_refs r ON r.accountKey = '' AND r.kind = mr.mediaType AND r.providerId = mr.mediaId",
    "DROP TABLE media_ratings",
    "ALTER TABLE media_ratings_new RENAME TO media_ratings",
    "CREATE INDEX index_media_ratings_mediaUid ON media_ratings(mediaUid)",

    // --- track_preferences ---
    "CREATE TABLE track_preferences_new (" +
        "profileId INTEGER NOT NULL, mediaUid INTEGER NOT NULL, audioLang TEXT, subtitleLang TEXT, " +
        "PRIMARY KEY(profileId, mediaUid), " +
        "FOREIGN KEY(profileId) REFERENCES profiles(id) ON DELETE CASCADE, " +
        "FOREIGN KEY(mediaUid) REFERENCES media_refs(mediaUid) ON DELETE CASCADE)",
    "INSERT INTO track_preferences_new (profileId, mediaUid, audioLang, subtitleLang) " +
        "SELECT tp.profileId, r.mediaUid, tp.audioLang, tp.subtitleLang FROM track_preferences tp " +
        "JOIN profiles p ON p.id = tp.profileId " +
        "JOIN media_refs r ON r.accountKey = '' AND r.kind = tp.mediaType AND r.providerId = tp.mediaId",
    "DROP TABLE track_preferences",
    "ALTER TABLE track_preferences_new RENAME TO track_preferences",
    "CREATE INDEX index_track_preferences_mediaUid ON track_preferences(mediaUid)",

    // --- series_watch_state ---
    "CREATE TABLE series_watch_state_new (" +
        "profileId INTEGER NOT NULL, mediaUid INTEGER NOT NULL, lastKnownSeason INTEGER NOT NULL, " +
        "lastKnownEpisode INTEGER NOT NULL, lastNotifiedSeason INTEGER NOT NULL, " +
        "lastNotifiedEpisode INTEGER NOT NULL, updatedAt INTEGER NOT NULL, " +
        "PRIMARY KEY(profileId, mediaUid), " +
        "FOREIGN KEY(profileId) REFERENCES profiles(id) ON DELETE CASCADE, " +
        "FOREIGN KEY(mediaUid) REFERENCES media_refs(mediaUid) ON DELETE CASCADE)",
    "INSERT INTO series_watch_state_new (" +
        "profileId, mediaUid, lastKnownSeason, lastKnownEpisode, lastNotifiedSeason, lastNotifiedEpisode, updatedAt) " +
        "SELECT sw.profileId, r.mediaUid, sw.lastKnownSeason, sw.lastKnownEpisode, sw.lastNotifiedSeason, " +
        "sw.lastNotifiedEpisode, sw.updatedAt FROM series_watch_state sw " +
        "JOIN profiles p ON p.id = sw.profileId " +
        "JOIN media_refs r ON r.accountKey = '' AND r.kind = 'series' AND r.providerId = sw.seriesId",
    "DROP TABLE series_watch_state",
    "ALTER TABLE series_watch_state_new RENAME TO series_watch_state",
    "CREATE INDEX index_series_watch_state_mediaUid ON series_watch_state(mediaUid)",

    // --- category_preferences ---
    "CREATE TABLE category_preferences_new (" +
        "profileId INTEGER NOT NULL, catUid INTEGER NOT NULL, hidden INTEGER NOT NULL, sortOrder INTEGER, " +
        "PRIMARY KEY(profileId, catUid), " +
        "FOREIGN KEY(profileId) REFERENCES profiles(id) ON DELETE CASCADE, " +
        "FOREIGN KEY(catUid) REFERENCES category_refs(catUid) ON DELETE CASCADE)",
    "INSERT INTO category_preferences_new (profileId, catUid, hidden, sortOrder) " +
        "SELECT cp.profileId, cr.catUid, cp.hidden, cp.sortOrder FROM category_preferences cp " +
        "JOIN profiles p ON p.id = cp.profileId " +
        "JOIN category_refs cr ON cr.accountKey = '' AND cr.kind = cp.type AND cr.providerCategoryId = cp.categoryId",
    "DROP TABLE category_preferences",
    "ALTER TABLE category_preferences_new RENAME TO category_preferences",
    "CREATE INDEX index_category_preferences_catUid ON category_preferences(catUid)",

    // --- downloaded_media: global (no profileId), contentId becomes derived (DownloadContentId) ---
    "CREATE TABLE downloaded_media_new (" +
        "mediaUid INTEGER NOT NULL PRIMARY KEY, status TEXT NOT NULL, percent INTEGER NOT NULL, " +
        "bytesDownloaded INTEGER NOT NULL, totalBytes INTEGER NOT NULL, createdAt INTEGER NOT NULL, " +
        "FOREIGN KEY(mediaUid) REFERENCES media_refs(mediaUid) ON DELETE CASCADE)",
    "INSERT INTO downloaded_media_new (mediaUid, status, percent, bytesDownloaded, totalBytes, createdAt) " +
        "SELECT r.mediaUid, dm.status, dm.percent, dm.bytesDownloaded, dm.totalBytes, dm.createdAt " +
        "FROM downloaded_media dm " +
        "JOIN media_refs r ON r.accountKey = '' AND r.kind = dm.type AND r.providerId = dm.streamId",
    "DROP TABLE downloaded_media",
    "ALTER TABLE downloaded_media_new RENAME TO downloaded_media",
    "CREATE UNIQUE INDEX index_downloaded_media_mediaUid ON downloaded_media(mediaUid)",

    // --- Cascades catalogue : une série/chaîne retirée du bouquet nettoie désormais ses enfants ---
    "CREATE TABLE series_seasons_new (" +
        "seriesId INTEGER NOT NULL, seasonNumber INTEGER NOT NULL, name TEXT NOT NULL, " +
        "episodeCount INTEGER NOT NULL, cover TEXT, cachedAt INTEGER NOT NULL, " +
        "PRIMARY KEY(seriesId, seasonNumber), " +
        "FOREIGN KEY(seriesId) REFERENCES series_streams(seriesId) ON DELETE CASCADE)",
    "INSERT INTO series_seasons_new (seriesId, seasonNumber, name, episodeCount, cover, cachedAt) " +
        "SELECT ss.seriesId, ss.seasonNumber, ss.name, ss.episodeCount, ss.cover, ss.cachedAt " +
        "FROM series_seasons ss JOIN series_streams s ON s.seriesId = ss.seriesId",
    "DROP TABLE series_seasons",
    "ALTER TABLE series_seasons_new RENAME TO series_seasons",
    "CREATE INDEX index_series_seasons_seriesId ON series_seasons(seriesId)",

    "CREATE TABLE series_episodes_new (" +
        "episodeId INTEGER NOT NULL PRIMARY KEY, seriesId INTEGER NOT NULL, seasonNum INTEGER NOT NULL, " +
        "episodeNum INTEGER NOT NULL, title TEXT NOT NULL, containerExtension TEXT NOT NULL, plot TEXT, " +
        "duration TEXT, releaseDate TEXT, movieImage TEXT, orderIndex INTEGER NOT NULL, cachedAt INTEGER NOT NULL, " +
        "FOREIGN KEY(seriesId) REFERENCES series_streams(seriesId) ON DELETE CASCADE)",
    "INSERT INTO series_episodes_new (episodeId, seriesId, seasonNum, episodeNum, title, containerExtension, " +
        "plot, duration, releaseDate, movieImage, orderIndex, cachedAt) " +
        "SELECT se.episodeId, se.seriesId, se.seasonNum, se.episodeNum, se.title, se.containerExtension, " +
        "se.plot, se.duration, se.releaseDate, se.movieImage, se.orderIndex, se.cachedAt " +
        "FROM series_episodes se JOIN series_streams s ON s.seriesId = se.seriesId",
    "DROP TABLE series_episodes",
    "ALTER TABLE series_episodes_new RENAME TO series_episodes",
    "CREATE INDEX index_series_episodes_seriesId_seasonNum ON series_episodes(seriesId, seasonNum)",

    "CREATE TABLE epg_cache_new (" +
        "streamId INTEGER NOT NULL, startTimestamp INTEGER NOT NULL, endTimestamp INTEGER NOT NULL, " +
        "title TEXT NOT NULL, description TEXT, cachedAt INTEGER NOT NULL, " +
        "PRIMARY KEY(streamId, startTimestamp), " +
        "FOREIGN KEY(streamId) REFERENCES live_streams(streamId) ON DELETE CASCADE)",
    "INSERT INTO epg_cache_new (streamId, startTimestamp, endTimestamp, title, description, cachedAt) " +
        "SELECT ec.streamId, ec.startTimestamp, ec.endTimestamp, ec.title, ec.description, ec.cachedAt " +
        "FROM epg_cache ec JOIN live_streams s ON s.streamId = ec.streamId",
    "DROP TABLE epg_cache",
    "ALTER TABLE epg_cache_new RENAME TO epg_cache",
    "CREATE INDEX index_epg_cache_streamId_endTimestamp ON epg_cache(streamId, endTimestamp)",

    // --- profile_sync_state: gains its profiles(id) cascade ---
    "CREATE TABLE profile_sync_state_new (" +
        "profileId INTEGER NOT NULL, namespace TEXT NOT NULL, etag TEXT, schemaVersion INTEGER NOT NULL, " +
        "baseSnapshot BLOB, pending INTEGER NOT NULL, lastSyncedAt INTEGER NOT NULL, lastAttemptAt INTEGER NOT NULL, " +
        "failureCode TEXT, retryCount INTEGER NOT NULL, " +
        "PRIMARY KEY(profileId, namespace), " +
        "FOREIGN KEY(profileId) REFERENCES profiles(id) ON DELETE CASCADE)",
    "INSERT INTO profile_sync_state_new (profileId, namespace, etag, schemaVersion, baseSnapshot, pending, " +
        "lastSyncedAt, lastAttemptAt, failureCode, retryCount) " +
        "SELECT pss.profileId, pss.namespace, pss.etag, pss.schemaVersion, pss.baseSnapshot, pss.pending, " +
        "pss.lastSyncedAt, pss.lastAttemptAt, pss.failureCode, pss.retryCount " +
        "FROM profile_sync_state pss JOIN profiles p ON p.id = pss.profileId",
    "DROP TABLE profile_sync_state",
    "ALTER TABLE profile_sync_state_new RENAME TO profile_sync_state",
    "CREATE INDEX index_profile_sync_state_profileId ON profile_sync_state(profileId)",

    // --- Identités sans aucun état référent : aucune raison de survivre ---
    "DELETE FROM media_refs WHERE mediaUid NOT IN (" +
        "SELECT mediaUid FROM favorites UNION SELECT mediaUid FROM playback_positions " +
        "UNION SELECT mediaUid FROM recently_watched_live UNION SELECT mediaUid FROM media_ratings " +
        "UNION SELECT mediaUid FROM track_preferences UNION SELECT mediaUid FROM series_watch_state " +
        "UNION SELECT mediaUid FROM downloaded_media)",
    "DELETE FROM category_refs WHERE catUid NOT IN (SELECT catUid FROM category_preferences)",

    // --- Compactage différé : VACUUM ne peut pas s'exécuter dans cette transaction ---
    "INSERT OR REPLACE INTO db_maintenance (task, requestedAt) VALUES ('vacuum', ${System.currentTimeMillis()})",
)

/**
 * T24 : table `canonical_media_links`, découplée de `vod_streams`/
 * `series_streams` (voir `CanonicalMediaLinkEntity` — un `canonicalId`
 * stocké directement sur ces tables serait écrasé à chaque sync catalogue,
 * qui les réécrit en intégralité via `@Upsert`).
 */
val MIGRATION_29_30 = object : Migration(29, 30) {
    override fun migrate(db: SupportSQLiteDatabase) {
        migration29To30Statements().forEach(db::execSQL)
    }
}

internal fun migration29To30Statements(): List<String> = listOf(
    "CREATE TABLE IF NOT EXISTS canonical_media_links (" +
        "kind TEXT NOT NULL, providerId INTEGER NOT NULL, canonicalId TEXT NOT NULL, " +
        "updatedAt INTEGER NOT NULL, PRIMARY KEY(kind, providerId))",
    "CREATE INDEX IF NOT EXISTS index_canonical_media_links_canonicalId ON canonical_media_links(canonicalId)"
)

/**
 * T23 : table `playback_repair_profiles`, une ligne par `mediaUid` (§8.5) — la configuration de
 * réparation dépend de l'appareil et du fichier, pas du profil (contrairement à
 * `track_preferences`), donc pas de `profileId` en clé composite.
 */
val MIGRATION_30_31 = object : Migration(30, 31) {
    override fun migrate(db: SupportSQLiteDatabase) {
        migration30To31Statements().forEach(db::execSQL)
    }
}

internal fun migration30To31Statements(): List<String> = listOf(
    "CREATE TABLE IF NOT EXISTS playback_repair_profiles (" +
        "mediaUid INTEGER NOT NULL PRIMARY KEY, decoderStrategy TEXT NOT NULL, " +
        "disabledTrackJson TEXT, preferredAudioJson TEXT, updatedAt INTEGER NOT NULL, " +
        "schemaVersion INTEGER NOT NULL, " +
        "FOREIGN KEY(mediaUid) REFERENCES media_refs(mediaUid) ON DELETE CASCADE)",
    "CREATE UNIQUE INDEX IF NOT EXISTS index_playback_repair_profiles_mediaUid ON playback_repair_profiles(mediaUid)"
)

/**
 * F39 : deux changements indépendants, réunis ici faute d'un second numéro
 * de version disponible avant livraison (règle T21 §8.5) —
 *
 * 1. **Badges de version (§8.4)** : l'index couvrant de l'onglet « Tout »
 *    (posé par [MIGRATION_23_24]) est étendu à `languageTag`/`qualityTag`
 *    pour que la projection élargie reste servie entièrement par l'index,
 *    sans tri temporaire ni accès table (voir `CoveringIndexF39SqlTest`).
 *    Recréer l'index sous le nom généré par Room pour la nouvelle liste de
 *    colonnes déclarée sur `VodStreamEntity`/`SeriesStreamEntity` — sinon la
 *    validation de schéma Room échoue au premier accès après migration.
 * 2. **Préférence de version série (§8.3)** : nouvelle table
 *    `series_version_preferences`, voir `SeriesVersionPreferenceEntity`.
 */
val MIGRATION_31_32 = object : Migration(31, 32) {
    override fun migrate(db: SupportSQLiteDatabase) {
        migration31To32Statements().forEach(db::execSQL)
    }
}

internal fun migration31To32Statements(): List<String> = listOf(
    "DROP INDEX IF EXISTS index_vod_streams_categoryRank_streamId_name_streamIcon_rating_added_categoryId_genre_releaseYear",
    "CREATE INDEX IF NOT EXISTS " +
        "index_vod_streams_categoryRank_streamId_name_streamIcon_rating_added_categoryId_genre_releaseYear_languageTag_qualityTag " +
        "ON vod_streams(categoryRank, streamId, name, streamIcon, rating, added, categoryId, genre, releaseYear, languageTag, qualityTag)",
    "DROP INDEX IF EXISTS index_series_streams_categoryRank_seriesId_name_cover_rating_added_categoryId_genre_releaseYear",
    "CREATE INDEX IF NOT EXISTS " +
        "index_series_streams_categoryRank_seriesId_name_cover_rating_added_categoryId_genre_releaseYear_languageTag_qualityTag " +
        "ON series_streams(categoryRank, seriesId, name, cover, rating, added, categoryId, genre, releaseYear, languageTag, qualityTag)",
    "CREATE TABLE IF NOT EXISTS series_version_preferences (" +
        "profileId INTEGER NOT NULL, linkKey TEXT NOT NULL, preferredSeriesId INTEGER NOT NULL, " +
        "updatedAt INTEGER NOT NULL, PRIMARY KEY(profileId, linkKey), " +
        "FOREIGN KEY(profileId) REFERENCES profiles(id) ON DELETE CASCADE)",
    "CREATE INDEX IF NOT EXISTS index_series_version_preferences_profileId ON series_version_preferences(profileId)",
    "CREATE INDEX IF NOT EXISTS index_series_version_preferences_linkKey ON series_version_preferences(linkKey)"
)

/**
 * F39 (évolution PO) : `versionLabel` remplace `languageTag`/`qualityTag` comme
 * source d'affichage des badges/sélecteurs — un seul champ, tous les fragments
 * reconnus du titre (langue, qualité, technique), dans l'ordre, jamais reformulés
 * (ex. `VO · STFR · 4K`). `languageTag`/`qualityTag` restent en base, inchangés,
 * uniquement pour le tri des versions par qualité (`mediaQualityRank`).
 *
 * Colonne ajoutée en queue sur `vod_streams`/`series_streams` (`ALTER TABLE`,
 * pas de changement de clé primaire, cf. AGENTS.md), index couvrant de l'onglet
 * « Tout » réétendu une nouvelle fois (voir migration 31→32) pour rester
 * couvrant sur la projection élargie.
 */
val MIGRATION_32_33 = object : Migration(32, 33) {
    override fun migrate(db: SupportSQLiteDatabase) {
        migration32To33Statements().forEach(db::execSQL)
    }
}

internal fun migration32To33Statements(): List<String> = listOf(
    "ALTER TABLE vod_streams ADD COLUMN versionLabel TEXT",
    "ALTER TABLE series_streams ADD COLUMN versionLabel TEXT",
    "DROP INDEX IF EXISTS index_vod_streams_categoryRank_streamId_name_streamIcon_rating_added_categoryId_genre_releaseYear_languageTag_qualityTag",
    "CREATE INDEX IF NOT EXISTS " +
        "index_vod_streams_categoryRank_streamId_name_streamIcon_rating_added_categoryId_genre_releaseYear_languageTag_qualityTag_versionLabel " +
        "ON vod_streams(categoryRank, streamId, name, streamIcon, rating, added, categoryId, genre, releaseYear, languageTag, qualityTag, versionLabel)",
    "DROP INDEX IF EXISTS index_series_streams_categoryRank_seriesId_name_cover_rating_added_categoryId_genre_releaseYear_languageTag_qualityTag",
    "CREATE INDEX IF NOT EXISTS " +
        "index_series_streams_categoryRank_seriesId_name_cover_rating_added_categoryId_genre_releaseYear_languageTag_qualityTag_versionLabel " +
        "ON series_streams(categoryRank, seriesId, name, cover, rating, added, categoryId, genre, releaseYear, languageTag, qualityTag, versionLabel)"
)

val ALL_MIGRATIONS = arrayOf(MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27, MIGRATION_27_28, MIGRATION_28_29, MIGRATION_29_30, MIGRATION_30_31, MIGRATION_31_32, MIGRATION_32_33)
