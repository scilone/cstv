package com.cstv.app.data.local.db

import java.sql.Connection
import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** SQL-only migration coverage, following the existing sqlite-jdbc migration-test pattern. */
class Migration28To29SqlTest {
    private fun Connection.createRoom28Catalog() = createStatement().use { statement ->
        statement.execute("CREATE TABLE live_streams (streamId INTEGER NOT NULL PRIMARY KEY, name TEXT NOT NULL)")
        statement.execute("CREATE TABLE vod_streams (streamId INTEGER NOT NULL PRIMARY KEY, name TEXT NOT NULL)")
        statement.execute("CREATE TABLE series_streams (seriesId INTEGER NOT NULL PRIMARY KEY, name TEXT NOT NULL)")
        statement.execute("INSERT INTO live_streams VALUES (1, 'TF1 HD')")
        statement.execute("INSERT INTO vod_streams VALUES (2, 'Film VF')")
        statement.execute("INSERT INTO series_streams VALUES (3, 'Série MULTI')")
    }

    @Test
    fun `migration preserves rows and adds empty normalization columns with indexes`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createRoom28Catalog()
            connection.createStatement().use { statement -> migration28To29Statements().forEach(statement::execute) }

            connection.createStatement().use { statement ->
                listOf(
                    "live_streams" to "streamId",
                    "vod_streams" to "streamId",
                    "series_streams" to "seriesId"
                ).forEach { (table, idColumn) ->
                    statement.executeQuery("SELECT name, cleanTitle, linkKey, languageTag, languageRaw, qualityTag, qualityRaw FROM $table WHERE $idColumn > 0").use { row ->
                    assertTrue(row.next())
                    assertEquals("", row.getString("cleanTitle"))
                    assertEquals("", row.getString("linkKey"))
                    assertEquals(null, row.getString("languageTag"))
                    assertEquals(null, row.getString("languageRaw"))
                    assertEquals(null, row.getString("qualityTag"))
                    assertEquals(null, row.getString("qualityRaw"))
                }
                    statement.executeQuery("PRAGMA index_list($table)").use { indexes ->
                    val names = generateSequence { if (indexes.next()) indexes.getString("name") else null }.toSet()
                    assertTrue("index_${table}_linkKey" in names)
                }
                }
            }
            }
        }
    }
