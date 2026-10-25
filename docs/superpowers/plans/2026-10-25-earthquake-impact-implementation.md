# 地震時の衝撃検知から表示まで Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 位置記録中は消灯中も端末の衝撃を観測し、速報受信時刻または住民の最初の報告時刻の1分前〜10分後の衝撃を既存画面へ表示する。

**Architecture:** 既存PositionServiceが加速度取得・検知を所有する。候補を端末内へ保存し、地震の基準時刻に一致する観測だけを既存共有とMapPersonへ投影する。生波形・未照合候補は共有しない。

**Tech Stack:** Kotlin、SensorManager、Foreground Service、PowerManager、Coroutines/Flow、Room、Compose、既存Nearby/lab交換・demo。

**Spec:** [設計案](2026-10-25-earthquake-impact-design.md)。利用者が依頼した計画報告用の案。未確定判断を含み、実装開始・端末設定変更の指示ではない。

## Global Constraints

- 時間窓は速報の端末受信時刻または住民の最初の報告時刻Tの1分前〜10分後。2026-10-25の回答で基準確定。端点込みを提案。物理的な地震発生時刻・速報の発表時刻で代用しない。
- 消灯・ロック・背景・省電力での観測成立が第一の合格条件。受信未成立の方式を通常版として完成扱いにしない。
- 速報・報告・各続報の時間窓は和集合で採用。未採用候補・採用した加速度衝撃記録とも直近24時間を保持する。未確定の関連付け・検知値を勝手に決めない。
- 現在のDBはversion 7。移行時の最新版を確認し、他作業の変更を戻さない。位置・救助・地震・落下の既存データを維持する。
- 画面は既存の衝撃アイコンと詳細日時を使い、必要な機能状態以外のラベルを増やさない。UI実装時はdemoと公開サイトも同じ作業で更新する。
- 実機試験前に操作と時間を説明。再起動・高所からの落下は計画に含めない。2台目の入手待ちで独立作業を止めない。
- 生データ・APK・鍵はGit管理外。plan.txtと古いAndroidコピーは変更しない。

## Review Focus

- Doze中に登録だけ残ってサンプルが止まる条件：Task 1と7でデータ自体の継続を確認。
- 速報受信時刻と発表時刻を混同する条件、遅れて届いた住民報告で直前衝撃を消す条件：Task 3と4の基準時刻・遅延入力試験。
- 取消・時刻訂正・地震切替で別の災害のマークが残る条件：Task 4〜6で再投影を試験。
- 位置記録の停止・グループ変更で受信やWakeLockが残る条件：Task 1と7の実ライフサイクル試験。
- 候補未受信／センサー停止を衝撃なしと扱う条件：Task 1と6の状態表示試験。

## ファイルと境界（パスはリポジトリ相対）

既存 `android/app/src/main/java/io/github/nevaseme/plandmk1/` 以下を基点とする。

- `motion/AndroidMotionSource.kt`：サンプリング設定と能力確認。`MotionTrace.kt`の4096件校正バッファは時間窓保存と混同せず、校正用途を保持。
- 新規 `impact/ImpactDetector.kt`：波形から候補を生成する純Kotlin処理。
- 新規 `impact/ImpactMonitoring.kt`：監視状態・既存MotionSource購読・検知器・保存を束ねる。
- 新規 `impact/ImpactWakePolicy.kt`：取得可能性と必要設定の判定。WakeLockの取得解放はAndroid側所有者へ閉じ込める。
- 新規 `core/ImpactObservation.kt`：候補と採用観測の型・時間条件・表示集約。
- 新規 `data/ImpactStore.kt`：Room候補・採用観測、再照合、保持期間。
- 新規 `impact/QuakeImpactMatcher.kt`：速報受信／最初の報告の基準時刻と観測の照合。
- 変更 `location/PositionService.kt`、`PlanDApplication.kt`、`AndroidManifest.xml`：監視の開始停止と必要設定。
- 変更 `eew/EewReceiptStore.kt`と受信処理：端末受信時刻の保持・更新通知。住民報告は`core/QuakeLifecycle.kt`の`firstReportedAt`の投影を使う。発生時刻パーサーの追加は不要。既存災害成立条件自体は変更しない。
- 変更 `data/PlanDatabase.kt`、`core/Resident.kt`の`RecordStore`、`core/SyncEngine.kt`と既存共有アダプター：保存と交換。実装着手時に現在のStoreインターフェースを確認する。
- 変更 `PlanViewModel.kt`、`ui/PlanApp.kt`、`ui/ResidentMap.kt`、`ui/SituationPresentation.kt`の`residentMapPerson`、`ui/PositionSettings.kt`：災害別観測を各表示経路へ接続。
- 変更 `demo/index.html`、`demo/app.mjs`、`demo/data.mjs`、必要なら`demo/people-view.mjs`：既存比較を残して更新版を追加。

計画用インターフェース案：

```kotlin
data class ImpactCandidate(val id: String, val groupId: String, val originId: String,
    val observedAt: Long, val elapsedNanos: Long, val peakMps2: Double, val widthNanos: Long,
    val methodVersion: String, val training: Boolean)
enum class ImpactWindowBasis { ALERT_RECEIVED, FIRST_REPORT }
data class QuakeImpactWindow(val groupId: String, val incidentId: String, val sourceEventId: String,
    val referenceAt: Long, val basis: ImpactWindowBasis, val training: Boolean)
data class ImpactObservation(val candidate: ImpactCandidate, val incidentId: String,
    val sourceEventId: String)
fun inImpactWindow(observedAt: Long, referenceAt: Long): Boolean
fun matchImpacts(candidates: List<ImpactCandidate>, windows: List<QuakeImpactWindow>): List<ImpactObservation>
fun latestImpactAt(observations: List<ImpactObservation>, incidentId: String, originId: String): Long?
interface ImpactDetector { fun accept(sample: AccelerationSample): ImpactCandidate?; fun reset() }
```

`QuakeImpactWindow`へ渡す段階で対象グループ・地域と災害IDを確認し、照合でもグループと実災害／訓練の一致を必須にする。通常の実センサー候補は実観測、訓練入力は明示的に分離する案とし、実観測を訓練へ流用する仕様は追加しない。候補の位置は既存PhonePositionの取得時刻付きスナップショットとして別途保存し、位置がなくても候補を作る。地震窓の版・取消を保持し再投影できるよう、採用観測の単一フラグだけを共有しない。具体的交換フィールドはTask 5で既存形式に合わせる。

### Task 0: 仕様判断の確定

- [x] 基準Tは速報の端末受信時刻／住民の最初の報告時刻とする利用者回答を設計案へ反映した。
- [x] 速報と報告が併存する場合は両窓の和集合（どちらかに入れば採用）とする回答を反映した。
- [x] 同じ地震の速報続報も、受信時刻の1分前〜10分後を和集合に追加する回答を反映した。
- [x] 未採用候補は直近24時間を保持し、それより古いものを削除する回答を反映した。
- [x] 採用した加速度衝撃記録も直近24時間のみ保持する回答を反映した。vivo落下の既存30日は変更しない。
- [x] 取消速報だけに一致する衝撃は表示から除外し、他の有効な窓にも一致する衝撃は維持する回答を反映した。
- [ ] アプリ内災害への紐付けを確認する。
- [x] 複数衝撃は最新日時を表示し、各回の記録を24時間保持する回答を反映した。
- [ ] 異なる地震の紐付けを確認する。閾値はTask 2の根拠を見て通常版有効化前に確認する。
- [ ] 未回答は該当機能の実装を保留し、値を仮設定して通常版へ混入させない。

### Task 1: 消灯中に成立する観測基盤

**Files:** `impact/ImpactMonitoring.kt`、`impact/ImpactWakePolicy.kt`、`motion/AndroidMotionSource.kt`、`location/PositionService.kt`、`PlanDApplication.kt`、Manifest。テスト：新規`ImpactMonitoringTest.kt`、`ImpactMonitoringDeviceTest.kt`。

**Interfaces:** `ImpactMonitoring.start(token: Long, group: String)` / `stop(token: Long)` / `state: StateFlow<ImpactMonitoringState>`。状態は停止・設定不足・観測中・中断を区別し、観測中は登録成功だけでなくサンプル受信で遷移する。

- [x] 偽MotionSourceで開始・停止・グループ変更・センサー不在・設定不足・サンプル停止のテストを先に書き、未実装参照による失敗を確認した。8件実装後成功。
- [ ] AndroidMotionSourceを設定可能にし、既存debug校正呼び出しを維持。wake-upセンサーがあれば能力を取得、なければnon-wake-up経路を選ぶ。
- [x] 非wake-up用の部分WakeLockとDoze設定確認を追加。位置記録サービスと同じ開始停止に結び、別の常駐サービス・定期アラームを追加しない。実機証明は未実施。
- [ ] ManifestにWAKE_LOCKと必要なFGS宣言を追加。PositionServiceの`startForeground`に渡す実行時種別も、現在のLOCATION単独から対象APIに応じた構成へ変更する。最適化設定への標準導線を設け、利用者の実操作で許可された状態を確認する。
- [x] `:app:testDebugUnitTest`で既存を含む139件成功。エミュレーターの背景移行・Activity再作成・通知停止試験を確認。実機センサー性能は未確認。
- [ ] 実機の受信検証をTask 7手順で先に実施する。消灯・Dozeで未成立なら電力測定へ進まず原因と未達条件を報告する。UI・純ロジックは独立して作業可能。

### Task 2: 衝撃候補の検出

**Files:** `impact/ImpactDetector.kt`、`core/ImpactObservation.kt`。テスト：新規`ImpactDetectorTest.kt`、架空／出典確認済み波形fixture。

**Interfaces:** 上記ImpactDetector。判定設定は単位・重力の扱い・方法版を持ち、未確定の本番値をデフォルトに置かない。

- [ ] 静止、歩行相当、衝撃パルス、閾値境界、回転、連続する衝撃、欠測・飽和の波形試験を作成し、未実装で失敗を確認する。
- [ ] 合成加速度とピーク・幅を使う最小検知器を実装し、自由落下や地震通知の到着を必要条件にしない。
- [ ] `:app:testDebugUnitTest --tests '*ImpactDetectorTest'`を通し、50/100/200Hzなどの再標本化で見落とす幅を資料化する。これを端末実測や地震実証と表記しない。
- [ ] 安全な日常動作の記録と既存使用可能波形から誤検知を評価し、閾値・幅・まとめ間隔・目標性能を利用者へ提示して確定する。高所落下を要求しない。
- [ ] 確定した設定でテストを固定し、サンプリング周波数と許容欠測も決める。未確定なら試験実装に留める。

### Task 3: 時刻窓と端末内保存

**Files:** `core/ImpactObservation.kt`、`data/ImpactStore.kt`、`data/PlanDatabase.kt`。テスト：新規`ImpactWindowTest.kt`、`ImpactStorageTest.kt`。

**Interfaces:** `inImpactWindow`、`ImpactStore.appendCandidate(...)`、`observe(incidentId)`、`reconcile(windows)`、`prune(now)`。保持ポリシーはTask 0で決めた値のみ使用する。

- [ ] `T=1_000_000`に対して`T-60_001=false`、`T-60_000=true`、`T+600_000=true`、`T+600_001=false`の試験を作る。採用する端点仕様を確認後に固定。
- [ ] 観測後にアプリを閉じても候補が残り、遅延した地震入力でも観測時刻で一致する試験を書く。対象外破棄と保持期限境界も追加する。
- [ ] 現行DBから破壊しない移行を追加。連続波形は保存せず候補単位で書く。衝撃の生時刻と換算時刻、照合した基準Tと種別を保存し、センサーの配信遅延で衝撃の観測時刻を動かさない。
- [ ] 単体と端末DB移行試験を実行。既存の位置・安否・救助・地震記録が保持されることを確認する。

### Task 4: 地震入力と照合

**Files:** `impact/QuakeImpactMatcher.kt`、`eew/`受信時刻保存・更新通知、`core/QuakeLifecycle.kt`の投影接続、`PlanDApplication.kt`。テスト：新規`QuakeImpactMatcherTest.kt`、既存`EewDeliveryTest.kt`。

**Interfaces:** `matchImpacts`と`QuakeImpactWindow`。入力アダプターが基準種別・referenceAt・sourceEventId・incidentIdを供給する。

- [ ] 速報では発表時刻を変えても窓が変わらず、端末受信時刻を変えると窓が変わる試験を書く。住民報告では共有到着時刻を変えても窓が変わらず、最初の報告時刻が変わると再照合する試験を書く。取消、訓練分離、重なる窓とTask 0で確定した続報規則も試験する。
- [ ] 速報の端末受信時刻と住民報告のfirstReportedAtを取得する。欠損は未確定扱いとし、issue.timeや地震発生時刻で代用しない。共有先で再受信した時刻へ基準を置換しない。速報と報告の統合はTask 0で確定した規則のみ実装する。
- [ ] 新規候補と地震入力更新の両方で再照合する。災害終了操作と10分窓を混同せず、地震終了操作で監視を止めない。
- [ ] 模擬地震で全経路をテストする。本番上流の429が未解消なら別依存として残し、FCM模擬通知を本番地震受信の成功に数えない。取得方式をこの作業で勝手に変更しない。

### Task 5: 既存共有への接続

**Files:** `core/SyncEngine.kt`、`core/Resident.kt`の`RecordStore`、各Storeアダプター、対応する`lab/web/sync.mjs`等。テスト：新規`ImpactSyncTest.kt`と既存交換試験。

**Interfaces:** 安定した観測ID、出典地震IDと更新版を持つ採用観測の交換。候補・波形は端末内限定。

- [ ] 交換後に同じ災害／人物の同じ観測が得られる、重複で増えない、訂正・取消で古い表示が戻らない、異なるグループが混ざらない試験を書く。
- [ ] 既存交換経路へ観測と必要な関連付け更新を追加。既存メッセージとの互換性を確認し、未対応相手への方針を仕様として明記する。
- [ ] JVM/JSの交換試験を実施。1台でも模擬相手で検証を進め、2台実機の消灯中交換は未実施項目として残せるが完了済みにしない。

### Task 6: 既存の衝撃表示へ接続

**Files:** `PlanViewModel.kt`、`ui/PlanApp.kt`、`ui/ResidentMap.kt`、`ui/SituationPresentation.kt`の`residentMapPerson`、`ui/PositionSettings.kt`、demo。テスト：既存`SituationPresentationTest.kt`、新規`ImpactFlowDeviceTest.kt`、demo試験。

**Interfaces:** `latestImpactAt`で選択災害・人物から`MapPerson.impactAt`へ投影。複数観測は最新日時を表示する（確認済み）。

- [ ] 対象時間内でのみ既存マークが出る、広域では出ない、詳細日時一致、災害切替・取消で更新されるテストを書く。地図から／一覧から／ホームからの詳細をすべて確認する。
- [ ] 既存SVG・レイアウトを維持して実データを接続。監視設定不足・中断だけを位置記録画面と既存通知へ簡潔に表示し、位置記録中と衝撃観測中を誤って同一視しない。
- [ ] demoに架空データの対象内／対象外・遅延入力・災害切替を用意し、既存版を残した右側の更新版に版と変更説明を付ける。
- [ ] Webで操作確認後、既存GitHub Pages経路で公開し反映を確認する。未公開なら未反映と記録する。完成を示すために新しい外部ホスティングは作らない。

### Task 7: 消灯状態の実機合格と通し検証

**Files:** `ImpactMonitoringDeviceTest.kt`、`ImpactFlowDeviceTest.kt`、debug専用の集計ログ、実装結果文書。

- [ ] 試験開始前に端末状態・必要設定・復元対象を記録。操作と待ち時間を説明する。変更するのは同意された試験設定だけ。再起動しない。
- [ ] 自動試験：消灯・ロック・アプリ非表示で10分程度、debug擬似衝撃→検知器→保存→模擬地震→表示データまで確認。これは配線試験で物理センサーの合格と区別する。
- [ ] 実センサー試験：USBを外し20〜30分を目安に静置し、自然な省電力遷移とデータを端末内へ集計。別に強制Dozeの試験状態を作る場合は事前説明し、復旧も行う。画面点灯で試験を成立させない。
- [ ] 深い省電力に入った証拠、実サンプル数・時刻間隔・遅延・欠測を回収する。Doze滞在区間とサンプル時刻を照合し、その区間内の継続受信を確認する。刺激で省電力が解除される可能性があるため刺激時の状態は別記し、試験中のどこかでDozeだっただけで合格としない。所要時間だけ経過してDoze未到達なら「深い省電力未検証」。許容欠測はTask 2の対象パルス幅から設定し、以前の1秒基準を衝撃検知へ流用しない。
- [ ] 日常動作・合意した安全な刺激で実測候補を作り、消灯中の候補保存と、画面復帰後のマーク・日時を照合する。模擬値と実センサー値を明確に区別する。
- [ ] 通常停止・再開、OSによるプロセス再作成、位置権限変更、グループ変更を確認し、観測中断と復帰を確認。強制停止・電源断時に観測可能とは報告しない。
- [ ] `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug`、対象端末テスト、demoテストを実行し結果を記録する。
- [ ] 電力は機能成立後に候補方式の追加消費を評価。以前の短時間残量テストを繰り返さず、利用者が結果で採用判断する。低消費未確認を隠して運用版の採用完了としない。

## 実行順と報告単位

Task 0で判断を確認しつつTask 1を最初の技術合格点にする。Task 2〜4は模擬入力・純Kotlin・保存試験で独立して進められる。Task 5〜6で既存の共有と表示へ接続し、Task 7で実センサーから表示まで確認する。

報告は「消灯中の観測」「衝撃判定」「地震時間窓」「保存・共有」「表示・公開」「追加電力」を分け、実装済み／試験入力で確認／実機確認／未確認を明記する。plan.txtへの反映や全既存変更の一括コミットはしない。レビュー済みの関連ソースだけを実装単位でコミットする。

## 計画レビュー記録

2026-10-25：設計とタスクの対応、型、既存ファイル参照を自己確認。別担当の読み取りレビューで重大な要件漏れは指摘されず、訓練入力の分離、FGSの実行時種別、Doze滞在区間とサンプルの時刻対応を追記した。設計案の判断表は未確定のまま明示している。これは計画のレビューであり、実装・端末試験の合格を意味しない。

同日追記：利用者回答に基づき、基準を物理的な地震発生時刻から「速報の端末受信時刻／住民の最初の報告時刻」へ変更。型・保存対象・試験期待値を更新し、発生時刻抽出の依存を除去した。実装・実機試験は行っていない。
