#!/usr/bin/env bash
# Downloads the latest Morph APK into ~/Downloads, ready to send to someone.
# Usage: bash scripts/get-apk.sh   (or: bash ~/MAX/mor-finance/scripts/get-apk.sh)
set -euo pipefail
REPO="${1:-kiambati-abn/mor-finance}"
gh release download --repo "$REPO" --pattern '*.apk' --dir "$HOME/Downloads" --clobber
ls -t "$HOME"/Downloads/morph-*.apk | head -n1 | sed 's/^/Ready to send: /'
