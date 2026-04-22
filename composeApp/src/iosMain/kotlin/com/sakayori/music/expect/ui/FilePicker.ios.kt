package com.sakayori.music.expect.ui

import androidx.compose.runtime.Composable

private object NoopFilePickerLauncher : FilePickerLauncher {
    override fun launch() {}
}

@Composable
actual fun filePickerResult(
    mimeType: String,
    onResultUri: (String?) -> Unit,
): FilePickerLauncher = NoopFilePickerLauncher

@Composable
actual fun fileSaverResult(
    fileName: String,
    mimeType: String,
    onResultUri: (String?) -> Unit,
): FilePickerLauncher = NoopFilePickerLauncher
