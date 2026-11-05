# Apple・Googleデザイン監査（2026-11-01）

追記：以下は修正前の監査記録。指摘6点への実装と再検証は[同日の修正記録](2026-11-01-platform-quality-fixes.md)を参照する。過去の指摘を削除して合格に書き換えず、修正後の証跡と分けて保存する。

## 結論

主要4タブ、アイコンと文字を併用する安否表示、整理した3区分のメニューは良い基盤になっている。一方、入力保護、利用者向けの用語、文字拡大、テーマ対応に改善が必要であり、アプリ全体がガイドラインを十分満たすとはまだ判断しない。これは現状の監査であり、下記の問題を修正したという記録ではない。

Appleの明確な階層・一貫性・フィードバック・アクセシビリティを参照しつつ、Androidの戻る操作やMaterialコンポーネントは維持する。iOSの外観をAndroidへそのまま移す方針ではない。

## 確認した手順と対象

1. 公開PWAの既存タブを観察、更新後も版表示を確認した。既存登録情報は変更しなかった。
2. 最新PWAソース1.6.10に架空の参加者・訓練・通報を与え、ローカル専用ブラウザーで地図→一覧→人物詳細→対応→案件詳細→編集→安否→メニュー→プロフィールを確認した。
3. 対応結果を入力し、タブを押した後、同じ編集画面を開き直して入力保持を確認した。保存操作はしていない。
4. 幅400pxの通常表示と幅320px・ルート文字200%の日本語／英語表示を確認した。
5. Android1.5.36エミュレーターで主要4画面とプロフィール・グループ・建物・災害／訓練・オフライン地図・位置記録・情報・メニューの12画面を撮影した。
6. AndroidのOSをダークモードへ一時変更して表示を確認し、元のライト設定へ戻した。

証跡：`output/platform-audit/`。Android撮影結果は`android/emulator-5554/result.json`。ローカルPWAはAPIを無効化し、クラウド接続部分の代わりに架空データを使用した。ローカル実験画面にだけ出る接続・グループ項目は、公開PWAの不具合として数えていない。

## 優先改善点

### P1：PWAの未保存の対応結果がタブ操作で消える

- 手順：対応→通報→対応状況を変更→結果に「監査用の未保存メモ」を入力→下部の「対応」を押す→同じ編集画面を開く。
- 結果：確認なしで編集画面が閉じ、再表示した結果欄は空。DOMのvalueも空だった。
- 証跡：`07-pwa-editor.png`、`08-pwa-draft-return.png`。`lab/web/app.mjs`のtab()はcloseSheets()を呼び、`web1617-ui.mjs`の編集画面は保存済み情報から再生成される。
- 改善：案件ごとの下書き保持、または変更がある場合のみ破棄確認。タブを押しただけで入力を失わないようにする。
- 根拠：Apple Feedbackの予期しないデータ損失の防止、Googleの画面状態・編集情報の保持。
- Androidでも同じ問題があるかは今回再現していない。PWAの結果をAndroidへ一般化しない。

### P1：公開PWAの既存タブに最新版が届いていることが分からない

- 最新ソースの版表示は1.6.10だが、公開URLの既存タブは再読み込み後も1.6.0を表示した。
- 証跡：`01-pwa-existing.png`。直前の配信検証はサーバーの6ファイル一致であり、既存の全クライアントが更新済みという意味ではない。
- 改善：更新待ちを検出し、保存中の編集を保護した上で更新できる導線を用意する。古い画面の利用をユーザーが判断できるようにする。
- 原因候補はService Workerの更新待ち・キャッシュだが、今回根本原因までは確定していない。

### P2：Androidの設定画面に開発用の言葉と内部IDが残る

- プロフィールの先頭が「実験グループ」とUUIDの入力欄で、利用者が編集すべき情報に見える。
- 訓練進行中の「災害・訓練」画面には「地震発生」という主ボタンがあり、現在の訓練との関係・押した結果が読み取りづらい。実装の権限制御違反を確認したという指摘ではない。
- 証跡：Android `05-profile.png`、`08-incidents.png`。
- 改善：参加グループの名前はグループ画面にまとめ、プロフィールは名前・住居へ集中。災害操作は「地震の発生を報告」など動作を明示し、進行中の訓練との関係を整理する。災害開始条件は勝手に変えない。
- 根拠：Appleの明確な階層と行動を予測できるラベル、Googleの一貫した標準的な操作。

### P2：PWAの文字拡大時、英語タブ名が単語の途中で分割される

- 幅320px・文字200%で「Resident list」「Response」「My status」が複数の短い断片へ折り返される。タブ高さ約144pxで、画面640pxの約23%を占めた。
- 証跡：`13-pwa-large-english.png`。クリッピングは計測上なく、文字の欠落ではなく読みやすさと地図領域の問題。
- 日本語メニューはスクロール可能で、閉じるボタンも表示された（`12-pwa-large-menu.png`）。
- 改善：短く意味の通る訳語、単語単位の改行、文字拡大に対応する余白の調整。文字を縮める対応にはしない。
- 根拠：Apple Larger Textの階層・利用可能性の保持、Googleのテキスト可読性。

### P2：システムのダークモードへ対応していない

- AndroidでOSのNight modeをyesへ変えても白背景のまま（`13-system-dark.png`）。`MainActivity.kt`は既定引数でThemeを呼び、`ui/theme/Theme.kt`のdarkTheme既定値はfalse。
- PWAも`style.css`のcolor-schemeがlightで、暗色への適応がない。
- 改善：OS設定への追従と、各状態色・文字・地図コントロールの両テーマ検証。テーマ変数を切り替えるだけで全画面対応済みとしない。
- 根拠：Google Core app qualityのTheme_Support。Apple側は利用者の表示設定を尊重する方針として参照。

### P3：PWA地図の凡例の押下高さが40px

- 可視DOM計測は44×40 CSS px。証跡`map-controls.json`、`02-pwa-map.png`。
- 改善：PWAのタッチ領域を少なくとも44px、共通設計では48pxを目安にする。Appleの44ptとAndroidの48dpとCSS pxを同じ物理単位と断言しない。
- 無事スイッチはinput単体より親labelが大きいため、inputの38×24pxだけを根拠に不合格扱いしない。

## 良好だった点

- 主要4タブが一貫し、文字とアイコンと選択表示で位置が分かる。
- 無事／SOSは色だけでなく文字と記号を併用。安否回答の主要操作は十分目立つ。
- 新メニューは登録情報／災害への備え／アプリ設定に整理され、見出しと閉じるボタンを残して項目をスクロールできる。
- PWA人物詳細では情報と「救助済みにする」の主操作が区別されている。
- Androidの補助画面はタイトルと戻る操作を備え、主画面と区別されている。

## 監査範囲の限界

- 今回の全体監査はAndroidエミュレーターとデスクトップ内蔵ブラウザー。直前のメニュー実装ではvivoの遷移を確認したが、今回の全項目をvivoで再試験したわけではない。
- iPhone SafariのDynamic Type・VoiceOver、Android TalkBackの読み上げ順、スイッチ操作、全色のコントラスト、全権限ダイアログ、キーボード表示、横向き・大型画面、アプリ再生成時の全フォーム保持は未完。
- PWAの文字200%はCSSルート文字拡大による確認であり、iPhone実機の文字拡大と同一の試験ではない。
- クラウド参加・通信復旧の挙動は本監査で再試験していない。ローカルfixtureの実験項目を公開PWAへ誤って帰属させない。
- 正式なApple/Googleの認定や全規則への準拠宣言ではない。

## 公式参考資料

- Apple UI Design Tips — https://developer.apple.com/design/tips/
- Apple HIG Accessibility — https://developer.apple.com/design/human-interface-guidelines/accessibility
- Apple HIG Feedback — https://developer.apple.com/design/human-interface-guidelines/feedback
- Apple Larger Text evaluation criteria — https://developer.apple.com/help/app-store-connect/manage-app-accessibility/larger-text-evaluation-criteria
- Google Core app quality — https://developer.android.com/develop/adaptive-apps/quality-guidelines/core-app-quality
- Google Compose accessibility defaults — https://developer.android.com/develop/ui/compose/accessibility/api-defaults

## 修正・再検証の順序

入力保持と更新到達の問題を先に解決し、プロフィール／災害操作の用語、文字拡大・押下領域、テーマを順に改善する。画面を変更する際は既存Web1.6.17の構成を基準にし、Android・PWA・公開比較の差分を揃える。実機読み上げとキーボードの検証を終えるまで「全体適合」とは記載しない。
