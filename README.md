> **Tigrinho compilado:** JAR novo em `dist/tupinicraft-0.5.3-tigrinho-dev.jar`. Build, testes de regras e teste de servidor dedicado aprovados. A interface e os giros com jogadores ainda precisam de teste no cliente. Consulte [docs/TIGRINHO.md](docs/TIGRINHO.md).

# TupiniCraft

Mod brasileiro de sátira política para **Minecraft 1.21.1** com **NeoForge 21.1.x** (Java 21).
Origem regional (27 UFs), cargos políticos, XP político, popularidade, HUD e eleições multiplayer com urna eletrônica.

### Atualização 0.5.2

[Correções, validação e instalação da 0.5.2](docs/VALIDACAO_0.5.2.md).

Cliente e servidor devem usar a versão 0.5.2.

O JAR desta entrega está em `dist/tupinicraft-0.5.2.jar`. Remova o JAR antigo do TupiniCraft da pasta `mods` e coloque o novo, mantendo as dependências descritas abaixo.

- Aba **TupiniCraft** no criativo, com a Urna Eletrônica, o ovo do Cabo Eleitoral e os 33 ícones das árvores.
- Texturas dos ícones movidas para `textures/item/skilltree`, incluído no atlas padrão do Minecraft; modelos atualizados.
- Validação de modelos e texturas também dentro do JAR, com rejeição de referências fora do atlas.

### Atualização 0.5.1

O histórico dos testes da atualização anterior está em [Validação 0.5.1](docs/VALIDACAO_0.5.1.md).

- Efeitos de habilidades são removidos ou atualizados corretamente após `/reload`, inclusive quando a skill sai do datapack, muda de atributo ou perde efeitos.
- Mandatos vencidos são encerrados antes de uma nova posse, mantendo o cargo e as atualizações pendentes dos jogadores consistentes.
- A apuração usa pontuações de 64 bits, inclusive no pacote do HUD. Cliente e servidor precisam desta mesma versão (protocolo de rede 2).
- As árvores e as quatro habilidades existentes usam as imagens fornecidas em `Arte.rar`; 33 ícones foram adaptados para PNG transparente de 128 × 128. Veja [o catálogo de arte](docs/ARTE_SKILL_TREES.md).

## Como jogar (instalar no seu Minecraft)

1. **Baixe o jar do mod** na página de [Releases](https://github.com/Juann027/MineModding_01/releases/latest)
   (arquivo `tupinicraft-<versão>.jar` em *Assets*), ou compile você mesmo com `./gradlew build`
   (Windows: `gradlew.bat build`) → o jar sai em `build/libs/`.
2. **Instale o NeoForge 21.1.x para Minecraft 1.21.1**: baixe o instalador em <https://neoforged.net>, execute e escolha "Install client".
   Baixe também os 4 mods **obrigatórios**, todos para NeoForge 1.21.1 (coloque na mesma pasta `mods`).
   Estão no Modrinth (links abaixo) e também podem ser procurados pelo nome no CurseForge:

   | Mod | Versão | Para quê | Onde instalar | Download |
   |---|---|---|---|---|
   | GeckoLib | 4.9.3 | animações dos mobs | cliente e servidor | <https://modrinth.com/mod/geckolib> |
   | SmartBrainLib | 1.16.11 | IA dos mobs | cliente e servidor | <https://modrinth.com/mod/smartbrainlib> |
   | UI Lib (DAQEM) | 9.0.0 | tela das árvores de habilidades (tecla **K**) | só no cliente | <https://modrinth.com/mod/ui-lib> |
   | Architectury API | 13.0.x | exigido pelo UI Lib | só no cliente | <https://modrinth.com/mod/architectury-api> |

   **Vindo da v0.4.0?** Pode apagar SkillLib, KubeJS e Rhino da pasta `mods`: o TupiniCraft não usa mais.
   As skills compradas na v0.4.0 ficavam guardadas no SkillLib e não são recuperadas (eram só as 4 de teste).

   Se faltar algum, o jogo mostra na tela de carregamento qual é.
3. Abra o launcher do Minecraft uma vez com o perfil **NeoForge** (isso cria a pasta `mods`).
4. Copie o jar para a pasta `mods`:
   - Windows: `%APPDATA%\.minecraft\mods`
   - macOS: `~/Library/Application Support/minecraft/mods`
   - Linux: `~/.minecraft/mods`
5. Inicie o jogo com o perfil NeoForge.

### Multiplayer

- O **servidor** precisa de NeoForge 21.1.x (use o mesmo instalador com "Install server") e dos mesmos jars em `mods/`
  (TupiniCraft, GeckoLib e SmartBrainLib; UI Lib e Architectury são só do cliente, mas podem ficar no servidor).
- **Todos os jogadores** precisam dos mesmos mods no cliente.
- Comandos de administração (`/brasil admin ...`) exigem OP nível 2 (`/op <jogador>` no console).
- Configurações padrão: `config/tupinicraft-server.toml` do servidor. Administradores podem mudar valores no
  próprio mundo com `/brasil admin config set` (veja abaixo); essas mudanças ficam salvas no mundo e têm
  prioridade sobre o arquivo.

## Comandos

| Comando | Descrição |
|---|---|
| `/brasil perfil` | Origem, cargo, XP político, popularidade e próximo cargo |
| `/brasil origem` | Sua origem regional |
| `/brasil eleicao status` | Fase, cargos em disputa, candidatos e tempo restante |
| `/brasil eleicao candidatar [cargo]` | Inscreve você numa disputa do seu domicílio eleitoral (basta ter o XP político mínimo) |
| `/brasil eleicao votar <candidato>` | Vota (ou use a Urna Eletrônica com o botão direito) |
| `/brasil skills` | (Opcional) lista as skills no chat; o normal é usar a tela da tecla **K** |
| `/brasil mandatos [território]` | Mandatos vigentes, ou todas as cadeiras de um território (ex.: `es`, `vitoria`) |
| `/brasil admin eleicao iniciar <cargo> [território] [cargo2 território2]` | Inicia uma eleição (ex.: `prefeito vitoria`) |
| `/brasil admin eleicao avancar` / `cancelar` | Pula para a próxima fase / cancela |
| `/brasil admin xp add\|set <jogador> <qtd>` | Ajusta XP político |
| `/brasil admin popularidade add\|set <jogador> <qtd>` | Ajusta popularidade |
| `/brasil admin cargo set <jogador> <cargo>` | Define o cargo (ignora requisitos; para testes) |
| `/brasil admin origem set <jogador> <uf>` | Define a origem (ex.: `MA`) |
| `/brasil admin mandato encerrar <território> <cargo>` | Encerra um mandato (cadeira fica vaga) |
| `/brasil admin config list [prefixo]` | Lista as configurações e de onde vem o valor (mundo ou arquivo) |
| `/brasil admin config get <chave>` | Mostra uma configuração |
| `/brasil admin config set <chave> <valor>` | Muda no mundo (ex.: `mandato.senador 60`, `eleicao.intervalo_gerais 45`) |
| `/brasil admin config reset <chave>` | Volta ao valor do arquivo |
| `/brasil admin teste dinheiro set\|add <jogador> <valor>` | **Teste:** muda o saldo (a economia ainda não existe) |
| `/brasil admin teste partido set <jogador> <id>` / `sair <jogador>` | **Teste:** filia/desfilia (partidos ainda não existem) |
| `/brasil admin teste pontos set <jogador> <cargo> <qtd>` | **Teste:** pontos de habilidade de um cargo |

Cargos: `desempregado`, `vereador`, `deputado_estadual`, `prefeito`, `deputado_federal`, `senador`, `governador`, `presidente`.

### Carreira política

- Não existe "subir um cargo de cada vez". Qualquer jogador, mesmo **Desempregado**, pode concorrer
  **direto a qualquer cargo**, até Presidente, desde que tenha o **XP político mínimo** do cargo.
- **Popularidade não é requisito.** Ela aumenta a sua pontuação na apuração e o peso do seu voto.
- Ao vencer, você assume o cargo. Quando o mandato termina, ou se você perde a cadeira, volta a ser Desempregado,
  mas mantém o XP, a popularidade e os pontos de habilidade daquele cargo. Eles voltam a valer se você reassumir o cargo.
- Cada jogador ocupa **uma cadeira por vez**. Ao vencer outra eleição, você renuncia à cadeira anterior.

### Árvores de habilidades (tecla K)

Aperte **K** (configurável em Opções → Controles) para abrir as árvores, numa tela no estilo da de
Progressos do Minecraft: uma aba por árvore, skills ligadas por linhas (cada uma exige a anterior) e moldura
dourada nas compradas. As abas que aparecem:
- **Pessoais:** para todos.
- **Regional:** só a da macrorregião da sua origem.
- **Partidária:** só a do partido em que você está filiado.
- **Cargos políticos:** uma por cargo.

Uma árvore que você deixou de poder usar (mudou de partido, por exemplo) continua aparecendo se você já comprou
algo nela, para você ver o que ficou suspenso.

Como funcionam:
- **Compra:** passe o mouse na skill para ver o preço e **clique nela**. Ela é comprada e já fica ativa; uma
  notificação no canto da tela confirma ou explica o motivo da recusa. As pessoais, regionais e partidárias custam
  **dinheiro** (reais fictícios); as de cargo custam **pontos daquele cargo**, ganhos exercendo o mandato online.
  O XP político pode ser exigido, mas nunca é gasto.
- **Saldo:** aparece no painel do canto superior direito. Cada jogador começa com **R$ 1.000** (configurável em
  `economia.saldo_inicial`).
- **Ordem:** dentro de uma árvore, é preciso comprar a skill anterior (a ligada pela linha) antes da próxima.
- **Suspensão:** se você deixar o partido, perder o mandato ou não for daquela região, a skill **fica suspensa** e volta a funcionar sozinha quando o requisito voltar. Nada do que foi comprado é perdido.

Nesta versão existem 4 skills de teste, uma por árvore. Ainda não há como ganhar dinheiro nem entrar em partidos
(isso vem com a economia e os partidos); por enquanto, além do saldo inicial, só os administradores mudam saldo e
filiação, com os comandos de teste.

### Mob de teste

O **Cabo Eleitoral (teste)** (ovo no criativo ou `/summon tupinicraft:cabo_eleitoral`) valida as bibliotecas de
animação (GeckoLib) e de IA (SmartBrainLib): passeia, olha para os jogadores e revida se for atacado.

### Territórios e mandatos

- Presidente governa o **Brasil**; Governador, Senador e Deputados, um **estado**; Prefeito e Vereador, um **município**
  (por enquanto, as 27 capitais).
- Cada jogador disputa no seu **domicílio eleitoral**, que vem da origem: o próprio estado e a capital dele.
- Quem vence toma posse da cadeira por um mandato (padrão 30 min, Senador 45 min). Mandatos vencidos deixam a cadeira vaga.
- Quando faltam candidatos, **aldeões** (NPCs) entram na disputa e podem ocupar cadeiras.
- Exercendo mandato, o jogador ganha **pontos de habilidade daquele cargo** pelo tempo online (padrão: 1 a cada 10 min).
- As eleições automáticas seguem dois calendários: **gerais** e **municipais**.

## Desenvolvimento

Para lançar uma nova versão: aumente `mod_version` em `gradle.properties` e faça merge na `main`;
o workflow **Release** compila e publica o jar automaticamente (se a versão ainda não tiver release).

```bash
./gradlew build       # compila e roda os testes de regras
./gradlew runClient   # cliente de desenvolvimento
./gradlew runServer   # servidor de desenvolvimento
bash scripts/regression-test.sh    # regressões sem dependências do Minecraft (Java 21)
python3 scripts/validate-skill-art.py # referências, modelos e integridade dos ícones
```

O Gradle baixa todas as bibliotecas sozinho (UI Lib e Architectury pelo Maven do Modrinth).

Convenções técnicas em [CLAUDE.md](CLAUDE.md).
