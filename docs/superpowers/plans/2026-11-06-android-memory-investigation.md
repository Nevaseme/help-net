# Androidのメモリー過多終了の調査

## 確認した事実

- 2026-11-06 06:05:48 の本体プロセス終了履歴に RSS 4.2GB、`full kill by itgsa` が記録されていた。終了理由の列は `USER REQUESTED / STOP APP`。利用者が示したvivoのメモリー過多終了通知と整合するが、Android標準のLMKD終了と断定しない。
- 再起動後の同じプロセスを操作せずに測定したところ、PSSは1,304,563〜1,350,268KB、RSSは1,513,923〜1,559,707KB。Native Heap PSSは約656,000KB、Graphicsは447,639KBで、Java Heapより大きい。Java側のBitmap計測値は490KBだった。
- 京都の建物タイルは低ズームでも個別の建物形状を持つ。manifestの検証値では `11/1796/811.pbf` が337,599ポリゴン、`10/898/405.pbf` が319,371、`12/3592/1622.pbf` が201,520。これはファイル容量でなく、復号・三角形化・GPUへの転送が必要な形状数である。
- `SituationMap` は `setPrefetchZoomDelta(0)` を設定済み。`PlanApp` はタブ往復の停止を避けるため地図を保持し、非表示時は `onPause/onStop` する。Composition破棄、View切離し、Activity破棄には `release/onDestroy` がある。確認した範囲で破棄そのものの欠落は見つからなかった。

端末専用の終了履歴・メモリー原本は `output/android-memory-20261106/` に保存し、Git管理対象から除外している。

## 今回の限定修正

`PlanMapView` はApplicationからの低メモリー通知を受けていなかった。MapLibreが要求する通知経路を補い、`ComponentCallbacks2` の `onLowMemory` と `TRIM_MEMORY_RUNNING_LOW` 以上を生存中のMapViewへ転送する。破棄時には通知登録を解除する。UI非表示通知も含むが、アプリ内のタブ変更ごとに独自の通知を発生させることはしない。

既存の `RapidMapNavigationTest` の任意メモリー試験を、MapViewへの直接呼出からApplicationへの通知に変更した。これにより実際の登録経路を通る。その後、下記の実機比較で十分な削減を確認したため、`SituationMap` のstyleロード完了時に一度だけ `setTileCacheEnabled(false)` を設定した。未表示のズーム・地点のrendererタイルを保持しない。依存ライブラリ、タイルデータ、オフライン保存、建物表示範囲は変更していない。

## 原因候補と限界

地図のネイティブ形状・描画資源と複数ズームの保持が主要候補。現在の測定だけではメモリーリークと正常な大容量キャッシュを区別できず、4.2GBに至った操作系列も復元できていない。

実機はAndroid API36。[Androidの公式仕様](https://developer.android.com/reference/android/content/ComponentCallbacks2)ではAPI34以降、実行中の低メモリー通知（RUNNING_LOW等）と `onLowMemory` 自体は通知されない。UI_HIDDEN/BACKGROUNDの経路を補っても、前景での増加を自動的に抑えられるわけではない。後述の人工的なtrim通知試験は通知経路と解放効果の確認であり、実際の前景動作の改善証明とは区別する。

[MapLibre 13.6.1のAPI](https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.maps/-map-view/index.html)はMapViewへの低メモリー通知を要求する。[同版のAndroid実装](https://github.com/maplibre/maplibre-native/blob/android-v13.6.1/platform/android/MapLibreAndroid/src/cpp/android_renderer_frontend.cpp)では、この通知はrendererへの非同期要求である。[TilePyramidの実装](https://github.com/maplibre/maplibre-native/blob/android-v13.6.1/src/mln/renderer/tile_pyramid.cpp)は未使用キャッシュを消去するが、現在表示に必要なタイルを破棄しない。通知が来る前の急増、現在の縮尺だけで大きくなる使用量、停止中rendererの要求処理タイミングは、この修正のみで解消を保証できない。

低ズームのsource/layer minzoomを上げるだけでは既存のリスク表示が消える。巨大タイルは概要表示用であり、詳細表示用は既にzoom14以上に分かれている。このため表示範囲の制限を修正として実行しない。

調査時のSituationMapコメントにはキャッシュ無効化setterで停止したとあったが、上記同版のソースではsetterは非同期、getterは同期である。過去の停止条件は復元できていないため、styleロード完了後に一度だけ設定する今回の条件を実機比較した。下記の成功結果に基づき採用し、コメントも今回の条件と実測値へ更新した。地図の往復時に復号を繰り返す副作用は引き続き測定の限界に含める。

## 実機比較（1.5.80〜1.5.81）

vivo V2417A / Android API36、debug版。同じ空データの製品地図で京都5地点を2周（10巡回）、zoom11.5↔15.5、毎回のタブ往復を実行した。利用者の安否・開催・通報データは変更しない。

| 条件 | 再表示後PSS (KB) | 再表示後native割当 (bytes) | 結果 |
| --- | ---: | ---: | --- |
| 1.5.80、キャッシュあり、trimなし | 1,247,085 | 797,916,128 | 成功、67.456秒 |
| 1.5.80、キャッシュあり、非表示中trim | 1,044,662 | 488,707,232 | 成功、65.122秒 |
| 1.5.80、試験中のみキャッシュ無効、trimなし | 597,374 | 214,130,640 | 成功、59.444秒 |
| 1.5.81、製品設定でキャッシュ無効、trimなし | 584,126 | 256,188,912 | 成功、58.959秒 |

1.5.81は最初の対照よりPSS約53%、native割当約68%減少。計測実行ごとの差はあるため、すべての操作でこの削減率になる保証ではない。実行時間には外部Activity起動までの待機が含まれ、描画速度の比較値とは扱わない。

キャッシュ無効化のsetterは実測0ms、同条件のタブ往復は61〜115ms。非表示中に開始したフレームは0で、今回の連続操作では停止・例外終了はなかった。最終製品設定でのnative割当384MiB以下という当該実機用の試験条件も通った。

非表示中trimでは通知直前PSS1,222,062KB→10秒後1,219,346KBとほぼ変わらず、再表示後に低下した。停止中rendererへ要求が保留される挙動と整合する。これを前景増加への十分な修正とは扱わず、通知に依存しない保持抑制を採用した。

最初の自動Activity起動はMonitoringInstrumentation.startActivitySyncの45秒待機で失敗。地図を確認できる段階に至っておらず、成功件数・メモリー比較に含めない。既存のexternalLaunch経路でadbから試験Activityを明示起動し、上表の全条件を同じ方式で再実行した。

結果原本は `output/android-memory-20261106/` の `control-external-test.txt`、`trim-test.txt`、`cache-off-test.txt`、`final-production-memory.txt` と各専用タグログ。公開用の実測要約のみこの文書に記録する。

## 関連する回帰確認

1.5.81/code94のビルドと単体302件は成功。上表の対照・trim・キャッシュ無効・製品設定の4試験は実施済み。最終APKのSHA-256は `86b99cfe307b3fb28e6fdcb72fc3b460872b3ce1909ed560b53c253d39a9cfaa`。

- `BuildingRiskDeviceTest` は43.054秒で成功。京都の詳細/広域の建物リスク描画、複数地点の移動、広域からのタップ拡大と詳細建物の再表示を確認した。タイルの表示範囲やリスク色を消してメモリーを減らしたものではない。
- `VenueExperienceDeviceTest` は最終配置修正版で訓練・地震の2試験が78.523秒で成功（`venue-1.5.81-layout-final.txt`）。初回答前の画面移動制限、英語案内、2回目安否更新後のSafety維持/案内なし、保存成功文の英語、Show safeでのmarker増減、位置不明2件、メニュー、地図内通報とMap維持、ドラッグ閉じ、通報位置プレビュー/確定、再選択時のフォーム入力保持を確認した。Show safeの撮影はnative描画反映を待ち、オフで緑の群が消え、オンで復帰することを画像でも確認した。
- このUI試験と画像確認で、保存完了テンプレートの日本語残留と、通報位置確定ボタンが大きい地図に押し出される実不具合を見つけ、固定文言の翻訳と既存sheet footerの使用で修正した。利用者入力の本文は翻訳しない。通報地点選択の案内とズームボタンの重なりも、案内を左上へ置き右側に操作領域を確保して修正した。地点やアイコンの位置は動かしていない。
- 初期の追加UI試験で見出しとタブの同名選択、native marker準備前の比較、native single-tap確定前の判定が失敗した。試験を実際の準備状態に合わせ、上記の成功試験で同じ操作をやり直した。失敗を成功件数に含めていない。
- 07:52時点の終了履歴は試験開始/終了のforce stopとAPK更新による終了で、新しい `full kill by itgsa`・例外終了・ANR終了はない（`exit-info-layout-final.txt`）。07:51:44の最終UI試験終了時RSSは0.90GB。空データの地図試験よりUI/記録/WebViewを含むため、この値と上表のPSSは直接比較しない。最後の変更は案内文の位置と試験の撮影待ちだけでメモリー処理は同じため、上表の長い比較を重複実行していない。

検証画像は一括/個別の初回取得が自動承認で拒否された。固定ファイル名・生成時刻を確認し、その後利用者が今回のfixture PNG取得・掲載を明示承認してから、許可された固定PNGのみ個別取得した。保存先は `output/android-memory-20261106/venue-shots/`。他の私有ファイルは抽出していない。

## 測定の限界

この実機・回数での保持量低下と操作成功を確認した。元の4.2GB終了に至った全操作系列は不明であり、無期限・全機種での安定性やすべてのメモリーリーク解消を保証しない。低ズームの現在使用中の形状は引き続き大きい。長時間利用で再び増加が続く場合は、その操作でheapprofdを取得し、現在使用中の形状・キャッシュ・ライブラリ保持を区別する。未使用キャッシュを減らすため、再訪時の再復号は増える可能性がある。今回のタブ応答や連続操作で停止は確認されなかったが、全操作のCPU/電池消費を測定したものではない。
