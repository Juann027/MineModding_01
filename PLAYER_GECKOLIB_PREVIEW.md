# TupiniCraft 0.5.6 — teste de rig GeckoLib do jogador

## O que foi integrado

- Assets enviados: `tupinicraft_animated_player.bbmodel`, geometria GeckoLib (36 bones), animações `idle` e `wave`, skin de teste.
- A skin de cada jogador e obtida do jogo (`player.getSkin().texture()`), sem substituir a textura da conta.
- Uma pequena geometria vestido de busto feminino (`PlayerBustLayer`) aparece sobre a skin vanilla para personagens criados com `bodyType = feminino`. Ela nao muda a textura nem o sexo de outro jogador.
- O editor K, os sete penteados antigos, HUD, contadores e finalizadores foram preservados.
- Um renderizador experimental opt-in permite visualizar **o modelo Geo completo** com as duas animações exportadas do Blockbench em terceira pessoa.

## Como testar (client NeoForge 1.21.1, Java 21)

1. Compile com `./gradlew build` (ou `.\\gradlew.bat build` no Windows). Este arquivo é fonte; **não contém JAR compilado**.
2. Abra Minecraft com as dependencias do projeto. Crie/edite o personagem com K e salve.
3. Em um local seguro, pressione F5 para terceira pessoa.
4. Pressione **F8** para ativar o `Gecko player preview`. O modelo completo Geo substitui **somente a imagem do seu jogador local** nesse cliente. A animação `idle` roda em loop.
5. Pressione **F9** para executar `animation.tupinicraft.player.wave` (2 s). F9 também habilita o preview se estava desligado.
6. Pressione **F8** novamente para voltar imediatamente ao renderizador vanilla.
7. No menu K, alterne `masculino` e `feminino` e observe a geometria extra no tronco. Fora do preview, a camada `PlayerBustLayer` segue o movimento do torso vanilla.
8. Verifique também uma skin diferente e um personagem com braços Slim (Alex). A geometria do rig mascara bones Classic/Slim pelo tipo da skin.

## Limitações intencionais deste MVP

- **Não é um substituto de produção do PlayerRenderer.** O preview F8 não redesenha armor, capas, itens nas mãos, nome acima da cabeça nem alguns efeitos do renderer vanilla. Isso só vale enquanto F8 estiver ligado.
- `wave` não está sincronizado em multiplayer; F9 altera apenas a visualização do seu cliente. A troca de modelo é client-side e não modifica dados salvos.
- F8/F9 não têm relação com o menu K nem com a mecânica de finalizadores. Eles servem exclusivamente para testar o rig de Blockbench exportado pelo GeckoLib.
- A textura de teste colorida foi incluída para depuração; não é aplicada ao usuário. O modelo Geo usa a **skin individual** da conta Minecraft.
- `tupinicraft_player_overlays.geo.json` foi derivado do arquivo enviado e incluído como **recurso de referência**, mas ainda não é consumido pelo Java. No render vanilla, o busto usa um `ModelPart` simples com UV do torso, não o mesh GeckoLib completo.
- O busto da skin vanilla e do rig exportado pode ficar texturizado de maneira irregular. O cabelo GeckoLib provisório foi ocultado no preview, pois o UV dele usa um trecho não reservado da skin vanilla; o `PlayerHairLayer` antigo ainda funciona no render vanilla.
- Pivôs de braços, direção da rotação e posição de render GeckoLib precisam de **teste visual real dentro do Minecraft**. Não foi possível executar o jogo no ambiente que gerou este patch.
- Esta versão não traz animação em primeira pessoa, braços segurando itens, sincronização multiplayer de animação nem suporte a outras animações.

## Onde estão os arquivos

```text
model_sources/player/tupinicraft_animated_player.bbmodel
src/main/resources/assets/tupinicraft/geo/player/tupinicraft_animated_player.geo.json
src/main/resources/assets/tupinicraft/geo/player/tupinicraft_player_overlays.geo.json
src/main/resources/assets/tupinicraft/animations/player/tupinicraft_animated_player.animation.json
src/main/resources/assets/tupinicraft/textures/entity/player_test/tupinicraft_test_skin_classic.png
src/main/java/com/tupinicraft/client/playergeo/*
src/main/java/com/tupinicraft/client/character/PlayerBustLayer.java
```

## Para reverter o teste

Aperte F8 para voltar ao renderer vanilla. Se o preview causar problemas de renderização, remova os listeners `PlayerGeoTestKeys` do `TupiniCraftClient.java`; todos os demais recursos seguem independentes. Para remover a geometria feminina de teste, remova somente o registro `new PlayerBustLayer(renderer)`.

## Validação

Os arquivos JSON e PNG fornecidos foram lidos e a correspondência dos bones das animações com a geometria foi verificada. **Compilação e execução no Minecraft não foram realizadas**: o Gradle wrapper 9.2.1 depende de `services.gradle.org`, indisponível neste ambiente. Rode o build no CI antes de instalar o JAR no cliente e, se houver erros, envie o log.
