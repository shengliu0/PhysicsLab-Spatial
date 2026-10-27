#!/usr/bin/env python3
"""Icon artwork for 物理实验室 Spatial — one design, every format.

The motif: a Kepler orbit drawn as a full spectrum (mechanics + optics) around a glowing sun, a lit
planet on the orbit and a comet (the app's hand-thrown comets) on a deep-space disc.

Outputs (run from the SolarSpatial/ directory):
  app/src/main/res/drawable/icon_3d_layer_0.png   PICO 3-D icon, back layer: the disc (space, orbit, comet)
  app/src/main/res/drawable/icon_3d_layer_1.png   PICO 3-D icon, front layer: sun + planet (pop out)
  app/src/main/res/drawable/icon_3d_sdf_{0,1}.png  their relief maps: grey = alpha = 128 + 2.55 x (signed
                                                  distance inside the shape, px), the encoding of the
                                                  template's own SDFs (measured: edge 128, +-50 px ramp)
  app/src/main/res/mipmap-anydpi/ic_spatial_launcher.png   flat round composite (2-D fallback)
  app/src/main/res/drawable/ic_launcher_foreground.png     adaptive-icon foreground, 1424 px full bleed
  docs/store/icon_1024.png                                 store icon, 1024 x 1024, opaque
"""
import colorsys
import math
import os

import numpy as np
from PIL import Image, ImageFilter

SS = 2                       # supersampling
N = 1024 * SS                # working canvas
rng = np.random.default_rng(20261026)

Y, X = np.mgrid[0:N, 0:N].astype(np.float32) / SS          # pixel centres in 1024 units
X += 0.5 / SS; Y += 0.5 / SS

SUN = (512.0, 492.0); SUN_R = 124.0
ORB_C = (512.0, 528.0); ORB_A, ORB_B, ORB_ROT = 404.0, 156.0, math.radians(-15)
PLANET_T = math.radians(118)                                # position on the orbit (front, lower left)
PLANET_R = 50.0


def lin(c):  # sRGB 0..255 -> linear 0..1
    c = np.asarray(c, np.float32) / 255.0
    return np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)


def srgb(l):
    l = np.clip(l, 0, 1)
    return np.where(l <= 0.0031308, l * 12.92, 1.055 * l ** (1 / 2.4) - 0.055)


def over(dst_rgb, dst_a, src_rgb, src_a):
    """Premultiplied-free 'over' in linear light. rgb arrays HxWx3, alpha HxW."""
    a = src_a[..., None]
    out_a = src_a + dst_a * (1 - src_a)
    out = (src_rgb * a + dst_rgb * dst_a[..., None] * (1 - a)) / np.maximum(out_a[..., None], 1e-6)
    return out, out_a


def add(dst_rgb, src_rgb, k):
    return dst_rgb + src_rgb * k[..., None]


def blur(a, r):
    im = Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8))      # 8-bit is plenty for a soft glow
    return np.asarray(im.filter(ImageFilter.GaussianBlur(r * SS)), np.float32) / 255.0


def disc_alpha(cx, cy, r):
    d = np.hypot(X - cx, Y - cy)
    return np.clip((r - d) * SS + 0.5, 0, 1), d


def orbit_point(t):
    x, y = ORB_A * math.cos(t), ORB_B * math.sin(t)
    c, s = math.cos(ORB_ROT), math.sin(ORB_ROT)
    return ORB_C[0] + x * c - y * s, ORB_C[1] + x * s + y * c


# ------------------------------------------------------------------ back layer (space disc)
def background():
    # deep-space radial gradient, brighter just above the sun
    r = np.hypot(X - 512, Y - 430) / 724.0
    c0, c1, c2 = lin([30, 44, 104]), lin([12, 18, 50]), lin([3, 5, 16])
    t = np.clip(r, 0, 1)[..., None]
    rgb = np.where(t < 0.45, c0 + (c1 - c0) * (t / 0.45), c1 + (c2 - c1) * ((t - 0.45) / 0.55))
    # faint nebula tints
    for (cx, cy, rad, col, k) in [(250, 280, 230, [90, 60, 190], 0.05), (820, 780, 280, [30, 110, 210], 0.06)]:
        g = np.exp(-((X - cx) ** 2 + (Y - cy) ** 2) / (2 * rad ** 2))
        rgb = rgb + lin(col) * (g * k)[..., None]
    # stars (fewer near the sun's glare)
    for _ in range(55):
        sx, sy = rng.uniform(40, 984, 2)
        if math.hypot(sx - SUN[0], sy - SUN[1]) < 230: continue
        sr = rng.uniform(0.6, 1.6); br = rng.uniform(0.3, 1.0)
        g = np.exp(-((X - sx) ** 2 + (Y - sy) ** 2) / (2 * sr ** 2))
        rgb = rgb + lin([220, 230, 255]) * (g * br * 0.9)[..., None]
    return rgb


def orbit_layers():
    """Spectrum orbit: (rgb, alpha_back, alpha_front, glow)."""
    c, s = math.cos(-ORB_ROT), math.sin(-ORB_ROT)
    xr = (X - ORB_C[0]) * c - (Y - ORB_C[1]) * s
    yr = (X - ORB_C[0]) * s + (Y - ORB_C[1]) * c
    F = (xr / ORB_A) ** 2 + (yr / ORB_B) ** 2 - 1
    G = 2 * np.sqrt((xr / ORB_A ** 2) ** 2 + (yr / ORB_B ** 2) ** 2)
    dist = np.abs(F / np.maximum(G, 1e-6))
    w = 7.0
    a = np.clip((w - dist) * SS + 0.5, 0, 1)
    th = np.arctan2(yr / ORB_B, xr / ORB_A)                 # -pi..pi, 0 = right end
    sfrac = (th % (2 * np.pi)) / (2 * np.pi)
    hue = 0.76 * (1 - np.abs(2 * sfrac - 1))                 # red at the right end -> violet at the left, both halves
    rgb = np.zeros((N, N, 3), np.float32)
    hs = np.linspace(0, 0.76, 256)
    lut = np.array([colorsys.hsv_to_rgb(h, 0.80, 1.0) for h in hs], np.float32)
    idx = np.clip((hue / 0.76 * 255).astype(int), 0, 255)
    rgb = lin(lut[idx] * 255)
    front = yr > 0
    a_back = a * np.where(front, 0, 0.55)
    a_front = a * np.where(front, 1, 0)
    glow = blur(a, 9) * 0.9
    return rgb, a_back, a_front, glow


def comet():
    """Comet up right, ion tail pointing away from the sun. Returns (rgb, alpha) additive."""
    cx, cy = 806.0, 236.0
    ux, uy = cx - SUN[0], cy - SUN[1]; L = math.hypot(ux, uy); ux, uy = ux / L, uy / L
    px, py = -uy, ux
    along = (X - cx) * ux + (Y - cy) * uy
    across = (X - cx) * px + (Y - cy) * py
    t = np.clip(along / 250.0, 0, 1)
    width = 4 + 30 * t
    tail = np.where((along > 0) & (along < 250), np.exp(-(across / np.maximum(width * 0.55, 1e-3)) ** 2) * (1 - t) ** 1.6, 0)
    head = np.exp(-((X - cx) ** 2 + (Y - cy) ** 2) / (2 * 9.0 ** 2))
    core, _ = disc_alpha(cx, cy, 6.5)
    rgb = lin([150, 215, 255]) * tail[..., None] * 0.9 + lin([210, 240, 255]) * head[..., None] * 0.9 + core[..., None] * 1.2
    return rgb


# ------------------------------------------------------------------ front layer (sun + planet)
def sun():
    a, d = disc_alpha(*SUN, SUN_R)
    rr = np.clip(d / SUN_R, 0, 1)
    mu = np.sqrt(np.clip(1 - rr ** 2, 0, 1))
    core, mid, edge = lin([255, 252, 236]), lin([255, 206, 92]), lin([250, 112, 28])
    t = rr[..., None]
    col = np.where(t < 0.55, core + (mid - core) * (t / 0.55), mid + (edge - mid) * ((t - 0.55) / 0.45))
    col = col * (0.70 + 0.30 * mu)[..., None] * 1.18          # limb darkening, bright core
    # subtle granulation
    gx = np.sin(X * 0.21 + np.sin(Y * 0.13) * 2) * np.sin(Y * 0.19 + np.sin(X * 0.11) * 2)
    col = col * (1 + 0.035 * gx)[..., None]
    return col, a


def sun_glow():
    d = np.hypot(X - SUN[0], Y - SUN[1])
    # tight corona + a modest halo: the glow must not tint the whole sky (first draft went mauve)
    g = np.exp(-np.clip(d - SUN_R * 0.92, 0, None) / 38.0) * 0.95 + np.exp(-np.clip(d - SUN_R, 0, None) / 120.0) * 0.16
    return lin([255, 165, 60]) * g[..., None]


def planet():
    cx, cy = orbit_point(PLANET_T)
    a, d = disc_alpha(cx, cy, PLANET_R)
    nx = (X - cx) / PLANET_R; ny = (Y - cy) / PLANET_R
    nz = np.sqrt(np.clip(1 - nx ** 2 - ny ** 2, 0, 1))
    lx, ly = SUN[0] - cx, SUN[1] - cy; ll = math.hypot(lx, ly)
    L = np.array([lx / ll * 0.85, ly / ll * 0.85, 0.53]); L = L / np.linalg.norm(L)
    diff = np.clip(nx * L[0] + ny * L[1] + nz * L[2], 0, 1)
    ocean_hi, ocean_lo, night = lin([92, 190, 255]), lin([30, 90, 220]), lin([8, 16, 40])
    base = ocean_lo + (ocean_hi - ocean_lo) * (diff ** 0.8)[..., None]
    # soft land masses
    land = (np.sin(nx * 5.1 + 1.3) * np.sin(ny * 4.3 - 0.4) + 0.35 * np.sin(nx * 9 + ny * 7)) > 0.42
    base = np.where(land[..., None], base * 0.55 + lin([70, 170, 110]) * 0.45 * diff[..., None] + 0.02, base)
    col = night + (base - night) * np.clip(diff * 1.25, 0, 1)[..., None]
    rim = np.clip((np.hypot(nx, ny) - 0.86) / 0.14, 0, 1) * np.clip(diff * 1.5 + 0.2, 0, 1)
    col = col + lin([120, 220, 255]) * (rim * 0.55)[..., None]
    spec = np.clip(nx * L[0] + ny * L[1] + nz * L[2], 0, 1) ** 40 * 0.8
    col = col + spec[..., None]
    return col, a, (cx, cy)


def sdf_png(dist_inside):
    """128 + 2.55 x signed distance (px, + inside), grey with alpha = grey (the template's encoding)."""
    v = np.clip(128 + 2.55 * dist_inside, 0, 255).astype(np.uint8)
    return Image.fromarray(np.dstack([v, v, v, v]))


def to_img(rgb, a=None):
    s = (srgb(rgb) * 255 + 0.5).astype(np.uint8)
    if a is None:
        im = Image.fromarray(s)
    else:
        im = Image.fromarray(np.dstack([s, (np.clip(a, 0, 1) * 255 + 0.5).astype(np.uint8)]))
    return im.resize((1024, 1024), Image.LANCZOS)


def main():
    os.makedirs("docs/store", exist_ok=True)
    disc_a, disc_d = disc_alpha(512, 512, 512)

    # ---- back layer: space + orbit + comet + sun glow
    bg = background()
    orb_rgb, orb_back, orb_front, orb_glow = orbit_layers()
    back = bg.copy()
    back = add(back, orb_rgb, orb_glow * 0.55)
    back, _ = over(back, np.ones((N, N), np.float32), orb_rgb, orb_back)
    back = back + sun_glow()
    back = back + comet()
    back, _ = over(back, np.ones((N, N), np.float32), orb_rgb, orb_front)

    # ---- front layer: sun + planet
    s_rgb, s_a = sun()
    p_rgb, p_a, pc = planet()
    front = np.zeros((N, N, 3), np.float32); front_a = np.zeros((N, N), np.float32)
    front, front_a = over(front, front_a, s_rgb, s_a)
    front, front_a = over(front, front_a, p_rgb, p_a)

    # PICO 3-D icon layers (round disc)
    to_img(back, disc_a).save("app/src/main/res/drawable/icon_3d_layer_0.png", optimize=True)
    to_img(front, front_a).save("app/src/main/res/drawable/icon_3d_layer_1.png", optimize=True)
    g = np.mgrid[0:1024, 0:1024].astype(np.float32) + 0.5
    d_disc = 512 - np.hypot(g[1] - 512, g[0] - 512)
    d_sun = SUN_R - np.hypot(g[1] - SUN[0], g[0] - SUN[1])
    d_pl = PLANET_R - np.hypot(g[1] - pc[0], g[0] - pc[1])
    sdf_png(d_disc).save("app/src/main/res/drawable/icon_3d_sdf_0.png", optimize=True)
    sdf_png(np.maximum(d_sun, d_pl)).save("app/src/main/res/drawable/icon_3d_sdf_1.png", optimize=True)

    # flat composites
    flat, flat_a = over(back, np.ones((N, N), np.float32), front, front_a)
    to_img(flat, disc_a).save("app/src/main/res/mipmap-anydpi/ic_spatial_launcher.png", optimize=True)
    square = to_img(flat)                                         # full bleed, opaque
    square.save("docs/store/icon_1024.png", optimize=True)
    fg = Image.new("RGB", (1424, 1424))
    fg.paste(square.resize((1424, 1424), Image.LANCZOS))
    fg.save("app/src/main/res/drawable/ic_launcher_foreground.png", optimize=True)
    print("icons written")


if __name__ == "__main__":
    main()
