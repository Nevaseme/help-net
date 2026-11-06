# 開始済み地震への参加・端末別案内の最終確認

## 変更

- 参加案内は端末を判定し、iPhone/iPad、Android、PC/Macの案内をそれぞれ表示。iPadのデスクトップ形式User-Agentもタッチ対応条件で判定する。EN/JP切替とTomodachi/Boeingを維持。
- AndroidはAPKを先に置き、濃色の主ボタンに変更。ブラウザー版は下に枠線ボタンで表示。PC/Macはブラウザー版へのボタンを表示。
- `e8b059e`、Pages run `42250717651`で公開済み。PWA本体・API・保存形式・配布APKは変更しない。
- 旧URL転送ページの途中に出ていた案内文字は、通常は表示しないよう即時の`location.replace`と`noscript`内の代替リンクに変更。旧名リポジトリcommit `9fde54cccee5d17cab484304deb3c4ed3dc301b7`。初回公開はGitHub Pagesの`updating_pages`が10分を超えて失敗し、失敗ジョブを再実行中。公開確認前に完了扱いとしない。

## 公開環境で通した操作

公開PWA 1.7.0と実際のCloudflare共有サーバーを使用。ルートの差替え・APIモックは使用しない。実際のTomodachi/Boeingの照合成功を確認したが、そのグループへの参加・安否書込み・開催変更はしない。

独立したQA用グループを作り、公開CloudClientから管理者が地震を先に開始。参加者の新規ブラウザーを後から開き、次を実操作した。

1. `https://nevaseme.github.io/safety_confirm/help-net/` → 現在の参加案内 → PWAボタン。
2. 試験用グループ名・参加パスワード → 名前 → 安否画面。同じ開始時刻・開催IDを保持し、住居の建物IDを持つことも確認。
3. 初回安否登録前はMap・People・Tasks・メニューへ移動できない。
4. 無事を登録 → 承認済みの京都案内 → Continue → 地図。公開背景地図、人物、現在地Canvas、Show safe切替を確認。
5. SafetyでSOSへ更新してもSafetyに留まり、京都案内を再表示しない。管理者側のサーバー同期でその回答を確認。
6. ページを再読込しても所属が残り、再参加や京都案内の再表示がない。

ChromeでiPhone・iPad・Androidの画面/UA条件およびPCを使用し、4ケースすべて成功。通信エラー・JavaScript例外なし。名前の登録から安否操作可能までそれぞれ1,111 / 1,173 / 1,189 / 1,303ms。地図の観測時間は確認用3秒待機を含むため、読み込み速度の測定値にはしない。試験開催は終了済み。試験グループの履歴はサーバーに残る。

端末別案内は別途iPhone、iPad、iPadデスクトップ表示、Android、Windows、Macの6条件で旧URLから開き、該当案内だけの表示・日英切替・選択保持・手順開閉・横はみ出しなしを確認。AndroidのAPKがブラウザー選択肢より上にあり、主ボタンの色であることも検査。スクリーンショットを目視確認。

## Android実機

配布済み1.7.0 / code103に対し、既存の`VenueExperienceDeviceTest#earthquakePresentationKeepsTrainingBehavior`を実行し、14.369秒、1試験成功。隔離fixtureで安否ゲート・初回案内・地図の移動と拡大縮小・画面往復・安否再更新を操作。既存開催への新規参加のUIそのものをこの試験で通したという意味ではない。試験後は元のグループと言語へ復元し、通常のMainActivityへ戻した。

## 試験中の訂正と確認範囲

- 最初のPWA試験は青丸を`.my-location-dot`のDOM要素として待って失敗。実装はCanvasであり、画面でも青丸を確認できた。Canvasの確認へ訂正後、全4ケース成功。アプリのコード変更は不要。
- 端末別案内の公開試験は転送直後のCSS読込前に表示判定して失敗。転送先のload完了を待つよう訂正後、全6ケース成功。
- Mac試験ではPCホストのタッチ情報が残ったため、実際のMac相当の`maxTouchPoints=0`を指定して再確認した。
- 古い`VenueOnboardingTest.kt`は旧日本語ラベルや「住居なし」を前提にした試験で、現在の仕様と一致しない。今回これを成功の証拠に使わず、現在の公開フローと現行実機試験を用いた。無関係の既存試験ソース変更はコミットしない。
- iPhone/iPad実機のSafari・ホーム画面追加後の起動は、このPC上のChrome試験では保証できない。利用者へ実機確認を依頼中。

証跡はローカルの`output/playwright/final-demo/`、`output/playwright/guide-*.png`、`output/final-demo-android-device.txt`。端末データや生成物はコミットしない。
