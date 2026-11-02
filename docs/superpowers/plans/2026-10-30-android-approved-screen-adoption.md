# Android の保存済み画面基準の移植

基準: `2026-10-30-web-1617-design-baseline.md` / `components.md` / `translations.md` と保存済み 27 画像、source.zip（aee4413）。通常アプリ版は 1.5.19 / code 32。公開済み扱いにしない。閉所測定専用 APK と 1.5.18 保存版には変更しない。

最終仮想端末確認: JVM 215件・失敗0、対象instrumentation 13件を順次実行して全合格。実PlanAppの拡張操作試験は84.077秒で成功。最終通常APKは `.implementation/android-approved-ui/app-1.5.19-verified.apk`（SHA256 `E41B5B555A0C6C1175E6234E496C07832038717DFA2807DB168E8743ED92CC33`）、試験APKは同ディレクトリ `app-1.5.19-verified-androidTest.apk`（`55F837FFBCE02247D2AEE067089D21F21E641948670EE0241584E93F48D96577`）。実機検証と公開は主担当へ引継ぐ。

## 接続した画面

- 4 タブ（地図・一覧・対応・安否）、青い選択下線、災害日時、実際の参加者権限とグループ名。グループ名がない場合は「現在のグループ」とする。
- ホーム・住居・登録情報・共有・災害/訓練管理はメニュー内。言語設定は日本語/Englishを端末に保存し、利用者の氏名・本文・住所・資材名は変換しない。
- 人物一覧の2列フィルター、取得時刻/経過/位置不明、人物詳細。SOSの位置既知時は住居/寝室を非表示、位置不明時は表示。応答なしは当該災害の最初の開始時刻から3分間匿名、その後住居を表示。無事/救助済みは匿名かつ観測非表示、住居は位置不明の場合のみ表示。保存データは変更しない。
- 対応案件一覧（担当無し・自分・すべて）、本人の活動状態、担当/進捗/緊急度/資材/履歴、通報追加/訂正/取消/復元、CSV。本人が無事になっても対応履歴のある案件を残し、表示は匿名へ切り替える。
- 地図から通報位置を選択し、保存済み通報を地図に表示。未評価は灰色旗、低/中は保存済み黄三角、高は赤三角。MapLibreの建物リスク表示・retained map・表示停止の処理は維持。
- 詳細は画面内容の内側に重ねる。下のタブは操作可能で、切替時に古い地図詳細を閉じる。既存の別用途 `PlanModalBottomSheet` の振動対策は保持。
- 管理者だけに明示的な「訓練を開始」を表示。TRAINING_START に保存し、実災害 REPORT の成立条件は変更しない。

## 記録と権限

実名簿 MEMBER/LEADER/ADMIN と既存 ResponseStore を使用する。自治体役や他グループ閲覧を捏造しない。地域カタログの保存 API は存在しないため「地域未設定」と表示し、架空の地域や集計値を作らない。現在のグループの実データのみ件数を表示する。

同時更新の head は消さない。通報/進捗/資材/優先度/取消/復元で複数 head を置き換える場合、各現在内容を表示し、確認チェックがあるまで保存できない。新しい head が来れば確認を解除する。完了は空でない全進捗 head が DONE のときだけとし、DONE と IN_PROGRESS の競合を完了扱いしない。通報取消も全 head が取消の場合だけ通常一覧から除外する。

## 検証

2026-10-30 時点で JVM 214 件、失敗/エラー 0。通常 debug APK と androidTest APK のビルド成功。追加した ApprovedPrivacyTest (5件)、ApprovedResponseTest (4件)、共有契約/保存テストを含む。

仮想端末の ApprovedScreensTest は、架空の隔離グループで実DB保存、本人担当登録、タブ操作、言語永続化と実際の PlanApp の画像を確認する。画像は試験用Activityの private files/approved-screens に出す。これは計画時点では実行待ちであり、結果は追記する。

実行経過:

- emulator-5554 / API 37 に確認用 1.5.19 と試験APKを導入。物理端末へは導入していない。
- 詳細表示中の下部タブ操作・言語設定永続化 1件成功。ResponseStorageTest 2件、TrainingStartStorageTest 1件成功。
- 最初の通常ViewModel訓練テストはテスト自身の MapLibre 初期化不足、フォーム用訓練fixtureはtitle/area不足で失敗。MainActivityと同じ初期化をテストへ追加しfixtureを訂正、試験APK再ビルド成功。アプリの権限/保存仕様は緩和しない。
- 修正版試験の起動、および試験ランナーを使わない通常 MainActivity 起動が、API37仮想端末上で BIND_APPLICATION ANR により終了。完了/合格扱いにしない。最初の am start の Status:ok は画面成功を示さず、WaitTime約30秒後もランチャー前面だった。
- `.implementation/android-approved-ui/startup-anr.txt` と `normal-startup-anr.txt` に正規 ActivityManager exit-info API の trace を保存。どちらも main Runnable、FirebaseInitProvider の eager component 初期化中。試験時は FirebaseInstallations/Charset、通常起動時は GoogleApi/CloudMessaging/FirebaseMessaging。PlanDApplication.onCreate、Room、MapLibre、画面コードより前。異なる上端なので特定 HashMap の無限ループとは断定しない。
- ホスト/仮想端末負荷は `guest-load.txt`。ANR時に高いCPU/I/O pressureがあり、その後は改善。負荷だけが原因とも、アプリ自体が無関係とも断定しない。FCMを無効化して試験を通すことはしない。
- 原因確認と低負荷での再試験が終わるまで、画面スクリーンショット/高速タブ回帰/フォーム実保存を未確認として保持する。
- 2026-10-30 20時頃、証拠保存後に emulator-5554 のみ `emu kill` で正常終了。AVD/記録を消去せず、実36条件の計算中のホストメモリを解放する。実データ準備後に同じ `Medium_Phone_API_37.0` を再開し、FCMを変更せず通常起動の前後比較・修正済み試験を再確認する。
- ネイティブの寝室表示は floor/room を別々に非空抽出して結合するため、階数なし・部屋説明のみでも、住所表示が許可された人物の一覧/詳細に残る。Webで見つかった floor 必須ガードはネイティブにはない。

実36条件データ到着後の確認:

- 通常/test APK 再ビルド成功。JVM 214件・失敗/エラー0（コード変更なしのcached結果を含む）。実bundleのAPK内SHA256は `6E3EB4ED4CD388C1442591D07267392EC5B08F5C2399F9C18A65D33603C2D767` で原本と一致。
- 通常APK SHA256 `7803DA8C499B5E11F60440AE002480E3ACD91F96A2F14243D3D1FDA7E44A797E`。旧 `assets/tsunami-atami.json` は含まれず、事前計算adapterが含まれる。AGP9.4.1の `androidResources.ignoreAssetsPatterns` で既定除外を維持し旧basenameだけを追加。旧ソース/JVM試験は保持。
- 同じAVDを `-no-snapshot-load -no-snapshot-save -memory 1536` で再開。起動ログが `Increasing RAM size to 4096MB` と明示し、guest MemTotalは4007628kB。メモリ増量はsnapshot復元ではなくEmulator側の動作と判明。
- 起動完了後、emulator-5554のみに両APK更新成功。ホスト空き約868MB/CPU100%となり、Web試験終了を待ってから通常起動を比較する。低負荷で確認済みとは扱わない。
- 負荷がhost空き約2GB/guest load5.95へ改善した時点の通常起動は、`Displayed MainActivity +12s857ms` と地図/新4タブの実描画まで到達。`am start -W` は10秒でtimeoutを返したが、後続描画をログ/画像で確認。OSの `System UI isn't responding` ダイアログも記録し、端末負荷が完全解消したとは扱わない。
- 実機能試験8件中7件成功（管理者単独開始/メンバー拒否/権限未登録拒否/訓練永続化/ResponseStore2件/タブ言語）。通報フォームは実際の欠落資源 `category` で失敗したため、正しい `report_category` へ修正。`change` の日英資源と緊急度の動的ラベル参照、資材不足の書式引数も点検・修正し、固定/enum経由の資源整合性JVM試験を追加。
- 実36条件の `TsunamiSavedResultsTest` 成功。APK内SHA/元データの深さ・面積・セル数・達成高さを検証。`.implementation/android-approved-ui/tsunami-saved-results.json` に記録。初回2263ms、cache利用36件77～1557ms（高負荷仮想端末、adapter→native結果まで、地図描画/タッチを含まない）。
- `RapidMapNavigationTest` と `SheetStabilityTest` 2件は成功。`TsunamiMapTest` は35m実ポリゴン→タブ往復→0m表示→凡例まで通過し、最後の日本語固定文言assertだけ失敗。単体画面のシステム言語に合わせてresource参照に訂正し再確認待ち。
- 通報UI試験を進捗DONE+結果、緊急度HIGH+理由、資材4/1本、取消理由/復元の保存まで拡張。修正後再実行と画像確認は継続中。
- 再実行で通報保存/本人担当/IN_PROGRESS保存まで成功。試験側の `保存` は実資源 `保存する` と不一致だったため訂正（保存ボタンのlambdaは元から正しく、画面の描画不具合ではなかった）。
- 日本語実画面の `in_progress` が依存ライブラリの `values-ja` により「処理しています」となっていた。失敗画像/semanticsとmerged values-jaで確認したため、新規画面資源に `approved_` 接頭辞を付けて衝突を解消する。既存アプリ資源は改名しない。
- 担当中の詳細下端は「担当を解除 / Unassign myself」へ切替え、本人担当だけ解除し進捗を維持する。編集開始時の親head IDsを固定し、編集中に現在headが変われば保存を止めて開き直しを促す。未表示の新しい訂正を自動で置換しない。
- 新規資材の `materialId=null` に対し、型変換失敗のnullまで一致して非資材記録を競合表示していた条件を修正。Material型である場合だけIDを比較する。
- 最終拡張試験では、通報保存、本人担当登録/解除/再登録と進捗保全、編集中のhead差替え検知・保存拒否・開き直し、DONE+結果、HIGH+理由、資材4/1本と不足3本、取消理由/復元、一覧/人物詳細/安否、地図タップから座標入力済み通報への遷移、日英設定まで実DB/UIで成功。取消/復元や資材の値を実保存データで照合した。
- 画像は `.implementation/android-approved-ui/verified/` に回収し13枚目視。比較候補は05一覧、19対応一覧(English)、13資材詳細、18安否、および03/04言語設定、06人物詳細、09通報フォーム、11通報詳細、12資材フォーム、02地図選択後フォーム。10は保存途中の描画フレーム、01は架空座標35/135が京都収録範囲外の背景なので比較には使わない。撮影元はemulator-5554/API37、全て隔離した架空訓練/住民。実機画像ではない。

実地図/36条件の仮想端末試験は完了。同梱bundle SHA256は原本と一致し、旧計算JSONの非同梱も最終APKで再確認済み。実機への導入・記録維持確認は主担当が実施する。

## vivoでの通常版確認（最終追記）

- 検証済み1.5.19と試験APKの更新成功。端末に表示された通常のストア外インストール案内を確認して続行した。保護設定変更、再起動、データ消去は行わない。
- 最初の全フォーム試験は試験Activity起動待ちで停止。JDWPでmainが正常なLooper待機、試験スレッドが `Instrumentation.startActivitySync → ActivityScenarioRule.before` と確認。本文/fixtureには未到達。OSによる遮断とまでは断定しない。この試験を明示停止し、成功件数には入れない。
- 通常MainActivityは起動723ms、再確認511msで表示。新4タブと京都建物の色表示を確認した。
- `TsunamiSavedResultsTest` 単体は3.165秒で成功。36高さの達成値/面積/セル数/深さとSHA一致を確認。V2417Aのnative結果取得は初回184.373538ms、cache11.365538〜63.671770ms、平均47.053624ms。タッチ/地図描画時間ではない。
- 通常画面をUI操作して35m表示→一覧/対応/安否/地図→35m保持→0mで浸水色消去→京都地図復帰を確認。記録更新操作は行わない。既存選択グループは参加者未登録で、対応記録のボタンは理由を表示して無効。
- 証跡は `.implementation/android-approved-ui-vivo/` の `saved-results-test.txt`、`tsunami-saved-results.json`、`normal-ui-verification.json`、各normal画面。端末画像は公開比較には使用しない。比較ページmain `535a94e` の8列・切替・400px表示は公開先で確認済み。

## 残る確認

仮想端末の操作・画像確認・高速タブ移動回帰は完了。実機の通常表示・津波読込/地図確認と比較Web公開も完了。全フォームの自動試験の実機実行は上記の起動待ちのため未完了で、通常画面による表示確認と区別する。主4画面・対応フォームの定型文は言語資源へ移したが、既存の建物計算説明・災害履歴・共有設定などには日本語の定型文が残り、対応履歴の日時書式は端末設定に従う。翻訳が全アプリで完了したという扱いにしない。起動時に見られたAPI37仮想端末全体の高負荷/OSのANRを、アプリ修正で解決したとは断言しない。
