"""TCP relay 127.0.0.1:25597 -> :25598 that holds each connection for DELAY seconds first.

1.16-1.19 clients started with --server connect while resources are still loading and can render the
world before the block models are baked (vanilla NPE "Tesselating block in world"). Holding the login
until the title screen would be up makes the test behave like a player clicking Join."""
import socket, sys, threading, time

DELAY = float(sys.argv[1]) if len(sys.argv) > 1 else 25


def pipe(src, dst):
    try:
        while True:
            data = src.recv(65536)
            if not data:
                break
            dst.sendall(data)
    except OSError:
        pass
    finally:
        for s in (src, dst):
            try:
                s.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass


def handle(client):
    time.sleep(DELAY)
    server = socket.create_connection(('127.0.0.1', 25598))
    threading.Thread(target=pipe, args=(client, server), daemon=True).start()
    pipe(server, client)


listener = socket.socket()
listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
listener.bind(('127.0.0.1', 25597))
listener.listen(8)
print('delay proxy ready', flush=True)
while True:
    conn, _ = listener.accept()
    threading.Thread(target=handle, args=(conn,), daemon=True).start()
