"""Minimal NBT and region file reader/writer for the smoke test (Java edition, big endian)."""
import io
import struct
import zlib

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTE_ARRAY, STRING, LIST, COMPOUND, INT_ARRAY, LONG_ARRAY = range(13)


class Tag:
    def __init__(self, type_id, value, list_type=END):
        self.type = type_id
        self.value = value
        self.list_type = list_type

    def __repr__(self):
        return f"Tag({self.type}, {self.value!r})"


def _read_string(f):
    (n,) = struct.unpack(">H", f.read(2))
    return f.read(n).decode("utf-8", "surrogatepass")


def _write_string(f, s):
    b = s.encode("utf-8", "surrogatepass")
    f.write(struct.pack(">H", len(b)))
    f.write(b)


def _read_payload(f, t):
    if t == BYTE:
        return Tag(t, struct.unpack(">b", f.read(1))[0])
    if t == SHORT:
        return Tag(t, struct.unpack(">h", f.read(2))[0])
    if t == INT:
        return Tag(t, struct.unpack(">i", f.read(4))[0])
    if t == LONG:
        return Tag(t, struct.unpack(">q", f.read(8))[0])
    if t == FLOAT:
        return Tag(t, struct.unpack(">f", f.read(4))[0])
    if t == DOUBLE:
        return Tag(t, struct.unpack(">d", f.read(8))[0])
    if t == BYTE_ARRAY:
        (n,) = struct.unpack(">i", f.read(4))
        return Tag(t, bytearray(f.read(n)))
    if t == STRING:
        return Tag(t, _read_string(f))
    if t == LIST:
        lt, n = struct.unpack(">bi", f.read(5))
        return Tag(t, [_read_payload(f, lt) for _ in range(n)], lt)
    if t == COMPOUND:
        d = {}
        while True:
            (ct,) = struct.unpack(">b", f.read(1))
            if ct == END:
                return Tag(t, d)
            name = _read_string(f)
            d[name] = _read_payload(f, ct)
    if t == INT_ARRAY:
        (n,) = struct.unpack(">i", f.read(4))
        return Tag(t, list(struct.unpack(f">{n}i", f.read(4 * n))))
    if t == LONG_ARRAY:
        (n,) = struct.unpack(">i", f.read(4))
        return Tag(t, list(struct.unpack(f">{n}q", f.read(8 * n))))
    raise ValueError(f"Unknown tag type {t}")


def _write_payload(f, tag):
    t, v = tag.type, tag.value
    if t == BYTE:
        f.write(struct.pack(">b", v))
    elif t == SHORT:
        f.write(struct.pack(">h", v))
    elif t == INT:
        f.write(struct.pack(">i", v))
    elif t == LONG:
        f.write(struct.pack(">q", v))
    elif t == FLOAT:
        f.write(struct.pack(">f", v))
    elif t == DOUBLE:
        f.write(struct.pack(">d", v))
    elif t == BYTE_ARRAY:
        f.write(struct.pack(">i", len(v)))
        f.write(bytes(v))
    elif t == STRING:
        _write_string(f, v)
    elif t == LIST:
        lt = v[0].type if v else tag.list_type
        f.write(struct.pack(">bi", lt, len(v)))
        for item in v:
            _write_payload(f, item)
    elif t == COMPOUND:
        for name, child in v.items():
            f.write(struct.pack(">b", child.type))
            _write_string(f, name)
            _write_payload(f, child)
        f.write(b"\x00")
    elif t == INT_ARRAY:
        f.write(struct.pack(f">i{len(v)}i", len(v), *v))
    elif t == LONG_ARRAY:
        f.write(struct.pack(f">i{len(v)}q", len(v), *v))
    else:
        raise ValueError(f"Unknown tag type {t}")


def read_root(data):
    f = io.BytesIO(data)
    (t,) = struct.unpack(">b", f.read(1))
    _read_string(f)
    return _read_payload(f, t)


def write_root(tag):
    f = io.BytesIO()
    f.write(struct.pack(">b", tag.type))
    _write_string(f, "")
    _write_payload(f, tag)
    return f.getvalue()


def read_chunk(region_path, index):
    with open(region_path, "rb") as f:
        data = f.read()
    (loc,) = struct.unpack(">I", data[index * 4:index * 4 + 4])
    offset, sectors = loc >> 8, loc & 0xFF
    if offset == 0:
        return None
    start = offset * 4096
    length, compression = struct.unpack(">iB", data[start:start + 5])
    payload = data[start + 5:start + 4 + length]
    if compression == 2:
        payload = zlib.decompress(payload)
    elif compression != 3:
        raise ValueError(f"Unsupported compression {compression}")
    return read_root(payload)


def write_chunk(region_path, index, tag):
    with open(region_path, "rb") as f:
        data = bytearray(f.read())
    payload = zlib.compress(write_root(tag))
    body = struct.pack(">iB", len(payload) + 1, 2) + payload
    body += b"\x00" * (-len(body) % 4096)
    offset = (len(data) + 4095) // 4096
    data += b"\x00" * (offset * 4096 - len(data))
    data += body
    struct.pack_into(">I", data, index * 4, (offset << 8) | (len(body) // 4096))
    with open(region_path, "wb") as f:
        f.write(data)
