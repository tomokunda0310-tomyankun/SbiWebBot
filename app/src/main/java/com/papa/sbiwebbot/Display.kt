//app/src/main/java/com/papa/sbiwebbot/Display.kt
//ver 1.00-15
package com.papa.sbiwebbot
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.*
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.tabs.TabLayout
import java.text.SimpleDateFormat
import java.util.*

class Display(private val context: Context, private val tvLog: TextView, private val tabLayout: TabLayout) {
    private val handler = Handler(Looper.getMainLooper())
    private val blinkingAnims = mutableMapOf<Int, ValueAnimator>()

    fun appendLog(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        tvLog.post { tvLog.append("[$time] $msg\n") }
    }

    fun setTabState(index: Int, isBlinking: Boolean, colorHex: String) {
        handler.post {
            val tab = tabLayout.getTabAt(index) ?: return@post
            val view = tab.view
            blinkingAnims[index]?.cancel()
            
            if (isBlinking) {
                val anim = ValueAnimator.ofObject(ArgbEvaluator(), Color.TRANSPARENT, Color.parseColor(colorHex))
                anim.duration = 500; anim.repeatCount = ValueAnimator.INFINITE; anim.repeatMode = ValueAnimator.REVERSE
                anim.addUpdateListener { view.setBackgroundColor(it.animatedValue as Int) }
                anim.start()
                blinkingAnims[index] = anim
            } else {
                view.setBackgroundColor(Color.parseColor(colorHex))
            }
        }
    }

    fun showActionDialog(element: HtmlElement, onAction: (String) -> Unit) {
        val items = arrayOf("Click", "Input User", "Input Pass")
        AlertDialog.Builder(context).setTitle("${element.tag}: ${element.text}").setItems(items) { _, w -> onAction(items[w]) }.show()
    }
    fun getVersion() = "1.00-15"
}
