# P2P HTTP取得の有限比較試験

`probe.mjs` は通常の警報配信から切り離した測定用コード。Cloudflareの既存SQLite Durable Objectに一時接続し、認証済み管理者の開始操作で1回だけ動かす。取得した履歴をFCMへ送る処理はない。

- 5秒×10分 → 2秒×5分 → 5秒×10分。
- 最初の応答は過去履歴の基準値。以後の新規IDだけを新着候補として記録。
- 遅れた実行枠をスキップし、429・5xx・通信失敗は最低60秒待つ。
- 保存した進行を復元するため、PCの接続・ポーリングは不要。
- 完了済みの同じRUN_IDは再実行しない。
- 試験後はWorkerへの一時接続を除去する。

検証: `node --test lab/eew-http-probe/probe.test.mjs`

集計: `node lab/eew-http-probe/analyze.mjs <保存した結果JSON>`

`estimatedPublishedToReadMs` は取得成功間隔と通信時間からの推定。アクセス制限による取得成功の空白を含むが、最初の成功より前と最後の成功より後は含まない。更新時刻が一様、応答の内容が要求開始時点のスナップショットという仮定を置く。実地震の受信遅延・FCM通知時間・到達保証として扱わない。公開API側の反映遅れも別途発生する。

結果と無料枠の確認は [検証記録](../../docs/superpowers/plans/2026-10-25-p2p-http-validation.md) に記録する。
