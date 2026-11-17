# HANDOFF — Etapa 4.5: RespectCraft, organização dos handoffs e correção da roleta

Base: `main` em `828e683` (merge do PR #25). `mod_version=0.5.7` não mudou; nenhuma Release foi publicada.
PR: ver a seção "Entrega" no fim.

## 1. Rebranding RespectCraft

Só o **nome público** mudou.

| Arquivo | O que mudou |
|---|---|
| `gradle.properties` | `mod_name=RespectCraft` (vira o `displayName` da lista de mods) |
| `src/main/templates/META-INF/neoforge.mods.toml` | descrição e comentário |
| `assets/tupinicraft/lang/pt_br.json`, `en_us.json` | categoria de teclas, aba criativa, nome da tecla K, títulos do criador, menu, roleta e habilidades |
| `command/TupiniCommands.java`, `.github/scripts/server-smoke-test.sh` | mensagem `Autoteste RespectCraft: OK` (o teste de fumaça procura essa frase) |
| `testgame/FinisherTestMechanic.java`, `needs/NeedsConfig.java` | prefixo `[RespectCraft Teste]` e comentário do arquivo de config |
| `README.md`, `CLAUDE.md`, `AGENTS.md` | títulos e aviso de que os ids internos continuam `tupinicraft` |

**Preservados (não renomear):** `mod_id=tupinicraft`, pacote `com.tupinicraft`, classes `TupiniCraft*`, namespace
`tupinicraft`, nome do jar `tupinicraft-<versão>.jar`, ids de itens, `tupinicraft:leao_do_fim`, habilidades,
animações, finalizações, origens e atributos, attachments (`tupinicraft:character_profile` etc.), chaves de
`persistentData`, config `tupinicraft-needs-server.toml`, comandos `/tupini`, `/tupinianim`, `/tupinitest`,
`/leaodofim`, o repositório GitHub e os handoffs históricos. `.github/release-*.txt|md` descrevem a 0.5.7 publicada e
não foram alterados: ajuste o nome neles quando a próxima Release for pedida.

## 2. Documentação

- Os oito `HANDOFF_*.md` foram movidos com `git mv` da raiz para `docs/handoffs/`, sem mudar o conteúdo.
- Índice novo: [`docs/handoffs/README.md`](README.md) (etapa, sistema, link, status, limitações e qual ler).
- `CLAUDE.md` e `AGENTS.md` apontam para o índice e pedem que só o handoff do sistema tocado seja lido.
- Caminhos atualizados em `tools/player_anim/README.md`, `tools/player_anim/bbsync.py` e `tools/visual-test/helpers.sh`.
- `CLAUDE.md`, `AGENTS.md` e `README.md` continuam na raiz. Evidências seguem em `docs/evidencias/`.

## 3. Roleta: causa real do giro infinito

Fluxo antigo: a tela mandava `BirthRollRequest(reroll)` e girava até ver o `rollCount` da ficha mudar; após 200 ticks
voltava ao estado anterior.

1. **O servidor descartava pedidos em silêncio.** `ProfileService.requestRoll` tinha um cooldown de 40 ticks
   medido em `serverLevel().getGameTime()` contra um valor salvo no `persistentData` (`tupinicraft_last_roll`), e
   ignorava sem resposta o pedido de novo giro de quem não nasceu. Sempre que o relógio do mundo não andava o
   suficiente (jogo congelado com `/tick freeze`, primeiros 2 s de um mundo novo, pedido repetido) **nenhum pacote
   voltava**. A tela girava a toda velocidade sem escolher nada e com Continuar travado, parava depois de 10 s num
   ângulo que não era a origem da ficha, e cada novo clique repetia o ciclo.
2. **A tela só reconhecia a ficha nova no estado "esperando".** Uma ficha que chegasse fora dele (resposta atrasada
   depois do timeout, giro do admin, ficha zerada) não atualizava botões nem resultado: Continuar ficava desativado,
   o rótulo errado, e o clique seguinte mandava um **novo giro** que gastava o reroll.

Reproduzido no cliente real antes da correção (`docs/evidencias/etapa4_5/01_antes_reroll_sem_resposta.png`): o
reroll girou ~10 s sem resultado, com Continuar desativado, sem nenhuma linha de sorteio no log do servidor, e parou
fora da fatia da origem mostrada. O caminho comum (servidor respondendo na hora) funcionava em dedicado e
singleplayer, por isso o build e os testes antigos não pegavam o defeito.

## 4. Correção

- `BirthRollRequest(reroll, expectedRollCount)`: o cliente manda o `rollCount` que estava vendo.
  `BirthRoulette.validate` (pura) recusa pedido sobre ficha desatualizada (`STALE`) e pedido sem giro restante
  (`NO_REROLLS`): **pedido repetido ou atrasado não gera resultado duplicado nem gasta o novo giro**.
- `BirthRollReply(outcome, rollCount)`: **todo pedido recebe resposta**, enviada depois do `ProfileSync`. O cooldown
  por `gameTime` saiu; a chave antiga do `persistentData` é apagada no próximo giro. Registrar de
  `GameplayNetworking` passou para a versão `"2"` (cliente e servidor precisam da mesma versão do mod, como antes).
- `BirthRouletteScreen`, estados: `IDLE` (aguardando sorteio) → `WAITING` (pedido enviado, girando) → `SPINNING`
  (resposta recebida, desacelerando até a origem da ficha) → `REVEAL` (atributos) → `DONE` (resultado disponível:
  Continuar ou o único novo giro) → Continuar abre o menu do personagem (concluído). Recusa ou timeout mostram um aviso
  e param a roda na origem real; ficha que chega sem pedido também é animada. Botões são recalculados a cada tick;
  Continuar só existe com resultado. A animação nunca decide dados.
- Mantidos: 8 origens, pesos e bônus, um primeiro giro + **um** novo giro opcional, giro do admin sem conceder giro,
  migração `familia_politica` → `familia_artistica`, formato salvo da ficha.
- `SelfTest`: primeiro giro aceito, novo giro antes de nascer recusado, novo giro aceito e consumido, pedido
  duplicado/atrasado recusado, sem giros restantes e resposta desconhecida.

## 5. Testes

| Teste | Resultado |
|---|---|
| `./gradlew build` | OK |
| `bash .github/scripts/server-smoke-test.sh` (inclui `/tupini selftest`) | OK (`Autoteste RespectCraft: OK`) |
| `python3 tools/player_anim/bbsync.py` (caminho no aviso mudou) | OK |
| Cliente real (Xvfb), servidor dedicado, jogador novo: criador → roleta → giro → parou em Classe Média, atributos e Continuar liberado | OK — `02_criador_e_primeiro_giro.png` |
| Reroll **com o jogo congelado** (`/tick freeze`, a condição que travava): aceito e resolvido; 3º clique com "Girar de novo (0)" desativado | OK — `03_reroll_com_jogo_congelado.png` |
| Lista de mods: "RespectCraft 0.5.7", ModID `tupinicraft` | OK — `04_lista_de_mods.png` |
| Continuar → menu; cliente fechado, **servidor reiniciado**, reconexão: mesma origem e atributos; roleta com novo giro 0; `/tupini admin perfil` = `origem=classe_media rerolls=0` | OK — `05_reconexao_apos_reinicio.png` |
| Giro do admin com a roleta aberta: a tela anima até a nova origem, sem conceder giro | OK — `06_resultado_sem_pedido.png` |
| Antes da correção, singleplayer e dedicado (caminho comum) e reroll sem resposta | caminho comum OK; reroll sem resposta reproduziu o defeito |

Não repetidos (fora do escopo): animações de combate, Finisher Engine, Blockbench, multiplayer de finalizações,
Coom e HUD de necessidades.

## Limitações

- O timeout de resposta (10 s) só foi exercitado antes da correção; depois dela o servidor sempre responde, então o
  aviso de timeout aparece só se a conexão falhar de verdade.
- Depois de Continuar, o único novo giro continua disponível pelo menu (regra das Etapas 3/3.1: zera habilidades e
  devolve pontos). O resultado salvo é definitivo no sentido de que reabrir a tela ou reconectar não dá giro novo.
- Cliente e servidor precisam da mesma versão do mod (o registrar de rede agora é `"2"`).

## Entrega

Branch `claude/confident-mayer-87l0tl`, commits: rebranding, handoffs, correção da roleta, este handoff.
