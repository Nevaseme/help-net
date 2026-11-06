# Help Net

災害時の安否や救助の記録を端末間で共有するアプリ。開発中。

製品名はHelp Net（旧称：Tasukeai／PlanD／案D）。開発用の識別名や過去の記録との対応は[開発資料](docs/README.md)を参照。

[地図デモを見る](https://nevaseme.github.io/safety_confirm/)

- [Androidアプリ](android/)
- [PWA・中継サーバーの実験](lab/)
- [設計・開発資料](docs/)

## ビルド

`android/` をAndroid Studioで開く。最小SDKは24。

作業場所は `.` に統一する。別のworktreeや旧Android Studioフォルダーは編集・公開元に使わない。

```powershell
cd android
.\gradlew.bat :app:assembleDebug
```

PWAは `lab/web/`、公開用APIと配信処理は `cloud-sync/`、比較サイトは `demo/`。
本フォルダーからの確認・公開手順は [統合後の作業手順](docs/workspace.md) を参照。
