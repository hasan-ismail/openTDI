#!/usr/bin/env bash
# Installs the transcription model and the Silero VAD from tools/stt/models/ into com.opentdi's private storage on the
# phone (files/stt/), where the app looks for them after each copy from the watch. Touches only com.opentdi (user 0)
# and its own temporary copy in /data/local/tmp, which is deleted at the end.
# Usage: tools/stt/push_stt_model.sh <phone-serial> [parakeet|moonshine]
set -euo pipefail
export MSYS_NO_PATHCONV=1 # Git Bash on Windows would rewrite the device paths below

SERIAL="${1:?usage: push_stt_model.sh <phone-serial> [parakeet|moonshine]}"
case "${2:-parakeet}" in
  parakeet) MODEL=sherpa-onnx-nemo-parakeet_tdt_ctc_110m-en-36000-int8 ;;
  moonshine) MODEL=sherpa-onnx-moonshine-tiny-en-quantized-2026-02-27 ;;
  *) echo "usage: push_stt_model.sh <phone-serial> [parakeet|moonshine]" >&2; exit 2 ;;
esac
# pwd -W gives a Windows path under Git Bash, which adb.exe needs; elsewhere it fails and pwd answers.
SRC="$(cd "$(dirname "$0")" && (pwd -W 2>/dev/null || pwd))/models"
TMP="/data/local/tmp/opentdi-stt"

if [ ! -d "$SRC/$MODEL" ] || [ ! -f "$SRC/silero_vad.onnx" ]; then
  echo "No model in $SRC. Run tools/stt/fetch_stt_model.sh ${2:-parakeet} first." >&2
  exit 1
fi

phone() { adb -s "$SERIAL" "$@"; }
phone shell "rm -rf $TMP && mkdir -p $TMP"
phone push "$SRC/$MODEL" "$TMP/$MODEL"
phone push "$SRC/silero_vad.onnx" "$TMP/silero_vad.onnx"
# The shell user reads its own copy and pipes it into run-as, so the app needs no access to /data/local/tmp.
phone shell "cd $TMP && tar cf - . | run-as com.opentdi --user 0 sh -c \
  'rm -rf files/stt/$MODEL && mkdir -p files/stt && tar xf - -C files/stt'"
phone shell "rm -rf $TMP"
phone shell "run-as com.opentdi --user 0 ls -lR files/stt"
echo "Installed. OpenTDI transcribes the dream recordings after its next copy from the watch (open Stats)."
