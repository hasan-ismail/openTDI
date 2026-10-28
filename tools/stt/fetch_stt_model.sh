#!/usr/bin/env bash
# Downloads the offline transcription model for sherpa-onnx into tools/stt/models/ (gitignored), checks each download
# against its pinned SHA-256 and unpacks it: NVIDIA Parakeet TDT-CTC 110M int8 (CC-BY-4.0), or with "moonshine" the
# smaller fallback Moonshine tiny English (MIT), plus the Silero VAD (MIT) that both need.
# Then install it on the phone with push_stt_model.sh (docs/SETUP.md §13).
# Usage: tools/stt/fetch_stt_model.sh [parakeet|moonshine]
set -euo pipefail

RELEASE=https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models
case "${1:-parakeet}" in
  parakeet)
    MODEL=sherpa-onnx-nemo-parakeet_tdt_ctc_110m-en-36000-int8
    SHA256=17f945007b52ccd8b7200ffc7c5652e9e8e961dfdf479cefcabd06cf5703630b
    SIZE="104 MB" ;;
  moonshine)
    MODEL=sherpa-onnx-moonshine-tiny-en-quantized-2026-02-27
    SHA256=9ec31b342d8fa3240c3b81b8f82e1cf7e3ac467c93ca5a999b741d5887164f8d
    SIZE="30 MB" ;;
  *) echo "usage: fetch_stt_model.sh [parakeet|moonshine]" >&2; exit 2 ;;
esac
VAD_SHA256=9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6
DIR="$(cd "$(dirname "$0")" && pwd)/models"

digest() {
  if command -v sha256sum >/dev/null; then sha256sum "$1"; else shasum -a 256 "$1"; fi | cut -d' ' -f1
}

# fetch <file> <sha256> <size>: $RELEASE/<file> into $DIR, unless a copy with that digest is already there.
fetch() {
  if [ -f "$DIR/$1" ] && [ "$(digest "$DIR/$1")" = "$2" ]; then return; fi
  echo "Downloading $RELEASE/$1 ($3)"
  curl -fL --retry 3 -o "$DIR/$1.part" "$RELEASE/$1"
  actual="$(digest "$DIR/$1.part")"
  if [ "$actual" != "$2" ]; then
    rm -f "$DIR/$1.part"
    echo "Digest mismatch for $1: expected $2, got $actual" >&2
    exit 1
  fi
  mv "$DIR/$1.part" "$DIR/$1"
}

mkdir -p "$DIR"
fetch silero_vad.onnx "$VAD_SHA256" "629 KB"
fetch "$MODEL.tar.bz2" "$SHA256" "$SIZE"
rm -rf "${DIR:?}/$MODEL"
tar xjf "$DIR/$MODEL.tar.bz2" -C "$DIR"
echo "Ready: $DIR/$MODEL ($(du -sh "$DIR/$MODEL" | cut -f1)) and $DIR/silero_vad.onnx"
