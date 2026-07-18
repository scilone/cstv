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

val ALL_MIGRATIONS = arrayOf(MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16)
