# 虎ノ門の建築年・構造を補完する候補

## 範囲拡大の実査

2025年度PLATEAUの追加6メッシュ（港区3、中央区・千代田区・新宿区各1）をストリームXML解析。合計11,351 Buildingレコードで yearOfConstruction / buildingStructureType は各0タグ。約7km×7kmの検索矩形からの抽出であり、その全域や東京全体を全棟調査したものではない。URL・SHA256・個別件数は同日付の expanded-attribute-audit JSON に保存。

範囲拡大だけで属性が増える根拠は今回の実査から得られなかった。PLATEAUの形状に、別資料の確定した属性を出典付きで追加する方が有望。

## 一次資料で見つかった補完候補

- 虎ノ門PFビル：1986年、RC、地上5階・地下2階。ユナイテッド・アーバン投資法人の公式物件資料。 https://www.united-reit.co.jp/ja/portfolio/detail.html?id=00164
- ザイマックス西新橋ビル：西新橋2-6-2、2000年8月22日、RC、9階。セントラル・リート投資法人の公式物件資料。 https://centralreit.co.jp/ja/portfolio/detail.html?id=0001
- 物産ビル：西新橋1-4-14、1982年3月、地上7階・地下1階、RC一部SRC。貸主の資料。複合構造なので、RCの曲線を無条件に適用しない。 https://www.mbtk.co.jp/business/buildinglist/000007.html
- 虎ノ門KTビル：1979年5月、SRC10階。2025年12月の事業者発表では建替計画あり。現存・解体の照合が必要。 https://www.nskre.co.jp/company/news/2025/20251208.html

上記は属性補完候補を発見した段階。PLATEAU IDとの照合・現存確認・計算・公開反映はまだ行っていない。古いPDFや竣工予定を現在の実績として採用しない。ビュレックス虎ノ門には2011年S13階の旧資料があるが、再開発での扱いの確認が必要なため即採用候補にはしない。

## 公的資料

- 東京都の建築計画概要書等電子閲覧：オンラインでPDFを無料取得可能。ただしアカウント登録、所管・対象年次・規模の制限があり、申請当時の地名地番で検索。登録に1週間程度かかる可能性が明記される。11/7までの一括補完の主経路には向かない。 https://www.toshiseibi.metro.tokyo.lg.jp/kenchiku_kaihatsu/denshi/kenchiku_denshi/denshi_etsuran_o
- 港区所管の概要書：1989年4月以降が対象、昭和分は廃棄済み。窓口閲覧用PCから物件を特定する案内。 https://www.city.minato.tokyo.jp/kenchikujimu/kenchikukeikakugaiyousyo1.html
- 港区の耐震診断結果公表：対象建物の評価・改修状況を補える。全住宅のデータベースではなく、診断区分をPlanD全壊率に直接変換するものでもない。 https://www.city.minato.tokyo.jp/taishinkasuishintan/kouhyou.html

## 推奨

現在の約2km四方で所有者・管理会社・設計者の公開資料を照合し、出典付き属性を追加する。16階未満のRC・S造で現在の計算に必要な条件が揃うものから候補を確定する。名称・住所・輪郭・建替状況を照合し、最寄りだけで結び付けない。追加できる棟数・網羅率はこの照合を終えるまで未確定。通常の民家を広く埋められるとはまだ言えない。
