//app/src/main/java/com/papa/sbiwebbot/Config.kt
//ver 1.00-46
package com.papa.sbiwebbot
import android.content.Context
import android.util.Base64
import java.io.File
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
class Config(private val context: Context) {
    private val aesKey = "1234567890123456".toByteArray()
    fun saveEncrypted(data: String): Boolean {
        return try {
            val cipher = Cipher.getInstance("AES"); cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"))
            val enc = Base64.encodeToString(cipher.doFinal(data.toByteArray()), Base64.DEFAULT)
            File(context.filesDir, "config.dat").writeText(enc); true
        } catch (e: Exception) { false }
    }
    fun loadDecrypted(): String? {
        return try {
            val file = File(context.filesDir, "config.dat")
            if (!file.exists()) return null
            val cipher = Cipher.getInstance("AES"); cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"))
            String(cipher.doFinal(Base64.decode(file.readText(), Base64.DEFAULT)))
        } catch (e: Exception) { null }
    }
}
