package app.homely.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Observe a whole surface, but claim movement only when it belongs to theme navigation. Ordinary
 * vertical drawer scrolling is left to the lazy container.
 */
internal fun Modifier.themeSwipeInput(
    key: Any?,
    canHandle: (String) -> Boolean,
    onGesture: (String) -> Unit,
): Modifier =
    pointerInput(key) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var movement = Offset.Zero
            var scale = 1f
            var claimed: String? = null
            var multiple = false
            val threshold = 38.dp.toPx()
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.count { it.pressed }
                if (pressed >= 2) {
                    multiple = true
                    scale *= event.calculateZoom()
                    if (claimed == null && (scale < .82f || scale > 1.18f)) {
                        val gesture = if (scale < 1f) "pinch_in" else "pinch_out"
                        if (canHandle(gesture)) claimed = gesture
                    }
                } else if (!multiple && claimed == null) {
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change != null) movement += change.position - change.previousPosition
                    if (movement.getDistance() >= threshold) {
                        val gesture =
                            if (abs(movement.x) > abs(movement.y)) {
                                if (movement.x < 0) "swipe_left" else "swipe_right"
                            } else if (movement.y < 0) "swipe_up" else "swipe_down"
                        if (canHandle(gesture)) claimed = gesture
                    }
                }
                if (claimed != null) event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
            claimed?.let(onGesture)
        }
    }
