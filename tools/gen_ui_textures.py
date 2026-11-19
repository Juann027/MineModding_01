#!/usr/bin/env python3
"""Gera as texturas proprias da UI (paineis, botoes, fundos e nos da arvore) e dos itens consumiveis.

Uso: python3 tools/gen_ui_textures.py  (pixel art procedural; substitua os PNGs por arte final quando houver)
"""
import math
import pathlib
import random

from PIL import Image, ImageDraw

ROOT = pathlib.Path(__file__).resolve().parent.parent / "src/main/resources/assets/respectcraft/textures"
GUI = ROOT / "gui"
ITEM = ROOT / "item"


def save(img, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)


def rgba(h, a=255):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (a,)


def frame(w, h, outer, mid, inner, fill, highlight=None, noise=0, seed=1):
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle([1, 0, w - 2, h - 1], fill=outer)
    d.rectangle([0, 1, w - 1, h - 2], fill=outer)
    d.rectangle([1, 1, w - 2, h - 2], fill=mid)
    d.rectangle([2, 2, w - 3, h - 3], fill=inner)
    d.rectangle([3, 3, w - 4, h - 4], fill=fill)
    if highlight:
        d.line([3, 3, w - 4, 3], fill=highlight)
    if noise:
        rnd = random.Random(seed)
        px = img.load()
        for y in range(4, h - 4):
            for x in range(4, w - 4):
                r, g, b, a = px[x, y]
                n = rnd.randint(-noise, noise)
                px[x, y] = (max(0, min(255, r + n)), max(0, min(255, g + n)), max(0, min(255, b + n)), a)
    return img


# ------------------------------------------------------------------ painel e botoes (nine-slice, borda 4)
panel = frame(32, 32, rgba("0b0f14"), rgba("d6b84a"), rgba("1d5c3a"), rgba("1a222c"), rgba("26313d"), noise=3)
# cantos dourados
pp = panel.load()
for (x, y) in [(1, 1), (30, 1), (1, 30), (30, 30)]:
    pp[x, y] = rgba("ffe27a")
save(panel, GUI / "ui/panel.png")

states = [
    ("2a3644", "4f6275", "3a4a5c", "607891"),  # normal
    ("2f5c3e", "7ccf8f", "3f7a52", "a4e8b2"),  # hover
    ("6b5216", "ffe27a", "a4801f", "fff0b0"),  # selecionado
    ("1c2129", "323a44", "262d36", "3a434e"),  # desativado
]
button = Image.new("RGBA", (64, 80), (0, 0, 0, 0))
for i, (fill, border, shade, light) in enumerate(states):
    b = frame(64, 20, rgba("07090c"), rgba(border), rgba(shade), rgba(fill), rgba(light))
    d = ImageDraw.Draw(b)
    d.line([3, 16, 60, 16], fill=rgba(shade))
    button.paste(b, (0, i * 20))
save(button, GUI / "ui/button.png")

# Aba (24x24, 3 estados: normal, hover, selecionada)
tab = Image.new("RGBA", (72, 24), (0, 0, 0, 0))
for i, (fill, border) in enumerate([("1f2833", "56697d"), ("2b3a49", "8fb3c9"), ("33402a", "ffe27a")]):
    t = frame(24, 24, rgba("07090c"), rgba(border), rgba(fill), rgba(fill))
    tab.paste(t, (i * 24, 0))
save(tab, GUI / "ui/tab.png")

# Brilho radial suave (VFX), 32x32 branco com alfa
glow = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
gp = glow.load()
for y in range(32):
    for x in range(32):
        dist = math.hypot(x - 15.5, y - 15.5) / 15.5
        a = max(0.0, 1 - dist) ** 2
        gp[x, y] = (255, 255, 255, int(a * 255))
save(glow, GUI / "ui/glow.png")

# ------------------------------------------------------------------ nos da arvore (26x26: bloqueado, disponivel, comprado)
node = Image.new("RGBA", (78, 26), (0, 0, 0, 0))
for i, (border, inner, fill) in enumerate([("3b4048", "23272d", "15181c"),
                                           ("c9d6e2", "4c6378", "1d2a36"),
                                           ("ffe27a", "b8862a", "3b2c10")]):
    n = Image.new("RGBA", (26, 26), (0, 0, 0, 0))
    d = ImageDraw.Draw(n)
    # losango arredondado / octogono
    pts = [(7, 0), (18, 0), (25, 7), (25, 18), (18, 25), (7, 25), (0, 18), (0, 7)]
    d.polygon(pts, fill=rgba("06080a"))
    pts2 = [(7, 1), (18, 1), (24, 7), (24, 18), (18, 24), (7, 24), (1, 18), (1, 7)]
    d.polygon(pts2, fill=rgba(border))
    pts3 = [(8, 3), (17, 3), (22, 8), (22, 17), (17, 22), (8, 22), (3, 17), (3, 8)]
    d.polygon(pts3, fill=rgba(inner))
    pts4 = [(8, 4), (17, 4), (21, 8), (21, 17), (17, 21), (8, 21), (4, 17), (4, 8)]
    d.polygon(pts4, fill=rgba(fill))
    node.paste(n, (i * 26, 0))
save(node, GUI / "skilltree/node.png")

# ------------------------------------------------------------------ fundos das abas (64x64, repetidos)


def bg_combate():
    rnd = random.Random(7)
    img = Image.new("RGBA", (64, 64), rgba("2a1414"))
    d = ImageDraw.Draw(img)
    for y in range(0, 64, 16):  # tijolos de pedra avermelhada
        off = 0 if (y // 16) % 2 == 0 else 16
        for x in range(-16, 64, 32):
            c = rnd.randint(-8, 8)
            d.rectangle([x + off + 1, y + 1, x + off + 30, y + 14], fill=(58 + c, 26 + c // 2, 24 + c // 2, 255))
            d.line([x + off + 1, y + 1, x + off + 30, y + 1], fill=(78 + c, 38, 34, 255))
    for _ in range(40):
        x, y = rnd.randrange(64), rnd.randrange(64)
        img.putpixel((x, y), (90, 40, 32, 255))
    return img


def bg_sobrevivencia():
    rnd = random.Random(11)
    img = Image.new("RGBA", (64, 64), rgba("10261a"))
    px = img.load()
    for y in range(64):
        for x in range(64):
            v = int(8 * math.sin(x * 0.2) * math.cos(y * 0.25))
            px[x, y] = (16 + v // 2, 38 + v, 26 + v // 2, 255)
    d = ImageDraw.Draw(img)
    for _ in range(14):  # folhas
        cx, cy = rnd.randrange(64), rnd.randrange(64)
        ang = rnd.random() * math.pi
        for t in range(-5, 6):
            x = int(cx + math.cos(ang) * t) % 64
            y = int(cy + math.sin(ang) * t) % 64
            w = 2 - abs(t) // 3
            d.ellipse([x - w, y - w, x + w, y + w], fill=(44, 92, 52, 255))
            px[x, y] = (70, 130, 70, 255)
    return img


def bg_malandragem():
    rnd = random.Random(5)
    img = Image.new("RGBA", (64, 64), rgba("140f22"))
    d = ImageDraw.Draw(img)
    # janelas de predio em neon
    for y in range(2, 64, 8):
        for x in range(2, 64, 8):
            r = rnd.random()
            if r < 0.25:
                col = (180, 110, 230, 255)
            elif r < 0.35:
                col = (240, 200, 90, 255)
            else:
                col = (34, 26, 52, 255)
            d.rectangle([x, y, x + 3, y + 4], fill=col)
    return img


save(bg_combate(), GUI / "skilltree/bg_combate.png")
save(bg_sobrevivencia(), GUI / "skilltree/bg_sobrevivencia.png")
save(bg_malandragem(), GUI / "skilltree/bg_malandragem.png")

# ------------------------------------------------------------------ itens 16x16


def item(name, draw):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw(ImageDraw.Draw(img), img)
    save(img, ITEM / f"{name}.png")


def coco(d, img):
    d.ellipse([2, 3, 13, 14], fill=rgba("3d7a2a"), outline=rgba("1f4a14"))
    d.ellipse([4, 5, 9, 9], fill=rgba("5fa83c"))
    d.line([9, 1, 11, 5], fill=rgba("e8e8e8"))
    d.line([10, 1, 12, 5], fill=rgba("ff5a5a"))


def cafe(d, img):
    d.rectangle([3, 6, 11, 13], fill=rgba("f2f2f2"), outline=rgba("7a7a7a"))
    d.rectangle([4, 7, 10, 8], fill=rgba("5a3418"))
    d.arc([10, 7, 14, 11], 270, 90, fill=rgba("7a7a7a"))
    d.line([2, 14, 12, 14], fill=rgba("bfbfbf"))
    d.point([(6, 3), (7, 2), (8, 4)], fill=rgba("d0d0d0"))


def guarana(d, img):
    d.rectangle([5, 2, 10, 14], fill=rgba("1e8a3a"), outline=rgba("0d4a1c"))
    d.rectangle([6, 6, 9, 9], fill=rgba("c8202a"))
    d.point([(7, 7), (8, 8)], fill=rgba("ffffff"))
    d.rectangle([6, 1, 9, 2], fill=rgba("b0b0b0"))


def pao(d, img):
    d.ellipse([2, 5, 13, 13], fill=rgba("e7b956"), outline=rgba("9a6d22"))
    d.ellipse([4, 6, 8, 9], fill=rgba("f6d98a"))
    d.point([(9, 10), (6, 11), (10, 7)], fill=rgba("c48a2e"))


def coxinha(d, img):
    d.polygon([(8, 1), (13, 13), (3, 13)], fill=rgba("d8892c"), outline=rgba("8a4d14"))
    d.ellipse([3, 9, 13, 15], fill=rgba("d8892c"), outline=rgba("8a4d14"))
    d.point([(7, 6), (9, 9), (6, 11), (10, 12)], fill=rgba("f0b25a"))


def brigadeiro(d, img):
    d.rectangle([3, 10, 12, 14], fill=rgba("d93c3c"), outline=rgba("8a1c1c"))
    d.ellipse([3, 4, 12, 12], fill=rgba("4a2614"), outline=rgba("2a140a"))
    for p in [(5, 6), (8, 5), (10, 8), (6, 9), (9, 10), (7, 7)]:
        d.point(p, fill=rgba("ffd1e0"))


def penico(d, img):
    d.ellipse([2, 4, 13, 8], fill=rgba("f2f2f2"), outline=rgba("8a8a8a"))
    d.rectangle([3, 6, 12, 13], fill=rgba("e0e0e0"), outline=rgba("8a8a8a"))
    d.ellipse([4, 5, 11, 7], fill=rgba("5c88c9"))
    d.arc([11, 7, 15, 12], 270, 90, fill=rgba("8a8a8a"))


def pill_box(color, label):
    def draw(d, img):
        d.rectangle([3, 3, 12, 13], fill=rgba("f4f4f4"), outline=rgba("6a6a6a"))
        d.rectangle([3, 6, 12, 9], fill=rgba(color))
        d.ellipse([9, 10, 14, 14], fill=rgba(label), outline=rgba("3a3a3a"))
    return draw


def bottle(liquid, cap):
    def draw(d, img):
        d.rectangle([6, 1, 9, 3], fill=rgba(cap))
        d.rectangle([5, 4, 10, 5], fill=rgba("cfe6ef"))
        d.ellipse([3, 5, 12, 14], fill=rgba("cfe6ef"), outline=rgba("5a6d78"))
        d.ellipse([4, 8, 11, 13], fill=rgba(liquid))
    return draw


def cup(liquid):
    def draw(d, img):
        d.rectangle([3, 6, 11, 13], fill=rgba("ece2cf"), outline=rgba("7a6a55"))
        d.rectangle([4, 7, 10, 8], fill=rgba(liquid))
        d.arc([10, 7, 14, 11], 270, 90, fill=rgba("7a6a55"))
        d.point([(6, 3), (7, 2), (8, 4)], fill=rgba("d0d0d0"))
    return draw


def pilula_azul(d, img):
    d.ellipse([3, 5, 9, 11], fill=rgba("2f62d6"), outline=rgba("17337a"))
    d.ellipse([7, 5, 13, 11], fill=rgba("3f7cf0"), outline=rgba("17337a"))
    d.point([(5, 7), (9, 6)], fill=rgba("cfe0ff"))


def lata(d, img):
    d.rectangle([4, 2, 11, 14], fill=rgba("f2a516"), outline=rgba("7a4a06"))
    d.rectangle([4, 6, 11, 9], fill=rgba("1a1a1a"))
    d.point([(6, 7), (7, 8), (8, 7), (9, 8)], fill=rgba("f2a516"))
    d.rectangle([5, 1, 10, 2], fill=rgba("b0b0b0"))


for name, fn in [("laxante_industrial", pill_box("8a5a2c", "c2a070")), ("antidiarreico", pill_box("3aa65a", "e0e0e0")),
                 ("cafe_extraforte", cup("2a1408")), ("cha_diuretico", cup("b8c84a")), ("cha_calmante", cup("8fc9a0")),
                 ("afrodisiaco_misterioso", bottle("e0569a", "7a1f4a")), ("pilula_azul", pilula_azul),
                 ("energetico_do_tigrinho", lata)]:
    item(name, fn)

for name, fn in [("agua_de_coco", coco), ("cafezinho", cafe), ("guarana", guarana), ("pao_de_queijo", pao),
                 ("coxinha", coxinha), ("brigadeiro", brigadeiro), ("penico", penico)]:
    item(name, fn)
print("Texturas geradas em", ROOT)
