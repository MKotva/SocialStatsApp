package com.example.socialstasts.mock

import android.content.Context
import java.io.File
import java.io.FileOutputStream

object MediaSeeder {
    private const val MEDIADIR = "mock_media"

    /**
     * Returns the app external storage directory used for media (at least from what I know)
     */
    fun mediaDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "media")
        if (!dir.exists())
            dir.mkdirs()
        return dir
    }

    /**
     * Copies bundled mock media from the app assets into the runtime media directory
     * only if the directory is currently empty. It is just in the sake of testing the
     * app. Real use would download the actual media from our server
     */
    fun setDefaultMedia(context: Context) {
        val outDir = mediaDir(context)
        val exist = outDir.listFiles()?.filter { it.isFile } ?: emptyList()
        if (exist.isNotEmpty())
            return

        for (name in context.assets.list(MEDIADIR)?.toList().orEmpty()) {
            context.assets.open("$MEDIADIR/$name").use { input ->
                FileOutputStream(File(outDir, name)).use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    /** Lists all media files currently present in the runtime media directory*/
    fun listMediaUris(context: Context): List<String> {
        val files = mediaDir(context).listFiles()?.filter { it.isFile } ?: emptyList()
        return files.map { it.toURI().toString() }
    }

    /** Returns only image URIs */
    fun listImageUris(context: Context): List<String> =
        listMediaUris(context).filter {
                    it.endsWith(".jpg", true) ||
                    it.endsWith(".jpeg", true) ||
                    it.endsWith(".png", true) ||
                    it.endsWith(".webp", true)
        }

    /** Returns only video URIs */
    fun listVideoUris(context: Context): List<String> =
        listMediaUris(context).filter {
                    it.endsWith(".mp4", true) ||
                    it.endsWith(".webm",true) ||
                    it.endsWith(".mkv", true)
        }
}