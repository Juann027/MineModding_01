#!/usr/bin/env python3
"""Empacota o Finisher Scene Authoring Kit num ZIP independente (templates, manifestos, exemplos, guias e as
ferramentas de composicao/separacao, so biblioteca padrao).

  python3 tools/scenekit/package_kit.py [--out build/scenekit]
"""
import argparse
import hashlib
import pathlib
import sys
import zipfile

HERE = pathlib.Path(__file__).resolve().parent
REPO = HERE.parents[1]
TOOLS = ("scenekit.py", "compose_scene.py", "split_scene.py")


def _add(z, name, data):
    info = zipfile.ZipInfo("respectcraft-scenekit/" + name, (2026, 1, 1, 0, 0, 0))
    info.compress_type = zipfile.ZIP_DEFLATED
    z.writestr(info, data)


def build(out_dir):
    out = pathlib.Path(out_dir) / "respectcraft-scenekit-v1.zip"
    out.parent.mkdir(parents=True, exist_ok=True)
    kit = REPO / "scenekit"
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        for path in sorted(kit.rglob("*")):
            if path.is_file():
                _add(z, path.relative_to(kit).as_posix(), path.read_bytes())
        for f in TOOLS:
            _add(z, "tools/" + f, (HERE / f).read_bytes())
    return out


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--out", default=str(REPO / "build/scenekit"))
    out = build(ap.parse_args(argv).out)
    print(out, hashlib.sha256(out.read_bytes()).hexdigest())
    return 0


if __name__ == "__main__":
    sys.exit(main())
