package de.koenidv.spheres.ui.components

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import de.koenidv.spheres.ui.theme.Ink

val LocalOpenPersonMenu = compositionLocalOf<(PersonMenuTarget) -> Unit> { {} }

class PersonOrder {
    var ids: List<String> = emptyList()
    internal var owner: Any? = null
}

val LocalPersonOrder = compositionLocalOf<PersonOrder?> { null }

// Lets the shell step through the visible people with the arrow keys
@Composable
fun PublishPersonOrder(ids: List<String>) {
    val order = LocalPersonOrder.current ?: return
    val token = remember { Any() }
    SideEffect {
        order.ids = ids
        order.owner = token
    }
    DisposableEffect(order) {
        onDispose {
            if (order.owner === token) {
                order.ids = emptyList()
                order.owner = null
            }
        }
    }
}

@Composable
fun Modifier.hoverHighlight(): Modifier {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    return this
        .hoverable(source)
        .pointerHoverIcon(PointerIcon.Hand)
        .drawBehind { if (hovered) drawRect(Ink.copy(alpha = 0.05f)) }
}

@Composable
fun Modifier.onSecondaryClick(onClick: (windowPosition: Offset) -> Unit): Modifier {
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val current by rememberUpdatedState(onClick)
    return this
        .onGloballyPositioned { coords[0] = it }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                        val change = event.changes.first()
                        change.consume()
                        current(coords[0]?.localToWindow(change.position) ?: change.position)
                    }
                }
            }
        }
}

@Composable
fun Modifier.personContextMenu(personId: String): Modifier {
    val open = LocalOpenPersonMenu.current
    val size = with(LocalDensity.current) { RADAR_AVATAR_SIZE.toPx() }
    return onSecondaryClick { p ->
        open(PersonMenuTarget(personId, p.x - size / 2f, p.y - size / 2f, size, size))
    }
}
