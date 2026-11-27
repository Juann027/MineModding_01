// Abre um projeto conjunto do Finisher Scene Authoring Kit no Blockbench web real (compilado do codigo oficial, servido
// em 127.0.0.1:8765 por check_scene.sh) com o plugin GeckoLib, toca as animacoes, mede palco e pontos de contato no
// proprio editor, grava o "Export Animations" do plugin e uma captura no golpe.
// Uso: node probe_scene.js <projeto.bbmodel> <cena.scene.json> <saida_dir>
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

(async () => {
  const [file, manifestPath, out] = process.argv.slice(2);
  const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
  const browser = await chromium.launch({ args: ['--use-gl=swiftshader', '--enable-unsafe-swiftshader'] });
  const page = await browser.newPage({ viewport: { width: 1400, height: 860 } });
  const logs = [];
  page.on('console', m => logs.push(`[${m.type()}] ${m.text()}`));
  page.on('pageerror', e => logs.push(`[pageerror] ${e.message}`));
  page.on('dialog', d => { logs.push(`[dialog] ${d.message()}`); (d.message().includes('load this plugin') ? d.accept() : d.dismiss()); });
  await page.goto('http://127.0.0.1:8765/index.html');
  await page.waitForFunction(() => window.Blockbench && window.Codecs && Codecs.project && window.Plugin, null, { timeout: 60000 });
  await page.waitForTimeout(1500);
  await page.evaluate(async () => { const code = await (await fetch('/geckolib_plugin.js')).text(); await new Plugin().loadFromFile({ path: 'geckolib.js', name: 'geckolib.js', content: code }, true); });
  await page.waitForTimeout(1500);
  const errorsBefore = logs.filter(l => l.startsWith('[pageerror]') || l.startsWith('[error]')).length;
  const text = fs.readFileSync(file, 'utf8');
  const info = await page.evaluate(([text, name, manifest]) => {
    loadModelFile({ content: text, path: name, name });
    const r = { version: Blockbench.version, plugin: !!(Plugins.registered.geckolib && Plugins.registered.geckolib.installed),
      format: Format.id, formatName: Format.name, perTextureUv: !!Format.per_texture_uv_size, boxUvOptional: !!Format.optional_box_uv,
      resolution: [Project.texture_width, Project.texture_height], groups: Group.all.length, cubes: Cube.all.length,
      textures: Texture.all.map(t => ({ name: t.name, w: t.width, h: t.height, uvw: t.uv_width, uvh: t.uv_height })),
      anims: AnimationItem.all.map(a => ({ name: a.name, length: a.length, animators: Object.keys(a.animators).length })) };
    // reproducao: toca cada animacao do comeco ao fim no modo Animate
    Modes.options.animate.select();
    const world = n => { const g = Group.all.find(x => x.name === n); if (!g) return null; g.mesh.updateWorldMatrix(true, false); return g.mesh.getWorldPosition(new THREE.Vector3()).toArray(); };
    r.played = [];
    for (const a of AnimationItem.all) {
      a.select();
      let frames = 0;
      for (let t = 0; t <= a.length + 1e-6; t += 0.05) { Timeline.setTime(t); Animator.preview(); frames++; }
      r.played.push({ name: a.name, frames });
    }
    // palco: raiz da vitima relativa a raiz do executor, na pose de descanso (t = 0)
    const main = AnimationItem.all.find(a => a.name === manifest.animation) || AnimationItem.all[0];
    main.select(); Timeline.setTime(0); Animator.preview();
    const pa = manifest.participants.attacker, pv = manifest.participants.victim;
    const ra = world(pa.prefix + pa.root_bones[0]), rv = world(pv.prefix + pv.root_bones[0]);
    r.stage = { attacker_root: ra, victim_root: rv, offset: ra && rv ? rv.map((v, i) => v - ra[i]) : null, expected: manifest.stage.victim_anchor_px };
    // contatos: nos instantes dos eventos (hit = linha "hit", grab = "note grab") de cada animacao
    r.contacts = [];
    for (const a of AnimationItem.all) {
      const fx = a.animators.effects;
      const times = { };
      if (fx) for (const kf of fx.keyframes) for (const dp of kf.data_points) {
        const s = dp.script || '';
        if (/@attacker hit /.test(s)) (times.hit = times.hit || []).push(kf.time);
        if (/note grab/.test(s)) (times.grab = times.grab || []).push(kf.time);
      }
      a.select();
      for (const c of manifest.contacts || []) for (const t of times[c.event] || []) {
        Timeline.setTime(t); Animator.preview();
        const p = world(pa.prefix + c.attacker_bone), q = world(pv.prefix + c.victim_bone);
        r.contacts.push({ animation: a.name, event: c.event, time: t, attacker: p, victim: q, max_px: c.max_px,
          distance: p && q ? Math.hypot(p[0] - q[0], p[1] - q[1], p[2] - q[2]) : null });
      }
    }
    // ida e volta: o que o Blockbench salvaria
    const saved = JSON.parse(Codecs.project.compile());
    r.saved = { groups: saved.groups.length, elements: saved.elements.length, textures: saved.textures.length,
      animations: saved.animations.length, sceneField: !!saved.respectcraft_scene };
    return r;
  }, [text, path.basename(file), manifest]);
  info.errors = logs.filter(l => l.startsWith('[pageerror]') || l.startsWith('[error]')).slice(errorsBefore);
  const exported = await page.evaluate(() => { const codec = AnimationCodec.getCodec(); return codec ? JSON.stringify(codec.compileFile(AnimationItem.all)) : null; });
  const stem = path.basename(file, '.bbmodel');
  if (exported) fs.writeFileSync(path.join(out, stem + '.blockbench_export.json'), exported);
  info.exported = !!exported;
  // captura no golpe da demonstracao
  const demo = info.anims.find(a => /demo/.test(a.name)) || info.anims[0];
  const hit = info.contacts.find(c => c.animation === demo.name && c.event === 'hit');
  await page.evaluate(([n, t]) => { const a = AnimationItem.all.find(x => x.name === n); a.select(); Timeline.setTime(t); Animator.preview();
    const v = Preview.selected; v.camera.position.set(-60, 40, -10); v.controls.target.set(0, 14, -10); v.controls.update(); Timeline.setTime(t); Animator.preview(); },
    [demo.name, hit ? hit.time : 0.7]);
  await page.waitForTimeout(500);
  await page.screenshot({ path: path.join(out, stem + '.hit.png') });
  fs.writeFileSync(path.join(out, stem + '.probe.json'), JSON.stringify(info, null, 1));
  fs.writeFileSync(path.join(out, stem + '.console.log'), logs.join('\n'));
  console.log(JSON.stringify(info, null, 1));
  await browser.close();
})().catch(e => { console.error(e); process.exit(1); });
