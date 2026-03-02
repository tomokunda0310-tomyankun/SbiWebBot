//app/src/main/java/com/papa/sbiwebbot/MainActivity.kt
//ver 1.02-20
package com.papa.sbiwebbot

import android.content.Intent
import android.os.Bundle
import android.net.Uri
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.RenderProcessGoneDetail
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.tabs.TabLayout
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.text.SimpleDateFormat
import java.util.*
import java.security.MessageDigest

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var tabLayout: TabLayout
    private lateinit var tvLog: TextView
    private lateinit var layoutLog: ScrollView
    private lateinit var lvRec: ListView
    private lateinit var btnBack: Button
    private lateinit var btnRec: Button
    private lateinit var btnLog: Button
    private lateinit var btnYahoo: Button
    private lateinit var btnSbi: Button
    private lateinit var btnExport: Button
    private lateinit var btnNeed: Button
    private lateinit var btnNope: Button
    private lateinit var tvNavInfo: TextView

    private lateinit var display: Display
    private lateinit var logStore: LogStore

    private lateinit var pickLogDirLauncher: ActivityResultLauncher<Uri?>


    // ==== Explore state ====
    private val appVersion = "1.02-20"
    private val sid: String by lazy {
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    }
    private var seq: Long = 0

    private val keywords = listOf("銘柄", "ランキング", "値", "売上", "株価", "上昇", "下落", "値上がり", "値下がり", "出来高")

    private data class RecItem(
        val tag: String,
        val text: String,
        val href: String?,
        val xpath: String,
        val frameCss: String?,
        val score: Int,
        val hitKw: List<String>
    )

    private var recItems: List<RecItem> = emptyList()
    private lateinit var recAdapter: ArrayAdapter<String>

    // navigation tracking
    private var lastFromUrl: String? = null
    private var lastTrigger: RecItem? = null
    private var lastNavTrySeq: Long = -1


    // last REC hit count (for delaying heavy rescans)
    private var lastRecHitCount: Int = 0

    // ===== AUTO crawl (no-login, best-effort) =====
    private enum class AutoMode { NONE, SBI_RANKING }
    private data class AutoStep(val url: String, val pageType: String, val code: String?)
    private var autoMode: AutoMode = AutoMode.NONE
    private val autoQueue: MutableList<AutoStep> = mutableListOf()
    private var autoIndex: Int = 0
    private var autoBusy: Boolean = false
    private var autoCurrentCode: String? = null
    private val autoMaxSteps: Int = 25
    // Files (fixed folder structure)
    private val fileTry = "log/nav_try" // .jsonl will be appended
    private val filePins = "pins/pins"  // .jsonl
    private val fileRecCandidates = "rec/rec_candidates" // .jsonl
    private val fileXpathLog = "log/xpath" // .log
    private val fileSrefLog = "log/sref" // .log

    // Quick jump URLs
    private val URL_YAHOO_UP = "https://finance.yahoo.co.jp/stocks/ranking/up?market=all"

    // SBI rankings (no-login)
    // 値上がり率(東証プライム)
    private val URL_SBI_UPRATE_T1 = "https://www.sbisec.co.jp/ETGate/?OutSide=on&_ActionID=DefaultAID&_ControlID=WPLETmgR001Control&_DataStoreID=DSWPLETmgR001Control&_PageID=WPLETmgR001Mdtl20&burl=iris_ranking&cat1=market&cat2=ranking&dir=tl1-rnk%7Ctl2-stock%7Ctl3-price%7Ctl4-uprate%7Ctl5-priceview%7Ctl7-T1&file=index.html&getFlg=on"
    // 値下がり率(東証プライム)
    private val URL_SBI_DOWNRATE_T1 = "https://www.sbisec.co.jp/ETGate/?OutSide=on&_ActionID=DefaultAID&_ControlID=WPLETmgR001Control&_DataStoreID=DSWPLETmgR001Control&_PageID=WPLETmgR001Mdtl20&burl=iris_ranking&cat1=market&cat2=ranking&dir=tl1-rnk%7Ctl2-stock%7Ctl3-price%7Ctl4-downrate%7Ctl5-priceview%7Ctl7-T1&file=index.html&getFlg=on"
    // 出来高上位(東証プライム)
    private val URL_SBI_TURNOVER_T1 = "https://www.sbisec.co.jp/ETGate/?OutSide=on&_ActionID=DefaultAID&_ControlID=WPLETmgR001Control&_DataStoreID=DSWPLETmgR001Control&_PageID=WPLETmgR001Mdtl20&burl=iris_ranking&cat1=market&cat2=ranking&dir=tl1-rnk%7Ctl2-stock%7Ctl3-turnover%7Ctl4-high%7Ctl5-priceview%7Ctl7-T1&file=index.html&getFlg=on"
    // 売買代金上位(東証プライム)
    private val URL_SBI_SALESVAL_T1 = "https://www.sbisec.co.jp/ETGate/?OutSide=on&_ActionID=DefaultAID&_ControlID=WPLETmgR001Control&_DataStoreID=DSWPLETmgR001Control&_PageID=WPLETmgR001Mdtl20&burl=iris_ranking&cat1=market&cat2=ranking&dir=tl1-rnk%7Ctl2-stock%7Ctl3-salesval%7Ctl4-high%7Ctl5-priceview%7Ctl7-T1&file=index.html&getFlg=on"

    private fun nextSeq(): Long {
        seq += 1
        return seq
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        tabLayout = findViewById(R.id.tabLayout)
        tvLog = findViewById(R.id.tvLog)
        layoutLog = findViewById(R.id.layoutLog)
        lvRec = findViewById(R.id.lvRec)
        btnBack = findViewById(R.id.btnBack)
        btnRec = findViewById(R.id.btnRec)
        btnLog = findViewById(R.id.btnLog)
        btnYahoo = findViewById(R.id.btnYahoo)
        btnSbi = findViewById(R.id.btnSbi)
        btnExport = findViewById(R.id.btnExport)
        btnNeed = findViewById(R.id.btnNeed)
        btnNope = findViewById(R.id.btnNope)
        tvNavInfo = findViewById(R.id.tvNavInfo)
        logStore = LogStore(this, sid, appVersion)
        logStore.ensureRunFolders()
        display = Display(this, tvLog, tabLayout, logStore)

        // Crash log -> Download/Sbi/<sid>/log/crash.txt
        installCrashHandler()

        // Sref snapshot (replay seeds) - keep it simple and reproducible.
        // NOTE: do NOT hard-fix tracking/time params here (only keep in comments/log).
        try {
            val sb = StringBuilder()
            sb.append("v=").append(appVersion).append("\n")
            sb.append("sid=").append(sid).append("\n")
            sb.append("ts_ms=").append(System.currentTimeMillis()).append("\n")
            sb.append("\n# Yahoo\n")
            sb.append("URL_YAHOO_UP=").append(URL_YAHOO_UP).append("\n")
            sb.append("\n# SBI (OutSide=on / no-login)\n")
            sb.append("URL_SBI_UPRATE_T1=").append(URL_SBI_UPRATE_T1).append("\n")
            sb.append("URL_SBI_DOWNRATE_T1=").append(URL_SBI_DOWNRATE_T1).append("\n")
            sb.append("URL_SBI_TURNOVER_T1=").append(URL_SBI_TURNOVER_T1).append("\n")
            sb.append("URL_SBI_SALESVAL_T1=").append(URL_SBI_SALESVAL_T1).append("\n")
            sb.append("\n# NOTE\n")
            sb.append("- _gl/_ga 等のトラッキング系や日付時刻パラメータは固定化しない（ログに残すのみ）\n")
            logStore.writeText("log/sref.log", "text/plain", sb.toString())
        } catch (_: Throwable) {
        }

pickLogDirLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
    if (uri != null) {
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uri, flags)
        } catch (_: Throwable) {
        }
        logStore.setPublicTreeUri(uri)
        display.appendLog("LOG_DIR: set (SAF) -> $uri")
        Toast.makeText(this, "LOG_DIR set (SAF). Long-press EXP again to change.", Toast.LENGTH_LONG).show()
    } else {
        display.appendLog("LOG_DIR: canceled")
    }
}


        // tabs
        // TabLayout is hidden in v1.02-02 (buttons are used instead)
        tabLayout.visibility = View.GONE

        recAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        lvRec.adapter = recAdapter
        lvRec.setOnItemClickListener { _, _, position, _ ->
            val item = recItems.getOrNull(position) ?: return@setOnItemClickListener
            navigateByRec(item)
        }

        btnBack.setOnClickListener { doBack() }
        btnRec.setOnClickListener {
            showRecPanel()
            runRec()
        }
        // Long-press REC: start AUTO crawl (best-effort) from current page
        btnRec.setOnLongClickListener {
            startAutoFromCurrent()
            true
        }
        btnLog.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("LOG / PIN")
                .setItems(arrayOf("ログ表示(画面)", "ログ一覧", "PIN一覧")) { _, which ->
                    when (which) {
                        0 -> showLogPanel()
                        1 -> {
                            val it = Intent(this, LogViewerActivity::class.java)
                            it.putExtra(LogViewerActivity.EXTRA_SID, sid)
                            it.putExtra(LogViewerActivity.EXTRA_MODE, "logs")
                            startActivity(it)
                        }
                        2 -> {
                            val it = Intent(this, LogViewerActivity::class.java)
                            it.putExtra(LogViewerActivity.EXTRA_SID, sid)
                            it.putExtra(LogViewerActivity.EXTRA_MODE, "pins")
                            startActivity(it)
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        btnYahoo.setOnClickListener {
            display.appendLog("NAV: open Yahoo ranking")
            webView.loadUrl(URL_YAHOO_UP)
        }
        // Long-press Y!: quick snapshot (HTML+JSON)
        btnYahoo.setOnLongClickListener {
            captureCurrentPage("manual")
            true
        }

        btnSbi.setOnClickListener {
            display.appendLog("NAV: open SBI ranking (direct)")
            webView.loadUrl(URL_SBI_UPRATE_T1)
        }
        // Long-press S!: menu (ranking switch / auto)
        btnSbi.setOnLongClickListener {
            showSbiMenuWithAuto()
            true
        }

        btnExport.setOnClickListener {
            val r = logStore.exportAllToPublic()
            if (r.ok) {
                display.appendLog("EXPORT: OK -> ${r.publicDir}")
                Toast.makeText(this, "Export OK: ${r.publicDir}", Toast.LENGTH_SHORT).show()
            } else {
                display.appendLog("EXPORT: NG -> ${r.message}")
                Toast.makeText(this, "Export NG: ${r.message}", Toast.LENGTH_SHORT).show()
            }
        }

btnExport.setOnLongClickListener {
    // One-time setup: choose a folder (e.g. Download/Sbi) via SAF to make log export reliable on Android 13/14.
    try {
        Toast.makeText(this, "ログ出力フォルダを選択してください（例: Download/Sbi）", Toast.LENGTH_LONG).show()
    } catch (_: Throwable) {
    }
    pickLogDirLauncher.launch(null)
    true
}

        btnNeed.setOnClickListener { markNeed() }
        btnNope.setOnClickListener { markNopeAndBack() }

        setupWebView()

        display.appendLog("System Init: v$appVersion")
        display.appendLog("MODE: EXPLORE (no-login)")
        tvNavInfo.text = "EXPLORE / sid=$sid"
        display.appendLog("OUT: Download/Sbi/${logStore.getRunDirName()}/...")

        showRecPanel()

        // start: Yahoo ranking (public) as a safe first target
        // SBI without login often redirects; this still allows exploration flow.
        webView.loadUrl(URL_YAHOO_UP)

        updateDecisionButtons()
    }

    /** RECパネル（候補一覧）を表示 */
    private fun showRecPanel() {
        lvRec.visibility = View.VISIBLE
        layoutLog.visibility = View.GONE
        btnRec.isEnabled = false
        btnLog.isEnabled = true    }

    /** LOGパネル（ログ表示）を表示 */
    private fun showLogPanel() {
        lvRec.visibility = View.GONE
        layoutLog.visibility = View.VISIBLE
        btnRec.isEnabled = true
        btnLog.isEnabled = false    }

    /**
     * evaluateJavascript() の戻り値は "..." の文字列として返ることが多い。
     * JSONTokenerで一度デコードしてから JSONArray に渡す。
     */
    private fun decodeJsResultToJson(raw: String?): String {
        if (raw.isNullOrBlank() || raw == "null") return "[]"
        return try {
            val v = JSONTokener(raw).nextValue()
            when (v) {
                is String -> v
                else -> v?.toString() ?: "[]"
            }
        } catch (_: Throwable) {
            val t = raw.trim()
            if (t.length >= 2 && t.first() == '"' && t.last() == '"') {
                t.substring(1, t.length - 1)
                    .replace("\\\\", "\\")
                    .replace("\\\"", "\"")
            } else {
                t
            }
        }
    }

    /** 画面に常時出すヘルプ。迷子防止 */

    // ===== JS Bridge for capturing USER manual taps =====
    private inner class JsBridge {
        private var lastTs: Long = 0
        private var lastKey: String = ""

        @JavascriptInterface
        fun onUserClick(json: String?) {
            if (json.isNullOrBlank()) return
            val now = System.currentTimeMillis()
            if (now - lastTs < 300) return

            try {
                val o = JSONObject(json)
                val tag = o.optString("tag", "")
                val type = o.optString("type", "")
                val text = o.optString("text", "").trim()
                val href = o.optString("href", "").ifBlank { null }
                val xpath = o.optString("xpath", "")
                val frameCss = o.optString("frameCss", "").ifBlank { null }
                if (xpath.isBlank()) return

                val key = "$tag|$type|$text|${href ?: ""}|$xpath"
                if (key == lastKey && now - lastTs < 1200) return

                lastKey = key
                lastTs = now

                runOnUiThread {
                    val from = webView.url ?: ""
                    val (score, hits) = scoreKeywords(text + " " + (href ?: ""))
                    val rec = RecItem(
                        tag = if (type.isNotBlank()) "$tag($type)" else tag,
                        text = text.ifBlank { "(no-text)" },
                        href = href,
                        xpath = xpath,
                        frameCss = frameCss,
                        score = score,
                        hitKw = hits
                    )

                    // set pending trigger so "必要/不要" becomes available
                    lastFromUrl = from
                    lastTrigger = rec

                    val seqNow = nextSeq()
                    lastNavTrySeq = seqNow

                    appendJsonl(fileTry, JSONObject().apply {
                        put("sid", sid)
                        put("seq", seqNow)
                        put("ts_ms", System.currentTimeMillis())
                        put("type", "NAV_TAP") // manual tap
                        put("from_url", from)
                        put("trigger", JSONObject().apply {
                            put("tag", rec.tag)
                            put("text", rec.text)
                            put("href", rec.href ?: JSONObject.NULL)
                            put("xpath", rec.xpath)
                            put("frameCss", rec.frameCss ?: JSONObject.NULL)
                            put("score", rec.score)
                            put("kw", JSONArray(rec.hitKw))
                        })
                    })

                    // also record as a rec_candidate (manual tap)
                    appendJsonl(fileRecCandidates, JSONObject().apply {
                        put("sid", sid)
                        put("ts_ms", System.currentTimeMillis())
                        put("type", "REC_TAP")
                        put("url", from)
                        put("title", try { webView.title ?: "" } catch (_: Throwable) { "" })
                        put("tag", rec.tag)
                        put("text", rec.text)
                        put("href", rec.href ?: JSONObject.NULL)
                        put("xpath", rec.xpath)
                        put("frameCss", rec.frameCss ?: JSONObject.NULL)
                        put("score", rec.score)
                        put("kw", JSONArray(rec.hitKw))
                    })

                    display.appendLog("NAV_TAP: seq=$seqNow text=${rec.text} score=${rec.score}")
                    updateDecisionButtons()
                }
            } catch (_: Throwable) {
            }
        }
    }

private fun installCrashHandler() {
    try {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sb = StringBuilder()
                sb.append("v=").append(appVersion).append("\n")
                sb.append("sid=").append(sid).append("\n")
                sb.append("ts_ms=").append(System.currentTimeMillis()).append("\n")
                sb.append("thread=").append(t.name).append("\n")
                sb.append("\n")
                sb.append(android.util.Log.getStackTraceString(e))
                logStore.writeText("log/crash.txt", "text/plain", sb.toString())
            } catch (_: Throwable) {
            }
            try {
                prev?.uncaughtException(t, e)
            } catch (_: Throwable) {
            }
        }
    } catch (_: Throwable) {
    }
}


    private fun logRenderProcessGone(detail: RenderProcessGoneDetail?) {
        try {
            val sb = StringBuilder()
            sb.append("v=").append(appVersion).append("\n")
            sb.append("sid=").append(sid).append("\n")
            sb.append("ts_ms=").append(System.currentTimeMillis()).append("\n")
            sb.append("url=").append(webView.url ?: "").append("\n")
            sb.append("didCrash=").append(detail?.didCrash() ?: false).append("\n")
            sb.append("rendererPriorityAtExit=").append(detail?.rendererPriorityAtExit() ?: -1).append("\n")
            logStore.writeText("log/render_gone.txt", "text/plain", sb.toString())
        } catch (_: Throwable) {
        }
    }





    private fun setupWebView() {
        WebView.setWebContentsDebuggingEnabled(true)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.setSupportMultipleWindows(true)
        webView.settings.javaScriptCanOpenWindowsAutomatically = true
        webView.settings.userAgentString = webView.settings.userAgentString + " SbiWebBot/$appVersion"

        webView.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean {
                // SBIなどで target=_blank / window.open のポップアップが来るときに、
                // 親WebViewをそのまま渡すと端末によっては
                // IllegalArgumentException("Parent WebView cannot host its own popup window")
                // で落ちる。
                // ここではダミーWebViewでURLだけ捕まえて、親WebViewにロードする。
                return try {
                    val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false

                    val popupWebView = WebView(this@MainActivity)
                    popupWebView.settings.javaScriptEnabled = true
                    popupWebView.settings.domStorageEnabled = true

                    fun handlePopupUrl(u: String) {
                        if (u.isBlank() || u == "about:blank") return
                        val from = try { webView.url ?: "" } catch (_: Throwable) { "" }
                        display.appendLog("POPUP: $u")
                        appendJsonl(fileTry, JSONObject().apply {
                            put("sid", sid)
                            put("seq", nextSeq())
                            put("ts_ms", System.currentTimeMillis())
                            put("type", "POPUP")
                            put("from_url", from)
                            put("url", u)
                        })
                        try {
                            webView.post { webView.loadUrl(u) }
                        } catch (_: Throwable) {
                        }
                    }

                    popupWebView.webViewClient = object : android.webkit.WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            v: WebView?,
                            request: android.webkit.WebResourceRequest?
                        ): Boolean {
                            val u = try { request?.url?.toString() ?: "" } catch (_: Throwable) { "" }
                            handlePopupUrl(u)
                            try { v?.stopLoading() } catch (_: Throwable) {}
                            try { v?.destroy() } catch (_: Throwable) {}
                            return true
                        }

                        override fun shouldOverrideUrlLoading(v: WebView?, url: String?): Boolean {
                            val u = url ?: ""
                            handlePopupUrl(u)
                            try { v?.stopLoading() } catch (_: Throwable) {}
                            try { v?.destroy() } catch (_: Throwable) {}
                            return true
                        }

                        override fun onPageStarted(
                            v: WebView?,
                            url: String?,
                            favicon: android.graphics.Bitmap?
                        ) {
                            // about:blank → 実URL のケースを拾う
                            handlePopupUrl(url ?: "")
                            try { v?.stopLoading() } catch (_: Throwable) {}
                            try { v?.destroy() } catch (_: Throwable) {}
                        }
                    }

                    transport.webView = popupWebView
                    resultMsg.sendToTarget()
                    true
                } catch (t: Throwable) {
                    display.appendLog("Web: onCreateWindow error: ${t.javaClass.simpleName}")
                    false
                }
            }
        }

        // capture manual taps from WebView
        webView.addJavascriptInterface(JsBridge(), "AndroidBridge")

        webView.webViewClient = object : android.webkit.WebViewClient() {
override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
    val url = try { request?.url?.toString() ?: "" } catch (_: Throwable) { "" }
    if (url.isBlank()) return false
    return handleNonHttpScheme(url)
}

override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
    val u = url ?: return false
    return handleNonHttpScheme(u)
}

            
            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                // WebView renderer can die (OOM / WebView bug). Handle it to avoid app crash and keep logs.
                try {
                    display.appendLog("Web: RenderProcessGone didCrash=${detail?.didCrash() ?: false}")
                    logRenderProcessGone(detail)
                } catch (_: Throwable) {
                }
                try {
                    Toast.makeText(this@MainActivity, "WebViewが落ちました。再起動します。", Toast.LENGTH_SHORT).show()
                } catch (_: Throwable) {
                }
                try {
                    this@MainActivity.recreate()
                } catch (_: Throwable) {
                }
                return true
            }

override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                val u = url ?: return
                display.appendLog("Web: Page -> $u")
                // install click hook for manual taps
                webView.evaluateJavascript(WebScripts.installClickHookScript(), null)
                tvNavInfo.text = "EXPLORE\n$u"

                // After navigation completes, show decision buttons if we have a pending trigger
                updateDecisionButtons()

                // Auto-run REC (heavy pages may kill WebView; delay rescan only when first scan has 0 hits)
                lastRecHitCount = 0
                runRec()
                try {
                    val pageUrl = u
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        val cur = webView.url ?: ""
                        if (cur == pageUrl && lastRecHitCount == 0) {
                            runRec()
                        }
                    }, 900)
                } catch (_: Throwable) {
                }

                // AUTO crawl hook
                try {
                    autoOnPageFinished(u)
                } catch (_: Throwable) {
                }
}
        }
    }

    private fun updateDecisionButtons() {
        // Always enabled for "intuitive" operation.
        // If pending is none, markNeed/Nope will just show a message.
        btnNeed.isEnabled = true
        btnNope.isEnabled = true
    }

    private fun doBack() {
        if (webView.canGoBack()) {
            display.appendLog("NAV: back(goBack)")
            webView.goBack()
            return
        }
        val fallback = lastFromUrl
        if (!fallback.isNullOrBlank()) {
            display.appendLog("NAV: back(loadUrl) -> $fallback")
            webView.loadUrl(fallback)
        } else {
            display.appendLog("NAV: back(no history)")
        }
    }

    private fun runRec() {
        val script = WebScripts.inspectClickableScript()
        webView.evaluateJavascript(script) { json ->
            try {
                val arr = JSONArray(decodeJsResultToJson(json))
                val list = mutableListOf<RecItem>()
                val maxItems = 800
                val pageUrl = webView.url ?: ""
                val pageTitle = try { webView.title ?: "" } catch (_: Throwable) { "" }
                for (i in 0 until arr.length()) {
                    if (i >= maxItems) break
                    val o = arr.getJSONObject(i)
                    val tag = o.optString("tag", "")
                    val text = o.optString("text", "").trim()
                    val href = o.optString("href", "").ifBlank { null }
                    val xpath = o.optString("xpath", "")
                    val frameCss = o.optString("frameCss", "").ifBlank { null }
                    if (xpath.isBlank()) continue
                    val (score, hits) = scoreKeywords(text + " " + (href ?: ""))

                    
                    if (score <= 0) continue

                    appendJsonl(fileRecCandidates, JSONObject().apply {
                        put("sid", sid)
                        put("ts_ms", System.currentTimeMillis())
                        put("type", "REC_CAND")
                        put("url", pageUrl)
                        put("title", pageTitle)
                        put("tag", tag)
                        put("text", text)
                        put("href", href ?: JSONObject.NULL)
                        put("xpath", xpath)
                        put("frameCss", frameCss ?: JSONObject.NULL)
                        put("score", score)
                        put("kw", JSONArray(hits))
                    })
                    list.add(RecItem(tag, text, href, xpath, frameCss, score, hits))
                }

                // sort by score desc, then text length asc
                val sorted = list.sortedWith(compareByDescending<RecItem> { it.score }.thenBy { it.text.length }).take(60)
                recItems = sorted

                lastRecHitCount = sorted.size
                val lines = sorted.map { item ->
                    val kw = item.hitKw.joinToString(",")
                    val t = if (item.text.isNotBlank()) item.text else "(no-text)"
                    val h = item.href ?: ""
                    "[${item.score}] {$kw} $t  $h\n${item.xpath}"
                }

                runOnUiThread {
                    (recAdapter.clear())
                    recAdapter.addAll(lines)
                    recAdapter.notifyDataSetChanged()
                    display.appendLog("REC: candidates=${sorted.size}")                }
            } catch (e: Exception) {
                display.appendLog("REC: parse error: ${e.message}")
            }
        }
    }

    private fun scoreKeywords(s: String): Pair<Int, List<String>> {
        var score = 0
        val hits = mutableListOf<String>()
        for (k in keywords) {
            if (s.contains(k)) {
                score += 1
                hits.add(k)
            }
        }
        return Pair(score, hits)
    }

    private fun navigateByRec(item: RecItem) {
        val from = webView.url ?: ""
        lastFromUrl = from
        lastTrigger = item

        // log try
        val seqNow = nextSeq()
        lastNavTrySeq = seqNow
        appendJsonl(fileTry, JSONObject().apply {
            put("sid", sid)
            put("seq", seqNow)
            put("ts_ms", System.currentTimeMillis())
            put("type", "NAV_TRY")
            put("from_url", from)
            put("trigger", JSONObject().apply {
                put("tag", item.tag)
                put("text", item.text)
                put("href", item.href ?: JSONObject.NULL)
                put("xpath", item.xpath)
                put("score", item.score)
                put("kw", JSONArray(item.hitKw))
            })
        })

        display.appendLog("NAV_TRY: seq=$seqNow text=${item.text} score=${item.score}")

        // Prefer click by xpath (handles JS links). If it fails, fallback to loadUrl(href).
        val clickScript = WebScripts.clickByXpathScript(item.xpath, item.frameCss)
        webView.evaluateJavascript(clickScript) { res ->
            val ok = (res ?: "").contains("true", ignoreCase = true)
            if (!ok) {
                val h = item.href
                if (!h.isNullOrBlank()) {
                    val abs = toAbsoluteUrl(from, h)
                    display.appendLog("NAV_TRY: clickByXpath failed -> loadUrl $abs")
                    webView.post { webView.loadUrl(abs) }
                } else {
                    display.appendLog("NAV_TRY: clickByXpath failed and href is empty")
                }
            } else {
                display.appendLog("NAV_TRY: clickByXpath ok")
            }
        }

        updateDecisionButtons()
    }


private fun handleNonHttpScheme(url: String): Boolean {
    val u = url.trim()
    val lower = u.lowercase(Locale.getDefault())
    if (lower.startsWith("http://") || lower.startsWith("https://")) {
        return false
    }
    // Block dangerous / unsupported schemes; try to delegate safely.
    display.appendLog("Web: non-http scheme -> $u")
    try {
        if (lower.startsWith("intent:")) {
            val it = Intent.parseUri(u, Intent.URI_INTENT_SCHEME)
            try {
                startActivity(it)
            } catch (_: Throwable) {
                // fallback to browser url if present
                val fb = it.getStringExtra("browser_fallback_url")
                if (!fb.isNullOrBlank()) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fb)))
                }
            }
            return true
        }
        val it = Intent(Intent.ACTION_VIEW, Uri.parse(u))
        startActivity(it)
        return true
    } catch (t: Throwable) {
        display.appendLog("Web: scheme handle failed: ${t.javaClass.simpleName}")
        Toast.makeText(this, "リンクを開けません: ${t.javaClass.simpleName}", Toast.LENGTH_SHORT).show()
        return true
    }
}
    private fun toAbsoluteUrl(base: String, href: String): String {
        if (href.startsWith("http://") || href.startsWith("https://")) return href
        return try {
            val b = java.net.URI(base)
            b.resolve(href).toString()
        } catch (_: Exception) {
            href
        }
    }

    private fun markNeed() {
        val from = lastFromUrl
        val trig = lastTrigger
        if (from == null || trig == null) {
            display.appendLog("PIN_ADD: skipped (PENDING none)")
            Toast.makeText(this, "PENDING none: 先にリンクをタップ", Toast.LENGTH_SHORT).show()
            return
        }
        val to = webView.url ?: ""

        val pinId = "PIN_%04d".format(nextSeq().toInt())
        val o = JSONObject().apply {
            put("sid", sid)
            put("ts_ms", System.currentTimeMillis())
            put("type", "PIN_ADD")
            put("pin_id", pinId)
            put("from_url", from)
            put("to_url", to)
            put("selectors", JSONArray().apply {
                put(JSONObject().apply { put("type", "xpath"); put("v", trig.xpath); put("frameCss", trig.frameCss ?: JSONObject.NULL) })
                if (!trig.text.isBlank()) put(JSONObject().apply { put("type", "text"); put("v", trig.text) })
                if (!trig.href.isNullOrBlank()) put(JSONObject().apply { put("type", "href"); put("v", trig.href) })
            })
            put("trigger", JSONObject().apply {
                put("text", trig.text)
                put("score", trig.score)
                put("kw", JSONArray(trig.hitKw))
            })
        }
        appendJsonl(filePins, o)
        // human-confirmed xpath only
        try {
            logStore.appendLog(fileXpathLog, "${System.currentTimeMillis()}\t$pinId\t${trig.xpath}\n")
        } catch (_: Throwable) {
        }
        display.appendLog("PIN_ADD: $pinId")
        Toast.makeText(this, "PIN saved: $pinId", Toast.LENGTH_SHORT).show()

        // clear pending trigger
        lastFromUrl = null
        lastTrigger = null
        updateDecisionButtons()    }

    private fun markNopeAndBack() {
        val from = lastFromUrl
        val trig = lastTrigger
        if (from == null || trig == null) {
            display.appendLog("NAV_REJECT: skipped (PENDING none)")
            Toast.makeText(this, "PENDING none: 先にリンクをタップ", Toast.LENGTH_SHORT).show()
            return
        }
        val to = webView.url ?: ""

        val o = JSONObject().apply {
            put("sid", sid)
            put("seq", nextSeq())
            put("ts_ms", System.currentTimeMillis())
            put("type", "NAV_REJECT")
            put("from_url", from)
            put("to_url", to)
            put("trigger_xpath", trig.xpath)
            put("trigger_text", trig.text)
        }
        appendJsonl(fileTry, o)
        display.appendLog("NAV_REJECT: back")
        Toast.makeText(this, "Reject -> Back", Toast.LENGTH_SHORT).show()

        // clear pending trigger then back
        lastFromUrl = null
        lastTrigger = null
        updateDecisionButtons()
        doBack()
    }

    // ===== AUTO / SNAPSHOT =====

    private fun startAutoFromCurrent() {
        val url = webView.url ?: ""
        if (url.contains("sbisec.co.jp") && url.contains("cat2=ranking")) {
            startAutoSbiRanking()
            return
        }
        // fallback: just snapshot current
        captureCurrentPage("auto")
    }

    private fun startAutoSbiRanking() {
        display.appendLog("AUTO: start SBI ranking crawl")
        autoMode = AutoMode.SBI_RANKING
        autoQueue.clear()
        autoIndex = 0
        autoBusy = false
        autoCurrentCode = null

        val url = webView.url ?: ""
        if (!url.contains("sbisec.co.jp") || !url.contains("cat2=ranking")) {
            webView.loadUrl(URL_SBI_UPRATE_T1)
        }
    }

    private fun autoOnPageFinished(url: String) {
        if (autoMode == AutoMode.NONE) return
        if (autoBusy) return

        // always snapshot
        autoBusy = true
        captureCurrentPage("auto") {
            try {
                autoBusy = false
                if (autoMode != AutoMode.SBI_RANKING) return@captureCurrentPage

                // 1) if queue not built yet and we are on ranking page => build queue
                if (autoQueue.isEmpty() && url.contains("cat2=ranking")) {
                    val js = WebScripts.extractSbiStockLinksScript(limit = 8)
                    webView.evaluateJavascript(js) { raw ->
                        try {
                            val s = decodeJsResultToJson(raw)
                            val arr = JSONArray(s)
                            for (i in 0 until arr.length()) {
                                val o = arr.getJSONObject(i)
                                val u = o.optString("url", "")
                                val code = o.optString("code", "").ifBlank { null }
                                if (u.isBlank()) continue
                                autoQueue.add(AutoStep(url = u, pageType = "stock", code = code))
                            }
                            display.appendLog("AUTO: queue=${autoQueue.size}")
                            if (autoQueue.isNotEmpty()) {
                                autoIndex = 0
                                autoCurrentCode = autoQueue[0].code
                                webView.loadUrl(autoQueue[0].url)
                            } else {
                                display.appendLog("AUTO: no stock links found")
                            }
                        } catch (t: Throwable) {
                            display.appendLog("AUTO: extract failed: ${t.javaClass.simpleName}")
                        }
                    }
                    return@captureCurrentPage
                }

                // 2) if current looks like stock page => try PTS
                if (url.contains("stock_sec_code=")) {
                    val code = extractCodeFromUrl(url)
                    if (!code.isNullOrBlank()) autoCurrentCode = code
                    val jsPts = WebScripts.findPtsLinkScript()
                    webView.evaluateJavascript(jsPts) { raw ->
                        val s = decodeJsResultToJson(raw)
                        val ptsUrl = s.trim().trim('"')
                        if (ptsUrl.isNotBlank()) {
                            display.appendLog("AUTO: open PTS")
                            webView.loadUrl(ptsUrl)
                        } else {
                            autoGoNextStock()
                        }
                    }
                    return@captureCurrentPage
                }

                // 3) if current looks like PTS page => next
                if (url.contains("exchange_code=PTS") || url.contains("getInfoOfCurrentMarket")) {
                    autoGoNextStock()
                    return@captureCurrentPage
                }

                // otherwise: do nothing
            } catch (_: Throwable) {
            }
        }
    }

    private fun autoGoNextStock() {
        autoIndex += 1
        if (autoIndex >= autoQueue.size || autoIndex >= autoMaxSteps) {
            display.appendLog("AUTO: done (steps=${autoIndex}/${autoQueue.size})")
            autoMode = AutoMode.NONE
            autoQueue.clear()
            autoIndex = 0
            autoCurrentCode = null
            return
        }
        val step = autoQueue[autoIndex]
        autoCurrentCode = step.code
        display.appendLog("AUTO: next ${autoIndex + 1}/${autoQueue.size} code=${step.code ?: ""}")
        webView.loadUrl(step.url)
    }

    private fun extractCodeFromUrl(url: String): String? {
        return try {
            val m = Regex("stock_sec_code=([0-9]{4})").find(url)
            m?.groupValues?.getOrNull(1)
        } catch (_: Throwable) {
            null
        }
    }

    private fun sha1Hex(s: String): String {
        return try {
            val md = MessageDigest.getInstance("SHA-1")
            val b = md.digest(s.toByteArray(Charsets.UTF_8))
            val sb = StringBuilder()
            for (x in b) sb.append(String.format("%02x", x))
            sb.toString()
        } catch (_: Throwable) {
            ""
        }
    }

    private fun safeName(s: String): String {
        return s.replace("\\", "_")
            .replace("/", "_")
            .replace(":", "_")
            .replace("?", "_")
            .replace("&", "_")
            .replace("=", "_")
            .replace("%", "_")
            .replace("#", "_")
    }

    private fun detectPageType(url: String): String {
        val u = url.lowercase(Locale.getDefault())
        if (u.contains("exchange_code=pts") || u.contains("getinfoofcurrentmarket")) return "pts"
        if (u.contains("cat2=ranking")) return "ranking"
        if (u.contains("stock_sec_code=")) return "stock"
        return "page"
    }

    private fun detectSource(url: String): String {
        val u = url.lowercase(Locale.getDefault())
        if (u.contains("finance.yahoo.co.jp")) return "yahoo"
        if (u.contains("sbisec.co.jp")) return "sbi"
        return "other"
    }

    private fun captureCurrentPage(tag: String, onDone: (() -> Unit)? = null) {
        val url = webView.url ?: ""
        val title = try { webView.title ?: "" } catch (_: Throwable) { "" }
        val pageType = detectPageType(url)
        val source = detectSource(url)
        val code = extractCodeFromUrl(url) ?: autoCurrentCode
        val ts = System.currentTimeMillis()

        webView.evaluateJavascript(WebScripts.outerHtmlScript()) { rawHtml ->
            val html = decodeJsResultToJson(rawHtml)
            webView.evaluateJavascript(WebScripts.bodyTextScript()) { rawText ->
                val text = decodeJsResultToJson(rawText)

                val htmlHash = sha1Hex(html)
                val snippet = if (text.length > 1200) text.substring(0, 1200) else text

                val base = buildString {
                    append(tag)
                    append("-")
                    append(pageType)
                    if (!code.isNullOrBlank()) {
                        append("-")
                        append(code)
                    }
                    append("-")
                    append(SimpleDateFormat("HHmmss", Locale.getDefault()).format(Date()))
                }
                val name = safeName(base)

                try {
                    logStore.writeText("html/$name.html", "text/html", html)
                } catch (_: Throwable) {
                }

                try {
                    val jo = JSONObject().apply {
                        put("sid", sid)
                        put("ts_ms", ts)
                        put("source", source)
                        put("pageType", pageType)
                        put("url", url)
                        put("title", title)
                        put("code", code ?: JSONObject.NULL)
                        put("htmlSha1", htmlHash)
                        put("textSnippet", snippet)
                    }
                    logStore.writeText("json/$name.json", "application/json", jo.toString(2))
                    display.appendLog("SNAP: $name ($pageType)")
                } catch (_: Throwable) {
                }
                onDone?.invoke()
            }
        }
    }

    private fun showSbiMenuWithAuto() {
        val items = arrayOf(
            "AUTO開始 (このランキングから)",
            "値上がり率 (SBI)",
            "値下がり率 (SBI)",
            "出来高上位 (SBI)",
            "売買代金上位 (SBI)"
        )
        AlertDialog.Builder(this)
            .setTitle("SBI")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> startAutoSbiRanking()
                    1 -> webView.loadUrl(URL_SBI_UPRATE_T1)
                    2 -> webView.loadUrl(URL_SBI_DOWNRATE_T1)
                    3 -> webView.loadUrl(URL_SBI_TURNOVER_T1)
                    4 -> webView.loadUrl(URL_SBI_SALESVAL_T1)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun appendJsonl(fileName: String, obj: JSONObject) {
        try {
			// NOTE: keep this as an escaped newline. Do NOT put a raw line break inside quotes.
			val line = obj.toString() + "\n"
            // internal + public (Download/Sbi/<sid>/...)
            logStore.appendJsonl(fileName, line)
        } catch (e: Exception) {
            display.appendLog("FILE: write failed $fileName : ${e.message}")
        }
    }

    override fun onBackPressed() {
        // hardware back == web back
        doBack()
    }
}
