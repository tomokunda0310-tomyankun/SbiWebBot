//app/src/main/java/com/papa/sbiwebbot/LogViewerActivity.kt
//ver 1.02-12
package com.papa.sbiwebbot

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class LogViewerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SID = "sid"
        const val EXTRA_MODE = "mode" // "logs" | "pins"
    }

    private val appVersion = "1.02-12"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_viewer)

        val btnShowOp: Button = findViewById(R.id.btnShowOp)
        val btnShowTry: Button = findViewById(R.id.btnShowTry)
        val btnShowPins: Button = findViewById(R.id.btnShowPins)
        val btnShowXpath: Button = findViewById(R.id.btnShowXpath)
        val btnShowSref: Button = findViewById(R.id.btnShowSref)
        val tvTitle: TextView = findViewById(R.id.tvTitle)
        val tvBody: TextView = findViewById(R.id.tvBody)

        val sid = intent.getStringExtra(EXTRA_SID) ?: "(no-sid)"
        val mode = intent.getStringExtra(EXTRA_MODE) ?: "logs"

        fun readFile(relPath: String): String {
            return try {
                val root = File(filesDir, sid)
                val f = File(root, relPath)
                if (!f.exists()) return "(no file) ${f.absolutePath}"
                f.readText(Charsets.UTF_8)
            } catch (e: Exception) {
                "(read error) ${e.message}"
            }
        }

        fun show(relPath: String) {
            tvTitle.text = "LOG v$appVersion : $sid / $relPath"
            tvBody.text = readFile(relPath)
        }

        btnShowOp.setOnClickListener { show("log/oplog.txt") }
        btnShowTry.setOnClickListener { show("log/nav_try.jsonl") }
        btnShowPins.setOnClickListener { show("pins/pins.jsonl") }
        btnShowXpath.setOnClickListener { show("log/xpath.log") }
        btnShowSref.setOnClickListener { show("log/sref.log") }

        // mode hint
        if (mode == "pins") {
            show("pins/pins.jsonl")
        } else {
            show("log/oplog.txt")
        }
    }
}
