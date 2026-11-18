# Handoff — Etapa 4.7: raridades do Family Legacy

Status: **implementada e desligada da roleta ao vivo** (catálogo de famílias ainda não finalizado).

## Distribuição aprovada (substitui todas as anteriores)

| Raridade (`FamilyLegacyRarity`) | Chance | Milésimos | Bilhetes (0–999) |
|---|---:|---:|---|
| Common | 51,5% | 515 | 0–514 |
| Uncommon | 30,0% | 300 | 515–814 |
| Rare | 10,0% | 100 | 815–914 |
| Epic | 5,0% | 50 | 915–964 |
| Legendary | 3,0% | 30 | 965–994 |
| **BONER** (mais alta) | 0,5% | 5 | 995–999 |
| **Total** | **100,0%** | **1000** | |

A ordem do enum é o ranking: BONER fica acima de Legendary (`rank()`, `isHigherThan`, `highest()`).

## Como funciona

- `profile.FamilyLegacyOdds`: chances inteiras em milésimos (resolução 0,1%) que somam **exatamente** 1000. O sorteio
  tira um bilhete `nextInt(1000)` e converte pela soma acumulada, então cada raridade tem exatamente a sua fração. Uma
  distribuição que não soma 100,0%, tem negativo, raridade faltando ou mais de uma casa decimal é **recusada**
  (nunca normalizada).
- `profile.FamilyLegacyRoll`: servidor autoritativo. **Primeiro a raridade, depois** uma família elegível dentro dela
  (uniforme). Se a raridade sorteada não tem família elegível, o resultado mantém a raridade, sem família, com
  `Status.NO_FAMILY_IN_RARITY` — sem novo sorteio e sem cair para outra raridade.
- `profile.FamilyLegacy` (id, raridade, `BirthTrait.Eligibility`) e `profile.FamilyLegacies` (catálogo validado,
  ids únicos). O catálogo oficial `FamilyLegacies.CATALOG` está **vazio** e `CATALOG_FINALIZED = false`; nenhuma
  família nem efeito de jogo foi inventado.
- `profile.FamilyLegacyConfig`: config SERVER `config/tupinicraft-family-legacy-server.toml`
  (`[family_legacy_rarity]`, porcentagens `common` … `boner`). Config inválida → erro no log e uso de
  `FamilyLegacyOdds.DEFAULT`.
- Texto visível em inglês: `family_legacy.tupinicraft.rarity.<id>` no `en_us.json` (BONER em maiúsculas).
- Comandos (permissão 2, mensagens em inglês): `/tupini familylegacy odds` (chances ativas, famílias por raridade,
  se o catálogo foi finalizado e se está ao vivo) e `/tupini familylegacy simulate <n>` (simula só a raridade; não
  grava nada).
- `SelfTest.familyLegacy`: chances exatas, contagem de bilhetes, limites, validação da config, amostra de 200 000
  sorteios com raridades vazias (prova que não há redistribuição), elegibilidade por corpo, ids duplicados e a trava
  da roleta ao vivo.

## O que **não** mudou

- `BirthRoulette.roll`, `CharacterProfile` e seus Codecs/rede: nenhum campo novo, nenhum consumo extra do
  `RandomGenerator` da roleta. Saves, origens, atributos, traços de nascimento e o único novo giro continuam iguais.
- `FamilyLegacies.isLive()` é falso; nada chama o sorteio fora do comando de simulação e do autoteste.

## Para ligar quando o catálogo for aprovado

1. Cadastrar as famílias aprovadas em `FamilyLegacies.CATALOG` (ids persistentes) e as chaves de nome no `en_us.json`.
2. Gravar o resultado na ficha com um campo **opcional** novo no Codec de `CharacterProfile` (perfis antigos sem o
   campo continuam válidos) e incluí-lo no zeramento do novo giro.
3. Chamar `FamilyLegacyRoll.roll(random, FamilyLegacyConfig.odds(), FamilyLegacies.CATALOG, body)` no servidor
   durante o sorteio de nascimento e mudar `CATALOG_FINALIZED` para `true`.
4. Decidir (com o dono) o que exibir quando sair `NO_FAMILY_IN_RARITY`; o sistema não redistribui.

## Limitações

- O escopo anterior da Etapa 4.7 (além das raridades) não está registrado neste repositório; esta entrega cobre só o
  sistema de raridades e não mexe em outras partes.
- Sem família cadastrada, toda simulação termina em `NO_FAMILY_IN_RARITY` (esperado).
