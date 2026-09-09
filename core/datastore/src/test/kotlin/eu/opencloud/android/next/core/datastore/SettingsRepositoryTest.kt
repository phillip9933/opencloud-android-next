package eu.opencloud.android.next.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import eu.opencloud.android.next.core.datastore.proto.AppSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryTest {
    @Test fun `invalid persisted values map to canonical defaults`() =
        runTest {
            val file = temporaryFile()
            file.outputStream().use {
                AppSettings
                    .newBuilder()
                    .setCacheRetentionDays(-10)
                    .setActiveAccountId("   ")
                    .build()
                    .writeTo(it)
            }
            val repository =
                SettingsRepository.create(
                    DataStoreFactory.create(
                        serializer = AppSettingsSerializer,
                        scope = backgroundScope,
                        produceFile = { file },
                    ),
                )
            val settings = repository.settings.first()
            assertEquals(SettingsBrowserLayout.DEFAULT_TABLE, settings.browserLayout)
            assertEquals(DEFAULT_CACHE_RETENTION_DAYS, settings.cacheRetentionDays)
            assertNull(settings.activeAccountId)
        }

    @Test fun `updates persist validated settings`() =
        runTest {
            val repository = SettingsRepository.create(InMemoryDataStore())
            repository.setBrowserLayout(SettingsBrowserLayout.TILES)
            repository.setActiveAccountId(" account-2 ")
            repository.setCacheRetentionDays(999)
            val settings = repository.settings.first()
            assertEquals(SettingsBrowserLayout.TILES, settings.browserLayout)
            assertEquals("account-2", settings.activeAccountId)
            assertEquals(365, settings.cacheRetentionDays)
        }

    private fun temporaryFile(): File = File.createTempFile("opencloud-settings", ".pb").apply { delete() }

    private class InMemoryDataStore : DataStore<AppSettings> {
        private val state = MutableStateFlow(AppSettings.getDefaultInstance())
        override val data: Flow<AppSettings> = state

        override suspend fun updateData(transform: suspend (t: AppSettings) -> AppSettings): AppSettings =
            transform(state.value).also { state.value = it }
    }
}
