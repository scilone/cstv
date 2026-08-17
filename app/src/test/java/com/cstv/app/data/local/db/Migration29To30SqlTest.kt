package com.cstv.app.data.local.db

import java.sql.Connection
import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T24 : SQL-only migration coverage, suivant le motif sqlite-jdbc existant
 * (voir `Migration28To29SqlTest`). Vérifie que la migration crée
 * `canonical_media_links` sans toucher au catalogue existant, et que la
 * table peut effectivement stocker plusieurs `providerId` sous le même
 * `canonicalId` (versions multiples d'une même œuvre).
 */
class Migration29To30SqlTest {
    private fun Connection.createRoom29Catalog() = createStatement().use { statement ->
        statement.execute("CREATE TABLE vod_streams (streamId INTEGER NOT NULL PRIMARY KEY, name TEXT NOT NULL)")
        statement.execute("CREATE TABLE series_streams (seriesId INTEGER NOT NULL PRIMARY KEY, name TEXT NOT NULL)")
        statement.execute("INSERT INTO vod_streams VALUES (10, 'Film VF')")
        statement.execute("INSERT INTO series_streams VALUES (20, 'Série MULTI')")
    }

    @Test
    fun `migration creates canonical_media_links without touching existing catalog`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createRoom29Catalog()
            connection.createStatement().use { statement -> migration29To30Statements().forEach(statement::execute) }

            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) AS n FROM vod_streams").use { assertTrue(it.next()); assertEquals(1, it.getInt("n")) }
                statement.executeQuery("SELECT COUNT(*) AS n FROM series_streams").use { assertTrue(it.next()); assertEquals(1, it.getInt("n")) }
                statement.executeQuery("SELECT COUNT(*) AS n FROM canonical_media_links").use { assertTrue(it.next()); assertEquals(0, it.getInt("n")) }

                statement.executeQuery("PRAGMA index_list(canonical_media_links)").use { indexes ->
                    val names = generateSequence { if (indexes.next()) indexes.getString("name") else null }.toSet()
                    assertTrue("index_canonical_media_links_canonicalId" in names)
                }
            }
        }
    }

    @Test
    fun `several providerId can share the same canonicalId (multi-version work)`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createRoom29Catalog()
            connection.createStatement().use { statement -> migration29To30Statements().forEach(statement::execute) }
            connection.createStatement().use { statement ->
                statement.execute("INSERT INTO canonical_media_links VALUES ('movie', 10, 'movie:438631', 1000)")
                statement.execute("INSERT INTO canonical_media_links VALUES ('movie', 11, 'movie:438631', 1000)")
            }

            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT providerId FROM canonical_media_links WHERE canonicalId = 'movie:438631' ORDER BY providerId").use { row ->
                    assertTrue(row.next()); assertEquals(10, row.getInt("providerId"))
                    assertTrue(row.next()); assertEquals(11, row.getInt("providerId"))
                }
            }
        }
    }

    @Test
    fun `providerId is not a durable identity across kinds - same providerId, different kind, coexist`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createRoom29Catalog()
            connection.createStatement().use { statement -> migration29To30Statements().forEach(statement::execute) }
            connection.createStatement().use { statement ->
                // Namespaces Xtream distincts movie/series : même entier providerId, kinds différents.
                statement.execute("INSERT INTO canonical_media_links VALUES ('movie', 10, 'movie:1', 1000)")
                statement.execute("INSERT INTO canonical_media_links VALUES ('series', 10, 'series:1', 1000)")
            }

            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) AS n FROM canonical_media_links WHERE providerId = 10").use {
                    assertTrue(it.next()); assertEquals(2, it.getInt("n"))
                }
            }
        }
    }
}
