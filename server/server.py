#!/usr/bin/env python3
"""
Phone-Deck server — runs on your Linux PC (Bazzite).
Pure stdlib, no pip install needed. Just: python3 server.py

The phone opens http://<pc-ip>:8787/ and taps buttons.
Buttons can: launch a whitelisted app on the PC, or pop a desktop notification.
"""
import glob
import json
import re
import shlex
import socket
import subprocess
import os
import threading
import http.server
import socketserver

PORT = 8787
TOKEN = "change-me"  # must match the token in index.html

# UDP "where are you" broadcast so the Phone Mirror app can auto-fill the PC
# URL instead of you typing the IP. Reuses PORT — UDP and TCP are separate
# namespaces so this doesn't conflict with the HTTP server below.
DISCOVERY_MAGIC = b"PHONE_DECK_DISCOVER_V1"
DISCOVERY_REPLY_PREFIX = b"PHONE_DECK_V1:"


def discovery_responder():
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.bind(("0.0.0.0", PORT))
    while True:
        try:
            data, addr = sock.recvfrom(1024)
            if data.strip() == DISCOVERY_MAGIC:
                sock.sendto(DISCOVERY_REPLY_PREFIX + str(PORT).encode(), addr)
        except OSError:
            pass

# Whitelist of things the phone is ALLOWED to launch.
# Key = what the phone sends; value = the actual command list.
# This is deliberately a whitelist, not "run any command" — see notes.
ALLOWED = {
    "steam":    ["steam"],
    "firefox":  ["firefox"],
    "chrome":   ["google-chrome-stable"],
    "files":    ["xdg-open", os.path.expanduser("~")],
    "youtube":  ["xdg-open", "https://youtube.com"],
    "discord":  ["flatpak", "run", "com.discordapp.Discord"],
}

HERE = os.path.dirname(os.path.abspath(__file__))

# Where Linux desktops keep .desktop launcher files. Scanned on demand for
# GET /apps so the phone can show "everything installed" without you having
# to hand-maintain a list. Field-code placeholders (%f %u etc.) are stripped
# from Exec= since the phone never supplies a file/URL argument.
DESKTOP_DIRS = [
    os.path.expanduser("~/.local/share/applications"),
    "/usr/share/applications",
    "/var/lib/flatpak/exports/share/applications",
    os.path.expanduser("~/.local/share/flatpak/exports/share/applications"),
]

_FIELD_CODE_RE = re.compile(r"%[a-zA-Z%]")

# id -> argv, populated by scan_desktop_apps() and consulted by POST /launch.
# This is what keeps "/launch {id: ...}" a whitelist rather than "run anything":
# only apps that showed up in a real scan of installed .desktop files can run.
_app_cache = {}


def _parse_desktop_file(path):
    name = exec_line = icon = None
    no_display = hidden = False
    in_entry = False
    try:
        with open(path, encoding="utf-8", errors="ignore") as f:
            for raw in f:
                line = raw.strip()
                if line.startswith("["):
                    in_entry = (line == "[Desktop Entry]")
                    continue
                if not in_entry:
                    continue
                if line.startswith("Name=") and name is None:
                    name = line[len("Name="):].strip()
                elif line.startswith("Exec=") and exec_line is None:
                    exec_line = line[len("Exec="):].strip()
                elif line.startswith("Icon=") and icon is None:
                    icon = line[len("Icon="):].strip()
                elif line == "NoDisplay=true":
                    no_display = True
                elif line == "Hidden=true":
                    hidden = True
    except OSError:
        return None

    if not name or not exec_line or no_display or hidden:
        return None

    cleaned = _FIELD_CODE_RE.sub("", exec_line).strip()
    try:
        argv = shlex.split(cleaned)
    except ValueError:
        return None
    if not argv:
        return None

    return {"name": name, "exec": argv, "icon": icon or ""}


def scan_desktop_apps():
    """Re-scan .desktop files, refresh _app_cache, return [{id, name, icon}]."""
    apps = []
    seen = set()
    for d in DESKTOP_DIRS:
        for path in sorted(glob.glob(os.path.join(d, "*.desktop"))):
            app_id = os.path.splitext(os.path.basename(path))[0]
            if app_id in seen:
                continue
            entry = _parse_desktop_file(path)
            if not entry:
                continue
            seen.add(app_id)
            _app_cache[app_id] = entry["exec"]
            apps.append({"id": app_id, "name": entry["name"], "icon": entry["icon"]})
    apps.sort(key=lambda a: a["name"].lower())
    return apps


def notify(title, body):
    # notify-send is the simplest. If it's missing on your immutable
    # install, swap for the gdbus fallback in the notes.
    subprocess.Popen(["notify-send", title, body])


class Handler(http.server.BaseHTTPRequestHandler):
    def log_message(self, *a):  # quieter console
        pass

    def _json(self, code, obj):
        data = json.dumps(obj).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        self._json(404, {"error": "not found"})

    def do_POST(self):
        if self.headers.get("X-Token") != TOKEN:
            return self._json(401, {"error": "bad token"})
        length = int(self.headers.get("Content-Length", 0))
        try:
            body = json.loads(self.rfile.read(length) or "{}")
        except json.JSONDecodeError:
            return self._json(400, {"error": "bad json"})

        if self.path == "/notify":
            notify(body.get("title", "Phone"), body.get("body", ""))
            return self._json(200, {"ok": True})

        if self.path == "/mirror":
            # Mirrored phone notification: {app, title, body} from Phone Mirror.
            app = body.get("app", "Phone")
            title = body.get("title", "")
            text = body.get("body", "")
            notify(f"{app}: {title}" if title else app, text)
            return self._json(200, {"ok": True})

        if self.path == "/launch":
            # Launch a previously-scanned .desktop app by id (see GET /apps —
            # only ids that came out of a real scan are in _app_cache, so this
            # stays "launch something installed", not "run any command").
            app_id = body.get("id")
            if app_id:
                cmd = _app_cache.get(app_id)
                if not cmd:
                    # Cache may be empty after a restart; do one fresh scan.
                    scan_desktop_apps()
                    cmd = _app_cache.get(app_id)
                if cmd:
                    subprocess.Popen(cmd)
                    return self._json(200, {"ok": True})
                return self._json(400, {"error": f"unknown app id '{app_id}'"})

            # Open a URL on the PC with the default browser.
            url = body.get("url")
            if url:
                if not (url.startswith("http://") or url.startswith("https://")):
                    return self._json(400, {"error": "url must start with http:// or https://"})
                subprocess.Popen(["xdg-open", url])
                return self._json(200, {"ok": True})

            # Legacy hand-written whitelist (see ALLOWED above).
            key = body.get("app")
            cmd = ALLOWED.get(key)
            if cmd:
                subprocess.Popen(cmd)
                return self._json(200, {"ok": True})
            return self._json(400, {"error": f"'{key}' not in whitelist"})

        self._json(404, {"error": "unknown endpoint"})


if __name__ == "__main__":
    threading.Thread(target=discovery_responder, daemon=True).start()
    apps = scan_desktop_apps()
    print(f"Scanned {len(apps)} launchable apps from .desktop files")

    socketserver.ThreadingTCPServer.allow_reuse_address = True
    with socketserver.ThreadingTCPServer(("0.0.0.0", PORT), Handler) as httpd:
        print(f"Phone-Deck server running on http://0.0.0.0:{PORT}")
        print("Open that address (with your PC's IP) on your phone.")
        print("UDP auto-discovery responder running — Phone Mirror's 'Find PC' button will locate this server.")
        try:
            httpd.serve_forever()
        except KeyboardInterrupt:
            print("\nbye")
