# Help Net: error.txt 残件修正

対象は利用者が指定したチャットの最新版 `C:/Users/TAKUMI/.codex/worktrees/building-vulnerability/planD`。Android 1.5.68 (81)、PWA 1.6.40 / cache r70。元の `plan.txt` と旧 AndroidStudioProjects は変更していない。

## 今回の実装

- メニュー、位置取得、共有、通知の英語文言を補完。読み上げ用ラベルも切り替える。入力した氏名・住所・自由記述は翻訳しない。
- 名称を Help Net に変更。アプリID、保存形式、既存の公開URLは維持する。
- 地震・訓練の「対象地域」入力を削除。選択グループの自治体／地域を使用。架空京都訓練は京都市。
- 発行済みレポートをアプリ内表示し、Webはブラウザーの印刷、AndroidはPrintManagerからPDF保存を選べるようにした。旧Androidの日時API互換性も修正。
- 建物詳細の応答なし／SOS人数を押すと、公開条件を満たす住民を絞り込む。人物行から人物詳細を開く。非公開住民は人数のみで、既存の3分ルールを維持。
- 負傷情報はけがの有無、SOSは助けが必要な状態、と入力箇所で説明。負傷記録は安否回答を変更しない。
- 町内会の吹き出しを再作成せず位置を保持。配置の変更は180msで補間。空きがあればアイコンを避け、避けきれない場合は重なりを許容する（利用者確認済み）。吹き出しを外部一覧へ移す仕様にはしていない。
- アプリ設定の変更に表示が追従するように修正。Activity Result APIと古いFragment依存関係の不整合を解消。

## error.txt の確認範囲

| 指摘 | 状態・確認 |
|---|---|
| 英語化 | 上記の漏れを修正。ローカルPWAでメニュー、人物・建物詳細、活動、レポートを操作確認。すべての端末通知の実機確認は未実施 |
| 未回答でも地図へ進める | 既存修正を維持。今回も未回答で地図が開かず、回答後に開くことを確認 |
| 町内会名のdemo | 既存の通常名を維持。架空データである旨はレポート内に表示 |
| 管理者の地震開始／PWA保存 | 既存修正を維持。既存の権限・保存テストを再実行 |
| 京都で現在地ボタン | 架空訓練の割当位置を使う既存仕様を維持 |
| 英語の地図上部／Show safe／位置不明 | 390px幅のブラウザーで表示確認 |
| 自分の活動の担当外警告 | 架空メンバーで変更・保存し、不要な警告が出ないことを確認 |
| 負傷とSOS | 区別を入力欄に追加 |
| レポート内表示／PDF | レポート発行・内容表示・印刷ボタンの有効化を確認。最終PDFファイルの保存結果とAndroid実機印刷は未確認 |
| 登録住民・建物の区別 | 既存の公開範囲表示を維持し、人数から絞り込めるように修正 |
| 吹き出し | 位置維持・補間を実装。実機の動きの体感評価は未実施 |
| 震度6.2の表示場所 | Aboutに表示する既存仕様を維持 |
| 津波ボタン余白 | 既存のコンパクトな表示を維持し、英語390px幅で確認 |
| PLATEAUの表示名 | 利用者向けの既定名を「選択した建物」に変更 |
| 建物の応答なし人数が押せない | 人数の絞り込みと人物詳細への遷移をブラウザーで確認 |
| 対象地域 | 利用者の確認に基づき入力削除・グループ地域を継承 |
| 名称 | Help Netに変更 |

## 検証

- Web／lab: 243テスト成功。作業ディレクトリをlab配下にすると地図データの相対参照で失敗するため、従来のテストと同じプロジェクト直下から実行。
- cloud-sync: 74テスト成功、配信用ビルド成功。
- Android: 293単体テスト成功、debug APKと画面テストAPKのビルド成功、lintDebug成功（既存の警告は残る）。実機にはインストールしていない。
- 確認用サーバーはメモリー上の架空データを使用。本番の住民記録を書き換えていない。
- 印刷ボタンのクリック後、確認用ブラウザーの操作応答が一時停止したためEscで戻った。PDFファイルを書き出せたとは記録しない。

## 公開

- PWA: https://tasukeai-sync.tomodachi-takumi.workers.dev/ （1.6.40/r70、deployment `a4a32d851bab430aaac8c4cd8adbfa29`）。既存の保存先と共有設定を維持。
- 比較: https://nevaseme.github.io/safety_confirm/#helpnet-feedback-comparison 。既存の7画面を残し右側に追加。追加ページは架空データによるWeb操作見本であり、Android実機画像ではない。
- 比較公開コミット: `e3a2481`。追加3ファイルとindexへの読込1行のみ。元の作業場所と最新版の両方のdemoにも追加。
- 公開ページで既存7画面と右端の追加を確認し、英語切り替え、建物表示、応答なし2人への絞り込みを操作確認。公開PWAも再読込後にHelp Netへの更新を確認。確認画像: `output/helpnet-feedback/public-comparison.jpg`。

## デザイン根拠

既存の画面構成を保ち、操作対象の大きさ、フォーカス、明確な名称、英語での折り返し、動きを減らす設定を優先。各制度への認証・完全準拠を主張するものではない。

- [Apple Human Interface Guidelines: Accessibility](https://developer.apple.com/design/human-interface-guidelines/accessibility)
- [Google: Accessible apps / 48dp touch targets](https://developer.android.com/guide/topics/ui/accessibility/apps)
- [Material Design: Accessible design](https://m3.material.io/foundations/accessible-design/overview)
- [米国政府 USWDS Design principles](https://designsystem.digital.gov/design-principles/)
- [デジタル庁デザインシステム](https://design.digital.go.jp/dads/)、[ボタン](https://design.digital.go.jp/dads/components/button/)
- [Android HTML printing](https://developer.android.com/training/printing/html-docs)
- [Android Fragment releases](https://developer.android.com/jetpack/androidx/releases/fragment)

## 文書と実装の差

- 旧資料のPlanD／Tasukeai表記は今回のHelp Netと異なる。過去記録は書き換えず、この記録を最新の実装状況とする。
- 既存の `2026-11-05-menu-access-report.md` は比較サイトの更新をしない前提だったが、現在の利用者指示に従って更新した。
- 最新Android作業ツリーのdemoは公開中の7画面より古かった。公開mainを基準に追加分だけを反映し、古いdemo一式で置換していない。
- 従来の建物画面テストは人物行で地図へ移る期待だった。現在は人物詳細を開くため期待を変更し、テストAPKのビルドまで確認。実機での実行は未実施。
