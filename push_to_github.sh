#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "Pushing GlassBeat Desktop (v1.1.7 / Build 20) to https://github.com/abzgif/mac-glassbeat ..."
git branch -M main
git remote remove origin 2>/dev/null || true
git remote add origin https://github.com/abzgif/mac-glassbeat.git
git push -u origin main --force
git tag -f v1.1.7
git push origin v1.1.7 --force
echo "Done! GitHub Actions is now building and releasing GlassBeat v1.1.7 for macOS, Windows, and Linux at https://github.com/abzgif/mac-glassbeat/actions"
