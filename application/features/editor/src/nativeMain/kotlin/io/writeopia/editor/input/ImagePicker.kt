package io.writeopia.editor.input

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDate
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
@Composable
fun rememberImagePickerLauncher(
    onImageSelected: (String) -> Unit
): () -> Unit {
    val delegate = remember {
        ImagePickerDelegate(onImageSelected)
    }

    DisposableEffect(Unit) {
        onDispose {
            delegate.cleanup()
        }
    }

    return {
        showImagePicker(delegate)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun showImagePicker(delegate: ImagePickerDelegate) {
    val configuration = PHPickerConfiguration().apply {
        filter = PHPickerFilter.imagesFilter
        selectionLimit = 1
    }

    val picker = PHPickerViewController(configuration = configuration)
    picker.delegate = delegate

    val rootViewController = UIApplication.sharedApplication.keyWindow?.rootViewController
    rootViewController?.presentViewController(picker, animated = true, completion = null)
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class ImagePickerDelegate(
    private val onImageSelected: (String) -> Unit
) : NSObject(), PHPickerViewControllerDelegateProtocol {

    private var pickerViewController: PHPickerViewController? = null

    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        pickerViewController = picker

        val results = didFinishPicking.filterIsInstance<PHPickerResult>()

        if (results.isEmpty()) {
            picker.dismissViewControllerAnimated(true, completion = null)
            return
        }

        val result = results.first()
        val itemProvider = result.itemProvider

        if (itemProvider.hasItemConformingToTypeIdentifier(UTTypeImage.identifier)) {
            itemProvider.loadDataRepresentationForTypeIdentifier(
                UTTypeImage.identifier
            ) { data, error ->
                if (error != null || data == null) {
                    picker.dismissViewControllerAnimated(true, completion = null)
                    return@loadDataRepresentationForTypeIdentifier
                }

                val uiImage = UIImage(data = data)
                if (uiImage != null) {
                    val filePath = saveImageToDocuments(uiImage)
                    if (filePath != null) {
                        onImageSelected(filePath)
                    }
                }

                picker.dismissViewControllerAnimated(true, completion = null)
            }
        } else {
            picker.dismissViewControllerAnimated(true, completion = null)
        }
    }

    private fun saveImageToDocuments(image: UIImage): String? {
        val imageData = UIImageJPEGRepresentation(image, 0.8) ?: return null

        val fileManager = NSFileManager.defaultManager
        val documentsUrl = fileManager.URLsForDirectory(
            NSDocumentDirectory,
            NSUserDomainMask
        ).firstOrNull() as? NSURL ?: return null

        val fileName = "image_${NSDate().timeIntervalSince1970.toLong()}.jpg"
        val fileUrl = documentsUrl.URLByAppendingPathComponent(fileName) ?: return null
        val filePath = fileUrl.path ?: return null

        val success = imageData.writeToFile(filePath, atomically = true)
        return if (success) filePath else null
    }

    fun cleanup() {
        pickerViewController = null
    }
}
