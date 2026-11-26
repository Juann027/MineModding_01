# Handoff — Etapa 4.11: avisos holográficos automáticos e caça a recompensa

## Pedido

1. Analisar e, se preciso, atualizar o sistema da UI holográfica (menu K).
2. Sistema de **notificações automáticas** na UI holográfica para eventos futuros (chefes, aberturas de portais etc.),
   integrado ao **Calamidade_Awakening**, mesmo que esses eventos ainda não existam.
3. Deixar pronta uma aba de **caça a recompensa contra outros jogadores**.

## Análise do menu K (o que mudou no núcleo)

- O arco, os chips e as teclas numéricas assumiam **cinco** seções (`(ordinal - 2) * passo` nos chips,
  `GLFW_KEY_1..5` na tela de reserva). Agora tudo deriva de `Section.values().length`: chips centrados pelo número
  de seções e teclas 1–N.
- Com sete seções o chip fica estreito: a fileira pode ir até `plateW + 130` e, abaixo de ~58 unidades, o chip mostra
  **só o ícone** (nome na dica, como já era com texto cortado).
- `HoloNav.badge(section)`: ponto aceso no chip (Alerts = não lidos; Bounty = recompensa contra mim).
- Fita de identidade: novo botão **✉** (selo com os não lidos) que leva à seção Alerts; os botões passaram de 4 para 5.
- Painel Display: toggle **Notice cards** (`noticeToasts`); config ganhou também `noticeSounds` (só no arquivo).

## Avisos holográficos (`notice`)

| Classe | Papel |
|---|---|
| `NoticeKind` | categoria: `system`, `world_event`, `boss_spawn`, `boss_defeat`, `portal`, `realm`, `bounty` — cor, glifo, gravidade padrão e farol (`Beacon`). Nova categoria **no fim** (rede usa ordinal). |
| `NoticeSeverity` | INFO 5 s, ALERT 7 s, CRITICAL 10 s (tempo do cartão e som). |
| `HoloNotice` | título, texto (`Component`), local opcional (dimensão + bloco), chave de duplicata; `STREAM_CODEC`. |
| `NoticeRules` | regras puras (no `SelfTest`): duplicata em 20 s, 3 cartões visíveis, histórico de 40, raios (chefe 160, portal 64), oito ventos, distância arredondada, visibilidade do cartão. |
| `HoloNotices` | **único caminho**: monta audiência (jogador, todos, nível, raio), posta `HoloNoticeEvent` (cancelável, pode trocar aviso/audiência), envia `HoloNetworking.NoticePayload` e acende o farol. Fontes automáticas e API futura. |
| `NoticeFx` | **único dono** dos faróis de aviso no Calamidade: um efeito por `Beacon` (pilar de luz, anel no chão que abre, luz), 80 ticks, alcance 160, registrado no construtor do mod. Fonte = entidade (chefe) ou bloco. Sem `film`, conjurador ou `shake`. Limite recusado = aviso sem farol (log debug). |
| `NoticeCommands` | `/respectcraft notice kinds` e `test <kind> [players]` (permissão 2). |

Fontes automáticas registradas em `RespectCraft`:

- `EntityJoinLevelEvent` de entidade `c:bosses` **nova** (não carregada do disco) → `bossAppeared` (no próximo tick do
  servidor, para nome/posição certos) para quem está a 160 blocos, farol vermelho seguindo o chefe.
- `BossDefeatEvent` → `boss_defeat` para o nível (texto com quem levou o crédito / "finished off"). **Nenhuma
  recompensa** (a regra da 4.8 continua).
- `BlockEvent.PortalSpawnEvent` (LOWEST, não cancelado) → `portalOpened` (portal do Nether aceso), raio 64, farol violeta.
- `PlayerChangedDimensionEvent` para `respectcraft:inverted_realm` → aviso só para o jogador.

API para o que ainda vai chegar: `HoloNotices.bossAppeared(boss)`, `portalOpened(level, pos, destino)`,
`worldEvent(level, título, texto, pos)`, ou `send(...)` com um `HoloNotice` próprio.

Cliente: `network.NoticeClientCache` (fila de cartões + histórico, duplicatas descartadas, limpo no logout; sem import
de cliente), `client.holo.HoloToasts` (camada de GUI acima do chat, canto **superior esquerdo** — o direito é dos
efeitos e das notificações vanilla; some com F3; desliza, varredura, moldura piscando no CRITICAL, barra de tempo,
distância/direção na linha da categoria; som ao aparecer) e `client.holo.NoticesView` (seção 06 Alerts: cartões com
categoria, "há quanto tempo", texto, coordenadas + distância/direção ou "other dimension"; entrar marca como lido e os
novos ficam acesos; botão Clear).

## Caça a recompensa (`bounty`)

- `Bounty` (contrato, Codec estável), `BountyBoard` (SavedData `respectcraft_bounties` no Overworld: `bounties`,
  `pending_returns`; o limite de atualização por jogador é transitório), `BountyRules` (puras, no `SelfTest`),
  `BountyService` (único dono), `BountyCommands` (`/respectcraft bounty list|place <alvo> <dias>|accept|abandon|cancel
  <id>|admin clear`).
- **Recompensa** = a pilha inteira da mão principal de quem paga (lida no servidor), guardada no quadro. Não existe
  moeda nem economia.
- Abrir: alvo online e diferente de si, 1/3/7 dias de jogo (24000 ticks/dia, ≈ 20/60/140 min reais), até 3 contratos
  abertos por pagador e 1 por par pagador/alvo.
- Aceitar: nem pagador nem alvo; até 8 caçadores por contrato e 3 contratos por caçador. Abandonar a qualquer hora.
- Cobrar: caçador que aceitou mata o alvo (`LivingDeathEvent` LOWEST, não cancelado; killer = fonte ou kill credit) ou
  conclui uma finalização PLAYER_VS_PLAYER nele (`FinisherSessionEvent.Completed`). Todos os contratos que ele caçava
  nesse alvo são pagos; itens vão ao inventário (ou caem no chão).
- Cancelar: só o pagador e só sem caçadores (admin: sempre). Expirar: conferido a cada 100 ticks; itens voltam ao
  pagador, ou ficam em `pending_returns` até o próximo login.
- Sincronização: `HoloNetworking.BountyBoardSync` personalizado (flags MINE/ON_ME/HUNTING) no login, a cada mudança e
  por `BountyActionRequest(REFRESH)` — a aba pede ao entrar e a cada 5 s enquanto está na frente; o servidor ignora
  pedidos com menos de 40 ticks. Rastreio só para quem aceitou: distância arredondada + oito ventos, "outra dimensão"
  ou "offline". Recusas voltam como aviso BOUNTY com o motivo (`bounty.respectcraft.result.*`).
- Aba 07 **Bounty Hunt** (`client.holo.BountyView`): cartão vermelho "price on your head"; contratos (alvo, selo
  YOU/YOURS/HUNTING, tempo restante, recompensa na cor da raridade do item, pagador, caçadores, Accept/Abandon/Cancel,
  rastreio); formulário com seletor de alvo (jogadores online pela lista do cliente), duração, prévia da mão principal
  e **Post bounty com segundo clique** (tira os itens). Itens não são desenhados como ícone: a superfície do painel não
  tem buffer de profundidade e itens 3D sairiam errados.

## Rede

`HoloNetworking.register` (registrar `"1"`): `respectcraft:holo_notice` (S→C), `respectcraft:bounty_board_sync` (S→C),
`respectcraft:bounty_action` (C→S). Handlers S→C por lambda chamando as caches.

## Testes

- `./gradlew build` OK.
- `bash .github/scripts/server-smoke-test.sh` OK: `RespectCraft self-test: OK` (casos novos `notices` e `bounties`:
  duplicata, ventos, distância, raio, cartão, ids das categorias, pacote ida/volta, todas as regras de contrato, Codec),
  `RespectCraft notice FX registered on Calamidade_Awakening`, `/respectcraft notice kinds|test` e
  `/respectcraft bounty list` respondendo, `Live instances on server: 0 / 64` (nenhum farol preso).
- Cliente real em Xvfb (llvmpipe, 854×480, GUI 2), dois clientes (Juca e Zeca) no servidor dedicado, evidências em
  `docs/evidencias/etapa4_11/`:
  - `notice test boss_spawn`/`portal` como jogador → dois cartões empilhados no canto (`01_*` mostra o cartão CRITICAL
    "A bounty is on your head!" que o alvo recebeu ao abrir o contrato).
  - Botão ✉ da fita (selo de não lidos) → seção 06 Alerts com os cartões NEW, categoria, "há quanto tempo",
    coordenadas (`02_*`); o aviso de login "You are still wanted" apareceu no histórico do alvo.
  - Sete chips compactos (só ícone) na fileira (`05_*`).
  - Bounty Hunt do pagador: contrato YOURS + Cancel e formulário (`03_*`); do alvo: aviso "price on your head", selo
    YOU, tempo restante contando, sem botões, seletor de alvo/duração com setas (`04_*`).
  - Correções feitas a partir do teste: linha da categoria do cartão em tamanho normal (a 0,75 ficava ilegível), setas
    do seletor cortadas em botão de 14 (agora 20), botão do contrato saindo do cartão (cartão mais alto), "right here"
    a menos de 5 blocos.

## Limitações e próximos passos

- Portal: só o do Nether vanilla dispara aviso; portais do mod devem chamar `HoloNotices.portalOpened`.
- Eventos de mundo e chefes próprios ainda não existem (API pronta).
- Histórico de avisos só no cliente (some no logout).
- Recompensa só em itens; alvo precisa estar online para abrir o contrato; o tempo restante na aba é estimado no
  cliente entre atualizações.
- Sem recompensa por chefe (regra da 4.8 mantida).
