# Android 自動起動の案内（1.5.56）

## 実装

- Xiaomi / Redmi / POCO、vivo / iQOOで、グループの近距離共有が開始し、最初の安否回答を終えて地図・一覧を表示した後に案内する。初回参加前、安否入力中、位置情報の権限要求中、他の詳細画面を開いている間には新しく割り込まない。
- 「再起動後も安否共有を続ける」という目的と、アプリの画面を勝手に開かないことを説明する。
- 「設定を開く」はメーカーの自動起動設定へ移動する。画面が存在しない・外部起動不可の場合はアプリ情報へ移動し、端末設定で「自動起動」を検索する手順を表示する。そこにも移動できなければ手順を残して終了できる。
- 「確認した」「あとで」は案内への回答だけを端末内に保存する。OSの許可済みとは判定しない。同期・グループ変更で繰り返し促さない。
- メニュー → アプリ設定 → 再起動後の安否共有から再確認できる。日本語・英語、既存のライト・ダークテーマに対応。
- メーカーの設定を無断で変更しない。自動起動設定を持たない一般的なAndroidには、このメーカー向けの初回案内を出さない。
- PWAと公開比較ページは変更していない。

## 検証

- 通常版ビルドとJVM261件成功（失敗・エラー0）。
- vivoを1.5.56へ更新。実際の所属グループの地図で自動案内を確認。設定ボタンから`com.vivo.permissionmanager/.activity.BgStartUpManagerActivity`の「自動起動」画面が開き、戻る操作、案内の終了、アプリ設定から再表示できることを確認。OSの許可・無線設定は変更していない。
- Mi 9Tは利用者が切り離したため、この新しい案内ボタンは未確認。自動起動を手動許可した後の再起動・圏外共有は直前の1.5.55試験で成功している。
- エミュレーターで新規3件成功：参加条件・権限操作待ちの後の表示、確認後の抑制と手動再表示、メーカー設定が起動拒否された際の代替、英語の手順と終了。既存GroupSetupUxTestの2件も成功。
- 最初の日本語UI試験は通常アプリのPlanLocalizationを使用しておらず、ダイアログが端末の既定言語で表示されて失敗した。通常アプリと同じ言語処理へ修正し、3件成功を確認。製品コードの変更で試験を回避していない。
- エミュレーターの試験APKインストールが停止したため、AVDのみ保存データを消さずコールド起動して復旧した。実機は再起動していない。

ローカル証拠：`.implementation/autostart-guide-build.log`、`autostart-guide-emulator-final.log`（新規3件）、`autostart-guide-emulator-wait.log` / `autostart-guide-emulator.log`（初期失敗を含む経過）、`autostart-vivo-result.json`、`autostart-guide-vivo.png`。端末固有のログ・画像は公開しない。

参考：[Xiaomiの自動起動設定](https://www.mi.com/uk/support/faq/details/KA-497677/)、[Androidの権限宣言](https://developer.android.com/training/permissions/declaring)、[Notifee開発元のメーカー設定画面一覧](https://docs.page/invertase/notifee/react-native/android/background-restrictions)。メーカー画面名は非共通のため、存在・起動可否の失敗を処理する。
