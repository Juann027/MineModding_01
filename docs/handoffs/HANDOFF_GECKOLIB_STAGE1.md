# HANDOFF — GeckoLib Player Test, Etapa 1 (auditoria, compilação e testes reais)

## Versão e commit-base

- TupiniCraft `0.5.6` (`mod_version` não foi alterado: nenhuma Release nova é publicada por este trabalho).
- Commit-base: `710605c` (`main`, merge do PR #16 "Integra GeckoPlayer teste").
- O ZIP citado no pedido não chegou ao ambiente da sessão. A auditoria foi feita sobre a `main`, que já contém a
  0.5.6 — GeckoLib Player Test (mesmos arquivos descritos em `PLAYER_GECKOLIB_PREVIEW.md`). Se o ZIP tiver algo
  que não está na `main`, isso **não** foi comparado.

## Auditoria (conferido nos arquivos)

| Item | Valor real |
|---|---|
| Minecraft / NeoForge | 1.21.1 / 21.1.265 (ModDevGradle 2.0.148, Parchment 2024.11.17) |
| Java / Gradle | Java 21 (toolchain) / wrapper 9.2.1 |
| Mod ID | `tupinicraft` |
| Dependências ativas | GeckoLib 4.9.3 e SmartBrainLib 1.16.11 (obrigatórias nos dois lados). UI Lib e Architectury estão só em `gradle.properties`, sem uso no `build.gradle` nem no `neoforge.mods.toml` |
| Entrada do cliente | `TupiniCraftClient` (`@Mod(dist = CLIENT)`): teclas no mod bus, `ClientTickEvent.Post` e `RenderPlayerEvent.Pre` no bus do jogo, limpeza no `LoggingOut` |
| Teclas | K (criador/menu), F8 (liga/desliga preview), F9 (wave). F5 é a câmera vanilla |
| Rig | `geo/player/tupinicraft_animated_player.geo.json` (36 bones, 64×64), `animations/player/...animation.json` (`idle` em loop de 2 s, `wave` de 2 s). Todos os bones das animações existem na geometria |
| Textura | skin da conta (`player.getSkin().texture()`); a skin de teste é só reserva |
| Renderização | `GeoObjectRenderer` chamado dentro de `RenderPlayerEvent.Pre`, que é cancelado; só o jogador local, só em terceira pessoa |
| CI | `build.yml` (`./gradlew build` + teste de fumaça do servidor dedicado) e `release.yml` (publica só quando `mod_version` é nova) |

Observação: o `CLAUDE.md` ainda descreve `rulesTest`, skill trees e UI Lib, que estão arquivados; o build atual não
tem testes automatizados de regra (`tests/manual/FinisherRulesTest.java` não é executado pelo Gradle).

## Como foi testado

Cliente **real** do Minecraft (`./gradlew runClient`) em tela virtual: Xvfb 854×480 + openbox, OpenGL 4.5 por
software (Mesa llvmpipe), teclado e mouse por `xdotool`, capturas com ImageMagick e vídeo com `ffmpeg`. O cliente
entra sozinho (`--quickPlayMultiplayer`) num **servidor dedicado** local (`./gradlew runServer`, offline, mundo
plano), então todo teste também cobre multiplayer/servidor dedicado. Os comandos de servidor (`tp`, `kill`,
`gamemode`) foram enviados pelo console.

Para repetir: `. tools/visual-test/helpers.sh; start_display; start_server; launch Dev; key F5; key F5; key F8; shot teste`.
O nome do jogador escolhe a skin padrão offline: `Dev` = Efe Slim, `Steve` = Efe Wide, `Juca` = Makena Wide.

As capturas estão em `docs/evidencias/geckolib_stage1/` (todas tiradas do jogo rodando; nenhuma é montagem além de
recortes, ampliação e colocação lado a lado).

## Problemas encontrados e corrigidos

| # | Problema (visto no jogo) | Causa | Correção | Evidência |
|---|---|---|---|---|
| 1 | Rig GeckoLib 6,7% maior que o jogador vanilla (cabeça acima, ombros mais largos) | o `PlayerRenderer` desenha em 15/16; o rig era desenhado em 1:1 (2 blocos de altura) | `withScale(0.9375F)` em `PreviewPlayerGeoRenderer` | `01` (antes) e `02` (depois) |
| 2 | Depois de respawn ou troca de dimensão o rig podia ficar parado, sem `idle` | o prazo do wave era comparado com o `tickCount`, que recomeça do zero no novo `LocalPlayer`; o controller ficava no último quadro do `wave` | `PreviewPlayerAnimatable.setPlayer` zera o prazo quando o jogador muda | conferido por diferença de pixels entre dois quadros após respawn e após voltar do Nether |
| 3 | Rig sem o flash vermelho de dano | `GeoObjectRenderer` usa `NO_OVERLAY` | `getPackedOverlay` usa `hurtTime`/`deathTime` | só compilação; não capturei um quadro de dano |
| 4 | Menu K e criador totalmente borrados (título, retrato e textos ilegíveis) | na 1.21.1 `Screen#render` chama `renderBackground`, que aplica o blur por cima do painel já desenhado | `renderBackground` vazio em `CharacterCreationScreen` e `CharacterHubScreen` (era a intenção do comentário original) | `06` (antes) e `07` (depois) |
| 5 | Busto feminino do render vanilla aparecia **em cima da cabeça** | Y do `ModelPart` do tronco cresce para baixo (0..12); a caixa estava em Y negativo | caixa movida para Y 2,5..6 (mesma faixa do bone `FemaleBust`) | `08` (antes) e `09` (depois) |
| 6 | Painel Body & Traits nunca aparecia no inventário | o evento `Foreground` chega com a pose deslocada para o canto do inventário; o painel era desenhado 7 px além da borda da tela | desfaz o deslocamento antes de desenhar | `10` |

Os itens 4 a 6 estão fora do rig, mas eram defeitos que impediam testar as variantes (corpo, cabelo) e sistemas
que constavam como prontos. Nenhum muda layout, cores, textos ou regras.

## Resultados dos testes

**F8**

| Verificação | Resultado |
|---|---|
| Modelo GeckoLib é ativado | OK |
| Skin individual preservada, sem inversão | OK (3 skins; pulseira e detalhes do mesmo lado que no vanilla) |
| Escala e altura | OK após a correção 1; pés no chão, silhueta coincide com o vanilla |
| Sem membros duplicados; cabeça, braços e pernas alinhados | OK (Slim e Wide mostram só o conjunto certo de braços) |
| `idle` é reproduzido | OK (movimento pequeno: 1° no tronco, 2,5° nos braços) |
| F8 desligado restaura o vanilla | OK, inclusive cabelo e busto |

**F9**

| Verificação | Resultado |
|---|---|
| Wave acionado; liga o preview se estava desligado | OK |
| Braço direito se movimenta (é o do lado esquerdo da tela, visto de frente) | OK |
| Duração | cerca de 2 s (medido em vídeo a 5 quadros/s: 9 a 10 quadros) |
| Volta ao repouso | OK |
| Repetir o aceno (4 vezes seguidas, inclusive no meio da animação) | OK, reinicia sem deformar |
| Não interfere em outras poses | OK: depois do wave o `idle` volta e o vanilla fica igual ao de antes |

**Variantes**

| Verificação | Resultado |
|---|---|
| Slim (`Dev`) e Wide (`Steve`, `Juca`) | OK |
| Masculino / feminino | OK; no rig o bone `FemaleBust` aparece no tronco com textura irregular (limitação já documentada) |
| Penteados e cores | OK no render vanilla (testado "Long" + "Red"). **No preview F8 o cabelo não aparece** (limitação conhecida) |
| Troca de dimensão (Overworld → Nether → Overworld) com preview ligado | OK, preview e wave continuam |
| Morte e respawn com preview ligado (morte no meio de um wave) | OK; durante a morte aparece o vanilla |
| Reconexão | OK: aparência volta do servidor; o preview recomeça desligado |
| Reinício do servidor | OK: aparência persistida |
| Inventário com preview ligado | OK, o boneco do inventário usa o rig |

**Build e servidor**

- `./gradlew build`: OK. JAR `build/libs/tupinicraft-0.5.6.jar` contém as 4 classes `client/playergeo`, os dois
  `.geo.json` do jogador, o `.animation.json` e a skin de teste; o `.bbmodel` fica fora, como esperado.
- `.github/scripts/server-smoke-test.sh`: OK (servidor dedicado sobe sem classes de cliente e invoca os dois mobs).
- Logs do cliente: nenhum erro do TupiniCraft ou do GeckoLib. Os únicos erros são de rede/áudio do ambiente
  (sessão da Mojang inacessível, sem dispositivo OpenAL).

## Limitações e o que não foi testado

- Renderização por software, 854×480, cerca de 30 quadros/s: fluidez e desempenho em GPU real não foram medidos.
- Só skins padrão offline. Skins de conta (baixadas da Mojang) e capas não foram testadas: `sessionserver` e
  `textures.minecraft.net` ficam fora da rede do ambiente.
- Um cliente por vez: não houve teste com dois clientes simultâneos (como outro jogador vê quem está com o preview).
  Pelo código, o preview só troca o desenho do jogador local e não envia nada ao servidor.
- Testados 1 de 6 penteados não vazios e 1 de 6 cores; primeira pessoa e modo espectador não foram capturados.
- O flash de dano (correção 3) não tem captura.
- Continuam valendo as limitações do MVP: com F8 ligado não há armadura, capa, item na mão, cabelo voxel,
  agachar, nadar, elytra, movimento de pernas ao andar nem cabeça seguindo o olhar; o wave não é sincronizado
  entre jogadores.

## Arquivos modificados

- `client/playergeo/PreviewPlayerGeoRenderer.java` — escala 15/16 e overlay de dano.
- `client/playergeo/PreviewPlayerAnimatable.java` — zera o prazo do wave quando o jogador muda.
- `client/character/CharacterCreationScreen.java`, `CharacterHubScreen.java` — sem blur.
- `client/character/PlayerBustLayer.java` — posição do busto.
- `client/hud/BodyStatusInventoryPanel.java` — posição do painel.
- `PLAYER_GECKOLIB_PREVIEW.md` — seção de validação atualizada.
- Novos: `HANDOFF_GECKOLIB_STAGE1.md`, `tools/visual-test/*`, `docs/evidencias/geckolib_stage1/*`.

Rig, animações, texturas, mobs, finalizadores, rede e dados salvos não foram tocados.

## Próximos passos técnicos

1. Trocar `GeoObjectRenderer` + `RenderPlayerEvent.Pre` por um renderer próprio do jogador (por exemplo
   `GeoReplacedEntityRenderer`), para ganhar rotações de corpo vanilla (dormir, morrer, nadar), nome e camadas.
2. Cabeça seguindo o olhar e agachamento em `setCustomAnimations`; animação de caminhada no rig.
3. Cabelo e busto como `GeoRenderLayer` com textura própria (o `HairPlaceholder` segue oculto); usar ou remover
   `tupinicraft_player_overlays.geo.json`, hoje sem uso.
4. Itens nas mãos pelos bones `RightHandAttachment`/`LeftHandAttachment` (`BlockAndItemGeoLayer`) e armadura.
5. Sincronizar animações entre jogadores (payload servidor→clientes) e aplicar o rig a outros jogadores.
6. Levar o teste visual para o CI (Xvfb + llvmpipe já funcionam) e atualizar o `CLAUDE.md`, que descreve sistemas arquivados.
