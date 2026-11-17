#!/usr/bin/env python3
"""Gera os clips da Etapa 4 a partir de poses.py/clips.py, valida e grava com protecao contra sobrescrita.

  python3 tools/player_anim/export.py --dry-run          # so mostra o que mudaria (nada e gravado)
  python3 tools/player_anim/export.py                    # grava se nenhum arquivo foi editado fora daqui
  python3 tools/player_anim/export.py --check            # saida 1 se algo mudaria ou houver conflito (CI)
  python3 tools/player_anim/export.py --out /tmp/x       # grava em outro diretorio (mesmos caminhos relativos)
  python3 tools/player_anim/export.py --force            # sobrescreve arquivos editados (copia antes em .backup/)
  python3 tools/player_anim/export.py --sheets DIR       # tambem gera folhas de quadros (PNG) em DIR

Saidas (caminhos relativos a raiz de saida; padrao = repositorio):
  src/main/resources/assets/tupinicraft/animations/player/tupinicraft_player_combat.animation.json  (artefato do jogo)
  model_sources/player/tupinicraft_player_combat.bbmodel   (rig + todos os clips, abre no Blockbench)
  model_sources/player/tupinicraft_combo_scene.bbmodel     (dois rigs, coreografia lado a lado)

Protecao:
  - export_manifest.json guarda o hash do que este script gravou por ultimo. Arquivo existente com hash
    diferente = editado fora do gerador (Blockbench, a mao) -> o script recusa e nao grava NADA sem --force.
  - master.json diz quem e a fonte de verdade desses arquivos: "gerador" (este script) ou "blockbench"
    (edicao manual; o script nunca grava no repositorio, nem com --force).
  - Tudo e validado antes (estrutura, bones do rig, e a ida e volta .bbmodel -> export do Blockbench -> jogo);
    a gravacao e atomica (arquivo temporario + rename).
Codigos de saida: 0 ok | 1 --check com diferencas | 2 conflito | 3 mestre = blockbench | 4 validacao falhou.
"""
import argparse
import datetime
import hashlib
import json
import math
import os
import pathlib
import shutil
import sys

import numpy as np

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from rig import Rig, REPO, GEO, sample_clip  # noqa: E402
import clips as C  # noqa: E402
import bbmodel  # noqa: E402
import bbsync  # noqa: E402

TOOL_DIR = pathlib.Path(__file__).resolve().parent
ANIM_FILE = REPO / "src/main/resources/assets/tupinicraft/animations/player/tupinicraft_player_combat.animation.json"
TARGETS = {  # chave -> caminho relativo a raiz de saida
    "animation": ANIM_FILE.relative_to(REPO),
    "bbmodel_clips": bbmodel.OUT_CLIPS.relative_to(REPO),
    "bbmodel_scene": bbmodel.OUT_SCENE.relative_to(REPO),
}
MANIFEST_NAME = "export_manifest.json"
MASTER_FILE = TOOL_DIR / "master.json"
BACKUP_DIR = TOOL_DIR / ".backup"
PREFIX = "animation.tupinicraft.player."
STAGE_DISTANCE = 1.15  # blocos entre executor e alvo (palco da Finisher Engine)
EASINGS = {"linear", "step", "catmullrom"} | {f"ease{m}{f}" for m in ("In", "Out", "InOut")
                                               for f in ("Sine", "Quad", "Cubic", "Quart", "Quint", "Circ")}


def build_animation_data(clips):
    return {"format_version": "1.8.0", "animations": {PREFIX + n: c.to_json() for n, c in clips.items()}}


def build_outputs(clips, data):
    """Conteudo de cada arquivo (texto), sem gravar nada."""
    return {
        "animation": json.dumps(data, indent="\t", ensure_ascii=False) + "\n",
        "bbmodel_clips": bbmodel.dumps(bbmodel.build_clips(clips)),
        "bbmodel_scene": bbmodel.dumps(bbmodel.build_scene(clips)),
    }


# ------------------------------------------------------------------ validacao (antes de gravar)

def validate_animations(data):
    errors = []
    geo = json.loads(GEO.read_text(encoding="utf-8"))["minecraft:geometry"][0]
    rig_bones = {b["name"] for b in geo["bones"]}
    for name, clip in data["animations"].items():
        length = clip.get("animation_length")
        if not isinstance(length, (int, float)) or length <= 0:
            errors.append(f"{name}: animation_length invalido ({length})")
            continue
        if clip.get("loop", False) not in (True, False, "hold_on_last_frame"):
            errors.append(f"{name}: loop invalido")
        for bone, chans in clip.get("bones", {}).items():
            if bone not in rig_bones:
                errors.append(f"{name}: bone '{bone}' nao existe no rig")
            for ch, keys in chans.items():
                if ch not in ("rotation", "position", "scale"):
                    errors.append(f"{name}/{bone}: canal desconhecido {ch}")
                for t, entry in keys.items():
                    vec = entry.get("vector", [])
                    if len(vec) != 3 or not all(isinstance(v, (int, float)) and math.isfinite(v) for v in vec):
                        errors.append(f"{name}/{bone}/{ch}@{t}: vetor invalido {vec}")
                    if not 0 <= float(t) <= length + 1e-6:
                        errors.append(f"{name}/{bone}/{ch}@{t}: fora de [0, {length}]")
                    if entry.get("easing", "linear") not in EASINGS:
                        errors.append(f"{name}/{bone}/{ch}@{t}: easing desconhecido {entry.get('easing')}")
    return errors


def validate_outputs(data, texts):
    errors = []
    for key in ("bbmodel_clips", "bbmodel_scene"):
        model = json.loads(texts[key])
        errors += [f"{TARGETS[key].name}: {e}" for e in _validate_bbmodel(model)]
    # Ida e volta: o que o Blockbench exportaria destes .bbmodel tem de ser exatamente o arquivo do jogo.
    clips_model = json.loads(texts["bbmodel_clips"])
    exported, skipped = bbsync.animations_from_bbmodel(clips_model)
    errors += [f"ida e volta (clips): {p}" for p in bbsync.diff_clips(data["animations"], exported)]
    scene_model = json.loads(texts["bbmodel_scene"])
    for prefix, clip in bbsync.SCENE_CLIPS.items():
        part, more = bbsync.animations_from_bbmodel(scene_model, prefix)
        skipped += more
        part = {clip: part["combo_scene"]}
        errors += [f"ida e volta (cena): {p}" for p in bbsync.diff_clips(data["animations"], part, only=[clip])]
    errors += [f"nao verificavel: {s}" for s in skipped]
    return errors


def _validate_bbmodel(model):
    errors = []
    if model.get("meta", {}).get("model_format") != "geckolib_model":
        errors.append("meta.model_format deveria ser geckolib_model")
    uuids = [g["uuid"] for g in model["groups"]] + [e["uuid"] for e in model["elements"]]
    if len(uuids) != len(set(uuids)):
        errors.append("uuids repetidos entre grupos/cubos")
    names = [g["name"] for g in model["groups"]]
    if len(names) != len(set(names)):
        errors.append("nomes de bones repetidos")
    groups = {g["uuid"]: g["name"] for g in model["groups"]}

    def walk(nodes):
        for n in nodes:
            if isinstance(n, str):
                if n not in uuids:
                    errors.append(f"outliner cita cubo inexistente {n}")
            else:
                if n["uuid"] not in groups:
                    errors.append(f"outliner cita grupo inexistente {n['uuid']}")
                walk(n.get("children", []))
    walk(model["outliner"])
    for anim in model.get("animations", []):
        for uuid, animator in anim["animators"].items():
            if groups.get(uuid) != animator.get("name"):
                errors.append(f"{anim['name']}: animador {animator.get('name')} nao corresponde ao grupo {uuid}")
    return errors


# ------------------------------------------------------------------ gravacao protegida

def sha(text_or_bytes):
    b = text_or_bytes.encode("utf-8") if isinstance(text_or_bytes, str) else text_or_bytes
    return hashlib.sha256(b).hexdigest()


def load_manifest(path):
    if path.exists():
        return json.loads(path.read_text(encoding="utf-8")).get("arquivos", {})
    return {}


def save_manifest(path, entries):
    doc = {"_leia": "Gerado por tools/player_anim/export.py: hash do ultimo conteudo gravado por ele. Arquivo com "
                    "hash diferente foi editado fora do gerador e nao e sobrescrito sem --force. Nao edite.",
           "arquivos": dict(sorted(entries.items()))}
    _atomic_write(path, json.dumps(doc, indent=1, ensure_ascii=False) + "\n")


def plan(root, texts, manifest):
    """[(chave, caminho, estado)] com estado em: novo, igual, atualizar, EDITADO."""
    rows = []
    for key, rel in TARGETS.items():
        path = root / rel
        if not path.exists():
            state = "novo"
        else:
            current = sha(path.read_bytes())
            if current == sha(texts[key]):
                state = "igual"
            elif manifest.get(rel.as_posix()) == current:
                state = "atualizar"
            else:
                state = "EDITADO"
        rows.append((key, path, state))
    return rows


def _atomic_write(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_name(path.name + ".tmp-export")
    tmp.write_text(text, encoding="utf-8")
    os.replace(tmp, path)


def read_master(path=MASTER_FILE):
    if not path.exists():
        return "gerador"
    master = json.loads(path.read_text(encoding="utf-8")).get("mestre", "gerador")
    if master not in ("gerador", "blockbench"):
        raise SystemExit(f"master.json: 'mestre' deve ser 'gerador' ou 'blockbench' (veio '{master}')")
    return master


def _boxes_world(rig, pose, place):
    out = []
    for name, corners in rig.boxes(pose):
        w = (place @ np.c_[corners, np.ones(8)].T).T[:, :3]
        out.append((name, w))
    return out


def _inside(points, box, margin=0.25):
    c0, e1, e2, e3 = box[0], box[4] - box[0], box[2] - box[0], box[1] - box[0]
    m = np.column_stack([e1, e2, e3])
    try:
        coef = np.linalg.solve(m, (points - c0).T).T
    except np.linalg.LinAlgError:
        return 0.0
    # profundidade aproximada (px) do ponto mais enterrado
    depth = 0.0
    for a in coef:
        if np.all(a > 0) and np.all(a < 1):
            d = min(min(a[i], 1 - a[i]) * np.linalg.norm([e1, e2, e3][i]) for i in range(3))
            depth = max(depth, d)
    return depth if depth > margin else 0.0


def _box_distance(p, box):
    """Distancia (px) do ponto ate o cubo orientado; negativa = dentro."""
    c0, e = box[0], [box[4] - box[0], box[2] - box[0], box[1] - box[0]]
    coef = np.linalg.solve(np.column_stack(e), p - c0)
    near = c0 + sum(np.clip(coef[i], 0, 1) * e[i] for i in range(3))
    d = float(np.linalg.norm(p - near))
    return -min(min(coef[i], 1 - coef[i]) * np.linalg.norm(e[i]) for i in range(3)) if d == 0 else d


def _probe(w):
    face = [w[[0, 1, 3, 2]].mean(0), w[[4, 6, 7, 5]].mean(0), w[[0, 4, 5, 1]].mean(0), w[[2, 3, 7, 6]].mean(0),
            w[[0, 2, 6, 4]].mean(0), w[[1, 5, 7, 3]].mean(0)]
    return np.vstack([w, face, w.mean(0, keepdims=True)])


def placements():
    from preview import placement
    d = STAGE_DISTANCE * 16
    return placement(), placement(0, -d, 180)


def report(clips, rig):
    print("clip".ljust(34), "ticks  chao_min(px)")
    for name, clip in clips.items():
        js = clip.to_json()
        low = min(min(c[:, 1].min() for _, c in rig.boxes(sample_clip(js, t / 20))) for t in range(clip.length + 1))
        flag = "  <-- atravessa o chao" if low < -0.6 else ""
        print(name.ljust(34), str(clip.length).rjust(5), f"{low:7.2f}{flag}")
    # coreografia: contato dos golpes e interpenetracao
    pa, pv = placements()
    att = clips["finisher.combo_attacker"].to_json()
    vic = clips["finisher.combo_victim"].to_json()
    worst = (0.0, -1, "")
    for t in range(C.COMBO_LEN + 1):
        A = _boxes_world(rig, sample_clip(att, t / 20), pa)
        V = _boxes_world(rig, sample_clip(vic, t / 20), pv)
        for an, ab in A:
            for vn, vb in V:
                d = max(_inside(_probe(ab), vb), _inside(_probe(vb), ab))
                ok_contact = t in range(C.IMPACT_PUNCH - 1, C.IMPACT_PUNCH + 3) or t in range(C.IMPACT_KICK - 1, C.IMPACT_KICK + 4)
                if d > worst[0] and not ok_contact:
                    worst = (d, t, f"{an} x {vn}")
    print(f"maior interpenetracao fora dos impactos: {worst[0]:.2f} px no tick {worst[1]} ({worst[2]})")
    for label, tick, bone, local in (("soco", C.IMPACT_PUNCH, "RightArm", (6, 12, 0)),
                                     ("chute", C.IMPACT_KICK, "RightLeg", (1.9, 0, 0))):
        tip = (pa @ np.r_[rig.point(to_pose(sample_clip(att, tick / 20)), bone, local), 1])[:3]
        V = _boxes_world(rig, sample_clip(vic, tick / 20), pv)
        body = [w for n, w in V if n == "Body"][0]
        dist = _box_distance(tip, body)
        print(f"{label}: ponta a {dist:.1f} px da superficie do tronco do alvo no tick {tick} (altura {tip[1]:.1f} px)")


# Pares tocados em sequencia sem transicao visivel (PlayerAnimations.FALL_AND_RECOVER): fim de um = inicio do outro.
CHAINS = (("knockdown", "hands_and_knees"), ("hands_and_knees", "hands_and_knees"), ("hands_and_knees", "get_up"))


def chain_gaps(clips):
    """[(a, b, maior diferenca em graus/px entre o ultimo quadro de a e o primeiro de b)]."""
    out = []
    for a, b in CHAINS:
        ja, jb = clips[a].to_json(), clips[b].to_json()
        pa, pb = sample_clip(ja, ja["animation_length"]), sample_clip(jb, 0)
        gap = 0.0
        for bone in set(pa) | set(pb):
            for ch in ("rotation", "position"):
                va = np.array(pa.get(bone, {}).get(ch, (0, 0, 0)), float)
                vb = np.array(pb.get(bone, {}).get(ch, (0, 0, 0)), float)
                gap = max(gap, float(np.abs(va - vb).max()))
        out.append((a, b, gap))
    return out


def to_pose(p):
    return p


def sheets(clips, rig, out):
    from preview import render, Camera, sheet, PALETTES, placement
    out = pathlib.Path(out)
    out.mkdir(parents=True, exist_ok=True)
    for name, clip in clips.items():
        if name.startswith("finisher."):
            continue
        js = clip.to_json()
        step = max(1, clip.length // 12)
        ims = [render([(rig, sample_clip(js, t / 20), placement(), PALETTES["executor"])],
                      Camera(yaw=70, pitch=10, dist=66, target=(0, 13, 0), size=(170, 170)), label=f"{name} t{t}")
               for t in range(0, clip.length + 1, step)]
        sheet(ims, 7).save(out / f"clip_{name}.png")
    pa, pv = placements()
    att = clips["finisher.combo_attacker"].to_json()
    vic = clips["finisher.combo_victim"].to_json()
    ticks = list(range(0, C.COMBO_LEN + 1, 5))
    for view, yaw in (("lado", 90), ("diag", 40)):
        ims = [render([(rig, sample_clip(att, t / 20), pa, PALETTES["executor"]),
                       (rig, sample_clip(vic, t / 20), pv, PALETTES["alvo"])],
                      Camera(yaw=yaw, pitch=12, dist=95, target=(0, 12, -8), size=(200, 160)), label=f"t{t}")
               for t in ticks]
        sheet(ims, 6).save(out / f"combo_{view}.png")


def main(argv=None):
    ap = argparse.ArgumentParser(description="Exporta os clips do jogador com protecao contra sobrescrita.")
    ap.add_argument("--dry-run", action="store_true", help="so mostra o que mudaria; nao grava nada")
    ap.add_argument("--check", action="store_true", help="como --dry-run, mas sai com 1 se algo mudaria")
    ap.add_argument("--force", action="store_true", help="sobrescreve arquivos editados (faz copia em .backup/)")
    ap.add_argument("--out", metavar="DIR", help="raiz de saida (padrao: o repositorio)")
    ap.add_argument("--sheets", metavar="DIR", help="gera folhas de quadros (PNG) para conferir")
    ap.add_argument("--no-report", action="store_true", help="pula o relatorio de chao/contato")
    args = ap.parse_args(argv)

    root = pathlib.Path(args.out).resolve() if args.out else REPO
    in_repo = root == REPO
    manifest_path = (TOOL_DIR / MANIFEST_NAME) if in_repo else root / MANIFEST_NAME
    clips = C.all_clips()
    data = build_animation_data(clips)
    errors = validate_animations(data)
    errors += [f"encadeamento {a} -> {b}: pose salta {gap:.2f} (graus/px)" for a, b, gap in chain_gaps(clips) if gap > 0.05]
    if not errors:
        texts = build_outputs(clips, data)
        errors = validate_outputs(data, texts)
    if errors:
        print("VALIDACAO FALHOU; nada foi gravado:")
        for e in errors[:40]:
            print("  ", e)
        return 4
    print("validacao: ok (estrutura, bones do rig, encadeamentos, ida e volta pelo formato do Blockbench)")

    if not args.no_report and not args.check:
        rig = Rig()
        report(clips, rig)
        if args.sheets:
            sheets(clips, rig, args.sheets)

    manifest = load_manifest(manifest_path)
    rows = plan(root, texts, manifest)
    # Fora do repositorio o mestre so vale se a raiz tiver o proprio master.json (usado nos testes).
    master = read_master(MASTER_FILE if in_repo else root / "master.json")
    print(f"\nraiz de saida: {root}" + ("" if in_repo else " (fora do repositorio)") + f" | mestre: {master}")
    for _, path, state in rows:
        print(f"  {state:10s} {path.relative_to(root) if path.is_relative_to(root) else path}")
    conflicts = [r for r in rows if r[2] == "EDITADO"]
    changes = [r for r in rows if r[2] != "igual"]

    if args.check:
        if changes:
            print("\n--check: os arquivos nao correspondem ao gerador.")
        return 1 if changes else 0
    if args.dry_run:
        print("\n--dry-run: nada foi gravado.")
        return 0
    if master == "blockbench" and changes:
        print("\nRECUSADO: tools/player_anim/master.json define o Blockbench como fonte de verdade destes arquivos."
              "\nNada foi gravado. Para gerar em outro lugar use --out DIR; para voltar ao gerador, mude"
              "\n\"mestre\" para \"gerador\" (as edicoes feitas no Blockbench serao substituidas).")
        return 3
    if conflicts and not args.force:
        print("\nRECUSADO: arquivo(s) EDITADO(s) fora do gerador (ex.: salvo pelo Blockbench). Nada foi gravado."
              "\n  - para guardar a edicao: copie os valores para poses.py/clips.py, ou marque o Blockbench como"
              "\n    mestre em tools/player_anim/master.json;"
              "\n  - para comparar: --out /tmp/gerado e um diff;"
              "\n  - para descartar a edicao: --force (copia os arquivos atuais para tools/player_anim/.backup/).")
        return 2
    if conflicts:
        stamp = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
        for _, path, _ in conflicts:
            dest = (BACKUP_DIR if in_repo else root / ".backup") / stamp / path.relative_to(root)
            dest.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(path, dest)
            print(f"  copia de seguranca: {dest.relative_to(REPO) if dest.is_relative_to(REPO) else dest}")
    written = 0
    for key, path, state in rows:
        if state != "igual":
            _atomic_write(path, texts[key])
            written += 1
        manifest[TARGETS[key].as_posix()] = sha(texts[key])
    save_manifest(manifest_path, manifest)
    print(f"\ngravado(s): {written} arquivo(s); manifesto: {manifest_path.relative_to(root) if manifest_path.is_relative_to(root) else manifest_path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
