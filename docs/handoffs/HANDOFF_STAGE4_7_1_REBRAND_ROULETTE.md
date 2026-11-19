# Handoff — Etapa 4.7.1: rebrand técnico RespectCraft e refinamento da roleta

Status: **concluída**. O mod é RespectCraft por fora **e por dentro**, e a roda da roleta passou a ter fatias iguais
com as chances reais do servidor.

> **Quebra de compatibilidade (decisão do dono).** Mundos, fichas e configs criados antes desta etapa **não** são
> carregados pelo mod: o mod id, os ids de registro, os attachments e as chaves de `persistentData` mudaram de
> namespace. Em um mundo antigo, os itens do mod e o Leão do Fim somem, e o personagem volta a "não nascido".
> Não há migração de registro (pedido explícito). Faça backup e use mundos novos.

## Parte A — identidade

| Antes | Agora |
|---|---|
| mod id / namespace `tupinicraft` | `respectcraft` (`RespectCraft.MOD_ID`, `RespectCraft.id(...)`) |
| pacote `com.tupinicraft` | `com.respectcraft` (mesma estrutura de subpacotes) |
| `TupiniCraft`, `client.TupiniCraftClient` | `RespectCraft`, `client.RespectCraftClient` |
| `command.TupiniCommands` | `command.RespectCraftCommands` |
| `client.ui.TupiScreen`, `TupiButton` | `client.ui.RespectScreen`, `RespectButton` |
| `assets/tupinicraft/` | `assets/respectcraft/` (não há `data/` no projeto) |
| `geo/player/tupinicraft_*.geo.json`, `animations/player/tupinicraft_*.animation.json`, `textures/entity/player_test/tupinicraft_test_skin_classic.png` | `respectcraft_*` |
| clips `animation.tupinicraft.player.<nome>` | `animation.respectcraft.player.<nome>` (o `<nome>`, bones e UVs não mudaram) |
| `model_sources/player/tupinicraft_*.bbmodel` | `respectcraft_*.bbmodel` |
| `config/tupinicraft-needs-server.toml`, `config/tupinicraft-speciality-server.toml` | `config/respectcraft-needs-server.toml`, `config/respectcraft-speciality-server.toml` |
| jar `tupinicraft-<versão>.jar`, artefato do CI `tupinicraft-jar` | `respectcraft-<versão>.jar`, `respectcraft-jar` |
| attachments `tupinicraft:*`, `persistentData` `tupinicraft_*`, lang `*.tupinicraft.*`, som `tupinicraft:*`, fonte `tupinicraft:defeat_icons`, aba criativa, categoria de teclas | mesmo nome com `respectcraft` |

- Feito com `git mv` (histórico preservado) e substituição só do token do nome do mod em arquivos ativos (`src`,
  `tools`, `model_sources/player`, `tests`, CI, Gradle). Textos de jogo, nomes de clips, bones e coordenadas de
  textura não foram tocados.
- `gradle.properties`: `mod_id=respectcraft`, `mod_group_id=com.respectcraft`. `mod_name` já era RespectCraft.
  `mod_version` continua `0.5.7`; Minecraft, NeoForge, GeckoLib, SmartBrainLib e Java **não mudaram**.
  `settings.gradle` não tem nome de projeto (o nome do jar vem de `archivesName = mod_id`).
- Exportador do jogador: o `.animation.json` e o `combo_scene.bbmodel` saíram idênticos ao gerador; no
  `player_combat.bbmodel` só os `uuid` das animações mudaram (são derivados do nome do clip). O arquivo foi regravado
  pelo gerador e o `export_manifest.json` tem os caminhos/hashes novos. `bbsync.py`, `test_export.py`,
  `export.py --check` e `validate_leao.py` passam.
- Protocolo de rede `GameplayNetworking`: `"3"` → `"4"`.

### Comandos

Uma só árvore `/respectcraft` (montada em `RespectCraftCommands`; `PlayerAnimationCommands.node()` e
`TestGameCommands.node()` são ramos dela). Sem alias com o nome antigo; o teste de fumaça confere que `/tupini`
responde *Unknown or incomplete command*. Subcomandos e argumentos em inglês; permissões iguais às de antes.

| Antes | Agora | Permissão |
|---|---|---|
| `/tupini perfil` | `/respectcraft profile` | todos |
| `/tupini skills list`, `/tupini finishers` | `/respectcraft skills list`, `/respectcraft finishers` | todos |
| `/tupini selftest` | `/respectcraft selftest` | 2 |
| `/tupini speciality list\|odds\|simulate <rolls>` | `/respectcraft speciality list\|odds\|simulate <rolls>` | 2 |
| `/tupini admin perfil\|pontos\|roleta\|resetar\|skill\|finisher\|efeito\|necessidade` (`<jogador>`, `<quantidade>`, `<raridade>`, `<executor> <alvo>`, `<tipo> <valor>`) | `/respectcraft admin profile\|points\|roulette\|reset\|skill\|finisher\|effect\|need` (`<player>`, `<amount>`, `<rarity>`, `<attacker> <target>`, `<type> <value>`) | 2 |
| `/tupinianim list\|play\|together\|sequence\|next\|stop` (`queda`) | `/respectcraft anim ...` (`fall`) | 2 (no ramo `anim`) |
| `/tupinitest finisher accept\|deny\|mobtest\|mobs on\|off`, `defeats get` | `/respectcraft test finisher ...`, `/respectcraft test defeats get` | todos |
| `/tupinitest defeats add\|set` | `/respectcraft test defeats add\|set` | 2 |

`/leaodofim` (nome do mob) e `/stopitstopnow` (parada de emergência) não carregam o nome antigo e ficaram como estão.
Exemplo do pedido: `/respectcraft admin roulette <player> boner`. As mensagens do convite de finalização citam os
comandos novos.

## Parte B — roleta

- **Fatias iguais:** `profile.RouletteLayout` (puro) define seis fatias de 60°, na ordem do ranking (Common a BONER).
  O tamanho é só visual; o sorteio continua em `SpecialityOdds`/`SpecialityRoll` no servidor e não lê o desenho.
- **Para no resultado do servidor:** a rotação de repouso é a fatia da raridade gravada na ficha, com um deslocamento
  determinístico por `rollCount` longe das divisórias (reabrir a tela mostra a roda no mesmo lugar). A máquina de
  estados (IDLE/WAITING/SPINNING/REVEAL/DONE), o pedido com `rollCount`, a resposta obrigatória e o tempo-limite não
  mudaram.
- **Chances reais:** `GameplayNetworking.SpecialityOddsSync` (servidor → dono, milésimos na ordem das raridades).
  Enviado no login, antes de cada `BirthRollReply` e para todos quando a config recarrega
  (`ProfileService.onConfigReload`, `ModConfigEvent.Reloading`). O cliente guarda em `ProfileClientCache.odds()`
  (padrão até chegar; limpo no logout); pacote inválido cai no padrão (`SpecialityOdds.ofOrDefault`). Não existe
  pacote cliente → servidor de chances.
- **Onde aparecem:** nome + % em cada fatia; aviso sob a roda ("Equal slices · % = real server odds"); dica ao passar
  o mouse na roda com a tabela das seis chances e "Slice size does not reflect the chance."; selo do resultado com
  raridade e % (`Common · 41.5%`).
- **Cores/contraste:** cores de `SpecialityRarity` (cinza, verde, azul, roxo, dourado, rosa choque). Fatias com a cor
  escurecida e texto escolhido por luminância (`Ui.readableOn`): branco com sombra no fundo escuro, quase preto na
  fatia destacada (clara). Selo com fundo escuro tingido, borda e texto na cor da raridade. Rótulos reduzidos usam
  escala que mapeia cada pixel da fonte em pixels inteiros da janela (`crispScale`: 0,5 na GUI 2, 0,67 na 3, 0,75 na
  4) — sem borrão. Nome de Speciality longo desce para baixo do selo.
- **BONER:** ~2 s de faíscas rosa/douradas, anéis, brilho pulsante, miolo rosa, nome e borda do selo pulsando e um
  segundo som de vitória mais agudo. Só partículas da própria tela (`UiParticles`), sem assets novos e fora da
  máquina de estados.
- Sem mudança em: 15 Specialities, nomes, raridades, chances padrão, traços, atributos, giro extra único, Continuar,
  `birthSpeciality`/`currentSpeciality`, Character Hub e painel do inventário.

## Testes

- `./gradlew build`: OK. `bash .github/scripts/server-smoke-test.sh`: OK (servidor dedicado com mod id
  `respectcraft`, Leão do Fim, `respectcraft anim list`, `RespectCraft self-test: OK`, 18 skills, 3 finalizações,
  chances padrão, `config/respectcraft-speciality-server.toml` gerada, nenhuma config `tupini*`, `/tupini` recusado,
  sem erro de classe de cliente).
- Ferramentas: `bbsync.py`, `test_export.py` (0 falhas), `export.py --check`, `validate_leao.py`: OK.
- `SelfTest` novo (`rouletteWheel`): fatias iguais e fechando 360°, roda para na raridade sorteada para as seis
  raridades × 64 `rollCount` (também depois de voltas inteiras a partir de outro ângulo), ida e volta do
  `SpecialityOddsSync` com chances customizadas, chances inválidas → padrão, padrão 41,5/25/20/10/3/0,5. Os testes da
  4.7 (15 Specialities, frequências, BONER forçado = Island Guy, giro único, confirmação, persistência) continuam.
- Cliente real (Xvfb, servidor dedicado com config **customizada** 35/25/20/10/7,5/2,5; capturas em
  `docs/evidencias/etapa4_7_1/`):
  - `01_creator.png`: Character Creator (textura, Body Layers e lang em `respectcraft`).
  - `02_roulette_custom_odds.png`, `02b_custom_odds_zoom.png`: roda com as chances customizadas do servidor (GUI 2).
  - `03_odds_tooltip.png`: dica com a tabela de chances.
  - `05_spin_result.png`: giro normal → The Voyeurs (Common), igual ao `/respectcraft admin profile` do servidor.
  - `08_reroll.png`: o único novo giro → The Foot Worshippers; botão passa a "Spin again (0)" desativado.
  - `09_hub.png`, `10_inventory.png`: Continue → Character Hub e painel Body & Traits com a Speciality.
  - `11_anim_punch.png`: `/respectcraft anim play Tester respectcraft:punch loop` (rig GeckoLib); `anim sequence ... fall`
    também rodou.
  - `12_live_reload_default_odds_zoom.png`: config devolvida ao padrão com o jogador conectado → a roda mostra
    41,5/25/20/10/3/0,5 sem reconectar.
  - `13_hub_scale3.png`, `14_roulette_scale3.png`: reconexão (GUI 3, 1280×720): ficha, Speciality e traço mantidos.
  - `15_boner_scale3_reveal.png`, `16_boner_scale3_done.png`: `/respectcraft admin roulette Tester boner` com a tela
    aberta → Island Guy, ponteiro na fatia BONER, efeito de revelação.
  - `05` e `08` são anteriores ao ajuste final do selo (antes: fundo na cor da raridade e texto claro); as capturas
    da GUI 3 mostram o selo final.
  - BONER forçado também foi testado na GUI 2 (Island Guy, fatia BONER).

## Referências históricas que ficam (intencionais)

- `docs/handoffs/*` anteriores a esta etapa e o índice (registro histórico; não reescritos).
- `docs/evidencias/` das etapas anteriores (capturas com o nome antigo).
- `.github/release-notes.md` e `.github/release-title.txt`: descrevem a release 0.5.7 já publicada com o jar antigo.
  **Reescreva-os na próxima release** (o workflow já procura `respectcraft-*.jar`).
- `.github/scripts/server-smoke-test.sh`: envia `tupini selftest` de propósito para confirmar que a raiz antiga não
  existe mais, e procura configs `tupini*` para falhar se aparecerem.
- Histórico do Git e o nome do repositório no GitHub (não renomeado).

## Problemas encontrados

- Em NeoForge 21.1 as configs SERVER ficam em `config/` (o `<mundo>/serverconfig/` só sobrepõe); o teste de fumaça
  confere `run/config/respectcraft-speciality-server.toml`.
- No Character Hub, a linha do traço "Perfectly Average Neutral" encosta na borda na GUI 3 (anterior a esta etapa,
  fora do escopo).
- O painel do inventário corta nomes longos de Speciality (anterior a esta etapa).

## Entrega

- Branch `claude/admiring-edison-7gx3t6`, PR [#30](https://github.com/Juann027/MineModding_01/pull/30), CI **Build**
  verde (build + teste de fumaça), integrado na `main` com o merge commit `122c29967f3922ceb7509bc1c92300bc8fa02e05`.
- Sem release: `mod_version` continua `0.5.7`.
