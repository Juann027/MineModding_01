# CLAUDE.md — TupiniCraft

RPG de humor para **Minecraft Java 1.21.1 / NeoForge 21.1.x / Java 21**: criador de personagem, roleta de
nascimento, habilidades, necessidades e consumíveis, rig GeckoLib do jogador com animações sincronizadas,
finalizações cinematográficas, contador de derrotas e o mob Leão do Fim. Versão atual: `0.5.7` (`gradle.properties`).

Leia este arquivo, o `README.md` e só os arquivos do pedido. Os handoffs (fim do arquivo) têm os detalhes.

## Identidade e stack

| Item | Valor |
|---|---|
| Mod id / pacote | `tupinicraft` (`TupiniCraft.MOD_ID`, `TupiniCraft.id("caminho")`) / `com.tupinicraft` |
| Minecraft / NeoForge | 1.21.1 / 21.1.265 (ModDevGradle, Parchment 2024.11.17), Gradle wrapper 9.2.1 |
| Java | 21 (toolchain no `build.gradle`) |
| GeckoLib | **4.9.3** (`software.bernie.geckolib:geckolib-neoforge-1.21.1`, Cloudsmith) — cliente e servidor. Não use a 5. |
| SmartBrainLib | 1.16.11 (`net.tslat.smartbrainlib:SmartBrainLib-neoforge-1.21.1`, Cloudsmith) — cliente e servidor |
| UI Lib / Architectury | **Não são dependências.** As telas usam o framework próprio `client.ui`. Não reintroduza. |

- Versões em `gradle.properties`; `neoforge.mods.toml` é gerado de `src/main/templates/`.
- Só APIs do NeoForge **21.1** (nada de Forge 1.20.x, Fabric ou NeoForge 21.2+: `RegisterClientPayloadHandlersEvent`,
  sync automático de attachments, `SavedDataType` etc.). Confira a assinatura 1.21.1.
- Nunca copie classes das bibliotecas para dentro do mod. Sem dependências novas sem pedido explícito.

## Arquitetura (pacotes)

| Pacote | O que é |
|---|---|
| `TupiniCraft`, `registry` | Entrada comum: `ModItems`, `ModEntities`, `ModAttachments`, `ModSounds`, `ModCreativeTabs`; config SERVER `tupinicraft-needs-server.toml`. |
| `client` | Tudo que importa `net.minecraft.client.*`. Entrada `client.TupiniCraftClient` (`@Mod(dist = CLIENT)`). |
| `client.playergeo` | **Rig GeckoLib do jogador.** `PlayerRigLayer` é a primeira camada do `PlayerRenderer` vanilla: lê a pose vanilla, desenha o rig e devolve a pose ao `PlayerModel` (armadura, itens e camadas acompanham). F8 = modo de teste, F9 = aceno. |
| `client.bodylayer` | **Body Layers**: busto, cabelo, pele, rosto e acessórios como `BodyLayerDefinition` em `BodyLayers`. |
| `animation` | **Animation Manager.** Animações são dados (`PlayerAnimations`, `PlayerAnimationDef`, `AnimationSequence`); `PlayerAnimationService` é o único que inicia/para; estado sincronizado `PlayerAnimationState` (attachment). Comando `/tupinianim`. |
| `finisher`, `testgame`, `client.cinematic` | **Finisher Engine**: `Finishers` (dados), `FinisherTestMechanic` (sessão no servidor), `CameraTrack` + `CinematicCamera` (câmera dos participantes). `DefeatCounter` + `client.testgame.DefeatCounterNameplate` (contador 🍆💦). Comando `/tupinitest`. |
| `character`, `client.character` | **Character Creator** (tecla K → `CharacterHubScreen`): aparência (`CharacterAppearance`, `CharacterAppearanceService`). |
| `profile`, `client.roulette` | **Ficha** (`CharacterProfile`, `ProfileService`) e **roleta de nascimento** (`BirthRoulette`, `BirthOrigin`, `LegacyOriginIds`). |
| `skill`, `client.skilltree` | **Skill Trees**: 3 abas (`SkillBranch`), 18 habilidades em código (`Skills`), compra e modificadores em `SkillService`. |
| `needs`, `client.hud` | **Necessidades** (`Needs`, `NeedsRules`, `NeedsService`) e **consumíveis** (`NeedEffects`/`NeedEffectDef`, `ConsumableItem`, `ChamberPotItem`). Orbe de Emoção na tela; painel Body & Traits no inventário. |
| `network` | Payloads (`GameplayNetworking`, `TestGameNetworking`) e caches de cliente. |
| `entity`, `client.render`, `command` | Mob **Leão do Fim** (GeckoLib + SmartBrainLib), renderer e `/leaodofim`. `TupiniCommands` = `/tupini`. |
| `selftest` | `SelfTest`: regras puras, rodado por `/tupini selftest` (teste de fumaça). |
| `biome`, `dimension`, `npc` | Só `LEIA-ME.md`: espaço reservado, sem código. |

## Regras importantes

- **Servidor autoritativo.** Sorteio da roleta, compra de habilidades, efeitos de necessidades, finalizações,
  dano e contador de derrotas acontecem só no servidor. O cliente envia só a intenção e exibe caches.
- **Separação de lado.** Nada fora de `client` importa `net.minecraft.client.*`. Handlers servidor→cliente são
  registrados por **lambdas** que chamam a classe do cliente (não troque por method reference).
- **Um dono por dado.** Ficha só por `ProfileService`; necessidades e efeitos só por `NeedsService`; animações só por
  `PlayerAnimationService`; aparência só por `CharacterAppearanceService`; derrotas só por `DefeatCounter`.
  Não crie sistemas paralelos para algo que já existe — estenda o existente.
- **Sem estado de mundo em `static`.** Estáticos só para caches visuais do cliente, limpos no logout.
- **Sincronização multiplayer:** login, respawn, troca de dimensão, `StartTracking` e quando o valor muda. Nunca a
  cada tick. Animações trocam pelo relógio do servidor (`PlayerAnimationState.at`); não mande um pacote por clip.
- **Modificadores de atributo** do mod são transitórios (`tupinicraft:stat/...`) e recolocados por
  `SkillService.refreshModifiers`; nunca grave no save.
- **Roleta:** 8 origens com peso e bônus (`BirthOrigin`); sorteio no servidor; no máximo **1** novo giro
  (`CharacterProfile.canReroll`), que zera habilidades e devolve os pontos. A tela só apresenta o resultado.
- **Rig/render:** não cancele `RenderPlayerEvent.Pre` nem desenhe o jogador fora do renderer vanilla. Em
  `PlayerRigModel.compose` mantenha o `GeoBone::resetStateChanges` no fim. Peça de corpo nova = `BodyLayerDefinition`
  (não crie `RenderLayer` por peça). Pele/rosto do criador são Body Layers sobre a skin da conta; não troque a textura.
- **Cenas de dois participantes:** um clip `FULL_BODY` por participante, mesmo comprimento, iniciados por
  `playTogether`; posição no mundo é da sessão, keyframes só com deslocamentos que voltam a zero.
- **Câmera cinematográfica** segue o estado de animação sincronizado e restaura a perspectiva em qualquer saída.
- **Contador de derrotas:** +1 para o alvo só em `FinisherTestMechanic.complete` (`abort` nunca conta). A plaqueta
  replica a matemática do `renderNameTag` 1.21.1; ícones são a fonte bitmap `tupinicraft:defeat_icons`, não emoji.
- **Necessidades:** Coom, Bexiga, Intestino, Emoção (id `humor`), Hidratação, Energia. Medidores detalhados só no
  painel do inventário; na tela de jogo, só o orbe de Emoção. Consumível novo = entrada em `NeedEffects`.
- **Telas** herdam `client.ui.TupiScreen` e usam os widgets de `client.ui` (texturas próprias, sem blur vanilla).
- **Textos** em português do Brasil via `Component`/lang (`pt_br.json` e `en_us.json` com as mesmas chaves).
- **Mobs:** recursos `geo/entity/<nome>.geo.json`, `animations/entity/<nome>.animation.json`,
  `textures/entity/<nome>.png`; GeckoLib só anima, dano/alvo/IA no servidor. Registre em `ModEntities` + atributos,
  renderer em `client.render` registrado em `TupiniCraftClient`.
- **Tema:** o mod não tem mais eleições, política, partidos, mandatos, urna, economia ou símbolos nacionais. Não
  reintroduza (nem a partir do histórico do Git) sem pedido explícito.

## IDs persistentes e compatibilidade de saves (não renomear)

| Dado | Onde fica |
|---|---|
| `tupinicraft:character_profile`, `tupinicraft:needs`, `tupinicraft:need_effects`, `tupinicraft:player_animation` | attachments (`ModAttachments`); nomes de campos dos Codecs também são estáveis |
| `tupinicraft_character_appearance_v1`, `tupinicraft_special_defeats`, `tupinicraft_allow_mob_finishers_test` | `persistentData` do jogador |
| ids de `BirthOrigin`, `CharacterAttribute`, `Need`, `Skills` (`combate/...`), finalizações e animações | gravados na ficha/estado |
| itens, `tupinicraft:leao_do_fim` | registries |

- Id de origem aposentado: adicione o alias em `profile.LegacyOriginIds` (lido pelo Codec da ficha e regravado com o
  id novo) e um caso no `SelfTest`. Nunca converta um perfil inválido em "personagem não criado".
- Remover item/entidade registrado apaga essas instâncias em mundos antigos: avise no PR e no handoff.
- Recomende sempre backup dos mundos antes de atualizar.

## Blockbench e animações do jogador

- Projetos `.bbmodel` do jogador em `model_sources/player/` (`tupinicraft_animated_player`, `tupinicraft_player_combat`,
  `tupinicraft_combo_scene` — este só para ajustar o tempo da cena). Leão do Fim: `model_sources/leao_do_fim.bbmodel`
  (`tools/export_leao.py`, `tools/validate_leao.py`).
- `tools/player_anim/export.py` gera a biblioteca de combate (`assets/tupinicraft/animations/player/
  tupinicraft_player_combat.animation.json`, `PlayerAnimations.COMBAT_FILE`) e os `.bbmodel` a partir de
  `poses.py`/`clips.py`.
- `tools/player_anim/master.json` escolhe a fonte de verdade: `gerador` (scripts) ou `blockbench` (edição manual).
  Editou keyframes no Blockbench? Mude para `blockbench`. O exportador recusa sobrescrever arquivo editado fora dele
  (manifesto de hashes); `--force` regrava com cópia em `.backup/` e nunca com mestre `blockbench`. Use
  `--dry-run`/`--check`/`--out`.
- Convenções GeckoLib 4.9.3: rotação com X e Y invertidos, posição com X invertido, ordem Z·Y·X. O `.bbmodel` guarda
  a convenção do Blockbench (`bbsync.flip`); `python3 tools/player_anim/bbsync.py` confere os dois (roda no CI).
- Guia passo a passo: `tools/player_anim/README.md`. Conferência com o Blockbench real: `tools/player_anim/blockbench/check.sh`.

## Build e testes

```bash
./gradlew build                          # compila e empacota (build/libs/tupinicraft-<versão>.jar)
bash .github/scripts/server-smoke-test.sh # servidor dedicado: Leão do Fim, /tupini selftest, catálogos
python3 tools/player_anim/bbsync.py      # .bbmodel x .animation.json
python3 tools/player_anim/test_export.py # proteção do exportador
./gradlew runClient                      # cliente de desenvolvimento
./gradlew runServer                      # servidor dedicado (aceita comandos no console)
```

- Não há JUnit. Regra pura nova entra no `selftest.SelfTest` (sai como `Autoteste TupiniCraft: OK` no smoke test).
- O CI (`.github/workflows/build.yml`) roda `bbsync.py`, `./gradlew build` e o teste de fumaça.
- Teste com clientes reais (`tools/visual-test/helpers.sh`, Xvfb) só quando a mudança afeta render, telas ou
  sincronização multiplayer; um cenário representativo basta. Não repita a validação de etapas anteriores nem rode
  clientes para mudanças só de documentação.
- Em sessões de nuvem o `maven.neoforged.net` ou a Mojang podem estar bloqueados; então valide pelo CI do GitHub.
- Nunca diga que compilou ou testou sem ter rodado o comando.

## GitHub e entregas (política do dono do repositório)

1. Trabalhe numa branch própria (em sessões de nuvem, a branch designada; se o PR anterior dela já foi integrado,
   recomece a partir da `main` atualizada). Commits organizados por assunto.
2. Rode os testes pertinentes e corrija erros antes do PR.
3. Abra o PR para a `main` e aguarde o workflow **Build** (build + teste de fumaça).
4. Com CI verde e sem conflitos, faça o merge (merge commit) — o dono autorizou merge automático. Confirme o merge
   e informe PR, commit de merge e resumo.
5. Nunca faça merge com CI vermelho/pendente, nem contorne proteções (force-push na `main`, desativar checks,
   pular testes). Se bloqueado, explique o motivo.
6. **Release:** o workflow **Release** publica quando `mod_version` muda na `main` (título, notas e pre-release em
   `.github/release-*.txt|md`). Só mude `mod_version` quando a release for pedida. Não altere tags/releases publicadas.

## Documentação complementar

- `README.md` — visão geral, instalação e uso. `AGENTS.md` — resumo para agentes.
- `HANDOFF_GECKOLIB_STAGE1.md` (teste visual), `HANDOFF_GECKOLIB_STAGE2.md` (rig, Body Layers, Animation Manager).
- `HANDOFF_ETAPA3.md` (UI, criador, roleta, skills, necessidades, finalizações) e `HANDOFF_ETAPA3_1.md` (necessidades
  e consumíveis).
- `HANDOFF_PLAYER_ANIMATIONS_STAGE4.md` e `HANDOFF_PLAYER_ANIMATIONS_STAGE4_1.md` (biblioteca de combate, Blockbench,
  sequências), `HANDOFF_DEFEAT_COUNTER_STAGE4_2.md` (contador de derrotas).
- `HANDOFF_LEGACY_CLEANUP.md` — remoção do legado político (Etapa 4.4) e migração da origem antiga.
- Os handoffs são históricos: quando contradizem este arquivo ou o código, vale o código e este arquivo.
