package com.sakayori.music.ui.mini_player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.sakayori.logger.Logger
import com.sakayori.music.viewModel.SharedViewModel
import com.sakayori.music.viewModel.UIEvent
import org.jetbrains.compose.resources.painterResource
import com.sakayori.music.generated.resources.Res
import com.sakayori.music.generated.resources.circle_app_icon
import java.awt.Dimension
import java.util.prefs.Preferences

@Composable
fun MiniPlayerWindow(
    sharedViewModel: SharedViewModel,
    onCloseRequest: () -> Unit,
) {
    val prefs = remember { Preferences.userRoot().node("SakayoriMusic/MiniPlayer") }

    val minWidth = 200f
    val minHeight = 56f

    val savedX = prefs.getFloat("windowX", Float.NaN)
    val savedY = prefs.getFloat("windowY", Float.NaN)
    val savedWidth = prefs.getFloat("windowWidth", 400f).coerceAtLeast(minWidth)
    val savedHeight = prefs.getFloat("windowHeight", 56f).coerceAtLeast(minHeight)

    var windowState by remember {
        mutableStateOf(
            WindowState(
                placement = WindowPlacement.Floating,
                position =
                    if (savedX.isNaN() || savedY.isNaN()) {
                        WindowPosition(Alignment.BottomEnd)
                    } else {
                        WindowPosition(savedX.coerceAtLeast(0f).dp, savedY.coerceAtLeast(0f).dp)
                    },
                size = DpSize(savedWidth.coerceAtLeast(0f).dp, savedHeight.coerceAtLeast(0f).dp),
            ),
        )
    }

    LaunchedEffect(windowState.position, windowState.size) {
        val pos = windowState.position
        Logger.w("MiniPlayerWindow", "Saving position: $pos")
        if (pos is WindowPosition.Absolute) {
            prefs.putFloat("windowX", pos.x.value)
            prefs.putFloat("windowY", pos.y.value)
        }
        prefs.putFloat("windowWidth", windowState.size.width.value)
        prefs.putFloat("windowHeight", windowState.size.height.value)
    }

    Window(
        onCloseRequest = onCloseRequest,
        title = "SakayoriMusic - Mini Player",
        icon = painterResource(Res.drawable.circle_app_icon),
        alwaysOnTop = true,
        undecorated = true,
        transparent = true,
        resizable = true,
        state = windowState,
        onKeyEvent = { keyEvent ->
            when {
                keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Spacebar -> {
                    sharedViewModel.onUIEvent(UIEvent.PlayPause)
                    true
                }

                keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionRight -> {
                    sharedViewModel.onUIEvent(UIEvent.Next)
                    true
                }

                keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionLeft -> {
                    sharedViewModel.onUIEvent(UIEvent.Previous)
                    true
                }

                else -> {
                    false
                }
            }
        },
    ) {
        LaunchedEffect(Unit) {
            (window as? java.awt.Window)?.minimumSize =
                Dimension(
                    (minWidth * window.graphicsConfiguration.defaultTransform.scaleX).toInt(),
                    (minHeight * window.graphicsConfiguration.defaultTransform.scaleY).toInt(),
                )
        }

        MiniPlayerRoot(
            sharedViewModel = sharedViewModel,
            onClose = onCloseRequest,
            windowState = windowState,
        )
    }
}
