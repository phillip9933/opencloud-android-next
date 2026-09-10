package eu.opencloud.android.next.core.sync

import androidx.room.Room
import eu.opencloud.android.next.core.database.FileBrowserDatabase
import eu.opencloud.android.next.core.database.FileBrowserStore
import eu.opencloud.android.next.core.database.SpaceEntity
import eu.opencloud.android.next.core.network.LibreGraphSpacesClient
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SpaceRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var database: FileBrowserDatabase
    private lateinit var store: FileBrowserStore
    private lateinit var repository: SpaceRepository

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        database =
            Room
                .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), FileBrowserDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        store = FileBrowserStore(database)
        repository = SpaceRepository(store, LibreGraphSpacesClient(OkHttpClient(), initiatorId = "test"))
    }

    @After
    fun tearDown() {
        database.close()
        server.shutdown()
    }

    @Test
    fun `synchronize replaces stale spaces and persists graph fields`() =
        runTest {
            store.replaceRemoteSpaces("account", listOf(staleSpace()))
            server.enqueue(
                MockResponse().setBody(
                    """{"value":[{"id":"project","driveAlias":"project-mars","name":"Mars","driveType":"project","owner":{"user":{"id":"alice","displayName":"Alice"}},"quota":{"total":100,"used":25,"remaining":75,"state":"normal"},"root":{"id":"root","webDavUrl":"${server.url(
                        "dav/spaces/project",
                    )}"}}]}""",
                ),
            )

            val synchronized = repository.synchronize("account", server.url("/").toString(), "Bearer token").single()

            assertEquals("project-mars", synchronized.driveAlias)
            assertEquals("Alice", synchronized.ownerName)
            assertEquals(25L, synchronized.quotaUsedBytes)
            assertEquals(synchronized, store.space("account", "project"))
            assertNull(store.space("account", "stale"))
        }

    private fun staleSpace() =
        SpaceEntity(
            accountId = "account",
            driveId = "stale",
            name = "Stale",
            type = "project",
            description = null,
            ownerName = null,
            rootId = "stale-root",
            rootWebDavUrl = "https://cloud.example.test/dav/spaces/stale",
            rootETag = null,
            quotaBytes = null,
        )
}
