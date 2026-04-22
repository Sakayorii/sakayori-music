package com.sakayori.music.expect.ui

import androidx.compose.runtime.Composable

private object NoopPhotoPickerLauncher : PhotoPickerLauncher {
    override fun launch() {}
}

@Composable
actual fun photoPickerResult(onResultUri: (String?) -> Unit): PhotoPickerLauncher = NoopPhotoPickerLauncher
