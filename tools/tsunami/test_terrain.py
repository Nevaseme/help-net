"""Terrain import must preserve unknowns instead of manufacturing bathymetry."""
import tempfile
import unittest
from pathlib import Path
import zipfile

from tools.tsunami.terrain import load_xyz_tiles
import numpy as np


class TerrainTest(unittest.TestCase):
    def tile(self, directory, name, rows):
        path = Path(directory)/(name+".zip")
        with zipfile.ZipFile(path, "w") as archive:
            archive.writestr(name+"_DEM.txt", "\n".join(" ".join(map(str, row)) for row in rows))
        return {"path": path, "url": "https://example.test/"+path.name}

    def test_adjacent_tiles_keep_south_north_order_and_source_hashes(self):
        with tempfile.TemporaryDirectory() as directory:
            west = self.tile(directory, "west", [[0,0,2],[0,1,3]])
            east = self.tile(directory, "east", [[1,1,-2],[1,0,-3]])
            data = load_xyz_tiles([west, east], [0,0,1,1], 1)
            np.testing.assert_array_equal(data["bed_m"], [[2,-3],[3,-2]])
            self.assertEqual(len(data["source_sha256"]), 64)
            self.assertEqual(len(data["sources"]), 2)

    def test_hole_is_rejected_without_interpolation_or_flat_ocean_fill(self):
        with tempfile.TemporaryDirectory() as directory:
            tile = self.tile(directory, "missing", [[0,0,2],[0,1,3],[1,0,-3]])
            with self.assertRaisesRegex(ValueError, "missing"):
                load_xyz_tiles([tile], [0,0,1,1], 1)

    def test_conflicting_overlap_and_nonfinite_input_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            for rows in ([[0,0,2],[0,0,3]], [[0,0,float("nan")]]):
                tile = self.tile(directory, "invalid", rows)
                with self.assertRaises(ValueError): load_xyz_tiles([tile], [0,0,1,1], 1)

    def test_coarse_selection_uses_only_exact_source_samples(self):
        with tempfile.TemporaryDirectory() as directory:
            rows = [[x,y,x-y] for x in range(3) for y in range(3)]
            tile = self.tile(directory, "grid", rows)
            data = load_xyz_tiles([tile], [0,0,2,2], 2)
            np.testing.assert_array_equal(data["bed_m"], [[0,2],[-2,0]])
            with self.assertRaisesRegex(ValueError, "missing"):
                load_xyz_tiles([tile], [0.5,0.5,1.5,1.5], 1)


if __name__ == "__main__": unittest.main()
