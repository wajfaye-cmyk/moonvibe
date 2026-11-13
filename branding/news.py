"""Renders the launcher news images (640x360) from the Moonvibe logo and cosmetic thumbnails.

Usage: python branding/news.py   (run after the thumbnails exist in backend/thumbnails)
"""
import os
import random

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'launcher', 'assets', 'news')
THUMBS = os.path.join(ROOT, 'backend', 'thumbnails')
W, H = 640, 360
FONT = next(p for p in [os.environ.get('MOONVIBE_FONT', ''),
                        '/usr/share/fonts/truetype/google-fonts/Poppins-Bold.ttf',
                        'C:/Windows/Fonts/segoeuib.ttf'] if p and os.path.exists(p))


def sky(seed, top=(24, 28, 58), bottom=(9, 11, 24)):
    img = Image.new('RGB', (W, H))
    d = ImageDraw.Draw(img)
    for y in range(H):
        t = y / H
        d.line([(0, y), (W, y)], fill=tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3)))
    rnd = random.Random(seed)
    for _ in range(70):
        x, y = rnd.uniform(0, W), rnd.uniform(0, H)
        r = rnd.uniform(0.5, 1.4)
        a = int(rnd.uniform(110, 230))
        d.ellipse([x - r, y - r, x + r, y + r], fill=(a, a, min(255, a + 20)))
    return img.convert('RGBA')


def paste(img, path, size, pos):
    if not os.path.exists(path):
        return
    im = Image.open(path).convert('RGBA')
    im.thumbnail((size, size), Image.LANCZOS)
    img.alpha_composite(im, (int(pos[0] - im.width / 2), int(pos[1] - im.height / 2)))


def thumbs_row(img, ids, y, size):
    step = W / (len(ids) + 1)
    for i, cid in enumerate(ids):
        paste(img, os.path.join(THUMBS, f'{cid}.png'), size, (step * (i + 1), y))


def save(img, name):
    os.makedirs(OUT, exist_ok=True)
    img.convert('RGB').save(os.path.join(OUT, name), quality=90)
    print('news/' + name)


def main():
    # Welcome: logo on the night sky
    img = sky(1)
    paste(img, os.path.join(ROOT, 'launcher', 'assets', 'wordmark.png'), 520, (W / 2, H / 2))
    save(img, 'client.jpg')

    # New cosmetics: headphones + sneakers
    img = sky(2, (30, 26, 64))
    thumbs_row(img, ['headphones_moon', 'headphones_neon', 'headphones_pink'], 120, 170)
    thumbs_row(img, ['sneakers_moon', 'sneakers_neon', 'sneakers_gold'], 270, 160)
    save(img, 'cosmetics.jpg')

    # Capes
    img = sky(3, (22, 30, 60))
    thumbs_row(img, ['moonvibe', 'moonlight', 'moon_neon', 'moonvibe_gold'], H / 2, 220)
    save(img, 'capes.jpg')

    # HUD: simple HUD boxes
    img = sky(4, (20, 24, 44))
    d = ImageDraw.Draw(img)
    f = ImageFont.truetype(FONT, 26)
    small = ImageFont.truetype(FONT, 20)
    for i, text in enumerate(['144 FPS', '12 CPS', '24 ms']):
        x, y = 40, 40 + i * 58
        d.rounded_rectangle([x, y, x + 170, y + 46], radius=10, fill=(12, 14, 28, 220), outline=(60, 66, 110))
        d.text((x + 18, y + 7), text, font=f, fill=(236, 233, 224))
    keys = [('W', 1, 0), ('A', 0, 1), ('S', 1, 1), ('D', 2, 1)]
    for k, cx, cy in keys:
        x, y = 420 + cx * 62, 90 + cy * 62
        fill = (124, 131, 255) if k == 'W' else (12, 14, 28)
        d.rounded_rectangle([x, y, x + 54, y + 54], radius=10, fill=fill, outline=(60, 66, 110))
        d.text((x + 27, y + 27), k, font=f, fill=(255, 255, 255), anchor='mm')
    d.rounded_rectangle([420, 218, 590, 262], radius=10, fill=(12, 14, 28), outline=(60, 66, 110))
    d.text((505, 240), 'LMB   RMB', font=small, fill=(236, 233, 224), anchor='mm')
    save(img, 'hud.jpg')

    # Versions: chips with version numbers around the moon
    img = sky(5, (26, 24, 54))
    paste(img, os.path.join(ROOT, 'launcher', 'assets', 'logo-main.png'), 150, (W / 2, H / 2))
    d = ImageDraw.Draw(img)
    f = ImageFont.truetype(FONT, 22)
    chips = [('1.8.9', 120, 80), ('1.12.2', 110, 270), ('1.16.5', 200, 175), ('1.20.6', 450, 175),
             ('1.21.11', 530, 80), ('26.3', 525, 275)]
    for text, cx, cy in chips:
        tw = d.textlength(text, font=f)
        d.rounded_rectangle([cx - tw / 2 - 16, cy - 20, cx + tw / 2 + 16, cy + 20], radius=20,
                            fill=(124, 131, 255) if text in ('1.8.9', '26.3') else (14, 16, 32), outline=(70, 76, 130))
        d.text((cx, cy), text, font=f, fill=(255, 255, 255), anchor='mm')
    save(img, 'versions.jpg')


if __name__ == '__main__':
    main()
