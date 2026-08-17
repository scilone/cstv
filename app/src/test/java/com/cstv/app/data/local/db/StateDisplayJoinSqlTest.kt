package com.cstv.app.data.local.db

import com.cstv.app.data.local.dao.FAVORITE_LIST_QUERY
import com.cstv.app.data.local.dao.PLAYBACK_LIST_QUERY
import com.cstv.app.data.local.dao.RECENTLY_WATCHED_LIST_QUERY
import java.sql.Connection
import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T20 §4.4/T20-4 : rejoue les projections d'affichage réelles — importées
 * directement depuis les DAO (`FAVORITE_LIST_QUERY`, `PLAYBACK_LIST_QUERY`,
 * `RECENTLY_WATCHED_LIST_QUERY`), pas une copie qui pourrait diverger — sur
 * SQLite en mémoire, pour prouver les trois invariants de la validation
 * T20-4 : une cible absente du catalogue est masquée, elle réapparaît à son
 * retour, et aucune fuite ne traverse deux `accountKey` partageant le même
 * `providerId` (le cas justifiant `media_refs`, cf. T20 §4.1).
 *
 * Les requêtes Room utilisent des paramètres nommés (`:profileId`, ...),
 * inutilisables tels quels en JDBC brut : [withParams] fait une substitution
 * textuelle triviale, sûre ici puisque les valeurs sont des littéraux de
 * test, jamais une entrée utilisateur.
 */
class StateDisplayJoinSqlTest {

    private fun Connection.createSchema() {
        createStatement().use { statement ->
            statement.execute(
                "CREATE TABLE media_refs (mediaUid INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, accountKey TEXT NOT NULL, " +
                    "kind TEXT NOT NULL, providerId INTEGER NOT NULL)"
            )
            statement.execute(
                "CREATE TABLE favorites (profileId INTEGER NOT NULL, mediaUid INTEGER NOT NULL, addedAt INTEGER NOT NULL, " +
                    "PRIMARY KEY(profileId, mediaUid))"
            )
            statement.execute(
                "CREATE TABLE playback_positions (profileId INTEGER NOT NULL, mediaUid INTEGER NOT NULL, " +
                    "positionMs INTEGER NOT NULL, durationMs INTEGER NOT NULL, lastAccessedAt INTEGER NOT NULL, " +
                    "PRIMARY KEY(profileId, mediaUid))"
            )
            statement.execute(
                "CREATE TABLE recently_watched_live (profileId INTEGER NOT NULL, mediaUid INTEGER NOT NULL, " +
                    "watchedAt INTEGER NOT NULL, PRIMARY KEY(profileId, mediaUid))"
            )
            statement.execute(
                "CREATE TABLE live_streams (streamId INTEGER NOT NULL PRIMARY KEY, name TEXT NOT NULL, " +
                    "streamIcon TEXT, categoryId TEXT NOT NULL, num INTEGER NOT NULL DEFAULT 0)"
            )
            statement.execute(
                "CREATE TABLE vod_streams (streamId INTEGER NOT NULL PRIMARY KEY, name TEXT NOT NULL, " +
                    "streamIcon TEXT, categoryId TEXT NOT NULL, plot TEXT, duration TEXT, containerExtension TEXT, " +
                    "releaseYear INTEGER, genre TEXT, languageTag TEXT, qualityTag TEXT, versionLabel TEXT)"
            )
            statement.execute(
                "CREATE TABLE series_streams (seriesId INTEGER NOT NULL PRIMARY KEY, name TEXT NOT NULL, " +
                    "cover TEXT, categoryId TEXT NOT NULL, genre TEXT, languageTag TEXT, qualityTag TEXT, versionLabel TEXT)"
            )
            statement.execute(
                "CREATE TABLE series_episodes (episodeId INTEGER NOT NULL PRIMARY KEY, seriesId INTEGER NOT NULL, " +
                    "seasonNum INTEGER NOT NULL, episodeNum INTEGER NOT NULL, title TEXT NOT NULL, movieImage TEXT, " +
                    "containerExtension TEXT, plot TEXT, duration TEXT, releaseDate TEXT)"
            )
        }
    }

    private fun String.withParams(vararg pairs: Pair<String, String>): String =
        pairs.fold(this) { sql, (name, value) -> sql.replace(":$name", value) }

    private fun Connection.query(sql: String): List<Map<String, Any?>> =
        createStatement().use { statement ->
            statement.executeQuery(sql).use { rs ->
                val meta = rs.metaData
                val columns = (1..meta.columnCount).map { meta.getColumnLabel(it) }
                buildList {
                    while (rs.next()) add(columns.associateWith { rs.getObject(it) })
                }
            }
        }

    @Test
    fun `a favorite whose target left the catalogue is hidden, without deleting the favorite row`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createSchema()
            connection.createStatement().use { statement ->
                statement.execute("INSERT INTO media_refs(mediaUid, accountKey, kind, providerId) VALUES (1, 'acc-1', 'live', 900)")
                statement.execute("INSERT INTO favorites(profileId, mediaUid, addedAt) VALUES (1, 1, 100)")
                // Pas de ligne live_streams(900, ...) : la chaîne a quitté le bouquet.
            }

            val rows = connection.query(FAVORITE_LIST_QUERY.withParams("profileId" to "1", "accountKey" to "'acc-1'"))

            assertTrue(rows.isEmpty())
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) AS n FROM favorites").use { it.next(); assertEquals(1, it.getInt("n")) }
            }
        }
    }

    @Test
    fun `the same favorite reappears the moment its target returns to the catalogue`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createSchema()
            connection.createStatement().use { statement ->
                statement.execute("INSERT INTO media_refs(mediaUid, accountKey, kind, providerId) VALUES (1, 'acc-1', 'live', 900)")
                statement.execute("INSERT INTO favorites(profileId, mediaUid, addedAt) VALUES (1, 1, 100)")
            }
            assertTrue(connection.query(FAVORITE_LIST_QUERY.withParams("profileId" to "1", "accountKey" to "'acc-1'")).isEmpty())

            connection.createStatement().use { statement ->
                statement.execute("INSERT INTO live_streams(streamId, name, streamIcon, categoryId) VALUES (900, 'TF1', NULL, '1')")
            }

            val rows = connection.query(FAVORITE_LIST_QUERY.withParams("profileId" to "1", "accountKey" to "'acc-1'"))
            assertEquals(1, rows.size)
            assertEquals("TF1", rows.single()["name"])
        }
    }

    /**
     * Le cas motivant `media_refs` (T20 §4.1) : deux comptes dont les panels
     * réutilisent le même `providerId` numérique ne doivent jamais partager
     * un état, même s'ils pointent la même ligne catalogue physiquement.
     */
    @Test
    fun `two accounts sharing the same numeric providerId never see each other's favorite`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createSchema()
            connection.createStatement().use { statement ->
                statement.execute("INSERT INTO live_streams(streamId, name, streamIcon, categoryId) VALUES (900, 'TF1', NULL, '1')")
                statement.execute("INSERT INTO media_refs(mediaUid, accountKey, kind, providerId) VALUES (1, 'acc-1', 'live', 900)")
                statement.execute("INSERT INTO media_refs(mediaUid, accountKey, kind, providerId) VALUES (2, 'acc-2', 'live', 900)")
                // Seul le compte acc-1 a mis ce providerId en favori.
                statement.execute("INSERT INTO favorites(profileId, mediaUid, addedAt) VALUES (1, 1, 100)")
            }

            assertEquals(1, connection.query(FAVORITE_LIST_QUERY.withParams("profileId" to "1", "accountKey" to "'acc-1'")).size)
            assertTrue(connection.query(FAVORITE_LIST_QUERY.withParams("profileId" to "1", "accountKey" to "'acc-2'")).isEmpty())
        }
    }

    @Test
    fun `an episode resume position is hidden when its episode row is gone, and joins through the parent series`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createSchema()
            connection.createStatement().use { statement ->
                statement.execute("INSERT INTO series_streams(seriesId, name, cover, categoryId, genre) VALUES (50, 'Série', NULL, '1', 'Drame')")
                statement.execute(
                    "INSERT INTO series_episodes(episodeId, seriesId, seasonNum, episodeNum, title, containerExtension) " +
                        "VALUES (700, 50, 1, 1, 'E1', 'mkv')"
                )
                statement.execute("INSERT INTO media_refs(mediaUid, accountKey, kind, providerId) VALUES (1, 'acc-1', 'episode', 700)")
                statement.execute("INSERT INTO playback_positions(profileId, mediaUid, positionMs, durationMs, lastAccessedAt) VALUES (1, 1, 100, 200, 10)")
            }

            val rows = connection.query(PLAYBACK_LIST_QUERY.withParams("profileId" to "1", "accountKey" to "'acc-1'"))
            assertEquals(1, rows.size)
            assertEquals("E1", rows.single()["title"])
            assertEquals(50, (rows.single()["seriesId"] as Number).toInt())
            // Le genre vient de la série parente : un épisode n'en porte pas.
            assertEquals("Drame", rows.single()["genre"])

            connection.createStatement().use { it.execute("DELETE FROM series_episodes WHERE episodeId = 700") }

            assertTrue(connection.query(PLAYBACK_LIST_QUERY.withParams("profileId" to "1", "accountKey" to "'acc-1'")).isEmpty())
        }
    }

    @Test
    fun `recently-watched-live is filtered by profile and account, hidden when the channel is absent`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createSchema()
            connection.createStatement().use { statement ->
                statement.execute("INSERT INTO live_streams(streamId, name, streamIcon, categoryId) VALUES (900, 'TF1', NULL, '1')")
                statement.execute("INSERT INTO media_refs(mediaUid, accountKey, kind, providerId) VALUES (1, 'acc-1', 'live', 900)")
                statement.execute("INSERT INTO recently_watched_live(profileId, mediaUid, watchedAt) VALUES (1, 1, 500)")
                // Un autre profil, même compte : ne doit rien voir (filtre par profileId).
            }

            assertEquals(1, connection.query(RECENTLY_WATCHED_LIST_QUERY.withParams("profileId" to "1", "accountKey" to "'acc-1'", "limit" to "10")).size)
            assertTrue(connection.query(RECENTLY_WATCHED_LIST_QUERY.withParams("profileId" to "2", "accountKey" to "'acc-1'", "limit" to "10")).isEmpty())

            connection.createStatement().use { it.execute("DELETE FROM live_streams WHERE streamId = 900") }

            assertTrue(connection.query(RECENTLY_WATCHED_LIST_QUERY.withParams("profileId" to "1", "accountKey" to "'acc-1'", "limit" to "10")).isEmpty())
        }
    }
}
