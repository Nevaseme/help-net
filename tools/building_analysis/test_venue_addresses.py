"""Reject false address precision and geometrically ambiguous demo matches."""
import copy
import json
import unittest

import build_venue_homes as subject


class VenueAddressTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.evidence = json.loads(subject.EVIDENCE.read_text(encoding='utf8'))
        cls.buildings = subject.load_buildings()

    def test_catalog_has_twelve_unique_verified_buildings_and_source_attributes(self):
        catalog = subject.build_catalog(self.evidence, self.buildings)
        self.assertEqual(len(catalog['homes']), 64)
        self.assertEqual(len({h['plateauId'] for h in catalog['homes']}), 12)
        self.assertEqual(len({(h['latitude'], h['longitude']) for h in catalog['homes']}), 64)
        for home in catalog['homes']:
            self.assertEqual(home['addressPrecision'], 'verified-building-address')
            self.assertNotIn('付近', home['address'])
            self.assertTrue(home['addressEvidence']['sources'])
            self.assertEqual(home['attributes'], self.buildings[home['plateauId']]['attributes'])
            self.assertEqual(home['room'], '')
            self.assertEqual(home['floor'], '')
            point = subject.Point(home['longitude'], home['latitude'])
            self.assertTrue(subject.shape(self.buildings[home['plateauId']]['geometry']).contains(point))
            source = next(r for r in self.evidence['buildings'] if r['expectedPlateauId'] == home['plateauId'])
            self.assertTrue(subject.shape(source['osm']['geometry']).contains(point))

    def test_wrong_expected_id_rejected_even_with_nearby_valid_building(self):
        evidence = copy.deepcopy(self.evidence)
        evidence['buildings'][0]['expectedPlateauId'] = evidence['buildings'][1]['expectedPlateauId']
        with self.assertRaisesRegex(ValueError, 'expected PLATEAU'):
            subject.build_catalog(evidence, self.buildings)

    def test_missing_address_source_rejected(self):
        evidence = copy.deepcopy(self.evidence)
        evidence['buildings'][0]['addressSources'] = []
        with self.assertRaisesRegex(ValueError, 'address source'):
            subject.build_catalog(evidence, self.buildings)

    def test_nearby_town_label_rejected(self):
        evidence = copy.deepcopy(self.evidence)
        evidence['buildings'][0]['address'] = '京都府京都市中京区少将井町付近'
        with self.assertRaisesRegex(ValueError, 'parcel-number'):
            subject.build_catalog(evidence, self.buildings)

    def test_overlapping_duplicate_building_rejected(self):
        buildings = copy.deepcopy(self.buildings)
        original = buildings[self.evidence['buildings'][0]['expectedPlateauId']]
        buildings['bldg_ambiguous'] = dict(original, id='bldg_ambiguous')
        with self.assertRaisesRegex(ValueError, 'ambiguous'):
            subject.build_catalog(self.evidence, buildings)


if __name__ == '__main__':
    unittest.main()
