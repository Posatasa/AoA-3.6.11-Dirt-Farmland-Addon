"""Remaps compiled classes from MCP (Mojang) names to SRG names.

ForgeGradle 5 normally does this inside `reobfJar` with SpecialSource. `tools/build.py` needs
the same transformation to build without Gradle, and SpecialSource's ASM 5.0.3 predates Java 11+
class files, so this performs the rewrite directly on the class file's constant pool.

Only the constant pool changes: class names, field names, and method names/descriptors are
rewritten, and every string literal, bytecode instruction and attribute is copied through
untouched. Method descriptors *only* appear in the constant pool (and as attribute indices), so
rewriting the pool is sufficient - nothing in the raw method bodies needs patching.

The pool is rebuilt rather than edited in place: entries vary in length, and shifting a bytearray
underneath indices that the same pass is still reading is a good way to corrupt a class file.

Mappings come from the `srg_to_official` TSRG file ForgeGradle already cached. It maps the other
way round (SRG on the left, official on the right) and writes method descriptors in official
names, so the tables here are built inverted.
"""
import os
import re
import struct
import sys

CONSTANT_UTF8 = 1
CONSTANT_INTEGER = 3
CONSTANT_FLOAT = 4
CONSTANT_LONG = 5
CONSTANT_DOUBLE = 6
CONSTANT_CLASS = 7
CONSTANT_STRING = 8
CONSTANT_FIELDREF = 9
CONSTANT_METHODREF = 10
CONSTANT_INTERFACE_METHODREF = 11
CONSTANT_NAME_AND_TYPE = 12
CONSTANT_METHOD_HANDLE = 15
CONSTANT_METHOD_TYPE = 16
CONSTANT_DYNAMIC = 17
CONSTANT_INVOKE_DYNAMIC = 18
CONSTANT_MODULE = 19
CONSTANT_PACKAGE = 20

VALID_TAGS = {1, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 15, 16, 17, 18, 19, 20}

# Payload length in bytes, ignoring the tag byte itself. Long and double occupy two slots.
TAG_SIZES = {
    CONSTANT_UTF8: None,          # variable, read from the entry
    CONSTANT_INTEGER: 4,
    CONSTANT_FLOAT: 4,
    CONSTANT_LONG: 8,
    CONSTANT_DOUBLE: 8,
    CONSTANT_CLASS: 2,
    CONSTANT_STRING: 2,
    CONSTANT_FIELDREF: 4,
    CONSTANT_METHODREF: 4,
    CONSTANT_INTERFACE_METHODREF: 4,
    CONSTANT_NAME_AND_TYPE: 4,
    CONSTANT_METHOD_HANDLE: 3,
    CONSTANT_METHOD_TYPE: 2,
    CONSTANT_DYNAMIC: 4,
    CONSTANT_INVOKE_DYNAMIC: 4,
    CONSTANT_MODULE: 2,
    CONSTANT_PACKAGE: 2,
}

_TYPE_RE = re.compile(r"L([^;]+);")


class Hierarchy:
    """Declaring-class resolution.

    TSRG lists a member only on the class that *declares* it. A Methodref, on the other hand, names
    whichever class the original source mentioned - and javac emits the static type it saw, which
    for an inherited method is the subclass. `BlockState.getBlock()` is precisely that case:
    `BlockState` extends `AbstractBlock$AbstractBlockState`, and only the latter declares
    `getBlock`/`func_177230_c`. Without this, such a call is left with its MCP name and the game
    dies with a NoSuchMethodError at runtime.

    The names to search for are the *source* (MCP) names, so the superclass chain and the member
    tables are read from the MCP-named jar. Class names are spelled the same in both jars, so the
    class this finds is also the key to use in the TSRG tables.
    """

    def __init__(self, mcp_jar):
        self.supers = {}        # class -> superclass
        self.interfaces = {}    # class -> list of direct interfaces
        self.members = {}       # class -> set of (name, params or None)
        self._load(mcp_jar)

    def _load(self, mcp_jar):
        import zipfile
        with zipfile.ZipFile(mcp_jar) as jar:
            for entry in jar.namelist():
                if not entry.endswith(".class"):
                    continue
                try:
                    cls = ClassFile(jar.read(entry))
                except Exception:
                    continue
                name = cls.this_name()
                if cls.super_name:
                    self.supers[name] = cls.super_name
                self.interfaces[name] = cls.interface_names()
                # Read the class's own field and method tables rather than its constant pool: the
                # pool also names everything the class *calls*, which would make an inherited call
                # look like a local declaration.
                self.members[name] = cls.declared_members()

    def declaring_class(self, owner, name, params=None):
        """The class or interface in the chain that declares this member, or None.

        Interfaces matter here just as much as superclasses: `World` does not declare
        `isEmptyBlock`, it inherits it from `IBlockReader`, and only `IBlockReader`'s entry in the
        TSRG tables will produce the SRG name.
        """
        seen = set()
        stack = [owner]
        while stack:
            current = stack.pop(0)                 # breadth first: nearer declarations win
            if current in seen:
                continue
            seen.add(current)
            if (name, params) in self.members.get(current, ()):
                return current
            if current in self.supers:
                stack.append(self.supers[current])
            stack.extend(self.interfaces.get(current, ()))
        return None


class Mappings:
    """Official -> SRG lookup tables.

    Methods are keyed on the owner and the *parameter* descriptor only, both when parsing and
    when looking up. SRG deliberately gives every overload of a method the same name - all four
    `AbstractBlock.Properties.of(...)` overloads are `func_235836_a_`, for instance - so a
    name-only lookup picks whichever overload happened to be parsed last and silently produces a
    call the verifier then rejects. The parameter list is what distinguishes overloads. The return
    type is left out of the key because the TSRG file and the compiler do not always agree on it
    (a covariant override can be written with the narrower return type), which would break the
    match for no benefit.

    `hierarchy_jar`, when given, is the MCP-named jar whose class hierarchy lets inherited
    members resolve to the class that declares them.
    """

    def __init__(self, tsrg_path, hierarchy_jar=None):
        self.classes = {}                       # "a/b/C" -> "x/y/Z"
        self.fields = {}                        # ("a/b/C", "name") -> "field_1"
        self.methods = {}                       # ("a/b/C", "name", "(params)") -> ("func_1", descriptor)
        self.hierarchy = Hierarchy(hierarchy_jar) if hierarchy_jar else None
        self._parse(tsrg_path)

    @staticmethod
    def params(descriptor):
        close = descriptor.find(")")
        return descriptor if close < 0 else descriptor[:close + 1]

    def _parse(self, path):
        current = None
        with open(path, "r", encoding="utf-8") as fh:
            header = fh.readline().strip().split()
            if len(header) != 3 or header[0] != "tsrg2":
                raise ValueError("unexpected TSRG header: %r" % header)
            for line in fh:
                if not line.strip():
                    continue
                if line.startswith("\t"):
                    if current is None:
                        continue
                    parts = line.strip().split()
                    if len(parts) == 2:
                        srg, official = parts
                        self.fields[(current, official)] = srg
                    elif len(parts) == 3:
                        srg, descriptor, official = parts
                        self.methods[(current, official, self.params(descriptor))] = (srg, descriptor)
                else:
                    parts = line.split()
                    srg, official = parts[0], parts[1]
                    current = official
                    if srg != official:
                        self.classes[official] = srg

    def map_class(self, name):
        return self.classes.get(name, name)

    def map_descriptor(self, descriptor):
        if "L" not in descriptor:
            return descriptor
        return _TYPE_RE.sub(lambda m: "L" + self.map_class(m.group(1)) + ";", descriptor)

    def map_field(self, owner, name):
        key = (owner, name)
        if key in self.fields:
            return self.fields[key]
        if self.hierarchy:
            declared = self.hierarchy.declaring_class(owner, name)
            if declared is not None:
                return self.fields.get((declared, name), name)
        return name

    def map_method(self, owner, name, descriptor):
        """Returns (new name, new descriptor).

        The descriptor is the *compiled* one with class names mapped, not the descriptor written in
        the TSRG file. Those two disagree for covariant returns - TSRG records
        `ItemUseContext.getPos` as returning `Vector3i`, while the method actually returns
        `BlockPos` - and the descriptor the class file must carry is the one the JVM will resolve
        against the SRG jar, i.e. the real one.
        """
        params = self.params(descriptor)
        entry = self.methods.get((owner, name, params))
        if entry is None and self.hierarchy:
            declared = self.hierarchy.declaring_class(owner, name, params)
            if declared is not None:
                entry = self.methods.get((declared, name, params))
        if entry is None:
            return name, self.map_descriptor(descriptor)
        return entry[0], self.map_descriptor(descriptor)


class ClassFile:
    """Just enough of a class file to rebuild its constant pool and keep the rest verbatim."""

    def __init__(self, data):
        self.raw = data
        self.magic, self.minor, self.major, self.count = struct.unpack_from(">IHHH", data, 0)
        if self.magic != 0xCAFEBABE:
            raise ValueError("not a class file")
        self.entries = [None] * self.count        # index -> (tag, payload bytes) or None
        self._parse_pool()
        self.rest = data[self.pool_end:]          # access_flags .. end, all pool-index based
        # this_class / super_class are u2s immediately after access_flags.
        self.this_index = struct.unpack_from(">H", data, self.pool_end + 2)[0]
        self.super_index = struct.unpack_from(">H", data, self.pool_end + 4)[0]

    def this_name(self):
        return self.utf8(self.u2(self.entries[self.this_index][1]))

    @property
    def super_name(self):
        if not self.super_index:
            return None
        return self.utf8(self.u2(self.entries[self.super_index][1]))

    def interface_names(self):
        """The class's direct interfaces, in declaration order."""
        pos = self.pool_end + 6
        count = struct.unpack_from(">H", self.raw, pos)[0]
        pos += 2
        names = []
        for _ in range(count):
            index = struct.unpack_from(">H", self.raw, pos)[0]
            pos += 2
            names.append(self.utf8(self.u2(self.entries[index][1])))
        return names

    def _parse_pool(self):
        pos = 10
        i = 1
        while i < self.count:
            tag = self.raw[pos]
            if tag not in VALID_TAGS:
                raise ValueError("unknown constant pool tag %d at index %d" % (tag, i))
            start = pos
            pos += 1
            size = TAG_SIZES[tag]
            if size is None:
                length = struct.unpack_from(">H", self.raw, pos)[0]
                pos += 2 + length
            else:
                pos += size
            self.entries[i] = (tag, self.raw[start + 1:pos])
            if tag in (CONSTANT_LONG, CONSTANT_DOUBLE):
                i += 1                            # the extra slot is unusable
            i += 1
        self.pool_end = pos

    def u2(self, payload, offset=0):
        return struct.unpack_from(">H", payload, offset)[0]

    def utf8(self, index):
        tag, payload = self.entries[index]
        assert tag == CONSTANT_UTF8, "expected UTF8 at %d, found tag %d" % (index, tag)
        length = struct.unpack_from(">H", payload, 0)[0]
        return payload[2:2 + length].decode("utf-8", "surrogateescape")

    def set_utf8(self, index, text):
        encoded = text.encode("utf-8", "surrogateescape")
        self.entries[index] = (CONSTANT_UTF8, struct.pack(">H", len(encoded)) + encoded)

    def declared_members(self):
        """The (name, params or None) pairs this class declares itself."""
        members = set()
        base = self.pool_end
        pos = base + 6                                  # interfaces_count
        interfaces = struct.unpack_from(">H", self.raw, pos)[0]
        pos += 2 + 2 * interfaces
        for _ in range(2):                              # fields, then methods
            count = struct.unpack_from(">H", self.raw, pos)[0]
            pos += 2
            for _ in range(count):
                _, name_index, descriptor_index, attributes = struct.unpack_from(">HHHH",
                                                                                 self.raw, pos)
                pos += 8
                name = self.utf8(name_index)
                descriptor = self.utf8(descriptor_index)
                members.add((name, Mappings.params(descriptor) if descriptor.startswith("(") else None))
                for _ in range(attributes):
                    _, length = struct.unpack_from(">HI", self.raw, pos)
                    pos += 6 + length
        return members

    def rebuild(self):
        out = bytearray()
        out += struct.pack(">IHHH", self.magic, self.minor, self.major, self.count)
        for i in range(1, self.count):
            entry = self.entries[i]
            if entry is None:                     # the second slot of a long/double
                continue
            tag, payload = entry
            out.append(tag)
            out += payload
        out += self.rest
        return bytes(out)


def remap_class_bytes(data, mappings):
    cls = ClassFile(data)
    # Decide every rename first, then apply it. A single pass that reads *and* writes the pool
    # would see its own edits: two references can share one UTF8 entry, and the second read would
    # then get the renamed text (or, worse, an index that now holds a different kind of entry).
    renames = {}          # pool index -> new text
    for i in range(1, cls.count):
        entry = cls.entries[i]
        if entry is None:
            continue
        tag, payload = entry
        if tag == CONSTANT_CLASS:
            name = cls.utf8(cls.u2(payload))
            mapped = mappings.map_class(name)
            if mapped != name:
                renames[cls.u2(payload)] = mapped
        elif tag == CONSTANT_NAME_AND_TYPE:
            descriptor_index = cls.u2(payload, 2)
            descriptor = cls.utf8(descriptor_index)
            # Field NameAndType entries hold a type descriptor; method ones start with "(".
            if descriptor.startswith("("):
                mapped = mappings.map_descriptor(descriptor)
                if mapped != descriptor:
                    renames[descriptor_index] = mapped
        elif tag in (CONSTANT_FIELDREF, CONSTANT_METHODREF, CONSTANT_INTERFACE_METHODREF):
            # A ref names its owner through a CONSTANT_Class entry, so go via that entry's name.
            owner = cls.utf8(cls.u2(cls.entries[cls.u2(payload)][1]))
            nat_payload = cls.entries[cls.u2(payload, 2)][1]
            name_index, descriptor_index = cls.u2(nat_payload), cls.u2(nat_payload, 2)
            name, descriptor = cls.utf8(name_index), cls.utf8(descriptor_index)
            if tag == CONSTANT_FIELDREF:
                mapped_name = mappings.map_field(owner, name)
                mapped_descriptor = mappings.map_descriptor(descriptor)
            else:
                mapped_name, mapped_descriptor = mappings.map_method(owner, name, descriptor)
            if mapped_name != name:
                renames[name_index] = mapped_name
            if mapped_descriptor != descriptor:
                renames[descriptor_index] = mapped_descriptor

    for index, text in renames.items():
        if cls.entries[index][0] != CONSTANT_UTF8:
            raise ValueError("pool index %d is not a UTF8 entry" % index)
        cls.set_utf8(index, text)
    return cls.rebuild()


def remap_tree(source_dir, target_dir, mappings, verbose=False):
    count = 0
    for root, _, files in os.walk(source_dir):
        for filename in files:
            if not filename.endswith(".class"):
                continue
            src = os.path.join(root, filename)
            rel = os.path.relpath(src, source_dir)
            with open(src, "rb") as fh:
                new_data = remap_class_bytes(fh.read(), mappings)
            dst = os.path.join(target_dir, rel)
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            with open(dst, "wb") as fh:
                fh.write(new_data)
            count += 1
            if verbose:
                print("  remapped", rel)
    return count


if __name__ == "__main__":
    if len(sys.argv) != 4:
        print("usage: remap.py <mappings.tsrg> <input-dir> <output-dir>")
        raise SystemExit(2)
    mappings = Mappings(sys.argv[1])
    print("mappings: %d classes, %d fields, %d methods"
          % (len(mappings.classes), len(mappings.fields), len(mappings.methods)))
    n = remap_tree(sys.argv[2], sys.argv[3], mappings, verbose=True)
    print("remapped %d classes" % n)
