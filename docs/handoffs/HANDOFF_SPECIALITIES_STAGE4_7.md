# Handoff — Etapa 4.7: Specialities

Status: **concluída**. A roleta de nascimento sorteia uma **Speciality** no lugar das antigas origens sociais.

> Histórico: a primeira versão desta etapa (nesta mesma branch, nunca integrada) se chamava *Family Legacy*, usava
> outra distribuição (51,5/30/10/5/3/0,5) e um catálogo vazio. Foi substituída por esta especificação; nada do
> nome antigo ficou no código, na config ou nos comandos.

## Distribuição de raridades

| Raridade (`SpecialityRarity`) | Chance | Milésimos | Bilhetes (0–999) | Specialities |
|---|---:|---:|---|---|
| Common | 41,5% | 415 | 0–414 | The Vanilla, The Voyeurs, The Exhibitionists, The Foot Worshippers |
| Uncommon | 25,0% | 250 | 415–664 | The Ropebound, The Spitters |
| Rare | 20,0% | 200 | 665–864 | Showers of Gold, The Bootstompers, The Dominants, The Latex Royals |
| Epic | 10,0% | 100 | 865–964 | The Scatborn, The Cuckholders, The Humiliationists |
| Legendary | 3,0% | 30 | 965–994 | Ultra Gooner |
| **BONER** (mais alta) | 0,5% | 5 | 995–999 | Island Guy |
| **Total** | **100,0%** | **1000** | | **15** |

A ordem do enum é o ranking: BONER fica acima de Legendary (`rank()`, `isHigherThan`, `highest()`). Dentro da
raridade a escolha é uniforme entre as Specialities elegíveis (hoje todas são `Eligibility.ALL`). Chance final de
cada uma = chance da raridade ÷ quantidade na raridade (ex.: cada Common 10,375%, Island Guy 0,5%).

## Código

| Classe | Papel |
|---|---|
| `profile.Speciality` | Enum com as 15 (id persistente, raridade, elegibilidade). Ids: `vanilla`, `voyeurs`, `exhibitionists`, `foot_worshippers`, `ropebound`, `spitters`, `showers_of_gold`, `bootstompers`, `dominants`, `latex_royals`, `scatborn`, `cuckholders`, `humiliationists`, `ultra_gooner`, `island_guy`. |
| `profile.SpecialityRarity` | Seis raridades, chance padrão em milésimos, cor e chave de lang. |
| `profile.SpecialityOdds` | Distribuição validada (soma **exatamente** 1000, sem negativo, uma casa decimal). Sorteio por bilhete `nextInt(1000)` pela soma acumulada. Nunca normaliza. |
| `profile.Specialities` | Catálogo agrupado por raridade (`CATALOG` = o enum inteiro; testes montam catálogos parciais). |
| `profile.SpecialityRoll` | Raridade primeiro, depois Speciality elegível; raridade sem Speciality → `NO_SPECIALITY_IN_RARITY` (sem novo sorteio, sem cair para outra raridade). |
| `profile.SpecialityConfig` | Config SERVER `config/tupinicraft-speciality-server.toml`, seção `[speciality_rarity]` (`common` … `boner`, em %). Inválida → erro no log e `SpecialityOdds.DEFAULT`. |
| `profile.BirthRoulette.roll` | Ordem: raridade → Speciality → atributos → traços. `Result(speciality, attributes, traits)`. Se o catálogo oficial ficasse sem Speciality numa raridade, lança erro (o autoteste garante que isso não acontece). |
| `profile.CharacterProfile` | Campos novos `birthSpeciality`/`currentSpeciality` (Codec `birth_speciality`/`current_speciality`, opcionais; também no `STREAM_CODEC`). `withBirth` grava a mesma Speciality nos dois e deixa `origin` vazio. |
| `profile.BirthOrigin` | **Legado**: não é mais sorteado; só exibe fichas antigas (o bônus já está nos atributos gravados). |

- **Atributos:** base 3 + 12 pontos sorteados (teto 8), como antes, mas **sem** bônus de origem: Speciality não dá
  bônus, perk, modificador nem efeito. Pontos iniciais (Inteligência), traços, reroll único e Continuar não mudaram.
- **Servidor autoritativo:** só `ProfileService.roll` sorteia; a tela só apresenta a ficha sincronizada.
- **Nascimento × ativa:** `birthSpeciality` é a de nascimento; `currentSpeciality` é a ativa, hoje sempre igual
  (vazia no save = a de nascimento). A futura troca no fim de jogo só precisará mudar `currentSpeciality`
  (`CharacterProfile` já grava os dois separados); **a troca não foi implementada**.
- **Telas (texto em inglês, `en_us.json`):**
  - Roleta: roda com as seis raridades (fatias pela distribuição aprovada; a config do servidor muda só o sorteio). Ao
    parar mostra nome (cor da raridade), raridade e descrição curta.
  - Character Hub (K): nome, raridade e descrição acima dos atributos.
  - Painel Body & Traits do inventário: `Speciality: <nome>` com dica (raridade e descrição).
  - Chaves: `speciality.tupinicraft.<id>`, `.desc`, `speciality.tupinicraft.rarity.<id>`, `gui.tupinicraft.speciality`,
    `gui.tupinicraft.speciality.legacy_origin`.
- **Comandos** (permissão 2, mensagens em inglês):
  - `/tupini speciality list` lista o catálogo por raridade.
  - `/tupini speciality odds` mostra as chances ativas.
  - `/tupini speciality simulate <n>` simula sem gravar.
  - `/tupini admin roleta <jogador> [raridade]` é o **único** jeito de forçar uma raridade (teste administrativo:
    a Speciality sai dentro dela, atributos e traços são sorteados normalmente, o novo giro do jogador não muda).
  - `/tupini perfil` mostra `speciality`, `current` e `legacy_origin`.

## Compatibilidade de saves

- Ficha anterior à 4.7 (com `origin`, sem os campos novos) carrega **intacta**: mesma origem (inclusive o alias
  `familia_politica` → `familia_artistica`), atributos, traços, habilidades, pontos e novo giro. **Nenhuma Speciality
  é atribuída ao carregar.** As telas mostram a origem com "Legacy origin (born before Specialities)".
- Confirmar (Continuar) ou trocar o corpo numa ficha antiga não mexe na origem.
- Se a ficha antiga ainda tem o novo giro, usá-lo é um nascimento novo: sai uma Speciality e a origem fica vazia
  (habilidades zeradas e pontos devolvidos, como em qualquer novo giro).
- Id de Speciality desconhecido (versão futura) fica guardado, mas não aparece.
- Nada foi removido de registries. Recomende backup dos mundos antes de atualizar.

## Validação feita

- `./gradlew build` e `bash .github/scripts/server-smoke-test.sh`: OK (`RespectCraft self-test: OK`). O smoke test
  agora também roda `/tupini speciality odds` e confere `boner = 0.5% (specialities: 1)`.
- `SelfTest`:
  - Roleta: 40 000 nascimentos, frequência por raridade, todas as 15 sorteadas, soma de atributos sem bônus,
    raridade forçada e BONER forçado = Island Guy.
  - Raridades: chances exatas, contagem e limites dos bilhetes, validação da config.
  - Catálogo: as 15 com a raridade aprovada, nenhuma raridade vazia para os dois corpos.
  - Sem redistribuição: 200 000 sorteios com catálogo parcial.
  - Persistência: NBT e rede, ficha da v0.5.7 com origem, confirmação e troca de corpo em ficha antiga, novo giro de
    ficha antiga, `current_speciality` ausente, id desconhecido, nascimento × ativa gravadas separadas.
- Cliente real (Xvfb, 854×480; capturas em `docs/evidencias/etapa4_7/`):
  - `02_roulette_idle.png`: roda de raridades.
  - `03_roulette_spin.png`: giro normal pelo botão, Showers of Gold (Rare).
  - `04_roulette_boner_forced.png`: `/tupini admin roleta Tester boner` com a tela aberta, ponteiro na fatia
    BONER, Island Guy.
  - `05_hub.png`: Character Hub.
  - `06_inventory.png`: painel do inventário.

## Fora do escopo (não implementado)

- Perks/efeitos das Specialities (virão nas Skill Trees de cada uma).
- Troca da Speciality ativa no fim de jogo.
- Rage Mode e gravidez.

## Limitações

- As fatias da roda usam a distribuição padrão; se o servidor mudar a config, o sorteio segue a config, mas o
  tamanho visual das fatias não muda.
- A fatia BONER tem 1,8°: o ponteiro para no centro dela, mas ela é estreita.
- A fatia Common é clara e o rótulo branco fica com pouco contraste.
