"""Safety properties for geometry-only cadastral candidates."""
import unittest
from shapely.geometry import box, Polygon
from shapely.strtree import STRtree
from build_kyoto_parcel_matches import match_building, parcel_info


class ParcelMatchingTests(unittest.TestCase):
    def match(self, building, parcels, numbers=None):
        numbers = numbers or ['10'] * len(parcels)
        metadata = [parcel_info({'都道府県名': '京都府', '市区町村名': '京都市左京区',
                    '大字名': '讃州寺町', '小字名': '', '行政区域C': '26103', '地番': n}, i)
                    for i, n in enumerate(numbers)]
        return match_building(building, STRtree(parcels), parcels, metadata)

    def test_containment_is_candidate_not_postal_verification(self):
        result = self.match(box(1, 1, 9, 9), [box(0, 0, 10, 10)], ['203'])
        self.assertEqual(result['status'], 'single-parcel')
        self.assertEqual(result['candidates'][0]['address'], '京都府京都市左京区讃州寺町203')
        self.assertNotIn('verifiedAddress', result)

    def test_nearby_and_touching_parcels_never_assigned(self):
        result = self.match(box(0, 0, 10, 10), [box(10, 0, 20, 10), box(12, 0, 22, 10)])
        self.assertEqual(result['status'], 'unmatched')
        self.assertEqual(result['candidates'], [])

    def test_crossing_keeps_both_parcels(self):
        result = self.match(box(0, 0, 10, 10), [box(0, 0, 6, 10), box(6, 0, 10, 10)])
        self.assertEqual(result['status'], 'multiple-parcels')
        self.assertEqual([c['buildingCoverage'] for c in result['candidates']], [0.6, 0.4])
        self.assertEqual(result['candidates'][0]['address'], result['candidates'][1]['address'])
        self.assertNotEqual(result['candidates'][0]['parcelId'], result['candidates'][1]['parcelId'])

    def test_partial_coverage_not_promoted(self):
        self.assertEqual(self.match(box(0, 0, 10, 10), [box(0, 0, 7, 10)])['status'], 'partial')

    def test_overlapping_source_parcels_flagged(self):
        result = self.match(box(0, 0, 10, 10), [box(0, 0, 10, 10), box(0, 0, 10, 10)])
        self.assertEqual(result['status'], 'multiple-parcels')
        self.assertEqual(result['unionBuildingCoverage'], 1)
        self.assertIn('overlapping-source-parcels', result['flags'])

    def test_parcel_hole_prevents_false_assignment(self):
        parcel = Polygon([(0, 0), (20, 0), (20, 20), (0, 20)],
                         [[(5, 5), (15, 5), (15, 15), (5, 15)]])
        self.assertEqual(self.match(box(6, 6, 14, 14), [parcel])['status'], 'unmatched')

    def test_road_without_number_not_confident(self):
        result = self.match(box(1, 1, 9, 9), [box(0, 0, 10, 10)], ['道路'])
        self.assertEqual(result['status'], 'partial')
        self.assertIn('unnumbered-parcel', result['flags'])

    def test_invalid_geometry_is_repaired_and_flagged(self):
        crossed = Polygon([(0, 0), (10, 10), (0, 10), (10, 0), (0, 0)])
        result = self.match(crossed, [box(-1, -1, 11, 11)])
        self.assertEqual(result['status'], 'partial')
        self.assertIn('building-geometry-repaired', result['flags'])


if __name__ == '__main__':
    unittest.main()
