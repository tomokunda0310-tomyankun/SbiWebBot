//app/src/main/java/com/papa/sbiwebbot/Time.kt
//ver 1.00-57
package com.papa.sbiwebbot

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Time {
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.JAPAN)

    fun now(): String = fmt.format(Date())
}
