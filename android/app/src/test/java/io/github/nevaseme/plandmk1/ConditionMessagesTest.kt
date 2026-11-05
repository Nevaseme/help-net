package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.ui.UiMessages
import org.junit.Assert.*
import org.junit.Test

class ConditionMessagesTest {
 @Test fun generatedResidentCountsAndHistoryTranslateWithoutChangingNames() {
  assertEquals("Resident safety: SOS 1 / Safe 4 / No response 2",UiMessages.text("住民の安否：SOS 1人 / 無事 4人 / 未回答 2人","en"))
  assertEquals("Building name: 山田ビル",UiMessages.text("建物名：山田ビル","en"))
  assertEquals("Checking (Superseded)",UiMessages.text("確認中（訂正前）","en"))
 }
 @Test fun joinAndRecoveryErrorsAreEnglish() {
  for(s in listOf("グループ名またはパスワードを確認してください。","この会場デモは終了しています。","復元コードを確認してください。有効期限は発行から24時間です。","参加パスワードを1〜128文字で入力してください。","権限設定を利用するため、アプリを最新版へ更新してください。"))assertFalse(UiMessages.text(s,"en"),Regex("[\\u3040-\\u9fff]").containsMatchIn(UiMessages.text(s,"en")))
 }
}
