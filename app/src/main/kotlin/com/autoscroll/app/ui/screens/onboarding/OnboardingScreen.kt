package com.autoscroll.app.ui.screens.onboarding

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.autoscroll.app.R
import com.autoscroll.app.settingsRepository
import com.autoscroll.app.ui.util.OnResume
import com.autoscroll.app.ui.util.isAutoScrollAccessibilityServiceEnabled
import kotlinx.coroutines.launch

/**
 * Three-step first-run wizard. See class-level history note in
 * [com.autoscroll.app.ui.navigation.AppNavigation] for the routing.
 */
@Composable
fun OnboardingScreen(onCompleted: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var stepIndex by remember { mutableIntStateOf(0) }
    var a11yGranted by remember { mutableStateOf(isAutoScrollAccessibilityServiceEnabled(context)) }
    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    OnResume {
        a11yGranted = isAutoScrollAccessibilityServiceEnabled(context)
        overlayGranted = Settings.canDrawOverlays(context)
    }

    val totalSteps = 3
    val isLastStep = stepIndex == totalSteps - 1

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StepDots(current = stepIndex, total = totalSteps)
            Spacer(Modifier.height(32.dp))

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (stepIndex) {
                    0 -> WelcomeStep()
                    1 -> AccessibilityStep(
                        granted = a11yGranted,
                        onOpenSystemSettings = {
                            context.startActivity(
                                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        },
                    )
                    2 -> OverlayStep(
                        granted = overlayGranted,
                        onOpenSystemSettings = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}"),
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (stepIndex > 0) {
                    OutlinedButton(onClick = { stepIndex-- }) {
                        Text(stringResource(R.string.action_back))
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                Button(onClick = {
                    if (isLastStep) {
                        scope.launch {
                            context.settingsRepository.setOnboardingCompleted(true)
                            onCompleted()
                        }
                    } else {
                        stepIndex++
                    }
                }) {
                    Text(stringResource(
                        if (isLastStep) R.string.action_get_started else R.string.action_next
                    ))
                }
            }
        }
    }
}

// ---- Step UI primitives ----------------------------------------------------

@Composable
private fun StepDots(current: Int, total: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(total) { index ->
            val color = if (index == current) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
            Surface(
                shape = CircleShape,
                color = color,
                modifier = Modifier.size(10.dp),
            ) {}
        }
    }
}

@Composable
private fun StepLayout(
    icon: ImageVector,
    title: String,
    body: String,
    extra: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            modifier = Modifier.size(96.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp),
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (extra != null) {
            Spacer(Modifier.height(24.dp))
            extra()
        }
    }
}

// ---- Individual steps ------------------------------------------------------

@Composable
private fun WelcomeStep() {
    StepLayout(
        icon = Icons.Filled.PlayArrow,
        title = stringResource(R.string.onboarding_welcome_title),
        body = stringResource(R.string.onboarding_welcome_body),
    )
}

@Composable
private fun AccessibilityStep(granted: Boolean, onOpenSystemSettings: () -> Unit) {
    StepLayout(
        icon = Icons.Filled.Accessibility,
        title = stringResource(R.string.onboarding_a11y_title),
        body = stringResource(R.string.onboarding_a11y_body),
        extra = {
            if (granted) {
                GrantedChip(label = stringResource(R.string.onboarding_a11y_granted))
            } else {
                Button(onClick = onOpenSystemSettings) {
                    Text(stringResource(R.string.onboarding_a11y_grant))
                }
            }
        },
    )
}

@Composable
private fun OverlayStep(granted: Boolean, onOpenSystemSettings: () -> Unit) {
    StepLayout(
        icon = Icons.Filled.Layers,
        title = stringResource(R.string.onboarding_overlay_title),
        body = stringResource(R.string.onboarding_overlay_body),
        extra = {
            if (granted) {
                GrantedChip(label = stringResource(R.string.onboarding_overlay_granted))
            } else {
                Button(onClick = onOpenSystemSettings) {
                    Text(stringResource(R.string.onboarding_overlay_grant))
                }
            }
        },
    )
}

@Composable
private fun GrantedChip(label: String) {
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(label) },
        leadingIcon = {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
        },
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
            disabledLeadingIconContentColor = MaterialTheme.colorScheme.primary,
            disabledLabelColor = MaterialTheme.colorScheme.primary,
        ),
    )
}
