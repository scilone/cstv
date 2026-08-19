package com.cstv.app.data.local.db

import java.sql.Connection
import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * F44 : `MIGRATION_35_36` ajoute `profiles.maxAgeRating`, nullable, sans
 * backfill. Un profil créé avant la migration doit rester non bridé
 * (`NULL`) après son passage, jamais bridé par défaut.
 */
class Migration35To36SqlTest {

    private fun Connection.createV35Schema() {
        createStatement().use { statement ->
            statement.execute(
                "CREATE TABLE profiles (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, " +
                    "avatarId INTEGER NOT NULL, createdAt INTEGER NOT NULL, remoteId TEXT)"
            )
            statement.execute(
                "INSERT INTO profiles(id, name, avatarId, createdAt, remoteId) VALUES (1, 'Nico', 0, 1000, NULL)"
            )
        }
    }

    @Test
    fun `existing profiles stay unbridged after the migration`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createV35Schema()
            connection.createStatement().use { it.execute("ALTER TABLE profiles ADD COLUMN maxAgeRating INTEGER") }

            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT maxAgeRating FROM profiles WHERE id = 1").use {
                    it.next()
                    it.getInt("maxAgeRating")
                    assertNull(it.getObject("maxAgeRating"))
                }
            }
        }
    }

    @Test
    fun `a bridged level can be written and read back`(): Unit {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createV35Schema()
            connection.createStatement().use { it.execute("ALTER TABLE profiles ADD COLUMN maxAgeRating INTEGER") }
            connection.createStatement().use { it.execute("UPDATE profiles SET maxAgeRating = 12 WHERE id = 1") }

            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT maxAgeRating FROM profiles WHERE id = 1").use {
                    it.next()
                    assertEquals(12, it.getInt("maxAgeRating"))
                }
            }
        }
    }
}
