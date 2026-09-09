package eu.opencloud.android.next.feature.settings

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.opencloud.android.next.core.datastore.SettingsRepository
import eu.opencloud.android.next.core.datastore.UserSettings
import eu.opencloud.android.next.core.designsystem.theme.OpenCloudDimensions
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository = SettingsRepository.create(application)
    val state = repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    fun setRetention(days: Int) {
        viewModelScope.launch { repository.setCacheRetentionDays(days) }
    }
}

@Composable
fun SettingsRoute(
    onNavigateBack: () -> Unit,
    onOpenBackupSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    SettingsScreen(
        state,
        onNavigateBack,
        onOpenBackupSettings,
        viewModel::setRetention,
        modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: UserSettings,
    onNavigateBack: () -> Unit,
    onOpenBackupSettings: () -> Unit,
    onSetRetention: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                    ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ListItem(
                headlineContent = { Text("Folder & camera backup") },
                supportingContent = { Text("Configure automatic local folder uploads") },
                leadingContent = { Icon(Icons.Default.PhotoCamera, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onOpenBackupSettings),
            )
            ListItem(
                headlineContent = { Text("Cache retention") },
                supportingContent = { Text("${state.cacheRetentionDays} days") },
                leadingContent = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
            )
            androidx.compose.foundation.layout.Row(Modifier.padding(horizontal = OpenCloudDimensions.SpacingMd)) {
                listOf(7, 30, 90).forEach { days ->
                    androidx.compose.material3.TextButton(onClick = { onSetRetention(days) }) { Text("$days days") }
                }
            }
        }
    }
}
