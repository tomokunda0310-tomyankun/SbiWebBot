//app/src/main/java/com/papa/sbiwebbot/LogViewerActivity.kt
//ver 1.02-00
package com.papa.sbiwebbot

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class LogViewerActivity : AppCompatActivity() {

    private val appVersion = "1.02-00"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_viewer)

        val btnShowOp: Button = findViewById(R.id.btnShowOp)
        val btnShowTry: Button = findViewById(R.id.btnShowTry)
        val btnShowPins: Button = findViewById(R.id.btnShowPins)
        val tvTitle: TextView = findViewById(R.id.tvTitle)
        val tvBody: TextView = findViewById(R.id.tvBody)

        fun readFile(name: String): String {
            return try {
                val f = File(filesDir, name)
                if (!f.exists()) return "(no file) ${f.absolutePath}"
                f.readText(Charsets.UTF_8)
            } catch (e: Exception) {
                "(read error) ${e.message}"
            }
        }

        fun show(name: String) {
            tvTitle.text = "LOG v$appVersion : $name"
            tvBody.text = readFile(name)
        }

        btnShowOp.setOnClickListener { show("oplog.txt") }
        btnShowTry.setOnClickListener { show("nav_try.jsonl") }
        btnShowPins.setOnClickListener { show("pins.jsonl") }

        show("oplog.txt")
    }
}
