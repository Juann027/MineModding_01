# HANDOFF — Etapa 3 (UI, Character Creator, Skill Tree, Roleta, Necessidades e Finisher Engine)

Documento de continuidade. Leia junto com `HANDOFF_GECKOLIB_STAGE2.md` (rig, Body Layers e Animation Manager,
que **não** foram reimplementados: esta etapa só os usa).

## Estado

- Versão: `0.5.6` (`mod_version` não foi alterado; o merge não publica Release).
- Branch: `claude/sweet-lovelace-vcr9w1`, a partir de `main` em `4309988` (merge do PR #18).
- `./gradlew build` e `.github/scripts/server-smoke-test.sh` passam localmente (o teste de fumaça agora roda
  `/tupini selftest`, `/tupini skills list` e `/tupini finishers`).
- Teste visual com **dois clientes reais** + servidor dedicado (Xvfb, llvmpipe): evidências em
  `docs/evidencias/etapa3/`.

## O que existe agora

| Módulo | Pacotes | Resumo |
|---|---|---|
| UI Framework | `client.ui` | `Ui` (nine-slice, fundo repetido, brilho aditivo, linha grossa, barra, ícone 128px, sons), `TupiScreen` (painel próprio, sem blur, camada de VFX), `TupiButton` (4 estados de textura, hover animado, ícone, selo, amostra de cor), `CycleSelector` (`< valor >`, roda do mouse), `UiParticles` (faíscas, anéis, clarões, brasas), `EntityPreview` (jogador 3D girável, com rig e Body Layers). Texturas em `textures/gui/ui/`. |
| Character Creator | `client.character` | Abas Corpo/Cabelo/Rosto, prévia 3D **ao vivo** (prévia local em `CharacterAppearanceClientCache.setPreview`, nunca enviada), paleta clicável, aleatório, arrastar para girar. Salvar continua esperando a confirmação do servidor; personagem sem nascimento segue para a roleta. |
| Menu K | `client.character.CharacterHubScreen` | Prévia 3D, origem, atributos, necessidades e atalhos (Aparência, Roleta/Nascimento, Habilidades com selo de pontos). |
| Ficha do personagem | `profile` | `CharacterProfile` (attachment `tupinicraft:character_profile`, Codec, **copiado na morte**): nascimento, origem, 6 atributos, rerolls, pontos, habilidades. `ProfileService` é o único ponto de escrita e sincroniza com o dono (`ProfileSync`). |
| Roleta de nascimento | `profile.BirthRoulette`, `BirthOrigin`, `client.roulette` | 8 origens com peso e bônus; 12 pontos sorteados sobre a base 3 (teto 8 antes do bônus, faixa 1–10). **O servidor sorteia**; a tela gira enquanto espera, desacelera até a fatia sorteada e revela os atributos com som e VFX. 1 novo giro grátis (zera habilidades e devolve os pontos). |
| Atributos | `skill.SkillService.refreshModifiers` | Força → dano, Agilidade → velocidade, Vigor → vida, Sorte → sorte (modificadores **transitórios** `tupinicraft:stat/...`, recolocados no login/respawn/mudança). Inteligência → pontos iniciais; Carisma → humor; Vigor também reduz o gasto das necessidades. |
| Skill Tree | `skill`, `client.skilltree` | 3 abas (Combate, Sobrevivência, Malandragem) com fundo, cor e ícone próprios; 18 habilidades em código (`Skills`, só dados). Efeitos: atributo (+valor / +%), ritmo de necessidade, humor, finalização liberada. Nós com moldura própria (bloqueado/disponível/comprado), linhas com pulso animado, tooltip, arrastar para mover, VFX de compra (tela + partículas no mundo). Compra só no servidor (`SkillPurchaseRequest`); +1 ponto por nível de XP vanilla novo. |
| Necessidades | `needs` | `Needs` (attachment `tupinicraft:needs`, salvo, **não** copiado na morte): hidratação, energia, bexiga, intestino, humor (0–10000). `NeedsRules` (puro) por segundo; `NeedsService` aplica efeitos (lentidão, fadiga, fraqueza, sorte), "acidente" com partículas/som ao estourar bexiga/intestino, avisos, sono, comida/água vanilla. HUD: grade 2x2 acima da fome + painel do inventário com valores reais. |
| Consumíveis | `needs.ConsumableItem`, `ChamberPotItem`, `registry.ModItems` | Água de Coco, Cafezinho, Guaraná (bebidas), Pão de Queijo, Coxinha, Brigadeiro (comidas) e Penico (alivia bexiga/intestino). Tooltip mostra o efeito. Na aba do mod e em Comidas e Bebidas. |
| Finisher Engine | `finisher`, `testgame.FinisherTestMechanic`, `client.cinematic` | `FinisherDef` (animação do executor/vítima + `CameraTrack` + requisito de habilidade + peso). `takedown` (60 ticks) e `rasteira` (40 ticks, liberada pela habilidade). Palco frente a frente (sem montaria), clips `FULL_BODY` via `playTogether`, impactos decididos no servidor (`finisher_hit`: som, partículas, lampejo de dano), vítima caída em loop (`knocked_down`) durante a recuperação. |
| Câmera dinâmica | `client.cinematic.CinematicCamera` | Só para os participantes. Fonte da verdade = estado de animação sincronizado; terceira pessoa forçada durante a cena, trilha orbital com anti-parede, barras de cinema, tremor (`camera_shake`) e pulso de FOV (`camera_fov`). Restaura a perspectiva anterior no fim, cancelamento, dano, morte, desconexão, respawn/dimensão (`ClientPlayerNetworkEvent.Clone`) e logout. Posição aplicada no `ViewportEvent.ComputeFov` (vem logo após `Camera#setup`; o `ComputeCameraAngles` vem antes do posicionamento) com access transformer (`META-INF/accesstransformer.cfg`). |
| Áudio | `registry.ModSounds`, `sounds.json` | 11 eventos próprios (`finisher.*`, `skill.unlock`, `roulette.*`, `needs.*`, `ui.*`) apontando para eventos vanilla. Para áudio definitivo: `.ogg` em `assets/tupinicraft/sounds/` e editar só o `sounds.json`. |
| Sincronização temporal | `client.playergeo.PlayerRigAnimatable.SyncedController` | **Limitação 1 da Etapa 2 corrigida.** Quem começa a ver uma animação no meio pula a transição e o clip é adiantado até o instante do servidor (ajuste de `tickOffset` ao iniciar o clip; em loop, só a fase). |
| Autoteste | `selftest.SelfTest`, `/tupini selftest` | Regras puras (roleta: faixa/soma/frequência; necessidades; compra; catálogo; câmera; Codecs e dados corrompidos). Roda no servidor dedicado do CI. |

### Comandos

- `/tupini perfil` · `/tupini skills list` · `/tupini finishers`
- Admin (permissão 2): `/tupini selftest`, `/tupini admin perfil|pontos|roleta|resetar|skill|necessidade ...`,
  `/tupini admin finisher <executor> <alvo> [id]` (inicia a cena sem convite/vida baixa/cooldown — ferramenta de
  teste; as travas de distância, sessão e montaria continuam).

## Testes executados

| Verificação | Resultado | Evidência |
|---|---|---|
| `./gradlew build` | OK | — |
| Teste de fumaça do servidor dedicado (+ autoteste, catálogos) | OK | CI / local |
| Criador: abas, prévia 3D ao vivo, salvar → roleta | OK | 02 |
| Roleta: giro, parada na origem do servidor, revelação, novo giro | OK | 01 |
| Menu K com atributos e necessidades | OK | 03 |
| Árvore: abas com fundos, compra com VFX, tooltip, bloqueios | OK | 04 |
| HUD das necessidades (críticos piscando) | OK; a 1ª versão encostava no orbe e foi trocada pela grade 2x2 (não recapturada) | 09 |
| Finalização jogador × jogador, vista pelos dois (câmera, impactos, restauração) | OK | 05 |
| Cancelamento por dano no meio: cena para, câmera volta à perspectiva salva (F5 antes) | OK | 06 |
| Quem entra no meio (outra dimensão → perto): mesma pose/instante do executor | OK | 07 |
| Parceiro desconecta no meio: cancelada, câmera restaurada | OK | 08 |
| Sede zerada causa dano e cancela a cena (efeito colateral correto, visto no teste) | OK | — |

Não testado: GPU real; som (sem OpenAL no Xvfb); consumíveis e penico com o mouse (lógica no servidor, só
compilada/autoteste parcial); acidente de bexiga/intestino em jogo; morte durante a cena (mesmo caminho do dano);
mais de dois clientes; escala de GUI 2+ nas telas novas (o teste rodou em escala 1 na janela 854x480).

## Limitações e próximos passos

- Pele e rosto continuam só no retrato 2D (o rig ainda não tem rosto/pele próprios).
- Sons usam eventos vanilla; faltam `.ogg` próprios.
- `CinematicCamera` empurra a câmera com o FOV event: se outro mod também mexer na câmera nesse evento, a ordem
  importa. Alternativa futura: mixin no fim de `Camera#setup`.
- As finalizações em mobs só animam o executor (mobs não têm rig de jogador); a vítima mob recebe impactos e
  fica parada no palco.
- Penteados ainda em código; desalinhamento de tronco ao agachar em `OVERRIDE_ANIMATED` (limitação 2 da Etapa 2)
  não foi tocado.
- Clips das finalizações são gerados por `tools/gen_finisher_clips.py` (keyframes à mão). Para refinar no Blockbench,
  importe o `.animation.json` e mantenha os nomes `animation.tupinicraft.player.finisher.*`.
- Texturas da UI e dos itens são procedurais (`tools/gen_ui_textures.py`); troque os PNGs por arte final quando houver.

## Como estender

- **Habilidade:** `Skills.register(...)` com aba, pai(s), custo, posição (x à direita do pai) e efeitos; adicione
  `skill.tupinicraft.<aba>.<id>` (+ `.desc`) nos idiomas. O autoteste valida pai/aba/posição.
- **Origem:** novo valor em `BirthOrigin` (id estável, peso, cor, ícone, 6 bônus) + `origin.tupinicraft.<id>` (+ `.desc`).
- **Consumível:** `new ConsumableItem(props, new NeedsDelta(...), bebida)` em `ModItems` + modelo/textura/idioma.
- **Finalização:** gere/exporte os dois clips, registre um `FinisherDef` em `Finishers` com eventos `finisher_hit`,
  `camera_shake`, `camera_fov`, `sound` e uma `CameraTrack`; opcionalmente exija uma habilidade (`SkillEffect.finisher`).
