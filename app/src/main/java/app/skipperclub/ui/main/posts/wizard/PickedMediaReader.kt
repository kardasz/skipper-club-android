package app.skipperclub.ui.main.posts.wizard

import android.content.Context
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import app.skipperclub.data.MediaUploadMeta
import java.io.ByteArrayInputStream
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_IMAGE_BYTES = 10L * 1024 * 1024
private const val MAX_VIDEO_BYTES = 50L * 1024 * 1024

internal data class PickedMedia(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
    val meta: MediaUploadMeta,
)

/**
 * Reads a photo-picker result into memory and extracts capture metadata: image
 * dimensions + EXIF (GPS, date, camera, orientation, ISO/aperture/shutter) for
 * photos, and dimensions + duration + frame rate + GPS/date for videos.
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

    val meta = if (isVideo) readVideoMeta(bytes) else readImageMeta(bytes)

    PickedMedia(fileName = fileName, mimeType = mimeType, bytes = bytes, meta = meta)
}

private fun readImageMeta(bytes: ByteArray): MediaUploadMeta {
    var width: Int? = null
    var height: Int? = null
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    if (options.outWidth > 0 && options.outHeight > 0) {
        width = options.outWidth
        height = options.outHeight
    }

    val exif = runCatching { ExifInterface(ByteArrayInputStream(bytes)) }.getOrNull()
        ?: return MediaUploadMeta(width = width, height = height)

    val latLong = FloatArray(2)
    val hasLatLong = runCatching { exif.getLatLong(latLong) }.getOrDefault(false)
    val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0).takeIf { it in 1..8 }
    val camera = listOfNotNull(
        exif.getAttribute(ExifInterface.TAG_MAKE)?.trim()?.takeIf { it.isNotEmpty() },
        exif.getAttribute(ExifInterface.TAG_MODEL)?.trim()?.takeIf { it.isNotEmpty() },
    ).distinct().joinToString(" ").ifBlank { null }
    val dateTaken = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
        ?.let { exifDateToIso(it) }

    val extra = buildMap {
        exif.getAttribute(ExifInterface.TAG_F_NUMBER)?.let { put("aperture", "f/$it") }
        @Suppress("DEPRECATION")
        exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)?.let { put("iso", it) }
        exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.let { put("exposureTime", it) }
        exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)?.let { put("focalLength", it) }
    }

    return MediaUploadMeta(
        width = width,
        height = height,
        camera = camera,
        lat = if (hasLatLong) latLong[0].toDouble() else null,
        lon = if (hasLatLong) latLong[1].toDouble() else null,
        orientation = orientation,
        dateTaken = dateTaken,
        extra = extra,
    )
}

private fun readVideoMeta(bytes: ByteArray): MediaUploadMeta {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(ByteArrayMediaDataSource(bytes))
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
        val durationSeconds = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()?.let { it / 1000.0 }
        val frameRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
            ?.toFloatOrNull()?.toDouble()
        val (lat, lon) = parseIso6709(
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION),
        )
        MediaUploadMeta(
            width = width,
            height = height,
            duration = durationSeconds,
            frameRate = frameRate,
            lat = lat,
            lon = lon,
        )
    } catch (_: Exception) {
        MediaUploadMeta()
    } finally {
        runCatching { retriever.release() }
    }
}

/** Parses EXIF "yyyy:MM:dd HH:mm:ss" (device local time) into an ISO-8601 instant. */
private fun exifDateToIso(value: String): String? = runCatching {
    val formatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")
    val local = LocalDateTime.parse(value.trim(), formatter)
    local.atZone(ZoneId.systemDefault()).toInstant().toString()
}.getOrNull()

/** Parses ISO-6709 ("+54.3520+018.6466/") into (lat, lon). */
private fun parseIso6709(value: String?): Pair<Double?, Double?> {
    if (value.isNullOrBlank()) return null to null
    val match = Regex("([+-]\\d+(?:\\.\\d+)?)([+-]\\d+(?:\\.\\d+)?)").find(value) ?: return null to null
    val lat = match.groupValues[1].toDoubleOrNull()
    val lon = match.groupValues[2].toDoubleOrNull()
    return lat to lon
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

/** Feeds in-memory bytes to [MediaMetadataRetriever] without a temp file. */
private class ByteArrayMediaDataSource(private val data: ByteArray) : MediaDataSource() {
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (position >= data.size) return -1
        val end = minOf(position + size, data.size.toLong()).toInt()
        val length = end - position.toInt()
        if (length <= 0) return -1
        System.arraycopy(data, position.toInt(), buffer, offset, length)
        return length
    }

    override fun getSize(): Long = data.size.toLong()

    override fun close() = Unit
}
