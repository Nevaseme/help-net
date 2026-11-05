# メニューの情報整理

対象：AndroidのハンバーガーメニューとPWAの対応するメニュー。記録、権限、通信処理は変更しない。

## 実装前の設計

- 色：白 #FFFFFF、文字 #183447、補助 #536478、操作 #155EEF、区切り #D6E0E8、選択面 #F3F7FB。
- 書体：既存Noto Sans JP／OSの日本語書体。タイトル23、項目16、区分13。左揃え、押下領域48以上、項目の高さは文字拡大に追従。
- 構成：見出しと閉じるボタンは固定。スクロールする項目を3群に分ける。行間で関係を示し、説明文やカード装飾は増やさない。

```text
メニュー                         閉じる
登録情報
  自分の情報・住居                 >
  グループ                        >
  建物・住戸                      >

災害への備え
  災害・訓練                      >
  オフライン地図                  > Androidのみ
  位置記録                        > Androidのみ

アプリ設定
  言語                            >
  アプリ情報                      > Androidのみ
```

下部の「対応」と重複する入口、開発用「画面デモ」は通常メニューから除去。災害・訓練は一般参加者にも既存の確認・投票操作があるため、管理者専用と誤表示しない。作成・終了の権限制御は既存画面で維持。

自己レビュー：華美なカードや全項目の説明文は防災用途の素早い探索に不向きなので不採用。既存の4タブと安否色を維持し、メニュー内の情報順序と区切りだけで改善する。PWAには未実装機能を追加表示しない。

## 参考

- Apple HIG Sidebars: https://developer.apple.com/design/human-interface-guidelines/sidebars — 短いグループ名と浅い階層。
- Apple HIG Layout: https://developer.apple.com/design/human-interface-guidelines/layout — 整列と視覚的優先順位。
- デジタル庁ドロワー: https://design.digital.go.jp/dads/components/drawer/ — 展開するナビゲーションコンテナ。
- GOV.UK Service navigation: https://design-system.service.gov.uk/components/service-navigation/ — 一貫したナビゲーション。
- Google Material navigation drawer: https://m3.material.io/components/navigation-drawer/guidelines — 参照先確認済み。ただし本文はJS依存で取得できず、詳細規則の根拠には用いない。

## 検証方針

Android実機・エミュレーターで開閉、全項目の遷移、下端までスクロール、文字拡大を確認。PWAは専用ブラウザーで小画面・通常・文字拡大・英語・キーボード操作を確認。Browser plugin not availableのため既存承認済みPlaywrightを使用する。比較公開には架空データのみ使用。

## 実施結果

- Android1.5.36/code49：エミュレーターとvivoで8項目の遷移と閉じる・戻るが成功。エミュレーター文字1.5倍で末尾への移動も成功し、1.0へ復元。
- PWA1.6.10：幅320/400/1280、文字2倍、英語を含む4条件で横溢れなし、48px以上の項目、閉じる固定、Escと元ボタンへのフォーカス復帰が成功。5入口の遷移を確認。API通信はテストで遮断し架空ローカル記録のみ使用、グループの作成・同期自体はこの試験に含めない。
- Esc処理をオフライン準備完了待ちの後に置くと、準備待ち中に登録されないため、他のメニューイベントと同じ初期化位置へ移動。
- PWA公開6ファイル一致（CRLF/LFのみ正規化）。Cloudflare deployment `307ab81930b64f08947076b787a9e4c8`。バックエンドと既存グループDBは維持。
- 比較main `2e49bad`にAndroid・PWAの実装画像を2列追加。証跡は `output/menu-1536/`。iPhone実機での再確認は未実施。
- 既存実装の「Web共通項目の末尾へ追加」という方針は今回の目的別構成で置換。「設定」の中身が言語だけだったため、入口を具体的な「言語」に改称。
