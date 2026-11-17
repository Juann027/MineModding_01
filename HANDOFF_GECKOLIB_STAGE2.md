# HANDOFF — GeckoLib Player, Etapa 2 (renderizador definitivo, Body Layers e multiplayer)

## Versão e commits

- TupiniCraft `0.5.6` (`mod_version` **não** foi alterado: o merge não publica Release).
- Commit inicial: `9962ef5` (`main`, merge do PR #17, fim da Etapa 1).
- Branch de trabalho: `claude/adoring-darwin-lcdua3`. Commits: `dce901c` (implementação) e o commit de
  fechamento (opção de loop, teste de fumaça, documentação). PR e commit de merge: ver a descrição do PR.

## O que mudou em uma frase

O rig GeckoLib deixou de ser um desenho à parte que substituía o jogador (e por isso perdia armadura, itens,
cabelo, caminhada, agachamento e olhar) e passou a ser **uma camada dentro do `PlayerRenderer` vanilla**, com a
pose trocada nos dois sentidos entre o modelo vanilla e o rig.

## Arquitetura do renderizador (`client/playergeo`)

Modo híbrido, decidido por jogador e por quadro em `PlayerRigController.shouldUseRig`:

- **Jogo normal:** jogador vanilla + camadas. Nada do rig é executado.
- **Rig ativo** (o servidor mandou uma animação, ou o modo de teste F8 está ligado): em um quadro,
  1. `RenderPlayerEvent.Pre` marca `skipDraw` nas 12 partes do `PlayerModel` (o corpo vanilla não é desenhado; o
     evento **não** é cancelado, então nome, sombra, rotações de corpo e demais regras vanilla continuam valendo);
  2. o vanilla calcula a pose normalmente (`setupAnim`: andar, correr, agachar, olhar, atacar, segurar item);
  3. `PlayerRigLayer`, que é a **primeira** camada do renderer, lê essa pose, desenha o rig com a skin do jogador e
     escreve a pose final de volta no `PlayerModel`;
  4. as camadas seguintes (armadura, itens nas mãos, capa, elytra, cabeça customizada, flechas, Body Layers do
     TupiniCraft e camadas de outros mods) são as do próprio vanilla e acompanham o rig sem código específico;
  5. `RenderPlayerEvent.Post` desfaz o `skipDraw` e devolve as poses guardadas.

| Classe | Papel |
|---|---|
| `PlayerRigController` | decide vanilla × rig, liga/desliga `skipDraw`, teclas F8/F9, um `PlayerRigAnimatable` por UUID |
| `PlayerRigLayer` | camada de índice 0 do `PlayerRenderer`; lê pose, desenha o rig, devolve a pose |
| `PlayerRigAnimatable` | estado GeckoLib de **um** jogador (controller próprio), pose vanilla do quadro e pesos de mistura |
| `PlayerRigModel` | skin da conta, braços Classic/Slim, camadas externas conforme as opções de skin, combinação de pose |
| `PlayerRigRenderer` | `GeoObjectRenderer` com overlay de dano |
| `PlayerPoseBridge` | matemática da conversão de pose |
| `PlayerPart` | as seis partes comuns (Body, Head, RightArm, LeftArm, RightLeg, LeftLeg) e suas posições de repouso |

A camada entra no índice 0 por `ObfuscationReflectionHelper` (o campo `LivingEntityRenderer.layers` é protegido).
Se a reflexão falhar, a camada vai para o fim e um erro é registrado (o rig aparece, mas armadura e itens não
acompanham). Trocar por access transformer é uma opção para a Etapa 3.

### Ponte de poses (`PlayerPoseBridge`)

Toda pose é representada como a matriz "repouso → atual" no espaço do GeckoLib (blocos, Y para cima).

- `readVanilla`: `M = V2G · T(pos) · Rzyx(rot) · T(-repouso) · G2V`, onde `G2V = T(0, 1.501, 0) · S(-1, -1, 1)` é a
  mudança de espaço que o `LivingEntityRenderer` aplica.
- `addToBone`: decompõe a matriz em rotação ZYX em torno do pivô e deslocamento, nas convenções do `GeoBone` (X da
  posição invertido), e **soma** ao bone.
- `boneWorld` + `writeVanilla`: percorre a cadeia de pais do bone e faz o caminho inverso para o `ModelPart`.

Combinação em `PlayerRigModel.compose`, a cada quadro:

1. os bones voltam aos valores puros do clip (capturados em `setCustomAnimations`);
2. cabeça e braços recebem a pose vanilla **relativa ao tronco vanilla** (no rig eles são filhos do tronco); tronco e
   pernas recebem a pose vanilla direta;
3. a rotação vanilla entra com peso `1 - overrideWeight` da parte; a **posição vanilla sempre entra** (agachar);
4. `GeoBone::resetStateChanges` em todos os bones. Isto é obrigatório: os setters do `GeoBone` marcam o bone como
   alterado e, sem limpar a marca, o GeckoLib deixa de recalcular os bones sem animação e a pose vanilla acumula a
   cada quadro (foi o primeiro defeito encontrado: cabeça afundando no tronco e pernas abrindo).

`overrideWeight` vai de 0 a 1 em `transitionTicks` e volta do mesmo jeito; depois do fim do clip o rig continua
desenhado por `RECOVERY_TICKS` (4) enquanto os bones voltam ao repouso.

## Body Layers (`client/bodylayer`)

Peças integradas ao corpo, sem slot de armadura e sem entidade. Uma única camada (`BodyLayersRenderLayer`) desenha
todas, usando a pose do `PlayerModel`, que é a vanilla no jogo normal e a do rig durante animações. Por isso cada peça
é desenhada **uma só vez** nos dois modos e nunca fica presa na pose anterior. No rig, os bones `FemaleBust` e
`HairPlaceholder` ficam sempre ocultos.

Uma peça é um `BodyLayerDefinition(id, geometry, visible, texture, tint)` registrado em `BodyLayers`:

- `GeoGeometry(modelo, {bone → PlayerPart})`: modelo Blockbench exportado como GeckoLib, modelado **sobre o rig**, nas
  mesmas coordenadas. Usado pelo busto (`geo/player/tupinicraft_player_overlays.geo.json`, bone `FemaleBust` → `BODY`).
- `PartGeometry(PlayerPart, ModelPart)`: malha montada em código. Usado pelos seis penteados (`HairMeshes`).

Visibilidade e cor vêm da aparência sincronizada pelo servidor (`CharacterAppearanceClientCache`). Recursos inválidos
(modelo ou bone inexistente) desativam só aquela peça, com erro no log.

**Como adicionar uma geometria Blockbench:** modele sobre `model_sources/player/tupinicraft_animated_player.bbmodel`,
exporte como "GeckoLib Animated Model" em `assets/tupinicraft/geo/player/<nome>.geo.json` (textura 64×64 se usar a
skin; qualquer tamanho com textura própria) e registre:

```java
BodyLayers.register(new BodyLayerDefinition(TupiniCraft.id("chapeu"),
        new GeoGeometry(TupiniCraft.id("geo/player/chapeu.geo.json"), Map.of("Chapeu", PlayerPart.HEAD)),
        ctx -> /* quando aparece */ true,
        ctx -> TupiniCraft.id("textures/entity/character/chapeu.png"),
        ctx -> -1));
```

O bone listado precisa existir no arquivo; a parte indicada é a que ele acompanha.

## Animation Manager

Comum (`animation`), sem classes de cliente:

- `PlayerAnimationDef` (só dados): id, arquivo, clip, duração, loop, prioridade, transição, `Blend`
  (`ADDITIVE`, `OVERRIDE_ANIMATED`, `FULL_BODY`), `overrideBones`, `playerTriggerable`, `stopOnHurt`, eventos
  temporizados. `PlayerAnimations` é o catálogo; `IDLE` e `WAVE` são os exemplos.
- `PlayerAnimationService` (servidor, único ponto de escrita): `play`, `playTogether` (duas entidades, mesmo instante,
  parceiro cruzado), `stop`, `current`, `handleRequest`. Regras: vivo e não espectador; uma animação em andamento só é
  trocada por outra de prioridade igual ou maior (ou `force`); morte, dano (`stopOnHurt`) e saída interrompem. Estado
  em attachment transitório (`ModAttachments.PLAYER_ANIMATION`), sem persistência.
- `PlayerAnimationEvent.Start / Timed / End`, publicados em `NeoForge.EVENT_BUS` **nos dois lados**: são os pontos de
  integração para som, VFX, partículas, câmera, tremor, FOV e início/fim de sequência. Os tipos reservados estão em
  `PlayerAnimations.EVENT_*`. Keyframes de som/partícula/instrução gravados no clip chegam como `Timed` com os tipos
  `EVENT_CLIP_*`. Só o som (`EVENT_SOUND`, `data` = id do som) tem tratador (`ClientPlayerAnimations.onTimedEvent`);
  nenhuma animação atual usa eventos, então esse caminho **não foi exercitado em jogo**.
- `/tupinianim list | play <jogadores> <id> [loop] | together <a> <b> <id> | stop <jogadores>` (permissão 2).

Cliente (`client/animation/ClientPlayerAnimations`): valida cada animação no primeiro uso (rig, arquivo, clip, bones;
aviso se a duração registrada difere do clip), revalida após recarga de recursos, acompanha o estado e publica os
eventos. Animação inválida é ignorada com erro no log.

**Como adicionar uma animação:** exporte o clip para um `.animation.json` (o do jogador ou outro) e registre na
inicialização do mod, sem classe nova:

```java
PlayerAnimations.register(PlayerAnimationDef
        .builder(TupiniCraft.id("reverencia"), PlayerAnimations.PLAYER_FILE, "animation.tupinicraft.player.reverencia", 30)
        .priority(10).blend(PlayerAnimationDef.Blend.FULL_BODY).playerTriggerable().build());
```

Nomes de clip devem ser únicos entre os arquivos. A duração é informada no registro porque o servidor não lê assets.

## Sincronização multiplayer

- `PlayerAnimationSyncPayload(UUID, Optional<PlayerAnimationState>)`, servidor → quem está vendo o jogador e ele
  mesmo. O estado leva id, tempo de jogo do início, duração, loop, sequência, parceiro e parâmetros (NBT livre). Vazio
  = parou.
- `PlayerAnimationRequestPayload(id, stop)`, cliente → servidor: só a intenção (F9). O servidor revalida, com intervalo
  mínimo de 5 ticks entre pedidos.
- Reenvio em `StartTracking` (inclusive a limpeza de estado antigo), troca de dimensão e respawn; na saída, todos
  esquecem o jogador. O fim natural não gera pacote: o cliente encerra pelo relógio do mundo.
- Um `PlayerRigAnimatable` por UUID; nenhum controller é compartilhado.

## Testes executados

Cliente real em Xvfb, OpenGL por software (llvmpipe), contra servidor dedicado local. **Dois clientes ao mesmo
tempo**, cada um em sua tela virtual. Evidências em `docs/evidencias/geckolib_stage2/`.

| Verificação | Resultado | Evidência |
|---|---|---|
| Rig × vanilla em repouso | coincide; diferença restrita ao balanço dos braços | 01 |
| Armadura (4 peças), espada, escudo, agachado, olhar | iguais ao vanilla | 02 |
| Caminhada no rig | OK | 03 |
| Cabelo e busto, vanilla × rig, vistos pelos dois clientes | iguais | 04 |
| A acena (F9), B vê; B acena, A vê; ambos ao mesmo tempo | OK | 05, 06, 07 |
| Sequência sincronizada pelo servidor; acenar andando | OK | 08 |
| Modo híbrido (F8 desligado) com cabelo e busto | OK | 09 |
| Morte no meio do aceno, respawn e novo aceno | OK | 10 |
| Nether (os dois acenam) e volta ao Overworld | OK | 11, 12 |
| Atualização de aparência pelo menu K, vista pelo outro cliente | OK | 04, 14 |
| Desconexão (o outro continua), reconexão no meio de um loop, parada | OK | 15 |
| Criador e menu K nítidos; 1280×720 com GUI 3 (rig, inventário criativo, criador) | OK | 13, 14, 16 |
| Skins Slim (`Dev`) e Wide (`Juca`, `Steve`) | OK | 05, 16 |
| `./gradlew build` e teste de fumaça do servidor dedicado (agora também `tupinianim list`) | OK | CI |

Não testado: GPU real e desempenho; skins de conta e capas (rede bloqueada); corrida, ataque, nado, elytra e sono no
rig (devem funcionar, pois vêm da pose vanilla, mas não foram capturados); inventário de sobrevivência e painel
Body & Traits com o rig (só o inventário criativo foi aberto); flash de dano; eventos temporizados; primeira pessoa;
mais de dois clientes; recarga de recursos (F3+T) com o jogo aberto.

## Problemas e limitações conhecidos

- Quem começa a ver uma animação **finita** no meio a reproduz do início e é cortado no fim sincronizado (não há
  avanço do clip até o ponto certo). Loops não são afetados.
- Em `OVERRIDE_ANIMATED`, só a rotação é substituída. Agachar durante um clip que substitui o tronco pode desalinhar
  um pouco tronco e pernas.
- As rotações são somadas em ângulos de Euler; poses vanilla extremas combinadas com clips podem divergir levemente.
- Escala de bones não é levada ao modelo vanilla (armadura não escala com o bone).
- Jogador invisível ou espectador usa sempre o vanilla; em primeira pessoa o braço é o vanilla.
- O busto usa a região do tronco da skin, com textura irregular em algumas skins (herdado da Etapa 1).
- Os penteados ainda são malhas em código; não há modelos Blockbench de cabelo.
- O `CLAUDE.md` continua descrevendo sistemas arquivados (eleições, skill trees, `rulesTest`); só ganhou a seção do rig.

## Orientações para a Etapa 3

1. Finisher Engine: assinar `PlayerAnimationEvent` no cliente para câmera, tremor e FOV; usar `playTogether`, o campo
   `partner` e `params` para cenas de duas entidades; registrar clips `FULL_BODY`.
2. Avanço de clip para quem entra no meio (subclasse de `AnimationController` ajustando `tickOffset`).
3. Penteados e acessórios em Blockbench como `GeoGeometry`; Body Layers animadas (hoje a geometria é estática).
4. Animações em primeira pessoa e escala de bones na ponte.
5. Access transformer no lugar da reflexão; levar o teste visual para o CI (`tools/visual-test/helpers.sh` já aceita
   várias telas: `start_display :98`, `on :98 launch Juca`).
6. Atualizar o restante do `CLAUDE.md`.

## Arquivos

- Novos: `animation/*` (8), `client/animation/ClientPlayerAnimations`, `client/playergeo/*` (7),
  `client/bodylayer/*` (5), `network/PlayerAnimation{Sync,Request}Payload`, `network/PlayerAnimationClientCache`,
  `registry/ModAttachments`, `docs/evidencias/geckolib_stage2/*`, este arquivo.
- Removidos: `client/playergeo/PreviewPlayer*`, `PlayerGeoTestKeys`, `client/character/PlayerHairLayer`,
  `PlayerBustLayer` (migrados para Body Layers).
- Alterados: `TupiniCraft`, `TupiniCraftClient`, `TestGameNetworking`, `lang/*.json`, `tools/visual-test/*`,
  `.github/scripts/server-smoke-test.sh`, `PLAYER_GECKOLIB_PREVIEW.md`, `CLAUDE.md`.

Rig, animações e texturas não foram alterados. As correções da Etapa 1 foram preservadas (escala 15/16 agora vem do
próprio `PlayerRenderer`; overlay de dano; telas sem blur; posição do busto; painel Body & Traits).
