import http.server, json, struct, zlib
def png(w, h, rows):
    raw = b''.join(b'\x00' + bytes(r) for r in rows)
    def chunk(t, d): return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(raw)) + chunk(b'IEND', b'')
colors = [(255,0,0),(0,255,0),(0,0,255),(255,255,0)]
rows = []
for f in range(4):
    for y in range(32): rows.append([c for x in range(64) for c in (*colors[f], 255)])
STRIP = png(64, 128, rows)
B = 'b'*64; C = 'c'*64
class H(http.server.BaseHTTPRequestHandler):
    def log_message(self, *a): pass
    def do_GET(self):
        if self.path.startswith('/v1/skins/directory'):
            body = json.dumps({'ok':True,'epoch':1,'rev':1,'full':True,'textureBase':'http://127.0.0.1:8099/t/','entries':[{'n':'TestAlice','m':'slim','s':None,'c':B,'u':None,'r':1,'a':{'h':C,'f':4,'p':4}}]}).encode()
            self.send_response(200); self.send_header('Content-Type','application/json'); self.send_header('Content-Length',str(len(body))); self.end_headers(); self.wfile.write(body); return
        if self.path.startswith('/v1/skins/stream'):
            self.send_response(200); self.send_header('Content-Type','text/event-stream'); self.end_headers()
            self.wfile.write(b'event: hello\ndata: {"epoch":1,"rev":1,"resync":false}\n\n'); self.wfile.flush()
            import time; time.sleep(120); return
        if self.path == '/t/' + C:
            self.send_response(200); self.send_header('Content-Type','image/png'); self.send_header('Content-Length',str(len(STRIP))); self.end_headers(); self.wfile.write(STRIP); return
        self.send_response(404); self.end_headers()
http.server.ThreadingHTTPServer(('127.0.0.1', 8099), H).serve_forever()
