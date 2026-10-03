import json, os, sys, urllib.request
from concurrent.futures import ThreadPoolExecutor
r = os.path.join(os.environ.get('WORK', '.'), f'mc-{sys.argv[1]}')
idx = json.load(open(r + '/launch.json'))['assetIndex']
objs = json.load(open(f'{r}/assets/indexes/{idx}.json'))['objects']
todo = [(k, v['hash']) for k, v in objs.items() if not k.startswith('minecraft/sounds/') and not k.startswith('minecraft/lang/') or k.endswith('en_us.json')]
def dl(item):
    k, h = item; p = f'{r}/assets/objects/{h[:2]}/{h}'
    if os.path.exists(p): return
    os.makedirs(os.path.dirname(p), exist_ok=True)
    urllib.request.urlretrieve(f'https://resources.download.minecraft.net/{h[:2]}/{h}', p)
with ThreadPoolExecutor(32) as ex: list(ex.map(dl, todo))
print(sys.argv[1], len(todo))
