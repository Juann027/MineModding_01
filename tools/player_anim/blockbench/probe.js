// Abre o Blockbench web (compilado do codigo oficial; servido em 127.0.0.1:8765 por check.sh) no Chromium headless,
// carrega o plugin GeckoLib, abre um .bbmodel, imprime o que o editor entendeu (formato, bones, clips, keyframes) e
// grava <nome>.export.json com o "Export Animations" do proprio Blockbench.
// Uso: node probe.js <arquivo.bbmodel> [saida_dir]
// Capturas opcionais: SHOTS='[["<clip>", <segundos>, "<arquivo>", [camX,camY,camZ,alvoX,alvoY,alvoZ]]]'
// Precisa do pacote playwright (ja instalado nas sessoes de nuvem; fora delas: npm i playwright).
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

(async () => {
  const file = process.argv[2];
  const out = process.argv[3] || '.';
  const browser = await chromium.launch({ args: ['--use-gl=swiftshader', '--enable-unsafe-swiftshader'] });
  const page = await browser.newPage({ viewport: { width: 1400, height: 860 } });
  const logs = [];
  page.on('console', m => logs.push(`[${m.type()}] ${m.text()}`));
  page.on('pageerror', e => logs.push(`[pageerror] ${e.message}`));
  page.on('dialog', d => { logs.push(`[dialog] ${d.message()}`); (d.message().includes('load this plugin') ? d.accept() : d.dismiss()); });
  await page.goto('http://127.0.0.1:8765/index.html');
  await page.waitForFunction(() => window.Blockbench && window.Codecs && Codecs.project && window.Plugin, null, { timeout: 60000 });
  await page.waitForTimeout(1500);
  const version = await page.evaluate(() => Blockbench.version);
  await page.evaluate(async () => { const code = await (await fetch('/geckolib_plugin.js')).text(); await new Plugin().loadFromFile({ path: 'geckolib.js', name: 'geckolib.js', content: code }, true); });
  await page.waitForTimeout(1500);
  const text = fs.readFileSync(file, 'utf8');
  const info = await page.evaluate(([text, name]) => {
    loadModelFile({ content: text, path: name, name });
    const groups = Group.all.map(g => g.name);
    const cubes = Cube.all.length;
    const anims = AnimationItem.all.map(a => ({ name: a.name, length: a.length, loop: a.loop,
      animators: Object.values(a.animators).filter(x => x.keyframes.length).map(x => x.name),
      keyframes: Object.values(a.animators).reduce((n, x) => n + x.keyframes.length, 0) }));
    return { plugin: !!(Plugins.registered.geckolib && Plugins.registered.geckolib.installed), format: Format.id, formatName: Format.name, project: Project.name, textures: Texture.all.length,
             groups: groups.length, groupNames: groups, cubes, anims,
             boxUv: Project.box_uv, resolution: [Project.texture_width, Project.texture_height] };
  }, [text, path.basename(file)]);
  console.log(JSON.stringify({ version, ...info }, null, 1));
  // Exporta as animacoes com o codec do formato (o mesmo do "Export Animations" do plugin)
  const exported = await page.evaluate(() => {
    const codec = AnimationCodec.getCodec();
    return codec ? JSON.stringify(codec.compileFile(AnimationItem.all)) : null;
  });
  if (exported) fs.writeFileSync(path.join(out, path.basename(file, '.bbmodel') + '.export.json'), exported);
  // Capturas no modo Animate, nos instantes pedidos
  const shots = JSON.parse(process.env.SHOTS || '[]');
  if (shots.length) {
    await page.evaluate(() => { Modes.options.animate.select(); });
    await page.waitForTimeout(800);
    for (const [anim, t, label, preset] of shots) {
      const pose = await page.evaluate(([anim, t, preset]) => {
        const a = AnimationItem.all.find(x => x.name === anim);
        a.select();
        Timeline.setTime(t);
        Animator.preview();
        if (preset) { const v = Preview.selected; v.camera.position.set(preset[0], preset[1], preset[2]); v.controls.target.set(preset[3], preset[4], preset[5]); v.controls.update(); }
        Timeline.setTime(t); Animator.preview();
        const pick = n => { const g = Group.all.find(x => x.name === n); return g ? g.mesh.getWorldPosition(new THREE.Vector3()).toArray().map(v => +v.toFixed(2)) : null; };
        return { time: Timeline.time, Root: pick('Root'), Head: pick('Head'), RightArm: pick('RightArm'), Alvo_Head: pick('Alvo_Head') };
      }, [anim, t, preset]);
      await page.waitForTimeout(400);
      await page.screenshot({ path: path.join(out, label + '.png') });
      console.log('pose', label, JSON.stringify(pose));
    }
  }
  fs.writeFileSync(path.join(out, path.basename(file, '.bbmodel') + '.console.log'), logs.join('\n'));
  await browser.close();
})().catch(e => { console.error(e); process.exit(1); });
