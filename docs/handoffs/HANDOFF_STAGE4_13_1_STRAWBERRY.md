# Handoff — Etapa 4.13.1: Fruit Mob Integration — Strawberry

Base: `main` em `4d4f93d` (merge do PR #52, Etapa 4.12, CI verde). Versão pública inalterada (`0.5.7`). Sem
dependências novas. Prova de conceito da integração de **assets externos** (Blockbench Web) no Custom Rig Framework.

## Assets (arquivos do artista, sem regeneração)

| Entregue (nome do navegador) | No repositório |
|---|---|
| `strawberry.geo (2).json` | `src/main/resources/assets/respectcraft/geo/entity/strawberry.geo.json` (byte a byte) |
| `strawberry (3).png` | `src/main/resources/assets/respectcraft/textures/entity/strawberry.png` (a textura embutida no `.bbmodel`; pixels idênticos ao PNG anexado, RGBA 256×256) |
| `strawberry.binding (2).json` | `src/main/resources/assets/respectcraft/rigs/entities/strawberry.json` (byte a byte, sem campos novos) |
| `strawberry_final.bbmodel` | `model_sources/rig/strawberry/strawberry.bbmodel` (fonte) |
| `strawberry_visual_states.md` | `model_sources/rig/strawberry/strawberry_visual_states.md` |

Modelo: 66 bones (16 obrigatórios, 9 sockets, 32 extras), 225 cubos, família `respectcraft:fruit_humanoid_v1`,
proporção `[1,1,1]`, animações compartilhadas da família, nenhuma própria. **Não** passe estes arquivos pelo
`tools/rigkit/make_assets.py` (ele só gera família/fixtures).

## Código

- `entity.rig.CustomRigMob` — base comum dos mobs de rig, **extraída** do `RigMannequinEntity` da 4.12 (um controlador
  `main` com o `idle` da família + gatilhos do registro). Os manequins agora a estendem; nada de infraestrutura nova.
- `entity.fruit.StrawberryEntity` — `respectcraft:strawberry`, CREATURE, caixa 0,8 × 2,1 (geometria 14 × 34 px).
  Comportamento provisório: `FloatGoal`, `PanicGoal` (foge quando ferido por alguém), passeio, olhar para jogadores.
  Atributos provisórios: 12 de vida, velocidade 0,23. Sem ataque, sem drop, sem XP, não some longe do jogador.
- `ModEntities.STRAWBERRY`, `ModItems.STRAWBERRY_SPAWN_EGG` (abas Spawn Eggs e RespectCraft), renderer genérico
  `RigGeoRenderer` em `RespectCraftClient`, `Rigs.bind(STRAWBERRY_ID)`, lang `entity.respectcraft.strawberry` /
  `item.respectcraft.strawberry_spawn_egg`. **Sem spawn natural** (nenhum bioma o lista).
- Finalizações: nenhuma mudança no catálogo. O Takedown já tinha o clip da família para a vítima (4.12) e a cena de
  teste `respectcraft:dev/rig_peel_demo` aceita qualquer vítima da família com mãos. O morango não executa
  finalizações (não há clip de executor; como em todo mob, sessões começam pelo jogador).
- Alinhamento: o binding não declara `alignment` (decisão do artista: distância é da integração). O automático da
  família ficou correto em jogo (sem interpenetração nas capturas), então **nenhum ajuste** e nenhum bone obrigatório
  mexido.

## Estados visuais

Partes efetivas = família (`shell` = `head_shell` + `torso_shell`, `core` = `torso_core`) + binding (`face_normal`,
`eyes_wide`, `eyes_closed`, `brows_panic`, `mouth_open`, `mouth_scream`, `damage_head`, `damage_torso`, `leaves`,
`stem`, `top`, `earrings`). Padrão conferido no `RigSelfTest` e em jogo (`rig inspect`): casca, rosto normal, folhas,
caule, top e brincos visíveis; polpa, expressões alternativas e rachaduras ocultas.

Regras (convenção das cenas, **não** campos do schema), testadas no `RigSelfTest`:

- Expressão = esconder `face_normal` + mostrar **um** olho (`eyes_wide` × `eyes_closed`), `brows_panic` e **uma** boca
  (`mouth_open` × `mouth_scream`). Os três estados do documento (choque, grito, espremida) passam; variante sem esconder
  o rosto normal, dois olhos ou duas bocas são detectados como sobreposição.
- `rig:reset` volta **todas** as partes ao padrão (casca e folhas inclusive). Numa cena de destruição, voltar ao rosto
  normal é explícito: `rig:hide:eyes_wide;rig:hide:brows_panic;rig:hide:mouth_scream;rig:show:face_normal;` (o teste
  confirma que isso preserva casca oculta/polpa/rachadura, e que `reset` não preserva).
- `damage_head` fica **sobre a casca**: com a casca da cabeça escondida as rachaduras da cabeça flutuam (visto em
  `05_peeled_zoom.png`, onde foi ligado de propósito). Descascado + rachado usa só `damage_torso` (como o documento).
- **Casca da cabeça × torso independentes: avaliado e não feito.** Uma parte do binding com `head_shell` ou
  `torso_shell` duplicaria bones da parte `shell` da família; `RigGeoModel` aplica as partes em ordem e a última
  vence, então o resultado dependeria da ordem (frágil). Fazer direito = separar `shell` no contrato da família (nova
  versão do contrato) — fora do escopo, sem necessidade nesta etapa.

## Comandos de teste (permissão 2)

```
/summon respectcraft:strawberry ~ ~ ~3
/respectcraft rig inspect @e[type=respectcraft:strawberry,limit=1,sort=nearest]
/respectcraft rig clips @e[type=respectcraft:strawberry,limit=1,sort=nearest]
/respectcraft rig check @s @e[type=respectcraft:strawberry,limit=1,sort=nearest]
/respectcraft admin finisher @s @e[type=respectcraft:strawberry,limit=1,sort=nearest] respectcraft:takedown
/respectcraft rig demo @e[type=respectcraft:strawberry,limit=1,sort=nearest]
/respectcraft rig part @e[type=respectcraft:strawberry,limit=1,sort=nearest] face_normal hide   (eyes_wide/brows_panic/mouth_scream show …)
/respectcraft rig part @e[type=respectcraft:strawberry,limit=1,sort=nearest] reset
/stopitstopnow   (durante a cena: cancela e restaura as partes)
```

## Testes (o que rodou de verdade)

| Teste | Resultado |
|---|---|
| `python3 tools/rigkit/validate_rig.py --repo` | morango: 0 erros, 0 avisos (bones, pivôs, extras declarados, 9 sockets, partes, textura 256×256) |
| `validate_rig.py --rig ... --geo ... --texture ... --bbmodel ... --binding ...` (bbmodel × geo × png × binding) | 0 erros/avisos nas checagens de geo/bbmodel/textura (os 2 ERROR de caminho do binding no modo avulso somem no `--repo`, que resolve `respectcraft:` pelo jar) |
| `./gradlew build` | OK |
| `bash .github/scripts/server-smoke-test.sh` | OK: `RespectCraft self-test: OK` (com os casos do morango), `3 bound entity type(s)`, `rig inspect/clips/part` do morango |
| `tools/rigkit/test_rigkit.py` (atualizado: 4 relatórios no repo), `tools/player_anim/bbsync.py`, `test_export.py` | OK |
| Dois clientes reais (Xvfb, llvmpipe; servidor dedicado; Ana executa, Beto observa) | ver abaixo |

### Evidências (`docs/evidencias/stage4_13_1/`, logs em `resumo.txt`)

- `01_normal_*`, `01_normal_zoom.png` — estado normal (idle compartilhado, casca, rosto arrogante, folhas, acessórios).
- `02_takedown_b_ana.png` (câmera cinematográfica) e `02_takedown_a_beto.png` — Takedown com a animação compartilhada da
  vítima; log `respectcraft:takedown ... on respectcraft:strawberry (Strawberry): SUCCESS (killed)` (execução real).
- `03_panic_*` — expressão de pânico (olhos arregalados, sobrancelhas, grito), sem sobreposição.
- `04_cracks_zoom.png` — rachaduras (`damage_head` + `damage_torso`).
- `05_peeled_*` — casca oculta, polpa exposta, folhas e caule ocultos; `05b_reset_zoom.png` — `reset` devolve tudo.
- `06_demo_peel_*` — cena `dev/rig_peel_demo` nos dois clientes: `rig:hide:shell;rig:show:core` do timeline compartilhado
  + expressão/rachadura/folhas/caule trocados durante a cena (log `Parts: shell=hidden ... leaves=hidden stem=hidden`).
- `07_after_cancel_*` — `/stopitstopnow`: `CANCELLED (player request)` e as partes voltam ao estado de antes da cena nos
  dois clientes (log `Parts: shell=visible ... face_normal=visible ... leaves=visible`); morango vivo.
- `00_states_sheet.png` — normal / pânico / rachado / descascado / restaurado lado a lado.
- Também rodou: `demo_peel` completo até `SUCCESS (killed)`; morango ferido por jogador foge (`Pos` de `[0.5,-60,4.5]`
  para `[6.37,-60,0.64]` em 2,5 s).

Sincronização multiplayer: as trocas de parte por comando e pela cena apareceram nos **dois** clientes (Ana na câmera da
cena, Beto como observador) e a restauração também. Não repetido nesta etapa (coberto na 4.12): observador que entra
no meio da cena e reenvio no `StartTracking`.

## Limitações e pendências

- **Sem animação de andar**: a família só tem `idle`, `finisher.takedown_victim` e `finisher.demo_peel`; o morango
  desliza com o `idle` enquanto anda. Um `walk` deve entrar na família (para todas as frutas), não no morango.
- Comportamento e atributos provisórios (sem papel definido no jogo).
- Casca única para cabeça e torso (ver acima).
- Capturas em llvmpipe (sem GPU), um cenário de cada. Não testado em cliente: "descascada + sem top".
- Entidade e ovo novos registrados: removê-los no futuro apaga instâncias em mundos.
