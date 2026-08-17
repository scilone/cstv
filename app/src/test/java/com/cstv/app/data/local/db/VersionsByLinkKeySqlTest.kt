package com.cstv.app.data.local.db

import java.sql.Connection
import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * F39 §8.2, tâche 2 : rejoue la requête réelle `VodDao.getStreamsByLinkKey` /
 * `SeriesDao.getStreamsByLinkKey` (groupement par `linkKey`, compatibilité
 * d'année, plafond) sur SQLite en mémoire — même motif que
 * [StateDisplayJoinSqlTest] et [CoveringIndexF39SqlTest].
 */
class VersionsByLinkKeySqlTest {

    private companion object {
        const val VOD_QUERY = "SELECT * FROM vod_streams WHERE linkKey = ? AND linkKey != '' " +
            "AND (? IS NULL OR ? <= 0 OR releaseYear IS NULL OR releaseYear <= 0 OR releaseYear = ?) " +
            "ORDER BY streamId ASC LIMIT ?"
    }

    private fun Connection.createVodTable() {
        createStatement().use {
            it.execute(
                "CREATE TABLE vod_streams (streamId INTEGER PRIMARY KEY, name TEXT NOT NULL, " +
                    "linkKey TEXT NOT NULL DEFAULT '', releaseYear INTEGER, qualityTag TEXT)"
            )
        }
    }

    private fun Connection.insertVod(id: Int, linkKey: String, year: Int?, quality: String?) {
        prepareStatement("INSERT INTO vod_streams(streamId, name, linkKey, releaseYear, qualityTag) VALUES (?, ?, ?, ?, ?)").use { ps ->
            ps.setInt(1, id)
            ps.setString(2, "Film $id")
            ps.setString(3, linkKey)
            if (year == null) ps.setNull(4, java.sql.Types.INTEGER) else ps.setInt(4, year)
            ps.setString(5, quality)
            ps.executeUpdate()
        }
    }

    private fun Connection.queryVersions(linkKey: String, year: Int?, limit: Int): List<Int> =
        prepareStatement(VOD_QUERY).use { ps ->
            ps.setString(1, linkKey)
            if (year == null) ps.setNull(2, java.sql.Types.INTEGER) else ps.setInt(2, year)
            if (year == null) ps.setNull(3, java.sql.Types.INTEGER) else ps.setInt(3, year)
            if (year == null) ps.setNull(4, java.sql.Types.INTEGER) else ps.setInt(4, year)
            ps.setInt(5, limit)
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.getInt("streamId")) } }
        }

    @Test
    fun `groups only entries sharing the same non-empty linkKey`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createVodTable()
            connection.insertVod(1, "key-a", null, null)
            connection.insertVod(2, "key-a", null, null)
            connection.insertVod(3, "key-b", null, null)
            connection.insertVod(4, "", null, null) // pas encore normalisé (T21) : jamais regroupé

            assertEquals(listOf(1, 2), connection.queryVersions("key-a", null, limit = 20))
        }
    }

    @Test
    fun `an entry with an unknown release year stays a candidate for any requested year`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createVodTable()
            connection.insertVod(1, "key-a", 2020, null)
            connection.insertVod(2, "key-a", null, null) // année inconnue -> reste candidate
            connection.insertVod(3, "key-a", 2019, null) // année incompatible -> exclue

            assertEquals(listOf(1, 2), connection.queryVersions("key-a", 2020, limit = 20))
        }
    }

    @Test
    fun `the defensive cap truncates an abnormally large linkKey group`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createVodTable()
            repeat(25) { connection.insertVod(it + 1, "key-huge", null, null) }

            assertEquals(20, connection.queryVersions("key-huge", null, limit = 20).size)
        }
    }
}
