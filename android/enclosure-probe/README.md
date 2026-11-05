# PlanD 閉所測定（非公開の比較測定用）

独立したアプリID `io.github.nevaseme.plandprobe` で動作する。
通常のPlanDを更新せず、同居できる。通常アプリのDB・Firebase・地図・通信権限は含めない。
既存の閉所測定4クラスと画面テーマだけをビルド時に同期する。

## 使い方

1. 「PlanD 閉所測定」を開き、条件・置き方・回数が分かる試行名を入力する。
2. 「センサーを20秒測る」を押す。最初の5秒は露出、次の10秒は試行条件、最後の5秒は露出に戻す。
3. 必要な場合だけ「5秒後に環境音を3秒測る」を押し、端末の録音許可を選ぶ。5秒の準備時間で指定条件に置き、その後3秒測る。音の再生はしない。生音声は保存せず、特徴量だけを残す。準備中の中止・画面離脱では録音しない。
4. 完了した測定は専用アプリ内の `files/enclosure-trials.json` に自動保存し、起動・画面再生成時に復元する。途中で中止した測定は保存しない。画面は縦固定。
5. **保管・共有するには「結果を書き出す」で端末の保存先を選ぶ。** 保存完了表示を確認する。専用アプリを削除すると内部の保存結果は失われる。
6. 条件ごとに書き出したJSONを保管する。共有は端末側でユーザーが選んだ場合のみ行う。

開始しなければ測定しない。中止や画面を離れた場合は進行中の測定を破棄する。
閉所・埋没の分類、確率、自動SOSは実装しない。

## 開発用

`./gradlew :enclosure-probe:testDebugUnitTest :enclosure-probe:assembleDebug :enclosure-probe:assembleDebugAndroidTest`

専用APKは `enclosure-probe/build/outputs/apk/debug/enclosure-probe-debug.apk`。
端末への導入時は通常アプリ `io.github.nevaseme.plandmk1` をアンインストールしない。
自動UI試験はセンサーだけを操作し、マイク許可を付与しない。
