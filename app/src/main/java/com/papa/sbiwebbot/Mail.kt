//app/src/main/java/com/papa/sbiwebbot/Mail.kt
//ver 1.00-46
package com.papa.sbiwebbot
import org.json.JSONObject
import java.util.*
import javax.mail.*
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeUtility

data class EmailItem(
    val subject: String,
    val body: String,
    val date: String,
    val sentTimeMs: Long,
    val from: String
)

class Mail {

    fun fetchLatest(configStr: String, callback: (Result<List<EmailItem>>) -> Unit) {
        Thread {
            try {
                val root = JSONObject(configStr)
                val c = root.getJSONObject("mail_server")

                val props = Properties().apply {
                    put("mail.pop3.host", c.getString("host"))
                    put("mail.pop3.port", "110")
                    put("mail.pop3.ssl.enable", "false")
                    put("mail.pop3.connectiontimeout", "8000")
                    put("mail.pop3.timeout", "8000")
                }

                val store = Session.getInstance(props).getStore("pop3")
                store.connect(c.getString("user"), c.getString("password"))

                val inbox = store.getFolder("INBOX")
                inbox.open(Folder.READ_ONLY)

                val msgs = inbox.messages
                val start = if (msgs.size > 20) msgs.size - 20 else 0

                val list = mutableListOf<EmailItem>()
                for (i in msgs.size - 1 downTo start) {
                    val m = msgs[i] as MimeMessage
                    val subj = MimeUtility.decodeText(m.subject ?: "")
                    val body = extractText(m) // 全文
                    val sentMs = m.sentDate?.time ?: 0L
                    val date = m.sentDate?.toString() ?: ""
                    val from = try { MimeUtility.decodeText(m.from?.firstOrNull()?.toString() ?: "") } catch (_: Exception) { "" }
                    list.add(EmailItem(subj, body, date, sentMs, from))
                }

                inbox.close(false)
                store.close()

                callback(Result.success(list))
            } catch (e: Exception) {
                callback(Result.failure(e))
            }
        }.start()
    }

    fun extractUrls(text: String): List<String> {
        // SBIのメール本文URLを想定（https中心）
        // HTMLエンティティ(&amp;)や折返しを可能な範囲で正規化
        val normalized = text
            .replace("&amp;", "&")
            .replace("&#38;", "&")
            .replace("\r\n", "\n")
            .replace("=\n", "") // quoted-printable の改行継続を軽く吸収

        val re = Regex("""https?://[^\s<>"']+""")
        return re.findAll(normalized)
            .map {
                it.value.trim()
                    .trimEnd(')', ']', '}', '>', '.', ',', ';', '！', '。')
            }
            .distinct()
            .toList()
    }

    
    fun extractDeviceAuthUrl(text: String): String? {
        // 仕様: https://m.sbisec.co.jp/deviceAuthentication/input?param1=...&param2=...
        val normalized = text
            .replace("&amp;", "&")
            .replace("&#38;", "&")
            .replace("\r\n", "\n")
            .replace("=\n", "")

        val re = Regex("https://m\\.sbisec\\.co\\.jp/deviceAuthentication/input\\?param1=[^\\s<>\"']+?&param2=[^\\s<>\"']+")
        return re.find(normalized)?.value
            ?.trim()
            ?.trimEnd(')', ']', '}', '>', '.', ',', ';', '！', '。')
    }

private fun extractText(p: Part): String {
        return try {
            when {
                p.isMimeType("text/*") -> (p.content as? String) ?: ""
                p.isMimeType("multipart/*") -> {
                    val mp = p.content as Multipart
                    val sb = StringBuilder()
                    for (i in 0 until mp.count) {
                        val part = mp.getBodyPart(i)
                        val txt = extractText(part)
                        if (txt.isNotBlank()) {
                            sb.append(txt).append("\n")
                            // text/plain優先で早めに返す
                            if (part.isMimeType("text/plain")) break
                        }
                    }
                    sb.toString()
                }
                else -> ""
            }
        } catch (_: Exception) {
            ""
        }
    }
}
