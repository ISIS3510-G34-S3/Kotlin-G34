package com.example.kotlinview.data.local.files

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.URL
import java.util.Locale

class LocalImageStore(private val context: Context) {

    private fun imagesDir(): File {
        val base = File(context.filesDir, "catalog/images")
        if (!base.exists()) base.mkdirs()
        return base
    }

    private fun extFromUrl(url: String): String {
        val guess = MimeTypeMap.getFileExtensionFromUrl(url)
        if (!guess.isNullOrBlank()) return guess.lowercase(Locale.US)
        val path = Uri.parse(url).lastPathSegment ?: return "jpg"
        val dot = path.lastIndexOf('.')
        return if (dot != -1 && dot < path.length - 1) path.substring(dot + 1).lowercase(Locale.US) else "jpg"
    }

    fun localImageFileFor(expId: String, ext: String? = null): File {
        val e = ext?.lowercase(Locale.US) ?: "jpg"
        return File(imagesDir(), "exp_${expId}_orig.$e")
    }

    fun findAnyLocalImageFor(expId: String): File? {
        val dir = imagesDir()
        // look for exp_<id>_orig.*
        return dir.listFiles()?.firstOrNull { it.name.startsWith("exp_${expId}_orig.") }
    }

    suspend fun saveOriginalIfNeeded(expId: String, primaryImageUrl: String): File? = withContext(Dispatchers.IO) {
        try {
            // Skip if already exists
            findAnyLocalImageFor(expId)?.let { return@withContext it }

            val ext = extFromUrl(primaryImageUrl)
            val outFile = localImageFileFor(expId, ext)
            val tmp = File(outFile.parentFile, outFile.name + ".download")

            URL(primaryImageUrl).openStream().use { input ->
                tmp.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (outFile.exists()) outFile.delete()
            if (!tmp.renameTo(outFile)) throw IOException("Rename failed")
            outFile
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            null
        }
    }

    /** Optional very simple LRU by count (keep at most maxCount files). */
    suspend fun enforceCountCap(maxCount: Int = 12) = withContext(Dispatchers.IO) {
        val files = imagesDir().listFiles()?.toList().orEmpty()
        if (files.size <= maxCount) return@withContext
        val sorted = files.sortedBy { it.lastModified() }
        val toDelete = sorted.take(files.size - maxCount)
        toDelete.forEach { runCatching { it.delete() } }
    }
}
