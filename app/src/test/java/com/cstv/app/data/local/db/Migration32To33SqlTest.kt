package com.cstv.app.data.local.db

import java.sql.Connection
import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F39 (évolution PO) : rejoue [migration32To33Statements] sur SQLite en
 * mémoire — colonne `versionLabel` ajoutée à `vod_streams`/`series_streams`,
 * index couvrant de l'onglet « Tout » réétendu sans perdre sa couverture
 * (même motif que [Migration31To32SqlTest]).
 */
class Migration32To33SqlTest {

    private fun Connection.createV32Schema() {
        createStatement().use { statement ->
            statement.execute(
                "CREATE TABLE vod_streams (streamId INTEGER NOT NULL PRIMARY KEY, name TEXT NOT NULL, " +
                    "streamIcon TEXT, rating TEXT, added TEXT, categoryId TEXT NOT NULL, genre TEXT, " +
                    "releaseYear INTEGER, categoryRank INTEGER NOT NULL DEFAULT 0, languageTag TEXT, qualityTag TEXT)"
            )
            statement.execute(
                "CREATE INDEX index_vod_streams_categoryRank_streamId_name_streamIcon_rating_added_categoryId_genre_releaseYear_languageTag_qualityTag " +
                    "ON vod_streams(categoryRank, streamId, name, streamIcon, rating, added, categoryId, genre, releaseYear, languageTag, qualityTag)"
            )
            statement.execute(
                "CREATE TABLE series_streams (seriesId INTEGER NOT NULL PRIMARY KEY, name TEXT NOT NULL, " +
                    "cover TEXT, rating TEXT, added TEXT, categoryId TEXT NOT NULL, genre TEXT, " +
                    "releaseYear INTEGER, categoryRank INTEGER NOT NULL DEFAULT 0, languageTag TEXT, qualityTag TEXT)"
            )
            statement.execute(
                "CREATE INDEX index_series_streams_categoryRank_seriesId_name_cover_rating_added_categoryId_genre_releaseYear_languageTag_qualityTag " +
                    "ON series_streams(categoryRank, seriesId, name, cover, rating, added, categoryId, genre, releaseYear, languageTag, qualityTag)"
            )
        }
    }

    private fun Connection.explain(sql: String): List<String> =
        createStatement().use { statement ->
            statement.executeQuery("EXPLAIN QUERY PLAN $sql").use { rs ->
                buildList { while (rs.next()) add(rs.getString("detail")) }
            }
        }

    @Test
    fun `versionLabel column is added to both tables and defaults to null`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createV32Schema()
            migration32To33Statements().forEach { connection.createStatement().use { s -> s.execute(it) } }

            connection.createStatement().use { statement ->
                statement.execute(
                    "INSERT INTO vod_streams(streamId, name, categoryId, categoryRank) VALUES (1, 'Supergirl', 'cat', 0)"
                )
            }
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT versionLabel FROM vod_streams WHERE streamId = 1").use {
                    it.next()
                    assertNull(it.getString("versionLabel"))
                }
            }
        }
    }

    @Test
    fun `the re-extended covering index still resolves the all-tab query without a temp sort`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createV32Schema()
            migration32To33Statements().forEach { connection.createStatement().use { s -> s.execute(it) } }

            val vodPlan = connection.explain(
                "SELECT streamId, name, streamIcon, rating, added, categoryId, genre, releaseYear, languageTag, qualityTag, versionLabel " +
                    "FROM vod_streams WHERE categoryRank < 100 ORDER BY categoryRank ASC"
            )
            assertTrue(vodPlan.any { it.contains("USING COVERING INDEX index_vod_streams_categoryRank", ignoreCase = true) })
            assertFalse(vodPlan.any { it.contains("USE TEMP B-TREE", ignoreCase = true) })

            val seriesPlan = connection.explain(
                "SELECT seriesId, name, cover, rating, added, categoryId, genre, releaseYear, languageTag, qualityTag, versionLabel " +
                    "FROM series_streams WHERE categoryRank < 100 ORDER BY categoryRank ASC"
            )
            assertTrue(seriesPlan.any { it.contains("USING COVERING INDEX index_series_streams_categoryRank", ignoreCase = true) })
            assertFalse(seriesPlan.any { it.contains("USE TEMP B-TREE", ignoreCase = true) })
        }
    }

    @Test
    fun `re-running the migration statements is idempotent`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createV32Schema()
            migration32To33Statements().forEach { connection.createStatement().use { s -> s.execute(it) } }
            // ALTER TABLE ADD COLUMN n'est pas idempotent nativement en SQLite (pas de IF NOT
            // EXISTS) : seules les instructions d'index le sont. On vérifie donc que rejouer
            // uniquement les DROP/CREATE INDEX ne lève pas, comme pour la migration 31→32.
            migration32To33Statements().drop(2).forEach { connection.createStatement().use { s -> s.execute(it) } }
        }
    }
}
