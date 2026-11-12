# TupiniCraft 0.5.4-ui-dev — UI cívica

Repaginação da urna e dos anúncios globais, mantendo os modelos, sistemas de jogo e regras eleitorais do projeto fornecido.

## Implementado

- Identidade própria: verde profundo, jade e dourado, selo geométrico e painéis desenhados pelo mod. Não exige pacote de texturas adicional.
- Botões próprios com iluminação de hover, foco de teclado e estados desabilitados. Base Button preserva ativação e narração nativas.
- Urna com navegação entre disputas e paginação dos candidatos. Textos extensos são abreviados dentro dos controles.
- Seleção local seguida de confirmação explícita; não fecha a tela ao enviar o voto. O servidor continua responsável pela validação, e retorna o resultado à tela.
- Bloqueio durante envio, timeout de 10 s, estado de voto registrado e restrições de candidatura preservadas.
- Layout reduzido proporcionalmente abaixo de 320 × 240 unidades de GUI, com coordenadas de clique correspondentes.
- HUD eleitoral resumido: inscrições, progresso pessoal e apuração, sem uma lista ilimitada de candidatos sobre o mundo.
- Cartões globais com revelação horizontal, selo, varredura suave, partículas de interface e barra de duração. Efeitos limitados ao cartão, sem partículas no mundo nem flashes de tela inteira.
- Fila limitada a dez eventos pendentes; o mais antigo pendente é descartado em sobrecarga. Eventos longos têm páginas automáticas de cinco linhas.
- Avisos pausados enquanto uma tela está aberta ou a GUI está oculta; adiados quando não cabe um cartão acima da hotbar. Desconexão limpa a fila.
- Mesma infraestrutura aceita categoria, título, mensagem, cor e duração para novos eventos globais. Não depende de reconhecer palavras na mensagem.

## Novos eventos

Na thread do servidor:

```java
GlobalEvents.broadcast("festival", Component.literal("FESTIVAL / EVENTO GLOBAL"),
    Component.literal("O festival começou em todas as regiões!"), 0xFF70E0B7, 7000);
```

Importe `com.tupinicraft.event.GlobalEvents`. O envio alcança todos os jogadores conectados, em todas as dimensões. Não há histórico persistente para jogadores offline. A eleição já utiliza essa API.

## Compatibilidade e compilação

Protocolo de rede alterado de 3 para 4: cliente e servidor precisam usar o mesmo build atualizado. Nenhuma migração de mundo ou mudança no formato dos votos foi introduzida.

Execute com Java 21 e acesso aos repositórios das dependências:

```sh
./gradlew build
```

O JAR atualizado será produzido em `build/libs`. Os arquivos já presentes em `dist` vieram do ZIP original e **não contêm esta repaginação**.

## Validação realizada

- 27 verificações de regressão existentes: passaram.
- 2.491 verificações de layout: passaram (paginação de até 501 candidatos, rodapé, mudança de página e colisão dos cartões com o HUD).
- Parser do JDK: todos os arquivos Java analisados sem erros de sintaxe.
- Build completo: não executado até o fim. O download do Gradle 9.2.1 falhou com `Network is unreachable`; não foi possível validar os tipos contra Minecraft/NeoForge nem iniciar o cliente.
- Não houve teste visual em Minecraft nem teste multijogador desta versão. Não confundir os testes puros de geometria com validação visual em execução.

## Checklist em jogo

1. Compilar e instalar o mesmo JAR em cliente e servidor; manter as dependências já exigidas pelo projeto.
2. Abrir eleição com várias disputas e mais de 20 candidatos. Percorrer páginas, trocar disputa, redimensionar janela e mudar escala da GUI.
3. Selecionar um nome: nenhum voto deve sair antes de Confirmar. Confirmar e verificar o retorno; tentar clique repetido, candidato votando na própria disputa e fase encerrada.
4. Testar Tab, Enter, Esc e nomes longos. Simular atraso de rede e conferir o estado do servidor após timeout.
5. Verificar inscrição, votação, apuração e resultado em dois clientes e dimensões distintas.
6. Testar eventos junto de bossbars, efeitos de poção e F3; ocultar GUI, abrir inventário/urna, fechar e desconectar/reconectar.
7. Conferir listas longas de mensagens, fila cheia e tela pequena. Quando não existe espaço seguro, o aviso aguarda espaço disponível.
