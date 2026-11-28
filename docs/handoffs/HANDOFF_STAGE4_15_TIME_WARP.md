# Handoff — Etapa 4.15: Time Warp Dimension

Branch `claude/keen-hamilton-mdwmuu`, empilhada sobre a 4.14 (PR #56, que por sua vez está sobre a 4.13.3, PR #55).
Versão pública **0.5.7** (inalterada). Calamidade_Awakening **0.15.0** (inalterado; nenhuma API nova usada).

## 1. Auditoria inicial (resumo)

- PRs #55 (4.13.3) e #56 (4.14) estavam **abertos, sem merge**; #56 contém #55. Base escolhida: a ponta da #56
  (Quest Engine, Notification HUD V2, Calamidade 0.15.0). O PR desta etapa deve entrar depois deles.
- Inverted Realm (`respectcraft:inverted_realm`) é só dados + `InvertedRealm`/`InvertedRealmCommands`; **nada dele foi
  tocado**. O Time Warp não reaproveita blocos, céu, ruído, viagem nem comandos do Inverted Realm.
- Reaproveitados: `HoloNotices`/`HoloNotice` (HUD V2), `QuestEngine`/`QuestService` (objetivos `advancement`), padrão
  de config SERVER/CLIENT, padrão de payloads com handler de cliente por lambda, `SelfTest`, teste de fumaça.

## 2. O que foi entregue (por subetapa)

### 4.15.1 — Dimension & Worldgen (dados)
- `dimension_type/time_warp.json` (`coordinate_scale` **3.33**, céu com ciclo de dia, `natural`, efeitos
  `respectcraft:time_warp`), `dimension/time_warp.json` (gerador noise, `multi_noise` com dois biomas pelo
  `continentalness`).
- `noise_settings/time_warp.json`: `sea_level` 64 (água até y 63), sem aquíferos/veios/cavernas, mobs desligados,
  regra de superfície "Beta" (grama/terra; areia na praia até y 66; areia/cascalho no fundo; bedrock).
- Relevo 2D (sem ilhas flutuantes nem saliências): `density_function/time_warp/{island_value,shape,offset,depth}`
  com os ruídos `worldgen/noise/time_warp_{islands,coast,hills}`. Ilha = `island_value` acima de 0,245 + ~0,25.
- Biomas `time_warp_ocean` (fundo com discos de areia/argila/cascalho) e `time_warp_island` (carvalhos clássicos
  raros, flores, capim, cana; carvão/ferro). Sem spawns (solidão). Música = `music.game` com intervalos longos; humor =
  `ambient.cave` (o som de caverna das versões antigas).

### 4.15.2 — Nostalgic Environment & Structures
- Estrutura de código `TimeWarpStructure` (tipo `respectcraft:time_warp_dwelling`), duas estruturas em dados:
  `time_warp_house` (spacing 9 / separation 4, só em terra, 55% das tentativas → ilhas vazias) e
  `time_warp_settlement` (spacing 40 / separation 16, exige ≥ 30/49 de terra numa amostra 7×7 de 72 blocos; exclusão
  de 4 chunks das casas). `findGenerationPoint` usa `getBaseHeight` (determinístico pela seed, sem gerar chunks).
- Peças `TimeWarpPiece` (`WOOD_HOUSE`, `COBBLE_HOUSE`, `DIRT_HUT`, `FARM`, `DOCK`, `UNFINISHED`, `COMMONS`) e
  `TimeWarpPathPiece` (cascalho com falhas; pontezinha de tábuas sobre água). Cada peça tem `seed` próprio, então sai
  igual em todos os chunks. Imperfeições: vidro faltando, bloco "emprestado" na parede, porta às vezes aberta, beiral
  irregular, pilar de terra ao lado da obra inacabada, placas com recados curtos ("brb", "back soon", "server restart
  at 9?"…). Baús com loot `chests/time_warp_house` e `time_warp_settlement` (pode ter um relógio). Comida pronta
  esquecida em fornalhas. **Nenhuma entidade** (sem aldeões/NPCs).
- Cliente: `TimeWarpEffects` (nuvens em y 108, sem leque do pôr do sol, luz do céu levemente dessaturada) e
  `TimeWarpClient` (neblina por `ViewportEvent.RenderFog`: 34 → 30 → 26 → 22 blocos por estágio, nunca além de 75%
  da distância de renderização; cor dessaturada por estágio). Vale em qualquer modo gráfico (uniforme do shader).
  Debaixo d'água/cegueira/escuridão fica o vanilla.
- **Limitação (texturas antigas):** não há troca de texturas por dimensão (exigiria mexer no atlas/renderer). A
  aproximação é por cores de bioma (grama/folhas verdes fortes, água azul), luz, neblina e blocos de aparência clássica.
  Nenhuma textura vanilla foi substituída; Overworld/Nether/End/Inverted Realm intactos.

### 4.15.3 — Time Warp Travel
- **Relógio:** segurar o botão de usar com um relógio na **mão principal** por 3 s (`chargeSeconds`). O cliente
  repete o "usar" a cada ~4 ticks; cada repetição chega ao servidor como `RightClickItem` e a carga continua se o
  intervalo for ≤ 10 ticks (`TimeWarpRules.chargeContinues`). O relógio vanilla não tem uso, então nada é cancelado;
  usar uma cama/bloco com o relógio na mão continua sendo o vanilla. Barra de progresso na barra de ação + tique.
- **Escala:** fator só por `DimensionType.getTeleportationScale` (os dois `coordinate_scale`), aplicado uma vez em
  `TimeWarp.enter/exit`; `teleportTo` não escala. Entrada só a partir do Overworld; saída sempre para o Overworld na
  posição **atual** escalada (×3,33), nunca de volta ao ponto de entrada.
- **Chegada:** coordenadas presas à borda do mundo com margem de 16. Time Warp: chão seco num raio de 16 (anéis,
  mais perto primeiro); senão a superfície do mar na coluna exata (pés no bloco do topo da água, cabeça fora) +
  respiração aquática 60 s + ar cheio. Overworld: chão firme sem fluido/lava/fogo/folhas num raio de 32, senão
  superfície de água, senão ponto de entrada salvo, senão surgimento do mundo. Nada é construído (sem plataformas).
- **Segurança:** cooldown de 30 s gravado (`last_travel`, relógio do mundo; sobrevive a relog) + cooldown visual do
  item; carga zerada antes do teleporte e `travelling` contra ativação dupla; `stopRiding` (o barco fica, nada é
  duplicado); nada criado nem consumido; exceção no teleporte = mensagem + log, sem cooldown.
- **Sem relógio (recuperação):** no Time Warp, sem relógio no inventário, ficar parado **olhando reto para o céu** por
  15 s (em terra, boiando ou num barco; respiração aquática enquanto boia) leva ao **ponto de entrada** salvo (sem
  escala → nunca é viagem rápida de graça). Dica no chat na primeira vez. Configurável (`driftRecovery`). Assentamentos
  podem ter um relógio no baú.
- **Regras da dimensão:** camas não funcionam ("You can't rest here. The sheets are still warm."), ponto de
  renascimento nunca fica no Time Warp (só por comando forçado), portal do Nether não acende (o vanilla levaria ao
  Nether e furaria a viagem do relógio).

### 4.15.4 — Exposure & Psychological Atmosphere
- `TimeWarpExposure`: +1 por tick no Time Warp (vivo, não espectador); fora, −`recoveryRate` (2) por tick **só
  online**. Teto = 5 estágios. Estágios (`stageSeconds` = 60): 0–60 estável, 60–120 anomalia, 120–180 instabilidade,
  180+ crítico. **Nunca mata** nem prende.
- Ao subir de estágio: aviso do Sistema (anomalia: cartão "Background readings are drifting."; instabilidade: cartão
  SYSTEM WARNING "Prolonged stay is not recommended."; crítico: janela SYSTEM WARNING + conquista oculta `lingered`).
  Entrada: janela "You have entered an unidentified region."; saída: cartão "Region identified. Signal restored.";
  login no Time Warp: "Connection restored. Region still unidentified.". Nenhum contador regressivo.
- Acontecimentos (servidor, por jogador, a cada segundo com chance e intervalo mínimo por estágio:
  `TimeWarpRules.eventDue`; estável ≥ 50 s e 0,6%/s; crítico ≥ 9 s e 11%/s; `eventFrequency` 0 desliga):
  porta distante na neblina, passos sem origem, som de caverna, pulso de neblina, passos que se aproximam por trás e
  param, silêncio repentino (para música/ambiente/clima por 8–14 s), mensagem SYSTEM ERROR ("Unable to identify the
  source of the signal.", "Last save: 5,479 days ago.", "Chunk already loaded by another session."…). **Compartilhado:**
  ruído dentro de uma construção próxima (baú, porta, fornalha, passos no assoalho) para todos os jogadores a até 48
  blocos do ponto. Nada muda blocos nem cria entidades; nada confirma a presença de alguém.
- Áudio no cliente (`TimeWarpClient`): categoria Ambiente do vanilla × `ambientVolume`; sons distantes trazidos para
  12 blocos na mesma direção com volume menor (o vanilla corta além de 16); no máximo 8 sons e 48 passos na fila;
  tudo parado/limpo ao sair da dimensão, trocar de nível e no logout.

### 4.15.5 — Quest & Notification Integration
- Avisos só por `HoloNotices` (`NoticeKind.TIME_WARP` e `NoticeStyle.SYSTEM_ERROR`, ambos no **fim** dos enums),
  grupo `time_warp`, chave de duplicata por mensagem, modificadores `time_warp` + `phenomenon:static|none` (ganchos do
  Holographic UI Phenomena; `client.holo.HoloPhenomena` continua vazio e ignora ids desconhecidos).
- Conquistas ocultas (sem display, sem toast, sem aba): `respectcraft:time_warp/entered`, `found_island`,
  `found_house` (casa ou assentamento), `explored_settlement`, `lingered` (dada pela exposição) e `returned`.
- **Correção no Quest Engine:** o NeoForge 21.1 só dispara `AdvancementEarnEvent` para conquistas **com display**.
  `QuestService.onAdvancementProgress` entrega ao motor a conclusão das conquistas sem display (as com display nunca
  passam por ali: sem contagem dupla). O retroativo do login já funcionava.
- Missão secreta `respectcraft:secret/echoes_of_an_old_world`: descoberta ao estar no Time Warp, aceite/resgate
  automáticos, objetivos vagos "Find land", "Find where someone lived", "Come back"; 120 XP. As três missões antigas
  não foram alteradas. `explored_settlement` e `lingered` ficam prontas para missões futuras.
- Configs: `respectcraft-timewarp-server.toml` (viagem, recuperação, estágios, recuperação da exposição,
  acontecimentos, frequência, avisos) e `respectcraft-timewarp-client.toml` (volume, pulsos, escala da neblina
  0,8–1,6). Textos em inglês (`en_us.json`); `pt_br` não foi pedido.
- Comandos (permissão 2): `/respectcraft warp enter|leave [players]`, `status [player]`, `exposure <players> <s>`,
  `happen <player> <acontecimento>`, `survey [raio] [seed]`, `census [raio] [seed]`, `pieces`, `check`.

## 3. Regras de persistência e recuperação da exposição

| Situação | O que acontece |
|---|---|
| Logout no Time Warp | Exposição, estágio, ponto de entrada e cooldown ficam no arquivo do jogador; nada conta offline. Ao voltar, continua de onde parou (testado: 102 s → 119 s depois de reconectar). |
| Fora do Time Warp, online | −2 ticks por tick (3 min lá dentro somem em 1,5 min fora). |
| Morte no Time Warp | Renasce no ponto de renascimento (nunca no Time Warp); exposição copiada e passa a cair; ponto de entrada apagado. |
| Servidor reinicia/cai | Volta o último salvamento do jogador (testado: jogador reapareceu no Time Warp com o ponto de entrada intacto). |
| Saída por outro meio (`/tp`, comando) | Ponto de entrada apagado; estado do cliente limpo; exposição cai normalmente. |
| Entrada por comando/`/tp` sem relógio | Sem ponto de entrada: a recuperação sem relógio cai no surgimento do Overworld. |

## 4. Validação

### Executado (nesta sessão, com saída conferida)
- `./gradlew build` → `build/libs/respectcraft-0.5.7.jar`; `/respectcraft selftest` → `RespectCraft self-test: OK`
  (casos novos: escala 100/300/1000 → 333/999/3330, negativos, ida e volta sem escala dupla, borda, quatro estágios,
  recuperação, carga, cooldown, acontecimentos, neblina).
- `bash .github/scripts/server-smoke-test.sh` → **exit 0** (servidor dedicado; todas as checagens antigas + Time Warp
  pronto, 0 blocos estranhos, água 95,52% / 95,48%, censo com casas, config gerada).
- `bbsync.py`, `test_rigkit.py`, `validate_rig.py --repo`, `test_scenekit.py`, `make_templates.py --check`,
  `check_shaders.py`, `test_export.py` → todos sem falhas.
- **Worldgen (várias seeds, 16 384 × 16 384 blocos, passo 128):** água 95,66% (seed do mundo de teste), 95,52% (1),
  94,67% (42), 94,96% (−987654321), 95,48% (20111118); ~110–127 ilhas por amostra (0,4–0,5/km²; ~60% pequenas).
  **Estruturas (`census`):** casas 1,03–1,33/km², assentamentos 0,09–0,11/km² (≈ 1 a cada 10 km²) nas seeds 1, 42 e
  20111118. `check`: 0 blocos do Inverted Realm (`respectcraft:*`, terracota roxa, micélio, ametista). Nenhuma falha
  de geração de chunks depois da correção da placa (ver 5).
- **Clientes reais (Xvfb, llvmpipe, 1280×720, dois clientes):** evidências em `docs/evidencias/stage4_15/`:
  oceano aberto de barco (01, 02), ilha vazia (03), ilha com abrigo + plantação (04, 05), interior (06, 07),
  assentamento (08–10), entardecer/noite/chuva (11–13), pulso de neblina (14, 15), relógio carregando/chegada (16, 17),
  avisos de estágio (18–21), dois jogadores com exposições diferentes no mesmo instante (22, 23), cama recusada + dica
  sem relógio (24), recuperação sem relógio (25), morte/renascimento (26, 27), missão secreta concluída (28).
- **Viagem (logs):** relógio OW (334, −998) → TW (100, 63, −300) oceano; TW (−299, 1001) → OW (−998, 62, 3331)
  superfície de água; OW (−2689, −2689) → TW (−807, 77, −807) **terra**; borda 2000 → TW (600, −700) → OW (984, −984);
  recuperação sem relógio (terra e boiando) → ponto de entrada; morte no TW → renasce no OW sem ponto de entrada.
- **Multiplayer:** Ana em crítico recebe a janela SYSTEM WARNING enquanto Bia (estável) não recebe nada; missão
  descoberta individualmente; acontecimentos individuais por pacote para um jogador; ruído de construção enviado a
  todos por perto.

### Pendente / não verificado
- **Áudio audível:** o container não tem dispositivo OpenAL; os caminhos de som rodaram sem erro, mas volume,
  distância e direção não foram ouvidos. Conferir num cliente com áudio.
- Só renderização por software (llvmpipe, Fast, distância 4); Fabulous/GPU real e distâncias maiores não testadas
  (a neblina é por uniforme e é limitada a 75% da distância, então deve valer igual).
- Acontecimentos por sorteio natural ao longo de vários minutos não foram cronometrados em cliente (foram forçados com
  `happen` e as regras estão no `SelfTest`); `-Drespectcraft.timewarp.debug=true` loga cada um.
- Docas: geradas pelo código (vistas no censo/peças), mas nenhuma foi fotografada.
- CI do GitHub (Build) roda no PR.

## 5. Problemas encontrados e corrigidos durante a validação
- Placa com texto durante a geração chamava `SignBlockEntity.setText` → `sendBlockUpdated` com `Level` nulo e
  derrubava a geração do chunk do assentamento. Agora o texto é carregado pelo codec (`loadCustomOnly`).
- Neblina de 52 blocos deixava o horizonte nítido a partir do barco: reduzida (34–22), presa a 75% da distância de
  renderização, sem leque do pôr do sol.
- Conquistas ocultas não moviam a missão (NeoForge só dispara `AdvancementEarnEvent` com display): ver 4.15.5.
- `found_house` só aceitava a casa isolada; agora casa **ou** assentamento.
- Recuperação sem relógio disparava ao boiar parado por acaso: virou gesto explícito (olhar para o céu).

## 6. Arquivos principais
`dimension/timewarp/*` (TimeWarp, TimeWarpTravel, TimeWarpExposure, TimeWarpRules, TimeWarpConfig, TimeWarpSession,
TimeWarpStructure(s), TimeWarpPiece, TimeWarpPathPiece, TimeWarpCommands), `network/TimeWarpNetworking`,
`client/dimension/TimeWarpEffects` e `TimeWarpClient`, `quest/QuestService` (progresso de conquista sem display),
`notice/NoticeKind`/`NoticeStyle` (valores no fim), `registry/ModAttachments` (`time_warp_session`, sem save),
`data/respectcraft/{dimension,dimension_type}/time_warp.json`, `worldgen/{noise,noise_settings,density_function,
biome,structure,structure_set,placed_feature}/time_warp*`, `tags/worldgen/biome/time_warp_land.json`,
`loot_table/chests/time_warp_*`, `advancement/time_warp/*`, `quest/secret/echoes_of_an_old_world.json`, `en_us.json`,
`.github/scripts/server-smoke-test.sh`.

## 7. Próximos passos sugeridos
Texturas antigas por dimensão (ou pacote de recursos opcional), fenômenos holográficos usando os modificadores já
enviados, missões secretas com `explored_settlement`/`lingered`, acontecimentos maiores compartilhados — sempre sem
confirmar a presença (roadmap, seção 15).
