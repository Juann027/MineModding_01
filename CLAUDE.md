# CLAUDE.md — TupiniCraft

Mod brasileiro de sátira política para **Minecraft 1.21.1 / NeoForge 21.1.x / Java 21**.
Base: MDK oficial `NeoForgeMDKs/MDK-1.21.1-ModDevGradle` (plugin `net.neoforged.moddev`).

## Stack e limites

- Minecraft `1.21.1`, NeoForge `21.1.265` (`gradle.properties`), Parchment `2024.11.17`.
- Java 21 (toolchain no `build.gradle`). Gradle wrapper 9.2.1.
- **Não usar** APIs/tutoriais de Forge 1.20.x, Fabric ou NeoForge 21.2+ (ex.: `RegisterClientPayloadHandlersEvent`,
  sync automático de attachments, `SavedDataType`, `ResourceKey`-based items do 1.21.2+). Confira sempre a assinatura 1.21.1.
- **Sem dependências externas novas** (nem JUnit). Testes de regra puros rodam via `rulesTest` (JavaExec).
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
  nomes de campos NBT/Codec, nome do SavedData `tupinicraft_election`, attachment `tupinicraft:political_data`.

## Pacotes

| Pacote | Responsabilidade |
|---|---|
| `origin` | `RegionalOrigin` (27 UFs: nome, sigla, gentílico, macrorregião), `Macroregion`, `OriginService` (sorteio/atribuição). |
| `player` | `PoliticalData` (record imutável + Codec + StreamCodec), `PoliticalDataService` (único ponto de leitura/escrita + sync), eventos de jogador. |
| `politics` | `PoliticalOffice`: ordem dos cargos e **fonte única** dos requisitos de XP/popularidade. |
| `election` | Máquina de estados, `ElectionSavedData` (persistência), `ElectionManager` (regras no servidor), `ElectionTally` (cálculo puro), item da urna. |
| `network` | Payloads (`CustomPacketPayload` + `StreamCodec`) e registro em `RegisterPayloadHandlersEvent`. |
| `client` | Entrada `@Mod(dist = CLIENT)`, caches visuais, camadas de HUD, faixa de avisos, tela da urna. |
| `command` | Brigadier (`/brasil ...`) registrado em `RegisterCommandsEvent`. |
| `registry` | `DeferredRegister` de itens e attachment types. |

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
- **Balanceamento:** requisitos de cargo só em `PoliticalOffice`; fórmulas de voto em `ElectionTally`
  (`voteWeight`, `VOTE_SCORE_MULTIPLIER`, `WIN_MARGIN`); durações e eleições automáticas em `TupiniConfig`
  (`serverconfig/tupinicraft-server.toml`).
- **Textos:** português do Brasil, com `Component` do Minecraft. Anúncios da eleição vão para a faixa na tela
  (`AnnouncementPayload` → `AnnouncementOverlay`), não para o chat. Respostas de comando usam `sendSuccess/sendFailure`.
  Votos são secretos: não repasse o resultado do voto aos admins (`notifyAdmins = false`).
- **HUD:** apenas `RegisterGuiLayersEvent` + `GuiGraphics` (sem texturas de UI). Respeitar ícones de efeitos (topo
  direito), F3 e `gui.leftHeight/rightHeight` (a barra de XP político empurra corações/fome para cima).
- **Comandos admin** ficam sob `/brasil admin` com `hasPermission(2)`. Validar entradas e retornar mensagens claras.
- **Extensões futuras:** benefícios por origem entram em `OriginService.applyOriginEffects` (hoje vazio).
  Ainda fora do escopo: partidos, skill trees, buffs de origem, dimensões, NPCs, economia, missões.

## Testes

- Regras puras (`politics`, `origin`, `ElectionTally`, `ElectionRace`) não importam classes do Minecraft e são
  cobertas por `RulesSelfTest`. Ao mudar fórmulas/requisitos, atualize esse teste.
- Fluxos multiplayer (candidatura, urna, HUD) exigem teste manual com 2+ clientes (`runClient` + servidor).
