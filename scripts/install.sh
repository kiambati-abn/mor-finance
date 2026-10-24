#!/usr/bin/env bash
# Installs (or updates) the latest Morph release on the phone plugged in over USB.
# Usage: ./scripts/install.sh [owner/mor-finance]        (Mac / Linux)
# Needs: adb (Android platform-tools) and gh (GitHub CLI, logged in with `gh auth login`).
set -euo pipefail

repo="${1:-${MORPH_REPO:-}}"
if [ -z "$repo" ]; then
  url="$(git -C "$(dirname "$0")/.." config --get remote.origin.url 2>/dev/null || true)"
  repo="$(echo "$url" | sed -E 's#(git@github.com:|https://github.com/)##; s#\.git$##')"
fi
[ -n "$repo" ] || { echo "Tell me the repo: ./scripts/install.sh <owner>/mor-finance"; exit 1; }

command -v adb >/dev/null || { echo "adb not found. Install Android platform-tools: https://developer.android.com/tools/releases/platform-tools"; exit 1; }
command -v gh >/dev/null || { echo "gh not found. Install the GitHub CLI: https://cli.github.com/ then run: gh auth login"; exit 1; }

state="$(adb get-state 2>/dev/null || true)"
if [ "$state" != "device" ]; then
  echo "No phone found. Plug it in, unlock it, and tap 'Allow' on the USB debugging prompt. Then run this again."
  adb devices
  exit 1
fi

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
echo "Downloading the latest Morph from $repo…"
gh release download --repo "$repo" --pattern '*.apk' --dir "$tmp"
apk="$(ls "$tmp"/*.apk | head -n1)"
echo "Installing $(basename "$apk") (your data is kept)…"
adb install -r "$apk"
adb shell monkey -p com.morphlabs.morph -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || true
echo "Done 🦋"
