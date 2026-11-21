"""Gera a textura provisoria twisted_log_top.png (Etapa 4.9).

O tronco da Fase 1 ("Blocks.zip") nao chegou; o twisted_log provisorio usa a casca de twisted_wood nas laterais e
este topo: os aneis de stripped_twisted_log_top com uma borda de 1 px da casca (como o oak_log_top vanilla).
Quando a arte da Fase 1 chegar, basta trocar o PNG (o id respectcraft:twisted_log ja e o definitivo).

Uso: python3 tools/inverted_realm/make_twisted_log_top.py [--check]
"""
import pathlib
import sys

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[2]
TEX = ROOT / "src/main/resources/assets/respectcraft/textures/block"


def build() -> Image.Image:
    rings = Image.open(TEX / "stripped_twisted_log_top.png").convert("RGBA")
    bark = Image.open(TEX / "twisted_wood.png").convert("RGBA")
    out = rings.copy()
    w, h = out.size
    for y in range(h):
        for x in range(w):
            if x in (0, w - 1) or y in (0, h - 1):
                out.putpixel((x, y), bark.getpixel((x, y)))
    return out


def main() -> int:
    image = build()
    target = TEX / "twisted_log_top.png"
    if "--check" in sys.argv:
        same = target.exists() and Image.open(target).convert("RGBA").tobytes() == image.tobytes()
        print("twisted_log_top.png " + ("OK" if same else "DIFFERS"))
        return 0 if same else 1
    image.save(target)
    print(f"wrote {target.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
