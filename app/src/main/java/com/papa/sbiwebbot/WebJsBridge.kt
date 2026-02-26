//app/src/main/java/com/papa/sbiwebbot/WebJsBridge.kt
//ver 1.00-50
package com.papa.sbiwebbot

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.webkit.JavascriptInterface
import org.json.JSONArray

class WebJsBridge(
    private val context: Context,
    private val display: Display,
    private val callback: () -> Web.WebCallback?
) {
    private var lastAuthCode: String? = null

    
    private fun saveRecToDownloadsSbi(text: String) {
        try {
            val resolver = context.contentResolver
            val relativePath = "Download/Sbi"
            val displayName = "rec_xpath.txt"

            // delete existing
            val sel = "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?"
            val args = arrayOf(displayName, "$relativePath/")
            resolver.delete(MediaStore.Downloads.EXTERNAL_CONTENT_URI, sel, args)

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            }
            val uri: Uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
            resolver.openOutputStream(uri, "w")?.use { os ->
                os.write(text.toByteArray(Charsets.UTF_8))
                os.flush()
            }
        } catch (e: Exception) {
            display.appendLog("REC: save fail: ${e.message}")
        }
    }

    private fun handleRecSnapshot(url: String?, title: String?, json: String) {
        try {
            val arr = JSONArray(json)
            val list = mutableListOf<HtmlElement>()
            val sb = StringBuilder()
            sb.append("url=").append(url ?: "").append("\n")
            sb.append("title=").append(title ?: "").append("\n")
            sb.append("count=").append(arr.length()).append("\n\n")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val tag = o.optString("tag")
                val xp = o.optString("xpath")
                val tx = o.optString("text")
                list.add(HtmlElement(tag, xp, tx))
                sb.append(i).append(": ").append(tag).append("  ").append(xp).append("  ").append(tx).append("\n")
            }
            display.appendLog("REC: captured ${list.size} elems (${url ?: ""})")
            saveRecToDownloadsSbi(sb.toString())
            display.setTabState(2, false, "#FFFF99")
            callback()?.onElementsInspected(list)
        } catch (e: Exception) {
            display.appendLog("REC parse fail: ${e.message}")
            display.setTabState(2, false, "#FFFF99")
            callback()?.onElementsInspected(emptyList())
        }
    }

@JavascriptInterface
    fun log(msg: String) {
        display.appendLog("JS: $msg")
    }

    @JavascriptInterface
    fun sendElements(json: String) {
        // backward compat: url/title unknown
        handleRecSnapshot(null, null, json)
    }


    @JavascriptInterface
    fun sendRecSnapshot(url: String?, title: String?, json: String) {
        handleRecSnapshot(url, title, json)
    }

    @JavascriptInterface
    fun onAuth(code: String) {
        if (lastAuthCode == code) return
        lastAuthCode = code
        display.appendLog("!!! AUTH CODE DETECTED: $code !!!")
        callback()?.onAuthDetected(code)
    }

    @JavascriptInterface
    fun onRanking(json: String) {
        display.appendLog("RANKING_JSON len=${json.length}")
        display.setTabState(2, false, "#FFFF99")
        callback()?.onRankingData(json)
    }

    @JavascriptInterface
    fun onClickResult(label: String, ok: Boolean) {
        display.appendLog("ClickTry[$label] => " + (if (ok) "OK" else "NG"))
        callback()?.onClickResult(label, ok)
    }
}
