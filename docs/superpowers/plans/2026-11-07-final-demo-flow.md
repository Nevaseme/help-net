# 開始済み地震への参加・端末別案内の最終確認

## 変更

- 参加案内は端末を判定し、iPhone/iPad、Android、PC/Macの案内をそれぞれ表示。iPadのデスクトップ形式User-Agentもタッチ対応条件で判定する。EN/JP切替とTomodachi/Boeingを維持。
- AndroidはAPKを先に置き、濃色の主ボタンに変更。ブラウザー版は下に枠線ボタンで表示。PC/Macはブラウザー版へのボタンを表示。
- `e8b059e`、Pages run `42250717651`で公開済み。PWA本体・API・保存形式・配布APKは変更しない。
- 旧URL転送ページの途中に出ていた案内文字は、通常は表示しないよう即時の`location.replace`と`noscript`内の代替リンクに変更。初回公開はGitHub Pagesの`updating_pages`が10分を超えて失敗し、同じ公開IDの再試行もキャンセル状態を引き継いだ。ページタイトルを「Opening Help Net」にした新commit `aeb72c8821a3187862bac2ea1a8d6206c561e341`、run `42253448040`で公開成功。公開HTMLに`location.replace`が含まれることを確認。

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
- 利用者からiPhone実機の旧URL→専用案内→開始済み地震への参加→安否登録→地図表示について「実機でも問題なく進めた」と回答あり。iPad実機と、今回新たにホーム画面へ追加する操作自体は未確認。

証跡はローカルの`output/playwright/final-demo/`、`output/playwright/guide-*.png`、`output/final-demo-android-device.txt`。端末データや生成物はコミットしない。

## 追加指示：津波の初回案内と熱海移動（1.7.1）

初回のTsunami操作で「Tsunami inundation is shown for Atami only. Enter the tsunami height in meters (0–35).」を表示。日本語は「津波の浸水表示は熱海のみ対応しています。津波の高さをメートル単位で入力してください（0〜35m）。」。地震・訓練ごとに一度だけで、同じ開催の再起動では再表示しない。「Go to Atami／熱海へ移動」はカメラのみを35.095,139.07へ動かし、所属・住居・位置情報は変えない。閉じるだけの操作も用意。参加案内には対象地域の一文を追記する。

PWAは見出しへフォーカスし、操作ボタンを勝手に選択表示しない。閉じた後に高さ入力欄へフォーカスしない。キーボードのTabによるボタン操作は維持。Androidも自動の入力フォーカスを追加しない。

PWAのローカルおよび公開資産での試験成功：同じ開催で2回目なし、再読込後もなし、次の訓練で再表示、日英文言、カメラだけの移動、5mの浸水表示、見出しフォーカス、Tab操作。Android実機の`TsunamiGuidanceDeviceTest`は3.225秒で成功。開催切替・以前の開催へ戻った場合・日英・移動先・入力とボタンのフォーカスを検査した。

PWA 1.7.1/r114を公開し、4公開資産がビルド結果と一致。Cloudflare deployment `951aa7c8d12b4a8abaed97ceeb1a8500`、Worker SHA256 `31a472f53f3f5151761328a742a247d0190e6ca5ab1a2b286c304a60fffdfab5`。更新前Workerのハッシュを照合し、既存の保存領域・レート制限・互換設定・監視設定を保持。API・保存形式の変更なし。

Android実機は1.7.1/code104へデータを消さず更新し、通常のMainActivityへ戻した。APKは327,726,297 bytes、SHA256 `4392656d637dced77ea378c4fe83be1af0a4641f38cef11b897e42d1581795a2`。配布APKと案内ページへの反映確認は継続中。
