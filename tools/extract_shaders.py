#!/usr/bin/env python3
"""Extracts every GLSL shader that ships in the APK, assembled exactly as the
Java code assembles it, so the sources can be checked by a real GLES2 checker.

Shaders are written as String constants -- sometimes split into pieces and
concatenated (FRAG = FRAG_HEAD + FRAG_BODY). A naive extractor silently drops
those, which is how a shader bug reaches a phone: this one resolves constants
recursively and refuses to emit an empty shader.
"""
import re
import os
import sys

SRC = "app/src/main/java/com/laststar/game"
OUT = "build/shaders"

CONST_RE = re.compile(
    r'(?:public |private )?static final String (\w+)\s*=\s*(.*?);\n', re.S)
LITERAL_RE = re.compile(r'"((?:[^"\\]|\\.)*)"')
IDENT_RE = re.compile(r'\b([A-Z][A-Z0-9_]{2,})\b')


def unescape(s):
    return s.replace('\\n', '\n').replace('\\"', '"').replace('\\\\', '\\')


def main():
    out = {}
    for root, _dirs, files in os.walk(SRC):
        for fname in sorted(files):
            if not fname.endswith('.java'):
                continue
            src = open(os.path.join(root, fname)).read()
            consts, order = {}, []
            for m in CONST_RE.finditer(src):
                consts[m.group(1)] = m.group(2)
                order.append(m.group(1))
            if not consts:
                continue

            def resolve(name, depth=0):
                """Assemble a constant, inlining any other constants it names."""
                if depth > 8 or name not in consts:
                    return ''
                body = consts[name]

                def inline(mm):
                    ref = mm.group(1)
                    if ref in consts:
                        # Inline as Java source, then let the literal pass below
                        # pull the quotes out of the combined text.
                        return consts[ref]
                    return mm.group(0)

                expanded = IDENT_RE.sub(inline, body)
                # Only string literals matter; + and whitespace fall away.
                return unescape(''.join(LITERAL_RE.findall(expanded)))

            pieces = [n for n in order
                      if n.endswith('_HEAD') or n.endswith('_BODY')]
            for name in order:
                if name in pieces or name == 'EXT_DIRECTIVE':
                    continue
                if name == 'FRAG' and 'FRAG_PLAIN' in consts:
                    # Two variants ship: the extended one is tried first, the
                    # plain one is the fallback when the driver refuses it.
                    out[fname + ":FRAG_EXT"] = (
                        resolve('EXT_DIRECTIVE') + resolve('FRAG'))
                    out[fname + ":FRAG_PLAIN"] = resolve('FRAG_PLAIN')
                    continue
                text = resolve(name)
                if not text:
                    continue
                if 'gl_' not in text and 'aPos' not in text and 'vColor' not in text:
                    continue
                out[fname + ":" + name] = text

    os.makedirs(OUT, exist_ok=True)
    empty = 0
    for k, v in out.items():
        stem = k.replace('.java', '').replace(':', '_')
        open(os.path.join(OUT, stem + ".glsl"), "w").write(v)
        print("%-34s %5d chars" % (k, len(v)))
        if len(v) < 40:
            empty += 1
            print("   ^^ suspiciously short: the extractor may have lost it")
    print("wrote %d shader sources" % len(out))
    return 1 if empty else 0


if __name__ == "__main__":
    sys.exit(main())
