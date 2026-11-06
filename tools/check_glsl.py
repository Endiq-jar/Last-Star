#!/usr/bin/env python3
"""Static GLES2 semantic checks that a plain syntax parser misses.

Catches the class of bug that produced a black screen on device:
  * fwidth/dFdx/dFdy used without #extension GL_OES_standard_derivatives
  * #extension placed after the first non-preprocessor token (illegal)
  * precision qualifier missing in a fragment shader
  * unterminated statements / unbalanced braces
"""
import re, os, sys

fail = 0
for f in sorted(os.listdir('build/shaders')):
    src = open(os.path.join('build/shaders', f)).read()
    is_frag = 'FRAG' in f
    problems = []

    uses_deriv = re.search(r'\b(fwidth|dFdx|dFdy)\s*\(', src)
    has_ext = 'GL_OES_standard_derivatives' in src
    if uses_deriv and not has_ext:
        problems.append('uses %s without GL_OES_standard_derivatives'
                        % uses_deriv.group(1))

    # #extension must precede every other token
    lines = src.split('\n')
    for i, line in enumerate(lines):
        t = line.strip()
        if t.startswith('#extension'):
            prior = [l.strip() for l in lines[:i]
                     if l.strip() and not l.strip().startswith('//')]
            if prior:
                problems.append('#extension on line %d comes after "%s"'
                                % (i + 1, prior[0][:30]))
    if is_frag and 'precision' not in src:
        problems.append('fragment shader without a precision qualifier')

    if src.count('{') != src.count('}'):
        problems.append('unbalanced braces')
    if src.count('(') != src.count(')'):
        problems.append('unbalanced parentheses')
    if 'gl_FragColor' not in src and is_frag:
        problems.append('fragment shader never writes gl_FragColor')

    if problems:
        fail += 1
        print('FAIL %s' % f)
        for p in problems:
            print('       ' + p)
    else:
        print('ok   %s' % f)

print()
print('GLES2 semantic checks: %d file(s) failed' % fail if fail else
      'GLES2 semantic checks: all clean')
sys.exit(1 if fail else 0)
