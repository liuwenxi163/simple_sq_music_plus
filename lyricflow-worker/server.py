"""Persistent, single-worker LyricFlow adapter. No public port required."""
from contextlib import contextmanager
import hashlib
import hmac
import json
import os
from pathlib import Path
import signal
import sqlite3
import threading
import time
import uuid
from http.server import HTTPServer, BaseHTTPRequestHandler

ROOT = Path(os.environ.get('MUSIC_PATH', '/music')).resolve()
STATE = Path(os.environ.get('STATE_DIR', '/state'))
KEY = os.environ.get('LYRICFLOW_KEY', '')
if len(KEY) < 32:
    raise RuntimeError('LYRICFLOW_KEY must contain at least 32 characters')
STATE.mkdir(parents=True, exist_ok=True)
DB = STATE / 'jobs.sqlite'
stop = threading.Event()
wake = threading.Event()

@contextmanager
def connect():
    c = sqlite3.connect(DB, timeout=10)
    c.row_factory = sqlite3.Row
    try:
        with c: yield c
    finally: c.close()

with connect() as c:
    c.execute('CREATE TABLE IF NOT EXISTS jobs(id TEXT PRIMARY KEY,path TEXT,provider TEXT,embed INTEGER,state TEXT,stats TEXT,created REAL,updated REAL)')
    c.execute("UPDATE jobs SET state='interrupted', updated=? WHERE state='running'", (time.time(),))

def safe_path(value):
    if not isinstance(value, str) or len(value) > 2048:
        raise ValueError('Invalid path')
    p = (ROOT / value).resolve(strict=True)
    if not p.is_relative_to(ROOT) or not p.is_dir():
        raise ValueError('Directory outside music library')
    return p

def job_view(row):
    result = dict(row)
    result['stats'] = json.loads(result['stats'])
    result['embed'] = bool(result['embed'])
    return result

def submit(body):
    p = safe_path(body.get('path', ''))
    provider = body.get('provider', 'tunehub')
    if provider not in ('tunehub', 'lrcapi') or not isinstance(body.get('embed', False), bool):
        raise ValueError('Invalid options')
    relative = str(p.relative_to(ROOT))
    if relative == '.': relative = ''
    with connect() as c:
        c.execute('BEGIN IMMEDIATE')
        existing = c.execute("SELECT * FROM jobs WHERE state IN ('queued','running') AND path=? AND provider=? AND embed=?", (relative, provider, int(body.get('embed', False)))).fetchone()
        if existing: return job_view(existing)
        if c.execute("SELECT COUNT(*) FROM jobs WHERE state IN ('queued','running')").fetchone()[0] >= 256:
            raise OverflowError('Queue full')
        ident = uuid.uuid4().hex
        now = time.time()
        c.execute('INSERT INTO jobs VALUES(?,?,?,?,?,?,?,?)', (ident, relative, provider, int(body.get('embed', False)), 'queued', '{}', now, now))
        row = c.execute('SELECT * FROM jobs WHERE id=?', (ident,)).fetchone()
    wake.set()
    return job_view(row)

def process(row):
    from src.config import Config
    from src.main import LyricFlow
    from src.scanner import MusicScanner
    root = safe_path(row['path'])
    config = Config.from_env()
    config.music_path = str(ROOT)
    config.api_provider = row['provider']
    config.overwrite_lyrics = config.overwrite_cover = False
    config.download_lyrics = config.download_cover = True
    config.force_update_lyrics = config.force_update_cover = config.force_update_basic_info = False
    config.update_lyrics = config.update_cover = config.update_basic_info = bool(row['embed'])
    config.audio_extensions = tuple(x for x in config.audio_extensions if x != '.strm')
    tool = LyricFlow(config)
    original = MusicScanner(config)
    ident = row['id']
    class SafeScanner:
        def scan(self):
            for item in original.scan(str(root)):
                if stop.is_set(): raise InterruptedError()
                real = item.path.resolve(strict=True)
                if item.path.is_symlink() or not real.is_relative_to(ROOT): continue
                # Validate sidecars too; upstream writes cover.jpg beside each file.
                if not item.lyrics_path.resolve().is_relative_to(ROOT) or not item.cover_path.resolve().is_relative_to(ROOT): continue
                if item.lyrics_path.is_symlink() or item.cover_path.is_symlink(): continue
                with connect() as c: c.execute('UPDATE jobs SET stats=?,updated=? WHERE id=?', (json.dumps(tool.stats), time.time(), ident))
                yield item
    tool.scanner = SafeScanner()
    try:
        tool.run()
        return 'done', tool.stats
    except InterruptedError:
        return 'interrupted', tool.stats
    except Exception:
        return 'failed', tool.stats

def worker():
    while not stop.is_set():
        with connect() as c:
            row = c.execute("SELECT * FROM jobs WHERE state='queued' ORDER BY created LIMIT 1").fetchone()
            if row: c.execute("UPDATE jobs SET state='running',updated=? WHERE id=?", (time.time(), row['id']))
        if not row: wake.wait(3); wake.clear(); continue
        try: state, stats = process(row)
        except Exception: state, stats = 'failed', {}
        with connect() as c:
            c.execute('UPDATE jobs SET state=?,stats=?,updated=? WHERE id=?', (state, json.dumps(stats), time.time(), row['id']))
            c.execute("DELETE FROM jobs WHERE id IN (SELECT id FROM jobs WHERE state NOT IN ('queued','running') ORDER BY created DESC LIMIT -1 OFFSET 1000)")

class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args): pass
    def reply(self, status, data):
        value = json.dumps(data).encode()
        self.send_response(status); self.send_header('Content-Type', 'application/json'); self.send_header('Content-Length', str(len(value))); self.end_headers(); self.wfile.write(value)
    def authorized(self): return hmac.compare_digest(self.headers.get('X-Worker-Key', ''), KEY)
    def do_GET(self):
        if self.path == '/health': return self.reply(200, {'ok': True})
        if not self.authorized(): return self.reply(403, {'error': 'Forbidden'})
        if self.path != '/jobs': return self.reply(404, {})
        with connect() as c: rows = c.execute('SELECT * FROM jobs ORDER BY created DESC LIMIT 100').fetchall()
        self.reply(200, {'jobs': [job_view(row) for row in rows]})
    def do_POST(self):
        if not self.authorized(): return self.reply(403, {'error': 'Forbidden'})
        try:
            length = int(self.headers.get('Content-Length', '0'))
            if not 0 <= length <= 8192: raise ValueError()
            body = json.loads(self.rfile.read(length) or b'{}')
            if self.path == '/jobs': result = submit(body)
            elif self.path.startswith('/jobs/') and self.path.endswith('/retry'):
                ident = self.path.split('/')[2]
                with connect() as c: row = c.execute('SELECT * FROM jobs WHERE id=?', (ident,)).fetchone()
                if not row: return self.reply(404, {})
                result = submit({'path': row['path'], 'provider': row['provider'], 'embed': bool(row['embed'])})
            else: return self.reply(404, {})
            self.reply(200, result)
        except OverflowError: self.reply(429, {'error': 'Queue full'})
        except (ValueError, TypeError, OSError): self.reply(400, {'error': 'Invalid request'})

if __name__ == '__main__':
    thread = threading.Thread(target=worker, daemon=True); thread.start()
    server = HTTPServer(('0.0.0.0', 8765), Handler)
    server.timeout = 1
    def shutdown(*_): stop.set(); wake.set()
    signal.signal(signal.SIGTERM, shutdown); signal.signal(signal.SIGINT, shutdown)
    while not stop.is_set(): server.handle_request()
    server.server_close(); thread.join(timeout=25)
