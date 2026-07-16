package com.poc.iptvxtream.data.local.storage

import android.content.SharedPreferences
import com.poc.iptvxtream.domain.model.Credentials
import java.security.GeneralSecurityException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests JVM de CredentialsManager : la logique (sérialisation, migration,
 * gestion d'erreur, purge) est testée avec un cipher factice et des
 * SharedPreferences en mémoire — le vrai chiffrement Keystore
 * (KeystoreCredentialsCipher) n'est exécutable que sur device.
 */
class CredentialsManagerTest {

    /** Cipher réversible trivial (inversion des octets) — pas de Keystore. */
    private class FakeCipher : CredentialsCipher {
        override fun encrypt(plaintext: ByteArray) = plaintext.reversedArray()
        override fun decrypt(payload: ByteArray) = payload.reversedArray()
    }

    /** Cipher dont le déchiffrement échoue systématiquement. */
    private class BrokenCipher : CredentialsCipher {
        override fun encrypt(plaintext: ByteArray) = plaintext
        override fun decrypt(payload: ByteArray): ByteArray =
            throw GeneralSecurityException("clé perdue")
    }

    private class InMemoryPrefs : SharedPreferences {
        val map = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = map
        override fun getString(key: String, defValue: String?) = map[key] as? String ?: defValue
        override fun getStringSet(key: String, defValues: MutableSet<String>?) = defValues
        override fun getInt(key: String, defValue: Int) = map[key] as? Int ?: defValue
        override fun getLong(key: String, defValue: Long) = map[key] as? Long ?: defValue
        override fun getFloat(key: String, defValue: Float) = map[key] as? Float ?: defValue
        override fun getBoolean(key: String, defValue: Boolean) = map[key] as? Boolean ?: defValue
        override fun contains(key: String) = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor()
        override fun registerOnSharedPreferenceChangeListener(
            l: SharedPreferences.OnSharedPreferenceChangeListener?
        ) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(
            l: SharedPreferences.OnSharedPreferenceChangeListener?
        ) = Unit

        inner class Editor : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private var clearRequested = false

            override fun putString(key: String, value: String?) = apply { pending[key] = value }
            override fun putStringSet(key: String, values: MutableSet<String>?) =
                apply { pending[key] = values }
            override fun putInt(key: String, value: Int) = apply { pending[key] = value }
            override fun putLong(key: String, value: Long) = apply { pending[key] = value }
            override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
            override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
            override fun remove(key: String) = apply { pending[key] = null }
            override fun clear() = apply { clearRequested = true }
            override fun commit(): Boolean {
                apply(); return true
            }
            override fun apply() {
                if (clearRequested) map.clear()
                pending.forEach { (k, v) -> if (v == null) map.remove(k) else map[k] = v }
            }
        }
    }

    private val credentials = Credentials(
        host = "http://panel.example.com",
        port = 8080,
        username = "user",
        password = "p@ss:word",
        rememberMe = true
    )

    private fun manager(
        prefs: InMemoryPrefs = InMemoryPrefs(),
        cipher: CredentialsCipher = FakeCipher(),
        legacy: LegacyCredentialsSource? = null
    ) = CredentialsManager(prefs, cipher, legacy)

    @Test
    fun `save puis get restitue les credentials`() {
        val manager = manager()
        manager.saveCredentials(credentials)
        assertEquals(credentials, manager.getCredentials())
    }

    @Test
    fun `les valeurs stockees ne sont pas en clair`() {
        val prefs = InMemoryPrefs()
        manager(prefs).saveCredentials(credentials)
        val stored = prefs.map.values.filterIsInstance<String>()
        assertTrue(stored.isNotEmpty())
        stored.forEach {
            assertTrue(!it.contains("panel.example.com") && !it.contains("p@ss:word"))
        }
    }

    @Test
    fun `getCredentials retourne null si rien de stocke`() {
        assertNull(manager().getCredentials())
    }

    @Test
    fun `echec de dechiffrement purge et retourne null`() {
        val prefs = InMemoryPrefs()
        manager(prefs).saveCredentials(credentials)

        val broken = manager(prefs, cipher = BrokenCipher())
        assertNull(broken.getCredentials())
        assertTrue(prefs.map.isEmpty())
    }

    @Test
    fun `clearCredentials vide le stockage`() {
        val prefs = InMemoryPrefs()
        val manager = manager(prefs)
        manager.saveCredentials(credentials)
        manager.clearCredentials()
        assertNull(manager.getCredentials())
        assertTrue(prefs.map.isEmpty())
    }

    @Test
    fun `migration legacy au premier acces`() {
        val legacy = object : LegacyCredentialsSource {
            var purged = false
            override fun readAndPurge(): Credentials {
                purged = true
                return credentials
            }
        }
        val prefs = InMemoryPrefs()
        val manager = manager(prefs, legacy = legacy)

        assertEquals(credentials, manager.getCredentials())
        assertTrue(legacy.purged)
        assertTrue(prefs.map.isNotEmpty())
    }

    @Test
    fun `pas de migration si des credentials existent deja au nouveau format`() {
        val prefs = InMemoryPrefs()
        manager(prefs).saveCredentials(credentials)

        var called = false
        val legacy = object : LegacyCredentialsSource {
            override fun readAndPurge(): Credentials? {
                called = true
                return null
            }
        }
        assertEquals(credentials, manager(prefs, legacy = legacy).getCredentials())
        assertTrue(!called)
    }

    @Test
    fun `migration absente ne cree rien`() {
        val legacy = object : LegacyCredentialsSource {
            override fun readAndPurge(): Credentials? = null
        }
        assertNull(manager(legacy = legacy).getCredentials())
    }
}
