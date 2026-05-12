package com.autoscroll.app.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autoscroll.app.R
import com.autoscroll.app.core.AutoScrollEngine
import com.autoscroll.app.core.EngineState
import kotlin.math.roundToInt

/**
 * The pill-shaped floating control rendered inside the overlay window.
 *
 * Three interactions:
 *  - **Tap** the body         → toggle pause/play (master switch).
 *  - **Drag** the body        → move overlay around screen.
 *  - **Tap close icon**       → dismiss overlay (stops foreground service).
 *
 * Visual state is driven by [AutoScrollEngine.state] — when scrolling is
 * active we show countdown text; when standby (e.g. user on Chrome) we show
 * a dash and a muted colour.
 *
 * @param onDragDelta  Called with cumulative pointer delta in pixels each
 *                     drag frame. The controller translates this into
 *                     [android.view.WindowManager.LayoutParams] x/y updates.
 *
 * @param onTogglePause Tap on body — toggle the master switch.
 *
 * @param onClose       Tap on the X icon — stop the foreground service.
 */
@Composable
internal fun FloatingPanel(
    onDragDelta: (dx: Int, dy: Int) -> Unit,
    onTogglePause: () -> Unit,
    onClose: () -> Unit,
) {
    val state: EngineState by AutoScrollEngine.state.collectAsStateWithLifecycle()
    val isActive = state.isActive

    val containerColor = if (isActive) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (isActive) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = containerColor,
        contentColor = contentColor,
        shadowElevation = 8.dp,
        modifier = Modifier
            .pointerInput(Unit) {
                // Custom drag handler: we want raw pixel deltas to feed
                // straight into WindowManager.LayoutParams x/y.
                handleTapOrDrag(
                    onDragDelta = onDragDelta,
                    onTap = onTogglePause,
                )
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Icon(
                imageVector = if (isActive) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )

            Text(
                text = if (isActive) "${state.secondsRemaining}s" else "—",
                style = MaterialTheme.typography.titleSmall,
            )

            Spacer(Modifier.width(4.dp))

            Text(
                text = state.currentPlatform?.displayName
                    ?: stringResource(R.string.overlay_standby),
                style = MaterialTheme.typography.labelMedium,
            )

            Spacer(Modifier.width(4.dp))

            // Close button — own clickable region so taps here don't toggle pause.
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.15f))
                    .pointerInput(Unit) {
                        handleTapOrDrag(
                            onDragDelta = { _, _ -> /* ignore */ },
                            onTap = onClose,
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.overlay_close_cd),
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

/**
 * Tiny gesture utility: distinguish "tap" (down + up, no movement) from
 * "drag" (down + move). We don't use Compose's `detectTapGestures` /
 * `detectDragGestures` because we want raw pixel deltas (Int) to feed
 * directly into [android.view.WindowManager.LayoutParams], and those APIs
 * deal in [androidx.compose.ui.geometry.Offset].
 */
private suspend fun PointerInputScope.handleTapOrDrag(
    onDragDelta: (dx: Int, dy: Int) -> Unit,
    onTap: () -> Unit,
) {
    awaitEachGesture {
        awaitFirstDown()
        var dragged = false

        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.first()
            if (!change.pressed) break    // pointer up — gesture ended

            val pos = change.positionChange()
            if (pos.x != 0f || pos.y != 0f) {
                dragged = true
                onDragDelta(pos.x.roundToInt(), pos.y.roundToInt())
                change.consume()
            }
        }

        // No measurable movement → it was a tap.
        if (!dragged) onTap()
    }
}
