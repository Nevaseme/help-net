# FCMによる常時受信の実装計画

最新（2026-10-25夜）：利用者の選択でWolfxへ変更。Cloudflareから20分の継続接続を確認し、Androidと中継へ実装した。[Wolfxの実装・検証記録](2026-10-25-wolfx-implementation.md)を現在の状態として参照する。以下のP2P接続拒否・HTTP検討は過去の経緯として保持する。

利用者がA（FCM）を選び、中継先にCloudflare Workers／Durable Objectsの無料プランを選択した。その後、Firebase・Cloudflareの操作もプラグイン経由で行うよう指示した。既存のP2P解析・端末保存を受信経路へ接続する。地域は選択済みの京都市に対応する京都府南部。常時待受は位置記録の開始・停止に依存させない。

2026-10-25 21時台の追記：利用者が5秒HTTP取得を選び、既存Cloudflareで25分の比較試験を実施。5秒は計130回中10回、2秒は26回中4回がHTTP429となり、安定受信経路として不適合。試験用のWorker変更は除去・再配置済み。詳細は[HTTP試験結果](2026-10-25-p2p-http-validation.md)。その後、震度はoptional・速報受信を最優先、用途は非商用・研究実験のコンペデモと明示されたため、[Wolfx・AXIS等の候補](2026-10-25-eew-reception-priority-options.md)を再検討している。新しい配信元の採用・接続試験は未実施。以下は各時点の実装・調査記録。

## 構成と範囲

- Android：安定版Firebase Messaging SDK。データ通知で最小限の必要情報を受け取り、追加通信を待たず保存・システム通知する。アプリ画面が閉じていてもSDKから呼ばれるサービスを登録する。
- Cloudflare：SQLiteのDurable Objectを1個に固定。P2P本番WebSocketの接続・再接続、端末トークンの登録、京都府南部の速報・続報・取消のFCM送信。接続の維持・復帰にDurable Objectのアラームを使う。
- P2P全国データはFCMのサイズ上限に収まらない場合があるため、端末へは京都府南部の属性・出典ID・報数・発表時刻を含む地域別電文を渡す。中継では最新原文を保持し、端末には実際に受け取った地域別電文を保持する。
- FCMの安定版HTTP v1高優先度データ通知を使用。AndroidConfigV2は現時点でEAP SDKのため採用しない。TTLは0秒（即時に配信できなければ破棄）。取りこぼしがなくなるという保証ではない。
- 通知を伴う処理に限定。手動の地震成立条件、管理者終了、事前想定震度・建物分類は変更しない。
- 試験通知は管理者用の受信試験からのみ送信し、Android debug版で「受信試験」として区別。P2Pの訓練報を本番警報として表示しない。
- Firebase設定・中継URL・送信秘密鍵はGit管理外。Analytics・課金プランは使わない。独自の端末常駐接続は追加しない。

## タスクと検証

- [x] 1. Androidの受信電文→保存・通知内容をJVMテストから実装する。重複、古い続報、取消、震度範囲、試験・不正電文を確認。
- [x] 2. FCMサービス、トークン登録の再送、Application・Manifest・任意のローカル設定を接続。設定なしでもビルド可能にし、未設定を受信可能と表示しない。
- [ ] 3. Cloudflareの中継・Google送信認証・登録・受信試験・接続復帰を実装。実際の署名検証、ローカル実行と擬似上流・FCMで検証。
- [x] 4. Androidのビルド・単体・lint、エミュレーターでサービス入口からの保存・通知を確認。FCM実配信はエミュレーターの強制Doze・消灯、vivoの通常消灯・アプリ非表示で確認。vivoの強制Doze試験は行っていない。
- [x] 5. 利用者の操作手順と実装済み／実配信未検証の区別を記録する。

## 配置・受信試験の結果（2026-10-25）

- Firebase `tasukeai-67320` とAndroidアプリを接続。承認済みの専用サービスアカウント・FCM送信権限を使用し、送信鍵はCloudflareの秘密変数だけに登録した。Analytics・有料プランは有効化していない。Cloudflareは利用者がWorkers Freeと確認した。
- 中継を `https://pland-eew-relay.tomodachi-takumi.workers.dev` に配置。端末登録と管理者認証付きのFCM受信試験が動作。氏名・住居・位置・安否を受信登録に送信しない。
- Android全131件のJVMテスト、debug APK・試験APKビルド、lintが成功。エミュレーターでトークン発行と保存・通知の計2件の端末試験が成功。
- エミュレーターで実際のクラウドFCM送信を確認。13:48:30の受信試験はホーム画面・消灯・強制Doze中に保存と通知を確認した。強制Dozeと電池の試験状態は解除済み。
- vivoは通常版debug APKをデータを保持して更新。13:56:22に通常受信、14:00:15にアプリ非表示・消灯中の実FCM受信を確認。後者は通知生成時刻1792904415665から保存1792904415977まで312ms。ただし1件の診断電文であり、地震発表からの所要時間・平均遅延・到達保証ではない。再起動・落下操作・強制Dozeは行っていない。
- 背景起動時に不要な地図初期化でエミュレーターの起動が遅れる問題があり、地図初期化をActivityへ移した。FCMの背景処理では地図を初期化しない。
- **未完了**：CloudflareからP2P本番WebSocketへ接続するとHTTP429。ローカル実行では接続成功、Cloudflareから同じP2Pの公式HTTP履歴APIはHTTP200だった。公開サーバーコードには429条件がなく、共有送信元や前段の制限という原因は特定できていない。HTTP取得への切替を提案したが、利用者は構成をゼロから再検討し、候補と利点・欠点を提示するよう指示した。切替は保留し、履歴を警報配信していない。接続エラー・次回確認時刻はhealthに保持し、接続成功と誤表示しない。
- このためタスク3は実装・模擬試験済みでも、本番上流の接続確認が未達。**実地震の常時取得が完成したとは扱わない**。災害成立・建物評価への自動適用も別途未実装。

## WebSocket接続拒否の調査（2026-10-25 14時台JST）

利用者の指示によりA（CloudflareのWebSocket）の原因調査を実施。取得方式の変更は行っていない。Context7の `/cloudflare/cloudflare-docs` でも公式仕様を照合した。

### 確認できた事実

- P2P運営者は[2026-05-29の告知](https://p2pquake.hatenablog.jp/entry/2026/05/29/005109)で、6月中旬からWebSocketを1 IPにつき2接続に制限すると発表。[現行仕様ページ](https://www.p2pquake.net/develop/json_api_v2/)が読み込む [specification.yaml](https://www.p2pquake.net/swagger-ui/specification.yaml)にも `/ws: 2 接続 (IP アドレス毎)` と明記されている。以前参照したGitHub masterのJSON API仕様書にはこの追記がなく、GitHub版だけで現行の制限を判断したのは不十分だった。
- [Cloudflare公式仕様](https://developers.cloudflare.com/fundamentals/reference/http-headers/#cf-connecting-ip-in-worker-subrequests)では、別のCloudflareゾーンへのWorker subrequestの `CF-Connecting-IP` は共通値 `2a06:98c0:3600::103` となる。Context7でも同じ公式記述を確認。
- 実際のDurable ObjectからP2Pホストの公開診断URL `/cdn-cgi/trace` を確認すると、`ip=2a06:98c0:3600::103`。WebSocketはHTTP429、応答serverはcloudflare、Ray IDは `a4feb804182d00a5-KIX`。本文は空で、Retry-Afterとcf-mitigatedは付いていなかった。この追加trace取得処理は調査後に除去した。
- 同じ識別用User-Agentを付けたPCからのWebSocketは14:08:23 JSTにHTTP101で接続成功し、診断後1000で正常切断。Ray IDは `a4feb4016ecad4ea-NRT`。上流全体の停止や、識別用User-Agent自体の拒否とは整合しない。
- 実装の `fetch(https URL, {headers:{Upgrade:'websocket'}})` → `response.webSocket` → `accept()` は[公式のクライアント例](https://developers.cloudflare.com/workers/examples/websockets/)と一致。Context7でも、Sec-WebSocket-Key等をランタイムが補うことを確認。FCM認証が実行されるより前の、P2Pとの接続開始時点で拒否されている。

### 判定と残る不確実性

最有力なのは「Cloudflare Workers共通IPに対する、P2PのIP単位接続数制限またはそのIPへのアクセス制限」。PlanDを1接続にしても、相手が共通IP単位で判定するなら他のWorkers利用者と枠を共有する。ただし、P2P運営側の接続数・制限ルールのログは見えておらず、現在誰が何接続占有しているか、2接続上限と追加遮断のどちらが直接429を返したかまでは未確定。Cloudflare全般でWebSocketが不可能という意味ではない。

ヘッダーを偽装したり、未公開の接続先へ迂回する検証はしていない。別のWorkers名・同じ方式の別Workerを作るだけでは、共通IPの条件を解消できる根拠がない。確定には上記Ray IDと時刻をP2P運営側で照合する必要がある。問い合わせは送信していない。

なおHTTP APIも1 IPあたり60回/分なので、先の「2秒間隔なら30回/分」という計算はPlanD単体の回数であり、共通IPでの他利用者分を含めた余裕を証明しない。HTTP200の短時間確認だけでBの長期安定性を保証できない点も、次の方式選択に反映する。

根拠：[FCM受信](https://firebase.google.com/docs/cloud-messaging/android/receive-messages)、[有効期間](https://firebase.google.com/docs/cloud-messaging/customize-messages/setting-message-lifespan)、[AndroidConfigV2のEAP](https://firebase.google.com/docs/cloud-messaging/send/v2-api)、[Cloudflare無料枠](https://developers.cloudflare.com/durable-objects/platform/pricing/)。1オブジェクトが128MBで24時間動く目安は約10,800GB秒／日で、無料枠13,000GB秒／日以内。ただし他用途を含む枠消費・CPU・通信・保存・リクエスト制限もあり、無制限の無料運用ではない。無料プランでは超過時に停止し、自動的に課金プランへ移行させない。
