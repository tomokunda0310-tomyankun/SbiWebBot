//app/src/main/java/com/papa/sbiwebbot/WebJsBridge.kt
//ver 1.00-30
package com.papa.sbiwebbot

import android.webkit.JavascriptInterface
import org.json.JSONArray

class WebJsBridge(
    private val display: Display,
    private val callback: () -> Web.WebCallback?
) {
    private var lastAuthCode: String? = null

    @JavascriptInterface
    fun log(msg: String) {
        display.appendLog("JS: $msg")
    }

    @JavascriptInterface
    fun sendElements(json: String) {
        try {
            val arr = JSONArray(json)
            val list = mutableListOf<HtmlElement>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(HtmlElement(o.getString("tag"), o.getString("xpath"), o.getString("text")))
            }
            display.setTabState(2, false, "#FFFF99")
            callback()?.onElementsInspected(list)
        } catch (e: Exception) {
            display.appendLog("sendElements parse fail: ${e.message}")
            display.setTabState(2, false, "#FFFF99")
            callback()?.onElementsInspected(emptyList())
        }
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
