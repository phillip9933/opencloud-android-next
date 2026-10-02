package eu.opencloud.android.next.core.sync

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Application lifetime; recovery runs off the main thread after WorkManager initialization. */
object TransferStartup {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Suppress("TooGenericExceptionCaught")
    fun initialize(context: Context) {
        val application = context.applicationContext
        scope.launch {
            try {
                scheduleSharedDownloadCleanup(application)
                TransferManager(application).apply {
                    FileOperationManager(application).reconcile()
                    schedulePendingPins(application)
                    reconcile()
                    reconcileOfflineTraversals(application)
                    scheduleCleanup()
                }
                DocumentEditReconciler(application).reconcile()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Durable intents remain in Room and will be retried on the next process start.
                Log.e("OpenCloudSync", "STARTUP_RECONCILIATION_FAILED")
            }
        }
    }
}
