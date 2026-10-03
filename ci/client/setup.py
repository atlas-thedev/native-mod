# Downloads a runnable Fabric client: python3 setup.py <mc> <loader>
import json, os, sys, urllib.request, zipfile, hashlib
mc, loader = sys.argv[1], sys.argv[2]
root = os.path.abspath(os.path.join(os.environ.get('WORK', '.'), f'mc-{mc}')); os.makedirs(root+'/libs', exist_ok=True); os.makedirs(root+'/natives', exist_ok=True)
def get(url, path):
    if os.path.exists(path): return path
    os.makedirs(os.path.dirname(path), exist_ok=True)
    urllib.request.urlretrieve(url, path+'.part'); os.rename(path+'.part', path); return path
man = json.load(urllib.request.urlopen('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'))
vj = json.load(urllib.request.urlopen([v for v in man['versions'] if v['id']==mc][0]['url']))
cp = []
def allowed(lib):
    rules = lib.get('rules'); 
    if not rules: return True
    ok = False
    for r in rules:
        os_ = r.get('os', {}).get('name')
        if os_ is None or os_ == 'linux': ok = r['action'] == 'allow'
    return ok
for lib in vj['libraries']:
    if not allowed(lib): continue
    d = lib.get('downloads', {})
    if 'artifact' in d:
        p = get(d['artifact']['url'], root+'/libs/'+d['artifact']['path'])
        if 'natives' in lib['name']:
            with zipfile.ZipFile(p) as z:
                for n in z.namelist():
                    if n.endswith('.so'): open(root+'/natives/'+os.path.basename(n),'wb').write(z.read(n))
        else: cp.append(p)
    nat = lib.get('natives', {}).get('linux')
    if nat and nat in d.get('classifiers', {}):
        c = d['classifiers'][nat]; p = get(c['url'], root+'/libs/'+c['path'])
        with zipfile.ZipFile(p) as z:
            for n in z.namelist():
                if n.endswith('.so'): open(root+'/natives/'+os.path.basename(n),'wb').write(z.read(n))
fab = json.load(urllib.request.urlopen(f'https://meta.fabricmc.net/v2/versions/loader/{mc}/{loader}/profile/json'))
names = set()
fcp = []
for lib in fab['libraries']:
    g, a, v = lib['name'].split(':')[:3]
    path = f"{g.replace('.','/')}/{a}/{v}/{a}-{v}.jar"
    fcp.append(get(lib['url'].rstrip('/')+'/'+path, root+'/libs/'+path)); names.add(g+':'+a)
# drop vanilla libs that fabric overrides (e.g. asm)
cp = [p for p in cp if not any(('/'+n.split(':')[0].replace('.','/')+'/'+n.split(':')[1]+'/') in p for n in names)]
client = get(vj['downloads']['client']['url'], root+'/client.jar')
ai = vj['assetIndex']; get(ai['url'], root+f"/assets/indexes/{ai['id']}.json")
json.dump({'cp': fcp+cp+[client], 'main': fab['mainClass'], 'assetIndex': ai['id'], 'java': vj.get('javaVersion',{}).get('majorVersion')}, open(root+'/launch.json','w'))
print('ready', root, fab['mainClass'], vj.get('javaVersion'))
