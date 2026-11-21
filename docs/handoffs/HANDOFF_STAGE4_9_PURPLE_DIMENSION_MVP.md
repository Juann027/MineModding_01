# Handoff — Etapa 4.9: Inverted Realm (dimensão de céu roxo, MVP de teste)

Status: **implementada e testada** (build, teste de fumaça do servidor dedicado e cliente real em Xvfb).
Base: `main` em `f765e4b`. PR [#43](https://github.com/Juann027/MineModding_01/pull/43), integrado na `main` pelo
merge commit `5cd36e7` (CI **Build** verde: build + teste de fumaça). Versão continua `0.5.7` (sem release).

## 1. Pedido

Primeira versão jogável da nova dimensão: carregar, entrar e explorar; **céu roxo obrigatório**, ambiente próprio e
terreno visivelmente diferente do Overworld, usando os assets enviados. Sem lore, progressão, chefe, portal com missão
nem balanceamento. Acesso fácil para teste.

## 2. Material recebido e o que foi usado

| Arquivo | Situação | Uso |
|---|---|---|
| `Blocks.zip` | **Não chegou na sessão** (só os arquivos abaixo foram enviados) | Nada. O chão usa blocos vanilla até ele chegar (seção 6) |
| `avesso_fase2_etapaA_madeira.zip` ("O Avesso — Fase 2, Etapa A") | Pronto: 19 blocos, código, modelos, texturas, loot, receitas, tags, en_us/pt_br | **Integrado inteiro** como Madeira Retorcida (seção 3.2) |
| `agua_viva.geo.json` + `.animation.json` | Pronto (149 ossos, 692 cubos, ~5×9×5 blocos; clips idle, swim, hurt, death, attack_beam, grab) | Mob **Giant Jellyfish** (seção 3.4) |
| `agua_viva_filhote.geo.json` + `.animation.json` | Pronto (26 ossos, ~0,8 bloco; idle, swim, hurt, death) | Mob **Baby Jellyfish** |
| Imagens 1–4 | São as texturas das agua-vivas: 2 = `agua_viva.png` (128²), 1 = seu `_glowmask`, 3 = `agua_viva_filhote.png` (64²), 4 = seu `_glowmask` (alfa binário → recorte) | Texturas dos mobs; mapeamento conferido no cliente real |

Conferências feitas antes de integrar: todos os ossos animados existem nas geos, nenhuma UV fora da textura, alfa só
0/255, as APIs do pacote de madeira batem com o NeoForge 21.1.265 (construtor estendido de `Boat.Type`,
`ModelLayers.createBoatModelName` com `ResourceLocation.parse`).

## 3. O que foi implementado

### 3.1 Dimensão (tudo em dados)

| Arquivo | Conteúdo |
|---|---|
| `data/respectcraft/dimension_type/inverted_realm.json` | `fixed_time` **12400** (crepúsculo eterno: céu a 71 %, estrelas fracas, luz do céu 12), `effects` = `respectcraft:inverted_realm`, y 0–256, `natural: false` (bússola/relógio giram), sem invasões, `ambient_light` 0,08, monstros só em luz 0 (e o bioma não tem monstros) |
| `data/respectcraft/dimension/inverted_realm.json` | Gerador `minecraft:noise` com `respectcraft:inverted_realm`, bioma fixo `respectcraft:twisted_wilds` |
| `worldgen/noise_settings/inverted_realm.json` | `final_density` = máx(**chão**, **ilhas**). Chão: gradiente y 36→132 + `overworld/base_3d_noise` ×1,6 (morros com saliências, y ≈ 60–115) + piso forte em y 0–24 (nunca há buraco para o vazio). Ilhas: `end/base_3d_noise` −0,08 numa faixa y 148–236 (≈ 13 % das colunas). Mar em 60 (lagos violeta nos vales). Bloco padrão **deepslate**. Superfície: grama (ou **micélio** em manchas do ruído `surface`), terracota magenta no fundo de água, **terracota roxa** como subsolo e no teto das saliências/ilhas, bedrock em y 0–5 |
| `worldgen/biome/twisted_wilds.json` | Céu `#A855F7`, neblina `#B062D8`, água `#C04BFF`/`#3B0F66`, **grama `#9B4FD8`** e **folhas `#E0489A`** (tingem a grama/folhas vanilla), sem chuva, partícula ambiente de pó rosa→violeta, sons do warped forest, música `music.end`, cavernas e cânions vanilla. Spawns: `ambient` com as agua-vivas |
| `worldgen/configured_feature` + `placed_feature` | Árvores no **`twisted_log`**: copa ramificada (`cherry_trunk_placer`), tronco torto (`bending_trunk_placer`) e gigante 2×2 rara (`dark_oak`), folhas de carvalho tingidas, em manchas de floresta e clareiras (`noise_threshold_count`); flores violeta (allium, tulipa rosa); **brotos de ametista** (aglomerados/brotos que brilham); pedras de ametista. Vanilla reaproveitado: geodos, minérios (versões deepslate), nascentes, líquen brilhante, grama/capim |

### 3.2 Madeira Retorcida (`block.wood`, `registry.ModBlocks`)

Portada do pacote da Etapa A para o projeto: `inverted_realm` → `respectcraft` (ids, JSON, lang), pacote
`com.invertedrealm.wood` → `com.respectcraft.block.wood`. Diferenças em relação ao pacote:

- Blocos em `ModBlocks.BLOCKS`/`BLOCK_ENTITY_TYPES`, itens em `ModItems.ITEMS` (um dono por registro).
- O código de cliente (`Sheets.addWoodType` e renderizadores das placas) foi para `client.render.TwistedWoodRenderers`
  (regra de lado do projeto); o pacote usava `FMLEnvironment.dist`.
- Barco: entrada de enum `RESPECTCRAFT_TWISTED` (nome `respectcraft:twisted`), `enumExtensions` no `neoforge.mods.toml`.
- **`twisted_log` provisório** (o da Fase 1 estava no `Blocks.zip`): casca de `twisted_wood` (3 variantes) nas laterais
  e `twisted_log_top.png` gerado por `tools/inverted_realm/make_twisted_log_top.py` (anéis do descascado + borda de casca;
  `--check` confere). Id já definitivo: a arte da Fase 1 troca só os recursos. Descasca com machado (mantém o eixo).
- Só `en_us.json` (o `pt_br.json` do pacote não entrou). Itens na aba do RespectCraft e em "Building Blocks".
- Os scripts `tools/etapa_a/` e `merge_into_project.py` do pacote **não** foram trazidos (geram/validam para o
  namespace antigo); ficam no zip original.

20 blocos (os 19 do pacote + `twisted_log`), 21 itens (com barco e barco com baú), 2 block entities.

### 3.3 Céu e acesso (código)

- `client.dimension.InvertedRealmEffects` (registrado por `RegisterDimensionSpecialEffectsEvent`): sem nuvens vanilla,
  neblina que mantém o violeta, **brilho do horizonte magenta** no lugar do laranja, luz do céu levemente violeta no
  lightmap (tochas inalteradas). Nada de framebuffer, shader ou passada própria.
- `dimension.InvertedRealm` + `InvertedRealmCommands` → **`/respectcraft realm`** (permissão 2):
  - `enter [players]`: mesma coluna x/z (escala 1:1). Busca chão em anéis até 48 blocos: primeiro só grama/micélio,
    depois qualquer bloco firme; nunca folhas, fluido ou o alto de uma ilha flutuante (começa abaixo de y 146); 2 blocos
    livres acima. Sem chão, plataforma 3×3 de tábuas retorcidas em y 120. Guarda a volta em `persistentData`
    (`respectcraft_realm_return`, copiada no clone).
  - `leave [players]`: volta para onde o jogador estava; sem registro, ponto de surgimento do Overworld.
  - `check [radius]`: gera a chegada em (0, 0) e uma amostra de colunas; informa chão, bioma, hora fixa, faixa de
    altura, colunas sob ilhas e com água. Usado pelo teste de fumaça.
- Também funciona o vanilla: `/execute in respectcraft:inverted_realm run tp @s 0 120 0`.

### 3.4 Agua-vivas (`entity.AguaVivaEntity`, `client.render.AguaVivaRenderer`)

Uma classe para os dois tipos (`respectcraft:agua_viva` "Giant Jellyfish", 40 HP, caixa 4×8,5;
`respectcraft:agua_viva_filhote` "Baby Jellyfish", 8 HP, 0,8×0,9). `FlyingMob` **passivo**: impulsos lentos para um
ponto no ar (estilo Ghast, sem gravidade nem dano de queda), faixa de altura acima do chão (filhote 3–16, gigante
10–28). GeckoLib: `idle` parado, `swim` em movimento, `hurt` por gatilho, `death` segurado até sumir (2 s/3 s, sem
tombar). Brilho pelo `AutoGlowingGeoLayer`. Sons vanilla (sino de ametista, glow squid). Categoria AMBIENT no bioma
(filhote peso 12 em grupos de 2–4, gigante peso 1); surgem no ar livre sobre o terreno, nunca em cavernas; ovo e
`/summon` ficam persistentes. Ovos na aba do mod e em Spawn Eggs. **`attack_beam` e `grab` não são usados** (sem regra
de combate aprovada).

## 4. Validação

| Teste | Resultado |
|---|---|
| `./gradlew build` | OK |
| `bash .github/scripts/server-smoke-test.sh` (estendido) | **OK**: `Inverted Realm ready: arrival 0, 90, 0 on minecraft:grass_block, biome respectcraft:twisted_wilds, fixed time 12400`, Baby/Giant Jellyfish invocadas, `kill` da filhote sem erro, self-test OK, nada de classe de cliente no servidor |
| `python3 tools/player_anim/bbsync.py`, `test_export.py`, `make_twisted_log_top.py --check` | OK |
| Medição do terreno (`realm check 6`, 169 colunas) | chão y 62–111, 22 colunas (13 %) sob ilhas |
| Cliente real (Xvfb, llvmpipe, 854×480, servidor dedicado) | Entrada pelo comando em grama; **céu roxo** confirmado (violeta no alto, magenta/rosa no lado do sol), sem nuvens; terreno, árvores, cânions, lagos e ilhas gerando; voo em espectador sem erros; `leave` volta à posição salva; Madeira Retorcida colocada no mundo (troncos, tábuas, porta, alçapão, cerca, portão, escada, estante, bancada, placa com texto, placa pendurada) e **Twisted Boat** com textura; Giant e Baby Jellyfish com textura e brilho corretos; **14 Baby Jellyfish surgiram sozinhas** em ~2 min (nenhuma gigante nesse tempo); nenhum aviso de modelo/textura faltando do `respectcraft` |

Evidências em `docs/evidencias/etapa4_9/` (chegada, céu rosa no horizonte, agua-vivas, ilha flutuante, madeira,
spawns naturais). Sons não testados (o Xvfb não tem dispositivo de áudio).

## 5. Como testar

```
/respectcraft realm enter            (op; leva você, ou [players])
/respectcraft realm leave
/respectcraft realm check 4          (relatório do terreno)
/give @s respectcraft:twisted_planks 64
/summon respectcraft:agua_viva ~ ~5 ~12
```

Tudo da madeira e os ovos estão na aba **RespectCraft** do criativo.

## 6. O que ficou incompleto / para depois

- **`Blocks.zip` não chegou**: o chão é vanilla tingido (grama/micélio/terracota/deepslate). A lateral da grama vanilla
  ainda mostra terra marrom. Quando a Fase 1 chegar: trocar `default_block` e a `surface_rule` do `noise_settings`, o
  `dirt_provider`/folhas das árvores e as texturas do `twisted_log` (o id fica).
- Folhas são `oak_leaves` tingidas pelo bioma (não há folha retorcida própria); mudas não existem.
- **Sem portal** (só o comando). Sem progressão, estruturas, monstros, lore ou chefe.
- Agua-vivas sem ataque (`attack_beam`/`grab` prontos nos assets), sem drops, sem sons próprios; a gigante é rara no
  surgimento natural e precisa de espaço aberto (caixa 4×8,5).
- Uma dimensão nova só aparece em mundos criados ou abertos com esta versão; `/respectcraft realm` precisa de cheats/op.
- Testado só em llvmpipe/Fast; não medido em hardware real.

## 7. IDs novos (estáveis a partir daqui)

Dimensão/tipo/noise `respectcraft:inverted_realm`; bioma `respectcraft:twisted_wilds`; features `respectcraft:twisted_*`,
`patch_violet_flowers`, `patch_crystal_sprouts`, `amethyst_rock`; blocos `respectcraft:twisted_*`; block entities
`twisted_sign`, `twisted_hanging_sign`; entidades `agua_viva`, `agua_viva_filhote`; `WoodType`/`BlockSetType`/`Boat.Type`
`respectcraft:twisted`; `persistentData` `respectcraft_realm_return`.
