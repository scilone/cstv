package com.cstv.app.data.local.db

import java.sql.Connection
import java.sql.DriverManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F39 §8.4 : preuve automatisée que l'ajout de `languageTag`/`qualityTag` à
 * la projection de l'onglet « Tout » ne fait pas perdre la couverture de
 * l'index T9 (MIGRATION_23_24) — l'`EXPLAIN QUERY PLAN` doit rester un
 * balayage d'index pur, sans tri temporaire ni accès table.
 *
 * Rejoue sur SQLite en mémoire l'index couvrant tel qu'étendu par la
 * migration 31→32 (`index_vod_streams_..._languageTag_qualityTag`), plutôt
 * que de dépendre d'une vérification manuelle non reproductible en CI.
 */
class CoveringIndexF39SqlTest {

    private fun Connection.explain(sql: String): List<String> =
        createStatement().use { statement ->
            statement.executeQuery("EXPLAIN QUERY PLAN $sql").use { rs ->
                buildList { while (rs.next()) add(rs.getString("detail")) }
            }
        }

    @Test
    fun `all-tab query on vod_streams resolves entirely in the extended covering index`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    "CREATE TABLE vod_streams (streamId INTEGER NOT NULL PRIMARY KEY, name TEXT NOT NULL, " +
                        "streamIcon TEXT, rating TEXT, added TEXT, categoryId TEXT NOT NULL, genre TEXT, " +
                        "releaseYear INTEGER, categoryRank INTEGER NOT NULL DEFAULT 0, " +
                        "languageTag TEXT, qualityTag TEXT, plot TEXT, searchText TEXT)"
                )
                statement.execute(
                    "CREATE INDEX index_vod_streams_categoryRank_streamId_name_streamIcon_rating_added_categoryId_genre_releaseYear_languageTag_qualityTag " +
                        "ON vod_streams(categoryRank, streamId, name, streamIcon, rating, added, categoryId, genre, releaseYear, languageTag, qualityTag)"
                )
            }

            val plan = connection.explain(
                "SELECT streamId, name, streamIcon, rating, added, categoryId, genre, releaseYear, languageTag, qualityTag " +
                    "FROM vod_streams WHERE categoryRank < 100 ORDER BY categoryRank ASC"
            )

            assertTrue(
                "attendu un balayage de l'index couvrant, obtenu : $plan",
                plan.any { it.contains("USING COVERING INDEX index_vod_streams_categoryRank", ignoreCase = true) }
            )
            assertFalse(
                "aucun tri temporaire attendu, l'index porte déjà categoryRank en tête : $plan",
                plan.any { it.contains("USE TEMP B-TREE", ignoreCase = true) }
            )
        }
    }

    @Test
    fun `all-tab query on series_streams resolves entirely in the extended covering index`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    "CREATE TABLE series_streams (seriesId INTEGER NOT NULL PRIMARY KEY, name TEXT NOT NULL, " +
                        "cover TEXT, rating TEXT, added TEXT, categoryId TEXT NOT NULL, genre TEXT, " +
                        "releaseYear INTEGER, categoryRank INTEGER NOT NULL DEFAULT 0, " +
                        "languageTag TEXT, qualityTag TEXT, plot TEXT, searchText TEXT)"
                )
                statement.execute(
                    "CREATE INDEX index_series_streams_categoryRank_seriesId_name_cover_rating_added_categoryId_genre_releaseYear_languageTag_qualityTag " +
                        "ON series_streams(categoryRank, seriesId, name, cover, rating, added, categoryId, genre, releaseYear, languageTag, qualityTag)"
                )
            }

            val plan = connection.explain(
                "SELECT seriesId, name, cover, rating, added, categoryId, genre, releaseYear, languageTag, qualityTag " +
                    "FROM series_streams WHERE categoryRank < 100 ORDER BY categoryRank ASC"
            )

            assertTrue(
                "attendu un balayage de l'index couvrant, obtenu : $plan",
                plan.any { it.contains("USING COVERING INDEX index_series_streams_categoryRank", ignoreCase = true) }
            )
            assertFalse(
                "aucun tri temporaire attendu, l'index porte déjà categoryRank en tête : $plan",
                plan.any { it.contains("USE TEMP B-TREE", ignoreCase = true) }
            )
        }
    }
}
