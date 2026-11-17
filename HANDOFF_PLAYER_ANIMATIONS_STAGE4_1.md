# HANDOFF — Etapa 4.1: compatibilidade com o Blockbench e transições entre animações

Continuação da Etapa 4. Nada foi reconstruído: rig, bones, Animation Manager, Finisher Engine, câmera
cinematográfica, Body Layers, os 10 clips e o combo de 7 s continuam iguais (o `.animation.json` do jogo não mudou
nem um byte). A etapa corrige três limitações das ferramentas de animação.

- Base: `main` em `8efdb20` (merge do PR #21). Versão `0.5.6` (sem mudança → sem Release).
- Branch: `claude/dazzling-cray-q5t32l`.
- Guia de uso para quem vai criar clips: `tools/player_anim/README.md`.

## A. Compatibilidade dos `.bbmodel` com o Blockbench

### O que foi realmente aberto

O ambiente de nuvem bloqueia `blockbench.net`/`web.blockbench.net`, mas alcança o GitHub por `git`. O Blockbench
**5.2.2** foi compilado do código oficial (`JannisX11/blockbench`, versão web, `node build.js --target=web`) e aberto
no Chromium sem tela (Playwright), com o plugin oficial **GeckoLib Models & Animations 4.2.5**
(`JannisX11/blockbench-plugins`) carregado pela interface de plugins do próprio editor. Nada foi instalado no projeto;
o processo é reproduzível com `bash tools/player_anim/blockbench/check.sh` (~25 s do zero).

| Arquivo | Aberto no Blockbench | Resultado |
|---|---|---|
| `tupinicraft_player_combat.bbmodel` | sim | formato *GeckoLib Animated Model*, 36 bones, 21 cubos, 1 textura 64×64 box-UV, 12 clips, todos os keyframes (24–236 por clip) |
| `tupinicraft_combo_scene.bbmodel` | sim | mesmo formato, 73 grupos (rig + `Alvo_Palco` + rig do alvo), 1 animação `combo_scene` (7 s, 14 trilhas, 436 keyframes) |
| `tupinicraft_animated_player.bbmodel` (rig original) | sim | idle e wave exportam igual ao arquivo do jogo (referência da convenção) |

Conferido no editor (`docs/evidencias/etapa4_1/01`, `02`): clip individual (`punch` no impacto, t = 0,65 s), queda
(`knockdown` no fim = `hands_and_knees` no início: mesmas posições de bones avaliadas pelo Blockbench), `get_up` a
meio, e a coreografia em t0, t25 (soco), t43 (chute) e t80 (alvo em quatro apoios).

### Defeito encontrado e corrigido

Os `.bbmodel` gerados na Etapa 4 tinham os valores do `.animation.json` copiados direto para os keyframes. O
Blockbench guarda os keyframes no próprio referencial e, ao exportar (`Keyframe.compileBedrockKeyframe`), **inverte X
e Y da rotação e X da posição**. Resultado: no editor os clips apareciam espelhados, e um *Export Animations* feito
pelo Blockbench geraria um `.animation.json` com 637 valores de sinal trocado. Correção em `bbmodel.py`
(`bbsync.flip`); os dois `.bbmodel` foram regenerados.

Depois da correção, o *Export Animations* feito **pelo próprio Blockbench** é idêntico ao arquivo do jogo:
12 clips, 84 trilhas, 0 diferenças (tempos, vetores, easing, loop, duração). Na cena dupla, as trilhas do executor e
as `Alvo_*` exportadas pelo editor são exatamente `finisher.combo_attacker` e `finisher.combo_victim`.

### Cena com dois rigs

- Executor e alvo separados, hierarquias independentes (`Root…` e `Alvo_Root…` dentro de `Alvo_Palco`).
- Referencial: executor na origem olhando para −Z (frente do Blockbench); alvo a 17,07 px (1 bloco no mundo, escala
  15/16 do `PlayerRenderer`), girado 180°, olhando para o executor (posição da cabeça do alvo em t0 avaliada pelo
  editor: `(0, 24, −17,07)`).
- Pivôs: os do rig, copiados (não foram alterados).
- Animações editáveis; para levar a um clip individual: as trilhas sem prefixo são o executor, as `Alvo_*` o alvo
  (o `check.sh` e o `bbsync` fazem essa separação; não exporte o geo deste arquivo).

## B. Exportação sem sobrescrita acidental

`tools/player_anim/export.py` agora:

- valida tudo **antes** de gravar: estrutura, bones existentes no rig, números finitos, tempos dentro do clip,
  easings conhecidos, continuidade dos encadeamentos, consistência dos `.bbmodel` (uuids, outliner, animadores) e a
  ida e volta `.bbmodel` → regra de exportação do Blockbench → `.animation.json`. Falhou → saída 4, nada gravado;
- guarda em `export_manifest.json` o hash do que gravou. Arquivo que mudou desde então (ex.: salvo pelo Blockbench)
  aparece como `EDITADO` e o script **recusa e não grava nada** (saída 2) sem `--force`;
- `--force` copia os arquivos editados para `tools/player_anim/.backup/<data>/` antes de regravar;
- `master.json` escolhe a fonte de verdade: `gerador` (padrão) ou `blockbench`. Com `blockbench` o script nunca grava
  no repositório, nem com `--force` (saída 3);
- `--dry-run` (mostra o plano), `--check` (saída 1 se algo mudaria), `--out DIR` (outra raiz de saída), gravação
  atômica (temporário + rename).

`bbsync.py` (só biblioteca padrão) reproduz a regra de exportação do Blockbench e aponta divergências entre
`.bbmodel` e `.animation.json`; roda no CI (workflow **Build**) antes do Gradle.

**Limitação assumida:** não existe importação Blockbench → `poses.py`/`clips.py`. Quem editar no Blockbench passa o
mestre para `blockbench` (ou copia valores à mão, com a troca de sinal). O `bbsync` não reproduz keyframes bezier,
step, catmullrom de interpolação ou com vários pontos: esses aparecem como "não verificável" (confira com
`blockbench/check.sh`, que usa o export do próprio editor).

## C. Sequências de animação (sem voltar à pose em pé)

### Problema

Cada clip era uma execução independente: ao fim natural de `knockdown` o cliente encerrava a animação e o rig
voltava ao vanilla até o próximo comando chegar. Medido em jogo (Juca vendo Dev): 17 quadros (0,85 s) em pé entre
`knockdown` e `hands_and_knees` (a duração depende de quando o segundo comando chega; nunca era zero) (`docs/evidencias/etapa4_1/03`, `04`).

### Solução (mudança localizada no Animation Manager)

- `PlayerAnimationState` passou a descrever o **segmento atual + os seguintes** (`then`, `segmentTicks`, `segment`).
  Os instantes de troca saem de `startGameTime` e das durações; servidor e clientes calculam o segmento vigente com
  `state.at(gameTime)` e trocam **no mesmo tick, sem pacote extra** (sem latência entre clips). O construtor antigo
  (animação avulsa) foi mantido e se comporta como antes.
- `AnimationSequence` (dados): `of(KNOCKDOWN).thenFor(HANDS_AND_KNEES, 40).then(GET_UP)`. Loops dentro de sequência
  exigem duração explícita; limite de 5 min por segmento; no máximo 17 clips. Nenhum estado fica sem fim.
- `PlayerAnimationService.playSequence(...)` e `advance(player)` (sai do segmento atual agora, ex.: quando uma
  condição do servidor for cumprida). Prioridade, `stopOnHurt` e o cancelamento pelo próprio jogador olham o
  segmento vigente. A cada troca o servidor publica `End(FINISHED)` do segmento e `Start` do seguinte; eventos
  temporizados de cada clip continuam saindo pelo relógio do servidor.
- Cliente: `ClientPlayerAnimations.active()` devolve o segmento vigente (rig, câmera e `PlayerRigController`
  continuam lendo o mesmo método). O rig troca de segmento misturando a partir da pose atual e, quando a sequência
  continua, toca o clip com `thenPlayAndHold` (segura o último quadro até a troca).
- Interrupções reutilizam a limpeza existente e encerram a sequência inteira: `/tupinianim stop`, morte, saída,
  dano em clip `stopOnHurt`, animação de prioridade maior. Troca de dimensão mantém a sequência (o jogador recebe o
  estado com os segmentos restantes; quem passa a vê-lo também).
- Pronta para uso: `PlayerAnimations.FALL_AND_RECOVER` (knockdown → 40 ticks em quatro apoios → get_up, 114 ticks).

Correção extra encontrada no teste: ao começar uma animação logo depois de o rig ficar sem ser desenhado (ex.:
sequência cancelada em quatro apoios), os *snapshots* de bones do GeckoLib ainda tinham a pose antiga e a transição
partia dela, mostrando essa pose por **um quadro**. `PlayerRigAnimatable.beginFrame` limpa os snapshots desse
jogador (`AnimatableManager.clearSnapshotCache`) quando o rig volta depois de parado (antes do processamento do
GeckoLib). Defeito anterior a esta etapa (caminho de início não foi alterado), intermitente.

### Como criar uma sequência

```java
public static final AnimationSequence MINHA = AnimationSequence.of(STAGGER).then(KNOCKDOWN)
        .thenFor(HANDS_AND_KNEES, 60).then(GET_UP);              // em PlayerAnimations (só dados)
PlayerAnimationService.playSequence(player, PlayerAnimations.MINHA, new CompoundTag(), false);
```

Os clips encadeados devem terminar na pose em que o seguinte começa (o `export.py` confere os pares de
`CHAINS`); se não, a transição do clip seguinte (`transitionTicks`) suaviza a diferença. Comandos de teste:
`/tupinianim sequence <jogadores> queda`, `/tupinianim sequence <jogadores> knockdown hands_and_knees@60 get_up`,
`/tupinianim next <jogadores>`, `/tupinianim stop <jogadores>`.

## Testes realmente executados

| Teste | Resultado | Evidência |
|---|---|---|
| Abrir os 3 `.bbmodel` no Blockbench 5.2.2 + plugin GeckoLib 4.2.5 (formato, bones, clips, keyframes) | OK | `01`, `02` |
| *Export Animations* do Blockbench × arquivo do jogo, antes da correção | 637 diferenças (X/Y da rotação) | — |
| Idem, depois da correção (biblioteca e os dois rigs da cena) | 0 diferenças | `blockbench/check.sh` |
| `test_export.py`: exportação em diretório vazio, repetição sem regravar, `.bbmodel` editado recusado e intacto, `--dry-run`, `--check`, mestre = blockbench recusado com `--force`, `--force` com cópia de segurança, validação impedindo gravação, `bbsync` detectando o `.bbmodel` espelhado | 15/15 OK | — |
| `export.py --check` no repositório (gerador reproduz os arquivos commitados) e continuidade dos encadeamentos | OK (0,00 de diferença nos 3 pares) | — |
| `./gradlew build` | OK | — |
| Teste de fumaça do servidor dedicado (autoteste com as checagens novas: continuidade tick a tick, instantes de troca, avanço antecipado, animação avulsa inalterada, loops sem duração recusados, pacote ida e volta, comando) | OK | — |
| Dois clientes reais (Xvfb, llvmpipe) + servidor dedicado: Dev (3ª pessoa) e Juca (observador) | — | — |
| `sequence queda`: topo da silhueta de Dev quadro a quadro (20 fps) nos dois clientes, sem volta à pose em pé nas duas trocas; termina em pé | OK | `03`, `04` |
| Comportamento antigo (`play knockdown` + `play hands_and_knees`) para comparação | 17 quadros em pé entre os clips | `03`, `04` |
| Cancelamento (`stop`) em quatro apoios: volta ao vanilla em ~3 quadros nos dois clientes | OK | — |
| 5 ciclos cancelar → recomeçar (10 inícios em 2 clientes) após a correção dos snapshots | nenhum quadro com pose antiga | — |
| `next` saindo de um loop de 600 ticks | levanta sem salto | — |
| Morte no meio da sequência e respawn | volta em pé | `05` |
| Troca de dimensão (End → Overworld) no meio da sequência | segue no segmento certo nos dois clientes e termina em pé | `05` |

Não testado: saída do jogo no meio da sequência (caminho de limpeza existente, sem mudança: `onLogout` encerra e
avisa todos os clientes); dano em clip `stopOnHurt` (nenhum clip da biblioteca usa); som (sem OpenAL no Xvfb);
GPU real; mais de dois clientes; abrir o Blockbench desktop (Electron) — foi usada a versão web do mesmo código.

## Limitações restantes

- Sem importação Blockbench → gerador (ver B).
- A troca de segmento usa a transição do clip seguinte; num loop cortado no meio do ciclo (ex.: `hands_and_knees`
  por 50 ticks) a mistura de `transitionTicks` suaviza a respiração, sem continuidade exata.
- O cliente ainda conta a sequência pelo relógio do mundo dele; atraso grande de rede só desloca o instante, não cria
  buraco entre clips.
- `rasteira`, `takedown` e `knocked_down` (de `tools/gen_finisher_clips.py`) existem só no `.animation.json` do rig
  original; não há `.bbmodel` para eles.
- Limitações da Etapa 4 que continuam: pernas sem joelho, `crawl` no lugar, empurrões só visuais.

## PR e commits

Veja a descrição do PR desta etapa (lista de commits e resultado do CI).
