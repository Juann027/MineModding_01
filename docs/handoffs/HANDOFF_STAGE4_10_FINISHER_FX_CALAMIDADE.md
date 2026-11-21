# Handoff — Etapa 4.10: Finisher Engine com efeitos do Calamidade_Awakening 0.10.0

Status: **implementada e testada** (build, `SelfTest`, teste de fumaça do servidor dedicado com o Calamidade,
shaders compilados com `glslangValidator`, cliente real em Xvfb — seção 8). Base: `main` em `f44408d` (depois da
Etapa 4.9.1). Versão continua `0.5.7` (sem release).

## 1. Pedido

Pedido explícito do dono: o **Calamidade_Awakening** (github.com/Juann027/Calamidade_Awakening-Mod-1.21.1, release
**v0.10.0**) vira dependência **obrigatória** e o motor de efeitos visuais do Finisher Engine (e, no futuro, de outros
sistemas). Cinco etapas: dependência; ponte única `fx`; efeitos das três finalizações; ganchos para o futuro (GeckoLib e
`SkillEffect`); testes e entrega.

Lido antes: `CLAUDE.md`, handoff 4.8, pacotes `finisher`/`testgame`/`client.cinematic` e, no Calamidade v0.10.0,
`AGENTS.md`, `docs/INTEGRACAO-0.10.md`, `docs/ai/usar-como-dependencia.md`, `docs/ai/README.md`, `docs/ai/glsl.md`,
`docs/ai/API-REFERENCIA.md` e as receitas (1, 4, 13 e 17 em detalhe). Assinaturas conferidas com `javap` no jar.

## 2. Dependência (Etapa 1)

| Item | Valor |
|---|---|
| Jar | `calamidade_awakening-0.10.0.jar` do release v0.10.0 do GitHub, SHA-256 `c6229eb58a486c976429fc661b5228521ff87c96f69aaad10e0281170529bcde` |
| Onde | `libs/` (no `.gitignore`), baixado pela tarefa `baixarCalamidade` (classe `BaixarVerificado` no `build.gradle`, compatível com o configuration cache; `.part` + SHA-256; `-Pcalamidade.url=file:///...` troca o endereço) |
| Gradle | `compileOnly` + `runtimeOnly` (nada embutido; o teste de fumaça reprova jar com `dev/calamidade_awakening`) |
| RimuLib | **não** é baixada de novo: continua a do CurseMaven (`curse.maven:rimulib-1753907:9676532`) |
| `neoforge.mods.toml` | `calamidade_awakening`, `required`, `[0.10.0,0.11.0)`, `AFTER`, `BOTH` (faixa em `gradle.properties`: `calamidade_version_range`) |
| Instalação | **cinco jars** (README): RespectCraft, GeckoLib, SmartBrainLib, Rimulib e Calamidade — servidor e clientes com a mesma versão do Calamidade |
| CI | o `./gradlew build` baixa o jar do GitHub; passo novo "Shaders fx" instala `glslang-tools` e roda `tools/fx/check_shaders.py --strict`; o teste de fumaça confere `Calamidade_Awakening 0.10.x (calamidade_awakening)` no servidor dedicado, o registro dos efeitos e `/calamidade status` com `0 / 64` |

## 3. Ponte única: pacote `com.respectcraft.fx` (Etapa 2)

| Classe | Papel |
|---|---|
| `FinisherFxDef` | Dados: `scene` (id do efeito HERO), `look` (desenho: `TAKEDOWN_FOCUS`, `RASTEIRA_SWEEP`, `COMBO_PRESSURE`), `tier` (`SUBTLE` 0,55 · `MEDIUM` 0,8 · `INTENSE` 1,0), `orientation` (`ARRIVING_FROM_ATTACKER` ou `ANCHOR_YAW`). Ids derivados: `finisher_fx/hit_<tier>_<1..3>`, `finisher_fx/fall_<tier>`, `finisher_fx/boss_atmosphere` |
| `FinisherDef.fxDef` / `fx()` / `withFx()` | Campo **opcional**. Sem ele a finalização é exatamente a de antes (partículas e sons vanilla do `FinisherTestMechanic`) |
| `FinisherFxRules` | Regras puras (no `SelfTest`): camadas por força, momentos da cena tirados dos eventos das animações (`cues`), `due` (cada momento uma vez, mesmo com tick pulado), fim → `RELEASE`/abort → `STOP`, papel da fonte, acessibilidade (`screenStrength`, `impactFrameInverts`), `parseFall` |
| `FinisherFx` | **Único dono** dos efeitos de finalização: `register()` no construtor do mod (lado comum), `start` no início da sessão, `tick` no tick da sessão, `end` em `abort`/`complete`. Estado por sessão em `FinisherFx.Scene`, guardado na `Session` do `FinisherTestMechanic` (nada estático) |
| `GeoFxKeyframes` | Gancho de mobs (Etapa 4, seção 6) |

Como a sessão usa a ponte (`FinisherTestMechanic`):

- `start`: depois de `playTogether`/`play` e dos `triggerAnim`, `FinisherFx.start(executor, vítima, def, ponto da cena,
  yaw, chefe?)`. Ponto da cena = o mesmo dos parâmetros da animação (meio dos dois, no chão mais baixo).
  - HERO: `Placement.at(ponto)` + `arrivingFrom(executor no mesmo y)` ou `withYaw(yaw da âncora)`, `withSource(player)`.
  - Chefe (`FinisherParticipants.Kind.BOSS`, tag `c:bosses`, em qualquer papel): `finisher_fx/boss_atmosphere`.
- `onPlayerTick` (uma vez por tick da sessão): `FinisherFx.tick(scene, tick da cena)` dispara os **golpes** (efeito
  `hit_<tier>_<força>` na vítima, `arrivingFrom` executor) e as **quedas** (`fall_<tier>` onde o corpo bate: recuo para
  trás da vítima, como a poeira vanilla) cujos **marcadores** chegaram. Os marcadores da timeline da cena são os ticks
  dos `EVENT_HIT` do clip do executor e dos `EVENT_FALL` do clip da vítima (os dois começam no mesmo tick do servidor).
  Por isso os golpes também saem quando o executor é um mob (o clip dele é `Hold`, sem eventos). Quedas só com
  vítima jogador (`FinisherFxRules.forSession`): mob vítima fica parado e não cai.
- `abort` → `FinisherFx.end(scene, ABORT)` = `stop()` em tudo. `complete` → `end(scene, COMPLETE)` = `release()`, que só
  age em sustain (atmosfera do chefe: saída de 10 ticks) e cai para `stop()` nos demais. Nada da cena fica ativo.
- Fonte: `EffectSource.player` do executor quando é jogador, senão da vítima jogador (reserva de vagas dos jogadores;
  sessão sem jogador não existe). Recusa por limite do Calamidade = `Optional.empty()`: a cena continua sem aquele
  efeito (log `debug`).
- Com `FinisherFxDef`, as partículas vanilla dos golpes e da queda saem; os **sons** e o lampejo de dano
  (`broadcastDamageEvent`) continuam no `FinisherTestMechanic`.
- Câmera: continua 100% da `CinematicCamera`. Nenhum efeito tem `film`, conjurador (`withCaster`) ou `post.shake`.
- Pontos: âncora da cena + deslocamentos no referencial orientado (`Point.facing`, `post.facing`). Nenhum osso do
  GeckoLib é lido.

## 4. Os efeitos (Etapa 3)

Todos no namespace `respectcraft`, registrados por `FinisherFx.register()`. Shaders em
`assets/respectcraft/shaders/fx/` com a cópia de `calamidade_fx.glsl` (idêntica à do motor; o `check_shaders.py`
compara com o jar). Intensidade crescente pelo `tier`.

| Efeito | Prioridade | Conteúdo |
|---|---|---|
| `finisher_fx/takedown_scene` (60 t) | HERO | Linhas de foco de anime convergindo no peito entre os dois (`finisher_takedown.fsh`, pulsam a cada golpe), vinheta suave pela energia, luz quente baixa (`Light.point`, peso = `pulse`) e disco no chão sob a vítima (peso = `energy`) |
| `finisher_fx/rasteira_scene` (40 t) | HERO | Arco luminoso rente ao chão em volta das pernas da vítima, do lado do executor (`finisher_rasteira.fsh`, varre da esquerda à direita nos 8 ticks antes do golpe, com faíscas), 5 feixes curtos do arco (`Beam`, peso `sweep_fade`), luz ao longo do feixe central e disco no golpe |
| `finisher_fx/combo_scene` (140 t) | HERO | Cúpula de pressão (raio 2,3) com aro e grade que pulsam a cada golpe e onda no chão a cada golpe/queda (`finisher_combo.fsh`), octógono de 8 feixes no chão aceso pela energia acumulada, luz vermelha forte (`pulse`) e disco (`energy`), bloom e aberração cromática nos impulsos |
| `finisher_fx/hit_<tier>_1` (16 t) | normal | Luz breve no ponto do golpe |
| `finisher_fx/hit_<tier>_2` | normal | + anel de luz no chão que abre (`Light.ring`, raio pela trilha `ring_r`) |
| `finisher_fx/hit_<tier>_3` | **HERO** | + impact frame de duas cores (`finisher_impact_frame.fsh`, 6 ticks, holofote claro na vítima, inverte depois de 2 ticks). A cena cede o passe ao impact frame nesses ticks (fases `impact_frame_<marcador>`) |
| `finisher_fx/fall_<tier>` (24 t) | normal | Onda de poeira com ruído que corre pelo chão (`finisher_dust_wave.fsh`, a poeira cobre a cena em vez de somar luz) + disco e anel de luz no chão |
| `finisher_fx/boss_atmosphere` (sustain) | normal | Atmosfera: céu escurecido e avermelhado, névoa quente, exposição 0,88, tinta ambiente. Entrada 20 t, laço, saída 10 t no `release` |

Timeline da cena (`FinisherFx.sceneTimeline`): fase `scene` do tamanho da finalização, um marcador e uma fase curta por
golpe/queda (`hit_0`, `fall_0`...), `impact_frame_<marcador>` nos golpes de força 3, trilhas `pulse` (impulso que decai
a cada momento), `energy` (sobe com os golpes, some no fim), `sweep` e `sweep_fade` (RASTEIRA). Ticks: TAKEDOWN 14/26/44
(força 1, 1, 3); RASTEIRA 14 (força 2); COMBO 25/43 (força 1, 2) e quedas 62/65.

Acessibilidade (regra 6 do Calamidade), em `client.fx.FinisherFxClient`:

- Todo passe próprio multiplica a força por `post.screenEffects()` (0 = nenhum passe).
- Impact frame: com "ocultar clarões" (`!flashAllowed()`) cai para 35% e **não inverte** (não pisca).
- Linhas de foco: só trocam de desenho (a cada 2 ticks) com `motionEffects()`; sem eles ficam paradas.
- `bloom`, `vignette`, `aberration` passam pelos métodos do `AddonLayers.Post` (já respeitam as opções).
- Orçamento: cada callback confere `post.passesLeft()`; os efeitos de golpe 1/2 não usam passe (só luzes e bloom).

## 5. O que não mudou

- `CinematicCamera` (câmera, tremor, FOV, letterbox), eventos de som, contador de derrotas, recusas, palco e limpeza.
- Finalizações sem `FinisherFxDef` (nenhuma hoje, mas o caminho continua e está no `SelfTest`).

## 6. Ganchos para o futuro (Etapa 4, só infraestrutura)

**GeckoLib → Calamidade para mobs** (`fx.GeoFxKeyframes`):

1. No `.animation.json` do mob, um keyframe de instrução do GeckoLib 4 (bloco `"timeline"`):
   `"timeline": {"0.6": "calamidade:respectcraft:golpe;"}` (`calamidade:golpe` usa o namespace `respectcraft`; várias
   instruções separadas por `;`; o valor pode ser lista).
2. Registre o efeito no construtor do mod (`Effects.register` + timeline/luzes...).
3. No servidor, dispare a animação com `GeoFxKeyframes.triggerAnim(mob, controlador, gatilho,
   RespectCraft.id("animations/entity/<mob>.animation.json"), "animation.<mob>.<clip>")`: chama
   `GeoEntity.triggerAnim` e agenda o efeito para o tick do keyframe (segundos × 20, arredondado), começado com
   `EffectTriggers.entity(efeito).fire(mob)` (preso ao mob, yaw seguindo o corpo, fonte = o mob, limite 4 por mob).

Decisão: as instruções são lidas **no servidor** (do jar do mod) e agendadas pelo relógio dele, porque os handlers de
keyframe do GeckoLib rodam só nos clientes e o Calamidade começa efeitos no servidor (o próprio guia
`INTEGRACAO-0.10.md` desaconselha pacote cliente→servidor para isso). A agenda é o attachment sem serializador
`respectcraft:geo_fx_schedule` (não é salvo, some com o mob). Animações que só o cliente decide (idle/walk) não disparam
efeitos. Nenhum mob, animação ou efeito novo foi criado.

**`SkillEffect.Kind.VISUAL_FX`**: `SkillEffect.visualFx(id)` referencia um efeito do Calamidade (`visualFxId()`).
Nenhuma habilidade usa; `SkillService` não aplica (filtra por tipo); a árvore mostra "Visual effect" se um dia aparecer.

## 7. Decisões e limitações

- **Golpes pelos marcadores da cena, não pelo evento de animação do jogador.** Mesmo tick (os clips e o HERO começam
  juntos), mas funciona também com executor mob (que não tem eventos). O evento de animação continua tocando os sons.
- **Atmosfera de chefe com sustain**: no fim da cena ela recebe `release` e some em 10 ticks (0,5 s); no cancelamento,
  `stop` imediato. Os demais efeitos param no último tick da cena.
- **Golpe de força 3 é HERO**: com a cena, são 2 HERO no quadro (o máximo do motor); o impact frame nunca é cortado.
  Em LOW (1 passe HERO) a cena e o impact frame disputam o passe; a cena cede nos ticks do quadro.
- **Pontos aproximados**: o foco do TAKEDOWN fica no peito entre os dois e o arco da RASTEIRA na distância de palco de
  dois jogadores (0,58 à frente do ponto da cena). Com mobs largos (palco mais longo) o arco fica um pouco à frente da
  vítima. Ossos reais virão quando o Calamidade tiver pontos presos a ossos.
- **SmartBrainLib** segue declarada sem uso (decisão do dono pendente, ver 4.9.1).
- O verificador de shaders precisa do jar da RimuLib no cache do Gradle (roda depois do build).

## 8. Testes

- `./gradlew build`: OK. `python3 tools/fx/check_shaders.py`: 7 shaders OK, `calamidade_fx.glsl` igual à do motor
  (o verificador reprova um shader quebrado, conferido).
- `bash .github/scripts/server-smoke-test.sh`: OK — `Calamidade_Awakening 0.10.0 (calamidade_awakening)` no servidor
  dedicado, `RespectCraft finisher FX registered on Calamidade_Awakening`, `RespectCraft self-test: OK` (casos novos
  `fx:`), `Live instances on server: 0 / 64`, jar sem Calamidade.
- `SelfTest` (casos `fx:`): camadas por força (impact frame só na 3), ids por tier/força, FxDef opcional (sem ele,
  vanilla; cópia com clip de mob mantém), intensidade crescente, ticks e forças dos golpes/quedas das três
  finalizações, marcadores únicos e timeline HERO do tamanho da cena, cada momento dispara uma vez, a cena só cede o
  passe na força 3, fim/abort, fonte, acessibilidade, keyframes `calamidade:` e `SkillEffect.VISUAL_FX` sem uso.
- Cliente real: ver seção 9 (preenchida com o resultado do roteiro `docs/evidencias/etapa4_10/cenario_cliente_real.sh`).

## 9. Cliente real (Xvfb, llvmpipe)

Roteiro `docs/evidencias/etapa4_10/cenario_cliente_real.sh` (servidor dedicado de desenvolvimento + um cliente,
854×480, BALANCED, jogador Juca × zumbi vanilla parado com capacete; F7 do Calamidade aberto). Evidências na mesma
pasta (folhas de quadros extraídas das gravações e capturas) e `calamidade_status.txt` (saídas do `/calamidade status`).

| Caso | Resultado |
|---|---|
| COMBO completo (`sheet_combo.jpg`) | F7 "Protagonists (HERO): 1 drawn · 2/2 passes", 8 feixes do octógono e luzes "pass HERO"; luz e anel nos golpes 25/43, ondas nas quedas 62/65 (nesse teste a vítima era o zumbi: a regra "queda só com vítima jogador" entrou depois — seção 3 — e está no `SelfTest`). Depois: `0 / 64` |
| RASTEIRA completa (`sheet_rasteira.jpg`) | Arco ciano varrendo em volta das pernas antes do golpe, anel do golpe de força 2; depois: F7 "Events: 0 resident", `0 / 64` |
| TAKEDOWN completo (`sheet_takedown.jpg`) | Discreto: luz baixa e disco; impact frame de duas cores no golpe de força 3 (tick 44), anel depois; F7 no fim "Events: 0 resident", `0 / 64` |
| Abort (`/stopitstopnow` no meio do COMBO) | `2 / 64` durante a cena (HERO + golpe) e `0 / 64` logo depois; F7 zerado (`abort_after_f7.jpg`) |
| Contra chefe (Wither NoAI, `c:bosses`) | Céu escurecido e avermelhado, F7 "Atmosphere: 1 active · pass yes" e "HERO: 2 drawn" no golpe final (`boss_mid.jpg`); depois do fim: atmosfera 0, `0 / 64` (`boss_after_f7.jpg`) |

Primeira tentativa do roteiro descartada: com dificuldade *easy* nasceram monstros em volta e um golpe deles cancelou a
cena ("interrupted by damage", comportamento correto da 4.8). O roteiro passou a desligar `doMobSpawning` e limpar o
mundo. Não testado com cliente real: opções de acessibilidade (cobertas pelas regras puras no `SelfTest`), F8 em
LOW/HIGH, dois clientes, GPU real (só llvmpipe).

## 10. Para testar no jogo (dono)

1. Instale os cinco jars (servidor e cliente) e entre num mundo de teste (faça backup).
2. `/respectcraft admin finisher <você> <zumbi> respectcraft:combo` (e `rasteira`, `takedown`) com **F7** aberto: a
   linha "Protagonists (HERO)" mostra 1 desenhado durante a cena (2 no golpe final do TAKEDOWN); depois do fim,
   "Events: 0 resident" e `/calamidade status` = `0 / 64`.
3. Cancele no meio (`/stopitstopnow` ou Shift como vítima): tudo some na hora.
4. Contra um chefe (Wither com NoAI): o céu escurece e avermelha e volta em meio segundo depois do fim.
5. Opções do Calamidade: intensidade dos efeitos de tela 0 (sem passes), ocultar clarões (impact frame fraco e sem
   inverter), efeitos de movimento desligados (linhas do TAKEDOWN paradas). F8 em LOW/BALANCED/HIGH.
6. Multiplayer: um segundo jogador perto vê os mesmos efeitos (o Calamidade sincroniza pelo servidor).
