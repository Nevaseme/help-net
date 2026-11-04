# Kyoto demo address display aliases

Reviewed 2026-11-04. Scope: the 12 unique published building addresses already
present in `cloud-sync/src/venue-buildings.json`, covering its 64 simulated home
placements in Kyoto Gosho south / Karasuma Oike. This does not translate other
addresses in the bounding box or elsewhere in Japan.

`lab/web/demo-address-display.mjs` exports `DEMO_ADDRESS_LABELS` and
`demoAddressLabel(address, english = false)`. The mapping contains exact full
Japanese address keys and plain ASCII display labels. Android uses the same
keys and labels in its display layer. Japanese mode and unmatched input return
the original value unchanged, including whitespace, near matches, unknown lot
numbers, building names, and user-entered notes. Apply the helper to the address
field before formatting separate floor/room fields. It does not parse combined
address/floor/room strings or invent real units for simulated placements.

The saved Japanese addresses, IDs, names, coordinates, catalogue evidence, and
exports remain the source of truth. English aliases are for display only and
must not be written back to records or used as search/geocoding input.

## Reading evidence

Japan Post's official postal-address pages provide the kana readings below.
The ASCII romanizations and English address ordering are editorial display
choices derived from those readings, not a claim of officially registered
English addresses. Long vowels use plain ASCII without macrons; familiar Kyoto
and Nakagyo spellings are used. Building brand names are not translated.

| Town | Confirmed reading | Display romanization | Primary source |
| --- | --- | --- | --- |
| 少将井町 | ショウショウイチョウ | Shoshoicho | [Japan Post 604-0862](https://www.post.japanpost.jp/cgi-zip/zipcode.php?zip=6040862) |
| 御池之町 | オイケノチョウ | Oikenocho | [Japan Post 604-0022](https://www.post.japanpost.jp/cgi-zip/zipcode.php?zip=6040022) |
| 俵屋町 | タワラヤチョウ | Tawaraya-cho | [Japan Post 604-0966](https://www.post.japanpost.jp/cgi-zip/zipcode.php?city=1261040&id=101683&merge=&pref=26) |
| 高宮町 | タカミヤチョウ | Takamiya-cho | [Japan Post 604-0835](https://www.post.japanpost.jp/cgi-zip/zipcode.php?zip=6040835) |
| 守山町 | モリヤマチョウ | Moriyama-cho | [Japan Post 604-0953](https://www.post.japanpost.jp/cgi-zip/zipcode.php?city=1261040&id=101921&pref=26) |
| 毘沙門町 | ビシャモンチョウ | Bishamon-cho | [Japan Post 604-0981](https://www.post.japanpost.jp/cgi-zip/zipcode.php?zip=6040981) |
| 六丁目 | ロクチョウメ | Rokuchome | [Japan Post 604-0965](https://www.post.japanpost.jp/cgi-zip/zipcode.php?zip=6040965) |

Some Kyoto town names occur more than once within the ward. The selected postal
pages correspond to the existing demo catalogue locations (俵屋町 here is the
夷川通富小路西入 / 夷川通柳馬場東入 town; the [Kyoto Prefecture contractor
directory](https://www.pref.kyoto.jp/kensetugyo/documents/kyokameibor6.pdf)
also identifies 俵屋町292 under postal code 6040966). No postal code or street qualifier is
added to the displayed labels because it was not part of the catalogue address.
Published building-address evidence, rather than the postal pages, supports the
lot numbers; see each catalogue home's `addressEvidence`.

## Exact mapping

| Japanese source address | English display label |
| --- | --- |
| 京都府京都市中京区少将井町245-1 | 245-1 Shoshoicho, Nakagyo-ku, Kyoto, Kyoto Prefecture |
| 京都府京都市中京区御池之町301 | 301 Oikenocho, Nakagyo-ku, Kyoto, Kyoto Prefecture |
| 京都府京都市中京区御池之町302 | 302 Oikenocho, Nakagyo-ku, Kyoto, Kyoto Prefecture |
| 京都府京都市中京区御池之町306 | 306 Oikenocho, Nakagyo-ku, Kyoto, Kyoto Prefecture |
| 京都府京都市中京区俵屋町292 | 292 Tawaraya-cho, Nakagyo-ku, Kyoto, Kyoto Prefecture |
| 京都府京都市中京区俵屋町293-1 | 293-1 Tawaraya-cho, Nakagyo-ku, Kyoto, Kyoto Prefecture |
| 京都府京都市中京区俵屋町298-1 | 298-1 Tawaraya-cho, Nakagyo-ku, Kyoto, Kyoto Prefecture |
| 京都府京都市中京区俵屋町296 | 296 Tawaraya-cho, Nakagyo-ku, Kyoto, Kyoto Prefecture |
| 京都府京都市中京区高宮町219 | 219 Takamiya-cho, Nakagyo-ku, Kyoto, Kyoto Prefecture |
| 京都府京都市中京区守山町156-3 | 156-3 Moriyama-cho, Nakagyo-ku, Kyoto, Kyoto Prefecture |
| 京都府京都市中京区毘沙門町546 | 546 Bishamon-cho, Nakagyo-ku, Kyoto, Kyoto Prefecture |
| 京都府京都市中京区六丁目290 | 290 Rokuchome, Nakagyo-ku, Kyoto, Kyoto Prefecture |
