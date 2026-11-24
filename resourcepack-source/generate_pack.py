#!/usr/bin/env python3
"""
Génère le resource pack de VortexEssentials : textures PEINTES en 32x32 (dégradés, reflets, halo, contour sombre)
pour tous les items custom (outils, armes légendaires et mythiques, armures mythiques, potions, éclat), + modèles.

Usage :  pip install pillow numpy  &&  python3 resourcepack-source/generate_pack.py
Sortie :  resourcepack/VortexEssentials-Pack.zip  (+ aperçu preview.png et SHA-1)

Chaque texture est dessinée en 256x256 (supersampling x8) puis réduite en 32x32, ce qui donne un rendu lissé et net.
Les arbalètes (Blazing Crossbow, Voidrender) restent en modèle vanilla : un modèle plat supprimerait l'animation de charge.
Le plugin applique ces modèles via le composant item_model ("vortex:<id>") quand use-custom-models: true dans config.yml.
L'apparence des armures PORTÉES reste celle du jeu : le pack habille leurs icônes (inventaire, main, sol).
"""
import hashlib, json, math, zipfile
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "resourcepack"
BUILD = OUT / "build"
PACK_FORMAT = 75          # Minecraft 1.21.11
N, S = 32, 4
POSTER = 4                # nombre de paliers d'ombrage (aspect pixel art)
W = N * S

OUTLINE = (14, 10, 22)
WOOD = (122, 82, 48)
WOOD_D = (74, 50, 28)
GOLD = (255, 205, 70)


def rgb(h):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255)


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c)


def add(a, b):
    return (a[0] + b[0], a[1] + b[1])


def sub(a, b):
    return (a[0] - b[0], a[1] - b[1])


def mul(a, k):
    return (a[0] * k, a[1] * k)


def norm(v):
    l = math.hypot(*v) or 1.0
    return (v[0] / l, v[1] / l)


def perp(v):
    return (-v[1], v[0])


def bez(p0, c, p1, n=18):
    return [((1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * c[0] + t * t * p1[0],
             (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * c[1] + t * t * p1[1]) for t in [i / n for i in range(n + 1)]]


def along(a, b, w0, w1):
    d = norm(sub(b, a))
    p = perp(d)
    return [add(a, mul(p, w0 / 2)), add(b, mul(p, w1 / 2)), add(b, mul(p, -w1 / 2)), add(a, mul(p, -w0 / 2))]


def band(cx, cy, ro, ri, a0, a1, taper=1.6, n=28):
    """Croissant entre deux arcs (angles en degrés, y vers le bas) ; la pointe se resserre vers a1."""
    outer, inner = [], []
    for i in range(n + 1):
        t = i / n
        a = math.radians(a0 + (a1 - a0) * t)
        r_in = ri + (ro - ri) * (t ** taper)
        outer.append((cx + ro * math.cos(a), cy + ro * math.sin(a)))
        inner.append((cx + r_in * math.cos(a), cy + r_in * math.sin(a)))
    return outer + inner[::-1]


class Art:
    def __init__(self):
        self.img = Image.new("RGBA", (W, W), (0, 0, 0, 0))
        self.aura = None
        self.sparks = []
        self.spark_col = None

    def _bbox(self, pts):
        xs = [p[0] * S for p in pts]
        ys = [p[1] * S for p in pts]
        return min(xs), min(ys), max(xs), max(ys)

    def _grad(self, c1, c2, angle, box):
        x0, y0, x1, y1 = box
        ys, xs = np.mgrid[0:W, 0:W]
        a = math.radians(angle)
        proj = xs * math.cos(a) + ys * math.sin(a)
        corners = [x0 * math.cos(a) + y0 * math.sin(a), x1 * math.cos(a) + y0 * math.sin(a),
                   x0 * math.cos(a) + y1 * math.sin(a), x1 * math.cos(a) + y1 * math.sin(a)]
        lo, hi = min(corners), max(corners)
        t = np.clip((proj - lo) / max(1e-6, hi - lo), 0, 1)
        t = np.clip(np.floor(t * POSTER) / (POSTER - 1), 0, 1)   # ombrage par paliers
        arr = np.zeros((W, W, 4), np.uint8)
        for k in range(3):
            arr[..., k] = (c1[k] + (c2[k] - c1[k]) * t).astype(np.uint8)
        arr[..., 3] = 255
        return Image.fromarray(arr, "RGBA")

    def _composite(self, fill, mask, alpha=255):
        if alpha < 255:
            mask = mask.point(lambda v: v * alpha // 255)
        layer = fill.copy()
        layer.putalpha(mask)
        self.img = Image.alpha_composite(self.img, layer)

    def poly(self, pts, c1, c2=None, angle=90, alpha=255):
        m = Image.new("L", (W, W), 0)
        ImageDraw.Draw(m).polygon([(x * S, y * S) for x, y in pts], fill=255)
        self._composite(self._grad(c1, c2 or c1, angle, self._bbox(pts)), m, alpha)

    def circle(self, cx, cy, r, c1, c2=None, angle=90, alpha=255):
        m = Image.new("L", (W, W), 0)
        ImageDraw.Draw(m).ellipse([(cx - r) * S, (cy - r) * S, (cx + r) * S, (cy + r) * S], fill=255)
        self._composite(self._grad(c1, c2 or c1, angle, ((cx - r) * S, (cy - r) * S, (cx + r) * S, (cy + r) * S)), m, alpha)

    def ring(self, cx, cy, r, width, col, alpha=255):
        m = Image.new("L", (W, W), 0)
        ImageDraw.Draw(m).ellipse([(cx - r) * S, (cy - r) * S, (cx + r) * S, (cy + r) * S], outline=255, width=int(width * S))
        self._composite(self._grad(col, col, 0, (0, 0, W, W)), m, alpha)

    def line(self, a, b, width, col, alpha=255):
        m = Image.new("L", (W, W), 0)
        d = ImageDraw.Draw(m)
        d.line([(a[0] * S, a[1] * S), (b[0] * S, b[1] * S)], fill=255, width=max(1, int(width * S)))
        r = width * S / 2
        for p in (a, b):
            d.ellipse([p[0] * S - r, p[1] * S - r, p[0] * S + r, p[1] * S + r], fill=255)
        self._composite(self._grad(col, col, 0, (0, 0, W, W)), m, alpha)

    def lines(self, pts, width, col, alpha=255):
        for a, b in zip(pts, pts[1:]):
            self.line(a, b, width, col, alpha)

    def star(self, cx, cy, size, col):
        pts = [(cx, cy - size), (cx + size * 0.28, cy - size * 0.28), (cx + size, cy), (cx + size * 0.28, cy + size * 0.28),
               (cx, cy + size), (cx - size * 0.28, cy + size * 0.28), (cx - size, cy), (cx - size * 0.28, cy - size * 0.28)]
        self.poly(pts, col)

    def fit(self, scale=0.9):
        sz = int(W * scale)
        sm = self.img.resize((sz, sz), Image.LANCZOS)
        im = Image.new("RGBA", (W, W), (0, 0, 0, 0))
        im.paste(sm, ((W - sz) // 2, (W - sz) // 2), sm)
        self.img = im

    def glow(self, col, radius=0, strength=0):
        self.aura = col

    def outline(self, col=OUTLINE, r=0):
        pass

    def rotate(self, deg, scale=1.0):
        im = self.img
        if scale != 1.0:
            sz = int(W * scale)
            sm = im.resize((sz, sz), Image.LANCZOS)
            im = Image.new("RGBA", (W, W), (0, 0, 0, 0))
            im.paste(sm, ((W - sz) // 2, (W - sz) // 2), sm)
        self.img = im.rotate(deg, resample=Image.BICUBIC, center=(W / 2, W / 2))

    def done(self):
        """Réduit en 32x32 net : alpha dur, biseau (lumière en haut à gauche), contour 1 px, aura de rareté, étincelles."""
        im = self.img.resize((N, N), Image.BOX)
        px = im.load()
        for y in range(N):
            for x in range(N):
                r, g, b, al = px[x, y]
                px[x, y] = (r, g, b, 255) if al >= 112 else (0, 0, 0, 0)
        src = im.copy()
        sp = src.load()

        def solid(x, y):
            return 0 <= x < N and 0 <= y < N and sp[x, y][3] > 0

        for y in range(N):
            for x in range(N):
                if sp[x, y][3] == 0:
                    continue
                r, g, b, _ = sp[x, y]
                if not solid(x + 1, y) or not solid(x, y + 1):
                    f = 0.68
                elif not solid(x - 1, y) or not solid(x, y - 1):
                    f = 1.3
                else:
                    continue
                px[x, y] = (min(255, int(r * f)), min(255, int(g * f)), min(255, int(b * f)), 255)

        out = Image.new("RGBA", (N, N), (0, 0, 0, 0))
        op = out.load()
        ocol = (shade(self.aura, 0.25) if self.aura else OUTLINE) + (255,)
        ring = set()
        for y in range(N):
            for x in range(N):
                if sp[x, y][3] == 0 and any(solid(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    op[x, y] = ocol
                    ring.add((x, y))
        if self.aura:
            near = set()
            for (x, y) in ring:
                for dx in (-1, 0, 1):
                    for dy in (-1, 0, 1):
                        q = (x + dx, y + dy)
                        if 0 <= q[0] < N and 0 <= q[1] < N and q not in ring and sp[q[0], q[1]][3] == 0:
                            near.add(q)
            for (x, y) in near:
                op[x, y] = self.aura + (150,)
            far = set()
            for (x, y) in near:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    q = (x + dx, y + dy)
                    if 0 <= q[0] < N and 0 <= q[1] < N and q not in ring and q not in near and sp[q[0], q[1]][3] == 0:
                        far.add(q)
            for (x, y) in far:
                op[x, y] = self.aura + (65,)
        res = Image.alpha_composite(out, im)
        rp = res.load()
        if self.spark_col:
            for (cx, cy) in self.sparks:
                for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
                    x, y = cx + dx, cy + dy
                    if 0 <= x < N and 0 <= y < N and rp[x, y][3] < 200:
                        rp[x, y] = (255, 255, 255, 255) if (dx, dy) == (0, 0) else self.spark_col + (230,)
        return res


# ───────────────────────── Éléments communs ─────────────────────────

def grip(a, p0, p1, w=2.3):
    a.poly(along(p0, p1, w, w), (150, 104, 62), WOOD_D, angle=0)
    d = norm(sub(p1, p0))
    L = math.hypot(*sub(p1, p0))
    for i in range(1, 4):
        c = add(p0, mul(d, L * i / 4))
        a.line(add(c, mul(perp(d), -w / 2)), add(c, mul(perp(d), w / 2)), 0.45, shade(WOOD_D, 0.8))


def metal(a, pts, base, light, dark, angle=0):
    a.poly(pts, light, dark, angle=angle)


def edge_line(a, p0, p1, col, w=0.55):
    a.line(p0, p1, w, col, 230)


# ───────────────────────── Formes ─────────────────────────

def sword(a, blade, light, dark, guard, gem, fuller=None):
    bl = along((11.5, 20.5), (28, 4), 5.0, 1.2)
    a.poly(bl, light, dark, angle=45)
    a.poly([(28, 4), (30.2, 1.8), (28.6, 5.4)], light, light)
    a.line((12.5, 19.5), (27.5, 4.5), 0.9, fuller or shade(light, 1.25), 200)
    edge_line(a, (13.2, 17.2), (28.6, 3.0), (255, 255, 255))
    a.poly(along((6.4, 18.2), (14.0, 25.8), 2.6, 2.6), guard, shade(guard, 0.6), angle=0)
    a.circle(10.2, 22.0, 1.6, gem, shade(gem, 0.6))
    a.circle(10.0, 21.7, 0.5, (255, 255, 255))
    grip(a, (9.0, 23.0), (4.6, 27.4))
    a.circle(3.6, 28.4, 2.0, guard, shade(guard, 0.6))


def pickaxe(a, head, light, dark, accent):
    grip(a, (4, 28), (22, 10), 2.4)
    E1, E2, C = (14.5, 3.0), (29.0, 17.5), (31.5, 0.5)
    outer = bez(E1, C, E2)
    inner = bez(E1, add(C, (-4.2, 4.2)), E2)
    a.poly(outer + inner[::-1], light, dark, angle=300)
    edge_line(a, (16.5, 3.4), (28.5, 8.5), (255, 255, 255))
    a.circle(22, 10, 1.6, accent, shade(accent, 0.6))
    a.circle(21.7, 9.7, 0.5, (255, 255, 255))


def axe(a, head, light, dark, accent):
    grip(a, (4, 28), (22, 10), 2.4)
    a.poly([(15.5, 11.5), (19.2, 7.6), (22.6, 10.4), (18.8, 14.4)], shade(dark, 0.9), shade(dark, 0.6), angle=45)   # talon
    E1, E2, C = (20.5, 3.8), (25.0, 17.2), (37.0, 7.0)
    outer = bez(E1, C, E2)
    inner = bez(E1, (24.0, 11.0), E2)
    a.poly(outer + inner[::-1], light, dark, angle=330)
    a.lines(outer[3:-2], 0.55, (255, 255, 255), 235)
    a.circle(21, 10.6, 1.5, accent, shade(accent, 0.6))
    a.circle(20.7, 10.3, 0.45, (255, 255, 255))


def shovel(a, head, light, dark, accent):
    grip(a, (4, 28), (20, 12), 2.4)
    a.poly(along((19.0, 13.0), (24.0, 8.0), 2.6, 6.0), shade(dark, 1.1), shade(dark, 0.8), angle=45)   # collet
    d = norm((1, -1))
    p = perp(d)
    base = (23.5, 8.5)
    pts = [add(base, mul(p, 3.0)), add(add(base, mul(d, 4.5)), mul(p, 4.7)), add(add(base, mul(d, 8.2)), mul(p, 2.4)),
           add(base, mul(d, 9.6)),
           add(add(base, mul(d, 8.2)), mul(p, -2.4)), add(add(base, mul(d, 4.5)), mul(p, -4.7)), add(base, mul(p, -3.0))]
    a.poly(pts, light, dark, angle=315)
    a.line(add(base, mul(d, 1.0)), add(base, mul(d, 8.4)), 0.7, (255, 255, 255), 150)
    a.lines([pts[1], pts[2], pts[3]], 0.5, (255, 255, 255), 200)
    a.circle(22.3, 9.7, 1.3, accent, shade(accent, 0.6))


def trident(a, head, light, dark, accent):
    grip(a, (4, 28), (20, 12), 2.0)
    a.poly(along((16, 6), (26, 16), 2.4, 2.4), accent, shade(accent, 0.6), angle=0)
    a.poly(along((19.5, 12.5), (29.8, 2.2), 3.0, 0.5), light, dark, angle=45)
    a.poly(along((16, 6), (21.5, 0.8), 2.2, 0.4), light, dark, angle=45)
    a.poly(along((26, 16), (31, 11), 2.2, 0.4), light, dark, angle=45)
    a.poly([(21.5, 0.8), (22.8, 3.6), (19.8, 2.8)], light, light)
    a.poly([(31, 11), (28.2, 9.6), (29.4, 13.2)], light, light)
    a.circle(21, 11, 1.5, accent, shade(accent, 0.6))


def spear(a, head, light, dark, accent):
    grip(a, (3.5, 28.5), (21, 11), 1.9)
    a.poly(along((13.5, 18.5), (15.3, 16.7), 2.6, 2.6), accent, shade(accent, 0.6), angle=0)
    d = norm((8.5, -8.5))
    p = perp(d)
    base = (21, 11)
    pts = [add(base, mul(p, 1.2)), add(add(base, mul(d, 3.0)), mul(p, 3.9)), add(add(base, mul(d, 8.0)), mul(p, 2.5)),
           (30.8, 1.2),
           add(add(base, mul(d, 8.0)), mul(p, -2.5)), add(add(base, mul(d, 3.0)), mul(p, -3.9)), add(base, mul(p, -1.2))]
    a.poly(pts, light, dark, angle=45)
    a.line((22.5, 9.5), (30, 2), 0.8, (255, 255, 255), 200)
    a.line((21.5, 10.5), (28.6, 3.6), 0.5, accent, 210)


def scythe(a, head, light, dark, accent, shaft=None):
    grip(a, (5.5, 28.5), (18, 6), 1.9)
    a.poly(band(19.5, 15.5, 13.5, 8.6, -108, -4, taper=1.5), light, dark, angle=330)
    edge_line(a, (15.5, 3.4), (29.8, 12.2), (255, 255, 255), 0.5)
    a.circle(18, 6.2, 1.5, accent, shade(accent, 0.6))
    a.circle(17.7, 5.9, 0.5, (255, 255, 255))


def mace(a, head, light, dark, accent):
    grip(a, (4.5, 27.5), (19.0, 13.0), 2.3)
    cx, cy = 23.4, 8.6
    for i in range(8):
        ang = math.radians(i * 45 + 22)
        tip = (cx + math.cos(ang) * 8.4, cy + math.sin(ang) * 8.4)
        l = (cx + math.cos(ang - 0.38) * 5.0, cy + math.sin(ang - 0.38) * 5.0)
        r = (cx + math.cos(ang + 0.38) * 5.0, cy + math.sin(ang + 0.38) * 5.0)
        a.poly([l, tip, r], light, dark, angle=45)
    a.circle(cx, cy, 5.6, light, dark, angle=45)
    a.ring(cx, cy, 3.6, 0.5, accent, 200)
    a.circle(cx, cy, 1.9, accent, shade(accent, 0.55))
    a.circle(cx - 0.4, cy - 0.5, 0.6, (255, 255, 255))


def club(a, head, light, dark, accent):
    grip(a, (4.5, 27.5), (11.5, 20.5), 2.3)
    a.poly(along((10.5, 21.5), (25.5, 6.5), 3.0, 9.4), light, dark, angle=45)
    a.circle(26, 6, 4.6, light, dark, angle=45)
    for p0, p1 in [((13.5, 18.5), (16.2, 18.8)), ((16.5, 15.5), (17.0, 12.2)), ((19.5, 12.8), (23.2, 13.6)),
                   ((22.5, 9.5), (22.0, 5.6)), ((25.0, 8.0), (28.6, 8.6))]:
        a.line(p0, p1, 0.75, accent)
        a.line(p0, p1, 0.3, (255, 240, 180))
    for q in [(20.4, 16.4), (27.5, 12.2), (24.2, 2.6)]:
        a.circle(q[0], q[1], 0.9, accent)


def wand(a, head, light, dark, accent):
    grip(a, (4.5, 28), (20.5, 12), 1.9)
    for t in (0.35, 0.65):
        c = add((4.5, 28), mul(sub((20.5, 12), (4.5, 28)), t))
        a.circle(c[0], c[1], 1.5, accent, shade(accent, 0.6))
    a.poly(along((20, 12.5), (28.5, 4), 3.4, 0.6), light, dark, angle=45)
    a.circle(24.6, 7.8, 1.6, (255, 255, 255), accent)
    a.ring(26.6, 5.6, 4.8, 0.5, accent, 190)


def sceptre(a, head, light, dark, accent):
    grip(a, (4.5, 28), (20, 13), 2.0)
    cx, cy = 24.2, 8.0
    a.circle(cx, cy, 6.8, light, dark, angle=45, alpha=150)
    for k in range(8):
        ang = math.radians(k * 45)
        a.line((cx, cy), (cx + math.cos(ang) * 6.8, cy + math.sin(ang) * 6.8), 0.45, (235, 235, 245), 230)
    for r in (2.6, 4.6):
        a.ring(cx, cy, r, 0.4, (235, 235, 245), 220)
    a.ring(cx, cy, 6.8, 0.9, dark, 255)
    a.circle(cx, cy, 2.2, accent, shade(accent, 0.5))
    a.circle(cx - 0.5, cy - 0.5, 0.6, (255, 255, 255))


def bow(a, wood, light, dark, accent, pull=0):
    A, B = (4.5, 4.5), (27.5, 27.5)
    C = (31.0, 1.0)
    curve = bez(A, C, B, 30)
    mid = (16.0, 16.0)
    pulled = (mid[0] - pull * 5.0, mid[1] + pull * 5.0)
    a.lines(curve, 3.0, shade(light, 0.55))
    a.lines(curve, 2.2, light)
    a.lines([(x - 0.2, y - 0.2) for x, y in curve[3:-3]], 0.8, (255, 255, 255), 150)
    apex = curve[15]
    a.poly(along((apex[0] - 1.6, apex[1] - 1.6), (apex[0] + 1.6, apex[1] + 1.6), 3.4, 3.4), WOOD, WOOD_D, angle=0)
    a.circle(apex[0], apex[1], 1.3, accent, shade(accent, 0.6))
    a.lines([A, pulled, B], 1.5, (30, 24, 44), 200)
    a.lines([A, pulled, B], 0.9, (252, 252, 255))
    for t in (0.18, 0.82):
        q = curve[int(30 * t)]
        a.circle(q[0], q[1], 1.1, accent, shade(accent, 0.6))
    a.circle(A[0], A[1], 1.1, accent)
    a.circle(B[0], B[1], 1.1, accent)
    if pull > 0:
        d = norm((1, -1))
        tail = pulled
        tip = add(apex, mul(d, 2.4 + (1 - pull) * 0.0))
        a.line(tail, tip, 0.8, WOOD)
        a.poly([add(tip, mul(d, 2.0)), add(tip, add(mul(perp(d), 1.3), mul(d, -0.2))), add(tip, add(mul(perp(d), -1.3), mul(d, -0.2)))], (235, 235, 245))
        a.line(tail, add(tail, add(mul(d, -1.6), mul(perp(d), 1.2))), 0.45, (240, 240, 240))
        a.line(tail, add(tail, add(mul(d, -1.6), mul(perp(d), -1.2))), 0.45, (240, 240, 240))


def crossbow(a, wood, light, dark, accent, pull=0.0, load=None):
    grip(a, (4.5, 27.5), (19.5, 12.5), 2.8)
    L, R, C = (11.5, 3.5), (28.5, 20.5), (31.5, 1.5)
    limb = bez(L, C, R, 26)
    nock = (19.5 - pull * 5.0, 12.5 + pull * 5.0)
    a.lines([L, nock, R], 0.9, (250, 250, 255))
    a.lines(limb, 3.2, shade(light, 0.55))
    a.lines(limb, 2.3, light)
    a.lines([(x - 0.2, y - 0.2) for x, y in limb[4:-4]], 0.75, (255, 255, 255), 150)
    a.poly(along((16.5, 15.5), (21.5, 10.5), 3.0, 3.0), shade(dark, 0.9), shade(dark, 0.6), angle=45)
    a.circle(18.5, 13.5, 1.2, accent, shade(accent, 0.6))
    a.circle(L[0], L[1], 1.1, accent)
    a.circle(R[0], R[1], 1.1, accent)
    if load == "arrow":
        a.line(nock, (27.0, 5.0), 0.8, WOOD)
        a.poly([(30.0, 2.0), (26.6, 3.2), (28.0, 6.4)], (235, 235, 245))
    elif load == "firework":
        a.line(nock, (25.0, 7.0), 2.2, (230, 70, 60))
        a.line(add(nock, (2.0, -2.0)), (25.0, 7.0), 0.8, (255, 255, 255))
        a.star(27.2, 4.8, 2.4, (255, 220, 90))


def lightning(a, p0, p1, col, n=7):
    d = norm(sub(p1, p0))
    pp = perp(d)
    L = math.hypot(*sub(p1, p0))
    pts = [p0]
    for i in range(1, n):
        c = add(p0, mul(d, L * i / n))
        pts.append(add(c, mul(pp, 1.3 if i % 2 else -1.3)))
    pts.append(p1)
    a.lines(pts, 0.85, col)
    a.lines(pts, 0.35, (255, 255, 255))


def deco_lightspeed(a): lightning(a, (13.5, 19.0), (27.0, 5.5), (0, 229, 255))
def deco_storm(a): lightning(a, (22.5, 9.5), (30.0, 2.5), (125, 249, 255), 5)


def deco_chrono(a):
    a.ring(10.2, 22.0, 3.0, 0.5, (255, 224, 130))
    a.line((10.2, 22.0), (10.2, 20.2), 0.45, (255, 255, 255))
    a.line((10.2, 22.0), (11.6, 22.6), 0.45, (255, 255, 255))


def deco_reaper(a):
    for (x, y, r) in ((9.5, 11.5, 1.1), (6.8, 15.5, 0.8), (13.0, 9.0, 0.7)):
        a.circle(x, y, r, (150, 255, 190), (60, 220, 130), alpha=200)


def deco_sealord(a):
    pts = [(10.5 + i * 1.6, 25.5 - i * 1.6 + (1.0 if i % 2 else -1.0) * 0.8) for i in range(8)]
    a.lines(pts, 0.55, (170, 245, 255), 200)


def deco_umbral(a):
    a.ring(23.4, 8.6, 7.6, 0.35, (210, 160, 255), 150)


DECOR = {"lightspeed": deco_lightspeed, "stormpiercer": deco_storm, "chrono_sword": deco_chrono,
         "reaper_scythe": deco_reaper, "sealord": deco_sealord, "umbral_scythe": deco_umbral}


def helmet(a, main, light, dark, accent, crown=False):
    body = [(7, 23), (6, 14), (9, 8), (16, 5.5), (23, 8), (26, 14), (25, 23), (20.6, 23), (19.6, 17.5), (12.4, 17.5), (11.4, 23)]
    a.poly(body, light, dark, angle=70)
    a.poly([(11.2, 14.4), (20.8, 14.4), (20.2, 16.4), (11.8, 16.4)], (20, 16, 30), (40, 34, 56))
    a.line((16, 16.4), (16, 19.6), 0.9, shade(light, 0.8))
    a.line((9.5, 12), (15, 7.8), 0.9, (255, 255, 255), 130)
    a.line((16, 6), (16, 14), 0.7, accent, 220)
    if crown:
        for x, h in ((8.5, 4), (12.2, 7), (16, 9), (19.8, 7), (23.5, 4)):
            a.poly([(x - 1.9, 10.5), (x, 10.5 - h), (x + 1.9, 10.5)], accent, shade(accent, 0.7), angle=90)
            a.circle(x, 10.5 - h, 0.9, (255, 255, 255))
        a.poly([(7.2, 12.4), (24.8, 12.4), (25.2, 10.2), (6.8, 10.2)], accent, shade(accent, 0.65), angle=90)
    else:
        a.circle(16, 9.8, 1.6, accent, shade(accent, 0.6))


def chestplate(a, main, light, dark, accent):
    body = [(5.5, 7), (11, 4), (14, 7.5), (18, 7.5), (21, 4), (26.5, 7), (29, 13), (24.6, 14), (24.6, 27.5), (7.4, 27.5),
            (7.4, 14), (3, 13)]
    a.poly(body, light, dark, angle=70)
    a.poly([(11, 4), (14, 7.5), (18, 7.5), (21, 4), (18.5, 5.8), (13.5, 5.8)], shade(light, 1.15), light)
    a.circle(16, 16, 3.6, accent, shade(accent, 0.55))
    a.circle(15.3, 15.2, 1.0, (255, 255, 255))
    a.ring(16, 16, 5.2, 0.5, shade(light, 1.3), 220)
    a.line((9, 23), (23, 23), 0.7, shade(dark, 0.8), 200)
    a.line((8.4, 9), (11.6, 7), 0.8, (255, 255, 255), 130)


def leggings(a, main, light, dark, accent):
    body = [(8, 4), (24, 4), (25.5, 28.5), (18.2, 28.5), (16, 13.5), (13.8, 28.5), (6.5, 28.5)]
    a.poly(body, light, dark, angle=70)
    a.poly([(8, 4), (24, 4), (24.4, 8.4), (7.6, 8.4)], shade(light, 0.85), shade(dark, 0.9), angle=90)
    a.circle(16, 6.2, 1.7, accent, shade(accent, 0.55))
    a.line((9.5, 14), (9.5, 26), 0.7, (255, 255, 255), 110)
    a.line((22.5, 14), (22.5, 26), 0.7, shade(dark, 0.7), 150)
    a.ring(11, 18, 1.3, 0.45, accent, 220)
    a.ring(21, 18, 1.3, 0.45, accent, 220)


def boots(a, main, light, dark, accent):
    for ox in (0, 14.5):
        b = [(5 + ox, 7), (12 + ox, 7), (12 + ox, 20), (14.8 + ox, 23.5), (14.8 + ox, 28.2), (3.6 + ox, 28.2), (3.6 + ox, 23)]
        a.poly(b, light, dark, angle=70)
        a.poly([(3.6 + ox, 24.4), (14.8 + ox, 24.4), (14.8 + ox, 28.2), (3.6 + ox, 28.2)], shade(dark, 0.7), shade(dark, 0.5))
        a.poly([(5 + ox, 7), (12 + ox, 7), (12 + ox, 10), (5 + ox, 10)], accent, shade(accent, 0.65))
        a.line((5.8 + ox, 12), (5.8 + ox, 20), 0.7, (255, 255, 255), 120)
    for ox in (0, 14.5):
        a.line((3 + ox + 6, 22), (3 + ox + 9, 22), 0.7, accent, 220)


def bottle(a, liquid, light, accent, splash=False, halo=False, dots=None):
    glass_t, glass_b = (225, 238, 248), (170, 195, 215)
    neck = [(13.2, 5.5), (18.8, 5.5), (18.8, 12.5), (13.2, 12.5)]
    a.circle(16, 20.4, 9.2, glass_t, glass_b, angle=60, alpha=235)
    a.poly(neck, glass_t, glass_b, angle=0, alpha=235)
    m = Image.new("L", (W, W), 0)
    ImageDraw.Draw(m).ellipse([(16 - 8.0) * S, (20.4 - 8.0) * S, (16 + 8.0) * S, (20.4 + 8.0) * S], fill=255)
    cut = Image.new("L", (W, W), 0)
    ImageDraw.Draw(cut).rectangle([0, int(18.2 * S), W, W], fill=255)
    from PIL import ImageChops
    liq_mask = ImageChops.multiply(m, cut)
    a._composite(a._grad(light, liquid, 90, (0, 18 * S, W, 29 * S)), liq_mask)
    a.line((9.4, 18.4), (22.6, 18.4), 0.55, light, 230)
    a.poly([(12.5, 3.5), (19.5, 3.5), (19.0, 6.4), (13.0, 6.4)], (176, 128, 74), (110, 74, 40), angle=0)
    a.line((12.7, 4.6), (19.3, 4.6), 0.4, (90, 60, 32))
    a.line((10.6, 15.5), (9.6, 21.5), 0.9, (255, 255, 255), 190)
    a.circle(11.6, 23.8, 0.7, (255, 255, 255), alpha=170)
    if dots:
        for (x, y, col) in dots:
            a.circle(x, y, 1.15, col, shade(col, 0.7))
            a.circle(x - 0.3, y - 0.3, 0.35, (255, 255, 255))
    else:
        a.circle(18.5, 23.5, 1.0, accent)
        a.circle(14.2, 25.6, 0.7, accent)
    if splash:
        for (x, y, r) in ((5.5, 10, 1.0), (26.5, 12.5, 1.3), (4.2, 24, 0.8), (27.4, 25, 0.9), (22.8, 4.5, 0.8)):
            a.circle(x, y, r, liquid, light)
    if halo:
        a.ring(16, 2.0, 5.2, 0.75, GOLD)
        a.ring(16, 2.0, 5.2, 0.28, (255, 250, 210))
        a.star(5.2, 6.8, 2.0, (255, 245, 190))
        a.star(26.8, 7.8, 1.6, (255, 245, 190))


def shard(a, main, light, dark, accent):
    facets = [
        ([(16, 2), (23, 10), (16, 14), (9, 10)], light, main),
        ([(9, 10), (16, 14), (16, 30), (6, 15)], main, dark),
        ([(23, 10), (26, 15), (16, 30), (16, 14)], shade(main, 1.15), shade(dark, 0.9)),
    ]
    for pts, c1, c2 in facets:
        a.poly(pts, c1, c2, angle=60)
    a.line((16, 2.5), (16, 29), 0.45, (255, 255, 255), 110)
    a.line((9.6, 10.4), (22.4, 10.4), 0.45, (255, 255, 255), 150)
    a.star(21.5, 21, 2.2, accent)
    a.star(10.5, 22.5, 1.5, (255, 255, 255))


SHAPES = {"sword": sword, "pickaxe": pickaxe, "axe": axe, "shovel": shovel, "trident": trident, "spear": spear,
          "scythe": scythe, "mace": mace, "club": club, "wand": wand, "sceptre": sceptre}

# id : (forme, métal principal, éclat, accent, rang)
ITEMS = {
    "smelter_pickaxe": ("pickaxe", 0xe8731c, 0xffb347, 0xffd34d, "tool"),
    "super_smelter_pickaxe": ("pickaxe", 0xf0a020, 0xffd966, 0xff5a1f, "tool"),
    "netherite_smelter_pickaxe": ("pickaxe", 0x6b1f1f, 0xb5472f, 0xff8a3d, "tool"),
    "explosive_pickaxe": ("pickaxe", 0xc62828, 0xff6e60, 0xffee58, "tool"),
    "netherite_explosive_pickaxe": ("pickaxe", 0x3a1414, 0x8a2b2b, 0xff5252, "tool"),
    "lumberjack_axe": ("axe", 0x4caf50, 0x9be39e, 0xe8c07a, "tool"),
    "super_lumberjack_axe": ("axe", 0x1fa88a, 0x7be6cf, 0xe8c07a, "tool"),
    "netherite_lumberjack_axe": ("axe", 0x1b4d3e, 0x3f8f78, 0xe8c07a, "tool"),
    "fractured": ("pickaxe", 0x35d4e8, 0xbff6ff, 0xffffff, "mythic"),
    "terraloop": ("axe", 0x2ecc71, 0xa8f5c6, 0xf1c40f, "mythic"),
    "jugas": ("shovel", 0xf5b020, 0xffe08a, 0xff7a1a, "mythic"),
    "reaper_scythe": ("scythe", 0x6a2c91, 0xb98ae0, 0x39ff88, "legend"),
    "excalibur": ("sword", 0xf2d16b, 0xffffff, 0x4aa3ff, "legend"),
    "evoker_wand": ("wand", 0x2a9d8f, 0x8be9dd, 0xe9c46a, "legend"),
    "chrono_sword": ("sword", 0x3a86ff, 0xa8d0ff, 0xffd166, "legend"),
    "sceptre_of_arachne": ("sceptre", 0x3b2a4d, 0x8e6fb0, 0x6dff8a, "legend"),
    "magma_club": ("club", 0x3a2a26, 0x7a5a4e, 0xff6a1a, "legend"),
    "sealord": ("trident", 0x16b8c9, 0xa6f4ff, 0xffd700, "mythic"),
    "lightspeed": ("sword", 0xfff176, 0xffffff, 0x00e5ff, "mythic"),
    "mintus": ("axe", 0x43a047, 0xb9f6ca, 0xffd700, "mythic"),
    "umbral_scythe": ("mace", 0x4a2a7a, 0xa070e0, 0xc77dff, "secret"),
    "stormpiercer": ("spear", 0x9fd8ff, 0xffffff, 0x7df9ff, "mythic"),
}
ARMORS = {
    "radiant_crown": ("helmet", 0xf5c542, 0xfff2a8, 0xffffff, 0xffe066, "mythic"),
    "colossus_plate": ("chestplate", 0x8a8f9c, 0xd6dbe6, 0x4a4f5e, 0xff5a4f, "mythic"),
    "ember_greaves": ("leggings", 0x7a2a1c, 0xe0623a, 0x3a120c, 0xffa726, "mythic"),
    "phantom_treads": ("boots", 0x4b3a78, 0xa58be0, 0x241a45, 0x7df9ff, "mythic"),
}
BOTTLES = {
    "panacea": dict(liquid=rgb(0xff4fa0), light=rgb(0xffc1e3), accent=(255, 255, 255), splash=False),
    "agony": dict(liquid=rgb(0x7b1fa2), light=rgb(0xd38cf5), accent=rgb(0x39ff88), splash=True),
    "all_in_one": dict(liquid=rgb(0xff9a1f), light=rgb(0xffd27a), accent=(255, 255, 255), splash=True,
                       dots=[(12.4, 22.8, rgb(0xd32f2f)), (18.8, 23.6, rgb(0x00d4ff)), (15.6, 26.2, rgb(0xff7a00))]),
    "holy_master": dict(liquid=rgb(0xffe27a), light=rgb(0xfffbd0), accent=(255, 255, 255), splash=True, halo=True),
}
CROSSBOWS = {"blazing_crossbow": (0x8a2a14, 0xff7a2a, 0xffd34d), "voidrender": (0x2a1450, 0x8a5cf0, 0x7df9ff)}
GLOW_OVERRIDE = {"jugas": (255, 110, 30), "lightspeed": (80, 230, 255), "stormpiercer": (110, 215, 255), "mintus": (90, 255, 140),
                 "fractured": (80, 230, 255), "terraloop": (110, 255, 150), "sealord": (60, 200, 255), "reaper_scythe": (110, 255, 160),
                 "excalibur": (255, 235, 150), "chrono_sword": (90, 150, 255), "magma_club": (255, 90, 20)}
GLOW = {"legend": (255, 150, 30), "mythic": (255, 215, 70), "secret": (170, 90, 255)}
SPARK = {"legend": (255, 190, 60), "mythic": (255, 240, 150), "secret": (230, 190, 255)}


def sparkles(a, rank):
    if rank not in SPARK:
        return
    col = SPARK[rank]
    for (x, y, s) in ((28.0, 5.0, 2.2), (5.0, 8.5, 1.4), (26.5, 25.0, 1.3)):
        a.star(x, y, s, col)


def make_tool(name, spec):
    shape, m, l, ac, rank = spec
    a = Art()
    main, light, accent = rgb(m), rgb(l), rgb(ac)
    SHAPES[shape](a, main, light, shade(main, 0.55), accent) if shape not in ("scythe", "mace", "club", "wand", "sceptre") else \
        SHAPES[shape](a, main, lerp(light, main, 0.15), shade(main, 0.5), accent)
    a.fit(0.9)
    DECOR[name](a) if name in DECOR else None
    a.fit(0.9) if False else None
    if rank in GLOW:
        a.glow(GLOW_OVERRIDE.get(name, GLOW[rank]), 1.5, 0.55)
    sparkles(a, rank)
    a.outline()
    return a.done()


def make_sword_like(name, spec):
    shape, m, l, ac, rank = spec
    a = Art()
    main, light, accent = rgb(m), rgb(l), rgb(ac)
    guard = WOOD if False else shade(accent, 0.9)
    sword(a, main, lerp(light, main, 0.2), shade(main, 0.55), guard, accent)
    DECOR[name](a) if name in DECOR else None
    a.fit(0.9)
    if rank in GLOW:
        a.glow(GLOW_OVERRIDE.get(name, GLOW[rank]), 1.5, 0.55)
    sparkles(a, rank)
    a.outline()
    return a.done()


def make_armor(name, spec):
    kind, m, l, d, ac, rank = spec
    a = Art()
    main, light, dark, accent = rgb(m), rgb(l), rgb(d), rgb(ac)
    if kind == "helmet":
        helmet(a, main, light, shade(main, 0.6), accent, crown=True)
    elif kind == "chestplate":
        chestplate(a, main, light, dark, accent)
    elif kind == "leggings":
        leggings(a, main, light, dark, accent)
    else:
        boots(a, main, light, dark, accent)
    a.fit(0.9)
    a.glow(GLOW[rank], 1.5, 0.55)
    sparkles(a, rank)
    a.outline()
    return a.done()


def make_bottle(name, kw):
    a = Art()
    bottle(a, **kw)
    a.fit(0.92)
    a.glow(rgb(0xffe27a) if name == "holy_master" else kw["liquid"], 1.4, 0.5)
    if name in ("holy_master", "all_in_one"):
        sparkles(a, "mythic")
    a.outline()
    return a.done()


def make_shard():
    a = Art()
    shard(a, rgb(0xb06ae0), rgb(0xe4c7ff), rgb(0x5a2a8a), rgb(0xffffff))
    a.fit(0.92)
    a.glow((190, 110, 255), 1.5, 0.55)
    a.outline()
    return a.done()


def make_bow(pull):
    a = Art()
    bow(a, WOOD, rgb(0x8fe3ff), rgb(0x2a5d7a), rgb(0xffd54f), pull=pull)
    a.fit(0.92)
    a.glow(GLOW["mythic"], 1.4, 0.5)
    a.outline()
    return a.done()


def make_crossbow(spec, pull=0.0, load=None, glow=None):
    a = Art()
    m, l, ac = (rgb(x) for x in spec)
    crossbow(a, m, l, shade(m, 0.55), ac, pull=pull, load=load)
    a.fit(0.92)
    a.glow(glow or (255, 150, 30), 1.4, 0.5)
    a.outline()
    return a.done()


# ───── textures des armures PORTÉES (calques 64x32) ─────
def _rect(img, r, c1, c2, accent=None):
    x, y, w, h = r
    d = ImageDraw.Draw(img)
    for i in range(h):
        d.rectangle([x, y + i, x + w - 1, y + i], fill=lerp(c1, c2, i / max(1, h - 1)) + (255,))
    d.line([x, y, x + w - 1, y], fill=shade(c1, 1.25) + (255,))
    d.line([x, y, x, y + h - 1], fill=shade(c1, 1.12) + (255,))
    d.line([x, y + h - 1, x + w - 1, y + h - 1], fill=shade(c2, 0.7) + (255,))
    d.line([x + w - 1, y, x + w - 1, y + h - 1], fill=shade(c2, 0.8) + (255,))


def armor_layer(kind, main, light, dark, accent):
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    acc = accent + (255,)
    wh = (255, 255, 255, 255)
    if kind == "helmet":
        for r in ((8, 0, 8, 8), (16, 0, 8, 8), (0, 8, 8, 8), (8, 8, 8, 8), (16, 8, 8, 8), (24, 8, 8, 8)):
            _rect(img, r, light, shade(main, 0.8))
        d.rectangle([8, 12, 15, 13], fill=(20, 16, 30, 255))                    # visière
        d.line([9, 12, 14, 12], fill=shade(accent, 0.9) + (255,))
        d.rectangle([8, 9, 15, 9], fill=acc)                                     # bandeau
        d.rectangle([11, 8, 12, 9], fill=wh)                                     # gemme frontale
        for x0 in (0, 16, 24):
            d.rectangle([x0, 9, x0 + 7, 9], fill=acc)
        d.rectangle([8, 3, 15, 4], fill=acc)                                     # sommet orné
    elif kind == "chestplate":
        for r in ((20, 16, 8, 4), (28, 16, 8, 4), (16, 20, 4, 12), (20, 20, 8, 12), (28, 20, 4, 12), (32, 20, 8, 12)):
            _rect(img, r, light, shade(main, 0.75))
        for r in ((44, 16, 4, 4), (48, 16, 4, 4), (40, 20, 4, 12), (44, 20, 4, 12), (48, 20, 4, 12), (52, 20, 4, 12)):
            _rect(img, r, light, shade(main, 0.7))
        for x0 in (40, 44, 48, 52):
            d.rectangle([x0, 20, x0 + 3, 23], fill=shade(accent, 0.8) + (255,))   # épaulières
        d.ellipse([22, 22, 25, 25], fill=acc)
        d.point([22, 22], fill=wh)
        d.rectangle([22, 27, 25, 27], fill=shade(dark, 0.8) + (255,))
        d.rectangle([20, 31, 27, 31], fill=shade(accent, 0.8) + (255,))
    elif kind == "boots":
        for r in ((4, 16, 4, 4), (8, 16, 4, 4)):
            pass
        for x0 in (0, 4, 8, 12):
            _rect(img, (x0, 27, 4, 5), shade(main, 0.9), shade(dark, 0.9))
            d.rectangle([x0, 27, x0 + 3, 27], fill=acc)                           # revers
            d.rectangle([x0, 31, x0 + 3, 31], fill=shade(dark, 0.55) + (255,))    # semelle
        d.rectangle([4, 29, 7, 29], fill=shade(accent, 0.8) + (255,))
    return img


def leggings_layer(main, light, dark, accent):
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    acc = accent + (255,)
    for r in ((20, 16, 8, 4), (28, 16, 8, 4), (16, 20, 4, 12), (20, 20, 8, 12), (28, 20, 4, 12), (32, 20, 8, 12)):
        _rect(img, r, light, shade(main, 0.75))
    for x0 in (16, 20, 28, 32):
        w = 4 if x0 in (16, 28) else 8
        d.rectangle([x0, 20, x0 + w - 1, 22], fill=shade(accent, 0.75) + (255,))     # ceinture
    d.rectangle([23, 20, 24, 22], fill=acc)                                           # boucle
    for r in ((4, 16, 4, 4), (8, 16, 4, 4), (0, 20, 4, 12), (4, 20, 4, 12), (8, 20, 4, 12), (12, 20, 4, 12)):
        _rect(img, r, light, shade(main, 0.7))
    for x0 in (0, 4, 8, 12):
        d.rectangle([x0, 26, x0 + 3, 27], fill=acc)                                   # genouillères
        d.line([x0 + 1, 21, x0 + 1, 31], fill=shade(dark, 0.9) + (255,))
    return img


HANDHELD = set(ITEMS) | set(CROSSBOWS)
BOW_DISPLAY = {
    "thirdperson_righthand": {"rotation": [-80, 260, -40], "translation": [-1, -2, 2.5], "scale": [0.9, 0.9, 0.9]},
    "thirdperson_lefthand": {"rotation": [-80, -280, 40], "translation": [-1, -2, 2.5], "scale": [0.9, 0.9, 0.9]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
}


def _finish(a, name, rank, fit=0.84, aura=None):
    a.fit(fit)
    a.aura = aura if aura is not None else (GLOW_OVERRIDE.get(name, GLOW.get(rank)) if rank else None)
    if rank in SPARK:
        a.sparks = [(27, 4), (4, 8), (26, 26)]
        a.spark_col = SPARK[rank]
    return a.done()


def make_tool(name, spec):
    shape, m, l, ac, rank = spec
    a = Art()
    main, light, accent = rgb(m), rgb(l), rgb(ac)
    if shape in ("scythe", "mace", "club", "wand", "sceptre"):
        SHAPES[shape](a, main, lerp(light, main, 0.15), shade(main, 0.5), accent)
    else:
        SHAPES[shape](a, main, light, shade(main, 0.55), accent)
    if name in DECOR:
        DECOR[name](a)
    return _finish(a, name, rank)


def make_sword_like(name, spec):
    shape, m, l, ac, rank = spec
    a = Art()
    main, light, accent = rgb(m), rgb(l), rgb(ac)
    sword(a, main, lerp(light, main, 0.2), shade(main, 0.55), shade(accent, 0.9), accent)
    if name in DECOR:
        DECOR[name](a)
    return _finish(a, name, rank)


def make_armor(name, spec):
    kind, m, l, d, ac, rank = spec
    a = Art()
    main, light, dark, accent = rgb(m), rgb(l), rgb(d), rgb(ac)
    if kind == "helmet":
        helmet(a, main, light, shade(main, 0.6), accent, crown=True)
    elif kind == "chestplate":
        chestplate(a, main, light, dark, accent)
    elif kind == "leggings":
        leggings(a, main, light, dark, accent)
    else:
        boots(a, main, light, dark, accent)
    return _finish(a, name, rank)


def make_bottle(name, kw):
    a = Art()
    bottle(a, **kw)
    return _finish(a, name, "mythic" if name in ("holy_master", "all_in_one") else None, 0.9,
                   aura=(255, 226, 122) if name == "holy_master" else kw["liquid"])


def make_shard():
    a = Art()
    shard(a, rgb(0xb06ae0), rgb(0xe4c7ff), rgb(0x5a2a8a), rgb(0xffffff))
    return _finish(a, "crystallization_shard", None, 0.9, aura=(190, 110, 255))


def make_bow(pull):
    a = Art()
    bow(a, WOOD, rgb(0x8fe3ff), rgb(0x2a5d7a), rgb(0xffd54f), pull=pull)
    return _finish(a, "skyfall", "mythic", 0.9, aura=(255, 215, 70))


def make_crossbow(spec, pull=0.0, load=None, glow=None):
    a = Art()
    m, l, ac = (rgb(x) for x in spec)
    crossbow(a, m, l, shade(m, 0.55), ac, pull=pull, load=load)
    return _finish(a, "crossbow", "legend", 0.9, aura=glow or (255, 150, 30))


def wjson(path, obj):
    path.write_text(json.dumps(obj, indent=2, ensure_ascii=False), encoding="utf-8")


def build():
    if BUILD.exists():
        for p in sorted(BUILD.rglob("*"), reverse=True):
            p.unlink() if p.is_file() else p.rmdir()
    tex = BUILD / "assets/vortex/textures/item"
    mod = BUILD / "assets/vortex/models/item"
    itm = BUILD / "assets/vortex/items"
    for d in (tex, mod, itm):
        d.mkdir(parents=True, exist_ok=True)

    sprites = {}
    for name, spec in ITEMS.items():
        sprites[name] = make_sword_like(name, spec) if spec[0] == "sword" else make_tool(name, spec)
    for name, spec in ARMORS.items():
        sprites[name] = make_armor(name, spec)
    for name, kw in BOTTLES.items():
        sprites[name] = make_bottle(name, kw)
    sprites["crystallization_shard"] = make_shard()
    sprites["skyfall"] = make_bow(0)
    cross_states = {}
    for cid, spec in CROSSBOWS.items():
        gl = (255, 120, 30) if cid == "blazing_crossbow" else (150, 100, 255)
        sprites[cid] = make_crossbow(spec, 0.0, None, gl)
        for i, pl in enumerate((0.2, 0.55, 1.0)):
            cross_states[f"{cid}_pulling_{i}"] = make_crossbow(spec, pl, None, gl)
        cross_states[f"{cid}_arrow"] = make_crossbow(spec, 0.5, "arrow", gl)
        cross_states[f"{cid}_firework"] = make_crossbow(spec, 0.5, "firework", gl)
    bow_states = {f"skyfall_pulling_{i}": make_bow(p) for i, p in enumerate((0.25, 0.6, 1.0))}

    for name, img in {**sprites, **bow_states, **cross_states}.items():
        img.save(tex / f"{name}.png")
        if name in HANDHELD:
            parent = "minecraft:item/handheld"
        else:
            parent = "minecraft:item/generated"
        model = {"parent": parent, "textures": {"layer0": f"vortex:item/{name}"}}
        if name.startswith("skyfall"):
            model["display"] = BOW_DISPLAY
        wjson(mod / f"{name}.json", model)
    for name in sprites:
        if name == "skyfall" or name in CROSSBOWS:
            continue
        wjson(itm / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"vortex:item/{name}"}})
    # arc : états de tension de la corde
    wjson(itm / "skyfall.json", {"model": {
        "type": "minecraft:condition", "property": "minecraft:using_item",
        "on_false": {"type": "minecraft:model", "model": "vortex:item/skyfall"},
        "on_true": {"type": "minecraft:range_dispatch", "property": "minecraft:use_duration", "scale": 0.05,
                    "entries": [
                        {"threshold": 0.65, "model": {"type": "minecraft:model", "model": "vortex:item/skyfall_pulling_1"}},
                        {"threshold": 0.9, "model": {"type": "minecraft:model", "model": "vortex:item/skyfall_pulling_2"}}],
                    "fallback": {"type": "minecraft:model", "model": "vortex:item/skyfall_pulling_0"}}}})

    for cid in CROSSBOWS:  # arbalète : tension, puis flèche / feu d'artifice chargés
        mdl = lambda n: {"type": "minecraft:model", "model": f"vortex:item/{n}"}
        wjson(itm / f"{cid}.json", {"model": {
            "type": "minecraft:condition", "property": "minecraft:using_item",
            "on_false": {"type": "minecraft:select", "property": "minecraft:charge_type",
                         "cases": [{"when": "arrow", "model": mdl(f"{cid}_arrow")}, {"when": "rocket", "model": mdl(f"{cid}_firework")}],
                         "fallback": mdl(cid)},
            "on_true": {"type": "minecraft:range_dispatch", "property": "minecraft:crossbow/pull",
                        "entries": [{"threshold": 0.58, "model": mdl(f"{cid}_pulling_1")}, {"threshold": 1.0, "model": mdl(f"{cid}_pulling_2")}],
                        "fallback": mdl(f"{cid}_pulling_0")}}})

    # armures portées : équipement + calques humanoid / humanoid_leggings
    eq_dir = BUILD / "assets/vortex/equipment"
    t1 = BUILD / "assets/vortex/textures/entity/equipment/humanoid"
    t2 = BUILD / "assets/vortex/textures/entity/equipment/humanoid_leggings"
    for d_ in (eq_dir, t1, t2):
        d_.mkdir(parents=True, exist_ok=True)
    layers = {}
    for name, (kind, m, l, d, ac, rank) in ARMORS.items():
        main, light, dark, accent = rgb(m), rgb(l), rgb(d), rgb(ac)
        if kind == "leggings":
            img = leggings_layer(main, light, dark, accent)
            img.save(t2 / f"{name}.png")
            wjson(eq_dir / f"{name}.json", {"layers": {"humanoid_leggings": [{"texture": f"vortex:{name}"}]}})
        else:
            img = armor_layer(kind, main, light, dark, accent)
            img.save(t1 / f"{name}.png")
            wjson(eq_dir / f"{name}.json", {"layers": {"humanoid": [{"texture": f"vortex:{name}"}]}})
        layers[name] = img
    sheet2 = Image.new("RGBA", (4 * 64 * 4 + 50, 32 * 4 + 20), (60, 56, 80, 255))
    for i, (name, img) in enumerate(layers.items()):
        big = img.resize((64 * 4, 32 * 4), Image.NEAREST)
        sheet2.paste(big, (10 + i * (64 * 4 + 10), 10), big)
    OUT.mkdir(exist_ok=True)
    sheet2.save(OUT / "preview_armor.png")

    wjson(BUILD / "pack.mcmeta", {"pack": {
        "description": "§bVortexSMP §7— textures des items custom",
        "pack_format": PACK_FORMAT, "min_format": PACK_FORMAT, "max_format": 999}})

    # icône du pack
    icon = Image.new("RGBA", (128, 128), (22, 18, 38, 255))
    for i, name in enumerate(("excalibur", "stormpiercer", "umbral_scythe", "holy_master")):
        big = sprites[name].resize((60, 60), Image.NEAREST)
        icon.paste(big, (3 + (i % 2) * 63, 3 + (i // 2) * 63), big)
    icon.save(BUILD / "pack.png")

    # aperçu (planche contact)
    cols = 8
    names = list(sprites)
    rows = (len(names) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * 132, rows * 132), (38, 34, 54, 255))
    for i, name in enumerate(names):
        big = sprites[name].resize((120, 120), Image.NEAREST)
        sheet.paste(big, ((i % cols) * 132 + 6, (i // cols) * 132 + 6), big)
    OUT.mkdir(exist_ok=True)
    sheet.save(OUT / "preview.png")

    zpath = OUT / "VortexEssentials-Pack.zip"
    with zipfile.ZipFile(zpath, "w", zipfile.ZIP_DEFLATED) as z:
        for p in sorted(BUILD.rglob("*")):
            if p.is_file():
                z.write(p, p.relative_to(BUILD).as_posix())
    sha1 = hashlib.sha1(zpath.read_bytes()).hexdigest()
    (OUT / "SHA1.txt").write_text(sha1 + "\n", encoding="utf-8")
    print(f"OK : {zpath} ({len(sprites)} items) — SHA-1 {sha1}")


if __name__ == "__main__":
    build()
