package com.cstv.app.data.local.db

import java.sql.Connection
import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * T20 §4.3 : preuve SQL de la raison d'être du remplacement `@Insert(REPLACE)`
 * → `@Upsert` + suppression différentielle par `cachedAt` sur `live_streams`
 * et `series_streams` (`LiveTvDao`/`SeriesDao`). Même limitation que
 * [Migration27To28SqlTest] (pas d'`androidTest`/`MigrationTestHelper`) : le
 * schéma Room 28 pertinent est rejoué à la main sur SQLite en mémoire, et le
 * SQL exercé reproduit exactement la sémantique compilée par Room pour
 * `@Upsert` (`INSERT ... ON CONFLICT(pk) DO UPDATE`), par opposition à
 * `INSERT OR REPLACE` qui supprime puis réinsère la ligne en conflit.
 *
 * La preuve tient en une phrase : `REPLACE` déclenche `ON DELETE CASCADE`
 * sur un enfant même quand le parent est *maintenu* (son `streamId` reste le
 * même) ; `ON CONFLICT DO UPDATE` ne le déclenche jamais, parce qu'aucune
 * ligne n'est supprimée.
 */
class CatalogUpsertSqlTest {

    private fun Connection.createRoom28CatalogSchema() {
        createStatement().use { statement ->
            statement.execute("PRAGMA foreign_keys = ON")
            statement.execute(
                "CREATE TABLE live_streams (streamId INTEGER NOT NULL PRIMARY KEY, name TEXT NOT NULL, " +
                    "categoryId TEXT NOT NULL, cachedAt INTEGER NOT NULL)"
            )
            statement.execute(
                "CREATE TABLE epg_cache (streamId INTEGER NOT NULL, startTimestamp INTEGER NOT NULL, " +
                    "endTimestamp INTEGER NOT NULL, title TEXT NOT NULL, cachedAt INTEGER NOT NULL, " +
                    "PRIMARY KEY(streamId, startTimestamp), " +
                    "FOREIGN KEY(streamId) REFERENCES live_streams(streamId) ON DELETE CASCADE)"
            )
            statement.execute(
                "CREATE TABLE series_streams (seriesId INTEGER NOT NULL PRIMARY KEY, name TEXT NOT NULL, " +
                    "categoryId TEXT NOT NULL, cachedAt INTEGER NOT NULL)"
            )
            statement.execute(
                "CREATE TABLE series_seasons (seriesId INTEGER NOT NULL, seasonNumber INTEGER NOT NULL, " +
                    "name TEXT NOT NULL, cachedAt INTEGER NOT NULL, PRIMARY KEY(seriesId, seasonNumber), " +
                    "FOREIGN KEY(seriesId) REFERENCES series_streams(seriesId) ON DELETE CASCADE)"
            )
            statement.execute(
                "CREATE TABLE series_episodes (episodeId INTEGER NOT NULL PRIMARY KEY, seriesId INTEGER NOT NULL, " +
                    "title TEXT NOT NULL, cachedAt INTEGER NOT NULL, " +
                    "FOREIGN KEY(seriesId) REFERENCES series_streams(seriesId) ON DELETE CASCADE)"
            )
            // Un état utilisateur qui référence le stream : preuve que le
            // remplacement différentiel ne le perd jamais tant que le stream
            // reste dans le lot synchronisé (contrairement à un clear+insert).
            statement.execute(
                "CREATE TABLE media_refs (mediaUid INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, accountKey TEXT NOT NULL, " +
                    "kind TEXT NOT NULL, providerId INTEGER NOT NULL)"
            )
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
    fun `INSERT OR REPLACE -- the old behavior -- destroys epg_cache even for a stream that is still there`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createRoom28CatalogSchema()
            connection.createStatement().use { statement ->
                statement.execute("INSERT INTO live_streams(streamId, name, categoryId, cachedAt) VALUES (900, 'Chaîne', '1', 0)")
                statement.execute("INSERT INTO epg_cache(streamId, startTimestamp, endTimestamp, title, cachedAt) VALUES (900, 0, 100, 'Prog', 0)")
            }

            // Un refresh de catalogue qui revoit exactement le même streamId,
            // via l'ancien `INSERT OR REPLACE` : documente le bug corrigé, pas
            // le comportement voulu.
            connection.createStatement().use { statement ->
                statement.execute("INSERT OR REPLACE INTO live_streams(streamId, name, categoryId, cachedAt) VALUES (900, 'Chaîne', '1', 1)")
            }

            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) AS n FROM epg_cache").use {
                    it.next(); assertEquals(0, it.getInt("n"))
                }
            }
        }
    }

    @Test
    fun `upsert via ON CONFLICT DO UPDATE preserves epg_cache for a stream still present in the batch`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createRoom28CatalogSchema()
            connection.createStatement().use { statement ->
                statement.execute("INSERT INTO live_streams(streamId, name, categoryId, cachedAt) VALUES (900, 'Chaîne', '1', 0)")
                statement.execute("INSERT INTO epg_cache(streamId, startTimestamp, endTimestamp, title, cachedAt) VALUES (900, 0, 100, 'Prog', 0)")
            }

            // Sémantique exacte compilée par Room pour `@Upsert` sur une entité
            // dont la clé primaire est `streamId` : mêmes lignes réécrites en
            // place, jamais supprimées.
            connection.createStatement().use { statement ->
                statement.execute(
                    "INSERT INTO live_streams(streamId, name, categoryId, cachedAt) VALUES (900, 'Chaîne', '1', 1) " +
                        "ON CONFLICT(streamId) DO UPDATE SET name = excluded.name, categoryId = excluded.categoryId, cachedAt = excluded.cachedAt"
                )
            }

            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) AS n FROM epg_cache").use {
                    it.next(); assertEquals(1, it.getInt("n"))
                }
                statement.executeQuery("SELECT cachedAt FROM live_streams WHERE streamId = 900").use {
                    it.next(); assertEquals(1L, it.getLong("cachedAt"))
                }
            }
            assertEquals(0, connection.foreignKeyViolations())
        }
    }

    @Test
    fun `differential delete by batchTs removes only the truly absent stream, cascading its EPG, and keeps the maintained one`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createRoom28CatalogSchema()
            connection.createStatement().use { statement ->
                // Deux chaînes existantes avant la synchronisation, chacune avec son EPG.
                statement.execute("INSERT INTO live_streams(streamId, name, categoryId, cachedAt) VALUES (900, 'Maintenue', '1', 0)")
                statement.execute("INSERT INTO epg_cache(streamId, startTimestamp, endTimestamp, title, cachedAt) VALUES (900, 0, 100, 'Prog A', 0)")
                statement.execute("INSERT INTO live_streams(streamId, name, categoryId, cachedAt) VALUES (901, 'Disparue', '1', 0)")
                statement.execute("INSERT INTO epg_cache(streamId, startTimestamp, endTimestamp, title, cachedAt) VALUES (901, 0, 100, 'Prog B', 0)")
                // Un favori qui pointe encore vers la chaîne disparue : preuve
                // que la disparition catalogue n'entraîne aucune cascade sur
                // l'état utilisateur (règle du lien d'affichage, pas de FK
                // état → catalogue), cf. T20 §4.4.
                statement.execute("INSERT INTO media_refs(accountKey, kind, providerId) VALUES ('acc', 'live', 901)")
            }

            // Le lot resynchronisé ne revoit que 900 (`batchTs` = 1) : upsert du
            // lot, puis suppression des seules lignes non revues -- exactement
            // `LiveTvDao.replaceAllStreams`.
            connection.createStatement().use { statement ->
                statement.execute(
                    "INSERT INTO live_streams(streamId, name, categoryId, cachedAt) VALUES (900, 'Maintenue', '1', 1) " +
                        "ON CONFLICT(streamId) DO UPDATE SET name = excluded.name, categoryId = excluded.categoryId, cachedAt = excluded.cachedAt"
                )
                statement.execute("DELETE FROM live_streams WHERE cachedAt < 1")
            }

            connection.createStatement().use { statement ->
                // Chaîne maintenue : EPG intact.
                statement.executeQuery("SELECT COUNT(*) AS n FROM epg_cache WHERE streamId = 900").use {
                    it.next(); assertEquals(1, it.getInt("n"))
                }
                // Chaîne réellement absente : supprimée, son EPG avec elle (cascade).
                statement.executeQuery("SELECT COUNT(*) AS n FROM live_streams WHERE streamId = 901").use {
                    it.next(); assertEquals(0, it.getInt("n"))
                }
                statement.executeQuery("SELECT COUNT(*) AS n FROM epg_cache WHERE streamId = 901").use {
                    it.next(); assertEquals(0, it.getInt("n"))
                }
                // Le favori orphelin de catalogue survit : c'est le rôle de la
                // jointure d'affichage (invisible tant que la cible est absente),
                // jamais d'une cascade destructrice.
                statement.executeQuery("SELECT COUNT(*) AS n FROM media_refs WHERE providerId = 901").use {
                    it.next(); assertEquals(1, it.getInt("n"))
                }
            }
            assertEquals(0, connection.foreignKeyViolations())
        }
    }

    @Test
    fun `the same differential upsert-then-delete principle applies to series, cascading seasons and episodes`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createRoom28CatalogSchema()
            connection.createStatement().use { statement ->
                statement.execute("INSERT INTO series_streams(seriesId, name, categoryId, cachedAt) VALUES (50, 'Maintenue', '1', 0)")
                statement.execute("INSERT INTO series_seasons(seriesId, seasonNumber, name, cachedAt) VALUES (50, 1, 'S1', 0)")
                statement.execute("INSERT INTO series_episodes(episodeId, seriesId, title, cachedAt) VALUES (700, 50, 'E1', 0)")
                statement.execute("INSERT INTO series_streams(seriesId, name, categoryId, cachedAt) VALUES (51, 'Disparue', '1', 0)")
                statement.execute("INSERT INTO series_seasons(seriesId, seasonNumber, name, cachedAt) VALUES (51, 1, 'S1', 0)")
                statement.execute("INSERT INTO series_episodes(episodeId, seriesId, title, cachedAt) VALUES (701, 51, 'E1', 0)")
            }

            connection.createStatement().use { statement ->
                statement.execute(
                    "INSERT INTO series_streams(seriesId, name, categoryId, cachedAt) VALUES (50, 'Maintenue', '1', 1) " +
                        "ON CONFLICT(seriesId) DO UPDATE SET name = excluded.name, categoryId = excluded.categoryId, cachedAt = excluded.cachedAt"
                )
                statement.execute("DELETE FROM series_streams WHERE cachedAt < 1")
            }

            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) AS n FROM series_seasons WHERE seriesId = 50").use { it.next(); assertEquals(1, it.getInt("n")) }
                statement.executeQuery("SELECT COUNT(*) AS n FROM series_episodes WHERE seriesId = 50").use { it.next(); assertEquals(1, it.getInt("n")) }
                statement.executeQuery("SELECT COUNT(*) AS n FROM series_seasons WHERE seriesId = 51").use { it.next(); assertEquals(0, it.getInt("n")) }
                statement.executeQuery("SELECT COUNT(*) AS n FROM series_episodes WHERE seriesId = 51").use { it.next(); assertEquals(0, it.getInt("n")) }
            }
            assertEquals(0, connection.foreignKeyViolations())
        }
    }
}
