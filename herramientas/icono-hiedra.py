#!/usr/bin/env python3
"""Saca el icono adaptable de Hiedra (fondo, primer plano y monocromo).

Con docs/iconos/hiedra-original.jpg (una tarjeta verde muy oscura con esquinas redondas sobre un fondo oscuro y una hoja de hiedra en líneas
claras) se saca de la imagen, como en Marfil, Carmesí y Cobalto:
  - la tarjeta se detecta (píxeles verdes) y se mete un 9 % por cada lado;
  - el fondo es el degradado de la tarjeta (el interior muy desenfocado, sin líneas);
  - el primer plano son solo las líneas claras de la hoja, en blanco, dentro de la zona segura (66 % central);
  - el monocromo es la misma hoja en negro (para los iconos con tema).
Sin la imagen, se dibuja a mano una hoja de hiedra (cinco lóbulos, su tallo y sus nervios) sobre verde hiedra.
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


def hoja(S):
    """El contorno de una hoja de hiedra (cinco lóbulos, la punta abajo), en píxeles, con el centro en (S/2, S/2)."""
    pts = []
    # Un radio que varía con el ángulo: cinco lóbulos (los dos de arriba pequeños, los dos laterales medianos, la punta grande abajo)
    for k in range(720):
        t = 2 * math.pi * k / 720            # 0 = abajo (la punta)
        lob = 0.78 + 0.22 * math.cos(5 * t)   # cinco lóbulos
        base = 1.0 - 0.28 * (1 - math.cos(t)) / 2   # más estrecha arriba (hacia el tallo)
        r = (0.42 * S) * lob * base
        if abs(t) < 0.25 or abs(t - 2 * math.pi) < 0.25: r *= 1.12   # la punta, más larga
        x = S / 2 + r * math.sin(t)
        y = S / 2 - 0.05 * S + r * math.cos(t)
        pts.append((x, y))
    return pts


def a_mano():
    S = 1200
    y, x = np.mgrid[0:S, 0:S].astype(float) / S
    t = np.clip((x + y) / 2, 0, 1)
    arriba = np.array([26., 72., 44.]); abajo = np.array([8., 34., 22.])
    rgb = arriba[None, None, :] * (1 - t[..., None]) + abajo[None, None, :] * t[..., None]
    bg = Image.fromarray(rgb.astype(np.uint8), 'RGB')
    lin = Image.new('L', (S, S), 0)
    d = ImageDraw.Draw(lin)
    grosor = int(S * 0.016)
    p = hoja(S)
    d.line(p + [p[0]], fill=255, width=grosor, joint='curve')
    cx, cy = S / 2, S / 2 - 0.05 * S
    # El tallo, desde la escotadura de arriba
    d.line([(cx, cy - 0.30 * S), (cx + 0.02 * S, cy - 0.47 * S)], fill=255, width=grosor)
    # Los nervios: el central hasta la punta y uno hacia cada lóbulo
    d.line([(cx, cy - 0.28 * S), (cx, cy + 0.44 * S)], fill=255, width=int(grosor * 0.8))
    for ang, largo in ((72, 0.40), (-72, 0.40), (144, 0.30), (-144, 0.30)):
        a = math.radians(ang)
        d.line([(cx, cy - 0.05 * S), (cx + largo * S * math.sin(a), cy - 0.05 * S + largo * S * math.cos(a))], fill=255, width=int(grosor * 0.7))
    halo = lin.filter(ImageFilter.GaussianBlur(S * 0.02))
    alfa = np.clip(np.asarray(lin).astype(float) / 255 + np.asarray(halo).astype(float) / 255 * 0.3, 0, 1)
    return bg, alfa


r = de_la_imagen() if os.path.exists(SRC) else None
origen = 'de la imagen'
if r is None:
    r = a_mano(); origen = 'dibujado a mano'
bg, alfa = r
bg.resize((C, C), Image.BICUBIC).save(RES + 'ic_launcher_hiedra_bg.png')
capa(alfa, (255, 255, 255)).save(RES + 'ic_launcher_hiedra_fg.png')
capa(alfa, (0, 0, 0)).save(RES + 'ic_launcher_hiedra_mono.png')
print('Hecho (' + origen + '): fondo, primer plano y monocromo en', RES)
