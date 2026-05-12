package com.autoscroll.app.ui.screens.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autoscroll.app.R
import com.autoscroll.app.core.AutoScrollEngine
import com.autoscroll.app.data.model.AppSettings
import com.autoscroll.app.data.model.ScrollMode
import com.autoscroll.app.settingsRepository
import com.autoscroll.app.ui.util.OnResume
import com.autoscroll.app.ui.util.isAutoScrollAccessibilityServiceEnabled
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = context.settingsRepository

    val settings: AppSettings by repo.settingsFlow
        .collectAsStateWithLifecycle(initialValue = AppSettings())
    val engineState by AutoScrollEngine.state.collectAsStateWithLifecycle()

    var a11yGranted by remember { mutableStateOf(isAutoScrollAccessibilityServiceEnabled(context)) }
    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var notificationsGranted by remember { mutableStateOf(hasNotificationPermission(context)) }
    OnResume {
        a11yGranted = isAutoScrollAccessibilityServiceEnabled(context)
        overlayGranted = Settings.canDrawOverlays(context)
        notificationsGranted = hasNotificationPermission(context)
    }

    // Runtime permission launcher for POST_NOTIFICATIONS (API 33+).
    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> notificationsGranted = granted }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!a11yGranted) {
                WarningCard(
                    title = stringResource(R.string.home_warning_a11y_title),
                    body = stringResource(R.string.home_warning_a11y_body),
                    actionLabel = stringResource(R.string.action_open_settings),
                    onAction = {
                        context.startActivity(
                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                )
            }
            if (!overlayGranted) {
                WarningCard(
                    title = stringResource(R.string.home_warning_overlay_title),
                    body = stringResource(R.string.home_warning_overlay_body),
                    actionLabel = stringResource(R.string.action_allow_overlay),
                    onAction = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}"),
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                )
            }
            if (!notificationsGranted) {
                WarningCard(
                    title = stringResource(R.string.home_warning_notifications_title),
                    body = stringResource(R.string.home_warning_notifications_body),
                    actionLabel = stringResource(R.string.action_allow_notifications),
                    onAction = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                )
            }

            // Status card
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    Text(stringResource(R.string.home_status_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    val platformLabel = engineState.currentPlatform?.displayName
                        ?: stringResource(R.string.home_status_standby)
                    Text(platformLabel, style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = when {
                            engineState.isActive ->
                                stringResource(R.string.home_status_next_swipe, engineState.secondsRemaining)
                            settings.isEnabled ->
                                stringResource(R.string.home_status_open_apps)
                            else ->
                                stringResource(R.string.home_status_off)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Master switch
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (settings.isEnabled) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(stringResource(R.string.home_master_title), style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = stringResource(
                                if (settings.isEnabled) R.string.home_master_running
                                else R.string.home_master_off_hint,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = settings.isEnabled,
                        onCheckedChange = { v -> scope.launch { repo.setEnabled(v) } },
                    )
                }
            }

            // Quick summary
            QuickSummaryCard(settings = settings, onOpenSettings = onOpenSettings)

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun WarningCard(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(6.dp))
            Text(body, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun QuickSummaryCard(settings: AppSettings, onOpenSettings: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.home_summary_title), style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = onOpenSettings) { Text(stringResource(R.string.action_edit)) }
            }
            Spacer(Modifier.height(12.dp))
            SummaryRow(
                label = stringResource(R.string.home_summary_mode),
                value = when (settings.scrollMode) {
                    ScrollMode.BY_TIMER     -> stringResource(R.string.home_summary_mode_timer, settings.timerSeconds)
                    ScrollMode.BY_VIDEO_END -> stringResource(R.string.home_summary_mode_video_end)
                },
            )
            SummaryRow(
                label = stringResource(R.string.home_summary_skip_ads),
                value = stringResource(if (settings.skipAdsEnabled) R.string.label_on else R.string.label_off),
            )
            SummaryRow(
                label = stringResource(R.string.home_summary_active_on),
                value = if (settings.enabledPlatforms.isEmpty()) {
                    stringResource(R.string.home_summary_active_nowhere)
                } else {
                    settings.enabledPlatforms.joinToString { it.displayName }
                },
            )
            SummaryRow(
                label = stringResource(R.string.home_summary_panel),
                value = stringResource(
                    if (settings.showFloatingOverlay) R.string.home_summary_panel_visible
                    else R.string.home_summary_panel_hidden,
                ),
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
             color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * On pre-Tiramisu Android the permission is implicitly granted via the
 * manifest entry, so we treat that as "granted". On API 33+ we must check
 * at runtime; without it the FGS notification simply won't appear in the
 * shade (service still runs).
 */
private fun hasNotificationPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
}
