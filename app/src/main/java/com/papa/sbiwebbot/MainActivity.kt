//app/src/main/java/com/papa/sbiwebbot/MainActivity.kt
//ver 1.00-21
package com.papa.sbiwebbot
import android.os.Bundle
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

    private var webLoading: Boolean = false
    private var mailLoading: Boolean = false

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
                    applyInteractionRules()
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
                runOnUiThread { display.appendLog("AUTH(ONSCREEN): $code") }
            }

            override fun onRankingData(json: String) {
                runOnUiThread { display.appendLog("RANKING_JSON: $json") }
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
            display.showMailOption(item, urls) { url -> web.loadUrl(url) }
        }

        applyInteractionRules()

        web.loadUrl("https://www.sbisec.co.jp/ETGate/")
        display.appendLog("System Init: v${display.getVersion()}")
    }

    private fun setupTabs(tl: TabLayout) {
        val layouts = listOf(
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
                layouts.forEach { it.visibility = View.GONE }
                layouts[pos].visibility = View.VISIBLE
                if (pos == 1) fetchMail()
                applyInteractionRules()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {
                if (tab?.position == 1) fetchMail()
            }
        })
    }

    private fun applyInteractionRules() {
        // 要件: タブ点滅中(=処理中)は、中身を非表示にして誤タップ防止
        lvInspect.visibility = if (webLoading) View.INVISIBLE else View.VISIBLE
        lvMail.visibility = if (mailLoading) View.INVISIBLE else View.VISIBLE

        lvInspect.isEnabled = !webLoading
        lvMail.isEnabled = !mailLoading
    }

    private fun fetchMail() {
        display.appendLog("MAIL: fetch start")
        mailLoading = true
        applyInteractionRules()

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

                    // 本文に「認証」が含まれるメールから6桁を拾う（デバッグ表示）
                    val hit = list.firstOrNull { it.body.contains("認証") }
                    if (hit != null) {
                        val m = Regex("""(?<![0-9A-Za-z_])\d{6}(?![0-9A-Za-z_])""").find(hit.body)?.value
                        if (!m.isNullOrBlank()) display.appendLog("AUTH(MAIL): $m")
                        val urls = mail.extractUrls(hit.body)
                        if (urls.isNotEmpty()) display.appendLog("MAIL: urlHit=" + urls.first())
                    }

                    mailLoading = false
                    applyInteractionRules()

                    // MAIL: 完了（薄い緑 点灯）
                    display.setTabState(1, false, "#CCFFCC")
                }
                res.onFailure { e ->
                    display.appendLog("MAIL: fetch fail: ${e.message}")
                    mailLoading = false
                    applyInteractionRules()
                    display.setTabState(1, false, "#FFCCCC")
                }
            }
        }
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btnBack).setOnClickListener { web.goBack() }

        // 巡回: タップで次のステップへ
        findViewById<Button>(R.id.btnPatrol).setOnClickListener { doPatrolStep() }

        // 巡回 長押し => AutoLogin（旧SAVE長押し移植）
        findViewById<Button>(R.id.btnPatrol).setOnLongClickListener {
            display.appendLog("PATROL(LONG): autoLogin")
            web.autoLogin(etConfig.text.toString())
            true
        }

        // RANK: ランキング抽出入口
        findViewById<Button>(R.id.btnRank).setOnClickListener { web.extractRanking() }

        findViewById<Button>(R.id.btnSaveConfig).setOnClickListener { config.saveEncrypted(etConfig.text.toString()) }
        findViewById<Button>(R.id.btnLoadConfig).setOnClickListener { etConfig.setText(config.loadDecrypted() ?: "") }
    }

    private fun doPatrolStep() {
        val url = web.getCurrentUrl() ?: ""
        display.appendLog("PATROL: url=$url")

        when {
            url.contains("/login/entry") || url.contains("login.sbisec.co.jp/login/") -> {
                display.appendLog("PATROL: autoLogin")
                web.autoLogin(etConfig.text.toString())
            }
            url.contains("/otp/entry") || url.contains("/otp/confirm") -> {
                display.appendLog("PATROL: click 'Eメールを送信する' and fetch mail")
                web.sendOtpEmail()

                // MAILへ移動して取得
                tabLayout.getTabAt(1)?.select()
                fetchMail()
            }
            else -> {
                display.appendLog("PATROL: no rule (manual)")
            }
        }
    }
}
