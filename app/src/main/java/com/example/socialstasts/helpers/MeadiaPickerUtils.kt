package com.example.socialstasts.createpost

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.example.socialstasts.helpers.PickedMedia

fun buildPickedMedia(context: Context, uri: Uri): PickedMedia? {
    val resolver = context.contentResolver
    val mime = resolver.getType(uri).orEmpty()

    val mediaType = when {
        mime.startsWith("image/") -> "IMAGE"
        mime.startsWith("video/") -> "VIDEO"
        else -> return null
    }

    try {
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } catch (_: SecurityException) { }
    catch (_: UnsupportedOperationException) { }

    return PickedMedia(
        mediaType = mediaType,
        mediaUri = uri.toString(),
        displayName = queryDisplayName(resolver, uri) ?: uri.lastPathSegment ?: "picked_media"
    )
}

/**
 * Reads media name from content provider metadata, if available
 * */
private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? {
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && cursor.moveToFirst())
            return cursor.getString(idx)
    }
    return null
}