# HANDOFF — Etapa 4.5 revisada: criador limpo, traços de nascimento e necessidades alinhadas

Base: `main` em `fe1c507` (merge do PR #26, que já trouxe o rebranding RespectCraft, os handoffs em
`docs/handoffs/` e a correção do giro infinito da roleta). Esta etapa **não refaz** nada disso.
`mod_version=0.5.7` não mudou; nenhuma Release foi publicada.

## 1. Character Creator — o que saiu da tela

| Removido | Onde estava |
|---|---|
| Retrato 2D do rosto (quadrado à direita, com moldura) | `CharacterCreationScreen.drawPortrait()` — método apagado |
| Frase "Skin tone and face show on the 3D model..." | chave `gui.tupinicraft.creator.note` — apagada de `en_us.json` e `pt_br.json` |
| Seletor de cor do cabelo com setas (`CycleSelector` "Cor") | aba Cabelo |

Reorganização: a prévia 3D passou a ocupar ~45% do painel (antes ~35%) e o modelo ficou maior (escala até 60,
antes 48). As abas Corpo/Cabelo/Rosto saíram (deixavam metade da coluna vazia): todas as opções ficam numa página —
Corpo, Pele, Rosto, Estilo do cabelo e a paleta de cor (chaves `gui.tupinicraft.creator.tab.*` apagadas). A rotação
por arrasto, o botão Aleatório, Voltar e Salvar continuam. Pele e rosto continuam mudando a prévia 3D (Body Layers).
O aviso "Salvando..."/erro usa a linha onde ficava a nota.

## 2. Cor do cabelo — um seletor só

O cabelo tem o seletor de **estilo** (setas) e **uma** paleta de quadradinhos clicáveis. Acima da paleta aparece
"Cor: <nome>"; cada quadradinho tem dica com o nome da cor; o selecionado fica destacado. Clicar atualiza a prévia na
hora; Salvar grava como antes. As 6 cores e os índices salvos (`CharacterAppearance.COLOR_IDS`, `pack()`) não mudaram.

## 3. Três conceitos separados

| Conceito | Classe | O que é |
|---|---|---|
| Atributos principais | `profile.CharacterAttribute` | Força, Agilidade, Vigor, Inteligência, Carisma, Sorte. Sorteio + bônus da origem. |
| Necessidades | `needs.Need` | Medidores que mudam no jogo. Ativas: Coom, Bexiga, Intestino, Emoção. |
| Traços de nascimento | `profile.BirthTrait` (novo) | Características individuais (buff/debuff/neutro) sorteadas à parte. |

A **origem** (`BirthOrigin`) continua sendo só origem social com bônus de atributos; não é traço e não gera traço.

### Os seis atributos foram preservados

`FORCA`, `AGILIDADE`, `VIGOR`, `INTELIGENCIA`, `CARISMA`, `SORTE`: mesmos ids, valores salvos, sorteio
(`BirthRoulette.roll`), bônus de origem e efeitos existentes (dano, velocidade, vida, sorte em
`SkillService.refreshModifiers`; pontos iniciais por Inteligência; Vigor/Carisma no Coom e na Emoção). Nenhum efeito
novo. O menu K e a roleta continuam mostrando os seis com valor e barra.

## 4. Hidratação e Energia — dados legados inativos

Escolha: **manter os campos e desligar o comportamento** (mais seguro que remover campos serializados).

- `Need.HYDRATION` e `Need.ENERGY` continuam no enum e no Codec de `Needs` (`hidratacao`, `energia`); saves antigos
  carregam e regravam os valores intactos. `Need.active()` é falso para as duas; `Need.ACTIVE` = Coom, Bexiga,
  Intestino, Emoção (ordem da interface).
- `NeedsRules.tickSecond` não as altera mais; `moodTarget` não as considera (Emoção não cai por sede/cansaço).
  As constantes de ritmo delas saíram. `Context.hydrationMul/energyMul` ficaram na assinatura e são ignorados.
- `NeedsService`: sem lentidão/fadiga/dano por sede e cansaço, sem avisos delas; `apply` descarta a parte
  hidratação/energia dos deltas (`NeedsDelta.activeOnly`), então consumíveis e comida vanilla não as mexem.
  O listener de acordar (enchia a energia) saiu.
- Interface: o painel Body & Traits do inventário e o menu K mostram só as quatro ativas (a seção "Sobrevivência"
  saiu); a dica dos consumíveis só lista necessidades ativas; `/tupini admin necessidade` só aceita as ativas.
- Consumíveis afetados (continuam com os outros efeitos): Água de Coco, Cafezinho, Guaraná (bexiga + emoção),
  Café Extraforte (bexiga, pressa, velocidade), Energético do Tigrinho (velocidade/pressa, bexiga, emoção e o
  tombo atrasado), Chá Calmante (emoção e Coom). Descrições do café e do energético foram ajustadas.
- Habilidades: `sobrevivencia/cantil` (hidratação −20%) e `sobrevivencia/rede` (energia −20%) **ficaram sem efeito**;
  `sertanejo` e `rolezinho` perderam só a parte de hidratação/energia (mantêm velocidade). Ids e compras salvas não
  mudaram; a árvore mostra "Hidratação: inativa (sem efeito)" nessas linhas. Novos efeitos para elas não foram
  inventados — decisão de design pendente (ver Limitações).

## 5. Traços de nascimento

`profile.BirthTrait` (enum, código como `BirthOrigin`): id estável, nome e descrição traduzidos
(`trait.tupinicraft.<id>` / `.desc`), categoria, polaridade (`BUFF`, `DEBUFF`, `NEUTRAL`, com cor), peso e efeitos
(`List<SkillEffect>`). Catálogo inicial (referências de design, **sem efeito de jogo**):

| id | Nome (pt / en) | Categoria | Polaridade | Peso |
|---|---|---|---|---|
| `high_endurance` | Resistência Alta / High Endurance | ENDURANCE | Buff | 10 |
| `limp` | Molenga / Limp | ENDURANCE | Debuff | 10 |
| `heavy_loader` | Carregadão / Heavy Loader | LOAD | Buff | 10 |
| `loadless` | Descarregado / Loadless | LOAD | Debuff | 10 |
| `hung` | Bem-Dotado / Hung | ENDOWMENT | Buff | 10 |
| `tiny_dick` | Pinto Pequeno / Tiny Dick | ENDOWMENT | Debuff | 10 |
| `average` | Na Média / Perfectly Average | GENERAL | Neutro | 20 |

- Todos têm `effects` vazio: **nenhum bônus oculto**, e a interface diz "Sem efeito no jogo por enquanto". Descrições
  são só humor leve, sem conteúdo explícito.
- Para dar efeito a um traço: acrescente `SkillEffect`s no construtor (atributo vanilla, ritmo de necessidade ativa ou
  bônus de Emoção). `SkillService` já aplica efeitos de traços como os de habilidades (modificadores transitórios
  `tupinicraft:stat/trait/<id>/<n>`, `needRate`, `moodBonus`); o `SelfTest` hoje exige `effects` vazio, então
  atualize esse caso junto.
- Sorteio: `BirthTrait.roll` escolhe `PER_BIRTH` (= 1) traço ponderado, no máximo um por categoria, depois dos
  atributos e independente da origem (`BirthRoulette.Result` ganhou `traits`).

## 6. Persistência, rede e reroll

- `CharacterProfile` ganhou `traits` (Codec `birth_traits`, `optionalFieldOf` com lista vazia). Fichas antigas
  carregam sem traços e **não são resetadas nem recebem traço retroativo**: o menu mostra "Sem traço de nascimento:
  este personagem nasceu antes dos traços". Duplicatas são removidas; ids desconhecidos ficam guardados (versão
  futura) mas não aparecem (`birthTraits()`). Limite de 8 por ficha (`MAX_TRAITS`).
- O `StreamCodec` da ficha envia os traços; sincroniza junto com a ficha (login, respawn, dimensão, mudança).
  Registrar de `GameplayNetworking` foi para a versão `"3"` (cliente e servidor precisam da mesma versão).
- **Regra do reroll:** o novo giro sorteia origem, atributos **e traços** de novo; os traços do giro anterior são
  substituídos (nunca somados). Continua existindo um único novo giro; nada concede outro.
- **Confirmação:** Continuar envia `BirthConfirmRequest(rollCount)`; o servidor (`ProfileService.confirmBirth` →
  `BirthRoulette.confirm`, pura) zera o novo giro restante. Estado confirmado = `born && rerolls == 0`
  (`birthConfirmed()`), sem campo novo no save. Pedido atrasado (rollCount antigo) não muda nada. O botão Continuar
  mostra a dica "o novo giro restante é descartado" quando ainda há giro. Fichas antigas que ainda guardam o giro
  podem usá-lo uma vez ou confirmar.

## 7. Roleta

A máquina de estados da Etapa 4.5 (`IDLE → WAITING → SPINNING → REVEAL → DONE`, resposta a todo pedido, timeout) já
estava funcionando e não foi reimplementada. Mudanças: o traço aparece abaixo dos atributos no resultado (nome e
Buff/Debuff/Neutro; descrição na dica) e Continuar confirma o nascimento. Oito origens, Família Artística e o alias
`familia_politica` → `familia_artistica` seguem iguais.

## 8. Character Hub e painel do inventário

- Menu K: origem e os seis atributos na coluna do meio; as quatro necessidades em 2×2; na coluna da direita, abaixo
  dos botões e dos pontos, a seção "Traços de nascimento" (nome, Buff/Debuff/Neutro, descrição curta e a linha de
  efeito). Sem abas novas: coube no painel.
- Painel Body & Traits (inventário): quatro medidores; embaixo "Origem: ..." e "Traço: ..." (antes a linha
  "Traits/Origem" mostrava a origem, o que misturava os conceitos). Dica do traço ao passar o mouse.
- Nenhuma HUD 2×2 na tela de jogo; o orbe de Emoção continua.

## 9. Testes executados

| Teste | Resultado |
|---|---|
| `./gradlew build` | OK |
| `bash .github/scripts/server-smoke-test.sh` (inclui `/tupini selftest`) | OK (`Autoteste RespectCraft: OK`) |
| `python3 tools/player_anim/bbsync.py` | OK (nada de animação mudou) |

Casos novos no `SelfTest` (rodam no smoke test): Hidratação/Energia não mudam e não derrubam a Emoção, deltas de
consumíveis ignoram as legadas, quatro necessidades ativas; nascimento sorteia 1 traço, reroll substitui (não soma),
todos os traços sorteáveis, ids únicos, nenhum com efeito; traço não altera atributos/origem/pontos; duplicatas e
ids desconhecidos; confirmação descarta o novo giro e mantém o resultado, confirmação atrasada/sem nascimento não
muda nada; **ficha da v0.5.7 sem `birth_traits`** (com `familia_politica`) carrega intacta; traços ida e volta no
NBT; ficha ida e volta pelo `StreamCodec` (com e sem traços, padrão).

Cliente real (Xvfb, servidor dedicado, `/tick freeze`), evidências em `docs/evidencias/etapa4_5_rev/`:

| Cenário | Resultado |
|---|---|
| Criador sem retrato 2D e sem a frase; cor do cabelo só pela paleta, com nome e dica; prévia 3D muda na hora | OK — `03_cabelo_ruivo_tooltip.png` (abas, 1ª versão), `21_criador_cor_branca.png` (página única) |
| Mesmo criador em GUI escala 2 (tela menor, 427×250) | OK — `25_criador_pagina_unica_escala2.png` |
| Roleta: giro → para na origem → seis atributos → traço (Heavy Loader, Buff); dica de Continuar | OK — `06_primeiro_giro.png`, `08_continuar_tooltip.png` |
| Novo giro: origem, atributos e traço trocados (Favela, Tiny Dick); "Girar de novo (0)" desativado | OK — `09_reroll.png` |
| Continuar → menu K com 6 atributos, 4 necessidades e o traço | OK — `10_hub.png` |
| Inventário: 4 necessidades, Origem e Traço separados | OK — `11_inventario.png`, `15_inventario_escala2.png` |
| Reabrir a roleta pelo menu: mesmo resultado, sem giro | OK — `12_roleta_reaberta.png` |
| Reconexão (cliente reiniciado): ficha e traço iguais; `/tupini admin perfil` = `origem=favela rerolls=0 tracos=tiny_dick` | OK — `13_hub_reconectado_escala2.png` |
| 2º jogador: um giro e Continuar **com o novo giro guardado** → servidor confirma (`rerolls=0 tracos=loadless`); roleta reaberta com "(0)" | OK — `23_juca_hub_apos_continuar.png`, `24_juca_roleta_confirmada.png` |

Não repetidos (fora do escopo): Finisher Engine, combos, Blockbench, animações, contador de derrotas, multiplayer
de finalizações. Os medidores foram vistos em modo sobrevivência com o jogo congelado (valores estáveis); o efeito
"sem penalidade de sede/cansaço" é coberto pelo `SelfTest` e pela remoção do código.

## 10. Limitações

- Habilidades `cantil` e `rede` ficaram sem efeito (só hidratação/energia). Precisam de efeito novo ou de troca na
  árvore — decisão de design do dono; ids preservados para não quebrar saves.
- Traços não têm efeito de jogo (por pedido). Nomes/descrições são provisórios; ids são estáveis a partir daqui.
- Personagens que nasceram antes desta versão ficam sem traço (sem migração retroativa). Se o dono quiser dar traço
  a eles, o caminho seguro é sortear na próxima confirmação/reroll, não no login.
- Hidratação/Energia antigas ficam congeladas no save; reativar exige rever `NeedsRules`, `NeedsService`, telas e
  as habilidades acima.
- Fechar a roleta com Esc sem Continuar não confirma: o novo giro segue disponível pelo menu até ser usado ou
  confirmado.

## 11. Entrega

Branch `claude/kind-faraday-75g3gf`. Commits: criador (retrato, nota e paleta), Hidratação/Energia inativas, traços de
nascimento + confirmação + telas, criador em página única, documentação. PR: [Juann027/MineModding_01#27](https://github.com/Juann027/MineModding_01/pull/27), integrado com merge commit (o hash fica no histórico da `main`).
