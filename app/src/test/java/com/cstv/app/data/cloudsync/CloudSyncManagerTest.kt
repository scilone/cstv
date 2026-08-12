package com.cstv.app.data.cloudsync

import com.cstv.app.data.cloudsync.merge.SnapshotMerger
import com.cstv.app.data.local.dao.ProfileDao
import com.cstv.app.data.local.dao.ProfileSyncStateDao
import com.cstv.app.data.local.entity.ProfileEntity
import com.cstv.app.data.local.entity.ProfileSyncStateEntity
import com.cstv.app.data.local.storage.CstvSessionManager
import com.cstv.app.data.remote.CstvErrorMapper
import com.cstv.app.data.remote.api.CstvObjectsApiService
import com.cstv.app.data.remote.dto.ObjectListDto
import com.cstv.app.data.remote.dto.ObjectMetadataDto
import com.cstv.app.domain.model.CstvSession
import com.cstv.app.domain.network.NetworkMonitor
import com.cstv.app.domain.sync.CloudSyncStatus
import com.cstv.app.domain.sync.SyncNamespace
import com.google.gson.Gson
import com.google.gson.JsonPrimitive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import retrofit2.Response

/**
 * Covers the retained/documented paths of 5.2-5.4: creation without `If-Match`,
 * update with the current ETag, the fast ETag-match skip, the bounded 412 retry,
 * the offline short-circuit, the oversized-payload guard, and the first-fusion
 * merge (5.5/5.4 "sans base"), including the push-after-merge fix (a merge that
 * adds content the server doesn't have must still be uploaded even when nothing
 * was explicitly `pending`).
 */
class CloudSyncManagerTest {
    @get:Rule val globalTimeout: Timeout = Timeout.seconds(60)

    private val profileId = 7
    private val remoteId = "remote-profile-1"
    private val namespace = SyncNamespace.FAVORITES

    @Mock private lateinit var profiles: ProfileDao
    @Mock private lateinit var states: ProfileSyncStateDao
    @Mock private lateinit var objects: CstvObjectsApiService
    @Mock private lateinit var network: NetworkMonitor
    @Mock private lateinit var sessions: CstvSessionManager

    private val gson = Gson()
    private lateinit var codec: SnapshotCodec
    private lateinit var merger: SnapshotMerger
    private lateinit var serializer: FakeSnapshotSerializer
    private lateinit var manager: CloudSyncManagerImpl

    // `synchronizeProfile` walks all seven namespaces; every test below exercises
    // FAVORITES specifically, so the other six are pinned to a harmless
    // already-in-sync state (matching ETag, not pending) that short-circuits
    // before any network call other than the shared listing.
    private val otherNamespaces = SyncNamespace.entries.filterNot { it == namespace }

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        codec = SnapshotCodec(gson)
        merger = SnapshotMerger()
        serializer = FakeSnapshotSerializer()
        whenever(network.isCurrentlyOnline()).thenReturn(true)
        whenever(sessions.get()).thenReturn(CstvSession("token", "account-1", "mail@example.test", Long.MAX_VALUE))
        manager = CloudSyncManagerImpl(
            profiles, states, objects, serializer, codec, merger, CstvErrorMapper(gson), network, sessions,
            appContext = org.mockito.kotlin.mock(), scope = CoroutineScope(Dispatchers.Unconfined)
        )
    }

    /** Stubs the mocks that every test needs (suspend calls, so out of `@Before`). */
    private suspend fun commonStubs() {
        val activeProfile = ProfileEntity(id = profileId, name = "P", avatarId = 0, createdAt = 0L, remoteId = remoteId)
        whenever(profiles.getById(profileId)).thenReturn(activeProfile)
        otherNamespaces.forEach { ns ->
            whenever(states.get(profileId, ns.wireName))
                .thenReturn(ProfileSyncStateEntity(profileId, ns.wireName, etag = "idle", pending = false))
        }
        stubListing(null)
    }

    private fun entry(obj: String, value: String) = obj to JsonPrimitive(value)
    private fun snapshotOf(vararg entries: Pair<String, JsonPrimitive>) =
        NamespaceSnapshot(SnapshotCodec.SCHEMA_VERSION, namespace.wireName, entries.toMap())

    /** Sets the FAVORITES metadata under test; the other six stay "idle" (see [otherNamespaces]). */
    private suspend fun stubListing(metadata: ObjectMetadataDto?) {
        val idle = otherNamespaces.map { ObjectMetadataDto(it.wireName, "idle", 1, 10, null) }
        whenever(objects.listObjects(remoteId)).thenReturn(Response.success(ObjectListDto(idle + listOfNotNull(metadata))))
    }

    private suspend fun okPut(etag: String = "server-etag") {
        // `ifMatch` is null on a first-ever create: `any()` alone does not match a
        // null argument in mockito-kotlin, hence `anyOrNull()` here.
        whenever(objects.putObject(eq(remoteId), eq(namespace.wireName), any(), org.mockito.kotlin.anyOrNull(), any()))
            .thenReturn(Response.success(Unit, okhttp3.Headers.headersOf("ETag", "\"$etag\"")))
    }

    private fun bodyOf(snapshot: NamespaceSnapshot): okhttp3.ResponseBody {
        val bytes = (codec.encode(snapshot) as SnapshotEncodeResult.Success).bytes
        return bytes.toResponseBody("application/vnd.cstv.blob+gzip".toMediaType())
    }

    @Test
    fun `identical etag and no pending change skips the network entirely`() = runTest {
        commonStubs()
        whenever(states.get(profileId, namespace.wireName))
            .thenReturn(ProfileSyncStateEntity(profileId, namespace.wireName, etag = "same", pending = false))
        stubListing(ObjectMetadataDto(namespace.wireName, "same", 1, 10, null))

        manager.synchronizeProfile(profileId)

        verify(objects, never()).getObject(any(), any())
        verify(objects, never()).putObject(any(), any(), any(), any(), any())
    }

    @Test
    fun `namespace never created remotely is pushed without If-Match`() = runTest {
        commonStubs()
        whenever(states.get(profileId, namespace.wireName)).thenReturn(null)
        stubListing(null)
        serializer.snapshots[namespace] = snapshotOf(entry("live:1", "chan"))
        okPut("first-etag")

        manager.synchronizeProfile(profileId)

        verify(objects).putObject(eq(remoteId), eq(namespace.wireName), any(), eq(null), any())
    }

    @Test
    fun `pending mutation with unchanged remote etag is pushed with If-Match`() = runTest {
        commonStubs()
        whenever(states.get(profileId, namespace.wireName))
            .thenReturn(ProfileSyncStateEntity(profileId, namespace.wireName, etag = "current", pending = true))
        stubListing(ObjectMetadataDto(namespace.wireName, "current", 1, 10, null))
        serializer.snapshots[namespace] = snapshotOf(entry("live:1", "chan"))
        okPut("next-etag")

        manager.synchronizeProfile(profileId)

        verify(objects).putObject(eq(remoteId), eq(namespace.wireName), any(), eq("\"current\""), any())
    }

    @Test
    fun `first contact importing remote-only content does not need a push back`() = runTest {
        commonStubs()
        // base == null (never synced before), local has nothing new, remote already has the object.
        whenever(states.get(profileId, namespace.wireName))
            .thenReturn(ProfileSyncStateEntity(profileId, namespace.wireName, etag = null, pending = false))
        val remote = snapshotOf(entry("movie:1", "already-on-server"))
        stubListing(ObjectMetadataDto(namespace.wireName, "remote-etag", 1, 10, null))
        whenever(objects.getObject(remoteId, namespace.wireName)).thenReturn(Response.success(bodyOf(remote)))
        serializer.snapshots[namespace] = snapshotOf() // nothing locally yet

        manager.synchronizeProfile(profileId)

        assertEquals(remote.objects, serializer.applied[namespace]?.objects)
        verify(objects, never()).putObject(any(), any(), any(), any(), any())
    }

    @Test
    fun `first contact with local-only content is pushed back even though nothing was pending`() = runTest {
        commonStubs()
        // This is the bug this review found: base == null, pending == false, but local
        // holds an object the server has never seen. It must not be silently dropped.
        whenever(states.get(profileId, namespace.wireName))
            .thenReturn(ProfileSyncStateEntity(profileId, namespace.wireName, etag = null, pending = false))
        val remote = snapshotOf(entry("movie:1", "server-only"))
        stubListing(ObjectMetadataDto(namespace.wireName, "remote-etag", 1, 10, null))
        whenever(objects.getObject(remoteId, namespace.wireName)).thenReturn(Response.success(bodyOf(remote)))
        serializer.snapshots[namespace] = snapshotOf(entry("movie:2", "device-only"))
        okPut("merged-etag")

        manager.synchronizeProfile(profileId)

        val applied = serializer.applied[namespace]
        assertEquals(setOf("movie:1", "movie:2"), applied?.objects?.keys)
        verify(objects).putObject(eq(remoteId), eq(namespace.wireName), any(), eq("\"remote-etag\""), any())
    }

    @Test
    fun `repeated 412 gives up after the bounded retry count`() = runTest {
        commonStubs()
        whenever(states.get(profileId, namespace.wireName))
            .thenReturn(ProfileSyncStateEntity(profileId, namespace.wireName, etag = "current", pending = true))
        stubListing(ObjectMetadataDto(namespace.wireName, "current", 1, 10, null))
        serializer.snapshots[namespace] = snapshotOf(entry("live:1", "chan"))
        whenever(objects.putObject(eq(remoteId), eq(namespace.wireName), any(), any(), any()))
            .thenReturn(Response.error<Unit>(412, "{}".toResponseBody("application/json".toMediaType())))

        manager.synchronizeProfile(profileId)

        verify(objects, org.mockito.kotlin.times(3)).putObject(eq(remoteId), eq(namespace.wireName), any(), any(), any())
        assertEquals(CloudSyncStatus.Failed("ETAG_MISMATCH"), manager.status.value)
    }

    @Test
    fun `a response arriving after an account switch is never applied to Room`() = runTest {
        // FAVORITES is the first entry of SyncNamespace, so its accountIdAtStart
        // capture is the very first sessions.get() call of the whole
        // synchronizeProfile sweep; every call from here on returns the
        // switched-to account, simulating a switch that happened mid-flight.
        whenever(sessions.get())
            .thenReturn(CstvSession("token", "account-1", "mail@example.test", Long.MAX_VALUE))
            .thenReturn(CstvSession("token", "account-2", "other@example.test", Long.MAX_VALUE))
        commonStubs()
        whenever(states.get(profileId, namespace.wireName))
            .thenReturn(ProfileSyncStateEntity(profileId, namespace.wireName, etag = null, pending = false))
        val remote = snapshotOf(entry("movie:1", "server-only"))
        stubListing(ObjectMetadataDto(namespace.wireName, "remote-etag", 1, 10, null))
        whenever(objects.getObject(remoteId, namespace.wireName)).thenReturn(Response.success(bodyOf(remote)))
        serializer.snapshots[namespace] = snapshotOf()

        manager.synchronizeProfile(profileId)

        assertEquals(null, serializer.applied[namespace])
        verify(objects, never()).putObject(any(), any(), any(), any(), any())
    }

    @Test
    fun `offline device never calls the network`() = runTest {
        whenever(network.isCurrentlyOnline()).thenReturn(false)

        manager.synchronizeProfile(profileId)

        verify(objects, never()).listObjects(any())
    }

    @Test
    fun `oversized snapshot is refused locally without an upload attempt`() = runTest {
        commonStubs()
        whenever(states.get(profileId, namespace.wireName))
            .thenReturn(ProfileSyncStateEntity(profileId, namespace.wireName, etag = "current", pending = true))
        stubListing(ObjectMetadataDto(namespace.wireName, "current", 1, 10, null))
        // A repeated character defeats the point (gzip flattens it to a few bytes):
        // high-entropy content is needed to actually exceed the cap post-compression.
        val incompressible = java.util.Base64.getEncoder()
            .encodeToString(kotlin.random.Random(42).nextBytes(2 * SnapshotCodec.MAX_OBJECT_SIZE_BYTES))
        serializer.snapshots[namespace] = snapshotOf(entry("huge", incompressible))

        manager.synchronizeProfile(profileId)

        verify(objects, never()).putObject(any(), any(), any(), any(), any())
        assertEquals(CloudSyncStatus.Failed("PAYLOAD_TOO_LARGE"), manager.status.value)
    }
}

private class FakeSnapshotSerializer : SnapshotSerializer {
    val snapshots = mutableMapOf<SyncNamespace, NamespaceSnapshot>()
    val applied = mutableMapOf<SyncNamespace, NamespaceSnapshot>()
    override suspend fun snapshot(profileId: Int, namespace: SyncNamespace): NamespaceSnapshot =
        snapshots[namespace] ?: NamespaceSnapshot(SnapshotCodec.SCHEMA_VERSION, namespace.wireName, emptyMap())
    override suspend fun apply(profileId: Int, snapshot: NamespaceSnapshot) {
        applied[SyncNamespace.fromWireName(snapshot.namespace)!!] = snapshot
    }
}
