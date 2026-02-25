//app/src/main/java/com/papa/sbiwebbot/Display.kt
//ver 1.00-44
package com.papa.sbiwebbot
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.*
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.text.method.ScrollingMovementMethod
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.tabs.TabLayout
import java.text.SimpleDateFormat
import java.util.*

class Display(private val context: Context, private val tvLog: TextView, private val tabLayout: TabLayout) {
    private val handler = Handler(Looper.getMainLooper())
    private val blinkingAnims = mutableMapOf<Int, ValueAnimator>()
    private val appVersion = "1.00-44"

    init {
        tvLog.setOnClickListener {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("SbiLog", tvLog.text))
            Toast.makeText(context, "Log Copied (v$appVersion)", Toast.LENGTH_SHORT).show()
        }
    }

    fun appendLog(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        tvLog.post { tvLog.append("[$time] $msg\n") }
    }

    fun setTabState(index: Int, isBlinking: Boolean, colorHex: String) {
        handler.post {
            val tab = tabLayout.getTabAt(index) ?: return@post
            blinkingAnims[index]?.cancel()
            if (isBlinking) {
                val anim = ValueAnimator.ofObject(ArgbEvaluator(), Color.TRANSPARENT, Color.parseColor(colorHex))
                anim.duration = 500
                anim.repeatCount = ValueAnimator.INFINITE
                anim.repeatMode = ValueAnimator.REVERSE
                anim.addUpdateListener { tab.view.setBackgroundColor(it.animatedValue as Int) }
                anim.start()
                blinkingAnims[index] = anim
            } else {
                tab.view.setBackgroundColor(Color.parseColor(colorHex))
            }
        }
    }

    fun showMailOption(item: EmailItem, urls: List<String>, onOpenUrl: (String) -> Unit) {
        val hasUrl = urls.isNotEmpty()
        val options = if (hasUrl) arrayOf("本文(全文)", "URL一覧") else arrayOf("本文(全文)")

        AlertDialog.Builder(context)
            .setTitle(item.subject)
            .setItems(options) { _, which ->
                if (options[which] == "本文(全文)") {
                    showLargeTextDialog("MAIL BODY", item.body)
                } else {
                    showUrlListDialog(urls, onOpenUrl)
                }
            }
            .show()
    }

    private fun showUrlListDialog(urls: List<String>, onOpenUrl: (String) -> Unit) {
        if (urls.isEmpty()) return
        AlertDialog.Builder(context)
            .setTitle("URL一覧")
            .setItems(urls.toTypedArray()) { _, which ->
                onOpenUrl(urls[which])
            }
            .show()
    }

    private fun showLargeTextDialog(title: String, body: String) {
        val tv = TextView(context).apply {
            text = body
            setTextIsSelectable(true)
            movementMethod = ScrollingMovementMethod()
            setPadding(24, 16, 24, 16)
        }
        val sc = ScrollView(context).apply {
            addView(tv, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        AlertDialog.Builder(context)
            .setTitle(title)
            .setView(sc)
            .setPositiveButton("OK", null)
            .show()
    }

    /**
     * REC用ポップアップ
     * - Tap(Click)
     * - TenKey入力 (0-9 / BS / CLR / OK)
     * - Back(WebView.goBack)
     */
    fun showRecPopup(
        el: HtmlElement,
        initialText: String = "",
        onTap: () -> Unit,
        onInput: (String) -> Unit,
        onBack: () -> Unit,
    ) {
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)
        }

        val tvTitle = TextView(context).apply {
            text = "[${el.tag}] ${el.text}\n${el.xpath}"
            setTextIsSelectable(true)
        }
        box.addView(tvTitle)

        val et = EditText(context).apply {
            setText(initialText)
            setSelection(text.length)
        }
        box.addView(et)

        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val btnTap = Button(context).apply {
            text = "TAP"
            setOnClickListener { onTap() }
        }
        val btnBack = Button(context).apply {
            text = "BACK"
            setOnClickListener { onBack() }
        }
        val btnOk = Button(context).apply {
            text = "OK"
            setOnClickListener { onInput(et.text?.toString() ?: "") }
        }
        btnRow.addView(btnTap, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        btnRow.addView(btnBack, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        btnRow.addView(btnOk, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        box.addView(btnRow)

        val grid = GridLayout(context).apply {
            // 3x4 keypad (1-9 / CLR-0-BS)
            columnCount = 3
            rowCount = 4
            setPadding(0, 16, 0, 0)
        }

        fun addKey(label: String, row: Int, col: Int, onKey: () -> Unit) {
            val b = Button(context).apply {
                text = label
                setOnClickListener { onKey() }
            }
            val lp = GridLayout.LayoutParams().apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(col, 1, 1f)
                rowSpec = GridLayout.spec(row, 1, 1f)
                setMargins(6, 6, 6, 6)
            }
            grid.addView(b, lp)
        }

        // Row 0: 1 2 3
        addKey("1", 0, 0) { et.append("1"); onInput(et.text?.toString() ?: "") }
        addKey("2", 0, 1) { et.append("2"); onInput(et.text?.toString() ?: "") }
        addKey("3", 0, 2) { et.append("3"); onInput(et.text?.toString() ?: "") }
        // Row 1: 4 5 6
        addKey("4", 1, 0) { et.append("4"); onInput(et.text?.toString() ?: "") }
        addKey("5", 1, 1) { et.append("5"); onInput(et.text?.toString() ?: "") }
        addKey("6", 1, 2) { et.append("6"); onInput(et.text?.toString() ?: "") }
        // Row 2: 7 8 9
        addKey("7", 2, 0) { et.append("7"); onInput(et.text?.toString() ?: "") }
        addKey("8", 2, 1) { et.append("8"); onInput(et.text?.toString() ?: "") }
        addKey("9", 2, 2) { et.append("9"); onInput(et.text?.toString() ?: "") }
        // Row 3: CLR 0 BS
        addKey("CLR", 3, 0) {
            et.setText("")
            onInput("")
        }
        addKey("0", 3, 1) {
            et.append("0")
            onInput(et.text?.toString() ?: "")
        }
        addKey("BS", 3, 2) {
            val s = et.text?.toString() ?: ""
            if (s.isNotEmpty()) {
                et.setText(s.dropLast(1))
                et.setSelection(et.text.length)
                onInput(et.text?.toString() ?: "")
            }
        }
        box.addView(grid)

        AlertDialog.Builder(context)
            .setTitle("REC")
            .setView(box)
            .setPositiveButton("CLOSE", null)
            .show()
    }

    fun showErrorPopup(title: String, message: String, onOk: () -> Unit) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("OK") { _, _ -> onOk() }
            .show()
    }

    fun getVersion() = appVersion
}