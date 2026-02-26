//app/src/main/java/com/papa/sbiwebbot/MainActivity.kt
//ver 1.00-50
package com.papa.sbiwebbot

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.content.Context
import android.view.View
import android.view.MotionEvent
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.Guideline
import com.google.android.material.tabs.TabLayout
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private lateinit var display: Display
    private lateinit var config: Config
    private lateinit var webMain: Web
    private lateinit var webAuth: Web
    private lateinit var mail: Mail
    private lateinit var cookieStore: CookieStore

    private var btnWebMain: Button? = null
    private var btnWebAuth: Button? = null
    private var authTabVisible: Boolean = false

    // 認証コード候補（6桁）: すべて表示してユーザーが選択
    private val authCodeCandidates = mutableListOf<String>()
    private var selectedAuthCode: String? = null

    private lateinit var etConfig: EditText
    private lateinit var lvInspect: ListView
    private lateinit var lvMail: ListView
    private lateinit var tabLayout: TabLayout

    // Split UI
    private lateinit var guideSplit: Guideline
    private lateinit var splitHandle: View
    private lateinit var panel: View
    private var panelCollapsed: Boolean = false
    private var splitPercent: Float = 0.58f
    private lateinit var seekSplit: SeekBar

    private val inspectedElements = mutableListOf<HtmlElement>()
    private val mailItems = mutableListOf<EmailItem>()

    private val handler = Handler(Looper.getMainLooper())

    private var currentTabIndex: Int = 0
    private lateinit var tabPages: List<View>

    private var webLoading: Boolean = false
    private var mailLoading: Boolean = false

    private var autoRunning: Boolean = false
    private var lastAuthCode: String? = null
    private var mailPollGen: Int = 0
    private var pendingDeviceAuthUrl: String? = null

    private var sbiMailPolling: Boolean = false
    private var sbiMailMinSentMs: Long = 0L
    private var sbiMailDeadlineMs: Long = 0L

    // OTP送信ボタン連打防止
    // OTPメール送信クリックが「成功した時刻」を基準に、メールポーリングを開始する
    private var otpClickInFlight: Boolean = false
    private var otpClickMs: Long = 0L
    private var otpClickOk: Boolean = false

    // autoLogin連打防止（遅い/エラー対策）
    private var lastAutoLoginAt: Long = 0L
    private var lastAutoLoginUrl: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Split UI (draggable divider)
        guideSplit = findViewById(R.id.guideSplit)
        splitHandle = findViewById(R.id.splitHandle)
        panel = findViewById(R.id.panel)
        seekSplit = findViewById(R.id.seekSplit)
        setupSplitUi()

        tabLayout = findViewById(R.id.tabLayout)
        display = Display(this, findViewById(R.id.tvLog), tabLayout)

        config = Config(this)
        mail = Mail()
        // WebViews (MAIN / AUTH)
        val wvMain: android.webkit.WebView = findViewById(R.id.webViewMain)
        val wvAuth: android.webkit.WebView = findViewById(R.id.webViewAuth)

        webMain = Web(wvMain, display)
        webAuth = Web(wvAuth, display)
        cookieStore = CookieStore(this)

        // Cookie復元（アプリ更新後も引き継ぐ）
        cookieStore.restore()

        etConfig = findViewById(R.id.etConfig)
        lvInspect = findViewById(R.id.lvInspect)
        lvMail = findViewById(R.id.lvMail)

        setupTabs(tabLayout)
        setupButtons()

        webMain.setCallback(object : Web.WebCallback {

            override fun onWebLoading(isLoading: Boolean, url: String?) {
                runOnUiThread {
                    webLoading = isLoading
                    updateTabVisibility()
                    if (!isLoading) {
                        // Cookie自動保存（SBIドメインのみ）
                        if (isSbiDomain(url)) {
                            cookieStore.maybeSaveDebounced()
                        }

                        // 勝手に外部サイトへ飛ぶ事故対策: AUTO中にSBI以外へ遷移したら停止してTOPへ戻す
                        if (autoRunning && !isSbiDomain(url)) {
                            display.appendLog("AUTO: blocked external navigation -> back to SBI TOP")
                            stopAuto("external navigation blocked")
                            webMain.loadUrl("https://www.sbisec.co.jp/ETGate/")
                            return@runOnUiThread
                        }
                        maybeStartAutoPatrol()
                    }
                }
            }

            override fun onElementsInspected(list: List<HtmlElement>) {
                runOnUiThread {
                    // 要望: RECは「認証」関連だけ表示（他の文字が多すぎるため）
                    val filtered = list.filter { it.text.contains("認証") }
                    inspectedElements.clear()
                    inspectedElements.addAll(filtered)
                    lvInspect.adapter = ArrayAdapter(
                        this@MainActivity,
                        android.R.layout.simple_list_item_1,
                        filtered.map { "[${it.tag}] ${it.text}" }
                    )
                }
            }

            override fun onAuthDetected(code: String) {
                runOnUiThread {
                    lastAuthCode = code
                    display.appendLog("AUTH(ONSCREEN): $code")
                }
            }

            override fun onRankingData(json: String) {
                runOnUiThread { display.appendLog("RANKING_JSON: $json") }
            }

            override fun onClickResult(label: String, ok: Boolean) {
                runOnUiThread {
                    // OTP送信のクリック結果だけを状態機械に反映
                    if (label == "otp_send_email") {
                        otpClickInFlight = false
                        if (ok) {
                            otpClickOk = true
                            // クリック成功時刻を基準に、5秒待ってからポーリング開始（startSbiMailPolling内で5秒待つ）
                            display.appendLog("AUTO: otp send click confirmed")
                            if (!sbiMailPolling) startSbiMailPolling(otpClickMs)
                        } else {
                            display.appendLog("AUTO: otp send click failed (will retry)")
                        }
                    }
                }
            }
        })

        // deviceAuth(メールURL)は別WebViewで開く。REC/タップはこのWebViewが対象。
        webAuth.setCallback(object : Web.WebCallback {
            override fun onWebLoading(isLoading: Boolean, url: String?) {
                runOnUiThread {
                    if (authTabVisible) {
                        webLoading = isLoading
                        updateTabVisibility()
                    }
                }
            }

            override fun onElementsInspected(list: List<HtmlElement>) {
                runOnUiThread {
                    val filtered = list.filter { it.text.contains("認証") }
                    inspectedElements.clear()
                    inspectedElements.addAll(filtered)
                    lvInspect.adapter = ArrayAdapter(
                        this@MainActivity,
                        android.R.layout.simple_list_item_1,
                        filtered.map { "[${it.tag}] ${it.text}" }
                    )
                }
            }

            override fun onAuthDetected(code: String) {
                // no-op
            }

            override fun onRankingData(json: String) {
                // no-op
            }

            override fun onClickResult(label: String, ok: Boolean) {
                // no-op
            }
        })

        lvInspect.setOnItemClickListener { _, _, p, _ ->
            if (webLoading) return@setOnItemClickListener
            val el = inspectedElements[p]

            val activeWeb = if (authTabVisible) webAuth else webMain

            // 要望: RECポップアップにテンキー + BACK。User/Pass自動入力は廃止。
            display.showRecPopup(
                el = el,
                authCodes = authCodeCandidates,
                initialText = "",
                onTap = {
                    activeWeb.executeAction(el.xpath, "click")
                    display.appendLog("CLICK -> ${el.xpath}")
                },
                onInput = { v ->
                    // 6桁なら「認証コードとして選択」扱い
                    val vv = v.trim()
                    if (Regex("""^\\d{6}$""").matches(vv)) {
                        selectedAuthCode = vv
                        if (!authCodeCandidates.contains(vv)) authCodeCandidates.add(0, vv)
                        display.appendLog("AUTH(SELECTED): $vv")
                    }
                    activeWeb.executeAction(el.xpath, "input", vv)
                },
                onBack = {
                    activeWeb.goBack()
                    display.appendLog("WEB: back")
                }
            )
        }

        lvMail.setOnItemClickListener { _, _, p, _ ->
            if (mailLoading) return@setOnItemClickListener
            val item = mailItems[p]
            val urls = mail.extractUrls(item.body)
            display.showMailOption(item, urls) { url ->
                if (url.contains("m.sbisec.co.jp/deviceAuthentication", ignoreCase = true)) {
                    openAuthUrl(url)
                } else {
                    webMain.loadUrl(url)
                    showAuthTab(false)
                }
            }
        }

        // 起動時: config自動ロード
        val saved = config.loadDecrypted()
        if (!saved.isNullOrBlank()) {
            etConfig.setText(saved)
        }

        // 初期表示LOG
        currentTabIndex = 0
        tabLayout.getTabAt(0)?.select()
        updateTabVisibility()

        // 起動時: TOPアクセス
        webMain.loadUrl("https://www.sbisec.co.jp/ETGate/")
        display.appendLog("System Init: v${display.getVersion()}")

        // config読み込み後: メール自動取得
        if (!etConfig.text.isNullOrBlank()) {
            fetchMail(isAuto = true)
        }
    }

    override fun onPause() {
        super.onPause()
        // Cookie自動保存（configのように）
        cookieStore.saveNow()
    }

    private fun setupTabs(tl: TabLayout) {
        tabPages = listOf(
            findViewById<View>(R.id.layoutLog),
            findViewById<View>(R.id.lvMail),
            findViewById<View>(R.id.lvInspect),
            findViewById<View>(R.id.layoutConfig)
        )

        tl.addTab(tl.newTab().setText("LOG (v${display.getVersion()})"))
        tl.addTab(tl.newTab().setText("MAIL"))
        tl.addTab(tl.newTab().setText("REC"))
        tl.addTab(tl.newTab().setText("CONFIG"))

        tl.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val pos = tab?.position ?: 0
                currentTabIndex = pos
                updateTabVisibility()
                // 仕様: 自動メール取得中は、MAILタップでの手動取得は動作しない
                if (pos == 1 && !sbiMailPolling) fetchMail(isAuto = false)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}

            override fun onTabReselected(tab: TabLayout.Tab?) {
                if (tab?.position == 1 && !sbiMailPolling) fetchMail(isAuto = false)
            }
        })
    }

    private fun updateTabVisibility() {
        // MAIL/RECの重なり防止: 選択中だけVISIBLE、他はGONE
        tabPages.forEachIndexed { idx, v ->
            v.visibility = if (idx == currentTabIndex) View.VISIBLE else View.GONE
        }

        // 要件: タブ点滅中(=処理中)は中身を非表示(INVISIBLE)
        if (currentTabIndex == 1) {
            lvMail.visibility = if (mailLoading) View.INVISIBLE else View.VISIBLE
            lvMail.isEnabled = !mailLoading
        }
        if (currentTabIndex == 2) {
            lvInspect.visibility = if (webLoading) View.INVISIBLE else View.VISIBLE
            lvInspect.isEnabled = !webLoading
        }
    }

    private fun fetchMail(isAuto: Boolean) {
        if (!isAuto && sbiMailPolling) {
            display.appendLog("MAIL: manual fetch ignored (auto polling)")
            return
        }
        display.appendLog("MAIL: fetch start")
        mailLoading = true
        updateTabVisibility()

        // MAIL: 取得中（薄い青 点滅）
        display.setTabState(1, true, "#CCEEFF")

        mail.fetchLatest(etConfig.text.toString()) { res ->
            runOnUiThread {
                res.onSuccess { list ->
                    mailItems.clear()
                    mailItems.addAll(list)
                    lvMail.adapter = ArrayAdapter(
                        this@MainActivity,
                        android.R.layout.simple_list_item_1,
                        list.map { it.subject }
                    )

                    display.appendLog("MAIL: fetched ${list.size}")

                    // MAILから「6桁」候補を全部集める（スパム混在でも、候補は全表示してユーザーが選ぶ）
                    authCodeCandidates.clear()
                    list.forEach { item ->
                        val fromOk = item.from.contains("info@sbisec.co.jp", ignoreCase = true)
                        if (!fromOk) return@forEach

                        Regex("""(?<![0-9A-Za-z_])\d{6}(?![0-9A-Za-z_])""")
                            .findAll(item.body)
                            .map { it.value }
                            .distinct()
                            .forEach { code ->
                                if (!authCodeCandidates.contains(code)) {
                                    authCodeCandidates.add(code)
                                    display.appendLog("AUTH(MAIL-CAND): $code")
                                }
                            }

                        // URLログ（安全ドメインのみ）
                        val urls = mail.extractUrls(item.body)
                        val safe = urls.firstOrNull {
                            it.contains("sbisec.co.jp", ignoreCase = true) ||
                                it.contains("sbisec.akamaized.net", ignoreCase = true)
                        }
                        if (!safe.isNullOrBlank()) display.appendLog("MAIL: urlHit=" + safe)
                    }

                    mailLoading = false
                    updateTabVisibility()

                    // MAIL: 完了（薄い緑 点灯）
                    display.setTabState(1, false, "#CCFFCC")

                    if (isAuto) {
                        maybeStartAutoPatrol()
                    }

                    if (autoRunning || sbiMailPolling) {
                        handleMailForAuto(list)
                    }
                }

                res.onFailure { e ->
                    display.appendLog("MAIL: fetch fail: ${e.message}")
                    mailLoading = false
                    updateTabVisibility()
                    display.setTabState(1, false, "#FFCCCC")
                }
            }
        }
    }


    private fun setupSplitUi() {
        val prefs = getSharedPreferences("ui", Context.MODE_PRIVATE)
        // 要望: 下パネルをもっと下まで下げたい/小さくしたい
        splitPercent = prefs.getFloat("splitPercent", 0.58f).coerceIn(0.15f, 0.95f)
        panelCollapsed = prefs.getBoolean("panelCollapsed", false)

        fun apply() {
            // collapsed時はほぼ最下段まで（下パネルを最小化）
            val p = if (panelCollapsed) 0.98f else splitPercent
            guideSplit.setGuidelinePercent(p.coerceIn(0.15f, 0.98f))

            // Slider sync (0..100 -> 0.15..0.95)
            val prog = (((splitPercent - 0.15f) / (0.95f - 0.15f)) * 100f).toInt().coerceIn(0, 100)
            if (!seekSplit.isPressed) {
                seekSplit.progress = prog
            }
        }
        apply()

        // Slider to resize
        seekSplit.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val percent = 0.15f + (progress / 100f) * (0.95f - 0.15f)
                splitPercent = percent.coerceIn(0.15f, 0.95f)
                panelCollapsed = false
                apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                prefs.edit()
                    .putFloat("splitPercent", splitPercent)
                    .putBoolean("panelCollapsed", panelCollapsed)
                    .apply()
            }
        })

        fun attachDrag(target: View) {
            target.setOnTouchListener { v, ev ->
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val root = findViewById<View>(R.id.root)
                        val loc = IntArray(2)
                        root.getLocationOnScreen(loc)
                        val rootTop = loc[1]
                        val h = root.height.takeIf { it > 0 } ?: return@setOnTouchListener true
                        val y = (ev.rawY - rootTop).coerceIn(0f, h.toFloat())

                        // 仕様: 上:WebView / 下:パネル の比率を変更
                        val percent = (y / h.toFloat()).coerceIn(0.15f, 0.95f)
                        splitPercent = percent
                        panelCollapsed = false
                        apply()
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        prefs.edit()
                            .putFloat("splitPercent", splitPercent)
                            .putBoolean("panelCollapsed", panelCollapsed)
                            .apply()
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                        true
                    }
                    else -> false
                }
            }
        }

        // Drag handle to resize
        attachDrag(splitHandle)

        // 要望: rec/mailタブ付近からも上下に動かしたい（ハンドルまで指が届かないことがある）
        attachDrag(findViewById(R.id.tabLayout))

        // PANEL button toggles collapse/expand
        findViewById<Button>(R.id.btnPanel).setOnClickListener {
            panelCollapsed = !panelCollapsed
            apply()
            prefs.edit().putBoolean("panelCollapsed", panelCollapsed).apply()
        }
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btnBack).setOnClickListener {
            if (authTabVisible) webAuth.goBack() else webMain.goBack()
        }

        val idBtnWebMain = resources.getIdentifier("btnWebMain", "id", packageName)
        btnWebMain = if (idBtnWebMain != 0) findViewById(idBtnWebMain) else null
        val idBtnWebAuth = resources.getIdentifier("btnWebAuth", "id", packageName)
        btnWebAuth = if (idBtnWebAuth != 0) findViewById(idBtnWebAuth) else null

        btnWebMain?.setOnClickListener { showAuthTab(false) }
        btnWebAuth?.setOnClickListener { showAuthTab(true) }

        findViewById<Button>(R.id.btnSaveConfig).setOnClickListener {
            config.saveEncrypted(etConfig.text.toString())
        }
        findViewById<Button>(R.id.btnLoadConfig).setOnClickListener {
            etConfig.setText(config.loadDecrypted() ?: "")
            if (!etConfig.text.isNullOrBlank()) {
                fetchMail(isAuto = true)
            }
        }
    }

    private fun showAuthTab(show: Boolean) {
        val wMain = findViewById<View>(R.id.webViewMain)
        val wAuth = findViewById<View>(R.id.webViewAuth)
        authTabVisible = show
        wAuth.visibility = if (show) View.VISIBLE else View.GONE
        wMain.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun openAuthUrl(url: String) {
        display.appendLog("AUTH: open in AUTH tab")
        // AUTH画面表示中はAUTOを止める（勝手に巡回しない）
        autoRunning = false
        webMain.setAutoRunning(false)
        showAuthTab(true)
        webAuth.loadUrl(url)
    }

    private fun maybeStartAutoPatrol() {
        if (autoRunning) return
        if (webLoading) return
        if (mailLoading) return
        if (etConfig.text.isNullOrBlank()) return

        autoRunning = true
        webMain.setAutoRunning(true)
        pendingDeviceAuthUrl = null
        otpClickInFlight = false
        otpClickOk = false
        otpClickMs = 0L
        display.appendLog("AUTO: start")
        handler.post { autoTick() }
    }

    private fun autoTick() {
        if (!autoRunning) return

        if (webLoading || mailLoading) {
            handler.postDelayed({ autoTick() }, 700)
            return
        }

        val url = webMain.getCurrentUrl() ?: ""

        when {
            url.contains("/login/entry") || url.contains("login.sbisec.co.jp/login/") || url.contains("/idpw/auth") -> {
                // 連打防止（同一URLで短時間に複数回呼ぶと、入力が遅くなったりエラーになることがある）
                val now = System.currentTimeMillis()
                if (url == lastAutoLoginUrl && (now - lastAutoLoginAt) < 2500) {
                    // skip
                } else {
                    lastAutoLoginUrl = url
                    lastAutoLoginAt = now
                    display.appendLog("AUTO: autoLogin")
                    webMain.autoLogin(etConfig.text.toString())
                }
            }

            // OTP送信は /otp/entry で「1回だけ」
            url.contains("/otp/entry") -> {
                // ここでは「クリック試行」だけ。クリック成功が返ってきたら onClickResult でポーリング開始。
                if (!otpClickOk && !otpClickInFlight) {
                    val now = System.currentTimeMillis()
                    display.appendLog("AUTO: send OTP email")
                    otpClickInFlight = true
                    otpClickMs = now
                    webMain.sendOtpEmail()
                }
            }

            // confirmに入ったら連打しない。必要ならメール待ちポーリングだけ維持/再開。
            url.contains("/otp/confirm") -> {
                // confirmに入ったら、クリック成功済みならポーリングだけ開始/維持
                if (otpClickOk && !sbiMailPolling) {
                    display.appendLog("AUTO: otp confirm -> start mail polling")
                    startSbiMailPolling(otpClickMs)
                }
            }

            url.contains("deviceAuthentication", ignoreCase = true) -> {
                val code = selectedAuthCode
                if (!code.isNullOrBlank()) {
                    display.appendLog("AUTO: submit device auth code")
                    webMain.submitDeviceAuthCode(code)
                    stopAuto("device-auth submitted")
                } else {
                    display.appendLog("AUTO: auth code not selected (open AUTH tab)")
                    // 自動では進めない。ユーザーが候補から選択して入力する。
                    stopAuto("device-auth waiting selection")
                }
                return
            }

            else -> {
                // TOP/遷移中はWeb側の自動クリック待ち
            }
        }

        handler.postDelayed({ autoTick() }, 600)
    }

    private fun startMailPolling() {
        // (旧) 互換のため残すが、現行は startSbiMailPolling() を使う
        startSbiMailPolling(System.currentTimeMillis())
    }

    private fun startSbiMailPolling(sentAtMs: Long) {
        mailPollGen++
        val gen = mailPollGen

        sbiMailPolling = true
        sbiMailMinSentMs = sentAtMs
        sbiMailDeadlineMs = sentAtMs + 120_000L
        pendingDeviceAuthUrl = null

        // 仕様: クリック後5秒待ってから、5秒間隔で最大120秒ポーリング
        display.appendLog("AUTO: wait 5s then poll SBI mail every 5s up to 120s")

        // 仕様: 自動取得中でもタブ遷移を妨げない（MAILへ強制遷移しない）

        handler.postDelayed(object : Runnable {
            override fun run() {
                if (mailPollGen != gen) return

                val now = System.currentTimeMillis()
                if (now > sbiMailDeadlineMs) {
                    sbiMailPolling = false
                    mailPollGen++
                    display.setTabState(1, false, "#FFCCCC")
                    display.showErrorPopup(
                        "SBIメール未着",
                        "Eメール送信から120秒以内にSBIメールが見つかりませんでした。\nアプリを終了し、最初からやり直してください。"
                    ) {
                        finishAffinity()
                    }
                    return
                }

                fetchMail(isAuto = true)
                handler.postDelayed(this, 5000)
            }
        }, 5000)
    }

    private fun handleMailForAuto(list: List<EmailItem>) {
        if (pendingDeviceAuthUrl != null) return

        // ポーリング中は「送信クリック以降」に届いたSBIメールだけを見る
        val url = findDeviceAuthUrlFromMails(list, if (sbiMailPolling) sbiMailMinSentMs else 0L)
        if (url != null) {
            pendingDeviceAuthUrl = url
            mailPollGen++ // polling stop
            sbiMailPolling = false

            display.appendLog("AUTO: deviceAuthUrl found -> open")
            openAuthUrl(url)
        }
    }

    private fun findDeviceAuthUrlFromMails(list: List<EmailItem>, minSentMs: Long): String? {
        for (m in list) {
            // 仕様: 送信元メールアドレスだけで判定して良い
            val fromOk = m.from.contains("info@sbisec.co.jp", ignoreCase = true)
            if (!fromOk) continue

            // 仕様: 送信ボタン押下時刻以降のみ
            if (minSentMs > 0 && m.sentTimeMs > 0 && m.sentTimeMs < minSentMs) continue

            // AUTH CODE: メール本文内の「6桁」を全部拾う（スパム混在でも候補を全部出す）
            Regex("""(?<![0-9A-Za-z_])\d{6}(?![0-9A-Za-z_])""")
                .findAll(m.body)
                .map { it.value }
                .distinct()
                .forEach { code ->
                    if (!authCodeCandidates.contains(code)) {
                        authCodeCandidates.add(code)
                        display.appendLog("AUTH(MAIL-CAND): $code")
                    }
                }

            val urls = mail.extractUrls(m.body)
            val hit = urls.firstOrNull {
                it.contains("https://m.sbisec.co.jp/deviceAuthentication/input?param1=", ignoreCase = true)
            }
            if (hit != null) return hit
        }
        return null
    }

    private fun isSbiDomain(url: String?): Boolean {
        val u = (url ?: "").lowercase()
        return u.contains("sbisec.co.jp") || u.contains("login.sbisec.co.jp") || u.contains("m.sbisec.co.jp")
    }

    private fun stopAuto(reason: String) {
        display.appendLog("AUTO: stop ($reason)")
        autoRunning = false
        webMain.setAutoRunning(false)
        pendingDeviceAuthUrl = null
        mailPollGen++
        sbiMailPolling = false
    }
}