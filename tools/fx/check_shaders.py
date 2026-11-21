#!/usr/bin/env python3
"""Compila os shaders fx do RespectCraft como a RimuLib 1.3.0 os monta (Etapa 4.10).

A RimuLib junta: common.glsl dela (que comeca com #version 150), uma linha "#line 1 1" e o arquivo; cada linha
'#include "x"' vira o conteudo de assets/<ns>/shaders/fx/x (sem recursao). Este script faz o mesmo e passa o
resultado ao glslangValidator (GLSL 1.50, fragmento). Tambem confere que a copia de calamidade_fx.glsl e identica a
do jar do Calamidade_Awakening em libs/.

Uso: python3 tools/fx/check_shaders.py   (depois de ./gradlew build: precisa do jar da RimuLib no cache do Gradle)
Sem glslangValidator instalado (apt install glslang-tools), so avisa e sai com 0, a menos que --strict.
"""
import glob
import os
import shutil
import subprocess
import sys
import tempfile
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
FX = os.path.join(ROOT, "src", "main", "resources", "assets", "respectcraft", "shaders", "fx")


def find_jar(patterns):
    for pattern in patterns:
        hits = sorted(glob.glob(os.path.expanduser(pattern), recursive=True))
        if hits:
            return hits[0]
    return None


def main():
    strict = "--strict" in sys.argv
    failures = 0
    # 1. calamidade_fx.glsl igual a do motor.
    cal = find_jar([os.path.join(ROOT, "libs", "calamidade_awakening-*.jar")])
    if cal is None:
        print("aviso: jar do Calamidade nao encontrado em libs/ (rode ./gradlew baixarCalamidade)")
    else:
        with zipfile.ZipFile(cal) as z:
            engine = z.read("assets/calamidade_awakening/shaders/fx/calamidade_fx.glsl")
        with open(os.path.join(FX, "calamidade_fx.glsl"), "rb") as f:
            ours = f.read()
        if engine != ours:
            print("ERRO: assets/respectcraft/shaders/fx/calamidade_fx.glsl difere da copia do motor em", cal)
            failures += 1
        else:
            print("calamidade_fx.glsl: igual a do motor")
    # 2. common.glsl da RimuLib.
    rimu = find_jar(["~/.gradle/caches/modules-2/files-2.1/curse.maven/rimulib-1753907/**/*.jar"])
    validator = shutil.which("glslangValidator")
    if rimu is None or validator is None:
        msg = "jar da RimuLib nao encontrado no cache do Gradle" if rimu is None else "glslangValidator nao instalado"
        print("aviso:", msg, "- shaders nao compilados")
        return 1 if strict or failures else 0
    with zipfile.ZipFile(rimu) as z:
        common = z.read("assets/rimulib/shaders/fx/common.glsl").decode("utf-8")
    for name in sorted(os.listdir(FX)):
        if not name.endswith(".fsh"):
            continue
        with open(os.path.join(FX, name), encoding="utf-8") as f:
            lines = f.read().split("\n")
        body = []
        for i, line in enumerate(lines, 1):
            s = line.strip()
            if s.startswith('#include "') and s.endswith('"'):
                inc = s[len('#include "'):-1]
                path = os.path.join(FX, inc)
                if not os.path.exists(path):
                    print("ERRO:", name, "inclui", inc, "que nao existe em assets/respectcraft/shaders/fx")
                    failures += 1
                    continue
                with open(path, encoding="utf-8") as f:
                    body.append(f.read())
                body.append("#line %d 1" % (i + 1))
            else:
                body.append(line)
        source = common + "\n#line 1 1\n" + "\n".join(body) + "\n"
        with tempfile.NamedTemporaryFile("w", suffix=".frag", delete=False, encoding="utf-8") as tmp:
            tmp.write(source)
        try:
            out = subprocess.run([validator, "-S", "frag", tmp.name], capture_output=True, text=True)
        finally:
            os.unlink(tmp.name)
        if out.returncode != 0:
            print("ERRO:", name)
            print(out.stdout.strip(), out.stderr.strip())
            failures += 1
        else:
            print(name + ": OK")
    print("%d falha(s)" % failures)
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
