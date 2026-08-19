package com.cstv.app.data.local.db

import java.sql.Connection
import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Hotfix 2026-08-19 : rejoue [migration34To35Statements] sur SQLite en
 * mémoire — même mécanisme que 33→34 ([Migration33To34SqlTest]). Vide
 * `linkKey`/tags sur `vod_streams`/`series_streams` pour que
 * [CatalogNormalizationWorker] (draine déjà `WHERE linkKey = ''`) retraite
 * tout le catalogue avec le `leadingPipeTagBlock` corrigé, sans passer par le
 * réseau ni attendre le TTL 7 jours de `detailsCachedAt`.
 */
class Migration34To35SqlTest {

    private fun Connection.createV34Schema() {
        createStatement().use { statement ->
            statement.execute(
                "CREATE TABLE vod_streams (streamId INTEGER NOT NULL PRIMARY KEY, name TEXT NOT NULL, " +
                    "categoryId TEXT NOT NULL, cleanTitle TEXT NOT NULL DEFAULT '', linkKey TEXT NOT NULL DEFAULT '', " +
                    "languageTag TEXT, languageRaw TEXT, qualityTag TEXT, qualityRaw TEXT, versionLabel TEXT)"
            )
            statement.execute(
                "CREATE TABLE series_streams (seriesId INTEGER NOT NULL PRIMARY KEY, name TEXT NOT NULL, " +
                    "categoryId TEXT NOT NULL, cleanTitle TEXT NOT NULL DEFAULT '', linkKey TEXT NOT NULL DEFAULT '', " +
                    "languageTag TEXT, languageRaw TEXT, qualityTag TEXT, qualityRaw TEXT, versionLabel TEXT)"
            )
            statement.execute(
                "INSERT INTO vod_streams(streamId, name, categoryId, cleanTitle, linkKey, languageTag, versionLabel) " +
                    "VALUES (1, '|4K-DV| Dragon ball super: super hero (VO|STFR)', 'cat', " +
                    "'Dragon ball super: super hero (VO|STFR)', 'stale-key', 'vo', " +
                    "'4K-DV · Dragon ball super: super hero (vo · STFR')"
            )
            statement.execute(
                "INSERT INTO series_streams(seriesId, name, categoryId, cleanTitle, linkKey, languageTag, versionLabel) " +
                    "VALUES (1, '|VO|STFR| Supergirl', 'cat', 'STFR Supergirl', 'other-stale-key', 'vo', 'VO')"
            )
        }
    }

    @Test
    fun `T21 columns are cleared so the background worker requeues every row`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createV34Schema()
            migration34To35Statements().forEach { connection.createStatement().use { s -> s.execute(it) } }

            connection.createStatement().use { statement ->
                statement.executeQuery(
                    "SELECT cleanTitle, linkKey, languageTag, versionLabel FROM vod_streams WHERE streamId = 1"
                ).use {
                    it.next()
                    assertEquals("", it.getString("cleanTitle"))
                    assertEquals("", it.getString("linkKey"))
                    assertNull(it.getString("languageTag"))
                    assertNull(it.getString("versionLabel"))
                }
                statement.executeQuery(
                    "SELECT COUNT(*) AS n FROM series_streams WHERE linkKey = ''"
                ).use { it.next(); assertEquals(1, it.getInt("n")) }
            }
        }
    }

    @Test
    fun `re-running the migration statements is idempotent`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createV34Schema()
            migration34To35Statements().forEach { connection.createStatement().use { s -> s.execute(it) } }
            migration34To35Statements().forEach { connection.createStatement().use { s -> s.execute(it) } }
        }
    }
}
