# Stand-in for the Native API with a few friends, a group and live Relay events.
#   python3 mock.py            (listens on 127.0.0.1:8099)
#   curl -X POST 127.0.0.1:8099/test/push?from=f1&text=hi   -> pushes a DM from friend f1 down every mod stream
import http.server, json, time, threading, urllib.parse, queue, itertools
NOW = lambda: int(time.time() * 1000)
ME = {'id': 'me', 'name': 'TestAlice'}
FRIENDS = [
    {'id': 'f1', 'name': 'Dinal', 'status': 'online', 'activity': 'Playing Minecraft 1.21.4', 'online': True, 'unread': 2, 'skin': None},
    {'id': 'f2', 'name': 'Kasun', 'status': 'idle', 'activity': None, 'online': True, 'unread': 0, 'skin': None},
    {'id': 'f3', 'name': 'Nethmi', 'status': 'online', 'activity': 'Hypixel', 'serverAddress': 'mc.hypixel.net', 'online': True, 'unread': 0, 'skin': None},
    {'id': 'f4', 'name': 'Steve', 'status': 'offline', 'online': False, 'unread': 0, 'skin': None},
]
ids = itertools.count(1000)

# ── store / textures (wardrobe) ──
import hashlib, io
def _png(w, h, paint):
    from PIL import Image
    im = Image.new('RGBA', (w, h), (0, 0, 0, 0)); px = im.load()
    for y in range(h):
        for x in range(w): px[x, y] = paint(x, y)
    b = io.BytesIO(); im.save(b, 'PNG'); return b.getvalue()
def _skin(x, y):
    if 8 <= x < 16 and 8 <= y < 16:  # face: red stripe on the image-left = wearer's right
        return (220, 60, 60, 255) if x < 10 else ((40, 40, 40, 255) if y == 12 and x in (10, 13) else (230, 190, 150, 255))
    if y < 16 and x < 32: return (120, 80, 50, 255)      # head
    if 16 <= y < 32 and 16 <= x < 40: return (40, 120, 200, 255)  # body
    if 16 <= y < 32 and x >= 40: return (230, 190, 150, 255)       # right arm
    if 16 <= y < 32 and x < 16: return (50, 50, 140, 255)          # right leg
    if y >= 48 and 32 <= x < 48: return (200, 160, 130, 255)       # left arm
    if y >= 48 and 16 <= x < 32: return (30, 30, 110, 255)         # left leg
    return (0, 0, 0, 0)
def _cape(x, y): return (180, 60 + y * 5, 200, 255) if x < 22 else (90, 30, 100, 255)
def _hat(x, y): return (20, 20, 24, 255) if y < 16 else (200, 30, 30, 255)
HAT_MODEL = json.dumps({"format": 1, "texture": [64, 32], "parts": [{"id": "hat", "attach": "head", "pivot": [0, 0, 0],
    "cubes": [{"origin": [-5, -9, -5], "size": [10, 1, 10], "uv": [0, 0]}, {"origin": [-3.5, -15, -3.5], "size": [7, 6, 7], "uv": [0, 11]}]}]}).encode()
TEX = {}
MEDIA = {}
def _gradient(w, h, phase=0):
    from PIL import Image
    im = Image.new('RGB', (w, h)); px = im.load()
    for y in range(h):
        for x in range(w): px[x, y] = ((x * 255 // w + phase) % 256, y * 255 // h, 160)
    return im
def _store_png(w, h):
    b = io.BytesIO(); _gradient(w, h).save(b, 'PNG'); MEDIA['pic1.png'] = (b.getvalue(), 'image/png')
def _store_gif(name, phase0):
    frames = [_gradient(160, 100, phase0 + i * 40) for i in range(6)]
    b = io.BytesIO(); frames[0].save(b, 'GIF', save_all=True, append_images=frames[1:], duration=120, loop=0); MEDIA[name] = (b.getvalue(), 'image/gif')
_store_png(480, 300); _store_gif('g1.gif', 0); _gif2 = _store_gif('g2.gif', 90)
def _put(b): h = hashlib.sha256(b).hexdigest(); TEX[h] = b; return h
SKIN_H = _put(_png(64, 64, _skin)); CAPE_H = _put(_png(64, 32, _cape)); HAT_T = _put(_png(64, 32, _hat)); HAT_M = _put(HAT_MODEL)
B = 'http://127.0.0.1:8099/csl/textures/'
CATALOG = [
    {'id': 'cape-lilac', 'name': 'Lilac Cloak', 'kind': 'cape', 'stillUrl': B + CAPE_H},
    {'id': 'cape-two', 'name': 'Second Cape', 'kind': 'cape', 'stillUrl': B + CAPE_H},
    {'id': 'tophat', 'name': 'Top Hat', 'kind': 'cosmetic', 'slot': 'hats', 'stillUrl': B + HAT_T, 'modelUrl': B + HAT_M, 'textureUrl': B + HAT_T},
]
LOCKER = {'equipped': 'cape-lilac', 'wearing': {'hats': 'tophat'}}
ME['skin'] = SKIN_H

def msg(sender, text, ago=0, **kw):
    n = next(ids); return dict({'id': f'm{n}', 'senderId': sender, 'senderName': {'me': 'TestAlice', 'f1': 'Dinal', 'f2': 'Kasun', 'f3': 'Nethmi'}.get(sender, sender), 'content': text, 'createdAt': NOW() - ago}, **kw)
MURL = 'http://127.0.0.1:8099/v1/social/media/'
DM = {'f1': [msg('f1', 'machan server ekata enawada?', 86400000 * 2), msg('me', 'ow, poddak inna', 86400000 * 2 - 60000),
             msg('f1', 'ela! mama 1.21.4 eke', 3600000), msg('f1', 'Native chat eka game eka athule wada karanawa 🔥', 3500000),
             msg('me', 'supiri, UI eka launcher eka wage ne', 120000), msg('f1', 'ow, FPS drop ekak naha', 60000),
             msg('f1', '', 50000, mediaUrl=MURL + 'pic1.png', mediaName='pic1.png', mediaKind='image', isMedia=True),
             msg('me', '', 40000, mediaUrl=MURL + 'g1.gif', mediaName='GIF.gif', mediaKind='image', isMedia=True)]}
GROUPS = [{'id': 'g1', 'name': 'SMP Squad', 'memberCount': 4, 'unreadCount': 3, 'members': [{'id': 'me', 'name': 'TestAlice'}, {'id': 'f1', 'name': 'Dinal'}, {'id': 'f2', 'name': 'Kasun'}, {'id': 'f3', 'name': 'Nethmi'}]}]
GM = {'g1': [msg('f2', 'tonight 9pm build session', 7200000), msg('f3', "I'll bring the redstone", 7100000), msg('f1', 'ela 👍', 7000000)]}
streams = []
def push(event, data):
    for q in list(streams): q.put((event, data))
class H(http.server.BaseHTTPRequestHandler):
    protocol_version = 'HTTP/1.1'
    def log_message(self, *a): pass
    def j(self, obj, code=200):
        b = json.dumps(obj).encode(); self.send_response(code); self.send_header('Content-Type', 'application/json'); self.send_header('Content-Length', str(len(b))); self.end_headers(); self.wfile.write(b)
    def body(self):
        n = int(self.headers.get('Content-Length') or 0); return json.loads(self.rfile.read(n) or b'{}') if n else {}
    def do_GET(self):
        u = urllib.parse.urlparse(self.path); p = u.path
        if p.startswith('/v1/skins/directory'): return self.j({'ok': True, 'epoch': 1, 'rev': 1, 'full': True, 'textureBase': 'http://127.0.0.1:8099/t/', 'entries': []})
        if p.startswith('/t/') or p.startswith('/csl/textures/'):
            b = TEX.get(p.rsplit('/', 1)[1])
            if b is None: self.send_response(404); self.send_header('Content-Length', '0'); self.end_headers(); return
            self.send_response(200); self.send_header('Content-Type', 'image/png'); self.send_header('Content-Length', str(len(b))); self.end_headers(); self.wfile.write(b); return
        if p.startswith('/v1/social/media/'):
            e = MEDIA.get(p.rsplit('/', 1)[1])
            if e is None: self.send_response(404); self.send_header('Content-Length', '0'); self.end_headers(); return
            self.send_response(200); self.send_header('Content-Type', e[1]); self.send_header('Content-Length', str(len(e[0]))); self.end_headers(); self.wfile.write(e[0]); return
        if p == '/v1/social/relay/gifs':
            return self.j({'ok': True, 'source': 'mock', 'hasMore': False, 'gifs': [{'id': n, 'title': n, 'url': MURL + n, 'preview': MURL + n} for n in ('g1.gif', 'g2.gif', 'g1.gif', 'g2.gif')]})
        if p == '/v1/store/catalog': return self.j({'ok': True, 'textureBase': B, 'items': CATALOG})
        if p == '/v1/store/me': return self.j({'ok': True, 'equipped': LOCKER['equipped'], 'wearing': LOCKER['wearing'], 'sides': {}, 'owned': [{'id': i['id']} for i in CATALOG], 'dyes': {}})
        if p == '/v1/mod/me': return self.j({'ok': True, 'account': ME})
        if p == '/v1/mod/friends':
            g = GROUPS[0]; g['lastMessage'] = {'content': GM['g1'][-1]['content'], 'senderName': GM['g1'][-1]['senderName'], 'createdAt': GM['g1'][-1]['createdAt']}
            return self.j({'account': ME, 'friends': FRIENDS, 'requests': {'received': [{'id': 'r1'}]}})
        if p == '/v1/social/relay/groups':
            g = GROUPS[0]; g['lastMessage'] = {'content': GM['g1'][-1]['content'], 'senderName': GM['g1'][-1]['senderName'], 'createdAt': GM['g1'][-1]['createdAt']}
            return self.j({'groups': GROUPS})
        if p.startswith('/v1/social/relay/dm/') and p.endswith('/messages'):
            return self.j({'messages': DM.get(p.split('/')[5], []), 'hasMore': False})
        if p.startswith('/v1/social/relay/groups/') and p.endswith('/messages'):
            return self.j({'messages': GM.get(p.split('/')[5], []), 'hasMore': False})
        if p in ('/v1/mod/stream', '/v1/skins/stream'):
            self.send_response(200); self.send_header('Content-Type', 'text/event-stream'); self.send_header('Cache-Control', 'no-cache'); self.end_headers()
            q = queue.Queue(); streams.append(q) if p == '/v1/mod/stream' else None
            try:
                self.wfile.write(b'event: hello\ndata: {"epoch":1,"rev":1,"resync":false}\n\n'); self.wfile.flush()
                while True:
                    try: ev, data = q.get(timeout=20)
                    except queue.Empty: self.wfile.write(b': ping\n\n'); self.wfile.flush(); continue
                    self.wfile.write(f'event: {ev}\ndata: {json.dumps(data)}\n\n'.encode()); self.wfile.flush()
            except Exception: pass
            finally:
                if q in streams: streams.remove(q)
            return
        self.send_response(404); self.send_header('Content-Length', '0'); self.end_headers()
    def do_POST(self):
        u = urllib.parse.urlparse(self.path); p = u.path; qs = urllib.parse.parse_qs(u.query)
        if p == '/test/push':
            who = qs.get('from', ['f1'])[0]; text = qs.get('text', ['hey!'])[0]; g = qs.get('group', [None])[0]
            m = msg(who, text)
            if g: GM[g].append(m); push('group:message', {'groupId': g, 'message': m})
            else: m['receiverId'] = 'me'; DM.setdefault(who, []).append(m); push('message:new', {'message': m})
            return self.j({'ok': True, 'streams': len(streams)})
        if p == '/v1/social/relay/upload':
            import base64
            b = self.body(); head, _, data = b['data'].partition(',')
            raw = base64.b64decode(data); name = 'up%d.%s' % (len(MEDIA), 'png' if 'png' in head else 'gif' if 'gif' in head else 'jpg')
            MEDIA[name] = (raw, head[5:].split(';')[0]); print('UPLOAD', name, len(raw), flush=True)
            return self.j({'ok': True, 'url': MURL + name, 'name': b.get('name'), 'size': len(raw), 'kind': 'image'})
        if p.startswith('/v1/social/relay/') and p.endswith('/messages'):
            b = self.body(); m = msg('me', b.get('content', ''), mediaUrl=b.get('mediaUrl'), mediaName=b.get('mediaName'), mediaKind=b.get('mediaKind'), isMedia=b.get('isMedia'))
            (GM if '/groups/' in p else DM).setdefault(p.split('/')[5], []).append(m)
            print('SENT', p, b.get('content'), flush=True)
            return self.j({'message': m})
        if p == '/v1/store/equip':
            b = self.body(); it = b.get('itemId')
            if it is None and b.get('slot'): LOCKER['wearing'].pop(b['slot'], None)
            elif it is None: LOCKER['equipped'] = None
            else:
                item = next(i for i in CATALOG if i['id'] == it)
                if item['kind'] == 'cape': LOCKER['equipped'] = it
                else: LOCKER['wearing'][item['slot']] = it
            print('EQUIP', b, flush=True)
            return self.j({'ok': True, 'equipped': LOCKER['equipped'], 'wearing': LOCKER['wearing'], 'sides': {}, 'owned': [{'id': i['id']} for i in CATALOG]})
        self.body(); return self.j({'ok': True})
http.server.ThreadingHTTPServer(('127.0.0.1', 8099), H).serve_forever()
