package eu.opencloud.android.next.feature.search

import eu.opencloud.android.next.core.database.AccountEntity
import eu.opencloud.android.next.core.database.ResourceEntity
import eu.opencloud.android.next.core.model.ResourceKind
import eu.opencloud.android.next.core.network.RemoteSearchResource
import eu.opencloud.android.next.core.sync.RoomSearchRepository
import eu.opencloud.android.next.core.sync.SearchRepositoryResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoomSearchRepositoryTest {
    @Test fun `local results emit immediately before debounced remote results`() =
        runTest {
            var remoteCalls = 0
            val repository =
                repository(
                    account = account(remoteSearchUrl = "https://cloud.test/remote.php/dav/spaces/"),
                    remoteSearch = { _, _ ->
                        remoteCalls++
                        listOf(remote("remote"))
                    },
                )
            val emissions = mutableListOf<SearchRepositoryResult>()
            val job = launch { repository.search("account", "plan").take(3).toList(emissions) }

            runCurrent()
            assertEquals(2, emissions.size)
            assertFalse(emissions.first().remoteSupported)
            assertTrue(emissions.last().remoteLoading)
            assertEquals(listOf("local"), emissions.first().resources.map(ResourceEntity::remoteId))
            assertEquals(0, remoteCalls)

            advanceTimeBy(350)
            runCurrent()
            job.join()
            assertEquals(1, remoteCalls)
            assertEquals(listOf("local", "remote"), emissions.last().resources.map(ResourceEntity::remoteId))
        }

    @Test fun `unsupported capability never invokes remote search`() =
        runTest {
            var remoteCalls = 0
            val result =
                repository(
                    account = account(remoteSearchUrl = null),
                    remoteSearch = { _, _ ->
                        remoteCalls++
                        emptyList()
                    },
                ).search("account", "plan").toList().last()

            assertFalse(result.remoteSupported)
            assertEquals(0, remoteCalls)
            assertEquals(listOf("local"), result.resources.map(ResourceEntity::remoteId))
        }

    @Test fun `cancelling obsolete query prevents remote invocation`() =
        runTest {
            var remoteCalls = 0
            val repository =
                repository(
                    account = account(remoteSearchUrl = "https://cloud.test/remote.php/dav/spaces/"),
                    remoteSearch = { _, _ ->
                        remoteCalls++
                        emptyList()
                    },
                )
            val job = launch { repository.search("account", "old query").toList() }
            runCurrent()
            job.cancel()
            advanceTimeBy(350)
            runCurrent()

            assertEquals(0, remoteCalls)
        }

    private fun repository(
        account: AccountEntity,
        remoteSearch: suspend (AccountEntity, String) -> List<RemoteSearchResource>,
    ) = RoomSearchRepository(
        localSearch = { _, _ -> flowOf(listOf(localResource())) },
        accountProvider = { account },
        remoteSearch = remoteSearch,
    )

    private fun account(remoteSearchUrl: String?) =
        AccountEntity(
            "account",
            "https://cloud.test",
            "alice",
            "Alice",
            "BASIC",
            false,
            remoteSearchUrl = remoteSearchUrl,
        )

    private fun localResource() =
        ResourceEntity(
            "account",
            "space",
            "local",
            null,
            "/Plan.pdf",
            "Plan.pdf",
            ResourceKind.FILE,
            null,
            1,
            null,
            0,
            0,
        )

    private fun remote(id: String) =
        RemoteSearchResource(id, "space", "/Remote.pdf", "Remote.pdf", false, null, 1, null, 0)
}
