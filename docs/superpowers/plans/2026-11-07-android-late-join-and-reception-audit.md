# Android途中参加の修正・受信調査

## Android 1.7.2 / code105

再インストールして既存開催へ参加した実機で、Safetyに「SOS saved on this device」があるのに他画面へ進めないことを確認。端末内の限定SQL照会では、本人の回答1件は選択中の開催とは別の開催に所属していた。履歴には古い未終了の実地震と現在の会場開催が併存していた。

初回同期は複数ページを順次保存する。以前の実装は、先に届いた古い開始記録へ回答可能な状態で移動し、同期完了後に現在の会場開催へ切り替わっていた。保存完了表示のキーも全開催共通だったため、別開催の成功表示が残った。

- 会場開催のメタデータがある場合は、その開催を優先して選ぶ。該当開始記録の取得前はLoadingを表示し、別の地震へ回答させない。同期エラー時はRetryを表示。
- 安否・SOSメモの保存結果をグループと開催ごとのキーへ変更。
- 一般の開催選択、未回答時の画面制限、初回京都案内、回答済みの更新時はSafetyに留まる動作は維持。
- 既存回答は書き換えず、現在の開催では本人に再回答を依頼。現在の開催へ回答を自動転記しない。

試験：`VenueStartupSelectionTest`は修正前に失敗し修正後は2件成功。`IncidentTest`も成功。実機`VenueLateJoinDeviceTest`は隔離fixtureを段階的に受信し、Loading→現在開催のSOS保存→初回案内→Map/People/Tasks/Safety→安否更新後Safety維持まで7.186秒で成功。初回の試験はPeopleとSafetyの見出しをタブと混同したセレクターで失敗し、クリック可能なタブに限定後成功。元のグループと言語を復元しMainActivityへ戻した。

Android DB全体のコピーは自動承認審査が拒否。その後利用者の明示許可があったが、結局コピーせず、端末内SQLiteの件数・開催の一致・時刻だけの照会で原因を特定した。端末情報や生成APKはGitに含めない。

## 11月6日の地震受信調査

- 気象情報では11月6日19:39の関東東方沖M6.1、最大震度3が候補。東京の地震という説明だけでは09:06の相模湾M3.7もあるため、対象時刻の確認が必要。
- 中継は全国のWolfx緊急地震速報電文を取得するが、`prepareWolfx`は京都府南部が警報対象の電文だけをFCM送信対象にする。Androidの`EewReceiver.REGION`も京都府南部固定。PWAはこのFCM直接受信処理を持たず、共有された開催記録を扱う。
- 公開healthは調査時点でconfigured/enabled=true、upstream=connected、lastError=null。累計接続102、heartbeat17036、データ電文264、接続失敗610。ただし現在の状態と累積件数は11月6日の特定電文の受信証明にはならない。
- 実機のfiles/eew-receiptsは存在しなかった。利用者がアンインストール→再インストールしており、当時の端末保存記録をこの端末から確認できない。現在のFCM登録成功はログで確認した。
- 中継Durable Objectの保存領域は`_cf_KV`。Cloudflare公式SQL参照APIでイベント限定の読み取りを試みたが、`SQLITE_AUTH`により禁止されている。APIやWorkerを変更して取り出すことはしない。したがって中継が該当電文を受信したかは未確認。実機未受信と断定しない。
- READMEに残る10月25日の接続確認と受信試験は歴史的な記録であり、11月6日の実地震受信の証拠には使わない。READMEの「PlanD 受信試験」は現在のAndroid通知「Help Net 受信試験」と名称が異なる。

## vivoのAPK警告

実機モデルV2417Aの`/system/priv-app/PackageInstaller/PackageInstaller.apk`に、`filing_risk_warning_title_failed_to_filed`として利用者報告と同一の「アプリケーション登録の検証に失敗しました」を確認。英語はApp registration verification failed、中国語は应用备案校验未通过。同じ文言群にICP registrationの説明があり、Google Android Developer Consoleの限定配布とは別の、中国のアプリ届出確認に関する警告と判断した。インストーラーの個人データは取得していない。

Googleの限定配布は無料だが、端末のQR/リンク→端末生成コード→開発者側でAdd deviceという追加手順がある。既存識別名を登録できず新規package nameのみという制限も公式ヘルプにある。20台/50台の公式文書の不一致あり。今回の警告対策として導入しない。既存スライドQRと参加案内URLは維持。

参考：
- https://support.google.com/android-developer-console/answer/17131204?hl=en
- https://developer.android.com/developer-verification/guides/limited-distribution
- https://support.google.com/android-developer-console/answer/16561738?hl=en

## このアプリについて：承認待ち案

以下は提案であり、アプリには未反映。

日本語：
Help Netは、地震や訓練の際に、地域の安否・位置情報・通報・救助の進捗をグループで共有するアプリです。
記録は端末に保存され、通信できるときにグループへ共有されます。位置情報の取得には端末の許可が必要です。
建物の全壊率は、計測震度6.2を想定した、構造・築年数に基づく推定値です。個々の建物の被害を確定するものではありません。津波の浸水表示は熱海のみ対応しています。

English:
Help Net lets your group share safety updates, locations, incident reports, and rescue progress during earthquakes and drills.
Records are saved on your device and shared with your group when a connection is available. Access to your location requires your device’s permission.
Building collapse risk is estimated from structural type and building age, assuming a JMA instrumental intensity of 6.2. It does not confirm damage to an individual building. Tsunami inundation is available for Atami only.

末尾にData and credits／データ・クレジットとして地図・住所・建物・津波計算・アイコンの出典をまとめる案。既存のバージョン表示を維持。
