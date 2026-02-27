//app/src/main/java/com/papa/sbiwebbot/LogStore.kt
//ver 1.02-07
package com.papa.sbiwebbot

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.MediaStore.MediaColumns

/**
 * Log files are written to BOTH:
 *  - internal: /data/data/<pkg>/files/
 *  - public:   Download/Sbi/  (MediaStore Downloads)
 *
 * adbなしでもファイルマネージャで参照可能。
 */
class LogStore(
    private val context: Context,
    private val sid: String,
    private val version: String,
) {
    private data class Sink(
        val internalName: String,
        var publicUri: Uri? = null
    )

    private val sinks = mutableMapOf<String, Sink>()

    private fun sink(key: String, ext: String): Sink {
        return sinks.getOrPut("$key.$ext") {
            Sink(internalName = "${key}_${sid}.$ext")
        }
    }

    fun appendOplog(lineWithLf: String) {
        append("oplog", "txt", "text/plain", lineWithLf)
    }

    fun appendJsonl(key: String, lineWithLf: String) {
        append(key, "jsonl", "text/plain", lineWithLf)
    }

    private fun append(key: String, ext: String, mime: String, lineWithLf: String) {
        val s = sink(key, ext)

        // 1) internal append (best-effort)
        try {
            context.openFileOutput(s.internalName, Context.MODE_APPEND).use { out ->
                out.write(lineWithLf.toByteArray(Charsets.UTF_8))
            }
        } catch (_: Throwable) {
        }

        // 2) public sync (overwrite whole file)
        // NOTE: "append" mode ("wa") is not reliable on some devices/providers.
        try {
            if (s.publicUri == null) {
                s.publicUri = createPublicFile(s.internalName, mime)
            }
            val uri = s.publicUri ?: return
            syncInternalToPublic(s.internalName, uri)
        } catch (_: Throwable) {
            // ignore
        }
    }

    /**
     * Create file in Downloads/Sbi/ using MediaStore (scoped storage friendly).
     */
    private fun createPublicFile(displayName: String, mime: String): Uri? {
        val values = ContentValues().apply {
            put(MediaColumns.DISPLAY_NAME, displayName)
            put(MediaColumns.MIME_TYPE, mime)
            // "Download/Sbi/"  (Android uses "Download" not "Downloads")
            put(MediaColumns.RELATIVE_PATH, "Download/Sbi/")
            put(MediaColumns.IS_PENDING, 1)
        }

        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val uri = context.contentResolver.insert(collection, values) ?: return null

        // mark not pending
        try {
            val done = ContentValues().apply { put(MediaColumns.IS_PENDING, 0) }
            context.contentResolver.update(uri, done, null, null)
        } catch (_: Throwable) {
        }
        return uri
    }

    private fun syncInternalToPublic(internalName: String, uri: Uri) {
        context.openFileInput(internalName).use { ins ->
            context.contentResolver.openOutputStream(uri, "w").use { os ->
                if (os != null) {
                    ins.copyTo(os)
                    os.flush()
                }
            }
        }
    }

    /**
     * For displaying to user.
     */
    fun getPublicDirHint(): String = "Download/Sbi/"
    fun getInternalDirHint(): String = "/data/data/${context.packageName}/files/ (非表示のため通常は見れない)"

    /**
     * internal file names (for debug)
     */
    fun listInternalFilesHint(): String {
        return try {
            val files = context.filesDir.listFiles()?.map { it.name }?.sorted() ?: emptyList()
            files.joinToString(", ")
        } catch (_: Throwable) {
            ""
        }
    }
}
