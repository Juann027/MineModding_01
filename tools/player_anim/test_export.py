#!/usr/bin/env python3
"""Teste rapido da protecao do exportador (so em diretorio temporario; nao toca no repositorio).

  python3 tools/player_anim/test_export.py

Cenarios: exportacao valida num diretorio vazio, repeticao sem mudancas, .bbmodel editado (simula salvar no
Blockbench) recusado sem --force, --dry-run e --check sem gravar, --force com copia de seguranca, mestre =
blockbench recusado mesmo com --force, validacao que impede gravar, e o bbsync detectando o .bbmodel com a
convencao antiga (espelhada) da Etapa 4.
"""
import contextlib
import copy
import io
import json
import pathlib
import sys
import tempfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import bbsync  # noqa: E402
import export  # noqa: E402

FAILS = []


def run(*args):
    buf = io.StringIO()
    with contextlib.redirect_stdout(buf):
        code = export.main(["--no-report", *args])
    return code, buf.getvalue()


def expect(cond, label):
    print(("ok    " if cond else "FALHOU") + " " + label)
    if not cond:
        FAILS.append(label)


def main():
    with tempfile.TemporaryDirectory() as tmp:
        root = pathlib.Path(tmp)
        out = ["--out", str(root)]
        anim = root / export.TARGETS["animation"]
        clips_bb = root / export.TARGETS["bbmodel_clips"]

        code, log = run(*out)
        expect(code == 0 and log.count("novo") == 3, "exporta 3 arquivos novos num diretorio vazio")
        data = json.loads(anim.read_text(encoding="utf-8"))
        expect(len(data["animations"]) == 12, "o .animation.json gerado tem os 12 clips")
        problems, skipped = bbsync.check_pair(clips_bb, anim)
        expect(not problems and not skipped, "o .bbmodel gerado exporta exatamente o .animation.json (bbsync)")
        expect(anim.read_bytes() == export.ANIM_FILE.read_bytes(), "saida igual ao arquivo do jogo no repositorio")

        code, log = run(*out)
        expect(code == 0 and log.count("igual") == 3 and "gravado(s): 0" in log, "repetir nao regrava nada")

        # Simula salvar no Blockbench: muda um keyframe e reformata o arquivo.
        model = json.loads(clips_bb.read_text(encoding="utf-8"))
        kf = next(iter(model["animations"][1]["animators"].values()))["keyframes"][1]
        kf["data_points"][0]["x"] = "33"
        clips_bb.write_text(json.dumps(model), encoding="utf-8")
        edited = clips_bb.read_bytes()

        code, log = run(*out)
        expect(code == 2 and "EDITADO" in log, "arquivo editado: recusa sem --force (saida 2)")
        expect(clips_bb.read_bytes() == edited, "arquivo editado continua intacto")
        code, _ = run(*out, "--dry-run")
        expect(code == 0 and clips_bb.read_bytes() == edited, "--dry-run nao grava")
        code, _ = run(*out, "--check")
        expect(code == 1 and clips_bb.read_bytes() == edited, "--check acusa diferenca (saida 1) sem gravar")
        problems, _ = bbsync.check_pair(clips_bb, anim)
        expect(len(problems) == 1, "bbsync aponta o keyframe editado e nao exportado")

        (root / "master.json").write_text('{"mestre": "blockbench"}', encoding="utf-8")
        code, log = run(*out, "--force")
        expect(code == 3 and clips_bb.read_bytes() == edited, "mestre = blockbench: recusa ate com --force (saida 3)")
        (root / "master.json").unlink()

        code, log = run(*out, "--force")
        backups = list((root / ".backup").rglob("*.bbmodel"))
        expect(code == 0 and clips_bb.read_bytes() != edited, "--force regrava")
        expect(len(backups) == 1 and backups[0].read_bytes() == edited, "--force guarda copia do arquivo editado")

        # Validacao: um clip com bone inexistente nao pode gravar nada.
        original = export.C.all_clips

        def broken():
            clips = original()
            bad = copy.deepcopy(clips["punch"])
            bad.keys[("BoneQueNaoExiste", "r")] = {0: ((0, 0, 0), "linear"), 10: ((5, 0, 0), "linear")}
            clips["punch"] = bad
            return clips
        export.C.all_clips = broken
        try:
            anim.unlink()
            code, log = run(*out)
        finally:
            export.C.all_clips = original
        expect(code == 4 and not anim.exists(), "validacao falhou: saida 4 e nada gravado")

    # Regressao: o .bbmodel da Etapa 4 (valores do jogo copiados sem a conversao do Blockbench) e detectado.
    model = json.loads(export.bbmodel.OUT_CLIPS.read_text(encoding="utf-8"))
    for anim_ in model["animations"]:
        for animator in anim_["animators"].values():
            for kf in animator["keyframes"]:
                p = kf["data_points"][0]
                x, y, z = bbsync.flip(kf["channel"], [float(p["x"]), float(p["y"]), float(p["z"])])
                p.update(x=str(x), y=str(y), z=str(z))
    game = json.loads(export.ANIM_FILE.read_text(encoding="utf-8"))["animations"]
    exported, _ = bbsync.animations_from_bbmodel(model)
    expect(len(bbsync.diff_clips(game, exported)) > 100, "bbsync detecta .bbmodel espelhado (bug da Etapa 4)")

    print(f"\n{len(FAILS)} falha(s)")
    return 1 if FAILS else 0


if __name__ == "__main__":
    sys.exit(main())
