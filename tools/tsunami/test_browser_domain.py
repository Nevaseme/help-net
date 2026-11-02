import unittest
import copy
from tools.tsunami.build_browser_domain import build_domain, pair_domains
from tools.tsunami.test_coastal import experiment


class BrowserDomainTest(unittest.TestCase):
    def site(self):
        s,c=experiment()
        s['kind']='site-scenario';s['grid']['crs']='EPSG:6676'
        s['terrain'].update(kind='measured',source_sha256='a'*64,
            vertical_datum_status='verified',sources=[{'url':'https://example.test/synthetic-test-fixture','sha256':'a'*64}])
        s['source_reference']='Synthetic test only, not real site data'
        return s,c

    def test_conversion_preserves_cell_order_and_geographic_reference(self):
        s,c=self.site();p=build_domain(s,c)
        self.assertEqual(p['config']['nx'],20)
        self.assertEqual(len(p['config']['bed']),40)
        self.assertAlmostEqual(p['config']['bed'][0],.95)
        self.assertAlmostEqual(p['config']['bed'][19],-.95)
        self.assertEqual(len(p['nodes_lonlat']),63)
        self.assertEqual(p['review']['status'],'pending')
        self.assertEqual(p['config']['reference_index'],37)
        self.assertLess(p['config']['bed'][37],0)
        self.assertEqual(p['conversion']['reference_point_realised_m'],[17.5,1.5])
        self.assertNotIn('dry_edges',p['config']['boundaries']['right'])

    def test_unverified_or_dry_reference_is_not_exported(self):
        s,c=self.site();s['terrain']['vertical_datum_status']='unknown'
        with self.assertRaisesRegex(ValueError,'verified'):build_domain(s,c)
        s,c=self.site();c['reference_point_m']=[0,0]
        with self.assertRaisesRegex(ValueError,'sea water'):build_domain(s,c)

    def test_identity_changes_when_physical_conditions_change(self):
        s,c=self.site();first=build_domain(s,c)
        s['manning_n']+=.001
        self.assertNotEqual(first['id'],build_domain(s,c)['id'])

    def test_pair_maps_by_coordinates_and_rejects_mismatched_terrain(self):
        s,c=self.site();p=build_domain(s,c)
        with self.assertRaisesRegex(ValueError,'different domain extents'):pair_domains(p,p,[5,0,20,2])
        s['grid']['ny']=4;s['grid']['height_m']=4
        s['terrain']['bed_m']+=[s['terrain']['bed_m'][-1][:] for _ in range(2)]
        q=build_domain(s,c)
        paired=pair_domains(p,q,[5,0,20,2])
        self.assertEqual(paired['reporting_window'],[5,0,20,2])
        self.assertEqual(paired['comparison']['primary_to_secondary'],list(range(40)))
        self.assertEqual(paired['review']['status'],'pending')
        self.assertFalse(paired['study']['global_inundation_extent_complete'])
        q['config']['bed'][10]+=.01
        with self.assertRaisesRegex(ValueError,'identical measured terrain'):
            pair_domains(p,q,[5,0,20,2])
        q=build_domain(s,c);q['conversion']['origin_m'][0]+=.5
        with self.assertRaisesRegex(ValueError,'alignment'):
            pair_domains(p,q,[5,0,20,2])


if __name__=='__main__':unittest.main()
