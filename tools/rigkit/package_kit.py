#!/usr/bin/env python3
"""Empacota o kit de rigs (Etapa 4.12) num ZIP independente: templates, contratos, exemplos, prompt e o validador.

  python3 tools/rigkit/package_kit.py [--out build/rigkit]

O ZIP abre fora do repositorio: `python3 tools/validate_rig.py --rig fruit_humanoid_v1/fruit_humanoid_v1.rig.json ...`.
"""
import argparse
import hashlib
import pathlib
import sys
import zipfile

import rigkit as K

FILES_FROM_TOOLS = ("validate_rig.py", "rigkit.py")


def build(out_dir):
    contract = K.load_json(K.ASSETS / "rigs/fruit_humanoid_v1.rig.json")
    name = f"respectcraft-rigkit-fruit_humanoid_v1-c{contract['contract_version']}.zip"
    out = pathlib.Path(out_dir) / name
    out.parent.mkdir(parents=True, exist_ok=True)
    kit = K.REPO / "rigkit"
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        for path in sorted(kit.rglob("*")):
            if path.is_file():
                info = zipfile.ZipInfo("respectcraft-rigkit/" + path.relative_to(kit).as_posix(), (2026, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                z.writestr(info, path.read_bytes())
        for f in FILES_FROM_TOOLS:
            info = zipfile.ZipInfo("respectcraft-rigkit/tools/" + f, (2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(info, (K.KIT_DIR / f).read_bytes())
    return out


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--out", default=str(K.REPO / "build/rigkit"))
    args = ap.parse_args(argv)
    out = build(args.out)
    print(out, hashlib.sha256(out.read_bytes()).hexdigest())
    return 0


if __name__ == "__main__":
    sys.exit(main())
