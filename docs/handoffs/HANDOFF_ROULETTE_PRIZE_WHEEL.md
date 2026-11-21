# Handoff — Roleta de nascimento: roda de prêmios (redesign só visual)

Status: **implementada e testada em cliente real**. Base: `main` em `68c1890` (depois da 4.8.4-C).

## 1. Pedido

A roda da Birth Roulette deve ler como uma roda de prêmios física: cada fatia na **cor da própria raridade** (não
mais fundo escuro genérico), com degradê/brilho; divisórias claras e elegantes; miolo e ponteiro bem acabados;
fatia vencedora com destaque forte. Compatível com a janela holográfica no mundo. **Sem mexer em lógica nem chances.**

## 2. O que mudou

| Arquivo | Mudança |
|---|---|
| `client/roulette/PrizeWheel.java` (novo) | Pintor único da roda, geometria com cor por vértice no `RenderType.gui()` (sem textura, sem framebuffer). Um objeto por casca guarda só animação visual (`focus` do vencedor e relógio). |
| `client/holo/RouletteView.java` | `drawWheel` chama o `PrizeWheel` (opacidade da janela via `HoloCanvas::c`, movimento via `HoloConfig.motion()`); textos das fatias continuam pelo `HoloCanvas`. Brilho de fundo na cor da vencedora. |
| `client/roulette/BirthRouletteScreen.java` | Tela de reserva usa o mesmo pintor (sem duplicar desenho); removidos `disc`/`sector`/`segmentAtPointer`. |

Camadas do `PrizeWheel` (de trás pra frente):

1. Sombra em volta e **aro de metal** dourado (faixas escuro → claro → escuro), iluminado por uma luz fixa de
   cima/esquerda.
2. **Fatias** em malha (anel × ângulo): miolo escuro da raridade → cor da raridade → borda clara ("lábio") → fio
   escuro; bordas laterais um pouco mais escuras ("almofada"); reflexo da luz fixa (fica parado enquanto a roda gira).
   Common cinza-prata, Uncommon verde, Rare azul, Epic roxo, Legendary âmbar/ouro com brilho que corre devagar,
   BONER rosa/magenta com tom violeta pulsando, brilho que corre mais rápido e **faíscas** (mais fortes no efeito
   BONER).
3. Reflexo de vidro (meia-lua translúcida em cima/esquerda).
4. **Vencedora** (fase REVEAL/DONE com Speciality): clareia e pulsa, contorno branco na borda, luz nas duas
   divisórias; as outras escurecem 40 % (o texto delas um pouco menos). Entra suave (`focus`).
5. **Divisórias** em fita escura com fio dourado e **pino** de metal na ponta.
6. **18 lâmpadas fixas** no aro: brilho lento parado, "perseguição" ligada ao ângulo durante o giro, piscam na cor da
   vencedora; rosa piscando no efeito BONER.
7. Textos (nome + chance do servidor) escritos pela casca; cor branca ou tinta escura pelo **contraste WCAG** com a
   cor da fatia naquele raio (a vencedora clareada passa a texto escuro).
8. **Miolo**: sombra, anel de metal com 6 rebites (giram com a roda), face de ônix e joia na cor da vencedora.
9. **Ponteiro** tipo lingueta: cabeça de metal com joia (vermelha; vira a cor da vencedora), lâmina com lado claro e
   escuro, sombra; **inclina quando um pino passa** (a roda gira no sentido horário) e nunca em repouso (a roda para
   a ≥ 20 % da fatia longe da divisória).

Nada lê ou grava o sorteio: ângulo, fatias iguais e raridade sob o ponteiro continuam vindo de `RouletteMachine` e
`RouletteLayout`. Chances, `requestId`, erro/timeout, novo giro e Continuar intactos.

## 3. Testes

| Comando | Resultado |
|---|---|
| `./gradlew build` | OK |
| `bash .github/scripts/server-smoke-test.sh` | OK — `RespectCraft self-test: OK` |

**Cliente real** (Xvfb + openbox sem atalhos de Alt, llvmpipe, 960 × 540, GUI 2) contra o servidor dedicado de
desenvolvimento. Evidências em `docs/evidencias/roleta_premios/`:

| Cenário | Evidência |
|---|---|
| Janela no mundo, roda parada | `01_roda_parada.png` |
| Spin real (modo cursor): giro, desaceleração, ponteiro, vencedora Uncommon | `02_giro_folha.png`, `03_resultado_janela.png` |
| Parada × vencedora BONER × vencedora Legendary (`/respectcraft admin roulette Dev <rarity>`) | `04_parada_boner_legendary.png` |
| Tela de reserva `mode = "SCREEN"`, vencedora Epic | `05_tela_reserva.png` |

Achado e corrigido no teste: texto branco ilegível sobre a BONER clareada e rótulos das não vencedoras apagados
demais → escolha por contraste WCAG e escurecimento menor do texto.

**Não verificado:** GPU real, *Fancy/Fabulous*, shaders; custo por quadro medido (malha ≈ 1 000 quads por quadro,
sem alocação de GPU).

## 4. Correção visual: roda preta (PR seguinte ao #41)

Pedido do dono, com foto de referência (roda física preta fosca, divisórias finas claras, texto colorido): a roda do
#41 tinha fatias inteiras coloridas. Agora:

- Fatias **pretas** (`SURFACE` carvão); a raridade é só **luz colorida** que nasce perto do aro e some no preto, com
  as laterais de cada fatia pretas; fio de luz junto do aro. Nunca cor chapada (luz máxima limitada a 72 %).
- Texto das fatias na **cor da raridade** clareada (como o giz colorido da foto); a vencedora fica quase branca.
- Divisórias finas e claras (prata) em vez de douradas; pinos, aro, lâmpadas, miolo, joia e ponteiro mantidos.
- Vencedora: pouca luz a mais dentro da fatia; o destaque é brilho no aro por fora, fio na cor dela na borda e luz
  nas divisórias. As outras apagam a luz em 55 %.
- Legendary/BONER: a onda de luz que corre continua, agora como luz sobre o preto.
- Reflexo de vidro mais discreto.

Teste em cliente real (mesma configuração): parada, giro real até Uncommon, BONER e Legendary forçadas
(`docs/evidencias/roleta_premios_preta/`; `00_antes_pr41.png` é o antes). Achado e corrigido: texto rosa sobre a luz
rosa da BONER vencedora. A foto de referência não está no repositório (só veio pela conversa).
