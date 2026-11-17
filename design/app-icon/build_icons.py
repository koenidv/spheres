"""Builds every Spheres app icon asset from one pixel-art master ("Orbits, tonal planets", 32px grid).

Run from anywhere:  python3 design/app-icon/build_icons.py
Needs ImageMagick 7 (`magick`) on PATH.

Outputs
  iOS      AppIcon (light / dark / tinted), LaunchIcon imageset, LaunchBackground colorset
  Android  adaptive launcher (foreground PNGs + background color + monochrome vector),
           legacy ic_launcher / ic_launcher_round PNGs (API 24-25), ic_stat_spheres status bar vector
  Stores   design/app-icon/out/play-store-512.png, design/app-icon/out/app-store-1024.png,
           design/app-icon/out/play-feature-graphic-{en,de}.png (1024x500)
"""
import json
import math
import os
import subprocess
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
OUT = os.path.join(HERE, "out")
ANDROID_RES = os.path.join(ROOT, "composeApp", "src", "androidMain", "res")
IOS_ASSETS = os.path.join(ROOT, "iosApp", "iosApp", "Assets.xcassets")

PINK, PURPLE, BLUE, CORAL = "#eb78b0", "#9d7ce7", "#5b9cf0", "#e36f73"
FONTS = os.path.join(ROOT, "composeApp", "src", "commonMain", "composeResources", "font")
TILE = "#f5effc"        # lilac icon background
DARK_TILE = "#17151d"

N = 32                  # master grid size


# ---------------------------------------------------------------- color helpers

def rgb(hex_):
    h = hex_.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def tint(hex_, pct):
    """Mix with white like tintColor(color, pct) in the app."""
    f = pct / 100
    return "#%02x%02x%02x" % tuple(round(255 - (255 - c) * f) for c in rgb(hex_))


def shade(hex_, f):
    return "#%02x%02x%02x" % tuple(round(c * f) for c in rgb(hex_))


# ---------------------------------------------------------------- master artwork

def shaded_sphere(px, cx, cy, r, base):
    """Pixel sphere with 4-step banded shading, lit from the top left."""
    lx, ly = -.45, -.5
    ln = math.hypot(lx, ly, 1)
    tones = [shade(base, .72), base, tint(base, 72), tint(base, 45)]
    painted = set()
    for y in range(N):
        for x in range(N):
            dx, dy = (x + .5 - cx) / r, (y + .5 - cy) / r
            if dx * dx + dy * dy > 1:
                continue
            l = (dx * lx + dy * ly + math.sqrt(1 - dx * dx - dy * dy)) / ln
            px[(x, y)] = tones[0 if l < .35 else 1 if l < .72 else 2 if l < .9 else 3]
            painted.add((x, y))
    return painted


def artwork(shadow="#e6dcf6", sphere_shadow=None, with_shadows=True):
    """Returns {(x, y): (hex, is_shadow)} for the 32px master. Empty pixels are absent."""
    px, shadows = {}, set()
    c, w, so = N / 2, 2, 1
    rings = [(c, BLUE), (c * .70, PURPLE), (c * .44, PINK)]
    people = [(0, -45), (1, 200), (0, 120)]  # (ring index, angle°); planets take their ring's color
    pr = 2.4
    halo = pr + 1.2

    def pos(ri, deg):
        r = rings[ri][0] - w / 2
        return c + r * math.cos(math.radians(deg)), c + r * math.sin(math.radians(deg))

    planets = [pos(ri, a) for ri, a in people]
    cut = lambda x, y: any(math.hypot(x + .5 - px_, y + .5 - py_) <= halo for px_, py_ in planets)

    for layer in (("shadow", "ring") if with_shadows else ("ring",)):
        off = so if layer == "shadow" else 0
        for y in range(N):
            for x in range(N):
                for r, col in rings:
                    d = math.hypot(x + .5 - c - off, y + .5 - c - off)
                    if r - w <= d <= r and not cut(x - off, y - off):
                        if layer == "ring":
                            px[(x, y)] = col
                            shadows.discard((x, y))
                        elif (x, y) not in px:
                            px[(x, y)] = shadow
                            shadows.add((x, y))

    for ri, a in people:
        shadows -= shaded_sphere(px, *pos(ri, a), pr, rings[ri][1])

    cr = c * .24
    if with_shadows:
        for y in range(N):
            for x in range(N):
                if math.hypot(x + .5 - c - so, y + .5 - c - so) <= cr:
                    px[(x, y)] = sphere_shadow or tint(CORAL, 40)
                    shadows.add((x, y))
    shadows -= shaded_sphere(px, c, c, cr, CORAL)
    return {k: (v, k in shadows) for k, v in px.items()}


def status_bar_glyph():
    """24x24 single-color glyph: one orbit, one planet, the sphere. Returns a set of pixels."""
    n, c = 24, 12
    on = set()
    planet = (c + 8.8 * math.cos(math.radians(-45)), c + 8.8 * math.sin(math.radians(-45)))
    for y in range(n):
        for x in range(n):
            fx, fy = x + .5, y + .5
            d = math.hypot(fx - c, fy - c)
            dp = math.hypot(fx - planet[0], fy - planet[1])
            if 7.9 <= d <= 9.9 and dp > 3.6:   # orbit ring with a gap around the planet
                on.add((x, y))
            if dp <= 2.5:                        # planet
                on.add((x, y))
            if d <= 4.6:                         # sphere
                on.add((x, y))
    return on


# ---------------------------------------------------------------- rendering

def run(*args):
    subprocess.run(["magick", *args], check=True)


def grid_png(pixels, path, n=N, recolor=None):
    """Writes the art at 1 px per grid cell, transparent where empty."""
    lines = [f"# ImageMagick pixel enumeration: {n},{n},255,srgba"]
    for y in range(n):
        for x in range(n):
            if (x, y) in pixels:
                col = pixels[(x, y)]
                col = recolor(col) if recolor else col
                r, g, b = rgb(col)
                lines.append(f"{x},{y}: ({r},{g},{b},255)")
            else:
                lines.append(f"{x},{y}: (0,0,0,0)")
    txt = path + ".txt"
    with open(txt, "w") as f:
        f.write("\n".join(lines))
    run(f"txt:{txt}", path)
    os.remove(txt)


def place_fit(src, dst, size, canvas):
    """Like place(), but for a non-integer scale: upscale crisply, then area-average down to `size`."""
    if size % N == 0:
        return place(src, dst, size // N, canvas)
    run(src, "-filter", "point", "-resize", "1600%", "-filter", "Box", "-resize", f"{size}x{size}",
        "-background", "none", "-gravity", "center", "-extent", f"{canvas}x{canvas}", dst)


def place(src, dst, scale, canvas, bg="none", opaque=False):
    """Integer nearest-neighbour upscale, centered on a square canvas."""
    args = [src, "-filter", "point", "-resize", f"{scale * 100}%",
            "-background", bg, "-gravity", "center", "-extent", f"{canvas}x{canvas}"]
    if opaque:
        args += ["-alpha", "remove", "-alpha", "off"]
    run(*args, dst)


def vector_drawable(pixels, n, viewport, offset, scale, color, path, tint_attr=None):
    """Single-color VectorDrawable built from merged horizontal pixel runs."""
    segs = []
    for y in range(n):
        x = 0
        while x < n:
            if (x, y) not in pixels:
                x += 1
                continue
            x2 = x
            while (x2 + 1, y) in pixels:
                x2 += 1
            X, Y, W = offset + x * scale, offset + y * scale, (x2 - x + 1) * scale
            segs.append(f"M{X:g},{Y:g}h{W:g}v{scale:g}h{-W:g}z")
            x = x2 + 1
    size = f'{viewport:g}dp'
    xml = f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by design/app-icon/build_icons.py. Do not edit by hand. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="{size}"
    android:height="{size}"
    android:viewportWidth="{viewport:g}"
    android:viewportHeight="{viewport:g}"{f'{chr(10)}    android:tint="{tint_attr}"' if tint_attr else ""}>
    <path
        android:fillColor="{color}"
        android:pathData="{''.join(segs)}" />
</vector>
'''
    with open(path, "w") as f:
        f.write(xml)


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def feature_graphic(light_png, tmp):
    """Google Play feature graphic: name and tagline left, the orbit art right."""
    taglines = {
        "en": "Stay close to the\npeople who matter.",
        "de": "Bleib nah an den Menschen,\ndie dir wichtig sind.",
    }
    place(light_png, f"{tmp}/fg-art.png", 12, 384)
    for lang, tagline in taglines.items():
        run("-size", "1024x500", f"xc:{TILE}",
            f"{tmp}/fg-art.png", "-geometry", "+584+58", "-composite",
            "-font", os.path.join(FONTS, "pixelify_sans_bold.ttf"), "-pointsize", "96",
            "-fill", "#111111", "-annotate", "+72+228", "Spheres",
            "-font", os.path.join(FONTS, "inter_medium.ttf"), "-pointsize", "30",
            "-interline-spacing", "6", "-fill", "#5a4f72", "-annotate", "+76+290", tagline,
            "-alpha", "off", "-depth", "8", f"{OUT}/play-feature-graphic-{lang}.png")


# ---------------------------------------------------------------- build

def main():
    os.makedirs(OUT, exist_ok=True)
    tmp = tempfile.mkdtemp()

    light = {k: v for k, (v, _) in artwork().items()}
    dark = {k: v for k, (v, _) in artwork(shadow="#2b2638", sphere_shadow="#3a2530").items()}
    flat = {k for k, (_, is_shadow) in artwork(with_shadows=False).items() if not is_shadow}

    def gray(hex_):  # iOS tinted icons are grayscale; the system applies the tint by luminance
        r, g, b = rgb(hex_)
        v = min(255, round((.299 * r + .587 * g + .114 * b) * 1.05))
        return "#%02x%02x%02x" % (v, v, v)

    grid_png(light, f"{tmp}/light.png")
    grid_png(dark, f"{tmp}/dark.png")
    tinted_px = {k: v for k, v in light.items() if k in flat}  # tinted: no drop shadows
    grid_png(tinted_px, f"{tmp}/tinted.png", recolor=gray)

    # --- iOS app icon: 26px per grid cell → 832px art on a 1024 tile
    icon_dir = os.path.join(IOS_ASSETS, "AppIcon.appiconset")
    for f in os.listdir(icon_dir):
        if f.endswith(".png"):
            os.remove(os.path.join(icon_dir, f))
    place(f"{tmp}/light.png", f"{icon_dir}/AppIcon-1024.png", 26, 1024, TILE, opaque=True)
    place(f"{tmp}/dark.png", f"{icon_dir}/AppIcon-1024-dark.png", 26, 1024, DARK_TILE, opaque=True)
    place(f"{tmp}/tinted.png", f"{icon_dir}/AppIcon-1024-tinted.png", 26, 1024, "#000000", opaque=True)
    write_json(f"{icon_dir}/Contents.json", {
        "images": [
            {"filename": "AppIcon-1024.png", "idiom": "universal", "platform": "ios", "size": "1024x1024"},
            {"appearances": [{"appearance": "luminosity", "value": "dark"}],
             "filename": "AppIcon-1024-dark.png", "idiom": "universal", "platform": "ios", "size": "1024x1024"},
            {"appearances": [{"appearance": "luminosity", "value": "tinted"}],
             "filename": "AppIcon-1024-tinted.png", "idiom": "universal", "platform": "ios", "size": "1024x1024"},
        ],
        "info": {"author": "xcode", "version": 1},
    })

    # --- iOS launch screen: 128pt art (4pt per cell) on white
    launch_dir = os.path.join(IOS_ASSETS, "LaunchIcon.imageset")
    os.makedirs(launch_dir, exist_ok=True)
    images = []
    for s in (1, 2, 3):
        name = f"LaunchIcon@{s}x.png" if s > 1 else "LaunchIcon.png"
        place(f"{tmp}/light.png", f"{launch_dir}/{name}", 4 * s, 32 * 4 * s)
        images.append({"filename": name, "idiom": "universal", "scale": f"{s}x"})
    write_json(f"{launch_dir}/Contents.json", {"images": images, "info": {"author": "xcode", "version": 1}})
    write_json(os.path.join(IOS_ASSETS, "LaunchBackground.colorset", "Contents.json"), {
        "colors": [{"idiom": "universal", "color": {"color-space": "srgb", "components": {
            "red": "1.000", "green": "1.000", "blue": "1.000", "alpha": "1.000"}}}],
        "info": {"author": "xcode", "version": 1},
    })

    # --- Android adaptive icon: 108dp canvas, art at 1.75dp per cell (56dp, leaves air inside circle masks)
    densities = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
    for name, d in densities.items():
        mip = os.path.join(ANDROID_RES, f"mipmap-{name}")
        os.makedirs(mip, exist_ok=True)
        place_fit(f"{tmp}/light.png", f"{mip}/ic_launcher_foreground.png", int(56 * d), int(108 * d))
        # legacy icons for API 24-25: lilac tile with the art, rounded square and circle
        size, inner = int(48 * d), int(44 * d)
        place(f"{tmp}/light.png", f"{tmp}/legacy.png", 26, 1024, TILE, opaque=True)
        run(f"{tmp}/legacy.png", "-resize", f"{inner}x{inner}", f"{tmp}/legacy-small.png")
        masks = {
            "ic_launcher": f"roundrectangle 0,0,{inner - 1},{inner - 1},{int(9 * d)},{int(9 * d)}",
            "ic_launcher_round": f"circle {inner / 2 - .5},{inner / 2 - .5} {inner / 2 - .5},0",
        }
        for out_name, shape in masks.items():
            run(f"{tmp}/legacy-small.png", "-alpha", "set",
                "(", "-size", f"{inner}x{inner}", "xc:none", "-fill", "white", "-draw", shape, "-alpha", "extract", ")",
                "-compose", "CopyOpacity", "-composite", "-compose", "Over",
                "-background", "none", "-gravity", "center", "-extent", f"{size}x{size}", f"{mip}/{out_name}.png")

    drawable = os.path.join(ANDROID_RES, "drawable")
    os.makedirs(drawable, exist_ok=True)
    vector_drawable(flat, N, 108, 26, 1.75, "#FFFFFFFF", f"{drawable}/ic_launcher_monochrome.xml")
    vector_drawable(status_bar_glyph(), 24, 24, 0, 1, "#FFFFFFFF", f"{drawable}/ic_stat_spheres.xml")

    anydpi = os.path.join(ANDROID_RES, "mipmap-anydpi-v26")
    os.makedirs(anydpi, exist_ok=True)
    adaptive = '''<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by design/app-icon/build_icons.py. Do not edit by hand. -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
'''
    for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
        with open(os.path.join(anydpi, name), "w") as f:
            f.write(adaptive)
    with open(os.path.join(ANDROID_RES, "values", "ic_launcher_background.xml"), "w") as f:
        f.write(f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by design/app-icon/build_icons.py. Do not edit by hand. -->
<resources>
    <color name="ic_launcher_background">{TILE.upper()}</color>
</resources>
''')

    # --- store icons and previews
    place(f"{tmp}/light.png", f"{OUT}/play-store-512.png", 13, 512, TILE, opaque=True)
    place(f"{tmp}/light.png", f"{OUT}/app-store-1024.png", 26, 1024, TILE, opaque=True)
    grid_png({k: "#ffffff" for k in status_bar_glyph()}, f"{tmp}/stat.png", n=24)
    place(f"{tmp}/stat.png", f"{OUT}/status-bar-preview.png", 16, 384, "#1f1d24", opaque=True)
    grid_png({k: "#ffffff" for k in flat}, f"{tmp}/mono.png")
    place(f"{tmp}/mono.png", f"{OUT}/monochrome-preview.png", 12, 512, "#3a3448", opaque=True)
    feature_graphic(f"{tmp}/light.png", tmp)
    print("Icons written.")


if __name__ == "__main__":
    main()
