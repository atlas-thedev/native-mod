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
def msg(sender, text, ago=0, **kw):
    n = next(ids); return dict({'id': f'm{n}', 'senderId': sender, 'senderName': {'me': 'TestAlice', 'f1': 'Dinal', 'f2': 'Kasun', 'f3': 'Nethmi'}.get(sender, sender), 'content': text, 'createdAt': NOW() - ago}, **kw)
DM = {'f1': [msg('f1', 'machan server ekata enawada?', 86400000 * 2), msg('me', 'ow, poddak inna', 86400000 * 2 - 60000),
             msg('f1', 'ela! mama 1.21.4 eke', 3600000), msg('f1', 'Native chat eka game eka athule wada karanawa 🔥', 3500000),
             msg('me', 'supiri, UI eka launcher eka wage ne', 120000), msg('f1', 'ow, FPS drop ekak naha', 60000)]}
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
        if p.startswith('/v1/social/relay/') and p.endswith('/messages'):
            b = self.body(); m = msg('me', b.get('content', ''))
            (GM if '/groups/' in p else DM).setdefault(p.split('/')[5], []).append(m)
            print('SENT', p, b.get('content'), flush=True)
            return self.j({'message': m})
        self.body(); return self.j({'ok': True})
http.server.ThreadingHTTPServer(('127.0.0.1', 8099), H).serve_forever()
