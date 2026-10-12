package com.tan.gratify.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.FilterNone
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import gratify.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import java.awt.MouseInfo
import java.awt.Window

/** Windows caption controls, with dragging restricted to the caption surface. */
@Composable
fun CustomTitleBar(title: String, windowState: WindowState, window: Window,
    onCloseRequest: () -> Unit, modifier: Modifier = Modifier) {
    fun toggleMaximize() {
        windowState.placement = if (windowState.placement == WindowPlacement.Maximized)
            WindowPlacement.Floating else WindowPlacement.Maximized
    }
    var dragStartX by remember { mutableIntStateOf(0) }
    var dragStartY by remember { mutableIntStateOf(0) }
    Row(modifier.fillMaxWidth().height(32.dp).background(Color.Black), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).fillMaxHeight()
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { toggleMaximize() }) }
            .pointerInput(Unit) {
                detectDragGestures(onDragStart = {
                    val mouse = MouseInfo.getPointerInfo().location
                    dragStartX = mouse.x - window.x; dragStartY = mouse.y - window.y
                }, onDrag = { change, _ ->
                    change.consume()
                    val mouse = MouseInfo.getPointerInfo().location
                    if (windowState.placement == WindowPlacement.Maximized) {
                        windowState.placement = WindowPlacement.Floating
                        dragStartX = window.width / 2; dragStartY = 16
                    }
                    window.setLocation(mouse.x - dragStartX, mouse.y - dragStartY)
                })
            }, contentAlignment = Alignment.CenterStart) {
            Text(title, Modifier.padding(start = 16.dp), color = Color(0xFFB3B3B3), fontSize = 11.sp)
        }
        CaptionButton(Icons.Rounded.Remove, stringResource(Res.string.desktop_minimize)) { windowState.isMinimized = true }
        CaptionButton(if (windowState.placement == WindowPlacement.Maximized) Icons.Rounded.FilterNone else Icons.Rounded.CropSquare,
            stringResource(Res.string.desktop_maximize), onClick = ::toggleMaximize)
        CaptionButton(Icons.Rounded.Close, stringResource(Res.string.desktop_close_window), close = true, onClick = onCloseRequest)
    }
}

@Composable
private fun CaptionButton(icon: ImageVector, label: String, close: Boolean = false, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    IconButton(onClick = onClick, modifier = Modifier.width(46.dp).height(32.dp)
        .background(if (hovered) if (close) Color(0xFFE81123) else Color(0xFF303030) else Color.Transparent)
        .hoverable(interaction), interactionSource = interaction) {
        Icon(icon, label, Modifier.size(14.dp), tint = Color.White)
    }
}
