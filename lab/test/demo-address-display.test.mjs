import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {DEMO_ADDRESS_LABELS, demoAddressLabel} from '../web/demo-address-display.mjs';

const catalogue = JSON.parse(await readFile(new URL('../../cloud-sync/src/venue-buildings.json', import.meta.url)));

test('English display covers exactly the verified demo addresses without changing catalogue records', () => {
  const before = JSON.stringify(catalogue);
  const addresses = [...new Set(catalogue.homes.map(home => home.address))];
  assert.equal(addresses.length, 12);
  assert.deepEqual(Object.keys(DEMO_ADDRESS_LABELS).sort(), addresses.sort());
  for (const home of catalogue.homes) {
    assert.equal(home.addressPrecision, 'verified-building-address');
    assert.match(demoAddressLabel(home.address, true), /^[\x20-\x7e]+$/);
    assert.equal(demoAddressLabel(home.address, false), home.address);
    assert.equal(demoAddressLabel(home.address), home.address);
  }
  assert.equal(JSON.stringify(catalogue), before);
});

test('unknown parcels, similar town names, unit notes and arbitrary text are never guessed', () => {
  for (const address of [
    '京都府京都市中京区少将井町245-10',
    '京都府京都市中京区少将井町245',
    '京都府京都市中京区少将井御旅町245-1',
    '京都府京都市中京区少将井町245-1 2階 201号室',
    '京都府京都市中京区少将井町245-1・裏口から入る',
    '京都府京都市中京区少将井町245-1 ',
    '京都府京都市中京区少将井町付近',
    '地図で選択した住居',
    '建物名・自由記述',
    'toString', '__proto__', '', null, undefined,
  ]) assert.equal(demoAddressLabel(address, true), address);
});

test('confirmed readings preserve non-obvious town names and exact lot numbers', () => {
  assert.equal(demoAddressLabel('京都府京都市中京区少将井町245-1', true), '245-1 Shoshoicho, Nakagyo-ku, Kyoto, Kyoto Prefecture');
  assert.equal(demoAddressLabel('京都府京都市中京区六丁目290', true), '290 Rokuchome, Nakagyo-ku, Kyoto, Kyoto Prefecture');
  assert.equal(demoAddressLabel('京都府京都市中京区俵屋町293-1', true), '293-1 Tawaraya-cho, Nakagyo-ku, Kyoto, Kyoto Prefecture');
});
