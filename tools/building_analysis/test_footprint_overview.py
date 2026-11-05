import unittest
from shapely.geometry import Polygon,box
import mapbox_vector_tile
from footprint_overview import simplify_footprint,encode_footprints,EXTENT

class FootprintOverviewTest(unittest.TestCase):
    def test_quantized_thin_edges_cannot_turn_other_buildings_into_holes(self):
        # Valid source footprint from tile 11/1796/811. Its narrow edges used to
        # self-intersect after integer rounding and reverse the first ring's sign.
        from shapely import from_wkt
        from shapely.geometry import shape
        g=from_wkt('MULTIPOLYGON (((0.8769984759114224 0.3960427731260092, 0.8769984463786288 0.396042770151844, 0.8769984508202265 0.396042725805271, 0.8769981704129962 0.3960426972477178, 0.8769981558994484 0.3960428416005611, 0.8769984414736743 0.3960428664775367, 0.8769984661378146 0.3960428688539381, 0.8769984759114224 0.3960427731260092)))')
        n=2048;x=1796;y=811
        other=box((x+.2)/n,(y+.2)/n,(x+.21)/n,(y+.21)/n)
        self.assertTrue(g.is_valid)
        data,_=encode_footprints([(g,'w0'),(other,'w0')],11,x,y)
        features=mapbox_vector_tile.decode(data)['footprints']['features']
        self.assertTrue(all(shape(f['geometry']).is_valid for f in features))
        self.assertTrue(all(len(p.interiors)==0 for f in features for p in
            ([shape(f['geometry'])] if f['geometry']['type']=='Polygon' else shape(f['geometry']).geoms)))
    def test_simplification_stays_inside_original_and_preserves_courtyard(self):
        g=Polygon([(.1,.1),(.4,.1),(.4,.4),(.1,.4)],holes=[[ (.2,.2),(.2,.3),(.3,.3),(.3,.2)]])
        shapes=simplify_footprint(g,8)
        self.assertTrue(shapes)
        self.assertTrue(all(p.difference(g).area<1e-15 for p in shapes))
        self.assertEqual(sum(p.intersection(box(.22,.22,.28,.28)).area for p in shapes),0)
    def test_separate_buildings_do_not_fill_gap_and_keep_profiles(self):
        n=2**10
        a=box(.1/n,.1/n,.3/n,.3/n);b=box(.7/n,.1/n,.9/n,.3/n)
        data,count=encode_footprints([(a,'old'),(b,'new')],10,0,0)
        features=mapbox_vector_tile.decode(data)['footprints']['features']
        self.assertEqual(count,2)
        self.assertEqual({f['properties']['p'] for f in features},{'old','new'})
        from shapely.geometry import shape
        self.assertTrue(all(shape(f['geometry']).intersection(box(.4*EXTENT,.1*EXTENT,.6*EXTENT,.3*EXTENT)).area==0 for f in features))
        self.assertTrue(all('n' not in f['properties'] and 'f12' not in f['properties'] for f in features))
    def test_subpixel_is_omitted_but_returns_when_zoomed_in(self):
        g=box(.5,.5,.5000001,.5000001)
        self.assertFalse(simplify_footprint(g,8))
        self.assertTrue(simplify_footprint(g,14))

if __name__=='__main__':unittest.main()
