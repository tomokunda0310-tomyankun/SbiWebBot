//app/src/main/java/com/papa/sbiwebbot/MainActivity.kt
//ver 1.00-15
package com.papa.sbiwebbot
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.tabs.TabLayout
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private lateinit var display: Display; private lateinit var config: Config; private lateinit var web: Web; private lateinit var mail: Mail
    private lateinit var etConfig: EditText; private lateinit var lvInspect: ListView; private lateinit var lvMail: ListView
    private val inspectedElements = mutableListOf<HtmlElement>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        display = Display(this, findViewById(R.id.tvLog), tabLayout)
        config = Config(this); mail = Mail(); web = Web(findViewById(R.id.webView), display)
        etConfig = findViewById(R.id.etConfig); lvInspect = findViewById(R.id.lvInspect); lvMail = findViewById(R.id.lvMail)
        
        setupTabs(tabLayout)
        setupButtons()
        web.setCallback(object : Web.WebCallback {
            override fun onElementsInspected(list: List<HtmlElement>) {
                runOnUiThread {
                    inspectedElements.clear(); inspectedElements.addAll(list)
                    lvInspect.adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_list_item_1, list.map { "[${it.tag}] ${it.text}" })
                }
            }
        })
        lvInspect.setOnItemClickListener { _, _, p, _ ->
            val el = inspectedElements[p]
            display.showActionDialog(el) { action ->
                val json = etConfig.text.toString()
                when (action) {
                    "Click" -> web.executeAction(el.xpath, "click")
                    "Input User" -> web.executeAction(el.xpath, "input", JSONObject(json).getJSONObject("sbi_credentials").getString("username"))
                    "Input Pass" -> web.executeAction(el.xpath, "input", JSONObject(json).getJSONObject("sbi_credentials").getString("password"))
                }
            }
        }
        web.loadSbi(); display.appendLog("System Init: v" + display.getVersion())
    }

    private fun setupTabs(tl: TabLayout) {
        val layouts = listOf(findViewById<View>(R.id.layoutLog), findViewById<View>(R.id.lvMail), findViewById<View>(R.id.lvInspect), findViewById<View>(R.id.layoutConfig))
        tl.addTab(tl.newTab().setText("LOG")); tl.addTab(tl.newTab().setText("MAIL")); tl.addTab(tl.newTab().setText("REC")); tl.addTab(tl.newTab().setText("CONFIG"))
        tl.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val pos = tab?.position ?: 0
                layouts.forEach { it.visibility = View.GONE }; layouts[pos].visibility = View.VISIBLE
                if (pos == 1) fetchMail()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) { if(tab?.position == 1) fetchMail() }
        })
    }

    private fun fetchMail() {
        display.setTabState(1, true, "#CCEEFF") // 薄青点滅
        mail.fetchLatest(etConfig.text.toString()) { res ->
            runOnUiThread {
                res.onSuccess { list ->
                    lvMail.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, list.map { it.subject })
                    display.setTabState(1, false, "#CCFFCC") // 薄緑点灯
                }
                res.onFailure { display.appendLog("Mail Error"); display.setTabState(1, false, "#FFCCCC") }
            }
        }
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btnBack).setOnClickListener { web.goBack() }
        findViewById<Button>(R.id.btnSaveConfig).setOnClickListener { config.saveEncrypted(etConfig.text.toString()) }
        findViewById<Button>(R.id.btnLoadConfig).setOnClickListener { etConfig.setText(config.loadDecrypted() ?: "") }
        findViewById<Button>(R.id.btnSaveConfig).setOnLongClickListener { web.autoLogin(etConfig.text.toString()); true }
    }
}
