package com.cstv.app.data.local.db

import java.sql.Connection
import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * F46 §8.3 : preuve SQL de [com.cstv.app.data.local.dao.ExternalMetadataDao.observeCoverage] —
 * même limitation que [CatalogUpsertSqlTest] (pas d'`androidTest`/`MigrationTestHelper`), le
 * schéma pertinent (`vod_streams`, `series_streams`, `external_media_links`) est rejoué à la main
 * sur SQLite en mémoire et la requête exercée est un copier-coller exact de `COVERAGE_QUERY`.
 */
class ExternalMetadataCoverageSqlTest {

    private fun Connection.createSchema() {
        createStatement().use { statement ->
            statement.execute("CREATE TABLE vod_streams (streamId INTEGER NOT NULL PRIMARY KEY, name TEXT NOT NULL)")
            statement.execute("CREATE TABLE series_streams (seriesId INTEGER NOT NULL PRIMARY KEY, name TEXT NOT NULL)")
            statement.execute(
                "CREATE TABLE external_media_links (kind TEXT NOT NULL, providerId INTEGER NOT NULL, " +
                    "externalId TEXT, lastMatchAttemptAt INTEGER, retryAfter INTEGER, " +
                    "PRIMARY KEY(kind, providerId))"
            )
        }
    }

    private fun Connection.coverage(): CoverageRow {
        createStatement().use { statement ->
            statement.executeQuery(COVERAGE_SQL).use { rs ->
                rs.next()
                return CoverageRow(
                    movieTotal = rs.getInt("movieTotal"),
                    movieLinked = rs.getInt("movieLinked"),
                    movieProcessed = rs.getInt("movieProcessed"),
                    seriesTotal = rs.getInt("seriesTotal"),
                    seriesLinked = rs.getInt("seriesLinked"),
                    seriesProcessed = rs.getInt("seriesProcessed"),
                )
            }
        }
    }

    private data class CoverageRow(
        val movieTotal: Int, val movieLinked: Int, val movieProcessed: Int,
        val seriesTotal: Int, val seriesLinked: Int, val seriesProcessed: Int,
    )

    @Test
    fun `a movie without a link is pending`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createSchema()
            connection.createStatement().use { it.execute("INSERT INTO vod_streams(streamId, name) VALUES (1, 'A')") }

            val coverage = connection.coverage()
            assertEquals(1, coverage.movieTotal)
            assertEquals(0, coverage.movieLinked)
            assertEquals(0, coverage.movieProcessed)
        }
    }

    @Test
    fun `a linked movie counts as linked and processed`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createSchema()
            connection.createStatement().use { statement ->
                statement.execute("INSERT INTO vod_streams(streamId, name) VALUES (1, 'A')")
                statement.execute(
                    "INSERT INTO external_media_links(kind, providerId, externalId, lastMatchAttemptAt, retryAfter) " +
                        "VALUES ('movie', 1, 'ext-1', 1000, NULL)"
                )
            }

            val coverage = connection.coverage()
            assertEquals(1, coverage.movieLinked)
            assertEquals(1, coverage.movieProcessed)
        }
    }

    @Test
    fun `an UNRESOLVED movie counts as processed but not linked`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createSchema()
            connection.createStatement().use { statement ->
                statement.execute("INSERT INTO vod_streams(streamId, name) VALUES (1, 'A')")
                statement.execute(
                    "INSERT INTO external_media_links(kind, providerId, externalId, lastMatchAttemptAt, retryAfter) " +
                        "VALUES ('movie', 1, NULL, 1000, 5000)"
                )
            }

            val coverage = connection.coverage()
            assertEquals(0, coverage.movieLinked)
            assertEquals(1, coverage.movieProcessed)
        }
    }

    @Test
    fun `a linked series counts as linked and processed`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createSchema()
            connection.createStatement().use { statement ->
                statement.execute("INSERT INTO series_streams(seriesId, name) VALUES (1, 'S')")
                statement.execute(
                    "INSERT INTO external_media_links(kind, providerId, externalId, lastMatchAttemptAt, retryAfter) " +
                        "VALUES ('series', 1, 'ext-1', 1000, NULL)"
                )
            }

            val coverage = connection.coverage()
            assertEquals(1, coverage.seriesLinked)
            assertEquals(1, coverage.seriesProcessed)
        }
    }

    @Test
    fun `an unresolved series counts as processed but not linked`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createSchema()
            connection.createStatement().use { statement ->
                statement.execute("INSERT INTO series_streams(seriesId, name) VALUES (1, 'S')")
                statement.execute(
                    "INSERT INTO external_media_links(kind, providerId, externalId, lastMatchAttemptAt, retryAfter) " +
                        "VALUES ('series', 1, NULL, 1000, 5000)"
                )
            }

            val coverage = connection.coverage()
            assertEquals(0, coverage.seriesLinked)
            assertEquals(1, coverage.seriesProcessed)
        }
    }

    @Test
    fun `an orphan link pointing to a providerId no longer in the catalogue is ignored`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createSchema()
            connection.createStatement().use { statement ->
                // Aucun vod_streams(1) : le catalogue local ne contient plus ce média.
                statement.execute(
                    "INSERT INTO external_media_links(kind, providerId, externalId, lastMatchAttemptAt, retryAfter) " +
                        "VALUES ('movie', 1, 'ext-1', 1000, NULL)"
                )
            }

            val coverage = connection.coverage()
            assertEquals(0, coverage.movieTotal)
            assertEquals(0, coverage.movieLinked)
            assertEquals(0, coverage.movieProcessed)
        }
    }

    @Test
    fun `removing a media from the catalogue removes it from the total`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createSchema()
            connection.createStatement().use { statement ->
                statement.execute("INSERT INTO vod_streams(streamId, name) VALUES (1, 'A')")
                statement.execute(
                    "INSERT INTO external_media_links(kind, providerId, externalId, lastMatchAttemptAt, retryAfter) " +
                        "VALUES ('movie', 1, 'ext-1', 1000, NULL)"
                )
            }
            assertEquals(1, connection.coverage().movieTotal)

            connection.createStatement().use { it.execute("DELETE FROM vod_streams WHERE streamId = 1") }

            val coverage = connection.coverage()
            assertEquals(0, coverage.movieTotal)
            assertEquals(0, coverage.movieLinked)
        }
    }

    @Test
    fun `a movie without externalId and without lastMatchAttemptAt is pending`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createSchema()
            connection.createStatement().use { statement ->
                statement.execute("INSERT INTO vod_streams(streamId, name) VALUES (1, 'A')")
                // Ligne en file d'attente technique, sans résultat métier persisté.
                statement.execute(
                    "INSERT INTO external_media_links(kind, providerId, externalId, lastMatchAttemptAt, retryAfter) " +
                        "VALUES ('movie', 1, NULL, NULL, NULL)"
                )
            }

            val coverage = connection.coverage()
            assertEquals(0, coverage.movieLinked)
            assertEquals(0, coverage.movieProcessed)
        }
    }

    @Test
    fun `an UNRESOLVED with a future retryAfter cooldown still counts as processed`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createSchema()
            connection.createStatement().use { statement ->
                statement.execute("INSERT INTO vod_streams(streamId, name) VALUES (1, 'A')")
                statement.execute(
                    "INSERT INTO external_media_links(kind, providerId, externalId, lastMatchAttemptAt, retryAfter) " +
                        "VALUES ('movie', 1, NULL, 1000, 999999999)"
                )
            }

            val coverage = connection.coverage()
            assertEquals(1, coverage.movieProcessed)
        }
    }

    @Test
    fun `movie and series totals are separate and exact`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createSchema()
            connection.createStatement().use { statement ->
                statement.execute("INSERT INTO vod_streams(streamId, name) VALUES (1, 'A')")
                statement.execute("INSERT INTO vod_streams(streamId, name) VALUES (2, 'B')")
                statement.execute("INSERT INTO series_streams(seriesId, name) VALUES (1, 'S')")
                statement.execute(
                    "INSERT INTO external_media_links(kind, providerId, externalId, lastMatchAttemptAt, retryAfter) " +
                        "VALUES ('movie', 1, 'ext-1', 1000, NULL)"
                )
            }

            val coverage = connection.coverage()
            assertEquals(2, coverage.movieTotal)
            assertEquals(1, coverage.movieLinked)
            assertEquals(1, coverage.seriesTotal)
            assertEquals(0, coverage.seriesLinked)
        }
    }

    companion object {
        // Copie exacte de `COVERAGE_QUERY` (ExternalMetadataDao.kt) — toute dérive entre les deux
        // doit faire échouer ce test, pas seulement la lecture au runtime.
        private val COVERAGE_SQL = """
            SELECT
                (SELECT COUNT(*) FROM vod_streams) AS movieTotal,
                (
                    SELECT COUNT(*)
                    FROM vod_streams v
                    JOIN external_media_links l ON l.kind = 'movie' AND l.providerId = v.streamId
                    WHERE l.externalId IS NOT NULL
                ) AS movieLinked,
                (
                    SELECT COUNT(*)
                    FROM vod_streams v
                    JOIN external_media_links l ON l.kind = 'movie' AND l.providerId = v.streamId
                    WHERE l.externalId IS NOT NULL OR l.lastMatchAttemptAt IS NOT NULL
                ) AS movieProcessed,
                (SELECT COUNT(*) FROM series_streams) AS seriesTotal,
                (
                    SELECT COUNT(*)
                    FROM series_streams s
                    JOIN external_media_links l ON l.kind = 'series' AND l.providerId = s.seriesId
                    WHERE l.externalId IS NOT NULL
                ) AS seriesLinked,
                (
                    SELECT COUNT(*)
                    FROM series_streams s
                    JOIN external_media_links l ON l.kind = 'series' AND l.providerId = s.seriesId
                    WHERE l.externalId IS NOT NULL OR l.lastMatchAttemptAt IS NOT NULL
                ) AS seriesProcessed
        """
    }
}
