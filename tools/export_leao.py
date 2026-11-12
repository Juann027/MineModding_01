#!/usr/bin/env python3
"""Export the supplied Blockbench 5 project to GeckoLib 4 resources (stdlib only).

Original clips are retained. Runtime overlays remove unrelated visibility keys,
so an eye loop cannot hide the orb or bolts animated by the body controller.
Unsupported features fail loudly instead of being silently discarded.
"""
import copy
import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'model_sources'
ASSETS = ROOT / 'src/main/resources/assets/tupinicraft'


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def position(v):
    return [-v[0], v[1], v[2]]


def rotation(v):
    return [-v[0], -v[1], v[2]]


def vector(key):
    assert len(key['data_points']) == 1, 'Multiple datapoints need explicit export support'
    v = [float(key['data_points'][0][axis]) for axis in 'xyz']
    return position(v) if key['channel'] == 'position' else rotation(v) if key['channel'] == 'rotation' else v


def export():
    model = json.loads((SOURCE / 'leao_do_fim.bbmodel').read_text(encoding='utf-8'))
    assert model['meta']['format_version'] == '5.0', 'This exporter targets Blockbench project format 5.0 (animation axis conventions changed)'
    groups = {g['uuid']: g for g in model['groups']}
    elements = {e['uuid']: e for e in model['elements']}
    bones, audit = [], []

    def visit(nodes, parent=None):
        for node in nodes:
            assert isinstance(node, dict), 'Loose root cubes are not supported'
            g = groups[node['uuid']]
            bone = {'name': g['name'], 'pivot': position(g['origin'])}
            if parent:
                bone['parent'] = parent
            if any(g.get('rotation', [])):
                bone['rotation'] = rotation(g['rotation'])
            cubes = []
            for child in node.get('children', []):
                if isinstance(child, dict):
                    continue
                e = elements[child]
                assert e.get('type', 'cube') == 'cube'
                assert e.get('export', True)
                assert not e.get('box_uv', False)
                cube = {'origin': [-e['to'][0], e['from'][1], e['from'][2]],
                        'size': [b-a for a, b in zip(e['from'], e['to'])], 'uv': {}}
                if any(e.get('rotation', [])):
                    cube.update(pivot=position(e['origin']), rotation=rotation(e['rotation']))
                if e.get('inflate'):
                    cube['inflate'] = e['inflate']
                for face, f in e['faces'].items():
                    if f.get('texture') is None:
                        continue
                    assert f['texture'] == 0, 'Multiple materials need a custom renderer'
                    assert not f.get('rotation', 0)
                    u, v, u2, v2 = f['uv']
                    uv, size = [u, v], [u2-u, v2-v]
                    if face in ('up', 'down'):
                        uv, size = [u2, v2], [u-u2, v-v2]
                    cube['uv'][face] = {'uv': uv, 'uv_size': size}
                cubes.append(cube)
            if cubes:
                bone['cubes'] = cubes
            bones.append(bone)
            audit.append({'name': g['name'], 'parent': parent, 'pivot_blockbench': g['origin'],
                          'rotation_blockbench': g.get('rotation', [0, 0, 0]), 'cubes': len(cubes)})
            visit([n for n in node.get('children', []) if isinstance(n, dict)], g['name'])

    visit(model['outliner'])
    bounds = model['visible_box']
    geo = {'format_version': '1.12.0', 'minecraft:geometry': [{
        'description': {'identifier': 'geometry.leao_do_fim',
                        'texture_width': model['resolution']['width'],
                        'texture_height': model['resolution']['height'],
                        'visible_bounds_width': bounds[0], 'visible_bounds_height': bounds[1],
                        'visible_bounds_offset': [0, bounds[2], 0]}, 'bones': bones}]}
    clips = {}
    for a in model['animations']:
        clip = {'loop': {'loop': True, 'once': False, 'hold': 'hold_on_last_frame'}[a['loop']],
                'animation_length': a['length'], 'bones': {}}
        for animator in a['animators'].values():
            assert animator.get('type', 'bone') == 'bone'
            assert not animator.get('rotation_global', False)
            channels = {}
            for channel in ('rotation', 'position', 'scale'):
                keys = sorted([k for k in animator.get('keyframes', []) if k['channel'] == channel], key=lambda k: k['time'])
                if not keys:
                    continue
                values = {}
                for i, key in enumerate(keys):
                    assert key['interpolation'] in ('linear', 'catmullrom', 'step')
                    v = vector(key)
                    # A step key holds until the next timecode. Bedrock pre/post
                    # expresses the discontinuity without an unsupported lerp_mode.
                    previous_step = i > 0 and keys[i-1]['interpolation'] == 'step'
                    if key['interpolation'] == 'catmullrom':
                        value = {'post': v, 'lerp_mode': 'catmullrom'}
                        if (i == 0 and key['time'] > 0) or (i > 0 and keys[i-1]['interpolation'] != 'catmullrom'):
                            value['pre'] = v
                    elif previous_step:
                        value = {'pre': vector(keys[i-1]), 'post': v}
                    else:
                        value = v
                    values[str(float(key['time']))] = value
                channels[channel] = values
            if channels:
                clip['bones'][animator['name']] = channels
        clips[a['name']] = clip

    masks = {'sigil': {'sigil_orbit', 'sigil_spin', 'sigil_ring'},
             'aura_idle': {'face_aura'}, 'aura_charge': {'face_aura'}}
    eyes = {g['name'] for g in groups.values() if g['name'].startswith(('eye_wisp_', 'eye_glow_', 'eye_trail_'))}
    masks.update(eye_aura_idle=eyes, eye_aura_charge=eyes)
    for name, mask in masks.items():
        clip = copy.deepcopy(clips['animation.lionxx.' + name])
        clip['bones'] = {k: v for k, v in clip['bones'].items() if k in mask}
        clips['animation.leao_do_fim.layer_' + name] = clip

    write(ASSETS / 'geo/entity/leao_do_fim.geo.json', geo)
    write(ASSETS / 'animations/entity/leao_do_fim.animation.json', {'format_version': '1.8.0', 'animations': clips})
    for source, dest in [('lionxx.png', 'leao_do_fim.png'), ('lionxx_glowmask.png', 'leao_do_fim_glowmask.png')]:
        target = ASSETS / 'textures/entity' / dest
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(SOURCE / source, target)
    write(ROOT / 'docs/leao_do_fim_model_audit.json', {
        'bones': audit, 'locators': [], 'cube_count': sum(len(b.get('cubes', [])) for b in bones),
        'animations': [{'name': a['name'], 'duration_seconds': a['length'], 'loop': a['loop']} for a in model['animations']],
        'runtime_overlays': list(masks)})
    assert len(bones) == len(groups) and sum(len(b.get('cubes', [])) for b in bones) == len(elements)
    assert all(set(c['bones']) <= {b['name'] for b in bones} for c in clips.values())
    print(f'Export OK: {len(bones)} bones, {len(elements)} cubes, {len(model["animations"])} original clips, {len(masks)} overlays')


if __name__ == '__main__':
    export()
