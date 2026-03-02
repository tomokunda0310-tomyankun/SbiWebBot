//app/src/main/java/com/papa/sbiwebbot/Display.kt
//ver 1.02-33
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

class Display(private val context: Context, private val tvLog: TextView, private val tabLayout: TabLayout, private val logStore: LogStore? = null) {
    private val handler = Handler(Looper.getMainLooper())
    private val blinkingAnims = mutableMapOf<Int, ValueAnimator>()
    private val appVersion = "1.02-33"

    // 操作ログをファイルへ保存（内部ストレージ）
    // /data/data/<package>/files/oplog.txt
    private val logFileName = "oplog.txt"

    init {
        tvLog.setOnClickListener {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("SbiLog", tvLog.text))
            Toast.makeText(context, "Log Copied (v$appVersion)", Toast.LENGTH_SHORT).show()
        }
    }

    fun appendLog(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val line = "[$time] $msg\n"
        tvLog.post { tvLog.append(line) }

        // best-effort file append
        try {
            context.openFileOutput(logFileName, Context.MODE_APPEND).use { out ->
                out.write(line.toByteArray(Charsets.UTF_8))
            }
        } catch (_: Throwable) {
        }

        // also write to public Downloads/Sbi/
        try {
            logStore?.appendOplog(line)
        } catch (_: Throwable) {
        }
    }

    fun getLogFileName(): String = logFileName

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
        authCodes: List<String> = emptyList(),
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

        // 認証コードボタン（テンキー廃止）
        if (authCodes.isNotEmpty()) {
            val codeRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 12, 0, 0)
            }
            authCodes.distinct().take(3).forEach { code ->
                val b = Button(context).apply {
                    text = code
                    setOnClickListener {
                        et.setText(code)
                        et.setSelection(et.text.length)
                        onInput(code)
                    }
                    setOnLongClickListener {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("AuthCode", code))
                        Toast.makeText(context, "AuthCode Copied", Toast.LENGTH_SHORT).show()
                        true
                    }
                }
                codeRow.addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
            box.addView(codeRow)
        }

        box.addView(et)

        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 12, 0, 0)
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
            text = "INPUT"
            setOnClickListener { onInput(et.text?.toString() ?: "") }
        }
        btnRow.addView(btnTap, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        btnRow.addView(btnBack, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        btnRow.addView(btnOk, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        box.addView(btnRow)

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