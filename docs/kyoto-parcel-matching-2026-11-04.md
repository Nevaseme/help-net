# 京都市の地番候補と建物の対応

京都市の地番図に由来する国土交通省の公開データと、アプリに既存の PLATEAU 建物ポリゴンを重ねる。結果は **地番候補** であり、建物の確認済み郵便住所、登記境界、所有者、居住者、階・部屋を表さない。既存の12棟・64人の仮想配置、評価タイル、保存済みプロフィールをこの処理では変更しない。

## 取得元と利用条件

- 公開ページ: https://www.mlit.go.jp/tochi_fudousan_kensetsugyo/tochi_fudousan_kensetsugyo_tk17_000001_00063.html
- 取得ファイル: https://www.mlit.go.jp/tochi_fudousan_kensetsugyo/content/26100_Shapefile.zip
- SHA-256: `fe5784b439f63cf8f1ee4f632a34cf169cd0ee831d3c6a016b0cf508aa88ba50`
- ZIP: 43,528,893 bytes。収録名 `26100_京都市_土地境界データ_2025.*`。地物数 718,603。
- `*.prj` の実測定義: **JGD2011 / Japan Plane Rectangular CS VI (EPSG:6674)**。メートル単位。`*.cpg` は Shift_JIS。
- 定義書: https://www.mlit.go.jp/tochi_fudousan_kensetsugyo/content/001973643.pdf
- 変換方法: https://www.mlit.go.jp/tochi_fudousan_kensetsugyo/content/chibangenkyoconvert.pdf

国土交通省ページは自治体の地番図を共通形式に変換した試作データと説明する。ファイル名の「2025」は確認できたが、原典となった京都市データの基準日は未確認である。**2024年1月1日時点と断定しない。** 京都市の2024年版公開ページ https://data.city.kyoto.lg.jp/dataset/00677/ は検索索引に残っているが、今回の直接取得はタイムアウトした。

文字集合は宣言の Shift_JIS で読み、ベンダー拡張漢字「﨑」を含む33行のみ CP932 で再読込する。文字置換や町名変更はしない。空のポリゴン5行を除き718,598筆を索引化し、番号を含む718,575筆を検索対象とする。修復したポリゴンは1筆。33行は京北の筆で、全建物タイルの範囲と交差しないことも確認した。

実際に確認した国土交通省の利用規約 https://www.mlit.go.jp/link.html は、特記がなければ PDL1.0 とする。例外一覧 https://www.mlit.go.jp/page/kanbo01_hy_003657.html に地理空間情報課ラボの試作地番図は挙げられていない。PDL1.0 https://www.digital.go.jp/resources/open_data/public_data_license_v1.0 は出典表示・加工表示による再利用を認める。京都市原典ページの索引上の CC BY 4.0 表示も帰属情報として保持する。京都市の別添 DOCX 規約本文を直接取得できたとは主張しない。

表示例: 「京都市の地番図を基にした国土交通省『土地境界データ（地番現況図）試作データ』を加工して作成」。配布時は出典・規約リンクも保持する。公開ページ、規約、例外、PDL本文の取得スナップショットとハッシュはローカルの `source/web-evidence.json` に保存する。

## 処理と判定

建物の経緯度を EPSG:6674 に変換し、地番ポリゴンとの交差面積を計算する。最近隣点への割当ては行わない。不正形状は `shapely.make_valid` で修復し、その事実を出力する。空形状・読めない形状は除外して行番号を記録する。

- 面積 0.1 m² 未満、または建物面積の0.1%未満の交差は候補にしない。辺が接するだけの地番も候補にしない。
- 建物面積の1%以上を占める候補が複数あれば `multiple-parcels`。
- 95%以上を占める地番があり、1%以上の別候補がなく、修復・地番重複・微小交差等の注意フラグがなく、番号がある場合のみ `single-parcel`。
- その他の交差は `partial`、交差候補なしは `unmatched`。
- `buildingCoverage` は建物に対する交差割合、`parcelCoverage` は筆に対する割合。重複筆があっても `unionBuildingCoverage` は二重計上しない。
- 地番内の代表点を候補に保存するが、これは土地の検索位置であり、自宅や建物の確定ピンではない。

この判定の confidence は幾何学的な一致の強さだけを示し、住所確認を意味しない。地番図の位置誤差、筆界未定、欠落、原典と建物の時点差があるため、候補から選択しても別途建物を確認する必要がある。

## 既知の12棟で確認できたこと

12棟すべてで公開済みの建物住所が交差候補に含まれる。一方、最大の交差面積をもつ地番が公開住所と異なる棟が2棟ある。藤和シティスクエア烏丸丸太町では少将井町247が最大だが公開住所は245-1、アスヴェル京都御所前Ⅱでは六丁目290-1が最大だが公開住所は俵屋町292である。**重なりが最大の地番を建物住所に自動確定してはいけない。** 12棟の判定は単一3棟、複数8棟、一部重なり1棟となる。

検索課題の「京都府京都市左京区讃州寺町203」は原典行97446に存在する。筆内の代表点は経度135.77448339、緯度35.01152586。これは筆の検索位置であって、建物内の居住位置ではない。

## 全パッケージの実行結果

既存の京都建物パッケージ242タイル、513,815棟すべてを処理した。京都市内に存在する全建物を完全収録しているという意味ではなく、特に地番図の方が京北など広い範囲を含む。

| 判定 | 棟数 |
|---|---:|
| 単一地番の高い幾何一致 | 176,204 |
| 複数地番 | 293,777 |
| 一部重なり・要確認 | 31,749 |
| 候補なし | 12,085 |
| 合計 | 513,815 |

少なくとも1候補を得た建物は501,730棟。延べ候補数は1,026,539。建物サイドカーの圧縮合計55,572,450 bytes（約55.6 MB）、最大タイル865,628 bytes。元建物タイルのSHA-256、出力SHA-256、ID集合、全件数、割合の範囲を242タイルすべてで照合した。

デモbbox（経度135.758–135.767、緯度35.011–35.018）に交差する建物2,821棟も処理済み。単一635、複数2,033、一部131、候補なし22。既知12棟の公開住所一致と「最大の地番を自動採用すると2棟で誤る」ことは検証スクリプトの回帰条件にもしている。

## 出力契約

作業出力は `.implementation/kyoto-parcel-match/` に置く。ZIP・展開 SHP 一式は Git に追加しない。

`tiles/index.json`: `schemaVersion:1`, `source`, `counts`, `parcelCounts`, `algorithm`, `limitations`, `chunks`。各 chunk は既存 PLATEAU と同一 mesh、ファイル名、SHA-256、圧縮サイズ、bbox、建物数、元建物タイルのハッシュを持つ。

`tiles/{mesh}.json.gzip`: `{schemaVersion:1,mesh,sourceId,buildings:{[plateauId]:{status,confidence,candidates,unionBuildingCoverage,flags}}}`。

各候補: `parcelId,address,wardCode,parcelNumber,sourceFeatureIndex,longitude,latitude,buildingCoverage,parcelCoverage,overlapM2,numbered,geometryRepaired`。`parcelId` は固定ハッシュの原典行番号に結びつく生成IDであり、登記上の不動産番号ではない。`numbered:false` の候補を通常の住所選択肢として扱わない。

`search.json.gzip`: `{schemaVersion:1,source,rows:[[id,address,lon,lat,"parcel-area",metadata]]}`。metadata は `matchLevel:"parcel",source,sourceVersion,municipalityCode:"26100",wardCode,sourceFeatureIndex`。同じ住所文字列でも別ポリゴンは別行を保持し、町・街区代表点を補強できる。一致する文字列があるだけで既存の精度が高い住宅座標を置き換えない。

`parcel-address-search.json.gzip` は上記のオブジェクト形式。`validation.json` は既知12棟との照合、讃州寺町203の元筆、ケース別実例、ライブラリ版、原典メンバーハッシュ、除外行、件数・配布サイズを持つ。

## 再現

依存: Python 3.12、shapely 2.2.0、pyproj 3.8.0、pyshp 3.1.6。作業用に `.implementation/address-geometry-deps` に配置。

```powershell
$env:PYTHONPATH='.implementation/address-geometry-deps'
python -X utf8 tools/building_analysis/build_kyoto_parcel_matches.py
python -X utf8 -m unittest discover -s tools/building_analysis -p test_kyoto_parcel_matches.py -v
python -X utf8 tools/building_analysis/verify_kyoto_parcel_matches.py
```

初回は公開ZIPを取得し、以後は固定ハッシュと展開物を照合して再利用する。ハッシュが変わった場合は自動的に最新データへ更新せず停止する。`--mesh-prefix 523546` を付けると建物処理だけをデモ周辺に限定できる。gzip の時刻を固定し、同じ入力から同じ配布バイト列を生成する。
