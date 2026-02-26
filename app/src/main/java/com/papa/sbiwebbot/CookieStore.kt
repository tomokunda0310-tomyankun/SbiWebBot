//app/src/main/java/com/papa/sbiwebbot/CookieStore.kt
//ver 1.00-50
package com.papa.sbiwebbot

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.CookieManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Cookie をアプリ更新後も引き継ぐための簡易ストア。
 * - CookieManager は通常WebView内部に永続化されるが、端末/実装差で消えるケースに備えて
 *   明示的に保存/復元できるようにする。
 * - 保存先は app/files/cookies.dat (AES)
 */
class CookieStore(private val context: Context) {

    private val ui = Handler(Looper.getMainLooper())
    private val aesKey = "1234567890123456".toByteArray()

    private val file = File(context.filesDir, "cookies.dat")

    // 保存対象URL（必要なら追加）
    private val targets = listOf(
        "https://www.sbisec.co.jp/",
        "https://login.sbisec.co.jp/",
        "https://m.sbisec.co.jp/"
    )

    private var pendingSave = false
    private var lastSaveAt: Long = 0L

    fun maybeSaveDebounced() {
        val now = System.currentTimeMillis()
        // 連打抑制
        if (pendingSave) return
        if (now - lastSaveAt < 1200) return
        pendingSave = true
        ui.postDelayed({
            pendingSave = false
            saveNow()
        }, 500)
    }

    fun saveNow() {
        try {
            val cm = CookieManager.getInstance()
            val arr = JSONArray()
            for (u in targets) {
                val ck = cm.getCookie(u)
                if (!ck.isNullOrBlank()) {
                    val o = JSONObject()
                    o.put("url", u)
                    o.put("cookie", ck)
                    arr.put(o)
                }
            }

            val root = JSONObject()
            root.put("targets", JSONArray(targets))
            root.put("items", arr)
            val plain = root.toString()

            val cipher = Cipher.getInstance("AES")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"))
            val enc = Base64.encodeToString(cipher.doFinal(plain.toByteArray()), Base64.DEFAULT)
            file.writeText(enc)

            lastSaveAt = System.currentTimeMillis()
            // CookieManager自体の永続化も促進
            try { cm.flush() } catch (_: Exception) {}
        } catch (_: Exception) {
            // ignore
        }
    }

    fun restore() {
        try {
            if (!file.exists()) return
            val cipher = Cipher.getInstance("AES")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"))
            val plain = String(cipher.doFinal(Base64.decode(file.readText(), Base64.DEFAULT)))

            val root = JSONObject(plain)
            val items = root.optJSONArray("items") ?: return
            val cm = CookieManager.getInstance()
            cm.setAcceptCookie(true)

            for (i in 0 until items.length()) {
                val o = items.getJSONObject(i)
                val url = o.optString("url")
                val cookie = o.optString("cookie")
                if (url.isNotBlank() && cookie.isNotBlank()) {
                    cm.setCookie(url, cookie)
                }
            }
            try { cm.flush() } catch (_: Exception) {}
        } catch (_: Exception) {
            // ignore
        }
    }
}
