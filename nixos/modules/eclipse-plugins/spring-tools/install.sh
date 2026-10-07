#!/bin/sh
# install.sh --- Installs Spring Tools 5 into a scratch copy of eclipse-rcp
# via the real Eclipse p2 director, then extracts only the newly added
# plugins/features as a dropin. Runs inside a Nix fixed-output derivation
# (see ../../eclipse.nix), so the network access p2 needs is sanctioned the
# same way fetchurl's is: the result is verified against a pinned hash.
# License: GPL-3.0-or-later
set -eu

ECLIPSE_RCP="$1"   # pkgs.eclipses.eclipse-rcp store path
JDK="$2"            # pkgs.jdk25 store path
OUT="$3"            # $out

SCRATCH="$TMPDIR/eclipse-scratch"
export HOME="$TMPDIR/home"
mkdir -p "$HOME"

# Without these, GTK3 hangs indefinitely (not a clean crash) trying to reach
# a D-Bus session bus / AT-SPI accessibility bridge that doesn't exist in the
# build sandbox -- the standard headless-GTK mitigation.
export NO_AT_BRIDGE=1
export DBUS_SESSION_BUS_ADDRESS=/dev/null
export GTK_A11Y=none

echo "==> Copying eclipse-rcp into a writable scratch profile..."
cp -a "$ECLIPSE_RCP/eclipse" "$SCRATCH"
chmod -R u+w "$SCRATCH"

# NOT the bundled justj JRE (org.eclipse.justj.openjdk.hotspot.jre.full.*):
# that's a generic upstream Linux binary that only runs on NixOS via nix-ld,
# a live-system compatibility shim that isn't present inside this pure build
# sandbox ("No such file or directory" despite the file existing -- the
# classic missing-dynamic-interpreter symptom). pkgs.jdk25 is a proper
# NixOS-patched build and needs no such shim.
JRE="$JDK/bin/java"

echo "==> Snapshotting base plugins/features..."
find "$SCRATCH/plugins" -maxdepth 1 \( -name '*.jar' -o -type d \) -printf '%f\n' | sort > "$TMPDIR/plugins-before.txt"
find "$SCRATCH/features" -maxdepth 1 -type d -printf '%f\n' | sort > "$TMPDIR/features-before.txt"

# The Spring-specific feature groups from the STS5 e4.41 update site (skips
# the cosmetic standalone-product branding feature, and anything from the
# platform/jdt/pde/egit/buildship/m2e/wildwebdeveloper/docker features also
# present in that composite repo -- eclipse-rcp already ships those).
IUS="org.springframework.boot.ide.main.feature.feature.group,\
org.springframework.ide.eclipse.boot.dash.feature.feature.group,\
org.springframework.ide.eclipse.xml.namespaces.feature.feature.group,\
org.springframework.tooling.boot.ls.feature.feature.group,\
org.springframework.tooling.bosh.ls.feature.feature.group,\
org.springframework.tooling.cloudfoundry.manifest.ls.feature.feature.group,\
org.springframework.tooling.concourse.ls.feature.feature.group"

# Source the package's own bin/eclipse wrapper for its env setup (GTK3/
# webkitgtk/libsecret/etc on LD_LIBRARY_PATH) WITHOUT letting it exec the
# native eclipse binary -- that native launcher (and the GTK-linked
# launcher library it loads to show a splash/probe the display) is what
# hung indefinitely pre-JVM in earlier attempts, even once -nosplash,
# Xvfb, fontconfig, and CA certs were all in place. Invoking the Equinox
# launcher JAR directly via plain `java -jar` skips eclipse.ini's forced
# `-product` (which pulled in the full RCP/workbench), the native launcher
# binary, and its GTK-linked library entirely -- p2.director is a non-UI
# application and doesn't need any of that.
ENV_SETUP="$TMPDIR/eclipse-env-setup.sh"
sed '$d' "$ECLIPSE_RCP/bin/eclipse" > "$ENV_SETUP"
# shellcheck disable=SC1090
. "$ENV_SETUP"

LAUNCHER_JAR="$(find "$SCRATCH/plugins" -maxdepth 1 -name 'org.eclipse.equinox.launcher_*.jar' | head -1)"

echo "==> Running p2 director (installIU: $IUS)..."
timeout 600 "$JRE" -jar "$LAUNCHER_JAR" \
  -nosplash \
  -application org.eclipse.equinox.p2.director \
  -repository https://cdn.spring.io/spring-tools/release/update/5.4.0.RELEASE/e4.41,https://download.eclipse.org/releases/2026-09 \
  -installIU "$IUS" \
  -destination "$SCRATCH" \
  -profile epp.package.rcp.profile \
  -profileProperties org.eclipse.update.install.features=true \
  -p2.os linux -p2.ws gtk -p2.arch x86_64

echo "==> Diffing against the base install to find what p2 actually added..."
find "$SCRATCH/plugins" -maxdepth 1 \( -name '*.jar' -o -type d \) -printf '%f\n' | sort > "$TMPDIR/plugins-after.txt"
find "$SCRATCH/features" -maxdepth 1 -type d -printf '%f\n' | sort > "$TMPDIR/features-after.txt"
comm -13 "$TMPDIR/plugins-before.txt" "$TMPDIR/plugins-after.txt" > "$TMPDIR/new-plugins.txt"
comm -13 "$TMPDIR/features-before.txt" "$TMPDIR/features-after.txt" > "$TMPDIR/new-features.txt"

NEW_PLUGIN_COUNT=$(wc -l < "$TMPDIR/new-plugins.txt")
NEW_FEATURE_COUNT=$(wc -l < "$TMPDIR/new-features.txt")
echo "==> $NEW_PLUGIN_COUNT new plugin(s), $NEW_FEATURE_COUNT new feature(s)."
[ "$NEW_PLUGIN_COUNT" -gt 0 ] || { echo "No new plugins found -- p2 resolution likely failed silently." >&2; exit 1; }

DROPIN="$OUT/eclipse/dropins/spring-tools"
mkdir -p "$DROPIN/plugins" "$DROPIN/features"

while IFS= read -r name; do
  cp -a "$SCRATCH/plugins/$name" "$DROPIN/plugins/$name"
done < "$TMPDIR/new-plugins.txt"

while IFS= read -r name; do
  cp -a "$SCRATCH/features/$name" "$DROPIN/features/$name"
done < "$TMPDIR/new-features.txt"

echo "==> Done: $DROPIN"
