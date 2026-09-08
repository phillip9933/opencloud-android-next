package eu.opencloud.android.next.core.sync

import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupExecutionPolicyTest {
    @Test
    fun `debug backup bypasses persisted charging and unmetered requirements`() {
        assertTrue(
            BackupExecutionPolicy.canRun(
                debug = true,
                wifiOnly = true,
                chargingOnly = true,
                unmetered = false,
                charging = false,
            ),
        )
    }

    @Test
    fun `release backup honors persisted charging and unmetered requirements`() {
        assertFalse(
            BackupExecutionPolicy.canRun(
                debug = false,
                wifiOnly = true,
                chargingOnly = true,
                unmetered = false,
                charging = false,
            ),
        )
    }

    @Test
    fun `debug work request has no strict backup constraints`() {
        val constraints = backupConstraints(debug = true)
        assertEquals(NetworkType.CONNECTED, constraints.requiredNetworkType)
        assertFalse(constraints.requiresCharging())
    }

    @Test
    fun `release work request retains strict backup constraints`() {
        val constraints = backupConstraints(debug = false)
        assertEquals(NetworkType.UNMETERED, constraints.requiredNetworkType)
        assertTrue(constraints.requiresCharging())
    }
}
