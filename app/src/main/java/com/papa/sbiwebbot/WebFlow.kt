//app/src/main/java/com/papa/sbiwebbot/WebFlow.kt
//ver 1.00-50
package com.papa.sbiwebbot

/**
 * Web.kt が増えてきた場合の分割先候補。
 *
 * 推奨命名:
 * - WebFlow : 巡回フロー(ログイン/OTP/デバイス登録)のステップ制御
 * - WebActions : クリック/入力/待機などの低レベル操作ユーティリティ
 * - WebSelectors : XPath/テキスト検索などのセレクタ集
 *
 * 今は「名前の確保 + 今後の移植先」を先にFIXしておく目的で用意。
 * 実装は段階的に Web.kt / MainActivity のロジックからこちらへ移す。
 */
object WebFlow
