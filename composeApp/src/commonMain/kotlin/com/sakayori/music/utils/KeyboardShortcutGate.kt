package com.sakayori.music.utils

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged

object KeyboardShortcutGate {
    private val focusedInputs = mutableStateOf(0)
    val hasTextInputFocus: Boolean get() = focusedInputs.value > 0
    internal fun onFocusGained() { focusedInputs.value = focusedInputs.value + 1 }
    internal fun onFocusLost() { focusedInputs.value = (focusedInputs.value - 1).coerceAtLeast(0) }
}

fun Modifier.trackTextInputFocus(): Modifier = composed {
    var wasFocused by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose {
            if (wasFocused) {
                KeyboardShortcutGate.onFocusLost()
                wasFocused = false
            }
        }
    }
    this.onFocusChanged { state ->
        if (state.isFocused && !wasFocused) {
            KeyboardShortcutGate.onFocusGained()
            wasFocused = true
        } else if (!state.isFocused && wasFocused) {
            KeyboardShortcutGate.onFocusLost()
            wasFocused = false
        }
    }
}
