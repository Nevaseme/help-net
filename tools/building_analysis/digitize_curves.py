"""Reproduce a 1 percentage-point table from Cabinet Office 2025 raster charts.

Requires the official sanko_gaiyo.pdf, not a screenshot resized by a browser.
Values are graphical approximations, NOT the Cabinet Office's original coefficients.
"""
import argparse
import hashlib
import io
import json
from pathlib import Path
import numpy as np
from PIL import Image
from pypdf import PdfReader

# Image ID, x(5), x(7), y(0%), y(100%), curve groups in paint order.
CHARTS = [
    (10,100,910,498,26,'full',['w0','w1','w2','w3','w4','w5']),
    (11,100,922,498,26,'fullHalf',['w0','w1','w2','w3','w4','w5']),
    (12,190,931,505,31,'full',['s0h','s0m','s0l']),
    (13,190,930,505,31,'full',['s1h','s1m','s1l']),
    (14,190,932,505,32,'fullHalf',['s0h','s0m','s0l']),
    (15,190,932,505,31,'fullHalf',['s1h','s1m','s1l']),
    (16,190,936,503,33,'full',['r0h','r0m','r0l']),
    (17,191,941,505,33,'fullHalf',['r0h','r0m','r0l']),
    (18,190,936,502,34,'full',['r1h','r1m','r1l']),
    (19,190,936,501,34,'fullHalf',['r1h','r1m','r1l']),
    (20,190,936,477,31,'fullHalf',['r2h','r2m','r2l']),
    (21,191,937,477,33,'full',['r2h','r2m','r2l']),
]
WOOD = [(192,0,0),(255,153,0),(204,102,0),(112,173,71),(112,48,160),(0,112,192)]
OTHER = [(192,80,78),(247,150,71),(155,187,88)]
# Visually read points whose marker centers are hidden by later-painted series.
# These are rounded readings of the chart, not fitted model coefficients.
OCCLUDED = {
    (10,'w0'): {0:0,1:0,2:0,3:0,4:0,5:0,6:1,7:2,8:5,9:9,10:17,18:98,19:99,20:100},
    (10,'w1'): {0:0,1:0,2:0,3:0,4:0,5:0,6:1,7:2},
    (10,'w2'): {0:0,1:0,2:0,3:0,4:0,5:0,6:0,7:1,8:2},
    (10,'w5'): {0:0,1:0,2:0,3:0,4:0,5:0,6:0,7:0,8:0,9:0,10:0,11:0,12:1,13:1,14:1,15:2,16:5},
    (11,'w0'): {0:0,1:1,2:1,3:3,4:5,5:10,6:17,7:26,8:38,9:50,10:62,11:73,12:83,13:90,14:94,15:97,16:99,17:100,18:100,19:100,20:100},
    (11,'w1'): {0:0,1:0,2:1,3:2},
    (11,'w2'): {0:0,1:0,2:0,3:1},
    (11,'w4'): {0:0,1:0,2:0,3:0,4:0,5:0,6:0,7:0,8:1,9:2},
    (11,'w5'): {0:0,1:0,2:0,3:0,4:0,5:0,6:0,7:0,8:0,9:0},
    (12,'s0h'): {0:0,1:0,2:0,3:0,4:0},
    (13,'s1h'): {0:0,1:0,2:0,3:0,4:0,5:0,6:1,7:1,8:2},
    (14,'s0h'): {0:4},
    (14,'s0m'): {0:3},
    (15,'s1h'): {0:2,1:3},
    (15,'s1m'): {0:2},
    (16,'r0h'): {0:0,1:0,2:0,3:0,4:0,5:1},
    (17,'r0h'): {0:1,1:1,2:2,3:2},
    (18,'r1h'): {0:0,1:0,2:0,3:0,4:0,5:0,6:1},
    (19,'r1h'): {0:1,1:1,2:2,3:2,4:3},
    (19,'r1m'): {0:1},
    (20,'r2h'): {0:1,1:1,2:2,3:2,4:3},
    (20,'r2m'): {0:1},
    (20,'r2l'): {4:1},
    (21,'r2h'): {0:0,1:0,2:0,3:0,4:0,5:0,6:1,7:1,8:2},
}
for chart in CHARTS:
    if chart[0]>=12 and chart[5]=='full':
        for group in chart[6]:
            OCCLUDED.setdefault((chart[0],group),{}).update({i:0 for i in range(5)})


def extract(image, config):
    _, x0, x1, y0, y1, metric, groups = config
    pixels = np.asarray(image.convert('RGB')).astype(int)
    palette = WOOD if len(groups)==6 else OTHER
    results, evidence = {}, []
    for group, color in zip(groups, palette):
        mask = np.max(np.abs(pixels - color),axis=2) < 24
        values = [None]*21
        previous = y1
        for i in range(20,-1,-1):
            if i in OCCLUDED.get((config[0],group),{}):
                values[i]=OCCLUDED[(config[0],group)][i]
                previous=y0-values[i]/100*(y0-y1)
                continue
            x = round(x0 + (x1-x0)*i/20)
            # Reverse traversal plus monotonicity excludes the top-left legend.
            low = max(0,round(previous-7))
            centers=[]
            for offset in ((0,) if i in (0,20) or len(groups)==6 else (-12,12)):
                xx=x+offset
                if not x0<=xx<=x1:
                    continue
                ys = np.where(mask[low:y0+8, xx-1:xx+2].any(axis=1))[0]+low
                if len(ys):
                    splits = np.split(ys,np.where(np.diff(ys)>2)[0]+1)
                    centers.append(float(np.median(splits[-1])))
            if centers:
                y = float(np.mean(centers))
                previous = y
                values[i] = max(0,min(100,round((y0-y)/(y0-y1)*100)))
        # Markers hide curves close to zero. Only infer zeros below the table's
        # 1pp resolution; any other missing point requires manual investigation.
        missing=[]
        for i, value in enumerate(values):
            if value is None:
                right = next((v for v in values[i+1:] if v is not None),None)
                if right is not None and right <= 1:
                    values[i]=0
                    missing.append(i)
                else:
                    raise ValueError(f'Unreadable {group} {metric} intensity {5+i/10}: {values}')
        if any(b<a for a,b in zip(values,values[1:])):
            raise ValueError(f'Nonmonotonic {group} {metric}: {values}')
        results[group]=values
        evidence.append({'group':group,'metric':metric,'occludedNearZeroIndices':missing,
                         'manualRoundedReadings':OCCLUDED.get((config[0],group),{})})
    return results,evidence


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('pdf',type=Path)
    parser.add_argument('output',type=Path)
    args=parser.parse_args()
    expected='5f5b5d26ba9e7f1c1f387de8110a5c722efddd7bd8c7c19ef93c56b2e9e30aca'
    if hashlib.sha256(args.pdf.read_bytes()).hexdigest()!=expected:
        raise ValueError('Source PDF changed; recalibrate and review before generating another model version')
    pdf=PdfReader(args.pdf)
    images={int(image.name.split('.')[0][2:]):image.data for page in pdf.pages[5:9] for image in page.images}
    curves={}
    evidence=[]
    for config in CHARTS:
        results,report=extract(Image.open(io.BytesIO(images[config[0]])),config)
        for group,values in results.items():
            curves.setdefault(group,{})[config[5]]=values
        evidence.extend(report)
    for group,pair in curves.items():
        if any(f>h for f,h in zip(pair['full'],pair['fullHalf'])):
            raise ValueError(f'Inconsistent cumulative rate: {group}')
    payload={'schemaVersion':1,'modelId':'pland-cabinet-2025-digitized-v1',
             'sourceUrl':'https://www.bousai.go.jp/jishin/nankai/taisaku_wg_02/pdf/sanko_gaiyo.pdf',
             'sourceSha256':hashlib.sha256(args.pdf.read_bytes()).hexdigest(),
             'sourcePrintedPages':[5,6,7,8], 'intensities':[round(5+i/10,1) for i in range(21)],
             'unit':'percent','roundingPercentagePoints':1,'originalCoefficients':False,
             'method':'Raster marker/line color tracing with explicit visual readings for occluded points; nearest 1 percentage point. Non-wood full-damage curves at 5.0–5.4 read as 0 at this resolution. Other occluded values below 1pp recorded as 0. Linear interpolation between intensity samples; no extrapolation.',
             'calibration':CHARTS,'digitizationEvidence':evidence,'curves':curves}
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(payload,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    for group,pair in curves.items():
        print(group,pair)


if __name__=='__main__':
    main()
