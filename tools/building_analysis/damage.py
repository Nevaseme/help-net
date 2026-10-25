"""Independent conditional shaking damage estimates; not a safety diagnosis."""
import math


def classify(attrs):
    code, year, floors = (attrs.get(k) for k in ('structureCode','year','floors'))
    # Explicit user decision: retain a separately identified zero assumption.
    if floors is not None and floors >= 16:
        return 'highrise-zero', ['cabinet-highrise-zero-assumption']
    reasons=[]
    if code not in ('601','602','603','604'):
        reasons.append('unknown-structure' if code is None or code=='611' else 'unsupported-structure')
    if year is None:
        reasons.append('unknown-year')
    if code in ('602','603','604') and floors is None:
        reasons.append('unknown-floors')
    if reasons:
        return None,reasons
    if code=='601':
        return 'w'+str(sum(year>edge for edge in (1962,1971,1980,1989,2001))),[]
    if code=='604':
        return 's'+str(int(year>=1981))+('l' if floors<=4 else 'm' if floors<=6 else 'h'),[]
    return 'r'+str(sum(year>edge for edge in (1971,1980)))+('l' if floors<=6 else 'm' if floors<=10 else 'h'),[]


def rates(curve, intensity):
    if not math.isfinite(intensity) or not 5<=intensity<=7:
        raise ValueError('Instrumental intensity must be within the digitized 5.0–7.0 domain')
    position=(intensity-5)*10
    lo=min(19,math.floor(position))
    fraction=position-lo
    full=(curve['full'][lo]*(1-fraction)+curve['full'][lo+1]*fraction)/100
    cumulative=(curve['fullHalf'][lo]*(1-fraction)+curve['fullHalf'][lo+1]*fraction)/100
    return {'full':full,'halfOnly':cumulative-full,'fullHalf':cumulative}
