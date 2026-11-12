# URSC — offline installation and use

URSC runs entirely on one computer. It needs **no internet connection and no Java
installation**: the bundle carries its own minimal Java runtime, and the user interface is a
local web page served only to this computer (address `127.0.0.1`). Nothing is downloaded at
run time; maps, the 3D globe, fonts, gravity field, help and search are all inside the bundle.

## What you receive

| File | Contents |
|---|---|
| `URSC-<ver>-<os>-<arch>-app.*` | Native application (`URSC`, `URSC.exe` or `URSC.app`) with its runtime |
| `URSC-<ver>-<os>-<arch>-portable.*` | Plain folder: `runtime/`, `lib/ursc.jar`, start scripts. Use when native launchers are not permitted |
| `SHA256SUMS` / `SHA256SUMS.txt` | Checksums of the archives |

Bundles are built per operating system (Linux, Windows, macOS); take the one for the target.

## Bringing it into the facility

1. On the connected side, verify the archive: `sha256sum -c SHA256SUMS` (Linux),
   `shasum -a 256 -c SHA256SUMS` (macOS), or compare `Get-FileHash <zip>` (Windows).
2. Transfer the archive through your approved media process.
3. Verify the checksum again on the offline machine, then unpack anywhere the user can write
   to (no administrator rights needed).

## Starting

- **Application bundle**: run `URSC/bin/URSC` (Linux), `URSC\URSC.exe` (Windows; the console
  window shows the address and closing it stops URSC) or open `URSC.app` (macOS).
- **Portable**: `./ursc-gui.sh` (Linux/macOS) or `ursc-gui.cmd` (Windows).

The default browser opens on `http://127.0.0.1:<port>/#t=<token>`. The token is a per-start
secret; only that link works. If no browser opens (for example, a locked-down desktop), copy
the address printed in the console into any installed browser.

Options (portable scripts pass them through; with the native launcher, repeat the command
name first, e.g. `URSC gui --port 8765`):

| Option | Meaning |
|---|---|
| `--port N` | Fixed port instead of a random free one |
| `--workspace DIR` | Where catalog, runs, presets and stations are stored (default `~/URSC-workspace`) |
| `--no-browser` | Do not open a browser |

Stop with the **⏻ Quit** button, Ctrl+C in the console, or by closing the console window.

## Security properties

- Listens on the loopback interface only; other computers cannot connect.
- Every API call needs the per-start token; requests with a foreign `Host` header are refused.
- The page loads scripts, styles and data only from itself (Content-Security-Policy
  `default-src 'self'`).
- Help search is local. Optional AI answer providers (local Ollama, cloud keys) are disabled
  unless configured; nothing is sent anywhere by default.

## Getting data in

Copy files from the approved media into a folder, then in **Data** either *Add* it once or
*Watch* it: watched folders are indexed automatically when new files arrive and you are
notified (runs are never started automatically). Accepted: GNSSRD RINEX observations, IGS
SP3/CLK (Final, Rapid, Ultra-rapid; `.gz`, `.Z` or plain), RINEX broadcast navigation, POEORB
reference orbits, PROQUA attitude, finals2000A EOP, CSSI space weather, ICGEM gravity. The JPL
DE440 ephemeris needed by the Orekit engine (Sun and Moon) is included in the bundle
(`data/ephemeris/lnxp1990.440` next to the program); Orekit itself is inside the jar.
Only the observations, one GPS product and the UTC cutoff are required.

To try the software without real data: **Data → Create demo dataset**.

## Updating

Unpack the new version next to the old one. The workspace (default `~/URSC-workspace`) is
separate from the program, so catalog, run history, presets and stations carry over.

## Troubleshooting

- **The browser says "No internet" / `ERR_INTERNET_DISCONNECTED` for `127.0.0.1`.** Chromium-based
  browsers (Chrome, Edge) can refuse every page, including local ones, when the computer has no
  network adapter other than loopback. This was reproduced in testing with Chromium on Linux;
  it worked as soon as any (even unconnected) adapter existed. Fixes: use Firefox, or enable any
  network adapter, or on Windows add the "Microsoft KM-TEST Loopback Adapter". URSC itself is
  unaffected and still needs no internet.
- **Port already in use.** Start with `--port` and another number, or omit it for a random port.
- **Nothing happens on start (Windows).** Use the console window, or the portable
  `ursc-gui.cmd`, to see the address and any error message.
