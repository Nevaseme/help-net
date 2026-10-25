# 京都市の固定建物評価データ

PCでの事前計算とAndroidへの収録。公式IESの再実装ではなく、利用者が選択した内閣府2025年の揺れによる被害関数を数値化した独自試算。

## 再現

Python 3.11以上。今回の実行はWindowsのPython 3.12.14、依存関係は `requirements.txt`。原資料・中間ファイルをGitに追加しない。

```text
python -m pip install -r tools/building_analysis/requirements.txt
python tools/building_analysis/fetch_kyoto.py --cache .implementation/kyoto
python tools/building_analysis/digitize_curves.py .implementation/nankai-2025-method.pdf tools/building_analysis/curves-2025.json
python -m unittest discover -s tools/building_analysis -v
python tools/building_analysis/build_assets.py --cache .implementation/kyoto --output android/app/src/main/assets/kyoto-building-assessment
python tools/building_analysis/verify_assets.py android/app/src/main/assets/kyoto-building-assessment
python tools/building_analysis/verify_assets.py android/app/src/main/assets/kyoto-building-assessment --apk android/app/build/outputs/apk/debug/app-debug.apk
```

PDFは [内閣府防災情報のページ・2025年被害想定手法](https://www.bousai.go.jp/jishin/nankai/taisaku_wg_02/pdf/sanko_gaiyo.pdf) を `.implementation/nankai-2025-method.pdf` に保存する。原図のハッシュが異なる場合は数値化処理を停止する。

Colabでも同じPython処理を実行できる構成だが、今回Colabは実行していない。元GMLの取得・保存には数GB以上が必要。ダウンロード処理は3並列、区画単位で再開できる。失敗区画を黙って省略して成果物を作成しない。

## 原資料との一致度監査

`reference-agreement.ipynb` に2026-10-25の集計結果、点別CSVからの再集計、入力震度への感度計算を保存した。全件の元GML再読取りはノートブックの `RUN_FULL_AUDIT` を有効にするか、以下を実行する。

```text
python tools/building_analysis/audit_reference_agreement.py --assets android/app/src/main/assets/kyoto-building-assessment --pdf .implementation/nankai-2025-method.pdf --cache .implementation/kyoto --out .implementation/building-agreement-rerun
```

監査は資産を書き換えない。曲線再読取りは計測震度6.0〜7.0に限定し、原画像・色・軸を共有する。マーカーの一部が隠れることによる偏りが残るため、報告された読取り差をモデルの誤差上限や実被害の予測精度として使わない。解釈と速報候補は `docs/superpowers/plans/2026-10-25-building-accuracy-and-eew-research.md` に記録した。

## 入力・加工・出典

- [京都市2025年度PLATEAU](https://www.geospatial.jp/ckan/dataset/plateau-26100-kyoto-shi-2025)。公式PLATEAU MCPに矩形 `135.55,34.85,135.9,35.35` を照会し、京都市2025年度の242建物メッシュを固定した `kyoto-sources.json` を使用。
- 構造コードは同一配布物の `codelists/BuildingDetailAttribute_buildingStructureType.xml` に従う。601=木造・土蔵造、602=SRC、603=RC、604=鉄骨造。605軽量鉄骨造・606組積造・610非木造一般を、根拠なしに別の曲線へ割り当てない。
- 形状はLOD0の外形・内周を保存。EPSG6697等の緯度・経度順をGeoJSONの経度・緯度順に変換。矩形や高さから建物外形を創作しない。
- 原値も保存し、0001年、0階、負の面積、不明構造などを有効値へ補完しない。全棟が評価可能という前提は置かない。
- 内閣府PDFの本文5–8ページの12グラフ、21分類×全壊・全半壊の42曲線を0.1刻みで読み取り、1パーセントポイント単位へ丸めた。元の係数表ではない。色抽出、画素座標校正、隠れた点の目視読み取りを `digitize_curves.py` と出力の `digitizationEvidence` に残す。これは読み取りの再現性であり、公式係数との一致や実被害への精度保証ではない。
- 全半壊率は全壊を含む。半壊のみは「全半壊−全壊」。モデルの統計的不確実性は、グラフの読み取り・丸めの誤差とは別であり、ここでは信頼区間を推定しない。丸め後の0は実被害が起こらないことを意味しない。
- 採用曲線の範囲5.0〜7.0以外は拒否する。計測震度の連続値を入力する。「震度6弱」等の階級を勝手に代表値へ変換しない。
- 16階以上は利用者の明示指示により `highrise-zero`。全壊率・半壊率0とし、`status=assumed` と `cabinet-highrise-zero-assumption` を必ず付ける。通常の曲線推定 `estimated` と区別する。
- 耐震改修の情報がないため、耐震改修による軽減を加えない。各建物が未改修だと確認した意味ではない。

## 成果物と利用

`index.json` は出典URL・元GMLハッシュ・成果物ハッシュ・件数・評価不能理由・範囲を収録する。`model.json` に共通の計算済み曲線を置き、各建物にその分類を固定して参照させる。同一分類の42個の数値を何十万回も複製しない。端末では指定震度の表を参照し、刻みの間だけ線形補間する。

Android `BuildingAssessmentRepository` は通信せずに2メッシュまでをキャッシュし、範囲で絞ってから外形・内周で点を照合する。重なる建物は全件返し、最寄りの1棟へ勝手に割り当てない。評価不明はnullであり被害率0ではない。`PlanViewModel.assessBuildingsAt` から利用できる。

既存住民・手入力建物のIDやRoomデータは変更しない。現在は読込・検索APIまで。画面の新規表示、強中弱への変換、想定地震シナリオとの紐付けは行っていない。独自試算であり、個別建物の耐震診断や立入り可否判定として使うものではない。

加工者はPlanD。建物原資料は京都市・国土交通省 Project PLATEAU、被害関数原図は内閣府。公式結果と誤認させないよう、出典と加工内容を成果物に保持する。[PLATEAU利用条件](https://www.mlit.go.jp/plateau/site-policy/)・[内閣府防災情報のページの利用条件](https://www.bousai.go.jp/notice.html)を参照。
