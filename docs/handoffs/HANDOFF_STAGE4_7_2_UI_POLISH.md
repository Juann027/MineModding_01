# Handoff — Etapa 4.7.2: polimento das telas do personagem e estabilidade pós-rebrand

Etapa pequena, só de layout de cliente. Nenhuma regra de jogo, dado salvo, id, chance, Speciality, traço, comando ou
config mudou. `mod_version` continua `0.5.7` (sem release).

## Problemas encontrados

1. **Character Hub (tecla K) — traço de nascimento passando da borda.** A linha do traço desenhava nome e polaridade
   na mesma linha sem conferir a largura: "Perfectly Average" + "Neutral" (≈144 px) não cabe na coluna de 122 px e
   o rótulo era empurrado para fora do painel. A linha "Effects not implemented yet." também era um `drawString` sem
   quebra e passava da coluna. Em telas estreitas (320×240 de GUI) a coluna do meio também vazava: "Bladder 34%"
   invadia a coluna dos traços e o nome da Speciality ficava espremido contra a raridade.
2. **Painel Body & Traits do inventário — nomes longos de Speciality cortados.** Rótulo e nome dividiam uma linha
   ("Speciality: The Foot W", "Trait: Perfectly Avera") cortada por `plainSubstrByWidth`; a raridade não aparecia
   (só na cor e na dica).

## Soluções

- `client.ui.Ui` ganhou dois ajudantes de texto: `fit` (texto inteiro ou cortado com "...") e `wrap` (quebra em até
  N linhas; a última termina em "..." se sobrar texto). Quem usa sempre oferece a dica com o texto completo.
- `CharacterHubScreen` (mesmo desenho, mesmas colunas e botões):
  - Traços: a polaridade fica ao lado do nome quando cabe; senão vai para a linha de baixo, alinhada à direita. Nome,
    descrição e "efeito" quebram na largura da coluna (`TRAIT_W` = 122, a largura dos botões, 10 px antes da borda).
    Nada é desenhado abaixo do limite acima de "Back to game"; passar o mouse no traço mostra nome, polaridade,
    descrição e efeito completos.
  - "Points" e "Birth traits" subiram 2–4 px para dar espaço à quebra.
  - Speciality: nome e raridade na mesma linha quando cabem (caso normal, igual a antes); em tela estreita o nome
    quebra em até duas linhas, a raridade vai embaixo e a descrição fica na dica (mouse sobre o bloco).
  - Necessidades: em tela estreita o nome encurta (no limite, só a inicial, como no painel compacto do inventário)
    e o valor fica à direita, sem invadir a coluna vizinha; a dica mostra nome e valor.
- `BodyStatusInventoryPanel`:
  - Dois blocos, Speciality e traço: primeira linha com o rótulo (cinza) e a **raridade**/**polaridade** à direita
    (na cor dela); embaixo o **nome inteiro**, quebrado em até duas linhas na cor da raridade/polaridade.
  - A altura do painel acompanha as linhas (record `Block`, `panelHeight(...)`); continua presa à tela e sem cobrir
    os slots. As quatro necessidades ativas não mudaram.
  - Modo compacto (painel estreito, ex.: livro de receitas aberto): sem rótulo, raridade/polaridade, nome quebrado
    com "..." se preciso; a dica (já existente) mostra o nome completo.
- Sem framework novo, sem aba nova, sem texto novo no `en_us.json`.

## Conferência pós-rebrand (direcionada)

| Item | Como | Resultado |
|---|---|---|
| Configs novas | teste de fumaça: `run/config/respectcraft-speciality-server.toml` (e `respectcraft-needs-server.toml`) geradas; nenhuma `tupini*` | OK |
| Árvore `/respectcraft` | teste de fumaça: `anim list`, `selftest`, `skills list`, `finishers`, `speciality odds`; `/tupini` responde "Unknown or incomplete command" | OK |
| Character Creator | cliente real: K num jogador novo abre o criador; Save leva à roleta | OK (`01_creator.png`) |
| Birth Roulette | cliente real: chances 41,5/25/20/10/3/0,5 do servidor nas fatias iguais; Spin → The Foot Worshippers, "Spin again (1)", Continue | OK (`02_roulette_result.png`) |
| Character Hub | cliente real, GUI 2, GUI 3 e 320×240 | OK |
| Painel Body & Traits | cliente real, GUI 2, GUI 3 e 320×240 | OK |
| Animação GeckoLib do jogador | `/respectcraft anim play Tester respectcraft:wave loop`, terceira pessoa | OK (`11_anim_wave.png`) |

Nenhum alias nem camada de compatibilidade foi criado.

## Testes

- `./gradlew build`: **BUILD SUCCESSFUL**.
- `bash .github/scripts/server-smoke-test.sh` (código final): **OK** (`RespectCraft self-test: OK`, Leão do Fim,
  animações, catálogos, configs, raiz antiga ausente).
- `python3 tools/player_anim/bbsync.py`: em sincronia.
- Cliente real (Xvfb, servidor dedicado, `tools/visual-test/helpers.sh`). Capturas em `docs/evidencias/etapa4_7_2/`:
  - `03_hub_gui2_fertile.png`: Hub GUI 2, traço curto (Fertile/Buff na mesma linha).
  - `05_hub_gui2_average.png`, `06_hub_gui2_trait_tooltip.png`: Hub GUI 2, "Perfectly Average" com "Neutral" na
    linha de baixo, dentro da coluna; dica com o texto completo.
  - `07_inv_gui2_foot.png`: painel GUI 2, "The Foot Worshippers" inteiro, raridade e traço visíveis.
  - `08_hub_gui3_average.png`: **Hub GUI 3** (426×246), o caso relatado, com margem até a borda.
  - `09_inv_gui3_foot.png`, `09b_inv_gui3_tooltip.png`: **painel GUI 3**, nome quebrado em duas linhas; dica.
  - `10_inv_gui3_long_names.png`: painel GUI 3 com The Foot Worshippers, The Humiliationists (Epic),
    The Exhibitionists e Showers of Gold (Rare).
  - `12_hub_min.png`, `13_inv_min.png`: menor GUI possível (320×240): Hub com nome quebrado e necessidades sem
    sobreposição; painel compacto.
  - As capturas GUI 2 (`03`–`07`) são de antes do ajuste de tela estreita da coluna do meio; nessa largura o
    caminho de desenho é o mesmo (o texto cabe), e `08`/`09`/`12`/`13` são do código final.
- Não repeti simulações estatísticas nem suítes de combate/animação.

## Limitações e observações

- **GUI 3 em Xvfb:** com janela de 1280×720 a barra de título deixa o framebuffer abaixo de 720 px e o Minecraft
  limita a escala a 2 sem avisar. A captura `13_hub_scale3.png` da 4.7.1 era, na prática, GUI 2. Para GUI 3 real
  use uma tela maior (ex.: `Xvfb :98 -screen 0 1400x820x24` e `MC_SIZE=1280x760`).
- Na tela da roleta a descrição da Speciality ocupa uma linha e é cortada ("Never look you in the eye. They look").
  Fora do escopo desta etapa (não relatado); o Hub e as dicas mostram a descrição inteira.
- Fichas com mais de um traço (`PER_BIRTH` = 1 hoje) cabem no Hub enquanto houver espaço; o excedente não é
  desenhado e fica na dica. O painel do inventário mostra o primeiro traço, como antes.

## Entrega

- Branch `claude/keen-mayer-8zadhb`, PR e merge commit: veja o índice dos handoffs (registrados após o merge).
