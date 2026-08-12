package com.cstv.app.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.rules.Timeout
import java.io.File

private const val APP_ID = "com.cstv.app"

/** Inspecteur fake : le nom du fichier encode `<package>|<versionCode>`, ou vide s'il est illisible. */
private class FakeApkInspector(private val entries: MutableMap<String, InspectedApk> = mutableMapOf()) : ApkInspector {
    fun register(file: File, packageName: String, versionCode: Int) {
        entries[file.name] = InspectedApk(packageName, versionCode)
    }

    override fun inspect(file: File): InspectedApk? = entries[file.name]
}

class UpdateApkStoreTest {

    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var directory: File
    private lateinit var inspector: FakeApkInspector
    private lateinit var store: UpdateApkStore

    @Before
    fun setUp() {
        directory = tempFolder.newFolder("app_updates")
        inspector = FakeApkInspector()
        store = UpdateApkStore(directory, inspector, APP_ID)
    }

    @Test
    fun `promotePart renames the part file atomically`() {
        val versionCode = 17_800
        store.ensureDirectory()
        store.partFileFor(versionCode).writeBytes(byteArrayOf(1, 2, 3))

        val final = store.promotePart(versionCode)

        assertTrue(final.exists())
        assertFalse(store.partFileFor(versionCode).exists())
        assertEquals(3, final.length())
    }

    @Test
    fun `findReusableApk returns null when nothing is cached`() {
        assertNull(store.findReusableApk(17_800, expectedSizeBytes = 10))
    }

    @Test
    fun `findReusableApk rejects a size mismatch`() {
        val file = store.finalFileFor(17_800).apply { parentFile?.mkdirs(); writeBytes(ByteArray(5)) }
        inspector.register(file, APP_ID, 17_800)

        assertNull(store.findReusableApk(17_800, expectedSizeBytes = 999))
    }

    @Test
    fun `findReusableApk rejects wrong package or version`() {
        val file = store.finalFileFor(17_800).apply { parentFile?.mkdirs(); writeBytes(ByteArray(5)) }
        inspector.register(file, "com.other.app", 17_800)

        assertNull(store.findReusableApk(17_800, expectedSizeBytes = 5))
    }

    @Test
    fun `findReusableApk accepts a matching cached apk`() {
        val file = store.finalFileFor(17_800).apply { parentFile?.mkdirs(); writeBytes(ByteArray(5)) }
        inspector.register(file, APP_ID, 17_800)

        assertEquals(file, store.findReusableApk(17_800, expectedSizeBytes = 5))
    }

    @Test
    fun `F35-R2 validatePromoted accepts a matching freshly promoted apk`() {
        val file = store.finalFileFor(17_800).apply { parentFile?.mkdirs(); writeBytes(ByteArray(5)) }
        inspector.register(file, APP_ID, 17_800)

        assertEquals(file, store.validatePromoted(17_800, expectedSizeBytes = 5))
    }

    @Test
    fun `F35-R2 validatePromoted deletes and rejects a truncated download`() {
        val file = store.finalFileFor(17_800).apply { parentFile?.mkdirs(); writeBytes(ByteArray(2)) }
        inspector.register(file, APP_ID, 17_800)

        assertNull(store.validatePromoted(17_800, expectedSizeBytes = 5))
        assertFalse(file.exists())
    }

    @Test
    fun `F35-R2 validatePromoted deletes and rejects a wrong-package asset`() {
        val file = store.finalFileFor(17_800).apply { parentFile?.mkdirs(); writeBytes(ByteArray(5)) }
        inspector.register(file, "com.other.app", 17_800)

        assertNull(store.validatePromoted(17_800, expectedSizeBytes = 5))
        assertFalse(file.exists())
    }

    @Test
    fun `purge deletes interrupted part files`() {
        store.ensureDirectory()
        store.partFileFor(17_800).writeBytes(byteArrayOf(1))

        store.purge(installedVersionCode = 17_706)

        assertFalse(store.partFileFor(17_800).exists())
    }

    @Test
    fun `purge deletes an apk of an obsolete or lower version`() {
        val obsolete = store.finalFileFor(17_706).apply { parentFile?.mkdirs(); writeBytes(ByteArray(1)) }
        inspector.register(obsolete, APP_ID, 17_706)

        store.purge(installedVersionCode = 17_706)

        assertFalse(obsolete.exists())
    }

    @Test
    fun `purge deletes an unreadable or foreign apk`() {
        val unreadable = File(directory, "cstv-99999.apk").apply { parentFile?.mkdirs(); writeBytes(ByteArray(1)) }
        // Non enregistré dans le fake inspecteur : inspect() renvoie null.

        store.purge(installedVersionCode = 17_706)

        assertFalse(unreadable.exists())
    }

    @Test
    fun `purge keeps a valid apk of a higher version (CA16)`() {
        val required = store.finalFileFor(20_000).apply { parentFile?.mkdirs(); writeBytes(ByteArray(1)) }
        inspector.register(required, APP_ID, 20_000)

        store.purge(installedVersionCode = 17_706)

        assertTrue(required.exists())
    }
}
