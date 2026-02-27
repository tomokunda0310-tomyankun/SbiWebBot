//app/src/main/java/com/papa/sbiwebbot/YahooSref.kt
//ver 1.02-10
package com.papa.sbiwebbot

/**
 * Auto-generated from crawl logs (sid=20260227_145147).
 * This file is for REPLAY / reference only.
 */
object YahooSref {
    const val SID: String = "20260227_145147"

    data class Selector(val type: String, val v: String)
    data class Trigger(val text: String, val score: Int)
    data class Pin(
        val pinId: String,
        val fromUrl: String,
        val toUrl: String,
        val selectors: List<Selector>,
        val trigger: Trigger,
        val tsMs: Long,
    )

    val PINS: List<Pin> = listOf(
        Pin(
            pinId = "PIN_0004",
            fromUrl = "https://finance.yahoo.co.jp/stocks/ranking/up?market=all",
            toUrl = "https://finance.yahoo.co.jp/stocks/ranking/down?term=daily&market=all&page=1",
            selectors = listOf(Selector("xpath", "//body[1]/div[2]/div[1]/div[3]/div[1]/section[1]/footer[1]/ul[1]/li[1]/button[1]"), Selector("text", "決定")),
            trigger = Trigger(text = "決定", score = 0),
            tsMs = 1772171523768L,
        ),
        Pin(
            pinId = "PIN_0008",
            fromUrl = "https://finance.yahoo.co.jp/stocks/ranking/down?term=daily&market=all&page=1",
            toUrl = "https://finance.yahoo.co.jp/stocks/ranking/volume?term=daily&market=all&page=1",
            selectors = listOf(Selector("xpath", "//body[1]/div[2]/div[1]/div[3]/div[1]/section[1]/footer[1]/ul[1]/li[1]/button[1]"), Selector("text", "決定")),
            trigger = Trigger(text = "決定", score = 0),
            tsMs = 1772171530443L,
        ),
        Pin(
            pinId = "PIN_0012",
            fromUrl = "https://finance.yahoo.co.jp/stocks/ranking/volume?term=daily&market=all&page=1",
            toUrl = "https://finance.yahoo.co.jp/stocks/ranking/volumeIncrease?term=previous&market=all&page=1",
            selectors = listOf(Selector("xpath", "//body[1]/div[2]/div[1]/div[3]/div[1]/section[1]/footer[1]/ul[1]/li[1]/button[1]"), Selector("text", "決定")),
            trigger = Trigger(text = "決定", score = 0),
            tsMs = 1772171539509L,
        ),
        Pin(
            pinId = "PIN_0017",
            fromUrl = "https://finance.yahoo.co.jp/stocks/ranking/volumeIncrease?term=previous&market=all&page=1",
            toUrl = "https://finance.yahoo.co.jp/stocks/ranking/tradingValueHigh?term=daily&market=all&page=1",
            selectors = listOf(Selector("xpath", "//body[1]/div[2]/div[1]/div[3]/div[1]/section[1]/footer[1]/ul[1]/li[1]/button[1]"), Selector("text", "決定")),
            trigger = Trigger(text = "決定", score = 0),
            tsMs = 1772171546606L,
        ),
        Pin(
            pinId = "PIN_0021",
            fromUrl = "https://finance.yahoo.co.jp/stocks/ranking/tradingValueHigh?term=daily&market=all&page=1",
            toUrl = "https://finance.yahoo.co.jp/stocks/ranking/tradingValueLow?term=daily&market=all&page=1",
            selectors = listOf(Selector("xpath", "//body[1]/div[2]/div[1]/div[3]/div[1]/section[1]/footer[1]/ul[1]/li[1]/button[1]"), Selector("text", "決定")),
            trigger = Trigger(text = "決定", score = 0),
            tsMs = 1772171552719L,
        ),
        Pin(
            pinId = "PIN_0025",
            fromUrl = "https://finance.yahoo.co.jp/stocks/ranking/tradingValueLow?term=daily&market=all&page=1",
            toUrl = "https://finance.yahoo.co.jp/stocks/ranking/creditBuybackIncrease?term=weekly&market=all&page=1",
            selectors = listOf(Selector("xpath", "//body[1]/div[2]/div[1]/div[3]/div[1]/section[1]/footer[1]/ul[1]/li[1]/button[1]"), Selector("text", "決定")),
            trigger = Trigger(text = "決定", score = 0),
            tsMs = 1772171562883L,
        ),
        Pin(
            pinId = "PIN_0029",
            fromUrl = "https://finance.yahoo.co.jp/stocks/ranking/creditBuybackIncrease?term=weekly&market=all&page=1",
            toUrl = "https://finance.yahoo.co.jp/stocks/ranking/creditBuybackDecrease?term=weekly&market=all&page=1",
            selectors = listOf(Selector("xpath", "//body[1]/div[2]/div[1]/div[3]/div[1]/section[1]/footer[1]/ul[1]/li[1]/button[1]"), Selector("text", "決定")),
            trigger = Trigger(text = "決定", score = 0),
            tsMs = 1772171570138L,
        ),
        Pin(
            pinId = "PIN_0033",
            fromUrl = "https://finance.yahoo.co.jp/stocks/ranking/creditBuybackDecrease?term=weekly&market=all&page=1",
            toUrl = "https://finance.yahoo.co.jp/stocks/ranking/creditShortfallIncrease?term=weekly&market=all&page=1",
            selectors = listOf(Selector("xpath", "//body[1]/div[2]/div[1]/div[3]/div[1]/section[1]/footer[1]/ul[1]/li[1]/button[1]"), Selector("text", "決定")),
            trigger = Trigger(text = "決定", score = 0),
            tsMs = 1772171576818L,
        ),
        Pin(
            pinId = "PIN_0037",
            fromUrl = "https://finance.yahoo.co.jp/stocks/ranking/creditShortfallIncrease?term=weekly&market=all&page=1",
            toUrl = "https://finance.yahoo.co.jp/stocks/ranking/creditShortfallDecrease?term=weekly&market=all&page=1",
            selectors = listOf(Selector("xpath", "//body[1]/div[2]/div[1]/div[3]/div[1]/section[1]/footer[1]/ul[1]/li[1]/button[1]"), Selector("text", "決定")),
            trigger = Trigger(text = "決定", score = 0),
            tsMs = 1772171584718L,
        ),
        Pin(
            pinId = "PIN_0041",
            fromUrl = "https://finance.yahoo.co.jp/stocks/ranking/creditShortfallDecrease?term=weekly&market=all&page=1",
            toUrl = "https://finance.yahoo.co.jp/stocks/ranking/creditRatioHigh?term=weekly&market=all&page=1",
            selectors = listOf(Selector("xpath", "//body[1]/div[2]/div[1]/div[3]/div[1]/section[1]/footer[1]/ul[1]/li[1]/button[1]"), Selector("text", "決定")),
            trigger = Trigger(text = "決定", score = 0),
            tsMs = 1772171590291L,
        ),
        Pin(
            pinId = "PIN_0045",
            fromUrl = "https://finance.yahoo.co.jp/stocks/ranking/creditRatioHigh?term=weekly&market=all&page=1",
            toUrl = "https://finance.yahoo.co.jp/stocks/ranking/creditRatioLow?term=weekly&market=all&page=1",
            selectors = listOf(Selector("xpath", "//body[1]/div[2]/div[1]/div[3]/div[1]/section[1]/footer[1]/ul[1]/li[1]/button[1]"), Selector("text", "決定")),
            trigger = Trigger(text = "決定", score = 0),
            tsMs = 1772171596886L,
        ),
        Pin(
            pinId = "PIN_0049",
            fromUrl = "https://finance.yahoo.co.jp/stocks/ranking/creditRatioLow?term=weekly&market=all&page=1",
            toUrl = "https://finance.yahoo.co.jp/stocks/ranking/goldenCross?term=daily&market=all&page=1",
            selectors = listOf(Selector("xpath", "//body[1]/div[2]/div[1]/div[3]/div[1]/section[1]/footer[1]/ul[1]/li[1]/button[1]"), Selector("text", "決定")),
            trigger = Trigger(text = "決定", score = 0),
            tsMs = 1772171602985L,
        ),
        Pin(
            pinId = "PIN_0053",
            fromUrl = "https://finance.yahoo.co.jp/stocks/ranking/goldenCross?term=daily&market=all&page=1",
            toUrl = "https://finance.yahoo.co.jp/stocks/ranking/deadCross?term=daily&market=all&page=1",
            selectors = listOf(Selector("xpath", "//body[1]/div[2]/div[1]/div[3]/div[1]/section[1]/footer[1]/ul[1]/li[1]/button[1]"), Selector("text", "決定")),
            trigger = Trigger(text = "決定", score = 0),
            tsMs = 1772171608920L,
        ),
    )
}