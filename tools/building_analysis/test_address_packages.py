import gzip,json,unittest
from pathlib import Path

class AddressPackagesTest(unittest.TestCase):
    def read(self,code):
        return json.loads(gzip.decompress(Path(f'demo/data/cities/{code}/addresses.json.gz').read_bytes()))['rows']
    def test_203_uses_declared_block_source(self):
        r=next((r for r in self.read('26100') if r[1]=='京都府京都市左京区讃州寺町203'),None)
        self.assertIsNotNone(r)
        self.assertEqual(r[4],'block')
        self.assertEqual(r[5]['source'],'ISJ')
        self.assertEqual(r[5]['matchLevel'],'parcel')
        self.assertAlmostEqual(r[2],135.774524,6)
    def test_minato_is_distributed(self):
        p=Path('demo/data/cities/13103/addresses.json.gz')
        self.assertTrue(p.exists())
        self.assertTrue(any('六本木' in r[1] for r in self.read('13103')))
    def test_residential_number_is_not_discarded(self):
        rows=self.read('22205')
        self.assertTrue(any(r[1]=='静岡県熱海市中央町1-1' and r[4]=='residential' for r in rows))
if __name__=='__main__':unittest.main()
