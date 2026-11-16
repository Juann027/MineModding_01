# TupiniCraft 0.5.5 — Correção do Criador de Personagem / tecla K

Este patch parte do arquivo `MineModding_01-main (6).zip` enviado pelo usuário em 15/11/2026, contendo `mod_version=0.5.5`.

## Correções

- Removida a chamada `Screen#renderBackground` da tela de criação, que aciona o desfoque do mundo na versão 1.21.1. Agora o mundo permanece nítido sob um escurecimento leve.
- A tela de criação teve o espaçamento vertical reduzido para evitar que os botões inferiores ficassem parcialmente fora do painel em escalas de GUI menores.
- `Salvar` desabilita temporariamente seu botão, mostra a mensagem de progresso e espera o servidor sincronizar os dados confirmados antes de abrir o menu K. Se a confirmação não chegar após ~10 segundos, libera uma nova tentativa e mostra uma mensagem de falha.
- Uma nova tela `CharacterHubScreen` passa a ser o menu principal da tecla K depois da criação. O botão `Editar aparência` reabre o criador; `Voltar ao jogo` o fecha. Não foram inventadas telas de Skill Tree ou valores de atributos.
- `Voltar` no criador retorna ao hub se um personagem já foi criado; caso contrário, volta ao jogo como no código original.
- O cache do cliente passou a registrar um contador de sincronizações por jogador, para diferenciar atualização enviada de confirmação recebida. O salvamento em si continua sendo executado e persistido pelo servidor por `CharacterAppearanceService`.
- Traduções pt-BR e en-US atualizadas.

## O que não foi alterado

Nenhum arquivo de gameplay (mobs, habilidades, finalizações, HUD, modelos GeckoLib, status real, atributos) foi alterado, e não foram importados arquivos Blockbench.

## Como testar no jogo

1. Compilar o mod (Java 21): `./gradlew build` no Linux/macOS ou `gradlew.bat build` no Windows.
2. Abrir um mundo e apertar `K` sem personagem criado: deve mostrar o Criador, com o mundo ao fundo nítido e levemente escurecido.
3. Modificar o penteado e clicar **Salvar**. Deve aparecer **Salvando no servidor...** e, após confirmação, o menu K principal com **Personagem salvo**.
4. Fechar e apertar `K` novamente: agora deve abrir o menu principal, não o criador.
5. No menu, clicar **Editar aparência**, mudar a cor do cabelo e salvar novamente.
6. Sair do mundo e entrar de novo para confirmar persistência.
7. Repetir o teste em servidor dedicado com dois jogadores, verificando se o penteado de um aparece para o outro.
8. Ajustar a escala da GUI e verificar se os botões não estão cortados.

### Limitações dos testes

O teste isolado Java dos 756 estados de aparência e as validações de traduções/estrutura passaram. Não foi possível executar `./gradlew compileJava`: a rede do ambiente bloqueou a resolução de `services.gradle.org` (Gradle 9.2.1). Não há JAR compilado nem teste visual no Minecraft nesta entrega. Recomenda-se passar o patch pelo CI antes de mesclar à `main`.

### Arquivos afetados

- `src/main/java/com/tupinicraft/client/character/CharacterCreationScreen.java`
- `src/main/java/com/tupinicraft/client/character/CharacterCreatorKeybinds.java`
- `src/main/java/com/tupinicraft/client/character/CharacterHubScreen.java` (novo)
- `src/main/java/com/tupinicraft/network/CharacterAppearanceClientCache.java`
- `src/main/resources/assets/tupinicraft/lang/pt_br.json`
- `src/main/resources/assets/tupinicraft/lang/en_us.json`
