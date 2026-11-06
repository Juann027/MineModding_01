# CLAUDE.md — TupiniCraft

Mod brasileiro de sátira política para **Minecraft 1.21.1 / NeoForge 21.1.x / Java 21**.
Base: MDK oficial `NeoForgeMDKs/MDK-1.21.1-ModDevGradle` (plugin `net.neoforged.moddev`).

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
| SkillLib (Yvliux) | 1.4.0 | `maven.modrinth:skilllib` | `https://api.modrinth.com/maven` (Maven oficial do Modrinth) | **opcional** (ver bloqueio abaixo) |

- Versões ficam em `gradle.properties`; `neoforge.mods.toml` declara as dependências (GeckoLib `[4.9.3,5)`,
  SmartBrainLib `[1.16.11,)` obrigatórias; SkillLib `[1.4.0,)` opcional). Não use GeckoLib 5 (é para 1.21.4+).
- **Bloqueio do SkillLib 1.4.0:** o jar publicado (único disponível; o GitHub só tem o código 1.3.6, sem releases)
  quebra ao iniciar sem KubeJS (`NoClassDefFoundError: dev/latvian/mods/kubejs/event/KubeEvent`): o
  `SkillLib.onServerStarting` chama a API do KubeJS direto, e o `isLoaded("kubejs")` não evita a verificação da
  classe pela JVM. Por isso o SkillLib é **opcional** e o código dele é `compileOnly`. Para usá-lo hoje é
  preciso instalar também KubeJS `2101.7.2-build.381`, Rhino `2101.2.8-build.91+Rhino-1.21` e Better Advanced
  Tooltips `2101.1.0-build.5` (todos no Modrinth). No desenvolvimento: `./gradlew runServer -PwithSkillLib`
  (ou `runClient`) adiciona os quatro só no ambiente local (`localRuntime`), sem ir para o jar do mod.
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
carregamento sem classes de cliente e persistência de uma eleição ativa após reinício.

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
| `skilltree` | Lado TupiniCraft das árvores: `SkillRule`/`SkillRuleParser`/`SkillGate` (puros), `SkillRules` (lê o bloco `"tupinicraft"` dos JSON de skill), `SkillBackend` (fronteira com a biblioteca), `SkillTreeService` (compra e suspensão), `SkillTreeState`. |
| `compat.skilllib` | `SkillLibBackend`: único lugar que importa `cn.skilllib.*`; só carregado se o SkillLib estiver instalado. |
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
- **Árvores de habilidades (SkillLib):**
  - Quatro categorias raiz independentes: `tupinicraft:pessoais`, `tupinicraft:regionais` (subcategorias
    `regional_<macrorregião>`), `tupinicraft:partidarias` (uma subcategoria por partido) e `tupinicraft:cargos`
    (subcategorias `cargo_<cargo>`). Arquivos em `data/tupinicraft/skilllib/categories|skills/*.json`.
  - **Um arquivo por skill**: o formato do SkillLib + um bloco `"tupinicraft"` com as regras do TupiniCraft
    (`arvore`: pessoal|regional|partidaria|cargo; `regiao`; `partido`; `cargo`; `custo` em reais; `pontos` do
    cargo; `xp_min`). O SkillLib ignora o bloco; o TupiniCraft lê o mesmo arquivo (`SkillRules`).
  - Donos dos dados: **SkillLib** = definição, interface, desbloqueio, ativação, nível e sincronização dessas
    informações (attachment dele). **TupiniCraft** = dinheiro, filiação, mandato, pontos por cargo, custo e a
    decisão de quem pode comprar e manter cada efeito. Não duplique o estado de desbloqueio.
  - Compra só pelo servidor (`/brasil skills comprar`), que valida, cobra e chama `SkillLibAPI.unlockSkill`.
    Skills pessoais, regionais e partidárias custam dinheiro; as de cargo custam pontos **daquele cargo**
    (`OfficeProgress.skillPoints`). XP político pode ser exigido, mas **nunca é consumido**.
  - A cada segundo o `SkillTreeService` desativa efeitos cujo requisito deixou de valer (região, partido,
    mandato vigente) e os religa quando o requisito volta (`SkillTreeState.suspended`). O progresso nunca é apagado.
  - Limitações do SkillLib 1.4.0: a interface só liga/desliga skills já desbloqueadas (não compra); não há
    eventos para vetar a ativação nem para esconder categorias por jogador (todas aparecem; o bloqueio é no
    servidor); não há moedas nem custos nativos; o README cita operações antigas: em `skilllib:attribute_modifier`
    use `add_value`, `add_multiplied_base` ou `add_multiplied_total`. Mantenha `resetOnDeath=false` no
    `skilllib-server.toml`, senão o SkillLib apaga as skills na morte. Atributos 1.21.1: `minecraft:generic.*`.
- **Economia (contrato, ainda sem fontes de renda):** a única moeda é `Wallet` (attachment, por UUID, copiado na
  morte, alterado só por `WalletService`). Skills e a futura economia (Pix fictício etc.) devem usar só ela.
  Hoje o saldo só muda por `/brasil admin teste dinheiro`.
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
- **Como adicionar uma skill:** crie `data/tupinicraft/skilllib/skills/<id>.json` no formato do SkillLib, com
  `category` numa subcategoria existente e o bloco `"tupinicraft"`; rode `./gradlew build` (o `rulesTest` valida os
  arquivos) e teste com `-PwithSkillLib`.
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
  invocado no servidor dedicado e, numa terceira inicialização com `-PwithSkillLib`, o carregamento das skills.
- Fluxos multiplayer (candidatura, urna, HUD) exigem teste manual com 2+ clientes (`runClient` + servidor).
