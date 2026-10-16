#!/usr/bin/env python3
"""Original Native module icon artwork; no imported icon paths or third-party glyphs.

Rebuild: python3 scripts/native-module-icons.py
Audit:   python3 scripts/native-module-icons.py --check
Preview: python3 scripts/native-module-icons.py --preview /data/native-icons.html
Export:  python3 scripts/native-module-icons.py --export-dir /data/native-module-assets
Requires fonttools. The checked-in base64 font is decoded by the normal Gradle asset task.
Codepoints are append-only; existing module configuration/IDs never change.
"""
import argparse, base64, hashlib, io, json, math
from pathlib import Path
from fontTools.fontBuilder import FontBuilder
from fontTools.pens.ttGlyphPen import TTGlyphPen

ROOT = Path(__file__).resolve().parents[1]
STROKE = 1.85
ICONS = []

def icon(id, label, *lines):
    ICONS.append((id, label, lines))

def poly(*points): return list(points)
def box(x,y,w,h,c=1.5):
    return poly((x+c,y),(x+w-c,y),(x+w,y+c),(x+w,y+h-c),(x+w-c,y+h),(x+c,y+h),(x,y+h-c),(x,y+c),(x+c,y))
def ring(cx,cy,r,n=8):
    a=[(cx+math.cos(-math.pi/2+i*2*math.pi/n)*r,cy+math.sin(-math.pi/2+i*2*math.pi/n)*r) for i in range(n)]
    return a+[a[0]]
def dot(x,y): return [(x,y),(x+.01,y)]

# A shared cut-corner silhouette language, optical 24-unit grid and round terminals.
# Every module has a distinct original construction, including previously shared icons.
icon('fps','FPS',poly((3,15),(3,9),(6,5),(18,5),(21,9),(21,15)),poly((6,19),(18,19)),poly((12,15),(17,10)),dot(12,15),poly((7,9),(8,10)),poly((12,7),(12,9)))
icon('cps','CPS',poly((5,3),(5,17),(9,13),(13,20),(16,18),(12,12),(18,12),(5,3)),poly((17,3),(17,6)),poly((21,7),(19,8)))
icon('keystrokes','Keystrokes',box(8.5,2.5,7,7,1),box(2,13,6,7,1),box(9,13,6,7,1),box(16,13,6,7,1),poly((11,7),(12,5.5),(13,7)))
icon('coords','Coordinates',poly((5,17),(5,5)),poly((5,17),(19,17)),poly((5,17),(13,9)),poly((3,7),(5,5),(7,7)),poly((17,15),(19,17),(17,19)),box(11.5,7,4,4,.8))
icon('direction','Direction',ring(12,12,9),poly((12,5),(16,15),(12,13),(8,15),(12,5)),poly((12,13),(12,18)))
icon('clock','Clock',ring(12,12,9),poly((12,7),(12,12),(16,14)),dot(12,12))
icon('memory','Memory',box(3,5,18,12,1.8),poly((6,9),(9,9),(9,13),(6,13),(6,9)),poly((13,9),(18,9)),poly((13,13),(18,13)),poly((6,20),(6,17)),poly((10,20),(10,17)),poly((14,20),(14,17)),poly((18,20),(18,17)))
icon('ping','Ping',dot(5,12),poly((9,8),(12,12),(9,16)),poly((13,4),(20,12),(13,20)),poly((3,20),(7,20)))
icon('server','Server IP',box(3,3,18,8,1.5),box(3,13,18,8,1.5),dot(6,7),dot(6,17),poly((12,7),(18,7)),poly((12,17),(18,17)))
icon('speed','Speed',poly((2,7),(9,7)),poly((2,12),(6,12)),poly((2,17),(9,17)),poly((9,5),(21,12),(9,19),(12,12),(9,5)))
icon('scoreboard','Scoreboard',box(3,3,18,18,2),poly((7,7),(8,7)),poly((12,7),(17,7)),poly((7,12),(8,12)),poly((12,12),(17,12)),poly((7,17),(8,17)),poly((12,17),(15,17)))
icon('bossbar','Boss Bar',poly((5,3),(8,7),(12,3),(16,7),(19,3),(18,10),(6,10),(5,3)),box(2,14,20,6,1),poly((5,17),(13,17)))
icon('togglesprint','Toggle Sprint',poly((10,3),(9,9),(6,14),(6,20),(18,20),(20,18),(17,15),(13,15),(14,10),(10,3)),poly((2,5),(6,5)),poly((2,9),(5,9)),poly((7,17),(15,17)))
icon('zoom','Zoom',poly((3,17),(7,21),(20,8),(16,4),(3,17)),poly((7,13),(11,17)),poly((14,6),(18,10)),poly((4,12),(4,7),(9,7)))
icon('fullbright','Fullbright',box(7,7,10,10,2),poly((12,2),(12,4)),poly((12,20),(12,22)),poly((2,12),(4,12)),poly((20,12),(22,12)),poly((4,4),(5.5,5.5)),poly((18.5,18.5),(20,20)),poly((4,20),(5.5,18.5)),poly((18.5,5.5),(20,4)))
icon('bgfps','Background FPS',poly((3,14),(3,4),(5,2),(17,2),(19,4)),box(7,7,15,14,1.5),poly((7,11),(22,11)),poly((12,14),(12,18)),poly((17,14),(17,18)))
icon('effects','Potion Effects',poly((9,3),(15,3)),poly((10,3),(10,9),(4,18),(6,21),(18,21),(20,18),(14,9),(14,3)),poly((7,15),(17,15)),dot(10,18),poly((18,5),(20,7),(22,5),(20,3),(18,5)))
icon('armor','Armor Status',poly((7,3),(10,6),(14,6),(17,3),(22,7),(19,12),(17,10),(17,21),(7,21),(7,10),(5,12),(2,7),(7,3)),poly((10,10),(10,17),(14,17),(14,10)))
icon('inventory','Inventory',poly((6,5),(8,2),(16,2),(18,5)),box(4,5,16,17,2),poly((4,11),(20,11)),box(8,14,8,5,1),poly((10,8),(14,8)))
icon('crosshair','Crosshair',poly((3,8),(3,3),(8,3)),poly((16,3),(21,3),(21,8)),poly((21,16),(21,21),(16,21)),poly((8,21),(3,21),(3,16)),poly((12,9),(15,12),(12,15),(9,12),(12,9)))
icon('hitbox-colours','Hitbox Colours',poly((5,7),(15,3),(21,7),(21,18),(11,22),(5,18),(5,7),(11,11),(21,7)),poly((11,11),(11,22)),poly((5,12),(11,16),(21,12)),poly((2,5),(2,11)))
icon('critical-colours','Critical Colours',poly((12,2),(15,9),(20,12),(15,15),(12,22),(9,15),(4,12),(9,9),(12,2)),poly((3,3),(5,5)),poly((19,3),(21,3)),poly((19,19),(21,21)))
icon('watermark','Native Watermark',poly((4,20),(4,4),(7,4),(17,20),(20,20),(20,4)),poly((4,4),(8,4)),poly((16,20),(20,20)))
icon('voice','Voice Chat',box(9,2,6,12,3),poly((5,10),(5,11),(6,14),(9,16.5),(12,17),(15,16.5),(18,14),(19,11),(19,10)),poly((12,17),(12,21)),poly((8,21),(16,21)))
icon('voice-overlay','Voice Overlay',poly((4,14),(4,11),(6,6),(12,3),(18,6),(20,11),(20,14)),box(3,13,4,7,1),box(17,13,4,7,1),poly((10,12),(10,16)),poly((12,10),(12,18)),poly((14,12),(14,16)))

def arc(cx,cy,r,a0,a1,n=14):
    return [(cx+math.cos(math.radians(a0+(a1-a0)*i/n))*r,cy+math.sin(math.radians(a0+(a1-a0)*i/n))*r) for i in range(n+1)]
def crescent():
    # a disc with a bite out of its upper right: the outer arc, then the bite's arc back inside the disc
    ax,ay,ar,bx,by,br=12,12.5,8.8,17.4,7.4,7.2
    gap=math.degrees(math.atan2(by-ay,bx-ax))
    outer=[a for a in range(8,353,8) if math.hypot(ax+math.cos(math.radians(gap+a))*ar-bx,ay+math.sin(math.radians(gap+a))*ar-by)>br]
    first,last=outer[0],outer[-1]
    big=[(ax+math.cos(math.radians(gap+a))*ar,ay+math.sin(math.radians(gap+a))*ar) for a in range(first,last+1,8)]
    inner=[]
    for k in range(0,361,8):
        x,y=bx+math.cos(math.radians(k))*br,by+math.sin(math.radians(k))*br
        if math.hypot(x-ax,y-ay)<ar-0.2:inner.append((k,x,y))
    # order the bite's points so the path runs from the outer arc's end back to its start
    ends=(big[-1],big[0])
    inner.sort(key=lambda t:math.hypot(t[1]-ends[0][0],t[2]-ends[0][1]))
    pts=[(x,y) for _,x,y in sorted(inner,key=lambda t:(t[0]-math.degrees(math.atan2(ends[0][1]-by,ends[0][0]-bx)))%360)]
    return big+pts+[big[0]]
icon('nether-coords','Nether Coordinates',box(4,2,16,20,2),poly((12,7),(16,12),(12,17),(8,12),(12,7)),dot(12,12))
icon('moon-phase','Moon Phase',crescent(),dot(19,15),poly((20,4),(20,7)),poly((18.5,5.5),(21.5,5.5)))
icon('day-night','Day / Night Cycle',arc(12,12,8.5,90,270,10)+[(12,3.5)],poly((12,3.5),(12,20.5)),poly((16,6),(17.5,4.5)),poly((18.5,12),(21,12)),poly((16,18),(17.5,19.5)))
icon('totem-counter','Totem Counter',poly((12,2.5),(17.5,6.5),(17.5,14.5),(12,21.5),(6.5,14.5),(6.5,6.5),(12,2.5)),poly((12,7),(12,15)),poly((9,10.5),(15,10.5)))
icon('item-counter','Item Counter',poly((6,6),(8,3),(16,3),(18,6)),box(3,6,18,15,2),poly((8,11),(12,15)),poly((12,11),(8,15)),poly((15.5,11),(15.5,17)))
icon('playtime','Playtime',poly((6,3),(18,3),(18,7),(12,12),(18,17),(18,21),(6,21),(6,17),(12,12),(6,7),(6,3)),poly((9,18.5),(15,18.5)),dot(12,9))
icon('reach','Reach Display',poly((3,12),(21,12)),poly((6,9),(3,12),(6,15)),poly((18,9),(21,12),(18,15)),poly((12,5),(12,9)),poly((12,15),(12,19)))
icon('shulker-preview','Shulker Preview',poly((3,9),(5,4),(19,4),(21,9)),box(4,9,16,12,1.5),poly((4,14),(20,14)),poly((12,14),(12,21)),poly((8,6.5),(16,6.5)))
icon('low-fire','Low Fire',poly((12,2.8),(16,8),(18.5,13),(17.4,17.6),(14.4,20.6),(12,21),(9.6,20.6),(6.6,17.6),(5.8,13.4),(7.4,9.6),(9.6,12.6),(10.6,7.2),(12,2.8)),poly((10,17),(12,19.5),(14,17)),poly((3,22),(21,22)))
icon('damage-tint','Damage Tint',poly((12,3),(17.5,10.5),(18,15),(15,19.5),(9,19.5),(6,15),(6.5,10.5),(12,3)),poly((9.5,14),(10.5,16.5)),poly((14.5,13),(14.5,15)))

def contours(lines):
    out=[];r=STROKE/2
    for points in lines:
        for a,b in zip(points,points[1:]):
            dx=b[0]-a[0];dy=b[1]-a[1];length=math.hypot(dx,dy)
            if length==0: continue
            nx=-dy/length*r;ny=dx/length*r
            out.append([(a[0]+nx,a[1]+ny),(b[0]+nx,b[1]+ny),(b[0]-nx,b[1]-ny),(a[0]-nx,a[1]-ny)])
        for x,y in points:
            out.append([(x+math.cos(i*2*math.pi/16)*r,y+math.sin(i*2*math.pi/16)*r) for i in range(16)])
    return out

def build():
    glyphs={'.notdef':TTGlyphPen(None).glyph(),'space':TTGlyphPen(None).glyph()}
    cmap={32:'space'};manifest=[]
    symbols=[]
    for i,(id,label,lines) in enumerate(ICONS):
        cp=0xF400+i;name='native_'+id.replace('-','_');pen=TTGlyphPen(None)
        for contour in contours(lines):
            pts=[(round(x/24*1000),round((24-y)/24*1000)) for x,y in contour]
            # Consistent winding for the union of overlapping filled stroke pieces.
            area=sum(a[0]*b[1]-b[0]*a[1] for a,b in zip(pts,pts[1:]+pts[:1]))
            if area>0:pts.reverse()
            pen.moveTo(pts[0])
            for p in pts[1:]:pen.lineTo(p)
            pen.closePath()
        g=pen.glyph();g.recalcBounds(None)
        assert 0<=g.xMin<g.xMax<=1000 and 0<=g.yMin<g.yMax<=1000,(id,g.xMin,g.xMax,g.yMin,g.yMax)
        glyphs[name]=g;cmap[cp]=name
        paths=''.join('<polyline points="'+' '.join(f'{x:g},{y:g}' for x,y in l)+'" />' for l in lines)
        symbols.append(f'<symbol id="{id}" viewBox="0 0 24 24"><g fill="none" stroke="currentColor" stroke-width="{STROKE}" stroke-linecap="round" stroke-linejoin="round">{paths}</g></symbol>')
        manifest.append({'id':id,'label':label,'codepoint':f'{cp:04X}','glyph':name,'sha256':hashlib.sha256(json.dumps(lines).encode()).hexdigest()})
    assert len({x['sha256'] for x in manifest})==len(ICONS)
    fb=FontBuilder(1000,isTTF=True);fb.setupGlyphOrder(list(glyphs));fb.setupCharacterMap(cmap);fb.setupGlyf(glyphs)
    fb.setupHorizontalMetrics({name:(1000,0) for name in glyphs});fb.setupHorizontalHeader(ascent=1000,descent=0,lineGap=0)
    fb.setupNameTable({'familyName':'Native Module Icons','styleName':'Regular','uniqueFontIdentifier':'NativeModuleIcons-1.0','fullName':'Native Module Icons','psName':'NativeModuleIcons-Regular','version':'Version 1.0','copyright':'Original Native artwork. CC0-1.0.'})
    fb.setupOS2(sTypoAscender=1000,sTypoDescender=0,usWinAscent=1000,usWinDescent=0);fb.setupPost();fb.setupMaxp()
    fb.font.recalcTimestamp=False;fb.font['head'].created=fb.font['head'].modified=2082844800
    buf=io.BytesIO();fb.save(buf);raw=buf.getvalue();enc=base64.b64encode(raw).decode()
    svg='<svg xmlns="http://www.w3.org/2000/svg"><!-- Original Native module icon source. CC0-1.0. -->\n'+ '\n'.join(symbols)+'\n</svg>\n'
    assets={'native-modules.ttf.b64':'\n'.join(enc[i:i+76] for i in range(0,len(enc),76))+'\n','native-modules.json':json.dumps({'family':'Native Module Icons','version':1,'license':'CC0-1.0','icons':manifest},indent=2)+'\n','native-modules.svg':svg}
    return assets,enc,manifest

def preview(path,enc,items):
    cards=[]
    for it in items:
        glyph=chr(int(it['codepoint'],16))
        cards.append(f'<article><div class="hero">{glyph}</div><strong>{it["label"]}</strong><div class="sizes"><span style="font-size:14px">{glyph}</span><span style="font-size:18px">{glyph}</span><span style="font-size:24px">{glyph}</span><span style="font-size:30px">{glyph}</span></div></article>')
    html='''<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Native — Original module icons</title><style>
@font-face{font-family:NativeIcons;src:url(data:font/ttf;base64,'''+enc+''') format('truetype')}
*{box-sizing:border-box}body{margin:0;background:#0d0e12;color:#eeeef3;font:15px Arial,sans-serif;padding:36px}header{display:flex;justify-content:space-between;align-items:end;margin-bottom:28px}h1{margin:0;font-size:28px;font-weight:600;letter-spacing:-.7px}header p{margin:8px 0 0;color:#999da9;font-size:14px}.tag{color:#7fb9fa;font-size:13px;letter-spacing:.8px}.grid{display:grid;grid-template-columns:repeat(6,minmax(0,1fr));gap:12px}article{border:1px solid #272a33;border-radius:12px;background:#15171d;padding:16px;text-align:center;min-height:171px}.hero{font-family:NativeIcons;font-size:48px;line-height:1;margin:7px auto 16px;color:#f4f5f8}strong{display:block;font-size:14px;font-weight:500;height:22px}.sizes{font-family:NativeIcons;display:flex;gap:14px;justify-content:center;align-items:center;margin-top:12px;height:30px;color:#a0acbe}footer{font-size:13px;color:#8c93a3;margin-top:22px} @media(max-width:600px){body{padding:20px}.grid{grid-template-columns:repeat(2,minmax(0,1fr))}header{display:block}.tag{margin-top:15px}}
</style><header><div><h1>Native module icons</h1><p>Original geometry. One distinct mark for every module.</p></div><div class="tag">'+str(len(items))+' CUSTOM MARKS · CC0</div></header><main class="grid">'''+''.join(cards)+'''</main><footer>Actual bundled font shown at 48 px; each card also tests 14, 18, 24 and 30 px. No stock icon outlines.</footer>'''
    path.write_text(html)

def export(directory, assets, enc, items):
    directory.mkdir(parents=True,exist_ok=True)
    svgdir=directory/'svg';svgdir.mkdir(exist_ok=True)
    for id,label,lines in ICONS:
        paths=''.join('<polyline points="'+' '.join(f'{x:g},{y:g}' for x,y in l)+'" />' for l in lines)
        (svgdir/(id+'.svg')).write_text(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="128" height="128" role="img" aria-label="{label}"><title>{label}</title><g fill="none" stroke="currentColor" stroke-width="{STROKE}" stroke-linecap="round" stroke-linejoin="round">{paths}</g></svg>\n')
    (directory/'native-modules.ttf').write_bytes(base64.b64decode(enc))
    for name in ('native-modules.json','native-modules.svg'):(directory/name).write_text(assets[name])
    (directory/'LICENSE.txt').write_text((ROOT/'src/main/uiassets/native-modules-LICENSE.txt').read_text())
    (directory/'native-module-icons.py').write_text(Path(__file__).read_text())
    preview(directory/'preview.html',enc,items)
    (directory/'README.txt').write_text(f'Native Module Icons — {len(ICONS)} original marks. CC0-1.0.\n\nSVG files are editable 24-unit vectors; use CSS color or replace currentColor.\nTTF codepoints from F400 and stable module IDs are listed in native-modules.json.\nThe bundled font is used in every module UI surface, not the navigation icons.\nTransparent white PNG exports are provided at 64 and 128 px.\nGenerator requires Python + fonttools; run from the Native repository scripts folder.\n')

def main():
    ap=argparse.ArgumentParser();ap.add_argument('--check',action='store_true');ap.add_argument('--preview',type=Path);ap.add_argument('--export-dir',type=Path);args=ap.parse_args()
    assets,enc,items=build();directory=ROOT/'src/main/uiassets'
    for name,content in assets.items():
        p=directory/name
        if args.check:assert p.read_text()==content,'Stale generated asset: '+name
        else:p.write_text(content)
    if args.preview:preview(args.preview,enc,items)
    if args.export_dir:export(args.export_dir,assets,enc,items)
    print(f'{len(items)} original icons; font {len(base64.b64decode(enc)):,} bytes; '+('assets verified' if args.check else 'assets generated'))
if __name__=='__main__':main()
