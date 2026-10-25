# Androidの既存衝撃通知：調査と実機確認

調査日：2026-10-24。対象は「倒壊などでスマホが受ける大きな衝撃」。利用者の希望は、OSや端末の既存検知結果をPlanDへ通知してもらい、アプリ側で加速度を常時解析しない方式。

## 結論

**この調査後の更新：利用者の明示依頼で通常版1.5.8へ位置記録中の受信・30日保存を実装し、16:41:51 JSTに画面消灯中の新規通知1件を保存した。[実装・検証結果](2026-10-24-vivo-drop-recording.md)を参照。以下の「通常アプリ未実装」は、通常版への実装前の調査時点を示す。**

**最新結果：当該vivo実機では既存drop_depth通知をPlanDで受信できた。さらにメーカーの既存許可文字列を含む受信処理名で、PlanD名が許可されていない比較条件でも登録とキャッシュ通知の受信に成功した。これはリストへの恒久追加ではなく、このファームウェアの部分一致判定を利用した試験用の迂回経路である。通常アプリへの組込みはまだ行っていない。**

実際の新規通知は15:21:01 JSTに1件、OSイベント時刻からコールバック取得まで約5.6msで受信した。値2.29の単位・意味や倒壊の判定精度は未確認。バックグラウンドでは、位置記録の常駐サービスとvivoの「自動起動」オンの組合せで約4分の消灯中に接続凍結が見られなかった。ただし消灯中の新規通知、長時間稼働、再起動後の動作は未検証。

利用者の停止指示以降、再起動していない。受信処理名の比較後は、直前の一時的な「PlanDのみ」の実行時リストへ戻した。メーカー既定の5項目に復元済みという意味ではない。元のシステム設定ファイルは未変更。「自動起動」はオンを維持し、試験用のDoze除外は解除済み。以下は各時点の調査履歴であり、以前の「未受信」「復元済み」を現在の状態と混同しない。

同日追加調査で、登録失敗の原因となるvivo固有の許可リスト判定を特定した。詳細は末尾の「登録拒否の原因を追跡した結果」を参照。当初記録の「拒否理由は未特定」を更新する。

さらに利用者の「whitelistは変更できないの？」を受け、PCから内部更新処理を呼べることを確認した。ただし追加後も登録はfalseだった。後述の追加・削除処理に不整合が見つかり、試験後の削除が別の実行時項目に影響した可能性がある。利用者の明示許可で再起動を実施し、今回のメモリ上の変更を解消。起動IDの変化、元の設定ファイルのハッシュ一致、OSの落下センサー接続を確認した。実行時リスト全項目の直接読出しではない。以下の「端末設定を変更していない」は、この更新試験より前の範囲を示す。

公開仕様に基づく汎用の通知経路は確認できなかった。末尾の受信処理名による経路は、接続中のvivo/iQOO V2417Aの非公開実装に依存する。すべてのAndroid端末で利用できるものではない。

衝撃通知の本実装は追加していない。位置記録開始に伴う加速度校正の自動起動は無効化し、明示的なdebug試験だけに限定した。独自の閾値、歩行検知の衝撃への読み替え、車両事故判定の倒壊への転用は行っていない。画面変更はない。

## 比較した経路

| 経路 | 公開資料で確認できる機能 | 今回の適合性 |
|---|---|---|
| AndroidのSignificant Motion | 歩行・自転車・移動車両などによる位置の大きな変化を一度通知 | 衝突・衝撃の通知ではない |
| Google Activity Recognition | 移動状態とその遷移の通知 | 衝撃を表す公開イベントは確認できない |
| PixelのPersonal Safety | 激しい自動車衝突を検知して緊急通報・共有 | 調べた公式資料では一般アプリ向け受信APIを確認できない。対象も車両事故で、今回の実機には同アプリなし |
| Android Context Hub / CHRE | 低消費電力プロセッサ上で処理するnanoapp | ContextHubManagerはシステムAPIで、任意の第三者アプリには開放されていない |
| vivoのdrop_depth | 実機に独自のon-change・wake-upセンサーあり | 公開の値の定義なし。通常アプリによる登録が実機でfalse |
| DriveQuant DriveKit | crashDetectedコールバックあり | SDKが走行中の位置・センサーデータを収集し、サーバー確認・通信・契約設定が必要。既存OS通知ではなく、倒壊用でもない |
| Dolphin MOVE | 走行中の衝撃検出とサーバーへのイベント | センサー・位置を使う走行SDK。今回の希望する通知経路ではない |
| Wear OS / Samsung Health Services | 対応する腕時計でFALL_DETECTEDを受信 | 腕時計での人の転倒検知。スマホ単体の衝撃とは対象・機器が異なる |
| Huaweiの転倒通知 | 指定の業務向け腕時計で通知を購読 | 対応機器が必要。今回のスマホ単体には適用できない |

標準SDK37.0のSensor定数も確認した。公開標準センサーには今回の用途の衝撃通知型は確認できなかった。一般の通知読取権限を使って画面上の通知文を推測する方式も、衝撃イベントの定義・確実な配信を確認できないため採用していない。

## vivo実機の結果

対象：V2417A、Android16、ファームウェア `PD2417_A_16.2.14.1.W10`。通常のdebugアプリ権限で公開SensorManagerを使用した。

- センサー名 `drop_depth`、vendor `vivo`、type `66561`、stringType `android.sensor.drop_depth`。
- reportingMode=1（on-change）、wakeUp=true。maximumRange=3.0、resolution=1.0だが、値の単位や意味は公開仕様を確認できていないため、メートルや加速度とは解釈しない。
- `registerListener` は3回の試験でfalse。例外なし、受信イベントなし。
- 最終試験はprocessImportance=100で、アプリが前面にあることを確認。同じプロセスから比較用の照度センサーの登録はtrue。単なる背面実行制限では説明できない。
- 当初の試験時点ではメーカー側の拒否理由・必要な承認条件は未特定。その後、末尾の追加調査で許可リスト判定を特定した。権限回避や非公開APIの呼出しは試みていない。
- センサー登録を調べただけで、端末の落下・衝撃の発生・緊急通報は行っていない。

端末内の読み出し可能なシステムライブラリをローカルで調査したところ、`com.vivo.sensor.SDC.mems.drop.DropDepth` がセンサー値を受け取り、落下の区分・回数などの内部記録を処理していた。これは落下統計に関する機能という推測を支えるが、第三者アプリ向け仕様や「倒壊の衝撃検知」を証明しない。システムファイル、解析物、端末の生ログはGit管理外の `.implementation/` のみに保持し、配布しない。

## 再確認用の試験

試験コード：`android/app/src/androidTest/java/io/github/nevaseme/plandmk1/VendorImpactProbeTest.kt`。

`vendorImpactProbe=true` の明示指定時だけ実行する。`probeForeground=true` では画面起動を試み、登録時のprocessImportanceを記録する。起動指示だけで前面に来たとは扱わない。最終試験では外部から通常の画面起動操作も行い、importance=100を確認した。

候補を列挙し登録可否を記録する。登録できた場合だけ5秒間、最大32イベントを取得し、解除する。値の衝撃判定はしない。照度センサーは登録可否の比較後すぐ解除する。結果はアプリ内の非公開ディレクトリに保存し、通常アプリの記録・共有機能へ流さない。加速度センサーは登録しない。

実機ログ：`.implementation/vendor-impact-probe-phone.log`、`vendor-impact-probe-foreground.log`、`vendor-impact-probe-adb-front.log`。最後の `OK (1 test)` は試験コードの実行成功であり、落下センサーの登録や検知の成功を意味しない。

## 自動校正の修正と検証

`PlanDApplication.motionCalibrationEnabled` の初期値はfalseで保存しない。PositionServiceはdebug版かつこの値がtrueの場合だけ校正を開始する。有効化は端末試験コードのみ。通常の位置記録がセンサーを開始しないことを失敗から成功へ変わる回帰試験で検証した。

単体試験104件、静的検査、通常・試験用APKのビルドが成功。API37エミュレーターのMotionDeviceTest全6件が成功。修正後の1.5.7をV2417A実機へ上書きインストール成功。実機での測定精度・消灯時継続性・電池負担は未検証であり、この結果から衝撃検知の精度を主張しない。

別担当による読取専用レビューで、校正の初期無効化・debug限定・試験の設定復元・登録失敗と検知成功の区別を確認。修正指摘なし。既存の建物評価や校正基盤全体の再レビューではない。

## 公開の根拠資料

- [Android Motion sensors](https://developer.android.com/develop/sensors-and-location/sensors/sensors_motion)、[AOSP Sensor types](https://source.android.com/docs/core/interaction/sensors/sensor-types?hl=en)：Significant Motionの用途とセンサーの定義。
- [ActivityRecognitionClient](https://developers.google.com/android/reference/com/google/android/gms/location/ActivityRecognitionClient)：公開の行動認識API。
- [Google Pixelの緊急時機能](https://support.google.com/pixelphone/answer/7055029?hl=en)：自動車事故検出の対象・動作。
- [AOSP Context Hub](https://source.android.com/docs/core/interaction/contexthub)：システムAPIと第三者アプリの制約。
- [DriveKit Crash detection](https://docs.drivequant.com/trip-analysis/android/crash-detection)：コールバック、走行中の動作、サーバー確認と通信条件。
- [MOVE Services](https://docs.movesdk.com/move-platform/move-services)：走行中の衝撃検出とデータ処理。
- [Samsung Events Monitoring](https://developer.samsung.com/health/blog/en/events-monitoring)：腕時計のFALL_DETECTEDとPassiveListenerService。
- [Huawei 転倒イベントの購読](https://developer.huawei.com/consumer/en/doc/huaweihealth-Guides/subscribe-fall-detect-event-0000002342757126)：対象腕時計の制約。

参考：iPhoneには[Apple SafetyKit](https://developer.apple.com/documentation/safetykit?language=objc)による第三者アプリへの通知経路がある。ただしAppleの承認・利用者の許可を伴う激しい自動車衝突のイベントであり、Androidや一般の倒壊衝撃の通知ではない。

## 登録拒否の原因を追跡した結果（同日追加調査）

利用者提供の `Device Info.txt` を参照し、同じV2417A・Android16/API36・ファームウェアであることを確認。端末情報全文にはネットワーク情報・導入済みアプリが含まれるため、Git管理対象外にした。アプリのコンパイル対象API37とは区別する。

### 確認した拒否条件

1. 実機から読み出した `/system/etc/SensorConfig/SensorNativeList.json` に `dropDepthWhiteList` が存在する。初期リストは次の5項目で、PlanDは含まれない。
   - `com.qualcomm.qti.sensors`
   - `com.qualcomm.sensors.qsensortest`
   - `com.iqoo.engineermode`
   - `com.vivo.services.touchscreen`
   - `com.vivo.sensor.SDC`
2. 実機の `/system/lib64/libsensorservice_ex.so` を静的解析した。`VivoFusionImpl::invalidEnable` はセンサーtype=66561を専用の判定へ分岐させる。`ListManager::initJsonList` が `dropDepthWhiteList` として初期化するリストを使い、接続名・パッケージ名に該当するものがなければ登録を拒否する。通常の実行時権限を問い合わせて許可する分岐は、この落下センサー用判定にはない。
3. `libsensorservice.so` の登録経路は上記判定の拒否時にエラー値−38を返す。したがって、センサーが列挙できること・`perm: n/a`であることだけでは登録可能と判断できない。これはHALの標準requiredPermissionとは別のメーカー独自制限である。
4. 再度、設定を変えず公開SensorManagerから登録試験を実行し、drop_depthはfalse、照度はtrueを確認。今回の再試験のprocessImportanceは125で、前回のimportance=100の前面試験と区別する。拒否メッセージ自体は取得できたlogcatに出ておらず、原因の特定は実機バイナリ・設定の静的解析と再現結果の照合による。
5. OS自身の `com.vivo.sensor.SDC.common.RegisterSensor`（uid1000）はdrop_depthへ接続中だった。センサー全体が存在しない・無効である状況とは異なる。

許可リストの項目は単純な「ユーザーが許可ボタンで選べるアプリ一覧」とは異なり、OS内部の接続名も含む。上記は仕組みの説明であり、別アプリや内部処理への偽装を実装したものではない。

### 権限・設定で変更できる範囲

| 候補 | 判断と根拠 |
|---|---|
| 身体センサー／身体活動の許可 | 本実機ではAppOpsがignoreだったが、drop_depthの標準requiredPermissionは空。独自許可リストの照合を通す条件ではないため、無関係な権限は追加・変更しなかった |
| 高頻度センサー権限 | 200Hzを超える取得向けで、今回の低頻度登録と独自リストの制限を解決しない |
| 自動起動・バックグラウンド実行・電池制限解除 | 登録後の生存性とは関係し得るが、前面で再現するリスト拒否を解除しない |
| 通常のセンサー設定 | 独自リストを編集する公開API・利用者向け設定は確認できなかった |
| メーカー設定の更新 | OSにはListUpdateという非公開の設定更新処理がある。システム設定とメーカー更新用ファイルを扱う経路で、一般アプリの権限要求とは異なる。今回、初期設定はroot所有・0644。更新用ファイルは調べた2か所に存在しなかった |
| メーカーによるPlanDの許可 | 現実的に検討するには、vivo側での連携可否と許可、イベント値・時刻・誤検知・対応機種の仕様提供が必要。これが提供されるとの確認はない |

設定更新用パスは `/data/bbkcore/SensorUpdateNativeList.json` と `/data/SensorConfig/SensorUpdateNativeList.json`。これらのファイルを作成・改変したり、ブートローダー解除・root化・メーカー識別子の偽装を行ったりはしていない。読み取りで確認できた情報を超えて、すべての非公開更新経路が利用不可能と証明したものではない。

### 他の候補を端末情報から再確認

- `significant`：標準の大きな移動検出で、衝撃の意味を持たない。
- `raiseup`・`putdown`・`vivomotion`・`amd`・`activity_wk/nwk`：一覧には存在するが、名前だけから落下や衝撃を判定しない。汎用の衝撃値として利用できる公開仕様は確認できない。vivomotionなどにも別のメーカー許可リストがある。
- `VIBRATION_CLASSIFICATION`：OS実装に名前と独自許可リストを発見したが、この端末の公開センサー一覧には該当型がない。振動分類をスマホの事故検知へ転用できる根拠もない。
- ST製 `lsm6dsve_acc`：近い名称の[LSM6DSV公式資料](https://www.st.com/en/mems-and-sensors/lsm6dsv.html)には低消費電力の自由落下・wake-up・タップ割込みがある。ただし、実機のLSM6DSVEと完全に同一仕様であるとは確認できていない。またチップの割込みとAndroidアプリへ公開される通知は別で、この端末の一般アプリからそれらを設定・購読できる経路は見つからなかった。
- `TYPE_MOTION_DETECT`：標準仕様でも数秒続く動きの通知で、瞬間的な衝撃とは異なる。本機の列挙一覧にもない。[Android SensorEvent](https://developer.android.google.cn/reference/android/hardware/SensorEvent)
- 端末一覧の緊急発信用アプリの存在だけでは、衝撃通知を他アプリへ配信する根拠にならない。今回、緊急発信は操作していない。

Context7でAndroid公式のHAL仕様・requiredPermission・メーカー独自センサーの扱いを照合した。メーカー独自型は標準型のように動作仕様や互換性が保証されない。[AOSP Sensors](https://source.android.com/docs/core/interaction/sensors)、[HAL interface](https://source.android.com/docs/core/interaction/sensors/hal-interface)

### 実装への反映と検証範囲

通常の権限・設定を変えて登録できる経路は確認できなかったため、衝撃通知の本実装は追加しなかった。通常の位置記録で加速度校正を開始しない前回の修正を維持する。本追加調査でAndroidソース・端末設定・APKは変更していない。新しい結果は公開SensorManagerの登録試験1件の再実行と静的解析であり、前回の単体104件・エミュレーター6件の再実行ではない。

解析物と生ログは `.implementation/vendor-sensor-research/`、`.implementation/vendor-impact-cause-probe.log` に保管し、Gitには含めない。再照合用SHA-256：

| ファイル | SHA-256 |
|---|---|
| libsensorservice.so | `9031532d7c4ed1f367b107b6e4a5335fa321d6e3640ea12ded0e4b0dddcd552d` |
| libsensorservice_ex.so | `9ee07ed599eda040f401d03301d553ebfa5df0c986f2d91c5ebdc9e475f71b10` |
| SensorNativeList.json | `d4ddb9ec3a7cccfba36db56f6b8d2ba2c2e51a0d25303cd7d304ae88637b2b3d` |

解析位置（上記バイナリの仮想アドレス）：type66561の分岐先 `0x58d40`、リスト読込先オフセット `0x108`、拒否時の処理 `0x58ff4`、サービス側判定呼出し `0x33840`、エラー分岐 `0x33e58`。機種・OS更新で変わり得るため、アプリにはこれらの値を組み込まない。

## 許可リスト更新の追加試験（利用者の追加質問への対応）

ADBのshell権限（uid2000、SELinux Enforcing）では、元のシステム設定と `/data/bbkcore` に対する書込み可否検査はいずれも不可だった。一方、実機のSensorServerに許可リスト更新用の内部呼出しが存在し、PCから応答を受け取れた。通常の権限画面から変更できることとは別である。

PlanDの名前だけを追加する操作と、登録試験後に同じ名前を削除する操作を2組実施。返り値はいずれも1だったが、登録結果はfalseのまま。2回目はprocessImportance=100を確認した。実機のListManager::updateListは項目変更の成否にかかわらず1を返す経路があり、この応答だけでは追加成功を証明できない。

後続の静的解析で、addSingleItem（0x78ac0）はstrcmpが一致したときに次項目へ進み、不一致のときに「既に存在する」の処理へ進むように見える。deleteSingleItem（0x78c20）も一致時に次へ進み、不一致の項目を削除対象とするように見える。この解釈では、試験後の削除2回で初期リストの先頭2項目が実行時に削除された可能性がある。削除を逆操作として扱った判断は不十分だった。

このため追加の変更操作を停止し、利用者に状況を説明した。利用者が「今、再起動して復元する」と明示許可し、再起動して未変更の元ファイルを読み直した。保存データや設定ファイルの削除は行っていない。通常の衝撃検知実装へこの内部更新処理を組み込んではいない。

独立した読取専用レビューで、実際のELF再配置先がstrcmpであること、比較条件の逆転、dropDepthWhiteListが同じリスト領域（+0x108）を使うことを確認した。initJsonListは元のシステム設定からこの領域を初期化し、その後にメーカーの更新ファイルを確認する。再起動は今回の実行時変更を破棄するが、リスト全体の読出しAPIは確認できていないため、起動時のメーカー側処理まで含めた全項目一致を直接検証できるとは言わない。

ローカル記録：`vendor-whitelist-add.log`、`vendor-whitelist-added-probe.log`、`vendor-whitelist-remove.log`、`vendor-whitelist-restored-probe.log`、`vendor-whitelist-foreground-*.log`。ファイル名のrestoredは当初の試験意図であり、許可リスト全体の復元を検証した意味ではない。

### 再起動後の確認

- `sys.boot_completed=1`、起動IDが再起動前と異なることを確認。
- 元のSensorNativeList.jsonはSHA-256が調査開始時と一致。
- `/data/SensorConfig/SensorUpdateNativeList.json` と `/data/bbkcore/SensorUpdateNativeList.json` は再起動後も存在しない。
- drop_depthへのOS内部SDC（uid1000）の接続が起動し直していることを確認。
- 復元の根拠はプロセスの再生成と変更していない設定の再読込であり、非公開の実行時リスト全項目を取得して比較したものではない。

記録：`.implementation/vendor-whitelist-before-reboot-id.txt`、`vendor-whitelist-after-reboot-*.txt`。本機・本OS版では更新処理の返値を根拠に許可リスト変更を実装できない。メーカーの修正・連携仕様が必要であり、root化やOS改変による代替は未調査・未実施。

## 利用者指定の「空リスト→PlanDのみ」試験

利用者は「再起動すれば元に戻せるなら、一度リストを空にしてPlanDだけを追加し、受信登録できるか試して。試験後は再起動して元に戻して」と明示指示した。

対象ファームウェアとlibsensorservice_ex.so、SensorNativeList.jsonのSHA-256が解析時と一致し、メーカーの更新用ファイル2か所が存在しないことを確認。試験前の登録はimportance=100でfalse、照度の比較用登録はtrueだった。

落下センサー用dropDepthWhiteListだけを対象に、前述の不整合を踏まえ既存5項目を取り除く呼出しを5回行い、空のリストへPlanD名を追加する呼出しを1回行った。他のセンサー用リストやディスク上の設定は変更していない。

| 条件 | プロセス状態 | drop_depth登録 | 比較用の照度登録 | 待受結果 |
|---|---|---|---|---|
| 変更前 | 前面（100） | false | true | 登録できず |
| 空リストからPlanDのみを追加した後 | 前面（100） | **true** | true | 5秒間で0イベント、例外なし |
| 試験後に再起動 | 前面（100） | false | true | 元の登録不可状態へ戻った |

更新呼出しの応答だけでなく、PlanDの公開SensorManagerからの実際の登録成功を確認した。センサーを解除してから、finally処理で端末再起動を要求し、正常に受け付けられた。登録成功は衝撃検知成功ではなく、端末は落としていない。既存のメーカー用許可を維持できる方法でもないため、このリスト変更を通常アプリへ組み込んではいない。

ローカル記録：`.implementation/vendor-empty-baseline-probe.log`、`vendor-empty-pland-only-probe.log`、`vendor-empty-mutations.log`、`vendor-empty-before-boot-id.txt`。

### この試験後の復元確認

再起動後に起動完了と起動IDの変化を確認した。元の設定ファイルは開始時と同じSHA-256で、メーカー更新ファイル2か所も存在しないまま。OS自身のSDC（uid1000）がdrop_depthへ接続していることを確認した。ロック解除後、変更前と同じimportance=100の前面状態で再試験し、PlanDの登録はfalse、照度の比較用登録はtrueへ戻った。今回の一時許可が再起動で解消されたことを確認できた。実行中の許可リスト全項目を直接取得して比較したものではない。

記録：`.implementation/vendor-empty-after-*.txt`、`vendor-empty-restored-probe.log`。3条件の試験はいずれも試験コード自体が正常終了した。Androidソース・APKの更新はなく、既存の明示指定プローブだけを使用した。

## 実際の落下通知の受信試験

### 3分の自動終了を使った先行試験

試験APKだけを更新し、イベント時刻・コールバック取得時刻・値の記録を追加した。利用者が落下操作を報告したが、PlanDの受信は0件だった。一方、OSのsensorserviceには15:14:11.649のdrop_depthイベント（先頭値0.71）が存在した。操作後の読取時にはPlanDのセンサー接続がfrozenと表示され、前面に戻すと解除された。スナップショットだけではイベント発生瞬間の凍結状態や停止開始時刻まで証明できない。2回目の操作報告後もOSの新規イベントとPlanD受信は増えなかった。

この試験は待受開始から180秒で自動終了し、15:16:48 JSTに再起動を要求した。最後の落下操作から十分な待受を保証しておらず、利用者から指摘を受けた。後からOS記録を発見したことと通知の配信遅延を混同しない。試験方式を以下の明示終了へ変更した。

記録：`.implementation/vendor-event-live.log`、`vendor-event-result.json`、`vendor-event-active-sensors.txt`、`vendor-event-after-second-action.txt`、`vendor-event-reboot.txt`。

### 時間で打ち切らない再試験

`VendorImpactProbeTest`に明示指定の`probeManualStop=true`を追加。このモードでは実行ごとに作成する停止用パスにファイルが置かれるまで待受を継続し、時間・最初のイベント受信・ADB切断を終了条件にしない。既定の短い登録確認モードとは別である。10秒ごとにプロセス状態と受信数を記録し、イベントは受信時にアプリ専用領域へ追記する。起動用スクリプトに終了・再起動操作は含めない。通常アプリや画面の変更はなく、加速度の連続測定も行わない。

再起動後の元の設定ハッシュと更新ファイル不在を確認し、変更前はimportance=100、drop_depth=false、照度=trueだった。その後、利用者が認めた一時的なPlanDのみのリストへ変更し、登録成功・配信の凍結がない状態を確認してから操作の合図を出した。利用者は頭ほどの高さから実施したと報告した。実際の高さ・着地点は測定しておらず、推奨手順の実施と同一視しない。追加の落下操作は求めていない。

| 項目 | 観測結果 |
|---|---|
| 受信 | PlanDのSensorEventListenerで1件受信 |
| 受信日時 | 2026-10-24 15:21:01.505 JST |
| OSイベントの経過時刻 | 241367048818 ns |
| コールバック取得時の経過時刻 | 241372656628 ns |
| 両時刻の差 | 5.60781 ms。物理的な落下開始からの検出時間ではない |
| 値 | 先頭2.29、残る15要素は0。単位・意味は未確定 |
| 古い通知か | イベント時刻は登録開始より後であり、登録前のキャッシュではない |
| 保存 | アプリ専用ファイルをPCへ読み出し、通知ログと照合済み |
| OSとの一致 | sensorserviceのdrop_depth時刻と先頭値が一致 |

これにより、当該実機・ファームウェア・一時許可・前面状態で、既存の落下センサーからアプリへ実通知を届けられることを確認した。倒壊の識別、任意の衝撃の検出、バックグラウンドや画面消灯時の継続受信、他機種の互換性、既存メーカー項目を維持した導入方法は未検証・未解決である。

記録：`.implementation/vendor-manual-build.log`、`vendor-manual-baseline.log`、`vendor-manual-live.log`、`vendor-manual-device-events.jsonl`、`vendor-manual-before-action.txt`、`vendor-manual-after-action.txt`。端末由来の生ログはGitへ含めない。

## 常時受信と恒久的な許可の追加調査

利用者は再起動を止め、常時受信と恒久的な許可を調べるよう指示した。以降は追加の落下操作や再起動を行っていない。手動待受は受信結果を確認した後、明示的な停止ファイルで終了した。

### 画面消灯時の接続維持

試験コードに`probePositionService=true`を追加し、PlanDの既存の位置記録用フォアグラウンドサービスを使用した。位置取得元は待機だけを行う試験実装に置き換え、位置情報を取得・保存せず、加速度校正も無効にした。既存の位置記録が動作中なら試験を拒否し、終了時には試験用サービスを停止して取得元を戻す。

| 条件 | 観測 |
|---|---|
| 常駐サービスあり、vivo自動起動オフ | 消灯後約30秒でセンサー接続がfrozen。サービス自体は動作中 |
| 上記＋Android標準のDoze除外 | 凍結を解消できず。試験後に除外を解除 |
| vivoのバックグラウンド電力使用 | 元から「許可する」。変更なし |
| 常駐サービスあり、vivo自動起動オン | 約4分の消灯中、複数のスナップショットで接続が凍結せず、サービス・試験処理も継続 |

自動起動オンの確認後、15:36頃から15:40頃まで観測。端末の電源状態はAsleep、位置記録サービスはisForeground=true、試験のheartbeatではpositionActive=trueだった。条件比較を含む試験全体は731.916秒で正常終了。追加のイベントは発生しておらず、登録時に受け取った1件は15:21の通知のキャッシュ（beforeRegistration=true）である。消灯中の新規通知や無期限の常時受信が実証できたという意味ではない。USB給電中・試験ランナー使用という条件も残る。

Androidは[バックグラウンドのセンサー利用制限](https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview)を定めている。[wake-upセンサー](https://source.android.com/docs/core/interaction/sensors/suspend-mode)であることはSoCの起床に関する性質であり、メーカーのアプリ凍結からの除外を意味しない。[vivo公式の案内](https://kefu.vivo.com.cn/robot/imgmsgData/2616a9cd7cd64a5083a264d16e5767da/index_1.html)も自動起動とバックグラウンド電力使用の設定を扱っている。本機の静的解析でもFrozenPackage処理からセンサー凍結を呼ぶ経路を確認した。

記録：`.implementation/vendor-background-fgs.log`、`vendor-background-screenoff-1.txt`、`vendor-background-idle-exempt-screenoff.txt`、`vendor-background-autostart-confirm-ui.xml`、`vendor-background-autostart-screenoff*.txt`、`vendor-background-autostart-final-services.txt`、`vendor-background-autostart-final-power.txt`。自動起動はオンのまま。連動起動や他のアプリの設定は変更していない。

### 保存される許可リストを追加できるか

システムの元ファイルは書込み不可で、更新元の`/data/bbkcore`もshellから書込み不可。更新先`/data/SensorConfig`は存在しなかった。メーカーの更新処理は、更新元JSONの追加・削除を適用したうえで更新先へコピーする構成だった。起動時の更新ファイル読込も、先に確認した比較条件の不整合がある同じListManager.updateListを使う。単に更新用JSONを用意すれば既存5項目を維持して追加できる、とはいえない。

関連するUnifiedConfigProviderとUnifiedConfigServiceにはsignatureOrSystemのメーカー権限が指定されていた。shellからの読取り問い合わせも権限不足で拒否された。権限画面で与えられる種類の権限ではない。書込み、更新通知の送信、root化、OS改変は行っていない。

この調査では、既存項目を維持し、再起動後も残る許可リスト追加方法は確認できなかった。実行時の内部更新は保存処理を持たず、再起動までの一時変更である。未知のメーカー経路がすべて存在しないという証明ではない。

記録：`.implementation/vendor-sensor-research/persistence-native.asm.txt`、`change-item.asm.txt`、`network-update-classes.txt`、`abe-manifest.txt`。これらの解析物やメーカーAPKはGitに含めない。

### リスト追加を必要としない受信処理名の比較

当該ファームウェアの許可判定は、各許可文字列についてパッケージ名と受信処理名をString8::findで検索する。完全一致ではなく部分一致だった。関数呼出し先のELF再配置も照合した。

これを調べるため、試験APK内に`io.github.nevaseme.plandmk1.vendorprobe.com.vivo.sensor.SDC.DropNotificationListener`を追加した。PlanD自身の名前空間・UID・パッケージを保ったまま、既存項目`com.vivo.sensor.SDC`を受信処理のクラス名に含めている。メーカーのコンポーネントでも、公開された連携APIでもない。

比較中だけ既知の1項目リストを「PlanDのみ」から「com.vivo.sensor.SDCのみ」へ切り替え、finallyで試験前のPlanDのみへ戻した。各試験は前面状態importance=100で行った。

| 実行時リスト | 受信処理 | 登録 | 通知 |
|---|---|---|---|
| PlanDのみ（開始時） | 通常名 | true | 比較の基準 |
| com.vivo.sensor.SDCのみ | 通常名 | false | なし |
| com.vivo.sensor.SDCのみ | 上記の名前 | **true** | 15:21のキャッシュ通知を1件受信 |
| PlanDのみ（比較後） | 通常名 | true | 試験前の登録可能状態へ復帰 |

新しい受信処理で受け取ったイベントはelapsedNanos=241367048818、beforeRegistration=trueであり、新たな落下による通知ではない。照度センサーの比較用登録も成功した。4回の登録試験はいずれも正常終了し、試験APKのビルド・端末への更新も成功した。

メーカー初期リストにも同じSDC文字列が含まれるため、静的解析と比較結果から、リストを変更せず利用できる可能性がある。ただし今回は初期5項目の状態や端末再起動後には試していない。名前を変える方法と自動起動オンの消灯条件を組み合わせた試験もまだである。OS更新、名前の難読化、端末差で成立しなくなる可能性があり、製品全体の常時受信を保証するものではない。

記録：`.implementation/vendor-listener-build.log`、`vendor-listener-current-baseline.log`、`vendor-listener-sdc-normal.log`、`vendor-listener-sdc-named.log`、`vendor-listener-current-restored.log`、`vendor-listener-comparison.json`。試験後の起動IDは15:16の再起動後と同一、元のSensorNativeList.jsonのハッシュも一致。試験用常駐サービスは終了済み。通常アプリへの通知保存・位置との関連付け・衝撃判定は今回追加していない。
