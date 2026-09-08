package eu.opencloud.android.next.feature.transfers

import eu.opencloud.android.next.core.database.TransferEntity
import eu.opencloud.android.next.core.database.TransferState
import org.junit.Assert.assertEquals
import org.junit.Test

class TransfersStateTest {
    @Test fun `categorizes durable Room transfer states`() {
        val result =
            categorizeTransfers(
                listOf(
                    transfer("active", TransferState.RUNNING),
                    transfer("failed", TransferState.FAILED),
                    transfer("conflict", TransferState.CONFLICT),
                    transfer("done", TransferState.SUCCEEDED),
                ),
            )
        assertEquals(listOf("active"), result.active.map(TransferEntity::id))
        assertEquals(listOf("failed", "conflict"), result.failed.map(TransferEntity::id))
        assertEquals(listOf("done"), result.history.map(TransferEntity::id))
    }

    @Test fun `retry all includes failed transfers but excludes conflicts`() {
        val result =
            retryableTransfers(
                listOf(
                    transfer("failed", TransferState.FAILED),
                    transfer("conflict", TransferState.CONFLICT),
                    transfer("done", TransferState.SUCCEEDED),
                ),
            )

        assertEquals(listOf("failed"), result.map(TransferEntity::id))
    }

    private fun transfer(
        id: String,
        state: TransferState,
    ) = TransferEntity(
        id,
        "account",
        "space",
        null,
        "UPLOAD",
        null,
        "/$id",
        id,
        null,
        10,
        state = state.name,
        createdAtEpochMillis = 0,
        updatedAtEpochMillis = 0,
    )
}
