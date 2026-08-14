package com.cstv.app.data.local.storage

import com.cstv.app.domain.model.IptvCloudBackupState
import com.cstv.app.domain.model.PendingCloudOp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class IptvCloudBackupStoreTest {
    @Test fun defaultsAreOptInAndChangingAccountResetsTheState() {
        val store = MemoryStore()
        assertFalse(store.get().consent)
        assertEquals(PendingCloudOp.NONE, store.get().pendingOp)
        store.save(IptvCloudBackupState(accountId = "a", consent = true, pendingOp = PendingCloudOp.DELETE))
        val reset = store.resetForAccount("b")
        assertEquals("b", reset.accountId)
        assertFalse(reset.consent)
        assertEquals(PendingCloudOp.NONE, reset.pendingOp)
    }
    private class MemoryStore : IptvCloudBackupStore {
        private var state = IptvCloudBackupState()
        override fun get() = state
        override fun save(state: IptvCloudBackupState) { this.state = state }
        override fun resetForAccount(accountId: String?): IptvCloudBackupState {
            if (state.accountId != accountId) state = IptvCloudBackupState(accountId = accountId)
            return state
        }
    }
}
