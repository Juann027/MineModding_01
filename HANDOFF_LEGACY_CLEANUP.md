# HANDOFF — Etapa 4.4: limpeza do legado político e reset da documentação

Base: `main` em `e857d81` (merge do PR #24, v0.5.7). `mod_id` e `mod_version=0.5.7` não mudaram; nenhuma Release
foi publicada. PR: [Juann027/MineModding_01#25](https://github.com/Juann027/MineModding_01/pull/25) (o commit de merge aparece no PR).

## Removido

| O quê | Arquivos |
|---|---|
| Mob de teste Cabo Eleitoral | `entity/CaboEleitoralEntity`, `client/render/CaboEleitoralRenderer`, registro em `ModEntities`, ovo e entradas de aba em `ModItems`/`ModCreativeTabs`, `geo/`, `animations/`, textura, modelo do ovo, traduções |
| Urna eletrônica (sem item registrado) | `models/item/urna_eletronica.json`, `textures/item/urna_eletronica.png`, tradução |
| Máquina do Tigrinho (bloco nunca registrado) | `blockstates/`, `models/block/`, `models/item/tigrinho_machine.json`, `textures/gui/tigrinho/*`, tradução |
| Modelos `icone_*` (33, sem item registrado) | `models/item/icone_*.json` e traduções `item.tupinicraft.icone_*` |
| Ícones políticos/nacionais | `textures/item/skilltree/`: `bandeira_brasil`, `bolsa`, `cedulas`, `cristo_redentor`, `mst`, `policia_federal`, `receita_federal`, `rejeicao`, `constituicao` e as caricaturas de políticos `mito`, `painho`, `mao_em_l`, `comprimido`, `intelectual` |
| Backups dos sistemas arquivados | `backups/*.zip` (eleições, mandatos, economia, Tigrinho, árvores antigas; nada no build usava). Continuam no histórico do Git. |
| Notas obsoletas | `CORRECAO_MENU_K_0.5.5.md`, `FINISHER_COMBATE_TESTE.md`, `HUD_IMPLEMENTACAO.md`, `PERSONAGEM_CABELOS_MVP.md`, `PLAYER_GECKOLIB_PREVIEW.md` (descreviam classes que não existem mais) |
| Sobras do Gradle | repositório do Modrinth e propriedades `uilib_*`/`architectury_version` (UI Lib e Architectury já não eram dependências) |

## Alterado

- **Origem `familia_politica` → `familia_artistica`** ("Família Artística", mesmo peso 6, cor e bônus; ícone `trevo`).
  Continuam 8 origens e a regra de 1 novo giro.
- Ícones trocados (ids, nomes e efeitos iguais): Punhos de Aço → `estadio`, Lenda do Bairro (`combate/mito`) →
  `favela`, Bexiga de Aço → `penico`, Lábia → `cafezinho`, origem Família Intelectual → `cafe_extraforte`, botão
  Habilidades do menu K → `niobio`. Os novos `penico`/`cafezinho`/`cafe_extraforte` de 128 px são as texturas dos
  próprios itens do mod ampliadas 8× (sem arte nova).
- `neoforge.mods.toml`, `.github/release-notes.md` (modelo das próximas releases; a v0.5.7 publicada não foi mexida),
  `README.md`, `AGENTS.md` e `CLAUDE.md` (reescrito do zero, 149 linhas).
- Teste de fumaça: invoca só o Leão do Fim.

## Migração e saves

- `profile.LegacyOriginIds` converte `familia_politica` na leitura do Codec da ficha; o perfil é regravado com o
  id novo. Nascimento, atributos, habilidades, pontos e giros ficam intactos (atributos já incluem o bônus, que é igual).
  O id antigo existe só ali e no `SelfTest`, não no catálogo, no sorteio nem nas traduções.
- **Perda em mundos antigos:** entidades `tupinicraft:cabo_eleitoral` são descartadas ao carregar o chunk (aviso
  `Skipping Entity with id tupinicraft:cabo_eleitoral`) e ovos `cabo_eleitoral_spawn_egg` somem dos inventários.
  Nenhum outro dado salvo mudou. Faça backup antes de atualizar.

## Testes

- `./gradlew build`: OK. `python3 tools/player_anim/bbsync.py`: em sincronia.
- `bash .github/scripts/server-smoke-test.sh`: OK (Leão do Fim invocado, `Autoteste TupiniCraft: OK`, 18 habilidades,
  3 finalizações). O autoteste ganhou três checagens: NBT com `familia_politica` vira `familia_artistica` sem perder
  dados, a ficha migrada grava o id novo, o catálogo tem 8 origens sem a antiga.
- Cenário real: mundo criado com o código da `e857d81` contendo um Cabo Eleitoral e um Leão do Fim persistentes,
  aberto na versão limpa: servidor sobe sem exceções, o Cabo é descartado com aviso e o Leão continua no mundo.
- Sem teste com cliente real: não houve mudança de render; os ícones trocados são texturas 128×128 válidas.

## Referências restantes (justificadas)

- `familia_politica` em `LegacyOriginIds` e `SelfTest`: alias de migração de saves.
- "Cabo Eleitoral" no `README.md`: aviso de perda em mundos antigos.
- `CLAUDE.md`/`AGENTS.md`: regra para não reintroduzir o tema; "português do Brasil" = idioma.
- `HANDOFF_GECKOLIB_STAGE2.md`: frase histórica sobre o `CLAUDE.md` antigo.
- Conteúdo cultural mantido de propósito (não político): comidas, Sertão, Pampa, Amazônia, Favela, skills Jeitinho,
  Nióbio, Tigrinho (gíria), `carteira_trabalho` (ícone de Classe Média) e `cus` (Intestino Preso). Se o dono quiser
  remover também esses ícones institucionais, troque só o nome do ícone em `BirthOrigin`/`Skills`.
