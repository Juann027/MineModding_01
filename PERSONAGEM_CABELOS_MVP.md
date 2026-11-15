# TupiniCraft 0.5.4 – Criador de personagem e penteados (MVP)

## Escopo implementado

- **K** abre o `CharacterCreationScreen` sem substituir o inventário vanilla.
- Escolha visual de **7 penteados**: careca, curto, franja, cacheado, longo, rabo de cavalo e moicano.
- **6 cores** de cabelo: preto, castanho, loiro, ruivo, branco e azul.
- Opções de modelo masculino/feminino, tom de pele e rosto salvas (por enquanto só na prévia 2D).
- `PlayerHairLayer` adiciona cabelos **voxel provisórios em 3D** sobre o bone da cabeça nos renderizadores Steve e Alex, usando uma textura branca que recebe cor dinamicamente. Não precisa de modelo Blockbench agora.
- Persistência por jogador em um **novo campo separado** `tupinicraft_character_appearance_v1` no `PersistentData` vanilla; não modifica os dados preexistentes. Copia aparência na morte.
- Rede servidor/cliente: cliente pede alteração, servidor valida IDs e transmite estado aos jogadores. Limpa cache ao desconectar.
- Menu editável novamente via K; editar aparência **não reexecuta roleta de atributos**.

## Como testar

1. Abra o projeto na IDE; use Java 21.
2. Execute `./gradlew runClient` ou `./gradlew build` (é necessário Gradle 9.2.1 / rede para baixar dependências).
3. Entre em um mundo, pressione **K**, clique em **Penteado** e em **Cabelo** para alternar.
4. Clique em **Salvar**. Entre em terceira pessoa (F5) para observar os acessórios na cabeça.
5. Repita com outro jogador em LAN/multiplayer para verificar a sincronização.
6. Morra/renasça e reconecte para confirmar a persistência.

## Limites conhecidos

- **Os penteados são blocos geométricos provisórios**, não modelos finalizados pelo Blockbench.
- A pele e o rosto ainda não substituem a skin Mojang no modelo 3D; só afetam o retrato ilustrativo e ficam registrados como preferências. Masculino/feminino também é apenas informação visual nesta etapa.
- **Careca** não remove cabelo pintado na skin original do Minecraft. É necessário gerar/selecionar uma textura corporal sem cabelo ao implementar as skins próprias.
- O menu **não implementa** confirmação de termos, roleta de nascimento ou anatomia 3D. Essas funcionalidades são etapas separadas.
- Cabelos podem sobrepor a camada do chapéu da skin original; ajustes finos e compatibilidade com todos os mods de renderização virão depois.
- A compilação completa e o render real **precisam ser verificados** na máquina com Gradle/Minecraft. Este ZIP fornece o fonte.

## Arquivos novos

- `character/CharacterAppearance.java`: catálogo, cor e codec compacto validado.
- `character/CharacterAppearanceService.java`: salvamento, clone e sincronização.
- `network/CharacterAppearance*`: pacotes e cache do cliente.
- `client/character/CharacterCreatorKeybinds.java`: tecla K.
- `client/character/CharacterCreationScreen.java`: menu com prévia e botões.
- `client/character/PlayerHairLayer.java`: acessórios voxel provisórios para os renderizadores vanilla.
- `textures/entity/character/hair_white.png`: textura branca para tingir.

## Observação técnica

Os penteados ficam isolados em `PlayerHairLayer`. Quando chegarem modelos `.bbmodel` reais, poderemos substituir a criação geométrica pelo renderizador e assets escolhidos sem alterar o formato dos dados salvos dos jogadores.
