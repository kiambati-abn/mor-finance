#!/usr/bin/env bash
# Downloads the latest Morph APK into ~/MAX, ready to send to someone.
# Usage: bash scripts/get-apk.sh   (or: bash ~/MAX/mor-finance/scripts/get-apk.sh)
set -euo pipefail
REPO="${1:-kiambati-abn/mor-finance}"
gh release download --repo "$REPO" --pattern '*.apk' --dir "$HOME/MAX" --clobber
ls -t "$HOME"/MAX/morph-*.apk | head -n1 | sed 's/^/Ready to send: /'
