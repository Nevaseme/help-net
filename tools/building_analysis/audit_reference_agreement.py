"""Read-only audit: raster agreement and independent GML attribute/class joins.

This measures implementation/source agreement, NOT predictive accuracy.
Raster audit shares the published model's axis calibration and colors; it uses
an alternative vertical-slice extractor with no OCCLUDED readings, no production
classifier/parser, and no expected table value to choose a component.
"""
import argparse
from collections import Counter
import csv
import gzip
import hashlib
import io
import json
import math
from pathlib import Path

from lxml import etree
import numpy as np
from PIL import Image
from pypdf import PdfReader


def sha(path):
    with path.open('rb') as f:
        return hashlib.file_digest(f, 'sha256').hexdigest()


def raster_audit(pdf_path, model, out):
    assert sha(pdf_path) == model['sourceSha256']
    pdf = PdfReader(pdf_path)
    images = {int(im.name.split('.')[0][2:]): im.data
              for page in pdf.pages[5:9] for im in page.images}
    wood = [(192,0,0),(255,153,0),(204,102,0),(112,173,71),(112,48,160),(0,112,192)]
    nonwood = [(192,80,78),(247,150,71),(155,187,88)]
    records = []
    for image_id, x0, x1, y0, y1, metric, groups in model['calibration']:
        pixels = np.array(Image.open(io.BytesIO(images[image_id])).convert('RGB')).astype(int)
        for group, color in zip(groups, wood if len(groups) == 6 else nonwood):
            mask = np.max(np.abs(pixels - color), axis=2) < 24
            # Only 6.0-7.0: excludes the colored legend in the upper-left.
            # Includes midpoints to check interpolation as well as stored nodes.
            for j in range(20,41):
                intensity = 5+j/20
                x = round(x0+(x1-x0)*j/40)
                ys = np.flatnonzero(mask[max(0,y1-8):y0+9, x-1:x+2].any(axis=1))+max(0,y1-8)
                parts = np.split(ys, np.flatnonzero(np.diff(ys)>2)+1) if len(ys) else []
                row = dict(image=image_id, profile=group, metric=metric, intensity=intensity,
                           point_kind='node' if j%2==0 else 'midpoint')
                if len(parts)!=1 or len(parts[0])<2 or parts[0][-1]-parts[0][0]>24:
                    row['status']='unreadable_or_ambiguous'
                else:
                    actual = min(100,max(0,(y0-float(np.median(parts[0])))/(y0-y1)*100))
                    values=model['curves'][group][metric]
                    expected=values[j//2] if j%2==0 else (values[j//2]+values[j//2+1])/2
                    row.update(status='compared', table_percent=expected, raster_percent=actual,
                               absolute_difference_pp=abs(actual-expected))
                records.append(row)
    with (out/'raster-points.csv').open('w',newline='',encoding='utf-8') as f:
        w=csv.DictWriter(f,fieldnames=['image','profile','metric','intensity','point_kind','status',
                                    'table_percent','raster_percent','absolute_difference_pp'])
        w.writeheader();w.writerows(records)
    def summarize(kind):
        subset=[r for r in records if r['point_kind']==kind]
        good=[r for r in subset if r['status']=='compared']
        errors=np.array([r['absolute_difference_pp'] for r in good])
        return dict(attempted=len(subset), compared=len(good), excluded=len(subset)-len(good),
                    mean_absolute_difference_pp=float(errors.mean()),
                    median_absolute_difference_pp=float(np.median(errors)),
                    p95_absolute_difference_pp=float(np.quantile(errors,.95)),
                    max_absolute_difference_pp=float(errors.max()),
                    within_1pp=int((errors<=1).sum()),within_2pp=int((errors<=2).sum()))
    return dict(nodes=summarize('node'),midpoints=summarize('midpoint'),
                scope='42 curves; intensity 6.0-7.0 only; excluded points are not passes',
                limitations='Same raster/axes/colors as production; not an independent truth set or original coefficients; marker overlap and raster thickness remain.',
                worst=sorted((r for r in records if r['status']=='compared'),
                             key=lambda r:r['absolute_difference_pp'],reverse=True)[:12])


def integer(text, lo, hi):
    try:
        value=float(text)
        return int(value) if math.isfinite(value) and value.is_integer() and lo<=value<=hi else None
    except (TypeError,ValueError):
        return None


def expected_profile(raw):
    # Independently transcribed ranges from Cabinet Office printed pp.5-8.
    year=integer(raw['year'],2,2025);floors=integer(raw['floors'],1,300)
    structure=raw['structureCode']
    if floors is not None and floors>=16:
        return 'highrise-zero','assumed'
    if year is None or structure not in ['601','602','603','604']:
        return None,'unknown'
    if structure=='601':
        ranges=[(2,1962,'w0'),(1963,1971,'w1'),(1972,1980,'w2'),
                (1981,1989,'w3'),(1990,2001,'w4'),(2002,2025,'w5')]
        return next(code for start,end,code in ranges if start<=year<=end),'estimated'
    if floors is None:
        return None,'unknown'
    if structure=='604':
        age='0' if year<=1980 else '1'
        height=next(code for start,end,code in [(1,4,'l'),(5,6,'m'),(7,15,'h')] if start<=floors<=end)
        return 's'+age+height,'estimated'
    age=next(code for start,end,code in [(2,1971,'0'),(1972,1980,'1'),(1981,2025,'2')] if start<=year<=end)
    height=next(code for start,end,code in [(1,6,'l'),(7,10,'m'),(11,15,'h')] if start<=floors<=end)
    return 'r'+age+height,'estimated'


def source_audit(root, cache):
    index=json.loads((root/'index.json').read_text(encoding='utf-8'))
    b='{http://www.opengis.net/citygml/building/2.0}';g='{http://www.opengis.net/gml}'
    counts=Counter();details=[];profile_counts=Counter();seen=set()
    def issue(kind,mesh,identifier):
        counts[kind]+=1
        if len(details)<30:details.append(dict(kind=kind,mesh=mesh,id=identifier))
    for n,chunk in enumerate(index['chunks']):
        mesh=chunk['mesh'];src=cache/(mesh+'.gml')
        if sha(src)!=chunk['source']['sha256']:issue('source_hash_mismatch',mesh,'')
        asset=root/chunk['file']
        if sha(asset)!=chunk['sha256']:issue('asset_hash_mismatch',mesh,'')
        rows=json.loads(gzip.decompress(asset.read_bytes()))['buildings']
        lookup={r['id']:r for r in rows};matched=set()
        if len(rows)!=len(lookup):issue('duplicate_asset_id',mesh,'')
        for _,element in etree.iterparse(str(src),events=('end',),tag=b+'Building',
                                         resolve_entities=False,load_dtd=False,no_network=True):
            identifier=element.get(g+'id');counts['source_buildings']+=1
            if identifier in seen:issue('duplicate_source_id',mesh,identifier)
            seen.add(identifier)
            raw={k:element.findtext(b+tag) for k,tag in [('year','yearOfConstruction'),('floors','storeysAboveGround')]}
            # Select structure through direct BuildingDetailAttribute children;
            # do not import the production CityGML parser.
            structures=[]
            for child in element:
                if etree.QName(child).localname=='buildingDetailAttribute':
                    for attr in child:
                        structures.extend(x.text for x in attr if etree.QName(x).localname=='buildingStructureType')
            raw['structureCode']=structures[0] if len(structures)==1 else None
            row=lookup.get(identifier)
            if row is None:issue('missing_asset_building',mesh,identifier)
            else:
                matched.add(identifier)
                if any(raw[k]!=row['raw'].get(k) for k in raw):issue('raw_attribute_mismatch',mesh,identifier)
                expected=expected_profile(raw)
                if expected!=(row['profile'],row['status']):issue('profile_or_status_mismatch',mesh,identifier)
                y=integer(raw['year'],2,2025);f=integer(raw['floors'],1,300)
                s=raw['structureCode'] if raw['structureCode'] in [str(v) for v in range(601,611)] else None
                if (y,f,s)!=(row['attributes']['year'],row['attributes']['floors'],row['attributes']['structureCode']):
                    issue('normalized_attribute_mismatch',mesh,identifier)
                counts['matched_buildings']+=1;profile_counts[row['profile'] or 'unknown']+=1
            parent=element.getparent();element.clear()
            if parent is not None:
                while parent.getprevious() is not None:del parent.getparent()[0]
        for identifier in lookup.keys()-matched:issue('extra_asset_building',mesh,identifier)
        counts['meshes']+=1
        if (n+1)%40==0:print(f'Checked {n+1}/{len(index["chunks"])} meshes',flush=True)
    return dict(counts=dict(counts),issues=details,profiles=dict(profile_counts),
                limitations='Attribute/class join only; not a second geometry audit, source-year ground truth check, or observed-damage validation.')


def main():
    p=argparse.ArgumentParser();p.add_argument('--assets',type=Path,required=True)
    p.add_argument('--pdf',type=Path,required=True);p.add_argument('--cache',type=Path)
    p.add_argument('--out',type=Path,required=True);args=p.parse_args();args.out.mkdir(parents=True,exist_ok=True)
    model=json.loads((args.assets/'model.json').read_text(encoding='utf-8'))
    reference=json.loads(Path(__file__).with_name('curves-2025.json').read_text(encoding='utf-8'))
    equal=sum(model['curves'][k][metric][i]==reference['curves'][k][metric][i]
              for k in reference['curves'] for metric in ['full','fullHalf'] for i in range(21))
    manual=sum(len(e['manualRoundedReadings']) for e in model['digitizationEvidence'])
    report=dict(modelId=model['modelId'],copied_curve_nodes=dict(equal=equal,total=882),
                manual_or_policy_nodes=manual,raster=raster_audit(args.pdf,model,args.out),
                predictive_accuracy='Not measured: no matched observed-damage labels.')
    (args.out/'agreement.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    if args.cache:
        report['source']=source_audit(args.assets,args.cache)
        (args.out/'agreement.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(report,ensure_ascii=False,indent=2))


if __name__=='__main__':main()
