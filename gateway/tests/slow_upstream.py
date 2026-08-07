from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from time import sleep


REQUEST_COUNT = Path('/tmp/request-count')


class SlowHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        count = int(REQUEST_COUNT.read_text() if REQUEST_COUNT.exists() else '0') + 1
        REQUEST_COUNT.write_text(str(count))
        sleep(12)
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.end_headers()
        self.wfile.write(b'{"success":true}')

    def log_message(self, _format, *_args):
        return


ThreadingHTTPServer(('0.0.0.0', 8080), SlowHandler).serve_forever()
