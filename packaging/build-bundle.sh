#!/usr/bin/env bash
# Builds self-contained URSC bundles for THIS operating system (Linux or macOS):
#   dist/URSC/                  jpackage application image (native launcher + private Java runtime)
#   dist/URSC-<os>-portable/    plain folder: private runtime + jar + start scripts
#   dist/*.tar.gz + SHA256SUMS  archives to carry into the air-gapped facility
# Needs a JDK 17+ with jlink and jpackage, and Maven (only on the build machine; the
# target machine needs nothing installed). Run on each OS you deliver to: jpackage does
# not cross-build. Windows: use build-bundle.ps1.
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT=$(pwd)
DIST="$ROOT/dist"
VERSION=$(sed -n 's:.*<version>\([0-9][^<]*\)</version>.*:\1:p' pom.xml | head -1)
case "$(uname -s)" in
  Linux*) OS=linux ;;
  Darwin*) OS=macos ;;
  *) echo "unsupported OS; use build-bundle.ps1 on Windows" >&2; exit 1 ;;
esac
ARCH=$(uname -m)

if [[ "${SKIP_BUILD:-0}" != 1 ]]; then
  mvn -B -q -DskipTests package
fi
JAR=$(ls target/leo-trajectory-*.jar | grep -v -e sources -e javadoc | head -1)
MODULES=$(jdeps --print-module-deps --ignore-missing-deps "$JAR" 2>/dev/null | tail -1)
echo "modules: $MODULES"

rm -rf "$DIST" && mkdir -p "$DIST/input"
cp "$JAR" "$DIST/input/ursc.jar"
# the Orekit engine needs the JPL DE440 ephemeris; it is looked up next to the jar
mkdir -p "$DIST/input/data/ephemeris"
cp data/ephemeris/lnxp1990.440 "$DIST/input/data/ephemeris/"

# 1) minimal private Java runtime
jlink --add-modules "$MODULES" --strip-debug --no-header-files --no-man-pages \
      --compress=zip-6 --output "$DIST/runtime" 2>/dev/null \
  || jlink --add-modules "$MODULES" --strip-debug --no-header-files --no-man-pages \
      --compress=2 --output "$DIST/runtime"

# 2) native application image (launcher starts the GUI)
jpackage --type app-image --name URSC --app-version "$VERSION" \
  --input "$DIST/input" --main-jar ursc.jar --main-class org.ursc.trajectory.app.Cli \
  --runtime-image "$DIST/runtime" --arguments gui \
  --java-options "-Dfile.encoding=UTF-8" \
  --description "URSC LEO orbit determination and prediction (offline)" \
  --dest "$DIST"

# 3) portable folder (no native launcher, same runtime)
P="$DIST/URSC-$OS-$ARCH-portable"
mkdir -p "$P/lib"
cp -r "$DIST/runtime" "$P/runtime"
cp "$DIST/input/ursc.jar" "$P/lib/ursc.jar"
mkdir -p "$P/data/ephemeris"
cp data/ephemeris/lnxp1990.440 "$P/data/ephemeris/"
cat > "$P/ursc-gui.sh" <<'SH'
#!/usr/bin/env sh
# Starts the URSC GUI on this computer only (127.0.0.1) and opens the browser.
DIR=$(cd "$(dirname "$0")" && pwd)
exec "$DIR/runtime/bin/java" -Dfile.encoding=UTF-8 -jar "$DIR/lib/ursc.jar" gui "$@"
SH
cat > "$P/ursc.sh" <<'SH'
#!/usr/bin/env sh
# Command-line interface (all subcommands): ./ursc.sh help
DIR=$(cd "$(dirname "$0")" && pwd)
exec "$DIR/runtime/bin/java" -Dfile.encoding=UTF-8 -jar "$DIR/lib/ursc.jar" "$@"
SH
chmod +x "$P"/*.sh
cp packaging/README-OFFLINE.md "$P/README-OFFLINE.md"
cp packaging/README-OFFLINE.md "$DIST/URSC/README-OFFLINE.md" 2>/dev/null || true

# 4) archives + checksums for transfer
( cd "$DIST"
  if [[ "$OS" == macos ]]; then APPDIR=URSC.app; else APPDIR=URSC; fi
  tar -czf "URSC-$VERSION-$OS-$ARCH-app.tar.gz" "$APPDIR"
  tar -czf "URSC-$VERSION-$OS-$ARCH-portable.tar.gz" "URSC-$OS-$ARCH-portable"
  if command -v sha256sum >/dev/null; then sha256sum ./*.tar.gz > SHA256SUMS; else shasum -a 256 ./*.tar.gz > SHA256SUMS; fi
  rm -rf input runtime )
echo
echo "Bundles in $DIST:"
ls -la "$DIST"
