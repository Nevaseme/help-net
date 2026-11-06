# 自動起動案内の文言検討とAndroid 1.7.3実装

## 対象と根拠

利用者が提示したvivo画面と現行 `AutostartGuidance.kt`、`AutostartSettings.kt`、日英リソースを確認。新しい画面操作試験やアクセシビリティ適合試験は行っていない。frontend-designの文言指針とProduct Designの限定範囲のレビューを適用した。

![利用者提示の現行画面](</C:/Users/TAKUMI/projects/planD/unnamed (2).jpg>)

1. 安否登録後にMapへ進む：提示画像ではMapへの遷移を確認できる。ただし自動起動案内が操作を遮る。
2. 案内を読む：目的は示されているが、見出し・本文・手順・補足が長く、再起動後の共有に関する任意設定だと素早く理解しにくい。
3. 選択する：Open settingsは動作が明確。LaterとReviewedは保存文字列が異なるだけで、どちらも閉じて自動再表示を止める。Reviewedは権限の確認・設定完了のようにも見え、Laterは再通知するようにも読める。
4. 設定から戻る：OSの許可状態は取得していない。戻っただけで設定完了と表示してはいけない。設定画面を直接開けなかった場合の既存案内は必要。

## 推奨する文案

| 項目 | English | 日本語 |
| --- | --- | --- |
| 見出し | Sharing after a restart | 端末再起動後の共有 |
| 本文 | To help Help Net resume sharing after your phone restarts, enable Autostart in your phone settings. Turn on Help Net in the app list. | 端末の再起動後に共有を再開できるよう、端末設定の「自動起動」でHelp Netをオンにしてください。 |
| 主操作 | Open settings | 設定を開く |
| 閉じる操作 | Close | 閉じる |

ReviewedとLaterを1つのCloseに整理する。Closeは案内を閉じ、既存どおり確認済みを保存する。OS権限は変更しない。設定を開くボタンも許可を直接与えるわけではないためEnableとは書かない。設定から戻った後も、閉じる操作を明示する。

既存の再起動後の共有メニューは維持し、そこから案内を再度開ける。長い再訪問方法の補足はダイアログから外す案。自動表示のタイミング変更や非モーダル化は今回の文言提案に含めず、実装しない。

配置は既存テーマと書体を使い、見出し・短い本文・2つの操作に整理する。設定を開くを主操作、閉じるを補助操作にする。文字拡大時の折り返しと操作領域を維持する。キーボード／支援技術のフォーカスを失わせず、タッチ時に不要なボタン強調を追加しない。実装後の実機確認は未実施。

## 公式指針との対応

- [Apple HIG Alerts](https://developer.apple.com/design/human-interface-guidelines/alerts)：短く具体的な見出しと本文、選択結果が分かるボタン名。
- [Google Material Writing](https://m1.material.io/style/writing.html)：簡潔・正確・直接的な文言、ユーザーが行う操作を中心に説明。Material 3サイトは取得時JavaScriptのみのため、読めた公式Writing資料を使用。
- [Microsoft Buttons](https://learn.microsoft.com/en-us/windows/apps/develop/ui/controls/buttons)：目的と状態を明確にし、同時に提示するボタンを1〜2個に絞る。
- [USWDS Button](https://designsystem.digital.gov/components/button/)／[Modal](https://designsystem.digital.gov/components/modal/)：実行結果の分かる短い操作名、モーダル内の操作を制限する。
- [デジタル庁 ボタン](https://design.digital.go.jp/dads/components/button/usage/)：重要度に応じた強調、ラベル長に応じたサイズ、操作領域の確保。

複数OSの外観や配置規則を混ぜる提案ではない。文言と操作の明確さについて各指針を参照し、Androidの既存コンポーネントに合わせる。

## 承認と実装状況

利用者が案を承認したためAndroid 1.7.3/code106へ反映。Open settingsとCloseの2操作にし、既存メーカー分岐に加え、実際に開ける自動起動Activityの存在・有効状態・公開状態・必要権限を確認する。確認できない端末では自動案内と設定メニュー項目を表示しない。対象パッケージだけをManifestのqueriesへ追加し、全パッケージ照会権限は使わない。端末のOS権限は変更していない。

ビルド成功。vivo実機のAutostartGuidanceTestは4件成功（5.838秒）。日英の表示、Close、設定画面を開いても許可済みにしないこと、非対応時の非表示を確認。実アプリのApp settingsから案内を開き、短い見出し・本文・2ボタンを画面とUI階層で確認し、閉じて地図へ戻した。versionName=1.7.3/code106を確認。

既存テストにPlanD Mk1の旧文言が残っていたため、現行Help Net文言へ合わせた。「このアプリについて」の文案は別資料で修正したが、ANUGAの役割の説明も今回の確認事項のためアプリ本文には未反映。

## 同時に確認した事項

- 受信は警報対象地域を中継とAndroidの両方で京都府南部に固定している。東京の地震一般への対応は警報地域の追加だけでは足りず、震度情報の受信・起動条件も関わるため、利用者の「難しい作業はしない」に従い保留。受信コードは変更していない。
- 公開失敗run 42269019818は生成とアップロード後、Pagesのdeployment_queuedが続いた後にDeploy Help Netで失敗。その後42269928646と42272926013は成功。アプリの不具合によるビルド失敗ではなかった。
- 津波の現行配布データは独自JS solver2.3の事前計算結果。ANUGA 3.3.10は別のオフライン計算・独立検証で使う。旧説明に「アプリから共通ブラウザー計算を使う」が残るが、現行アプリは事前計算データ読込。計算方式の差替えは行っていない。
