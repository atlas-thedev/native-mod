"""Adds extra Lucide icons to the bundled subset without touching the existing glyphs/codepoints.

    python3 scripts/lucide-extra.py <full lucide.ttf (lucide-static 1.54.0)>

Each name in EXTRA gets codepoint 0xF100 + index (keep the order: append only). Theme.I_* constants mirror this list.
"""
import base64, copy, sys
from fontTools.ttLib import TTFont

EXTRA = ['gauge', 'mouse-pointer-click', 'compass', 'map-pin', 'clock-3', 'memory-stick', 'signal', 'zap', 'footprints',
         'zoom-in', 'sun', 'shirt', 'move', 'rotate-ccw', 'sliders-horizontal', 'layout-dashboard', 'palette', 'check',
         'eye-off', 'mouse', 'timer', 'wifi', 'glasses', 'backpack', 'hand', 'hard-hat', 'layout-grid', 'magnet', 'type',
         'person-standing', 'activity', 'crosshair', 'feather', 'arrows-up-from-line', 'grid-3-x-3', 'brush',
         'heart', 'star', 'list', 'store', 'ban']
B64 = 'src/main/uiassets/lucide.ttf.b64'

full = TTFont(sys.argv[1])
names = {v: k for k, v in full.getBestCmap().items()}
out = TTFont(__import__('io').BytesIO(base64.b64decode(open(B64).read())))
order = out.getGlyphOrder()
for i, name in enumerate(EXTRA):
    g = 'x_' + name.replace('-', '_')
    src = full.getGlyphSet()  # noqa: F841 (forces glyf load)
    if g not in order:
        order.append(g)
        out['glyf'].glyphs[g] = copy.deepcopy(full['glyf'][full.getBestCmap()[names[name]]])
        out['hmtx'].metrics[g] = full['hmtx'].metrics[full.getBestCmap()[names[name]]]
    for table in out['cmap'].tables:
        if table.isUnicode():
            table.cmap[0xF100 + i] = g
out.setGlyphOrder(order)
out['glyf'].glyphOrder = order
import io
buf = io.BytesIO(); out.save(buf)
open(B64, 'w').write(base64.b64encode(buf.getvalue()).decode())
print('icons:', len(out.getBestCmap()))
