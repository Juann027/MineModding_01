# HANDOFF — Etapa 3.1 (correções de design: Coom, HUD, roleta, prévia 3D e consumíveis)

Continuação da Etapa 3 (`HANDOFF_ETAPA3.md`). Nada foi reconstruído: só as cinco diferenças pedidas foram
corrigidas. Finisher Engine, câmera cinematográfica, Animation Manager, animações GeckoLib, Skill Tree,
renderizador das Body Layers, redes de animação e mobs **não foram alterados**.

## Estado

- Base: `main` em `e8b80fe` (merge do PR #19). Versão: `0.5.6` (`mod_version` não mudou → sem Release).
- Branch: `claude/great-faraday-teyxut`. Commit e PR: ver o fim deste documento.

## O que mudou

### A — Medidor Coom (restaurado)

- `Need.COOM` (id `coom`, cor rosa, "cheio = crítico"), campo `coom` em `Needs` (0–10000 = 0–100%).
  Persistido no attachment `tupinicraft:needs` (campo opcional: saves da Etapa 3 carregam com 0) e sincronizado
  pelo mesmo `NeedsSync` (StreamCodec com 6 campos). O servidor é a única fonte.
- Acúmulo em `NeedsRules.tickSecond`: `COOM_PER_SECOND` (8/s ≈ 21 min de 0 a 100%) ×
  **taxa configurável** (`config/tupinicraft-needs-server.toml`, `necessidades.coom_taxa`, 0–10, padrão 1.0; 0 desliga) ×
  **atributos** (`coomAttributeMultiplier`: Vigor +5%/ponto acima da base, Carisma −3%/ponto; faixa 0,6–1,6) ×
  **habilidades** (`SkillService.needRate(profile, "coom")`, mesmo gancho das outras necessidades) ×
  **consumíveis** (ritmos dos efeitos ativos). Dormindo, acumula a 30%.
- Efeitos: acima de 80% o alvo da Emoção cai 10 pontos (`COOM_MOOD_PENALTY`); acima de 90%, "distraído"
  (fadiga de mineração I). Aviso na barra de ação ao cruzar 80%. Reduzem: Chá Calmante (−25% e ritmo ×0,3).
- **Limite (proposital):** o Coom é só um recurso abstrato de gameplay. Não há cenas, animações nem interações
  íntimas; também não há "alívio" próprio além dos consumíveis/comando. As interfaces (necessidade, ritmo,
  efeitos, sincronização) ficam prontas para uma etapa futura.

### B — HUD e painel Body & Traits

- **Removida** a grade 2x2 permanente (`client.hud.NeedsHudOverlay` apagado, camada `tupinicraft:needs` não é mais
  registrada). Na tela de jogo ficam só o **orbe de Emoção** (com as expressões) e os elementos vanilla.
- `BodyStatusInventoryPanel` (painel ao lado do inventário, sem blur, somente leitura) agora mostra:
  medidores principais **Coom, Bexiga, Intestino, Emoção** e, separados em "Sobrevivência", **Hidratação e Energia**.
  Cada linha: ícone (item reduzido a 8 px), nome, valor, barra e estado crítico (nome/valor vermelhos, barra
  piscando; mesmos limites do servidor: `NeedsRules.LOW`/`URGENT`). Dica (tooltip) por linha com descrição, aviso
  crítico e consumíveis ativos que mexem naquela necessidade; rodapé "Efeitos ativos: N" com dica dos efeitos e
  segundos restantes.
- Posição: lado livre do inventário; com efeitos de poção ativos vai para a esquerda (o vanilla lista os efeitos à
  direita); altura 169 px, presa dentro da tela; GUIs estreitas usam o modo compacto (abreviações). Nenhuma lógica de
  medidor foi reimplementada: lê `ProfileClientCache`.
- "Humor" passou a se chamar **Emoção** na interface (o id persistido continua `humor`).
- O menu K (`CharacterHubScreen`) já iterava `Need.values()` e mostra o Coom sem mudança.

### C — Roleta de nascimento (um único novo giro)

O fluxo já sorteava no servidor e a tela só apresentava; foram fechadas as brechas:

- `CharacterProfile` normaliza `rerolls` para `0..BirthRoulette.FREE_REROLLS` (= 1) e para 0 antes do nascimento —
  fichas antigas ou corrompidas são corrigidas ao carregar, sem apagar origem/atributos/habilidades.
- `CharacterProfile.canReroll()` é a única regra usada por `ProfileService.requestRoll`.
- O giro forçado do admin (`/tupini admin roleta`) não concede mais um novo giro ao jogador (mantém o que ele tinha).
- Regras (validadas no autoteste e no teste com cliente): servidor sorteia uma vez; resultado salvo no attachment
  `tupinicraft:character_profile` (sobrevive a reinício e morte); 1 novo giro; 3º pedido é recusado e a tela volta
  ao estado real; editar aparência não toca na ficha (`CharacterAppearanceService` é separado); a animação da tela
  só desacelera até a fatia que o servidor mandou. Animações, sons e VFX da roleta não mudaram.

### D — Pele e rosto na prévia 3D

- Novas Body Layers (só registro de definições em `BodyLayers.registerFaceAndSkin`; o renderizador não mudou):
  - `face/skin`: placa 8x8 logo à frente do rosto, tingida com o tom escolhido (`face_skin.png`, branca);
  - `face/neutro|feliz|serio`: olhos, sobrancelhas e boca (`face_<id>.png`), um pouco à frente da placa;
  - `skin/right_hand_*`, `skin/left_hand_*` (classic e slim): os 3 px de baixo de cada braço no tom escolhido.
- Como valem para qualquer `PlayerRenderer`, aparecem igual no jogo, no criador (prévia local
  `CharacterAppearanceClientCache.setPreview`) e no menu K; seguem a pose do rig GeckoLib e ficam por baixo das
  camadas externas da skin (chapéu/mangas). A aparência salva é a mesma que a prévia mostra e continua
  sincronizada entre jogadores pelo fluxo existente.
- **A skin da conta é preservada:** nada é substituído na textura; só rosto e mãos recebem a camada. Novas opções
  **"Da skin"** (índice 3, anexadas no fim — o formato empacotado continua compatível) desligam a camada de pele
  e/ou de rosto. Com tom "Da skin" e rosto personalizado, a placa do rosto usa o tom médio.
- O retrato 2D continua, como referência rápida.

### E — Consumíveis das necessidades (definições reutilizáveis)

- `NeedEffectDef` (record + builder, só dados): imediato, gradual (total distribuído por segundo), atrasado
  (variação + buffs/debuffs + som), duração, ritmos por necessidade, buffs/debuffs vanilla, recarga do item,
  partícula e sons. `NeedEffectTimeline` (puro) diz o que acontece a cada segundo; `NeedsService.consume/tickEffects`
  aplica no servidor. Efeitos ativos ficam no attachment **`tupinicraft:need_effects`** (salvo, não copiado na morte;
  consumir de novo reinicia em vez de acumular) e vão ao dono por `NeedEffectsSync` quando a lista muda, no login e
  no respawn (o cliente conta os segundos sozinho).
- `ConsumableItem(props, NeedEffectDef, Kind.FOOD|DRINK|PILL)` — os 6 itens da Etapa 3 viraram definições com o
  mesmo efeito imediato. Tooltip lista as variações, a descrição, a recarga e "efeito fictício de jogo".

| Item | Efeito |
|---|---|
| Laxante Industrial (pílula) | Nada na hora; **30 s depois** Intestino +65%, lentidão 6 s; ritmo do intestino ×2 por 90 s. Recarga 60 s. |
| Antidiarreico (pílula) | Intestino −15% e ritmo ×0,2 por 5 min. |
| Café Extraforte (bebida) | Energia +30%, Bexiga +15%; Pressa 60 s e Velocidade 30 s; ritmo da bexiga ×1,5 por 2 min. |
| Chá Diurético (bebida) | Hidratação +15%; ritmo da bexiga ×2,5 e +10% gradual por 3 min. |
| Chá Calmante (bebida) | Emoção +15% (+10% gradual em 3 min), Coom −25% e ritmo ×0,3 por 3 min, Regeneração 5 s. |
| Afrodisíaco Misterioso (bebida) | Ritmo do Coom ×3 e +15% gradual por 2 min; Emoção +5%. Recarga 60 s. |
| Pílula Azul (pílula) | Buff fictício de desempenho: Força, Salto e Resistência I por 90 s; ritmo do Coom ×1,5. Recarga 120 s. |
| Energético do Tigrinho (bebida) | Energia +40%, Velocidade/Pressa II por 60 s (energia não cai); **depois de 60 s**: Energia −35%, Emoção −12%, Lentidão e Fadiga 30 s. |

Texturas provisórias geradas por `tools/gen_ui_textures.py`; sons novos `needs.effect` e `needs.effect_late`
(eventos vanilla em `sounds.json`).

### Como adicionar um consumível

1. Registre a definição em `needs.NeedEffects`:
   `NeedEffectDef.builder("meu_item").now(Need.ENERGY, 2_000).gradual(Need.MOOD, 500).rate(Need.BLADDER, 1.5F).over(60)
   .after(30).later(Need.BOWEL, 1_000).laterEffect(MobEffects.CONFUSION, 100, 0).cooldown(200)
   .particle(ParticleTypes.HEART).sound(ModSounds.NEEDS_EFFECT).build()` (unidades: 100 = 1%).
2. Em `ModItems`: `drink("meu_item", NeedEffects.MEU_ITEM)` (ou `pill`/`food`) e inclua em `consumables()`.
3. Modelo `models/item/meu_item.json`, textura, `item.tupinicraft.meu_item` e `effect.tupinicraft.need.meu_item.desc`
   (+ `msg.tupinicraft.needs.effect_late.meu_item` se tiver estágio atrasado). O autoteste valida a linha do tempo de
   todas as definições.

## Comandos novos

- `/tupini admin efeito <jogador> <id>` — aplica o efeito de um consumível (teste).
- `/tupini admin necessidade <jogador> coom <0-100>` (já existia; agora aceita `coom`).
- `/tupini admin perfil <jogador>` mostra também `coom` e os efeitos ativos.

## Arquivos principais

`needs/{Need,Needs,NeedsDelta,NeedsRules,NeedsService,NeedsConfig,NeedEffectDef,NeedEffectTimeline,NeedEffects,ActiveNeedEffects,ConsumableItem}.java`,
`client/hud/{BodyStatusInventoryPanel,HudMeters}.java`, `client/bodylayer/{BodyLayers,FaceMeshes}.java`,
`character/CharacterAppearance.java`, `profile/{CharacterProfile,ProfileService}.java`, `registry/{ModItems,ModAttachments,ModSounds}.java`,
`network/{GameplayNetworking,ProfileClientCache}.java`, `command/TupiniCommands.java`, `selftest/SelfTest.java`,
texturas `textures/entity/character/face_*.png`, `skin_white.png`, `textures/item/<8 itens>.png`.

## Testes realizados

| Verificação | Resultado |
|---|---|
| `./gradlew build` | OK |
| Teste de fumaça do servidor dedicado (inclui `/tupini selftest`) | OK |
| Autoteste novo: acúmulo/taxa 0/atributos/emoção do Coom, save antigo sem `coom`, gradual fecha o total, estágio atrasado dispara 1x, expiração, ritmos, repetir reinicia, Codec dos efeitos, regra de 1 novo giro e normalização de fichas antigas | OK |
| Cliente real (Xvfb): HUD só com orbe de Emoção (sem grade) | OK |
| Criador: tom de pele e rosto mudam o modelo 3D ao vivo; "Da skin" desliga a camada; salvar → roleta | OK |
| Roleta: giro, 1 novo giro, 3º pedido recusado (`rerolls=0`), origem/atributos iguais após reiniciar o servidor | OK |
| Energético usado com o mouse: buffs na hora, tombo de energia aos 60 s; laxante (comando) disparou aos 30 s; afrodisíaco levou o Coom a 100% | OK |
| Painel Body & Traits com 6 medidores, efeitos ativos e estado crítico; com efeitos de poção vai para a esquerda | OK |

Não testado: dois clientes simultâneos (a sincronização de aparência usa o mesmo pacote/ cache de antes; as novas
camadas leem esse cache, como o cabelo); escala de GUI 3+; GPU real; som (sem OpenAL no Xvfb).

## Limitações restantes

- A placa do rosto cobre o rosto da skin da conta quando um rosto do criador é escolhido (use "Da skin" para manter o
  original). Rostos e mãos são arte provisória 8x8.
- Com tom "Da skin" e rosto personalizado, a placa usa o tom médio (não há leitura dos pixels da skin baixada).
- Com o livro de receitas aberto **e** efeitos de poção ativos, o painel pode encostar na lista de efeitos vanilla.
- Coom: sem ações próprias de alívio nem conteúdo adulto (fora do escopo desta etapa, por pedido).
- O cliente estima os segundos restantes dos efeitos a partir do último pacote (pode variar ~1 s).
