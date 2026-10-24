#!/usr/bin/env bash
# Morph one-step updater.
# Finds the newest mor-finance*.bundle in ~/MAX (or ~/Downloads), loads it, pushes to GitHub,
# waits for the build, then installs the new app on the phone plugged in over USB.
# Usage:  bash ~/MAX/morph-update.sh
set -euo pipefail

REPO="kiambati-abn/mor-finance"
CODE="$HOME/MAX/mor-finance"
say() { printf '\n\033[1;35m🦋 %s\033[0m\n' "$*"; }
fail() { printf '\n\033[1;31m✗ %s\033[0m\n' "$*"; exit 1; }

say "1/5 Looking for the newest Morph update file…"
echo "Looking in: ~/MAX (and ~/Downloads, just in case)."
bundle="$(ls -t "$HOME"/MAX/mor-finance*.bundle "$HOME"/Downloads/mor-finance*.bundle 2>/dev/null | head -n1 || true)"
if [ -z "$bundle" ]; then
  echo
  echo "I couldn't find the update file (mor-finance-latest.bundle)."
  echo "  1. In the chat with Claude, tap the file mor-finance-latest.bundle to download it."
  echo "  2. Save it in your MAX folder:  $HOME/MAX"
  echo
  read -r -p "Press Enter once it's downloaded (or type its full path): " typed
  if [ -n "$typed" ]; then bundle="$typed"; else
    bundle="$(ls -t "$HOME"/MAX/mor-finance*.bundle "$HOME"/Downloads/mor-finance*.bundle 2>/dev/null | head -n1 || true)"
  fi
fi
[ -n "$bundle" ] && [ -f "$bundle" ] || fail "Still no update file. Download mor-finance-latest.bundle into $HOME/MAX, then run this again."
echo "Using: $bundle (saved $(date -r "$bundle" '+%d %b %H:%M'))"
read -r -p "Is this the file Claude just sent? [Y/n] " ok
case "$ok" in [nN]*) fail "Download the newest mor-finance-latest.bundle into $HOME/MAX (replace the old one), then run this again." ;; esac
[ -d "$CODE/.git" ] || fail "Can't find your Morph code at $CODE."

say "2/5 Loading the changes…"
cd "$CODE"
git checkout -q main 2>/dev/null || git checkout -q -b main
git pull --no-edit "$bundle" main

say "3/5 Sending to GitHub…"
git push -u github main

say "4/5 Waiting for GitHub to build the app (about 5–8 minutes)…"
sleep 15
run_id="$(gh run list --repo "$REPO" --workflow build.yml --limit 1 --json databaseId --jq '.[0].databaseId')"
if ! gh run watch "$run_id" --repo "$REPO" --exit-status; then
  echo
  gh run view "$run_id" --repo "$REPO" --log-failed | tail -40 || true
  fail "The build failed. Copy the lines above and send them to Claude."
fi

say "5/5 Installing on your phone…"
if [ "$(adb get-state 2>/dev/null || true)" != "device" ]; then
  echo "Plug in your phone, unlock it and tap 'Allow'. Waiting…"
  adb wait-for-device
fi
"$CODE/scripts/install.sh" "$REPO"
# Tidy up: the update is now in your code and on GitHub, so the downloaded file isn't needed.
rm -f "$HOME"/MAX/mor-finance*.bundle "$HOME"/Downloads/mor-finance*.bundle
cp "$CODE/scripts/morph-update.sh" "$HOME/MAX/morph-update.sh" 2>/dev/null || true
say "All done. Morph is updated and your data is kept."
echo "Next time: save mor-finance-latest.bundle into ~/MAX and run:  bash ~/MAX/morph-update.sh"
