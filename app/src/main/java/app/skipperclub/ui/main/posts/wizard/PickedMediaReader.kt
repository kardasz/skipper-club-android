package app.skipperclub.ui.main.posts.wizard

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_IMAGE_BYTES = 10L * 1024 * 1024
private const val MAX_VIDEO_BYTES = 50L * 1024 * 1024

internal data class PickedMedia(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
    val width: Int?,
    val height: Int?,
)

/**
 * Reads a photo-picker result into memory and extracts image dimensions.
 * Returns null when the file exceeds the API size limits (10MB images /
 * 50MB videos per docs/api/media) or cannot be read.
 *
 * The dispatcher is injectable until an `AppDispatchers` wrapper exists
 * (CLAUDE.md §Don'ts).
 */
internal suspend fun readPickedMedia(
    context: Context,
    uri: Uri,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
): PickedMedia? = withContext(dispatcher) {
    val resolver = context.contentResolver
    val mimeType = resolver.getType(uri) ?: "image/jpeg"
    val isVideo = mimeType.startsWith("video/")
    val maxBytes = if (isVideo) MAX_VIDEO_BYTES else MAX_IMAGE_BYTES

    val bytes = runCatching {
        resolver.openInputStream(uri)?.use { it.readBytes() }
    }.getOrNull() ?: return@withContext null
    if (bytes.size > maxBytes) return@withContext null

    val fileName = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull() ?: defaultFileName(mimeType)

    var width: Int? = null
    var height: Int? = null
    if (!isVideo) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        if (options.outWidth > 0 && options.outHeight > 0) {
            width = options.outWidth
            height = options.outHeight
        }
    }

    PickedMedia(
        fileName = fileName,
        mimeType = mimeType,
        bytes = bytes,
        width = width,
        height = height,
    )
}

private fun defaultFileName(mimeType: String): String {
    val extension = when (mimeType) {
        "image/png" -> "png"
        "image/heic" -> "heic"
        "video/mp4" -> "mp4"
        else -> "jpg"
    }
    return "media.$extension"
}
