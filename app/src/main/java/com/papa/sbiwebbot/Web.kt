//app/src/main/java/com/papa/sbiwebbot/Web.kt
//ver 1.00-35
package com.papa.sbiwebbot

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.WebChromeClient
import android.webkit.JsPromptResult
import android.webkit.JsResult
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
        fun onClickResult(label: String, ok: Boolean)
    }

    private var callback: WebCallback? = null
    private var currentUrl: String? = null

    private var lastInspectUrl: String? = null
    private var lastInspectAt: Long = 0L

    private val ui = Handler(Looper.getMainLooper())

    private val jsBridge = WebJsBridge(webView.context, display) { callback }

    init {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true

            // allow scroll/zoom for device-auth popup pages
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
        }
        webView.addJavascriptInterface(jsBridge, "AndroidApp")

        webView.webChromeClient = object : WebChromeClient() {
            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                display.appendLog("JS_ALERT: " + (message ?: ""))
                result?.confirm()
                return true
            }
            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                display.appendLog("JS_CONFIRM: " + (message ?: ""))
                result?.confirm()
                return true
            }
            override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: JsPromptResult?): Boolean {
                display.appendLog("JS_PROMPT: " + (message ?: ""))
                // SBI系は基本OKで閉じる
                result?.confirm(defaultValue ?: "")
                return true
            }
        }

        webView.webViewClient = object : WebViewClient() {

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                currentUrl = url
                // RECは「未知サイト(=メールURL等)」のみ使用
                if (shouldInspect(url)) {
                    display.setTabState(2, true, "#FFFFCC") // REC: サイト表示中(薄黄点滅)
                } else {
                    display.setTabState(2, false, "#FFFFFF")
                }
                callback?.onWebLoading(true, url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                currentUrl = url
                display.appendLog("Web: Page -> ${(view?.title ?: "No Title")} ($url)")
                // RECは「未知サイト(=メールURL等)」のみ使用
                if (shouldInspect(url)) {
                    display.setTabState(2, false, "#FFFF99") // REC: サイト表示完了(黄点灯)
                } else {
                    display.setTabState(2, false, "#FFFFFF")
                }
                callback?.onWebLoading(false, url)

                if (url?.contains("ETGate") == true) {
                    // 遷移が遅いのでログイン画面へ直行
                    webView.loadUrl("https://login.sbisec.co.jp/login/entry?cccid=main-site-user")
                }

                webView.evaluateJavascript(WebScripts.popupAutoCloseScript(), null)

                // デバイス認証URL(メールから開いたサイト)のフィッシング注意ポップアップを閉じる
                if (url != null && url.contains("/deviceAuthentication/input", ignoreCase = true)) {
                    // JS側で複数回リトライし、成功/失敗に関わらずREC候補も送ってくる
                    webView.evaluateJavascript(WebScripts.deviceAuthPopupProceedScript(), null)
                }

                // 解析は「未知サイト(=メールURL等)」のみ。
                // 無限ループ防止のため、同一URLでの連続実行を抑制する。
                if (shouldInspect(url)) {
                    requestInspect(reason = "pageFinished")
                }
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
                "if('$action'==='click'){try{el.focus();}catch(e){} try{el.scrollIntoView({block:'center'});}catch(e){} try{el.click();}catch(e){} try{var ev1=new MouseEvent('mousedown',{view:window,bubbles:true,cancelable:true});el.dispatchEvent(ev1);}catch(e){} try{var ev2=new MouseEvent('mouseup',{view:window,bubbles:true,cancelable:true});el.dispatchEvent(ev2);}catch(e){} try{var ev3=new MouseEvent('click',{view:window,bubbles:true,cancelable:true});el.dispatchEvent(ev3);}catch(e){} try{var tev1=new TouchEvent('touchstart',{bubbles:true,cancelable:true});el.dispatchEvent(tev1);}catch(e){} try{var tev2=new TouchEvent('touchend',{bubbles:true,cancelable:true});el.dispatchEvent(tev2);}catch(e){} } else {try{el.focus();}catch(e){} el.value='$value';}" +
            "})();"
        webView.evaluateJavascript(js, null)
    }

    fun autoLogin(json: String) {
        try {
            val c = JSONObject(json).getJSONObject("sbi_credentials")
            val user = c.getString("username")
            val pass = c.getString("password")
            // XPath固定はSBI側の更新で壊れやすいので、JSで探索して入力&クリック
            webView.evaluateJavascript(WebScripts.autoLoginScript(user, pass), null)
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

    fun submitDeviceAuthCode(code: String) {
        webView.evaluateJavascript(WebScripts.submitDeviceAuthScript(code), null)
    }

    fun tryClosePopup() {
        display.appendLog("Popup: try close")
        webView.evaluateJavascript(WebScripts.popupAutoCloseScript(), null)
    }

    fun extractRanking() {
        display.appendLog("Ranking: start extract")
        display.setTabState(2, true, "#FFFFCC")
        webView.evaluateJavascript(WebScripts.rankingScript(), null)
    }

    
    private fun requestInspect(reason: String) {
        val url = currentUrl
        if (!shouldInspect(url)) return

        val now = System.currentTimeMillis()
        // 同一URLで短時間に連続実行しない（無限ループ/過負荷防止）
        if (url != null && url == lastInspectUrl && (now - lastInspectAt) < 1500) {
            return
        }
        lastInspectUrl = url
        lastInspectAt = now

        display.appendLog("REC: inspect ($reason) url=${url ?: ""}")
        injectInspectScript()
    }

private fun injectInspectScript() {
        display.setTabState(2, true, "#FFFFCC")
        webView.evaluateJavascript(WebScripts.inspectScript(), null)
    }

    private fun shouldInspect(url: String?): Boolean {
        if (url.isNullOrBlank()) return false

        // mail URL(デバイス認証)や未知サイトのみ解析する
        if (url.contains("/deviceAuthentication/", ignoreCase = true)) return true

        // 既知(解析不要) : TOP / LOGIN / OTP
        val knownNoInspect = listOf(
            "https://www.sbisec.co.jp/ETGate",
            "https://login.sbisec.co.jp/login/",
            "https://login.sbisec.co.jp/idpw/",
            "/otp/entry",
            "/otp/confirm"
        )
        for (k in knownNoInspect) {
            if (url.contains(k, ignoreCase = true)) return false
        }
        return true
    }
}