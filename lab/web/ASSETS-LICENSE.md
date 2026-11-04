MIT License

Copyright (c) 2023 Phosphor Icons

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
## City map and address data (2026-11-01)

- Boundaries: 国土数値情報 N03, 2025, 国土交通省. Original URLs and hashes are recorded in each public `data/cities/{code}/city.json`.
- Address index: Geolonia `japanese-addresses-v2`, derived from デジタル庁アドレス・ベース・レジストリ, CC BY 4.0. Normalized into searchable city packages; town representative points remain distinct from coordinate-bearing parcel entries.
- Kyoto building footprints and attributes: 京都市・国土交通省 Project PLATEAU, 2025. Original mesh URLs/hashes and processing/model attribution are included in the building index.
- Background map: OpenFreeMap / OpenMapTiles / OpenStreetMap contributors (ODbL). The mirrored public snapshot retains original resource URLs and hashes in the map manifest. Noto Sans glyphs use the SIL Open Font License; see the accompanying `map-assets/OFL.txt`.
- MapLibre GL JS 6.16.0 (BSD-3-Clause), `maplibre-LICENSE.txt`; MapLibre GL Leaflet 0.1.4 (ISC), `leaflet-maplibre-LICENSE.txt`.



京都市・熱海市の `places.json.gz` は、既存の OpenFreeMap / OpenMapTiles タイルの名前付き POI から生成した施設検索索引です。© OpenStreetMap contributors, ODbL 1.0。https://www.openstreetmap.org/copyright 。生成元・件数・整合性ハッシュは各市の `city.json`、生成手順は `tools/building_analysis/build_city_places.py` に保存しています。

## Address package v2 (2026-11-03)

Kyoto, Atami, and Minato address indexes combine ABR-derived Geolonia v2 (CC BY 4.0) with 国土交通省・位置参照情報 2025, release 24.0a (https://nlftp.mlit.go.jp/isj/ ; 公共データ利用規約 https://nlftp.mlit.go.jp/ksj/other/agreement.html). Source files and SHA-256 checksums appear in each city manifest. Modified into search rows; shared coordinate points are labeled block representatives. Facility indexes use OpenStreetMap contributors / OpenMapTiles / OpenFreeMap (ODbL), independently attributed from address rows.

Kyoto facility index additionally includes 1,077 named PLATEAU building points extracted from 242 checked CityGML files. Points lie inside the corresponding retained footprint; they are facility positions, not parcel address coordinates. Source mesh checksums and building IDs accompany the rows.

## Venue building addresses (2026-11-04)

The derived demonstration placement database is available at [demo-building-addresses.json](./demo-building-addresses.json), with geometry and source references at [demo-building-address-sources.json](./demo-building-address-sources.json). OpenStreetMap building names and geometry: © OpenStreetMap contributors, [ODbL 1.0](https://www.openstreetmap.org/copyright). The derived database is distributed under ODbL 1.0. PLATEAU geometry and attributes: 京都市・国土交通省 Project PLATEAU (2025); source assets and checksums accompany each row. Published building addresses are cross-checked against the cited sources. The 64 demonstration placements represent 12 buildings, not verified dwellings or cadastral parcels; no occupant information is included.

## Kyoto parcel candidates (2026-11-04)

京都市「京都市全域地番参考図」由来の国土交通省試行公開データ（ファイル名2025）を加工。原市データの基準日は未確認です。国土交通省: https://www.mlit.go.jp/tochi_fudousan_kensetsugyo/tochi_fudousan_kensetsugyo_tk17_000001_00063.html （公共データ利用規約1.0）、京都市原データ: https://data.city.kyoto.lg.jp/dataset/00677/ （CC BY 4.0）。地番区画内の代表点を検索用に生成し、PLATEAU建物形状との重なりから候補を生成しました。地番と郵便上の住所、建物入口は同一と保証しません。取得元・チェックサム・処理条件は各索引に記録しています。
