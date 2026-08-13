package com.cstv.app.data.local.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.cstv.app.data.local.dao.DbMaintenanceDao
import com.cstv.app.data.local.entity.DbMaintenanceEntity
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.io.File

/**
 * T20-6 : le `VACUUM` demandé par la migration 27→28 ne s'exécute que quand
 * il est sûr — espace disponible, hors transaction — et une reprise après
 * échec ou refus laisse toujours la base et la demande intactes.
 */
class DatabaseMaintenanceRunnerTest {
    @get:Rule val globalTimeout: Timeout = Timeout.seconds(60)

    @Mock private lateinit var context: Context
    @Mock private lateinit var database: AppDatabase
    @Mock private lateinit var dbMaintenanceDao: DbMaintenanceDao
    @Mock private lateinit var openHelper: SupportSQLiteOpenHelper
    @Mock private lateinit var sqliteDatabase: SupportSQLiteDatabase

    private lateinit var dbFile: File
    private lateinit var runner: DatabaseMaintenanceRunner

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        dbFile = File.createTempFile("iptv_xtream_cache", ".db")
        dbFile.writeBytes(ByteArray(1024))
        whenever(context.getDatabasePath(AppDatabase.DATABASE_NAME)).thenReturn(dbFile)
        whenever(database.openHelper).thenReturn(openHelper)
        whenever(openHelper.writableDatabase).thenReturn(sqliteDatabase)
    }

    private fun runnerWithFreeBytes(freeBytes: Long) = DatabaseMaintenanceRunner(
        context, database, dbMaintenanceDao,
        DiskSpaceProbe { freeBytes },
    )

    @Test
    fun `no pending request touches nothing`() = runTest {
        whenever(dbMaintenanceDao.get("vacuum")).thenReturn(null)

        runnerWithFreeBytes(Long.MAX_VALUE).run()

        verify(dbMaintenanceDao, never()).complete(org.mockito.kotlin.any())
        verifyNoMoreInteractions(openHelper)
    }

    @Test
    fun `missing db file clears the stale request without touching the database`() = runTest {
        whenever(dbMaintenanceDao.get("vacuum")).thenReturn(DbMaintenanceEntity("vacuum", 10L))
        whenever(context.getDatabasePath(AppDatabase.DATABASE_NAME)).thenReturn(File("/nonexistent/does-not-exist.db"))

        runnerWithFreeBytes(Long.MAX_VALUE).run()

        verify(dbMaintenanceDao).complete("vacuum")
        verifyNoMoreInteractions(openHelper)
    }

    @Test
    fun `insufficient free space leaves the request in place -- retried at next startup`() = runTest {
        whenever(dbMaintenanceDao.get("vacuum")).thenReturn(DbMaintenanceEntity("vacuum", 10L))

        runnerWithFreeBytes(freeBytes = dbFile.length()).run()

        verify(dbMaintenanceDao, never()).complete(org.mockito.kotlin.any())
        verifyNoMoreInteractions(openHelper)
    }

    @Test
    fun `enough free space runs VACUUM outside a transaction and clears the request`() = runTest {
        whenever(dbMaintenanceDao.get("vacuum")).thenReturn(DbMaintenanceEntity("vacuum", 10L))

        runnerWithFreeBytes(freeBytes = dbFile.length() + 1).run()

        verify(sqliteDatabase).execSQL("VACUUM")
        verify(dbMaintenanceDao).complete("vacuum")
    }

    @Test
    fun `a VACUUM failure leaves a valid request for the next startup, never marked complete`() = runTest {
        whenever(dbMaintenanceDao.get("vacuum")).thenReturn(DbMaintenanceEntity("vacuum", 10L))
        whenever(sqliteDatabase.execSQL("VACUUM")).doThrow(RuntimeException("disk I/O error"))

        runnerWithFreeBytes(freeBytes = dbFile.length() + 1).run()

        verify(dbMaintenanceDao, never()).complete(org.mockito.kotlin.any())
    }
}
