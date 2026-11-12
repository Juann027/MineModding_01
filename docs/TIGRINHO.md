# Tigrinho — TupiniCraft 0.5.3-tigrinho-dev

## Estado da entrega

Implementação adicionada diretamente ao projeto original e **compilada com sucesso**. JAR novo em `dist/tupinicraft-0.5.3-tigrinho-dev.jar`.
O JAR `dist/tupinicraft-0.5.2.jar` continua preservado como original e não contém o minigame; instale somente a nova versão.

Na nova tentativa foi possível configurar o proxy efetivamente disponível para o Java, baixar o Gradle e usar um JDK 21 completo. A compilação detectou uma chamada ao construtor de `Session` sem o argumento `now`; ela foi corrigida. O build seguinte passou em 16 segundos, após preparar as dependências.

Resultados realmente executados com Java 21:

| Verificação | Resultado |
|---|---|
| `scripts/test-tigrinho.sh` | 2.169 verificações aprovadas |
| `scripts/regression-test.sh` | 27 verificações existentes aprovadas |
| `scripts/validate-tigrinho.py` | 63 JSONs válidos; sete imagens com dimensões/referências corretas; modelo, receita, loot e tag presentes |
| Parser Java do JDK, todos os fontes | 110 arquivos; zero erros sintáticos; não é checagem de tipos/imports |
| Gradle 9.2.1 `build --no-daemon --no-configuration-cache --max-workers=2` | **BUILD SUCCESSFUL**; JAR gerado; `rulesTest`: 136 aprovados; `tigrinhoTest`: 2.169 aprovados |
| Servidor dedicado | Duas inicializações, desligamentos normais e regressões de persistência de eleição/configuração: **OK**; máquina colocada e reconhecida via comando |
| Cliente gráfico, giros com jogadores, multiplayer do minigame e persistência de fichas reais | Não executados; permanecem pendentes |

Os registros estão em `docs/tigrinho-validation/`. Os testes de regras não usam mocks de Minecraft e não comprovam o fluxo de rede com jogadores. O carregamento no servidor dedicado foi verificado separadamente. O teste de servidor usou o script existente, apontado para o Gradle local, com dois comandos adicionais para colocar e reconhecer a máquina. O log contém avisos de consulta externa às chaves de autenticação e da configuração flat do teste; eles não impediram as duas inicializações nem as verificações.

## Auditoria do projeto recebido

- Modid `tupinicraft`; pacote `com.tupinicraft`; entrada comum `TupiniCraft` e cliente `TupiniCraftClient`.
- Minecraft 1.21.1, NeoForge 21.1.265, Java 21, Gradle wrapper 9.2.1, plugin ModDevGradle 2.0.148; Parchment 2024.11.17.
- Registros existentes: itens, entidades, attachments e aba criativa. Não havia registro de blocos; foi acrescentado `ModBlocks`.
- O Tigrinho **não era uma entidade**: era `tupinicraft:icone_tigrinho`, registrado pelo laço de ícones de `ModItems`. Sua textura fica em `textures/item/skilltree/tigrinho.png` (128×128). O ID, modelo, textura e uso como ícone foram preservados. O item agora tem uma subclasse com interação de botão direito.
- Rede existente em `ModNetwork`: payloads de política, urna, carteira e habilidades. O protocolo passou de 2 para 3, pois cliente e servidor precisam compartilhar os novos payloads.
- Economia existente em `Wallet`/`WalletService`; persistência também usa attachments e `SavedData` nos sistemas de eleição, mandatos e configuração. As fichas têm ledger próprio e não convertem a moeda existente.
- Bibliotecas realmente declaradas: GeckoLib 4.9.3, SmartBrainLib 1.16.11, UI Lib 9.0.0 (versão Modrinth XSWl7TVm) e Architectury 13.0.11+neoforge. Better Advanced Tooltips, KubeJS, Rhino e SkillsLib não são dependências declaradas desta revisão. O histórico do projeto explica a migração de SkillsLib para UI Lib.
- Nenhuma dependência adicionada. A nova interface usa `Screen`, `GuiGraphics` e botões nativos estilizados; não exige animação de entidade, IA ou UI Lib. As dependências anteriores continuam necessárias para os demais sistemas.

## Arquitetura e fluxo

1. Botão direito no item existente ou na nova máquina chama `TigrinhoService.open` no servidor.
2. Servidor valida acesso e cria sessão com UUID aleatório, dimensão, origem e cópia das regras. Envia saldo, apostas e símbolos à tela.
3. Cliente envia somente UUID da sessão, sequência e aposta. Fechar usa aposta `-1` como mensagem de encerramento, nunca como giro.
4. Servidor valida jogador vivo, espectador, dimensão, prazo da sessão, item na mão ou máquina ainda presente a até 8 blocos, sequência, frequência, aposta, saldo, IDs das recompensas e limite de crédito.
5. Sorteia três símbolos independentes com RNG do servidor. Debita a aposta e credita o prêmio em uma atualização do ledger. Entrega itens adicionais, quando habilitados; excedente de inventário é derrubado junto ao jogador.
6. Cliente anima os resultados já definidos; rolos desaceleram por curva cúbica e param em tempos diferentes. O recebimento/fechamento da tela nunca concede prêmios.

Responsabilidades:

| Classe | Função |
|---|---|
| `SlotRules` | Parser limitado, pesos, apostas e cálculo de trincas, sem dependência de Minecraft |
| `SpinGate` | Sequência exata e limite de um pedido aceito por tick |
| `TigrinhoConfig` | Configuração do servidor e validação de estrutura |
| `TigrinhoLedger` | Saldo/cooldown por UUID, salvo no mundo |
| `TigrinhoService` | Sessões, validação, sorteio, pagamentos, comandos e limpeza |
| `TigrinhoItem`, `TigrinhoBlock` | Dois acessos à mesma lógica |
| `TigrinhoSpinPayload`, `TigrinhoStatePayload` | Intenção C→S e snapshot validado S→C |
| `TigrinhoScreen`, `ReelAnimation` | Interface e apresentação do resultado |
| `ModBlocks` | Registro da máquina |

A máquina não tem block entity nem renderer com tick. Usa modelo JSON estático com gabinete, molduras, visor e símbolos anexados. A colisão é de um bloco inteiro. Não armazena fichas: remover/reposicionar a máquina não altera contas.

As classes gráficas ficam sob `client`; o handler comum usa o padrão de chamada adiada por lambda já empregado pelo projeto. O carregamento dessas classes no servidor dedicado foi exercitado nas duas inicializações do teste de fumaça, sem erro de classes exclusivas do cliente.

## Economia, regras e probabilidades

Cada UUID recebe **1.000 fichas uma única vez por mundo**. Morte, reconexão, mudança de dimensão e troca de acesso não renovam esse crédito. Não existe pagamento, saque, dinheiro real ou serviço externo.

Custo = aposta selecionada. Valores padrão: 10, 25, 50, 100. Apenas três símbolos iguais pagam; não há pagamento por pares, linhas diagonais ou wildcards. O prêmio é **bruto**: saldo final = saldo anterior − aposta + aposta × multiplicador. Itens configurados são adicionais e sua quantidade não varia com a aposta.

| Símbolo/ID | Peso | Chance em cada rolo | Chance da trinca | Multiplicador bruto |
|---|---:|---:|---:|---:|
| `tiger` | 12 | 12% | 0,1728% | 60× |
| `cards` | 20 | 20% | 0,8% | 24× |
| `pouch` | 25 | 25% | 1,5625% | 15× |
| `fortune` | 25 | 25% | 1,5625% | 15× |
| `t` | 5 | 5% | 0,0125% | 300× |
| `diamond` | 5 | 5% | 0,0125% | 300× |
| `apple` | 8 | 8% | 0,0512% | 120× |

Total dos pesos = 100. Chance de qualquer trinca = **4,174%**; chance de giro sem prêmio = **95,826%**. Retorno teórico bruto em fichas = **90,087%** da aposta, considerando somente as fichas. Não é uma promessa para uma sequência finita de giros. Fórmulas: `P(i) = peso_i / soma_pesos`; `P(trinca_i) = P(i)^3`; `retorno = soma(P(i)^3 × multiplicador_i)`.

A trinca `t` recebe mensagem especial “Tigrinho Dourado”. As demais trincas usam o mesmo cálculo configurável. Não há ajuste por histórico, saldo, quantidade de derrotas ou jogador. O saldo só participa da validação de fundos e prevenção de overflow.

## Configuração

Arquivo `tupinicraft-tigrinho-server.toml`, registrado como configuração SERVER. Nesta execução NeoForge 21.1.265 o caminho efetivo foi `run/config/tupinicraft-tigrinho-server.toml`. Consulte o caminho anunciado por `ConfigTracker` no log da sua instalação; não presuma o diretório `serverconfig` de outras versões.

| Chave | Padrão | Limites/significado |
|---|---|---|
| `enabled` | `true` | Habilita o minigame; desabilitar também invalida novos giros de sessão aberta |
| `sounds` | `true` | Sons de botões, giro e vitória na nova tela |
| `initialChips` | `1000` | 0–1.000.000; somente contas ainda inexistentes |
| `animationTicks` | `60` | 20–200 ticks; 20 ticks = 1 segundo |
| `cooldownTicks` | `80` | 20–1.200; intervalo efetivo é o maior entre animação e cooldown |
| `sessionSeconds` | `300` | 30–3.600; renovado após giro válido |
| `bets` | `"10,25,50,100"` | Até 12 valores distintos, entre 1 e 100.000; texto até 256 caracteres |
| `symbols` | Sete símbolos acima | 2–32 entradas; texto total até 8.192 caracteres |

Formato de cada símbolo (entradas separadas por `;`):

```text
id|texture|width|height|weight|tripleMultiplier|rewardItem|rewardCount
```

Exemplo para alterar a entrada `t` e conceder um item que existe no projeto:

```text
t|tupinicraft:textures/gui/tigrinho/t.png|64|64|5|300|tupinicraft:icone_tigrinho|1
```

Não substitua toda a lista por uma única entrada: preserve pelo menos duas. Peso 1–10.000; multiplicador 0–10.000; dimensões 1–2.048; quantidade adicional 0–64. Quantidade `0` desativa a recompensa de item (padrão de todas as entradas). Item não registrado, quando a quantidade é maior que zero, bloqueia o giro **antes do débito**. IDs de símbolos devem ser únicos e conter somente letras minúsculas, números ou `_` (até 32 caracteres).

Para adicionar arte: acrescente o PNG ao caminho configurado em `assets/<namespace>/textures/`, com as dimensões reais; acrescente a entrada à lista. Distribua o mesmo recurso ao cliente, no mod ou em resource pack. A tabela se adapta e pagina a cada cinco símbolos. Novos símbolos não exigem código Java. Arquivos ausentes usam a indicação de textura ausente do Minecraft.

Sessões copiam símbolos, apostas, som e animação na abertura para manter tabela e sorteio coerentes. Reabra a tela após editar essas opções; para testar configuração de forma reproduzível, pare o servidor, edite o TOML e reinicie. `enabled` e cooldown são consultados no servidor durante os pedidos.

Saldo máximo: 1.000.000.000.000. Um giro que poderia ultrapassar esse limite é rejeitado antes do sorteio, sem truncar pagamentos ou alterar as chances.

Comandos:

```text
/tigrinho saldo
/tigrinho definir <jogador_online> <fichas>
```

O segundo exige permissão nível 2 (operador) e aceita 0 até o limite de saldo. Não exige uma sessão aberta e preserva o cooldown. Serve para administrar a economia fictícia e repor fichas em testes.

## Recursos visuais

Os PNGs anexados foram copiados **sem modificar os bytes** para `textures/gui/tigrinho/`:

| Arquivo fornecido | Destino | Dimensões |
|---|---|---|
| `IconeRoleta01.png` | `tiger.png` | 64×64 |
| `IconeRoleta02.png` | `cards.png` | 64×64 |
| `IconeRoleta03.png` | `pouch.png` | 64×64 |
| `IconeRoleta04.png` | `fortune.png` | 64×64 |
| `SimboloTdeTigrinhoRoleta.png` | `t.png` | 64×64 |
| `11-2-minecraft-diamond-png.png` | `diamond.png` | 605×497 |
| `icons8-minecraft-maçã-dourada-48.png` | `apple.png` | 48×48 |

Mascote: textura original `textures/item/skilltree/tigrinho.png`, reutilizada sem alterações. Os anexos não incluem fundos completos, botões, molduras, imagens de premiação ou áudio. Fundo, botões, brilhos e partículas de tela são desenhados com primitivas nativas; a máquina reutiliza materiais vanilla e os símbolos fornecidos. Não há PNG provisório criado fingindo ser arte final. Uma arte exclusiva de gabinete/fundo e sons próprios podem ser adicionados depois; não são necessários para a lógica. A interface ainda precisa de avaliação visual dentro do jogo. O diamante fornecido tem proporção não quadrada e é ajustado à célula quadrada.

## Como compilar e testar no Minecraft

1. Instale Java/JDK 21 e extraia o projeto. Na pasta `MineModding_01-main`, execute `./gradlew build` (Windows: `gradlew.bat build`). No Linux, use `bash gradlew build` se faltar permissão de execução.
2. O build executa `rulesTest` e `tigrinhoTest` pelo `check`. Para as verificações independentes: `bash scripts/test-tigrinho.sh`, `bash scripts/regression-test.sh` e `python3 scripts/validate-tigrinho.py`.
3. O build desta entrega foi aprovado. Use o novo `dist/tupinicraft-0.5.3-tigrinho-dev.jar`, ou `build/libs/tupinicraft-0.5.3-tigrinho-dev.jar` se recompilar. Não use o JAR 0.5.2 antigo nem instale as duas versões simultaneamente.
4. Instale no cliente e servidor NeoForge 21.1.265 / Minecraft 1.21.1, mantendo as dependências originais descritas no README. UI Lib/Architectury continuam sendo exigências do cliente; GeckoLib/SmartBrainLib também são usadas pelo restante do mod no servidor.
5. Em mundo de teste, obtenha `/give @s tupinicraft:icone_tigrinho` e use botão direito no ar. Selecione aposta, gire e verifique saldo. A tecla Tab navega pelos botões; Escape fecha; a tela não pausa o servidor integrado.
6. Obtenha `/give @s tupinicraft:tigrinho_machine`, posicione e clique com botão direito. Também há receita de bancada: `IGI / IRI / III`, com I=ferro, G=ouro, R=redstone. Quebre e recoloque; o bloco deve cair como item e o saldo deve continuar igual.
7. Verifique GUI nas escalas 1–4/automática e após redimensionar a janela. Verifique animação, alinhamento dos símbolos, foco de teclado, página de prêmios e sons com `sounds=false`.
8. Use `/tigrinho definir <nome> 0`: a interface deve impedir aposta sem saldo. Reponha com o mesmo comando. Em testes, altere pesos/multiplicadores sem mudar as probabilidades durante uma sessão; reabra a tela.
9. Habilite recompensa de item, teste inventário livre e cheio, e teste ID de item inexistente: o último deve produzir rejeição sem débito. Para cobertura determinística de vitórias, configure temporariamente dois símbolos de pesos 10.000 e 1; restaure os valores após o teste.
10. Com dois clientes, confirme saldos separados, interações simultâneas e resultados independentes. Teste afastar-se mais de 8 blocos, destruir a máquina, trocar o item de mão, mudar dimensão, morrer e desabilitar o recurso com a tela aberta.
11. Feche ou desconecte durante a animação: a transação já foi liquidada. Reconecte, verifique saldo e cooldown; não deve haver pagamento repetido nem renovação das fichas iniciais. Repita com desligamento normal/reinício do servidor.
12. Em cliente de desenvolvimento que possa enviar payloads, repita UUID+sequência, envie sequência futura, aposta negativa/fora da lista, sessão inventada/antiga e spam. Só um pedido válido deve debitar. Fechar com `-1` encerra a sessão intencionalmente. Os testes de `SpinGate` cobrem a regra pura, não substituem esse teste de rede.
13. Execute também `.github/scripts/server-smoke-test.sh` para as regressões de servidor já existentes; ele não cobre sozinho o minigame. Faça o smoke test da máquina e dos comandos no servidor dedicado.

## Persistência e limites conhecidos

- Ledger em `<mundo>/data/tupinicraft_tigrinho.dat`, usando o armazenamento do overworld; contas compartilhadas pelas dimensões. O arquivo é salvo pelo ciclo normal de `SavedData`, não após cada pacote.
- Logout descarta a sessão, não o saldo. Cooldown é persistido em ticks globais do mundo e não é contornado por sair/entrar, fechar/reabrir ou trocar de acesso. Sessões expiradas são limpas a cada segundo; servidor parado libera o cache.
- Queda abrupta do processo antes do save pode perder a última transação. Não foi implementado journal em disco nem atomicidade entre o save do inventário e o ledger. Faça backup e use encerramento normal; não há garantia de recuperação transacional após crash.
- Build completo e teste de fumaça de servidor dedicado executados e aprovados. Não foram executados cliente gráfico, benchmark de FPS, ensaio dos payloads com jogadores, giros completos ou persistência de fichas após reconexão/reinício. A versão permanece `-dev` até essa homologação funcional; compilar e iniciar o servidor não comprova a interface nem todas as regras em multiplayer.
- A tela mostra textos em português; nomes do bloco possuem entradas pt_br/en_us. Localização completa da nova interface é uma melhoria futura.
- Não houve criação de repositório externo, PR, merge ou publicação. A entrega inclui fontes atualizados e o JAR compilado.

## Referências consultadas

A implementação segue os padrões locais e a documentação oficial específica de 1.21–1.21.1:

- https://docs.neoforged.net/docs/1.21.1/networking/payload/
- https://docs.neoforged.net/docs/1.21.1/blocks/
- https://docs.neoforged.net/docs/1.21.1/gui/screens/
- https://docs.neoforged.net/docs/1.21.1/misc/config/

A relação exata de arquivos criados e modificados está em `TIGRINHO_ARQUIVOS.md`.
