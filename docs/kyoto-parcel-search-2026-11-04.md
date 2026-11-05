# 京都市の地番検索（2026-11-04）

国土交通省の地番図試作データから作成した 718,575 件の地番検索を、Android と PWA の住所検索に追加した。位置は土地ポリゴン内部の代表点であり、建物、玄関、住居表示、居住者を確認した座標ではない。検索結果は `precision: parcel-area`、`matchLevel: parcel` として扱い、地図上で建物を確認する既存の操作へ渡す。原典の時点は未確認であり、ファイル名の 2025 を原典の観測年とはしていない。

## データと生成

`tools/building_analysis/prepare_parcel_search.py` が `build_kyoto_parcel_matches.py` の `parcel-address-search.json.gzip` を読み込み、`.implementation/kyoto-parcel-search` に検索データを生成する。元データの独立した `parcelNumber` を除いて町名を得るため、数字を含む町名や丁目から誤って地番を切り出さない。

- 町索引: 5,642 町、340,687 バイト。
- 地番分割データ: 町名のハッシュによる 256 ファイル、最大 7,036 件/ファイル。
- 索引を含む合計: 15,406,705 バイト。
- 索引 SHA-256: `f19eaf30c7c1824b0f5d254215fa34a124d994a736df2b6813ce8a10cc5eb7e3`。
- 配布先: Android `assets/kyoto-parcel-search/`、Web `data/cities/26100/parcel-search/`。

Web の `city.json.parcelSearch` は `{path, sha256, bytes}`。索引は `schemaVersion: 1`、`source`、`rowCount`、`towns: [[normalizedQualifiedTown, chunkId]]`、`chunks: [{id,file,count,bytes,sha256}]` を持つ。各 `file` は索引を基準とする相対パス。各 gzip ファイルは `{schemaVersion:1, rows:[...]}`、行は `[id,address,longitude,latitude,"parcel-area",metadata,normalizedAddress]`。metadata に原典 ID、版、自治体コード、区コード、元フィーチャ番号を保持する。

## 検索の契約

`ParcelSearchRepository.kt` と `parcel-search.mjs` は、索引にある町名から該当ファイルのみを読み、正規化した住所の完全一致を検索する。数字が近い別地番の代用はしない。全角数字、丁目、番地、京都の通り名表記を既存の共通正規化に合わせる。同名の町が別の区にある場合や、同じ住所に複数筆がある場合は候補を残す。明示された別市の住所から京都市の地番を作らない。

メモリ内の分割データは最大 4 ファイル。既存の町・街区位置は一致する地番があれば地番位置へ更新するが、確認精度の高い residential/building 等の候補は保持する。地番が存在しない場合は既存の町名候補を町精度のまま返す。部分ダウンロードや通信断で地番を読めない場合も、既存の住所候補を元の精度・出典のまま返す。失敗した分割ファイルの読み込みは次回に再試行する。

PWA の住所パッケージキーは v3 へ更新し、旧利用者も新しい索引を取得する。オフラインで v3 が作れない場合は、保存済みの v2 を保持して利用する。市の保存処理は索引と全分割ファイルを保存してから完了扱いとする。

## 検証

Node の `parcel-search.test.mjs`、既存の `address-integration.test.mjs` と、Android の `ParcelSearchRepositoryTest`、`BuildingAddressTest` が対象。実際の全域データによる検索、全角・通り名、区違い、町名の曖昧さ、未知の 999999、別市、旧字体「京北下町島﨑」、既存 residential 座標の維持、壊れたファイル、部分保存からの再試行、旧住所キャッシュのオフライン利用を確認する。

開発機上の代表的な計測では、読み込み済み地番検索が Node 約 0.05–0.1 ms、JVM 約 0.07–0.12 ms、既存の住所検索も含む Web の検索が約 25–40 ms。左京区讃州寺町203 の検索は 256 ファイル中 1 ファイルのみを読み込む。この値は開発機上の測定であり、実機の通信・初回読み込み時間は含まない。
