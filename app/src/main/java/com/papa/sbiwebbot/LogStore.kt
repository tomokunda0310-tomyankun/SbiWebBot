//app/src/main/java/com/papa/sbiwebbot/LogStore.kt
//ver 1.02-33
package com.papa.sbiwebbot

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.MediaStore.MediaColumns
import androidx.documentfile.provider.DocumentFile
import java.io.File

/**
 * Log files are written to BOTH:
 *  - internal: /data/data/<pkg>/files/<sid>/...
 *  - public:   Download/Sbi/<sid>/...  (MediaStore Downloads / SAF)
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
        val relPath: String,
        val mime: String,
        var publicUri: Uri? = null,
    )

    private val sinks = mutableMapOf<String, Sink>() // key=relPath

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

    private fun sink(relPath: String, mime: String): Sink {
        val rp = relPath.replace("\\", "/").trimStart('/')
        return sinks.getOrPut(rp) { Sink(relPath = rp, mime = mime) }
    }

    /**
     * Ensure internal run folders exist.
     * internal root: files/<sid>/(log|json|html|rec|pins|xpath)
     */
    fun ensureRunFolders() {
        try {
            val root = File(context.filesDir, sid)
            File(root, "log").mkdirs()
            File(root, "json").mkdirs()
            File(root, "html").mkdirs()
            File(root, "rec").mkdirs()
            File(root, "pins").mkdirs()
            File(root, "xpath").mkdirs()
        } catch (_: Throwable) {
        }
    }

    fun getRunDirName(): String = sid

    fun appendOplog(lineWithLf: String) {
        appendText("log/oplog.txt", "text/plain", lineWithLf)
    }

    fun appendJsonl(relPath: String, lineWithLf: String) {
        val rp = if (relPath.endsWith(".jsonl")) relPath else "$relPath.jsonl"
        appendText(rp, "text/plain", lineWithLf)
    }

    fun appendLog(relPath: String, lineWithLf: String) {
        val rp = if (relPath.endsWith(".log")) relPath else "$relPath.log"
        appendText(rp, "text/plain", lineWithLf)
    }

    fun writeText(relPath: String, mime: String, content: String) {
        val s = sink(relPath, mime)
        // internal overwrite
        try {
            val f = internalFile(s.relPath)
            f.parentFile?.mkdirs()
            f.writeText(content, Charsets.UTF_8)
        } catch (_: Throwable) {
        }

        // public sync
        try {
            if (s.publicUri == null) {
                s.publicUri = createPublicFile(s.relPath, s.mime)
            }
            val uri = s.publicUri ?: return
            syncInternalToPublic(s.relPath, uri)
            lastPublicError = ""
        } catch (t: Throwable) {
            lastPublicError = "${t.javaClass.simpleName}: ${t.message ?: ""}".trim()
        }
    }

    private fun appendText(relPath: String, mime: String, lineWithLf: String) {
        val s = sink(relPath, mime)
        // 1) internal append (best-effort)
        try {
            val f = internalFile(s.relPath)
            f.parentFile?.mkdirs()
            f.appendText(lineWithLf, Charsets.UTF_8)
        } catch (_: Throwable) {
        }

        // 2) public sync (overwrite whole file)
        // NOTE: "append" mode ("wa") is not reliable on some devices/providers.
        try {
            if (s.publicUri == null) {
                s.publicUri = createPublicFile(s.relPath, s.mime)
            }
            val uri = s.publicUri ?: return
            syncInternalToPublic(s.relPath, uri)
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
    private fun createPublicFile(relPath: String, mime: String): Uri? {
        val rp = relPath.replace("\\", "/").trimStart('/')
        val fileName = rp.substringAfterLast('/')
        val parentPath = rp.substringBeforeLast('/', missingDelimiterValue = "")

        // 1) SAF tree (user-selected folder)
        val treeUri = getPublicTreeUri()
        if (treeUri != null) {
            try {
                val picked = DocumentFile.fromTreeUri(context, treeUri)
                if (picked != null && picked.isDirectory) {
                    val base = if (picked.name == "Sbi") picked else (picked.findFile("Sbi") ?: picked.createDirectory("Sbi"))
                    val run = base?.let { it.findFile(sid) ?: it.createDirectory(sid) }
                    if (run != null) {
                        val dir = ensureSubDirs(run, parentPath)
                        val existing = dir.findFile(fileName)
                        val f = existing ?: dir.createFile(mime, fileName)
                        if (f != null) return f.uri
                    }
                }
            } catch (t: Throwable) {
                lastPublicError = "SAF: ${t.javaClass.simpleName}"
            }
        }

        // 2) MediaStore Downloads
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI

        fun insertWithPath(path: String): Uri? {
            val values = ContentValues().apply {
                put(MediaColumns.DISPLAY_NAME, fileName)
                put(MediaColumns.MIME_TYPE, mime)
                put(MediaColumns.RELATIVE_PATH, path)
            }
            return context.contentResolver.insert(collection, values)
        }

        val sub = if (parentPath.isBlank()) "" else "$parentPath/"
        val p1 = Environment.DIRECTORY_DOWNLOADS + "/Sbi/$sid/$sub"
        return insertWithPath(p1)
            ?: insertWithPath("Download/Sbi/$sid/$sub")
            ?: insertWithPath("Downloads/Sbi/$sid/$sub")
    }

    private fun ensureSubDirs(root: DocumentFile, parentPath: String): DocumentFile {
        var cur = root
        val parts = parentPath.split('/').map { it.trim() }.filter { it.isNotEmpty() }
        for (p in parts) {
            val next = cur.findFile(p) ?: cur.createDirectory(p)
            if (next != null && next.isDirectory) {
                cur = next
            }
        }
        return cur
    }

    private fun internalFile(relPath: String): File {
        val rp = relPath.replace("\\", "/").trimStart('/')
        return File(File(context.filesDir, sid), rp)
    }

    private fun syncInternalToPublic(relPath: String, uri: Uri) {
        val f = internalFile(relPath)
        if (!f.exists()) throw java.io.FileNotFoundException("internal not found: ${f.absolutePath}")
        context.contentResolver.openOutputStream(uri, "w")?.use { out ->
            f.inputStream().use { ins ->
                ins.copyTo(out)
                out.flush()
            }
        } ?: throw java.io.FileNotFoundException("openOutputStream returned null")
    }

    /**
     * For displaying to user.
     */
    fun getPublicDirHint(): String {
        val t = getPublicTreeUri()
        return if (t != null) {
            "選択フォルダ/Sbi/$sid"
        } else {
            "Download/Sbi/$sid"
        }
    }
    fun getInternalDirHint(): String = "/data/data/${context.packageName}/files/ (非表示のため通常は見れない)"

    /**
     * internal file names (for debug)
     */
    fun listInternalFilesHint(): String {
        return try {
            val root = File(context.filesDir, sid)
            val files = root.walkTopDown().filter { it.isFile }.map { it.relativeTo(root).path }.toList().sorted()
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
                    s.publicUri = createPublicFile(s.relPath, s.mime)
                }
                val uri = s.publicUri
                if (uri == null) {
                    errors.add("${s.relPath}: uri=null")
                    continue
                }
                syncInternalToPublic(s.relPath, uri)
                okCount += 1
            } catch (t: Throwable) {
                errors.add("${s.relPath}: ${t.javaClass.simpleName}(${t.message ?: ""})")
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

    /**
     * Read internal text for UI preview (best-effort).
     * NOTE: Public (Download/Sbi) read is intentionally NOT done here to avoid provider differences.
     */
    fun readTextOrNull(relPath: String): String? {
        return try {
            val f = internalFile(relPath)
            if (!f.exists()) return null
            f.readText(Charsets.UTF_8)
        } catch (_: Throwable) {
            null
        }
    }
}
