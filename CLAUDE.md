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
  IDs de território (`tupinicraft:brasil`, `tupinicraft:es`, `tupinicraft:vitoria`…), caminhos de `ConfigKeys`
  (usados nos overrides), nomes de campos NBT/Codec, SavedData `tupinicraft_election`, `tupinicraft_mandates`,
  `tupinicraft_config_overrides` e o attachment `tupinicraft:political_data`.

## Pacotes

| Pacote | Responsabilidade |
|---|---|
| `origin` | `RegionalOrigin` (27 UFs: nome, sigla, gentílico, macrorregião), `Macroregion`, `OriginService` (sorteio/atribuição). |
| `player` | `PoliticalData` (record imutável + Codec + StreamCodec), `PoliticalDataService` (único ponto de leitura/escrita + sync), eventos de jogador. |
| `politics` | `PoliticalOffice`: ordem dos cargos, jurisdição (país/estado/município) e **valores padrão** do TOML. |
| `config` | `ConfigKeys` (tabela única de chaves, limites e padrões), `BrasilServerConfig` (TOML SERVER gerado da tabela), `ConfigOverridesSavedData` (overrides do mundo), `ServerSettings` (**único ponto de leitura** dos valores efetivos). |
| `territory` | `Territories`: catálogo imutável (Brasil, 27 UFs, 27 capitais) com IDs estáveis e domicílio eleitoral. |
| `mandate` | Cadeiras (`SeatKey`), mandatos, `MandateRegistry` (regra de 1 ocupante), `MandateQueries` (API de consulta), `PoliticalMapSnapshot`, `MandateSavedData`, `MandateService` (posse, expiração, pontos de habilidade). |
| `election` | Máquina de estados, calendários, `ElectionRules` (regras congeladas por eleição), `ElectionSavedData`, `ElectionManager` (regras no servidor), `ElectionTally` e `NpcCandidateFactory` (puros), item da urna. |
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
- **Configuração (sem números mágicos):** todo valor de gameplay ajustável é uma chave em `ConfigKeys` e é lido
  por `ServerSettings`. Prioridade: **1) override do mundo** (`/brasil admin config set`, salvo em
  `ConfigOverridesSavedData`) → **2) TOML SERVER** (`config/tupinicraft-server.toml`). `reset` remove o override.
  Para uma chave nova: adicione em `ConfigKeys` (com limites) e leia por `ServerSettings`; o TOML e os comandos
  se atualizam sozinhos. Pares mínimo/máximo são registrados com `pair(...)` e validados no `set`.
- **Mudanças com eventos em andamento:** uma eleição congela suas regras (`ElectionRules`) e cada disputa congela
  pontuação mínima e faixa de NPCs na criação; votos guardam o peso no momento do voto. Mandatos guardam o fim
  previsto na posse. Mudanças de configuração valem só para as próximas eleições/mandatos; o intervalo dos
  calendários vale a partir do próximo ciclo.
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
- **Comandos admin** ficam sob `/brasil admin` com `hasPermission(2)`. Validar entradas e retornar mensagens claras.
- **Extensões futuras:** benefícios por origem entram em `OriginService.applyOriginEffects` (hoje vazio).
  Ainda fora do escopo: partidos, skill trees, buffs de origem, dimensões, NPCs, economia, missões.

## Testes

- Regras puras (`politics`, `origin`, `territory`, `config/ConfigKey(s)`, `mandate` exceto `*SavedData`/`MandateService`,
  `ElectionTally`, `ElectionRules`, `ElectionRace`, `NpcCandidateFactory`) não importam classes do Minecraft e são
  cobertas por `RulesSelfTest`. Ao mudar fórmulas, chaves ou territórios, atualize esse teste.
- O teste de fumaça do CI cobre: override persistente após reinício, reset para o TOML, rejeição de valores
  inválidos, aldeões completando disputas, prazo congelado de eleição ativa e consulta de mandatos.
- Fluxos multiplayer (candidatura, urna, HUD) exigem teste manual com 2+ clientes (`runClient` + servidor).
