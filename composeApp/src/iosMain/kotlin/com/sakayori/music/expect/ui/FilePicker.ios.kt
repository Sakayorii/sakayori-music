@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.sakayori.music.expect.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeData
import platform.UniformTypeIdentifiers.UTTypeItem
import platform.darwin.NSObject

private fun topViewController(): platform.UIKit.UIViewController? {
    var vc = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (vc?.presentedViewController != null) {
        vc = vc.presentedViewController
    }
    return vc
}

private class DocumentPickerDelegate(
    private val onResultUri: (String?) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
        onResultUri(url?.absoluteString)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onResultUri(null)
    }
}

private class FilePickerLauncherImpl(
    private val mimeType: String,
    private val onResultUri: (String?) -> Unit,
    private val forExport: Boolean,
    private val exportFileName: String? = null,
) : FilePickerLauncher {
    private var delegate: DocumentPickerDelegate? = null

    override fun launch() {
        val parent = topViewController() ?: return
        val kept = DocumentPickerDelegate(onResultUri)
        delegate = kept
        val types = listOf(mimeTypeToUTType(mimeType))
        val picker = if (forExport && exportFileName != null) {
            UIDocumentPickerViewController(
                forExportingURLs = emptyList<NSURL>(),
                asCopy = true,
            )
        } else {
            UIDocumentPickerViewController(forOpeningContentTypes = types)
        }
        picker.delegate = kept
        parent.presentViewController(picker, animated = true, completion = null)
    }

    private fun mimeTypeToUTType(mime: String): UTType = when {
        mime.contains("image") -> UTType.typeWithMIMEType(mime) ?: UTTypeItem
        mime.contains("audio") -> UTType.typeWithMIMEType(mime) ?: UTTypeData
        mime == "*/*" -> UTTypeItem
        else -> UTType.typeWithMIMEType(mime) ?: UTTypeData
    }
}

@Composable
actual fun filePickerResult(
    mimeType: String,
    onResultUri: (String?) -> Unit,
): FilePickerLauncher = remember(mimeType, onResultUri) {
    FilePickerLauncherImpl(mimeType = mimeType, onResultUri = onResultUri, forExport = false)
}

@Composable
actual fun fileSaverResult(
    fileName: String,
    mimeType: String,
    onResultUri: (String?) -> Unit,
): FilePickerLauncher = remember(fileName, mimeType, onResultUri) {
    FilePickerLauncherImpl(
        mimeType = mimeType,
        onResultUri = onResultUri,
        forExport = true,
        exportFileName = fileName,
    )
}
