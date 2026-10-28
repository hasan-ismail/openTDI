#!/usr/bin/env python3
# Copyright (C) 2026 Hasan Ismail
# SPDX-License-Identifier: GPL-3.0-only
# This file is part of OpenTDI, free software under the GNU General Public License version 3 only (see LICENSE).
"""Fails when an APK defines a class under com.samsung, so no published build carries Samsung code.

Usage: python3 tools/release/dexcheck.py <apk>...   (exit 1 when any class is defined, 0 otherwise)

Owner request 2026-10-28 ("use github actions to publish builds"); run by .github/workflows/ci.yml and release.yml,
see NOTICE. Only definitions count: OpenTDI's own code may reference Samsung's classes, which the user's own kit
provides at run time. The phone's assets/watch.apk (the watch app it installs) is checked as well.
"""
import contextlib
import io
import struct
import sys
import zipfile

PREFIX = 'Lcom/samsung/'
WATCH_APK = 'assets/watch.apk'


def uleb(b, i):
    r = s = 0
    while True:
        x = b[i]
        i += 1
        r |= (x & 0x7f) << s
        if x < 0x80:
            return r, i
        s += 7


def defined_classes(dex):
    """The descriptor (e.g. Lcom/example/Foo;) of every class_def in one dex file."""
    _, string_ids_off, _, type_ids_off = struct.unpack_from('<IIII', dex, 0x38)
    class_defs_size, class_defs_off = struct.unpack_from('<II', dex, 0x60)

    def string(idx):
        off = struct.unpack_from('<I', dex, string_ids_off + 4 * idx)[0]
        _, i = uleb(dex, off)  # skips the UTF-16 length
        return dex[i:dex.index(b'\0', i)].decode('utf-8', 'replace')

    for k in range(class_defs_size):
        type_idx = struct.unpack_from('<I', dex, class_defs_off + 32 * k)[0]
        yield string(struct.unpack_from('<I', dex, type_ids_off + 4 * type_idx)[0])


def scan(apk, label):
    """Prints and returns the Samsung classes defined in [apk] (a path or file object) and its assets/watch.apk."""
    with zipfile.ZipFile(apk) as z:
        dexes = [n for n in z.namelist() if n.endswith('.dex')]
        if not dexes:
            sys.exit(f'{label}: no dex files, so nothing could be checked')
        found = [c for n in dexes for c in defined_classes(z.read(n)) if c.startswith(PREFIX)]
        print(f'{label}: {len(found)} Samsung class definitions' + (f', e.g. {found[:3]}' if found else ''))
        if WATCH_APK in z.namelist():
            found += scan(io.BytesIO(z.read(WATCH_APK)), f'{label} > {WATCH_APK}')
    return found


def dex_defining(descriptor):
    """A minimal dex holding one string, one type and one class_def, for the self-check."""
    dex = bytearray(0x98)
    struct.pack_into('<IIII', dex, 0x38, 1, 0x70, 1, 0x74)  # 1 string id at 0x70, 1 type id at 0x74
    struct.pack_into('<II', dex, 0x60, 1, 0x78)  # 1 class_def at 0x78 (class_idx 0)
    struct.pack_into('<II', dex, 0x70, 0x98, 0)  # string 0 starts right after; type 0 is string 0
    return bytes(dex) + bytes([len(descriptor)]) + descriptor.encode() + b'\0'


def zipped(files):
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, 'w') as z:
        for name, data in files.items():
            z.writestr(name, data)
    return buf.getvalue()


def self_check():
    """A Samsung class inside a phone's assets/watch.apk must be caught, or no clean result can be trusted."""
    watch = zipped({'classes.dex': dex_defining('Lcom/samsung/Kit;')})
    phone = zipped({'classes.dex': dex_defining('Lcom/opentdi/App;'), WATCH_APK: watch})
    with contextlib.redirect_stdout(io.StringIO()):
        found = scan(io.BytesIO(phone), 'self-check')
    if found != ['Lcom/samsung/Kit;']:
        sys.exit(f'dexcheck self-check failed: found {found}')


if __name__ == '__main__':
    self_check()
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    hits = [c for path in sys.argv[1:] for c in scan(path, path)]
    sys.exit(1 if hits else 0)
