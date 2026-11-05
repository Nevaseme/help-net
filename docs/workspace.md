# 統合後の作業場所と確認手順

作業場所: `C:\Users\TAKUMI\projects\planD`。Android Studioでは、この中の `android/` を開く。
旧worktreeは退避用として保持する。旧 `AndroidStudioProjects/PlanDMk1` は同期しない。

## ローカル確認

リポジトリのルートから、各コマンドを順に実行する。

```powershell
npm ci --prefix lab
npm ci --prefix cloud-sync
npm test --prefix lab
npm test --prefix cloud-sync
node cloud-sync/scripts/build.mjs --check
```

Androidは `android/` で実行する。

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

SDKの場所はローカルの `android/local.properties`、JavaはAndroid Studio付属のJBRを利用する。
`lab/web/` の津波計算資産はGradleが取り込むため、Androidだけを別フォルダーにコピーしない。

## 公開

- PWAとAPI: `cloud-sync/` の既存Workerへ公開する。`node cloud-sync/scripts/build.mjs --standalone` が単一配信物を生成する。既存のDurable Objectと設定を維持する。
- 比較サイト: `demo/` を更新し、GitHubの `main` に反映すると既存Pagesワークフローが公開する。過去の比較を残し、最新の確認済み画面を右側へ追加する。
- 公開後はPWAの版、主要操作、比較サイトの追加画面をブラウザーで確認する。ビルド・送信成功だけを公開確認とはしない。

認証操作が必要な場合は利用者の帰席を待つ。既存認証を回避する設定変更はしない。

## ローカル専用ファイル

`plan.txt` と `add.txt` は利用者管理。ローカル内容を変更せず、追跡・コミットから除外する。
認証情報、端末情報、`local.properties`、`google-services.json`、APK、依存パッケージ、ビルド出力、`output/`、`.implementation/` はコミットしない。

## 文書と実装の区別

過去の設計文書は、その時点の案を含む。旧名称Tasukeai／PlanDや旧画面の説明は現在の実装を表さない。
実装・試験・公開の状態は、日付付き確認報告と各配信物のバージョンで確認する。
