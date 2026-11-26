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

Continua futuro: outras famílias (quadrúpede, voadora, mecânica, serpente — o formato já aceita anatomia arbitrária),
pose animada no servidor (o GeckoLib 4.9.3 não tem), IK, retargeting, gore/fragmentos com física, extensão por addons
fora do jar do mod (hoje os manifestos são lidos do jar).

## 2. Pipeline de produção externa (Blockbench) — **kit entregue na Etapa 4.12**

Feito: `rigkit/` (README com guia de produção no Blockbench Web e de integração, `CONTRACT.md`, template
`.bbmodel` editável com manequim, sockets e animações compartilhadas, referência exportada, exemplo de binding,
`BBFORGE_PROMPT.md`), gerador protegido `tools/rigkit/make_assets.py`, validador `tools/rigkit/validate_rig.py`
(ERROR/WARN/OK/CHECK), testes `tools/rigkit/test_rigkit.py` e ZIP independente (`tools/rigkit/package_kit.py`).
Falta: abrir o template no Blockbench Web de verdade (teste manual do dono) e os modelos artísticos.

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
e o kit de produção; nenhuma fruta, textura, animação artística ou finalização exclusiva existe ainda.

## 4. Finisher Selection & Loadout

Três modos: **Random** (o sorteio de hoje entre elegíveis e desbloqueadas), **Preset** (equipar antes pelo menu
holográfico) e **Manual** (ao finalizar, carrossel/slides holográfico para escolher). Considerar previews, nome e
descrição, raridade, requisitos, compatibilidade com o alvo, bloqueadas × desbloqueadas, exclusivas de chefes e de
entidades/rigs (`FinisherTargeting`). O servidor revalida a elegibilidade na confirmação; abrir o seletor não pausa o
servidor nem dá invulnerabilidade. Reutiliza o menu holográfico (`HoloMenu`/`HoloWorldInput`) e o catálogo
`Finishers`.

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

## 10. Holographic UI Phenomena

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

## Decisões do dono (consolidação)

- **Numeração "4.11":** mantida a histórica; as duas etapas são "4.11 (finisher)" e "4.11 (avisos)".
- **Recompensa × crédito de morte:** mantido o crédito vanilla (`getKillCredit`) em `BountyService.onDeath`, inclusive
  quando a morte final é `/kill` (também durante uma cena).
