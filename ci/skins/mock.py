# Native API stand-in: TestAlice (local player) wears a red skin, TestBob (remote, offline server) a green
# one, TestCarol (remote, no textures property at all) a blue one.
import http.server, json, struct, zlib, sys, hashlib
def png(w, h, rgb):
    raw = b''.join(b'\x00' + bytes([*rgb, 255] * w) for _ in range(h))
    def chunk(t, d): return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(raw)) + chunk(b'IEND', b'')
SKINS = {n: png(64, 64, c) for n, c in (('alice', (255, 0, 0)), ('bob', (0, 255, 0)), ('carol', (0, 0, 255)))}
H = {n: hashlib.sha256(b).hexdigest() for n, b in SKINS.items()}
BY_HASH = {H[n]: SKINS[n] for n in SKINS}
class Handler(http.server.BaseHTTPRequestHandler):
    def log_message(self, fmt, *a): sys.stderr.write('MOCK ' + (fmt % a) + '\n'); sys.stderr.flush()
    def send(self, code, ctype, body):
        self.send_response(code); self.send_header('Content-Type', ctype); self.send_header('Content-Length', str(len(body))); self.end_headers(); self.wfile.write(body)
    def do_GET(self):
        if self.path.startswith('/v1/skins/directory'):
            entries = [{'n': 'TestAlice', 'm': 'default', 's': H['alice'], 'c': None, 'u': None, 'r': 1, 'a': None},
                       {'n': 'TestBob', 'm': 'slim', 's': H['bob'], 'c': None, 'u': None, 'r': 2, 'a': None},
                       {'n': 'TestCarol', 'm': 'default', 's': H['carol'], 'c': None, 'u': None, 'r': 3, 'a': None}]
            return self.send(200, 'application/json', json.dumps({'ok': True, 'epoch': 1, 'rev': 3, 'full': True, 'textureBase': 'http://127.0.0.1:8098/t/', 'entries': entries}).encode())
        if self.path.startswith('/v1/skins/stream'):
            self.send_response(200); self.send_header('Content-Type', 'text/event-stream'); self.end_headers()
            self.wfile.write(b'event: hello\ndata: {"epoch":1,"rev":3,"resync":false}\n\n'); self.wfile.flush()
            import time; time.sleep(300); return
        if self.path.startswith('/t/') and self.path[3:] in BY_HASH:
            return self.send(200, 'image/png', BY_HASH[self.path[3:]])
        self.send(404, 'text/plain', b'nope')
print(json.dumps(H), flush=True)
http.server.ThreadingHTTPServer(('127.0.0.1', 8098), Handler).serve_forever()
