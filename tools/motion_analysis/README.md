# 加速度校正ファイルの集計

`MotionDeviceTest#captureCalibrationOnRequest` が明示的に保存したJSONをPCへ取得して使う。実測の操作と注意点は[実装記録](../../docs/superpowers/plans/2026-10-24-motion-observation-results.md)を参照。

```powershell
python -m unittest discover -s tools/motion_analysis -p "test_*.py"
python tools/motion_analysis/summarize.py .implementation/motion-captures/*.json
```

シェルがワイルドカードを展開しない場合は実際のファイル名を列挙する。Python標準ライブラリのみ使用する。

センサー時刻から実際の平均配信頻度と最大間隔を計算し、重力込みの加速度を標準重力9.80665m/s²で割ってg単位の平均・中央値・95/99パーセンタイル・最大値を返す。パーセンタイルは並べ替え後の位置を線形補間する。センサー軸の最大範囲から分解能以内にある点を数え、飽和の疑いを調べる材料とする。重力除去・衝撃判定・危険判定は行わない。

不正な時刻順、非有限値、3軸と合成加速度の不一致、別の測定方式を拒否する。ファイル内のラベルは人が付けた動作名であり、正解の衝撃ラベルではない。エミュレーター値を実機の校正や精度評価へ流用しない。

測定ファイルは個人の端末データとしてGit対象外の `.implementation/` 等に置き、公開しない。
