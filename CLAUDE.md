# CLAUDE.md — RespectCraft

RPG de humor para **Minecraft Java 1.21.1 / NeoForge 21.1.x / Java 21**: criador de personagem, roleta de
nascimento, habilidades, necessidades e consumíveis, rig GeckoLib do jogador com animações sincronizadas,
finalizações cinematográficas, contador de derrotas e o mob Leão do Fim. Versão atual: `0.5.7` (`gradle.properties`).

**Nome: RespectCraft**, público e interno (Etapa 4.7.1): mod id `respectcraft`, pacote `com.respectcraft`, classes
`RespectCraft*`, namespace de recursos `respectcraft`, comando `/respectcraft`, configs `respectcraft-*-server.toml` e
jar `respectcraft-<versão>.jar`. O nome antigo do projeto só aparece em handoffs históricos e no histórico do Git; não o
reintroduza em código, recursos, comandos, configs ou documentos ativos (nem como alias). Mundos criados antes da
Etapa 4.7.1 não são compatíveis (decisão do dono).

Leia este arquivo, o `README.md` e só os arquivos do pedido. Handoffs ficam em `docs/handoffs/` (consulte só os do
sistema em que vai mexer; veja o fim deste arquivo).

## Identidade e stack

| Item | Valor |
|---|---|
| Nome público / mod id / pacote | RespectCraft / `respectcraft` (`RespectCraft.MOD_ID`, `RespectCraft.id("caminho")`) / `com.respectcraft` |
| Minecraft / NeoForge | 1.21.1 / 21.1.265 (ModDevGradle, Parchment 2024.11.17), Gradle wrapper 9.2.1 |
| Java | 21 (toolchain no `build.gradle`) |
| GeckoLib | **4.9.3** (`software.bernie.geckolib:geckolib-neoforge-1.21.1`, Cloudsmith) — cliente e servidor. Não use a 5. |
| SmartBrainLib | 1.16.11 (`net.tslat.smartbrainlib:SmartBrainLib-neoforge-1.21.1`, Cloudsmith) — cliente e servidor |
| Rimulib | **1.3.0** NeoForge (Etapa 4.8; sem Maven público: `curse.maven:rimulib-1753907:9676532` pelo CurseMaven) — cliente e servidor, não embutido. Conectado em `RespectCraft.RIMULIB`. Só `cyou.rimuru.rimulib.mc1211.api` no código comum; `...mc1211.client.api` (ex.: `FxApi`) só em `client` |
| UI Lib / Architectury | **Não são dependências.** As telas usam o framework próprio `client.ui`. Não reintroduza. |

- Versões em `gradle.properties`; `neoforge.mods.toml` é gerado de `src/main/templates/`.
- Só APIs do NeoForge **21.1** (nada de Forge 1.20.x, Fabric ou NeoForge 21.2+: `RegisterClientPayloadHandlersEvent`,
  sync automático de attachments, `SavedDataType` etc.). Confira a assinatura 1.21.1.
- Nunca copie classes das bibliotecas para dentro do mod. Sem dependências novas sem pedido explícito.

## Arquitetura (pacotes)

| Pacote | O que é |
|---|---|
| `RespectCraft`, `registry` | Entrada comum: `ModItems`, `ModEntities`, `ModAttachments`, `ModSounds`, `ModCreativeTabs`; config SERVER `respectcraft-needs-server.toml`. |
| `client` | Tudo que importa `net.minecraft.client.*`. Entrada `client.RespectCraftClient` (`@Mod(dist = CLIENT)`). |
| `client.playergeo` | **Rig GeckoLib do jogador.** `PlayerRigLayer` é a primeira camada do `PlayerRenderer` vanilla: lê a pose vanilla, desenha o rig e devolve a pose ao `PlayerModel` (armadura, itens e camadas acompanham). F8 = modo de teste, F9 = aceno. |
| `client.bodylayer` | **Body Layers**: busto, cabelo, pele, rosto e acessórios como `BodyLayerDefinition` em `BodyLayers`. |
| `animation` | **Animation Manager.** Animações são dados (`PlayerAnimations`, `PlayerAnimationDef`, `AnimationSequence`); `PlayerAnimationService` é o único que inicia/para; estado sincronizado `PlayerAnimationState` (attachment). Comando `/respectcraft anim`. |
| `finisher`, `testgame`, `client.cinematic` | **Finisher Engine**: `Finishers` (dados), `FinisherTestMechanic` (sessão no servidor), `CameraTrack` + `CinematicCamera` (câmera dos participantes). Vários rigs (Etapa 4.8): `FinisherParticipants` (tipo, interação, clip por participante), `EntityFinisherClip` (clip no rig GeckoLib do mob), `FinisherSessionEvent`, `BossDefeats`/`BossDefeatEvent` (só preparação). `DefeatCounter` + `client.testgame.DefeatCounterNameplate` (contador 🍆💦). Comando `/respectcraft test` (e `/stopitstopnow`). |
| `character`, `client.character` | **Character Creator** (tecla K → `CharacterHubScreen`): aparência (`CharacterAppearance`, `CharacterAppearanceService`). |
| `profile`, `client.roulette` | **Ficha** (`CharacterProfile`, `ProfileService`), **roleta de nascimento** (`BirthRoulette`, `BirthOrigin`, `LegacyOriginIds`), **traços de nascimento** (`BirthTrait`) e **Specialities** (`Speciality`, `SpecialityRarity`, `SpecialityOdds`, `SpecialityRoll`, `Specialities`, `SpecialityConfig`). |
| `skill`, `client.skilltree` | **Skill Trees**: 3 abas (`SkillBranch`), 18 habilidades em código (`Skills`), compra e modificadores em `SkillService`. |
| `needs`, `client.hud` | **Necessidades** (`Needs`, `NeedsRules`, `NeedsService`) e **consumíveis** (`NeedEffects`/`NeedEffectDef`, `ConsumableItem`, `ChamberPotItem`). Orbe de Emoção na tela; painel Body & Traits no inventário. |
| `network` | Payloads (`GameplayNetworking`, `TestGameNetworking`) e caches de cliente. |
| `entity`, `client.render`, `command` | Mob **Leão do Fim** (GeckoLib + SmartBrainLib), renderer e `/leaodofim`. `RespectCraftCommands` = árvore única `/respectcraft` (inclui os ramos `anim` e `test`). |
| `selftest` | `SelfTest`: regras puras, rodado por `/respectcraft selftest` (teste de fumaça). |
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
- **Modificadores de atributo** do mod são transitórios (`respectcraft:stat/...`) e recolocados por
  `SkillService.refreshModifiers`; nunca grave no save.
- **Roleta:** sorteia Speciality, atributos e traços no servidor (as 8 origens com bônus de `BirthOrigin` são
  legadas); no máximo **1** novo giro (`CharacterProfile.canReroll`), que zera habilidades, devolve os pontos e
  sorteia Speciality, atributos e traços de novo. Continuar envia `BirthConfirmRequest` e descarta o novo giro restante (`birthConfirmed`). A tela só
  apresenta o resultado. Todo `BirthRollRequest` (com o `rollCount` esperado) recebe um `BirthRollReply`; nunca
  descarte pedido em silêncio.
- **Três conceitos separados:** **atributos** (`CharacterAttribute`: Força, Agilidade, Vigor, Inteligência,
  Carisma, Sorte — vêm do sorteio; fichas antigas incluem o bônus da origem legada), **necessidades** (`Need`, medidores que mudam no jogo) e
  **traços de nascimento** (`BirthTrait`, sorteados à parte, nunca derivados da origem; gravados em
  `birth_traits`). Traço sem efeito aprovado fica com `effects` vazio (sem bônus oculto); efeitos de traços usam
  `SkillEffect` e são aplicados por `SkillService` como os de habilidades.
- **Elegibilidade de traços (Etapa 4.6):** `BirthTrait.Eligibility` = `ALL` (High Endurance, Limp, Fertile, Infertile,
  Perfectly Average) ou `MALE_ONLY` (Heavy Loader, Loadless, Hung, Tiny Dick). O servidor sorteia com o corpo da
  aparência salva (`ProfileService.body`). Elegibilidade vale só para sorteios novos: traço gravado nunca é apagado.
  Trocar o corpo com resultado **não confirmado** troca o traço inelegível por um de mesma polaridade,
  determinístico (`CharacterProfile.withTraitsCoherentWith`), sem sortear, sem mexer no novo giro. Fertile/Infertile
  não têm efeito; **não há gravidez** (não implemente sem pedido).
- **Specialities (Etapa 4.7):** a roleta sorteia uma das **15** `Speciality` no lugar das origens sociais. Raridade
  primeiro (`SpecialityRarity`: Common 41,5 · Uncommon 25 · Rare 20 · Epic 10 · Legendary 3 · BONER 0,5%; BONER é a
  mais alta), depois uma Speciality elegível dela (`SpecialityRoll`); chances exatas em milésimos (`SpecialityOdds`,
  config SERVER `respectcraft-speciality-server.toml`); raridade sem Speciality dá `NO_SPECIALITY_IN_RARITY`, **nunca
  redistribui**. Ficha grava `birth_speciality` e `current_speciality` (hoje iguais; a troca no fim de jogo não existe
  ainda). Specialities **não têm perks, modificadores nem efeitos** (virão nas Skill Trees) e não dão bônus de atributo.
  Fichas antigas mantêm a origem social (`BirthOrigin`, só exibição/legado) e **nunca** recebem Speciality ao carregar;
  só um novo giro troca a origem por uma Speciality. Raridade forçada só por `/respectcraft admin roulette <player> <rarity>`.
  Não invente Specialities, não mude nomes/raridades e não implemente Rage Mode nem gravidez.
- **Roda da roleta (Etapa 4.7.1):** seis fatias **iguais** (`RouletteLayout`, só visual; o tamanho não é a chance).
  As chances reais vão do servidor ao cliente (`GameplayNetworking.SpecialityOddsSync`: login, antes de cada
  `BirthRollReply` e quando a config recarrega) e a tela as escreve em cada fatia, na dica da roda e no selo do
  resultado (`ProfileClientCache.odds()`). O cliente nunca envia chances. A roda sempre para na raridade da ficha.
- **Rig/render:** não cancele `RenderPlayerEvent.Pre` nem desenhe o jogador fora do renderer vanilla. Em
  `PlayerRigModel.compose` mantenha o `GeoBone::resetStateChanges` no fim. Peça de corpo nova = `BodyLayerDefinition`
  (não crie `RenderLayer` por peça). Pele/rosto do criador são Body Layers sobre a skin da conta; não troque a textura.
- **Cenas de dois participantes:** um clip `FULL_BODY` por participante, mesmo comprimento, iniciados por
  `playTogether`; posição no mundo é da sessão, keyframes só com deslocamentos que voltam a zero.
- **Vários rigs (Etapa 4.8):** cada participante resolve o próprio clip (`FinisherParticipants.resolve`): jogador →
  `PlayerAnimationDef`; mob/chefe → `EntityFinisherClip` (controlador e animação gatilho do **próprio** rig,
  `triggerAnim` no mesmo tick) ou parado. Nunca um controlador universal nem ossos de jogador em mob. Toda sessão
  precisa de um jogador (mob × mob é recusado). Mob é segurado sem gravar nada na entidade (sem `NoAI`). Não invente
  clips de mob: só registre `EntityFinisherClip` quando o asset existir. Chefe = tag `c:bosses`; `BossDefeatEvent`
  sai no máximo uma vez por chefe e **ninguém concede recompensa** até as regras serem pedidas.
- **Câmera cinematográfica** segue o estado de animação sincronizado e restaura a perspectiva em qualquer saída.
- **Contador de derrotas:** +1 para o alvo **jogador** só em `FinisherTestMechanic.complete` (`abort` nunca conta;
  `FinisherRules.countsDefeat`: mob e chefe nunca têm contador, estatística ou plaqueta). A plaqueta
  replica a matemática do `renderNameTag` 1.21.1; ícones são a fonte bitmap `respectcraft:defeat_icons`, não emoji.
- **Necessidades:** ativas são Coom, Bexiga, Intestino e Emoção (id `humor`) — `Need.ACTIVE`. Hidratação e Energia
  são **dados legados inativos** (`Need.active() == false`): continuam no Codec, mas não mudam, não afetam a Emoção,
  não penalizam e não aparecem. Não as reative sem pedido. Medidores detalhados só no painel do inventário e no
  menu K; na tela de jogo, só o orbe de Emoção. Consumível novo = entrada em `NeedEffects`.
- **Telas** herdam `client.ui.RespectScreen` e usam os widgets de `client.ui` (texturas próprias, sem blur vanilla).
  Texto nunca passa da coluna/painel: quebre ou encurte com `Ui.wrap`/`Ui.fit` e ofereça a dica com o texto inteiro
  (Etapa 4.7.2). Confira GUI 2, GUI 3 e a menor GUI (320×240).
- **Textos visíveis ao jogador em inglês** (Etapa 4.6) via `Component`/lang: `en_us.json` é o único arquivo de idioma
  (não recrie `pt_br.json`; outros idiomas caem no inglês). Mensagens de comando/chat também em inglês. Código,
  comentários, docs e ids internos seguem como estão.
- **Mobs:** recursos `geo/entity/<nome>.geo.json`, `animations/entity/<nome>.animation.json`,
  `textures/entity/<nome>.png`; GeckoLib só anima, dano/alvo/IA no servidor. Registre em `ModEntities` + atributos,
  renderer em `client.render` registrado em `RespectCraftClient`.
- **Tema:** o mod não tem mais eleições, política, partidos, mandatos, urna, economia ou símbolos nacionais. Não
  reintroduza (nem a partir do histórico do Git) sem pedido explícito.

## IDs persistentes e compatibilidade de saves (não renomear)

Válidos desde a Etapa 4.7.1, que trocou o namespace inteiro para `respectcraft` e quebrou de propósito a
compatibilidade com mundos, saves e configs anteriores. A partir dela, estes ids voltam a ser estáveis.

| Dado | Onde fica |
|---|---|
| `respectcraft:character_profile`, `respectcraft:needs`, `respectcraft:need_effects`, `respectcraft:player_animation` | attachments (`ModAttachments`); nomes de campos dos Codecs também são estáveis (incl. `birth_traits`, `birth_speciality`, `current_speciality` e os legados `hidratacao`/`energia`) |
| `respectcraft_character_appearance_v1`, `respectcraft_special_defeats`, `respectcraft_allow_mob_finishers_test` | `persistentData` do jogador |
| `respectcraft_boss_finisher_credit`, `respectcraft_boss_defeat_posted` | `persistentData` de entidades chefe (Etapa 4.8) |
| ids de `BirthOrigin`, `Speciality`, `BirthTrait`, `CharacterAttribute`, `Need`, `Skills` (`combate/...`), finalizações e animações | gravados na ficha/estado |
| itens, `respectcraft:leao_do_fim` | registries |

- Id de origem aposentado: adicione o alias em `profile.LegacyOriginIds` (lido pelo Codec da ficha e regravado com o
  id novo) e um caso no `SelfTest`. Nunca converta um perfil inválido em "personagem não criado".
- Remover item/entidade registrado apaga essas instâncias em mundos antigos: avise no PR e no handoff.
- Recomende sempre backup dos mundos antes de atualizar.

## Blockbench e animações do jogador

- Projetos `.bbmodel` do jogador em `model_sources/player/` (`respectcraft_animated_player`, `respectcraft_player_combat`,
  `respectcraft_combo_scene` — este só para ajustar o tempo da cena). Leão do Fim: `model_sources/leao_do_fim.bbmodel`
  (`tools/export_leao.py`, `tools/validate_leao.py`).
- `tools/player_anim/export.py` gera a biblioteca de combate (`assets/respectcraft/animations/player/
  respectcraft_player_combat.animation.json`, `PlayerAnimations.COMBAT_FILE`) e os `.bbmodel` a partir de
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
./gradlew build                          # compila e empacota (build/libs/respectcraft-<versão>.jar)
bash .github/scripts/server-smoke-test.sh # servidor dedicado: Leão do Fim, /respectcraft selftest, catálogos
python3 tools/player_anim/bbsync.py      # .bbmodel x .animation.json
python3 tools/player_anim/test_export.py # proteção do exportador
./gradlew runClient                      # cliente de desenvolvimento
./gradlew runServer                      # servidor dedicado (aceita comandos no console)
```

- Não há JUnit. Regra pura nova entra no `selftest.SelfTest` (sai como `RespectCraft self-test: OK` no smoke test).
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
- Handoffs das etapas em [`docs/handoffs/`](docs/handoffs/README.md): o índice diz etapa, sistema, status e
  limitações. **Abra só o handoff do sistema que a tarefa toca** (nunca todos): rig/Body Layers → Etapa 2; telas,
  criador, roleta, skills, necessidades, finalizações → Etapas 3/3.1; animações e Blockbench → Etapas 4/4.1;
  contador de derrotas → 4.2; Specialities → 4.7; finalizações com vários rigs, Rimulib e recompensas de chefe
  (preparação) → 4.8; legado, saves e ids aposentados → 4.4; marca RespectCraft e protocolo da
  roleta → 4.5; criador limpo, traços de nascimento e Hidratação/Energia inativas → 4.5 revisada; traços por corpo
  (universais × só masculinos) e textos em inglês → 4.6; rebrand técnico (mod id/pacote/comandos/configs) e roda da
  roleta com chances do servidor → 4.7.1; layout de texto do Hub e do painel Body & Traits → 4.7.2.
- Os handoffs são históricos: quando contradizem este arquivo ou o código, vale o código e este arquivo.
