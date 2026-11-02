# EEW disaster connection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 受信済み速報の保存・通知を、災害開始と既存の初回安否回答へつなぐ。
**Architecture:** 既存FCM受信を入口に、人数報告とは区別した速報成立根拠を保存する。既存の管理者終了・災害統合・保存後共有・画面投影へ接続する。
**Tech Stack:** Kotlin, Room, Compose, Firebase Messaging、既存JavaScript実験PWA。
**Spec:** ../specs/2026-10-30-eew-disaster-connection-design.md

## Global Constraints

- 京都府南部、発表から3分以内。取消後も管理者終了まで継続。震度は任意。
- minSdk24、画面デザイン・建物評価値・クラウド構成は変更しない。
- 既存記録・未コミットの地図改善・別チャットのWeb作業を保持。plan.txtは変更しない。
- 仕様を変えずに実装できない点は実装前に報告する。既存の参加者設定に存在しない管理者を補完しない。

## Review Focus

- 通知の重複抑制で保存失敗の再試行まで止めない（Task 2）。
- 管理者終了後に別端末から同じ地震の続報が到着しても再開しない（Task 1）。
- 人数による成立と速報が逆順に保存・共有されても回答を保持する（Task 1）。
- 保存された古い履歴を起動時に新しい災害として採用しない（Task 2）。
- 古い同期形式の相手に対応不能な新記録を送って保存済みと誤認しない（Task 3）。

### Task 1: 速報の成立根拠と既存災害状態

**Files:** core/QuakeLifecycle.kt、data/QuakeStore.kt、必要な新規coreの速報根拠型、対応するJVMテスト。パスはいずれもandroid/app/src以下の既存パッケージ。

- [x] 通常警報・震度欠損・地域外・訓練・取消先着・3分境界を固定入力で試験し、未実装の失敗を確認する。
- [x] 人数条件を維持したまま速報による成立を表現し、QuakeStoreのトランザクションで保存・投影する。
- [x] QuakeLifecycleとAndroid/PWAの投影が人のREPORTを前提にしている箇所を併せて修正する。速報専用の根拠、期間への割当て、START生成、災害IDの別名統合を一貫させる。
- [x] 同一地震・続報・手動成立との統合・取消後の継続・管理者終了と後着続報・順序反転の試験を通す。
- [x] 2端末が同じ地震の異なる報数を受信し、片方で管理者終了してから双方向に記録交換する試験を行う。受信時刻・報数に依存しない地震の同一性と、端末ごとの記録IDの衝突防止を確認する。

### Task 2: FCM受信と安否画面への接続

**Files:** eew/EewReceiver.kt、eew/EewMessagingService.kt、eew/EewReceiptStore.kt、PlanDApplication.kt、data/InstallationSettings.kt、必要ならPlanViewModel.kt。既存画面の配置は変えない。

- [x] 保存失敗後の同一報再試行、再起動、グループ変更、過去履歴からの不正開始防止を試験する。
- [x] 受信時のグループ・受信時刻・処理状態を保存し、通知の重複抑制と保存処理の再試行を分ける。有効な参加者設定がない受信を、後の別グループへ付け替えない。
- [x] FCM入口から受信・記録・災害投影を完了し、成功した記録を対象として通知と画面選択を反映する。
- [x] FCMコールバック終了前に永続保存の完了を待つ。DB保存は成功し表示選択だけ失敗した中断から、再試行で正しい災害選択に復帰できることを確認する。
- [x] 独立した試験グループで、背景受信後に初回回答が現れ、回答が保存され地図へ進むことを確認する。
- [x] 正常系の試験は独立DB・グループへ通常速報と同形式の入力を与える。診断・訓練報を実災害開始に利用できるような抜け道を作らない。

### Task 3: 同期互換性と回帰確認

**Files:** 必要に応じcore/SyncEngine.kt・通信形式、lab/web/managed-records.mjs・managed-store.mjs・sync.mjs、既存Android/PWAテスト。公開demo/は編集しない。

- [x] 新しい成立根拠の共有・保存後ACK・再送・順不同・管理者終了をAndroid/PWA双方で検証する。旧形式へ新しい根拠を偽装しない。
- [x] QuakeEventだけでなく、別経路で共有される投影済みIncidentEventのSTARTも互換性の対象にする。旧相手が速報STARTだけを受け取り、管理者終了を理解できない状態を作らない。
- [x] Android単体試験、既存Node試験、debugビルドを実行し結果を確認する。
- [x] 端末の結合試験は地図改善担当の使用後に実行し、対象版・実施済み・未確認を外部検証記録とSTATUS.mdへ整理する。

変更が共存するチェックアウトなので一括コミットはしない。実装の選択で同期形式の移行など利用者の操作条件が変わる場合は、その変更だけを具体化して確認する。

## 実施結果（2026-10-30）

通常版1.5.14/code27で実装。JVM160件、Node56件、エミュレーターとvivoの独立Room試験各3件、エミュレーターの実画面フロー1件が成功。実画面試験では旧ボタン文言の試験修正後、試験開始前のBIND_APPLICATION_ANRが1回あり、再実行で成功した。実地震の上流→FCM遅延の測定とは別。詳細はローカル検証記録 `eew-connection-2026-10-30.md`。後続の共有サーバー・招待参加は追加計画B8で扱う。
