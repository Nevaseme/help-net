# 最新版の通信・iPhone地域集計の確認

対象: Android 1.5.52、公開PWA 1.6.26。

## 準備と実施状況

- vivo V2417A と Mi 9T のUSB接続を確認。vivoは1.5.52。Mi 9Tは開始時1.5.48だったため、1.5.52と対応する通信試験APKをDownloadへコピーし、利用者へ手動更新を依頼した。
- Mi 9Tの位置情報権限は許可済み。両端末に未復元の `qa-safety-backup.json` はなかった。
- 通信試験は `OfflineSafetyRadioDeviceTest`。通常のSharingService/NearbyTransport/Roomを使用する。専用の架空グループで、双方向の無事・通報、消灯中のSOS・通報、Bluetooth切断中に保存した無事・通報の自動再接続後配送、別DB接続からの永続保存確認を行う。
- 無線設定は利用者が手動操作する。試験中は両端末のデフォルトネットワークを監視し、インターネット経由の送信を圏外共有成功として扱わない。終了時には試験がグループ選択を復元する。
- iPhone確認専用の「地域集計確認1103」を作成。架空住民12人、通報3件、負傷あり2人、救助済み2人のシナリオを公開PWAで確認し、利用者へ参加・地域集計の確認を依頼した。

## 完了した事前試験

公開PWAに対する既存 `lab/test/community-demo.browser.mjs` が成功。

- グループ名・パスワード → 名前 → 安否回答 → 地図。
- 聴衆・管理者2人と架空住民12人、全員の建物紐付け。
- 町内会3地区、負傷者2人、救助済み2人、通報3件、不足資材。
- 負傷情報の更新、日本語・英語、320px幅。
- 地域集計を開いたまま別端末の更新を反映。
- 発行済みレポートが圏外再起動後も同一内容で残る。
- 圏外で保存したSOSを通信復帰後に自動共有。
- 次のデモに前回の通報・負傷情報を混ぜない。

これはPCの専用Chromeによる事前試験。iPhone実機・Android実無線の結果の代用ではない。

証拠: Codexの可視化フォルダー `community-current-public/result.json` と画面画像。普段のブラウザー状態は使用していない。

## 実機で確認できたこと

両端末を1.5.52に揃え、`safety-radio-1793693427`で実施。

- 双方向の無事回答と通報: 両端末で `initial-pass`。
- 双方向の消灯中SOS回答と通報: 両端末で `screen-pass`。受信判定時は両方とも `screenOn:false`。通常の共有サービスの通知が存在することも試験で検査。
- vivoのBluetooth OFF後、両端末の接続数が0。新しい無事回答と通報をそれぞれ保存し、相手へは届いていないことを確認して `queued`。
- Bluetooth ONの操作前に再接続待ちの180秒を開始してしまい、説明・利用者とのやり取り中にタイムアウトした。総合JUnit結果は両端末とも失敗。これは再接続成功の証拠にはしない。元のグループ選択はfinallyで復元された。次回は利用者のBluetooth ON完了後に `reconnect` を送る。
- 証拠: `.implementation/safety-radio-1793693427/` の各端末の試験ログ、フェーズ、ネットワーク監視ログ。無線設定はこの実行では変更していない。
- iPhone: 利用者が専用グループへの参加、無事回答、町内会3つ・合計14人・負傷者2人・救助済み2人・通報3件・英語資材の表示を確認した。

## 追加で分かった制限

- 現状のSharingService.readyはBluetooth ONを条件にしている。OFFでSharingSession.setRadiosReady(false)がNearbyTransport.stopを呼び、SDKのstopAllEndpointsまで行う。Bluetooth OFF試験はこのアプリ側制御の停止・再開を試すものであり、SDKがWi-Fi経由の通信を維持できるかの検証ではない。
- 再起動用Receiverは位置記録の復帰を呼ぶが、近距離共有を直接開始する処理はない。共有のSTART_STICKYによるプロセス再生成と端末再起動は区別する。再起動後の実機での自動復帰は未確認。
- 地域集計の負傷編集は管理者専用ではなく、進行中の訓練・災害のグループメンバーに許可されている。今回のiPhoneも一般メンバー。
- レポートはHTML/JSON保存のみ。利用者からiPhoneで保存HTMLをタップするだけでは表示できないとの指摘あり。PDF出力・アプリ内即時閲覧は未実装。

## 継続中

- vivo・Mi 9Tで切断後の自動再接続を再試験。両端末のBluetooth ON・ロック解除を依頼済み。
- iPhone実機でレポート発行・圏外再起動・復帰後共有。利用者へ圏外再起動後の集計・発行済みレポートの確認を依頼済み。

当日の手順は [vivo管理者・PWAメンバーのデモ手順](venue-demo-vivo-pwa-guide.md) を最新版に更新した。APK配布QRは今回の通信試験とは別であり、未確認の配布先は案内しない。

## 1.5.53：Bluetooth条件と端末起動時の修正

利用者からの指示により、同じグループの自動共有を次のように修正した。

- BluetoothとWi-Fiのうち一方が有効なら共有サービスを維持する。両方OFFまたは必要権限の不足時には待機し、復帰後は自動再開する。
- 無線切替時は周辺端末の検索を更新するが、接続済みの相手をアプリ判断で切断しない。
- 自動共有の広告・検索失敗では、接続済みの通信と受信処理を保持する。検索のみ5〜60秒の間隔で再試行する。SDK自身が切断した場合は通常の再接続処理へ進む。
- BOOT_COMPLETED / MY_PACKAGE_REPLACEDから、以前有効だった現在のグループの共有を再開する。停止済み・別グループ・共有鍵なしの場合は開始しない。起動が拒否されても再開設定は消さない。
- 通信試験にBluetooth OFF＋Wi-Fi ONの観察段階と、切断中に安否・通報を保存してから実際に再起動する段階を追加した。操作待ちには終了時刻を設定しない。

### 完了したコード検証

- 修正前のNearbyLifecycleTest追加試験で、検索失敗が認証済み接続を消す問題を再現（接続集合が空になり失敗）。
- 修正後、同じ試験で接続・後続データ受信・検索再試行を確認。
- JVMテスト261件成功。エミュレーターのNearbyLifecycleTest 9件とSharingServiceTestの3件、合計12件成功。
- サービス試験ではBluetooth OFF / Wi-Fi ONでも受信・保存が続き、両無線OFFからの復帰を確認。起動Receiverでは、アプリが背景にある状態での再開、受信・保存、重複起動防止、停止済みグループの非再開を確認した。
- これらのサービス試験はGoogleの通信境界を模擬しており、実機のWi-Fi単独通信や実OS再起動成功の代用ではない。
- ローカル証拠：`.implementation/radio-fix-build.log`、`.implementation/radio-fix-emulator.log`。

### 実機試験の準備状況

- Mi 9TのDownloadへ`PlanD-1.5.53.apk`と`PlanD-communication-test-1.5.53.apk`をコピー済み。利用者の手動更新待ち。
- vivoは更新を実行したが端末側で拒否されたため、ロック解除・インストール許可の返答後に再試行する。
- 1.5.53の実無線・消灯・再接続・実再起動の結果はまだ出ていない。
- 実再起動後はアプリ起動や追加のinstrumentationより前に、通常サービスの起動とRoom内の相手由来の安否・通報を読み取る。試験用グループの復元情報は端末内に残し、検証後に元のグループへ戻す。

参考：Google公式の[Nearbyの通信方式](https://developers.google.com/nearby/connections/overview)、[接続構成](https://developers.google.com/nearby/connections/strategies)、[起動時のForeground Service条件](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)。SDKが無線を選択するため、Bluetooth OFF時の成功を測定せずにWi-Fi Direct成功とは呼ばない。

## 1.5.53の実機経過と1.5.54の追加修正

- 両端末の1.5.53で、ネット未接続・Bluetooth/Wi-Fi ONの安否と通報が双方向に届いた（`safety-radio-1793695484`、`safety-radio-1793695666`の`initial-pass`）。
- 最初の実行はビルド中に試験コードを編集したため、試験APKにWi-Fi・再起動段階が入っていなかった。元グループへ戻し、試験APKだけ再生成・更新。再生成APK内の`wifi-observation` / `reboot-queued`を確認した。
- 2回目はMi 9TのUSB切断でPC監視が停止。両端末の試験を終了して元グループへ復元した。Wi-Fi単独、消灯、自動再接続の合格とは扱わない。
- 利用者がMi 9TのBluetoothをOFFにしてもONへ戻ると報告。`dumpsys bluetooth_manager`のEnable logで、17:48:43〜17:49:50に利用者OFFの後、`com.google.android.gms`がONを要求していることを確認した。
- Nearby広告・接続要求の既定値BALANCEDから、`ConnectionType.NON_DISRUPTIVE`を明示する1.5.54へ変更した。Google公式では無線状態を変更しない設定。実端末での尊重は再確認が必要。
- SDKへ渡される設定の回帰テストは修正前に失敗（expected 2 / actual 0）、修正後に成功。1.5.54のJVM261件、NearbyLifecycleTest9件成功。
- PC側監視はUSB一時切断を記録して再接続待ちにし、勝手に試験中断しないよう変更した。USB不通期間のネット状態を確認済みとは扱わない。
- 1.5.54通常版を両端末へ配布・更新中。試験APKは1.5.53-v2を継続使用できる。

追加証拠：`.implementation/non-disruptive-red.log`、`non-disruptive-build.log`、`non-disruptive-emulator.log`。参考：[Google ConnectionType](https://developers.google.com/android/reference/com/google/android/gms/nearby/connection/ConnectionType)。

## 1.5.55：探索と接続保持をさらに分離

- 1.5.54でも両端末でBluetoothが自動ONへ戻った。Mi 9Tでは17:59:08/20の利用者OFF後、17:59:16/22にGoogle Play開発者サービスからON要求が記録された。NON_DISRUPTIVE指定だけでは今回の探索中の挙動を抑制できなかった。
- 1.5.55はBluetoothのTURNING_OFF通知で広告・探索を停止し、OFF中はその再試行と新しい接続要求を行わない。既存の接続・認証済み受信経路は保持する。ユーザーがBluetoothをONに戻したら探索を再開する。
- 起動時にBluetoothがOFFなら、Wi-FiがONでも探索を始めてBluetoothをONにしようとはしない。既存接続を保持できることと、新規探索を開始することを別条件にした。
- JVM261件とエミュレーター13件成功。追加のアダプター試験では、探索OFF中のSDK広告非呼出し、既存データ受信の継続、ON復帰時に既存接続を作り直さず探索再開することを確認した。
- 両端末を1.5.55へ更新済み。`safety-radio-1793696872`で安否・通報の双方向共有を確認し、Bluetooth OFF観察を開始する準備ができた。以降の実機結果は継続記録する。
- 証拠：`.implementation/discovery-pause-build.log`、`discovery-pause-emulator.log`、`mi9t-bluetooth-auto-enable-1554.txt`。
