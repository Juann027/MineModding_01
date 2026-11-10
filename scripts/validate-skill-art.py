#!/usr/bin/env python3
"""Valida referências e texturas das árvores sem instalar Pillow ou iniciar o Minecraft."""
import json
import argparse
import zipfile
from pathlib import Path
import re
import struct
import zlib

root = Path(__file__).resolve().parents[1]
assets = root / "src/main/resources/assets/tupinicraft"
data = root / "src/main/resources/data/tupinicraft/tupinicraft_skilltree"
source = (root / "src/main/java/com/tupinicraft/registry/ModItems.java").read_text()
icon_block = source.split("for (String icon : java.util.List.of(", 1)[1].split("))", 1)[0]
registered = set(re.findall(r'"([a-z0-9_]+)"', icon_block))
assert registered, "Nenhum ícone registrado"

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--jar", type=Path, help="Valida também os recursos dentro do JAR compilado")
args = parser.parse_args()
if args.jar and args.jar.is_dir():
    jars = sorted(args.jar.glob("tupinicraft-*.jar"))
    assert len(jars) == 1, "Esperado exatamente um JAR do TupiniCraft no diretório"
    args.jar = jars[0]
archive = zipfile.ZipFile(args.jar) if args.jar else None

def resource(relative):
    if archive:
        return archive.read("assets/tupinicraft/" + relative)
    return (assets / relative).read_bytes()

for item in sorted({"urna_eletronica", "cabo_eleitoral_spawn_egg"} | {"icone_" + name for name in registered}):
    model = json.loads(resource(f"models/item/{item}.json"))
    for texture in model.get("textures", {}).values():
        namespace, path = texture.split(":", 1)
        assert namespace == "tupinicraft"
        # A existência do PNG não basta: o atlas vanilla só coleta item/ e block/ automaticamente.
        assert path.startswith(("item/", "block/")), f"Textura fora do atlas padrão: {texture}"
        assert resource(f"textures/{path}.png"), f"Textura ausente: {texture}"

for name in sorted(registered):
    raw = resource(f"textures/item/skilltree/{name}.png")
    assert raw[:8] == b"\x89PNG\r\n\x1a\n", f"PNG inválido: {name}"
    width, height, depth, color = struct.unpack(">IIBB", raw[16:26])
    assert (width, height, depth, color) == (128, 128, 8, 6), f"Formato inesperado: {name}"
    # Verifica CRC de todos os chunks, incluindo os dados comprimidos.
    offset = 8
    while offset < len(raw):
        length = struct.unpack(">I", raw[offset:offset + 4])[0]
        chunk = raw[offset + 4:offset + 8 + length]
        crc = struct.unpack(">I", raw[offset + 8 + length:offset + 12 + length])[0]
        assert zlib.crc32(chunk) & 0xFFFFFFFF == crc, f"CRC incorreto: {name}"
        offset += length + 12
    model = json.loads(resource(f"models/item/icone_{name}.json"))
    assert model["parent"] == "minecraft:item/generated"
    assert model["textures"]["layer0"] == f"tupinicraft:item/skilltree/{name}"
    for language in ("pt_br", "en_us"):
        translations = json.loads(resource(f"lang/{language}.json"))
        assert f"item.tupinicraft.icone_{name}" in translations

references = 0
for path in data.glob("*/*.json"):
    icon = json.loads(path.read_text())["icone"]
    assert icon.startswith("tupinicraft:icone_"), f"Ícone não adaptado: {path.name}"
    assert icon.removeprefix("tupinicraft:icone_") in registered, f"Ícone não registrado: {icon}"
    references += 1
print(f"PASS {len(registered)} ícones RGBA, modelos, traduções e {references} referências do datapack")

for language in ("pt_br", "en_us"):
    assert json.loads(resource(f"lang/{language}.json"))["itemGroup.tupinicraft"] == "TupiniCraft"
if archive:
    archive.close()
    print("PASS JAR: recursos dos 35 itens presentes e texturas no atlas padrão")
