@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.sakayori.music.expect.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSURL
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.darwin.NSObject

private fun topViewControllerForPhotoPicker(): platform.UIKit.UIViewController? {
    var vc = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (vc?.presentedViewController != null) {
        vc = vc.presentedViewController
    }
    return vc
}

private class PhotoPickerDelegate(
    private val onResultUri: (String?) -> Unit,
) : NSObject(), PHPickerViewControllerDelegateProtocol {
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val first = didFinishPicking.firstOrNull() as? PHPickerResult
        val provider = first?.itemProvider
        if (provider == null) {
            onResultUri(null)
            return
        }
        provider.loadFileRepresentationForTypeIdentifier("public.image") { url: NSURL?, _ ->
            onResultUri(url?.absoluteString)
        }
    }
}

private class PhotoPickerLauncherImpl(
    private val onResultUri: (String?) -> Unit,
) : PhotoPickerLauncher {
    private var delegate: PhotoPickerDelegate? = null

    override fun launch() {
        val parent = topViewControllerForPhotoPicker() ?: return
        val config = PHPickerConfiguration()
        config.setSelectionLimit(1)
        val picker = PHPickerViewController(configuration = config)
        val kept = PhotoPickerDelegate(onResultUri)
        delegate = kept
        picker.delegate = kept
        parent.presentViewController(picker, animated = true, completion = null)
    }
}

@Composable
actual fun photoPickerResult(onResultUri: (String?) -> Unit): PhotoPickerLauncher =
    remember(onResultUri) { PhotoPickerLauncherImpl(onResultUri) }
