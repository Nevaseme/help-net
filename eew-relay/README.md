# PlanD 緊急地震速報の中継

2026-10-25：利用者の選択により上流をWolfxへ変更。Cloudflareから20分間の接続維持、21件のheartbeatと過去の最新速報1件の取得を確認した。AndroidもWolfxの解析・保存・通知に対応。新規地震速報の発表から端末到着までの実測は未実施。[Wolfx実装・検証](../docs/superpowers/plans/2026-10-25-wolfx-implementation.md)。P2Pの接続拒否とHTTP試験は[過去の調査](../docs/superpowers/plans/2026-10-25-fcm-reception-implementation.md)に記録する。

**23:14の確認でCloudflare→Wolfxは接続中。** 配置切替後の503は15回の自動再試行後に復帰し、その後約76分の接続継続を確認した。Wolfx形式のFCMはエミュレーター・vivo双方の消灯中に保存・通知を確認。新規実地震の総遅延と長期の安定性は未検証。

Wolfxの `wss://ws-api.wolfx.jp/jma_eew` を1本だけ受信し、京都府南部が警報対象の速報をFCMでAndroidへ届ける。位置記録の状態に依存しない。全国の電文を取得するが、端末へは京都府南部の情報を含む地域別電文を送る。Wolfxは非公式の配信元として明記する。PLATEAUの振動解析とは別機能。

接続・再接続時は `query_jmaeew` で最新報を取得する。発表から3分以内ならすぐ送信し、3分超・未来時刻は中継内の記録だけにする。通常の継続受信にも同じ鮮度条件を適用。Androidでも到着時に再確認する。震度がなくても対象地域が分かれば通知する。全国最大震度を京都府南部の震度として流用しない。heartbeatが途絶えて150秒を超えた場合は次の定期確認で再接続する。

## ローカル検証

```powershell
npm ci
npm test
npm run check
```

`check` は公開せずWorkerとDurable Objectの変換・バインディングを検証する。`test` は架空電文・テスト用RSA鍵を使い、実際の端末へ通知しない。

## クラウド構成

- `EewRelay`：SQLite Durable Object 1個（名前 `kyoto-south`）。切断時に再接続し、アラームで接続を確認する。原文と最新報数・端末登録を保持する。
- `FcmSender`：同じWorkerのサービス呼出し。40台ごとに分けて送ることで、Workers Freeの1呼出し50サブリクエスト制限を避ける。各バッチ内の送信は最大5並列。
- 有料プランへ変更する処理は含めない。アカウントがFreeであることを配置前に確認する。Freeの日次枠を超えた場合は処理が停止し、配信できなくなる。
- FCMは安定版HTTP v1の高優先度データメッセージ、TTL 0秒。即時配信できなければ破棄し、通信復旧後に古い警報を鳴らす待ち行列にはしない。FCM応答が成功しても、端末での受信を証明したことにはならない。
- 配信失敗は `/health` の状態に反映する。FCMの `UNREGISTERED` は対象の登録を除く。認証エラー等を誤って端末登録の失効と扱わない。

## 設定

CloudflareのプラグインまたはWranglerで配置する。`wrangler.jsonc` の `new_sqlite_classes` を維持し、同じ名前の既存Workerへ無確認で上書きしない。

Cloudflareの秘密変数に次を設定する。コードやGitに入れず、チャットにも貼り付けない。

| 名前 | 内容 |
|---|---|
| `FIREBASE_SERVICE_ACCOUNT` | FCM送信専用サービスアカウントのJSON。少なくとも `project_id`, `client_email`, `private_key` が必要。FCM送信用の権限だけを付与する |
| `ADMIN_TOKEN` | 受信試験・起動操作用の十分に長いランダム値 |

Androidでは `app/google-services.json` と、`android/local.properties` の `eew.relayUrl=https://…workers.dev` を設定して再ビルドする。SDK設定はFirebaseプラグインの `firebase_get_sdk_config` から取得可能。中継へ送るのは受信用の独立したランダムIDとFCMトークンだけで、氏名・住居・位置・安否は送らない。

## HTTP

| 操作 | 用途 |
|---|---|
| `GET /health` | 設定の有無・接続・エラー。端末トークンや鍵は返さない |
| `POST /register` | Androidから `{ "id": "受信用UUID", "token": "FCMトークン" }` を送る。トークン更新時は同じIDの登録を更新 |
| `POST /start` | `Authorization: Bearer <ADMIN_TOKEN>` で開始。最初の端末登録時にも開始する |
| `POST /test` | 同じ管理者認証で `{ "id": "受信用UUID" }` を送り、1台だけに明示的な受信試験を送信 |

テスト電文は `isTraining:true` と専用の診断フラグを持ち、Android debug版のみ「PlanD 受信試験」として表示する。Wolfx上流の訓練・試験報は端末へ送らない。取消や地域の対象解除は以前に対象だった地震について通知を更新し、災害を自動終了しない。既存のP2P保存記録も引き続き読み取れる。

## 実配信の確認

Firebase設定込みのAndroidでトークン取得・受信登録が成功し、`/health` が `configured:true`, `enabled:true`, `upstream:"connected"` になってから行う。

1. 対象端末を通常起動し、通知を許可する。
2. `/test` から「受信試験」を送り、表示と端末内保存を確認する。
3. アプリを閉じて画面を消し、同じ試験を行う。
4. AndroidのDozeを使う検証では、強制Dozeから必ず復帰させる。実機操作と待ち時間を先に利用者へ説明する。

架空データのローカル試験、FCMトークン取得、FCM送信APIの成功、消灯中の端末受信はそれぞれ区別して記録する。設定からの強制停止、通信断、端末電源OFFやメーカー独自制限下の即時配信を保証するものではない。

根拠：[P2P現行仕様](https://www.p2pquake.net/develop/json_api_v2/)、[FCMデータ受信](https://firebase.google.com/docs/cloud-messaging/android/receive-messages)、[FCM TTL](https://firebase.google.com/docs/cloud-messaging/customize-messages/setting-message-lifespan)、[Durable Objects無料枠](https://developers.cloudflare.com/durable-objects/platform/pricing/)、[Workersの制限](https://developers.cloudflare.com/workers/platform/limits/)。
