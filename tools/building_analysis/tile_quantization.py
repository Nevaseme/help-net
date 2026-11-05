"""Topology-preserving rounding at MapLibre Native's actual geometry precision."""
from shapely import make_valid, set_precision
from shapely.affinity import affine_transform

# MapLibre Native rescales MVT geometry to 8192. A finer encoded grid would be
# rounded again in the renderer, undoing validation performed on that finer grid.
EXTENT = 8192

def polygon_parts(geometry):
    if geometry.geom_type == 'Polygon':
        return [] if geometry.is_empty else [geometry]
    if hasattr(geometry, 'geoms'):
        return [p for child in geometry.geoms for p in polygon_parts(child)]
    return []

def quantized_polygons(geometry, bounds):
    west, north, east, south = bounds
    sx, sy = EXTENT/(east-west), EXTENT/(south-north)
    local = affine_transform(geometry, [sx, 0, 0, sy, -west*sx, -north*sy])
    result = []
    for part in polygon_parts(local):
        # Input clipping is normally valid; repair individually rather than union
        # neighboring buildings. Round with GEOS valid_output, not round(vertex).
        if not part.is_valid:
            part = make_valid(part)
        for valid in polygon_parts(part):
            snapped = set_precision(valid, 1.0, mode='valid_output')
            for polygon in polygon_parts(snapped):
                assert polygon.is_valid and polygon.area > 0
                result.append(polygon)
    return result
