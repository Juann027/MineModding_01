# CLAUDE.md — RespectCraft

RPG de humor para **Minecraft Java 1.21.1 / NeoForge 21.1.x / Java 21**: criador de personagem, roleta de
nascimento, habilidades, necessidades e consumíveis, rig GeckoLib do jogador com animações sincronizadas,
finalizações cinematográficas que **executam** o alvo de verdade (Etapa 4.10.1; efeitos visuais pelo motor
**Calamidade_Awakening**, Etapa 4.10), contador de derrotas, infraestrutura de chefes personalizados (Etapa 4.11),
avisos holográficos automáticos e caça a recompensa entre jogadores (Etapa 4.11), o Custom Rig Framework com o kit
Blockbench (Etapa 4.12), o Finisher Scene Authoring Kit (Etapa 4.13.2), o morango de gameplay com cinco
finalizações exclusivas e música própria (Etapa 4.13.3) e
(Etapa 4.9, MVP) a dimensão **Inverted Realm**
(céu roxo, Madeira Retorcida, agua-vivas). Versão atual: `0.5.7` (`gradle.properties`).

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
| SmartBrainLib | 1.16.11 (`net.tslat.smartbrainlib:SmartBrainLib-neoforge-1.21.1`, Cloudsmith) — cliente e servidor. Sem uso no código desde a remoção do Leão do Fim (4.9.1); continua declarada até o dono decidir |
| Rimulib | **1.3.0** NeoForge (Etapa 4.8; sem Maven público: `curse.maven:rimulib-1753907:9676532` pelo CurseMaven) — cliente e servidor, não embutido. Conectado em `RespectCraft.RIMULIB`. Só `cyou.rimuru.rimulib.mc1211.api` no código comum; `...mc1211.client.api` (ex.: `FxApi`) só em `client` |
| Calamidade_Awakening | **0.14.0** (Etapa 4.10, obrigatório; aceita `[0.14.0,0.15.0)`: a API `api.v1` da 0.14 é a da 0.10 + adições, compila sem mudança de código; NeoForge 21.1.265 e RimuLib 1.3.0 atendem ao que ela pede; `calamidade_fx.glsl` copiado da 0.14.0, versão 3). Sem Maven: a tarefa `baixarCalamidade` baixa o jar do release do GitHub para `libs/` (fora do git) e confere o SHA-256 (`gradle.properties`); `compileOnly` + `runtimeOnly`, nunca embutido. A RimuLib dele é a do CurseMaven acima (não baixe de novo). Servidor e clientes na **mesma versão**. Só `dev.calamidade_awakening.api.v1` no código comum; `api.v1.client` (`AddonLayers`) só em `client` |
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
| `finisher`, `testgame`, `client.cinematic` | **Finisher Engine**: `Finishers` (dados), `FinisherTestMechanic` (sessão no servidor), `CameraTrack` + `CinematicCamera` (câmera dos participantes). Vários rigs (Etapa 4.8): `FinisherParticipants` (tipo, interação, clip por participante), `EntityFinisherClip` (clip no rig GeckoLib do mob), `FinisherSessionEvent`, `BossDefeats`/`BossDefeatEvent` (só preparação). `DefeatCounter` + `client.testgame.DefeatCounterNameplate` (contador 🍆💦). Execuções reais (Etapa 4.10.1): `FinisherTargeting` (quem cada finalização pode executar; chefes só pela exclusiva) e `FinisherExecution` (golpe final = dano comum `respectcraft:finisher_execution`, resultado `SUCCESS`/`CANCELLED`/`FAILED`). Política de seleção por categoria de vítima `FinisherSelection` e pools de NPC `FruitFinishers` (Etapa 4.13.3). Comandos `/respectcraft test` (e `/stopitstopnow`), `/respectcraft finishers <alvo>` (elegibilidade). |
| `fx`, `client.fx` | **Ponte com o Calamidade_Awakening** (Etapa 4.10). `FinisherFx` é o **único dono** dos efeitos de finalização (registro no construtor do mod, HERO da cena, golpes/quedas nos marcadores, fim/abort); dados em `FinisherFxDef` (campo opcional `FinisherDef.fx()`), regras puras em `FinisherFxRules` (no `SelfTest`). Gancho de mobs `GeoFxKeyframes` (keyframe `calamidade:<efeito>`, lido no servidor). Passes de pós e shaders só em `client.fx.FinisherFxClient` + `assets/respectcraft/shaders/fx/finisher_*.fsh` (com a cópia de `calamidade_fx.glsl`). |
| `character`, `client.character` | **Character Creator** (primeira vez na tecla K): aparência (`CharacterAppearance`, `CharacterAppearanceService`), só Body, Hairstyle e Hair Color (Etapa 4.8.4-C); rascunho sem desenho `AppearanceDraft`. |
| `client.holo`, `ui` | **Menu holográfico da tecla K** (Etapas 4.8.1, 4.8.4-A, 4.8.4-B e 4.8.4-C). Criador e roleta são **janelas** do mesmo arco (`HoloPopup`: `CreatorView`, `RouletteView`); roleta sem desenho em `ui.RouletteMachine` (pura, no `SelfTest`) + `client.roulette.RouletteSession`; desenho da roda só em `client.roulette.PrizeWheel` (janela e tela de reserva). Núcleo `HoloMenu` (arco das seções Overview, Character, Body & Traits, Speciality, Skills, superfícies por painel, passada 3D `Holo3D`, `press` comum de clique) com duas cascas: **`HoloWorld`** (padrão: placas paradas no mundo, `RenderFrameEvent.Pre` + `RenderLevelStageEvent` AFTER_LEVEL, K/Recall, posição sem paredes `ui.HoloPlacement`, mira/clique/arrasto/roda/dica) e `HoloCharacterScreen` (tela da 4.8.1, reserva temporária `mode = "SCREEN"`). Entrada no mundo: **`HoloWorldInput`** (dono por aperto com `ui.HoloInputArbiter`, modo cursor). Geometria pura em `ui.HoloGeometry` (no `SelfTest`); config de cliente `respectcraft-holomenu-client.toml`. |
| `profile`, `client.roulette` | **Ficha** (`CharacterProfile`, `ProfileService`), **roleta de nascimento** (`BirthRoulette`, `BirthOrigin`, `LegacyOriginIds`), **traços de nascimento** (`BirthTrait`) e **Specialities** (`Speciality`, `SpecialityRarity`, `SpecialityOdds`, `SpecialityRoll`, `Specialities`, `SpecialityConfig`). |
| `skill` | **Skill Trees**: 3 abas (`SkillBranch`), 18 habilidades em código (`Skills`), compra e modificadores em `SkillService`; a árvore é a seção Skills do menu K (`client.holo.SkillsView`). |
| `needs`, `client.hud` | **Necessidades** (`Needs`, `NeedsRules`, `NeedsService`) e **consumíveis** (`NeedEffects`/`NeedEffectDef`, `ConsumableItem`, `ChamberPotItem`). Orbe de Emoção na tela; painel Body & Traits no inventário. |
| `network` | Payloads (`GameplayNetworking`, `TestGameNetworking`) e caches de cliente. |
| `entity`, `client.render`, `command` | Mobs **agua-vivas** (`AguaVivaEntity`, Etapa 4.9) e renderers. O Leão do Fim foi removido na Etapa 4.9.1 (não o reintroduza). `RespectCraftCommands` = árvore única `/respectcraft` (inclui os ramos `anim`, `test` e `realm`). |
| `dimension`, `client.dimension` | **Inverted Realm** (Etapa 4.9, MVP): dimensão, bioma e features são **dados** (`data/respectcraft/dimension*`, `worldgen/...`); `InvertedRealm` (chave, viagem de teste, chão seguro) e `InvertedRealmCommands` (`/respectcraft realm`); céu/neblina/luz em `client.dimension.InvertedRealmEffects`. |
| `block.wood`, `registry.ModBlocks` | **Madeira Retorcida** (Etapa 4.9, do pacote "O Avesso — Fase 2, Etapa A"): blocos em `ModBlocks`, itens em `ModItems`, placas/bancada em `TwistedFurniture`, barco por `enumextensions.json`; placas no cliente em `client.render.TwistedWoodRenderers`. |
| `boss` | **Chefes personalizados** (Etapa 4.11): `Bosses` (registro em código, **vazio**), `BossProfile` (tipo, exclusivas, condições), `BossCondition` (`HealthBelow`, `RequiresSkill`, `RequiresProgress`), `BossExecutionCheckEvent` (só recusa). Avaliado em `FinisherTestMechanic.eligibility` antes da cena. |
| `notice`, `client.holo.HoloToasts` | **Avisos holográficos automáticos** (Etapa 4.11). `HoloNotices` é o **único** caminho de um aviso (audiência, `HoloNoticeEvent` cancelável, pacote, farol); dados `HoloNotice` (`NoticeKind`, `NoticeSeverity`), regras puras `NoticeRules` (no `SelfTest`); faróis no Calamidade em `NoticeFx` (único dono). Fontes de hoje: chefe que aparece, chefe derrotado, portal do Nether aceso, chegada ao Inverted Realm, caça a recompensa; API pronta para chefes/portais/eventos futuros (`bossAppeared`, `portalOpened`, `worldEvent`). Cliente: `network.NoticeClientCache` (fila + histórico), cartões na tela `HoloToasts`, seção Alerts `client.holo.NoticesView`. Comando `/respectcraft notice`. |
| `bounty` | **Caça a recompensa** contra jogadores (Etapa 4.11). `BountyService` é o único dono do quadro `BountyBoard` (SavedData do Overworld); contrato `Bounty`; regras puras `BountyRules` (no `SelfTest`); aba Bounty Hunt `client.holo.BountyView` + cache `network.BountyClientCache`; pacotes em `network.HoloNetworking`; comando `/respectcraft bounty`. |
| `rig`, `client.rig` | **Custom Rig Framework** (Etapa 4.12). Famílias de rig = manifesto `assets/<ns>/rigs/<id>.rig.json` (`RigDefinition`); entidade → família = `rigs/entities/<entidade>.json` (`RigBinding`); catálogo `CustomRigRegistry.GLOBAL` preenchido por `Rigs.bootstrap()` (os dois lados). Clips por família e requisitos de participante em `FinisherDef.rigs()` (`finisher.FinisherRigs`); elegibilidade única `finisher.FinisherEligibility` (+ `FinisherTestMechanic.check`). Partes (ocultar/expor) só por `RigVisuals` (attachment sem save, pacote `network.RigNetworking`); sockets em pose de repouso `RigSockets`. Cliente: `RigGeoModel`/`RigGeoRenderer` genéricos. Base de mob `entity.rig.CustomRigMob`; fixtures `entity.dev.RigMannequinEntity` (`dev_rig_mannequin_a/b`); morango `entity.fruit.StrawberryEntity` (4.13.1) sobre a base de frutas `entity.fruit.FruitMob` (4.13.3: IA neutra, música, spawn; `FruitCommands` = `/respectcraft fruit`; som no cliente `client.fruit.FruitMusicClient`). Comando `/respectcraft rig`. Kit externo em `rigkit/` + `tools/rigkit/`. |
| `scenekit/`, `tools/scenekit` | **Finisher Scene Authoring Kit** (Etapa 4.13.2, ferramenta externa, nada roda no jogo): projeto conjunto do Blockbench com ATTACKER (jogador, `ATK_`) e VICTIM (CustomRig ou jogador, `VIC_`) no palco do Finisher Engine (`compose_scene.py`), separação em um clip por participante + relatório de eventos (`split_scene.py`), templates/exemplos gerados por `make_templates.py` (protegido por hash). Contrato em `scenekit/CONTRACT.md`. Cena de teste `rig.Rigs.AUTHORING_DEMO` (`respectcraft:dev/authoring_demo`, fixture: só manequim B, `/respectcraft rig demo <alvo> authoring`). |
| `selftest` | `SelfTest` (+ `RigSelfTest`): regras puras, rodado por `/respectcraft selftest` (teste de fumaça). |
| `biome`, `npc` | Só `LEIA-ME.md`: sem código (biomas são dados desde a 4.9). |

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
  apresenta o resultado. Todo `BirthRollRequest` (com o `rollCount` esperado e um `requestId`) recebe um
  `BirthRollReply` com o mesmo `requestId`; nunca descarte pedido em silêncio. Exceção no servidor vira resposta
  `ERROR` (nada gravado) ou `ROLLED` (já gravado) com log do `requestId`, sem a ficha inteira (Etapa 4.8.2).
  Gerador dos sorteios: só `BirthRoulette.newRandom()` (`SplittableRandom`, módulo `java.base`). **Nunca**
  `RandomGenerator.of("...")` com algoritmo nomeado: exige o módulo opcional `jdk.random`, ausente em alguns runtimes
  de launcher (causa confirmada do ERROR/timeout da roleta).
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
  (não crie `RenderLayer` por peça). Não troque a textura da skin da conta. **Skin e Face saíram do criador
  (Etapa 4.8.4-C):** o servidor grava sempre pele/rosto "conta" (`withAccountSkinAndFace`) e
  `customSkinTone()/customFace()` são sempre falsos (valores antigos continuam válidos no pacote, só não aparecem).
  Não reintroduza essas opções sem pedido.
- **Cenas de dois participantes:** um clip `FULL_BODY` por participante, mesmo comprimento, iniciados por
  `playTogether`; posição no mundo é da sessão, keyframes só com deslocamentos que voltam a zero.
- **Vários rigs (Etapa 4.8):** cada participante resolve o próprio clip (`FinisherParticipants.resolve`): jogador →
  `PlayerAnimationDef`; mob/chefe → `EntityFinisherClip` (controlador e animação gatilho do **próprio** rig,
  `triggerAnim` no mesmo tick) ou parado. Nunca um controlador universal nem ossos de jogador em mob. Toda sessão
  precisa de um jogador (mob × mob é recusado). Mob é segurado sem gravar nada na entidade (sem `NoAI`). Não invente
  clips de mob: só registre `EntityFinisherClip` quando o asset existir. Chefe personalizado (Etapa 4.11) = ficha em
  `boss.Bosses` (exclusivas + condições); não registre chefe sem aprovação do dono. Chefe = tag `c:bosses` ou `respectcraft:bosses`;
  `BossDefeatEvent` sai no máximo uma vez por chefe e **ninguém concede recompensa** até as regras serem pedidas.
- **Custom Rig Framework (Etapa 4.12):** compatibilidade de rig é **explícita** (binding declarado + validador);
  nomes de bones iguais nunca bastam, sem retargeting. Resolução do clip de um mob: `entityClips` da finalização (tipo)
  → `clip_overrides` do binding → clip da família (`FinisherRigs.Clip`) → parado; requisito `requiresClip` recusa o
  parado **antes** da cena (com a mensagem do motivo). Requisitos por tipo, tag, família, capacidades e papel em
  `FinisherRigs.Requirement`; o sorteio só oferece o que `FinisherEligibility` aceita. Rig **não** é classificação de
  gameplay: a regra de chefes (tags) vale igual para entidades com rig. Partes só por `RigVisuals` (instruções de
  timeline `rig:hide:<parte>`, `rig:show:<parte>`, `rig:reset`, agendadas pelo relógio da sessão), snapshot no início e
  restauração em `release` (concluída, cancelada, golpe recusado); nunca gravadas no save. Sockets no servidor só em
  pose de repouso (efeitos/alinhamento, nunca dano); pose animada só no cliente. Observador que começa a rastrear um
  participante no meio da cena recebe o gatilho GeckoLib do clip (`FinisherTestMechanic.onStartTracking`). Manifestos
  ficam no jar (lidos por `getResourceAsStream`, como `GeoFxKeyframes`); família/binding nova = manifesto + uma linha em
  `Rigs.bootstrap()`. Os manequins `dev_rig_mannequin_a/b` e a cena `respectcraft:dev/rig_peel_demo` são **fixtures**
  (sem spawn natural, ovo, drop ou progressão; a cena fica fora do catálogo). Não crie frutas, texturas/animações
  artísticas nem finalizações exclusivas delas sem pedido. Assets da família/fixtures são gerados por
  `tools/rigkit/make_assets.py` (protegido por hash, como o exportador do jogador).
- **Morango (Etapa 4.13.1):** `respectcraft:strawberry` (`entity.fruit.StrawberryEntity`, base comum
  `entity.rig.CustomRigMob`, a mesma dos manequins) é o primeiro mob de gameplay da família, feito **fora** do
  repositório: `geo/entity/strawberry.geo.json`, `textures/entity/strawberry.png` e o binding
  `rigs/entities/strawberry.json` são os arquivos do artista **sem regeneração** (fonte em
  `model_sources/rig/strawberry/`, com `strawberry_visual_states.md`); não os passe pelo `make_assets.py`. No binding só
  `animation_file` + `clip_overrides` foram acrescentados (4.13.3, pedido do dono). Papel: **vítima** (nunca executor).
  **Etapa 4.13.3:** NPC neutro (`FruitMob`: passeia, olha, revida quem o fere com 2 de dano, larga o alvo perdido; golpes
  desligados durante uma cena), 12 HP, 3–5 XP, sem drops; spawn natural **só** em `minecraft:plains` e
  `minecraft:savanna` (biome modifier `neoforge/biome_modifier/strawberry_spawns.json`, peso 5, grupo 1–2, CREATURE);
  nascidos pelo mundo somem longe dos jogadores, ovo/`/summon` ficam persistentes. Cinco finalizações exclusivas
  (`FruitFinishers`: `strawberry_crusher`, `fruit_punch`, `ground_smasher`, `shut_up`, `strawberry_blender`); clips do
  jogador em `animations/player/respectcraft_authored_scenes.animation.json` e do morango em
  `animations/entity/strawberry.animation.json`, gerados **só** por `tools/scenekit/strawberry_finishers.py --install`
  a partir de `model_sources/rig/strawberry/finishers/` (conferido no CI com `--check`; não edite os clips à mão).
  Música `respectcraft:entity.strawberry.music` (OGG mono de `model_sources/sounds/strawberry_music.mp3`), tocada só no
  cliente por `FruitMusicClient` (loop preso à entidade, uma instância por fruta, limite/volume/desligar em
  `respectcraft-fruits-client.toml`); o servidor só liga o corte seco (`FruitMob.setMusicSilenced`, dado sincronizado,
  nunca salvo) no `Finishers.EVENT_SILENCE` da cena e o devolve no cancelamento/fruta viva. Fruta nova = subclasse de
  `FruitMob` + binding + som + pool, sem sistema paralelo. **Reprodução entre frutas** é decisão futura (roadmap): não
  implemente. Expressões são partes do binding: trocar de
  expressão = esconder `face_normal` + mostrar um olho, `brows_panic` e uma boca (exclusões e estados no
  `strawberry_visual_states.md`, conferidos no `RigSelfTest`); no meio de uma cena de destruição troque por
  `hide/show` explícitos — `rig:reset` devolve **todas** as partes (casca, folhas) ao padrão. `shell` cobre cabeça e
  torso juntos (descascar só um exige parte nova no contrato da família; não duplique bones em partes do binding).
- **Finisher Scene Authoring Kit (Etapa 4.13.2):** projeto conjunto só para autoria; a geometria conjunta nunca vai
  para o jogo e os modelos de origem só são lidos. Palco = referencial do executor (jogador: 1 bloco = 17,0667 px pelo
  15/16 do `PlayerRenderer`; CustomRig: 16/`render_scale`), distância como `FinisherParticipants.stageDistance`;
  `STAGE_*` são grupos de palco (`export: false`; `STAGE_VICTIM_SCALE` tem um keyframe constante de calibração) e
  animá-los é `ERROR` (a sessão não move entidades). O split preserva os keyframes do *Export Animations* do plugin
  (sem segunda conversão de eixos; `--bbmodel` só para lineares/easing, o resto vira `ERROR`), nunca descarta nada em
  silêncio e exige dono em cada linha da timeline (`@attacker`/`@victim`/`@scene`; eventos = os que o engine já
  entende: `hit`, `fall`, `shake`, `fov`, `sound`, `camera`, `execute` só no fim, `rig:`/`calamidade:` só em CustomRig).
  Eventos de jogador viram linhas Java no relatório (não são keyframes). Clips de jogador vão para arquivos próprios
  (`respectcraft_dev_scenes`/`respectcraft_authored_scenes`), nunca para a biblioteca de combate; `--install` recusa
  arquivos de outros geradores, fontes e arquivos editados fora do kit. Não regenere o binding/geo/png do morango:
  dar um clip ao morango (`animation_file` + `clip_overrides`) é decisão do dono.
- **Política de seleção de finalizações (Etapa 4.13.3, permanente):** `finisher.FinisherSelection` (regra pura, usada
  por `Finishers.pickFor`), separada da compatibilidade técnica (`FinisherTargeting`/`FinisherEligibility`). **PLAYER:** só
  comuns (`FinisherTargeting.Category.GENERIC`) com desbloqueio; consentimento/PvP/times como antes; o futuro seletor
  Random/Preset/Manual (roadmap 4) escolhe dentro delas — não implemente o seletor sem pedido. **BOSS:** só a exclusiva
  do chefe, nunca fallback. **MOB/NPC:** o servidor sorteia a cena completa; tipo com pool próprio
  (`FinisherTargeting.npcExclusive(tipos)`) usa só ele, sem desbloqueio pessoal; sem pool, as comuns compatíveis com mob;
  nada elegível = recusa antes de mexer no mundo. Exclusivas de NPC nunca contra jogador, chefe ou outro tipo e nunca no
  loadout de PvP (`FinisherSelection.pvpLoadoutAllowed`). Cena só PvE pode ter `FinisherDef.victim() == null` (sem clip
  PvP falso; só exclusiva de NPC) e eventos da vítima entidade em `entityVictimEvents` (quedas, `EVENT_SILENCE`),
  disparados pelo relógio da sessão. Suco/sementes/lascas de fruta saem do `FinisherFx` (partículas vanilla com a cor de
  `FruitMob.juice()`), sem framework de fluidos paralelo.
- **Câmera cinematográfica** segue o estado de animação sincronizado e restaura a perspectiva em qualquer saída.
- **Regra permanente de chefes (Etapa 4.10.1):** chefes podem ser derrotados normalmente (dano comum, armas,
  habilidades; nunca imunes à morte), mas **só a finalização exclusiva designada para aquele chefe pode executá-lo pelo
  Finisher Engine. Finalizações comuns nunca executam chefes.** Regra em `FinisherTargeting` (campo
  `FinisherDef.targeting()`, padrão `REGULAR`; `exclusiveTo(<tipo de entidade do chefe>)` só para o chefe daquele tipo e
  nenhum outro alvo). A recusa é no servidor, em `FinisherTestMechanic.start`, **antes** de palco, animação, trava,
  efeito ou dano — vale para o clique, o convite e o `/respectcraft admin finisher`. Chefe = `FinisherParticipants.Kind.BOSS`:
  tag `c:bosses` **ou** `respectcraft:bosses` (dados; chefes futuros do mod e de outros mods sem `c:bosses` entram aí),
  partes (`PartEntity`, ex.: Dragão do Fim) contam como a dona; nunca por vida, tamanho, nome ou barra. O sorteio
  (`Finishers.pickFor`) só oferece o que pode executar o alvo; exclusiva nunca é sorteada contra mob/jogador. **Nenhum
  chefe do RespectCraft nem finalização exclusiva existe ainda:** não crie chefes, ids, animações ou exclusivas
  provisórias, não use Wither/Dragão como substitutos permanentes e não registre fixtures (as do `SelfTest` ficam fora do
  catálogo). Desbloqueio por progressão (conquistas, Skill Trees), recompensas de execução e invocação de chefes são de
  etapas futuras (plano no handoff 4.10.1).
- **Execuções reais (Etapa 4.10.1):** a cena concluída termina com **um** golpe final (`FinisherExecution.execute`, em
  `FinisherTestMechanic.complete`, depois de `release` e do `FinisherFx.end`): `LivingEntity.hurt` com o tipo de dano
  `respectcraft:finisher_execution` causado pelo executor — morte normal do Minecraft (eventos, matador, loot e XP de
  morte por jogador, estatísticas, conquistas, mensagem de morte), respeitando totem, invulnerabilidade/criativo, PvP do
  servidor/times e mods de proteção (`AttackEntityEvent`). Nunca `discard()` nem vida forçada a zero. `SUCCESS` só com o
  `LivingDeathEvent` da execução confirmado (LOWEST, não cancelado); dano recusado/vítima viva = `FAILED` (sem segunda
  tentativa); qualquer interrupção = `CANCELLED` (sem golpe final). `FinisherSessionEvent.getOutcome()` carrega isso.
  PvP desligado/times recusam o convite e o início. A sessão sai do mapa antes do golpe (idempotente). Jogador vítima
  também morre (com consentimento do convite, como antes); se sobreviver, volta a recuperação caída de antes.
  `BossDefeatEvent.Method.FINISHER` só para morte confirmada pelo golpe final de uma exclusiva do próprio chefe
  (crédito em `persistentData` só durante o golpe).
- **Servidor parando (Etapa 4.10.2):** o `/stop` desconecta sem `PlayerLoggedOutEvent`; por isso
  `FinisherTestMechanic.onServerStopping` encerra toda sessão ativa por `abort` (`CANCELLED (server stopping)`, nunca
  executa) e limpa convites, recuperações e cooldowns. Estado novo de finalização em `static` precisa ser limpo ali também.
- **Efeitos de finalização (Etapa 4.10, Calamidade_Awakening):** só `fx.FinisherFx` começa/encerra efeitos de
  finalização (não chame `Effects.start` para isso em outro lugar). `FinisherDef` sem `FinisherFxDef` = partículas
  vanilla de antes (sons ficam no `FinisherTestMechanic` nos dois casos). Por sessão: um efeito **HERO** da cena
  (timeline do tamanho da cena, no ponto da cena, `arrivingFrom` executor→vítima ou yaw da âncora); golpes (`EVENT_HIT`
  1/2/3 → luz; + anel no chão a partir de 2; + impact frame só no 3) e quedas (`EVENT_FALL` → poeira e luz no chão)
  nos **marcadores** da cena, que são os ticks dos eventos das animações; contra chefe (`c:bosses`), atmosfera. Fonte =
  `EffectSource.player` do jogador participante (executor, senão vítima). Encerramento em `abort` (stop) e `complete`
  (release, que cai para stop sem sustain): nada da cena fica ativo. **Nunca** `film`, conjurador nem `post.shake`
  (câmera e tremor são da `CinematicCamera`). Sem ossos do GeckoLib: pontos pela âncora + deslocamentos no referencial
  orientado. Passes próprios multiplicam por `post.screenEffects()`, não piscam sem `flashAllowed()` e não se mexem sem
  `motionEffects()` (`FinisherFxRules.screenStrength`/`impactFrameInverts`). Estado da cena vive na sessão (`Scene`).
  Keyframe `calamidade:<efeito>` em animação de mob só dispara por `GeoFxKeyframes.triggerAnim` no servidor (nunca por
  handler de keyframe no cliente). `SkillEffect.Kind.VISUAL_FX` existe, mas nenhuma habilidade o usa e `SkillService`
  não o aplica. Antes de usar algo novo do Calamidade/RimuLib, confira com `javap` no jar.
- **Contador de derrotas:** +1 para o alvo **jogador** só em `FinisherTestMechanic.complete` e só com execução
  `SUCCESS` (Etapa 4.11; `FAILED`/totem e `abort` nunca contam; `FinisherRules.countsDefeat(kind, outcome)`). Dano com
  `bypasses_invulnerability` (`/kill`, vazio) a um participante cancela a cena e passa; outro dano é descartado. (`FinisherRules.countsDefeat`: mob e chefe nunca têm contador, estatística ou plaqueta). A plaqueta
  replica a matemática do `renderNameTag` 1.21.1; ícones são a fonte bitmap `respectcraft:defeat_icons`, não emoji.
- **Necessidades:** ativas são Coom, Bexiga, Intestino e Emoção (id `humor`) — `Need.ACTIVE`. Hidratação e Energia
  são **dados legados inativos** (`Need.active() == false`): continuam no Codec, mas não mudam, não afetam a Emoção,
  não penalizam e não aparecem. Não as reative sem pedido. Medidores detalhados só no painel do inventário e no
  menu K; na tela de jogo, só o orbe de Emoção. Consumível novo = entrada em `NeedEffects`.
- **Menu K holográfico (Etapas 4.8.1, 4.8.4-A, 4.8.4-B e 4.8.4-C):** desenho e clique/hover usam as mesmas matrizes do quadro
  (`HoloPanel`); conteúdo novo entra numa `HoloView` desenhada com `HoloCanvas` (regiões registradas no desenho) e
  vale para as duas cascas (`HoloMenu`). **Modo mundo (padrão):** nada de `Screen` nem entrada capturada; a âncora
  (olho + yaw, `HoloWorld`) só muda por K/Recall; o depth buffer do mundo nunca é limpo (`Holo3D.beginWorld/endWorld`);
  fecha na morte, respawn, troca de nível/dimensão, logout e telas incompatíveis. **Entrada no mundo (4.8.4-B):** só
  por `HoloWorldInput` (nunca desligue controles vanilla de forma global). A mira é o raio do vanilla (olhos/olhar da
  entidade da câmera, mesmo quadro do `hitResult`); a placa só é dona pela face da frente, no alcance e **mais perto**
  que o alvo do vanilla. O dono de cada botão é decidido no aperto (`InputEvent.MouseButton.Pre`, cancelado = nada de
  ataque/mineração/uso/balanço) e vale até soltar; aperto do mundo nunca vira clique de placa, mas a repetição segurada
  não age atrás de uma placa (`InteractionKeyMappingTriggered`). Roda só é consumida com a mira numa placa. Modo cursor
  (Alt esquerdo segurado) solta o mouse e o prende de novo ao soltar/perder o foco. A tela da 4.8.1 é reserva temporária
  (sem recursos novos). Não aloque framebuffer por quadro, não crie entidades/blocos para o menu e devolva todo o
  estado GL em `Holo3D.end`/`endWorld`. Fundo do mundo pelo Rimulib
  com passada própria (`FxApi` + `shaders/fx/holo_backdrop.fsh`): os campos de `Frame.screen()` sozinhos não fazem nada
  no 1.3.0. Fontes do Rimulib: o `y` do `UiApi.text` é a linha de base (`HoloCanvas.capHeight`).
  **Criador e roleta no mundo (4.8.4-C):** são janelas (`HoloMenu.popup`, como o Display) na frente do arco; enquanto
  abertas só elas respondem (`HoloMenu.exclusive()`), o resto recua e o clique nele é só consumido. No modo mundo
  **nunca** abra `CharacterCreationScreen`/`BirthRouletteScreen` (`HoloWorld.onScreenOpening` os troca pela janela).
  K segue o fluxo: sem aparência → criador; sem nascimento → roleta; nascido → Hub. Estado do criador só em
  `AppearanceDraft` e da roleta só em `RouletteSession`/`RouletteMachine` (as telas de reserva usam os mesmos): não
  duplique pedido, `requestId`, erro ou timeout da 4.8.2. Novo giro e Continuar com giro sobrando pedem segundo clique.
  As telas antigas saem na 4.8.5.
- **Seções do menu K (Etapa 4.11):** sete — Overview, Character, Body & Traits, Speciality, Skills, **Alerts** e
  **Bounty Hunt** (`HoloCharacterScreen.Section`, ids/ordem fazem parte das teclas 1–7 da tela de reserva). Seção nova =
  valor no enum + `HoloView` no array de `HoloMenu` (o arco, os chips e as teclas se ajustam ao número de seções; chip
  estreito vira só ícone com a dica). Ponto aceso no chip por `HoloNav.badge`; a fita tem o botão ✉ (não lidos).
- **Avisos holográficos (Etapa 4.11):** só `notice.HoloNotices` manda aviso (nunca mande o pacote direto) e só
  `notice.NoticeFx` acende farol de aviso no Calamidade (pilar + anel + luz no local, sem `film`/`shake`/câmera;
  recusado pelos limites = aviso sem farol). Aviso é só exibição: nunca concede nada (chefe derrotado continua **sem
  recompensa**). Duplicatas pela chave (`NoticeRules.DEDUPE_MILLIS`) são descartadas no cliente; histórico só no
  cliente (`HISTORY_LIMIT`), limpo no logout. Cartões no canto superior esquerdo (somem com F3 ou com `noticeToasts`
  desligado na config/Display; continuam na seção Alerts). Sistemas futuros (chefes do mod, portais, eventos) chamam a
  API de `HoloNotices`; categoria nova entra no **fim** de `NoticeKind` (o pacote usa o ordinal).
- **Caça a recompensa (Etapa 4.11):** servidor autoritativo em `BountyService`. Recompensa = a pilha inteira da mão
  principal de quem paga, guardada no quadro (sem moeda/economia); paga ao caçador que **aceitou** o contrato quando ele
  mata o alvo (inclusive pela execução real de uma finalização jogador × jogador, `SUCCESS`; totem/`FAILED` e
  cancelamento nunca pagam, `BountyRules.finisherClaims`; o contrato sai do quadro no primeiro pagamento); volta ao pagador no cancelamento (só sem caçadores) ou
  ao expirar (1/3/7 dias de jogo, conferido a cada 5 s; offline = no próximo login). Limites em `BountyRules`. O quadro
  vai a cada jogador no login, quando muda e quando a aba pede (a cada 5 s com a aba na frente, limite de 40 ticks por
  jogador); o rastreio é só distância arredondada + oito ventos para quem aceitou, nunca a posição exata. A aba
  "Post bounty" pede segundo clique (tira os itens da mão).
- **Telas** herdam `client.ui.RespectScreen` e usam os widgets de `client.ui` (texturas próprias, sem blur vanilla).
  Texto nunca passa da coluna/painel: quebre ou encurte com `Ui.wrap`/`Ui.fit` e ofereça a dica com o texto inteiro
  (Etapa 4.7.2). Confira GUI 2, GUI 3 e a menor GUI (320×240).
- **Textos visíveis ao jogador em inglês** (Etapa 4.6) via `Component`/lang: `en_us.json` é o único arquivo de idioma
  (não recrie `pt_br.json`; outros idiomas caem no inglês). Mensagens de comando/chat também em inglês. Código,
  comentários, docs e ids internos seguem como estão.
- **Mobs:** recursos `geo/entity/<nome>.geo.json`, `animations/entity/<nome>.animation.json`,
  `textures/entity/<nome>.png`; GeckoLib só anima, dano/alvo/IA no servidor. Registre em `ModEntities` + atributos,
  renderer em `client.render` registrado em `RespectCraftClient`.
- **Inverted Realm (Etapa 4.9, MVP):** `respectcraft:inverted_realm` (tipo, gerador e `noise_settings` com o mesmo id),
  bioma único `respectcraft:twisted_wilds`. Céu roxo = `sky_color` do bioma + `fixed_time` 12400 (crepúsculo eterno) +
  `InvertedRealmEffects` (id = `effects` do tipo). Terreno: chão de 3D noise com saliências (y ≈ 60–115, mar em 60) e uma
  faixa de ilhas flutuantes (y 148–236, `InvertedRealm.ISLAND_FLOOR`). Acesso **só** por `/respectcraft realm
  enter|leave [players]` (permissão 2; chegada no chão de baixo, nunca no alto de uma ilha; sem chão seco num raio de 48,
  plataforma 3×3) e `check [raio]` (usado no teste de fumaça). **Não há portal** ainda. O chão usa blocos vanilla
  (grama/micélio tingidos pelo bioma, terracota roxa, deepslate) até a arte da Fase 1 chegar; `twisted_log` é
  provisório (casca de `twisted_wood` + `twisted_log_top` gerado por `tools/inverted_realm/make_twisted_log_top.py`).
  Agua-vivas (`agua_viva` gigante, `agua_viva_filhote`) são passivas e flutuam (categoria AMBIENT no bioma): os clips
  `attack_beam` e `grab` existem mas **não** têm regra de combate — não invente.
- **Tema:** o mod não tem mais eleições, política, partidos, mandatos, urna, economia ou símbolos nacionais. Não
  reintroduza (nem a partir do histórico do Git) sem pedido explícito.

## IDs persistentes e compatibilidade de saves (não renomear)

Válidos desde a Etapa 4.7.1, que trocou o namespace inteiro para `respectcraft` e quebrou de propósito a
compatibilidade com mundos, saves e configs anteriores. A partir dela, estes ids voltam a ser estáveis.

| Dado | Onde fica |
|---|---|
| `respectcraft:character_profile`, `respectcraft:needs`, `respectcraft:need_effects`, `respectcraft:player_animation` | attachments (`ModAttachments`); nomes de campos dos Codecs também são estáveis (incl. `birth_traits`, `birth_speciality`, `current_speciality` e os legados `hidratacao`/`energia`) |
| `respectcraft_character_appearance_v1`, `respectcraft_special_defeats`, `respectcraft_allow_mob_finishers_test`, `respectcraft_debug_fail_next_roll` | `persistentData` do jogador |
| `respectcraft_boss_finisher_credit`, `respectcraft_boss_defeat_posted` | `persistentData` de entidades chefe (Etapa 4.8) |
| `respectcraft_realm_return` | `persistentData` do jogador: volta do `/respectcraft realm leave` (Etapa 4.9) |
| `respectcraft_bounties` (campos `bounties`, `pending_returns` e os do Codec de `Bounty`) | SavedData do Overworld (`data/respectcraft_bounties.dat`): contratos abertos e recompensas a devolver (Etapa 4.11) |
| ids de `BirthOrigin`, `Speciality`, `BirthTrait`, `CharacterAttribute`, `Need`, `Skills` (`combate/...`), finalizações e animações | gravados na ficha/estado |
| itens, blocos (`twisted_*`), block entities `twisted_sign`/`twisted_hanging_sign`, `respectcraft:agua_viva`, `respectcraft:agua_viva_filhote` | registries |
| entidades `respectcraft:dev_rig_mannequin_a`, `respectcraft:dev_rig_mannequin_b` (fixtures da 4.12), `respectcraft:strawberry` + item `respectcraft:strawberry_spawn_egg` e os nomes das partes do binding do morango (4.13.1), família `respectcraft:fruit_humanoid_v1` (nomes dos bones e animações `animation.fruit_humanoid_v1.*` fazem parte do contrato v1) | registries / manifestos (Etapa 4.12); o attachment `respectcraft:rig_visual` não é salvo |
| finalizações `respectcraft:strawberry_crusher`, `fruit_punch`, `ground_smasher`, `shut_up`, `strawberry_blender`; som `respectcraft:entity.strawberry.music` | catálogo/estado de animação e `sounds.json` (Etapa 4.13.3) |
| tipo de dano `respectcraft:finisher_execution` (mensagens `death.attack.respectcraft.finisher_execution*`), tag de entidade `respectcraft:bosses` | dados (Etapa 4.10.1) |
| dimensão `respectcraft:inverted_realm`, bioma `respectcraft:twisted_wilds`, `WoodType`/`BlockSetType` e `Boat.Type` `respectcraft:twisted` (enum `RESPECTCRAFT_TWISTED`) | pasta do mundo, chunks e NBT de barcos (Etapa 4.9) |

- Id de origem aposentado: adicione o alias em `profile.LegacyOriginIds` (lido pelo Codec da ficha e regravado com o
  id novo) e um caso no `SelfTest`. Nunca converta um perfil inválido em "personagem não criado".
- Remover item/entidade registrado apaga essas instâncias em mundos antigos: avise no PR e no handoff.
- Aposentados (não reutilize os ids): entidade `respectcraft:leao_do_fim` e item `respectcraft:leao_do_fim_spawn_egg`
  (Leão do Fim removido na Etapa 4.9.1, pedido do dono; Leões e ovos de mundos salvos somem).
- Recomende sempre backup dos mundos antes de atualizar.

## Blockbench e animações do jogador

- Projetos `.bbmodel` do jogador em `model_sources/player/` (`respectcraft_animated_player`, `respectcraft_player_combat`,
  `respectcraft_combo_scene` — este só para ajustar o tempo da cena).
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
bash .github/scripts/server-smoke-test.sh # servidor dedicado: zumbis vanilla, /respectcraft selftest, catálogos
python3 tools/player_anim/bbsync.py      # .bbmodel x .animation.json
python3 tools/player_anim/test_export.py # proteção do exportador
python3 tools/fx/check_shaders.py        # shaders fx montados como a Rimulib + glslangValidator (roda no CI)
python3 tools/rigkit/test_rigkit.py      # validador de rigs, template e gerador em dia (roda no CI)
python3 tools/rigkit/validate_rig.py --repo  # famílias e bindings do repositório
python3 tools/scenekit/strawberry_finishers.py --check  # clips das 5 cenas do morango == separação da fonte (CI)
python3 tools/scenekit/test_scenekit.py     # kit de autoria de cenas: composição, separação, proteção (roda no CI)
python3 tools/scenekit/make_templates.py --check  # templates, exemplos e clips da cena de teste em dia
bash tools/scenekit/blockbench/check_scene.sh # templates no Blockbench web real (job do CI; precisa de github.com)
./gradlew runClient                      # cliente de desenvolvimento
./gradlew runServer                      # servidor dedicado (aceita comandos no console)
```

- Não há JUnit. Regra pura nova entra no `selftest.SelfTest` (sai como `RespectCraft self-test: OK` no smoke test).
- O CI (`.github/workflows/build.yml`) roda `bbsync.py`, os kits de rigs e de cenas, `./gradlew build`, o teste de
  fumaça e (job `scenekit-blockbench`) os templates no Blockbench real; publica os ZIPs dos dois kits.
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
  roleta com chances do servidor → 4.7.1; erro estruturado da roleta e `requestId` → 4.8.2; layout de texto do Hub e do painel Body & Traits → 4.7.2; menu holográfico da
  tecla K (projeção, clique, Rimulib UiApi/FxApi) → 4.8.1; auditoria e plano do menu no mundo → 4.8.3; menu K no mundo
  (âncora, posição, passada no mundo, K/Recall) → 4.8.4-A; clique, arrasto, roda, dicas, dono da entrada e modo cursor
  no mundo → 4.8.4-B; criador e roleta como janelas holográficas, fluxo do primeiro personagem, Skin/Face removidos →
  4.8.4-C; visual da roda da roleta (roda de prêmios) → `HANDOFF_ROULETTE_PRIZE_WHEEL.md`; dimensão Inverted Realm (céu
  roxo, terreno, `/respectcraft realm`), Madeira Retorcida e agua-vivas → 4.9 (`HANDOFF_STAGE4_9_PURPLE_DIMENSION_MVP.md`);
  remoção do Leão do Fim → 4.9.1; efeitos das finalizações no Calamidade_Awakening (ponte `fx`, shaders, gancho
  GeckoLib, dependência) → 4.10 (`HANDOFF_STAGE4_10_FINISHER_FX_CALAMIDADE.md`); execuções reais, regra de chefes,
  finalizações exclusivas e plano de desbloqueio → 4.10.1 (`HANDOFF_STAGE4_10_1_TRUE_EXECUTIONS.md`); validação
  multiplayer com dois clientes reais (PvP, times, totem, mob × jogador, saídas, servidor parando) → 4.10.2
  (`HANDOFF_STAGE4_10_2_MULTIPLAYER_VALIDATION.md`); avisos holográficos automáticos, faróis no Calamidade, seções
  Alerts/Bounty Hunt e caça a recompensa → 4.11 (`HANDOFF_STAGE4_11_NOTICES_BOUNTIES.md`); ciclo de vida do Finisher
  (contador só com `SUCCESS`, `/kill` na cena) e registro de chefes personalizados → 4.11
  (`HANDOFF_STAGE4_11_FINISHER_CONSOLIDATION_BOSSES.md`); Custom Rig Framework, família `fruit_humanoid_v1`, kit
  Blockbench, validador e manequins → 4.12 (`HANDOFF_STAGE4_12_CUSTOM_RIG_FRAMEWORK.md`); integração de assets
  externos e morango → 4.13.1 (`HANDOFF_STAGE4_13_1_STRAWBERRY.md`); autoria conjunta de finalizações (kit de cenas,
  templates, separação) → 4.13.2 (`HANDOFF_STAGE4_13_2_FINISHER_SCENE_KIT.md`); morango de gameplay (spawn, IA, XP,
  música), política de seleção e finalizações do morango → 4.13.3 (`HANDOFF_STAGE4_13_3_STRAWBERRY_GAMEPLAY.md`).
- **Roadmap** (funcionalidades futuras, só registro — não implemente sem pedido): [`docs/ROADMAP.md`](docs/ROADMAP.md).
- Os handoffs são históricos: quando contradizem este arquivo ou o código, vale o código e este arquivo.
