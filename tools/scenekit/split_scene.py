#!/usr/bin/env python3
"""Separa a animacao de uma cena conjunta em um clip por participante (Finisher Scene Authoring Kit).

  # preferido: o JSON do "Export Animations" do plugin GeckoLib (mantem bezier, step, catmullrom, Molang)
  python3 tools/scenekit/split_scene.py CENA.scene.json --export CENA.animation.json --out build/scenekit/out
  # alternativa: direto do projeto salvo (so keyframes lineares/easing; o resto vira ERROR, nunca some)
  python3 tools/scenekit/split_scene.py CENA.scene.json --bbmodel CENA.bbmodel --out DIR [--animation scene.x]
  # integracao no repositorio (Claude Code): grava/mescla os clips nos arquivos de destino, com protecao
  python3 tools/scenekit/split_scene.py ... --install src/main/resources [--force]

Saidas em DIR: <papel>.animation.json (um por participante, nomes originais dos bones), <cena>.export_meta.json
(duracao, palco, eventos com dono, linhas Java), <cena>.events.txt e <cena>.validation.txt.
Codigos: 0 ok | 1 validacao com ERROR | 2 entrada invalida | 3 instalacao recusada (arquivo protegido/editado).
"""
import argparse
import datetime
import hashlib
import json
import pathlib
import shutil
import sys

import scenekit as S

KIT = pathlib.Path(__file__).resolve().parent
INSTALLED = KIT / "installed_manifest.json"
# Arquivos de outros pipelines (geradores com hash) e assets artisticos: o kit nunca grava neles.
PROTECTED_MANIFESTS = ("../player_anim/export_manifest.json", "../rigkit/generated_manifest.json",
                       "generated_manifest.json")
ARTIST_MARKERS = ("/model_sources/", "/rigkit/", "/scenekit/")  # fontes do Blockbench e kits: nunca destino


def _sha(path):
    return hashlib.sha256(pathlib.Path(path).read_bytes()).hexdigest()


def _repo_rel(path):
    try:
        return pathlib.Path(path).resolve().relative_to(KIT.parents[1]).as_posix()
    except ValueError:
        return pathlib.Path(path).resolve().as_posix()


def _protected():
    out = set()
    for m in PROTECTED_MANIFESTS:
        p = (KIT / m).resolve()
        if p.exists():
            data = json.loads(p.read_text(encoding="utf-8"))
            out |= set(data.get("arquivos", data))  # export.py guarda em "arquivos"; os outros na raiz
    return out


def install(files, assets_root, force=False, dry_run=False):
    """files = {"ns:path/arquivo.animation.json": {clip: clip}}. Mescla clip a clip num arquivo do kit; recusa
    arquivo de outro pipeline, asset artistico ou arquivo do kit editado fora daqui (sem --force)."""
    owned = json.loads(INSTALLED.read_text(encoding="utf-8")) if INSTALLED.exists() else {}
    protected = _protected()
    plan, refused = [], []
    for rl, clips in files.items():
        ns, _, rel = rl.partition(":")
        target = pathlib.Path(assets_root) / "assets" / ns / rel
        key = _repo_rel(target)
        if key in protected or any(m in "/" + key for m in ARTIST_MARKERS):
            refused.append(f"{key}: protected (other generator or artist asset) - export to a new file instead")
            continue
        data = {"format_version": "1.8.0", "animations": {}}
        if target.exists():
            if key not in owned or (owned[key] != _sha(target) and not force):
                refused.append(f"{key}: exists and was not written by the scene kit (or edited since): use --force"
                               " (backup in tools/scenekit/.backup/)")
                continue
            data = json.loads(target.read_text(encoding="utf-8"))
        data.setdefault("animations", {}).update(clips)
        data["animations"] = dict(sorted(data["animations"].items()))
        plan.append((target, key, data))
    if refused:
        for r in refused:
            print("REFUSED", r)
        return 3
    for target, key, data in plan:
        print(("would write" if dry_run else "write"), key, sorted(data["animations"]))
        if dry_run:
            continue
        if target.exists() and force:
            bk = KIT / ".backup" / datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
            bk.mkdir(parents=True, exist_ok=True)
            shutil.copy2(target, bk / target.name)
        target.parent.mkdir(parents=True, exist_ok=True)
        tmp = target.with_suffix(".tmp")
        tmp.write_text(S.dumps(data, 2), encoding="utf-8")
        tmp.replace(target)
        owned[key] = _sha(target)
    if not dry_run:
        INSTALLED.write_text(S.dumps(dict(sorted(owned.items())), 2), encoding="utf-8")
    return 0


def run(manifest, export, out, animation=None, geo=None, quiet=False):
    """-> (codigo, clips por papel, meta). Grava as saidas em out."""
    bones = {}
    for role in S.ROLES:
        names = {b["bone"] for b in manifest["bones"].values() if b["role"] == role}
        if geo and geo.get(role):
            runtime = S.geo_bones(S.load_json(geo[role]))
            missing = sorted(names - runtime)
            if missing:
                print(f"WARN  {role}: {len(missing)} bone(s) of the authoring rig not in the runtime geo: {missing[:8]}")
            names &= runtime
        bones[role] = names
    clips, events, rep, scene_anim = S.split(manifest, export, animation, bones)
    meta = S.metadata(manifest, events, rep, scene_anim)
    out = pathlib.Path(out)
    out.mkdir(parents=True, exist_ok=True)
    stem = scene_anim.replace("scene.", "", 1)
    for role in S.ROLES:
        (out / f"{stem}.{role}.animation.json").write_text(
            S.dumps(S.participant_file(role, manifest, clips[role], scene_anim), 2), encoding="utf-8")
    (out / f"{stem}.export_meta.json").write_text(S.dumps(meta, 2), encoding="utf-8")
    (out / f"{stem}.events.txt").write_text(S.events_text(events, manifest), encoding="utf-8")
    (out / f"{stem}.validation.txt").write_text(S.report_text(rep), encoding="utf-8")
    if not quiet:
        print(S.report_text(rep), end="")
        print(f"-> {out} ({meta['errors']} error(s), {meta['warnings']} warning(s))")
    return (1 if meta["errors"] else 0), clips, meta


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("manifest", help="<cena>.scene.json gerado pelo compose_scene.py")
    src = ap.add_mutually_exclusive_group(required=True)
    src.add_argument("--export", help="JSON do Export Animations (GeckoLib) da cena")
    src.add_argument("--bbmodel", help="projeto conjunto salvo pelo Blockbench")
    ap.add_argument("--animation", help="animacao da cena (padrao: a do manifesto)")
    ap.add_argument("--out", required=True)
    ap.add_argument("--geo-attacker")
    ap.add_argument("--geo-victim")
    ap.add_argument("--install", metavar="RESOURCES_ROOT")
    ap.add_argument("--force", action="store_true")
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args(argv)
    try:
        manifest = S.load_json(args.manifest)
        if args.export:
            export = S.load_json(args.export)
        else:
            export, problems = S.compile_bbmodel(S.load_json(args.bbmodel))
            for p in problems:
                print("ERROR", p, "(not exported: use the plugin's Export Animations JSON with --export)")
            if problems:
                return 1
        code, clips, meta = run(manifest, export, args.out, args.animation,
                                {"attacker": args.geo_attacker, "victim": args.geo_victim})
    except (S.SceneError, KeyError, OSError, ValueError) as e:
        print("ERROR", e)
        return 2
    if code or not args.install:
        return code
    files = {}
    for role in S.ROLES:
        p = meta["participants"][role]
        files.setdefault(p["file"], {})[p["animation"]] = clips[role]
    return install(files, args.install, args.force, args.dry_run)


if __name__ == "__main__":
    sys.exit(main())
