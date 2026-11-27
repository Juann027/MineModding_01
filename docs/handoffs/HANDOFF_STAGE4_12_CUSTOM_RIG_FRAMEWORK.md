# Handoff — Etapa 4.12: Custom Rig Framework & Blockbench Development Kit

Base: `main` em `fbccd25` (consolidação 4.10.1–4.11). Versão pública inalterada (`0.5.7`). Sem dependências novas.

## O que existia (auditoria) e o que mudou

| Já existia (mantido) | Expandido nesta etapa |
|---|---|
| `FinisherParticipants.resolve`: jogador → clip do rig do jogador; mob → `EntityFinisherClip` por **tipo** ou parado | resolução com **família de rig**: explícito (tipo) → override do binding → família → parado; `Source` para diagnóstico; requisitos de participante |
| `EntityFinisherClip` (controlador + animação gatilho + `Alignment`) | reaproveitado: clips de família e overrides viram `EntityFinisherClip` sintetizados (o resto do Finisher Engine não mudou) |
| `FinisherParticipants.registerStaging` (palco por tipo) | palco por binding/família para entidade de rig parada |
| `FinisherTargeting` (chefes só por exclusiva) | intacto; `FinisherEligibility` junta targeting + rigs numa consulta única (sorteio, `rig check`, futuro seletor) |
| `GeoFxKeyframes` (timeline `calamidade:` lido do jar, agendado no servidor) | `instructions()`/`load()` públicos, reaproveitados pelas instruções `rig:` |
| `FinisherTestMechanic` (sessão, abort/complete/release, `onServerStopping`) | snapshot/restauração de partes em `release`, eventos `rig:` no tick da sessão, partícula no socket `impact_point`, gatilho GeckoLib para observador tardio, `check()` público, motivo real na recusa |
| `tools/player_anim` (exportador protegido por hash, `bbsync.flip`) | mesmo modelo de proteção e a mesma regra de inversão no `tools/rigkit` |

Nada foi substituído: não há segunda engine de animação; `PlayerAnimationService`, `FinisherFx`, `FinisherExecution`,
`FinisherSessionEvent`, `PlayerRigController` não mudaram.

## Arquitetura nova

- **`rig.RigDefinition`** — família (manifesto `assets/<ns>/rigs/<id>.rig.json`): bones (nome, pai, pivô, obrigatório,
  `strict`), prefixos livres, sockets, partes, capacidades, controlador GeckoLib, prefixo/arquivo de animações
  compartilhadas (com duração), bones que animações compartilhadas podem animar, alinhamento padrão. Valida nomes
  únicos, raiz única, pais, ciclos, partes que esconderiam bone obrigatório. Qualquer anatomia.
- **`rig.RigBinding`** — entidade → família (`rigs/entities/<entidade>.json`): geo, textura, animações próprias,
  `proportion`, `render_scale`, `extra_bones`, capacidades, partes, sockets (override), alinhamento, `clip_overrides`.
- **`rig.CustomRigRegistry`** — catálogo (`GLOBAL`, preenchido por `rig.Rigs.bootstrap()` no construtor do mod, os dois
  lados); duplicata, família inexistente, versão de contrato diferente e extras sem prefixo são recusados.
- **`finisher.FinisherRigs`** — novo componente da `FinisherDef` (`rigs()`, padrão `NONE`): `Clip(rig, papel, chave)` e
  `Requirement(papel, tipos, tags, família, capacidades, requiresClip)`. `withRigClip`/`withRigRequirement`/`withId`.
- **`finisher.FinisherEligibility`** — interação + targeting + resolução dos dois lados; usado no sorteio
  (`Finishers.pickFor(..., supported)`), em `FinisherTestMechanic.check` (que soma ficha de chefe e PvP) e no
  `/respectcraft rig check`.
- **`rig.RigVisuals`** — único dono das partes: attachment `respectcraft:rig_visual` **sem serializador**, pacote
  `network.RigNetworking.RigPartsSync` (só partes ≠ padrão, quando muda e no `StartTracking`), cache de cliente
  `network.RigVisualClientCache` (limpo no logout e quando a entidade sai do nível). Instruções de timeline
  `rig:hide:<parte>`, `rig:show:<parte>`, `rig:reset`.
- **`rig.RigSockets`** — socket = pivô do bone (× proporção) + deslocamento, girado pelo corpo; **pose de repouso**.
- **`client.rig.RigGeoModel` / `RigGeoRenderer`** — modelo/renderer GeckoLib genéricos a partir do binding; animações
  da família como `getAnimationResourceFallbacks`; partes aplicadas a cada desenho; rastreio de matriz dos `socket_*`.
- **Fixtures**: `entity.dev.RigMannequinEntity` (`respectcraft:dev_rig_mannequin_a` alto/fino com antena e override
  do Takedown; `..._b` baixo/largo com casca, núcleo, folha destacável, socket e alinhamento próprios). MISC, sem
  spawn natural, ovo, drop ou XP. Cena `respectcraft:dev/rig_peel_demo` (`Rigs.PEEL_DEMO`) **fora do catálogo**.
- **Catálogo**: o Takedown ganhou o clip da família `fruit_humanoid_v1` para a vítima
  (`finisher.takedown_victim`). Nenhuma entidade de gameplay usa a família; jogadores e mobs vanilla resolvem igual a
  antes (coberto no SelfTest e no teste de fumaça).

### Sockets: espaços e limites (GeckoLib 4.9.3)

- Local: px do Blockbench, pés em y = 0, frente −Z, +X = direita. O `.geo.json` tem X invertido; o GeckoLib desinverte
  ao assar (espaço assado = Blockbench). O `GeoEntityRenderer` gira por `180 − yawCorpo` e escala por `render_scale`.
- Servidor: o GeckoLib não avalia animações no servidor → só **pose de repouso** (`RigSockets.restWorld`); usado para
  partículas/alinhamento, **nunca** dano/colisão.
- Cliente: `GeoBone.getWorldPosition()` dos `socket_*` (com `setTrackingMatrices`) dá a pose animada do quadro; só
  visual.
- Locators do Blockbench são lidos (`Bone.locators`) mas não chegam ao modelo assado → marcadores são bones vazios.

### Proporções diferentes

Pivôs esperados = contrato × `proportion` (exceto `strict`); animações compartilhadas usam rotações (translação só no
`pelvis`); palco por binding (`alignment.distance`); sockets por binding. Sem retargeting: modelo que não fica bom
com o clip compartilhado ganha `clip_overrides`.

## Kit Blockbench

`rigkit/` (no repositório) e o ZIP `build/rigkit/respectcraft-rigkit-fruit_humanoid_v1-c1.zip`
(`python3 tools/rigkit/package_kit.py`; também artefato `respectcraft-rigkit` do CI): `README.md` (produção no
Blockbench Web + integração + comandos), `fruit_humanoid_v1/CONTRACT.md` (bones, árvore, pivôs, eixos, sockets,
partes, UV, exportação, o que é livre × o que quebra), `fruit_humanoid_v1.rig.json`, template
`fruit_humanoid_v1_template.bbmodel` (formato GeckoLib, 25 bones + 9 marcadores de socket, manequim colorido por lado,
textura embutida, as 3 animações compartilhadas), `reference/` (geo/png/animações exportados do template),
`examples/banana.binding.example.json`, `BBFORGE_PROMPT.md`, `tools/validate_rig.py` + `rigkit.py`.

Ferramentas no repositório (`tools/rigkit/`): `make_assets.py` (gera template, referência, manequins e animações da
família a partir do contrato; recusa sobrescrever arquivo editado fora dele — `generated_manifest.json`, `--force` com
cópia em `.backup/`), `validate_rig.py`, `test_rigkit.py`, `package_kit.py`.

## Comandos (permissão 2)

`/respectcraft rig list | fixtures | inspect <e> | clips <e> | check <executor> <alvo> | part <e> <parte> hide|show |
part <e> reset | demo <alvo>` — saídas esperadas no `rigkit/README.md`.

## Testes (o que rodou de verdade)

| Teste | Resultado |
|---|---|
| `./gradlew build` | OK |
| `bash .github/scripts/server-smoke-test.sh` (servidor dedicado; `RespectCraft self-test: OK` com o novo `RigSelfTest`; asserts novos de `rig list/inspect/clips/part/check`) | OK |
| `python3 tools/rigkit/test_rigkit.py` (30 casos: bone obrigatório ausente, duplicado, hierarquia, pivô, raiz estrita, esquerda/direita, pivô ausente, bone sem prefixo, partes, socket, animação com bone inexistente, vetor inválido, duração de override, versão/família do binding, animações compartilhadas, JSON inválido, textura, CLI) | OK |
| `python3 tools/rigkit/validate_rig.py --repo` | 0 erros, 0 avisos |
| ZIP aberto fora do repositório (`python3 -I tools/validate_rig.py ...` no template e na referência) | OK |
| `tools/player_anim/bbsync.py`, `test_export.py`, `tools/fx/check_shaders.py` (glslangValidator) | OK (sem mudanças) |
| Dois clientes reais (Xvfb, `tools/visual-test/helpers.sh`; Ana executa, Beto observa) | ver evidências abaixo |

SelfTest (`RigSelfTest`): família carregada; registro duplicado; binding para família inexistente / outra versão /
extra sem prefixo / pai inexistente; famílias inválidas (bone duplicado, pai inexistente, duas raízes, parte com bone
obrigatório); anatomia não humanoide aceita; prioridade explícito → binding → família → parado; retrocompatibilidade
de todo o catálogo para zumbi e jogador; clip sem `GeoEntity`; compartilhamento A/B; alinhamento do binding (com clip e
parado); recusa de mob sem rig; duração diferente; gatilhos e arquivos de animação; capacidade exigida; requisito por
papel, tipo e tag; animação obrigatória ausente; chefe com rig continua exigindo exclusiva; mob × mob; sorteio filtrado;
sockets (proporção, override, local→mundo); instruções `rig:`; visibilidade padrão/override; partes/capacidades
efetivas; eventos do timeline compartilhado.

### Evidências com clientes reais

Imagens em `docs/evidencias/stage4_12/` (servidor dedicado de desenvolvimento, mundo plano, dois clientes):

- `01_idle_beto.png` — os dois manequins (geometrias distintas) parados com o `idle` compartilhado.
- `02_takedown_A_override_ana.png` — Takedown no A: override próprio (giro), câmera cinematográfica da Ana.
- `03_takedown_B_family_ana.png` — Takedown no B: clip da família; o rosto aponta para o executor.
- `04_peel_B_parts_*.png`, `05_peel_A_shared_beto.png` — a mesma animação compartilhada `demo_peel` nos dois;
  log `Parts: shell=hidden ... core=visible` durante a cena (algumas capturas caíram no quadro da morte, com o
  tombo vanilla do fim da cena).
- `07_late_viewer_beto.png` + log `late viewer Beto receives animation.fruit_humanoid_v1.finisher.demo_peel`.
- `08a_before_cancel_beto.png` → `08b_after_cancel_*.png` — cena cancelada (`/stopitstopnow`) com a casca escondida:
  log `CANCELLED (player request)`, depois `Parts: shell=visible ... core=hidden` e a placa verde de volta no cliente;
  o manequim continua vivo.
- `09a/09b/09c_*_beto.png` — partes trocadas por comando, o observador sai do alcance e volta (o estado é reenviado no
  `StartTracking`), depois `reset`.
- `10_refused_*.png` + log `Could not start: victim: minecraft:pig has no custom rig; requires
  respectcraft:fruit_humanoid_v1.` — recusa **antes** da cena (sem palco, animação, trava ou efeito);
  `rig check` mostra as três finalizações do catálogo `ELIGIBLE` contra o porco (retrocompatibilidade).
- `11_two_b_independent_parts.png` (build final) — dois manequins B do mesmo modelo com partes diferentes ao mesmo tempo
  (bones assados compartilhados; estado aplicado no `preRender` de cada entidade).

Resumo dos logs: `docs/evidencias/stage4_12/resumo.txt`. Llvmpipe (sem GPU); um cenário de cada, não repetido.
Todas as cenas concluídas terminaram com `SUCCESS (killed)` (execução real de sempre).

## Limitações e pendências

- **Blockbench Web não foi aberto** nesta sessão (sem navegador com o plugin GeckoLib). O template foi validado
  estruturalmente (formato 5.0 `geckolib_model`, árvore/pivôs iguais ao geo de referência, validador limpo). Teste
  manual pendente: abrir o `.bbmodel` no Blockbench Web com o plugin, tocar as animações, exportar e rodar o validador.
- Observador tardio vê o clip **desde o começo** (o GeckoLib não aceita deslocamento no gatilho); ele para junto com
  a cena.
- Pose animada de sockets só no cliente; servidor usa pose de repouso.
- Manifestos lidos do jar (sem pacote de dados/addons externos ainda).
- Remover os manequins no futuro apaga instâncias em mundos (são fixtures; avisar no PR).
