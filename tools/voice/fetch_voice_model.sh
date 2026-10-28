#!/usr/bin/env bash
# Downloads the neural cue voice (Kokoro-82M v0.19, int8, for sherpa-onnx) into tools/voice/models/ (gitignored),
# checks it against the pinned SHA-256 and unpacks it. Licences: Kokoro weights Apache-2.0, espeak-ng-data GPL-3.0.
# Then install it on the phone with push_voice_model.sh (docs/SETUP.md §11).
set -euo pipefail

MODEL=kokoro-int8-en-v0_19
URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/$MODEL.tar.bz2"
SHA256=c9f0dd393615805b0bab050c340834d5e684e732aec91c0e860cd30e982c08bd
DIR="$(cd "$(dirname "$0")" && pwd)/models"
ARCHIVE="$DIR/$MODEL.tar.bz2"

digest() {
  if command -v sha256sum >/dev/null; then sha256sum "$1"; else shasum -a 256 "$1"; fi | cut -d' ' -f1
}

mkdir -p "$DIR"
if [ ! -f "$ARCHIVE" ] || [ "$(digest "$ARCHIVE")" != "$SHA256" ]; then
  echo "Downloading $URL (103 MB)"
  curl -fL --retry 3 -o "$ARCHIVE.part" "$URL"
  actual="$(digest "$ARCHIVE.part")"
  if [ "$actual" != "$SHA256" ]; then
    rm -f "$ARCHIVE.part"
    echo "Digest mismatch: expected $SHA256, got $actual" >&2
    exit 1
  fi
  mv "$ARCHIVE.part" "$ARCHIVE"
fi

rm -rf "${DIR:?}/$MODEL"
tar xjf "$ARCHIVE" -C "$DIR"
echo "Ready: $DIR/$MODEL ($(du -sh "$DIR/$MODEL" | cut -f1))"
