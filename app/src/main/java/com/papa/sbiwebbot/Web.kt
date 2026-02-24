//app/src/main/java/com/papa/sbiwebbot/Web.kt
//ver 1.00-21
package com.papa.sbiwebbot

import android.graphics.Bitmap
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject

data class HtmlElement(val tag: String, val xpath: String, val text: String)

class Web(private val webView: WebView, private val display: Display) {

    interface WebCallback {
        fun onWebLoading(isLoading: Boolean, url: String?)
        fun onElementsInspected(elements: List<HtmlElement>)
        fun onAuthDetected(code: String)
        fun onRankingData(json: String)
    }

    private var callback: WebCallback? = null
    private var currentUrl: String? = null

    private val jsBridge = WebJsBridge(display) { callback }

    init {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
        }

        webView.addJavascriptInterface(jsBridge, "AndroidApp")

        webView.webViewClient = object : WebViewClient() {

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                currentUrl = url
                display.setTabState(2, true, "#FFFFCC") // REC: サイト表示中(薄黄点滅)
                callback?.onWebLoading(true, url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                currentUrl = url
                display.appendLog("Web: Page -> ${(view?.title ?: "No Title")} ($url)")
                display.setTabState(2, false, "#FFFF99") // REC: サイト表示完了(黄点灯)
                callback?.onWebLoading(false, url)

                if (url?.contains("ETGate") == true) {
                    executeAction("/html[1]/body[1]/div[6]/div[5]/div[1]/a[1]", "click")
                }

                injectInspectScript()
            }
        }
    }

    fun setCallback(cb: WebCallback) { callback = cb }
    fun loadUrl(url: String) { webView.loadUrl(url) }
    fun goBack() { if (webView.canGoBack()) webView.goBack() }
    fun getCurrentUrl(): String? = currentUrl

    fun executeAction(xpath: String, action: String, value: String = "") {
        val js =
            "(function(){" +
                "var el=document.evaluate('$xpath',document,null,9,null).singleNodeValue;" +
                "if(!el){AndroidApp.log('executeAction: element not found'); return;}" +
                "if('$action'==='click'){el.click();} else {try{el.focus();}catch(e){} el.value='$value';}" +
            "})();"
        webView.evaluateJavascript(js, null)
    }

    fun autoLogin(json: String) {
        try {
            val c = JSONObject(json).getJSONObject("sbi_credentials")
            executeAction(
                "/html[1]/body[1]/main[1]/section[1]/article[1]/div[2]/ul[1]/li[2]/form[1]/ul[1]/li[1]/div[1]/input[1]",
                "input",
                c.getString("username")
            )
            executeAction(
                "/html[1]/body[1]/main[1]/section[1]/article[1]/div[2]/ul[1]/li[2]/form[1]/ul[1]/li[2]/div[1]/input[1]",
                "input",
                c.getString("password")
            )
            executeAction("id(\"pw-btn\")", "click")
        } catch (e: Exception) {
            display.appendLog("Login Fail: ${e.message}")
        }
    }

    fun sendOtpEmail() {
        clickByText("otp_send_email", "Eメールを送信する")
    }

    fun clickByText(label: String, needle: String) {
        webView.evaluateJavascript(WebScripts.clickByTextScript(label, needle), null)
    }

    fun extractRanking() {
        display.appendLog("Ranking: start extract")
        display.setTabState(2, true, "#FFFFCC")
        webView.evaluateJavascript(WebScripts.rankingScript(), null)
    }

    private fun injectInspectScript() {
        display.setTabState(2, true, "#FFFFCC")
        webView.evaluateJavascript(WebScripts.inspectScript(), null)
    }
}
