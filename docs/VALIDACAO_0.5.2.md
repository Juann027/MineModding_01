# Correções de inventário e texturas — 0.5.2

## Alterações

- Nova aba criativa `tupinicraft:tupinicraft`, traduzida como **TupiniCraft**, com os 35 itens do mod: Urna Eletrônica, ovo do Cabo Eleitoral e 33 ícones.
- A aba é preenchida a partir do registro de itens, incluindo também os itens na busca do criativo.
- As 33 texturas dos ícones foram movidas de `textures/skilltree` para `textures/item/skilltree`. Os modelos agora usam `tupinicraft:item/skilltree/<nome>`, coletado pelo atlas padrão do Minecraft 1.21.1.
- Os IDs dos itens e os dados salvos continuam preservados. O protocolo de rede continua 2.
- Nomes dos ícones em português corrigidos.

## Validações executadas

- `./gradlew clean build`: aprovado, incluindo 136 verificações de regras e 27 verificações de regressão.
- Novo `IconTextureSelfTest`: os 34 PNGs próprios de itens (33 ícones e a urna) foram abertos por `NativeImage` do Minecraft e processados por `ItemModelGenerator`, produzindo geometria de sprite visível. O ovo usa o modelo padrão de spawn egg do Minecraft.
- O novo validador de recursos rejeitou o JAR anterior 0.5.1 por usar texturas fora do atlas padrão e aprovou os recursos do JAR 0.5.2.
- Verificação temporária em servidor NeoForge real: a aba e sua coleção de busca foram construídas após o registro dos itens. Ambas contêm exatamente os 35 itens; cada item registrado foi conferido individualmente.
- Teste de fumaça do servidor dedicado: aprovado, com dois reinícios, persistência da eleição/configurações, apuração, posse, consulta de mandatos e mob de teste.
- JAR final conferido sem classes de instrumentação temporária. Modelos, traduções e PNGs estão presentes no pacote.

A abertura da janela gráfica falhou na tela virtual deste ambiente; não foi possível inspecionar uma captura do inventário dentro do cliente. A leitura das imagens e a geração dos sprites foram verificadas com as classes do próprio Minecraft, sem janela/OpenGL.

## Instalação

1. Feche o jogo e o servidor.
2. Remova a versão antiga do **TupiniCraft** da pasta `mods`.
3. Coloque `dist/tupinicraft-0.5.2.jar` na pasta `mods`. Não coloque o ZIP nem os arquivos-fonte nessa pasta.
4. Use Minecraft **1.21.1** com **NeoForge 21.1.x** e as dependências indicadas no README.
5. No multiplayer, use a versão 0.5.2 no cliente e no servidor.

A aba se chama **TupiniCraft**, com ícone da urna. Se outros mods adicionarem abas e houver mais de uma página, use as setas do inventário criativo.
