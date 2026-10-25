"""Numerical and CityGML boundary tests, using synthetic buildings only."""
import io
import unittest

from citygml import read_buildings


def gml(attributes='', coordinates='35 135 0 35 135.001 0 35.001 135.001 0 35 135 0', crs='6697'):
    return io.BytesIO(f'''<core:CityModel xmlns:core="http://www.opengis.net/citygml/2.0"
      xmlns:bldg="http://www.opengis.net/citygml/building/2.0"
      xmlns:gml="http://www.opengis.net/gml" xmlns:uro="https://www.geospatial.jp/iur/uro/3.2">
      <gml:boundedBy><gml:Envelope srsName="http://www.opengis.net/def/crs/EPSG/0/{crs}" srsDimension="3"/></gml:boundedBy>
      <core:cityObjectMember><bldg:Building gml:id="bldg_test">{attributes}
      <bldg:lod0RoofEdge><gml:MultiSurface><gml:surfaceMember><gml:Polygon>
      <gml:exterior><gml:LinearRing><gml:posList>{coordinates}</gml:posList></gml:LinearRing></gml:exterior>
      <gml:interior><gml:LinearRing><gml:posList>35.0001 135.0002 0 35.0001 135.0003 0 35.0002 135.0003 0 35.0001 135.0002 0</gml:posList></gml:LinearRing></gml:interior>
      </gml:Polygon></gml:surfaceMember></gml:MultiSurface></bldg:lod0RoofEdge>
      </bldg:Building></core:cityObjectMember></core:CityModel>'''.encode())


class CityGMLTests(unittest.TestCase):
    def test_axis_order_holes_and_raw_attributes_are_preserved(self):
        record, = read_buildings(gml('''<bldg:yearOfConstruction>1980</bldg:yearOfConstruction>
          <bldg:storeysAboveGround>2</bldg:storeysAboveGround><bldg:measuredHeight uom="m">6.5</bldg:measuredHeight>
          <uro:buildingDetailAttribute><uro:BuildingDetailAttribute><uro:buildingStructureType>601</uro:buildingStructureType>
          <uro:totalFloorArea uom="m2">110</uro:totalFloorArea></uro:BuildingDetailAttribute></uro:buildingDetailAttribute>'''))
        self.assertEqual(record['id'], 'bldg_test')
        self.assertEqual(record['geometry']['coordinates'][0][0][0], [135.0, 35.0])
        self.assertEqual(len(record['geometry']['coordinates'][0]), 2)
        self.assertEqual(record['attributes']['year'], 1980)
        self.assertEqual(record['attributes']['structureCode'], '601')
        self.assertEqual(record['attributes']['floors'], 2)
        self.assertEqual(record['attributes']['floorAreaM2'], 110.0)
        self.assertEqual(record['missing'], [])

    def test_unknown_sentinels_do_not_become_building_parameters(self):
        record, = read_buildings(gml('''<bldg:yearOfConstruction>0001</bldg:yearOfConstruction>
          <bldg:storeysAboveGround>0</bldg:storeysAboveGround><bldg:measuredHeight uom="m">NaN</bldg:measuredHeight>
          <uro:buildingDetailAttribute><uro:BuildingDetailAttribute><uro:buildingStructureType>611</uro:buildingStructureType>
          <uro:totalFloorArea uom="m2">-9999</uro:totalFloorArea></uro:BuildingDetailAttribute></uro:buildingDetailAttribute>'''))
        self.assertTrue(all(record['attributes'][key] is None for key in ['year','floors','heightM','structureCode','floorAreaM2']))
        self.assertEqual(record['raw']['year'], '0001')
        self.assertIn('structureCode', record['missing'])

    def test_unrecognized_crs_rejected_instead_of_swapping_projected_coordinates(self):
        with self.assertRaisesRegex(ValueError, 'CRS'):
            list(read_buildings(gml(crs='6674')))

    def test_broken_ring_is_unknown_instead_of_inventing_a_footprint(self):
        record, = read_buildings(gml(coordinates='35 135 0 35 135.001 0'))
        self.assertIsNone(record['geometry'])
        self.assertIn('geometry', record['missing'])


if __name__ == '__main__':
    unittest.main()
