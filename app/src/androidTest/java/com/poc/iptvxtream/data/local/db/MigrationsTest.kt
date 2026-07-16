package com.poc.iptvxtream.data.local.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests instrumentés des migrations Room (voir AGENTS.md § "Base de données
 * Room") : chaque migration de Migrations.kt est vérifiée contre les schémas
 * JSON exportés dans app/schemas/ (couverture depuis la v9, première version
 * régénérée depuis l'historique git).
 *
 * Exécution : ./gradlew connectedDebugAndroidTest (émulateur/device requis).
 */
@RunWith(AndroidJUnit4::class)
class MigrationsTest {

    private val dbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    // ---------- 9 -> 10 : profils multiples ----------

    private fun seedV9(db: SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT INTO favorites (id, type, name, cover, categoryId, addedAt) " +
                "VALUES (42, 'live', 'Chaîne Test', NULL, 'cat1', 1000)"
        )
        db.execSQL(
            "INSERT INTO playback_positions (streamId, positionMs, durationMs, lastAccessedAt, title) " +
                "VALUES (7, 120000, 3600000, 2000, 'Film Test')"
        )
        db.execSQL(
            "INSERT INTO recently_watched_live (streamId, name, streamIcon, categoryId, num, watchedAt) " +
                "VALUES (9, 'TF1', NULL, 'cat1', 1, 3000)"
        )
    }

    @Test
    fun migrate9To10_preservesDataAndBackfillsProfileId() {
        helper.createDatabase(dbName, 9).apply {
            seedV9(this)
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 10, true, MIGRATION_9_10)

        // Un profil par défaut id=1 est créé.
        db.query("SELECT id, name FROM profiles").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals(1, c.getInt(0))
        }
        // Les données existantes sont rattachées au profil 1.
        db.query("SELECT id, name, profileId FROM favorites").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals(42, c.getInt(0))
            assertEquals("Chaîne Test", c.getString(1))
            assertEquals(1, c.getInt(2))
        }
        db.query("SELECT streamId, positionMs, profileId FROM playback_positions").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals(7, c.getInt(0))
            assertEquals(120000, c.getInt(1))
            assertEquals(1, c.getInt(2))
        }
        db.query("SELECT streamId, name, profileId FROM recently_watched_live").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals(9, c.getInt(0))
            assertEquals("TF1", c.getString(1))
            assertEquals(1, c.getInt(2))
        }
    }

    // ---------- 10 -> 11 : préférences de pistes ----------

    @Test
    fun migrate10To11_createsTrackPreferences() {
        helper.createDatabase(dbName, 10).close()

        val db = helper.runMigrationsAndValidate(dbName, 11, true, MIGRATION_10_11)

        db.query("SELECT COUNT(*) FROM track_preferences").use { c ->
            c.moveToFirst()
            assertEquals(0, c.getInt(0))
        }
    }

    // ---------- 11 -> 12 : tables FTS + backfill ----------

    private fun seedV11(db: SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT INTO live_streams (streamId, name, num, categoryId, cachedAt) " +
                "VALUES (1, 'France 2', 2, 'cat1', 100)"
        )
        db.execSQL(
            "INSERT INTO vod_streams (streamId, name, categoryId, cachedAt, actors, director, genre) " +
                "VALUES (2, 'Inception', 'cat2', 100, 'Leonardo DiCaprio', 'Christopher Nolan', 'Sci-Fi')"
        )
        db.execSQL(
            "INSERT INTO series_streams (seriesId, name, categoryId, cachedAt, actors, director, genre) " +
                "VALUES (3, 'Dark', 'cat3', 100, 'Louis Hofmann', 'Baran bo Odar', 'Thriller')"
        )
    }

    @Test
    fun migrate11To12_createsFtsTablesAndBackfills() {
        helper.createDatabase(dbName, 11).apply {
            seedV11(this)
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 12, true, MIGRATION_11_12)

        // Backfill : rowid = clé primaire de la table source.
        db.query("SELECT rowid, name FROM live_streams_fts").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals(1, c.getInt(0))
            assertEquals("France 2", c.getString(1))
        }
        db.query("SELECT rowid FROM vod_streams_fts WHERE vod_streams_fts MATCH 'nolan'").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals(2, c.getInt(0))
        }
        db.query("SELECT rowid FROM series_streams_fts WHERE series_streams_fts MATCH 'dark'").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals(3, c.getInt(0))
        }
    }

    // ---------- 9 -> 12 : chaîne complète ----------

    @Test
    fun migrateAll9To12() {
        helper.createDatabase(dbName, 9).apply {
            seedV9(this)
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 12, true, *ALL_MIGRATIONS)

        db.query("SELECT COUNT(*) FROM profiles").use { c ->
            c.moveToFirst()
            assertEquals(1, c.getInt(0))
        }
        db.query("SELECT profileId FROM favorites WHERE id = 42").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals(1, c.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM track_preferences").use { c ->
            c.moveToFirst()
            assertEquals(0, c.getInt(0))
        }
        // Tables FTS créées (vides : live_streams était vide en v9 seedé).
        db.query("SELECT COUNT(*) FROM live_streams_fts").use { c ->
            assertTrue(c.moveToFirst())
        }
    }
}
