//app/src/main/java/com/papa/sbiwebbot/MainActivity.kt
//ver 1.02-09
package com.papa.sbiwebbot

import android.content.Intent
import android.os.Bundle
import android.net.Uri
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.JavascriptInterface
import android.webkit.WebView
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
    private lateinit var tvHelp: TextView

    private lateinit var display: Display
    private lateinit var logStore: LogStore

    private lateinit var pickLogDirLauncher: ActivityResultLauncher<Uri?>


    // ==== Explore state ====
    private val appVersion = "1.02-09"
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
        val score: Int,
        val hitKw: List<String>
    )

    private var recItems: List<RecItem> = emptyList()
    private lateinit var recAdapter: ArrayAdapter<String>

    // navigation tracking
    private var lastFromUrl: String? = null
    private var lastTrigger: RecItem? = null
    private var lastNavTrySeq: Long = -1

    // Files
    private val fileTry = "nav_try.jsonl"
    private val filePins = "pins.jsonl"

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
        tvHelp = findViewById(R.id.tvHelp)

        logStore = LogStore(this, sid, appVersion)
        display = Display(this, tvLog, tabLayout, logStore)

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
        btnLog.setOnClickListener {
            showLogPanel()
        }
        btnLog.setOnLongClickListener {
            startActivity(Intent(this, LogViewerActivity::class.java))
            true
        }

        btnYahoo.setOnClickListener {
            display.appendLog("NAV: open Yahoo ranking")
            webView.loadUrl(URL_YAHOO_UP)
        }

        btnSbi.setOnClickListener {
            showSbiMenu()
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

        tvHelp.text = buildHelpText()
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
        btnLog.isEnabled = true
        tvHelp.text = buildHelpText()
    }

    /** LOGパネル（ログ表示）を表示 */
    private fun showLogPanel() {
        lvRec.visibility = View.GONE
        layoutLog.visibility = View.VISIBLE
        btnRec.isEnabled = true
        btnLog.isEnabled = false
        tvHelp.text = buildHelpText()
    }

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
    private fun buildHelpText(): String {
        val url = webView.url ?: "(no url)"
        val mode = "EXPLORE(no-login)"
        val panel = if (lvRec.visibility == View.VISIBLE) "REC" else "LOG"
        val cand = recItems.size
        val hasPending = (lastTrigger != null && lastFromUrl != null)
        val pendingText = if (hasPending) {
            val t = lastTrigger?.text ?: ""
            val s = lastTrigger?.score ?: 0
            "PENDING: score=$s $t"
        } else {
            "PENDING: none (候補をタップ or 手動タップ→遷移後に必要/不要)"
        }

        val p = logStore.getPublicDirHint()
        val oplogName = "oplog_${sid}.txt"
        val tryName = "nav_try_${sid}.jsonl"
        val pinsName = "pins_${sid}.jsonl"

        return buildString {
            append("v$appVersion  $mode  panel=$panel\n")
            append("操作: 1) REC→候補  2) 候補タップ or 手動タップで遷移\n")
            append("      3) 遷移後『必要』でPIN保存 / 『不要』で戻る  4) BACK\n")
            append("候補数=$cand   $pendingText\n")
            append("ログ保存先: $p\n")
            append("  $oplogName\n  $tryName\n  $pinsName\n")
            val pubErr = logStore.getLastPublicError()
            if (pubErr.isNotBlank()) {
                append("PUBLIC_LOG_ERR: $pubErr\n")
                append("→ EXP を押してExportを試す\n")
            }
            append("URL: $url")
        }
    }


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
                            put("score", rec.score)
                            put("kw", JSONArray(rec.hitKw))
                        })
                    })

                    display.appendLog("NAV_TAP: seq=$seqNow text=${rec.text} score=${rec.score}")
                    updateDecisionButtons()
                }
            } catch (_: Throwable) {
            }
        }
    }


    private fun setupWebView() {
        WebView.setWebContentsDebuggingEnabled(true)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.userAgentString = webView.settings.userAgentString + " SbiWebBot/$appVersion"

        webView.webChromeClient = WebChromeClient()

        // capture manual taps from WebView
        webView.addJavascriptInterface(JsBridge(), "AndroidBridge")

        webView.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                val u = url ?: return
                display.appendLog("Web: Page -> $u")
                // install click hook for manual taps
                webView.evaluateJavascript(WebScripts.installClickHookScript(), null)
                tvNavInfo.text = "EXPLORE\n$u"

                // After navigation completes, show decision buttons if we have a pending trigger
                updateDecisionButtons()

                // Auto-run REC for convenience
                // (If noisy, user can tap REC manually)
                runRec()
            }
        }
    }

    private fun updateDecisionButtons() {
        val hasTrigger = lastTrigger != null && lastFromUrl != null
        // Always enabled for "intuitive" operation.
        // If pending is none, markNeed/Nope will just show a message.
        btnNeed.isEnabled = true
        btnNope.isEnabled = true
        tvHelp.text = buildHelpText()
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
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val tag = o.optString("tag", "")
                    val text = o.optString("text", "").trim()
                    val href = o.optString("href", "").ifBlank { null }
                    val xpath = o.optString("xpath", "")
                    if (xpath.isBlank()) continue
                    val (score, hits) = scoreKeywords(text + " " + (href ?: ""))
                    if (score <= 0) continue

                    list.add(RecItem(tag, text, href, xpath, score, hits))
                }

                // sort by score desc, then text length asc
                val sorted = list.sortedWith(compareByDescending<RecItem> { it.score }.thenBy { it.text.length }).take(60)
                recItems = sorted

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
                    display.appendLog("REC: candidates=${sorted.size}")
                    tvHelp.text = buildHelpText()
                }
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
        val clickScript = WebScripts.clickByXpathScript(item.xpath)
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
                put(JSONObject().apply { put("type", "xpath"); put("v", trig.xpath) })
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
        display.appendLog("PIN_ADD: $pinId")
        Toast.makeText(this, "PIN saved: $pinId", Toast.LENGTH_SHORT).show()

        // clear pending trigger
        lastFromUrl = null
        lastTrigger = null
        updateDecisionButtons()
        tvHelp.text = buildHelpText()
    }

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
        tvHelp.text = buildHelpText()
        doBack()
    }

    private fun showSbiMenu() {
        val items = arrayOf(
            "値上がり率 (SBI)",
            "値下がり率 (SBI)",
            "出来高上位 (SBI)",
            "売買代金上位 (SBI)"
        )
        AlertDialog.Builder(this)
            .setTitle("SBIランキング")
            .setItems(items) { _, which ->
                val url = when (which) {
                    0 -> URL_SBI_UPRATE_T1
                    1 -> URL_SBI_DOWNRATE_T1
                    2 -> URL_SBI_TURNOVER_T1
                    3 -> URL_SBI_SALESVAL_T1
                    else -> URL_SBI_UPRATE_T1
                }
                display.appendLog("NAV: open SBI ranking idx=$which")
                webView.loadUrl(url)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun appendJsonl(fileName: String, obj: JSONObject) {
        try {
			// NOTE: keep this as an escaped newline. Do NOT put a raw line break inside quotes.
			val line = obj.toString() + "\\n"
// internal + public (Download/Sbi/)
            val key = fileName
                .removeSuffix(".jsonl")
                .removeSuffix(".txt")
                .replace("/", "_")
            logStore.appendJsonl(key, line)
        } catch (e: Exception) {
            display.appendLog("FILE: write failed $fileName : ${e.message}")
        }
    }

    override fun onBackPressed() {
        // hardware back == web back
        doBack()
    }
}
