# Handoff — Etapa 4.8: Finisher Engine 2.0 (vários rigs) e Rimulib

Etapa de **fundação**. Nenhum mob, finalização, animação, perk, chefe, dimensão, portal, recompensa ou efeito de
tela novo. Specialities, traços, necessidades, criador, roleta e Skill Trees não mudaram. `mod_version` continua
`0.5.7` (sem release).

## 1. Rimulib 1.3.0

| Item | Valor |
|---|---|
| Versão | **1.3.0** (NeoForge; o mesmo jar traz 1.20.1 e 1.21.1) |
| Arquivo oficial | CurseForge, projeto `1753907`, arquivo `9676532` (<https://www.curseforge.com/minecraft/mc-mods/rimulib/files/9676532>) |
| Pacotes usados | `cyou.rimuru.rimulib.mc1211.api` (comum) e, no futuro, `cyou.rimuru.rimulib.mc1211.client.api` (só cliente) |
| Lado | cliente **e** servidor (`side="BOTH"`), como GeckoLib e SmartBrainLib |
| Embutido no jar? | **Não.** O jogador instala `rimulib-...jar` na pasta `mods` ao lado do RespectCraft |

**Configuração.** O autor não publica em Maven público (a documentação manda compilar contra o jar). Para não
versionar um jar "All Rights Reserved" no repositório, o build baixa o **mesmo arquivo oficial** pelo CurseMaven
(espelho dos arquivos do CurseForge):

- `gradle.properties`: `rimulib_version=1.3.0`, `rimulib_curse_project=1753907`, `rimulib_curse_file=9676532`.
- `build.gradle`: repositório `https://cursemaven.com` em `exclusiveContent` (só o grupo `curse.maven`) e
  `implementation "curse.maven:rimulib-1753907:9676532"`. `implementation` sem `jarJar` = não embute.
- `neoforge.mods.toml` (template): `modId="rimulib"`, `type="required"`, `versionRange="[1.3.0,2.0)"`,
  `ordering="AFTER"`, `side="BOTH"` (o `type` é o pedido pela documentação para NeoForge 1.21.1).
- Para atualizar: troque os três valores em `gradle.properties` (o número do arquivo vem da URL do CurseForge).

**Inicialização.** Conferida no jar instalado (`javap`), não suposta: `RimulibApi.connect(String, Class, String)`
existe e devolve `cyou.rimuru.rimulib.mc1211.RimuMod`. Fica em `RespectCraft.RIMULIB` (campo estático da classe
principal, como pede a documentação), com mínimo `RespectCraft.RIMULIB_MINIMUM = "1.3.0"` (Rimulib mais antigo
falha na carga com mensagem clara). `connect` é Java puro (registro + pasta de config): seguro no servidor dedicado.
O log de carga mostra `RespectCraft connected to Rimulib 1.3.0 (API 1.3.0, loader neoforge)`. Nada de mixin ou
chamada extra: o Rimulib carrega os próprios mixins e sistemas.

### Como usar o Rimulib para efeitos visuais futuros (não implementado nesta etapa)

- **Efeitos de tela** (`FxApi`, só cliente): crie um estágio uma vez na inicialização do cliente,
  `FxApi.Stage STAGE = FxApi.stage(RespectCraft.MOD_ID)`, dentro de `com.respectcraft.client`. Um efeito é um
  `FxApi.Layer` (`frame -> { frame.pass("nome", u -> u.f1("Strength", x)); frame.screen().shake = ...; }`)
  adicionado com `STAGE.add(layer)` e removido com `STAGE.remove(layer)` quando acaba. Shaders em
  `assets/respectcraft/shaders/fx/<nome>.fsh` (sem `#version`; o Rimulib injeta `common.glsl`).
- **Quem dispara:** o servidor decide (como hoje com eventos temporizados de animação: `EVENT_CAMERA_SHAKE`,
  `EVENT_CAMERA_FOV`) e o cliente só reage ao estado sincronizado. Caminho natural: um novo tipo de evento temporizado
  em `PlayerAnimationDef` tratado no cliente que adiciona/remove a camada.
- **Separação de lado:** qualquer `cyou.rimuru.rimulib.mc1211.client.api.*` só em `com.respectcraft.client`
  (o teste de fumaça falha se uma classe comum importar). NeoForge não tem source set de cliente separado.
- **Não substitua** a câmera cinematográfica (`CinematicCamera`) nem o GeckoLib: o Rimulib complementa.
- Outras APIs do 1.3.0 (`CarveApi`, `SkillApi`, `NetApi`, `FilmApi`, `MenuApi`, `UiApi`) existem, mas não foram
  avaliadas para o RespectCraft. Antes de usar qualquer método, confira a assinatura no jar instalado; só
  `cyou.rimuru.rimulib.mc1211.api` e `...mc1211.client.api` são API pública.

## 2. Finisher Engine 2.0 — participantes com rigs diferentes

A mudança é a menor possível sobre o motor existente (`Finishers`/`FinisherDef` + sessão em
`testgame.FinisherTestMechanic`). Não há controlador universal: cada participante toca o próprio clip no
próprio rig.

| Tipo novo | O que é |
|---|---|
| `finisher.FinisherRole` | `ATTACKER` / `VICTIM` |
| `finisher.EntityFinisherClip` | clip de um **tipo de entidade** num papel: controlador GeckoLib, nome da animação gatilho, duração, `Alignment` (distância, deslocamento lateral, giro do modelo) |
| `FinisherDef.entityClips` | lista de `EntityFinisherClip` (vazia nas 3 finalizações atuais); `withEntityClip(...)`, `entityClip(type, role)`, `playerClip(role)` |
| `finisher.FinisherParticipants` | regras: `Kind` (PLAYER/MOB/BOSS), `Interaction`, `resolve(...)`, `stageDistance`, `stage`, `alignment`, `yawOffset`; palco por tipo de entidade (`registerStaging`/`staging`) |
| `Finishers.LEAO_DO_FIM_STAGING` | palco do Leão do Fim: 1,9 blocos (a cabeça passa da caixa de colisão; medido no `.geo.json`) |
| `finisher.FinisherSessionEvent` | `Completed` / `Aborted(reason)`, postados uma vez por sessão no servidor |
| `finisher.BossDefeats`, `finisher.BossDefeatEvent` | caminho preparado para recompensas de chefe (seção 5) |

**O que cada participante expõe/resolve** (`FinisherParticipants.resolve(def, role, entity)`):

- **Tipo de entidade e tipo de participante:** `typeId(entity)`, `kindOf(entity)` — jogador; chefe = tag comum
  `c:bosses` da NeoForge (`Tags.EntityTypes.BOSSES`, sem lista própria); o resto é mob.
- **Clip + controlador compatível:** jogador → `PlayerClip` (`PlayerAnimationDef` do papel, rig do jogador via
  `PlayerAnimationService`); entidade com clip → `EntityClip` (`GeoEntity.triggerAnim(controller, animation)` no
  controlador da própria entidade); entidade sem clip → `Hold` (fica parada com as animações normais dela, como antes).
- **Papel:** `FinisherRole`.
- **Posição/orientação inicial:** vítima de frente para o executor a `stageDistance` — 1,15 entre jogadores (igual a
  antes); com rig mais largo, `(larguras/2) + 0,25` para as caixas não se sobreporem; ou a distância/lateral fixas de
  um `Alignment`. Prioridade: alinhamento do clip da entidade → palco do tipo de entidade
  (`FinisherParticipants.registerStaging`, para modelos que passam da caixa de colisão) → automático. `yawOffset`
  gira só a entidade cujo modelo não olha para +Z.
- **Leão do Fim:** a cabeça vai a ~1,41 bloco à frente do centro (caixa de 1,4 de largura). No teste em cliente real
  com o automático (1,25) o jogador ficava dentro da juba; com `LEAO_DO_FIM_STAGING` (1,9) fica diante da cabeça.
  Mob novo cujo modelo passa da caixa: registre o palco dele em `Finishers` do mesmo jeito.
- **Duração:** a da cena; clip de entidade com duração diferente é recusado no registro (regra "mesmo comprimento").
- **Cancelamento e limpeza:** ver seção 3.

**Interações** (`Interaction`): `PLAYER_VS_PLAYER`, `PLAYER_VS_MOB`, `PLAYER_VS_BOSS`, `MOB_VS_PLAYER`,
`BOSS_VS_PLAYER`. `PLAYER_VS_BOSS` entrou porque a "derrota de chefe por finalização" precisa do jogador como
executor. **Sem jogador (mob × mob, chefe × mob…) a cena é recusada** — antes, `/respectcraft admin finisher` entre
dois mobs criava uma sessão que nunca avançava (só o tick do jogador processa a sessão) e prendia os dois mobs.

**Jogador × jogador não mudou:** mesmos clips, `playTogether`, mesma distância (1,15) e giro, mesma câmera, mesmo
contador. Coberto pelo `SelfTest` para as três finalizações.

### Como registrar uma finalização para um mob custom

1. No mob (`GeoEntity`), registre a animação gatilho no controlador dele, como o Leão já faz:
   `new AnimationController<>(this, "body", 0, ...).triggerableAnim("finisher_takedown", RawAnimation.begin().thenPlay("animation.<mob>.finisher_takedown"))`.
2. No `.animation.json` do mob (`assets/respectcraft/animations/entity/<mob>.animation.json`), crie o clip com a
   **mesma duração da cena** (ex.: 60 ticks = 3 s para `takedown`; 140 para `combo`), deslocamentos que voltam a zero.
3. Em `Finishers`, acrescente o clip à definição antes do `register`:

```java
public static final FinisherDef TAKEDOWN = register(new FinisherDef(TAKEDOWN_ID, ..., null, 3)
        .withEntityClip(new EntityFinisherClip(RespectCraft.id("meu_mob"), FinisherRole.ATTACKER,
                "body", "finisher_takedown", 60))
        .withEntityClip(new EntityFinisherClip(RespectCraft.id("meu_mob"), FinisherRole.VICTIM,
                "body", "finisher_takedown_victim", 60,
                new EntityFinisherClip.Alignment(Double.NaN, 0.0, 0F))));   // NaN = distância automática
```

   Finalização **nova** exige pedido (esta etapa não cria nenhuma). A vítima/executor jogador continua usando os
   clips de jogador da mesma definição; o servidor toca os dois no mesmo tick.
4. Rode `/respectcraft selftest` (valida duração, repetição, controlador) e teste com
   `/respectcraft admin finisher <executor> <alvo> <id>`.

**Clips por entidade:** um `EntityFinisherClip` por (tipo de entidade, papel) em cada finalização. Ossos são os do
`.geo.json` do próprio mob; nada aqui conhece ossos de jogador. O servidor nunca lê `.animation.json` (são recursos
do cliente): se o nome do clip não existir no arquivo, o GeckoLib do cliente avisa no log e o controlador continua
no estado normal (o mob fica como `Hold`); a cena segue e termina no tempo do servidor.

## 3. Sincronização, cancelamento e limpeza

- **Servidor autoritativo e início único:** jogadores via `PlayerAnimationService` (`PlayerAnimationState.at` = tick
  do servidor) e entidades via `triggerAnim` no **mesmo tick**; o GeckoLib manda o gatilho a quem vê a entidade.
- **Recusa antes de mexer em algo:** sem jogador, clip de jogador não registrado ou clip de entidade em quem não é
  `GeoEntity` → nada é movido/tocado; log `Finisher ... refused: <motivo>` e o comando responde
  `Could not start (...)`.
- **Cancelamento** (regra pura `FinisherRules.abortReason`): participante morto, removido (despawn/descarregado),
  desconectado, em outra dimensão ou a mais de 4 blocos; mais os caminhos de antes (dano, Shift da vítima,
  `/stopitstopnow`, morte, logout).
- **Limpeza:** `release` remove a sessão dos dois, para o clip de entidade (`stopTriggeredAnim`, inclusive no fim
  normal, para clips "play and hold") e avisa os clientes; `abort` também para os clips de jogador.
- **Nada fica congelado:** o mob é segurado só reaplicando posição, giro e navegação a cada tick; **nada é gravado na
  entidade** (sem `NoAI`). Se o servidor cair no meio, o mob volta ao normal ao carregar.
- **IA do parceiro mob:** o golpe automático do próprio parceiro mob é anulado sem cancelar a cena (antes, o mob
  executor batia na vítima e a cena se cancelava). Qualquer outro dano continua cancelando, sem aplicar o dano.

## 4. Contadores

- Sem mudança de regra: `+1` só para **vítima jogador** em `complete` (`abort` nunca conta). A regra agora é explícita
  em `FinisherRules.countsDefeat(kind)` (só `PLAYER`). Jogador vencido por mob continua contando, como antes.
- Mob e chefe **nunca** recebem contador, estatística, plaqueta ou dado persistente de humilhação.

## 5. Recompensas de chefe — só preparação

Nada é concedido. Não há ouvinte de `BossDefeatEvent` no mod.

- `FinisherSessionEvent.Completed` com `PLAYER_VS_BOSS` → `BossDefeats.onFinisherCompleted` grava no próprio chefe
  (`persistentData`, chave `respectcraft_boss_finisher_credit`: jogador, finalização, tick).
- `LivingDeathEvent` (prioridade `LOWEST`, só se não cancelada, só servidor, só `c:bosses`) →
  `BossDefeats.decide(...)`:
  - **FINISHER**: morte até `BossDefeats.FINISHER_CREDIT_TICKS` (100, provisório) depois da finalização qualificante;
    creditado = executor da finalização.
  - **NORMAL**: qualquer outra morte; creditado = jogador que matou (ou `getKillCredit()`), se houver.
- **Sem disparo duplo:** a chave `respectcraft_boss_defeat_posted` no chefe garante no máximo um
  `BossDefeatEvent` por entidade (coberto pelo `SelfTest`). As finalizações atuais não matam; o caminho já distingue
  as duas derrotas para quando a lista de chefes e as regras chegarem.
- **Pontos de integração futuros:** ouvir `BossDefeatEvent` (método, jogador creditado, finalização) para conceder
  habilidade/conquista; restringir "finalização qualificante" (hoje: qualquer finalização concluída contra chefe) com
  um campo na definição, se a regra pedir; ajustar a janela. O dragão do End vanilla não passa pelo `die()` comum;
  chefes futuros do mod passam.

## 6. Planos futuros (registro apenas; nada implementado)

- Novos efeitos visuais (via Rimulib `FxApi`, seção 1) e novos efeitos de status.
- Novas dimensões (`dimension/` continua só com `LEIA-ME.md`).
- Portais que só abrem depois de eventos específicos do jogo.
- Progressão do mundo ligada a chefes (provável gatilho: `BossDefeatEvent`).

Nenhum registry, portal, dimensão, geração de mundo ou sistema de eventos de mundo foi criado.

## 7. O que dá para testar hoje × o que falta

**Hoje:** Rimulib carregando no servidor dedicado e no cliente; jogador × jogador como antes; jogador × Leão do Fim
e Leão do Fim × jogador com o Leão **parado** (`Hold`) no próprio rig, cancelamento/limpeza e recusa de mob × mob.

**Falta (assets):** nenhum mob tem clip de finalização. O Leão do Fim só tem `idle`, `walk`, `bite`, `roar`, `hit`,
`charge_attack`, `release_attack`, `sigil_*` — nenhum com a duração de uma cena; não foram reaproveitados nem
inventados. Um teste visual de **mob animado** numa finalização exige o modelo/clip do mob (seção 2, passos 1–2).
Chefes: não existem no mod; o caminho de recompensa só foi validado pelas regras puras.

## 8. Testes e resultados

Rodados nesta sessão de nuvem (o `maven.neoforged.net`, a Mojang, o CurseMaven e a documentação do Rimulib estavam
liberados), com o código final:

| Comando | Resultado |
|---|---|
| `./gradlew build` | OK (`build/libs/respectcraft-0.5.7.jar`, 0 entradas `cyou/rimuru` no jar) |
| `bash .github/scripts/server-smoke-test.sh` | `Teste de fumaça do servidor dedicado: OK` |
| `python3 tools/player_anim/bbsync.py` / `test_export.py` | em sincronia / 0 falhas |

**Testes focados novos.**

| Pedido | Onde | O que confere |
|---|---|---|
| Rimulib carregando | teste de fumaça + `SelfTest.rimulib` | log `RespectCraft connected to Rimulib 1.3.`; `RimulibApi.isLoaded`, versão ≥ 1.3.0, conexão `respectcraft` |
| Rimulib não embutido | teste de fumaça | o jar não tem `cyou/rimuru` nem `META-INF/jarjar` |
| Sem classe de cliente no servidor | teste de fumaça | nada fora de `com.respectcraft.client` importa `net.minecraft.client.*` ou `mc1211.client.*`; sem `NoClassDefFoundError`/`invalid dist` no servidor dedicado |
| Compatibilidade jogador × jogador | `SelfTest.multiRig` | as 3 finalizações resolvem os mesmos clips; distância 1,15; contador do jogador |
| Validação por entidade | `SelfTest.multiRig` | clip vale só no próprio tipo/papel; sem `GeoEntity` recusa; duração diferente/repetido/controlador vazio/alinhamento absurdo recusados; palco do Leão |
| Animação ausente | `SelfTest.multiRig` | clip de jogador não registrado recusa a cena; mob sem clip fica parado (`Hold`) |
| Cancelamento e limpeza | `SelfTest.multiRig` + teste de fumaça + cliente real | regra `abortReason`; mob × mob recusado num servidor dedicado de verdade (`... has no player participant`) |
| Sem contador de mob | `SelfTest.multiRig` + cliente real | `countsDefeat` só `PLAYER` |
| Sem disparo duplo de chefe | `SelfTest.bossDefeats` | janela de crédito, derrota comum, sem jogador, tempo inválido, 3 tentativas = 1 anúncio |

**Cliente real** (Xvfb, servidor dedicado de desenvolvimento, um cliente "Juca", `tools/visual-test/helpers.sh`;
roteiro em `docs/evidencias/etapa4_8/cenario_cliente_real.sh`), Leão do Fim como mob GeckoLib, **sem clip de mob**
(o Leão fica parado no próprio rig; o jogador toca o clip dele):

1. Jogador × Leão (`takedown`): começou, câmera cinematográfica, impactos; terminou com
   `Test combat finisher complete.` e **nenhuma** mensagem/contador de derrota para o mob.
2. Jogador × Leão (`combo`), Leão levado para o Nether no meio da cena (remoção): `Finisher cancelled: participant
   unavailable`; `anim stop @a` = 0 (nada ficou tocando).
3. Jogador × Leão, `/kill` no Leão no meio da cena: `Finisher cancelled: interrupted by damage` (dano anulado, como
   antes); a mesma dupla começou outra cena logo depois (nenhuma sessão presa).
4. Leão × jogador (`takedown`): jogador toca o clip de vítima, termina caído com `Special TEST defeat recorded (1)`
   — regra antiga preservada para vítima jogador.
5. Jogador expulso (`kick`) no meio de uma cena com o Leão: ao voltar, o Leão já tinha andado (não ficou congelado) e
   uma nova cena com ele começou de primeira.
6. O teste revelou o problema de palco do Leão (com 1,25 automático o jogador ficava dentro da juba,
   `01_jogador_x_leao_auto_1_25_sobreposto.png`); corrigido com `LEAO_DO_FIM_STAGING` = 1,9
   (`02_...`, `03_...`). `04_...` mostra o fim do Leão × jogador.

**Não testado** (sem asset ou fora do escopo): mob **animado** numa finalização (não há clip); chefe real (o mod não
tem chefes); jogador × jogador com dois clientes (código do caminho não mudou; coberto pelo `SelfTest`). Não foi
afirmado nenhum teste visual de finalização animada de mob.

## 9. Arquivos

- Dependência: `gradle.properties`, `build.gradle`, `src/main/templates/META-INF/neoforge.mods.toml`, `RespectCraft`.
- Motor: `finisher/FinisherRole`, `finisher/EntityFinisherClip`, `finisher/FinisherParticipants`,
  `finisher/FinisherDef`, `finisher/FinisherSessionEvent`, `finisher/BossDefeats`, `finisher/BossDefeatEvent`,
  `testgame/FinisherTestMechanic`, `testgame/FinisherRules`, `command/RespectCraftCommands` (mensagem de recusa).
- Testes: `selftest/SelfTest` (`multiRig`, `bossDefeats`, `rimulib`), `.github/scripts/server-smoke-test.sh`.
- Docs: este handoff, `docs/handoffs/README.md`, `README.md`, `CLAUDE.md`, `AGENTS.md`; capturas em
  `docs/evidencias/etapa4_8/`.

**Saves:** nenhum id persistente mudou. Chaves novas só em `persistentData` de chefes
(`respectcraft_boss_finisher_credit`, `respectcraft_boss_defeat_posted`); não renomear. Recomende backup antes de
atualizar, como sempre.
