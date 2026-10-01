"""Serve only the reviewed non-secret SQL bundle on loopback for browser transfer."""
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from html import escape

OUT = Path(__file__).resolve().parent
class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path != '/bundle':
            self.send_error(404)
            return
        sql = (OUT / 'supabase-launch-fixes-uuid.sql').read_text()
        page = '<!doctype html><meta charset="utf-8"><title>Gratify UUID migration review</title><h1>Gratify UUID migration</h1><p>Reviewed source, no credentials or user records.</p><pre id="migration">' + escape(sql) + '</pre>'
        data = page.encode()
        self.send_response(200)
        self.send_header('Content-Type', 'text/html; charset=utf-8')
        self.send_header('Content-Length', str(len(data)))
        self.send_header('Cache-Control', 'no-store')
        self.end_headers()
        self.wfile.write(data)
    def log_message(self, *_):
        pass

HTTPServer(('127.0.0.1', 8876), Handler).serve_forever()
