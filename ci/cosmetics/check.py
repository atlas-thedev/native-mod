# python3 check.py <shot.rgb> <width> <height>: counts pixels of each test cosmetic colour (lighting only scales
# brightness, so the hue survives). Exit 1 when any slot is missing.
import sys
data = open(sys.argv[1], 'rb').read(); w, h = int(sys.argv[2]), int(sys.argv[3])
count = {'hat': 0, 'glasses': 0, 'back': 0, 'shoes': 0}
for i in range(0, w * h * 3, 3):
    r, g, b = data[i], data[i + 1], data[i + 2]
    hi = max(r, g, b)
    if hi < 60: continue
    lo = min(r, g, b)
    if lo > 0.25 * hi: continue
    if r > 0.7 * hi and b > 0.7 * hi and g < 0.25 * hi: count['hat'] += 1
    elif g > 0.7 * hi and b > 0.7 * hi and r < 0.25 * hi: count['glasses'] += 1
    elif r > 0.7 * hi and g > 0.7 * hi and b < 0.25 * hi: count['back'] += 1
    elif r > 0.7 * hi and g < 0.25 * hi and b < 0.25 * hi: count['shoes'] += 1
print('COSMETIC PIXELS', count)
missing = [k for k, v in count.items() if v < 25]
if missing:
    print('missing:', ', '.join(missing)); sys.exit(1)
