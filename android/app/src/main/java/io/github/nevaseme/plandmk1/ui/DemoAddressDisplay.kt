package io.github.nevaseme.plandmk1.ui

// Exact display aliases only; preserve stored addresses and arbitrary user text.
// Readings and provenance: docs/demo-address-display.md.
private val demoAddressLabels=mapOf(
    "京都府京都市中京区三本木五丁目東洞院通竹屋町付近" to "Near Sanbongi Gochome, Higashinotoin-dori Takeyamachi, Nakagyo-ku, Kyoto",
    "京都府京都市中京区亀屋町付近" to "Near Kameya-cho, Nakagyo-ku, Kyoto",
    "京都府京都市中京区塀之内町付近" to "Near Heinouchi-cho, Nakagyo-ku, Kyoto",
    "京都府京都市中京区壺屋町付近" to "Near Tsuboya-cho, Nakagyo-ku, Kyoto",
    "京都府京都市中京区天守町付近" to "Near Tenshu-cho, Nakagyo-ku, Kyoto",
    "京都府京都市中京区少将井御旅町付近" to "Near Shoshoi Otabicho, Nakagyo-ku, Kyoto",
    "京都府京都市中京区巴町付近" to "Near Tomoe-cho, Nakagyo-ku, Kyoto",
    "京都府京都市中京区松屋町付近" to "Near Matsuya-cho, Nakagyo-ku, Kyoto",
    "京都府京都市中京区楠町付近" to "Near Kusunoki-cho, Nakagyo-ku, Kyoto",
    "京都府京都市中京区真如堂町付近" to "Near Shinnyodo-cho, Nakagyo-ku, Kyoto",
    "京都府京都市中京区鍵屋町付近" to "Near Kagiya-cho, Nakagyo-ku, Kyoto",
    "京都府京都市中京区少将井町245-1" to "245-1 Shoshoicho, Nakagyo-ku, Kyoto, Kyoto Prefecture",
    "京都府京都市中京区御池之町301" to "301 Oikenocho, Nakagyo-ku, Kyoto, Kyoto Prefecture",
    "京都府京都市中京区御池之町302" to "302 Oikenocho, Nakagyo-ku, Kyoto, Kyoto Prefecture",
    "京都府京都市中京区御池之町306" to "306 Oikenocho, Nakagyo-ku, Kyoto, Kyoto Prefecture",
    "京都府京都市中京区俵屋町292" to "292 Tawaraya-cho, Nakagyo-ku, Kyoto, Kyoto Prefecture",
    "京都府京都市中京区俵屋町293-1" to "293-1 Tawaraya-cho, Nakagyo-ku, Kyoto, Kyoto Prefecture",
    "京都府京都市中京区俵屋町298-1" to "298-1 Tawaraya-cho, Nakagyo-ku, Kyoto, Kyoto Prefecture",
    "京都府京都市中京区俵屋町296" to "296 Tawaraya-cho, Nakagyo-ku, Kyoto, Kyoto Prefecture",
    "京都府京都市中京区高宮町219" to "219 Takamiya-cho, Nakagyo-ku, Kyoto, Kyoto Prefecture",
    "京都府京都市中京区守山町156-3" to "156-3 Moriyama-cho, Nakagyo-ku, Kyoto, Kyoto Prefecture",
    "京都府京都市中京区毘沙門町546" to "546 Bishamon-cho, Nakagyo-ku, Kyoto, Kyoto Prefecture",
    "京都府京都市中京区六丁目290" to "290 Rokuchome, Nakagyo-ku, Kyoto, Kyoto Prefecture",
)
fun demoAddressLabel(address:String,language:String="ja")=if(language=="en")demoAddressLabels[address]?:address else address
