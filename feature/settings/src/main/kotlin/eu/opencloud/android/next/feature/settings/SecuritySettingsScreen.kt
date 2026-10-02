package eu.opencloud.android.next.feature.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import eu.opencloud.android.next.core.security.AppLock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecuritySettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lock = remember { AppLock(context) }
    var revision by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { revision++ }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { revision++ }
    val enabled = remember(revision) { lock.enabled }
    val lockDelayOptions = LockDelayOptions()
    val currentLockDelay =
        lockDelayOptions.firstOrNull { it.first == lock.timeoutMinutes }?.second
            ?: pluralStringResource(R.plurals.settings_lock_delay_minutes, lock.timeoutMinutes, lock.timeoutMinutes)
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_security)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            MetadataPermissionSettings()
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_lock_opencloud)) },
                supportingContent = {
                    Text(
                        if (lock.deviceSecure) {
                            stringResource(R.string.settings_lock_description_secure)
                        } else {
                            stringResource(R.string.settings_lock_description_insecure)
                        },
                    )
                },
                trailingContent = {
                    Switch(
                        enabled,
                        onCheckedChange = { value ->
                            launcher.launch(
                                Intent()
                                    .setClassName(context.packageName, AppLock.AUTH_ACTIVITY)
                                    .setAction(if (value) "enable-lock" else "disable-lock"),
                            )
                        },
                        enabled = lock.deviceSecure,
                    )
                },
            )
            if (enabled) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_biometric_unlock)) },
                    supportingContent = {
                        Text(
                            if (lock.biometricAvailable) {
                                stringResource(R.string.settings_biometric_description_available)
                            } else {
                                stringResource(R.string.settings_biometric_description_unavailable)
                            },
                        )
                    },
                    trailingContent = {
                        Switch(
                            lock.biometricEnabled,
                            {
                                lock.setBiometricEnabled(it)
                                revision++
                            },
                            enabled = lock.biometricAvailable || lock.biometricEnabled,
                        )
                    },
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_lock_after_leaving)) },
                    supportingContent = {
                        Text(stringResource(R.string.settings_lock_after_leaving_description))
                    },
                    trailingContent = {
                        SettingsChoice(
                            stringResource(R.string.settings_change_lock_delay),
                            currentLockDelay,
                            lockDelayOptions.map { it.second },
                        ) { label ->
                            lock.setTimeout(lockDelayOptions.first { it.second == label }.first)
                            revision++
                        }
                    },
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_protect_other_apps)) },
                    supportingContent = {
                        Text(stringResource(R.string.settings_protect_other_apps_description))
                    },
                    trailingContent = {
                        Switch(
                            lock.protectDocuments,
                            {
                                lock.setProtectDocuments(it)
                                revision++
                            },
                        )
                    },
                )
                TextButton(
                    onClick = {
                        launcher.launch(Intent().setClassName(context.packageName, AppLock.AUTH_ACTIVITY))
                    },
                ) { Text(stringResource(R.string.settings_unlock_file_picker)) }
            }
        }
    }
}

@Composable
private fun LockDelayOptions() =
    listOf(
        0 to stringResource(R.string.settings_lock_delay_immediately),
        1 to pluralStringResource(R.plurals.settings_lock_delay_minutes, 1, 1),
        5 to pluralStringResource(R.plurals.settings_lock_delay_minutes, 5, 5),
        30 to pluralStringResource(R.plurals.settings_lock_delay_minutes, 30, 30),
    )
