#!/usr/bin/env python3
#
# MyBuoy — outdoor navigation & mark-tracking for boating, hiking, foraging and discovery walks.
# Copyright (C) 2026 Golden-Block
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
# GNU Affero General Public License for more details.
#
# You should have received a copy of the GNU Affero General Public License
# along with this program. If not, see <https://www.gnu.org/licenses/>.
#
# For commercial licensing (use without AGPL obligations), contact:
# licence@golden-block.fr
#
"""Builds BuoyRound.ttf, the rounded heavy display font used by mkico.sh.

Every glyph is a skeleton (centre lines and arcs) stroked with a round pen,
then slanted. Nothing is traced from another font.

    python3 make_buoyround.py [out.ttf]     (needs fontTools)
"""
import math
import os
import sys

from fontTools.fontBuilder import FontBuilder
from fontTools.pens.ttGlyphPen import TTGlyphPen

UPM = 1000
R = 72            # pen radius: stroke width = 2R
SLANT = 0.12      # x += SLANT * y
SIDE = 45         # side bearing

# Centre-line heights (the ink extends R beyond them).
B = R             # baseline
X = 520 - R       # x-height
C = 720 - R       # cap height
A = 750 - R       # ascender
D = -230 + R      # descender
MID = (B + X) / 2
CMID = (B + C) / 2


# ---------------------------------------------------------------- skeleton helpers
def arc(cx, cy, rx, ry, a0, a1):
    """Points along an elliptic arc, from angle a0 to a1 (degrees, either way round)."""
    n = max(4, int(abs(a1 - a0) / 7))
    pts = []
    for k in range(n + 1):
        a = math.radians(a0 + (a1 - a0) * k / n)
        pts.append((cx + rx * math.cos(a), cy + ry * math.sin(a)))
    return pts


def ellipse(cx, cy, rx, ry):
    return arc(cx, cy, rx, ry, 0, 360)


def dot(x, y):
    return [(x, y)]


def chain(*parts):
    """Joins point lists into one stroke (drops duplicated junction points)."""
    out = []
    for p in parts:
        for q in p:
            if not out or math.dist(out[-1], q) > 0.5:
                out.append(q)
    return out


def L(*pts):
    return list(pts)


# ---------------------------------------------------------------- glyph skeletons
def glyphs():
    g = {}
    # Lowercase
    g['a'] = [ellipse(250, MID, 150, MID - B), L((400, X), (400, B))]
    g['b'] = [L((100, A), (100, B)), ellipse(250, MID, 150, MID - B)]
    g['c'] = [arc(260, MID, 160, MID - B, 40, 320)]
    g['d'] = [ellipse(250, MID, 150, MID - B), L((400, A), (400, B))]
    g['e'] = [chain(L((110, MID + 10), (410, MID + 10)), arc(260, MID, 150, MID - B, 4, 320))]
    g['f'] = [chain(L((180, B), (180, 520)), arc(320, 520, 140, A - 520, 180, 70)),
              L((80, X), (330, X))]
    g['g'] = [ellipse(250, MID, 150, MID - B),
              chain(L((400, X), (400, -40)), arc(250, -40, 150, -40 - D, 0, -160))]
    g['h'] = [L((100, A), (100, B)),
              chain(arc(250, 300, 150, X - 300, 180, 0), L((400, 300), (400, B)))]
    g['i'] = [L((100, X), (100, B)), dot(100, 655)]
    g['j'] = [chain(L((210, X), (210, -60)), arc(100, -60, 110, -60 - D, 0, -150)), dot(210, 655)]
    g['k'] = [L((100, A), (100, B)), L((390, X), (110, 230)), L((210, 290), (400, B))]
    g['l'] = [L((100, A), (100, B))]
    g['m'] = [L((100, X), (100, B)),
              chain(arc(225, 320, 125, X - 320, 180, 0), L((350, 320), (350, B))),
              chain(arc(475, 320, 125, X - 320, 180, 0), L((600, 320), (600, B)))]
    g['n'] = [L((100, X), (100, B)),
              chain(arc(250, 300, 150, X - 300, 180, 0), L((400, 300), (400, B)))]
    g['o'] = [ellipse(260, MID, 160, MID - B)]
    g['p'] = [L((100, X), (100, D)), ellipse(250, MID, 150, MID - B)]
    g['q'] = [ellipse(250, MID, 150, MID - B), L((400, X), (400, D))]
    g['r'] = [L((100, X), (100, B)), arc(260, 300, 160, X - 300, 180, 55)]
    g['s'] = [chain(arc(250, 355, 135, 95, 25, 270), arc(250, 165, 145, 95, 90, -155))]
    g['t'] = [chain(L((180, 620), (180, 200)), arc(310, 200, 130, 200 - B, 180, 290)),
              L((80, X), (340, X))]
    g['u'] = [chain(L((100, X), (100, 220)), arc(250, 220, 150, 220 - B, 180, 360), L((400, 220), (400, X))),
              L((400, X), (400, B))]
    g['v'] = [L((90, X), (250, B)), L((250, B), (410, X))]
    g['w'] = [L((90, X), (210, B)), L((210, B), (330, X - 120)), L((330, X - 120), (450, B)), L((450, B), (570, X))]
    g['x'] = [L((100, X), (400, B)), L((400, X), (100, B))]
    g['y'] = [L((100, X), (260, 150)), L((410, X), (160, D))]
    g['z'] = [L((100, X), (400, X)), L((400, X), (100, B)), L((100, B), (400, B))]
    # Uppercase
    g['A'] = [L((90, B), (290, C)), L((290, C), (490, B)), L((170, 250), (410, 250))]
    g['B'] = [L((100, C), (100, B)),
              chain(L((100, C), (290, C)), arc(290, 505, 150, C - 505, 90, -90), L((290, 360), (100, 360))),
              chain(L((100, 360), (310, 360)), arc(310, 215, 160, 360 - 215, 90, -90), L((310, B), (100, B)))]
    g['C'] = [arc(330, CMID, 240, CMID - B, 45, 315)]
    g['D'] = [L((100, C), (100, B)),
              chain(L((100, C), (250, C)), arc(250, CMID, 230, CMID - B, 90, -90), L((250, B), (100, B)))]
    g['E'] = [L((100, C), (100, B)), L((100, C), (430, C)), L((100, 360), (380, 360)), L((100, B), (430, B))]
    g['F'] = [L((100, C), (100, B)), L((100, C), (430, C)), L((100, 360), (380, 360))]
    g['G'] = [chain(arc(330, CMID, 240, CMID - B, 45, 350), L((568, 320), (568, 150))),
              L((380, 310), (560, 310))]
    g['H'] = [L((100, C), (100, B)), L((470, C), (470, B)), L((100, 360), (470, 360))]
    g['I'] = [L((100, C), (100, B))]
    g['J'] = [chain(L((360, C), (360, 230)), arc(215, 230, 145, 230 - B, 0, -180))]
    g['K'] = [L((100, C), (100, B)), L((460, C), (110, 300)), L((230, 400), (470, B))]
    g['L'] = [L((100, C), (100, B)), L((100, B), (420, B))]
    g['M'] = [L((100, B), (100, C)), L((100, C), (310, 260)), L((310, 260), (520, C)), L((520, C), (520, B))]
    g['N'] = [L((100, B), (100, C)), L((100, C), (460, B)), L((460, B), (460, C))]
    g['O'] = [ellipse(330, CMID, 240, CMID - B)]
    g['P'] = [L((100, C), (100, B)),
              chain(L((100, C), (290, C)), arc(290, 480, 170, C - 480, 90, -90), L((290, 310), (100, 310)))]
    g['Q'] = [ellipse(330, CMID, 240, CMID - B), L((360, 200), (560, B - 20))]
    g['R'] = [L((100, C), (100, B)),
              chain(L((100, C), (290, C)), arc(290, 480, 170, C - 480, 90, -90), L((290, 310), (100, 310))),
              L((280, 310), (470, B))]
    g['S'] = [chain(arc(300, 505, 185, C - 505, 25, 270), arc(300, 215, 200, 290 - 145, 90, -155))]
    g['T'] = [L((60, C), (480, C)), L((270, C), (270, B))]
    g['U'] = [chain(L((100, C), (100, 260)), arc(290, 260, 190, 260 - B, 180, 360), L((480, 260), (480, C)))]
    g['V'] = [L((80, C), (290, B)), L((290, B), (500, C))]
    g['W'] = [L((80, C), (220, B)), L((220, B), (355, C - 200)), L((355, C - 200), (490, B)), L((490, B), (630, C))]
    g['X'] = [L((90, C), (480, B)), L((480, C), (90, B))]
    g['Y'] = [L((80, C), (280, 340)), L((280, 340), (480, C)), L((280, 340), (280, B))]
    g['Z'] = [L((90, C), (470, C)), L((470, C), (90, B)), L((90, B), (470, B))]
    # Digits
    g['0'] = [ellipse(270, CMID, 190, CMID - B)]
    g['1'] = [L((100, 500), (250, C)), L((250, C), (250, B))]
    g['2'] = [chain(arc(265, 480, 170, C - 480, 160, -30), L((400, 400), (95, B))), L((95, B), (440, B))]
    g['3'] = [chain(arc(255, 505, 150, C - 505, 150, -90), arc(255, 215, 165, 360 - 215, 90, -150))]
    g['4'] = [L((340, B), (340, C)), L((340, C), (80, 250)), L((80, 250), (470, 250))]
    g['5'] = [L((430, C), (140, C)), L((140, C), (120, 400)), arc(270, 240, 175, 400 - 240 + 10, 140, -145)]
    g['6'] = [ellipse(275, 235, 170, 235 - B), arc(345, 330, 235, C - 330, 75, 180)]
    g['7'] = [L((90, C), (460, C)), L((460, C), (200, B))]
    g['8'] = [ellipse(275, 510, 140, C - 510), ellipse(275, 215, 170, 360 - 215 + 5)]
    g['9'] = [ellipse(270, 485, 170, C - 485), arc(200, 390, 240, 390 - B, 0, -105)]
    # Punctuation
    g['.'] = [dot(100, B)]
    g[','] = [L((115, B + 10), (75, -110))]
    g[':'] = [dot(100, B), dot(100, X)]
    g[';'] = [L((115, B + 10), (75, -110)), dot(110, X)]
    g['!'] = [L((100, C), (100, 260)), dot(100, B)]
    g['?'] = [chain(arc(250, 490, 150, C - 490, 160, -60), L((300, 350), (250, 260))), dot(250, B)]
    g['-'] = [L((90, 300), (310, 300))]
    g['_'] = [L((60, -60), (460, -60))]
    g["'"] = [L((100, C), (100, 500))]
    g['"'] = [L((100, C), (100, 500)), L((260, C), (260, 500))]
    g['('] = [arc(330, CMID - 40, 210, C - CMID + 90, 120, 240)]
    g[')'] = [arc(-30, CMID - 40, 210, C - CMID + 90, 60, -60)]
    g['/'] = [L((80, -60), (380, C))]
    g['+'] = [L((90, 300), (390, 300)), L((240, 150), (240, 450))]
    g['='] = [L((90, 380), (390, 380)), L((90, 220), (390, 220))]
    g['&'] = [chain(L((480, B), (200, 450)), arc(270, 525, 105, C - 525, 225, -45), L((345, 450), (150, 250)),
                    arc(270, 210, 150, 210 - B, 165, 330), L((395, 125), (500, 300)))]
    return g


# ---------------------------------------------------------------- stroking
def circle(cx, cy, r, n=28):
    return [(cx + r * math.cos(2 * math.pi * k / n), cy + r * math.sin(2 * math.pi * k / n)) for k in range(n)]


def split_sharp(pts, limit_deg=35):
    """Splits a stroke where it turns sharply: each piece gets round caps,
    and the overlapping caps make a round join."""
    pieces = [[pts[0]]]
    for i in range(1, len(pts)):
        pieces[-1].append(pts[i])
        if 0 < i < len(pts) - 1:
            a = math.atan2(pts[i][1] - pts[i - 1][1], pts[i][0] - pts[i - 1][0])
            b = math.atan2(pts[i + 1][1] - pts[i][1], pts[i + 1][0] - pts[i][0])
            turn = abs((math.degrees(b - a) + 180) % 360 - 180)
            if turn > limit_deg:
                pieces.append([pts[i]])
    return [p for p in pieces if len(p) >= 2]


def stroke(pts, r=R):
    """Round-capped outline of a smooth polyline, as one closed contour."""
    if len(pts) == 1:
        return [(circle(pts[0][0], pts[0][1], r), False)]
    closed = math.dist(pts[0], pts[-1]) < 1
    if closed:
        pts = pts[:-1]
        n = len(pts)
        outer, inner = [], []
        for i in range(n):
            p0, p1, p2 = pts[i - 1], pts[i], pts[(i + 1) % n]
            nx, ny = _normal(p0, p1, p2)
            outer.append((p1[0] + nx * r, p1[1] + ny * r))
            inner.append((p1[0] - nx * r, p1[1] - ny * r))
        # Ring: the larger side is the outline, the smaller one the hole.
        if abs(area(outer)) < abs(area(inner)):
            outer, inner = inner, outer
        return [(outer, False), (inner, True)]
    contours = []
    for piece in split_sharp(pts):
        left, right = [], []
        n = len(piece)
        for i in range(n):
            p0 = piece[i - 1] if i > 0 else None
            p2 = piece[i + 1] if i < n - 1 else None
            nx, ny = _normal(p0, piece[i], p2)
            left.append((piece[i][0] + nx * r, piece[i][1] + ny * r))
            right.append((piece[i][0] - nx * r, piece[i][1] - ny * r))
        end_cap = _cap(piece[-1], piece[-2], r)
        start_cap = _cap(piece[0], piece[1], r)
        contours.append((left + end_cap + right[::-1] + start_cap, False))
    return contours


def _normal(p0, p1, p2):
    dirs = []
    for a, b in ((p0, p1), (p1, p2)):
        if a is not None and b is not None:
            dx, dy = b[0] - a[0], b[1] - a[1]
            l = math.hypot(dx, dy) or 1
            dirs.append((dx / l, dy / l))
    dx = sum(d[0] for d in dirs)
    dy = sum(d[1] for d in dirs)
    l = math.hypot(dx, dy) or 1
    tx, ty = dx / l, dy / l
    scale = 1.0
    if len(dirs) == 2:
        cosh = dirs[0][0] * tx + dirs[0][1] * ty
        scale = 1 / max(cosh, 0.5)
    return (-ty * scale, tx * scale)


def _cap(p, prev, r, n=12):
    """Half circle around p, from the left side to the right side of the stroke end."""
    a = math.atan2(p[1] - prev[1], p[0] - prev[0])
    return [(p[0] + r * math.cos(a + math.pi / 2 - math.pi * k / n),
             p[1] + r * math.sin(a + math.pi / 2 - math.pi * k / n)) for k in range(1, n)]


def area(c):
    return sum(c[i - 1][0] * c[i][1] - c[i][0] * c[i - 1][1] for i in range(len(c))) / 2


# ---------------------------------------------------------------- build
def build(out):
    sk = glyphs()
    order = ['.notdef', 'space'] + [f'g{ord(ch):04X}' for ch in sk]
    cmap = {32: 'space'}
    widths = {'.notdef': (500, 50), 'space': (260, 0)}
    outlines = {}
    for ch, strokes in sk.items():
        name = f'g{ord(ch):04X}'
        cmap[ord(ch)] = name
        contours = []
        for s in strokes:
            for c, hole in stroke(s):
                c = [(x + SLANT * y, y) for x, y in c]
                # TrueType: filled contours clockwise (negative area), holes counter-clockwise.
                if (area(c) > 0) != hole:
                    c = c[::-1]
                contours.append(c)
        xs = [p[0] for c in contours for p in c]
        shift = SIDE - min(xs)
        contours = [[(round(x + shift), round(y)) for x, y in c] for c in contours]
        outlines[name] = contours
        widths[name] = (round(max(xs) - min(xs) + 2 * SIDE), SIDE)

    glyf = {}
    for name in order:
        pen = TTGlyphPen(None)
        if name == '.notdef':
            for c in ([(50, 0), (50, 700), (450, 700), (450, 0)], [(100, 50), (400, 50), (400, 650), (100, 650)]):
                pen.moveTo(c[0])
                for p in c[1:]:
                    pen.lineTo(p)
                pen.closePath()
        elif name in outlines:
            for c in outlines[name]:
                pen.moveTo(c[0])
                for p in c[1:]:
                    pen.lineTo(p)
                pen.closePath()
        glyf[name] = pen.glyph()

    fb = FontBuilder(UPM, isTTF=True)
    fb.setupGlyphOrder(order)
    fb.setupCharacterMap(cmap)
    fb.setupGlyf(glyf)
    metrics = {}
    for name in order:
        adv, lsb = widths[name]
        metrics[name] = (adv, lsb)
    fb.setupHorizontalMetrics(metrics)
    fb.setupHorizontalHeader(ascent=900, descent=-260)
    notice = ('Copyright (C) 2026 Golden-Block. AGPL-3.0-or-later; '
              'commercial licensing: licence@golden-block.fr')
    fb.setupNameTable({
        'copyright': notice,
        'familyName': 'BuoyRound',
        'styleName': 'Regular',
        'uniqueFontIdentifier': 'Golden-Block: BuoyRound 1.0',
        'fullName': 'BuoyRound',
        'psName': 'BuoyRound-Regular',
        'version': 'Version 1.000',
        'manufacturer': 'Golden-Block',
        'licenseDescription': notice,
    })
    fb.setupOS2(sTypoAscender=900, sTypoDescender=-260, usWinAscent=900, usWinDescent=260,
                achVendID='GBLK', fsType=0)
    fb.setupPost()
    fb.save(out)


if __name__ == '__main__':
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), 'BuoyRound.ttf')
    build(out)
    print(out)
