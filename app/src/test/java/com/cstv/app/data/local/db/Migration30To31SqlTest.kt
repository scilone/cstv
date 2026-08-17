package com.cstv.app.data.local.db

import java.sql.Connection
import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T23 : SQL-only migration coverage, suivant le motif sqlite-jdbc existant (voir
 * `Migration29To30SqlTest`). Vérifie que la migration crée `playback_repair_profiles` sans
 * toucher `media_refs`, et que la contrainte `mediaUid` unique + `ON DELETE CASCADE` fonctionne.
 */
class Migration30To31SqlTest {
    private fun Connection.createRoom30Catalog() = createStatement().use { statement ->
        statement.execute("PRAGMA foreign_keys = ON")
        statement.execute("CREATE TABLE media_refs (mediaUid INTEGER NOT NULL PRIMARY KEY, accountKey TEXT NOT NULL, kind TEXT NOT NULL, providerId INTEGER NOT NULL)")
        statement.execute("INSERT INTO media_refs VALUES (1, 'acc', 'movie', 10)")
    }

    @Test
    fun `migration creates playback_repair_profiles without touching media_refs`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createRoom30Catalog()
            connection.createStatement().use { statement -> migration30To31Statements().forEach(statement::execute) }

            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) AS n FROM media_refs").use { assertTrue(it.next()); assertEquals(1, it.getInt("n")) }
                statement.executeQuery("SELECT COUNT(*) AS n FROM playback_repair_profiles").use { assertTrue(it.next()); assertEquals(0, it.getInt("n")) }

                statement.executeQuery("PRAGMA index_list(playback_repair_profiles)").use { indexes ->
                    val names = generateSequence { if (indexes.next()) indexes.getString("name") else null }.toSet()
                    assertTrue("index_playback_repair_profiles_mediaUid" in names)
                }
            }
        }
    }

    @Test
    fun `upsert replaces the previous profile for the same media - single row per mediaUid`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createRoom30Catalog()
            connection.createStatement().use { statement -> migration30To31Statements().forEach(statement::execute) }

            connection.createStatement().use { statement ->
                statement.execute("INSERT INTO playback_repair_profiles VALUES (1, 'DEFAULT', NULL, NULL, 1000, 1)")
                statement.execute("INSERT OR REPLACE INTO playback_repair_profiles VALUES (1, 'SOFTWARE_PREFERRED', NULL, NULL, 2000, 1)")
            }

            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) AS n, decoderStrategy FROM playback_repair_profiles").use {
                    assertTrue(it.next()); assertEquals(1, it.getInt("n")); assertEquals("SOFTWARE_PREFERRED", it.getString("decoderStrategy"))
                }
            }
        }
    }

    @Test
    fun `deleting the referenced media_refs row cascades to its repair profile`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createRoom30Catalog()
            connection.createStatement().use { statement -> migration30To31Statements().forEach(statement::execute) }
            connection.createStatement().use { statement -> statement.execute("INSERT INTO playback_repair_profiles VALUES (1, 'DEFAULT', NULL, NULL, 1000, 1)") }

            connection.createStatement().use { statement -> statement.execute("DELETE FROM media_refs WHERE mediaUid = 1") }

            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) AS n FROM playback_repair_profiles").use { assertTrue(it.next()); assertEquals(0, it.getInt("n")) }
            }
        }
    }

    /**
     * Review R10 (T23 §12) : `MediaRefDao.purgeUnreferenced` couvre bien `playback_repair_profiles`
     * — vérifié ici directement en SQL (la requête est reproduite à l'identique) faute d'infra Room
     * in-memory pour ce DAO dans les tests existants du projet. Un média encore référencé
     * uniquement par un profil de réparation doit survivre à la purge ; un média sans aucune
     * référence doit disparaître.
     */
    @Test
    fun `purgeUnreferenced query keeps a media_refs row still referenced only by playback_repair_profiles`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createRoom30Catalog() // insère déjà media_refs(1, ...)
            connection.createStatement().use { statement ->
                migration30To31Statements().forEach(statement::execute)
                statement.execute("INSERT INTO media_refs VALUES (2, 'acc', 'movie', 20)") // sans aucune référence
                // Tables d'état minimales (seule mediaUid importe pour cette requête) — les six
                // autres restent vides, seule playback_repair_profiles référence le média 1.
                statement.execute("CREATE TABLE favorites (mediaUid INTEGER)")
                statement.execute("CREATE TABLE playback_positions (mediaUid INTEGER)")
                statement.execute("CREATE TABLE recently_watched_live (mediaUid INTEGER)")
                statement.execute("CREATE TABLE media_ratings (mediaUid INTEGER)")
                statement.execute("CREATE TABLE track_preferences (mediaUid INTEGER)")
                statement.execute("CREATE TABLE series_watch_state (mediaUid INTEGER)")
                statement.execute("CREATE TABLE downloaded_media (mediaUid INTEGER)")
                statement.execute("INSERT INTO playback_repair_profiles VALUES (1, 'DEFAULT', NULL, NULL, 1000, 1)")
            }

            connection.createStatement().use { statement ->
                // Requête reproduite à l'identique de `MediaRefDao.purgeUnreferenced`.
                statement.execute(
                    "DELETE FROM media_refs WHERE mediaUid NOT IN (" +
                        "SELECT mediaUid FROM favorites " +
                        "UNION SELECT mediaUid FROM playback_positions " +
                        "UNION SELECT mediaUid FROM recently_watched_live " +
                        "UNION SELECT mediaUid FROM media_ratings " +
                        "UNION SELECT mediaUid FROM track_preferences " +
                        "UNION SELECT mediaUid FROM series_watch_state " +
                        "UNION SELECT mediaUid FROM downloaded_media " +
                        "UNION SELECT mediaUid FROM playback_repair_profiles)"
                )
            }

            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT mediaUid FROM media_refs ORDER BY mediaUid").use {
                    assertTrue(it.next()); assertEquals(1, it.getInt("mediaUid"))
                    assertTrue("mediaUid=2 n'était référencé nulle part, il doit disparaître", !it.next())
                }
                // Le profil de réparation du média survivant n'a pas été délogé par une cascade.
                statement.executeQuery("SELECT COUNT(*) AS n FROM playback_repair_profiles").use { assertTrue(it.next()); assertEquals(1, it.getInt("n")) }
            }
        }
    }
}
