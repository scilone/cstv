package com.cstv.app.data.local.db

import java.sql.Connection
import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F39 §8.3/§8.4, tâche 3 : rejoue [migration31To32Statements] sur SQLite en
 * mémoire — nouvelle table `series_version_preferences` en cascade sur
 * `profiles`, et index couvrant étendu sans perdre sa couverture (même motif
 * que [CategoryRankMigrationSqlTest]).
 */
class Migration31To32SqlTest {

    private fun Connection.createV31Schema() {
        createStatement().use { statement ->
            statement.execute(
                "CREATE TABLE profiles (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, " +
                    "avatarId INTEGER NOT NULL, createdAt INTEGER NOT NULL, remoteId TEXT)"
            )
            statement.execute(
                "CREATE TABLE vod_streams (streamId INTEGER NOT NULL PRIMARY KEY, name TEXT NOT NULL, " +
                    "streamIcon TEXT, rating TEXT, added TEXT, categoryId TEXT NOT NULL, genre TEXT, " +
                    "releaseYear INTEGER, categoryRank INTEGER NOT NULL DEFAULT 0, languageTag TEXT, qualityTag TEXT)"
            )
            statement.execute(
                "CREATE INDEX index_vod_streams_categoryRank_streamId_name_streamIcon_rating_added_categoryId_genre_releaseYear " +
                    "ON vod_streams(categoryRank, streamId, name, streamIcon, rating, added, categoryId, genre, releaseYear)"
            )
            statement.execute(
                "CREATE TABLE series_streams (seriesId INTEGER NOT NULL PRIMARY KEY, name TEXT NOT NULL, " +
                    "cover TEXT, rating TEXT, added TEXT, categoryId TEXT NOT NULL, genre TEXT, " +
                    "releaseYear INTEGER, categoryRank INTEGER NOT NULL DEFAULT 0, languageTag TEXT, qualityTag TEXT)"
            )
            statement.execute(
                "CREATE INDEX index_series_streams_categoryRank_seriesId_name_cover_rating_added_categoryId_genre_releaseYear " +
                    "ON series_streams(categoryRank, seriesId, name, cover, rating, added, categoryId, genre, releaseYear)"
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
    fun `series_version_preferences is created with a cascade to the active profile`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createV31Schema()
            connection.createStatement().use { it.execute("PRAGMA foreign_keys = ON") }
            migration31To32Statements().forEach { connection.createStatement().use { s -> s.execute(it) } }

            connection.createStatement().use { statement ->
                statement.execute("INSERT INTO profiles(id, name, avatarId, createdAt) VALUES (1, 'Profil', 0, 100)")
                statement.execute(
                    "INSERT INTO series_version_preferences(profileId, linkKey, preferredSeriesId, updatedAt) " +
                        "VALUES (1, 'key-a', 50, 200)"
                )
            }

            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT preferredSeriesId FROM series_version_preferences WHERE profileId = 1 AND linkKey = 'key-a'").use {
                    it.next()
                    assertEquals(50, it.getInt("preferredSeriesId"))
                }
            }

            // Cascade : supprimer le profil supprime sa préférence, comme track_preferences.
            connection.createStatement().use { it.execute("DELETE FROM profiles WHERE id = 1") }
            connection.createStatement().use { statement ->
                assertEquals(
                    0,
                    statement.executeQuery("SELECT COUNT(*) AS n FROM series_version_preferences").use { it.next(); it.getInt("n") }
                )
            }
        }
    }

    @Test
    fun `the extended covering index still resolves the all-tab query without a temp sort`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createV31Schema()
            migration31To32Statements().forEach { connection.createStatement().use { s -> s.execute(it) } }

            val vodPlan = connection.explain(
                "SELECT streamId, name, streamIcon, rating, added, categoryId, genre, releaseYear, languageTag, qualityTag " +
                    "FROM vod_streams WHERE categoryRank < 100 ORDER BY categoryRank ASC"
            )
            assertTrue(vodPlan.any { it.contains("USING COVERING INDEX index_vod_streams_categoryRank", ignoreCase = true) })
            assertFalse(vodPlan.any { it.contains("USE TEMP B-TREE", ignoreCase = true) })

            val seriesPlan = connection.explain(
                "SELECT seriesId, name, cover, rating, added, categoryId, genre, releaseYear, languageTag, qualityTag " +
                    "FROM series_streams WHERE categoryRank < 100 ORDER BY categoryRank ASC"
            )
            assertTrue(seriesPlan.any { it.contains("USING COVERING INDEX index_series_streams_categoryRank", ignoreCase = true) })
            assertFalse(seriesPlan.any { it.contains("USE TEMP B-TREE", ignoreCase = true) })
        }
    }

    @Test
    fun `re-running the migration statements is idempotent`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createV31Schema()
            migration31To32Statements().forEach { connection.createStatement().use { s -> s.execute(it) } }
            // Rejouer une seconde fois ne doit jamais lever (IF NOT EXISTS/IF EXISTS partout).
            migration31To32Statements().forEach { connection.createStatement().use { s -> s.execute(it) } }
        }
    }
}
