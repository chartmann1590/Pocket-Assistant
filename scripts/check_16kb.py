#!/usr/bin/env python3
"""Fail if any 64-bit native library in the given APKs is not 16 KB page-size compatible.

Checks each lib/arm64-v8a and lib/x86_64 .so: every ELF LOAD segment must be
aligned to at least 16 KB, and uncompressed libraries must start on a 16 KB
boundary inside the APK.
"""
import struct
import sys
import zipfile

PAGE = 16384


def load_aligns(data):
    if data[:4] != b"\x7fELF" or data[4] != 2:
        return []
    phoff = struct.unpack_from("<Q", data, 0x20)[0]
    phentsize, phnum = struct.unpack_from("<HH", data, 0x36)
    out = []
    for i in range(phnum):
        off = phoff + i * phentsize
        if struct.unpack_from("<I", data, off)[0] == 1:
            out.append(struct.unpack_from("<Q", data, off + 0x30)[0])
    return out


def check(path):
    bad = 0
    count = 0
    with zipfile.ZipFile(path) as z, open(path, "rb") as raw:
        for info in z.infolist():
            name = info.filename
            if not name.endswith(".so") or not (name.startswith("lib/arm64-v8a/") or name.startswith("lib/x86_64/")):
                continue
            count += 1
            aligns = load_aligns(z.read(info))
            problems = []
            if aligns and min(aligns) < PAGE:
                problems.append("ELF LOAD alignment %d" % min(aligns))
            if info.compress_type == zipfile.ZIP_STORED:
                raw.seek(info.header_offset)
                header = raw.read(30)
                name_len, extra_len = struct.unpack_from("<HH", header, 26)
                data_off = info.header_offset + 30 + name_len + extra_len
                if data_off % PAGE:
                    problems.append("zip offset not 16 KB aligned")
            if problems:
                bad += 1
                print("FAIL %s: %s" % (name, ", ".join(problems)))
    print("%s: %d 64-bit native libs checked, %d not 16 KB compatible" % (path, count, bad))
    return bad


def main(paths):
    if not paths:
        print("usage: check_16kb.py app.apk [...]")
        return 2
    return 1 if sum(check(p) for p in paths) else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
