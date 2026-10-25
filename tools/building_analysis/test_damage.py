import json
import math
from pathlib import Path
import unittest
from damage import classify, rates


class DamageTests(unittest.TestCase):
    def test_wood_age_boundaries(self):
        for year, expected in [(1962,'w0'),(1963,'w1'),(1971,'w1'),(1972,'w2'),(1980,'w2'),(1981,'w3'),(1989,'w3'),(1990,'w4'),(2001,'w4'),(2002,'w5')]:
            self.assertEqual(classify({'structureCode':'601','year':year,'floors':None}), (expected,[]))

    def test_nonwood_floor_and_age_boundaries(self):
        for code,year,floor,expected in [('604',1980,4,'s0l'),('604',1981,5,'s1m'),('604',1981,7,'s1h'),('603',1971,6,'r0l'),('602',1972,7,'r1m'),('603',1981,11,'r2h')]:
            self.assertEqual(classify({'structureCode':code,'year':year,'floors':floor}), (expected,[]))

    def test_unknown_is_not_zero(self):
        for attrs in [{'structureCode':'605','year':2000,'floors':2},{'structureCode':'611','year':2000,'floors':2},{'structureCode':'603','year':2000,'floors':None},{'structureCode':'601','year':None,'floors':2}]:
            profile,reasons=classify(attrs)
            self.assertIsNone(profile)
            self.assertTrue(reasons)

    def test_highrise_user_approved_assumption_not_curve(self):
        self.assertEqual(classify({'structureCode':'603','year':None,'floors':16}),('highrise-zero',['cabinet-highrise-zero-assumption']))

    def test_rates_interpolation_cumulative_and_support(self):
        curve={'full':[10,30]+[30]*19,'fullHalf':[40,50]+[50]*19}
        result=rates(curve,5.05)
        self.assertAlmostEqual(result['full'],.2)
        self.assertAlmostEqual(result['halfOnly'],.25)
        self.assertAlmostEqual(result['fullHalf'],.45)
        for intensity in [4.9,7.1,float('nan'),float('inf')]:
            with self.assertRaises(ValueError): rates(curve,intensity)

    def test_digitized_curves_physical_constraints_and_independent_visual_anchors(self):
        table=json.loads(Path(__file__).with_name('curves-2025.json').read_text())['curves']
        self.assertEqual(len(table),21)
        for pair in table.values():
            for metric in ['full','fullHalf']:
                self.assertEqual(len(pair[metric]),21)
                self.assertTrue(all(0<=x<=100 for x in pair[metric]))
                self.assertEqual(sorted(pair[metric]),pair[metric])
            self.assertTrue(all(f<=h for f,h in zip(pair['full'],pair['fullHalf'])))
        # Read independently from the axes and marker centers in printed pp.5–8.
        for group,metric,index,percent in [('w0','full',15,82),('w5','full',20,21),('w3','fullHalf',20,83),('s0l','full',20,72),('s1h','full',20,59),('r0h','full',15,42),('r2l','fullHalf',20,58)]:
            self.assertLessEqual(abs(table[group][metric][index]-percent),2)
