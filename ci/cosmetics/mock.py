# Native API stand-in for the cosmetics test: TestAlice wears a grey skin plus four solid-colour test cosmetics,
# one per slot: hat = magenta, glasses = cyan, back = yellow, shoes = red. run.sh finds those colours on screen.
import http.server, json, struct, zlib, sys, hashlib, time
def png(w, h, rgb):
    raw = b''.join(b'\x00' + bytes([*rgb, 255] * w) for _ in range(h))
    def chunk(t, d): return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(raw)) + chunk(b'IEND', b'')
def model(attach, cubes, anim=None, armor=None):
    part = {'id': 'p', 'attach': attach, 'cubes': [{'origin': o, 'size': s, 'uv': [0, 0]} for o, s in cubes]}
    if anim: part['anim'] = anim
    if armor: part['armor'] = armor
    return json.dumps({'format': 1, 'texture': [64, 64], 'parts': [part]}).encode()
FILES = {}
def put(data):
    h = hashlib.sha256(data).hexdigest(); FILES[h] = data; return h
SKIN = put(png(64, 64, (128, 128, 128)))
WORN = []
for slot, rgb, m in (
    ('hat', (255, 0, 255), model('head', [([-5, -12, -5], [10, 4, 10])], anim=[{'type': 'spin', 'axis': 'y', 'speed': 90}], armor={'slot': 'head'})),
    ('glasses', (0, 255, 255), model('head', [([-4.5, -5.5, -5], [9, 3, 1])])),
    ('back', (255, 255, 0), model('body', [([-12, 0, 2.5], [24, 10, 1])], anim=[{'type': 'swing', 'axis': 'z', 'amplitude': 4, 'speed': 1}])),
    ('shoes', (255, 0, 0), model('rightLeg', [([-2.5, 9, -3.5], [5, 3, 6])])),
):
    WORN.append({'i': 'test_' + slot, 'm': put(m), 'x': put(png(64, 64, rgb))})
class Handler(http.server.BaseHTTPRequestHandler):
    def log_message(self, fmt, *a): sys.stderr.write('MOCK ' + (fmt % a) + '\n'); sys.stderr.flush()
    def send(self, code, ctype, body):
        self.send_response(code); self.send_header('Content-Type', ctype); self.send_header('Content-Length', str(len(body))); self.end_headers(); self.wfile.write(body)
    def do_GET(self):
        if self.path.startswith('/v1/skins/directory'):
            entries = [{'n': 'TestAlice', 'm': 'default', 's': SKIN, 'c': None, 'u': None, 'r': 1, 'a': None, 'k': WORN}]
            return self.send(200, 'application/json', json.dumps({'ok': True, 'epoch': 1, 'rev': 1, 'full': True, 'textureBase': 'http://127.0.0.1:8097/t/', 'entries': entries}).encode())
        if self.path.startswith('/v1/skins/stream'):
            self.send_response(200); self.send_header('Content-Type', 'text/event-stream'); self.end_headers()
            self.wfile.write(b'event: hello\ndata: {"epoch":1,"rev":1,"resync":false}\n\n'); self.wfile.flush()
            time.sleep(600); return
        if self.path.startswith('/t/') and self.path[3:] in FILES:
            return self.send(200, 'application/octet-stream', FILES[self.path[3:]])
        # Mojang services for 1.16-1.19 clients with an offline token (authlib host overrides in run.sh):
        # without these, old clients grey out Multiplayer and never join the test server
        if self.path.startswith('/privileges') or self.path.startswith('/player/attributes'):
            on = {'enabled': True}
            return self.send(200, 'application/json', json.dumps({'privileges': {'onlineChat': on, 'multiplayerServer': on, 'multiplayerRealms': on, 'telemetry': {'enabled': False}}, 'profanityFilterPreferences': {'profanityFilterOn': False}}).encode())
        if self.path.startswith('/privacy/blocklist'):
            return self.send(200, 'application/json', b'{"blockedProfiles":[]}')
        self.send(404, 'text/plain', b'nope')
print('mock ready', flush=True)
http.server.ThreadingHTTPServer(('127.0.0.1', 8097), Handler).serve_forever()
