#!/usr/bin/env python3
"""zipalign replacement: rewrites a zip so entries are correctly aligned.

* uncompressed entries are padded to 4-byte boundaries (Android requires
  resources.arsc to be 4-byte aligned and stored uncompressed on API 30+)
* native libraries (.so) are padded to a full page (16 KiB) boundary, which is
  what modern ARM64 devices expect and what the SDK's zipalign -p does

Usage: python3 tools/zipalign.py <in.apk> <out.apk>
"""
import struct
import sys
import zlib

PAGE_SIZE = 16384
ALIGNMENT = 4

LOCAL_SIG = 0x04034B50
CENTRAL_SIG = 0x02014B50
EOCD_SIG = 0x06054B50


class Entry:
    __slots__ = ("name", "data", "method", "crc", "csize", "usize",
                 "external_attr", "internal_attr", "create_version",
                 "extract_version", "flag_bits", "date_time", "offset")


def read_entries(blob):
    eocd = blob.rfind(struct.pack("<I", EOCD_SIG))
    if eocd < 0:
        raise SystemExit("not a zip: no EOCD")
    (_, _, _, _, total, cd_size, cd_off, _) = struct.unpack("<IHHHHIIH", blob[eocd:eocd + 22])
    entries = []
    pos = cd_off
    for _ in range(total):
        if struct.unpack("<I", blob[pos:pos + 4])[0] != CENTRAL_SIG:
            raise SystemExit("bad central directory signature")
        fields = struct.unpack("<IHHHHHHIIIHHHHHII", blob[pos:pos + 46])
        (_, create_ver, extract_ver, flags, method, time, date, crc, csize, usize,
         nlen, elen, clen, disk, iattr, eattr, local_off) = fields
        name = blob[pos + 46:pos + 46 + nlen].decode("utf-8", "replace")
        e = Entry()
        e.name = name
        e.create_version = create_ver
        e.extract_version = extract_ver
        e.flag_bits = flags
        e.method = method
        e.date_time = (date, time)
        e.crc = crc
        e.csize = csize
        e.usize = usize
        e.internal_attr = iattr
        e.external_attr = eattr
        e.offset = local_off
        # Local header: 30 bytes + name + extra
        lh = blob[local_off:local_off + 30]
        lnlen, lelen = struct.unpack("<HH", lh[26:30])
        data_start = local_off + 30 + lnlen + lelen
        e.data = blob[data_start:data_start + csize]
        entries.append(e)
        pos += 46 + nlen + elen + clen
    return entries


def write_zip(path, entries):
    out = bytearray()
    directory = []
    for e in entries:
        alignment = PAGE_SIZE if e.name.endswith(".so") else ALIGNMENT
        offset = len(out)
        # Local header is 30 + name length + padding extra field.
        name_bytes = e.name.encode("utf-8")
        base = offset + 30 + len(name_bytes)
        pad = (alignment - (base % alignment)) % alignment
        extra = b""
        if pad:
            # Alignment padding uses a "padding" extra field (header id 0xD935).
            if pad < 4:
                pad += alignment
            body = b"\x00" * (pad - 4)
            extra = struct.pack("<HH", 0xD935, len(body)) + body
        header = struct.pack("<IHHHHHIIIHH", LOCAL_SIG, e.extract_version, e.flag_bits,
                             e.method, e.date_time[1], e.date_time[0], e.crc,
                             e.csize, e.usize, len(name_bytes), len(extra))
        out += header + name_bytes + extra
        assert len(out) % alignment == 0, (e.name, len(out), alignment)
        out += e.data
        directory.append((e, offset, extra))

    cd_start = len(out)
    for e, offset, extra in directory:
        name_bytes = e.name.encode("utf-8")
        out += struct.pack("<IHHHHHHIIIHHHHHII", CENTRAL_SIG, e.create_version,
                           e.extract_version, e.flag_bits, e.method, e.date_time[1],
                           e.date_time[0], e.crc, e.csize, e.usize, len(name_bytes),
                           len(extra), 0, 0, e.internal_attr, e.external_attr, offset)
        out += name_bytes
        out += extra
    cd_size = len(out) - cd_start
    out += struct.pack("<IHHHHIIH", EOCD_SIG, 0, 0, len(directory), len(directory),
                       cd_size, cd_start, 0)
    with open(path, "wb") as f:
        f.write(out)


def main():
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    with open(sys.argv[1], "rb") as f:
        blob = f.read()
    entries = read_entries(blob)
    write_zip(sys.argv[2], entries)
    print(f"    aligned {len(entries)} entries -> {sys.argv[2]}")


if __name__ == "__main__":
    main()
