package eu.opencloud.android.next.feature.files

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import eu.opencloud.android.next.core.database.ResourceEntity
import kotlinx.coroutines.launch

@Composable
internal fun rememberFileOpener(viewModel: FileBrowserViewModel): (ResourceEntity, ExternalAction) -> Unit {
    val context = LocalContext.current
    val openingScope = rememberCoroutineScope()
    var opening by remember { mutableStateOf(false) }
    val openFile: (ResourceEntity, ExternalAction) -> Unit = openFile@{ resource, action ->
        if (opening) return@openFile
        if (!resource.hasLocalCopy &&
            !eu.opencloud.android.next.core.sync
                .AndroidNetworkStatus(context)
                .isConnected()
        ) {
            viewModel.reportOpenError("This file is cloud only. Connect to the internet or choose a file from Offline.")
            return@openFile
        }
        opening = true
        openingScope.launch {
            try {
                val prepared = viewModel.prepareExternalFile(resource)
                val intent =
                    if (action ==
                        ExternalAction.SEND
                    ) {
                        externalSendIntent(context, prepared)
                    } else {
                        externalFileIntent(context, prepared)
                    }
                context.startActivity(
                    if (action != ExternalAction.OPEN) {
                        Intent
                            .createChooser(
                                intent,
                                if (action ==
                                    ExternalAction.SEND
                                ) {
                                    "Send file"
                                } else {
                                    "Open with"
                                },
                            ).apply {
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                clipData = intent.clipData
                            }
                    } else {
                        intent
                    },
                )
                viewModel.recordOpened(prepared)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: ActivityNotFoundException) {
                viewModel.reportOpenError("No installed app can open this file type.")
            } catch (_: SecurityException) {
                viewModel.reportOpenError(
                    "Android blocked opening this file. Check app permissions or use Copy to device.",
                )
            } catch (_: Exception) {
                viewModel.reportOpenError("This file could not be opened. Check the transfer status and try again.")
            } finally {
                opening = false
            }
        }
    }
    return openFile
}
