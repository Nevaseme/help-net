# Web 1.6.17 翻訳の保存記録

[デザイン記録](2026-10-30-web-1617-design-baseline.md)と同じ時点の翻訳を保存する。今回の作業では訳語を再選定していない。

## 保存物の使い分け

| 保存物 | 用途 |
| --- | --- |
| [説明付き対訳表](assets/2026-10-30-web-1617/english-ui-glossary.md) | 用語選定の理由、一次資料、用途・注意。WebとAndroid向け推奨の両方を含む |
| [対訳表CSV](assets/2026-10-30-web-1617/english-ui-glossary.csv) | 同じ表を表計算で確認。UTF-8 BOM付き |
| [Web辞書CSV](assets/2026-10-30-web-1617/web-dictionary.csv) | `english-ui.mjs` と `language.mjs` の追加・上書き辞書を合成した固定一覧。内部の断片も含むため、そのままAndroidのラベル一覧にはしない |
| [文脈別出力CSV](assets/2026-10-30-web-1617/translation-examples.csv) | 文脈、人数・時刻・動的な文を現行変換関数に通した結果 |
| [コード保存版](assets/2026-10-30-web-1617/source.zip) | 文脈分岐、動的な組み立て、翻訳対象外の実装を確認 |

翻訳表の行数は保存マニフェストを参照。固定コピーは以後の変更と混ざらないよう、この記録の時点のものとして扱う。

## 確定した主要ラベル

| 日本語 | 現行の英語 | 文脈 |
| --- | --- | --- |
| 地震発生 | Earthquake | 見出し |
| 地図 | Map | タブ |
| 一覧／メンバー一覧 | Resident list | 人物一覧 |
| 対応 | Response | タブ |
| 安否 | My status | 自分の安否タブ |
| 未対応 | Not started | 案件の進捗 |
| 対応予定 | Acknowledged | 引き受け済み・本対応は未着手 |
| 対応中 | In progress | 案件の進捗 |
| 対応済み | Resolved | 現在採用されている訳。今回変更しない |
| 対応中 | Busy | 自分の活動状態 |
| 担当者無し | Unassigned | 担当の有無 |
| 自分の担当 | Assigned to me | 担当フィルター |
| 衝撃あり | Impact detected | スマホの観測 |
| 閉所を検知／閉所あり | Enclosed space detected | スマホの観測 |
| 応答なし／反応なし | No response | 安否状態 |
| 普段の住居・寝室 | Home and sleeping area | 人物詳細 |
| 普段昼間いる場所 | Usual daytime location | 人物詳細 |
| 最後のスマホ位置 | Last phone location | 人物詳細 |
| 位置不明 | Location unknown | 位置状態 |
| 通報を追加 | Report an incident | 追加操作 |
| 道路閉塞 | Blocked road | 通報種別 |
| 建物倒壊 | Building collapse | 通報種別 |
| 町内会別の安否統計 | Safety status by neighborhood | 統計見出し |
| 地域集計 | Area summary | 通報の地域集計 |
| 地域を変更 | Change report area | 通報の集計先変更 |
| 無事 | Safe | 他人の安否表示 |
| 無事 | I’m safe | 自分の安否選択 |
| SOS | I need help | 自分の安否選択。地図記号はSOSのまま |
| 未設定 | Not assessed | 緊急度 |
| 未設定 | Not set | 一般の未設定項目 |

`Acknowledged` の画面内説明は “Acknowledged means someone has agreed to respond but has not started the response work yet.”。担当割当・受付・日時指定を、新しくこの状態の必須条件にしていない。

## 翻訳範囲と保持するもの

- ボタン、見出し、ラベル、状態、説明、入力エラー、空表示、読み上げ名、CSVの列名と定型値が対象。
- 氏名・住所・寝室・昼間いる場所・自由入力の本文・対応結果・資材名・単位は入力どおりに保つ。登録された名前を英語名に差し替えない。
- デモの固定町内会名と班名には表示用の英訳がある。これは現実の固有名詞を自動翻訳する機能とは異なる。
- 地図配信元の地名表示はこの切り替えでは翻訳しない。
- 内部の状態値・ID・人数・件数・入力値・公開権限・3分の時計は変えない。自由文に「未対応」などの単語が含まれていても勝手に翻訳しない。
- 日付、人数、経過時間は固定文字列だけでなく組み立て規則も確認する。動的出力例にない全組み合わせが実測済みという意味ではない。
- 日本語／Englishは母語表記を維持。初回日本語、選択は即時反映、同じブラウザーに保存する。

Androidへ移す際には、文字列リソースのキーを文脈ごとに分ける。同じ日本語だから同じ訳を当てる、WebのDOM変換方式をそのまま移植する、といった実装をこの記録は指定しない。
