//app/src/main/java/com/papa/sbiwebbot/Web.kt
//ver 1.00-15
package com.papa.sbiwebbot
import android.webkit.*
import org.json.JSONObject

data class HtmlElement(val tag: String, val xpath: String, val text: String)

class Web(private val webView: WebView, private val display: Display) {
    interface WebCallback { fun onElementsInspected(elements: List<HtmlElement>) }
    private var callback: WebCallback? = null

    init {
        webView.settings.apply { javaScriptEnabled = true; domStorageEnabled = true; loadWithOverviewMode = true; useWideViewPort = true }
        webView.addJavascriptInterface(object {
            @JavascriptInterface fun log(msg: String) { display.appendLog("JS: $msg") }
            @JavascriptInterface fun sendElements(json: String) {
                val arr = org.json.JSONArray(json); val list = mutableListOf<HtmlElement>()
                for (i in 0 until arr.length()) { val o = arr.getJSONObject(i); list.add(HtmlElement(o.getString("tag"), o.getString("xpath"), o.getString("text"))) }
                display.setTabState(2, false, "#FFFFCC") // 薄黄色点灯
                callback?.onElementsInspected(list)
            }
        }, "AndroidApp")
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                display.appendLog("Web: Loaded -> $url")
                if (url?.contains("ETGate") == true) { 
                    executeAction("/html[1]/body[1]/div[6]/div[5]/div[1]/a[1]", "click")
                }
                injectInspectScript()
            }
        }
    }
    fun setCallback(cb: WebCallback) { callback = cb }
    fun loadSbi() { webView.loadUrl("https://www.sbisec.co.jp/ETGate/") }
    fun goBack() { if (webView.canGoBack()) webView.goBack() }

    fun executeAction(xpath: String, action: String, value: String = "") {
        val js = "(function(){ var el = document.evaluate('$xpath', document, null, 9, null).singleNodeValue; " +
                 "if(!el) return; if('$action'==='click') el.click(); else el.value='$value'; })();"
        webView.evaluateJavascript(js, null)
    }

    private fun injectInspectScript() {
        display.setTabState(2, true, "#FFCCCC") // 薄赤点滅
        display.appendLog("REC: Scanning...")
        val js = """
            (function() {
                function getXP(el) {
                    if (el.id) return 'id("' + el.id + '")';
                    var path = '';
                    while (el && el.nodeType === 1) {
                        var i = 1, s = el.previousSibling;
                        while (s) { if (s.nodeType === 1 && s.tagName === el.tagName) i++; s = s.previousSibling; }
                        path = '/' + el.tagName.toLowerCase() + '[' + i + ']' + path;
                        el = el.parentNode;
                    }
                    return path;
                }
                var elements = [], tags = ['input', 'button', 'a'];
                tags.forEach(t => { Array.from(document.getElementsByTagName(t)).forEach(e => {
                    elements.push({tag: t, xpath: getXP(e), text: (e.innerText || e.value || '').substring(0, 20)});
                });});
                AndroidApp.sendElements(JSON.stringify(elements));
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    fun autoLogin(json: String) {
        try {
            val c = JSONObject(json).getJSONObject("sbi_credentials")
            executeAction("/html[1]/body[1]/main[1]/section[1]/article[1]/div[2]/ul[1]/li[2]/form[1]/ul[1]/li[1]/div[1]/input[1]", "input", c.getString("username"))
            executeAction("/html[1]/body[1]/main[1]/section[1]/article[1]/div[2]/ul[1]/li[2]/form[1]/ul[1]/li[2]/div[1]/input[1]", "input", c.getString("password"))
            executeAction("id(\"pw-btn\")", "click")
        } catch (e: Exception) { display.appendLog("Login Fail") }
    }
}
