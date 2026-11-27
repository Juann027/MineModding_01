#!/usr/bin/env python3
"""Compoe o projeto conjunto do Blockbench (GeckoLib Animated Model) de uma finalizacao: ATTACKER (jogador) e VICTIM
(CustomRig ou jogador) no palco do Finisher Engine. Nunca altera os modelos de origem.

  python3 tools/scenekit/compose_scene.py scenekit/scenes/player_vs_strawberry/scene.spec.json --out build/scenekit
  python3 tools/scenekit/compose_scene.py SPEC --out DIR [--textures atlas|multi] [--distance 1.4] [--lateral 0.2]
                                          [--duration 80] [--name minha_cena]

Saidas em DIR: <name>.bbmodel (abra no Blockbench Web) e <name>.scene.json (manifesto: mapa explicito de bones,
palco, partes e sockets; o split_scene.py precisa dele). Codigos: 0 ok | 2 erro na composicao.
"""
import argparse
import pathlib
import sys

import scenekit as S


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("spec")
    ap.add_argument("--out", required=True)
    ap.add_argument("--textures", choices=("atlas", "multi"), default="atlas",
                    help="atlas = uma textura so no projeto de autoria (padrao); multi = as texturas originais")
    ap.add_argument("--distance", type=float, help="distancia do palco em blocos (sobrepoe o binding/familia)")
    ap.add_argument("--lateral", type=float)
    ap.add_argument("--yaw-offset", type=float)
    ap.add_argument("--duration", type=int, help="duracao da cena em ticks")
    ap.add_argument("--name")
    args = ap.parse_args(argv)
    spec_path = pathlib.Path(args.spec)
    spec = S.load_json(spec_path)
    if args.name:
        spec["name"] = args.name
    if args.duration:
        spec["duration_ticks"] = args.duration
    for k, v in (("distance", args.distance), ("lateral", args.lateral), ("yaw_offset", args.yaw_offset)):
        if v is not None:
            spec.setdefault("stage", {})[k] = v
    try:
        model, manifest = S.compose(spec, spec_path.parent, args.textures)
    except (S.SceneError, KeyError, OSError) as e:
        print("ERROR", e)
        return 2
    out = pathlib.Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    (out / f"{spec['name']}.bbmodel").write_text(S.dumps(model), encoding="utf-8")
    (out / f"{spec['name']}.scene.json").write_text(S.dumps(manifest, 2), encoding="utf-8")
    st = manifest["stage"]
    print(f"{spec['name']}: {len(model['groups'])} groups, {len(model['elements'])} cubes, {len(model['textures'])}"
          f" texture(s) ({args.textures}); victim at {st['distance_blocks']} blocks ({st['source']}), scale"
          f" {st['victim_scale']} -> {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
