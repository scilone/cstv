package com.poc.iptvxtream.data.local.db

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

val ALL_MIGRATIONS = arrayOf(MIGRATION_9_10)
