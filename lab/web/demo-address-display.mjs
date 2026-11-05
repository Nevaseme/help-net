// Display-only aliases for 12 verified buildings and 11 legacy approximate drill labels.
// Town readings: Japan Post; provenance and scope: docs/demo-address-display.md.
// This is deliberately not a general address translator or normalizer.
export const DEMO_ADDRESS_LABELS = Object.freeze({
  "京都府京都市中京区三本木五丁目東洞院通竹屋町付近": "Near Sanbongi Gochome, Higashinotoin-dori Takeyamachi, Nakagyo-ku, Kyoto",
  "京都府京都市中京区亀屋町付近": "Near Kameya-cho, Nakagyo-ku, Kyoto",
  "京都府京都市中京区塀之内町付近": "Near Heinouchi-cho, Nakagyo-ku, Kyoto",
  "京都府京都市中京区壺屋町付近": "Near Tsuboya-cho, Nakagyo-ku, Kyoto",
  "京都府京都市中京区天守町付近": "Near Tenshu-cho, Nakagyo-ku, Kyoto",
  "京都府京都市中京区少将井御旅町付近": "Near Shoshoi Otabicho, Nakagyo-ku, Kyoto",
  "京都府京都市中京区巴町付近": "Near Tomoe-cho, Nakagyo-ku, Kyoto",
  "京都府京都市中京区松屋町付近": "Near Matsuya-cho, Nakagyo-ku, Kyoto",
  "京都府京都市中京区楠町付近": "Near Kusunoki-cho, Nakagyo-ku, Kyoto",
  "京都府京都市中京区真如堂町付近": "Near Shinnyodo-cho, Nakagyo-ku, Kyoto",
  "京都府京都市中京区鍵屋町付近": "Near Kagiya-cho, Nakagyo-ku, Kyoto",
  '京都府京都市中京区少将井町245-1': '245-1 Shoshoicho, Nakagyo-ku, Kyoto, Kyoto Prefecture',
  '京都府京都市中京区御池之町301': '301 Oikenocho, Nakagyo-ku, Kyoto, Kyoto Prefecture',
  '京都府京都市中京区御池之町302': '302 Oikenocho, Nakagyo-ku, Kyoto, Kyoto Prefecture',
  '京都府京都市中京区御池之町306': '306 Oikenocho, Nakagyo-ku, Kyoto, Kyoto Prefecture',
  '京都府京都市中京区俵屋町292': '292 Tawaraya-cho, Nakagyo-ku, Kyoto, Kyoto Prefecture',
  '京都府京都市中京区俵屋町293-1': '293-1 Tawaraya-cho, Nakagyo-ku, Kyoto, Kyoto Prefecture',
  '京都府京都市中京区俵屋町298-1': '298-1 Tawaraya-cho, Nakagyo-ku, Kyoto, Kyoto Prefecture',
  '京都府京都市中京区俵屋町296': '296 Tawaraya-cho, Nakagyo-ku, Kyoto, Kyoto Prefecture',
  '京都府京都市中京区高宮町219': '219 Takamiya-cho, Nakagyo-ku, Kyoto, Kyoto Prefecture',
  '京都府京都市中京区守山町156-3': '156-3 Moriyama-cho, Nakagyo-ku, Kyoto, Kyoto Prefecture',
  '京都府京都市中京区毘沙門町546': '546 Bishamon-cho, Nakagyo-ku, Kyoto, Kyoto Prefecture',
  '京都府京都市中京区六丁目290': '290 Rokuchome, Nakagyo-ku, Kyoto, Kyoto Prefecture',
});

/** Keep the original value for Japanese mode, unknown addresses, and free text. */
export function demoAddressLabel(address, english = false) {
  return english && typeof address === 'string' && Object.hasOwn(DEMO_ADDRESS_LABELS, address)
    ? DEMO_ADDRESS_LABELS[address]
    : address;
}
