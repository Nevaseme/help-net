"""Pair two measured browser domain packs without approving them for publication."""
import argparse
import json
from pathlib import Path
from tools.tsunami.build_browser_domain import pair_domains


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('primary',type=Path)
    parser.add_argument('secondary',type=Path)
    parser.add_argument('output',type=Path)
    parser.add_argument('--reporting-bounds',type=float,nargs=4,required=True,
                        metavar=('WEST','SOUTH','EAST','NORTH'))
    args=parser.parse_args()
    read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'))
    pack=pair_domains(read(args.primary),read(args.secondary),args.reporting_bounds)
    with args.output.open('x',encoding='utf-8') as f:
        json.dump(pack,f,ensure_ascii=False,allow_nan=False,separators=(',',':'))
    print(json.dumps({'id':pack['id'],'review':pack['review'],
                      'reporting_window':pack['reporting_window']}))


if __name__=='__main__':main()
