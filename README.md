# Phone Mirror

A native Android app with two jobs:

1. **Mirror** — captures your phone's notifications via
   `NotificationListenerService` and POSTs each one as JSON to your phone-deck
   PC server's `POST /mirror` endpoint, replacing a MacroDroid rule.
2. **Deck launcher** — a Steam-Deck-style home screen of dark tiles that launch
   apps or open web links on that same PC, fed by the server's live scan of
   your `.desktop` files. This replaces opening `phone-deck`'s web page in a
   browser — it's now the app's home screen.

- minSdk 26, targetSdk/compileSdk 34
- Kotlin, single Gradle module (`app`)
- No third-party HTTP dependency — uses `HttpURLConnection`

## What it does

### Home screen — `LauncherActivity` (the deck)
A dark grid of tiles, each either a PC app or a web link. On open it checks the
saved PC URL is reachable; if not, it broadcasts a "find PC" UDP request and
auto-saves whatever answers; if nothing answers, it pops a dialog pointing you
at Settings. Tap a tile to launch it on the PC (`POST /launch`); long-press to
remove it. The **+** button opens **`AddTileActivity`**, which fetches the
server's live `.desktop` scan (`GET /apps`, filterable by name) so you can pick
any installed app, or add a custom web link (label + `http(s)://` URL). The
gear button opens **Settings** (`MainActivity`).

### Settings — `MainActivity`
Enter your PC's base URL (e.g. `http://100.x.x.x:8787`) and the `X-Token`
value, Save (stored in `SharedPreferences`); **Find PC on network** to
auto-discover it by UDP broadcast instead of typing the IP; **Grant
notification access**; **Ignore battery optimizations**; **Send test** to POST
a sample notification and see the result inline.

### Notification mirror — `MirrorService`
A `NotificationListenerService`: on every new notification, resolves the app's
display name via `PackageManager`, reads `android.title`/`android.text` from
the notification extras, skips ongoing/foreground-service notifications and the
app's own notifications, and POSTs `{"app", "title", "body"}` to `{url}/mirror`
with the `X-Token` header on a background coroutine. Empty title+body is
skipped. All failures are logged (logcat tags `MirrorClient`/`MirrorService`/
`DeckClient`/`PcDiscovery`) — nothing here ever crashes the app or the service.

### Auto-discovery — `PcDiscovery` (app side) / `discovery_responder` (server side)
The app sends a UDP broadcast `PHONE_DECK_DISCOVER_V1` to the LAN on port 8787;
the server listens on the same port over UDP (separate namespace from its TCP
HTTP server) and replies `PHONE_DECK_V1:<port>` from its real IP. The app reads
the sender address out of the reply packet — no manual IP typing needed, as
long as the phone and PC share a broadcast domain (same Wi-Fi/LAN; this won't
cross a Tailscale link, which is point-to-point, not broadcast).

---

## Server-side: phone-deck additions

The original `phone-deck.zip` server only had `/notify` and `/launch` (a
hand-written app whitelist). To support Mirror + the Deck launcher, a working
copy lives at `~/program/phone-deck/server.py` with these additions — still
pure stdlib, still `python3 server.py`:

- **`POST /mirror`** `{app, title, body}` → pops a desktop notification
  `"<app>: <title>"` / body. This is what `MirrorService` posts to.
- **`GET /apps`** (requires `X-Token`) → scans `~/.local/share/applications`,
  `/usr/share/applications`, and the Flatpak export dirs for `.desktop` files,
  parses `Name=`/`Exec=`/`Icon=` (skipping `NoDisplay=true`/`Hidden=true`,
  stripping field-code placeholders like `%f`/`%U` from `Exec=`), and returns
  `{"apps": [{"id", "name", "icon"}, ...]}`. Re-scans fresh on every call —
  install something new and it shows up next time you open **Add tile**.
- **`POST /launch`** extended to accept:
  - `{"id": "<scanned-app-id>"}` — looks the id up in the cache built by the
    last `/apps` scan (re-scanning once if the cache is empty, e.g. after a
    restart) and runs its `Exec=` argv. This keeps it a *whitelist of things
    that really are installed*, not "run any command".
  - `{"url": "https://…"}` — runs `xdg-open <url>` on the PC. Rejects anything
    that isn't `http://`/`https://`.
  - `{"app": "<key>"}` — the original hand-written `ALLOWED` whitelist, kept
    for backwards compatibility.
- **UDP discovery responder** — a daemon thread bound to UDP `0.0.0.0:8787`
  (alongside the TCP HTTP server on the same port number — different
  protocols, no conflict) that answers `PHONE_DECK_DISCOVER_V1` broadcasts with
  `PHONE_DECK_V1:<port>`.

### Running it automatically (systemd user service)

So you don't have to remember to start it, it runs as a `systemd --user` service
that starts at login (and even before login, since lingering is enabled —
`loginctl show-user $USER -p Linger` shows `Linger=yes`) and restarts on crash:

```
~/.config/systemd/user/phone-deck.service
```

```ini
[Unit]
Description=Phone Deck server (notification mirror + app launcher backend for Phone Mirror)
After=network-online.target

[Service]
ExecStart=/usr/bin/python3 /home/cj/program/phone-deck/server.py
WorkingDirectory=/home/cj/program/phone-deck
Restart=always
RestartSec=3
Environment=HOME=/home/cj

[Install]
WantedBy=default.target
```

```bash
# one-time setup (already done):
systemctl --user daemon-reload
systemctl --user enable --now phone-deck.service

# day-to-day:
systemctl --user status  phone-deck.service     # check it's running
systemctl --user restart phone-deck.service     # e.g. after editing server.py
journalctl --user -u phone-deck.service -f      # tail its logs
```

Manual `cd ~/program/phone-deck && python3 server.py` still works fine for
quick testing — just `systemctl --user stop phone-deck.service` first so two
copies don't fight over port 8787.

---

## 1. Toolchain setup (inside a distrobox container — nothing installed on the host)

Bazzite is immutable, so the entire Android build toolchain (JDK, SDK,
build-tools) lives inside an Ubuntu 24.04 distrobox container. The host only
needs `distrobox`/`podman` (already present on Bazzite) and never sees a JDK or
Android SDK.

```bash
# 1. Create and enter an Ubuntu 24.04 box (skip create if you already have one)
distrobox create --name ubuntu --image docker.io/library/ubuntu:24.04
distrobox enter ubuntu

# 2. Install OpenJDK 17 + helpers
sudo apt-get update
sudo apt-get install -y openjdk-17-jdk-headless wget unzip

# 3. Download the Android commandline-tools and lay them out as the SDK expects
mkdir -p ~/android-sdk/cmdline-tools
cd /tmp
wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O commandlinetools.zip
unzip -q -o commandlinetools.zip -d ~/android-sdk/cmdline-tools
mv ~/android-sdk/cmdline-tools/cmdline-tools ~/android-sdk/cmdline-tools/latest

# 4. Put the SDK tools on PATH (also append this block to ~/.bashrc so it persists)
export ANDROID_SDK_ROOT=$HOME/android-sdk
export PATH=$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH

# 5. Accept all SDK licenses non-interactively, then install the pieces we need
yes | sdkmanager --licenses --sdk_root=$ANDROID_SDK_ROOT
sdkmanager --sdk_root=$ANDROID_SDK_ROOT \
  'platform-tools' 'platforms;android-34' 'build-tools;34.0.0'
```

That's the entire toolchain — everything lives under `~/android-sdk` inside the
container's home directory, isolated from the Bazzite host.

## 2. Build

The project ships its own Gradle wrapper (`./gradlew`), so you never need a
system-wide Gradle install — the wrapper downloads (and caches) the exact
Gradle version the project needs on first run.

```bash
distrobox enter ubuntu
cd ~/program/phone-mirror

# Point Gradle at the SDK we just installed
echo "sdk.dir=$ANDROID_SDK_ROOT" > local.properties

./gradlew assembleDebug
```

Output APK:

```
app/build/outputs/apk/debug/app-debug.apk
```

Because distrobox containers share the host's home directory, you can copy it
straight to your Downloads folder from inside (or outside) the container:

```bash
cp app/build/outputs/apk/debug/app-debug.apk ~/Downloads/phone-mirror-debug.apk
```

A copy is already at `~/Downloads/phone-mirror-debug.apk`.

## 3. Install on your phone

This is a personal sideload — debug-signed, no Play Store, no release keystore.

### Option A — adb over USB

```bash
distrobox enter ubuntu
# Enable Developer Options + USB debugging on the phone first, then plug it in
adb devices                 # confirm the phone shows up (accept the RSA prompt on-phone)
adb install -r ~/program/phone-mirror/app/build/outputs/apk/debug/app-debug.apk
```

### Option B — file transfer + tap-to-install

1. On the phone: Settings → Apps → (the file manager / browser you'll use to
   open the APK) → "Install unknown apps" → allow for that app.
2. Copy `~/Downloads/phone-mirror-debug.apk` to the phone (USB file transfer,
   a cloud drive, `scp`, etc.).
3. Open it on the phone with a file manager and tap "Install".

## 4. One-time setup on the phone (manual — cannot be automated)

The app opens straight to the **Deck launcher** (the dark tile grid). Tap the
**gear icon** (top right) to reach Settings, where you do the one-time setup:

1. Either tap **Find PC on network** (auto-discovers the server by UDP
   broadcast — phone and PC must be on the same Wi-Fi/LAN) or type the PC's
   base URL by hand (e.g. `http://<LAN-ip>:8787` — no trailing `/mirror`), plus
   the token that matches the server's `X-Token` check, then tap **Save**.
2. Tap **Grant notification access** → find "Phone Mirror" in the list → toggle
   it on → confirm. **This step is a manual Android security toggle and cannot
   be scripted or automated from the app itself.**
3. Tap **Ignore battery optimizations** and allow it when prompted.
4. Tap **Send test** — you should see "Status: test sent OK" and a desktop
   notification should pop on the PC.
5. Back out to the launcher (system back / gear again) — it should now say
   "Connected" up top. Tap **+** to build your deck: pick apps from the live
   `.desktop` scan or add custom web links. Tap a tile to launch it on the PC;
   long-press to remove it.

After setup, real notifications mirror to the PC automatically within ~1–2s of
arriving (handled entirely by the system-bound `MirrorService`, no foreground
app needed) — and the launcher is there whenever you open the app.

## Notes

- **Cleartext HTTP**: the manifest sets `android:usesCleartextTraffic="true"`
  because the PC server speaks plain `http://`, not `https://`. This is fine
  for a personal app talking to your own LAN/Tailscale server, but means
  traffic isn't encrypted — don't point this at anything over the open
  internet.
- **Why NotificationListenerService survives**: Android re-binds
  `NotificationListenerService` implementations at the system level (it's not
  just "a background app the OS can kill") — that's why it keeps working even
  after the app's UI process is gone. The "Ignore battery optimizations" step
  just removes one more thing that could delay the POST.
- **Cannot be automated** (call these out explicitly — don't attempt to script
  them):
  - Granting Notification Access on the phone (Android requires an explicit,
    manual toggle in Settings for security reasons).
  - Installing the APK on the physical phone (requires either a USB connection
    + manual `adb` confirmation prompt on-device, or manually opening and
    tapping "Install" on the file).
