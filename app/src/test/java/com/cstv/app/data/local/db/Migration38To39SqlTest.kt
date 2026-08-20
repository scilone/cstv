package com.cstv.app.data.local.db

import java.sql.Connection
import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F45 (Tâche 5) : `MIGRATION_38_39` est purement additive — elle ne fait que créer les nouvelles
 * tables provider-neutral (`external_media`/`external_movies`/`external_series`/
 * `external_media_links`/`external_hydration_queue`). Le SQL réel de [migration38To39Statements]
 * est rejoué ici sur un SQLite en mémoire (`org.xerial:sqlite-jdbc`), motif déjà utilisé par
 * [Migration27To28SqlTest] : le projet n'a pas d'infrastructure de test instrumenté
 * (`MigrationTestHelper`), voir AGENTS.md.
 *
 * Une fixture Room 38 minimaliste (`canonical_media_links`, `content_classifications`,
 * `trailer_cache`, `profiles`) est peuplée à la main pour prouver que ces caches T24/F44 —
 * volontairement conservés le temps que les tâches Repository/consommateurs basculent dessus
 * (§ 11 F45) — traversent la migration sans perte.
 */
class Migration38To39SqlTest {

    private fun Connection.createRoom38Schema() {
        createStatement().use { statement ->
            statement.execute(
                "CREATE TABLE profiles (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, " +
                    "avatarId INTEGER NOT NULL, createdAt INTEGER NOT NULL, remoteId TEXT, maxAgeRating INTEGER)"
            )
            statement.execute(
                "CREATE TABLE canonical_media_links (kind TEXT NOT NULL, providerId INTEGER NOT NULL, " +
                    "canonicalId TEXT NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(kind, providerId))"
            )
            statement.execute(
                "CREATE TABLE content_classifications (kind TEXT NOT NULL, providerId INTEGER NOT NULL, " +
                    "title TEXT NOT NULL, year INTEGER, ageRating INTEGER, resolvedAt INTEGER NOT NULL, " +
                    "PRIMARY KEY(kind, providerId))"
            )
            statement.execute(
                "CREATE TABLE trailer_cache (mediaType TEXT NOT NULL, catalogId INTEGER NOT NULL, videoId TEXT, " +
                    "source TEXT, resolvedTmdbId INTEGER, resolvedAt INTEGER NOT NULL, " +
                    "PRIMARY KEY(mediaType, catalogId))"
            )
            statement.execute("INSERT INTO profiles VALUES (1, 'Nico', 0, 1000, NULL, NULL)")
            statement.execute("INSERT INTO canonical_media_links VALUES ('movie', 42, 'movie:12345', 1000)")
            statement.execute("INSERT INTO content_classifications VALUES ('movie', 42, 'The Thing', 1982, 17, 1000)")
            statement.execute("INSERT INTO trailer_cache VALUES ('movie', 42, 'abc123', 'youtube', 12345, 1000)")
        }
    }

    private fun Connection.applyMigration38To39() {
        createStatement().use { statement ->
            migration38To39Statements().forEach(statement::execute)
        }
    }

    private fun Connection.applyMigration39To40() {
        createStatement().use { statement ->
            migration39To40Statements().forEach(statement::execute)
        }
    }

    @Test
    fun `pre-existing T24 and F44 caches survive the migration untouched`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createRoom38Schema()
            connection.applyMigration38To39()

            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT canonicalId FROM canonical_media_links WHERE kind = 'movie' AND providerId = 42").use {
                    assertTrue(it.next())
                    assertEquals("movie:12345", it.getString(1))
                }
                statement.executeQuery("SELECT ageRating FROM content_classifications WHERE kind = 'movie' AND providerId = 42").use {
                    assertTrue(it.next())
                    assertEquals(17, it.getInt(1))
                }
                statement.executeQuery("SELECT resolvedTmdbId FROM trailer_cache WHERE mediaType = 'movie' AND catalogId = 42").use {
                    assertTrue(it.next())
                    assertEquals(12345, it.getInt(1))
                }
                statement.executeQuery("SELECT name FROM profiles WHERE id = 1").use {
                    assertTrue(it.next())
                    assertEquals("Nico", it.getString(1))
                }
            }
        }
    }

    @Test
    fun `all F45 external relation tables and indices exist after the migrations`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createRoom38Schema()
            connection.applyMigration38To39()
            connection.applyMigration39To40()

            val expectedTables = setOf(
                "external_media", "external_movies", "external_series",
                "external_media_links", "external_catalog_links", "external_hydration_queue", "external_seasons",
                "external_episodes", "external_media_genres", "external_media_keywords",
                "external_media_origin_countries", "external_series_episode_runtimes",
                "external_alternative_titles", "external_recommendations", "external_videos"
            )
            val expectedIndices = setOf(
                "index_external_media_kind",
                "index_external_media_links_externalId", "index_external_media_links_linkKey",
                "index_external_catalog_links_externalId",
                "index_external_hydration_queue_priority_nextAttemptAt",
                "index_external_episodes_externalId_seasonNumber"
            )
            connection.createStatement().use { statement ->
                statement.executeQuery(
                    "SELECT name FROM sqlite_master WHERE type = 'table' AND name LIKE 'external_%'"
                ).use { rows ->
                    val found = mutableSetOf<String>()
                    while (rows.next()) found += rows.getString(1)
                    assertEquals(expectedTables, found)
                }
                statement.executeQuery(
                    "SELECT name FROM sqlite_master WHERE type = 'index' AND name LIKE 'index_external_%'"
                ).use { rows ->
                    val found = mutableSetOf<String>()
                    while (rows.next()) found += rows.getString(1)
                    assertEquals(expectedIndices, found)
                }
            }
        }
    }

    @Test
    fun `provider trailer ids are removed rather than converted into a fake external id`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createRoom38Schema()
            connection.applyMigration38To39()
            connection.applyMigration39To40()
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT videoId, externalId FROM trailer_cache WHERE mediaType = 'movie' AND catalogId = 42").use {
                    assertTrue(it.next())
                    assertEquals("abc123", it.getString("videoId"))
                    assertEquals(null, it.getString("externalId"))
                }
            }
        }
    }

    @Test
    fun `legacy canonical links are purged and replaced by an empty external-id cache`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createRoom38Schema()
            connection.applyMigration38To39()
            connection.applyMigration39To40()
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) FROM external_catalog_links").use {
                    assertTrue(it.next())
                    assertEquals(0, it.getInt(1))
                }
                statement.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'canonical_media_links'").use {
                    assertTrue(!it.next())
                }
            }
        }
    }

    @Test
    fun `external identity links and queue preserve opaque ids and multi-version links`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createRoom38Schema()
            connection.applyMigration38To39()

            connection.createStatement().use { statement ->
                statement.execute(
                    "INSERT INTO external_media VALUES ('5e37ba2a-1cda-4faf-9f10-335b2f6556a7', 'movie', 1, NULL, NULL)"
                )
                statement.execute(
                    "INSERT INTO external_media_links VALUES " +
                        "('movie', 10, '5e37ba2a-1cda-4faf-9f10-335b2f6556a7', 'work', 96, 'title+year', 1, 1, 1, NULL)"
                )
                statement.execute(
                    "INSERT INTO external_media_links VALUES " +
                        "('movie', 11, '5e37ba2a-1cda-4faf-9f10-335b2f6556a7', 'work', 96, 'title+year', 1, 1, 1, NULL)"
                )
                statement.execute("INSERT INTO external_hydration_queue VALUES ('movie', 10, 'MISSING_METADATA', 1, 1, 1, 0)")
                statement.executeQuery(
                    "SELECT providerId FROM external_media_links WHERE externalId = '5e37ba2a-1cda-4faf-9f10-335b2f6556a7' ORDER BY providerId"
                ).use { rows ->
                    assertTrue(rows.next()); assertEquals(10, rows.getInt(1))
                    assertTrue(rows.next()); assertEquals(11, rows.getInt(1))
                }
            }
        }
    }

    @Test
    fun `running the migration twice is idempotent`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createRoom38Schema()
            connection.applyMigration38To39()
            connection.applyMigration38To39()
        }
    }
}
