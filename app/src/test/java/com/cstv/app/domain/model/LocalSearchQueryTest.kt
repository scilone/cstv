package com.cstv.app.domain.model

import com.cstv.app.data.local.dao.SEARCH_TEXT_LIKE_PREDICATE
import com.cstv.app.data.local.db.SEARCH_FOLD_MAP
import com.cstv.app.data.local.db.searchFoldSql
import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSearchQueryTest {
    @Test
    fun `empty and whitespace queries are empty`() {
        assertTrue(LocalSearchQuery.parse("").isEmpty)
        assertTrue(LocalSearchQuery.parse("   \t ").isEmpty)
        assertFalse(LocalSearchQuery.parse("").matches("anything"))
    }

    @Test
    fun `matches substrings anywhere with case and accent folding`() {
        val text = LocalSearchQuery.buildCatalogSearchText("Marsupilami Odysée René", null, null, null, "films")

        listOf("marsu", "pilami", "lami", "ody", "ODYSEE", "odysée", "dysee", "rene").forEach {
            assertTrue("$it should match", LocalSearchQuery.parse(it).matches(text))
        }
    }

    @Test
    fun `requires every token regardless of order or field`() {
        val text = LocalSearchQuery.buildCatalogSearchText("Le Marsupilami", "Jean Reno", null, null, "5")

        assertTrue(LocalSearchQuery.parse("reno marsu").matches(text))
        assertFalse(LocalSearchQuery.parse("marsu absent").matches(text))
    }

    @Test
    fun `folds ligatures and escapes literal like metacharacters`() {
        assertTrue(LocalSearchQuery.parse("coeur strasse").matches(
            LocalSearchQuery.buildCatalogSearchText("Cœur Straße", null, null, null, "5")
        ))
        assertEquals("%a\\%\\_\\\\b%", LocalSearchQuery.parse("a%_\\b").likePattern)
    }

    @Test
    fun `search text includes all catalog fields`() {
        assertEquals(
            "odysee\nrene\ncoeur\naction\n12",
            LocalSearchQuery.buildCatalogSearchText("Odysée", "René", "Cœur", "Action", "12")
        )
        assertEquals("tf1\n10", LocalSearchQuery.buildLiveSearchText("TF1", "10"))
    }
    @Test
    fun choosesLongestTokenAsSqlAnchor() {
        assertEquals("%marsupilami%", LocalSearchQuery.parse("ami marsupilami").likePattern)
    }

    @Test
    fun likePredicateExecutesWithOneCharacterEscape() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("CREATE TABLE streams (searchText TEXT NOT NULL)")
                statement.execute("INSERT INTO streams(searchText) VALUES ('a%_\\b')")
            }
            connection.prepareStatement(
                "SELECT searchText FROM streams WHERE $SEARCH_TEXT_LIKE_PREDICATE"
            ).use { statement ->
                statement.setString(1, LocalSearchQuery.parse("a%_\\b").likePattern)
                statement.executeQuery().use { result ->
                    assertTrue(result.next())
                    assertEquals("a%_\\b", result.getString("searchText"))
                    assertFalse(result.next())
                }
            }
        }
    }

    @Test
    fun migrationSqlFoldMapMatchesKotlinNormalization() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            SEARCH_FOLD_MAP.forEach { (from, expected) ->
                connection.prepareStatement("SELECT " + searchFoldSql("?")).use { statement ->
                    statement.setString(1, from.uppercase())
                    statement.executeQuery().use { result ->
                        assertTrue(result.next())
                        assertEquals(expected, result.getString(1))
                        assertEquals(expected, LocalSearchQuery.normalize(from.uppercase()))
                    }
                }
            }
        }
    }
}
