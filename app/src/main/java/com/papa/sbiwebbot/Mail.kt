//app/src/main/java/com/papa/sbiwebbot/Mail.kt
//ver 1.00-15
package com.papa.sbiwebbot
import org.json.JSONObject
import java.util.*
import javax.mail.*
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeUtility
data class EmailItem(val subject: String, val body: String, val date: String)
class Mail {
    fun fetchLatest(configStr: String, callback: (Result<List<EmailItem>>) -> Unit) {
        Thread {
            try {
                val root = JSONObject(configStr); val config = root.getJSONObject("mail_server")
                val props = Properties().apply { put("mail.pop3.host", config.getString("host")); put("mail.pop3.port", "110"); put("mail.pop3.ssl.enable", "false") }
                val store = Session.getInstance(props).getStore("pop3")
                store.connect(config.getString("user"), config.getString("password"))
                val inbox = store.getFolder("INBOX"); inbox.open(Folder.READ_ONLY)
                val msgs = inbox.messages; val start = if (msgs.size > 20) msgs.size - 20 else 0
                val list = mutableListOf<EmailItem>()
                for (i in msgs.size - 1 downTo start) {
                    val m = msgs[i] as MimeMessage
                    list.add(EmailItem(MimeUtility.decodeText(m.subject ?: ""), "", m.sentDate?.toString() ?: ""))
                }
                inbox.close(false); store.close(); callback(Result.success(list))
            } catch (e: Exception) { callback(Result.failure(e)) }
        }.start()
    }
}
