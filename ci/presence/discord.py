# Stand-in for the Discord desktop app: answers the IPC handshake and logs every frame.
#   python3 discord.py <socket path> <log file>
import json, os, socket, struct, sys, threading
path, log = sys.argv[1], sys.argv[2]
if os.path.exists(path): os.remove(path)
srv = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM); srv.bind(path); srv.listen(4)
out = open(log, 'a', buffering=1)
def serve(c):
    buf = b''
    while True:
        chunk = c.recv(65536)
        if not chunk: return
        buf += chunk
        while len(buf) >= 8:
            op, n = struct.unpack('<ii', buf[:8])
            if len(buf) < 8 + n: break
            body = buf[8:8+n].decode(); buf = buf[8+n:]
            out.write(json.dumps({'op': op, 'body': json.loads(body)}) + '\n')
            if op == 0:
                ready = json.dumps({'cmd': 'DISPATCH', 'evt': 'READY', 'data': {'v': 1}}).encode()
                c.sendall(struct.pack('<ii', 1, len(ready)) + ready)
            elif op == 1:
                reply = json.dumps({'cmd': 'SET_ACTIVITY', 'nonce': json.loads(body).get('nonce'), 'evt': None, 'data': {}}).encode()
                c.sendall(struct.pack('<ii', 1, len(reply)) + reply)
while True:
    c, _ = srv.accept()
    threading.Thread(target=serve, args=(c,), daemon=True).start()
