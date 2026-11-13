"""Renders the Moonvibe logos (round moon + wordmark) into branding/source and every target size.

Usage: python branding/generate.py   (needs Pillow; fonts: Poppins Bold or any bold TTF via MOONVIBE_FONT)
"""
import math
import os
import random

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, 'branding', 'source')
SS = 4  # supersampling

FONT_CANDIDATES = [
    os.environ.get('MOONVIBE_FONT', ''),
    '/usr/share/fonts/truetype/google-fonts/Poppins-Bold.ttf',
    'C:/Windows/Fonts/segoeuib.ttf',
    '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf',
]

NAVY = (13, 16, 30)
TEXT = (244, 242, 236)
ACCENT = (154, 168, 255)


def font(size):
    for path in FONT_CANDIDATES:
        if path and os.path.exists(path):
            return ImageFont.truetype(path, size)
    raise SystemExit('No bold font found. Set MOONVIBE_FONT to a .ttf file.')


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(len(a)))


def moon(size, seed=7):
    """Full round moon with soft shading and craters, transparent background."""
    s = size * SS
    img = Image.new('RGBA', (s, s), (0, 0, 0, 0))
    cx = cy = s / 2
    r = s / 2 - s * 0.01

    # Body shading: light from the upper left.
    light = (246, 243, 233)
    dark = (176, 170, 158)
    body = Image.new('RGBA', (s, s), (0, 0, 0, 0))
    px = body.load()
    lx, ly = -0.55, -0.6
    step = 1
    for y in range(0, s, step):
        dy = (y + 0.5 - cy) / r
        for x in range(0, s, step):
            dx = (x + 0.5 - cx) / r
            d2 = dx * dx + dy * dy
            if d2 > 1:
                continue
            dz = math.sqrt(1 - d2)
            lum = max(0.0, (dx * lx + dy * ly + dz * 0.6) / 1.0)
            t = min(1.0, max(0.0, 1 - (lum * 0.85 + 0.15)))
            px[x, y] = lerp(light, dark, t) + (255,)
    img = Image.alpha_composite(img, body)

    # Craters.
    rnd = random.Random(seed)
    craters = [(-0.32, -0.18, 0.2), (0.28, 0.3, 0.24), (0.22, -0.38, 0.12), (-0.18, 0.42, 0.13),
               (0.48, -0.05, 0.09), (-0.5, 0.15, 0.08), (0.02, 0.05, 0.07)]
    layer = Image.new('RGBA', (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for ox, oy, cr in craters:
        x, y, rr = cx + ox * r, cy + oy * r, cr * r
        d.ellipse([x - rr, y - rr, x + rr, y + rr], fill=(150, 144, 134, 120))
        # inner shadow on the upper-left rim, lit rim lower-right
        d.arc([x - rr, y - rr, x + rr, y + rr], 200, 330, fill=(120, 114, 104, 150), width=max(2, int(rr * 0.16)))
        d.arc([x - rr, y - rr, x + rr, y + rr], 20, 150, fill=(255, 252, 244, 150), width=max(2, int(rr * 0.12)))
    for _ in range(26):
        a = rnd.random() * math.tau
        dist = math.sqrt(rnd.random()) * 0.82
        rr = r * (0.015 + rnd.random() * 0.03)
        x, y = cx + math.cos(a) * dist * r, cy + math.sin(a) * dist * r
        d.ellipse([x - rr, y - rr, x + rr, y + rr], fill=(140, 134, 124, 90))
    mask = Image.new('L', (s, s), 0)
    ImageDraw.Draw(mask).ellipse([cx - r, cy - r, cx + r, cy + r], fill=255)
    layer.putalpha(Image.composite(layer.getchannel('A'), Image.new('L', (s, s), 0), mask))
    img = Image.alpha_composite(img, layer)
    return img.resize((size, size), Image.LANCZOS)


def rounded_square(size, color, radius_ratio=0.23):
    s = size * SS
    img = Image.new('RGBA', (s, s), (0, 0, 0, 0))
    ImageDraw.Draw(img).rounded_rectangle([0, 0, s - 1, s - 1], radius=int(s * radius_ratio), fill=color + (255,))
    return img.resize((size, size), Image.LANCZOS)


def app_icon(size):
    """Navy rounded square with a few stars and the moon."""
    s = size * SS
    base = Image.new('RGBA', (s, s), (0, 0, 0, 0))
    bg = Image.new('RGBA', (s, s), (0, 0, 0, 0))
    gd = ImageDraw.Draw(bg)
    for y in range(s):
        gd.line([(0, y), (s, y)], fill=lerp((24, 28, 52), (9, 11, 22), y / s) + (255,))
    mask = Image.new('L', (s, s), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, s - 1, s - 1], radius=int(s * 0.23), fill=255)
    bg.putalpha(mask)
    base = Image.alpha_composite(base, bg)
    sd = ImageDraw.Draw(base)
    rnd = random.Random(3)
    for _ in range(14):
        x, y = rnd.uniform(0.1, 0.9) * s, rnd.uniform(0.08, 0.92) * s
        if math.hypot(x - s * 0.5, y - s * 0.5) < s * 0.36:
            continue
        rr = s * rnd.uniform(0.004, 0.009)
        sd.ellipse([x - rr, y - rr, x + rr, y + rr], fill=(220, 226, 255, int(rnd.uniform(120, 230))))
    m = moon(int(size * 0.66)).resize((int(s * 0.66), int(s * 0.66)), Image.LANCZOS)
    base.alpha_composite(m, (int(s * 0.17), int(s * 0.17)))
    return base.resize((size, size), Image.LANCZOS)


def wordmark(width, height, subtitle=None):
    """Moon on the left, 'Moonvibe' on the right. Transparent background."""
    s_w, s_h = width * SS, height * SS
    img = Image.new('RGBA', (s_w, s_h), (0, 0, 0, 0))
    moon_size = int(s_h * 0.62)
    f = font(int(s_h * 0.36))
    d = ImageDraw.Draw(img)
    text = 'Moonvibe'
    tb = d.textbbox((0, 0), text, font=f)
    tw, th = tb[2] - tb[0], tb[3] - tb[1]
    gap = int(s_h * 0.1)
    total = moon_size + gap + tw
    x0 = (s_w - total) // 2
    m = moon(moon_size // SS).resize((moon_size, moon_size), Image.LANCZOS)
    img.alpha_composite(m, (x0, (s_h - moon_size) // 2))
    ty = (s_h - th) // 2 - tb[1]
    if subtitle:
        ty -= int(s_h * 0.06)
    d.text((x0 + moon_size + gap, ty), text, font=f, fill=TEXT + (255,))
    if subtitle:
        sf = font(int(s_h * 0.085))
        d.text((x0 + moon_size + gap + int(s_h * 0.012), ty + tb[3] + int(s_h * 0.05)), subtitle, font=sf,
               fill=ACCENT + (255,), spacing=0)
    return img.resize((width, height), Image.LANCZOS)


def save(img, rel):
    path = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    print(f'{rel:70} {img.size[0]}x{img.size[1]}')


def main():
    icon = app_icon(1024)
    mark = moon(1024)
    title = wordmark(2172, 724)
    splash = wordmark(2172, 724, subtitle='P V P   C L I E N T')

    save(mark, 'branding/source/01_moonvibe_main_logo.png')
    save(title, 'branding/source/02_moonvibe_ingame_menu_title.png')
    save(splash, 'branding/source/03_moonvibe_startup_splash.png')
    save(icon, 'branding/source/04_moonvibe_launcher_icon.png')
    icon.save(os.path.join(SRC, '04_moonvibe_launcher_icon.ico'),
              sizes=[(256, 256), (128, 128), (64, 64), (48, 48), (32, 32), (16, 16)])

    def sized(img, w):
        return img.resize((w, round(img.height * w / img.width)), Image.LANCZOS)

    save(sized(icon, 512), 'launcher/assets/icon.png')
    save(sized(icon, 64), 'launcher/assets/icon-64.png')
    save(sized(mark, 720), 'launcher/assets/logo-main.png')
    save(sized(splash, 1200), 'launcher/assets/wordmark.png')
    save(sized(title, 1200), 'launcher/assets/wordmark-alt.png')
    icon.save(os.path.join(ROOT, 'launcher/assets/icon.ico'),
              sizes=[(256, 256), (128, 128), (64, 64), (48, 48), (32, 32), (16, 16)])

    for mod in ('mod', 'mod-legacy'):
        base = f'{mod}/src/main/resources/assets/moonvibe'
        save(sized(icon, 256), f'{base}/icon.png')
        save(sized(mark, 128), f'{base}/textures/gui/mark.png')
        save(sized(title, 1024), f'{base}/textures/gui/title.png')
        save(sized(splash, 1024), f'{base}/textures/gui/splash.png')

    banner = Image.new('RGBA', (1000, 333), NAVY + (255,))
    banner.alpha_composite(sized(splash, 1000), (0, 0))
    save(banner, 'docs/banner.png')
    save(sized(icon, 256), 'docs/icon.png')
    save(sized(icon, 500), 'docs/github-avatar.png')


if __name__ == '__main__':
    main()
