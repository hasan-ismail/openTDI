#!/usr/bin/env bash
# Installs the neural cue voice from tools/voice/models/ into com.opentdi's private storage on the phone
# (files/voice/<model>/), where Prepare cues looks for it. Touches only com.opentdi and its own temporary copy in
# /data/local/tmp, which is deleted at the end. Usage: tools/voice/push_voice_model.sh <phone-serial>
set -euo pipefail
export MSYS_NO_PATHCONV=1 # Git Bash on Windows would rewrite the device paths below

SERIAL="${1:?usage: push_voice_model.sh <phone-serial>}"
MODEL=kokoro-int8-en-v0_19
# pwd -W gives a Windows path under Git Bash, which adb.exe needs; elsewhere it fails and pwd answers.
SRC="$(cd "$(dirname "$0")" && (pwd -W 2>/dev/null || pwd))/models/$MODEL"
TMP="/data/local/tmp/opentdi-$MODEL"

if [ ! -f "$SRC/model.int8.onnx" ]; then
  echo "No model in $SRC. Run tools/voice/fetch_voice_model.sh first." >&2
  exit 1
fi

phone() { adb -s "$SERIAL" "$@"; }
phone shell "rm -rf $TMP"
phone push "$SRC" "$TMP"
# The shell user reads its own copy and pipes it into run-as, so the app needs no access to /data/local/tmp.
phone shell "cd $TMP && tar cf - . | run-as com.opentdi --user 0 sh -c \
  'rm -rf files/voice/$MODEL && mkdir -p files/voice/$MODEL && tar xf - -C files/voice/$MODEL'"
phone shell "rm -rf $TMP"
phone shell "run-as com.opentdi --user 0 ls -l files/voice/$MODEL"
echo "Installed. In OpenTDI, turn on Natural voice and tap Prepare cues."
