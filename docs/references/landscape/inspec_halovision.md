# Halovision INSPEC (lucidcode) — what is useful for OpenTDI

Repo: https://github.com/lucidcode/Halovision-INSPEC @0671fadb (2026-10-25), 66 stars. **No license** (GitHub
`license: none`, no LICENSE file, no headers in the scripts) → all rights reserved: study ideas, copy no code.
The firmware under `firmware/` is a fork of OpenMV (its own license); only `software/*.py` (MicroPython) and `api/`
are INSPEC-specific. Read 2026-10-27 (Gemini scout summary, key claims verified in the code by Claude).

## What it is

A bedside night-vision smart camera (OpenMV N6 class) on a tripod that watches the sleeper's face, detects eye
movements to find **REM** for lucid dreaming, and fires LED/audio alerts via the Lucid Scribe apps (iOS, Android,
desktop). Different stage (REM, not N1) and different sensor (camera, not wrist) from OpenTDI.

## Mechanisms (FACT, file:line in `software/`)

| Mechanism | How it works |
|---|---|
| REM ladder (`rem.py:16-80`) | Integer evidence level 0–8: +1 per qualifying eye movement, at most once per second (`:75-78`); decays −1 every 2 s once 60 s pass without eye movement (`:37-39`); a gross body movement ("toss": global frame variance ≥ `TossThreshold` 256) resets it to 0 and starts a `TossCooldown` (1 s) (`:26-35`); an artifact filter rejects movement that is mostly outside the eye region (`:44-58`); a burst-gap rule (`EyeBurst` 2500 ms) ignores isolated flickers (`:66-73`). An alert fires at level 8 |
| "NREM1 Detection" mode (`nrem.py:10-23`) | Stillness only: any frame motion ≥ `TriggerThreshold` resets the level to 0; every `NREM1Delay`/8 (60 s / 8 = 7.5 s) without motion adds 1, up to 8. So "NREM1" = 60 s of camera-visible stillness. No physiology |
| Sleep quality index (`quality.py:10-30`) | 0–100: +1 per minute without a toss, −1 per toss (at most every 10 s) |
| Alert timing (`config.py`) | `TriggerDelay` 0 ms (detection → alert) and `TriggerInterval` 60 s (re-trigger lockout), kept separate; no maximum alerts per night; no volume or brightness ramps |
| Integration | BLE GATT service (custom UUIDs, text commands, ~7.8 Hz telemetry `variance;eye_movements;face;sqi`); Wi-Fi TCP server on port 5000 streaming `\r\n` lines (`rem:<n>`, `nrem1:<n>`, `quality:<n>`, `trigger:<v>`, raw variance); `api/webhook_server.py` turns that stream into HTTP POSTs (`/rem`, `/nrem1`, `/quality`, `/trigger`, `/variance`) for other tools |
| Logging | Per-session folder with a config snapshot, per-minute aggregate lines (`HH:MM:channel - values`), optional per-frame CSV + images |

## Comparison with OpenTDI

- **Their "NREM1" is stillness alone.** A shipping lucid-dreaming device treats 60 s of stillness as N1. OpenTDI
  deliberately requires stillness **and** a heart-rate drop (owner requirement 11), because "a relaxed but awake
  person is also still" (spec §3). INSPEC publishes no validation of its NREM1 mode, so it is not evidence either way
  (anecdotal).
- **Graded evidence vs a hard reset.** OpenTDI's `hold` counter (spec §8.7) resets to 0 after a single second
  without the core condition; INSPEC's ladder rises one step at a time and decays slowly, so one noisy second does not
  erase built-up evidence. Worth considering for Phase 2 as an option ("a false second decrements the hold instead
  of resetting it"), tested through the replay harness on real naps before it becomes a default. GUESSED/anecdotal.
- **Toss reset + cooldown, delay vs lockout, config snapshot per session**: OpenTDI already has equivalents (the
  stillness gate and the countdown movement abort, `countdownS` vs `refractoryS`, `w_config.csv`).
- **Event stream for other tools**: INSPEC's line protocol + webhook bridge lets lights or home automation react to
  detections. A similar optional local event feed (onset, cue, state) is a possible Deferred feature for OpenTDI.
- **Possible extra sensor (Deferred idea):** slow rolling eye movements are characteristic of sleep onset, and a
  bedside camera can see eyelids and eyes; an owner with an INSPEC could feed its TCP stream (`nrem1:`, raw variance)
  into OpenTDI as bonus evidence. Not planned; listed only as an idea.
- Not relevant: face/eye tracking models, camera tuning, MJPEG streaming, LED drivers, BLE OTA.
