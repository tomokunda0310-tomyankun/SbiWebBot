//app/src/main/java/com/papa/sbiwebbot/LogStore.kt
//ver 1.02-09
package com.papa.sbiwebbot

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.MediaStore.MediaColumns
import androidx.documentfile.provider.DocumentFile

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
    data class ExportResult(
        val ok: Boolean,
        val publicDir: String,
        val message: String,
    )
    private data class Sink(
        val internalName: String,
        var publicUri: Uri? = null
    )

    private val sinks = mutableMapOf<String, Sink>()

    @Volatile
    private var lastPublicError: String = ""

    private val prefs = context.getSharedPreferences("logstore", Context.MODE_PRIVATE)
    private val PREF_PUBLIC_TREE_URI = "public_tree_uri"

    fun setPublicTreeUri(uri: Uri?) {
        if (uri == null) {
            prefs.edit().remove(PREF_PUBLIC_TREE_URI).apply()
            return
        }
        prefs.edit().putString(PREF_PUBLIC_TREE_URI, uri.toString()).apply()
    }

    fun getPublicTreeUri(): Uri? {
        val s = prefs.getString(PREF_PUBLIC_TREE_URI, null) ?: return null
        return try { Uri.parse(s) } catch (_: Throwable) { null }
    }

    fun hasPublicTree(): Boolean = getPublicTreeUri() != null

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
            lastPublicError = ""
        } catch (t: Throwable) {
            lastPublicError = "${t.javaClass.simpleName}: ${t.message ?: ""}".trim()
        }
    }

    /**
 * Create (or find) public file.
 *
 * Priority:
 *  1) If user selected a folder via SAF (OpenDocumentTree), write there (most reliable on Android 13/14).
 *  2) Otherwise, try MediaStore Downloads: Download/Sbi/
 */
private fun createPublicFile(displayName: String, mime: String): Uri? {
    // 1) SAF tree (user-selected folder)
    val treeUri = getPublicTreeUri()
    if (treeUri != null) {
        try {
            val dir = DocumentFile.fromTreeUri(context, treeUri)
            if (dir != null && dir.isDirectory) {
                val existing = dir.findFile(displayName)
                val f = existing ?: dir.createFile(mime, displayName)
                if (f != null) return f.uri
            }
        } catch (t: Throwable) {
            // fallthrough to MediaStore
            lastPublicError = "SAF: ${t.javaClass.simpleName}"
        }
    }

    // 2) MediaStore Downloads
    val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI

    fun insertWithPath(path: String): Uri? {
        val values = ContentValues().apply {
            put(MediaColumns.DISPLAY_NAME, displayName)
            put(MediaColumns.MIME_TYPE, mime)
            put(MediaColumns.RELATIVE_PATH, path)
        }
        return context.contentResolver.insert(collection, values)
    }

    // Prefer official constant: "Download" (DIRECTORY_DOWNLOADS), and also try vendor variations.
    val p1 = Environment.DIRECTORY_DOWNLOADS + "/Sbi/"
    val uri = insertWithPath(p1)
        ?: insertWithPath("Download/Sbi/")
        ?: insertWithPath("Downloads/Sbi/")
        ?: return null

    return uri
}

    private fun syncInternalToPublic(internalName: String, uri: Uri) {
    // NOTE: openOutputStream can throw FileNotFoundException depending on provider.
    context.openFileInput(internalName).use { ins ->
        val os = context.contentResolver.openOutputStream(uri, "w")
            ?: throw java.io.FileNotFoundException("openOutputStream returned null")
        os.use { out ->
            ins.copyTo(out)
            out.flush()
        }
    }
}

    /**
     * For displaying to user.
     */
    fun getPublicDirHint(): String {
        val t = getPublicTreeUri()
        return if (t != null) {
            "選択フォルダ(Logs)"
        } else {
            "Download/Sbi"
        }
    }
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

    /**
        * Force export (sync) all known internal log files into Download/Sbi.
        * This is used when automatic sync fails or user wants a manual refresh.
        */
    fun exportAllToPublic(): ExportResult {
        val dir = getPublicDirHint()
        val keys = sinks.keys.toList()
        if (keys.isEmpty()) {
            return ExportResult(false, dir, "no sinks")
        }

        var okCount = 0
        val errors = mutableListOf<String>()
        for (k in keys) {
            val s = sinks[k] ?: continue
            try {
                if (s.publicUri == null) {
                    s.publicUri = createPublicFile(s.internalName, "text/plain")
                }
                val uri = s.publicUri
                if (uri == null) {
                    errors.add("${s.internalName}: uri=null")
                    continue
                }
                syncInternalToPublic(s.internalName, uri)
                okCount += 1
            } catch (t: Throwable) {
                errors.add("${s.internalName}: ${t.javaClass.simpleName}(${t.message ?: ""})")
            }
        }

        val ok = okCount > 0 && errors.isEmpty()
        val msg = if (errors.isEmpty()) {
            "okCount=$okCount"
        } else {
            "okCount=$okCount err=${errors.joinToString(" | ")}".take(300)
        }
        return ExportResult(ok, dir, msg)
    }

    fun getLastPublicError(): String = lastPublicError
}
