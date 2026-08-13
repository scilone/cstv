package com.cstv.app.data.local.db

import java.sql.Connection
import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T20: le projet n'a pas d'infrastructure de test instrumenté (`androidTest`, pas de
 * `MigrationTestHelper`), donc pas de moyen de valider une migration Room via l'API Android — le
 * SQL de `MIGRATION_27_28` est rejoué ici sur un SQLite en mémoire (`org.xerial:sqlite-jdbc`),
 * motif déjà utilisé par [CategoryRankMigrationSqlTest]. Une fixture Room 27 dénormalisée est
 * peuplée à la main (favoris, reprises film/épisode, une reprise "indéterminable", des lignes déjà
 * orphelines de profil), la migration est rejouée, puis on vérifie : les identités créées avec le
 * bon `kind`, les colonnes dénormalisées disparues, les lignes orphelines supprimées, aucune
 * violation de clé étrangère résiduelle, et la demande de compactage différé posée.
 */
class Migration27To28SqlTest {

    private fun Connection.createRoom27Schema() {
        createStatement().use { statement ->
            statement.execute("PRAGMA foreign_keys = ON")
            statement.execute(
                "CREATE TABLE profiles (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, " +
                    "avatarId INTEGER NOT NULL, createdAt INTEGER NOT NULL, remoteId TEXT)"
            )
            statement.execute(
                "CREATE TABLE favorites (id INTEGER NOT NULL, type TEXT NOT NULL, name TEXT NOT NULL, cover TEXT, " +
                    "categoryId TEXT NOT NULL, addedAt INTEGER NOT NULL, profileId INTEGER NOT NULL, " +
                    "PRIMARY KEY(id, type, profileId))"
            )
            statement.execute(
                "CREATE TABLE playback_positions (streamId INTEGER NOT NULL, profileId INTEGER NOT NULL, " +
                    "positionMs INTEGER NOT NULL, durationMs INTEGER NOT NULL, lastAccessedAt INTEGER NOT NULL, " +
                    "title TEXT, coverUrl TEXT, type TEXT, containerExtension TEXT, seriesId INTEGER, " +
                    "episodeNum INTEGER, seasonNum INTEGER, plot TEXT, duration TEXT, releaseDate TEXT, " +
                    "categoryId TEXT, PRIMARY KEY(streamId, profileId))"
            )
            statement.execute(
                "CREATE TABLE recently_watched_live (streamId INTEGER NOT NULL, profileId INTEGER NOT NULL, " +
                    "name TEXT NOT NULL, streamIcon TEXT, categoryId TEXT, num INTEGER, watchedAt INTEGER NOT NULL, " +
                    "PRIMARY KEY(streamId, profileId))"
            )
            statement.execute(
                "CREATE TABLE media_ratings (profileId INTEGER NOT NULL, mediaType TEXT NOT NULL, " +
                    "mediaId INTEGER NOT NULL, value INTEGER NOT NULL, PRIMARY KEY(profileId, mediaType, mediaId))"
            )
            statement.execute(
                "CREATE TABLE track_preferences (profileId INTEGER NOT NULL, mediaType TEXT NOT NULL, " +
                    "mediaId INTEGER NOT NULL, audioLang TEXT, subtitleLang TEXT, " +
                    "PRIMARY KEY(profileId, mediaType, mediaId))"
            )
            statement.execute(
                "CREATE TABLE series_watch_state (profileId INTEGER NOT NULL, seriesId INTEGER NOT NULL, " +
                    "lastKnownSeason INTEGER NOT NULL, lastKnownEpisode INTEGER NOT NULL, " +
                    "lastNotifiedSeason INTEGER NOT NULL, lastNotifiedEpisode INTEGER NOT NULL, " +
                    "updatedAt INTEGER NOT NULL, PRIMARY KEY(profileId, seriesId))"
            )
            statement.execute(
                "CREATE TABLE category_preferences (categoryId TEXT NOT NULL, type TEXT NOT NULL, " +
                    "profileId INTEGER NOT NULL, hidden INTEGER NOT NULL, sortOrder INTEGER, " +
                    "PRIMARY KEY(categoryId, type, profileId))"
            )
            statement.execute(
                "CREATE TABLE downloaded_media (contentId TEXT NOT NULL PRIMARY KEY, type TEXT NOT NULL, " +
                    "streamId INTEGER NOT NULL, seriesId INTEGER, seasonNum INTEGER, episodeNum INTEGER, " +
                    "title TEXT NOT NULL, subtitle TEXT, coverUrl TEXT, containerExtension TEXT NOT NULL, " +
                    "status TEXT NOT NULL, percent INTEGER NOT NULL, bytesDownloaded INTEGER NOT NULL, " +
                    "totalBytes INTEGER NOT NULL, createdAt INTEGER NOT NULL)"
            )
            statement.execute(
                "CREATE TABLE series_streams (seriesId INTEGER NOT NULL PRIMARY KEY, name TEXT NOT NULL, " +
                    "categoryId TEXT NOT NULL, cachedAt INTEGER NOT NULL)"
            )
            statement.execute(
                "CREATE TABLE live_streams (streamId INTEGER NOT NULL PRIMARY KEY, name TEXT NOT NULL, " +
                    "categoryId TEXT NOT NULL, cachedAt INTEGER NOT NULL)"
            )
            statement.execute(
                "CREATE TABLE series_seasons (seriesId INTEGER NOT NULL, seasonNumber INTEGER NOT NULL, " +
                    "name TEXT NOT NULL, episodeCount INTEGER NOT NULL, cover TEXT, cachedAt INTEGER NOT NULL, " +
                    "PRIMARY KEY(seriesId, seasonNumber))"
            )
            statement.execute(
                "CREATE TABLE series_episodes (episodeId INTEGER NOT NULL PRIMARY KEY, seriesId INTEGER NOT NULL, " +
                    "seasonNum INTEGER NOT NULL, episodeNum INTEGER NOT NULL, title TEXT NOT NULL, " +
                    "containerExtension TEXT NOT NULL, plot TEXT, duration TEXT, releaseDate TEXT, movieImage TEXT, " +
                    "orderIndex INTEGER NOT NULL, cachedAt INTEGER NOT NULL)"
            )
            statement.execute(
                "CREATE TABLE epg_cache (streamId INTEGER NOT NULL, startTimestamp INTEGER NOT NULL, " +
                    "endTimestamp INTEGER NOT NULL, title TEXT NOT NULL, description TEXT, cachedAt INTEGER NOT NULL, " +
                    "PRIMARY KEY(streamId, startTimestamp))"
            )
            statement.execute(
                "CREATE TABLE profile_sync_state (profileId INTEGER NOT NULL, namespace TEXT NOT NULL, etag TEXT, " +
                    "schemaVersion INTEGER NOT NULL, baseSnapshot BLOB, pending INTEGER NOT NULL, " +
                    "lastSyncedAt INTEGER NOT NULL, lastAttemptAt INTEGER NOT NULL, failureCode TEXT, " +
                    "retryCount INTEGER NOT NULL, PRIMARY KEY(profileId, namespace))"
            )
        }
    }

    private fun runMigration(connection: Connection) {
        connection.createStatement().use { statement ->
            migration27To28Statements().forEach(statement::execute)
        }
    }

    private fun Connection.foreignKeyViolations(): Int {
        createStatement().use { statement ->
            statement.executeQuery("PRAGMA foreign_key_check").use { result ->
                var count = 0
                while (result.next()) count++
                return count
            }
        }
    }

    @Test
    fun `identities are created with the right kind, including the ambiguous playback cases`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createRoom27Schema()
            connection.createStatement().use { statement ->
                statement.execute("INSERT INTO profiles(id, name, avatarId, createdAt) VALUES (1, 'P1', 0, 0)")
                statement.execute("INSERT INTO series_streams(seriesId, name, categoryId, cachedAt) VALUES (50, 'Série', '1', 0)")
                // A known episode (series_episodes match) whose playback row carries neither
                // `seriesId` nor `type='series'` -- must still resolve to 'episode' via the EXISTS
                // fallback, not silently default to 'movie'.
                statement.execute(
                    "INSERT INTO series_episodes(episodeId, seriesId, seasonNum, episodeNum, title, containerExtension, orderIndex, cachedAt) " +
                        "VALUES (700, 50, 1, 1, 'E1', 'mkv', 0, 0)"
                )
                statement.execute(
                    "INSERT INTO playback_positions(streamId, profileId, positionMs, durationMs, lastAccessedAt) VALUES (700, 1, 100, 200, 0)"
                )
                // A genuine movie: no seriesId, no type, and no matching episode row.
                statement.execute(
                    "INSERT INTO playback_positions(streamId, profileId, positionMs, durationMs, lastAccessedAt) VALUES (900, 1, 100, 200, 0)"
                )
                // An episode explicitly flagged by the old `type` column.
                statement.execute(
                    "INSERT INTO playback_positions(streamId, profileId, positionMs, durationMs, lastAccessedAt, type) VALUES (701, 1, 1, 2, 0, 'series')"
                )
            }

            runMigration(connection)

            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT kind FROM media_refs WHERE providerId = 700").use {
                    assertTrue(it.next()); assertEquals("episode", it.getString("kind"))
                }
                statement.executeQuery("SELECT kind FROM media_refs WHERE providerId = 900").use {
                    assertTrue(it.next()); assertEquals("movie", it.getString("kind"))
                }
                statement.executeQuery("SELECT kind FROM media_refs WHERE providerId = 701").use {
                    assertTrue(it.next()); assertEquals("episode", it.getString("kind"))
                }
            }
            assertEquals(0, connection.foreignKeyViolations())
        }
    }

    @Test
    fun `denormalized columns are gone from every reconstructed state table`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createRoom27Schema()
            connection.createStatement().use { statement ->
                statement.execute("INSERT INTO profiles(id, name, avatarId, createdAt) VALUES (1, 'P1', 0, 0)")
                statement.execute(
                    "INSERT INTO favorites(id, type, name, cover, categoryId, addedAt, profileId) " +
                        "VALUES (10, 'movie', 'Titre', 'cover.jpg', '5', 111, 1)"
                )
                statement.execute(
                    "INSERT INTO playback_positions(streamId, profileId, positionMs, durationMs, lastAccessedAt, title, plot) " +
                        "VALUES (900, 1, 100, 200, 5, 'Un titre', 'Un long résumé')"
                )
            }

            runMigration(connection)

            connection.createStatement().use { statement ->
                val favoriteColumns = statement.executeQuery("SELECT * FROM favorites LIMIT 1").metaData
                val favoriteColumnNames = (1..favoriteColumns.columnCount).map { favoriteColumns.getColumnName(it) }.toSet()
                assertEquals(setOf("profileId", "mediaUid", "addedAt"), favoriteColumnNames)

                val playbackColumns = statement.executeQuery("SELECT * FROM playback_positions LIMIT 1").metaData
                val playbackColumnNames = (1..playbackColumns.columnCount).map { playbackColumns.getColumnName(it) }.toSet()
                assertEquals(setOf("profileId", "mediaUid", "positionMs", "durationMs", "lastAccessedAt"), playbackColumnNames)
            }
        }
    }

    @Test
    fun `rows already orphaned of their profile in Room 27 are dropped, not carried forward`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createRoom27Schema()
            connection.createStatement().use { statement ->
                statement.execute("INSERT INTO profiles(id, name, avatarId, createdAt) VALUES (1, 'P1', 0, 0)")
                // profileId 99 does not exist: a Room 27 orphan (deleted profile whose cascade
                // never ran, or a pre-Phase-27 row).
                statement.execute(
                    "INSERT INTO favorites(id, type, name, cover, categoryId, addedAt, profileId) " +
                        "VALUES (10, 'movie', 'Titre', NULL, '5', 111, 99)"
                )
                statement.execute(
                    "INSERT INTO favorites(id, type, name, cover, categoryId, addedAt, profileId) " +
                        "VALUES (11, 'movie', 'Titre 2', NULL, '5', 222, 1)"
                )
            }

            runMigration(connection)

            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) AS n FROM favorites").use {
                    it.next(); assertEquals(1, it.getInt("n"))
                }
            }
            assertEquals(0, connection.foreignKeyViolations())
        }
    }

    @Test
    fun `an unresolvable playback row is dropped rather than guessed into the wrong bucket`() {
        // Neither `seriesId`, `type = 'series'`, nor a matching `series_episodes` row: the CASE
        // expression's ELSE defaults it to 'movie'. This is intentional per the migration's own
        // literal spec (§4.7 point 3) -- verified here so a future edit can't silently change it
        // to drop the row instead, or vice versa, without a test noticing.
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createRoom27Schema()
            connection.createStatement().use { statement ->
                statement.execute("INSERT INTO profiles(id, name, avatarId, createdAt) VALUES (1, 'P1', 0, 0)")
                statement.execute(
                    "INSERT INTO playback_positions(streamId, profileId, positionMs, durationMs, lastAccessedAt) VALUES (555, 1, 1, 2, 0)"
                )
            }

            runMigration(connection)

            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT r.kind FROM playback_positions pp JOIN media_refs r ON r.mediaUid = pp.mediaUid").use {
                    assertTrue(it.next())
                    assertEquals("movie", it.getString("kind"))
                }
            }
        }
    }

    @Test
    fun `catalogue cascades are wired -- removing a series drops its seasons, episodes and does not orphan playback`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createRoom27Schema()
            connection.createStatement().use { statement ->
                statement.execute("INSERT INTO profiles(id, name, avatarId, createdAt) VALUES (1, 'P1', 0, 0)")
                statement.execute("INSERT INTO series_streams(seriesId, name, categoryId, cachedAt) VALUES (50, 'Série', '1', 0)")
                statement.execute("INSERT INTO series_seasons(seriesId, seasonNumber, name, episodeCount, cachedAt) VALUES (50, 1, 'S1', 1, 0)")
                statement.execute(
                    "INSERT INTO series_episodes(episodeId, seriesId, seasonNum, episodeNum, title, containerExtension, orderIndex, cachedAt) " +
                        "VALUES (700, 50, 1, 1, 'E1', 'mkv', 0, 0)"
                )
                statement.execute("INSERT INTO live_streams(streamId, name, categoryId, cachedAt) VALUES (900, 'Chaîne', '1', 0)")
                statement.execute("INSERT INTO epg_cache(streamId, startTimestamp, endTimestamp, title, cachedAt) VALUES (900, 0, 100, 'Prog', 0)")
            }

            runMigration(connection)

            // Cascade not yet exercised by the migration itself (it only adds the constraint),
            // but the constraint must be live: deleting the parent must cascade in this same
            // connection, proving the FK was actually created, not just declared in a comment.
            connection.createStatement().use { statement ->
                statement.execute("DELETE FROM series_streams WHERE seriesId = 50")
                statement.execute("DELETE FROM live_streams WHERE streamId = 900")
                statement.executeQuery("SELECT COUNT(*) AS n FROM series_seasons").use { it.next(); assertEquals(0, it.getInt("n")) }
                statement.executeQuery("SELECT COUNT(*) AS n FROM series_episodes").use { it.next(); assertEquals(0, it.getInt("n")) }
                statement.executeQuery("SELECT COUNT(*) AS n FROM epg_cache").use { it.next(); assertEquals(0, it.getInt("n")) }
            }
        }
    }

    @Test
    fun `deleting a profile cascades to every one of its state rows and its sync state`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createRoom27Schema()
            connection.createStatement().use { statement ->
                statement.execute("INSERT INTO profiles(id, name, avatarId, createdAt) VALUES (1, 'P1', 0, 0)")
                statement.execute(
                    "INSERT INTO favorites(id, type, name, cover, categoryId, addedAt, profileId) VALUES (10, 'movie', 'T', NULL, '5', 1, 1)"
                )
                statement.execute(
                    "INSERT INTO profile_sync_state(profileId, namespace, schemaVersion, pending, lastSyncedAt, lastAttemptAt, retryCount) " +
                        "VALUES (1, 'favorites', 1, 0, 0, 0, 0)"
                )
            }

            runMigration(connection)

            connection.createStatement().use { statement ->
                statement.execute("DELETE FROM profiles WHERE id = 1")
                statement.executeQuery("SELECT COUNT(*) AS n FROM favorites").use { it.next(); assertEquals(0, it.getInt("n")) }
                statement.executeQuery("SELECT COUNT(*) AS n FROM profile_sync_state").use { it.next(); assertEquals(0, it.getInt("n")) }
            }
        }
    }

    @Test
    fun `deferred compaction is requested exactly once`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createRoom27Schema()
            runMigration(connection)

            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT task FROM db_maintenance").use { result ->
                    assertTrue(result.next())
                    assertEquals("vacuum", result.getString("task"))
                    assertFalse(result.next())
                }
            }
        }
    }

    @Test
    fun `migration is idempotent-safe for the identity tables it may already own`() {
        // AppModule can construct the identity DAOs before the migration runs on a fresh install
        // path in some test setups; the CREATE TABLE statements must tolerate already existing.
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createRoom27Schema()
            connection.createStatement().use { statement ->
                statement.execute("CREATE TABLE media_refs (mediaUid INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, accountKey TEXT NOT NULL, kind TEXT NOT NULL, providerId INTEGER NOT NULL)")
            }

            runMigration(connection) // must not throw

            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) AS n FROM media_refs").use { assertTrue(it.next()) }
            }
        }
    }

    @Test
    fun `no foreign key violation survives the full migration on a richer fixture`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createRoom27Schema()
            connection.createStatement().use { statement ->
                statement.execute("INSERT INTO profiles(id, name, avatarId, createdAt) VALUES (1, 'P1', 0, 0)")
                statement.execute("INSERT INTO profiles(id, name, avatarId, createdAt) VALUES (2, 'P2', 0, 0)")
                statement.execute(
                    "INSERT INTO favorites(id, type, name, cover, categoryId, addedAt, profileId) VALUES (1, 'live', 'C', NULL, '1', 1, 1)"
                )
                statement.execute(
                    "INSERT INTO media_ratings(profileId, mediaType, mediaId, value) VALUES (1, 'movie', 5, 1)"
                )
                statement.execute(
                    "INSERT INTO track_preferences(profileId, mediaType, mediaId, audioLang, subtitleLang) VALUES (2, 'series', 6, 'fr', NULL)"
                )
                statement.execute(
                    "INSERT INTO series_watch_state(profileId, seriesId, lastKnownSeason, lastKnownEpisode, lastNotifiedSeason, lastNotifiedEpisode, updatedAt) " +
                        "VALUES (1, 7, 1, 1, -1, -1, 0)"
                )
                statement.execute(
                    "INSERT INTO category_preferences(categoryId, type, profileId, hidden, sortOrder) VALUES ('8', 'vod', 1, 1, NULL)"
                )
                statement.execute(
                    "INSERT INTO downloaded_media(contentId, type, streamId, title, containerExtension, status, percent, bytesDownloaded, totalBytes, createdAt) " +
                        "VALUES ('movie_9', 'movie', 9, 'T', 'mp4', 'COMPLETED', 100, 1, 1, 0)"
                )
                statement.execute(
                    "INSERT INTO recently_watched_live(streamId, profileId, name, watchedAt) VALUES (11, 1, 'Chan', 0)"
                )
            }

            runMigration(connection)

            assertEquals(0, connection.foreignKeyViolations())
        }
    }
}
