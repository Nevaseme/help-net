import unittest
import math
from build_map_tiles import project, tile_geometry, tile_range
from shapely.geometry import Polygon, MultiPolygon, box
import mapbox_vector_tile

class MapTilesTest(unittest.TestCase):
    def test_projection_and_boundary(self):
        self.assertEqual(project(0,0),(0.5,0.5))
        self.assertEqual(list(tile_range((.49,.49,.51,.51),1)),[(0,0),(0,1),(1,0),(1,1)])
    def test_hole_and_boundary_survive_encoding(self):
        polygon=Polygon([(.48,.48),(.52,.48),(.52,.52),(.48,.52)],holes=[[ (.49,.49),(.49,.51),(.51,.51),(.51,.49)]])
        for x,y in tile_range(polygon.bounds,1):
            encoded=tile_geometry([('building-a',polygon,{'p':'w0'})],1,x,y)
            decoded=mapbox_vector_tile.decode(encoded)['buildings']['features']
            self.assertTrue(decoded)
            self.assertEqual(decoded[0]['properties']['bid'],'building-a')
            self.assertEqual(decoded[0]['properties']['p'],'w0')
        encoded=tile_geometry([('building-a',polygon,{'p':'w0'})],0,0,0)
        self.assertEqual(len(mapbox_vector_tile.decode(encoded)['buildings']['features'][0]['geometry']['coordinates']),2)
    def test_empty_tile_is_valid(self):
        self.assertEqual(mapbox_vector_tile.decode(tile_geometry([],14,1,1))['buildings']['features'],[])
if __name__=='__main__':unittest.main()
