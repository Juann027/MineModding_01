# CLAUDE.md — TupiniCraft

Mod brasileiro de sátira política para **Minecraft 1.21.1 / NeoForge 21.1.x / Java 21**.
Base: MDK oficial `NeoForgeMDKs/MDK-1.21.1-ModDevGradle` (plugin `net.neoforged.moddev`).

> **Variante atual (v0.5.5):** Tigrinho, eleições, mandatos, economia geral, HUD/urna cívica e
> `rulesTest` foram **desativados/arquivados** (cópia em `backups/*.zip`; não extraia nem reintroduza sem pedido
> explícito). Skill trees também foram desativadas (código/datapack no backup). Ficam ativos: Cabo Eleitoral, Leão do Fim e pastas `biome`/`dimension`/`npc` como pontos de partida. Leia o `README.md` e o
> `AGENTS.md`. As seções abaixo sobre eleições, mandatos, economia, HUD e testes descrevem o sistema arquivado.
> O CI agora roda `./gradlew build` + teste de fumaça (servidor sobe e mobs são invocados).

## Rig GeckoLib do jogador, Body Layers e animações (ativo; detalhes em `HANDOFF_GECKOLIB_STAGE2.md`)

- O rig é a **primeira camada** do `PlayerRenderer` (`client.playergeo.PlayerRigLayer`): lê a pose vanilla, desenha o
  rig e devolve a pose ao `PlayerModel`, para armadura, itens e demais camadas acompanharem. Não cancele
  `RenderPlayerEvent.Pre` nem volte a desenhar o jogador fora do renderer vanilla.
- Em `PlayerRigModel.compose`, mantenha o `GeoBone::resetStateChanges` no fim (sem ele a pose acumula a cada quadro).
- Peças do corpo (busto, cabelo, acessórios) são `BodyLayerDefinition` em `client.bodylayer.BodyLayers`; não crie
  `RenderLayer` nova por peça nem desenhe a peça dentro do rig.
- Animações são dados em `animation.PlayerAnimations` (sem classe por animação). Só `PlayerAnimationService` inicia e
  para animações; o cliente envia apenas a intenção. Som, VFX e câmera entram por `PlayerAnimationEvent`.

## Etapa 3 (ativo; detalhes em `HANDOFF_ETAPA3.md`)

- Telas novas herdam `client.ui.TupiScreen` e usam os widgets de `client.ui` (sem blur vanilla, texturas próprias).
- Ficha (`profile.CharacterProfile`, attachment `tupinicraft:character_profile`) só muda por `ProfileService`;
  necessidades (`needs.Needs`, attachment `tupinicraft:needs`) só por `NeedsService`. Sorteio da roleta e compra de
  habilidades acontecem **só no servidor**; o cliente envia a intenção. Ids de origens, atributos, necessidades e
  habilidades são persistidos (não renomear).
- Modificadores de atributo do mod são transitórios (`tupinicraft:stat/...`) e recolocados por
  `SkillService.refreshModifiers`; nunca os grave no save.
- Finalizações são dados (`finisher.Finishers`); a câmera cinematográfica (`client.cinematic.CinematicCamera`) segue
  o estado de animação sincronizado e precisa restaurar a perspectiva em qualquer saída. Regras puras novas entram no
  `selftest.SelfTest` (rodado por `/tupini selftest` no teste de fumaça).

## Fluxo de entrega e merge automático (política do dono do repositório)

O dono do repositório autorizou merge automático dos PRs ao concluir cada tarefa, sem aguardar aprovação manual.
Em toda implementação:

1. Crie uma branch para a funcionalidade (em sessões de nuvem, use a branch designada pela sessão; se o PR
   anterior dela já foi integrado, recomece-a a partir da `main` atualizada).
2. Implemente e execute os testes (`./gradlew build`, que inclui `rulesTest`; e o teste de fumaça do servidor
   quando a mudança tocar servidor/persistência).
3. Corrija erros de compilação, testes e regressões antes de abrir o PR.
4. Abra o Pull Request para a `main`.
5. Aguarde a conclusão do CI do PR (workflow **Build**: `./gradlew build` + teste de fumaça do servidor dedicado).
6. Se todos os testes e verificações obrigatórias passarem e o PR estiver sem conflitos, faça o merge na `main`
   (merge commit). O workflow **Release** publica a versão se `mod_version` for nova.
7. Confirme que o merge aconteceu (estado do PR e commit na `main`).
8. Informe o número do PR, o commit final (merge) e um resumo das alterações.

Regras:
- **Nunca** faça merge com testes obrigatórios falhando, CI pendente/vermelho ou erros críticos conhecidos.
- Se o merge estiver bloqueado (conflitos, testes, regras de proteção), resolva o que for possível **sem contornar
  proteções** (nada de force-push na `main`, desativar checks ou pular testes). Se não der, informe o motivo.
- Mantenha o namespace `tupinicraft` e a compatibilidade com NeoForge 1.21.1, Java 21, servidor dedicado e
  multiplayer, além dos dados salvos de versões anteriores.

## Bibliotecas de mods (versões exatas)

| Mod | Versão | Coordenada Gradle | Repositório Maven | Instalação |
|---|---|---|---|---|
| GeckoLib 4 | 4.9.3 | `software.bernie.geckolib:geckolib-neoforge-1.21.1` | `https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/` | **obrigatória** no cliente e no servidor |
| SmartBrainLib | 1.16.11 | `net.tslat.smartbrainlib:SmartBrainLib-neoforge-1.21.1` | `https://dl.cloudsmith.io/public/tslat/sbl/maven/` | **obrigatória** no cliente e no servidor |
| UI Lib (DAQEM) | 9.0.0 | `maven.modrinth:ui-lib:XSWl7TVm` (ID da versão NeoForge) | `https://api.modrinth.com/maven` (Maven oficial do Modrinth) | **obrigatória só no cliente** |
| Architectury API | 13.0.11+neoforge | `maven.modrinth:architectury-api` | Maven do Modrinth | **obrigatória só no cliente** (dependência do UI Lib) |

- Versões ficam em `gradle.properties`; `neoforge.mods.toml` declara GeckoLib `[4.9.3,5)` e SmartBrainLib `[1.16.11,)`
  obrigatórias nos dois lados, e UI Lib `[9.0.0,10)` e Architectury `[13.0.6,)` com `side="CLIENT"` (o servidor
  dedicado não precisa deles; o teste manual confirmou o servidor só com GeckoLib + SmartBrainLib). Não use GeckoLib 5.
- **UI Lib no Gradle:** o Modrinth publica a 9.0.0 de Fabric e de NeoForge com o mesmo número, então a coordenada usa
  o ID da versão NeoForge (`uilib_modrinth_version_id`). UI Lib é `implementation`; Architectury é `localRuntime`.
  Só o pacote `client` importa `com.daqem.uilib.*`.
- **Bugs do UI Lib 9.0.0 no 1.21.1 (contornados no nosso código, sem copiar classes dele):**
  - as molduras dos nós e as caixas de dica usam `textures/gui/advancements/widgets.png`, removido do Minecraft na
    1.20.2 → `LegacyAdvancementWidgets` monta esse arquivo na hora, a partir dos sprites
    `textures/gui/sprites/advancements/*` da própria instalação (não distribuímos arte da Mojang);
  - a dica dos nós sai no canto da tela e sem cores → `SkillTreeScreen.renderTooltips` desenha a dica padrão do jogo;
  - ao trocar de aba o título antigo não é removido → a tela remove os títulos repetidos;
  - os botões de seta das abas usam `textures/gui/widgets.png` (também removido): só aparecem com mais de 10 abas,
    o que não acontece hoje (pessoais + 1 região + 1 partido + 7 cargos).
- **Histórico:** até a v0.4.0 as árvores usavam o SkillLib 1.4.0 (que exigia KubeJS e Rhino). Foi trocado pelo UI Lib
  por decisão do dono do repositório; as compras feitas no SkillLib não foram migradas (ficavam no attachment dele).
- Nunca copie classes das bibliotecas para dentro do mod; use só as APIs públicas.

## Stack e limites

- Minecraft `1.21.1`, NeoForge `21.1.265` (`gradle.properties`), Parchment `2024.11.17`.
- Java 21 (toolchain no `build.gradle`). Gradle wrapper 9.2.1.
- **Não usar** APIs/tutoriais de Forge 1.20.x, Fabric ou NeoForge 21.2+ (ex.: `RegisterClientPayloadHandlersEvent`,
  sync automático de attachments, `SavedDataType`, `ResourceKey`-based items do 1.21.2+). Confira sempre a assinatura 1.21.1.
- Dependências de mods: só as da seção "Bibliotecas"; nada de JUnit. Testes de regra rodam via `rulesTest` (JavaExec).
- Multiplayer e servidor dedicado são obrigatórios.

## Comandos de build/teste

```bash
./gradlew build          # compila, empacota o jar e roda rulesTest
./gradlew rulesTest      # só os testes de regras puras (src/test/java/com/tupinicraft/RulesSelfTest.java)
./gradlew runClient      # cliente de desenvolvimento
./gradlew runServer      # servidor dedicado de desenvolvimento (aceita comandos no console)
```

O CI (`.github/workflows/build.yml`) roda `./gradlew build` e depois
`.github/scripts/server-smoke-test.sh`, que sobe o servidor dedicado duas vezes e verifica
carregamento sem classes de cliente, das skills e persistência de uma eleição ativa após reinício.

> Em sessões de nuvem do Claude Code o `maven.neoforged.net` e os servidores da Mojang podem estar
> bloqueados pela política de rede; nesse caso valide a compilação pelo CI do GitHub.

## Identificadores

- Mod id: `tupinicraft` (`TupiniCraft.MOD_ID`). Pacote base: `com.tupinicraft`.
- Use `TupiniCraft.id("caminho")` para `ResourceLocation`.
- IDs persistidos **estáveis** (não renomear): sigla da UF (`RegionalOrigin.code()`), `PoliticalOffice.id()`,
  IDs de território (`tupinicraft:brasil`, `tupinicraft:es`, `tupinicraft:vitoria`…), caminhos de `ConfigKeys`
  (usados nos overrides), nomes de campos NBT/Codec, SavedData `tupinicraft_election`, `tupinicraft_mandates`,
  `tupinicraft_config_overrides` e o attachment `tupinicraft:political_data`.

## Pacotes

| Pacote | Responsabilidade |
|---|---|
| `origin` | `RegionalOrigin` (27 UFs: nome, sigla, gentílico, macrorregião), `Macroregion`, `OriginService` (sorteio/atribuição). |
| `player` | `PoliticalData` (record imutável + Codec + StreamCodec), `OfficeProgress`/`SkillProgression` (habilidades por cargo, puros), `PoliticalDataService` (único ponto de leitura/escrita + sync), eventos de jogador. |
| `politics` | `PoliticalOffice`: cargos (ordem só de exibição), jurisdição (país/estado/município) e **valores padrão** do TOML. |
| `config` | `ConfigKeys` (tabela única de chaves, limites e padrões), `BrasilServerConfig` (TOML SERVER gerado da tabela), `ConfigOverridesSavedData` (overrides do mundo), `ServerSettings` (**único ponto de leitura** dos valores efetivos). |
| `territory` | `Territories`: catálogo imutável (Brasil, 27 UFs, 27 capitais) com IDs estáveis e domicílio eleitoral. |
| `mandate` | Cadeiras (`SeatKey`), mandatos, `MandateRegistry` (regra de 1 ocupante), `MandateQueries` (API de consulta), `PoliticalMapSnapshot`, `MandateSavedData`, `MandateService` (posse, expiração, pontos de habilidade). |
| `election` | Máquina de estados, calendários, `CandidacyRules` (elegibilidade, pura), `ElectionRules` (regras congeladas por eleição), `ElectionSavedData`, `ElectionManager` (regras no servidor), `ElectionTally` e `NpcCandidateFactory` (puros), item da urna. |
| `network` | Payloads (`CustomPacketPayload` + `StreamCodec`) e registro em `RegisterPayloadHandlersEvent`. |
| `client` | Entrada `@Mod(dist = CLIENT)`, caches visuais, camadas de HUD, faixa de avisos, tela da urna. |
| `command` | Brigadier (`/brasil ...`) registrado em `RegisterCommandsEvent`. |
| `registry` | `DeferredRegister` de itens, entidades e attachment types. |
| `skilltree` | Árvores de habilidades (servidor): `SkillTreeDef`/`SkillDef`/`SkillEffectDef`/`SkillRule`, `SkillDataParser`, `SkillCatalogData` (validação) e `SkillGate` (puros); `SkillCatalog` (carrega o datapack), `SkillEffects` (modificadores de atributo), `SkillTreeService` (compra, suspensão, sincronização), `SkillTreeState` (compras do jogador). |
| `client.skilltree` | Tela das árvores com UI Lib (`SkillTreeScreen`), tecla K (`SkillTreeKeys`), cache (`ClientSkillCache`) e `LegacyAdvancementWidgets`. |
| `economy` | `Wallet` (saldo em reais fictícios por jogador), `WalletService` (único ponto de débito/crédito), `Money` (formato `R$ 4.500`). |
| `party` | `PartyAffiliation` (filiação; hoje só por comando de teste). |
| `entity` | Entidades (lógica de servidor). Hoje só o mob de teste `CaboEleitoralEntity` (GeckoLib + SmartBrainLib). |

## Convenções técnicas

- **Servidor é autoritativo.** Cliente só exibe caches recebidos por pacote; nenhuma regra roda no cliente.
  Pacotes cliente→servidor carregam só a intenção (ex.: `CastVotePayload(candidate)`); o servidor revalida tudo
  e identifica o jogador pela conexão.
- **Separação de lado:** nada fora de `client` pode importar `net.minecraft.client.*`. Handlers de pacotes
  servidor→cliente são registrados em `ModNetwork` por **lambdas** que chamam `ClientPayloadHandler`, para a classe
  só ser carregada no cliente. Não troque por method reference `ClientPayloadHandler::x`.
- **Sem estado de mundo em `static`.** Dados de jogador → Data Attachment (`ModAttachments.POLITICAL_DATA`,
  `serialize(CODEC)` + `copyOnDeath()`). Estado global → `ElectionSavedData` no Overworld; chame `setDirty()` após mudar.
  Estáticos permitidos apenas para caches visuais do cliente (limpos no logout).
- **Dados do jogador:** sempre via `PoliticalDataService.set(...)`, que persiste e sincroniza o HUD. Valores são
  inteiros em `[0, PoliticalData.MAX_VALUE]`.
- **Sincronização:** só no login, respawn, troca de dimensão e quando algo muda. Eleição: em mudanças, a cada 5 s
  (correção de relógio) e a cada 1 s na apuração. Nunca a cada tick.
- **Tempo:** a eleição avança apenas por `ServerTickEvent.Post` (contagem em ticks salva no SavedData). Nada de
  threads ou timers do cliente. Com o servidor desligado, o cronômetro fica pausado.
- **Configuração (sem números mágicos):** todo valor de gameplay ajustável é uma chave em `ConfigKeys` e é lido
  por `ServerSettings`. Prioridade: **1) override do mundo** (`/brasil admin config set`, salvo em
  `ConfigOverridesSavedData`) → **2) TOML SERVER** (`config/tupinicraft-server.toml`). `reset` remove o override.
  Para uma chave nova: adicione em `ConfigKeys` (com limites) e leia por `ServerSettings`; o TOML e os comandos
  se atualizam sozinhos. Pares mínimo/máximo são registrados com `pair(...)` e validados no `set`.
- **Mudanças com eventos em andamento:** uma eleição congela suas regras (`ElectionRules`) e cada disputa congela
  pontuação mínima e faixa de NPCs na criação; votos guardam o peso no momento do voto. Mandatos guardam o fim
  previsto na posse. Mudanças de configuração valem só para as próximas eleições/mandatos; o intervalo dos
  calendários vale a partir do próximo ciclo.
- **Carreira política (sem progressão sequencial):** qualquer jogador, inclusive Desempregado, pode concorrer a
  qualquer cargo; o **único requisito pessoal é o XP político mínimo** (`progressao.xp_min.<cargo>`), mais o
  domicílio eleitoral. **Popularidade não é requisito**: só pesa na pontuação e no peso do voto
  (`eleicao.popularidade_referencia.<cargo>` entra só na pontuação mínima de vitória). Não reintroduza checagens
  de "cargo anterior" (`PoliticalOffice.next()` é só para exibição).
- **Cargo × mandato:** o cargo do jogador acompanha o mandato vigente. Na posse vira o cargo da cadeira; ao perder
  ou terminar o mandato volta a **Desempregado**, mantendo XP, popularidade e o progresso de habilidades por cargo
  (`PoliticalData.officeProgress`), que volta a valer se ele reassumir o cargo. Um jogador ocupa **no máximo uma
  cadeira vigente** (`MandateRegistry`); ao tomar posse de outra, renuncia à anterior (`MandateService.inaugurate`).
  Mudanças de cargo de jogadores offline ficam pendentes em `MandateSavedData` e são aplicadas no login.
  Títulos de cargo da v0.1.0 (sem cadeira) são preservados.
- **Territórios e mandatos:** a origem do jogador define só o **domicílio eleitoral** (`Territories.domicile`); o que
  ele governa fica nas cadeiras (`MandateRegistry`). Identifique ocupantes por UUID do jogador ou ID do NPC, nunca
  pelo nome. Consultas e o snapshot do mapa são derivados do registro (não crie cópias persistentes). Mandatos
  expirados aparecem vagos nas consultas mesmo antes da limpeza periódica. Tempo dos mandatos = tempo de jogo do
  Overworld (`MandateService.now`).
- **Mapa político (futuro):** o cliente só lê snapshots (`PoliticalMapSnapshotPayload`, ainda não registrado). Ao
  implementar a tela, registre um pedido cliente→servidor e responda com o snapshot dos territórios pedidos;
  nunca sincronize todas as cadeiras por tick.
- **Textos:** português do Brasil, com `Component` do Minecraft. Anúncios da eleição vão para a faixa na tela
  (`AnnouncementPayload` → `AnnouncementOverlay`), não para o chat. Respostas de comando usam `sendSuccess/sendFailure`.
  Votos são secretos: não repasse o resultado do voto aos admins (`notifyAdmins = false`).
- **HUD:** apenas `RegisterGuiLayersEvent` + `GuiGraphics` (sem texturas de UI). Respeitar ícones de efeitos (topo
  direito), F3 e `gui.leftHeight/rightHeight` (a barra de XP político empurra corações/fome para cima).
  A faixa de avisos é posicionada por `AnnouncementLayout` (cálculo puro): nunca cobre os painéis da direita
  (`PoliticalHud.panelBounds`) nem as barras de chefe (medidas por `BossBarArea` via
  `CustomizeGuiOverlayEvent.BossEventProgress`, sem desenhar). Painel novo no HUD → incluir em `panelBounds`.
- **Comandos admin** ficam sob `/brasil admin` com `hasPermission(2)`. Validar entradas e retornar mensagens claras.
- **Árvores de habilidades (sistema próprio + tela do UI Lib):**
  - Arquivos em `data/<ns>/tupinicraft_skilltree/arvores/*.json` (uma aba) e `.../skills/*.json` (um nó cada),
    recarregados com `/reload`. A **árvore** define o tipo (`pessoal|regional|partidaria|cargo`) e o requisito
    (`regiao`, `partido` ou `cargo`), nome, `icone` (item), `fundo` (textura repetida) e `ordem`. A **skill** define
    `arvore`, `pai` (skill anterior; vazio = raiz), `nome`, `descricao`, `icone`, `moldura` (`task|goal|challenge`),
    `custo` (reais) ou `pontos` (do cargo), `xp_min` e `efeitos` (`atributo`, `operacao` = `add_value` |
    `add_multiplied_base` | `add_multiplied_total`, `valor`; atributos 1.21.1 `minecraft:generic.*`).
  - Validação (`SkillCatalogData`): árvore existente, pai existente e da mesma árvore, **exatamente uma raiz por
    árvore**; arquivos inválidos são descartados com erro no log, sem derrubar os outros. Árvore sem skills não vira aba.
  - Donos dos dados: tudo é do TupiniCraft. Compras e suspensões ficam em `SkillTreeState` (attachment
    `tupinicraft:skill_tree_state`, copiado na morte). Os efeitos são modificadores de atributo **transitórios**
    (`tupinicraft:skill/<ns>/<skill>/<i>`), recolocados pelo servidor a partir das compras (login, respawn, reinício).
  - Compra só no servidor (`SkillTreeService.purchase`): skill anterior comprada, requisitos do `SkillGate`, cobra
    dinheiro (pessoal/regional/partidária) ou pontos **daquele cargo**; XP político nunca é consumido. Na tela, clicar
    num nó não comprado envia `SkillPurchaseRequestPayload`; a resposta vem como toast (`SkillNoticePayload`).
    Não há liga/desliga: comprada = ativa enquanto o requisito valer. `/brasil skills comprar` continua como alternativa.
  - Quatro vezes por segundo o `SkillTreeService` aplica/suspende os efeitos conforme os requisitos (região, partido,
    mandato vigente), avisa por toast nas mudanças e sincroniza a tela (`SkillStatePayload`, só quando muda). As
    definições vão ao cliente em `SkillDefinitionsPayload` no login e após `/reload` (`OnDatapackSyncEvent`).
  - Abas visíveis (`SkillGate.treeVisible`): pessoais e de cargo sempre; regional só da macrorregião do jogador;
    partidária só do partido dele; qualquer árvore onde ele já comprou algo (para ver o que está suspenso).
- **Economia (contrato, ainda sem fontes de renda):** a única moeda é `Wallet` (attachment, por UUID, copiado na
  morte, alterado só por `WalletService`). Skills e a futura economia (Pix fictício etc.) devem usar só ela.
  Cada jogador recebe `economia.saldo_inicial` (padrão R$ 1.000) uma única vez no login (`Wallet.starterGranted`;
  carteiras da v0.3.0 recebem na próxima entrada). Fora isso, o saldo só muda por compras e por
  `/brasil admin teste dinheiro`. O saldo vai ao HUD por `WalletSyncPayload` a cada mudança e no login.
- **Partidos (ainda não existem):** `PartyAffiliation` guarda a filiação; só `/brasil admin teste partido` a altera.
  Perder mandato não mexe na filiação; sair do partido só suspende as skills partidárias.
- **Mobs (GeckoLib + SmartBrainLib):**
  - Recursos por mob `<nome>`: `assets/tupinicraft/geo/entity/<nome>.geo.json`,
    `animations/entity/<nome>.animation.json` (animações `animation.<nome>.<acao>`: `idle`, `walk`, `attack`...)
    e `textures/entity/<nome>.png`. Exporte do Blockbench no formato "GeckoLib Animated Model".
  - Entidade em `entity` (servidor): implementa `GeoEntity` (controllers e `triggerAnim` disparado no servidor)
    e `SmartBrainOwner` (`brainProvider()` → `SmartBrainProvider`, `customServerAiStep()` → `tickBrain`).
    Renderer em `client.render` (`GeoEntityRenderer` + `DefaultedEntityGeoModel`), registrado só no cliente.
  - GeckoLib cuida só da animação. Dano, colisão, cooldown e alvo são decididos no servidor. Poderes com movimento
    próprio (bolas de fogo, projéteis, lasers) devem ser **entidades** (ex.: `Projectile`) com a lógica no servidor,
    não efeitos visuais presos ao modelo. Ataques em área, ondas de choque e invocações também rodam no servidor.
  - Use a IA do SmartBrainLib só onde ela agrega (sensores, alvos, comportamentos especiais); não troque IA
    vanilla sem motivo. No modo Pacífico, mobs não miram jogadores (regra do Minecraft).
- **Extensões futuras:** benefícios por origem entram em `OriginService.applyOriginEffects` (hoje vazio).
  Ainda fora do escopo: partidos completos, economia (Pix, Mega-Sena, Tigrinho), dimensões, minérios, facções,
  bosses e missões.
- **Como adicionar uma skill:** crie `data/tupinicraft/tupinicraft_skilltree/skills/<id>.json` com `arvore` (uma das
  árvores existentes) e `pai` (a skill anterior; só a raiz fica sem pai); rode `./gradlew build` (o `rulesTest` valida
  todos os arquivos reais) e confira a tela com `runClient` + `runServer`.
- **Como adicionar um mob:** registre o `EntityType` em `ModEntities` (+ atributos), crie a classe em `entity`, os três
  recursos GeckoLib e o renderer em `client.render` (registrado em `TupiniCraftClient`).

## Testes

- Regras puras (`politics`, `origin`, `territory`, `config/ConfigKey(s)`, `mandate` exceto `*SavedData`/`MandateService`,
  `CandidacyRules`, `ElectionTally`, `ElectionRules`, `ElectionRace`, `NpcCandidateFactory`, `SkillProgression`,
  `client.AnnouncementLayout`) não
  importam classes do Minecraft e são cobertas por `RulesSelfTest`. Ao mudar fórmulas, chaves ou territórios,
  atualize esse teste. O source set de teste tem as dependências do Minecraft (`addModdingDependenciesTo`), usadas
  para validar a compatibilidade dos dados salvos (ex.: NBT da v0.1.0 via `PoliticalData.CODEC`).
- O teste de fumaça do CI cobre: override persistente após reinício, reset para o TOML, rejeição de valores
  inválidos, aldeões completando disputas, prazo congelado de eleição ativa, consulta de mandatos, mob de teste
  invocado no servidor dedicado e o carregamento das árvores e das 4 skills de teste.
- Fluxos multiplayer (candidatura, urna, HUD) exigem teste manual com 2+ clientes (`runClient` + servidor).
