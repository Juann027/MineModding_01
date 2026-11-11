#!/usr/bin/env python3
"""Structural resource validation; does not replace loading Minecraft."""
from pathlib import Path
import json, re, struct
root=Path(__file__).resolve().parents[1]
resources=root/'src/main/resources'
count=0
for f in resources.rglob('*.json'):
    json.loads(f.read_text()); count+=1
config=(root/'src/main/java/com/tupinicraft/tigrinho/TigrinhoConfig.java').read_text()
entries=re.findall(r'"([a-z]+\|tupinicraft:textures/[^";]+);?"',config)
assert len(entries)==7,entries
weight=0; weighted_return=0; triple=0
for entry in entries:
    name,texture,w,h,weight_s,mult,reward,n=entry.split('|')
    namespace,path=texture.split(':');data=(resources/'assets'/namespace/path).read_bytes()
    assert data[:8]==b'\x89PNG\r\n\x1a\n'
    assert struct.unpack('>II',data[16:24])==(int(w),int(h)),name
    weight+=int(weight_s); weighted_return+=int(weight_s)**3*int(mult);triple+=int(weight_s)**3
model=json.loads((resources/'assets/tupinicraft/models/block/tigrinho_machine.json').read_text())
for texture in model['textures'].values():
    if texture.startswith('tupinicraft:'):
        assert (resources/'assets/tupinicraft/textures'/f'{texture.split(":")[1]}.png').exists(),texture
for required in ['assets/tupinicraft/models/item/tigrinho_machine.json','assets/tupinicraft/blockstates/tigrinho_machine.json','data/tupinicraft/loot_table/blocks/tigrinho_machine.json','data/tupinicraft/recipe/tigrinho_machine.json','data/minecraft/tags/block/mineable/pickaxe.json']:
    assert (resources/required).exists(),required
print(f'JSON: {count} arquivos válidos. Texturas: {len(entries)} dimensões/referências válidas. Modelo/receita/loot/tag presentes.')
print(f'P(trinca)={triple/weight**3:.6%}; retorno esperado bruto={weighted_return/weight**3:.6%}; pesos totais={weight}.')
