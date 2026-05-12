package com.autoscroll.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autoscroll.app.R
import com.autoscroll.app.data.model.AppSettings
import com.autoscroll.app.data.model.Platform
import com.autoscroll.app.data.model.ScrollMode
import com.autoscroll.app.settingsRepository
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = context.settingsRepository

    val settings: AppSettings by repo.settingsFlow
        .collectAsStateWithLifecycle(initialValue = AppSettings())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back))
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard(title = stringResource(R.string.settings_section_mode)) {
                ScrollMode.entries.forEach { mode ->
                    ModeRow(
                        selected = settings.scrollMode == mode,
                        label = stringResource(labelOf(mode)),
                        description = stringResource(descriptionOf(mode)),
                        onClick = { scope.launch { repo.setScrollMode(mode) } },
                    )
                }
            }

            SectionCard(title = stringResource(R.string.settings_section_timer)) {
                TimerRow(
                    currentSeconds = settings.timerSeconds,
                    onChange = { secs -> scope.launch { repo.setTimerSeconds(secs) } },
                )
            }

            SectionCard(title = stringResource(R.string.settings_section_ads)) {
                ToggleRow(
                    title = stringResource(R.string.settings_skip_ads_title),
                    subtitle = stringResource(R.string.settings_skip_ads_desc),
                    checked = settings.skipAdsEnabled,
                    onChange = { v -> scope.launch { repo.setSkipAdsEnabled(v) } },
                )
            }

            SectionCard(title = stringResource(R.string.settings_section_platforms)) {
                Platform.entries.forEach { platform ->
                    ToggleRow(
                        title = platform.displayName,
                        subtitle = null,
                        checked = platform in settings.enabledPlatforms,
                        onChange = { v ->
                            scope.launch { repo.togglePlatform(platform, v) }
                        },
                    )
                }
            }

            SectionCard(title = stringResource(R.string.settings_section_overlay)) {
                ToggleRow(
                    title = stringResource(R.string.settings_overlay_title),
                    subtitle = stringResource(R.string.settings_overlay_desc),
                    checked = settings.showFloatingOverlay,
                    onChange = { v -> scope.launch { repo.setShowFloatingOverlay(v) } },
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
            Column(modifier = Modifier.padding(4.dp)) { content() }
        }
    }
}

@Composable
private fun ModeRow(
    selected: Boolean,
    label: String,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun TimerRow(currentSeconds: Int, onChange: (Int) -> Unit) {
    var localValue by remember(currentSeconds) {
        mutableFloatStateOf(currentSeconds.toFloat())
    }
    val rangeStart = AppSettings.TIMER_RANGE.first.toFloat()
    val rangeEnd = AppSettings.TIMER_RANGE.last.toFloat()

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.settings_timer_label), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.settings_timer_value, localValue.roundToInt()),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = localValue,
            onValueChange = { localValue = it },
            onValueChangeFinished = { onChange(localValue.roundToInt()) },
            valueRange = rangeStart..rangeEnd,
        )
        Text(
            stringResource(
                R.string.settings_timer_range,
                AppSettings.TIMER_RANGE.first,
                AppSettings.TIMER_RANGE.last,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun labelOf(mode: ScrollMode): Int = when (mode) {
    ScrollMode.BY_TIMER     -> R.string.settings_mode_timer
    ScrollMode.BY_VIDEO_END -> R.string.settings_mode_video_end
}

private fun descriptionOf(mode: ScrollMode): Int = when (mode) {
    ScrollMode.BY_TIMER     -> R.string.settings_mode_timer_desc
    ScrollMode.BY_VIDEO_END -> R.string.settings_mode_video_end_desc
}
