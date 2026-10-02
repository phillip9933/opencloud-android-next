package eu.opencloud.android.next.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import eu.opencloud.android.next.core.datastore.Appearance
import eu.opencloud.android.next.core.datastore.UserSettings
import eu.opencloud.android.next.core.designsystem.theme.OpenCloudDimensions

@Composable
@Suppress("LongParameterList") // Explicit settings values and callbacks keep navigation out of the rendering layer.
internal fun SettingsSections(
    state: UserSettings,
    onRetention: (Int) -> Unit,
    onOpenAppearance: () -> Unit,
    onBackup: () -> Unit,
    diagnostics: SettingsDiagnostics,
    onSecurity: () -> Unit,
    onClearTemporary: () -> Unit,
) {
    val retentionOptions =
        listOf(
            0 to stringResource(R.string.settings_retention_never),
            1 to pluralStringResource(R.plurals.settings_retention_hours, 1, 1),
            12 to pluralStringResource(R.plurals.settings_retention_hours, 12, 12),
            24 to pluralStringResource(R.plurals.settings_retention_days, 1, 1),
            720 to pluralStringResource(R.plurals.settings_retention_days, 30, 30),
        )
    Column(
        Modifier.padding(OpenCloudDimensions.SpacingMd),
        verticalArrangement = Arrangement.spacedBy(OpenCloudDimensions.SpacingMd),
    ) {
        Card(onClick = onOpenAppearance, modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_appearance)) },
                supportingContent = { Text(stringResource(R.string.settings_appearance_summary)) },
                leadingContent = { Icon(Icons.Default.Palette, null) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
            )
        }
        Text(stringResource(R.string.settings_local_storage), style = MaterialTheme.typography.titleSmall)
        Card {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_delete_temporary_copies)) },
                leadingContent = { Icon(Icons.Default.History, null) },
                supportingContent = { Text(stringResource(R.string.settings_temporary_copy_retention_description)) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                trailingContent = {
                    SettingsChoice(
                        stringResource(R.string.settings_change_copy_retention),
                        retentionOptions.first { it.first == state.temporaryCopyRetentionHours }.second,
                        retentionOptions.map { it.second },
                    ) {
                        onRetention(retentionOptions.first { entry -> entry.second == it }.first)
                    }
                },
            )
            Box(
                Modifier.padding(horizontal = OpenCloudDimensions.SpacingMd, vertical = OpenCloudDimensions.SpacingXs),
            ) {
                TemporaryCleanupButton(onClearTemporary)
            }
        }
        Text(stringResource(R.string.settings_backup_security), style = MaterialTheme.typography.titleSmall)
        Card(onClick = onBackup, modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_folder_camera_backup)) },
                supportingContent = { Text(stringResource(R.string.settings_manage_automatic_uploads)) },
                leadingContent = { Icon(Icons.Default.Sync, null) },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.settings_open_backup_configuration),
                    )
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
            )
        }
        Card(onClick = onSecurity, modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_security)) },
                leadingContent = { Icon(Icons.Default.Lock, null) },
                supportingContent = { Text(stringResource(R.string.settings_app_lock_other_apps)) },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.settings_open_security_settings),
                    )
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
            )
        }
        Text(stringResource(R.string.settings_troubleshooting), style = MaterialTheme.typography.titleSmall)
        DiagnosticsCard(state, diagnostics)
    }
}

@Composable
internal fun SettingsChoice(
    description: String,
    current: String,
    options: List<String>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilledTonalButton(
            onClick = { expanded = true },
            shape = RoundedCornerShape(OpenCloudDimensions.SpacingMd),
            modifier = Modifier.semantics { contentDescription = description },
        ) {
            Text(current)
            Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                    leadingIcon = {
                        if (option == current) {
                            Icon(Icons.Default.Check, contentDescription = stringResource(R.string.settings_selected))
                        }
                    },
                    modifier = Modifier.semantics { selected = option == current },
                )
            }
        }
    }
}

@Composable
private fun DiagnosticsCard(
    state: UserSettings,
    diagnostics: SettingsDiagnostics,
) {
    val diagnosticsLabel = stringResource(R.string.settings_local_diagnostics)
    Card {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_local_diagnostics)) },
            leadingContent = { Icon(Icons.Default.BugReport, null) },
            supportingContent = {
                Text(stringResource(R.string.settings_diagnostics_description, 100))
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
            trailingContent = {
                Switch(
                    state.localDiagnosticsEnabled,
                    diagnostics.onSetEnabled,
                    Modifier.semantics { contentDescription = diagnosticsLabel },
                )
            },
        )
        if (state.localDiagnosticsEnabled) {
            TextButton(onClick = diagnostics.onRead) { Text(stringResource(R.string.settings_view_local_diagnostics)) }
        }
    }
}

internal fun Appearance.resourceId() =
    when (this) {
        Appearance.LIGHT -> R.string.settings_appearance_light
        Appearance.DARK -> R.string.settings_appearance_dark
        Appearance.SYSTEM -> R.string.settings_appearance_system
    }
