package com.cstv.app.data.local.db

import java.sql.Connection
import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T29 cycle backfill P0-3 : preuve SQL de
 * [com.cstv.app.data.local.dao.ExternalMetadataDao.findMoviesMissingExternalMetadata].
 *
 * Le seeder pagine désormais réellement sur `afterId` : la correction ne tient que si la requête
 * (a) avance strictement, (b) exclut ce qui est déjà en file — sinon la page suivante reproposerait
 * les mêmes médias et le seeder tournerait à vide. Même limitation que [ExternalMetadataCoverageSqlTest]
 * (pas d'`androidTest`/`MigrationTestHelper`) : le schéma pertinent est rejoué à la main sur SQLite
 * en mémoire et la requête exercée est un copier-coller exact de l'annotation `@Query`.
 */
class ExternalMetadataBackfillSqlTest {

    private fun Connection.createSchema() {
        createStatement().use { statement ->
            statement.execute("CREATE TABLE vod_streams (streamId INTEGER NOT NULL PRIMARY KEY, linkKey TEXT NOT NULL)")
            statement.execute(
                "CREATE TABLE external_media_links (kind TEXT NOT NULL, providerId INTEGER NOT NULL, " +
                    "externalId TEXT, lastMatchAttemptAt INTEGER, retryAfter INTEGER, PRIMARY KEY(kind, providerId))",
            )
            statement.execute(
                "CREATE TABLE external_hydration_queue (kind TEXT NOT NULL, providerId INTEGER NOT NULL, " +
                    "nextAttemptAt INTEGER NOT NULL, PRIMARY KEY(kind, providerId))",
            )
        }
    }

    private fun Connection.page(afterId: Int, now: Long = NOW, limit: Int = 2): List<Int> {
        prepareStatement(MOVIES_MISSING_SQL).use { statement ->
            statement.setInt(1, afterId)
            statement.setLong(2, now)
            statement.setInt(3, limit)
            statement.executeQuery().use { rs ->
                val ids = mutableListOf<Int>()
                while (rs.next()) ids += rs.getInt("providerId")
                return ids
            }
        }
    }

    private fun connection(): Connection = DriverManager.getConnection("jdbc:sqlite::memory:").apply { createSchema() }

    @Test
    fun `the keyset cursor walks the whole catalog page by page without repeating`() {
        connection().use { connection ->
            connection.createStatement().use { statement ->
                (1..5).forEach { statement.execute("INSERT INTO vod_streams VALUES ($it, 'link-$it')") }
            }

            val seen = mutableListOf<Int>()
            var afterId = 0
            while (true) {
                val page = connection.page(afterId)
                if (page.isEmpty()) break
                seen += page
                afterId = page.last()
            }

            assertEquals(listOf(1, 2, 3, 4, 5), seen)
            assertEquals(seen.size, seen.toSet().size)
        }
    }

    @Test
    fun `a media already queued is never proposed again`() {
        connection().use { connection ->
            connection.createStatement().use { statement ->
                (1..3).forEach { statement.execute("INSERT INTO vod_streams VALUES ($it, 'link-$it')") }
                statement.execute("INSERT INTO external_hydration_queue VALUES ('movie', 2, 0)")
            }

            assertEquals(listOf(1, 3), connection.page(afterId = 0, limit = 10))
        }
    }

    @Test
    fun `a linked media is excluded, an expired unresolved cooldown comes back`() {
        connection().use { connection ->
            connection.createStatement().use { statement ->
                (1..3).forEach { statement.execute("INSERT INTO vod_streams VALUES ($it, 'link-$it')") }
                // 1 : déjà lié -> jamais reproposé.
                statement.execute("INSERT INTO external_media_links VALUES ('movie', 1, 'ext-1', $NOW, NULL)")
                // 2 : unresolved, cooldown encore actif -> exclu.
                statement.execute("INSERT INTO external_media_links VALUES ('movie', 2, NULL, $NOW, ${NOW + 60_000})")
                // 3 : unresolved, cooldown expiré -> à retenter.
                statement.execute("INSERT INTO external_media_links VALUES ('movie', 3, NULL, $NOW, ${NOW - 60_000})")
            }

            assertEquals(listOf(3), connection.page(afterId = 0, limit = 10))
        }
    }

    @Test
    fun `a page is capped by its limit and the next one resumes right after it`() {
        connection().use { connection ->
            connection.createStatement().use { statement ->
                (10..40 step 10).forEach { statement.execute("INSERT INTO vod_streams VALUES ($it, 'link-$it')") }
            }

            val first = connection.page(afterId = 0, limit = 2)
            assertEquals(listOf(10, 20), first)
            val second = connection.page(afterId = first.last(), limit = 2)
            assertEquals(listOf(30, 40), second)
            assertTrue(connection.page(afterId = second.last(), limit = 2).isEmpty())
        }
    }

    private companion object {
        const val NOW = 1_000_000L

        /** Copie exacte de `ExternalMetadataDao.findMoviesMissingExternalMetadata`. */
        val MOVIES_MISSING_SQL = "SELECT streamId AS providerId, linkKey FROM vod_streams WHERE streamId > ? " +
            "AND NOT EXISTS (SELECT 1 FROM external_media_links l WHERE l.kind = 'movie' AND l.providerId = vod_streams.streamId " +
            "AND (l.externalId IS NOT NULL OR l.retryAfter IS NULL OR l.retryAfter > ?)) " +
            "AND NOT EXISTS (SELECT 1 FROM external_hydration_queue q WHERE q.kind = 'movie' AND q.providerId = vod_streams.streamId) " +
            "ORDER BY streamId LIMIT ?"
    }
}
