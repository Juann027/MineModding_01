# RespectCraft — Roadmap

Direção futura do mod. **Nada aqui está implementado**, exceto o que está marcado como implementado (Etapa 4.12:
seções 1 e 2, base) e nada deve ser antecipado (sem classes vazias, registries
provisórios, assets temporários ou abstrações especulativas). A ordem e a numeração das etapas serão decididas depois,
pelas dependências técnicas. Cada item, quando virar etapa, ganha handoff próprio em `docs/handoffs/`.

Base de partida: a branch de consolidação (Etapas 4.10.1, 4.10.2, 4.11 finisher/chefes, 4.11 avisos/caça a
recompensa e Calamidade_Awakening 0.14.0). Estado do código: `CLAUDE.md`.

## Diretrizes de arquitetura

- Reutilizar infraestrutura que já funciona antes de criar sistema novo; estender o dono existente (`CLAUDE.md`,
  "Um dono por dado").
- Separar conteúdo (dados/registros), assets, gameplay, animações, VFX e interface.
- **Calamidade_Awakening** é o motor de efeitos cinematográficos (pontes únicas como `fx.FinisherFx`/`notice.NoticeFx`).
- **GeckoLib 4.9.3** para geometria e animação; **Animation Manager** (`PlayerAnimationService`) e **Finisher Engine**
  continuam os únicos donos do que já controlam — nada de segunda engine de animação.
- Servidor autoritativo sobre gameplay; o cliente envia intenção e exibe caches.
- Multiplayer desde o primeiro commit (login, respawn, dimensão, `StartTracking`, mudança de valor).
- Sem sistemas redundantes; extensível para novos mobs, chefes, habilidades e conteúdos.
- Assets produzidos **externamente no Blockbench** pelo dono; o Claude faz infraestrutura, contratos, templates
  técnicos, validação, importação, comportamento, sincronização e integração.
- Nada de abstração sem caso de uso real: o primeiro consumidor concreto define o formato.

## 1. Custom Rig Framework — **implementado na Etapa 4.12** (base)

Feito (handoff `HANDOFF_STAGE4_12_CUSTOM_RIG_FRAMEWORK.md`): pacote `rig` com famílias por manifesto JSON
(`RigDefinition`, `assets/<ns>/rigs/<id>.rig.json`), associação entidade → família (`RigBinding`,
`rigs/entities/<entidade>.json`), registro `CustomRigRegistry`, capacidades (`RigCapabilities`), sockets em pose de
repouso no servidor (`RigSockets`), partes ocultáveis/destacáveis com estado visual sincronizado e restaurado no fim da
cena (`RigVisuals`), clips por família e requisitos de participante na `FinisherDef` (`FinisherRigs`), resolução
explícito → override do binding → família → parado, elegibilidade única (`FinisherEligibility` +
`FinisherTestMechanic.check`), renderer genérico (`client.rig.RigGeoRenderer`), família de referência
`respectcraft:fruit_humanoid_v1` e dois manequins de teste (`dev_rig_mannequin_a/b`). Comando `/respectcraft rig`.

Continua futuro: **sincronização temporal dos clips para observadores tardios** (quem começa a rastrear uma entidade
no meio de uma finalização hoje recebe o clip desde o início, porque o gatilho do GeckoLib 4.9.3 não aceita
deslocamento; o ideal é entrar no ponto certo da cena); outras famílias (quadrúpede, voadora, mecânica, serpente — o formato já aceita anatomia arbitrária),
pose animada no servidor (o GeckoLib 4.9.3 não tem), IK, retargeting, gore/fragmentos com física, extensão por addons
fora do jar do mod (hoje os manifestos são lidos do jar).

## 2. Pipeline de produção externa (Blockbench) — **kit entregue na Etapa 4.12**

Feito: `rigkit/` (README com guia de produção no Blockbench Web e de integração, `CONTRACT.md`, template
`.bbmodel` editável com manequim, sockets e animações compartilhadas, referência exportada, exemplo de binding,
`BBFORGE_PROMPT.md`), gerador protegido `tools/rigkit/make_assets.py`, validador `tools/rigkit/validate_rig.py`
(ERROR/WARN/OK/CHECK), testes `tools/rigkit/test_rigkit.py` e ZIP independente (`tools/rigkit/package_kit.py`).
Template aberto e testado no Blockbench Web pelo dono; primeiro modelo artístico integrado na Etapa 4.13.1
(morango). Próximo: `strawberry.animation.json` + `.bbmodel` atualizado, produzidos externamente pelo dono.

### 2.1 Ferramenta de autoria de finishers (PlayerRig + CustomRig) — **feito na Etapa 4.13.2 (Finisher Scene Authoring Kit)**

Uma **cena de referência do Blockbench** com o rig do jogador (`respectcraft_player_combat`) e um modelo de família
(CustomRig, ex.: morango) lado a lado, na distância/alinhamento de palco da finalização, para animar os dois
participantes juntos: ver contato, mãos nos sockets e tempos dos golpes; manter a mesma duração e os mesmos
marcadores de evento (`EVENT_HIT`/`EVENT_FALL`, `rig:`/`calamidade:` no timeline) nos dois; e **exportar cada clip
separadamente** para o destino de sempre — o do jogador para a biblioteca de combate (`tools/player_anim`, com a
proteção por hash e o `bbsync.flip`) e o do mob para o `<entidade>.animation.json` (ou a família), com o
`clip_overrides`/`FinisherRigs.Clip` correspondente — conferidos pelo `validate_rig.py` e pelo `bbsync.py`. Estende
o que existe (`respectcraft_combo_scene.bbmodel`, `tools/player_anim`, `tools/rigkit`); não cria engine nem formato
novo, e o jogo continua tocando um clip por participante pelo Finisher Engine.

Feito (4.13.2): `scenekit/` (templates jogador × morango e jogador × manequim da família, contrato, guias do
Blockbench Web e do Claude no Chrome, exemplos) e `tools/scenekit/` (`compose_scene.py`, `split_scene.py`,
`make_templates.py` protegido por hash, testes, conferência no Blockbench real no CI, ZIP). Os clips de jogador vão
para um arquivo próprio (a biblioteca de combate continua do `export.py`). Próximo: a primeira finalização definitiva
feita com o kit (pedido do dono), com o `clip_overrides` do morango.

## 3. AI Fruit Love Island Mobs

Frutas humanoides exageradas, expressivas, dramáticas e propositalmente irritantes, inspiradas nas novelinhas de
frutas geradas por IA (TikTok/Kwai; referência não é Italian Brainrot). Candidatas: banana, melancia, morango,
laranja, abacaxi, outras depois. Modelos e animações GeckoLib (feitos pelo dono), personalidades, expressões, danças,
provocações, música repetitiva espacializada presa à entidade (corte abrupto no impacto final como piada; som exato a
definir), finalizações agressivas com destruição estilizada (casca, polpa, sementes, suco, fragmentos, partes
destacáveis).

Programação futura: registro das entidades, associação à família de rig, importação/validação dos assets, IA e
comportamento, áudio ligado à entidade, partes destacáveis, destruição visual, finalizações e targeting,
sincronização, integração com Animation Manager, Finisher Engine e Calamidade. A família "fruta" é só **uma
implementação registrada** no Custom Rig Framework e serve de validação prática dele. Pronto desde a 4.12: o contrato
`respectcraft:fruit_humanoid_v1` (esqueleto, sockets, partes `shell`/`core`, animações compartilhadas de referência)
e o kit de produção. Etapa 4.13.1: morango `respectcraft:strawberry` (assets do artista, vítima). **Etapa 4.13.3:**
o morango é NPC de gameplay completo — spawn natural (plains/savanna), IA neutra com retaliação, XP, música própria
em loop (base reutilizável `entity.fruit.FruitMob` + `client.fruit.FruitMusicClient`, corte seco no impacto decisivo) e
cinco finalizações exclusivas (pool próprio). Próximos (não implementar sem pedido):

- animações próprias de locomoção, ataque e personalidade das frutas (produzidas externamente pelo dono);
- banana, melancia e abacaxi (estendem `FruitMob`: binding, atributos, som e pool próprios);
- **reprodução entre frutas (decisão de design do dono, 4.13.3):** as frutas humanoides poderão se reproduzir no futuro,
  inclusive entre espécies diferentes da família (morango × morango, morango × banana, banana × melancia, abacaxi ×
  morango...). Espécie resultante, condições, crescimento, filhotes e híbridos ficam para uma etapa futura — nada de
  reprodução, filhotes, híbridos ou infraestrutura especulativa antes disso;
- **Universal Fluid & Destruction FX Framework** no Calamidade_Awakening (suco, sementes e fragmentos hoje são
  partículas vanilla pelo `FinisherFx`);
- destruição avançada e partes destacáveis com física (hoje as partes `extra_*` só aparecem/somem, ainda presas).

## 4. Finisher Selection & Loadout

Três modos: **Random** (o sorteio de hoje entre elegíveis e desbloqueadas), **Preset** (equipar antes pelo menu
holográfico) e **Manual** (ao finalizar, carrossel/slides holográfico para escolher). Considerar previews, nome e
descrição, raridade, requisitos, compatibilidade com o alvo, bloqueadas × desbloqueadas, exclusivas de chefes e de
entidades/rigs (`FinisherTargeting`). O servidor revalida a elegibilidade na confirmação; abrir o seletor não pausa o
servidor nem dá invulnerabilidade. Reutiliza o menu holográfico (`HoloMenu`/`HoloWorldInput`) e o catálogo
`Finishers`.

**Política permanente de seleção (Etapa 4.13.3, `finisher.FinisherSelection`):** PLAYER = só finalizações comuns, com
desbloqueio (o seletor Random/Preset/Manual escolhe dentro delas; exclusivas de NPC nunca entram no loadout); BOSS = só
a exclusiva do chefe, nunca fallback; MOB/NPC = o servidor sorteia uma cena completa — pool próprio do tipo
(`FinisherTargeting.npcExclusive`, sem desbloqueio pessoal) ou, sem pool, as comuns compatíveis com mob; nada elegível =
recusa antes de mexer no mundo.

## 5. Unlock & Progression Framework

Infraestrutura comum de registro, desbloqueio, requisitos, persistência e sincronização de conteúdos especiais
(finalizações, poderes, montarias, invocações de chefes, aparências, outros). Fontes possíveis: chefes, missões, drops
raros, conquistas, Skill Trees, eventos, segredos, progressão dimensional. Não substitui sistemas de progressão que já
existem (ex. `SkillService`); cada categoria mantém as próprias regras de gameplay. O plano de desbloqueio da 4.10.1
(`HANDOFF_STAGE4_10_1_TRUE_EXECUTIONS.md`) é o ponto de partida para finalizações.

## 6. Active Ability System

Poderes usados fora de uma execução (ex.: Sword Slash, Energy Ball, área, projéteis, movimento, barreiras), ativados
por tecla/atalho/menu holográfico e desbloqueados por missões, chefes, drops ou progressão. Prever cooldown, custo,
targeting, requisitos, raridade, dano, animações, VFX, restrições por dimensão/situação e validação no servidor.
Reutiliza Animation Manager e Calamidade; ciclo de vida **independente** das sessões de finalização (não são
finalizações disfarçadas), compartilhando infraestrutura quando fizer sentido. `SkillEffect.Kind.VISUAL_FX` existe e
continua sem uso até lá.

## 7. Holographic Quick Menu

Menu holográfico de acesso rápido (radial, carrossel, painel compacto ou outro formato no visual do mod) para escolher
finalização, equipar/ativar poderes, invocar montarias, acessar desbloqueios e atalhos configuráveis. Reutiliza a
renderização, entrada, navegação e interação do menu K; sem UI paralela.

## 8. Mount Summoning

Montarias especiais de recompensas/drops raríssimos de chefes. Desbloqueio permanente e reutilizável; cada invocação
com duração limitada, cooldown, sumiço no fim do tempo, invocação pelo menu holográfico, entrada/saída cinematográfica.
Considerar combate, voo, dimensões, desconexão. Balanceamento a definir. Modelos feitos externamente.

## 9. Boss Summoning

Invocar chefes pelo menu holográfico com requisitos (chefes anteriores, dimensão, altar, itens/recursos, progressão,
eventos, condições individuais ou coletivas). Prever validação no servidor, sem invocação duplicada, cooldown,
restrição de posição, eventos de mundo, avisos (`HoloNotices.bossAppeared`/`worldEvent`) e progressão. Reutiliza
`boss.Bosses`/`BossProfile`, `BossDefeats` e os avisos. **Condição de invocar ≠ condição de executar** (`BossCondition`
só vale para a finalização exclusiva).

## 10. Holographic UI Phenomena — **ponto de extensão preparado na Etapa 4.14.1**

`client.holo.HoloPhenomena` (`Look`, `Modifier`, registro por id, globais) já é aplicado pela janela cinematográfica e
pelo rastreador de missões; avisos carregam ids em `HoloNotice.modifiers`. Nenhum fenômeno existe ainda. Planejado:
interferências, glitches, distorções, cores temporárias, aparência assombrada, "bateria fraca", informação
visualmente corrompida, mensagens misteriosas, reações a dimensões e chefes. **Separar sempre** o fenômeno puramente
estético (cliente, `HoloPhenomena`) do efeito real de gameplay (decidido no servidor e só então refletido na UI).

A interface holográfica reage a mundo, dimensões, eventos, chefes e segredos: cores temporárias, tom vermelho/escuro
em perigo, interferência, glitches, distorção, aparência assombrada, "bateria fraca", instabilidade em certas
dimensões, informação aparentemente corrompida, mensagens secretas, sons alterados, elementos revelados sob condição.
Infraestrutura de modificadores visuais e comportamentais aplicada **sobre** o menu existente (sem espalhar condições de
dimensão/chefe pelas classes da UI). Separar: efeito só estético × fenômeno que altera a informação mostrada ×
restrição real de gameplay (esta decidida no servidor). Considerar composição, prioridade, duração, limpeza,
performance e acessibilidade (respeitar `post.screenEffects()`/`flashAllowed()`/`motionEffects()` como os passes fx).

## 11. Party System

Grupos de jogadores: criar, convidar, aceitar/recusar, liderança, identificação, status e HP, marcadores,
comunicação, fogo amigo configurável, chefes, missões cooperativas, recompensas e progressão compartilhada quando
apropriado. Pode abrir caminho para habilidades e finalizações com vários participantes (hoje a sessão tem dois).
Regras a definir. Interage com a caça a recompensa (ex.: membro da party como alvo/caçador) — decidir na etapa.

## 12. Quest Engine — **implementado na Etapa 4.14** (base)

Missões por datapack (`docs/QUESTS.md`), seção Quests e rastreador. Próximos passos registrados abaixo (13–16).

## 13. Quest Portals

Algumas missões secretas permitirão **invocar fisicamente um portal pelo menu holográfico**, levando a dimensões, áreas
especiais, estruturas ou desafios. Prever condições de ativação, acesso, duração e encerramento, validação no servidor
e aviso `portal_available` (estilo já existe). Reutilizar `HoloNotices.portalOpened` e o Quest Engine (recompensa/
objetivo novos por tipo registrado). Não implementar sem pedido.

## 14. Multiplayer Quest Invasions

Missões que outros jogadores podem invadir (inspiração: Dark Souls, Deepwoken), possivelmente ligadas à Bounty Hunt
quando o jogador está sendo procurado. Sempre dependentes das regras da missão, do PvP/times e da configuração do
servidor. Sem networking ou sistemas especulativos de invasão até ser pedido.

## 15. Time Warp Dimension

**Base implementada na Etapa 4.15** (`HANDOFF_STAGE4_15_TIME_WARP.md`): dimensão `respectcraft:time_warp` independente
do Inverted Realm — oceano com ≈95% de água, ilhas raras, casas e assentamentos abandonados (sem habitantes), neblina
densa por estágio, viagem pelo relógio 3,33 : 1 com chegada segura e recuperação sem relógio, exposição individual em
quatro estágios (nunca mata), acontecimentos sonoros/visuais raros, avisos do Sistema pela Notification HUD V2 e a
missão secreta *Echoes of an Old World*.

Futuro (não implementar sem pedido):
- **Texturas antigas só no Time Warp** (Alpha/Beta): hoje é aproximação por cores de bioma, luz e neblina; trocar o
  atlas por dimensão exige mudança no renderer (ou pacote de recursos opcional do jogador).
- **Fenômenos holográficos** (item 10) dentro do Time Warp: os avisos já levam os modificadores `time_warp` e
  `phenomenon:*`; `client.holo.HoloPhenomena` é quem os desenharia.
- **Missões secretas avançadas** usando as conquistas ocultas já existentes (`explored_settlement`, `lingered`) e
  acontecimentos que só ocorrem em estágios altos; portais de missão (item 13) com destino ao Time Warp.
- **Acontecimentos desconhecidos** maiores (eventos de mundo compartilhados, mudanças físicas em construções), sempre
  sem confirmar a presença de alguém; nada de Herobrine/criatura observadora explícita sem decisão do dono.
- Relógio próprio/artefato com modelo artístico; portal físico; invasões multiplayer (item 14) partindo do Time Warp.

## 16. Advanced Progression

Integração futura das missões com: desbloqueio de finishers PvP (item 4), habilidades ativas (6), invocações e
montarias raras (8, 9), condições de aparecimento de chefes e chefes exclusivos, eventos especiais e Party System (11,
missões cooperativas e progresso compartilhado — hoje o progresso é sempre individual).

## Decisões do dono (consolidação)

- **Numeração "4.11":** mantida a histórica; as duas etapas são "4.11 (finisher)" e "4.11 (avisos)".
- **Recompensa × crédito de morte:** mantido o crédito vanilla (`getKillCredit`) em `BountyService.onDeath`, inclusive
  quando a morte final é `/kill` (também durante uma cena).
