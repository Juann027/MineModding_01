#!/usr/bin/env python3
"""Resource regression checks; not a replacement for a Java build or in-game test."""
import hashlib
import json
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/tupinicraft'
source = json.loads((ROOT / 'model_sources/leao_do_fim.bbmodel').read_text())
geo = json.loads((ASSETS / 'geo/entity/leao_do_fim.geo.json').read_text())['minecraft:geometry'][0]
clips = json.loads((ASSETS / 'animations/entity/leao_do_fim.animation.json').read_text())['animations']
bones = {b['name']: b for b in geo['bones']}
assert len(bones) == len(source['groups']) == 45
assert sum(len(b.get('cubes', [])) for b in bones.values()) == len(source['elements']) == 127
for g in source['groups']:
    b = bones[g['name']]
    assert [-b['pivot'][0], *b['pivot'][1:]] == g['origin']
    r = b.get('rotation', [0, 0, 0])
    assert [-r[0], -r[1], r[2]] == g.get('rotation', [0, 0, 0])
    visited = {b['name']}
    while 'parent' in b:
        assert b['parent'] not in visited
        visited.add(b['parent'])
        b = bones[b['parent']]

key_count = 0
for a in source['animations']:
    exported = clips[a['name']]
    assert exported['animation_length'] == a['length']
    assert exported['loop'] == {'loop': True, 'hold': 'hold_on_last_frame', 'once': False}[a['loop']]
    for anim in a['animators'].values():
        for k in anim.get('keyframes', []):
            value = exported['bones'][anim['name']][k['channel']][str(float(k['time']))]
            if isinstance(value, dict):
                if k['interpolation'] == 'catmullrom':
                    assert value['lerp_mode'] == 'catmullrom'
                value = value['post']
            v = list(value)
            if k['channel'] in ('position', 'rotation'): v[0] *= -1
            if k['channel'] == 'rotation': v[1] *= -1
            assert v == [float(k['data_points'][0][axis]) for axis in 'xyz']
            key_count += 1
assert key_count == 1777
for name, clip in clips.items():
    assert set(clip['bones']) <= set(bones)
    if '.layer_' in name:
        assert not set(clip['bones']) & {'energy_orb', 'orb_particles', 'tail_laser', 'tail_glow', 'sigil_bolts'}
for source_name, dest_name in [('lionxx.png', 'leao_do_fim.png'), ('lionxx_glowmask.png', 'leao_do_fim_glowmask.png')]:
    original = (ROOT / 'model_sources' / source_name).read_bytes()
    exported = (ASSETS / 'textures/entity' / dest_name).read_bytes()
    assert hashlib.sha256(original).digest() == hashlib.sha256(exported).digest()
    assert exported[:8] == b'\x89PNG\r\n\x1a\n'
    assert struct.unpack('>II', exported[16:24]) == (128, 128)
for lang in ('en_us',):  # en_us e o unico idioma (Etapa 4.6: texto visivel em ingles)
    data = json.loads((ASSETS / 'lang' / (lang + '.json')).read_text())
    assert data['entity.tupinicraft.leao_do_fim'] and data['item.tupinicraft.leao_do_fim_spawn_egg']
print(f'PASS: 45 bones, 127 cubes, 14 original animations, {key_count} keyframes, 5 filtered layers, textures and translations')
