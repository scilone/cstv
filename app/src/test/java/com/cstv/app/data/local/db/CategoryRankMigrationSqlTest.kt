package com.cstv.app.data.local.db

import com.cstv.app.data.local.dao.ALL_MODE_ROWS_PER_CATEGORY
import java.sql.Connection
import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le projet n'a pas d'infrastructure de test instrumenté (`androidTest`), donc
 * pas de `MigrationTestHelper` : le SQL de `MIGRATION_23_24` est rejoué ici sur
 * un SQLite en mémoire (`org.xerial:sqlite-jdbc`), comme le fait déjà
 * `LocalSearchQueryTest` pour le repli d'accents.
 *
 * Ce qui est vérifié : le rang par catégorie calculé par le remplissage, et le
 * fait que la requête de l'onglet « Tout » ne remonte bien que les premiers
 * éléments de chaque catégorie, dans l'ordre du serveur.
 */
class CategoryRankMigrationSqlTest {

    /**
     * Reprend mot pour mot `VodDao.observeAllStreamListRows`. Toute
     * modification de la requête doit être reportée ici.
     */
    private val allModeQuery =
        "SELECT streamId, name, streamIcon, rating, added, categoryId, genre, releaseYear " +
            "FROM vod_streams WHERE categoryRank < ? ORDER BY categoryRank ASC"

    private fun Connection.createLegacyCatalog() {
        createStatement().use { statement ->
            // Schéma de la version 23, avant l'ajout de `categoryRank`.
            statement.execute(
                """
                CREATE TABLE vod_streams (
                    streamId INTEGER NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    streamIcon TEXT,
                    rating TEXT,
                    added TEXT,
                    categoryId TEXT NOT NULL,
                    cachedAt INTEGER NOT NULL,
                    genre TEXT,
                    orderIndex INTEGER NOT NULL DEFAULT 0,
                    releaseYear INTEGER
                )
                """.trimIndent()
            )
            statement.execute("ALTER TABLE vod_streams ADD COLUMN categoryRank INTEGER NOT NULL DEFAULT 0")
        }
    }

    /** `orderIndex` est global sur toute la réponse, catégories entrelacées. */
    private fun Connection.insertStreams(categoriesBySize: Map<String, Int>) {
        var orderIndex = 0
        prepareStatement(
            "INSERT INTO vod_streams(streamId, name, categoryId, cachedAt, orderIndex) VALUES (?, ?, ?, 0, ?)"
        ).use { statement ->
            val remaining = categoriesBySize.toMutableMap()
            while (remaining.values.any { it > 0 }) {
                for (categoryId in categoriesBySize.keys) {
                    val left = remaining[categoryId] ?: 0
                    if (left <= 0) continue
                    remaining[categoryId] = left - 1
                    statement.setInt(1, orderIndex + 1)
                    statement.setString(2, "Film $orderIndex")
                    statement.setString(3, categoryId)
                    statement.setInt(4, orderIndex)
                    statement.addBatch()
                    orderIndex++
                }
            }
            statement.executeBatch()
        }
    }

    private fun Connection.backfill() {
        createStatement().use { statement ->
            categoryRankBackfillStatements(table = "vod_streams", keyColumn = "streamId")
                .forEach { statement.execute(it) }
        }
    }

    @Test
    fun backfillNumbersEachCategoryFromZero() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createLegacyCatalog()
            connection.insertStreams(mapOf("69" to 3, "78" to 2))
            connection.backfill()

            connection.createStatement().use { statement ->
                statement.executeQuery(
                    "SELECT categoryId, categoryRank FROM vod_streams ORDER BY orderIndex"
                ).use { result ->
                    val ranks = mutableListOf<Pair<String, Int>>()
                    while (result.next()) {
                        ranks += result.getString("categoryId") to result.getInt("categoryRank")
                    }
                    // Entrelacement 69, 78, 69, 78, 69 : chaque catégorie
                    // repart de 0 malgré un `orderIndex` global croissant.
                    assertEquals(
                        listOf("69" to 0, "78" to 0, "69" to 1, "78" to 1, "69" to 2),
                        ranks
                    )
                }
            }
        }
    }

    @Test
    fun allModeQueryReturnsAtMostTheRowCapPerCategory() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createLegacyCatalog()
            val bigCategory = ALL_MODE_ROWS_PER_CATEGORY + 40
            connection.insertStreams(mapOf("69" to bigCategory, "78" to 7))
            connection.backfill()

            connection.prepareStatement(allModeQuery).use { statement ->
                statement.setInt(1, ALL_MODE_ROWS_PER_CATEGORY)
                statement.executeQuery().use { result ->
                    val perCategory = mutableMapOf<String, MutableList<Int>>()
                    while (result.next()) {
                        perCategory.getOrPut(result.getString("categoryId")) { mutableListOf() }
                            .add(result.getInt("streamId"))
                    }
                    assertEquals(ALL_MODE_ROWS_PER_CATEGORY, perCategory.getValue("69").size)
                    assertEquals(7, perCategory.getValue("78").size)
                    // `groupBy` conserve l'ordre de rencontre : l'ordre du
                    // serveur doit donc déjà être celui du curseur.
                    assertTrue(perCategory.getValue("69").zipWithNext().all { (a, b) -> a < b })
                    assertTrue(perCategory.getValue("78").zipWithNext().all { (a, b) -> a < b })
                }
            }
        }
    }

    @Test
    fun allModeQueryReturnsEverythingWhenRankStaysAtDefault() {
        // Appareils dont le SQLite ne connaît pas `ROW_NUMBER()` : le
        // remplissage est sauté, `categoryRank` reste à 0, et l'onglet
        // « Tout » retrouve son comportement d'avant la migration.
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createLegacyCatalog()
            connection.insertStreams(mapOf("69" to 150))

            connection.prepareStatement(allModeQuery).use { statement ->
                statement.setInt(1, ALL_MODE_ROWS_PER_CATEGORY)
                statement.executeQuery().use { result ->
                    var count = 0
                    while (result.next()) count++
                    assertEquals(150, count)
                }
            }
        }
    }
}
