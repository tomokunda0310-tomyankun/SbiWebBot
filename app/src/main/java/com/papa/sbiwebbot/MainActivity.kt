//app/src/main/java/com/papa/sbiwebbot/MainActivity.kt
//ver 1.00-30
package com.papa.sbiwebbot

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.tabs.TabLayout
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private lateinit var display: Display
    private lateinit var config: Config
    private lateinit var web: Web
    private lateinit var mail: Mail

    private lateinit var etConfig: EditText
    private lateinit var lvInspect: ListView
    private lateinit var lvMail: ListView
    private lateinit var tabLayout: TabLayout

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tabLayout = findViewById(R.id.tabLayout)
        display = Display(this, findViewById(R.id.tvLog), tabLayout)

        config = Config(this)
        mail = Mail()
        web = Web(findViewById(R.id.webView), display)

        etConfig = findViewById(R.id.etConfig)
        lvInspect = findViewById(R.id.lvInspect)
        lvMail = findViewById(R.id.lvMail)

        setupTabs(tabLayout)
        setupButtons()

        web.setCallback(object : Web.WebCallback {

            override fun onWebLoading(isLoading: Boolean, url: String?) {
                runOnUiThread {
                    webLoading = isLoading
                    updateTabVisibility()
                    if (!isLoading) maybeStartAutoPatrol()
                }
            }

            override fun onElementsInspected(list: List<HtmlElement>) {
                runOnUiThread {
                    inspectedElements.clear()
                    inspectedElements.addAll(list)
                    lvInspect.adapter = ArrayAdapter(
                        this@MainActivity,
                        android.R.layout.simple_list_item_1,
                        list.map { "[${it.tag}] ${it.text}" }
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

        lvInspect.setOnItemClickListener { _, _, p, _ ->
            if (webLoading) return@setOnItemClickListener
            val el = inspectedElements[p]
            display.showActionDialog(el) { action ->
                try {
                    val creds = JSONObject(etConfig.text.toString()).getJSONObject("sbi_credentials")
                    when (action) {
                        "Click" -> {
                            web.executeAction(el.xpath, "click")
                            display.appendLog("CLICK -> ${el.xpath}")
                        }
                        "Input User" -> web.executeAction(el.xpath, "input", creds.getString("username"))
                        "Input Pass" -> web.executeAction(el.xpath, "input", creds.getString("password"))
                    }
                } catch (e: Exception) {
                    display.appendLog("Action Fail: ${e.message}")
                }
            }
        }

        lvMail.setOnItemClickListener { _, _, p, _ ->
            if (mailLoading) return@setOnItemClickListener
            val item = mailItems[p]
            val urls = mail.extractUrls(item.body)
            display.showMailOption(item, urls) { url ->
                web.loadUrl(url)
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
        web.loadUrl("https://www.sbisec.co.jp/ETGate/")
        display.appendLog("System Init: v${display.getVersion()}")

        // config読み込み後: メール自動取得
        if (!etConfig.text.isNullOrBlank()) {
            fetchMail(isAuto = true)
        }
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

                    // デバッグ: 本文に「認証」が含まれるメールから6桁とURLを拾う
                    val hit = list.firstOrNull { it.body.contains("認証") }
                    if (hit != null) {
                        val m = Regex("""(?<![0-9A-Za-z_])\d{6}(?![0-9A-Za-z_])""").find(hit.body)?.value
                        if (!m.isNullOrBlank()) display.appendLog("AUTH(MAIL): $m")
                        val urls = mail.extractUrls(hit.body)
                        if (urls.isNotEmpty()) display.appendLog("MAIL: urlHit=" + urls.first())
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

    private fun setupButtons() {
        findViewById<Button>(R.id.btnBack).setOnClickListener { web.goBack() }

        // 巡回: 手動ステップ
        findViewById<Button>(R.id.btnPatrol).setOnClickListener { doPatrolStep() }

        // 巡回 長押し => AutoLogin（旧SAVE長押し移植）
        findViewById<Button>(R.id.btnPatrol).setOnLongClickListener {
            display.appendLog("PATROL(LONG): autoLogin")
            web.autoLogin(etConfig.text.toString())
            true
        }

        // RANK: ランキング抽出入口
        findViewById<Button>(R.id.btnRank).setOnClickListener { web.extractRanking() }

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

    private fun doPatrolStep() {
        val url = web.getCurrentUrl() ?: ""

        // 画面遷移でOTP送信状態をリセット
        if (!url.contains("/otp/", ignoreCase = true)) {
            otpClickInFlight = false
            otpClickOk = false
            otpClickMs = 0L
        }
        display.appendLog("PATROL: url=$url")

        when {
            url.contains("/login/entry") || url.contains("login.sbisec.co.jp/login/") -> {
                display.appendLog("PATROL: autoLogin")
                web.autoLogin(etConfig.text.toString())
            }

            url.contains("/otp/entry") || url.contains("/otp/confirm") -> {
                display.appendLog("PATROL: click 'Eメールを送信する' then poll SBI mail")
                otpClickInFlight = true
                otpClickOk = false
                otpClickMs = System.currentTimeMillis()
                web.sendOtpEmail()
                // ポーリング開始は onClickResult(OK) で行う
            }

            else -> {
                display.appendLog("PATROL: no rule (manual)")
            }
        }
    }

    private fun maybeStartAutoPatrol() {
        if (autoRunning) return
        if (webLoading) return
        if (mailLoading) return
        if (etConfig.text.isNullOrBlank()) return

        autoRunning = true
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

        val url = web.getCurrentUrl() ?: ""

        when {
            url.contains("/login/entry") || url.contains("login.sbisec.co.jp/login/") -> {
                display.appendLog("AUTO: autoLogin")
                web.autoLogin(etConfig.text.toString())
            }

            // OTP送信は /otp/entry で「1回だけ」
            url.contains("/otp/entry") -> {
                // ここでは「クリック試行」だけ。クリック成功が返ってきたら onClickResult でポーリング開始。
                if (!otpClickOk && !otpClickInFlight) {
                    val now = System.currentTimeMillis()
                    display.appendLog("AUTO: send OTP email")
                    otpClickInFlight = true
                    otpClickMs = now
                    web.sendOtpEmail()
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
                val code = lastAuthCode
                if (!code.isNullOrBlank()) {
                    display.appendLog("AUTO: submit device auth code")
                    web.submitDeviceAuthCode(code)
                } else {
                    display.appendLog("AUTO: auth code missing")
                }
                stopAuto("device-auth submitted")
                return
            }

            else -> {
                // TOP/遷移中はWeb側の自動クリック待ち
            }
        }

        handler.postDelayed({ autoTick() }, 900)
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
            web.loadUrl(url)
        }
    }

    private fun findDeviceAuthUrlFromMails(list: List<EmailItem>, minSentMs: Long): String? {
        for (m in list) {
            // 仕様: 送信元メールアドレスだけで判定して良い
            val fromOk = m.from.contains("info@sbisec.co.jp", ignoreCase = true)
            if (!fromOk) continue

            // 仕様: 送信ボタン押下時刻以降のみ
            if (minSentMs > 0 && m.sentTimeMs > 0 && m.sentTimeMs < minSentMs) continue

            val urls = mail.extractUrls(m.body)
            val hit = urls.firstOrNull {
                it.contains("https://m.sbisec.co.jp/deviceAuthentication/input?param1=", ignoreCase = true)
            }
            if (hit != null) return hit
        }
        return null
    }

    private fun stopAuto(reason: String) {
        display.appendLog("AUTO: stop ($reason)")
        autoRunning = false
        pendingDeviceAuthUrl = null
        mailPollGen++
        sbiMailPolling = false
    }
}