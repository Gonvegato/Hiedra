#!/usr/bin/env python3
"""Saca el icono adaptable de Hiedra (fondo, primer plano y monocromo).

Con docs/iconos/hiedra-original.jpg (una tarjeta verde muy oscura con esquinas redondas sobre un fondo oscuro y una hoja de hiedra en líneas
claras) se saca de la imagen, como en Marfil, Carmesí y Cobalto:
  - la tarjeta se detecta (píxeles verdes) y se mete un 9 % por cada lado;
  - el fondo es el degradado de la tarjeta (el interior muy desenfocado, sin líneas);
  - el primer plano son solo las líneas claras de la hoja, en blanco, dentro de la zona segura (66 % central);
  - el monocromo es la misma hoja en negro (para los iconos con tema).
Sin la imagen, se dibuja a mano una hoja de hiedra (rellena, con sus lóbulos, su tallo y los nervios vaciados) sobre verde hiedra.
Uso: python3 herramientas/icono-hiedra.py     (necesita Pillow y numpy)
"""
import math, os
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

RAIZ = __file__.rsplit('/herramientas/', 1)[0]
SRC = RAIZ + '/docs/iconos/hiedra-original.jpg'
RES = RAIZ + '/app/src/main/res/drawable-nodpi/'
C = 432


def capa(alfa, color):
    out = Image.new('RGBA', (C, C), (0, 0, 0, 0))
    esc = int(C * 0.66)
    al = Image.fromarray((alfa * 255).astype(np.uint8)).resize((esc, esc), Image.LANCZOS)
    col = Image.new('RGBA', (esc, esc), tuple(int(v) for v in color) + (255,)); col.putalpha(al)
    out.paste(col, ((C - esc) // 2, (C - esc) // 2), col)
    return out


def de_la_imagen():
    im = Image.open(SRC).convert('RGB')
    a = np.asarray(im).astype(float)
    m = (a[..., 1] > 40) & (a[..., 1] > a[..., 0] * 1.3) & (a[..., 1] > a[..., 2] * 1.15)
    ys = np.where(m.mean(1) > 0.5)[0]; xs = np.where(m.mean(0) > 0.5)[0]
    if len(xs) == 0 or len(ys) == 0: return None
    x0, x1, y0, y1 = xs[0], xs[-1], ys[0], ys[-1]
    ins = int((x1 - x0) * 0.09)
    inter = im.crop((x0 + ins, y0 + ins, x1 - ins, y1 - ins)).resize((1200, 1200), Image.LANCZOS)
    L = np.asarray(inter).astype(float) @ np.array([.299, .587, .114])
    bg = inter.filter(ImageFilter.GaussianBlur(120))
    bgL = np.asarray(bg).astype(float) @ np.array([.299, .587, .114])
    alfa = np.clip((L - bgL) / 40.0, 0, 1) ** 0.8
    return bg, alfa


def spline(ctrl, n=24):
    """Una curva suave (Catmull-Rom) por los puntos de control, cerrada."""
    out = []
    m = len(ctrl)
    for i in range(m):
        p0, p1, p2, p3 = ctrl[(i - 1) % m], ctrl[i], ctrl[(i + 1) % m], ctrl[(i + 2) % m]
        for k in range(n):
            t = k / n; t2 = t * t; t3 = t2 * t
            x = 0.5 * ((2 * p1[0]) + (-p0[0] + p2[0]) * t + (2 * p0[0] - 5 * p1[0] + 4 * p2[0] - p3[0]) * t2 + (-p0[0] + 3 * p1[0] - 3 * p2[0] + p3[0]) * t3)
            y = 0.5 * ((2 * p1[1]) + (-p0[1] + p2[1]) * t + (2 * p0[1] - 5 * p1[1] + 4 * p2[1] - p3[1]) * t2 + (-p0[1] + 3 * p1[1] - 3 * p2[1] + p3[1]) * t3)
            out.append((x, y))
    return out


# La mitad derecha de una hoja de hiedra (Hedera helix): la escotadura de arriba (donde va el tallo), el lóbulo de arriba, el lateral y la punta.
# En unidades de media hoja, x hacia la derecha, y hacia abajo.
MEDIA = [(0.00, -0.48), (0.12, -0.64), (0.30, -0.82), (0.47, -0.86), (0.55, -0.74), (0.51, -0.58), (0.45, -0.46),
         (0.64, -0.42), (0.86, -0.32), (0.99, -0.18), (0.93, -0.02), (0.74, 0.07), (0.56, 0.10),
         (0.51, 0.30), (0.39, 0.60), (0.21, 0.84), (0.00, 1.00)]


def hoja(S):
    """El contorno de una hoja de hiedra, en píxeles, con el centro en (S/2, S/2) y la punta abajo."""
    der = MEDIA
    izq = [(-x, y) for (x, y) in reversed(MEDIA[1:-1])]
    ctrl = der + izq
    esc = 0.46 * S
    cx, cy = S / 2, S / 2 - 0.04 * S
    return [(cx + x * esc, cy + y * esc) for (x, y) in spline(ctrl)]


def a_mano():
    S = 1200; K = 4   # se dibuja a 4x y se reduce (bordes suaves)
    y, x = np.mgrid[0:S, 0:S].astype(float) / S
    t = np.clip((x + y) / 2, 0, 1)
    arriba = np.array([28., 78., 48.]); abajo = np.array([7., 32., 20.])
    rgb = arriba[None, None, :] * (1 - t[..., None]) + abajo[None, None, :] * t[..., None]
    bg = Image.fromarray(rgb.astype(np.uint8), 'RGB')
    G = S * K
    lin = Image.new('L', (G, G), 0)
    d = ImageDraw.Draw(lin)
    p = hoja(G)
    d.polygon(p, fill=255)
    cx, cy = G / 2, G / 2 - 0.04 * G
    esc = 0.46 * G
    grosor = int(G * 0.022)
    # El tallo (peciolo), desde la escotadura hacia arriba, un poco curvado
    tallo = [(cx + 0.00 * esc, cy - 0.46 * esc), (cx + 0.02 * esc, cy - 0.62 * esc), (cx + 0.07 * esc, cy - 0.80 * esc), (cx + 0.15 * esc, cy - 0.96 * esc)]
    d.line(tallo, fill=255, width=grosor, joint='curve')
    d.ellipse([tallo[-1][0] - grosor / 2, tallo[-1][1] - grosor / 2, tallo[-1][0] + grosor / 2, tallo[-1][1] + grosor / 2], fill=255)
    # Los nervios, vaciados de la hoja (se ve el fondo): el central y uno hacia cada lóbulo
    fino = int(G * 0.009)
    nac = (cx, cy - 0.30 * esc)
    d.line([(cx, cy - 0.44 * esc), (cx, cy + 0.92 * esc)], fill=0, width=fino)
    for (tx, ty) in ((0.86, -0.17), (-0.86, -0.17), (0.40, -0.76), (-0.40, -0.76)):
        d.line([nac, (cx + tx * esc, cy + ty * esc)], fill=0, width=fino)
    lin = lin.resize((S, S), Image.LANCZOS)
    alfa = np.asarray(lin).astype(float) / 255
    return bg, alfa


r = de_la_imagen() if os.path.exists(SRC) else None
origen = 'de la imagen'
if r is None:
    r = a_mano(); origen = 'dibujado a mano'
bg, alfa = r
bg.resize((C, C), Image.BICUBIC).save(RES + 'ic_launcher_hiedra_bg.png')
capa(alfa, (214, 228, 196)).save(RES + 'ic_launcher_hiedra_fg.png')
capa(alfa, (0, 0, 0)).save(RES + 'ic_launcher_hiedra_mono.png')
print('Hecho (' + origen + '): fondo, primer plano y monocromo en', RES)
