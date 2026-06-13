package app.skipperclub.ui.main.profile

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_AVATAR_BYTES = 10L * 1024 * 1024

internal data class PickedAvatar(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
    val width: Int?,
    val height: Int?,
)

/**
 * Reads a photo-picker result into memory and extracts image dimensions for the
 * avatar upload. Returns null when the file exceeds the 10 MB avatar limit
 * (docs/api/users → Avatar Upload), is not a supported image, or cannot be read.
 *
 * The dispatcher is injectable until an `AppDispatchers` wrapper exists
 * (CLAUDE.md §Don'ts).
 */
internal suspend fun readPickedAvatar(
    context: Context,
    uri: Uri,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
): PickedAvatar? = withContext(dispatcher) {
    val resolver = context.contentResolver
    val mimeType = resolver.getType(uri) ?: "image/jpeg"
    if (mimeType !in SUPPORTED_AVATAR_TYPES) return@withContext null

    val bytes = runCatching {
        resolver.openInputStream(uri)?.use { it.readBytes() }
    }.getOrNull() ?: return@withContext null
    if (bytes.isEmpty() || bytes.size > MAX_AVATAR_BYTES) return@withContext null

    val fileName = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull() ?: defaultFileName(mimeType)

    var width: Int? = null
    var height: Int? = null
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    if (options.outWidth > 0 && options.outHeight > 0) {
        width = options.outWidth
        height = options.outHeight
    }

    PickedAvatar(fileName = fileName, mimeType = mimeType, bytes = bytes, width = width, height = height)
}

private val SUPPORTED_AVATAR_TYPES = setOf("image/jpeg", "image/png", "image/heic")

private fun defaultFileName(mimeType: String): String {
    val extension = when (mimeType) {
        "image/png" -> "png"
        "image/heic" -> "heic"
        else -> "jpg"
    }
    return "avatar.$extension"
}
