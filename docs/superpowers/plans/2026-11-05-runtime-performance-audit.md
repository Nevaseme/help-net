# 統合後のAndroid・PWA性能調査

対象は `.` のAndroid 1.5.76（89）、PWA 1.6.50。2026-11-05に採取。**この調査による整理、依存更新、最適化は実装していない。** 通報の自動回避撤去は別途承認された機能修正である。

## 結論と測定の限界

Androidの配布容量はソースの長さより地図・住所資産が支配している。デバッグAPKは328,836,142 bytes（約328.8 MB）、主要Kotlinソース136ファイルは873,399 bytes（約0.87 MB）。ファイル分割だけでAPKや描画が大幅に軽くなる根拠はない。

PWA配信ファイル97件は5,082,099 bytes。津波結果と地図ライブラリが大半を占める。画面の大きなソースを分割しても、同じものを一括取得するなら取得量は減らない。

Androidの地図連続移動・拡大縮小・画面往復は修正版で成功したが、エミュレーターのフレーム遅延は残る。ロック解除後のvivo実機でも同版を検証し、今回の連続移動・画面往復で停止・終了は再現しなかった。実機値は末尾の追記を参照。PWAはデスクトップ上の390px表示であり、実スマートフォンの性能値ではない。

## 実行範囲と依存関係

| 範囲 | 実際の入口と参照 | 整理時に見落とせない依存 |
|---|---|---|
| Android通常画面 | Manifest → MainActivity → PlanApp / PlanViewModel → SituationScreen / SituationMap、ResponseScreen等 | Composeの状態保持、MapViewのライフサイクル、非表示時の地図保持、戻る操作 |
| Android永続化・共有 | PlanDApplication → Room、CloudController、SharingController、NearbyTransport、位置取得 | ProcessLifecycleOwner、WorkManager、サービス、Manifestの受信機、再起動・消灯時の復帰 |
| Android地図・住所 | BuildingAssessmentRepository、BuildingMapModel、ParcelAddressRepository、ParcelSearchRepository、CityAddresses | assetsの分割インデックス、自治体ID、建物ID、オフライン時の代替経路 |
| Android津波 | Gradleの生成assets → tsunami-runtime | `lab/web/tsunami-precomputed.mjs` とJSONをAndroidにも取り込む。PWA側だけのファイルではない |
| PWA | index.html → app.mjs → web1617-ui.mjs、tsunami-map.mjs、各画面 | Service Worker、IndexedDB、保存フォーム、地図の動的読み込み、別Workerでの住所検索 |
| 公開API | cloud-sync/src/worker.mjs → journal等 | build.mjsが`lab/web`の共有モジュール参照をdist/sharedへ書換。UI側の分割もサーバーの束ね方に影響する |
| 比較画面 | demo/index.html → app.mjsと版別comparison | 本番PWAの入口とは別。ただしdemo/data/citiesはPWAが取得する実データでもあり、比較専用として一括削除不可 |
| 試験・研究 | Android test/androidTest、lab/test、cloud-sync/test、tools/tsunami、tools/building_analysis | 研究用津波ソルバー5資産は公開ビルドから除外済み。旧tsunami-atami.jsonはAndroid配布除外だがJVM試験では参照 |
| 生成物・外部コード | build、dist、node_modules、MapLibre、Leaflet | 生成物と依存ライブラリを自作の未使用コードとして数えない。動的・Manifest・資産参照があるので検索ゼロだけで削除しない |

## Android APKの実測内訳

ZIP内の圧縮後サイズ。複数の役割を持つ地理データを同じデータの重複と断定しない。

| 主な内容 | 約MB | 利用目的 |
|---|---:|---|
| kyoto-risk-tiles | 95.47 | 建物の形・リスクを地図描画 |
| kyoto-building-assessment | 83.06 | 建物の属性・評価照合 |
| kyoto-parcel-addresses | 55.63 | 地番・建物位置の照合 |
| kyoto-parcel-search | 14.44 | 分割索引による住所検索 |
| cities | 10.93 | 自治体別資産 |
| ネイティブライブラリ4 ABI合計 | 41.03 | arm64、armv7、x86_64、x86の端末・エミュレーター対応 |
| res全体 | 約6.11 | フォント等。フォントはその主要部分 |
| tsunami-runtime | 約1.00 | PWAと共有する津波結果の表示 |

これはdebug APKで、release/AAB配信の実容量は今回測定していない。最大KotlinファイルはUiMessages約58.6KB、SituationScreen約38.9KB、ResponseScreen約36.9KB。翻訳表の長さと実行負荷を混同しない。

## PWAの取得・更新・保存

- 津波結果JSON 2,331,019 bytes、MapLibre本体1,108,049 bytes、Worker519,372 bytes、Leaflet147,552 bytes。
- Service Workerの事前保存は83 URL、ファイルサイズ合計4,886,312 bytes。`/`と`/index.html`は同じ内容を含む。この値は圧縮前のファイル合計で、実ネットワーク転送量ではない。
- 初回インストール時に事前保存対象を取得する。キャッシュ名の版が変わる更新では同じ対象を新キャッシュへ保存し、旧版を除去する。HTTP再検証・圧縮により実転送量は変わるため、毎回4.89MBの通信と断定しない。
- 自治体データは別キャッシュ。完全保存の目印があるキャッシュだけを利用し、不完全なダウンロードを完成扱いしない。自治体データ全体は上記5.08MBに含まれない。
- 公開Workerの単一配信物は5,383,037 bytes、gzip約1.742MB。これはサーバー配信物で、ブラウザーの初回ダウンロード量とは異なる。

## 限定操作の実測

生の結果はローカルの `output/report-location-fix/` に保持し、端末情報を含むログはGitへ入れない。

| 環境・操作 | 測定結果 | 解釈 |
|---|---|---|
| PWA、地図移動・ズーム20秒 | 1168 rAF、中央値16.7ms、p95 17.5ms、最大65.8ms、long task 1件59ms | この操作ではおおむね滑らか。rAFは地図GPU描画の完全な測定ではない |
| PWA、People/Map往復・移動20秒 | 1191 rAF、中央値16.7ms、p95 16.8ms、最大116.6ms、long task 1件115ms | 一時的な長い処理あり。原因の断定にはトレースが必要 |
| PWA JS heap | 地図36.43→39.06MB、往復42.61→75.70MB | GC前後を統制していない。リーク確定ではなく反復・静置後の追加測定が必要 |
| Android debugエミュレーター、16回カメラ移動/ズーム | 診断取得込み13.10秒、gfxinfo 70フレーム、p50 48ms、p95 81ms、p99 101ms、janky 100% | 明確な遅延。デバッグ・エミュレーター・GPU別Surfaceの条件なので実機FPSへの換算不可 |
| 同Androidメモリー | PSS 334,316→367,737KB、RSS 498,416→531,988KB、native heap PSS111,378→145,487KB | 増加はある。タイル・キャッシュの増加とリークを分離できていない |

AndroidのGPU percentileは4950msという上限値に偏り、この環境でのGPU所要時間として採用しない。先行試験にはSystem UI自身のANRが画面を覆う失敗があり、通報の可視性試験を最初からやり直した。修正版の12画面試験と道路地点の追加試験は合格。実機の安定性保証とは区別する。

## 候補と変更リスク（すべて未実装）

| 候補 | 期待効果・根拠 | 影響・壊れ得る機能 | 必要な回帰確認・判定 |
|---|---|---|---|
| 地図・住所資産の地域別取得 | 配布容量で最大。未選択地域分のAPK削減余地 | 初回オフライン起動、住所検索、建物詳細、災害時取得不能、資産の版対応 | 初回圏外・途中失敗・再開・切替・完全保存を確認。製品仕様判断が必要 |
| ABI別配布/AAB | 4方式約41MBのうち不要な方式を端末に配らない余地 | 古いARM端末、x86実機・エミュレーター、配布・署名手順 | 対応端末表と全方式の起動/地図。debug値だけで削減率を保証不可 |
| フォントの配布方法・収録範囲 | res容量削減の余地 | 日本語氏名・地名、PDF、英語、文字拡大、圏外表示 | 文字集合とPDF埋込を照合。安易なサブセット化不可 |
| 地図のイベント処理・再描画の整理 | ReportMapMarkersは座標投影だけになったが、カメラ中のCompose更新がある | アイコンの追従・タップ位置、画面復帰、選択建物 | 実機Perfettoで処理時間を分離。現時点で追加調査が必要 |
| 町内会のBitmap生成の再利用 | SituationMapは町内会Bitmapを生成後に内容署名を比較する経路がある | 言語・人数・拡大率の更新、既存アイコンの寿命、アニメーション | 割当回数・native heapを測定。キャッシュ鍵と破棄を検証するまで変更しない |
| PWAの描画通知のまとめ方 | setPeople/setReports/setCommunitiesなどがdrawCasesを呼ぶ | 同期直後の表示漏れ、位置不明人数、SOSの即時反映 | 操作トレースと複数更新の計測。処理の遅延による表示漏れを検証 |
| 津波結果・地図ライブラリの遅延取得 | 初回取得を減らせる可能性 | 災害時オフラインで津波表示が使えない、新旧SW混在 | 圏外初回/更新後・結果選択・旧キャッシュ復旧。仕様判断が必要 |
| 更新キャッシュの再利用 | 未変更ファイルの再取得を減らす可能性 | 旧コードと新データの組合せ、不完全更新、オフライン起動 | 新旧版混在・途中切断の試験。内容ハッシュ管理等の複雑化を伴う |
| JSON処理・共有差分の見直し | ResponseWorkの検証毎Json生成、画面側JSON.stringify比較などがある | 22KB制限、署名/直列化、順序、競合解決、復元 | CPUプロファイルで支配的か確認。共有契約を変えず同値試験が必要 |
| 大きな画面・翻訳ファイルの分割 | 読みやすさ、責務把握の改善候補 | Compose remember/saveable、クロージャ、フォーム下書き、動的翻訳、共有モジュール | 画面往復・再生成・多言語・PDF・権限・APIビルドを再確認。高速化効果は未証明 |

住所検索は分割索引を使用し、全地番JSONを毎キー入力で読む構造ではない。統合試験では256分割中1件の取得と結果一致を確認した。試験の所要時間はキャッシュ・PC条件に依存するため、スマートフォンの検索速度保証には用いない。

## 次に必要な調査

短時間の実機操作・ANR/終了履歴・フレーム・メモリーは末尾追記のとおり確認済み。残る追加調査は長時間の反復、GC条件を揃えた静置後メモリー、取得済みPerfettoの詳細解析。停止が再現した場合はそのスタックを原因と結び付ける。上記候補の実装は別途指示を受けるまで行わない。

## 実機確認の追記

ロック解除後にvivo V2417A / Android 16へ1.5.76（89）を更新し、[実機確認](2026-11-05-physical-map-verification.md)を実施した。通報なしの移動16回・ズーム4回ではp95 13ms、janky 0.24%。通報のある範囲の移動16回・ズーム4回ではp95 15ms、janky 0.07%。画面往復9回と通報開閉・ドラッグも成功し、新しいANR・例外終了なし。

PSSは移動で453,376→653,883KB、通報範囲で607,517→688,248KB。画面往復後671,264KB、後続静置確認612,579KBまで低下した。リークの有無や長時間使用は未確定であり、追加の最適化はしていない。エミュレーターの遅延値をそのまま実機へ当てはめない。実機Perfettoは取得済みだが詳細解析は未実施。最初のSafety画面を含んだ計測は破棄し、区間ごとにMapを確認した2区間のみを採用した。
