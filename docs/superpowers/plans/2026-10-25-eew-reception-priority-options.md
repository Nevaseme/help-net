# 速報受信を優先した配信元の再検討

最新の決定：利用者がWolfxを選択した。既存Cloudflareからの20分の接続維持と過去の最新速報取得を確認し、PlanDへ組込み。[実装・検証結果](2026-10-25-wolfx-implementation.md)。以下は選択前の候補比較を記録したもの。

2026-10-25、利用者は「震度はoptionalで、速報を受信できることが最も重要」と指定。さらにコンペ用のデモであり、個人・非商用・研究・実験・学術目的と明示した。無料必須、PCの常時稼働に依存しない、外部サービス追加をなるべく避ける条件は維持する。一般公開商用サービスを前提に候補を除外しない。震度不足だけを理由に候補から除外しない。情報の受信・保存と、京都で自動的に災害を成立させる条件は別の判断であり、全国の速報を無条件で京都の災害に変換することは決めていない。

## 候補

1. **Wolfx WebSocket → 既存Cloudflare → 既存FCM**。無料とサーバーからの自動プッシュを明記。P2Pを置換するなら運用するクラウド基盤・アカウントを増やす必要がない。非公式の独立プロジェクト。Cloudflareからの接続・継続性は未検証。利用規約は緊急判断の唯一の情報源にすることや公式警報としての表示を禁止している。PlanDの利用目的・再提供に適用される個別条件の確認が必要で、採用済みとはしない。
2. **P2PネットワークのEPSP受信 → 既存Cloudflare → FCM**。WebSocket APIとは別の公式公開プロトコル。速報の発表・取消・続報と震源、震度4以上予想の府県予報区等を簡易伝達するため、震度が必須でなければ再検討対象。ただし警報利用は仕様上非推奨。CloudflareのTCP接続、通信の維持、署名検証、地域判定の適合性を未検証。APIの接続拒否がこの方式で解消されるとは断定しない。実装負担が大きいため、候補説明なしに追加しない。
3. **YDITS API → 既存Cloudflare → FCM**。運営者の公開説明はJMBSCからの直接取得、現在は警報のみ、HTTP最短1秒、WebSocketは予定。ベータで遅延・停止があり得る。無料での再配信条件は読んだ公式APIページだけでは確定できず、未確認として扱う。現時点でプッシュ方式として選ばない。
4. **AXIS**。公式Aboutは非商用無料、登録条件も私的利用・実験研究を認めるため、明示されたコンペデモでは有力候補。GitHub登録・アクセストークンが必要。同時2接続、配布ソフトの各利用者が個別登録という条件がある。一般向け町内会運用の可否と、今回の実験利用を混同しない。公開Message Sample一覧にも `eew`（緊急地震速報 beta）を確認。実際の購読権限・詳細は認証後に最終確認する。Cloudflareからの接続は未検証。

## 今回の条件では採らないもの

- DMDATA/DM-D.S.S：個人の警報440円/月、予報1,650円/月、無料試用なし。必須の無料条件に合わない。
- Safety tips：アプリが無料でもAPIは同じ条件ではない。観光庁の導入資料は費用負担検討・最低1年・制作会社との契約を要求。無料必須の候補として確定できない。
- 強震モニタの表示用データ抽出：防災科研は表示される緊急地震速報の震源情報等に依拠した即時的情報配信を控えるよう説明。正式な無料通知APIの代用として採用しない。

## 根拠（2026-10-25確認）

- https://wolfx.jp/docs/websocket/
- https://wolfx.jp/docs/open-api/
- https://wolfx.jp/legal/terms/
- https://p2pquake.github.io/epsp-specifications/epsp-specifications.html
- https://developers.cloudflare.com/workers/runtime-apis/tcp-sockets/
- https://www.ydits.net/api/
- https://www.ydits.net/eew-guideline/
- https://axis.prioris.jp/about/
- https://axis.prioris.jp/accounts/signup/
- https://axis.prioris.jp/docs/authentication/
- https://axis.prioris.jp/docs/message/sample/
- https://dmdata.jp/
- https://www.mlit.go.jp/kankocho/content/810002937.pdf
- https://www.rcsc.co.jp/safety-tips-api
- https://www.kyoshin.bosai.go.jp/ja/about_kmoni/

新規プロバイダーへの接続・契約・利用規約同意・本番切替は実施していない。
