#!/usr/bin/env bash
# Packages the rs_di_compat/ folder into a standalone datapack zip.
#
# Output: rs_di_compat/build/rs_di_compat-<version>.zip
#
# This datapack is the sole delivery vehicle for Repurposed Structures ×
# Domestication Innovation petshop compat. The fork's main CTOV jar does
# NOT include any RS-specific NBT files — RS integration is intentionally
# isolated to this datapack so the jar can be built and used without any
# RS-side assets being present at build time.
#
# The zip is buildable with or without the petshop .nbt files in place.
# Without the NBTs, the datapack still parses cleanly but no petshop
# buildings will generate in RS villages (the pool_additions JSONs
# reference NBTs that aren't there — RS will simply never roll that
# pool entry). With the NBTs in place (the default — starter NBTs are
# committed under rs_di_compat/data/ctov/structures/villages/<biome>/,
# copied from the fork's own CTOV petshop NBTs with biome-appropriate
# mapping), everything works out of the box.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC_DIR="$REPO_ROOT/rs_di_compat"
BUILD_DIR="$SRC_DIR/build"

# Read mod_version from gradle.properties, fall back to a timestamp.
VERSION="$(awk -F= '/^mod_version=/ {print $2; exit}' "$REPO_ROOT/gradle.properties" | tr -d ' \r')"
if [[ -z "$VERSION" ]]; then
    VERSION="$(date +%Y%m%d%H%M%S)"
fi

OUT_ZIP="$BUILD_DIR/rs_di_compat-$VERSION.zip"

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"

# Zip the datapack contents from inside SRC_DIR so paths in the zip start at `data/` and `pack.mcmeta`.
# We exclude the build/ subdir and the README.md (the README is for repo readers, not for the in-game datapack).
( cd "$SRC_DIR" && find . \
    -type f \
    \! -path "./build/*" \
    \! -path "./README.md" \
    -print0 | sort -z | xargs -0 zip -q -X "$OUT_ZIP" )

echo "Built: $OUT_ZIP"
# Summary output. Use unzip if available; fall back to a plain find-based list
# so the script still succeeds on minimal environments (e.g. slim Docker images).
if command -v unzip >/dev/null 2>&1; then
    unzip -l "$OUT_ZIP" | tail -n +4 | head -n -2 | awk '{print $4}' | sort
    echo "---"
    echo "Total entries: $(unzip -l "$OUT_ZIP" | tail -1 | awk '{print $2}')"
else
    echo "(unzip not installed — skipping contents listing)"
fi
