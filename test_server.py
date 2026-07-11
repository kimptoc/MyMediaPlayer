from http.server import BaseHTTPRequestHandler, HTTPServer
import time
import json

class MyHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        time.sleep(0.1)  # Simulate 100ms latency
        self.send_response(200)
        self.send_header('Content-type', 'application/json')
        self.end_headers()
        self.wfile.write(b'{"status": "ok"}')
    def log_message(self, format, *args):
        pass # suppress logs

if __name__ == '__main__':
    server = HTTPServer(('127.0.0.1', 8080), MyHandler)
    server.serve_forever()
