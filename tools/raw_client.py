"""Raw protocol client for debugging the chat server (no GUI, no Java).

Usage:   python tools/raw_client.py [host] [port]      (defaults: localhost 3000)
The first line you type is your username (press Enter alone for read-only mode).
Then type messages, 'allUsers', or 'bye'. Every line the server sends is printed as-is,
so you can see the protocol exactly as it travels on the wire.
"""
import socket
import sys
import threading

host = sys.argv[1] if len(sys.argv) > 1 else "localhost"
port = int(sys.argv[2]) if len(sys.argv) > 2 else 3000

sock = socket.create_connection((host, port))
print(f"Connected to {host}:{port}. Type your username (or just Enter for read-only):")


def receive():
    with sock.makefile("r", encoding="utf-8") as lines:
        for line in lines:
            print("<- " + line.rstrip("\n"))
    print("<- (server closed the connection, press Enter to quit)")


receiver = threading.Thread(target=receive, daemon=True)
receiver.start()

try:
    for typed in sys.stdin:
        sock.sendall(typed.encode("utf-8"))  # typed already ends with '\n'
        if typed.strip().lower() in ("bye", "end"):
            receiver.join(timeout=2)  # let the server's BYE line arrive before closing
            break
except (KeyboardInterrupt, OSError):
    pass
finally:
    sock.close()
