# 位置共有・衝撃表示の確認

2026-11-06。実装の存在、試験注入による確認、実機での確認を区別する。

## PWA位置共有

虎ノ門（ACTUAL）は `app.mjs` のwatchPositionから `positions` 保存、`cloud-client.mjs` の送信、受信、地図表示まで接続済み。表示中の定期同期と変更通知、通信復帰時の再送がある。通常モードの現在地青丸だけでは共有記録を作成しないが、利用者は虎ノ門で共有できればよいと確認した。

追加指示に従い京都でもGPS監視を行い、成功時の実timestampを京都の割当座標と共に共有する。京都から実GPS座標は送らない。古い取得、別開催、取得失敗では更新しない。未取得時は時刻不明。サンプル住民は実端末の測定ではなくシナリオの時刻である。

新規 `venue-location.test.mjs` と既存位置/同期試験は成功。新規 `venue-location.browser.mjs` は実Chromeで現行アプリを起動し、GPS成功・失敗、表示/非表示による購読停止と再開を確認した。入力は試験用callbackであり、iPhoneを実際に移動して別端末へ反映する一貫試験は未確認。

AndroidはPWAから受信した京都取得時刻を表示する。Android自身の京都GPS記録や衝撃監視の起動条件は、このPWA向け追加指示では変更していない。

## Android衝撃検知・表示

`AndroidDropSource.kt` はvivo V2417A / SDK36の `android.sensor.drop_depth` だけを対象にする。通知から `PlanDApplication.kt` のIMPACT保存、`RoomCloudJournal.kt` の共有、`MainScreens.kt` と `SituationMap.kt` のマーク、`SituationScreen.kt` の日時表示まで接続されている。

汎用の加速度から衝撃を判定する機能は未実装。`ImpactMonitoring.kt` は受信・遅延・途絶の監視であり、衝撃閾値を持たない。vivo通知の単位・内部判定も未確認で、建物倒壊の判定と同一視しない。

`PositionService.kt` が落下通知の監視を起動する。`MainActivity.kt` の自動起動条件は通常の住民登録またはACTUAL開催の参加者で、京都開催への参加だけでは起動しない。内部の非対応/設定要状態も通常画面では明示されない。したがって一般Androidで「完成」とは言えない。

無事・救助済みの人はプライバシー処理で衝撃表示を隠す。SOS/応答なしは表示対象。実衝撃の直近30日フィルターは同じグループ/端末単位であり、今回の災害開始後だけには限定されない。サンプル住民の衝撃マークは端末検知の成功証拠に数えない。

既存DropRecording、ImpactMonitoring、ApprovedPrivacy、SituationPresentation試験には合格記録がある。現行版で実vivo通知→保存→別端末→マーク表示までを確認した証拠は未取得。今回の依頼は調査なので汎用衝撃判定や監視条件の拡張は実装していない。

## 資料との不一致

`2026-10-30-remaining-work-audit.md` は実衝撃記録の表示・同期を未実装としていた。現コードには接続が存在するため、この点は古い記述である。一方、過去のvivo消灯中受信確認は現行版の表示・別端末同期を保証しない。
