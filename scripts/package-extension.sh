#!/usr/bin/env bash
# Builds the Chrome Web Store upload zip pointed at your production backend.
# Usage: scripts/package-extension.sh https://api.example.com
set -euo pipefail
cd "$(dirname "$0")/.."
BACKEND="${1:?usage: $0 https://your-backend-domain}"
case "$BACKEND" in https://*) ;; *) echo "Backend must be an https:// URL for a public release" >&2; exit 1;; esac
BACKEND="${BACKEND%/}"
VERSION=$(node -p "require(\"./extension/manifest.json\").version")
BUILD="dist/build"
rm -rf dist && mkdir -p "$BUILD"
cp -r extension/. "$BUILD/"
rm -f "$BUILD/package.json"

# Point the extension at production, drop localhost and the broad optional host permissions.
BACKEND="$BACKEND" BUILD="$BUILD" node -e "
const fs=require(\"fs\");
const mp=process.env.BUILD+\"/manifest.json\";
const m=JSON.parse(fs.readFileSync(mp,\"utf8\"));
m.host_permissions=[process.env.BACKEND+\"/*\"];
delete m.optional_host_permissions;
fs.writeFileSync(mp,JSON.stringify(m,null,2));
const sp=process.env.BUILD+\"/shared/storage.js\";
let s=fs.readFileSync(sp,\"utf8\");
if(!s.includes(\"http://localhost:8080\")) throw new Error(\"default backend URL marker not found\");
fs.writeFileSync(sp,s.replace(\"http://localhost:8080\",process.env.BACKEND));
"
(cd "$BUILD" && zip -qr "../pricepulse-extension-v${VERSION}.zip" .)
echo "Created dist/pricepulse-extension-v${VERSION}.zip for $BACKEND"
