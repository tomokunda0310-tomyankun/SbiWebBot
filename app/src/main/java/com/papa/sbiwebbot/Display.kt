//app/src/main/java/com/papa/sbiwebbot/Display.kt
//ver 1.00-21
package com.papa.sbiwebbot
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.*
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.text.method.ScrollingMovementMethod
import android.view.ViewGroup
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
    private val appVersion = "1.00-21"

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

    fun showActionDialog(el: HtmlElement, onAction: (String) -> Unit) {
        val items = arrayOf("Click", "Input User", "Input Pass")
        AlertDialog.Builder(context).setTitle("${el.tag}: ${el.text}").setItems(items) { _, w -> onAction(items[w]) }.show()
    }

    fun getVersion() = appVersion
}
