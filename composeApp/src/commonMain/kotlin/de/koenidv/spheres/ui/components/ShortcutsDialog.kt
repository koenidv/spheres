package de.koenidv.spheres.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import de.koenidv.spheres.platform.isWebPlatform
import de.koenidv.spheres.ui.theme.Ink
import de.koenidv.spheres.ui.theme.Line
import de.koenidv.spheres.ui.theme.Muted
import de.koenidv.spheres.ui.theme.PixelFontFamily
import de.koenidv.spheres.ui.theme.SansFontFamily

private data class ShortcutRow(val keys: List<String>, val label: String)
private data class ShortcutGroup(val title: String, val rows: List<ShortcutRow>)

private fun shortcutGroups(web: Boolean): List<ShortcutGroup> {
    val mod = "⌘"
    return if (web) listOf(
        ShortcutGroup("Create", listOf(
            ShortcutRow(listOf("N"), "New connection"),
            ShortcutRow(listOf("L"), "Log an interaction"),
        )),
        ShortcutGroup("Go to", listOf(
            ShortcutRow(listOf("G", "R"), "Radar"),
            ShortcutRow(listOf("G", "D"), "Due"),
            ShortcutRow(listOf("G", "S"), "Spheres"),
            ShortcutRow(listOf("G", "M"), "Map"),
            ShortcutRow(listOf("/"), "Search people"),
            ShortcutRow(listOf(","), "Settings"),
        )),
        ShortcutGroup("Navigate", listOf(
            ShortcutRow(listOf("↑", "↓"), "Move through the list"),
            ShortcutRow(listOf("Esc"), "Close, or go back"),
            ShortcutRow(listOf("?"), "Show this overview"),
        )),
    ) else listOf(
        ShortcutGroup("Create", listOf(
            ShortcutRow(listOf(mod, "N"), "New connection"),
            ShortcutRow(listOf(mod, "L"), "Log an interaction"),
        )),
        ShortcutGroup("Go to", listOf(
            ShortcutRow(listOf(mod, "1"), "Radar"),
            ShortcutRow(listOf(mod, "2"), "Due"),
            ShortcutRow(listOf(mod, "3"), "Spheres"),
            ShortcutRow(listOf(mod, "4"), "Map"),
            ShortcutRow(listOf(mod, "F"), "Search people"),
            ShortcutRow(listOf(mod, ","), "Settings"),
        )),
        ShortcutGroup("Navigate", listOf(
            ShortcutRow(listOf("↑", "↓"), "Move through the list"),
            ShortcutRow(listOf("Esc"), "Close, or go back"),
        )),
    )
}

@Composable
private fun KeyCap(text: String) {
    Box(
        modifier = Modifier
            .heightIn(min = 28.dp)
            .widthIn(min = 28.dp)
            .clip(PxClipShape)
            .background(Line)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = TextStyle(fontFamily = PixelFontFamily(), fontSize = 15.sp, color = Ink)
        )
    }
}

@Composable
fun ShortcutsDialog(onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .clip(SteppedPolygonShape(steps = 3))
                .background(Color.White)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Keyboard shortcuts",
                style = TextStyle(fontFamily = PixelFontFamily(), fontSize = 22.sp, lineHeight = 28.sp, color = Ink)
            )
            Text(
                text = if (isWebPlatform) {
                    "Press a key anywhere outside a text field. For \"go to\" shortcuts, press G first, then the letter."
                } else {
                    "Hold ⌘ (or Ctrl) and press the key. Arrow keys and Esc work on their own."
                },
                style = TextStyle(fontFamily = SansFontFamily(), fontSize = 14.sp, lineHeight = 20.sp, color = Muted)
            )
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                shortcutGroups(isWebPlatform).forEach { group ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = group.title.uppercase(),
                            style = TextStyle(fontFamily = PixelFontFamily(), fontSize = 13.sp, letterSpacing = 1.sp, color = Muted)
                        )
                        group.rows.forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = row.label,
                                    modifier = Modifier.weight(1f),
                                    style = TextStyle(fontFamily = SansFontFamily(), fontSize = 15.sp, color = Ink)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    row.keys.forEach { KeyCap(it) }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            PixelButton(text = "Got it", variant = ButtonVariant.SECONDARY, fullWidth = true, onClick = onClose)
        }
    }
}
