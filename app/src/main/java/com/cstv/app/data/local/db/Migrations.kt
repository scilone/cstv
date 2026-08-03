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
 * la synchro est faite explicitement côté app à chaque écriture (voir
 * `insertStreamsWithFts`/`clearAllFts`/`clearFtsByCategory` dans les DAOs),
 * ce qui reste cohérent avec le pattern clear+insert déjà utilisé par les
 * repositories pour les tables sources.
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
internal fun categoryRankBackfillStatements(table: String, keyColumn: String): List<String> = listOf(
    "DROP TABLE IF EXISTS temp.catalog_rank",
    "CREATE TEMP TABLE catalog_rank AS SELECT $keyColumn AS id, " +
        "ROW_NUMBER() OVER (PARTITION BY categoryId ORDER BY orderIndex) - 1 AS rk FROM $table",
    "CREATE INDEX temp.index_catalog_rank_id ON catalog_rank(id)",
    "UPDATE $table SET categoryRank = " +
        "ifnull((SELECT rk FROM temp.catalog_rank WHERE id = $table.$keyColumn), 0)",
    "DROP TABLE temp.catalog_rank"
)

val ALL_MIGRATIONS = arrayOf(MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24)
