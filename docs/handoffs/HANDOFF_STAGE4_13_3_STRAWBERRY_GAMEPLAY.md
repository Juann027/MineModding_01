# Handoff — Etapa 4.13.3: Strawberry Gameplay & NPC Finishers

O morango (`respectcraft:strawberry`) virou o primeiro NPC completo da família de frutas: spawn natural em plains e
savanna, IA neutra com retaliação, XP, música própria em loop e cinco finalizações cinematográficas exclusivas,
sorteadas pelo servidor. A etapa também fixa a política de seleção de finalizações por categoria de vítima. Versão
continua `0.5.7`. Base: `main` com o PR #54 (Scene Kit) integrado no início da etapa.

## Política de seleção (permanente)

`finisher.FinisherSelection` é a regra pura e `Finishers.pickFor` delega para ela. Fica separada da compatibilidade
técnica: `FinisherTargeting` diz quem pode ser executado, `FinisherEligibility` cuida de rigs, clips e requisitos.

| Vítima | Candidatas |
|---|---|
| PLAYER | só comuns (`Category.GENERIC`), com desbloqueio (`requiresSkill`). Consentimento, PvP e times seguem no `FinisherTestMechanic`. O futuro seletor Random/Preset/Manual escolhe dentro desta lista. |
| BOSS | só a exclusiva do chefe, nunca fallback (sem mudança). |
| MOB/NPC | o servidor sorteia uma cena completa. Se o tipo tem pool próprio (`FinisherTargeting.npcExclusive(tipos)`), usa só ele, sem desbloqueio pessoal. Sem pool, usa as comuns compatíveis com mob. Se nada for elegível, a interação é recusada antes de mexer no mundo. |

- `FinisherTargeting` ganhou `npcTypes` e `Category`. Uma exclusiva de NPC recusa jogador, chefe e qualquer outro tipo
  (manequins incluídos). `FinisherSelection.pvpLoadoutAllowed` deixa essas exclusivas fora do futuro loadout de PvP.
- `FinisherDef.victim` pode ser `null` só numa exclusiva de NPC (cena só PvE). Assim não é preciso criar um clip de
  PvP falso. Os eventos do lado da vítima entidade ficam em `entityVictimEvents`: quedas (`EVENT_FALL`) e o corte da
  música (`EVENT_SILENCE`), disparados pelo relógio da sessão (`tickVictimEvents`). As quedas também entram nos
  marcadores do `FinisherFx`.
- O crédito de chefe em `FinisherExecution` agora depende de `Category.BOSS_EXCLUSIVE` (antes era `!regular()`).

## As cinco finalizações

A fonte artística fica intacta em `model_sources/rig/strawberry/finishers/` (V2: `.bbmodel`, export do plugin e
relatório de eventos). A integração é reproduzível:

```bash
python3 tools/scenekit/strawberry_finishers.py --install   # separa e grava os clips (protegido)
python3 tools/scenekit/strawberry_finishers.py --check     # CI: instalados == separação atual
```

- O script faz uma cópia do manifesto do template Player × Strawberry para cada cena (`duration_ticks` e animação
  da cena) e passa pelo `split_scene.run` e pelo `install` do kit. Template e manifesto originais não são alterados.
  Resultado: **0 ERROR** nas cinco. Os 4 WARN de cada uma são só "outra cena no mesmo export".
- Única correção aplicada: nas três cenas V1 (fruit_punch @4.45 s, ground_smasher @4.4 s, shut_up @2.2 s), a linha
  da polpa mostrava `damage_head`/`damage_torso` junto com `rig:hide:shell`, e as rachaduras ficavam flutuando. A
  correção é a mesma da V2: as placas somem junto com a casca. Nada mais muda nos clips (interpolação, easing e
  eventos preservados).
- Destinos: clips do jogador em `animations/player/respectcraft_authored_scenes.animation.json` e do morango em
  `animations/entity/strawberry.animation.json`. No binding do artista entraram só `animation_file` e
  `clip_overrides`. Geo, png e partes não mudaram.
- `finisher.FruitFinishers` registra as cinco, com eventos e câmeras copiados das linhas Java do relatório:

| Id | Ticks | FX | Corte da música (estouro da polpa) |
|---|---|---|---|
| `respectcraft:strawberry_crusher` | 120 | COMBO_PRESSURE/INTENSE | 84 (golpe 3) |
| `respectcraft:fruit_punch` | 140 | COMBO_PRESSURE/INTENSE | 89 (golpe 3) |
| `respectcraft:ground_smasher` | 120 | COMBO_PRESSURE/INTENSE | 88 (golpe 3) |
| `respectcraft:shut_up` | 80 | TAKEDOWN_FOCUS/MEDIUM | 44 (golpe 3) |
| `respectcraft:strawberry_blender` | 160 | COMBO_PRESSURE/INTENSE | 121: é a **queda** do estouro (o último golpe é o 87, força 2) |

- As cinco têm pesos iguais, não exigem desbloqueio e têm o requisito executor = jogador, vítima = morango (família
  `fruit_humanoid_v1`, clip obrigatório).
- Suco, sementes e lascas saem do `FinisherFx` (dono único) como partículas vanilla, com a cor de `FruitMob.juice()`:
  poeira colorida em golpes e quedas, lascas de sementes e, no golpe 3, lascas de casca/polpa.
- Eventos `note` continuam só como documentação. As partes destacáveis não ganharam física.
- `validate_rig`: a timeline agora aceita as partes declaradas pelo binding. Antes eram 74 WARN falsos; agora são 0.

## Morango de gameplay

- **`entity.fruit.FruitMob`** é a base de toda fruta futura. Tem:
  - IA neutra: `HurtByTargetGoal` + `MeleeAttackGoal`, passeio e olhar. Ataque e passeio ficam desligados durante
    uma cena.
  - Despawn só para os nascidos pelo mundo (`NATURAL`/`CHUNK_GENERATION`). Ovo e `/summon` ficam persistentes.
  - Regra de spawn: chão de grama (`ANIMALS_SPAWNABLE_ON`) com luz > 8.
  - Corte da música como dado sincronizado (nunca salvo).
  - Suco da fruta.
- **Morango:** 12 HP, 2 de dano, velocidade 0,23, percepção 16, XP 3–5 (`getBaseExperienceReward`), sem drops.
  `PanicGoal` foi removido.
- **Spawn:** `data/respectcraft/neoforge/biome_modifier/strawberry_spawns.json` (`neoforge:add_spawns`) com só
  `minecraft:plains` e `minecraft:savanna`, peso 5, grupo 1–2, CREATURE (teto de criaturas vanilla), placement
  `ON_GROUND`. Mobs vanilla não foram tocados. Os manequins continuam sem spawn.
- **Música:**
  - `StrawberrySound.mp3` foi convertido para OGG Vorbis mono 44,1 kHz
    (`assets/respectcraft/sounds/entity/strawberry_music.ogg`, `stream: true`, atenuação de 20 blocos). O original
    fica em `model_sources/sounds/strawberry_music.mp3`.
  - `client.fruit.FruitMusicClient` mantém um loop por fruta, preso à entidade (segue a posição a cada tick), nunca
    reiniciado por movimento ou IA.
  - Toca no máximo `maxMusicSources` (4, configurável) fontes ao mesmo tempo, sempre as mais próximas.
  - Categoria Jukebox/Note Blocks, mais `musicVolume` e `music` (ligar/desligar) em `respectcraft-fruits-client.toml`.
  - Limpeza: morte (morrendo = sem música), saída do nível (despawn, fim do rastreio, chunk), troca de nível ou
    dimensão e logout.
  - **Corte:** `EVENT_SILENCE` liga `setMusicSilenced(true)` e o cliente para o som no mesmo tick, sem fade. O
    silêncio vai até o fim da cena. Em cancelamento (`abort`) ou execução sem morte (`FAILED`/totem), a música volta
    (`restoreMusic`).
- **Comandos (permissão 2):**
  - `/respectcraft fruit spawns`: entradas de spawn por bioma de referência.
  - `/respectcraft fruit music <alvo> true|false`: corta ou devolve a música, para testar o áudio sem cena.
  - `/respectcraft finishers <alvo>` agora mostra também o `Selection pool (no unlocks)`.

## Validação

**Executado nesta sessão:**

- `./gradlew build`: OK.
- `bash .github/scripts/server-smoke-test.sh`: OK. Inclui `RespectCraft self-test: OK` e as verificações novas:
  - as cinco cenas resolvidas pelo `BINDING`;
  - pool do morango = as cinco;
  - nada delas no pool do zumbi;
  - spawn só em plains/savanna (sunflower_plains, savanna_plateau, windswept_savanna, forest, desert e meadow:
    `none`);
  - corte e volta da música.
- `RigSelfTest` novo:
  - as cinco elegíveis pelo binding, sem desbloqueio, peso 1, fora do loadout PvP;
  - recusadas contra jogador, manequins A/B, zumbi e chefe; o morango nunca é executor;
  - 400 sorteios contra o morango só do pool, com as cinco aparecendo;
  - nada do pool contra jogador, zumbi, manequim ou chefe;
  - mob sem pool usa as comuns; cenas de teste fora do catálogo;
  - timeline tick a tick: expressões sem sobreposição, rachaduras nunca sem a casca, polpa exposta no fim, corte da
    música no tick do estouro (±1).
- `bbsync.py`, `test_rigkit.py` (0 falhas), `test_scenekit.py` (0 falhas), `validate_rig.py --repo` (0 error,
  0 warning), `test_export.py` (0 falhas), `strawberry_finishers.py --check` (instalados == separação).
- **Dois clientes reais** (servidor dedicado, Xvfb, Ana executora com áudio gravado de um sink nulo do PulseAudio,
  Beto observador):
  - As cinco cenas (forçadas por `/respectcraft admin finisher`) terminaram `SUCCESS (killed)`, com câmera, contato,
    destruição visual, suco e anel de impacto. Beto viu as cenas sincronizadas.
  - Interação normal (Shift + clique direito, morango com 2 HP): 3/3 cenas começaram. O servidor sorteou `shut_up`,
    `shut_up` e `ground_smasher`; todas `SUCCESS`.
  - XP ganho por execução: 3–5. Por morte comum (`/damage … by Ana`): 4.
  - Cancelamento (`/stopitstopnow` no meio do Crusher): `CANCELLED (player request)`. Todas as partes voltaram ao
    padrão (`rig inspect`) e a música voltou a tocar (log do cliente: `start` depois do `silenced`).
  - Neutro: com Ana parada perto do morango por 12 s, a vida ficou em 20. Retaliação: um soco tirou o morango de 12
    para 11 HP; ele perseguiu e bateu (Ana 20 → 19 → 13,2 em ~5 s, ~2 por golpe na dificuldade normal).
  - Spawn natural: em chunks novos de plains (mundo plano, `doMobSpawning true`), 2 morangos nasceram perto da Ana
    em ~35 s.
  - Música: log do cliente `start` → `stop (silenced)` → `start` → `stop (died/removed)`, uma instância por
    entidade. Nas cinco cenas, o `stop (silenced)` veio 0,08–0,14 s depois do tick do estouro (com a latência do
    console). No áudio gravado, a correlação com a música cai de 0,94 para 0,13 na primeira janela de 0,4 s depois
    do corte, ou seja, o stream para ali.
- Evidência: `docs/evidencias/stage4_13_3/two_clients_strawberry_finishers.jpg`. Linha a linha: Crusher (polpa);
  Fruit Punch; Ground Smasher; Shut Up; Blender (estouro); Fruit Punch visto pelo Beto; antes/depois do
  cancelamento; interação normal vista pelo Beto; retaliação.

**Não verificado / pendente:**

- Depois do corte aparece no áudio gravado um resíduo tonal, sem correlação com a música (≈ −6 a −15 dB), que decai
  em ~3 s. Pode vir do pipeline de captura ou de outro som. Não foi identificado; vale ouvir num cliente com áudio
  real.
- Vários morangos tocando ao mesmo tempo e o limite de fontes: a lógica está no código, mas só uma fruta por vez foi
  exercitada em cliente real.
- O controle de volume pela config não foi alterado em teste.
- Terreno natural com desníveis e obstáculos: os testes foram no mundo plano. No palco, a vítima fica parada se não
  houver espaço (regra antiga). Não houve teste em savanna real nem em terreno acidentado.
- Ausência de spawn fora dos biomas foi conferida pelas listas de spawn do servidor (biome modifier aplicado), não
  por espera em mundo real.
- O CI do GitHub (build, smoke test, job do Blockbench) roda no PR.

## Limitações e próximos passos (roadmap)

- Sem animações de andar, atacar e personalidade (o dono produz). O morango ataca com a animação parada da família.
- Coroa, caule e folhas não se soltam como props (sem física). Suco e sementes são partículas vanilla. O Universal
  Fluid & Destruction FX Framework fica no Calamidade.
- Banana, melancia e abacaxi: estendem `FruitMob` (binding, atributos, som e pool próprios).
- Reprodução entre frutas (inclusive entre espécies diferentes) ficou registrada no roadmap como decisão futura. Nada
  foi implementado.
- Seletor holográfico de finalizações PvP (Random/Preset/Manual): não implementado. A política de seleção já separa o
  que ele vai usar.
